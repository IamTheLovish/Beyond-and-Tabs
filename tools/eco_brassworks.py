#!/usr/bin/env python3
"""The Brassworks economy set, redrawn as one family - THE ENGINE HOUSE (wind_generator_brass), THE CRUCIBLE SMELTER
(energy_converter_brass) and THE STRONGROOM (storage_vault_brass) - so a row of them reads as the yard of the Grand
Foundry (the capitol, tools/capitol_foundry_hall.py), beside the Iron Golem Drill (the extractor, tools/mex_brass.py)
and the Machine Shop (the T1 lab, tools/lab_machine_shop.py).

lovish, Oct 19 2026: "I want the buildings to look like they match each other so when bases get bigger they look
cohesive and beautiful". Players build these three in rows, so they share one skeleton and differ in one big idea each:
  - the GENERATOR is the tall one: a steam engine house with the faction's smokestack and the drawn FLYWHEEL turning on
    its west face (energy = the machine that turns);
  - the CONVERTER is a furnace: two glowing furnace mouths at its foot and a crucible of molten metal glowing in its
    roof (energy in, metal out);
  - the VAULT is a safe: a round vault door, riveted iron, barrels and a heap of raw copper and iron behind its parapet
    (what is stored shows from the RTS camera).

THE BRASSWORKS STYLE KIT (the capitol's, the extractor's, the lab's - named in capitol_foundry_hall / lab_machine_shop):
  - main blocks: COPPER in its weathering stages (waxed cut / exposed / weathered / oxidized cut copper and their stairs
    and slabs; bright waxed copper blocks for the gear rims), DARK OAK (stripped logs as the timber frame, planks,
    fences) and SOOT (polished blackstone bricks, chiseled polished blackstone, deepslate) as the plinth and the stacks;
  - trim: IRON - iron bars (grates over the furnace mouths), chains, iron blocks (rivets, the vault door);
  - light: LANTERNS on the dark-oak corner posts' copper caps, and GLOWING FURNACE MOUTHS (lit blast furnaces, iron-bar
    grates over shroomlight) - the capitol's furnace bays in small;
  - silhouette motif: SMOKESTACKS AND PIPES - soot stacks banded in exposed copper with a copper cap and a smoking
    campfire (campfires only on stack tops, where no unit walks), copper steam pipes (lightning rods);
  - signature detail: THE GEAR - the capitol's great gear (bright copper rim, glowing ochre-froglight hub) as a badge in
    the south face of every building, at the SAME height on all three (y 3..5) so a row of them carries one band of
    gears; on the vault it is the vault door's wheel (an iron hub);
  - the same skeleton on all three: a soot plinth on a deepslate pad, dark-oak corner posts with copper caps, copper
    walls between, a stair eave at the same height (y 6) - the rhythm a row of them shares.

THE BUILDING STANDARD on 3 x 3 (design/building-revamp-progress.md): three depths per face (the dark-oak posts proud,
the copper wall plane, the grates / gear hub / door recess); a broken top (stacks, pipes, lanterns, the crucible and
the hoard); 3-4 materials per zone with stairs and slabs on every edge; lights at night (furnace mouths, gear hubs,
the crucible, lanterns). The RTS camera looks from the south-west: it sees the tops, the west face (x = 0) and the south
face (z = 2), so the show is on those.

GAME RULES (asserted in check()):
  - footprint exactly 3 x 3; the generator under 16 high, the converter and vault under 10; under ~150 blocks each;
  - nothing pops off (gen_structures._SunRig.check_support: lanterns stand on copper caps, campfires on copper);
  - no grass, water, fluids, fire, gravity blocks or redstone where units walk; campfires only on stack tops;
  - no pockets (tools/audit_structure_pockets.audit); every edge cell of layers 1-2 is a full block, so nothing on the
    building's rim invites a unit onto it or blocks a builder standing beside any side (the bot soak once saw
    "energy_converter_brass: no build progress for 45 s ... builders in range 0 of 7");
  - the three classes set no startingBlockTypes (nothing pinned in layer 0); layer 0 is a full pad anyway;
  - the flywheel (WindGenerator.MOTIONS "_brass" WHEEL): a horizontal polished-basalt axle on the x axis in the west
    face with open air west of it, a radius that clears the ground and keeps the wheel inside the footprint's z span
    (rows packed side by side must not cross wheels); checked here with tools/check_sail_hubs.check_motion's rules.

Usage: python3 tools/eco_brassworks.py [OUT_DIR]   (writes wind_generator_brass.nbt, energy_converter_brass.nbt,
       storage_vault_brass.nbt into OUT_DIR - default the current folder - and runs the checks)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

S = 3
# the kit's blocks, by role (the capitol's and the lab's names)
CU, CU_E, CU_W, CU_O = ("waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper",
                        "waxed_oxidized_cut_copper")
CUB = "waxed_copper_block"           # bright brass: the gear rims
DOL, DO = "stripped_dark_oak_log", "dark_oak_planks"
SB, SBS, SBW = "polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_wall"
CPB = "chiseled_polished_blackstone"
DT, DB = "deepslate_tiles", "deepslate_bricks"
HUB = "ochre_froglight"
LANTERN = dict(hanging="false", waterlogged="false")
CAMPFIRE = dict(lit="true", facing="south", signal_fire="false", waterlogged="false")
ROD = dict(powered="false", waterlogged="false")
EAVE = 6                             # the stair eave course, the same on all three (the row's shared line)
GEAR_Y = 4                           # the gear badge's hub height, the same on all three

# The flywheel: WindGenerator.MOTIONS "_brass" (was BlockPos(0, 2, 1), WEST, 1.1f)
WHEEL_AXLE, WHEEL_OUT, WHEEL_R = (0, 3, 1), "WEST", 1.35

FORBIDDEN = ("water", "lava", "fire", "sand", "gravel", "concrete_powder", "anvil", "redstone", "grass_block",
             "mycelium", "_door", "_leaves", "piston", "lamp", "magma_block", "torch", "candle", "pressure_plate")


def st(c):
    return c + "_stairs"


def sl(c):
    return c + "_slab"


class Rig(gs._SunRig):
    """gen_structures' rig canvas (settle() shapes stairs and joins walls / bars like the game) on a 3 x 3."""

    def __init__(self, sy):
        super().__init__(S, sy, S)

    def log(self, x, y, z):
        self.set(x, y, z, DOL, axis="y")

    def lantern(self, x, y, z):
        self.set(x, y, z, "lantern", **LANTERN)

    def rod(self, x, y, z, facing="up"):
        self.set(x, y, z, "lightning_rod", facing=facing, **ROD)

    def slab(self, x, y, z, name=sl(CU), typ="bottom"):
        self.set(x, y, z, name, type=typ, waterlogged="false")

    def bars(self, x, y, z):
        self.set(x, y, z, "iron_bars")      # settle() joins it


