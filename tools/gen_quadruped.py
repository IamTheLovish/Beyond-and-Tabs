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
      root_<l|r><k> -> _1 -> _2  (bison only) the roots hanging in the shag, chains the model subclass sways with lag

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

def lift(hx, t):
    """A colour lifted toward a warm white by t: the fur palette is written as it LOOKS RIGHT and lifted here, because
    the painted, side-lit, ambient-occluded animal lands a stop and a half below its hex (round 10, measured: with
    the "right" hexes 61% of the bison's pixels in a review sheet sat under luma 40; an upright face gets half the
    light of a top face, the baked occlusion under the shag takes up to half again, the value curve a fifth)."""
    c = [int(hx[i:i + 2], 16) for i in (1, 3, 5)]
    warm = (255, 244, 224)
    k = 1.0 + t * 1.3                                  # mostly brighter (the hue kept), a little toward white
    return "#%02x%02x%02x" % tuple(min(255, int(min(255.0, v * k) + (w - min(255.0, v * k)) * t * 0.3)) for v, w in zip(c, warm))


# round 11: the lift is PER REGION, retargeted on the rendered medians (regions.py; review 10: "the colour anatomy
# is inverted - the face and saddle pale, the body dark"): face, beard and forelegs 25-32; the muzzle under 20;
# shoulder and cape 70-85; the hump top 85-95; the rump 60-70; the belly about 45
PALETTE = {
    "fur": lift("#5e4228", 0.75),           # the belly, the dewlap, the dark lower masses
    "fur_head": lift("#5e4228", 0.42),      # the face: a bison's head is its darkest mass
    "fur_brow": lift("#6e5030", 0.3),       # the forehead cap, the brow, the lock ridges down the face
    "muzzle": "#2a2018",                    # near black (the cream muzzle read above 150, round 11)
    "fur_leg": lift("#5a3e26", 0.32),       # the forelegs
    "fur_front": lift("#6e5030", 0.85),     # the cape over the shoulders and the neck: the lit middle of the animal
    "fur_light": lift("#a8844c", 0.42),     # the hump's crown
    "fur_dark": lift("#3e2a18", 0.45),      # the beard, the partings, the shadow lines
    "fur_pale": lift("#bc9a62", 0.2),       # the ears, the cheekbone
    "fur_rump": lift("#b08a48", 0.45),      # the rump's cap and the haunch tops
    "fur_thigh": lift("#8e6a3e", 0.18),     # the hind legs
    "fur_hips": lift("#8e6a3e", 0.55),      # the hips, under the moss
    "root": "#6a3c2a", "root_edge": "#b08a66", "root_knob": "#86543a", "berry_rim": "#4a2e18", "glow_core": "#ffd040",
    "skin": "#5a4232", "nose": "#1e1814", "eye": "#0d0b09",
    "horn": "#2a2622", "horn_tip": "#4a4540", "hoof": "#1f1a16",
    "moss": "#5a7c30", "moss_dark": "#3a5222", "moss_pale": "#7a9a44", "leaf": "#4f7a2e", "wood": "#6b4a2c", "wood_dark": "#46301e",
    "glow_berry": "#e8701a",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
}
EMISSIVE = ["glow_berry", "glow_core"]

