#!/usr/bin/env python3
"""The Paladin - Radiant, second pass (fourth round of looking: sliced forms, a real sword - see the functions) (lovish picked Radiant, Oct 14 17:44): the Tabs-pioneered techniques
(claude/avatar.md) brought onto the chosen direction, inside THE HUMAN DIAL's Sunforged band (humanoid, not human:
taller, longer shins, smaller head, bigger shoulders, NO face) - so the helm is the face:

- anatomy under the plate: a V torso (trapezius slopes under the gorget, a breastplate over a narrow waist, three
  abdominal lames), rerebraces with shoulder caps, couters with a fan, vambraces that taper to the wrist, flared
  gauntlet cuffs; long legs with the digitigrade hint, knee cops with a side wing, lamed sabatons;
- hands that grip: each gauntlet closes round the greatsword's grip - a back-of-hand plate, four finger lames
  wrapping the front with the splits drawn, a thumb over the index finger - and the forearm ends at its wrist;
- a sculpted helm with an expression: a domed skull, a brow ridge, cheek guards angled in, a narrower bevor with a
  chin point, and the glowing eye slit drawn as two angled halves (a frown - determined); the swept crest and the
  side wings stay;
- cloth: a TEAM-COLOUR tabard front and back (the biggest colour area, tabs-study lesson 11) in two chained
  segments with a gold hem and a sun emblem, and a team-colour cape in three chained segments - its lining a darker
  team colour (the inside is what the front and quarter views see) with light along the edges - and a swallowtail hem - parts the animation layer can swing (a cape that hangs, not a board);
- the signature stays: a blade of light through a ring (the halo), now with a sun emblem on the chest.

    python3 tools/gen_paladin.py      # writes design/models/sunforged_paladin_radiant_v2.json

Space and rotations are the DSL's (y up, faces -Z, +x = the unit's left, rotation x then y then z). Rx > 0 swings
a hanging part's foot forward (-z); a cape flares back with Rx < 0.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, fauld, ring, OUT  # noqa: E402

PALETTE = {
    # plate a step below white and its dark a real step down: the v2 render clipped #e9e4d8 to white under the key
    # light and every fluting line, lame and ridge vanished; the visor is dark iron so the glowing eyes carry the face
    "plate": "#d8d1c2", "plate_dark": "#a39b8a", "visor": "#3e3934", "gold": "#d4a63c", "gold_dark": "#a87f24",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c", "leather": "#5b3f2a", "steel": "#8c8f96", "dark": "#3b3630",
    "sun": "#ffd96a", "glow": "#fff1b0",
    # fifth pass: the blade is steel with light in its fuller (the all-glow blade read as a lightsaber)
    "steel_bright": "#c3c8d0", "steel_edge": "#e6e9ee",
}
EMISSIVE = ["sun", "glow"]

HIP, WAIST_H, CHEST_H, DEPTH = 22.0, 4.0, 10.0, 6.0
WAIST_W, CHEST_W = 6.0, 10.5
CHEST_Y = HIP + WAIST_H          # 26
TOP = CHEST_Y + CHEST_H          # 36: the shoulder line
SHOULDER_X = 6.4
UPPER, FORE = 8.5, 8.0
ARM_W, ARM_D = 3.2, 3.6


# ------------------------------------------------------------------ torso ------------------------------------------
# The torso as a stack of slices (fourth pass - lovish 18:23: "an amalgamation of blocks ... smoothing out the mesh
# so parts of the armor blend"): each slice a little wider/deeper than the one under it, so the breastplate is one
# swelling, rounded form - narrow waist, full chest pushed forward like gothic plate, narrowing again under the
# gorget - instead of a box with plates stuck on. (y of the slice's bottom, width, depth.)
TORSO = ((24.0, 6.4, 5.2), (25.2, 6.6, 5.4), (26.2, 7.0, 5.7), (27.3, 7.8, 6.1), (28.4, 8.7, 6.6), (29.5, 9.6, 7.0),
         (30.6, 10.3, 7.3), (31.7, 10.7, 7.2), (32.8, 10.9, 6.8), (33.9, 10.6, 6.2), (34.9, 9.8, 5.4))
TORSO_TOP = TOP + 0.4
FRONT, BACK = 0.57, 0.43       # how the depth splits: the chest pushes forward


def torso_slice(y):
    """(width, depth, front z, back z) of the torso at height y."""
    k = 0
    for n, (y0, w, d) in enumerate(TORSO):
        if y >= y0:
            k = n
    _, w, d = TORSO[k]
    return w, d, -d * FRONT, d * BACK


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    # pelvis under the faulds, belt, buckle
    b.cube([-3.2, HIP, -2.6], [6.4, 2.6, 5.0], "plate")
    for n, (y0, w, d) in enumerate(TORSO):
        y1 = TORSO[n + 1][0] if n + 1 < len(TORSO) else TORSO_TOP
        h = y1 - y0
        zf = -d * FRONT
        b.cube([-w / 2, y0, zf], [w, h + 0.05, d], "plate")
        b.cube([-w / 2 - 0.25, y0 + 0.15, zf + 0.6], [w + 0.5, max(h - 0.3, 0.3), d - 1.2], "plate")   # rounding twin
        b.cube([-0.3, y0, zf - 0.18], [0.6, h + 0.05, 0.3], "gold" if y0 >= 28.4 else "plate_dark")      # the keel
        # fifth pass: no fluting lines or plackart lame lines - from the quarter they read as a picket fence over
        # the chest; a smooth cuirass with one keel is the white-harness look, and the slices now blend (one object
        # per bone in the renderer, a bone-wide gradient in the game texture)
        if n + 1 < len(TORSO):                                                    # a half step between slices
            yn, wn, dn = TORSO[n + 1]
            hn = (TORSO[n + 2][0] if n + 2 < len(TORSO) else TORSO_TOP) - yn
            wb, db = (w + wn) / 2, (d + dn) / 2
            b.cube([-wb / 2, y0 + h * 0.5, -db * FRONT], [wb, h * 0.5 + hn * 0.5, db], "plate")
    b.cube([-3.6, CHEST_Y - 1.5, -3.25], [7.2, 1.6, 6.5], "leather")                                     # belt
    b.cube([-1.1, CHEST_Y - 1.6, -3.6], [2.2, 1.8, 0.6], "sun")                                          # buckle
    # the sun on the breast, on the fullest slice
    ey = 32.2
    _, _, zf, _ = torso_slice(ey)
    sc = (0, ey, zf - 0.35)
    b.box_at(sc, (1.9, 1.9, 0.5), "sun", rotation=[0, 0, 45], pivot=list(sc))
    for k in range(8):
        a = k * math.pi / 4
        L = 1.4 if k % 2 == 0 else 0.9
        r = 1.2 + L / 2
        cx, cy = sc[0] + r * math.cos(a), sc[1] + r * math.sin(a)
        b.box_at((cx, cy, sc[2] + 0.1), (L, 0.4, 0.4), "gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, sc[2]])
    # gorget: two lames and a rolled edge, rising out of the top slice
    b.cube([-3.3, TOP - 0.9, -2.9], [6.6, 0.8, 5.4], "plate")
    b.cube([-2.7, TOP - 0.2, -2.6], [5.4, 1.4, 4.8], "gold")
    b.cube([-2.8, TOP + 1.1, -2.7], [5.6, 0.35, 5.0], "gold_dark")
    for s_ in (1, -1):
        # besagews hung before the armpits, on the chest's surface
        _, _, zf, _ = torso_slice(32.4)
        bc = (s_ * 5.0, 32.4, zf - 0.3)
        b.box_at(bc, (2.2, 2.2, 0.4), "gold", rotation=[0, 0, 45], pivot=list(bc))
        b.box_at((bc[0], bc[1], bc[2] - 0.25), (1.1, 1.1, 0.3), "sun", rotation=[0, 0, 45], pivot=list(bc))
        # haute-pieces: the neck guards standing on the pauldrons
        hp = (s_ * 4.7, TOP + 0.3, 0)
        b.cube([hp[0] - 0.35, hp[1], -2.6], [0.7, 2.6, 5.2], "plate", rotation=[0, 0, s_ * 12], pivot=list(hp))
        b.cube([hp[0] - 0.4, hp[1] + 2.5, -2.7], [0.8, 0.4, 5.4], "gold", rotation=[0, 0, s_ * 12], pivot=list(hp))
        # trapezius under the plate
        b.cube([s_ * 3.6 - 2.3, TOP - 1.4, -2.2], [4.6, 1.6, 4.4], "plate_dark", rotation=[0, 0, -s_ * 18],
               pivot=[s_ * 2.2, TOP - 0.4, 0])
    return b


def pauldrons(body):
    """Four lames fanned over each shoulder, each with a narrower, thicker rounding lame inside it so the edge reads
    curved; the top lame enamelled in team colour (what the RTS camera sees from above), a gilt rolled edge on the
    lowest lame only (ornament on few plates)."""
    for side in (1, -1):
        sx, sy, sz = side * 5.6, TOP + 0.5, 0
        for i in range(4):
            droop = 8 + 12 * i
            w = 6.4 - 0.5 * i
            d = 6.8 - 0.4 * i
            thick = 1.25
            x0 = sx if side > 0 else sx - w
            rot, piv = [0, 0, -side * droop], [sx, sy, sz]
            y0 = sy - thick * (i + 1)
            body.cube([x0, y0, sz - d / 2], [w, thick, d], "tabard" if i == 0 else "plate", rotation=rot, pivot=piv)
            body.cube([x0 + (0.0 if side > 0 else 0.6), y0 - 0.2, sz - d / 2 + 0.6], [w - 0.6, thick + 0.4, d - 1.2],
                      "tabard_dark" if i == 0 else "plate", rotation=rot, pivot=piv)
            if i == 0:
                body.cube([x0, y0 + thick - 0.25, sz - 0.7], [w, 0.9, 1.4], "gold", rotation=rot, pivot=piv)  # rib
            if i == 3:
                body.cube([x0, y0 - 0.15, sz - d / 2 - 0.1], [w, 0.4, d + 0.2], "gold", rotation=rot, pivot=piv)


def sash_and_relic(m):
    """Asymmetry and things that swing: a team-colour sash knotted on the left hip with two tails, and a prayer book
    on a chain at the right hip (leather, gold corners, a sun on the cover) - each its own bone."""
    sash = m.bone("sash", [4.4, HIP + 1.0, -0.4], parent="body")
    sash.cube([3.4, HIP + 0.2, -1.8], [2.0, 1.7, 2.2], "tabard_dark")                                     # the knot
    for k, (dx, L, rz) in enumerate(((4.9, 6.2, 8), (5.6, 5.0, 17))):
        sash.cube([dx, HIP + 0.6 - L, -0.9], [1.1, L, 0.45], "tabard", rotation=[0, 0, rz], pivot=[4.4, HIP + 1.0, -0.6])
        sash.cube([dx, HIP + 0.6 - L - 0.5, -0.95], [1.1, 0.6, 0.55], "gold", rotation=[0, 0, rz], pivot=[4.4, HIP + 1.0, -0.6])
    rel = m.bone("relic", [-5.6, HIP + 0.8, -0.4], parent="body")
    for k in range(3):                                                                                    # the chain
        rel.cube([-5.75, HIP + 0.3 - k * 1.1, -0.55], [0.3, 0.9, 0.3], "steel")
    by = HIP - 5.4
    rel.cube([-6.6, by, -1.2], [2.0, 2.6, 1.0], "leather")                                               # the book
    rel.cube([-6.55, by + 0.15, -1.15], [1.9, 2.3, 0.9], "plate")                                        # its pages
    rel.cube([-6.7, by - 0.05, -1.3], [2.2, 2.7, 0.25], "leather")                                       # front cover
    for cx, cy in ((-6.75, by - 0.1), (-4.95, by - 0.1), (-6.75, by + 2.25), (-4.95, by + 2.25)):
        rel.cube([cx, cy, -1.4], [0.5, 0.5, 0.3], "gold")                                                # corners
    rel.box_at((-5.6, by + 1.3, -1.45), (0.8, 0.8, 0.2), "sun", rotation=[0, 0, 45], pivot=[-5.6, by + 1.3, -1.45])


def tabard(m):
    """Team colour front and back: two chained segments each (the animation layer swings them), a gold hem, the
    front one carrying a sun - the largest single colour on the body."""
    for side, z, sign in (("front", -DEPTH / 2 - 0.95, 1), ("back", DEPTH / 2 + 0.45, -1)):
        top = CHEST_Y - 0.2
        b1 = m.bone("tabard_%s_0" % side, [0, top, z + 0.25], parent="body", rotation=[sign * 6, 0, 0])
        b1.cube([-2.8, top - 6.5, z], [5.6, 6.5, 0.5], "tabard")
        for e in (-2.8, 2.45):
            b1.cube([e, top - 6.5, z - 0.08], [0.35, 6.5, 0.6], "gold")                                      # borders
        b1.cube([-3.0, top - 0.9, z - 0.1], [6.0, 0.9, 0.7], "gold")                                         # under the belt
        y2 = top - 6.5
        b2 = m.bone("tabard_%s_1" % side, [0, y2, z + 0.25], parent=b1.name, rotation=[sign * 5, 0, 0])
        b2.cube([-3.2, y2 - 6.0, z], [6.4, 6.0, 0.5], "tabard")
        for e in (-3.2, 2.85):
            b2.cube([e, y2 - 6.0, z - 0.08], [0.35, 6.0, 0.6], "gold")
        b2.cube([-3.3, y2 - 6.0, z - 0.1], [6.6, 0.7, 0.7], "gold")                                          # hem
        b2.cube([-0.25, y2 - 6.6, z - 0.05], [0.5, 1.2, 0.6], "tabard_dark")                                 # a centre notch
        if side == "front":
            c = (0, top - 5.4, z - 0.3)
            b1.box_at(c, (2.4, 2.4, 0.4), "sun", rotation=[0, 0, 45], pivot=list(c))
            b1.box_at((c[0], c[1], c[2] + 0.05), (3.4, 0.5, 0.35), "gold")
            b1.box_at((c[0], c[1], c[2] + 0.05), (0.5, 3.4, 0.35), "gold")


def cape(m, flow=10):
    """Team-colour cape from the shoulders: three chained segments widening toward the hem, each with a light lining
    a hair inside it, a swallowtail hem; flow = degrees it streams back."""
    z0 = DEPTH / 2 + 1.0
    y = TOP - 0.6
    parent = "body"
    segs = ((11.0, 8.5), (12.0, 8.0), (13.0, 7.0))
    for i, (w, L) in enumerate(segs):
        bend = -(flow * (0.8, 0.35, 0.3)[i] + (3, 2, 2)[i])
        seg = m.bone("cape_%d" % i, [0, y, z0], parent=parent, rotation=[bend, 0, 0])
        seg.cube([-w / 2, y - L, z0], [w, L + 0.15, 0.45], "tabard")
        seg.cube([-w / 2 + 0.5, y - L + 0.3, z0 - 0.3], [w - 1.0, L - 0.3, 0.3], "tabard_dark")            # the lining
        for s in (1, -1):                                                                                 # light at the edges
            seg.cube([s * (w / 2 - 0.25) - 0.25, y - L + 0.2, z0 - 0.38], [0.5, L - 0.2, 0.35], "glow")
        if i == 0:
            seg.cube([-w / 2 - 0.4, y - 1.6, z0 - 0.4], [w + 0.8, 1.6, 1.2], "gold")                       # clasp bar
            for s in (1, -1):
                seg.box_at((s * (w / 2 - 0.6), y - 0.8, z0 - 0.7), (1.6, 1.6, 0.6), "sun", rotation=[0, 0, 45],
                           pivot=[s * (w / 2 - 0.6), y - 0.8, z0 - 0.7])
        if i == 1:
            # the sun of the Sunforged on the cape's back: what the RTS camera sees from above and behind
            ec = (0, y - L / 2 + 0.6, z0 + 0.62)
            seg.box_at(ec, (2.6, 2.6, 0.3), "sun", rotation=[0, 0, 45], pivot=list(ec))
            ring(seg, ec, 3.1, 12, 0.45, 0.3, "gold")
            for k in range(12):
                a = k * math.pi / 6
                L2 = 1.6 if k % 2 == 0 else 0.9
                r = 3.4 + L2 / 2
                cx, cy = ec[0] + r * math.cos(a), ec[1] + r * math.sin(a)
                seg.box_at((cx, cy, ec[2]), (L2, 0.4, 0.3), "gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, ec[2]])
        if i == len(segs) - 1:
            # swallowtail: the hem cut in three tongues, the outer two longer
            for k, (dx, ext) in enumerate(((-w / 2, 2.6), (-w / 6, 1.2), (w / 6, 2.6))):
                tw = w / 3 - 0.3
                seg.cube([dx + 0.15, y - L - ext, z0], [tw, ext + 0.05, 0.45], "tabard")
                seg.cube([dx + 0.15, y - L - ext, z0 - 0.08], [tw, 0.6, 0.55], "gold")
        parent, y = seg.name, y - L


# ------------------------------------------------------------------ arms and hands ---------------------------------
def arm(m, side, parent="body"):
    """Rerebrace (core + two rounding twins = an octagonal limb), articulated lames above and below a couter, a
    vambrace that narrows to the wrist, a flared cuff. Decluttered in the fourth pass: no cuff points, a small plate
    fan on the couter. The hand is NOT on the forearm: the gauntlet closes round the sword grip (grip_hand). Returns
    (upper, fore, wrist point in rest space)."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, TOP - 0.6
    x0 = sx - ARM_W / 2
    elbow_y = sy - UPPER
    wrist_y = elbow_y - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    up.cube([x0, elbow_y + 1.6, -ARM_D / 2], [ARM_W, UPPER - 1.6, ARM_D], "plate")                         # rerebrace
    up.cube([x0 - 0.25, elbow_y + 2.0, -ARM_D / 2 + 0.6], [ARM_W + 0.5, UPPER - 2.8, ARM_D - 1.2], "plate")
    up.cube([x0 + 0.5, elbow_y + 2.0, -ARM_D / 2 - 0.25], [ARM_W - 1.0, UPPER - 2.8, ARM_D + 0.5], "plate")
    up.cube([x0 - 0.4, sy - 2.6, -ARM_D / 2 - 0.4], [ARM_W + 0.8, 3.0, ARM_D + 0.8], "plate_dark")         # under the pauldron
    for k, yy in enumerate((elbow_y + 0.85, elbow_y + 0.2)):                                              # lames above
        up.cube([x0 - 0.1 - 0.1 * k, yy, -ARM_D / 2 - 0.1 - 0.1 * k], [ARM_W + 0.2 + 0.2 * k, 0.7, ARM_D + 0.2 + 0.2 * k],
                "plate_dark" if k == 0 else "plate")
    up.cube([x0 + 0.2, elbow_y + 3.6, -ARM_D / 2 - 0.3], [ARM_W - 0.4, 0.9, ARM_D + 0.6], "leather")      # strap
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    fo.cube([x0 - 0.3, elbow_y - 1.4, -ARM_D / 2 - 0.3], [ARM_W + 0.6, 2.4, ARM_D + 0.6], "plate", inflate=0.15)   # couter
    fo.cube([x0 + 0.5, elbow_y - 1.0, -ARM_D / 2 - 0.65], [ARM_W - 1.0, 1.6, 0.5], "plate", inflate=0.05)      # its boss
    ox = x0 + ARM_W + 0.1 if side > 0 else x0 - 0.7
    fo.cube([ox, elbow_y - 1.1, -1.1], [0.6, 2.0, 2.2], "plate", rotation=[0, 0, side * 12], pivot=[ox + 0.3, elbow_y, 0])
    for k, yy in enumerate((elbow_y - 2.1, elbow_y - 2.75)):                                              # lames below
        fo.cube([x0 - 0.15 + 0.1 * k, yy, -ARM_D / 2 - 0.15 + 0.1 * k], [ARM_W + 0.3 - 0.2 * k, 0.6, ARM_D + 0.3 - 0.2 * k],
                "plate" if k == 0 else "plate_dark")
    vt = elbow_y - 2.9
    fo.cube([x0 + 0.15, wrist_y + 1.4, -ARM_D / 2 + 0.15], [ARM_W - 0.3, vt - wrist_y - 1.4, ARM_D - 0.3], "plate")   # vambrace
    fo.cube([x0 - 0.1, wrist_y + 1.8, -ARM_D / 2 + 0.7], [ARM_W + 0.2, vt - wrist_y - 2.2, ARM_D - 1.4], "plate")
    fo.cube([x0 + 0.6, wrist_y + 1.8, -ARM_D / 2 - 0.1], [ARM_W - 1.2, vt - wrist_y - 2.2, ARM_D + 0.2], "plate")
    fo.cube([sx - 0.18, wrist_y + 2.2, -ARM_D / 2 - 0.2], [0.36, vt - wrist_y - 3.0, 0.3], "plate_dark")    # its ridge
    fo.cube([x0 - 0.35, wrist_y, -ARM_D / 2 - 0.35], [ARM_W + 0.7, 1.6, ARM_D + 0.7], "plate_dark")       # flared cuff
    fo.cube([x0 - 0.4, wrist_y + 1.3, -ARM_D / 2 - 0.4], [ARM_W + 0.8, 0.4, ARM_D + 0.8], "gold")          # cuff rim
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.2, 0)


