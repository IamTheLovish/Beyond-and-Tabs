#!/usr/bin/env python3
"""The Sunforged Squire Runner (militia_unit in the game) - the T1 raider body through the Paladin pipeline
(claude/paladin-radiant.md, claude/sunforged-t1-silhouettes.md).

Silhouette: the SMALLEST of the line - short, lean, long-legged, leaning into the run - and the one thing that names
him: a long team-colour pennant on a pole rising from his back, streaming behind him (from the RTS camera a coloured
streak; the faster he runs the flatter it flies). A team-colour brigandine (the big colour from above), padded
sleeves, a round nasal helm with a small sun knob (a dome, not the Levy's brim or the Halberdier's sallet), knee
boots with steel knee cops.

Not clunky (lovish, Oct 14 22:53, on the first pass): every limb is slim and octagonal in section (two crossed boxes,
lesson 5) and narrows toward the wrist and the ankle; the shoulder plates are small and follow the slope of the
shoulder; the waist is narrower than the chest; the head is small on a visible neck; the stance has bent knees.

Weapons - the squire swaps between a sword and a bow (MilitiaUnit.swapWeapons, once Militia Bows is researched):
  - "sword": an arming sword in the right hand (gold cross, sun pommel); "sheathed": the hilt in the scabbard on the
    left hip, shown while he holds the bow (the scabbard itself is always there);
  - "bow": a short recurve in the left hand (string toward him on "bow_string", tips flicking forward); "arrow": the
    nocked arrow and the drawn string, shown while he draws (a body child laid where the AIM pose puts them);
  - the Militia Bows research (UpgradeVisuals SHAPE) shows the "bows" parts: a bow slung across the back and a
    quiver at the right hip - the bow on the back ("back_bow") hides while the bow is in his hand;
  - "sword_rune" / "bow_rune": light in the fuller / along the bow's belly when the weapon is enchanted (the
    Enchanter's Martial Enchantment).
The aim is side-on: the renderer turns the whole body a quarter turn so his LEFT side faces the target (model +x),
and the AIM keys below (solved here, printed for SquireRunnerUnitModel) raise the bow arm along +x, draw the right
hand to the jaw and turn the head to the target.

    python3 tools/gen_squire.py && python3 tools/game_preview.py design/models/sunforged_squire_runner_v2.json ...
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT, rot_mat, apply, foot  # noqa: E402

PALETTE = {
    "cloth": "#e4dccb", "cloth_dark": "#b3a891", "visor": "#2e2a26",
    "steel": "#a3aab6", "steel_dark": "#7c828d", "gold": "#d4a63c", "gold_dark": "#a87f24",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "leather": "#6b4a30", "leather_dark": "#4a3220", "wood": "#7a5536", "hose": "#4b4440", "boot": "#5b3f2a",
    "string": "#efe6d2", "feather": "#f2eee6", "sun": "#ffd96a",
}
EMISSIVE = ["sun"]
FRONT = 0.56

HIP = 13.0
SHOULDER_X, SHOULDER_Y = 3.4, 20.6
UPPER, FORE = 4.4, 4.0
LEAN = -9                     # the rest lean (DSL x on "body": negative tips him forward)
HEAD_Y = SHOULDER_Y + 1.5     # the head's pivot: a neck shows under the helm

# the aim, side-on (model +x = the target): body straightened a little, head turned to the target, legs apart
AIM_FIXED = {"body": [6, 0, 0], "head": [-6, -84, 0], "left_thigh": [0, 0, 9], "right_thigh": [0, 0, -9]}
AIM = {}                      # filled by solve_aim(): AIM_FIXED + the solved arm and bow keys


# ------------------------------------------------------------------ shapes ------------------------------------------
def oct_at(b, cx, cz, y0, y1, w, d, ch, color, **kw):
    """A box centred on (cx, cz) with its four vertical edges chamfered by ch: two crossed boxes."""
    b.cube([cx - w / 2, y0, cz - d / 2 + ch], [w, y1 - y0, d - 2 * ch], color, **kw)
    b.cube([cx - w / 2 + ch, y0, cz - d / 2], [w - 2 * ch, y1 - y0, d], color, **kw)


def torso(m):
    b = m.bone("body", [0, HIP, 0], rotation=[LEAN, 0, 0])
    oct_at(b, 0, -0.1, HIP - 0.6, HIP + 1.2, 4.4, 3.0, 0.6, "hose")                                  # hips
    oct_at(b, 0, -0.15, 10.2, 13.8, 5.2, 3.6, 0.8, "cloth")                                          # doublet skirt
    b.cube([-2.55, 11.4, -1.95], [5.1, 0.16, 0.3], "cloth_dark")                                    # its hem quilt
    # the brigandine: team cloth over small plates; a narrow waist, the chest fullest, the shoulders sloping in
    oct_at(b, 0, -0.1, 13.4, 16.2, 4.6, 3.3, 0.7, "tabard")
    oct_at(b, 0, -0.15, 16.0, 19.6, 5.8, 3.8, 0.9, "tabard")
    oct_at(b, 0, -0.1, 19.4, 20.9, 4.9, 3.4, 0.9, "tabard")
    # its gilt rivets: two rows down the chest, a row across, two on the back
    zf = -0.15 - 3.8 / 2 - 0.1
    for y in (16.6, 17.6, 18.6):
        for x in (-1.6, 1.6):
            b.cube([x - 0.14, y, zf], [0.28, 0.28, 0.14], "gold")
    for x in (-0.8, 0.0, 0.8):
        b.cube([x - 0.14, 19.0, zf], [0.28, 0.28, 0.14], "gold")
    for y in (16.8, 18.2):
        for x in (-1.5, 1.5):
            b.cube([x - 0.14, y, 1.8], [0.28, 0.28, 0.14], "gold")
    b.cube([-2.0, 15.9, zf + 0.04], [4.0, 0.18, 0.18], "tabard_dark")                              # a plate seam
    # the belt, a gold buckle with the sun
    oct_at(b, 0, -0.1, 13.4, 14.3, 4.9, 3.5, 0.7, "leather")
    b.cube([-0.6, 13.25, -2.25], [1.2, 1.2, 0.4], "gold")
    b.cube([-0.27, 13.55, -2.38], [0.54, 0.54, 0.2], "sun")
    # the neck and the padded collar of the arming doublet
    oct_at(b, 0, 0.0, 20.6, 21.9, 2.6, 2.6, 0.6, "cloth")
    b.cube([-1.4, 20.7, -1.45], [2.8, 0.18, 0.2], "cloth_dark")
    return b


def scabbard(m):
    """The scabbard on the left hip (always) and the sword's hilt in it ("sheathed", shown while he holds the bow)."""
    b = m.bones["body"]
    sw = (2.8, 14.0, 0.3)
    rot = [-24, 0, 8]
    b.cube([sw[0] - 0.4, sw[1] - 7.6, sw[2] - 0.6], [0.8, 7.6, 1.2], "leather_dark", rotation=rot, pivot=list(sw))
    b.cube([sw[0] - 0.45, sw[1] - 8.1, sw[2] - 0.65], [0.9, 0.6, 1.3], "gold", rotation=rot, pivot=list(sw))    # chape
    b.cube([sw[0] - 0.45, sw[1] - 0.8, sw[2] - 0.65], [0.9, 0.8, 1.3], "gold_dark", rotation=rot, pivot=list(sw))  # locket
    sh = m.bone("sheathed", list(sw), parent="body", rotation=rot)
    sh.cube([sw[0] - 0.25, sw[1], sw[2] - 0.25], [0.5, 1.9, 0.5], "leather")                         # grip
    sh.cube([sw[0] - 1.1, sw[1] - 0.05, sw[2] - 0.25], [2.2, 0.4, 0.5], "gold")                      # cross
    sh.box_at((sw[0], sw[1] + 2.2, sw[2]), (0.8, 0.8, 0.8), "gold", rotation=[0, 0, 45], pivot=[sw[0], sw[1] + 2.2, sw[2]])


