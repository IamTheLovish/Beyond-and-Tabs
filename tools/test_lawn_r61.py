#!/usr/bin/env python3
"""Round 61 lawn (lovish Nov 4: round 60 "needs more saturation it looks dull and lifeless. We want realistic organic,
lush, depth, shadows, intricate lines"; he picked concept A "lush meadow" from r61_lawn_concepts at 02:40 ET).

The checks are made where he judges: the RTS camera. The finished grass_block_top (fitted, softened, toned - what the
pack writes) is box-mipped to 32 texels per block, tiled 10 x 10 and tinted by the battlefield biome's #6FA64A
(tools/texture_camera_sim.py; that sim's std ~ the plain lane's in-game std x 0.85-1.2). Round 60 measured there:
L 0.412, p5 0.271, p95 0.495, std 0.071, sat 0.57, meso 0.0166 - a soft felt, its shade gaps no darker than p5 0.27.

Run: python3 -m unittest tools/test_lawn_r61.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402
import texture_camera_sim as sim  # noqa: E402

_CACHE = {}


def lawn_at_rts():
    if "m" not in _CACHE:
        albs = [gen_blocks.scan_textures("grass_block_top", i, 256)["alb"] for i in range(6)]
        field = sim.tile(albs, 10, 32, sim.hex_rgb(sim.LAWN_TINT), seed=3, rotate=False)   # the blockstate turns no variant
        _CACHE["m"] = sim.measure(field)
    return _CACHE["m"]


class LawnRound61Test(unittest.TestCase):
    def test_saturated(self):
        """'more saturation': the tinted lawn's mean colour well past round 60's 0.57."""
        self.assertGreaterEqual(lawn_at_rts()["sat"], 0.60)

    def test_deep_shadow_between_the_tufts(self):
        """'depth, shadows': the darkest 5% (the gaps between tufts) clearly darker than round 60's 0.27."""
        self.assertLessEqual(lawn_at_rts()["p5"], 0.20)

    def test_lit_blades(self):
        """the lit flanks and tips stay bright, so the lawn is not just darker (round 60 p95 0.495)."""
        self.assertGreaterEqual(lawn_at_rts()["p95"], 0.50)

    def test_forms_the_camera_keeps(self):
        """'intricate lines': structure at 1/8-1/2 block (meso), a BAND 0.018-0.035 set where round 61b's grain is.
        History: first floor 0.028 (set before any build); c0d3bd3 re-set it to 0.020 after the lattice fix - not
        approved; 9a402fe restored 0.028. Then lovish chose (Nov 4 ~15:25 ET), asked with the in-game numbers -
        bench 0.005, 61b 0.016-0.018 ("61b looks better"), r62 0.027 ("terrible", tree canopy) - "Set it where 61b
        is": meeting 0.028 meant going back to r62-sized clumps. Floor 0.018 = 61b's grain (it can't drift flat);
        ceiling 0.035 unchanged (above the latticed 0.031, so block-scale blotches or pepper still fail)."""
        m = lawn_at_rts()["meso"]
        self.assertGreaterEqual(m, 0.018)
        self.assertLessEqual(m, 0.035)

    def test_no_block_scale_blotches(self):
        """the block-scale unevenness that drew the lattice stays out (macro; the latticed build measured 0.019)."""
        self.assertLessEqual(lawn_at_rts()["macro"], 0.010)

    def test_not_a_dark_lawn(self):
        """lush, not murky: the mean stays near round 60's (0.41), never a dark mat."""
        self.assertGreaterEqual(lawn_at_rts()["L"], 0.34)

    def test_no_lattice_at_the_block(self):
        """Eyes 41793444430 (round 61, our shader): a diamond lattice on every block - the base tile's shared border
        ring (every variant keeps it, so the blocks join seamlessly) came out darker than its middle by chance
        (luma 0.55 vs 0.69 at the RTS mip), so each block drew a dark frame round a light lozenge. The border and the
        middle of every variant must be the same value at the RTS mip."""
        P = gen_blocks.scan_textures
        for i in range(6):
            L = sim.box_mip(P("grass_block_top", i, 256)["alb"], 32) @ [0.2126, 0.7152, 0.0722]
            ring = list(L[:4].ravel()) + list(L[-4:].ravel()) + list(L[4:-4, :4].ravel()) + list(L[4:-4, -4:].ravel())
            centre = L[8:24, 8:24].mean()
            self.assertLess(abs(centre - sum(ring) / len(ring)), 0.03, "variant %d" % i)

    def test_normals_model_the_tufts(self):
        """the handoff's diagnosis: tilt 4 deg is flat normals - our shader cannot model the tufts; AO 0.15 too low."""
        t = gen_blocks.TARGETS["grass_block_top"]
        self.assertGreaterEqual(t["tilt"], 12.0)
        self.assertGreaterEqual(t["ao"], 0.35)


if __name__ == "__main__":
    unittest.main()
