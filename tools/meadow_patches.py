#!/usr/bin/env python3
"""A bit-exact Python mirror of maps/MeadowPatches.java (the pre-made maps' meadow patches, concept C, Nov 4 2026),
for the offline tools: the meadow sampler (tools/gen_meadow_sampler.py) and the top-down patch map. Java is the
source of truth; tools/test_meadow_patches.py and MeadowPatchesTest both check bar/src/test/resources/maps/
meadow_patch_golden.txt (the plants of two windows of real maps), so the two can't drift apart.

    python3 tools/meadow_patches.py --golden    # rewrite the golden file from this mirror (after changing BOTH)

Everything here copies MapLayout.hash / decorHash / mixSeed with Java's 64-bit wrap-around, and every float
expression in the same order as the Java, so the same doubles come out."""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GOLDEN = os.path.join(ROOT, "bar", "src", "test", "resources", "maps", "meadow_patch_golden.txt")

# indexes into MapBuilder.PLANTS (MeadowPatches' constants)
NONE, GRASS, TALL, FERN, LARGE_FERN, POPPY, DANDELION, DAISY, BLUET, CORNFLOWER, ALLIUM = range(-1, 10)
NAMES = {GRASS: "grass", TALL: "tall_grass", FERN: "fern", LARGE_FERN: "large_fern", POPPY: "poppy",
         DANDELION: "dandelion", DAISY: "oxeye_daisy", BLUET: "azure_bluet", CORNFLOWER: "cornflower",
         ALLIUM: "allium"}
CHARS = {NONE: ".", GRASS: "g", TALL: "T", FERN: "f", LARGE_FERN: "F", POPPY: "p", DANDELION: "y", DAISY: "d",
         BLUET: "b", CORNFLOWER: "c", ALLIUM: "a"}
FLOWERS = (POPPY, DANDELION, DAISY, BLUET, CORNFLOWER, ALLIUM)

# MapPreset ordinals (the enum's order)
ARCHIPELAGO, HIGHLANDS, VERDANT_VALLEY, DESERT_MESA = 1, 2, 5, 6

M64 = (1 << 64) - 1


def _s64(x):
    x &= M64
    return x - (1 << 64) if x >> 63 else x


def _ushr(x, n):
    return (x & M64) >> n


def mix_seed(seed):
    """MapLayout.mixSeed (SplitMix64's finaliser; 0 stays 0)."""
    if seed == 0:
        return 0
    z = _s64(seed + 0x9E3779B97F4A7C15)
    z = _s64((z ^ _ushr(z, 30)) * 0xBF58476D1CE4E5B9)
    z = _s64((z ^ _ushr(z, 27)) * 0x94D049BB133111EB)
    return _s64(z ^ _ushr(z, 31))


def jround(x):
    """Java's Math.round(double) for the values we meet: floor(x + 1/2) taken exactly."""
    r = math.floor(x)
    return r + 1 if x - r >= 0.5 else r


class Layout:
    """The two MapLayout methods the patches read: hash and decorHash (a function of preset and seed only)."""

    def __init__(self, ordinal, seed=0):
        self.ordinal = ordinal
        self.seed_mix = mix_seed(seed)

    def hash(self, x, y):
        h = _s64(x * 73856093) ^ _s64(y * 19349663) ^ _s64((self.ordinal + 1) * 83492791) ^ self.seed_mix
        h = _s64(h ^ _ushr(h, 13))
        h = _s64(h * 0x5BD1E995)
        h = _s64(h ^ _ushr(h, 15))
        return ((h & 0xFFFF) / 32767.5) - 1

    def decor_hash(self, u, v):
        return (self.hash(jround(u), jround(v) + 7919) + 1) / 2


# ---------------------------------------------------------------------------------------------- the Java constants
C22, S22 = 0.9238795325112867, 0.3826834323650898     # cos / sin 22.5 degrees
C67, S67 = 0.3826834323650898, 0.9238795325112867     # cos / sin 67.5 degrees
T = 0.60           # patch where the field is above T (its edge's middle): ~31% of open ground
E = 0.06           # half the soft edge, in field units (~1.1 blocks each side of T)
H = 0.08           # the fringe of single tufts outside the edge, in field units (~1.5 blocks)
CORE_DENSITY = 0.9
EDGE_DENSITY = 0.25
FRINGE_DENSITY = 0.22
TALL_FROM, TALL_SPAN = 0.3, 0.6
TALL_CLEAR, TALL_RAMP = 2.0, 2.0
FLOWER_CORE, FLOWER_EDGE, FLOWER_FRINGE, FLOWER_OPEN = 1.0, 1.4, 0.45, 0.04
CLUSTER_FROM, CLUSTER_LOW = 0.55, 0.3


