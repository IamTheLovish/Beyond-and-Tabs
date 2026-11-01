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
south-west: it sees the tops, the west face (x = 0) and the south face (z = 7). Redrawn Nov 2026 by blind massing
rounds (PR #481; baseline critics 4 / 4: "a mossy garden lost in grass", "a leaf clump"; the old tree was a leaf
curtain two cells deep on the north edge, a strip from above): ONE CHERRY CROWN on a dark trunk standing on the
NORTH-WEST (where the camera sees its trunk, roots and face), the old shrine's stones on the EAST, the glade open to
the west and the south - the way units leave (grove_frees_its_units sends them to the far -x / +z corner).
  L1 THE STAG GLADE - the Mother Tree (stripped dark oak, one dark-mid value; x 1..3, z 0..2) with buttress roots flaring down the west
     fringe, toward the camera over a podzol root bed and along the north band; its crown three unequal lobes - the
     high one over the trunk, a lower one out east resting on the shrine's pier, a small one leaning to the camera -
     every leaf open to the sky or a side is cherry (the capitol's crown), the underside a dark oak-leaf hem.
     The STAG-DRYAD looks south from the trunk: a birch snout and mask, dark eye sockets, dark-oak brows, birch
     burrs and antlers rising into the leaning lobe. The shrine: a broken pier (chiseled foot, mossy / cracked courses,
     a jagged top with a lantern) and a wall fragment stepping down the east edge. Floor: quiet mossy flagstones in a
     stone kerb (the plot owns its edge), chiseled stones on the two spawn cells; one light, the pier's lantern.
  L2 THE ROOTED COURT - the trunk climbs into an upper tier over the crown (the high point 16), the pier rises into the
     shrine tower (a lancet, a jutting cornice, a lantern), the antlers climb through the leaning lobe.
  L3 THE ELDER GROVE - the trunk climbs again into a cherry crown (the high point 21), the tower's lancet glows and a
     second lantern stands on a wall post, the antlers climb to y 16 with tines.

GAME RULES (asserted in check_levels; the coordinator wires the levels, this file edits nothing):
  - every level keeps the 9 x 8 footprint (BuildingLevels has no larger footprint); a level grows taller (14, 17, 22);
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
TOP_L1, TOP_L2, TOP_L3 = 13, 17, 23     # each level's highest row (box heights 14, 18, 24)
BARK, BARK_W, BARK_S = "stripped_dark_oak_log", "stripped_dark_oak_wood", "dark_oak_stairs"   # one dark-mid value
# (oak bark read as a chequer, dark oak bark as a black lattice: critics, rounds 2 and 3)
HEM = "dark_oak_leaves"                  # the crown's underside
TRUNK = ((1, 0), (2, 0), (1, 1), (2, 1), (1, 2), (2, 2), (3, 0), (3, 1))   # the tree stands on the north-west
ROOT_BED = ((0, 0), (0, 1), (0, 2), (0, 3), (1, 3), (0, 4), (1, 4), (2, 3))  # podzol / moss under the west roots
LOBES_L1 = ((2.4, 1.3, 10.6, 3.6, 2.7, 2.9),   # the main crown over the trunk, the high point
            (6.7, 2.0, 9.3, 2.3, 2.4, 1.8),    # a lower lobe out east over the shrine
            (1.0, 4.0, 8.6, 1.5, 1.4, 1.2))    # a small lobe leaning to the camera (south-west) over the mask


def ground_l1(r):
    """y0: a stone kerb on the plot's edge (chiseled corners, mossy and cracked bricks between: the plot owns its edge
    against the lawn), quiet mossy flagstones inside with verdant froglight on the two spawn cells (where the trained
    units step out), and the root bed of podzol, rooted dirt and moss under the west roots. The foundation types (moss,
    mossy bricks) are in this layer."""
    for x in range(SX):
        for z in range(SZ):
            h = hsh(x, z)
            edge = x in (0, SX - 1) or z in (0, SZ - 1)
            if (x, z) in CENTRE:
                r.set(x, 0, z, CHB)                          # the spawn stones (a lit tile read as a skylight)
            elif (x, z) in ROOT_BED:
                name = ("podzol", "rooted_dirt", "podzol", "moss_block")[h % (2 if edge else 4)]
                r.set(x, 0, z, name, **({"snowy": "false"} if name == "podzol" else {}))
            elif edge:
                r.set(x, 0, z, CHB if x in (0, SX - 1) and z in (0, SZ - 1) else (CSB if h % 3 == 0 else MSB))
            else:
                r.set(x, 0, z, CSB if h % 5 == 0 else MSB)
    r.set(1, 0, 3, "moss_block")              # the foundation types stay in layer 0 whatever the scatter does


def plants_l1(r):
    """Ferns and flowers on the root bed's soil (a unit walks through them), never on the flagstones."""
    for (x, z) in ROOT_BED:
        if r.get(x, 0, z)[0] in SOIL and r.free(x, 1, z):
            k = hsh(x * 3 + 1, z + 5) % 9
            if k < len(FLOWERS):
                r.set(x, 1, z, FLOWERS[k])


def trunk(r, top):
    """The Mother Tree's trunk on the north-west (dark oak, one value), tapering: the north-east column stops two short,
    the south pair one short."""
    for (x, z) in TRUNK:
        t = top - (2 if x == 3 else 1 if z == 2 else 0)
        for y in range(1, t + 1):
            r.log(x, y, z, name=BARK)


def roots_l1(r):
    """Buttress roots flaring out of the trunk's feet: down the west fringe, toward the camera over the root bed and
    along the north band to the east (bark blocks, then stairs sloping down away from the trunk)."""
    for (x, y, z) in ((0, 1, 0), (0, 2, 0), (0, 1, 1), (0, 1, 2), (4, 1, 0), (1, 1, 3), (0, 1, 4), (2, 1, 4)):
        r.log(x, y, z, name=BARK_W)
    for (x, y, z, f) in ((0, 3, 0, "east"), (0, 2, 1, "east"), (0, 2, 2, "east"), (0, 1, 3, "north"),
                         (2, 1, 3, "north"), (1, 2, 3, "north"), (1, 1, 4, "north"), (4, 2, 0, "west"),
                         (5, 1, 0, "west"), (4, 1, 1, "west")):
        r.st(x, y, z, f, name=BARK_S)


def boughs(r, y0):
    """A bough climbing out east from the trunk into the shrine's lobe."""
    for k in range(3):
        for dx in (0, 1):
            x = 3 + 2 * k + dx
            if x < SX:
                r.log(x, y0 + k, 1, axis="x", name=BARK)


def mask_l1(r, y0=4):
    """The STAG-DRYAD on the trunk's south face (z 3, where the camera looks): a birch snout hollowed under, the pale
    mask with verdant froglight eyes, dark-oak brows, and birch antlers rising either side into the leaning lobe."""
    for x in (1, 2):
        r.st(x, y0, 3, "north", "top", name=BIRCH_S)       # the snout, hollowed under
        r.log(x, y0 + 1, 3, name=BIRCH_W)                  # the muzzle
        r.log(x, y0 + 2, 3, name=BARK)                     # the eyes, dark sockets (lit, the mask read as a lamp)
        r.st(x, y0 + 3, 3, "north", "top", name=DOS)       # the brows
        r.log(x, y0 + 4, 3, name=BIRCH)                    # the burrs
    for (x, y) in ((0, y0 + 4), (0, y0 + 5), (3, y0 + 4), (3, y0 + 5)):   # the antlers' beams
        r.set(x, y, 3, BIRCH_F)


def antlers_up(r, y0, y1):
    """The antlers climb as the tree grows: birch beams at x 0 and 3 (z 3), a knuckle every third row."""
    for y in range(y0, y1 + 1):
        for x in (0, 3):
            if (y - y0) % 3 == 2:
                r.log(x, y, 3, name=BIRCH)
            else:
                r.set(x, y, 3, BIRCH_F)


def lobes(r, specs, y_lo, y_hi):
    for (cx, cz, cy, rx, rz, ry) in specs:
        for x in range(SX):
            for z in range(SZ):
                if (x, z) in CENTRE:
                    continue
                for y in range(y_lo, y_hi + 1):
                    if ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                        r.leaf(x, y, z)


def shell(r):
    """The crown's colours by exposure: a leaf open to the sky or to a side is CHERRY (the capitol's crown, the lab's
    read against the lawn); one open only below is the dark HEM (the underside in shadow, a dark line under the pink
    in 3/4); the inside stays azalea. No fruit: a lit row under the lobe read as "teeth" / "windows" (round 3)."""
    leaves = [p for p, (n, _) in r.b.items() if n.endswith("_leaves")]

    def air(q):
        return not (0 <= q[0] < SX and 0 <= q[2] < SZ) or q not in r.b

    for (x, y, z) in leaves:
        up = air((x, y + 1, z))
        side = any(air(q) for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)))
        down = air((x, y - 1, z))
        if up or (side and not down):
            name = CHERRY
        elif down:
            name = HEM
        else:
            name = FLEAF if hsh(x + y, z) % 4 == 0 else LEAF
        r.set(x, y, z, name, persistent="true", distance="7", waterlogged="false")


