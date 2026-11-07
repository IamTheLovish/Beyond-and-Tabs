#!/usr/bin/env python3
"""Two tree samplers with ONE layout for a blind before / after in the same eyes run (eyes.yml `structure`):

    python3 tools/tree_ab_samplers.py     # writes design/trees/tree_ab_before.nbt and design/trees/tree_ab_after.nbt

before: the trees the battlefield biome grew until the tree shapes lane - vanilla's plains oak and birch, rebuilt
        here from vanilla's own rules (a straight trunk of 4-6 / 5-7 logs; blob leaves of radius 2 on the two lower
        rows and 1 on the two upper, corners cut at random on the lower rows and always on the top row), our own
        code, no game file copied.
after:  the templates the game now grows (tools/gen_trees.py: oak_1-4, birch_1-4), same spots.

Each sampler: a 42 x 18 x 22 grass pad, 2 rows of 4 trees 10 apart, two oaks to one birch as in broadleaf_trees.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_trees as g  # noqa: E402
from gen_structures import Structure  # noqa: E402

GAP = 10
LAYOUT = ("oak", "oak", "birch", "oak", "oak", "birch", "oak", "birch")
OURS = {"oak": [1, 2, 3, 4, 1, 3], "birch": [1, 2, 3, 4]}


def vanilla(kind, rng):
    """Vanilla's straight-trunk tree with blob leaves (oak: trunk 4 + 0-2, birch: 5 + 0-2)."""
    t = g.Tree("oak_log" if kind == "oak" else "birch_log", "oak_leaves" if kind == "oak" else "birch_leaves")
    h = (4 if kind == "oak" else 5) + rng.randint(0, 2)
    for y in range(h):
        t.logs[(g.C, y, g.C)] = "y"
    top = h                                    # the leaves' anchor: one above the last log
    for j in (0, -1, -2, -3):
        r = 1 if j > -2 else 2
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r and (j == 0 or rng.random() < 0.5):
                    continue
                p = (g.C + dx, top + j, g.C + dz)
                if p not in t.logs:
                    t.leaf.add(p)
    return t


def ours(kind, k):
    v = OURS[kind][k % len(OURS[kind])] - 1
    names = list(g.SPECIES)
    seed = 1000 + 17 * v + 101 * names.index(kind)
    if kind == "oak":
        return g.oak(seed, old=(v == 3))
    return g.birch(seed, ("straight", "fork", "lean", "straight")[v])


def sampler(trees, path):
    s = Structure(4 * GAP + 2, g.H + 2, 2 * GAP + 2)          # + 2: the pad and a lifted 16-high birch
    s.fill(0, 0, 0, 4 * GAP + 1, 0, 2 * GAP + 1, "grass_block", snowy="false")
    for k, tree in enumerate(trees):
        row, col = divmod(k, 4)
        ox, oz = col * GAP + 1 + GAP // 2 - g.C, row * GAP + 1 + GAP // 2 - g.C
        g._write_piece(s, tree, ox, oz, lift=1)
    s.write(path)


def main():
    rng = random.Random(555)
    sampler([vanilla(kind, rng) for kind in LAYOUT], os.path.join(g.OUT, "tree_ab_before.nbt"))
    count = {"oak": 0, "birch": 0}
    after = []
    for kind in LAYOUT:
        after.append(ours(kind, count[kind]))
        count[kind] += 1
    sampler(after, os.path.join(g.OUT, "tree_ab_after.nbt"))


if __name__ == "__main__":
    main()
