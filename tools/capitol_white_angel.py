#!/usr/bin/env python3
"""The White Angel (Sunforged) capitol as a PALACE THAT GROWS - six levels in one reserved footprint.

lovish, Oct 20 2026: the buildings "look pretty bad", capitols "can be a lot more intricate and detailed, can be sized
up even more"; his reference (a white classical palace: a domed rotunda on the axis, a great curved colonnade embracing
a courtyard, towers capping the colonnade, terraced gardens, strict symmetry, big clean white volumes with the detail on
cornices, capitals, dome and gate); "these buildings' upgrades make it more intricate and detailed so it starts off small
and gets bigger and bigger"; and (19:57) a building has SIX levels - placed at L1, two upgrades at tech 1 (L2, L3), two
more once tech 2 is unlocked (L4, L5), one at tech 3 (L6). Inspiration only, never traced.

THE GROWTH (one 29 x 29 footprint reserved at placement, the same origin for every level; a level only ADDS blocks to
the one before or replaces some - plants on paved-over ground aside - so an upgrade grows out and up inside the
reservation):
  - L1  the TEMPLE: a round temple on a stepped podium - twelve columns with gold capitals round a quartz cella with a
        door on each axis, an entablature, a frieze and a low stepped roof. Its plaza (lapis cross, gold sun at the
        heart) ends in a garden edge. Complete on its own.
  - L2  the DRUM: a balustrade with candles round the roof terrace, a windowed drum, its cornice and the first two
        tiers of the dome.
  - L3  the DOME: the hemisphere closes under a lantern cupola and the gold sun; lantern posts on the plaza; the plaza
        widens to a paved ring with a new garden edge.
  - L4  the GATES: four gate pavilions rise on the axes at the reservation's edge (arched, pedimented, lit), the inner
        ring of the colonnade's columns stands between them, the courtyard is paved out to it.
  - L5  the COLONNADE: the outer ring of columns, the walk's roof, cornices and balustrades - the courtyard closed by a
        ring of paired columns (the reference's embracing curve, a full ring because a base grows in every direction);
        the garden moves out to the reservation's rim.
  - L6  the CROWN: towers where the colonnade turns on the diagonals (belfries, little domes, gold spires), the dome's
        gold ribs and crown, angels on the colonnade walk, terraced gardens in the corners, parterres in the courtyard.

THE KIT (capitol_town_centre.py's Sunforged kit): white stone and quartz, gold trim, lapis on the axes, blue glass,
lanterns and candles, tiered domes, the angel. Detail where the reference puts it - capitals, cornices, the dome, the
gates - and the big volumes clean, so the whole reads as one white mass with a rhythm of columns.

RULES (check()): the box is 29 x 27 x 29 at every level; each level keeps every non-plant cell of the one before; no
water, lava, gravity blocks or redstone; nothing pops off (_SunRig.check_support); the centre spawn cell walks out
through the axis doors. Not wired into the game: the reserved-footprint placement and the level switch are Java work
for after lovish's verdict.

Usage: python3 tools/capitol_white_angel.py OUT_DIR   -> OUT_DIR/white_angel_capitol_l1.nbt .. _l6.nbt
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from capitol_town_centre import Cap, LANTERN, FLOWERS, hsh   # noqa: E402

R = 14          # the reserved footprint: 29 x 29
H = 27          # y0 .. y26
LEVELS = 6
QUIET_FLOWERS = ("lily_of_the_valley", "oxeye_daisy", "azure_bluet")    # white and pale: no purple pepper
PLANTS = set(FLOWERS) | set(QUIET_FLOWERS)

# ----------------------------------------------------------------------------------------------- round geometry


def dist(dx, dz):
    return math.hypot(dx, dz)


def ring(r):
    """The cells of a circle of radius r (one cell wide), inside the footprint."""
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            if abs(dist(dx, dz) - r) < 0.5:
                yield dx, dz


def disc(r, r0=-1.0):
    """The cells within radius r and outside r0."""
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            d = dist(dx, dz)
            if r0 + 0.5 <= d < r + 0.5:
                yield dx, dz


def toward_centre(dx, dz):
    """The facing that points at the centre from (dx, dz): a stair with it climbs inward."""
    if abs(dx) >= abs(dz):
        return "west" if dx > 0 else "east"
    return "north" if dz > 0 else "south"


OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def away(dx, dz):
    return OPP[toward_centre(dx, dz)]


def on_circle(r, n, phase=0.0):
    out = []
    for i in range(n):
        a = phase + 2 * math.pi * i / n
        p = (int(round(r * math.cos(a))), int(round(r * math.sin(a))))
        if p not in out:
            out.append(p)
    return out


AXES = [(0, 1), (0, -1), (1, 0), (-1, 0)]
DIAGS = [(1, 1), (1, -1), (-1, 1), (-1, -1)]


def is_axis(dx, dz, w=0):
    return abs(dx) <= w or abs(dz) <= w


def garden(c, cells, y=0):
    """The kit's feathered edge: grass, moss and calcite flags with flowers - no hard kerb."""
    for dx, dz in cells:
        h = hsh(dx, dz)
        kind = h % 9                     # mostly grass, moss in drifts, a rare flag: calm from the RTS camera
        c.set(dx, y, dz, "calcite" if kind == 0 else "moss_block" if kind in (1, 2, 3) else "grass_block",
              **({"snowy": "false"} if kind > 3 else {}))
        # no flowers: scattered single flowers read as coloured pepper from the RTS camera (lovish Oct 20)


