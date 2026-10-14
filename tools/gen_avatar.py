#!/usr/bin/env python3
"""Tabs - Itadori's avatar (claude/avatar.md): an ORIGINAL builder-warrior from the world of Beyond and Tabs, in
the design/models DSL, one JSON per reaction pose under design/avatar/. He shares Itadori's heart, not anyone's
face: dark-teal messy hair with one sun-gold streak, amber eyes, a scar across the left eyebrow, a sleeveless
charcoal work tunic, parchment forearm wraps, a long sunset-orange scarf whose tail glows when he is fired up, a
utility belt with pouches, boots, and a short builder's hammer.

    python3 tools/gen_avatar.py            # writes design/avatar/tabs_<pose>.json
    python3 tools/preview_model.py design/avatar/tabs_cheer.json --out /tmp/tabs.png

Same space as gen_models.py: y up, he faces -Z, +x is HIS left. ~34 px tall (a young man, a head shorter than the
Paladins), a slightly large head for a readable face at sheet size. Every pose is the same body: the builder sets
bone rotations, the hands are solved onto their targets with gen_models.Model.solve_arm.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design", "avatar")

PALETTE = {
    "skin": "#d39b74", "skin_dark": "#b97f5a", "hair": "#1f5c61", "hair_dark": "#163f43", "streak": "#e8b84a",
    "eye": "#e3a32e", "pupil": "#2a1d12", "brow": "#163f43", "mouth": "#7a3a2c", "teeth": "#f2ece0",
    "scar": "#e8c0a2", "tunic": "#3a3d44", "tunic_dark": "#2b2d33", "wrap": "#e6dcc3", "trousers": "#5a5045",
    "scarf": "#e8692c", "scarf_dark": "#c4501d", "ember": "#ffc35a", "belt": "#6b4a2e", "pouch": "#8a6440",
    "buckle": "#c9a24a", "boot": "#4a3324", "sole": "#2e2017", "steel": "#8c8f96", "steel_dark": "#5e6168",
    "wood": "#8a5a33", "paper": "#4a7bd0", "ink": "#dfe9ff", "crate": "#9a6b3c", "crate_dark": "#6e4a28",
    "sweat": "#bfe6ff",
}
EMISSIVE = ["ember"]

HIP = 13.0          # top of the legs
LEG_W, LEG_D = 3.6, 3.6
THIGH, SHIN = 6.5, 6.5
WAIST_H, CHEST_H = 3.5, 8.0
CHEST_W, DEPTH = 8.4, 4.6
NECK = HIP + WAIST_H + CHEST_H     # 24.5: the head bone's pivot
HEAD_W, HEAD_H, HEAD_D = 8.2, 8.4, 7.6
ARM_W, ARM_D = 3.0, 3.2
UPPER, FORE, HAND = 5.6, 5.2, 2.6
SHOULDER_X = CHEST_W / 2 + ARM_W / 2 - 0.1


def body(m):
    b = m.bone("body", [0, HIP, 0])
    cy = HIP + WAIST_H
    b.cube([-3.6, HIP, -DEPTH / 2 + 0.3], [7.2, WAIST_H, DEPTH - 0.6], "trousers")                          # hips
    b.cube([-CHEST_W / 2, cy, -DEPTH / 2], [CHEST_W, CHEST_H, DEPTH], "tunic")                                 # tunic
    b.cube([-CHEST_W / 2 + 0.6, cy + 1.2, -DEPTH / 2 - 0.3], [CHEST_W - 1.2, CHEST_H - 2.6, 0.4], "tunic_dark")  # front panel
    b.cube([-0.25, cy + 1.6, -DEPTH / 2 - 0.45], [0.5, CHEST_H - 3.4, 0.3], "tunic")                         # seam
    b.cube([-CHEST_W / 2 + 0.2, NECK - 1.2, -DEPTH / 2 + 0.3], [CHEST_W - 0.4, 1.2, DEPTH - 0.6], "skin")      # neckline
    # belt with buckle, two pouches and a loop for the hammer
    b.cube([-3.9, cy - 1.2, -DEPTH / 2 - 0.15], [7.8, 1.5, DEPTH + 0.3], "belt")
    b.cube([-0.9, cy - 1.35, -DEPTH / 2 - 0.4], [1.8, 1.8, 0.4], "buckle")
    b.cube([1.6, cy - 2.6, -DEPTH / 2 - 0.9], [2.0, 2.2, 1.2], "pouch")
    b.cube([-3.6, cy - 2.4, -DEPTH / 2 - 0.7], [1.6, 1.9, 1.0], "pouch")
    b.cube([1.7, cy - 0.7, -DEPTH / 2 - 1.0], [1.8, 0.5, 0.3], "buckle")                                     # pouch clasp
    return b


def head(m, mood):
    h = m.bone("head", [0, NECK, 0], parent="body")
    y0, fz = NECK + 0.4, -HEAD_D / 2
    h.cube([-HEAD_W / 2, y0, fz], [HEAD_W, HEAD_H, HEAD_D], "skin")
    h.cube([-HEAD_W / 2 - 0.15, y0 + 2.6, -0.8], [0.4, 2.0, 1.6], "skin_dark")                              # ears
    h.cube([HEAD_W / 2 - 0.25, y0 + 2.6, -0.8], [0.4, 2.0, 1.6], "skin_dark")
    # hair: a cap, a back mass, side locks, and a messy fringe of angled tufts; one sun-gold streak at his front-left
    h.cube([-HEAD_W / 2 - 0.4, y0 + HEAD_H - 2.0, fz - 0.4], [HEAD_W + 0.8, 2.8, HEAD_D + 0.8], "hair")
    h.cube([-HEAD_W / 2 - 0.4, y0 + 2.0, -fz - 2.0], [HEAD_W + 0.8, HEAD_H - 2.5, 2.4], "hair")
    for s in (1, -1):
        x0 = HEAD_W / 2 - 0.1 if s > 0 else -HEAD_W / 2 - 0.5
        h.cube([x0, y0 + 3.4, fz + 0.6], [0.6, HEAD_H - 3.4, HEAD_D - 1.2], "hair")
    tufts = [(-3.0, -18, 8), (-1.4, 10, -6), (0.4, -12, 4), (2.0, 16, -10), (3.2, -8, 12)]
    for i, (x, rz, rx) in enumerate(tufts):
        col = "streak" if i == 4 else ("hair_dark" if i % 2 else "hair")
        h.cube([x - 0.9, y0 + HEAD_H - 2.8, fz - 0.9], [1.8, 2.4, 1.4], col,
               rotation=[rx - 20, 0, rz], pivot=[x, y0 + HEAD_H - 0.6, fz - 0.2])
    for x, rz in ((-2.2, -25), (1.0, 20), (3.0, -30)):                                                     # crown spikes
        h.cube([x - 0.7, y0 + HEAD_H + 0.4, -1.5], [1.4, 1.8, 2.6], "hair", rotation=[-10, 0, rz],
               pivot=[x, y0 + HEAD_H + 0.4, -0.2])
    # face (on -z): brows, amber eyes, the scar over HIS LEFT brow (+x), and a mouth per mood
    ez = fz - 0.15
    for s in (1, -1):
        ex = s * 1.9
        if mood == "tired":
            h.cube([ex - 0.9, y0 + 3.4, ez - 0.05], [1.8, 0.4, 0.2], "pupil")                                # closed, content
        else:
            h.cube([ex - 0.9, y0 + 2.9, ez - 0.05], [1.8, 1.6, 0.2], "eye")
            h.cube([ex - 0.35 + (0.25 if s < 0 else -0.25), y0 + 3.0, ez - 0.12], [0.8, 1.1, 0.15], "pupil")
        brow_rz = {"determined": s * 12, "oops": -s * 10, "thinking": (s * 8 if s > 0 else 0)}.get(mood, 0)
        h.cube([ex - 1.0, y0 + 4.9, ez - 0.1], [2.0, 0.5, 0.25], "brow", rotation=[0, 0, brow_rz],
               pivot=[ex, y0 + 5.15, ez])
    h.cube([1.2, y0 + 4.3, ez - 0.2], [0.3, 1.8, 0.2], "scar", rotation=[0, 0, -28], pivot=[1.35, y0 + 5.2, ez])
    if mood in ("cheer", "fist_bump"):
        h.cube([-1.5, y0 + 0.9, ez - 0.1], [3.0, 1.1, 0.2], "mouth")                                          # open grin
        h.cube([-1.3, y0 + 1.6, ez - 0.15], [2.6, 0.4, 0.2], "teeth")
    elif mood == "oops":
        h.cube([-0.5, y0 + 1.0, ez - 0.1], [1.0, 0.9, 0.2], "mouth")
        h.cube([-HEAD_W / 2 - 0.6, y0 + 5.4, -2.0], [0.6, 1.2, 0.8], "sweat")                                # sweat drop
    elif mood == "determined":
        h.cube([-1.1, y0 + 1.3, ez - 0.1], [2.2, 0.45, 0.2], "mouth")
    elif mood == "thinking":
        h.cube([-0.2, y0 + 1.3, ez - 0.1], [1.6, 0.45, 0.2], "mouth", rotation=[0, 0, 10], pivot=[0.6, y0 + 1.5, ez])
    else:   # thumbs_up, tired: a closed smile
        h.cube([-1.3, y0 + 1.3, ez - 0.1], [2.6, 0.5, 0.2], "mouth")
        for s in (1, -1):
            h.cube([s * 1.3 - 0.25, y0 + 1.55, ez - 0.1], [0.5, 0.5, 0.2], "mouth")
    return h


def scarf(m, flow):
    """Wrapped twice round the neck (under the chin, not over it), a knot end hanging in front, and the long tail off
    his right shoulder in three chained segments that curl a little more each - flow = degrees the tail streams back
    (negative: hangs slack). The tail's end glows (ember) - the 'fired up' tell from avatar.md."""
    s = m.bone("scarf", [0, NECK, DEPTH / 2], parent="body")
    s.cube([-(CHEST_W + 0.6) / 2, NECK - 1.8, -DEPTH / 2 - 0.6], [CHEST_W + 0.6, 1.6, DEPTH + 1.2], "scarf")
    s.cube([-(CHEST_W - 0.6) / 2, NECK - 0.8, -DEPTH / 2 - 0.4], [CHEST_W - 0.6, 1.4, DEPTH + 0.8], "scarf_dark")
    s.cube([-1.6, NECK - 4.6, -DEPTH / 2 - 0.8], [2.2, 3.0, 0.6], "scarf", rotation=[0, 0, 8],
           pivot=[-0.5, NECK - 1.6, -DEPTH / 2 - 0.5])                                                     # knot end in front
    x0, z0 = -3.6, DEPTH / 2 + 0.2
    y, parent = NECK - 0.8, "scarf"
    segs = ((5.4, 2.9, "scarf"), (5.0, 2.7, "scarf"), (4.6, 2.5, "ember_tip"))
    for i, (L, w, kind) in enumerate(segs):
        bend = flow * (0.5 if i == 0 else 0.35) if flow >= 0 else flow * 0.5
        seg = m.bone("scarf_tail_%d" % i, [x0 + 1.3, y, z0], parent=parent, rotation=[-bend, 0, -6 if i == 0 else 4])
        if kind == "ember_tip":
            seg.cube([x0 + 1.3 - w / 2, y - L + 1.6, z0 - 0.3], [w, L - 1.6, 0.6], "scarf")
            seg.cube([x0 + 1.3 - w / 2, y - L, z0 - 0.35], [w, 1.8, 0.7], "ember")
            seg.cube([x0 + 1.3 - w / 2 + 0.3, y - L - 0.6, z0 - 0.2], [0.5, 0.8, 0.4], "ember")             # frayed ends
            seg.cube([x0 + 1.3 + w / 2 - 0.9, y - L - 0.5, z0 - 0.2], [0.5, 0.7, 0.4], "ember")
        else:
            seg.cube([x0 + 1.3 - w / 2, y - L, z0 - 0.3], [w, L, 0.6], "scarf")
            seg.cube([x0 + 1.3 - w / 2, y - L + 1.2, z0 + 0.25], [w, 0.4, 0.15], "scarf_dark")             # a fold line
        parent, y = seg.name, y - L
    return s


