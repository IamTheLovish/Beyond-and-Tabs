#!/usr/bin/env python3
"""A blind A/B from two Blender render folders (tools/blender/dsl_render.py cells): the two sides get the labels X
and Y in a random order; the mapping goes ONLY to <out>/ab-mapping.txt.

    python tools/render/quarry_saint/ab.py --a NAME=CELLS_DIR:TAG --b NAME=CELLS_DIR:TAG --out OUT

Sheets: ab_body_rts.png (rts close 42, quarter 42, the slam hold at rts 42) and ab_tactical.png (rts 21 at rest and at
the slam hold), each cell from the side's own cells/<tag>_*.png.
"""
import argparse
import os
import random

from PIL import Image, ImageDraw, ImageFont


def font(size):
    for name in ("DejaVuSans.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


F, FB = font(15), font(18)


def side(spec):
    name, rest = spec.split("=", 1)
    cells, tag = rest.rsplit(":", 1)
    return name, cells, tag


def load(cells, tag, key):
    p = os.path.join(cells, "%s_%s.png" % (tag, key))
    return Image.open(p).convert("RGB") if os.path.exists(p) else None


def compose(rows, title, sub, path):
    gap = 10
    W = max(sum(im.width for im, _ in r) + gap * (len(r) + 1) for r in rows)
    H = sum(max(im.height for im, _ in r) + 24 for r in rows) + gap * (len(rows) + 1) + 52
    out = Image.new("RGB", (max(W, 700), H), (245, 243, 239))
    d = ImageDraw.Draw(out)
    d.text((gap, 6), title, fill=(10, 10, 10), font=FB)
    d.text((gap, 30), sub, fill=(60, 60, 60), font=F)
    y = 52 + gap
    for r in rows:
        x = gap
        for im, lab in r:
            out.paste(im, (x, y))
            d.text((x + 4, y + im.height + 3), lab, fill=(20, 20, 20), font=F)
            x += im.width + gap
        y += max(im.height for im, _ in r) + 24 + gap
    out.save(path)
    print(path, out.size)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--a", required=True)
    ap.add_argument("--b", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--slam", default="t016", help="the slam hold's cell tick")
    a = ap.parse_args()
    sides = [side(a.a), side(a.b)]
    random.SystemRandom().shuffle(sides)
    labels = {"X": sides[0], "Y": sides[1]}
    os.makedirs(a.out, exist_ok=True)
    with open(os.path.join(a.out, "ab-mapping.txt"), "w") as f:
        for lab, (name, _c, _t) in labels.items():
            f.write("%s = %s\n" % (lab, name))
    body, tac = [], []
    for lab, (_name, cells, tag) in labels.items():
        r = []
        for key, cap in (("view_rts_42", "rts close 42 px/block, rest"), ("view_quarter_42", "quarter 42, rest"),
                         ("slam_rts_42_%s" % a.slam, "slam hold, rts 42")):
            im = load(cells, tag, key)
            if im is not None:
                r.append((im, "%s - %s" % (lab, cap)))
        body.append(r)
        r = []
        for key, cap in (("view_rts_21", "rts tactical 21, rest"), ("slam_rts_21_%s" % a.slam, "slam hold, rts 21")):
            im = load(cells, tag, key)
            if im is not None:
                r.append((im, "%s - %s" % (lab, cap)))
        tac.append(r)
    compose(body, "Quarry Saint - A/B: X vs Y (Blender Workbench, the repo's bone math)",
            "each side rendered from its own commit; rest and the slam hold", os.path.join(a.out, "ab_body_rts.png"))
    compose(tac, "Quarry Saint - A/B tactical: X vs Y at 21 px per block",
            "each side rendered from its own commit; rest and the slam hold", os.path.join(a.out, "ab_tactical.png"))


if __name__ == "__main__":
    main()
