"""Generates the README's images into ``docs/images/``.

Everything here is drawn from code rather than captured from a running game, so the
output is a *rendering of the documented layout*, not a screenshot. The items and the
container are drawn with Minecraft's own GUI palette (the classic #C6C6C6 window with
#8B8B8B slots) so a reader recognises them at a glance, and every claim the pictures
make is one the rest of the documentation makes in words.

Usage:
    python tools/make_assets.py
"""

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "images")

FONTS = {
    "head": "C:/Windows/Fonts/bahnschrift.ttf",
    "bold": "C:/Windows/Fonts/segoeuib.ttf",
    "body": "C:/Windows/Fonts/segoeui.ttf",
    "mono": "C:/Windows/Fonts/consola.ttf",
    "monob": "C:/Windows/Fonts/consolab.ttf",
}
_font_cache = {}


def font(kind, size):
    key = (kind, size)
    if key not in _font_cache:
        _font_cache[key] = ImageFont.truetype(FONTS[kind], size)
    return _font_cache[key]


# -- palette ------------------------------------------------------------------
BG = (16, 18, 23)
PANEL = (26, 29, 36)
PANEL_2 = (34, 38, 47)
LINE = (52, 58, 70)
WHITE = (240, 243, 248)
MUTED = (146, 156, 173)
DIM = (104, 113, 130)
GOLD = (255, 199, 66)
GREEN = (86, 178, 74)
GREEN_D = (52, 122, 48)
RED = (186, 52, 52)
RED_D = (126, 32, 32)
BLUE = (94, 168, 255)
PURPLE = (176, 128, 255)

MC_GUI = (198, 198, 198)
MC_SLOT = (139, 139, 139)
MC_DARK = (55, 55, 55)
MC_LIGHT = (255, 255, 255)
MC_TITLE = (60, 60, 60)

SLOT = 48          # slot size in the window mockup
GAP = 2


def canvas(w, h, bg=BG):
    img = Image.new("RGB", (w, h), bg)
    return img, ImageDraw.Draw(img)


def grid_bg(draw, w, h, step=32, colour=(24, 27, 33)):
    for x in range(0, w, step):
        draw.line([(x, 0), (x, h)], fill=colour)
    for y in range(0, h, step):
        draw.line([(0, y), (w, y)], fill=colour)


def centre(draw, xy, s, f, fill):
    draw.text(xy, s, font=f, fill=fill, anchor="mm")


def left(draw, xy, s, f, fill):
    draw.text(xy, s, font=f, fill=fill, anchor="lm")


def chip(draw, x, y, label, fg, bg=None, pad=14, h=34, f=None):
    f = f or font("bold", 15)
    w = draw.textlength(label, font=f) + pad * 2
    draw.rounded_rectangle([x, y, x + w, y + h], radius=h // 2,
                           fill=bg or (28, 32, 40), outline=fg, width=2)
    centre(draw, (x + w / 2, y + h / 2 + 1), label, f, fg)
    return w


# -- item art -----------------------------------------------------------------
def slot(draw, x, y, size=SLOT):
    draw.rectangle([x, y, x + size, y + size], fill=MC_SLOT)
    draw.line([(x, y), (x + size, y)], fill=MC_DARK, width=2)
    draw.line([(x, y), (x, y + size)], fill=MC_DARK, width=2)
    draw.line([(x, y + size), (x + size, y + size)], fill=MC_LIGHT, width=2)
    draw.line([(x + size, y), (x + size, y + size)], fill=MC_LIGHT, width=2)


def concrete(draw, x, y, size, fill, dark):
    slot(draw, x, y, size)
    p = int(size * 0.14)
    draw.rectangle([x + p, y + p, x + size - p, y + size - p], fill=fill)
    draw.line([(x + p, y + p), (x + size - p, y + p)], fill=(255, 255, 255, 40), width=2)
    draw.line([(x + p, y + p), (x + p, y + size - p)], fill=(255, 255, 255, 40), width=2)
    draw.line([(x + p, y + size - p), (x + size - p, y + size - p)], fill=dark, width=2)
    draw.line([(x + size - p, y + p), (x + size - p, y + size - p)], fill=dark, width=2)


