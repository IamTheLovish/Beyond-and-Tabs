#!/usr/bin/env python3
"""The Brassworks capitol, the Foundry Hall, redrawn as THE GRAND FOUNDRY - the centrepiece of the base.

lovish, Oct 19 2026: capitols "can be a lot bigger than the current capital size, a lot taller, a lot more detailed",
"I want it to be the center piece of the base", everything "connects" to it with "fluidity", "don't break the game".
Detailed = fine-grained (stairs, slabs, walls, chains, bars), not chunky blocks. The bar is the Sunforged Temple of the
Sun (tools/capitol_town_centre.py build_grand) and the Gravebound Necropolis Cathedral (tools/capitol_mausoleum.py).

THE BRASSWORKS STYLE KIT (every Brassworks building follows it; the capitol is its fullest expression):
  - main blocks: COPPER in its weathering stages (waxed cut / exposed / weathered / oxidized cut copper, their stairs
    and slabs; bright waxed copper blocks for the gear rims - allowed in a capitol, it is not an extractor) and DARK OAK
    (planks, stripped logs for the timber frame, stairs, fences); soot (polished blackstone) as the plinth and stacks;
  - trim: IRON - iron bars, chains, iron blocks (the gear teeth, the truss ties, the catwalk rails);
  - light: LANTERNS and GLOWING FURNACE MOUTHS (lit blast furnaces and smokers) and the clock's glowing hub;
  - silhouette motif: PIPES EVERYWHERE - copper (lightning-rod) and soot (blackstone-wall) pipe runs with stair and
    slab elbows, climbing the walls, crossing the roof between the stacks;
  - signature detail: SMOKE - a lit campfire on top of every chimney stack (only on stack tops, where no unit walks);
  - family figure: the IRON GOLEM DRILL's automaton (tools/mex_brass.py) - four small brass golems guard the yard
    corners with their furnace bellies glowing, so the capitol and the extractors read as one family;
  - edge: a works yard of cobbles, rails, crates and coal heaps that breaks up into the ground, the same on every side.

THE DESIGN (inspiration only: the "gothicpunk pipeworks" reference (109) - a steampunk dwarf-engineer guild hall):
  - a soot PLINTH stepped all round (ring 6) under a 9 x 9 HALL (ring 4) that is the same on every side (players build
    in every direction): each face runs tower - furnace bay - buttress - PORTAL - buttress - furnace bay - tower;
    the buttresses (ring 5) are dark-oak piers with copper caps and iron tie chains, the bays hold a lit blast furnace
    at ground level and a tall furnace window (orange glass over a glowing core) above it, so three depths show on
    every face (pier, wall, recessed window);
  - the PORTALS (3 wide, an arched dark-oak head with a copper keystone) open into the NAVE - a tall open hall with
    dark-oak columns and IRON TRUSSES (iron-bar ties, chains) under the roof - and up into the clock tower's shaft,
    so the centre spawn stands under a cathedral-like volume and walks out of any of four portals;
  - four CORNER TOWERS (2 x 2) of copper and dark oak, each carrying a SMOKESTACK of a different height (the
    tallest at the back, the shortest at the front corner the camera sees first, so the stacks step up behind the
    clock), copper bands, a campfire smoking on every top; one tower carries a CRANE jib with a chain and hook;
  - the roof: a copper cornice of upside-down stairs, a weathered-copper hip climbing to a CATWALK of iron bars round
    the CLOCK TOWER, soot PIPE RUNS crossing the roof from the stacks to the tower;
  - the CLOCK TOWER (5 x 5, ring 2): dark-oak corner posts, oxidized copper walls and on every face a GREAT GEAR
    (a bright copper rim, iron teeth, a glowing hub and an iron hand), then a riveted copper DOME (stair rings) with
    a lantern lantern-house and a lightning-rod spire;
  - the YARD (ring 7): cobbles, rails, crates, coal heaps and lantern posts breaking up into coarse dirt, with a brass
    automaton guarding each corner.

WHAT THE SIZE TOUCHES IN THE GAME (the coordinator applies these; this file edits nothing):
  - tools/gen_structures.py: foundry_hall() returns this build() (as the Temple and the Cathedral are wired), so both
    structure trees get the 15 x 22 x 15 hall;
  - FoundryHall.buildTimeBlocks stays 428 (BuildingPlacement times construction by it, not the block count); the
    starting types (POLISHED_BLACKSTONE, DEEPSLATE_BRICKS) stay and sit in layer 0 only; foundationYLayers stays 1;
  - BuildingAmbienceClientEvents.CAPITOL_SPOTS gains "foundry_hall" -> SPOTS below (steam off the pressure dome,
    embers at the four furnace mouths on the camera's faces); the stacks smoke from their own campfires;
  - StartAreaClearing.capitolSpan measures the blueprints (15 already, the Temple's) - nothing to change but its
    comment; no GameTest pins the Foundry Hall's box (SkirmishGameTests' pocket test stamps it - it fits the 40 cap);
  - CapitolLife: none (no moving part).

RULES (asserted in check()): the box; the starting types (FoundryHall.startingBlockTypes: polished_blackstone,
deepslate_bricks) in layer 0 and nowhere above; nothing pops off (_SunRig.check_support); no water, lava, gravity
blocks, redstone; campfires only on stack tops no unit can reach; under 2000 blocks; the centre spawn
(ProductionBuilding.getIndoorSpawnPoint: the box centre walked down) lands on the nave floor and walks out; no pockets
(tools/audit_structure_pockets.py audit() on the written nbt: __main__ does).

Usage: python3 tools/capitol_foundry_hall.py [OUT.nbt]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import capitol_town_centre as tc   # noqa: E402  - the Cap canvas (a _SunRig addressed from the centre, 4-fold sides)

R, H = 7, 22                       # 15 x 22 x 15
O4 = tc.O4
CORNERS = tc.CORNERS
inward, outward, along, hsh = tc.inward, tc.outward, tc.along, tc.hsh

LANTERN = dict(hanging="false", waterlogged="false")
DRY = dict(waterlogged="false")
CAMPFIRE = dict(lit="true", facing="north", signal_fire="false", waterlogged="false")
ROD = dict(powered="false", waterlogged="false")

# the kit's blocks, by role
CU, CU_E, CU_W, CU_O = ("waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper",
                        "waxed_oxidized_cut_copper")
CUB = "waxed_copper_block"         # bright polished brass: the gear rims
DO, DOL, DOS, DOF, DOSL = "dark_oak_planks", "stripped_dark_oak_log", "dark_oak_stairs", "dark_oak_fence", "dark_oak_slab"
SB, SBS, SBW = "polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_wall"
CPB = "chiseled_polished_blackstone"
DT, PD, DTS = "deepslate_tiles", "polished_deepslate", "deepslate_tile_stairs"


def st(c):
    """A copper block's stairs (waxed_cut_copper -> waxed_cut_copper_stairs)."""
    return c + "_stairs"


