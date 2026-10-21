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
        self.plants = {}          # (x, y, z) -> block: the grass / flower skirt at the foot

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
        sx, sy, sz = s.size
        for (x, y, z), b in self.plants.items():
            if (x, y, z) in self.logs or (x, y, z) in dist or not (0 <= ox + x < sx and 0 <= oz + z < sz and y < sy):
                continue
            if isinstance(b, tuple):
                s.set(ox + x, y, oz + z, b[0], **b[1])
            else:
                s.set(ox + x, y, oz + z, b)
        return len(self.leaf) - len(dist)


NB = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
C = SIZE // 2


def skirt(t, rng, radius=2.6, density=0.55, flowers=("poppy", "dandelion", "oxeye_daisy", "azure_bluet")):
    """A ring of grass, fern and a few flowers round the foot (lovish's Tibetan cherry reference, Oct 21): the ground
    under a tree is never bare lawn. 1.20.1 ids ("grass" is the short grass)."""
    for x in range(int(C - radius - 1), int(C + radius + 2)):
        for z in range(int(C - radius - 1), int(C + radius + 2)):
            d = math.hypot(x - C, z - C)
            if 0.9 < d <= radius + 0.6 * (rng.random() - 0.5) and rng.random() < density * (1.2 - d / (radius + 1)):
                r = rng.random()
                t.plants[(x, 0, z)] = rng.choice(flowers) if r < 0.15 else "fern" if r < 0.35 else "grass"


def claw_roots(t, rng, n, top=2, reach=3):
    """Roots that leave the trunk a little above the ground and reach out and DOWN into it like claws (lovish's bonsai
    reference sheet, Oct 21): each root a diagonal run of wood from (trunk, y=top) to the ground 2-3 blocks out."""
    a0 = rng.uniform(0, 2 * math.pi)
    for k in range(n):
        a = a0 + k * 2 * math.pi / n + rng.uniform(-0.3, 0.3)
        r = reach * rng.uniform(0.8, 1.1)
        t.line((C, top, C), (round(C + r * math.cos(a)), 0, round(C + r * math.sin(a))))


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
    if old:
        roots(t, rng, 3)
    else:
        claw_roots(t, rng, rng.randint(3, 4), top=rng.randint(1, 2), reach=rng.uniform(2.2, 3.0))
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
        t.clump((e[0], e[1] + 0.3, e[2]), 1.4, 0.8, 1.4, rng, 0.1, dome=0.75)
        for side in (-0.7, 0.7):                                 # the limb forks: two twigs, a clump each
            a2 = ang + side
            tw = (round(e[0] + 1.2 * math.cos(a2)), e[1] + 1, round(e[2] + 1.2 * math.sin(a2)))
            t.line(e, tw)                                        # (a skipped twig left its clump > 6 from wood)
            # pads of different proportions and heights, not one "+" stamp repeated (round 3 review)
            # round 6 (lovish's custom-oak and world-tree references): PADS - wide, flat-bottomed, at staggered heights,
            # so the crown reads in layers with a shadowed underside and sky between the layers, not one leaf mass
            lift = rng.choice((0.3, 1.3, 2.3))
            if lift > 1:                                         # the twig climbs into a raised pad (leaves > 6 decay)
                t.line(tw, (tw[0], tw[1] + int(lift), tw[2]))
            t.clump((tw[0] + rng.uniform(-0.4, 0.4), tw[1] + lift, tw[2] + rng.uniform(-0.4, 0.4)),
                    rng.uniform(1.5, 2.2), rng.uniform(0.8, 1.0), rng.uniform(1.5, 2.2), rng, 0.08, dome=0.75)
    t.line(top, (top[0], top[1] + 3, top[2]))
    t.clump((top[0], top[1] + 3.2, top[2]), 1.8, 1.1, 1.8, rng, 0.1, dome=0.7)
    skirt(t, rng, 3.0)
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
    skirt(t, rng, 2.2, flowers=("oxeye_daisy", "azure_bluet", "dandelion"))
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
    skirt(t, rng, 2.4, 0.4, flowers=("fern",))                   # needles and ferns under a spruce, no flowers
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


