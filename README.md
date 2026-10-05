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

## 当前状态

当前为 **准备工程（骨架）** 阶段，P2 已回退为待写：

- [x] Maven 多模块工程与依赖管理
- [x] 配置文件（MySQL / Redis / Spring Cache / Redisson / RabbitMQ / JWT）
- [x] Docker Compose 与 SQL 初始化脚本（9 张表 + 演示数据，**不含账号**）
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
>
> 账号需自行注册（`sql/init.sql` 不含账号）；需要管理端权限时手动绑定 `ADMIN` 角色并**重新登录**，
> 详见 [开发计划.md](开发计划.md) 第 7.3 节。

## 说明

- 当前环境为 JDK 8，故采用 Spring Boot 2.7.x；如需升级 JDK 17+ 可切换 Spring Boot 3.x（注意 MP 的 `-spring-boot3` 依赖差异）。
- RabbitMQ 消费端由 `rabbitmq.consumer.enabled` 控制（dev 已开启）；业务队列由 `RabbitMqConfig` 自动声明。
