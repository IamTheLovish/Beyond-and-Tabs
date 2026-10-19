#!/usr/bin/env python3
"""The Brassworks T1 army lab, the Machine Shop, redrawn at all three levels - L1 THE MACHINE SHOP, L2 THE ENGINE WORKS,
L3 THE CLOCKWORK FOUNDRY - so it belongs to the family the Grand Foundry (the capitol, tools/capitol_foundry_hall.py)
and the Iron Golem Drill (the extractor, tools/mex_brass.py) set.

lovish, Oct 19 2026: buildings must "look like they match each other so when bases get bigger they look cohesive and
beautiful"; "upgraded buildings can change in shape or fundamental design ... make upgraded buildings stand out for the
opponent; ... much bigger or have cooler, more intricate designs". The old shop was a flat brass lid on soot posts
(9 x 6 x 9, 216 blocks) and its levels a shared crown (gen_lab_levels.brass_lab, which the T2 / T3 labs keep).

THE BRASSWORKS STYLE KIT (the capitol's, the extractor's):
  - main blocks: COPPER in its weathering stages (waxed cut / exposed / weathered / oxidized cut copper, their stairs and
    slabs; bright waxed copper blocks for gear rims and boilers) and DARK OAK (planks, stripped logs as the timber
    frame, stairs, fences); SOOT (polished blackstone bricks, deepslate) as the plinth and the stacks;
  - trim: IRON - iron bars, chains, iron blocks (gear teeth, crane hooks, catwalk rails);
  - light: LANTERNS and GLOWING FURNACE MOUTHS (lit blast furnaces, smokers, orange glass over shroomlight) and the
    clock's glowing hub (ochre froglight);
  - silhouette motif: PIPES and SMOKESTACKS - copper (lightning-rod) and soot (blackstone-wall) pipe runs with stair
    elbows; a campfire smoking on every stack top (only on stack tops, where no unit walks);
  - signature detail: THE GEAR - the capitol's great clock gears (a bright copper rim, iron teeth, a glowing hub) on
    the engine house's face, and the clock tower's dial at L3;
  - family figure: the IRON GOLEM DRILL's automaton - here a golem being BUILT on the assembly stand (this is the lab
    that makes the faction's machines): L1 a bare frame on the stand, L2 plated and lit under the hoist, L3 finished
    with its boiler pack and smokestack;
  - edge: the works yard - rails, crates, coal, lantern posts - on blackstone, open on the yard's two sides.

THE PLAN (9 x 9, the shipped footprint). The RTS camera looks from the south-west: it sees the roofs, the west face
(x = 0) and the south face (z = 8). So the shop is an L round a WORKS YARD that opens toward the camera:
  - the SHOP (x 0..5, z 0..2): a dark-oak and copper range under a copper gable, two furnace bays facing the yard and a
    lit smoker's mouth and gable window on its west end toward the camera;
  - the ENGINE HOUSE (x 6..8, z 0..8): a taller weathered-copper block whose west face carries THE GEAR over the yard
    and whose south end (the camera's face) holds the big furnace door; its smokestack on the back corner;
  - the WORKS YARD (x 0..5, z 3..8): blackstone with a copper turntable under the spawn, rails running out to the
    south, the assembly stand with its golem on the west side, crates and a lantern post.
  L2 THE ENGINE WORKS - the shop becomes a factory:
  - the shop's roof becomes a SAWTOOTH roof (three teeth of orange glass facing west, toward the camera);
  - a second, taller stack on the shop's back, a soot pipe bridge between the two stacks, copper risers;
  - a BOILER (a riveted bright-copper drum with a pressure dome and a gauge) on the engine house;
  - a JIB CRANE out of the shop's roof over the assembly stand (a soot beam on a dark-oak brace, a chain hoist holding
    the golem's helm, a hung lantern) and the golem plated and lit (pauldrons with lanterns, a visor, a breastplate).
  L3 THE CLOCKWORK FOUNDRY - the landmark:
  - a CLOCK TOWER on the shop's west end, the corner the camera meets first (the Barracks' turret has the same place):
    a soot foot, dark-oak quoins, oxidized-copper walls, a gear dial in its west face and a second dial standing proud
    of its south face over the yard, a cornice leaning out with lamps, the capitol's copper hip, crown and lantern;
  - the shop's and the engine house's stacks grow into tall banded chimneys (19 and 20 high), a soot pipe crosses the
    roof from the tower;
  - a CATWALK (dark-oak slabs, an iron-bar rail, a hung lantern) from the tower to the engine house along the yard's
    back edge (never over the spawn);
  - the golem finished: a boiler pack on its back, a bright copper crown.

GAME RULES (asserted in check_levels):
  - every level keeps the 9 x 9 footprint (BuildingLevels has no larger footprint); a level may grow taller;
  - the chain: every non-air cell of L1 is non-air in L2, and of L2 in L3 (changeStructure only adds or replaces):
    L2 is drawn over L1 and L3 over L2, and that is asserted;
  - MachineShop.startingBlockTypes = polished_blackstone, deepslate_bricks: in layer 0 (the yard and the footings),
    nowhere above;
  - the unit spawn: ProductionBuilding.getIndoorSpawnPoint walks down from centrePos, the L1 box's centre. On this odd
    footprint (minX + maxX) / 2 is a whole number, so centrePos is the cell (4, 4) whatever the rotation or the sign of
    the world coordinates, at y = L1 maxY / 2; changeStructure never moves it. The yard keeps the 3 x 3 round it
    (x 3..5, z 3..5) open from the floor to the sky at every level (the Iron Warden is 1.15 wide and 2.2 tall), and
    units walk out of it to the south and the west;
  - nothing pops off (lanterns standing / hung, chains); no pockets (tools/audit_structure_pockets.py on all three);
    no water, lava, magma, gravity blocks (anvils), redstone, doors, leaves; campfires only on stack tops no unit
    can reach.

Usage: python3 tools/lab_machine_shop.py [OUT_DIR]   (writes machine_shop.nbt, machine_shop_l2.nbt, machine_shop_l3.nbt
       and checks them; default OUT_DIR: the scratch folder - shipping them is the coordinator's step)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

SX, SZ = 9, 9
CENTRE = [(4, 4)]                                   # the spawn cell (see the docstring)
CLEAR = [(x, z) for x in (3, 4, 5) for z in (3, 4, 5)]   # kept open to the sky round it, for wide machines
START_TYPES = ("polished_blackstone", "deepslate_bricks")   # MachineShop.startingBlockTypes

# the kit's blocks, by role (the capitol's names)
CU, CU_E, CU_W, CU_O = ("waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper",
                        "waxed_oxidized_cut_copper")
CUB = "waxed_copper_block"
DO, DOL, DOS, DOF, DOSL = "dark_oak_planks", "stripped_dark_oak_log", "dark_oak_stairs", "dark_oak_fence", "dark_oak_slab"
SB, SBS, SBW = "polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_wall"
CPB = "chiseled_polished_blackstone"
DT = "deepslate_tiles"
LANTERN = dict(hanging="false", waterlogged="false")
CAMPFIRE = dict(lit="true", facing="south", signal_fire="false", waterlogged="false")
ROD = dict(powered="false", waterlogged="false")


def st(c):
    return c + "_stairs"


def sl(c):
    return c + "_slab"


class Rig(gs._SunRig):
    """The rig canvas from gen_structures (settle() shapes stairs and joins walls, bars and fences like the game) with
    the support check this lab needs: _SunRig's own refuses hung lanterns, and this lab hangs them under eaves and
    from chains."""

    def __init__(self, sy):
        super().__init__(SX, sy, SZ)

    def st(self, x, y, z, facing, half="bottom", name=st(CU)):
        self.stair(x, y, z, facing, half, name)

    def slab(self, x, y, z, name=sl(CU), typ="bottom"):
        self.set(x, y, z, name, type=typ, waterlogged="false")

    def fill(self, x1, y1, z1, x2, y2, z2, name, **p):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **p)

    def log(self, x, y, z):
        self.set(x, y, z, DOL, axis="y")

    def lantern(self, x, y, z, hanging=False):
        self.set(x, y, z, "lantern", hanging="true" if hanging else "false", waterlogged="false")

    def rod(self, x, y, z, facing="up"):
        self.set(x, y, z, "lightning_rod", facing=facing, **ROD)

    def chain(self, x, y, z, axis="y"):
        self.set(x, y, z, "chain", axis=axis, waterlogged="false")

    def check_support(self, tier):
        bad = []
        for pos, (n, p) in self.b.items():
            below, above = self.nb(pos, "down"), self.nb(pos, "up")
            if n in ("lantern", "soul_lantern"):
                if p.get("hanging") == "true":
                    ok = gs._sun_sturdy(above, "down") or above[0] in ("chain",) or \
                        above[0].endswith(("_fence", "_wall")) or above[0] == "iron_bars"
                else:
                    ok = gs._sun_centre_up(below)
            elif n.endswith("candle"):
                ok = gs._sun_centre_up(below)
            elif n == "rail":
                ok = gs._sun_sturdy(below, "up")
            elif n == "campfire":
                ok = gs._sun_sturdy(below, "up")
            elif n.endswith(("_torch", "_button", "ladder", "_carpet", "_banner")) or n in ("torch", "bell"):
                ok = False    # none are drawn on this lab
            else:
                continue
            if not ok:
                bad.append((pos, n))
        assert not bad, "machine shop %s: would pop off / hang: %s" % (tier, sorted(bad))


def ring(r, x1, x2, z1, z2, y, name=st(CU), half="bottom"):
    """A ring of stairs climbing inward over [x1..x2] x [z1..z2] (settle() turns the corners): the kit's dome tier."""
    for x in range(x1, x2 + 1):
        r.st(x, y, z1, "south", half, name)
        r.st(x, y, z2, "north", half, name)
    for z in range(z1 + 1, z2):
        r.st(x1, y, z, "east", half, name)
        r.st(x2, y, z, "west", half, name)


