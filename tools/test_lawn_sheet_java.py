#!/usr/bin/env python3
"""The lawn sheet's size is one number on both sides (review round 1 on #496, Nov 5): tools/gen_blocks.py writes the
sheet as LAWN_SHEET_BLOCKS x LAWN_SHEET_BLOCKS sprites grass_block_top_g1..g<N*N>, cell 1 + x + z * N, and the
game's GiantLawnCells lays a grass top's cell out with its own SIZE. Round 64 made the sheet 8 x 8 while the Java kept
SIZE = 4, so the game drew the 8-wide sheet's first two rows as a 4 x 4 grid: seams at every z step and every 4th x.
No test tied the two; these do, by reading the Java source (no game boot needed).

- GiantLawnCells.SIZE == gen_blocks.LAWN_SHEET_BLOCKS
- the Java's sheet sprite prefix is the generator's variant-1 name: block/grass_block_top_g<cell>
- the Java's cell rule is 1 + floorMod(x, SIZE) + floorMod(z, SIZE) * SIZE, the generator's 1 + x + z * N
- and the generator writes in that order: write_lawn_sheet names cell 1 + x + z * N the block cut from image column x
  (u) and row z (v) - its source, read like the Java's (review round 2: the generator's order was pinned nowhere)

If #504's GroundSheetRules replaces GiantLawnCells (it sizes the sheet from the sprite count), point these at it.

Run: python3 -m unittest tools/test_lawn_sheet_java.py
"""
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_blocks  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CELLS_JAVA = os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "environment",
                          "lawn", "GiantLawnCells.java")


def java():
    with open(CELLS_JAVA, encoding="utf-8") as f:
        return f.read()


class LawnSheetJavaSizeTest(unittest.TestCase):

    def test_java_size_is_the_sheet_the_generator_writes(self):
        m = re.search(r"static final int SIZE = (\d+);", java())
        self.assertIsNotNone(m, "GiantLawnCells.SIZE not found")
        self.assertEqual(int(m.group(1)), gen_blocks.LAWN_SHEET_BLOCKS,
                         "GiantLawnCells.SIZE must equal gen_blocks.LAWN_SHEET_BLOCKS: the game would lay the sheet's "
                         "cells out on the wrong grid")

    def test_java_sprite_names_are_the_generators(self):
        src = java()
        self.assertIn('TOP = "block/grass_block_top"', src)
        self.assertIn('SHEET = TOP + "_g"', src)
        n = gen_blocks.LAWN_SHEET_BLOCKS
        self.assertEqual(gen_blocks.lawn_sheet_sprite(1, 1), "grass_block_top_g1")
        self.assertEqual(gen_blocks.lawn_sheet_sprite(1, n * n), "grass_block_top_g%d" % (n * n))

    def test_java_cell_rule_is_the_generators_row_major_order(self):
        self.assertIn("return 1 + Math.floorMod(x, SIZE) + Math.floorMod(z, SIZE) * SIZE;", java())

    def test_generator_writes_cells_in_the_same_row_major_order(self):
        import inspect
        src = inspect.getsource(gen_blocks.write_lawn_sheet)
        self.assertIn("lawn_sheet_sprite(variant, 1 + x + z * blocks)", src)
        # cell (x, z) is the block at image column x (u) and image row z (v): rows are the first array axis
        self.assertIn("sl = (slice(z * res, (z + 1) * res), slice(x * res, (x + 1) * res))", src)


if __name__ == "__main__":
    unittest.main()
