#!/usr/bin/env python3
"""The Gravebound Tithe Binder (design sheet: .scratch/unit1/design-sheet.md, sections 2-4 and 7.3): a hooded dead
knight-errant hovering above his grave, a soul lantern at his chest, and a wheel of up to ten weapons chained to his
ribs. Our own design - nothing copied from another mod, an anime or a game.

The rig (DSL bones; the hand-written model subclass animates them by name):

    hover                                 root, no cubes: the runtime bob (a y offset)
      body                                the robe column (skirt, hem band, belt chain)
        tatter_f / tatter_b / tatter_l / tatter_r      spring flaps under the hem
        hem_chain_1 / _2 / _3 -> hem_chain_<n>_b       two-segment spring chains into the hover gap
        chest                             tunic, sternum, ribs, bandolier
          mantle -> cape                  the TEAM top plane, shoulder lobes, bead trim; the cape swings behind
          head                            hood, void, bone mask
          right_arm -> right_forearm -> right_hand     (+ right_forearm/right_shackle_chain, a spring)
          left_arm  -> left_forearm  -> left_hand      (+ left_forearm/left_shackle_chain)
          lantern_chain -> lantern        a spring pendulum; the core is the body's one glow
      ring                                the weapon wheel (parent hover, NOT chest: the lean does not drag it)
        slot_1 .. slot_N                  one per bound weapon; pivot = the docked pommel, rotation = its azimuth
          weapon_k                        the weapon (pommel at the pivot, blade along local -z, built at the docked
                                          0.7 scale); rotation x = the tip's pitch (inner -12, outer +45)
            weapon_6_ball                 the flail's head on its own short chain (hangs plumb)

Coordinates: DSL px (16 = 1 block), y up, the unit faces -z, +x its left (design/models/README.md). Ground y = 0;
the lowest tatter tip at y ~5 is the hover gap. Weapons 2-10 are phantoms (soul_core body, soul_glow edge lines;
the renderer draws them translucent); weapon 1 - the Grave Hook - is real rusted iron.

    python tools/gen_tithe_binder.py                       # design/models/gravebound_tithe_binder.json (N = 10)
    python tools/gen_tithe_binder.py --weapons 3 --out .scratch/tb_N3.json   # a still of a younger Binder
    python tools/render_concept.py --check design/models/gravebound_tithe_binder.json
    python tools/dsl_to_game.py design/models/gravebound_tithe_binder.json \\
        --class TitheBinderModel --texture tithe_binder_unit --team tabard,tabard_dark
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT, rot_mat, mat_mul, transpose, euler  # noqa: E402

NAME = "gravebound_tithe_binder"

# sheet 2.3, exactly
PALETTE = {
    "bone": "#e4ddc8", "bone_dark": "#b9b09a", "bone_old": "#c6bca3", "socket": "#15120f",
    "robe": "#2a2729", "robe_edge": "#4b4448", "deepslate": "#4f5055", "deepslate_dark": "#35363b",
    "iron_rust": "#7a5a44", "iron_dark": "#5b4335",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
    "soul_glow": "#62f3cb", "soul_core": "#34dba3", "soul_thread": "#2f8f7f", "soul_dim": "#1b6d53",
    "white_hot": "#e9fff8",
}
EMISSIVE = ["soul_glow"]
UPGRADE = "tithe_of_iron"

# the wheel (sheet 3.2): inner tier slots 1-5, outer tier 6-10
DOCK_SCALE = 0.7
RING_PIVOT = (0.0, 27.0, 0.0)
# inner pommel radius 10, not the sheet's 8: the mantle's shoulder lobes reach x +-7.5 (to 8.6 px from the axis at their
# corners) at the inner tier's height, so at r 8 every pommel passing a lobe sits inside it (0.6 px at rest, 1.5 px in
# the move lean) and the side spokes cross the sleeves; r 10 clears the lobes at any wheel yaw
INNER = dict(r=10.0, y=27.0, pitch=-12.0)    # tips 12 deg below horizontal (DSL x < 0 lowers a -z pointing part)
OUTER = dict(r=12.0, y=33.0, pitch=45.0)     # tips 45 deg above
BASE_YAW = -45.0                              # slot 1 at the front-left (yaw < 0 turns -z toward +x, the unit's left)


def slot_layout(n):
    """[(k, tier, yaw, pivot, pitch)] for n bound weapons: inner spokes 360/min(n,5) apart from the front-left, outer
    spokes 72 apart offset 36 from the inner ones (they fill the gaps)."""
    out = []
    inner = min(n, 5)
    for k in range(1, n + 1):
        if k <= 5:
            tier, yaw = INNER, BASE_YAW + 360.0 / inner * (k - 1)
        else:
            tier, yaw = OUTER, BASE_YAW + 36.0 + 72.0 * (k - 6)
        d = (-math.sin(math.radians(yaw)), 0.0, -math.cos(math.radians(yaw)))
        pivot = (round(tier["r"] * d[0], 3), tier["y"], round(tier["r"] * d[2], 3))
        out.append((k, "inner" if k <= 5 else "outer", yaw, pivot, tier["pitch"]))
    return out


# ------------------------------------------------------------------ helpers ------------------------------------------
def world_rotation(m, parent, R):
    """The DSL rotation for a child of `parent` whose frame in the posed model is the world rotation matrix R."""
    rot = euler(mat_mul(transpose(m.frame(parent)), R))
    check = mat_mul(m.frame(parent), rot_mat(*rot))
    assert max(abs(check[i][j] - R[i][j]) for i in range(3) for j in range(3)) < 1e-3, "world_rotation"
    return rot


def link_chain(bone, top, n, step, sizes, colours, axis_len=None):
    """n chain links hanging straight down from `top` (x, y, z): link i's top at y - i*step, alternating the sizes
    (a face-on link and an edge-on one) and colours. Returns the y of the last link's bottom."""
    x, y, z = top
    bottom = y
    for i in range(n):
        w, h, d = sizes[i % len(sizes)]
        if axis_len is not None and i == n - 1:
            h = axis_len
        yt = y - i * step
        bone.cube([x - w / 2, yt - h, z - d / 2], [w, h, d], colours[i % len(colours)])
        bottom = yt - h
    return bottom