def pane(draw, x, y, size):
    """A black stained-glass pane: a thin cross of bars, as the item texture is."""
    slot(draw, x, y, size)
    c = size // 2
    t = max(2, size // 16)
    col = (22, 22, 28)
    draw.rectangle([x + c - t, y + 8, x + c + t, y + size - 8], fill=col)
    draw.rectangle([x + 8, y + c - t, x + size - 8, y + c + t], fill=col)
    draw.rectangle([x + 8, y + 8, x + size - 8, y + size - 8], outline=(40, 40, 50), width=1)
    draw.rectangle([x + c - t, y + 8, x + c + t, y + size - 8], fill=col)
    draw.rectangle([x + 8, y + c - t, x + size - 8, y + c + t], fill=col)


def head(draw, x, y, size, skin=(166, 122, 84), hair=(56, 38, 26)):
    slot(draw, x, y, size)
    p = int(size * 0.14)
    box = [x + p, y + p, x + size - p, y + size - p]
    draw.rectangle(box, fill=skin)
    # hair across the top third
    draw.rectangle([box[0], box[1], box[2], box[1] + (box[3] - box[1]) // 3], fill=hair)
    eye_y = box[1] + int((box[3] - box[1]) * 0.52)
    ew = max(2, size // 10)
    draw.rectangle([box[0] + ew * 2, eye_y, box[0] + ew * 3, eye_y + ew], fill=(250, 250, 250))
    draw.rectangle([box[2] - ew * 3, eye_y, box[2] - ew * 2, eye_y + ew], fill=(250, 250, 250))
    draw.rectangle([box[0] + ew * 2 + 1, eye_y + 1, box[0] + ew * 3 - 1, eye_y + ew], fill=(58, 46, 120))
    draw.rectangle([box[2] - ew * 3 + 1, eye_y + 1, box[2] - ew * 2 - 1, eye_y + ew], fill=(58, 46, 120))
    mouth_y = box[1] + int((box[3] - box[1]) * 0.78)
    draw.rectangle([box[0] + ew * 2, mouth_y, box[2] - ew * 2, mouth_y + max(1, ew // 2)],
                   fill=(120, 78, 62))


def ghast_tear(draw, x, y, size):
    """A ghast tear: a fat teardrop, round above and pointed below."""
    slot(draw, x, y, size)
    s = size
    cx = x + s / 2
    body = (232, 242, 255)
    shade = (168, 196, 234)
    edge = (116, 152, 202)
    rx = s * 0.27
    top = y + s * 0.17
    bottom = y + s * 0.63          # the ellipse's lower edge
    centre_y = (top + bottom) / 2  # where its widest points are
    tip = y + s * 0.86
    # Fill the body as one silhouette: the point first, the round head over it, so
    # no seam shows where they meet.
    draw.polygon([(cx - rx, centre_y), (cx, tip), (cx + rx, centre_y)], fill=body)
    draw.ellipse([cx - rx, top, cx + rx, bottom], fill=body)
    # Then one continuous outline: the head's top arc plus the two sides to the tip.
    draw.arc([cx - rx, top, cx + rx, bottom], 180, 360, fill=edge, width=2)
    draw.line([(cx - rx, centre_y), (cx, tip)], fill=edge, width=2)
    draw.line([(cx + rx, centre_y), (cx, tip)], fill=edge, width=2)
    # A highlight so it reads as glass rather than paper.
    draw.ellipse([cx - s * 0.18, top + s * 0.07, cx - s * 0.03, top + s * 0.21],
                 fill=(255, 255, 255))
    draw.arc([cx - rx, top, cx + rx, bottom], 205, 250, fill=shade, width=2)


def gold_ingot(draw, x, y, size):
    slot(draw, x, y, size)
    s = size
    body = (250, 214, 96)
    dark = (196, 148, 40)
    draw.polygon([(x + s * 0.24, y + s * 0.62), (x + s * 0.38, y + s * 0.36),
                  (x + s * 0.76, y + s * 0.36), (x + s * 0.62, y + s * 0.62)], fill=body)
    draw.polygon([(x + s * 0.24, y + s * 0.62), (x + s * 0.38, y + s * 0.36),
                  (x + s * 0.76, y + s * 0.36), (x + s * 0.62, y + s * 0.62)], outline=dark)
    draw.line([(x + s * 0.38, y + s * 0.44), (x + s * 0.72, y + s * 0.44)], fill=(255, 240, 178), width=2)


def paper(draw, x, y, size):
    slot(draw, x, y, size)
    p = int(size * 0.20)
    draw.rectangle([x + p, y + p - 4, x + size - p, y + size - p + 4], fill=(244, 244, 240))
    for i in range(4):
        yy = y + p + 2 + i * 6
        draw.line([(x + p + 3, yy), (x + size - p - 3, yy)], fill=(178, 178, 178), width=1)


def arrow(draw, p1, p2, colour=MUTED, width=3, head_len=12):
    draw.line([p1, p2], fill=colour, width=width)
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    length = max(1e-6, (dx * dx + dy * dy) ** 0.5)
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    a = (p2[0] - ux * head_len + px * head_len * 0.6,
         p2[1] - uy * head_len + py * head_len * 0.6)
    b = (p2[0] - ux * head_len - px * head_len * 0.6,
         p2[1] - uy * head_len - py * head_len * 0.6)
    draw.polygon([p2, a, b], fill=colour)


def callout(draw, x, y, w, h, title, lines, accent=BLUE, title_f=None, body_f=None):
    title_f = title_f or font("bold", 17)
    body_f = body_f or font("body", 15)
    draw.rounded_rectangle([x, y, x + w, y + h], radius=10, fill=PANEL_2, outline=accent, width=2)
    draw.rounded_rectangle([x, y, x + 5, y + h], radius=3, fill=accent)
    left(draw, (x + 18, y + 21), title, title_f, WHITE)
    yy = y + 46
    for line in lines:
        left(draw, (x + 18, yy), line, body_f, MUTED)
        yy += 23
    return h


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    img.save(path, optimize=True)
    print(f"  {name}  {img.width}x{img.height}")
    return path


# -- 1. banner -----------------------------------------------------------------
def banner():
    w, h = 1280, 460
    img, d = canvas(w, h)
    grid_bg(d, w, h, 40, (22, 25, 31))
    # a soft band behind the title
    d.rectangle([0, 0, w, 8], fill=GREEN)
    d.rectangle([0, h - 8, w, h], fill=RED)

    # decorative items, top right
    bx, by, s = w - 330, 46, 54
    head(d, bx, by, s)
    head(d, bx + s + 10, by, s, skin=(196, 158, 120), hair=(120, 84, 44))
    pane(d, bx + (s + 10) * 2, by, s)
    concrete(d, bx + (s + 10) * 3, by, s, GREEN, GREEN_D)

    centre(d, (w / 2, 132), "TRADE WINDOW", font("head", 88), WHITE)
    centre(d, (w / 2, 200), "Server-side player trading for Minecraft",
           font("body", 26), MUTED)
    centre(d, (w / 2, 236), "1.21.1  →  26.3", font("bold", 30), GOLD)

    labels = [("No client mod needed", GREEN), ("Registers nothing", BLUE),
              ("Vanilla double chest", GOLD), ("One datapack recipe", PURPLE)]
    widths = [d.textlength(t, font=font("bold", 15)) + 28 for t, _ in labels]
    x = (w - (sum(widths) + 12 * (len(labels) - 1))) / 2
    for (text, colour), cw in zip(labels, widths):
        chip(d, x, 300, text, colour)
        x += cw + 12

    centre(d, (w / 2, 396), "point of sale: 14 targets · one design · nothing registered",
           font("mono", 15), DIM)
    return save(img, "banner.png")


# -- 2. the trade token recipe -------------------------------------------------
def recipe():
    w, h = 1280, 620
    img, d = canvas(w, h)
    grid_bg(d, w, h, 40, (22, 25, 31))
    left(d, (48, 44), "1 · CRAFT THE TOKEN", font("head", 34), WHITE)
    left(d, (48, 80), "ghast tear + gold ingot  →  a Ghast Tear named \"Trade Token\"",
         font("body", 17), MUTED)

    # crafting grid panel
    px, py, s = 70, 140, 62
    d.rounded_rectangle([px - 22, py - 22, px + s * 3 + 40, py + s * 3 + 22],
                        radius=12, fill=PANEL, outline=LINE, width=2)
    for r in range(3):
        for c in range(3):
            slot(d, px + c * s, py + r * s, s)
    ghast_tear(d, px, py, s)
    gold_ingot(d, px + s, py, s)

    arrow(d, (px + s * 3 + 68, py + s * 1.5), (px + s * 3 + 150, py + s * 1.5), GOLD, 4, 16)

    # result
    rx = px + s * 3 + 176
    slot(d, rx, py + s, s + 10)
    ghast_tear(d, rx, py + s, s + 10)
    left(d, (rx + 4, py + s * 2 + 44), "Trade Token", font("bold", 22), (255, 85, 85))
    left(d, (rx + 4, py + s * 2 + 72), "x1   stacks to 16", font("body", 16), MUTED)

    # tooltip
    tx, ty = rx + 250, py + s - 10
    d.rounded_rectangle([tx, ty, tx + 330, ty + 78], radius=8, fill=(12, 10, 26, 255),
                        outline=(90, 60, 160), width=2)
    left(d, (tx + 14, ty + 22), "Trade Token", font("bold", 18), (255, 85, 85))
    left(d, (tx + 14, ty + 50), "Right-click a player to trade", font("body", 15), (170, 170, 170))

    # the components
    callout(d, 70, 400, 560, 168, "What the recipe actually sets",
            ["minecraft:custom_name      red  \"Trade Token\"",
             "minecraft:lore             gray \"Right-click a player to trade\"",
             "minecraft:max_stack_size   16"], GOLD, body_f=font("mono", 15))
    callout(d, 660, 400, 550, 168, "No custom item is registered",
            ["the result is a real minecraft:ghast_tear,",
             "so vanilla clients are never kicked and",
             "the server's registries stay identical to vanilla's."], GREEN)
    return save(img, "recipe.png")


# -- 3. the window -------------------------------------------------------------
def window():
    s, gap = SLOT, GAP
    rows, cols = 6, 9
    pad = 8
    grid_w = cols * s + (cols - 1) * gap
    win_w = grid_w + pad * 2
    win_h = rows * s + (rows - 1) * gap + pad * 2
    inv_gap = 24

    w = 1280
    h = 96 + 28 + win_h + inv_gap + 3 * s + 2 * gap + 14 + s + 80
    img, d = canvas(w, h)
    grid_bg(d, w, h, 40, (22, 25, 31))

    left(d, (32, 40), "3 · THE TRADE WINDOW", font("head", 32), WHITE)
    left(d, (32, 74), "a vanilla double chest — every player sees their own offer in the middle",
         font("body", 16), MUTED)

    ox = (w - win_w) / 2
    oy = 112

    # inventory panel behind everything
    d.rounded_rectangle([ox - 6, oy - 6, ox + win_w + 6, oy + win_h + 6],
                        radius=6, fill=MC_GUI)
    d.rectangle([ox - 6, oy - 6, ox + win_w + 6, oy], fill=(160, 160, 160))

    def slot_at(col, row):
        return (ox + pad + col * (s + gap), oy + pad + row * (s + gap))

    for r in range(rows):
        for c in range(cols):
            slot(d, *slot_at(c, r), s)

    # row 0: your header
    pane(d, *slot_at(0, 0), s)
    pane(d, *slot_at(1, 0), s)
    pane(d, *slot_at(2, 0), s)
    concrete(d, *slot_at(3, 0), s, GREEN, GREEN_D)
    concrete(d, *slot_at(5, 0), s, RED, RED_D)
    head(d, *slot_at(4, 0), s)
    for c in (6, 7, 8):
        pane(d, *slot_at(c, 0), s)

    # row 3: their header (mirrored)
    pane(d, *slot_at(0, 3), s)
    pane(d, *slot_at(1, 3), s)
    pane(d, *slot_at(2, 3), s)
    concrete(d, *slot_at(3, 3), s, GREEN, GREEN_D)
    concrete(d, *slot_at(5, 3), s, RED, RED_D)
    head(d, *slot_at(4, 3), s, skin=(196, 158, 120), hair=(120, 84, 44))
    for c in (6, 7, 8):
        pane(d, *slot_at(c, 3), s)

    # fences: your offer rows and their offer rows
    for r in (1, 2):
        d.rectangle([ox + pad - 4, oy + pad + r * (s + gap) - 4,
                     ox + pad + grid_w + 4, oy + pad + r * (s + gap) + s + 4],
                    outline=GREEN, width=3)
    for r in (4, 5):
        d.rectangle([ox + pad - 4, oy + pad + r * (s + gap) - 4,
                     ox + pad + grid_w + 4, oy + pad + r * (s + gap) + s + 4],
                    outline=GOLD, width=3)

    # player inventory
    iy = oy + win_h + inv_gap
    d.rounded_rectangle([ox - 6, iy - 6, ox + win_w + 6, iy + 4 * s + 3 * gap + 6],
                        radius=6, fill=MC_GUI)
    for r in range(4):
        base = iy + r * (s + gap) + (6 if r == 3 else 0)
        for c in range(cols):
            slot(d, ox + pad + c * (s + gap), base, s)

    # annotations on the left
    def ann(y, title, lines, accent):
        callout(d, 24, y, 300, 40 + 23 * len(lines), title, lines, accent)

    hdr_y = oy + pad + s / 2
    arrow(d, (330, hdr_y + 18), (ox + pad - 10, hdr_y), GREEN, 3, 12)
    callout(d, 24, 130, 296, 112, "Your header row",
            ["green block = LOCK your side", "red block = CANCEL the trade",
             "middle = your head"], GREEN)

    your_y = oy + pad + 1.5 * (s + gap)
    arrow(d, (330, your_y + 30), (ox + pad - 10, your_y), GREEN, 3, 12)
    callout(d, 24, your_y - 34, 296, 64, "Your offer (rows 2–3)", ["put your items here"], GREEN)

    their_y = oy + pad + 4.5 * (s + gap)
    arrow(d, (330, their_y + 30), (ox + pad - 10, their_y), GOLD, 3, 12)
    callout(d, 24, their_y - 34, 296, 64, "Their offer (rows 5–6)", ["only they can change it"], GOLD)

    other_hdr_y = oy + pad + 3 * (s + gap) + s / 2
    arrow(d, (ox + win_w + 12, other_hdr_y + 18), (980, other_hdr_y), PURPLE, 3, 12)
    callout(d, 978, oy + 118, 288, 112, "Their header row",
            ["the same blocks, for them", "your window mirrors theirs",
             "so both see themselves on top"], PURPLE)

    left(d, (ox + 4, iy + 4 * s + 3 * gap + 22),
         "your inventory and hotbar — unchanged", font("body", 15), (80, 80, 80))

    centre(d, (w / 2, h - 32),
           "both players share one container; each sees their own offer in the middle rows",
           font("body", 16), MUTED)
    return save(img, "window.png")


# -- 4. lifecycle --------------------------------------------------------------
def flow():
    w, h = 1280, 620
    img, d = canvas(w, h)
    grid_bg(d, w, h, 40, (22, 25, 31))
    left(d, (40, 40), "4 · HOW A TRADE GOES", font("head", 32), WHITE)
    left(d, (40, 74), "six steps, and the three ways out of them", font("body", 16), MUTED)

    bw, bh = 230, 92

    def box(x, y, n, title, body, accent):
        d.rounded_rectangle([x, y, x + bw, y + bh], radius=12, fill=PANEL, outline=accent, width=2)
        d.ellipse([x + 14, y + 14, x + 44, y + 44], fill=accent)
        centre(d, (x + 29, y + 29), n, font("bold", 17), (16, 18, 23))
        left(d, (x + 56, y + 28), title, font("bold", 18), WHITE)
        yy = y + 56
        for line in body:
            left(d, (x + 18, yy), line, font("body", 14), MUTED)
            yy += 20

    y1 = 130
    box(40, y1, "1", "Hold the token", ["a red Ghast Tear,", "crafted or in hand"], GOLD)
    arrow(d, (270, y1 + bh / 2), (312, y1 + bh / 2), DIM, 3, 12)
    box(312, y1, "2", "Right-click a player", ["/trade <player> works", "from any distance"], BLUE)
    arrow(d, (542, y1 + bh / 2), (584, y1 + bh / 2), DIM, 3, 12)
    box(584, y1, "3", "They accept", ["one chat line with", "[ Accept ] [ Decline ]"], GREEN)

    y2 = 268
    arrow(d, (699, y1 + bh), (699, y2 - 12), DIM, 3, 12)
    box(584, y2, "4", "Window opens", ["a vanilla double chest,", "titled Trading Window"], GREEN)
    arrow(d, (584, y2 + bh / 2), (542, y2 + bh / 2), DIM, 3, 12)
    box(312, y2, "5", "Both click LOCK", ["green concrete; the", "window closes on swap"], GREEN)
    arrow(d, (312, y2 + bh / 2), (270, y2 + bh / 2), DIM, 3, 12)
    box(40, y2, "6", "Items swap", ["atomically — all or", "nothing, never partial"], GOLD)

    # the three exits
    left(d, (40, 410), "THREE WAYS A TRADE ENDS EARLY", font("bold", 16), MUTED)
    outs = [("CANCEL", "red concrete, or /trade cancel", RED),
            ("TIMEOUT", "15 s to answer, 2 min in the window", GOLD),
            ("DISCONNECT", "close, death, or leaving range", PURPLE)]
    x, y = 40, 440
    for title, body, accent in outs:
        d.rounded_rectangle([x, y, x + 380, y + 74], radius=10, fill=PANEL_2,
                            outline=accent, width=2)
        left(d, (x + 18, y + 26), title, font("bold", 17), accent)
        left(d, (x + 18, y + 52), body, font("body", 15), MUTED)
        x += 400

    callout(d, 40, 530, 1200, 62,
            "Items always return to their owner on every one of those paths.",
            ["Nothing is dropped on the ground, and nothing is lost to a crash: anything stranded by a restart is returned on next login."],
            GREEN)
    return save(img, "flow.png")


# -- 5. commands ---------------------------------------------------------------
def commands():
    w, h = 1280, 660
    img, d = canvas(w, h)
    grid_bg(d, w, h, 40, (22, 25, 31))
    left(d, (40, 40), "5 · COMMANDS", font("head", 32), WHITE)
    # two lines of the two callouts are continued below them
    left(d, (40, 74), "three for players, four for operators — the window does the rest",
         font("body", 16), MUTED)

    def section(x, y, title, accent, rows):
        left(d, (x, y), title, font("bold", 19), accent)
        yy = y + 34
        for cmd, desc in rows:
            d.rounded_rectangle([x, yy - 16, x + 560, yy + 18], radius=8,
                                fill=PANEL if rows.index((cmd, desc)) % 2 == 0 else PANEL_2)
            left(d, (x + 14, yy), cmd, font("monob", 15), WHITE)
            left(d, (x + 250, yy), desc, font("body", 15), MUTED)
            yy += 42
        return yy

    section(40, 130, "PLAYER", GREEN, [
        ("/trade <player>", "send a trade request"),
        ("/trade accept", "accept an incoming request"),
        ("/trade decline", "decline an incoming request"),
    ])
    section(680, 130, "OPERATOR", GOLD, [
        ("/tradeadmin crossdistance on|off", "trade from any distance, or 20 blocks"),
        ("/tradeadmin list", "active trades: players, elapsed, locks"),
        ("/tradeadmin spectate <player>", "watch a trade read-only"),
        ("/tradeadmin history [player]", "last 20 completed trades"),
    ])

    callout(d, 40, 320, 560, 210, "There is no /trade lock — that is deliberate",
            ["locking and cancelling are clicks on the blocks",
             "inside the window, beside the items:",
             "",
             "green concrete  →  LOCK your side",
             "red concrete    →  CANCEL the trade",
             "",
             "There is no unlock: cancel and trade again."], GREEN)
    callout(d, 640, 320, 600, 210, "Chat carries only the decisions that need one",
            ["the request arrives as a single line:",
             "",
             "[Trade] Alice wants to trade.",
             "[ Accept ]  [ Decline ]",
             "",
             "the window itself shows the state."], BLUE)

    left(d, (24, 552), "Player commands need a Trade Token in your inventory unless requireToken is off.",
         font("body", 15), DIM)
    left(d, (24, 578), "/tradeadmin needs permission level 2 (op).  There is no /trade confirm, /trade unlock or /trade history.",
         font("body", 15), DIM)
    left(d, (24, 610), "No chat line is sent when an item is placed or removed, or when a side locks — the window is the status display.",
         font("body", 15), MUTED)
    return save(img, "commands.png")


# -- 6. architecture -----------------------------------------------------------
def architecture():
    w, h = 1280, 600
    img, d = canvas(w, h)
    grid_bg(d, w, h, 40, (22, 25, 31))
    left(d, (40, 40), "6 · SERVER-SIDE ONLY", font("head", 32), WHITE)
    left(d, (40, 74), "the mod runs where the world runs; connecting clients need nothing",
         font("body", 16), MUTED)

    # server box
    d.rounded_rectangle([70, 140, 520, 400], radius=14, fill=PANEL, outline=GREEN, width=3)
    left(d, (94, 168), "YOUR SERVER / SINGLE-PLAYER", font("bold", 18), GREEN)
    left(d, (94, 200), "Trade Window + Fabric API", font("mono", 15), WHITE)
    for i, line in enumerate([
        "one main entrypoint, no client entrypoint",
        "/trade and /tradeadmin commands",
        "the trade session, locks and the atomic swap",
        "the token recipe (a data pack file)",
        "the container menu, synced by the game",
    ]):
        left(d, (110, 232 + i * 26), "· " + line, font("body", 15), MUTED)

    # clients
    for i, (title, sub, y) in enumerate([
        ("VANILLA CLIENT", "no mods at all", 140),
        ("VANILLA CLIENT", "no mods at all", 288),
    ]):
        x = 800
        d.rounded_rectangle([x, y, x + 410, y + 112], radius=14, fill=PANEL_2,
                            outline=BLUE, width=3)
        left(d, (x + 24, y + 32), title, font("bold", 18), BLUE)
        left(d, (x + 24, y + 62), sub, font("body", 15), MUTED)
        left(d, (x + 24, y + 88), "renders a plain chest, clicks plain slots",
             font("body", 14), DIM)

    # the channel between them: two ordinary vanilla directions
    arrow(d, (540, 200), (786, 200), BLUE, 3, 14)
    arrow(d, (786, 340), (540, 340), GREEN, 3, 14)
    centre(d, (663, 178), "menu sync (vanilla)", font("body", 14), MUTED)
    centre(d, (663, 364), "slot clicks (vanilla)", font("body", 14), MUTED)
    d.line([(663, 214), (663, 326)], fill=(58, 64, 76), width=2)
    centre(d, (663, 270), "no custom packets", font("body", 13), DIM)
    centre(d, (663, 292), "no client mod", font("body", 13), DIM)

    callout(d, 70, 424, 1150, 68, "Why a vanilla client can trade",
            ["Nothing is added to a registry, so there is no \"unknown registry entry\" kick, and no client code is needed to draw or click anything."],
            GOLD)
    callout(d, 70, 508, 1150, 68, "Single-player works too",
            ["Fabric treats single-player as an integrated server, so the same jar goes in your client's mods folder — see the environment note in the README."],
            PURPLE)
    return save(img, "architecture.png")


# -- 7. configuration ----------------------------------------------------------
def configuration():
    w, h = 1280, 560
    img, d = canvas(w, h)
    grid_bg(d, w, h, 40, (22, 25, 31))
    left(d, (40, 40), "7 · CONFIGURATION", font("head", 32), WHITE)
    left(d, (40, 74), "config/tradewindow.json — written with live-server defaults on first run",
         font("body", 16), MUTED)

    rows = [
        ("tradeTimeoutSeconds", "15", "seconds to accept a request"),
        ("guiTimeoutSeconds", "120", "seconds the window may stay open"),
        ("maxDistanceBlocks", "20", "used only when crossdistance is off"),
        ("cancelOnMove", "false", "end the trade if someone walks off"),
        ("cancelOnDamage", "true", "end it if someone is hit"),
        ("requireToken", "true", "must hold a Trade Token to trade"),
        ("allowCreativeTrading", "false", "creative players may trade"),
        ("allowCrossDimensionTrading", "false", "trade across dimensions"),
        ("maxTradeValue", "-1", "cap on trade value, -1 = no cap"),
        ("blacklistedItems", "[...]", "bedrock, command block, ..."),
        ("logTrades", "true", "append to tradewindow-history.jsonl"),
    ]
    d.rounded_rectangle([60, 130, 760, 130 + len(rows) * 33 + 20], radius=12,
                        fill=PANEL, outline=LINE, width=2)
    y = 152
    for key, value, desc in rows:
        left(d, (82, y), f'"{key}"', font("mono", 14), BLUE)
        left(d, (352, y), value, font("monob", 14), GOLD)
        left(d, (470, y), desc, font("body", 14), MUTED)
        y += 33

    callout(d, 800, 130, 420, 116, "Defaults are a fresh install's",
            ["the file is only written when it does not exist,",
             "so delete it (or edit the lines) to re-read the",
             "new defaults after an update."], GOLD)
    callout(d, 800, 258, 420, 140, "Distance is two switches",
            ["crossDistanceTrading on  → no distance rule",
             "crossDistanceTrading off → the 20-block rule",
             "applies, and is checked before a token is spent."], GREEN)
    callout(d, 800, 410, 420, 116, "Nothing is hidden in code",
            ["every value here has a matching entry in the",
             "README's table, and the packaging gate fails if",
             "the shipped defaults drift from the docs."], PURPLE)

    left(d, (60, 130 + len(rows) * 33 + 50),
         "Change it with /tradeadmin crossdistance, or by editing the file and restarting.",
         font("body", 15), DIM)
    return save(img, "configuration.png")


def main():
    print("writing docs/images/:")
    banner()
    recipe()
    window()
    flow()
    commands()
    architecture()
    configuration()


if __name__ == "__main__":
    main()
