#!/usr/bin/env python3
"""A meadow sampler for the eyes `structure` lane (Itachi 1, Oct 28 2026): the grass look as a player sees it - the
grass blocks (and their tint, GrassTint), the grass plants, a worn path, a few map trees, a bush and a boulder - on
one 48 x 48 patch. The tour's flat map is bare grass with no plants (eyes stills 02 / 04), so neither the plant
sprites nor the tint's patches can be judged there.

    python3 tools/gen_meadow_sampler.py     # writes design/eyes/meadow_sampler.nbt
    gh api -X POST repos/IamTheLovish/Beyond-and-Tabs/actions/workflows/eyes.yml/dispatches -f ref=<branch> \\
        -f 'inputs[structure]=design/eyes/meadow_sampler.nbt' -f 'inputs[shaders]=true'

Plant cover follows maps/MapBuilder's plant cover (two-octave meadow noise; short grass mostly, tall grass and ferns
only in the densest middle, flowers in their own drifts) at the density the denser-plant-cover change aims for, so
the stills show what a real map's meadow will look like. Deterministic (fixed seeds)."""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_trees  # noqa: E402
from gen_structures import Structure  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "design", "eyes", "meadow_sampler.nbt")
N, HGT = 48, 18


def hash01(x, z, salt):
    h = (x * 374761393 + z * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def vnoise(x, z, scale, salt):
    fx, fz = x / scale, z / scale
    x0, z0 = math.floor(fx), math.floor(fz)
    tx, tz = fx - x0, fz - z0
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a, b = hash01(x0, z0, salt), hash01(x0 + 1, z0, salt)
    c, d = hash01(x0, z0 + 1, salt), hash01(x0 + 1, z0 + 1, salt)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def plant_at(x, z):
    """An index into the plant list, or None: MapBuilder.plantAt's rule with a VERDANT_VALLEY-like mix."""
    meadow = 0.65 * vnoise(x, z, 11.0, 1) + 0.35 * vnoise(x, z, 4.0, 2)
    dense = max(0.0, min(1.0, (meadow - 0.22) / 0.45))
    pick = hash01(x, z, 3)
    if pick < 0.75 * dense * (2 - dense) + 0.10:                  # + a sparse scatter outside the meadows
        h = hash01(x, z, 4)
        if meadow > 0.62 and h < 0.10:
            return "large_fern" if h < 0.025 else "tall_grass"
        return "fern" if h < 0.16 else "grass"
    if vnoise(x, z, 13.0, 5) > 0.68 and pick > 0.93:
        return ("poppy", "dandelion", "oxeye_daisy", "azure_bluet", "cornflower")[int(hash01(x, z, 6) * 5)]
    return None


def main():
    s = Structure(N, HGT, N)
    s.fill(0, 0, 0, N - 1, 0, N - 1, "grass_block", snowy="false")
    path = set()
    for i in range(N):                                          # a worn path across the corner, wandering
        cx = int(round(i * 0.75 + 6 + 3 * math.sin(i / 7.0)))
        for w in range(-1, 2):                                     # r31: a 1-wide core, feathered sides
            x, z = cx + w, i
            rr = hash01(x, z, 9)
            if w != 0 and rr > 0.5:
                continue
            if 0 <= x < N:
                path.add((x, z))
                s.set(x, 0, z, "dirt_path" if rr < 0.25 else "coarse_dirt" if rr < 0.8 else "rooted_dirt")
    occupied = set(path)
    rnd = random.Random(28)
    pieces = [(gen_trees.oak(3), 31, 8), (gen_trees.oak(11, old=True), 6, 28), (gen_trees.birch(5), 36, 33),
              (gen_trees.bush(2), 22, 38), (gen_trees.boulder(4), 14, 12)]
    for piece, ox, oz in pieces:
        gen_trees._write_piece(s, piece, ox, oz, lift=1)
        for dx in range(gen_trees.SIZE):
            for dz in range(gen_trees.SIZE):
                if abs(dx - gen_trees.SIZE // 2) <= 2 and abs(dz - gen_trees.SIZE // 2) <= 2:
                    occupied.add((ox + dx, oz + dz))
    rnd.random()
    for x in range(N):
        for z in range(N):
            if (x, z) in occupied:
                continue
            p = plant_at(x, z)
            if p is None:
                continue
            if p in ("tall_grass", "large_fern"):
                s.set(x, 1, z, p, half="lower")
                s.set(x, 2, z, p, half="upper")
            else:
                s.set(x, 1, z, p)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    s.write(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
