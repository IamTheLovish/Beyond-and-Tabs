#!/usr/bin/env python3
"""The Tidewrought T1 army lab, the SLIPWAY, redrawn at all three levels - L1 THE SLIP, L2 THE FITTING-OUT, L3 THE
LAUNCH - as one family with the Admiral's Beacon (the capitol, tools/capitol_wreck_harbour.py) and the Tide-Pool Pump
(the extractor, tools/mex_tide.py).

lovish, Oct 19 2026: buildings must "look like they match each other so when bases get bigger they look cohesive and
beautiful"; "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out for the
opponent; ... much bigger or have cooler, more intricate designs". The old L1 (gen_structures.slipway, 9 x 7 x 9, 141
blocks) was a flat yard with a block of a hull and a bare crane - and an anvil (a gravity block) for its anchor; its
L2 / L3 (gen_lab_levels.tide_slipway) put a box hull straight over the yard's centre, where the units spawn.

THE TIDEWROUGHT STYLE KIT (the capitol's and the extractor's):
  - main blocks: PRISMARINE (bricks / dark / plain), SANDSTONE (smooth / cut / chiseled) and DRIFTWOOD (stripped dark
    oak and stripped mangrove logs, dark oak and mangrove planks);
  - accent and light: SEA LANTERNS (portholes, the stern window, the quay's corner lamps, the figurehead's beacon, the
    lamp room), lanterns on the bollards and mastheads;
  - silhouette motif: the STRIPED LIGHTHOUSE TOWER (the extractor's pump house, the capitol's stern tower);
  - signature detail: weathered ROPE and CHAIN (dark-oak fence rope rails and the rope boom, chain shrouds, the crane's
    chain, the anchor's cable);
  - the fleet's colours: white sailcloth with a light-blue stripe and light-blue banners (the capitol's sails).

THE STORY: the wreck-salvagers build their warships on a sideways-launching slip. The camera looks from the south-west
(it sees the roofs, the west face x = 0 and the south face z = 8), so the hull lies along x on the north side with its
bow to the west (the camera's left), its stern and the striped tower at the north-east (the far corner: the tallest
thing hides nothing), the launching basin along the south face, and the yard - where units are trained - between them.
  L1 THE SLIP - the hull on the stocks, half built: stone keel blocks lift it so daylight shows under the keel; a dark
     prismarine bilge strake and one strake of mangrove planking, and above it the bare red FRAMES (stripped mangrove
     ribs with thin fence beams across their heads) - a ship's skeleton; the stem curling forward; timber shores
     under the camera side. At the stern a striped crane house (the lighthouse motif's first stage) whose jib lowers
     a timber on a chain into the hull. The basin: five still sources walled by a dark-prismarine rim and a
     cut-sandstone quay with sea-lantern corners; the ground ways (flush driftwood in the floor) run from under the
     hull to the water and dip into it as stairs; a rope boom between two lantern bollards. The yard: barrels, a kelp
     bale, driftwood baulks, dead coral and a spare iron anchor standing on its crown.
  L2 THE FITTING-OUT - planked and decked: the capitol's hull bands (bilge, planking, a dark wale, exposed ribs between
     glowing portholes, a bulwark with a rope rail, an overhanging gunwale cap); the mainmast stepped with its yard
     crossed and the course furled on it, chain shrouds, a masthead lantern; a stern castle with a lit window toward
     the camera, a cornice, a poop rail, a lantern and the fleet's banner; the crane house rises a striped stage with
     a lit window, a stepped roof and a lamp post.
  L3 THE LAUNCH - the war galleon ready to go: the course let fall as one striped sheet hung forward of the mast and
     bellied by the wind (a third depth), a topsail on a lengthened topmast with the pennant at the truck; a
     sea-maiden FIGUREHEAD on the stem lifting a sea lantern (the Admiral's Beacon, as on the capitol); cannon run out
     of two gunports; and the crane house becomes the STRIPED LIGHTHOUSE - the shaft rises, a corbelled gallery with a
     rope rail and a corner lamp, the lamp room of blue glass panes round a sea lantern, a prismarine cap and a copper
     vane - the crane's jib still reaching out of its side. 21 tall: the lab reads across the map as a lit harbour.

HOW THE SLIPWAY WORKS IN THE GAME (bar/.../building/buildings/tide/Slipway.java) - kept working in every level:
  - Slipway.startingBlockTypes = smooth_sandstone, prismarine_bricks: both in layer 0 (the apron and the kerb), the
    instant foundation (BuildingServerEvents takes only the foundation layer);
  - the unit spawn (ProductionBuilding.getIndoorSpawnPoint) is centrePos walked down. The footprint is odd (9 x 9), so
    centrePos is the column x 4, z 4 for every rotation and sign; its height is L1's mid-height and an upgrade keeps it.
    That column is open from the yard floor to the sky at every level, and the 3 x 3 round it is clear at y1, so even
    the Mudskipper Sloop (1.54 wide, under a block tall) lands on the spawn cell itself; units walk out west or east
    along the yard (z 4-5);
  - the water: still sources at y1 on a prismarine bed, every side walled; the rope boom over them shuts every water
    cell's head room (WalkabilityBuilder.blocksBody), so the grid never calls a water cell walkable: no unit can step
    or fall in, and the structures_have_no_unit_pockets GameTest's "closed ground cell" rule (c) finds none there;
  - nothing else reads the slipway's blocks: no GameTest pins its box or blocks (HeroGameTests places one and only
    checks it is a ProductionPlacement; SkirmishGameTests lists slipway.nbt as shipped and as GENERATED_STRUCTURES for
    the pocket audit), no BuildingAmbienceClientEvents spot names it, no CapitolLife motion.

RULES (asserted in check_levels): the 9 x 9 footprint at every level (BuildingLevels has no larger footprint; a level
grows only taller); the chain - every non-air cell of L1 is non-air in L2, and of L2 in L3 (L2 is drawn over L1 and L3
over L2); the starting types in layer 0; a full foundation; nothing pops off (lanterns, banners, dead coral); water only
as walled still sources no unit can reach; the spawn column open and walking out; no pockets
(tools/audit_structure_pockets.py audit() on all three); no lava, gravity blocks, redstone, fire, doors, leaves,
carpets, candles or sea pickles (nothing part-height on a way out).

Usage: python3 tools/lab_slipway.py [OUT_DIR]   (writes slipway.nbt, slipway_l2.nbt, slipway_l3.nbt and checks them;
       default OUT_DIR: the scratch folder - shipping them is the coordinator's step)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

SX = SZ = 9
SPAWN = (4, 4)
DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
DP, PR, PB = "dark_prismarine", "prismarine", "prismarine_bricks"
SS, CS, CHS = "smooth_sandstone", "cut_sandstone", "chiseled_sandstone"
DOP, MAP = "dark_oak_planks", "mangrove_planks"
SDL, SML = "stripped_dark_oak_log", "stripped_mangrove_log"
ROPE = "dark_oak_fence"
LAMP = "sea_lantern"
BAND = {1: DP, 2: CS, 3: CS, 4: PB, 5: PB, 6: CS, 7: CS, 8: PB, 9: PB, 10: CS, 11: CS, 12: PB, 13: PB, 14: CS}


class Rig(gs._SunRig):
    def __init__(self, sy):
        super().__init__(SX, sy, SZ)

    def st(self, x, y, z, facing, half="bottom", name="dark_oak_stairs"):
        self.stair(x, y, z, facing, half, name)

    def slab(self, x, y, z, name, typ="bottom"):
        self.set(x, y, z, name, type=typ, **DRY)

    def log(self, x, y, z, name=SDL, axis="y"):
        self.set(x, y, z, name, axis=axis)

    def lantern(self, x, y, z, hanging=False):
        self.set(x, y, z, "lantern", hanging="true" if hanging else "false", **DRY)

    def chain(self, x, y, z, axis="y"):
        self.set(x, y, z, "chain", axis=axis, **DRY)

    def fill(self, x1, y1, z1, x2, y2, z2, name, **p):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **p)


# ============================================================================================================== L1
def ground(r):
    """Layer 0, the instant foundation: a prismarine-brick kerb with chiseled sandstone corners round a smooth sandstone
    apron (the extractor's pad - both of Slipway.startingBlockTypes), the ground ways flush in the floor from under the
    hull to the water (driftwood: units walk over them), and a pale prismarine bed under the basin."""
    for x in range(SX):
        for z in range(SZ):
            corner = x in (0, SX - 1) and z in (0, SZ - 1)
            edge = x in (0, SX - 1) or z in (0, SZ - 1)
            r.set(x, 0, z, CHS if corner else PB if edge else SS)
    for x in (2, 5):                       # the ground ways
        for z in range(1, 6):
            r.log(x, 0, z, SDL, "z")
    for x in range(2, 7):
        r.set(x, 0, 7, PR)                 # the channel bed


def basin(r):
    """The launching basin on the south (camera) side: five still sources at y1 on a prismarine bed, walled on every
    side by the quay. A rope boom (fence) strung between two driftwood bollards runs over the water: it shuts every
    water cell's head room, so no ground unit can step into the water (and none can be stranded in it), and it leaves
    the water open to the camera on both sides of the rope."""
    for x in range(2, 7):
        r.set(x, 1, 7, "water", level="0")
        r.set(x, 2, 7, ROPE)
    for x in range(1, 8):
        r.set(x, 1, 6, DP)
        r.set(x, 1, 8, CS)
    for x in (2, 5):
        r.st(x, 1, 6, "north", name="dark_oak_stairs")   # the ways dip into the water
    for x in (1, 7):
        r.set(x, 1, 7, DP)
        r.set(x, 1, 8, LAMP)
        r.log(x, 2, 7, SDL, "y")              # the bollards, a lantern on each
        r.lantern(x, 3, 7)
    r.set(4, 1, 8, CHS)                       # a carved stone in the middle of the quay


def hull_l1(r):
    """The hull on the stocks, half built. Sleepers at y1 (x 1, 3, 5) lift it so daylight shows under the keel."""
    for x in (1, 3, 5):
        for z in range(0, 3):
            r.set(x, 1, z, PB if z == 0 else DP)        # stone keel blocks, as the capitol's hull foot
    # keel and bottom (y2), bilge strakes narrowing to it
    for x in range(0, 7):
        for z in (1, 2):
            r.log(x, 2, z, SDL, "x")
    for x in range(2, 7):
        r.st(x, 2, 0, "south", "top", "dark_prismarine_stairs")
        r.st(x, 2, 3, "north", "top", "dark_prismarine_stairs")
    # the first strake of mangrove planking (y3) and the hold's floor inside it - the rest is still bare frames
    for x in range(2, 7):
        r.set(x, 3, 0, MAP)
        r.set(x, 3, 3, MAP)
        for z in (1, 2):
            r.set(x, 3, z, DOP)
    # the bow: two wide, its cheeks stepping in, the stem rising and curling forward
    for z in (1, 2):
        r.set(1, 3, z, MAP)
        r.st(0, 2, z, "east")                    # the forefoot
        r.set(0, 3, z, MAP)
    for z, f in ((0, "south"), (3, "north")):
        r.st(1, 3, z, f, "top", "mangrove_stairs")
    for y in (4, 5, 6):
        r.log(0, y, 2, SDL, "y")
    r.log(0, 4, 1, SDL, "y")
    r.st(0, 5, 1, "south", "bottom", "dark_oak_stairs")
    r.st(0, 7, 2, "west", "bottom", "dark_oak_stairs")      # the stem head curling forward
    for z in (1, 2):
        r.set(1, 4, z, ROPE)
        r.set(1, 5, z, ROPE)
    # the frames: ribs of stripped mangrove (the red driftwood) rising from the strake at every other x, thin fence
    # beams across their heads; the bays between them open, so the hull reads as a skeleton on the stocks
    for x in (2, 4, 6):
        for z in (0, 3):
            for y in (4, 5):
                r.log(x, y, z, SML, "y")
            r.set(x, 6, z, ROPE)
        for z in (1, 2):
            r.set(x, 6, z, ROPE)
    r.lantern(2, 7, 3)                          # a work lamp on the foremost frame head (it stays at every level)
    # the sternpost
    for z in (1, 2):
        for y in (4, 5, 6):
            r.log(6, y, z, SDL, "y")
    # a ribband: one batten of dark oak tying the frames along the camera side at mid height
    for x in (3, 5):
        r.set(x, 5, 3, "dark_oak_slab", type="top", **DRY)
        r.set(x, 5, 0, "dark_oak_slab", type="top", **DRY)
    # shores on the camera side: timber props wedged under the bilge
    for x in (2, 6):
        r.st(x, 1, 4, "north")


def tower_l1(r):
    """The crane house at the stern (x 7-8, z 0-1): a striped stub in the extractor's bands with a sea-lantern window,
    a slab roof and a timber crane - the stage the L3 lighthouse grows from."""
    for y in range(1, 7):
        for x in (7, 8):
            for z in (0, 1):
                r.set(x, y, z, BAND[y])
    r.set(7, 4, 1, LAMP)
    for x in (7, 8):
        for z in (0, 1):
            r.slab(x, 7, z, "dark_prismarine_slab")
    # the crane: a mast on the tower, the jib reaching west over the stern, a chain lowering a timber
    for y in range(7, 11):
        r.log(8, y, 1, SDL, "y")
    for x in range(5, 8):
        r.log(x, 10, 1, SDL, "x")
    for y in (7, 8, 9):
        r.chain(5, y, 1)
    r.log(5, 6, 1, SML, "x")
    r.set(8, 11, 1, ROPE)
    r.lantern(8, 12, 1)


def yard_l1(r):
    """The yard's stores, kept off the spawn's 3 x 3 and off the way out (z 4-5)."""
    r.set(7, 1, 2, "barrel", facing="up", open="false")
    r.set(8, 1, 2, "dried_kelp_block")
    r.set(8, 2, 2, "barrel", facing="up", open="false")
    # the shipwright's timber: two driftwood baulks on the west kerb, dead coral washed up beside them
    for z in (7, 8):
        r.log(0, 1, z, SDL, "z")
    r.set(0, 1, 6, "dead_brain_coral_fan", **DRY)
    # the strand's dressing on the kerb where nobody paths: dead coral washed up by the bow and behind the barrels
    r.set(0, 1, 0, "dead_tube_coral_fan", **DRY)
    r.set(8, 1, 3, "dead_horn_coral_fan", **DRY)
    # a spare ANCHOR stood on its crown in the south-east corner (the capitol's anchors: iron-dark polished deepslate,
    # the one iron thing in the yard), its stock a thin driftwood bar, its cable run in chain to the bollard
    r.set(8, 1, 7, "polished_deepslate")
    r.st(8, 1, 6, "north", name="polished_deepslate_stairs")
    r.st(8, 1, 8, "south", name="polished_deepslate_stairs")
    r.chain(8, 2, 6)
    r.chain(8, 2, 8)
    for y in (2, 3, 4):
        r.set(8, y, 7, "polished_deepslate_wall")
    for z in (6, 7, 8):
        r.set(8, 4, z, ROPE)
    r.chain(8, 5, 7)


def draw_l1(r):
    ground(r)
    basin(r)
    hull_l1(r)
    tower_l1(r)
    yard_l1(r)


# ============================================================================================================== L2
def hull_l2(r):
    """The hull planked up and decked: the wale over the first strake, exposed ribs between glowing portholes (the
    capitol's hull bands), a bulwark and a rope rail, the deck over the frames."""
    for x in range(1, 7):
        for z in (0, 3):
            if x == 1:
                r.set(x, 4, z, MAP)
                continue
            r.log(x, 4, z, SDL, "x")                         # the wale
            if x % 2:
                r.set(x, 5, z, LAMP if z == 3 else MAP)      # portholes between the ribs on the camera side
            r.set(x, 6, z, DOP)                              # the bulwark
            if x < 5 and r.get(x, 7, z)[0] == "air":
                r.set(x, 7, z, ROPE)                         # the rope rail
        for z in (1, 2):
            for y in (4, 5):
                r.set(x, y, z, DOP)                          # the hold, packed (nothing to stand in)
            r.set(x, 6, z, MAP if x % 2 else DOP)            # the deck
    for z in (0, 3):
        r.set(1, 5, z, MAP)
        r.set(1, 6, z, DOP)
        r.set(1, 7, z, ROPE)
    r.set(0, 7, 1, ROPE)                                     # the bow's rail closes round the stem head
    # the gunwale cap overhangs the camera side (never over the spawn column x 4)
    for x in (1, 2, 3, 5, 6):
        r.st(x, 6, 4, "north", "top", "dark_oak_stairs")


def mainmast_l2(r):
    """The mainmast stepped amidships (x 3) with its yard crossed and the sail furled on it; chain shrouds."""
    for y in range(7, 14):
        r.log(3, y, 2, SDL, "y")
    r.set(3, 14, 2, ROPE)
    r.lantern(3, 15, 2)
    for z in (0, 1, 3, 4):
        r.log(3, 12, z, SDL, "z")
        r.set(3, 11, z, "white_wool")                       # the furled course
    for z in (0, 4):
        for y in (8, 9, 10):
            r.chain(3, y, z)
    r.chain(3, 7, 4)


def castle_l2(r):
    """The stern castle (x 5-6): a cabin with a lit stern window toward the camera, a poop deck with a rope rail."""
    for x in (5, 6):
        for z in range(0, 4):
            for y in (7, 8):
                r.set(x, y, z, MAP)
            r.slab(x, 9, z, "dark_oak_slab", "top")
    for z in (0, 3):
        for y in (7, 8):
            r.log(6, y, z, SDL, "y")
    r.set(5, 7, 3, "light_blue_stained_glass")
    r.set(5, 7, 2, LAMP)
    r.set(5, 8, 3, DOP)
    for x in (5, 6):
        r.st(x, 9, 3, "north", "top", "dark_oak_stairs")    # the cornice
    r.set(5, 10, 3, ROPE)
    r.set(6, 10, 3, ROPE)
    r.set(6, 10, 0, ROPE)
    r.set(5, 10, 0, ROPE)
    r.lantern(6, 11, 3)
    r.set(6, 10, 2, "light_blue_banner", rotation="4")      # the fleet's colour over the stern


def tower_l2(r):
    """The crane house rises a stage: two more bands, a lit window, a stepped prismarine roof and a lamp post."""
    for y in range(7, 11):
        for x in (7, 8):
            for z in (0, 1):
                r.set(x, y, z, BAND[y])
    r.set(7, 8, 1, LAMP)
    for x in (7, 8):
        r.st(x, 11, 1, "north", name="dark_prismarine_stairs")
        r.st(x, 11, 0, "south", name="dark_prismarine_stairs")
    r.set(8, 11, 1, DP)
    r.set(8, 12, 1, "prismarine_wall")
    r.lantern(8, 13, 1)


def yard_l2(r):
    r.set(7, 1, 3, "barrel", facing="up", open="false")
    r.lantern(8, 3, 2)


def draw_l2(r):
    draw_l1(r)
    hull_l2(r)
    mainmast_l2(r)
    castle_l2(r)
    tower_l2(r)
    yard_l2(r)


# ============================================================================================================== L3
def sheet(r, x, cells):
    """Sailcloth: white wool with the fleet's blue stripe down the middle (z 2), the capitol's sails."""
    for (y, z) in cells:
        r.set(x, y, z, "light_blue_wool" if z == 2 else "white_wool")


def rig_l3(r):
    """The sails set: the main course let fall from its yard as one sheet hung forward of the mast, its middle
    bellied out toward the bow by the wind (a third depth), a topsail over it on a lengthened topmast, the fleet's
    pennant at the truck and chain sheets from the course's foot to the rail."""
    sheet(r, 2, [(y, z) for y in (8, 9, 10, 11) for z in range(0, 5)])
    sheet(r, 1, [(y, z) for y in (9, 10) for z in (1, 2, 3)])
    for y in (14, 15, 16):
        r.log(3, y, 2, SDL, "y")
    r.set(3, 17, 2, ROPE)
    r.set(3, 18, 2, "light_blue_banner", rotation="4")
    for z in (1, 3):
        r.log(3, 15, z, SDL, "z")
    sheet(r, 2, [(y, z) for y in (13, 14) for z in (1, 2, 3)])


def figurehead_l3(r):
    """The sea-maiden figurehead on the stem lifting a sea lantern (the Admiral's Beacon, as on the capitol)."""
    r.st(0, 4, 2, "east", "top", "dark_prismarine_stairs")
    r.st(0, 5, 2, "west", "bottom", "prismarine_brick_stairs")
    r.set(0, 6, 2, "prismarine_wall")
    r.set(0, 7, 2, PR)
    r.set(0, 8, 2, LAMP)
    for z in (1, 3):
        r.set(0, 7, z, "prismarine_wall")
    r.st(0, 8, 1, "south", "top", "prismarine_brick_stairs")
    r.st(0, 8, 3, "north", "top", "prismarine_brick_stairs")


def guns_l3(r):
    """Two portholes become gunports with an iron cannon run out of each (off the spawn column)."""
    for x in (3, 5):
        r.set(x, 5, 3, "polished_deepslate")
        r.set(x, 5, 4, "polished_deepslate_wall")


def lighthouse_l3(r):
    """The crane house becomes the striped lighthouse: the shaft rises, a corbelled gallery with a rope rail, the
    sea-lantern lamp room behind blue glass, a prismarine cap and a copper vane."""
    for y in range(11, 15):
        for x in (7, 8):
            for z in (0, 1):
                r.set(x, y, z, BAND.get(y, CS))
    r.set(7, 12, 1, LAMP)
    for x in range(5, 9):
        for z in range(0, 4):
            if x == 5 or z == 3:
                f = "east" if x == 5 and z != 3 else "north"
                r.st(x, 14, z, f, "top", "dark_prismarine_stairs")
                r.slab(x, 15, z, "dark_prismarine_slab", "top")
                r.set(x, 16, z, ROPE)
            else:
                r.set(x, 15, z, DP)
    r.set(5, 16, 3, "prismarine_wall")
    r.lantern(5, 17, 3)
    for y in (16, 17):
        for x in range(6, 9):
            for z in range(0, 3):
                if (x, z) == (7, 1):
                    r.set(x, y, z, LAMP)
                elif x != 7 and z != 1:
                    r.set(x, y, z, "prismarine_wall")
                else:
                    r.set(x, y, z, "light_blue_stained_glass_pane")
    for x in range(6, 9):
        for z in range(0, 3):
            if (x, z) == (7, 1):
                r.set(x, 18, z, DP)
            else:
                f = "east" if x == 6 else "west" if x == 8 else "south" if z == 0 else "north"
                r.st(x, 18, z, f, name="dark_prismarine_stairs")
    r.set(7, 19, 1, "prismarine_wall")
    r.set(7, 20, 1, "lightning_rod", facing="up", powered="false", **DRY)


def draw_l3(r):
    draw_l2(r)
    rig_l3(r)
    figurehead_l3(r)
    guns_l3(r)
    lighthouse_l3(r)


# ============================================================================================================ build
YMAX = 24


def build(draw):
    r = Rig(YMAX)
    draw(r)
    r.settle()
    top = max(y for (x, y, z), (n, p) in r.b.items() if n != "air")
    s = gs.Structure(SX, top + 1, SZ)
    for (x, y, z), (n, p) in sorted(r.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        s.set(x, y, z, n, **p)
    return s


def l1():
    return build(draw_l1)


def l2():
    return build(draw_l2)


def l3():
    return build(draw_l3)


def base():
    """The L1 the game ships as slipway.nbt (gen_structures.slipway() returns it)."""
    return l1()


def levels():
    """(L2, L3) for gen_lab_levels.LABS["slipway"]."""
    return l2(), l3()


# ============================================================================================================ checks
START_TYPES = ("smooth_sandstone", "prismarine_bricks")       # Slipway.startingBlockTypes
FORBIDDEN = ("lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "_door", "_leaves", "_carpet", "candle", "sea_pickle",
             "kelp", "seagrass")
FLOOR_ATTACHED = ("_coral_fan", "_coral")                     # dead coral (fans and plants) needs a full face below


def blocks_of(s):
    return {p: (s.palette[i][0], dict(s.palette[i][1])) for p, i in s.blocks.items()
            if s.palette[i][0] not in ("air", "cave_air")}


def check_support(s, tag):
    """Nothing the level adds would pop off once its neighbours update (vanilla canSurvive)."""
    b = blocks_of(s)
    air = ("air", {})

    def at(x, y, z):
        return b.get((x, y, z), air)
    bad = []
    for (x, y, z), (n, p) in b.items():
        below, above = at(x, y - 1, z), at(x, y + 1, z)
        if n in ("lantern", "soul_lantern"):
            ok = (gs._sun_sturdy(above, "down") or above[0] == "chain") if p.get("hanging") == "true" \
                else gs._sun_centre_up(below)
        elif n.endswith("_wall_banner") or n.endswith("_wall_torch") or n.endswith("_coral_wall_fan"):
            ok = False                                         # none drawn on this lab
        elif n.endswith("_banner"):
            ok = gs._sun_solid(below)
        elif n.endswith(FLOOR_ATTACHED):
            ok = gs._sun_sturdy(below, "up")
        elif n == "water":
            ok = True                                          # check_water
        else:
            continue
        if not ok:
            bad.append(((x, y, z), n))
    assert not bad, "slipway %s: would pop off: %s" % (tag, sorted(bad))


def check_water(s, tag):
    """Still sources only, each on a solid bed, walled on all four sides; and no ground unit can walk or fall into
    one (a forward flood from the open ground round the lab, with the pocket audit's moves, never touches water)."""
    import audit_structure_pockets as ap
    b = blocks_of(s)
    wet = [p for p, (n, _) in b.items() if n == "water"]
    for (x, y, z) in wet:
        assert b[(x, y, z)][1].get("level") == "0", "%s: water %s is not a source" % (tag, (x, y, z))
        assert (x, y - 1, z) in b and b[(x, y - 1, z)][0] != "water", "%s: water %s has no bed" % (tag, (x, y, z))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y, z + dz)
            assert q in b, "%s: water %s is open toward %s" % (tag, (x, y, z), q)
    pad = _pad(s)
    reached = _flood_in(pad, ap)
    dipped = sorted(c for c in reached if ap.base(pad.at(*c)[0]) == "water")
    assert not dipped, "%s: units can step into the water at %s" % (tag, dipped)
    return len(wet)


def _pad(s):
    import audit_structure_pockets as ap
    nbt = {"size": list(s.size), "palette": [dict(Name="minecraft:" + n, Properties=dict(p)) for n, p in s.palette],
           "blocks": [dict(pos=list(pos), state=i) for pos, i in s.blocks.items()]}
    nbt["palette"] = [dict(Name="minecraft:" + n, Properties=dict(p)) for n, p in s.palette]
    return ap.Pad(nbt)


def _flood_in(pad, ap):
    """Every cell a ground unit standing on the open ground round the lab can walk or fall into."""
    lo, hx, hz = -ap.MARGIN, pad.sx - 1 + ap.MARGIN, pad.sz - 1 + ap.MARGIN
    stack = [(x, 1, z) for x in range(lo, hx + 1) for z in range(lo, hz + 1)
             if (x < 0 or z < 0 or x >= pad.sx or z >= pad.sz) and pad.walkable(x, 1, z)]
    seen = set(stack)
    while stack:
        for n in ap.neighbours(pad, *stack.pop()):
            if lo <= n[0] <= hx and lo <= n[2] <= hz and 1 <= n[1] <= pad.sy + 1 and n not in seen:
                seen.add(n)
                stack.append(n)
    return seen


def check_levels(s1, s2, s3):
    """The rules the game needs, on the three finished Structures. Returns report lines."""
    import audit_structure_pockets as ap
    lv = (("L1", s1), ("L2", s2), ("L3", s3))
    report = []
    for tag, s in lv:
        assert (s.size[0], s.size[2]) == (SX, SZ), "%s: footprint %s, the Slipway's is 9 x 9" % (tag, s.size)
        b = blocks_of(s)
        assert max(p[1] for p in b) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in b for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
        names = {n for n, _ in b.values()}
        bad = sorted(n for n in names if any(n == f or n.endswith(f) for f in FORBIDDEN))
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        for t in START_TYPES:
            assert any(n == t and p[1] == 0 for p, (n, _) in b.items()), "%s: no %s in layer 0" % (tag, t)
        check_support(s, tag)
        wet = check_water(s, tag)
        # the spawn: centrePos is the box centre (4, 4) - an odd footprint, so every rotation and sign gives the same
        # column - walked down from L1's mid-height; an upgrade keeps it. Open from the floor to the sky, every level.
        x, z = SPAWN
        assert (x, 0, z) in b, "%s: no floor under the spawn" % tag
        shut = [y for y in range(1, s.size[1]) if (x, y, z) in b]
        assert not shut, "%s: the spawn column is shut at %s" % (tag, shut)
        # room for the widest body the Slipway trains (the Mudskipper Sloop, 1.54 wide and under a block tall) on the
        # spawn cell itself: the 3 x 3 round it is clear at y1 (safeSpawnPoint keeps a wide body where it fits)
        wide = [(x + dx, 1, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if (x + dx, 1, z + dz) in b]
        assert not wide, "%s: the wide-body room round the spawn is taken at %s" % (tag, wide)
        report.append((tag, s, len(b), wet))
    # the chain: an upgrade only adds or replaces (BuildingPlacement.changeStructure / refreshBlocks)
    for (ta, a), (tb, c) in ((lv[0], lv[1]), (lv[1], lv[2])):
        lost = sorted(set(blocks_of(a)) - set(blocks_of(c)))
        assert not lost, "%s -> %s would leave blocks behind: %s" % (ta, tb, lost[:8])
    lines = []
    for tag, s, n, wet in report:
        import tempfile
        path = os.path.join(tempfile.mkdtemp(), "slipway_%s.nbt" % tag)
        s.write(path)
        one_way, ground, roof, rooms, note = ap.audit(tag, path)
        assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (
            tag, one_way[:6], ground[:6], note)
        pad = ap.Pad(ap.load_nbt(path))
        start = (SPAWN[0], 1, SPAWN[1])
        assert pad.walkable(*start), "%s: the spawn cell is not standable" % tag
        assert start in _flood_in(pad, ap) and _escapes(pad, ap, start), "%s: a unit spawned at %s can't walk out" % (
            tag, start)
        lines.append("%s %dx%dx%d, %d blocks (%d still water): no pockets (%d roof-only cells nothing can reach), "
                     "no water a unit can step into, the spawn %s walks out%s" % (
                         tag, s.size[0], s.size[1], s.size[2], n, wet, len(roof), start, note))
    return lines


def _escapes(pad, ap, start):
    seen, stack = {start}, [start]
    while stack:
        c = stack.pop()
        if c[0] < 0 or c[2] < 0 or c[0] >= pad.sx or c[2] >= pad.sz:
            return True
        for n in ap.neighbours(pad, *c):
            if n not in seen and -3 <= n[0] <= pad.sx + 2 and -3 <= n[2] <= pad.sz + 2 and 1 <= n[1] <= pad.sy + 1:
                seen.add(n)
                stack.append(n)
    return False


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/lab_slip"
    os.makedirs(out, exist_ok=True)
    built = (l1(), l2(), l3())
    for name, s in zip(("slipway", "slipway_l2", "slipway_l3"), built):
        s.write(os.path.join(out, name + ".nbt"))
    for line in check_levels(*built):
        print(line)
