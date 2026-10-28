#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
电影票购票系统 · 压测与集群一致性校验

只有标准库依赖，不需要 pip install。

子命令
------
  read      读场景压测：并发查余票，统计 QPS / P99 / 缓存挡库效果
  write     写场景压测：N 个用户抢 M 张票，验证并发下不超卖
  cluster   集群专项（多实例 + nginx 负载均衡）：
              1 负载均衡分发        2 JWT 跨实例
              3 余票缓存共享        4 幂等键跨实例
              5 跨实例并发不超卖    6 订单号全局唯一

示例
------
  python scripts/loadtest.py read    --concurrency 20 --rounds 20
  python scripts/loadtest.py write   --concurrency 12 --stock 10
  python scripts/loadtest.py cluster --screening 1 --date 2026-11-01

前置
----
  集群模式需要：两个后端实例（8080 / 8081）+ nginx upstream 指向它们，
  并且本机能调用 mysql / redis-cli（用于重置票池、清缓存）。
  路径不对时用 --mysql-exe / --redis-cli 显式指定。
"""

import argparse
import http.client
import json
import os
import statistics
import subprocess
import sys
import threading
import time
import urllib.parse

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# ============================ HTTP 客户端 ============================


class Client:
    """极简 HTTP 客户端，按线程复用连接（keep-alive），压测数字才准。"""

    def __init__(self, base):
        u = urllib.parse.urlparse(base)
        self.base = base.rstrip("/")
        self.host = u.hostname
        self.port = u.port or 80
        self.local = threading.local()

    def _conn(self):
        conn = getattr(self.local, "conn", None)
        if conn is None:
            conn = http.client.HTTPConnection(self.host, self.port, timeout=15)
            self.local.conn = conn
        return conn

    def request(self, method, path, body=None, token=None, timeout=15):
        """返回 (http_status, json_body, headers)"""
        # 请求体必须显式编码成 UTF-8 字节：http.client 收到 str 会按 latin-1 发送，
        # 票种这类中文参数会直接抛 "not valid Latin-1"
        payload = (json.dumps(body, ensure_ascii=False).encode("utf-8")
                   if body is not None else None)
        headers = {"Content-Type": "application/json; charset=utf-8"}
        if token:
            headers["Authorization"] = "Bearer " + token

        last_err = None
        for attempt in range(2):
            try:
                conn = self._conn()
                conn.request(method, path, body=payload, headers=headers)
                resp = conn.getresponse()
                raw = resp.read().decode("utf-8", "replace")
                hdrs = {k.lower(): v for k, v in resp.getheaders()}
                try:
                    data = json.loads(raw) if raw else {}
                except json.JSONDecodeError:
                    data = {"raw": raw}
                return resp.status, data, hdrs
            except Exception as e:  # 连接被对端关掉 → 重建一次
                last_err = e
                try:
                    self._conn().close()
                except Exception:
                    pass
                self.local.conn = None
                if attempt == 1:
                    raise last_err

    # ---- 业务便捷方法 ----

    def login(self, username, password):
        st, data, _ = self.request("POST", "/api/auth/login",
                                   {"username": username, "password": password})
        if data.get("code") != 200:
            raise RuntimeError("登录失败 %s: %s" % (username, data.get("message")))
        return data["data"]["token"]

    def register(self, username, password, nickname=None):
        st, data, _ = self.request("POST", "/api/auth/register",
                                   {"username": username, "password": password,
                                    "nickname": nickname or username})
        return data

    def remain(self, screening_id, show_date, ticket_type, token):
        path = ("/api/ticket/remain?screeningId=%d&showDate=%s&ticketType=%s"
                % (screening_id, show_date, urllib.parse.quote(ticket_type)))
        st, data, _ = self.request("GET", path, token=token)
        return data

    def buy(self, screening_id, show_date, ticket_type, count, token, name="测试用户"):
        return self.request("POST", "/api/ticket/buy", {
            "screeningId": screening_id,
            "showDate": show_date,
            "ticketType": ticket_type,
            "ticketCount": count,
            "passengerName": name,
            "idCardNo": "440101199001010011",
        }, token=token)[1]


# ============================ 统计工具 ============================


def percentile(values, p):
    if not values:
        return 0.0
    values = sorted(values)
    idx = min(len(values) - 1, max(0, int(round(p / 100.0 * len(values))) - 1))
    return values[idx]


class Tally:
    def __init__(self):
        self.lock = threading.Lock()
        self.latencies = []
        self.ok = []
        self.fail = []

    def add(self, ok, latency_ms, detail=None):
        with self.lock:
            self.latencies.append(latency_ms)
            (self.ok if ok else self.fail).append(detail)

    def summary(self):
        total = len(self.latencies)
        wall = (max(self.latencies) if self.latencies else 0)
        return {
            "total": total,
            "ok": len(self.ok),
            "fail": len(self.fail),
            "p50": percentile(self.latencies, 50),
            "p95": percentile(self.latencies, 95),
            "p99": percentile(self.latencies, 99),
            "max": max(self.latencies) if self.latencies else 0,
        }


def wait_for(predicate, timeout=30, interval=0.5):
    """轮询直到 predicate() 为真，返回 (是否等到, 实际耗时秒)。

    MQ 是异步落库的：buy 接口返回成功时订单还在队列里，
    立刻查库会把"消费者还没跑完"误判成"少卖/超卖"，所以断言前必须等它追平。
    """
    t0 = time.time()
    while time.time() - t0 < timeout:
        if predicate():
            return True, time.time() - t0
        time.sleep(interval)
    return False, time.time() - t0


def run_concurrent(n, worker):
    """n 个线程在同一个 Barrier 上同时起跑，尽量逼近真实并发。"""
    barrier = threading.Barrier(n)
    results = [None] * n

    def wrap(i):
        barrier.wait()
        results[i] = worker(i)

    threads = [threading.Thread(target=wrap, args=(i,), daemon=True) for i in range(n)]
    t0 = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join(timeout=120)
    return results, time.time() - t0


# ============================ 外部命令 ============================


def sh(cmd, timeout=30, quiet=True):
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                       timeout=timeout, shell=False)
    out = (p.stdout or b"").decode("utf-8", "replace")
    err = (p.stderr or b"").decode("utf-8", "replace")
    if p.returncode != 0 and not quiet:
        print("  命令失败: %s\n  %s" % (" ".join(cmd), err.strip()))
    return p.returncode, out, err


class Env:
    """压测需要直接改库 / 清缓存，封装成类，方便换参数。"""

    def __init__(self, args):
        self.mysql = args.mysql_exe
        self.redis = args.redis_cli
        self.db = args.db
        self.user = args.db_user
        self.password = args.db_pass

    def sql(self, statement, database=True):
        cmd = [self.mysql, "-u" + self.user, "-p" + self.password, "--default-character-set=utf8mb4",
               "-N", "-B"]
        if database:
            cmd.append(self.db)
        cmd += ["-e", statement]
        code, out, err = sh(cmd)
        if code != 0:
            raise RuntimeError("SQL 执行失败: %s" % (err.strip() or out.strip()))
        return [line.split("\t") for line in out.strip().splitlines() if line.strip()]

    def redis_cli(self, *cmd):
        code, out, err = sh([self.redis] + list(cmd))
        return out.strip()

    def reset_stock(self, screening_id, show_date, ticket_type, count, price=4500):
        """把某票种重置为 count 张：先改库，再把预热值写回 Redis（模拟放票）。"""
        self.sql(
            "UPDATE screening_seat SET total=%d, remain=%d, price=%d "
            "WHERE screening_id=%d AND show_date='%s' AND ticket_type='%s'"
            % (count, count, price, screening_id, show_date, ticket_type))
        key = "screening:stock:%d:%s:%s" % (screening_id, show_date, ticket_type)
        self.redis_cli("SET", key, str(count), "EX", "1800")
        return key

    def clear_buy_state(self, screening_id, show_date, ticket_type):
        """清掉余票缓存 + 幂等键，保证每轮测试互不干扰。"""
        keys = ["screening:stock:%d:%s:%s" % (screening_id, show_date, ticket_type)]
        for k in keys:
            self.redis_cli("DEL", k)
        self.redis_cli("EVAL",
                       "local ks=redis.call('KEYS','order:dup:*') "
                       "if #ks>0 then redis.call('DEL',unpack(ks)) end return #ks", "0")

    def stock_row(self, screening_id, show_date, ticket_type):
        rows = self.sql(
            "SELECT total, remain FROM screening_seat "
            "WHERE screening_id=%d AND show_date='%s' AND ticket_type='%s'"
            % (screening_id, show_date, ticket_type))
        if not rows:
            return 0, 0
        return int(rows[0][0]), int(rows[0][1])

    def count_orders(self, screening_id, show_date):
        rows = self.sql(
            "SELECT COUNT(*), COUNT(DISTINCT order_no) FROM movie_order "
            "WHERE screening_id=%d AND show_date='%s'" % (screening_id, show_date))
        return int(rows[0][0]), int(rows[0][1])

    def log_sql_hits(self, log_dir, needle="from screening_seat"):
        """统计各实例日志里打到 screening_seat 的 SQL 条数（用于验证缓存挡库）。"""
        hits = {}
        if not log_dir or not os.path.isdir(log_dir):
            return hits
        for name in sorted(os.listdir(log_dir)):
            if not (name.startswith("node-") and name.endswith(".log")):
                continue
            path = os.path.join(log_dir, name)
            try:
                with open(path, "r", encoding="utf-8", errors="ignore") as f:
                    text = f.read().lower()
            except OSError:
                continue
            hits[name] = text.count(needle)
        return hits


# ============================ 子命令：read ============================


def cmd_read(args, client, env):
    print("\n=== 读场景压测：%d 并发 × %d 轮 ===" % (args.concurrency, args.rounds))
    token = client.login(args.user, args.password)

    # 先查一次，把缓存建起来（否则第一批请求全部回源，QPS 不准）
    client.remain(args.screening, args.date, args.type, token)

    hits_before = env.log_sql_hits(args.log_dir)
    tally = Tally()

    def worker(i):
        for _ in range(args.rounds):
            t0 = time.time()
            try:
                data = client.remain(args.screening, args.date, args.type, token)
                cost = (time.time() - t0) * 1000
                tally.add(data.get("code") == 200, cost, data.get("message"))
            except Exception as e:
                tally.add(False, (time.time() - t0) * 1000, str(e))

    _, wall = run_concurrent(args.concurrency, worker)
    s = tally.summary()
    hits_after = env.log_sql_hits(args.log_dir)

    db_hits = 0
    for k, v in hits_after.items():
        db_hits += max(0, v - hits_before.get(k, 0))

    print("  请求总数 : %d" % s["total"])
    print("  成功率   : %.1f%%" % (100.0 * s["ok"] / max(1, s["total"])))
    print("  吞吐     : %.0f QPS" % (s["total"] / max(wall, 0.001)))
    print("  延迟     : P50 %.1fms  P95 %.1fms  P99 %.1fms  Max %.1fms"
          % (s["p50"], s["p95"], s["p99"], s["max"]))
    print("  打到 MySQL: %d 次 / %d 次查询（缓存挡掉 %.1f%%）"
          % (db_hits, s["total"], 100.0 * (1 - db_hits / max(1, s["total"]))))
    return 0 if s["fail"] == 0 else 1


# ============================ 子命令：write ============================


def prepare_users(client, count, prefix, password):
    """准备 count 个可用于抢票的用户：先尝试登录，不存在就注册。"""
    tokens = []
    for i in range(1, count + 1):
        name = "%s%02d" % (prefix, i)
        data = client.register(name, password)
        if data.get("code") == 200:
            tokens.append((name, data["data"]["token"]))
        elif data.get("code") == 1005:  # 用户名已存在
            tokens.append((name, client.login(name, password)))
        else:
            raise RuntimeError("准备用户 %s 失败: %s" % (name, data.get("message")))
    return tokens


def cmd_write(args, client, env):
    print("\n=== 写场景压测：%d 人抢 %d 张票 ===" % (args.concurrency, args.stock))

    env.clear_buy_state(args.screening, args.date, args.type)
    env.reset_stock(args.screening, args.date, args.type, args.stock, args.price)
    users = prepare_users(client, args.concurrency, args.user_prefix, args.user_pass)

    before_orders, _ = env.count_orders(args.screening, args.date)
    tally = Tally()
    success = []

    def worker(i):
        name, token = users[i]
        t0 = time.time()
        try:
            data = client.buy(args.screening, args.date, args.type, 1, token, name)
            cost = (time.time() - t0) * 1000
            if data.get("code") == 200:
                success.append(data["data"]["orderNo"])
                tally.add(True, cost, "OK")
            else:
                tally.add(False, cost, data.get("message"))
        except Exception as e:
            tally.add(False, (time.time() - t0) * 1000, str(e))

    _, wall = run_concurrent(args.concurrency, worker)

    # 等 MQ 消费者把订单落库（异步），再对账
    settled, waited = wait_for(
        lambda: env.count_orders(args.screening, args.date)[0] - before_orders >= len(success),
        timeout=args.settle_timeout)
    total, remain = env.stock_row(args.screening, args.date, args.type)
    after_orders, distinct = env.count_orders(args.screening, args.date)
    redis_remain = env.redis_cli("GET", "screening:stock:%d:%s:%s"
                                 % (args.screening, args.date, args.type))

    s = tally.summary()
    print("  耗时      : %.2fs" % wall)
    print("  成功 / 失败: %d / %d" % (s["ok"], s["fail"]))
    reasons = {}
    for m in tally.fail:
        reasons[m] = reasons.get(m, 0) + 1
    for msg, n in sorted(reasons.items(), key=lambda kv: -kv[1]):
        print("    - %s × %d" % (msg, n))
    print("  库存      : 放票 %d，DB 剩余 %d，Redis 剩余 %s" % (total, remain, redis_remain))
    print("  订单落库  : 新增 %d 单（失败数 %d）" % (after_orders - before_orders, s["fail"]))

    ok = True
    if s["ok"] != args.stock:
        print("  [FAIL] 成功数 %d != 库存 %d" % (s["ok"], args.stock))
        ok = False
    if remain != total - s["ok"]:
        print("  [FAIL] DB 余票 %d + 成功 %d != 放票 %d（超卖或少卖）"
              % (remain, s["ok"], total))
        ok = False
    if str(redis_remain) != str(remain):
        print("  [FAIL] Redis 余票 %s 与 DB %d 不一致" % (redis_remain, remain))
        ok = False
    if distinct != after_orders:
        print("  [FAIL] 订单号出现重复（%d 单 / %d 个不同订单号）" % (after_orders, distinct))
        ok = False
    print("  结论      : %s" % ("PASS 账目对平，无超卖" if ok else "FAIL 见上"))
    return 0 if ok else 1


# ============================ 子命令：cluster ============================


class Check:
    def __init__(self):
        self.items = []

    def add(self, name, ok, detail=""):
        self.items.append((name, ok, detail))
        print("  [%s] %-18s %s" % ("PASS" if ok else "FAIL", name, detail))

    def ok(self):
        return all(x[1] for x in self.items)


def cmd_cluster(args, client, env):
    nodes = [n.strip().rstrip("/") for n in args.nodes.split(",") if n.strip()]
    node_clients = [Client(n) for n in nodes]
    check = Check()

    print("\n=== 集群专项：%d 个节点 %s，出口 nginx %s ==="
          % (len(nodes), " / ".join(nodes), args.base))

    # ---------- 1. 负载均衡 ----------
    dist = {}
    for _ in range(40):
        st, _, hdrs = client.request("GET", "/api/cinema/list")
        addr = hdrs.get("x-upstream-addr", "unknown")
        dist[addr] = dist.get(addr, 0) + 1
    expect = {"127.0.0.1:8080", "127.0.0.1:8081"}
    spread = "  ".join("%s=%d" % (k, v) for k, v in sorted(dist.items()))
    check.add("负载均衡", len(dist) == 2 and expect.issubset(set(dist)), spread)

    # ---------- 2. JWT 跨实例 ----------
    # 注意：本项目 token 存在共享 Redis 且严格比对，同一账号再登录会顶掉旧 token（单点登录）。
    # 所以这里必须用两个不同账号，否则第二次登录会让第一次的 token 失效，误判成"跨实例失效"。
    second_user = args.second_user
    token = node_clients[0].login(args.user, args.password)
    st, data, _ = node_clients[1].request("GET", "/api/order/my?page=1&size=5", token=token)

    rev_token = node_clients[1].login(second_user, args.password)
    st2, data2, _ = node_clients[0].request("GET", "/api/order/my?page=1&size=5", token=rev_token)
    ok_jwt = data.get("code") == 200 and data2.get("code") == 200
    check.add("JWT 跨实例", ok_jwt,
              "%s 在A登录→B校验 %s，%s 在B登录→A校验 %s"
              % (args.user, data.get("code"), second_user, data2.get("code")))

    # ---------- 3. 余票缓存共享 ----------
    # 先把票池重置到 100 并清掉缓存，然后：node1 查一次回源建缓存，
    # node2 再查应该直接命中 Redis（全集群只回源 1 次）
    env.reset_stock(args.screening, args.date, args.type, args.cache_stock, args.price)
    env.redis_cli("DEL", "screening:stock:%d:%s:%s"
                  % (args.screening, args.date, args.type))
    hits_before = env.log_sql_hits(args.log_dir)
    r1 = node_clients[0].remain(args.screening, args.date, args.type, token)
    r2 = node_clients[1].remain(args.screening, args.date, args.type, token)
    hits_after = env.log_sql_hits(args.log_dir)
    db_hits = sum(max(0, v - hits_before.get(k, 0)) for k, v in hits_after.items())
    cached = env.redis_cli("GET", "screening:stock:%d:%s:%s"
                           % (args.screening, args.date, args.type))
    ok_cache = (r1.get("data") == r2.get("data") and cached not in (None, "")
                and db_hits == 1)
    check.add("缓存共享", ok_cache,
              "A 查=%s B 查=%s 回源=%d 次 Redis=%s"
              % (r1.get("data"), r2.get("data"), db_hits, cached))

    # ---------- 4. 幂等键跨实例 ----------
    env.clear_buy_state(args.screening, args.date, args.type)
    env.reset_stock(args.screening, args.date, args.type, 50, args.price)
    d1 = node_clients[0].buy(args.screening, args.date, args.type, 1, token, "幂等测试")
    d2 = node_clients[1].buy(args.screening, args.date, args.type, 1, token, "幂等测试")
    ok_dup = d1.get("code") == 200 and d2.get("code") == 1002
    check.add("幂等跨实例", ok_dup,
              "A 首次=%s，B 重放=%s(%s)" % (d1.get("code"), d2.get("code"), d2.get("message")))

    # ---------- 5. 跨实例并发不超卖 ----------
    env.clear_buy_state(args.screening, args.date, args.type)
    env.reset_stock(args.screening, args.date, args.type, args.stock, args.price)
    users = prepare_users(client, args.concurrency, args.user_prefix, args.user_pass)
    before_orders, _ = env.count_orders(args.screening, args.date)

    lock_seen = set()

    def worker(i):
        # 一半打 8080，一半打 8081，故意让并发的请求分散到两个 JVM
        node = node_clients[i % len(node_clients)]
        name, tok = users[i]
        try:
            data = node.buy(args.screening, args.date, args.type, 1, tok, name)
            # 失败响应里 data 是 null，必须先兜底再取 orderNo
            return data.get("code"), data.get("message"), (data.get("data") or {}).get("orderNo")
        except Exception as e:
            return -1, str(e), None

    results, wall = run_concurrent(args.concurrency, worker)
    success = [r for r in results if r and r[0] == 200]

    # 等 MQ 消费者落库：下单成功 ≠ 已入库，必须等 DB 追平再对账
    settled, waited = wait_for(
        lambda: env.count_orders(args.screening, args.date)[0] - before_orders >= len(success),
        timeout=args.settle_timeout)
    if settled:
        print("      落库完成: 等待 %.1fs（MQ 异步消费）" % waited)
    else:
        print("      [警告] 等待 %.0fs 仍有订单未落库，可能消息堆积/消费者异常" % waited)

    total, remain = env.stock_row(args.screening, args.date, args.type)
    after_orders, distinct = env.count_orders(args.screening, args.date)
    redis_remain = env.redis_cli("GET", "screening:stock:%d:%s:%s"
                                 % (args.screening, args.date, args.type))
    success = [r for r in results if r and r[0] == 200]
    fail_reasons = {}
    for r in results:
        if r and r[0] != 200:
            fail_reasons[r[1]] = fail_reasons.get(r[1], 0) + 1

    dist = {}
    for i, r in enumerate(results):
        if r and r[0] == 200:
            addr = nodes[i % len(nodes)]
            dist[addr] = dist.get(addr, 0) + 1
    spread = "  ".join("%s:%d" % (k.rsplit(":", 1)[-1], v) for k, v in sorted(dist.items()))

    ok_stock = (len(success) == args.stock
                and remain == total - len(success)
                and str(redis_remain) == str(remain)
                and distinct == after_orders)
    check.add("跨实例不超卖", ok_stock,
              "%d 人抢 %d 张 → 成功 %d，余 %d，订单 +%d（%s）"
              % (args.concurrency, args.stock, len(success), remain,
                 after_orders - before_orders, spread))
    if fail_reasons:
        print("      失败原因: %s" % "，".join("%s×%d" % (k, v) for k, v in fail_reasons.items()))

    # ---------- 6. 订单号全局唯一 ----------
    rows = env.sql(
        "SELECT COUNT(*), COUNT(DISTINCT order_no) FROM movie_order "
        "WHERE screening_id=%d AND show_date='%s'" % (args.screening, args.date))
    orders, uniq = int(rows[0][0]), int(rows[0][1])
    check.add("订单号唯一", orders == uniq, "%d 单 / %d 个不同订单号" % (orders, uniq))

    # ---------- 汇总 ----------
    passed = sum(1 for x in check.items if x[1])
    print("\n  集群检查：%d/%d 通过%s"
          % (passed, len(check.items), "" if check.ok() else "  ← 有失败项"))
    return 0 if check.ok() else 1


# ============================ 入口 ============================


def main():
    p = argparse.ArgumentParser(description="电影票系统压测 / 集群一致性校验")
    sub = p.add_subparsers(dest="cmd", required=True)

    def common(sp):
        sp.add_argument("--base", default="http://127.0.0.1:200",
                        help="请求入口，默认 nginx 200 端口")
        sp.add_argument("--screening", type=int, default=1, help="场次 id")
        sp.add_argument("--date", default="2026-11-01", help="放映日期")
        sp.add_argument("--type", default="普通票", help="票种")
        sp.add_argument("--price", type=int, default=4500, help="票价（分）")
        sp.add_argument("--user", default="demo", help="用于读接口的账号")
        sp.add_argument("--password", default="123456")
        sp.add_argument("--log-dir", default="logs", help="实例日志目录（统计回源次数）")
        sp.add_argument("--mysql-exe", default="mysql")
        sp.add_argument("--redis-cli", default="redis-cli")
        sp.add_argument("--db", default="movie")
        sp.add_argument("--db-user", default="root")
        sp.add_argument("--db-pass", default="123456")
        sp.add_argument("--settle-timeout", type=int, default=60,
                        help="等 MQ 异步落库的最长秒数（写场景/集群用）")

    r = sub.add_parser("read", help="读场景压测")
    common(r)
    r.add_argument("--concurrency", type=int, default=20)
    r.add_argument("--rounds", type=int, default=20)
    r.set_defaults(func=cmd_read)

    w = sub.add_parser("write", help="写场景压测（抢票不超卖）")
    common(w)
    w.add_argument("--concurrency", type=int, default=12)
    w.add_argument("--stock", type=int, default=10)
    w.add_argument("--user-prefix", default="load")
    w.add_argument("--user-pass", default="load123456")
    w.set_defaults(func=cmd_write)

    c = sub.add_parser("cluster", help="集群一致性专项")
    common(c)
    c.add_argument("--nodes", default="http://127.0.0.1:8080,http://127.0.0.1:8081")
    c.add_argument("--second-user", default="demo02",
                   help="跨实例验证 JWT 用的第二个账号（不能与 --user 相同，否则会顶号）")
    c.add_argument("--concurrency", type=int, default=12)
    c.add_argument("--stock", type=int, default=10)
    c.add_argument("--cache-stock", type=int, default=100,
                   help="验证缓存共享时用的票池大小（要明显大于 0，便于观察）")
    c.add_argument("--user-prefix", default="cluster")
    c.add_argument("--user-pass", default="cluster123456")
    c.set_defaults(func=cmd_cluster)

    args = p.parse_args()
    client = Client(args.base)
    env = Env(args)
    try:
        return args.func(args, client, env)
    except KeyboardInterrupt:
        print("\n已中断")
        return 130
    except Exception as e:
        print("\n执行出错: %s" % e)
        return 2


if __name__ == "__main__":
    sys.exit(main())
