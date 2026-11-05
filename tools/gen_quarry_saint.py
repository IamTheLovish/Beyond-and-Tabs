#!/usr/bin/env python3
"""The Quarry Saint: the Sunforged T2 siege statue (replaces the Siege Ox's Ravager body). Design sheet: Animation
Studio unit 2 (Nov 5). A foundation statue of the White Angel's temple, cracked open by sunlight, walking off its
plinth with the sanctuary's maul: a low, wide, hunched block of carved stone (yoke + two round pauldron discs), a sunk
eyeless head under the shoulder line, chunks of its plinth on its feet, a rough quarry-cut back, light in its cracks,
and a 46 px maul whose light head is the brightest thing on it.

    python3 tools/gen_quarry_saint.py      # writes design/models/sunforged_quarry_saint.json
    python3 tools/render_concept.py --check design/models/sunforged_quarry_saint.json
    python3 tools/dsl_to_game.py design/models/sunforged_quarry_saint.json --class QuarrySaintModel \\
        --texture quarry_saint_unit --team tabard,tabard_dark
    python3 tools/quarry_saint_poses.py > poses.json   # the key poses (tools/quarry_saint_poses.py)

Space is the DSL's (design/models/README.md): px, y up from the soles, the unit facing -Z, +x its LEFT. Bone names
follow the sheet (pelvis, torso, head, thigh_r/shin_r, shoulder_r/forearm_r/hand_r, maul, ...), plus three the
sheet did not have (see the round log): foot_r/foot_l (an ankle, so a lowered pelvis keeps the plinths flat on
y = 0), pauldron_r/pauldron_l (the round shoulder discs on their own bone: an arm raised overhead does not whirl its
disc with it) and the light-crack damage sets cracks_hp66 / cracks_hp33 (two bones of emissive cubes only, so the
game shows each set with ModelPart.visible below 66% / 33% hp).

Palette: the sheet's stone values under the angel statue's names (marble_light / marble / marble_shade /
marble_deep): tools/materials.py paints any colour named "marble*" with the angel's carved-stone surface (soft
cloud, thin veins, worn arrises), so the two statues are visibly the same quarry; the hexes are the sheet's.

The rest pose (the bind) is the sheet's REST stance, baked: the right hand on the maul by the pommel with the head on
the ground ahead-right (solved: limb_solve + the maul frame), the left arm hanging, the plinths on y = 0.
The limb solver here (limb_solve) is gen_models.Model.solve_arm's cost in numpy over the same grid then a finer
one - the same answer, fast enough to re-solve all four limbs for every key pose (tools/quarry_saint_poses.py).
"""
import copy
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT, ngon_prism, octagon_axle, slab, rot_mat, mat_mul, transpose, euler  # noqa: E402
from gen_models import apply, rotate_about, _unit, _cross, _dot  # noqa: E402

NAME = "sunforged_quarry_saint"
PALETTE = {
    "marble_light": "#f4f0e6",   # stone_light: the maul head, the face (value group 1: the read)
    "marble": "#e9e4d8",         # stone: the body blocks
    "marble_shade": "#cdc4b4",   # stone_shade: bevels, pectoral, haft, caps, tassets
    "marble_deep": "#a99f8e",    # stone_deep: cuts, plinth chunks, quarry back, shards
    "gold": "#d4a63c",           # belt, disc rims, maul bands, ferrule, pommel ONLY
    "gold_dark": "#a87f24",      # (reserved by the sheet: band undersides; unused in round 1)
    "tabard": "#2f5fb3",         # the team stole (mask)
    "tabard_dark": "#244a8c",    # stole edges, fringe (mask, darker)
    "sun": "#ffd96a",            # the light in the cracks and the maul's disc (emissive)
}
EMISSIVE = ["sun"]
UPGRADE = "gilded_seams"

# the maul's grip in the right fist (bind), the hand and elbow pivots, the shoulder pivot (right side; x -> -x left)
GRIP_R = (-17.25, 12.5, 2.0)
HAND_PIV = (-17.25, 16.0, 2.0)
ELBOW_PIV = (-17.25, 27.0, 2.0)
SHOULDER_PIV = (-15.0, 38.0, 2.0)
HIP_PIV = (-6.5, 19.0, 2.0)
KNEE_PIV = (-6.5, 11.0, 2.0)
ANKLE_PIV = (-6.5, 4.5, 1.0)
LEFT_ON_HAFT = 11.0           # the left fist sits this far up the haft from GRIP_R, toward the head
HAFT_HEAD_Z = -32.0           # the head's centre along the haft (bind z)
TORSO_REST = -14.0            # the hunch
HEAD_REST = 10.0


def mx(x, s):
    """A right-side x for side s (-1 right, +1 left)."""
    return x if s < 0 else -x


def fx(x0, w, s):
    """A right-side cube's from-x for side s."""
    return x0 if s < 0 else -(x0 + w)


def sn(s):
    return "r" if s < 0 else "l"


# ------------------------------------------------------------------ small shape helpers ------------------------------
def zoct(bone, c, length, w, color, **kw):
    """A regular octagon prism along z (a disc facing forward): octagon_axle's four boxes about z."""
    side = w * math.tan(math.pi / 8)
    bone.box_at(list(c), [w, side, length], color, **kw)
    bone.box_at(list(c), [side, w, length], color, **kw)
    for a in (45, -45):
        bone.box_at(list(c), [w, side, length], color, rotation=[0, 0, a], pivot=list(c), **kw)