def arm(m, side):
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, NECK - 0.8
    x0 = sx - ARM_W / 2
    elbow_y = sy - UPPER
    wrist_y = elbow_y - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent="body")
    up.cube([x0, elbow_y, -ARM_D / 2], [ARM_W, UPPER, ARM_D], "skin")                                        # bare upper arm
    up.cube([x0 - 0.1, sy - 1.6, -ARM_D / 2 - 0.1], [ARM_W + 0.2, 1.6, ARM_D + 0.2], "tunic")                 # tunic edge
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    fo.cube([x0, wrist_y, -ARM_D / 2], [ARM_W, FORE, ARM_D], "skin")
    fo.cube([x0 - 0.15, wrist_y + 0.3, -ARM_D / 2 - 0.15], [ARM_W + 0.3, FORE - 1.0, ARM_D + 0.3], "wrap")    # forearm wraps
    for k in range(3):
        fo.cube([x0 - 0.2, wrist_y + 0.9 + k * 1.3, -ARM_D / 2 - 0.2], [ARM_W + 0.4, 0.3, ARM_D + 0.4], "skin_dark")
    fo.cube([x0 + 0.1, wrist_y - HAND, -ARM_D / 2 + 0.1], [ARM_W - 0.2, HAND, ARM_D - 0.2], "skin")         # fist
    return pre + "_arm", pre + "_fore", (sx, wrist_y - HAND / 2, 0)


