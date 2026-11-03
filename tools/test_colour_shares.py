"""tools/colour_shares.py: colour classes (red / dark / grey / pale) and hue, saturation and luma medians measured
on a render's or a still's pixels, on a named crop. Run:
python3 -m unittest discover -s tools -p 'test_colour_shares.py'

Written before the tool, on synthetic images whose answers are known by hand.
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    from PIL import Image
    HAVE_PIL = True
except ImportError:
    HAVE_PIL = False
if HAVE_PIL:
    import colour_shares as CS  # noqa: E402

RED, DARK, GREY, PALE, SKY = (180, 30, 30), (20, 20, 20), (128, 128, 128), (230, 230, 230), (90, 150, 230)


def strips(*colours, w=10, h=10):
    """Vertical strips, w px each, left to right."""
    im = Image.new("RGB", (w * len(colours), h))
    for i, c in enumerate(colours):
        im.paste(c, (i * w, 0, (i + 1) * w, h))
    return im


@unittest.skipUnless(HAVE_PIL, "Pillow not installed")
class ColourShares(unittest.TestCase):
    def test_each_class_is_recognised(self):
        self.assertEqual(CS.shares(strips(RED, DARK, GREY, PALE)), {"red": 25, "dark": 25, "grey": 25, "pale": 25})

    def test_sky_and_grass_are_left_out_of_the_shares(self):
        self.assertEqual(CS.shares(strips(RED, SKY, SKY, DARK)), {"red": 50, "dark": 50, "grey": 0, "pale": 0})

    def test_the_renderers_flat_sky_is_left_out(self):
        """structure_render's sky (165, 190, 215) read as 'pale': the r1 front measured pale 52 % until it was masked
        by hand (builder and critic, Oct 28). Calcite / quartz (near-neutral) stay pale."""
        self.assertEqual(CS.shares(strips(RED, (165, 190, 215), (165, 190, 215), DARK)),
                         {"red": 50, "dark": 50, "grey": 0, "pale": 0})
        self.assertEqual(CS.shares(strips((223, 223, 223), (231, 226, 215)))["pale"], 100)

    def test_a_crop_measures_only_inside_it(self):
        im = strips(RED, RED, PALE, PALE)
        self.assertEqual(CS.shares(im, (20, 0, 40, 10))["pale"], 100)

    def test_maroon_counts_as_dark_not_red(self):
        """Red nether brick renders maroon (78, 14, 14): it reads near-black from the camera (pilot r3)."""
        self.assertEqual(CS.shares(strips((40, 8, 8)))["dark"], 100)

    def test_medians_on_a_crop(self):
        m = CS.medians(strips(RED, RED, GREY), (0, 0, 20, 10))
        # luma of (180, 30, 30): 0.299 * 180 + 0.587 * 30 + 0.114 * 30 = 74.85 (first written 77: a hand slip)
        self.assertEqual((round(m["hue"]), round(m["sat"], 2), round(m["luma"])), (0, 0.83, 75))

    def test_cli_reads_a_file(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "x.png")
            strips(RED, DARK).save(p)
            self.assertIn("red 50", CS.report(p, (0, 0, 20, 10)))


if __name__ == "__main__":
    unittest.main()
