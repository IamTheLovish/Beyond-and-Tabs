#!/usr/bin/env python3
"""Greybox massing options for the Stormcallers capitol (jade_pagoda.nbt), judged blind by two fresh critics before
any detail round (the r12 method: massing first).

Baseline (eyes 41263811904, two critics 4 / 4): from the RTS camera the five roofs, one ring apart, merge into "one
four-sided pyramid of equal-height steps - no overhang, no shadow gap between tiers, no curved eave": a green
ziggurat, not a pagoda. Each option here answers that with a different shape, in the kit's materials only (jade
roofs, red lacquer posts, paper walls, dark oak), no ornament, no halo:
  A  THREE DEEP ROOFS - three wide hipped roofs (lips ring 7 / 5 / 3), each two stair courses deep with upturned
     corners, over tall storeys, and a tall finial: fewer, bigger, separated tiers.
  B  HALL AND TOWER - one broad hipped hall roof (lip ring 7, three courses) and out of it a slender four-roof tower
     (lips ring 3) to y 21: the temple compound's two scales.
  C  FIVE THIN ROOFS - the current tier count, but every roof a thin jade sheet over a dark oak fascia band and
     storeys four high: does a value line between tiers alone break the ziggurat?
Usage: python3 tools/capitol_jade_pagoda_massing.py OUT_DIR  -> OUT_DIR/jade_pagoda_massing_{a,b,c}.nbt
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_structures import Structure  # noqa: E402

SX, SY, SZ = 15, 22, 15
C = 7
JADE, JADE_ST, JADE_SLAB = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"
OAK, OAK_ST = "dark_oak_planks", "dark_oak_stairs"
RED, PAPER, GOLD = "stripped_mangrove_log", "red_stained_glass", "gold_block"
BASE, STEP = "stone_bricks", "polished_andesite"          # the starting types (JadePagoda.startingBlockTypes)
SIDES = ((-1, 0), (1, 0), (0, -1), (0, 1))
INWARD = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}
CORNERS = ((-1, -1), (-1, 1), (1, -1), (1, 1))
DRY = dict(waterlogged="false")


class Canvas:
    def __init__(self):
        self.b = {}

    def set(self, x, y, z, name, **p):
        assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z, name)
        self.b[(x, y, z)] = (name, p)

    def ring(self, k, y, name, **p):
        for d in range(-k, k + 1):
            for (x, z) in ((C - k, C + d), (C + k, C + d), (C + d, C - k), (C + d, C + k)):
                self.set(x, y, z, name, **p)

    def disc(self, k, y, name, **p):
        for dx in range(-k, k + 1):
            for dz in range(-k, k + 1):
                self.set(C + dx, y, C + dz, name, **p)

    def structure(self):
        s = Structure(SX, SY, SZ)
        for x in range(SX):
            for y in range(SY):
                for z in range(SZ):
                    n, p = self.b.get((x, y, z), ("air", {}))
                    s.set(x, y, z, n, **p)
        return s


def stair_ring(c, k, y, name=JADE_ST, half="bottom"):
    """A ring of stairs at half-width k, low side out (a roof course)."""
    for (ox, oz) in SIDES:
        for t in range(-k + 1, k):
            x, z = C + k * ox - t * oz, C + k * oz + t * ox
            c.set(x, y, z, name, facing=INWARD[(ox, oz)], half=half, shape="straight", **DRY)


def upturn(c, k, y, lift=1):
    """The upturned eave corners: a gold block at each corner of ring k, a jade slab tip lifted on it."""
    for (sx, sz) in CORNERS:
        x, z = C + sx * k, C + sz * k
        c.set(x, y, z, GOLD)
        for i in range(1, lift):
            c.set(x, y + i, z, JADE)
        c.set(x, y + lift, z, JADE_SLAB, type="bottom", **DRY)


def hip(c, lip, y, courses, under=OAK):
    """A hipped roof: `courses` stair rings from ring lip at y stepping in one ring and up one row each, the rest of
    the top filled jade; a dark oak soffit under the lip ring."""
    for i in range(courses):
        stair_ring(c, lip - i, y + i)
        c.disc(lip - i - 1, y + i, JADE)
    c.ring(lip, y - 1, under) if under else None
    upturn(c, lip, y, lift=2 if courses >= 2 else 1)


def storey(c, k, y0, y1, posts=True):
    for y in range(y0, y1 + 1):
        for d in range(-k + 1, k):
            for (x, z) in ((C - k, C + d), (C + k, C + d), (C + d, C - k), (C + d, C + k)):
                c.set(x, y, z, PAPER if (d + k) % 2 == 0 and y < y1 else OAK)
        if posts:
            for (sx, sz) in CORNERS:
                c.set(C + sx * k, y, C + sz * k, RED, axis="y")
    for y in range(y0, y1 + 1):                  # solid core so nothing reads hollow through the paper
        c.disc(k - 1, y, OAK)


def base(c, terrace=6):
    c.disc(7, 0, BASE)
    c.disc(terrace, 1, STEP)


def finial(c, y0, top=21):
    c.set(C, y0, C, GOLD)
    for y in range(y0 + 1, top + 1):
        c.set(C, y, C, "lightning_rod", facing="up", powered="false", **DRY)


def option_a():
    """Three deep roofs: lips ring 7 / 5 / 3, two courses each, storeys 4-5 tall, a tall finial."""
    c = Canvas()
    base(c)
    storey(c, 5, 2, 5)
    hip(c, 7, 6, 2)                       # y6-7, top at ring 5
    storey(c, 3, 8, 11)
    hip(c, 5, 12, 2)                      # y12-13
    storey(c, 2, 14, 15)
    hip(c, 3, 16, 2)                      # y16-17
    c.disc(1, 18, JADE)
    finial(c, 18)
    return c


def option_b():
    """Hall and tower: one broad hall roof (lip 7, three courses, y6-8), a slender tower ring 2 with four small roofs
    (lip 3) to y 21."""
    c = Canvas()
    base(c)
    storey(c, 5, 2, 5)
    hip(c, 7, 6, 3)                       # y6-8, top at ring 4
    for i, y in enumerate((9, 12, 15)):
        storey(c, 2, y, y + 1)
        hip(c, 3, y + 2, 1)
    storey(c, 1, 18, 18, posts=False)
    hip(c, 2, 19, 1)
    finial(c, 20)
    return c


def option_c():
    """Five thin roofs: lips ring 7..3 each a single jade sheet
    over a dark oak fascia, storeys 3 / 3 / 2 / 2 / 2 tall."""
    c = Canvas()
    base(c)
    y = 2
    for lip, h in ((7, 3), (6, 3), (5, 2), (4, 2), (3, 2)):
        top = y + h - 1
        storey(c, max(lip - 2, 1), y, top)
        y = top + 1
        c.ring(lip, y - 1, OAK)           # the dark fascia line under each sheet
        stair_ring(c, lip, y)
        c.disc(lip - 1, y, JADE)
        upturn(c, lip, y)
        y += 1
    finial(c, y)
    return c


# ---- massing round 2 (blind critics on A/B/C, eyes 41275632354: split A 1st / B 1st, both beat the shipped build,
# none reached 7 on RTS read). Shared faults: each roof "a stair of equal one-block steps" (read as terraces, not one
# slope); no crown that reads at normal zoom; storey gaps too thin; straight eaves. Round 2 keeps A and B's outlines
# and changes only those: half-block roof slopes (slab, stair, slab, ...), corners lifted two with the cells beside
# them lifted half (a curve), taller storeys, and a crown sized for the far read. ----


def slope(c, lip, y, rings, under=OAK):
    """A roof whose plane climbs half a block per ring: ring lip a bottom slab at y, then a stair (half to full), then
    a bottom slab one up, ... - `rings` rings in all, the inside filled jade to the top course; dark oak soffit."""
    for i in range(rings):
        k = lip - i
        yy = y + i // 2
        if i % 2 == 0:
            c.ring(k, yy, JADE_SLAB, type="bottom", **DRY)
        else:
            stair_ring(c, k, yy)
        for j in range(y, yy):
            c.ring(k, j, JADE)
    top = y + (rings - 1) // 2
    c.disc(lip - rings, top, JADE)
    for j in range(y, top):
        c.disc(lip - rings, j, JADE)
    if under:
        c.ring(lip, y - 1, under)
    # the curve: corners up two (gold, jade, a slab tip), the cell beside each corner up half
    for (sx, sz) in CORNERS:
        x, z = C + sx * lip, C + sz * lip
        c.set(x, y, z, GOLD)
        c.set(x, y + 1, z, JADE)
        c.set(x, y + 2, z, JADE_SLAB, type="bottom", **DRY)
        for (ax, az) in ((x - sx, z), (x, z - sz)):
            c.set(ax, y, az, JADE)
            c.set(ax, y + 1, az, JADE_SLAB, type="bottom", **DRY)
    return top


def crown(c, y):
    """A crown for the far read: a 3 x 3 gold-trimmed jade cap, a two-high gold boss, a rod; rods on its corners."""
    c.disc(1, y, JADE)
    for (sx, sz) in CORNERS:
        c.set(C + sx, y, C + sz, GOLD)
        c.set(C + sx, y + 1, C + sz, "lightning_rod", facing="up", powered="false", **DRY)
    c.set(C, y + 1, C, GOLD)
    c.set(C, y + 2, C, GOLD)
    c.set(C, y + 3, C, "lightning_rod", facing="up", powered="false", **DRY)


def option_a2():
    """A, round 2: three roofs (lips 7 / 5 / 3) with half-block slopes and curved corners, storeys 4 tall, a crown."""
    c = Canvas()
    base(c)
    storey(c, 5, 2, 5)
    t = slope(c, 7, 6, 4)                 # y6-7
    storey(c, 3, t + 1, t + 4)            # y8-11
    t = slope(c, 5, t + 5, 3)             # y12-13
    storey(c, 2, t + 1, t + 2)            # y14-15
    t = slope(c, 3, t + 3, 2)             # y16
    crown(c, t + 1)                        # y17-20
    return c


def option_b2():
    """B, round 2: the hall skirt (lip 7, half-block slope, a deep soffit) and a tower with two widely spaced roofs
    (lips 4 and 3) instead of four tight ones, storeys 3-4 tall, a crown."""
    c = Canvas()
    base(c)
    storey(c, 5, 2, 4)
    t = slope(c, 7, 5, 4)                 # y5-6
    storey(c, 2, t + 1, t + 4)            # y7-10
    t = slope(c, 4, t + 5, 3)             # y11-12
    storey(c, 1, t + 1, t + 3)            # y13-15
    t = slope(c, 3, t + 4, 2)             # y16
    crown(c, t + 1)                        # y17-20
    return c


if __name__ == "__main__":
    out = sys.argv[1]
    opts = (("a", option_a), ("b", option_b), ("c", option_c), ("a2", option_a2), ("b2", option_b2))
    for tag, fn in opts:
        c = fn()
        print(tag, "blocks", sum(1 for v in c.b.values() if v[0] != "air"),
              "top", max(q[1] for q in c.b))
        c.structure().write(os.path.join(out, "jade_pagoda_massing_%s.nbt" % tag))
