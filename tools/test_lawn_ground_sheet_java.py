#!/usr/bin/env python3
"""The lawn sheet is one contract on both sides (Command Center Eris.14, Nov 5: "the grass top's sheetSize from the
sprite count == gen_blocks.LAWN_SHEET_BLOCKS", so the intent of #496's test_lawn_sheet_java.py survives #504 replacing
environment/lawn with environment/ground). tools/gen_blocks.py writes the lawn as LAWN_VARIANTS sheets of
LAWN_SHEET_BLOCKS x LAWN_SHEET_BLOCKS sprites (variant 1 grass_block_top_g<cell>, variant k grass_block_top_k<k>_g<cell>,
cell 1 + x + z * N); the game's GroundSheetRules sizes the sheet from the sprite count and lays the cells out. Round 64
showed what a mismatch does (an 8-wide sheet laid on a 4 x 4 grid: seams and a 4-block repeat; eyes 41991825109 again
on a base without #504). These read the Java source (no game boot):

- the grass block's top is in GroundSheetRules.TABLE, and LAWN_SHEET_BLOCKS is one of its SIZES with N * N <= MAX_CELLS
  - so sheetSize, which counts <top>_g1.. and returns n when the count is exactly n * n, returns LAWN_SHEET_BLOCKS
- LAWN_VARIANTS <= MAX_VARIANTS (a variant past it is never picked)
- the sprite names are the generator's: <top>_g<cell> for variant 1, <top>_k<k>_g<cell> for the others
- the cell rule is the generator's row-major 1 + x + z * N, and the generator cuts cell (x, z) from image column x, row z

Run: python3 -m unittest tools/test_lawn_ground_sheet_java.py
"""
import inspect
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES_JAVA = os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "environment",
                          "ground", "GroundSheetRules.java")


def java():
    with open(RULES_JAVA, encoding="utf-8") as f:
        return f.read()


def int_const(name):
    m = re.search(r"static final int %s = (\d+);" % name, java())
    if m is None:
        raise AssertionError("GroundSheetRules.%s not found" % name)
    return int(m.group(1))


class LawnGroundSheetJavaTest(unittest.TestCase):

    def test_the_grass_top_is_a_sheet_top(self):
        self.assertIn('new Entry("grass_block", "block/grass_block_top"', java())

    def test_sheet_size_from_the_sprite_count_is_the_generators(self):
        n = gen_blocks.LAWN_SHEET_BLOCKS
        m = re.search(r"static final int\[\] SIZES = \{([\d,\s]+)\};", java())
        self.assertIsNotNone(m, "GroundSheetRules.SIZES not found")
        sizes = [int(v) for v in m.group(1).split(",")]
        self.assertIn(n, sizes, "the game would switch an %d x %d lawn sheet off (sheetSize 0)" % (n, n))
        self.assertLessEqual(n * n, int_const("MAX_CELLS"), "sheetSize stops counting before the last cell")
        src = java()
        self.assertIn("while (count <= MAX_CELLS && present.test(sheetSprite(top, count + 1)))", src)
        self.assertIn("if (count == n * n)", src)

    def test_every_variant_the_generator_writes_can_be_picked(self):
        self.assertLessEqual(gen_blocks.LAWN_VARIANTS, int_const("MAX_VARIANTS"))

    def test_sprite_names_are_the_generators(self):
        src = java()
        self.assertIn('return top + "_g" + cell;', src)
        self.assertIn('return variant == 1 ? sheetSprite(top, cell) : top + "_k" + variant + "_g" + cell;', src)
        n = gen_blocks.LAWN_SHEET_BLOCKS
        self.assertEqual(gen_blocks.lawn_sheet_sprite(1, n * n), "grass_block_top_g%d" % (n * n))
        self.assertEqual(gen_blocks.lawn_sheet_sprite(2, 1), "grass_block_top_k2_g1")

    def test_cell_rule_is_the_generators_row_major_order(self):
        self.assertIn("return 1 + Math.floorMod(x, n) + Math.floorMod(z, n) * n;", java())
        src = inspect.getsource(gen_blocks.write_lawn_sheet)
        self.assertIn("lawn_sheet_sprite(variant, 1 + x + z * blocks)", src)
        self.assertIn("sl = (slice(z * res, (z + 1) * res), slice(x * res, (x + 1) * res))", src)


if __name__ == "__main__":
    unittest.main()
