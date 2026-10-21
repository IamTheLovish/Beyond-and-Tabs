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
    "hair": "#5a4028", "hair_dark": "#3a2a1a", "hair_pale": "#7a5f45", "hair_moss": "#3f4a22",
    "skin": "#5a4634", "nose": "#1e1814", "eye": "#0d0b09",
    "horn": "#2a2622", "horn_tip": "#4a4540", "hoof": "#1f1a16",
    "moss": "#4f7a2a", "moss_dark": "#3a5a1f", "leaf": "#5c8f33", "wood": "#5b3f26", "wood_dark": "#3f2b1a",
    "glow_berry": "#ffb347",
    "tabard": "#2f5fb3", "tabard_dark": "#244a8c",
}
EMISSIVE = ["glow_berry"]

# the Mossback Bison's proportions (px): a heavy, high-withered ox - the hump the highest point, the head carried low
BISON = dict(
    body_w=14.0, body_z0=-14.0, body_z1=4.0, body_y0=14.5, body_y1=31.5,   # the thorax barrel
    hips_w=12.0, hips_z1=16.0, hips_y0=17.0, hips_y1=31.0,               # the pelvis barrel (rump top 31 + shag = 34)
    hump_top=40.0, hump_z0=-13.0, hump_z1=-1.0, hump_w=12.0,
    neck_y=28.0, neck_len=8.0, neck_drop=22.0,                           # the neck leaves the chest at y 28, down 22 deg
    head_w=9.0, head_h=9.0, head_len=9.0, muzzle_len=5.5,
    shoulder=(5.0, 27.0, -9.0), elbow_len=11.0, fore_len=9.0, front_foot=7.0, front_w=4.2,
    hip=(4.5, 28.0, 11.0), thigh_len=11.0, shin_len=9.0, hind_foot=8.0, hind_w=4.6,
    tail_y=29.0, tail_len=8.0, tail_tip=4.0,
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


def shag(bone, cy, cz, z0, z1, w, h, color, n=5, seed=0):
    """A shaggy coat over a barrel: n hanging tufts along each flank and a few on the top, each a small slab turned a
    little differently, so the silhouette breaks like matted hair instead of a tube (a bison's front half)."""
    rnd = seed
    for i in range(n):
        rnd = (rnd * 1103515245 + 12345) & 0x7fffffff
        z = z0 + (z1 - z0) * (i + 0.5) / n
        for side in (1, -1):
            lean = 6 + (rnd >> 8) % 12
            L = h * 0.42 + (rnd >> 4) % 3
            c = (side * (w / 2 + 0.15), cy - h * 0.1, z)
            bone.box_at(c, [0.9, L, (z1 - z0) / n + 0.3], color, rotation=[0, 0, side * lean], pivot=list(c))
        c = ((rnd >> 12) % 5 - 2, cy + h / 2 - 0.2, z)
        bone.box_at(c, [w * 0.45, 1.2, (z1 - z0) / n + 0.2], color, rotation=[0, 0, (rnd >> 6) % 9 - 4], pivot=list(c))


def leg(m, name, parent, top, upper_len, lower_len, foot_len, w, rot_upper, rot_lower, rot_foot, hairy):
    """upper (pivot top) -> lower (its child) -> foot (its child), each bone's box overlapping the joint above it by a
    knee's worth so the fold never shows a gap. Returns (foot, lower) for Model.ground_leg."""
    x, y, z = top
    d = w * 1.15
    up = m.bone(name + "_upper", [x, y, z], parent=parent, rotation=rot_upper)
    barrel(up, y - upper_len / 2 + 0.4, z, z - d / 2, z + d / 2, w, upper_len + 0.8, "skin" if not hairy else "hair", k=2)
    up.cube([x - w / 2 - 0.2, y - upper_len / 2, z - d / 2 - 0.2], [w + 0.4, upper_len * 0.55, d + 0.4], "hair_dark" if hairy else "hair_pale", inflate=0.1)
    ky = y - upper_len
    lo = m.bone(name + "_lower", [x, ky, z], parent=name + "_upper", rotation=rot_lower)
    lw = w * 0.78
    lo.cube([x - lw / 2, ky - lower_len, z - lw / 2], [lw, lower_len + 1.6, lw], "skin")
    lo.cube([x - lw / 2 - 0.3, ky - 1.0, z - lw / 2 - 0.3], [lw + 0.6, 2.4, lw + 0.6], "hair_pale")        # the joint
    fy = ky - lower_len
    ft = m.bone(name + "_foot", [x, fy, z], parent=name + "_lower", rotation=rot_foot)
    fw = w * 0.84
    ft.cube([x - fw / 2, fy - foot_len + 2.4, z - fw / 2], [fw, foot_len - 2.4 + 1.2, fw], "skin")
    ft.cube([x - fw / 2 - 0.2, fy - foot_len, z - fw / 2 - 0.3], [fw + 0.4, 2.6, fw + 0.6], "hoof")       # the hoof
    ft.cube([x - 0.15, fy - foot_len, z - fw / 2 - 0.4], [0.3, 2.6, 0.5], "hair_dark")                    # the cleft
    return name + "_foot", name + "_lower"


def quadruped(m, P, hairy_front=True):
    """The rig's bones and the plain beast on them from the proportions P. Returns the bones a body decorates."""
    body = m.bone("body", [0, (P["body_y0"] + P["body_y1"]) / 2, (P["body_z0"] + P["body_z1"]) / 2])
    bw, bh = P["body_w"], P["body_y1"] - P["body_y0"]
    barrel(body, (P["body_y0"] + P["body_y1"]) / 2, 0, P["body_z0"], P["body_z1"], bw, bh, "hair" if hairy_front else "skin")
    # the belly line sags behind the chest, the sternum sits lower in front
    body.cube([-bw * 0.3, P["body_y0"] - 1.2, P["body_z0"] + 3], [bw * 0.6, 2.0, P["body_z1"] - P["body_z0"] - 6], "hair_pale", inflate=0.2)

    hump = m.bone("hump", [0, P["body_y1"], (P["hump_z0"] + P["hump_z1"]) / 2], parent="body")
    hz0, hz1, hw = P["hump_z0"], P["hump_z1"], P["hump_w"]
    rise = P["hump_top"] - P["body_y1"]
    for j, (f0, f1, fw) in enumerate(((0.0, 0.45, 1.0), (0.45, 0.78, 0.86), (0.78, 1.0, 0.62))):
        y0, y1 = P["body_y1"] - 0.5 + rise * f0, P["body_y1"] + rise * f1
        zin = (hz1 - hz0) * (1 - fw) / 2
        barrel(hump, (y0 + y1) / 2, 0, hz0 + zin, hz1 - zin * 0.6, hw * fw, y1 - y0, "hair", k=2)

    hips = m.bone("hips", [0, (P["hips_y0"] + P["hips_y1"]) / 2, P["body_z1"]], parent="body")
    hy0, hy1 = P["hips_y0"], P["hips_y1"]
    barrel(hips, (hy0 + hy1) / 2, 0, P["body_z1"] - 1.5, P["hips_z1"], P["hips_w"], hy1 - hy0, "skin")
    barrel(hips, (hy0 + hy1) / 2 + 0.5, 0, P["hips_z1"] - 1.0, P["hips_z1"] + 1.6, P["hips_w"] * 0.8, (hy1 - hy0) * 0.8, "skin", k=2)

    # the neck leaves the top of the chest and drops to the head; the head counter-tilts so its box reads level
    nd = P["neck_drop"]
    nz = P["body_z0"] + 1.0
    neck = m.bone("neck", [0, P["neck_y"], nz], parent="body", rotation=[-nd, 0, 0])
    nl = P["neck_len"]
    barrel(neck, P["neck_y"] - 2.5, 0, nz - nl - 1.0, nz + 2.0, bw * 0.62, 9.5, "hair" if hairy_front else "skin", k=2)
    head = m.bone("head", [0, P["neck_y"] - 2.0, nz - nl], parent="neck", rotation=[nd, 0, 0])
    return body, hump, hips, neck, head


def bison_head(m, P, head):
    hw, hh, hl, ml = P["head_w"], P["head_h"], P["head_len"], P["muzzle_len"]
    hx, hy, hz = head.pivot
    top, bot = hy + hh * 0.55, hy - hh * 0.45
    barrel(head, (top + bot) / 2, 0, hz - hl, hz + 1.5, hw, hh, "hair", k=2)
    # the woolly cap over the brow and the forelock falling between the eyes
    head.cube([-hw / 2 - 0.4, top - 2.2, hz - hl + 0.5], [hw + 0.8, 3.0, hl - 1.5], "hair_dark", inflate=0.25)
    head.cube([-1.6, top - 3.4, hz - hl - 0.6], [3.2, 2.6, 1.0], "hair_dark")
    # the muzzle: lower and narrower, the broad wet nose, the lip
    mz0 = hz - hl - ml
    barrel(head, bot + hh * 0.34, 0, mz0, hz - hl + 0.6, hw * 0.74, hh * 0.68, "hair_pale", k=2)
    head.cube([-hw * 0.3, bot + hh * 0.28, mz0 - 0.5], [hw * 0.6, 2.6, 1.0], "nose")
    head.cube([-hw * 0.28, bot - 0.4, mz0 + 0.4], [hw * 0.56, 1.8, ml - 1.0], "hair_pale")
    # eyes, set in the sides under the brow
    for s in (1, -1):
        head.cube([s * (hw / 2 + 0.05) - 0.55, top - 3.8, hz - hl + 1.4], [1.1, 1.3, 1.5], "eye")
    # the beard under the chin, longer at the back
    beard = head.cube([-2.2, bot - 4.2, hz - hl + 0.5], [4.4, 4.4, hl - 2.0], "hair_dark")
    head.cube([-1.4, bot - 5.6, hz - 2.0], [2.8, 2.0, 2.0], "hair_dark", rotation=[12, 0, 0])
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
        eb.box_at(c, [3.2, 1.6, 1.1], "hair_pale", rotation=[0, 0, s * 20], pivot=list(c))
    return beard


def bison_grove(m, P, body, hump, hips):
    """The living grove's roots and moss on the hide (the moss bed, bushes and canopy are real blocks drawn by the
    renderer's GroveLayer over the rump and hump): moss creeping over the hump and flanks, roots hanging down the sides,
    glow berries in the shag."""
    hz0, hz1, hw = P["hump_z0"], P["hump_z1"], P["hump_w"]
    top = P["hump_top"]
    hump.cube([-hw * 0.36, top - 1.4, hz0 + 2.0], [hw * 0.72, 1.6, hz1 - hz0 - 4.0], "moss", inflate=0.3)
    for s in (1, -1):
        c = (s * (hw * 0.42), top - 3.6, (hz0 + hz1) / 2)
        hump.box_at(c, [1.0, 4.0, 6.0], "moss_dark", rotation=[0, 0, s * -30], pivot=list(c))
        # roots: a few sinews of living wood hanging over the flank from the hump and the hips
        for k, (z, L, lean) in enumerate(((hz0 + 2.5, 7.0, 10), (hz1 - 2.0, 9.0, -6), (P["body_z1"] + 6.0, 6.0, 14))):
            host = hips if z > P["body_z1"] else hump
            x = s * (P["body_w"] / 2 + 0.6 if host is hump else P["hips_w"] / 2 + 0.5)
            y = (P["body_y1"] - 0.5) if host is hump else P["hips_y1"] - 0.8
            c = (x, y - L / 2, z)
            host.box_at(c, [1.2, L, 1.2], "wood", rotation=[0, 0, s * lean], pivot=list(c))
            host.box_at((x, y - L - 0.3, z + 0.4), [0.9, 1.4, 0.9], "wood_dark", rotation=[0, 0, s * lean], pivot=[x, y - L / 2, z])
    for (x, z) in ((-3.2, hz0 + 4), (2.6, hz0 + 7), (0.4, hz1 - 2.5), (-4.4, hz1 - 1.5)):
        hump.cube([x - 0.45, top - 2.8, z - 0.45], [0.9, 0.9, 0.9], "glow_berry")
        hump.cube([x - 0.7, top - 1.6, z - 0.7], [1.4, 0.7, 1.4], "leaf")
    # moss on the rump, round where the moss bed sits, and a mossy saddle line on the spine
    hips.cube([-P["hips_w"] * 0.38, P["hips_y1"] - 0.6, P["body_z1"] + 0.5], [P["hips_w"] * 0.76, 1.2, P["hips_z1"] - P["body_z1"] - 2.0], "moss", inflate=0.2)
    body.cube([-1.0, P["body_y1"] - 0.6, P["hump_z1"] - 0.5], [2.0, 1.4, P["body_z1"] - P["hump_z1"] + 1.0], "moss_dark")


def mossback_bison():
    P = BISON
    m = Model("Mossback Bison", "verdant", "T2 war-bison, the Hollow Grove carrier (quadruped rig)", PALETTE, EMISSIVE)
    body, hump, hips, neck, head = quadruped(m, P, hairy_front=True)
    shag(body, (P["body_y0"] + P["body_y1"]) / 2, 0, P["body_z0"], P["body_z0"] + 11, P["body_w"], P["body_y1"] - P["body_y0"], "hair_dark", n=4, seed=3)
    shag(hump, P["hump_top"] - 4.0, 0, P["hump_z0"] + 0.5, P["hump_z1"] - 0.5, P["hump_w"] * 0.96, 7.0, "hair_dark", n=3, seed=11)
    shag(neck, P["neck_y"] - 3.0, 0, P["body_z0"] - P["neck_len"], P["body_z0"] + 1.0, P["body_w"] * 0.62, 9.0, "hair_dark", n=3, seed=7)
    bison_head(m, P, head)
    bison_grove(m, P, body, hump, hips)
    # the tail: a two-link chain off the rump, a tuft at the tip
    tz = P["hips_z1"] + 0.8
    tail = m.bone("tail", [0, P["tail_y"], tz], parent="hips", rotation=[-14, 0, 0])
    tail.cube([-0.8, P["tail_y"] - P["tail_len"], tz - 0.8], [1.6, P["tail_len"] + 0.6, 1.6], "skin")
    tip = m.bone("tail_tip", [0, P["tail_y"] - P["tail_len"], tz], parent="tail", rotation=[-6, 0, 0])
    tip.cube([-1.3, P["tail_y"] - P["tail_len"] - P["tail_tip"], tz - 1.3], [2.6, P["tail_tip"] + 0.8, 2.6], "hair_dark")
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
    m.pose = {"head": [-3, 0, 0]}
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
