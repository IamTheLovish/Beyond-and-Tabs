#!/usr/bin/env python3
"""Our own map trees (Lane B, lovish Oct 21 2026: "tree shapes can be modified to fit the game") - original tree
templates to replace the vanilla TreeFeatures that maps/MapBuilder.decorate() places, which read as cube blobs from the
RTS camera (design/ART-FAULTS.md "blob canopies / stiff trunks").

Each tree: a trunk that leans and tapers in steps (a root flare at the foot), branches that leave the trunk at angles
as horizontal logs, and the crown built from SEVERAL leaf clumps at the branch ends and the top - so light falls between
the clumps and the silhouette breaks up, instead of one ellipsoid of leaves. Within design/maps.md's map rules: short
(trees are reclaim lanes, never a canopy over a base or a main lane), logs + leaves only so resources/TreeClearing still
fells them, and every leaf within 6 of a log with its `distance` state written (persistent=false): nothing decays, and
nothing floats once the logs are felled.

    python3 tools/gen_trees.py      # writes design/trees/<species>_<n>.nbt and design/trees/tree_sampler_a/b.nbt

Deterministic (fixed seeds). Templates leave every unset position as structure void, so placing one never erases the
ground around it. y = 0 is the first block above the ground."""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_structures import Structure  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "design", "trees")
SIZE = 15            # template footprint (blocks); the trunk stands at the centre
H = 16               # template height


class Tree:
    def __init__(self, log, leaves):
        self.log, self.leaves = log, leaves
        # limbs use the species' WOOD block (bark on all six faces): a horizontal log shows its end grain, which read as
        # orange squares through the spruce skirts (reviewer, eyes runs 39552449667/39552446960). Wood is in
        # BlockTags.LOGS, so resources/TreeClearing still fells it with the tree.
        self.wood = log.replace("_log", "_wood")
        self.logs = {}            # (x, y, z) -> axis
        self.leaf = set()

    def put_log(self, p, axis="y"):
        x, y, z = p
        if 0 <= x < SIZE and 0 <= y < H and 0 <= z < SIZE:
            if self.logs.get((x, y, z)) == "y" and axis != "y":
                return                            # the trunk keeps its upright log where a limb leaves it
            self.logs[(x, y, z)] = axis
            self.leaf.discard((x, y, z))

    def line(self, a, b, horizontal_axis=True):
        """Logs from a to b, one block per step; a step that moves sideways lies along that axis (a branch), a step
        up stands upright."""
        (x0, y0, z0), (x1, y1, z1) = a, b
        n = max(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0), 1)
        prev = a
        for i in range(n + 1):
            t = i / n
            p = (round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), round(z0 + (z1 - z0) * t))
            d = (p[0] - prev[0], p[1] - prev[1], p[2] - prev[2])
            if sum(1 for c in d if c) > 1:
                # a diagonal step touches the last block only at an edge - the oak's crown read as floating above its
                # stump (reviewer); fill the corner so every step shares a face
                self.put_log((p[0], prev[1], p[2]) if d[1] else (p[0], p[1], prev[2]), "w")
            axis = "y"
            if horizontal_axis and d[1] == 0 and (d[0] or d[2]):
                axis = "w"
            self.put_log(p, axis)
            prev = p
        return prev

    def clump(self, c, rx, ry, rz, rng, holes=0.10):
        """An irregular leaf clump: an ellipsoid with a ragged edge (noise on the radius) and a few holes."""
        cx, cy, cz = c
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for y in range(int(cy - ry - 1), int(cy + ry + 2)):
                for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                    if d <= 1.0 + 0.35 * (rng.random() - 0.5) and rng.random() > holes * d:
                        if 0 <= x < SIZE and 0 <= y < H and 0 <= z < SIZE and (x, y, z) not in self.logs:
                            self.leaf.add((x, y, z))

    def write(self, s, ox=0, oz=0):
        # leaf distance: steps to the nearest log through leaves (vanilla's rule); drop leaves further than 6
        dist = {}
        frontier = [p for p in self.leaf if any((p[0] + dx, p[1] + dy, p[2] + dz) in self.logs
                                              for dx, dy, dz in NB)]
        for p in frontier:
            dist[p] = 1
        k = 0
        while k < len(frontier):
            p = frontier[k]
            k += 1
            for dx, dy, dz in NB:
                q = (p[0] + dx, p[1] + dy, p[2] + dz)
                if q in self.leaf and q not in dist and dist[p] < 6:
                    dist[q] = dist[p] + 1
                    frontier.append(q)
        for (x, y, z), axis in self.logs.items():
            if axis == "w":
                s.set(ox + x, y, oz + z, self.wood, axis="y")
            else:
                s.set(ox + x, y, oz + z, self.log, axis=axis)
        for p, d in dist.items():
            s.set(ox + p[0], p[1], oz + p[2], self.leaves, distance=str(d), persistent="false", waterlogged="false")
        return len(self.leaf) - len(dist)