def grip_hand(bone, centre, side):
    """A gauntlet closed round a vertical grip at `centre` (on the sword's bone): the palm and back of the hand round
    the grip, four finger lames across the front with their splits, the knuckle ridge, a thumb over the index finger
    from the side the arm comes from (side), a gold knuckle rivet line on the back."""
    x, y, z = centre
    bone.cube([x - 1.5, y - 1.35, z - 1.4], [3.0, 2.7, 3.0], "plate_dark")                                 # palm + back
    bone.cube([x - 1.6, y - 1.3, z - 1.9], [3.2, 2.6, 0.6], "plate")                                       # finger lames
    for k in (-0.65, 0.0, 0.65):
        bone.cube([x - 1.65, y + k - 0.05, z - 1.95], [3.3, 0.1, 0.65], "dark")                             # the splits
    bone.cube([x - 1.7, y - 1.3, z - 1.5], [3.4, 2.6, 0.45], "plate", inflate=0.05)                        # knuckle ridge
    tx = x + 0.2 if side > 0 else x - 1.8
    bone.cube([tx, y + 0.55, z - 2.15], [1.6, 0.8, 0.9], "plate", rotation=[0, 0, -side * 14],
              pivot=[tx + 0.8, y + 0.95, z - 1.7])                                                          # thumb
    bone.cube([x - 1.2, y - 0.2, z + 1.55], [2.4, 0.5, 0.2], "gold")                                        # rivets


