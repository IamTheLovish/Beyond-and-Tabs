"""tools/crown_measure.py: the crown lighting lane's one measure (crown / lawn luma from an eyes leaf sweep).

Synthetic stills with known colours: the measure must find each crown from the magenta mask, keep the lawn clear of the
crowns and of other objects (their shadows and AO), and give the exact ratio - so a number it prints on a real sweep is
the crowns' and the lawn's, not a box that caught a post or a shadow. numpy + pillow only (CI's python job).
"""
import os
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import crown_measure as cm  # noqa: E402

LAWN = (100, 140, 60)
POST = (90, 60, 40)
MAGENTA = (170, 50, 160)


def luma(rgb):
    return float(np.dot(np.array(rgb) / 255.0, cm.LUMA))


def scene(crown_colours, h=160, w=320):
    """A lawn with two square crowns (left, right), a brown post between them, and the matching mask still."""
    still = np.zeros((h, w, 3), np.uint8)
    still[:] = LAWN
    mask = still.copy()
    boxes = [(40, 60, 40, 100), (40, 60, 220, 280)]      # y0, size, x0, x1
    for (y0, size, x0, x1), colour in zip(boxes, crown_colours):
        still[y0:y0 + size, x0:x1] = colour
        mask[y0:y0 + size, x0:x1] = MAGENTA
    still[50:90, 150:165] = POST
    mask[50:90, 150:165] = POST
    return still, mask


def write(folder, name, arr):
    Image.fromarray(arr).save(os.path.join(folder, name))


class CrownMeasure(unittest.TestCase):
    def test_the_ratio_is_the_crowns_over_the_open_lawn(self):
        dark, light = (40, 60, 30), (80, 110, 50)
        with tempfile.TemporaryDirectory() as d:
            for v, colours in (("0.20", (dark, dark)), ("0.80", (light, dark))):
                still, mask = scene(colours)
                write(d, "bat_eyes_15_x_rts_leafao%s.png" % v, still)
            write(d, "bat_eyes_15_x_rts_leafmask.png", mask)
            res = cm.run(d, "15_x", "rts")
        self.assertEqual(list(res), ["0.20", "0.80"])
        self.assertAlmostEqual(res["0.20"]["lawn"], luma(LAWN), places=3)
        self.assertAlmostEqual(res["0.20"]["ratio"], luma(dark) / luma(LAWN), places=3)
        # two crowns, left to right, each its own ratio; the overall ratio is pixel-weighted
        per = res["0.80"]["crowns"]
        self.assertEqual(len(per), 2)
        self.assertLess(per[0]["x"], per[1]["x"])
        self.assertAlmostEqual(per[0]["ratio"], luma(light) / luma(LAWN), places=3)
        self.assertAlmostEqual(per[1]["ratio"], luma(dark) / luma(LAWN), places=3)
        self.assertAlmostEqual(res["0.80"]["ratio"], (luma(light) + luma(dark)) / 2 / luma(LAWN), places=3)

    def test_the_lawn_keeps_clear_of_crowns_posts_and_their_shadows(self):
        still, mask = scene(((40, 60, 30), (40, 60, 30)))
        # a dark shadow right beside a crown and beside the post: inside LAWN_GAP, so never lawn
        still[100:100 + cm.LAWN_GAP - 2, 40:100] = (30, 45, 20)
        still[50:90, 165:165 + cm.LAWN_GAP - 2] = (30, 45, 20)
        crowns, lawn, comps = cm.masks(mask.astype(np.float64) / 255.0)
        r = cm.measure(still.astype(np.float64) / 255.0, crowns, lawn, comps)
        self.assertAlmostEqual(r["lawn"], luma(LAWN), places=3)
        self.assertGreater(r["lawn_px"], 1000)
        # the crown mask is eroded one pixel: an anti-aliased edge never counts as crown
        self.assertEqual(r["crown_px"], 2 * (60 - 2) * (60 - 2))

    def test_lawn_texture_specks_do_not_wipe_out_the_lawn(self):
        still, mask = scene(((40, 60, 30), (40, 60, 30)))
        rng = np.random.default_rng(3)
        ys, xs = rng.integers(0, 160, 300), rng.integers(0, 320, 300)
        mask[ys, xs] = (20, 20, 20)                     # dark texels: single pixels, not objects
        crowns, lawn, comps = cm.masks(mask.astype(np.float64) / 255.0)
        self.assertGreater(lawn.sum(), 3000)

    def test_no_mask_or_no_crown_is_an_error_not_a_number(self):
        with tempfile.TemporaryDirectory() as d:
            still, mask = scene(((40, 60, 30), (40, 60, 30)))
            write(d, "bat_eyes_15_x_rts_leafao0.20.png", still)
            with self.assertRaises(FileNotFoundError):
                cm.run(d, "15_x", "rts")
            write(d, "bat_eyes_15_x_rts_leafmask.png", still)   # a mask still with no magenta in it
            with self.assertRaises(ValueError):
                cm.run(d, "15_x", "rts")

    def test_the_band_is_the_one_fixed_before_the_change(self):
        # crown-lighting-notes.md, Nov 6, before any lighting code: 0.75-0.88 of the lawn
        self.assertEqual(cm.BAND, (0.75, 0.88))


if __name__ == "__main__":
    unittest.main()
