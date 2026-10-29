#!/usr/bin/env python3
"""Overgrowth: a post-process that takes a structure .nbt and returns an overgrown copy - vines down the walls, moss on
the stone base, dark bushes at the wall feet and on ledges, grass and ferns on soil. It never edits a generator: it
reads the finished building, so any building can be dressed (or left bare) without touching the lane that owns it.

Why (lovish, Oct 28 - "lets do it"): his reference is a Mediterranean villa in Minecraft - cream plaster, terracotta
roofs, vines hanging from the eaves, moss on the stone base, bushes spilling over terraces, no surface bare. Measured,
its foliage is dark and rich (HSV hue ~99, sat ~0.35, value ~0.24), so the greens here are deep, never lime:
  - bushes are mostly SPRUCE leaves (vanilla tints them a fixed dark green, #619961 - no biome tint, so the colour
    holds on every map), some dark-oak leaves; never azalea (reads lime) or moss blocks / carpet (lime too);
  - no rooted dirt (reads magenta from the RTS camera).

Rules (deterministic by seed; every added id is vanilla 1.20.1 - an unknown id loads as AIR):
  1. Moss: stone with a vanilla mossy twin (cobblestone, stone bricks, and their stairs / slabs / walls) at y 0..3 and
     exposed to the air turns mossy, with the same shape properties; more low down and on the shaded north/west sides.
  2. Vines: minecraft:vine hangs 1-5 blocks from the top of a wall run or from under an overhang (eaves, ledges,
     sills), only in front of a FULL solid face (never glass, slabs or stairs), every face that touches a full solid
     block is set (and only those), and the column stops where the wall behind it stops (a window, a door). More on
     north and west faces (shade) and under eaves. Never inside a door or arcade throat.
  3. Bushes: 1-3 persistent leaf blocks at wall feet and in corners, and on ledge edges (spilling over terraces and
     stair tops), each one checked against the walkable-space rule below.
  4. Grass and ferns on soil (grass block, dirt, podzol) next to walls and bushes; a rare potted fern on a ledge.
Walkable space: units are 4 wide x 8 tall at most. reachable_windows() finds every 4x4 footprint with 8 clear blocks
above it that a unit can reach from outside the box; a block that collides is only placed if every such footprint
reachable before still is - or one a block beside it is (a bush at a wall foot narrows a yard, it never closes a
passage). Vines and grass do not collide, but are kept out of door/arcade throats anyway. Nothing is placed outside
the bounding box, no original block is removed, and the whole pass stays under a budget share of the block count.

    python3 tools/overgrowth.py IN.nbt OUT.nbt [--seed N] [--density 1.0] [--budget 0.3]
    python3 tools/overgrowth.py --pilot      (writes design/eyes/overgrowth_pilot_{before,after}.nbt)
Tests: tools/test_overgrowth.py. Preview: python3 tools/structure_render.py design/eyes/overgrowth_pilot_*.nbt
"""
import argparse
import collections
import os
import random
import shutil
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_structures import Structure  # noqa: E402  - the NBT writer the generators use
from preview_structure import load_nbt  # noqa: E402  - the NBT reader

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# The pilot: the White Angel infantry lab at level 3 (Cathedral of Arms) - the most wall and ledge area of the
# infantry labs, and cream-white walls closest to the reference's plaster.
PILOT_SOURCE = os.path.join(REPO, "bar/src/main/resources/assets/reignofnether/structures/barracks_l3.nbt")
PILOT_BEFORE = os.path.join(REPO, "design/eyes/overgrowth_pilot_before.nbt")
PILOT_AFTER = os.path.join(REPO, "design/eyes/overgrowth_pilot_after.nbt")
PILOT_SEED = 28
PILOT_DENSITY = 1.8           # tuned by eye on the structure_render previews (1.0 read as a few stray sprigs)

BUDGET = 0.30                 # additions + swaps <= this share of the original block count
SHARES = {"moss": 0.22, "vine": 0.40, "bush": 0.24, "ledge": 0.09, "plant": 0.05}   # of the budget, per rule
UNIT_W, UNIT_H = 4, 8         # the largest unit's footprint and height (design/LANES.md: >= 4 wide x 8 tall)

