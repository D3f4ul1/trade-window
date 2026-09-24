#!/usr/bin/env python3
"""Crops the real in-game screenshots used by the README into ``docs/images/``.

The raw captures are ordinary 1366x768 Minecraft screenshots taken by hand during
testing. They live outside the repository - most of each frame is unrelated world -
so this tool keeps only the part of each frame that documents the mod, which also
means the gallery can be rebuilt from the originals at any time.

Usage:

    python tools/make_screenshots.py <folder-with-raw-screenshots>

Every crop is a pixel box measured from the running client rather than guessed: at
GUI scale 2 the double-chest panel sits at x=512..851, its first slot is 32px wide
starting at x=522, the slot pitch is 36px, and the slot rows begin at y=200. Crop
boxes are upscaled with NEAREST because the captures are pixel art - any smoother
resampling blurs the item icons and the font.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "images"

# The 9x6 container alone: the title bar plus six slot rows, with a small margin.
# It stops at the last slot row, so the player's own "Inventory" label - which sits
# directly underneath and belongs to the game, not to this mod - stays out of frame.
CONTAINER = (504, 158, 860, 416)

# The whole window - title bar, the six rows above, then the player's own Inventory
# underneath - caught the moment it opened: both header rows in place, both offers
# still empty. This is the one picture that shows the finished layout at a glance,
# which is why it is what the banner is built around. The panel's own edges, read
# off the frame: black outline at x=506 and x=853, y=162 and y=602.
WHOLE_WINDOW = (505, 161, 855, 604)

# name -> (source screenshot, crop box, scale). A box of None uses the whole file.
CROPS = {
    # The token's recipe in a real crafting table: "Crafting", ghast tear and gold
    # ingot in the grid, and the result hovered so the red name and gray lore show.
    # This source is already the hand-cropped capture, so it is taken as it stands.
    "recipe.png": ("2026-09-24_02.11.26.png", None, 2.0),
    # A live trade, seen by the player whose offer is the armour: their own rows
    # above, their partner's below, and the partner's LOCK already pressed.
    "window.png": ("2026-09-24_02.23.54.png", CONTAINER, 2.0),
    # The same trade, same instant, on the *other* player's client. Identical
    # window, but the armour has moved to the bottom offer rows and the pressed
    # LOCK to the top header - which is the mirroring, caught on camera twice.
    "window-mirrored.png": ("2026-09-24_02.23.42.png", CONTAINER, 2.0),
    # The request arriving in chat. The message wraps onto two lines, so the crop
    # starts on the "[Trade]" line and ends before the unrelated debug line below.
    "request.png": ("2026-09-24_02.18.38.png", (0, 632, 352, 672), 2.0),
    # The banner's hero, left at native size: the banner scales it in one step so the
    # pixel edges stay sharp.
    "hero.png": ("2026-09-24_02.20.41.png", WHOLE_WINDOW, 1.0),
}


def crop(folder: Path, source: str, box, scale: float) -> Image.Image:
    path = folder / source
    if not path.exists():
        raise SystemExit(f"missing source screenshot: {path}")
    image = Image.open(path).convert("RGB")
    if box:
        image = image.crop(box)
    if scale != 1.0:
        image = image.resize(
            (round(image.width * scale), round(image.height * scale)), Image.NEAREST
        )
    return image


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    folder = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (source, box, scale) in CROPS.items():
        image = crop(folder, source, box, scale)
        image.save(OUT / name)
        print(f"{name:22s} <- {source}  {image.width}x{image.height}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
