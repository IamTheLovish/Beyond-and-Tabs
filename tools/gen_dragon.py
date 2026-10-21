#!/usr/bin/env python3
"""ONE articulated dragon rig for every winged wyrm (lovish, Oct 21: animations "realistic, alive, and detailed";
dragons should "look like they're breathing and move articulated"), and the first body on it: the Gravebound Bone
Dragon. Our own model - nothing copied from another mod, a vanilla phantom or a game.

The rig (DSL bones; the same names on every dragon, so unit/modelling/animations/Dragon.java animates them all - the
Celestial and Thunder dragons join by building their own body on these bones):

    body                                   the thorax; the root everything hangs from (rises on the downstroke, banks)
      ribs                                 the ribcage alone: swells with the breath without scaling the neck or tail
      neck_1 -> neck_2 -> ... -> neck_5    a chain of five; a wave rolls root to tip, the head counters it
        -> head: jaw, horn_left, horn_right, (crest, ear_left, ear_right: a body carries what it has)
      spine_1 -> spine_2                   the lumbar and the pelvis
        spine_2 -> tail_1 -> ... -> tail_7 a tapering chain of seven, the beat and the sway lag down it
      front_left_upper -> front_left_lower -> front_left_foot       shoulder -> elbow -> wrist, claws on the foot
      front_right_upper -> ...                                      (upper bones hang from body)
      spine_2/hind_left_upper -> hind_left_lower -> hind_left_foot  hip -> knee -> ankle
      spine_2/hind_right_upper -> ...
      wing_left_arm -> wing_left_fore -> wing_left_hand             humerus -> radius -> the hand and its fingers
        wing_left_arm/mem_left_1, wing_left_fore/mem_left_2, wing_left_hand/mem_left_3   membrane panels: fold in stages
      wing_right_arm -> ...

Proportions are a dict (P) so the next dragon changes numbers, not structure. Coordinates: DSL px (16 = 1 block), y up,
the unit faces -z, +x its left (design/models/README.md), its left wing along +x. The Bone Dragon is built at 1x and
drawn x1.4 (BoneDragonRenderer.DRAW_SCALE): a wingspan of ~110 px, nose to tail tip ~150 px, so ~10 blocks across in
the world as the old phantom body was. The body hangs round y 13 (the hitbox's middle at that scale), the legs
tucked under it.

    python3 tools/gen_dragon.py && python3 tools/dsl_to_game.py design/models/gravebound_bone_dragon.json \\
        --class BoneDragonModel --texture bone_dragon_unit --team tabard,tabard_dark
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT  # noqa: E402

PALETTE = {
    "bone": "#d6cfb9", "bone_dark": "#a39a80", "bone_pale": "#ebe6d4", "bone_old": "#bfb69c",
    "tooth": "#f0ebdb", "claw": "#4a443b", "horn": "#3f3a33",
    "deepslate": "#4f5055", "deepslate_dark": "#35363b", "blackstone": "#2a2729", "blackstone_edge": "#4b4448",
    "membrane": "#8e9385", "membrane_dark": "#5c6156", "membrane_pale": "#a9ae9d",
    "soul_glow": "#62f3cb", "soul_core": "#34dba3", "soul_dim": "#1b6d53", "socket": "#15120f",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
}
EMISSIVE = ["soul_glow", "soul_core"]

# the Bone Dragon's proportions (px at 1x): a long wyrm - a deep ribcage, a five-link neck, a seven-link tail, big
# bat-like wings, the legs light and tucked
DRAGON = dict(
    body_w=13.0, body_h=12.0, body_z0=-13.0, body_z1=7.0, body_y=13.0,     # the thorax barrel
    lumbar_z1=16.0, lumbar_w=10.0, lumbar_h=9.5,                            # spine_1
    pelvis_z1=24.0, pelvis_w=9.5, pelvis_h=8.5,                             # spine_2
    tail_n=7, tail_seg=8.0, tail_w0=4.6, tail_w1=1.4,
    neck_n=5, neck_seg=7.0, neck_w0=5.6, neck_w1=3.9, neck_y=15.5,
    head_len=13.0, head_w=6.6, head_h=6.0, jaw_len=11.0,
    shoulder=(6.5, 9.5, -6.5), f_upper=9.0, f_lower=8.0, f_foot=5.0, f_w=3.0,
    hip=(5.0, 8.5, 20.0), h_upper=9.5, h_lower=8.5, h_foot=6.0, h_w=3.4,
    wing_root=(5.5, 17.5, -5.0), arm=17.0, fore=21.0, hand=24.0,
    arm_rest=(0, -12, 8), fore_rest=(0, 8, -5), hand_rest=(0, 18, -4),    # the left wing's rest rotations
)

# the neck's S-curve and the tail's droop at rest (degrees about x per link; x > 0 lifts a forward part's nose and
# drops a backward part's tip)
NECK_REST = [28, 12, -2, -14, -12]
HEAD_REST = -8
TAIL_REST = [8, 5, 2, 0, -2, -3, -3]
# the legs tucked under the body in flight: shoulder back, elbow folded, wrist dropped; hip forward, knee folded
FRONT_REST = (-42, 100, -28)
HIND_REST = (38, -104, 44)


def barrel(bone, cy, z0, z1, w, h, color, k=3, cx=0.0, **kw):
    """A box lying along z with ROUND edges: k nested boxes whose corners lie on the ellipse w x h (gen_quadruped)."""
    A, B = w / 2, h / 2
    s = 1.0 / math.cos(0.5 / k * math.pi / 2)
    for i in range(k):
        t = (i + 0.5) / k * math.pi / 2
        hw, hh = A * s * math.cos(t), B * s * math.sin(t)
        bone.cube([cx - hw, cy - hh, z0], [2 * hw, 2 * hh, z1 - z0], color, **kw)


def bar(bone, p0, p1, w, color, plane="xy", ext=0.0, **kw):
    """A straight bone between two points as one rotated box: in the x-y plane (a rib, rotated about z), the y-z
    plane (a leg link seen from the side, about x) or the x-z plane (a wing finger, about y)."""
    x0, y0, z0 = p0
    x1, y1, z1 = p1
    c = [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2]
    if plane == "xy":
        L = math.hypot(x1 - x0, y1 - y0) + ext
        a = math.degrees(math.atan2(y1 - y0, x1 - x0))
        return bone.box_at(c, [L, w, w], color, rotation=[0, 0, a], pivot=list(c), **kw)
    if plane == "yz":
        L = math.hypot(y1 - y0, z1 - z0) + ext
        a = math.degrees(math.atan2(z1 - z0, y1 - y0))          # 0 = straight up; x > 0 swings a hanging part forward
        return bone.box_at(c, [w, L, w], color, rotation=[-a, 0, 0], pivot=list(c), **kw)
    L = math.hypot(x1 - x0, z1 - z0) + ext
    a = math.degrees(math.atan2(-(z1 - z0), x1 - x0))           # about y: + turns a +x part toward -z
    return bone.box_at(c, [L, w, w], color, rotation=[0, a, 0], pivot=list(c), **kw)


def knob(bone, c, r, color, **kw):
    bone.box_at(list(c), [r, r, r], color, **kw)
    bone.box_at(list(c), [r * 0.7, r * 1.3, r * 0.7], color, **kw)


def vertebra(bone, cz, cy, w, L, color="bone", spine="blackstone", proc=True, spike=1.0):
    """One vertebra lying along z: the centrum (a rounded drum), the neural spine as a blackstone shard leaning back,
    a pair of transverse processes, and a knuckle at the back where the next one joins."""
    barrel(bone, cy, cz - L / 2, cz + L / 2 + 0.4, w, w * 0.95, color, k=2)
    bone.box_at([0, cy, cz + L / 2], [w * 0.8, w * 0.8, 1.4], "bone_dark")                          # the disc
    if spike > 0:
        h = w * 0.9 * spike + 1.0
        c = (0, cy + w / 2 + h / 2 - 0.4, cz)
        bone.box_at(c, [w * 0.28, h, L * 0.42], spine, rotation=[-18, 0, 0], pivot=[0, cy + w / 2 - 0.4, cz])
        bone.box_at((0, cy + w / 2 + h * 0.35, cz - L * 0.1), [w * 0.14, h * 0.5, L * 0.2], "blackstone_edge",
                    rotation=[-18, 0, 0], pivot=[0, cy + w / 2 - 0.4, cz])
    if proc:
        for s in (1, -1):
            bone.box_at([s * (w / 2 + 0.3), cy + w * 0.1, cz], [1.6, w * 0.3, L * 0.35], "bone_dark")


def claw_foot(bone, x, y, z, L, w, color="bone", n=3, fwd=-1):
    """A foot: a flat metatarsal block, n toes fanning forward, each ending in a dark claw that hooks down."""
    bone.cube([x - w / 2, y - L * 0.5, z - w * 0.5], [w, L * 0.5 + 0.6, w], color)
    for i in range(n):
        t = (i - (n - 1) / 2) / max(1, n - 1)
        tx = x + t * w * 1.1
        ang = t * 28
        c = (tx, y - L * 0.55, z + fwd * w * 0.55)
        bone.box_at(c, [w * 0.32, L * 0.5, w * 0.9], "bone_dark", rotation=[0, -fwd * ang, 0], pivot=[tx, y - L * 0.55, z])
        c2 = (tx, y - L * 0.95, z + fwd * w * 1.0)
        bone.box_at(c2, [w * 0.26, L * 0.5, w * 0.3], "claw", rotation=[fwd * 35, -fwd * ang, 0], pivot=[tx, y - L * 0.75, z + fwd * w])


def leg(m, name, parent, top, upper_len, lower_len, foot_len, w, rest, fwd=-1):
    """upper (pivot top) -> lower -> foot, each link a bone bar with a knuckle at each joint. Light bones: a dragon
    carries its weight on its wings; the knee / elbow is a knob so the fold reads from the camera."""
    x, y, z = top
    up = m.bone(name + "_upper", [x, y, z], parent=parent, rotation=[rest[0], 0, 0])
    knob(up, (x, y, z), w * 1.15, "deepslate")                                       # the ball of the joint
    bar(up, (x, y, z), (x, y - upper_len, z), w, "bone", plane="yz", ext=1.0)
    up.box_at((x, y - upper_len * 0.5, z + fwd * -w * 0.5), [w * 0.5, upper_len * 0.6, w * 0.4], "bone_dark")   # the ridge
    ky = y - upper_len
    lo = m.bone(name + "_lower", [x, ky, z], parent=name + "_upper", rotation=[rest[1], 0, 0])
    knob(lo, (x, ky, z), w * 1.0, "bone_old")
    bar(lo, (x, ky, z), (x, ky - lower_len, z), w * 0.8, "bone", plane="yz", ext=0.8)
    bar(lo, (x + w * 0.35, ky - 0.5, z + 0.3), (x + w * 0.2, ky - lower_len + 0.5, z - 0.3), w * 0.35, "bone_dark", plane="yz")  # the second bone
    fy = ky - lower_len
    ft = m.bone(name + "_foot", [x, fy, z], parent=name + "_lower", rotation=[rest[2], 0, 0])
    knob(ft, (x, fy, z), w * 0.85, "bone_old")
    claw_foot(ft, x, fy, z, foot_len, w * 1.6, fwd=fwd)


def membrane_panel(bone, xr, x0, x1, z_lead, depth0, depth1, y, s, color, dark, seed, steps=3, hole=None):
    """A membrane behind a leading-edge bone running along x from x0 to x1 (in the bone's frame): `steps` slabs of
    decreasing depth from z_lead back (depth0 at x0 .. depth1 at x1), each a little lower than the last so they
    overlap, the trailing edge ragged - a notch cut from every other slab, a hole where `hole` says. s = +1 for the
    left wing (panels at +x), -1 for the right; xr is the bone's root x, x0 / x1 distances out along it."""
    rnd = seed
    n = steps
    for i in range(n):
        rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
        xa = x0 + (x1 - x0) * i / n
        xb = x0 + (x1 - x0) * (i + 1) / n
        d = depth0 + (depth1 - depth0) * (i + 0.5) / n
        d *= 1.0 - 0.12 * ((rnd >> 4) % 3)                                 # ragged: some slabs shorter
        col = dark if (rnd >> 7) & 1 and i == n - 1 else color
        cx = (xa + xb) / 2
        w = abs(xb - xa) + 0.3
        X = lambda v: xr + s * v
        if hole is not None and i == hole:
            # a hole: the slab split in two along z with a gap between
            bone.box_at([X(cx), y - 0.1 * i, z_lead + d * 0.22], [w, 0.4, d * 0.42], col)
            bone.box_at([X(cx), y - 0.1 * i, z_lead + d * 0.82], [w, 0.4, d * 0.3], dark)
        else:
            bone.box_at([X(cx), y - 0.1 * i, z_lead + d / 2], [w, 0.4, d], col)
        # the trailing fringe: a tatter hanging off the edge, every other slab
        if i % 2 == 0:
            bone.box_at([X(cx + (xb - xa) * 0.2), y - 0.6, z_lead + d + 1.2], [w * 0.4, 0.4, 2.8], dark,
                        rotation=[-25, 0, 0], pivot=[X(cx + (xb - xa) * 0.2), y - 0.1 * i, z_lead + d])


def wing(m, P, s):
    """One wing (s = +1 left, -1 right): humerus -> radius+ulna -> the hand with four finger bones fanning back,
    membrane panels under each segment so the wing folds in three stages. All x coordinates are mirrored by s and
    the y / z rotations negated, so the two wings are exact mirrors."""
    side = "left" if s > 0 else "right"
    rx, ry, rz = P["wing_root"]
    rx *= s
    mir = lambda r: [r[0], r[1] * s, r[2] * s]
    arm = m.bone("wing_%s_arm" % side, [rx, ry, rz], parent="body", rotation=mir(P["arm_rest"]))
    A = P["arm"]
    knob(arm, (rx, ry, rz), 4.2, "deepslate")                                                     # the shoulder
    arm.box_at([rx + s * 1.5, ry + 1.6, rz], [4.0, 1.4, 3.2], "deepslate_dark")                   # the scapula plate
    bar(arm, (rx, ry, rz), (rx + s * A, ry, rz), 2.3, "bone", plane="xz", ext=1.0)
    arm.box_at([rx + s * A * 0.5, ry + 0.9, rz - 0.8], [A * 0.6, 0.8, 1.0], "bone_pale")         # the lit top ridge
    membrane_panel(arm, rx, 0.5, A, rz + 0.8, 23.0, 16.0, ry - 1.0, s, "membrane", "membrane_dark", seed=3 + s, steps=3)
    # the elbow: the fore bone's pivot
    ex = rx + s * A
    fore = m.bone("wing_%s_fore" % side, [ex, ry, rz], parent="wing_%s_arm" % side, rotation=mir(P["fore_rest"]))
    F = P["fore"]
    knob(fore, (ex, ry, rz), 3.4, "bone_old")
    bar(fore, (ex, ry, rz), (ex + s * F, ry, rz), 1.9, "bone", plane="xz", ext=0.8)
    bar(fore, (ex + s * 0.5, ry - 0.4, rz + 1.6), (ex + s * (F - 0.5), ry - 0.2, rz + 0.6), 0.9, "bone_dark", plane="xz")   # the ulna
    membrane_panel(fore, ex, 0.5, F, rz + 0.8, 17.0, 12.0, ry - 1.0, s, "membrane", "membrane_dark", seed=11 + s, steps=3, hole=1)
    # tattered rags of the owner's colour at the elbow
    fore.box_at([ex + s * 2.0, ry - 2.6, rz + 0.4], [1.4, 4.4, 0.5], "tabard", rotation=[12, 0, s * 15], pivot=[ex + s * 2.0, ry - 0.4, rz + 0.4])
    fore.box_at([ex + s * 3.6, ry - 2.0, rz + 0.6], [1.0, 3.2, 0.5], "tabard_dark", rotation=[18, 0, s * -10], pivot=[ex + s * 3.6, ry - 0.4, rz + 0.6])
    # the hand: the wrist knob and four fingers fanning back from it, the membrane fan between them
    hx = ex + s * F
    hand = m.bone("wing_%s_hand" % side, [hx, ry, rz], parent="wing_%s_fore" % side, rotation=mir(P["hand_rest"]))
    knob(hand, (hx, ry, rz), 3.0, "bone_old")
    hand.box_at([hx + s * 1.5, ry + 1.2, rz - 1.2], [2.2, 2.4, 1.6], "claw", rotation=[0, 0, s * 30], pivot=[hx, ry, rz])   # the thumb claw
    H = P["hand"]
    fingers = ((0, 1.0, 1.8), (26, 0.92, 1.5), (52, 0.78, 1.3), (78, 0.55, 1.1))   # (angle back, length share, thickness)
    for k, (ang, share, th) in enumerate(fingers):
        L = H * share
        a = math.radians(ang)
        tip = (hx + s * L * math.cos(a), ry, rz + L * math.sin(a))
        bar(hand, (hx, ry, rz), tip, th, "bone" if k < 3 else "bone_dark", plane="xz", ext=0.6)
        # a knuckle a third of the way out, a dark hooked tip
        hand.box_at([hx + s * L * 0.38 * math.cos(a), ry, rz + L * 0.38 * math.sin(a)], [th * 1.5, th * 1.5, th * 1.5], "bone_old")
        hand.box_at([tip[0] + s * 0.4 * math.cos(a), ry - 0.6, tip[2] + 0.4 * math.sin(a)], [th * 0.9, 1.6, th * 0.9], "claw")
    # the membrane fan: between each pair of fingers, two slabs along the gap's middle direction (inner short and
    # narrow, outer wide), tattered at the trailing edge; the last gap (toward the body) half rotted away
    for k in range(3):
        a0, a1 = math.radians(fingers[k][0]), math.radians(fingers[k + 1][0])
        L0, L1 = H * fingers[k][1], H * fingers[k + 1][1]
        am = (a0 + a1) / 2
        Lm = min(L0, L1) * 0.92
        for q, (r0, r1, wf) in enumerate(((0.1, 0.5, 0.8), (0.5, 0.86, 1.05), (0.86, 1.0, 0.85))):
            if k == 2 and q == 2:
                continue                                                    # the rotted-out tip of the inner web
            rm = (r0 + r1) / 2 * Lm
            width = 2 * rm * math.tan((a1 - a0) / 2) * wf + 0.6
            c = [hx + s * rm * math.cos(am), ry - 0.9 - 0.1 * q, rz + rm * math.sin(am)]
            col = "membrane_dark" if (k + q) % 3 == 2 else ("membrane_pale" if q == 1 else "membrane")
            hand.box_at(c, [(r1 - r0) * Lm + 0.3, 0.4, width], col, rotation=[0, s * -math.degrees(am), 0], pivot=c)
    return arm, fore, hand


def dragon(m, P):
    """The rig's bones and the plain beast on them from the proportions P."""
    bw, bh, by = P["body_w"], P["body_h"], P["body_y"]
    z0, z1 = P["body_z0"], P["body_z1"]
    body = m.bone("body", [0, by, (z0 + z1) / 2])
    # the spine: a run of vertebrae along the top of the thorax, their shards the dark line the camera reads
    n = 5
    for i in range(n):
        cz = z0 + (z1 - z0) * (i + 0.5) / n
        vertebra(body, cz, by + bh / 2 - 1.0, 4.4, (z1 - z0) / n - 0.6, spike=1.1 if 1 <= i <= 3 else 0.8)
    # the sternum: a keel of bone under the chest, the deepslate breastplate growth over its front
    body.cube([-2.0, by - bh / 2 - 0.6, z0 + 1.0], [4.0, 2.2, z1 - z0 - 3.0], "bone_old")
    body.box_at([0, by - bh / 2 + 0.2, z0 + 2.5], [5.5, 3.0, 5.0], "deepslate", rotation=[20, 0, 0])
    # the ribcage: its own bone so the breath swells it alone
    ribs = m.bone("ribs", [0, by, (z0 + z1) / 2], parent="body")
    nr = 6
    for i in range(nr):
        cz = z0 + 1.5 + (z1 - z0 - 3.0) * i / (nr - 1)
        f = 1.0 - 0.12 * abs(i - 2) / 2                                     # the chest deepest at the third rib
        hw, hh = bw / 2 * f, bh / 2 * f
        pts = []
        for j in range(6):
            t = math.radians(82 - 157 * j / 5)
            pts.append((hw * math.cos(t) * (1.0 if j < 5 else 0.55), by + hh * math.sin(t), cz))
        for s in (1, -1):
            for j in range(5):
                p0 = (s * pts[j][0], pts[j][1], cz + 0.9 * j * 0.35)        # ribs sweep back toward the belly
                p1 = (s * pts[j + 1][0], pts[j + 1][1], cz + 0.9 * (j + 1) * 0.35)
                bar(ribs, p0, p1, 1.5 - 0.1 * j, "bone" if j % 2 == 0 else "bone_pale", plane="xy", ext=0.5)
    # the soul inside: a lit core the ribs stand in front of, a dim haze of it along the sternum
    ribs.box_at([0, by - 0.5, (z0 + z1) / 2], [4.5, 4.0, z1 - z0 - 8.0], "soul_core")
    ribs.box_at([0, by + 1.0, (z0 + z1) / 2 - 2.0], [2.5, 2.5, 5.0], "soul_glow")
    ribs.box_at([0, by - 3.5, (z0 + z1) / 2], [6.5, 1.0, z1 - z0 - 6.0], "soul_dim")
    # the blackstone growths over the shoulders: the stone the Gravebound bind their dead in
    for s in (1, -1):
        body.box_at([s * (bw / 2 - 1.0), by + bh / 2 - 2.0, z0 + 8.0], [3.4, 2.0, 5.0], "blackstone", rotation=[0, 0, s * -30], pivot=[s * (bw / 2 - 2.5), by + bh / 2 - 1.0, z0 + 8.0])
        body.box_at([s * (bw / 2 - 1.8), by + bh / 2 - 3.2, z0 + 7.2], [1.8, 1.4, 2.6], "blackstone_edge", rotation=[0, 0, s * -42], pivot=[s * (bw / 2 - 2.5), by + bh / 2 - 1.0, z0 + 8.0])

    # spine_1 (lumbar) and spine_2 (pelvis): shorter vertebrae, the hip bones flaring out
    lz1, pz1 = P["lumbar_z1"], P["pelvis_z1"]
    sp1 = m.bone("spine_1", [0, by, z1], parent="body")
    for i in range(3):
        cz = z1 + (lz1 - z1) * (i + 0.5) / 3
        vertebra(sp1, cz, by + 1.5 - 0.3 * i, 4.0 - 0.2 * i, (lz1 - z1) / 3 - 0.5, spike=0.8)
    sp1.cube([-1.4, by - 3.5, z1 - 0.5], [2.8, 1.6, lz1 - z1], "bone_old")                          # the belly strap
    sp2 = m.bone("spine_2", [0, by - 0.5, lz1], parent="spine_1")
    for i in range(2):
        cz = lz1 + (pz1 - lz1) * (i + 0.5) / 2
        vertebra(sp2, cz, by + 0.6, 3.6, (pz1 - lz1) / 2 - 0.5, spike=0.9)
    for s in (1, -1):                                                        # the ilium: a flared plate each side
        sp2.box_at([s * (P["pelvis_w"] / 2 - 0.8), by - 0.5, lz1 + 2.5], [1.6, 5.5, 6.0], "bone", rotation=[0, 0, s * 22], pivot=[s * 1.5, by + 1.5, lz1 + 2.5])
        sp2.box_at([s * (P["pelvis_w"] / 2 - 0.4), by - 1.5, lz1 + 1.5], [1.2, 3.0, 3.0], "deepslate_dark", rotation=[0, 0, s * 22], pivot=[s * 1.5, by + 1.5, lz1 + 2.5])
    sp2.box_at([0, by - 3.0, lz1 + 3.5], [4.0, 2.0, 5.0], "bone_old")                              # the pubis

    # the tail: a tapering chain of seven vertebrae, a blackstone blade at the tip
    tw0, tw1, seg = P["tail_w0"], P["tail_w1"], P["tail_seg"]
    parent, z = "spine_2", pz1
    for k in range(P["tail_n"]):
        t = k / (P["tail_n"] - 1)
        w = tw0 + (tw1 - tw0) * t
        b = m.bone("tail_%d" % (k + 1), [0, by + 0.5 - 0.1 * k, z], parent=parent, rotation=[TAIL_REST[k], 0, 0])
        vertebra(b, z + seg / 2, by + 0.5 - 0.1 * k, w, seg - 0.6, proc=k < 4, spike=0.9 - 0.08 * k)
        if k < 4:
            # a tattered scrap of the owner's colour hangs from the second and fourth links
            if k in (1, 3):
                b.box_at([0.4, by - w / 2 - 2.2, z + seg * 0.5], [1.2, 4.0, 0.5], "tabard" if k == 1 else "tabard_dark", rotation=[0, 0, 8], pivot=[0.4, by - w / 2, z + seg * 0.5])
        parent, z = "tail_%d" % (k + 1), z + seg
    tip = m.bones["tail_%d" % P["tail_n"]]
    # the tail blade: three blackstone shards fanning from the last link
    for q, (ang, L) in enumerate(((0, 7.0), (28, 5.0), (-28, 5.0))):
        c = [0, by - 0.2, z + L / 2 - 0.5]
        tip.box_at(c, [1.0, 2.6 if q == 0 else 1.8, L], "blackstone", rotation=[0, ang, 0], pivot=[0, by - 0.2, z - 0.5])
        tip.box_at([0, by + 0.9 if q == 0 else by + 0.5, z + L * 0.45], [0.5, 0.8, L * 0.6], "blackstone_edge", rotation=[0, ang, 0], pivot=[0, by - 0.2, z - 0.5])

    # the neck: five vertebrae climbing forward in an S, each a bone so the chain rolls; the head on the last
    nw0, nw1, nseg, ny = P["neck_w0"], P["neck_w1"], P["neck_seg"], P["neck_y"]
    parent, z = "body", z0
    for k in range(P["neck_n"]):
        t = k / (P["neck_n"] - 1)
        w = nw0 + (nw1 - nw0) * t
        b = m.bone("neck_%d" % (k + 1), [0, ny - 0.3 * k, z], parent=parent, rotation=[NECK_REST[k], 0, 0])
        vertebra(b, z - nseg / 2, ny - 0.3 * k, w, nseg - 0.6, spike=1.0 - 0.1 * k)
        b.box_at([0, ny - 0.3 * k - w / 2 - 0.3, z - nseg / 2], [w * 0.5, 1.2, nseg * 0.7], "bone_old")   # the throat strap
        parent, z = "neck_%d" % (k + 1), z - nseg
    head = m.bone("head", [0, ny - 1.5, z], parent=parent, rotation=[HEAD_REST, 0, 0])
    return body, ribs, head, z


def bone_skull(m, P, head, z):
    """The skull off the neck's end at z (facing -z): a long muzzle, a heavy brow with lit sockets, teeth, swept
    horns, a blackstone crest; the jaw its own bone."""
    hl, hw, hh = P["head_len"], P["head_w"], P["head_h"]
    hx, hy, hz = head.pivot
    top, bot = hy + hh * 0.55, hy - hh * 0.45
    # the brain case and the muzzle: the case a rounded block, the muzzle narrower and lower
    barrel(head, (top + bot) / 2, hz - hl * 0.45, hz + 1.5, hw, hh, "bone", k=2)
    barrel(head, (top + bot) / 2 - 0.8, hz - hl, hz - hl * 0.4, hw * 0.72, hh * 0.78, "bone", k=2)
    head.box_at([0, top - 0.3, hz - hl * 0.25], [hw * 0.9, 1.4, hl * 0.4], "bone_pale")                 # the lit brow
    head.box_at([0, (top + bot) / 2 - 1.0, hz - hl + 1.0], [hw * 0.5, hh * 0.5, 2.2], "bone_dark")     # the nares
    # the eye sockets: a dark pit each side under the brow, a lit soul in it (the glow the camera reads)
    for s in (1, -1):
        head.cube([s * (hw / 2 - 0.4) - 1.2, top - 3.2, hz - hl * 0.42], [2.4, 2.2, 2.4], "socket")
        head.cube([s * (hw / 2 - 0.2) - 0.8, top - 2.8, hz - hl * 0.42 + 0.4], [1.6, 1.4, 1.6], "soul_glow", inflate=0.3)
        # the brow ridge over the socket, a blackstone plate growing on it
        head.box_at([s * (hw / 2 - 0.6), top - 0.6, hz - hl * 0.42 + 0.5], [2.6, 1.4, 3.6], "bone_old", rotation=[0, 0, s * -18], pivot=[s * (hw / 2 - 0.6), top - 0.6, hz - hl * 0.42])
        # the horn: a bone, swept up and back, a dark tip, out of a stone collar
        hb = m.bone("horn_%s" % ("left" if s > 0 else "right"), [s * (hw / 2 - 0.8), top - 0.5, hz - 1.0], parent="head")
        base = (s * (hw / 2 - 0.8), top - 0.5, hz - 1.0)
        hb.box_at(list(base), [2.6, 2.6, 2.6], "blackstone")
        c1 = (base[0] + s * 1.6, base[1] + 2.2, base[2] + 3.0)
        hb.box_at(c1, [1.8, 1.8, 7.0], "horn", rotation=[-32, 0, s * -14], pivot=list(base))
        c2 = (base[0] + s * 2.6, base[1] + 4.2, base[2] + 8.4)
        hb.box_at(c2, [1.3, 1.3, 5.0], "horn", rotation=[-44, 0, s * -14], pivot=list(base))
        c3 = (base[0] + s * 3.0, base[1] + 6.2, base[2] + 11.8)
        hb.box_at(c3, [0.9, 0.9, 3.0], "claw", rotation=[-58, 0, s * -14], pivot=list(base))
    # the crest: a blackstone shard up from the back of the skull, and the upper teeth along the muzzle
    head.box_at([0, top + 1.6, hz - 1.5], [1.4, 4.0, 3.0], "blackstone", rotation=[-30, 0, 0], pivot=[0, top, hz - 1.5])
    head.box_at([0, top + 2.8, hz - 2.2], [0.6, 2.2, 1.2], "blackstone_edge", rotation=[-30, 0, 0], pivot=[0, top, hz - 1.5])
    for i in range(5):
        tz = hz - hl + 1.2 + i * (hl * 0.55) / 4
        for s in (1, -1):
            head.box_at([s * (hw * 0.33 - 0.1 * i), bot - 0.5, tz], [0.7, 1.6 if i % 2 else 1.1, 0.7], "tooth")
    # the jaw: hinged at the back of the skull, a long mandible with its own teeth, the throat glow between
    jaw = m.bone("jaw", [0, bot + 0.8, hz - 0.5], parent="head")
    jaw.cube([-hw * 0.36, bot - 1.6, hz - hl + 0.6], [hw * 0.72, 1.8, hl - 0.6], "bone_old")
    jaw.cube([-hw * 0.3, bot - 2.4, hz - hl + 0.6], [hw * 0.6, 1.0, hl * 0.6], "bone_dark")
    jaw.box_at([0, bot - 1.2, hz - hl * 0.3], [hw * 0.5, 0.9, hl * 0.4], "soul_dim")                      # the lit gullet
    for i in range(4):
        tz = hz - hl + 1.6 + i * (hl * 0.5) / 3
        for s in (1, -1):
            jaw.box_at([s * (hw * 0.3 - 0.1 * i), bot + 0.2, tz], [0.6, 1.3 if i % 2 == 0 else 0.9, 0.6], "tooth")
    return jaw


def bone_dragon():
    P = DRAGON
    m = Model("Bone Dragon", "gravebound", "T3 flying wyrm, the Gravebound's dead dragon (dragon rig)", PALETTE, EMISSIVE)
    body, ribs, head, hz = dragon(m, P)
    bone_skull(m, P, head, hz)
    for s in (1, -1):
        wing(m, P, s)
    sx, sy, sz = P["shoulder"]
    for s, side in ((1, "left"), (-1, "right")):
        leg(m, "front_" + side, "body", (s * sx, sy, sz), P["f_upper"], P["f_lower"], P["f_foot"], P["f_w"], FRONT_REST)
    hx, hy, hzz = P["hip"]
    for s, side in ((1, "left"), (-1, "right")):
        leg(m, "hind_" + side, "spine_2", (s * hx, hy, hzz), P["h_upper"], P["h_lower"], P["h_foot"], P["h_w"], HIND_REST)
    m.pose = {}
    m.limb_ok = [b for b in m.bones]          # a flier at rest: nothing stands on the ground
    return m


MODELS = {"gravebound_bone_dragon": bone_dragon}


def main():
    only = sys.argv[1:]
    for name, build in MODELS.items():
        if only and name not in only:
            continue
        m = build()
        path = os.path.normpath(os.path.join(OUT, name + ".json"))
        with open(path, "w") as f:
            json.dump(m.to_json(), f, indent=1)
        nc = sum(len(b.cubes) for b in m.bones.values())
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes")


if __name__ == "__main__":
    main()
