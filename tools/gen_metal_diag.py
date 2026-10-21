#!/usr/bin/env python3
"""Round 23 metal split test (diagnostic branch claude/diag-metals - never merged). Carrier blocks (the 16 wools,
which the pack doesn't texture) get a channel-split copy of each metal texture:
    A = the real block (our full texture)       B = albedo only (flat _n, neutral dielectric _s)
    C = normals/height only (grey albedo, neutral _s)   D = _s only (grey albedo, flat _n)
and a neutral grey reference block (grey albedo, flat _n, neutral _s). Run after gen_blocks.py (the diag branch's
gen_blocks.build calls write_variants); design/eyes/metal_diag.nbt lays them out: one row per metal, A B C D, the grey
reference at the end of the front row."""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_structures import Structure  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "resourcepacks", "BeyondAndTabs-Realistic")
BLOCK = os.path.join(PACK, "assets", "minecraft", "textures", "block")
MODEL = os.path.join(PACK, "assets", "minecraft", "models", "block")
METALS = ["iron_block", "gold_block", "cut_copper", "netherite_block", "raw_iron_block"]
WOOLS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
         "blue", "brown", "green", "red", "black"]
GREY = 0.50          # the reference albedo (sRGB value ~ 128+: mid grey)
NEUTRAL_SMOOTH = 60  # the neutral _s: a matte dielectric


def carriers():
    """(carrier block, metal, variant) for every panel; the last wool is the grey reference."""
    out = []
    i = 0
    for m in METALS:
        for v in "BCD":
            out.append((WOOLS[i] + "_wool", m, v))
            i += 1
    out.append((WOOLS[i] + "_wool", None, "ref"))
    return out


def write_variants():
    for carrier, metal, v in carriers():
        if metal:
            alb = np.asarray(Image.open(os.path.join(BLOCK, metal + ".png")).convert("RGBA"))
            n = np.asarray(Image.open(os.path.join(BLOCK, metal + "_n.png")).convert("RGBA"))
            s = np.asarray(Image.open(os.path.join(BLOCK, metal + "_s.png")).convert("RGBA"))
        else:
            alb = np.zeros((128, 128, 4), np.uint8)
            n = s = alb
        h, w = alb.shape[:2]
        grey = np.zeros((h, w, 4), np.uint8)
        grey[..., :3] = int(round(GREY ** (1 / 2.2) * 255))
        grey[..., 3] = 255
        flat_n = np.zeros((h, w, 4), np.uint8)
        flat_n[..., 0] = flat_n[..., 1] = 128
        flat_n[..., 2] = flat_n[..., 3] = 255
        neutral_s = np.zeros((s.shape[0], s.shape[1], 4), np.uint8)
        neutral_s[..., 0], neutral_s[..., 1], neutral_s[..., 2], neutral_s[..., 3] = NEUTRAL_SMOOTH, 10, 0, 255
        if v == "B":
            a, nn, ss = alb, flat_n, neutral_s
        elif v == "C":
            a, nn, ss = grey, n, neutral_s
        elif v == "D":
            a, nn, ss = grey, flat_n, s
        else:
            a, nn, ss = grey, flat_n, neutral_s
        for suffix, img in (("", a), ("_n", nn), ("_s", ss)):
            Image.fromarray(img, "RGBA").save(os.path.join(BLOCK, carrier + suffix + ".png"))
        with open(os.path.join(MODEL, carrier + ".json"), "w") as f:
            json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "minecraft:block/" + carrier}}, f)


def write_structure():
    cols, rows = 5, len(METALS)
    s = Structure(cols * 3 + 1, rows + 3, rows * 3 + 1)
    s.fill(0, 0, 0, cols * 3, 0, rows * 3, "grass_block", snowy="false")
    panels = {}
    for carrier, metal, v in carriers():
        if metal:
            panels[(METALS.index(metal), "ABCD".index(v))] = carrier
        else:
            panels[(0, 4)] = carrier
    for r, m in enumerate(METALS):
        panels[(r, 0)] = m
    for (r, c), name in panels.items():
        x0, z0, y0 = 1 + c * 3, (rows - 1 - r) * 3 + 1, 1 + r
        for y in range(1, y0):
            for dx in (0, 1):
                for dz in (0, 1):
                    s.set(x0 + dx, y, z0 + dz, "stone_bricks")
        for dx in (0, 1):
            for dz in (0, 1):
                for dy in (0, 1):
                    s.set(x0 + dx, y0 + dy, z0 + dz, name)
    out = os.path.join(ROOT, "design", "eyes", "metal_diag.nbt")
    s.write(out)
    return out


if __name__ == "__main__":
    write_variants()
    print(write_structure())
