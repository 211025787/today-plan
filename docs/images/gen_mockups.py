"""生成 README 用的界面预览图（纯手绘 SVG 示意图，非真实截图）。

运行：python docs/images/gen_mockups.py
"""
import os

OUT = os.path.dirname(os.path.abspath(__file__))

PRIMARY = "#4772FA"
VIOLET = "#9B8CFF"
INK = "#1B1B2F"
MUTED = "#8A8FA3"
BG = "#F4F5FB"
LINE = "#E6E8F2"

WEEK = ["一", "二", "三", "四", "五", "六", "日"]


def head(w, h, extra=""):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
        f'viewBox="0 0 {w} {h}" font-family="-apple-system, \'PingFang SC\', sans-serif">'
        f"<defs>{extra}</defs>"
    )


def phone_frame(w, h, body):
    """手机外框：浅色屏幕 + 状态栏。"""
    return (
        f'<rect x="0" y="0" width="{w}" height="{h}" rx="26" fill="{BG}"/>'
        f'<rect x="0" y="0" width="{w}" height="{h}" rx="26" fill="none" stroke="{LINE}"/>'
        f'<text x="24" y="26" font-size="10" fill="{MUTED}">9:41</text>'
        f'<rect x="{w-58}" y="18" width="16" height="8" rx="2" fill="{MUTED}" opacity="0.6"/>'
        f'<rect x="{w-38}" y="17" width="14" height="10" rx="3" fill="{MUTED}" opacity="0.6"/>'
        + body
    )


def defs_common():
    return (
        '<linearGradient id="cardBlue" x1="0" y1="0" x2="1" y2="1">'
        f'<stop offset="0" stop-color="{PRIMARY}"/><stop offset="1" stop-color="#2C3E9B"/>'
        "</linearGradient>"
        '<linearGradient id="cardViolet" x1="0" y1="0" x2="1" y2="1">'
        f'<stop offset="0" stop-color="#8E7BF0"/><stop offset="1" stop-color="#4B3A9E"/>'
        "</linearGradient>"
        '<radialGradient id="halo" cx="50%" cy="50%" r="50%">'
        f'<stop offset="0" stop-color="{VIOLET}" stop-opacity="0.30"/>'
        f'<stop offset="0.55" stop-color="{PRIMARY}" stop-opacity="0.12"/>'
        '<stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>'
        "</radialGradient>"
        '<linearGradient id="bannerBg" x1="0" y1="0" x2="1" y2="1">'
        '<stop offset="0" stop-color="#12132A"/><stop offset="1" stop-color="#241C4D"/>'
        "</linearGradient>"
        '<filter id="soft" x="-30%" y="-30%" width="160%" height="160%">'
        '<feGaussianBlur stdDeviation="22"/></filter>'
    )


def banner():
    w, h = 1200, 300
    s = [head(w, h, defs_common())]
    s.append(f'<rect width="{w}" height="{h}" rx="18" fill="url(#bannerBg)"/>')
    # 背景光斑
    s.append(f'<circle cx="150" cy="60" r="220" fill="{PRIMARY}" opacity="0.35" filter="url(#soft)"/>')
    s.append(f'<circle cx="1020" cy="260" r="240" fill="{VIOLET}" opacity="0.30" filter="url(#soft)"/>')
    # 右侧装饰：光环
    for i in range(9):
        r = 60 + i * 18
        op = 0.20 - i * 0.017
        s.append(
            f'<circle cx="960" cy="150" r="{r}" fill="none" stroke="{VIOLET}" '
            f'stroke-opacity="{op:.3f}" stroke-width="1"/>'
        )
    s.append(f'<circle cx="960" cy="150" r="52" fill="none" stroke="white" stroke-opacity="0.85" stroke-width="8"/>')
    s.append(f'<path d="M960 98 A52 52 0 0 1 1005 124" fill="none" stroke="{VIOLET}" stroke-width="8" stroke-linecap="round"/>')
    s.append('<text x="96" y="132" font-size="46" font-weight="600" fill="#FFFFFF">滴答清单 · 离线版</text>')
    s.append('<text x="98" y="176" font-size="19" fill="#C9C7E8">日历计划 × 番茄专注 × 当日备注，纯本地存储的轻量时间管理</text>')
    s.append(f'<rect x="98" y="204" width="126" height="34" rx="17" fill="#FFFFFF" fill-opacity="0.12"/>')
    s.append('<text x="161" y="226" font-size="14" fill="#FFFFFF" text-anchor="middle">v1.0.30</text>')
    s.append(f'<rect x="236" y="204" width="150" height="34" rx="17" fill="#FFFFFF" fill-opacity="0.12"/>')
    s.append('<text x="311" y="226" font-size="14" fill="#FFFFFF" text-anchor="middle">Kotlin · Compose</text>')
    s.append("</svg>")
    return s