def sl(c):
    return c + "_slab"


STARTING_TYPES = {"polished_blackstone", "deepslate_bricks"}   # FoundryHall.startingBlockTypes
# BuildingAmbienceClientEvents.CAPITOL_SPOTS["foundry_hall"] (structure coordinates, the block (x, y, z) spans x..x+1):
# steam off the pressure dome on the south-west tower, embers out of the four lit blast furnaces on the south (+z) and
# west (-x) faces - the camera's faces
SPOTS = ((2.5, 13.0, 12.5, "STEAM"), (4.5, 2.6, 12.2, "EMBER"), (10.5, 2.6, 12.2, "EMBER"),
         (2.8, 2.6, 4.5, "EMBER"), (2.8, 2.6, 10.5, "EMBER"))


# ======================================================================================================= ground
def yard(c):
    """y0 everywhere: the soot floor under the hall (the instant foundation: polished blackstone and deepslate bricks,
    FoundryHall.startingBlockTypes) and the works yard on rings 6-7 breaking up into the ground on every side."""
    for dx, dz in c.cells(R):
        ring = max(abs(dx), abs(dz))
        h = hsh(dx, dz)
        if ring <= 5:
            name = "deepslate_bricks" if ring in (2, 5) or dx == 0 or dz == 0 else "polished_blackstone"
        elif ring == 6:
            name = ("polished_blackstone", "deepslate_bricks", "cobblestone", "polished_blackstone", "cobbled_deepslate",
                    "deepslate_bricks")[h % 6]
        else:
            name = ("cobblestone", "coarse_dirt", "cobbled_deepslate", "tuff", "coarse_dirt", "polished_blackstone",
                    "cobblestone", "rooted_dirt", "deepslate_bricks")[h % 9]
        c.set(dx, 0, dz, name)