def stair(c, dx, y, dz, facing, half="bottom", name="smooth_quartz_stairs"):
    c.stair(dx, y, dz, facing, half, name)


# ======================================================================================================= levels
def l1_temple(c):
    # y0: the plaza - diorite, a calcite cross with lapis ends to a gold sun, the garden edge on r8
    for dx, dz in disc(7):
        c.set(dx, 0, dz, "polished_diorite")
        if is_axis(dx, dz):
            c.set(dx, 0, dz, "lapis_block" if dist(dx, dz) > 6.4 else "calcite")
    c.set(0, 0, 0, "gold_block")
    garden(c, list(ring(8)))
    # y1-y2: the podium - a quartz step all round on r7 and r6, the stylobate inside, the cross and sun on its floor
    for dx, dz in disc(6):
        c.set(dx, 1, dz, "smooth_quartz")
    for dx, dz in ring(7):
        stair(c, dx, 1, dz, toward_centre(dx, dz), name="quartz_stairs")
    for dx, dz in disc(5):
        c.set(dx, 2, dz, "calcite" if is_axis(dx, dz) else "smooth_quartz")
    c.set(0, 2, 0, "gold_block")
    for dx, dz in ring(6):
        stair(c, dx, 2, dz, toward_centre(dx, dz), name="quartz_stairs")
    # the peristyle on r5: twelve columns - chiseled base, quartz-pillar shaft, gold capital
    for dx, dz in on_circle(5, 12, math.pi / 12):
        c.set(dx, 3, dz, "chiseled_quartz_block")
        for y in range(4, 9):
            c.set(dx, y, dz, "quartz_pillar", axis="y")
        c.set(dx, 9, dz, "gold_block")
    # the cella on r3: quartz brick, blue windows on the diagonals, a door (1 x 3) on each axis under a lintel
    for dx, dz in ring(3):
        for y in range(3, 10):
            if is_axis(dx, dz) and y <= 5:
                continue
            win = not is_axis(dx, dz, 1) and y in (6, 7)
            c.set(dx, y, dz, "blue_stained_glass" if win else "quartz_bricks")
    for (ox, oz) in AXES:
        stair(c, 3 * ox, 6, 3 * oz, away(3 * ox, 3 * oz), "top")
        c.set(3 * ox, 7, 3 * oz, "chiseled_quartz_block")
    # y10: the architrave (r2..r5) and the cornice of upside-down stairs on r6; y11: the frieze on r5, the roof inside
    for dx, dz in disc(5):
        c.set(dx, 10, dz, "smooth_quartz")
    for dx, dz in ring(6):
        stair(c, dx, 10, dz, toward_centre(dx, dz), "top")
    for dx, dz in ring(5):
        c.set(dx, 11, dz, "lapis_block" if (dx + dz) % 2 else "chiseled_quartz_block")
    for dx, dz in disc(4):
        c.set(dx, 11, dz, "smooth_quartz")
    # the low roof: a stair ring on r4 climbing to a slab-topped disc, a gold boss
    for dx, dz in ring(4):
        stair(c, dx, 12, dz, toward_centre(dx, dz))
    for dx, dz in disc(3):
        c.set(dx, 12, dz, "smooth_quartz")
    c.set(0, 13, 0, "gold_block")
    # two lanterns at each door, on the podium's top step
    for (ox, oz) in AXES:
        for s in (-1, 1):
            x, z = 5 * ox - 2 * s * oz, 5 * oz + 2 * s * ox
            c.set(x, 3, z, "lantern", **LANTERN)