def pennant(m):
    """THE signature: a pole in a back socket, a gold finial with the sun, and a long tapering swallow-tailed pennant
    in three segments (chained bones, so the game can make it flutter), streaming back from the pole's top."""
    b = m.bones["body"]
    # over the right shoulder blade (Oct 15 review: on the spine at (-0.5, 2.3) the pole, leaning with the body,
    # ran through the back of the helm - here it clears the helm by >= 0.32 px at any head turn); socket and strap
    # ring reach back to the brigandine so the pole is held, not floating
    px, pz = -2.0, 2.9
    back = 1.6                                               # the brigandine's back surface
    b.cube([px - 0.5, 12.6, back], [1.0, 1.6, pz + 0.5 - back], "leather_dark")                     # the socket
    b.cube([px - 0.55, 19.6, back], [1.1, 0.6, pz + 0.55 - back], "leather_dark")                   # upper strap ring
    b.cube([px - 0.28, 12.8, pz - 0.28], [0.56, 22.0, 0.56], "wood")                                 # the pole
    top = 34.8
    b.cube([px - 0.42, top, pz - 0.42], [0.84, 0.6, 0.84], "gold_dark")
    b.box_at((px, top + 1.4, pz), (1.5, 1.5, 0.35), "gold", rotation=[0, 0, 45], pivot=[px, top + 1.4, pz])
    b.box_at((px, top + 1.4, pz), (0.85, 0.85, 0.45), "sun", rotation=[0, 0, 45], pivot=[px, top + 1.4, pz])
    # the pennant: hoisted at the pole between y 31 and 34.6, three segments back along +z, tapering, the last cut
    # into a swallowtail; each segment's pivot is where it meets the one before
    segs = ((5.2, 3.6, 3.0), (5.0, 3.0, 2.3), (5.6, 2.3, 1.6))      # (length, height at start, height at end)
    y_top = 34.6
    z0 = pz + 0.28
    parent = "body"
    for i, (L, h0, h1) in enumerate(segs):
        name = "pennant_%d" % (i + 1)
        p = m.bone(name, [px, y_top, z0], parent=parent, rotation=[6 if i == 0 else 4, 0, 0])
        hm = (h0 + h1) / 2
        p.cube([px - 0.2, y_top - h0, z0], [0.4, h0, L / 2], "tabard")
        if i < len(segs) - 1:
            p.cube([px - 0.2, y_top - hm, z0 + L / 2], [0.4, hm, L / 2], "tabard")
        else:
            # the swallowtail: an upper and a lower tail with the notch between
            p.cube([px - 0.2, y_top - hm * 0.42, z0 + L / 2], [0.4, hm * 0.42, L / 2 + 0.6], "tabard")
            p.cube([px - 0.2, y_top - hm, z0 + L / 2], [0.4, hm * 0.42, L / 2 + 0.6], "tabard")
        # gold hems top and bottom (the art director's round: a team colour near the grass's value vanished at far -
        # the hem keeps the pennant's outline whatever the owner's colour)
        p.cube([px - 0.25, y_top - 0.35, z0], [0.5, 0.35, L], "gold")                                # the top hem
        if i < len(segs) - 1:
            p.cube([px - 0.25, y_top - h0, z0], [0.5, 0.35, L / 2], "gold")                           # the lower hem
            p.cube([px - 0.25, y_top - hm, z0 + L / 2], [0.5, 0.35, L / 2], "gold")
        else:
            p.cube([px - 0.25, y_top - h0, z0], [0.5, 0.35, L / 2], "gold")
            p.cube([px - 0.25, y_top - hm, z0 + L / 2], [0.5, 0.35, L / 2 + 0.6], "gold")
        if i == 0:
            sc = (px, y_top - h0 / 2, z0 + 1.9)                                                      # the sun, both faces
            for s in (1, -1):
                p.box_at((px + s * 0.25, sc[1], sc[2]), (0.15, 1.8, 1.8), "gold", rotation=[45, 0, 0], pivot=[px, sc[1], sc[2]])
                p.box_at((px + s * 0.33, sc[1], sc[2]), (0.12, 0.9, 0.9), "sun", rotation=[45, 0, 0], pivot=[px, sc[1], sc[2]])
        parent = name
        z0 += L
    b.cube([px - 0.33, 30.8, pz - 0.33], [0.66, 0.35, 0.66], "gold_dark")                           # the lacing