def pine(seed):
    """A tall pine (lovish's 'cute pine' reference, Oct 21): a long bare trunk with a few dead fence stubs, the crown only
    in the top third - short limbs, each holding its own flat needle pad, sky between the pads."""
    rng = random.Random(seed)
    t = Tree("spruce_log", "spruce_leaves")
    h = rng.randint(10, 12)                                     # the crown must fit the 16-high template
    t.line((C, 0, C), (C, h, C))
    roots(t, rng, 2)
    for y in range(3, int(h * 0.55)):                              # dead stubs on the bare trunk
        if rng.random() < 0.35:
            dx, dz = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
            side = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)]
            props = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
            props[side] = "true"                                  # joined to the trunk
            t.plants[(C + dx, y, C + dz)] = ("spruce_fence", props)
    y = int(h * 0.55)
    ang = rng.uniform(0, 2 * math.pi)
    while y <= h - 1:
        ang += rng.uniform(1.9, 2.7)
        reach = rng.choice((1, 2, 2))
        e = t.line((C, y, C), (round(C + reach * math.cos(ang)), y + 1, round(C + reach * math.sin(ang))))
        t.clump((e[0], e[1] + 0.5, e[2]), rng.uniform(1.5, 2.1), 0.8, rng.uniform(1.5, 2.1), rng, 0.04, dome=0.7)
        y += 1
    t.clump((C, h + 0.6, C), 1.5, 0.9, 1.5, rng, 0.05, dome=0.6)  # the crown's top pad
    skirt(t, rng, 2.0, 0.35, flowers=("fern",))
    return t


def cherry(seed):
    """Cherry (lovish's Tibetan-cherry and cherry-blossom references, Oct 21): 2-3 stems that part from the foot and
    climb in diagonal steps, each ending in a few SEPARATE round blossom puffs with sky between them; pink petals and
    grass on the ground under it."""
    rng = random.Random(seed)
    t = Tree("cherry_log", "cherry_leaves")
    t.line((C, 0, C), (C, 1, C))
    n = rng.choice((2, 3, 3))
    a0 = rng.uniform(0, 2 * math.pi)
    for k in range(n):
        a = a0 + k * 2 * math.pi / n + rng.uniform(-0.35, 0.35)
        out = rng.uniform(1.8, 2.6)
        h = rng.randint(5, 7)
        top = t.line((C, 1, C), (round(C + out * math.cos(a)), h, round(C + out * math.sin(a))))
        for j in range(rng.randint(3, 4)):                         # puffs round the stem's end, apart from each other
            b = a + rng.uniform(-1.3, 1.3)
            r = rng.uniform(1.0, 1.6)
            tw = t.line(top, (round(top[0] + r * math.cos(b)), top[1] + rng.randint(0, 1), round(top[2] + r * math.sin(b))))
            # puffs a little smaller and more numerous so sky shows between them (first offline pass read as pink slabs)
            t.clump((tw[0], tw[1] + 0.8, tw[2]), rng.uniform(1.1, 1.5), rng.uniform(0.9, 1.2), rng.uniform(1.1, 1.5), rng,
                    0.08, dome=0.4)
    skirt(t, rng, 3.0, 0.5, flowers=("pink_petals",))
    for (x, y, z), b in list(t.plants.items()):
        if b == "pink_petals":                                    # a few petals on each texel, facing at random
            t.plants[(x, y, z)] = ("pink_petals", {"flower_amount": str(rng.randint(2, 4)),
                                                    "facing": rng.choice(("north", "south", "east", "west"))})
    return t


SPECIES = {"oak": oak, "birch": birch, "spruce": spruce, "acacia": acacia, "pine": pine, "cherry": cherry}
VARIANTS = 4


# ---- ground scatter (Oct 21): small pieces that break up flat grass between trees - the realism list's "ground
# clutter" at map scale. Each piece is low (<= 3 blocks) so units stay visible from the RTS camera, and every block is
# one that resources/TreeClearing or a builder can clear like terrain.

def bush(seed):
    """A low shrub: a short wood stub under a squat, uneven leaf mound (2-4 wide, 1-2 high)."""
    rng = random.Random(seed)
    leaves = rng.choice(("oak_leaves", "oak_leaves", "dark_oak_leaves", "azalea_leaves"))
    t = Tree("oak_log", leaves)
    t.put_log((C, 0, C), "w")
    r = rng.uniform(1.6, 2.3)
    # centred low so the mound sits ON the grass round its stub (at 0.8 high it floated as a slab over a bare stub)
    t.clump((C + rng.uniform(-0.4, 0.4), 0.5, C + rng.uniform(-0.4, 0.4)), r, 1.3, r * rng.uniform(0.75, 1.0), rng, 0.08,
            dome=0.2)
    if rng.random() < 0.6:                                       # a second, smaller lobe: not a round ball
        a = rng.uniform(0, 2 * math.pi)
        t.clump((C + 1.6 * math.cos(a), 0.3, C + 1.6 * math.sin(a)), r * 0.7, 1.0, r * 0.7, rng, 0.1, dome=0.2)
    return t