NB = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
C = SIZE // 2


def roots(t, rng, n=3, log=None):
    for k in rng.sample(range(4), n):
        dx, dz = ((1, 0), (-1, 0), (0, 1), (0, -1))[k]
        t.put_log((C + dx, 0, C + dz), "w")


def oak(seed):
    """Broad, open crown on 3-4 limbs; each limb ends in its own clump, a smaller clump caps the trunk."""
    rng = random.Random(seed)
    t = Tree("oak_log", "oak_leaves")
    h = rng.randint(5, 6)
    lean = (rng.choice((-1, 0, 1)), rng.choice((-1, 0, 1)))
    top = t.line((C, 0, C), (C + lean[0], h, C + lean[1]))
    roots(t, rng, 3)
    for k in range(rng.randint(3, 4)):
        ang = k * 2 * math.pi / 4 + rng.uniform(-0.5, 0.5)
        reach = rng.uniform(2.0, 2.8)                            # crown <= ~7 wide: units stay visible
        start = (top[0], top[1] - rng.randint(1, 3), top[2])
        end = (round(start[0] + reach * math.cos(ang)), start[1] + rng.randint(2, 3),
               round(start[2] + reach * math.sin(ang)))
        e = t.line(start, (end[0], start[1] + 1, end[2]))       # limbs climb as they reach out
        e = t.line(e, end)
        t.clump((e[0], e[1] + 1.0, e[2]), rng.uniform(1.4, 1.8), rng.uniform(1.4, 1.8), rng.uniform(1.4, 1.8), rng,
                0.18)
    t.line(top, (top[0], top[1] + 3, top[2]))
    t.clump((top[0], top[1] + 3.2, top[2]), 1.7, 1.7, 1.7, rng, 0.18)
    return t


def birch(seed):
    """Tall, slim and pale: a straight trunk, short twiggy limbs, a narrow crown in two or three stacked clumps."""
    rng = random.Random(seed)
    t = Tree("birch_log", "birch_leaves")
    # slim (reviewer: "a pale oak"): 4+ blocks of white trunk below the crown from the RTS angle, a crown ~3-4 wide of
    # small offset clumps
    h = rng.randint(8, 10)
    top = t.line((C, 0, C), (C, h, C))
    ang = rng.uniform(0, 2 * math.pi)
    for y in range(5, h, 2):
        ang += rng.uniform(1.8, 2.6)                             # limbs spiral round the trunk
        e = t.line((C, y, C), (round(C + 1.2 * math.cos(ang)), y, round(C + 1.2 * math.sin(ang))))
        t.clump((e[0], e[1] + 0.5, e[2]), 1.4, 1.4, 1.4, rng, 0.12)
    t.clump((C, h + 0.4, C), 1.6, 1.9, 1.6, rng, 0.08)
    return t