def head(m):
    hy = HEAD_Y
    h = m.bone("head", [0, hy, 0], parent="body", rotation=[-LEAN + 2, 0, 0])
    # the arming cap under the helm and the face in shadow; the nasal splits it into two eyes
    oct_at(h, 0, -0.1, hy - 0.4, hy + 3.4, 3.6, 3.7, 0.7, "cloth")
    h.cube([-1.3, hy + 0.5, -2.05], [2.6, 2.3, 0.2], "visor")
    # the helm: a rounded skull (two octagons and a cap), a brow band, the ridge, the sun knob on top
    oct_at(h, 0, -0.1, hy + 2.6, hy + 4.5, 4.2, 4.3, 1.0, "steel")
    oct_at(h, 0, -0.1, hy + 4.3, hy + 5.1, 3.1, 3.2, 0.8, "steel")
    h.cube([-0.9, hy + 4.95, -1.0], [1.8, 0.45, 1.8], "steel")
    oct_at(h, 0, -0.1, hy + 2.5, hy + 3.05, 4.4, 4.5, 1.05, "steel_dark")                          # brow band
    h.cube([-0.28, hy + 0.9, -2.38], [0.56, 2.3, 0.32], "steel")                                     # the nasal
    h.cube([-0.3, hy + 3.4, -2.3], [0.6, 1.4, 0.3], "steel_dark")                                    # the ridge
    h.box_at((0, hy + 5.65, -0.1), (0.9, 0.9, 0.9), "gold", rotation=[0, 45, 0], pivot=[0, hy + 5.65, -0.1])
    h.box_at((0, hy + 6.15, -0.1), (0.5, 0.5, 0.5), "sun", rotation=[0, 45, 0], pivot=[0, hy + 6.15, -0.1])
    return h


