#!/usr/bin/env python3
"""Round 66 lawn: lovish's "D1" - softer, diffuse, not patterned.

lovish on round 64 in game (Nov 4 ~23:10Z): "this grass looks like it has too much going on it needs to be softer",
"the blend needs to diffuse a bit better", "it looks too symettrical or patterned". The blotches outlined on the r64
still were one size (2-6 blocks), evenly spread: a camouflage print. Half of them were not the texture at all but
postfx/GrassTint's dark CLUMPS (2-7 blocks, 14% darker) and its 3.5-5 block value MOTTLE. He picked mock D1 (grain
contrast -40%, the blotches replaced by broad, uneven drifts), then, shown D / D1 with soil / D1 without (Nov 5),
"D1, no soil specks" - the soil specks repeated with the sheet, the same brown spots every 8 blocks.

So the 8 x 8 sheet carries the grain only, and the world colour field carries every variation bigger than a block:
- the sheet alone (variant 1, 3 x 3 tiles, 32 texels per block) keeps <= 0.03 of its luma variance at 2+ blocks
  (r65 D: 0.087) - its tonal patches are gone; the drifts are the world field's
- no soil specks in any variant (brown, hue < 80, darker than L 0.35 at the mip: under 0.05%; r65 D ~0.5%)
- GrassTint has no equal-size blobs on grass: GRASS_CLUMP_DARK = 0 and GRASS_MOTTLE = 0 (the 3.5-7 block value terms
  retired for grass; leaves keep them)
- the field as the player sees it (the hashed 24 x 24-block field of sheet variants times GrassTint, per vertex) has
  no favourite blob size: its luma power per octave of wavelength does not fall from 8-16 blocks to 4-8 to 2-4
  (a camouflage print has a bump at one size; drifts are 1/f-like)
test_lawn_r64's range band (0.17-0.23) and the calm / speckle bands on sheet x world field are the rest of D1.

Run: python3 -m unittest tools/test_lawn_r66.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402
import grass_tint_preview as gtp  # noqa: E402
import test_lawn_r64 as r64  # noqa: E402
import texture_camera_sim as sim  # noqa: E402

PX = 32


def octave_density(L, lo_blocks, hi_blocks):
    """luma power at wavelengths lo..hi blocks, per octave."""
    P = np.abs(np.fft.fft2(L - L.mean())) ** 2
    f = np.sqrt(np.add.outer(np.fft.fftfreq(L.shape[0]) ** 2, np.fft.fftfreq(L.shape[1]) ** 2))
    wl = 1.0 / np.maximum(f, 1e-9) / PX
    return float(P[(wl >= lo_blocks) & (wl < hi_blocks) & (f > 0)].sum() / np.log2(hi_blocks / lo_blocks))


class LawnRound66Test(unittest.TestCase):
    def test_the_sheet_carries_the_grain_only(self):
        L = sim.luma(np.tile(r64.sheet_mip(), (3, 3, 1)))
        self.assertLessEqual(r64.band_share(L, 64, 1e9, wavelength=True), 0.03)

    def test_no_soil_specks_in_the_sheet(self):
        tint = sim.hex_rgb(sim.LAWN_TINT)
        for k, (_H, alb) in enumerate(r64.variants(256), 1):
            f = np.clip(sim.box_mip(alb, gen_blocks.LAWN_SHEET_BLOCKS * PX) * tint, 0, 1)
            soil = (r64.hue_deg(f) < 80) & (sim.luma(f) < 0.35)
            self.assertLess(float(soil.mean()), 0.0005, "variant %d" % k)

    def test_grass_tint_has_no_equal_size_blobs(self):
        k = gtp.constants()
        self.assertEqual(k["GRASS_CLUMP_DARK"], 0.0)     # the grass's own (leaves keep rounds 1-7's clumps)
        self.assertEqual(k["GRASS_MOTTLE"], 0.0)

    def test_drifts_have_no_favourite_size(self):
        cells = 3
        field = r64.hashed_field(256, cells) / sim.hex_rgb(sim.LAWN_TINT)       # untinted
        L = sim.luma(np.clip(field * sim.world_tint(cells * gen_blocks.LAWN_SHEET_BLOCKS, PX), 0, 1))
        d24, d48, d816 = (octave_density(L, a, b) for a, b in ((2, 4), (4, 8), (8, 16)))
        self.assertLessEqual(d24, d48, "2-4 blocks %.4g over 4-8 blocks %.4g" % (d24, d48))
        self.assertLessEqual(d48, d816, "4-8 blocks %.4g over 8-16 blocks %.4g" % (d48, d816))


if __name__ == "__main__":
    unittest.main()
