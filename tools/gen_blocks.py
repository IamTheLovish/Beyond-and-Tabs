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
    "grass_block_top":  dict(luma=0.56, std=0.110, sat=0.00, tilt=21.0, smooth=24, pore=40),
    "grass_block_side": dict(luma=0.40, std=0.085, sat=0.36, tilt=22.0, smooth=26, pore=48),
    "grass_block_side_overlay": dict(luma=0.56, std=0.090, sat=0.00, tilt=18.0, smooth=24, pore=40),
    "dirt":             dict(luma=0.36, std=0.081, sat=0.38, tilt=19.0, smooth=26, pore=56),
    "stone":            dict(luma=0.52, std=0.080, sat=0.05, tilt=21.0, smooth=40, pore=12),
    "cobblestone":      dict(luma=0.41, std=0.090, sat=0.08, tilt=20.5, smooth=32, pore=16),
    "sand":             dict(luma=0.67, std=0.030, sat=0.40, tilt=11.0, smooth=40, pore=60),
    "gravel":           dict(luma=0.46, std=0.115, sat=0.10, tilt=26.0, smooth=50, pore=30),
    "oak_planks":       dict(luma=0.46, std=0.055, sat=0.46, tilt=8.5, smooth=70, pore=24),
    "oak_log":          dict(luma=0.33, std=0.060, sat=0.38, tilt=27.0, smooth=40, pore=36),
    "oak_log_top":      dict(luma=0.44, std=0.073, sat=0.48, tilt=12.0, smooth=40, pore=30),
}


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
    peb = smoothstep(0.0, 0.035, edge)                   # a rounded pebble profile, thin gaps
    tone = 0.3 + 0.4 * F.rng.random(k)                   # a narrower tone range: gravel, not a mosaic
    kind = F.rng.random(k)
    H = 0.2 + 0.75 * peb ** 0.45 + 0.03 * F.noise(16, 40, 0.2)
    base = np.where(kind[idx][..., None] < 0.55, np.array([0.50, 0.49, 0.48]),
                    np.where(kind[idx][..., None] < 0.85, np.array([0.52, 0.47, 0.42]), np.array([0.36, 0.35, 0.35])))
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
    shade = 0.80 + 0.30 * norm01(H) - 0.18 * soil
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


def m_planks(F, n):
    rows = 4                                             # vanilla's four boards per block
    row = np.floor(F.v * rows).astype(int)
    in_row = (F.v * rows) % 1.0
    off = np.array([0.0, 0.5, 0.25, 0.75])[row % 4]
    joint_u = (F.u + off) % 1.0
    grain = F.warp(F.noise(2, 48, 0.8, sx=10.0, sy=1.0), 0.0, 0.02 * F.noise(2, 6, 0.5))
    board_tone = (F.rng.random(rows) - 0.5)[row]
    gap = smoothstep(0.0, 0.03, in_row) * smoothstep(0.0, 0.03, 1 - in_row)
    endgap = smoothstep(0.0, 0.012, np.abs(joint_u - 0.5) * 2) if False else smoothstep(0.0, 0.01, np.minimum(joint_u, 1 - joint_u))
    seams = gap * endgap
    H = 0.35 + 0.55 * seams + 0.05 * grain
    nails = ((np.abs(in_row - 0.5) < 0.06) & (np.minimum(np.abs(joint_u - 0.04), np.abs(joint_u - 0.96)) < 0.012))
    H = np.where(nails, H - 0.15, H)
    shade = (0.82 + 0.12 * board_tone + 0.14 * grain) * (0.55 + 0.45 * seams)
    jit = np.stack([0.03 * grain, 0.0 * grain, -0.03 * grain], -1)
    alb = tint((0.66, 0.51, 0.31), shade, jit)
    alb = np.where(nails[..., None], np.array([0.33, 0.31, 0.29]), alb)
    return H, alb


def m_log(F, n):
    ridges = 1 - np.abs(F.warp(F.noise(5, 20, 0.5, sx=1.0, sy=10.0), 0.03 * F.noise(2, 6), 0.0))
    fiss_n = np.abs(F.warp(F.noise(4, 10, 0.5, sx=1.0, sy=8.0), 0.02 * F.noise(3, 8), 0.0))
    fissure = 1 - smoothstep(0.0, 0.08, fiss_n)
    H = 0.45 + 0.35 * ridges ** 2 - 0.30 * fissure + 0.04 * F.noise(16, 40, 0.2, sx=1.0, sy=4.0)
    shade = 0.75 + 0.45 * norm01(H)
    hue = F.noise(2, 6, 0.5)
    jit = np.stack([0.04 * hue, 0.01 * hue, -0.03 * hue], -1)
    return H, tint((0.42, 0.33, 0.21), shade, jit)


def m_log_top(F, n):
    du, dv = F.u - 0.5, F.v - 0.5
    r = np.sqrt(du * du + dv * dv)
    r_w = r + 0.012 * F.noise(3, 10, 0.5)
    rings = 0.5 + 0.5 * np.cos(r_w * 2 * math.pi * 14)
    bark = smoothstep(0.43, 0.47, np.maximum(np.abs(du), np.abs(dv)))   # the square bark border, ~1/16 block
    checks = 1 - smoothstep(0.0, 0.02, np.abs((np.arctan2(dv, du) * 3 / math.pi) % 1.0 - 0.5) * r * 4 + (1 - smoothstep(0.1, 0.35, r)))
    H = 0.6 + 0.08 * rings - 0.15 * checks * (r < 0.4) + 0.05 * F.noise(20, 60, 0.3)
    H = np.where(bark > 0.5, 0.5 + 0.2 * F.noise(8, 30, 0.5), H)
    shade = 0.82 + 0.25 * rings
    alb = tint((0.70, 0.56, 0.36), shade)
    bark_alb = tint((0.42, 0.33, 0.21), 0.75 + 0.4 * norm01(H))
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
    s = materials.encode_specular(np.floor(smooth), f0=10, porosity=t["pore"], emission=255)   # F0 4%: a dielectric
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


# ------------------------------------------------------------------ build -----------------------------------------
def build(res, preview_dir=None):
    os.makedirs(BLOCK_DIR, exist_ok=True)
    seeds = {k: 1000 + i * 37 for i, k in enumerate(TARGETS)}
    out, report, previews = {}, {}, []
    plain = {"dirt": m_dirt, "stone": m_stone, "cobblestone": m_cobblestone, "gravel": m_gravel, "sand": m_sand,
             "oak_planks": m_planks, "oak_log": m_log, "oak_log_top": m_log_top, "grass_block_top": m_grass_top}
    for name, fn in plain.items():
        F = Field(res, seeds[name])
        H, alb = fn(F, res)
        out[name] = (F, H, fit_albedo(alb, TARGETS[name]), None)
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
        report[name] = {"normal_strength": round(k, 2), "tilt_mip0": round(mip_tilt(nxx, nyy, vis, 0), 1),
                        "tilt_mip2": round(mip_tilt(nxx, nyy, vis), 1), "target": TARGETS[name]["tilt"]}
        if preview_dir:
            shown = alb
            if name in ("grass_block_top", "grass_block_side_overlay"):
                shown = np.clip(alb * np.array([0.57, 0.74, 0.35]) / 0.62, 0, 1)   # a plains-like biome tint
            if alpha is not None:
                shown = np.where(alpha[..., None] > 0.5, shown, out["grass_block_side"][2])
            previews.append((name, lit_preview(shown, n_img)))
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
