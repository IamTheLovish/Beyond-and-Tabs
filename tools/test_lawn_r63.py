#!/usr/bin/env python3
"""Round 63 lawn: the 4 x 4-block sheet as a CLOVER CARPET (lovish Nov 4 04:23-04:50 ET: concept C "clover + grass
patches"; on round 62's sheet "61b looks better the last one is terrible" - its 0.26-0.52-block clumps read as tree
canopy from the RTS camera; "more blending and contrast between the ground", "needs more shadow and contrasts").

Round 62 landed in game (eyes 41809178294, our shader) within the sim's prediction (sim L 0.352 / near-black 0.7% ->
game 0.343 / 1.1%), so these bands are set on tools/texture_camera_sim.py at the RTS camera (the sheet box-mipped to
32 texels per block, tiled 3 x 3, tinted #6FA64A), the darkness ones under our shader's lawn gain (R 1.025, G 0.827,
B 0.827 - the biome-tint pull that is the shader lane's to fix). They sit beside test_lawn_r62's (which stay):
- near-black (L < 0.15) under 0.5% under our gain (61b 5%, r62 0.7% sim / 1.1% game; the bar in game is 1%)
- lit-side asymmetry round the darkest 10% >= 0.02 (r62's method and floor; r62 0.033, 61b ~0)
- speckle share (luma variance at 2-8 screen px) a BAND 0.40-0.50: the 2-4 px leaf grain IS the carpet, and its
  lit side (the asymmetry above) is the same structure - every setting of the recipe with the lit side showing
  measured 0.45-0.49 (bigger leaves / denser carpets reached 0.42-0.44 and lost the lit side); under 0.40 the grain
  has gone the way of r62's clumps (0.38), 0.50 is r62's ceiling (61b 0.54)
- block-scale share (wavelengths of 2+ blocks) 0.08-0.30, test_lawn_r62's band (0.15 until lovish's "D, and lower
  both floors", Nov 4 ~17:05 ET): here it is the worn-soil patches and the 2-4 block colour patches lovish asked
  for, never blotches
- the grain is FINE, 61b's scale not 62's: more luma variance under 0.2 block than at 0.25-0.6 block
- saturation >= 0.58; L >= 0.34 under our gain (r62 in game 0.343; a white albedo under this gain is 0.50, and the
  cool shade between the leaves and the worn soil are what the brief asked for - L 0.38+ waits on the shader fix)
- worn soil 0.3-8% of the mip in patches, not specks (1% until lovish's "D, and lower both floors", Nov 4 ~17:05
  ET); flower specks 2+ screen px, the brightest thing on the sheet
and the clover layer sprites (grass_clover_layer_1..8, a floating cutout plane another lane draws): 128 x 128 RGBA,
10-25% opaque, pale albedo (the biome tint makes them bright green), nothing near-black.

Since lovish's answer (Nov 5 ~05:00Z, "Yes, measure what ships") the sheet checks read the SHIPPED 8 x 8 sheet
(gen_blocks.LAWN_SHEET_BLOCKS) at 32 texels per block, not the 4-block default cut; bands unchanged. The flower
count reads one whole shipped sheet with its floors scaled by the area (same specks per block).

Since lovish's answer (Nov 5 ~05:00Z, "Yes, as the player sees it") the calm-patch and speckle shares read the
sheet TIMES the world colour field (postfx/GrassTint, sim.world_tint) - the big soft drifts live there, not in the
8-block sheet; bands unchanged.

Run: python3 -m unittest tools/test_lawn_r63.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402
import texture_camera_sim as sim  # noqa: E402

LAWN_GAIN = np.array([1.025, 0.827, 0.827])     # our shader on the tinted lawn (common brief, Nov 4; eyes 41809178294)
_C = {}


def sheet():
    if "s" not in _C:
        _C["s"] = gen_blocks.lawn_sheet(256, blocks=gen_blocks.LAWN_SHEET_BLOCKS)  # the sheet the pack ships (8 x 8 since r64)
    return _C["s"]


def field(gain=True):
    key = "fg" if gain else "f"
    if key not in _C:
        _H, alb = sheet()
        m = sim.box_mip(alb, 32 * gen_blocks.LAWN_SHEET_BLOCKS)                    # 32 texels per block
        f = np.tile(m, (3, 3, 1)) * sim.hex_rgb(sim.LAWN_TINT)
        _C[key] = np.clip(f * LAWN_GAIN if gain else f, 0, 1)
    return _C[key]


def world(gain=True):
    """the shipped sheet tiled 3 x 3 times the world colour field (GrassTint, per vertex): what the player sees."""
    key = "wg" if gain else "w"
    if key not in _C:
        _H, alb = sheet()
        m = sim.box_mip(alb, 32 * gen_blocks.LAWN_SHEET_BLOCKS)
        f = np.tile(m, (3, 3, 1)) * sim.world_tint(3 * gen_blocks.LAWN_SHEET_BLOCKS)
        _C[key] = np.clip(f * LAWN_GAIN if gain else f, 0, 1)
    return _C[key]


def band_share(L, lo, hi, wavelength=False):
    """Share of the luma variance in features lo..hi px across (half-wavelength; or the full wavelength), from the
    power spectrum."""
    P = np.abs(np.fft.fft2(L - L.mean())) ** 2
    f = np.sqrt(np.add.outer(np.fft.fftfreq(L.shape[0]) ** 2, np.fft.fftfreq(L.shape[1]) ** 2))
    size = (1.0 if wavelength else 0.5) / np.maximum(f, 1e-9)
    return float(P[(size >= lo) & (size <= hi) & (f > 0)].sum() / P[f > 0].sum())


def hue_deg(rgb):
    """Per-pixel HSV hue in degrees (vectorised)."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx, mn = rgb.max(-1), rgb.min(-1)
    d = np.maximum(mx - mn, 1e-9)
    h = np.where(mx == r, (g - b) / d % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4))
    return h * 60


