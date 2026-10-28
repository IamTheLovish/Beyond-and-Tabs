"""Grass tint preview: a field of grass blocks as the RTS camera sees it, tinted by an exact port of
postfx/GrassTint.java (the constants are read from the Java file, so the two cannot drift apart).

Each block is tinted per vertex corner and blended across the face (Embeddium, which the pack ships, samples the
biome colour per corner); `--per-block` shows vanilla's one colour per face instead. The grass texture is mipped
down to the RTS pixel size and turned a random quarter per block, as tools/gen_blocks.py rts_view() does.

    python tools/gen_blocks.py                      # builds the pack's grass_block_top.png first
    python tools/grass_tint_preview.py out.png      # flat | tinted, 96 x 96 blocks at 12 px per block

Prints the patch-scale luma std (the std of 16 x 16-block means / mean luma): how much a field varies at the scale
the RTS camera reads. A flat field is ~0.000; the Oct 22 version of GrassTint 0.018; Oct 28 round 3 ~0.046 (and at the 4-block scale ~0.04).
"""
import argparse
import os
import re

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether/postfx/GrassTint.java")
TEX = os.path.join(ROOT, "resourcepacks/BeyondAndTabs-Realistic/assets/minecraft/textures/block/grass_block_top.png")
PLAINS = 0x91BD59            # vanilla plains grass colour
LUMA = np.array([0.2126, 0.7152, 0.0722])


def constants(path=JAVA):
    src = open(path, encoding="utf-8").read()
    k = {}
    for name, val in re.findall(r"static final double (\w+) = ([-0-9.]+);", src):
        k[name] = float(val)
    for name, val in re.findall(r"static final double\[\] (\w+) = \{([^}]*)\};", src):
        k[name] = [float(v) for v in val.split(",")]
    return k


U64 = np.uint64


def _lattice(ix, iz, seed):
    ix = ix.astype(np.int64).view(np.uint64)
    iz = iz.astype(np.int64).view(np.uint64)
    s = np.int64(seed).astype(np.uint64) if seed >= 0 else np.array(seed, np.int64).view(np.uint64)
    with np.errstate(over="ignore"):
        h = (ix * U64(0x9E3779B97F4A7C15)) ^ (iz * U64(0xC2B2AE3D27D4EB4F)) ^ (s * U64(0x165667B19E3779F9))
        h = (h ^ (h >> U64(30))) * U64(0xBF58476D1CE4E5B9)
        h = (h ^ (h >> U64(27))) * U64(0x94D049BB133111EB)
        h ^= h >> U64(31)
    return (h >> U64(11)).astype(np.float64) * 2.0 ** -53 * 2 - 1


def _fade(t):
    return t * t * t * (t * (t * 6 - 15) + 10)


def _value_noise(x, z, seed):
    ix, iz = np.floor(x), np.floor(z)
    u, v = _fade(x - ix), _fade(z - iz)
    a, b = _lattice(ix, iz, seed), _lattice(ix + 1, iz, seed)
    c, d = _lattice(ix, iz + 1, seed), _lattice(ix + 1, iz + 1, seed)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v


def _octaves(x, z, seed, cells, weights):
    return sum(w * _value_noise(x / c, z / c, seed + i) for i, (c, w) in enumerate(zip(cells, weights))) / sum(weights)


def apply(rgb, x, z, salt, k):
    """GrassTint.apply on arrays of block coordinates; returns float rgb (before rounding)."""
    def shape(f, c):
        return np.tanh(c * f) / np.tanh(c)

    def smooth(t):
        return t * t * (3 - 2 * t)
    f = shape(_octaves(x, z, salt * 8, k["CELLS"], k["WEIGHTS"]), k["CONTRAST"])
    dry = shape(_octaves(x + 7919, z - 4583, (salt + 101) * 8, k["DRY_CELLS"], k["DRY_WEIGHTS"]), k["DRY_CONTRAST"])
    st = _octaves(x - 1201, z + 911, (salt + 303) * 8, k["STRAW_CELLS"], k["STRAW_WEIGHTS"])
    s = smooth(np.clip((st - k["STRAW_AT"]) / k["STRAW_SOFT"], 0, 1)) * np.clip((dry - k["STRAW_GATE"]) / 0.4, 0, 1)
    cl = _octaves(x - 3001, z + 1777, (salt + 202) * 8, k["CLUMP_CELLS"], k["CLUMP_WEIGHTS"])
    m = smooth(np.clip((cl - k["CLUMP_AT"]) / k["CLUMP_SOFT"], 0, 1)) * np.clip((k["CLUMP_GATE"] - dry) / 0.4, 0, 1) * (1 - s)
    mottle = _octaves(x + 555, z - 777, salt * 8 + 40, k["MOTTLE_CELLS"], k["MOTTLE_WEIGHTS"])
    hm = k["HUE_MOTTLE"] * _octaves(x + 999, z + 333, salt * 8 + 60, k["MOTTLE_CELLS"], k["MOTTLE_WEIGHTS"])
    v = (1 + k["VALUE"] * f) * (1 - k["CLUMP_DARK"] * m) * (1 + 0.10 * s) * (1 + k["MOTTLE"] * np.where(mottle > 0, mottle, 1.6 * mottle))
    dp, dn = np.maximum(dry, 0), np.minimum(dry, 0)
    o = k["OLIVE"]
    r = ((rgb >> 16) & 0xFF) * o[0] * v * (1 + 0.6 * hm) * (1 + 0.04 * dp + 0.06 * dn) * (1 + 0.10 * s) * (1 - 0.02 * m)
    g = ((rgb >> 8) & 0xFF) * o[1] * v * (1 - 0.01 * dp - 0.03 * dn) * (1 + 0.04 * s)
    b = (rgb & 0xFF) * o[2] * v * (1 - 0.9 * np.maximum(hm, 0)) * (1 - 0.15 * dp) * (1 - 0.12 * s) * (1 - 0.02 * m)
    return np.clip(np.stack([r, g, b], -1), 0, 255)


