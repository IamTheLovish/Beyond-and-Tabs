#!/usr/bin/env python3
"""The Sunforged Field Medic (witch_unit in the game) - a T1 support body through the Paladin's pipeline
(claude/paladin-radiant.md): concept -> tools/dsl_to_game.py -> the game.

Silhouette (claude/sunforged-t1-silhouettes.md, the Oct 14 lineup verdict "the Medic doesn't read - one oversized prop
each"): a slim robed healer, hood up, carrying a staff half again her height crowned with a SUN DISC that floats in a
gilded ring and glows - "the glowing one", a bright dot on a pole from the RTS camera. Human dial 2: no face (a hood
with a dark opening and two calm lights), slightly long proportions; the floating disc is her one impossible thing.

Lessons carried over from the Paladin: few big forms with chamfered vertical edges (octagon()), not slices; folds as
a handful of long dark strips; team colour on big areas the RTS camera sees (the shoulder mantle and the scapular
front and back); gold on few places (hem, cuffs, circlet, clasp, the staff); light on the one prop that matters (the
disc) plus small sparks (vials) that say "potions".

Rig for the game: the right arm and the staff hang from one bone on the right shoulder ("staff_arm"), the staff's
pivot is the grip (turning the staff alone = the wrist), the disc is its own bone ("sun_disc": it spins and bobs);
the robe hangs in four panels from the hips (they sway and part for the legs).

    python3 tools/gen_medic.py && python3 tools/game_preview.py design/models/sunforged_field_medic_v2.json ...
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, ring, OUT  # noqa: E402
from gen_paladin import octagon  # noqa: E402

PALETTE = {
    "robe": "#e9e2d2", "robe_dark": "#b7ad98", "hood_dark": "#231f1b",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "gold": "#d4a63c", "gold_dark": "#a87f24",
    "leather": "#6b4a30", "leather_dark": "#4a3220", "cord": "#c9a46a", "staff_wood": "#7a5536", "boot": "#5b3f2a",
    "sun": "#ffd96a", "glow": "#fff1b0", "glow_soft": "#f2d89c",
    "potion_red": "#ff5a5a", "potion_gold": "#ffcf4a", "potion_blue": "#6ad2ff",
}
EMISSIVE = ["sun", "glow", "glow_soft", "potion_red", "potion_gold", "potion_blue"]
FRONT = 0.55

HIP = 13.0
SHOULDER_X, SHOULDER_Y = 4.2, 22.6
UPPER, FORE = 5.0, 4.6
HEM = 2.4


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    # a few forms: waist, bodice, chest (octagons), a robe core under the panels from the hips down
    for (y0, y1, w, d, ch) in ((12.6, 16.2, 5.4, 3.8, 0.5), (16.0, 20.2, 6.2, 4.2, 0.6), (20.0, SHOULDER_Y + 0.8, 7.0, 4.4, 0.6)):
        octagon(b, y0, y1, w, d, ch, "robe", zf=-d * FRONT)
    octagon(b, HEM + 2.0, HIP + 0.6, 5.4, 3.6, 0.6, "robe_dark", zf=-3.6 * FRONT)
    # the cord belt with a knot at the front and two hanging ends
    octagon(b, 15.5, 16.3, 5.9, 4.3, 0.55, "cord", zf=-4.3 * FRONT)
    b.cube([0.6, 14.9, -2.75], [1.2, 1.4, 0.6], "cord")
    for k, (dx, L) in enumerate(((0.8, 3.6), (1.5, 2.8))):
        b.cube([dx, 15.0 - L, -2.65], [0.45, L, 0.45], "cord")
        b.cube([dx - 0.1, 15.0 - L - 0.5, -2.75], [0.65, 0.55, 0.65], "gold_dark")
    # the scapular on the bodice, team colour, with the sun of the order on the breast
    zc = -4.4 * FRONT
    b.cube([-1.5, 16.3, -4.2 * FRONT - 0.3], [3.0, 4.0, 0.4], "tabard")
    b.cube([-1.5, 20.0, zc - 0.3], [3.0, SHOULDER_Y - 20.0 + 0.6, 0.4], "tabard")
    b.box_at((0, 19.0, -4.2 * FRONT - 0.55), (1.5, 1.5, 0.3), "gold", rotation=[0, 0, 45], pivot=[0, 19.0, -4.2 * FRONT - 0.55])
    b.box_at((0, 19.0, -4.2 * FRONT - 0.7), (0.7, 0.7, 0.2), "sun", rotation=[0, 0, 45], pivot=[0, 19.0, -4.2 * FRONT - 0.7])
    b.cube([-1.5, 16.3, 4.2 * (1 - FRONT) - 0.1], [3.0, SHOULDER_Y - 16.3 + 0.6, 0.4], "tabard")         # back
    # three vials on the belt, front right: potions are what she throws
    for k, col in enumerate(("potion_red", "potion_gold", "potion_blue")):
        x = -1.3 - k * 0.9
        b.cube([x - 0.3, 14.0, -2.75], [0.6, 1.05, 0.6], col)
        b.cube([x - 0.2, 15.05, -2.7], [0.4, 0.35, 0.4], "leather_dark")
    return b


def mantle(body):
    """A short shoulder mantle in team colour - the biggest colour the RTS camera sees - over the shoulders and the top
    of the arms, a gold sun clasp at the throat, a point at the back."""
    y0, y1 = 20.8, SHOULDER_Y + 1.5
    octagon(body, y0, y1, 9.4, 5.6, 1.1, "tabard", zf=-5.6 * FRONT)
    octagon(body, y0 - 0.5, y0 + 0.4, 9.8, 5.9, 1.2, "tabard_dark", zf=-5.9 * FRONT)                    # its hem
    body.cube([-3.8, y1 - 0.2, -2.4], [7.6, 0.7, 4.6], "tabard")                                        # over the shoulders
    for s in (1, -1):                                                                                    # falls over the arms
        x0 = s * 4.6 - (0 if s > 0 else 1.2)
        body.cube([x0, y0 - 1.4, -2.6], [1.2, y1 - y0 + 1.2, 5.0], "tabard", rotation=[0, 0, s * 10],
                  pivot=[s * 4.6, y1, 0])
    body.cube([-1.2, y0 - 2.6, 5.6 * (1 - FRONT) - 0.9], [2.4, 2.8, 0.7], "tabard", rotation=[0, 0, 45],
              pivot=[0, y0 - 1.2, 5.6 * (1 - FRONT) - 0.55])                                             # point at the back
    cz = -5.6 * FRONT - 0.35
    body.box_at((0, y1 - 1.4, cz), (1.7, 1.7, 0.5), "gold", rotation=[0, 0, 45], pivot=[0, y1 - 1.4, cz])   # the clasp
    body.box_at((0, y1 - 1.4, cz - 0.2), (0.8, 0.8, 0.3), "sun", rotation=[0, 0, 45], pivot=[0, y1 - 1.4, cz - 0.2])


def robe_panels(m):
    """The skirt in four panels hung from the hips, each flared a little outward, with long folds; the front and
    back carry the scapular down to a gold-edged hem."""
    top = HIP + 0.8
    L = top - HEM
    panels = (("robe_front", [0, top, -1.9], [7, 0, 0], "front"), ("robe_back", [0, top, 1.9], [-7, 0, 0], "back"),
              ("robe_left", [2.6, top, 0], [0, 0, 9], "left"), ("robe_right", [-2.6, top, 0], [0, 0, -9], "right"))
    for name, piv, rot, side in panels:
        p = m.bone(name, piv, parent="body", rotation=rot)
        x, y, z = piv
        if side in ("front", "back"):
            s = -1 if side == "front" else 1
            w = 6.6
            zf = z - 0.4 if s < 0 else z - 0.4
            p.cube([-w / 2, HEM, zf], [w, L, 0.8], "robe")
            p.cube([-1.5, HEM + 1.0, zf + s * 0.3 - (0.2 if s < 0 else -0.2)], [3.0, L - 1.0, 0.4], "tabard")
            for fx in (-2.4, 2.1):                                                                       # folds
                p.cube([fx, HEM + 0.3, zf + (-0.1 if s < 0 else 0.5)], [0.35, L - 2.5, 0.4], "robe_dark")
            p.cube([-w / 2 - 0.05, HEM - 0.05, zf - 0.05], [w + 0.1, 0.55, 0.9], "gold")                 # hem
            if side == "front":                                                                        # embroidered sun
                ez = zf + s * 0.3 - 0.2 - 0.15
                p.box_at((0, HEM + 2.4, ez), (1.3, 1.3, 0.2), "gold", rotation=[0, 0, 45], pivot=[0, HEM + 2.4, ez])
                for k in range(4):
                    a = k * 90
                    rx, ry = 1.25 * math.cos(math.radians(a)), 1.25 * math.sin(math.radians(a))
                    p.box_at((rx, HEM + 2.4 + ry, ez), (0.9, 0.22, 0.15), "gold", rotation=[0, 0, a],
                             pivot=[rx, HEM + 2.4 + ry, ez])
        else:
            s = 1 if side == "left" else -1
            d = 4.2
            x0 = x - 0.4
            p.cube([x0, HEM, -d / 2], [0.8, L, d], "robe")
            p.cube([x0 + s * 0.3, HEM + 0.3, -0.25], [0.4, L - 2.5, 0.5], "robe_dark")                  # a fold
            p.cube([x0 - 0.05, HEM - 0.05, -d / 2 - 0.05], [0.9, 0.55, d + 0.1], "gold")


def satchel(body):
    """A leather satchel on the left hip with a sun clasp, its strap across the chest from the right shoulder."""
    body.cube([2.7, 11.6, -1.4], [1.9, 3.4, 3.0], "leather")
    body.cube([2.6, 13.7, -1.5], [2.1, 1.4, 3.2], "leather_dark")                                     # flap
    body.box_at((4.75, 13.4, 0.1), (0.3, 0.9, 0.9), "gold", rotation=[45, 0, 0], pivot=[4.75, 13.4, 0.1])
    a = math.degrees(math.atan2(15.2 - 22.2, 3.4 - (-3.0)))
    L = math.hypot(15.2 - 22.2, 3.4 - (-3.0))
    mx, my = (3.4 - 3.0) / 2, (15.2 + 22.2) / 2
    body.box_at((mx, my, -2.72), (L, 0.75, 0.3), "leather_dark", rotation=[0, 0, a], pivot=[mx, my, -2.72])


def hood(m):
    hy = SHOULDER_Y + 1.0
    h = m.bone("head", [0, hy, 0], parent="body")
    octagon(h, hy + 0.2, hy + 5.8, 5.6, 5.8, 0.9, "robe", zf=-3.2)                                        # the hood
    octagon(h, hy + 5.6, hy + 6.8, 4.0, 4.4, 0.6, "robe", zf=-2.4)                                        # its crown
    h.cube([-0.9, hy - 0.4, 2.3], [1.8, 5.6, 1.3], "robe", rotation=[-8, 0, 0], pivot=[0, hy + 5.2, 2.6])    # the liripipe
    # the face: a dark opening framed by the hood's rim, two calm lights in it
    h.cube([-1.9, hy + 0.8, -3.32], [3.8, 4.0, 0.3], "hood_dark")
    for s in (1, -1):
        h.cube([s * 2.15 - 0.35, hy + 0.4, -3.6], [0.7, 4.8, 0.6], "robe")                              # rim sides
        h.cube([s * 0.85 - 0.45, hy + 2.9, -3.45], [0.9, 0.5, 0.2], "glow_soft")                        # the eyes
    h.cube([-2.5, hy + 4.7, -3.65], [5.0, 0.8, 0.7], "robe")                                            # rim top
    for s in (1, -1):                                                                                   # its lining
        h.cube([s * 1.75 - 0.15, hy + 0.8, -3.42], [0.3, 4.0, 0.15], "robe_dark")
    h.cube([-1.9, hy + 4.55, -3.42], [3.8, 0.25, 0.15], "robe_dark")
    h.cube([-2.55, hy + 5.0, -3.75], [5.1, 0.35, 0.3], "gold")                                          # the circlet
    h.box_at((0, hy + 5.5, -3.85), (1.2, 1.2, 0.3), "gold", rotation=[0, 0, 45], pivot=[0, hy + 5.5, -3.85])
    h.box_at((0, hy + 5.5, -4.0), (0.6, 0.6, 0.2), "sun", rotation=[0, 0, 45], pivot=[0, hy + 5.5, -4.0])
    return h


def sleeve_arm(m, side, parent, hand=True):
    """A robed arm: a sleeve that widens to a bell cuff with a gold edge; a leather glove below (or none, when the
    hand is on the staff)."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    up.cube([sx - 1.2, elbow_y, -1.3], [2.4, UPPER + 0.4, 2.6], "robe")
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    fo.cube([sx - 1.25, wrist_y + 1.8, -1.35], [2.5, FORE - 1.6, 2.7], "robe")
    fo.cube([sx - 1.7, wrist_y, -1.75], [3.4, 2.0, 3.5], "robe")                                         # the bell
    fo.cube([sx - 1.75, wrist_y - 0.05, -1.8], [3.5, 0.45, 3.6], "gold")                               # its edge
    fo.cube([sx - 1.4, wrist_y + 0.3, -1.45], [2.8, 1.6, 2.9], "robe_dark")                            # inside
    if hand:
        fo.cube([sx - 0.9, wrist_y - 2.0, -1.0], [1.8, 2.1, 2.0], "leather")                           # the glove
        fo.cube([sx - 0.95, wrist_y - 0.5, -1.05], [1.9, 0.5, 2.1], "leather_dark")                     # its cuff
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.2, 0)