def oct_band(bone, cx, cz, y0, y1, w, t, color, n=8, **kw):
    """A band of n slabs round the flats of an n-gon prism of width w (a rim round ngon_prism's disc)."""
    side = w * math.tan(math.pi / n) + 0.3
    r = w / 2 + t / 2
    yc = (y0 + y1) / 2
    for k in range(n):
        a = math.radians(360.0 / n * k)
        c = (cx + r * math.sin(a), yc, cz + r * math.cos(a))
        slab(bone, c, (math.cos(a), 0.0, -math.sin(a)), (math.sin(a), 0.0, math.cos(a)), (t, y1 - y0, side), color,
             **kw)


def flat_seg(bone, a, b, y, w, h, color, **kw):
    """A strip lying on a horizontal face from (x, z) a to b: width w across, height h, its top at y."""
    p0, p1 = (a[0], y - h / 2, a[1]), (b[0], y - h / 2, b[1])
    c = tuple((p0[i] + p1[i]) / 2 for i in range(3))
    along = tuple(p1[i] - p0[i] for i in range(3))
    across = (-along[2], 0.0, along[0])
    return slab(bone, c, along, across, (w, h, math.dist(p0, p1)), color, **kw)


def face_seg(bone, a, b, plane, w, t, color, **kw):
    """A strip on a vertical face. plane = ("z", z0) for a face square to z (a, b are (x, y)), ("x", x0) for a face
    square to x (a, b are (z, y)). Width w in the face, thickness t centred on the plane."""
    ax, v = plane
    if ax == "z":
        p0, p1 = (a[0], a[1], v), (b[0], b[1], v)
        normal = (0.0, 0.0, 1.0)
    else:
        p0, p1 = (v, a[1], a[0]), (v, b[1], b[0])
        normal = (1.0, 0.0, 0.0)
    c = tuple((p0[i] + p1[i]) / 2 for i in range(3))
    along = tuple(p1[i] - p0[i] for i in range(3))
    return slab(bone, c, along, normal, (t, w, math.dist(p0, p1)), color, **kw)


def face_staple(bone, a, b, t_at, plane, size, color, **kw):
    """A gold staple across a crack strip on a vertical face at fraction t_at (size = (length across, width, proud))."""
    ax, v = plane
    pa = (a[0] + (b[0] - a[0]) * t_at, a[1] + (b[1] - a[1]) * t_at)
    d = (b[0] - a[0], b[1] - a[1])
    perp = (-d[1], d[0])
    if ax == "z":
        c, along, normal = (pa[0], pa[1], v - size[2] / 2 + 0.2), (perp[0], perp[1], 0.0), (0.0, 0.0, 1.0)
    else:
        c, along, normal = (v - size[2] / 2 + 0.2, pa[1], pa[0]), (0.0, perp[1], perp[0]), (1.0, 0.0, 0.0)
    return slab(bone, c, along, normal, (size[2], size[1], size[0]), color, **kw)


# ------------------------------------------------------------------ the body -------------------------------------------
# yoke-top light cracks (x, z) on y 45, kept out of the pauldron discs (r ~7 round (+-17, 2)) and the stole (|x| < 5)
CRACKS_YOKE = [((13.8, 12.4), (9.6, 8.6)), ((9.6, 8.6), (5.4, 5.6)), ((-5.4, 0.6), (-9.4, -3.2)),
               ((9.4, 8.4), (10.6, 3.4)), ((-7.4, -1.3), (-10.6, 2.4)), ((6.2, 9.0), (7.6, 12.9))]
STAPLES_YOKE = [(0, 0.2), (0, 0.65), (1, 0.45), (2, 0.35), (2, 0.8), (3, 0.5), (4, 0.5), (5, 0.5)]
# down the pectoral from the yoke crack's front end ((x, y) on the face z = -8; the last on the chest face z = -7)
# (outside the stole's right tail, x -9..-5.6)
CRACKS_CHEST = [((-9.6, 36.2), (-10.6, 32.6), -8.0), ((-10.6, 32.6), (-9.7, 29.3), -8.0),
                ((-9.8, 29.0), (-11.4, 25.4), -7.0)]


def pelvis(m):
    p = m.bone("pelvis", [0, 22, 2])
    p.cube([-10, 17, -5], [20, 9, 14], "marble")                                   # hips
    p.cube([-10.5, 24, -5.5], [21, 3, 15], "gold")                                 # belt
    p.cube([-2, 24.2, -6.4], [4, 2.6, 1], "marble_deep")                           # the belt's stone boss
    for i in range(4):                                                             # tassets, front (flared)
        x0 = -10 + 5.1 * i
        p.cube([x0, 13, -6.3], [4.8, 9, 1.4], "marble_shade", rotation=[10, 0, 0], pivot=[x0 + 2.4, 22, -5.6])
    for i in range(3):                                                             # back
        x0 = -7.5 + 5.1 * i
        p.cube([x0, 13, 8.9], [4.8, 9, 1.4], "marble_shade", rotation=[-10, 0, 0], pivot=[x0 + 2.4, 22, 9.6])
    for s in (-1, 1):                                                              # sides
        p.cube([fx(-12.0, 1.4, s), 13, -1], [1.4, 9, 6], "marble_shade", rotation=[0, 0, 8 * s],
               pivot=[mx(-11.3, s), 22, 2])
    # the belt tabard (team): flared with the tassets it hangs over, its lower half its own bone
    t0 = m.bone("tabard_front_0", [0, 24, -6.6], parent="pelvis", rotation=[10, 0, 0])
    t0.cube([-5, 14, -7], [10, 10, 1], "tabard")
    t1 = m.bone("tabard_front_1", [0, 14, -7], parent="tabard_front_0")
    t1.cube([-5, 7, -7.2], [10, 7, 1], "tabard")
    for i in range(4):
        t1.cube([-4.4 + 2.6 * i, 5.3, -7.0], [1.2, 1.8, 0.26], "tabard_dark")       # fringe (lod by size)
    return p


