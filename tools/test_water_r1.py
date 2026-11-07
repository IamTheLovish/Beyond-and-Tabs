"""Water round 1 (texture pack lane, Nov 7): the river stops reading as woven denim.

Blind critic r0 (side-session notes critic-texturepack-r0.md) scored the water 3/10 against Complementary's 6.5:
"uniform 1-texel teal noise ... reads as denim/carpet, opaque, no depth". Measured on main's eyes still
(81f2c5c, bat_eyes_12_river_crossing.png, no shader, open water below the mist): hue 200-206 (already teal-blue),
luma 0.31-0.34 against the lawn's 0.41.

Cause (the RTS camera sim, rotate=False because fluids are never turned): water_still is a 1-block tile drawn
unturned on every block, so its pattern repeats exactly each block. At 10% contrast (rel. luma std 0.099 at the
camera) that repeat is the weave. Bands inside one tile would repeat the same way, and Athena's giant sheets do not
reach fluids, so the fix is to quiet the repeat (the ripple moves to the normal map, which the shader reads) and let
the bed show through (alpha 0.80): sand, gravel and depth vary from block to block and do not repeat.

The value (0.235-0.27 on camera) is test_water_palette's band, now with the no-shader calibration measured above.

    python3 -m unittest tools/test_water_r1.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks as g  # noqa: E402

LUMA = np.array([0.2126, 0.7152, 0.0722])
TINT = __import__("test_water_palette").water_colour()     # battlefield.json water_color
RES = 128


def camera(alb):
    """The tile as the RTS camera draws open water: unturned blocks, nearest texel, mip blend (rts_view)."""
    img, _ = g.rts_view(alb, tint=TINT, rotate=False, blocks=8)
    return img @ LUMA


def fine_share(L):
    """Share of the luma variance at wavelengths of 2 screen px or less (single-texel grain)."""
    P = np.abs(np.fft.fft2(L - L.mean())) ** 2
    f = np.fft.fftfreq(L.shape[0])
    fx, fy = np.meshgrid(f, f)
    return float(P[np.hypot(fx, fy) >= 0.5].sum() / P.sum())


class WaterR1(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.strip, cls.alpha = g.water_albedo(RES)
        cls.frames = [cls.strip[i * RES:(i + 1) * RES] for i in range(g.WATER["frames"])]

    def test_the_block_repeat_is_quiet(self):
        # main: 0.099 (the weave). Under ~4.5% a pattern repeating every block no longer reads as cloth; above 1.5%
        # the surface still moves (the animation) and is not a flat plastic sheet.
        for i in (0, 8, 16, 24):
            L = camera(self.frames[i])
            rel = L.std() / L.mean()
            self.assertLessEqual(rel, 0.045, "frame %d: the block repeat shows (rel. luma std %.3f)" % (i, rel))
            self.assertGreaterEqual(rel, 0.015, "frame %d: dead flat water (rel. luma std %.3f)" % (i, rel))

    def test_no_single_texel_grain(self):
        # the critic's "1-texel noise": what is left of the ripple is soft, not grain (main 0.024 - a guard)
        for i in (0, 16):
            self.assertLessEqual(fine_share(camera(self.frames[i])), 0.05)

    def test_the_bed_shows_through(self):
        # critic r0: alpha 0.7-0.8 so the bed shows; test_water_palette keeps the bed under a quarter (>= 0.75)
        a = round(float(np.mean(self.alpha)), 3)   # 0.80 is stored as 204/255
        self.assertTrue(0.75 <= a <= 0.80, "alpha %.2f" % a)

    def test_frames_hold_one_value(self):
        # one surface, moving: no frame brighter than another by more than 1% (flicker)
        m = [float(camera(f).mean()) for f in self.frames[::4]]
        self.assertLessEqual(max(m) - min(m), 0.01 * float(np.mean(m)), m)


    def test_slate_not_bright_blue(self):
        # critic r1 (critic-water-r1.md): r1 reads "too blue" at camera S 0.385 vs Complementary's 0.11. Within
        # test_water_palette's floor (S >= 0.25, the Oct 29 palette) the modelled pixel goes to S <= 0.30 (r1: 0.41)
        import colorsys
        import test_water_palette as wp
        s = colorsys.rgb_to_hsv(*wp.modelled_pixel())[1]
        self.assertLessEqual(s, 0.30, "saturation %.3f" % s)

if __name__ == "__main__":
    unittest.main()
