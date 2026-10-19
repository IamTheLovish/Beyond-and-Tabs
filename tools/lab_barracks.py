#!/usr/bin/env python3
"""The Sunforged (White Angel) T1 army lab, the Barracks, redrawn at all three levels - L1 THE TRAINING HALL, L2 THE
KNIGHTS' CLOISTER, L3 THE CATHEDRAL OF ARMS - so it belongs to the family the Temple of the Sun (the capitol,
tools/capitol_town_centre.py build_grand) and the Angel Fountain (the extractor, gen_structures._sun_fountain_t1) set.

lovish, Oct 19 2026: buildings must "look like they match each other so when bases get bigger they look cohesive and
beautiful"; "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out for the
opponent; ... much bigger or have cooler, more intricate designs". The White Angel is classical at tiers 1-2 and gothic
at tier 3 (refs 84-86, 92: cathedral tracery and buttresses), so L1 and L2 are a classical temple court and L3 turns
it into a white cathedral.

THE SUNFORGED STYLE KIT (the capitol's and the fountain's):
  - main blocks: white stone and quartz (smooth / bricks / pillar / chiseled quartz, calcite, polished diorite);
  - trim: gold (the column capitals, the bands, the crowns), lapis on the axes (the frieze triglyphs);
  - accent: BLUE GLASS (lancets, the drums, the rose window) and blue banners;
  - light: lanterns on posts, pinnacles and crowns, candles on the balustrade;
  - silhouette motif: TIERED DOMES (rings of stairs climbing inward) with a gold crown;
  - family figure: the angel - on the bell turret (L1-L2), gilded on top of the belfry spire (L3);
  - edge: a garden (grass and flowers) at the yard's corners.

THE PLAN (10 x 8, the shipped footprint). The RTS camera looks from the south-west: it sees the roofs, the west face
(x = 0) and the south face (z = 7). So the buildings stand in a NORTH RANGE (z 0..2) and face south over the DRILL
YARD (z 3..7) toward the camera; the bell turret is the range's west end, the corner the camera meets first.
  L1 THE TRAINING HALL
  - the hall (x 3..9, z 0..2): a stylobate, a south colonnade - corner piers, two quartz columns with gold capitals,
    three open bays into the hall - a frieze of lapis triglyphs and chiseled metopes, a cornice of upside-down stairs,
    a balustrade with candle posts, and over the middle a small tiered dome: a drum of pillars and blue glass, a ring
    of stairs, the gold crown and a lantern;
  - the bell turret (x 0..2, z 0..2): a quartz shaft with blue lancets, a gold-cornered band, an open belfry with the
    Barracks' bell, a ring-of-stairs cap and on a pillar the ANGEL with spread wings holding up a lantern;
  - the drill yard (x 0..9, z 3..7): packed sand (smooth sandstone - real sand falls) in an andesite kerb, a calcite
    drill ring round a gold sun with a pell (an archery butt on a post), a rack of white lances (end rods), blue
    banners at the corners, lantern posts at the hall's ends, garden beds with flowers on the two front corners.
  L2 THE KNIGHTS' CLOISTER - the yard is enclosed:
  - two open domed pavilions on the yard's front corners (four columns, an entablature with a lapis frieze, a drum of
    blue glass, a ring of stairs, a gold crown and lantern, a lantern hung inside): the corner towers with tiered
    domes, see-through for the camera and walk-through for units;
  - arcaded cloister walls between them (a two-wide gate on the axis in the south wall, arches on the west and east
    sides; a walk on top, lanterns on the piers) - every arch is open;
  - the hall's dome rises on a three-course drum with a gold band, flanked by a stepped tier, and the hall's corners
    carry the capitol's pinnacles with blue banners.
  L3 THE CATHEDRAL OF ARMS - gothic:
  - the hall's middle rises into a transept gable facing the camera with a ROSE WINDOW (blue glass, chiseled tracery,
    a glowing heart) under a hood, a gold finial on its apex; the dome becomes a crossing tower with pinnacles and a
    gold needle;
  - clerestory walls with lancets and a steep white roof with a gold ridge over the rest of the range;
  - FLYING BUTTRESSES on the camera's side: piers rising from the yard with pinnacles, flyers leaning onto the nave;
  - the bell turret becomes a BELFRY SPIRE in gothic stages (square shaft, chamfered stage with corner pinnacles, the
    bell up in a new open belfry under pointed arches, a gold knop) with a GOLD ANGEL on top - the landmark;
  - the pavilions' crowns become gold spirelets with pinnacles.

GAME RULES (asserted in check_levels):
  - every level keeps the 10 x 8 footprint (BuildingLevels has no larger footprint); a level may grow taller;
  - the chain: every non-air cell of L1 is non-air in L2, and of L2 in L3 (changeStructure only adds or replaces):
    L2 is drawn over L1 and L3 over L2, and that is asserted;
  - Barracks.startingBlockTypes = polished_andesite_stairs: in layer 0 (the kerb), nowhere above;
  - the unit spawn: ProductionBuilding.getIndoorSpawnPoint walks down from centrePos, the L1 box's centre. centrePos is
    (int) of ((minX + maxX) / 2f, ...) in WORLD coordinates, so on this even footprint it is the cell x 4 or 5 and z 3
    or 4 depending on the sign of the world coordinate and on the placement's rotation, at y = L1 maxY / 2 (or one
    up below y = 0); changeStructure never moves it, so L2 and L3 spawn there too. The four central columns
    (x 4..5, z 3..4) are yard, open from the floor to above that height at every level, and units walk out;
  - nothing pops off (lanterns standing / hung, candles, banners, the bell, flowers); no pockets
    (tools/audit_structure_pockets.py on all three); no water, lava, gravity blocks, redstone, campfires, doors, leaves.

Usage: python3 tools/lab_barracks.py [OUT_DIR]   (writes barracks.nbt, barracks_l2.nbt, barracks_l3.nbt and checks them;
       default OUT_DIR: the scratch folder - shipping them is the coordinator's step)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

SX, SZ = 10, 8
CENTRE = [(4, 3), (5, 3), (4, 4), (5, 4)]   # the cells the spawn can start in (see the docstring)
FLOWERS = ("cornflower", "lily_of_the_valley", "azure_bluet", "oxeye_daisy", "allium")
START_TYPE = "polished_andesite_stairs"       # Barracks.startingBlockTypes
CORNERS3 = ((0, 0), (2, 0), (0, 2), (2, 2))


class Rig(gs._SunRig):
    """The Sunforged rig (gen_structures._SunRig: settle() shapes stairs and joins walls like the game does) with
    the support check this lab needs: _SunRig's own refuses any hung bell and any wall banner, this lab has both."""

    def __init__(self, sy):
        super().__init__(SX, sy, SZ)

    def st(self, x, y, z, facing, half="bottom", name="quartz_stairs"):
        self.stair(x, y, z, facing, half, name)

    def slab(self, x, y, z, name="smooth_quartz_slab", typ="bottom"):
        self.set(x, y, z, name, type=typ, waterlogged="false")

    def fill(self, x1, y1, z1, x2, y2, z2, name, **p):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **p)

    def pillar(self, x, y, z):
        self.set(x, y, z, "quartz_pillar", axis="y")

    def lantern(self, x, y, z, hanging=False):
        self.set(x, y, z, "lantern", hanging="true" if hanging else "false", waterlogged="false")

    def candle(self, x, y, z, n=3):
        self.set(x, y, z, "white_candle", candles=str(n), lit="true", waterlogged="false")

    def check_support(self, tier):
        bad = []
        for pos, (n, p) in self.b.items():
            below, above = self.nb(pos, "down"), self.nb(pos, "up")
            if n in ("lantern", "soul_lantern"):
                if p.get("hanging") == "true":
                    ok = gs._sun_sturdy(above, "down") or above[0] == "chain"
                else:
                    ok = gs._sun_centre_up(below)
            elif n.endswith("candle"):
                ok = gs._sun_centre_up(below)
            elif n.endswith("_wall_banner"):
                behind = self.nb(pos, gs._SUN_OPP[p["facing"]])
                ok = gs._sun_solid(behind) and not gs._sun_is(behind, *gs._SUN_THIN)
            elif n.endswith("_banner"):
                ok = gs._sun_solid(below)
            elif n == "bell":     # vanilla BellBlock.canSurvive: a ceiling bell needs a full face over it
                a = p.get("attachment")
                ok = (a == "ceiling" and gs._sun_sturdy(above, "down")) or \
                    (a == "floor" and gs._sun_sturdy(below, "up"))
            elif n in FLOWERS:
                ok = below[0] in ("grass_block", "dirt", "moss_block", "coarse_dirt", "podzol")
            elif n.endswith(("_torch", "_button", "ladder", "_carpet")) or n in ("torch", "soul_torch"):
                ok = False    # none are drawn on this lab
            else:
                continue
            if not ok:
                bad.append((pos, n))
        assert not bad, "barracks %s: would pop off / hang: %s" % (tier, sorted(bad))