def arm(m, side, rest, fore_rest):
    """A slim arm: an octagonal padded sleeve narrowing to the elbow, a small spaulder following the shoulder's slope,
    a forearm in a leather bracer, a small fist."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent="body", rotation=rest)
    oct_at(up, sx, 0, elbow_y + 1.6, sy + 0.3, 1.85, 1.95, 0.5, "cloth")                             # upper sleeve
    oct_at(up, sx, 0, elbow_y - 0.2, elbow_y + 1.8, 1.6, 1.75, 0.45, "cloth")                        # toward the elbow
    up.cube([sx - 0.95, elbow_y + 1.6, -1.0], [1.9, 0.16, 2.0], "cloth_dark")                       # a seam
    # the spaulder: an octagonal cap tipped down the shoulder's slope, a rounding lame under it
    oct_at(up, sx + side * 0.2, 0, sy - 0.6, sy + 0.55, 2.4, 2.5, 0.7, "steel", rotation=[0, 0, side * 16],
           pivot=[sx, sy, 0])
    oct_at(up, sx + side * 0.3, 0, sy - 1.35, sy - 0.55, 2.1, 2.2, 0.6, "steel_dark", rotation=[0, 0, side * 16],
           pivot=[sx, sy, 0])
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm", rotation=fore_rest)
    oct_at(fo, sx, 0, elbow_y - 1.6, elbow_y + 0.3, 1.55, 1.7, 0.45, "cloth")
    oct_at(fo, sx, 0, wrist_y + 0.1, elbow_y - 1.4, 1.65, 1.75, 0.45, "leather")                    # the bracer
    fo.cube([sx - 0.83, elbow_y - 1.6, -0.88], [1.66, 0.22, 1.76], "leather_dark")                  # its cuff
    oct_at(fo, sx, -0.05, wrist_y - 1.35, wrist_y + 0.2, 1.4, 1.55, 0.35, "leather_dark")           # the fist (a
    # glove darker than the bracer: in one colour the hand vanished and the sword read as an arm-blade)
    return pre + "_arm", pre + "_fore", (sx, wrist_y - 0.6, 0.0)


def sword(m, fist):
    """An arming sword in the right fist, pointing ahead: steel blade with a fuller, gold cross, leather grip, the sun
    on the pommel. The blade runs along -z of its bone from the fist."""
    x, y, z = fist
    # -25 in the forearm's frame: the grip crosses the palm and the blade points ahead and a little down (at -72 the
    # blade ran 18 degrees off the forearm's axis with the pommel in the bracer - an arm-blade, Oct 15 review);
    # SquireRunnerUnitModel.KEY_SWORD is keyed against this rest (it was shifted by -47 with it)
    s = m.bone("sword", [x, y, z], parent="right_fore", rotation=[-25, 0, 0])
    s.cube([x - 0.27, y - 0.27, z - 1.1], [0.54, 0.54, 2.2], "leather")                             # the grip
    s.cube([x - 1.4, y - 0.27, z - 1.55], [2.8, 0.54, 0.45], "gold")                                # the cross
    s.cube([x - 0.45, y - 0.45, z + 1.05], [0.9, 0.9, 0.75], "gold")                                # pommel
    s.cube([x - 0.22, y - 0.22, z + 1.8], [0.44, 0.44, 0.05], "sun")                                # its sun
    L = 9.2
    zb = z - 1.55
    s.cube([x - 0.58, y - 0.17, zb - L + 1.6], [1.16, 0.34, L - 1.6], "steel")                      # the blade
    s.cube([x - 0.38, y - 0.17, zb - L + 0.6], [0.76, 0.34, 1.0], "steel")                          # the taper
    s.cube([x - 0.15, y - 0.15, zb - L], [0.3, 0.3, 0.6], "steel")                                  # the point
    s.cube([x - 0.12, y - 0.23, zb - L + 2.4], [0.24, 0.46, L - 3.0], "steel_dark")                 # the fuller
    r = m.bone("sword_rune", [x, y, z], parent="sword")
    r.cube([x - 0.13, y - 0.26, zb - L + 2.6], [0.26, 0.52, L - 3.4], "sun")
    return s


def bow(m, fist):
    """A short recurve in the left fist: limbs up and down from the grip, bending back toward him (string side -x),
    the tips flicking forward again; gold nocks; the string straight between the tips (its own bone, hidden while
    the drawn string shows). Returns the two nock points in the bow's rest frame."""
    x, y, z = fist
    # turned back 38 degrees in the forearm's frame: held (the game swings the forearm 16 + 22 forward) the bow stands
    # upright before the thigh - built along the forearm it ran up the arm into the chest (Oct 15 review)
    bw = m.bone("bow", [x, y, z], parent="left_fore", rotation=[-38, 0, 0])
    bw.cube([x - 0.4, y - 1.0, z - 0.4], [0.8, 2.0, 0.8], "leather")                                # the grip
    tips = []
    for s in (1, -1):
        # each limb in two pieces, the outer one bent further back
        bw.cube([x - 0.32, y + (0.9 if s > 0 else -3.6), z - 0.32], [0.64, 2.7, 0.64], "wood",
                rotation=[0, 0, s * 12], pivot=[x, y + s * 0.9, z])
        t0 = (x - math.sin(math.radians(12)) * 2.7, y + s * (0.9 + 2.7 * math.cos(math.radians(12))), z)
        bw.cube([t0[0] - 0.27, t0[1] + (0 if s > 0 else -2.4), z - 0.27], [0.54, 2.4, 0.54], "wood",
                rotation=[0, 0, s * 26], pivot=list(t0))
        tip = (t0[0] - math.sin(math.radians(26)) * 2.4, t0[1] + s * 2.4 * math.cos(math.radians(26)), z)
        bw.box_at(tip, (0.62, 0.62, 0.62), "gold")                                                  # the nock
        tips.append(tip)
    top, bot = tips
    st = m.bone("bow_string", [x, y, z], parent="bow")
    st.cube([top[0] - 0.19, bot[1], z - 0.09], [0.18, top[1] - bot[1], 0.18], "string")
    rr = m.bone("bow_rune", [x, y, z], parent="bow")
    for s in (1, -1):
        rr.cube([x + 0.26, y + (0.9 if s > 0 else -3.4), z - 0.15], [0.12, 2.5, 0.3], "sun",
                rotation=[0, 0, s * 12], pivot=[x, y + s * 0.9, z])
    return (top[0] - 0.1, top[1], z), (bot[0] - 0.1, bot[1], z)


