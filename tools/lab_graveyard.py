#!/usr/bin/env python3
"""The Gravebound T1 army lab, the GRAVEYARD, redrawn as one family with the Necropolis Cathedral (the capitol,
tools/capitol_mausoleum.py) and the Bone-Ribbed Well (the extractor, tools/mex_grave.py): its L1 (and the Overflowing
Graveyard it turns into), and the levels L2 and L3 drawn over it.

lovish (Oct 19 2026): buildings must "look like they match each other so when bases get bigger they look cohesive and
beautiful"; "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out for
the opponent; ... much bigger or have cooler, more intricate designs". The old L1 was RoN's flat grass plot with mossy
stone tombstones (4 high) - it read as a garden, not the Death faction - and its old L2 / L3 (tools/gen_lab_levels.py
grave_graveyard) built crypts and a hall straight over the yard cells the Graveyard's overflow puts its skulls on.

THE GRAVEBOUND STYLE KIT (the same on every Gravebound building):
  - main blocks: deepslate (bricks / tiles / polished) and blackstone (polished / bricks / chiseled);
  - accent: BONE (bone blocks, smooth-quartz stairs as the bone's rounded bends - the extractor's trick);
  - light: SOUL LANTERNS and a GREEN GLOW (verdant froglight behind green glass);
  - silhouette motif: SPIRES (spirelets with soul lanterns, needles of blackstone wall);
  - signature detail: CHAINS and SKULLS.

THE DESIGN (inspiration only, never traced: ref 42 the gothic castle's layered depth, ref 44 the dark wall with chains,
soul lanterns and finials, ref 47 the gargoyles, ref 73 the cathedral buttresses). The camera looks from the south-west,
so the yard opens to the south and west and the masses stand along the north and east, rising to the north-east:
  L1  THE OSSUARY YARD - a grave plot walled in iron railings on blackstone posts behind a pointed bone-and-blackstone
      gate (south) with a crouched gargoyle; graves in rows (dark headstones, grave slabs, candles) round the open
      plots where the dead rise; the old mass-grave stones become an ossuary wall (north) and a stela (east), the
      skull racks the overflow hangs its skulls on; a small crypt in the north-east corner with a soul-lit doorway, a
      tiled pyramid roof and a spirelet.
  Overflowing (the research) - the yard spills over: the kerb turns to deepslate tiles (the marker the game reads),
      fresh-dug graves, bones in the soil, the crypt's door glows green, more grave lights and candles.
  L2  THE CATACOMB GATE - the ossuary wall and the stela grow into two crypt wings (north and east) with tiled gable
      roofs, skull niches and spirelets; the gate becomes a taller gatehouse with pinnacles and chains; the crypt
      rises a storey.
  L3  THE BONE SPIRE - the north wing becomes a dark chapel with a green-glowing rose window under a gable, the crypt
      becomes a bone-ribbed tower with a tall spire (soul fire on top), gargoyles on the tower and the gate, flying
      buttresses with chains over the east wing - the tallest thing in a Gravebound base after the capitol.

HOW THE GRAVEYARD WORKS (bar/.../building/buildings/placements/GraveyardPlacement.java) - kept working in every level:
  - once overflowing (research, or any level >= 2: Graveyard.getUpgradeLevel), each unit trained becomes a SKULL on
    the next free cell of skullPoses (ten standing skulls on the yard floor, y1) and, with Mass Burial, wallSkullPoses
    (five wall skulls facing south on the north rack, five facing west on the east rack); the release turns them back
    into units. A skull is set wherever the cell is not already a skull - a building block there would be OVERWRITTEN
    (the old L2 / L3 put crypt walls on seven of these cells). So every skull cell is air in every level, every floor
    skull has ground under it, every wall skull its rack block behind it, and the cell over each floor skull stays
    open so the risen dead are seen (SKULL_CELLS, asserted).
  - Graveyard.getUpgradeLevel reads the overflow from the blocks: deepslate_tiles or chiseled_deepslate. The plain L1
    has neither (tile stairs / slabs / walls are other blocks); the Overflowing Graveyard has both.
  - the unit spawn (ProductionBuilding.getIndoorSpawnPoint) is the L1 box's centre walked down: the yard's middle
    2 x 2 stays open to the sky in every level, on the yard floor, with a way out through the gate.

WHAT THE GAME NEEDS (this file edits nothing; the coordinator applies these):
  - tools/gen_lab_levels.py: LABS["graveyard"] = lab_graveyard.levels, and its __main__ also writes bases()
    (graveyard.nbt, overflowing_graveyard.nbt) into both structure trees - the L1 is generated here now;
  - Graveyard.java: buildTimeBlocks = BUILD_TIME_BLOCKS (99, RoN's block count) so the richer L1 builds as fast as
    before (BuildingPlacement times construction by it); startingBlockTypes (deepslate bricks) stays: the kerb, layer 0;
  - nothing else reads the graveyard's blocks: GraveyardPlacement's skull cells are kept (above), the spawn is the box
    centre (kept open), BuildingAmbienceClientEvents breathes labs from their roofline (no fixed spots).

Usage: python3 tools/lab_graveyard.py [OUT_DIR]  -> graveyard.nbt, overflowing_graveyard.nbt, graveyard_l2.nbt,
graveyard_l3.nbt (default: a temp folder), with every check and the pocket audit.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

SX, SZ = 8, 8                  # the Graveyard's footprint (RoN's): every level keeps it
YMAX = 24

DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
HANG = dict(hanging="true", waterlogged="false")
CHAIN_Y = dict(axis="y", waterlogged="false")
CHAIN_X = dict(axis="x", waterlogged="false")
CHAIN_Z = dict(axis="z", waterlogged="false")
SOUL_FIRE = dict(lit="true", facing="south", signal_fire="false", waterlogged="false")
# the kit's blocks by role
DB, CDB, PD, CD = "deepslate_bricks", "cracked_deepslate_bricks", "polished_deepslate", "cobbled_deepslate"
DT, CHD = "deepslate_tiles", "chiseled_deepslate"          # the overflow markers (never in the plain L1)
PBB, CPB, PB, BS = "polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone", "blackstone"
PBW, DTW, DBW = "polished_blackstone_wall", "deepslate_tile_wall", "deepslate_brick_wall"
DTS, DBS, PBS, PBBS = "deepslate_tile_stairs", "deepslate_brick_stairs", "polished_blackstone_stairs", \
    "polished_blackstone_brick_stairs"
BONE, BEND = "bone_block", "smooth_quartz_stairs"         # bone, and its rounded bends (the extractor's trick)
GLASS, GLOW = "green_stained_glass", "verdant_froglight"
# standing skull rotation (16ths of a turn, 0 = facing south): toward the camera (south-west) and the faces
TO_CAMERA, S_SOUTH, S_WEST = "2", "0", "4"

# ---- the Graveyard's skull cells (GraveyardPlacement: relative pos == structure pos, see the module docstring) ----
FLOOR_SKULLS = [(1, 1, 3), (2, 1, 3), (1, 1, 5), (2, 1, 5), (3, 1, 4), (4, 1, 2), (4, 1, 3), (5, 1, 3), (4, 1, 6),
                (5, 1, 6)]
WALL_SKULLS_S = [(2, 1, 2), (2, 2, 2), (2, 3, 2), (3, 2, 2), (3, 3, 2)]     # facing south: hang on z - 1
WALL_SKULLS_W = [(5, 1, 4), (5, 2, 4), (5, 3, 4), (5, 2, 5), (5, 3, 5)]     # facing west: hang on x + 1
SKULL_CELLS = FLOOR_SKULLS + WALL_SKULLS_S + WALL_SKULLS_W
RACK = [(x, y, z - 1) for (x, y, z) in WALL_SKULLS_S] + [(x + 1, y, z) for (x, y, z) in WALL_SKULLS_W]
# the spawn: the centre of the L1 box (x, z 3..4 depending on rotation and sign) walked down to the yard floor
SPAWN_COLUMNS = [(3, 3), (4, 3), (3, 4), (4, 4)]
GATE = [(3, 7), (4, 7)]        # the way out, two wide, two high


class Lvl:
    """One level drawn as a dict of (x, y, z) -> (name, props); next() copies it for the level drawn over it."""

    def __init__(self, blocks=None):
        self.b = dict(blocks or {})

    def next(self):
        return Lvl(self.b)

    def set(self, x, y, z, name, **props):
        assert 0 <= x < SX and 0 <= z < SZ and 0 <= y < YMAX, (x, y, z, name)
        self.b[(x, y, z)] = (name, props)

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air", {}))

    def empty(self, x, y, z):
        return self.get(x, y, z)[0] == "air"

    def put(self, x, y, z, name, **props):
        if self.empty(x, y, z):
            self.set(x, y, z, name, **props)

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def stair(self, x, y, z, facing, half="bottom", name=DBS):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", waterlogged="false")

    def slab(self, x, y, z, name="deepslate_tile_slab", kind="bottom"):
        self.set(x, y, z, name, type=kind, waterlogged="false")

    def fill(self, x1, y1, z1, x2, y2, z2, name, **props):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **props)

    def lantern(self, x, y, z, hanging=False):
        self.set(x, y, z, "soul_lantern", **(HANG if hanging else LANTERN))

    def skull(self, x, y, z, rot=TO_CAMERA, wither=False):
        assert (x, y, z) not in SKULL_CELLS, "a decorative skull on a skull cell would count as a stored unit"
        self.set(x, y, z, "wither_skeleton_skull" if wither else "skeleton_skull", rotation=rot)

    def candle(self, x, y, z, n=2, colour="black"):
        self.set(x, y, z, colour + "_candle", candles=str(n), lit="true", waterlogged="false")

    def spirelet(self, x, z, y0, top, lamp=True):
        """The kit's spirelet (the extractor's corner spires): a chiseled die, a thin blackstone-wall shaft, a soul
        lantern on top."""
        self.set(x, y0, z, CPB)
        for y in range(y0 + 1, top):
            self.set(x, y, z, PBW)
        if lamp:
            self.lantern(x, top, z)
        else:
            self.set(x, top, z, PBW)

    def height(self):
        return max(y for (x, y, z), (n, _) in self.b.items() if n != "air") + 1


# ============================================================================================================ checks
def _centre_support(blk, face):
    """Vanilla canSupportCenter on `face` ('up' for a standing lantern / candle, 'down' for a hanging one)."""
    n, p = blk
    if gs._sun_sturdy(blk, face):
        return True
    if n.endswith(("_wall", "_fence", "_pane")) or n == "iron_bars":
        return True
    if n == "chain":
        return p.get("axis", "y") == "y"
    return False


SOIL = ("podzol", "dirt", "coarse_dirt", "rooted_dirt", "grass_block", "moss_block", "mud", "soul_soil", "soul_sand")
PLANTS = ("wither_rose", "dead_bush", "fern", "grass", "tall_grass")


def check_support(b, tag):
    """Nothing pops off: lanterns and candles on a centre support (hanging ones under one), plants on soil, carpets on
    something; no wall-mounted blocks (wall skulls / torches / banners) at all."""
    bad = []
    for (x, y, z), (n, p) in b.items():
        below = b.get((x, y - 1, z), ("air", {}))
        above = b.get((x, y + 1, z), ("air", {}))
        if n in ("lantern", "soul_lantern"):
            ok = _centre_support(above, "down") if p.get("hanging") == "true" else _centre_support(below, "up")
        elif n.endswith("candle"):
            ok = _centre_support(below, "up")
        elif n in PLANTS:
            ok = below[0] in SOIL
        elif n.endswith("_carpet"):
            ok = below[0] != "air"
        elif "wall_" in n and not n.endswith("_wall"):
            ok = False
        else:
            continue
        if not ok:
            bad.append(((x, y, z), n, below[0], above[0]))
    assert not bad, "%s: would pop off: %s" % (tag, bad)


FORBIDDEN = ("water", "lava", "sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater", "comparator",
             "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "scaffolding", "pointed_dripstone")


def check_level(b, tag, overflowing):
    names = {p: n for p, (n, _) in b.items() if n != "air"}
    xs = [p[0] for p in names]
    zs = [p[2] for p in names]
    assert min(xs) == 0 and max(xs) == SX - 1 and min(zs) == 0 and max(zs) == SZ - 1, "%s: footprint" % tag
    bad = sorted({n for n in names.values() if any(n == f or n.endswith(f) for f in FORBIDDEN)})
    assert not bad, "%s: forbidden blocks %s" % (tag, bad)
    # the overflow marker: Graveyard.getUpgradeLevel
    marked = any(n in (DT, CHD) for n in names.values())
    assert marked == overflowing, "%s: overflow marker present=%s" % (tag, marked)
    # the starting type (Graveyard.startingBlockTypes: deepslate bricks) in the foundation layer
    assert overflowing or any(n == DB for (x, y, z), n in names.items() if y == 0), "%s: no deepslate bricks in layer 0" % tag
    # the skull cells: air, ground under the floor ones, open over them; the racks solid behind the wall ones
    for c in SKULL_CELLS:
        assert c not in names, "%s: skull cell %s holds %s" % (tag, c, names[c])
    for (x, y, z) in FLOOR_SKULLS:
        assert (x, y - 1, z) in names and gs._sun_sturdy(b[(x, y - 1, z)], "up"), "%s: no ground under %s" % (tag, (x, y, z))
        assert (x, y + 1, z) not in names, "%s: the floor skull %s is covered" % (tag, (x, y, z))
    for c in RACK:
        assert c in names and gs._sun_sturdy(b[c], "up") and not names[c].endswith(("_stairs", "_slab")), \
            "%s: the skull rack %s is %s" % (tag, c, names.get(c))
    # the spawn: the centre columns open from the yard floor to the sky, the floor solid
    top = max(p[1] for p in names)
    for (x, z) in SPAWN_COLUMNS:
        assert (x, 0, z) in names, "%s: no floor at the spawn %s" % (tag, (x, z))
        for y in range(1, top + 1):
            if (x, y, z) not in SKULL_CELLS:
                assert (x, y, z) not in names, "%s: the spawn column %s is shut at y%d" % (tag, (x, z), y)
    for (x, z) in GATE:
        assert (x, 1, z) not in names and (x, 2, z) not in names, "%s: the gate is shut" % tag
    check_support(b, tag)
    return len(names)


def check_chain(lower, upper, tag):
    """BuildingPlacement.changeStructure only adds or replaces: every non-air cell below stays non-air."""
    lost = sorted(p for p, (n, _) in lower.items() if n != "air" and upper.get(p, ("air", {}))[0] == "air")
    assert not lost, "%s: the level below has blocks this level would leave behind: %s" % (tag, lost[:8])


def settle(lvl):
    """Stairs take their corner shapes, walls / bars / panes their joins (the game's own states once placed)."""
    r = gs._SunRig(SX, YMAX, SZ)
    r.b = dict(lvl.b)
    r.settle()
    return Lvl(r.b)


def structure(lvl, height=None):
    """The Structure with the whole box written (air included, as RoN's own blueprints: the box sets the placement's
    corners and so its centre, the spawn)."""
    h = height or lvl.height()
    s = gs.Structure(SX, h, SZ)
    for y in range(h):
        for z in range(SZ):
            for x in range(SX):
                n, p = lvl.get(x, y, z)
                s.set(x, y, z, n, **p)
    return s


# ============================================================================================================== L1
def _ground(c):
    """Layer 0: the deepslate-brick kerb (the starting type: the instant foundation) round grave soil - podzol, rooted
    and coarse dirt, moss; dark soul soil under the plots the dead rise from; deepslate flags on the path from the gate
    to the spawn."""
    soil = ("podzol", "podzol", "rooted_dirt", "podzol", "coarse_dirt", "podzol", "moss_block")
    for x in range(SX):
        for z in range(SZ):
            if x in (0, SX - 1) or z in (0, SZ - 1):
                corner = x in (0, SX - 1) and z in (0, SZ - 1)
                c.set(x, 0, z, PD if corner else DB)
            else:
                n = soil[(x * 5 + z * 3) % len(soil)]
                c.set(x, 0, z, n, **({"snowy": "false"} if n == "podzol" else {}))
    for (x, y, z) in FLOOR_SKULLS:
        c.set(x, 0, z, "soul_soil")                           # the plots: dark dug earth
    for (x, z) in ((3, 6), (3, 5), (4, 5), (3, 3), (4, 4)):
        c.set(x, 0, z, PD if (x + z) % 2 else CD)             # the path in from the gate


def _ossuary_wall(c):
    """North: the ossuary wall - the old tombstone's skull rack. A deepslate wall (z=0) with a rack of BONE on its face
    (x2..3, z=1: the five south-facing wall skulls of the overflow hang on it), a blackstone corner pier (x0) and a pier
    at x1, a coping of brick stairs, soul lanterns on the piers and a chain slung along the top (the kit's chains)."""
    for x in range(0, 5):
        for y in (1, 2, 3):
            c.set(x, y, 0, DB if (x + y) % 3 else CDB)
        c.slab(x, 4, 0, "deepslate_brick_slab")
    for x in (2, 3):
        for y in (1, 2, 3):
            c.set(x, y, 1, BONE, axis="y")
        c.stair(x, 4, 1, "north", name=DBS)
    for x in (0, 1):
        c.set(x, 1, 1, PBB)
        c.set(x, 2, 1, PBB)
        c.set(x, 3, 1, CPB)
        c.set(x, 4, 1, PBW)
    c.set(0, 4, 0, PBB)
    c.lantern(1, 5, 1)
    c.spirelet(0, 0, 5, 7)
    c.set(4, 4, 0, PBW)
    c.lantern(4, 5, 0)
    for x in (2, 3):
        c.set(x, 5, 1, "chain", **CHAIN_X)


def _crypt(c):
    """North-east: the crypt (x5..7, z0..2) - deepslate with blackstone quoins, a soul-lit doorway facing the yard and
    the camera (west): a pointed recess with a soul lantern hanging in it and the green glow at its back; a tiled
    pyramid roof and a spirelet."""
    for x in range(5, 8):
        for z in range(0, 3):
            for y in range(1, 5):
                c.set(x, y, z, DB)
    for (x, z) in ((5, 2), (7, 2), (5, 0), (7, 0)):
        for y in range(1, 5):
            c.set(x, y, z, CPB if y == 4 else PBB)
    c.clear(5, 1, 1)
    for z in (0, 2):                                          # bone door posts (the capitol's portals have them)
        c.set(5, 1, z, BONE, axis="y")
        c.set(5, 2, z, BONE, axis="y")
    c.lantern(5, 2, 1, hanging=True)
    c.set(6, 1, 1, GLOW, axis="y")
    c.set(6, 2, 1, GLASS)
    c.set(5, 3, 1, PBB)
    c.set(5, 4, 1, CPB)
    # a lancet on the south face, over the stela
    c.set(6, 2, 2, GLASS)
    c.set(6, 3, 2, GLASS)
    # the roof: a ring of tile stairs climbing to a blackstone die, the spirelet on it
    for x in range(5, 8):
        c.stair(x, 5, 0, "south", name=DTS)
        c.stair(x, 5, 2, "north", name=DTS)
    c.stair(5, 5, 1, "east", name=DTS)
    c.stair(7, 5, 1, "west", name=DTS)
    c.set(6, 5, 1, PBB)
    c.spirelet(6, 1, 6, 8)


def _stela(c):
    """East: the mass-grave stela - the second skull rack (five wall skulls facing west hang on x=6, z4..5): a two-wide
    deepslate slab with a chiseled cap and a bone-white skull watching the yard."""
    for z in (4, 5):
        for y in (1, 2, 3):
            c.set(6, y, z, DB)
            c.set(7, y, z, PBB if y == 1 else DB)
        c.stair(6, 4, z, "east", name=DBS)
        c.set(7, 4, z, PBB)
    c.set(7, 5, 4, CPB)
    c.skull(7, 6, 4, S_WEST)
    c.set(7, 5, 5, PBW)
    c.lantern(7, 6, 5)


def _railings(c):
    """The yard's fence: iron railings on the kerb between low blackstone posts (west, south, east) with soul lanterns
    on the corners, a wicket in the west side (z=4) beside the gate so the yard drains two ways."""
    for (x, z) in ((0, 7), (7, 7)):
        c.set(x, 1, z, PBB)
        c.set(x, 2, z, PBW)
    for (x, z) in ((0, 3), (0, 5), (7, 3)):
        c.set(x, 1, z, PBW)
        c.set(x, 2, z, PBW)
    for (x, z) in ((0, 7), (7, 7)):
        c.lantern(x, 3, z)
    for (x, z) in ((0, 2), (0, 6), (1, 7), (6, 7), (7, 6)):
        c.set(x, 1, z, "iron_bars")


def _gate(c):
    """South: the pointed gate of bone and blackstone (x3..4 open two high): bone piers on blackstone plinths, a pointed
    arch of bone (smooth-quartz bends) under a blackstone crest, a crouched gargoyle on the west pier (the camera's
    side), a soul lantern on the east one."""
    for x in (2, 5):
        c.set(x, 1, 7, PBB)
        c.set(x, 2, 7, BONE, axis="y")
        c.set(x, 3, 7, BONE, axis="y")
        c.set(x, 4, 7, CPB)
    c.stair(3, 3, 7, "west", "top", BEND)
    c.stair(4, 3, 7, "east", "top", BEND)
    c.stair(3, 4, 7, "east", name=PBBS)
    c.stair(4, 4, 7, "west", name=PBBS)
    # the gargoyle (ref 47): a blackstone haunch crouched on the pier, a bone-white skull head (reads on black)
    c.stair(2, 5, 7, "north", name=PBS)
    c.skull(2, 6, 7, TO_CAMERA)
    c.set(5, 5, 7, PBW)
    c.lantern(5, 6, 7)


def _graves(c):
    """The graves in rows round the open plots: headstones (blackstone and tile walls), grave slabs, candles. None on a
    skull cell, none in the path from the spawn to the gate."""
    stones = {(1, 2): PBW, (1, 4): DTW, (2, 4): PBW, (6, 6): DTW, (1, 6): DTW, (5, 5): PBW}
    for (x, z), n in stones.items():
        c.set(x, 1, z, n)
    c.candle(1, 2, 4, 3)
    c.candle(6, 2, 6, 1, "white")
    c.slab(2, 1, 6, "polished_blackstone_brick_slab")        # a grave slab at the foot of the west plots
    c.slab(3, 1, 2, "deepslate_tile_slab")                    # under the north rack's skulls
    c.skull(1, 1, 1, TO_CAMERA)                               # an old skull in the north-west corner


def _l1(c):
    _ground(c)
    _ossuary_wall(c)
    _crypt(c)
    _stela(c)
    _railings(c)
    _gate(c)
    _graves(c)


def _overflow(c):
    """The Overflowing Graveyard (the research, and every level): the yard spills over. The kerb turns to deepslate
    tiles with chiseled corners and the crypt door's lintel to chiseled deepslate (the markers Graveyard.getUpgradeLevel
    reads - RoN's own overflow look was the tiled kerb), bones surface in the soil and heap by the crypt, more grave
    candles."""
    for (x, z) in ((0, 0), (7, 0), (0, 7), (7, 7)):
        c.set(x, 0, z, CHD)
    for i in range(1, 7):                                     # the kerb turns to tiles all round (RoN's overflow look)
        for (x, z) in ((i, 0), (i, 7), (0, i), (7, i)):
            c.set(x, 0, z, DT)
    c.set(5, 3, 1, CHD)
    c.set(5, 0, 5, BONE, axis="x")
    c.set(2, 0, 1, BONE, axis="z")
    c.set(6, 0, 3, "soul_soil")
    c.candle(2, 2, 4, 2, "white")
    c.candle(1, 2, 2, 3)
    c.set(6, 1, 3, BONE, axis="z")                            # a heap of bones by the crypt
    c.skull(6, 2, 3, S_WEST)
    c.set(2, 1, 6, BONE, axis="x")


def l1():
    """The plain Graveyard, L1: the Ossuary Yard."""
    c = Lvl()
    _l1(c)
    c = settle(c)
    return c


def l1_overflowing():
    c = l1().next()
    _overflow(c)
    return settle(c)


# ============================================================================================================== L2
def _north_wing_l2(c):
    """The ossuary wall becomes a crypt range (x0..4, z0..1) two storeys high: the bone rack stays at its foot, a string
    course over it, a row of skull niches under stone hoods (the ossuary), a tiled A-roof; the corner pier grows into a
    spirelet, the end over the crypt door's forecourt into a pinnacle."""
    for x in range(0, 5):
        for y in (4, 5, 6):
            c.set(x, y, 0, DT if y > 4 else DB)
            c.set(x, y, 1, DT if y > 4 else DB)
    for y in (4, 5, 6):
        c.set(1, y, 1, PBB if y < 6 else CPB)
    c.set(0, 4, 1, PBB)
    for x in (2, 3):
        c.set(x, 4, 1, PBB)                                   # the string course over the rack
        c.skull(x, 5, 1, S_SOUTH)                             # the niches: skulls set in the wall's face
        c.stair(x, 6, 1, "north", "top", DBS)                 # their hoods
    c.set(4, 3, 1, PBB)                                       # a lintel over the crypt door's forecourt
    c.set(4, 6, 1, CPB)
    for x in range(0, 5):
        c.stair(x, 7, 0, "south", name=DTS)
        c.stair(x, 7, 1, "north", name=DTS)
    # the corner spirelet (NW) and the end pinnacle (x4)
    c.set(0, 5, 0, PBB)
    c.set(0, 6, 0, PBB)
    c.spirelet(0, 0, 7, 10)
    c.set(4, 7, 1, PBB)
    c.set(4, 8, 1, PBW)
    c.lantern(4, 9, 1)
    # soul lanterns on chains hang in front of the ossuary from stone corbels (the kit's chains)
    for x in (1, 4):
        c.stair(x, 6, 2, "north", "top", DBS)
        c.set(x, 5, 2, "chain", **CHAIN_Y)
        c.set(x, 4, 2, "chain", **CHAIN_Y)
        c.lantern(x, 3, 2, hanging=True)


def _east_wing_l2(c):
    """The stela becomes a crypt range (x6..7, z3..7): deepslate between blackstone end piers, the west-facing skull
    rack in its face with skull niches over it, a tiled A-roof, a south gable end toward the camera with a green-lit
    niche, a spirelet on the south-east corner and a pinnacle at the north end."""
    for z in range(3, 8):
        for x in (6, 7):
            for y in range(1, 6):
                if (x, y, z) not in SKULL_CELLS:
                    c.set(x, y, z, DB if y < 5 else DT)
    for z in (3, 7):
        for y in range(1, 6):
            c.set(6, y, z, CPB if y in (1, 5) else PBB)
    for z in (4, 5):
        c.skull(6, 4, z, S_WEST)                              # skull niches over the rack
        c.stair(6, 5, z, "east", "top", DBS)                  # their hoods
    c.set(6, 2, 6, GLASS)                                     # a lancet between the rack and the gable end
    c.set(7, 2, 6, GLOW, axis="y")
    # the south gable end (z = 7): a green-lit niche under a pointed head
    c.set(7, 1, 7, PBB)
    c.set(7, 2, 7, GLOW, axis="y")
    c.set(7, 3, 7, GLASS)
    c.set(7, 4, 7, PBB)
    for z in range(3, 8):
        c.stair(6, 6, z, "east", name=DTS)
        c.stair(7, 6, z, "west", name=DTS)
    c.spirelet(7, 7, 6, 9)
    c.set(6, 6, 3, PBB)
    c.set(6, 7, 3, PBW)
    c.lantern(6, 8, 3)


def _gatehouse_l2(c):
    """The gate becomes a gatehouse: the bone piers rise two blocks to chiseled capitals, a pointed blackstone gable
    over the arch, a buttress behind the west pier; the gargoyle climbs the west pier, a soul lantern crowns the east
    one."""
    for x in (2, 5):
        c.set(x, 4, 7, BONE, axis="y")
        c.set(x, 5, 7, BONE, axis="y")
        c.set(x, 6, 7, CPB)
    for x in (3, 4):
        c.set(x, 4, 7, PBB)
    c.stair(3, 5, 7, "east", name=PBBS)
    c.stair(4, 5, 7, "west", name=PBBS)
    for y in range(1, 4):                                     # the buttress behind the west pier
        c.set(2, y, 6, PBB)
    c.stair(2, 4, 6, "south", name=PBBS)
    c.stair(2, 7, 7, "north", name=PBS)                       # the gargoyle, up a storey
    c.skull(2, 8, 7, TO_CAMERA)
    c.set(5, 7, 7, PBW)
    c.lantern(5, 8, 7)


def _crypt_l2(c):
    """The crypt rises a storey: the roof ring becomes wall with a lit lancet, a blackstone band, a new tiled roof and
    a taller spirelet."""
    for x in range(5, 8):
        for z in range(0, 3):
            for y in (5, 6, 7):
                c.set(x, y, z, DB if y < 7 else PBB)
    for (x, z) in ((5, 2), (7, 2), (5, 0), (7, 0)):
        for y in (5, 6):
            c.set(x, y, z, PBB)
    c.set(5, 5, 1, GLASS)                                     # lancets west and south, lit from inside
    c.set(5, 6, 1, GLASS)
    c.set(6, 5, 1, GLOW, axis="y")
    c.set(6, 6, 1, GLOW, axis="y")
    c.set(6, 5, 2, GLASS)
    c.set(6, 6, 2, GLASS)
    for x in range(5, 8):
        c.stair(x, 8, 0, "south", name=DTS)
        c.stair(x, 8, 2, "north", name=DTS)
    c.stair(5, 8, 1, "east", name=DTS)
    c.stair(7, 8, 1, "west", name=DTS)
    c.set(6, 8, 1, PBB)
    c.spirelet(6, 1, 9, 12)


def _l2(c):
    """THE CATACOMB GATE."""
    _north_wing_l2(c)
    _east_wing_l2(c)
    _gatehouse_l2(c)
    _crypt_l2(c)


def l2():
    c = l1_overflowing().next()
    _l2(c)
    return settle(c)


# ============================================================================================================== L3
def _tower_l3(c):
    """The crypt becomes the BONE SPIRE: a tower to y12 with bone ribs on its corners and tall green lancets, a
    blackstone band with pinnacles and a gargoyle on its corners, then the spire - tile eaves, a tile collar, a chiseled
    die, a needle of blackstone wall - to soul fire at y20 (the base's beacon)."""
    for x in range(5, 8):
        for z in range(0, 3):
            for y in range(8, 12):
                c.set(x, y, z, DB if y < 11 else DT)
    for (x, z) in ((5, 2), (7, 2), (5, 0), (7, 0)):
        for y in range(5, 12):
            if y == 8:
                c.set(x, y, z, PBB)                           # a blackstone knuckle halfway up
            else:
                c.set(x, y, z, BONE, axis="y")                # the bone ribs
    for y in (6, 7, 9, 10):                                   # two tiers of green lancets, west and south
        c.set(5, y, 1, GLASS)
        c.set(6, y, 2, GLASS)
        c.set(6, y, 1, GLOW, axis="y")
    for (x, z) in ((5, 1), (6, 2)):
        c.set(x, 8, z, PBB)                                   # the string course between the tiers
    # a flying buttress (ref 73) from the east wing's north end leaps to the tower's south-east rib
    c.set(7, 7, 3, PBB)
    c.set(7, 8, 3, PBB)
    c.stair(7, 9, 3, "north", name=PBBS)
    for x in range(5, 8):
        for z in range(0, 3):
            c.set(x, 12, z, PBB)
    # the spire
    for x in range(5, 8):
        c.stair(x, 13, 0, "south", name=DTS)
        c.stair(x, 13, 2, "north", name=DTS)
    c.stair(5, 13, 1, "east", name=DTS)
    c.stair(7, 13, 1, "west", name=DTS)
    c.set(6, 13, 1, DT)
    c.stair(6, 14, 0, "south", name=DTS)
    c.stair(6, 14, 2, "north", name=DTS)
    c.stair(5, 14, 1, "east", name=DTS)
    c.stair(7, 14, 1, "west", name=DTS)
    c.set(6, 14, 1, DT)
    c.set(6, 15, 1, DT)
    c.set(6, 16, 1, CPB)
    for y in (17, 18, 19):
        c.set(6, y, 1, PBW)
    c.set(6, 20, 1, "soul_campfire", **SOUL_FIRE)
    # corner pinnacles, and the gargoyle on the camera's corner (south-west), leaning out over the yard
    for (x, z) in ((7, 0), (7, 2), (5, 0)):
        c.set(x, 13, z, PBW)
        c.lantern(x, 14, z)
    c.stair(5, 13, 2, "north", name=PBS)
    c.skull(5, 14, 2, TO_CAMERA)


def _chapel_l3(c):
    """The north wing becomes the chapel: walls to y9, the ROSE WINDOW on its south face (green glass round a verdant
    heart in a frame of bone) under a gablet with a wither skull, a tiled roof, the NW spirelet grown tall, a
    pinnacle at the east end."""
    for x in range(0, 5):
        for z in (0, 1):
            for y in range(7, 10):
                c.set(x, y, z, DT if y < 9 else DB)
    for y in range(7, 10):
        c.set(0, y, 1, PBB)
        c.set(4, y, 1, PBB)
    # the rose window, framed in bone (smooth-quartz bends)
    c.set(2, 7, 1, GLOW, axis="y")
    for (x, y) in ((1, 7), (3, 7), (2, 6), (2, 8)):
        c.set(x, y, 1, GLASS)
    c.stair(1, 8, 1, "west", "top", BEND)
    c.stair(3, 8, 1, "east", "top", BEND)
    c.stair(1, 6, 1, "west", name=BEND)
    c.stair(3, 6, 1, "east", name=BEND)
    # the roof
    for x in range(0, 5):
        c.stair(x, 10, 0, "south", name=DTS)
        c.stair(x, 10, 1, "north", name=DTS)
    # the gablet over the rose
    c.stair(1, 9, 1, "east", name=DTS)
    c.stair(3, 9, 1, "west", name=DTS)
    c.set(1, 10, 1, PBB)
    c.set(2, 10, 1, PBB)
    c.set(3, 10, 1, PBB)
    c.stair(1, 11, 1, "east", name=DTS)
    c.stair(3, 11, 1, "west", name=DTS)
    c.set(2, 11, 1, CPB)
    c.skull(2, 12, 1, TO_CAMERA, wither=True)
    # a green lancet in the west end
    for y in (6, 7):
        c.set(0, y, 0, GLASS)
        c.set(1, y, 0, GLOW, axis="y")
    # the spirelets
    for y in range(10, 13):
        c.set(0, y, 0, PBW)
    c.lantern(0, 13, 0)
    c.set(4, 10, 1, PBB)
    c.set(4, 11, 1, PBW)
    c.lantern(4, 12, 1)


def _gatehouse_l3(c):
    """The gatehouse's crown: the piers rise a storey (bone between blackstone knuckles), an open pointed arch of bone
    springs between them over the gable, the gargoyle climbs to the top of the west pier and soul fire burns in a
    brazier on the east one."""
    for x in (2, 5):
        c.set(x, 7, 7, BONE, axis="y")
        c.set(x, 8, 7, CPB)
    c.stair(3, 7, 7, "west", "top", BEND)
    c.stair(4, 7, 7, "east", "top", BEND)
    c.stair(3, 8, 7, "east", name=BEND)
    c.stair(4, 8, 7, "west", name=BEND)
    c.stair(2, 9, 7, "north", name=PBS)                       # the gargoyle
    c.skull(2, 10, 7, TO_CAMERA)
    c.set(5, 9, 7, "soul_campfire", **SOUL_FIRE)
    for x in (3, 4):
        c.set(x, 6, 7, "chain", **CHAIN_Y)                    # chains hung in the arch
    c.set(2, 5, 6, PBB)                                       # the west buttress rises to carry the arch's thrust
    c.set(2, 6, 6, PBB)
    c.stair(2, 7, 6, "south", name=PBBS)


def _l3(c):
    """THE BONE SPIRE."""
    _tower_l3(c)
    _chapel_l3(c)
    _gatehouse_l3(c)


def l3():
    c = l2().next()
    _l3(c)
    return settle(c)


# ============================================================================================================== main
BUILD_TIME_BLOCKS = 99         # RoN's graveyard.nbt's non-air count: Graveyard.buildTimeBlocks keeps its build time


def build_all():
    """Every level, checked: each level's rules (check_level), the add / replace chain plain -> overflowing -> L2 -> L3
    (and plain -> L2: an un-researched graveyard can level up), the footprint. Returns the Structures and counts."""
    a, o, b2, b3 = l1(), l1_overflowing(), l2(), l3()
    counts = {}
    for lv, tag, over in ((a, "graveyard", False), (o, "overflowing_graveyard", True), (b2, "graveyard_l2", True),
                          (b3, "graveyard_l3", True)):
        counts[tag] = check_level(lv.b, tag, over)
    check_chain(a.b, o.b, "L1 -> overflowing")
    check_chain(o.b, b2.b, "overflowing -> L2")
    check_chain(a.b, b2.b, "L1 -> L2")
    check_chain(b2.b, b3.b, "L2 -> L3")
    h1 = max(a.height(), o.height())
    # the plain L1 and the Overflowing Graveyard share a box (the research swaps one for the other in place)
    structs = {"graveyard": structure(a, h1), "overflowing_graveyard": structure(o, h1),
               "graveyard_l2": structure(b2), "graveyard_l3": structure(b3)}
    for s in structs.values():
        assert s.size[0] == SX and s.size[2] == SZ, "the footprint changed"
    return structs, counts


def bases():
    """The two L1 structures (graveyard, overflowing_graveyard) for tools/gen_lab_levels.py to write."""
    s, _ = build_all()
    return s["graveyard"], s["overflowing_graveyard"]


def levels():
    """(L2, L3) for tools/gen_lab_levels.py LABS["graveyard"]."""
    s, _ = build_all()
    return s["graveyard_l2"], s["graveyard_l3"]


def audit_all(out):
    import audit_structure_pockets as asp
    res = {}
    for name in ("graveyard", "overflowing_graveyard", "graveyard_l2", "graveyard_l3"):
        one_way, ground, roof, rooms, note = asp.audit(name, os.path.join(out, name + ".nbt"))
        res[name] = (one_way, ground, roof, rooms, note)
    return res


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/lab_graveyard"
    os.makedirs(out, exist_ok=True)
    structs, counts = build_all()
    for name, s in structs.items():
        s.write(os.path.join(out, name + ".nbt"))
        print("%-24s %dx%dx%d  %d blocks" % (name, s.size[0], s.size[1], s.size[2], counts[name]))
    bad = False
    for name, (one_way, ground, roof, rooms, note) in audit_all(out).items():
        print("%-24s pockets: one-way %d, ground %d, roof-only %d%s" % (name, len(one_way), len(ground), len(roof), note))
        bad |= bool(one_way or ground or "TRAPPED" in note)
    assert not bad, "pockets"
