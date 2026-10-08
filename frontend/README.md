# 康轨 KangRail · 前端

纯 HTML / CSS / JavaScript，**只调用后端真实接口，没有任何本地模拟数据**。

## 运行

nginx 已把站点根目录指向本目录，直接访问：

```
http://localhost:200/
```

> ⚠️ 前端能打开**不等于**接口能用。前端页面由 nginx 直接托管，而 `/api/**` 要由 nginx 反代到后端 8080。
> **后端没启动时页面照常显示，但所有接口都是 502。** 先启动后端：

```powershell
& 'C:\Users\王照康\.jdks\jdk-17.0.2\bin\java.exe' -jar server\target\server-1.0.0-SNAPSHOT.jar
```

## 页面路由 = 后端接口路径（不带 #）

nginx 按请求头 `Accept` 分流：浏览器直接访问返回页面，前端 `fetch` 调用返回 JSON。

| 页面地址 | 页面 | 调用的后端接口 |
| --- | --- | --- |
| `/` | 首页 / 查询入口 | — |
| `/api/auth/login` | 登录 / 注册 | `POST /api/auth/register`、`POST /api/auth/login` |
| `/api/user/me` | 个人中心 | `GET /api/user/me`、`POST /api/user/logout` |
| `/api/station/list` | 车站列表 | `GET /api/station/list` |
| `/api/train/page` | 车次列表（支持 `?from=&to=&date=` 区间查询） | `GET /api/train/page`（区间模式再用 `GET /api/station/list`） |
| `/api/train/{id}` | 车次详情 / 时刻表（含**经停站**） | `GET /api/train/{id}` |
| `/api/ticket/remain` | 余票查询 | `GET /api/ticket/remain` |
| `/api/ticket/buy` | 购票下单 | `POST /api/ticket/buy` |
| `/api/order/my` | 我的订单 | `GET /api/order/my`、`GET /api/order/{orderNo}`、`POST /api/order/{orderNo}/cancel` |
| `/api/admin/ping` | **管理控制台**（默认打开「放票」页） | `GET /api/admin/ping` |
| `/api/admin/stock/publish` | 管理台 · 放票 / 库存预热 | `POST /api/admin/stock/publish` |
| `/api/admin/train` | 管理台 · 车次维护 | `PUT /api/admin/train/{trainId}` |
| `/api/admin/station` | 管理台 · 车站维护 | `PUT /api/admin/station/{stationId}` |
| `/console` | 接口联调控制台（调试页，非后端接口） | 全部 17 个接口 |

## 管理控制台（P2 完成后已可用）

进入 `/api/admin/ping`，左侧五个功能页：

| 功能页 | 做什么 | 对应接口 | 对应任务 |
| --- | --- | --- | --- |
| **放票 / 库存预热** | 给「车次 + 日期 + 席别」放票，写 DB 并预热 Redis；已存在则 total/remain 累加 | `POST /api/admin/stock/publish` | P2-2 |
| **车次维护** | 改车次名称、启用/停运（会自动清 `railway:cache:train::id` 缓存） | `PUT /api/admin/train/{trainId}` | P2-4 |
| **车站维护** | 改站名、城市（清 station 缓存） | `PUT /api/admin/station/{stationId}` | P2-4 |
| 余票查询 | 查 Redis 里的余票 | `GET /api/ticket/remain` | P2 |
| 接口调用日志 | 本页发起的调用记录 | — | — |

**需要用 `admin / admin123` 登录**（`demo` 是普通用户，所有管理接口都会 403）。

联调顺序建议：

```
登录 admin → 放票（记下返回的余票）→ 放票后查余票 → 两次放票看是否累加
          → 车次维护改名 → 回车次详情看是否立刻生效（验证缓存清理）
          → 退出登录，用 demo 再访问管理台，应看到 403 提示
```

## 登录与鉴权

- 登录成功后，后端返回的 JWT 存在 `localStorage['kr_token']`；
- 之后所有请求自动带 `Authorization: Bearer <token>`；
- 后端返回 401 时前端会清除本地 token 并提示重新登录；
- **角色写在 JWT 里**，改了角色必须重新登录才生效；
- token 只能在**页面发起的请求**里带上，浏览器地址栏直接打开接口不会带，会看到 401 JSON——这是正常现象。

## ⚠️ 三个「假成功」陷阱（前端已做防护）

后端有些接口目前是 TODO 占位，**返回 HTTP 200 + code 200，但什么都没做**。
前端不能只看状态码，必须检查返回内容：

| 接口 | 后端现状 | 返回 | 前端的处理 |
| --- | --- | --- | --- |
| `POST /api/ticket/buy` | `TicketServiceImpl.buy()` → `return null` | `200` + `data: null` | 判断 `data.orderNo` 是否存在，没有就显示「**下单未生效**」并说明原因 |
| `POST /api/order/{orderNo}/cancel` | `OrderServiceImpl.cancel()` 空实现 | `200` + 什么都不改 | 调用后**重新查一次订单**，比对状态有没有变；没变就提示「状态没变 —— cancel() 还是 TODO」 |
| `POST /api/admin/stock/publish` | 已实现，但 `publish()` 最后 `return 0` | `200` + `data: 0` | 正常显示，但 `data=0` 时补一句说明 |

> 这三点不是前端多此一举：**HTTP 200 只代表「请求被正确处理了」，不代表「业务成功了」**。
> 本项目约定只有 401 用真实状态码，其余业务错误都是 HTTP 200 + body 里的 `code`。

## 文件结构

```
frontend/
├── index.html        页面骨架（导航 / 页脚 / 弹窗容器）
├── css/style.css     设计系统
└── js/
    ├── api.js        API 客户端（17 个接口 + 调用日志 + 接口清单）
    ├── ui.js         组件：提示、弹窗、登录、支付提示、时刻表
    ├── pages.js      页面：首页/登录/个人中心/车站/车次/详情/余票/下单/订单/管理台/控制台
    └── app.js        历史模式路由（路径与接口一致）+ 顶部栏
```

## 后端的待实现项（前端已按现状处理）

| 功能 | 现状 |
| --- | --- |
| `POST /api/ticket/buy` 下单 | **TODO（P3）**：`buy()` 是 `return null` 占位，会返回 200 但 data 为空 |
| `POST /api/order/{orderNo}/cancel` 取消 | **TODO（P3/P4）**：空实现，不报错也不改状态 |
| 支付 | 后端无支付接口（P4），前端只做提示，不做本地模拟 |
| 退票 | 后端无接口（P4），前端只提示 |
| `POST /api/admin/stock/publish` 的返回值 | 已实现但最后 `return 0`（P2-2 收尾时补 `return remain` 即可） |

## 联调前的自检

```powershell
# 一键把前端会用到的接口按顺序全跑一遍（走 nginx，和浏览器同一条链路）
pwsh -File .\scripts\check-api-flows.ps1
```
