#!/usr/bin/env python3
"""The Sunforged Dome Cleric (evoker_unit in the game) on the human body (tools/human_kit.py) - until Oct 21 a
repainted villager (PR #114's tools/hd_villager.py skin, whose outfit is this brief): lovish 21:20, "every human unit
moves off the Minecraft villager body onto the realistic human body".

The priest of the faction's dome: an older man, grey-bearded, under a linen skullcap with a gilt cross over its crown.
The ivory CHASUBLE over a full-length under-robe in the owner's colour - the chasuble's two panels fall from the
shoulders to the shin front and back, the gilt ORPHREY down them (a pillar in front, the Y-cross on the back, the
vestment's oldest mark); its yoke covers the shoulders and the tops of the arms, so the team sleeves show from the
elbow and the team skirt at the sides and under the hem - from the RTS camera an ivory cross-shape over a coloured
column. A cincture of cord at the waist; a heavy BOOK carried against the chest in the left hand, gilt corners and a
sun boss on its cover; the right hand free (it raises the light when he casts).

Rig (EvokerBodyUnitModel / tools/human_base.py "cleric"): the human base's body / head / thighs / shins / feet and
both arms; the under-robe in four panels (robe_front / back / left / right) and the chasuble's skirts below the waist
(chasuble_front / chasuble_back) that sway and part for the legs; the book rides the left forearm.

    python3 tools/gen_cleric.py && python3 tools/game_preview.py design/models/sunforged_cleric_v2.json \\
        --team tabard,tabard_dark --out /tmp/cleric.png
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import human_kit as hk  # noqa: E402
from human_kit import HIP, SHOULDER_X, SHOULDER_Y, HEAD_Y, TRUNK  # noqa: E402
from gen_models import Model, OUT, slab, lathe, split_above, _unit  # noqa: E402
from gen_levy import ngon, oct_pole  # noqa: E402
from gen_medic import cone  # noqa: E402

PALETTE = dict(hk.BASE_PALETTE, **{
    # the chasuble: ivory silk (a "linen" name: painted as cloth), its shade; the gilt orphrey; the team under-robe
    "linen": "#ece4d0", "linen_shade": "#c4b89c", "cloth_dark": "#8a7f6c", "hose": "#5a5048",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "hair": "#857c70", "beard": "#a59d92",               # salt-and-pepper: the older man of the line
    "cord": "#e8dcc0", "leather": "#6b4a30", "page": "#efe6cf",
    "skin": "#c08a6c",
})
EMISSIVE = ["sun"]

ROBE_TOP, ROBE_HEM = HIP + 1.0, 0.85          # the under-robe: from under the cincture to the ankle
ROBE = dict(A=3.15, B=2.45, A_hem=4.3)
CZ = -0.15
HINGE = 17.4                                   # the chasuble's skirts swing from the waist (the cincture's height)
CHAS_HEM = 6.4                                 # mid-shin
CHAS_W = (5.6, 6.2)                            # its panels' width at the waist and at the hem
CHEST_Z = TRUNK[3][3]                          # the padded chest's front (-2.75)


def robe(m):
    """The under-robe: the trunk in team cloth, the skirt one flared cone to the ankles in four swaying panels, a
    darker band at its hem."""
    b = m.bone("body", [0, HIP, 0])
    hk.trunk(b, "tabard")
    for c in b.cubes:                                   # the shoulders' band is under the chasuble's yoke: linen
        if c["from"][1] >= TRUNK[4][0] - 0.01:
            c["color"] = "linen_shade"
    bones, plates, hems = hk.skirt_panels(m, "robe", ROBE_TOP, ROBE_HEM, ROBE["A"], ROBE["B"], ROBE["A_hem"], "tabard",
                                          hem_colour="tabard_dark", hem_h=0.5, cz=CZ, hinge=HIP + 0.8)
    # the cincture: a white cord twice round the waist, knotted on the left, its ends hanging to the knee
    ngon(b, 0, -0.15, 16.95, 17.45, 5.9, "cord", n=8)
    ngon(b, 0, -0.15, 17.5, 17.9, 5.75, "cord", n=8)
    for k, (dx, L) in enumerate(((0.0, 6.2), (0.45, 5.4))):
        x = 2.75 + dx
        b.box_at((x, 17.3 - L / 2, -1.6), (0.32, L, 0.32), "cord", rotation=[0, 0, 4 + 3 * k],
                 pivot=[x, 17.3, -1.6])
        b.box_at((x + 0.2 + 0.05 * k, 17.3 - L - 0.2, -1.6), (0.5, 0.55, 0.5), "cord")          # the tassel's knot
    m.contact_ok += [("body", n) for n in ("left_thigh", "right_thigh", "left_shin", "right_shin")]   # in the robe
    return b, bones


def chasuble(m):
    """The ivory chasuble: a yoke round the shoulders and over the tops of the arms (eight planar plates, gen_medic's
    mantle), and its front and back panels from the yoke to the shin - the upper half on the trunk, the skirts below
    the waist on their own bones (split at the hinge: only the skirt swings). Gilt orphrey: a pillar down the front,
    the Y-cross on the back; a gold edging along every hem."""
    b = m.bones["body"]
    A1, B1, y1 = SHOULDER_X + 0.55, 2.95, SHOULDER_Y - 0.4           # the shoulder line, over the arm's top
    A0, y0 = 2.05, HEAD_Y + 0.45                                     # at the neck
    cone(b, 0, CZ, A1, B1, y1, 1.0, y0, A0 / A1, 12, 0.42, "linen", exact_at=y1)
    yb, grow = SHOULDER_Y - 2.9, 0.1                                 # the yoke's drape over the arms: its foot, flare
    cone(b, 0, CZ, A1, B1, yb + 0.4, 1.0 + grow * 0.86, y1, 1.0, 12, 0.42, "linen", exact_at=yb + 0.4)
    cone(b, 0, CZ, A1, B1, yb, 1.0 + grow, yb + 0.4, 1.0 + grow * 0.86, 12, 0.42, "gold", exact_at=yb)
    # the neck's opening: an amice of linen round it, the gold apparel on it
    ngon(b, 0, CZ, y0 - 0.25, y0 + 0.35, 2 * A0 * 0.98, "gold", n=12, phase=15)
    for name, zsign, rot in (("chasuble_front", -1, [2, 0, 0]), ("chasuble_back", 1, [-2, 0, 0])):
        z_top = CZ + zsign * (B1 - 0.2)                          # where it leaves the yoke (inside its foot)
        z_h = CZ + zsign * 2.95                                  # at the waist: clear of the chest and the cord
        p = m.bone(name, [0, HINGE, z_h], parent="body", rotation=rot)
        w0, w1 = CHAS_W
        # the chest part: from inside the yoke straight down to the hinge (on the trunk after the split)
        slab(p, (0, (yb + 0.2 + HINGE) / 2, (z_top + z_h) / 2), (0, yb + 0.2 - HINGE, z_top - z_h), (1, 0, 0),
             (w0, 0.36, math.hypot(yb + 0.2 - HINGE, z_top - z_h)), "linen")
        # the skirt: widening a little to the hem
        L = HINGE - CHAS_HEM
        # in two leaves turned 8 degrees off the centre line, the orphrey on the crease: the silk wraps him
        for sx in (1, -1):
            p.box_at((sx * w1 / 4, (HINGE + CHAS_HEM) / 2, z_h + zsign * 0.1), (w1 / 2 + 0.06, L, 0.36), "linen",
                     rotation=[0, sx * zsign * 8, 0], pivot=[0, (HINGE + CHAS_HEM) / 2, z_h])
        p.box_at((0, CHAS_HEM + 0.25, z_h), (w1 + 0.08, 0.5, 0.44), "gold")                      # the hem's edging
        # the silk's folds: a ridge catching the light and its shadow beside it, from the hem up to the waist, each
        # a different length (a heavy vestment falls in a few long folds, not a flat board)
        for fx, fh in ((-2.0, 7.4), (1.9, 6.1), (-1.0, 4.2)):
            p.box_at((fx, CHAS_HEM + 0.5 + fh / 2, z_h + zsign * 0.25), (0.5, fh, 0.3), "linen")
            p.box_at((fx + 0.38, CHAS_HEM + 0.55 + fh / 2 - 0.3, z_h + zsign * 0.2), (0.24, fh - 0.6, 0.14),
                     "linen_shade")
        for s in (1, -1):                                                                        # the side edgings
            p.box_at((s * (w1 / 2 - 0.06), (HINGE + CHAS_HEM) / 2, z_h), (0.18, L, 0.42), "gold")
        zo = z_h + zsign * 0.26                                                                   # on its face
        # the orphrey: a gilt pillar from the hem to the yoke (front), the Y-cross on the back
        p.box_at((0, (CHAS_HEM + HINGE) / 2 + 0.1, zo), (1.15, L - 0.2, 0.18), "gold")
        top_y = yb + 0.3
        zc = (z_top + z_h) / 2 + zsign * 0.24
        if zsign < 0:
            slab(p, (0, (top_y + HINGE) / 2, zc), (0, top_y - HINGE, z_top - z_h), (1, 0, 0),
                 (1.15, 0.18, math.hypot(top_y - HINGE, z_top - z_h)), "gold")
            # the sun of the dome on the breast, on the pillar
            sy = HINGE + 2.4
            sz = z_h + (z_top - z_h) * (sy - HINGE) / (yb + 0.2 - HINGE) - 0.42
            p.box_at((0, sy, sz), (1.7, 1.7, 0.2), "gold", rotation=[0, 0, 45], pivot=[0, sy, sz])
            p.box_at((0, sy, sz - 0.1), (0.85, 0.85, 0.2), "sun", rotation=[0, 0, 45], pivot=[0, sy, sz - 0.1])
        else:
            fork = HINGE + 2.2                                   # the Y: the pillar forks under the shoulder blades
            slab(p, (0, (fork + HINGE) / 2, z_h + 0.26 + (fork - HINGE) * 0.0), (0, 1, 0), (1, 0, 0),
                 (1.15, 0.18, fork - HINGE + 0.4), "gold")
            for s in (1, -1):
                a = (0, fork, z_h + 0.26)
                bb = (s * 2.7, top_y, z_top + 0.24)
                slab(p, tuple((a[k] + bb[k]) / 2 for k in range(3)), tuple(bb[k] - a[k] for k in range(3)),
                     (1, 0, 0), (1.0, 0.18, math.dist(a, bb) + 0.4), "gold")
        split_above(m, name, HINGE, "body")


def stole(m):
    """The stole's ends in the owner's colour, hanging below the chasuble's front hem to the ankle - the team colour
    seen from the front and the RTS camera between the ivory and the robe's foot - each ending in a gold fringe."""
    p = m.bones["chasuble_front"]
    z = -3.25 - 0.3
    for s in (1, -1):
        x = s * 1.25
        p.box_at((x, CHAS_HEM - 1.6, z), (1.0, 3.6, 0.22), "tabard")
        p.box_at((x, CHAS_HEM - 3.55, z), (1.1, 0.45, 0.28), "gold")
        p.box_at((x, CHAS_HEM - 2.0, z - 0.14), (0.5, 0.5, 0.1), "gold", rotation=[0, 0, 45],
                 pivot=[x, CHAS_HEM - 2.0, z - 0.14])                                   # a small cross-mark


