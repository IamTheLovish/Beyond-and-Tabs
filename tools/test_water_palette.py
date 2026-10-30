"""Water in the environment palette (claude/environment-palette-oct29.md, Oct 29): open water must read LOWER than the
lawn from the RTS camera - darker by a clear step - in a teal-blue that sits with olive grass, not royal violet.

Eyes r54 (Verdant, noon, plain): water luma 0.42 / hue 213 against the lawn's 0.32 - the river read as a painted strip
above the land. A model of the water's pixel, before the world look's grade, from the pack's own numbers: the grey
water texture (tools/gen_blocks.py WATER luma) x the battlefield biome's water colour, at the texture's alpha over a
sand bed (Verdant's bank and bed). The camera has the last word (before / after eyes stills); this guards the numbers.

    python3 -m unittest tools/test_water_palette.py
"""
import colorsys
import json
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BIOME = os.path.join(ROOT, "bar/src/main/resources/data/reignofnether/worldgen/biome/battlefield.json")
GEN = os.path.join(ROOT, "tools/gen_blocks.py")
LUMA = (0.2126, 0.7152, 0.0722)


def water_constants():
    src = open(GEN, encoding="utf-8").read()
    m = re.search(r"^WATER = dict\(([^)]*)\)", src, re.M)
    d = dict(re.findall(r"(\w+)=([-0-9.]+)", m.group(1)))
    sand = re.search(r'"sand":\s*dict\(luma=([0-9.]+)', src)
    return float(d["luma"]), float(d["alpha"]), float(sand.group(1))


def water_colour():
    c = json.load(open(BIOME, encoding="utf-8"))["effects"]["water_color"]
    return ((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0


def modelled_pixel():
    luma, alpha, sand = water_constants()
    wc = water_colour()
    return tuple(alpha * luma * wc[i] + (1 - alpha) * sand for i in range(3))


class WaterPalette(unittest.TestCase):

    def test_open_water_reads_below_the_lawn(self):
        # calibration (eyes r54, Verdant noon, plain, after the grade): this model gave 0.392 where the camera measured
        # 0.42 - the camera reads ~1.07x the model. The palette band 0.24-0.31 (at least 0.06 under the lawn's ~0.33)
        # is therefore a model band of 0.225-0.29
        px = modelled_pixel()
        lum = sum(LUMA[i] * px[i] for i in range(3))
        self.assertGreaterEqual(lum, 0.225, "water too dark - reads as a hole: %.3f" % lum)
        self.assertLessEqual(lum, 0.29, "water as bright as the land: %.3f" % lum)

    def test_teal_blue_not_royal_violet(self):
        h, s, v = colorsys.rgb_to_hsv(*modelled_pixel())
        self.assertTrue(190 <= h * 360 <= 212, "hue %.0f" % (h * 360))
        self.assertTrue(0.25 <= s <= 0.6, "saturation %.2f" % s)

    def test_the_bed_shows_but_does_not_lead(self):
        # Minecraft draws the surface once, so the bed's share is (1 - alpha) everywhere; deeper beds still darken by
        # the light water takes (one level a block) - the depth cue. The bed's share stays under a quarter
        _, alpha, _ = water_constants()
        self.assertTrue(0.75 <= alpha <= 0.9, "alpha %.2f" % alpha)


if __name__ == "__main__":
    unittest.main()