def stack(r, x, z, y1, y2, smoke=True):
    """A soot smokestack from y1 to y2 (inclusive), banded in exposed copper every third course, a copper cap and the
    kit's smoke (a lit campfire) on top."""
    for y in range(y1, y2):
        r.set(x, y, z, CU_E if y % 3 == 0 else SB)
    r.set(x, y2, z, CU)
    if smoke:
        r.set(x, y2 + 1, z, "campfire", **CAMPFIRE)


# ======================================================================================================== L1
EAVE = 4             # the shop's eave course (the copper lintel over the bays)
EH = 7               # the engine house's top wall course
GZ, GY = 5, 5        # THE GEAR's hub on the engine house's west face (x 6)
BAYS = (1, 4)        # the shop's furnace bays on the yard face
GOLEM = (1, 6)       # the assembly stand


def pad_l1(r):
    """Layer 0: the instant foundation. The yard is polished blackstone with a deepslate-brick kerb on its two open
    sides (both starting types); the ranges stand on deepslate-brick footings round deepslate tiles; a copper
    turntable lies under the spawn (a bright hub, a verdigris ring) - the floor the finished machines roll off."""
    for x in range(SX):
        for z in range(SZ):
            yard = x <= 5 and z >= 3
            edge = x in (0, SX - 1) or z in (0, SZ - 1)
            r.set(x, 0, z, ("deepslate_bricks" if edge else "polished_blackstone") if yard else
                  ("deepslate_bricks" if edge else DT))
    for (x, z) in CLEAR:
        r.set(x, 0, z, CU_O)
    r.set(4, 0, 4, CUB)


