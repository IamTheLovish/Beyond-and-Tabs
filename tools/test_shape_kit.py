"""tools/shape_kit.py: clean geometry -> blocks. Run: python3 -m unittest discover -s tools -p 'test_*.py'"""
import collections
import math
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import shape_kit as K  # noqa: E402


def kinds(cells):
    return collections.Counter(c.kind for c in cells.values())


class VoxelCounts(unittest.TestCase):
    def test_box_is_exact(self):
        cells = K.voxelize(K.box((0, 0, 0), (4, 2, 3)), edges=False)
        self.assertEqual(len(cells), 5 * 3 * 4)
        self.assertEqual(min(cells), (0, 0, 0))
        self.assertEqual(max(cells), (4, 2, 3))

    def test_sphere_volume(self):
        n = len(K.voxelize(K.sphere((0, 0, 0), 8), edges=False))
        self.assertAlmostEqual(n / (4 / 3 * math.pi * 8 ** 3), 1.0, delta=0.06)

    def test_subtraction_and_intersection(self):
        a = K.box((0, 0, 0), (9, 0, 9))
        hole = K.box((3, 0, 3), (6, 0, 6))
        self.assertEqual(len(K.voxelize(a - hole, edges=False)), 100 - 16)
        self.assertEqual(len(K.voxelize(a & hole, edges=False)), 16)
        self.assertEqual(len(K.voxelize(a | K.box((20, 0, 0), (20, 0, 0)), edges=False)), 101)

    def test_cone_tapers(self):
        cells = K.voxelize(K.cone(0, 0, 6, 0, 12), edges=False)
        width = {y: sum(1 for (x, yy, z) in cells if yy == y and z == 0) for y in range(13)}
        self.assertGreater(width[0], width[6])
        self.assertGreater(width[6], width[11])
        self.assertTrue(all(width[y] >= width[y + 1] for y in range(12)))


class EdgePass(unittest.TestCase):
    def test_gable_slopes_take_stairs_facing_the_ridge(self):
        # a straight 45-degree gable along x, ridge at z = 0: the south slope climbs north, the north slope south
        cells = K.voxelize(K.curved_roof(0, 8, 0, 6, 0, 6, curve=1.0))
        st = [(p, c) for p, c in cells.items() if c.kind == "stairs" and 1 <= p[0] <= 7 and p[2] != 0]
        self.assertTrue(st)
        for (x, y, z), c in st:
            self.assertEqual(c.half, "bottom", (x, y, z))
            self.assertEqual(c.facing, "north" if z > 0 else "south", (x, y, z))

    def test_vertical_walls_stay_full_blocks(self):
        cells = K.voxelize(K.cylinder(0, 0, 6, 0, 8), edges=True)
        side = [c for (x, y, z), c in cells.items() if 1 <= y <= 7]
        self.assertTrue(side)
        self.assertEqual(kinds({i: c for i, c in enumerate(side)}), {"full": len(side)})

    def test_underside_takes_upside_down_stairs(self):
        # an onion dome's bulge: its lower flank faces down and out, so its stairs hang (half = top)
        cells = K.voxelize(K.onion_dome(0, 0, 0, 6, 16))
        halves = {c.half for c in cells.values() if c.kind == "stairs"}
        self.assertEqual(halves, {"bottom", "top"})


class HollowShell(unittest.TestCase):
    def test_dome_shell_has_no_interior_fill_and_is_closed(self):
        cells = K.voxelize(K.sphere((0, 0, 0), 7), shell=1)
        solid = K.voxelize(K.sphere((0, 0, 0), 7), edges=False)
        self.assertLess(len(cells), len(solid) * 0.6)
        self.assertFalse([p for p in cells if math.dist(p, (0, 0, 0)) < 5.0])
        # closed: a flood from the centre through empty cells never gets out of the sphere's box
        seen, todo = {(0, 0, 0)}, [(0, 0, 0)]
        while todo:
            x, y, z = todo.pop()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                q = (x + d[0], y + d[1], z + d[2])
                self.assertLessEqual(max(map(abs, q)), 8, "the shell leaks at %s" % (q,))
                if q not in seen and q not in cells:
                    seen.add(q)
                    todo.append(q)


class PartBridge(unittest.TestCase):
    def test_build_writes_roles_into_a_part(self):
        import gothic_parts as G
        p = G.Part()
        K.build(p, K.cone(0, 0, 4, 0, 8), "roof")
        names = {n for n, _ in p.b.values()}
        pal = p.pal
        self.assertIn(pal["roof"], names)
        self.assertIn(pal["roof_st"], names)
        for n, props in p.b.values():
            if n == pal["roof_st"]:
                self.assertIn(props["facing"], ("north", "south", "east", "west"))


if __name__ == "__main__":
    unittest.main()
