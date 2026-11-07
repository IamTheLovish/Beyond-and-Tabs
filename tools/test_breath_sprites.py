"""tools/breath_sprites.py: the dragon breath's flame atlas. Run:
python3 -m unittest discover -s tools -p 'test_breath_sprites.py'
"""
import importlib
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    import numpy as np
    import PIL  # noqa: F401
    HAVE_LIBS = True
except ImportError:
    HAVE_LIBS = False


def load_without_scipy():
    """Import the tool with scipy made unimportable, as on CI."""
    saved = sys.modules.get("scipy", "absent")
    sys.modules["scipy"] = None
    try:
        sys.modules.pop("breath_sprites", None)
        return importlib.import_module("breath_sprites")
    finally:
        if saved == "absent":
            del sys.modules["scipy"]
        else:
            sys.modules["scipy"] = saved


@unittest.skipUnless(HAVE_LIBS, "numpy / pillow not installed")
class BreathSpritesTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.bs = load_without_scipy()
        cls.atlas = np.asarray(cls.bs.draw())

    def frame(self, i):
        n = self.bs.N
        r, c = divmod(i, self.bs.COLS)
        return self.atlas[r * n:(r + 1) * n, c * n:(c + 1) * n]

    def test_atlas_size_matches_the_game(self):
        # BarFxClient.drawFlame reads frame i at u (i % 4) / 4, v (i / 4) / 2
        self.assertEqual(self.atlas.shape, (2 * self.bs.N, 4 * self.bs.N, 4))
        self.assertEqual(self.bs.TONGUES + self.bs.PUFFS, 8)

    def test_no_frame_bleeds_into_its_neighbour(self):
        for i in range(8):
            a = self.frame(i)[..., 3]
            self.assertEqual(int(a[:3, :].max()) + int(a[-3:, :].max()) + int(a[:, :3].max()) + int(a[:, -3:].max()), 0,
                             "frame %d has alpha on its border" % i)

    def test_tongues_point_along_plus_u(self):
        # the head (most alpha) sits right of centre, the tail left: drawFlame lays +u along the motion
        for i in range(self.bs.TONGUES):
            a = self.frame(i)[..., 3].astype(float)
            cols = a.sum(0)
            n = len(cols)
            self.assertGreater(cols[n // 2:].sum(), cols[:n // 2].sum() * 1.3, "tongue %d does not lead with its head" % i)

    def test_every_frame_has_a_body(self):
        for i in range(8):
            a = self.frame(i)[..., 3]
            share = float((a > 64).mean())
            self.assertTrue(0.05 < share < 0.6, "frame %d covers %.2f of its square" % (i, share))

    def test_committed_atlas_is_a_fresh_draw(self):
        path = self.bs.OUT
        self.assertTrue(os.path.isfile(path), "breath_flame.png committed (run tools/breath_sprites.py)")
        from PIL import Image
        self.assertTrue(np.array_equal(np.asarray(Image.open(path).convert("RGBA")), self.atlas),
                        "the committed atlas differs from a fresh draw (run tools/breath_sprites.py)")


if __name__ == "__main__":
    unittest.main()
