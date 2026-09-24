"""Draws the README banner (the GitHub social preview) into ``docs/images/``.

This is the only picture in the repository that is *drawn* rather than captured. Its
one job is to say what the mod is in the time someone spends scrolling past a repo,
so it stays deliberately plain: a title, one sentence, the version range, three plain
-language facts, and the real trading window from a live client on the right, because
a picture of the thing sells it better than a drawing of the thing.

The wording is checked against the rest of the documentation - nothing here claims
anything the README or the source does not.

Usage:
    python tools/make_assets.py     (after tools/make_screenshots.py)
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
}
_font_cache = {}


def font(kind, size):
    key = (kind, size)
    if key not in _font_cache:
        _font_cache[key] = ImageFont.truetype(FONTS[kind], size)
    return _font_cache[key]


# -- palette ------------------------------------------------------------------
BG = (14, 16, 21)
GRID = (21, 24, 30)
WHITE = (240, 243, 248)
MUTED = (150, 160, 177)
DIM = (100, 109, 126)
GOLD = (255, 199, 66)
GREEN = (86, 178, 74)
BLUE = (94, 168, 255)
RED = (186, 52, 52)
FRAME = (48, 54, 66)


def canvas(w, h, bg=BG):
    img = Image.new("RGB", (w, h), bg)
    draw = ImageDraw.Draw(img)
    for x in range(0, w, 40):
        draw.line([(x, 0), (x, h)], fill=GRID)
    for y in range(0, h, 40):
        draw.line([(0, y), (w, y)], fill=GRID)
    return img, draw


def centre(draw, xy, s, f, fill):
    draw.text(xy, s, font=f, fill=fill, anchor="mm")


def left(draw, xy, s, f, fill):
    draw.text(xy, s, font=f, fill=fill, anchor="lm")


def chip(draw, x, y, label, fg, h=36, pad=17, f=None):
    """A solid, rounded, coloured pill with white text - the only decoration here."""
    f = f or font("bold", 15)
    w = draw.textlength(label, font=f) + pad * 2
    draw.rounded_rectangle([x, y, x + w, y + h], radius=h // 2, fill=fg)
    centre(draw, (x + w / 2, y + h / 2 + 1), label, f, WHITE)
    return w


def screenshot(name):
    path = os.path.join(OUT, name)
    if not os.path.exists(path):
        return None
    return Image.open(path).convert("RGB")


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    img.save(path, optimize=True)
    print(f"  {name}  {img.width}x{img.height}")
    return path


def banner():
    w, h = 1280, 460
    img, d = canvas(w, h)
    d.rectangle([0, 0, w, 7], fill=GREEN)
    d.rectangle([0, h - 7, w, h], fill=RED)

    # The real window from a live client, on the right, in a thin frame.
    shot = screenshot("window.png")
    text_right = 700
    if shot is not None:
        margin = 30
        target_h = h - margin * 2
        shot = shot.resize(
            (round(shot.width * target_h / shot.height), target_h), Image.NEAREST
        )
        sx = w - margin - shot.width
        sy = (h - shot.height) // 2
        d.rounded_rectangle(
            [sx - 5, sy - 5, sx + shot.width + 4, sy + shot.height + 4],
            radius=4,
            fill=(8, 9, 12),
            outline=FRAME,
            width=2,
        )
        img.paste(shot, (sx, sy))
        text_right = sx - 40

    cx = 46 + (text_right - 46) / 2
    centre(d, (cx, 138), "TRADE WINDOW", font("head", 82), WHITE)
    # A small three-colour rule under the title, tying it to the window's own colours.
    bar_w, bar_y = 300, 190
    for i, colour in enumerate((GREEN, GOLD, RED)):
        d.rounded_rectangle(
            [cx - bar_w / 2 + i * bar_w / 3, bar_y,
             cx - bar_w / 2 + (i + 1) * bar_w / 3 - 8, bar_y + 7],
            radius=3,
            fill=colour,
        )
    centre(d, (cx, 232), "A safe trade window for your Minecraft server",
           font("body", 27), MUTED)
    centre(d, (cx, 280), "Minecraft 1.21.1  →  26.3", font("bold", 29), GOLD)

    labels = [
        ("Players install nothing", GREEN),
        ("Runs on the server", BLUE),
        ("Easy to configure", GOLD),
    ]
    f_chip = font("bold", 15)
    widths = [d.textlength(t, font=f_chip) + 34 for t, _ in labels]
    x = cx - (sum(widths) + 12 * (len(labels) - 1)) / 2
    for (text, colour), cw in zip(labels, widths):
        chip(d, x, 328, text, colour, f=f_chip)
        x += cw + 12

    centre(d, (cx, 414), "github.com/D3f4ul1/trade-window", font("mono", 16), DIM)
    return save(img, "banner.png")


def main():
    print("writing docs/images/:")
    banner()


if __name__ == "__main__":
    main()
