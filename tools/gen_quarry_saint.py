#!/usr/bin/env python3
"""The Quarry Saint: the Sunforged T2 siege statue (replaces the Siege Ox's Ravager body). Design sheet: Animation
Studio unit 2 (Nov 5); massing "b, the stepped altar" (lovish's pick after the round-2 options, Nov 5). A foundation
statue of the White Angel's temple, cracked open by sunlight, walking off its pedestal with the sanctuary's maul: a
22 px torso on a broken altar step, a head on a short neck in a carved cowl with a solid octagon halo behind it, a
plinth chunk under the left foot, light in its cracks, and a 46 px maul whose light head is the brightest thing on it.

    python3 tools/gen_quarry_saint.py      # writes design/models/sunforged_quarry_saint.json
    python3 tools/render_concept.py --check design/models/sunforged_quarry_saint.json
    python3 tools/dsl_to_game.py design/models/sunforged_quarry_saint.json --class QuarrySaintModel \
        --texture quarry_saint_unit --team tabard,tabard_dark
    python3 tools/quarry_saint_poses.py --out poses.json    # the solved key poses
    python3 tools/quarry_saint_anim.py --keys poses.json    # their key file; the clips: tools/quarry_saint_anim.py

The round-1 body (twin shoulder discs, sunk head) and the round-2 massing options live in git history only
(dac3d820, ad7bd0ac, fead253b).

Space is the DSL's (design/models/README.md): px, y up from the soles, the unit facing -Z, +x its LEFT. Bones: pelvis,
torso, head, halo, thigh/shin/foot, shoulder/forearm/hand, maul + chain, stole, belt tabard (no loose shards); foot_r/foot_l
are ankles (a lowered pelvis keeps the plinths flat on y = 0) and cracks_hp66 / cracks_hp33 hold only emissive
cubes (the game shows each set with ModelPart.visible below 66% / 33% hp).

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


# ------------------------------------------------------------------ the body -------------------------------------------
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


# ------------------------------------------------------------------ the body: the stepped altar (massing b) ---------
# lovish picked massing b (Nov 5); the round-1 body (control) and the round-2 options a / c are in git history only
# (commits dac3d820 r1, ad7bd0ac r2, fead253b r3). The legs and the maul are built at the round-1 coordinates above
# and moved to b's (wider feet, the grip at b's arm); B sets the arm / torso constants the solver and the poses read.
# r3b: the shoulder pivot on the arm's own centre line (r3: 3.5 px inboard, so any inward turn drove the arm's inner
# face into the chest) and the arm 1 px off the torso's side
B = dict(name="Quarry Saint", torso_rest=-4.0, head_rest=4.0, sh_x=-16.0, cx=-16.0,
         up=(8.0, 7.0), fore=(8.0, 6.0), bracer=(8.8, 6.8), fist=(8.0, 7.0), elbow_w=7.0,
         halo_disc=(14.0, 40.0), feet_dx=1.0, haft=4.5)
FEET_DX = 0.0


def configure():
    """b's arm / torso constants (the solver, the rest stance and tools/quarry_saint_poses.py read them)."""
    global GRIP_R, HAND_PIV, ELBOW_PIV, SHOULDER_PIV, TORSO_REST, HEAD_REST, REST_GRIP, UPPER_LEN, FEET_DX
    global CORNER_AVOID
    o = B
    FEET_DX = o["feet_dx"]
    CORNER_AVOID = True
    cx = o["cx"]
    GRIP_R = (cx, 12.5, 2.0)
    HAND_PIV = (cx, 16.0, 2.0)
    ELBOW_PIV = (cx, 27.0, 2.0)
    SHOULDER_PIV = (o["sh_x"], 38.0, 2.0)
    TORSO_REST, HEAD_REST = o["torso_rest"], o["head_rest"]
    REST_GRIP = (-20.0 + (cx + 17.25), 34.0, -14.0)
    UPPER_LEN = math.dist(SHOULDER_PIV, ELBOW_PIV)