def leg(m, s):
    k = sn(s)
    th = m.bone("thigh_" + k, [mx(HIP_PIV[0], s), HIP_PIV[1], HIP_PIV[2]], parent="pelvis")
    th.cube([fx(-11, 9, s), 10, -3], [9, 10, 10], "marble")
    octagon_axle(th, (mx(-11.3, s), 14.0, 2.0), 0.6, 6.0, "marble_shade")         # the hip disc on the outer face
    sh = m.bone("shin_" + k, [mx(KNEE_PIV[0], s), KNEE_PIV[1], KNEE_PIV[2]], parent="thigh_" + k)
    sh.cube([fx(-11, 9, s), 3, -2.5], [9, 9, 9], "marble")
    zoct(sh, (mx(-6.5, s), 11.0, -3.0), 1.2, 7.0, "marble_shade")                  # the knee cop
    ft = m.bone("foot_" + k, [mx(ANKLE_PIV[0], s), ANKLE_PIV[1], ANKLE_PIV[2]], parent="shin_" + k)
    ft.cube([fx(-12, 11, s), 3, -7.5], [11, 3.5, 13], "marble_shade")              # the foot block
    ft.cube([fx(-13.6, 13, s), 0, -8], [13, 3, 15], "marble_deep")                 # its plinth chunk
    ft.cube([fx(-15.6, 3, s), 0, -9.3], [3, 2, 3], "marble_deep")                  # broken bits off its outer corners
    ft.cube([fx(-15.2, 2.5, s), 0, 4.6], [2.5, 1.6, 2.5], "marble_deep")


def torso(m):
    t = m.bone("torso", [0, 25, 2], parent="pelvis", rotation=[TORSO_REST, 0, 0])
    t.cube([-13, 25, -7], [26, 13, 17], "marble")                                  # chest
    t.cube([-15, 36, -4], [30, 9, 17], "marble")                                   # yoke (top at y 45)
    t.cube([-14, 37, -5.6], [28, 6, 1.6], "marble_shade")                          # its front bevel
    t.cube([-11, 29, -8], [22, 7, 1.5], "marble_shade")                            # pectoral
    for i in range(3):
        t.cube([-11, 25.4 + 2.4 * i, -7.6], [22, 1.4, 0.8], "marble_deep")         # carved ribs
    t.cube([-12, 28, 10], [24, 15, 3], "marble_deep")                              # the rough quarry back
    for (x, y, w) in ((-9, 41, 5), (-2, 39.5, 6), (4, 41.5, 4), (-7, 35, 7), (1, 33, 5), (5, 30.5, 6)):
        t.cube([x, y, 13.0], [w, 0.35, 0.35], "marble_shade")                      # chisel steps (lod by size)
    for a, b in CRACKS_YOKE:                                                       # the light-cracks on the yoke
        flat_seg(t, a, b, 45.1, 1.5, 0.5, "sun")
    for a, b, z in CRACKS_CHEST:                                                   # ... and down the chest
        face_seg(t, a, b, ("z", z - 0.05), 1.2, 0.5, "sun")
    # the team stole over the hump, edged darker: a band down the back of the hump, parting round the sunk head into
    # two front tails (a priest's stole; one central front drop ran through the head and beard)
    t.cube([-4.5, 45, -1.5], [9, 1, 15.5], "tabard")
    for x in (-5.0, 4.5):
        t.cube([x, 45, -1.5], [0.5, 1.1, 15.5], "tabard_dark")
    for x in (-9.0, 5.6):
        t.cube([x, 45, -6.0], [3.4, 1, 3.0], "tabard")                            # over the yoke to the front tails
        t.cube([-9.0 if x < 0 else 4.5, 45, -3.2], [4.5, 1, 2.0], "tabard")         # ... joined to the band
    # upgrade gilded_seams: gold staples across the yoke cracks and the chest cracks
    for i, f in STAPLES_YOKE:
        (ax, az), (bx, bz) = CRACKS_YOKE[i]
        c = (ax + (bx - ax) * f, 45.5, az + (bz - az) * f)
        slab(t, c, (-(bz - az), 0.0, bx - ax), (bx - ax, 0.0, bz - az), (2, 1, 4), "gold", upgrade=UPGRADE)
    for (a, b, z) in CRACKS_CHEST[:2]:
        face_staple(t, a, b, 0.5, ("z", z), (4, 2, 1), "gold", upgrade=UPGRADE)
    # the damage-state cracks: their own bones (emissive cubes only) - the game shows each set below 66% / 33% hp
    h66 = m.bone("cracks_hp66", [0, 25, 2], parent="torso")
    flat_seg(h66, (9.8, 0.2), (12.0, -3.6), 45.1, 1.5, 0.5, "sun")
    face_seg(h66, (9.4, 35.8), (10.4, 31.8), ("z", -8.05), 1.2, 0.5, "sun")
    face_seg(h66, (-2.0, 35.0), (1.0, 31.0), ("x", 13.05), 1.2, 0.5, "sun")
    h33 = m.bone("cracks_hp33", [0, 25, 2], parent="torso")
    flat_seg(h33, (-5.8, 9.6), (-8.6, 12.9), 45.1, 1.5, 0.5, "sun")
    face_seg(h33, (-10.5, 42.5), (-12.0, 38.2), ("z", -5.65), 1.2, 0.5, "sun")
    face_seg(h33, (0.0, 36.0), (3.0, 31.5), ("x", -13.05), 1.2, 0.5, "sun")
    # the stole's front and back drops (front hung plumb against the hunch: +14 cancels the torso's -14)
    for s, k in ((1, "l"), (-1, "r")):
        x0 = 5.6 if s > 0 else -9.0
        sf0 = m.bone("stole_front_%s_0" % k, [s * 7.3, 44, -6], parent="torso", rotation=[14, 0, 0])
        sf0.cube([x0, 34, -7], [3.4, 11.8, 1], "tabard")
        sf1 = m.bone("stole_front_%s_1" % k, [s * 7.3, 34, -7], parent="stole_front_%s_0" % k)
        sf1.cube([x0, 27, -7.2], [3.4, 7, 1], "tabard")
        sf1.cube([x0 + 1.1, 25.3, -7.0], [1.2, 1.8, 0.26], "tabard_dark")
    sb0 = m.bone("stole_back_0", [0, 44, 13.6], parent="torso")
    sb0.cube([-4, 32, 13.1], [8, 13.8, 1], "tabard")
    sb1 = m.bone("stole_back_1", [0, 32, 13.6], parent="stole_back_0")
    sb1.cube([-4, 24, 13.3], [8, 8, 1], "tabard")
    for i in range(3):
        sb1.cube([-3.3 + 2.6 * i, 22.3, 13.4], [1.2, 1.8, 0.26], "tabard_dark")
    # loose shards on the yoke (hop on impacts)
    for name, frm, size in (("shard_0", [-13.0, 45, 8.5], [3, 2, 3]), ("shard_1", [-7.5, 45, 4.5], [2.5, 1.5, 2.5]),
                            ("shard_2", [1.5, 45, -3.9], [2, 2, 2])):
        b = m.bone(name, [frm[0] + size[0] / 2, 45, frm[2] + size[2] / 2], parent="torso")
        b.cube(frm, size, "marble_deep")
    return t


