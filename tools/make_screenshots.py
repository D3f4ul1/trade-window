#!/usr/bin/env python3
"""Crops the real in-game screenshots used in the README into `docs/images/`.

The raw captures are ordinary Minecraft screenshots taken by hand during testing;
they live outside the repository (they are 1366x768 frames of a private session, and
most of each frame is unrelated world). This tool keeps only the part of each frame
that documents the mod, so the gallery can be regenerated from the originals at any
time without committing several megabytes of raw PNG.

Usage:

    python tools/make_screenshots.py <folder-with-raw-screenshots>

The folder must contain the source files named below. Every crop is expressed as a
pixel box in the 1366x768 original, measured from the running client rather than
guessed: the container panel sits at x=510..853, its slots begin at x=519 and are
36px apart (GUI scale 2), and the chat occupies the bottom-left corner.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "images"

# The container panel and its first slot, measured from the captures.
PANEL = (510, 166, 853, 599)  # x0, y0, x1, y1 of the double-chest background
SLOT_WITH_PANEL = (495, 158, 875, 607)  # that panel plus a small margin

# name -> (source screenshot, crop box, optional upscale)
CROPS = {
    # Crafting the token: the recipe grid with the result hovered, so the red
    # "Trade Token" name and the gray lore line are both on screen.
    "screenshot-recipe.png": ("2026-09-24_02.11.26.png", (498, 208, 1132, 562), 1.4),
    # The request arriving: the two chat lines that matter, with the buttons.
    "screenshot-request.png": ("2026-09-24_02.18.38.png", (2, 634, 474, 671), 2.0),
    # A live trade, seen by the player whose offer is the armour: own rows on top,
    # the partner's below, and the partner's LOCK already pressed.
    "screenshot-window.png": ("2026-09-24_02.23.54.png", SLOT_WITH_PANEL, 1.0),
    # The same trade seen by the *other* player: the window is identical, but now
    # the top rows are theirs and the pressed LOCK is on the other side.
    "screenshot-mirrored.png": ("2026-09-24_02.23.42.png", SLOT_WITH_PANEL, 1.0),
    # Both sides locked: the window has closed and the swap has happened.
    "screenshot-complete.png": ("2026-09-24_02.24.02.png", (2, 604, 704, 694), 1.5),
}


def crop(folder: Path, source: str, box, scale: float) -> Image.Image:
    path = folder / source
    if not path.exists():
        raise SystemExit(f"missing source screenshot: {path}")
    image = Image.open(path).convert("RGB").crop(box)
    if scale != 1.0:
        size = (round(image.width * scale), round(image.height * scale))
        # Nearest-neighbour: the captures are pixel art at GUI scale 2, and
        # anything smoother would blur the item icons.
        image = image.resize(size, Image.NEAREST)
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
        print(f"{name:28s} <- {source}  {image.width}x{image.height}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