def quiver_and_slung_bow(m):
    """The Militia Bows research: a quiver at the right hip (white fletchings showing) and a bow slung across the back
    (the "back_bow" bone - hidden while the bow is in his hand)."""
    b = m.bones["body"]
    qp = (-2.9, 13.6, 1.0)
    kw = dict(rotation=[12, 0, -10], pivot=list(qp), upgrade="bows")
    oct_at(b, qp[0], qp[2], 8.6, 13.6, 1.6, 1.5, 0.4, "leather", **kw)
    oct_at(b, qp[0], qp[2], 13.6, 14.0, 1.75, 1.65, 0.42, "leather_dark", **kw)
    for k, (dx, dz) in enumerate(((-0.35, -0.25), (0.3, -0.2), (-0.1, 0.3))):
        b.cube([qp[0] + dx - 0.11, 14.0, qp[2] + dz - 0.11], [0.22, 1.3 + 0.25 * k, 0.22], "wood", **kw)
        b.cube([qp[0] + dx - 0.26, 14.6 + 0.25 * k, qp[2] + dz - 0.05], [0.52, 0.9, 0.1], "feather", **kw)
    # the slung bow: from the left shoulder down to the right hip, across the back, string outward
    c = (0.4, 16.8, 2.35)
    bb = m.bone("back_bow", list(c), parent="body", rotation=[0, 0, 38], upgrade="bows")
    bb.cube([c[0] - 0.32, c[1] - 5.6, c[2] - 0.32], [0.64, 11.2, 0.64], "wood")
    bb.cube([c[0] - 0.42, c[1] - 1.0, c[2] - 0.42], [0.84, 2.0, 0.84], "leather")
    for s in (1, -1):
        bb.box_at((c[0], c[1] + s * 5.75, c[2]), (0.62, 0.62, 0.62), "gold")
    bb.cube([c[0] - 0.09, c[1] - 5.6, c[2] + 0.7], [0.18, 11.2, 0.18], "string")


