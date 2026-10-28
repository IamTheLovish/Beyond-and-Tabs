#!/usr/bin/env python3
"""Lovish's Wager Book item icon (16x16), drawn here pixel by pixel - our own art, no source image.

A fat, lopsided ledger in patched brown leather, tea-stained page edges showing on the right and at the bottom, and a
brass clasp shaped like a die (a gold square with dark pips). Standard library only (zlib + struct), so it runs in
the prepush sandbox without Pillow. Rerun after editing the grid:

    python3 tools/gen_wager_book_icon.py           (writes bar/src/main/resources/assets/reignofnether/textures/item/wager_book.png)
    python3 tools/gen_wager_book_icon.py --check   (exit 1 if the committed PNG differs)
"""
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/item/wager_book.png")

PALETTE = {
    ".": (0, 0, 0, 0),
    "k": (38, 24, 16, 255),     # outline
    "L": (92, 58, 34, 255),     # leather
    "l": (120, 80, 48, 255),    # leather highlight
    "P": (140, 104, 64, 255),   # patch
    "s": (70, 44, 26, 255),     # stitch / shadow
    "p": (233, 220, 184, 255),  # page edge
    "t": (196, 170, 120, 255),  # tea stain on the edge
    "G": (201, 162, 58, 255),   # brass
    "g": (150, 112, 36, 255),   # brass shadow
    "d": (40, 28, 18, 255),     # die pip
}

# a slight lean: the spine runs down the left, the cover overhangs the pages, the bottom right sags a pixel
GRID = [
    "................",
    "..kkkkkkkkkk....",
    ".kLLlLLLLLLLkk..",
    ".kLlLLLLLLLLLpk.",
    ".kLLLLPPPLLLLpk.",
    ".kLLLLPsPLLLLtk.",
    ".kLLLLPPPLLLGGGk",
    ".kLLLLLLLLLLGdGk",
    ".kLlLLLLLLLLGGgk",
    ".kLLLLLLLLLLLpk.",
    ".kLLLsLLLLLLLtk.",
    ".kLLLLsLLLLLLpk.",
    ".kLlLLLLLLLLLpk.",
    ".kLLLLLLLLLLLLpk",
    "..kpptpppptpppkk",
    "...kkkkkkkkkkk..",
]


def png(grid):
    rows = b""
    for line in grid:
        assert len(line) == 16, line
        rows += b"\x00" + b"".join(bytes(PALETTE[c]) for c in line)

    def chunk(kind, data):
        c = struct.pack(">I", len(data)) + kind + data
        return c + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", 16, len(grid), 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")


def main():
    data = png(GRID)
    if "--check" in sys.argv:
        ok = os.path.exists(OUT) and open(OUT, "rb").read() == data
        print("wager_book.png " + ("matches" if ok else "differs - rerun tools/gen_wager_book_icon.py"))
        return 0 if ok else 1
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(data)
    print("wrote " + os.path.relpath(OUT, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