def glove_grip(bone, centre, side):
    """A leather glove closed round a vertical shaft at `centre`: palm, fingers over the front, a thumb on top."""
    x, y, z = centre
    bone.cube([x - 1.0, y - 1.0, z - 1.0], [2.0, 2.0, 2.0], "leather")
    bone.cube([x - 1.1, y - 0.95, z - 1.35], [2.2, 1.9, 0.5], "leather")                               # the fingers
    for k in (-0.45, 0.2):
        bone.cube([x - 1.15, y + k, z - 1.4], [2.3, 0.08, 0.55], "leather_dark")
    bone.cube([x - 0.55, y + 0.9, z - 1.0], [1.1, 0.5, 1.3], "leather", rotation=[0, 0, -side * 10],
              pivot=[x, y + 1.1, z - 0.4])                                                               # the thumb
    bone.cube([x - 1.05, y + 0.95, z - 0.95], [2.1, 0.45, 1.9], "leather_dark")                        # the cuff


def staff(m, gx, gz, grip_y):
    """THE prop: a staff half again her height - wood, gold bands and ferrule, a gilded neck rising into a ring that
    holds the floating sun disc (its own bone) with rays round it."""
    st = m.bone("staff", [gx, grip_y, gz], parent="staff_arm")
    top = 38.0
    st.cube([gx - 0.45, 0.0, gz - 0.45], [0.9, top, 0.9], "staff_wood")
    st.cube([gx - 0.55, -0.2, gz - 0.55], [1.1, 1.6, 1.1], "gold")                                      # ferrule
    for yb in (9.0, grip_y + 2.6, 30.0):
        st.cube([gx - 0.58, yb, gz - 0.58], [1.16, 0.6, 1.16], "gold")
    st.cube([gx - 0.7, top - 1.0, gz - 0.7], [1.4, 2.2, 1.4], "gold")                                  # the neck
    st.cube([gx - 0.9, top + 0.8, gz - 0.9], [1.8, 0.7, 1.8], "gold_dark")
    c = (gx, top + 5.6, gz)
    ring(st, c, 3.9, 14, 0.75, 0.9, "gold")                                                            # the ring
    for k in range(12):                                                                               # rays
        a = k * math.pi / 6 + math.pi / 12
        L = 1.9 if k % 2 == 0 else 1.0
        r = 4.5 + L / 2
        cx, cy = c[0] + r * math.cos(a), c[1] + r * math.sin(a)
        st.box_at((cx, cy, c[2]), (L, 0.5, 0.5), "sun", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, c[2]])
    # the cradle: two prongs from the neck up into the ring
    for s in (1, -1):
        st.cube([gx + s * 1.0 - 0.3, top + 1.2, gz - 0.35], [0.6, 2.2, 0.7], "gold", rotation=[0, 0, -s * 24],
                pivot=[gx, top + 1.2, gz])
    # two ribbons tied under the neck, team colour: the one prop carries the side too, and they flutter
    rb = m.bone("staff_ribbon", [gx, top - 1.4, gz], parent="staff")
    rb.cube([gx - 0.75, top - 1.9, gz - 0.75], [1.5, 0.7, 1.5], "tabard_dark")                         # the knot
    for k, (dx, L, tilt) in enumerate(((0.25, 7.0, 10), (-0.55, 5.6, 22))):
        rb.cube([gx + dx - 0.4, top - 1.9 - L, gz + 0.45], [0.8, L, 0.2], "tabard", rotation=[-tilt, 0, 6 - k * 14],
                pivot=[gx + dx, top - 1.9, gz + 0.5])
        rb.cube([gx + dx - 0.4, top - 1.9 - L - 0.6, gz + 0.45], [0.8, 0.7, 0.2], "tabard_dark",
                rotation=[-tilt, 0, 6 - k * 14], pivot=[gx + dx, top - 1.9, gz + 0.5])
    disc = m.bone("sun_disc", list(c), parent="staff")
    rows = (1.4, 2.6, 3.2, 3.4, 3.4, 3.2, 2.6, 1.4)
    rh = 0.75
    y0 = c[1] - len(rows) * rh / 2
    for i, w in enumerate(rows):
        disc.cube([c[0] - w / 2, y0 + i * rh, c[2] - 0.35], [w, rh + 0.02, 0.7], "sun")
    disc.box_at(c, (1.4, 1.4, 1.6), "glow")                                                            # the bright core
    st.cube([gx - 0.4, top + 5.6 + 3.9 + 0.2, gz - 0.4], [0.8, 0.9, 0.8], "gold")                     # the finial
    return st


