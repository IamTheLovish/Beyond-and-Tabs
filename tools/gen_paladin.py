#!/usr/bin/env python3
"""The Paladin - Radiant, second pass (fourth round of looking: sliced forms, a real sword - see the functions) (lovish picked Radiant, Oct 14 17:44): the Tabs-pioneered techniques
(claude/avatar.md) brought onto the chosen direction, inside THE HUMAN DIAL's Sunforged band (humanoid, not human:
taller, longer shins, smaller head, bigger shoulders, NO face) - so the helm is the face:

- anatomy under the plate: a V torso (a gothic breastplate on a keel over a narrow waist, a plackart, a turned
  gorget), rerebraces, couters turned round the elbow's hinge, vambraces to a flared gauntlet cuff; long legs with
  the digitigrade hint, knee cops turned round the knee, sabatons that slope to a rounded toe (round 3, Oct 16:
  the legs TURNED plate - see leg());
- hands that grip: each gauntlet closes round the greatsword's grip (gen_models.grip_hand: four fingers in three
  lames wrapping the grip, knuckle plates, the thumb over the index finger) and the arm is solved onto its cuff;
- a CLOSED helm: a turned skull and dome, a proud visor over a real eye slit with the light deep in it, a bevor and
  cheeks, a gilt comb over the crown;
- cloth: a TEAM-COLOUR tabard front and back (the biggest colour area, tabs-study lesson 11) in two chained
  segments with a gold hem and a sun emblem, and a team-colour cape in three chained segments - its lining a darker
  team colour (the inside is what the front and quarter views see) with light along the edges - and a swallowtail hem - parts the animation layer can swing (a cape that hangs, not a board);
- the signature stays: a blade of light through a ring (the halo), now with a sun emblem on the chest.

Round 2 (Oct 16 - lovish 04:15 "the helmets could curve a bit more", 04:38 "sleaker, with more detail, and depth ...
the fingers ... look like a block mesh", 07:10 "the elbows dont match human anatomy", 07:13 "the face just being a
black blob isnt right", 07:14 "blocks ... sticking out"): the Halberdier's techniques (gen_halberdier.py) on the
Paladin - exact turned forms (ngon, shell, chamfered, octagon_pole, octagon_axle), real gauntlets, solved elbows
(hold()), the closed helm, the feathered wings, and the protruding plates bent, turned or gone (see each function).

    python3 tools/gen_paladin.py      # writes design/models/sunforged_paladin_radiant_v2.json and _winged.json

Space and rotations are the DSL's (y up, faces -Z, +x = the unit's left, rotation x then y then z). Rx > 0 swings
a hanging part's foot forward (-z); a cape flares back with Rx < 0.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, ring, OUT  # noqa: E402
from gen_models import Bone, lathe, rounded, slab, ngon_prism, octagon_pole, octagon_axle  # noqa: E402
from gen_models import grip_hand as closed_hand, apply, transpose, _unit, _cross, rot_mat, orient  # noqa: E402

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
    # the inside of the closed helm seen through its eye slit and breaths (Oct 16): a dark, matt "plain" colour (no
    # plate / steel / dark in the name - those are painted as metal), shaded further by the baked occlusion of the
    # visor over it - the slit reads as a shadow with depth, not a painted black band (the Halberdier's lesson)
    "hollow": "#4f4a44",
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


# ------------------------------------------------------------------ exact forms (round 2, Oct 16) -----------------
# lovish, Oct 16 07:14: "The blocks your using are sticking out from the characters making them look blocky and
# edges sticking out" - two crossed boxes (octagon() above) are a plus sign whose notched corners read as two blocks
# pushed through each other, a flat plate across a round limb stands its corners out, stacked slices step. These are
# the Halberdier's exact forms (tools/gen_halberdier.py) plus two of the Paladin's own; defined here, not imported, so
# gen_paladin stays the root the other generators import octagon() from.
def ngon(bone, cx, cz, y0, y1, w, color, n=12, phase=None, **kw):
    """An upright prism of REGULAR n-gon section, w across the flats; phase 180/n puts a CORNER down the front (the
    crease a visor follows) and the flats where gen_models.lathe puts its slabs, so a skull and its dome meet edge to
    edge (gen_halberdier.ngon)."""
    phase = 180.0 / n if phase is None else phase
    side = w * math.tan(math.pi / n)
    yc, h = (y0 + y1) / 2, y1 - y0
    for k in range(n // 2):
        a = (phase + 360.0 / n * k) % 180.0
        if abs(a) < 1e-9:
            bone.box_at([cx, yc, cz], [w, h, side], color, **kw)
        elif abs(a - 90.0) < 1e-9:
            bone.box_at([cx, yc, cz], [side, h, w], color, **kw)
        else:
            bone.box_at([cx, yc, cz], [w, h, side], color, rotation=[0, a, 0], pivot=[cx, yc, cz], **kw)


def shell(bone, cx, cz, y0, r0, y1, r1, a0, a1, n, t, color, **kw):
    """n plates over the arc a0..a1 (degrees from the front, + toward the unit's right, -x) of a surface turned about
    the vertical through (cx, cz): each plate's OUTER face passes r0 from the axis at y0 and r1 at y1 along its centre
    line - a visor, a bevor, a cheek, a lame bent round a limb (gen_halberdier.shell)."""
    d = (a1 - a0) / n
    lean = math.atan2(r0 - r1, y1 - y0)
    ri = (r0 + r1) / 2 - (t / 2) * math.cos(lean)
    yc = (y0 + y1) / 2 - (t / 2) * math.sin(lean)
    length = math.hypot(r0 - r1, y1 - y0) + 0.04
    width = 2 * max(r0, r1) * math.tan(math.radians(d) / 2) + 0.04
    for k in range(n):
        th = math.radians(a0 + d * (k + 0.5))
        c = (cx - ri * math.sin(th), yc, cz - ri * math.cos(th))
        bone.box_at(c, (width, length, t), color, rotation=[math.degrees(lean), math.degrees(th), 0], pivot=list(c),
                    **kw)


def shell_point(cx, cz, y0, r0, y1, r1, th_deg, u, y, out=0.0):
    """(centre, tangent (+u), up along the plate, outward normal) of the point u px along the shell() plate centred at
    th_deg, at height y, `out` px off its face: where a rivet, a breath or an eye goes."""
    lean = math.atan2(r0 - r1, y1 - y0)
    th = math.radians(th_deg)
    r = r0 + (r1 - r0) * (y - y0) / (y1 - y0)
    nrm = (-math.cos(lean) * math.sin(th), math.sin(lean), -math.cos(lean) * math.cos(th))
    tang = (-math.cos(th), 0.0, math.sin(th))
    up = (math.sin(lean) * math.sin(th), math.cos(lean), math.sin(lean) * math.cos(th))
    c = (cx - r * math.sin(th) + u * tang[0] + out * nrm[0], y + out * nrm[1], cz - r * math.cos(th) + u * tang[2] + out * nrm[2])
    return c, tang, up, nrm


def oct_at(b, cx, cz, y0, y1, w, d, ch, color, **kw):
    """octagon() centred on (cx, cz): two crossed boxes (for a lame hidden on three sides, where six cubes don't pay)."""
    b.cube([cx - w / 2, y0, cz - d / 2 + ch], [w, y1 - y0, d - 2 * ch], color, **kw)
    b.cube([cx - w / 2 + ch, y0, cz - d / 2], [w - 2 * ch, y1 - y0, d], color, **kw)


def chamfered(bone, cx, cz, y0, y1, w, d, ch, color, **kw):
    """An upright box w x d with its four vertical edges cut by EXACT 45-degree chamfers: octagon()'s two crossed boxes
    plus a slab on each chamfer filling the notch (its outer face on the chamfer line, ch/sqrt2 thick - its inner
    corners fall inside the crossed boxes, so nothing stands out). Six cubes; the outline is the true octagon."""
    yc, h = (y0 + y1) / 2, y1 - y0
    bone.box_at([cx, yc, cz], [w, h, d - 2 * ch], color, **kw)
    bone.box_at([cx, yc, cz], [w - 2 * ch, h, d], color, **kw)
    t = ch / math.sqrt(2)
    for sx in (1, -1):
        for sz in (1, -1):
            mx, mz = cx + sx * (w / 2 - ch / 2), cz + sz * (d / 2 - ch / 2)       # the chamfer line's midpoint
            c = [mx - sx * t / 2 / math.sqrt(2), yc, mz - sz * t / 2 / math.sqrt(2)]
            bone.box_at(c, [ch * math.sqrt(2) + 0.02, h, t], color, rotation=[0, sx * sz * 45, 0], pivot=list(c), **kw)


def facet(bone, p0, p1, y0, y1, t, color, ext=0.0, **kw):
    """An upright plate whose FRONT face runs from p0 to p1 (x, z) at heights y0..y1, t thick behind it (turned about
    y only): the facets of a breastplate (gen_levy.plate)."""
    dx, dz = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dz)
    nx, nz = dz / L, -dx / L
    if nz > 0:
        nx, nz = -nx, -nz                                     # the normal that faces the front (-z)
    cx, cz = (p0[0] + p1[0]) / 2 - nx * t / 2, (p0[1] + p1[1]) / 2 - nz * t / 2
    a = math.degrees(math.atan2(-dz, dx))
    if a > 90:
        a -= 180
    elif a < -90:
        a += 180
    yc = (y0 + y1) / 2
    return bone.box_at([cx, yc, cz], [L + ext, y1 - y0, t], color, rotation=[0, a, 0], pivot=[cx, yc, cz], **kw)


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    # (the pelvis box under the faulds went: the waist plate and the fauld lames close over it - Oct 16)
    for (y0, y1, w, d, ch) in PLATES:
        octagon(b, y0, y1, w, d, ch, "plate")
    # the breast (round 2, Oct 16): a GOTHIC breastplate - four facets that turn back from a keel down the centre,
    # standing on the chest plate. It was two boxes stacked on its front, a stepped block whose corners stood out at
    # every angle ("edges sticking out", lovish 07:14); the facets' outline is the plate's own, a ridge catches the light
    zc = -7.2 * FRONT                                                                # the chest plate's front (-4.1)
    keel = zc - 0.75
    for s_ in (1, -1):
        facet(b, (0.0, keel), (s_ * 2.9, zc - 0.35), 29.4, 34.6, 0.9, "plate")
        facet(b, (s_ * 2.8, zc - 0.38), (s_ * 4.6, zc + 0.35), 29.8, 34.4, 0.7, "plate")
    # the plackart: a plate over the abdomen rising to a point over the breastplate's lower edge
    za = -6.0 * FRONT
    b.cube([-2.9, 26.0, za - 0.6], [5.8, 2.6, 1.0], "plate")
    b.box_at((0, 28.3, za - 0.45), (2.6, 2.6, 0.8), "plate", rotation=[0, 0, 45], pivot=[0, 28.3, za - 0.45])
    b.cube([-2.95, 25.95, za - 0.65], [5.9, 0.3, 1.1], "plate_dark")                                     # its lower edge
    # the belt round the waist plate: an EXACT octagon a hair proud of it (a 7.2 x 6.5 box stood its four corners
    # 0.5 px out of the 6.6 x 5.4 waist - the stepped ring every view showed at the hips)
    # (its buckle went: the tabard's top hangs over the belt's front - it was never seen)
    chamfered(b, 0, -5.4 * FRONT + 2.7 - 0.08, CHEST_Y - 1.5, CHEST_Y + 0.1, 7.0, 5.8, 0.75, "leather")
    # the sun on the breast, on the keel: one bold disc (a square and the same square turned 45 - an octagon), paint
    # not light; the eight thin rays round it went (trim merged into fewer, larger pieces - 60/30/10)
    ey = 32.6
    sc = (0, ey, keel - 0.2)
    b.box_at(sc, (2.4, 2.4, 0.5), "sun_gold", rotation=[0, 0, 45], pivot=list(sc))
    b.box_at((sc[0], sc[1], sc[2] + 0.05), (2.4, 2.4, 0.45), "gold")
    # gorget: a TURNED collar (an exact octagon) rising out of the top slice under the aventail - three stacked square
    # boxes stepped out at every corner of the neck
    ngon(b, 0, -0.35, TOP - 0.9, TOP + 1.1, 5.6, "plate", n=8, phase=0)
    # (the besagews - gold squares turned 45 at the chest's edges - and the haute-pieces - flat neck guards standing
    # 2.6 px up off the pauldrons with square ends - went: the pauldron lames now bend round the shoulder to the front
    # and back, closing the armpit, and their top lame rises against the gorget - Oct 16)
    # (the trapezius slabs under the plate went too: the pauldrons' top lames and the gorget cover the shoulder line)
    return b


# (a, L, r, droop, colour): each lame's inner end a px out along its axis from the pauldron's axis point, its length,
# the radius it is bent round, how far it droops - each lower lame further out, on a smaller radius, drooping more
PAULDRON = ((-1.5, 3.6, 3.15, 10, "tabard"), (0.55, 3.0, 2.95, 30, "tabard_dark"), (1.55, 2.5, 2.72, 51, "plate"),
            (2.3, 1.95, 2.4, 69, "plate"))


# the back pair (the culet) hangs closer and wraps less since the legs' round 3 (Oct 16): over the slimmer thighs the
# old 22 degree flare and 2.5 px stand-off left two boards floating behind the hip from the side (in the brace and on
# the stride most of all); now they lie over the back of the cuisses
FAULD_FLARE, FAULD_FLARE_BACK = (10, 17), (5, 9)
FAULD_BACK_Z, FAULD_TURN_BACK = 2.15, 30


def faulds(body):
    """The skirt of plate under the belt: two lames front and back, each lower one wider and flaring further out - and
    each made of two halves meeting at a crease on the centre line, their outer ends turned 20 degrees back round the
    hips (round 2, Oct 16: gen_models.fauld's flat boards, 6.6-9.8 px wide, stood their square side ends 1.3 px out past
    the tabards and the hips from behind and above - "edges sticking out", lovish 07:14)."""
    for k, (w, h, flare) in enumerate(((7.0, 3.1, 10), (8.3, 3.0, 22))):
        top = HIP + 0.3 - 2.6 * k
        for zf, sz in ((-(DEPTH / 2 - 0.5) - 0.7 + 0.15 * k, -1), (FAULD_BACK_Z - 0.1 * k, 1)):
            piv = [0, top, zf + 0.35]
            flare = FAULD_FLARE[k] if sz < 0 else FAULD_FLARE_BACK[k]
            turn = 20 if sz < 0 else FAULD_TURN_BACK
            for side in (1, -1):
                x0 = -0.1 if side > 0 else -w / 2
                body.cube([x0, top - h, zf], [w / 2 + 0.1, h, 0.7], "plate",
                          rotation=[-sz * flare, sz * side * turn, 0], pivot=piv)


def pauldrons(body):
    """Four lames fanned down over each shoulder, each BENT round it - a front, a top and a back plate on an arc about
    the lame's own axis (a cylinder's section), so the front and back hang down beside the arm and the shoulder reads
    as one rounded form (lovish, Oct 16 07:14: "The blocks your using are sticking out from the characters making them
    look blocky and edges sticking out" - the lames were flat 6.4 x 6.8 slabs with a thicker twin inside each, square
    shelves whose corners stood 1.5-2 px out ahead of and behind the arm and, from the RTS camera, two blue boards).
    The Halberdier's construction (gen_halberdier.pauldron) at the Paladin's scale: one axis point inside the shoulder,
    the lames lying like roof tiles; the top TWO in team colour (what the RTS camera sees from above - the art
    director's 20% mark), a gilt rolled edge along the lowest lame's rim (ornament on few plates)."""
    t = 0.5
    for side in (1, -1):
        P = (side * SHOULDER_X, TOP - 1.55, 0.0)
        for n, (a, L, r, droop, col) in enumerate(PAULDRON):
            x0 = P[0] + side * a if side > 0 else P[0] + side * (a + L)
            for phi in (-54, 0, 54):
                c = 2 * r * math.tan(math.radians(27)) + 0.1
                body.cube([x0, P[1] + r - t, -c / 2], [L, t, c], col, rotation=[phi, 0, -side * droop], pivot=list(P))
                if n == len(PAULDRON) - 1:
                    # the rolled edge: a gilt band round the lowest lame's rim, a hair proud of it
                    xe = x0 + L - 0.45 if side > 0 else x0 - 0.05
                    body.cube([xe, P[1] + r - t - 0.06, -c / 2 - 0.03], [0.5, t + 0.12, c + 0.06], "gold",
                              rotation=[phi, 0, -side * droop], pivot=list(P))


def sash_and_relic(m):
    """Asymmetry and things that swing: a team-colour sash knotted on the left hip with two tails, and a prayer book
    on a chain at the right hip (leather, a gilt clasp, a sun on the cover) - each its own bone."""
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
    rel.cube([-6.85, by + 0.9, -1.35], [0.25, 0.8, 1.2], "gold")                                         # the clasp
    # (four gold corner studs stood 0.15 px off the cover's corners - Oct 16: one clasp over the fore-edge instead)
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
        b1.cube([-w1 / 2 - 0.02, top - 0.9, z - 0.1], [w1 + 0.04, 0.9, 0.7], "gold")                         # under the belt
        # (flush with the cloth's edges: 0.2 px wider each side, its square ends stood out past the hems - Oct 16)
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


WING_TILT = -20
WING_PITCH = (20.0, 9.0, 5.0)      # flights, greater coverts, medians: each row swung forward about its top (wings())
WING_COLOURS = ("feather", "tabard", "tabard_dark")    # flights, greater coverts, medians


def wings(m, root_y=33.6):
    """Folded wings for the winged option (lovish's angel ref #1, Oct 14 17:59: "huge folded wings ... wings taller
    than the body frame the figure"). Each wing is its own bone ("wing_left" / "wing_right", which the game breathes
    and opens) whose plane is turned 40 degrees (outer edge back) and tipped back 20, so it shows from the front, the
    side, behind and from the RTS camera.

    Fourth pass, Oct 16 (lovish, Oct 15: the wings "read as grey planks from every angle"; Oct 16 04:38 "sleeker, with
    more detail, and depth"): FEATHERED - an arm and three overlapping rows, the way a folded wing is built. The arm is
    the leading edge (the lesser coverts): three segments rising from the shoulder blade to a wrist above and outside
    the shoulder (the two wrists frame the halo) and on past it, a 3.2 px band of team colour with a gold hem along its
    top - what the RTS camera sees first. Under it a row of median coverts in the darker team colour, under those the
    greater coverts in team colour with pointed tips (the wing's top half carries the owner's colour - the 20% mark),
    and from under them the flight feathers: seven, close together like a closed fan - the inner ones short, the
    primaries long, the tips converging - each tapering to a point (a vane and a tip turned 45 degrees: two cubes),
    so the trailing edge is a scalloped line of feather tips, not four boards fanned out with daylight between.
    Depth: each upper row is thicker than the one below, so it lies over the next one's tops on BOTH faces with a
    visible step at its pointed edge; each row hangs from its top swung forward (WING_PITCH - the primaries hang
    straight down under coverts that lean back with the plane, the way a folded wing falls, and their faces look up
    into the light instead of down at the grass); the flight feathers are LOUVRED 12 degrees about their shafts, so
    each is its own lit strip with the shadowed edge of the next beside it; and below the coverts the surface bends
    back toward the outer primaries (CURVE): from above the two wings are a cupped V, not two flat planes. Golem
    Smithing lights the three outer primaries' shafts (sun_ward)."""
    ARC = ((0.3, -0.6), (3.6, 8.6), (7.9, 13.0), (10.2, 11.2))      # (u out from the root, v up) along the leading edge
    BAND, HEM = 3.2, 0.55
    TILT = WING_TILT  # the plane tipped back 20: the wing's top comes forward of the halo's plane (upright, the ring
    #                   and its rays ran through both wings' arms and coverts - 1.2-1.6 px, Oct 16); the rows swung
    #                   forward from it (WING_PITCH) win back the light the tipped face loses
    CURVE = 0.03   # below the coverts the surface bends back by up to CURVE * u^2 px (u px out from the root): the
    #               primaries' tips 2.9 px further back than the inner feathers'; the arm stays in the plane (bent with
    #               it, its thickness ran into the halo's rays, which sweep round as the halo turns)

    def arc_v(u):
        for (u0, v0), (u1, v1) in zip(ARC, ARC[1:]):
            if u <= u1:
                return v0 + (v1 - v0) * (u - u0) / (u1 - u0)
        return ARC[-1][1]

    # the rows: (u where it hangs, v below the arc line, length, width, fan degrees out from straight down)
    # (the flight feathers CONVERGE: the inner ones short and turned a little out, the primaries long and turned in,
    # so their tips step down to the wing's point like a closed fan - the four slats fanned 3 to 19 degrees OUT spread
    # their tips over 20 px with daylight between: planks)
    MEDIAN = [(1.6 + 2.3 * k, 1.1, 3.6, 2.8, -1.5 * k) for k in range(4)]
    GREATER = [(1.2 + 2.75 * k, 2.5, 6.6, 2.75, 2 - 2 * k) for k in range(4)]
    FLIGHT = [(0.8 + 1.5 * k, 5.2, None, 2.5 - 0.06 * k, 3 - 1.2 * k) for k in range(7)]
    FLIGHT_TIP = [-8.0 - 2.5 * k for k in range(7)]                  # v of each flight feather's tip
    PITCH = WING_PITCH

    for side in (1, -1):
        pre = "wing_left" if side > 0 else "wing_right"
        rx, ry, rz = side * 2.6, root_y, 3.6
        wg = m.bone(pre, [rx, ry, rz], parent="body", rotation=[TILT, -side * 40, 0])

        def bend(u, v):
            return min(1.0, max(0.0, (arc_v(u) - 7.0 - v) / 12.0))      # 0 down to the coverts' tips, 1 lower

        def P(u, v, w=0.0):
            """A point of the wing's surface (u out, v up, w off it toward +z), in the bone's rest frame."""
            return (rx + side * u, ry + v, rz + CURVE * u * u * bend(u, v) + w)

        def frame(u, dv_u, dv_v, v=None):
            """At (u, v), the unit vector along the direction (du, dv) in the surface and the one across it, in 3D."""
            slope = 2 * CURVE * u * (bend(u, v) if v is not None else 0.0)
            d = _unit((side * dv_u, dv_v, slope * dv_u))
            q = _unit((side * dv_v, -dv_u, slope * dv_v))                # (du, dv) turned 90 in the surface
            return d, q

        Rw = rot_mat(TILT, -side * 40, 0)

        def feather(u, v_top, v_tip, wd, fan, thick, dz, col, louvre=0.0, pitch=0.0, tip=True, glow=False):
            a = math.radians(fan)
            du, dv = math.sin(a), -math.cos(a)
            L = (v_top - v_tip) / math.cos(a)
            top, end = P(u, v_top, dz), P(u + du * L, v_tip, dz)
            d = _unit(tuple(end[k] - top[k] for k in range(3)))            # top to tip, on the bent surface
            L = math.dist(top, end)
            _, q = frame(u + du * L / 2, du, dv, (v_top + v_tip) / 2)
            q = _unit(tuple(q[k] - d[k] * sum(q[j] * d[j] for j in range(3)) for k in range(3)))
            nrm = _unit((q[1] * d[2] - q[2] * d[1], q[2] * d[0] - q[0] * d[2], q[0] * d[1] - q[1] * d[0]))
            if pitch:
                # swung about its top toward the wing's front face (-z): its foot comes forward, its faces look up
                fwd = nrm if nrm[2] < 0 else tuple(-x for x in nrm)
                p_ = math.radians(pitch)
                d = tuple(d[k] * math.cos(p_) + fwd[k] * math.sin(p_) for k in range(3))
                nrm = _unit((q[1] * d[2] - q[2] * d[1], q[2] * d[0] - q[0] * d[2], q[0] * d[1] - q[1] * d[0]))
            q2 = q
            if louvre:
                # turned about its shaft toward facing the front and the back (the way Minecraft's two entity lights
                # come): of the two senses, the one whose face ends up nearer +-z in the posed model
                best = None
                for sg in (1, -1):
                    tw = math.radians(sg * louvre)
                    qq = tuple(q[k] * math.cos(tw) + nrm[k] * math.sin(tw) for k in range(3))
                    nn = apply(Rw, _unit((qq[1] * d[2] - qq[2] * d[1], qq[2] * d[0] - qq[0] * d[2],
                                          qq[0] * d[1] - qq[1] * d[0])))
                    if best is None or abs(nn[2]) > best[0]:
                        best = (abs(nn[2]), qq)
                q2 = best[1]
            vane = L - (0.495 * wd if tip else 0.0)
            c = tuple(top[k] + d[k] * vane / 2 for k in range(3))
            slab(wg, c, d, q2, (wd, thick, vane + 0.02), col)
            if tip:
                s = 0.7 * wd
                ct = tuple(top[k] + d[k] * vane for k in range(3))
                d45 = tuple((d[k] + q2[k]) / math.sqrt(2) for k in range(3))
                q45 = tuple((q2[k] - d[k]) / math.sqrt(2) for k in range(3))
                slab(wg, ct, d45, q45, (s, thick, s), col)
            if glow:
                n2 = _unit((q2[1] * d[2] - q2[2] * d[1], q2[2] * d[0] - q2[0] * d[2], q2[0] * d[1] - q2[1] * d[0]))
                if n2[2] < 0:
                    n2 = tuple(-x for x in n2)                            # the +z (outer, back) face
                cg = tuple(top[k] + d[k] * (L - 2.6) + n2[k] * (thick / 2 + 0.06) for k in range(3))
                slab(wg, cg, d, q2, (0.32, 0.12, 3.2), "glow", upgrade="sun_ward")

        # flight feathers first, then the rows that lie over their tops
        # the flight feathers louvred 12 degrees, each turned about its shaft toward the front (and so its back face
        # toward the back): their edges stand out of the wing in a row of overlaps - each feather lit as its own
        # strip with the shadowed edge of the next beside it - and their faces take more of Minecraft's front and back
        # entity light (the two lights come from the front and the back, not the side)
        for k, ((u, vb, _, wd, fan), vt) in enumerate(zip(FLIGHT, FLIGHT_TIP)):
            feather(u, arc_v(u) - vb, vt, wd, fan, 0.45, 0.0, WING_COLOURS[0], louvre=12, pitch=PITCH[0], glow=k >= 4)
        # the covert rows over their tops, each thicker than what it covers (so it lies over it on BOTH faces - a row
        # of feathers with a visible depth at its lower, pointed edge)
        for k, (u, vb, L, wd, fan) in enumerate(GREATER):
            v0 = arc_v(u) - vb
            feather(u, v0, v0 - L, wd, fan, 1.04, 0.0, WING_COLOURS[1], pitch=PITCH[1])
        for k, (u, vb, L, wd, fan) in enumerate(MEDIAN):
            v0 = arc_v(u) - vb
            feather(u, v0, v0 - L, wd, fan, 1.2, 0.0, WING_COLOURS[2], pitch=PITCH[2], tip=False)
        # the arm: team band, gold hem on its upper edge - one rotated piece per segment, following the curve
        for n, ((u0, v0), (u1, v1)) in enumerate(zip(ARC, ARC[1:])):
            A, B = P(u0, v0), P(u1, v1)
            um = (u0 + u1) / 2
            d, q = frame(um, u1 - u0, v1 - v0)
            if q[1] < 0:
                q = tuple(-x for x in q)                                  # the across that points up
            L = math.dist(A, B) + 0.8                                     # overlap the next segment at the joint
            c = tuple((A[k] + B[k]) / 2 for k in range(3))
            slab(wg, c, d, q, (BAND, 1.36, L), "tabard")
            # the hem along its top - on the first segment from 1.6 px out (at the root it ran into the back plate)
            cut = 1.6 if n == 0 else 0.0
            ch = tuple(c[k] + q[k] * (BAND / 2 + HEM / 2 - 0.05) + d[k] * cut / 2 for k in range(3))
            slab(wg, ch, d, q, (HEM, 1.46, L - 0.2 - cut), "gold")
        # the wrist: a gilded knuckle at the top of the arc
        wx, wy, wz = P(*ARC[2])
        wg.box_at((wx, wy + 0.1, wz), (1.6, 1.6, 1.2), "gold", rotation=[0, 0, 45], pivot=[wx, wy + 0.1, wz])


# ------------------------------------------------------------------ arms and hands ---------------------------------
def arm(m, side, parent="body"):
    """Round 2 (Oct 16): the arm TURNED and jointed like a person's. lovish, 04:38: "everything about the designs need
    to be sleaker"; 07:10 "the elbows dont match human anatomy"; 07:14 "edges sticking out". The rerebrace, vambrace
    and cuff are regular octagons (gen_models.octagon_pole: the core box and its two rounding twins were a plus sign in
    section, stepped at every corner, and the square lames and cuff rims stood their corners out round it); the couter
    is a turned joint round the elbow's hinge (octagon_axle - an octagonal cylinder along the axis the forearm bends
    about, so it is one rounded elbow at any bend; the 3.8 x 2.4 x 4.2 box swung its corners out past the arm at every
    angle), its fan a diamond on the outer face; one articulation lame below it. The hand is NOT on the forearm: the
    gauntlet closes round the sword grip (gauntlet()) and the forearm is solved onto its cuff. Returns (upper, fore,
    wrist point in rest space - inside the flared cuff, where the gauntlet's cuff ends)."""
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, TOP - 0.6
    elbow_y = sy - UPPER
    wrist_y = elbow_y - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    octagon_pole(up, sx, 0, elbow_y + 0.9, sy - 0.6, 2.9, "plate")                                       # rerebrace
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    octagon_axle(fo, (sx + side * 0.08, elbow_y, 0.0), 3.1, 3.3, "plate")                               # couter
    fo.box_at((sx + side * 1.66, elbow_y, 0.15), (0.34, 2.1, 2.1), "plate", rotation=[45, 0, 0],
              pivot=[sx + side * 1.66, elbow_y, 0.15])                                                  # its fan
    octagon_pole(fo, sx, 0, elbow_y - 2.05, elbow_y - 1.35, 3.0, "plate_dark")                          # a lame
    # the vambrace from the lame to the gauntlet's flared cuff
    octagon_pole(fo, sx, 0, wrist_y + 1.0, elbow_y - 1.6, 2.72, "plate")
    octagon_pole(fo, sx, 0, wrist_y - 0.05, wrist_y + 1.3, 3.05, "plate_dark")
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.55, 0)


