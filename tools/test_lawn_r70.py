#!/usr/bin/env python3
"""Round 70 lawn: natural contrast - big masses of shade and sun across the meadow.

lovish (Nov 6 00:30Z, relayed by the coordinator): "I want natural contrast that makes the grass look good like the
realistic minecraft art I sent you". After round 69 the blind critic ranked it best but named the gap: "macro
contrast" - the references have shade regions next to sunlit patches several blocks across, while the lawn's darkest
value is mid-olive and its variation lives inside one block. Measured on the world colour field (postfx/GrassTint,
grass, the battlefield tint #6FA64A, 256 x 256 blocks): 7% of the blocks sit 10%+ below the median luma, and the
darkest fifth is only ~1 deg cooler than the brightest fifth. lovish (03:12Z Nov 6): "Allow big masses
(Recommended)".

Round 70 (one idea: light and shade as masses) gives the grass field its own shaped mass octaves (GRASS_MASS_*): the
shade side darker and a little cooler, the sunlit side a little lighter and warmer. The finer drift octaves step back
so the 2-6 block blotch scale stays calm (test_lawn_r65).
- shade covers the meadow: at least SHADE_AREA of the blocks sit 10%+ below the median luma
- the shade is cooler than the sun: the median hue of the darkest fifth of the blocks sits at least SHADE_COOL
  degrees bluer than the brightest fifth's

Run: python3 -m unittest tools/test_lawn_r70.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import grass_tint_preview as gtp  # noqa: E402
import test_lawn_r69 as r69  # noqa: E402

SHADE_AREA = 0.15
SHADE_COOL = 4.0
BATTLEFIELD = 0x6FA64A
LUMA = np.array([0.2126, 0.7152, 0.0722])


def meadow():
    """GrassTint on grass at the battlefield tint over 256 x 256 blocks (Java's rounding), 0..1 rgb, and its luma."""
    k = gtp.constants()
    x, z = np.meshgrid(np.arange(256.0), np.arange(256.0))
    c = np.round(gtp.apply(BATTLEFIELD, x, z, 1, k)) / 255.0
    return c, c @ LUMA


def numbers():
    c, L = meadow()
    area = float((L < 0.9 * np.median(L)).mean())
    lo, hi = np.percentile(L, [20, 80])
    h = r69.hue_deg(c)
    cool = float(np.median(h[L <= lo]) - np.median(h[L >= hi]))
    return area, cool


class LawnRound70Test(unittest.TestCase):
    def test_shade_covers_the_meadow(self):
        self.assertGreaterEqual(numbers()[0], SHADE_AREA)

    def test_the_shade_is_cooler_than_the_sun(self):
        self.assertGreaterEqual(numbers()[1], SHADE_COOL)


if __name__ == "__main__":
    unittest.main()
