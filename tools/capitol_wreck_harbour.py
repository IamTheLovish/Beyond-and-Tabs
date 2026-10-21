#!/usr/bin/env python3
"""The Tidewrought capitol, the Wreck Harbour, redrawn as THE ADMIRAL'S BEACON - the centrepiece of the base.

lovish, Oct 19 2026: capitols "can be a lot bigger than the current capital size, a lot taller, a lot more detailed",
"I want it to be the center piece of the base", everything "connects" to it with "fluidity", "don't break the game".
Detailed = fine-grained (stairs, slabs, walls, fences, chains), not chunky blocks. The bar is the Sunforged Temple of
the Sun (tools/capitol_town_centre.py build_grand, 15 x 20 x 15).

THE TIDEWROUGHT STYLE KIT (every Tidewrought building follows it; the capitol is its fullest expression):
  - main blocks: PRISMARINE (plain / bricks / dark), SANDSTONE (smooth / cut / plain) and DRIFTWOOD (stripped dark oak
    and mangrove logs, dark oak and mangrove planks);
  - accent and light: SEA LANTERNS (the lamp room, glowing portholes and stern windows, the dock's bollard lamps, the
    figurehead's beacon, inlays in the shore);
  - silhouette motif: the striped LIGHTHOUSE TOWER (here the tallest thing in the base);
  - signature detail: weathered ROPE and CHAIN (fence rope-rails on every rail and gallery, chains for the shrouds,
    the anchor cables and the mooring lines);
  - edge: a sandy shore of sandstone, clay and coral rubble with driftwood washed up, the same on every side - it runs
    on into the Tidewrought strand aura (FactionAura.STRAND: sand, clay, shingle, dead coral).

THE STORY AND THE DESIGN: a wreck-salvage fleet hauled the hull of a giant galleon ashore and made it their seat.
  - the HULL lies along z, its prow and figurehead to the south (a camera side: the RTS camera looks north-east from the
    south-west, seeing the west and south faces and the tops). Its sides are banded like a real ship - a barnacled
    dark-prismarine bilge strake (upside-down stairs, so the hull narrows to the keel), mangrove planks, a dark wale
    with a rubbing strake standing proud of it, sea-lantern portholes between exposed ribs, a rope rail on top;
  - the salvagers cut a GATE through each side (west and east, 3 wide, 3 tall, rib jambs and splintered plank ends):
    the hold is the great hall - units are trained in it (the spawn is the box centre walked down through the open
    cargo hatch onto the hold floor) and walk out of either gate;
  - the MASTS: a mainmast and a foremast with yards, TATTERED SAILS (white wool with the fleet's blue stripe, a belly
    blown forward, holes and a ragged foot), chain shrouds from the yard arms, a blue pennant and a lantern at each
    masthead;
  - the STERN CASTLE glows through its stern windows, and the STRIPED LIGHTHOUSE rises from the stern: an octagonal
    shaft (wall-block corners) in bands of sandstone and prismarine, glowing slit windows, a corbelled gallery with a
    rope rail, the SEA-LANTERN LAMP ROOM behind blue glass - the crown of the base - a prismarine cap and a vane;
  - the PROW: a stem post, a bowsprit, a figurehead holding up a sea lantern (the Admiral's Beacon), two ANCHORS
    resting on the sand either side, their cables run in chains to the stem;
  - the DOCK: a walled basin of still water on the west (camera) side, sunk into the ground layer (sources only, every
    side walled by the quay and the hull's foundation, solid ground under it), a rough breakwater of prismarine, coral
    and driftwood round it, bollard lamps at its mouth, and a gangplank from the west gate across it;
  - the SALVAGE YARD on the east: a capstan, barrels, a stack of driftwood, coral rubble, lamp posts;
  - the SHORE ring feathers out into the ground on all four sides.

WHAT THE SIZE TOUCHES IN THE GAME (the coordinator applies these; this file edits nothing) - see the report.
  - WreckHarbour sets no buildTimeBlocks (0 = timed by its own block count), so the bigger art would build ~6x slower:
    it needs buildTimeBlocks = 243 (the shipped harbour's block count);
  - tools/gen_structures.py writes wreck_harbour.nbt from wreck_harbour(): it should write this build();
  - BuildingAmbienceClientEvents.CAPITOL_SPOTS gains "wreck_harbour" (SPOTS below: a glint over the lamp room and
    splashes on the dock); CapitolLife has no motion for it.

RULES (asserted in check()): the box; the starting types (WreckHarbour.startingBlockTypes: smooth_sandstone,
prismarine_bricks) in layer 0 and nowhere above; nothing pops off (_SunRig.check_support, plus corals on full blocks);
water only as still sources in layer 0 with every side non-air; no lava, gravity blocks, redstone or fire; under 2000
blocks; the centre spawn lands on the hold floor with headroom; no pockets (tools/audit_structure_pockets.py audit()).

Usage: python3 tools/capitol_wreck_harbour.py [OUT.nbt]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

W, H = 15, 22
C = 7                       # the centre column (x and z)
LANTERN = dict(hanging="false", waterlogged="false")
DRY = dict(waterlogged="false")
# the kit's blocks, by role
DP, PR, PB = "dark_prismarine", "prismarine", "prismarine_bricks"
SS, CS, SA = "smooth_sandstone", "cut_sandstone", "sandstone"
DOP, MAP = "dark_oak_planks", "mangrove_planks"
SDL, SML = "stripped_dark_oak_log", "stripped_mangrove_log"
ROPE = "dark_oak_fence"     # fences are the kit's weathered rope rails
LAMP = "sea_lantern"

# the hull's half-width at each z (the prow points south, to z = 14): stern transom at z 4, the stem at z 13
HW = {4: 3, 5: 3, 6: 3, 7: 3, 8: 3, 9: 3, 10: 3, 11: 2, 12: 1, 13: 0}
GATE_Z = (6, 7, 8)          # the gates cut through both sides (y 1-3)
DOCK = [(x, z) for x in (1, 2, 3) for z in range(5, 11)]
MAIN_Z, FORE_Z = 9, 11      # the masts stand on the centre line (x = 7), clear of the hatch over the spawn

# BuildingAmbienceClientEvents.CAPITOL_SPOTS["wreck_harbour"] (structure coordinates, a block spans y..y+1)
SPOTS = (("GLINT", (7.5, 22.1, 2.5)),       # just over the vane on the lamp room
         ("SPLASH", (2.0, 1.15, 6.5)),      # on the dock
         ("SPLASH", (2.0, 1.15, 9.5)))
STARTING_TYPES = {SS, PB}                   # WreckHarbour.startingBlockTypes


def hsh(x, z):
    return ((x + 31) * 73856093 ^ (z + 17) * 19349663) % 101


class Rig(gs._SunRig):
    def slab(self, x, y, z, name, typ="bottom"):
        self.set(x, y, z, name, type=typ, **DRY)

    def log(self, x, y, z, name, axis):
        self.set(x, y, z, name, axis=axis)

    def empty(self, x, y, z):
        return self.get(x, y, z)[0] in ("air", "cave_air")


def in_hull(x, z):
    return z in HW and abs(x - C) <= HW[z]


def hull_side(x, z):
    return z in HW and abs(x - C) == HW[z]


# ========================================================================================================== ground
def shore(r):
    """Layer 0 everywhere: the hold floor under the hull, the lighthouse footing, the dock water, and round them the
    shore. Its outer two rings are loose sand-coloured stone, clay and coral rubble that thin out into whatever ground
    the capitol stands on (no kerb, no road: the same on every side); inside them a paving of smooth sandstone with
    prismarine-brick bands - the two starting types, so the instant foundation is the paving itself."""
    for x in range(W):
        for z in range(W):
            d = min(x, W - 1 - x, z, W - 1 - z)
            h = hsh(x, z)
            if d == 0:
                name = (SA, SS, SA, SA, "dead_brain_coral_block", SS, "clay", SA, SA, SS)[h % 10]
            elif d == 1:
                name = (SS, SS, SA, CS, SS, SS, SA, SS)[h % 8]
            else:
                name = PB if (x + z) % 4 == 0 else SS
            r.set(x, 0, z, name)
    # the shore's sea-lantern inlays: lit flags at the four corners of the paving
    for z, hw in HW.items():
        for x in range(C - hw, C + hw + 1):
            r.set(x, 0, z, DP if abs(x - C) == hw else (MAP if x % 2 else DOP))
    r.set(6, 0, 10, LAMP)          # the hold's floor lamps, either side of the spawn's walk out
    r.set(8, 0, 5, LAMP)
    for x in range(5, 10):
        for z in range(0, 4):
            r.set(x, 0, z, DP)
    for (x, z) in DOCK:
        r.set(x, 0, z, "water", level="0")


def dressing(r):
    """Washed up on the outer ring: dead coral, coral rubble, driftwood and sandstone slabs - single low pieces with
    open sand round them (never a closed cell). Only on empty cells over a full block."""
    for x in range(W):
        for z in range(W):
            d = min(x, W - 1 - x, z, W - 1 - z)
            if d > 1 or not r.empty(x, 1, z) or r.get(x, 0, z)[0] in ("water", LAMP):
                continue
            h = hsh(x * 3 + 1, z * 5 + 2)
            k = h % (9 if d == 0 else 16)
            if k == 0:
                r.set(x, 1, z, "dead_brain_coral_fan", **DRY)
            elif k == 1:
                r.set(x, 1, z, "dead_tube_coral", **DRY)
            elif k == 2:
                r.set(x, 1, z, "dead_horn_coral_fan", **DRY)
            elif k == 3 and d == 0:
                r.log(x, 1, z, SDL, "x" if x in (0, W - 1) else "z")          # a driftwood log, lying along the edge
            elif k == 4 and d == 0:
                r.slab(x, 1, z, "sandstone_slab")


# ============================================================================================================ hull
def hull(r):
    """The galleon's hull, y 1-6, banded strake by strake (three depths: the rubbing strake and cannon standing
    proud, the planking, the gates and the ribbed openings over them)."""
    for z, hw in HW.items():
        for s in (-1, 1) if hw else (0,):
            x = C + s * hw
            out = "west" if s < 0 else "east" if s > 0 else "south"
            inward = {"west": "east", "east": "west", "south": "north"}[out]
            gate = z in GATE_Z and hw == 3
            # y1: the barnacled bilge strake, upside-down so the hull narrows to the keel
            if not gate:
                r.stair(x, 1, z, inward, "top", "dark_prismarine_stairs")
                r.set(x, 2, z, MAP)
                r.log(x, 3, z, SDL, "z" if hw else "y")                   # the wale
            # y4: ribs between lit portholes (the planks salvaged away)
            if gate:
                r.log(x, 4, z, SDL, "z")                                 # the gate's lintel beam
            elif z % 2 == 0 or hw < 3:
                r.log(x, 4, z, SDL, "y")                                 # a rib
            else:
                r.set(x, 4, z, LAMP)                                     # a porthole
            r.set(x, 5, z, DOP)
            if z >= 11:                                                  # the bow rises: a bulwark, then the rail
                r.set(x, 6, z, MAP)
                if hw:
                    r.set(x, 7, z, ROPE)
            elif z >= 6:
                r.set(x, 6, z, ROPE)
        # the transom closes the stern
        if z == 4:
            for x in range(C - 2, C + 3):
                r.set(x, 1, z, DP)
                r.set(x, 2, z, MAP)
                r.log(x, 3, z, SDL, "x")
                r.set(x, 4, z, DOP)
                r.set(x, 5, z, DOP)
    # the rubbing strake: upside-down stairs standing proud of the wale (a shadow line along both sides)
    for z, hw in HW.items():
        if hw == 3 and z not in GATE_Z and z != 4:
            r.stair(C - 4, 3, z, "east", "top", "dark_oak_stairs")
            r.stair(C + 4, 3, z, "west", "top", "dark_oak_stairs")
    # the gunwale cap: upside-down stairs overhanging both long sides at y5 (a hood over the gates and the portholes,
    # the hull's top edge in shadow - the third depth of the BUILDING STANDARD)
    for z, hw in HW.items():
        if hw == 3 and z != 4:
            r.stair(C - 4, 5, z, "east", "top", "dark_oak_stairs")
            r.stair(C + 4, 5, z, "west", "top", "dark_oak_stairs")
    # the gates: rib jambs either side, splintered plank ends hanging into the opening, a gangplank across the dock
    for s in (-1, 1):
        x = C + 3 * s
        for z in (5, 9):
            for y in (1, 2, 3):
                r.log(x, y, z, SDL, "y")
        r.stair(x, 3, 6, "south", "top", "dark_oak_stairs")
        r.stair(x, 3, 8, "north", "top", "dark_oak_stairs")
    for x in (0, 1, 2, 3):
        r.slab(x, 1, 7, "mangrove_slab")
    # the main deck (y5), the cargo hatch over the spawn open to the sky, a coaming round it
    for z, hw in HW.items():
        for x in range(C - hw + 1, C + hw):
            if not (abs(x - C) <= 1 and 6 <= z <= 8):
                r.set(x, 5, z, MAP if x % 2 else DOP)
    for x in range(C - 2, C + 3):
        for z in range(5, 10):
            if max(abs(x - C), abs(z - 7)) == 2 and z > 5 and (x, z) != (C, MAIN_Z):
                r.slab(x, 6, z, "mangrove_slab")
    # the forecastle: the bow's deck raised a half storey over the waist (a galleon's profile: high stern, low waist,
    # raised forecastle), a step of stairs up to it from the waist
    for (x, z) in ((C - 1, 11), (C + 1, 11), (C, 12)):
        r.slab(x, 6, z, "dark_oak_slab", "top")
    for x in (C - 1, C + 1):
        r.stair(x, 6, 10, "south", "bottom", "dark_oak_stairs")
    # the hold: barrels and kelp bales against the walls, a lantern on a barrel; the walk from the spawn to both gates
    # (z 6-8) stays clear
    for (x, z) in ((5, 5), (9, 5), (5, 10), (9, 10), (6, 5)):
        r.set(x, 1, z, "barrel", facing="up", open="false")
    r.set(9, 2, 5, "barrel", facing="up", open="false")
    r.set(9, 1, 9, "dried_kelp_block")
    r.set(5, 2, 5, "lantern", **LANTERN)
    # chains from the deck beams into the hold (the hold's cargo tackle)
    for y in (2, 3, 4):
        r.set(5, y, 9, "chain", axis="y", **DRY)


def stern_castle(r):
    """The stern castle over the transom (z 4-5, y 6-9): glowing stern windows on every camera face, a cornice of
    upside-down stairs, a poop deck with a rope rail."""
    for x in range(C - 3, C + 4):
        for z in (4, 5):
            edge = x in (C - 3, C + 3)
            r.set(x, 6, z, MAP)
            r.set(x, 8, z, DOP)
            if z == 5 and not edge and x != C:
                r.set(x, 7, z, "light_blue_stained_glass")
            elif z == 4 and not edge:
                r.set(x, 7, z, LAMP)                         # the light behind the stern windows
            elif edge and z == 5:
                r.set(x, 7, z, "light_blue_stained_glass")
            else:
                r.log(x, 7, z, SDL, "y")
    # side windows show the lamp too: the west / east glass at z 5 has the sea lantern at z 4 beside it
    # the cornice and poop deck
    for x in range(C - 3, C + 4):
        r.stair(x, 9, 5, "north", "top", "dark_oak_stairs")
        r.slab(x, 9, 4, "dark_oak_slab", "top")
    for z in (4, 5):
        r.stair(C - 4, 8, z, "east", "top", "dark_oak_stairs")
        r.stair(C + 4, 8, z, "west", "top", "dark_oak_stairs")
    for x in range(C - 3, C + 4):
        if x in (C - 3, C + 3):
            r.log(x, 10, 5, SDL, "y")
            r.set(x, 11, 5, "lantern", **LANTERN)
        elif not (C - 1 <= x <= C + 1):
            r.set(x, 10, 5, ROPE)


# ======================================================================================================= lighthouse
def lighthouse(r):
    """The striped lighthouse rising from the stern (x 6-8, z 1-3): an octagonal shaft - the corners are walls of the
    band's stone, so it never reads as a square pillar - in two-row bands of cut sandstone and prismarine, glowing slit
    windows on the camera faces, a corbelled gallery with a rope rail, the sea-lantern lamp room behind blue glass, a
    prismarine cap and a copper vane. A splayed plinth of stairs at its foot."""
    x0, z0 = C - 1, 1
    for y in range(1, 15):
        sand = (y - 1) // 2 % 2 == 0
        block, wall = (CS, "sandstone_wall") if sand else (DP, "prismarine_wall")
        for x in range(x0, x0 + 3):
            for z in range(z0, z0 + 3):
                corner = x != C and z != 2
                r.set(x, y, z, wall if corner else block)
    for y in (4, 8, 12):                 # slit windows on the south and west faces, the lamp core behind them
        r.set(C, y, 3, "light_blue_stained_glass")
        r.set(C - 1, y, 2, "light_blue_stained_glass")
        r.set(C, y, 2, LAMP)
    # the plinth: stairs splaying out at the foot on the three free sides (the stern is the fourth)
    for z in range(1, 4):
        r.stair(C - 2, 1, z, "east", name="dark_prismarine_stairs")
        r.stair(C + 2, 1, z, "west", name="dark_prismarine_stairs")
    for x in range(C - 2, C + 3):
        r.stair(x, 1, 0, "south", name="dark_prismarine_stairs")
    # corbels (y14) under the gallery floor (y15), a rope rail round it with lamp posts on the corners
    for x in range(C - 2, C + 3):
        for z in range(0, 5):
            ring = max(abs(x - C), abs(z - 2))
            if ring == 2:
                f = "east" if x == C - 2 else "west" if x == C + 2 else "south" if z == 0 else "north"
                r.stair(x, 14, z, f, "top", "dark_prismarine_stairs")
                r.slab(x, 15, z, "dark_prismarine_slab", "top")
                if (abs(x - C), abs(z - 2)) == (2, 2):
                    r.set(x, 16, z, "prismarine_wall")
                    r.set(x, 17, z, "lantern", **LANTERN)
                else:
                    r.set(x, 16, z, ROPE)
            else:
                r.set(x, 15, z, DP)
    # the lamp room: prismarine posts on the corners, blue glass on the faces, a column of sea lanterns
    for y in (16, 17, 18):
        for x in range(C - 1, C + 2):
            for z in range(1, 4):
                if (x, z) == (C, 2):
                    r.set(x, y, z, LAMP)
                elif x != C and z != 2:
                    r.set(x, y, z, "prismarine_wall")
                else:
                    r.set(x, y, z, "light_blue_stained_glass_pane")
    # the cap: a ring of stairs, the dome, the vane
    for x in range(C - 1, C + 2):
        for z in range(1, 4):
            if (x, z) == (C, 2):
                r.set(x, 19, z, DP)
            else:
                f = "east" if x == C - 1 else "west" if x == C + 1 else "south" if z == 1 else "north"
                r.stair(x, 19, z, f, name="dark_prismarine_stairs")
    r.set(C, 20, 2, "prismarine_wall")
    r.set(C, 21, 2, "lightning_rod", facing="up", powered="false", **DRY)


# ============================================================================================================ masts
def sail(r, cells, stripe_x=C):
    for (x, y, z) in cells:
        r.set(x, y, z, "light_blue_wool" if x == stripe_x else "white_wool")


def masts(r):
    """The mainmast and the foremast: stripped dark-oak masts with fence topmasts, yards, and TATTERED SAILS - one
    clean plane of white wool hung just forward of each mast under its yard, the fleet's blue stripe down the middle,
    holes torn through and a ragged foot. CHAIN SHROUDS run from each yard arm down to the gunwale (the kit's chain
    and rope), a lantern burns at the main masthead and a pennant flies from the fore."""
    # the mainmast (on the deck at z 9): a course and a topsail
    for y in range(6, 15):
        r.log(C, y, MAIN_Z, SDL, "y")
    for y in (15, 16):
        r.set(C, y, MAIN_Z, "dark_oak_fence")
    r.set(C, 17, MAIN_Z, "lantern", **LANTERN)
    for x in range(C - 3, C + 4):
        if x != C:
            r.log(x, 11, MAIN_Z, SDL, "x")        # the course yard, its arms over the gunwales
    for x in range(C - 2, C + 3):
        if x != C:
            r.log(x, 15, MAIN_Z, SDL, "x")        # the topsail yard
    z = MAIN_Z + 1
    torn = {(C - 2, 8), (C + 1, 9), (C + 3, 7), (C + 2, 7), (C - 3, 7), (C, 7)}
    sail(r, [(x, y, z) for y in (7, 8, 9, 10) for x in range(C - 3, C + 4) if (x, y) not in torn])
    torn = {(C + 2, 12), (C - 1, 13)}
    sail(r, [(x, y, z) for y in (12, 13, 14) for x in range(C - 2, C + 3) if (x, y) not in torn])
    # the shrouds: chains from the course yard's arms down to the gunwale's rope rail
    for s in (-1, 1):
        for y in (7, 8, 9, 10):
            r.set(C + 3 * s, y, MAIN_Z, "chain", axis="y", **DRY)
        r.set(C + 3 * s, 6, MAIN_Z, "dark_oak_fence")
    # the foremast (z 11), its yard and a smaller, more torn sail
    for y in range(6, 13):
        r.log(C, y, FORE_Z, SDL, "y")
    r.set(C, 13, FORE_Z, "dark_oak_fence")
    r.set(C, 14, FORE_Z, "light_blue_banner", rotation="0")
    for x in range(C - 2, C + 3):
        if x != C:
            r.log(x, 12, FORE_Z, SDL, "x")
    torn = {(C - 2, 9), (C + 1, 10), (C + 2, 9), (C + 2, 8), (C - 1, 8)}
    sail(r, [(x, y, FORE_Z + 1) for y in (8, 9, 10, 11) for x in range(C - 2, C + 3) if (x, y) not in torn])
    for s in (-1, 1):
        for y in (8, 9, 10, 11):
            r.set(C + 2 * s, y, FORE_Z, "chain", axis="y", **DRY)


# ============================================================================================================= prow
def prow(r):
    """The stem post rising in a curve to the bowsprit, and under it the figurehead - a prismarine sea-maiden whose
    arms (walls joined to the lamp) lift the Admiral's beacon, a sea lantern, over her head - and the two ANCHORS
    resting on the sand either side of the bow, iron-dark (polished deepslate: the anchor is the one iron thing on the
    wreck, the colour of the chain), their cables run in chains up to the hawse holes."""
    for y in range(1, 7):
        r.log(C, y, 13, SDL, "y")
    r.stair(C, 7, 13, "north", "bottom", "dark_oak_stairs")          # the stem curling forward
    r.log(C, 8, 13, SDL, "z")
    r.log(C, 8, 14, SDL, "z")                                         # the bowsprit
    r.set(C, 9, 14, "dark_oak_fence")
    r.set(C, 10, 14, "light_blue_banner", rotation="0")              # the jack
    # the figurehead on the cutwater (z 14): tail, body, head, arms up holding the lamp
    r.stair(C, 2, 14, "north", "top", "dark_prismarine_stairs")      # the tail curling back under
    r.stair(C, 3, 14, "south", "bottom", "prismarine_brick_stairs")  # the hips, leaning out
    r.set(C, 4, 14, "prismarine_wall")                               # the waist
    r.set(C, 5, 14, PR)                                              # the shoulders and head
    r.set(C, 6, 14, LAMP)                                            # the beacon held up
    r.set(C - 1, 5, 14, "prismarine_wall")                           # the arms, joined to the lamp's sides
    r.set(C + 1, 5, 14, "prismarine_wall")
    r.stair(C - 1, 6, 14, "east", "top", "prismarine_brick_stairs")  # the hands round the lamp
    r.stair(C + 1, 6, 14, "west", "top", "prismarine_brick_stairs")
    for cx in (C - 3, C + 3):
        r.set(cx, 1, 13, "polished_deepslate")                        # the crown, sunk in the sand
        r.stair(cx - 1, 1, 13, "west", "bottom", "polished_deepslate_stairs")   # the flukes curving up
        r.stair(cx + 1, 1, 13, "east", "bottom", "polished_deepslate_stairs")
        r.set(cx - 1, 2, 13, "chain", axis="y", **DRY)                # the bills
        r.set(cx + 1, 2, 13, "chain", axis="y", **DRY)
        for y in (2, 3):
            r.set(cx, y, 13, "polished_deepslate_wall")              # the shank
        for x in (cx - 1, cx, cx + 1):
            r.log(x, 4, 13, SDL, "x")                                 # the wooden stock
        r.set(cx, 5, 13, "chain", axis="y", **DRY)                    # the ring
        for x in range(min(cx, C) + 1, max(cx, C)):
            r.set(x, 6, 13, "chain", axis="x", **DRY)                 # the cable to the hawse


# ============================================================================================================= dock
def dock(r):
    """The still-water dock on the west (a camera side): the water is in layer 0 (sunk into the ground, solid ground
    under it), walled on every side at that layer by the shore and the hull's foundation. Round it a LOW breakwater -
    slabs, coral rubble, a stair - so the water shows; glowing bollards (a sea lantern capped with a slab, a chain
    mooring line) at its four corners."""
    for z in range(4, 12):
        if z == 7:
            continue                                                   # the gangplank lands here
        k = hsh(0, z) % 3
        if z in (4, 11):
            continue
        r.slab(0, 1, z, ("prismarine_slab", "dark_prismarine_slab", "prismarine_brick_slab")[k])
    r.set(0, 1, 9, "dead_tube_coral_block")
    for x in (0, 1, 2, 3):
        for z in (4, 11):
            if x in (0, 3):
                r.set(x, 1, z, LAMP)                                   # the bollard
                r.slab(x, 2, z, "dark_prismarine_slab")
            else:
                r.stair(x, 1, z, "north" if z == 11 else "south", "bottom", "prismarine_brick_stairs")
    r.set(3, 1, 10, "dark_oak_fence")                                  # a mooring post on the hull side, a chain
    r.set(3, 2, 10, "chain", axis="y", **DRY)


# ============================================================================================================= yard
def salvage_yard(r):
    """The east side: a capstan, barrels, a stack of driftwood, lamp posts - leaving the east gate's way out (z 6-8)
    open to the shore."""
    r.log(12, 1, 4, SDL, "y")
    r.slab(12, 2, 4, "dark_oak_slab")
    r.set(11, 1, 4, "dark_oak_fence")
    r.set(13, 1, 4, "dark_oak_fence")
    for (x, z) in ((12, 1), (13, 2), (11, 11)):
        r.set(x, 1, z, "barrel", facing="up", open="false")
    r.set(12, 2, 1, "barrel", facing="up", open="false")
    for z in (9, 10):
        r.log(13, 1, z, SML, "z")
    r.log(13, 2, 10, SML, "z")
    r.log(12, 1, 10, SDL, "z")
    for (x, z) in ((11, 2), (11, 12)):
        r.set(x, 1, z, LAMP)                                           # glowing bollards, slab-capped
        r.slab(x, 2, z, "dark_prismarine_slab")
    # the north shore behind the lighthouse: barrels, coral rubble, a kelp bale
    for (x, z) in ((2, 1), (3, 2)):
        r.set(x, 1, z, "barrel", facing="up", open="false")
    r.set(2, 1, 2, "dried_kelp_block")
    r.set(3, 1, 1, "dead_horn_coral_block")


def build():
    r = Rig(W, H, W)
    shore(r)
    hull(r)
    stern_castle(r)
    lighthouse(r)
    masts(r)
    prow(r)
    dock(r)
    salvage_yard(r)
    dressing(r)
    r.settle()
    r.check_support("wreck_harbour")
    check_corals(r)
    return r.structure()


def check_corals(r):
    bad = [p for p, (n, _) in r.b.items() if "coral" in n and not n.endswith("_block")
           and not gs._sun_sturdy(r.get(p[0], p[1] - 1, p[2]), "up")]
    assert not bad, "corals with no full block under them: %s" % bad


# ============================================================================================================ rules
FORBIDDEN = ("lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater", "comparator",
             "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire", "dragon_egg",
             "scaffolding", "pointed_dripstone", "magma_block")


def check(s, box=(W, H, W)):
    assert s.size == box, "the box is %s, expected %s" % (s.size, box)
    names = {(x, y, z): s.palette[i] for (x, y, z), i in s.blocks.items() if s.palette[i][0] != "air"}
    n = len(names)
    assert n < 2000, n
    assert max(p[0] for p in names) == box[0] - 1 and max(p[1] for p in names) == box[1] - 1 \
        and max(p[2] for p in names) == box[2] - 1, "the drawn blocks do not fill the box"
    nm = {p: v[0] for p, v in names.items()}
    layer0 = {v for (x, y, z), v in nm.items() if y == 0}
    assert STARTING_TYPES <= layer0, "starting types missing from layer 0: %s" % (STARTING_TYPES - layer0)
    above = {v for (x, y, z), v in nm.items() if y > 0} & STARTING_TYPES
    assert not above, "a starting type above layer 0: %s" % above
    bad = sorted({v for v in nm.values() if any(v == f or v.endswith(f) for f in FORBIDDEN)})
    assert not bad, "forbidden blocks: %s" % bad
    # water: still sources in layer 0 only, every horizontal side non-air (ground round the footprint counts: the
    # cells outside the box are the world's ground at layer 0)
    for (x, y, z), (v, props) in names.items():
        if v == "water":
            assert y == 0 and dict(props).get("level") == "0", (x, y, z)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, 0, z + dz)
                assert q in nm, "water at %s has an open side" % ((x, y, z),)
        elif dict(props).get("waterlogged") == "true":
            raise AssertionError("waterlogged block at %s" % ((x, y, z),))
    # the spawn: the box centre walked down while the block below is air, onto the hold floor
    cx, cz = box[0] // 2, box[2] // 2
    y = (box[1] - 1) // 2
    while y > 0 and (cx, y - 1, cz) not in nm:
        y -= 1
    assert y == 1 and (cx, 1, cz) not in nm and (cx, 2, cz) not in nm and (cx, 3, cz) not in nm, \
        "the centre spawn lands at y%d" % y
    for kind, (x, y, z) in SPOTS:
        cell = (int(x), int(y - 0.2) if kind == "GLINT" else int(y) - 1, int(z))
        assert cell in nm, "nothing under the %s spot %s" % (kind, (x, y, z))
    return n


def audit_pockets(path):
    import audit_structure_pockets as asp
    return asp.audit("wreck_harbour", path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/wreck_harbour.nbt"
    st = build()
    print("blocks:", check(st))
    st.write(out)
    one_way, ground, roof, rooms, note = audit_pockets(out)
    print("pockets: one-way %d, ground %d, roof-only %d, rooms %d%s" % (len(one_way), len(ground), len(roof),
                                                                        len(rooms), note))
    assert not one_way and not ground and "TRAPPED" not in note, (one_way[:8], ground[:8], note)
    print("wrote", out)