# a hemisphere of radius ~4.5 on the r4 drum: 4.4, 4.0, 3.6 (rounded 4, 4, 4), 3.0, 2.1, 1 - vertical at the foot,
# closing fast at the top, so it reads round rather than stepped
DOME_TIERS = ((17, 4), (18, 4), (19, 4), (20, 3), (21, 2), (22, 1))


def l2_drum(c):
    # the balustrade round the roof terrace on r6 (walls, chiseled posts with candles)
    for dx, dz in ring(6):
        if (dx * 7 + dz * 3) % 2 == 0:
            c.set(dx, 11, dz, "diorite_wall")
    for dx, dz in on_circle(6, 8, math.pi / 8):
        c.set(dx, 11, dz, "chiseled_quartz_block")
        c.set(dx, 12, dz, "white_candle", candles="3", lit="true", waterlogged="false")
    # the drum on r4, y12-15 (replacing the low roof's stairs): pilasters, tall blue windows, gold on the pilasters
    pils = on_circle(4, 8)
    for dx, dz in ring(4):
        pil = (dx, dz) in pils
        for y in range(12, 16):
            win = not pil and y in (13, 14)
            if pil:
                c.set(dx, y, dz, "quartz_pillar", axis="y")
            else:
                c.set(dx, y, dz, "blue_stained_glass" if win else "quartz_bricks")
        if pil:
            c.set(dx, 15, dz, "gold_block")
    for dx, dz in disc(3):
        c.set(dx, 13, dz, "smooth_quartz")       # the drum's floor over the cella's roof (the L1 gold boss's cell)
        c.set(dx, 15, dz, "smooth_quartz")
    c.set(0, 15, 0, "ochre_froglight", axis="y")
    # the drum's cornice y16 on r5, then the dome's first two tiers
    for dx, dz in ring(5):
        stair(c, dx, 16, dz, toward_centre(dx, dz), "top")
    for dx, dz in disc(4):
        c.set(dx, 16, dz, "smooth_quartz")
    for y, r in DOME_TIERS[:2]:
        for dx, dz in disc(r - 1):
            c.set(dx, y, dz, "smooth_quartz")
        for dx, dz in ring(r):
            stair(c, dx, y, dz, toward_centre(dx, dz))
    c.set(0, 19, 0, "gold_block")      # a gold boss on the half-built dome
    c.set(0, 20, 0, "end_rod", facing="up")




def l3_dome(c):
    for y, r in DOME_TIERS[2:]:
        for dx, dz in disc(r - 1):
            c.set(dx, y, dz, "smooth_quartz")
        for dx, dz in ring(r):
            stair(c, dx, y, dz, toward_centre(dx, dz))
    c.set(0, 20, 0, "smooth_quartz")   # L2's spike gives way to the closing dome
    # the lantern cupola y23-24 on the dome's crown and the gold sun
    for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        c.set(dx, 23, dz, "quartz_pillar", axis="y")
    for dx, dz in AXES:
        c.set(dx, 23, dz, "blue_stained_glass")
    c.set(0, 23, 0, "ochre_froglight", axis="y")
    for dx, dz in ring(1):
        stair(c, dx, 24, dz, toward_centre(dx, dz))
    c.set(0, 24, 0, "gold_block")
    c.set(0, 25, 0, "gold_block")
    c.set(0, 26, 0, "end_rod", facing="up")
    # the plaza widens: paving on r8-9 (the old garden edge), a new garden edge on r10, lantern posts on the diagonals
    for dx, dz in disc(9, 7):
        c.set(dx, 0, dz, "lapis_block" if is_axis(dx, dz) else "polished_diorite")
    garden(c, list(ring(10)))
    for sx, sz in DIAGS:
        x, z = sx * 6, sz * 6
        c.set(x, 1, z, "diorite_wall")
        c.set(x, 2, z, "diorite_wall")
        c.set(x, 3, z, "lantern", **LANTERN)