def legs(m):
    """Long lean legs: an octagonal thigh in hose narrowing to the knee, a small knee cop, a calf in a slim knee boot,
    the knees a little bent (the weight on the front foot)."""
    feet = []
    for side, thigh, shin in ((1, [14, 0, 2], [-20, 0, 0]), (-1, [-4, 0, -2], [-12, 0, 0])):
        pre = "left" if side > 0 else "right"
        hx = side * 1.25
        knee = 7.0
        th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh)
        oct_at(th, hx, 0, knee + 2.6, HIP + 0.4, 1.95, 2.05, 0.5, "hose")
        oct_at(th, hx, -0.05, knee - 0.2, knee + 2.8, 1.7, 1.85, 0.45, "hose")
        sh = m.bone(pre + "_shin", [hx, knee, 0], parent=pre + "_thigh", rotation=shin)
        # the knee cop: a small steel plate with a rounding lame
        sh.cube([hx - 0.85, knee - 0.6, -1.25], [1.7, 1.5, 0.6], "steel")
        sh.cube([hx - 0.75, knee - 1.25, -1.15], [1.5, 0.7, 0.45], "steel_dark")
        oct_at(sh, hx, 0, knee - 2.2, knee + 0.2, 1.55, 1.7, 0.4, "hose")
        oct_at(sh, hx, 0, 1.1, knee - 2.0, 1.75, 1.95, 0.45, "boot")                                # the knee boot
        oct_at(sh, hx, 0, knee - 2.4, knee - 1.9, 1.95, 2.15, 0.5, "leather_dark")                  # its cuff
        # the foot: its own bone, level and turned out (gen_models.foot - lovish Oct 15: "the feet look odd")
        feet.append(foot(m, pre, hx, 0.0, 1.8, 4.3, 1.1, "boot", "leather_dark"))
    return feet


# ------------------------------------------------------------------ the aim (solved) --------------------------------
def world_rot(m, bone):
    """The rotation matrix of a bone in the model frame (its own rotation + pose, and every parent's)."""
    R = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
    b = m.bones[bone]
    chain = []
    while b is not None:
        chain.append(b)
        b = m.bones[b.parent] if b.parent else None
    for b in reversed(chain):
        rot = [b.rotation[a] + m.pose.get(b.name, [0, 0, 0])[a] for a in range(3)]
        Mb = rot_mat(*rot)
        R = tuple(tuple(sum(R[i][k] * Mb[k][j] for k in range(3)) for j in range(3)) for i in range(3))
    return R


