#!/usr/bin/env python3
"""Round 62 lawn: the 4 x 4-block sheet (lovish Nov 4 ~03:30 ET, "Yes, try it": the lawn's top as a sheet across
blocks, laid by world position by GiantLawnBakedModel - Athena's giant loader would cover the dirt sides too).

What the fresh critic of round 61b measured (claude/round-r61b-critic-nov4.md) and these checks pin, at the RTS camera
(the sheet box-mipped to 32 texels per block, tiled 3 x 3, tinted #6FA64A, not turned):
Since lovish's answer (Nov 5 ~05:00Z, "Yes, measure what ships") these read the SHIPPED 8 x 8 sheet
(gen_blocks.LAWN_SHEET_BLOCKS, 24 x 24 blocks tiled), not the 4-block default cut; bands unchanged.
- 10% near-black (holes, not shade; bench 0%)            -> near-black (L < 0.15) under 1%
- zero lit-side asymmetry (the blade flanks never showed) -> round the darkest 10%, away-from-sun minus toward-sun >= 0.02
- 4% of its variance at 2+ blocks (bench 48%: "calm")     -> >= 8% of the luma variance at wavelengths of 2+ blocks
                                                             (15% until lovish's "D, and lower both floors", Nov 4 ~17:05 ET)
- 60% at 2-8 screen px (speckle; bench 27%)              -> under 50%
and the round 61 gains stay: saturation >= 0.58, a lawn not darker than round 61b (L >= 0.34).

Since lovish's answer (Nov 5 ~05:00Z, "Yes, as the player sees it") the calm-patch and speckle shares read the
sheet TIMES the world colour field (postfx/GrassTint, sim.world_tint) - the big soft drifts live there, not in the
8-block sheet; bands unchanged.

Run: python3 -m unittest tools/test_lawn_r62.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402
import texture_camera_sim as sim  # noqa: E402

_C = {}


def sheet():
    if "s" not in _C:
        _C["s"] = gen_blocks.lawn_sheet(256, gen_blocks.LAWN_SHEET_BLOCKS)   # the shipped 8 x 8 sheet, 2048 x 2048
    return _C["s"]


def field():
    if "f" not in _C:
        _H, alb = sheet()
        m = sim.box_mip(alb, 32 * gen_blocks.LAWN_SHEET_BLOCKS)   # 32 texels per block
        _C["f"] = np.clip(np.tile(m, (3, 3, 1)) * sim.hex_rgb(sim.LAWN_TINT), 0, 1)
    return _C["f"]


def world():
    """the shipped sheet tiled 3 x 3 (24 x 24 blocks) times the world colour field (GrassTint, blended per vertex):
    the lawn as the player sees it."""
    if "w" not in _C:
        _H, alb = sheet()
        m = sim.box_mip(alb, 32 * gen_blocks.LAWN_SHEET_BLOCKS)
        _C["w"] = np.clip(np.tile(m, (3, 3, 1)) * sim.world_tint(3 * gen_blocks.LAWN_SHEET_BLOCKS), 0, 1)
    return _C["w"]


def band_share(L, lo, hi, wavelength=False):
    """Share of the luma variance in features lo..hi px across (half-wavelength; or the full wavelength), from the
    power spectrum."""
    P = np.abs(np.fft.fft2(L - L.mean())) ** 2
    f = np.sqrt(np.add.outer(np.fft.fftfreq(L.shape[0]) ** 2, np.fft.fftfreq(L.shape[1]) ** 2))
    size = (1.0 if wavelength else 0.5) / np.maximum(f, 1e-9)
    return float(P[(size >= lo) & (size <= hi) & (f > 0)].sum() / P[f > 0].sum())


class LawnSheetRound62Test(unittest.TestCase):
    def test_no_holes(self):
        L = sim.luma(field())
        self.assertLess(float((L < 0.15).mean()), 0.01)

    def test_lit_side_shows(self):
        """next to a gap, the clump rim on the gap's away-from-sun side faces the sun (lit), the rim on its sun side
        faces away (dark): luma 2 px away from the sun minus 2 px toward it, round the darkest 10%."""
        L = sim.luma(field())
        dark = L <= np.percentile(L, 10)
        su, sv = gen_blocks.LAWN_SUN
        du, dv = int(round(2 * su)), int(round(2 * sv))
        away = np.roll(np.roll(L, dv, 0), du, 1)          # value at (pixel - 2 * sun)
        toward = np.roll(np.roll(L, -dv, 0), -du, 1)      # value at (pixel + 2 * sun)
        self.assertGreaterEqual(float(away[dark].mean() - toward[dark].mean()), 0.02)

    def test_calm_patches_across_blocks(self):
        """wavelengths of 2+ blocks (64+ px; up to the shipped sheet's own 8-block period), as a BAND
        0.08-0.30. History: the floor 0.15 was set from the critic's numbers (bench 48%, r61b 4%) before any sheet
        existed; 2b44f04 re-set it to 0.08 after the r62 clumps' depth left the patches at 0.103 - lovish (via
        Eris.10, Nov 4 04:54 ET) did not approve that re-set: "make it better and realistic", and 9a402fe restored
        0.15. Then round 64 landed in game with its 2-6 block tonal patches carrying the share (0.25 in game) and
        lovish said (Nov 4 16:53 ET) "The grass looks blotchy"; shown r65's candidates (B = those patches at half
        amplitude, D = A + B + C) he chose (~17:05 ET) "D, and lower both floors". D measures ~0.10 at the test mip,
        so the floor is 0.08 by his choice; the ceiling 0.30 (patches never the main pattern - blotches) stays."""
        share = band_share(sim.luma(world()), 64, 1e9, wavelength=True)
        self.assertGreaterEqual(share, 0.08)
        self.assertLessEqual(share, 0.30)

    def test_not_speckle(self):
        self.assertLess(band_share(sim.luma(world()), 2, 8), 0.50)

    def test_saturated_and_not_dark(self):
        m = sim.measure(field())
        self.assertGreaterEqual(m["sat"], 0.58)
        self.assertGreaterEqual(m["L"], 0.34)

    def test_sheet_wraps_seamlessly(self):
        """the sheet repeats every 8 blocks: its last column / row must join its first like any inner neighbours do
        (within the spread of the inner rows - one row is noisy, so not against their mean)."""
        _H, alb = sheet()
        L = alb @ gen_blocks.LUMA
        cols = np.abs(np.diff(L, axis=1)).mean(0)
        rows = np.abs(np.diff(L, axis=0)).mean(1)
        self.assertLessEqual(np.abs(L[:, -1] - L[:, 0]).mean(), cols.max() * 1.15)
        self.assertLessEqual(np.abs(L[-1, :] - L[0, :]).mean(), rows.max() * 1.15)


if __name__ == "__main__":
    unittest.main()
