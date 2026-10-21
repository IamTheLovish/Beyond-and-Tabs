#!/usr/bin/env python3
"""The Sunforged (White Angel) capitol, the Town Centre, redrawn as a TEMPLE OF THE SUN - the centrepiece of the base.

lovish, Oct 19 2026: capitols "can be a lot bigger than the current capital size, a lot taller, a lot more detailed",
"I want it to be the center piece of the base", "don't break the game"; the capitol is what "everything connects" to.
Detailed means fine-grained: stairs, slabs, walls, candles, lanterns - not big chunky blocks.

THE SUNFORGED STYLE KIT (every Sunforged building follows it; the capitol is its fullest expression):
  - main blocks: white stone and quartz (smooth / bricks / pillar / chiseled quartz, calcite, polished diorite);
  - trim: gold (capitals, bands, the sun boss), lapis on the axes;
  - accent: BLUE GLASS (blue_stained_glass windows and lancets) and blue banners;
  - light: lanterns (warm points on walls / pedestals), candles on balustrade posts, an ochre froglight heart;
  - silhouette motif: TIERED DOMES - rings of stairs climbing inward, a gold sun at the crown;
  - family figure: the angel (the Angel Fountain extractor's), with a light held over its head;
  - edge: a garden that feathers into the ground (grass, moss, calcite flags, flowers), the same on every side.

THE DESIGN (inspiration only, never traced: ref 24 the round colonnaded temple with statues in a forest, ref 41 the
pale castle with spire clusters, ref 79 the white city in ringed tiers, and the classical order as a rule set):
  - a Greek-cross temple, the same on all four sides (players build in every direction): a stylobate with a moulded
    stair rim and steps up on each axis, a quartz-and-lapis cross on the floor running in to a gold sun at the heart;
  - the order on ring 4: smooth quartz base -> quartz-pillar shaft -> GOLD capital -> the entablature (a cornice of
    upside-down stairs all round); two columns per side frame a portico, chiseled corner piers carry blue lancets;
  - the cella on ring 2: quartz brick, blue windows, a door on every axis under a stepped lintel - the spawn cell
    (the centre, ProductionBuilding.getIndoorSpawnPoint) walks straight out through any of them;
  - the roof in ringed tiers (ref 79): a balustrade of slab rails and candle posts round a slab terrace, a drum of
    pillars and blue glass, two stair tiers of dome and the gold sun on top; blue banners on the corner pinnacles,
    a lantern over each portico, four glowing finials on the axes;
  - an angel on each plaza corner (the Angel Fountain in miniature) holding up a lantern, wings over the garden;
  - the plaza does not stop at a kerb: its outer ring is a garden of grass, moss and scattered calcite flags with
    flowers, so whichever way the base grows the capitol's ground runs on into it.

WHY THIS SIZE: build_grand() (15 x 20 x 15) is the capitol the game ships (Oct 19, the owner's "a lot bigger, a lot
taller"); tools/gen_capitol_details.py writes it. What it needed in the Java: the box in the GameTest
capitol_details_keep_box_build_time_and_anchors (BiomeBaseGameTests), the Town Centre's glint spots
(BuildingAmbienceClientEvents), the per-block build pacing timed by buildTimeBlocks (BuildingPlacement - the capitol laid
at a readied match start is command-placed and used to take longer for every detail block), and the wildlife test's
pad and animals moved outside the wider footprint (SkirmishGameTests). Everything else measures the capitol itself
(BuildingPlacement corners/centre, PlayerServerEvents.getBuildingOriginPos, StartAreaClearing.capitolSpan,
StartPhase.clearSpot, MapClient). build() (RoN's 11 x 9 x 11 box) is kept as the compact fallback.

RULES (asserted in check()): RoN's box; the starting types (TownCentre.startingBlockTypes: stone_brick_stairs,
grass_block, polished_andesite_stairs) in layer 0; the build time is TownCentre.buildTimeBlocks = 305 whatever the
block count (BuildingPlacement.tickFlowConstruction); nothing pops off (_SunRig.check_support); no pockets and the
centre spawn walks out (tools/audit_structure_pockets.py); no water, lava, gravity blocks or redstone; under 2000
blocks. No campfires: the plaza's lights are lanterns (a lit campfire burns
units that stand in it, and the plaza is walked on).

Usage: python3 tools/capitol_town_centre.py OUT.nbt [--grand]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

LANTERN = dict(hanging="false", waterlogged="false")
O4 = {"south": (0, 1), "west": (-1, 0), "north": (0, -1), "east": (1, 0)}
FACE = {v: k for k, v in O4.items()}
CORNERS = [(-1, -1), (-1, 1), (1, -1), (1, 1)]
CORNER_ROT = {(-1, 1): "2", (-1, -1): "6", (1, -1): "10", (1, 1): "14"}
FLOWERS = ("cornflower", "lily_of_the_valley", "azure_bluet", "oxeye_daisy", "cornflower", "allium")


def inward(o):
    return FACE[(-o[0], -o[1])]


def outward(o):
    return FACE[o]


def along(o, s):
    """The facing pointing along side o toward +t (s = 1) or -t (s = -1)."""
    return FACE[Cap.side(o, 0, s)]


def hsh(x, z):
    return ((x + 31) * 73856093 ^ (z + 17) * 19349663) % 101


class Cap:
    """A _SunRig addressed from the footprint's centre cell, so a ring of the temple is max(|dx|, |dz|) = k and each
    side is drawn once for all four: side(o, a, t) is the cell `a` out along side o and `t` across it."""

    def __init__(self, R, H):
        self.R, self.H = R, H
        self.rig = gs._SunRig(2 * R + 1, H, 2 * R + 1)

    def set(self, dx, y, dz, name, **p):
        self.rig.set(self.R + dx, y, self.R + dz, name, **p)

    def get(self, dx, y, dz):
        return self.rig.get(self.R + dx, y, self.R + dz)

    def stair(self, dx, y, dz, facing, half="bottom", name="smooth_quartz_stairs"):
        self.rig.stair(self.R + dx, y, self.R + dz, facing, half, name)

    def slab(self, dx, y, dz, name="smooth_quartz_slab", typ="bottom"):
        self.set(dx, y, dz, name, type=typ, waterlogged="false")

    @staticmethod
    def side(o, a, t):
        ox, oz = o
        return a * ox - t * oz, a * oz + t * ox

    def put(self, o, a, t, y, name, **p):
        x, z = self.side(o, a, t)
        self.set(x, y, z, name, **p)

    def st(self, o, a, t, y, facing, half="bottom", name="smooth_quartz_stairs"):
        x, z = self.side(o, a, t)
        self.stair(x, y, z, facing, half, name)

    def cells(self, k, k0=0):
        for dx in range(-k, k + 1):
            for dz in range(-k, k + 1):
                if max(abs(dx), abs(dz)) >= k0:
                    yield dx, dz

    def disc(self, k, y, name, k0=0, **p):
        for dx, dz in self.cells(k, k0):
            self.set(dx, y, dz, name, **p)

    def ring_stairs(self, k, y, half="bottom", name="smooth_quartz_stairs", out=False):
        for o in O4.values():
            for t in range(-k, k + 1):
                self.st(o, k, t, y, outward(o) if out else inward(o), half, name)


def plaza_edge(c, R, steps):
    """The outer ring: no hard kerb but a garden that feathers into whatever ground the capitol stands on - grass and
    moss with calcite flags scattered through them, flowers on the grass - the same on all four sides, since a base
    grows whichever way the player builds. RoN's starting types (the instant foundation) sit in it: andesite and
    stone-brick top stairs as the flat flags under and beside the steps (they read as paving), grass in the beds."""
    for dx, dz in c.cells(R, R):
        t = min(abs(dx), abs(dz))
        f = inward_of(dx, dz)
        if t <= steps:
            c.stair(dx, 0, dz, f, "top", "polished_andesite_stairs")
        elif t == steps + 1:
            c.stair(dx, 0, dz, f, "top", "stone_brick_stairs")
        else:
            h = hsh(dx, dz)
            c.set(dx, 0, dz, "calcite" if h % 4 == 0 else "moss_block" if h % 4 == 1 else "grass_block",
                  **({"snowy": "false"} if h % 4 > 1 else {}))
            if h % 4 > 1 and h % 3 != 0:
                c.set(dx, 1, dz, FLOWERS[h % len(FLOWERS)])


def inward_of(dx, dz):
    if abs(dx) >= abs(dz):
        return "west" if dx > 0 else "east"
    return "north" if dz > 0 else "south"


# ===================================================================================================== SAFE
def angel(c, sx, sz):
    """An angel on each plaza corner, looking out of it - the Angel Fountain's figure in miniature, so the capitol and
    the extractors read as one family: a chiseled pedestal, a quartz-pillar robe, a calcite head, wings spread over
    the two garden cells beside it (upside-down stairs for the hanging feathers, rising stairs over them for the
    leading edge, curling out), and held over the head the kit's light, a lantern on a gold boss."""
    x, z = sx * 5, sz * 5
    c.set(x, 0, z, "smooth_stone")
    c.set(x, 1, z, "chiseled_quartz_block")
    c.set(x, 2, z, "quartz_pillar", axis="y")
    c.set(x, 3, z, "calcite")
    c.set(x, 4, z, "gold_block")
    c.set(x, 5, z, "lantern", **LANTERN)
    for (wx, wz, toward_out) in ((sx * 4, z, "east" if sx > 0 else "west"),
                                 (x, sz * 4, "south" if sz > 0 else "north")):
        c.stair(wx, 3, wz, toward_out, "top")       # the hanging feathers: the lip hangs toward the angel
        c.stair(wx, 4, wz, _OPP[toward_out])        # the leading edge rising out to the tip


_OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def build():
    R, H = 5, 9
    c = Cap(R, H)
    # ---- y0: paving under the temple and the garden edge
    c.disc(4, 0, "smooth_stone")
    c.disc(3, 0, "polished_diorite")
    for o in O4.values():
        for a in (1, 2, 3):
            c.put(o, a, 0, 0, "calcite")
    c.set(0, 0, 0, "gold_block")
    plaza_edge(c, R, 1)
    # ---- y1: the stylobate: a moulded rim of quartz stairs (high side in) round a diorite floor, chiseled corners,
    # a quartz-and-lapis cross on the axes; steps on all four sides, lamp posts at the step ends
    c.disc(3, 1, "polished_diorite")
    for o in O4.values():
        for t in range(-3, 4):
            if abs(t) <= 1:
                c.put(o, 4, t, 1, "lapis_block" if t == 0 else "smooth_quartz")
            else:
                c.st(o, 4, t, 1, inward(o), name="quartz_stairs")
        for a in (1, 2, 3):
            c.put(o, a, 0, 1, "lapis_block" if a == 3 else "smooth_quartz")
        for t in (-1, 0, 1):
            c.st(o, R, t, 1, inward(o), name="quartz_stairs")
        for s in (-1, 1):
            c.put(o, R, 3 * s, 1, "diorite_wall")
            c.put(o, R, 3 * s, 2, "lantern", **LANTERN)
    for (sx, sz) in CORNERS:
        c.set(sx * 4, 1, sz * 4, "chiseled_quartz_block")
    # ---- the order on ring 4: corner piers and, per side, two columns framing the portico's 3-wide entrance.
    # Column: stylobate stair base, quartz-pillar shaft (y2-3), gold capital (y4) under the architrave
    for o in O4.values():
        for s in (-1, 1):
            c.put(o, 4, 2 * s, 1, "smooth_quartz")          # a square base under the column, not a stair
            c.put(o, 4, 2 * s, 2, "quartz_pillar", axis="y")
            c.put(o, 4, 2 * s, 3, "quartz_pillar", axis="y")
            c.put(o, 4, 2 * s, 4, "gold_block")
    for (sx, sz) in CORNERS:
        c.set(sx * 4, 2, sz * 4, "quartz_bricks")
        c.set(sx * 4, 3, sz * 4, "blue_stained_glass")
        c.set(sx * 4, 4, sz * 4, "chiseled_quartz_block")
    # ---- the cella on ring 2: a door on each axis under a lintel, blue lancets beside it, pillar corners
    for o in O4.values():
        for t in (-1, 1):
            c.put(o, 2, t, 2, "quartz_bricks")
            c.put(o, 2, t, 3, "blue_stained_glass")
            c.put(o, 2, t, 4, "quartz_bricks")
        c.st(o, 2, 0, 4, outward(o), "top")
    for (sx, sz) in CORNERS:
        for y in (2, 3, 4):
            c.set(sx * 2, y, sz * 2, "quartz_pillar", axis="y")
    # ---- y5: the entablature - the cornice of upside-down stairs all round, a smooth quartz roof, the hall's light
    c.disc(4, 5, "smooth_quartz")
    c.set(0, 5, 0, "ochre_froglight", axis="y")
    for o in O4.values():
        for t in range(-3, 4):
            c.st(o, 4, t, 5, inward(o), "top")
    for (sx, sz) in CORNERS:
        c.set(sx * 4, 5, sz * 4, "chiseled_quartz_block")
    # ---- y6-7 on ring 4: the balustrade - slab rails between posts, a lantern over each portico, candles on the
    # posts beside it, a quartz pinnacle with a blue banner on each corner
    for o in O4.values():
        for t in (-3, -1, 1, 3):
            c.put(o, 4, t, 6, "smooth_quartz_slab", type="bottom", waterlogged="false")
        for s in (-1, 1):
            c.put(o, 4, 2 * s, 6, "diorite_wall")
            c.put(o, 4, 2 * s, 7, "white_candle", candles="3", lit="true", waterlogged="false")
        c.put(o, 4, 0, 6, "chiseled_quartz_block")
        c.put(o, 4, 0, 7, "lantern", **LANTERN)
    for (sx, sz) in CORNERS:
        c.set(sx * 4, 6, sz * 4, "quartz_pillar", axis="y")
        c.set(sx * 4, 7, sz * 4, "blue_banner", rotation=CORNER_ROT[(sx, sz)])
    # ---- the tiered dome (the kit's silhouette motif, ref 79's ringed white city): a slab terrace on ring 3, a drum
    # on ring 2 (pillars and blue glass showing over the terrace), a stair tier on ring 2, another on ring 1, the gold
    # sun at the crown
    for o in O4.values():
        for t in range(-3, 4):
            c.put(o, 3, t, 6, "smooth_quartz_slab", type="bottom", waterlogged="false")
        for t in (-1, 1):
            c.put(o, 2, t, 6, "blue_stained_glass")
        c.put(o, 2, 0, 6, "quartz_pillar", axis="y")
    for (sx, sz) in CORNERS:
        c.set(sx * 2, 6, sz * 2, "quartz_pillar", axis="y")
    c.disc(1, 6, "smooth_quartz")
    c.disc(1, 7, "smooth_quartz")
    c.ring_stairs(2, 7, name="quartz_stairs")
    c.ring_stairs(1, 8, name="quartz_stairs")
    c.set(0, 8, 0, "gold_block")
    # four axis finials standing on the terrace: chiseled base, pillar, gold top - BuildingAmbienceClientEvents'
    # glints at (5.5, 9.1, 2.5) and (5.5, 9.1, 8.5) sit right over the north and south ones
    for o in O4.values():
        c.put(o, 3, 0, 6, "chiseled_quartz_block")
        c.put(o, 3, 0, 7, "diorite_wall")
        c.put(o, 3, 0, 8, "end_rod", facing="up")
    for (sx, sz) in CORNERS:
        angel(c, sx, sz)
    c.rig.settle()
    c.rig.check_support("town_centre")
    return c.rig.structure()


