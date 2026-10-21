#!/usr/bin/env python3
"""Branch wind (design/branch_wind.md): rewrite a tree template's limbs as sway blocks.

    python3 tools/sway_trees.py design/trees/oak_1.nbt [...] -o design/trees/sway      # <name>.nbt per input
    python3 tools/sway_trees.py design/trees/*.nbt --check                            # classify, print, write nothing

For every `minecraft:<wood>_wood` / `<wood>_log` / `<wood>_fence` block that is not trunk or root, the output holds
`reignofnether:sway_<wood>_wood` / `sway_<wood>_fence` with the same properties plus `sway=<tier>` (1 next to the
trunk .. 4 the outer twigs; environment/wind/SwayBlocks). Leaves, plants, ground and the trunk are copied unchanged, so
a tree placed from the output fells, pays and blocks exactly as the input (the sway blocks are in #minecraft:logs).

A post-processor rather than a flag in tools/gen_trees.py (PR #88, still in review rounds): it works on any tree
template, generated or hand-built, and the two branches never touch the same file. When #88 lands, gen_trees.py can
call `swayify()` from here under a --sway flag in one line.

The tier (section 5 of the design):
  trunk  = the wood column over the foot (a 2x2 foot included) plus, for a leaning trunk, the blocks beside it that have
           trunk straight above or below; roots = wood at y <= 1 within 3 of the column. Both tier 0: untouched vanilla.
  reach  = steps from the trunk through wood/fence blocks (26-neighbourhood).
  score  = reach + 0.15 x (height above the first limb); tier = clamp(ceil(score / 1.6), 1, 4); a fence (twig) is at
           least tier 3 - the thin tips always move most.
"""
import math
import os
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_structures import Structure                  # noqa: E402
from preview_structure import load_nbt                # noqa: E402

WOODS = ("oak", "birch", "spruce", "acacia", "cherry", "dark_oak")
MAX_TIER = 4
NB26 = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]


def _species(name):
    """('oak', 'wood') for minecraft:oak_wood / oak_log, ('oak', 'fence') for oak_fence, else None."""
    if name.startswith("minecraft:"):
        name = name[len("minecraft:"):]
    for w in WOODS:
        if name in (w + "_wood", w + "_log"):
            return w, "wood"
        if name == w + "_fence":
            return w, "fence"
    return None


def classify(blocks):
    """blocks: {(x, y, z): (name, props)}. Returns {(x, y, z): tier} for every wood/log/fence block (0 = trunk/root)."""
    limbs = {p: _species(n) for p, (n, _) in blocks.items() if _species(n)}
    if not limbs:
        return {}
    wood_only = [p for p, s in limbs.items() if s[1] == "wood"]
    y0 = min(p[1] for p in wood_only) if wood_only else min(p[1] for p in limbs)
    # the trunk column: the (x, z) with the tallest stack of wood; a 2x2 trunk has four such columns side by side, and
    # the centre is their centroid (roots and limbs are short stacks, so they never pull it)
    runs = {}
    for (x, y, z) in wood_only:
        runs[(x, z)] = runs.get((x, z), 0) + 1
    best = max(runs, key=runs.get) if runs else (0, 0)
    cols = [c for c, n in runs.items() if n >= max(2, 0.6 * runs[best]) and max(abs(c[0] - best[0]), abs(c[1] - best[1])) <= 1]
    cx = sum(c[0] for c in cols) / len(cols)
    cz = sum(c[1] for c in cols) / len(cols)
    # the column: every block over the foot's centre (a 2x2 foot centres between its four blocks: all four are within
    # 0.5 of it). A leaning trunk steps sideways with a filled corner (gen_trees.line): a wood block beside the column
    # with a trunk block straight above or below it is trunk too, repeated to a fixpoint. A spruce whorl's stub beside
    # the column has no trunk block over or under it, so it stays a limb (tier 1 - a stub barely moves anyway).
    trunk = {p for p in limbs if max(abs(p[0] - cx), abs(p[2] - cz)) <= 0.51}
    near = [p for p in limbs if limbs[p][1] == "wood" and max(abs(p[0] - cx), abs(p[2] - cz)) <= 1.01]
    while True:
        add = {p for p in near if p not in trunk
               and ((p[0], p[1] + 1, p[2]) in trunk or (p[0], p[1] - 1, p[2]) in trunk)}
        if not add:
            break
        trunk |= add
    roots = {p for p in limbs if p[1] <= y0 + 1 and max(abs(p[0] - cx), abs(p[2] - cz)) <= 3.01}
    base = trunk | roots
    # reach: BFS through limb blocks from the trunk
    reach = {p: 0 for p in base}
    q = deque(base)
    while q:
        p = q.popleft()
        for dx, dy, dz in NB26:
            n = (p[0] + dx, p[1] + dy, p[2] + dz)
            if n in limbs and n not in reach:
                reach[n] = reach[p] + 1
                q.append(n)
    out = {p: 0 for p in base}
    limb_blocks = [p for p in limbs if p not in base]
    if not limb_blocks:
        return out
    joined = [p for p in limb_blocks if p in reach]
    first_limb_y = min(p[1] for p in joined) if joined else y0
    for p in limb_blocks:
        r = reach.get(p)
        if r is None:                              # a wood block not joined to the trunk (a fallen piece): leave it
            out[p] = 0
            continue
        score = r + 0.15 * max(0, p[1] - first_limb_y)
        tier = max(1, min(MAX_TIER, math.ceil(score / 1.6)))
        if limbs[p][1] == "fence":
            tier = max(3, tier)
        out[p] = tier
    return out


