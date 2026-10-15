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
    # the art director's glow budget (art-theory section 15): light only in the halo and the sword's fuller - every
    # other sun emblem is bright gold paint, not light (the 85 glow cubes speckled at far zoom)
    "sun_gold": "#f2cb57",
    # fifth pass: the blade is steel with light in its fuller (the all-glow blade read as a lightsaber)
    "steel_bright": "#a3aab6", "steel_edge": "#d0d6df",
    # the winged option (his angel ref #1: an armoured angel, huge folded wings, greatsword planted, head bowed)
    "feather": "#ebe3d2", "feather_mid": "#d3c9b4", "feather_dark": "#b4a991",
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
# Sixth pass (from the v2.4 render): the torso is a few PLATES, not eleven slices. The slices read as a stack of
# pancakes from the side - every step a line - which is the opposite of armour that blends. Real plate is a handful
# of big curved pieces that overlap: so a waist, an abdomen, a breastplate in two (its lower taper and the full chest),
# the top under the gorget - each an octagon in section (two boxes: full width with the depth trimmed by the chamfer,
# full depth with the width trimmed), a swell on the chest, and a gothic plackart rising over the breastplate's lower
# edge to a point. Few edges, each one a real plate edge. (y0, y1, width, depth, chamfer)
PLATES = ((24.0, 26.4, 6.6, 5.4, 0.5),     # waist
          (26.2, 28.8, 7.6, 6.0, 0.6),     # abdomen
          (28.4, 30.6, 9.4, 6.6, 0.7),     # breastplate: lower taper
          (30.4, 35.0, 10.8, 7.2, 0.8),    # breastplate: the chest
          (34.8, TOP + 0.4, 9.6, 5.8, 0.7))  # the top of the chest, under the gorget
FRONT, BACK = 0.57, 0.43       # how the depth splits: the chest pushes forward


def torso_slice(y):
    """(width, depth, front z, back z) of the torso at height y (the plate that covers it; the upper one where two
    overlap)."""
    k = 0
    for n, (y0, y1, w, d, ch) in enumerate(PLATES):
        if y >= y0:
            k = n
    _, _, w, d, _ = PLATES[k]
    return w, d, -d * FRONT, d * BACK