def shop_l1(r):
    """The shop (x 0..5, z 0..2): a soot plinth, a dark-oak timber frame with weathered-copper infill, two furnace
    bays facing the yard, a west end with a lit smoker's mouth and a window toward the
    camera. A solid range: nothing to walk into."""
    r.fill(0, 1, 0, 5, 1, 2, SB)
    r.fill(0, 2, 0, 5, EAVE, 2, CU_W)
    for x in (0, 2, 3, 5):                           # the timber frame on the yard face (pier plane)
        for y in range(1, EAVE + 1):
            r.log(x, y, 2)
    for y in range(1, EAVE + 1):                     # the quoin at the back of the west end
        r.log(0, y, 0)
    for bx in BAYS:                                  # the bays: a furnace mouth, a tall window, a copper lintel
        r.set(bx, 1, 2, "blast_furnace", facing="south", lit="true")
        r.set(bx, 2, 2, "orange_stained_glass")
        r.set(bx, 3, 2, "orange_stained_glass")
        r.set(bx, 2, 1, "shroomlight")
        r.set(bx, 3, 1, "shroomlight")
        r.set(bx, EAVE, 2, CU_E)
    r.set(0, 2, 1, "smoker", facing="west", lit="true")   # the west end: a furnace mouth and a window over it
    r.set(0, 3, 1, "orange_stained_glass")
    r.set(1, 3, 1, "shroomlight")
    r.set(0, EAVE, 1, CU_E)
    # the roof: a copper gable with its ridge along x (weathered stairs on both eaves, an exposed ridge course, a slab
    # cap) and two vent pipes standing on the ridge; the west gable end, toward the camera, a dark-oak tympanum with a
    # glowing window
    for x in range(0, 6):
        r.st(x, EAVE + 1, 0, "south", name=st(CU_W))
        r.st(x, EAVE + 1, 2, "north", name=st(CU_W))
        r.set(x, EAVE + 1, 1, CU_E)
        r.slab(x, EAVE + 2, 1, sl(CU))
    r.set(0, EAVE + 1, 1, "orange_stained_glass")
    r.set(1, EAVE + 1, 1, "shroomlight")
    for x in (2, 4):
        r.rod(x, EAVE + 3, 1)