def shrine_l1(r):
    """The old shrine east of the tree, two unequal masses: a broken pier (x 7..8, z 3..4) under the east lobe - the
    crown rests on it, tree and shrine one gate over the glade - with a chiseled foot, mossy and cracked courses, a
    jagged top and a lantern on its front corner; and a wall fragment down the east edge (x 8, z 5..7) stepping down
    4, 3, 2 with stair copings."""
    for x in (7, 8):
        for z in (3, 4):
            r.set(x, 1, z, CHB)
            for y in range(2, 8):
                r.set(x, y, z, MSB if hsh(x * 5 + y, z + y) % 3 else CSB)
    r.set(8, 8, 4, CSB)                                  # the jagged top
    r.lantern(7, 8, 4)
    for z, h in ((5, 4), (6, 3), (7, 2)):
        for y in range(1, h):
            r.set(8, y, z, MSB if (y + z) % 3 else CSB)
        r.st(8, h, z, "north", name=MSS)
    r.set(7, 1, 6, MC)                                   # a fallen block at the wall's foot


def draw_l1(r):
    ground_l1(r)
    trunk(r, 9)
    roots_l1(r)
    boughs(r, 7)
    mask_l1(r)
    shrine_l1(r)
    lobes(r, LOBES_L1, 6, TOP_L1)
    for p in CLEAR:                           # the spawn room stays clear whatever a step above drew
        r.b.pop(p, None)
    plants_l1(r)


