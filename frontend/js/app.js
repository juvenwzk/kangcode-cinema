/* ==========================================================================
   应用入口：历史模式路由（不带 #），路径与后端接口一一对应
     /                        首页（查询入口）
     /api/auth/login          登录 / 注册页        → POST /api/auth/login、/api/auth/register
     /api/user/me             个人中心              → GET  /api/user/me、POST /api/user/logout
     /api/station/list        车站列表              → GET  /api/station/list
     /api/train/page          车次列表              → GET  /api/train/page
     /api/train/{id}          车次详情              → GET  /api/train/{id}
     /api/ticket/remain       余票查询              → GET  /api/ticket/remain
     /api/ticket/buy          购票下单              → POST /api/ticket/buy
     /api/order/my            我的订单              → GET  /api/order/my
     /api/admin/ping          管理控制台            → GET  /api/admin/ping
     /api/admin/stock/publish 管理台·放票页          → POST /api/admin/stock/publish
     /api/admin/train/{id}    管理台·车次维护页       → PUT  /api/admin/train/{id}
     /api/admin/station/{id}  管理台·车站维护页       → PUT  /api/admin/station/{id}
     /console                 接口联调控制台（调试页，非后端接口）
   ========================================================================== */
(function () {
  'use strict';

  var ROUTES = [
    { re: /^\/$/, page: 'home' },
    { re: /^\/api\/auth\/login\/?$/, page: 'login' },
    { re: /^\/api\/user\/me\/?$/, page: 'profile' },
    { re: /^\/api\/station\/list\/?$/, page: 'stations' },
    { re: /^\/api\/train\/page\/?$/, page: 'trains' },
    { re: /^\/api\/train\/(\d+)\/?$/, page: 'trainDetail' },
    { re: /^\/api\/ticket\/remain\/?$/, page: 'remain' },
    { re: /^\/api\/ticket\/buy\/?$/, page: 'booking' },
    { re: /^\/api\/order\/my\/?$/, page: 'orders' },
    { re: /^\/api\/admin\/ping\/?$/, page: 'admin' },
    { re: /^\/api\/admin\/stock\/publish\/?$/, page: 'admin' },
    { re: /^\/api\/admin\/train(\/\d+)?\/?$/, page: 'admin' },
    { re: /^\/api\/admin\/station(\/\d+)?\/?$/, page: 'admin' },
    { re: /^\/console\/?$/, page: 'console' }
  ];

  function parse() {
    var path = location.pathname;
    for (var i = 0; i < ROUTES.length; i++) {
      var m = path.match(ROUTES[i].re);
      if (m) { return { page: ROUTES[i].page, path: path, args: m.slice(1) }; }
    }
    return { page: 'home', path: '/', args: [] };
  }

  function route() {
    if (window._orderTimer) { clearInterval(window._orderTimer); window._orderTimer = null; }
    var r = parse();
    document.querySelectorAll('.main-nav a').forEach(function (a) {
      var href = a.getAttribute('href');
      a.classList.toggle('active', href === r.path || (href !== '/' && r.path.indexOf(href) === 0));
    });
    window.scrollTo(0, 0);
    var fn = Pages[r.page] || Pages.home;
    fn(r.args);
  }

  function navigate(path) {
    if (path === location.pathname + location.search) { route(); return; }
    history.pushState({}, '', path);
    route();
  }

  /* 站内链接拦截，避免整页刷新 */
  document.addEventListener('click', function (e) {
    var a = e.target.closest && e.target.closest('a[href^="/"]');
    if (!a || a.getAttribute('target') === '_blank' || e.metaKey || e.ctrlKey || e.shiftKey) { return; }
    e.preventDefault();
    navigate(a.getAttribute('href'));
  });

  /* ---------------- 顶部用户区 ---------------- */
  function refreshUser() {
    var box = document.getElementById('userBox');
    if (!API.hasToken()) {
      box.innerHTML =
        '<a class="btn btn-default btn-sm" href="/api/auth/login">登录</a>' +
        '<a class="btn btn-primary btn-sm" href="/api/auth/login">注册</a>';
      return;
    }
    API.auth.me().then(function (me) {
      render(me.username, (me.roles && me.roles[0]) || 'USER');
    }).catch(function () {
      API.clearToken();
      refreshUser();
    });

    function render(displayName, displayRole) {
      box.innerHTML =
        '<a class="avatar" href="/api/user/me" title="个人中心">' + UI.esc(displayName.slice(0, 1).toUpperCase()) + '</a>' +
        '<span class="user-name">' + UI.esc(displayName) + '</span>' +
        '<span class="user-role' + (displayRole === 'ADMIN' ? ' admin' : '') + '">' +
        (displayRole === 'ADMIN' ? '管理员' : '普通用户') + '</span>' +
        '<button class="btn btn-default btn-sm" id="btnLogout">退出</button>';
      document.getElementById('btnLogout').onclick = function () {
        API.auth.logout().catch(function () {}).then(function () {
          API.clearToken();
          UI.toast('已退出登录');
          refreshUser();
          navigate('/');
        });
      };
    }
  }

  /* ---------------- 后端状态指示器 ---------------- */
  function refreshBackend() {
    var pill = document.getElementById('sourcePill');
    var text = document.getElementById('sourceText');
    if (location.protocol === 'file:') {
      text.textContent = '本地文件打开 · 接口不可用';
      pill.className = 'source-pill';
      return;
    }
    API.raw('GET', '/api/station/list').then(function (r) {
      pill.classList.remove('real', 'mock');
      if (r.status === 200) { text.textContent = '后端正常 · 已登录'; pill.classList.add('real'); }
      else if (r.status === 401) { text.textContent = '后端正常 · 未登录'; pill.classList.add('mock'); }
      else { text.textContent = '后端异常 · HTTP ' + r.status; pill.classList.remove('real', 'mock'); }
    }).catch(function () {
      text.textContent = '后端不可达';
      pill.classList.remove('real', 'mock');
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (location.protocol === 'file:') {
      var warn = document.createElement('div');
      warn.style.cssText = 'background:#fff4ec;color:#c92a2a;border-bottom:1px solid #f0d9b5;padding:8px 24px;font-size:13px;text-align:center';
      warn.innerHTML = '当前以本地文件（file://）方式打开，接口无法调用。请改用 <b>http://localhost:200/</b> 访问本站。';
      document.body.insertBefore(warn, document.body.firstChild);
    }
    document.getElementById('sourcePill').onclick = function () { refreshBackend(); };
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') { UI.closeModal(); } });
    window.addEventListener('popstate', route);
    refreshUser();
    refreshBackend();
    route();
  });

  window.App = {
    navigate: navigate,
    route: route,
    refreshUser: refreshUser,
    refreshBackend: refreshBackend
  };
})();