AIR = {"air", "cave_air", "void_air", "structure_void"}
SOIL = {"grass_block", "dirt", "coarse_dirt", "podzol"}
MOSSY = {   # vanilla 1.20.1 mossy twins; shape properties carry over unchanged
    "cobblestone": "mossy_cobblestone", "cobblestone_stairs": "mossy_cobblestone_stairs",
    "cobblestone_slab": "mossy_cobblestone_slab", "cobblestone_wall": "mossy_cobblestone_wall",
    "stone_bricks": "mossy_stone_bricks", "stone_brick_stairs": "mossy_stone_brick_stairs",
    "stone_brick_slab": "mossy_stone_brick_slab", "stone_brick_wall": "mossy_stone_brick_wall",
}
# Weight per vine property = the side the wall is on. "south" means the wall is south of the vine, i.e. the vine
# covers a north-facing (shaded) wall face; "east" a west-facing one.
FACE_WEIGHT = {"south": 1.0, "east": 0.9, "north": 0.55, "west": 0.4}
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
VINE_LENGTHS = ([1, 2, 3, 4, 5], [0.15, 0.25, 0.25, 0.2, 0.15])

PLANT_NAMES = {"grass", "tall_grass", "short_grass", "fern", "large_fern", "dead_bush", "vine", "poppy", "dandelion",
               "cornflower", "allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "blue_orchid", "peony",
               "lilac", "rose_bush", "sunflower", "light"}
PASS_SUFFIX = ("_tulip", "_sapling", "_carpet", "torch", "_banner", "_sign", "_button", "_pressure_plate", "rail",
               "lever", "tripwire", "redstone_wire")
THIN_SUFFIX = ("_stairs", "_fence", "_fence_gate", "_wall", "_door", "_trapdoor", "_pane", "_carpet", "_banner",
               "_button", "_pressure_plate", "_sign", "_rod", "_sapling", "_bed", "_head", "_skull", "candle", "_coral",
               "_coral_fan", "_tulip", "_roots", "_mushroom", "rail")
THIN_WORDS = ("glass", "leaves", "torch", "lantern", "chain", "iron_bars", "bell", "vine", "fern", "flower_pot",
              "potted_", "fire", "ladder", "scaffolding", "cobweb", "lever", "tripwire", "dripleaf", "lichen", "water",
              "lava", "snow", "ice", "anvil", "hopper", "cauldron", "chest", "lectern", "grindstone", "stonecutter",
              "brewing", "enchanting", "composter", "beacon", "conduit", "pickle", "lily", "kelp", "seagrass",
              "sugar_cane", "bamboo", "cactus", "azalea", "spore", "berry", "sculk_vein", "frame")


def _path(name):
    return name.split(":")[-1]


def full_id(name):
    return name if ":" in name else "minecraft:" + name


def is_empty(blk):
    return blk is None or _path(blk[0]) in AIR


def full_solid(blk):
    """A full, opaque cube face on every side: what a vine may hang on (glass, slabs, stairs, leaves, fences,
    Macaw's shaped blocks and anything small are not)."""
    if is_empty(blk):
        return False
    name, props = blk
    ns, _, p = name.rpartition(":")
    if ns.startswith("mcw"):
        return False
    if p.endswith("_slab"):
        return props.get("type") == "double"
    if p in PLANT_NAMES or p.endswith(THIN_SUFFIX):
        return False
    return not any(w in p for w in THIN_WORDS)


def collides(blk):
    """Blocks a unit's body: anything but air, plants, vines, carpets, torches, banners and signs."""
    if is_empty(blk):
        return False
    p = _path(blk[0])
    return not (p in PLANT_NAMES or p.endswith(PASS_SUFFIX))