def ring(r, x1, x2, z1, z2, y, name="quartz_stairs", half="bottom"):
    """A ring of stairs climbing inward over [x1..x2] x [z1..z2]: one tier of the kit's dome (settle() turns the
    corners)."""
    for x in range(x1, x2 + 1):
        r.st(x, y, z1, "south", half, name)
        r.st(x, y, z2, "north", half, name)
    for z in range(z1 + 1, z2):
        r.st(x1, y, z, "east", half, name)
        r.st(x2, y, z, "west", half, name)


def angel(r, x, y, z, gold=False):
    """The family's angel (the fountain's, the capitol's plaza angels), feet at y, facing south: a quartz robe, a
    calcite head, and wings spread east-west from the shoulders and swept UP past the head (the fountain's wing: an
    upside-down stair for the hanging feathers, a full quill, a stair for the tip curling in) so the figure reads as a
    V of wings round the lantern it holds up on a gold boss - not a cross. Gilded on the L3 spire."""
    r.set(x, y, z, "gold_block") if gold else r.pillar(x, y, z)
    r.set(x, y + 1, z, "gold_block" if gold else "calcite")
    for dx, out in ((-1, "west"), (1, "east")):
        r.st(x + dx, y + 1, z, out, "top", name="smooth_quartz_stairs")          # the hanging feathers
        r.set(x + dx, y + 2, z, "gold_block" if gold else "smooth_quartz")       # the quill
        r.st(x + dx, y + 3, z, gs._SUN_OPP[out], name="smooth_quartz_stairs")    # the tip, curling in
    r.set(x, y + 2, z, "gold_block")
    r.lantern(x, y + 3, z)


