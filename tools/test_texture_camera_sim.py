#!/usr/bin/env python3
"""Tests for tools/texture_camera_sim.py: the box mip, the tint, the measures and the transfer fit."""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import texture_camera_sim as sim  # noqa: E402


class TextureCameraSimTest(unittest.TestCase):
    def test_box_mip_averages_blocks_of_texels(self):
        img = np.zeros((4, 4, 3))
        img[:2, :2] = 1.0
        m = sim.box_mip(img, 2)
        self.assertEqual(m.shape, (2, 2, 3))
        self.assertAlmostEqual(m[0, 0, 0], 1.0)
        self.assertAlmostEqual(m[1, 1, 0], 0.0)

    def test_box_mip_refuses_a_size_that_does_not_divide(self):
        with self.assertRaises(ValueError):
            sim.box_mip(np.zeros((10, 10, 3)), 4)

    def test_tile_multiplies_the_tint(self):
        grey = np.full((64, 64, 3), 0.5)
        f = sim.tile([grey], 3, 32, tint=sim.hex_rgb("6FA64A"))
        self.assertEqual(f.shape, (96, 96, 3))
        np.testing.assert_allclose(f[0, 0], 0.5 * sim.hex_rgb("6FA64A"), atol=1e-9)

    def test_flat_image_has_no_contrast_and_a_stripe_has_meso(self):
        flat = np.full((64, 64, 3), 0.4)
        m = sim.measure(flat)
        self.assertAlmostEqual(m["std"], 0.0, places=6)
        self.assertAlmostEqual(m["meso"], 0.0, places=4)
        stripes = np.zeros((64, 64, 3))
        stripes[:, (np.arange(64) // 8) % 2 == 0] = 1.0   # 8-px bars ~ 1/4 block at 32 px/block
        self.assertGreater(sim.measure(stripes)["meso"], 0.05)

    def test_hue_and_saturation_of_a_green(self):
        m = sim.measure(np.tile(sim.hex_rgb("6FA64A"), (8, 8, 1)))
        self.assertAlmostEqual(m["hue"], 96.0, delta=1.0)
        self.assertAlmostEqual(m["sat"], 0.554, delta=0.01)

    def test_transfer_maps_the_sim_mean_onto_the_game_mean(self):
        rng = np.random.default_rng(1)
        s = rng.uniform(0.2, 0.6, (16, 16, 3))
        g = s * np.array([0.9, 0.8, 0.6])
        gain = sim.fit_transfer(s, g)
        np.testing.assert_allclose(gain, [0.9, 0.8, 0.6], rtol=1e-9)
        np.testing.assert_allclose(sim.apply_transfer(s, gain).reshape(-1, 3).mean(0), g.reshape(-1, 3).mean(0))


if __name__ == "__main__":
    unittest.main()