# ------------------------------------------------------------------ helm -------------------------------------------
def helm(m, mood="determined"):
    """Faceless, but a face: domed skull, brow ridge, cheek guards angled in, a narrower bevor with a chin point; the
    eye slit in two halves that tilt with the mood (determined = a frown: inner ends low)."""
    hy, hw, hd = TOP + 1.1, 6.0, 7.0
    h = m.bone("head", [0, hy, 0], parent="body")
    # the skull as a dome of slices narrowing upward (y offset, height, width, depth), each with a gold rib on the
    # front centre so the rib runs over the curve from the brow to the crest
    DOME = ((2.4, 1.6, 6.0, 7.0), (4.0, 1.4, 6.0, 7.0), (5.4, 1.0, 5.6, 6.6), (6.4, 0.8, 5.0, 6.0),
            (7.2, 0.6, 4.2, 5.0), (7.8, 0.5, 3.0, 3.6))
    for i, (yo, hh, w, d) in enumerate(DOME):
        h.cube([-w / 2, hy + yo, -d / 2], [w, hh + 0.05, d], "plate")
        h.cube([-w / 2 - 0.2, hy + yo + 0.1, -d / 2 + 0.5], [w + 0.4, max(hh - 0.2, 0.2), d - 1.0], "plate")
        if i + 1 < len(DOME):     # fifth pass: a half step into the next slice - the dome reads round, not stepped
            yn, hn, wn, dn = DOME[i + 1]
            h.cube([-(w + wn) / 4, hy + yo + hh * 0.5, -(d + dn) / 4], [(w + wn) / 2, hh * 0.5 + hn * 0.5, (d + dn) / 2], "plate")
        if yo >= 5.4:
            h.cube([-0.3, hy + yo, -d / 2 - 0.2], [0.6, hh + 0.05, 0.3], "gold")
    h.cube([-hw / 2 + 0.7, hy, -hd / 2 + 0.4], [hw - 1.4, 2.6, hd - 0.8], "plate")                        # bevor
    h.cube([-1.1, hy - 0.5, -hd / 2 + 0.1], [2.2, 1.3, 1.6], "plate", inflate=0.05)                       # chin point
    h.cube([-hw / 2 - 0.1, hy - 1.4, -hd / 2 + 0.3], [hw + 0.2, 1.6, hd - 0.2], "steel")                 # mail aventail
    for k in range(5):                                                                                    # its rings, a row
        h.cube([-2.4 + k * 1.2, hy - 1.45, -hd / 2 + 0.2], [0.5, 0.35, 0.2], "plate_dark")
    for s in (1, -1):                                                                                     # cheek guards
        cx = s * (hw / 2 - 0.2)
        h.cube([cx - 0.35, hy + 0.6, -hd / 2 - 0.1], [0.7, 3.6, 3.0], "plate", rotation=[0, s * 16, 0],
               pivot=[cx, hy + 2.4, -hd / 2 + 1.4])
    h.cube([-hw / 2 + 0.6, hy + 1.0, -hd / 2 - 0.35], [hw - 1.2, 3.6, 0.5], "visor")                     # the face plate
    h.cube([-hw / 2 - 0.25, hy + 4.4, -hd / 2 - 0.75], [hw + 0.5, 0.95, 1.1], "gold")                    # brow ridge
    h.cube([-0.35, hy + 1.2, -hd / 2 - 0.6], [0.7, 2.0, 0.4], "plate")                                    # nasal ridge
    for s in (1, -1):
        h.cube([s * 2.85 - 0.22, hy + 3.9, -hd / 2 + 0.15], [0.45, 0.45, 0.35], "gold")                   # temple rivets
        for k in range(3):                                                                                # breaths
            h.cube([s * (1.35 + k * 0.5) - 0.13, hy + 0.15, -hd / 2 + 0.25], [0.26, 0.7, 0.2], "dark")
    tilt = {"determined": 14, "calm": 0, "fury": 24}[mood]
    for s in (1, -1):                                                                                     # the eye slit
        ex = s * 1.35
        h.cube([ex - 0.95, hy + 3.4, -hd / 2 - 0.62], [1.9, 0.45, 0.35], "glow", rotation=[0, 0, s * tilt],
               pivot=[ex, hy + 3.6, -hd / 2 - 0.45])
    h.cube([-0.3, hy + 1.3, -hd / 2 - 0.45], [0.6, 1.4, 0.2], "glow")                                    # breath slit (dim)
    # the swept crest and the side wings (Sunforged: winged helms)
    h.cube([-0.55, hy + 7.6, -2.6], [1.1, 1.6, 8.0], "gold", rotation=[-26, 0, 0], pivot=[0, hy + 7.6, -2.6])
    h.cube([-0.4, hy + 8.8, -1.6], [0.8, 1.2, 9.0], "sun", rotation=[-26, 0, 0], pivot=[0, hy + 7.6, -2.6], upgrade="sun_ward")
    for s in (1, -1):
        x0 = hw / 2 if s > 0 else -hw / 2 - 0.7
        for k, (L, a) in enumerate(((4.6, 28), (3.6, 44), (2.6, 60))):                                     # three feathers
            h.cube([x0, hy + 4.4 + k * 0.2, 0.2], [0.7, 1.0, L], "gold" if k == 0 else "gold_dark",
                   rotation=[-a, s * 22, 0], pivot=[x0 + 0.35, hy + 4.9, 0.4])
    return h, hy