def head(m):
    """The bare head (grey crop, a full grey beard) under the linen skullcap: a turned calotte over the crown, a gilt
    cross laid over it from brow to nape and ear to ear, a gold button at the crossing."""
    h = hk.bare_head(m, hair="hair", beard="beard", beard_len=1.0, hair_top=True)
    hy = HEAD_Y
    # the calotte sits on the crown over the hair (the grey crop shows round it, over the ears and the brow)
    lathe(h, 0, -0.1, 1.98, 2.28, ((hy + 4.4, 1.0, hy + 5.2, 0.78), (hy + 5.15, 0.78, hy + 5.72, 0.36)), "linen",
          t=0.45)
    oct_pole(h, 0, -0.1, hy + 5.3, hy + 5.78, 1.55, "linen")
    # the cross: two arcs of short plates following the cap's curve, front-back and side to side
    prof = ((hy + 4.45, 2.0), (hy + 5.1, 1.62), (hy + 5.6, 0.9), (hy + 5.82, 0.0))
    for axis in ((0, 0, 1), (1, 0, 0)):
        for sgn in (1, -1):
            for (ya, ra), (yb, rb) in zip(prof, prof[1:]):
                pa = tuple(axis[k] * sgn * ra for k in range(3))
                pb = tuple(axis[k] * sgn * rb for k in range(3))
                k = 1.15 if axis[2] else 1.0                          # the cap is deeper than wide
                a = (pa[0], ya + 0.06, pa[2] * k - 0.1)
                b = (pb[0], yb + 0.06, pb[2] * k - 0.1)
                across = (0, 0, 1) if axis[0] else (1, 0, 0)
                slab(h, tuple((a[k] + b[k]) / 2 for k in range(3)), tuple(b[k] - a[k] for k in range(3)), across,
                     (0.62, 0.2, math.dist(a, b) + 0.12), "gold")
    h.box_at((0, hy + 6.0, -0.15), (0.75, 0.3, 0.75), "gold", rotation=[0, 45, 0], pivot=[0, hy + 5.98, -0.15])
    hk.finish_head(h)
    return h


