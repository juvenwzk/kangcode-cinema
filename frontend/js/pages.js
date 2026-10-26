/* ==========================================================================
   页面渲染 —— 全部依赖后端真实接口，没有任何本地模拟数据
   ========================================================================== */
(function () {
  'use strict';

  var esc = UI.esc;
  var app = document.getElementById('app');

  /* 席别是业务常量（后端 train_seat.seat_type 的取值），不是模拟数据 */
  var SEAT_TYPES = {
    G: ['商务座', '一等座', '二等座'],
    D: ['商务座', '一等座', '二等座'],
    Z: ['软卧', '硬卧', '硬座'],
    T: ['软卧', '硬卧', '硬座'],
    K: ['硬卧', '硬座', '无座']
  };
  var TYPE_NAME = { G: '高铁 G', D: '动车 D', Z: '直达 Z', T: '特快 T', K: '快速 K' };

  /* 首页快捷入口用的线性图标（内联 SVG，和顶部 logo 同一风格） */
  var ICON = {
    train: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="3" width="14" height="13" rx="3"/><path d="M5 10h14M9 3v7M15 3v7M8 19l-2 2M16 19l2 2M9 16v3M15 16v3"/></svg>',
    station: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s7-6.4 7-11a7 7 0 1 0-14 0c0 4.6 7 11 7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>',
    ticket: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z"/><path d="M12 7v10"/></svg>',
    order: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3h9l4 4v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z"/><path d="M14 3v5h5M8 13h8M8 17h5"/></svg>',
    user: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>',
    admin: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l8 4v6c0 4.4-3.4 7.5-8 8-4.6-.5-8-3.6-8-8V7z"/><path d="M9 12l2 2 4-4"/></svg>',
    console: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9l3 3-3 3M13 15h4"/></svg>'
  };

  /* ---------------- 工具 ---------------- */
  function param(name) {
    var v = new URLSearchParams(location.search).get(name);
    return v == null ? '' : v;
  }
  function today(offset) {
    var d = new Date();
    d.setDate(d.getDate() + (offset || 0));
    var mm = d.getMonth() + 1, dd = d.getDate();
    return d.getFullYear() + '-' + (mm < 10 ? '0' + mm : mm) + '-' + (dd < 10 ? '0' + dd : dd);
  }
  function typeOf(train) { return String((train && train.trainNo) || 'G').charAt(0).toUpperCase(); }
  function seatTypesOf(train) { return SEAT_TYPES[typeOf(train)] || SEAT_TYPES.G; }
  function toMin(t) { var p = String(t || '0:0').split(':'); return (+p[0]) * 60 + (+p[1] || 0); }
  function durOf(train) {
    if (!train || !train.startTime || !train.endTime) { return 0; }
    var a = toMin(train.startTime), b = toMin(train.endTime);
    return b >= a ? b - a : (b + 1440 - a);
  }
  function fmtHM(min) {
    if (!min) { return '-'; }
    var h = Math.floor(min / 60), m = min % 60;
    return (h ? h + '小时' : '') + (m ? (m < 10 ? '0' + m : m) + '分' : (h ? '' : '0分'));
  }
  function addMinutes(depart, dur) {
    var m = toMin(depart) + (dur || 0);
    var nextDay = m >= 1440;
    m = ((m % 1440) + 1440) % 1440;
    var h = Math.floor(m / 60), mm = m % 60;
    return { time: (h < 10 ? '0' + h : h) + ':' + (mm < 10 ? '0' + mm : mm), nextDay: nextDay };
  }
  function hm(t) { return String(t || '').slice(0, 5) || '-'; }
  function money(fen) {
    if (fen == null) { return '-'; }
    return '¥' + (fen / 100).toFixed(2);
  }
  function statusText(s) { return ['待支付', '已支付', '已取消', '已出票', '已退票'][s] || ('状态' + s); }
  function statusClass(s) { return ['pending', 'paid', 'canceled', 'paid', 'refunded'][s] || ''; }
  function deadlineTs(order) {
    if (!order || !order.payExpireTime) { return 0; }
    return new Date(String(order.payExpireTime).replace(' ', 'T')).getTime() || 0;
  }
  function errorBox(host, err, tip) {
    var msg = (err && err.message) || '请求失败';
    var extra = '';
    if (err && err.code === 401) {
      extra = '<div style="margin-top:12px"><a class="btn btn-primary btn-sm" href="/api/auth/login">去登录</a></div>';
    }
    host.innerHTML = '<div class="empty-state"><strong>' + esc(tip || '加载失败') + '</strong>' + esc(msg) + extra + '</div>';
  }
  function loginRequired(host, text) {
    host.innerHTML = '<div class="empty-state"><strong>' + esc(text || '该操作需要登录') + '</strong>' +
      '请先登录，接口会带上 Authorization: Bearer 令牌' +
      '<div style="margin-top:14px"><a class="btn btn-primary" href="/api/auth/login">去登录</a></div></div>';
  }

  /* ======================================================================
     /　首页：查询入口
     ====================================================================== */
  function home() {
    app.innerHTML =
      '<div class="page">' +

      /* ---- Hero 品牌区 ---- */
      '  <div class="hero">' +
      '    <h1>康轨 KangRail · 铁路客票服务</h1>' +
      '    <p>高并发购票系统演示 —— Redis 原子预扣、Redisson 分布式锁、RabbitMQ 异步落库。' +
      '       下面的每一个页面都在<b>真实调用后端接口</b>，不是静态原型。</p>' +
      '    <div class="hero-tags">' +
      '      <span class="hero-tag">Spring Boot 2.7</span>' +
      '      <span class="hero-tag">JWT 鉴权</span>' +
      '      <span class="hero-tag">Redis + Lua 预扣</span>' +
      '      <span class="hero-tag">RabbitMQ 异步落库</span>' +
      '      <span class="hero-tag">不超卖</span>' +
      '    </div>' +
      '  </div>' +

      /* ---- 查询面板 ---- */
      '  <div class="search-panel" style="margin-bottom:20px">' +
      '    <div class="search-main">' +
      '      <div class="station-field"><label>出发地</label>' +
      '        <input id="fromCity" value="北京" placeholder="城市名" autocomplete="off">' +
      '        <button class="swap-btn" id="swapBtn" title="交换">' +
      '          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M7 4v13M7 17l-3-3M7 17l3-3M17 20V7M17 7l3 3M17 7l-3 3"/></svg>' +
      '        </button></div>' +
      '      <div class="station-field"><label>到达地</label>' +
      '        <input id="toCity" value="上海" placeholder="城市名" autocomplete="off"></div>' +
      '      <div class="search-submit"><button class="btn btn-primary" id="btnSearch">查询</button></div>' +
      '    </div>' +
      '    <div class="date-strip" id="dateStrip"></div>' +
      '  </div>' +

      /* ---- 快捷入口（卡片网格）---- */
      '  <div class="card" style="margin-bottom:20px">' +
      '    <div class="card-head"><h3>快捷入口</h3>' +
      '      <span class="muted" style="font-size:12px">每个入口对应一个后端接口</span></div>' +
      '    <div class="card-body">' +
      '      <div class="entry-grid">' +
      '        <a class="entry-card" href="/api/train/page">' +
      '          <span class="entry-icon">' + ICON.train + '</span>' +
      '          <span><span class="entry-title">车次查询</span>' +
      '          <span class="entry-desc">GET /api/train/page</span></span></a>' +
      '        <a class="entry-card" href="/api/station/list">' +
      '          <span class="entry-icon">' + ICON.station + '</span>' +
      '          <span><span class="entry-title">车站列表</span>' +
      '          <span class="entry-desc">GET /api/station/list</span></span></a>' +
      '        <a class="entry-card" href="/api/ticket/remain">' +
      '          <span class="entry-icon">' + ICON.ticket + '</span>' +
      '          <span><span class="entry-title">余票查询</span>' +
      '          <span class="entry-desc">GET /api/ticket/remain</span></span></a>' +
      '        <a class="entry-card" href="/api/order/my">' +
      '          <span class="entry-icon">' + ICON.order + '</span>' +
      '          <span><span class="entry-title">我的订单</span>' +
      '          <span class="entry-desc">GET /api/order/my</span></span></a>' +
      '        <a class="entry-card" href="/api/user/me">' +
      '          <span class="entry-icon">' + ICON.user + '</span>' +
      '          <span><span class="entry-title">个人中心</span>' +
      '          <span class="entry-desc">GET /api/user/me</span></span></a>' +
      '        <a class="entry-card" href="/api/admin/ping">' +
      '          <span class="entry-icon">' + ICON.admin + '</span>' +
      '          <span><span class="entry-title">管理控制台</span>' +
      '          <span class="entry-desc">GET /api/admin/ping</span></span></a>' +
      '        <a class="entry-card" href="/console">' +
      '          <span class="entry-icon">' + ICON.console + '</span>' +
      '          <span><span class="entry-title">接口联调控制台</span>' +
      '          <span class="entry-desc">17 个接口一把梭</span></span></a>' +
      '      </div>' +
      '    </div>' +
      '  </div>' +

      /* ---- 演示数据一览（换成卡片式的）---- */
      '  <div class="card"><div class="card-body">' +
      '    <div class="stat-row">' +
      '      <div class="stat-box"><div class="k">车站表</div><div class="v">5 条</div></div>' +
      '      <div class="stat-box"><div class="k">车次表</div><div class="v">2 条</div></div>' +
      '      <div class="stat-box"><div class="k">线路</div><div class="v">北京 ⇄ 上海 / 广州</div></div>' +
      '      <div class="stat-box"><div class="k">演示日期</div><div class="v">2026-09-01</div></div>' +
      '    </div>' +
      '    <div class="tip">数据来自本机 MySQL 的 <b>railway</b> 库。' +
      '      想看到变化，先用「管理控制台 → 放票」给某天放几张票，再来查余票。</div>' +
      '  </div></div>' +
      '</div>';

    /* ===== 以下逻辑与原来完全一致（只改了上面的 HTML 结构）===== */
    var strip = document.getElementById('dateStrip');
    var labels = ['今天', '明天', '后天'];
    var names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
    var html = '<span class="label">出发日期</span>';
    for (var i = 0; i < 7; i++) {
      var d = new Date(); d.setDate(d.getDate() + i);
      html += '<button class="date-chip' + (i === 0 ? ' on' : '') + '" data-date="' + today(i) + '">' +
        (labels[i] || names[d.getDay()]) + ' <b>' + today(i).slice(5) + '</b></button>';
    }
    strip.innerHTML = html;
    strip.onclick = function (e) {
      var chip = e.target.closest('.date-chip');
      if (!chip) { return; }
      strip.querySelectorAll('.date-chip').forEach(function (c) { c.classList.remove('on'); });
      chip.classList.add('on');
    };
    document.getElementById('swapBtn').onclick = function () {
      var f = document.getElementById('fromCity'), t = document.getElementById('toCity');
      var tmp = f.value; f.value = t.value; t.value = tmp;
    };
    document.getElementById('btnSearch').onclick = function () {
      var from = document.getElementById('fromCity').value.trim();
      var to = document.getElementById('toCity').value.trim();
      var chip = strip.querySelector('.date-chip.on');
      var date = chip ? chip.getAttribute('data-date') : today(0);
      if (!from || !to) { UI.toast('请填写出发地和到达地', 'error'); return; }
      App.navigate('/api/train/page' + API.query({ from: from, to: to, date: date }));
    };
  }

  /* ======================================================================
     /api/auth/login　登录 / 注册
     ====================================================================== */
  function login() {
    app.innerHTML =
      '<div class="page" style="max-width:520px">' +
      '  <div class="page-head"><h2>登录 / 注册</h2><span class="sub"><code>POST /api/auth/login</code></span></div>' +
      '  <div class="card"><div class="card-body">' +
      '    <div class="form-row"><label>用户名</label><input class="input" id="lgName" placeholder="用户名" autocomplete="off"></div>' +
      '    <div class="form-row"><label>密码</label><input class="input" id="lgPwd" type="password" placeholder="密码"></div>' +
      '    <div style="display:flex;gap:10px">' +
      '      <button class="btn btn-primary" style="flex:1" id="lgSubmit">登 录</button>' +
      '      <button class="btn btn-default" style="flex:1" id="lgRegister">注 册</button>' +
      '    </div>' +
      '    <div class="hint-box" style="margin-top:14px">演示账号：<b>demo / 123456</b>（普通用户）　<b>admin / admin123</b>（管理员）' +
      '      <div style="margin-top:6px;display:flex;gap:8px">' +
      '        <button class="btn btn-default btn-sm" id="fillUser" type="button">填入普通用户</button>' +
      '        <button class="btn btn-default btn-sm" id="fillAdmin" type="button">填入管理员</button>' +
      '      </div>' +
      '    </div>' +
      '  </div></div>' +
      '</div>';
    var nameEl = document.getElementById('lgName');
    var pwdEl = document.getElementById('lgPwd');
    document.getElementById('fillUser').onclick = function () { nameEl.value = 'demo'; pwdEl.value = '123456'; };
    document.getElementById('fillAdmin').onclick = function () { nameEl.value = 'admin'; pwdEl.value = 'admin123'; };
    function submit(isRegister) {
      var username = nameEl.value.trim(), password = pwdEl.value;
      if (!username || !password) { UI.toast('请输入用户名和密码', 'error'); return; }
      var p = isRegister
        ? API.auth.register({ username: username, password: password, nickname: username })
        : API.auth.login({ username: username, password: password });
      p.then(function () {
        UI.toast(isRegister ? '注册成功，已自动登录' : '登录成功');
        App.refreshUser();
        App.navigate('/api/user/me');
      }).catch(function (err) { UI.toast((err && err.message) || '操作失败', 'error'); });
    }
    document.getElementById('lgSubmit').onclick = function () { submit(false); };
    document.getElementById('lgRegister').onclick = function () { submit(true); };
    pwdEl.onkeydown = function (e) { if (e.key === 'Enter') { submit(false); } };
  }

  /* ======================================================================
     /api/user/me　个人中心
     ====================================================================== */
  function profile() {
    app.innerHTML =
      '<div class="page" style="max-width:640px">' +
      '  <div class="page-head"><h2>个人中心</h2><span class="sub"><code>GET /api/user/me</code></span></div>' +
      '  <div class="card"><div class="card-body" id="meBody">' + UI.skeletonRows(2) + '</div></div>' +
      '</div>';
    var host = document.getElementById('meBody');
    if (!API.hasToken()) { loginRequired(host, '尚未登录'); return; }
    API.auth.me().then(function (me) {
      host.innerHTML =
        '<table class="data-table">' +
        '<tr><th style="width:120px">用户 ID</th><td>' + esc(me.userId) + '</td></tr>' +
        '<tr><th>用户名</th><td>' + esc(me.username) + '</td></tr>' +
        '<tr><th>昵称</th><td>' + esc(me.nickname || '-') + '</td></tr>' +
        '<tr><th>角色</th><td>' + esc((me.roles || []).join('、')) + '</td></tr>' +
        '</table>' +
        '<div style="margin-top:16px;display:flex;gap:10px">' +
        '  <button class="btn btn-default" id="meLogout">退出登录</button>' +
        '  <a class="btn btn-primary" href="/api/order/my">我的订单</a>' +
        '</div>';
      document.getElementById('meLogout').onclick = function () {
        API.auth.logout().then(function () {
          UI.toast('已退出登录'); App.refreshUser(); App.navigate('/');
        }).catch(function () {
          API.clearToken(); App.refreshUser(); App.navigate('/');
        });
      };
    }).catch(function (err) { errorBox(host, err, '获取用户信息失败'); });
  }

  /* ======================================================================
     /api/station/list　车站列表
     ====================================================================== */
  function stations() {
    app.innerHTML =
      '<div class="page">' +
      '  <div class="page-head"><h2>车站列表</h2><span class="sub"><code>GET /api/station/list</code></span></div>' +
      '  <div class="card"><div class="table-wrap"><table class="data-table" id="stTable"></table></div>' +
      '    <div class="card-body" style="border-top:1px solid var(--line)"><span class="muted" id="stMeta">加载中…</span></div></div>' +
      '</div>';
    var table = document.getElementById('stTable');
    if (!API.hasToken()) { loginRequired(table.parentNode, '车站接口需要登录'); return; }
    API.station.list().then(function (list) {
      list = list || [];
      document.getElementById('stMeta').textContent = '共 ' + list.length + ' 个车站';
      table.innerHTML = '<tr><th>ID</th><th>站码</th><th>站名</th><th>城市</th></tr>' +
        list.map(function (s) {
          return '<tr><td class="num">' + s.id + '</td><td>' + esc(s.stationCode) + '</td>' +
            '<td><b>' + esc(s.stationName) + '</b></td><td>' + esc(s.city || '-') + '</td></tr>';
        }).join('');
    }).catch(function (err) { errorBox(table.parentNode, err); });
  }

  /* ======================================================================
     /api/train/page　车次列表（分页 / 关键字，或 ?from=&to= 区间过滤）
     ====================================================================== */
  var lib = { page: 1, size: 10, keyword: '', pages: 1, total: 0 };

  function trains() {
    var from = param('from'), to = param('to'), date = param('date');
    var routeMode = !!(from && to);

    app.innerHTML =
      '<div class="page">' +
      '  <div class="page-head"><h2>' + (routeMode ? '车次查询：' + esc(from) + ' → ' + esc(to) : '车次列表') + '</h2>' +
      '    <span class="sub"><code>GET /api/train/page</code>' + (routeMode ? '（+ GET /api/station/list，区间由前端过滤）' : '') + '</span></div>' +
      '  <div class="card" style="margin-bottom:14px"><div class="card-body" style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">' +
      (routeMode
        ? '<span class="muted">日期：' + esc(date || '未指定') + '　（余票请点每行的「余票/购票」）</span>'
        : '<input class="input" id="libKeyword" placeholder="车次号 / 车次名" style="width:220px" value="' + esc(lib.keyword) + '">' +
          '<button class="btn btn-primary" id="libSearch">查询</button>' +
          '<button class="btn btn-default" id="libReload">刷新</button>') +
      '  </div></div>' +
      '  <div class="card"><div class="table-wrap"><table class="data-table" id="libTable"></table></div>' +
      '    <div class="card-body" style="display:flex;justify-content:space-between;align-items:center;border-top:1px solid var(--line)">' +
      '      <span class="muted" id="libMeta">—</span>' +
      '      <span style="display:flex;gap:8px">' +
      '        <button class="btn btn-default btn-sm" id="libPrev">上一页</button>' +
      '        <button class="btn btn-default btn-sm" id="libNext">下一页</button>' +
      '      </span></div>' +
      '  </div>' +
      '  <div class="card" style="margin-top:14px"><div class="card-head"><h3>本页实际调用的接口</h3>' +
      '    <span class="muted" style="font-size:12px" id="libReq"></span></div>' +
      '    <div class="console" id="libRaw" style="margin:0;border-radius:0 0 var(--radius-l) var(--radius-l)">// 加载中…</div>' +
      '  </div>' +
      '</div>';

    var table = document.getElementById('libTable');
    if (!API.hasToken()) { loginRequired(table.parentNode, '车次接口需要登录'); return; }

    if (routeMode) {
      document.getElementById('libPrev').style.display = 'none';
      document.getElementById('libNext').style.display = 'none';
      loadRoute();
    } else {
      document.getElementById('libSearch').onclick = function () {
        lib.keyword = document.getElementById('libKeyword').value.trim();
        lib.page = 1;
        loadPage();
      };
      document.getElementById('libReload').onclick = loadPage;
      document.getElementById('libPrev').onclick = function () { if (lib.page > 1) { lib.page--; loadPage(); } };
      document.getElementById('libNext').onclick = function () { if (lib.page < lib.pages) { lib.page++; loadPage(); } };
      loadPage();
    }

    function showRaw(label, text) {
      document.getElementById('libReq').textContent = label;
      document.getElementById('libRaw').textContent = text;
    }

    function loadPage() {
      table.innerHTML = '<tr><td>' + UI.skeletonRows(3) + '</td></tr>';
      var path = '/api/train/page' + API.query({ page: lib.page, size: lib.size, keyword: lib.keyword });
      API.train.page({ page: lib.page, size: lib.size, keyword: lib.keyword }).then(function (p) {
        var records = (p && p.records) || [];
        lib.total = p.total || 0;
        lib.pages = p.pages || 1;
        document.getElementById('libMeta').textContent = '共 ' + lib.total + ' 条 · 第 ' + lib.page + '/' + lib.pages + ' 页';
        table.innerHTML = '<tr><th>ID</th><th>车次</th><th>名称</th><th>始发站ID</th><th>终到站ID</th><th>发车</th><th>到达</th><th>状态</th></tr>' +
          (records.length ? records.map(function (t) {
            return '<tr><td class="num">' + t.id + '</td>' +
              '<td><a href="/api/train/' + t.id + '"><b>' + esc(t.trainNo) + '</b></a></td>' +
              '<td>' + esc(t.trainName || '-') + '</td>' +
              '<td class="num">' + (t.startStationId || '-') + '</td>' +
              '<td class="num">' + (t.endStationId || '-') + '</td>' +
              '<td class="num">' + esc(hm(t.startTime)) + '</td>' +
              '<td class="num">' + esc(hm(t.endTime)) + '</td>' +
              '<td>' + (t.status === 1 ? '运行' : '停运') + '</td></tr>';
          }).join('') : '<tr><td colspan="8" class="muted">没有数据</td></tr>');
        API.raw('GET', path).then(function (r) {
          showRaw('GET ' + path, '← HTTP ' + r.status + '\n\n' + (r.body || '').slice(0, 1500));
        });
      }).catch(function (err) { errorBox(table.parentNode, err); });
    }

    /* 区间模式：车站 + 车次 两个真实接口，前端按城市过滤 */
    function loadRoute() {
      table.innerHTML = '<tr><td>' + UI.skeletonRows(3) + '</td></tr>';
      var stPath = '/api/station/list';
      var trPath = '/api/train/page' + API.query({ page: 1, size: 200 });
      Promise.all([API.station.list(), API.train.page({ page: 1, size: 200 })]).then(function (res) {
        var stations = res[0] || [], records = (res[1] && res[1].records) || [];
        var byId = {};
        stations.forEach(function (s) { byId[s.id] = s; });
        var list = records.map(function (t) {
          var a = byId[t.startStationId] || {}, b = byId[t.endStationId] || {};
          return { t: t, from: a.city || a.stationName || '', to: b.city || b.stationName || '' };
        }).filter(function (x) {
          return (x.from === from || x.from.indexOf(from) >= 0) && (x.to === to || x.to.indexOf(to) >= 0);
        });
        document.getElementById('libMeta').textContent = '共 ' + list.length + ' 趟（前端本地过滤）';
        table.innerHTML = '<tr><th>车次</th><th>出发站</th><th>到达站</th><th>发车</th><th>到达</th><th>历时</th><th>操作</th></tr>' +
          (list.length ? list.map(function (x) {
            var t = x.t;
            var arr = addMinutes(t.startTime, durOf(t));
            return '<tr><td><a href="/api/train/' + t.id + '"><b>' + esc(t.trainNo) + '</b></a></td>' +
              '<td>' + esc(x.from) + '</td><td>' + esc(x.to) + '</td>' +
              '<td class="num">' + esc(hm(t.startTime)) + '</td>' +
              '<td class="num">' + (arr.nextDay ? '次日 ' : '') + esc(arr.time) + '</td>' +
              '<td>' + esc(fmtHM(durOf(t))) + '</td>' +
              '<td><a class="btn btn-default btn-sm" href="/api/train/' + t.id + '">时刻表</a> ' +
              '<a class="btn btn-buy btn-sm" href="/api/ticket/remain' + API.query({ trainId: t.id, travelDate: date || '' }) + '">查余票</a></td></tr>';
          }).join('') : '<tr><td colspan="7" class="muted">你后端 train 表中没有 ' + esc(from) + ' → ' + esc(to) + ' 的车次</td></tr>');
        showRaw('GET ' + stPath + ' ＋ GET ' + trPath,
          '车站 ' + stations.length + ' 条\n车次 ' + records.length + ' 条\n本地过滤 ' + from + ' → ' + to + ' 命中 ' + list.length + ' 趟');
      }).catch(function (err) { errorBox(table.parentNode, err); });
    }
  }

  /* ======================================================================
     /api/train/{id}　车次详情
     ====================================================================== */
  function trainDetail(args) {
    var id = (args && args[0]) || param('id');
    app.innerHTML =
      '<div class="page" style="max-width:820px">' +
      '  <div class="page-head"><h2>车次详情</h2><span class="sub"><code>GET /api/train/' + esc(id) + '</code></span></div>' +
      '  <div class="card"><div class="card-body" id="tdBody">' + UI.skeletonRows(3) + '</div></div>' +
      '</div>';
    var host = document.getElementById('tdBody');
    if (!API.hasToken()) { loginRequired(host, '车次接口需要登录'); return; }
    API.train.detail(id).then(function (t) {
      var st = t.stations || [];
      host.innerHTML =
        '<div class="route-summary" style="margin-bottom:14px">' +
        '  <span class="big">' + esc(t.trainNo) + '</span>' +
        '  <span>' + esc(t.trainName || '') + '</span>' +
        '  <span class="muted">发车 ' + esc(hm(t.startTime)) + '　到达 ' + esc(hm(t.endTime)) + '　状态 ' + (t.status === 1 ? '运行' : '停运') + '</span></div>' +
        (st.length
          ? '<div class="timeline">' + st.map(function (s, i) {
            return '<div class="tl-item ' + (i === 0 ? 'first' : '') + ' ' + (i === st.length - 1 ? 'last' : '') + '">' +
              '<div class="time">' + esc(hm(s.leaveTime || s.arriveTime)) + '</div>' +
              '<div class="dot-col"><span class="dot"></span></div>' +
              '<div><div class="name">' + esc(s.stationName) + '</div>' +
              '<div class="meta">' + esc(s.stationCode || '') + '　累计 ' + (s.distanceKm || 0) + ' km</div></div>' +
              '<div class="stop">' + (s.stopMinutes ? '停 ' + s.stopMinutes + ' 分' : (i === 0 ? '始发' : '终到')) + '</div></div>';
          }).join('') + '</div>'
          : '<div class="empty-state">该接口暂未返回经停站数据（后端 TrainServiceImpl.getTrainDetail 的 TODO）</div>') +
        '<div class="hint-box" style="margin-top:14px">' +
        '  换乘/购票：<a href="/api/ticket/remain' + API.query({ trainId: t.id }) + '">查看余票</a></div>';
    }).catch(function (err) { errorBox(host, err); });
  }

  /* ======================================================================
     /api/ticket/remain　余票查询
     ====================================================================== */
  function remain() {
    var trainId = param('trainId') || '1';
    var travelDate = param('travelDate') || '2026-09-01';
    var seatType = param('seatType') || '二等座';
    app.innerHTML =
      '<div class="page" style="max-width:760px">' +
      '  <div class="page-head"><h2>余票查询</h2><span class="sub"><code>GET /api/ticket/remain</code></span></div>' +
      '  <div class="card"><div class="card-body">' +
      '    <div class="form-grid">' +
      '      <div class="form-row"><label>车次 ID</label><input class="input" id="rmTrain" value="' + esc(trainId) + '"></div>' +
      '      <div class="form-row"><label>乘车日期</label><input class="input" id="rmDate" value="' + esc(travelDate) + '"></div>' +
      '      <div class="form-row"><label>席别</label><input class="input" id="rmSeat" value="' + esc(seatType) + '"></div>' +
      '    </div>' +
      '    <button class="btn btn-primary" id="rmQuery">查询</button>' +
      '    <span class="muted" style="margin-left:10px;font-size:12px">后端 train_seat 只有 2026-09-01 的库存</span>' +
      '  </div>' +
      '  <div class="card-body" style="border-top:1px solid var(--line)" id="rmResult"></div></div>' +
      '  <div class="card" style="margin-top:14px"><div class="card-head"><h3>接口原始返回</h3></div>' +
      '    <div class="console" id="rmRaw" style="margin:0">// 待查询</div></div>' +
      '</div>';
    var host = document.getElementById('rmResult');
    if (!API.hasToken()) { loginRequired(host, '余票接口需要登录'); return; }

    function query() {
      var id = document.getElementById('rmTrain').value.trim();
      var date = document.getElementById('rmDate').value.trim();
      var seat = document.getElementById('rmSeat').value.trim();
      host.innerHTML = UI.skeletonRows(1);
      var path = '/api/ticket/remain' + API.query({ trainId: id, travelDate: date, seatType: seat });
      API.ticket.remain(id, date, seat).then(function (n) {
        host.innerHTML =
          '<div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">' +
          '  <span class="muted">车次 ' + esc(id) + '　' + esc(date) + '　' + esc(seat) + '</span>' +
          '  <span style="font-size:26px;font-weight:700;color:var(--price)">' + n + ' 张</span>' +
          '  <a class="btn btn-buy btn-sm" href="/api/ticket/buy' + API.query({ trainId: id, travelDate: date, seatType: seat }) + '">去购票</a>' +
          '</div>';
        API.raw('GET', path).then(function (r) {
          document.getElementById('rmRaw').textContent = 'GET ' + path + '\n← HTTP ' + r.status + '\n\n' + (r.body || '');
        });
      }).catch(function (err) {
        host.innerHTML = '<span class="muted">查询失败：' + esc(err.message || '') + '</span>';
      });
    }
    document.getElementById('rmQuery').onclick = query;
    query();
  }

  /* ======================================================================
     /api/ticket/buy　购票下单（POST /api/ticket/buy）
     ====================================================================== */
  function booking() {
    var trainId = param('trainId'), travelDate = param('travelDate') || param('date');
    var seatType = param('seatType') || param('seat');
    var count = Number(param('ticketCount') || 1);
    if (!trainId || !travelDate) {
      app.innerHTML = '<div class="page"><div class="card"><div class="empty-state"><strong>缺少参数</strong>' +
        '请从「车次列表 / 余票查询」进入下单页（需要 trainId、travelDate）' +
        '<div style="margin-top:14px"><a class="btn btn-primary" href="/api/train/page">去车次列表</a></div></div></div></div>';
      return;
    }

    app.innerHTML =
      '<div class="page" style="max-width:760px">' +
      '  <div class="page-head"><h2>购票下单</h2><span class="sub"><code>POST /api/ticket/buy</code></span></div>' +
      '  <div class="card"><div class="card-body">' +
      '    <div class="form-grid">' +
      '      <div class="form-row"><label>车次 ID</label><input class="input" id="bkTrain" value="' + esc(trainId) + '"></div>' +
      '      <div class="form-row"><label>乘车日期</label><input class="input" id="bkDate" value="' + esc(travelDate) + '"></div>' +
      '      <div class="form-row"><label>席别</label><input class="input" id="bkSeat" value="' + esc(seatType || '二等座') + '"></div>' +
      '      <div class="form-row"><label>购票数量（1~5）</label><input class="input" id="bkCount" value="' + esc(count) + '"></div>' +
      '      <div class="form-row"><label>乘客姓名（可选）</label><input class="input" id="bkPassenger" placeholder="不填则记为「乘客」"></div>' +
      '      <div class="form-row"><label>证件号（可选）</label><input class="input" id="bkIdCard" placeholder="不填则留空"></div>' +
      '    </div>' +
      '    <div class="hint-box">后端接受 trainId、travelDate、seatType、ticketCount，以及可选的 passengerName、idCardNo；' +
      '    乘客信息会写入订单明细表 train_order_item。</div>' +
      '    <button class="btn btn-buy btn-lg" style="width:100%;margin-top:14px" id="bkSubmit">提交订单</button>' +
      '  </div>' +
      '  <div class="card-body" style="border-top:1px solid var(--line)" id="bkResult"></div></div>' +
      '  <div class="card" style="margin-top:14px"><div class="card-head"><h3>本次请求</h3></div>' +
      '    <div class="console" id="bkRaw" style="margin:0">// 待提交</div></div>' +
      '</div>';

    document.getElementById('bkSubmit').onclick = function () {
      var payload = {
        trainId: Number(document.getElementById('bkTrain').value.trim()),
        travelDate: document.getElementById('bkDate').value.trim(),
        seatType: document.getElementById('bkSeat').value.trim(),
        ticketCount: Number(document.getElementById('bkCount').value.trim())
      };
      var pName = document.getElementById('bkPassenger').value.trim();
      var pId = document.getElementById('bkIdCard').value.trim();
      if (pName) { payload.passengerName = pName; }
      if (pId) { payload.idCardNo = pId; }
      var out = document.getElementById('bkResult');
      var rawBox = document.getElementById('bkRaw');
      rawBox.textContent = 'POST /api/ticket/buy\n请求体：' + JSON.stringify(payload) + '\n\n请求中…';
      out.innerHTML = UI.skeletonRows(1);

      API.raw('POST', '/api/ticket/buy', payload).then(function (r) {
        rawBox.textContent = 'POST /api/ticket/buy\n请求体：' + JSON.stringify(payload) +
          '\n\n← HTTP ' + r.status + '\n' + (r.body || '(空响应)');
      });

      API.ticket.buy(payload).then(function (data) {
        // 防御：正常应返回 orderNo；为空说明后端异常，不能误报「下单成功」
        if (!data || !data.orderNo) {
          out.innerHTML = '<div style="line-height:1.9">' +
            '<span style="color:var(--red);font-weight:600">下单异常：后端没有返回订单号</span>' +
            '<div class="muted" style="margin-top:6px;font-size:12px">原始返回：<code>' + esc(JSON.stringify(data)) + '</code></div>' +
            '</div>';
          return;
        }
        out.innerHTML = '<div style="display:flex;gap:14px;align-items:center;flex-wrap:wrap">' +
          '<span style="color:var(--green);font-weight:600">下单成功</span>' +
          '<span>订单号：<b>' + esc(data.orderNo) + '</b></span>' +
          (data.totalAmount != null ? '<span>金额：<b>' + money(data.totalAmount) + '</b></span>' : '') +
          (data.payExpireTime ? '<span class="muted">请在 ' + esc(data.payExpireTime) + ' 前支付</span>' : '') +
          '<a class="btn btn-primary btn-sm" href="/api/order/my">查看我的订单</a></div>';
      }).catch(function (err) {
        out.innerHTML = '<div style="color:var(--red);line-height:1.9">下单失败：' + esc(err.message || '') +
          (err.code === 401 ? '<br><a href="/api/auth/login">去登录</a>' : '') +
          '</div>';
      });
    };
  }

  /* ======================================================================
     /api/order/my　我的订单
     ====================================================================== */
  function orders() {
    var tab = param('tab') || 'ALL';
    app.innerHTML =
      '<div class="page">' +
      '  <div class="page-head"><h2>我的订单</h2><span class="sub"><code>GET /api/order/my</code></span></div>' +
      '  <div class="tabs" id="orderTabs"></div>' +
      '  <div id="orderList"></div>' +
      '</div>';
    var host = document.getElementById('orderList');
    if (!API.hasToken()) { loginRequired(host, '订单接口需要登录'); document.getElementById('orderTabs').style.display = 'none'; return; }

    API.order.my().then(function (list) {
      list = list || [];
      var tabs = [{ v: 'ALL', n: '全部' }, { v: 0, n: '待支付' }, { v: 1, n: '已支付' }, { v: 2, n: '已取消' }, { v: 3, n: '已出票' }, { v: 4, n: '已退票' }];
      document.getElementById('orderTabs').innerHTML = tabs.map(function (t) {
        var n = t.v === 'ALL' ? list.length : list.filter(function (o) { return Number(o.status) === t.v; }).length;
        return '<button data-v="' + t.v + '" class="' + (String(tab) === String(t.v) ? 'on' : '') + '">' + t.n + ' (' + n + ')</button>';
      }).join('');
      document.getElementById('orderTabs').onclick = function (e) {
        var b = e.target.closest('button');
        if (b) { App.navigate('/api/order/my?tab=' + b.getAttribute('data-v')); }
      };
      var filtered = list.filter(function (o) { return tab === 'ALL' || String(o.status) === String(tab); });
      host.innerHTML = filtered.length ? filtered.map(orderCard).join('')
        : '<div class="card"><div class="empty-state"><strong>暂无订单</strong>去车次列表下单试试</div></div>';
      host.querySelectorAll('[data-act]').forEach(function (b) {
        b.onclick = function () {
          var no = b.getAttribute('data-no'), act = b.getAttribute('data-act');
          var order = list.filter(function (o) { return o.orderNo === no; })[0];
          if (act === 'cancel') {
            UI.confirm('确认取消订单 ' + no + '？取消后库存会返还。', function () {
              API.order.cancel(no).then(function () {
                UI.toast('订单已取消，库存已返还');
                App.route();
              }).catch(function (err) { UI.toast('取消失败：' + (err.message || ''), 'error'); });
            });
          }
          if (act === 'detail') { orderDetail(order); }
          if (act === 'pay') { UI.payModal(order); }
          if (act === 'refund') {
            UI.confirm('确认对订单 ' + no + ' 办理退票？退票后库存会返还，且不可撤销。', function () {
              API.order.refund(no).then(function () {
                UI.toast('退票成功，库存已返还');
                App.route();
              }).catch(function (err) { UI.toast('退票失败：' + (err.message || ''), 'error'); });
            });
          }
        };
      });
      startCountdown();
    }).catch(function (err) { errorBox(host, err, '订单加载失败'); });
  }

  function orderCard(o) {
    var s = Number(o.status);
    var dl = deadlineTs(o);
    return '<div class="order-card">' +
      '  <div class="order-top"><span>订单号 ' + esc(o.orderNo) + '　下单时间 ' + esc(o.createTime || '-') + '</span>' +
      '    <span class="order-status ' + statusClass(s) + '">' + statusText(s) + '</span></div>' +
      '  <div class="order-main">' +
      '    <div><div class="order-meta">车次 ID：' + esc(o.trainId) + '　乘车日期：' + esc(o.travelDate || '-') + '</div>' +
      '      <div class="order-meta">席别：' + esc(o.seatType || '-') + '　数量：' + (o.ticketCount || 0) + ' 张</div></div>' +
      '    <div class="order-side"><div class="amount">' + money(o.totalAmount) + '</div></div>' +
      '  </div>' +
      '  <div class="order-actions">' +
      (s === 0 && dl ? '<span class="countdown">支付剩余 <b data-dl="' + dl + '">--:--</b></span>' : '<span></span>') +
      '    <button class="btn btn-default btn-sm" data-act="detail" data-no="' + esc(o.orderNo) + '">详情</button>' +
      (s === 0 ? '<button class="btn btn-buy btn-sm" data-act="pay" data-no="' + esc(o.orderNo) + '">去支付</button>' +
        '<button class="btn btn-default btn-sm" data-act="cancel" data-no="' + esc(o.orderNo) + '">取消订单</button>' : '') +
      (s === 1 || s === 3 ? '<button class="btn btn-default btn-sm" data-act="refund" data-no="' + esc(o.orderNo) + '">退票</button>' : '') +
      '  </div>' +
      '</div>';
  }

  function orderDetail(o) {
    if (!o) { return; }
    UI.openModal(
      '<h3>订单详情</h3>' +
      '<table class="data-table">' +
      '<tr><th style="width:130px">订单号</th><td>' + esc(o.orderNo) + '</td></tr>' +
      '<tr><th>状态</th><td>' + statusText(Number(o.status)) + '</td></tr>' +
      '<tr><th>车次 ID</th><td>' + esc(o.trainId) + '</td></tr>' +
      '<tr><th>乘车日期</th><td>' + esc(o.travelDate || '-') + '</td></tr>' +
      '<tr><th>席别 / 数量</th><td>' + esc(o.seatType || '-') + ' × ' + (o.ticketCount || 0) + '</td></tr>' +
      '<tr><th>金额</th><td>' + money(o.totalAmount) + '</td></tr>' +
      '<tr><th>支付截止</th><td>' + esc(o.payExpireTime || '-') + '</td></tr>' +
      '<tr><th>下单时间</th><td>' + esc(o.createTime || '-') + '</td></tr>' +
      '</table>' +
      '<div class="modal-actions"><button class="btn btn-default" id="odClose">关闭</button></div>'
    );
    document.getElementById('odClose').onclick = UI.closeModal;
  }

  function startCountdown() {
    if (window._orderTimer) { clearInterval(window._orderTimer); }
    window._orderTimer = setInterval(function () {
      var spans = document.querySelectorAll('[data-dl]');
      if (!spans.length) { return; }
      Array.prototype.forEach.call(spans, function (sp) {
        var left = (+sp.getAttribute('data-dl')) - Date.now();
        if (left <= 0) { sp.textContent = '已超时'; return; }
        var s = Math.floor(left / 1000), m = Math.floor(s / 60), sec = s % 60;
        sp.textContent = (m < 10 ? '0' + m : m) + ':' + (sec < 10 ? '0' + sec : sec);
      });
    }, 1000);
  }

  /* ======================================================================
     /api/admin/ping　管理控制台
     ====================================================================== */
  function admin() {
    /* 支持从不同入口进入时定位到对应功能页 */
    var path = location.pathname;
    var initial = /stock\/publish/.test(path) ? 'publish'
      : /admin\/train/.test(path) ? 'trains'
        : /admin\/station/.test(path) ? 'stations'
          : 'publish';

    app.innerHTML =
      '<div class="page">' +
      '  <div class="page-head"><h2>管理控制台</h2><span class="sub">' +
      '    <code>GET /api/admin/ping</code>　<span id="adminPing">校验中…</span></span></div>' +
      '  <div class="admin-layout">' +
      '    <div class="card side-nav">' +
      '      <button data-view="publish">放票 / 库存预热</button>' +
      '      <button data-view="trains">车次维护</button>' +
      '      <button data-view="stations">车站维护</button>' +
      '      <button data-view="stock">余票查询</button>' +
      '      <button data-view="logs">接口调用日志</button>' +
      '    </div>' +
      '    <div id="adminBody">' + UI.skeletonRows(3) + '</div>' +
      '  </div>' +
      '</div>';
    var body = document.getElementById('adminBody');
    if (!API.hasToken()) { loginRequired(body, '管理控制台需要登录'); return; }

    var VIEWS = { publish: loadPublish, trains: loadTrains, stations: loadStations, stock: loadStock, logs: loadLogs };

    function show(view) {
      document.querySelectorAll('.side-nav button').forEach(function (x) {
        x.classList.toggle('on', x.getAttribute('data-view') === view);
      });
      VIEWS[view]();
    }
    document.querySelectorAll('.side-nav button').forEach(function (b) {
      b.onclick = function () { show(b.getAttribute('data-view')); };
    });

    API.admin.ping().then(function () {
      document.getElementById('adminPing').innerHTML = '<span style="color:var(--green)">权限校验通过（ADMIN）</span>';
      show(initial);
    }).catch(function (err) {
      document.getElementById('adminPing').innerHTML = '<span style="color:var(--red)">' + esc(err.message || '校验失败') + '</span>';
      body.innerHTML = '<div class="card"><div class="empty-state"><strong>需要管理员权限</strong>' +
        esc(err.message || '') +
        '<div style="margin-top:14px"><a class="btn btn-primary" href="/api/auth/login">用 admin / admin123 登录</a></div>' +
        '<div class="muted" style="margin-top:10px;font-size:12px">登录后需要重新进入本页（角色写在 JWT 里）</div></div></div>';
    });

    /* ---------------- ① 放票 / 库存预热 ---------------- */
    function loadPublish() {
      body.innerHTML =
        '<div class="card">' +
        '  <div class="card-head"><h3>放票 / 库存预热</h3>' +
        '    <span class="muted" style="font-size:12px"><code>POST /api/admin/stock/publish</code> · 仅 ADMIN</span></div>' +
        '  <div class="card-body">' +
        '    <div class="form-grid">' +
        '      <div class="form-row"><label>车次 ID</label><input class="input" id="pbTrain" value="1"></div>' +
        '      <div class="form-row"><label>乘车日期</label><input class="input" id="pbDate" value="' + today(1) + '"></div>' +
        '      <div class="form-row"><label>席别</label><select class="input" id="pbSeat">' +
        '        <option>二等座</option><option>一等座</option><option>商务座</option>' +
        '        <option>硬卧</option><option>软卧</option><option>硬座</option><option>无座</option></select></div>' +
        '      <div class="form-row"><label>放票数量</label><input class="input" id="pbCount" value="100"></div>' +
        '    </div>' +
        '    <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">' +
        '      <button class="btn btn-primary" id="pbSubmit">放票</button>' +
        '      <button class="btn btn-outline" id="pbAfter">放票后立刻查余票</button>' +
        '      <span class="muted" style="font-size:12px">放票 → 查余票 → 再放一次，可以直观看到库存累加</span>' +
        '    </div>' +
        '    <div class="muted" style="margin-top:12px;font-size:12px;line-height:1.9">' +
        '      这个接口做两件事：<br>' +
        '      ① 写 <code>train_seat</code>（该「车次+日期+席别」不存在则插入，已存在则 <b>total / remain 一起累加</b>）<br>' +
        '      ② 把最新余票 <b>预热进 Redis</b> 的 <code>ticket:stock:{trainId}:{date}:{seatType}</code><br>' +
        '      <b>必须先放票，购票时 Redis 里才有库存可扣。</b>' +
        '    </div>' +
        '  </div>' +
        '  <div class="card-body" style="border-top:1px solid var(--line)" id="pbResult">' +
        '    <span class="muted">尚未调用</span></div>' +
        '</div>';

      var out = document.getElementById('pbResult');
      function vals() {
        return {
          trainId: document.getElementById('pbTrain').value.trim(),
          travelDate: document.getElementById('pbDate').value.trim(),
          seatType: document.getElementById('pbSeat').value,
          count: document.getElementById('pbCount').value.trim()
        };
      }
      function line(html) { out.innerHTML += (out.innerHTML.indexOf('尚未调用') >= 0 ? '' : '<br>') + html; }

      document.getElementById('pbSubmit').onclick = function () {
        var v = vals();
        out.innerHTML = '调用中…';
        API.admin.publishStock({
          trainId: Number(v.trainId), travelDate: v.travelDate,
          seatType: v.seatType, count: Number(v.count)
        }).then(function (remain) {
          var ok = remain > 0;
          out.innerHTML =
            '<div>' + (ok ? '<span class="tag-real">成功</span>' : '<span class="tag-mock">注意</span>') +
            '　车次 ' + esc(v.trainId) + '　' + esc(v.travelDate) + '　' + esc(v.seatType) +
            '　放票 ' + esc(v.count) + ' 张</div>' +
            '<div style="margin-top:6px;font-size:16px">接口返回的最新余票：<b style="color:var(--price)">' + esc(String(remain)) + '</b></div>' +
            (ok ? '' : '<div class="muted" style="margin-top:6px;font-size:12px">返回 0 说明 StockService.publish 还没有把 remain 返回出来（见「任务 2-2」）</div>');
        }).catch(function (err) {
          out.innerHTML = '<div><span class="tag-mock">失败</span>　' + esc(err.message || '调用失败') + '</div>' +
            (err.code === 403 ? '<div class="muted" style="margin-top:6px;font-size:12px">403 = 当前账号不是 ADMIN，请用 admin / admin123 登录</div>' : '');
        });
      };

      document.getElementById('pbAfter').onclick = function () {
        var v = vals();
        out.innerHTML = '查询中…';
        API.ticket.remain(v.trainId, v.travelDate, v.seatType).then(function (n) {
          out.innerHTML = '车次 ' + esc(v.trainId) + '　' + esc(v.travelDate) + '　' + esc(v.seatType) +
            ' → 当前余票 <b style="color:var(--price);font-size:20px">' + esc(String(n)) + '</b> 张';
        }).catch(function (err) {
          out.innerHTML = '<span class="muted">查询失败：' + esc(err.message || '') + '</span>';
        });
      };
    }

    /* ---------------- ② 车次维护 ---------------- */
    function loadTrains() {
      body.innerHTML = '<div class="card"><div class="card-head"><h3>车次维护</h3>' +
        '<span class="muted" style="font-size:12px"><code>PUT /api/admin/train/{trainId}</code> · 改完会清 <code>railway:cache:train::id</code></span></div>' +
        '<div class="table-wrap"><table class="data-table" id="adTrainTable"></table></div></div>';
      API.train.page({ page: 1, size: 20 }).then(function (p) {
        var records = (p && p.records) || [];
        document.getElementById('adTrainTable').innerHTML =
          '<tr><th>ID</th><th>车次</th><th>名称（可改）</th><th>始发</th><th>终到</th><th>发车</th><th>到达</th>' +
          '<th>状态</th><th>操作</th></tr>' +
          (records.length ? records.map(function (t) {
            return '<tr><td class="num">' + t.id + '</td>' +
              '<td><a href="/api/train/' + t.id + '"><b>' + esc(t.trainNo) + '</b></a></td>' +
              '<td><input class="input" style="height:30px;min-width:150px" id="tn' + t.id + '" value="' + esc(t.trainName || '') + '"></td>' +
              '<td class="num">' + (t.startStationId || '-') + '</td>' +
              '<td class="num">' + (t.endStationId || '-') + '</td>' +
              '<td class="num">' + esc(hm(t.startTime)) + '</td>' +
              '<td class="num">' + esc(hm(t.endTime)) + '</td>' +
              '<td><select class="input" style="height:30px" id="ts' + t.id + '">' +
              '<option value="1"' + (t.status === 1 ? ' selected' : '') + '>运行</option>' +
              '<option value="0"' + (t.status === 0 ? ' selected' : '') + '>停运</option></select></td>' +
              '<td><button class="btn btn-outline btn-sm" data-train="' + t.id + '">保存</button></td></tr>';
          }).join('') : '<tr><td class="muted">没有数据</td></tr>');

        document.getElementById('adTrainTable').querySelectorAll('[data-train]').forEach(function (b) {
          b.onclick = function () {
            var id = b.getAttribute('data-train');
            var name = document.getElementById('tn' + id).value.trim();
            var status = Number(document.getElementById('ts' + id).value);
            API.admin.updateTrain(id, { trainName: name, status: status }).then(function () {
              UI.toast('车次 ' + id + ' 已更新（缓存已清）');
              API.train.detail(id).then(function (d) {
                UI.toast('重新读取：' + d.trainName + ' / ' + (d.status === 1 ? '运行' : '停运'));
              }).catch(function () {});
            }).catch(function (err) { UI.toast('更新失败：' + (err.message || ''), 'error'); });
          };
        });
      }).catch(function (err) { body.innerHTML = '<div class="card"><div class="empty-state">' + esc(err.message || '') + '</div></div>'; });
    }

    /* ---------------- ③ 车站维护 ---------------- */
    function loadStations() {
      body.innerHTML = '<div class="card"><div class="card-head"><h3>车站维护</h3>' +
        '<span class="muted" style="font-size:12px"><code>PUT /api/admin/station/{stationId}</code> · 改完清整个 station 缓存</span></div>' +
        '<div class="table-wrap"><table class="data-table" id="adStationTable"></table></div></div>';
      API.station.list().then(function (list) {
        var rows = (list || []);
        document.getElementById('adStationTable').innerHTML =
          '<tr><th>ID</th><th>电报码</th><th>站名（可改）</th><th>城市（可改）</th><th>操作</th></tr>' +
          (rows.length ? rows.map(function (s) {
            return '<tr><td class="num">' + s.id + '</td><td class="num">' + esc(s.stationCode || '') + '</td>' +
              '<td><input class="input" style="height:30px;min-width:120px" id="sn' + s.id + '" value="' + esc(s.stationName || '') + '"></td>' +
              '<td><input class="input" style="height:30px;min-width:120px" id="sc' + s.id + '" value="' + esc(s.city || '') + '"></td>' +
              '<td><button class="btn btn-outline btn-sm" data-station="' + s.id + '">保存</button></td></tr>';
          }).join('') : '<tr><td class="muted">没有数据</td></tr>');

        document.getElementById('adStationTable').querySelectorAll('[data-station]').forEach(function (b) {
          b.onclick = function () {
            var id = b.getAttribute('data-station');
            var name = document.getElementById('sn' + id).value.trim();
            var city = document.getElementById('sc' + id).value.trim();
            if (!name) { UI.toast('站名不能为空', 'warn'); return; }
            var body2 = { stationName: name };
            if (city) { body2.city = city; }
            API.admin.updateStation(id, body2).then(function () {
              UI.toast('车站 ' + id + ' 已更新');
              return API.station.list();
            }).then(function (fresh) {
              var hit = (fresh || []).filter(function (x) { return String(x.id) === String(id); })[0];
              if (hit) { UI.toast('重新读取：' + hit.stationName + ' / ' + hit.city); }
            }).catch(function (err) { UI.toast('更新失败：' + (err.message || ''), 'error'); });
          };
        });
      }).catch(function (err) { body.innerHTML = '<div class="card"><div class="empty-state">' + esc(err.message || '') + '</div></div>'; });
    }

    /* ---------------- ④ 余票查询 ---------------- */
    function loadStock() {
      body.innerHTML = '<div class="card"><div class="card-head"><h3>余票查询</h3>' +
        '<span class="muted" style="font-size:12px"><code>GET /api/ticket/remain</code></span></div>' +
        '<div class="card-body"><div class="form-grid">' +
        '  <div class="form-row"><label>车次 ID</label><input class="input" id="adTrain" value="1"></div>' +
        '  <div class="form-row"><label>乘车日期</label><input class="input" id="adDate" value="' + today(1) + '"></div>' +
        '  <div class="form-row"><label>席别</label><input class="input" id="adSeat" value="二等座"></div>' +
        '</div><button class="btn btn-primary" id="adQuery">查询</button>' +
        '  <div class="muted" style="margin-top:10px;font-size:12px">' +
        '    返回的是 Redis 里的余票（缓存命中则不走库）。返回 0 可能是：<b>没放票</b>、或缓存里存的是空值标记。</div>' +
        '</div><div class="card-body" style="border-top:1px solid var(--line)" id="adResult"><span class="muted">尚未查询</span></div></div>';
      document.getElementById('adQuery').onclick = function () {
        var id = document.getElementById('adTrain').value.trim();
        var date = document.getElementById('adDate').value.trim();
        var seat = document.getElementById('adSeat').value.trim();
        var out = document.getElementById('adResult');
        out.innerHTML = '查询中…';
        API.ticket.remain(id, date, seat).then(function (n) {
          out.innerHTML = '车次 ' + esc(id) + '　' + esc(date) + '　' + esc(seat) +
            ' → <b style="color:var(--price);font-size:20px">' + esc(String(n)) + ' 张</b>';
        }).catch(function (err) {
          out.innerHTML = '<span class="muted">查询失败：' + esc(err.message || '') + '</span>';
        });
      };
    }

    /* ---------------- ⑤ 调用日志 ---------------- */
    function loadLogs() {
      var logs = API.logs();
      body.innerHTML = '<div class="card"><div class="card-head"><h3>接口调用日志</h3>' +
        '<span class="muted" style="font-size:12px">只记录本页面发起的调用</span></div>' +
        '<div class="console" style="margin:0;border-radius:0 0 var(--radius-l) var(--radius-l)">' +
        (logs.length ? logs.map(function (l) {
          return '<div><span class="ts">[' + l.t + ']</span> ' + l.method + ' ' + esc(l.path) +
            '　' + (l.ok ? '<span class="tag-real">成功</span>' : '<span class="tag-mock">失败</span>') +
            (l.note ? '　' + esc(l.note) : '') + '</div>';
        }).join('') : '<div>暂无调用记录</div>') +
        '</div></div>';
    }
  }

  /* ======================================================================
     /console　接口联调控制台
     ====================================================================== */
  var PIPELINE = {
    'POST /api/auth/login': function (v) { return API.auth.login({ username: v.username, password: v.password }); },
    'POST /api/auth/register': function (v) { return API.auth.register({ username: v.username, password: v.password, nickname: v.nickname }); },
    'GET /api/user/me': function () { return API.auth.me(); },
    'POST /api/user/logout': function () { return API.auth.logout(); },
    'GET /api/station/list': function () { return API.station.list(); },
    'GET /api/train/page': function (v) { return API.train.page({ page: v.page, size: v.size, keyword: v.keyword }); },
    'GET /api/train/{id}': function (v) { return API.train.detail(v.id); },
    'GET /api/ticket/remain': function (v) { return API.ticket.remain(v.trainId, v.travelDate, v.seatType); },
    'POST /api/ticket/buy': function (v) {
      return API.ticket.buy({ trainId: Number(v.trainId), travelDate: v.travelDate, seatType: v.seatType, ticketCount: Number(v.ticketCount) });
    },
    'GET /api/order/my': function () { return API.order.my(); },
    'GET /api/order/{orderNo}': function (v) { return API.order.detail(v.orderNo); },
    'POST /api/order/{orderNo}/cancel': function (v) { return API.order.cancel(v.orderNo); },
    'GET /api/admin/ping': function () { return API.admin.ping(); },
    'POST /api/admin/stock/publish': function (v) {
      return API.admin.publishStock({
        trainId: Number(v.trainId), travelDate: v.travelDate,
        seatType: v.seatType, count: Number(v.count)
      });
    },
    'PUT /api/admin/train/{trainId}': function (v) {
      var body = {};
      if (v.trainName !== '' && v.trainName !== undefined) { body.trainName = v.trainName; }
      if (v.status !== '' && v.status !== undefined) { body.status = Number(v.status); }
      return API.admin.updateTrain(v.trainId, body);
    },
    'PUT /api/admin/station/{stationId}': function (v) {
      var body = { stationName: v.stationName };
      if (v.city) { body.city = v.city; }
      return API.admin.updateStation(v.stationId, body);
    }
  };

  function consolePage() {
    var eps = API.endpoints;
    app.innerHTML =
      '<div class="page">' +
      '  <div class="page-head"><h2>接口联调控制台</h2><span class="sub">后端共 ' + eps.length + ' 个接口，全部直连真实后端（无本地模拟）</span></div>' +
      '  <div class="card" style="margin-bottom:14px"><div class="card-head"><h3>环境自检</h3>' +
      '    <button class="btn btn-default btn-sm" id="envReload">重新检测</button></div>' +
      '    <div class="card-body" id="envBody"><div class="skeleton sk-line" style="width:60%"></div></div></div>' +
      '  <div class="card"><div class="card-head"><h3>接口清单</h3>' +
      '    <span class="muted" style="font-size:12px">「直连调用」= 原样请求后端；「前端逻辑」= 走 api.js 封装</span></div>' +
      '    <div class="table-wrap"><table class="data-table" id="apiTable"></table></div></div>' +
      '  <div class="console" id="apiResp" style="margin-top:14px">// 调用结果</div>' +
      '</div>';
    checkEnv();
    renderTable();
    document.getElementById('envReload').onclick = checkEnv;

    function checkEnv() {
      var body = document.getElementById('envBody');
      var isFile = location.protocol === 'file:';
      body.innerHTML =
        '<div>页面地址：<code>' + location.href.split('#')[0] + '</code></div>' +
        '<div>接口基址：<code>' + (isFile ? '不可用（file://）' : location.origin + '/api/* → nginx → Spring Boot') + '</code></div>' +
        '<div>登录状态：' + (API.hasToken() ? '<span style="color:var(--green)">已登录（token 已保存）</span>' : '<span style="color:var(--price)">未登录</span>') + '</div>' +
        '<div id="probeLine">后端连通性：检测中…</div>';
      if (isFile) { document.getElementById('probeLine').textContent = '后端连通性：无法检测（file://）'; return; }
      var t0 = Date.now();
      API.raw('GET', '/api/station/list').then(function (r) {
        var hint = r.status === 200 ? '正常（已登录）'
          : r.status === 401 ? '后端正常，但当前未登录'
            : r.status === 502 ? 'nginx 连不上后端（8080/8081 未启动？）'
              : r.status === 404 ? '接口不存在（后端版本过旧？）' : 'HTTP ' + r.status;
        document.getElementById('probeLine').innerHTML = '后端连通性：<b>HTTP ' + r.status + '</b> 用时 ' + (Date.now() - t0) + 'ms · ' + hint;
      }).catch(function (e) {
        document.getElementById('probeLine').innerHTML = '后端连通性：<span style="color:var(--red)">请求失败 ' + esc(e.message) + '</span>';
      });
    }

    function renderTable() {
      var rows = '', lastGroup = '';
      eps.forEach(function (ep, i) {
        if (ep.g !== lastGroup) {
          lastGroup = ep.g;
          rows += '<tr><td colspan="4" style="background:#f7f9fc;font-weight:600;color:var(--ink-2)">' + esc(ep.g) + '</td></tr>';
        }
        var fields = (ep.f || []).map(function (f) {
          return '<label style="display:inline-flex;align-items:center;gap:4px;margin-right:8px;font-size:12px">' +
            '<span class="muted">' + esc(f.k) + '</span>' +
            '<input class="input" style="height:28px;width:' + Math.max(78, f.k.length * 9) + 'px" data-field="' + i + '|' + esc(f.k) + '" value="' + esc(f.v) + '"></label>';
        }).join('');
        rows += '<tr>' +
          '<td><span class="tag" style="background:' + (ep.m === 'GET' ? 'var(--brand)' : 'var(--green)') + ';color:#fff">' + ep.m + '</span>' +
          '<div class="muted" style="font-size:12px">' + (ep.auth ? (ep.admin ? 'ADMIN' : '需登录') : '公开') + '</div></td>' +
          '<td><code>' + esc(ep.p) + '</code><div class="muted" style="font-size:12px">' + esc(ep.d) + '</div></td>' +
          '<td>' + (fields || '<span class="muted">无参数</span>') + '</td>' +
          '<td style="white-space:nowrap">' +
          '  <button class="btn btn-outline btn-sm" data-raw="' + i + '">直连调用</button> ' +
          '  <button class="btn btn-default btn-sm" data-pipe="' + i + '">前端逻辑</button> ' +
          '  <button class="btn btn-default btn-sm" data-curl="' + i + '">复制 curl</button>' +
          '</td></tr>';
      });
      document.getElementById('apiTable').innerHTML =
        '<tr><th style="width:110px">方法</th><th style="width:280px">路径</th><th>参数</th><th>操作</th></tr>' + rows;
      document.getElementById('apiTable').querySelectorAll('[data-raw]').forEach(function (b) {
        b.onclick = function () { rawCall(eps[+b.getAttribute('data-raw')], +b.getAttribute('data-raw')); };
      });
      document.getElementById('apiTable').querySelectorAll('[data-pipe]').forEach(function (b) {
        b.onclick = function () { pipeCall(eps[+b.getAttribute('data-pipe')]); };
      });
      document.getElementById('apiTable').querySelectorAll('[data-curl]').forEach(function (b) {
        b.onclick = function () { copyCurl(eps[+b.getAttribute('data-curl')], +b.getAttribute('data-curl')); };
      });
    }

    function valuesOf(ep, index) {
      var v = {};
      (ep.f || []).forEach(function (f) {
        var el = document.querySelector('[data-field="' + index + '|' + f.k + '"]');
        v[f.k] = el ? el.value : f.v;
      });
      return v;
    }
    function buildUrl(ep, v) {
      var url = ep.p.replace(/\{(\w+)\}/g, function (_, k) { return encodeURIComponent(v[k] || ''); });
      if (!ep.body && ep.f && ep.f.length) {
        url += API.query(v);
      }
      return url;
    }
    /* 带 bf（body fields）时，只把这些字段放进请求体；
       不带则用全部字段——避免把路径参数（如 trainId）也塞进 JSON */
    function bodyOf(ep, v) {
      if (!ep.body) { return null; }
      if (!ep.bf) { return v; }
      var o = {};
      ep.bf.forEach(function (k) { if (v[k] !== undefined && v[k] !== '') { o[k] = v[k]; } });
      return o;
    }
    function print(title, payload) {
      var el = document.getElementById('apiResp');
      el.textContent += '\n\n' + title + '\n' + payload;
      el.scrollTop = el.scrollHeight;
    }
    function rawCall(ep, index) {
      var v = valuesOf(ep, index);
      var url = buildUrl(ep, v);
      var body = bodyOf(ep, v);
      print('→ ' + ep.m + ' ' + url, body ? '请求体：' + JSON.stringify(body) : '');
      var t0 = Date.now();
      API.raw(ep.m, url, body).then(function (r) {
        print('← ' + r.status + ' ' + ep.m + ' ' + url + '（' + (Date.now() - t0) + 'ms）', (r.body || '(空响应)').slice(0, 2000));
      }).catch(function (e) { print('← 请求失败', e.message); });
    }
    function pipeCall(ep) {
      var key = ep.m + ' ' + ep.p;
      var fn = PIPELINE[key];
      if (!fn) { print('→ 前端逻辑 ' + key, '该接口未接入页面逻辑'); return; }
      var v = valuesOf(ep, eps.indexOf(ep));
      print('→ 前端逻辑 ' + key, '参数：' + JSON.stringify(v));
      fn(v).then(function (data) {
        print('← 返回', typeof data === 'object' ? JSON.stringify(data).slice(0, 2000) : String(data));
      }).catch(function (e) { print('← 失败', (e && e.message) || String(e)); });
    }
    function copyCurl(ep, index) {
      var v = valuesOf(ep, index);
      var url = buildUrl(ep, v);
      var body = bodyOf(ep, v);
      var cmd = 'curl -i -X ' + ep.m + " 'http://localhost:200" + url + "'";
      if (API.hasToken()) { cmd += " \\\n  -H 'Authorization: Bearer " + API.token() + "'"; }
      if (body) { cmd += " \\\n  -H 'Content-Type: application/json' \\\n  -d '" + JSON.stringify(body) + "'"; }
      if (navigator.clipboard) {
        navigator.clipboard.writeText(cmd).then(function () { UI.toast('curl 已复制'); })
          .catch(function () { UI.toast('复制失败，见下方输出', 'warn'); });
      }
      print('$ ' + cmd, '（粘贴到终端执行可看到真实返回）');
    }
  }

  window.Pages = {
    home: home,
    login: login,
    profile: profile,
    stations: stations,
    trains: trains,
    trainDetail: trainDetail,
    remain: remain,
    booking: booking,
    orders: orders,
    admin: admin,
    console: consolePage
  };
})();
