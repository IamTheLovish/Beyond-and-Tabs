#!/usr/bin/env python3
"""The Enchanted look's Complementary preset (pack/build_packwiz.py ENCHANTED_COMPLEMENTARY) - what players with shaders get.

lovish, Oct 28 21:00: two critics measured the shader lawn ~35% brighter than the real-grass reference (V 0.60 vs 0.43)
and the preset's TM_EXPOSURE 0.90 barely moved it. An eyes A/B at 0.80 / 0.65 / 0.50 (runs 40795492254, 40795541411,
40795595275; lawn luma 0.50 / 0.47 / 0.41, reference 0.40) - he chose 0.65.

    python3 tools/test_shader_preset.py
"""
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent / "pack"))
import build_packwiz  # noqa: E402


def preset():
    return dict(line.split("=", 1) for line in build_packwiz.ENCHANTED_COMPLEMENTARY.splitlines() if line.strip())


class EnchantedPreset(unittest.TestCase):
    def test_exposure_is_the_level_lovish_chose(self):
        self.assertEqual(preset()["TM_EXPOSURE"], "0.65")

    def test_exposure_is_one_of_complementarys_steps(self):
        # Complementary's TM_EXPOSURE list runs 0.40 .. 2.80 in steps of 0.05 (shaders/lib/common.glsl)
        e = float(preset()["TM_EXPOSURE"])
        self.assertTrue(0.40 <= e <= 2.80)
        self.assertAlmostEqual(round(e / 0.05) * 0.05, e, places=9)


if __name__ == "__main__":
    unittest.main()