def screen_calendar():
    w, h = 360, 760
    body = [
        f'<text x="24" y="66" font-size="22" font-weight="600" fill="{INK}">2026 年 10 月</text>',
        f'<text x="264" y="62" font-size="17" fill="{MUTED}">‹  ›</text>',
        f'<text x="308" y="62" font-size="15" fill="{MUTED}">🗑</text>',
    ]
    # 星期表头
    for i, d in enumerate(WEEK):
        body.append(
            f'<text x="{24 + i * 46 + 21}" y="96" font-size="11" fill="{MUTED}" text-anchor="middle">{d}</text>'
        )
    # 日历格子：42 天，部分格子带 3 条计划进度条
    plan_colors = [PRIMARY, "#8E7BF0", "#3FA796"]
    day = 1
    for row in range(6):
        for col in range(7):
            x = 24 + col * 46
            y = 112 + row * 92
            if row == 0 and col < 3:
                num = 28 + col + 1
                muted = True
            else:
                if day > 31:
                    num = day - 31
                    muted = True
                else:
                    num = day
                    muted = False
                day += 1
            is_today = (not muted and num == 30)
            body.append(f'<rect x="{x}" y="{y}" width="43" height="86" rx="9" fill="#FFFFFF" fill-opacity="{0.55 if muted else 0.9}"/>')
            if is_today:
                body.append(f'<circle cx="{x + 14}" cy="{y + 14}" r="9" fill="{PRIMARY}"/>')
                body.append(f'<text x="{x + 14}" y="{y + 18}" font-size="11" fill="#FFFFFF" text-anchor="middle">{num}</text>')
            else:
                color = MUTED if muted else INK
                op = "0.35" if muted else "1"
                body.append(f'<text x="{x + 15}" y="{y + 18}" font-size="11" fill="{color}" fill-opacity="{op}" text-anchor="middle">{num}</text>')
            if not muted and (num % 3 != 1) and row < 5:
                bars = 3 if num % 2 == 0 else 2
                for b in range(bars):
                    by = y + 26 + b * 13
                    c = plan_colors[(num + b) % 3]
                    body.append(f'<rect x="{x + 5}" y="{by}" width="33" height="9" rx="3" fill="{c}" fill-opacity="0.16"/>')
                    frac = [0.75, 1.0, 0.2][b % 3]
                    body.append(f'<rect x="{x + 5}" y="{by}" width="{33 * frac:.1f}" height="9" rx="3" fill="{c}" fill-opacity="0.45"/>')
            elif not muted and num % 3 == 1:
                body.append(f'<text x="{x + 5}" y="{y + 34}" font-size="7.5" fill="{MUTED}">无计划</text>')
    # 底部 Dock
    body.append(f'<rect x="34" y="694" width="292" height="52" rx="26" fill="#FFFFFF" fill-opacity="0.85" stroke="{LINE}"/>')
    labels = ["日历", "专注", "习惯", "我的"]
    for i, t in enumerate(labels):
        cx = 34 + 36 + i * 74
        active = i == 0
        body.append(f'<circle cx="{cx}" cy="720" r="9" fill="{PRIMARY if active else MUTED}" fill-opacity="{1 if active else 0.45}"/>')
        body.append(f'<text x="{cx}" y="739" font-size="8.5" fill="{PRIMARY if active else MUTED}" text-anchor="middle">{t}</text>')
    return phone_frame(w, h, "".join(body))