def loop_links(bone, y0, h, half_x, half_z, n_front, n_side, colours, inflate=0.1, upgrade=None,
               long=(2.0, 0.6), cross=(0.6, 1.0), skip_front=False):
    """A chain round a box: n_front links on the front and back faces, n_side on each side, alternating a long link
    lying along the face and a cross link standing out of it."""
    k = 0

    def put(cx, cz, along_x, is_long):
        nonlocal k
        a, b = long if is_long else cross        # (length along the face, depth out of it)
        size = [a, h, b] if along_x else [b, h, a]
        bone.box_at([cx, y0 + h / 2, cz], size, colours[0] if is_long else colours[1], inflate=inflate,
                    upgrade=upgrade)
        k += 1
    for face_z in ((-half_z, half_z) if not skip_front else (half_z,)):
        for i in range(n_front):
            cx = -half_x + (i + 0.5) * (2 * half_x / n_front)
            put(cx, face_z, True, i % 2 == 0)
    for face_x in (-half_x, half_x):
        for i in range(n_side):
            cz = -half_z + (i + 0.5) * (2 * half_z / n_side)
            put(face_x, cz, False, i % 2 == 0)
    return k


# ------------------------------------------------------------------ the body -----------------------------------------
def body(m):
    m.bone("hover", [0, 0, 0])
    b = m.bone("body", [0, 18, 0], "hover", rotation=[-4, 0, 0])     # rest: the top leans 4 forward
    b.cube([-4, 14, -3], [8, 8, 6], "robe")                           # waist
    b.cube([-5, 10, -4], [10, 5, 8], "robe")                          # skirt flare
    b.cube([-5.5, 9, -4.5], [11, 1.5, 9], "robe_edge")                # hem band
    # chain 1, the belt: 12 links at y 17-18 round the waist (4 front, 4 back, 2 per side)
    loop_links(b, 17.0, 1.0, 4.3, 3.3, 4, 2, ("iron_rust", "iron_dark"))

    # tatters: jagged strips under the hem (front tips y 5 / 6 / 5.5)
    tf = m.bone("tatter_f", [0, 10, -4], "body")
    tf.cube([-4.5, 5, -4.3], [2.5, 5, 0.8], "robe")
    tf.cube([-1.5, 6, -4.3], [3, 4, 0.8], "robe_edge")
    tf.cube([1.5, 5.5, -4.3], [3, 4.5, 0.8], "robe")
    tb = m.bone("tatter_b", [0, 10, 4], "body")                       # mirrored to the back, 1 px longer (from 11)
    tb.cube([-4.5, 5, 3.5], [2.5, 6, 0.8], "robe")
    tb.cube([-1.5, 5.5, 3.5], [3, 5.5, 0.8], "robe")
    tb.cube([1.5, 5, 3.5], [3, 6, 0.8], "robe")
    for s, side in ((1, "l"), (-1, "r")):
        t = m.bone("tatter_" + side, [5 * s, 10, 0], "body")
        x0 = 4.7 if s > 0 else -5.5
        t.cube([x0, 5.5, -3.3], [0.8, 4.5, 3], "robe_edge")
        t.cube([x0, 6.0, 0.3], [0.8, 4.5, 3], "robe_edge")

    # chains 2-4, the hem chains: from the belt, front-right, front-left, back; two segments each (3 links, then 2
    # links + a broken shackle, tip at y ~6). The skirt flares to z -/+4.5, so each chain hangs from the belt's outer
    # face 0.6 px out and swings 14 deg out over the flare (the rest angle the springs settle to)
    for k, (px, pz, out) in enumerate(((-3.5, -3.6, -1), (3.5, -3.6, -1), (0.0, 3.6, 1)), start=1):
        za = pz + 0.6 * out
        a = m.bone("hem_chain_%d" % k, [px, 17, pz], "body", rotation=[14 * -out, 0, 0])
        link_chain(a, (px, 17, za), 3, 2.0, ((1, 2, 0.5), (0.5, 2, 1)), ("iron_rust", "iron_dark"))
        bb = m.bone("hem_chain_%d_b" % k, [px, 11, za], "hem_chain_%d" % k, rotation=[6 * out, 0, 0])
        link_chain(bb, (px, 11, za), 2, 1.8, ((1, 2, 0.5), (0.5, 2, 1)), ("iron_rust", "iron_dark"))
        bb.cube([px - 1, 6, za - 1], [2, 1.5, 2], "iron_rust")       # the broken shackle, tip y 6

    # chest
    c = m.bone("chest", [0, 22, 0], "body")
    c.cube([-4.5, 22, -2.5], [9, 9, 5], "robe")                       # tunic
    c.cube([-1.5, 24, -2.8], [3, 5, 0.6], "bone")                     # sternum through the torn robe
    for i in range(3):
        c.cube([-3.5, 24 + 2 * i, -2.7], [7, 0.8, 0.5], "bone_old")    # ribs
    # chain 5, the bandolier: 7 links from the right hip (-4, 22.5) to the left shoulder (4, 30), on the tunic
    p0, p1 = (-4.0, 22.5), (4.0, 30.0)
    ang = math.degrees(math.atan2(p1[0] - p0[0], p1[1] - p0[1]))     # from vertical, toward +x
    for i in range(7):
        t = (i + 0.5) / 7
        cx, cy = p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t
        c.box_at([cx, cy, -2.9], [1.2, 2, 0.6], "iron_rust" if i % 2 == 0 else "iron_dark",
                 rotation=[0, 0, -ang], pivot=[cx, cy, -2.9])

    # mantle (TEAM top plane) and cape
    mt = m.bone("mantle", [0, 31, 0], "chest")
    mt.cube([-7, 29, -4], [14, 3, 8], "tabard")
    mt.cube([-7.5, 26, -4.2], [4, 4, 8.4], "tabard_dark")
    mt.cube([3.5, 26, -4.2], [4, 4, 8.4], "tabard_dark")
    mt.cube([-7.2, 28.6, -4.2], [14.4, 0.8, 8.4], "bone_old")         # bead trim
    cp = m.bone("cape", [0, 30, 3.6], "mantle")
    cp.cube([-6, 22, 3.5], [12, 8, 1], "tabard")

    # head (hood + bone mask); rest [6, 0, 0] against the body's -4
    h = m.bone("head", [0, 31, -0.5], "chest", rotation=[6, 0, 0])
    h.cube([-4.5, 31, -4.5], [9, 9, 9], "tabard")                     # hood shell
    h.cube([-3.5, 40, -3.5], [7, 2, 7], "tabard")                     # hood crown
    h.cube([-1.5, 41.5, -1], [3, 2, 4], "tabard_dark", rotation=[-25, 0, 0], pivot=[0, 41.5, 1])   # drooped peak
    h.cube([-4.7, 31, -4.8], [9.4, 9, 0.8], "tabard_dark")            # hood rim
    h.cube([-3.5, 32, -4.9], [7, 7, 0.5], "socket")                   # hood void
    h.cube([-2.5, 32.5, -5.1], [5, 5.5, 0.6], "bone")                 # mask
    h.cube([-2.8, 36.2, -5.3], [5.6, 1, 0.6], "bone_old")             # brow ridge
    for ex in (0.6, -1.8):
        h.cube([ex, 34.2, -5.25], [1.2, 1, 0.3], "socket")            # eye sockets
        h.cube([ex + 0.35, 34.45, -5.32], [0.5, 0.5, 0.1], "soul_dim")   # glints (not emissive)
    h.cube([-2, 31.5, -5], [4, 1.2, 0.6], "bone_dark")                # jaw
    # Tithe of Iron (7.3): a bone grave-seal on the crown, a rune socket, a rusted collar round the hood base
    h.box_at([0, 42.4, -0.5], [6, 0.8, 6], "bone", upgrade=UPGRADE)
    h.box_at([0, 42.4, -0.5], [6, 0.8, 6], "bone", rotation=[0, 45, 0], pivot=[0, 42.4, -0.5], upgrade=UPGRADE)
    h.box_at([0, 42.95, -0.5], [2, 0.3, 2], "socket", upgrade=UPGRADE)
    loop_links(h, 31.9, 1.0, 4.85, 4.85, 2, 2, ("iron_rust", "iron_dark"), inflate=0.0, upgrade=UPGRADE,
               long=(1.5, 0.6), cross=(0.6, 1.0), skip_front=True)

    # arms: a wide sleeve, a bone forearm with a shackle and its broken chain, a bone hand (palm down at rest)
    for s, side in ((-1, "right"), (1, "left")):
        ax = 6.0 * s
        arm = m.bone(side + "_arm", [ax, 29, 0], "chest", rotation=[20, 0, 12 * s])
        arm.cube([ax - 2, 22, -2], [4, 8, 4], "robe")
        arm.cube([ax - 2.5, 21, -2.5], [5, 2, 5], "robe_edge")
        fore = m.bone(side + "_forearm", [ax, 22, 0], side + "_arm", rotation=[35, 0, 0])
        fore.cube([ax - 0.75, 15, -0.75], [1.5, 7, 1.5], "bone")
        fore.cube([ax - 1.75, 17, -1.75], [3.5, 1.5, 3.5], "iron_rust")
        hand = m.bone(side + "_hand", [ax, 15, 0], side + "_forearm")
        hand.cube([ax - 1.25, 13, -1.25], [2.5, 2, 2.5], "bone_dark")
        # three fingers spread across x so the palm faces down when the forearm points forward (the conductor's rest)
        for fx in (-0.85, 0.0, 0.85):
            hand.cube([ax + fx - 0.3, 10.8, -0.3], [0.6, 2.2, 0.6], "bone")
        tx = ax - 1.25 * s + 0.1 * s           # the thumb on the inner side (toward the body), splayed
        hand.cube([tx - 0.3, 11.6, 0.0], [0.6, 1.6, 0.6], "bone", rotation=[0, 0, -30 * s],
                  pivot=[tx, 13.2, 0.3])
    # the shackle chains (6-7) hang plumb from the shackle's underside (+z face at rest): rotation from the posed frame
    for s, side in ((-1, "right"), (1, "left")):
        ax = 6.0 * s
        piv = [ax, 17.75, 1.85]
        sc = m.bone(side + "_shackle_chain", piv, side + "_forearm")
        sc.rotation = world_rotation(m, side + "_forearm", rot_mat(0, 0, 0))
        link_chain(sc, (ax, piv[1], piv[2]), 3, 1.5, ((0.6, 1.6, 0.4), (0.4, 1.6, 0.6)), ("iron_dark",),
                   axis_len=1.2)

    # chain 8 + the lantern: hangs plumb from the sternum (the body leans 4 forward)
    lc = m.bone("lantern_chain", [0, 30, -4.6], "chest")
    lc.rotation = world_rotation(m, "chest", rot_mat(0, 0, 0))
    for i in range(3):
        w, d = ((0.8, 0.4), (0.4, 0.8))[i % 2]
        yt = 30 - i * 1.4
        lc.cube([-w / 2, yt - 1.6, -4.7 - 0.2 * i - d / 2], [w, 1.6, d], "iron_rust")
    ln = m.bone("lantern", [0, 26, -5.2], "lantern_chain")
    ln.cube([-2, 25, -7.2], [4, 1, 4], "deepslate_dark")              # caps
    ln.cube([-2, 20.5, -7.2], [4, 1, 4], "deepslate_dark")
    for px in (-2, 1.3):
        for pz in (-7.2, -3.9):
            ln.cube([px, 21.25, pz], [0.7, 4, 0.7], "deepslate")      # posts
    ln.cube([-1.4, 21.4, -6.6], [2.8, 3.2, 2.8], "soul_glow")         # the core: GLOW 1
    ln.cube([-0.75, 26, -5.45], [1.5, 1, 0.5], "deepslate")           # finial ring
    ln.cube([-0.5, 19, -5.7], [1, 1.5, 1], "deepslate")               # spike
    # Tithe of Iron: a bone cage over the core, four ribs leaning in at the top
    for (rx, rz, rot) in ((0, -7.55, [8, 0, 0]), (0, -2.85, [-8, 0, 0]), (-2.35, -5.2, [0, 0, -8]),
                          (2.35, -5.2, [0, 0, 8])):
        ln.box_at([rx, 23.5, rz], [0.6, 6, 0.6], "bone", rotation=rot, pivot=[rx, 20.5, rz], upgrade=UPGRADE)

    m.bone("ring", list(RING_PIVOT), "hover")