def head(m):
    h = m.bone("head", [0, 37, -6], parent="torso", rotation=[HEAD_REST, 0, 0])
    h.cube([-5, 33, -15], [10, 10, 10], "marble")                                  # skull
    h.cube([-5.5, 40, -15.8], [11, 2.5, 4], "marble_shade")                        # brow
    h.cube([-4, 34, -15.6], [8, 6, 1], "marble_light")                             # the smooth eyeless face
    for x in (-3.4, 0.9):
        h.cube([x, 38.0, -15.75], [2.5, 0.3, 0.3], "marble_deep")                 # eye cuts (lod)
    h.cube([-4.5, 31, -14.5], [9, 3, 7], "marble_shade")                           # the square carved beard
    for i in range(3):
        h.cube([-2.65 + 2.5 * i, 31.2, -14.65], [0.3, 2.5, 0.3], "marble_deep")    # its cuts (lod)


def arm(m, s):
    k = sn(s)
    piv = [mx(SHOULDER_PIV[0], s), SHOULDER_PIV[1], SHOULDER_PIV[2]]
    pd = m.bone("pauldron_" + k, piv, parent="torso")
    ngon_prism(pd, mx(-17, s), 2.0, 41.0, 45.5, 13.0, "marble", n=8)               # the round disc (from above)
    oct_band(pd, mx(-17, s), 2.0, 44.5, 45.5, 13.0, 0.5, "gold")                   # its gold rim
    pd.cube([fx(-19, 4, s), 45.5, 0], [4, 1, 4], "marble_shade")                   # boss
    up = m.bone("shoulder_" + k, piv, parent="torso")
    up.cube([fx(-21.5, 8.5, s), 26, -2.5], [8.5, 15.5, 9], "marble")               # upper arm (tucked under the disc)
    fo = m.bone("forearm_" + k, [mx(ELBOW_PIV[0], s), ELBOW_PIV[1], ELBOW_PIV[2]], parent="shoulder_" + k)
    octagon_axle(fo, (mx(-17.25, s), 27.0, 2.0), 8.5, 9.4, "marble")              # the elbow, round at every angle
    fo.cube([fx(-22, 9.5, s), 16, -3], [9.5, 11, 10], "marble")                    # forearm
    fo.cube([fx(-22.5, 10.5, s), 22, -3.5], [10.5, 3, 11], "marble_shade")         # bracer
    hd = m.bone("hand_" + k, [mx(HAND_PIV[0], s), HAND_PIV[1], HAND_PIV[2]], parent="forearm_" + k)
    hd.cube([fx(-22, 9.5, s), 9, -3.5], [9.5, 7, 11], "marble")                    # fist
    for z in (-2.5, 0.2, 2.9, 5.6):
        hd.cube([fx(-22.45, 0.5, s), 10.0, z], [0.5, 1.0, 1.1], "marble_shade")    # knuckle ridge (lod)


def maul(m):
    g = list(GRIP_R)
    mb = m.bone("maul", g, parent="hand_r")
    zoct(mb, (g[0], g[1], 7.5), 3.0, 5.0, "gold")                                  # pommel (z 6..9)
    mb.cube([-19, 10.75, -26], [3.5, 3.5, 32], "marble_shade")                     # the square haft (z -26..6)
    for z in (3, -1, -9):
        mb.cube([-19.3, 10.45, z - 0.5], [4.1, 4.1, 1], "marble_deep")             # grip rings (where the hands sit)
    mb.cube([-19.75, 10, -28], [5, 5, 2], "gold")                                  # ferrule
    mb.cube([-23.25, 2.5, -38], [12, 20, 12], "marble_light")                      # the head (long axis across)
    for y in (1.5, 22.5):
        mb.cube([-23.75, y, -38.5], [13, 1, 13], "marble_shade")                   # striking caps
    for y in (6, 17):
        mb.cube([-23.75, y, -38.5], [13, 1.5, 13], "gold")                         # bands
    for x in (-23.45, -11.05):                                                     # the sun disc through the head
        c = [x, 12.5, HAFT_HEAD_Z]
        mb.box_at(c, [0.4, 7.0, 2.9], "sun")
        mb.box_at(c, [0.4, 2.9, 7.0], "sun")
        mb.box_at(c, [0.4, 5.0, 5.0], "sun", rotation=[45, 0, 0], pivot=c)
    for y in (1.6, 22.6):                                                          # upgrade: gold rims on the caps
        mb.cube([-23.95, y, -38.7], [13.4, 0.8, 13.4], "gold", upgrade=UPGRADE)
    c0 = m.bone("chain_0", [g[0], g[1], 9.0], parent="maul")                       # a votive chain off the pommel
    c0.cube([g[0] - 0.6, 9.5, 8.4], [1.2, 3, 1.2], "gold")
    c1 = m.bone("chain_1", [g[0], 9.5, 9.0], parent="chain_0")
    c1.cube([g[0] - 0.6, 6.5, 8.4], [1.2, 3, 1.2], "gold")
    c2 = m.bone("chain_2", [g[0], 6.5, 9.0], parent="chain_1")
    c2.cube([g[0] - 1.25, 3.0, 8.6], [2.5, 3.5, 0.8], "marble_deep")               # the votive tablet