# the Mossback Bison's proportions (px): a heavy, high-withered ox - the hump the highest point, the head carried low
BISON = dict(
    body_w=15.0, body_z0=-14.0, body_z1=4.0, body_y0=8.0, body_y1=28.5,    # the thorax barrel: a deep chest
    hips_w=10.8, hips_z1=16.0, hips_y0=11.0, hips_y1=26.0,               # the pelvis: 72% of the chest's width, 1.5 lower, sloping to the tail (round 10: 60% of the mass forward)
    hump_top=36.5, hump_z0=-16.0, hump_z1=-1.0, hump_w=13.0,             # the withers rise from the neck base
    neck_y=25.0, neck_len=8.0, neck_drop=22.0,                           # the neck leaves the chest at y 25, down 22 deg
    head_w=9.5, head_h=9.5, head_len=9.0, muzzle_len=5.5,
    shoulder=(5.5, 22.0, -9.0), elbow_len=9.0, fore_len=7.0, front_foot=6.0, front_w=6.2,
    hip=(4.3, 21.5, 11.0), thigh_len=8.5, shin_len=7.0, hind_foot=6.5, hind_w=6.0,    # hind legs shorter and lighter than the fore
    tail_y=24.0, tail_len=8.0, tail_tip=4.0,
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
         floor=None, prefix=None, step=0.26, sweep=(1, 4), fringe=0):
    """A shaggy cape as overlapping SHINGLES: `rows` staggered rows of wide, short locks down each flank, each lock
    angled a little outward and swept back, longest at the front (the throat, the shoulder) and shorter toward the
    ribs; a ragged ridge line on top; `front`: locks hanging over the chest's face too, so the front view is broken
    the same way; `floor`: no lock reaches below this height (the knee). Not a row of planks: fewer, wider, varied.
    `prefix`: each row of shingles goes into its own bone (<prefix>_<row>, hung from `bone` at the row's top line) so
    the animator swings the coat a beat behind the body (round 10: secondary motion - the coat moves last).
    `step`: the rows' spacing as a share of h (0.13 with six rows: every lock lies over the two below it, no gap);
    `sweep`: (least, range) degrees each lock is rotated down-and-back, following the fur's flow (round 11);
    `fringe`: this many ragged strips per side hang under the belly, so the lower silhouette is soft."""
    rnd = seed
    span = z1 - z0
    lw = span / n * 1.6                                   # locks overlap their neighbours by a third
    ridge = lambda z: (prof(z) if prof else cy + h / 2)
    host = bone
    for r in range(rows):
        if prefix:
            zm = (z0 + z1) / 2
            bone = host.model.bone("%s_%d" % (prefix, r), [0, ridge(zm) - 0.4 - r * (h * step), zm], parent=host.name)
        stagger = (r % 2) * 0.5
        for i in range(n + (r % 2)):
            rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
            t = (i - stagger + 0.5) / n                   # 0 at the front of the span, 1 at the back
            if t < 0.02 or t > 1.0:
                continue
            z = z0 + span * t
            top = ridge(z) - 0.4 - r * (h * step)
            L = h * (0.40 - 0.14 * t) + (rnd >> 4) % 5 - 2 + (0 if r < rows - 1 else -1)
            if floor is not None:
                L = min(L, top - floor)
            if L < 2.5:
                continue
            for side in (1, -1):
                # round 10 ("a pile of debris"): the shingles sit FLUSH on the body form - seated a third of their
                # thickness into the barrel, a 2-5 degree lean, barely swept - overlapping like roof tiles; the soft
                # breakup is only at the outer edge, in the hanging points below
                out = 4 + (rnd >> 8) % 4                  # a slight outward lean, the hem out past the row below's top
                back = -(sweep[0] + (rnd >> 6) % sweep[1])   # swept down-and-back with the fur's flow
                col = color if r < rows * 0.66 else dark   # one colour per row (a mosaic of pale and dark tiles read as debris, round 11): the lowest rows the belly's tone
                x = side * (w / 2 + 0.5 - r * 0.22)      # roof tiles: each row sits a little INSIDE the one above, whose hem covers its top edge (round 11: the lit top faces read as a mosaic)
                c = (x, top - L / 2, z)
                # every lock ends in two points of different lengths: the hemline zigzags, no straight edge
                bone.box_at((x, top - (L - 2.4) / 2, z), [1.0, L - 2.4, lw], col, rotation=[back, 0, side * out], pivot=[x, top, z])
                pts = ((-0.28, 2.8), (0.3, 1.6)) if (rnd >> 11) & 1 else ((-0.3, 1.4), (0.26, 3.0))
                if r < rows - 2 and rows > 3:        # the points only at the hem rows: the upper rows are plain tiles under the next row (round 11)
                    pts = ()
                for q, (dz, dl) in enumerate(pts):   # two hanging points a cube wide: a notch between them
                    bone.box_at((x + side * 0.25, top - L + 2.4 - dl / 2 - 0.1, z + dz * lw), [1.2, dl + 0.4, lw * 0.34], col if q else dark,
                                rotation=[back, 0, side * (out + 6 + q * 5)], pivot=[x, top, z])
        # the ridge: short tufts along the top line, leaning either way
        c = ((rnd >> 12) % 5 - 2, ridge(z0 + span * (r + 0.5) / rows) + 0.2, z0 + span * (r + 0.5) / rows)
        bone.box_at(c, [w * 0.5, 1.8, span / rows + 0.4], color, rotation=[0, 0, (rnd >> 6) % 11 - 5], pivot=list(c))
    if prefix:
        bone = host.model.bone(prefix + "_front", [0, cy + h * 0.3, z0 - 0.3], parent=host.name) if front else host
    if front:   # the locks over the chest: hanging off the brisket, leaning forward and out
        # five narrower locks of different lengths, each ending in a hanging point, the points alternating long and
        # short across the chest: the hem is a zigzag, not a ruler line at the knee (round 8)
        for k, (fx, L, lean, out, drop) in enumerate(((-0.38, 0.42, 14, -8, 0.0), (-0.2, 0.36, 12, -4, 2.2), (0.0, 0.5, 18, 2, 1.0),
                                                      (0.18, 0.38, 10, 5, 2.6), (0.36, 0.44, 12, 9, 0.4))):
            x = fx * w
            top = cy + h * 0.3
            Lk = h * L
            if floor is not None:
                Lk = min(Lk, top - floor - drop)
            c = (x, top - Lk / 2, z0 - 0.3)
            col = color if k % 2 == 0 else dark
            bone.box_at(c, [w * 0.2, Lk, 1.2], col, rotation=[lean, 0, out], pivot=[x, top, z0 - 0.3])
            pt = 1.2 + (k * 7) % 3 * 0.7
            bone.box_at((x + (0.4 if k % 2 else -0.3), top - Lk - pt / 2 + 0.2, z0 - 0.3), [w * 0.1, pt + 0.4, 1.3], col if k % 3 else dark, rotation=[lean, 0, out], pivot=[x, top, z0 - 0.3])
    if belly:   # the mane under the chest: a few short tufts
        if prefix:
            bone = host.model.bone(prefix + "_belly", [0, cy - h / 2 + 0.6, (z0 + z1) / 2], parent=host.name)
        for i in range(3):
            rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
            z = z0 + span * (i + 0.5) / 3
            for side in (1, -1):
                L = h * 0.16 + (rnd >> 7) % 2
                c = (side * w * 0.2, cy - h / 2 - L / 2 + 0.6, z)
                bone.box_at(c, [w * 0.25, L, span / 3 * 0.7], "fur", rotation=[(rnd >> 9) % 11 - 5, 0, side * 6], pivot=[c[0], cy - h / 2 + 0.6, z])
        for i in range(fringe):   # the ragged fringe: thin strips of different lengths hanging off the belly line
            rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
            z = z0 + span * (i + 0.5) / fringe
            for side in (1, -1):
                L = 2.2 + (rnd >> 5) % 4 * 0.8 + (1.0 if (i + side) % 3 == 0 else 0.0)
                x = side * (w * 0.34 + ((rnd >> 9) % 3) * 0.3)
                c = (x, cy - h / 2 + 0.8 - L / 2, z + ((rnd >> 12) % 3 - 1) * 0.4)
                bone.box_at(c, [1.0, L, 1.3 + (rnd >> 14) % 2 * 0.4], "fur_dark" if (i + side) & 1 else "fur", rotation=[(rnd >> 7) % 13 - 6, 0, side * (8 + (rnd >> 10) % 10)],
                            pivot=[x, cy - h / 2 + 0.8, z])