def boulder(seed, scatter=None):
    """A weathered boulder half sunk in the grass: a lumpy ellipsoid of mixed stone (mossy on the shaded north side),
    with one or two loose stones beside it. Returns {(x, y, z): block}."""
    rng = random.Random(seed)
    rx, ry, rz = rng.uniform(1.6, 2.3), rng.uniform(1.3, 1.9), rng.uniform(1.4, 2.1)
    cy = -0.3                                                    # sunk: the widest part sits at grass level
    out = {}
    mix = ("stone", "stone", "andesite", "andesite", "cobblestone", "tuff")
    for x in range(int(C - rx - 1), int(C + rx + 2)):
        for y in range(0, int(cy + ry + 2)):
            for z in range(int(C - rz - 1), int(C + rz + 2)):
                d = ((x - C) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - C) / rz) ** 2
                if d <= 1.0 + 0.3 * (rng.random() - 0.5):
                    north = (z - C) < -0.3 * rz                   # -z: the side the sun never reaches in the scenes
                    b = rng.choice(mix)
                    if north and rng.random() < 0.6:
                        b = "mossy_cobblestone"
                    out[(x, y, z)] = b
    for _ in range(rng.randint(1, 2)):                           # loose stones
        a = rng.uniform(0, 2 * math.pi)
        p = (round(C + (rx + 1.6) * math.cos(a)), 0, round(C + (rz + 1.6) * math.sin(a)))
        out[p] = rng.choice(("cobblestone", "andesite", "mossy_cobblestone"))
    return out


def fallen_log(seed):
    """A fallen trunk lying in the grass with a broken stump nearby, moss on its top, a few leaves where the crown was."""
    rng = random.Random(seed)
    wood = rng.choice(("oak", "birch", "spruce"))
    t = Tree(wood + "_log", wood + "_leaves")
    L = rng.randint(4, 5)
    along_x = rng.random() < 0.5
    x0 = C - 3                                                   # the whole piece stays inside its 9 x 9 footprint
    for k in range(L):
        p = (x0 + k, 0, C) if along_x else (C, 0, x0 + k)
        t.put_log(p, "x" if along_x else "z")
    stump = (x0 - 1, 0, C + 2) if along_x else (C + 2, 0, x0 - 1)
    t.put_log((stump[0], 1, stump[2]), "w")                       # a 2-high broken stump
    t.put_log(stump, "w")                                         # bark all round: no cut ring showing on top
    end = (x0 + L, 0, C) if along_x else (C, 0, x0 + L)
    t.clump((end[0], 0.4, end[2]), 1.5, 0.9, 1.5, rng, 0.2, dome=0.3)   # what is left of the crown
    return t


SCATTER = {"bush": (bush, 4), "boulder": (boulder, 4), "fallen_log": (fallen_log, 3)}


# ---- bare trees (lovish's references, Oct 21 03:08: a dark gnarled dead tree at night and a giant bare tree with a
# "normal tree" beside it for scale). What makes them read: limbs that THIN as they fork - full wood, then fence twigs
# at the tips - and limbs that climb in diagonal steps rather than straight runs. Leafless, so the twigs carry the shape.

