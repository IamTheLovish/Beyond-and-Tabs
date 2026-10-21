#!/usr/bin/env python3
"""ONE articulated quadruped rig for the big four-legged units (lovish, Oct 21: animations "extremely realistic and
cohesive"; "dragons and animals ... look like they're breathing and move articulated"), and the first body on it: the
Verdant Court's Mossback Bison. Our own model - nothing copied from another mod or game.

The rig (DSL bones; the same names on every quadruped, so unit/modelling/animations/Quadruped.java animates them
all - the elephant, the rhino and the War Mammoth join by building their own body on these bones):

    body                       the thorax; the root everything hangs from (bobs, dips, pitches)
      hump                     the withers / shoulder hump (bison: shaggy; mammoth: the dome; elephant/rhino: low)
      neck -> head             the neck rolls first, the head follows and stays level
        head: jaw, ear_left, ear_right, horn_left, horn_right, tusk_left, tusk_right, trunk_1 -> trunk_2 -> trunk_3
                               (attachment bones: a body carries only the ones it has; the animator skips absent ones)
      hips -> tail -> tail_tip the pelvis rolls with the hind legs; the tail is a two-link chain with lag
      front_left_upper -> front_left_lower -> front_left_foot      shoulder -> elbow -> wrist (carpus)
      front_right_upper -> ...                                     (upper bones hang from body)
      hips/hind_left_upper -> hind_left_lower -> hind_left_foot     hip -> stifle -> hock
      hips/hind_right_upper -> ...                                  (upper bones hang from hips)

Proportions are a dict (P) so the next body changes numbers, not structure: leg lengths, body length/width/height,
hump height, neck drop, head size. Coordinates: DSL px (16 = 1 block), y up, the unit faces -z, +x its left
(design/models/README.md). The Bison is built at 1x and drawn x1.3 (MossbackBisonUnit.SCALE), its rump top and hump
top at the heights the grove layer expects (34 / 40 px).

    python3 tools/gen_quadruped.py && python3 tools/preview_model.py design/models/verdant_mossback_bison.json --out /tmp/b.png
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import Model, OUT, slab  # noqa: E402

PALETTE = {
    "fur": "#86653f", "fur_light": "#a0815a", "fur_dark": "#3a2919", "fur_pale": "#b09470",
    "skin": "#4a3627", "nose": "#1e1814", "eye": "#0d0b09",
    "horn": "#2a2622", "horn_tip": "#4a4540", "hoof": "#1f1a16",
    "moss": "#4f6f2b", "moss_dark": "#33481e", "moss_pale": "#6a8a3a", "leaf": "#4f7a2e", "wood": "#6b4a2c", "wood_dark": "#3f2b1a",
    "glow_berry": "#ffc45c",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
}
EMISSIVE = ["glow_berry"]

# the Mossback Bison's proportions (px): a heavy, high-withered ox - the hump the highest point, the head carried low
BISON = dict(
    body_w=15.0, body_z0=-14.0, body_z1=4.0, body_y0=8.0, body_y1=28.5,    # the thorax barrel: a deep chest
    hips_w=12.5, hips_z1=16.0, hips_y0=11.0, hips_y1=27.5,               # the pelvis barrel (rump top 27.5 + moss ~ 29)
    hump_top=36.5, hump_z0=-16.0, hump_z1=-1.0, hump_w=13.0,             # the withers rise from the neck base
    neck_y=25.0, neck_len=8.0, neck_drop=22.0,                           # the neck leaves the chest at y 25, down 22 deg
    head_w=9.5, head_h=9.5, head_len=9.0, muzzle_len=5.5,
    shoulder=(5.5, 22.0, -9.0), elbow_len=9.0, fore_len=7.0, front_foot=6.0, front_w=6.2,
    hip=(5.0, 23.0, 11.0), thigh_len=9.0, shin_len=7.5, hind_foot=6.5, hind_w=6.6,
    tail_y=25.5, tail_len=8.0, tail_tip=4.0,
)


def barrel(bone, cy, cz, z0, z1, w, h, color, k=3, **kw):
    """A box lying along z with ROUND edges along its length: k nested boxes whose corners lie on the ellipse w x h in
    the x-y plane (gen_models.rounded turned on its side) - a chest, a belly, a neck."""
    A, B = w / 2, h / 2
    s = 1.0 / math.cos(0.5 / k * math.pi / 2)
    for i in range(k):
        t = (i + 0.5) / k * math.pi / 2
        hw, hh = A * s * math.cos(t), B * s * math.sin(t)
        bone.cube([-hw + 0, cy - hh, z0], [2 * hw, 2 * hh, z1 - z0], color, **kw)


def ridge(bone, z0, z1, w, base, rise, color, n=7, peak=0.42):
    """The withers: a hill along the spine from z0 to z1 rising `rise` over `base`, its crest at `peak` of the length
    (forward of centre on a bison). n columns of rounded section follow the profile and a tilted slab rides each step
    of it, so the outline from the side is a slope, not a stack of boxes (the lathe trick in the y-z plane)."""
    def h(t):
        u = t / peak if t < peak else 1 - (t - peak) / (1 - peak)
        return base + rise * math.sin(u * math.pi / 2) ** 1.3
    zs = [z0 + (z1 - z0) * i / n for i in range(n + 1)]
    for i in range(n):
        za, zb = zs[i], zs[i + 1]
        ya, yb = h(i / n), h((i + 1) / n)
        lo, hi = min(ya, yb), max(ya, yb)
        wi = w * (0.8 + 0.2 * (lo - base) / rise)
        barrel(bone, (base - 1.0 + lo) / 2, 0, za, zb + 0.05, wi, lo - base + 1.0, color, k=2)
        # the slope between the columns' tops: a slab along (0, yb - ya, zb - za), laid on the step
        length = math.hypot(yb - ya, zb - za) + 0.3
        c = (0, (ya + yb) / 2 - 0.7, (za + zb) / 2)
        slab(bone, c, (0, yb - ya, zb - za), (1, 0, 0), [wi * 0.95, 1.5, length], color)
        slab(bone, (0, (ya + yb) / 2 - 1.6, (za + zb) / 2), (0, yb - ya, zb - za), (1, 0, 0), [wi * 0.75, 1.2, length], color)
    return lambda z: h(min(1.0, max(0.0, (z - z0) / (z1 - z0))))


def shag(bone, cy, cz, z0, z1, w, h, color, n=4, seed=0, dark="fur_dark", belly=True, prof=None, rows=3, front=False,
         floor=None):
    """A shaggy cape as overlapping SHINGLES: `rows` staggered rows of wide, short locks down each flank, each lock
    angled a little outward and swept back, longest at the front (the throat, the shoulder) and shorter toward the
    ribs; a ragged ridge line on top; `front`: locks hanging over the chest's face too, so the front view is broken
    the same way; `floor`: no lock reaches below this height (the knee). Not a row of planks: fewer, wider, varied."""
    rnd = seed
    span = z1 - z0
    lw = span / n * 1.45                                  # locks overlap their neighbours
    ridge = lambda z: (prof(z) if prof else cy + h / 2)
    for r in range(rows):
        stagger = (r % 2) * 0.5
        for i in range(n + (r % 2)):
            rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
            t = (i - stagger + 0.5) / n                   # 0 at the front of the span, 1 at the back
            if t < 0.02 or t > 1.0:
                continue
            z = z0 + span * t
            top = ridge(z) - 0.4 - r * (h * 0.26)
            L = h * (0.40 - 0.14 * t) + (rnd >> 4) % 3 - 1 + (0 if r < rows - 1 else -1)
            if floor is not None:
                L = min(L, top - floor)
            if L < 2.5:
                continue
            for side in (1, -1):
                out = 8 + (rnd >> 8) % 10 + r * 3        # outward lean: the lower rows hang further out
                back = -(4 + (rnd >> 6) % 8)              # swept back
                col = color if ((rnd >> 3) + r) & 1 else dark
                x = side * (w / 2 + 0.2 + r * 0.25)
                c = (x, top - L / 2, z)
                bone.box_at(c, [1.1, L, lw], col, rotation=[back, 0, side * out], pivot=[x, top, z])
        # the ridge: short tufts along the top line, leaning either way
        c = ((rnd >> 12) % 5 - 2, ridge(z0 + span * (r + 0.5) / rows) + 0.2, z0 + span * (r + 0.5) / rows)
        bone.box_at(c, [w * 0.5, 1.8, span / rows + 0.4], color, rotation=[0, 0, (rnd >> 6) % 11 - 5], pivot=list(c))
    if front:   # the locks over the chest: hanging off the brisket, leaning forward and out
        for k, (fx, L, lean, out) in enumerate(((-0.3, 0.42, 14, -8), (0.05, 0.5, 18, 2), (0.32, 0.4, 12, 9))):
            x = fx * w
            top = cy + h * 0.3
            Lk = h * L
            if floor is not None:
                Lk = min(Lk, top - floor)
            c = (x, top - Lk / 2, z0 - 0.3)
            bone.box_at(c, [w * 0.32, Lk, 1.2], color if k != 1 else dark, rotation=[lean, 0, out], pivot=[x, top, z0 - 0.3])
    if belly:   # the mane under the chest: a few short tufts
        for i in range(3):
            rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
            z = z0 + span * (i + 0.5) / 3
            for side in (1, -1):
                L = h * 0.16 + (rnd >> 7) % 2
                c = (side * w * 0.2, cy - h / 2 - L / 2 + 0.6, z)
                bone.box_at(c, [w * 0.25, L, span / 3 * 0.7], dark, rotation=[(rnd >> 9) % 11 - 5, 0, side * 6], pivot=[c[0], cy - h / 2 + 0.6, z])


def leg(m, name, parent, top, upper_len, lower_len, foot_len, w, rot_upper, rot_lower, rot_foot, hairy):
    """upper (pivot top) -> lower (its child) -> foot (its child), each bone's box overlapping the joint above it by a
    knee's worth so the fold never shows a gap. Returns (foot, lower) for Model.ground_leg."""
    x, y, z = top
    d = w * 1.15
    up = m.bone(name + "_upper", [x, y, z], parent=parent, rotation=rot_upper)
    coat = "fur_light" if hairy else "fur_dark"
    barrel(up, y - upper_len / 2 + 0.4, z, z - d / 2, z + d / 2, w, upper_len + 0.8, coat, k=2)
    ky = y - upper_len
    lo = m.bone(name + "_lower", [x, ky, z], parent=name + "_upper", rotation=rot_lower)
    lw = w * 0.72
    lo.cube([x - lw / 2, ky - lower_len, z - lw / 2], [lw, lower_len + 1.6, lw], "fur_dark")
    lo.cube([x - lw / 2 - 0.35, ky - 1.2, z - lw / 2 - 0.35], [lw + 0.7, 2.6, lw + 0.7], "fur_dark")        # the joint
    fy = ky - lower_len
    ft = m.bone(name + "_foot", [x, fy, z], parent=name + "_lower", rotation=rot_foot)
    fw = w * 0.78
    ft.cube([x - fw / 2, fy - foot_len + 2.4, z - fw / 2], [fw, foot_len - 2.4 + 1.2, fw], "fur_dark")
    ft.cube([x - fw / 2 - 0.2, fy - foot_len, z - fw / 2 - 0.3], [fw + 0.4, 2.6, fw + 0.6], "hoof")       # the hoof
    ft.cube([x - 0.15, fy - foot_len, z - fw / 2 - 0.4], [0.3, 2.6, 0.5], "fur_dark")                    # the cleft
    return name + "_foot", name + "_lower"


