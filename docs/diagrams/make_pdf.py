# -*- coding: utf-8 -*-
"""
把 9 张业务流程图合并成一个多页 PDF。
用途：语雀 / 飞书等网页版 Markdown 不解析相对路径图片，
     把 PDF 上传上去（或直接用 PDF 阅读器打开）就能一次看全。

用法：
    python make_pdf.py
输出：
    docs/diagrams/康轨业务流程图.pdf
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONT_BOLD = r'C:\Windows\Fonts\msyhbd.ttc'
FONT_REG = r'C:\Windows\Fonts\msyh.ttc'

PAGES = [
    ('01-architecture.png',      '图 1 · 系统分层架构与依赖方向',
     '代码写在哪、模块之间谁能依赖谁'),
    ('02-request-lifecycle.png', '图 2 · 一次 HTTP 请求的完整生命周期',
     '拦截器 → Controller → Service → 数据源 → 返回'),
    ('03-auth-flow.png',         '图 3 · 登录鉴权全流程',
     '上半：登录怎么签发 token；下半：后续请求怎么校验 token'),
    ('04-cache-remain.png',      '图 4 · 余票查询与「缓存三兄弟」',
     '缓存穿透 / 击穿 / 雪崩 的完整对策'),
    ('05-buy-flow.png',          '图 5 · 购票主流程（核心链路）',
     'Redisson 锁 + Lua 预扣 + MQ 异步落库 —— 整个项目最重要的一张图'),
    ('06-order-state.png',       '图 6 · 订单状态机',
     '状态流转 + 为什么状态判断必须放进 UPDATE 的 WHERE 条件'),
    ('07-mq-async.png',          '图 7 · MQ 异步链路与超时关单',
     '上半：异步落库（削峰）；下半：TTL + 死信队列实现延时消息'),
    ('08-cancel-refund.png',     '图 8 · 取消订单与退票',
     '库存回补的两条路径 + 一个必须注意的事务陷阱'),
    ('09-data-consistency.png',  '图 9 · 数据一致性全景',
     'MySQL / Redis / RabbitMQ 三者关系与一致性等级'),
]

A4_W, A4_H = 1654, 2339          # A4 @150dpi
MARGIN = 70
HEADER_H = 130


def make_cover():
    img = Image.new('RGB', (A4_W, A4_H), 'white')
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, A4_W, 420], fill='#1A237E')
    d.text((MARGIN, 150), '康轨 KangRail', font=ImageFont.truetype(FONT_BOLD, 76), fill='white')
    d.text((MARGIN, 260), '业务流程图（9 张）', font=ImageFont.truetype(FONT_BOLD, 52), fill='#C5CAE9')

    y = 520
    d.text((MARGIN, y), '从零看懂业务层', font=ImageFont.truetype(FONT_BOLD, 40), fill='#263238')
    y += 90
    for i, (_, title, sub) in enumerate(PAGES, 1):
        d.text((MARGIN, y), title, font=ImageFont.truetype(FONT_BOLD, 28), fill='#1A237E')
        y += 42
        d.text((MARGIN + 40, y), sub, font=ImageFont.truetype(FONT_REG, 23), fill='#546E7A')
        y += 62

    y += 40
    d.line([(MARGIN, y), (A4_W - MARGIN, y)], fill='#CFD8DC', width=3)
    y += 40
    for line in [
        '说明：',
        '· 这些图是真实 PNG 文件，放在 docs/diagrams/ 目录下。',
        '· 网页版 Markdown（语雀 / 飞书 / Notion）不解析相对路径，',
        '  所以那边看不到图 —— 用浏览器打开 docs/diagrams/index.html，',
        '  或者直接看本 PDF。',
        '· 想改图：编辑 docs/diagrams/make_diagrams.py 后重跑该脚本。',
    ]:
        d.text((MARGIN, y), line, font=ImageFont.truetype(FONT_REG, 24), fill='#37474F')
        y += 44
    return img


def make_page(png, title, sub):
    img = Image.new('RGB', (A4_W, A4_H), 'white')
    d = ImageDraw.Draw(img)

    d.text((MARGIN, 60), title, font=ImageFont.truetype(FONT_BOLD, 34), fill='#1A237E')
    d.text((MARGIN, 108), sub, font=ImageFont.truetype(FONT_REG, 22), fill='#607D8B')
    d.line([(MARGIN, HEADER_H + 26), (A4_W - MARGIN, HEADER_H + 26)], fill='#CFD8DC', width=2)

    src = Image.open(os.path.join(HERE, png))
    avail_w = A4_W - MARGIN * 2
    avail_h = A4_H - (HEADER_H + 80) - MARGIN
    scale = min(avail_w / src.width, avail_h / src.height)
    new = src.resize((int(src.width * scale), int(src.height * scale)), Image.LANCZOS)
    img.paste(new, ((A4_W - new.width) // 2, HEADER_H + 70))
    return img


if __name__ == '__main__':
    pages = [make_cover()]
    for png, title, sub in PAGES:
        pages.append(make_page(png, title, sub))
    out = os.path.join(HERE, '康轨业务流程图.pdf')
    pages[0].save(out, save_all=True, append_images=pages[1:], resolution=150.0)
    size = os.path.getsize(out) / 1024 / 1024
    print('saved:', out)
    print('pages:', len(pages), ' size:', round(size, 2), 'MB')
