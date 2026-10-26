"""
KangRail 压测脚本（不依赖 JMeter）

用法：
  python scripts/loadtest.py read  --concurrency 100 --rounds 20
  python scripts/loadtest.py write --concurrency 12  --stock 10

  read  ：反复打余票查询接口，测 QPS / P99（用来观察缓存效果）
  write ：N 个用户并发抢 M 张票，验证不超卖

前置：
  1. 应用跑在 http://localhost:8080
  2. 写场景需要 tester1..testerN 这些用户（下面这段注册一次即可）：
       for i in $(seq 1 20); do
         curl -s -X POST http://localhost:8080/api/auth/register \
           -H "Content-Type: application/json" \
           -d "{\\"username\\":\\"tester$i\\",\\"password\\":\\"123456\\"}" > /dev/null
       done
  3. TRAVEL_DATE 必须是未来日期（放票接口会拒绝历史日期）
"""
import argparse
import json
import statistics
import threading
import time
import urllib.parse
import urllib.request

BASE = "http://localhost:8080"
TRAIN_ID = 1
TRAVEL_DATE = "2026-12-20"
SEAT_TYPE = "二等座"


def _post(path, body, token=None):
    req = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))


def _get(path, token=None):
    req = urllib.request.Request(BASE + path)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))


def login(username, password):
    r = _post("/api/auth/login", {"username": username, "password": password})
    if not r.get("data"):
        raise RuntimeError("登录失败：%s" % r)
    return r["data"]["token"]


def _percentile(costs, p):
    """取 P 分位耗时（毫秒）"""
    if not costs:
        return 0.0
    s = sorted(costs)
    idx = min(len(s) - 1, int(len(s) * p / 100.0))
    return s[idx]


def run_read(concurrency, rounds, travel_date, train_id=TRAIN_ID):
    token = login("demo", "123456")
    qs = urllib.parse.urlencode({"trainId": train_id, "travelDate": travel_date, "seatType": SEAT_TYPE})
    url = "/api/ticket/remain?" + qs

    costs, errors = [], []
    lock = threading.Lock()
    barrier = threading.Barrier(concurrency)

    def worker():
        barrier.wait()
        for _ in range(rounds):
            t0 = time.perf_counter()
            try:
                _get(url, token)
                dt = (time.perf_counter() - t0) * 1000
                with lock:
                    costs.append(dt)
            except Exception as e:
                with lock:
                    errors.append(type(e).__name__)

    threads = [threading.Thread(target=worker) for _ in range(concurrency)]
    t_start = time.perf_counter()
    for th in threads:
        th.start()
    for th in threads:
        th.join()
    elapsed = time.perf_counter() - t_start

    total = len(costs) + len(errors)
    print("=" * 56)
    print("读场景：并发 %d，每人 %d 次，总请求 %d" % (concurrency, rounds, total))
    print("  总耗时      : %.2f s" % elapsed)
    print("  QPS         : %.0f" % (total / elapsed if elapsed else 0))
    print("  平均耗时    : %.2f ms" % (statistics.mean(costs) if costs else 0))
    print("  P95 / P99   : %.2f / %.2f ms" % (_percentile(costs, 95), _percentile(costs, 99)))
    print("  错误率      : %.2f%%  %s" % (100.0 * len(errors) / total if total else 0, errors[:5]))
    print("=" * 56)


def run_write(concurrency, stock, travel_date):
    admin = login("admin", "admin123")
    # 注意：放票是「增量」而不是「覆盖」——想要干净的库存，先重置：
    #   pwsh -File .\scripts\db-query.ps1 "UPDATE train_seat SET total=0, remain=0 WHERE train_id=1 AND travel_date='<日期>'"
    pub = _post("/api/admin/stock/publish",
                {"trainId": TRAIN_ID, "travelDate": travel_date,
                 "seatType": SEAT_TYPE, "count": stock}, admin)
    print("放票 %d 张（%s）→ %s" % (stock, travel_date, pub))

    tokens = []
    for i in range(1, concurrency + 1):
        try:
            tokens.append(login("tester%d" % i, "123456"))
        except Exception:
            pass
    print("拿到 %d 个用户 token" % len(tokens))

    results, lock = [], threading.Lock()
    barrier = threading.Barrier(len(tokens))

    def worker(t):
        barrier.wait()
        try:
            r = _post("/api/ticket/buy",
                      {"trainId": TRAIN_ID, "travelDate": travel_date,
                       "seatType": SEAT_TYPE, "ticketCount": 1}, t)
            code = r.get("code")
        except Exception as e:
            code = "EX:" + type(e).__name__
        with lock:
            results.append(code)

    threads = [threading.Thread(target=worker, args=(t,)) for t in tokens]
    for th in threads:
        th.start()
    for th in threads:
        th.join()

    ok = results.count(200)
    coding = {}
    for c in results:
        coding[c] = coding.get(c, 0) + 1

    demo = login("demo", "123456")
    remain = _get("/api/ticket/remain?" + urllib.parse.urlencode(
        {"trainId": TRAIN_ID, "travelDate": travel_date, "seatType": SEAT_TYPE}), demo)["data"]

    print("=" * 56)
    print("写场景：%d 个用户并发抢 %d 张票（%s）" % (len(tokens), stock, travel_date))
    print("  返回码分布  : %s" % coding)
    print("  成功下单    : %d 次" % ok)
    print("  抢完余票    : %s" % remain)
    balanced = (ok + remain == stock) and remain >= 0
    print("  账目核对    : %d(成功) + %s(余票) = %d(库存) → %s"
          % (ok, remain, stock, "平" if balanced else "不平"))
    print("  结论        : %s" % ("✅ 不超卖，通过" if balanced else "❌ 账目不平，可能超卖"))
    if coding.get(1000):
        print("  说明        : %d 个请求返回 1000 = 抢锁等待超时，" % coding[1000])
        print("                属于正常竞争失败（少卖，不会超卖），不是 bug。")
        print("                想减少它可调大 TicketServiceImpl.LOCK_WAIT_SECONDS。")
    if coding.get(1002) == len(results) and results:
        print("  说明        : 全部 1002 = 这批用户 30 秒内刚下过单，被幂等键挡住了。")
        print("                换个 --date（幂等键里带日期）或者换一批用户即可。")
    print("=" * 56)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["read", "write"])
    parser.add_argument("--concurrency", type=int, default=100)
    parser.add_argument("--rounds", type=int, default=20)
    parser.add_argument("--stock", type=int, default=10)
    parser.add_argument("--date", default=TRAVEL_DATE,
                        help="乘车日期（必须是未来日期）。重复压测时换个日期就能绕开幂等键")
    parser.add_argument("--train", type=int, default=TRAIN_ID,
                        help="车次 ID（默认 1）。做「缓存穿透」实验时用不存在的车次，如 9999")
    parser.add_argument("--base", default=BASE,
                        help="后端地址（默认 http://localhost:8080）；换端口时用，如 http://localhost:8081")
    args = parser.parse_args()

    BASE = args.base.rstrip("/")

    if args.mode == "read":
        run_read(args.concurrency, args.rounds, args.date, args.train)
    else:
        run_write(args.concurrency, args.stock, args.date)