def quadruped(m, P, hairy_front=True):
    """The rig's bones and the plain beast on them from the proportions P. Returns the bones a body decorates."""
    body = m.bone("body", [0, (P["body_y0"] + P["body_y1"]) / 2, (P["body_z0"] + P["body_z1"]) / 2])
    bw, bh = P["body_w"], P["body_y1"] - P["body_y0"]
    barrel(body, (P["body_y0"] + P["body_y1"]) / 2, 0, P["body_z0"], P["body_z1"], bw, bh, "fur_dark")
    if hairy_front:   # the cape: the pale shaggy coat over the forequarters, its ragged edge behind the shoulders
        cz1 = P["body_z0"] + (P["body_z1"] - P["body_z0"]) * 0.62
        barrel(body, (P["body_y0"] + P["body_y1"]) / 2 + 0.4, 0, P["body_z0"] - 0.4, cz1, bw + 0.9, bh + 0.4, "fur_light")
    # the belly line sags behind the chest, the sternum sits lower in front
    body.cube([-bw * 0.3, P["body_y0"] - 1.2, P["body_z0"] + 3], [bw * 0.6, 2.0, P["body_z1"] - P["body_z0"] - 6], "fur_dark", inflate=0.2)

    hump = m.bone("hump", [0, P["body_y1"] - 1.0, (P["hump_z0"] + P["hump_z1"]) / 2], parent="body")
    hz0, hz1, hw = P["hump_z0"], P["hump_z1"], P["hump_w"]
    base = P["body_y1"] - 3.0                     # sunk into the back and the neck base: no seam, no gap
    rise = P["hump_top"] - base
    m.hump_prof = ridge(hump, hz0 - 2.0, hz1 + 1.0, hw, base, rise, "fur_light", n=7, peak=0.42)

    hips = m.bone("hips", [0, (P["hips_y0"] + P["hips_y1"]) / 2, P["body_z1"]], parent="body")
    hy0, hy1 = P["hips_y0"], P["hips_y1"]
    barrel(hips, (hy0 + hy1) / 2, 0, P["body_z1"] - 1.5, P["hips_z1"], P["hips_w"], hy1 - hy0, "fur_dark")
    barrel(hips, (hy0 + hy1) / 2 + 0.5, 0, P["hips_z1"] - 1.0, P["hips_z1"] + 1.6, P["hips_w"] * 0.8, (hy1 - hy0) * 0.8, "fur_dark", k=2)

    # the neck leaves the top of the chest and drops to the head; the head counter-tilts so its box reads level
    nd = P["neck_drop"]
    nz = P["body_z0"] + 1.0
    neck = m.bone("neck", [0, P["neck_y"], nz], parent="body", rotation=[-nd, 0, 0])
    nl = P["neck_len"]
    barrel(neck, P["neck_y"] - 2.5, 0, nz - nl - 1.0, nz + 2.5, bw * 0.62, 10.5, "fur_light" if hairy_front else "fur_dark", k=2)
    # the dewlap: dark hair hanging under the throat from the chest to the chin
    neck.cube([-bw * 0.2, P["neck_y"] - 10.5, nz - nl - 0.5], [bw * 0.4, 4.0, nl + 1.5], "fur_dark", rotation=[8, 0, 0], pivot=[0, P["neck_y"] - 6.5, nz - nl / 2])
    head = m.bone("head", [0, P["neck_y"] - 4.0, nz - nl], parent="neck", rotation=[nd, 0, 0])   # hung low off the neck
    return body, hump, hips, neck, head


