#!/usr/bin/env python3
"""The Sunforged Halberdier (vindicator_unit in the game) - a T1 anti-cavalry body through the Paladin pipeline
(claude/paladin-radiant.md, claude/sunforged-t1-silhouettes.md).

Silhouette: tall, narrow, upright - and the TALLEST T1 shape because of the one oversized prop: a halberd ~1.6x
his height, planted beside him, its head (an axe blade turned outward, a back hook, a long top spike) high over
the plume. Team colour where the RTS camera looks: the tabard front and back, the helm's plume, a tassel under the
halberd's head. Gothic white harness from the Paladin's lessons (octagon forms, few gold places): a sallet with a
long tail and a dark visor slit, a gorget, small two-lame pauldrons, tassets, a sidearm on the left hip where his
left hand rests.

Rig for the game: the right arm and the halberd hang from one bone on the right shoulder ("halberd_arm"), the
halberd's pivot is the grip (the wrist), the tassel is its own bone; the tabard hangs in a front and a back panel.

    python3 tools/gen_halberdier.py && python3 tools/game_preview.py design/models/sunforged_halberdier_v2.json ...
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT, foot, split_above  # noqa: E402
from gen_paladin import octagon  # noqa: E402

PALETTE = {
    "plate": "#d8d1c2", "plate_dark": "#a39b8a", "visor": "#2e2a26",
    "gold": "#d4a63c", "gold_dark": "#a87f24",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "leather": "#5b3f2a", "leather_dark": "#3f2b1c", "haft": "#6a4a2e",
    "steel": "#a3aab6", "steel_edge": "#d0d6df",
    "sun": "#ffd96a",
}
EMISSIVE = ["sun"]
FRONT = 0.56

HIP = 14.0
SHOULDER_X, SHOULDER_Y = 4.6, 23.4
UPPER, FORE = 5.2, 4.8


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    b.cube([-2.8, HIP - 0.4, -1.9], [5.6, 2.2, 3.8], "leather_dark")                                  # hips under it
    for (y0, y1, w, d, ch) in ((14.0, 17.4, 5.6, 3.8, 0.5), (17.2, 22.4, 7.4, 4.6, 0.7), (22.2, 24.2, 7.0, 4.2, 0.7)):
        octagon(b, y0, y1, w, d, ch, "plate", zf=-d * FRONT)
    octagon(b, 23.8, 25.4, 4.2, 3.8, 0.5, "plate", zf=-3.8 * FRONT)                                    # gorget
    octagon(b, 24.2, 24.7, 4.5, 4.1, 0.55, "gold_dark", zf=-4.1 * FRONT)                               # its rolled edge
    # the belt over the tabard, a gold buckle with the sun; the sidearm on the left hip
    octagon(b, 15.4, 16.5, 6.1, 4.3, 0.55, "leather", zf=-4.3 * FRONT)
    b.cube([-0.8, 15.2, -4.3 * FRONT - 0.35], [1.6, 1.5, 0.4], "gold")
    b.cube([-0.35, 15.6, -4.3 * FRONT - 0.5], [0.7, 0.7, 0.2], "sun")
    sw = (3.5, 15.6, -0.4)
    b.cube([sw[0] - 0.4, sw[1] - 8.0, sw[2] - 0.6], [0.8, 8.0, 1.2], "leather_dark", rotation=[18, 0, 6],
           pivot=list(sw))                                                                              # scabbard
    b.cube([sw[0] - 0.45, sw[1] - 8.6, sw[2] - 0.65], [0.9, 0.7, 1.3], "gold", rotation=[18, 0, 6], pivot=list(sw))
    b.cube([sw[0] - 0.3, sw[1], sw[2] - 0.3], [0.6, 2.4, 0.6], "leather", rotation=[18, 0, 6], pivot=list(sw))  # hilt
    b.cube([sw[0] - 1.1, sw[1] - 0.05, sw[2] - 0.3], [2.2, 0.5, 0.6], "gold", rotation=[18, 0, 6], pivot=list(sw))
    b.cube([sw[0] - 0.45, sw[1] + 2.35, sw[2] - 0.45], [0.9, 0.8, 0.9], "gold", rotation=[18, 0, 6], pivot=list(sw))
    # tassets: two lames on each thigh, hung from the belt
    for s in (1, -1):
        for k in range(2):
            x0 = s * 1.7 - 1.45
            b.cube([x0, 13.0 - k * 1.7, -2.45 + 0.1 * k], [2.9, 1.9, 0.5], "plate" if k == 0 else "plate_dark",
                   rotation=[-8 - 4 * k, 0, s * 6], pivot=[s * 1.7, 15.2, -2.2])
    return b


def tabard(m):
    """Team colour front and back, from the chest to mid-thigh, with the sun of the order on the breast and a gold
    border at the hem; two panels hung from the belt line so they sway and part for the legs."""
    top, bottom = 22.4, 8.0
    for name, z, rot, s in (("tabard_front", -2.85, [4, 0, 0], -1), ("tabard_back", 2.4, [-4, 0, 0], 1)):
        p = m.bone(name, [0, 16.0, z], parent="body", rotation=rot)
        p.cube([-2.4, bottom, z - 0.25], [4.8, top - bottom, 0.5], "tabard")
        p.cube([-2.45, bottom - 0.05, z - 0.3], [4.9, 0.5, 0.6], "gold")                              # hem
        for x in (-2.45, 2.15):                                                                       # its long edges
            p.cube([x, bottom + 0.45, z - 0.3], [0.3, top - bottom - 0.45, 0.6], "gold")
        for fx in (-1.3, 1.1):
            p.cube([fx, bottom + 0.6, z + s * 0.2 - 0.05], [0.25, 5.5, 0.15], "tabard_dark")          # folds
        if s < 0:                                                                                     # the sun
            ez = z - 0.4
            p.box_at((0, 19.6, ez), (1.8, 1.8, 0.3), "gold", rotation=[0, 0, 45], pivot=[0, 19.6, ez])
            p.box_at((0, 19.6, ez - 0.15), (0.9, 0.9, 0.2), "sun", rotation=[0, 0, 45], pivot=[0, 19.6, ez - 0.15])
            for k in range(8):
                a = k * math.pi / 4
                L = 1.1 if k % 2 == 0 else 0.7
                r = 1.35 + L / 2
                cx, cy = r * math.cos(a), 19.6 + r * math.sin(a)
                p.box_at((cx, cy, ez), (L, 0.3, 0.2), "gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, ez])
        split_above(m, name, 16.0, "body")         # the bib on the chest; only the skirt swings from the belt


def helm(m):
    """A gothic sallet: a rounded skull with a long tail over the neck, a bevor, the dark visor slit with a gold brow
    band and the sun at its centre; a team-colour plume falling back from the crown."""
    hy = SHOULDER_Y + 1.6
    h = m.bone("head", [0, hy, 0], parent="body")
    octagon(h, hy + 0.6, hy + 5.4, 5.4, 5.8, 0.9, "plate", zf=-3.0)                                     # skull
    octagon(h, hy + 5.2, hy + 6.4, 3.8, 4.4, 0.6, "plate", zf=-2.3)                                     # crown
    h.cube([-2.5, hy + 0.2, 2.0], [5.0, 2.6, 2.8], "plate", rotation=[-22, 0, 0], pivot=[0, hy + 2.6, 2.4])  # the tail
    h.cube([-2.3, hy - 0.4, -3.3], [4.6, 2.9, 2.2], "plate")                                            # bevor
    h.cube([-1.0, hy - 0.6, -3.55], [2.0, 1.2, 0.6], "plate")                                           # its point
    h.cube([-2.75, hy + 2.6, -3.15], [5.5, 0.65, 0.3], "visor")                                         # the slit
    h.cube([-2.8, hy + 3.25, -3.3], [5.6, 0.5, 0.45], "gold")                                           # brow band
    h.box_at((0, hy + 3.5, -3.55), (0.9, 0.9, 0.25), "sun", rotation=[0, 0, 45], pivot=[0, hy + 3.5, -3.55])
    for s in (1, -1):
        h.cube([s * 2.75 - 0.2, hy + 2.0, -1.2], [0.4, 0.4, 0.4], "gold_dark")                         # rivets
    # the plume: three tufts stepping back and down, team colour with darker tips
    for k, (y, z, L, rx) in enumerate(((hy + 6.4, -0.8, 3.2, -30), (hy + 6.0, 0.8, 3.4, -55), (hy + 5.2, 2.0, 3.6, -80))):
        h.cube([-0.7, y, z - 0.6], [1.4, L, 1.2], "tabard" if k < 2 else "tabard_dark", rotation=[rx, 0, 0],
               pivot=[0, y, z])
    h.cube([-0.45, hy + 6.3, -1.4], [0.9, 0.6, 1.2], "gold")                                            # plume holder
    return h


def pauldron(body, side):
    """Three lames fanned over the shoulder, each with a narrower, thicker rounding lame inside it (the Paladin's
    lesson: a lame alone reads as a flat shelf); the top lame in team colour, the lowest with a gold edge."""
    sx, sy = side * (SHOULDER_X - 0.8), SHOULDER_Y + 1.0
    for i in range(3):
        w, d, droop = 4.2 - 0.4 * i, 4.8 - 0.3 * i, 18 + 20 * i
        x0 = sx if side > 0 else sx - w
        rot, piv = [0, 0, -side * droop], [sx, sy, 0]
        y0 = sy - 1.05 * (i + 1)
        body.cube([x0, y0, -d / 2], [w, 1.1, d], "tabard" if i == 0 else "plate", rotation=rot, pivot=piv)
        body.cube([x0 + (0.0 if side > 0 else 0.5), y0 - 0.2, -d / 2 + 0.5], [w - 0.5, 1.5, d - 1.0],
                  "tabard_dark" if i == 0 else "plate", rotation=rot, pivot=piv)
        if i == 2:
            body.cube([x0, y0 - 0.15, -d / 2 - 0.05], [w, 0.35, d + 0.1], "gold", rotation=rot, pivot=piv)


def arm(m, side, parent, hand=True):
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    up.cube([sx - 1.1, elbow_y + 1.0, -1.15], [2.2, UPPER - 1.0, 2.3], "plate")                         # rerebrace
    up.cube([sx - 1.3, elbow_y + 1.4, -0.85], [2.6, UPPER - 2.2, 1.7], "plate")
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    fo.cube([sx - 1.25, elbow_y - 1.2, -1.3], [2.5, 2.3, 2.6], "plate")                                 # couter
    fo.box_at((sx + side * 1.25, elbow_y - 0.1, 0), (0.4, 1.3, 1.3), "plate_dark", rotation=[45, 0, 0],
              pivot=[sx + side * 1.25, elbow_y - 0.1, 0])
    fo.cube([sx - 1.0, wrist_y + 0.4, -1.05], [2.0, FORE - 1.5, 2.1], "plate")                          # vambrace
    fo.cube([sx - 1.2, wrist_y, -1.25], [2.4, 0.9, 2.5], "plate_dark")                                 # cuff
    if hand:
        fo.cube([sx - 0.9, wrist_y - 2.0, -1.0], [1.8, 2.1, 2.0], "plate_dark")                         # gauntlet
        fo.cube([sx - 0.95, wrist_y - 1.4, -1.2], [1.9, 1.2, 0.5], "plate")
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.3, 0)


def gauntlet_grip(bone, c, side):
    x, y, z = c
    bone.cube([x - 1.0, y - 1.0, z - 1.0], [2.0, 2.0, 2.0], "plate_dark")
    bone.cube([x - 1.1, y - 0.95, z - 1.35], [2.2, 1.9, 0.5], "plate")                                # finger lames
    for k in (-0.4, 0.25):
        bone.cube([x - 1.15, y + k, z - 1.4], [2.3, 0.08, 0.55], "visor")
    bone.cube([x - 0.95, y - 0.95, z + 0.95], [1.9, 1.9, 0.5], "plate")                               # the cuff, on the
    # back face where the forearm comes in (on top it was built for a forearm from above - none comes from there)


def legs(m):
    feet = []
    for side, thigh, shin in ((1, [5, 0, 0], [-8, 0, 0]), (-1, [-3, 0, 0], [-3, 0, 0])):
        pre = "left" if side > 0 else "right"
        hx = side * 1.7
        th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh)
        th.cube([hx - 1.1, 7.6, -1.15], [2.2, HIP - 7.6, 2.3], "plate")                               # cuisse
        th.cube([hx - 1.3, 8.4, -0.85], [2.6, HIP - 9.4, 1.7], "plate")
        sh = m.bone(pre + "_shin", [hx, 7.0, 0], parent=pre + "_thigh", rotation=shin)
        sh.cube([hx - 1.3, 5.6, -1.55], [2.6, 2.2, 2.7], "plate")                                      # knee cop
        sh.box_at((hx, 6.7, -1.65), (1.1, 1.1, 0.4), "plate_dark", rotation=[0, 0, 45], pivot=[hx, 6.7, -1.65])
        sh.cube([hx - 1.0, 1.2, -1.05], [2.0, 4.6, 2.1], "plate")                                     # greave
        sh.cube([hx - 0.85, 2.2, 0.75], [1.7, 3.0, 0.6], "plate")                                     # calf
        # the sabaton: its own bone, level and turned out, a dark lame strapped over the instep (gen_models.foot -
        # lovish Oct 15: "the feet look odd")
        feet.append(foot(m, pre, hx, 0.0, 2.2, 4.8, 1.2, "plate", "plate_dark", strap="plate_dark"))
    return feet


def halberd(m, gx, gz, grip_y):
    """THE prop: ~1.6x his height. An ash haft with gold bands and a leather grip, steel langets, the axe blade turned
    outward (crescent, bright edge), a back hook, a long top spike with a diamond section, a team-colour tassel under
    the head (its own bone)."""
    hb = m.bone("halberd", [gx, grip_y, gz], parent="halberd_arm")
    top = 46.0
    hb.cube([gx - 0.45, 0.4, gz - 0.45], [0.9, top - 0.4, 0.9], "haft")
    hb.cube([gx - 0.55, 0.0, gz - 0.55], [1.1, 1.2, 1.1], "steel")                                  # butt cap
    for yb in (10.0, 30.0):
        hb.cube([gx - 0.55, yb, gz - 0.55], [1.1, 0.6, 1.1], "gold")
    hb.cube([gx - 0.52, grip_y - 2.4, gz - 0.52], [1.04, 4.8, 1.04], "leather_dark")              # the grip
    for s in (1, -1):                                                                              # langets
        hb.cube([gx + s * 0.45 - 0.1, top - 6.0, gz - 0.2], [0.2, 6.0, 0.4], "steel")
    hb.cube([gx - 0.75, top - 0.6, gz - 0.75], [1.5, 1.8, 1.5], "steel")                           # socket
    # the axe blade, turned outward (-x for a right-hand halberd): a crescent of rows, its edge bright
    y = top + 0.4
    for k, (w, h, curve) in enumerate(((2.6, 1.2, 0.0), (3.8, 1.4, 0.3), (4.4, 1.6, 0.6), (4.2, 1.4, 0.5),
                                       (3.4, 1.2, 0.2), (2.0, 1.0, -0.2))):
        x1 = gx - 0.5
        hb.cube([x1 - w, y, gz - 0.2], [w, h + 0.02, 0.4], "steel")
        hb.cube([x1 - w - 0.35 - curve * 0.3, y, gz - 0.15], [0.5, h + 0.02, 0.3], "steel_edge")
        y += h
    blade_top = y
    # "Diamond Axes" research (UpgradeVisuals SHAPE, tag "tempered"): the blade gilded along its edge and the sun
    # struck on both faces
    yb = top + 0.4
    hb.cube([gx - 0.5 - 4.4 - 0.5, yb + 1.0, gz - 0.22], [0.6, 5.4, 0.44], "gold", upgrade="tempered")
    for zz in (gz - 0.32, gz + 0.32):
        hb.box_at((gx - 3.0, top + 4.3, zz), (1.2, 1.2, 0.12), "sun", rotation=[0, 0, 45], pivot=[gx - 3.0, top + 4.3, zz],
                  upgrade="tempered")
    # the back hook: a short spike out the other side, curving up
    hk = (gx + 0.5, top + 3.6, gz)
    hb.cube([hk[0], hk[1] - 0.45, gz - 0.2], [2.4, 0.9, 0.4], "steel", rotation=[0, 0, 22], pivot=list(hk))
    hb.cube([hk[0] + 2.0, hk[1] + 0.2, gz - 0.15], [0.9, 0.5, 0.3], "steel_edge", rotation=[0, 0, 40], pivot=list(hk))
    # the top spike: four tapering sections, a diamond core
    yy = top + 1.2
    for w, h in ((0.9, 2.6), (0.75, 2.4), (0.55, 2.2), (0.35, 1.6)):
        hb.cube([gx - w / 2, yy, gz - w / 2], [w, h + 0.02, w], "steel")
        hb.cube([gx - w * 0.35, yy, gz - w * 0.35], [w * 0.7, h + 0.02, w * 0.7], "steel_edge", rotation=[0, 45, 0],
                pivot=[gx, yy + h / 2, gz])
        yy += h
    # the tassel under the head: a team-colour knot and three strands
    ts = m.bone("tassel", [gx, top - 0.8, gz], parent="halberd")
    ts.cube([gx - 0.75, top - 1.5, gz - 0.75], [1.5, 0.8, 1.5], "gold_dark")
    for k, (dx, dz, L) in enumerate(((-0.4, -0.3, 3.4), (0.3, -0.2, 2.8), (0.0, 0.4, 3.1))):
        ts.cube([gx + dx - 0.25, top - 1.5 - L, gz + dz - 0.25], [0.5, L, 0.5], "tabard")
        ts.cube([gx + dx - 0.25, top - 1.5 - L - 0.5, gz + dz - 0.25], [0.5, 0.55, 0.5], "tabard_dark")
    return hb


def halberdier():
    m = Model("Halberdier (v2)", "sunforged", "anti-cavalry (T1, through the Paladin pipeline)", PALETTE, EMISSIVE)
    body = torso(m)
    tabard(m)
    helm(m)
    for s in (1, -1):
        pauldron(body, s)
    feet = legs(m)
    m.bone("halberd_arm", [-SHOULDER_X, SHOULDER_Y, 0], parent="body")
    ra, rf, rw = arm(m, -1, "halberd_arm", hand=False)
    la, lf, lw = arm(m, 1, "body", hand=True)
    gx, gz, grip_y = -6.1, -2.6, 16.6
    hb = halberd(m, gx, gz, grip_y)
    gauntlet_grip(hb, (gx, grip_y, gz), -1)
    m.contact_ok += [("halberd", "right_fore"), ("halberd", "tassel")]   # the fist on the haft; the tassel tied on
    # the wrist just behind the fist (inboard), the forearm pointing through it at the haft: a straight wrist
    e1 = m.solve_arm(ra, rf, rw, (gx + 0.4, grip_y, gz + 1.3), elbow_out=1, elbow_down=1, point_at=(gx, grip_y, gz),
                     avoid=("body",), avoid_margin=1.0)
    # the left arm hangs easy beside the sidearm (solved onto the hilt it folded up to the shoulder)
    # (the forearm flexes FORWARD - DSL x > 0; it was -22, an elbow bent backward: lovish Oct 15 "the arms look bent")
    m.bones[la].rotation = [2, 0, 3]          # z 3: the hand clear of the sidearm's scabbard (at -6 it sank in 1.3 px)
    m.bones[lf].rotation = [16, 0, 0]
    e2 = 0.0
    for ft, shin in feet:
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-2, 0, 0]}
    return m, max(e1, e2)


MODELS = {"sunforged_halberdier_v2": halberdier}


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