def leg(m, name, parent, top, upper_len, lower_len, foot_len, w, rot_upper, rot_lower, rot_foot, hairy):
    """upper (pivot top) -> lower (its child) -> foot (its child), each bone's box overlapping the joint above it by a
    knee's worth so the fold never shows a gap. Returns (foot, lower) for Model.ground_leg."""
    x, y, z = top
    d = w * 1.15
    up = m.bone(name + "_upper", [x, y, z], parent=parent, rotation=rot_upper)
    coat = "fur_leg" if hairy else "fur_thigh"
    barrel(up, y - upper_len / 2 + 0.4, z, z - d / 2, z + d / 2, w, upper_len + 0.8, coat, k=2)
    if not hairy:
        # the haunch: a mass bulging out over the top half of the thigh, its top face sun-bleached, the dark coat
        # only from the stifle down - a mid-to-dark gradient down the leg instead of one black post
        s = 1 if x > 0 else -1
        hl = upper_len * 0.58
        barrel(up, y - hl / 2 + 0.6, z, z - d / 2 - 0.6, z + d / 2 + 0.9, w * 1.3, hl, "fur_thigh", k=2)
        up.cube([x - w * 0.62, y - 1.1, z - d / 2 - 0.4], [w * 1.24, 2.0, d + 1.1], "fur_rump", inflate=0.25)   # the lit top
        up.cube([x + s * (w * 0.62 - 0.5), y - 4.2, z - d / 2 + 0.2], [1.0, 3.2, d * 0.75], "fur_rump", inflate=0.2,
                rotation=[0, 0, s * -10], pivot=[x + s * w * 0.62, y - 1.0, z])                                # light down the outer haunch
    ky = y - upper_len
    lo = m.bone(name + "_lower", [x, ky, z], parent=name + "_upper", rotation=rot_lower)
    lw = w * 0.72
    lo.cube([x - lw / 2, ky - lower_len, z - lw / 2], [lw, lower_len + 1.6, lw], "fur_leg" if hairy else "fur_thigh")
    if not hairy:   # the gradient runs on down the shin: mid at the hock, dark from the cannon to the hoof
        lo.cube([x - lw / 2 - 0.05, ky - lower_len - 0.2, z - lw / 2 - 0.05], [lw + 0.1, lower_len * 0.55, lw + 0.1], "fur")
    lo.cube([x - lw / 2 - 0.35, ky - 1.2, z - lw / 2 - 0.35], [lw + 0.7, 2.6, lw + 0.7], "fur_leg" if hairy else "fur_thigh")        # the joint
    fy = ky - lower_len
    ft = m.bone(name + "_foot", [x, fy, z], parent=name + "_lower", rotation=rot_foot)
    fw = w * 0.78
    ft.cube([x - fw / 2, fy - foot_len + 2.4, z - fw / 2], [fw, foot_len - 2.4 + 1.2, fw], "fur_leg" if hairy else "fur_thigh")
    ft.cube([x - fw / 2 - 0.2, fy - foot_len, z - fw / 2 - 0.3], [fw + 0.4, 2.6, fw + 0.6], "hoof")       # the hoof
    ft.cube([x - 0.15, fy - foot_len, z - fw / 2 - 0.4], [0.3, 2.6, 0.5], "fur_dark")                    # the cleft
    return name + "_foot", name + "_lower"