def gear(r, gz, gy):
    """THE GEAR (the capitol's great gear, small): on the engine house's west face (x 6) a round rim of bright copper
    (a 5 x 5 border with its corners cut, so it reads round), iron teeth beyond the rim's top and bottom, and inside
    the rim a recess - a glowing hub and two iron-bar spokes over the verdigris plate one block behind."""
    for dz in range(-2, 3):
        for dy in range(-2, 3):
            border = abs(dz) == 2 or abs(dy) == 2
            corner = abs(dz) == 2 and abs(dy) == 2
            if border and not corner:
                r.set(6, gy + dy, gz + dz, CUB)
            else:
                r.b.pop((6, gy + dy, gz + dz), None)      # the recess (and the cut corners) stay open
            if not border:
                r.set(7, gy + dy, gz + dz, CU_O)
    r.set(6, gy, gz, "ochre_froglight", axis="x")
    r.set(6, gy + 1, gz, "iron_bars")
    r.set(6, gy, gz + 1, "iron_bars")
    r.set(6, gy - 3, gz, "iron_block")               # the teeth
    r.set(6, gy + 3, gz, "iron_block")


def engine_l1(r):
    """The engine house (x 6..8, z 0..8): weathered copper on a soot plinth, dark-oak quoins, THE GEAR on its west face
    over the yard, a furnace door with a grilled window on its south end (the camera's face), a hip of copper stairs
    and a ridge, and the smokestack on its back corner."""
    r.fill(6, 1, 0, 8, 1, 8, SB)
    r.fill(7, 2, 0, 8, EH, 8, CU_W)
    r.fill(6, 2, 0, 6, EH, 2, CU_W)
    r.fill(6, 2, 3, 6, 2, 7, SB)                     # the gear's footing: a two-course soot plinth
    for (x, z) in ((8, 0), (6, 8), (8, 8), (6, 2)):
        for y in range(1, EH + 1):
            r.log(x, y, z)
    r.set(6, 6, 1, "orange_stained_glass")           # a lit window in the face that stands over the shop's roof
    r.set(7, 6, 1, "shroomlight")
    r.st(6, 7, 1, "west", "top", st(CU_E))           # its hood
    for z in range(3, 8):                            # the frame over the gear
        r.set(6, EH, z, CU_E)
    gear(r, GZ, GY)
    # the south end (z 8): a lit blast furnace (the door), a soot lintel, a window of orange glass recessed behind an
    # iron grille, an exposed band
    r.set(7, 1, 8, "blast_furnace", facing="south", lit="true")
    r.set(7, 2, 8, SB)
    for y in (3, 4):
        r.set(7, y, 8, "iron_bars")
        r.set(7, y, 7, "orange_stained_glass")
        r.set(7, y, 6, "shroomlight")
    r.set(7, 5, 8, CU_E)
    r.st(7, 6, 8, "south", "top", st(CU_E))          # a hood over the window
    # the roof: a hip of copper stairs climbing to an exposed deck and a slab ridge; the stack on the back corner
    ring(r, 6, 8, 0, 8, EH + 1)
    for z in range(1, 8):
        r.set(7, EH + 1, z, CU_E)
        r.slab(7, EH + 2, z, sl(CU))
    stack(r, 8, 0, EH + 1, EH + 4)