def euler(R):
    """(x, y, z) degrees with rot_mat(x, y, z) == R (R = Rz Ry Rx)."""
    ry = math.degrees(math.asin(max(-1.0, min(1.0, -R[2][0]))))
    rx = math.degrees(math.atan2(R[2][1], R[2][2]))
    rz = math.degrees(math.atan2(R[1][0], R[0][0]))
    return [rx, ry, rz]


def transpose(R):
    return tuple(tuple(R[j][i] for j in range(3)) for i in range(3))


def solve(m, upper, fore, hand_rest, target, elbow_cost):
    """Upper arm (x, y, z) + elbow bend (x, flexion only) so the fist lands on target; elbow_cost(elbow) breaks ties
    toward a natural elbow. Coarse grid, then fine."""
    U, F = m.bones[upper], m.bones[fore]

    def cost(ux, uy, uz, fx):
        U.rotation, F.rotation = [ux, uy, uz], [fx, 0, 0]
        p = m.fk(fore, hand_rest)
        return sum((p[a] - target[a]) ** 2 for a in range(3)) + elbow_cost(m.fk(upper, F.pivot))
    best, bd = None, 1e9
    for ux in range(-60, 181, 12):
        for uy in range(-96, 97, 12):
            for uz in range(-96, 97, 12):
                for fx in range(0, 151, 10):
                    d = cost(ux, uy, uz, fx)
                    if d < bd:
                        best, bd = (ux, uy, uz, fx), d
    for step in (4, 1):
        ux, uy, uz, fx = best
        for a in range(ux - 3 * step, ux + 3 * step + 1, step):
            for b2 in range(uy - 3 * step, uy + 3 * step + 1, step):
                for c2 in range(uz - 3 * step, uz + 3 * step + 1, step):
                    for d2 in range(max(0, fx - 3 * step), fx + 3 * step + 1, step):
                        d = cost(a, b2, c2, d2)
                        if d < bd:
                            best, bd = (a, b2, c2, d2), d
    U.rotation, F.rotation = list(best[:3]), [best[3], 0, 0]
    p = m.fk(fore, hand_rest)
    return math.sqrt(sum((p[a] - target[a]) ** 2 for a in range(3)))


def to_body(m, p):
    """A point of the model frame, posed with AIM, back into the coordinates a body child's cubes are written in (the
    body's rotation - rest + AIM - undone about its pivot), so the part lands on p when the body takes the aim."""
    b = m.bones["body"]
    rot = [b.rotation[a] + AIM["body"][a] for a in range(3)]
    Rt = transpose(rot_mat(*rot))
    d = apply(Rt, (p[0] - b.pivot[0], p[1] - b.pivot[1], p[2] - b.pivot[2]))
    return (b.pivot[0] + d[0], b.pivot[1] + d[1], b.pivot[2] + d[2])


def rod(bone, a, b, thick, color):
    """A thin box from a to b (both in the bone's frame)."""
    dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    L = math.sqrt(dx * dx + dy * dy + dz * dz)
    ry = math.degrees(math.asin(-dz / L))
    rz = math.degrees(math.atan2(dy, dx))
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2)
    bone.box_at(mid, (L, thick, thick), color, rotation=[0, ry, rz], pivot=list(mid))