def dome3(r, x0, z0, y, drum=1, crown="gold_block", light=True):
    """The kit's tiered dome on a 3 x 3 (low corner x0, z0) from y: a drum of quartz pillars on the corners and blue
    glass on the faces (`drum` courses), a ring of stairs climbing in, the gold crown and a lantern. Returns the
    crown's height."""
    for yy in range(y, y + drum):
        for dx in (0, 1, 2):
            for dz in (0, 1, 2):
                corner, mid = dx != 1 and dz != 1, dx == 1 and dz == 1
                if corner:
                    r.pillar(x0 + dx, yy, z0 + dz)
                else:
                    r.set(x0 + dx, yy, z0 + dz, "smooth_quartz" if mid else "blue_stained_glass")
    ring(r, x0, x0 + 2, z0, z0 + 2, y + drum)
    r.set(x0 + 1, y + drum, z0 + 1, "smooth_quartz")
    r.set(x0 + 1, y + drum + 1, z0 + 1, crown)
    if light:
        r.lantern(x0 + 1, y + drum + 2, z0 + 1)
    return y + drum + 1


# ======================================================================================================== L1
STYLO, BASE, CAP, ENT, CORN = 1, 2, 4, 5, 6     # the order: stylobate, column foot, gold capital, frieze, cornice
DECK = CORN                                      # the hall's roof deck
HX0, HX1, HMID = 3, 9, 6                         # the hall's x range and its axis
COLS, PIERS = (5, 7), (3, 9)                     # on the south colonnade (z 2)
TB = 6                                           # the bell turret's belfry floor


def hall_l1(r):
    """The Training Hall (x 3..9, z 0..2), its colonnade facing the camera across the yard."""
    r.fill(HX0, 0, 0, HX1, 0, 2, "polished_diorite")
    r.fill(HX0, STYLO, 0, HX1, STYLO, 2, "smooth_quartz")
    for x in PIERS:
        for z in (0, 2):
            r.set(x, 0, z, "chiseled_quartz_block")
            r.set(x, STYLO, z, "chiseled_quartz_block")
    # the south colonnade: piers of quartz brick with chiseled caps, columns of quartz pillar with gold capitals (the
    # capitol's order), the bays between open into the hall
    for x in COLS:
        for y in range(BASE, CAP):
            r.pillar(x, y, 2)
        r.set(x, CAP, 2, "gold_block")
    for x in PIERS:
        for y in range(BASE, CAP):
            r.set(x, y, 2, "quartz_bricks")
        r.set(x, CAP, 2, "chiseled_quartz_block")
    # the back wall (z 0) with blue windows seen through the bays, the end walls (x 3 against the turret, x 9)
    for x in range(HX0, HX1 + 1):
        for y in range(BASE, CAP + 1):
            win = x in (4, 6, 8) and y == BASE + 1
            r.set(x, y, 0, "blue_stained_glass" if win else "quartz_bricks")
    for x in PIERS:
        for y in range(BASE, CAP + 1):
            r.set(x, y, 1, "quartz_bricks")
    # the entablature: the frieze on the camera's face (lapis triglyphs over the supports, chiseled metopes between),
    # smooth quartz elsewhere; the cornice above it all round (upside-down stairs, lip out) round the deck
    r.fill(HX0, ENT, 0, HX1, ENT, 2, "smooth_quartz")
    for x in range(HX0, HX1 + 1):
        r.set(x, ENT, 2, "lapis_block" if x in COLS + PIERS else "chiseled_quartz_block")
    r.set(HX1, ENT, 1, "chiseled_quartz_block")
    for x in range(HX0, HX1 + 1):
        r.st(x, CORN, 2, "north", "top")
        r.st(x, CORN, 0, "south", "top")
    r.st(HX1, CORN, 1, "west", "top")
    r.fill(HX0, CORN, 1, HX1 - 1, CORN, 1, "smooth_quartz")
    # the balustrade on the front: candle posts over the piers, slab rails between
    for x in PIERS:
        r.set(x, DECK + 1, 2, "diorite_wall")
        r.candle(x, DECK + 2, 2)
    for x in (4, 8):
        r.slab(x, DECK + 1, 2)
    # the small tiered dome over the hall's middle (x 5..7, the whole depth), on the axis
    dome3(r, HMID - 1, 0, DECK + 1, drum=2)


