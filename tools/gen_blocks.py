#!/usr/bin/env python3
"""Our own realistic block pack - original, procedural, 128x + LabPBR 1.3 maps (lovish, Oct 15: "make the 32x even
better"; claude/realistic-look.md: every player gets OUR pack, never a modified Patrix).

Every texture is built from a HEIGHT field (periodic noise, Voronoi stones and pebbles, boards, bark, rings), and the
three maps follow from it so they always agree: the albedo (palette + the height's cavities and ridges), the normal
map (<name>_n.png: RG normal with green along +v, down the texture - the convention tools/materials.py and
Complementary use; B = ambient occlusion from cavities, 255 = none; A = height for parallax, >= 1) and the specular
map (<name>_s.png: R smoothness, G F0 (10 = 4% dielectric), B porosity 0-64 - wet in rain, A 255 = no emission).

DETAIL SCALE: from the RTS camera (zoom 30 = ~36 screen px per block at 1080p) one texel of a 128x texture is ~0.3
px and mipmaps average it away, so the detail lives at 1/64-1/16 block (2-8 texels), not at single texels - the
first round put most of its grain at 1/128 and the sand read flat (pack_study's detail_by_scale, Oct 15).

CALIBRATION (the pack-study numbers, Oct 15 - run 38710336774 on the free Patrix 32x; numbers only): each material
has a target albedo luma mean / spread / saturation and a mean normal tilt. The albedo is fitted to the luma targets
after it is painted, and the normal strength is solved so the mean tilt lands on the target - so "does it match a
mature realistic pack" is measured, not guessed (tools/pack_study.py measures the output the same way). Where the
reference has nothing (height, AO, porosity: the free 32x ships none) we go further.

Deterministic (fixed seeds). Writes resourcepacks/BeyondAndTabs-Realistic/ (a normal resource pack folder) and, with
--preview DIR, lit 2x2 previews and a contact sheet for review.
    python3 tools/gen_blocks.py [--res 128] [--preview DIR]
"""
import argparse
import json
import math
import os

import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import materials  # noqa: E402  - the ONE LabPBR encoder, shared with the units (tools/dsl_to_game.py)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK_DIR = os.path.join(ROOT, "resourcepacks", "BeyondAndTabs-Realistic")
BLOCK_DIR = os.path.join(PACK_DIR, "assets", "minecraft", "textures", "block")

# target = the reference pack's group means (luma, luma std, saturation, normal tilt deg, smoothness p50 0-255)
TARGETS = {
    # grass top std: the reference's 0.110 read as noise from the RTS camera (on-screen 0.124 vs vanilla 0.072, run
    # 38735013028); 0.085 gives ~0.057 on screen at the default zoom (vanilla 0.048, round 3 0.070) - a little more
    # texture than vanilla, no camouflage (sim: nearest-mipmap sampling at 36 px per block matched the eyes stills)
    "grass_block_top":  dict(luma=0.56, std=0.085, sat=0.00, tilt=21.0, smooth=24, pore=40),
    "grass_block_side": dict(luma=0.40, std=0.085, sat=0.36, tilt=22.0, smooth=26, pore=48),
    "grass_block_side_overlay": dict(luma=0.56, std=0.090, sat=0.00, tilt=18.0, smooth=24, pore=40),
    "dirt":             dict(luma=0.36, std=0.081, sat=0.38, tilt=19.0, smooth=26, pore=56),
    "stone":            dict(luma=0.52, std=0.080, sat=0.05, tilt=21.0, smooth=40, pore=12),
    "cobblestone":      dict(luma=0.41, std=0.090, sat=0.08, tilt=20.5, smooth=32, pore=16),
    "sand":             dict(luma=0.67, std=0.030, sat=0.40, tilt=11.0, smooth=40, pore=60),
    "gravel":           dict(luma=0.46, std=0.085, sat=0.07, tilt=26.0, smooth=50, pore=30),
    "oak_planks":       dict(luma=0.46, std=0.055, sat=0.46, tilt=8.5, smooth=70, pore=24),
    "oak_log":          dict(luma=0.33, std=0.060, sat=0.38, tilt=27.0, smooth=40, pore=36),
    "oak_log_top":      dict(luma=0.44, std=0.073, sat=0.48, tilt=12.0, smooth=40, pore=30),
    # round 3 (Oct 15): what the RTS view shows most after grass and water (the eyes ground census); new keys go at
    # the END - the seeds follow this order, so the older textures stay byte-identical. Targets = the single-block
    # numbers of pack-study run 38732322940 (polished andesite is near flat and polished; smooth stone is rough)
    "stone_bricks":     dict(luma=0.47, std=0.081, sat=0.03, tilt=18.4, smooth=14, pore=12),
    "polished_andesite": dict(luma=0.49, std=0.055, sat=0.03, tilt=3.0, smooth=119, pore=4),
    "smooth_stone":     dict(luma=0.63, std=0.078, sat=0.01, tilt=17.7, smooth=16, pore=10),
    "smooth_stone_slab_side": dict(luma=0.63, std=0.078, sat=0.01, tilt=17.7, smooth=16, pore=10),
    "spruce_planks":    dict(luma=0.36, std=0.060, sat=0.30, tilt=7.7, smooth=53, pore=24),
    # leaves (round 5): the trees seen from above. Greyscale (the foliage colour tints them), alpha holes for 'fancy'
    # leaves; reference leaves group: luma 0.60, std 0.093, tilt 18, smoothness 60, SSS on 67% of texels. pore here is
    # the _s blue CODE: 65-255 = subsurface scattering (light through the leaf under a shader pack)
    "oak_leaves":       dict(luma=0.55, std=0.090, sat=0.00, tilt=18.0, smooth=60, pore=180),
    "birch_leaves":     dict(luma=0.58, std=0.090, sat=0.00, tilt=18.0, smooth=60, pore=180),
    "spruce_leaves":    dict(luma=0.48, std=0.085, sat=0.00, tilt=18.0, smooth=50, pore=150),
    "acacia_leaves":    dict(luma=0.55, std=0.090, sat=0.00, tilt=18.0, smooth=60, pore=180),
    "jungle_leaves":    dict(luma=0.52, std=0.095, sat=0.00, tilt=18.0, smooth=70, pore=170),
    "dark_oak_leaves":  dict(luma=0.50, std=0.090, sat=0.00, tilt=18.0, smooth=60, pore=170),
    # round 7: the metal resource patches and the capitol's gold, barrels and hay (pack-study 38740563625, single
    # blocks); f0 238 / 231 = metal (LabPBR 1.3: albedo as F0 / gold)
    "raw_iron_block":   dict(luma=0.53, std=0.100, sat=0.39, tilt=26.0, smooth=76, pore=10),
    # gold: the reference's 23 deg comes from an engraved pattern; a cast plate with tilt 18 came out as lumpy cheese.
    # Luma between the reference (0.63, which leans on a shader's reflections) and vanilla (0.81): without shaders
    # - most players - a 0.63 gold reads brown (pack-study-vanilla 38740868045)
    "gold_block":       dict(luma=0.72, std=0.050, sat=0.70, tilt=8.0, smooth=150, pore=0, f0=231),
    "barrel_side":      dict(luma=0.30, std=0.070, sat=0.46, tilt=17.2, smooth=57, pore=20),
    "barrel_top":       dict(luma=0.30, std=0.070, sat=0.46, tilt=17.2, smooth=57, pore=20),
    "barrel_bottom":    dict(luma=0.30, std=0.070, sat=0.46, tilt=17.2, smooth=57, pore=20),
    "hay_block_side":   dict(luma=0.54, std=0.085, sat=0.51, tilt=21.4, smooth=20, pore=50),
    "hay_block_top":    dict(luma=0.54, std=0.085, sat=0.51, tilt=21.4, smooth=20, pore=50),
    # round 8: every wood's bark and end grain (pack-study 38743610740, single blocks; the reference's log tops carry
    # little relief, 8.4 deg - ours keep 12 like the oak top)
    "spruce_log":       dict(luma=0.16, std=0.045, sat=0.46, tilt=26.2, smooth=40, pore=36),
    "spruce_log_top":   dict(luma=0.31, std=0.075, sat=0.49, tilt=12.0, smooth=40, pore=30),
    "birch_log":        dict(luma=0.85, std=0.140, sat=0.06, tilt=26.0, smooth=50, pore=30),
    "birch_log_top":    dict(luma=0.67, std=0.110, sat=0.35, tilt=12.0, smooth=40, pore=30),
    "acacia_log":       dict(luma=0.35, std=0.063, sat=0.19, tilt=26.1, smooth=40, pore=36),
    "acacia_log_top":   dict(luma=0.41, std=0.053, sat=0.63, tilt=12.0, smooth=40, pore=30),
    "jungle_log":       dict(luma=0.28, std=0.063, sat=0.57, tilt=31.2, smooth=40, pore=36),
    "jungle_log_top":   dict(luma=0.43, std=0.100, sat=0.55, tilt=12.0, smooth=40, pore=30),
    "dark_oak_log":     dict(luma=0.19, std=0.042, sat=0.49, tilt=31.3, smooth=40, pore=36),
    "dark_oak_log_top": dict(luma=0.21, std=0.039, sat=0.61, tilt=12.0, smooth=40, pore=30),
    # round 11: the other factions' buildings (structure census) - single-block numbers from pack-study 38760372803
    # (reference) beside 38760380796 (vanilla); std a little under the reference where rts_view says busy
    "polished_blackstone":        dict(luma=0.19, std=0.043, sat=0.10, tilt=12.0, smooth=60, pore=4),
    "polished_blackstone_bricks": dict(luma=0.19, std=0.049, sat=0.10, tilt=18.0, smooth=40, pore=8),
    "deepslate_bricks":           dict(luma=0.30, std=0.057, sat=0.02, tilt=15.0, smooth=15, pore=10),
    "deepslate_tiles":            dict(luma=0.21, std=0.060, sat=0.01, tilt=18.0, smooth=30, pore=10),
    "deepslate":                  dict(luma=0.31, std=0.070, sat=0.02, tilt=21.0, smooth=12, pore=10),
    "deepslate_top":              dict(luma=0.32, std=0.061, sat=0.03, tilt=18.0, smooth=15, pore=10),
    "polished_deepslate":         dict(luma=0.31, std=0.045, sat=0.02, tilt=4.5, smooth=90, pore=4),
    "cobbled_deepslate":          dict(luma=0.32, std=0.075, sat=0.03, tilt=21.0, smooth=23, pore=16),
    "cut_copper":                 dict(luma=0.50, std=0.058, sat=0.55, tilt=12.0, smooth=104, pore=0),
    "oxidized_cut_copper":        dict(luma=0.55, std=0.065, sat=0.45, tilt=12.0, smooth=12, pore=20),
    "azalea_leaves":              dict(luma=0.43, std=0.080, sat=0.65, tilt=16.5, smooth=70, pore=180),
    "flowering_azalea_leaves":    dict(luma=0.42, std=0.085, sat=0.62, tilt=16.5, smooth=60, pore=180),
    "netherrack":                 dict(luma=0.19, std=0.055, sat=0.58, tilt=30.0, smooth=60, pore=30),
    "nether_bricks":              dict(luma=0.15, std=0.050, sat=0.35, tilt=18.0, smooth=50, pore=12),
    "prismarine_bricks":          dict(luma=0.58, std=0.075, sat=0.33, tilt=12.0, smooth=44, pore=10),
    "dark_prismarine":            dict(luma=0.31, std=0.070, sat=0.36, tilt=15.6, smooth=78, pore=10),
    "moss_block":                 dict(luma=0.40, std=0.060, sat=0.60, tilt=14.0, smooth=41, pore=60),
    "mossy_stone_bricks":         dict(luma=0.44, std=0.070, sat=0.20, tilt=18.6, smooth=25, pore=30),
}


