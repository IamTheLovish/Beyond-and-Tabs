#!/usr/bin/env python3
"""Round 67 lawn: the grain stops looking combed.

Round 66 won its blind Fable critic (7/10 against r64's 5 and the benchmark's 5; eyes 42016524487), and its worst axis
was "no pattern" (5): "every strand leans the same way (lower-left to upper-right) ... it reads as brushed felt". The
sheet's blades streak 3:1 along one shared heading that drifts only 0.8 rad, and the tufts streak 1.5:1 along it, so
one direction wins everywhere. Round 67 (one variable: the grain's shape) shortens the blade streaks, rounds the
tufts and lets the lay drift further, keeping every earlier band: the lit side (test_lawn_r62/r64 >= 0.02), the range
(test_lawn_r64 0.17-0.23), speckle and calm patches (on sheet x world field).

- no direction rules the sheet: the coherence of the luma's structure tensor over the whole of variant 1 at the RTS
  mip (0 = strokes in every direction, 1 = all one way) is at most 0.17 (round 66: 0.218; the candidate 0.151)

Run: python3 -m unittest tools/test_lawn_r67.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import test_lawn_r64 as r64  # noqa: E402
import texture_camera_sim as sim  # noqa: E402


def coherence(L):
    """(l1 - l2) / (l1 + l2) of the mean gradient structure tensor: how much one stroke direction dominates."""
    gy, gx = np.gradient(L)
    j = np.array([[(gx * gx).mean(), (gx * gy).mean()], [(gx * gy).mean(), (gy * gy).mean()]])
    lo, hi = np.linalg.eigvalsh(j)
    return float((hi - lo) / (hi + lo))


class LawnRound67Test(unittest.TestCase):
    def test_no_direction_rules_the_grain(self):
        self.assertLessEqual(coherence(sim.luma(r64.sheet_mip())), 0.17)


if __name__ == "__main__":
    unittest.main()
