"""tools/mist_sprites.py: the valley mist's soft blobs. Run:
python3 -m unittest discover -s tools -p 'test_mist_sprites.py'
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
        sys.modules.pop("mist_sprites", None)
        return importlib.import_module("mist_sprites")
    finally:
        if saved == "absent":
            del sys.modules["scipy"]
        else:
            sys.modules["scipy"] = saved


@unittest.skipUnless(HAVE_LIBS, "numpy / pillow not installed")
class MistSpritesTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.ms = load_without_scipy()
        cls.imgs = [np.asarray(cls.ms.draw(k)) for k in range(cls.ms.VARIANTS)]

    def test_no_scipy(self):
        self.assertNotIn("scipy", [m for m, v in sys.modules.items() if v is not None and m.startswith("scipy")])

    def test_deterministic(self):
        for k in range(self.ms.VARIANTS):
            self.assertTrue(np.array_equal(self.imgs[k], np.asarray(self.ms.draw(k))), "variant %d redraws the same" % k)

    def test_size_and_variants_differ(self):
        self.assertEqual(4, len(self.imgs))
        for im in self.imgs:
            self.assertEqual((128, 128, 4), im.shape)
        for i in range(len(self.imgs)):
            for j in range(i + 1, len(self.imgs)):
                self.assertFalse(np.array_equal(self.imgs[i][..., 3], self.imgs[j][..., 3]), "variants %d and %d" % (i, j))

    def test_alpha_range(self):
        for k, im in enumerate(self.imgs):
            a = im[..., 3].astype(float) / 255
            self.assertGreater(a.max(), 0.8, "variant %d has a body" % k)
            self.assertLessEqual(a.max(), 0.91, "peak alpha ~0.9: the game's alpha does the thinning")
            self.assertGreater((a > 0.05).mean(), 0.2, "a big soft blob, not a dot")
            self.assertLess((a > 0.6).mean(), 0.35, "mostly soft, little solid core")

    def test_soft_edges(self):
        for k, im in enumerate(self.imgs):
            a = im[..., 3].astype(int)
            self.assertEqual(0, a[:3, :].max() + a[-3:, :].max() + a[:, :3].max() + a[:, -3:].max(),
                             "variant %d: the border is clear" % k)
            step = max(np.abs(np.diff(a, axis=0)).max(), np.abs(np.diff(a, axis=1)).max())
            self.assertLessEqual(step, 14, "variant %d: no hard edge between neighbour texels (max step %d)" % (k, step))

    def test_colour_is_constant_and_never_pure_white(self):
        for im in self.imgs:
            rgb = im[..., :3].reshape(-1, 3)
            self.assertTrue((rgb == rgb[0]).all(), "one colour everywhere: no dark fringe under filtering")
            self.assertLess(int(rgb.max()), 255)
            self.assertGreaterEqual(int(rgb[0][2]), int(rgb[0][0]), "a hair cool")

    def test_committed_files_match(self):
        for k, name in enumerate(self.ms.names()):
            path = os.path.join(self.ms.OUT, name + ".png")
            self.assertTrue(os.path.isfile(path), name + " committed (run tools/mist_sprites.py)")
            from PIL import Image
            self.assertTrue(np.array_equal(np.asarray(Image.open(path).convert("RGBA")), self.imgs[k]),
                            name + " matches a fresh draw")


if __name__ == "__main__":
    unittest.main()
