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

    def clump(self, c, rx, ry, rz, rng, holes=0.10, dome=0.0):
        """An irregular leaf clump: an ellipsoid with a ragged edge (noise on the radius) and a few holes. dome > 0
        squashes the underside (a real canopy is a dome over a flatter base): below the centre the vertical radius is
        ry * (1 - dome)."""
        cx, cy, cz = c
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for y in range(int(cy - ry - 1), int(cy + ry + 2)):
                for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                    ryy = ry * (1 - dome) if y < cy else ry
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / max(ryy, 0.5)) ** 2 + ((z - cz) / rz) ** 2
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
            # an upright log whose top is open (nothing above, or leaves) shows its cut end grain - an orange square in
            # the crown from the RTS camera (round 3 stills): those become bark-all-round wood too
            if axis == "y" and self.logs.get((x, y + 1, z)) != "y":
                axis = "w"
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


def oak(seed, old=False):
    """Broad, open crown on 3-4 climbing limbs; each limb forks once near its end and every twig carries its own domed
    clump, so the crown is 6-9 clumps with light between them; a cap clump over the trunk. old=True: a 2x2 trunk for its
    first blocks (an old oak - the variety a grove needs), the limbs leave higher."""
    rng = random.Random(seed)
    t = Tree("oak_log", "oak_leaves")
    h = rng.randint(5, 6) + (1 if old else 0)
    lean = (rng.choice((-1, 0, 1)), rng.choice((-1, 0, 1)))
    if old:                                                      # 2x2 at the foot, stepping in: 4, then 2 above
        for dx, dz, top_y in ((0, 0, 2), (1, 0, 1), (0, 1, 1), (1, 1, 0)):
            t.line((C + dx, 0, C + dz), (C + dx, top_y, C + dz))
        # (round 5: the four buttress roots read as a brown cross of cut logs on the ground from the RTS camera)
    top = t.line((C, 0, C), (C + lean[0], h, C + lean[1]))
    roots(t, rng, 3)
    n = rng.randint(3, 4)
    for k in range(n):
        ang = k * 2 * math.pi / n + rng.uniform(-0.4, 0.4)
        reach = rng.uniform(2.0, 2.6)                            # crown <= ~7 wide: units stay visible
        start = (top[0], top[1] - k % 2 - 1, top[2])               # limbs leave at two heights, not one knot
        end = (round(start[0] + reach * math.cos(ang)), start[1] + rng.randint(2, 3),
               round(start[2] + reach * math.sin(ang)))
        e = t.line(start, (end[0], start[1] + 1, end[2]))       # limbs climb as they reach out
        e = t.line(e, end)
        # round 5: a pad on the elbow too - with pads only on the twig tips the crown read skeletal (logs between)
        t.clump((e[0], e[1] + 0.3, e[2]), 1.4, 1.0, 1.4, rng, 0.1, dome=0.35)
        for side in (-0.7, 0.7):                                 # the limb forks: two twigs, a clump each
            a2 = ang + side
            tw = (round(e[0] + 1.2 * math.cos(a2)), e[1] + 1, round(e[2] + 1.2 * math.sin(a2)))
            t.line(e, tw)                                        # (a skipped twig left its clump > 6 from wood)
            # pads of different proportions and heights, not one "+" stamp repeated (round 3 review)
            t.clump((tw[0] + rng.uniform(-0.4, 0.4), tw[1] + rng.uniform(0.2, 1.2), tw[2] + rng.uniform(-0.4, 0.4)),
                    rng.uniform(1.4, 2.1), rng.uniform(1.1, 1.5), rng.uniform(1.4, 2.1), rng, 0.1, dome=0.35)
    t.line(top, (top[0], top[1] + 3, top[2]))
    t.clump((top[0], top[1] + 3.2, top[2]), 1.6, 1.6, 1.6, rng, 0.15, dome=0.3)
    return t


def birch(seed, form="straight"):
    """Slim and pale: 3-4 blocks of white trunk, then a narrow crown of two or three stacked clumps with a gap of light
    between them. form: "straight", "lean" (the trunk leans a block halfway up) or "fork" (two stems from knee height)."""
    rng = random.Random(seed)
    t = Tree("birch_log", "birch_leaves")
    h = rng.randint(7, 8)
    stems = []
    if form == "fork":
        knee = t.line((C, 0, C), (C, 2, C))
        d = rng.choice(((1, 0), (0, 1)))
        stems.append(t.line(knee, (C - d[0], h, C - d[1])))
        stems.append(t.line(knee, (C + d[0], h - 1, C + d[1])))
    elif form == "lean":
        mid = t.line((C, 0, C), (C, 3, C))
        d = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
        stems.append(t.line(mid, (C + d[0], h, C + d[1])))
    else:
        stems.append(t.line((C, 0, C), (C, h, C)))
    for top in stems:
        y = 4
        ang = rng.uniform(0, 2 * math.pi)
        while y < top[1]:
            ang += rng.uniform(1.8, 2.6)                         # twigs spiral round the stem
            e = t.line((top[0], y, top[2]), (round(top[0] + 1.2 * math.cos(ang)), y, round(top[2] + 1.2 * math.sin(ang))))
            # round 5: 1.3-wide clumps every 3 left the white trunk showing through the crown from above (a lollipop
            # with holes) - fuller clumps every 2, still a little light between them from the side
            t.clump((e[0], e[1] + 0.4, e[2]), 1.7, 1.2, 1.7, rng, 0.08)
            y += 2
        t.clump((top[0], top[1] + 0.6, top[2]), 1.8, 1.8, 1.8, rng, 0.04)   # caps the stem: no white log end on top
    return t


