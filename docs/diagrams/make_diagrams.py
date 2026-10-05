# -*- coding: utf-8 -*-
"""
康轨 KangRail —— 业务流程图生成器

用 Pillow 直接绘制 PNG（不依赖 matplotlib / graphviz / mermaid）。
生成结果输出到 docs/diagrams/*.png，被 开发计划.md 引用。

修改图只需改本文件后重跑：
    python make_diagrams.py

设计要点：
  · 节点框会登记到 self.boxes，标签放置时自动避让，避免文字压在框上
  · emoji 统一降级成中文字体支持的字形（微软雅黑没有 ✅❌⚠️ 这些字形）
  · 竖向流程用 vchain() 自动排版，不再手写 y 坐标
"""
import math
import os
import re
from PIL import Image, ImageDraw, ImageFont

FONT_REG = r'C:\Windows\Fonts\msyh.ttc'      # 微软雅黑
FONT_BOLD = r'C:\Windows\Fonts\msyhbd.ttc'   # 微软雅黑 Bold

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

# 微软雅黑缺少这些字形，统一替换掉，否则渲染成方框
EMOJI_MAP = {
    '⚠️': '【注意】', '⚠': '【注意】',
    '✅': '√', '❌': '×', '✔': '√', '✗': '×',
    '🔴': '', '⭐': '★',
}


def safe(s):
    if not s:
        return s
    for k, v in EMOJI_MAP.items():
        s = s.replace(k, v)
    return s


# ---------------- 配色（语义化：一眼看出组件归属） ----------------
C = {
    'entry':    ('#F3E5F5', '#7B1FA2'),   # 入口/Controller 紫
    'service':  ('#E8F5E9', '#2E7D32'),   # 业务/Service 绿
    'db':       ('#E3F2FD', '#1565C0'),   # MySQL 蓝
    'redis':    ('#FFEBEE', '#C62828'),   # Redis 红
    'mq':       ('#FFF3E0', '#EF6C00'),   # RabbitMQ 橙
    'redis_mq': ('#FFF8E1', '#FF8F00'),   # 混合
    'decision': ('#FFFDE7', '#F9A825'),   # 判断 黄
    'ok':       ('#E8F5E9', '#2E7D32'),   # 成功 绿
    'fail':     ('#FFEBEE', '#C62828'),   # 失败 红
    'neutral':  ('#ECEFF1', '#455A64'),   # 中性 灰
    'note':     ('#FAFAFA', '#9E9E9E'),   # 注释 浅灰
}


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def tokenize(s):
    return re.findall(r'[A-Za-z0-9_\-\./:+#*{}()\[\]=<>|]+|\s+|[^\sA-Za-z0-9_\-\./:+#*{}()\[\]=<>|]', s)


def wrap(text, f, max_w, d):
    out, cur = [], ''
    for tok in tokenize(safe(text)):
        if tok == '\n':
            out.append(cur); cur = ''; continue
        trial = cur + tok
        if d.textlength(trial, font=f) <= max_w or not cur:
            cur = trial
        else:
            out.append(cur.rstrip())
            cur = '' if tok.isspace() else tok
    if cur.strip():
        out.append(cur.rstrip())
    return out or ['']