# The seeds follow TARGETS' order (1000 + 37 * index): a key inserted in the middle re-rolled every texture after it
# (the leaves, Oct 15 - caught by comparing checksums). So the order is checked against this list: append new keys
# at the END of both, never insert.
SEED_ORDER = (
    "grass_block_top",
    "grass_block_side",
    "grass_block_side_overlay",
    "dirt",
    "stone",
    "cobblestone",
    "sand",
    "gravel",
    "oak_planks",
    "oak_log",
    "oak_log_top",
    "stone_bricks",
    "polished_andesite",
    "smooth_stone",
    "smooth_stone_slab_side",
    "spruce_planks",
    "oak_leaves",
    "birch_leaves",
    "spruce_leaves",
    "acacia_leaves",
    "jungle_leaves",
    "dark_oak_leaves",
    "raw_iron_block",
    "gold_block",
    "barrel_side",
    "barrel_top",
    "barrel_bottom",
    "hay_block_side",
    "hay_block_top",
    "spruce_log",
    "spruce_log_top",
    "birch_log",
    "birch_log_top",
    "acacia_log",
    "acacia_log_top",
    "jungle_log",
    "jungle_log_top",
    "dark_oak_log",
    "dark_oak_log_top",
    "polished_blackstone",
    "polished_blackstone_bricks",
    "deepslate_bricks",
    "deepslate_tiles",
    "deepslate",
    "deepslate_top",
    "polished_deepslate",
    "cobbled_deepslate",
    "cut_copper",
    "oxidized_cut_copper",
    "azalea_leaves",
    "flowering_azalea_leaves",
    "netherrack",
    "nether_bricks",
    "prismarine_bricks",
    "dark_prismarine",
    "moss_block",
    "mossy_stone_bricks",
)


# ------------------------------------------------------------------ periodic building blocks ---------------------
class Field:
    """Periodic noise and shapes on an N x N tile (everything wraps, so every texture tiles seamlessly)."""

    def __init__(self, n, seed):
        self.n = n
        self.rng = np.random.default_rng(seed)
        f = np.fft.fftfreq(n) * n                        # cycles per tile
        self.fx, self.fy = np.meshgrid(f, f)
        yy, xx = np.mgrid[0:n, 0:n]
        self.u, self.v = xx / n, yy / n                  # 0..1 tile coordinates

    def noise(self, lo, hi, slope=1.0, sx=1.0, sy=1.0):
        """Band-limited noise between lo and hi cycles per tile, power ~ f^-slope. sx / sy squeeze the band along u /
        v: a LARGE sx keeps only low frequencies across u, so features are LONG along u (horizontal); a large sy makes
        them long along v (vertical). Normalised to mean 0, std 1."""
        r = np.sqrt((self.fx * sx) ** 2 + (self.fy * sy) ** 2)
        band = (r >= lo) & (r <= hi)
        amp = np.where(band, 1.0 / np.maximum(r, 1e-6) ** slope, 0.0)
        w = self.rng.standard_normal((self.n, self.n)) + 1j * self.rng.standard_normal((self.n, self.n))
        out = np.real(np.fft.ifft2(np.fft.fft2(np.real(np.fft.ifft2(w))) * amp))
        s = out.std()
        return (out - out.mean()) / (s if s > 1e-12 else 1.0)

    def fbm(self, base, octaves, gain=0.5, sx=1.0, sy=1.0):
        out, a, total = np.zeros((self.n, self.n)), 1.0, 0.0
        for o in range(octaves):
            f = base * 2 ** o
            out += a * self.noise(f, f * 2, 0.0, sx, sy)
            total += a
            a *= gain
        return out / total

    def voronoi(self, count, jitter=1.0, aniso=(1.0, 1.0)):
        """Periodic Voronoi on jittered grid-ish points: F1, F2 (in tile units) and the nearest cell's index."""
        k = max(1, int(round(math.sqrt(count))))
        gx, gy = np.meshgrid((np.arange(k) + 0.5) / k, (np.arange(k) + 0.5) / k)
        pts = np.stack([gx.ravel(), gy.ravel()], 1)
        pts = (pts + (self.rng.random(pts.shape) - 0.5) * jitter / k) % 1.0
        f1 = np.full((self.n, self.n), 9.0)
        f2 = np.full((self.n, self.n), 9.0)
        idx = np.zeros((self.n, self.n), dtype=np.int32)
        for i, (px, py) in enumerate(pts):
            dx = (self.u - px + 0.5) % 1.0 - 0.5          # nearest periodic copy
            dy = (self.v - py + 0.5) % 1.0 - 0.5
            d = np.sqrt((dx * aniso[0]) ** 2 + (dy * aniso[1]) ** 2)
            closer = d < f1
            f2 = np.where(closer, f1, np.minimum(f2, d))
            idx = np.where(closer, i, idx)
            f1 = np.where(closer, d, f1)
        return f1, f2, idx, len(pts)

    def warp(self, img, du, dv):
        """Samples img at (u + du, v + dv) (tile units), bilinear, wrapping."""
        n = self.n
        x = (self.u + du) * n
        y = (self.v + dv) * n
        x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
        tx, ty = x - x0, y - y0
        x0 %= n
        y0 %= n
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        return (img[y0, x0] * (1 - tx) * (1 - ty) + img[y0, x1] * tx * (1 - ty)
                + img[y1, x0] * (1 - tx) * ty + img[y1, x1] * tx * ty)

    def blur(self, img, sigma_px):
        g = np.exp(-2 * (math.pi ** 2) * (sigma_px ** 2) * ((self.fx / self.n) ** 2 + (self.fy / self.n) ** 2))
        return np.real(np.fft.ifft2(np.fft.fft2(img) * g))


def norm01(a):
    lo, hi = np.percentile(a, 0.5), np.percentile(a, 99.5)
    return np.clip((a - lo) / max(hi - lo, 1e-9), 0, 1)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def tint(base_rgb, shade, jitter=None):
    """Albedo = a base colour scaled by shade (0..~1.5 around 1), with optional per-pixel colour jitter (N,N,3)."""
    rgb = np.asarray(base_rgb, dtype=np.float64)[None, None, :] * shade[..., None]
    if jitter is not None:
        rgb = rgb * (1 + jitter)
    return np.clip(rgb, 0, 1)


# ------------------------------------------------------------------ the materials ---------------------------------
def m_dirt(F, n):
    clumps = F.fbm(3, 4, 0.55)
    grit = F.noise(12, 40, 0.3)
    f1, f2, idx, k = F.voronoi(90, 1.0)
    pebble_on = F.rng.random(k) < 0.22
    peb = pebble_on[idx] & (f1 < 0.018)
    H = 0.55 + 0.20 * clumps + 0.06 * grit
    H = np.where(peb, H + 0.25 * (1 - f1 / 0.018), H)
    hue = F.noise(2, 6, 0.5)
    jit = np.stack([0.06 * hue, 0.02 * hue, -0.05 * hue], -1)
    shade = 0.8 + 0.35 * norm01(H)
    alb = tint((0.47, 0.33, 0.22), shade, jit)
    alb = np.where(peb[..., None], np.clip(alb * 0.6 + 0.18, 0, 1) * np.array([0.95, 0.93, 0.9]), alb)
    return H, alb


def m_stone(F, n):
    strata = F.fbm(2, 3, 0.5, sx=4.0, sy=1.0)            # soft horizontal bedding
    body = F.fbm(3, 5, 0.6)
    grain = F.noise(12, 40, 0.2)
    line = np.abs(F.warp(F.noise(2, 6, 0.5), 0.05 * F.noise(4, 10), 0.05 * F.noise(4, 10)))
    sparse = smoothstep(0.4, 0.9, F.noise(1, 3, 0.5))    # cracks only in some parts of the block
    cracks = (1 - smoothstep(0.0, 0.035, line)) * sparse
    H = 0.6 + 0.06 * strata + 0.10 * body + 0.025 * grain - 0.25 * cracks
    hue = F.noise(2, 5, 0.5)
    jit = np.stack([0.025 * hue, 0.0 * hue, -0.025 * hue], -1)
    shade = 0.86 + 0.22 * norm01(H) - 0.25 * cracks
    return H, tint((0.55, 0.55, 0.54), shade, jit)