def swayify(blocks):
    """Returns a new {(x, y, z): (name, props)} with the limb blocks rewritten as sway blocks."""
    tiers = classify(blocks)
    out = {}
    for p, (name, props) in blocks.items():
        t = tiers.get(p, 0)
        if t > 0:
            w, kind = _species(name)
            props = dict(props)
            if kind == "wood" and "axis" not in props:
                props["axis"] = "y"
            props["sway"] = str(t)
            out[p] = ("reignofnether:sway_%s_%s" % (w, kind), props)
        else:
            out[p] = (name, props)
    return out


def read_blocks(path):
    root = load_nbt(path)
    palette = root.get("palette") or (root.get("palettes") or [[]])[0]
    names = [p.get("Name", "minecraft:air") for p in palette]
    props_of = [p.get("Properties", {}) for p in palette]
    blocks = {}
    for b in root.get("blocks", []):
        x, y, z = b["pos"]
        blocks[(x, y, z)] = (names[b["state"]], dict(props_of[b["state"]]))
    return blocks, tuple(root["size"])


def write_blocks(path, blocks, size):
    s = Structure(*size)
    for (x, y, z), (name, props) in blocks.items():
        if name in ("minecraft:structure_void", "structure_void"):
            continue
        s.set(x, y, z, name, **props)
    s.write(path)


def main(argv):
    out_dir, check, files = None, False, []
    i = 0
    while i < len(argv):
        if argv[i] == "-o":
            out_dir = argv[i + 1]; i += 2
        elif argv[i] == "--check":
            check = True; i += 1
        else:
            files.append(argv[i]); i += 1
    if not files or (out_dir is None and not check):
        print(__doc__)
        return 2
    total = 0
    for f in files:
        blocks, size = read_blocks(f)
        tiers = classify(blocks)
        counts = [sum(1 for t in tiers.values() if t == k) for k in range(MAX_TIER + 1)]
        n = sum(counts[1:])
        total += n
        print("%-28s trunk/root %3d  limbs %3d  by tier %s" % (os.path.basename(f), counts[0], n, counts[1:]))
        if not check:
            os.makedirs(out_dir, exist_ok=True)
            write_blocks(os.path.join(out_dir, os.path.basename(f)), swayify(blocks), size)
    print("%d sway blocks over %d trees (%.1f per tree)" % (total, len(files), total / max(1, len(files))))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
