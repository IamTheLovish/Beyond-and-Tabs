#!/usr/bin/env python3
"""A material sampler for the eyes `structure` lane (Lane B, Oct 20 2026): the block pack's terrain and tree
materials side by side, so a structure-photo run shows them in the real game with shaders - the tour's flat map shows
no granite, no bark and only distant trees. Not a game structure: it lives in design/eyes/, not in the jar.

    python3 tools/gen_material_sampler.py          # writes design/eyes/material_sampler.nbt
    gh api repos/IamTheLovish/beyond-and-tabs/actions/workflows/eyes.yml/dispatches -X POST -f ref=main \\
        -f 'inputs[structure]=design/eyes/material_sampler.nbt' -f 'inputs[shaders]=true'

Layout (17 x 10 x 13): a back wall of stone, granite, diorite, andesite and tuff panels (3 wide, 4 tall, side by
side), a grass floor, three trees - oak, spruce and birch, trunks bare to 4 blocks so the bark shows, leaves
persistent (no decay) - and a front row of bare 3-block posts of every bark: oak, spruce, birch, jungle, acacia,
dark oak (round 18: the first sampler hid every trunk under its canopy).

Also writes design/eyes/block_sampler_1..3.nbt: every building-block texture of the pack (BUILDING_BLOCKS below):
    -f 'inputs[structure]=design/eyes/block_sampler_1.nbt,design/eyes/block_sampler_2.nbt,design/eyes/block_sampler_3.nbt'"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_structures import Structure  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "design", "eyes", "material_sampler.nbt")


def tree(s, x, z, log, leaves, height, radius):
    for y in range(1, height + 1):
        s.set(x, y, z, log, axis="y")
    top = height
    for dy in range(-2, 2):
        r = radius - (1 if dy >= 1 else 0)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dz * dz > r * r + 1 or (dx == 0 and dz == 0 and dy < 1):
                    continue
                y = top + dy
                if 1 <= y < s.size[1]:
                    s.set(x + dx, y, z + dz, leaves, persistent="true", distance="1", waterlogged="false")


# Building-block samplers (Lane B, Oct 21 2026): every texture in tools/gen_blocks.py that isn't terrain, a tree or a
# cut-out block, on a real full-cube block that shows it (picked from the vanilla 1.20.x blockstates and models: the
# block whose model is a cube with only our textures), plus the few non-cube blocks for the rest. 2x2x2 cubes in rows
# of 7, every row a block higher and further from the camera (the eyes shots look from the +z side), so a still shows
# each block's top and its front. Three structures, one eyes `structure` run photographs all three.
BUILDING_BLOCKS = [
    "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "smooth_stone",
    "polished_andesite", "polished_granite", "polished_diorite", "cobblestone", "quartz_block", "smooth_quartz",
    "quartz_bricks", "quartz_pillar", "calcite", "sandstone", "white_concrete", "bone_block", "snow_block",
    "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
    "mangrove_planks", "crimson_planks", "warped_planks", "hay_block", "barrel", "terracotta", "red_terracotta",
    "blue_terracotta", "clay", "mud", "deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks",
    "cracked_deepslate_bricks", "deepslate_tiles", "blackstone", "polished_blackstone", "polished_blackstone_bricks",
    "cracked_polished_blackstone_bricks", "chiseled_polished_blackstone", "basalt", "polished_basalt", "smooth_basalt",
    "obsidian", "netherrack", "nether_bricks", "red_nether_bricks", "chiseled_nether_bricks", "soul_sand",
    "prismarine_bricks", "dark_prismarine", "moss_block", "end_stone", "cut_copper", "oxidized_cut_copper",
    "raw_iron_block", "iron_block", "gold_block", "netherite_block", "diamond_block", "emerald_block", "lapis_block",
    "podzol", "coarse_dirt", "red_sand", "dirt", "sand", "gravel",
    ("smooth_stone_slab", {"type": "double", "waterlogged": "false"}), "dirt_path", "reinforced_deepslate",
    ("farmland", {"moisture": "0"}), ("farmland", {"moisture": "7"}),
]
PER_ROW, ROWS = 7, 4


def building_sampler(blocks):
    rows = (len(blocks) + PER_ROW - 1) // PER_ROW
    s = Structure(PER_ROW * 3 + 1, rows + 3, rows * 3 + 1)
    s.fill(0, 0, 0, PER_ROW * 3, 0, rows * 3, "grass_block", snowy="false")
    for k, b in enumerate(blocks):
        name, props = (b if isinstance(b, tuple) else (b, {}))
        if name in ("barrel",):
            props = {"facing": "up", "open": "false"}
        if name in ("basalt", "polished_basalt", "quartz_pillar", "bone_block", "hay_block"):
            props = {"axis": "y"}
        r, c = divmod(k, PER_ROW)
        x0, z0 = 1 + c * 3, (rows - 1 - r) * 3 + 1      # row 0 nearest the camera (+z), lowest
        y0 = 1 + r
        for y in range(1, y0):                          # the step under a raised row
            for dx in (0, 1):
                for dz in (0, 1):
                    s.set(x0 + dx, y, z0 + dz, "stone_bricks")
        for dx in (0, 1):
            for dz in (0, 1):
                for dy in (0, 1):
                    s.set(x0 + dx, y0 + dy, z0 + dz, name, **props)
    return s


# Terrain sampler (Lane B round 24, Oct 22 2026 - lovish: "the game's graphics still look flat overall"): the blocks a
# player sees most, each also photographed UP CLOSE (3.5 blocks; EyesDirector reads terrain_sampler.nbt.near and shoots
# 15_terrain_sampler_near_<name>). Two-layer floor (dirt under grass) so the pond is sunk; in front (+z, the camera's
# side) 3x3 floor patches, a pond with a sand and a grass edge, an oak with a bare trunk and a stepped grass hillside;
# behind them a row of 3x3 wall panels of the building stones, planks and cobble.
TERRAIN_FLOOR = ("dirt", "sand", "gravel", "stone", "cobblestone", "grass_block")
TERRAIN_WALLS = ("stone_bricks", "deepslate", "quartz_block", "mossy_stone_bricks", "sandstone", "basalt",
                 "cobblestone", "oak_planks", "stone")


def terrain_sampler():
    s = Structure(44, 12, 12)
    near = []
    s.fill(0, 0, 0, 43, 0, 11, "dirt")
    s.fill(0, 1, 0, 43, 1, 11, "grass_block", snowy="false")
    for i, name in enumerate(TERRAIN_FLOOR):
        x0 = 1 + i * 4
        s.fill(x0, 1, 7, x0 + 2, 1, 9, name, **({"snowy": "false"} if name == "grass_block" else {}))
        near.append(("floor_" + name, x0 + 1.5, 2.0, 8.5))
    # the pond: sand bottom, sand rim on the left, grass on the right
    s.fill(25, 0, 6, 29, 0, 9, "sand")
    s.fill(25, 1, 6, 29, 1, 9, "water", level="0")
    s.fill(24, 1, 6, 24, 1, 10, "sand")
    s.fill(25, 1, 10, 26, 1, 10, "sand")
    near.append(("water_edge", 25.0, 1.9, 9.0))
    # an oak: bare trunk to 6 blocks, a round crown
    for y in range(2, 10):
        s.set(33, y, 8, "oak_log", axis="y")
    for dy in range(-2, 2):
        y = 9 + dy
        r = 2 if dy < 1 else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dz * dz > r * r + 1 or (dx == 0 and dz == 0):
                    continue
                s.set(33 + dx, y, 8 + dz, "oak_leaves", persistent="true", distance="1", waterlogged="false")
    s.set(33, 10, 8, "oak_leaves", persistent="true", distance="1", waterlogged="false")
    near.append(("oak_log", 33.5, 4.0, 9.0))
    near.append(("oak_leaves", 32.5, 8.5, 10.5))
    # the hillside: three grass steps facing the camera
    for x in range(37, 44):
        for top, zmax in ((2, 9), (3, 7), (4, 5)):
            for z in range(0, zmax + 1):
                s.set(x, top - 1, z, "dirt")
                s.set(x, top, z, "grass_block", snowy="false")
    near.append(("hillside", 39.5, 2.8, 9.0))
    # the wall panels (front face at z = 3)
    for i, name in enumerate(TERRAIN_WALLS):
        x0 = i * 4
        s.fill(x0, 2, 1, x0 + 2, 4, 2, name, **({"axis": "y"} if name == "basalt" else {}))
        near.append(("wall_" + name, x0 + 1.5, 3.5, 3.0))
    return s, near


def main():
    ts, near = terrain_sampler()
    tpath = os.path.join(os.path.dirname(OUT), "terrain_sampler.nbt")
    ts.write(tpath)
    with open(tpath + ".near", "w") as f:
        f.write("# close-up points for EyesDirector (name x y z, structure-local) - tools/gen_material_sampler.py\n")
        for name, x, y, z in near:
            f.write("%s %.2f %.2f %.2f\n" % (name, x, y, z))
    s = Structure(17, 10, 13)
    s.fill(0, 0, 0, 16, 0, 12, "grass_block", snowy="false")
    for i, name in enumerate(("stone", "granite", "diorite", "andesite", "tuff")):
        x0 = 1 + i * 3
        s.fill(x0, 1, 11, x0 + 2, 4, 12, name)
    tree(s, 3, 6, "oak_log", "oak_leaves", 7, 3)
    tree(s, 8, 6, "spruce_log", "spruce_leaves", 7, 2)
    tree(s, 13, 6, "birch_log", "birch_leaves", 7, 2)
    for i, wood in enumerate(("oak", "spruce", "birch", "jungle", "acacia", "dark_oak")):
        for y in (1, 2, 3):
            s.set(1 + i * 3, y, 1, wood + "_log", axis="y")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    s.write(OUT)
    chunk = PER_ROW * ROWS
    for i in range(0, len(BUILDING_BLOCKS), chunk):
        building_sampler(BUILDING_BLOCKS[i:i + chunk]).write(
            os.path.join(os.path.dirname(OUT), "block_sampler_%d.nbt" % (i // chunk + 1)))


if __name__ == "__main__":
    main()
