"""tools/plant_sprites.py + the plant wiring in tools/gen_blocks.py (round 34, the tufts from the RTS camera).

Run: python3 -m unittest discover -s tools -p 'test_*.py'

The r33 critic scored the tufts 4/10 (default) and 6/10 (preset): ~40 blades 4 texels wide at 128x, minified ~4x at the
default RTS zoom (mip 2, 32 texels per block), blurred into smudges. Round 34 redraws them as a few bold blades
(claude/research-grass-rts-zoom-oct28.md); these checks keep the rules that make that work, and keep every blockstate
pointing at a model and a texture the pack really writes when the number of shapes changes.
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_blocks  # noqa: E402
import plant_sprites as ps  # noqa: E402

N = 128


def _mip_alpha(alpha, levels):
    """Box-filtered alpha, `levels` halvings down, cut at 0.5 - how much of the sprite is still drawn there."""
    a = alpha.astype(np.float64)
    for _ in range(levels):
        h, w = a.shape
        a = a.reshape(h // 2, 2, w // 2, 2).mean((1, 3))
    return a >= 0.5


def _sprites():
    """name -> the drawn alpha of every plant sprite (both halves of the two-block ones, as one canvas)."""
    out = {}
    for v in range(3):
        H, sh, green, straw = ps.grass_clump(N, np.random.default_rng(7 + v), variant=v)
        out["grass_%d" % v] = np.maximum(green, straw)
    for v in range(2):
        H, sh, green, straw = ps.grass_clump(N, np.random.default_rng(17 + v), tall=True, variant=v)
        out["tall_grass_%d" % v] = np.maximum(green, straw)
        out["fern_%d" % v] = ps.fern(N, np.random.default_rng(27 + v), variant=v)[2]
    out["large_fern"] = ps.fern(N, np.random.default_rng(37), large=True)[2]
    return out


class PlantSprites(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sprites = _sprites()

    def test_blades_survive_the_rts_mip(self):
        # at the default RTS zoom the atlas is read at mip 2: most of the drawn area must still be there
        for name, a in self.sprites.items():
            full = (a > 0.5).mean()
            kept = _mip_alpha(a > 0.5, 2).mean() / max(full, 1e-9)
            self.assertGreaterEqual(kept, 0.9, "%s keeps %.2f of its area at mip 2" % (name, kept))

    def test_short_grass_area_and_mass(self):
        for v in range(3):
            a = self.sprites["grass_%d" % v] > 0.5
            area = a.mean()
            self.assertTrue(0.22 <= area <= 0.30, "grass_%d covers %.3f of the card" % (v, area))
            cols = a.sum(0)
            mid = cols[int(0.2 * N):int(0.8 * N)].sum() / cols.sum()
            self.assertGreaterEqual(mid, 0.6, "grass_%d: %.2f of the mass in the central 60%%" % (v, mid))

    def test_short_grass_base_is_one_wedge(self):
        # the lowest ~20% of a tuft is one solid wedge: no gaps between its leftmost and rightmost blade
        for v in range(3):
            a = self.sprites["grass_%d" % v] > 0.5
            for y in range(int(0.82 * N), N):
                xs = np.nonzero(a[y])[0]
                self.assertTrue(len(xs) and xs[-1] - xs[0] + 1 == len(xs), "grass_%d row %d has a gap" % (v, y))

    def test_no_dark_texels_under_the_cutout(self):
        # MC-114265: mipmaps pull the colour of transparent texels into the edges - fill it from the nearest blade
        H, sh, green, straw = ps.grass_clump(N, np.random.default_rng(3), variant=0)
        rgb = np.stack([sh * 1.08, sh, sh * 0.8], -1)
        out = ps.bleed(rgb, green)
        self.assertTrue(np.allclose(out[green > 0], rgb[green > 0]))
        self.assertGreater(out[green == 0].min(), 0.3)


class PlantWiring(unittest.TestCase):
    def _textures(self, model):
        return {v.split("/")[-1] for k, v in gen_blocks.MODEL_OVERRIDES[model]["textures"].items()}

    def test_every_blockstate_names_a_written_model_and_textures(self):
        states = dict(gen_blocks.TUFT_BLOCKSTATES, **gen_blocks.TALL_BLOCKSTATES)
        for block, variants in states.items():
            lists = variants.values() if isinstance(variants, dict) else [variants]
            for lst in lists:
                for v in lst:
                    model = v["model"].split("/")[-1]
                    self.assertIn(model, gen_blocks.MODEL_OVERRIDES, block)
                    for tex in self._textures(model):
                        self.assertIn(tex, gen_blocks.RECIPES, "%s -> %s" % (model, tex))

    def test_three_grass_shapes_and_two_ferns(self):
        for block, count in (("grass", 3), ("fern", 2)):
            shown = {gen_blocks.MODEL_OVERRIDES[v["model"].split("/")[-1]]["textures"]["cross"]
                     for v in gen_blocks.TUFT_BLOCKSTATES[block]}
            self.assertEqual(len(shown), count, "%s shows %s" % (block, sorted(shown)))

    def test_tall_grass_halves_line_up(self):
        # DoublePlantBlock seeds both halves with the lower block's position, so the same index is picked in both
        # lists: index i of the bottom list must be the same shape as index i of the top list
        st = gen_blocks.TALL_BLOCKSTATES["tall_grass"]
        low, up = st["half=lower"], st["half=upper"]
        self.assertEqual(len(low), 2)
        self.assertEqual(len(low), len(up))
        for lo, hi in zip(low, up):
            b = gen_blocks.MODEL_OVERRIDES[lo["model"].split("/")[-1]]["textures"]["cross"]
            t = gen_blocks.MODEL_OVERRIDES[hi["model"].split("/")[-1]]["textures"]["cross"]
            self.assertEqual(b.replace("_bottom", ""), t.replace("_top", ""))


if __name__ == "__main__":
    unittest.main()