def turret_l1(r):
    """The bell turret (x 0..2, z 0..2): a quartz shaft with blue lancets, an open belfry with the Barracks' bell,
    a ring-of-stairs cap, and the angel on a pillar so its wings spread against the sky, not the roof."""
    r.fill(0, 0, 0, 2, 0, 2, "polished_diorite")
    r.fill(0, 1, 0, 2, 1, 2, "smooth_quartz")
    for y in range(2, TB - 1):
        r.fill(0, y, 0, 2, y, 2, "quartz_bricks")
    for (x, z) in CORNERS3:
        r.set(x, 0, z, "chiseled_quartz_block")
        r.set(x, 1, z, "chiseled_quartz_block")
        for y in range(2, TB - 1):
            r.pillar(x, y, z)
    for y in (2, 3):
        r.set(0, y, 1, "blue_stained_glass")
        r.set(1, y, 2, "blue_stained_glass")
    # the belfry floor carries the hall's capital line round the turret: chiseled with gold corners
    r.fill(0, TB - 1, 0, 2, TB - 1, 2, "chiseled_quartz_block")
    for (x, z) in CORNERS3:
        r.set(x, TB - 1, z, "gold_block")
        r.pillar(x, TB, z)
        r.pillar(x, TB + 1, z)
    r.fill(0, TB + 2, 0, 2, TB + 2, 2, "smooth_quartz")
    r.set(1, TB + 1, 1, "bell", attachment="ceiling", facing="south", powered="false")
    ring(r, 0, 2, 0, 2, TB + 3, name="smooth_quartz_stairs")
    r.set(1, TB + 3, 1, "chiseled_quartz_block")
    r.pillar(1, TB + 4, 1)
    angel(r, 1, TB + 5, 1)
    r.set(1, 4, 3, "blue_wall_banner", facing="south")       # the house banner, hung toward the yard


def yard_l1(r):
    """The drill yard (x 0..9, z 3..7)."""
    for x in range(SX):
        for z in range(3, SZ):
            r.set(x, 0, z, "smooth_sandstone")
    # the kerb on the three open sides: andesite stairs rising toward the yard - the starting type, the instant
    # foundation RoN lays first
    for z in range(3, SZ):
        r.st(0, 0, z, "east", name=START_TYPE)
        r.st(SX - 1, 0, z, "west", name=START_TYPE)
    for x in range(SX):
        r.st(x, 0, SZ - 1, "north", name=START_TYPE)
    # garden beds on the two front corners, flowers in them
    for i, (x, z) in enumerate(((0, 7), (1, 7), (0, 6), (9, 7), (8, 7), (9, 6))):
        r.set(x, 0, z, "grass_block", snowy="false")
        if (x, z) not in ((0, 6), (9, 6)):
            r.set(x, 1, z, FLOWERS[i % len(FLOWERS)])
    # the drill ring: calcite round a gold sun, a pell in it (an archery butt on a dark oak post)
    for (x, z) in ((1, 4), (2, 4), (3, 4), (1, 5), (3, 5), (1, 6), (2, 6), (3, 6)):
        r.set(x, 0, z, "calcite")
    r.set(2, 0, 5, "gold_block")
    r.set(2, 1, 5, "dark_oak_fence")
    r.set(2, 2, 5, "target", power="0")
    # the lance rack on the east side: dark oak posts, three white lances standing in it, a butt beside
    for x in (6, 7, 8):
        r.set(x, 1, 5, "dark_oak_fence")
        r.set(x, 2, 5, "end_rod", facing="up")
    r.set(8, 1, 3, "target", power="0")
    r.set(8, 0, 3, "calcite")
    # the muster square where trained units appear (the four spawn cells): lapis, on the gate's axis
    for (x, z) in CENTRE:
        r.set(x, 0, z, "lapis_block")
    # lantern posts at the hall's ends, a blue banner on a chiseled post at the yard's west corner
    for x in (3, 9):
        r.set(x, 1, 3, "diorite_wall")
        r.lantern(x, 2, 3)
    r.set(0, 1, 3, "chiseled_quartz_block")
    r.set(0, 2, 3, "blue_banner", rotation="2")


