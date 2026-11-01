"""tools/facade_audit.py: a building's faces seen region by region - depth planes, block variety, the biggest single
block's share, relief shapes and speckle - checked against the per-region rules (design/vehicle_labs/briefs/
brass_t1_checklist.md: >= 2 depth planes, >= 3 blocks, no block over 40 %). Run:
python3 -m unittest discover -s tools -p 'test_facade_audit.py'

Written before the tool (lovish's tests-first rule), on small synthetic walls whose answers are known by hand.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import facade_audit as FA  # noqa: E402


def wall(names, x=0):
    """A west-facing wall in the z-y plane at depth x: names[y][z] (row 0 = the bottom)."""
    return {(x, y, z): n for y, row in enumerate(names) for z, n in enumerate(row) if n}


WEST = {"name": "front", "face": "west", "box": [0, 3, 0, 3, 0, 5]}   # x0 x1 y0 y1 z0 z1 (inclusive)


class FacadeAudit(unittest.TestCase):
    def test_flat_single_block_wall_fails_every_rule(self):
        r = FA.audit_region(wall([["stone"] * 6] * 4), WEST)
        self.assertEqual((r["cells"], r["planes"], r["blocks"], r["top_share"]), (24, 1, 1, 100))
        self.assertEqual(sorted(FA.faults(r)), ["blocks 1 < 3", "planes 1 < 2", "stone 100 % > 40 %"])

    def test_piers_proud_of_recessed_bays_make_two_planes(self):
        cells = {}
        for z in range(6):
            for y in range(4):
                cells[(0 if z % 2 == 0 else 1, y, z)] = "pier" if z % 2 == 0 else "bay"
        r = FA.audit_region(cells, WEST)
        self.assertEqual(r["planes"], 2)
        self.assertEqual(r["depths"], {0: 50, 1: 50})

    def test_a_nearer_block_hides_the_one_behind(self):
        cells = wall([["back"] * 6] * 4, x=2)
        cells.update(wall([["front"] * 6] * 4, x=1))
        r = FA.audit_region(cells, WEST)
        self.assertEqual((r["blocks"], r["top_block"], r["planes"]), (1, "front", 1))

    def test_courses_are_structured_not_speckle(self):
        rows = [["a"] * 6, ["b"] * 6, ["c"] * 6, ["a"] * 6]
        r = FA.audit_region(wall(rows), WEST)
        self.assertEqual((r["blocks"], r["speckle"]), (3, 0))
        self.assertEqual(r["top_share"], 50)

    def test_checkerboard_is_all_speckle(self):
        rows = [["a" if (y + z) % 2 else "b" for z in range(6)] for y in range(4)]
        self.assertEqual(FA.audit_region(wall(rows), WEST)["speckle"], 100)

    def test_relief_shapes_are_counted(self):
        rows = [["stone_bricks"] * 6, ["stone_brick_stairs"] * 6, ["stone_brick_slab"] * 3 + ["andesite_wall"] * 3,
                ["stone_bricks"] * 6]
        self.assertEqual(FA.audit_region(wall(rows), WEST)["shapes"], 50)

    def test_one_material_in_three_shapes_is_one_material(self):
        """Variety counts MATERIALS: bricks + their stairs + their slab are one texture (the greybox's wing roofs)."""
        rows = [["red_nether_bricks"] * 6, ["red_nether_brick_stairs"] * 6, ["red_nether_brick_slab"] * 6,
                ["mangrove_planks"] * 3 + ["mangrove_stairs"] * 3]
        r = FA.audit_region(wall(rows), WEST)
        self.assertEqual((r["blocks"], r["top_block"], r["top_share"]), (2, "red_nether_brick", 75))

    def test_south_face_sees_the_highest_z_first(self):
        cells = {(x, y, z): "far" if z == 0 else "near" for x in range(3) for y in range(2) for z in (0, 4)}
        r = FA.audit_region(cells, {"name": "s", "face": "south", "box": [0, 2, 0, 1, 0, 4]})
        self.assertEqual((r["cells"], r["top_block"]), (6, "near"))

    def test_roofs_seen_from_above_take_the_top_cell(self):
        cells = {(x, y, z): "roof" if y == 3 else "wall" for x in range(2) for z in range(2) for y in range(4)}
        r = FA.audit_region(cells, {"name": "r", "face": "up", "box": [0, 1, 0, 3, 0, 1]})
        self.assertEqual((r["cells"], r["top_block"], r["depths"]), (4, "roof", {3: 100}))

    def test_cells_outside_the_box_are_ignored_and_air_is_empty(self):
        cells = wall([["stone"] * 6] * 4)
        cells[(0, 0, 9)] = "gold_block"
        cells[(0, 1, 1)] = "air"
        r = FA.audit_region(cells, WEST)
        self.assertEqual((r["cells"], r["blocks"]), (23, 1))

    def test_a_good_face_has_no_faults(self):
        cells = {}
        for z in range(6):
            for y in range(4):
                proud = z % 3 == 0
                cells[(0 if proud else 1, y, z)] = ("polished_blackstone_bricks" if proud else
                                                    ("red_terracotta", "mangrove_planks", "calcite", "red_terracotta")[y])
        r = FA.audit_region(cells, WEST)
        self.assertEqual(FA.faults(r), [])

    def test_report_from_a_structure_file(self):
        here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        nbt = os.path.join(here, "design/vehicle_labs/brass_t1_greybox.nbt")
        rep = FA.audit(nbt, [{"name": "front", "face": "west", "box": [0, 16, 0, 25, 0, 24]}])
        self.assertEqual(len(rep), 1)
        self.assertGreater(rep[0]["cells"], 300)
        self.assertIn("front", FA.format_report(rep))


if __name__ == "__main__":
    unittest.main()
