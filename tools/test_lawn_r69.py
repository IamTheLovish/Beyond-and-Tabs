#!/usr/bin/env python3
"""Round 69 lawn: the turf is alive in hue, not one flat green.

lovish played on Nov 5 (relayed by the coordinator Nov 6 00:20Z, verbatim): "the grass needs major improvments it
looks dull and lifeless". Measured: the turf's hue barely moves (variant 1 x the world colour field at the RTS mip: hue spread about
5.7 deg; the meadow reference lovish picked spreads 79-90 deg between its 10th and 90th percentiles), and the lit tips
and the shaded hollows are the same green - only their value differs. Round 69 (one idea: life from hue, not from more
contrast) warms the tall tips toward yellow-green, cools the hollows toward blue-green and lets single tufts lean
yellower or bluer, each texel keeping its luma, so every earlier band holds (the
luma range 0.17-0.23, the speckle and calm-patch shares, the r67 coherence, the r68 dark and light tails).

- the hue spreads: the standard deviation of the hue (degrees) of variant 1 x the world field at the RTS mip is at
  least HUE_SD
- the tips are warmer than the hollows: the median hue of the tallest 20% of the turf (its height at the same mip) sits
  at least TIP_SPLIT degrees toward yellow (lower hue) from the median hue of the lowest 20%
- the tufts are not dark stamps: lovish's game runs no shader (Embeddium, no Oculus), where the plant quads are
  shade = false and draw their tinted albedo. The sprites were tuned green under a shader (sat 0.30, a green bias) and
  the biome tint multiplied that green again: ~0.55 of the lawn in his clip. Each tuft's tinted luma is at least
  TUFT_RATIO of the lawn's (variant 1, the same tint)

Run: python3 -m unittest tools/test_lawn_r69.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402
import test_lawn_r64 as r64  # noqa: E402
import texture_camera_sim as sim  # noqa: E402

HUE_SD = 7.0
TIP_SPLIT = 6.0
TUFT_RATIO = 0.85
TUFTS = ("grass", "grass_b", "grass_c", "grass_m", "tall_grass_top", "tall_grass_bottom")


def hue_deg(rgb):
    """HSV hue in degrees (0..360) of an (..., 3) array in 0..1."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx, mn = rgb.max(-1), rgb.min(-1)
    d = np.maximum(mx - mn, 1e-9)
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4))
    return h * 60.0


def player_view():
    """Variant 1 at the RTS mip (32 texels per block) x the world colour field, and its height at the same mip."""
    H, alb = r64.variants(256)[0]
    px = gen_blocks.LAWN_SHEET_BLOCKS * 32
    m = sim.box_mip(alb, px)
    h = sim.box_mip(np.repeat(H[..., None], 3, -1), px)[..., 0]
    wt = sim.world_tint(gen_blocks.LAWN_SHEET_BLOCKS)
    return np.clip(m * wt, 0, 1), h


def numbers():
    view, h = player_view()
    hue = hue_deg(view)
    lo, hi = np.percentile(h, [20, 80])
    split = float(np.median(hue[h <= lo]) - np.median(hue[h >= hi]))
    return float(hue.std()), split


def tuft_ratio(name):
    """A tuft's luma over the lawn's, both under the battlefield tint, albedo only (no shader: the plant quads are
    shade = false, so the sprite's tinted albedo is what lovish sees)."""
    tint = sim.hex_rgb(sim.LAWN_TINT)
    d = gen_blocks.m_plant(name, 128)
    tuft = sim.luma(d["alb"][d["alpha"] > 0.5] * tint).mean()
    lawn = sim.luma(r64.variants(256)[0][1] * tint).mean()
    return float(tuft / lawn)


class LawnRound69Test(unittest.TestCase):
    def test_the_hue_spreads(self):
        self.assertGreaterEqual(numbers()[0], HUE_SD)

    def test_the_tips_are_warmer_than_the_hollows(self):
        self.assertGreaterEqual(numbers()[1], TIP_SPLIT)

    def test_the_tufts_are_not_dark_stamps(self):
        for name in TUFTS:
            with self.subTest(name):
                self.assertGreaterEqual(tuft_ratio(name), TUFT_RATIO)


if __name__ == "__main__":
    unittest.main()