GRIP_R = 0.66              # the greatsword grip's radius under the fingers (its octagon is 1.26 across the flats)
# a gauntlet at the Paladin's scale (gen_models.grip_hand is sized for a 1.85 px hand: his forearms are 2.6-2.9 across)
GAUNT = dict(finger_w=0.58, finger_t=0.5, palm_t=0.74, palm_len=1.75, cuff_len=0.95)


def gauntlet(bone, centre, axis, side, back):
    """A plate gauntlet closed round the sword grip (gen_models.grip_hand; lovish, Oct 16 04:38: "the fingers are also
    lacking apperance looks like a block mesh for hands ... when he puts his hand around the weapon it doesnt look
    natural" - it was a 3 x 2.7 x 3 block with three dark lines for fingers): four fingers in three tapering lames each,
    wrapping the grip, knuckle plates, two metacarpal lames over the back of the hand, the thumb over the index, the cuff
    toward the forearm (`back`)."""
    return closed_hand(bone, centre, axis, GRIP_R, side, "plate", accent="plate_dark", back=back, cuff="plate_dark",
                       knuckle="plate", heel="plate_dark", **GAUNT)


def hold(m, arm_bones, hand_bone, centre, axis, side, back, elbow_at, **solve):
    """Closes a gauntlet round the grip at `centre` (rest space of hand_bone) and solves the arm onto its cuff: the wrist
    where the cuff ends, the forearm pointing along the hand (a straight wrist), the elbow drawn toward elbow_at - where
    a person's elbow is for this hold. The hand's frame follows the forearm, so solve, read the solved forearm's
    direction, rebuild the hand along it, solve again (gen_halberdier's loop: four rounds settle under 2 degrees)."""
    upper, fore, wrist = arm_bones
    F = m.frame(hand_bone)
    err = 0.0
    used = back
    for _ in range(4):
        used = back
        g = gauntlet(Bone(m, "_scratch", [0, 0, 0]), centre, axis, side, used)
        err = m.solve_arm(upper, fore, wrist, m.fk(hand_bone, g["wrist"]), elbow_out=1, elbow_down=1,
                          point_at=m.fk(hand_bone, g["aim"]), elbow_at=elbow_at, **solve)
        el, wr = m.fk(fore, m.bones[fore].pivot), m.fk(fore, wrist)
        dw = _unit(tuple(el[k] - wr[k] for k in range(3)))
        dl = apply(transpose(F), dw)
        back = _unit(tuple(0.5 * used[k] + 0.5 * dl[k] for k in range(3)))
    off = math.degrees(math.acos(max(-1.0, min(1.0, sum(back[k] * used[k] for k in range(3))))))
    gauntlet(m.bones[hand_bone], centre, axis, side, used)
    e = m.fk(fore, m.bones[fore].pivot)
    print("  %s hand: cuff %.1f deg off the solved forearm, wrist %.2f px off the cuff, elbow at (%.1f, %.1f, %.1f), "
          "arm %s fore %s" % ("left" if side > 0 else "right", off, err, e[0], e[1], e[2], m.bones[upper].rotation,
                              m.bones[fore].rotation))
    return err


