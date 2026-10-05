/* ==========================================================================
   UI 组件：提示、弹窗、登录注册、支付、确认框
   ========================================================================== */
(function () {
  'use strict';

  var mask = document.getElementById('modalMask');
  var box = document.getElementById('modalBox');
  var toastWrap = document.getElementById('toastWrap');
  var esc = function (s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };

  function toast(msg, type) {
    var el = document.createElement('div');
    el.className = 'toast' + (type ? ' ' + type : '');
    el.textContent = msg;
    toastWrap.appendChild(el);
    setTimeout(function () {
      el.style.transition = 'opacity .3s';
      el.style.opacity = '0';
      setTimeout(function () { if (el.parentNode) { el.parentNode.removeChild(el); } }, 320);
    }, 2800);
  }

  function openModal(html, opts) {
    opts = opts || {};
    box.className = 'modal' + (opts.wide ? ' modal-wide' : '');
    box.innerHTML = html;
    mask.hidden = false;
    mask.onclick = opts.locked ? null : function (e) { if (e.target === mask) { closeModal(); } };
  }

  function closeModal() {
    mask.hidden = true;
    box.innerHTML = '';
    mask.onclick = null;
  }

  function confirm(message, onOk, okText) {
    openModal(
      '<h3>操作确认</h3>' +
      '<p style="color:var(--ink-2);line-height:1.8">' + esc(message) + '</p>' +
      '<div class="modal-actions">' +
      '<button class="btn btn-default" id="cfCancel">取消</button>' +
      '<button class="btn btn-primary" id="cfOk">' + (okText || '确定') + '</button>' +
      '</div>'
    );
    document.getElementById('cfCancel').onclick = closeModal;
    document.getElementById('cfOk').onclick = function () { closeModal(); if (onOk) { onOk(); } };
  }

  /* ---------------- 登录 / 注册 ---------------- */
  function authModal(tab) {
    renderAuth(tab || 'login');
  }

  function renderAuth(tab, tip) {
    var isLogin = tab === 'login';
    var html =
      '<h3>' + (isLogin ? '登录康轨平台' : '注册新账号') + '</h3>' +
      (tip ? '<div class="hint-box" style="margin-bottom:14px;color:var(--red)">' + esc(tip) + '</div>' : '') +
      '<div class="form-row"><label>用户名</label><input class="input" id="auName" placeholder="请输入用户名" autocomplete="off"></div>' +
      '<div class="form-row"><label>密码</label><input class="input" id="auPwd" type="password" placeholder="请输入密码"></div>' +
      (isLogin ? '' : '<div class="form-row"><label>昵称（选填）</label><input class="input" id="auNick" placeholder="请输入昵称"></div>') +
      '<div class="hint-box">演示账号：<b>admin / admin123</b>（管理员）　<b>demo / 123456</b>（普通用户）' +
      '  <div style="margin-top:6px;display:flex;gap:8px">' +
      '    <button class="btn btn-default btn-sm" id="fillUser" type="button">填入普通用户</button>' +
      '    <button class="btn btn-default btn-sm" id="fillAdmin" type="button">填入管理员</button>' +
      '  </div>' +
      '</div>' +
      '<div class="modal-actions"><button class="btn btn-primary" id="auSubmit">' + (isLogin ? '登 录' : '注 册') + '</button></div>' +
      '<div class="auth-switch">' +
      (isLogin ? '还没有账号？<a id="auSwitch">立即注册</a>' : '已有账号？<a id="auSwitch">去登录</a>') +
      '</div>';
    openModal(html);

    var nameEl = document.getElementById('auName');
    var pwdEl = document.getElementById('auPwd');
    var submit = document.getElementById('auSubmit');

    function doSubmit() {
      var username = nameEl.value.trim();
      var password = pwdEl.value;
      if (!username || !password) { toast('请输入用户名和密码', 'error'); return; }
      submit.disabled = true;
      submit.textContent = '处理中…';

      var p;
      if (isLogin) {
        p = API.auth.login({ username: username, password: password });
      } else {
        var nickname = (document.getElementById('auNick') || {}).value || '';
        p = API.auth.register({ username: username, password: password, nickname: nickname });
      }
      p.then(function () {
        closeModal();
        toast(isLogin ? '登录成功' : '注册成功，已自动登录');
        App.refreshUser();
        App.route();
      }).catch(function (err) {
        submit.disabled = false;
        submit.textContent = isLogin ? '登 录' : '注 册';
        toast((err && err.message) || '操作失败', 'error');
      });
    }

    submit.onclick = doSubmit;
    nameEl.onkeydown = function (e) { if (e.key === 'Enter') { doSubmit(); } };
    pwdEl.onkeydown = function (e) { if (e.key === 'Enter') { doSubmit(); } };
    document.getElementById('auSwitch').onclick = function () { renderAuth(isLogin ? 'register' : 'login'); };
    document.getElementById('fillUser').onclick = function () { nameEl.value = 'demo'; pwdEl.value = '123456'; };
    document.getElementById('fillAdmin').onclick = function () { nameEl.value = 'admin'; pwdEl.value = 'admin123'; };
  }

  /* 需要登录时调用；返回 Promise，登录成功后 resolve */
  function requireLogin(tip) {
    if (API.hasToken()) { return Promise.resolve(true); }
    authModal('login');
    if (tip) { toast(tip, 'warn'); }
    return Promise.resolve(false);
  }

  /* ---------------- 支付 ---------------- */
  function payModal(order) {
    var no = order.orderNo;
    var amount = order.totalAmount ? (order.totalAmount / 100) : order.total;
    openModal(
      '<h3>订单支付（模拟）</h3>' +
      '<div class="pay-amount">' + amount + '</div>' +
      '<div class="pay-note">订单号 ' + esc(no) + ' · 请在 30 分钟内完成支付</div>' +
      '<div class="hint-box" style="text-align:center">真实支付接口将在 P4 实现（支付回调 + 超时关单），此处为前端模拟。</div>' +
      '<div class="modal-actions">' +
      '<button class="btn btn-default" id="payCancel">取消</button>' +
      '<button class="btn btn-buy" id="payOk">模拟支付成功</button>' +
      '</div>'
    );
    document.getElementById('payCancel').onclick = closeModal;
    document.getElementById('payOk').onclick = function () {
      closeModal();
      toast('后端还没有支付接口（计划在 P4：支付回调 + 超时关单），这里不做任何本地模拟', 'warn');
    };
  }

  /* ---------------- 时刻表弹窗 ---------------- */
  function trainDetailModal(train, date) {
    openModal(
      '<h3>' + esc(train.trainNo) + ' 时刻表</h3>' +
      '<div class="muted" style="margin-bottom:12px">' + esc(train.from || '') + ' → ' + esc(train.to || '') +
      '　' + esc(date || '') + '</div>' +
      '<div id="detailBody" class="skeleton" style="height:180px"></div>' +
      '<div class="modal-actions"><button class="btn btn-default" id="dtClose">关闭</button></div>',
      { wide: true }
    );
    document.getElementById('dtClose').onclick = closeModal;

    API.train.detail(train.id).then(function (d) {
      var stations = d.stations || [];
      var html = '<div class="timeline">' + stations.map(function (s, i) {
        return '<div class="tl-item ' + (i === 0 ? 'first' : '') + ' ' + (i === stations.length - 1 ? 'last' : '') + '">' +
          '<div class="time">' + (s.leaveTime || s.arriveTime || '').slice(0, 5) + '</div>' +
          '<div class="dot-col"><span class="dot"></span></div>' +
          '<div><div class="name">' + esc(s.stationName) + '</div>' +
          '<div class="meta">' + esc(s.stationCode || '') + '　累计 ' + (s.distanceKm || 0) + ' km</div></div>' +
          '<div class="stop">' + (s.stopMinutes ? '停 ' + s.stopMinutes + ' 分' : (i === 0 ? '始发' : '终到')) + '</div>' +
          '</div>';
      }).join('') + '</div>';
      var body = document.getElementById('detailBody');
      if (body) { body.className = ''; body.innerHTML = stations.length ? html : '<div class="empty-state">暂无经停站数据</div>'; }
    }).catch(function (err) {
      var body = document.getElementById('detailBody');
      if (body) { body.className = ''; body.innerHTML = '<div class="empty-state">' + esc(err.message || '加载失败') + '</div>'; }
    });
  }

  function skeletonRows(n) {
    var html = '';
    for (var i = 0; i < n; i++) { html += '<div class="skeleton sk-row"></div>'; }
    return html;
  }

  window.UI = {
    toast: toast,
    openModal: openModal,
    closeModal: closeModal,
    confirm: confirm,
    authModal: authModal,
    requireLogin: requireLogin,
    payModal: payModal,
    trainDetailModal: trainDetailModal,
    skeletonRows: skeletonRows,
    esc: esc
  };
})();
