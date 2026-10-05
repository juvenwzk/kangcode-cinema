# 康轨 KangRail · 前端

纯 HTML / CSS / JavaScript，**只调用后端真实接口，没有任何本地模拟数据**。

## 运行

nginx 已把站点根目录指向本目录，直接访问：

```
http://localhost:200/
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
| `/api/train/{id}` | 车次详情 / 时刻表 | `GET /api/train/{id}` |
| `/api/ticket/remain` | 余票查询 | `GET /api/ticket/remain` |
| `/api/ticket/buy` | 购票下单 | `POST /api/ticket/buy` |
| `/api/order/my` | 我的订单 | `GET /api/order/my`、`GET /api/order/{orderNo}`、`POST /api/order/{orderNo}/cancel` |
| `/api/admin/ping` | 管理控制台 | `GET /api/admin/ping`（+ 车次库、余票查询） |
| `/console` | 接口联调控制台（调试页，非后端接口） | 全部 12 个接口 |

## 登录与鉴权

- 登录成功后，后端返回的 JWT 存在 `localStorage['kr_token']`；
- 之后所有请求自动带 `Authorization: Bearer <token>`；
- 后端返回 401 时前端会清除本地 token 并提示重新登录；
- token 只能在**页面发起的请求**里带上，浏览器地址栏直接打开接口不会带，会看到 401 JSON——这是正常现象。

## 文件结构

```
frontend/
├── index.html        页面骨架（导航 / 页脚 / 弹窗容器）
├── css/style.css     设计系统
└── js/
    ├── api.js        API 客户端（纯真实接口 + 调用日志 + 接口清单）
    ├── ui.js         组件：提示、弹窗、登录、支付提示、时刻表
    ├── pages.js      页面：首页/登录/个人中心/车站/车次/详情/余票/下单/订单/管理台/控制台
    └── app.js        历史模式路由（路径与接口一致）+ 顶部栏
```

## 后端的待实现项（前端已按现状处理）

| 功能 | 现状 |
| --- | --- |
| `POST /api/ticket/buy` 下单 | 后端 `TicketServiceImpl.buy` 还是 TODO（P3），调用会失败，页面会提示 |
| 支付 | 后端无支付接口（P4），前端只做提示，不做本地模拟 |
| `POST /api/order/{orderNo}/cancel` | 接口存在但实现为 TODO，调用不会真正改状态 |
| 放票 / 改库存 | 后端未提供，管理控制台只读 |
| 经停站 | `GET /api/train/{id}` 已返回 `stations`，但后端组装逻辑仍是 TODO |