def to_grid(root):
    if root.get("entities"):
        raise ValueError("overgrowth: the structure carries entities, which this tool cannot write back")
    pal = root["palette"]
    g = {}
    for b in root["blocks"]:
        if "nbt" in b:
            raise ValueError("overgrowth: block at %s carries block-entity NBT, which this tool cannot write back"
                             % (b["pos"],))
        st = pal[b["state"]]
        g[tuple(b["pos"])] = (full_id(st["Name"]), {k: str(v) for k, v in st.get("Properties", {}).items()})
    return g


def from_grid(g, size, data_version):
    palette, index, blocks = [], {}, []
    for pos in sorted(g, key=lambda p: (p[1], p[2], p[0])):
        name, props = g[pos]
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": name}
            if props:
                entry["Properties"] = dict(props)
            palette.append(entry)
        blocks.append({"pos": list(pos), "state": index[key]})
    return {"size": list(size), "palette": palette, "blocks": blocks, "entities": [], "DataVersion": data_version}


def write_nbt(root, path):
    sx, sy, sz = root["size"]
    s = Structure(sx, sy, sz)
    for (x, y, z), (name, props) in to_grid(root).items():
        s.set(x, y, z, name, **props)
    s.write(path)


# ---- walkable space ---------------------------------------------------------------------------------------------
PAD = UNIT_W + 2


def _windows(g, size):
    """(occupancy array, window array): win[x, y, z] = a 4x4 footprint with min corner (x - PAD, y, z - PAD) stands
    on something and has 8 clear blocks above every column. Outside the box the ground is y 0 and flat."""
    sx, sy, sz = size
    X, Y, Z = sx + 2 * PAD, sy + UNIT_H + 1, sz + 2 * PAD
    occ = np.zeros((X, Y, Z), bool)
    for (x, y, z), blk in g.items():
        if collides(blk):
            occ[x + PAD, y, z + PAD] = True
    support = occ.copy()
    support[:, 0, :] = True                       # y 0 is the ground (an empty y 0 cell in a sparse box keeps it)
    clear = np.zeros((X, Y, Z), bool)
    free = ~occ
    # free run of UNIT_H cells upward from y
    run = np.ones((X, Y, Z), bool)
    for k in range(UNIT_H):
        shifted = np.ones((X, Y, Z), bool)
        shifted[:, : Y - k, :] = free[:, k:, :]
        run &= shifted
    clear[:, 1:, :] = run[:, 1:, :] & support[:, :-1, :]
    view = np.lib.stride_tricks.sliding_window_view(clear, (UNIT_W, UNIT_W), axis=(0, 2))
    win = view.all(axis=(-2, -1))                  # shape (X-3, Y, Z-3)
    return win


def _reach(win, size):
    sx, sy, sz = size
    seeds = []
    WX, _, WZ = win.shape
    for x in range(WX):
        for z in range(WZ):
            outside = x + UNIT_W <= PAD or x >= PAD + sx or z + UNIT_W <= PAD or z >= PAD + sz
            if outside and win[x, 1, z]:
                seeds.append((x, 1, z))
    seen = set(seeds)
    todo = collections.deque(seeds)
    while todo:
        x, y, z = todo.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                n = (x + dx, y + dy, z + dz)
                if n in seen or not (0 <= n[0] < WX and 0 <= n[1] < win.shape[1] and 0 <= n[2] < WZ):
                    continue
                if win[n]:
                    seen.add(n)
                    todo.append(n)
    return seen


def reachable_windows(root_or_grid, size=None):
    """Every 4x4x8 unit footprint (min corner x, y, z in box coordinates; may lie outside the box) a unit can reach
    from outside the bounding box, stepping up or down one block at a time."""
    if size is None:
        g, size = to_grid(root_or_grid), root_or_grid["size"]
    else:
        g = root_or_grid
    return {(x - PAD, y, z - PAD) for x, y, z in _reach(_windows(g, size), size)}