# ==================================================================================================== GRAND
def build_grand():
    """The capitol at the size lovish asked for (15 x 20 x 15): NOT shippable until the Java changes in the module
    docstring land (the GameTest pins RoN's 11 x 9 x 11 box). Same kit, same plan, a full classical order."""
    R, H = 7, 20
    c = Cap(R, H)
    # ---- y0: paving and the feathered garden edge (ring 7)
    c.disc(6, 0, "smooth_stone")
    c.disc(5, 0, "polished_diorite")
    for o in O4.values():
        for a in range(1, 6):
            c.put(o, a, 0, 0, "calcite")
    c.set(0, 0, 0, "gold_block")
    plaza_edge(c, R, 2)
    # ---- y1: the krepis - a continuous quartz step all round on ring 6 - and the stylobate inside it
    c.disc(5, 1, "polished_diorite")
    c.ring_stairs(6, 1, name="quartz_stairs")
    for o in O4.values():
        for t in range(-4, 5):
            c.put(o, 5, t, 1, "smooth_quartz")
        for a in range(1, 5):
            c.put(o, a, 0, 1, "lapis_block" if a == 4 else "smooth_quartz")
        for s in (-1, 1):
            c.put(o, R, 4 * s, 1, "diorite_wall")
            c.put(o, R, 4 * s, 2, "lantern", **LANTERN)
    for (sx, sz) in CORNERS:
        c.set(sx * 6, 1, sz * 6, "chiseled_quartz_block")
        c.set(sx * 5, 1, sz * 5, "chiseled_quartz_block")
    # ---- the order on ring 5: plinth (chiseled), shaft (four quartz pillars), capital (gold, with quartz volutes -
    # upside-down stairs - either side of it on the architrave's underside is the next layer up)
    for o in O4.values():
        for t in (-3, -1, 1, 3):
            c.put(o, 5, t, 2, "chiseled_quartz_block")
            for y in range(3, 7):
                c.put(o, 5, t, y, "quartz_pillar", axis="y")
            c.put(o, 5, t, 7, "gold_block")
    # ---- the corner towers (rings 5-6 at the corners, 2 x 2): rusticated quartz brick, a blue lancet on each camera
    # face, gold band, spire cluster on top (ref 41)
    for (sx, sz) in CORNERS:
        for qx in (5, 6):
            for qz in (5, 6):
                for y in range(2, 14):
                    c.set(sx * qx, y, sz * qz, "quartz_bricks")
        for y in range(2, 14):
            c.set(sx * 6, y, sz * 6, "quartz_pillar", axis="y")
        for y in (4, 5, 10, 11):
            c.set(sx * 6, y, sz * 5, "blue_stained_glass")
            c.set(sx * 5, y, sz * 6, "blue_stained_glass")
        c.set(sx * 6, 8, sz * 5, "chiseled_quartz_block")
        c.set(sx * 5, 8, sz * 6, "chiseled_quartz_block")
        for (qx, qz) in ((5, 5), (6, 5), (5, 6)):
            c.set(sx * qx, 14, sz * qz, "smooth_quartz")
        c.set(sx * 6, 14, sz * 6, "gold_block")
        # the spires: a tall one on the outer corner, two lesser on the other outer cells, a lantern by them
        for y in (15, 16, 17):
            c.set(sx * 6, y, sz * 6, "diorite_wall")
        c.set(sx * 6, 18, sz * 6, "end_rod", facing="up")
        c.set(sx * 6, 15, sz * 5, "diorite_wall")
        c.set(sx * 6, 16, sz * 5, "end_rod", facing="up")
        c.set(sx * 5, 15, sz * 6, "diorite_wall")
        c.set(sx * 5, 16, sz * 6, "end_rod", facing="up")
        c.set(sx * 5, 15, sz * 5, "lantern", **LANTERN)
    # ---- the cella on ring 3: quartz brick, blue windows high up, a 1 x 3 door on each axis under a stepped lintel
    for o in O4.values():
        for t in range(-3, 4):
            for y in range(2, 8):
                if t == 0 and y < 5:
                    continue
                win = abs(t) == 2 and y in (4, 5)
                c.put(o, 3, t, y, "blue_stained_glass" if win else "quartz_bricks")
        c.st(o, 3, 0, 5, outward(o), "top")
        c.put(o, 3, 0, 6, "chiseled_quartz_block")
        for s in (-1, 1):
            c.st(o, 3, s, 4, along(o, -s), "top")              # the door's splayed jambs
    for (sx, sz) in CORNERS:
        for y in range(2, 8):
            c.set(sx * 3, y, sz * 3, "quartz_pillar", axis="y")
    # ---- the entablature over the colonnade (rings 3..5): architrave y8, frieze y9 (lapis triglyphs between
    # chiseled metopes), cornice y10 hanging out over the krepis on ring 6
    c.disc(5, 8, "smooth_quartz", k0=3)
    for o in O4.values():
        for t in range(-4, 5):
            c.put(o, 5, t, 9, "lapis_block" if t % 2 else "chiseled_quartz_block")
        for t in range(-4, 5):
            c.put(o, 4, t, 9, "smooth_quartz")
            c.put(o, 3, t, 9, "smooth_quartz")
        for t in range(-4, 5):
            c.st(o, 6, t, 10, inward(o), "top")
            c.put(o, 5, t, 10, "smooth_quartz")
            c.put(o, 4, t, 10, "smooth_quartz")
            c.put(o, 3, t, 10, "smooth_quartz")
    # ---- the pediments: a gable over each portico on ring 5-6, stairs climbing to a gold apex, a lapis tympanum
    for o in O4.values():
        for k, y in ((3, 11), (2, 12), (1, 13)):
            for a in (5, 6):
                for s in (-1, 1):
                    c.st(o, a, k * s, y, along(o, -s))
                    for kk in range(k - 1, 0, -1) if a == 5 else ():
                        c.put(o, a, kk * s, y, "lapis_block" if a == 5 and y == 11 else "smooth_quartz")
                if a == 5:
                    c.put(o, a, 0, y, "lapis_block" if y < 13 else "smooth_quartz")
        c.put(o, 6, 0, 13, "smooth_quartz")
        c.put(o, 6, 0, 14, "gold_block")
        c.put(o, 6, 0, 15, "lantern", **LANTERN)
        c.put(o, 5, 0, 14, "smooth_quartz_slab", type="bottom", waterlogged="false")
        c.put(o, 4, 0, 14, "smooth_quartz_slab", type="bottom", waterlogged="false")
        for t in (-1, 0, 1):
            for a in (4,):
                c.put(o, a, t, 11, "smooth_quartz")
                c.put(o, a, t, 12, "smooth_quartz")
                c.put(o, a, t, 13, "smooth_quartz")
        # the attic balustrade between pediment and tower: slab rails on walls
        for s in (-1, 1):
            c.put(o, 6, 4 * s, 11, "diorite_wall")
            c.put(o, 6, 4 * s, 12, "white_candle", candles="3", lit="true", waterlogged="false")
    # ---- the drum (ring 3, y11-13): pillars and tall blue windows, a gold band, and the tiered dome
    for o in O4.values():
        for t in range(-2, 3):
            for y in (11, 12, 13):
                c.put(o, 3, t, y, "blue_stained_glass" if abs(t) == 1 and y < 13 else "quartz_bricks")
    for (sx, sz) in CORNERS:
        for y in (11, 12, 13):
            c.set(sx * 3, y, sz * 3, "quartz_pillar", axis="y")
    c.disc(2, 13, "smooth_quartz")
    c.set(0, 13, 0, "ochre_froglight", axis="y")      # the rotunda's light, over the tall hall
    c.ring_stairs(3, 14, name="quartz_stairs")
    for (sx, sz) in CORNERS:
        c.set(sx * 3, 14, sz * 3, "gold_block")
    c.disc(2, 14, "smooth_quartz")
    # the lantern drum: ring 2 colonnettes and glass, then two more stair tiers and the gold sun
    for o in O4.values():
        for t in (-1, 0, 1):
            c.put(o, 2, t, 15, "blue_stained_glass" if t == 0 else "smooth_quartz")
    for (sx, sz) in CORNERS:
        c.set(sx * 2, 15, sz * 2, "quartz_pillar", axis="y")
    c.disc(1, 15, "smooth_quartz")
    c.ring_stairs(2, 16, name="quartz_stairs")
    c.disc(1, 16, "smooth_quartz")
    c.ring_stairs(1, 17, name="quartz_stairs")
    c.set(0, 17, 0, "gold_block")
    c.set(0, 18, 0, "gold_block")
    c.set(0, 19, 0, "end_rod", facing="up")
    # ---- the plaza angels, one on each corner of the garden
    for (sx, sz) in CORNERS:
        x, z = sx * 7, sz * 7
        c.set(x, 0, z, "smooth_stone")
        c.set(x, 1, z, "chiseled_quartz_block")
        c.set(x, 2, z, "quartz_pillar", axis="y")
        c.set(x, 3, z, "chiseled_quartz_block")
        c.set(x, 4, z, "lantern", **LANTERN)
        c.stair(sx * 6, 3, z, "east" if sx > 0 else "west", "top")
        c.stair(x, 3, sz * 6, "south" if sz > 0 else "north", "top")
        c.set(sx * 6, 2, z, "diorite_wall")
        c.set(x, 2, sz * 6, "diorite_wall")
    c.rig.settle()
    c.rig.check_support("town_centre grand")
    return c.rig.structure()