# ------------------------------------------------------------------ helm -------------------------------------------
def helm(m, mood="determined"):
    """A CLOSED helm (round 2, Oct 16) - faceless, as the Sunforged dial asks, but a defined face of steel. lovish,
    07:13: "the face just being a black blob isnt right. We could make masks or helmets that are more defined and
    developed with a shadow covering facial features instead"; 04:15 "the helmets could curve a bit more ... they are
    currently a bit boxy". It was three stacked chamfered tiers (a ziggurat), a flat dark face plate with two white
    bars on it and a gold bar across the brow.

    Now turned like the Halberdier's sallet (tools/gen_halberdier.py), its own shape: a ROUND skull (a regular 12-gon,
    a corner down the front) under a lathe-turned dome; a visor that stands proud of it and is creased down the centre,
    its lower edge the most proud so it overhangs the eye slit; under it a bevor that bulges to the chin and creases
    with it, cheeks rising to the visor's ends, a chin plate turning in to the mail. The eye slit is a real gap between
    visor and bevor with a bar between the sights: what shows in it is the skull's face 0.5 px back, a dark matt
    "hollow" the baked sky light shades deepest under the visor's edge - a shadow with depth - and deep in it the two
    eyes of light (light where the face should be, the angel's read from the RTS camera), set at the mood's angle
    (determined: a frown, inner ends low). Breaths on the right cheek, gilt visor hinges, the swept crest and a pair of
    gilt wings at the temples (Sunforged: winged helms) stay."""
    hy = TOP + 1.1
    h = m.bone("head", [0, hy, 0], parent="body")
    cz, R = 0.0, 3.1
    rounded(h, 0, 0.15, hy - 1.4, hy + 0.9, 5.1, 5.3, "steel", k=2)                                    # mail aventail
    ngon(h, 0, cz, hy + 0.7, hy + 5.25, 2 * R, "plate")                                                 # the skull
    lathe(h, 0, cz, R, R, ((hy + 5.2, 1.0, hy + 6.6, 0.8), (hy + 6.55, 0.8, hy + 7.5, 0.42)), "plate")
    rounded(h, 0, cz, hy + 7.1, hy + 7.65, 2.75, 2.75, "plate", k=2)                                     # crown cap
    # the slit's depth: the skull's face, dark, behind the gap
    SLIT = (hy + 2.55, R + 0.06, hy + 3.4, R + 0.06)
    shell(h, 0, cz, *SLIT, -66, 66, 4, 0.6, "hollow")
    tilt = {"determined": 13, "calm": 0, "fury": 24}[mood]
    for s in (1, -1):                                                         # the eyes, deep in the slit
        c, tang, up, nrm = shell_point(0, cz, *SLIT, -s * 16.5, -s * 0.32, hy + 2.97, out=0.05)
        a = math.radians(tilt)
        along = tuple(-s * tang[k] * math.cos(a) + up[k] * math.sin(a) for k in range(3))
        across = tuple(s * tang[k] * math.sin(a) + up[k] * math.cos(a) for k in range(3))
        slab(h, c, along, across, (0.24, 0.12, 1.15), "glow")
    h.box_at((0, hy + 2.97, cz - R - 0.27), (0.48, 0.9, 0.48), "plate", rotation=[0, 45, 0],
             pivot=[0, hy + 2.97, cz - R - 0.27])                                                     # the bar between
    # the visor: proud of the skull, its lower edge the most proud (it overhangs the slit), creased down the centre
    VIS = (hy + 3.36, R + 0.56, hy + 4.95, R + 0.2)
    shell(h, 0, cz, *VIS, -84, 84, 4, 0.45, "plate")
    for s in (1, -1):                                                                                 # its hinges
        c, tang, up, nrm = shell_point(0, cz, *VIS, s * 63, s * 0.75, hy + 4.15, out=0.1)
        slab(h, c, up, tang, (0.48, 0.2, 0.48), "gold_dark")
    # the bevor: from under the slit, bulging to the chin, creased with the visor; its cheeks rise to the visor's ends
    BEV = (hy + 1.15, R + 0.42, hy + 2.6, R + 0.24)
    shell(h, 0, cz, *BEV, -66, 66, 4, 0.42, "plate")
    slope = (BEV[3] - BEV[1]) / (BEV[2] - BEV[0])
    for s in (1, -1):
        shell(h, 0, cz, BEV[0], BEV[1], hy + 3.4, BEV[1] + slope * (hy + 3.4 - BEV[0]),
              66 if s > 0 else -96, 96 if s > 0 else -66, 1, 0.42, "plate")
    shell(h, 0, cz, hy - 0.35, R + 0.05, hy + 1.18, R + 0.42, -66, 66, 3, 0.42, "plate")             # the chin
    # the ridge down the bevor's crease (a square bar turned 45: a sharp line of light)
    zc0, zc1 = BEV[1] / math.cos(math.radians(16.5)), BEV[3] / math.cos(math.radians(16.5))
    slab(h, (0, (BEV[0] + BEV[2]) / 2, cz - (zc0 + zc1) / 2), (0, BEV[2] - BEV[0], zc0 - zc1), (1, 0, 1),
         (0.32, 0.32, BEV[2] - BEV[0] - 0.1), "plate")
    for u in (-0.5, 0.0, 0.5):                                                # the breaths, on the right cheek
        c, tang, up, nrm = shell_point(0, cz, *BEV, 49.5, u, hy + 1.85, out=0.0)
        slab(h, c, up, tang, (0.2, 0.12, 0.62), "hollow")
    # the crest: a gilt comb that FOLLOWS the dome from the brow over the crown and down the back - two pieces, its
    # foot inside the plate (it was a 7.8 px bar swept up off the crown, its back end 3 px clear of the skull; with the
    # gilt wings at the temples - six square-ended sticks round the head from the RTS camera - it was the most
    # "blocks sticking out" thing on him, lovish 07:14; the wings on his back say "winged" now)
    F, T, B = (hy + 5.95, cz - 2.75), (hy + 7.85, cz - 0.15), (hy + 7.05, cz + 2.7)
    for (p0, p1) in ((F, T), (T, B)):
        dy, dz = p1[0] - p0[0], p1[1] - p0[1]
        L = math.hypot(dy, dz)
        ny, nz = -dz / L, dy / L                                                     # the normal away from the skull
        if ny < 0:
            ny, nz = -ny, -nz
        slab(h, (0, (p0[0] + p1[0]) / 2 + ny * 0.42, (p0[1] + p1[1]) / 2 + nz * 0.42), (0, dy, dz), (1, 0, 0),
             (0.9, 1.5, L + 0.5), "gold")
    dy, dz = T[0] - F[0], T[1] - F[1]
    L = math.hypot(dy, dz)
    slab(h, (0, (F[0] + T[0]) / 2 + dz / L * 1.25, (F[1] + T[1]) / 2 - dy / L * 1.25), (0, dy, dz), (1, 0, 0),
         (0.5, 0.3, L), "sun", upgrade="sun_ward")                                  # Golem Smithing lights its edge
    return h, hy