def draw_l1(r):
    hall_l1(r)
    turret_l1(r)
    yard_l1(r)


# ======================================================================================================== L2
PV = 5          # the pavilions' gold capitals


def pavilion(r, x0, z0):
    """An open domed pavilion (a tempietto, 3 x 3 at x0, z0): four quartz columns with gold capitals on chiseled
    feet, an entablature with a lapis frieze, a drum of blue glass, a ring of stairs, the gold crown and its lantern,
    and a lantern hung inside over a gold sun in the floor. Open on every side: the camera sees through it, units
    walk through it."""
    for dx in (0, 1, 2):
        for dz in (0, 1, 2):
            if r.get(x0 + dx, 1, z0 + dz)[0] not in FLOWERS:     # a flower keeps the grass under it
                r.set(x0 + dx, 0, z0 + dz, "smooth_quartz")
    r.set(x0 + 1, 0, z0 + 1, "gold_block")
    for (dx, dz) in CORNERS3:
        x, z = x0 + dx, z0 + dz
        r.set(x, 0, z, "chiseled_quartz_block")
        r.set(x, 1, z, "chiseled_quartz_block")
        for y in range(2, PV):
            r.pillar(x, y, z)
        r.set(x, PV, z, "gold_block")
    r.fill(x0, PV + 1, z0, x0 + 2, PV + 1, z0 + 2, "smooth_quartz")
    r.set(x0, PV + 1, z0 + 1, "lapis_block")
    r.set(x0 + 2, PV + 1, z0 + 1, "lapis_block")
    r.set(x0 + 1, PV + 1, z0 + 2, "lapis_block")
    ring(r, x0, x0 + 2, z0, z0 + 2, PV + 2, name="smooth_quartz_stairs", half="top")
    r.set(x0 + 1, PV + 2, z0 + 1, "smooth_quartz")
    dome3(r, x0, z0, PV + 3)
    r.lantern(x0 + 1, PV, z0 + 1, hanging=True)


def cloister_l2(r):
    """The cloister: two domed pavilions on the yard's front corners and arcaded walls between them and the north
    range. Every arch is open; a walk on top, lanterns on the piers."""
    pavilion(r, 0, 5)
    pavilion(r, 7, 5)
    # the south gate on the axis (x 4..5): two piers, a two-wide arch (upside-down stairs springing from the piers),
    # a lapis lintel course and a gold parapet over it
    for x in (3, 6):
        r.set(x, 1, 7, "chiseled_quartz_block")
        r.pillar(x, 2, 7)
        r.pillar(x, 3, 7)
        r.set(x, 4, 7, "chiseled_quartz_block")
        r.set(x, 5, 7, "diorite_wall")
        r.lantern(x, 6, 7)
    r.st(4, 3, 7, "west", "top", name="smooth_quartz_stairs")
    r.st(5, 3, 7, "east", "top", name="smooth_quartz_stairs")
    for x in (4, 5):
        r.set(x, 4, 7, "lapis_block")
        r.set(x, 5, 7, "chiseled_quartz_block")
        r.set(x, 6, 7, "blue_banner", rotation="0")          # the house colours over the gate, toward the camera
    # the side arches (x 0 and x 9, z 3..4): springing from the north range and the pavilion, a walk over them
    for x in (0, 9):
        r.st(x, 3, 3, "north", "top", name="smooth_quartz_stairs")
        r.st(x, 3, 4, "south", "top", name="smooth_quartz_stairs")
        r.set(x, 4, 3, "smooth_quartz")
        r.set(x, 4, 4, "smooth_quartz")
        r.slab(x, 5, 3)
        r.slab(x, 5, 4)


