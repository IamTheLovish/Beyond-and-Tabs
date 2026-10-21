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


def main():
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