def leg(m, side, thigh_rot, shin_rot):
    pre = "left" if side > 0 else "right"
    hx = side * 1.9
    x0 = hx - LEG_W / 2
    knee_y = HIP - THIGH
    ankle_y = knee_y - SHIN
    th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh_rot)
    th.cube([x0, knee_y, -LEG_D / 2], [LEG_W, THIGH, LEG_D], "trousers")
    sh = m.bone(pre + "_shin", [hx, knee_y, 0], parent=pre + "_thigh", rotation=shin_rot)
    sh.cube([x0, ankle_y + 3.2, -LEG_D / 2], [LEG_W, SHIN - 3.2, LEG_D], "trousers")
    sh.cube([x0 - 0.2, ankle_y, -LEG_D / 2 - 0.2], [LEG_W + 0.4, 3.6, LEG_D + 0.4], "boot")                    # boot shaft
    sh.cube([x0 - 0.3, ankle_y + 3.2, -LEG_D / 2 - 0.3], [LEG_W + 0.6, 0.7, LEG_D + 0.6], "boot")             # cuff
    sh.cube([x0 - 0.2, ankle_y - 0.1, -LEG_D / 2 - 1.6], [LEG_W + 0.4, 1.6, LEG_D + 1.8], "boot")             # toe
    sh.cube([x0 - 0.25, ankle_y - 0.6, -LEG_D / 2 - 1.7], [LEG_W + 0.5, 0.6, LEG_D + 1.9], "sole")
    return pre + "_shin"