def golem_l1(r):
    """The golem on the assembly stand (the Iron Golem Drill's automaton, the capitol's yard golems): basalt legs, the
    lit furnace belly toward the camera, a copper chest, the glowing face, the helm and its antenna, chain arms
    hanging from stair shoulders."""
    x, z = GOLEM
    r.set(x, 1, z, "polished_basalt", axis="y")
    r.set(x, 2, z, "blast_furnace", facing="south", lit="true")
    r.set(x, 3, z, CU)
    r.set(x, 4, z, "shroomlight")
    r.slab(x, 5, z, sl(CU_E))
    r.rod(x, 6, z)
    for ax, f in ((x - 1, "west"), (x + 1, "east")):
        r.st(ax, 3, z, f, "top", st(CU_E))
        r.chain(ax, 2, z)


def yard_l1(r):
    """The works yard (x 0..5, z 3..8): rails from the turntable out to the south, crates and coal by the engine
    house's door, a barrel of tools with a grindstone, lantern posts on the corners away from the ways out."""
    for zz in range(6, 9):
        r.set(4, 1, zz, "rail", shape="north_south", waterlogged="false")
    r.set(5, 1, 8, "barrel", facing="up", open="false")
    r.set(5, 1, 7, "coal_block")
    r.set(0, 1, 8, "barrel", facing="up", open="false")
    r.set(0, 2, 8, "grindstone", face="floor", facing="north")
    for (x, z) in ((0, 3), (2, 8)):
        r.set(x, 1, z, DOF)
        r.lantern(x, 2, z)


def draw_l1(r):
    pad_l1(r)
    shop_l1(r)
    engine_l1(r)
    golem_l1(r)
    yard_l1(r)


# ======================================================================================================== L2
STACK_W, STACK_E = (5, 0), (8, 0)     # the twin stacks (the shop's back-east corner, the engine house's)


def sawtooth_l2(r):
    """The shop's roof becomes a SAWTOOTH north-light roof (the factory's tell): three teeth along x, each a strip of
    orange glass facing west - toward the camera - under a copper slab cap, and a copper slope falling east from it to
    the next tooth. The glass glows: a shroomlight under each tooth."""
    r.fill(0, EAVE + 1, 0, 5, EAVE + 1, 2, CU_W)
    for a in (0, 2, 4):
        for z in (0, 1, 2):
            r.set(a, EAVE + 2, z, "orange_stained_glass")
            r.st(a, EAVE + 3, z, "west", name=st(CU))
            r.slab(a + 1, EAVE + 2, z, sl(CU_E))
        r.set(a, EAVE + 1, 1, "shroomlight")
    r.set(1, EAVE + 3, 2, SB)          # the jib's root on the roof