class Passages:
    """Keeps every reachable footprint (or a neighbour of it) reachable while blocks are added."""

    def __init__(self, g, size):
        self.size = size
        self.r0 = reachable_windows(g, size)
        self._cover(self.r0)

    def _cover(self, r):
        self.covered = set()
        for x, y, z in r:
            for dx in range(UNIT_W):
                for dz in range(UNIT_W):
                    for dy in range(-1, UNIT_H):        # the support row below matters too
                        self.covered.add((x + dx, y + dy, z + dz))

    def try_place(self, g, pos, blk):
        """Place blk at pos if no passage closes; True if placed."""
        g[pos] = blk
        if pos not in self.covered:
            return True
        r1 = reachable_windows(g, self.size)
        ok = all(any((x + dx, y, z + dz) in r1 for dx in (-1, 0, 1) for dz in (-1, 0, 1)) for x, y, z in self.r0)
        if not ok:
            del g[pos]
            return False
        self._cover(r1 | self.r0)
        return True


# ---- the rules ----------------------------------------------------------------------------------------------------
def _throat(g, size, x, y, z):
    """Inside a door or arcade opening: walls on both sides along one axis within 6 blocks, open along the other,
    and a lintel above within 10. Vines and plants stay out (they would hang across the opening)."""
    sx, sy, sz = size

    def hit(dx, dz):
        for k in range(1, 7):
            px, pz = x + dx * k, z + dz * k
            if not (0 <= px < sx and 0 <= pz < sz):
                return None
            if collides(g.get((px, y, pz))):
                return k
        return None

    if not any(collides(g.get((x, y + k, z))) for k in range(1, 11)):
        return False
    for (a, b), (c, d) in (((-1, 0), (0, 1)), ((0, -1), (1, 0))):
        h1, h2 = hit(a, b), hit(-a, -b)
        bounded = h1 is not None and h2 is not None and h1 + h2 - 1 <= 6
        open_other = hit(c, d) is None or hit(-c, -d) is None
        if bounded and open_other:
            return True
    return False


def _in_box(size, x, y, z):
    sx, sy, sz = size
    return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz


def _vine_state(g, x, y, z):
    props = {}
    for f, (dx, dz) in DIRS.items():
        props[f] = "true" if full_solid(g.get((x + dx, y, z + dz))) else "false"
    props["up"] = "true" if full_solid(g.get((x, y + 1, z))) else "false"
    return props


def _leaf(kind):
    return ("minecraft:%s_leaves" % kind, {"distance": "1", "persistent": "true", "waterlogged": "false"})


class Budget:
    def __init__(self, total):
        self.left = {k: int(total * v) for k, v in SHARES.items()}
        self.total = int(total)
        self.used = 0

    def take(self, rule):
        if self.left[rule] <= 0 or self.used >= self.total:
            return False
        self.left[rule] -= 1
        self.used += 1
        return True


def _moss(g, size, rng, density, budget):
    chance = {0: 0.45, 1: 0.5, 2: 0.3, 3: 0.15}
    for pos in sorted(g, key=lambda p: (p[1], p[2], p[0])):
        x, y, z = pos
        name, props = g[pos]
        twin = MOSSY.get(_path(name))
        if twin is None or y > 3:
            continue
        open_sides = [f for f, (dx, dz) in DIRS.items() if is_empty(g.get((x + dx, y, z + dz)))]
        if not open_sides and not is_empty(g.get((x, y + 1, z))):
            continue                                       # buried: nobody sees it
        p = chance[y] * density * (1.3 if {"north", "west"} & set(open_sides) else 1.0)
        if rng.random() < p and budget.take("moss"):
            g[pos] = ("minecraft:" + twin, dict(props))


def _hang_vine(g, size, orig, rng, budget, x, y, z, length):
    placed = 0
    for k in range(length):
        yy = y - k
        if yy < 1 or not _in_box(size, x, yy, z) or not is_empty(g.get((x, yy, z))) or (x, yy, z) in orig:
            break
        state = _vine_state(g, x, yy, z)
        if not any(state[f] == "true" for f in DIRS) or _throat(g, size, x, yy, z):
            break
        if not budget.take("vine"):
            break
        g[(x, yy, z)] = ("minecraft:vine", state)
        placed += 1
    return placed