COL_IN, COL_OUT = 11, 13
# the colonnade is kept LOW (columns y2-4, roof y6) so from the RTS camera the courtyard and the temple read over it:
# the rotunda stays the tallest thing on the site, the ring is its frame (the reference's colonnade is a low arm)
COL_TOP = 5     # the gold capitals' y; the walk's roof is COL_TOP + 1, its balustrade COL_TOP + 2


def _gate(c, ox, oz):
    """A gate pavilion on axis (ox, oz), cells a = 10..14 out and t = -3..3 across: piers, a 3 x 3 arch, a frieze, a
    cornice and a low pediment with a gold apex and a lantern - a little taller than the colonnade, well under the
    temple."""
    def put(a, t, y, name, **p):
        c.set(a * ox - t * oz, y, a * oz + t * ox, name, **p)
    out_f = {(0, 1): "south", (0, -1): "north", (1, 0): "east", (-1, 0): "west"}[(ox, oz)]
    side_f = {(0, 1): "east", (0, -1): "west", (1, 0): "north", (-1, 0): "south"}[(ox, oz)]   # +t direction

    def st(a, t, y, facing, half="bottom"):
        put(a, t, y, "smooth_quartz_stairs", facing=facing, half=half, shape="straight", waterlogged="false")
    for a in range(10, 15):
        for t in range(-3, 4):
            put(a, t, 0, "calcite" if t == 0 else "smooth_stone")
    put(14, 0, 0, "lapis_block")
    for a in (11, 12, 13):
        for t in (-3, -2, 2, 3):
            for y in range(1, 7):
                corner = a in (11, 13) and abs(t) == 3
                put(a, t, y, "quartz_pillar" if corner else "quartz_bricks", **({"axis": "y"} if corner else {}))
        for s in (-1, 1):                                   # the arch: springers at y3, opening 3 wide x 3 high
            st(a, s, 3, side_f if s < 0 else OPP[side_f], "top")
            put(a, s, 4, "smooth_quartz")
        put(a, 0, 4, "smooth_quartz")
    for a in (11, 13):
        put(a, 0, 4, "gold_block")                          # the keystones
        for s in (-1, 1):
            put(a, 3 * s, 2, "blue_stained_glass")          # slit lancets in the corner piers
            put(a, 3 * s, 3, "blue_stained_glass")
    for s in (-1, 1):
        for a in (10, 14):
            put(a, 2 * s, 1, "diorite_wall")
            put(a, 2 * s, 2, "lantern", **LANTERN)
    for a in (11, 12, 13):                                   # the frieze y5
        for t in range(-3, 4):
            put(a, t, 5, "lapis_block" if t == 0 else "chiseled_quartz_block" if abs(t) == 2 else "smooth_quartz")
    for t in range(-3, 4):                                   # the cornice y6, overhanging both faces
        st(14, t, 6, OPP[out_f], "top")
        st(10, t, 6, out_f, "top")
        for a in (11, 12, 13):
            put(a, t, 6, "smooth_quartz")
    for k, y in ((3, 7), (2, 8), (1, 9)):                    # the pediment y7-9
        for a in (11, 12, 13):
            for s in (-1, 1):
                st(a, k * s, y, OPP[side_f] if s < 0 else side_f)
            for kk in range(-(k - 1), k):
                put(a, kk, y, "smooth_quartz")
    put(12, 0, 10, "gold_block")
    put(12, 0, 11, "lantern", **LANTERN)


def _columns(c, r):
    for dx, dz in on_circle(r, 32, math.pi / 32):
        if is_axis(dx, dz, 3):
            continue
        c.set(dx, 1, dz, "chiseled_quartz_block")
        for y in range(2, COL_TOP):
            c.set(dx, y, dz, "quartz_pillar", axis="y")
        c.set(dx, COL_TOP, dz, "gold_block")


def l4_gates(c):
    for dx, dz in disc(COL_IN, 9):          # the courtyard paved out to the inner columns (over L3's garden ring)
        c.set(dx, 0, dz, "calcite" if is_axis(dx, dz) else "polished_diorite")
    for dx, dz in disc(COL_OUT, COL_IN):    # the walk's floor
        c.set(dx, 0, dz, "smooth_stone")
    for ox, oz in AXES:
        _gate(c, ox, oz)
    _columns(c, COL_IN)
    garden(c, [p for p in ring(COL_OUT + 1) if not is_axis(*p, 3)])


