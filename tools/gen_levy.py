#!/usr/bin/env python3
"""The Sunforged Crossbow Levy (pillager_unit in the game) - a T1 skirmisher body through the Paladin pipeline
(claude/paladin-radiant.md, claude/sunforged-t1-silhouettes.md).

Silhouette: an average-height levy in a quilted gambeson under a WIDE-BRIMMED kettle hat (a ring from the RTS
camera), carrying the one oversized prop - a heavy crossbow held across the body, its steel prod wider than his
shoulders (the horizontal bar that names his role) - and a tall pavise slung on his back, painted in team colour
with the sun (the big colour the camera sees from behind and above). No face: a mail coif, the eyes a dark band in
the shadow of the brim. Detail where it reads: the quilting, a bolt quiver at the right hip with white fletchings, a
crank (cranequin) at the left hip, the crossbow's stirrup, nut, trigger lever and string.

Rig for the game: both arms and the crossbow hang from one bone on the shoulder line ("xbow_arms"); the gauntlets
are ON the crossbow (its pivot is the right-hand grip), the arms solved onto them - raising the rig and levelling
the crossbow is the aim; the tabard hangs in a front and a back panel.

    python3 tools/gen_levy.py && python3 tools/game_preview.py design/models/sunforged_crossbow_levy_v2.json ...
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT  # noqa: E402
from gen_paladin import octagon  # noqa: E402

PALETTE = {
    "cloth": "#e4dccb", "cloth_dark": "#b3a891", "mail": "#8f939a", "visor": "#2e2a26",
    "steel": "#a3aab6", "steel_dark": "#7c828d", "gold": "#d4a63c", "gold_dark": "#a87f24",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "leather": "#6b4a30", "leather_dark": "#4a3220", "wood": "#7a5536", "hose": "#4b4440", "boot": "#5b3f2a",
    "string": "#efe6d2", "feather": "#f2eee6", "sun": "#ffd96a",
}
EMISSIVE = ["sun"]
FRONT = 0.56

HIP = 13.0
SHOULDER_X, SHOULDER_Y = 4.2, 22.4
UPPER, FORE = 5.0, 4.6


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    octagon(b, 9.4, 13.6, 6.6, 4.6, 0.7, "cloth", zf=-4.6 * FRONT)                                   # gambeson skirt
    for (y0, y1, w, d, ch) in ((13.2, 16.2, 6.0, 4.2, 0.6), (16.0, 21.6, 6.8, 4.6, 0.7), (21.4, 23.4, 6.4, 4.2, 0.7)):
        octagon(b, y0, y1, w, d, ch, "cloth", zf=-d * FRONT)
    # quilting: a few long seams on the sides and the back (the front is under the tabard)
    for y in (11.2, 17.6, 19.6):
        for s in (1, -1):
            b.cube([s * 3.35 - 0.1 if y > 13 else s * 3.25 - 0.1, y, -1.2], [0.2, 0.18, 2.6], "cloth_dark")
    for y in (17.4, 19.4):
        b.cube([-2.4, y, 4.6 * (1 - FRONT) - 0.1], [4.8, 0.18, 0.2], "cloth_dark")
    octagon(b, 14.6, 15.6, 6.5, 4.7, 0.65, "leather", zf=-4.7 * FRONT)                                # belt
    b.cube([-0.7, 14.4, -4.7 * FRONT - 0.35], [1.4, 1.4, 0.4], "gold")                                 # buckle
    # the bolt quiver at the right hip, its bolts' white fletchings showing
    qp = (-3.6, 14.2, 1.0)
    b.cube([qp[0] - 1.0, 9.8, qp[2] - 0.9], [2.0, 5.8, 1.8], "leather", rotation=[8, 0, -12], pivot=list(qp))
    b.cube([qp[0] - 1.05, 15.2, qp[2] - 0.95], [2.1, 0.5, 1.9], "leather_dark", rotation=[8, 0, -12], pivot=list(qp))
    for k, (dx, dz) in enumerate(((-0.5, -0.4), (0.4, -0.3), (-0.2, 0.4), (0.5, 0.45))):
        b.cube([qp[0] + dx - 0.15, 15.6, qp[2] + dz - 0.15], [0.3, 1.8 + 0.2 * k, 0.3], "wood", rotation=[8, 0, -12],
               pivot=list(qp))
        b.cube([qp[0] + dx - 0.3, 16.6 + 0.2 * k, qp[2] + dz - 0.05], [0.6, 0.9, 0.1], "feather", rotation=[8, 0, -12],
               pivot=list(qp))
    # the crank at the left hip: a gear case and its handle
    b.cube([3.1, 11.8, -0.9], [1.0, 2.2, 1.8], "steel_dark")
    b.cube([3.3, 12.4, -1.5], [0.6, 0.6, 0.6], "steel")
    b.cube([3.4, 10.4, -1.3], [0.4, 2.0, 0.4], "steel")
    return b


def tabard(m):
    top, bottom = 21.4, 9.0
    for name, z, rot, s in (("tabard_front", -2.85, [4, 0, 0], -1), ("tabard_back", 2.35, [-4, 0, 0], 1)):
        p = m.bone(name, [0, 15.0, z], parent="body", rotation=rot)
        p.cube([-2.2, bottom, z - 0.25], [4.4, top - bottom, 0.5], "tabard")
        p.cube([-2.25, bottom - 0.05, z - 0.3], [4.5, 0.45, 0.6], "gold")
        if s < 0:
            ez = z - 0.4
            p.box_at((0, 18.8, ez), (1.5, 1.5, 0.3), "gold", rotation=[0, 0, 45], pivot=[0, 18.8, ez])
            p.box_at((0, 18.8, ez - 0.15), (0.7, 0.7, 0.2), "sun", rotation=[0, 0, 45], pivot=[0, 18.8, ez - 0.15])


def pavise(body):
    """The tall shield slung on his back: wood with a steel rim, team-colour face, the sun in gold, a strap."""
    c = (0.8, 17.0, 3.6)
    rot = [-8, 0, -14]
    w, h = 6.6, 11.0
    body.cube([c[0] - w / 2, c[1] - h / 2, c[2]], [w, h, 0.7], "wood", rotation=rot, pivot=list(c))
    body.cube([c[0] - w / 2 + 0.4, c[1] - h / 2 + 0.4, c[2] + 0.5], [w - 0.8, h - 0.8, 0.4], "tabard", rotation=rot,
              pivot=list(c))
    for (x0, y0, ww, hh) in ((-w / 2 - 0.1, -h / 2 - 0.1, w + 0.2, 0.5), (-w / 2 - 0.1, h / 2 - 0.4, w + 0.2, 0.5),
                             (-w / 2 - 0.1, -h / 2, 0.5, h), (w / 2 - 0.4, -h / 2, 0.5, h)):
        body.cube([c[0] + x0, c[1] + y0, c[2] - 0.05], [ww, hh, 0.95], "steel", rotation=rot, pivot=list(c))
    body.cube([c[0] - 0.6, c[1] - h / 2 + 0.4, c[2] + 0.75], [1.2, h - 0.8, 0.3], "tabard_dark", rotation=rot,
              pivot=list(c))                                                                              # the ridge
    sc = (c[0], c[1] + 1.2, c[2] + 0.95)
    body.box_at(sc, (2.4, 2.4, 0.25), "gold", rotation=rot[:2] + [rot[2] + 45], pivot=list(c))
    body.box_at(sc, (1.2, 1.2, 0.35), "sun", rotation=rot[:2] + [rot[2] + 45], pivot=list(c))
    # its strap across the chest, right shoulder to left hip (under the tabard's edge)
    a = math.degrees(math.atan2(14.8 - 22.4, 3.2 - (-3.2)))
    L = math.hypot(14.8 - 22.4, 3.2 - (-3.2))
    body.box_at((0.0, (14.8 + 22.4) / 2, -4.6 * FRONT - 0.2), (L, 0.7, 0.3), "leather_dark", rotation=[0, 0, a],
                pivot=[0.0, (14.8 + 22.4) / 2, -4.6 * FRONT - 0.2])


def head(m):
    hy = SHOULDER_Y + 0.9
    h = m.bone("head", [0, hy, 0], parent="body")
    octagon(h, hy, hy + 4.6, 4.6, 4.6, 0.7, "mail", zf=-2.5)                                          # mail coif
    # the face is the brim's shadow: one dark plane framed by the coif (mail rows across it read as a robot grille)
    h.cube([-1.6, hy + 0.9, -2.6], [3.2, 3.0, 0.2], "visor")
    h.cube([-2.4, hy - 0.4, -2.4], [4.8, 1.2, 4.6], "mail")                                           # aventail on the neck
    h.cube([-2.45, hy + 0.3, -2.45], [4.9, 0.35, 4.7], "steel_dark")                                  # its edge
    # the kettle hat: a dome, a team-colour band with the sun, and the wide brim (an octagon ring, from above a disc)
    octagon(h, hy + 4.0, hy + 6.4, 4.9, 4.9, 0.9, "steel", zf=-2.7)
    octagon(h, hy + 6.2, hy + 6.9, 3.4, 3.4, 0.7, "steel", zf=-1.95)
    octagon(h, hy + 4.2, hy + 4.9, 5.1, 5.1, 0.95, "tabard", zf=-2.8)
    h.box_at((0, hy + 4.55, -2.95), (0.8, 0.8, 0.2), "sun", rotation=[0, 0, 45], pivot=[0, hy + 4.55, -2.95])
    octagon(h, hy + 3.7, hy + 4.15, 9.0, 9.0, 2.4, "steel", zf=-4.75)                                 # the brim
    octagon(h, hy + 3.45, hy + 3.75, 9.4, 9.4, 2.6, "steel_dark", zf=-4.95)                           # its lip
    return h


def arm(m, side, parent):
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    up.cube([sx - 1.2, elbow_y, -1.3], [2.4, UPPER + 0.5, 2.6], "cloth")
    up.cube([sx - 1.25, sy - 1.6, -1.35], [2.5, 0.2, 2.7], "cloth_dark")                             # a seam
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    fo.cube([sx - 1.15, elbow_y - 1.6, -1.25], [2.3, 1.8, 2.5], "cloth")
    fo.cube([sx - 1.05, wrist_y, -1.15], [2.1, FORE - 1.5, 2.3], "leather")                         # bracer
    fo.cube([sx - 1.1, wrist_y + 1.4, -1.2], [2.2, 0.3, 2.4], "leather_dark")
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.2, 0)


def glove(bone, c):
    x, y, z = c
    bone.cube([x - 0.95, y - 0.95, z - 0.95], [1.9, 1.9, 1.9], "leather")
    bone.cube([x - 1.0, y - 1.0, z - 1.0], [2.0, 0.5, 2.0], "leather_dark")


def crossbow(m, x, y, z_butt):
    """THE prop: a heavy crossbow pointing forward (-z) - a wooden stock, the steel prod across the front wider than his
    shoulders (two halves swept back), the string to the nut, a stirrup at the nose, a long trigger lever under."""
    gx, gy, gz = x, y, z_butt - 1.2
    cb = m.bone("crossbow", [gx, gy, gz], parent="xbow_arms")
    L = 12.4
    z0 = z_butt - L                                                                                  # the nose
    cb.cube([x - 0.6, y - 0.7, z0], [1.2, 1.3, L], "wood")                                          # the stock
    cb.cube([x - 0.75, y - 1.5, z_butt - 3.2], [1.5, 2.2, 3.2], "wood")                             # the butt
    cb.cube([x - 0.65, y + 0.6, z0 + 2.0], [1.3, 0.3, L - 5.4], "wood")                             # the rail
    cb.cube([x - 0.4, y + 0.6, z0 + 6.2], [0.8, 0.6, 0.8], "steel")                                 # the nut
    cb.cube([x - 0.2, y - 2.6, z_butt - 5.4], [0.4, 2.1, 0.5], "steel", rotation=[-20, 0, 0],
            pivot=[x, y - 0.7, z_butt - 5.2])                                                        # trigger lever
    for s in (1, -1):                                                                               # the prod
        pz = z0 + 0.8
        # the arms sweep BACK toward him (a drawn prod), so Ry turns the +x half by -16 and the -x half by +16
        cb.cube([x if s > 0 else x - 7.0, y - 0.35, pz - 0.4], [7.0, 0.8, 0.8], "steel", rotation=[0, -s * 16, 0],
                pivot=[x, y, pz])
        cb.cube([x + 6.4 if s > 0 else x - 7.0, y - 0.45, pz - 0.5], [0.6, 1.0, 1.0], "steel_dark",
                rotation=[0, -s * 16, 0], pivot=[x, y, pz])                                          # the tips
        # the string, from the tip back to the nut
        tip = (x + s * 6.6 * math.cos(math.radians(16)), y + 0.35, pz + 6.6 * math.sin(math.radians(16)))
        nut = (x, y + 0.7, z0 + 6.6)
        dx, dz = nut[0] - tip[0], nut[2] - tip[2]
        Ls = math.hypot(dx, dz)
        ang = math.degrees(math.atan2(-dz, dx))
        mid = ((tip[0] + nut[0]) / 2, (tip[1] + nut[1]) / 2, (tip[2] + nut[2]) / 2)
        cb.box_at(mid, (Ls, 0.18, 0.18), "string", rotation=[0, ang, 0], pivot=list(mid))
    cb.cube([x - 0.9, y - 1.2, z0 - 1.6], [1.8, 0.35, 1.8], "steel")                               # stirrup
    for s in (1, -1):
        cb.cube([x + s * 0.75 - 0.15, y - 1.2, z0 - 1.6], [0.3, 1.5, 0.3], "steel")
    cb.cube([x - 0.55, y - 0.4, z0 - 0.2], [1.1, 1.0, 0.4], "gold")                                 # nose cap
    return cb, (x, y, z_butt - 1.2), (x, y - 0.4, z0 + 4.2)


def legs(m):
    feet = []
    for side, thigh, shin in ((1, [5, 0, 0], [-7, 0, 0]), (-1, [-4, 0, 0], [-3, 0, 0])):
        pre = "left" if side > 0 else "right"
        hx = side * 1.6
        th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh)
        th.cube([hx - 1.0, 6.6, -1.1], [2.0, HIP - 6.6, 2.2], "hose")
        sh = m.bone(pre + "_shin", [hx, 6.8, 0], parent=pre + "_thigh", rotation=shin)
        sh.cube([hx - 0.95, 5.0, -1.05], [1.9, 2.0, 2.1], "hose")
        sh.cube([hx - 1.15, 0.4, -1.25], [2.3, 5.2, 2.5], "boot")                                   # knee boots
        sh.cube([hx - 1.25, 4.9, -1.35], [2.5, 0.7, 2.7], "leather_dark")                           # the cuff
        sh.cube([hx - 1.1, 0.4, -2.7], [2.2, 1.4, 1.6], "boot")                                     # the toe
        sh.cube([hx - 1.2, 0.0, -2.8], [2.4, 0.45, 4.1], "leather_dark")                            # the sole
        feet.append(pre + "_shin")
    return feet


def levy():
    m = Model("Crossbow Levy (v2)", "sunforged", "skirmisher (T1, through the Paladin pipeline)", PALETTE, EMISSIVE)
    body = torso(m)
    tabard(m)
    pavise(body)
    head(m)
    feet = legs(m)
    m.bone("xbow_arms", [0, SHOULDER_Y, 0], parent="body")
    # the crossbow carried across the waist, pointing ahead and a little to his left
    cb, grip_r, grip_l = crossbow(m, -1.0, 15.2, 2.2)
    m.bones["crossbow"].rotation = [0, 12, 0]
    glove(cb, grip_r)
    glove(cb, grip_l)
    ra, rf, rw = arm(m, -1, "xbow_arms")
    la, lf, lw = arm(m, 1, "xbow_arms")
    e1 = m.solve_arm(ra, rf, rw, m.fk("crossbow", (grip_r[0] - 0.3, grip_r[1] + 0.9, grip_r[2] + 0.4)), elbow_out=1)
    e2 = m.solve_arm(la, lf, lw, m.fk("crossbow", (grip_l[0] + 0.3, grip_l[1] + 0.9, grip_l[2] + 0.4)), elbow_out=1)
    m.ground(feet)
    m.pose = {"head": [-3, 0, 0]}
    return m, max(e1, e2)


MODELS = {"sunforged_crossbow_levy_v2": levy}


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