def body():
    m = Model("Quarry Saint", "sunforged", "T2 siege statue (replaces the Siege Ox)", PALETTE, EMISSIVE)
    pelvis(m)
    for s in (-1, 1):
        leg(m, s)
    torso(m)
    head(m)
    for s in (-1, 1):
        arm(m, s)
    maul(m)
    # intended contacts: the fists round the haft, the sunk head in the chest, the arms under their discs
    m.contact_ok = [("hand_r", "maul"), ("hand_l", "maul"), ("torso", "head"), ("pauldron_r", "shoulder_r"),
                    ("pauldron_l", "shoulder_l")]
    return m


# ------------------------------------------------------------------ the solver ----------------------------------------
def rot_np(rx, ry, rz):
    """Batch rot_mat (degrees arrays) -> (..., 3, 3)."""
    rx, ry, rz = np.radians(rx), np.radians(ry), np.radians(rz)
    cx, sx, cy, sy, cz, sz = np.cos(rx), np.sin(rx), np.cos(ry), np.sin(ry), np.cos(rz), np.sin(rz)
    o, z = np.ones_like(rx), np.zeros_like(rx)
    Rx = np.stack([np.stack([o, z, z], -1), np.stack([z, cx, -sx], -1), np.stack([z, sx, cx], -1)], -2)
    Ry = np.stack([np.stack([cy, z, sy], -1), np.stack([z, o, z], -1), np.stack([-sy, z, cy], -1)], -2)
    Rz = np.stack([np.stack([cz, -sz, z], -1), np.stack([sz, cz, z], -1), np.stack([z, z, o], -1)], -2)
    return Rz @ Ry @ Rx


def world_of(m, bone):
    """(M, t): a rest-space point p of `bone` lands at M p + t in the posed model (no pelvis move)."""
    M = np.eye(3)
    t = np.zeros(3)
    chain = []
    b = m.bones[bone]
    while b is not None:
        chain.append(b)
        b = m.bones[b.parent] if b.parent else None
    for b in reversed(chain):
        rot = [b.rotation[a] + m.pose.get(b.name, [0, 0, 0])[a] for a in range(3)]
        R = np.array(rot_mat(*rot))
        piv = np.array(b.pivot, dtype=float)
        # p_parent = R (p - piv) + piv, then the parent's map
        t = M @ (piv - R @ piv) + t
        M = M @ R
    return M, t


def posed_boxes(m, bones, margin=0.0, min_vol=3.0):
    out = []
    for name in bones:
        for c in m.bones[name].cubes:
            if c["size"][0] * c["size"][1] * c["size"][2] < min_vol:
                continue
            pts = m.cube_corners(name, c)
            out.append((np.array([min(q[a] for q in pts) for a in range(3)]) - margin,
                        np.array([max(q[a] for q in pts) for a in range(3)]) + margin))
    return out


def _stages(rx, ry, rz, bend, solve_cost):
    best = None
    for step, span in ((10, None), (2, 10), (0.5, 2.5), (0.1, 0.6)):
        if span is None:
            ax, ay, az = np.arange(rx[0], rx[1], step), np.arange(ry[0], ry[1], step), np.arange(rz[0], rz[1], step)
            af = np.arange(0, 151, step) if bend > 0 else np.arange(-150, 1, step)
        else:
            c = best
            ax = np.arange(c[0] - span, c[0] + span + 1e-6, step)
            ay = np.arange(c[1] - span, c[1] + span + 1e-6, step)
            az = np.arange(c[2] - span, c[2] + span + 1e-6, step)
            af = np.arange(c[3] - span, c[3] + span + 1e-6, step)
            af = af[af * bend >= 0]
        G = np.meshgrid(ax, ay, az, af, indexing="ij")
        # the elbow / knee pull only picks the branch (the coarse grids); the finer grids then land the limb exactly
        cst = solve_cost(*[g.ravel() for g in G], pull=(span is None or span >= 10))
        i = int(np.argmin(cst))
        best = tuple(float(g.ravel()[i]) for g in G)
    return best


def _chain(m, upper, fore, rest_pt):
    """Batch forward kinematics of a two-bone limb: f(ux, uy, uz, fx) -> (point, elbow, shoulder) world arrays."""
    U, F = m.bones[upper], m.bones[fore]
    Mp, tp = world_of(m, U.parent)
    upiv, fpiv = np.array(U.pivot, float), np.array(F.pivot, float)
    rp = np.array(rest_pt, float)

    def f(ux, uy, uz, fxv):
        Ru = rot_np(ux, uy, uz)
        Rf = rot_np(fxv, np.zeros_like(fxv), np.zeros_like(fxv))
        loc_f = np.einsum("...ij,j->...i", Rf, rp - fpiv) + fpiv
        p = (np.einsum("...ij,...j->...i", Ru, loc_f - upiv) + upiv) @ Mp.T + tp
        e = (np.einsum("...ij,j->...i", Ru, fpiv - upiv) + upiv) @ Mp.T + tp
        return p, e, Mp @ upiv + tp
    return f