def l5_colonnade(c):
    _columns(c, COL_OUT)
    roof = COL_TOP + 1
    for dx, dz in disc(COL_OUT, COL_IN - 1):            # the walk's roof
        if not is_axis(dx, dz, 3):
            c.set(dx, roof, dz, "smooth_quartz")
    for dx, dz in ring(COL_OUT + 1):                     # cornices on both rims
        if not is_axis(dx, dz, 3):
            stair(c, dx, roof, dz, toward_centre(dx, dz), "top")
    for dx, dz in ring(COL_IN - 1):
        if not is_axis(dx, dz, 3):
            stair(c, dx, roof, dz, away(dx, dz), "top")
    for r in (COL_IN, COL_OUT):                          # balustrades on both edges of the walk
        for dx, dz in ring(r):
            if not is_axis(dx, dz, 4) and (dx * 5 + dz * 11) % 3 != 0:
                c.set(dx, roof + 1, dz, "diorite_wall")
    rim = [(dx, dz) for dx in range(-R, R + 1) for dz in range(-R, R + 1)
           if dist(dx, dz) >= COL_OUT + 1.5 and not is_axis(dx, dz, 3)]
    garden(c, rim)


TOWERS = [(sx * 9, sz * 9) for sx, sz in DIAGS]
TOWER_TOP = 10      # the shaft's last course; belfry above it, then the cap - the spire ends at y18, under the dome


def _tower(c, cx, cz):
    cells = [(cx + i, cz + j) for i in (-1, 0, 1) for j in (-1, 0, 1)]
    corner = {p for p in cells if abs(p[0] - cx) == 1 and abs(p[1] - cz) == 1}
    T = TOWER_TOP
    for x, z in cells:
        c.set(x, 0, z, "smooth_stone")
        for y in range(1, T + 1):
            c.set(x, y, z, "quartz_pillar" if (x, z) in corner else "quartz_bricks",
                  **({"axis": "y"} if (x, z) in corner else {}))
        c.set(x, T + 1, z, "smooth_quartz")
    for (fx, fz) in AXES:                                  # lancets and the gold band on the four faces
        for y in (3, 4, 8, 9):
            c.set(cx + fx, y, cz + fz, "blue_stained_glass")
        c.set(cx + fx, 6, cz + fz, "gold_block")
    for x, z in corner:                                    # the open belfry over the shaft
        for y in (T + 2, T + 3):
            c.set(x, y, z, "quartz_pillar", axis="y")
    c.set(cx, T + 2, cz, "lantern", **LANTERN)
    for x, z in cells:
        if (x, z) not in corner and (x, z) != (cx, cz):
            f = "west" if x > cx else "east" if x < cx else "north" if z > cz else "south"
            stair(c, x, T + 3, z, f, "top")
        c.set(x, T + 4, z, "smooth_quartz")
    for x, z in cells:                                     # the little dome and the gold spire
        if (x, z) != (cx, cz):
            f = ("west" if x > cx else "east") if x != cx else ("north" if z > cz else "south")
            stair(c, x, T + 5, z, f)
    c.set(cx, T + 5, cz, "smooth_quartz")
    c.set(cx, T + 6, cz, "gold_block")
    c.set(cx, T + 7, cz, "diorite_wall")
    c.set(cx, T + 8, cz, "end_rod", facing="up")