def screen_focus():
    w, h = 360, 760
    cx, cy = 180, 322
    body = [
        f'<text x="24" y="66" font-size="22" font-weight="600" fill="{INK}">专注</text>',
        f'<circle cx="320" cy="58" r="13" fill="#FFFFFF" fill-opacity="0.8" stroke="{LINE}"/>',
        f'<circle cx="320" cy="58" r="4.5" fill="none" stroke="{MUTED}" stroke-width="1.4"/>',
        f'<rect x="96" y="88" width="168" height="30" rx="15" fill="#FFFFFF" fill-opacity="0.75" stroke="{LINE}"/>',
        f'<text x="180" y="108" font-size="10.5" fill="{MUTED}" text-anchor="middle">今日已专注 185 分钟 · 7 个番茄</text>',
        f'<text x="180" y="146" font-size="12" fill="{MUTED}" text-anchor="middle">专注中</text>',
        # 外层光晕（多层柔光叠加）
        f'<circle cx="{cx}" cy="{cy}" r="168" fill="url(#halo)"/>',
        f'<circle cx="{cx}" cy="{cy}" r="120" fill="url(#halo)"/>',
    ]
    # 同心弧光环：更多圈、更明显
    for i in range(18):
        r = 94 + i * 4
        op = 0.30 - i * 0.013
        start = (i * 137) % 360
        sweep = 150 + 200 * ((i * 1.7) % 1)
        body.append(
            f'<path d="M {cx} {cy} m 0 -{r} a {r} {r} 0 1 1 -0.01 0" fill="none" '
            f'stroke="{VIOLET}" stroke-opacity="{op:.3f}" stroke-width="{1.0 + (i % 3) * 0.6:.1f}" '
            f'stroke-dasharray="{r * 3.14 * sweep / 360:.0f} {r * 3.14:.0f}" '
            f'transform="rotate({start} {cx} {cy})"/>'
        )
    # 反向扫光
    body.append(f'<path d="M {cx} {cy - 150} A 150 150 0 0 1 {cx + 130} {cy - 76}" fill="none" stroke="{VIOLET}" stroke-opacity="0.26" stroke-width="18" stroke-linecap="round"/>')
    body.append(f'<path d="M {cx} {cy - 150} A 150 150 0 0 1 {cx + 130} {cy - 76}" fill="none" stroke="#FFFFFF" stroke-opacity="0.35" stroke-width="7" stroke-linecap="round"/>')
    # 计时环
    body.append(f'<circle cx="{cx}" cy="{cy}" r="76" fill="#FFFFFF" fill-opacity="0.35"/>')
    body.append(f'<circle cx="{cx}" cy="{cy}" r="76" fill="none" stroke="#DCDEF0" stroke-width="13"/>')
    body.append(f'<path d="M {cx} {cy - 76} A 76 76 0 0 1 {cx + 66} {cy + 41}" fill="none" stroke="{PRIMARY}" stroke-width="13" stroke-linecap="round"/>')
    body.append(f'<text x="{cx}" y="{cy + 3}" font-size="34" font-weight="500" fill="{INK}" text-anchor="middle">24:36</text>')
    body.append(f'<text x="{cx}" y="{cy + 27}" font-size="10.5" fill="{MUTED}" text-anchor="middle">第 5 个番茄</text>')
    # 白噪音 chips
    chips = [("静音", False), ("白噪", True), ("棕噪", False)]
    x = 84
    for name, active in chips:
        cw = 64
        body.append(
            f'<rect x="{x}" y="488" width="{cw}" height="30" rx="15" fill="{PRIMARY if active else "#FFFFFF"}" '
            f'fill-opacity="{0.14 if active else 0.85}" stroke="{PRIMARY if active else LINE}"/>'
        )
        body.append(
            f'<circle cx="{x + 16}" cy="503" r="3" fill="{PRIMARY if active else MUTED}" fill-opacity="{1 if active else 0.5}"/>'
        )
        body.append(
            f'<text x="{x + 26}" y="507" font-size="10.5" fill="{PRIMARY if active else MUTED}">{name}</text>'
        )
        x += cw + 10
    # 主按钮
    body.append(f'<circle cx="{cx}" cy="596" r="44" fill="{PRIMARY}"/>')
    body.append(f'<path d="M {cx - 9} 580 l 30 16 l -30 16 z" fill="#FFFFFF"/>')
    body.append(f'<text x="{cx}" y="672" font-size="11" fill="{MUTED}" text-anchor="middle">选择计划</text>')
    # 底部一句话说明，填补留白
    body.append(f'<text x="{cx}" y="716" font-size="9" fill="{MUTED}" fill-opacity="0.8" text-anchor="middle">计时中画面会缓慢呼吸，防止长时间停留烧屏</text>')
    return phone_frame(w, h, "".join(body))