def _avoid_cost(boxes, p, e, sh):
    d = 0.0
    for lo, hi in boxes:
        for tt in (-0.3, 0.0, 0.3, 0.6, 0.85):
            q = e + (p - e) * tt if tt >= 0 else e + (sh - e) * (-tt)
            inside = np.all((q > lo) & (q < hi), axis=-1)
            depth = np.minimum((q - lo).min(-1), (hi - q).min(-1))
            d = d + np.where(inside, (depth + 0.1) ** 2 * 2.0, 0.0)
    return d


def limb_solve(m, upper, fore, rest_pt, target, bend=1, elbow_at=None, elbow_w=0.05, avoid=(), avoid_margin=1.0,
               rx=(-100, 171), ry=(-80, 81), rz=(-60, 61)):
    """Rotates `upper` (x, y, z) and `fore` (x only; bend +1 = a hanging part's forward flexion, an elbow; -1 = a knee)
    so `fore`'s rest point lands on target: Model.solve_arm's cost (miss, elbow pull, kept out of `avoid` boxes) over a
    coarse grid then finer ones, in numpy. Returns the miss in px."""
    chain = _chain(m, upper, fore, rest_pt)
    tgt = np.array(target, float)
    ea = None if elbow_at is None else np.array(elbow_at, float)
    boxes = posed_boxes(m, avoid, avoid_margin) if avoid else []

    def cost(ux, uy, uz, fxv, pull=True):
        p, e, sh = chain(ux, uy, uz, fxv)
        d = ((p - tgt) ** 2).sum(-1)
        if ea is not None and pull:
            d = d + elbow_w * ((e - ea) ** 2).sum(-1)
        return d + (_avoid_cost(boxes, p, e, sh) if boxes else 0.0)

    best = _stages(rx, ry, rz, bend, cost)
    m.bones[upper].rotation = [round(best[0], 3), round(best[1], 3), round(best[2], 3)]
    m.bones[fore].rotation = [round(best[3], 3), 0, 0]
    return math.dist(m.fk(fore, rest_pt), target)


def euler_safe(R):
    """DSL rotation (x, then y, then z degrees) of a rotation matrix at full precision (gen_models.euler rounds to 3
    decimals, which misses by more than a corner's tolerance close to its gimbal case), checked."""
    ry = math.asin(max(-1.0, min(1.0, -R[2][0])))
    if math.cos(ry) > 1e-4:
        rx = math.atan2(R[2][1], R[2][2])
        rz = math.atan2(R[1][0], R[0][0])
    else:
        rx, rz = 0.0, math.atan2(-R[0][1], R[1][1])
    rot = [round(math.degrees(v), 4) for v in (rx, ry, rz)]
    M = rot_mat(*rot)
    assert max(abs(M[i][j] - R[i][j]) for i in range(3) for j in range(3)) < 2e-3, "euler_safe"
    return rot


def maul_dir(yaw, pitch):
    """The haft from the grip toward the head: yaw 0 = forward (-Z), +90 = the unit's right (-X); pitch +90 up."""
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    return (-sy * cp, sp, -cy * cp)


def perp(v, d):
    k = _dot(v, d)
    w = tuple(v[i] - k * d[i] for i in range(3))
    n = math.sqrt(_dot(w, w))
    return None if n < 1e-6 else tuple(x / n for x in w)


def hand_frame(d, e):
    """World frame of a fist round the haft: bind -Z (the haft toward the head) -> d, bind +Y (grip -> wrist) -> e."""
    Z = tuple(-x for x in d)
    X = _cross(e, Z)
    return ((X[0], e[0], Z[0]), (X[1], e[1], Z[1]), (X[2], e[2], Z[2]))


def shoulder_world(m, s, T=(0, 0, 0)):
    p = m.fk("shoulder_" + sn(s), m.bones["shoulder_" + sn(s)].pivot)
    return tuple(p[i] + T[i] for i in range(3))


UPPER_LEN = math.dist(SHOULDER_PIV, ELBOW_PIV)
FORE_LEN = ELBOW_PIV[1] - HAND_PIV[1]
GRIP_LEN = HAND_PIV[1] - GRIP_R[1]
WRIST_FREE = 15.0
AVOID_MARGIN = 3.5          # about half a forearm: the solver samples the forearm's axis, the stone is 10 px thick