# ------------------------------------------------------------------ legs -------------------------------------------
def leg(m, side, thigh_rot, shin_rot, foot_rot):
    """Long, slim, the digitigrade hint (knee forward, ankle back) - with anatomy under the plate (third pass: the
    v2.1 render showed plain white columns): a thigh that tapers from hip to knee, a prominent knee, a calf that
    swells at the back of the shin and narrows to a slim ankle; every long plate gets a shallower, wider twin so its
    profile from above is an octagon, not a square. Gold only on the toe cap and a thin rim on the knee wing (the
    v2.1 legs were scattered with yellow squares - ornament lives on few big plates)."""
    pre = "left" if side > 0 else "right"
    hx = side * 3.0
    TH, SH = 10.0, 10.0
    knee_y, ankle_y = HIP - TH, HIP - TH - SH
    th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh_rot)
    # thigh: upper (wide) and lower (narrow) halves, each with its rounding twin
    for (w, d, y0, h) in ((4.2, 4.6, knee_y + 5.0, TH - 5.0), (3.6, 3.9, knee_y + 0.4, 4.8)):
        th.cube([hx - w / 2, y0, -d / 2], [w, h, d], "plate")
        th.cube([hx - w / 2 - 0.3, y0 + 0.4, -d / 2 + 0.7], [w + 0.6, h - 0.8, d - 1.4], "plate")
    for k, yy in enumerate((knee_y + 1.75, knee_y + 2.4)):                                                # lames above the knee
        th.cube([hx - 1.95 - 0.1 * k, yy, -2.2 - 0.1 * k], [3.9 + 0.2 * k, 0.6, 4.4 + 0.2 * k],
                "plate_dark" if k == 0 else "plate")
    th.cube([hx - 1.7, knee_y + 3.0, -2.75], [3.4, TH - 4.2, 0.7], "plate", inflate=0.1)                   # cuisse
    th.cube([hx - 0.2, knee_y + 2.8, -3.2], [0.4, TH - 4.6, 0.3], "plate_dark")                         # its ridge
    th.cube([hx - 2.2, HIP - 1.6, -2.5], [4.4, 1.2, 5.0], "leather")                                      # the strap
    for k in range(2):                                                                                    # tassets
        ty = HIP - 0.4 - k * 2.0
        th.cube([hx - 2.0, ty - 2.4, -3.3 - k * 0.12], [4.0, 2.4, 0.5], "plate", rotation=[8, 0, 0],
                pivot=[hx, ty, -3.1])
        th.cube([hx - 2.0, ty - 2.4, -3.4 - k * 0.12], [4.0, 0.4, 0.6], "plate_dark", rotation=[8, 0, 0],
                pivot=[hx, ty, -3.1])
    sh = m.bone(pre + "_shin", [hx, knee_y, 0], parent=pre + "_thigh", rotation=shin_rot)
    # shin: full under the knee, narrowing to the ankle; the calf swells at the back
    sh.cube([hx - 1.7, ankle_y + 4.0, -1.85], [3.4, SH - 4.0, 3.7], "plate")
    sh.cube([hx - 1.95, ankle_y + 4.4, -1.2], [3.9, SH - 4.8, 2.4], "plate")
    sh.cube([hx - 1.3, ankle_y + 0.6, -1.4], [2.6, 3.8, 2.8], "plate")                                    # to the ankle
    sh.cube([hx - 1.4, ankle_y + 3.6, 1.0], [2.8, 4.6, 1.3], "plate", inflate=0.1)                        # calf
    sh.cube([hx - 1.5, ankle_y + 1.2, -2.25], [3.0, SH - 2.6, 0.6], "plate", inflate=0.1)                 # greave front
    sh.cube([hx - 0.2, ankle_y + 1.6, -2.6], [0.4, SH - 3.8, 0.35], "plate_dark")                         # its keel
    sh.cube([hx - 2.15, knee_y - 1.9, -2.6], [4.3, 3.6, 5.0], "plate", inflate=0.25)                      # knee cop
    for k, yy in enumerate((knee_y - 2.6, knee_y - 3.25)):                                                # lames below it
        sh.cube([hx - 2.0 + 0.1 * k, yy, -2.35 + 0.1 * k], [4.0 - 0.2 * k, 0.6, 4.4 - 0.2 * k],
                "plate" if k == 0 else "plate_dark")
    sh.box_at((hx, knee_y - 0.1, -2.8), (2.2, 2.2, 0.6), "plate", rotation=[0, 0, 45], pivot=[hx, knee_y - 0.1, -2.8])   # its boss, a diamond
    sh.box_at((hx, knee_y - 0.1, -3.2), (0.7, 0.7, 0.3), "plate_dark", rotation=[0, 0, 45], pivot=[hx, knee_y - 0.1, -3.2])  # rivet
    wx = hx + 2.2 if side > 0 else hx - 3.1
    sh.cube([wx, knee_y - 1.4, -1.5], [0.9, 2.8, 3.0], "plate", rotation=[0, 0, side * 14], pivot=[wx + 0.45, knee_y, 0])   # wing
    sh.cube([wx - 0.05, knee_y - 1.5, -1.6], [1.0, 0.35, 3.2], "gold", rotation=[0, 0, side * 14], pivot=[wx + 0.45, knee_y, 0])  # its rim
    ft = m.bone(pre + "_foot", [hx, ankle_y, 0], parent=pre + "_shin", rotation=foot_rot)
    fl = 6.5
    ft.cube([hx - 1.4, ankle_y - 0.6, -1.4], [2.8, 1.0, 2.8], "plate_dark")                               # the ankle joint
    ft.cube([hx - 2.2, ankle_y - 2.2, -fl + 2.1], [4.4, 1.8, fl], "plate")                                 # sabaton
    ft.cube([hx - 1.9, ankle_y - 1.6, 0.6], [3.8, 1.4, 1.6], "plate")                                      # heel
    top = ankle_y - 0.4                                                                                   # the sabaton's top
    for k in range(3):        # lames climbing the instep toward the ankle, each overlapping the one before it
        ft.cube([hx - 2.1 + 0.15 * k, top - 0.3, -fl + 2.9 + k * 1.15], [4.2 - 0.3 * k, 0.55 + 0.25 * k, 1.5], "plate")
    ft.cube([hx - 2.2, ankle_y - 2.2, -fl + 1.8], [4.4, 1.2, 1.3], "gold")                                 # toe cap
    ft.cube([hx - 0.9, ankle_y - 2.2, -fl + 0.6], [1.8, 0.9, 1.3], "plate")                                # the point
    return pre + "_foot"


