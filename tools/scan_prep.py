#!/usr/bin/env python3
"""The CC0 photo scans the block pack builds stone, dirt and grass from (lovish Oct 22 20:23: "yes switch them and
keep improving") - prepared once from ambientCG's downloads into tools/scans/, which tools/gen_blocks.py reads.

Source: ambientCG (https://ambientcg.com), every asset CC0 1.0 (public domain dedication, ambientcg.com/license);
downloaded Oct 22 2026 as the 1K-JPG sets (Color, NormalGL, Displacement, AmbientOcclusion, Roughness). The ids are
in SCANS below and in NOTICE.md. Each ambientCG set is a seamless (periodic) tile; this keeps the whole tile and
only resamples it to SIZE with wrapped edges (so it still tiles) - every look decision (scale per block, the
low-frequency flattening, the colour fit, LabPBR) is made in gen_blocks.py, where it can be iterated and reviewed.
The originals are ~1-2 MB per map; the repo keeps these 512 px copies (~0.2 MB each).

    python3 tools/scan_prep.py DIR      # DIR: the unzipped download (DIR/<Id>/<Id>_1K-JPG_<Map>.jpg)
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "tools", "scans")
SIZE = 512                 # 2 x the pack's 256 texels per block: the build resamples from twice its resolution
QUALITY = 92
MAPS = ("Color", "NormalGL", "Displacement")
# id -> the block it becomes (why this one: tools/gen_blocks.py, the SCAN section)
SCANS = {
    "Rock058": "stone",
    "Ground106": "dirt (and the soil of the grass side)",
    "Grass004": "grass top (and the turf of the grass side)",
    "Rock035": "basalt (side and end)",
}


def periodic_resize(im, size):
    """Resample a seamless tile to size x size, its edges wrapped (a 3 x 3 tiling resized, the centre kept)."""
    w, h = im.size
    big = Image.new(im.mode, (3 * w, 3 * h))
    for i in range(3):
        for j in range(3):
            big.paste(im, (i * w, j * h))
    big = big.resize((3 * size, 3 * size), Image.LANCZOS)
    return big.crop((size, size, 2 * size, 2 * size))


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    src = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for asset in SCANS:
        for m in MAPS:
            p = os.path.join(src, asset, "%s_1K-JPG_%s.jpg" % (asset, m))
            im = Image.open(p)
            mode = "L" if m == "Displacement" else "RGB"
            out = periodic_resize(im.convert(mode), SIZE)
            dest = os.path.join(OUT, "%s_%s.jpg" % (asset, m))
            out.save(dest, quality=QUALITY, subsampling=0)      # 4:4:4 - no chroma blur on the colour
            print(dest, os.path.getsize(dest))


if __name__ == "__main__":
    main()