def stacks_l2(r):
    """Twin stacks at the back: the shop's new one at its back-east corner, the engine house's grown taller, a soot
    pipe bridge between them and a copper collar where it meets each."""
    x, z = STACK_W
    stack(r, x, z, EAVE + 1, 12)
    x, z = STACK_E
    stack(r, x, z, EH + 1, 14)
    for xx in (6, 7):
        r.set(xx, 10, 0, SBW)
    r.slab(6, 11, 0, sl(CU_E))


def boiler_l2(r):
    """A BOILER on the engine house's roof: a riveted drum along z (bright copper, its round shoulders stairs), two
    verdigris straps, a pressure dome with a gauge valve; a soot pipe feeds the stack."""
    for z in range(2, 7):
        strap = z in (2, 6)
        side = st(CU_E) if strap else st(CU)
        r.st(6, EH + 2, z, "east", "top", side)
        r.st(8, EH + 2, z, "west", "top", side)
        r.st(6, EH + 3, z, "east", "bottom", side)
        r.st(8, EH + 3, z, "west", "bottom", side)
        r.set(7, EH + 2, z, CU_E if strap else CUB)
        r.set(7, EH + 3, z, CU_E if strap else CUB)
    r.set(7, EH + 4, 4, CUB)
    r.slab(7, EH + 5, 4, sl(CU_O))
    r.rod(7, EH + 6, 4)
    r.set(8, EH + 2, 1, SBW)           # the feed to the stack
    r.set(8, EH + 2, 7, SBW)


def crane_l2(r):
    """A jib crane out of the shop's roof over the assembly stand: a soot beam on a dark-oak brace, a chain hoist
    holding the golem's helm, a lantern hung under the beam."""
    x, z = GOLEM
    for zz in range(3, z + 1):
        r.set(x, EAVE + 3, zz, SB if zz < z else CPB)
    r.st(x, EAVE + 2, 3, "north", "top", DOS)           # the brace under the beam, against the shop
    r.chain(x, EAVE + 2, z)                              # the hoist chain (replaces the antenna)
    r.lantern(x, EAVE + 2, 4, hanging=True)


def golem_l2(r):
    """The golem plated and lit: brass pauldrons carrying lanterns, a verdigris breastplate, an iron-bars visor."""
    x, z = GOLEM
    for ax in (x - 1, x + 1):
        r.set(ax, 4, z, CU_E)
        r.lantern(ax, 5, z)
    r.set(x, 4, z + 1, "iron_bars")
    r.st(x, 3, z + 1, "north", "top", st(CU_O))


def draw_l2(r):
    draw_l1(r)
    sawtooth_l2(r)
    stacks_l2(r)
    boiler_l2(r)
    crane_l2(r)
    golem_l2(r)


# ======================================================================================================== L3
TW = 16          # the clock tower's cornice course
DIAL = 12        # the dial's hub height