def octagon(b, y0, y1, w, d, ch, color, zf=None):
    """A box with its four vertical edges chamfered by ch: two boxes."""
    zf = -d * FRONT if zf is None else zf
    b.cube([-w / 2, y0, zf + ch], [w, y1 - y0, d - 2 * ch], color)
    b.cube([-w / 2 + ch, y0, zf], [w - 2 * ch, y1 - y0, d], color)


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    # pelvis under the faulds, belt, buckle
    b.cube([-3.2, HIP, -2.6], [6.4, 2.6, 5.0], "plate")
    for (y0, y1, w, d, ch) in PLATES:
        octagon(b, y0, y1, w, d, ch, "plate")
    # the swell of the chest (gothic plate is fullest high on the breast)
    zc = -7.2 * FRONT
    b.cube([-3.6, 30.9, zc - 0.45], [7.2, 3.7, 2.0], "plate")
    b.cube([-2.6, 31.3, zc - 0.75], [5.2, 2.9, 1.0], "plate")
    # the plackart: a plate over the abdomen rising to a point over the breastplate's lower edge
    za = -6.0 * FRONT
    b.cube([-2.9, 26.0, za - 0.6], [5.8, 2.6, 1.0], "plate")
    b.box_at((0, 28.3, za - 0.45), (2.6, 2.6, 0.8), "plate", rotation=[0, 0, 45], pivot=[0, 28.3, za - 0.45])
    b.cube([-2.95, 25.95, za - 0.65], [5.9, 0.3, 1.1], "plate_dark")                                     # its lower edge
    b.cube([-3.6, CHEST_Y - 1.5, -3.25], [7.2, 1.6, 6.5], "leather")                                     # belt
    b.cube([-1.1, CHEST_Y - 1.6, -3.6], [2.2, 1.8, 0.6], "sun_gold")                                     # buckle
    # the sun on the breast, on the fullest slice: one bold disc (a square and the same square turned 45 - an
    # octagon), paint not light; the eight thin rays round it went (trim merged into fewer, larger pieces - 60/30/10)
    ey = 32.6
    sc = (0, ey, zc - 0.75 - 0.3)                                                    # on the swell
    b.box_at(sc, (2.4, 2.4, 0.5), "sun_gold", rotation=[0, 0, 45], pivot=list(sc))
    b.box_at((sc[0], sc[1], sc[2] + 0.05), (2.4, 2.4, 0.45), "gold")
    # gorget: two lames and a rolled edge, rising out of the top slice
    b.cube([-3.3, TOP - 0.9, -2.9], [6.6, 0.8, 5.4], "plate")
    b.cube([-2.7, TOP - 0.2, -2.6], [5.4, 1.4, 4.8], "gold")
    b.cube([-2.8, TOP + 1.1, -2.7], [5.6, 0.35, 5.0], "gold_dark")
    for s_ in (1, -1):
        # besagews hung before the armpits, on the chest's surface
        _, _, zf, _ = torso_slice(32.4)
        bc = (s_ * 5.0, 32.4, zf - 0.3)
        b.box_at(bc, (2.2, 2.2, 0.4), "gold", rotation=[0, 0, 45], pivot=list(bc))
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
    curved; the top TWO lames enamelled in team colour (what the RTS camera sees from above; the art director's
    20% team-colour mark), a gilt rolled edge on the
    lowest lame only (ornament on few plates; the gold rib over the top lame went in the art director's round)."""
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
            team = i < 2
            body.cube([x0, y0, sz - d / 2], [w, thick, d], "tabard" if team else "plate", rotation=rot, pivot=piv)
            body.cube([x0 + (0.0 if side > 0 else 0.6), y0 - 0.2, sz - d / 2 + 0.6], [w - 0.6, thick + 0.4, d - 1.2],
                      "tabard_dark" if team else "plate", rotation=rot, pivot=piv)
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
    rel.box_at((-5.6, by + 1.3, -1.45), (0.8, 0.8, 0.2), "sun_gold", rotation=[0, 0, 45], pivot=[-5.6, by + 1.3, -1.45])


def tabard(m):
    """Team colour front and back: two chained segments each (the animation layer swings them), gold hems down both
    edges and along the bottom, the front one carrying a sun - the largest single colour on the body. The art
    director's round (art-theory section 15) widened the FRONT to an A-line nearly twice the old panel (team colour
    was 4-8% of the far read; the target is 20%), with two darker folds so the colour is shaded cloth, not flat neon;
    the back stays narrower - the wings hang over it."""
    top = CHEST_Y - 0.2
    for side, z, sign, (w1, w2), (l1, l2) in (("front", -DEPTH / 2 - 0.95, 1, (8.6, 10.4), (6.5, 7.0)),
                                              ("back", DEPTH / 2 + 0.45, -1, (6.4, 7.2), (6.5, 6.6))):
        out = -0.08 if sign > 0 else 0.0           # trim stands proud of the cloth on its outer face
        b1 = m.bone("tabard_%s_0" % side, [0, top, z + 0.25], parent="body", rotation=[sign * 6, 0, 0])
        b1.cube([-w1 / 2, top - l1, z], [w1, l1, 0.5], "tabard")
        for e in (-w1 / 2, w1 / 2 - 0.5):
            b1.cube([e, top - l1, z + out], [0.5, l1, 0.58], "gold")                                         # hems
        b1.cube([-w1 / 2 - 0.2, top - 0.9, z - 0.1], [w1 + 0.4, 0.9, 0.7], "gold")                           # under the belt
        y2 = top - l1
        b2 = m.bone("tabard_%s_1" % side, [0, y2, z + 0.25], parent=b1.name, rotation=[sign * 5, 0, 0])
        b2.cube([-w2 / 2, y2 - l2, z], [w2, l2, 0.5], "tabard")
        for e in (-w2 / 2, w2 / 2 - 0.5):
            b2.cube([e, y2 - l2, z + out], [0.5, l2, 0.58], "gold")
        b2.cube([-w2 / 2 - 0.1, y2 - l2, z - 0.1], [w2 + 0.2, 0.7, 0.7], "gold")                             # hem
        b2.cube([-0.25, y2 - l2 - 0.6, z - 0.05], [0.5, 1.2, 0.6], "tabard_dark")                           # a centre notch
        if side == "front":
            for fx in (-w2 * 0.27, w2 * 0.27):                                                               # two folds
                b2.cube([fx - 0.35, y2 - l2 + 0.7, z - 0.03], [0.7, l2 - 0.7, 0.5], "tabard_dark")
            c = (0, top - 5.0, z - 0.3)
            b1.box_at(c, (3.0, 3.0, 0.4), "sun_gold", rotation=[0, 0, 45], pivot=list(c))
            b1.box_at((c[0], c[1], c[2] + 0.05), (4.4, 0.6, 0.35), "gold")
            b1.box_at((c[0], c[1], c[2] + 0.05), (0.6, 4.4, 0.35), "gold")


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
        for s in (1, -1):                                                                                 # gold at the edges
            seg.cube([s * (w / 2 - 0.25) - 0.25, y - L + 0.2, z0 - 0.38], [0.5, L - 0.2, 0.35], "gold")
        if i == 0:
            seg.cube([-w / 2 - 0.4, y - 1.6, z0 - 0.4], [w + 0.8, 1.6, 1.2], "gold")                       # clasp bar
            for s in (1, -1):
                seg.box_at((s * (w / 2 - 0.6), y - 0.8, z0 - 0.7), (1.6, 1.6, 0.6), "sun_gold", rotation=[0, 0, 45],
                           pivot=[s * (w / 2 - 0.6), y - 0.8, z0 - 0.7])
        if i == 1:
            # the sun of the Sunforged on the cape's back: what the RTS camera sees from above and behind
            ec = (0, y - L / 2 + 0.6, z0 + 0.62)
            seg.box_at(ec, (2.6, 2.6, 0.3), "sun_gold", rotation=[0, 0, 45], pivot=list(ec))
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


def wings(m, root_y=33.6):
    """Folded wings for the winged option (lovish's angel ref #1, Oct 14 17:59: "huge folded wings ... wings taller
    than the body frame the figure"). Each wing is its own bone ("wing_left" / "wing_right", which the game breathes
    and opens) whose plane is turned 40 degrees (outer edge back) and tipped back a little, so it shows from the front,
    the side, behind and from the RTS camera.

    Third pass (the art director, art-theory section 15: "flat grey boards from above, read as doors"): the wing is an
    ARM and a FAN, not a board. The arm is the leading edge - three segments rising from the shoulder blade to a wrist
    above and outside the shoulder (the two wrists frame the halo) and on past it - dyed a 3.2 px band of team colour
    with a gold hem along its top (what the RTS camera sees first) and a row of coverts under it in the darker team
    colour (the team-colour mark: >= 20% of the far read). From the arm hang
    four broad feathers, each tapering in two steps to a tip, that overlap under the coverts and part lower down,
    fanned from 3 degrees in to 19 degrees out (the outline widens down and out, the daylight between the feathers
    opens toward the tips), cream both sides (the light alone models them - a darker inner twin read taupe in
    the game's lighting). The whole plane
    tips back 20 degrees so the outer face catches the top light. Golem Smithing lights the three outer tips
    (sun_ward)."""
    ARC = ((0.3, -0.6), (3.6, 8.6), (7.9, 13.0), (10.2, 11.2))      # (u out from the root, v up) along the leading edge
    BAND, HEM = 3.2, 0.55
    TILT = -20     # the plane tipped back: its outer face looks up into Minecraft's top light (cream, not taupe) and
    #               the RTS camera sees the two wings as a V of feathers, not two edges

    def arc_v(u):
        for (u0, v0), (u1, v1) in zip(ARC, ARC[1:]):
            if u <= u1:
                return v0 + (v1 - v0) * (u - u0) / (u1 - u0)
        return ARC[-1][1]

    # four broad primaries (u of the attachment, v of the tip, fan degrees out from straight down): they overlap
    # under the coverts and part a few px lower, so the daylight between them opens toward the tips
    SLATS = ((1.6, -10.0, -3), (4.2, -14.5, 5), (6.8, -19.0, 12), (9.3, -23.0, 19))
    WIDTHS = (3.3, 2.6, 1.5)                                     # upper vane, lower vane, tip
    SPLIT = (0.5, 0.42, 0.08)                                    # their shares of the length
    for side in (1, -1):
        pre = "wing_left" if side > 0 else "wing_right"
        rx, ry, rz = side * 2.6, root_y, 3.6
        wg = m.bone(pre, [rx, ry, rz], parent="body", rotation=[TILT, -side * 40, 0])
        P = lambda u, v: (rx + side * u, ry + v)
        # the slats first (the arm and coverts overlap their tops)
        for k, (u, vb, fan) in enumerate(SLATS):
            tx, ty = P(u, arc_v(u) - 1.4)
            a = math.radians(fan)
            L = (ty - (ry + vb)) / math.cos(a)
            dx, dy = side * math.sin(a), -math.cos(a)
            dz = 0.12 if k % 2 == 0 else -0.12                   # alternate depth: every edge catches the light
            rot = [0, 0, side * fan]
            d0 = 0.0
            for n, (w, f) in enumerate(zip(WIDTHS, SPLIT)):
                seg = L * f + (0.3 if n < 2 else 0.0)            # overlap the next piece
                cx, cy = tx + dx * (d0 + seg / 2), ty + dy * (d0 + seg / 2)
                # one cube, cream both faces: the light models the two sides (a darker twin read taupe in game)
                wg.box_at((cx, cy, rz + dz), (w, seg, 0.7), "feather", rotation=rot, pivot=[cx, cy, rz + dz])
                d0 += L * f
            if k >= 1:
                ex, ey = tx + dx * (L - 1.6), ty + dy * (L - 1.6)
                wg.box_at((ex, ey, rz + 0.42 + dz), (1.0, 3.0, 0.15), "glow", rotation=rot,
                          pivot=[ex, ey, rz + 0.42 + dz], upgrade="sun_ward")
        # the arm: team band, gold hem on its upper edge, cream coverts below - one rotated piece per segment
        for n, ((u0, v0), (u1, v1)) in enumerate(zip(ARC, ARC[1:])):
            (x0, y0), (x1, y1) = P(u0, v0), P(u1, v1)
            ang = math.atan2(y1 - y0, x1 - x0)
            nx, ny = -math.sin(ang), math.cos(ang)
            if ny < 0:
                nx, ny = -nx, -ny                                # the normal that points up
            L = math.hypot(x1 - x0, y1 - y0) + 0.8               # overlap the next segment at the joint
            mx, my = (x0 + x1) / 2, (y0 + y1) / 2
            rot = [0, 0, math.degrees(ang)]
            wg.box_at((mx, my, rz + 0.1), (L, BAND, 1.0), "tabard", rotation=rot, pivot=[mx, my, rz + 0.1])
            hx, hy = mx + nx * (BAND / 2 + HEM / 2 - 0.05), my + ny * (BAND / 2 + HEM / 2 - 0.05)
            wg.box_at((hx, hy, rz + 0.1), (L - 0.2, HEM, 1.1), "gold", rotation=rot, pivot=[hx, hy, rz + 0.1])
            if n < 2:
                cvx, cvy = mx - nx * (BAND / 2 + 1.3), my - ny * (BAND / 2 + 1.3)
                wg.box_at((cvx, cvy, rz + 0.55), (L - 0.6, 3.2, 0.45), "tabard_dark", rotation=rot, pivot=[cvx, cvy, rz + 0.55])
        # the wrist: a gilded knuckle at the top of the arc
        wx, wy = P(*ARC[2])
        wg.box_at((wx, wy + 0.1, rz + 0.1), (1.6, 1.6, 1.2), "gold", rotation=[0, 0, 45], pivot=[wx, wy + 0.1, rz + 0.1])


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
    # sixth pass: three chamfered tiers, not six slices (the slices read as a ziggurat): the skull, its shoulder,
    # the crown; the gold rib runs up the front and over the crown to the crest
    DOME = ((2.4, 6.4, 6.0, 7.0, 0.9), (6.2, 7.8, 5.0, 5.8, 0.9), (7.6, 8.4, 3.4, 4.0, 0.6))
    for (y0, y1, w, d, ch) in DOME:
        octagon(h, hy + y0, hy + y1, w, d, ch, "plate", zf=-d / 2)
    for (y0, y1, w, d, ch) in DOME[1:]:
        h.cube([-0.3, hy + y0, -d / 2 - 0.2], [0.6, y1 - y0, 0.3], "gold")
    h.cube([-0.3, hy + 8.4, -2.0], [0.6, 0.25, 4.0], "gold")
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
def leg(m, side, thigh_rot, shin_rot, foot_rot=None, toe_out=8):
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
    # the foot (Oct 15, lovish: "the feet look odd" - a 4.4-wide flat slab under a 2.6 ankle, toes locked straight
    # ahead): LEVEL in the posed model and turned out toe_out degrees (Model.level_rotation) unless a pose gives
    # foot_rot; a sabaton built like a foot - the ankle narrow, a heel behind the leg, lames stepping DOWN the instep
    # to a long gothic toe that narrows to its point, gold on the toe cap only, a dark sole a hair wider all round
    rot = foot_rot if foot_rot is not None else m.level_rotation(pre + "_shin", yaw=-side * toe_out)
    ft = m.bone(pre + "_foot", [hx, ankle_y, 0], parent=pre + "_shin", rotation=rot)
    g = ankle_y - 2.2                                                                                     # the ground
    ft.cube([hx - 1.3, ankle_y - 0.7, -1.3], [2.6, 1.0, 2.6], "plate_dark")                               # the ankle joint
    ft.cube([hx - 1.6, g + 0.35, -1.7], [3.2, 1.75, 3.2], "plate")                                        # instep over the heel
    ft.cube([hx - 1.5, g + 0.35, 1.2], [3.0, 1.3, 0.9], "plate")                                          # heel, behind the leg
    for k, (z0, L, h, w) in enumerate(((-3.0, 1.5, 1.45, 3.1), (-4.3, 1.5, 1.1, 2.8))):                   # lames, stepping down
        ft.cube([hx - w / 2, g + 0.35, z0], [w, h, L], "plate")
    ft.cube([hx - 1.3, g + 0.35, -5.4], [2.6, 0.85, 1.25], "gold")                                         # toe cap
    ft.cube([hx - 0.75, g + 0.35, -6.3], [1.5, 0.6, 1.0], "plate")                                         # the point
    ft.cube([hx - 1.75, g, -6.4], [3.5, 0.4, 8.6], "dark")                                                 # the sole
    return pre + "_foot", pre + "_shin"


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
    # grip: leather wrapped in wire, a ferrule at each end
    b.cube([gx - 0.6, 22.6, gz - 0.6], [1.2, G - 22.6, 1.2], "leather")
    for k in range(9):
        b.cube([gx - 0.66, 23.4 + k * 0.95, gz - 0.66], [1.32, 0.22, 1.32], "gold_dark")
    for yy in (22.5, G - 0.5):
        b.cube([gx - 0.78, yy, gz - 0.78], [1.56, 0.5, 1.56], "gold")
    # the quillon block, a sun on each face
    b.cube([gx - 1.3, G - 0.1, gz - 1.1], [2.6, 2.0, 2.2], "gold")
    for zz in (gz - 1.25, gz + 1.25):
        b.box_at((gx, G + 0.9, zz), (1.5, 1.5, 0.3), "sun_gold", rotation=[0, 0, 45], pivot=[gx, G + 0.9, zz])
    # quillons: three segments each side curving up, then a diamond finial
    for side in (1, -1):
        px, py = gx + side * 1.3, G + 0.9
        for L, t, dd, a in ((1.9, 1.0, 1.4, 0), (1.7, 0.9, 1.3, 14), (1.4, 0.8, 1.2, 32)):
            x0 = px if side > 0 else px - L
            b.cube([x0, py - t / 2, gz - dd / 2], [L, t, dd], "gold", rotation=[0, 0, side * a], pivot=[px, py, gz])
            px += side * L * math.cos(math.radians(a))
            py += L * math.sin(math.radians(a))
        b.box_at((px + side * 0.3, py + 0.3, gz), (1.0, 1.0, 1.0), "sun_gold", rotation=[0, 0, 45], pivot=[px + side * 0.3, py + 0.3, gz])
    # ricasso: gold, engraved with a sunburst rising off the guard
    R0, R1 = G + 1.9, G + 4.0
    b.cube([gx - 1.1, R0, gz - 0.5], [2.2, R1 - R0, 1.0], "gold")
    for k in range(3):
        a = math.radians(50 + k * 40)
        cx, cy = gx + 1.0 * math.cos(a), R0 + 0.2 + 1.0 * math.sin(a)
        b.box_at((cx, cy, gz - 0.6), (1.3, 0.3, 0.2), "sun_gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, gz - 0.6])
        b.box_at((cx, cy, gz + 0.6), (1.3, 0.3, 0.2), "sun_gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, gz + 0.6])
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
    m = Model("Paladin - Radiant v2", "sunforged", "tank (Radiant, sixth pass)", PALETTE, EMISSIVE)
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
    fl = leg(m, -1, [16, 0, 0], [-30, 0, 0])
    fr = leg(m, 1, [6, 0, 0], [-18, 0, 0])
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
    e1 = m.solve_arm(la, lf, lw, m.fk("sword", (gx + 0.6, top_hand[1], gz + 1.9)), elbow_out=1, elbow_down=1)
    e2 = m.solve_arm(ra, rf, rw, m.fk("sword", (gx - 0.6, low_hand[1], gz + 1.9)), elbow_out=1, elbow_down=1)
    for ft, shin in (fl, fr):
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-3, 0, 0]}
    return m, max(e1, e2)


def radiant_winged():
    """The winged option (sixth pass): his angel ref #1 read literally - an armoured angel, the halo kept (the Radiant
    he picked is "the halo one"), huge folded wings instead of the cape, the greatsword PLANTED point-down before him
    with both gauntlets on the grip under the pommel, head bowed: the solemn guardian at rest (the pose library's
    "hands on the pommel", shared with the Spartan heavy). Same body, helm, legs, pauldrons, tabard, relic."""
    m = Model("Paladin - Radiant, winged (option)", "sunforged", "tank (Radiant, winged option, sixth pass)", PALETTE,
              EMISSIVE)
    body = torso(m)
    fauld(body, HIP + 0.3, 6.6, DEPTH / 2 - 0.5, 3, "plate", flare0=10, flare_step=10, widen=1.6)
    tabard(m)
    wings(m)
    pauldrons(body)
    sash_and_relic(m)
    head, hy = helm(m)
    # 8.4 behind the helm (at 7.2 the ring cut both wings' leading edges by 1.0-1.35 px, Oct 15 review)
    hc = (0, hy + 3.8, 8.4)
    m.bone("halo", list(hc), parent="head", rotation=[-12, 0, 0])
    halo = m.bone("halo_ring", list(hc), parent="halo")
    ring(halo, hc, 8.6, 12, 1.0, 0.5, "sun")
    ring(halo, hc, 5.6, 12, 0.7, 0.4, "glow", upgrade="sun_ward")
    for k in range(12):
        a = k * math.pi / 6 + math.pi / 12
        L = 2.4 if k % 2 == 0 else 1.3
        r = 9.1 + L / 2
        cx, cy = hc[0] + r * math.cos(a), hc[1] + r * math.sin(a)
        halo.box_at((cx, cy, hc[2]), (L, 0.55, 0.45), "sun", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, hc[2]])
    fl = leg(m, -1, [10, 0, -2], [-16, 0, 0])
    fr = leg(m, 1, [2, 0, 2], [-8, 0, 0])
    # the sword planted point-down on the centre line before him: built upright, then turned 180 about x round a
    # pivot that puts the point 2 px into the ground
    gx, gz = 0.0, -8.6
    m.bone("arms", [0, TOP - 0.6, 0], parent="body")
    tip_y = 32.0 + 4.0 + 5 * 3.4 + 2.7          # G + ricasso + five sections + the point (greatsword)
    py = (tip_y - 2.0) / 2
    sword = m.bone("sword", [gx, py, gz], parent="arms", rotation=[180, 0, 0])
    greatsword(sword, gx, gz)
    # both gauntlets on the grip just under the pommel - on their own bone beside the sword's (a gauntlet built on
    # the turned bone would turn with it and face the wrong way); the grip runs 22.6..32 before the turn
    flip = lambda y: 2 * py - y
    top_hand, low_hand = (gx, flip(23.9), gz), (gx, flip(26.6), gz)
    hands = m.bone("hands", [0, TOP - 0.6, 0], parent="arms")
    # intended contact: the gauntlets close round the grip and meet the vambraces; the fauld lames lie over the thighs
    m.contact_ok += [("hands", "sword"), ("hands", "left_fore"), ("hands", "right_fore"), ("body", "left_thigh"),
                     ("body", "right_thigh")]
    grip_hand(hands, top_hand, 1)
    grip_hand(hands, low_hand, -1)
    la, lf, lw = arm(m, 1, parent="arms")
    ra, rf, rw = arm(m, -1, parent="arms")
    clear = dict(avoid=("body", "tabard_front_0"), avoid_margin=1.5)       # the vambraces are 3.2 px across
    e1 = m.solve_arm(la, lf, lw, m.fk("hands", (gx + 0.6, top_hand[1], gz + 1.9)), elbow_out=1, elbow_down=1,
                     point_at=m.fk("hands", top_hand), **clear)
    e2 = m.solve_arm(ra, rf, rw, m.fk("hands", (gx - 0.6, low_hand[1], gz + 1.9)), elbow_out=1, elbow_down=1,
                     point_at=m.fk("hands", low_hand), **clear)
    for ft, shin in (fl, fr):
        m.ground_leg(ft, also=(shin,))
    m.pose = {"head": [-14, 0, 0]}
    return m, max(e1, e2)


MODELS = {"sunforged_paladin_radiant_v2": radiant_v2, "sunforged_paladin_radiant_winged": radiant_winged}


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