def arm_on_haft(m, s, G, d, T=(0, 0, 0), t_pref=0.0, t_w=1.0, t_min=None, elbow_at=None, elbow_w=0.04):
    """One arm onto the haft line (world point G, unit direction d toward the head): the fist must close round the
    line (distance 0) at t_pref px along it (weight t_w; never below t_min), the wrist as straight as the haft allows
    (the fist's grip -> wrist axis is the forearm's direction made square to the haft: bend = the forearm's slant to
    the haft's normal plane, free to WRIST_FREE), the elbow and forearm out of the torso, pelvis and head. Then the
    hand bone is turned so the haft runs exactly along d through the fist. Returns (distance from the fist's grip
    point to the haft axis, position along it, wrist bend deg, elbow world)."""
    k = sn(s)
    upper, fore, hand = "shoulder_" + k, "forearm_" + k, "hand_" + k
    hp = (mx(HAND_PIV[0], s), HAND_PIV[1], HAND_PIV[2])
    chain = _chain(m, upper, fore, hp)
    Gm = np.array(G, float) - np.array(T, float)
    dv = np.array(d, float)
    S = np.array(shoulder_world(m, s))
    ea = np.array(elbow_at, float) - np.array(T, float) if elbow_at is not None else \
        S + np.array([s * 4.0, -8.0, -2.0])
    boxes = posed_boxes(m, ("torso", "pelvis", "head"), AVOID_MARGIN)

    def cost(ux, uy, uz, fxv, pull=True):
        p, e, sh = chain(ux, uy, uz, fxv)
        f = p - e
        f = f / (np.linalg.norm(f, axis=-1, keepdims=True) + 1e-9)
        fd = (f * dv).sum(-1)
        ee = -(f - fd[..., None] * dv)
        ee = ee / (np.linalg.norm(ee, axis=-1, keepdims=True) + 1e-9)
        grip = p - GRIP_LEN * ee
        w = grip - Gm
        t = (w * dv).sum(-1)
        r2 = (w ** 2).sum(-1) - t * t
        c = 20.0 * r2 + t_w * (t - t_pref) ** 2          # on the haft first: the hard rule
        if t_min is not None:
            c = c + 20.0 * np.maximum(0.0, t_min - t) ** 2
        bendd = np.degrees(np.arcsin(np.clip(np.abs(fd), 0, 1)))
        c = c + 0.01 * np.maximum(0.0, bendd - WRIST_FREE) ** 2
        if pull:
            c = c + elbow_w * ((e - ea) ** 2).sum(-1)
        return c + _avoid_cost(boxes, p, e, sh)

    best = _stages((-100, 191), (-80, 81), (-70, 71), 1, cost)
    m.bones[upper].rotation = [round(best[0], 3), round(best[1], 3), round(best[2], 3)]
    m.bones[fore].rotation = [round(best[3], 3), 0, 0]
    el = m.fk(fore, m.bones[fore].pivot)
    wr = m.fk(fore, hp)
    f = _unit(tuple(wr[i] - el[i] for i in range(3)))
    e = perp(tuple(-x for x in f), d)
    W = hand_frame(d, e)
    F = world_of(m, fore)[0]
    m.bones[hand].rotation = euler_safe(mat_mul(transpose(tuple(map(tuple, F))), W))
    dist, t = hand_to_haft_line(m, k, G, d, T)
    return dist, t, wrist_bend(m, k), tuple(el[i] + T[i] for i in range(3))


def hand_to_haft_line(m, k, G, d, T=(0, 0, 0)):
    """(distance px from a fist's grip point to the line through world G along d, its position along the line)."""
    s = -1 if k == "r" else 1
    p = m.fk("hand_" + k, (mx(GRIP_R[0], s), GRIP_R[1], GRIP_R[2]))
    w = tuple(p[i] + T[i] - G[i] for i in range(3))
    t = _dot(w, d)
    q = tuple(w[i] - t * d[i] for i in range(3))
    return math.sqrt(_dot(q, q)), t


def haft_axis(m, T=(0, 0, 0)):
    """(G, d): the maul's grip point and the haft's unit direction toward the head, world."""
    G = m.fk("maul", GRIP_R)
    H = m.fk("maul", (GRIP_R[0], GRIP_R[1], GRIP_R[2] - 10.0))
    d = _unit(tuple(H[i] - G[i] for i in range(3)))
    return tuple(G[i] + T[i] for i in range(3)), d


def hand_to_haft(m, k, T=(0, 0, 0)):
    """The fist's grip point against the maul AS POSED (distance to the haft axis, position along it from GRIP_R)."""
    G, d = haft_axis(m, T)
    return hand_to_haft_line(m, k, G, d, T)


def place_right(m, G, d, T=(0, 0, 0), elbow_at=None):
    """The right fist round the haft at (about) world G, the haft exactly along d (the maul hangs from the fist).
    Returns (grip miss from G px, wrist bend deg, elbow world)."""
    dist, t, wb, el = arm_on_haft(m, -1, G, d, T, t_pref=0.0, t_w=4.0, elbow_at=elbow_at)
    return math.hypot(dist, t), wb, el


def place_left(m, T=(0, 0, 0), along=LEFT_ON_HAFT, t_min=LEFT_ON_HAFT, elbow_at=None, t_w=1.0):
    """The left fist round the haft `along` px up from the right grip toward the head (never closer than t_min: the
    fists are 11 px long, so 11 is where they touch). Returns (distance to the haft axis, position along it, wrist
    bend deg, elbow world)."""
    G, d = haft_axis(m, T)
    return arm_on_haft(m, 1, G, d, T, t_pref=along, t_w=t_w, t_min=t_min, elbow_at=elbow_at)


def wrist_bend(m, k):
    """Degrees between the forearm's axis (elbow -> wrist) and the fist's (wrist -> grip)."""
    s = -1 if k == "r" else 1
    el = m.fk("forearm_" + k, m.bones["forearm_" + k].pivot)
    wr = m.fk("hand_" + k, m.bones["hand_" + k].pivot)
    gp = m.fk("hand_" + k, (mx(GRIP_R[0], s), GRIP_R[1], GRIP_R[2]))
    a = _unit(tuple(wr[i] - el[i] for i in range(3)))
    b = _unit(tuple(gp[i] - wr[i] for i in range(3)))
    return math.degrees(math.acos(max(-1.0, min(1.0, _dot(a, b)))))