def tower_l3(r):
    """The CLOCK TOWER on the shop's west end (x 0..2, z 0..2), the corner the camera meets first: a soot foot, an
    exposed-copper band with lit windows, an oxidized-copper shaft between dark-oak quoins, then the clock stage - a
    GEAR DIAL (a bright copper ring round a glowing hub, iron teeth over and under it) in the west face, and on the
    south face a second dial standing proud of the wall over the yard (the third depth) - a cornice of upside-down
    stairs leaning out over the yard, lanterns on the corners, a ring of stairs, the crown and the lightning-rod
    spire."""
    for y in range(EAVE + 1, TW):
        face = SB if y < 8 else CU_E if y == 8 else CU_O
        for x in (0, 1, 2):
            for z in (0, 1, 2):
                corner = x != 1 and z != 1
                if corner:
                    r.log(x, y, z)
                else:
                    r.set(x, y, z, face)
        r.set(1, y, 1, DO)
    for (x, z) in ((0, 1), (1, 2)):                    # the lit windows in the band
        r.set(x, 8, z, "orange_stained_glass")
    r.set(1, 8, 1, "shroomlight")
    # the west dial, in the face (x 0)
    for k in (-1, 0, 1):
        for dy in (-1, 0, 1):
            r.set(0, DIAL + dy, 1 + k, CUB)
    r.set(0, DIAL, 1, "ochre_froglight", axis="x")
    r.set(0, DIAL - 2, 1, "iron_block")
    r.set(0, DIAL + 2, 1, "iron_block")
    # the south dial, proud of the face (z 3, over the yard's west side - never over the spawn's 3 x 3)
    for k in (-1, 0, 1):
        for dy in (-1, 0, 1):
            r.set(1 + k, DIAL + dy, 3, CUB)
    r.set(1, DIAL, 3, "ochre_froglight", axis="z")
    r.set(1, DIAL + 1, 3, "iron_bars")                # the hand, at twelve
    r.set(1, DIAL - 2, 3, "iron_block")
    r.set(1, DIAL + 2, 3, "iron_block")
    # the cornice: an exposed cap, upside-down stairs leaning out over the yard (z 3) and over the roof (x 3)
    r.fill(0, TW, 0, 2, TW, 2, CU_E)
    for x in (0, 1, 2):
        r.st(x, TW, 3, "north", "top")
    for z in (0, 1, 2):
        r.st(3, TW, z, "west", "top")
    for x in (0, 2):
        r.lantern(x, TW + 1, 3)                       # lamps on the cornice's corners, out over the yard
    ring(r, 0, 2, 0, 2, TW + 1)                       # the copper hip, the crown and its lantern (the capitol's dome)
    r.set(1, TW + 1, 1, CU_E)
    r.set(1, TW + 2, 1, CUB)
    r.lantern(1, TW + 3, 1)


def stacks_l3(r):
    """Both stacks grow into tall banded chimneys; a soot pipe runs over the roof from the tower to the engine
    house."""
    x, z = STACK_W
    stack(r, x, z, EAVE + 1, 16)
    x, z = STACK_E
    stack(r, x, z, EH + 1, 18)
    for x in range(3, 6):
        r.set(x, 8, 1, SBW)


def catwalk_l3(r):
    """A CATWALK from the tower to the engine house along the yard's back edge (z 2, never over the spawn's 3 x 3):
    dark-oak top slabs over the sawtooth, an iron-bar rail, a lantern hung under its copper middle beam."""
    for x in range(3, 6):
        r.slab(x, 9, 2, DOSL, "top")
        r.set(x, 10, 2, "iron_bars")
    r.set(4, 9, 2, CU_E)               # a copper cross-beam in the middle, a lantern hung from it
    r.lantern(4, 8, 2, hanging=True)


def golem_l3(r):
    """The golem finished: a boiler pack on its back, a bright copper crown on the helm."""
    x, z = GOLEM
    r.set(x, 2, z - 1, SB)
    r.set(x, 3, z - 1, CU_E)
    r.set(x, 4, z - 1, SB)
    r.set(x, 5, z, CUB)


def draw_l3(r):
    draw_l2(r)
    tower_l3(r)
    stacks_l3(r)
    catwalk_l3(r)
    golem_l3(r)


# ======================================================================================================== build
L1_SY = EH + 6
L2_SY = 17
L3_SY = 23


def build(draw, sy, tier):
    r = Rig(sy)
    draw(r)
    r.settle()
    r.check_support(tier)
    s = r.structure()
    top = max(y for (x, y, z), (n, p) in r.b.items() if n not in ("air", "cave_air"))
    if top + 1 != sy:     # trim the box to the drawn blocks
        s2 = gs.Structure(SX, top + 1, SZ)
        for (x, y, z), (n, p) in sorted(r.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            s2.set(x, y, z, n, **p)
        s = s2
    return s


def l1():
    return build(draw_l1, L1_SY, "L1")


def l2():
    return build(draw_l2, L2_SY, "L2")


def l3():
    return build(draw_l3, L3_SY, "L3")


def base():
    """The L1 the game ships as machine_shop.nbt."""
    return l1()


def levels():
    """(L2, L3) for gen_lab_levels.LABS["machine_shop"], like lab_barracks.levels()."""
    return l2(), l3()


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "_door", "_leaves", "_carpet", "magma_block",
             "soul_sand", "candle")


