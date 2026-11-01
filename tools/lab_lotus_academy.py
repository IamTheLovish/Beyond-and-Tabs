#!/usr/bin/env python3
"""The Stormcallers T1 army lab, the Lotus Academy, redrawn at all three levels - L1 THE LOTUS COURT, L2 THE HALL OF
WINDS, L3 THE THOUSAND-LOTUS PAGODA - so it belongs to the family the Pagoda of a Thousand Storms (the capitol,
tools/capitol_jade_pagoda.py) and the Jade Thunder Spire (the extractor, tools/mex_storm.py) set.

lovish, Oct 19 2026: buildings must "look like they match each other so when bases get bigger they look cohesive and
beautiful"; "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out for the
opponent; ... much bigger or have cooler, more intricate designs". The old academy was a stone-brick pen with a red
front wall and a jade slab coping (9 x 6 x 9): from the RTS camera a grey box. A martial academy of the sky-mages is a
temple court: a hall where the adepts train, a gate, a yard to muster in - and as it levels, the hall stacks its roofs
into a pagoda, the way the capitol does.

THE STORMCALLERS STYLE KIT (the capitol's and the extractor's, so a Stormcaller base reads as one place):
  - main blocks: JADE (waxed oxidized cut copper + stairs / slabs - waxed, so it never weathers mid-match) and DARK OAK
    (planks, logs, stairs, slabs, fences); RED LACQUER posts (stripped mangrove logs) and RED PAPER screens (red
    stained glass); the court is local stone (stone, chiseled stone bricks, stone-brick stairs / walls / slabs);
  - trim: GOLD - column capitals, every eave corner under an upturned jade slab tip, the ridge ends, the name tablet;
  - light: PAPER LANTERNS - on stone lanterns (tooro) in the court, on the roof bands; the glowing heart of the lotus;
  - silhouette motif: CURVED PAGODA ROOFS - a jade stair lip that sags in the middle and lifts a block next to each
    gold corner, a flat band, a second stair course climbing to the storey above; each roof a ring narrower;
  - signature detail: HANGING CHIMES - a chain with a glowing end-rod striker under every eave corner;
  - accent: AMETHYST, the storm-made crystal (the extractor's ore, the capitol's halo) - here it grows in the lotus;
  - the ground: moss, a blossom tree (dark oak, cherry leaves) with petals, as in the capitol's garden.
  THE ACADEMY'S OWN FIGURE: THE LOTUS - a sculpted lotus (cherry-stair petals opening round a pearlescent heart on jade
  pads) on the court's south-west corner, the corner the camera meets first; a lotus mosaic in the floor where the
  trained units appear; at L3 a gilded LOTUS CROWN on top of the pagoda, its rod the tallest point.

NOV 1 2026 REDRAW (blind massing, eyes 41292980809, two fresh critics 6 / 6 for the two-eave hall vs 4 / 4 shipped;
tests tools/test_lab_lotus_academy.py): the L1 hall is double-eaved (the old L2 hall), the LOTUS is its crown on the
upper ridge (not a sculpture in the yard), the court is ONE quiet stone (no mosaic, blossom tree, target, petals)
under the hall's frame - a two-wide roofed gallery with spear racks down each side - and every roof corner is jade
(gold corners read as studs / dots; gold is the lotus's alone). L2 adds the gate roof, the guardians and the third
roof (the lotus opens on it); L3 the fourth roof and the crown. No amethyst and no banners (team-like hues). Where the
text below disagrees, this paragraph wins.

THE PLAN (9 x 9, the shipped footprint). The RTS camera looks from the south-west: it sees the roofs, the west face
(x = 0) and the south face (z = 8). So the hall stands across the north (z 0..3) and faces south over the court
(z 4..8) toward the camera; the gate is on the south edge on the axis, the lotus on the south-west corner.
  L1 THE LOTUS COURT (9 x 9 x 9, 320 blocks)
  - the HALL (body x 1..7, z 0..1) on a one-step stone podium (z 0..2): red lacquer posts, a dark oak dado and red paper
    screens, a gold name tablet over the middle; a VERANDAH (z 2) of four red columns with gold capitals and a dark oak
    architrave, reached by a stone-brick step (z 3) the whole width; over it ONE CURVED JADE ROOF (lip x 0..8, z 0..3)
    with gold corners, upturned tips, a jade band, a second course, a ridge with gold "owl-tail" ends and lightning
    rods, dark oak rafter ends under the front lip, and a chime under each corner;
  - the GATE: a red torii on the south edge (posts x 2 and 6, a three-wide way out on the axis) with a gold tablet and
    a black kasagi with swept-up ends, a striker hung under each end;
  - the COURT: polished andesite inside a stone-brick border (the two starting types, both only in layer 0), a jade
    path from the gate to a LOTUS MOSAIC (a gold heart, cherry petals, jade leaves) under the spawn cell; THE LOTUS on
    the south-west corner (cherry-stair petals opening round a glowing pearlescent heart, on jade pads); stone lanterns
    on the south corners and the west edge; a blossom tree on moss with petals in the south-east; an archery target on
    a post and spear racks (lightning rods on fences) in the hall's open end bays - the academy trains.
  L2 THE HALL OF WINDS (9 x 15 x 9, 435 blocks) - the hall becomes double-eaved, the torii a roofed gate:
  - an UPPER STOREY (x 2..6, z 0..1) rises off the first roof's band (red posts, paper screens), a gallery in front of
    it (z 2) between two red columns standing on the first roof's gold ridge ends; a SECOND CURVED ROOF (lip x 1..7,
    z 0..3) with its own gold corners, chimes and gold ridge ends carrying two-block rods;
  - the torii's beam becomes a curved JADE GATE ROOF (gold corners and upturned tips over the posts, a slab ridge, a
    lantern on a gold boss; the black kasagi ends still peek out past it) and two JADE GUARDIANS (the capitol's
    crouching jade beasts with dragon heads) stand under those ends looking out at the camera;
  - red house banners hang on the hall's end posts; amethyst (the storm's crystal) grows out of the lotus's back pads
    and a lightning rod stands in its heart; lanterns stand on the first roof's band at its ends.
  L3 THE THOUSAND-LOTUS PAGODA (9 x 23 x 9, 529 blocks) - the hall's middle rises into a pagoda:
  - two more storeys and curved roofs climb out of the second roof's ridge, each a ring narrower (lips x 2..6 then
    x 3..5, stepping back north as a temple climbs its hill), so FOUR curved roofs stack over the court, each with gold
    corners and chimes, rods on the third roof's front tips;
  - the LOTUS CROWN on top: a gold boss, cherry-stair petals opening round a pearlescent heart with cherry slab petals
    lying open between them, a gold seed head and a two-block lightning rod - the landmark an opponent spots across the
    map, the academy's name in blocks;
  - the gate roof grows a second, narrower tier (gold corners, a jade course, the lantern lifted onto it, rods on the
    lower tier's tips); the muster square's gold heart becomes a pearlescent froglight under the spawn.

GAME RULES (asserted in check_levels):
  - every level keeps the 9 x 9 footprint (BuildingLevels has no larger footprint); a level grows taller;
  - the chain: every non-air cell of L1 is non-air in L2, and of L2 in L3 (changeStructure only adds or replaces):
    L2 is drawn over L1 and L3 over L2, and that is asserted;
  - LotusAcademy.startingBlockTypes = polished_andesite, stone_bricks: both in layer 0, nowhere above;
  - the unit spawn: ProductionBuilding.getIndoorSpawnPoint walks down from centrePos, the L1 box's centre. On this odd
    footprint (int)((minX + maxX) / 2f) is exactly min + 4 whatever the sign or the rotation, so the spawn column is
    the cell x 4, z 4, at y = L1 maxY / 2; changeStructure never moves it. That column is open from the floor to the sky
    at every level (nothing of any roof stands on it: the roofs' front lips stop at z 3), and the spawn cell walks out;
  - nothing pops off (lanterns on full blocks / walls / fences, chimes hang from end rods that need nothing, banners on
    walls, amethyst on a full face, petals on moss, dragon heads standing); no pockets (tools/audit_structure_pockets.py
    on all three); no water, lava, gravity blocks, redstone, campfires, doors, carpets; no part-height block (candle,
    pickle, carpet) on a walkable step - the lights in the court stand on stone-lantern posts units don't path over.

Usage: python3 tools/lab_lotus_academy.py [OUT_DIR]   (writes lotus_academy.nbt, lotus_academy_l2.nbt,
       lotus_academy_l3.nbt and checks them; default OUT_DIR: the scratch folder - shipping them is the coordinator's)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

SX = SZ = 9
C = 4
CENTRE = [(4, 4)]                          # the one spawn column (see the docstring)
START_TYPES = ("polished_andesite", "stone_bricks")   # LotusAcademy.startingBlockTypes
SHIPPED_L1_BLOCKS = 181                    # the old lotus_academy.nbt's non-air count (LotusAcademy.buildTimeBlocks)

DRY = dict(waterlogged="false")
JADE, JADE_ST, JADE_SLAB = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"
OAK, OAK_LOG, OAK_ST, OAK_SLAB, OAK_FENCE = "dark_oak_planks", "dark_oak_log", "dark_oak_stairs", "dark_oak_slab", "dark_oak_fence"
RED = "stripped_mangrove_log"              # red lacquer
PAPER = "red_stained_glass"                # paper screens
GOLD = "gold_block"
SB_ST, SB_SLAB, SB_WALL = "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"
PETAL_ST, PETAL_SLAB = "cherry_stairs", "cherry_slab"
OPP = gs._SUN_OPP


class Rig(gs._SunRig):
    """The kit's canvas (gen_structures._SunRig: settle() shapes stairs and joins walls / fences like the game) with
    the support check this lab needs."""

    def __init__(self, sy):
        super().__init__(SX, sy, SZ)

    def st(self, x, y, z, facing, half="bottom", name=JADE_ST):
        self.stair(x, y, z, facing, half, name)

    def slab(self, x, y, z, name=JADE_SLAB, typ="bottom"):
        self.set(x, y, z, name, type=typ, **DRY)

    def fill(self, x1, y1, z1, x2, y2, z2, name, **p):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **p)

    def red(self, x, y, z):
        self.set(x, y, z, RED, axis="y")

    def lantern(self, x, y, z, hanging=False):
        self.set(x, y, z, "lantern", hanging="true" if hanging else "false", **DRY)

    def rod(self, x, y, z, facing="up"):
        self.set(x, y, z, "lightning_rod", facing=facing, powered="false", **DRY)

    def leaves(self):
        """Cherry leaves carry their true distance to a log (vanilla recomputes it on every neighbour update and RoN's
        isPlaced compares states: a wrong distance reads as a missing block)."""
        dist, frontier = {}, [p for p, (n, _) in self.b.items() if n.endswith("_log")]
        for p in frontier:
            dist[p] = 0
        d = 0
        while frontier and d < 7:
            d += 1
            nxt = []
            for (x, y, z) in frontier:
                for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                    q = (x + dx, y + dy, z + dz)
                    if q not in dist and self.b.get(q, ("air", {}))[0].endswith("_leaves"):
                        dist[q] = d
                        nxt.append(q)
            frontier = nxt
        for p, (n, props) in list(self.b.items()):
            if n.endswith("_leaves"):
                assert dist.get(p, 7) < 7, "a leaf too far from its trunk at %s" % (p,)
                self.b[p] = (n, dict(distance=str(dist[p]), persistent="true", **DRY))

    def check_support(self, tier):
        bad = []
        for pos, (n, p) in self.b.items():
            below, above = self.nb(pos, "down"), self.nb(pos, "up")
            if n in ("lantern", "soul_lantern"):
                ok = (gs._sun_sturdy(above, "down") or above[0] == "chain") if p.get("hanging") == "true" \
                    else gs._sun_centre_up(below)
            elif n.endswith("_wall_banner"):
                behind = self.nb(pos, OPP[p["facing"]])
                ok = gs._sun_solid(behind) and not gs._sun_is(behind, *gs._SUN_THIN)
            elif n.endswith("_banner"):
                ok = gs._sun_solid(below)
            elif n in ("amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud"):
                f = p["facing"]               # vanilla AmethystClusterBlock: a full face on the block it grows from
                ok = gs._sun_sturdy(self.nb(pos, OPP[f]), f)
            elif n == "pink_petals":
                ok = below[0] in ("grass_block", "dirt", "moss_block", "podzol", "coarse_dirt")
            elif n.endswith(("_head", "_skull")) and "wall" not in n:
                ok = below[0] != "air"
            elif n.endswith(("torch", "_button", "ladder", "_carpet", "candle", "bell", "_door")):
                ok = False                    # none are drawn on this lab
            else:
                continue
            if not ok:
                bad.append((pos, n))
        assert not bad, "lotus academy %s: would pop off / hang: %s" % (tier, sorted(bad))


# ======================================================================================================== shapes
def curved_roof(r, x1, x2, z1, z2, y, chimes, flush_north=True):
    """The kit's curved pagoda roof with its lip on the rectangle [x1..x2] x [z1..z2] at y (the capitol's roof(), drawn
    on a rectangle): along each side a jade stair, low edge out; the cells next to a corner lift a block (jade + a slab
    on it) so the eave sweeps up; the corner itself a gold block under an upturned jade slab tip. The north side is
    flush with the footprint (nobody sees it), so its lip is a plain jade course. Inside the lip a flat jade band.
    Returns the corner cells for chimes."""
    corners = [(x1, z1), (x2, z1), (x1, z2), (x2, z2)]
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge_x, edge_z = x in (x1, x2), z in (z1, z2)
            if not (edge_x or edge_z):
                r.set(x, y, z, JADE)          # the band
                continue
            if (x, z) in corners:              # jade, not gold: gold corners read as "studs" / "dots" (Nov 1)
                r.set(x, y, z, JADE)
                r.slab(x, y + 1, z)
                continue
            near = (edge_z and x in (x1 + 1, x2 - 1)) or (edge_x and z in (z1 + 1, z2 - 1))
            if z == z1 and flush_north:
                r.set(x, y, z, JADE)
                continue
            if near and (x2 - x1 >= 4):
                r.set(x, y, z, JADE)
                r.slab(x, y + 1, z)
                continue
            f = "north" if z == z2 else "south" if z == z1 else "east" if x == x1 else "west"
            r.st(x, y, z, f)
    for (x, z) in corners:
        chimes.append((x, y - 1, z))


def hang_chimes(r, chimes):
    """The kit's signature under every eave corner: a chain with a glowing end-rod striker where two cells are free,
    else the striker alone. Placed last, only into air, never lower than y 3 over a walkable cell."""
    for (x, y, z) in chimes:
        if r.get(x, y, z)[0] != "air":
            continue
        if y - 1 >= 4 and r.get(x, y - 1, z)[0] == "air" and r.get(x, y - 2, z)[0] == "air":
            r.set(x, y, z, "chain", axis="y", **DRY)
            r.set(x, y - 1, z, "end_rod", facing="down")
        else:
            r.set(x, y, z, "end_rod", facing="down")


def stone_lantern(r, x, z):
    """A tooro (the capitol's): a stone-brick wall post, a paper lantern, a slab hood."""
    r.set(x, 1, z, SB_WALL)
    r.lantern(x, 2, z)
    r.slab(x, 3, z, SB_SLAB)


def guardian(r, x, z, face):
    """The capitol's jade guardian, small: a chiseled plinth, a crouching jade body, a dragon's head looking `face`."""
    rot = {"south": "0", "west": "4", "north": "8", "east": "12"}[face]
    r.set(x, 1, z, "chiseled_stone_bricks")
    r.st(x, 2, z, OPP[face])
    r.set(x, 3, z, "dragon_head", rotation=rot)


# ======================================================================================================== L1
FLOOR = 1          # the podium's top course (the verandah floor)
CAP = 4            # the gold capitals / the architrave
R1 = 5             # the first roof's lip
COLS = (1, 3, 5, 7)


def court_l1(r):
    """Layer 0 and the court: ONE quiet stone (critics a / b on the blind massing, eyes 41292980809: the shipped mosaic,
    blossom tree, lotus sculpture and target read as "a chequer", "a literal bullseye", "four accents in the yard"),
    under the hall's frame: a roofed gallery down each side, the torii on the axis."""
    for x in range(SX):
        for z in range(SZ):
            edge = x in (0, SX - 1) or z in (0, SZ - 1)
            r.set(x, 0, z, "stone_bricks" if edge else "polished_andesite")
    # the gate: a red torii on the axis, a three-wide way out
    for x in (2, 6):
        for y in (1, 2, 3):
            r.red(x, y, 8)
    for x in (3, C, 5):
        r.slab(x, 3, 8, "mangrove_slab", "top")
    for x in range(2, 7):
        r.slab(x, 4, 8, OAK_SLAB, "top")
    r.st(1, 4, 8, "west", name=OAK_ST)                     # the kasagi's swept-up ends
    r.st(7, 4, 8, "east", name=OAK_ST)
    stone_lantern(r, 0, 8)
    stone_lantern(r, 8, 8)
    galleries(r)


GALLERY = (4, 7)   # the side galleries' rows (z), under the hall's first roof's line


def galleries(r):
    """A roofed gallery down each side of the court (x 0..1 and 7..8, z 4..7): red posts on the outer edge, a spear
    rack (lightning rods on dark oak fences) between them - the academy trains - and a jade roof at y 4 whose lip
    runs two cells into the court, so from the camera the court sits inside the hall's frame."""
    for x0, out in ((0, 0), (SX - 2, SX - 1)):
        for z in GALLERY:
            for y in (1, 2, 3):
                r.red(out, y, z)
        for z in range(GALLERY[0] + 1, GALLERY[1]):
            r.set(out, 1, z, OAK_FENCE)
            r.rod(out, 2, z)
        for z in range(GALLERY[0], GALLERY[1] + 1):
            r.set(out, 3, z, OAK)
        for z in range(GALLERY[0], GALLERY[1] + 1):
            mid, lip = (1, 2) if out == 0 else (SX - 2, SX - 3)
            r.set(out, 4, z, JADE)
            r.set(mid, 4, z, JADE)
            r.st(lip, 4, z, "east" if out == 0 else "west")
        for z in (GALLERY[0], GALLERY[1]):
            r.slab(out, 5, z)                              # the eave's lifted ends


def hall_l1(r):
    """The hall on its podium, the verandah, the first curved roof."""
    # the podium: one course of stone (not the starting types: those stay in layer 0) with chiseled corners; the
    # verandah floor of dark oak on its front row; a stone-brick step the whole width in front of it
    r.fill(0, FLOOR, 0, 8, FLOOR, 1, "stone")
    r.fill(0, FLOOR, 2, 8, FLOOR, 2, OAK)
    for x in (0, 8):
        r.set(x, FLOOR, 0, "chiseled_stone_bricks")
        r.set(x, FLOOR, 2, "chiseled_stone_bricks")
    for x in range(SX):
        r.st(x, FLOOR, 3, "north", name=SB_ST)
    # the body (x 1..7, z 0..1): the back wall dark oak between red posts, the front a dark oak dado and red paper
    # screens between red posts, a gold name tablet over the middle
    for y in range(FLOOR + 1, CAP + 1):
        for x in range(1, 8):
            r.set(x, y, 0, OAK)
            post = x in (1, 4, 7)
            if post:
                r.red(x, y, 1)
            elif y == FLOOR + 1:
                r.set(x, y, 1, OAK)
            elif y == CAP:
                r.set(x, y, 1, OAK)
            else:
                r.set(x, y, 1, PAPER)
        r.red(1, y, 0)
        r.red(7, y, 0)
    r.set(C, CAP, 1, GOLD)
    # the verandah: red columns with gold capitals, a dark oak architrave between them (a top slab: it reads thin)
    for x in COLS:
        for y in range(FLOOR + 1, CAP):
            r.red(x, y, 2)
        r.set(x, CAP, 2, GOLD)
    for x in (2, 4, 6):
        r.slab(x, CAP, 2, OAK_SLAB, "top")
    # spear racks in the hall's open end bays: lightning-rod spears on dark oak fence posts
    for x in (0, 8):
        for z in (0, 1):
            r.set(x, FLOOR + 1, z, OAK_FENCE)
            r.rod(x, FLOOR + 2, z)
    chimes = []
    roof1(r, chimes)
    return chimes


def roof1(r, chimes):
    curved_roof(r, 0, 8, 0, 3, R1, chimes)
    # round 4: a DARK HEM - the first roof's eave stairs in dark oak (its corners and lifted tips stay jade), so the
    # outer eave line has a value of its own against the lawn (critics a / b, rounds 1-3: "the jade sits in the
    # lawn's band", "no dark hem at the eave edge")
    for x in range(SX):
        for z in range(4):
            if r.get(x, R1, z)[0] == JADE_ST:
                q = r.b[(x, R1, z)][1]
                r.st(x, R1, z, q["facing"], q.get("half", "bottom"), OAK_ST)
    # rafter ends under the front lip, every other cell
    for x in (2, 4, 6):
        r.st(x, R1 - 1, 3, "north", "top", OAK_ST)
    # the second course climbing onto the band, the ridge with gold ends and rods
    for x in range(2, 7):
        r.st(x, R1 + 1, 2, "north")
        r.st(x, R1 + 1, 1, "south")
    for z in (1, 2):
        r.st(1, R1 + 1, z, "east")
        r.st(7, R1 + 1, z, "west")
    for x in range(3, 6):
        r.slab(x, R1 + 2, 1)
        r.slab(x, R1 + 2, 2)
    for x in (2, 6):                       # the ridge ends: a gold "owl tail" toward the camera, a rod on it
        r.set(x, R1 + 2, 1, JADE)
        r.set(x, R1 + 2, 2, JADE)          # jade, not gold (Nov 1: one gold leader, the lotus)
        r.rod(x, R1 + 3, 2)


def draw_l1(r):
    """THE LOTUS COURT (Nov 1): the hall double-eaved already at L1 (the blind massing winner), the lotus its crown."""
    court_l1(r)
    chimes = hall_l1(r)
    hall_l2(r, chimes)
    lotus_crown(r, R2 + 2)
    chimes += [(1, 3, 8), (7, 3, 8)]        # strikers under the torii's swept-up ends (L2's guardians replace them)
    return chimes


def lotus_crown(r, y, x0=C, z0=1):
    """THE LOTUS on the ridge, where the camera looks: a gold boss, cherry-stair petals opening round a pearlescent
    heart, cherry slab petals lying open between them, a gold seed head and the storm's rod."""
    r.set(x0, y, z0, GOLD)
    r.set(x0, y + 1, z0, "pearlescent_froglight", axis="y")
    for (dx, dz), f in (((1, 0), "east"), ((-1, 0), "west"), ((0, 1), "south"), ((0, -1), "north")):
        r.st(x0 + dx, y + 1, z0 + dz, f, name=PETAL_ST)
    for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        r.slab(x0 + dx, y + 1, z0 + dz, PETAL_SLAB)
    r.set(x0, y + 2, z0, GOLD)
    r.rod(x0, y + 3, z0)
    for dx in (-1, 0, 1):                  # a jade boss under the open petals (nothing floats)
        for dz in (-1, 0, 1):
            if r.get(x0 + dx, y, z0 + dz)[0] == "air":
                r.set(x0 + dx, y, z0 + dz, JADE)
    # round 4: the petal TIPS reach one cell past the square on the camera's two sides (west, south), so the outline
    # is a flower, not "a pink square with a gold dot" (critics a / b, rounds 1-3)
    for (dx, dz), f in (((-2, 0), "west"), ((0, 2), "south")):
        if r.get(x0 + dx, y + 1, z0 + dz)[0] == "air":
            r.st(x0 + dx, y + 1, z0 + dz, f, "top", PETAL_ST)


# ======================================================================================================== L2
R2 = 10            # the second roof's lip


def hall_l2(r, chimes):
    """The upper storey and the second curved roof: the hall becomes double-eaved."""
    # the storey (x 2..6, z 0..1) off the first roof's band: the old ridge and course are walled in
    for y in range(R1 + 1, R2):
        for x in range(2, 7):
            r.set(x, y, 0, OAK)
            if x in (2, 4, 6):
                r.red(x, y, 1)
            else:
                r.set(x, y, 1, PAPER if y in (R1 + 2, R1 + 3) else OAK)
    # the gallery in front of it: the old ridge's slabs are its sill (jade), the gold ridge ends carry two red columns
    for x in (3, 4, 5):
        r.slab(x, R1 + 2, 2)
    for x in (2, 6):
        r.set(x, R1 + 2, 2, JADE)
        for y in range(R1 + 3, R2):
            r.red(x, y, 2)
    curved_roof(r, 1, 7, 0, 3, R2, chimes)
    for x in (3, 5):
        r.st(x, R2 - 1, 3, "north", "top", OAK_ST)       # rafter ends
    # its course and ridge: gold "owl-tail" ends with rods, a jade ridge between
    for x in range(3, 6):
        r.st(x, R2 + 1, 2, "north")
        r.st(x, R2 + 1, 1, "south")
    for z in (1, 2):
        r.st(2, R2 + 1, z, "east")
        r.st(6, R2 + 1, z, "west")
    for x in (3, 5):
        r.set(x, R2 + 2, 1, JADE)
        r.set(x, R2 + 2, 2, JADE)
    # lanterns on the first roof's band at the ends, like lamps set out on a balcony
    for x in (1, 7):
        r.lantern(x, R1 + 1, 2) if r.get(x, R1, 2)[0] == JADE else None


def gate_l2(r, chimes):
    """The torii's black beam becomes a curved jade gate roof (one row: a stair sloping toward the camera under a slab
    ridge, gold corners with upturned tips over the swept-up ends, chimes, a lantern on a gold boss); two jade
    guardians flank it inside the court, looking out of the gate."""
    for x in (2, 6):                               # gold corners over the posts; the kasagi's black swept-up
        r.set(x, 4, 8, GOLD)                       # ends still peek out past them
        r.slab(x, 5, 8)
    for x in (3, 4, 5):
        r.st(x, 4, 8, "north")
        r.slab(x, 5, 8)
    r.set(C, 5, 8, GOLD)
    r.lantern(C, 6, 8)
    guardian(r, 1, 8, "south")                     # under the kasagi's ends, looking out at the camera
    guardian(r, 7, 8, "south")


def draw_l2(r):
    chimes = draw_l1(r)
    gate_l2(r, chimes)
    storey3(r, chimes)
    return chimes


# ======================================================================================================== L3
R3, R4 = 14, 17


def storey3(r, chimes):
    """L2 (Nov 1: the hall is double-eaved from L1): the third storey and roof rise out of the hall's ridge through
    the L1 lotus, which opens again on the third roof."""
    # storey 3 (x 3..5, z 0..2) off the second roof's ridge
    for y in range(R2 + 1, R3):
        for x in (3, 4, 5):
            for z in (0, 1, 2):
                if z == 2 and x != 4:
                    r.red(x, y, z)
                elif z == 2:
                    r.set(x, y, z, PAPER if y < R3 - 1 else OAK)
                else:
                    r.set(x, y, z, OAK)
    curved_roof(r, 2, 6, 0, 3, R3, chimes)
    for (x, z) in ((2, 3), (6, 3)):        # lightning rods on the third roof's front tips (the storm caught everywhere)
        r.rod(x, R3 + 2, z)
    lotus_crown(r, R3 + 1)                 # the lotus climbs onto the new top (L3's fourth storey grows through it)


def pagoda_l3(r, chimes):
    """L3: the fourth storey and roof, a ring narrower, and the LOTUS CROWN on top."""
    # storey 4: a red core (x 4, z 1..2) under the top roof
    for y in range(R3 + 1, R4):
        r.red(4, y, 1)
        r.set(4, y, 2, PAPER)
        r.set(3, y, 1, OAK)
        r.set(5, y, 1, OAK)
        r.set(4, y, 0, OAK)
    curved_roof(r, 3, 5, 0, 2, R4, chimes, flush_north=False)
    # the LOTUS CROWN: a gold boss, cherry petals opening round a pearlescent heart, gold inner petals, the rod
    x0, z0 = 4, 1
    y = R4 + 1
    r.set(x0, y, z0, GOLD)
    r.set(x0, y + 1, z0, "pearlescent_froglight", axis="y")
    for (dx, dz), f in (((1, 0), "east"), ((-1, 0), "west"), ((0, 1), "south"), ((0, -1), "north")):
        r.st(x0 + dx, y + 1, z0 + dz, f, name=PETAL_ST)
    for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        r.slab(x0 + dx, y + 1, z0 + dz, PETAL_SLAB)     # the outer petals lying open between them
    r.set(x0, y + 2, z0, GOLD)                          # the seed head
    r.rod(x0, y + 3, z0)
    r.rod(x0, y + 4, z0)


def gate_l3(r, chimes):
    """The gate roof grows a second, narrower tier: gold corners over the first ridge's ends, a jade stair course, the
    lantern lifted onto its own gold boss."""
    for x in (3, 5):
        r.set(x, 5, 8, GOLD)
        r.slab(x, 6, 8)
    r.set(C, 5, 8, JADE)
    r.set(C, 6, 8, GOLD)
    r.lantern(C, 7, 8)
    for x in (2, 6):
        r.rod(x, 6, 8)                             # rods on the lower tier's corner tips


def court_l3(r):
    """The muster square lights up: the lotus mosaic's gold heart becomes a pearlescent froglight under the spawn."""
    r.set(C, 0, 4, "pearlescent_froglight", axis="y")


def draw_l3(r):
    chimes = draw_l2(r)
    pagoda_l3(r, chimes)
    gate_l3(r, chimes)
    court_l3(r)
    return chimes


# ======================================================================================================== build
L1_SY = L2_SY = L3_SY = 32       # drawing room; build() crops the box to the top block


def build(draw, sy, tier):
    r = Rig(sy)
    chimes = draw(r)
    hang_chimes(r, chimes)
    r.leaves()
    r.settle()
    r.check_support(tier)
    r.size = (SX, max(p[1] for p in r.b) + 1, SZ)       # the box ends at the top block (the rod's tip)
    return r.structure()


def l1():
    return build(draw_l1, L1_SY, "L1")


def l2():
    return build(draw_l2, L2_SY, "L2")


def l3():
    return build(draw_l3, L3_SY, "L3")


def base():
    """The L1 the game ships as lotus_academy.nbt."""
    return l1()


def levels():
    """(L2, L3) for gen_lab_levels.LABS["lotus_academy"]."""
    return l2(), l3()


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "_door", "_carpet", "candle", "sea_pickle", "magma_block")


