"""Draws the README banner (the GitHub social preview) into ``docs/images/``.

This is the only picture in the repository that is *drawn* rather than captured. Its
one job is to say what the mod is in the time someone spends scrolling past a repo,
so it stays deliberately plain: a title, one sentence, the version range, three plain
-language facts, and the real trading window from a live client on the right, because
a picture of the thing sells it better than a drawing of the thing.

The window itself is ``docs/images/hero.png`` - a real capture of the window the
moment it opens, produced by ``tools/make_screenshots.py`` - enlarged in one nearest
-neighbour step so the slots and the font stay sharp. Nothing in it is drawn.

Every line is measured against the space it has, and the banner refuses to be written
if something would overflow: an earlier version silently ran the title under the
window, which is invisible in code and obvious in the picture.

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


def fits(draw, text, f, limit, what):
    """Text is measured, not eyeballed - if a line would run into the window (or off
    the edge), the banner is wrong and this says so instead of drawing it anyway."""
    width = draw.textlength(text, font=f)
    if width > limit:
        raise SystemExit(
            f"banner: {what} is {width:.0f}px wide but only {limit:.0f}px is free"
        )
    return width


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    img.save(path, optimize=True)
    print(f"  {name}  {img.width}x{img.height}")
    return path


def banner():
    w, h = 1280, 640
    img, d = canvas(w, h)
    d.rectangle([0, 0, w, 7], fill=GREEN)
    d.rectangle([0, h - 7, w, h], fill=RED)

    # The real window from a live client, on the right, as tall as the banner allows.
    shot = screenshot("hero.png")
    left, text_right = 48, w - 40
    if shot is not None:
        margin = 44
        target_h = h - margin * 2
        shot = shot.resize(
            (round(shot.width * target_h / shot.height), target_h), Image.NEAREST
        )
        sx = w - margin - shot.width
        sy = (h - shot.height) // 2
        d.rounded_rectangle(
            [sx - 6, sy - 6, sx + shot.width + 5, sy + shot.height + 5],
            radius=5,
            fill=(6, 7, 10),
            outline=FRAME,
            width=2,
        )
        img.paste(shot, (sx, sy))
        text_right = sx - 46

    cx = left + (text_right - left) / 2
    room = text_right - left

    f_title = font("head", 78)
    fits(d, "TRADE WINDOW", f_title, room, "the title")
    centre(d, (cx, 205), "TRADE WINDOW", f_title, WHITE)

    # A small three-colour rule under the title, tying it to the window's own colours.
    bar_w, bar_y = 320, 268
    for i, colour in enumerate((GREEN, GOLD, RED)):
        d.rounded_rectangle(
            [cx - bar_w / 2 + i * bar_w / 3, bar_y,
             cx - bar_w / 2 + (i + 1) * bar_w / 3 - 8, bar_y + 8],
            radius=4,
            fill=colour,
        )

    f_sub = font("body", 26)
    fits(d, "A safe trade window for your Minecraft server", f_sub, room, "the subtitle")
    centre(d, (cx, 320), "A safe trade window for your Minecraft server", f_sub, MUTED)

    f_ver = font("bold", 28)
    fits(d, "Minecraft 1.21.1  →  26.3", f_ver, room, "the version line")
    centre(d, (cx, 370), "Minecraft 1.21.1  →  26.3", f_ver, GOLD)

    labels = [
        ("Players install nothing", GREEN),
        ("Runs on the server", BLUE),
        ("Easy to configure", GOLD),
    ]
    f_chip = font("bold", 15)
    widths = [d.textlength(t, font=f_chip) + 34 for t, _ in labels]
    fits(d, " ".join(t for t, _ in labels), f_chip, room, "the chips")
    x = cx - (sum(widths) + 12 * (len(labels) - 1)) / 2
    for (text, colour), cw in zip(labels, widths):
        chip(d, x, 418, text, colour, f=f_chip)
        x += cw + 12

    centre(d, (cx, 592), "github.com/D3f4ul1/trade-window", font("mono", 16), DIM)
    return save(img, "banner.png")


def main():
    print("writing docs/images/:")
    banner()


if __name__ == "__main__":
    main()