def bison_head(m, P, head):
    hw, hh, hl, ml = P["head_w"], P["head_h"], P["head_len"], P["muzzle_len"]
    hx, hy, hz = head.pivot
    top, bot = hy + hh * 0.55, hy - hh * 0.45
    barrel(head, (top + bot) / 2, 0, hz - hl, hz + 1.5, hw, hh, "fur", k=2)
    # the woolly cap over the brow (a mop of cape-coloured tufts) and the forelock falling between the eyes
    head.cube([-hw / 2 - 0.4, top - 2.4, hz - hl + 0.5], [hw + 0.8, 3.2, hl - 1.5], "fur_light", inflate=0.3)
    for k, (dx, dzz, lean) in enumerate(((-0.3, 0.3, 16), (0.3, 0.6, -12), (0.0, 0.15, 0))):
        c = (dx * hw, top + 1.2, hz - hl + dzz * hl)
        head.box_at(c, [hw * 0.45, 2.0, hl * 0.3], "fur_light" if k != 2 else "fur_dark", rotation=[0, 0, lean], pivot=list(c))
    head.cube([-1.8, top - 3.6, hz - hl - 0.8], [3.6, 3.0, 1.2], "fur_light")
    # the muzzle: lower and narrower, the broad wet nose, the lip
    mz0 = hz - hl - ml
    barrel(head, bot + hh * 0.34, 0, mz0, hz - hl + 0.6, hw * 0.74, hh * 0.68, "fur_pale", k=2)
    head.cube([-hw * 0.3, bot + hh * 0.28, mz0 - 0.5], [hw * 0.6, 2.6, 1.0], "nose")
    head.cube([-hw * 0.28, bot - 0.4, mz0 + 0.4], [hw * 0.56, 1.8, ml - 1.0], "fur_pale")
    # eyes, set in the sides under the brow
    for s in (1, -1):
        head.cube([s * (hw / 2 + 0.05) - 0.55, top - 3.8, hz - hl + 1.4], [1.1, 1.3, 1.5], "eye")
    # the beard under the chin, longer at the back
    beard = head.cube([-2.2, bot - 4.2, hz - hl + 0.5], [4.4, 4.4, hl - 2.0], "fur_dark")
    head.cube([-1.4, bot - 5.6, hz - 2.0], [2.8, 2.0, 2.0], "fur_dark", rotation=[12, 0, 0])
    # horns: bones at the skull's sides, a base out, a sweep up and in, a pale tip - the attack's weapon
    for s, name in ((1, "horn_left"), (-1, "horn_right")):
        px = s * (hw / 2 - 0.6)
        hb = m.bone(name, [px, top - 1.4, hz - hl + 3.0], parent="head")
        base = [px, top - 1.4, hz - hl + 3.0]
        hb.box_at([px + s * 1.4, top - 1.2, base[2]], [3.2, 2.2, 2.2], "horn")
        hb.box_at([px + s * 1.4, top - 1.2, base[2]], [2.4, 2.8, 2.8], "tabard")                  # the woven band
        c1 = (px + s * 3.4, top + 0.6, base[2] - 0.2)
        hb.box_at(c1, [1.9, 4.2, 1.9], "horn", rotation=[0, 0, s * -48], pivot=list(c1))
        c2 = (px + s * 4.4, top + 3.4, base[2] - 0.5)
        hb.box_at(c2, [1.5, 3.4, 1.5], "horn", rotation=[0, 0, s * -12], pivot=list(c2))
        c3 = (px + s * 4.1, top + 5.6, base[2] - 0.8)
        hb.box_at(c3, [1.1, 2.2, 1.1], "horn_tip", rotation=[0, 0, s * 18], pivot=list(c3))
        eb = m.bone("ear_" + name.split("_")[1], [s * (hw / 2 - 0.2), top - 2.6, hz - hl + 5.5], parent="head")
        c = (s * (hw / 2 + 1.5), top - 3.0, hz - hl + 5.5)
        eb.box_at(c, [3.2, 1.6, 1.1], "fur_pale", rotation=[0, 0, s * 20], pivot=list(c))
    return beard


