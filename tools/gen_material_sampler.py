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
dark oak (round 18: the first sampler hid every trunk under its canopy)."""
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


if __name__ == "__main__":
    main()
