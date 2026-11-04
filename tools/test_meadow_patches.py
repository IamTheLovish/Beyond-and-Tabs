#!/usr/bin/env python3
"""tools/meadow_patches.py is a bit-exact mirror of maps/MeadowPatches.java: the hash and seed mix agree with the
values the Java printed (Nov 4 probe), and the plants of the golden windows (bar/src/test/resources/maps/
meadow_patch_golden.txt, which MeadowPatchesTest checks on the Java side) come out the same."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meadow_patches as M  # noqa: E402


class MirrorTest(unittest.TestCase):
    def test_seed_mix_and_hash_match_java(self):
        self.assertEqual(M.mix_seed(0), 0)
        self.assertEqual(M.mix_seed(42), -4767286540954276203)
        self.assertEqual(M.mix_seed(-7), 7790691224305936752)
        h = M.Layout(M.HIGHLANDS, 42)
        self.assertEqual(h.hash(3, 4), -0.033585107194628816)
        self.assertEqual(h.hash(-1200, 77), -0.3327229724574655)
        self.assertEqual(h.decor_hash(-2.5, 3.5), 0.5456473640039673)   # Java rounds -2.5 up, to -2
        self.assertEqual(h.decor_hash(2.5, -0.5), 0.05166704814221407)

    def test_fields_match_java(self):
        v = M.Layout(M.VERDANT_VALLEY, 0)
        self.assertEqual(M.field(v, 131.7, 52.3), 0.5354884299074272)
        self.assertEqual(M.cluster(v, -40.2, 9.9), 0.9083986696512648)

    def test_golden_windows(self):
        with open(M.GOLDEN) as f:
            self.assertEqual(f.read(), M.golden_text(),
                             "the mirror and the golden file differ: change MeadowPatches.java and this mirror "
                             "together, then python3 tools/meadow_patches.py --golden")

    def test_tall_grass_keeps_off_zones(self):
        mix, inf = M.MIXES[M.VERDANT_VALLEY], float("inf")
        # deep in a patch, a draw that grows tall grass in the open grows short grass near a zone
        self.assertEqual(M.kind_for(mix, 0.9, 0.0, 0.1, 0.1, 0.99, 0.0, inf), M.TALL)
        self.assertEqual(M.kind_for(mix, 0.9, 0.0, 0.1, 0.1, 0.99, 0.0, M.TALL_CLEAR), M.GRASS)
        self.assertEqual(M.kind_for(mix, 0.9, 0.0, 0.1, 0.1, 0.99, 0.0, -5), M.GRASS)
        # open clover grows nothing but the odd flower
        self.assertEqual(M.kind_for(mix, 0.2, 0.0, 0.0, 0.0, 0.99, 0.0, inf), M.NONE)
        self.assertEqual(M.kind_for(mix, 0.2, 0.9, 0.5, 0.5, 0.0, 0.0, inf), mix.flowers[0])


if __name__ == "__main__":
    unittest.main()