def bison_grove(m, P, body, hump, hips):
    # the moss and berries sit on the withers' profile (m.hump_prof, from ridge())
    """The living grove's roots and moss on the hide (the moss bed, bushes and canopy are real blocks drawn by the
    renderer's GroveLayer over the rump and hump): moss creeping over the hump and flanks, roots hanging down the sides,
    glow berries in the shag."""
    hz0, hz1, hw = P["hump_z0"], P["hump_z1"], P["hump_w"]
    top = P["hump_top"]
    def mossy(bone, cx, cy, z0, z1, w, seed, prof=None):
        """Moss growing out of the fur: a cushion of overlapping blobs at different heights and leans, its edges
        drooping over the hair below - not a slab laid on top."""
        rnd = seed
        n = max(2, int((z1 - z0) / 2.6))
        for i in range(n):
            rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
            z = z0 + (z1 - z0) * (i + 0.5) / n
            dz = (z1 - z0) / n
            if prof:
                cy = prof(z) - 0.6
            bone.box_at((cx + ((rnd >> 5) % 3 - 1) * 0.4, cy + 0.5, z), [w * 0.9, 1.6 + (rnd >> 9) % 2 * 0.5, dz + 0.9], "moss" if (rnd >> 2) & 1 else "moss_pale", rotation=[(rnd >> 4) % 9 - 4, 0, (rnd >> 7) % 7 - 3], pivot=[cx, cy + 0.5, z])
            for side in (1, -1):   # the overhang: a fringe leaning down over the edge
                c = (cx + side * (w / 2 + 0.2), cy - 0.6, z)
                bone.box_at(c, [1.3, 2.2, dz * 0.8], "moss_dark", rotation=[0, 0, side * (18 + (rnd >> 10) % 14)], pivot=[c[0], cy + 0.4, z])
    mossy(hump, 0, top - 0.6, hz0 + 1.0, hz1 - 0.5, hw * 0.72, 5, prof=m.hump_prof)
    for s in (1, -1):
        # moss creeping down the hump's flank, and the roots: thick sinews of living wood standing proud of the hair,
        # from the hump and from the hips, each ending in a dark root tip
        c = (s * (hw * 0.4), top - 4.0, (hz0 + hz1) / 2 + 1.0)
        hump.box_at(c, [1.4, 3.6, 5.5], "moss_dark", rotation=[0, 0, s * -32], pivot=list(c))
        for k, (z, L, lean) in enumerate(((hz0 + 4.0, 8.0, 12), (hz1 - 2.0, 10.0, -5), (P["body_z1"] + 6.0, 7.0, 16))):
            host = hips if z > P["body_z1"] else hump
            x = s * (P["body_w"] / 2 + 1.2 if host is hump else P["hips_w"] / 2 + 1.0)
            y = (P["body_y1"] + 0.5) if host is hump else P["hips_y1"] - 0.5
            c = (x, y - L / 2, z)
            host.box_at(c, [1.8, L, 1.8], "wood", rotation=[0, 0, s * lean], pivot=[x, y, z])
            host.box_at((x + s * 0.3, y - L * 0.4, z + 1.1), [1.1, L * 0.5, 1.1], "wood", rotation=[8, 0, s * (lean + 20)], pivot=[x, y, z])
            host.box_at((x, y - L - 0.4, z + 0.4), [1.2, 1.8, 1.2], "wood_dark", rotation=[0, 0, s * lean], pivot=[x, y, z])
    for (x, z) in ((-3.4, hz0 + 5), (2.8, hz0 + 8), (0.4, hz1 - 2.5), (-4.6, hz1 - 1.5), (4.2, hz0 + 10)):
        yb = m.hump_prof(z) + 0.4
        hump.cube([x - 0.75, yb - 0.6, z - 0.75], [1.5, 1.5, 1.5], "glow_berry")
        hump.cube([x - 1.0, yb + 0.9, z - 1.0], [2.0, 0.6, 2.0], "leaf")
    # moss on the rump, round where the moss bed sits, and a mossy saddle line on the spine
    mossy(hips, 0, P["hips_y1"] - 0.4, P["body_z1"] + 1.0, P["hips_z1"] - 1.5, P["hips_w"] * 0.8, 9)
    body.cube([-1.2, P["body_y1"] - 0.8, P["hump_z1"] - 0.5], [2.4, 1.8, P["body_z1"] - P["hump_z1"] + 1.0], "moss_dark")