def _vines(g, size, orig, rng, density, budget):
    sx, sy, sz = size
    starts = []
    for x in range(sx):
        for z in range(sz):
            for y in range(1, sy):
                if not is_empty(g.get((x, y, z))) or (x, y, z) in orig:
                    continue
                walls = [f for f, (dx, dz) in DIRS.items() if full_solid(g.get((x + dx, y, z + dz)))]
                if not walls:
                    continue
                over = collides(g.get((x, y + 1, z)))
                top = any(not full_solid(g.get((x + DIRS[f][0], y + 1, z + DIRS[f][1]))) for f in walls)
                if not (over or top):
                    continue
                w = max(FACE_WEIGHT[f] for f in walls)
                starts.append(((x, y, z), w * (1.5 if over else 1.0) * (0.5 + 0.5 * _low(y, sy))))
    for (x, y, z), w in starts:
        if rng.random() < min(0.95, 0.5 * w * density):
            length = rng.choices(*VINE_LENGTHS)[0]
            _hang_vine(g, size, orig, rng, budget, x, y, z, length)


def _low(y, sy):
    """1 at the ground, falling to ~0.1 at the top: growth gathers low, on the base and the first terraces (the
    reference's crowns stay mostly clean plaster and roof), so a tall building is not wrapped to its spire."""
    return max(0.1, 1.0 - 1.1 * y / max(1, sy))


def _standing(g, x, y, z):
    below = g.get((x, y - 1, z))
    return y >= 1 and is_empty(g.get((x, y, z))) and (collides(below) and _path(below[0]) not in PLANT_NAMES)


def _bushes(g, size, orig, rng, density, budget, passages):
    sx, sy, sz = size
    kinds = (["spruce"] * 3) + ["dark_oak"]
    cands = []
    for y in range(1, sy):
        for z in range(sz):
            for x in range(sx):
                if (x, y, z) in orig or not _standing(g, x, y, z) or _throat(g, size, x, y, z):
                    continue
                walls = [f for f, (dx, dz) in DIRS.items() if collides(g.get((x + dx, y, z + dz)))]
                if walls:
                    w = max(FACE_WEIGHT[f] for f in walls) * (1.6 if len(walls) >= 2 else 1.0)
                    cands.append(((x, y, z), w * _low(y, sy)))
    for (x, y, z), w in cands:
        if rng.random() >= min(0.9, 0.42 * w * density):
            continue
        kind = rng.choice(kinds)
        if not (is_empty(g.get((x, y, z))) and budget.take("bush")):
            continue
        if not passages.try_place(g, (x, y, z), _leaf(kind)):
            budget.left["bush"] += 1
            budget.used -= 1
            continue
        # grow the clump: up against the wall, or along it
        for _ in range(rng.choice([0, 0, 1, 1, 2])):
            options = [(x, y + 1, z)] + [(x + dx, y, z + dz) for dx, dz in DIRS.values()]
            nx, ny, nz = rng.choice(options)
            if not _in_box(size, nx, ny, nz) or (nx, ny, nz) in orig or not is_empty(g.get((nx, ny, nz))):
                continue
            if ny == y and not _standing(g, nx, ny, nz):
                continue
            if ny > y and not any(collides(g.get((nx + dx, ny, nz + dz))) for dx, dz in DIRS.values()):
                continue                                   # stack up only against a wall, never a free leaf pillar
            if _throat(g, size, nx, ny, nz) or not budget.take("bush"):
                continue
            if not passages.try_place(g, (nx, ny, nz), _leaf(kind)):
                budget.left["bush"] += 1
                budget.used -= 1


