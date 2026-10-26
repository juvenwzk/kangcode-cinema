# 影票 MovieTicket · 电影票购票系统

> 包名：`com.kangcode` ｜ Maven 坐标：`com.kangcode:kangcode-railway-ticket`
> 前端：Vue 3 + Element Plus ｜ 后端：Spring Boot 2.7 + MyBatis-Plus + Redis + RabbitMQ

一个面向**高并发场景**的购票系统示例，重点演练两件事：**余票扣减不超卖**、**异步链路的最终一致**。

---

## 技术栈

| 层 | 技术 | 用途 |
| --- | --- | --- |
| 语言 | Java 17 | — |
| 框架 | Spring Boot 2.7 | Web / IoC / 事务 |
| 认证 | JWT + 拦截器 + Redis | 登录签发 token，拦截器校验，支持踢下线 |
| ORM | MyBatis-Plus | 单表 CRUD、分页、Lambda 条件构造器 |
| 数据库 | MySQL 8 | 订单、库存、影院场次 |
| 缓存 | Spring Cache + StringRedisTemplate | 注解式缓存、余票缓存 |
| 分布式锁 | Redisson | 按「场次 + 日期 + 票种」加锁 |
| 原子操作 | Redis Lua | 判断 + 扣减合成一步 |
| 消息队列 | RabbitMQ | 异步落库、延时关单 |
| 前端 | Vue 3 + Vite + Element Plus + axios | 首页 / 影院 / 场次 / 订单 |
| 监控 | Actuator + MDC traceId | 健康检查、全链路日志 |

---

## 模块结构

```
common        常量（Redis Key、MQ 队列名、错误码）
pojo          数据对象：entity / dto / vo
framework     技术组件：JWT 拦截器、Redis/Redisson、RabbitMQ、MyBatis-Plus 配置、全局异常、事务工具
server        业务与启动：controller / service / mapper / mq / task
frontend-vue  Vue3 前端（Vite 工程）
docker        Docker Compose：MySQL / Redis / RabbitMQ
sql           建表脚本 + 数据迁移脚本
scripts       压测、接口自检、数据库查询脚本
docs          项目文档（项目地图 / 技术点详解 / 面试问答）
```

---

## 核心设计

### 1. 防超卖四层防护

| 层 | 手段 | 作用 |
| --- | --- | --- |
| 1 | 幂等键 `order:dup:{userId}:{screeningId}:{date}`，SETNX 30s | 挡住同一用户重复提交 |
| 2 | Redisson 锁 `screening:lock:{id}:{date}:{type}` | 锁粒度 = 一行库存 |
| 3 | Redis Lua 脚本 | 判断余票 + 扣减，原子完成 |
| 4 | `UPDATE ... WHERE remain >= n` | DB 兜底，绝不扣成负数 |

### 2. 缓存三兄弟

| 问题 | 解法 | 参数 |
| --- | --- | --- |
| 穿透 | 空值缓存 `EMPTY` | 60s |
| 击穿 | 互斥重建锁 `{key}#rebuild` | 10s + 抢不到时自旋读缓存 |
| 雪崩 | TTL 抖动 | 600s + random(0~60) |

实测（50 个请求查不存在的场次）：**关掉任一招，DB 访问从 5 次涨到 54 次**。

### 3. 异步与一致性

- 下单只做「Redis 预扣 + 发 MQ」，订单落库交给消费者 → 接口毫秒级返回
- 延时关单：`TTL 30 分钟 + 死信队列`，不轮询数据库
- 库存释放幂等：`stock_release_log` 用 `order_no` 唯一键挡住 MQ 重投

### 4. 订单状态机

```
0 待支付 ──支付──► 1 已支付 ──退票──► 4 已退票
   │                   │
   │取消/超时          │（出票，未实现）
   ▼                   ▼
 2 已取消            3 已出票
```

所有流转用**带源状态条件的 UPDATE + 影响行数判断**，避免「先查后改」的并发窗口。

---

## 数据模型

| 表 | 含义 | 关键字段 |
| --- | --- | --- |
| `cinema` | 影院 | cinema_code、cinema_name、city |
| `screening` | 场次 | screening_no、movie_name、cinema_id、hall_id、start_time、end_time |
| `hall` | 影厅 | screening_id、cinema_id、hall_no、open_time、close_time |
| `screening_seat` | 票种库存（热点表） | screening_id、show_date、ticket_type、total、remain、price |
| `movie_order` | 订单 | order_no、screening_id、show_date、ticket_type、status |
| `movie_order_item` | 订单明细 | order_id、passenger_name、id_card_no、seat_no |