def l6_crown(c):
    for cx, cz in TOWERS:
        _tower(c, cx, cz)
    for y, r in DOME_TIERS:                                # four gold ribs on the axes (eight read as stripes)
        for dx, dz in ring(r):
            if dx == 0 or dz == 0:
                c.set(dx, y, dz, "gold_block")
    for dx, dz in ring(1):
        c.set(dx, 24, dz, "gold_block")
    walk = COL_TOP + 2
    for i in range(8):                                     # angels on the colonnade walk, looking out
        a = math.pi / 8 + i * math.pi / 4
        dx, dz = int(round(12 * math.cos(a))), int(round(12 * math.sin(a)))
        c.set(dx, walk, dz, "chiseled_quartz_block")
        c.set(dx, walk + 1, dz, "quartz_pillar", axis="y")
        c.set(dx, walk + 2, dz, "calcite")
        c.set(dx, walk + 3, dz, "gold_block")
        c.set(dx, walk + 4, dz, "lantern", **LANTERN)
        o = away(dx, dz)
        for s in (-1, 1):
            if o in ("north", "south"):
                stair(c, dx + s, walk + 1, dz, o, "top", "quartz_stairs")
            else:
                stair(c, dx, walk + 1, dz + s, o, "top", "quartz_stairs")
    for sx, sz in DIAGS:                                   # terraced gardens in the corners, a flowering tree on top
        for i in range(4):
            for j in range(4):
                x, z = sx * (R - i), sz * (R - j)
                if dist(x, z) < COL_OUT + 1.5:
                    continue
                top = 2 if i + j <= 2 else 1
                for y in range(1, top + 1):
                    if y < top:
                        c.set(x, y, z, "moss_block")
                    else:
                        c.set(x, y, z, "grass_block", snowy="false")
                if i + j == 3:
                    c.set(x, top + 1, z, "azalea_leaves", persistent="true", distance="1", waterlogged="false")
        tx, tz = sx * R, sz * R
        for y in (3, 4, 5):
            c.set(tx, y, tz, "birch_log", axis="y")
        for (lx, lz, ly) in ((0, 0, 6), (-sx, 0, 5), (0, -sz, 5), (-sx, -sz, 5), (-sx, 0, 6), (0, -sz, 6), (0, 0, 7)):
            c.set(tx + lx, ly, tz + lz, "flowering_azalea_leaves", persistent="true", distance="1",
                  waterlogged="false")
    for sx, sz in DIAGS:                                   # parterres in the courtyard's quadrants: clipped hedges
        # three cells, clear of the towers (a hedge beside a tower and the inner columns boxed a cell in - the pocket
        # audit's catch)
        for (i, j) in ((6, 7), (7, 6), (7, 7)):
            x, z = sx * i, sz * j
            if not (8.5 <= dist(x, z) < 10.5):
                continue
            c.set(x, 0, z, "grass_block", snowy="false")
            c.set(x, 1, z, "azalea_leaves", persistent="true", distance="1", waterlogged="false")


STEPS = (l1_temple, l2_drum, l3_dome, l4_gates, l5_colonnade, l6_crown)


def level(n):
    """The capitol at level n (1..6) as a settled _SunRig."""
    assert 1 <= n <= LEVELS, n
    c = Cap(R, H)
    for step in STEPS[:n]:
        step(c)
    b = c.rig.b
    for (x, y, z), (name, _) in list(b.items()):            # a flower whose ground was paved over goes
        if name in PLANTS and b.get((x, y - 1, z), ("air",))[0] not in ("grass_block", "moss_block"):
            del b[(x, y, z)]
    c.rig.bury_grass()
    c.rig.settle()
    c.rig.check_support("white angel capitol L%d" % n)
    return c.rig


FORBIDDEN = ("water", "lava", "sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater", "comparator",
             "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "scaffolding")


def check(rigs):
    prev = None
    counts = []
    for n, rig in enumerate(rigs, 1):
        assert rig.size == (2 * R + 1, H, 2 * R + 1), rig.size
        names = {p: v[0] for p, v in rig.b.items()}
        bad = sorted({nm for nm in names.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)})
        assert not bad, "L%d forbidden: %s" % (n, bad)
        if prev is not None:
            lost = [p for p, nm in prev.items() if p not in names and nm not in PLANTS]
            assert not lost, "L%d empties %d cells of L%d, e.g. %s" % (n, len(lost), n - 1, lost[:5])
        assert names.get((R, 2, R)) and (R, 3, R) not in names and (R, 4, R) not in names, \
            "L%d: the centre spawn cell (y3) must stand on the floor with two cells of air" % n
        counts.append(len(names))
        prev = names
    return counts


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp"
    rigs = [level(n) for n in range(1, LEVELS + 1)]
    print("blocks per level:", check(rigs))
    for n, rig in enumerate(rigs, 1):
        path = os.path.join(out, "white_angel_capitol_l%d.nbt" % n)
        rig.structure().write(path)
        print("wrote", path)
