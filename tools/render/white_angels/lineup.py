#!/usr/bin/env python3
"""White Angels lane, post step of render.py: the far-zoom and silhouette lineups, composed (PIL only) from the
sheets tools/game_preview.py wrote. For every massing letter (jobs named angel_<letter>_<kind>) one row: Mender,
Archer, Sword, then the control (control_radiant_winged), each cut from the RTS-view row of its blue-owner sheet and
scaled so its body is FAR_PX tall (the far read at the usual RTS zoom), then blown up pixel for pixel:

    far_lineup_<letter>.png          the colour renders on the grass
    silhouette_lineup_<letter>.png   the same as flat black shapes
    far_lineup_all.png / silhouette_lineup_all.png   the rows stacked (a, b, b2, c ... from the top)
    far_lineup_b_vs_b2.png / silhouette_lineup_b_vs_b2.png   a later round (key b2) under the option it grew from (b)

The option letter is kept in the file names only (no captions in the images: the blind critic's copies are
relabelled by hand). Sheet geometry is game_preview.main's: cells SIZE px square, 8 px apart, rows SIZE + 22 apart,
with --art-check the columns colour / value / silhouette / far."""
import argparse
import json
import os
import re

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SIZE = 420
FAR_PX = 36
GAP = 10
BLOW = 4
GRASS = (127, 158, 91)
KINDS = ("mender", "archer", "sword")


def cell(sheet, row, col):
    x, y = 8 + col * (SIZE + 8), 8 + row * (SIZE + 22)
    return sheet.crop((x, y, x + SIZE, y + SIZE))


def cut(path, views):
    """(colour crop, mask crop) of the RTS view's body, both scaled so the body is FAR_PX tall."""
    sheet = Image.open(path).convert("RGB")
    r = views.index("rts")
    colour, sil = cell(sheet, r, 0), cell(sheet, r, 2)
    mask = sil.convert("L").point(lambda v: 255 if v < 120 else 0)
    box = mask.getbbox()
    if box is None:
        raise SystemExit("%s: empty silhouette" % path)
    colour, mask = colour.crop(box), mask.crop(box)
    k = FAR_PX / float(mask.size[1])
    size = (max(1, round(mask.size[0] * k)), FAR_PX)
    small_c = colour.resize(size, Image.BILINEAR)
    small_m = mask.resize(size, Image.BILINEAR).point(lambda v: 255 if v >= 128 else 0)
    return small_c, small_m


def row_images(cuts):
    w = sum(c.size[0] for c, _ in cuts) + GAP * (len(cuts) + 1)
    h = FAR_PX + 2 * GAP
    far = Image.new("RGB", (w, h), GRASS)
    sil = Image.new("RGB", (w, h), (236, 236, 236))
    x = GAP
    for c, m in cuts:
        far.paste(c, (x, GAP), m)
        sil.paste((18, 18, 22), (x, GAP, x + c.size[0], GAP + FAR_PX), m)
        x += c.size[0] + GAP
    return far, sil


def blow(im):
    return im.resize((im.size[0] * BLOW, im.size[1] * BLOW), Image.NEAREST)


def stack(ims, bg):
    w = max(i.size[0] for i in ims)
    out = Image.new("RGB", (w, sum(i.size[1] for i in ims)), bg)
    y = 0
    for i in ims:
        out.paste(i, (0, y))
        y += i.size[1]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    spec = json.load(open(os.path.join(HERE, "jobs.json")))
    jobs = {j["name"]: j for j in spec["jobs"]}

    def get(name):
        j = jobs[name]
        return cut(os.path.join(a.out, name + "_blue.png"), j.get("views", "front,quarter,side,rts").split(","))
    control = get("control_radiant_winged")
    # an option key is a letter with an optional round number (b, b2): jobs named angel_<key>_<kind>
    letters = sorted({m.group(1) for n in jobs for m in [re.match(r"angel_([a-z][0-9]*)_(%s)$" % "|".join(KINDS), n)]
                      if m})
    fars, sils, rows = [], [], {}
    for L in letters:
        cuts = [get("angel_%s_%s" % (L, k)) for k in KINDS if "angel_%s_%s" % (L, k) in jobs] + [control]
        far, sil = row_images(cuts)
        fars.append(far)
        sils.append(sil)
        rows[L] = (far, sil)
        blow(far).save(os.path.join(a.out, "far_lineup_%s.png" % L))
        blow(sil).save(os.path.join(a.out, "silhouette_lineup_%s.png" % L))
        print("lineup %s: %d models" % (L, len(cuts)))
    # a round against the option it grew from (b on top, b2 under): far_lineup_b_vs_b2.png, silhouette_lineup_b_vs_b2.png
    for L in letters:
        m2 = re.match(r"([a-z])([0-9]+)$", L)
        if m2 and m2.group(1) in rows:
            P = m2.group(1)
            blow(stack([rows[P][0], rows[L][0]], GRASS)).save(os.path.join(a.out, "far_lineup_%s_vs_%s.png" % (P, L)))
            blow(stack([rows[P][1], rows[L][1]], (236, 236, 236))).save(
                os.path.join(a.out, "silhouette_lineup_%s_vs_%s.png" % (P, L)))
            print("lineup %s vs %s" % (P, L))
    if fars:
        blow(stack(fars, GRASS)).save(os.path.join(a.out, "far_lineup_all.png"))
        blow(stack(sils, (236, 236, 236))).save(os.path.join(a.out, "silhouette_lineup_all.png"))


if __name__ == "__main__":
    main()