def hammer(m, parent, grip):
    """A short builder's hammer, its grip centred on `grip` (rest space of `parent`), head up."""
    gx, gy, gz = grip
    hm = m.bone("hammer", [gx, gy, gz], parent=parent)
    hm.cube([gx - 0.45, gy - 2.5, gz - 0.45], [0.9, 8.5, 0.9], "wood")
    hm.cube([gx - 0.55, gy - 1.2, gz - 0.55], [1.1, 2.2, 1.1], "belt")                                        # leather grip
    hm.cube([gx - 2.6, gy + 5.6, gz - 1.0], [5.2, 2.2, 2.0], "steel")
    hm.cube([gx + 2.4, gy + 5.8, gz - 0.8], [0.7, 1.8, 1.6], "steel_dark")                                    # striking face
    hm.cube([gx - 3.6, gy + 6.0, gz - 0.6], [1.2, 1.2, 1.2], "steel_dark", rotation=[0, 0, -20],
            pivot=[gx - 2.6, gy + 6.6, gz])                                                                       # claw
    return hm


def blueprint_roll(m, parent, at, open_=False):
    x, y, z = at
    bp = m.bone("blueprint", [x, y, z], parent=parent)
    if open_:
        bp.cube([x - 3.2, y - 0.2, z - 4.2], [6.4, 0.3, 4.6], "paper", rotation=[-55, 0, 0], pivot=[x, y, z])
        for k in range(3):
            bp.cube([x - 2.6 + k * 1.8, y + 0.15, z - 3.6], [1.2, 0.1, 3.4], "ink", rotation=[-55, 0, 0], pivot=[x, y, z])
        for s in (1, -1):
            bp.cube([x + s * 3.2 - 0.6, y - 0.6, z - 4.4], [1.2, 1.2, 5.0], "paper", rotation=[-55, 0, 0], pivot=[x, y, z])
    else:
        bp.cube([x - 0.7, y - 4.0, z - 0.7], [1.4, 8.0, 1.4], "paper")
        bp.cube([x - 0.75, y - 0.6, z - 0.75], [1.5, 0.5, 1.5], "belt")
    return bp