def hall_l2(r):
    """The cloister's hall: the dome rises on a taller drum with a gold band, a stepped tier beside it, and the
    hall's front corners carry the capitol's pinnacles with blue banners."""
    x0 = HMID - 1
    for yy in range(DECK + 1, DECK + 5):        # the drum grows (the L1 ring and crown are walled into it)
        for dx in (0, 1, 2):
            for dz in (0, 1, 2):
                corner, mid = dx != 1 and dz != 1, dx == 1 and dz == 1
                if corner:
                    r.pillar(x0 + dx, yy, dz)
                else:
                    r.set(x0 + dx, yy, dz, "smooth_quartz" if mid else "blue_stained_glass")
    for dx in (0, 1, 2):
        for dz in (0, 1, 2):
            corner = dx != 1 and dz != 1
            r.set(x0 + dx, DECK + 5, dz, "gold_block" if corner else "chiseled_quartz_block")
    ring(r, x0, x0 + 2, 0, 2, DECK + 6)
    r.set(HMID, DECK + 6, 1, "smooth_quartz")
    r.set(HMID, DECK + 7, 1, "gold_block")
    r.set(HMID, DECK + 8, 1, "diorite_wall")
    r.lantern(HMID, DECK + 9, 1)
    # the lower tier: stairs climbing to the drum on its east and west, over the deck
    for z in (0, 1, 2):
        r.st(x0 - 1, DECK + 1, z, "east")
        r.st(x0 + 3, DECK + 1, z, "west")
    # the pinnacles on the hall's four corners: a quartz pillar, a gold cap, a blue banner (the capitol's)
    for x in PIERS:
        for z in (2, 0):
            r.pillar(x, DECK + 1, z)
            r.pillar(x, DECK + 2, z)
            r.set(x, DECK + 3, z, "gold_block")
            r.set(x, DECK + 4, z, "blue_banner", rotation="2" if x == HX0 else "14")


def draw_l2(r):
    draw_l1(r)
    cloister_l2(r)
    hall_l2(r)


# ======================================================================================================== L3
ROSE = (HMID, 11)          # the rose window's centre on the transept gable (x, y), z 2
SB, SBF = 10, 14           # the belfry spire: the gold band over the square shaft, the new belfry's floor


def lancet(r, x, z, y1, y2):
    """A tall gothic lancet: blue glass with a chiseled head."""
    for y in range(y1, y2):
        r.set(x, y, z, "blue_stained_glass")
    r.set(x, y2, z, "chiseled_quartz_block")


def pinnacle(r, x, y, z, h=2, gold=True):
    """A gothic pinnacle: a thin quartz-wall shaft, a gold knop, a glowing finial (an end rod)."""
    for yy in range(y, y + h):
        r.set(x, yy, z, "diorite_wall")
    if gold:
        r.set(x, y + h, z, "gold_block")
    r.set(x, y + h + (1 if gold else 0), z, "end_rod", facing="up")


def cathedral_l3(r):
    """The hall becomes the cathedral: clerestory and steep roof over the range, the transept gable with the ROSE
    WINDOW facing the camera, the dome walled into a crossing tower with pinnacles and a gold needle."""
    top = DECK + 6                  # the clerestory's top course
    for x in range(HX0, HX1 + 1):
        for z in (0, 1, 2):
            for y in range(DECK + 1, top + 1):
                r.set(x, y, z, "quartz_bricks")
    for x in (4, 8):
        lancet(r, x, 2, DECK + 1, top - 1)
    for x in PIERS:
        for y in range(DECK + 1, top + 1):
            r.pillar(x, y, 2)
    # the steep roof over the side bays (x 3..4 and 8..9): stairs on the two eaves, a gold ridge
    for x in (HX0, HX0 + 1, HX1 - 1, HX1):
        r.st(x, top + 1, 0, "south", name="smooth_quartz_stairs")
        r.st(x, top + 1, 2, "north", name="smooth_quartz_stairs")
        r.set(x, top + 1, 1, "smooth_quartz")
        r.set(x, top + 2, 1, "gold_block")
    # the transept (x 4..8 at z 2) rises as a gable with the rose window
    rx, ry = ROSE
    for x in range(rx - 2, rx + 3):
        for y in range(top + 1, ry + 3):
            r.set(x, y, 2, "quartz_bricks")
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) == 2 and abs(dy) == 2:
                continue
            spoke = abs(dx) + abs(dy) == 1
            r.set(rx + dx, ry + dy, 2, "lapis_block" if spoke else "blue_stained_glass")
    r.set(rx, ry, 2, "ochre_froglight", axis="z")
    for (dx, dy) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):     # the rose's frame corners
        r.set(rx + dx, ry + dy, 2, "chiseled_quartz_block")
    for x in range(rx - 2, rx + 3):           # the hood over the rose, its lip toward the yard
        r.st(x, ry + 3, 3, "north", "top", name="smooth_quartz_stairs")
    gy = ry + 3                               # the gable's raking stairs to the apex and its gold finial
    r.st(rx - 2, gy, 2, "east", name="smooth_quartz_stairs")
    r.st(rx + 2, gy, 2, "west", name="smooth_quartz_stairs")
    r.fill(rx - 1, gy, 2, rx + 1, gy, 2, "quartz_bricks")
    r.st(rx - 1, gy + 1, 2, "east", name="smooth_quartz_stairs")
    r.st(rx + 1, gy + 1, 2, "west", name="smooth_quartz_stairs")
    r.set(rx, gy + 1, 2, "quartz_bricks")
    r.set(rx, gy + 2, 2, "gold_block")
    r.set(rx, gy + 3, 2, "end_rod", facing="up")
    # the crossing tower behind the gable (x 5..7, z 0..1): the old drum walled in, pinnacles, a gold needle
    for x in (rx - 1, rx, rx + 1):
        for z in (0, 1):
            for y in range(DECK + 1, gy + 3):
                r.set(x, y, z, "quartz_bricks")
            r.set(x, gy + 3, z, "chiseled_quartz_block")
    for x in (rx - 1, rx + 1):
        r.set(x, gy + 4, 0, "gold_block")
        pinnacle(r, x, gy + 5, 0, h=1, gold=False)
    r.set(rx, gy + 4, 0, "quartz_bricks")
    r.set(rx, gy + 4, 1, "quartz_bricks")
    r.pillar(rx, gy + 5, 1)
    r.set(rx, gy + 6, 1, "gold_block")
    r.set(rx, gy + 7, 1, "end_rod", facing="up")