def m_cobblestone(F, n):
    # irregular joints: warp the lookup position, not the distance (adding noise to the distance punched mortar
    # holes inside the stones)
    f1, f2, idx, k = F.voronoi(22, 0.9)
    wu, wv = 0.012 * F.noise(4, 12, 0.5), 0.012 * F.noise(4, 12, 0.5)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    idx = idx[(np.round((F.v + wv) * n).astype(int)) % n, (np.round((F.u + wu) * n).astype(int)) % n]
    edge = f2 - f1                                       # 0 at the joints
    stone = smoothstep(0.003, 0.06, edge)                # 0 in the mortar, 1 inside a stone (rounded)
    tone = F.rng.random(k)
    surf = F.noise(12, 48, 0.4)
    H = 0.25 + 0.6 * np.sqrt(stone) + 0.03 * surf * stone
    shade_stone = (0.74 + 0.45 * tone[idx]) * (0.95 + 0.05 * surf)
    shade = np.where(stone > 0.15, shade_stone, 0.45 + 0.2 * stone)
    hue = (tone[idx] - 0.5)
    jit = np.stack([0.04 * hue, 0.0 * hue, -0.04 * hue], -1)
    return H, tint((0.52, 0.52, 0.51), shade, jit)


def m_gravel(F, n):
    f1, f2, idx, k = F.voronoi(70, 1.0)
    g1, g2, gidx, gk = F.voronoi(260, 1.0)
    big = (F.rng.random(k) < 0.45)[idx]                  # some places a big pebble, elsewhere small ones
    edge = np.where(big, f2 - f1, g2 - g1)
    idx = np.where(big, idx, gidx + k)
    k = k + gk
    # a DOMED pebble (1 at its centre, 0 at the cell boundary where f1 = f2), not a bevelled polygon: the old profile
    # (smoothstep of f2 - f1) made flat-topped shards
    a1, a2 = np.where(big, f1, g1), np.where(big, f2, g2)
    peb = np.clip(1 - (2 * a1 / np.maximum(a1 + a2, 1e-6)) ** 2, 0, 1) * smoothstep(0.0, 0.02, edge)
    # round 10: the busiest texture on screen (rts std 0.114 vs vanilla's calm 0.067) with peach "shards" - a narrower
    # tone range and greyer warm pebbles; gravel lines riverbeds, seen through the water
    tone = 0.42 + 0.26 * F.rng.random(k)
    kind = F.rng.random(k)
    H = 0.2 + 0.75 * peb ** 0.45 + 0.03 * F.noise(16, 40, 0.2)
    base = np.where(kind[idx][..., None] < 0.55, np.array([0.50, 0.49, 0.48]),
                    np.where(kind[idx][..., None] < 0.85, np.array([0.50, 0.47, 0.44]), np.array([0.38, 0.37, 0.37])))
    shade = (0.65 + 0.7 * tone[idx]) * (0.55 + 0.45 * peb)
    alb = np.clip(base * shade[..., None], 0, 1)
    return H, alb


def m_sand(F, n):
    grains = F.noise(16, 40, 0.0)
    ripple_ph = F.v * 2 * math.pi * 5 + 1.2 * F.noise(1, 3, 0.5)
    ripples = np.sin(ripple_ph)
    H = 0.6 + 0.06 * ripples + 0.08 * grains + 0.05 * F.fbm(3, 3)
    hue = F.noise(3, 8, 0.4)
    jit = np.stack([0.02 * hue, 0.01 * hue, -0.03 * hue], -1)
    shade = 0.92 + 0.12 * norm01(H) + 0.05 * grains
    return H, tint((0.86, 0.78, 0.58), shade, jit)