def base(name, mood, flow=10, thigh=((0, 0, 0), (0, 0, 0)), shin=((0, 0, 0), (0, 0, 0))):
    m = Model("Tabs - " + name, "avatar", "Itadori's avatar (claude/avatar.md)", PALETTE, EMISSIVE)
    body(m)
    head(m, mood)
    scarf(m, flow)
    la, lf, lh = arm(m, 1)
    ra, rf, rh = arm(m, -1)
    fl = leg(m, 1, thigh[0], shin[0])
    fr = leg(m, -1, thigh[1], shin[1])
    return m, (la, lf, lh), (ra, rf, rh), (fl, fr)


def belt_hammer(m):
    hm = hammer(m, "body", (-3.2, HIP + 0.6, -DEPTH / 2 - 1.0))
    hm.rotation = [0, 0, 160]   # hanging head-down from the belt loop


def rest_arm(m, arm_, side, swing=6):
    a, f, _ = arm_
    m.bones[a].rotation = [swing, 0, -side * 6]
    m.bones[f].rotation = [-8, 0, 0]


# ------------------------------------------------------------------ the seven poses ------------------------------
def cheer():
    m, L, R, feet = base("Cheer", "cheer", flow=35, thigh=((-6, 0, 4), (8, 0, -4)), shin=((10, 0, 0), (-4, 0, 0)))
    belt_hammer(m)
    e = m.solve_arm(R[0], R[1], R[2], (-6.5, NECK + 11.5, -1.0))              # fist up high
    rest_arm(m, L, 1, swing=-10)
    m.bones["body"].rotation = [0, 0, 3]
    m.ground(feet)
    m.pose = {"head": [-12, 0, 0]}
    return m, e


def thumbs_up():
    m, L, R, feet = base("Thumbs up", "thumbs_up", flow=12)
    belt_hammer(m)
    e = m.solve_arm(R[0], R[1], R[2], (-3.5, NECK - 4.0, -7.0), elbow_out=1)   # fist forward at chest height
    m.bones[R[1]].cubes.append({"from": [round(R[2][0] - 0.5, 3), round(R[2][1] + 1.0, 3), round(R[2][2] - 0.5, 3)],
                                "size": [1.0, 2.2, 1.0], "color": "skin"})       # the thumb, up
    rest_arm(m, L, 1)
    m.ground(feet)
    m.pose = {"head": [-4, 10, 0]}
    return m, e


def determined():
    m, L, R, feet = base("Determined", "determined", flow=55, thigh=((4, 0, 6), (-4, 0, -6)))
    grip = (-SHOULDER_X - 0.4, NECK - 0.4, -3.2)
    e = m.solve_arm(R[0], R[1], R[2], grip, elbow_out=2)                                     # fist in front of the shoulder
    hm = hammer(m, "body", (grip[0], grip[1] + 1.6, grip[2]))                               # the hand holds the grip's foot
    hm.rotation = [52, 0, 14]                                                               # head up, laid back over the shoulder
    rest_arm(m, L, 1, swing=2)
    m.bones[L[0]].rotation = [0, 0, -14]
    m.ground(feet)
    m.pose = {"head": [-6, -8, 0]}
    return m, e


def oops():
    m, L, R, feet = base("Oops", "oops", flow=8, thigh=((2, 0, 0), (-2, 0, 0)))
    belt_hammer(m)
    e = m.solve_arm(R[0], R[1], R[2], (-2.5, NECK + 6.5, 3.5), elbow_out=2)     # hand behind his head
    rest_arm(m, L, 1, swing=0)
    m.bones["body"].rotation = [4, 0, -4]
    m.ground(feet)
    m.pose = {"head": [6, 0, -8]}
    return m, e