def solve_aim(m, l_fist, r_fist, bow_tips):
    """The side-on aim: the bow arm straight out along +x (the target), the bow upright with its string toward him,
    the right fist drawn to the jaw; then the nocked arrow and the drawn string laid where that pose puts them (an
    "arrow" body child, shown while he draws). Fills AIM with DSL degree deltas for the game."""
    rest = {n: list(m.bones[n].rotation) for n in ("left_arm", "left_fore", "right_arm", "right_fore", "bow")}
    m.pose = {k: list(v) for k, v in AIM_FIXED.items()}
    hy = HEAD_Y
    nock = m.fk("head", (-1.1, hy + 0.7, -2.2))                    # at the right corner of the jaw
    sh = m.fk("body", (SHOULDER_X, SHOULDER_Y, 0))
    reach = UPPER + FORE + 0.6                                     # shoulder to the fist's centre
    D = 4.0
    while D < 14 and math.dist(sh, (nock[0] + D, nock[1] + 0.3, nock[2])) < reach * 0.96:
        D += 0.05
    l_target = (nock[0] + D, nock[1] + 0.3, nock[2])
    e1 = solve(m, "left_arm", "left_fore", l_fist, l_target,
               lambda e: 0.05 * (e[1] - sh[1]) ** 2)                # the elbow level with the shoulder
    rsh = m.fk("body", (-SHOULDER_X, SHOULDER_Y, 0))
    r_target = (nock[0] - 0.7, nock[1] - 0.2, nock[2] + 0.2)
    e2 = solve(m, "right_arm", "right_fore", r_fist, r_target,
               lambda e: 0.08 * (e[1] - (rsh[1] + 0.6)) ** 2 + 0.03 * max(0.0, e[0] - (rsh[0] - 1.0)) ** 2)
    # the bow upright in the model frame (its string side, -x, toward him): local = parent^T
    R_par = world_rot(m, "left_fore")
    m.bones["bow"].rotation = euler(transpose(R_par))
    # what the pose puts where: the drawn string from each tip to the nock, the arrow from the nock past the bow
    m.pose.update({n: [0, 0, 0] for n in rest})
    solved = {n: list(m.bones[n].rotation) for n in rest}
    for n in rest:
        AIM[n] = [round(solved[n][a] - rest[n][a], 1) for a in range(3)]
        m.pose[n] = AIM[n]
        m.bones[n].rotation = rest[n]
    AIM.update({k: list(v) for k, v in AIM_FIXED.items()})
    m.pose = {k: list(v) for k, v in AIM.items()}
    tips_w = [m.fk("bow", t) for t in bow_tips]
    grip_w = m.fk("bow", (l_fist[0], l_fist[1] + 0.5, l_fist[2]))
    nock_w = (nock[0], nock[1], nock[2])
    m.pose = {}
    ar = m.bone("arrow", [0, HIP, 0], parent="body")
    nb = to_body(m, nock_w)
    for t in tips_w:
        rod(ar, to_body(m, t), nb, 0.18, "string")
    # the arrow: along the line nock -> grip, from just behind the nock to 1.6 past the grip
    gb = to_body(m, grip_w)
    d = [gb[a] - nb[a] for a in range(3)]
    L = math.sqrt(sum(v * v for v in d))
    u = [v / L for v in d]
    a0 = [nb[a] - u[a] * 0.3 for a in range(3)]
    a1 = [gb[a] + u[a] * 1.4 for a in range(3)]
    rod(ar, a0, a1, 0.24, "wood")
    rod(ar, [a1[a] - u[a] * 0.2 for a in range(3)], [a1[a] + u[a] * 0.9 for a in range(3)], 0.5, "steel")  # head
    for k in (0.4, 1.6):                                                                                 # fletching
        f0 = [nb[a] + u[a] * k for a in range(3)]
        rod(ar, f0, [f0[a] + u[a] * 1.1 for a in range(3)], 0.12, "feather")
        ar.box_at((f0[0] + u[0] * 0.55, f0[1] + 0.3, f0[2] + u[2] * 0.55), (0.9, 0.5, 0.1), "feather")
    return max(e1, e2)


def squire():
    m = Model("Squire Runner (v2)", "sunforged", "raider (T1, through the Paladin pipeline)", PALETTE, EMISSIVE)
    torso(m)
    scabbard(m)
    pennant(m)
    head(m)
    feet = legs(m)
    ra, rf, rfist = arm(m, -1, [16, 0, 5], [26, 0, 0])
    la, lf, lfist = arm(m, 1, [-8, 0, -5], [16, 0, 0])
    sword(m, rfist)
    tips = bow(m, lfist)
    quiver_and_slung_bow(m)
    for ft, shin in feet:
        m.ground_leg(ft, also=(shin,))
    err = solve_aim(m, lfist, rfist, tips)
    return m, err


MODELS = {"sunforged_squire_runner_v2": squire}


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
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes, aim fist error {err:.2f} px")
        print("AIM (DSL degrees added in SquireRunnerUnitModel):")
        for k, v in AIM.items():
            print("  %-12s %s" % (k, v))


if __name__ == "__main__":
    main()
