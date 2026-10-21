"""Brassworks metal extractor: THE IRON GOLEM DRILL (lovish, Oct 19 2026: "the current metal extractor building looks
really ugly" - replace the squat soot gantry with a characterful sculpture-building).

The story: the Brassworks are dwarf engineers who build machines and crew them, so their mine is a machine-man - a
squat riveted brass automaton standing over the patch, both fists on the T-bar of a screw drill that bores into the
ore (a raw-iron block in the pad), its furnace heart glowing in its belly and a soot boiler strapped to its back like a
pack, venting through a smokestack. Inspiration (never traced): the steampunk-golem / "gothicpunk pipeworks" idea in the
brief, the Great Forge's smokestacks-with-campfires and furnace mouths, and the Sunforged Angel Fountain's lesson that a
figure on a 5 x 5 patch reads by a strong silhouette plus a contrasting glowing face.

THE BUILDING STANDARD (design/building-revamp-progress.md), and how this meets it on 5 x 5:
1. Three depths on each camera face: the arms and fists stand proud on the south (z = 4) plane, the belly plates and
   pauldrons a step back, the chest/torso and the boiler pack recessed behind; on the west, arm -> torso -> boiler.
2. Broken top: the stack (and at T2 a second, taller one), the helm's antenna rod, the shoulder slabs.
3. Mixed materials per zone: brass = waxed cut copper (body), waxed exposed cut copper (worn plates), waxed oxidized cut
   copper (verdigris belly plates and drill collar); polished blackstone bricks / basalt / deepslate for the iron frame;
   stairs and slabs on every edge; chains (the T-bar, the neck joints), iron bars, walls, a lightning rod as thin members.
4. Brassworks style: brass, soot black, furnace orange (brassworks_plan.md).
5. Dressing: a lantern post, a barrel of tools, a raw-iron ore chunk with a lifting chain.
6. T2: a second taller stack, a cog crown on the helm (grindstones), lanterns on the shoulders, a copper pressure dome
   on the boiler and a glowing firebox window at the boiler's west end.
7. Lights: the blast-furnace belly and the shroomlight eyes behind a bars visor are the warm points at night.

Grindstones stand for gears (a grindstone survives with nothing under it in vanilla). The tier-2 marker is the
waxed_copper_block helm crown and pressure dome; tier 1 holds no copper_block / cut_copper / waxed_copper_block.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

BRASS, BRASS_ST, BRASS_SL, BRASS_D = gs.BRASS, gs.BRASS_ST, gs.BRASS_SL, gs.BRASS_D
SOOT, SOOT_B = gs.SOOT, gs.SOOT_B
SOOT_ST = "polished_blackstone_brick_stairs"
VERD = "waxed_oxidized_cut_copper"     # verdigris: a third copper tone so the brass body isn't one flat orange
LANTERN, CAMPFIRE = gs.LANTERN, gs.CAMPFIRE
ROD_DN = dict(facing="down", powered="false", waterlogged="false")
ROD_UP = dict(facing="up", powered="false", waterlogged="false")
H = 14


def _t1(r):
    # the pad: chiseled soot corners, a brick kerb, deepslate tiles, and the ore the drill bites (raw iron) with a
    # cracked slab beside it where the ground has split
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            r.set(x, 0, z, "chiseled_polished_blackstone" if corner else SOOT_B if edge else "deepslate_tiles")
    r.set(2, 0, 4, "raw_iron_block")
    r.set(2, 0, 3, "cracked_polished_blackstone_bricks")
    # legs: basalt pillars, soot boots with toes
    for x in (1, 3):
        r.set(x, 1, 2, SOOT_B)
        r.stair(x, 1, 3, "north", name=SOOT_ST)
        r.set(x, 2, 2, "polished_basalt", axis="y")
    # hips: a soot girdle (dark between brass chest and basalt legs, so the figure reads in three parts)
    for x in (1, 2, 3):
        r.set(x, 3, 2, SOOT_B)
        r.set(x, 3, 1, SOOT)
    # torso: a 3 x 2 brass barrel chest; the front (z = 3) is stepped - top-slab hip plates, the lit blast furnace as
    # the belly heart (the faction's furnace orange, facing the camera), verdigris plates, sloped stair shoulders
    for x in (1, 2, 3):
        for y in (4, 5, 6):
            r.set(x, y, 1, BRASS)
            r.set(x, y, 2, BRASS)
    r.set(1, 4, 3, BRASS_SL, type="top", waterlogged="false")
    r.set(3, 4, 3, BRASS_SL, type="top", waterlogged="false")
    r.set(2, 4, 3, "blast_furnace", facing="south", lit="true")   # the belly furnace
    for x in (1, 2, 3):
        r.set(x, 5, 3, VERD if x != 2 else BRASS)
    r.stair(1, 6, 3, "north", name=BRASS_ST)
    r.stair(3, 6, 3, "north", name=BRASS_ST)
    r.set(2, 6, 3, BRASS_D)
    # pauldrons on the arm tops, and the head: a brass block with a glowing shroomlight face behind an iron-bars
    # visor, a slab brow and crown, chain neck-joints to the pauldrons and an antenna rod
    for x in (0, 4):
        r.set(x, 6, 2, BRASS_D)
        r.set(x, 7, 2, BRASS_SL, type="bottom", waterlogged="false")
    r.set(2, 7, 2, BRASS)
    r.set(2, 7, 3, "shroomlight")
    r.set(2, 7, 4, "iron_bars")
    r.set(2, 8, 2, BRASS_SL, type="bottom", waterlogged="false")
    r.set(2, 8, 3, BRASS_SL, type="bottom", waterlogged="false")
    r.set(1, 7, 2, "chain", axis="x", waterlogged="false")
    r.set(3, 7, 2, "chain", axis="x", waterlogged="false")
    r.set(2, 9, 2, "lightning_rod", **ROD_UP)
    # arms: basalt upper arms, forearms (basalt on its side) reaching forward, brass fists gripping the T-bar
    for x in (0, 4):
        r.set(x, 5, 2, "polished_basalt", axis="y")
        r.set(x, 4, 2, SOOT_B)
        r.set(x, 4, 3, "polished_basalt", axis="z")
        r.set(x, 4, 4, BRASS)
    r.set(1, 4, 4, "chain", axis="x", waterlogged="false")
    r.set(3, 4, 4, "chain", axis="x", waterlogged="false")
    # the drill: a verdigris collar on the T-bar, a grindstone gear, a chain shaft and the lightning-rod screw bit
    # point-down into the raw iron
    r.set(2, 4, 4, VERD)
    r.set(2, 3, 4, "grindstone", face="floor", facing="north")
    r.set(2, 2, 4, "chain", axis="y", waterlogged="false")
    r.set(2, 1, 4, "lightning_rod", **ROD_DN)
    # the boiler pack on the golem's back (north, z = 0): dark so the brass figure stands out against it, banded with
    # worn brass; one stack of soot bricks with a brass cap and a lit campfire for smoke
    for x in (1, 2, 3):
        r.set(x, 1, 0, SOOT)
        r.set(x, 2, 0, SOOT_B)
        r.set(x, 3, 0, BRASS_D)
        r.set(x, 4, 0, SOOT_B)
        r.set(x, 5, 0, BRASS_D)
    r.stair(1, 6, 0, "east", name=SOOT_ST)
    r.set(2, 6, 0, SOOT_B)
    for y in (6, 7, 8, 9):
        r.set(3, y, 0, "polished_blackstone_wall" if y == 8 else SOOT_B)
    r.set(3, 10, 0, BRASS)
    r.set(3, 11, 0, "campfire", **CAMPFIRE)
    # dressing round the base
    r.set(0, 1, 4, "polished_blackstone_wall")
    r.set(0, 2, 4, "lantern", **LANTERN)
    r.set(4, 1, 4, "raw_iron_block")
    r.set(4, 2, 4, "chain", axis="y", waterlogged="false")
    r.set(0, 1, 0, "barrel", facing="up", open="false")


def _t2(r):
    """T2 over T1 (only adds or replaces): copper crown and cog, second taller stack, shoulder lanterns, a lit
    firebox window and a pressure dome on the boiler."""
    _t1(r)
    # the helm crown turns to waxed copper (the tier marker) with a cog and the antenna lifted onto it
    r.set(2, 8, 2, "waxed_copper_block")
    r.set(2, 9, 2, "grindstone", face="floor", facing="west")
    r.set(2, 10, 2, "lightning_rod", **ROD_UP)
    # the second smokestack, two blocks taller than the first, so the pair reads as a stepped skyline
    for y in (6, 7, 8, 9, 10, 11):
        r.set(1, y, 0, "polished_blackstone_wall" if y in (8, 10) else SOOT_B)
    r.set(1, 12, 0, BRASS)
    r.set(1, 13, 0, "campfire", **CAMPFIRE)
    # the shoulder slabs become brass blocks carrying lanterns
    for x in (0, 4):
        r.set(x, 7, 2, BRASS)
        r.set(x, 8, 2, "lantern", **LANTERN)
    # the boiler's end glows: a furnace window and a grilled firebox on the camera's west side
    r.set(1, 3, 0, "blast_furnace", facing="west", lit="true")
    r.set(1, 4, 0, "shroomlight")
    r.set(0, 3, 0, "iron_bars")              # the grate in front of the furnace mouth, so its glow shows
    r.set(0, 2, 0, "polished_blackstone_wall")
    r.set(0, 4, 0, SOOT_ST, facing="east", half="top", shape="straight", waterlogged="false")
    # the gear crown round the head: grindstone cogs on the cheeks
    r.set(1, 8, 2, "grindstone", face="floor", facing="north")
    r.set(3, 8, 2, "grindstone", face="floor", facing="north")
    # a pressure dome on the boiler
    r.set(2, 6, 0, "waxed_copper_block")
    r.set(2, 7, 0, BRASS_SL, type="bottom", waterlogged="false")


def _build(fn):
    """The rig canvas from gen_structures (settles stairs / walls / bars into their joined shapes)."""
    r = gs._SunRig(5, H, 5)
    fn(r)
    r.settle()
    return r


def t1():
    """T1 'Iron Golem Drill': a brass automaton boring into the patch, a boiler pack and one smokestack. 5 x 14 x 5."""
    r = _build(_t1)
    r.check_support("brass T1")
    return r.structure()


def t2():
    """T2 'Great Golem Drill': the T1 with the copper crown and cog, twin stacks, shoulder lanterns and a lit firebox.
    Every T1 block stays a block (check_mex_tiers). 5 x 14 x 5."""
    r = _build(_t2)
    r.check_support("brass T2")
    s = r.structure()
    gs.check_mex_tiers(t1(), s, "brassworks")
    return s


if __name__ == "__main__":
    # python3 tools/mex_brass.py OUT_DIR -> OUT_DIR/mex_brass_t1.nbt, mex_brass_t2.nbt, prints the block counts
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for name, fn in (("t1", t1), ("t2", t2)):
        s = fn()
        s.write(os.path.join(out, "mex_brass_%s.nbt" % name))
        print(name, sum(1 for i in s.blocks.values() if s.palette[i][0] != "air"))