def check_levels(s1, s2, s3, paths=None):
    """The rules the game needs, on the three finished Structures (support already ran in build())."""
    import audit_structure_pockets as ap
    levels = (("L1", s1), ("L2", s2), ("L3", s3))
    for tag, s in levels:
        assert (s.size[0], s.size[2]) == (SX, SZ), "%s: footprint %s, the academy's is 9 x 9" % (tag, s.size)
        n = names(s)
        assert max(p[1] for p in n) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
        bad = sorted({nm for nm in n.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        for t in START_TYPES:
            layer0 = [p for p, nm in n.items() if nm == t and p[1] == 0]
            above = [p for p, nm in n.items() if nm == t and p[1] > 0]
            assert len(layer0) >= 6 and not above, "%s: starting type %s: %d in layer 0, %d above" % (
                tag, t, len(layer0), len(above))
    # the chain: an upgrade only adds or replaces (BuildingPlacement.changeStructure / refreshBlocks)
    for (ta, a), (tb, b) in ((levels[0], levels[1]), (levels[1], levels[2])):
        lost = sorted(set(names(a)) - set(names(b)))
        assert not lost, "%s -> %s would leave blocks behind: %s" % (ta, tb, lost[:8])
    # the spawn: the centre column open from the floor to the sky at every level, and the walk down from the L1
    # centre height lands on the floor (y 1)
    ys = (s1.size[1] - 1) // 2
    for tag, s in levels:
        n = names(s)
        for (x, z) in CENTRE:
            assert (x, 0, z) in n, "%s: no floor under the spawn cell" % tag
            blocked = [y for y in range(1, s.size[1]) if (x, y, z) in n]
            assert not blocked, "%s: the spawn column %d/%d is blocked at %s" % (tag, x, z, blocked)
            y = ys
            while y > 0 and (x, y - 1, z) not in n:
                y -= 1
            assert y == 1, "%s: the spawn walks down to y %d" % (tag, y)
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
            report.append("%s %dx%dx%d, %d blocks: no pockets (%d roof-only cells nothing can reach), the spawn cell "
                          "walks out" % (tag, s.size[0], s.size[1], s.size[2], len(names(s)), len(roof)))
    return report


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/lab_lotus"
    os.makedirs(out, exist_ok=True)
    built = (l1(), l2(), l3())
    paths = [os.path.join(out, f) for f in ("lotus_academy.nbt", "lotus_academy_l2.nbt", "lotus_academy_l3.nbt")]
    for s, path in zip(built, paths):
        s.write(path)
    for line in check_levels(*built, paths=paths):
        print(line)