def book(m, centre):
    """The book of the dome: leather boards over a thick page block, the boards' gilt corners and clasp, a sun boss on
    the front cover. Upright, its front cover facing ahead, on its own bone (it rides the left hand)."""
    x, y, z = centre
    bk = m.bone("book", [x, y - 1.6, z])
    W, H, D = 2.7, 3.3, 1.15                                     # across, tall, thick
    bk.box_at((x, y, z), (W - 0.2, H - 0.25, D - 0.3), "page")                                   # the pages
    for s in (1, -1):
        bk.box_at((x, y, z + s * (D / 2 - 0.1)), (W, H, 0.2), "tabard_dark")                     # the boards
    bk.box_at((x - W / 2 + 0.05, y, z), (0.32, H, D), "leather")                                 # the spine
    for sx in (1, -1):                                                                           # the corners
        for sy in (1, -1):
            for sz in (1, -1):
                bk.box_at((x + sx * (W / 2 - 0.25), y + sy * (H / 2 - 0.25), z + sz * (D / 2 - 0.08)),
                          (0.5, 0.5, 0.18), "gold")
    zf = z - D / 2 - 0.08
    bk.box_at((x, y + 0.2, zf), (1.1, 1.1, 0.18), "gold", rotation=[0, 0, 45], pivot=[x, y + 0.2, zf])
    bk.box_at((x, y + 0.2, zf - 0.08), (0.52, 0.52, 0.14), "sun", rotation=[0, 0, 45], pivot=[x, y + 0.2, zf - 0.08])
    bk.box_at((x + W / 2 + 0.02, y, z), (0.2, 0.62, D + 0.12), "gold")                          # the clasp
    return bk