def quadruped(m, P, hairy_front=True):
    """The rig's bones and the plain beast on them from the proportions P. Returns the bones a body decorates."""
    body = m.bone("body", [0, (P["body_y0"] + P["body_y1"]) / 2, (P["body_z0"] + P["body_z1"]) / 2])
    bw, bh = P["body_w"], P["body_y1"] - P["body_y0"]
    barrel(body, (P["body_y0"] + P["body_y1"]) / 2, 0, P["body_z0"], P["body_z1"], bw, bh, "fur_dark")
    # the ribs behind the cape: the same mid as the hips over the upper two thirds, the dark kept to the belly
    barrel(body, P["body_y1"] - bh * 0.34, 0, P["body_z0"] + (P["body_z1"] - P["body_z0"]) * 0.5, P["body_z1"] + 0.2, bw + 0.2, bh * 0.68, "fur_thigh", k=3, inflate=0.05)
    if hairy_front:   # the cape: the pale shaggy coat over the forequarters, its ragged edge behind the shoulders
        cz1 = P["body_z0"] + (P["body_z1"] - P["body_z0"]) * 0.62
        ytop, ybot = P["body_y1"] + 0.6, P["body_y0"] + bh * 0.38
        barrel(body, (ytop + ybot) / 2, 0, P["body_z0"] - 0.4, cz1 - 3.0, bw + 0.9, ytop - ybot, "fur_front")
        # the ragged rear edge: three steps of decreasing height, then the locks take over
        for k, (dz, f) in enumerate(((0.0, 0.85), (1.6, 0.62), (2.9, 0.4))):
            barrel(body, ytop - (ytop - ybot) * f / 2, 0, cz1 - 3.0 + dz, cz1 - 3.0 + dz + 1.7, bw + 0.9 - k * 0.3, (ytop - ybot) * f, "fur_front", k=2)
    # the belly line sags behind the chest, the sternum sits lower in front
    body.cube([-bw * 0.3, P["body_y0"] - 1.2, P["body_z0"] + 3], [bw * 0.6, 2.0, P["body_z1"] - P["body_z0"] - 6], "fur", inflate=0.2)

    hump = m.bone("hump", [0, P["body_y1"] - 1.0, (P["hump_z0"] + P["hump_z1"]) / 2], parent="body")
    hz0, hz1, hw = P["hump_z0"], P["hump_z1"], P["hump_w"]
    base = P["body_y1"] - 3.0                     # sunk into the back and the neck base: no seam, no gap
    rise = P["hump_top"] - base
    m.hump_prof = ridge(hump, hz0 - 2.0, hz1 + 1.0, hw, base, rise, "fur_light", n=7, peak=0.42)

    hips = m.bone("hips", [0, (P["hips_y0"] + P["hips_y1"]) / 2, P["body_z1"]], parent="body")
    hy0, hy1 = P["hips_y0"], P["hips_y1"]
    slope = dict(rotation=[6, 0, 0], pivot=[0, hy1, P["body_z1"]])     # the croup slopes down to the tail
    barrel(hips, (hy0 + hy1) / 2, 0, P["body_z1"] - 1.5, P["hips_z1"], P["hips_w"], hy1 - hy0, "fur_hips", **slope)   # mid: one animal with the front (round 9)
    barrel(hips, hy0 + (hy1 - hy0) * 0.2, 0, P["body_z1"] - 1.0, P["hips_z1"] - 0.5, P["hips_w"] * 0.96, (hy1 - hy0) * 0.4, "fur_dark", k=2, inflate=0.05)   # the dark only low, under the flank fold
    barrel(hips, (hy0 + hy1) / 2 - 0.6, 0, P["hips_z1"] - 1.0, P["hips_z1"] + 1.6, P["hips_w"] * 0.8, (hy1 - hy0) * 0.76, "fur_hips", k=2, **slope)   # the pin bones: mid, not black
    # the hindquarters in three values (round 8: "any rear view shows a near-black blob"): the upper flank a mid
    # brown, the whole top line sun-bleached from the cape's edge to the tail, the dark only low and under
    hz0, hz1, hwid = P["body_z1"] - 1.0, P["hips_z1"] + 1.2, P["hips_w"]
    barrel(hips, hy1 - (hy1 - hy0) * 0.26, 0, hz0, hz1 - 0.3, hwid + 0.5, (hy1 - hy0) * 0.5, "fur_hips", k=2, inflate=0.1, **slope)
    barrel(hips, hy1 - 1.5, 0, hz0 + 0.5, hz1, hwid * 0.9, 4.2, "fur_rump", k=2, inflate=0.25, **slope)
    dz0 = P["hump_z1"] - 0.5
    for k, (fx, dzz, ww) in enumerate(((-0.28, 0.3, 2.0), (0.02, -0.2, 2.4), (0.3, 0.6, 1.8))):   # strands of the rump's light along the saddle, not a slab (round 11)
        body.cube([fx * bw - ww / 2, P["body_y1"] - 0.9, dz0 + dzz], [ww, 1.4, P["body_z1"] + 0.5 - dz0 - dzz], "fur_hips", rotation=[0, (k - 1) * 4, 0], pivot=[fx * bw, P["body_y1"], (dz0 + P["body_z1"]) / 2])
    for s in (1, -1):
        # the point of the hip: a bony wedge standing out at the pelvis' front corner, bleached on top
        px, pz = s * (hwid / 2 + 0.4), P["body_z1"] + 2.6
        hips.box_at((px, hy1 - 2.4, pz), [2.6, 3.0, 3.6], "fur_hips", rotation=[0, 0, s * -20], pivot=[px, hy1 - 0.9, pz])
        hips.box_at((px + s * 0.3, hy1 - 1.0, pz), [2.4, 1.4, 3.8], "fur_rump", rotation=[0, 0, s * -20], pivot=[px, hy1 - 0.9, pz], inflate=0.15)
        # the flank fold: a near-black crease where the thigh meets the belly, up and forward from the stifle
        cx, cy, cz = s * (hwid / 2 + 0.25), hy0 + 3.6, P["body_z1"] + 3.4
        hips.box_at((cx, cy, cz), [0.9, 1.0, 6.0], "nose", rotation=[-22, 0, 0], pivot=[cx, cy, cz])
    # the belly under the pelvis, a shade up from the thighs so the legs separate from it
    hips.cube([-hwid * 0.3, hy0 - 1.0, P["body_z1"] + 0.5], [hwid * 0.6, 2.4, P["hips_z1"] - P["body_z1"] - 3.5], "fur_hips", inflate=0.2)
    # the dock: a mound where the tail leaves the rump, bleached like the top line
    hips.cube([-2.0, hy1 - 1.6, P["hips_z1"] - 2.4], [4.0, 2.6, 3.6], "fur_rump", inflate=0.3, rotation=[-18, 0, 0], pivot=[0, hy1 - 0.3, P["hips_z1"] - 1.0])

    # the neck leaves the top of the chest and drops to the head; the head counter-tilts so its box reads level
    nd = P["neck_drop"]
    nz = P["body_z0"] + 1.0
    neck = m.bone("neck", [0, P["neck_y"], nz], parent="body", rotation=[-nd, 0, 0])
    nl = P["neck_len"]
    barrel(neck, P["neck_y"] - 2.5, 0, nz - nl - 1.0, nz + 2.5, bw * 0.62, 10.5, "fur_front" if hairy_front else "fur_dark", k=2)
    # the dewlap: dark hair hanging under the throat from the chest to the chin
    dew = m.bone("dewlap", [0, P["neck_y"] - 6.5, nz - nl / 2], parent="neck")
    dew.cube([-bw * 0.2, P["neck_y"] - 10.5, nz - nl - 0.5], [bw * 0.4, 4.0, nl + 1.5], "fur", rotation=[8, 0, 0], pivot=[0, P["neck_y"] - 6.5, nz - nl / 2])
    head = m.bone("head", [0, P["neck_y"] - 4.0, nz - nl], parent="neck", rotation=[nd, 0, 0])   # hung low off the neck
    return body, hump, hips, neck, head