def plinth(c):
    """y1: the hall floor (rings <= 5) and the plinth step all round on ring 6 (stairs climbing in)."""
    c.disc(5, 1, PD)
    for dx, dz in c.cells(3):
        c.set(dx, 1, dz, DT)
    for o in O4.values():
        for a in range(0, 4):
            c.put(o, a, 0, 1, CU_O)                      # a verdigris inlay cross running in to the hub
        for t in range(-5, 6):
            c.st(o, 6, t, 1, inward(o), name=SBS)
    c.set(0, 1, 0, CUB)


# ====================================================================================================== hall
def hall(c):
    """The hall's faces on ring 4 (y2-8) between the corner towers: furnace bay - buttress - portal - buttress -
    furnace bay, then the copper band and cornice."""
    for o in O4.values():
        for t in range(-3, 4):
            for y in range(2, 9):
                c.put(o, 4, t, y, DO)
        for s in (-1, 1):
            # the furnace bay (t = 3s): a lit blast furnace at the foot, a tall window above it
            c.put(o, 4, 3 * s, 2, "blast_furnace", facing=outward(o), lit="true")
            c.put(o, 4, 3 * s, 3, SB)
            for y in (4, 5, 6):
                c.put(o, 4, 3 * s, y, "orange_stained_glass")
                c.put(o, 3, 3 * s, y, "shroomlight" if y == 5 else CU_W)
            c.st(o, 4, 3 * s, 7, outward(o), "top", st(CU_E))
            # the timber posts beside the portal (t = 2s), behind the buttress
            for y in range(2, 9):
                c.put(o, 4, 2 * s, y, DOL, axis="y")
        # the portal: 3 wide, 3 high, an arched head (upside-down stair shoulders, a copper keystone)
        for t in (-1, 0, 1):
            for y in (2, 3, 4):
                c.put(o, 4, t, y, "air")
        for s in (-1, 1):
            c.st(o, 4, s, 5, along(o, s), "top", DOS)
        c.put(o, 4, 0, 5, CUB)
        # over the portal: the guild window (bars over orange glass)
        c.put(o, 4, 0, 6, "orange_stained_glass")
        c.put(o, 3, 0, 6, "shroomlight")
        # the copper band and the cornice
        for t in range(-3, 4):
            c.put(o, 4, t, 8, CU_E)
            c.st(o, 5, t, 9, inward(o), "top", st(CU))


def buttresses(c):
    """Dark-oak piers on ring 5 either side of every portal, stepping in with copper caps, an iron chain tie to the
    wall and a lantern on the pier's shoulder."""
    for o in O4.values():
        for s in (-1, 1):
            t = 2 * s
            c.put(o, 5, t, 2, SB)
            c.put(o, 5, t, 3, DOL, axis="y")
            c.put(o, 5, t, 4, DOL, axis="y")
            c.st(o, 5, t, 5, inward(o), name=st(CU_W))
            c.put(o, 5, t, 6, SBW)
            c.put(o, 5, t, 7, "lantern", **LANTERN)