def screen_day():
    w, h = 360, 760
    body = [
        f'<text x="24" y="62" font-size="22" font-weight="600" fill="{INK}">10 月 30 日</text>',
        f'<text x="24" y="88" font-size="10.5" fill="{PRIMARY}">‹ 返回日历</text>',
        # 计划卡 1
        '<rect x="20" y="104" width="152" height="164" rx="22" fill="url(#cardBlue)"/>',
        '<text x="36" y="136" font-size="11" fill="#FFFFFF" fill-opacity="0.85">🎯 健身</text>',
        '<text x="36" y="204" font-size="30" font-weight="600" fill="#FFFFFF">68%</text>',
        '<text x="36" y="226" font-size="10" fill="#FFFFFF" fill-opacity="0.8">剩余 12 分钟</text>',
        '<circle cx="140" cy="132" r="14" fill="#FFFFFF" fill-opacity="0.22"/>',
        '<path d="M136 126 l 8 6 l -8 6 z" fill="#FFFFFF"/>',
        '<rect x="36" y="242" width="120" height="6" rx="3" fill="#FFFFFF" fill-opacity="0.28"/>',
        '<rect x="36" y="242" width="82" height="6" rx="3" fill="#FFFFFF"/>',
        # 计划卡 2
        '<rect x="188" y="104" width="152" height="164" rx="22" fill="url(#cardViolet)"/>',
        '<text x="204" y="136" font-size="11" fill="#FFFFFF" fill-opacity="0.85">📚 学 Java</text>',
        '<text x="204" y="204" font-size="30" font-weight="600" fill="#FFFFFF">32%</text>',
        '<text x="204" y="226" font-size="10" fill="#FFFFFF" fill-opacity="0.8">剩余 34 分钟</text>',
        '<circle cx="308" cy="132" r="14" fill="#FFFFFF" fill-opacity="0.22"/>',
        '<path d="M312 126 l 0 12 M305 126 l 0 12" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/>',
        '<rect x="204" y="242" width="120" height="6" rx="3" fill="#FFFFFF" fill-opacity="0.28"/>',
        '<rect x="204" y="242" width="38" height="6" rx="3" fill="#FFFFFF"/>',
        # 备注区
        f'<text x="24" y="300" font-size="12" font-weight="500" fill="{MUTED}">📝 当日备注 · 2 条</text>',
        '<rect x="20" y="314" width="320" height="74" rx="18" fill="#FFFFFF" fill-opacity="0.85" stroke="' + LINE + '"/>',
        f'<text x="36" y="338" font-size="12" font-weight="500" fill="{INK}">周复盘</text>',
        f'<text x="262" y="337" font-size="9" fill="{MUTED}">10-30 21:12</text>',
        f'<text x="36" y="356" font-size="9.5" fill="{MUTED}">本周番茄 21 个，比上周多 5 个；下周重点…</text>',
        f'<text x="36" y="372" font-size="9.5" fill="{MUTED}">健身计划坚持了 5 天，周末断了两次。</text>',
        '<rect x="20" y="398" width="320" height="74" rx="18" fill="#FFFFFF" fill-opacity="0.85" stroke="' + LINE + '"/>',
        f'<text x="36" y="422" font-size="12" font-weight="500" fill="{INK}">读书笔记 · 深度工作</text>',
        f'<text x="266" y="421" font-size="9" fill="{MUTED}">10-30 09:20</text>',
        f'<text x="36" y="440" font-size="9.5" fill="{MUTED}">注意力是稀缺资源，深度工作的时长需要…</text>',
        f'<text x="36" y="456" font-size="9.5" fill="{MUTED}">刻意训练，先从每天 90 分钟做起。</text>',
        '<rect x="20" y="482" width="320" height="62" rx="18" fill="#FFFFFF" fill-opacity="0.85" stroke="' + LINE + '"/>',
        f'<text x="36" y="506" font-size="12" font-weight="500" fill="{INK}">随手记</text>',
        f'<text x="292" y="505" font-size="9" fill="{MUTED}">10-29 18:04</text>',
        f'<text x="36" y="524" font-size="9.5" fill="{MUTED}">买弹力带；周末约一次羽毛球。</text>',
        # 展开全部
        f'<text x="180" y="572" font-size="10" fill="{PRIMARY}" text-anchor="middle">展开全部 5 条备注</text>',
        # FAB
        f'<circle cx="300" cy="664" r="26" fill="{PRIMARY}"/>',
        '<path d="M288 664 h 24 M300 652 v 24" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>',
    ]
    return phone_frame(w, h, "".join(body))


