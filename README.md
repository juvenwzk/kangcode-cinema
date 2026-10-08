# 康轨 KangRail · 高并发铁路购票系统

> 包名：`com.kangcode` ｜ Maven 坐标：`com.kangcode:kangcode-railway-ticket`

KangRail（康轨）是一个面向高并发场景的铁路购票系统示例工程，重点演练 **缓存 + 分布式锁 + 消息队列** 的经典削峰/一致性架构。

## 技术栈

| 分类 | 选型 | 用途 |
| --- | --- | --- |
| 基础框架 | Spring Boot 2.7（JDK 8） | 应用容器 |
| 认证鉴权 | JWT（jjwt）+ Spring MVC 拦截器 | 登录签发 token，拦截器校验，`UserContext` 存放当前用户 |
| ORM | MyBatis-Plus | 数据访问、分页、乐观锁 |
| 数据库 | MySQL 8.0 | 主数据存储 |
| 缓存 | Spring Cache（Redis 实现）+ StringRedisTemplate | 注解式缓存读接口、余票缓存 |
| 分布式锁 | Redisson | 购票加锁、Lua 原子扣减 |
| 消息队列 | RabbitMQ | 下单/支付/关单异步化、削峰 |

## 模块结构

```
kangcode-railway-ticket
├── common                 # 通用：统一返回 Result、错误码、异常、常量
├── pojo                   # 数据对象：entity（表实体）、dto（入参）、vo（出参）
├── framework              # 技术组件：JWT+拦截器、Spring Cache、Redisson、RabbitMQ、MyBatis-Plus 配置、全局异常
├── server                 # 业务与启动：controller/service/mapper、启动类与配置
├── docker                 # Docker Compose：MySQL / Redis / RabbitMQ
└── sql                    # 建库建表 + 初始数据
```

## 核心链路设计（骨架阶段已预留）

1. **余票查询**：优先读 Redis（`ticket:stock:{trainId}:{date}:{seatType}`），缓存 miss 时加锁查库回填；车站/车次详情用 Spring Cache 注解缓存，定时对账兜底。
2. **购票下单**：Redisson 分布式锁 + Redis Lua 原子预扣 → 生成订单号并发送 RabbitMQ 下单消息 → 消费端幂等落库订单（待支付）→ 发送支付超时延时消息（TTL + 死信队列）。
3. **订单超时关单**：延时消息（TTL + 死信队列）触发，校验未支付则取消订单并释放库存（补偿消息保证最终一致）。
4. **安全认证**：注册/登录签发 JWT，Token 写入 Redis 支持续期与踢人；接口默认需登录，`/api/auth/**` 放行。

## 快速开始

### 1. 启动中间件

```bash
cd docker
docker compose up -d
```

启动内容：MySQL（自动执行 `sql/init.sql` 建库建表）、Redis、RabbitMQ（5672 + 管理台 15672）。

> 若本机已有中间件，可跳过本步，直接修改 `server` 模块下的 `application-dev.yml`。

### 2. 构建与运行

要求：JDK 8+、Maven 3.6+。

```bash
mvn clean package -DskipTests
java -jar server/target/server-1.0.0-SNAPSHOT.jar
```

默认端口 `8080`，默认激活 `dev` 环境；切换环境：

```bash
java -jar server/target/server-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

> 本地开发注意事项：
> - dev 环境 MySQL：`localhost:3306`，`root/123456`，库名 `railway`；
> - dev 环境 Redis：与 youmall 同一套远程服务 `43.166.0.94:6379`，密码 `123456`（见 `application-dev.yml`）；
> - dev 环境 RabbitMQ：与 youmall 同一套远程服务 `43.166.0.94:5672`，`admin/123456`，管理台 `http://43.166.0.94:15672`（见 `application-dev.yml`）。

### 3. 常用接口（骨架）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录（返回 JWT） |
| GET | `/api/train/page` | 车次分页 |
| GET | `/api/ticket/remain` | 余票查询 |
| POST | `/api/ticket/buy` | 购票下单 |
| GET | `/api/order/my` | 我的订单 |

## 开发计划

详细的分阶段计划见 [开发计划.md](开发计划.md)。

## 业务流程图（9 张）

从分层架构到数据一致性，**从零看懂业务层**。图是真实 PNG 文件，放在 `docs/diagrams/` 目录下：