def towers(c):
    """The four corner towers (2 x 2 on rings 4-5), y2-10: a soot foot with a lit smoker's furnace mouth on each outer
    face, weathered-copper walls between dark-oak quoins, exposed-copper bands; then each outer cell carries a
    SMOKESTACK of its own height - the shortest on the south-west corner the camera sees first, the tallest at the
    back, so the stacks step up round the clock - banded in copper with a slab collar and a campfire smoking on top."""
    top = 10
    stack_top = {(-1, -1): 18, (1, 1): 16, (1, -1): 21}
    for (sx, sz) in CORNERS:
        for qx in (4, 5):
            for qz in (4, 5):
                for y in range(2, top + 1):
                    nm = SB if y == 2 else CU_E if y in (6, top) else CU_W
                    c.set(sx * qx, y, sz * qz, nm)
        for y in range(3, top):
            c.set(sx * 5, y, sz * 5, DOL, axis="y")                # the dark-oak quoin
        out_x, out_z = ("east" if sx > 0 else "west"), ("south" if sz > 0 else "north")
        c.set(sx * 5, 2, sz * 4, "smoker", facing=out_x, lit="true")   # the furnace mouths at the tower feet
        c.set(sx * 4, 2, sz * 5, "smoker", facing=out_z, lit="true")
        for y in (4, 8):                                           # slit windows, lit from inside
            c.set(sx * 5, y, sz * 4, "orange_stained_glass")
            c.set(sx * 4, y, sz * 5, "orange_stained_glass")
        # the parapet: soot stairs on the three free cells, the stack on the outer one
        c.stair(sx * 4, top + 1, sz * 4, out_z, name=SBS)
        c.stair(sx * 5, top + 1, sz * 4, out_z, name=SBS)
        c.stair(sx * 4, top + 1, sz * 5, out_x, name=SBS)
        if (sx, sz) not in stack_top:
            # the corner the camera looks over keeps the clock in view: no stack, a riveted brass PRESSURE DOME (a
            # bright copper boiler with a verdigris collar and a gauge rod) on the tower's outer cell
            c.set(sx * 5, top + 1, sz * 5, CUB)
            c.set(sx * 5, top + 2, sz * 5, sl(CU_O), type="bottom", waterlogged="false")
            c.stair(sx * 5, top + 1, sz * 4, out_z, "top", st(CU_O))
            c.stair(sx * 4, top + 1, sz * 5, out_x, "top", st(CU_O))
            continue
        ty = stack_top[(sx, sz)]
        for y in range(top + 1, ty):
            c.set(sx * 5, y, sz * 5, CU_E if y % 3 == 0 else SB)
        c.set(sx * 5, ty - 1, sz * 5, CU)                          # the copper cap under the smoke
        c.set(sx * 5, ty, sz * 5, "campfire", **CAMPFIRE)


def pipes(c):
    """PIPES EVERYWHERE (the kit's silhouette): copper pipes (lightning rods - they never weather) climb the towers'
    outer faces from the furnace mouths and bend into the parapet with a stair elbow; a copper pipe runs along each
    face under the cornice; a soot manifold (a run of blackstone walls, which reads as a fat pipe from above) rings
    the clock tower's foot on the roof, with copper risers at its corners climbing to the gallery."""
    for (sx, sz) in CORNERS:
        # risers on the towers' outer faces, beside the quoin: from the smoker up to an elbow over the parapet
        for (x, z, f) in ((sx * 6, sz * 4, "east" if sx > 0 else "west"), (sx * 4, sz * 6, "south" if sz > 0 else "north")):
            for y in range(2, 10):
                c.set(x, y, z, "lightning_rod", facing="up", **ROD)
            c.stair(x, 10, z, tc._OPP[f], "top", st(CU))            # the elbow bending into the tower
    for o in O4.values():
        # the copper pipe along the face under the cornice, between the towers, with soot clamps
        for t in range(-3, 4):
            if abs(t) == 2:
                c.put(o, 5, t, 8, SB)                              # a clamp on the buttress head
            else:
                c.put(o, 5, t, 8, "lightning_rod", facing=along(o, 1), **ROD)
        # the manifold round the clock tower's foot (ring 3, y10), broken at the gear's bottom tooth
        for t in range(-3, 4):
            if abs(t) > 1:
                c.put(o, 3, t, 10, SBW)
    for (sx, sz) in CORNERS:
        for y in range(11, 18):
            c.set(sx * 3, y, sz * 3, "lightning_rod", facing="up", **ROD)


