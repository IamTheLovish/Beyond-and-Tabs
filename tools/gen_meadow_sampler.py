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
import collections
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


def vnoise(x, z, scale, salt, ang=0.0, ox=0.0, oz=0.0):
    """Smoothstep value noise on a square lattice of cell `scale`. r35: the lattice turned by `ang` degrees and shifted
    by (ox, oz) cells (0 / 0 / 0 = the r31-r34 lattice, unchanged). A square lattice's gradient energy is ~2x higher
    along its axes than along its diagonals (bug hunt r34: clump field 0.318 axis / 0.168 diagonal), which the 45-degree
    RTS camera shows as diagonal streaks; octaves turned 22.5 and 67.5 degrees spread it evenly."""
    if ang:
        ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
        fx, fz = (x * ca - z * sa + ox) / scale, (x * sa + z * ca + oz) / scale
    else:
        fx, fz = x / scale, z / scale
    x0, z0 = math.floor(fx), math.floor(fz)
    tx, tz = fx - x0, fz - z0
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a, b = hash01(x0, z0, salt), hash01(x0 + 1, z0, salt)
    c, d = hash01(x0, z0 + 1, salt), hash01(x0 + 1, z0 + 1, salt)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def plant_at(x, z):
    """An index into the plant list, or None. r31 pilot (critic r31: "wallpaper scatter - identical tufts at a uniform
    ~45%, every block"; in game each tuft reads as its own dark shadow star): clumps, not a scatter. Inside a meadow,
    tufts grow in clusters of ~4-7 blocks (a 5-block noise) at ~90%, with bare lawn between them (4%); the clusters'
    shadows merge into darker clumps instead of polka dots. Cover inside a meadow ~35%, about the old total.
    r35: kept as it was for gen_ground_sampler (its plants); the meadow sampler places plant_at_r35."""
    meadow = 0.65 * vnoise(x, z, 11.0, 1) + 0.35 * vnoise(x, z, 4.0, 2)
    dense = max(0.0, min(1.0, (meadow - 0.22) / 0.45))
    clump = vnoise(x, z, 5.0, 7) * 0.75 + 0.25 * vnoise(x, z, 2.0, 8)
    pick = hash01(x, z, 3)
    inside = clump > 0.62 - 0.22 * dense                              # bigger, more clumps where the meadow is lush
    if pick < (0.9 * min(1.0, 0.35 + dense) if inside else 0.04):
        h = hash01(x, z, 4)
        if meadow > 0.62 and inside and h < 0.14:
            return "large_fern" if h < 0.03 else "tall_grass"
        return "fern" if h < 0.16 else "grass"
    if vnoise(x, z, 13.0, 5) > 0.68 and pick > 0.93:
        return ("poppy", "dandelion", "oxeye_daisy", "azure_bluet", "cornflower")[int(hash01(x, z, 6) * 5)]
    return None


# r35: the octaves' lattice turns (degrees) and shifts (cells) - bug hunt r34: 22.5 and 67.5 put every lattice axis
# 22.5 degrees from both the world axes and the diagonals; distinct shifts so no two octaves share a lattice corner
TURNS = {"m11": (22.5, 0.0, 0.0), "m4": (67.5, 7.3, 3.1), "c5": (22.5, 1.7, 9.2), "c2": (67.5, 4.4, 0.6)}
CLUMP = (0.50, 0.26)    # r35: a block is in a clump where clump > CLUMP[0] - CLUMP[1] x the meadow's lushness


def fields(x, z, turned=True):
    """(meadow, clump) noise at a block: the meadow's lushness (two octaves, 11 and 4 blocks) and the clumps (5 and 2)."""
    t = TURNS if turned else {k: (0.0, 0.0, 0.0) for k in TURNS}
    meadow = 0.65 * vnoise(x, z, 11.0, 1, *t["m11"]) + 0.35 * vnoise(x, z, 4.0, 2, *t["m4"])
    clump = 0.75 * vnoise(x, z, 5.0, 7, *t["c5"]) + 0.25 * vnoise(x, z, 2.0, 8, *t["c2"])
    return meadow, clump


def plant_at_r35(x, z):
    """r35 (critic r34, tufts 5/10: "sparse, bare lawn between clumps... double tuft density"): toward a field, still
    in clumps (r31's clumping cut the dark shadow stars). The clumps bigger (threshold 0.62 - 0.22 dense -> 0.50 -
    0.26 dense) and fuller (0.9 x min(1, 0.35 + dense) -> 0.97 x min(1, 0.6 + dense)), and a thin scatter of single
    tufts between them (4% -> 20%), so the lawn between clumps is no longer bare; ~a quarter of the lawn stays open in
    clearings. On this patch tuft cover goes 39% -> 72% of the open lawn (summary()). Every octave on a turned
    lattice (TURNS): no diagonal streaks from the RTS camera. Same plant mix as plant_at."""
    meadow, clump = fields(x, z)
    dense = max(0.0, min(1.0, (meadow - 0.22) / 0.45))
    pick = hash01(x, z, 3)
    inside = clump > CLUMP[0] - CLUMP[1] * dense
    if pick < (0.97 * min(1.0, 0.6 + dense) if inside else 0.20):
        h = hash01(x, z, 4)
        if meadow > 0.62 and inside and h < 0.14:
            return "large_fern" if h < 0.03 else "tall_grass"
        return "fern" if h < 0.16 else "grass"
    if vnoise(x, z, 13.0, 5) > 0.68 and pick > 0.93:
        return ("poppy", "dandelion", "oxeye_daisy", "azure_bluet", "cornflower")[int(hash01(x, z, 6) * 5)]
    return None