def cleric():
    m = Model("Dome Cleric (v2)", "sunforged", "caster (T2, on the human body)", PALETTE, EMISSIVE)
    robe(m)
    chasuble(m)
    head(m)
    feet = hk.legs(m)
    ra, rf, rw = hk.cloth_arm(m, -1, "body", "tabard", cuff="gold")
    la, lf, lw = hk.cloth_arm(m, 1, "body", "tabard", cuff="gold")
    # the right hand hangs free, a little forward of the robe
    m.bones[ra].rotation = [4, 0, -4]
    m.bones[rf].rotation = [16, 0, 0]
    hk.free_hand(m, rf, -1, rw)
    # the book against the chest: the left hand under its foot, fingers round the lower edge, the forearm rising to it
    # from an elbow at the ribs (a priest carrying the book in procession)
    bc = (1.15, 17.65, -4.55)
    C = (bc[0], bc[1] - 1.85, bc[2] + 0.1)                      # the lower edge the fingers close round
    e = hk.hold(m, la, lf, lw, "left_fore", C, (1, 0, 0), 0.5, 1, elbow_at=(SHOULDER_X + 0.6, 18.4, -0.9),
                back=_unit((0.5, -0.5, 1.0)), avoid=("body", "chasuble_front"), on_fore=True)
    # the book: built where the hand holds it, then handed to the forearm (it rides the arm)
    bk = book(m, bc)
    hk.attach(m, bk, lf, C)
    m.contact_ok += [("book", "left_fore"), ("chasuble_front", "left_fore")]
    for ft, shin in feet:
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-4, 0, 0]}
    return m, e


MODELS = {"sunforged_cleric_v2": cleric}


def main():
    only = sys.argv[1:]
    for name, build in MODELS.items():
        if only and name not in only:
            continue
        m, err = build()
        path = os.path.normpath(os.path.join(OUT, name + ".json"))
        with open(path, "w") as f:
            json.dump(m.to_json(), f, indent=1)
        nc = sum(len(b.cubes) for b in m.bones.values())
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes, wrist error {err:.2f} px")


if __name__ == "__main__":
    main()