# ------------------------------------------------------------------ the ten weapons ----------------------------------
# Each weapon in its own frame at flight scale 1.0: the pommel at the origin, the blade along -z, x across, y up.
# Blades lie FLAT (their width along x, their thickness along y), so from the RTS camera a docked blade shows its
# whole width. A primitive is (centre, size, colour, rotation about its own centre).
def P(c, s, col, rot=(0, 0, 0)):
    return (tuple(c), tuple(s), col, tuple(rot))


def seg_along(p0, yaw, length, width, thick, col, y=0.0):
    """A flat segment of `length` from p0 (x, z) heading yaw degrees (0 = -z, + turns toward -x), and its end."""
    dx, dz = -math.sin(math.radians(yaw)), -math.cos(math.radians(yaw))
    c = (p0[0] + dx * length / 2, y, p0[1] + dz * length / 2)
    return P(c, (width, thick, length), col, (0, yaw, 0)), (p0[0] + dx * length, p0[1] + dz * length)


def edge_of(prim, side, col="soul_glow", t=0.4, inset=0.0):
    """A glowing edge line along one long side (side +1 = local +x) of a flat yawed segment."""
    (cx, cy, cz), (w, h, l), _c, (rx, ry, rz) = prim
    nx, nz = math.cos(math.radians(ry)), -math.sin(math.radians(ry))     # the segment's local +x in the x-z plane
    off = side * (w / 2 - inset)
    return P((cx + nx * off, cy, cz + nz * off), (t, t, l * 0.92), col, (0, ry, 0))