# ---------------------------------------------------------------------------------------------- the shared skeleton
def pad(r):
    """Layer 0: a soot pad - chiseled corners (the corner posts' footings), a deepslate-brick kerb, deepslate tiles
    inside: the yard floor of the capitol and the lab."""
    for x in range(S):
        for z in range(S):
            corner = x in (0, 2) and z in (0, 2)
            r.set(x, 0, z, CPB if corner else DB if (x in (0, 2) or z in (0, 2)) else DT)


def plinth(r):
    """Layer 1: the soot plinth, a full ring of full blocks (a builder can stand against every side, nothing on the
    rim a unit can step onto) round a solid core."""
    for x in range(S):
        for z in range(S):
            r.set(x, 1, z, SB)


def posts(r, y1, y2, skip=()):
    """The dark-oak corner posts (the kit's timber frame, the pier plane) from y1 to y2."""
    for (x, z) in gs.CORNERS3:
        for y in range(y1, y2 + 1):
            if (x, y, z) not in skip:
                r.log(x, y, z)


def gear(r, hub=HUB, rim=CUB):
    """THE GEAR in the south face (z = 2), hub at GEAR_Y: a bright copper rim round a glowing hub - top and bottom in
    the face's middle column, the two sides in the corner posts (the posts break for it), so the badge is the full face
    width and reads round at 32 px with the posts' dark corners cut out of it. The hub stays flush and glowing: tried
    one block back behind iron-bar spokes (the lab's gear), it went dark at RTS distance and the badge stopped reading."""
    r.set(1, GEAR_Y - 1, 2, rim)
    r.set(1, GEAR_Y + 1, 2, rim)
    r.set(0, GEAR_Y, 2, rim)
    r.set(2, GEAR_Y, 2, rim)
    props = {"axis": "z"} if hub.endswith("froglight") else {}
    r.set(1, GEAR_Y, 2, hub, **props)