def arm_b(m, s, o):
    """An arm: the upper arm ends at its pivot under a round shoulder (an octagon axle outside the torso's side - a
    block reaching above the pivot swung back into the chest whenever the arm rose), a round elbow, a 6 px deep
    forearm with its bracer, the fist."""
    k = sn(s)
    cx = o["cx"]
    piv = [mx(o["sh_x"], s), SHOULDER_PIV[1], SHOULDER_PIV[2]]
    up = m.bone("shoulder_" + k, piv, parent="torso")
    w, d = o["up"]
    up.cube([fx(cx - w / 2, w, s), 26, 2 - d / 2], [w, 12.0, d], "marble")
    octagon_axle(up, (mx(cx, s), 37.5, 2.0), w - 0.2, 8.0, "marble")
    fo = m.bone("forearm_" + k, [mx(cx, s), ELBOW_PIV[1], ELBOW_PIV[2]], parent="shoulder_" + k)
    octagon_axle(fo, (mx(cx, s), 27.0, 2.0), max(w, o["fore"][0]), o["elbow_w"], "marble")
    w, d = o["fore"]
    fo.cube([fx(cx - w / 2, w, s), 16, 2 - d / 2], [w, 11, d], "marble")
    w, d = o["bracer"]
    fo.cube([fx(cx - w / 2, w, s), 22, 2 - d / 2], [w, 3, d], "marble_shade")
    hd = m.bone("hand_" + k, [mx(cx, s), HAND_PIV[1], HAND_PIV[2]], parent="forearm_" + k)
    w, d = o["fist"]
    hd.cube([fx(cx - w / 2, w, s), 9, 2 - d / 2], [w, 7, d], "marble")


def shift_bone_x(m, name, dx):
    b = m.bones[name]
    b.pivot[0] = round(b.pivot[0] + dx, 3)
    for c in b.cubes:
        c["from"][0] = round(c["from"][0] + dx, 3)
        if "pivot" in c:
            c["pivot"][0] = round(c["pivot"][0] + dx, 3)


def head_b(m, o, top):
    """The raised head: a 3 px neck from the torso's top, the skull 10 x 10 x 9.5 in a 12 px cowl, a light face, the
    square beard, and a SOLID octagon halo (o["halo_disc"] = across the flats, tilt back in the world) with a gold rim,
    standing behind and above the crown."""
    h = m.bone("head", [0, top, -1.0], parent="torso", rotation=[o["head_rest"], 0, 0])
    h.cube([-2.5, top - 1.0, -3.5], [5, 4.0, 5], "marble_shade")                   # neck (3 px above the torso)
    y0 = top + 2.5
    h.cube([-5.0, y0, -6.5], [10, 10, 9.5], "marble")                              # skull
    h.cube([-4.0, y0 + 1.5, -7.1], [8, 6.5, 0.8], "marble_light")                  # the face
    h.cube([-4.0, y0 - 0.5, -7.0], [8, 2.5, 4.5], "marble_shade")                  # the square beard
    h.cube([-6.0, y0 + 9.0, -7.3], [12, 2.0, 11.0], "marble_shade")                # cowl: crown
    for x in (-6.0, 4.8):
        h.cube([x, y0, -7.3], [1.2, 9.0, 11.0], "marble_shade")                    # cowl: sides
    h.cube([-6.0, y0, 2.5], [12, 9.0, 1.2], "marble_shade")                        # cowl: back
    w, tilt = o["halo_disc"]
    hc = (0.0, y0 + 9.5, 5.0)
    halo = m.bone("halo", list(hc), parent="head", rotation=[tilt - o["torso_rest"] - o["head_rest"], 0, 0])
    zoct(halo, hc, 1.2, w, "marble_light")                                         # the disc
    from gen_models import ring
    ring(halo, (hc[0], hc[1], hc[2] - 0.1), w / 2 + 0.25, 8, 0.5, 1.5, "gold")    # its gold rim (r3b: 0.5, was 0.8)