def grave_hook():
    """1. His own: a bone haft, a rusted iron sickle blade curling 8 px to the side, one glowing edge."""
    out = [P((0, 0, 0), (2, 2, 1), "iron_dark"),                       # pommel ring
           P((0, 0, -6.0), (1, 1, 11), "bone")]                         # haft z -0.5 .. -11.5
    p = (0.0, -10.5)
    blade = []
    for yaw, length, width in ((30, 5, 3), (90, 4, 3), (150, 3, 2)):   # cumulative 30 / +60 / +60: an 8 px hook
        prim, p = seg_along(p, yaw, length, width, 1.0, "iron_rust")
        blade.append(prim)
    out += blade
    out += [edge_of(b, -1) for b in blade]                              # the inner (cutting) edge glows
    return out


def seax(sign):
    yaw = 12 * sign
    prims = [P((0, 0, -3.0), (0.8, 1, 3), "soul_core"),                 # grip
             P((0, 0, -4.8), (2.6, 1.0, 0.6), "soul_core"),             # guard
             P((0, 0, -9.1), (2, 0.8, 8), "soul_core")]                 # blade
    prims.append(P((0.2, 0, -13.5), (1.4, 0.7, 1.4), "soul_core", (0, 45, 0)))    # the broken-back tip
    prims.append(P((-0.9, 0, -9.1), (0.4, 0.4, 7.4), "soul_glow"))     # the edge
    out = []
    for (c, s, col, rot) in prims:
        R = rot_mat(0, yaw, 0)
        c2 = tuple(sum(R[i][k] * c[k] for k in range(3)) for i in range(3))
        out.append(P(c2, s, col, (rot[0], rot[1] + yaw, rot[2])))
    return out