def spruce(seed, height=None):
    """A conifer in tiers: rings of short horizontal limbs that shrink up the trunk, each ring a flat skirt of
    needles, a gap of light between the skirts, a spire on top."""
    rng = random.Random(seed)
    t = Tree("spruce_log", "spruce_leaves")
    h = height or rng.randint(9, 11)
    t.line((C, 0, C), (C, h - 1, C))                             # the trunk stops under the needle tip
    roots(t, rng, 2)
    tiers = [(y, max(1.2, 3.4 * (1 - (y - 2) / (h - 1)))) for y in list(range(2, h - 2, 2)) + [h - 2]]
    for y, r in tiers:
        for k in range(4 if r >= 2.2 else 0):            # no limbs in the small top tiers (they showed through)
            ang = k * math.pi / 2 + rng.uniform(-0.3, 0.3) + (y % 4) * math.pi / 4
            reach = max(1, round(r - 1.8))        # limbs stay inside the skirt (they poked through, r1)
            t.line((C, y, C), (round(C + reach * math.cos(ang)), y, round(C + reach * math.sin(ang))))
        t.clump((C, y + 0.3, C), r, 1.1, r, rng, 0.0)            # no holes: a hole showed the limb's bark
    t.clump((C, h - 0.5, C), 1.3, 1.8, 1.3, rng, 0.0)       # needles round the top of the trunk, not a bare spire
    t.clump((C, h + 0.5, C), 0.9, 1.4, 0.9, rng, 0.0)
    return t


def acacia(seed):
    """Savanna: a trunk that bends away, forks, and holds two or three flat umbrella crowns at different heights."""
    rng = random.Random(seed)
    t = Tree("acacia_log", "acacia_leaves")
    lean = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
    mid = t.line((C, 0, C), (C, 3, C))
    roots(t, rng, 2)
    # a real fork: two arms leaving the trunk in different directions, each carrying its own pad (<= ~7 wide overall)
    side = (-lean[1], lean[0]) if rng.random() < 0.5 else (lean[1], -lean[0])
    for k, (dx, dz, up) in enumerate(((lean[0] * 2, lean[1] * 2, 3), (side[0] * 2, side[1] * 2, 2))):
        e = t.line(mid, (mid[0] + dx, mid[1] + up, mid[2] + dz), horizontal_axis=False)
        t.clump((e[0], e[1] + 1, e[2]), rng.uniform(1.8, 2.2), 0.7, rng.uniform(1.8, 2.2), rng, 0.04)
    return t


SPECIES = {"oak": oak, "birch": birch, "spruce": spruce, "acacia": acacia}
VARIANTS = 3


def main():
    os.makedirs(OUT, exist_ok=True)
    trees = []
    for name, fn in SPECIES.items():
        for v in range(VARIANTS):
            seed = 1000 + 17 * v + 101 * list(SPECIES).index(name)
            tree = fn(seed, (7, 9, 11)[v]) if name == "spruce" else fn(seed)
            s = Structure(SIZE, H, SIZE)
            dropped = tree.write(s)
            s.write(os.path.join(OUT, "%s_%d.nbt" % (name, v + 1)))
            if dropped:
                print("  %s_%d: %d leaves further than 6 from a log were left out" % (name, v + 1, dropped))
            trees.append(tree)
    # two samplers for the eyes `structure` lane (one big grid was framed off-centre with trees cut off, reviewer):
    # tree_sampler_a = oak + birch rows, tree_sampler_b = spruce + acacia rows, 3 variants each, on grass
    gap = 10
    for half, label in ((0, "a"), (1, "b")):
        s = Structure(VARIANTS * gap + 2, H, 2 * gap + 2)
        s.fill(0, 0, 0, VARIANTS * gap + 1, 0, 2 * gap + 1, "grass_block", snowy="false")
        for k, tree in enumerate(trees[half * 2 * VARIANTS:(half + 1) * 2 * VARIANTS]):
            row, col = divmod(k, VARIANTS)
            ox, oz = col * gap + 1 + gap // 2 - C, row * gap + 1 + gap // 2 - C
            shifted = Tree(tree.log, tree.leaves)
            shifted.logs = {(x, y + 1, z): a for (x, y, z), a in tree.logs.items()}
            shifted.leaf = {(x, y + 1, z) for (x, y, z) in tree.leaf}
            _write_clipped(s, shifted, ox, oz)
        s.write(os.path.join(OUT, "tree_sampler_%s.nbt" % label))


def _write_clipped(s, tree, ox, oz):
    sx, sy, sz = s.size
    keep = lambda p: 0 <= ox + p[0] < sx and 0 <= p[1] < sy and 0 <= oz + p[2] < sz
    tree.logs = {p: a for p, a in tree.logs.items() if keep(p)}
    tree.leaf = {p for p in tree.leaf if keep(p)}
    global H
    old = H
    H = sy
    tree.write(s, ox, oz)
    H = old


if __name__ == "__main__":
    main()