def pipe_bridges(c):
    """Soot pipe runs on dark-oak trestles cross the roof from three stacks to the clock tower (y12): out of the stack
    along one side, an elbow, and in to the tower's corner post - the roofscape the reference's pipeworks are made
    of. Blackstone walls joined in a run read as a fat pipe from above; a copper slab collar marks each elbow."""
    for (sx, sz) in CORNERS:
        if (sx, sz) == (-1, 1):
            continue                       # the camera's corner keeps the pressure dome and the view of the clock
        for k in (4, 3, 2):
            c.set(sx * 5, 12, sz * k, SBW)
        c.set(sx * 4, 12, sz * 2, SBW)
        c.set(sx * 3, 12, sz * 2, CU_E)                     # the flange on the tower
        c.set(sx * 5, 13, sz * 2, sl(CU), type="bottom", waterlogged="false")   # the elbow's collar
        for y in (10, 11):
            c.set(sx * 5, y, sz * 3, DOF)                   # the trestle


def crane(c):
    """A dock crane on the north-west stack (the camera's left): a dark-oak jib braced off the stack over the yard,
    a chain falling to a hook and a hanging crate - the guild's yard at work."""
    _x, z = -5, -5
    for xx in (-6, -7):
        c.set(xx, 14, z, DOF)
    c.stair(-6, 13, z, "east", "top", DOS)                         # the brace under the jib
    for y in (10, 11, 12, 13):
        c.set(-7, y, z, "chain", axis="y", **DRY)
    c.set(-7, 9, z, "barrel", facing="up", open="false")          # the crate on the hook


def gallery(c):
    """The catwalk round the clock under the dome (ring 3, y16): dark-oak top slabs on iron-bar rails, lanterns on its
    corners over the copper risers."""
    for o in O4.values():
        for t in range(-3, 4):
            if t == 0:
                continue
            c.put(o, 3, t, 18, DOSL, type="top", waterlogged="false")
            if abs(t) == 2:
                c.put(o, 3, t, 19, "iron_bars")
    for (sx, sz) in CORNERS:
        c.set(sx * 3, 18, sz * 3, CU_E)
        c.set(sx * 3, 19, sz * 3, "lantern", **LANTERN)


# ====================================================================================================== roof
def roof(c):
    """The hip roof between the cornice and the clock tower: a copper deck on rings 3-4 at y9, weathered stairs on
    ring 4 at y10 climbing inward to it."""
    for o in O4.values():
        for t in range(-3, 4):
            c.put(o, 4, t, 9, CU_E)
            c.st(o, 4, t, 10, inward(o), name=st(CU_W))
            c.put(o, 3, t, 9, CU_E)


def clock_tower(c):
    """The clock tower on ring 2, y10-18: dark-oak posts, oxidized copper walls, a GREAT GEAR on every face (the Iron
    Golem Drill's cog grown into the guild's clock), then the riveted copper dome, a lantern house and the spire."""
    for o in O4.values():
        for t in range(-1, 2):
            for y in range(10, 19):
                c.put(o, 2, t, y, CU_O)
        # the GREAT GEAR on ring 3 (centre y15): a round rim of bright copper (a 5 x 5 border with its corners cut, so
        # it reads round, not as a window frame), four iron teeth standing out beyond the rim's middles, and inside the
        # rim the clock - a glowing hub and two iron-bar hands over the verdigris face one block behind (a recess: the
        # third depth)
        for t in range(-2, 3):
            for y in range(13, 18):
                border = abs(t) == 2 or y in (13, 17)
                corner = abs(t) == 2 and y in (13, 17)
                c.put(o, 3, t, y, CUB if border and not corner else "air")
        c.put(o, 3, 0, 15, "ochre_froglight", axis="y")
        c.put(o, 3, 0, 16, "iron_bars")                       # the minute hand, straight up
        c.put(o, 3, 1, 15, "iron_bars")                       # the hour hand, at three
        for (t, y) in ((0, 12), (0, 18), (-3, 15), (3, 15)):
            c.put(o, 3, t, y, "iron_block")                   # the teeth
    for (sx, sz) in CORNERS:
        for y in range(10, 19):
            c.set(sx * 2, y, sz * 2, DOL, axis="y")
    # the dome: copper stair rings, a weathered band, a bright cap, a lantern on top
    c.ring_stairs(2, 19, name=st(CU))
    c.disc(1, 19, CU_E)
    c.ring_stairs(1, 20, name=st(CU))
    c.set(0, 20, 0, CUB)
    c.set(0, 21, 0, "lantern", **LANTERN)
    # the shaft's ceiling (the clock's floor) and its light
    c.disc(1, 14, DO)
    c.set(0, 14, 0, "shroomlight")