# ===================================================================================================== rules
STARTING_TYPES = {"stone_brick_stairs", "grass_block", "polished_andesite_stairs"}   # TownCentre.startingBlockTypes
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "dragon_egg",
             "scaffolding", "pointed_dripstone")


def check(s, box=(11, 9, 11)):
    """The rules the game needs, on the finished Structure (the support check ran in build())."""
    assert s.size == box, "the box is %s, RoN's is %s (BiomeBaseGameTests pins it)" % (s.size, box)
    names = {(x, y, z): s.palette[i][0] for (x, y, z), i in s.blocks.items()}
    n = sum(1 for nm in names.values() if nm not in ("air", "cave_air"))
    assert n < 2000, n
    assert max(p[0] for p in names) == box[0] - 1 and max(p[1] for p in names) == box[1] - 1 \
        and max(p[2] for p in names) == box[2] - 1, "the drawn blocks do not fill the box"
    layer0 = {nm for (x, y, z), nm in names.items() if y == 0}
    assert STARTING_TYPES <= layer0, "starting types missing from layer 0: %s" % (STARTING_TYPES - layer0)
    above = {nm for (x, y, z), nm in names.items() if y > 0} & STARTING_TYPES
    assert not above, "a starting type above the foundation would join no queue but look odd: %s" % above
    bad = sorted({nm for nm in names.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)})
    assert not bad, "forbidden blocks: %s" % bad
    # the spawn cell: the centre column walked down from mid-height lands on the cella floor, open above
    cx, cz = box[0] // 2, box[2] // 2
    assert names.get((cx, 1, cz)) and names.get((cx, 2, cz)) is None and names.get((cx, 3, cz)) is None, \
        "the centre spawn cell (y2) must stand on the floor with two cells of air"
    return n


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/tc.nbt"
    grand = "--grand" in sys.argv
    st = build_grand() if grand else build()
    print("blocks:", check(st, (15, 20, 15) if grand else (11, 9, 11)))
    st.write(out)
    print("wrote", out)