def bare_tree(seed, wood="spruce", giant=False):
    """Returns ({(x, y, z): (block, props)}, size, height). Trunk 2x2 (giant) or 1x1, limbs that fork 3-4 times, the
    last forks fence twigs. Positions are inside a size x height x size box, trunk at its centre."""
    rng = random.Random(seed)
    size, height = (27, 30) if giant else (15, 16)
    c = size // 2
    blocks = {}
    W, F = wood + "_wood", wood + "_fence"
    STAIR = "dark_oak_stairs"                                  # planks are lighter than bark; dark oak planks sit closest to spruce and dark oak bark

    def put(p, b):
        x, y, z = p
        if 0 <= x < size and 0 <= y < height and 0 <= z < size:
            if blocks.get(p) == W and b != W:
                return                                            # thick wins where a twig or stair meets a limb
            blocks[p] = b

    def run(a, d, n, b):
        """n steps from a along direction d (a unit vector); every step shares a face with the last."""
        x, y, z = a
        prev = (round(x), round(y), round(z))
        for _ in range(n):
            x, y, z = x + d[0], y + d[1], z + d[2]
            p = (round(x), round(y), round(z))
            if p == prev:
                continue
            dd = [p[i] - prev[i] for i in range(3)]
            if sum(1 for v in dd if v) > 1:                       # diagonal: fill a corner so the limb stays joined
                hx = 0 if dd[0] else 2                            # the horizontal axis that moved
                if dd[1] > 0 and b == W and sum(1 for v in dd if v) == 2:
                    # a climbing step on a wood limb: a STAIR under the new block, rising the way the limb goes, so
                    # the bend reads as a slope (lovish's bare-tree references) instead of a cube staircase
                    q = list(p)
                    q[1] = prev[1]
                    face = {(0, 1): "east", (0, -1): "west", (2, 1): "south", (2, -1): "north"}[(hx, dd[hx])]
                    put(tuple(q), (STAIR, face))
                else:
                    q = list(prev)
                    for i in (1, 0, 2):
                        if dd[i]:
                            q[i] = p[i]
                            if tuple(q) != p:
                                put(tuple(q), b)
                            break
            put(p, b)
            prev = p
        return (x, y, z)

    def limb(a, ang, rise, length, level, maxlevel):
        b = F if level >= maxlevel - 1 else W
        h = math.hypot(1, rise)
        d = (math.cos(ang) / h, rise / h, math.sin(ang) / h)
        e = run(a, d, int(length), b)
        if level < maxlevel:
            for k in range(rng.choice((2, 2, 3))):
                ang2 = ang + rng.uniform(0.45, 0.95) * (1 if k % 2 else -1) + rng.uniform(-0.2, 0.2)
                # twigs reach OUT more than up: a rise that compounded per fork stood the fence tips upright like
                # candles (first offline sheet)
                rise2 = min(0.6, max(-0.15, rise * 0.6 + rng.uniform(-0.2, 0.3)))
                limb(e, ang2, rise2, max(2, length * rng.uniform(0.55, 0.75)), level + 1, maxlevel)
        elif rng.random() < 0.5:                                   # a crooked last twig: the "hook" in both refs
            put((round(e[0]), round(e[1]) + 1, round(e[2])), F)

    h = rng.randint(17, 20) if giant else rng.randint(6, 8)
    lean = rng.uniform(0, 2 * math.pi)
    for y in range(h):
        sway = (0.12 if giant else 0.15) * y
        x0, z0 = c + sway * math.cos(lean), c + sway * math.sin(lean)
        put((round(x0), y, round(z0)), W)
        if giant and y < h * 0.55:                                # a 2x2 bole that narrows to one block
            for dx, dz in ((1, 0), (0, 1), (1, 1)):
                put((round(x0) + dx, y, round(z0) + dz), W)
        if giant and y < 2:                                       # root flare
            for dx, dz in ((-1, 0), (0, -1), (2, 0), (0, 2), (2, 1), (1, 2)):
                if rng.random() < 0.7:
                    put((round(x0) + dx, y, round(z0) + dz), W)
    top = (c + 0.12 * h * math.cos(lean) if giant else c + 0.15 * h * math.cos(lean), h - 1,
           c + 0.12 * h * math.sin(lean) if giant else c + 0.15 * h * math.sin(lean))
    n = rng.randint(4, 5) if giant else rng.randint(3, 4)
    for k in range(n):                                            # main limbs leave at different heights round the bole
        ang = k * 2 * math.pi / n + rng.uniform(-0.4, 0.4)
        y0 = h - 1 - rng.randint(0, 5 if giant else 2)
        start = (top[0], y0, top[2])
        limb(start, ang, rng.uniform(0.35, 0.8), (6 if giant else 3) * rng.uniform(0.85, 1.15), 0, 4 if giant else 2)
    limb(top, rng.uniform(0, 2 * math.pi), 2.0, 4 if giant else 2, 1, 4 if giant else 2)   # the leader
    # fences connect to the fences and solid wood beside them
    out = {}
    for (x, y, z), b in blocks.items():
        props = {}
        if isinstance(b, tuple):                                  # a stair: (block, facing)
            out[(x, y, z)] = (b[0], {"facing": b[1], "half": "bottom", "shape": "straight", "waterlogged": "false"})
            continue
        if b == F:
            for side, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0)), ("east", (1, 0))):
                nb = blocks.get((x + dx, y, z + dz))
                props[side] = "true" if nb in (F, W) or isinstance(nb, tuple) else "false"
            props["waterlogged"] = "false"
        else:
            props["axis"] = "y"
        out[(x, y, z)] = (b, props)
    return out, size, height