def buttresses_l3(r):
    """FLYING BUTTRESSES on the camera's side, one at each end of the range (x 3 and x 9, clear of the spawn columns
    x 4..5): a pier standing out in the yard (z 4) rising to a chiseled cap and a pinnacle, and the flyer across the
    gap (z 3) - an upside-down stair springing from the pier as the arch, a stair over it sloping up onto the
    clerestory as the coping."""
    for x in PIERS:
        for y in range(1, DECK + 1):
            r.set(x, y, 4, "quartz_bricks")
        r.set(x, 1, 4, "chiseled_quartz_block")
        r.set(x, DECK + 1, 4, "chiseled_quartz_block")
        pinnacle(r, x, DECK + 2, 4, h=1)
        r.st(x, DECK, 3, "south", "top", name="smooth_quartz_stairs")       # the arch, springing from the pier
        r.st(x, DECK + 1, 3, "north", name="smooth_quartz_stairs")          # the coping, rising to the wall


def spire_l3(r):
    """The bell turret grows into the BELFRY SPIRE in gothic stages: the square shaft rises (the old belfry glazed
    into lancets), a gold band, a chamfered stage (corners cut back, a pinnacle on each - the gothic set-back) with
    tall lancets, an open belfry with the bell high up under pointed arches, a ring of stairs, a gold knop and the GOLD
    ANGEL on top: the landmark an opponent spots across the map."""
    for y in range(TB - 1, SB):
        r.fill(0, y, 0, 2, y, 2, "quartz_bricks")
        for (x, z) in CORNERS3:
            r.pillar(x, y, z)
    for (x, z) in ((0, 1), (1, 2)):
        lancet(r, x, z, TB, SB - 1)
    r.fill(0, SB, 0, 2, SB, 2, "chiseled_quartz_block")
    for (x, z) in CORNERS3:
        r.set(x, SB, z, "gold_block")
    for y in range(SB + 1, SBF):
        for (x, z) in ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2)):
            r.set(x, y, z, "quartz_bricks")
    for (x, z) in ((0, 1), (1, 2)):
        lancet(r, x, z, SB + 1, SBF - 1)
    for (x, z) in CORNERS3:
        pinnacle(r, x, SB + 1, z, h=1)
    r.fill(0, SBF, 0, 2, SBF, 2, "chiseled_quartz_block")
    for (x, z) in CORNERS3:
        r.set(x, SBF, z, "gold_block")
        for yy in (SBF + 1, SBF + 2, SBF + 3):
            r.pillar(x, yy, z)
    r.st(1, SBF + 3, 0, "south", "top", name="smooth_quartz_stairs")
    r.st(1, SBF + 3, 2, "north", "top", name="smooth_quartz_stairs")
    r.st(0, SBF + 3, 1, "east", "top", name="smooth_quartz_stairs")
    r.st(2, SBF + 3, 1, "west", "top", name="smooth_quartz_stairs")
    r.set(1, SBF + 3, 1, "bell", attachment="ceiling", facing="south", powered="false")
    r.fill(0, SBF + 4, 0, 2, SBF + 4, 2, "smooth_quartz")
    ring(r, 0, 2, 0, 2, SBF + 5, name="smooth_quartz_stairs")
    r.set(1, SBF + 5, 1, "smooth_quartz")
    r.set(1, SBF + 6, 1, "gold_block")
    angel(r, 1, SBF + 7, 1, gold=True)
    r.set(1, SB + 2, 3, "blue_wall_banner", facing="south")   # a banner high on the spire, over the yard


