#!/usr/bin/env python3
"""The Sunforged Royal Architect (royal_architect_unit, the T2 builder) on the human body (tools/human_kit.py) -
until Oct 21 a repainted villager (PR #114's tools/hd_villager.py skin, whose outfit is this brief).

The master builder of the crown's works: a man in his prime with a trimmed dark beard, in a knee-length coat of oat
linen - fitted, buttoned down the front, its skirts flared and split for walking - a leather belt with a brass buckle,
dark hose and the line's ankle boots. In the owner's colour a short SHOULDER CAPE (the master's) with a gilt hem and a
soft round CAP with a rolled brim, a gilt pair of dividers pinned on its front (the mason-architect's badge). A
leather SATCHEL of plans on his right hip, rolled drawings standing out of it, its strap across his chest; a rolled
PLAN carried in the left hand like a baton; a mallet at the belt that comes to his right hand when he builds.

Rig (RoyalArchitectBodyUnitModel / tools/human_base.py "architect"): the human base; the coat's skirts in four panels
(coat_front / back / left / right) that sway and part for the legs; the plan rides the left forearm; "mallet" (in the
right hand, shown while building, gathering or fighting) and "belt_mallet" (at the belt otherwise).

    python3 tools/gen_architect.py && python3 tools/game_preview.py design/models/sunforged_architect_v2.json \\
        --team tabard,tabard_dark --out /tmp/architect.png
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
from gen_medic import cone, on_plate  # noqa: E402

PALETTE = dict(hk.BASE_PALETTE, **{
    "linen": "#cbb994", "linen_shade": "#a08f6e",        # oat linen: the coat
    "cloth_dark": "#4f4740", "hose": "#4f4740",           # dark hose (the thighs' cuisse in the same wool)
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "hair": "#4a3426", "beard": "#4f3929",
    "leather": "#6b4a30", "page": "#ece2c6", "ink": "#6a5a44",
    "glove": "#b48c5c", "glove_dark": "#8e6a46", "wood": "#7a5536", "steel": "#a3aab6",
    "button": "#5a4030",
})
EMISSIVE = ["sun"]

CZ = -0.15
COAT_TOP, COAT_HEM = HIP + 0.9, 8.9            # the coat's skirts: from under the belt to just over the knee
COAT = dict(A=3.2, B=2.4, A_hem=4.05)


def coat(m):
    b = m.bone("body", [0, HIP, 0])
    hk.trunk(b, "linen")
    for c in b.cubes:                                   # the shoulders' band is under the cape: team cloth
        if c["from"][1] >= TRUNK[4][0] - 0.01:
            c["color"] = "tabard_dark"
    # the front's placket and its buttons, from the collar to the belt; a turned-down collar round the neck
    zf = TRUNK[3][3] - 0.08
    b.box_at((0, 20.2, zf + 0.02), (0.62, 3.6, 0.2), "linen_shade")
    for y in (18.6, 19.6, 20.6, 21.6):
        b.box_at((0, y, zf - 0.1), (0.32, 0.32, 0.16), "button")
    ngon(b, 0, -0.1, TRUNK[4][1] - 0.15, TRUNK[4][1] + 0.55, 3.3, "linen_shade", n=8)          # the collar
    # the skirts: one flared cone in four panels, a shaded hem, a slit up the front (the panels part there)
    bones, plates, _ = hk.skirt_panels(m, "coat", COAT_TOP, COAT_HEM, COAT["A"], COAT["B"], COAT["A_hem"], "linen",
                                       hem_colour="linen_shade", hem_h=0.35, cz=CZ, hinge=HIP + 0.7, t=0.34)
    front = plates[0]
    on_plate(bones["coat_front"], front, 0, -0.4, (0.16, front["L"] - 1.2, 0.08), "linen_shade")   # the slit's edge
    # the belt and its brass buckle
    from gen_paladin import octagon
    octagon(b, BELT[0], BELT[1], 5.95, 4.75, 0.95, "leather", zf=-2.62)
    b.box_at((0, sum(BELT) / 2, -2.72), (1.0, 1.15, 0.26), "gold")
    b.box_at((0, sum(BELT) / 2, -2.86), (0.4, 0.6, 0.1), "leather_dark")
    m.contact_ok += [("body", n) for n in ("left_thigh", "right_thigh")]
    return b, bones


def shoulder_cape(b):
    """The master's short shoulder cape in the owner's colour: eight planar plates from the neck over the shoulders to
    the middle of the upper arm, a gilt hem flush at its foot (gen_medic.mantle's construction)."""
    A1, B1, y1 = SHOULDER_X + 0.5, 2.85, SHOULDER_Y - 0.6
    A0, y0 = 2.0, HEAD_Y + 0.45
    cone(b, 0, CZ, A1, B1, y1, 1.0, y0, A0 / A1, 12, 0.42, "tabard", exact_at=y1)
    yh, yb, grow = 20.9, 20.45, 0.1
    s_h = 1.0 + grow * (y1 - yh) / (y1 - yb)
    cone(b, 0, CZ, A1, B1, yh, s_h, y1, 1.0, 12, 0.42, "tabard", exact_at=yh)
    cone(b, 0, CZ, A1, B1, yb, 1.0 + grow, yh, s_h, 12, 0.42, "gold", exact_at=yb)
    # its clasp at the throat: a gilt disc with the sun
    b.box_at((0, y0 - 0.45, CZ - 2.35), (0.9, 0.9, 0.24), "gold", rotation=[0, 0, 45], pivot=[0, y0 - 0.45, CZ - 2.35])
    b.box_at((0, y0 - 0.45, CZ - 2.5), (0.42, 0.42, 0.12), "sun", rotation=[0, 0, 45], pivot=[0, y0 - 0.45, CZ - 2.5])


def satchel(b):
    """A leather satchel of plans on the right hip, hung on a strap from the left shoulder across the chest; three
    rolled drawings stand out of its mouth, a flap buckled over the rest."""
    c = (-3.6, 14.1, -0.2)
    rot = [0, 4, -6]
    b.box_at(c, (1.3, 3.3, 3.0), "leather", rotation=rot, pivot=list(c))
    b.box_at((c[0] - 0.08, c[1] + 0.95, c[2]), (1.42, 1.3, 3.1), "leather_dark", rotation=rot, pivot=list(c))   # flap
    b.box_at((c[0] - 0.75, c[1] + 0.55, c[2] - 0.2), (0.12, 0.5, 0.5), "gold", rotation=rot, pivot=list(c))     # buckle
    for k, (dz, h, tilt) in enumerate(((-0.8, 3.6, -8), (0.15, 4.4, 4), (0.95, 3.2, 12))):
        p = (c[0] + 0.05, c[1] + 1.4, c[2] + dz)
        top = (p[0] - 0.2, p[1] + h, p[2] + math.tan(math.radians(tilt)) * h)
        hk.axis_pole(b, p, top, 0.62, "page")
        hk.axis_pole(b, (top[0] + 0.0, top[1] - 0.25, top[2]), (top[0], top[1] + 0.05, top[2]), 0.66, "ink")
    # the strap: from the satchel's top up the right side, across the chest to the left shoulder and down the back
    zf = TRUNK[3][3] - 0.12
    pts = ((-3.2, 15.9, -1.6), (-2.6, 17.7, zf + 0.15), (1.3, 21.4, zf + 0.05), (2.1, TRUNK[4][1] + 0.05, -1.0))
    for p, q in zip(pts, pts[1:]):
        d = tuple(q[k] - p[k] for k in range(3))
        slab(b, tuple((p[k] + q[k]) / 2 for k in range(3)), d, _cross(d, (0, 0, -1)),
             (0.5, 0.2, math.dist(p, q) + 0.12), "leather_dark")
    back = ((2.1, TRUNK[4][1] + 0.05, -1.0), (2.1, TRUNK[4][1] - 0.2, 1.75), (-2.7, 15.6, 2.0), (-3.3, 15.0, 1.6))
    for p, q in zip(back, back[1:]):
        d = tuple(q[k] - p[k] for k in range(3))
        nrm = (0, 1, 0) if abs(d[1]) < 0.3 * math.dist(p, q) else (0, 0, 1)
        slab(b, tuple((p[k] + q[k]) / 2 for k in range(3)), d, _cross(d, nrm),
             (0.5, 0.2, math.dist(p, q) + 0.12), "leather_dark")


def mallet(bone, grip, axis):
    """A builder's mallet: an ash handle through the grip along `axis`, a round beech head across its end."""
    a = _unit(axis)
    butt = tuple(grip[k] - a[k] * 1.2 for k in range(3))
    top = tuple(grip[k] + a[k] * 3.2 for k in range(3))
    hk.axis_pole(bone, butt, top, 0.5, "wood")
    across = _unit((-a[1], a[0], 0.0)) if abs(a[2]) < 0.9 else (0.0, 1.0, 0.0)
    h0 = tuple(top[k] - across[k] * 1.25 for k in range(3))
    h1 = tuple(top[k] + across[k] * 1.25 for k in range(3))
    hk.axis_pole(bone, h0, h1, 1.25, "wood_dark")
    for e, s in ((h0, 1), (h1, -1)):                                                   # iron bands at its faces
        hk.axis_pole(bone, tuple(e[k] + across[k] * s * 0.12 for k in range(3)),
                     tuple(e[k] + across[k] * s * 0.36 for k in range(3)), 1.32, "steel_dark")


def head(m):
    """The bare head (dark crop, a trimmed beard) under a soft round cap in the owner's colour: a turned crown, a rolled
    brim in the darker team cloth, the gilt dividers pinned on its front."""
    h = hk.bare_head(m, hair="hair", beard="beard", beard_len=0.55, hair_top=True)
    hy = HEAD_Y
    ngon(h, 0, -0.15, hy + 3.9, hy + 4.55, 4.45, "tabard_dark", n=12, phase=15)                   # the rolled brim
    lathe(h, 0, -0.2, 2.15, 2.4, ((hy + 4.45, 1.0, hy + 5.25, 0.92), (hy + 5.2, 0.92, hy + 5.8, 0.52)), "tabard", t=0.66)
    oct_pole(h, 0, -0.3, hy + 5.5, hy + 6.0, 2.5, "tabard")                                    # the crown's top
    # the master's badge on the brim's front: a gilt sun
    zf = -0.15 - 2.35 - 0.12
    h.box_at((0, hy + 4.3, zf), (0.95, 0.95, 0.2), "gold", rotation=[0, 0, 45], pivot=[0, hy + 4.3, zf])
    h.box_at((0, hy + 4.3, zf - 0.1), (0.45, 0.45, 0.14), "sun", rotation=[0, 0, 45], pivot=[0, hy + 4.3, zf - 0.1])
    hk.finish_head(h)
    return h


def architect():
    m = Model("Royal Architect (v2)", "sunforged", "builder (T2, on the human body)", PALETTE, EMISSIVE)
    b, _ = coat(m)
    shoulder_cape(b)
    satchel(b)
    head(m)
    feet = hk.legs(m)
    ra, rf, rw = hk.cloth_arm(m, -1, "body", "linen", cuff="linen_shade")
    la, lf, lw = hk.cloth_arm(m, 1, "body", "linen", cuff="linen_shade")
    # the right hand hangs free beside the satchel
    m.bones[ra].rotation = [3, 0, -7]
    m.bones[rf].rotation = [14, 0, 0]
    rg = hk.free_hand(m, rf, -1, rw, colour="glove", accent="glove_dark", cuff="leather_dark")
    # the mallet: at the belt on the left hip (belt_mallet), or in the right fist when he works (mallet)
    bm = m.bone("belt_mallet", [2.9, 17.0, 0.6], parent="body")
    bmc = (3.15, 15.6, 0.7)
    mallet(bm, bmc, (0.05, -1.0, 0.25))
    # (in the hand's rest space: the free hand closes loosely round a rod along -z - the handle runs there)
    ml = m.bone("mallet", list(rg["centre"]), parent=rf)
    mallet(ml, rg["centre"], (0.0, 0.0, -1.0))
    # the rolled plan in the left hand, carried like a baton: forward and a little down from the fist
    C = (SHOULDER_X + 0.35, 15.6, -2.9)
    ax = _unit((0.12, -0.5, -1.0))
    e = hk.hold(m, la, lf, lw, "left_fore", C, ax, 0.42, 1, elbow_at=(SHOULDER_X + 0.5, 18.3, -0.6),
                back=_unit((0.0, 0.6, 1.0)), colour="glove", accent="glove_dark", cuff="leather_dark",
                avoid=("body", "coat_front", "coat_left"), on_fore=True)
    pl = m.bone("plan", list(C))
    a0 = tuple(C[k] - ax[k] * 2.0 for k in range(3))
    a1 = tuple(C[k] + ax[k] * 3.6 for k in range(3))
    hk.axis_pole(pl, a0, a1, 0.84, "page")
    for t, col in ((2.4, "tabard"), (-1.2, "tabard")):                                        # its ties
        p = tuple(C[k] + ax[k] * t for k in range(3))
        hk.axis_pole(pl, tuple(p[k] - ax[k] * 0.18 for k in range(3)), tuple(p[k] + ax[k] * 0.18 for k in range(3)),
                     0.92, col)
    hk.axis_pole(pl, tuple(a1[k] - ax[k] * 0.05 for k in range(3)), tuple(a1[k] + ax[k] * 0.06 for k in range(3)), 0.5,
                 "ink")                                                                       # the roll's spiral end
    hk.attach(m, pl, lf, C)
    m.contact_ok += [("plan", "left_fore"), ("mallet", "right_fore"), ("body", "belt_mallet")]
    for ft, shin in feet:
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-3, 0, 0]}
    return m, e


MODELS = {"sunforged_architect_v2": architect}


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
