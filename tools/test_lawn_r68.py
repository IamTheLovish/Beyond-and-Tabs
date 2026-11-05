#!/usr/bin/env python3
"""Round 68 lawn: no salt-and-pepper pits.

Round 67 won its blind Fable critic (6.5 against the benchmark's 6 and round 66's 4.5; eyes 42039843473), and its
second flaw was "salt-and-pepper micro-contrast ... scattered near-black 2-4 px pits". Measured in game (clean box
100,240,560,440): the darkest 1% sits (mean - p1) / mean = 0.41 under the mean, the benchmark's 0.26. The pits are the
crease floors: full shade goes toward LAWN64_UNDER x under. Round 68 (one variable: the pit floor) lifts that floor and
re-balances the relief so the east light keeps lovish's lit-side floor (test_lawn_r62 / r64 >= 0.02), keeping every
earlier band (the range 0.17-0.23, speckle and calm patches on sheet x world field, the r67 coherence).

- the pits are shallow: (mean - p1) / mean of variant 1's luma at the RTS mip is at most 0.35 (round 67: 0.394; the
  candidate 0.325)
- and the flecks do not take their place: (p99 - mean) / mean at most 0.25 (round 67: 0.248; the candidate 0.235)

Run: python3 -m unittest tools/test_lawn_r68.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import test_lawn_r64 as r64  # noqa: E402
import texture_camera_sim as sim  # noqa: E402


def tails():
    L = sim.luma(r64.sheet_mip())
    m = float(L.mean())
    p1, p99 = np.percentile(L, [1, 99])
    return (m - p1) / m, (p99 - m) / m


class LawnRound68Test(unittest.TestCase):
    def test_no_dark_pits(self):
        self.assertLessEqual(tails()[0], 0.35)

    def test_no_lime_flecks_in_their_place(self):
        self.assertLessEqual(tails()[1], 0.25)


if __name__ == "__main__":
    unittest.main()