def fist_bump():
    m, L, R, feet = base("Fist bump", "fist_bump", flow=25, thigh=((-14, 0, 0), (14, 0, 0)), shin=((18, 0, 0), (0, 0, 0)))
    belt_hammer(m)
    e = m.solve_arm(R[0], R[1], R[2], (-2.0, NECK - 3.0, -12.0))               # straight at the viewer
    rest_arm(m, L, 1, swing=-14)
    m.bones["body"].rotation = [8, -12, 0]
    m.ground(feet)
    m.pose = {"head": [-4, 8, 0]}
    return m, e


def tired():
    """Sitting on a crate, scarf loose, hands on his knees."""
    m, L, R, feet = base("Tired but happy", "tired", flow=-4, thigh=((82, 0, -6), (82, 0, 6)), shin=((-84, 0, 0), (-80, 0, 0)))
    belt_hammer(m)
    crate = m.bone("crate", [0, 0, 0])
    crate.cube([-5.5, 0, -2.5], [11, 7.0, 7], "crate")
    for x in (-5.6, 4.6):
        crate.cube([x, 0, -2.6], [1.0, 7.0, 7.2], "crate_dark")
    crate.cube([-5.6, 3.0, -2.6], [11.2, 1.0, 7.2], "crate_dark")
    # the seated body: his seat on the crate top (thighs horizontal at y = HIP drop)
    drop = HIP - 7.6
    for b in ("body", "left_thigh", "right_thigh"):
        bone = m.bones[b]
        bone.pivot[1] -= drop
        for c in bone.cubes:
            c["from"][1] -= drop
            if "pivot" in c:
                c["pivot"][1] -= drop
    for name, bone in m.bones.items():
        if name in ("body", "left_thigh", "right_thigh", "crate"):
            continue
        bone.pivot[1] -= drop
        for c in bone.cubes:
            c["from"][1] -= drop
            if "pivot" in c:
                c["pivot"][1] -= drop
    knee_l = m.fk("left_shin", [1.9, HIP - THIGH - drop, 0])
    knee_r = m.fk("right_shin", [-1.9, HIP - THIGH - drop, 0])
    hl = (L[2][0], L[2][1] - drop, L[2][2])
    hr = (R[2][0], R[2][1] - drop, R[2][2])
    e1 = m.solve_arm(L[0], L[1], hl, (knee_l[0] + 0.5, knee_l[1] + 2.4, knee_l[2] + 1.0))
    e2 = m.solve_arm(R[0], R[1], hr, (knee_r[0] - 0.5, knee_r[1] + 2.4, knee_r[2] + 1.0))
    m.bones["body"].rotation = [10, 0, 0]
    m.pose = {"head": [8, 0, 6]}
    return m, max(e1, e2)


def thinking():
    m, L, R, feet = base("Thinking", "thinking", flow=6, thigh=((3, 0, 2), (-3, 0, -2)))
    belt_hammer(m)
    chin = (0.6, NECK + 1.4, -HEAD_D / 2 - 1.2)
    e1 = m.solve_arm(L[0], L[1], L[2], chin, elbow_out=1)                      # his left hand on his chin
    e2 = m.solve_arm(R[0], R[1], R[2], (-4.0, NECK - 6.0, -6.5), elbow_out=1)  # right hand holds the open plan
    blueprint_roll(m, R[1], (R[2][0] + 1.5, R[2][1] + 0.6, R[2][2] - 1.0), open_=True)
    m.ground(feet)
    m.pose = {"head": [10, 14, 6]}
    return m, max(e1, e2)


POSES = {
    "tabs_cheer": cheer, "tabs_thumbs_up": thumbs_up, "tabs_determined": determined, "tabs_oops": oops,
    "tabs_fist_bump": fist_bump, "tabs_tired": tired, "tabs_thinking": thinking,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    only = sys.argv[1:]
    for name, build in POSES.items():
        if only and name not in only:
            continue
        m, err = build()
        path = os.path.normpath(os.path.join(OUT, name + ".json"))
        with open(path, "w") as f:
            json.dump(m.to_json(), f, indent=1)
        nc = sum(len(b.cubes) for b in m.bones.values())
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes, hand error {err:.2f} px")


if __name__ == "__main__":
    main()