def twin_seaxes():
    """2. A pair in one slot, splayed +-12, their chains joined at a ring."""
    return [P((0, 0, 0), (1.4, 1.4, 0.8), "soul_core")] + seax(1) + seax(-1)


def hand_axe():
    """3. Bearded hand axe: a haft and a flat head out to the side with a beard sweeping back."""
    return [P((0, 0, 0), (1.4, 1.4, 1.0), "soul_core"),
            P((0, 0, -6.5), (1, 1, 13), "soul_core"),
            P((2.6, 0, -11.3), (4.2, 1, 3.4), "soul_core"),             # head, out along +x
            P((4.0, 0, -8.8), (2.0, 1, 2.6), "soul_core", (0, -20, 0)),  # beard, back toward the pommel
            P((4.85, 0, -10.4), (0.4, 0.4, 5.6), "soul_glow")]          # the edge


def arming_sword():
    """4. Arming sword: pommel, grip, cross guard, a flat blade in two widths and a diamond tip."""
    return [P((0, 0, -0.8), (1.6, 1.6, 1.6), "soul_core"),
            P((0, 0, -3.1), (1, 1, 3), "soul_core"),
            P((0, 0, -5.1), (5, 1, 1), "soul_core"),
            P((0, 0, -9.6), (2, 0.8, 8), "soul_core"),
            P((0, 0, -15.6), (1.6, 0.8, 4), "soul_core"),
            P((0, 0, -17.9), (1.2, 0.7, 1.2), "soul_core", (0, 45, 0)),
            P((1.0, 0, -11.5), (0.4, 0.4, 11.5), "soul_glow"),
            P((-1.0, 0, -11.5), (0.4, 0.4, 11.5), "soul_glow")]