def gables(c):
    """A NAVE GABLE over every portal (ring 4, y9-12), breaking the cornice line: a copper rake of stairs, a dark-oak
    tympanum with a round furnace window (orange glass, a glowing core behind it), a copper finial with a lantern; a
    short copper ridge runs back to the clock tower."""
    for o in O4.values():
        for k, y in ((3, 9), (2, 10), (1, 11)):
            for s in (-1, 1):
                c.st(o, 4, k * s, y, along(o, -s), name=st(CU))
                c.st(o, 5, k * s, y - 1, along(o, -s), "top", st(CU_E)) if y == 9 else None
            for t in range(-(k - 1), k):
                c.put(o, 4, t, y, DO)
        c.put(o, 4, 0, 10, "orange_stained_glass")
        c.put(o, 3, 0, 10, "shroomlight")
        for s in (-1, 1):
            c.put(o, 4, s, 10, "iron_bars")                   # the window's grille, either side
        c.put(o, 4, 0, 12, CU)
        c.put(o, 4, 0, 13, "lantern", **LANTERN)
        c.put(o, 3, 0, 11, CU_E)                              # the ridge back to the tower
        c.put(o, 3, 0, 12, sl(CU_E), type="bottom", waterlogged="false")


def nave(c):
    """The nave: open from the floor (y2) up to the clock's floor (y12) in the middle and to the roof elsewhere;
    dark-oak columns at the inner corners, an iron-bar truss ring at y8 between them, chains hanging from the
    trusses."""
    for dx, dz in c.cells(3):
        top = 14 if max(abs(dx), abs(dz)) <= 1 else 9
        for y in range(2, top):
            if c.get(dx, y, dz)[0] in ("air", "cave_air"):
                c.set(dx, y, dz, "air")
    for (sx, sz) in CORNERS:
        for y in range(2, 10):
            c.set(sx * 2, y, sz * 2, DOL, axis="y")
    for o in O4.values():
        for t in (-1, 0, 1):
            c.put(o, 2, t, 9, "iron_bars")
        for s in (-1, 1):
            c.put(o, 2, s, 8, "chain", axis="y", **DRY)


def golems(c):
    """A brass automaton on each yard corner (the Iron Golem Drill in miniature, so capitol and extractors read as one
    family): basalt legs, the lit blast-furnace belly glowing out of the corner, a copper chest with upside-down stair
    shoulders over the yard and chain arms, a copper head with a lightning-rod antenna."""
    for (sx, sz) in CORNERS:
        x, z = sx * 6, sz * 6
        face = "south" if sz > 0 else "north"
        c.set(x, 1, z, "polished_basalt", axis="y")
        c.set(x, 2, z, "blast_furnace", facing=face, lit="true")
        c.set(x, 3, z, CU)
        c.set(x, 4, z, "shroomlight")                             # the glowing face (the Drill's eyes)
        c.set(x, 5, z, sl(CU_E), type="bottom", waterlogged="false")   # the helm
        c.set(x, 6, z, "lightning_rod", facing="up", **ROD)       # the antenna
        for (ax, az, f) in ((sx * 7, z, "east" if sx > 0 else "west"), (x, sz * 7, face)):
            c.stair(ax, 3, az, f, "top", st(CU_E))                # the shoulder
            c.set(ax, 2, az, "chain", axis="y", **DRY)            # the arm


def yard_dressing(c):
    """The works yard on ring 7 (y1): short rail spurs, crates (barrels), coal heaps, lantern posts and bare cobbles,
    scattered the same way on every side - no road, the axes in front of the portals kept clear."""
    for dx, dz in c.cells(R, R):
        t = min(abs(dx), abs(dz))
        if t <= 1 or t >= 6:
            continue
        h = hsh(dx * 3, dz * 7)
        k = h % 7
        along_x = abs(dz) == R
        if k in (0, 1):
            c.set(dx, 1, dz, "rail", shape="east_west" if along_x else "north_south", waterlogged="false")
        elif k == 2:
            c.set(dx, 1, dz, "barrel", facing="up", open="false")
        elif k == 3:
            c.set(dx, 1, dz, "coal_block")
            c.set(dx, 0, dz, "coal_block")
        elif k == 4 and t in (3, 4):
            c.set(dx, 1, dz, DOF)
            c.set(dx, 2, dz, "lantern", **LANTERN)