# ------------------------------------------------------------------ the sword ------------------------------------
def greatsword(b, gx, gz):
    """The one part that carries the most detail (MORTEMARTT lesson; lovish 18:23 "the sword could look better"): a
    faceted pommel, a wire-wrapped grip with ferrules, a quillon block with the sun on both faces, quillons that curve
    up in three segments to diamond finials, a ricasso engraved with a sunburst, and a blade of light built like a
    real blade - a core, light along both EDGES (the brightest part), a raised diamond ridge down the middle (a cube
    turned 45 degrees about the blade's axis), engraved prayer marks, tapering in five sections to a real point."""
    G = 32.0                                                                                             # guard line
    # pommel: an octagonal prism (a square and the same square turned 45) and a button
    b.cube([gx - 1.2, 20.6, gz - 1.2], [2.4, 2.0, 2.4], "gold")
    b.cube([gx - 1.2, 20.7, gz - 1.2], [2.4, 1.8, 2.4], "gold", rotation=[0, 45, 0], pivot=[gx, 21.6, gz])
    b.cube([gx - 0.5, 20.0, gz - 0.5], [1.0, 0.7, 1.0], "gold_dark")
    for zz in (gz - 1.75, gz + 1.75):
        b.box_at((gx, 21.6, zz), (0.9, 0.9, 0.25), "sun", rotation=[0, 0, 45], pivot=[gx, 21.6, zz])
    # grip: leather wrapped in wire, a ferrule at each end
    b.cube([gx - 0.6, 22.6, gz - 0.6], [1.2, G - 22.6, 1.2], "leather")
    for k in range(9):
        b.cube([gx - 0.66, 23.4 + k * 0.95, gz - 0.66], [1.32, 0.22, 1.32], "gold_dark")
    for yy in (22.5, G - 0.5):
        b.cube([gx - 0.78, yy, gz - 0.78], [1.56, 0.5, 1.56], "gold")
    # the quillon block, a sun on each face
    b.cube([gx - 1.3, G - 0.1, gz - 1.1], [2.6, 2.0, 2.2], "gold")
    for zz in (gz - 1.25, gz + 1.25):
        b.box_at((gx, G + 0.9, zz), (1.5, 1.5, 0.3), "sun", rotation=[0, 0, 45], pivot=[gx, G + 0.9, zz])
    # quillons: three segments each side curving up, then a diamond finial
    for side in (1, -1):
        px, py = gx + side * 1.3, G + 0.9
        for L, t, dd, a in ((1.9, 1.0, 1.4, 0), (1.7, 0.9, 1.3, 14), (1.4, 0.8, 1.2, 32)):
            x0 = px if side > 0 else px - L
            b.cube([x0, py - t / 2, gz - dd / 2], [L, t, dd], "gold", rotation=[0, 0, side * a], pivot=[px, py, gz])
            px += side * L * math.cos(math.radians(a))
            py += L * math.sin(math.radians(a))
        b.box_at((px + side * 0.3, py + 0.3, gz), (1.0, 1.0, 1.0), "sun", rotation=[0, 0, 45], pivot=[px + side * 0.3, py + 0.3, gz])
    # ricasso: gold, engraved with a sunburst rising off the guard
    R0, R1 = G + 1.9, G + 4.0
    b.cube([gx - 1.1, R0, gz - 0.5], [2.2, R1 - R0, 1.0], "gold")
    for k in range(5):
        a = math.radians(40 + k * 25)
        cx, cy = gx + 1.0 * math.cos(a), R0 + 0.2 + 1.0 * math.sin(a)
        b.box_at((cx, cy, gz - 0.6), (1.3, 0.25, 0.2), "sun", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, gz - 0.6])
        b.box_at((cx, cy, gz + 0.6), (1.3, 0.25, 0.2), "sun", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, gz + 0.6])
    # the blade (fifth pass): steel, five tapering sections, a bevelled edge each side, and the light IN the fuller -
    # a channel of sun down the middle that ends in a small diamond before the point (the all-glow blade read as a
    # lightsaber; a steel blade carrying light is a holy sword). Golem Smithing (sun_ward) lights the edges too.
    y = R1
    widths = (3.6, 3.4, 3.1, 2.8, 2.5)
    for n, w in enumerate(widths):
        L = 3.4
        b.cube([gx - w / 2 + 0.4, y, gz - 0.35], [w - 0.8, L + 0.02, 0.7], "steel_bright")              # flat
        for e in (-1, 1):                                                                                 # the edges
            b.cube([gx + e * (w / 2 - 0.25) - 0.25, y, gz - 0.2], [0.5, L + 0.02, 0.4], "steel_edge")
            b.cube([gx + e * (w / 2 - 0.15) - 0.15, y + 0.1, gz - 0.12], [0.3, L - 0.18, 0.24], "glow",
                   upgrade="sun_ward")
        if n < 4:                                                                                         # the fuller
            b.cube([gx - 0.35, y - 0.01, gz - 0.39], [0.7, L + 0.04, 0.78], "sun")
        y += L
        if n == 3:
            b.box_at((gx, y + 0.5, gz), (1.1, 1.1, 0.8), "sun", rotation=[0, 0, 45], pivot=[gx, y + 0.5, gz])
    for w, h in ((2.0, 1.0), (1.3, 0.9), (0.6, 0.8)):                                                     # the point
        b.cube([gx - w / 2, y, gz - 0.3], [w, h + 0.02, 0.6], "steel_bright")
        b.cube([gx - w / 2 - 0.05, y, gz - 0.15], [w + 0.1, h + 0.02, 0.3], "steel_edge")
        y += h


