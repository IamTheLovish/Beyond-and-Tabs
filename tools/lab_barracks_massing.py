#!/usr/bin/env python3
"""Barracks (Sunforged T1 army lab) L1 massing options, Nov 2026 - judged blind against the shipped Training Hall.

Baseline (eyes 41263811904, two fresh critics 5 / 5): "white villa with blue banners" / "white box house, sandy yard";
shape 4-5 (a bell turret and a hall of near-equal boxes), detail 3-5, lapis bands + blue banners + red-white targets
read as team colours. Value already owns the lights (luma 0.648 vs lawn 0.409). Footprint 10 x 8 (BuildingLevels),
L1 <= 15 high, the spawn columns (4..5, 3..4) open to y 8, the yard walkable out to the south.
  A  GREAT DOME: one big tiered dome (round from above) over the hall's east, a slim campanile on the west corner
  B  CAMPANILE AND TEMPLE: a tall tapering bell tower on the camera's corner, a temple hall with its gable to the camera
  C  HALO ARENA: an oval colonnaded drill ring (the White Angel's halo seen from above) round the spawn, one tall
     sun-disc column on its north rim
Plain blocks only (quartz white, gold crowns, sand yard, andesite kerb), no detail.

Usage: python3 tools/lab_barracks_massing.py OUTDIR
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lab_barracks as lb  # noqa: E402

SX, SZ = lb.SX, lb.SZ
SY = 15
W = "smooth_quartz"
SPAWN = {(x, z) for x in (4, 5) for z in (3, 4)}


def floor(r):
    for x in range(SX):
        for z in range(SZ):
            edge = x in (0, SX - 1) or z in (0, SZ - 1)
            r.set(x, 0, z, "polished_andesite" if edge else "smooth_sandstone")
    for x in range(2, 8):     # the starting type in layer 0 (Barracks.startingBlockTypes)
        r.stair(x, 0, SZ - 1, "north", "bottom", lb.START_TYPE)


def box(r, x0, x1, z0, z1, y0, y1, name=W):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) in SPAWN and y0 <= 8:
                continue
            for y in range(y0, y1 + 1):
                r.set(x, y, z, name)


def dome(r, cx, cz, rad, y0, h, crown=True):
    """A tiered dome: rings shrinking with height (round from above), a gold crown on top."""
    top = y0
    for k in range(h):
        rr = rad * math.cos(k / h * math.pi / 2)
        for x in range(SX):
            for z in range(SZ):
                if (x - cx) ** 2 + (z - cz) ** 2 <= rr * rr + 0.3:
                    r.set(x, y0 + k, z, W)
                    top = y0 + k
    if crown:
        r.set(int(round(cx)), top + 1, int(round(cz)), "gold_block")


def option_a(r):
    """GREAT DOME: hall x 3..9 z 0..2 to y 4; a drum (r 2.6) round (7, 1.8) y 5..6 and a dome y 7..11 + gold crown;
    a 2 x 2 campanile x 0..1 z 0..1 to y 13 with a gold cap; a low west wing x 0..2 z 2..3 to y 3."""
    floor(r)
    box(r, 3, 9, 0, 2, 1, 4)
    box(r, 6, 9, 3, 3, 1, 3)
    dome(r, 7.0, 1.8, 2.7, 5, 2, crown=False)
    dome(r, 7.0, 1.8, 2.7, 7, 5)
    box(r, 0, 1, 0, 1, 1, 13)
    r.set(0, 14, 0, "gold_block")
    box(r, 0, 2, 2, 3, 1, 3)


def option_b(r):
    """CAMPANILE AND TEMPLE: a 3 x 3 tower x 0..2 z 0..2 to y 10, a 1-wide belfry stage to y 13, gold top y 14; a
    temple x 6..9 z 0..5 (walls to y 4) under a gable roof ridged north-south (the gable faces the camera's south),
    ridge y 7; a low colonnade x 3..5 z 0..1 to y 3 between them."""
    floor(r)
    box(r, 0, 2, 0, 2, 1, 10)
    for y in (11, 12):
        for (x, z) in ((0, 0), (2, 0), (0, 2), (2, 2)):
            r.set(x, y, z, W)
    box(r, 0, 2, 0, 2, 13, 13)
    r.set(1, 14, 1, "gold_block")
    box(r, 6, 9, 0, 5, 1, 4)
    for k, (xa, xb) in enumerate(((6, 9), (6, 9), (7, 8))):   # gable: stairs up both sides, ridge on top
        y = 5 + k
        for z in range(0, 6):
            if xa == xb - 1:
                r.set(xa, y, z, W)
                r.set(xb, y, z, W)
            else:
                r.stair(xa + k, y, z, "east", "bottom", "quartz_stairs")
                r.stair(xb - k, y, z, "west", "bottom", "quartz_stairs")
                for x in range(xa + k + 1, xb - k):
                    r.set(x, y, z, W)
    for z in range(0, 6):
        r.set(7, 7, z, "smooth_quartz_slab", type="bottom", waterlogged="false")
        r.set(8, 7, z, "smooth_quartz_slab", type="bottom", waterlogged="false")
    r.set(7, 8, 5, "gold_block")
    box(r, 3, 5, 0, 1, 1, 3)


def option_c(r):
    """HALO ARENA: an oval ring wall (a 4.9 x 3.9 round (4.5, 3.5), 1.6 thick) to y 4 with a flat white top, a south
    gate (x 4..5) and a west gate (z 3..4); a 2 x 2 column x 4..5 z 0..1 to y 12 with a gold sun disc (3 x 3) at y 13
    on the north rim."""
    floor(r)
    for x in range(SX):
        for z in range(SZ):
            d = ((x - 4.5) / 4.9) ** 2 + ((z - 3.5) / 3.9) ** 2
            if 0.45 <= d <= 1.08:
                gate = (x in (4, 5) and z >= 5) or (z in (3, 4) and x <= 1)
                for y in range(1, 5):
                    if gate and y <= 3:
                        continue
                    r.set(x, y, z, W)
    box(r, 4, 5, 0, 1, 1, 12)
    for x in range(3, 7):
        for y in (13, 14):
            if not (x in (3, 6) and y == 14):
                r.set(x, y, 0, "gold_block")


# ---- round 1 (on C, the blind winner 6 / 6 over A 5 / 5.5, B 4.5 / 4, shipped 4 / 4.5): no dominant mass, no capitol
# kinship (A's tiered crown is the donor), the column + offset disc reads as a lollipop / hook, the exit sides walled,
# one value group (78 % light, 0 % dark)
def ring_cells(rmin=0.45, rmax=1.08):
    for x in range(SX):
        for z in range(SZ):
            d = ((x - 4.5) / 4.9) ** 2 + ((z - 3.5) / 3.9) ** 2
            if rmin <= d <= rmax:
                yield x, z


def tiered(r, x0, x1, z0, z1, y0, steps):
    """A square base x0..x1 z0..z1 rising to y0, then rings stepping in by one per tier (steps rows each), gold crown."""
    box(r, x0, x1, z0, z1, 1, y0)
    y = y0
    while x1 - x0 >= 0 and z1 - z0 >= 0:
        for _ in range(steps):
            y += 1
            box(r, x0, x1, z0, z1, y, y)
        x0, x1, z0, z1 = x0 + 1, x1 - 1, z0 + 1, z1 - 1
        if x1 < x0 or z1 < z0:
            break
    r.set((x0 + x1 + 1) // 2 if x1 >= x0 else x0 - 1, y + 1, (z0 + z1 + 1) // 2 if z1 >= z0 else z0 - 1, "gold_block")


def arcade(r, x, z, h):
    """A ring cell: an arch (air y 1..2, a lintel over it) on every other cell, solid to h on the rest."""
    open_ = (x + z) % 2 == 0
    for y in range(1, h + 1):
        if not (open_ and y <= 2):
            r.set(x, y, z, W)


def option_c1(r):
    """HORSESHOE + KEEP: the oval arcaded ring (to y 4) open to the south-west quarter (x <= 4, z >= 4), a 4 x 4
    tiered keep on the north-east (x 6..9, z 0..3) to y 6 then three rings of two rows to a gold crown (~y 13)."""
    floor(r)
    for x, z in ring_cells():
        if x <= 4 and z >= 4:
            continue
        if x >= 6 and z <= 3:
            continue
        arcade(r, x, z, 4)
    tiered(r, 6, 9, 0, 3, 6, 2)


def option_c2(r):
    """HALO + DOME ON THE AXIS: the full oval arcaded ring, high (y 4) on the north / east half, low (y 2, a parapet)
    on the south / west half; a round tiered dome on the north rim over the gate's axis (r 2.6 round (4.5, 0.8),
    clipped to z <= 2) on a drum to y 5, dome y 6..10 and a gold crown; the south gate stays."""
    floor(r)
    for x, z in ring_cells():
        if (x in (4, 5) and z >= 5) or (z in (3, 4) and x <= 1):
            continue
        hi = z <= 3 or x >= 6
        arcade(r, x, z, 4 if hi else 2)
    for x in range(SX):
        for z in range(0, 3):
            dd = (x - 4.5) ** 2 + (z - 0.8) ** 2
            if dd <= 2.7 ** 2:
                box(r, x, x, z, z, 1, 5)
    for k in range(5):
        rr = 2.7 * math.cos(k / 5 * math.pi / 2)
        for x in range(SX):
            for z in range(0, 3):
                if (x - 4.5) ** 2 + (z - 0.8) ** 2 <= rr * rr + 0.3:
                    r.set(x, 6 + k, z, W)
    r.set(4, 11, 1, "gold_block")
    r.set(5, 11, 1, "gold_block")


def build(draw):
    r = lb.Rig(SY)
    draw(r)
    r.settle()
    return r.structure()


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for tag, fn in (("a", option_a), ("b", option_b), ("c", option_c), ("c1", option_c1), ("c2", option_c2)):
        build(fn).write(os.path.join(out, "massing_%s.nbt" % tag))
    print("wrote", out)