def bison_head(m, P, head):
    hw, hh, hl, ml = P["head_w"], P["head_h"], P["head_len"], P["muzzle_len"]
    hx, hy, hz = head.pivot
    top, bot = hy + hh * 0.55, hy - hh * 0.45
    barrel(head, (top + bot) / 2, 0, hz - hl, hz + 1.5, hw, hh, "fur_head", k=2)
    # the woolly cap over the brow (a mop of cape-coloured tufts) and the forelock falling between the eyes
    head.cube([-hw / 2 - 0.4, top - 2.4, hz - hl + 0.5], [hw + 0.8, 3.2, hl - 1.5], "fur_brow", inflate=0.3)
    for k, (dx, dzz, lean) in enumerate(((-0.3, 0.3, 16), (0.3, 0.6, -12), (0.0, 0.15, 0))):
        c = (dx * hw, top + 1.2, hz - hl + dzz * hl)
        head.box_at(c, [hw * 0.45, 2.0, hl * 0.3], "fur_brow" if k != 2 else "fur_dark", rotation=[0, 0, lean], pivot=list(c))
    head.cube([-1.8, top - 3.6, hz - hl - 0.8], [3.6, 3.0, 1.2], "fur_brow")
    for k, fx in enumerate((-0.32, -0.08, 0.16, 0.38)):   # lock ridges down the forehead, partings between
        head.cube([fx * hw - 0.6, top - 2.2, hz - hl - 0.9], [1.2, 3.4, 0.8], "fur_head" if k % 2 else "fur_brow", rotation=[0, 0, (k - 1.5) * 5], pivot=[fx * hw, top + 1.0, hz - hl - 0.5])
        head.cube([fx * hw + 0.7, top - 2.0, hz - hl - 0.7], [0.5, 2.8, 0.5], "fur_dark")
    # the muzzle: lower and narrower, the broad wet nose, the lip
    mz0 = hz - hl - ml
    barrel(head, bot + hh * 0.34, 0, mz0, hz - hl + 0.6, hw * 0.74, hh * 0.68, "muzzle", k=2)
    head.cube([-hw * 0.3, bot + hh * 0.28, mz0 - 0.5], [hw * 0.6, 2.6, 1.0], "nose")
    head.cube([-hw * 0.28, bot - 0.4, mz0 + 0.4], [hw * 0.56, 1.8, ml - 1.0], "muzzle")
    # the nostrils: two wings of skin standing off the nose pad's outer corners, a dark notch cut between each wing
    # and the pad (round 8: the muzzle ended in one flat dark rectangle)
    for s in (1, -1):
        wx = s * hw * 0.3
        head.box_at((wx + s * 0.5, bot + hh * 0.28 + 1.5, mz0 - 0.3), [1.5, 1.6, 1.6], "muzzle", rotation=[0, s * 18, s * -14], pivot=[wx, bot + hh * 0.28 + 1.5, mz0])
        head.box_at((wx - s * 0.35, bot + hh * 0.28 + 1.9, mz0 - 0.95), [0.7, 1.0, 0.5], "eye")                 # the notch
    # the brow shelf across the face: a bar from each eye in toward the forelock, standing a pixel proud of the
    # forehead and sloping down and out, a dark shadow line under it - the face breaks at the eyes from the front too
    for s in (1, -1):
        bx = s * (hw * 0.26 + 0.2)
        head.box_at((bx, top - 2.5, hz - hl - 0.5), [hw * 0.46, 1.4, 1.6], "fur_brow", rotation=[6, 0, s * -9], pivot=[s * 1.0, top - 2.2, hz - hl - 0.5])
        head.box_at((bx, top - 3.35, hz - hl - 0.35), [hw * 0.42, 0.5, 1.2], "fur_dark", rotation=[6, 0, s * -9], pivot=[s * 1.0, top - 2.2, hz - hl - 0.5])
    # the brow: a heavy ridge standing a full pixel proud over each eye, the eye sunk back into the socket under it
    # and a cheekbone standing out below (the eye was a block glued to a flat side wall, round 8)
    for s in (1, -1):
        ex = s * (hw / 2 - 0.25)
        head.cube([ex - 0.7, top - 3.9, hz - hl + 1.3], [1.4, 1.3, 1.6], "eye")
        head.box_at((ex + s * 0.5, top - 2.1, hz - hl + 2.0), [1.6, 1.7, 3.8], "fur_brow", rotation=[0, s * -14, s * 12], pivot=[ex, top - 1.6, hz - hl + 2.0])   # the brow
        head.box_at((ex + s * 0.35, top - 3.05, hz - hl + 2.0), [1.3, 0.5, 3.4], "fur_dark", rotation=[0, s * -14, s * 12], pivot=[ex, top - 1.6, hz - hl + 2.0])  # its shadow over the socket
        head.box_at((ex + s * 0.45, top - 2.4, hz - hl + 0.2), [1.3, 1.2, 1.4], "fur_head", rotation=[0, s * 24, s * 8], pivot=[ex, top - 1.8, hz - hl + 0.6])
        head.box_at((ex + s * 0.4, top - 5.1, hz - hl + 2.3), [1.2, 1.3, 2.6], "fur_brow", rotation=[0, 0, s * -8], pivot=[ex, top - 4.6, hz - hl + 2.3])    # the cheekbone
    # the beard under the chin, longer at the back
    bb = m.bone("beard", [0, bot + 0.2, hz - hl / 2], parent="head")
    beard = bb.cube([-2.2, bot - 4.2, hz - hl + 0.5], [4.4, 4.4, hl - 2.0], "fur_dark")
    bb.cube([-1.4, bot - 5.6, hz - 2.0], [2.8, 2.0, 2.0], "fur_dark", rotation=[12, 0, 0])
    for k, (dx, dz, L, lean) in enumerate(((-1.6, 0.8, 3.2, -14), (-0.5, 2.4, 2.2, 6), (0.6, 0.2, 3.8, 10), (1.5, 1.8, 2.6, -8), (0.1, 4.0, 1.8, 0))):
        # the ragged fringe under the beard: five strips of different lengths, each leaning its own way
        c = (dx, bot - 4.2 - L / 2 + 0.4, hz - hl + 0.5 + dz)
        bb.box_at(c, [0.9, L, 1.1], "fur_dark" if k % 2 else "fur_head", rotation=[(k - 2) * 6, 0, lean], pivot=[dx, bot - 3.8, c[2]])
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