def components(mask):
    """Sizes of the 4-connected components of a boolean image (stdlib flood fill; the mips are small)."""
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


class LawnSheetRound63Test(unittest.TestCase):
    def test_no_holes_under_our_shader(self):
        L = sim.luma(field())
        self.assertLess(float((L < 0.15).mean()), 0.005)

    def test_lit_side_shows(self):
        """next to a shadow, the leaf rim on the shadow's away-from-sun side faces the sun (lit), the rim on its sun
        side faces away (dark): luma 2 px away from the sun minus 2 px toward it, round the darkest 10% (the plain
        lane, as test_lawn_r62 measures it; our gain scales every difference by ~0.87)."""
        L = sim.luma(field(gain=False))
        dark = L <= np.percentile(L, 10)
        su, sv = gen_blocks.LAWN_SUN
        du, dv = int(round(2 * su)), int(round(2 * sv))
        away = np.roll(np.roll(L, dv, 0), du, 1)
        toward = np.roll(np.roll(L, -dv, 0), -du, 1)
        self.assertGreaterEqual(float(away[dark].mean() - toward[dark].mean()), 0.02)

    def test_speckle_band(self):
        share = band_share(sim.luma(world()), 2, 8)
        self.assertGreaterEqual(share, 0.40)
        self.assertLess(share, 0.50)

    def test_block_scale_band(self):
        """wavelengths of 2+ blocks (64+ px): the soil and colour patches lovish asked for ("blending ... between the
        ground"), never blotches that read as cloud shadow. test_lawn_r62's band: floor 0.15 -> 0.08 with it on
        lovish's "D, and lower both floors" (Nov 4 ~17:05 ET, after "The grass looks blotchy" at 16:53 - see
        test_lawn_r62 for the history); the ceiling 0.30 stays."""
        share = band_share(sim.luma(world(gain=False)), 64, 1e9, wavelength=True)
        self.assertGreaterEqual(share, 0.08)
        self.assertLessEqual(share, 0.30)

    def test_fine_grain(self):
        """61b's scale, not 62's: of the luma variance, more sits at features under 0.2 block (6 px) than at
        0.25-0.6 block (8-20 px, r62's clumps)."""
        L = sim.luma(field())
        self.assertGreater(band_share(L, 1.5, 6.4), band_share(L, 8, 20))

    def test_saturated_and_bright(self):
        m = sim.measure(field())
        self.assertGreaterEqual(m["sat"], 0.58)
        self.assertGreaterEqual(m["L"], 0.34)

    def test_worn_soil_patches(self):
        """worn ground (brown-ish: hue under 80 deg at the mip, before the gain) 0.3-8% of the area - the clover
        creeps over the patch cores, so a mip pixel of worn soil is mostly a mix - in patches, not specks: the biggest
        4-connected piece 32+ mip px (0.03 block^2), none over 2 x 2 blocks (4096 px). The floor was 1% (round 63,
        set before the build); round 64's blind critic read the soil as "scorch marks / droppings" (hard-edged,
        near-black brown, evenly scattered) and lovish said "The grass looks blotchy" (Nov 4 16:53 ET); r65's
        candidate A halves the scuffs, lightens them ~30% and feathers them - the lighter ones no longer count as
        soil here (0.4%) - and he chose "D, and lower both floors" (~17:05 ET): floor 0.3% by his choice. Round 66:
        the soil specks repeated with the sheet (the same brown spots every 8 blocks) and lovish picked "D1, no soil
        specks" and "Re-set to the new look" (Nov 5: "With no specks, the worn-soil minimum goes"): no floor; at most
        8%, and any soil there is comes in pieces of 32-4096 px."""
        f = field(gain=False)
        soil = (hue_deg(f) < 80) & (sim.luma(f) < 0.35)
        share = float(soil.mean())
        self.assertLessEqual(share, 0.08)
        sizes = components(soil) or [0]
        if share >= 0.0005:                          # specks of soil, if any, still come in patches
            self.assertGreaterEqual(max(sizes), 32)
        self.assertLessEqual(max(sizes), 4096)

    def test_flower_specks(self):
        """under the biome tint a flower cannot be white - only the brightest thing on the sheet (albedo G 1): the
        top 0.5% of the 8 x 8 sheet's mip forms 32+ specks of 2+ px, 16+ of them 5+ px (the buttercups, 0.1 block = 7
        mip px; 8 / 4 per 4 x 4 blocks, scaled by area), none a blotch over 30 px."""
        _H, alb = sheet()
        f = np.clip(sim.box_mip(alb, 32 * gen_blocks.LAWN_SHEET_BLOCKS) * sim.hex_rgb(sim.LAWN_TINT), 0, 1)
        L = sim.luma(f)
        sizes = [s for s in components(L > np.percentile(L, 99.5)) if s >= 2]
        self.assertGreaterEqual(len(sizes), 32)
        self.assertGreaterEqual(sum(1 for s in sizes if s >= 5), 16)
        self.assertLessEqual(max(sizes), 30)

    def test_sheet_wraps_seamlessly(self):
        """the sheet repeats every 8 blocks: its last column / row must join its first like any inner neighbours do."""
        _H, alb = sheet()
        L = alb @ gen_blocks.LUMA
        cols = np.abs(np.diff(L, axis=1)).mean(0)
        rows = np.abs(np.diff(L, axis=0)).mean(1)
        self.assertLessEqual(np.abs(L[:, -1] - L[:, 0]).mean(), cols.max() * 1.15)
        self.assertLessEqual(np.abs(L[-1, :] - L[0, :]).mean(), rows.max() * 1.15)


