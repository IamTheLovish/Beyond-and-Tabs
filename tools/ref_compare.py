#!/usr/bin/env python3
"""A building beside the picture it imitates - the check every art round starts and ends with.

    python3 tools/ref_compare.py BUILDING.nbt REFERENCE.png -o compare.png [--px 32] [--blur 6] [--flat]

Writes one sheet, two rows:
  1. the building rendered by tools/structure_render.py at game zoom (--px 32; 24-36 is the RTS default), LIT (its
     --light: ambient occlusion and a low sun's shadows - the references are shader screenshots, and beside them a flat
     render read flat whatever the building's depth; --flat for the old unlit render), next to the
     reference picture scaled (smoothly, never pixel-blown) to the same height;
  2. the SQUINT row: both blurred and greyscaled. Blurred, only the masses and the light/dark pattern survive - the
     hierarchy test. If the reference's squint shows one landmark, a dark gate and calm planes and the building's squint
     shows an even grey block, the building is not there yet, however much detail it has.

Why (design/vehicle_labs/LESSONS.md, Oct 28): seven labs stalled at 3-4/10 for two rounds because they blended many
references, were judged at 18 px (smaller than the game ever shows) and never sat beside their picture. Imitate ONE
picture; check against it at game zoom; check the squint first.

--front: compare like with like when the reference is a FRONT-ON photo. The sheet's first two rows then hold an
orthographic elevation of the building's west (-x) face - each (z, y) column's front-most block, every block drawn as a
full cube face, darker the deeper it is set back - beside the reference and its squint, and a third row keeps the RTS
corner view and its squint. Check the front outline column by column first, then the squint, then the corner view.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps  # noqa: E402

import structure_render as SR  # noqa: E402
import structure_preview as SP  # noqa: E402

BG = SR.BACKGROUND
SKY = (165, 190, 215)


def fit_height(img, h):
    w = max(1, round(img.width * h / img.height))
    return img.resize((w, h), Image.LANCZOS)


def squint(img, blur):
    flat = Image.new("RGB", img.size, BG)
    flat.paste(img.convert("RGB"), (0, 0), img.convert("RGBA") if img.mode == "RGBA" else None)
    return ImageOps.grayscale(flat).filter(ImageFilter.GaussianBlur(blur)).convert("RGB")


def front(path, tx, faces, px):
    """The building's west face, orthographic: z runs left to right (north to south), y up. Every block's shape boxes
    (stairs, slabs, walls as built) are painted far to near, so a stair-edged slope reads as the line it is; a box set
    back d blocks behind the front-most block is drawn d steps darker, so recesses and set-backs read."""
    root = SR.PS.load_nbt(path)
    looks = [SR.look_of(e, tx) for e in root.get("palette") or (root.get("palettes") or [[]])[0]]
    sx, sy, sz = root["size"]
    boxes = []
    for b in root.get("blocks", []):
        lk = looks[b["state"]]
        if not SR.PS.invisible(lk.name):
            x, y, z = b["pos"]
            boxes += [(x + bx[0], (x, y, z), bx, lk) for bx in lk.shape]
    img = Image.new("RGB", (sz * px, sy * px), SKY)   # a pale sky, so its squint compares with a daylight photo
    x_min = min((d for d, *_ in boxes), default=0)
    for d, (x, y, z), (x0, x1, y0, y1, z0, z1), lk in sorted(boxes, key=lambda q: -q[0]):
        w, h = round((z1 - z0) * px), round((y1 - y0) * px)
        if w < 1 or h < 1:
            continue
        shade = max(0.35, 0.9 - 0.08 * (d - x_min))
        face = faces.get(lk.side, SR._uv(z0, 1 - y1, z1, 1 - y0), None if lk.light else shade).resize((w, h))
        img.paste(face, (round((z + z0) * px), round((sy - y - y1) * px)), face)
    return img


def main():
    ap = argparse.ArgumentParser(description="A building beside its reference picture, plus the squint test.")
    ap.add_argument("building", help="the .nbt (or a structure name) to render")
    ap.add_argument("reference", help="the reference picture it imitates")
    ap.add_argument("-o", "--out", default="ref_compare.png")
    ap.add_argument("--px", type=int, default=32, help="pixels per block (24-36 = the RTS zoom; default 32)")
    ap.add_argument("--blur", type=float, default=0.0, help="squint blur radius in px (default: height / 60)")
    ap.add_argument("--flat", action="store_true", help="Minecraft's fixed face shading, no AO or sun shadows")
    ap.add_argument("--front", action="store_true",
                    help="the reference is front-on: rows 1-2 are the building's west elevation beside it, row 3 the corner view")
    a = ap.parse_args()
    if not os.path.isdir(SP.PACK):
        sys.exit("ref_compare: no block pack at %s - generate it: python3 tools/gen_blocks.py" % SP.PACK)

    tx = SP.Textures(a.px)
    img, size, total, _ = SR.render(SR.structure_path(a.building), tx, SR.Faces(tx), a.px, light=not a.flat)
    if img is None:
        sys.exit("ref_compare: the building is only air")
    ref_full = Image.open(a.reference).convert("RGB")
    ref = fit_height(ref_full, img.height)
    blur = a.blur or max(3.0, img.height / 60.0)
    rows = [("%s  %s  (--px %d)" % ("{name}", "x".join(map(str, size)), a.px), img, "{rname}", ref)]
    if a.front:
        el = front(SR.structure_path(a.building), tx, SR.Faces(tx), a.px)
        fref = fit_height(ref_full, el.height)
        fblur = a.blur or max(3.0, el.height / 60.0)
        rows = [("{name}  west elevation (front-on)", el, "{rname}", fref),
                ("squint: elevation", squint(el, fblur), "squint: reference", squint(fref, fblur)),
                ("{name}  RTS corner view", img, "squint: corner view", squint(img, blur))]
    else:
        rows.append(("squint: building", squint(img, blur), "squint: reference", squint(ref, blur)))

    gap, cap = max(16, a.px // 2), 28
    font = ImageFont.load_default(size=18)
    name = os.path.splitext(os.path.basename(a.building))[0]
    rname = os.path.splitext(os.path.basename(a.reference))[0]
    lw = max(r[1].width for r in rows)
    row_w = lw + max(r[3].width for r in rows) + 3 * gap
    sheet = Image.new("RGB", (row_w, sum(max(r[1].height, r[3].height) + cap + gap for r in rows) + gap), BG)
    d = ImageDraw.Draw(sheet)
    y = gap
    for label_l, left, label_r, right in rows:
        d.text((gap, y), label_l.format(name=name), fill=(230, 230, 230), font=font)
        d.text((2 * gap + lw, y), label_r.format(rname=rname), fill=(230, 230, 230), font=font)
        sheet.paste(left.convert("RGB"), (gap, y + cap), left.convert("RGBA") if left.mode == "RGBA" else None)
        sheet.paste(right, (2 * gap + lw, y + cap))
        y += max(left.height, right.height) + cap + gap
    sheet.save(a.out)
    print("wrote", a.out, sheet.size, "- %d blocks" % total)


if __name__ == "__main__":
    main()
