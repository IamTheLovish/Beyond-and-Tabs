#!/usr/bin/env python3
"""The Sunforged Field Mason (field_mason_unit, a combat engineer) on the human body (tools/human_kit.py) - until
Oct 21 a repainted villager (PR #114's tools/hd_villager.py skin, whose outfit is this brief).

The sapper who builds under fire: a broad weathered man with a short brown beard, in a quilted GAMBESON in the owner's
colour under a riveted steel BREASTPLATE (a keel down its middle, brass rivets round its edges), small steel caps on
the shoulders, leather bracers; a STEEL CAP with a raised keel from brow to nape and a short brim; a leather TOOL APRON
from the belt to the knee - a trowel, a chisel and a plumb line in its pockets; the PAVISE on his back painted with
the sun; a mason's war HAMMER in his right fist (a square face and a chisel peen on an ash haft).

Rig (FieldMasonBodyUnitModel / tools/human_base.py "mason"): the human base; the apron hangs from the belt on its own
bone ("apron") and swings with the thighs; the hammer rides the right forearm.

    python3 tools/gen_mason.py && python3 tools/game_preview.py design/models/sunforged_mason_v2.json \\
        --team tabard,tabard_dark --out /tmp/mason.png
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import human_kit as hk  # noqa: E402
from human_kit import HIP, SHOULDER_X, SHOULDER_Y, HEAD_Y, TRUNK, BELT  # noqa: E402
from gen_models import Model, OUT, slab, lathe, _unit, _cross  # noqa: E402
from gen_levy import ngon, oct_pole  # noqa: E402
from gen_halberdier import shell, on_shell  # noqa: E402
from gen_paladin import octagon  # noqa: E402
import gen_levy  # noqa: E402

PALETTE = dict(hk.BASE_PALETTE, **{
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "steel": "#a3aab6", "steel_dark": "#7c828d", "brass": "#c49a46",
    "leather": "#6b4a30", "glove": "#9c7650", "glove_dark": "#76573a",
    "cloth_dark": "#5a5046", "hose": "#5f564b",
    "hair": "#5a3e2a", "beard": "#5e4330",
    "wood": "#7a5536", "string": "#e2d6b8",
    "skin": "#bd8564",
})
EMISSIVE = ["sun"]


def gambeson(m):
    b = m.bone("body", [0, HIP, 0])
    hk.trunk(b, "tabard", quilt="tabard_dark")
    # the quilting's channels across the skirt's front and back too (the apron covers the middle of the front)
    for z, s in ((TRUNK[0][3] - 0.04, -1), (TRUNK[0][4] + 0.04, 1)):
        for x in (-2.0, -0.7, 0.7, 2.0):
            b.box_at((x, (TRUNK[0][0] + TRUNK[0][1]) / 2, z), (0.12, TRUNK[0][1] - TRUNK[0][0] - 0.3, 0.12),
                     "tabard_dark")
    # the belt over the gambeson, an iron buckle; a hammer frog on the left hip
    octagon(b, BELT[0], BELT[1], 6.0, 5.3, 1.0, "leather", zf=-3.3)
    b.box_at((0, sum(BELT) / 2, -3.38), (1.1, 1.25, 0.3), "steel_dark")
    m.contact_ok += [("body", n) for n in ("left_thigh", "right_thigh")]
    return b


def breastplate(b):
    """A riveted steel breastplate over the gambeson's chest: an arc of plates round the front from the ribs to under
    the collar (gen_halberdier.shell - its outline exact chords, standing 0.15 off the quilting), the keel down its
    middle catching the light, a turned-out edge at the neck and the arms, brass rivets round it; the straps over the
    shoulders and round the back."""
    cz = -0.15
    y0, y1 = 18.2, 22.7
    R0, R1 = 3.05, 3.05
    shell(b, 0, cz, y0, R0 + 0.15, y1, R1, -78, 78, 6, 0.36, "steel")
    # the keel: a square bar turned 45 down the crease
    zc = cz - R0 - 0.08
    slab(b, (0, (y0 + y1) / 2, zc), (0, 1, 0.03), (1, 0, 1), (0.36, 0.36, y1 - y0 - 0.2), "steel")
    # the plackart's lower lame: a second shell over the waist, the gambeson showing under its edge
    shell(b, 0, cz, 17.0, 3.0, 18.4, 3.12, -70, 70, 5, 0.34, "steel_dark")
    # the edges rolled out: at the neck and down the arm-holes
    shell(b, 0, cz, y1 - 0.25, R1 + 0.1, y1 + 0.2, R1 - 0.1, -55, 55, 4, 0.3, "steel_dark")
    # brass rivets: a row along the top, two down each side, a row at the waist
    for th in (-45, -22, 0, 22, 45):
        on_shell(b, 0, cz, y0, R0 + 0.15, y1, R1, th, 0.0, y1 - 0.65, (0.3, 0.3, 0.14), "brass")
    for th in (-66, 66):
        for y in (19.3, 20.6, 21.9):
            on_shell(b, 0, cz, y0, R0 + 0.15, y1, R1, th, 0.0, y, (0.3, 0.3, 0.14), "brass")
    for th in (-50, -18, 18, 50):
        on_shell(b, 0, cz, 17.0, 3.0, 18.4, 3.12, th, 0.0, 17.7, (0.28, 0.28, 0.12), "brass")
    # the straps: over each shoulder to the back
    for s in (1, -1):
        x = s * 1.85
        p0, p1, p2 = (x, y1 - 0.1, cz - 2.6), (x, TRUNK[4][1] + 0.08, cz), (x, 21.3, cz + 2.0)
        for p, q, nrm in ((p0, p1, (0, 1, -1)), (p1, p2, (0, 1, 1))):
            d = tuple(q[k] - p[k] for k in range(3))
            slab(b, tuple((p[k] + q[k]) / 2 for k in range(3)), d, _cross(d, nrm), (0.55, 0.2, math.dist(p, q) + 0.1),
                 "leather_dark")


def apron(m):
    """The tool apron: a leather panel from the belt to the knee, its own bone hinged at the belt (it swings with the
    thighs); two pockets stitched on it, a trowel's handle, a chisel and a folded plumb line standing in them."""
    hinge = sum(BELT) / 2
    z = -3.05
    p = m.bone("apron", [0, hinge, z], parent="body", rotation=[5, 0, 0])
    bottom = 9.3
    w0, w1 = 4.4, 4.9
    p.box_at((0, (hinge + bottom) / 2, z - 0.2), ((w0 + w1) / 2, hinge - bottom, 0.4), "leather")
    p.box_at((0, bottom + 0.2, z - 0.24), ((w0 + w1) / 2 + 0.06, 0.42, 0.44), "leather_dark")    # its hem, turned
    # the pockets: two leather pouches sewn on, their mouths open
    for s in (1, -1):
        cx = s * 1.15
        p.box_at((cx, 12.4, z - 0.55), (1.9, 2.4, 0.4), "leather_dark")
        p.box_at((cx, 13.55, z - 0.6), (2.0, 0.22, 0.46), "leather")
    # in them: a trowel (its handle and the blade's heel), a chisel's head, a plumb line's bob on its cord
    p.box_at((1.4, 14.25, z - 0.55), (0.4, 1.6, 0.4), "wood")
    p.box_at((1.4, 13.55, z - 0.62), (1.1, 0.2, 0.2), "steel_dark")
    p.box_at((0.75, 14.0, z - 0.55), (0.32, 1.3, 0.32), "steel")
    p.box_at((-1.2, 13.9, z - 0.6), (0.5, 0.5, 0.5), "brass")
    p.box_at((-1.2, 14.55, z - 0.6), (0.12, 0.9, 0.12), "string")
    m.contact_ok.append(("body", "apron"))


