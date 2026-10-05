/* ==========================================================================
   API 客户端 —— 只调用后端真实接口，没有任何本地模拟
   后端接口清单（与 server 模块 Controller 一一对应）：
     POST /api/auth/register      POST /api/auth/login
     GET  /api/user/me            POST /api/user/logout
     GET  /api/station/list
     GET  /api/train/page         GET  /api/train/{id}
     GET  /api/ticket/remain      POST /api/ticket/buy
     GET  /api/order/my           GET  /api/order/{orderNo}   POST /api/order/{orderNo}/cancel
     GET  /api/admin/ping
   ========================================================================== */
(function () {
  'use strict';

  var TOKEN_KEY = 'kr_token';
  var logs = [];
  var listeners = [];

  /* ---------------- token ---------------- */
  function token() { return localStorage.getItem(TOKEN_KEY) || ''; }
  function setToken(t) { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY); }
  function hasToken() { return !!token(); }
  function clearToken() { setToken(''); }

  /* ---------------- 调用日志（供控制台展示） ---------------- */
  function on(fn) { listeners.push(fn); }
  function emit() { listeners.forEach(function (f) { try { f(); } catch (e) { /* ignore */ } }); }
  function record(method, path, ok, note) {
    logs.unshift({
      t: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
      method: method, path: path, ok: ok, note: note || ''
    });
    if (logs.length > 100) { logs.length = 100; }
    emit();
  }
  function getLogs() { return logs.slice(); }

  /* ---------------- 统一请求 ---------------- */
  function request(method, path, body) {
    var headers = {};
    if (token()) { headers['Authorization'] = 'Bearer ' + token(); }
    var init = { method: method, headers: headers };
    if (body !== undefined && body !== null) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }
    return fetch(path, init).then(function (res) {
      return res.text().then(function (text) {
        var json = null;
        try { json = text ? JSON.parse(text) : null; } catch (e) { json = null; }

        if (res.status === 401) {
          clearToken();
          record(method, path, false, 'HTTP 401 未登录或登录已过期');
          throw { code: 401, message: (json && json.message) || '未登录或登录已过期' };
        }
        if (res.status === 403) {
          record(method, path, false, 'HTTP 403 无权限');
          throw { code: 403, message: (json && json.message) || '无权限访问' };
        }
        if (!json) {
          record(method, path, false, 'HTTP ' + res.status + ' 非 JSON 响应');
          throw { code: res.status, message: '接口返回异常（HTTP ' + res.status + '）' };
        }
        if (json.code !== 200) {
          record(method, path, false, json.message);
          throw { code: json.code, message: json.message };
        }
        record(method, path, true);
        return json.data;
      });
    }).catch(function (err) {
      if (err && err.code) { throw err; }
      record(method, path, false, '请求失败：' + (err && err.message));
      throw { code: -1, message: '无法连接后端（' + ((err && err.message) || '网络错误') + '）' };
    });
  }

  /* 原样请求（控制台用）：返回 HTTP 状态与原始响应体 */
  function raw(method, path, body) {
    var headers = {};
    if (token()) { headers['Authorization'] = 'Bearer ' + token(); }
    var init = { method: method, headers: headers };
    if (body) { headers['Content-Type'] = 'application/json'; init.body = JSON.stringify(body); }
    return fetch(path, init).then(function (res) {
      return res.text().then(function (text) { return { status: res.status, body: text }; });
    });
  }

  function query(params) {
    var qs = Object.keys(params)
      .filter(function (k) { return params[k] !== undefined && params[k] !== null && params[k] !== ''; })
      .map(function (k) { return k + '=' + encodeURIComponent(params[k]); })
      .join('&');
    return qs ? '?' + qs : '';
  }

  window.API = {
    endpoints: [
      { g: '认证', m: 'POST', p: '/api/auth/register', auth: false, body: true, d: '注册（注册即登录）',
        f: [{ k: 'username', v: 'user' + Math.floor(Math.random() * 9000 + 1000) }, { k: 'password', v: '123456' }, { k: 'nickname', v: '新用户' }] },
      { g: '认证', m: 'POST', p: '/api/auth/login', auth: false, body: true, d: '登录',
        f: [{ k: 'username', v: 'demo' }, { k: 'password', v: '123456' }] },
      { g: '认证', m: 'GET', p: '/api/user/me', auth: true, d: '当前登录用户' },
      { g: '认证', m: 'POST', p: '/api/user/logout', auth: true, d: '登出' },

      { g: '基础数据', m: 'GET', p: '/api/station/list', auth: true, d: '车站列表' },
      { g: '基础数据', m: 'GET', p: '/api/train/page', auth: true, d: '车次分页',
        f: [{ k: 'page', v: '1' }, { k: 'size', v: '10' }, { k: 'keyword', v: '' }] },
      { g: '基础数据', m: 'GET', p: '/api/train/{id}', auth: true, d: '车次详情',
        f: [{ k: 'id', v: '1' }] },

      { g: '票务', m: 'GET', p: '/api/ticket/remain', auth: true, d: '余票查询',
        f: [{ k: 'trainId', v: '1' }, { k: 'travelDate', v: '2026-09-01' }, { k: 'seatType', v: '二等座' }] },
      { g: '票务', m: 'POST', p: '/api/ticket/buy', auth: true, body: true, d: '购票下单',
        f: [{ k: 'trainId', v: '1' }, { k: 'travelDate', v: '2026-09-01' }, { k: 'seatType', v: '二等座' }, { k: 'ticketCount', v: '1' }] },

      { g: '订单', m: 'GET', p: '/api/order/my', auth: true, d: '我的订单' },
      { g: '订单', m: 'GET', p: '/api/order/{orderNo}', auth: true, d: '订单详情',
        f: [{ k: 'orderNo', v: '' }] },
      { g: '订单', m: 'POST', p: '/api/order/{orderNo}/cancel', auth: true, d: '取消订单',
        f: [{ k: 'orderNo', v: '' }] },

      { g: '管理端', m: 'GET', p: '/api/admin/ping', auth: true, admin: true, d: '管理员权限校验' }
    ],

    token: token,
    setToken: setToken,
    hasToken: hasToken,
    clearToken: clearToken,
    logs: getLogs,
    on: on,
    raw: raw,
    query: query,

    auth: {
      register: function (p) {
        return request('POST', '/api/auth/register', p).then(function (data) {
          if (data && data.token) { setToken(data.token); }
          return data;
        });
      },
      login: function (p) {
        return request('POST', '/api/auth/login', p).then(function (data) {
          if (data && data.token) { setToken(data.token); }
          return data;
        });
      },
      me: function () { return request('GET', '/api/user/me'); },
      logout: function () {
        return request('POST', '/api/user/logout').then(function (r) { clearToken(); return r; })
          .catch(function (err) { clearToken(); throw err; });
      }
    },

    station: {
      list: function () { return request('GET', '/api/station/list'); }
    },

    train: {
      page: function (q) {
        q = q || {};
        return request('GET', '/api/train/page' + query({ page: q.page || 1, size: q.size || 10, keyword: q.keyword }));
      },
      detail: function (id) { return request('GET', '/api/train/' + encodeURIComponent(id)); }
    },

    ticket: {
      remain: function (trainId, travelDate, seatType) {
        return request('GET', '/api/ticket/remain' + query({ trainId: trainId, travelDate: travelDate, seatType: seatType }));
      },
      buy: function (payload) { return request('POST', '/api/ticket/buy', payload); }
    },

    order: {
      my: function () { return request('GET', '/api/order/my'); },
      detail: function (orderNo) { return request('GET', '/api/order/' + encodeURIComponent(orderNo)); },
      cancel: function (orderNo) { return request('POST', '/api/order/' + encodeURIComponent(orderNo) + '/cancel'); }
    },

    admin: {
      ping: function () { return request('GET', '/api/admin/ping'); }
    }
  };
})();