class CloverLayerTest(unittest.TestCase):
    """grass_clover_layer_1..8: the floating cutout plane's sprites (another lane's Java looks them up by name and
    skips the layer when they are missing)."""

    def layers(self):
        if "layers" not in _C:
            _C["layers"] = [gen_blocks.clover_layer(128, k) for k in range(1, gen_blocks.CLOVER_LAYERS + 1)]
        return _C["layers"]

    def test_eight_sprites_128(self):
        self.assertEqual(gen_blocks.CLOVER_LAYERS, 8)
        for H, alb, alpha in self.layers():
            self.assertEqual(alb.shape, (128, 128, 3))
            self.assertEqual(alpha.shape, (128, 128))
            self.assertEqual(H.shape, (128, 128))

    def test_mostly_transparent(self):
        for _H, _alb, alpha in self.layers():
            cover = float((alpha > 0.5).mean())
            self.assertGreaterEqual(cover, 0.10)
            self.assertLessEqual(cover, 0.25)

    def test_pale_lit_leaves(self):
        """the tint (#6FA64A) makes them bright green: visible albedo luma 0.55+ on average, none under 0.25."""
        for _H, alb, alpha in self.layers():
            vis = alpha > 0.5
            L = (alb @ gen_blocks.LUMA)[vis]
            self.assertGreaterEqual(float(L.mean()), 0.55)
            self.assertLess(float((L < 0.25).mean()), 0.01)

    def test_leaf_sized_clusters(self):
        """leaves 0.06-0.12 block in clusters: several opaque pieces per sprite, none a quarter of it."""
        for _H, _alb, alpha in self.layers():
            sizes = components(alpha > 0.5)
            self.assertGreater(len(sizes), 8)
            self.assertLess(max(sizes), 128 * 128 // 4)


if __name__ == "__main__":
    unittest.main()