def pavilions_l3(r):
    """The pavilions go gothic too: pinnacles on their ring's corners, the crown drawn up into a gold spirelet."""
    for (x0, z0) in ((0, 5), (7, 5)):
        cy = PV + 5                    # dome3's crown course (a drum of 1 from PV + 3)
        for (dx, dz) in CORNERS3:
            r.set(x0 + dx, PV + 4, z0 + dz, "chiseled_quartz_block")
            pinnacle(r, x0 + dx, PV + 5, z0 + dz, h=1, gold=False)
        r.set(x0 + 1, cy + 1, z0 + 1, "diorite_wall")
        r.set(x0 + 1, cy + 2, z0 + 1, "gold_block")
        r.lantern(x0 + 1, cy + 3, z0 + 1)


def draw_l3(r):
    draw_l2(r)
    cathedral_l3(r)
    buttresses_l3(r)
    spire_l3(r)
    pavilions_l3(r)


# ======================================================================================================== build
L1_SY = TB + 9
L2_SY = DECK + 10
L3_SY = SBF + 11


def build(draw, sy, tier):
    r = Rig(sy)
    draw(r)
    r.settle()
    r.check_support(tier)
    return r.structure()


def l1():
    return build(draw_l1, L1_SY, "L1")


def l2():
    return build(draw_l2, L2_SY, "L2")


def l3():
    return build(draw_l3, L3_SY, "L3")


def base():
    """The L1 the game ships as barracks.nbt (gen_lab_levels writes it, like lab_graveyard.bases())."""
    return l1()


def levels():
    """(L2, L3) for gen_lab_levels.LABS["barracks"], like lab_graveyard.levels()."""
    return l2(), l3()


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "_door", "_leaves", "_carpet")


def check_levels(s1, s2, s3, paths=None):
    """The rules the game needs, on the three finished Structures (support already ran in build())."""
    import audit_structure_pockets as ap
    levels = (("L1", s1), ("L2", s2), ("L3", s3))
    for tag, s in levels:
        assert (s.size[0], s.size[2]) == (SX, SZ), "%s: footprint %s, the Barracks' is 10 x 8" % (tag, s.size)
        n = names(s)
        assert max(p[1] for p in n) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
        bad = sorted({nm for nm in n.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        layer0 = [p for p, nm in n.items() if nm == START_TYPE and p[1] == 0]
        above = [p for p, nm in n.items() if nm == START_TYPE and p[1] > 0]
        assert len(layer0) >= 6 and not above, "%s: starting type %s: %d in layer 0, %d above" % (
            tag, START_TYPE, len(layer0), len(above))
    # the chain: an upgrade only adds or replaces (BuildingPlacement.changeStructure / refreshBlocks)
    for (ta, a), (tb, b) in ((levels[0], levels[1]), (levels[1], levels[2])):
        lost = sorted(set(names(a)) - set(names(b)))
        assert not lost, "%s -> %s would leave blocks behind: %s" % (ta, tb, lost[:8])
    # the spawn: the four central columns open from the floor to one above L1's centre height at every level
    ytop = (s1.size[1] - 1) // 2 + 1
    for tag, s in levels:
        n = names(s)
        for (x, z) in CENTRE:
            assert (x, 0, z) in n, "%s: no floor under the spawn cell %d/%d" % (tag, x, z)
            blocked = [y for y in range(1, ytop + 1) if (x, y, z) in n]
            assert not blocked, "%s: the spawn column %d/%d is blocked at %s" % (tag, x, z, blocked)
    # pockets: tools/audit_structure_pockets.py on the written files, and every spawn cell walks out at every level
    report = []
    if paths:
        for (tag, s), path in zip(levels, paths):
            one_way, ground, roof, rooms, note = ap.audit(tag, path)
            assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (
                tag, one_way[:6], ground[:6], note)
            pad = ap.Pad(ap.load_nbt(path))
            for (x, z) in CENTRE:
                start = (x, 1, z)
                seen, stack, out = {start}, [start], False
                while stack and not out:
                    c = stack.pop()
                    if c[0] < 0 or c[2] < 0 or c[0] >= SX or c[2] >= SZ:
                        out = True
                        break
                    for nb in ap.neighbours(pad, *c):
                        if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 \
                                and 1 <= nb[1] <= s.size[1] + 1:
                            seen.add(nb)
                            stack.append(nb)
                assert out, "%s: a unit spawned at %s can't walk out" % (tag, start)
            report.append("%s %dx%dx%d, %d blocks: no pockets (%d roof-only cells nothing can reach), the four spawn "
                          "cells walk out" % (tag, s.size[0], s.size[1], s.size[2], len(names(s)), len(roof)))
    return report


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/lab_barracks"
    os.makedirs(out, exist_ok=True)
    built = (l1(), l2(), l3())
    paths = [os.path.join(out, f) for f in ("barracks.nbt", "barracks_l2.nbt", "barracks_l3.nbt")]
    for s, path in zip(built, paths):
        s.write(path)
    for line in check_levels(*built, paths=paths):
        print(line)