def medic():
    m = Model("Field Medic - Dawn Sister (v2)", "sunforged", "support (T1, through the Paladin pipeline)", PALETTE,
              EMISSIVE)
    body = torso(m)
    mantle(body)
    robe_panels(m)
    satchel(body)
    hood(m)
    # legs (mostly under the robe): soft boots, a calm stance
    feet = []
    for side, thigh, shin in ((1, [4, 0, 0], [-6, 0, 0]), (-1, [-3, 0, 0], [-4, 0, 0])):
        pre = "left" if side > 0 else "right"
        hx = side * 1.5
        th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh)
        th.cube([hx - 1.0, 7.0, -1.1], [2.0, HIP - 7.0, 2.2], "robe_dark")
        sh = m.bone(pre + "_shin", [hx, 7.0, 0], parent=pre + "_thigh", rotation=shin)
        sh.cube([hx - 0.95, 3.4, -1.05], [1.9, 3.8, 2.1], "robe_dark")
        sh.cube([hx - 1.15, 0.4, -1.25], [2.3, 3.4, 2.5], "boot")                                       # boot shaft
        sh.cube([hx - 1.2, 3.4, -1.3], [2.4, 0.5, 2.6], "leather_dark")                                 # its cuff
        sh.cube([hx - 1.1, 0.4, -2.7], [2.2, 1.4, 1.6], "boot")                                         # the toe
        sh.cube([hx - 1.2, 0.0, -2.8], [2.4, 0.45, 4.1], "leather_dark")                                # the sole
        feet.append(pre + "_shin")
    # the staff and the right arm on one rig at the right shoulder; the left arm holds a vial by the satchel
    m.bone("staff_arm", [-SHOULDER_X, SHOULDER_Y, 0], parent="body")
    ra, rf, rw = sleeve_arm(m, -1, "staff_arm", hand=False)
    la, lf, lw = sleeve_arm(m, 1, "body", hand=True)
    # the grip at the hip, a little forward and out: held lower the arm folded up with the elbow at the ear
    gx, gz, grip_y = -6.4, -2.2, 15.4
    st = staff(m, gx, gz, grip_y)
    glove_grip(st, (gx, grip_y, gz), -1)
    e1 = m.solve_arm(ra, rf, rw, (gx + 0.3, grip_y + 1.5, gz + 0.6), elbow_out=1)
    # the left hand forward a little, a vial in it
    m.bones[la].rotation = [14, 0, -6]
    m.bones[lf].rotation = [-28, 0, 0]
    lfore = m.bones[lf]
    wx, wy = SHOULDER_X, SHOULDER_Y - UPPER - FORE
    lfore.cube([wx - 0.35, wy - 3.2, -1.6], [0.7, 1.3, 0.7], "potion_red")
    lfore.cube([wx - 0.25, wy - 1.95, -1.5], [0.5, 0.4, 0.5], "leather_dark")
    m.ground(feet)
    m.pose = {"head": [-5, 0, 0]}
    return m, e1


MODELS = {"sunforged_field_medic_v2": medic}


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