| 图 | 内容 | 出现在 |
| --- | --- | --- |
| 图 1 | 系统分层架构与依赖方向 | 开发计划 4.1 · P0 |
| 图 2 | 一次 HTTP 请求的完整生命周期 | 开发计划 4.2 · P0 · P1 |
| 图 3 | 登录鉴权全流程 | 开发计划 4.3 · P0 · P1 |
| 图 4 | 余票查询与「缓存三兄弟」 | 开发计划 4.4 · P0 · P2 |
| 图 5 | **购票主流程（核心）** | 开发计划 4.5 · P3 |
| 图 6 | 订单状态机 | 开发计划 4.6 · P4 |
| 图 7 | MQ 异步链路与超时关单 | 开发计划 4.7 · P3 · P4 |
| 图 8 | 取消订单与退票 | 开发计划 4.8 · P4 |
| 图 9 | 数据一致性全景 | 开发计划 4.9 · P0 · P2 |

> 📊 **网页版 Markdown（语雀 / 飞书 / Notion）不解析相对路径的图片**，那边看不到这些图。
> 三个办法任选：
> 1. **看 PDF（最省事）**：`docs/diagrams/康轨业务流程图.pdf` —— 9 张图合成一个 10 页 PDF，任何阅读器都能开，
>    也可以直接上传到语雀当附件；
> 2. **浏览器画廊**：双击 `docs/diagrams/index.html`，一次看全 9 张图并带讲解；
> 3. **直接看 PNG**：打开 `docs/diagrams/` 目录逐个双击。
>
> 想改图：编辑 [docs/diagrams/make_diagrams.py](docs/diagrams/make_diagrams.py) 后执行
> `python docs\diagrams\make_diagrams.py`（改完图后可再跑 `python docs\diagrams\make_pdf.py` 重新生成 PDF）。

## 当前状态

当前为 **准备工程（骨架）** 阶段，P2 已回退为待写：

- [x] Maven 多模块工程与依赖管理
- [x] 配置文件（MySQL / Redis / Spring Cache / Redisson / RabbitMQ / JWT）
- [x] Docker Compose 与 SQL 初始化脚本（9 张表 + 演示数据 + **2 个演示账号**）
- [x] 实体 / Mapper / Service / Controller 骨架
- [x] 认证与权限（JWT + 拦截器 + `UserContext`，样例已跑通）
- [x] 余票缓存（空值防穿透 / 互斥重建防击穿 / TTL 抖动防雪崩）
- [ ] P2-1 车次经停站组装
- [ ] P2-2 放票与库存预热接口
- [ ] P2-3 定时对账任务
- [ ] P2-4 管理端写接口 + 缓存清理（可选）
- [ ] P3 分布式锁 + Lua 原子扣减 + MQ 异步落库
- [ ] P4 支付回调 / 超时关单 / 取消释放库存
- [ ] P5 压测与性能治理
- [ ] P6 可观测性与上线

> 项目已初始化 git：`main` 为准备工程基线，`impl-baseline` 保存了回退前的 P2 完整实现，
> 需要参考时用 `git diff main impl-baseline` 或 `git show impl-baseline:<文件路径>`。

## 演示账号

`sql/init.sql` 会插入这两个账号，与前端登录页提示的一致：

| 账号 | 密码 | 角色 | 管理端权限 |
| --- | --- | --- | --- |
| `demo` | `123456` | USER | ❌ 403 |
| `admin` | `admin123` | ADMIN | ✅ 可访问 `/api/admin/**` |

> 也可以自己注册新账号（只会有 USER 角色）；需要 ADMIN 时手动绑定角色并**重新登录**
> （角色写在 JWT 里），详见 [开发计划.md](开发计划.md) 第 8.3 节。

## 启动顺序（重要）

本项目前端由 **nginx（200 端口）** 托管，`/api` 请求由 nginx **反代到后端 8080**：

```
浏览器 → nginx:200 ─┬─ 静态文件 → frontend/ 目录
                    └─ /api/**  → 反向代理 → Spring Boot:8080
```

所以**必须先启动后端，前端才能正常调接口**，否则页面能打开但所有接口都返回 **502 Bad Gateway**。

```powershell
# 1. 先启动后端（8080）
java -jar server\target\server-1.0.0-SNAPSHOT.jar

# 2. nginx 若没在跑，启动它（nginx.conf 里 proxy_pass 必须指向 8080）
cd C:\nginx-1.30.0\nginx-1.30.0
.\nginx.exe

# 3. 访问前端
#    http://localhost:200/
```

> 502 的排查方法见 [开发计划.md](开发计划.md) 第 10.2 节和「坑 10」。

## 说明

- 当前环境为 JDK 8，故采用 Spring Boot 2.7.x；如需升级 JDK 17+ 可切换 Spring Boot 3.x（注意 MP 的 `-spring-boot3` 依赖差异）。
- RabbitMQ 消费端由 `rabbitmq.consumer.enabled` 控制（dev 已开启）；业务队列由 `RabbitMqConfig` 自动声明。
