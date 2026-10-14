#!/usr/bin/env python3
"""The Paladin - Radiant, second pass (lovish picked Radiant, Oct 14 17:44): the Tabs-pioneered techniques
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
from gen_models import Model, pauldron, fauld, ring, blade, OUT  # noqa: E402

PALETTE = {
    # plate a step below white and its dark a real step down: the v2 render clipped #e9e4d8 to white under the key
    # light and every fluting line, lame and ridge vanished; the visor is dark iron so the glowing eyes carry the face
    "plate": "#d8d1c2", "plate_dark": "#a39b8a", "visor": "#3e3934", "gold": "#d4a63c", "gold_dark": "#a87f24",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c", "leather": "#5b3f2a", "steel": "#8c8f96", "dark": "#3b3630",
    "sun": "#ffd96a", "glow": "#fff1b0",
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
def torso(m):
    b = m.bone("body", [0, HIP, 0])
    # pelvis, belt, buckle
    b.cube([-WAIST_W / 2, HIP, -DEPTH / 2 + 0.5], [WAIST_W, WAIST_H, DEPTH - 1], "plate")
    b.cube([-WAIST_W / 2 - 0.4, CHEST_Y - 1.4, -DEPTH / 2 + 0.2], [WAIST_W + 0.8, 1.6, DEPTH - 0.4], "leather")
    b.cube([-1.1, CHEST_Y - 1.5, -DEPTH / 2 - 0.45], [2.2, 1.8, 0.7], "sun")
    # three abdominal lames, narrowest at the bottom: the waist under the chest
    for i in range(3):
        y = CHEST_Y + 0.2 + i * 1.25
        w = 6.6 + i * 0.9
        b.cube([-w / 2, y, -DEPTH / 2 - 0.25], [w, 1.4, DEPTH + 0.5], "plate_dark" if i % 2 else "plate")
    # chest: wider than the waist, deeper at the top (a breast, not a box)
    chest_lo = CHEST_Y + 3.8
    b.cube([-CHEST_W / 2, chest_lo, -DEPTH / 2], [CHEST_W, TOP - chest_lo, DEPTH], "plate")
    # V breastplate: two plates meeting at the centre line, outer ends lower
    pw = CHEST_W / 2 - 0.5
    for s in (1, -1):
        x0 = 0 if s > 0 else -pw
        b.cube([x0, chest_lo + 0.6, -DEPTH / 2 - 0.6], [pw, TOP - chest_lo - 2.0, 0.8], "plate", inflate=0.1,
               rotation=[0, 0, -s * 9], pivot=[0, TOP - 1.4, -DEPTH / 2 - 0.2])
        for fx in (1.5, 3.1):                                                                            # fluting
            b.cube([s * fx - 0.18, chest_lo + 1.3, -DEPTH / 2 - 0.92], [0.36, TOP - chest_lo - 3.4, 0.3], "plate_dark",
                   rotation=[0, 0, -s * 9], pivot=[0, TOP - 1.4, -DEPTH / 2 - 0.2])
    b.cube([-0.35, chest_lo + 0.4, -DEPTH / 2 - 0.85], [0.7, TOP - chest_lo - 1.6, 0.4], "gold")          # the keel
    # the sun emblem on the breast: a disc turned 45 with four short rays
    sc = (0, chest_lo + 3.4, -DEPTH / 2 - 1.05)
    b.box_at(sc, (1.9, 1.9, 0.5), "sun", rotation=[0, 0, 45], pivot=list(sc))
    for k in range(8):                                                                                   # eight rays
        a = k * math.pi / 4
        L = 1.4 if k % 2 == 0 else 0.9
        r = 1.2 + L / 2
        cx, cy = sc[0] + r * math.cos(a), sc[1] + r * math.sin(a)
        b.box_at((cx, cy, sc[2] + 0.1), (L, 0.4, 0.4), "gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, sc[2]])
    # back plate, gorget, lower chest trim
    b.cube([-CHEST_W / 2 + 0.8, chest_lo + 0.5, DEPTH / 2 - 0.25], [CHEST_W - 1.6, TOP - chest_lo - 1.5, 0.8], "plate", inflate=0.1)
    b.cube([-2.6, TOP - 1.0, -DEPTH / 2 + 0.2], [5.2, 2.0, DEPTH - 0.4], "gold")                             # gorget
    b.cube([-3.1, TOP - 1.6, -DEPTH / 2 + 0.0], [6.2, 0.7, DEPTH], "plate")                                  # its lower lame
    b.cube([-2.7, TOP + 0.85, -DEPTH / 2 + 0.1], [5.4, 0.35, DEPTH - 0.2], "gold_dark")                     # rolled edge
    for s in (1, -1):
        # besagews: a sun rondel hung before each armpit
        bc = (s * 5.0, TOP - 3.6, -DEPTH / 2 - 0.75)
        b.box_at(bc, (2.2, 2.2, 0.4), "gold", rotation=[0, 0, 45], pivot=list(bc))
        b.box_at((bc[0], bc[1], bc[2] - 0.25), (1.1, 1.1, 0.3), "sun", rotation=[0, 0, 45], pivot=list(bc))
        # haute-piece: the neck guard standing on each pauldron, gold-edged - the shoulder line's peak
        hp = (s * 4.7, TOP + 0.3, 0)
        b.cube([hp[0] - 0.35, hp[1], -2.6], [0.7, 2.6, 5.2], "plate", rotation=[0, 0, s * 12], pivot=list(hp))
        b.cube([hp[0] - 0.4, hp[1] + 2.5, -2.7], [0.8, 0.4, 5.4], "gold", rotation=[0, 0, s * 12], pivot=list(hp))
    b.cube([-CHEST_W / 2 - 0.2, chest_lo - 0.2, -DEPTH / 2 - 0.25], [CHEST_W + 0.4, 0.7, DEPTH + 0.5], "gold")
    # trapezius under the plate: the slope from the gorget down to each shoulder
    for s in (1, -1):
        b.cube([s * 3.6 - 2.3, TOP - 1.6, -DEPTH / 2 + 0.6], [4.6, 1.6, DEPTH - 1.2], "plate_dark",
               rotation=[0, 0, -s * 18], pivot=[s * 2.2, TOP - 0.6, 0])
    return b


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
def arm(m, side):
    """Rerebrace with a shoulder cap, couter with a fan, a vambrace narrowing to the wrist, a flared gauntlet cuff.
    The hand is NOT on the forearm: the gauntlet closes round the sword grip (grip_hand). Returns (upper, fore,
    wrist point in rest space)."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, TOP - 0.6
    x0 = sx - ARM_W / 2
    elbow_y = sy - UPPER
    wrist_y = elbow_y - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent="body")
    up.cube([x0, elbow_y + 0.5, -ARM_D / 2], [ARM_W, UPPER - 0.5, ARM_D], "plate")                         # rerebrace
    up.cube([x0 - 0.4, sy - 2.6, -ARM_D / 2 - 0.4], [ARM_W + 0.8, 3.0, ARM_D + 0.8], "plate_dark")         # shoulder cap
    up.cube([x0 + 0.2, elbow_y + 2.0, -ARM_D / 2 - 0.3], [ARM_W - 0.4, 1.0, ARM_D + 0.6], "leather")      # strap
    up.cube([x0 + 1.0, elbow_y + 2.0, -ARM_D / 2 - 0.45], [0.6, 0.6, 0.3], "gold")                        # its buckle
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    fo.cube([x0 - 0.3, elbow_y - 1.8, -ARM_D / 2 - 0.3], [ARM_W + 0.6, 3.0, ARM_D + 0.6], "plate", inflate=0.15)   # couter
    ox = x0 + ARM_W + 0.1 if side > 0 else x0 - 1.0                                                     # its fan, outside
    fo.cube([ox, elbow_y - 1.6, -1.4], [0.9, 2.8, 2.8], "gold", rotation=[0, 0, side * 12],
            pivot=[ox + 0.45, elbow_y, 0])
    fo.cube([x0 + 0.1, elbow_y - FORE * 0.55, -ARM_D / 2 + 0.05], [ARM_W - 0.2, FORE * 0.55 - 1.4, ARM_D - 0.1], "plate")
    fo.cube([x0 + 0.4, wrist_y + 1.4, -ARM_D / 2 + 0.35], [ARM_W - 0.8, FORE * 0.45 - 1.4, ARM_D - 0.7], "plate")  # tapers
    fo.cube([x0 + 0.3, wrist_y + 1.6, -ARM_D / 2 + 0.1], [ARM_W - 0.6, 1.6, 0.4], "gold")                  # vambrace edge
    fo.cube([sx - 0.2, wrist_y + 3.2, -ARM_D / 2 - 0.1], [0.4, FORE * 0.55, 0.3], "plate_dark")           # its ridge
    cx_ = x0 + ARM_W + 0.05 if side > 0 else x0 - 0.6                                                    # cuff point
    fo.cube([cx_, wrist_y + 0.8, -1.2], [0.55, 2.4, 2.4], "plate_dark", rotation=[0, 0, -side * 10],
            pivot=[cx_ + 0.27, wrist_y + 0.8, 0])
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
    h.cube([-hw / 2, hy + 2.4, -hd / 2], [hw, 4.8, hd], "plate")                                          # skull
    h.cube([-hw / 2 + 0.5, hy + 7.0, -hd / 2 + 0.6], [hw - 1.0, 0.8, hd - 1.2], "plate")                  # dome
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
    h.cube([-0.3, hy + 5.3, -hd / 2 - 0.25], [0.6, 2.4, 0.5], "gold")                                     # the rib, brow to crest
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
    """Long, slim, the digitigrade hint (knee forward, ankle back); cuisse, a knee cop with a side wing, greave,
    lamed sabaton."""
    pre = "left" if side > 0 else "right"
    hx = side * 3.0
    w, d = 3.8, 4.2
    x0 = hx - w / 2
    TH, SH = 10.0, 10.0
    knee_y, ankle_y = HIP - TH, HIP - TH - SH
    th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh_rot)
    th.cube([x0, knee_y, -d / 2], [w, TH, d], "plate")
    th.cube([x0 + 0.3, knee_y + 2, -d / 2 - 0.4], [w - 0.6, TH - 3, 0.9], "plate", inflate=0.15)          # cuisse
    th.cube([x0 - 0.1, HIP - 1.6, -d / 2 - 0.2], [w + 0.2, 1.2, d + 0.4], "leather")
    th.cube([hx - 0.2, knee_y + 2.6, -d / 2 - 0.7], [0.4, TH - 4.2, 0.3], "plate_dark")                  # cuisse ridge
    for k in range(2):                                                                                    # tassets
        ty = HIP - 0.4 - k * 2.0
        th.cube([x0 + 0.05, ty - 2.4, -d / 2 - 1.0 - k * 0.12], [w - 0.1, 2.4, 0.5], "plate",
                rotation=[8, 0, 0], pivot=[hx, ty, -d / 2 - 0.8])
        th.cube([x0 + 0.05, ty - 2.4, -d / 2 - 1.1 - k * 0.12], [w - 0.1, 0.45, 0.6], "gold" if k == 1 else "plate_dark",
                rotation=[8, 0, 0], pivot=[hx, ty, -d / 2 - 0.8])
    sh = m.bone(pre + "_shin", [hx, knee_y, 0], parent=pre + "_thigh", rotation=shin_rot)
    sh.cube([x0 + 0.2, ankle_y, -d / 2 + 0.2], [w - 0.4, SH, d - 0.4], "plate")
    sh.cube([x0 + 0.4, ankle_y + 1, -d / 2 - 0.25], [w - 0.8, SH - 2.5, 0.8], "plate", inflate=0.1)       # greave
    sh.cube([x0 - 0.25, knee_y - 1.8, -d / 2 - 0.5], [w + 0.5, 3.4, d + 0.6], "plate", inflate=0.25)      # knee cop
    wx = x0 + w + 0.1 if side > 0 else x0 - 1.0
    sh.cube([wx, knee_y - 1.4, -1.5], [0.9, 2.8, 3.0], "gold", rotation=[0, 0, side * 14],
            pivot=[wx + 0.45, knee_y, 0])                                                                 # its wing
    sh.cube([x0 + w / 2 - 0.5, knee_y - 0.2, -d / 2 - 1.0], [1, 1.2, 0.8], "gold")
    sh.cube([hx - 0.2, ankle_y + 1.3, -d / 2 - 0.55], [0.4, SH - 3.6, 0.35], "plate_dark")               # greave keel
    ft = m.bone(pre + "_foot", [hx, ankle_y, 0], parent=pre + "_shin", rotation=foot_rot)
    fl = 6.5
    ft.cube([x0 - 0.3, ankle_y - 2.2, -fl + d / 2], [w + 0.6, 2.2, fl], "plate")
    for k in range(3):                                                                                    # sabaton lames
        ft.cube([x0 - 0.35, ankle_y - 1.0 - k * 0.15, -fl + d / 2 + 0.6 + k * 1.3], [w + 0.7, 0.35, 1.2],
                "plate_dark")
    ft.cube([x0 - 0.3, ankle_y - 2.2, -fl + d / 2 - 0.3], [w + 0.6, 1.2, 1.3], "gold")                    # toe cap
    ft.cube([hx - 0.8, ankle_y - 2.2, -fl + d / 2 - 1.6], [1.6, 0.9, 1.4], "gold")                        # the point
    return pre + "_foot"