def berries(bone, x, y, z):
    """A bunch of glow berries: three or four 1.2 px berries on a dark rim (the bunch's shadow), a leaf over them."""
    bone.cube([x - 1.6, y - 1.0, z - 1.6], [3.2, 0.9, 3.2], "berry_rim")                    # the dark pad under the bunch
    for (dx, dy, dz) in ((-0.8, 0.0, -0.7), (0.8, -0.1, -0.4), (0.0, 0.1, 0.8), (-0.2, 0.9, 0.1), (0.9, 0.6, 0.6)):
        bone.cube([x + dx - 0.7, y + dy - 0.1, z + dz - 0.7], [1.4, 1.4, 1.4], "glow_berry")
        bone.cube([x + dx - 0.25, y + dy + 0.5, z + dz - 0.25], [0.5, 0.5, 0.5], "glow_core", inflate=0.12)   # the lit centre texel, just proud of the skin
    bone.cube([x - 1.0, y + 1.3, z - 1.0], [2.0, 0.5, 2.0], "leaf")


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
            for side in (1, -1):   # the overhang: a fringe leaning down over the edge, lumps draping down the flank
                c = (cx + side * (w / 2 + 0.2), cy - 0.6, z)
                bone.box_at(c, [1.3, 2.2, dz * 0.8], "moss_dark", rotation=[0, 0, side * (18 + (rnd >> 10) % 14)], pivot=[c[0], cy + 0.4, z])
                drop = 1.5 + (rnd >> 13) % 3
                bone.box_at((cx + side * (w / 2 + 0.9), cy - 1.2 - drop / 2, z + ((rnd >> 15) % 3 - 1) * 0.5), [1.4, drop + 1.0, dz * 0.5], "moss", rotation=[0, 0, side * 8])
                bone.box_at((cx + side * (w / 2 + 0.6), cy - 2.6 - drop, z), [1.6, 0.8, dz * 0.9], "fur_dark")      # the shadow line under it
    mossy(hump, 0, top - 0.6, hz0 + 1.0, hz1 - 0.5, hw * 0.72, 5, prof=m.hump_prof)
    # the lobes and the front lumps hang from a bone of their own (moss_hump) so they jiggle a beat behind the hump
    mh = m.bone("moss_hump", [0, m.hump_prof((hz0 + hz1) / 2) + 0.5, (hz0 + hz1) / 2], parent="hump")
    for (fx, fz, r) in ((-0.22, 0.3, 2.6), (0.18, 0.55, 3.0), (0.05, 0.12, 2.2)):   # rounded lobes on the cushion
        x, z = fx * hw, hz0 + 1.0 + (hz1 - hz0) * fz
        yb = m.hump_prof(z) + 0.9
        mh.cube([x - r / 2, yb, z - r / 2], [r, 1.0, r], "moss")
        mh.cube([x - r * 0.3, yb + 0.9, z - r * 0.3], [r * 0.6, 0.8, r * 0.6], "moss_pale")
    zf = hz0 + 1.0
    # the front of the cushion: three lumps of different sizes tumbling over the edge (round 7's single slab read
    # as a green visor on the forehead from the front, round 8)
    for (dx, dy, r, lean) in ((-3.4, 1.4, 2.0, -30), (2.6, 1.2, 2.6, -18)):   # off centre, up on the crest: never a pair of eyes over the forehead
        c = (dx, m.hump_prof(zf) + dy, zf + 0.4 - r * 0.3)
        mh.box_at(c, [r, r * 0.7, r], "moss_dark" if r < 2 else "moss", rotation=[lean, 0, 0], pivot=[dx, m.hump_prof(zf) + 0.4, zf])
        mh.box_at((c[0], c[1] + r * 0.45, c[2] + 0.2), [r * 0.6, 0.7, r * 0.6], "moss_pale", rotation=[lean, 0, 0], pivot=[dx, m.hump_prof(zf) + 0.4, zf])
    for s in (1, -1):
        # moss creeping down the hump's flank, and the roots: thick sinews of living wood standing proud of the hair,
        # from the hump and from the hips, each ending in a dark root tip
        c = (s * (hw * 0.4), top - 4.0, (hz0 + hz1) / 2 + 1.0)
        hump.box_at(c, [1.4, 3.6, 5.5], "moss_dark", rotation=[0, 0, s * -32], pivot=list(c))
        # the roots: each a CHAIN of three tapering bones (root_<l|r><k> > _1 > _2) hanging off the hump or the hips,
        # a knob at every joint, each segment set 1-2 px off the joint's axis so the root crooks like living wood,
        # the lengths and bends different per root. The animator (MossbackBisonUnitModel) sways the lower bones a
        # beat behind the upper (Life.chain), so they trail and swing instead of hanging as rigid slabs (round 8)
        for k, (z, L, lean, parts, bends, dxs) in enumerate((
                (hz0 + 4.0, 9.0, 12, (0.42, 0.33, 0.25), ((3, 14), (-5, -17), (4, -7)), (1.2, -1.4, 0.8)),
                (hz1 - 2.0, 11.0, -6, (0.36, 0.36, 0.28), ((-4, -8), (6, 15), (-3, -10)), (-1.0, 1.6, -1.0)),
                (P["body_z1"] + 6.0, 7.5, 16, (0.46, 0.54), ((2, 18), (-6, -22)), (1.5, -1.2)))):
            host = hips if z > P["body_z1"] else hump
            x = s * (P["body_w"] / 2 + 1.2 if host is hump else P["hips_w"] / 2 + 1.0)
            y = (P["body_y1"] + 0.5) if host is hump else P["hips_y1"] - 0.5
            parent = host.name
            px, py, pz = x, y, z
            widths = (2.8, 2.0, 1.3)
            for q, frac in enumerate(parts):
                Lq = L * frac
                name = "root_%s%d" % ("l" if s > 0 else "r", k) + ("" if q == 0 else "_%d" % q)
                rb = m.bone(name, [px, py, pz], parent=parent, rotation=[bends[q][0], 0, s * (lean if q == 0 else bends[q][1])])
                wq = widths[q]
                last = q == len(parts) - 1
                ox = s * dxs[q]
                rb.box_at((px + ox, py - Lq / 2 - 0.2, pz), [wq, Lq + 0.6, wq], "root" if not last else "wood_dark")
                if not last:
                    rb.box_at((px + ox + s * (wq / 2 - 0.2), py - Lq / 2, pz - wq * 0.3), [0.5, Lq - 0.6, 0.6], "root_edge")   # the lit edge
                    rb.box_at((px + ox, py - Lq / 2, pz + wq * 0.5), [wq * 0.7, Lq * 0.5, 0.5], "wood_dark")                # a dark groove behind
                if q > 0:   # the knob at the joint: a burl wider than both segments
                    rb.box_at((px + ox * 0.4, py - 0.1, pz + 0.2), [wq + 1.2, wq + 0.6, wq + 1.0], "root_knob", rotation=[14, 0, s * 9], pivot=[px, py, pz])
                else:       # where it leaves the hide: a collar of moss
                    rb.box_at((px, py - 0.3, pz), [wq + 1.0, 1.4, wq + 1.4], "moss_dark", rotation=[0, 0, s * -10], pivot=[px, py, pz])
                if last:    # the tip: a rootlet or two
                    rb.box_at((px + ox + s * 0.4, py - Lq - 0.9, pz + 0.3), [0.7, 2.0, 0.7], "wood_dark", rotation=[12, 0, s * 20], pivot=[px + ox, py - Lq, pz])
                parent = name
                py -= Lq
    for (x, z) in ((-3.4, hz0 + 5), (2.8, hz0 + 8), (0.4, hz1 - 2.5), (-4.6, hz1 - 1.5), (4.2, hz0 + 10),
                   (hw * 0.42, hz0 + 3.5), (-hw * 0.42, hz0 + 7.5), (hw * 0.4, hz1 - 3.5), (-hw * 0.4, hz1 - 1.0)):
        edge = abs(x) > hw * 0.38
        yb = m.hump_prof(z) + (0.4 if not edge else -1.2)      # the later four hang on the moss's edge, under its fringe
        berries(hump, x, yb, z)
    # berries along the rump's moss edge too
    for (x, z) in ((P["hips_w"] * 0.42, P["body_z1"] + 4.0), (-P["hips_w"] * 0.42, P["body_z1"] + 8.0)):
        berries(hips, x, P["hips_y1"] - 1.2, z)
    # moss on the rump, round where the moss bed sits, and a mossy saddle line on the spine
    mossy(hips, 0, P["hips_y1"] - 0.4, P["body_z1"] + 1.0, P["hips_z1"] - 1.5, P["hips_w"] * 0.8, 9)
    # raised lobes on the rump's strip (round 8: it read as a flat ribbon from the side): three rounded clumps of
    # different sizes, each with a lit crown, leaning different ways
    mr = m.bone("moss_rump", [0, P["hips_y1"] + 0.6, (P["body_z1"] + P["hips_z1"]) / 2], parent="hips")
    for (fx, fz, r, lean) in ((-0.2, 0.2, 3.0, 6), (0.22, 0.52, 3.6, -5), (-0.05, 0.82, 2.4, 9)):
        x, z = fx * P["hips_w"], P["body_z1"] + 1.0 + (P["hips_z1"] - 2.5 - P["body_z1"]) * fz
        yb = P["hips_y1"] + 0.6
        mr.box_at((x, yb + r * 0.3, z), [r, r * 0.6, r], "moss", rotation=[0, lean, lean], pivot=[x, yb, z])
        mr.box_at((x + 0.2, yb + r * 0.3 + r * 0.32, z - 0.2), [r * 0.62, r * 0.4, r * 0.62], "moss_pale", rotation=[0, lean, lean], pivot=[x, yb, z])
        mr.box_at((x - r * 0.1, yb + r * 0.3 + r * 0.55, z + r * 0.1), [r * 0.3, 0.6, r * 0.3], "moss_pale", rotation=[0, lean, lean], pivot=[x, yb, z])
    body.cube([-1.2, P["body_y1"] - 0.8, P["hump_z1"] - 0.5], [2.4, 1.8, P["body_z1"] - P["hump_z1"] + 1.0], "moss_dark")


