#!/usr/bin/env python3
"""A meadow sampler for the eyes `structure` lane: concept C's ground as a player sees it - the clover lawn (the grass
block's texture), soft patches of tall meadow grass, short tufts and daisies, a worn dirt path crossing it and two
bare-soil spots - on one 48 x 48 patch, so a run shows how the plants blend into the open clover, the trail and the
soil. The tour's flat map has no plants (and no path), so neither the patches nor the blending can be judged there.

    python3 tools/gen_meadow_sampler.py     # writes design/eyes/meadow_sampler.nbt
    gh api -X POST repos/IamTheLovish/Beyond-and-Tabs/actions/workflows/eyes.yml/dispatches -f ref=<branch> \\
        -f 'inputs[structure]=design/eyes/meadow_sampler.nbt' -f 'inputs[shaders]=true' -f 'inputs[shader_pack]=ours'

The plants are the pre-made maps' own: tools/meadow_patches.py (the bit-exact mirror of maps/MeadowPatches.java)
over a 48 x 48 window of Verdant Valley's slot frame (seed 0), with the path and the soil spots as readability zones
- tall grass keeps off them exactly as it keeps off a start lane or a capture site in game, while short tufts and the
odd flower grow right up to their edges. Ported from PR #323 (never merged), where the plants copied the Oct 16 noise.
Deterministic."""
import collections
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meadow_patches as MP  # noqa: E402
from gen_structures import Structure  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "design", "eyes", "meadow_sampler.nbt")
N, HGT = 48, 4
# the window: Verdant Valley seed 0, slot frame u 108..156, v 106..154 - beyond the river, 36% of it in patches
PRESET, SEED, U0, V0 = MP.VERDANT_VALLEY, 0, 108, 106
SOIL = ((41, 17, 2.6), (12, 37, 2.2))     # bare-soil spots: centre x, z and radius (in a patch, on an edge)


def hash01(x, z, salt):
    h = (x * 374761393 + z * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def ground():
    """{(x, z): block} for the path and the soil spots; everything else is grass block."""
    out = {}
    for x in range(N):                     # a worn path, wandering west to east through two patches and the clover
        cz = int(round(x * 0.45 + 18 + 3 * math.sin(x / 7.0)))
        for w in range(-1, 3):                                    # a 2-wide core, feathered sides
            z = cz + w
            rr = hash01(x, z, 9)
            if w in (-1, 2) and rr > 0.45:
                continue
            if 0 <= z < N:
                out[(x, z)] = "dirt_path" if rr < 0.35 else "coarse_dirt"
    for sx, sz, r in SOIL:                                        # dark soil, a ragged round
        for x in range(N):
            for z in range(N):
                d = math.hypot(x - sx, z - sz) + 0.9 * (hash01(x, z, 11) - 0.5)
                if d < r:
                    out[(x, z)] = "dirt" if hash01(x, z, 12) < 0.7 else "rooted_dirt"
    return out


def clearance(bare, x, z):
    """Chebyshev blocks to the nearest path or soil block, the way MeadowPatches.clearance measures a keep-out."""
    best = math.inf
    for (bx, bz) in bare:
        best = min(best, max(abs(bx - x), abs(bz - z)))
    return best


def build():
    """The sampler: (structure, {(x, z): plant kind}, the lawn's blocks)."""
    s = Structure(N, HGT, N)
    bare = ground()
    for x in range(N):
        for z in range(N):
            s.set(x, 0, z, bare.get((x, z), "grass_block"), **({} if (x, z) in bare else {"snowy": "false"}))
    layout, mix = MP.Layout(PRESET, SEED), MP.MIXES[PRESET]
    placed = {}
    for x in range(N):
        for z in range(N):
            if (x, z) in bare:
                continue
            kind = MP.kind_at(mix, layout, U0 + x + 0.25, V0 + z + 0.5, clearance(bare, x, z))
            if kind == MP.NONE:
                continue
            placed[(x, z)] = kind
            name = MP.NAMES[kind]
            if kind in (MP.TALL, MP.LARGE_FERN):
                s.set(x, 1, z, name, half="lower")
                s.set(x, 2, z, name, half="upper")
            else:
                s.set(x, 1, z, name)
    lawn = [(x, z) for x in range(N) for z in range(N) if (x, z) not in bare]
    return s, placed, lawn, bare


def summary(placed, lawn, bare):
    kinds = collections.Counter(MP.NAMES[k] for k in placed.values())
    layout = MP.Layout(PRESET, SEED)
    patch = sum(MP.field(layout, U0 + x + 0.25, V0 + z + 0.5) > MP.T for x, z in lawn)
    near = [(x, z) for x, z in lawn if clearance(bare, x, z) <= MP.TALL_CLEAR]
    print("%d lawn blocks, %d path / soil; %.0f%% of the lawn in patches; plants on %.0f%%" % (
        len(lawn), len(bare), 100.0 * patch / len(lawn), 100.0 * len(placed) / len(lawn)))
    print("  " + ", ".join("%s %d" % kv for kv in sorted(kinds.items(), key=lambda kv: -kv[1])))
    print("  within %d blocks of the path / soil: %d lawn blocks, %d plants, %d tall" % (
        int(MP.TALL_CLEAR), len(near), sum(p in placed for p in near),
        sum(placed.get(p) in (MP.TALL, MP.LARGE_FERN) for p in near)))
    return kinds


# close-up points for EyesDirector.readNearPoints (name x y z, structure-local; the lawn's top is y 1): the blends
NEAR = (("path_through_patch", 8.5, 24.5), ("path_into_patch", 42.5, 36.5), ("soil_in_patch", 41.5, 17.5),
        ("soil_patch_edge", 12.5, 37.5), ("open_clover", 25.5, 17.5), ("patch_core", 22.5, 4.5))


def main():
    s, placed, lawn, bare = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    s.write(OUT)
    with open(OUT + ".near", "w") as f:
        f.write("# close-up points for EyesDirector (name x y z, structure-local) - tools/gen_meadow_sampler.py\n")
        for name, x, z in NEAR:
            f.write("%s %.2f 1.00 %.2f\n" % (name, x, z))
    print("wrote", OUT, "and", OUT + ".near")
    summary(placed, lawn, bare)


if __name__ == "__main__":
    main()