# ------------------------------------------------------------------ legs -------------------------------------------
# Round 3 (Oct 16 - lovish 12:06: "The dawn paladins leg looks reallly blocky from the side maybe we can make the legs a
# bit thinner and round to make it more realistic"). From the side the leg was a stack of boxes: a thigh box with a
# wider twin, a cuisse board on its front, a calf box stuck on the back of a shin box, a greave board on its front, a
# diamond boss and a diamond fan on a 4.25 px knee drum, a flat slab of a foot - every one a straight edge with a step
# where it met the next. Now the leg is TURNED plate over a human leg, thinner (mid-thigh 3.6 x 3.7 - it was 4.8 x
# 5.2; the knee 3.2 x 3.0 under a 2.6 px cop - the drum was 4.0 x 4.25; the calf 3.4 x 3.75 - it was 3.9 x 4.9): each
# plate an exact facet of an n-gon loft (loft() - the tangent polygon round an ellipse at each section, the plates
# leaned and turned onto it), so the outline tapers and swells with no stairs; the knee one hinge, the sabaton lames
# over a rounded toe.
LEG_N = 8                 # the loft's sides: a corner down the front (the shin's ridge) and the back, flats between
TH, SH = 10.0, 10.0       # thigh and shin: hip -> knee -> ankle pivots (the Java swings them there)
# (y, z front, z back, half width) in the bone's rest space, top down. The thigh deepest at the front-top and tapering
# to the knee; the greave a straight front edge (the shin bone), the calf swelling behind it a third of the way down
# and narrowing to the ankle.
CUISSE = ((HIP + 2.0, -3.0, 1.8, 2.05), (HIP - TH + 0.1, -1.5, 1.45, 1.6))
GREAVE = ((HIP - TH + 0.3, -1.3, 1.26, 1.38), (HIP - TH - 3.4, -1.55, 2.2, 1.7), (HIP - TH - SH + 0.45, -1.0, 0.97, 1.08))
KNEE_W, KNEE_L, KNEE_Z = 2.9, 2.6, -0.47   # the poleyn's hinge: across its flats, its length along x, its centre's z