def mossback_bison():
    P = BISON
    m = Model("Mossback Bison", "verdant", "T2 war-bison, the Hollow Grove carrier (quadruped rig)", PALETTE, EMISSIVE)
    body, hump, hips, neck, head = quadruped(m, P, hairy_front=True)
    knee = P["shoulder"][1] - P["elbow_len"] + 1.0
    # dangling tufts that break the cape line: under the chest and at the elbows, 1 x 2-3 px, out past the cape
    for (fx, fy, fz, L, lean) in ((0.55, 0.0, -11.0, 3.0, 12), (-0.55, 0.0, -8.0, 2.4, -10), (0.58, 0.5, -4.5, 2.6, 8),
                                  (-0.6, 0.5, -2.0, 3.2, -14), (0.2, -0.1, -13.5, 2.2, 0), (-0.25, -0.1, -12.5, 2.8, 0)):
        x = fx * (P["body_w"] + 2.4)
        top = knee + 1.5 + fy
        body.box_at((x, top - L / 2, fz), [1.1, L, 1.3], "fur" if fx * fx > 0.2 else "fur_front", rotation=[0, 0, lean], pivot=[x, top, fz])
    # relief on the withers and the forehead: raised lock ridges with darker partings between them
    for k, (fx, lean) in enumerate(((-0.3, 6), (-0.1, -4), (0.12, 3), (0.32, -7))):
        x = fx * P["hump_w"]
        z0, z1 = P["hump_z0"] + 2.0 + k * 0.7, P["hump_z1"] - 2.5 - k * 0.4
        hump.cube([x - 0.7, P["hump_top"] - 5.0, z0], [1.4, 0.9, z1 - z0], "fur_pale" if k % 2 else "fur_light", rotation=[0, lean, 0], pivot=[x, P["hump_top"] - 4.5, (z0 + z1) / 2])
        hump.cube([x + 0.9, P["hump_top"] - 5.2, z0 + 1.0], [0.6, 0.6, z1 - z0 - 2.0], "fur_dark", rotation=[0, lean, 0], pivot=[x, P["hump_top"] - 4.5, (z0 + z1) / 2])
    shag(body, (P["body_y0"] + P["body_y1"]) / 2 + 0.4, 0, P["body_z0"], P["body_z0"] + 12.0, P["body_w"] + 0.9, P["body_y1"] - P["body_y0"] + 0.4, "fur_front", n=2, seed=3, dark="fur", rows=6, front=True, floor=knee, prefix="cape",
         step=0.13, sweep=(10, 11), fringe=5)
    shag(hump, P["hump_top"] - 3.5, 0, P["hump_z0"] + 1.0, P["hump_z1"] - 0.5, P["hump_w"] * 0.9, 6.0, "fur_light", n=3, seed=11, dark="fur_rump", belly=False, prof=m.hump_prof, rows=2, prefix="humpcoat", sweep=(6, 8))
    shag(neck, P["neck_y"] - 3.0, 0, P["body_z0"] - P["neck_len"], P["body_z0"] + 1.0, P["body_w"] * 0.62, 10.5, "fur_front", n=2, seed=7, dark="fur", rows=3, belly=False, prefix="mane", step=0.2, sweep=(8, 10))
    bison_head(m, P, head)
    bison_grove(m, P, body, hump, hips)
    # the tail: a two-link chain off the rump, a tuft at the tip
    tz = P["hips_z1"] + 0.8
    tail = m.bone("tail", [0, P["tail_y"], tz], parent="hips", rotation=[-14, 0, 0])
    tail.cube([-0.9, P["tail_y"] - P["tail_len"], tz - 0.9], [1.8, P["tail_len"] + 0.6, 1.8], "fur_dark")
    tail.cube([-1.3, P["tail_y"] - 3.2, tz - 1.3], [2.6, 3.8, 2.6], "fur_thigh")                      # the thick base
    for k, (dx, L, lean) in enumerate(((-1.4, 3.2, 16), (1.3, 3.8, -12), (0.0, 2.6, 0))):            # the tuft at the root
        c = (dx, P["tail_y"] - 1.6 - L / 2, tz + 1.5)
        tail.box_at(c, [1.4, L, 1.3], "fur_rump" if k != 1 else "fur_dark", rotation=[14, 0, lean], pivot=[dx, P["tail_y"] - 1.6, tz + 1.5])
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
    m.pose = {"head": [-12, 3, 0], "neck": [0, -2, 0]}        # the forehead near vertical, the muzzle toward the grass, the head turned a little
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
