#!/usr/bin/env python3
"""The Sunforged Halberdier (vindicator_unit in the game) - a T1 anti-cavalry body through the Paladin pipeline
(claude/paladin-radiant.md, claude/sunforged-t1-silhouettes.md).

Silhouette: tall, narrow, upright - and the TALLEST T1 shape because of the one oversized prop: a halberd ~1.6x
his height, planted beside him, its head (an axe blade turned outward, a back hook, a long top spike) high over
the plume. Team colour where the RTS camera looks: the tabard front and back, the helm's plume, a tassel under the
halberd's head. Gothic white harness from the Paladin's lessons (octagon forms, few gold places): a visored sallet
with a tail over the nape - the visor and the bevor creased down the centre, the eye slit a real gap with a bar
between the sights, breaths on the right cheek - a gorget, three-lame pauldrons bent round the shoulders, turned
couters and knee cops, a mail skirt under tassets, a sidearm on the left hip where his left hand rests.

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
from gen_models import rounded, lathe  # noqa: E402
from gen_models import Bone, grip_hand, octagon_pole, _unit  # noqa: E402
from gen_models import slab, octagon_axle  # noqa: E402

PALETTE = {
    "plate": "#d8d1c2", "plate_dark": "#a39b8a", "visor": "#2e2a26",
    # the inside of the helm seen through the eye slit and the breaths: a dark, matt "plain" colour (no plate/steel
    # in the name - those are pulled toward bright steel), darkened further by the baked occlusion of the visor over it
    "hollow": "#5a554e", "mail": "#8f939a",
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


# ------------------------------------------------------------------ exact turned forms (lovish, Oct 16 07:14: "The
# blocks your using are sticking out from the characters making them look blocky and edges sticking out")
def ngon(bone, cx, cz, y0, y1, w, color, n=12, phase=None, **kw):
    """An upright prism of REGULAR n-gon section, w across the flats - gen_models.ngon_prism turned by `phase` degrees
    (default 180/n): a CORNER then runs down the front (the crease a visor's ridge follows) and the flats face where
    gen_models.lathe puts its slabs (15, 45, 75 ... degrees for n = 12), so a skull, the lathe dome over it and the
    shells laid on it meet edge to edge - rounded()'s nested boxes stair-step the outline between their corners."""
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


def _shell_frame(r0, r1, y0, y1, t):
    lean = math.atan2(r0 - r1, y1 - y0)
    return lean, (r0 + r1) / 2 - (t / 2) * math.cos(lean), (y0 + y1) / 2 - (t / 2) * math.sin(lean)


def shell(bone, cx, cz, y0, r0, y1, r1, a0, a1, n, t, color, **kw):
    """n plates over the arc a0..a1 (degrees from the front, + toward the unit's right, -x) of a surface turned about
    the vertical through (cx, cz) - gen_models.lathe over an arc, with its own radii: each plate's OUTER face passes r0
    from the axis at y0 and r1 at y1 along its centre line, so a shell stands off what it covers by a set amount and
    its outline is exact chords. A visor, a bevor, a sallet's tail, a mail skirt. The seams overlap by what the taper
    needs (a plate is as wide as the wider end's chord)."""
    d = (a1 - a0) / n
    lean, ri, yc = _shell_frame(r0, r1, y0, y1, t)
    length = math.hypot(r0 - r1, y1 - y0) + 0.04
    width = 2 * max(r0, r1) * math.tan(math.radians(d) / 2) + 0.04
    for k in range(n):
        th = math.radians(a0 + d * (k + 0.5))
        c = (cx - ri * math.sin(th), yc, cz - ri * math.cos(th))
        bone.box_at(c, (width, length, t), color, rotation=[math.degrees(lean), math.degrees(th), 0], pivot=list(c),
                    **kw)


def on_shell(bone, cx, cz, y0, r0, y1, r1, th_deg, u, y, size, color, proud=0.02, **kw):
    """A small plate laid ON the shell plate centred at th_deg (shell()'s geometry): u px along the plate from its
    centre line, at height y, standing `proud` off its face - a rivet, a breath, the hinge of a visor."""
    lean = math.atan2(r0 - r1, y1 - y0)
    th = math.radians(th_deg)
    r = r0 + (r1 - r0) * (y - y0) / (y1 - y0)
    out = (-math.cos(lean) * math.sin(th), math.sin(lean), -math.cos(lean) * math.cos(th))
    tang = (-math.cos(th), 0.0, math.sin(th))
    k = proud + size[2] / 2
    c = (cx - r * math.sin(th) + u * tang[0] + k * out[0], y + k * out[1], cz - r * math.cos(th) + u * tang[2] + k * out[2])
    bone.box_at(c, size, color, rotation=[math.degrees(lean), th_deg, 0], pivot=list(c), **kw)


def torso(m):
    b = m.bone("body", [0, HIP, 0])
    # the hips: a mail skirt under the plate and the tabard (a layer: body, mail, plate, cloth), showing between the
    # tabard's panels and under the tassets - it was a dark box at the hips
    octagon(b, HIP - 2.2, HIP + 1.8, 5.8, 4.0, 0.65, "mail", zf=-4.0 * FRONT)
    # the cuirass: waist, the swell of the ribs, the chest, the shoulders - the ribs' band halves the ledge where the
    # 7.4 px chest met the 5.6 px waist (a 0.9 px shelf at the sides, 07:14)
    for (y0, y1, w, d, ch) in ((14.0, 17.0, 5.6, 3.8, 0.5), (16.8, 18.2, 6.6, 4.25, 0.6), (18.0, 22.4, 7.4, 4.6, 0.7),
                               (22.2, 24.2, 7.0, 4.2, 0.7)):
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
    # tassets: two lames on each thigh, hung from the belt - each turned about y with the thigh's curve, its outer
    # edge wrapping back (a flat plate across a round thigh stood its outer corner out past the leg)
    for s in (1, -1):
        for k in range(2):
            x0 = s * 1.7 - 1.45
            b.cube([x0, 13.0 - k * 1.7, -2.45 + 0.1 * k], [2.9, 1.9, 0.5], "plate" if k == 0 else "plate_dark",
                   rotation=[-8 - 4 * k, -s * 18, s * 6], pivot=[s * 1.7, 15.2, -2.2])
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
        for fx in ((-1.3, 1.1) if s < 0 else ()):                                                     # folds (front)
            p.cube([fx, bottom + 0.6, z + s * 0.2 - 0.05], [0.25, 5.5, 0.15], "tabard_dark")
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
    band and the sun at its centre; a feather plume (plume()) rising from the back of the crown."""
    hy = SHOULDER_Y + 1.6
    h = m.bone("head", [0, hy, 0], parent="body")
    cz, R = -0.1, 2.8
    # the skull ROUND (lovish, Oct 16: "the helmets could curve a bit more ... a bit boxy"): a regular 12-gon with a
    # corner down the front (rounded()'s nested boxes stair-stepped between their corners, 07:14), the lathe dome over
    # it on the same twelve faces
    ngon(h, 0, cz, hy + 0.6, hy + 4.0, 2 * R, "plate")                                                  # skull
    lathe(h, 0, cz, R, R, ((hy + 3.95, 1.0, hy + 5.35, 0.8), (hy + 5.3, 0.8, hy + 6.25, 0.42)), "plate")
    rounded(h, 0, cz, hy + 5.85, hy + 6.4, 2.6, 2.6, "plate", k=2)                                      # the crown's cap
    # the tail: the skull's back sweeping on down and out over the nape in three plates from its lower edge (it was one
    # 5 x 2.6 x 2.8 block standing off the back of the head)
    shell(h, 0, cz, hy - 0.7, 3.3, hy + 0.95, 2.84, 102, 258, 3, 0.36, "plate")
    # THE FACE (lovish, 07:13: "the face just being a black blob isn't right ... masks or helmets that are more defined
    # and developed with a shadow covering facial features instead"). A visor and a bevor stand proud of the skull, both
    # creased down the centre; between them the eye slit, two sights either side of a bar, is a real gap: what shows in
    # it is the skull's face 0.4 px back in a dark matt colour, under the visor's overhanging lower edge - the baked sky
    # light (tools/unit_light.py) shades it deepest at the top, so it reads as the slit's shadow, not a painted band
    shell(h, 0, cz, hy + 2.46, R + 0.04, hy + 3.2, R + 0.04, -60, 60, 4, 0.7, "hollow")                # the slit's depth
    h.box_at((0, hy + 2.83, cz - 3.02), (0.42, 0.78, 0.42), "plate", rotation=[0, 45, 0],
             pivot=[0, hy + 2.83, cz - 3.02])                                                          # the bar between
    VIS = (hy + 3.12, 3.2, hy + 4.08, 3.04)                    # the visor: its lower edge the most proud (it overhangs)
    shell(h, 0, cz, *VIS, -90, 90, 6, 0.42, "plate")
    h.box_at((0, hy + 3.6, cz - 3.26), (0.85, 0.85, 0.3), "sun", rotation=[0, 0, 45], pivot=[0, hy + 3.6, cz - 3.26])
    for s in (1, -1):                                                                                 # its hinges
        on_shell(h, 0, cz, *VIS, s * 75, s * 0.42, hy + 3.6, (0.42, 0.42, 0.16), "gold_dark")
    # the bevor: from under the slit, bulging to the chin, then turning in under it to the gorget; the chin plates
    # span the front only, so its lower edge sweeps up toward the ears - a clear edge over the gorget's shadow
    BEV = (hy + 0.9, 3.34, hy + 2.5, 3.02)
    shell(h, 0, cz, *BEV, -60, 60, 4, 0.42, "plate")
    slope = (BEV[3] - BEV[1]) / (BEV[2] - BEV[0])
    for s in (1, -1):                                          # its cheeks rise to the visor: the slit ends at the sides
        shell(h, 0, cz, BEV[0], BEV[1], hy + 3.12, BEV[1] + slope * (hy + 3.12 - BEV[0]), 60 if s > 0 else -90,
              90 if s > 0 else -60, 1, 0.42, "plate")
    CHIN = (hy - 0.55, 2.7, hy + 0.92, 3.34)
    shell(h, 0, cz, *CHIN, -60, 60, 4, 0.42, "plate")
    # the ridge down the bevor's crease (a square bar turned 45: a sharp line of light), the breaths on the right cheek
    zc0, zc1 = BEV[1] / math.cos(math.radians(15)), BEV[3] / math.cos(math.radians(15))
    slab(h, (0, (BEV[0] + BEV[2]) / 2, cz - (zc0 + zc1) / 2), (0, BEV[2] - BEV[0], zc0 - zc1), (1, 0, 1),
         (0.3, 0.3, BEV[2] - BEV[0] - 0.1), "plate")
    for th, u in ((45, -0.55), (45, 0.0), (45, 0.55)):
        on_shell(h, 0, cz, *BEV, th, u, hy + 1.75, (0.2, 0.52, 0.1), "hollow")
    plume(h, hy, cz)
    return h


# the feathers of the plume: (yaw spread in degrees, length scale, base x offset); the middle one tallest
PLUME_FEATHERS = ((-26, 0.78, -0.26), (-13, 0.92, -0.13), (0, 1.0, 0.0), (13, 0.92, 0.13), (26, 0.78, 0.26))
# each feather's segments: (length, width across, lean back from upright in degrees) - an arc rising and curling back
PLUME_SEGS = ((1.25, 0.92, 12), (1.15, 0.8, 36), (1.0, 0.64, 62), (0.85, 0.48, 88))


def plume(h, hy, cz):
    """A feather plume rising from a gold socket at the back of the crown: five thin, tapering feathers fanned side to
    side, each an arc of four segments rising and curling back to level - flat side up, so the RTS camera sees the
    team colour. (lovish, Oct 16 14:51: "a weird blob hanging on the back of the units head" - in his build the
    sallet's tail was a block standing off the nape; and the plume here was three 1.4 px team-colour boxes stepping
    back and down from the crown, the last one hanging behind the head.)"""
    lean = math.radians(22)
    base = (0.0, hy + 5.75, cz + 1.25)                      # on the dome's back, below the crown cap
    up = (0.0, math.cos(lean), math.sin(lean))
    slab(h, (base[0], base[1] + up[1] * 0.45, base[2] + up[2] * 0.45), up, (1, 0, 0), (0.55, 0.55, 1.0), "gold")
    top = (base[0], base[1] + up[1] * 0.9, base[2] + up[2] * 0.9)
    for k, (yaw, scale, x0) in enumerate(PLUME_FEATHERS):
        phi = math.radians(yaw)
        p = [top[0] + x0, top[1], top[2]]
        for j, (L, w, th) in enumerate(PLUME_SEGS):
            L *= scale
            t = math.radians(th)
            d = (math.sin(t) * math.sin(phi), math.cos(t), math.sin(t) * math.cos(phi))
            across = (math.cos(phi), 0.0, -math.sin(phi))
            c = (p[0] + d[0] * L / 2, p[1] + d[1] * L / 2, p[2] + d[2] * L / 2)
            col = "tabard_dark" if (j == len(PLUME_SEGS) - 1 or k % 2) else "tabard"
            slab(h, c, d, across, (w, 0.22, L + 0.12), col)
            p = [p[0] + d[0] * L, p[1] + d[1] * L, p[2] + d[2] * L]


PAULDRON = ((-1.0, 2.35, 2.05, 12, "tabard"), (0.5, 2.0, 1.9, 33, "tabard_dark"), (1.1, 1.6, 1.7, 55, "plate"))


def pauldron(body, side):
    """Three lames fanned down over the shoulder, each BENT round it - a front, a top and a back plate on an arc about
    the lame's own axis (a cylinder's section), so the front and back hang down beside the arm (lovish, 07:14: the
    blocks "sticking out from the characters ... edges sticking out" - the lames were flat 4.8 px deep shelves whose
    square corners stood out ahead of and behind the arm, the top one 2 px out past the arm's side). The arcs share an
    axis point inside the shoulder, each lower lame on a smaller radius: they lie like roof tiles, the top lame in team
    colour. Each plate: rotation x (round the arc) then z (the lame's droop) about that point - one cube."""
    P = (side * SHOULDER_X, SHOULDER_Y - 0.75, 0.0)
    t = 0.38
    for (a, L, r, droop, col) in PAULDRON:
        x0 = P[0] + side * a if side > 0 else P[0] + side * (a + L)
        for phi in (-54, 0, 54):
            c = 2 * r * math.tan(math.radians(27)) + 0.08
            body.cube([x0, P[1] + r - t, -c / 2], [L, t, c], col, rotation=[phi, 0, -side * droop], pivot=list(P))


def arm(m, side, parent, hand=True):
    pre = "left" if side > 0 else "right"
    sx, sy = side * SHOULDER_X, SHOULDER_Y
    elbow_y, wrist_y = sy - UPPER, sy - UPPER - FORE
    up = m.bone(pre + "_arm", [sx, sy, 0], parent=parent)
    # the rerebrace TURNED (a regular octagon; two crossed boxes stepped at every corner and stood 0.2 px out at the
    # sides), ending inside the couter
    octagon_pole(up, sx, 0, elbow_y + 0.4, sy + 0.2, 2.25, "plate")                                    # rerebrace
    fo = m.bone(pre + "_fore", [sx, elbow_y, 0], parent=pre + "_arm")
    # the couter: a turned joint round the hinge - an octagonal cylinder along x, the axis the forearm bends about, so
    # it reads as one rounded elbow at any bend (lovish, 07:10: "the elbows don't match human anatomy" - it was a 2.5 px
    # box whose corners swung out past the arm at every bend); the fan on its outer face
    # (centred 0.12 px outboard: its inner end stays as far out as the old box's, clear of the chest in the READY guard)
    octagon_axle(fo, (sx + side * 0.12, elbow_y, 0), 2.35, 2.5, "plate")                               # couter
    fo.box_at((sx + side * 1.3, elbow_y, 0.1), (0.3, 1.7, 1.7), "plate_dark", rotation=[45, 0, 0],
              pivot=[sx + side * 1.3, elbow_y, 0.1])
    # the vambrace and the gauntlet's bell cuff TURNED (regular octagons; they were a 2.0 and a 2.4 px square box -
    # the wrist read as a box on a box where it meets the new hand); the vambrace's top tucked into the couter
    octagon_pole(fo, sx, 0, wrist_y + 0.4, elbow_y - 0.4, 2.06, "plate")                               # vambrace
    octagon_pole(fo, sx, 0, wrist_y - 0.05, wrist_y + 0.85, 2.5, "plate_dark")                         # bell cuff
    if hand:
        # the free hand, hanging easy beside the sidearm: a half-open gauntlet, palm to the thigh, thumb forward
        # (gen_models.grip_hand round a thin imaginary rod; it was a 1.8 x 2.1 x 2.0 block - lovish Oct 16: "looks
        # like a block mesh for hands")
        grip_hand(fo, None, (0, 0, -1), 0.5, side, "plate", accent="plate_dark", back=(0, 1, 0), curl=0.36,
                  cuff="plate_dark", wrist=(sx, wrist_y + 0.3, 0))
    return pre + "_arm", pre + "_fore", (sx, wrist_y + 0.3, 0)


GRIP_R = 0.57              # the grip wrap's radius (the haft is 0.96 across the flats, the wrap 0.09 proud)


def gauntlet_grip(bone, c, back):
    """The right gauntlet closed round the haft (gen_models.grip_hand): steel finger lames in two tones, the thumb
    over the index finger, the cuff toward the forearm (`back`: from the grip toward the elbow, as solved)."""
    return grip_hand(bone, c, (0, 1, 0), GRIP_R, -1, "plate", accent="plate_dark", back=back, cuff="plate_dark",
                     knuckle="plate", heel="plate_dark")


def legs(m):
    feet = []
    for side, thigh, shin in ((1, [5, 0, 0], [-8, 0, 0]), (-1, [-3, 0, 0], [-3, 0, 0])):
        pre = "left" if side > 0 else "right"
        hx = side * 1.7
        th = m.bone(pre + "_thigh", [hx, HIP, 0], rotation=thigh)
        # the cuisse turned (two crossed boxes stood out in fins at the sides), its foot inside the knee cop
        octagon_pole(th, hx, 0, 7.45, HIP, 2.3, "plate")                                               # cuisse
        sh = m.bone(pre + "_shin", [hx, 7.0, 0], parent=pre + "_thigh", rotation=shin)
        # the knee cop: a turned joint round the knee's hinge like the couter (a 2.6 px box, its corners out past the
        # leg at every step), the diamond boss on its front
        octagon_axle(sh, (hx, 7.0, 0), 2.5, 2.6, "plate")                                             # knee cop
        sh.box_at((hx, 6.85, -1.3), (1.1, 1.1, 0.34), "plate_dark", rotation=[0, 0, 45], pivot=[hx, 6.85, -1.3])
        # the greave turned (a regular octagon, it was a box) up into the knee cop, the gothic ridge down the shin's
        # front a square bar turned 45 (a sharp line; a flat strip stood off the greave at its ends); the calf's box,
        # stuck on the back of the greave, went
        octagon_pole(sh, hx, 0, 1.2, 6.5, 2.1, "plate")                                                # greave
        sh.box_at((hx, 3.65, -1.04), (0.34, 3.9, 0.34), "plate", rotation=[0, 45, 0], pivot=[hx, 3.65, -1.04])
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
    # the haft ONE turned ash pole, butt to socket (lovish, Oct 16: "the stick for the halberd looks like seperate
    # poles"): it was a 0.9 px square - one lit and one dark face, a light and a dark stripe that read as two thin poles
    # - cut into five lengths by two gold collars and a dark grip 0.07 proud of it. Now a regular octagon in one piece
    # (eight faces, eight shades: round), no collars along it, and the grip a thin wrap the fist hides
    octagon_pole(hb, gx, gz, 0.6, top - 0.1, 0.96, "haft")
    octagon_pole(hb, gx, gz, 0.0, 1.1, 1.12, "steel")                                                # butt ferrule
    octagon_pole(hb, gx, gz, grip_y - 1.9, grip_y + 1.75, 2 * GRIP_R, "leather_dark")                 # the grip wrap
    for s in (1, -1):                                                                              # langets
        hb.cube([gx + s * 0.45 - 0.1, top - 6.0, gz - 0.2], [0.2, 6.0, 0.4], "steel")
    hb.cube([gx - 0.75, top - 0.6, gz - 0.75], [1.5, 1.8, 1.5], "steel")                           # socket
    # the axe blade, turned outward (-x for a right-hand halberd). Sharper (lovish, Oct 16: "the halberd itself could
    # be a little more ... realistic to seem sharper, it kinda seems dull with the pixelated look"): the six stacked
    # rows read as a staircase; now three thin plates make the convex body and the bright edge is ONE arc of short
    # rotated segments - a clean crescent line, the brightest thing on the weapon
    y0, H, x1, t = top + 0.4, 7.8, gx - 0.5, 0.3
    hb.cube([x1 - 2.4, y0, gz - t / 2], [2.4, H, t], "steel")                                         # the body
    hb.cube([x1 - 3.0, y0 + 0.5, gz - t / 2], [3.0, H - 1.0, t], "steel")
    hb.cube([x1 - 4.2, y0 + 1.7, gz - t / 2], [4.2, H - 3.4, t], "steel")
    R, sag = 4.8, 2.0                                    # the edge: a circle through both blade tips and the belly
    ecx, ecy = x1 - 4.6 + R, y0 + H / 2
    a_max = math.asin(min(1.0, (H / 2) / R))
    n = 6
    for k in range(n):
        a0 = -a_max + 2 * a_max * k / n
        a1 = -a_max + 2 * a_max * (k + 1) / n
        am = (a0 + a1) / 2
        px, py = ecx - R * math.cos(am), ecy + R * math.sin(am)
        L = 2 * R * math.sin((a1 - a0) / 2) + 0.12
        hb.box_at((px + 0.12 * math.cos(am), py, gz), (0.42, L, t + 0.08), "steel_edge",
                  rotation=[0, 0, -math.degrees(am)], pivot=[px + 0.12 * math.cos(am), py, gz])
    blade_top = y0 + H
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
    for w, h in ((0.9, 3.0), (0.65, 3.0), (0.4, 2.8)):
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
    # the haft 0.4 px further out and 0.2 forward than before the gauntlet (Oct 16): the fist's cuff ends 1.8 px behind
    # the haft (a palm's length), and from -6.1 the solved elbow tucked into the back plate or, kept out of it, the
    # upper arm stood in the haft's swing plane in the thrust
    gx, gz, grip_y = -6.5, -2.8, 16.6
    hb = halberd(m, gx, gz, grip_y)
    m.contact_ok += [("halberd", "right_fore"), ("halberd", "tassel")]   # the fist on the haft; the tassel tied on
    # the fist closed round the haft and the arm solved onto its cuff: the wrist where the hand's cuff ends, the
    # forearm pointing along the hand (a straight wrist). The hand's frame follows the forearm, so solve, read the
    # solved forearm's direction, rebuild the hand along it and solve again (twice is enough: < 0.1 px, < 2 deg)
    C = (gx, grip_y, gz)
    # where a person's elbow is for this hold (lovish, 07:10: "the elbows don't match human anatomy"): a hand on a pole
    # close in front of the hip draws the elbow back past the body's side - natural, but without a target the solve
    # let it ride 2.35 px behind the shoulder once the cuirass grew its ribs' band; out from the side and a little back
    ELBOW_AT = (-6.0, 18.8, 1.2)
    back = _unit((0.4, 0.25, 1.3))                     # the first guess: the old wrist target, behind and inboard
    for _ in range(4):
        used = back
        g = gauntlet_grip(Bone(m, "_scratch", [0, 0, 0]), C, used)
        e1 = m.solve_arm(ra, rf, rw, g["wrist"], elbow_out=1, elbow_down=1, point_at=g["aim"],
                         avoid=("body",), avoid_margin=1.3, avoid_elbow=True, elbow_at=ELBOW_AT)
        el, wr = m.fk(rf, m.bones[rf].pivot), m.fk(rf, rw)
        back = _unit(tuple(0.5 * used[k] + 0.5 * (el[k] - wr[k]) / math.dist(el, wr) for k in range(3)))
    off = math.degrees(math.acos(max(-1.0, min(1.0, sum(back[k] * used[k] for k in range(3))))))
    print("  halberd grip: cuff %.1f deg off the solved forearm, wrist %.2f px off the cuff" % (off, e1))
    gauntlet_grip(hb, C, used)
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
