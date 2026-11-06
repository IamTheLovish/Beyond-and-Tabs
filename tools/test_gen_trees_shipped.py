"""The tree templates the game grows (bar/.../data/reignofnether/structures/trees, maps/TreeTemplates) are the ones
tools/gen_trees.py made: byte-equal to design/trees, and every template the configured features name is shipped."""
import json
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGN = os.path.join(ROOT, "design", "trees")
DATA = os.path.join(ROOT, "bar", "src", "main", "resources", "data", "reignofnether")
SHIP = os.path.join(DATA, "structures", "trees")
FEATURES = os.path.join(DATA, "worldgen", "configured_feature")


class ShippedTrees(unittest.TestCase):
    def test_shipped_templates_equal_the_generated_ones(self):
        shipped = sorted(f for f in os.listdir(SHIP) if f.endswith(".nbt"))
        self.assertTrue(shipped, "no tree templates shipped")
        for f in shipped:
            with open(os.path.join(SHIP, f), "rb") as a, open(os.path.join(DESIGN, f), "rb") as b:
                self.assertEqual(a.read(), b.read(), f + " differs from design/trees - rerun tools/gen_trees.py")

    def test_every_named_template_is_shipped(self):
        named = set()
        for f in os.listdir(FEATURES):
            with open(os.path.join(FEATURES, f), encoding="utf-8") as fh:
                feature = json.load(fh)
            if feature.get("type") == "reignofnether:template_tree":
                named |= set(feature["config"]["templates"])
        self.assertTrue(named, "no template_tree configured feature")
        for name in sorted(named):
            self.assertTrue(os.path.isfile(os.path.join(SHIP, name + ".nbt")), name + " is named but not shipped")


if __name__ == "__main__":
    unittest.main()