def plant_leg(m, s, ankle_world, T=(0, 0, 0), yaw=0.0):
    """The leg solved so its ankle lands on ankle_world (knee forward, flexing like a knee), the foot level."""
    k = sn(s)
    A = tuple(ankle_world[i] - T[i] for i in range(3))
    hip = m.fk("thigh_" + k, m.bones["thigh_" + k].pivot)
    knee_pref = (hip[0], (hip[1] + A[1]) / 2, min(hip[2], A[2]) - 1.5)
    m.bones["foot_" + k].rotation = [0, 0, 0]
    if math.dist(A, m.fk("shin_" + k, rest_ankle(s))) < 1e-6 and not any(m.bones["thigh_" + k].rotation)             and not any(m.bones["shin_" + k].rotation):
        m.bones["foot_" + k].rotation = m.level_rotation("shin_" + k, yaw=-s * yaw)
        return 0.0                                   # already there: the straight bind leg
    miss = limb_solve(m, "thigh_" + k, "shin_" + k, (mx(ANKLE_PIV[0], s), ANKLE_PIV[1], ANKLE_PIV[2]), A, bend=-1,
                      elbow_at=knee_pref, elbow_w=0.01, rx=(-60, 91), ry=(-40, 41), rz=(-40, 41))
    m.bones["foot_" + k].rotation = m.level_rotation("shin_" + k, yaw=-s * yaw)
    return miss


def rest_ankle(s):
    return (mx(ANKLE_PIV[0], s), ANKLE_PIV[1], ANKLE_PIV[2])


def plinth_low(m, k, T=(0, 0, 0)):
    """(min y, max y) of the plinth's underside corners (world): both 0 = planted flat."""
    c = m.bones["foot_" + k].cubes[1]
    pts = m.cube_corners("foot_" + k, c)
    ys = sorted(p[1] + T[1] for p in pts)[:4]
    return ys[0], ys[-1]


def chain_plumb(m):
    """chain_0 hangs plumb from the pommel (its world frame level), chain_1/2 straight under it."""
    m.bones["chain_0"].rotation = [0, 0, 0]
    m.bones["chain_0"].rotation = m.level_rotation("maul")


def stole_plumb(m):
    """The stole drops hang plumb (gravity) but never into the body: front only swings out, back only out."""
    for name, sign in (("stole_front_l_0", 1), ("stole_front_r_0", 1), ("stole_back_0", -1)):
        m.bones[name].rotation = [0, 0, 0]
        F = world_of(m, "torso")[0]
        # the torso's world pitch about x (top forward is negative)
        pitch = math.degrees(math.atan2(F[2][1], F[1][1]))      # +: the torso's up axis leans back (+z)
        want = -pitch                                          # rotating by -pitch about x brings it plumb
        want = max(0.0, want) if sign > 0 else min(0.0, want)
        m.bones[name].rotation = [round(max(-35.0, min(35.0, want)), 3), 0, 0]


def maul_head_low(m, T=(0, 0, 0)):
    """The lowest point of the maul head's cubes (world y)."""
    lo = 1e9
    for c in m.bones["maul"].cubes:
        if c["from"][2] < -26.5 and c["color"] != "sun" and not c.get("upgrade"):
            lo = min(lo, min(p[1] for p in m.cube_corners("maul", c)) + T[1])
    return lo


# ------------------------------------------------------------------ the rest stance (the bind) ------------------------
REST_GRIP = (-20.0, 34.0, -14.0)     # the sheet's [-15, 35, -6] is 5.6 px from the shoulder: the stone arm cannot fold
REST_YAW = 10.0                       # so far (see the round log); the fist rests by the pommel ahead-right


def rest_stance(m):
    """REST: the right fist by the pommel, the maul's head on the ground ahead-right lying on its edge, the left arm
    hanging plumb a little out, the plinths flat on y = 0. Pitch found so the head's lowest edge touches y = 0."""
    for s in (-1, 1):
        miss = plant_leg(m, s, rest_ankle(s))
    pitch = -55.0
    for _ in range(8):
        place_right(m, REST_GRIP, maul_dir(REST_YAW, pitch))
        low = maul_head_low(m)
        if abs(low) < 0.08:
            break
        pitch -= math.degrees(low / 34.0) * 0.9
    m.bones["shoulder_l"].rotation = [-TORSO_REST + 4.0, 0, 6.0]   # plumb under the hunch, +4 forward, 6 out
    m.bones["forearm_l"].rotation = [12.0, 0, 0]
    m.bones["hand_l"].rotation = [0, 0, 0]
    chain_plumb(m)
    stole_plumb(m)
    return pitch


def extent(m):
    pts = [p for b in m.bones.values() for c in b.cubes if not c.get("upgrade")
           for p in m.cube_corners(b.name, c)]
    return [min(p[a] for p in pts) for a in range(3)], [max(p[a] for p in pts) for a in range(3)]


def build():
    m = body()
    pitch = rest_stance(m)
    return m, pitch


def main():
    m, pitch = build()
    path = os.path.normpath(os.path.join(OUT, NAME + ".json"))
    with open(path, "w") as f:
        json.dump(m.to_json(), f, indent=1)
    nc = sum(len(b.cubes) for b in m.bones.values())
    lo, hi = extent(m)
    print("wrote %s: %d bones, %d cubes" % (path, len(m.bones), nc))
    print("rest: maul pitch %.1f, yaw %.1f; head low y %.2f; right wrist bend %.1f; hand miss: see the pose log" % (
        pitch, REST_YAW, maul_head_low(m), wrist_bend(m, "r")))
    for k in ("r", "l"):
        print("  plinth %s underside y %.3f..%.3f" % (k, *plinth_low(m, k)))
    print("posed extent px: x %.1f..%.1f  y %.1f..%.1f  z %.1f..%.1f  (blocks %.2f w x %.2f h x %.2f d)" % (
        lo[0], hi[0], lo[1], hi[1], lo[2], hi[2], (hi[0] - lo[0]) / 16, (hi[1] - lo[1]) / 16, (hi[2] - lo[2]) / 16))


if __name__ == "__main__":
    main()