# ------------------------------------------------------------------ the whole -------------------------------------
def radiant_v2():
    m = Model("Paladin - Radiant v2", "sunforged", "tank (Radiant, second pass)", PALETTE, EMISSIVE)
    body = torso(m)
    fauld(body, HIP + 0.3, 6.6, DEPTH / 2 - 0.5, 3, "plate", flare0=10, flare_step=10, widen=1.6)
    tabard(m)
    cape(m, flow=10)
    for s in (1, -1):
        pauldron(body, s, (s * 5.6, TOP + 0.5, 0), 4, 6.4, 6.8, 8, 12, "plate", trim="gold", thick=1.25)
        # the top lame enamelled in team colour: the shoulders are the biggest surfaces the RTS camera sees from
        # above (the top view showed white plate and gold everywhere, team colour only on the cape's upper edge)
        w0, x0 = 6.4, (s * 5.6 if s > 0 else s * 5.6 - 6.4)
        body.cube([x0, TOP + 0.5 - 1.25, -3.4], [w0, 1.25, 6.8], "tabard", inflate=0.06,
                  rotation=[0, 0, -s * 8], pivot=[s * 5.6, TOP + 0.5, 0])
    sash_and_relic(m)
    head, hy = helm(m)
    # the halo on its own bone (the animation layer turns it slowly), tipped forward 12 degrees so the RTS camera,
    # looking down, sees more of it than an edge; a crown of rays alternating long and short
    hc = (0, hy + 3.8, 6.4)
    halo = m.bone("halo", list(hc), parent="head", rotation=[-12, 0, 0])
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
    sword = m.bone("sword", [gx, 28.0, gz], parent="body", rotation=[0, 0, 8])
    sword.cube([gx - 0.6, 22.6, gz - 0.6], [1.2, 9.4, 1.2], "leather")                                   # grip
    for k in range(4):                                                                                    # its wrap
        sword.cube([gx - 0.65, 23.4 + k * 2.0, gz - 0.65], [1.3, 0.35, 1.3], "gold_dark")
    sword.cube([gx - 1.2, 21.2, gz - 1.2], [2.4, 1.8, 2.4], "gold")                                      # pommel
    sword.box_at((gx, 20.9, gz), (1.2, 1.2, 1.2), "sun", rotation=[0, 0, 45], pivot=[gx, 20.9, gz])
    sword.cube([gx - 4.8, 32.0, gz - 0.9], [9.6, 1.5, 1.8], "gold")                                      # crossguard
    for s in (1, -1):
        x0 = gx + 3.6 if s > 0 else gx - 5.4
        sword.cube([x0, 32.3, gz - 0.7], [1.8, 1.2, 1.4], "gold", rotation=[0, 0, s * 35], pivot=[gx + s * 4.5, 32.9, gz])
    sword.box_at((gx, 32.75, gz - 0.25), (2.0, 2.0, 1.4), "sun", rotation=[0, 0, 45], pivot=[gx, 32.75, gz])
    blade(sword, (gx, 33.5, gz), 20.5, (3.4, 3.0, 2.5, 1.9), 1.5, "glow", tip_color="sun", fuller="gold")
    sword.cube([gx - 1.9, 33.3, gz - 0.85], [3.8, 0.5, 1.7], "gold")                                     # blade collar
    for k in range(5):                                                                                    # sunburst at the ricasso
        a = math.radians(40 + k * 25)
        r = 1.6
        cx, cy = gx + r * math.cos(a), 33.6 + r * math.sin(a)
        sword.box_at((cx, cy, gz - 0.95), (2.0, 0.35, 0.3), "gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, gz - 0.95])
    for yy in (38.0, 43.0, 48.0):                                                                         # suns in the fuller
        sword.box_at((gx, yy, gz - 0.92), (0.9, 0.9, 0.3), "sun", rotation=[0, 0, 45], pivot=[gx, yy, gz - 0.92])
        sword.box_at((gx, yy, gz + 0.92), (0.9, 0.9, 0.3), "sun", rotation=[0, 0, 45], pivot=[gx, yy, gz + 0.92])
    top_hand, low_hand = (gx, 29.6, gz), (gx, 26.4, gz)
    grip_hand(sword, top_hand, 1)       # the left hand above
    grip_hand(sword, low_hand, -1)      # the right below
    la, lf, lw = arm(m, 1)
    ra, rf, rw = arm(m, -1)
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