def stole_b(m, top, zf, zb, half):
    """The team stole over the shoulders: two straps over the torso's top either side of the neck, hanging as two
    front tails (plumb) and joined in one drop down the back."""
    t = m.bones["torso"]
    for x in (-half - 3.4, half):
        t.cube([x, top, zf - 0.5], [3.4, 1.0, zb - zf + 1.0], "tabard")
        t.cube([x, top - 0.2, zf - 1.0], [3.4, 0.6, 0.6], "tabard_dark")
    for s, k in ((1, "l"), (-1, "r")):
        x0 = half if s > 0 else -half - 3.4
        sf0 = m.bone("stole_front_%s_0" % k, [x0 + 1.7, top, zf - 0.5], parent="torso",
                     rotation=[-B["torso_rest"], 0, 0])
        sf0.cube([x0, top - 10.0, zf - 1.5], [3.4, 10.0, 1.0], "tabard")
        sf1 = m.bone("stole_front_%s_1" % k, [x0 + 1.7, top - 10.0, zf - 1.5], parent="stole_front_%s_0" % k)
        sf1.cube([x0, top - 18.0, zf - 1.7], [3.4, 8.0, 1.0], "tabard")
        sf1.cube([x0 + 1.1, top - 19.8, zf - 1.5], [1.2, 1.8, 0.26], "tabard_dark")
    sb0 = m.bone("stole_back_0", [0, top, zb + 0.5], parent="torso")
    sb0.cube([-half - 3.4, top - 12.0, zb], [2 * half + 6.8, 12.0, 1.0], "tabard")
    sb1 = m.bone("stole_back_1", [0, top - 12.0, zb + 0.5], parent="stole_back_0")
    sb1.cube([-half - 3.4, top - 20.0, zb + 0.2], [2 * half + 6.8, 8.0, 1.0], "tabard")


def cracks_b(m, top, zf, half):
    """The light-cracks: down the chest front and across the top; the damage sets as their own bones."""
    t = m.bones["torso"]
    face_seg(t, (-half + 1.5, top - 1.0), (-half + 3.0, top - 5.0), ("z", zf - 0.05), 1.2, 0.5, "sun")
    face_seg(t, (-half + 3.0, top - 5.0), (-half + 1.8, top - 9.0), ("z", zf - 0.05), 1.2, 0.5, "sun")
    face_seg(t, (half - 2.0, top - 3.0), (half - 3.2, top - 7.5), ("z", zf - 0.05), 1.2, 0.5, "sun")
    flat_seg(t, (-half + 1.0, zf + 6.0), (-half + 4.0, zf + 10.0), top + 0.1, 1.5, 0.5, "sun")
    h66 = m.bone("cracks_hp66", [0, 25, 2], parent="torso")
    face_seg(h66, (half - 3.0, top - 9.0), (half - 1.5, top - 13.0), ("z", zf - 0.05), 1.2, 0.5, "sun")
    h33 = m.bone("cracks_hp33", [0, 25, 2], parent="torso")
    face_seg(h33, (-1.0, top - 12.0), (1.0, top - 16.0), ("z", zf - 0.05), 1.2, 0.5, "sun")


def torso_b(m, o):
    """The altar's step round the hips (20 x 3.5 px, broken at the front-right corner) and the 22 px torso."""
    p = m.bones["pelvis"]
    p.cube([-10, 13, -9], [14.5, 3.5, 20], "marble_shade")                         # the step (left and middle)
    p.cube([4.5, 13, -6.5], [5.5, 3.5, 15.5], "marble_shade")                      # its broken right end, set back
    t = m.bone("torso", [0, 25, 2], parent="pelvis", rotation=[o["torso_rest"], 0, 0])
    t.cube([-11, 24, -6], [22, 10, 15], "marble")                                  # chest
    t.cube([-11, 33, -6.5], [22, 8, 16], "marble")                                 # upper chest (top 41)
    t.cube([-9, 29, -7.2], [18, 6, 1.0], "marble_shade")                           # pectoral
    t.cube([-9, 27, 9.4], [18, 13, 1.6], "marble_deep")                            # quarry back
    return 41.0, -7.2, 9.4, 4.0