# ------------------------------------------------------------------ the whole -------------------------------------
def radiant_v2():
    m = Model("Paladin - Radiant v2", "sunforged", "tank (Radiant, fifth pass)", PALETTE, EMISSIVE)
    body = torso(m)
    fauld(body, HIP + 0.3, 6.6, DEPTH / 2 - 0.5, 3, "plate", flare0=10, flare_step=10, widen=1.6)
    tabard(m)
    cape(m, flow=10)
    pauldrons(body)
    sash_and_relic(m)
    head, hy = helm(m)
    # the halo on its own bone (the animation layer turns it slowly), tipped forward 12 degrees so the RTS camera,
    # looking down, sees more of it than an edge; a crown of rays alternating long and short
    hc = (0, hy + 3.8, 6.4)
    m.bone("halo", list(hc), parent="head", rotation=[-12, 0, 0])
    # the ring on a child bone of the tilted one, so the game spins it about its OWN axis (a part's Z*Y*X order would
    # wobble a tilted ring spun on the tilting bone itself)
    halo = m.bone("halo_ring", list(hc), parent="halo")
    ring(halo, hc, 8.6, 12, 1.0, 0.5, "sun")
    ring(halo, hc, 5.6, 12, 0.7, 0.4, "glow", upgrade="sun_ward")
    for k in range(12):
        a = k * math.pi / 6 + math.pi / 12
        L = 2.4 if k % 2 == 0 else 1.3
        r = 9.1 + L / 2
        cx, cy = hc[0] + r * math.cos(a), hc[1] + r * math.sin(a)
        halo.box_at((cx, cy, hc[2]), (L, 0.55, 0.45), "sun", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, hc[2]])
    fl = leg(m, -1, [16, 0, 0], [-30, 0, 0], [14, 0, 0])
    fr = leg(m, 1, [6, 0, 0], [-18, 0, 0], [12, 0, 0])
    # the greatsword of light, upright before the chest; both gauntlets close round its grip
    # a little off the centre line and leaning out 8 degrees, so the blade passes beside the helm's eyes, not over
    # them, and the left arm does not have to cross the whole chest
    gx, gz = -2.4, -8.0
    # the two arms and the sword hang from one rig bone on the shoulder line, so the game swings them as one piece -
    # the overhead chop, the brace - and the gauntlets never let go of the grip (the shoulders sit on its axis)
    m.bone("arms", [0, TOP - 0.6, 0], parent="body")
    sword = m.bone("sword", [gx, 28.0, gz], parent="arms", rotation=[0, 0, 8])
    greatsword(sword, gx, gz)
    top_hand, low_hand = (gx, 29.6, gz), (gx, 26.4, gz)
    grip_hand(sword, top_hand, 1)       # the left hand above
    grip_hand(sword, low_hand, -1)      # the right below
    # third pass: the whole sword sits 2.5 lower - the hands at the belly instead of the chest, so the forearms
    # angle down instead of crossing the breastplate (the v2.1 front read as a tangle of white blocks) and the sun on
    # the breast shows above them; the blade still rises through the halo
    DROP = 2.5
    sword.pivot[1] -= DROP
    for c in sword.cubes:
        c["from"][1] = round(c["from"][1] - DROP, 3)
        if "pivot" in c:
            c["pivot"][1] = round(c["pivot"][1] - DROP, 3)
    top_hand, low_hand = (gx, top_hand[1] - DROP, gz), (gx, low_hand[1] - DROP, gz)
    la, lf, lw = arm(m, 1, parent="arms")
    ra, rf, rw = arm(m, -1, parent="arms")
    # each wrist meets the back of its gauntlet (the +z face, where the forearm comes in), wherever the lean put it
    e1 = m.solve_arm(la, lf, lw, m.fk("sword", (gx + 0.6, top_hand[1], gz + 1.9)), elbow_out=1)
    e2 = m.solve_arm(ra, rf, rw, m.fk("sword", (gx - 0.6, low_hand[1], gz + 1.9)), elbow_out=1)
    m.ground([fl, fr])
    m.pose = {"head": [-3, 0, 0]}
    return m, max(e1, e2)


MODELS = {"sunforged_paladin_radiant_v2": radiant_v2}


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