def spruce(seed, height=None):
    """A conifer in tiers: rings of short horizontal limbs that shrink up the trunk, each ring a flat skirt of
    needles, a gap of light between the skirts, a spire on top."""
    rng = random.Random(seed)
    t = Tree("spruce_log", "spruce_leaves")
    h = height or rng.randint(9, 11)
    t.line((C, 0, C), (C, h - 1, C))                             # the trunk stops under the needle tip
    roots(t, rng, 2)
    # whorls 3 apart from 2 blocks up (bare trunk at the foot), shrinking upward, a gap of light between them (round 3
    # had them 2 apart with drooping edges filling the gap: a solid dark cone)
    tiers = [(y, max(1.2, 3.2 * (1 - (y - 2) / (h - 1)))) for y in range(2, h - 2, 3)]
    for y, r in tiers:
        for k in range(4 if r >= 2.2 else 0):            # no limbs in the small top tiers (they showed through)
            ang = k * math.pi / 2 + rng.uniform(-0.3, 0.3) + (y % 4) * math.pi / 4
            reach = max(1, round(r - 1.8))        # limbs stay inside the skirt (they poked through, r1)
            t.line((C, y, C), (round(C + reach * math.cos(ang)), y, round(C + reach * math.sin(ang))))
        # round 5: every tier was the same centred "+" slab - a stacked pagoda from above; each tier now sits a little
        # off the trunk and has its own width and oval
        r = r * rng.uniform(0.85, 1.15)
        off = (rng.uniform(-0.5, 0.5), rng.uniform(-0.5, 0.5))
        t.clump((C + off[0], y + 0.6, C + off[1]), r * rng.uniform(0.85, 1.0), 1.0, r * rng.uniform(0.85, 1.0), rng, 0.0)
        bite = rng.uniform(0, 2 * math.pi)                       # a missing chunk on one side: not a perfect cone
        for x in range(int(C - r - 1), int(C + r + 2)):
            for z in range(int(C - r - 1), int(C + r + 2)):
                if math.hypot(x - C, z - C) > r - 0.9 and math.cos(math.atan2(z - C, x - C) - bite) > 0.85:
                    for yy in (y, y + 1):
                        t.leaf.discard((x, yy, z))
    t.clump((C, h - 0.5, C), 1.3, 1.8, 1.3, rng, 0.0)       # needles round the top of the trunk, not a bare spire
    t.clump((C, h + 0.5, C), 0.9, 1.4, 0.9, rng, 0.0)
    if (C, h + 2, C) not in t.leaf and h + 2 < H:              # round 5: a one-block tip, not a blunt top
        t.leaf.add((C, h + 2, C))
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
        out = t.line(mid, (mid[0] + dx, mid[1] + 1, mid[2] + dz))          # the arm reaches out first...
        e = t.line(out, (out[0] + dx // 2, out[1] + up, out[2] + dz // 2))    # ...then rises
        r = rng.uniform(1.8, 2.2)
        t.clump((e[0], e[1] + 1, e[2]), r, 0.7, r, rng, 0.04)                  # the pad, and a smaller pad on top
        t.clump((e[0] + rng.choice((-1, 0, 1)), e[1] + 2, e[2] + rng.choice((-1, 0, 1))), r * 0.55, 0.6, r * 0.55, rng)
    return t


SPECIES = {"oak": oak, "birch": birch, "spruce": spruce, "acacia": acacia}
VARIANTS = 4


def main():
    os.makedirs(OUT, exist_ok=True)
    trees = []
    for name, fn in SPECIES.items():
        for v in range(VARIANTS):
            seed = 1000 + 17 * v + 101 * list(SPECIES).index(name)
            tree = fn(seed, (8, 10, 12, 9)[v]) if name == "spruce" else \
                fn(seed, old=(v == 3)) if name == "oak" else \
                fn(seed, ("straight", "fork", "lean", "straight")[v]) if name == "birch" else fn(seed)
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