def body():
    o = B
    m = Model(o["name"], "sunforged", "T2 siege statue (replaces the Siege Ox)", PALETTE, EMISSIVE)
    pelvis(m)
    for s in (-1, 1):
        leg(m, s)
    top, zf, zb, half = torso_b(m, o)
    head_b(m, o, top)
    stole_b(m, top, zf, zb, half)
    cracks_b(m, top, zf, half + 2.0)
    for s in (-1, 1):
        arm_b(m, s, o)
    global GRIP_R
    keep, GRIP_R = GRIP_R, (-17.25, 12.5, 2.0)
    maul(m)                                                                        # built at the round-1 grip ...
    GRIP_R = keep
    dx = o["cx"] + 17.25
    for name in ("maul", "chain_0", "chain_1", "chain_2"):                         # ... and moved to this grip
        shift_bone_x(m, name, dx)
    for s_ in (-1, 1):                                                             # the feet set wider
        for b_ in ("thigh_", "shin_", "foot_"):
            shift_bone_x(m, b_ + sn(s_), s_ * o["feet_dx"])
    ft = m.bones["foot_r"]                                                         # the plinth under the left only
    x0 = ft.cubes[0]["from"][0]
    ft.cubes = [{"from": [x0, 1.2, -7.5], "size": [11, 5.3, 13], "color": "marble_shade"},
                {"from": [round(x0 - 0.4, 3), 0, -8], "size": [11.8, 1.2, 14], "color": "marble_deep"}]
    for c in m.bones["maul"].cubes:                                                # the maul's bands 1 px (gold trim)
        if c["color"] == "gold" and c["size"] == [13, 1.5, 13]:
            c["from"] = [c["from"][0], round(c["from"][1] + 0.25, 3), c["from"][2]]
            c["size"] = [13, 1.0, 13]
    h = o["haft"]                                                                  # a thicker haft (and its fittings)
    for c in m.bones["maul"].cubes:
        sx, sy, sz = c["size"]
        if (sx, sy) == (3.5, 3.5) or (sx, sy) == (4.1, 4.1) or (sx, sy) == (5, 5):
            g = h - 3.5
            c["from"] = [round(c["from"][0] - g / 2, 3), round(c["from"][1] - g / 2, 3), c["from"][2]]
            c["size"] = [round(sx + g, 3), round(sy + g, 3), sz]
    m.contact_ok = [("hand_r", "maul"), ("hand_l", "maul"), ("torso", "head"), ("head", "halo")]
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

    def f(ux, uy, uz, fxv, up_pts=None, fo_pts=None):
        Ru = rot_np(ux, uy, uz)
        Rf = rot_np(fxv, np.zeros_like(fxv), np.zeros_like(fxv))
        loc_f = np.einsum("...ij,j->...i", Rf, rp - fpiv) + fpiv
        p = (np.einsum("...ij,...j->...i", Ru, loc_f - upiv) + upiv) @ Mp.T + tp
        e = (np.einsum("...ij,j->...i", Ru, fpiv - upiv) + upiv) @ Mp.T + tp
        if up_pts is None:
            return p, e, Mp @ upiv + tp
        a = (np.einsum("...ij,pj->...pi", Ru, up_pts - upiv) + upiv) @ Mp.T + tp
        lf = np.einsum("...ij,pj->...pi", Rf, fo_pts - fpiv) + fpiv
        b = (np.einsum("...ij,...pj->...pi", Ru, lf - upiv) + upiv) @ Mp.T + tp
        return p, e, Mp @ upiv + tp, np.concatenate([a, b], axis=-2)
    return f


def body_obbs(m, names, grow):
    """The posed cubes of `names` (volume > 3) as oriented boxes grown by `grow` px: (centre, inverse half-axes, half
    lengths) arrays for a point-in-box test."""
    C, Ai, H = [], [], []
    for n in names:
        if n not in m.bones:
            continue
        for c in m.bones[n].cubes:
            if c["size"][0] * c["size"][1] * c["size"][2] < 40.0 or not c["color"].startswith("marble"):
                continue                                  # the big stone blocks only (speed; cloth and cracks yield)
            q = np.array(m.cube_corners(n, c), float)
            ax = np.stack([(q[4] - q[0]) / 2, (q[2] - q[0]) / 2, (q[1] - q[0]) / 2], axis=1)   # columns
            ln = np.linalg.norm(ax, axis=0)
            ax = ax * ((ln + grow) / np.maximum(ln, 1e-9))
            C.append(q.mean(0))
            Ai.append(np.linalg.inv(ax))
            H.append(ln + grow)
    return np.array(C), np.array(Ai), np.array(H)


OBB_W = [10.0]                # the body-avoidance weight (arm_on_haft lowers it when the grip suffers)