def _tangent_ngon(n, phase):
    """The corners of the unit circle's tangent n-gon - tangents at 2 pi (k + phase) / n from the front (-z), + toward
    +x - as (x, z); corner k lies between tangent k and k + 1 (phase 0.5: corners at the front, the sides, the back)."""
    R = 1.0 / math.cos(math.pi / n)
    return [(R * math.sin(2 * math.pi * (k + phase + 0.5) / n), -R * math.cos(2 * math.pi * (k + phase + 0.5) / n))
            for k in range(n)]


def loft(bone, cx, sections, color, n=LEG_N, phase=0.5, t=0.45, ext=0.06, **kw):
    """A limb TURNED in plate: sections (y, z front, z back, half width), in any order of y, each the tangent n-gon round
    the ellipse with those extents (centre x cx); between each pair of neighbouring sections one plate per facet, its
    outer face on the facet - leaned by the taper, turned to face out, as wide as the facet at its middle height (at
    its ends it stands a hair inside or past the crease: under 0.1 px at these tapers) - so the outline tapers and
    swells with no stairs and no corner of a box standing out of it (stacked boxes and a box with a narrower twin
    were the Paladin's legs before - lovish, Oct 16 12:06 "looks really blocky from the side"). Outer face = the cube's
    north face (a side face for the painter, shaded by height in the bone), like shell() and gen_models.lathe."""
    U = _tangent_ngon(n, phase)
    ex_side = max(u[0] for u in U)
    ex_front, ex_back = max(-u[1] for u in U), max(u[1] for u in U)

    def ring(sec):
        y, zf, zb, hw = sec
        A, B = hw / ex_side, (zb - zf) / (ex_front + ex_back)
        cz = zf + B * ex_front
        return [(cx + A * u[0], y, cz + B * u[1]) for u in U], (cx, y, cz)
    secs = sorted(sections)
    for lo_s, hi_s in zip(secs, secs[1:]):
        (lo, clo), (hi, chi) = ring(lo_s), ring(hi_s)
        for k in range(n):
            b0, b1, t0, t1 = lo[k - 1], lo[k], hi[k - 1], hi[k]
            mb = tuple((b0[a] + b1[a]) / 2 for a in range(3))
            mt = tuple((t0[a] + t1[a]) / 2 for a in range(3))
            up = tuple(mt[a] - mb[a] for a in range(3))
            L = math.sqrt(sum(v * v for v in up))
            acr = tuple((b1[a] - b0[a] + t1[a] - t0[a]) / 2 for a in range(3))
            w = (math.dist(b0, b1) + math.dist(t0, t1)) / 2
            ey = _unit(up)
            nrm = _unit(_cross(acr, ey))
            mid = tuple((mb[a] + mt[a]) / 2 for a in range(3))
            axis = tuple((clo[a] + chi[a]) / 2 for a in range(3))
            if sum(nrm[a] * (mid[a] - axis[a]) for a in (0, 2)) < 0:
                nrm = tuple(-v for v in nrm)
            ez = tuple(-v for v in nrm)                         # the cube's +z points in: its north face is the outside
            ex = _cross(ey, ez)
            c = tuple(mid[a] - nrm[a] * t / 2 for a in range(3))
            bone.box_at(list(c), [w + 0.03, L + ext, t], color, rotation=orient(ex, ey, ez), pivot=list(c), **kw)