class Mix:
    def __init__(self, tall, fern, flower, flowers):
        self.tall, self.fern, self.flower, self.flowers = tall, fern, flower, flowers


def _table(*pairs):
    out = []
    for kind, n in pairs:
        out += [kind] * n
    return tuple(out)


MIXES = {
    VERDANT_VALLEY: Mix(0.55, 0.04, 0.10, _table((DAISY, 10), (DANDELION, 6), (BLUET, 2), (POPPY, 1), (CORNFLOWER, 1))),
    HIGHLANDS: Mix(0.35, 0.30, 0.07, _table((DAISY, 8), (DANDELION, 5), (CORNFLOWER, 3), (BLUET, 2), (ALLIUM, 2))),
    ARCHIPELAGO: Mix(0.45, 0.05, 0.08, _table((DAISY, 9), (DANDELION, 7), (POPPY, 2), (BLUET, 2))),
    DESERT_MESA: Mix(0.45, 0.10, 0.06, _table((DANDELION, 6), (DAISY, 4))),
}


def _noise(l, x, y, salt):
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    a, b = l.decor_hash(x0 + salt, y0), l.decor_hash(x0 + 1 + salt, y0)
    c, d = l.decor_hash(x0 + salt, y0 + 1), l.decor_hash(x0 + 1 + salt, y0 + 1)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def field(l, u, v):
    """MeadowPatches.field: the patch field in [0, 1]."""
    a = _noise(l, (u * C22 - v * S22) / 7.0, (u * S22 + v * C22) / 7.0, 1100)
    b = _noise(l, (u * C67 - v * S67) / 2.5, (u * S67 + v * C67) / 2.5, 1300)
    c = _noise(l, u / 20.0, v / 20.0, 1500)
    return 0.8 * a + 0.12 * b + 0.08 * c


def cluster(l, u, v):
    return _noise(l, (u * C67 - v * S67) / 3.0, (u * S67 + v * C67) / 3.0, 1700)


def _clamp01(x):
    return max(0.0, min(1.0, x))


def strength(p):
    if p >= T + E:
        return 1.0
    if p > T - E:
        return (p - (T - E)) / (2 * E)
    return 0.0


def fringe(p):
    if p > T - E or p <= T - E - H:
        return 0.0
    return (p - (T - E - H)) / H


def kind_for(mix, p, cl, pick, h, fp, fk, clear):
    """MeadowPatches.kindFor: the plant from the field, the flower cluster, four draws and the clearance."""
    s = strength(p)
    fr = fringe(p)
    if s >= 1:
        density = CORE_DENSITY
    elif s > 0:
        density = EDGE_DENSITY + (CORE_DENSITY - EDGE_DENSITY) * s
    else:
        density = FRINGE_DENSITY * fr
    kind = NONE
    if pick < density:
        tall = mix.tall * _clamp01((s - TALL_FROM) / TALL_SPAN) * _clamp01((clear - TALL_CLEAR) / TALL_RAMP)
        fern = mix.fern if s > 0 else 0.0
        if h < tall:
            kind = LARGE_FERN if mix.fern >= 0.2 and h < tall * 0.25 else TALL
        elif h < tall + fern:
            kind = FERN
        else:
            kind = GRASS
    w = FLOWER_CORE if s >= 1 else FLOWER_EDGE if s > 0 else FLOWER_FRINGE if fr > 0 else FLOWER_OPEN
    if fp < mix.flower * w * (1.0 if cl > CLUSTER_FROM else CLUSTER_LOW):
        return mix.flowers[int(fk * len(mix.flowers)) % len(mix.flowers)]
    return kind


def kind_at(mix, l, u, v, clear=math.inf):
    """MeadowPatches.kindAt."""
    return kind_for(mix, field(l, u, v), cluster(l, u, v), l.decor_hash(u + 311, v), l.decor_hash(u + 733, v),
                    l.decor_hash(u + 977, v), l.decor_hash(u + 1201, v), clear)


# the golden windows: (preset ordinal, seed, u0, v0, size) - Verdant's meadow beyond the river and a seeded Highlands
WINDOWS = ((VERDANT_VALLEY, 0, 100, 20, 64), (HIGHLANDS, 42, 60, 40, 32))


def window(ordinal, seed, u0, v0, size):
    l, mix = Layout(ordinal, seed), MIXES[ordinal]
    return ["".join(CHARS[kind_at(mix, l, u0 + i + 0.25, v0 + k + 0.5)] for i in range(size)) for k in range(size)]


def golden_text():
    out = []
    for w in WINDOWS:
        out.append("# %d %d %d %d %d" % w)
        out += window(*w)
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    if "--golden" in sys.argv:
        os.makedirs(os.path.dirname(GOLDEN), exist_ok=True)
        with open(GOLDEN, "w") as f:
            f.write(golden_text())
        print("wrote", GOLDEN)