def obb_cost(obbs, pts, w=10.0):
    """Points (..., P, 3) inside the grown boxes: w x (depth + 0.1)^2 summed (depth = the shallowest way out)."""
    C, Ai, H = obbs
    if len(C) == 0:
        return 0.0
    lead = pts.shape[:-2]
    flat = pts.reshape(-1, pts.shape[-2], 3)
    out = np.empty(flat.shape[0])
    for i in range(0, flat.shape[0], 3000):                                   # in chunks (memory)
        q = flat[i:i + 3000]
        loc = np.einsum("bij,npbj->npbi", Ai, q[:, :, None, :] - C)           # (n, P, B, 3)
        inside = np.all(np.abs(loc) < 1.0, axis=-1)
        depth = np.min((1.0 - np.abs(loc)) * H, axis=-1)
        out[i:i + 3000] = (w * np.where(inside, (depth + 0.1) ** 2, 0.0)).sum((-1, -2))
    return out.reshape(lead)


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
CORNER_AVOID = False          # r3b (the body b): the arm's cube corners kept out of the body's oriented boxes
CLEAR = 0.5                   # ... by this much (the critic's clearance)
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
    # r3b: the arm's own cube corners kept out of the body's oriented boxes (+ CLEAR px): the upper arm below its
    # shoulder seat (y <= 31) and the forearm, both at three heights
    corner = CORNER_AVOID
    if corner:
        obbs = body_obbs(m, ("torso", "pelvis", "head", "halo"), CLEAR)
        up_c = [c for c in m.bones[upper].cubes if c["size"][1] > 10][0]
        fo_c = [c for c in m.bones[fore].cubes if c["size"][1] > 10][0]

        def ring_pts(c, ys):
            x0, z0 = c["from"][0], c["from"][2]
            x1, z1 = x0 + c["size"][0], z0 + c["size"][2]
            return np.array([(x, y, z) for y in ys for x in (x0, x1) for z in (z0, z1)], float)
        up_pts = ring_pts(up_c, (up_c["from"][1], up_c["from"][1] + 4.0, up_c["from"][1] + 8.0,
                                 up_c["from"][1] + up_c["size"][1] - 0.5))
        fo_c0 = fo_c["from"][1]
        fo_pts = ring_pts(fo_c, (fo_c0, fo_c0 + 3.7, fo_c0 + 7.3, fo_c0 + 10.5))
        few = (np.array([0, 3, 12, 15]), np.array([0, 3, 12, 15]))

    def cost(ux, uy, uz, fxv, pull=True):
        if corner:
            big = np.size(ux) > 60000                       # the coarse grid: a few corners only
            p, e, sh, pts = chain(ux, uy, uz, fxv, up_pts[few[0]] if big else up_pts,
                                  fo_pts[few[1]] if big else fo_pts)
        else:
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
            c = c + 200.0 * np.maximum(0.0, t_min - t) ** 2   # r3: the fists never overlap (was 20)
        bendd = np.degrees(np.arcsin(np.clip(np.abs(fd), 0, 1)))
        c = c + 0.01 * np.maximum(0.0, bendd - WRIST_FREE) ** 2
        if pull:
            c = c + elbow_w * ((e - ea) ** 2).sum(-1)
        if corner:
            fist = np.stack([grip, grip + 3.5 * dv, grip - 3.5 * dv, p], axis=-2)
            c = c + obb_cost(obbs, np.concatenate([pts, fist], axis=-2), OBB_W[0])
        return c + _avoid_cost(boxes, p, e, sh)

    best = _stages((-100, 191), (-80, 81), (-70, 71), 1, cost)
    if corner:
        # the haft first (the hard rule): a solve that left the fist off the haft (or under t_min) is redone with the
        # body avoidance at a quarter, then off - the clearance is measured and reported, never bought with the grip
        for wk in (0.25, 0.0):
            p, e, sh = chain(*[np.array([v]) for v in best])
            f_ = (p - e) / np.linalg.norm(p - e)
            fd_ = float((f_ * dv).sum())
            ee_ = -(f_ - fd_ * dv)
            ee_ = ee_ / np.linalg.norm(ee_)
            w_ = (p - GRIP_LEN * ee_) - Gm
            t_ = float((w_ * dv).sum())
            r_ = float(np.sqrt(max(0.0, (w_ ** 2).sum() - t_ * t_)))
            if r_ <= 0.5 and (t_min is None or t_ >= t_min - 0.3):
                break
            obbs = (obbs[0], obbs[1], obbs[2]) if wk else (np.zeros((0, 3)), np.zeros((0, 3, 3)), np.zeros((0, 3)))
            OBB_W[0] = 10.0 * wk
            best = _stages((-100, 191), (-80, 81), (-70, 71), 1, cost)
        OBB_W[0] = 10.0
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
    return (mx(ANKLE_PIV[0] - FEET_DX, s), ANKLE_PIV[1], ANKLE_PIV[2])


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
    configure()
    m = body()
    pitch = rest_stance(m)
    return m, pitch


def main():
    import argparse
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    m, pitch = build()
    path = a.out or os.path.normpath(os.path.join(OUT, NAME + ".json"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
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