def build(plant_fn=None):
    """The sampler: (structure, {(x, z): plant}, the open lawn's blocks - not path, not under a tree / bush / boulder)."""
    plant_fn = plant_fn or plant_at_r35
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
                s.set(x, 0, z, "dirt_path" if rr < 0.15 else "coarse_dirt" if rr < 0.7 else "dirt")   # r3
    occupied = set(path)
    rnd = random.Random(28)
    # r27 oak pilot: a spreading oak and an old twin-stemmed oak - two of the pilot's three forms in one still
    pieces = [(gen_trees.oak(3, form="spread"), 31, 8), (gen_trees.oak(11, old=True, form="twin"), 6, 28),
              (gen_trees.birch(5), 36, 33),
              (gen_trees.bush(2), 22, 38), (gen_trees.boulder(4), 14, 12)]
    for piece, ox, oz in pieces:
        gen_trees._write_piece(s, piece, ox, oz, lift=1)
        for dx in range(gen_trees.SIZE):
            for dz in range(gen_trees.SIZE):
                if abs(dx - gen_trees.SIZE // 2) <= 2 and abs(dz - gen_trees.SIZE // 2) <= 2:
                    occupied.add((ox + dx, oz + dz))
    rnd.random()
    placed = {}
    for x in range(N):
        for z in range(N):
            if (x, z) in occupied:
                continue
            p = plant_fn(x, z)
            if p is None:
                continue
            placed[(x, z)] = p
            if p in ("tall_grass", "large_fern"):
                s.set(x, 1, z, p, half="lower")
                s.set(x, 2, z, p, half="upper")
            else:
                s.set(x, 1, z, p)
    lawn = [(x, z) for x in range(N) for z in range(N) if (x, z) not in occupied]
    return s, placed, lawn


TUFTS = ("grass", "fern", "tall_grass", "large_fern")


def _grad_share(f, half=10.0):
    """Shares of a field's gradient energy pointing within +-half degrees of the axes / of the diagonals (the bug
    hunt's measure; central differences here, where the bug hunt used a Sobel filter)."""
    import numpy as np
    gx, gz = np.gradient(f)
    ang = np.degrees(np.arctan2(gz, gx)) % 180
    w = np.hypot(gx, gz) ** 2

    def share(cs):
        m = np.zeros(ang.shape, bool)
        for c in cs:
            m |= np.abs(((ang - c) + 90) % 180 - 90) < half
        return float(w[m].sum() / w.sum())
    return share((0, 90)), share((45, 135))


def summary(placed, lawn):
    """Cover of the open lawn, r34's plant_at on the same patch beside it, and the noise fields' axis / diagonal
    gradient energy on the old and the turned lattices (128 x 128 blocks at a 0.5 step)."""
    import numpy as np
    out = {}
    for name, fn in (("r34 plant_at", plant_at), ("r35 plant_at_r35", plant_at_r35)):
        got = placed if fn is plant_at_r35 else {(x, z): fn(x, z) for x, z in lawn}
        kinds = collections.Counter(p for p in got.values() if p is not None)
        tufts = sum(kinds[k] for k in TUFTS)
        out[name] = tufts
        print("%-17s tufts %4d = %4.1f%% of the %d open lawn blocks (%s; flowers %d)" % (
            name, tufts, 100.0 * tufts / len(lawn), len(lawn), ", ".join("%s %d" % (k, kinds[k]) for k in TUFTS),
            sum(v for k, v in kinds.items() if k not in TUFTS)))
    inside = 0
    for x, z in lawn:
        meadow, clump = fields(x, z)
        inside += clump > CLUMP[0] - CLUMP[1] * max(0.0, min(1.0, (meadow - 0.22) / 0.45))
    print("r35 clumps cover %.1f%% of the open lawn; tuft cover x%.2f vs r34" % (
        100.0 * inside / len(lawn), out["r35 plant_at_r35"] / max(1, out["r34 plant_at"])))
    g = np.arange(0, 128, 0.5)
    for turned in (False, True):
        mf = np.zeros((len(g), len(g)))
        cf = np.zeros_like(mf)
        for i, x in enumerate(g):
            for j, z in enumerate(g):
                mf[i, j], cf[i, j] = fields(x, z, turned)
        print("%s lattice: clump field axis/diag %.3f/%.3f | meadow field axis/diag %.3f/%.3f" % (
            ("turned (r35)" if turned else "square (r34)",) + _grad_share(cf) + _grad_share(mf)))


def main():
    s, placed, lawn = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    s.write(OUT)
    print("wrote", OUT)
    summary(placed, lawn)


if __name__ == "__main__":
    main()