def render(tex, k, blocks=96, ppb=12, per_block=False, x0=0, z0=0):
    n = blocks * ppb
    if k is None:
        col = np.broadcast_to(np.array([(PLAINS >> 16) & 255, (PLAINS >> 8) & 255, PLAINS & 255], float), (n, n, 3))
    elif per_block:
        bx, bz = np.meshgrid(np.arange(blocks) + x0, np.arange(blocks) + z0)
        col = np.repeat(np.repeat(np.round(apply(PLAINS, bx.astype(float), bz.astype(float), 1, k)), ppb, 0), ppb, 1)
    else:
        # Embeddium: each corner takes the colour of the block it belongs to... approximated as the average of the
        # four blocks sharing that corner, then bilinear across the face
        bx, bz = np.meshgrid(np.arange(blocks + 2) + x0 - 1, np.arange(blocks + 2) + z0 - 1)
        blk = apply(PLAINS, bx.astype(float), bz.astype(float), 1, k)
        corner = 0.25 * (blk[:-1, :-1] + blk[1:, :-1] + blk[:-1, 1:] + blk[1:, 1:])
        t = (np.arange(n) + 0.5) / ppb
        bi = np.floor(t).astype(int)
        fr = (t - bi)
        fx, fz = fr[None, :, None], fr[:, None, None]
        A, B = corner[bi][:, bi], corner[bi][:, bi + 1]
        C, D = corner[bi + 1][:, bi], corner[bi + 1][:, bi + 1]
        col = (A * (1 - fx) + B * fx) * (1 - fz) + (C * (1 - fx) + D * fx) * fz
    a = tex.astype(float) / 255.0
    while a.shape[0] > ppb * 2:
        a = 0.25 * (a[0::2, 0::2] + a[1::2, 0::2] + a[0::2, 1::2] + a[1::2, 1::2])
    idx = (np.arange(ppb) * a.shape[0] / ppb).astype(int)
    rng = np.random.default_rng(3)
    rows = [np.concatenate([np.rot90(a, q)[np.ix_(idx, idx)] for q in rng.integers(4, size=blocks)], 1)
            for _ in range(blocks)]
    return np.clip(np.concatenate(rows, 0) * col, 0, 255).astype(np.uint8)


def patch_std(img, ppb=12, patch=16):
    lum = img.astype(float) @ LUMA / 255.0
    k = ppb * patch
    h = lum.shape[0] // k * k
    means = lum[:h, :h].reshape(h // k, k, h // k, k).mean((1, 3))
    return float(means.std() / lum.mean())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--tex", default=TEX)
    ap.add_argument("--blocks", type=int, default=96)
    ap.add_argument("--ppb", type=int, default=12, help="screen px per block (36 = default RTS zoom, 12 = zoomed out)")
    ap.add_argument("--per-block", action="store_true", help="vanilla's renderer: one colour per block face")
    a = ap.parse_args()
    tex = np.array(Image.open(a.tex).convert("RGB"))
    k = constants()
    panels = []
    for name, kk in (("flat biome colour", None), ("GrassTint", k)):
        img = render(tex, kk, a.blocks, a.ppb, a.per_block)
        print(f"{name}: patch-scale luma std {patch_std(img, a.ppb):.4f}")
        pil = Image.fromarray(img)
        d = ImageDraw.Draw(pil)
        d.rectangle([0, 0, pil.width, 20], fill=(0, 0, 0))
        d.text((6, 4), f"{name}  -  {a.blocks} x {a.blocks} blocks at {a.ppb} px/block", fill=(255, 255, 255))
        panels.append(pil)
    out = Image.new("RGB", (sum(p.width for p in panels) + 8, panels[0].height), (16, 16, 16))
    out.paste(panels[0], (0, 0))
    out.paste(panels[1], (panels[0].width + 8, 0))
    out.save(a.out)
    print("wrote", a.out)


if __name__ == "__main__":
    main()