def steel_cap(h, hy):
    """The steel cap: a turned skull from the brow, the KEEL standing up along it from the brow to the nape in four
    plates following the curve, a short brim round it, a leather lining's edge under it, a chin strap."""
    ngon(h, 0, -0.15, hy + 3.7, hy + 4.15, 5.3, "steel_dark", n=12, phase=15)                   # the brim
    ngon(h, 0, -0.15, hy + 3.55, hy + 3.75, 4.5, "leather_dark", n=12, phase=15)                # the lining's edge
    lathe(h, 0, -0.15, 2.32, 2.55, ((hy + 4.1, 1.0, hy + 5.0, 0.88), (hy + 4.95, 0.88, hy + 5.65, 0.5)), "steel", t=0.6)
    oct_pole(h, 0, -0.15, hy + 5.4, hy + 5.85, 2.4, "steel")
    # the keel: up the dome from the brow, over the top and down to the nape
    prof = ((-2.55, hy + 4.15), (-2.25, hy + 5.0), (-1.3, hy + 5.65), (0.0, hy + 5.9), (1.3, hy + 5.65),
            (2.25, hy + 5.0), (2.55, hy + 4.15))
    for (za, ya), (zb, yb) in zip(prof, prof[1:]):
        a, b = (0, ya + 0.25, za - 0.15), (0, yb + 0.25, zb - 0.15)
        d = (0, yb - ya, zb - za)
        slab(h, ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2), d, (1, 0, 0),
             (0.3, 0.75, math.dist(a, b) + 0.2), "steel")
    for s in (1, -1):                                                                          # the chin strap
        h.box_at((s * 2.05, hy + 2.0, -0.4), (0.18, 2.4, 0.42), "leather_dark", rotation=[8, 0, 0],
                 pivot=[s * 2.05, hy + 2.0, -0.4])


