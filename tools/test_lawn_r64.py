#!/usr/bin/env python3
"""Round 64 lawn: the 8 x 8-block TURF sheet in VARIANTS, and the untinted flower layer (lovish Nov 4 ~15:30 ET:
"61b looks better" than r62's clumps - fine, even grain is the right scale; the 4-block repeat gets fixed by
"8x8 sheet + hashed pick": the lawn spans 8 x 8 blocks and the Java side (PR #504's lane) picks between several sheet
variants per 8 x 8 cell by a position hash, with no turn, and draws an UNTINTED flower layer over ~6% of the lawn).

The round 63 blind critic (Fable): "No knob moved the result the player sees"; the repeat "severe" (autocorrelation
0.99 at exactly 4 blocks); "no discrete leaves, no highlight ... no white or yellow specks"; luma p5-p95 range 57
levels against 136 in the clover photo; fixes: kill the repeat, real relief + flowers (range up, near-black under 1%,
lit-side asymmetry >= 0.02). The round 62 critic's turf recipe: fine ANISOTROPIC blade stipple (wind lay drifting over
2-3 blocks), flat clover patches, warm scuffs, 2-6 block tonal patches, flower drifts; and the engine's sun at t5000
is EAST (+u), not LAWN_SUN's north - baked light comes from +u and stays weak.

These checks sit beside test_lawn_r61/r62/r63's, which all stay (the r62/r63 bands now measure the 8 x 8 sheet):
- the sheet is 8 x 8 blocks, LAWN_VARIANTS >= 2 of it, each its own detail (mip correlation between two variants
  under 0.3) yet joining every other variant at every edge like its own inner texels do
- the 24 x 24-block field built with the hashed pick (one variant per 8 x 8 cell, no turn): autocorrelation at the
  8-block step no more than 1/K + 0.12 (two cells at that step show the SAME variant with probability 1/K - that
  share is the Java pick's, not the texture's; the texture adds only its edge bands) and under 0.15 at 4 blocks
- luma p5-p95 range at the RTS camera (plain lane) >= 0.31 (79 levels; r63 0.30, 61b in game 0.30): the critic's
  >= 100 is out of reach under the biome tint's ceiling (albedo white = L 0.58) with near-black under 1%
- the painted light comes from the EAST: LAWN_SUN = (1, 0), and round the darkest 10% the luma 2 px WEST minus 2 px
  EAST >= 0.02 (test_lawn_r62's method, the direction fixed here)
- test_lawn_r62/r63's bands re-stated on the pack's 8 x 8 sheet at 32 texels per block (those files mip to a
  fixed 128 px - 32 texels per block of the 4-block sheet they were written for - so lawn_sheet keeps a 4-block
  default cut of the same recipe for them; their mechanical fix, box_mip(alb, 32 * LAWN_SHEET_BLOCKS), is not this
  lane's to make)
- the flower layer: grass_flower_layer_1..8, 128 x 128 RGBA, 1-6% opaque, 6-12 heads of 0.05-0.11 block, value
  0.75+ everywhere (untinted: real daisy white, buttercup yellow, clover pink), clear of the sprite's edges; and the
  build writes them (SHEET_JOBS) with the sheet variants' sprite names grass_block_top[_k<k>]_g<cell>

Run: python3 -m unittest tools/test_lawn_r64.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402
import texture_camera_sim as sim  # noqa: E402

TINT = sim.hex_rgb(sim.LAWN_TINT)
LAWN_GAIN = np.array([1.025, 0.827, 0.827])     # our shader on the tinted lawn (test_lawn_r63)
_C = {}


def components(mask):
    """Sizes of the 4-connected components of a boolean image (stdlib flood fill; the sprites are small)."""
    seen = np.zeros_like(mask, bool)
    sizes = []
    h, w = mask.shape
    for y0, x0 in zip(*np.nonzero(mask)):
        if seen[y0, x0]:
            continue
        stack, size = [(y0, x0)], 0
        seen[y0, x0] = True
        while stack:
            y, x = stack.pop()
            size += 1
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((ny, nx))
        sizes.append(size)
    return sizes


def variants(res):
    """(H, alb) of every variant at `res` texels per block."""
    key = ("v", res)
    if key not in _C:
        _C[key] = [gen_blocks.lawn_sheet(res, gen_blocks.LAWN_SHEET_BLOCKS, variant=k)
                   for k in range(1, gen_blocks.LAWN_VARIANTS + 1)]
    return _C[key]


def hash_pick(cx, cz, k):
    """A position hash as the Java side picks (any decent mix: the test only needs an even, position-keyed pick)."""
    h = (cx * 0x9E3779B1 + cz * 0x85EBCA77) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 0x2C1B3C6D) & 0xFFFFFFFF
    h ^= h >> 12
    return h % k


def hashed_field(res, cells=3):
    """cells x cells sheets at the RTS mip (32 texels per block), each cell its hashed variant, not turned, tinted."""
    key = ("field", res, cells)
    if key not in _C:
        mips = [sim.box_mip(alb, gen_blocks.LAWN_SHEET_BLOCKS * 32) for _H, alb in variants(res)]
        px = mips[0].shape[0]
        out = np.zeros((cells * px, cells * px, 3))
        for cz in range(cells):
            for cx in range(cells):
                out[cz * px:(cz + 1) * px, cx * px:(cx + 1) * px] = mips[hash_pick(cx, cz, len(mips))]
        _C[key] = np.clip(out * TINT, 0, 1)
    return _C[key]


def sheet_mip(gain=False):
    """variant 1 of the pack's 8 x 8 sheet at 32 texels per block, tinted (and under our shader's lawn gain)."""
    key = ("mip", gain)
    if key not in _C:
        _H, alb = variants(256)[0]
        f = sim.box_mip(alb, gen_blocks.LAWN_SHEET_BLOCKS * 32) * TINT
        _C[key] = np.clip(f * LAWN_GAIN if gain else f, 0, 1)
    return _C[key]


def band_share(L, lo, hi, wavelength=False):
    """test_lawn_r62's measure: the share of the luma variance in features lo..hi px across (half-wavelength; or the
    full wavelength), from the power spectrum."""
    P = np.abs(np.fft.fft2(L - L.mean())) ** 2
    f = np.sqrt(np.add.outer(np.fft.fftfreq(L.shape[0]) ** 2, np.fft.fftfreq(L.shape[1]) ** 2))
    size = (1.0 if wavelength else 0.5) / np.maximum(f, 1e-9)
    return float(P[(size >= lo) & (size <= hi) & (f > 0)].sum() / P[f > 0].sum())


def hue_deg(rgb):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx, mn = rgb.max(-1), rgb.min(-1)
    d = np.maximum(mx - mn, 1e-9)
    h = np.where(mx == r, (g - b) / d % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4))
    return h * 60


def autocorr(L, lag):
    """Normalised autocorrelation of the luma at `lag` px along x and along y (the field wraps: 3 x 3 sheets)."""
    d = L - L.mean()
    return float((d * np.roll(d, lag, 1)).mean() / d.var()), float((d * np.roll(d, lag, 0)).mean() / d.var())


class LawnSheetRound64Test(unittest.TestCase):
    def test_eight_by_eight_in_variants(self):
        self.assertEqual(gen_blocks.LAWN_SHEET_BLOCKS, 8)
        self.assertGreaterEqual(gen_blocks.LAWN_VARIANTS, 2)
        self.assertLessEqual(gen_blocks.LAWN_VARIANTS, 4)          # the Java pick takes up to 4
        H, alb = variants(128)[0]
        self.assertEqual(alb.shape, (1024, 1024, 3))
        self.assertEqual(H.shape, (1024, 1024))

    def test_variants_differ(self):
        """each variant its own detail: the RTS mips of any two correlate under 0.3 (the shared edge bands and the
        common lay direction are all they share)."""
        mips = [sim.luma(sim.box_mip(alb, 256)).ravel() for _H, alb in variants(128)]
        for a in range(len(mips)):
            for b in range(a + 1, len(mips)):
                self.assertLess(abs(np.corrcoef(mips[a], mips[b])[0, 1]), 0.3, "variants %d, %d" % (a + 1, b + 1))

    def test_variants_join_at_every_edge(self):
        """any variant's last column / row joins any other's first like inner neighbours do (test_lawn_r62's seam
        rule, 1.15 x the roughest inner step), in both orders and both axes - the hashed pick puts any two side by
        side."""
        Ls = [alb @ gen_blocks.LUMA for _H, alb in variants(128)]
        for a, La in enumerate(Ls):
            cols = np.abs(np.diff(La, axis=1)).mean(0).max()
            rows = np.abs(np.diff(La, axis=0)).mean(1).max()
            for b, Lb in enumerate(Ls):
                self.assertLessEqual(np.abs(La[:, -1] - Lb[:, 0]).mean(), cols * 1.15, "x edge %d|%d" % (a + 1, b + 1))
                self.assertLessEqual(np.abs(La[-1, :] - Lb[0, :]).mean(), rows * 1.15, "z edge %d|%d" % (a + 1, b + 1))

    def test_hashed_field_does_not_repeat_at_the_sheet_step(self):
        """the critic's measure on the 24 x 24-block hashed field: autocorrelation at the 8-block step no more than
        1/K + 0.12 (K variants: the 1/K is the pick's same-variant pairs, the margin is the edge bands), and under
        0.15 at the 4-block step (r63's repeat was 0.99 there)."""
        L = sim.luma(hashed_field(128))
        k = gen_blocks.LAWN_VARIANTS
        ax, az = autocorr(L, 8 * 32)
        self.assertLessEqual(max(ax, az), 1.0 / k + 0.12)
        ax4, az4 = autocorr(L, 4 * 32)
        self.assertLess(max(abs(ax4), abs(az4)), 0.15)

    def test_luma_range_at_the_camera(self):
        """p5-p95 of the plain lane's luma (one sheet at 32 texels per block, tinted) >= 0.31."""
        L = sim.luma(sheet_mip())
        self.assertGreaterEqual(float(np.percentile(L, 95) - np.percentile(L, 5)), 0.31)

    def test_lit_from_the_east(self):
        """the engine's sun at t5000 is east (+u): LAWN_SUN says so, and round the darkest 10% of the mip the luma
        2 px west (away from the sun) minus 2 px east (toward it) >= 0.02."""
        self.assertEqual(tuple(gen_blocks.LAWN_SUN), (1.0, 0.0))
        L = sim.luma(sheet_mip())
        dark = L <= np.percentile(L, 10)
        west, east = np.roll(L, 2, 1), np.roll(L, -2, 1)
        self.assertGreaterEqual(float(west[dark].mean() - east[dark].mean()), 0.02)

    def test_rounds_62_63_bands_on_the_8x8_sheet(self):
        """test_lawn_r62/r63 mip the sheet to a fixed 128 px (32 texels per block of the 4-block sheet they were
        written for), so they keep measuring lawn_sheet's default 4-block cut of the same recipe; the pack's 8 x 8
        sheet is held to the SAME bands here at 32 texels per block (3 x 3 sheets, as they tile): near-black
        < 0.5% under our gain, speckle 0.40-0.50, 2+ block share 0.08-0.30 (floor 0.15 -> 0.08 with test_lawn_r62's
        on lovish's "D, and lower both floors", Nov 4 ~17:05 ET), fine grain (under 0.2 block over
        0.25-0.6 block), saturation >= 0.58 and L >= 0.34 under our gain, worn soil 0.3-8% (floor 1% -> 0.3% with
        test_lawn_r63's on lovish's "D, and lower both floors") in pieces of 32-4096 px,
        the brightest 0.5% in 8+ specks of 2+ px (4+ of 5+ px, none over 30), and the sheet wraps."""
        f3 = np.tile(sheet_mip(), (3, 3, 1))
        g3 = np.tile(sheet_mip(gain=True), (3, 3, 1))
        L, Lg = sim.luma(f3), sim.luma(g3)
        with self.subTest("no holes under our shader"):
            self.assertLess(float((Lg < 0.15).mean()), 0.005)
        with self.subTest("speckle band"):
            self.assertGreaterEqual(band_share(Lg, 2, 8), 0.40)
            self.assertLess(band_share(Lg, 2, 8), 0.50)
        with self.subTest("block-scale band"):
            share = band_share(L, 64, 1e9, wavelength=True)
            self.assertGreaterEqual(share, 0.08)
            self.assertLessEqual(share, 0.30)
        with self.subTest("fine grain"):
            self.assertGreater(band_share(Lg, 1.5, 6.4), band_share(Lg, 8, 20))
        with self.subTest("saturated and bright"):
            m = sim.measure(g3)
            self.assertGreaterEqual(m["sat"], 0.58)
            self.assertGreaterEqual(m["L"], 0.34)
        with self.subTest("worn soil patches"):
            soil = (hue_deg(f3) < 80) & (L < 0.35)
            self.assertGreaterEqual(float(soil.mean()), 0.003)
            self.assertLessEqual(float(soil.mean()), 0.08)
            sizes = components(soil)
            self.assertGreaterEqual(max(sizes), 32)
            self.assertLessEqual(max(sizes), 4096)
        with self.subTest("flower specks"):
            L1 = sim.luma(sheet_mip())
            sizes = [s for s in components(L1 > np.percentile(L1, 99.5)) if s >= 2]
            # r63's 8 / 4 were per 4 x 4 blocks; this is the 8 x 8 sheet, 4x the area
            self.assertGreaterEqual(len(sizes), 32)
            self.assertGreaterEqual(sum(1 for s in sizes if s >= 5), 16)
            self.assertLessEqual(max(sizes), 30)
        with self.subTest("wraps"):
            _H, alb = variants(256)[0]
            Lb = alb @ gen_blocks.LUMA
            cols = np.abs(np.diff(Lb, axis=1)).mean(0)
            rows = np.abs(np.diff(Lb, axis=0)).mean(1)
            self.assertLessEqual(np.abs(Lb[:, -1] - Lb[:, 0]).mean(), cols.max() * 1.15)
            self.assertLessEqual(np.abs(Lb[-1, :] - Lb[0, :]).mean(), rows.max() * 1.15)

    def test_sprite_names_and_jobs(self):
        """variant 1 keeps the round 62 names (grass_block_top_g1..64); variant k >= 2 is grass_block_top_k<k>_g<cell>
        - GroundSheetRules.variantSprite's spelling (PR #504 c0674be9; the red-first commit guessed _v<k> before the
        Java side had pushed); the build runs the sheet variants and the flower layer."""
        self.assertEqual(gen_blocks.lawn_sheet_sprite(1, 1), "grass_block_top_g1")
        self.assertEqual(gen_blocks.lawn_sheet_sprite(1, 64), "grass_block_top_g64")
        self.assertEqual(gen_blocks.lawn_sheet_sprite(2, 1), "grass_block_top_k2_g1")
        self.assertEqual(gen_blocks.lawn_sheet_sprite(3, 64), "grass_block_top_k3_g64")
        self.assertIn("grass_block_top_sheet", gen_blocks.SHEET_JOBS)
        self.assertIn("grass_flower_layer", gen_blocks.SHEET_JOBS)
        self.assertIn("grass_clover_layer", gen_blocks.SHEET_JOBS)


class FlowerLayerTest(unittest.TestCase):
    """grass_flower_layer_1..8: the UNTINTED floating cutout plane's sprites (the Java side draws them over ~6% of
    the lawn, clustered, picked by a position hash)."""

    def layers(self):
        if "flowers" not in _C:
            _C["flowers"] = [gen_blocks.flower_layer(128, k) for k in range(1, gen_blocks.FLOWER_LAYERS + 1)]
        return _C["flowers"]

    def test_eight_sprites_128(self):
        self.assertEqual(gen_blocks.FLOWER_LAYERS, 8)
        for H, alb, alpha in self.layers():
            self.assertEqual(alb.shape, (128, 128, 3))
            self.assertEqual(alpha.shape, (128, 128))
            self.assertEqual(H.shape, (128, 128))

    def test_a_small_drift_of_heads(self):
        """1-6% opaque: 6-12 heads of 2-3 px at the RTS camera (0.05-0.11 block = 6-14 texels across, 25-160
        texels each), none touching the sprite's edge (a head cut at the block edge is a seam)."""
        for k, (_H, _alb, alpha) in enumerate(self.layers(), 1):
            on = alpha > 0.5
            self.assertGreaterEqual(float(on.mean()), 0.01, "sprite %d" % k)
            self.assertLessEqual(float(on.mean()), 0.06, "sprite %d" % k)
            sizes = [s for s in components(on) if s >= 4]
            self.assertGreaterEqual(len(sizes), 6, "sprite %d" % k)
            self.assertLessEqual(len(sizes), 12, "sprite %d" % k)
            self.assertGreaterEqual(min(sizes), 25, "sprite %d" % k)
            self.assertLessEqual(max(sizes), 160, "sprite %d" % k)
            self.assertFalse(on[:3].any() or on[-3:].any() or on[:, :3].any() or on[:, -3:].any(), "sprite %d" % k)

    def test_real_flower_colours(self):
        """untinted, so the colours are the flowers' own: value (max channel) 0.75+ on every visible texel, and the
        drift holds white heads (daisies, max - min under 0.15), yellow ones (buttercups: R, G over 0.85, B under
        0.55) and a pinkish one (pink = red first, GREEN last: R over B over G, R - G 0.08-0.3; the red-first
        commit had R over G over B, which is peach) somewhere in the eight."""
        kinds = set()
        for k, (_H, alb, alpha) in enumerate(self.layers(), 1):
            vis = alpha > 0.5
            px = alb[vis]
            self.assertGreaterEqual(float(px.max(1).min()), 0.75, "sprite %d" % k)
            spread = px.max(1) - px.min(1)
            if (spread < 0.15).mean() > 0.1:
                kinds.add("white")
            if ((px[:, 0] > 0.85) & (px[:, 1] > 0.85) & (px[:, 2] < 0.55)).mean() > 0.05:
                kinds.add("yellow")
            pink = (px[:, 0] > px[:, 2]) & (px[:, 2] > px[:, 1]) & (px[:, 0] - px[:, 1] > 0.08) & (px[:, 0] - px[:, 1] < 0.3)
            if pink.mean() > 0.05:
                kinds.add("pink")
        self.assertEqual(kinds, {"white", "yellow", "pink"})


if __name__ == "__main__":
    unittest.main()