def eave(r, name=st(CU_W)):
    """The eave at EAVE: stairs on the four edge cells climbing inward, copper caps on the posts (each carrying what
    the building puts there), the core solid."""
    for (x, z, f) in ((1, 0, "south"), (1, 2, "north"), (0, 1, "east"), (2, 1, "west")):
        r.stair(x, EAVE, z, f, name=name)
    for (x, z) in gs.CORNERS3:
        r.set(x, EAVE, z, CU)


def stack(r, x, z, y1, y2):
    """A soot smokestack from y1 to y2 (inclusive), banded in exposed copper every third course, a copper cap at y2 and
    the kit's smoke (a lit campfire) on it."""
    for y in range(y1, y2):
        r.set(x, y, z, CU_E if y % 3 == 0 else SB)
    r.set(x, y2, z, CU)
    r.set(x, y2 + 1, z, "campfire", **CAMPFIRE)


# --------------------------------------------------------------------------------------------- THE ENGINE HOUSE
def walls(r, main=CU_W, band=CU_E):
    """The wall plane between the posts, y 2..EAVE-1: the main block with a band course at the foot and under the eave
    (the kit's riveted hoops), the core solid."""
    for y in range(2, EAVE):
        for (x, z) in gs.EDGES3 + [(1, 1)]:
            r.set(x, y, z, band if y in (2, EAVE - 1) else main)


def _wind(r):
    """The generator: a steam engine house. A soot plinth with the firebox mouth under an iron grate in the south face,
    a weathered-copper engine block between dark-oak posts, the gear in the south face, the flywheel's basalt axle in
    the west face (the drawn wheel turns outside it), a bright copper pressure dome on the roof with steam pipes and
    lanterns on the post caps, and the tall banded smokestack on the back corner - the row's tallest silhouette, so the
    energy buildings read from across the base."""
    pad(r)
    plinth(r)
    r.set(1, 1, 2, "blast_furnace", facing="south", lit="true")      # the firebox mouth
    posts(r, 2, EAVE - 1, skip={(0, GEAR_Y, 2), (2, GEAR_Y, 2)})
    walls(r)
    r.bars(1, 2, 2)                                                  # the grate over the fire
    r.set(1, 2, 1, "shroomlight")                                    # the fire's glow behind it
    gear(r)
    # the west face, behind the flywheel: the axle in verdigris bearing plates (the drawn wheel's iron spokes show
    # against the green)
    r.set(*WHEEL_AXLE, "polished_basalt", axis="x")
    r.set(0, 2, 1, CU_O)
    r.set(0, 4, 1, CU_O)
    eave(r)
    r.set(1, EAVE, 1, CUB)                                           # the pressure dome
    r.slab(1, EAVE + 1, 1, sl(CU))
    stack(r, 2, 0, EAVE, 13)
    r.lantern(0, EAVE + 1, 2)                                        # the camera corner
    # the steam main: a copper pipe up the front-right cap and across the roof into the stack (an elbow of rods
    # lying north), and a vent pipe on the back-left cap
    for y in (EAVE + 1, EAVE + 2):
        r.rod(2, y, 2)
    r.rod(2, EAVE + 3, 2, "north")
    r.rod(2, EAVE + 3, 1, "north")
    for y in (EAVE + 1, EAVE + 2):
        r.rod(0, y, 0)