BARE = {"bare_oak": dict(wood="spruce"), "dead_tree": dict(wood="dark_oak"),
        "giant_bare": dict(wood="spruce", giant=True)}


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
    for half, label in ((0, "a"), (1, "b"), (2, "c")):     # c = pine + cherry
        s = Structure(VARIANTS * gap + 2, H, 2 * gap + 2)
        s.fill(0, 0, 0, VARIANTS * gap + 1, 0, 2 * gap + 1, "grass_block", snowy="false")
        for k, tree in enumerate(trees[half * 2 * VARIANTS:(half + 1) * 2 * VARIANTS]):
            row, col = divmod(k, VARIANTS)
            ox, oz = col * gap + 1 + gap // 2 - C, row * gap + 1 + gap // 2 - C
            shifted = Tree(tree.log, tree.leaves)
            shifted.logs = {(x, y + 1, z): a for (x, y, z), a in tree.logs.items()}
            shifted.leaf = {(x, y + 1, z) for (x, y, z) in tree.leaf}
            shifted.plants = {(x, y + 1, z): b for (x, y, z), b in tree.plants.items()}
            _write_clipped(s, shifted, ox, oz)
        s.write(os.path.join(OUT, "tree_sampler_%s.nbt" % label))
    # ground scatter: one .nbt per piece (7 x 4 x 7 footprint, the piece centred) and a sampler with all of them
    PS, PH = 9, 4
    pieces = []
    for name, (fn, count) in SCATTER.items():
        for v in range(count):
            seed = 5000 + 31 * v + 211 * list(SCATTER).index(name)
            piece = fn(seed)
            s = Structure(PS, PH, PS)
            off = C - PS // 2
            _write_piece(s, piece, -off, 0)
            s.write(os.path.join(OUT, "%s_%d.nbt" % (name, v + 1)))
            pieces.append(piece)
    cols, gap = 6, 7
    rows = (len(pieces) + cols - 1) // cols
    s = Structure(cols * gap + 2, PH + 1, rows * gap + 2)
    s.fill(0, 0, 0, cols * gap + 1, 0, rows * gap + 1, "grass_block", snowy="false")
    for k, piece in enumerate(pieces):
        row, col = divmod(k, cols)
        _write_piece(s, piece, col * gap + 1 + gap // 2 - C, row * gap + 1 + gap // 2 - C, lift=1)
    s.write(os.path.join(OUT, "scatter_sampler.nbt"))
    # bare trees: 3 variants of each small kind, 2 giants, each in its own .nbt; one sampler with all of them
    bare = []
    for name, kw in BARE.items():
        for v in range(2 if kw.get("giant") else 3):
            blocks, size, height = bare_tree(7000 + 37 * v + 307 * list(BARE).index(name), **kw)
            s = Structure(size, height, size)
            for (x, y, z), (b, props) in blocks.items():
                s.set(x, y, z, b, **props)
            s.write(os.path.join(OUT, "%s_%d.nbt" % (name, v + 1)))
            bare.append((blocks, size))
    s = Structure(80, 31, 34)
    s.fill(0, 0, 0, 79, 0, 33, "grass_block", snowy="false")
    x = 1
    for blocks, size in bare:                                    # small ones in a row at the front, giants behind
        if size > 15:
            continue
        for (bx, by, bz), (b, props) in blocks.items():
            s.set(x + bx, by + 1, 19 + bz, b, **props)
        x += 13
    x = 6
    for blocks, size in bare:
        if size <= 15:
            continue
        for (bx, by, bz), (b, props) in blocks.items():
            if 0 <= x + bx < 80 and by + 1 < 31 and 0 <= bz - 8 < 34:
                s.set(x + bx, by + 1, bz - 8, b, **props)
        x += 32
    s.write(os.path.join(OUT, "bare_sampler.nbt"))


def _write_piece(s, piece, ox, oz, lift=0):
    if isinstance(piece, dict):                                  # a boulder: plain blocks
        sx, sy, sz = s.size
        for (x, y, z), b in piece.items():
            if 0 <= ox + x < sx and 0 <= y + lift < sy and 0 <= oz + z < sz:
                s.set(ox + x, y + lift, oz + z, b)
        return
    shifted = Tree(piece.log, piece.leaves)
    shifted.logs = {(x, y + lift, z): a for (x, y, z), a in piece.logs.items()}
    shifted.leaf = {(x, y + lift, z) for (x, y, z) in piece.leaf}
    shifted.plants = {(x, y + lift, z): b for (x, y, z), b in piece.plants.items()}
    _write_clipped(s, shifted, ox, oz)


def _write_clipped(s, tree, ox, oz):
    sx, sy, sz = s.size
    keep = lambda p: 0 <= ox + p[0] < sx and 0 <= p[1] < sy and 0 <= oz + p[2] < sz
    tree.logs = {p: a for p, a in tree.logs.items() if keep(p)}
    tree.leaf = {p for p in tree.leaf if keep(p)}
    tree.plants = {p: b for p, b in tree.plants.items() if keep(p)}
    global H
    old = H
    H = sy
    tree.write(s, ox, oz)
    H = old


if __name__ == "__main__":
    main()