def check_levels(s1, s2, s3, paths=None):
    """The rules the game needs, on the three finished Structures (support already ran in build())."""
    import audit_structure_pockets as ap
    levels = (("L1", s1), ("L2", s2), ("L3", s3))
    for tag, s in levels:
        assert (s.size[0], s.size[2]) == (SX, SZ), "%s: footprint %s, the Machine Shop's is 9 x 9" % (tag, s.size)
        n = names(s)
        assert max(p[1] for p in n) == s.size[1] - 1, "%s: the box is taller than its blocks" % tag
        assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "%s: a hole in the foundation" % tag
        bad = sorted({nm for nm in n.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)
                      and nm != "campfire"})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        for t in START_TYPES:
            layer0 = [p for p, nm in n.items() if nm == t and p[1] == 0]
            above = [p for p, nm in n.items() if nm == t and p[1] > 0]
            assert len(layer0) >= 6 and not above, "%s: starting type %s: %d in layer 0, %d above" % (
                tag, t, len(layer0), len(above))
        # campfires (they burn units) only on stack tops: high up, on the stack's copper cap
        for (x, y, z), nm in n.items():
            if nm == "campfire":
                assert y >= 9 and n.get((x, y - 1, z)) == CU, "%s: a campfire at %s is not on a stack top" % (
                    tag, (x, y, z))
    # the chain: an upgrade only adds or replaces (BuildingPlacement.changeStructure / refreshBlocks)
    for (ta, a), (tb, b) in ((levels[0], levels[1]), (levels[1], levels[2])):
        lost = sorted(set(names(a)) - set(names(b)))
        assert not lost, "%s -> %s would leave blocks behind: %s" % (ta, tb, lost[:8])
    # the spawn: the 3 x 3 round the centre open from the floor to the sky at every level
    for tag, s in levels:
        n = names(s)
        for (x, z) in CLEAR:
            assert (x, 0, z) in n, "%s: no floor under the spawn cell %d/%d" % (tag, x, z)
            blocked = [y for y in range(1, s.size[1]) if (x, y, z) in n]
            assert not blocked, "%s: the spawn column %d/%d is blocked at %s" % (tag, x, z, blocked)
    report = []
    if paths:
        for (tag, s), path in zip(levels, paths):
            one_way, ground, roof, rooms, note = ap.audit(tag, path)
            assert not one_way and not ground and not rooms and "TRAPPED" not in note, "%s: pockets %s %s %s %s" % (
                tag, one_way[:6], ground[:6], rooms[:4], note)
            pad = ap.Pad(ap.load_nbt(path))
            for (x, z) in CENTRE:
                start = (x, 1, z)
                seen, stack_, out = {start}, [start], False
                while stack_ and not out:
                    c = stack_.pop()
                    if c[0] < 0 or c[2] < 0 or c[0] >= SX or c[2] >= SZ:
                        out = True
                        break
                    for nb in ap.neighbours(pad, *c):
                        if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 \
                                and 1 <= nb[1] <= s.size[1] + 1:
                            seen.add(nb)
                            stack_.append(nb)
                assert out, "%s: a unit spawned at %s can't walk out" % (tag, start)
            report.append("%s %dx%dx%d, %d blocks: no pockets (%d roof-only cells nothing can reach), the spawn walks "
                          "out%s" % (tag, s.size[0], s.size[1], s.size[2], len(names(s)), len(roof), note))
    return report


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/lab_ms"
    os.makedirs(out, exist_ok=True)
    built = (l1(), l2(), l3())
    paths = [os.path.join(out, f) for f in ("machine_shop.nbt", "machine_shop_l2.nbt", "machine_shop_l3.nbt")]
    for s, path in zip(built, paths):
        s.write(path)
    for line in check_levels(*built, paths=paths):
        print(line)