# ------------------------------------------------------------------------------------------ THE CRUCIBLE SMELTER
def _converter(r):
    """The converter: a smelter. Two lit furnace mouths in the plinth on the camera's faces (west and south), the
    furnace body between the posts with grates glowing over the fire, the gear in the south face, and in the roof a
    crucible of molten metal (a lava cauldron: a block, never a fluid; it glows and nothing can reach it) inside the
    eave, with a short soot stack on the back corner."""
    pad(r)
    plinth(r)
    r.set(1, 1, 2, "blast_furnace", facing="south", lit="true")
    r.set(0, 1, 1, "blast_furnace", facing="west", lit="true")
    posts(r, 2, EAVE - 1, skip={(0, GEAR_Y, 2), (2, GEAR_Y, 2)})
    walls(r)
    r.bars(1, 2, 2)
    r.set(1, 2, 1, "shroomlight")
    r.bars(0, 3, 1)                                                  # a glowing grate in the west face
    r.set(1, 3, 1, "shroomlight")
    r.stair(0, 4, 1, "east", "top", st(CU_E))                        # its hood (a recess under an overhang)
    gear(r)
    eave(r)
    r.set(1, EAVE, 1, "lava_cauldron")                               # the crucible
    stack(r, 2, 0, EAVE, 8)
    # the ladle chain: a dark-oak arm off the stack's cap reaching over the crucible, a chain hanging into the pour
    r.set(1, 8, 0, "dark_oak_fence")
    r.set(1, 8, 1, "dark_oak_fence")
    r.set(1, 7, 1, "chain", axis="y", waterlogged="false")
    r.lantern(0, EAVE + 1, 2)
    r.rod(2, EAVE + 1, 2)


# ------------------------------------------------------------------------------------------------- THE STRONGROOM
def _vault(r):
    """The vault: a strongroom. A soot plinth, heavy soot walls banded in copper between the posts, the round vault
    door in the south face (the gear, its hub an iron wheel boss) on an iron sill, barrels in the west face, and on the
    roof the hoard inside the eave - raw copper and raw iron and a barrel - with lanterns on the posts."""
    pad(r)
    plinth(r)
    posts(r, 2, EAVE - 1, skip={(0, GEAR_Y, 2), (2, GEAR_Y, 2)})
    walls(r, main=SB)
    gear(r, hub="iron_block")
    r.set(0, 3, 1, "barrel", facing="west", open="false")
    r.set(0, 4, 1, "barrel", facing="west", open="false")
    r.stair(0, 5, 1, "east", "top", st(CU_E))                        # a hood over the barrel bay
    eave(r)
    r.set(1, EAVE, 1, "raw_copper_block")
    r.set(1, EAVE + 1, 1, "barrel", facing="up", open="false")
    r.lantern(0, EAVE + 1, 2)
    r.lantern(2, EAVE + 1, 2)
    r.set(0, EAVE + 1, 0, "iron_block")   # not raw iron: that is the metal-patch marker the quick-build scans for
    # the hoist on the back-right post: a dark-oak mast, an arm over the hoard (the converter's ladle arm again) and
    # a chain lowering the crate onto the heap
    for y in (EAVE + 1, EAVE + 2, EAVE + 3):
        r.log(2, y, 0)
    r.set(1, EAVE + 3, 0, "dark_oak_fence")
    r.set(1, EAVE + 3, 1, "dark_oak_fence")
    r.set(1, EAVE + 2, 1, "chain", axis="y", waterlogged="false")



# --------------------------------------------------------------------------------------------------- build + checks
HEIGHTS = {"wind": 16, "converter": 10, "vault": 10}


def _build(fn, sy, tag):
    r = Rig(sy)
    fn(r)
    r.bury_grass()
    r.settle()
    r.check_support("brassworks " + tag)
    # trim the box to its blocks (the game's box is the structure's size)
    top = max(p[1] for p in r.b)
    r.size = (S, top + 1, S)
    return r.structure()


def wind():
    """The Engine House (wind_generator_brass): a steam engine house with the flywheel and the tall stack."""
    return _build(_wind, HEIGHTS["wind"], "engine house")


def converter():
    """The Crucible Smelter (energy_converter_brass): furnace mouths below, a glowing crucible above."""
    return _build(_converter, HEIGHTS["converter"], "crucible smelter")