def build():
    c = tc.Cap(R, H)
    yard(c)
    plinth(c)
    hall(c)
    buttresses(c)
    towers(c)
    roof(c)
    clock_tower(c)
    gables(c)
    pipes(c)
    pipe_bridges(c)
    gallery(c)
    crane(c)
    nave(c)
    golems(c)
    yard_dressing(c)
    c.rig.settle()
    c.rig.check_support("foundry_hall")
    return c.rig.structure()


# ===================================================================================================== rules
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "soul_sand", "magma_block", "suspicious_")


def check(s, box=(2 * R + 1, H, 2 * R + 1)):
    """The rules the game needs, on the finished Structure (the support check ran in build())."""
    assert s.size == box, "the box is %s, expected %s" % (s.size, box)
    names = {(x, y, z): s.palette[i][0] for (x, y, z), i in s.blocks.items() if s.palette[i][0] != "air"}
    n = len(names)
    assert n < 2000, n
    assert max(p[0] for p in names) == box[0] - 1 and max(p[1] for p in names) == box[1] - 1 \
        and max(p[2] for p in names) == box[2] - 1, "the drawn blocks do not fill the box"
    layer0 = {nm for (x, y, z), nm in names.items() if y == 0}
    assert STARTING_TYPES <= layer0, "starting types missing from layer 0: %s" % (STARTING_TYPES - layer0)
    above = {nm for (x, y, z), nm in names.items() if y > 0} & STARTING_TYPES
    assert not above, "a starting type above layer 0: %s" % above
    bad = sorted({nm for nm in names.values() if any(nm == f or nm.endswith(f) or nm.startswith(f) for f in FORBIDDEN)
                  and nm != "campfire"})
    assert not bad, "forbidden blocks: %s" % bad
    # campfires (they burn units) only on stack tops: nothing a unit could stand on beside them at their level
    for (x, y, z), nm in names.items():
        if nm == "campfire":
            assert y >= 13 and (x, y - 1, z) in names, "a campfire at %s is not on a stack top" % ((x, y, z),)
    # the spawn: the box centre walked down while the block below is air lands on the nave floor, with headroom
    cx, cz = box[0] // 2, box[2] // 2
    y = (box[1] - 1) // 2
    while y > 0 and (cx, y - 1, cz) not in names:
        y -= 1
    assert y == 2 and all((cx, yy, cz) not in names for yy in range(2, 14)), "the centre spawn lands at y%d" % y
    # each ambience spot sits just off its block: the steam over the pressure dome's slab, an ember just outside each
    # furnace's mouth (the block behind the spot on the face it is drawn on)
    holders = {(2.5, 13.0, 12.5): (2, 12, 12), (4.5, 2.6, 12.2): (4, 2, 11), (10.5, 2.6, 12.2): (10, 2, 11),
               (2.8, 2.6, 4.5): (3, 2, 4), (2.8, 2.6, 10.5): (3, 2, 10)}
    for (x, y, z, fx) in SPOTS:
        cell = holders[(x, y, z)]
        want = "waxed_oxidized_cut_copper_slab" if fx == "STEAM" else "blast_furnace"
        assert names.get(cell) == want, "spot %s: %s at %s" % ((x, y, z), names.get(cell), cell)
    return n


def audit_pockets(path):
    import audit_structure_pockets as asp
    return asp.audit("foundry_hall", path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/foundry_hall.nbt"
    st_ = build()
    print("blocks:", check(st_))
    st_.write(out)
    one_way, ground, roof, rooms, note = audit_pockets(out)
    print("pockets: one-way %d, ground %d, roof-only %d, rooms %d%s" % (len(one_way), len(ground), len(roof),
                                                                        len(rooms), note))
    assert not one_way and not ground and "TRAPPED" not in note, (one_way[:8], ground[:8], note)
    print("wrote", out)