# ======================================================================================================== L2
def tower_l2(r):
    """The pier rises into a tower (x 7..8, z 4..5) to y 10: courses, a lancet slot on the south face, a jutting cornice
    of upside-down stairs and a lantern on the top."""
    for x in (7, 8):
        for z in (3, 4, 5):
            for y in range(1, 10):
                r.set(x, y, z, CHB if y == 1 else (MSB if hsh(x * 5 + y, z + y) % 3 else CSB))
    for y in (4, 5, 6):
        r.set(7, y, 5, MSW)                              # the lancet
    for x in (7, 8):
        r.st(x, 10, 5, "north", "top")
    r.set(7, 10, 4, MSB)
    r.set(8, 10, 4, "moss_block")
    r.lantern(7, 11, 4)


def draw_l2(r):
    """L2 THE ROOTED COURT: L1 grown - the trunk climbs to y 13 into an upper tier over the main crown (the high point
    16-17), the east pier rises into the shrine tower, the antlers climb through the leaning lobe."""
    draw_l1(r)
    trunk(r, 13)
    tower_l2(r)
    antlers_up(r, 10, 11)
    lobes(r, ((2.0, 1.2, 14.4, 2.7, 2.1, 2.3),), 12, TOP_L2)
    for p in CLEAR:
        r.b.pop(p, None)


# ======================================================================================================== L3
def draw_l3(r):
    """L3 THE ELDER GROVE: the elder - the trunk to y 19 into a cherry crown (high point 22-23), the tower's lancet
    glows and a second lantern stands on its wall post, the antlers climb to y 16 with tines leaning out."""
    draw_l2(r)
    trunk(r, 19)
    lobes(r, ((2.2, 1.4, 19.6, 2.4, 1.9, 2.4),), 17, TOP_L3)
    for y in (4, 5, 6):
        r.set(7, y, 5, GLOW, axis="y")                   # the lancet wakes
    r.set(8, 11, 4, MSW)
    r.set(8, 12, 4, MSW)
    r.lantern(8, 13, 4)
    antlers_up(r, 12, 15)
    r.set(0, 16, 3, BIRCH_F)                             # the tines
    r.set(3, 16, 3, BIRCH_F)
    for p in CLEAR:
        r.b.pop(p, None)



YMAX = 24                       # drawing room; each level's box is cropped to its top block


def build(draw, tier):
    r = Rig(YMAX)
    draw(r)
    shell(r)
    mv._leaf_distances(r)
    r.bury_grass()   # no grass under the hay bales: it would die to dirt and stall the build
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