def boar_spear():
    """5. Boar spear: a long haft, the lugs (a crossbar), a leaf head and its point."""
    return [P((0, 0, 0), (1.4, 1.4, 1.0), "soul_core"),
            P((0, 0, -11), (1, 1, 22), "soul_core"),
            P((0, 0, -21.6), (3, 0.6, 0.6), "soul_core"),               # lugs
            P((0, 0, -25.0), (3, 0.6, 6), "soul_core"),                 # leaf head
            P((0, 0, -28.0), (1.6, 0.55, 1.6), "soul_core", (0, 45, 0)),
            P((0, 0, -25.0), (3.4, 0.35, 4.6), "soul_glow")]            # the edge round the leaf


def flail():
    """6. Flail: the haft only - the links and the spiked ball hang from weapon_6_ball (flail_head)."""
    return [P((0, 0, 0), (1.6, 1.6, 1.0), "soul_core"),
            P((0, 0, -5.0), (1.2, 1.2, 10), "soul_core"),
            P((0, 0, -10.0), (1.8, 1.8, 0.6), "soul_glow")]             # the glowing ferrule the chain hangs from


def flail_head():
    """The flail's 3 links and its ball (6 spikes), hanging along the bone's local -z from the haft's end."""
    out = []
    for i in range(3):
        w, h = ((1.0, 0.4), (0.4, 1.0))[i % 2]
        out.append(P((0, 0, -1.0 - 2.0 * i), (w, h, 2.0), "soul_core"))
    c = -8.0
    out.append(P((0, 0, c), (4, 4, 4), "soul_core"))
    for v in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        out.append(P((2.4 * v[0], 2.4 * v[1], c + 2.4 * v[2]), (1, 1, 1), "soul_glow" if v[2] == -1 else "soul_core"))
    return out


