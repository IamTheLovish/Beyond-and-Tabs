#!/usr/bin/env python3
"""The Verdant Court T1 army lab, the GROVE, redrawn at all three levels - L1 THE STAG GLADE, L2 THE ROOTED COURT,
L3 THE ELDER GROVE - as one family with the World-Tree Court (the capitol, tools/capitol_heartwood_hall.py) and the
Root Spring (the extractor, tools/mex_verdant.py).

lovish, Oct 19 2026: buildings must "look like they match each other so when bases get bigger they look cohesive and
beautiful"; "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out for the
opponent; ... much bigger or have cooler, more intricate designs". The old Grove (tools/gen_structures.py grove(), and
its L2 / L3 in gen_lab_levels.verdant_grove) was a ring of lollipop oaks under one flat azalea lid: from the RTS camera
a green box with no face, no ruin, no stag - nothing of the Court's family - and its gate faced away from the camera.

THE VERDANT STYLE KIT (the capitol's and the extractor's, the same on every Verdant building):
  - main blocks: MOSSY STONE (mossy / cracked / chiseled stone bricks, their stairs and walls, moss on the tops) and
    LIVING WOOD (oak logs and bark for the tree, stripped birch - the Court's silver - for the mask and the antlers);
  - accent: LEAVES (azalea, flowering azalea, cherry blossom at the crown), persistent with their true distance;
  - light: FROGLIGHTS and SHROOMLIGHT as eyes and glowing fruit set in the foliage, lanterns standing on mossy walls;
  - silhouette motif: ROOTS AND CANOPIES - roots clawing and arching over the ground, a canopy in layered tiers;
  - signature detail: the STAG-DRYAD - a pale birch mask, verdant froglight eyes under dark-oak brows, a moss crown and
    a glowing bud, and birch antlers that are the tree's own branches (the Root Spring's spirit, the World-Tree's face);
  - edge: moss, grass, rooted dirt and podzol with ferns and flowers on the open sides.

THE PLAN (9 x 8, the shipped footprint: x 0..8 west -> east, z 0..7 north -> south). The RTS camera looks from the
south-west: it sees the tops, the west face (x = 0) and the south face (z = 7). So the masses stand along the NORTH
(z 0..1) and face south over the GLADE (z 2..7) toward the camera, and the glade is open to the west and the south -
the way units leave (grove_frees_its_units sends them to the far -x / +z corner).
  L1 THE STAG GLADE - the Mother Tree stands between two ruined piers of an old shrine (2 x 2, chiseled feet, mossy
     and cracked courses, a cornice and a stepped buttress on the glade side, a colonnette lancet in the west face, moss
     on top, a lantern on a wall post; the arch they carried is gone but for springer stones the tree swallowed). Its
     trunk is the STAG-DRYAD's head looking south down the glade: an oak skull with heavy jowls, the birch mask, the
     eyes under dark-oak brows that jut out over the glade with the snout and the blaze, leafy ears, the moss crown and
     its glowing bud. The antlers branch out of the burrs over the piers in front of a LEAF CLOAK on the boughs behind
     (the Root Spring's T2 trick: the pale antlers and mask read against green, and the Grove reads as a tree, not a
     totem). Buttress roots claw along the north band. In the glade: a froglight heart where the trained units step
     out, a calcite sparring ring (the Court's silver) round it, a moonwell (a water cauldron between bare calcite
     kerbs) on the west for the Moonwell Bearers, the Thornbows' butts (hay and targets) on the east, ferns and flowers.
  L2 THE ROOTED COURT - the piers rise into towers (pilasters, the lancet carried up, a jutting cornice, moss caps): the
     OWLS' PERCH on the west (a silver birch post with a fork and a lamp) and the HIVE on the east (a honey-glowing
     heart under a flowering crown); great ROOTS arch out of the towers over the moonwell and the butts and plant their
     feet in the fringe (headroom 3 under them); the leader climbs and throws out a course of bare boughs carrying the
     first CANOPY TIER, wide and flat over the towers, notched in front of the stag, with glowing fruit in its
     under-edge; the antlers grow a second knuckle and pierce the tier; lantern posts on the glade's front corners.
  L3 THE ELDER GROVE - the Mother Tree becomes the elder: the leader thickens into a 3-wide trunk, a second course of
     boughs carries an UPPER TIER and a CHERRY-BLOSSOM CROWN with a shroomlight heart, bare trunk between the tiers so
     they read as layers (the World-Tree's canopy at the lab's scale); the middle tier spreads out over the glade's
     sides; the antlers climb to a third knuckle and a crown of tines standing outside the upper tiers - the landmark an
     opponent spots; the shrine wakes (the west tower's lancets glow green), the roots grow second roots and flower,
     moss creeps over the flagstones and pink petals fall on the fringe.

GAME RULES (asserted in check_levels; the coordinator wires the levels, this file edits nothing):
  - every level keeps the 9 x 8 footprint (BuildingLevels has no larger footprint); a level grows taller (14, 18, 24);
  - the chain: every non-air cell of L1 is non-air in L2, and of L2 in L3 (changeStructure only adds or replaces):
    L2 is drawn over L1 and L3 over L2, and that is asserted;
  - Grove.startingBlockTypes = moss_block, mossy_stone_bricks: both in layer 0 (only layer 0 is the foundation);
  - the unit spawn: ProductionBuilding.getIndoorSpawnPoint walks down from centrePos, the L1 box's centre, truncated in
    WORLD coordinates: x is odd (9) so x 4 exactly, z is even (8) so z 3 or 4 depending on the sign of the coordinate
    and the rotation (rotation maps {3, 4} onto itself); changeStructure never moves it. So the columns (4, 3) and
    (4, 4) stay open to the sky from the floor at EVERY level (the canopy tiers are notched there, in front of the
    stag's face), the walk down from L1's mid-height lands on the glade floor, and a 3 x 4 x 3 box round them (x 3..5,
    z 2..5, y 1..3) is clear so a Sentinel Treant (1.54 wide, 2.97 tall) fits where it is trained;
  - ways out: the glade is open to the west and the south at the floor (only flowers, ferns and petals, which a unit
    walks through); no candles, carpets or slabs anywhere (the Court's lesson: a unit stood on a candle on a kerb and
    could not step off); every standable floor cell walks out, so a unit the walk-out test builds into a trunk or a
    pier is rescued (UnitUnstick: the nearest standable cell that is not a pocket) onto a cell that leaves;
  - nothing pops off: lanterns stand on walls / posts / full tops, nothing hangs (the build runs bottom-up), flowers,
    ferns and petals stand on soil, leaves are persistent with their true distance (mex_verdant._leaf_distances), no
    vines; nothing floats (every block above the ground touches another);
  - no water (the moonwell is a cauldron), lava, gravity blocks, redstone or fire; no pockets
    (tools/audit_structure_pockets.py audit() on all three levels) and both spawn cells walk out of every level;
  - every block is decided for VERDANT in BiomePalette (flowers are BushBlocks, the cauldron an AbstractCauldronBlock).

WHAT THE GAME NEEDS (the coordinator applies these):
  - tools/gen_structures.py: grove() returns lab_grove.base() (as slipway() returns lab_slipway's and heartwood_hall()
    capitol_heartwood_hall's), so its __main__ keeps writing grove.nbt into both trees;
  - tools/gen_lab_levels.py: LABS["grove"] = lab_grove.levels (gen_lab_levels.verdant_grove becomes unused);
  - Grove.java: this.buildTimeBlocks = 287 (the shipped grove.nbt's non-air count) - the new L1 has more blocks.

Usage: python3 tools/lab_grove.py [OUT_DIR]   (writes grove.nbt, grove_l2.nbt, grove_l3.nbt and checks them; default
       OUT_DIR: the scratch folder - shipping them is the coordinator's step)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402
import mex_verdant as mv      # noqa: E402  - the leaf-distance pass shared with the Root Spring and the capitol

SX, SZ = 9, 8
CENTRE = [(4, 3), (4, 4)]                 # the cells the spawn can start in (see the docstring)
CLEAR = [(x, y, z) for x in (3, 4, 5) for z in (2, 3, 4, 5) for y in (1, 2, 3)]   # a Sentinel Treant's room
START_TYPES = {"moss_block", "mossy_stone_bricks"}  # Grove.startingBlockTypes
BUILD_TIME_BLOCKS = 287                   # the shipped grove.nbt's non-air count (Grove.buildTimeBlocks keeps it)

DRY = dict(waterlogged="false")
# the kit's blocks, by role
MSB, CSB, CHB, MC = "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "mossy_cobblestone"
MSS, MSW = "mossy_stone_brick_stairs", "mossy_stone_brick_wall"
LOG, WOOD, OST = "oak_log", "oak_wood", "oak_stairs"
BIRCH, BIRCH_W, BIRCH_S, BIRCH_F = "stripped_birch_log", "stripped_birch_wood", "birch_stairs", "birch_fence"
DOS = "dark_oak_stairs"
LEAF, FLEAF, CHERRY = "azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"
GLOW, FRUIT, SHROOM = "verdant_froglight", "ochre_froglight", "shroomlight"
SOIL = ("moss_block", "grass_block", "rooted_dirt", "podzol")
FLOWERS = ("fern", "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "cornflower", "allium", "fern")


def hsh(x, z):
    """A fixed scatter (the capitol's), so the moss and flowers fall the same way on every run."""
    return ((x + 31) * 73856093 ^ (z + 17) * 19349663) % 101


class Rig(gs._SunRig):
    """gen_structures._SunRig (settle() shapes stairs and joins walls / fences like the game does) with the Court's
    helpers and the support check this lab needs."""

    def __init__(self, sy):
        super().__init__(SX, sy, SZ)

    def st(self, x, y, z, facing, half="bottom", name=MSS):
        self.stair(x, y, z, facing, half, name)

    def log(self, x, y, z, axis="y", name=LOG):
        self.set(x, y, z, name, axis=axis)

    def lantern(self, x, y, z):
        self.set(x, y, z, "lantern", hanging="false", waterlogged="false")

    def leaf(self, x, y, z, name=LEAF):
        """A leaf (its distance is fixed once all the wood stands); never overwrites, clipped to the box."""
        if 0 <= x < SX and 0 <= z < SZ and 0 <= y < self.size[1] and (x, y, z) not in self.b:
            self.set(x, y, z, name, persistent="true", distance="7", waterlogged="false")

    def free(self, x, y, z):
        return (x, y, z) not in self.b

    def check_support(self, tier):
        bad = []
        for pos, (n, p) in self.b.items():
            below = self.nb(pos, "down")
            if n in ("lantern", "soul_lantern"):
                ok = p.get("hanging") != "true" and gs._sun_centre_up(below)
            elif n.endswith("candle") or n.endswith("_carpet") or n.endswith("_banner") or n == "bell":
                ok = False         # none on this lab (no candles or carpets where units step: the Court's lesson)
            elif n in FLOWERS or n == "pink_petals":
                ok = below[0] in SOIL
            elif n == "flowering_azalea" or n == "azalea":
                ok = below[0] in SOIL + ("clay", "mud")
            elif "wall_" in n or n.endswith(("_button", "ladder", "torch")) or n == "vine":
                ok = False
            else:
                continue
            if not ok:
                bad.append((pos, n, below[0]))
        assert not bad, "grove %s: would pop off / hang: %s" % (tier, sorted(bad))


# ======================================================================================================== L1
def ground_l1(r):
    """y0: the shrine's floor on the north band, the glade's flagstones with a calcite ring round a froglight heart (the
    spawn cells: where the trained units step out), and a garden fringe on the open sides - moss, grass, rooted dirt and
    podzol under ferns and flowers. The foundation types (moss, mossy bricks) are in this layer."""
    for x in range(SX):
        for z in range(SZ):
            h = hsh(x, z)
            fringe = x in (0, SX - 1) and z >= 2 or z == SZ - 1
            if z <= 1:
                name = MSB if h % 4 else CSB
            elif fringe:
                name = ("moss_block", "grass_block", "moss_block", "rooted_dirt", "podzol", "grass_block", MC)[h % 7]
            else:
                d = ((x - 4) ** 2 + (z - 3.5) ** 2) ** 0.5
                if (x, z) in CENTRE:
                    name = GLOW
                elif 1.4 <= d <= 2.2:
                    name = "calcite" if h % 3 else MSB      # the sparring ring: silver stones, a few lost to moss
                else:
                    name = MSB if h % 3 else "moss_block"
            props = {"snowy": "false"} if name in ("grass_block", "podzol") else {}
            if name == GLOW:
                props = {"axis": "y"}
            r.set(x, 0, z, name, **props)
    r.set(2, 0, 6, "moss_block")              # the foundation types stay in layer 0 whatever the scatter does
    r.set(6, 0, 6, MSB)


def plants_l1(r):
    """Ferns and flowers on the fringe soil (a unit walks through them), never on stone, never on a way-out step."""
    for x in range(SX):
        for z in range(2, SZ):
            if r.get(x, 0, z)[0] in SOIL and r.free(x, 1, z):
                k = hsh(x * 3 + 1, z + 5) % 9
                if k < len(FLOWERS):
                    r.set(x, 1, z, FLOWERS[k])


PIER_TOP = 6                     # the corner piers (x 0..1 and 7..8, z 0..1): y 1..5, a cornice at 6
HEAD = 4                         # the stag's jaw course; the head runs HEAD..HEAD+4, the burrs at HEAD+4
PIERS = ((0, 1), (7, 8))         # the piers' x pairs


def pier_l1(r, x0, x1, top=PIER_TOP):
    """A corner pier of the old shrine (2 x 2 on z 0..1): a chiseled foot, mossy and cracked courses, a cornice of
    upside-down stairs jutting out on the glade side, a wall post with a lantern on its front corner."""
    for x in (x0, x1):
        for z in (0, 1):
            r.set(x, 1, z, CHB)
            for y in range(2, top):
                r.set(x, y, z, MSB if hsh(x * 5 + y, z + y) % 3 else CSB)
            r.set(x, top, z, "moss_block")               # the moss has the pier's top (green from the camera)
    for x in (x0, x1):
        r.st(x, top, 2, "north", "top")                  # the cornice over the glade
    out = x0 if x0 == 0 else x1
    # a stepped buttress on the outer column's glade side (the pier's third depth): a chiseled block, a brick, a
    # set-off stair sloping back into the pier
    r.set(out, 1, 2, CHB)
    r.set(out, 2, 2, MSB)
    r.st(out, 3, 2, "north")
    # a lancet on the outer face (x 0 is the camera's west face): a thin colonnette in a slot of the wall
    for y in (3, 4):
        r.set(out, y, 0, MSW)
    r.st(out, 5, 0, "south", "top")
    r.set(out, top + 1, 1, MSW)                          # the lantern post on the front corner
    r.lantern(out, top + 2, 1)


def arch_l1(r):
    """The ruined shrine: two corner piers, and of the pointed arch they once carried across the north edge (z 0) only
    springer stones the tree has swallowed - the west one and a voussoir against the jowl, the east one snapped short,
    leaves spilling off the broken ends."""
    pier_l1(r, *PIERS[0])
    pier_l1(r, *PIERS[1])
    r.st(2, PIER_TOP, 0, "west", "top")                  # the west springer
    r.set(2, PIER_TOP + 1, 0, CSB)
    r.st(6, PIER_TOP, 0, "east", "top")                  # the east springer, all that stands of that half
    # moss and leaves spilling off the broken ends
    r.leaf(2, PIER_TOP + 2, 0, LEAF)
    r.leaf(6, PIER_TOP + 1, 0, FLEAF)


def tree_l1(r):
    """The Mother Tree: a 3-wide trunk (x 3..5, z 0..1) from the roots to the head, buttress roots clawing out along the
    north band (bark stairs rising toward the trunk, mangrove roots between) - never into the glade's spawn room (x 3..5
    stays clear at z 2) - and the leader rising behind the head into a small crown."""
    for y in range(1, HEAD):
        for x in (3, 4, 5):
            for z in (0, 1):
                r.log(x, y, z)
    for z in (0, 1):
        r.st(2, 1, z, "east", name=OST)
        r.st(6, 1, z, "west", name=OST)
    r.log(2, 2, 0, name=WOOD)                            # the back buttresses climb higher than the front
    r.log(6, 2, 0, name=WOOD)
    r.st(2, 3, 0, "east", name=OST)
    r.st(6, 3, 0, "west", name=OST)
    r.st(2, 1, 2, "east", name=OST)                      # root toes creeping into the glade beside the spawn room
    r.st(6, 1, 2, "west", name=OST)
    for y in range(HEAD + 5, HEAD + 9):                  # the leader, behind the moss crown
        r.log(4, y, 0)
    r.log(3, HEAD + 7, 0, axis="x")                      # boughs into the cloak
    r.log(5, HEAD + 7, 0, axis="x")


def head_l1(r, y0=HEAD):
    """The STAG-DRYAD's head on the trunk, looking south down the glade: an oak skull (z 0) with heavy jowls, a pale
    birch mask (z 1), the snout and the dark-oak brows jutting out over the glade (z 2), verdant froglight eyes set in
    the mask under them, leafy ears, a moss crown between the burrs and a glowing bud - the Root Spring's spirit at the
    lab's scale."""
    for y in range(y0, y0 + 5):
        for x in (3, 4, 5):
            r.log(x, y, 0, name=WOOD)
    for y in (y0 + 1, y0 + 2, y0 + 3):                  # the jowls: the skull is wider than the mask
        r.log(2, y, 0, name=WOOD)
        r.log(6, y, 0, name=WOOD)
    r.st(2, y0, 0, "east", "top", name=OST)              # rounded under the jowls
    r.st(6, y0, 0, "west", "top", name=OST)
    # the mask, bottom to top
    r.st(3, y0, 1, "east", "top", name=OST)              # the jaw line, hollowed under
    r.st(5, y0, 1, "west", "top", name=OST)
    r.log(4, y0, 1, name=BIRCH_W)                        # the muzzle
    r.st(3, y0 + 1, 1, "east", "top", name=BIRCH_S)      # the cheeks
    r.st(5, y0 + 1, 1, "west", "top", name=BIRCH_S)
    r.log(4, y0 + 1, 1, name=BIRCH_W)
    r.st(4, y0 + 1, 2, "north", name=BIRCH_S)            # the snout over the glade, sloping down to the nose
    r.set(3, y0 + 2, 1, GLOW, axis="y")                  # the eyes
    r.set(5, y0 + 2, 1, GLOW, axis="y")
    r.log(4, y0 + 2, 1, name=BIRCH_W)                    # the bridge of the nose
    r.log(4, y0 + 2, 2, name=BIRCH_W)                    # the blaze standing proud between the eyes
    r.st(3, y0 + 3, 2, "north", "top", name=DOS)         # the brows, jutting out over the eyes
    r.st(5, y0 + 3, 2, "north", "top", name=DOS)
    r.log(3, y0 + 3, 1, name=WOOD)
    r.log(5, y0 + 3, 1, name=WOOD)
    r.log(4, y0 + 3, 1, name=BIRCH_W)                    # the forehead
    r.log(3, y0 + 4, 1, name=BIRCH)                      # the burrs
    r.log(5, y0 + 4, 1, name=BIRCH)
    r.set(4, y0 + 4, 1, "moss_block")                    # the moss crown
    r.set(4, y0 + 5, 1, GLOW, axis="y")                  # the glowing bud: the dryad's jewel
    r.leaf(2, y0 + 3, 1, FLEAF)                          # leafy ears flicked out behind the brows
    r.leaf(6, y0 + 3, 1, LEAF)


ANTLER = (  # one antler from the burr (bx), as (dx outward, dy over the burr, dz, part): the beam leaves the burr,
            # sweeps out and up to a knuckle over the pier, tines fork up, out and toward the camera
    (0, 1, 0, "F"), (1, 1, 0, "Sb"), (1, 2, 0, "F"), (2, 2, 0, "F"), (2, 3, 0, "K"), (2, 4, 0, "F"), (2, 3, 1, "F"),
    (3, 3, 0, "F"), (3, 4, 0, "F"),
)


def antler(r, bx, s, y0, parts=ANTLER, z0=1):
    """One antler (s = -1 west, +1 east) from the burr at (bx, y0, z0): birch fence for the beam and tines, birch stairs
    where the beam bends, stripped birch at the knuckles - an open, branching pale line against the leaf cloak."""
    for (dx, dy, dz, k) in parts:
        x, y, z = bx + s * dx, y0 + dy, z0 + dz
        if not (0 <= x < SX):
            continue
        if k == "F":
            r.set(x, y, z, BIRCH_F)
        elif k == "K":
            r.log(x, y, z, name=BIRCH)
        elif k == "Sb":
            r.st(x, y, z, "east" if s < 0 else "west", "top", name=BIRCH_S)


def fruit(r, cells, name=FRUIT):
    """Glowing fruit (the kit's light): set only where a leaf is and a leaf covers it, so it glows out of the foliage's
    side and under-edge - a bare top face reads as a light bulb stuck on the canopy."""
    for (x, y, z) in cells:
        if r.get(x, y, z)[0].endswith("_leaves") and r.get(x, y + 1, z)[0].endswith("_leaves"):
            r.set(x, y, z, name, **({} if name == SHROOM else {"axis": "y"}))


def cloak_l1(r):
    """The leaf cloak the stag wears behind its antlers (the Root Spring's T2 trick): azalea on the boughs at the back
    (z 0) and round the leader, a hood over the crown, flowering here and there and one glowing fruit - the pale
    antlers and the mask read against it, and from the RTS camera the Grove is a tree, not a totem."""
    for y in range(HEAD + 5, HEAD + 10):
        for x in range(0, SX):
            for z in (0, 1):
                d = ((x - 4) / 4.3) ** 2 + ((y - (HEAD + 7)) / 2.6) ** 2 + (z * 0.45 if y < HEAD + 8 else 0)
                if d <= 1.0:
                    r.leaf(x, y, z, gs.leaf_for(x, y, z, 4))
    r.leaf(4, HEAD + 9, 2, FLEAF)                        # the hood's lip over the moss crown
    r.leaf(3, HEAD + 9, 2, LEAF)
    r.leaf(5, HEAD + 9, 2, LEAF)
    fruit(r, ((1, HEAD + 6, 1), (7, HEAD + 7, 1)))


def antlers_l1(r):
    for s in (-1, 1):
        antler(r, 4 + s, s, HEAD + 4)


def glade_l1(r):
    """The glade's furniture: the moonwell on the west (a still basin - a water cauldron, no flowing water - between
    calcite kerbs: no candles on them) and the Thornbows' butts on the east (hay bales and targets on a birch post)."""
    r.set(1, 1, 3, "water_cauldron", level="3")
    r.set(1, 1, 2, "calcite")
    r.set(2, 1, 3, "calcite")
    for z in (3, 4):
        r.set(SX - 1, 1, z, "hay_block", axis="y")
    r.set(SX - 1, 2, 4, "target", power="0")
    r.log(SX - 1, 2, 3, name=BIRCH)
    r.set(SX - 1, 3, 3, "target", power="0")


def draw_l1(r):
    ground_l1(r)
    arch_l1(r)
    tree_l1(r)
    head_l1(r)
    antlers_l1(r)
    cloak_l1(r)
    glade_l1(r)
    for p in CLEAR:                           # the spawn room stays clear whatever a step above drew
        r.b.pop(p, None)
    plants_l1(r)


# ======================================================================================================== L2
TOWER_TOP = 10                   # L2: the piers rise into towers to here (a cornice on top), their lanterns over it


def tower_l2(r, x0, x1, owl):
    """A corner pier rises into a tower (2 x 2): the L1 cap and lantern post are walled in, more courses with the lancet
    carried up the outer face, a jutting cornice of upside-down stairs, a mossy cap - and on top the OWL'S PERCH (west:
    a stripped-birch owl with froglight eyes and dark-oak ear tufts on a birch post, beside a lantern) or the HIVE
    (east: a flowering crown round a honey-glowing heart)."""
    for x in (x0, x1):
        for z in (0, 1):
            for y in range(PIER_TOP + 1, TOWER_TOP):
                r.set(x, y, z, MSB if hsh(x + y * 3, z * 7 + y) % 4 else CSB)
            r.set(x, TOWER_TOP, z, "moss_block")
    out = x0 if x0 == 0 else x1
    for y in (PIER_TOP + 1, PIER_TOP + 2):
        r.set(out, y, 0, MSW)                            # the lancet carried up the outer face
    r.st(out, PIER_TOP + 3, 0, "south", "top")
    for x in (x0, x1):                                   # the cornice, lip out over the glade
        r.st(x, TOWER_TOP, 2, "north", "top")
    for y in range(PIER_TOP + 1, TOWER_TOP):             # a pilaster on the outer column's glade face (the third
        r.set(out, y, 2, MSB if y % 3 else CHB)          # depth: pilaster, wall, lancet slot)
    if owl:
        r.set(x1, TOWER_TOP + 1, 1, MSW)
        r.lantern(x1, TOWER_TOP + 2, 1)
        r.log(x0, TOWER_TOP + 1, 0, name=BIRCH)          # the owls' perch: a silver post with a fork and a lamp
        r.log(x0, TOWER_TOP + 2, 0, name=BIRCH)
        r.set(x0, TOWER_TOP + 3, 0, BIRCH_F)
        r.set(x1, TOWER_TOP + 2, 0, BIRCH_F)
        r.lantern(x0, TOWER_TOP + 4, 0)
        r.leaf(x1, TOWER_TOP + 1, 0, LEAF)
        r.leaf(x0, TOWER_TOP + 1, 1, FLEAF)
    else:
        r.set(x0, TOWER_TOP + 1, 1, MSW)
        r.lantern(x0, TOWER_TOP + 2, 1)
        r.set(x1, TOWER_TOP + 1, 0, FRUIT, axis="y")     # the hive's honey glow under a flowering crown
        for (x, y, z) in ((x1, TOWER_TOP + 2, 0), (x0, TOWER_TOP + 1, 0), (x1, TOWER_TOP + 1, 1),
                          (x1, TOWER_TOP + 3, 0), (x0, TOWER_TOP + 2, 0)):
            r.leaf(x, y, z, FLEAF if (x + y) % 2 else LEAF)


ANTLER_L2 = (  # the antlers grow: the top tines rise to a second knuckle, through the new tier, forking out and
               # toward the camera, the tips standing over the leaves
    (2, 5, 0, "F"), (2, 6, 0, "K"), (2, 7, 0, "F"), (2, 8, 0, "F"), (2, 9, 0, "F"), (3, 6, 0, "F"), (3, 7, 0, "F"),
    (2, 6, 1, "F"),
)


def canopy_l2(r):
    """The first tier of a real canopy, lifted clear of the cloak: the leader climbs and throws out a course of bare
    boughs (y14: brown spokes seen under the tier), and on them a wide, flat tier (y15-16, over the towers, out to z 3)
    with a smaller cap (y17) - a layer with air under its edge, not a taller bush. The antlers pierce it."""
    for y in range(HEAD + 9, 17):
        r.log(4, y, 0)
    for (x, z, ax) in ((3, 0, "x"), (2, 0, "x"), (1, 0, "x"), (5, 0, "x"), (6, 0, "x"), (7, 0, "x"),
                       (4, 1, "z"), (4, 2, "z")):
        r.log(x, 14, z, axis=ax)
    for y, rad in ((15, 4.9), (16, 3.9)):
        for x in range(SX):
            for z in range(0, 4):
                if (x, z) in CENTRE or (z >= 3 and abs(x - 4) <= 1):
                    continue                             # the notch in front of the stag: it looks out under it
                d = (((x - 4) ** 2) + ((z - 0.5) * 1.5) ** 2) ** 0.5
                if d <= rad - (0.7 if hsh(x * 3 + y, z) % 4 == 0 else 0):
                    r.leaf(x, y, z, gs.leaf_for(x, y, z, 3))
    fruit(r, ((2, 15, 2), (6, 15, 3), (1, 15, 1)))
    for (x, z) in ((4, 0), (3, 0), (5, 0), (4, 1)):     # a leafy cap over the leader (its cut top read as a stump)
        r.leaf(x, 17, z, gs.leaf_for(x, 17, z, 3))


def roots_l2(r):
    """Roots arch over the glade's sides: on the west over the moonwell, on the east over the butts - each springs from
    the tower's front, arcs over (headroom 3 under it, so a Sentinel Treant walks under) and plants a foot of bark in
    the fringe with a mangrove root beside it."""
    for (x, s) in ((1, -1), (7, 1)):
        r.log(x, 4, 2, name=WOOD)
        r.log(x, 5, 3, name=WOOD)
        r.st(x, 5, 2, "south", name=OST)
        r.log(x, 4, 4, name=WOOD)
        r.st(x, 5, 4, "north", name=OST)
        r.log(x, 3, 5, name=WOOD)
        r.st(x, 4, 5, "north", name=OST)
        r.log(x, 2, 5, name=WOOD)
        r.log(x, 1, 5, name=WOOD)
        r.set(x + s, 1, 5, "mangrove_roots", **DRY) if 0 <= x + s < SX else None
        r.set(x, 1, 6, "mangrove_roots", **DRY)


def glade_l2(r):
    """Lantern posts on the glade's front corners."""
    for x in (0, SX - 1):
        r.set(x, 1, SZ - 1, MSW)
        r.lantern(x, 2, SZ - 1)


def draw_l2(r):
    draw_l1(r)
    tower_l2(r, *PIERS[0], owl=True)
    tower_l2(r, *PIERS[1], owl=False)
    for s in (-1, 1):
        antler(r, 4 + s, s, HEAD + 4, ANTLER_L2)
    canopy_l2(r)
    roots_l2(r)
    glade_l2(r)


# ======================================================================================================== L3
ANTLER_L3 = (  # the elder's antlers: from the second knuckle up through the middle tier to a third knuckle, then a
               # crown of tines standing outside the upper tiers (x 0 / 8) and over them (x 1 / 7) - the landmark
    (2, 10, 0, "K"), (3, 10, 0, "F"), (3, 11, 0, "K"), (3, 12, 0, "F"), (3, 13, 0, "F"), (3, 11, 1, "F"),
    (2, 11, 0, "F"), (2, 12, 0, "F"), (2, 12, 1, "F"),
)


def elder_l3(r):
    """The Mother Tree becomes the elder: the leader thickens into a 3-wide trunk (bark, x 3..5 z 0..1) from the cloak
    to the crown, and a second course of boughs (y18) carries the upper tiers; the leader alone
    climbs on to the crown, bare between the tiers so each reads as a layer."""
    for y in range(HEAD + 9, 22):
        for x in (3, 4, 5):
            for z in (0, 1):
                if y >= 19 and (x != 4 or z == 1 and y >= 20):
                    continue
                if (x, y, z) in r.b and not r.b[(x, y, z)][0].endswith("_leaves") and y < 15:
                    continue
                r.log(x, y, z, name=LOG if x == 4 else WOOD)
    for (x, z, ax) in ((2, 0, "x"), (6, 0, "x"), (2, 1, "x"), (6, 1, "x"), (4, 2, "z")):
        r.log(x, 18, z, axis=ax)


def canopy_l3(r):
    """The World-Tree's layered canopy on the Grove (capitol_heartwood_hall.canopy at the lab's scale): the L2 tier
    becomes the wide middle, an upper tier (y19-20) and a cherry-blossom crown (y21-22) with a shroomlight heart, a gap
    of bare boughs between each - notched over the glade in front of the stag, so its face still looks out and the
    spawn's columns stay open to the sky."""
    for y, rad, z1 in ((19, 3.7, 2), (20, 2.8, 2), (22, 2.1, 1), (23, 1.1, 1)):
        for x in range(SX):
            for z in range(0, z1 + 1):
                if (x, z) in CENTRE or (z >= 3 and abs(x - 4) <= 1):
                    continue
                d = (((x - 4) ** 2) + ((z - 0.7) * 1.5) ** 2) ** 0.5
                if d > rad - (0.6 if hsh(x * 5 + y, z + y) % 5 == 0 else 0):
                    continue
                if y >= 22:
                    name = CHERRY if hsh(x + y, z) % 3 else FLEAF
                else:
                    name = FLEAF if hsh(x * 7, z + y) % 3 == 0 else (CHERRY if hsh(x, z * 5 + y) % 7 == 0 else LEAF)
                r.leaf(x, y, z, name)
    r.set(4, 22, 1, SHROOM)                             # the crown's heart, glowing through the blossom
    # the middle tier spreads wider still at the elder: its edge reaches the glade's sides
    for x in range(SX):
        for z in range(0, 5):
            if (x, z) in CENTRE or (z >= 3 and abs(x - 4) <= 1):
                continue
            d = (((x - 4) ** 2) + ((z - 0.5) * 1.3) ** 2) ** 0.5
            if 4.0 <= d <= 5.6 - (0.6 if hsh(x * 3, z) % 4 == 0 else 0):
                r.leaf(x, 15, z, gs.leaf_for(x, 15, z, 3))
    fruit(r, ((2, 19, 2), (6, 19, 1)), GLOW)
    fruit(r, ((3, 22, 1),), SHROOM)


def roots_l3(r):
    """The elder's ground: the root arches grow a second root beside their feet (bark on the glade side, gripping the
    fringe) and flower on their crowns, the moss creeps over the glade's flagstones (the froglight heart and the calcite
    ring stay), pink petals fall on the fringe, and the shrine wakes - the west tower's lancets glow green."""
    for (x, s) in ((2, -1), (6, 1)):
        r.st(x, 1, 5, "east" if s < 0 else "west", name=OST)
        r.set(x, 1, 6, "mangrove_roots", **DRY)
    r.leaf(1, 6, 3, FLEAF)
    r.leaf(7, 6, 3, LEAF)
    for x in range(1, SX - 1):
        for z in range(2, SZ - 1):
            if r.get(x, 0, z)[0] == MSB and hsh(x + 7, z * 3) % 3 == 0:
                r.set(x, 0, z, "moss_block")
    for x in range(SX):
        for z in range(2, SZ):
            if r.get(x, 0, z)[0] in SOIL and r.free(x, 1, z) and hsh(x * 5, z + 2) % 3 == 0:
                r.set(x, 1, z, "pink_petals", facing="north", flower_amount=str(1 + hsh(x, z) % 4))
    for y in (4, PIER_TOP + 2):
        r.set(0, y, 0, GLOW, axis="y")                   # the lancets' upper lights (the slot's colonnette below)


def draw_l3(r):
    draw_l2(r)
    elder_l3(r)
    for s in (-1, 1):
        antler(r, 4 + s, s, HEAD + 4, ANTLER_L3)
    canopy_l3(r)
    roots_l3(r)


# ======================================================================================================== build
YMAX = 24                       # drawing room; each level's box is cropped to its top block


def build(draw, tier):
    r = Rig(YMAX)
    draw(r)
    mv._leaf_distances(r)
    r.settle()
    r.check_support(tier)
    r.size = (SX, max(y for (_, y, _) in r.b) + 1, SZ)
    return r.structure()


def l1():
    return build(draw_l1, "L1")


def l2():
    return build(draw_l2, "L2")


def l3():
    return build(draw_l3, "L3")


def base():
    """The L1 the game ships as grove.nbt (gen_lab_levels writes it, like lab_barracks.base())."""
    return l1()


def levels():
    """(L2, L3) for gen_lab_levels.LABS["grove"], like lab_barracks.levels()."""
    return l2(), l3()


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "_door", "_carpet", "candle", "_slab", "vine",
             "hanging_roots", "spore_blossom", "bell", "_banner", "torch", "sea_pickle")