def _ledges(g, size, orig, rng, density, budget, passages):
    """Leaves on the edge of a terrace, wall top or stair run, spilling over, with a vine draped below."""
    sx, sy, sz = size
    cands = []
    for y in range(2, int(0.7 * sy)):              # terraces, not the spire: crowns stay clean (the reference's roofs)
        for z in range(sz):
            for x in range(sx):
                if (x, y, z) in orig or not _standing(g, x, y, z):
                    continue
                drops = [f for f, (dx, dz) in DIRS.items()
                         if is_empty(g.get((x + dx, y, z + dz))) and is_empty(g.get((x + dx, y - 1, z + dz)))]
                if drops:
                    cands.append(((x, y, z), drops))
    for (x, y, z), drops in cands:
        if rng.random() >= 0.3 * _low(y, sy) * density or not is_empty(g.get((x, y, z))):
            continue
        pot = rng.random() < 0.12
        blk = (("minecraft:potted_fern", {}) if pot else _leaf(rng.choice(["spruce", "spruce", "dark_oak"])))
        if not budget.take("ledge"):
            return
        if not passages.try_place(g, (x, y, z), blk):
            budget.left["ledge"] += 1
            budget.used -= 1
            continue
        if pot:
            continue
        f = rng.choice(drops)
        dx, dz = DIRS[f]
        # the vine hangs in the open cell beside the ledge, on the ledge's own face (one below the leaves)
        _hang_vine(g, size, orig, rng, budget, x + dx, y - 1, z + dz, rng.choices(*VINE_LENGTHS)[0])


def _plants(g, size, orig, rng, density, budget):
    sx, sy, sz = size
    for y in range(1, sy):
        for z in range(sz):
            for x in range(sx):
                below = g.get((x, y - 1, z))
                if (x, y, z) in orig or not is_empty(g.get((x, y, z))) or below is None or _path(below[0]) not in SOIL:
                    continue
                if _throat(g, size, x, y, z):
                    continue
                near = any(collides(g.get((x + dx, y, z + dz))) for dx, dz in DIRS.values())
                if rng.random() < (0.55 if near else 0.12) * density and budget.take("plant"):
                    g[(x, y, z)] = ("minecraft:fern" if rng.random() < 0.6 else "minecraft:grass", {})


def overgrow(root, seed=0, density=1.0, budget=BUDGET):
    """An overgrown copy of root ({"size", "palette", "blocks", ...} as preview_structure.load_nbt reads it)."""
    size = tuple(root["size"])
    g = to_grid(root)
    orig = set(g)
    rng = random.Random(seed)
    b = Budget(budget * len(root["blocks"]))
    passages = Passages(g, size)
    _moss(g, size, rng, density, b)
    _bushes(g, size, orig, rng, density, b, passages)
    _ledges(g, size, orig, rng, density, b, passages)
    _vines(g, size, orig, rng, density, b)
    _plants(g, size, orig, rng, density, b)
    return from_grid(g, size, root.get("DataVersion", 3465))


def summary(before, after):
    g0, g1 = to_grid(before), to_grid(after)
    add = collections.Counter(n for p, (n, _) in g1.items() if p not in g0)
    swap = collections.Counter(n for p, (n, _) in g1.items() if p in g0 and g0[p][0] != n)
    return len(g0), len(g1), add, swap


def main():
    ap = argparse.ArgumentParser(description="An overgrown copy of a structure .nbt (vines, moss, dark bushes).")
    ap.add_argument("inp", nargs="?")
    ap.add_argument("out", nargs="?")
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--density", type=float, default=1.0)
    ap.add_argument("--budget", type=float, default=BUDGET)
    ap.add_argument("--pilot", action="store_true", help="write design/eyes/overgrowth_pilot_{before,after}.nbt")
    a = ap.parse_args()
    if a.pilot:
        shutil.copyfile(PILOT_SOURCE, PILOT_BEFORE)
        a.inp, a.out, a.seed, a.density = PILOT_BEFORE, PILOT_AFTER, PILOT_SEED, PILOT_DENSITY
    if not (a.inp and a.out):
        ap.error("give IN.nbt OUT.nbt, or --pilot")
    before = load_nbt(a.inp)
    after = overgrow(before, a.seed, a.density, a.budget)
    write_nbt(after, a.out)
    n0, n1, add, swap = summary(before, after)
    print("blocks %d -> %d; added %s; mossy swaps %d" % (n0, n1, dict(add.most_common()), sum(swap.values())))


if __name__ == "__main__":
    main()