def war_maul():
    """7. War maul: a heavy head across the haft's end, a back spike."""
    return [P((0, 0, 0), (1.8, 1.8, 1.2), "soul_core"),
            P((0, 0, -8.0), (1.2, 1.2, 16), "soul_core"),
            P((0, 0, -17.5), (7, 4, 5), "soul_core"),
            P((-4.5, 0, -17.5), (2, 1, 1), "soul_core"),                # back spike
            P((3.55, 0, -17.5), (0.4, 4.2, 5.2), "soul_glow")]          # the striking face


def scythe():
    """8. Scythe: a long snath, two grip pegs, a blade curving 14 px out and back toward the pommel."""
    out = [P((0, 0, 0), (1.4, 1.4, 1.0), "soul_core"),
           P((0, 0, -11), (1, 1, 22), "soul_core"),
           P((1.0, 0, -6.0), (2, 0.6, 0.6), "soul_core"),
           P((1.0, 0, -13.0), (2, 0.6, 0.6), "soul_core")]
    p = (0.0, -21.5)
    blade = []
    for yaw, length, width in ((-80, 5.5, 2.6), (-102, 5, 2.0), (-128, 4.2, 1.4)):
        prim, p = seg_along(p, yaw, length, width, 0.6, "soul_core")
        blade.append(prim)
    out += blade
    out += [edge_of(b, +1) for b in blade]                              # the concave edge (toward the pommel)
    return out


def greatsword():
    """9. Greatsword: a big flat blade, a wide guard, a long grip, a heavy pommel."""
    return [P((0, 0, -1.0), (2, 2, 2), "soul_core"),
            P((0, 0, -4.8), (1.2, 1.2, 6), "soul_core"),
            P((0, 0, -8.6), (8, 1.2, 1.5), "soul_core"),
            P((0, 0, -17.4), (3, 1, 16), "soul_core"),
            P((0, 0, -29.4), (2.4, 1, 8), "soul_core"),
            P((0, 0, -33.6), (1.7, 0.9, 1.7), "soul_core", (0, 45, 0)),
            P((1.5, 0, -20.8), (0.4, 0.4, 23), "soul_glow"),
            P((-1.5, 0, -20.8), (0.4, 0.4, 23), "soul_glow")]


def halberd():
    """10. Halberd: the longest haft, an axe blade to one side, a back spike, a top spike."""
    return [P((0, 0, 0), (1.8, 1.8, 1.2), "soul_core"),
            P((0, 0, -15.0), (1.2, 1.2, 30), "soul_core"),
            P((4.0, 0, -27.0), (7, 1, 5), "soul_core"),                 # axe blade, out along +x
            P((-2.6, 0, -27.0), (4, 1, 1), "soul_core"),                # back spike
            P((0, 0, -33.5), (0.8, 1.2, 7), "soul_core"),               # top spike
            P((7.6, 0, -27.0), (0.4, 1.2, 5.4), "soul_glow")]           # the edge


WEAPONS = {1: ("Grave Hook", grave_hook), 2: ("Twin Seaxes", twin_seaxes), 3: ("Bearded Hand Axe", hand_axe),
           4: ("Arming Sword", arming_sword), 5: ("Boar Spear", boar_spear), 6: ("Flail", flail),
           7: ("War Maul", war_maul), 8: ("Scythe", scythe), 9: ("Greatsword", greatsword), 10: ("Halberd", halberd)}