def screen_note():
    w, h = 360, 700
    body = [
        # 详情弹窗卡片
        f'<rect x="18" y="54" width="324" height="600" rx="22" fill="#FFFFFF" stroke="{LINE}"/>',
        f'<text x="42" y="92" font-size="16" font-weight="600" fill="{INK}">周复盘 · 第 44 周</text>',
        f'<text x="42" y="112" font-size="9" fill="{MUTED}">10-30 21:12</text>',
        f'<text x="42" y="142" font-size="13" font-weight="600" fill="{INK}">本周收获</text>',
        f'<text x="42" y="164" font-size="10" fill="{INK}">完成 21 个番茄，比上周多 5 个；</text>',
        f'<text x="42" y="182" font-size="10" fill="{INK}">健身计划坚持 5 天，周末被打断。</text>',
        f'<text x="42" y="208" font-size="10" fill="{MUTED}">- 专注时段集中在上午 9-11 点</text>',
        f'<text x="42" y="226" font-size="10" fill="{MUTED}">- 下午效率低，改到户外散步</text>',
        f'<text x="42" y="252" font-size="10" fill="{PRIMARY}">下周计划表 ↗</text>',
        # 图片附件
        '<rect x="42" y="268" width="276" height="128" rx="12" fill="#E9EBF7"/>',
        f'<circle cx="180" cy="326" r="20" fill="{VIOLET}" fill-opacity="0.35"/>',
        f'<path d="M165 335 l 11 -13 l 9 10 l 7 -7 l 11 14 z" fill="#FFFFFF"/>',
        f'<text x="180" y="382" font-size="8.5" fill="{MUTED}" text-anchor="middle">Markdown 在线图片 / 本地图片</text>',
        # 视频附件
        f'<rect x="42" y="408" width="276" height="42" rx="12" fill="#F1F2F9"/>',
        f'<text x="58" y="434" font-size="10" fill="{INK}">视频 · 本周专注记录</text>',
        f'<text x="298" y="434" font-size="9" fill="{PRIMARY}" text-anchor="end">点击播放 ›</text>',
        # 下周重点
        f'<text x="42" y="484" font-size="13" font-weight="600" fill="{INK}">下周重点</text>',
        f'<text x="42" y="506" font-size="10" fill="{INK}">把深度工作放在上午，下午安排运动与复盘；</text>',
        f'<text x="42" y="524" font-size="10" fill="{INK}">健身改为隔天一次，降低中断概率。</text>',
        f'<text x="42" y="552" font-size="8.5" fill="{MUTED}">支持加粗、斜体、列表、引用、链接与图片</text>',
        # 按钮
        f'<text x="60" y="620" font-size="11" fill="#D64545">删除</text>',
        f'<text x="300" y="620" font-size="11" fill="{PRIMARY}" text-anchor="end">关闭</text>',
    ]
    return phone_frame(w, h, "".join(body))


def write(name, parts):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        f.write("".join(parts))
    print("wrote", name)


if __name__ == "__main__":
    write("banner.svg", banner())
    write("screen-calendar.svg", [head(360, 760, defs_common()), screen_calendar(), "</svg>"])
    write("screen-focus.svg", [head(360, 760, defs_common()), screen_focus(), "</svg>"])
    write("screen-day.svg", [head(360, 760, defs_common()), screen_day(), "</svg>"])
    write("screen-note.svg", [head(360, 700, defs_common()), screen_note(), "</svg>"])
