#!/usr/bin/env python3
"""tools/gen_meadow_sampler.py grows the pre-made maps' own meadow (tools/meadow_patches.py, the mirror of
maps/MeadowPatches.java) on its 48 x 48 lawn: nothing on the path or the soil, no tall grass within 2 blocks of them,
short tufts right up to their edges (the blend the eyes run is for), and everywhere else exactly the map's plant."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_meadow_sampler as G  # noqa: E402
import meadow_patches as MP  # noqa: E402


class SamplerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.s, cls.placed, cls.lawn, cls.bare = G.build()

    def test_path_and_soil_are_bare_and_tall_grass_keeps_off(self):
        self.assertTrue(any(b == "dirt_path" for b in self.bare.values()))
        self.assertTrue(any(b in ("dirt", "rooted_dirt") for b in self.bare.values()))
        edge_tufts = 0
        for (x, z), kind in self.placed.items():
            self.assertNotIn((x, z), self.bare)
            c = G.clearance(self.bare, x, z)
            if kind in (MP.TALL, MP.LARGE_FERN):
                self.assertGreater(c, MP.TALL_CLEAR, "tall grass at %d, %d" % (x, z))
            edge_tufts += c == 1 and kind == MP.GRASS
        self.assertGreater(edge_tufts, 20, "no short tufts at the path / soil edges: no blend")

    def test_the_plants_are_the_maps_plants(self):
        layout, mix = MP.Layout(G.PRESET, G.SEED), MP.MIXES[G.PRESET]
        for x, z in self.lawn:
            want = MP.kind_at(mix, layout, G.U0 + x + 0.25, G.V0 + z + 0.5, G.clearance(self.bare, x, z))
            self.assertEqual(self.placed.get((x, z), MP.NONE), want, (x, z))

    def test_the_window_shows_patches_and_open_clover(self):
        layout = MP.Layout(G.PRESET, G.SEED)
        patch = sum(MP.field(layout, G.U0 + x + 0.25, G.V0 + z + 0.5) > MP.T for x, z in self.lawn)
        self.assertTrue(0.25 <= patch / len(self.lawn) <= 0.45, patch / len(self.lawn))
        kinds = set(self.placed.values())
        for k in (MP.GRASS, MP.TALL, MP.DAISY, MP.DANDELION):
            self.assertIn(k, kinds)

    def test_near_points_sit_on_the_lawn(self):
        for name, x, z in G.NEAR:
            self.assertTrue(0 <= x < G.N and 0 <= z < G.N, name)


if __name__ == "__main__":
    unittest.main()