def place(bone, origin, prims, scale=DOCK_SCALE):
    """Primitives in the weapon frame -> cubes of `bone` (whose pivot is the pommel at `origin`), scaled."""
    for (c, s, col, rot) in prims:
        centre = [origin[a] + c[a] * scale for a in range(3)]
        size = [v * scale for v in s]
        if any(abs(r) > 1e-9 for r in rot):
            bone.box_at(centre, size, col, rotation=list(rot), pivot=centre)
        else:
            bone.box_at(centre, size, col)


def weapons(m, n):
    for (k, tier, yaw, pivot, pitch) in slot_layout(n):
        slot = m.bone("slot_%d" % k, list(pivot), "ring", rotation=[0, round(yaw, 3), 0])
        w = m.bone("weapon_%d" % k, list(pivot), slot.name, rotation=[pitch, 0, 0])
        place(w, pivot, WEAPONS[k][1]())
        if k == 6:
            # the flail's head on its own chain from the haft's end; it hangs plumb (its spring's rest)
            end = m.fk(w.name, [pivot[0], pivot[1], pivot[2] - 10.0 * DOCK_SCALE])   # posed: where the chain starts
            # the bone sits at the haft's end in the weapon's own (unrotated) frame
            hp = [pivot[0], pivot[1], pivot[2] - 10.0 * DOCK_SCALE]
            hb = m.bone("weapon_6_ball", hp, w.name)
            # the world frame whose -z points straight down, its x along the slot's tangent
            hb.rotation = world_rotation(m, w.name, mat_mul(rot_mat(0, yaw, 0), rot_mat(-90, 0, 0)))
            place(hb, hp, flail_head())
            assert end[1] > 0


def build(n=10):
    if not 1 <= n <= 10:
        raise SystemExit("--weapons: 1..10")
    m = Model("Tithe Binder", "gravebound", "T1 hovering chain-caster: a wheel of 1-10 bound weapons", PALETTE,
              EMISSIVE)
    body(m)
    weapons(m, n)
    m.pose = {}
    m.limb_ok = ["right_arm", "left_arm"]
    # contacts that are how the body is built (tools/contact_check.py skips these pairs): the mantle drapes over the
    # chest, the sleeve tops and the hood's base, the cape hangs under it; the tatters' tops tuck under the hem band;
    # each sleeve's cuff closes round its forearm at the elbow; the right fist closes round the hook's haft (Grave Hook);
    # the hood's back folds over the cape's top when the head tips back (cloth on cloth at the nape)
    m.contact_ok = [("body", t) for t in ("tatter_f", "tatter_b", "tatter_l", "tatter_r")] + [
        ("chest", "mantle"), ("mantle", "cape"), ("mantle", "head"), ("mantle", "right_arm"), ("mantle", "left_arm"),
        ("head", "cape"),
        ("right_arm", "right_forearm"), ("left_arm", "left_forearm"), ("right_hand", "weapon_1")]
    return m


def counts(m):
    per = {}
    for b in m.bones.values():
        top = b.name
        while top and not top.startswith("weapon_") and m.bones[top].parent:
            top = m.bones[top].parent
        key = top if top.startswith("weapon_") else "body"
        if key.startswith("weapon_6"):
            key = "weapon_6"
        per[key] = per.get(key, 0) + len(b.cubes)
    return per


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--weapons", type=int, default=10, help="bound weapons N (1..10); the committed model is 10")
    ap.add_argument("--out", default=None, help="output path (default design/models/%s.json)" % NAME)
    a = ap.parse_args()
    m = build(a.weapons)
    path = os.path.normpath(a.out or os.path.join(OUT, NAME + ".json"))
    with open(path, "w") as f:
        json.dump(m.to_json(), f, indent=1)
    per = counts(m)
    nc = sum(per.values())
    up = sum(1 for b in m.bones.values() for c in b.cubes if c.get("upgrade"))
    print("wrote %s: N=%d, %d bones, %d cubes (body %d incl. %d upgrade; weapons %s)" % (
        path, a.weapons, len(m.bones), nc, per["body"], up,
        ", ".join("%s %d" % (k, v) for k, v in sorted(per.items(), key=lambda kv: (len(kv[0]), kv[0])) if k != "body")))


if __name__ == "__main__":
    main()
