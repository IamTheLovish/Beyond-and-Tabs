#!/usr/bin/env python3
"""tools/gen_blocks.py: no colour texture may be named like a LabPBR map of another.

Round r35 (eyes run 40810627885, Oct 28): the small tufts were named grass_s, grass_b_s ... - the names of the
specular maps of grass, grass_b ... (<name>_s.png). The game logged "Missing textures in model minecraft:grass#:
minecraft:block/grass_s ..." and the small tufts drew as magenta-and-black squares all over the meadow stills.

Run: python3 tools/test_gen_blocks_names.py"""
import pathlib
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import gen_blocks  # noqa: E402

MAP_SUFFIXES = ("_n", "_s")      # LabPBR: <texture>_n.png normals, <texture>_s.png specular


class TextureNames(unittest.TestCase):
    def colour_textures(self):
        names = set(gen_blocks.TARGETS)
        for model in gen_blocks.PLANT_MODELS.values():
            for t in model.get("textures", {}).values():
                if not t.startswith("#"):
                    names.add(t.split("/")[-1])
        return names

    def test_no_colour_texture_ends_like_a_pbr_map(self):
        bad = sorted(n for n in self.colour_textures() if n.endswith(MAP_SUFFIXES))
        self.assertEqual(bad, [], "these collide with the _n / _s maps of another texture")

    def test_no_colour_texture_is_another_ones_map(self):
        names = self.colour_textures()
        clash = sorted(n + s for n in names for s in MAP_SUFFIXES if n + s in names)
        self.assertEqual(clash, [])


if __name__ == "__main__":
    unittest.main()
