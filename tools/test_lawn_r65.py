#!/usr/bin/env python3
"""Round 65 lawn: lovish's pick D on r65_candidates.png ("D, and lower both floors", Nov 4 ~17:05 ET) after round 64
landed in game and he said "The grass looks blotchy" (16:53 ET). D = A + B + C:
- A: the worn soil scuffs halved, ~30% lighter, feathered and grouped (the r64 blind critic: "scorch marks /
  droppings" - 3-5 px, hard-edged, near-black brown, evenly scattered)
- B: the 2-6 block tonal / hue patches at half amplitude (r64's share of 2+ block variance 0.26 at the test mip; D
  ~0.10 - the calm-patches floor went 0.15 -> 0.08 for it, test_lawn_r62)
- C: the clover-layer leaves clear of the sprite's edge (a leaf cut at the block edge and not continued on the
  neighbour draws the block grid; the sheet itself is continuous)
plus the critic's flower heads: ~3 screen px with a lighter 1 px centre.
These pin D's intent (tests written with the art, red against round 64's generator - noted in the commit):
- no scorch marks: near-black brown (hue < 80, L < 0.22 under our gain) under 0.05% of the mip (r64 1.2%)
- the 2+ block share at the test mip no more than 0.18 (r64 0.26; D 0.10; the r62 band's floor 0.08 still holds);
  re-set Nov 6 for round 70 (lovish "Allow big masses"): the 2-6 block share at most 0.05, the 2+ share at most 0.30
- every clover sprite's outer 3 texels empty
- flower heads 70+ texels (~2.6 px across at 128 texels per block, 30 screen px per block), each one's centre third
  brighter than its outer ring

Run: python3 -m unittest tools/test_lawn_r65.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402
import texture_camera_sim as sim  # noqa: E402
import test_lawn_r64 as r64  # noqa: E402

TINT = sim.hex_rgb(sim.LAWN_TINT)
LAWN_GAIN = np.array([1.025, 0.827, 0.827])


class LawnRound65Test(unittest.TestCase):
    def test_no_scorch_marks(self):
        """variant 1 of the 8 x 8 sheet at 32 texels per block under our gain: brown (hue < 80) AND darker than
        L 0.22 - the near-black specks the critic saw - under 0.05% of the area."""
        f = r64.sheet_mip(gain=True)
        scorch = (r64.hue_deg(f) < 80) & (sim.luma(f) < 0.22)
        self.assertLess(float(scorch.mean()), 0.0005)

    def test_patches_not_blotchy(self):
        """3 x 3 sheets times the world colour field, plain lane (lovish Nov 5: "Yes, as the player sees it").
        - The blotch scale stays calm: 2-6 block wavelengths carry no more than 0.05 of the luma variance. r64's
          "blotchy" patches were 2-6 blocks; r69 measures 0.022 here.
        - All 2+ block wavelengths stay at or under test_lawn_r62's ceiling, 0.30. The floor is r62's 0.08.
        Re-set Nov 6 for round 70 (was: 2+ blocks <= 0.18). After r69 the blind critic named the gap "macro contrast":
        light and dark masses at 6-12 blocks. Those masses are 2+ block variance by definition, so the 0.18 cap
        forbade them. lovish (03:12Z Nov 6, via Eris.16): "Allow big masses (Recommended)". The cap moves to r62's
        ceiling, and the scale he called blotchy gets its own, tighter cap."""
        L = sim.luma(r64.world_mip())
        self.assertLessEqual(r64.band_share(L, 64, 192, wavelength=True), 0.05)
        self.assertLessEqual(r64.band_share(L, 64, 1e9, wavelength=True), 0.30)

    def test_clover_leaves_clear_of_the_block_edge(self):
        self.assertGreaterEqual(gen_blocks.CLOVER_MARGIN, 0.02)
        for k in range(1, gen_blocks.CLOVER_LAYERS + 1):
            _H, _alb, alpha = gen_blocks.clover_layer(128, k)
            on = alpha > 0.02
            self.assertFalse(on[:3].any() or on[-3:].any() or on[:, :3].any() or on[:, -3:].any(), "sprite %d" % k)

    def test_flower_heads_three_px_with_a_lighter_centre(self):
        for k in range(1, gen_blocks.FLOWER_LAYERS + 1):
            _H, alb, alpha = gen_blocks.flower_layer(128, k)
            on = alpha > 0.5
            L = alb @ gen_blocks.LUMA
            heads = r64.components(on)
            self.assertGreaterEqual(min(s for s in heads if s >= 4), 70, "sprite %d" % k)
            # each head: the luma of its centre third above its outer ring's
            seen = np.zeros_like(on)
            ys, xs = np.nonzero(on)
            for y0, x0 in zip(ys, xs):
                if seen[y0, x0]:
                    continue
                stack, pts = [(y0, x0)], []
                seen[y0, x0] = True
                while stack:
                    y, x = stack.pop()
                    pts.append((y, x))
                    for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                        if 0 <= ny < 128 and 0 <= nx < 128 and on[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            stack.append((ny, nx))
                if len(pts) < 4:
                    continue
                p = np.array(pts)
                c = p.mean(0)
                d = np.sqrt(((p - c) ** 2).sum(1))
                rmax = d.max()
                inner = L[p[d <= rmax / 3, 0], p[d <= rmax / 3, 1]].mean()
                outer = L[p[d >= 2 * rmax / 3, 0], p[d >= 2 * rmax / 3, 1]].mean()
                self.assertGreater(inner, outer, "sprite %d head at %s" % (k, c.round(1)))


if __name__ == "__main__":
    unittest.main()