def vault():
    """The Strongroom (storage_vault_brass): a vault door, riveted walls, the hoard on the roof."""
    return _build(_vault, HEIGHTS["vault"], "strongroom")


def names(s):
    return {p: s.palette[i][0] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


def check_wheel(s):
    """tools/check_sail_hubs.check_motion's WHEEL rules on this structure, plus: the wheel stays inside the footprint's
    z span (rows of generators packed side by side must not cross wheels)."""
    n = {p: (s.palette[i][0], dict(s.palette[i][1]) if len(s.palette[i]) > 1 else {}) for p, i in s.blocks.items()}
    x, y, z = WHEEL_AXLE
    blk = n.get(WHEEL_AXLE, ("air", {}))
    import check_sail_hubs as cs
    assert blk[0] in cs.HUB_BLOCKS and blk[1].get("axis") == cs.AXIS[WHEEL_OUT], "wheel axle is %s" % (blk,)
    dx, dz = cs.STEP[WHEEL_OUT]
    assert not (0 <= x + dx < S and 0 <= z + dz < S), "the wheel's outside cell is inside the footprint"
    assert WHEEL_R <= y + 0.45, "a %s-block wheel digs into the ground" % WHEEL_R
    assert z + 0.5 - WHEEL_R - 0.1 >= 0 and z + 0.5 + WHEEL_R + 0.1 <= S, "the wheel leaves the footprint's z span"


def check(paths=None):
    """The game's rules on all three (support ran in _build); with nbt paths, the pocket audit too."""
    import audit_structure_pockets as ap
    out = []
    for tag, fn, hmax in (("wind_generator_brass", wind, 16), ("energy_converter_brass", converter, 10),
                          ("storage_vault_brass", vault, 10)):
        s = fn()
        n = names(s)
        assert (s.size[0], s.size[2]) == (3, 3), "%s: footprint %s" % (tag, s.size)
        assert s.size[1] <= hmax, "%s: %d high" % (tag, s.size[1])
        assert len(n) <= 150, "%s: %d blocks" % (tag, len(n))
        assert all((x, 0, z) in n for x in range(3) for z in range(3)), "%s: a hole in the pad" % tag
        bad = sorted({nm for nm in n.values() if any(f in nm for f in FORBIDDEN) and nm not in ("lava_cauldron", "campfire")})
        assert not bad, "%s: forbidden blocks %s" % (tag, bad)
        # the rim a builder stands against: layers 1-2 edge cells are full blocks (bars count as a wall: the grate)
        for x in range(3):
            for z in range(3):
                if x in (0, 2) or z in (0, 2):
                    for y in (1, 2):
                        nm = n.get((x, y, z), "air")
                        assert nm != "air" and not nm.endswith(("_slab", "_stairs", "_wall", "_fence", "lantern",
                                                                "rod", "chain")), "%s: rim cell %s is %s" % (tag, (x, y, z), nm)
        for (x, y, z), nm in n.items():
            if nm == "campfire":
                assert y >= 8 and n.get((x, y - 1, z)) == CU, "%s: a campfire at %s is not on a stack top" % (tag, (x, y, z))
            if nm == "lava_cauldron":
                assert y >= EAVE and all(n.get((x + dx, y, z + dz)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))), \
                    "%s: the crucible is not walled in its roof" % tag
        if tag.startswith("wind"):
            check_wheel(s)
        line = "%s %dx%dx%d, %d blocks" % (tag, s.size[0], s.size[1], s.size[2], len(n))
        if paths:
            one_way, ground, roof, rooms, note = ap.audit(tag, paths[tag])
            assert not one_way and not ground and not rooms and "TRAPPED" not in note, "%s: pockets %s %s %s %s" % (
                tag, one_way[:6], ground[:6], rooms[:4], note)
            line += ", no pockets%s" % note
        out.append(line)
    return out


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    paths = {}
    for tag, fn in (("wind_generator_brass", wind), ("energy_converter_brass", converter),
                    ("storage_vault_brass", vault)):
        paths[tag] = os.path.join(out, tag + ".nbt")
        fn().write(paths[tag])
    for line in check(paths):
        print(line)