HELD = ("_fence", "_wall", "lantern", "_stairs")       # thin / part blocks that must touch the building somewhere


def floating(s):
    """Cells of a level that touch nothing (no face shared with another non-air block): a floating tine or leaf reads as
    debris from the camera and, for a fence or lantern, would not stand."""
    n = names(s)
    lone = []
    for (x, y, z), nm in n.items():
        if y == 0:
            continue
        if not any((x + dx, y + dy, z + dz) in n for dx, dy, dz in
                   ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            lone.append(((x, y, z), nm))
    return lone


def walk_out(pad, ap, start, sy):
    """Does a unit at `start` reach outside the footprint by the grid's moves (audit_structure_pockets.neighbours)?"""
    seen, stack = {start}, [start]
    while stack:
        c = stack.pop()
        if c[0] < 0 or c[2] < 0 or c[0] >= SX or c[2] >= SZ:
            return True
        for nb in ap.neighbours(pad, *c):
            if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 and 1 <= nb[1] <= sy + 1:
                seen.add(nb)
                stack.append(nb)
    return False


def check_levels(s1, s2, s3, paths=None):
    """The rules the game needs, on the three finished Structures (support already ran in build()). Returns a report."""
    import audit_structure_pockets as ap
    levels = (("L1", s1), ("L2", s2), ("L3", s3))
    for tag, s in levels:
        assert (s.size[0], s.size[2]) == (SX, SZ), "%s: footprint %s, the Grove's is 9 x 8" % (tag, s.size)
        n = names(s)
        assert max(p[1] for p in n) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
        layer0 = {nm for (x, y, z), nm in n.items() if y == 0}
        assert START_TYPES <= layer0, "%s: starting types missing from layer 0: %s" % (tag, START_TYPES - layer0)
        bad = sorted({nm for nm in n.values() if any(f in nm for f in FORBIDDEN) and nm != "water_cauldron"})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        lone = floating(s)
        assert not lone, "%s: floating blocks %s" % (tag, lone)
        for p, i in s.blocks.items():
            nm, props = s.palette[i]
            if nm.endswith("_leaves"):
                assert dict(props).get("persistent") == "true", (tag, p)
    # the chain: an upgrade only adds or replaces (BuildingPlacement.changeStructure / refreshBlocks)
    for (ta, a), (tb, b) in ((levels[0], levels[1]), (levels[1], levels[2])):
        lost = sorted(set(names(a)) - set(names(b)))
        assert not lost, "%s -> %s would leave blocks behind: %s" % (ta, tb, lost[:8])
    # the spawn: both centre columns open to the sky from the floor at every level, the treant's room clear, and the
    # centre walked down from L1's mid-height (getIndoorSpawnPoint) lands on the glade floor
    ymid = (s1.size[1] - 1) // 2
    for tag, s in levels:
        n = names(s)
        for (x, z) in CENTRE:
            assert (x, 0, z) in n, "%s: no floor under the spawn cell %d/%d" % (tag, x, z)
            blocked = [y for y in range(1, s.size[1]) if (x, y, z) in n]
            assert not blocked, "%s: the spawn column %d/%d is blocked at %s" % (tag, x, z, blocked)
            y = ymid
            while y > 0 and (x, y - 1, z) not in n:
                y -= 1
            assert y == 1, "%s: the spawn at %d/%d lands at y%d" % (tag, x, z, y)
        busy = [(p, n[p]) for p in CLEAR if p in n and not any(f in n[p] for f in FLOWERS)]
        assert not busy, "%s: the treant's room at the spawn is taken: %s" % (tag, busy)
    report = []
    if paths:
        for (tag, s), path in zip(levels, paths):
            one_way, ground, roof, rooms, note = ap.audit(tag, path)
            assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (
                tag, one_way[:6], ground[:6], note)
            pad = ap.Pad(ap.load_nbt(path))
            for (x, z) in CENTRE:
                assert walk_out(pad, ap, (x, 1, z), s.size[1]), "%s: a unit spawned at %d/1/%d can't walk out" % (
                    tag, x, z)
            # the walk-out GameTest builds a unit into a full block at standing height with a full block over it (a
            # trunk): every such cell must have a standable, escaping cell within reach (UnitUnstick rescues it to
            # the nearest one), and every standable floor cell in the footprint must walk out
            stuck = []
            for x in range(SX):
                for z in range(SZ):
                    for y in range(1, 3):
                        if pad.walkable(x, y, z) and not walk_out(pad, ap, (x, y, z), s.size[1]):
                            stuck.append((x, y, z))
            assert not stuck, "%s: floor cells that can't walk out: %s" % (tag, stuck[:8])
            report.append("%s %dx%dx%d, %d blocks: no pockets (%d roof-only cells), both spawn cells walk out, every "
                          "floor cell walks out" % (tag, s.size[0], s.size[1], s.size[2], len(names(s)), len(roof)))
    return report


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/lab_grove"
    os.makedirs(out, exist_ok=True)
    built = (l1(), l2(), l3())
    paths = [os.path.join(out, f) for f in ("grove.nbt", "grove_l2.nbt", "grove_l3.nbt")]
    for s, path in zip(built, paths):
        s.write(path)
    for line in check_levels(*built, paths=paths):
        print(line)