---

## 快速开始

### 1. 启动中间件

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| MySQL 8 | `localhost:3306`，库 `railway` | 执行 `sql/init.sql` 初始化 |
| Redis | `localhost:6379` | 无密码（见 `application-dev.yml`） |
| RabbitMQ | 见 `application-dev.yml` | 队列由 `RabbitMqConfig` 自动声明 |

```bash
# 全新库
mysql -uroot -p --default-character-set=utf8mb4 railway < sql/init.sql

# 从旧版（铁路口径）迁移，保留数据
mysql -uroot -p --default-character-set=utf8mb4 railway < sql/migrate-to-movie.sql
```

> ⚠️ 用 mysql 客户端必须加 `--default-character-set=utf8mb4`，否则中文写入会报 `Data too long for column`。

### 2. 启动后端

```bash
mvn -pl server -am package -DskipTests
java -jar server/target/server-1.0.0-SNAPSHOT.jar --server.port=8080
```

### 3. 启动前端

```bash
cd frontend-vue
npm install
npm run dev          # http://localhost:5173
```

Vite 已配代理：`/api` → `http://localhost:8080`。

生产部署：`npm run build` 生成 `dist/`，nginx 指向它，并加 `try_files $uri $uri/ /index.html`（history 路由必需）。

---

## 主要接口

| 方法 | 路径 | 说明 | 需登录 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` `/api/auth/register` | 登录 / 注册 | 否 |
| GET | `/api/cinema/list` | 影院列表 | **否**（浏览放行） |
| GET | `/api/screening/page` | 场次分页 | **否**（浏览放行） |
| GET | `/api/screening/{id}` | 场次详情（含影厅） | 否 |
| GET | `/api/screening/{id}/seats?showDate=` | 票种 / 票价 / 余票 | 否 |
| GET | `/api/screening/{id}/dates` | 已放票日期 | 否 |
| GET | `/api/ticket/remain` | 余票查询 | 是 |
| POST | `/api/ticket/buy` | 下单 | 是 |
| GET | `/api/order/my` | 我的订单 | 是 |
| POST | `/api/order/{no}/cancel` | 取消（仅待支付） | 是 |
| POST | `/api/order/{no}/refund` | 退票（已支付/已出票） | 是 |
| POST | `/api/pay/callback` | 支付回调（模拟） | 否 |

---

## 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `demo` | `123456` | 普通用户 |
| `admin` | `admin123` | 管理员 |

---

## 测试与压测

```bash
# 单元测试（45 个，不需要中间件）
mvn -pl server -am test -Dtest='P*Test,ResultTest,JwtUtilsTest' -DfailIfNoTests=false

# 读场景压测
python scripts/loadtest.py read --concurrency 20 --rounds 20

# 写场景：12 个用户抢 10 张票
python scripts/loadtest.py write --concurrency 12 --stock 10 --date 2026-11-01
```

实测：

| 指标 | 数值 |
| --- | --- |
| 不超卖 | 12 / 15 人抢 10 张，账目对平 |
| 缓存挡库 | 1000 次余票查询 → 4 次打到 MySQL |
| 读吞吐 | 20 并发 1430 QPS / P99 32ms |

---

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/00-项目地图.md](docs/00-项目地图.md) | 技术栈、模块、数据模型、一次下单完整链路 |
| [docs/01-技术点详解.md](docs/01-技术点详解.md) | 13 个技术点：代码位置 → 为什么 → 不这么做会怎样 |
| [docs/02-面试问答.md](docs/02-面试问答.md) | 高频追问、STAR 故事、项目短板 |

---

## 已知短板

| 项 | 现状 | 计划 |
| --- | --- | --- |
| 真·选座 | 目前是「票种 + 数量」，`seat_no` 预留未用 | 座位表 + 锁座 + 按座位并发控制 |
| 事务边界 | `AfterCommit` 工具类已实现，**尚未接入 4 个发送点** | 接入；进一步上本地消息表 |
| 集成测试 | 只有 45 个单元测试 | 用 Testcontainers 覆盖下单→支付→退票、消息重投 |