def _section_at(sections, y):
    """The (y, z front, z back, half width) section of a two-section loft at height y (linear in between)."""
    (y0, *a), (y1, *b) = sorted(sections)
    f = (y - y0) / (y1 - y0)
    return (y,) + tuple(a[k] + (b[k] - a[k]) * f for k in range(3))


def x_ngon(bone, c, length, w, color, n=6, phase=0.0, stagger=0.02, **kw):
    """A regular n-gon prism ALONG x (w across the flats), centred on c: gen_models.octagon_axle for any even n - a
    disc seen from the side when short (a poleyn's wing), a hinge when long. Each band `stagger` px shorter than the
    last: octagon_axle's four end faces are coplanar and z-fought into a cross-hatch on every drum end (the renders of
    Oct 16 - the couters show it too); 0.02 px apart they never fight and no eye sees the step."""
    side = w * math.tan(math.pi / n)
    for k in range(n // 2):
        a = phase + 360.0 / n * k
        bone.box_at(list(c), [length - stagger * k, w, side], color,
                    **(dict(rotation=[a, 0, 0], pivot=list(c)) if a % 180 else {}), **kw)


def y_ngon(bone, cx, cz, y0, y1, w, color, n=6, phase=0.0, stagger=0.02, **kw):
    """ngon() with its bands' heights staggered like x_ngon's (their top faces were coplanar and z-fought)."""
    side = w * math.tan(math.pi / n)
    for k in range(n // 2):
        a = phase + 360.0 / n * k
        h = y1 - y0 - stagger * k
        bone.box_at([cx, y0 + h / 2, cz], [w, h, side], color,
                    **(dict(rotation=[0, a, 0], pivot=[cx, y0 + h / 2, cz]) if a % 180 else {}), **kw)


def leg(m, side, thigh_rot, shin_rot, foot_rot=None, toe_out=8):
    """Long and slim, the digitigrade hint (knee forward, ankle back) - plate over a human leg (round 3, Oct 16; see
    the block above): a cuisse turned and tapering to the knee under two tassets, a poleyn turned round the knee's
    hinge with a domed cop and a wing, a greave shaped to the calf, a sabaton of lames over a rounded toe. Bones and
    pivots as before (the Java's walk and brace turn them there): thigh at the hip, shin at the knee, foot at the
    ankle, level and turned out (Model.level_rotation) unless a pose gives foot_rot."""
    pre = "left" if side > 0 else "right"
    hx = side * 3.0
    knee_y, ankle_y = HIP - TH, HIP - TH - SH
    th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh_rot)
    # the cuisse: one turned plate from inside the hip to inside the knee's hinge, a corner down its front (the
    # medial ridge). On the right it rises into the waist: the hip was see-through from the side between the belt and
    # the thigh (round 2 took the pelvis box out); on the left it stops under the sash's knot, which is tied there and
    # covers that hip (risen, the cuisse ran 1.3 px through the knot; at the hip line it still met it on the stride)
    top = CUISSE[0] if side < 0 else _section_at(CUISSE, HIP - 0.3)
    loft(th, hx, (top, CUISSE[1]), "plate")
    # tassets: two lames hung from under the faulds over the thigh's front-top, each BENT round it (shell(): two facets
    # meeting at a crease, from the inside of the thigh's front round to its outer side), flaring a little at the foot
    # so the upper lies over the lower like roof tiles - the front-top of the thigh, deepest there from the side; the
    # upper in the owner's colour (the pauldrons' top lames' language: what the side and RTS views see at the hip),
    # the lower in plate (the flat boards turned about y stood their outer corners 1 px out past the leg)
    a0, a1 = (-62, 24) if side > 0 else (-24, 45)            # degrees from the front, + toward -x: the outer side
    # (on the left, wrapped further round the outer side - what the side view sees - its top under the sash's knot)
    yt = HIP + 1.4 if side < 0 else HIP + 0.2
    for (y0, r0, y1, r1, col) in ((HIP - 2.2, 2.6, yt, 2.6 + 0.35 * (yt - HIP + 2.2) / 3.6, "tabard"),
                                  (HIP - 5.0, 2.3, HIP - 1.8, 2.45, "plate")):
        shell(th, hx, -0.3, y0, r0, y1, r1, a0, a1, 2, 0.42, col)
    # the poleyn: round 2's turned knee (an octagonal cylinder along x, the axis the shin bends about) now on the THIGH
    # (Model.ground_leg's shift of the shin cubes never moves it off the hinge), smaller (2.9 x 2.6 - it was 4.25 x 4.0
    # and stood out round the leg on every side, a drum) and set forward of the hinge: its front is the domed cop, 0.4
    # px proud of the cuisse and 0.6 of the greave - the knee from the side - and its back sinks into the leg (the back
    # of a knee is the cuisse lapping over the greave, not a ball). A round wing on its outer side with a gilt boss
    # (the square fan and the diamond boss stood their corners out)
    kc = (hx, knee_y, KNEE_Z)
    x_ngon(th, kc, KNEE_L, KNEE_W, "plate", n=8)
    wx, wz = hx + side * (CUISSE[-1][3] + 0.13), -0.15
    x_ngon(th, (wx, knee_y, wz), 0.3, 2.4, "plate", n=6)                                               # the wing
    th.box_at((wx + side * 0.17, knee_y, wz), (0.14, 0.8, 0.8), "gold", rotation=[45, 0, 0],
              pivot=[wx + side * 0.17, knee_y, wz])                                                   # its boss
    sh = m.bone(pre + "_shin", [hx, knee_y, 0], parent=pre + "_thigh", rotation=shin_rot)
    # the greave: one turned plate from inside the cuisse's foot to inside the ankle, its front a straight edge with a
    # ridge, the calf swelling at the back; its top telescopes up inside the cuisse, which laps over it at the sides and
    # behind the knee like a lame and slides over it as the knee bends
    loft(sh, hx, GREAVE, "plate")
    # the ankle: a turned joint centred on the foot's hinge, so it is the same at every flex of the foot - on the shin
    # (Model.ground_leg moves the shin's cubes and the foot's pivot by the same drop); on the foot it stood the foot
    # bone 3.3 px tall and the painter's horizon (dsl_to_game REFLECT: the ground mirrored below 46% of a bone's
    # height) turned the whole sabaton under it near black
    x_ngon(sh, (hx, ankle_y, 0.0), 2.35, 2.25, "plate", n=8)
    # the sabaton, 2.5 x 7.3 (it was 3.5 x 8.6): a heel sloping in under the ankle, one lame and a gilt lame stepping
    # DOWN the instep like roof tiles to a toe ROUNDED from above (an exact hexagon: the gothic point was a square
    # turned 45 with the sole's square corners past it), the sole flush with the sides
    rot = foot_rot if foot_rot is not None else m.level_rotation(pre + "_shin", yaw=-side * toe_out)
    ft = m.bone(pre + "_foot", [hx, ankle_y, 0], parent=pre + "_shin", rotation=rot)
    g = ankle_y - 2.2                                                                                     # the ground
    ft.cube([hx - 1.25, g + 0.3, -4.0], [2.5, 0.95, 5.4], "plate")                                       # heel to toe
    zt = -4.35                                                                                            # the toe's centre
    # (the heel's inner edge kept above the sole: 0.12 px under it, Model.ground_leg stood the foot on that corner and
    # the sole hovered)
    for (p0, p1, col, w, T) in (((g + 2.0, 0.55), (g + 0.62, 1.5), "plate", 2.45, 0.9),                 # the heel
                                ((g + 2.4, -0.5), (g + 1.42, -3.3), "plate", 2.5, 0.95),
                                ((g + 1.55, -3.05), (g + 0.98, zt - 0.25), "gold", 2.42, 0.8)):
        dy, dz = p1[0] - p0[0], p1[1] - p0[1]
        Ls = math.hypot(dy, dz)
        ny, nz = -dz / Ls, dy / Ls                                                   # the normal up and forward
        if ny < 0:
            ny, nz = -ny, -nz
        slab(ft, (hx, (p0[0] + p1[0]) / 2 - ny * T / 2, (p0[1] + p1[1]) / 2 - nz * T / 2), (0, dy, dz), (1, 0, 0),
             (w, T, Ls + 0.1), col)
    y_ngon(ft, hx, zt, g, g + 0.85, 2.4, "plate", n=6)                                                   # the toe
    ft.cube([hx - 1.25, g, zt], [2.5, 0.3, 1.75 - zt], "dark")                                            # the sole
    return pre + "_foot", pre + "_shin"


# ------------------------------------------------------------------ the sword ------------------------------------
def greatsword(b, gx, gz, front=-1):
    """The one part that carries the most detail (MORTEMARTT lesson; lovish 18:23 "the sword could look better"): a
    faceted pommel, a wrapped grip with a ferrule, a quillon block with the sun on its face, quillons that curve toward
    the blade in three segments to diamond finials, a ricasso engraved with a sunburst, and a steel blade carrying
    light in its fuller. `front`: the face the viewer sees (-1 = -z; +1 for the planted sword, which its bone turns 180
    degrees about x - the single-sided ornament goes on the face that ends up in front, not against his belly).

    Round 2 (Oct 16, lovish 04:38 "sleaker, with more detail, and depth"; 07:14 "edges sticking out"): the blade TAPERS
    as one form - each half of it one slab whose outer edge follows the taper, a bevelled steel edge each side on the
    same line, the light in ONE fuller channel - instead of five stacked sections stepping in at every joint (a
    staircase down both edges, 36 cubes); the point is two edge slabs that meet at the tip, the core under them (it was
    three steps); the pommel and grip are turned (exact octagons: the pommel was a square and the same square turned 45 -
    an eight-pointed star - and the grip a square bar)."""
    G = 32.0                                                                                             # guard line
    f = front
    # pommel: an exact octagonal wheel and a button; planted, it is the top of the sword under the gauntlets
    ngon_prism(b, gx, gz, 20.6, 22.55, 2.5, "gold", n=8)
    b.box_at((gx, 20.3, gz), (1.1, 0.7, 1.1), "gold_dark")
    # grip: leather on a turned core; the ferrule at the guard
    octagon_pole(b, gx, gz, 22.5, G - 0.3, 2 * GRIP_R - 0.06, "leather")
    octagon_pole(b, gx, gz, G - 0.6, G, 1.52, "gold")
    # the quillon block, the sun on its face
    b.cube([gx - 1.3, G - 0.1, gz - 1.1], [2.6, 2.0, 2.2], "gold")
    b.box_at((gx, G + 0.9, gz + f * 1.25), (1.5, 1.5, 0.3), "sun_gold", rotation=[0, 0, 45], pivot=[gx, G + 0.9, gz + f * 1.25])
    # quillons: two segments each side curving toward the blade, then a diamond finial
    for side in (1, -1):
        px, py = gx + side * 1.3, G + 0.9
        for L, t, dd, a in ((2.4, 1.0, 1.4, 4), (2.5, 0.85, 1.25, 26)):
            x0 = px if side > 0 else px - L
            b.cube([x0, py - t / 2, gz - dd / 2], [L, t, dd], "gold", rotation=[0, 0, side * a], pivot=[px, py, gz])
            px += side * L * math.cos(math.radians(a))
            py += L * math.sin(math.radians(a))
        b.box_at((px + side * 0.3, py + 0.3, gz), (1.0, 1.0, 1.0), "sun_gold", rotation=[0, 0, 45], pivot=[px + side * 0.3, py + 0.3, gz])
    # ricasso: gold, engraved with a sunburst rising off the guard (on its front face)
    R0, R1 = G + 1.9, G + 4.0
    b.cube([gx - 1.1, R0, gz - 0.5], [2.2, R1 - R0, 1.0], "gold")
    for k in range(3):
        a = math.radians(50 + k * 40)
        cx, cy = gx + 1.0 * math.cos(a), R0 + 0.2 + 1.0 * math.sin(a)
        b.box_at((cx, cy, gz + f * 0.6), (1.3, 0.3, 0.2), "sun_gold", rotation=[0, 0, math.degrees(a)], pivot=[cx, cy, gz + f * 0.6])
    # the blade: from R1 to Y0 its half-width runs HW0 -> HW1 in a straight taper, then the point to the tip
    Y0, YT, HW0, HW1 = R1 + 17.0, R1 + 17.0 + 2.7, 1.8, 1.25
    L = Y0 - R1
    for e in (1, -1):
        # the flat: one slab a side, its outer edge 0.4 inside the blade's edge (under the bevel), across the centre
        p0, p1 = (gx + e * (HW0 - 0.4), R1), (gx + e * (HW1 - 0.4), Y0)
        wc = 1.45
        c = ((p0[0] + p1[0]) / 2 - e * wc / 2, (p0[1] + p1[1]) / 2, gz)
        slab(b, c, (p1[0] - p0[0], L, 0), (1, 0, 0), (wc, 0.7, L + 0.02), "steel_bright")
        # the bevelled edge on the same taper, and the light along it once Golem Smithing is researched
        for (off, wd, th, col, up) in ((0.25, 0.5, 0.4, "steel_edge", None), (0.15, 0.3, 0.24, "glow", "sun_ward")):
            q0, q1 = (gx + e * (HW0 - off), R1), (gx + e * (HW1 - off), Y0)
            c = ((q0[0] + q1[0]) / 2, (q0[1] + q1[1]) / 2, gz)
            slab(b, c, (q1[0] - q0[0], L, 0), (1, 0, 0), (wd, th, L + 0.02 - (0.2 if up else 0.0)), col,
                 **({"upgrade": up} if up else {}))
    # the fuller: one channel of light down the middle, ending in a small diamond before the point
    b.cube([gx - 0.35, R1 - 0.01, gz - 0.39], [0.7, 13.6, 0.78], "sun")
    b.box_at((gx, R1 + 14.1, gz), (1.1, 1.1, 0.8), "sun", rotation=[0, 0, 45], pivot=[gx, R1 + 14.1, gz])
    # the point: an edge slab each side from the blade's edge to the tip, ending where its inner corner meets the other
    # side's edge (else it pokes past it), the core between them
    W = 0.45
    for e in (1, -1):
        P0, T = (gx + e * HW1, Y0), (gx, YT)
        d = _unit((T[0] - P0[0], T[1] - P0[1], 0.0))
        n = (-d[1], d[0])
        if n[0] * e > 0:
            n = (-n[0], -n[1])                                                       # the normal toward the axis
        lo, hi = 0.0, 1.5                                                            # how far short of the tip it ends
        for _ in range(40):
            s_ = (lo + hi) / 2
            ix, iy = T[0] - s_ * d[0] + W * n[0], T[1] - s_ * d[1] + W * n[1]
            mirror = gx - e * HW1 * (YT - iy) / (YT - Y0)
            (lo, hi) = (s_, hi) if (ix - mirror) * e < 0 else (lo, s_)
        a0 = (P0[0] - 0.15 * d[0], P0[1] - 0.15 * d[1])
        a1 = (T[0] - hi * d[0], T[1] - hi * d[1])
        Ls = math.dist(a0, a1)
        c = ((a0[0] + a1[0]) / 2 + n[0] * W / 2, (a0[1] + a1[1]) / 2 + n[1] * W / 2, gz)
        slab(b, c, (a1[0] - a0[0], a1[1] - a0[1], 0), (n[0], n[1], 0), (W, 0.36, Ls), "steel_edge")
    b.cube([gx - 0.62, Y0 - 0.1, gz - 0.3], [1.24, 1.1, 0.6], "steel_bright")
    b.cube([gx - 0.3, Y0 + 0.95, gz - 0.25], [0.6, 0.85, 0.5], "steel_bright")


# ------------------------------------------------------------------ the whole -------------------------------------
def radiant_v2():
    m = Model("Paladin - Radiant v2", "sunforged", "tank (Radiant, sixth pass)", PALETTE, EMISSIVE)
    body = torso(m)
    faulds(body)
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
    # round 2: the gauntlets closed round the grip on the sword's bone (gen_models.grip_hand), each arm solved onto its
    # cuff with the elbow where a person's would be (the left hand above, the right below)
    e1 = hold(m, (la, lf, lw), "sword", top_hand, (0, 1, 0), 1, _unit((7.0, -1.0, 6.5)), (7.0, 27.2, -1.6))
    e2 = hold(m, (ra, rf, rw), "sword", low_hand, (0, 1, 0), -1, _unit((-7.0, 1.0, 6.5)), (-7.4, 26.4, -1.2))
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
    faulds(body)
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
    tip_y = 32.0 + 4.0 + 17.0 + 2.7             # G + ricasso + the blade + the point (greatsword) - fixes the pivot
    py = (tip_y - 2.0) / 2
    sword = m.bone("sword", [gx, py, gz], parent="arms", rotation=[180, 0, 0])
    greatsword(sword, gx, gz, front=1)
    # both gauntlets on the grip just under the pommel - on their own bone beside the sword's (a gauntlet built on
    # the turned bone would turn with it and face the wrong way); the grip runs 22.5..31.7 before the turn
    flip = lambda y: 2 * py - y
    top_hand, low_hand = (gx, flip(23.9), gz), (gx, flip(26.6), gz)
    hands = m.bone("hands", [0, TOP - 0.6, 0], parent="arms")
    # intended contact: the gauntlets close round the grip and meet the vambraces; the fauld lames lie over the thighs
    m.contact_ok += [("hands", "sword"), ("hands", "left_fore"), ("hands", "right_fore"), ("body", "left_thigh"),
                     ("body", "right_thigh")]
    la, lf, lw = arm(m, 1, parent="arms")
    ra, rf, rw = arm(m, -1, parent="arms")
    # round 2 (Oct 16): real gauntlets closed round the grip (gen_models.grip_hand), each arm solved onto its cuff, the
    # elbows where a man's are when he rests both hands on a planted sword - hanging beside the body a little forward
    # and out of it, the forearms doing the reaching (lovish, 07:10: "the elbows dont match human anatomy" - the solve
    # had swung them 1.6 px out past the shoulders and 3.5 forward, the upper arms raised like wings)
    clear = dict(avoid=("body", "tabard_front_0"), avoid_margin=1.35, avoid_elbow=True)
    e1 = hold(m, (la, lf, lw), "hands", top_hand, (0, 1, 0), 1, _unit((7.2, -2.4, 6.6)), (7.2, 27.6, -2.2), **clear)
    e2 = hold(m, (ra, rf, rw), "hands", low_hand, (0, 1, 0), -1, _unit((-7.2, 0.2, 6.8)), (-7.2, 26.9, -1.8), **clear)
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