def mossback_bison():
    P = BISON
    m = Model("Mossback Bison", "verdant", "T2 war-bison, the Hollow Grove carrier (quadruped rig)", PALETTE, EMISSIVE)
    body, hump, hips, neck, head = quadruped(m, P, hairy_front=True)
    knee = P["shoulder"][1] - P["elbow_len"] + 1.0
    shag(body, (P["body_y0"] + P["body_y1"]) / 2 + 0.4, 0, P["body_z0"], P["body_z0"] + 12.0, P["body_w"] + 0.9, P["body_y1"] - P["body_y0"] + 0.4, "fur_light", n=4, seed=3, rows=3, front=True, floor=knee)
    shag(hump, P["hump_top"] - 3.5, 0, P["hump_z0"] + 1.0, P["hump_z1"] - 0.5, P["hump_w"] * 0.9, 6.0, "fur_light", n=3, seed=11, belly=False, prof=m.hump_prof, rows=2)
    shag(neck, P["neck_y"] - 3.0, 0, P["body_z0"] - P["neck_len"], P["body_z0"] + 1.0, P["body_w"] * 0.62, 10.5, "fur_light", n=2, seed=7, rows=2, belly=False)
    bison_head(m, P, head)
    bison_grove(m, P, body, hump, hips)
    # the tail: a two-link chain off the rump, a tuft at the tip
    tz = P["hips_z1"] + 0.8
    tail = m.bone("tail", [0, P["tail_y"], tz], parent="hips", rotation=[-14, 0, 0])
    tail.cube([-0.9, P["tail_y"] - P["tail_len"], tz - 0.9], [1.8, P["tail_len"] + 0.6, 1.8], "fur_dark")
    tip = m.bone("tail_tip", [0, P["tail_y"] - P["tail_len"], tz], parent="tail", rotation=[-6, 0, 0])
    tip.cube([-1.3, P["tail_y"] - P["tail_len"] - P["tail_tip"], tz - 1.3], [2.6, P["tail_tip"] + 0.8, 2.6], "fur_dark")
    tip.cube([-0.6, P["tail_y"] - P["tail_len"] - P["tail_tip"] - 0.8, tz - 0.6], [1.2, 1.0, 1.2], "tabard")
    # legs: the front pair hangs from the thorax (shoulder back, elbow under the chest), the hind pair from the hips
    # (stifle forward, hock back, cannon down) - the ungulate zig-zag, every joint a fold
    feet = []
    sx, sy, sz = P["shoulder"]
    for s, side in ((1, "left"), (-1, "right")):
        feet.append(leg(m, "front_" + side, "body", (s * sx, sy, sz), P["elbow_len"], P["fore_len"], P["front_foot"],
                        P["front_w"], [-10, 0, 0], [10, 0, 0], [0, 0, 0], True))
    hx, hy, hz = P["hip"]
    for s, side in ((1, "left"), (-1, "right")):
        feet.append(leg(m, "hind_" + side, "hips", (s * hx, hy, hz), P["thigh_len"], P["shin_len"], P["hind_foot"],
                        P["hind_w"], [16, 0, 0], [-34, 0, 0], [18, 0, 0], False))
    for ft, lo in feet:
        m.ground_leg(ft, also=(lo,))
    m.pose = {"head": [-10, 0, 0]}        # the forehead near vertical, the muzzle toward the grass
    m.limb_ok = ["neck", "head", "tail", "tail_tip"]
    return m


MODELS = {"verdant_mossback_bison": mossback_bison}


def main():
    only = sys.argv[1:]
    for name, build in MODELS.items():
        if only and name not in only:
            continue
        m = build()
        path = os.path.normpath(os.path.join(OUT, name + ".json"))
        with open(path, "w") as f:
            json.dump(m.to_json(), f, indent=1)
        nc = sum(len(b.cubes) for b in m.bones.values())
        print(f"wrote {path}: {len(m.bones)} bones, {nc} cubes")


if __name__ == "__main__":
    main()