def head(m):
    h = hk.bare_head(m, hair="hair", beard="beard", beard_len=0.6, hair_top=False)
    steel_cap(h, HEAD_Y)
    hk.finish_head(h)
    return h


def hammer(bone, grip, axis, side_dir):
    """The mason's war hammer: an ash haft along `axis` through the grip, a steel head across its top - a square
    hammer face one way (side_dir), a chisel peen the other - iron langets down the haft, a leather grip."""
    a = _unit(axis)
    butt = tuple(grip[k] - a[k] * 1.6 for k in range(3))
    top = tuple(grip[k] + a[k] * 7.0 for k in range(3))
    hk.axis_pole(bone, butt, top, 0.62, "wood")
    hk.axis_pole(bone, tuple(grip[k] - a[k] * 1.4 for k in range(3)), tuple(grip[k] + a[k] * 1.4 for k in range(3)),
                 0.7, "leather_dark")
    hk.axis_pole(bone, tuple(top[k] - a[k] * 2.3 for k in range(3)), tuple(top[k] - a[k] * 0.3 for k in range(3)), 0.72,
                 "steel_dark")                                                                 # the langets
    sd = _unit(side_dir)
    c = tuple(top[k] - a[k] * 0.6 for k in range(3))
    hk.axis_pole(bone, tuple(c[k] - sd[k] * 0.7 for k in range(3)), tuple(c[k] + sd[k] * 0.7 for k in range(3)), 1.25,
                 "steel")                                                                       # the eye
    hk.axis_pole(bone, c, tuple(c[k] + sd[k] * 2.0 for k in range(3)), 1.05, "steel", n=4)     # the hammer face
    hk.axis_pole(bone, tuple(c[k] + sd[k] * 1.85 for k in range(3)), tuple(c[k] + sd[k] * 2.15 for k in range(3)),
                 1.2, "steel_dark", n=4)
    tip = tuple(c[k] - sd[k] * 2.6 for k in range(3))                                           # the chisel peen
    slab(bone, tuple((c[k] + tip[k]) / 2 for k in range(3)), tuple(tip[k] - c[k] for k in range(3)), a,
         (0.9, 0.5, 2.6), "steel")
    slab(bone, tuple(tip[k] + sd[k] * 0.1 for k in range(3)), sd, a, (1.0, 0.2, 0.3), "steel_dark")
    hk.axis_pole(bone, top, tuple(top[k] + a[k] * 0.4 for k in range(3)), 0.5, "steel_dark")   # the cap


def mason():
    m = Model("Field Mason (v2)", "sunforged", "combat engineer (on the human body)", PALETTE, EMISSIVE)
    b = gambeson(m)
    breastplate(b)
    apron(m)
    gen_levy.pavise(b)
    head(m)
    feet = hk.legs(m)
    ra, rf, rw = hk.cloth_arm(m, -1, "body", "tabard", bracer="leather", cap="steel", cap_hem="steel_dark")
    la, lf, lw = hk.cloth_arm(m, 1, "body", "tabard", bracer="leather", cap="steel", cap_hem="steel_dark")
    # the left hand hangs free; the right carries the hammer at the ready, head up and forward
    m.bones[la].rotation = [2, 0, 6]
    m.bones[lf].rotation = [14, 0, 0]
    hk.free_hand(m, lf, 1, lw, colour="glove", accent="glove_dark", cuff="leather_dark")
    C = (-(SHOULDER_X + 0.75), 14.8, -3.0)
    ax = _unit((-0.1, 0.8, -0.6))
    hm = m.bone("hammer", list(C))
    hammer(hm, C, ax, (-1.0, 0.0, 0.0))
    e = hk.hold(m, ra, rf, rw, "right_fore", C, ax, 0.38, -1, elbow_at=(-(SHOULDER_X + 0.7), 18.4, 0.6),
                back=_unit((0.0, -0.4, 1.0)), colour="glove", accent="glove_dark", cuff="leather_dark",
                avoid=("body", "apron"), on_fore=True)
    hk.attach(m, hm, rf, C)
    m.contact_ok += [("hammer", "right_fore")]
    for ft, shin in feet:
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-3, 0, 0]}
    return m, e


MODELS = {"sunforged_mason_v2": mason}


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