class Canvas:
    def __init__(self, w, h, title=None, subtitle=None):
        self.w, self.h = w, h
        self.img = Image.new('RGB', (w, h), 'white')
        self.d = ImageDraw.Draw(self.img)
        self.boxes = []          # 已绘制的节点矩形，供标签避让
        self.top = 0
        if title:
            self.d.text((w // 2, 30), safe(title), font=font(30, True), fill='#1A237E', anchor='mm')
            self.top = 74
        if subtitle:
            self.d.text((w // 2, self.top), safe(subtitle), font=font(17), fill='#546E7A', anchor='mm')
            self.top += 30
        self.top += 8

    # ---------------- 碰撞检测 ----------------
    def _overlaps_box(self, rect):
        x0, y0, x1, y1 = rect
        for b in self.boxes:
            if not (x1 < b[0] or x0 > b[2] or y1 < b[1] or y0 > b[3]):
                return True
        return False

    def _text_rect(self, text, size, cx, cy):
        f = font(size)
        w = self.d.textlength(text, font=f)
        bb = self.d.textbbox((0, 0), text, font=f)
        h = bb[3] - bb[1]
        return (cx - w / 2 - 7, cy - h / 2 - 5, cx + w / 2 + 7, cy + h / 2 + 5)

    # ---------------- 基础图元 ----------------
    def box(self, x, y, w, h, title=None, lines=None, kind='rect', color='neutral',
            ts=20, ls=16, pad=14, radius=None):
        fill, border = C[color]
        x0, y0, x1, y1 = x, y, x + w, y + h
        if kind == 'diamond':
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            self.d.polygon([(cx, y0), (x1, cy), (cx, y1), (x0, cy)], fill=fill, outline=border, width=3)
        elif kind == 'stadium':
            self.d.rounded_rectangle([x0, y0, x1, y1], radius=h // 2, fill=fill, outline=border, width=3)
        elif kind == 'db':
            self.d.rounded_rectangle([x0, y0 + 12, x1, y1], radius=10, fill=fill, outline=border, width=3)
            self.d.ellipse([x0, y0, x1, y0 + 24], fill=fill, outline=border, width=3)
            self.d.arc([x0, y1 - 24, x1, y1], 0, 180, fill=border, width=3)
        else:
            r = radius if radius is not None else (16 if kind == 'round' else 6)
            self.d.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=fill, outline=border, width=3)

        ft, fl = font(ts, True), font(ls)
        blocks = []
        if title:
            for ln in wrap(title, ft, w - pad * 2, self.d):
                blocks.append((ln, ft))
        if lines:
            for raw in lines:
                for s in str(raw).split('\n'):
                    for ln in wrap(s, fl, w - pad * 2, self.d):
                        blocks.append((ln, fl))
        gap = 5
        hs = [self.d.textbbox((0, 0), t, font=f)[3] - self.d.textbbox((0, 0), t, font=f)[1] for t, f in blocks]
        total = sum(hs) + gap * (len(blocks) - 1)
        cy = y + h / 2 - total / 2
        for (t, f), hh in zip(blocks, hs):
            self.d.text((x + w / 2, cy + hh / 2), t, font=f, fill=('#102027' if f is ft else '#37474F'), anchor='mm')
            cy += hh + gap
        self.boxes.append((x0, y0, x1, y1))
        return (x0, y0, x1, y1)

    def label(self, x, y, text, size=16, bold=False, color='#37474F', anchor='mm', bg=None):
        f = font(size, bold)
        t = safe(text)
        if bg:
            bb = self.d.textbbox((x, y), t, font=f, anchor=anchor)
            self.d.rectangle([bb[0] - 5, bb[1] - 3, bb[2] + 5, bb[3] + 3], fill=bg)
        self.d.text((x, y), t, font=f, fill=color, anchor=anchor)

    # ---------------- 箭头 ----------------
    def _head(self, p_from, p_to, color, size=13):
        ang = math.atan2(p_to[1] - p_from[1], p_to[0] - p_from[0])
        a1 = (p_to[0] - size * math.cos(ang - 0.42), p_to[1] - size * math.sin(ang - 0.42))
        a2 = (p_to[0] - size * math.cos(ang + 0.42), p_to[1] - size * math.sin(ang + 0.42))
        self.d.polygon([p_to, a1, a2], fill=color)

    def arrow(self, pts, label=None, color='#455A64', width=3, dashed=False,
              label_at=None, label_size=15, label_color=None, head=True, label_bg='white'):
        """
        画折线箭头。label 若不给 label_at，会自动沿线找一处不与任何节点框重叠的位置。
        """
        if dashed:
            self._dashed_polyline(pts, color, width)
        else:
            self.d.line(pts, fill=color, width=width, joint='curve')
        if head:
            self._head(pts[-2], pts[-1], color)
        if label:
            if label_at is None:
                label_at = self._find_label_pos(pts, label, label_size)
            if label_at:
                self.label(label_at[0], label_at[1], label, size=label_size,
                           color=label_color or color, bg=label_bg)

    def _find_label_pos(self, pts, text, size):
        """沿线找一处不与节点框重叠的标签位置；优先中点"""
        cands = []
        for i in range(len(pts) - 1):
            (x1, y1), (x2, y2) = pts[i], pts[i + 1]
            for t in (0.5, 0.35, 0.65, 0.25, 0.75):
                cands.append((x1 + (x2 - x1) * t, y1 + (y2 - y1) * t))
        for c in cands:
            if not self._overlaps_box(self._text_rect(text, size, c[0], c[1])):
                return c
        # 都不行 → 沿垂直方向偏移试探
        for c in cands:
            for dy in (-30, 30, -50, 50, -70, 70):
                cc = (c[0], c[1] + dy)
                if not self._overlaps_box(self._text_rect(text, size, cc[0], cc[1])):
                    return cc
        return cands[0] if cands else None

    def _dashed_polyline(self, pts, color, width, dash=11, gap=8):
        for i in range(len(pts) - 1):
            (x1, y1), (x2, y2) = pts[i], pts[i + 1]
            dist = math.hypot(x2 - x1, y2 - y1)
            if dist == 0:
                continue
            ux, uy = (x2 - x1) / dist, (y2 - y1) / dist
            t = 0
            while t < dist:
                e = min(t + dash, dist)
                self.d.line([(x1 + ux * t, y1 + uy * t), (x1 + ux * e, y1 + uy * e)], fill=color, width=width)
                t = e + gap

    def legend(self, x, y, items, cols=1, box_w=250, row_h=30):
        f = font(16)
        for i, (ck, txt) in enumerate(items):
            cx = x + (i % cols) * box_w
            cy = y + (i // cols) * row_h
            fill, border = C[ck]
            self.d.rounded_rectangle([cx, cy, cx + 26, cy + 18], radius=4, fill=fill, outline=border, width=2)
            self.d.text((cx + 36, cy + 9), safe(txt), font=f, fill='#37474F', anchor='lm')

    # ---------------- 竖向流程自动排版 ----------------
    def vchain(self, x, y, w, steps, gap=46):
        """steps: [(title, lines, color, height)] 竖向排列，返回 (最后一个框的底边 y, 中心 x)"""
        prev_bottom = y
        cx = x + w / 2
        for i, (title, lines, color, h) in enumerate(steps):
            yy = prev_bottom + (gap if i > 0 else 0)
            self.box(x, yy, w, h, title, lines, color=color, ts=19, ls=13)
            if i > 0:
                self.arrow([(cx, prev_bottom), (cx, yy)])
            prev_bottom = yy + h
        return prev_bottom, cx

    def save(self, name):
        p = os.path.join(OUT_DIR, name)
        self.img.save(p)
        print('  saved', name, self.img.size)
        return p


# =====================================================================
# 图 1：系统分层架构
# =====================================================================
def fig01():
    c = Canvas(1500, 1010, '图 1 · 康轨购票系统 —— 分层架构与依赖方向',
               '依赖只能从上往下：server → framework → pojo → common（下层永远不知道上层的存在）')

    c.box(600, 108, 300, 66, '浏览器 / 前端页面', ['frontend/ · nginx :200'], kind='stadium',
          color='neutral', ts=19, ls=15)

    chain = [
        ('JwtInterceptor（JWT 拦截器）', ['framework/security/ · 验签 + 比对 Redis 里的 token'], 'entry', 76),
        ('Controller 层（server/controller）',
         ['AuthController  UserController  StationController  TrainController\n'
          'TicketController  OrderController  AdminController'], 'entry', 96),
        ('Service 层（server/service + service/impl）',
         ['业务逻辑都写在这里 —— 你 90% 的时间花在这一层\n'
          'TicketServiceImpl · OrderServiceImpl · StockService（待写）'], 'service', 96),
        ('Mapper 层（server/mapper）',
         ['MyBatis-Plus BaseMapper，单表 CRUD 不写 SQL'], 'neutral', 70),
    ]
    bottom, cx = c.vchain(400, 214, 700, chain, gap=40)

    c.box(560, bottom + 40, 380, 92, 'MySQL 8.0', ['railway 库 · 9 张表 · 唯一真相来源'],
          kind='db', color='db', ts=19, ls=15)
    c.arrow([(cx, bottom), (cx, bottom + 40)], label='写数据（真相源）', label_at=(cx + 150, bottom + 20))

    # 右侧旁挂
    c.box(1160, 470, 300, 120, 'Redis', [
        'Spring Cache：读多写少数据',
        'StringRedisTemplate：余票、幂等键',
        'Redisson：分布式锁 RLock',
    ], kind='db', color='redis', ts=20, ls=14)

    c.box(1160, 650, 300, 120, 'RabbitMQ', [
        '下单异步落库',
        '超时关单（TTL + 死信）',
        '取消/退票释放库存',
    ], kind='db', color='mq', ts=20, ls=14)

    c.arrow([(1100, 530), (1158, 530)], color='#C62828', label='缓存 / 锁', label_size=14)
    c.arrow([(1158, 560), (1102, 560)], color='#C62828', dashed=True, head=False)
    c.arrow([(1100, 600), (1158, 690)], color='#EF6C00', label='发消息', label_size=14)
    c.arrow([(1158, 720), (1102, 630)], color='#EF6C00', dashed=True, head=False)

    # 左侧模块依赖
    c.box(60, 340, 280, 330, '模块依赖方向', [
        'server', '    ↓ 可以依赖', 'framework', '    ↓ 可以依赖', 'pojo', '    ↓ 可以依赖', 'common',
        '', '规则：', '· common / pojo 不含业务逻辑', '· framework 只放技术组件',
        '· 业务代码全部写在 server',
    ], color='note', ts=19, ls=15, kind='round')

    c.label(200, 706, '注意：framework 里不要直接调 Mapper', size=15, bold=True, color='#C62828')
    c.label(200, 732, '否则会破坏依赖方向（见任务 3-3）', size=14, color='#C62828')

    c.legend(60, 880, [
        ('entry', '入口层（Controller / 拦截器）'),
        ('service', '业务层（你写代码的地方）'),
        ('db', 'MySQL 数据库'),
        ('redis', 'Redis 缓存 / 锁'),
        ('mq', 'RabbitMQ 消息队列'),
    ], cols=3, box_w=280, row_h=32)
    c.save('01-architecture.png')


# =====================================================================
# 图 2：一次请求的完整生命周期
# =====================================================================
def fig02():
    c = Canvas(1560, 900, '图 2 · 一次 HTTP 请求的完整生命周期',
               '以「查询余票」为例：请求从进入到返回，中间经过了哪些环节')

    y = 150
    c.box(60, y, 190, 74, '浏览器', ['fetch / curl'], kind='stadium', color='neutral', ts=18, ls=14)
    c.box(310, y - 34, 250, 142, '1. JwtInterceptor', [
        'framework/security/', '', '· 取 Authorization 头', '· 验签 + 验过期',
        '· 与 Redis 里的 token 比对', '· 通过则 UserContext.set()',
    ], color='entry', ts=18, ls=13)
    c.box(620, y - 10, 200, 94, '2. Controller', ['TicketController', '· 接收参数', '· 调 Service'],
          color='entry', ts=18, ls=13)
    c.box(880, y - 34, 250, 142, '3. Service', [
        'TicketServiceImpl', '· 业务逻辑', '· 决定读缓存还是查库',
        '· UserContext.userId() 取当前用户',
    ], color='service', ts=18, ls=13)
    c.box(1190, y - 34, 300, 142, '4. 数据来源', [
        '', 'Redis 缓存命中 → 直接返回', '未命中 → 查 MySQL 再回填缓存',
    ], color='redis_mq', ts=18, ls=14)

    c.arrow([(250, y + 37), (308, y + 37)], label='HTTP', label_size=14)
    c.arrow([(560, y + 37), (618, y + 37)])
    c.arrow([(820, y + 37), (878, y + 37)])
    c.arrow([(1130, y + 37), (1188, y + 37)])

    c.arrow([(1190, y + 175), (880, y + 175), (880, y + 112)], color='#2E7D32', dashed=True, head=False)
    c.arrow([(880, y + 200), (310, y + 200)], color='#2E7D32', dashed=True, head=False)
    c.arrow([(310, y + 200), (155, y + 200), (155, y + 126)], color='#2E7D32')
    c.label(700, y + 226, '5. Result 统一包装后原路返回  {code, message, data}', size=16, color='#2E7D32')

    by = 420
    c.box(60, by, 460, 200, '关键点 A · 拦截器只认 token', [
        '所有 /api/** 都被拦，只有白名单放行：',
        '    /api/auth/login',
        '    /api/auth/register',
        '    /error',
        '',
        '未带 token → HTTP 401（真实状态码）',
        '这是全项目唯一会用非 200 状态码的情况',
    ], color='note', ts=18, ls=14)

    c.box(550, by, 460, 200, '关键点 B · UserContext 是 ThreadLocal', [
        '一个请求 = 一个线程',
        '拦截器把当前用户存进线程私有变量',
        '业务层随时 UserContext.userId() 取用',
        '',
        'afterCompletion 里必须 clear()',
        '否则线程复用时会把用户串到下一个请求',
    ], color='note', ts=18, ls=14)

    c.box(1040, by, 460, 200, '关键点 C · 返回值统一包装', [
        'Controller 返回 Result<T>',
        '    Result.ok(data)         → code 200',
        '    Result.fail(code, msg)  → 业务错误码',
        '',
        '业务异常被 GlobalExceptionHandler 兜住，',
        '转成 HTTP 200 + 业务 code（不是 500）',
    ], color='note', ts=18, ls=14)

    c.box(60, by + 230, 1440, 90, '一句话总结这条链路', [
        '拦截器负责「你是谁」→ Controller 负责「收参数」→ Service 负责「做什么」→ Mapper/Redis 负责「数据从哪来」→ Result 负责「怎么回」。',
    ], color='service', ts=19, ls=15)

    c.save('02-request-lifecycle.png')


# =====================================================================
# 图 3：登录鉴权流程
# =====================================================================
def fig03():
    c = Canvas(1600, 1120, '图 3 · 登录鉴权全流程（JWT + 拦截器，替代 Spring Security）',
               '上半部分：登录怎么签发 token；下半部分：后续请求怎么校验 token')

    c.label(60, 112, '【一】注册 / 登录：签发 token', size=21, bold=True, color='#1A237E', anchor='lm')
    y = 146
    c.box(60, y, 210, 80, 'POST /api/auth/login', ['{username, password}'], kind='stadium',
          color='neutral', ts=16, ls=14)
    c.box(330, y, 250, 80, 'AuthServiceImpl.login()', ['① 按 username 查 sys_user'], color='service', ts=17, ls=14)
    c.box(640, y, 240, 80, 'BCrypt.checkpw()', ['② 明文 vs 库里的密文'], color='service', ts=17, ls=14)
    c.box(940, y, 230, 80, '查角色', ['③ sys_user_role → sys_role'], color='db', ts=17, ls=14)
    c.box(1230, y - 34, 310, 148, '④ 签发 JWT', [
        'JwtUtils.createToken()', '', 'payload 里含：', '    sub = userId',
        '    username / role', '', '有效期 720 分钟',
    ], color='service', ts=17, ls=13)

    c.arrow([(270, y + 40), (328, y + 40)])
    c.arrow([(580, y + 40), (638, y + 40)])
    c.arrow([(880, y + 40), (938, y + 40)])
    c.arrow([(1170, y + 40), (1228, y + 40)])

    c.arrow([(1150, y + 130), (1150, y + 176)], color='#C62828', width=3)
    c.box(830, y + 176, 640, 74, '⑤ token 写入 Redis', [
        'key = user:token:{userId}     value = token     TTL = 720 分钟',
    ], color='redis', ts=18, ls=14)
    c.arrow([(830, y + 213), (330, y + 213), (330, y + 260)], color='#C62828', width=3)
    c.box(60, y + 260, 540, 78, '⑥ 返回 LoginVO', [
        '{token, userId, username, nickname, role}',
    ], color='ok', ts=18, ls=14)

    c.d.line([(60, 560), (1540, 560)], fill='#B0BEC5', width=2)
    c.label(60, 588, '【二】后续每次请求：校验 token', size=21, bold=True, color='#1A237E', anchor='lm')

    y2 = 636
    c.box(60, y2, 200, 76, '请求 /api/**', ['Authorization: Bearer xxx'], kind='stadium', color='neutral', ts=16, ls=13)
    c.box(320, y2 - 24, 210, 124, '有 token ?', [], kind='diamond', color='decision', ts=18)
    c.box(600, y2, 240, 76, '解析 JWT', ['验签 + 验过期 + 取 userId/role'], color='service', ts=17, ls=13)
    c.box(900, y2 - 30, 280, 136, '与 Redis 比对', [
        'key = user:token:{userId}', '', 'Redis 里的值 == 请求的 token ?',
    ], kind='diamond', color='decision', ts=17, ls=14)
    c.box(1260, y2, 260, 76, '放行', ['UserContext.set(LoginUser) → Controller'], color='ok', ts=18, ls=13)

    c.arrow([(260, y2 + 38), (318, y2 + 38)])
    c.arrow([(530, y2 + 38), (598, y2 + 38)], label='有', label_size=14)
    c.arrow([(840, y2 + 38), (898, y2 + 38)])
    c.arrow([(1180, y2 + 38), (1258, y2 + 38)], label='一致', label_size=14)

    # 失败分支放底部一行，避免与主链路线条打架
    c.box(320, y2 + 200, 500, 88, 'HTTP 401   未登录或登录已过期', [
        '① 没带 token    ② token 签名错 / 已过期',
    ], color='fail', ts=17, ls=13)
    c.arrow([(425, y2 + 100), (425, y2 + 198)], color='#C62828', width=3,
            label='没有 / 解析失败', label_at=(588, y2 + 148), label_size=15, label_color='#C62828')

    c.box(900, y2 + 200, 620, 88, 'HTTP 401   登录已失效，请重新登录', [
        'Redis 里的 token 与请求的不一致 → 已登出 / 被别处登录顶号',
    ], color='fail', ts=17, ls=13)
    c.arrow([(1180, y2 + 132), (1180, y2 + 198)], color='#C62828', width=3,
            label='不一致', label_at=(1272, y2 + 165), label_size=15, label_color='#C62828')

    c.box(60, 960, 1480, 120, '三条必须记住的规则', [
        '① 角色（role）是写在 JWT 里的 —— 给用户加了 ADMIN 角色后必须【重新登录】才生效，否则接口一直是 403。',
        '② 同一账号在别处登录会顶掉旧 token（Redis 里的值被覆盖），旧 token 再用会得到「登录已失效，请重新登录」。',
        '③ 两种 401 的 message 不同：没带 token 是「未登录或登录已过期」；被顶号/登出是「登录已失效，请重新登录」。',
    ], color='note', ts=19, ls=15)

    c.save('03-auth-flow.png')


# =====================================================================
# 图 4：余票查询 —— 缓存三兄弟
# =====================================================================
def fig04():
    c = Canvas(1640, 1220, '图 4 · 余票查询流程 —— 缓存穿透 / 击穿 / 雪崩 的完整对策',
               '对应 TicketServiceImpl.queryRemain()，这是准备工程里最值得精读的方法')

    y = 130
    c.box(60, y, 230, 74, 'GET /api/ticket/remain', ['trainId, travelDate, seatType'],
          kind='stadium', color='neutral', ts=15, ls=13)
    c.box(360, y, 300, 74, '拼缓存 key', ['ticket:stock:{trainId}:{date}:{seatType}'],
          color='redis', ts=16, ls=12)
    c.arrow([(290, y + 37), (358, y + 37)])

    c.box(730, y - 10, 200, 94, '读 Redis 缓存', ['GET key'], color='redis', ts=17, ls=13)
    c.arrow([(660, y + 37), (728, y + 37)])

    c.box(990, y - 26, 210, 126, '命中了吗 ?', [], kind='diamond', color='decision', ts=18)
    c.arrow([(930, y + 37), (988, y + 37)])

    c.box(1260, y - 40, 330, 154, '【命中】直接返回', [
        '· 值是 "EMPTY" → 返回 0',
        '  （说明 DB 里也没有，防穿透）',
        '· 否则 → 返回数字',
        '',
        '全程不碰数据库',
    ], color='ok', ts=18, ls=14)
    c.arrow([(1200, y + 37), (1258, y + 37)], label='命中', label_size=15, color='#2E7D32')

    c.box(990, y + 180, 210, 88, '未命中', ['继续往下'], color='fail', ts=17, ls=14)
    c.arrow([(1095, y + 100), (1095, y + 178)], label='未命中', label_size=15, color='#C62828')

    c.box(690, y + 320, 330, 82, '抢「重建锁」', [
        'SET key#rebuild 1 NX EX 10  （setIfAbsent）',
    ], color='redis', ts=17, ls=13)
    c.arrow([(1095, y + 268), (1095, y + 320), (1022, y + 320)], color='#C62828')

    c.box(1080, y + 320, 210, 82, '抢到了吗 ?', [], kind='diamond', color='decision', ts=17)
    c.arrow([(1020, y + 361), (1078, y + 361)])

    c.box(1350, y + 300, 240, 122, '查 MySQL 并回填缓存', [
        '· 查到 → SET key = remain',
        '  TTL = 10 分钟 + 0~60 秒随机',
        '· 没查到 → SET key = "EMPTY"',
        '  TTL = 60 秒',
    ], color='db', ts=16, ls=13)
    c.arrow([(1290, y + 361), (1348, y + 361)], label='抢到', label_size=15, color='#2E7D32')

    c.box(1080, y + 440, 300, 90, '自旋等待（5 次 × 50ms）', [
        '每 50ms 重读一次缓存，别人回填好就返回',
    ], color='neutral', ts=16, ls=13)
    c.arrow([(1185, y + 402), (1185, y + 438)], label='没抢到', label_size=15, color='#C62828')

    c.box(1080, y + 580, 300, 82, '兜底：自己查库回填', ['5 次都没等到 → loadFromDbAndCache()'],
          color='db', ts=16, ls=13)
    c.arrow([(1230, y + 530), (1230, y + 578)])

    # 左侧三兄弟说明（竖向排）
    c.box(60, y + 130, 560, 170, '缓存穿透（Penetration）', [
        '现象：一直查数据库里也不存在的数据，缓存永远不命中，每次都打 DB',
        '对策：把「空」也缓存起来（值为 EMPTY，TTL 60 秒）',
        '本图位置：命中分支里的 EMPTY 判断',
    ], color='redis', ts=18, ls=14)

    c.box(60, y + 320, 560, 190, '缓存击穿（Breakdown）', [
        '现象：某个热点 key 刚好过期的那一瞬间，大量请求同时涌进来，',
        '      全都发现缓存没了 → 一起打 DB',
        '对策：用 setIfAbsent 抢一把「重建锁」，只让一个请求去查库回填，',
        '      其余请求自旋等待',
        '本图位置：抢重建锁 → 自旋等待 → 兜底查库',
    ], color='redis', ts=18, ls=14)

    c.box(60, y + 530, 560, 170, '缓存雪崩（Avalanche）', [
        '现象：大量 key 在同一时刻集体过期，数据库瞬间被打爆',
        '对策：TTL 加随机抖动',
        '      TTL = 10 分钟 + random(0, 60) 秒',
        '本图位置：查库回填时的 TTL 计算',
    ], color='redis', ts=18, ls=14)

    c.box(60, y + 740, 1530, 150, '一个已经踩过的坑：key 用错会 WRONGTYPE', [
        '余票必须用 ticketStockKey()  →  ticket:stock:...     存的是普通字符串',
        '分布式锁用 ticketLockKey()   →  ticket:lock:...       Redisson RLock，存的是 Hash 结构',
        '',
        '最初 queryRemain 误用了 ticketLockKey，把余票字符串写进了锁的 key 空间 —— 结果是库存 key 永远读不到（缓存形同失效），',
        '而且 P3 实现购票加锁时会直接抛 WRONGTYPE。已修复，见「坑 5」。',
    ], color='fail', ts=19, ls=14)

    c.save('04-cache-remain.png')


# =====================================================================
# 图 5：购票主流程（核心）—— 动态排版，底部不再与流程重叠
# =====================================================================
def fig05():
    steps = [
        ('① 幂等校验', [
            'key = order:dup:{userId}:{trainId}:{date}',
            'setIfAbsent(key, "1", 30, SECONDS)',
            '',
            '抢不到 → 抛 1002 请勿重复下单',
        ], 'redis', 132),
        ('② 加锁（防超卖）', [
            'Redisson RLock = ticket:lock:{trainId}:{date}:{seatType}',
            'tryLock(3, 10, SECONDS)',
            '    最多等 3 秒，10 秒后自动释放',
            '',
            '拿不到 → 抛业务异常；拿到后进 try 块',
        ], 'redis', 152),
        ('③ Lua 原子预扣余票', [
            'KEYS[1] = ticket:stock:{trainId}:{date}:{seatType}',
            '    返回 -2 → 缓存没预热（该车次还没放票）',
            '    返回 -1 → 余票不足，抛 1001',
            '    否则    → 返回扣减后的余票',
            '',
            '【注意】判断 + 扣减必须在同一个脚本里，否则会超卖',
        ], 'redis', 172),
        ('④ 生成订单号', [
            'INCR order:seq:{yyyyMMdd}',
            'orderNo = "KR" + yyyyMMdd + 6 位序号',
            '    例：KR20261005000001',
        ], 'redis', 112),
        ('⑤ 发 MQ 消息（异步落库）', [
            'ticketOrderProducer.sendOrderCreate(msg)',
            '消息体用 DTO，带 MessageId 做幂等',
            '',
            '写订单表交给消费者，接口立即返回 → 这就是「削峰」',
        ], 'mq', 142),
        ('⑥ finally 释放锁', [
            'if (lock.isHeldByCurrentThread()) lock.unlock();',
            '',
            '【注意】必须先判断归属，否则锁自动过期后 unlock 会抛',
            '        IllegalMonitorStateException',
        ], 'service', 142),
    ]

    # 先算高度，再建画布
    flow_h = sum(s[3] for s in steps) + 46 * len(steps)
    flow_top = 120
    left_bottom = flow_top + 84 + 46 + flow_h + 46 + 80   # 含底部返回框

    reasons = [
        ('为什么 ① 幂等不能少', '用户手抖点两次「提交订单」，不加幂等键就会生成两个订单。'),
        ('为什么 ② 分布式锁不能少',
         'synchronized 只在单个 JVM 内有效。应用部署 2 个实例时，\n两个 JVM 各有一把自己的锁，仍然会同时扣库存。'),
        ('为什么 ③ 必须用 Lua',
         '如果写成「先 GET 判断，再 DECRBY 扣减」两条命令，\n两个请求可能同时判断「够」，然后都扣 → 超卖。\nLua 脚本在 Redis 里原子执行，中途不会被插队。'),
        ('为什么 ⑤ 要异步落库',
         '同步写订单表会让接口响应变慢，扛不住峰值。\n先扣库存并立即返回，写库交给消费者慢慢做。'),
        ('为什么 ⑥ 要判断 isHeldByCurrentThread',
         '如果业务执行超过了锁的 10 秒 TTL，锁已经自动释放，\n此时直接 unlock() 会抛 IllegalMonitorStateException。'),
    ]
    right_h = 96 + 18
    for t, body in reasons:
        right_h += 60 + 24 * len(body.split('\n')) + 18

    bottom_h = 240
    H = max(left_bottom, 120 + right_h) + bottom_h + 60

    c = Canvas(1660, H, '图 5 · 购票主流程（核心链路）—— Redisson 锁 + Lua 预扣 + MQ 异步落库',
               '对应 TicketServiceImpl.buy()，这是整个项目最重要的方法（任务 3-1）')

    x, W = 110, 470
    c.box(x, flow_top, W, 84, 'POST /api/ticket/buy',
          ['{trainId, travelDate, seatType, ticketCount(1~5)}'], kind='stadium', color='neutral', ts=17, ls=13)
    bottom, cx = c.vchain(x, flow_top + 84, W, steps)
    c.box(x, bottom + 46, W, 80, '返回 TicketBuyVO', [
        '{orderNo, userId, status:0}',
        'message = "下单成功，请在 30 分钟内完成支付"',
    ], color='ok', ts=17, ls=13)
    c.arrow([(cx, bottom), (cx, bottom + 46)])

    # 右侧说明
    rx = 660
    c.box(rx, 120, 900, 96, '这条链路要解决的核心问题：不超卖 + 抗峰值', [
        '库存 5 张票，20 个人同时抢 —— 必须正好成功 5 个，余票不能为负，订单表不能有重复订单号',
    ], color='note', ts=19, ls=15)
    ry = 234
    for t, body in reasons:
        lines = body.split('\n')
        h = 60 + 24 * len(lines)
        c.box(rx, ry, 900, h, t, lines, color='note', ts=18, ls=15)
        ry += h + 18

    # 底部对照（动态定位，绝不与流程重叠）
    by = max(left_bottom, ry) + 40
    c.box(x, by, W, 200, '× 错误写法（会超卖）', [
        'String stock = redis.get(key);',
        'if (Integer.parseInt(stock) >= count) {',
        '    redis.decrBy(key, count);',
        '}',
        '',
        '两个线程同时判断「够」，然后都扣 → 超卖',
    ], color='fail', ts=18, ls=14)

    c.box(rx, by, 900, 200, '√ 正确写法（Lua 原子）', [
        "local stock = redis.call('GET', KEYS[1])",
        'if not stock then return -2 end',
        'if tonumber(stock) < tonumber(ARGV[1]) then return -1 end',
        "return redis.call('DECRBY', KEYS[1], ARGV[1])",
        '',
        '判断和扣减在一个脚本里，Redis 保证原子性 —— 不可能超卖',
    ], color='ok', ts=18, ls=14)

    c.save('05-buy-flow.png')


# =====================================================================
# 图 6：订单状态机 —— 重新设计，箭头走框外，SQL 说明移到下方
# =====================================================================
def fig06():
    c = Canvas(1600, 1080, '图 6 · 订单状态机',
               '订单只能沿着箭头方向流转；每次流转都靠「带条件的 UPDATE」保证不会并发错乱')

    # 状态节点：左列 0→2，右列 1→3→4
    b0 = c.box(140, 150, 300, 100, '0  待支付', ['下单成功后的初始状态'], kind='stadium',
               color='decision', ts=22, ls=15)
    b1 = c.box(1150, 150, 300, 100, '1  已支付', ['调用支付回调后'], kind='stadium',
               color='ok', ts=22, ls=15)
    b2 = c.box(140, 620, 300, 100, '2  已取消', ['超时关单 或 用户主动取消'], kind='stadium',
               color='fail', ts=22, ls=15)
    b3 = c.box(1150, 360, 300, 100, '3  已出票', ['（选做，本项目未实现）'], kind='stadium',
               color='neutral', ts=22, ls=15)
    b4 = c.box(1150, 620, 300, 100, '4  已退票', ['对已支付/已出票的订单退票'], kind='stadium',
               color='neutral', ts=22, ls=15)

    # 0 → 2（竖向，标签放左侧空白区）
    c.arrow([(290, 250), (290, 618)], color='#C62828', width=4)
    c.label(285, 372, '超时 30 分钟未支付（任务 4-3）', size=17, bold=True, color='#C62828', anchor='rm')
    c.label(285, 400, 'TTL + 死信队列触发关单', size=15, color='#C62828', anchor='rm')
    c.label(285, 434, '用户主动取消（任务 4-1）', size=17, bold=True, color='#C62828', anchor='rm')
    c.label(285, 462, 'POST /api/order/{orderNo}/cancel', size=14, color='#C62828', anchor='rm')

    # 0 → 1（横向，标签放线上方空白处）
    c.arrow([(440, 200), (1148, 200)], color='#2E7D32', width=4)
    c.label(794, 168, 'POST /api/pay/callback（任务 4-2）', size=18, bold=True, color='#2E7D32')

    # 1 → 3
    c.arrow([(1300, 250), (1300, 358)], color='#455A64', width=4)
    c.label(1320, 304, '出票（选做）', size=15, color='#455A64', anchor='lm')

    # 3 → 4
    c.arrow([(1300, 460), (1300, 618)], color='#455A64', width=4)
    c.label(1320, 539, '退票（任务 4-4）', size=15, color='#455A64', anchor='lm')

    # 1 → 4：走最右侧外围绕行，不穿任何框
    c.arrow([(1450, 200), (1560, 200), (1560, 670), (1452, 670)], color='#455A64', width=4)
    c.label(1520, 430, '退票', size=16, color='#455A64', anchor='rm')

    # 中间空白区放「两种取消触发方式」说明
    c.box(560, 380, 470, 180, '0 → 2 有两种触发方式', [
        '① 超时关单（任务 4-3）',
        '   下单后 30 分钟未支付，由 MQ 延时消息自动触发',
        '',
        '② 用户主动取消（任务 4-1）',
        '   用户调 cancel 接口，条件 UPDATE 校验后流转',
        '',
        '两条路径最终都会发释放库存消息',
    ], color='note', ts=17, ls=14)

    # 底部：每次流转对应的 SQL
    c.box(60, 760, 1480, 130, '每次流转都靠「带条件的 UPDATE」—— 状态判断必须放进 WHERE', [
        '❌ 错误：先查后改，两步之间有窗口          int st = order.getStatus();  if (st == 0) { order.setStatus(2); update(); }',
        '                                           → 查完到改之间，用户可能已经支付成功！',
        '√ 正确：把状态判断放进 WHERE，靠影响行数判断   UPDATE train_order SET status=2 WHERE order_no=? AND user_id=? AND status=0',
        '                                           → rows != 1 就说明状态已经变了，直接中止',
    ], color='service', ts=18, ls=14)

    c.box(60, 920, 1480, 120, '每个状态对应的库存动作', [
        '0 → 1 已支付：不动库存（票已经卖出）              0 → 2 已取消：库存回补（+ticketCount）',
        '1 / 3 → 4 已退票：库存回补                        触发方式：发 railway.stock.release.queue 消息',
        '注意：库存回补是【异步】的，靠 MQ 消费者完成，所以必须保证幂等 —— 不能加两次。',
    ], color='note', ts=18, ls=14)

    c.save('06-order-state.png')


# =====================================================================
# 图 7：MQ 异步链路 + 超时关单
# =====================================================================
def fig07():
    c = Canvas(1660, 1180, '图 7 · RabbitMQ 异步链路 —— 下单落库 与 超时关单',
               '上方：下单消息如何异步写入数据库；下方：30 分钟未支付如何自动关单（TTL + 死信队列）')

    c.label(60, 112, '【一】下单消息：异步落库（任务 3-1 / 3-2 / 3-3）', size=21, bold=True, color='#1A237E', anchor='lm')

    y = 150
    c.box(60, y, 260, 96, 'TicketServiceImpl.buy()', ['扣完库存后发消息'], color='service', ts=18, ls=13)
    c.box(400, y - 16, 300, 128, 'TicketOrderProducer', [
        'sendOrderCreate(msg)', '· 消息体用 DTO', '· 设 MessageId（幂等）', '· 开 confirm 回调',
    ], color='mq', ts=18, ls=13)
    c.arrow([(320, y + 48), (398, y + 48)])

    c.box(780, y - 16, 320, 128, 'railway.order.create.queue', [
        '持久化队列', 'publisher-confirm: correlated',
    ], kind='db', color='mq', ts=17, ls=13)
    c.arrow([(700, y + 48), (778, y + 48)])

    c.box(1180, y - 16, 420, 128, 'OrderCreateConsumer', [
        '@RabbitListener(queues = ORDER_CREATE_QUEUE)',
        '只做转发：调 OrderService.createOrder()',
    ], color='service', ts=17, ls=14)
    c.arrow([(1100, y + 48), (1178, y + 48)])

    c.box(1180, y + 190, 420, 176, 'OrderServiceImpl.createOrder()', [
        '① 按 orderNo 查一次 → 已存在则 return（幂等第一层）',
        '② INSERT train_order（status=0，写 pay_expire_time）',
        '③ INSERT train_order_item（订单明细）',
        '④ 捕获 DuplicateKeyException 当作已处理',
        '   （唯一索引兜底，幂等第二层）',
    ], color='service', ts=16, ls=13)
    c.arrow([(1390, y + 112), (1390, y + 188)])

    c.box(1180, y + 410, 420, 76, 'MySQL train_order', ['uk_order_no 唯一索引保证不重复'],
          kind='db', color='db', ts=18, ls=13)
    c.arrow([(1390, y + 366), (1390, y + 408)])

    c.box(60, y + 190, 1060, 296, '为什么要异步落库（这就是「削峰」）', [
        '同步写法：用户点购票 → 扣库存 → 写订单表 → 写明细 → 返回。写库慢，并发一高接口就超时。',
        '',
        '异步写法：用户点购票 → 扣库存 → 丢一条消息进队列 → 立刻返回「下单成功」。',
        '          写库由消费者在后台做，接口响应时间大幅缩短。',
        '',
        '代价：用户拿到 200 时，订单其实还没落库（最终一致）。所以前端要能接受',
        '      「下单成功但订单列表暂时看不到」这种情况。',
        '',
        '【注意】分层要求：消费者在 framework 模块，不要直接调 Mapper。',
        '         正确做法是 framework 的消费者 → 调 server 的 OrderService → 再由它调 Mapper。',
    ], color='note', ts=18, ls=14)

    c.d.line([(60, 720), (1600, 720)], fill='#B0BEC5', width=2)
    c.label(60, 750, '【二】超时关单：TTL + 死信队列实现「延时消息」（任务 4-3）', size=21, bold=True,
            color='#1A237E', anchor='lm')

    y2 = 800
    c.box(60, y2, 250, 110, '下单成功', ['createOrder 里发出延时消息'], color='ok', ts=18, ls=13)
    c.box(390, y2, 350, 110, 'railway.order.timeout.queue', [
        '【故意不设消费者】', 'x-message-ttl = 1800000（30 分钟）',
    ], kind='db', color='mq', ts=17, ls=13)
    c.arrow([(310, y2 + 55), (388, y2 + 55)])

    c.label(565, y2 + 140, '消息在队列里躺 30 分钟没人消费 → 过期', size=16, color='#EF6C00')
    c.arrow([(565, y2 + 162), (565, y2 + 196)])

    c.box(390, y2 + 196, 350, 110, '被当作「死信」转发', [
        'x-dead-letter-routing-key =', 'railway.order.close.queue',
    ], color='mq', ts=17, ls=13)

    c.box(820, y2 + 196, 300, 110, 'railway.order.close.queue', [
        '真正的关单队列', '有消费者监听这里',
    ], kind='db', color='mq', ts=17, ls=13)
    c.arrow([(740, y2 + 251), (818, y2 + 251)])

    c.box(1200, y2 + 176, 400, 150, 'OrderTimeoutConsumer', [
        '查订单当前状态：',
        '· 仍是 0 待支付 → 置为 2，并发释放库存消息',
        '· 已是 1 已支付  → 直接丢弃消息',
        '· 已是 2 已取消  → 直接丢弃消息',
    ], color='service', ts=16, ls=13)
    c.arrow([(1120, y2 + 251), (1198, y2 + 251)])

    c.box(60, y2 + 196, 290, 110, '为什么不用消息级 TTL', [
        '消息级 TTL 有坑：队列先进先出，',
        '队头消息没过期会挡住后面所有消息。',
        '所以用队列级 TTL 更可靠。',
    ], color='fail', ts=16, ls=13)

    c.save('07-mq-async.png')


# =====================================================================
# 图 8：取消订单 / 退票
# =====================================================================
def fig08():
    c = Canvas(1600, 1060, '图 8 · 取消订单 与 退票（库存回补的两条路径）',
               '两者都会回补库存，但对象与复杂度不同：取消针对未付款订单，退票涉及已付款的资金回流')

    c.label(80, 112, '路径 A · 取消订单（任务 4-1）', size=21, bold=True, color='#C62828', anchor='lm')
    c.label(840, 112, '路径 B · 退票（任务 4-4，选做）', size=21, bold=True, color='#455A64', anchor='lm')

    # 路径 A
    x, W = 80, 460
    y = 150
    stepsA = [
        ('POST /api/order/{orderNo}/cancel', ['用户点「取消订单」'], 'neutral', 76),
        ('① 一条带条件的 UPDATE', [
            'UPDATE train_order SET status = 2',
            'WHERE order_no = ?',
            '  AND user_id = ?      ← 只能取消自己的',
            '  AND status  = 0      ← 只有待支付能取消',
            '',
            '靠影响行数判断：rows != 1 → 抛业务异常',
        ], 'service', 168),
        ('② 发释放库存消息', [
            'ticketOrderProducer.sendStockRelease(msg)',
            '→ railway.stock.release.queue',
        ], 'mq', 90),
        ('③ StockReleaseConsumer（任务 3-4）', [
            'Redis：INCRBY ticket:stock:{trainId}:{date}:{seatType}',
            'MySQL：UPDATE train_seat SET remain = remain + ?',
            '        WHERE ... AND remain + ? <= total   ← 上限保护',
            '',
            '【注意】必须用 ticketStockKey，不能用 ticketLockKey',
        ], 'service', 150),
    ]
    bottomA, cxA = c.vchain(x, y, W, stepsA)

    # 路径 B
    x2, W2 = 840, 680
    stepsB = [
        ('POST /api/order/{orderNo}/refund', ['用户对【已支付】的订单申请退票'], 'neutral', 76),
        ('① 条件 UPDATE（可退状态更宽）', [
            'UPDATE train_order SET status = 4   （已退票）',
            'WHERE order_no = ? AND user_id = ?',
            '  AND status IN (1, 3)      ← 已支付 / 已出票才可退',
        ], 'service', 122),
        ('② 退款（外部调用，难点所在）', [
            '真实场景要调支付平台的退款接口 ——',
            '· 不受你的事务控制',
            '· 可能超时、可能失败、可能重复回调',
            '',
            '所以真实系统通常引入「退款中」中间状态 + 定时补偿',
        ], 'fail', 158),
        ('③ 回补库存（复用同一套）', [
            '与路径 A 完全一样：发 railway.stock.release.queue 消息',
            '由 StockReleaseConsumer 统一处理',
            '',
            '√ 好处：回补逻辑只写一次，取消 / 超时 / 退票三条路径共用',
        ], 'mq', 140),
    ]
    bottomB, cxB = c.vchain(x2, y, W2, stepsB)

    by = max(bottomA, bottomB) + 50
    c.box(80, by, 1440, 190, '两条路径的对比 —— 以及一个必须注意的事务陷阱', [
        '· 对象不同：取消只能针对「待支付」（钱还没付）；退票针对「已支付 / 已出票」（涉及资金回流）。',
        '· 复杂度不同：取消只改状态；退票要调外部退款接口，结果异步且可能失败，需要额外状态跟踪。',
        '· 库存动作相同：都靠 railway.stock.release.queue 消息回补，消费者只写一份。',
        '',
        '【注意】事务陷阱：不要在 @Transactional 方法内部发 MQ 消息！事务未提交时消息已到消费者，消费者会读到旧状态。',
        '解决方案：① 不加事务（本项目推荐，只有一条 UPDATE）  ② 事务提交后再发（afterCommit）  ③ 本地消息表。',
    ], color='note', ts=18, ls=15)

    c.save('08-cancel-refund.png')


# =====================================================================
# 图 9：数据一致性全景
# =====================================================================
def fig09():
    c = Canvas(1640, 1040, '图 9 · 数据一致性全景 —— MySQL / Redis / RabbitMQ 三者关系',
               'MySQL 是唯一真相来源；Redis 是加速层（可能不一致）；定时对账负责兜底')

    c.box(80, 130, 420, 140, 'MySQL（唯一真相来源）', [
        'train_seat.total / remain    ← 真实库存',
        'train_order                   ← 真实订单',
        '任何时刻都以它为准',
    ], kind='db', color='db', ts=20, ls=15)

    c.box(610, 130, 420, 140, 'Redis（加速层）', [
        'ticket:stock:*     余票缓存',
        'order:dup:*        幂等键（30 秒）',
        'ticket:lock:*      Redisson 分布式锁',
        'user:token:*       登录 token',
    ], kind='db', color='redis', ts=20, ls=14)

    c.box(1140, 130, 420, 140, 'RabbitMQ（异步层）', [
        'order.create.queue    下单落库',
        'order.timeout.queue   延时（无消费者）',
        'order.close.queue     超时关单',
        'stock.release.queue   库存回补',
    ], kind='db', color='mq', ts=20, ls=14)

    c.box(80, 320, 1480, 130, '为什么会有不一致？—— 三个真实场景', [
        '① 缓存过期策略：余票缓存 TTL 10 分钟，这 10 分钟内 Redis 的数字就是「权威」，DB 改了也不会立刻反映。',
        '② Redis 重启 / 数据丢失：内存数据没了，但 DB 还在 —— 此时查余票会拿到 0（EMPTY 标记）或直接查库回填。',
        '③ 异步链路中断：扣了 Redis 库存，但 MQ 消费失败，订单没落库 —— 用户以为下单成功了，订单列表里却没有。',
    ], color='fail', ts=18, ls=15)

    c.box(80, 490, 720, 230, '兜底手段 A · 定时对账（任务 2-3）', [
        '思路：定时用 MySQL 的值【强制覆盖】Redis',
        '',
        '@Scheduled(cron = "0 */5 * * * ?")     // 每 5 分钟',
        '① 查出今天及未来 7 天的 train_seat',
        '② 逐条把 remain 写回 ticket:stock:*',
        '③ 日志打印对账条数',
        '',
        '验证：手动把 Redis 改成 999，等一轮对账，值会被 DB 校准回 500',
    ], color='service', ts=18, ls=14)

    c.box(840, 490, 720, 230, '兜底手段 B · 唯一索引防重（已就绪）', [
        'train_order.order_no 上已有唯一索引 uk_order_no',
        '（见 sql/init.sql 第 119 行）',
        '',
        '作用：即使 MQ 消息重复投递、或并发消费，',
        '      第二条插入也会被数据库拒绝（DuplicateKeyException）',
        '',
        '这是幂等的【最后一道防线】，',
        '应用层的「先查再插」是第一道防线。',
    ], color='service', ts=18, ls=14)

    c.box(80, 760, 1480, 220, '本项目的「一致性等级」—— 明确知道自己在什么水平', [
        '· 库存扣减：强一致（Redis Lua 原子 + 分布式锁），不会超卖。',
        '· 订单落库：最终一致（MQ 异步），用户拿到 200 时订单可能还没写进 DB。',
        '· 缓存与 DB：最终一致（TTL 过期 + 定时对账兜底），最多容忍 10 分钟偏差。',
        '',
        '这不是「设计缺陷」，而是高并发系统的常态取舍 —— 用一致性换吞吐。面试时能讲清这一点，比堆功能更有价值。',
        '想进一步加强：本地消息表（把「要发的消息」和业务数据写进同一个事务），或引入 Canal 监听 binlog（本项目已移除）。',
    ], color='note', ts=19, ls=15)

    c.save('09-data-consistency.png')


if __name__ == '__main__':
    print('生成业务流程图 ->', OUT_DIR)
    for fn in (fig01, fig02, fig03, fig04, fig05, fig06, fig07, fig08, fig09):
        fn()
    print('全部完成')
