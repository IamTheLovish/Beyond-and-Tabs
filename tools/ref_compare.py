#!/usr/bin/env python3
"""A building beside the picture it imitates - the check every art round starts and ends with.

    python3 tools/ref_compare.py BUILDING.nbt REFERENCE.png -o compare.png [--px 32] [--blur 6]

Writes one sheet, two rows:
  1. the building rendered by tools/structure_render.py at game zoom (--px 32; 24-36 is the RTS default) next to the
     reference picture scaled (smoothly, never pixel-blown) to the same height;
  2. the SQUINT row: both blurred and greyscaled. Blurred, only the masses and the light/dark pattern survive - the
     hierarchy test. If the reference's squint shows one landmark, a dark gate and calm planes and the building's squint
     shows an even grey block, the building is not there yet, however much detail it has.

Why (design/vehicle_labs/LESSONS.md, Oct 28): seven labs stalled at 3-4/10 for two rounds because they blended many
references, were judged at 18 px (smaller than the game ever shows) and never sat beside their picture. Imitate ONE
picture; check against it at game zoom; check the squint first.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps  # noqa: E402

import structure_render as SR  # noqa: E402
import structure_preview as SP  # noqa: E402

BG = SR.BACKGROUND


def fit_height(img, h):
    w = max(1, round(img.width * h / img.height))
    return img.resize((w, h), Image.LANCZOS)


def squint(img, blur):
    flat = Image.new("RGB", img.size, BG)
    flat.paste(img.convert("RGB"), (0, 0), img.convert("RGBA") if img.mode == "RGBA" else None)
    return ImageOps.grayscale(flat).filter(ImageFilter.GaussianBlur(blur)).convert("RGB")


def main():
    ap = argparse.ArgumentParser(description="A building beside its reference picture, plus the squint test.")
    ap.add_argument("building", help="the .nbt (or a structure name) to render")
    ap.add_argument("reference", help="the reference picture it imitates")
    ap.add_argument("-o", "--out", default="ref_compare.png")
    ap.add_argument("--px", type=int, default=32, help="pixels per block (24-36 = the RTS zoom; default 32)")
    ap.add_argument("--blur", type=float, default=0.0, help="squint blur radius in px (default: height / 60)")
    a = ap.parse_args()
    if not os.path.isdir(SP.PACK):
        sys.exit("ref_compare: no block pack at %s - generate it: python3 tools/gen_blocks.py" % SP.PACK)

    tx = SP.Textures(a.px)
    img, size, total, _ = SR.render(SR.structure_path(a.building), tx, SR.Faces(tx), a.px)
    if img is None:
        sys.exit("ref_compare: the building is only air")
    ref = Image.open(a.reference).convert("RGB")
    ref = fit_height(ref, img.height)
    blur = a.blur or max(3.0, img.height / 60.0)

    gap, cap = max(16, a.px // 2), 28
    font = ImageFont.load_default(size=18)
    row_w = img.width + ref.width + 3 * gap
    sheet = Image.new("RGB", (row_w, 2 * (img.height + cap) + 3 * gap), BG)
    d = ImageDraw.Draw(sheet)
    name = os.path.splitext(os.path.basename(a.building))[0]
    rname = os.path.splitext(os.path.basename(a.reference))[0]
    y = gap
    for label_l, left, label_r, right in (
            ("%s  %s  (--px %d)" % (name, "x".join(map(str, size)), a.px), img, rname, ref),
            ("squint: building", squint(img, blur), "squint: reference", squint(ref, blur))):
        d.text((gap, y), label_l, fill=(230, 230, 230), font=font)
        d.text((2 * gap + img.width, y), label_r, fill=(230, 230, 230), font=font)
        sheet.paste(left.convert("RGB"), (gap, y + cap), left.convert("RGBA") if left.mode == "RGBA" else None)
        sheet.paste(right, (2 * gap + img.width, y + cap))
        y += img.height + cap + gap
    sheet.save(a.out)
    print("wrote", a.out, sheet.size, "- %d blocks" % total)


if __name__ == "__main__":
    main()
