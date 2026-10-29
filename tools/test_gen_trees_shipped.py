"""Maps spec M6: the tree templates the maps plant ship in the jar, byte-equal to the art lane's design/trees set.

Rerun `python3 tools/gen_trees.py` (it writes design/trees and then copies the shipped set) after changing a tree.
Also checks that every template family maps/TreeScatter.java plants is shipped.
"""
import os
import re
import unittest

import gen_trees

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCATTER = os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "maps",
                       "TreeScatter.java")


class ShippedTreesTest(unittest.TestCase):
    def test_every_shipped_template_matches_its_design_copy(self):
        for name in gen_trees.SHIPPED:
            for v in range(1, gen_trees.SHIPPED_VARIANTS + 1):
                f = "%s_%d.nbt" % (name, v)
                with open(os.path.join(gen_trees.OUT, f), "rb") as a, open(os.path.join(gen_trees.SHIP_DIR, f), "rb") as b:
                    self.assertEqual(a.read(), b.read(), f + " differs: rerun tools/gen_trees.py")

    def test_tree_scatter_plants_only_shipped_families(self):
        with open(SCATTER) as fh:
            src = fh.read()
        kinds = re.findall(r'[A-Z]+\("([a-z_]+)", (\d+), \d+, \d+\)', src)
        self.assertTrue(kinds, "no Kind entries found in TreeScatter.java")
        for file, variants in kinds:
            self.assertIn(file, gen_trees.SHIPPED, file + " is planted but not shipped")
            self.assertLessEqual(int(variants), gen_trees.SHIPPED_VARIANTS, file)


if __name__ == "__main__":
    unittest.main()