def grass_blades(F, count, length_px, width_px):
    """Short blades scattered over the tile: a height bump along each blade, wrapped."""
    n = F.n
    H = np.zeros((n, n))
    rng = F.rng
    for _ in range(count):
        cx, cy = rng.random() * n, rng.random() * n
        ang = rng.random() * math.pi
        ln = length_px * (0.6 + 0.8 * rng.random())
        dx, dy = math.cos(ang), math.sin(ang)
        steps = int(ln * 2) + 1
        for s in range(steps):
            t = s / max(1, steps - 1)
            x = int(round(cx + dx * (t - 0.5) * ln)) % n
            y = int(round(cy + dy * (t - 0.5) * ln)) % n
            hgt = 1.0 - abs(t - 0.5) * 1.2                 # highest at the middle of the blade
            for w in range(-(width_px // 2), width_px - width_px // 2):
                xx, yy = (x + int(round(-dy * w))) % n, (y + int(round(dx * w))) % n
                H[yy, xx] = max(H[yy, xx], hgt)
    return H


def m_grass_top(F, n):
    blades = grass_blades(F, int(9 * n), n / 36, 1)     # ~1100 blades of ~3-6 texels at 128x
    clumps = F.fbm(3, 4, 0.55)
    fine = F.noise(16, 40, 0.0)
    soil = smoothstep(0.55, 0.9, F.noise(4, 12, 0.5))   # a few thin spots where the soil shows (darker)
    H = 0.30 + 0.35 * blades + 0.18 * clumps + 0.05 * fine - 0.12 * soil
    # the COLOUR is calmer than the relief (round 4, from the first real eyes stills, run 38735013028): painted from H,
    # the blades and soil spots made a dark squiggly network that read as camouflage / moss from the RTS camera
    # (on-screen luma std 0.124 vs vanilla's calm 0.072). Soft small tufts carry the colour; the blades stay in the
    # normal map, where a shader's light turns them into real grain.
    # small tufts at 1/6 - 1/16 block, nothing coarser: block-sized patches repeat on every block and drew a lattice
    # across the field (a block texture cannot vary across many blocks - the biome tint does that)
    tufts = F.noise(6, 16, 0.5)
    shade = 0.85 + 0.12 * norm01(tufts) + 0.06 * norm01(blades + 0.5 * fine) - 0.05 * soil
    grey = np.clip(np.stack([shade] * 3, -1) * 0.62, 0, 1)    # greyscale: the biome colour tints it in game
    return H, grey


def m_grass_overlay(F, n, fringe):
    """The tinted fringe over the side texture: the lawn seen from the side, grey, alpha = the fringe."""
    blades = grass_blades(F, int(6 * n), n / 30, 1)
    clumps = F.fbm(3, 3, 0.55)
    H = 0.35 + 0.4 * blades + 0.15 * clumps
    shade = 0.80 + 0.30 * norm01(H)
    grey = np.clip(np.stack([shade] * 3, -1) * 0.62, 0, 1)
    return H, grey


def side_fringe(F, n):
    """How far the grass reaches down the side: a ragged edge ~3/16 of the block, with drips."""
    depth = 0.17 + 0.05 * F.noise(4, 16, 0.6)[0:1, :] + 0.06 * np.clip(F.noise(10, 30, 0.2)[0:1, :], 0, None)
    return (F.v < depth).astype(np.float64) * 1.0


def m_grass_side(F, n):
    Hd, ad = m_dirt(F, n)
    fringe = side_fringe(F, n)
    # the untinted base under the overlay: a grey-green where the fringe is (seen only at its frayed edge)
    grassy = np.clip(np.stack([np.full((n, n), 0.42), np.full((n, n), 0.50), np.full((n, n), 0.32)], -1), 0, 1)
    alb = np.where(fringe[..., None] > 0.5, grassy * (0.85 + 0.3 * norm01(Hd))[..., None], ad)
    H = np.where(fringe > 0.5, 0.75 + 0.1 * Hd, Hd)
    return H, alb, fringe


def m_planks(F, n, base=(0.66, 0.51, 0.31), wave=0.02):
    rows = 4                                             # vanilla's four boards per block
    row = np.floor(F.v * rows).astype(int)
    in_row = (F.v * rows) % 1.0
    off = np.array([0.0, 0.5, 0.25, 0.75])[row % 4]
    joint_u = (F.u + off) % 1.0
    grain = F.warp(F.noise(2, 48, 0.8, sx=10.0, sy=1.0), 0.0, wave * F.noise(2, 6, 0.5))
    board_tone = (F.rng.random(rows) - 0.5)[row]
    gap = smoothstep(0.0, 0.03, in_row) * smoothstep(0.0, 0.03, 1 - in_row)
    endgap = smoothstep(0.0, 0.012, np.abs(joint_u - 0.5) * 2) if False else smoothstep(0.0, 0.01, np.minimum(joint_u, 1 - joint_u))
    seams = gap * endgap
    H = 0.35 + 0.55 * seams + 0.05 * grain
    nails = ((np.abs(in_row - 0.5) < 0.06) & (np.minimum(np.abs(joint_u - 0.04), np.abs(joint_u - 0.96)) < 0.012))
    H = np.where(nails, H - 0.15, H)
    shade = (0.82 + 0.12 * board_tone + 0.14 * grain) * (0.55 + 0.45 * seams)
    jit = np.stack([0.03 * grain, 0.0 * grain, -0.03 * grain], -1)
    alb = tint(base, shade, jit)
    alb = np.where(nails[..., None], np.array([0.33, 0.31, 0.29]), alb)
    return H, alb


def m_spruce_planks(F, n):
    # the darker, redder board with a straight grain (fit_albedo sets the luma): the oak's wave read as marble on the
    # capitol roof (run 38735013028)
    return m_planks(F, n, base=(0.48, 0.35, 0.21), wave=0.006)


def m_leaves(F, n, count, length, width, round_tip=True, droop=0.0):
    """A leaf canopy tile: `count` leaves (length / width in tile units) dropped at random depths and angles; the top
    leaf wins each texel. Returns H, greyscale albedo (the foliage colour tints it in game) and the alpha mask (the
    holes 'fancy' leaves show). Under the holes the colour is a dark leaf shade: 'fast' graphics ignore the alpha and
    draw it (vanilla does the same)."""
    rng = F.rng
    depth = np.full((n, n), -1.0)
    tone = np.zeros((n, n))
    along = np.zeros((n, n))
    across = np.zeros((n, n))
    for _ in range(count):
        cx, cy = rng.random(), rng.random()
        ang = rng.random() * math.pi * 2
        L = length * (0.7 + 0.6 * rng.random())
        W = width * (0.7 + 0.6 * rng.random())
        d = rng.random()
        dx = (F.u - cx + 0.5) % 1.0 - 0.5
        dy = (F.v - cy + 0.5) % 1.0 - 0.5
        ca, sa = math.cos(ang), math.sin(ang)
        a = (dx * ca + dy * sa) / L                      # -0.5..0.5 along the leaf, base to tip
        c = (-dx * sa + dy * ca) / W                     # -0.5..0.5 across
        shape = 0.5 * np.sin(np.clip(a + 0.5, 0, 1) * math.pi) ** (0.6 if round_tip else 1.2)
        inside = (np.abs(a) <= 0.5) & (np.abs(c) <= shape) & (d > depth)
        depth = np.where(inside, d, depth)
        tone = np.where(inside, rng.random(), tone)
        along = np.where(inside, a, along)
        across = np.where(inside, c / np.maximum(shape, 1e-3), across)
    alpha = (depth >= 0).astype(np.float64)
    rib = np.exp(-(across / 0.12) ** 2)                  # the midrib, a faint raised line
    dome = 1 - across ** 2                               # each leaf curves up to its midrib
    H = np.where(alpha > 0, 0.25 + 0.5 * depth + 0.15 * dome + 0.05 * rib - droop * (along + 0.5) ** 2, 0.0)
    shade = np.where(alpha > 0, 0.70 + 0.25 * depth + 0.10 * (tone - 0.5) + 0.08 * dome + 0.06 * rib, 0.35)
    return H, np.clip(np.stack([shade] * 3, -1), 0, 1), alpha


LEAVES = {   # name: (leaves per tile, length, width, round tip) - at 128 texels per block
    "oak_leaves": (150, 0.16, 0.09, True),
    "birch_leaves": (190, 0.12, 0.08, True),
    "spruce_leaves": (800, 0.10, 0.025, False),          # needles, dense (vanilla spruce covers ~3/4)
    "acacia_leaves": (380, 0.11, 0.05, False),
    "jungle_leaves": (80, 0.26, 0.13, True),
    "dark_oak_leaves": (170, 0.17, 0.10, True),
}


def m_raw_iron(F, n):
    """The metal resource patches: raw iron lumps fused in a rusty crust - rounded nuggets (Voronoi), metal grey where
    they are worn bright, orange-brown rust in the hollows. Returns H, albedo and the per-texel F0 code."""
    f1, f2, idx, k = F.voronoi(26, 1.0)
    wu, wv = 0.01 * F.noise(4, 12, 0.5), 0.01 * F.noise(4, 12, 0.5)
    f1, f2 = F.warp(f1, wu, wv), F.warp(f2, wu, wv)
    lump = smoothstep(0.0, 0.07, f2 - f1)                # 0 in the seams between nuggets
    pits = smoothstep(0.6, 1.0, F.noise(12, 40, 0.3))
    H = 0.2 + 0.7 * np.sqrt(lump) + 0.04 * F.noise(8, 30, 0.4) - 0.08 * pits
    worn = smoothstep(0.55, 0.85, norm01(H) + 0.15 * F.noise(4, 12, 0.5))   # the crests rubbed to bare metal
    rust = np.array([0.62, 0.38, 0.24])
    metal = np.array([0.62, 0.58, 0.55])
    shade = 0.70 + 0.35 * norm01(H) - 0.15 * pits
    alb = (rust[None, None, :] * (1 - worn[..., None]) + metal[None, None, :] * worn[..., None]) * shade[..., None]
    f0 = np.where(worn > 0.5, 238, 10)
    return H, np.clip(alb, 0, 1), f0


def m_gold(F, n):
    """The capitol's gold: a cast and polished plate with a bevelled rim, faint hammer dents and polish scratches."""
    bev = frame(F, 0.07, 0.015)
    dents = F.noise(2, 6, 0.6)                           # broad, soft unevenness of a cast plate
    scratch = np.abs(F.warp(F.noise(20, 60, 0.0, sx=6.0, sy=1.0), 0.0, 0.01 * F.noise(2, 6)))
    sc = 1 - smoothstep(0.0, 0.04, scratch)
    H = 0.35 + 0.5 * bev + 0.012 * dents - 0.01 * sc
    shade = (0.88 + 0.06 * dents - 0.05 * sc) * (0.72 + 0.28 * bev)
    return H, tint((0.98, 0.78, 0.25), shade)


def staves(F, n, count=6):
    """Barrel staves running along v: count boards, a dark seam between them, a little bow and grain."""
    col = np.floor(F.u * count).astype(int)
    inside = (F.u * count) % 1.0
    seam = smoothstep(0.0, 0.06, inside) * smoothstep(0.0, 0.06, 1 - inside)
    bow = 1 - (2 * inside - 1) ** 2                       # each stave curves out a little
    grain = F.noise(2, 40, 0.8, sx=1.0, sy=8.0)
    tone = (F.rng.random(count) - 0.5)[col % count]
    return seam, bow, grain, tone


def m_barrel_side(F, n):
    seam, bow, grain, tone = staves(F, n)
    hoop = (np.abs(F.v - 0.22) < 0.06) | (np.abs(F.v - 0.78) < 0.06)   # two iron hoops
    H = 0.35 + 0.35 * seam + 0.1 * bow + 0.04 * grain
    H = np.where(hoop, 0.85 + 0.03 * F.noise(8, 30, 0.4), H)
    shade = (0.82 + 0.12 * tone + 0.10 * grain) * (0.55 + 0.45 * seam)
    alb = tint((0.47, 0.33, 0.20), shade)
    iron = tint((0.33, 0.32, 0.31), 0.85 + 0.2 * F.noise(8, 30, 0.4))
    return H, np.where(hoop[..., None], iron, alb), np.where(hoop, 238, 10)


def m_barrel_end(F, n, hole):
    """Barrel top / bottom: boards across the lid inside an iron rim; the top has the bung hole."""
    bev = frame(F, 0.10, 0.02)
    rim = bev < 0.95
    rows = 4
    row = np.floor(F.v * rows).astype(int)
    inr = (F.v * rows) % 1.0
    gap = smoothstep(0.0, 0.04, inr) * smoothstep(0.0, 0.04, 1 - inr)
    grain = F.noise(2, 40, 0.8, sx=8.0, sy=1.0)
    tone = (F.rng.random(rows) - 0.5)[row]
    H = 0.35 + 0.35 * gap + 0.04 * grain
    shade = (0.82 + 0.12 * tone + 0.10 * grain) * (0.6 + 0.4 * gap)
    alb = tint((0.47, 0.33, 0.20), shade)
    if hole:
        r = np.sqrt((F.u - 0.5) ** 2 + (F.v - 0.5) ** 2)
        bung = r < 0.08
        H = np.where(bung, 0.1, H)
        alb = np.where(bung[..., None], np.array([0.08, 0.06, 0.05]), alb)
    ring = (frame(F, 0.12, 0.10) < 0.9)               # the iron rim, ~1/10 block
    H = np.where(ring, 0.8, H)
    iron = tint((0.33, 0.32, 0.31), 0.85 + 0.2 * F.noise(8, 30, 0.4))
    return H, np.where(ring[..., None], iron, alb), np.where(ring, 238, 10)


def m_hay_side(F, n):
    strands = F.noise(10, 60, 0.4, sx=1.0, sy=12.0)     # long straws along v
    clump = F.noise(3, 8, 0.6, sx=1.0, sy=4.0)
    band = (np.abs(F.v - 0.25) < 0.045) | (np.abs(F.v - 0.75) < 0.045)   # two cords round the bale
    H = 0.45 + 0.25 * norm01(strands) + 0.1 * clump
    H = np.where(band, H - 0.15, H)
    shade = 0.80 + 0.25 * norm01(strands) + 0.08 * clump
    alb = tint((0.86, 0.70, 0.30), shade)
    cord = tint((0.55, 0.22, 0.14), 0.85 + 0.15 * norm01(strands))
    return H, np.where(band[..., None], cord, alb)


def m_hay_top(F, n):
    f1, f2, idx, k = F.voronoi(400, 1.0)                  # the cut ends of the straws, packed
    end = smoothstep(0.0, 0.012, f2 - f1)
    tone = F.rng.random(k)[idx]
    H = 0.3 + 0.5 * end + 0.05 * tone
    shade = (0.75 + 0.3 * tone) * (0.5 + 0.5 * end)
    return H, tint((0.86, 0.70, 0.30), shade)


def m_birch_log(F, n):
    """Birch bark: pale and papery, dark horizontal lenticels (short dashes across the trunk) and black patches where
    branches fell or the bark cracked."""
    paper = F.fbm(3, 4, 0.5, sx=3.0, sy=1.0)
    dash = np.abs(F.warp(F.noise(6, 20, 0.4, sx=10.0, sy=1.0), 0.0, 0.01 * F.noise(3, 8)))   # long along u: across
    lent = (1 - smoothstep(0.0, 0.05, dash)) * smoothstep(0.3, 0.8, F.noise(8, 24, 0.4))
    patch = smoothstep(1.2, 1.8, F.noise(3, 8, 0.6, sx=2.0, sy=1.0))
    H = 0.6 + 0.08 * paper - 0.08 * lent - 0.05 * patch   # the marks are in the colour, barely in the relief
    shade = 0.92 + 0.08 * paper - 0.55 * lent - 0.75 * patch
    return H, tint((0.86, 0.85, 0.80), shade)


# each wood: (bark colour, end-grain colour) - fit_albedo sets luma / spread / saturation from the targets
WOODS = {
    "spruce": ((0.30, 0.20, 0.12), (0.62, 0.45, 0.28)),
    "acacia": ((0.45, 0.42, 0.38), (0.78, 0.42, 0.20)),
    "jungle": ((0.45, 0.35, 0.18), (0.72, 0.56, 0.36)),
    "dark_oak": ((0.25, 0.18, 0.10), (0.45, 0.30, 0.18)),
}


# ------------------------------------------------------------------ round 11: the other factions' buildings -------
def m_bricks(F, n, courses, per_course, base, moss=0.0, pits=0.08, tone_amp=0.06):
    """Dressed bricks: `courses` rows per block, `per_course` bricks per row, joints offset by half a brick on every
    other row; chipped arrises, a weathered face; `moss` > 0 grows moss in the joints and over some brick edges."""
    course = np.floor(F.v * courses).astype(int)
    in_c = (F.v * courses) % 1.0
    ju = (F.u * per_course + np.where(course % 2 == 0, 0.0, 0.5)) % 1.0
    brick = (course * per_course + np.floor(F.u * per_course + np.where(course % 2 == 0, 0.0, 0.5)).astype(int))
    chip = 0.006 * F.noise(8, 24, 0.5)
    dv = np.minimum(in_c, 1 - in_c) / courses + chip
    du = np.minimum(ju, 1 - ju) / per_course + chip
    face = smoothstep(0.004, 0.018, np.minimum(dv, du))
    tone = (F.rng.random(64) - 0.5)[brick % 64]
    weather = F.fbm(3, 4, 0.55)
    grain = F.noise(12, 40, 0.3)
    pit = smoothstep(0.75, 0.95, F.noise(10, 30, 0.4))
    H = 0.25 + 0.6 * face + 0.05 * weather * face + 0.02 * grain - pits * pit * face
    shade = np.where(face > 0.2, 0.82 + tone_amp * tone + 0.10 * weather + 0.04 * grain - 1.5 * pits * pit,
                     0.55 + 0.2 * face)
    alb = tint(base, shade)
    if moss > 0:
        m = smoothstep(0.50, 0.80, F.fbm(3, 4, 0.6) * 0.5 + 0.5 + 0.35 * (1 - face)) * moss   # ~1/4 of the face
        moss_rgb = tint((0.33, 0.45, 0.18), 0.85 + 0.25 * norm01(F.noise(10, 30, 0.3)))
        alb = alb * (1 - m[..., None]) + moss_rgb * m[..., None]
        H = H + 0.08 * m
    return H, alb


def m_polished(F, n, base, mottle=0.10, crystals=0.10):
    """A polished stone face (polished blackstone / deepslate): soft mottling, few crystals, a bevelled border."""
    mot = F.fbm(3, 4, 0.55)
    speck = F.noise(16, 48, 0.0)
    flecks = smoothstep(1.3, 2.2, F.noise(20, 56, 0.0))
    bev = frame(F, 0.06, 0.012)
    H = 0.35 + 0.5 * bev + 0.03 * mot + 0.01 * speck
    shade = (0.88 + mottle * mot + 0.02 * speck - crystals * flecks) * (0.75 + 0.25 * bev)
    return H, tint(base, shade)


def m_tiles(F, n, grid, base):
    """Square tiles, grid x grid per block, each a little uneven in tone and set, thin dark joints."""
    cu, cv = np.floor(F.u * grid).astype(int), np.floor(F.v * grid).astype(int)
    iu, iv = (F.u * grid) % 1.0, (F.v * grid) % 1.0
    edge = np.minimum(np.minimum(iu, 1 - iu), np.minimum(iv, 1 - iv)) / grid
    face = smoothstep(0.003, 0.014, edge)
    tone = (F.rng.random(grid * grid) - 0.5)[(cv * grid + cu) % (grid * grid)]
    tilt = (F.rng.random(grid * grid) - 0.5)[(cv * grid + cu) % (grid * grid)]
    rough = F.noise(10, 40, 0.3)
    H = 0.3 + 0.55 * face + 0.05 * tilt * iu * face + 0.02 * rough
    shade = np.where(face > 0.2, 0.84 + 0.10 * tone + 0.05 * rough, 0.55 + 0.2 * face)
    return H, tint(base, shade)


def m_deepslate(F, n, top=False):
    """Deepslate: dark slate in vertical layers on the side (it forms upright), a tight grain on the top."""
    if top:
        layers = F.fbm(3, 4, 0.6)
    else:
        layers = F.fbm(2, 4, 0.55, sx=1.0, sy=5.0)       # long along v: the upright bedding
    grain = F.noise(12, 40, 0.3, sx=1.0, sy=1.0 if top else 3.0)
    crack = 1 - smoothstep(0.0, 0.03, np.abs(F.warp(F.noise(2, 6, 0.5, sx=1.0, sy=1.0 if top else 4.0),
                                                       0.03 * F.noise(4, 10), 0.03 * F.noise(4, 10))))
    crack *= smoothstep(0.4, 0.9, F.noise(1, 3, 0.5))
    H = 0.6 + 0.10 * layers + 0.03 * grain - 0.2 * crack
    shade = 0.86 + 0.20 * norm01(H) - 0.2 * crack
    return H, tint((0.30, 0.30, 0.32), shade)


def m_copper(F, n, oxidized):
    """Cut copper: four plates per block with seams; bare metal (LabPBR copper, F0 234) or a teal patina that runs in
    streaks and gathers in the seams (dielectric). Returns H, albedo, F0 map."""
    grid = 2
    iu, iv = (F.u * grid) % 1.0, (F.v * grid) % 1.0
    edge = np.minimum(np.minimum(iu, 1 - iu), np.minimum(iv, 1 - iv)) / grid
    face = smoothstep(0.004, 0.02, edge)
    ham = F.noise(4, 14, 0.6)
    scratch = 1 - smoothstep(0.0, 0.04, np.abs(F.noise(20, 60, 0.0, sx=6.0, sy=1.0)))
    H = 0.35 + 0.5 * face + 0.02 * ham - 0.01 * scratch
    if not oxidized:
        shade = (0.86 + 0.08 * ham - 0.04 * scratch) * (0.65 + 0.35 * face)
        return H, tint((0.80, 0.45, 0.30), shade), np.full(H.shape, 234)
    streak = F.fbm(3, 4, 0.6, sx=1.0, sy=4.0)
    shade = (0.85 + 0.12 * streak + 0.04 * ham) * (0.7 + 0.3 * face)
    alb = tint((0.32, 0.64, 0.55), shade)
    alb = np.where((face < 0.3)[..., None], alb * 0.75, alb)
    return H, alb, np.full(H.shape, 10)


def m_netherrack(F, n):
    """Netherrack: a porous, fused, crumbly red rock - lumps and pits, darker veins."""
    f1, f2, idx, k = F.voronoi(60, 1.0)
    lump = smoothstep(0.0, 0.05, f2 - f1)
    pores = smoothstep(0.55, 0.95, F.noise(14, 44, 0.2))
    vein = 1 - smoothstep(0.0, 0.04, np.abs(F.noise(3, 9, 0.5)))
    H = 0.3 + 0.5 * np.sqrt(lump) - 0.18 * pores - 0.1 * vein
    shade = 0.75 + 0.35 * norm01(H) - 0.15 * vein
    return H, tint((0.50, 0.16, 0.14), shade)


def m_moss(F, n):
    """A moss carpet: soft tufts, a few darker hollows (coloured: moss blocks are not biome-tinted)."""
    tufts = F.noise(6, 18, 0.5)
    fine = F.noise(18, 50, 0.1)
    hollow = smoothstep(0.6, 0.95, F.noise(3, 8, 0.5))
    H = 0.5 + 0.2 * tufts + 0.05 * fine - 0.15 * hollow
    shade = 0.85 + 0.15 * norm01(tufts) + 0.05 * fine - 0.15 * hollow
    return H, tint((0.36, 0.48, 0.18), shade)


def m_azalea(F, n, flowering):
    """Azalea leaves: the canopy generator, coloured (not tinted), with pink blossoms on the flowering kind."""
    H, grey, alpha = m_leaves(F, n, 170, 0.14, 0.09, True)
    alb = grey * np.asarray((0.42, 0.62, 0.22))[None, None, :] / 0.7
    if flowering:
        f1, f2, idx, k = F.voronoi(30, 1.0)
        blossom = (f1 < 0.06) & (F.rng.random(k) < 0.7)[idx] & (alpha > 0.5)   # clusters a leaf wide
        petals = tint((0.88, 0.52, 0.78), 0.85 + 0.2 * norm01(F.noise(12, 40, 0.2)))
        alb = np.where(blossom[..., None], petals, alb)
        H = np.where(blossom, H + 0.1, H)
    return H, np.clip(alb, 0, 1), alpha


def frame(F, width, groove):
    """A border bevel round the block (cut / polished stone): 1 inside, falling to 0 in a groove at the edge, so tiled
    blocks read as separate stones. width and groove in tile units."""
    d = np.minimum(np.minimum(F.u, 1 - F.u), np.minimum(F.v, 1 - F.v))
    return smoothstep(0.0, groove, d) * (0.85 + 0.15 * smoothstep(groove, width, d))


def m_stone_bricks(F, n):
    # two courses per block, joints offset by half a brick (as vanilla): big dressed blocks, chipped edges, a
    # weathered face - the capitol's walls and stairs
    course = np.floor(F.v * 2).astype(int)
    in_c = (F.v * 2) % 1.0
    ju = (F.u + np.where(course % 2 == 0, 0.0, 0.5)) % 1.0
    chip = 0.006 * F.noise(8, 24, 0.5)                    # ragged joints
    dv = np.minimum(in_c, 1 - in_c) / 2 + chip           # distance to a bed joint (tile units)
    du = np.minimum(ju, 1 - ju) + chip                   # to a head joint
    face = smoothstep(0.004, 0.022, np.minimum(dv, du))  # 0 in the mortar, 1 on the face (a rounded arris)
    # small tone steps between the bricks: the texture repeats on every block (stone bricks are never turned), so one
    # darker brick became a lattice across a wall (rts_view)
    tone = (F.rng.random(4) - 0.5)[(course * 2 + (ju > 0.5)) % 4]
    weather = F.fbm(3, 4, 0.55)
    grain = F.noise(12, 40, 0.3)
    pits = smoothstep(0.75, 0.95, F.noise(10, 30, 0.4))
    H = 0.25 + 0.6 * face + 0.05 * weather * face + 0.02 * grain - 0.08 * pits * face
    shade = np.where(face > 0.2, (0.82 + 0.06 * tone + 0.10 * weather + 0.04 * grain - 0.12 * pits), 0.55 + 0.2 * face)
    hue = F.noise(2, 5, 0.5)
    jit = np.stack([0.02 * hue, 0.0 * hue, -0.02 * hue], -1)
    return H, tint((0.50, 0.50, 0.49), shade, jit)


def m_polished_andesite(F, n):
    # a polished face: soft mottling and speckle (the andesite's crystals), almost no relief, a bevelled border
    mottle = F.fbm(3, 4, 0.55)
    speck = F.noise(16, 48, 0.0)
    flecks = smoothstep(1.3, 2.2, F.noise(20, 56, 0.0))   # sparse dark crystals
    bev = frame(F, 0.06, 0.012)
    H = 0.35 + 0.5 * bev + 0.03 * mottle + 0.01 * speck
    # few, faint crystals: with more the polished steps read as rough granite from the RTS camera (run 38735013028)
    shade = (0.88 + 0.10 * mottle + 0.02 * speck - 0.10 * flecks) * (0.75 + 0.25 * bev)
    jit = np.stack([0.015 * mottle, 0.0 * mottle, -0.01 * mottle], -1)
    return H, tint((0.53, 0.53, 0.52), shade, jit)


def m_smooth_stone(F, n):
    # sawn limestone-like slab face: very flat, faint clouding, a thin groove at the border
    # the reference's relief (17.7 deg at 32 texels per block) is a sawn, granular face, not soft bumps: the tilt comes
    # from grain at 1/20-1/6 block and a few pores, the clouding stays in the colour
    cloud = F.fbm(2, 4, 0.6)
    grain = F.noise(6, 20, 0.4)                          # 1/20-1/6 block: survives the mipmaps (finer read as pitting)
    pores = smoothstep(1.4, 2.4, F.noise(16, 48, 0.0))
    bev = frame(F, 0.04, 0.010)
    H = 0.4 + 0.45 * bev + 0.01 * cloud + 0.03 * grain - 0.03 * pores
    shade = (0.92 + 0.06 * cloud + 0.03 * grain - 0.10 * pores) * (0.72 + 0.28 * bev)
    return H, tint((0.62, 0.62, 0.61), shade)


def m_smooth_stone_slab_side(F, n):
    # the side of a smooth stone slab block: two slabs (top and bottom halves), each with its own bordered face
    H, alb = m_smooth_stone(F, n)
    seam = smoothstep(0.0, 0.010, np.abs(F.v - 0.5))
    H = H * (0.6 + 0.4 * seam)
    alb = alb * (0.72 + 0.28 * seam)[..., None]
    return H, alb


def m_log(F, n, base=(0.42, 0.33, 0.21)):
    ridges = 1 - np.abs(F.warp(F.noise(5, 20, 0.5, sx=1.0, sy=10.0), 0.03 * F.noise(2, 6), 0.0))
    fiss_n = np.abs(F.warp(F.noise(4, 10, 0.5, sx=1.0, sy=8.0), 0.02 * F.noise(3, 8), 0.0))
    fissure = 1 - smoothstep(0.0, 0.08, fiss_n)
    H = 0.45 + 0.35 * ridges ** 2 - 0.30 * fissure + 0.04 * F.noise(16, 40, 0.2, sx=1.0, sy=4.0)
    shade = 0.75 + 0.45 * norm01(H)
    hue = F.noise(2, 6, 0.5)
    jit = np.stack([0.04 * hue, 0.01 * hue, -0.03 * hue], -1)
    return H, tint(base, shade, jit)


def m_log_top(F, n, wood=(0.70, 0.56, 0.36), bark_rgb=(0.42, 0.33, 0.21)):
    du, dv = F.u - 0.5, F.v - 0.5
    r = np.sqrt(du * du + dv * dv)
    r_w = r + 0.012 * F.noise(3, 10, 0.5)
    rings = 0.5 + 0.5 * np.cos(r_w * 2 * math.pi * 14)
    bark = smoothstep(0.43, 0.47, np.maximum(np.abs(du), np.abs(dv)))   # the square bark border, ~1/16 block
    checks = 1 - smoothstep(0.0, 0.02, np.abs((np.arctan2(dv, du) * 3 / math.pi) % 1.0 - 0.5) * r * 4 + (1 - smoothstep(0.1, 0.35, r)))
    H = 0.6 + 0.08 * rings - 0.15 * checks * (r < 0.4) + 0.05 * F.noise(20, 60, 0.3)
    H = np.where(bark > 0.5, 0.5 + 0.2 * F.noise(8, 30, 0.5), H)
    shade = 0.82 + 0.25 * rings
    alb = tint(wood, shade)
    bark_alb = tint(bark_rgb, 0.75 + 0.4 * norm01(H))
    alb = np.where(bark[..., None] > 0.5, bark_alb, alb)
    return H, alb


# ------------------------------------------------------------------ calibration + maps ----------------------------
# Calibrated at MIP 2, not at full size (round 2, Oct 15): from the RTS camera at the default zoom a 128x texture is
# sampled at mip 2 (32 texels per block - the reference's own resolution). Fitted at mip 0, round 1 lost ~25% of its
# contrast and half or more of its relief there (grass top 7 deg vs 21; pack_study --mip 2).
CAL_MIP = 2
LF_GAIN = 1.2          # extra low-frequency relief (survives the mipmaps)
TILT_CAP = 1.8         # the full-size tilt may exceed the target by at most this factor (close-ups not embossed)


def mip(a, levels):
    for _ in range(levels):
        h, w = a.shape[:2]
        a = (a[0:h:2, 0:w:2] + a[1:h:2, 0:w:2] + a[0:h:2, 1:w:2] + a[1:h:2, 1:w:2]) / 4.0
    return a


def mip_tilt(nx, ny, m=None, levels=CAL_MIP):
    """Mean tilt (deg) of the normals as the GPU sees them at a mip level: xy averaged, z rebuilt from xy."""
    ax, ay = mip(nx, levels), mip(ny, levels)
    r = np.sqrt(np.clip(ax * ax + ay * ay, 0, 1))
    t = np.degrees(np.arcsin(r))
    if m is not None:
        t = t[mip(m.astype(np.float64), levels) > 0.5]
    return float(t.mean())


def fit_albedo(alb, t, mask=None):
    """Fits luma mean / spread (the spread measured at CAL_MIP) and saturation to the targets, keeping each hue."""
    m = np.ones(alb.shape[:2], bool) if mask is None else mask
    L = alb[..., 0] * 0.2126 + alb[..., 1] * 0.7152 + alb[..., 2] * 0.0722
    grey = L[..., None]
    mx, mn = alb.max(-1), alb.min(-1)
    sat = np.where(mx > 1e-6, (mx - mn) / np.maximum(mx, 1e-6), 0)[m].mean()
    if t["sat"] <= 0.01:
        alb = np.repeat(grey, 3, -1)
    elif sat > 1e-6:
        alb = np.clip(grey + (alb - grey) * (t["sat"] / sat), 0, 1)
    L = alb[..., 0] * 0.2126 + alb[..., 1] * 0.7152 + alb[..., 2] * 0.0722
    mu = L[m].mean()
    Lm, mm = mip(L, CAL_MIP), mip(m.astype(np.float64), CAL_MIP) > 0.5
    sd = Lm[mm].std() if mm.any() else L[m].std()
    L2 = t["luma"] + (L - mu) * (t["std"] / max(sd, 1e-6))
    scale = np.where(L > 1e-4, L2 / np.maximum(L, 1e-4), 1.0)
    return np.clip(alb * scale[..., None], 0, 1)


def normals(H, strength):
    return materials.height_normals(H, strength, wrap=True)    # a block texture tiles: its slopes wrap


def _bisect(fn, target):
    lo, hi = 0.0, 4096.0
    for _ in range(50):
        mid = (lo + hi) / 2
        lo, hi = (mid, hi) if fn(mid) < target else (lo, mid)
    return (lo + hi) / 2


def solve_strength(H, tilt_deg, mask=None):
    """The gradient scale whose mean tilt AT CAL_MIP (over mask) equals the target, unless that would push the
    full-size tilt past TILT_CAP x the target - then the cap wins (bisection: tilt grows with the scale)."""
    m = np.ones(H.shape, bool) if mask is None else mask

    def at_mip(k):
        nx, ny, _ = normals(H, k)
        return mip_tilt(nx, ny, m)

    def full(k):
        nx, ny, nz = normals(H, k)
        return float(np.degrees(np.arccos(np.clip(nz[m], -1, 1))).mean())
    return min(_bisect(at_mip, tilt_deg), _bisect(full, tilt_deg * TILT_CAP))


def maps(F, H, t, alpha=None):
    H = norm01(H)
    lf = F.blur(H, F.n / 48)                              # ~1/48 block: relief the mipmaps keep
    H = norm01(H + LF_GAIN * (lf - lf.mean()))
    k = solve_strength(H, t["tilt"], None if alpha is None else alpha > 0.5)
    nx, ny, nz = normals(H, k)
    cav = np.clip(F.blur(H, F.n / 48) - H, 0, None)        # below the local average = occluded
    ao = 1 - np.clip(cav * 2.5, 0, 0.45)
    n = materials.encode_normal(nx, ny, ao, H)
    smooth = np.clip(t["smooth"] + 30 * (H - 0.5) + 10 * F.noise(4, 16, 0.5), 0, 229)   # ridges worn shinier
    # F0 code: 10 = 4%, a dielectric (most blocks); 231 = gold, 238 = a metal with its albedo as F0 (LabPBR 1.3);
    # a material may paint its own per-texel code (rusty iron: metal lumps, dielectric rust) as t["f0_map"]
    f0 = t.get("f0_map", t.get("f0", 10))
    s = materials.encode_specular(np.floor(smooth), f0=f0, porosity=t["pore"], emission=255, shape=H.shape)
    if alpha is not None:
        n[..., 3] = np.where(alpha > 0.5, n[..., 3], 255)
    probs = materials.check_labpbr(n, s, None if alpha is None else alpha > 0.5)
    if probs:
        raise SystemExit("LabPBR check failed: " + "; ".join(probs))
    return n, s, k


def to_png(rgb, alpha=None):
    a = np.ones(rgb.shape[:2]) if alpha is None else alpha
    rgba = np.concatenate([rgb, a[..., None]], -1)
    return Image.fromarray(np.clip(rgba * 255 + 0.5, 0, 255).astype(np.uint8), "RGBA")


def lit_preview(alb, n_img, light=(-0.45, -0.55, 0.70)):
    """Albedo lit by the normal map (a low sun from the top left) and AO, tiled 2x2 - how relief reads."""
    nm = np.asarray(n_img, dtype=np.float64) / 255.0
    nx, ny = nm[..., 0] * 2 - 1, nm[..., 1] * 2 - 1
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
    lx, ly, lz = light
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    ndl = np.clip((nx * lx + ny * ly + nz * lz) / ln, 0, 1)
    shade = (0.35 + 0.85 * ndl) * nm[..., 2]
    img = np.clip(alb[..., :3] * shade[..., None], 0, 1)
    return np.tile(img, (2, 2, 1))


RTS_PPB = 36                       # screen px per block at the RTS default zoom (30 blocks of view height, 1080p)
PLAINS_TINT = (0.57, 0.74, 0.35)   # a plains-like biome colour for the tinted textures
FOLIAGE_TINT = (0.47, 0.67, 0.18)  # the plains foliage colour (#77AB2F) for the leaves


ROTATED = {"grass_block_top", "dirt", "sand", "gravel", "stone"}   # vanilla models turn these at random per block


def rts_view(alb, ppb=RTS_PPB, blocks=8, tint=None, seed=1, rotate=True):
    """The texture as the RTS camera draws it: a field of blocks - each turned a random quarter where the vanilla
    model does that (ROTATED), else all alike, so a pattern that repeats on every block shows - sampled the way the block atlas is (nearest texel, the two nearest mip levels
    blended). Its luma std matched the eyes stills (round 3 grass: 0.070 on screen at zoom 30, 0.074 here), so it is
    how a texture's busyness is judged before a CI run. Returns (image, on-screen luma std)."""
    a = alb[..., :3] if tint is None else alb[..., :3] * np.asarray(tint)[None, None, :]
    mips = [a]
    while mips[-1].shape[0] > 8:
        mips.append(mip(mips[-1], 1))
    lod = max(0.0, math.log2(a.shape[0] / ppb))
    lo = min(int(lod), len(mips) - 1)
    hi = min(lo + 1, len(mips) - 1)
    f = lod - int(lod)
    rng = np.random.default_rng(seed)

    def samp(m, k):
        idx = (np.arange(ppb) * m.shape[0] / ppb).astype(int)
        return np.rot90(m, k)[np.ix_(idx, idx)]
    rows = []
    for _ in range(blocks):
        ks = rng.integers(4, size=blocks) if rotate else np.zeros(blocks, int)
        rows.append(np.concatenate([(1 - f) * samp(mips[lo], k) + f * samp(mips[hi], k) for k in ks], 1))
    img = np.concatenate(rows, 0)
    return img, float((img @ np.array([0.2126, 0.7152, 0.0722])).std())


# ------------------------------------------------------------------ build -----------------------------------------
# Water (round 6): 15% of the RTS view (the eyes ground census). Targets = vanilla's own water (pack-study-vanilla,
# run 38740005727: greyscale - the biome water colour tints it - luma 0.68, std 0.064, alpha 0.71 on every texel)
# because the reference pack's water was lumped with lava. Animated like vanilla's (32 frames, 2 ticks each = a 3.2 s
# loop): ripple layers drift a whole number of tiles per loop, so the strip loops without a seam.
WATER = dict(luma=0.68, std=0.064, alpha=0.71, frames=32, frametime=2, tilt=10.0, smooth=230, f0=5)
# (lo, hi cycles per tile, slope, weight, tiles moved per loop along u, along v)
STILL_LAYERS = ((2, 5, 1.0, 1.0, 1, 0), (4, 10, 0.8, 0.6, 0, 1), (5, 12, 0.8, 0.5, -1, 1), (12, 28, 0.5, 0.25, 2, -1))
# Flowing water (round 9): vanilla's flow texture is drawn at half scale (2 blocks per tile) and turned so v points
# downstream, so the tile is twice as wide (256) and every layer runs down v - streaks stretched along the current.
# 1 tick per frame (vanilla's flow animates every tick): one tile = 2 blocks per 1.6 s loop, ~1.25 blocks / s.
FLOW_LAYERS = ((2, 6, 1.0, 1.0, 0, 1), (5, 14, 0.8, 0.6, 0, 1), (8, 20, 0.8, 0.45, 1, 2), (16, 40, 0.5, 0.25, 0, 3))
FLOW = dict(frametime=1, aniso=(1.0, 3.0))


def water_frames(res, seed=4242, layers_spec=STILL_LAYERS, aniso=(1.0, 1.0)):
    """(H, albedo) per frame of water: a slow swell, crossing ripples and fine glints, each layer a fixed random
    spectrum shifted in phase over the loop (a pure translation by whole tiles). aniso stretches the features (a
    large second value: long along v)."""
    F = Field(res, seed)
    layers = []   # (spectrum, weight, tiles moved per loop along u, along v)
    for lo, hi, slope, w, du, dv in layers_spec:
        r = np.sqrt((F.fx * aniso[0]) ** 2 + (F.fy * aniso[1]) ** 2)
        band = (r >= lo) & (r <= hi)
        spec = np.where(band, 1.0 / np.maximum(r, 1e-6) ** slope, 0.0) * (
            F.rng.standard_normal((res, res)) + 1j * F.rng.standard_normal((res, res)))
        layers.append((spec, w, du, dv))
    out = []
    for t in range(WATER["frames"]):
        ph = t / WATER["frames"]
        H = np.zeros((res, res))
        for spec, w, du, dv in layers:
            # shifting a periodic field by (du, dv) tiles over the loop: phase exp(-2 pi i (fx du + fy dv) ph)
            f = np.real(np.fft.ifft2(spec * np.exp(-2j * math.pi * (F.fx * du + F.fy * dv) * ph)))
            H += w * f / max(f.std(), 1e-9)
        out.append(H)
    # one normalisation for the whole loop, so frames keep their relative brightness (no flicker)
    allH = np.stack(out)
    allH = (allH - allH.mean()) / allH.std()
    return F, [h for h in allH]


def write_water(res, preview_dir=None, name="water_still"):
    flow = name == "water_flow"
    F, Hs = water_frames(res, 4243, FLOW_LAYERS, FLOW["aniso"]) if flow else water_frames(res)
    alb_frames, n_frames, s_frames = [], [], []
    t = dict(tilt=WATER["tilt"])
    # the normal strength is solved once (frame 0) and kept: one surface, moving
    H0 = norm01(Hs[0])
    k = solve_strength(H0, t["tilt"])
    for H in Hs:
        Hn = np.clip(0.5 + 0.18 * H, 0, 1)
        nx, ny, nz = normals(Hn, k)
        # a glint on the crests facing a high sun, troughs a little darker: water reads by its light, not its colour
        lit = nx * -0.35 + ny * -0.45 + nz * 0.82
        shade = 1.0 + 0.9 * (lit - 0.82) + 0.05 * H
        alb_frames.append(np.stack([shade] * 3, -1))
        n_frames.append(materials.encode_normal(nx, ny, np.ones_like(Hn), np.maximum(Hn, 1 / 255)))
        s_frames.append(materials.encode_specular(np.full(Hn.shape, WATER["smooth"]), f0=WATER["f0"], porosity=0,
                                                  emission=255))
    strip = np.concatenate(alb_frames, 0)
    L = strip[..., 0]
    strip = np.clip(WATER["luma"] + (strip - L.mean()) * (WATER["std"] / max(mip(L, CAL_MIP).std(), 1e-6)), 0, 1)
    alpha = np.full(strip.shape[:2], WATER["alpha"])
    nstrip, sstrip = np.concatenate(n_frames, 0), np.concatenate(s_frames, 0)
    probs = materials.check_labpbr(nstrip, sstrip)
    if probs:
        raise SystemExit("LabPBR check failed (water): " + "; ".join(probs))
    to_png(strip, alpha).save(os.path.join(BLOCK_DIR, name + ".png"), optimize=True)
    Image.fromarray(nstrip, "RGBA").save(os.path.join(BLOCK_DIR, name + "_n.png"), optimize=True)
    Image.fromarray(sstrip, "RGBA").save(os.path.join(BLOCK_DIR, name + "_s.png"), optimize=True)
    for suffix in ("", "_n", "_s"):     # the maps animate with the colour (Iris / Oculus read each one's own .mcmeta)
        with open(os.path.join(BLOCK_DIR, "%s%s.png.mcmeta" % (name, suffix)), "w") as f:
            json.dump({"animation": {"frametime": FLOW["frametime"] if flow else WATER["frametime"]}}, f)
    first = strip[:res]
    tinted = first * np.asarray((0.25, 0.46, 0.89))[None, None, :]      # the plains water colour (#3F76E4)
    img, std = rts_view(tinted)
    if preview_dir:
        Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB").save(
            os.path.join(preview_dir, name + "_rts.png"))
        frames = [np.clip(strip[i * res:(i + 1) * res] * np.asarray((0.25, 0.46, 0.89)), 0, 1) for i in (0, 8, 16, 24)]
        Image.fromarray((np.concatenate(frames, 1) * 255).astype(np.uint8), "RGB").save(
            os.path.join(preview_dir, name + "_frames.png"))
    return {"normal_strength": round(k, 2), "frames": WATER["frames"], "rts_luma_std": round(std, 3),
            "luma": round(float(strip[..., 0].mean()), 3)}


def build(res, preview_dir=None):
    if tuple(TARGETS) != SEED_ORDER:
        raise SystemExit("TARGETS' order differs from SEED_ORDER - append new materials at the end of both (the seeds "
                         "follow the order; an insertion re-rolls every texture after it)")
    os.makedirs(BLOCK_DIR, exist_ok=True)
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
    seeds = {k: 1000 + i * 37 for i, k in enumerate(TARGETS)}
    out, report, previews = {}, {}, []
    plain = {"dirt": m_dirt, "stone": m_stone, "cobblestone": m_cobblestone, "gravel": m_gravel, "sand": m_sand,
             "oak_planks": m_planks, "oak_log": m_log, "oak_log_top": m_log_top, "grass_block_top": m_grass_top,
             "stone_bricks": m_stone_bricks, "polished_andesite": m_polished_andesite, "smooth_stone": m_smooth_stone,
             "smooth_stone_slab_side": m_smooth_stone_slab_side, "spruce_planks": m_spruce_planks}
    for name, fn in plain.items():
        F = Field(res, seeds[name])
        H, alb = fn(F, res)
        out[name] = (F, H, fit_albedo(alb, TARGETS[name]), None)
    for name, fn in (("raw_iron_block", m_raw_iron), ("barrel_side", m_barrel_side),
                     ("barrel_top", lambda F, n: m_barrel_end(F, n, True)),
                     ("barrel_bottom", lambda F, n: m_barrel_end(F, n, False))):
        F = Field(res, seeds[name])
        H, alb, f0 = fn(F, res)
        TARGETS[name]["f0_map"] = f0
        out[name] = (F, H, fit_albedo(alb, TARGETS[name]), None)
    for name, fn in (("gold_block", m_gold), ("hay_block_side", m_hay_side), ("hay_block_top", m_hay_top)):
        F = Field(res, seeds[name])
        H, alb = fn(F, res)
        out[name] = (F, H, fit_albedo(alb, TARGETS[name]), None)
    for wood, (bark, end) in WOODS.items():
        for name, fn in ((wood + "_log", lambda F, n, b=bark: m_log(F, n, b)),
                         (wood + "_log_top", lambda F, n, w=end, b=bark: m_log_top(F, n, w, b))):
            F = Field(res, seeds[name])
            H, alb = fn(F, res)
            out[name] = (F, H, fit_albedo(alb, TARGETS[name]), None)
    F = Field(res, seeds["birch_log"])
    H, alb = m_birch_log(F, res)
    out["birch_log"] = (F, H, fit_albedo(alb, TARGETS["birch_log"]), None)
    F = Field(res, seeds["birch_log_top"])
    H, alb = m_log_top(F, res, (0.86, 0.76, 0.56), (0.86, 0.85, 0.80))
    out["birch_log_top"] = (F, H, fit_albedo(alb, TARGETS["birch_log_top"]), None)
    # round 11
    r11 = {
        "polished_blackstone": lambda F, n: m_polished(F, n, (0.20, 0.18, 0.20), 0.08, 0.06),
        "polished_blackstone_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.21, 0.19, 0.21)),
        "deepslate_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.30, 0.30, 0.32)),
        "deepslate_tiles": lambda F, n: m_tiles(F, n, 4, (0.22, 0.22, 0.24)),
        "deepslate": lambda F, n: m_deepslate(F, n),
        "deepslate_top": lambda F, n: m_deepslate(F, n, top=True),
        "polished_deepslate": lambda F, n: m_polished(F, n, (0.32, 0.32, 0.34), 0.06, 0.04),
        "cobbled_deepslate": m_cobblestone,
        "netherrack": m_netherrack,
        "nether_bricks": lambda F, n: m_bricks(F, n, 4, 2, (0.30, 0.12, 0.14), pits=0.05),
        "prismarine_bricks": lambda F, n: m_bricks(F, n, 2, 2, (0.42, 0.66, 0.58), tone_amp=0.10),
        "dark_prismarine": lambda F, n: m_tiles(F, n, 2, (0.20, 0.38, 0.33)),
        "moss_block": m_moss,
        "mossy_stone_bricks": lambda F, n: m_bricks(F, n, 2, 1, (0.50, 0.50, 0.49), moss=0.8),
    }
    for name, fn in r11.items():
        F = Field(res, seeds[name])
        H, alb = fn(F, res)
        out[name] = (F, H, fit_albedo(alb, TARGETS[name]), None)
    for name in ("cut_copper", "oxidized_cut_copper"):
        F = Field(res, seeds[name])
        H, alb, f0 = m_copper(F, res, name.startswith("oxidized"))
        TARGETS[name]["f0_map"] = f0
        out[name] = (F, H, fit_albedo(alb, TARGETS[name]), None)
    for name in ("azalea_leaves", "flowering_azalea_leaves"):
        F = Field(res, seeds[name])
        H, alb, alpha = m_azalea(F, res, name.startswith("flowering"))
        alb = np.where(alpha[..., None] > 0.5, fit_albedo(alb, TARGETS[name], alpha > 0.5), alb)
        out[name] = (F, H, alb, alpha)
    for name, (count, length, width, round_tip) in LEAVES.items():
        F = Field(res, seeds[name])
        H, alb, alpha = m_leaves(F, res, count, length, width, round_tip)
        alb = np.where(alpha[..., None] > 0.5, fit_albedo(alb, TARGETS[name], alpha > 0.5), alb)
        out[name] = (F, H, alb, alpha)
    F = Field(res, seeds["grass_block_side"])
    H, alb, fringe = m_grass_side(F, res)
    out["grass_block_side"] = (F, H, fit_albedo(alb, TARGETS["grass_block_side"]), None)
    Fo = Field(res, seeds["grass_block_side_overlay"])
    Ho, ao = m_grass_overlay(Fo, res, fringe)
    out["grass_block_side_overlay"] = (Fo, Ho, fit_albedo(ao, TARGETS["grass_block_side_overlay"], fringe > 0.5),
                                       fringe)
    for name, (F, H, alb, alpha) in out.items():
        n_img, s_img, k = maps(F, H, TARGETS[name], alpha)
        to_png(alb, alpha).save(os.path.join(BLOCK_DIR, name + ".png"), optimize=True)
        Image.fromarray(n_img, "RGBA").save(os.path.join(BLOCK_DIR, name + "_n.png"), optimize=True)
        Image.fromarray(s_img, "RGBA").save(os.path.join(BLOCK_DIR, name + "_s.png"), optimize=True)
        nxx = (n_img[..., 0].astype(np.float64) / 127.5 - 1)
        nyy = (n_img[..., 1].astype(np.float64) / 127.5 - 1)
        vis = None if alpha is None else alpha > 0.5
        tint = PLAINS_TINT if name in ("grass_block_top", "grass_block_side_overlay") else \
            FOLIAGE_TINT if name in LEAVES else None
        seen = alb if alpha is None or name not in LEAVES else alb * alpha[..., None] + 0.08 * (1 - alpha[..., None])
        rts_img, rts_std = rts_view(seen, tint=tint, rotate=name in ROTATED)
        report[name] = {"normal_strength": round(k, 2), "tilt_mip0": round(mip_tilt(nxx, nyy, vis, 0), 1),
                        "tilt_mip2": round(mip_tilt(nxx, nyy, vis), 1), "target": TARGETS[name]["tilt"],
                        "rts_luma_std": round(rts_std, 3)}
        if preview_dir:
            shown = alb
            if name in ("grass_block_top", "grass_block_side_overlay"):
                shown = np.clip(alb * np.array([0.57, 0.74, 0.35]) / 0.62, 0, 1)   # a plains-like biome tint
            if name in LEAVES:
                shown = np.clip(alb * np.array(FOLIAGE_TINT) / 0.55, 0, 1)
            if alpha is not None:
                under = np.full_like(shown, 0.06) if name in LEAVES else out["grass_block_side"][2]
                shown = np.where(alpha[..., None] > 0.5, shown, under)
            previews.append((name, lit_preview(shown, n_img)))
            if alpha is None or name in LEAVES:
                Image.fromarray((np.clip(rts_img, 0, 1) * 255).astype(np.uint8), "RGB").save(
                    os.path.join(preview_dir, name + "_rts.png"))
    report["water_still"] = write_water(res, preview_dir)
    report["water_flow"] = write_water(res * 2, preview_dir, "water_flow")
    with open(os.path.join(PACK_DIR, "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"pack_format": 15, "description": "Beyond and Tabs - Realistic (original, 128x LabPBR)"}},
                  f, indent=1)
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        tiles = []
        for name, img in previews:
            im = Image.fromarray((img * 255).astype(np.uint8), "RGB").resize((256, 256), Image.LANCZOS)
            im.save(os.path.join(preview_dir, name + "_lit.png"))
            tiles.append(im)
        cols = 4
        rows = math.ceil(len(tiles) / cols)
        sheet = Image.new("RGB", (cols * 256, rows * 256), (20, 20, 20))
        for i, im in enumerate(tiles):
            sheet.paste(im, ((i % cols) * 256, (i // cols) * 256))
        sheet.save(os.path.join(preview_dir, "sheet.png"))
        with open(os.path.join(preview_dir, "order.txt"), "w") as f:
            f.write("\n".join(n for n, _ in previews))
    return report


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", type=int, default=128)
    ap.add_argument("--preview")
    a = ap.parse_args()
    rep = build(a.res, a.preview)
    print(json.dumps(rep, indent=1))
    print("wrote", BLOCK_DIR)


if __name__ == "__main__":
    main()
