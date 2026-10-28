"""tools/shape_kit.py: SDF shapes voxelized into roles, with stairs / slabs on the slopes. Run:
python3 -m unittest discover -s tools -p 'test_shape_kit.py'

Cell convention under test: cell (x, y, z) spans [x - 0.5, x + 0.5] x [y, y + 1] x [z - 0.5, z + 0.5], so a shape
centred on (0, 0) sits on the column x = z = 0 and y = 0 is the floor.
"""
import math
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    import numpy  # noqa: F401  - the CI lint job may not install it; skip there rather than fail
    HAVE_NUMPY = True
except ImportError:
    HAVE_NUMPY = False
if HAVE_NUMPY:
    import gen_structures as gs  # noqa: E402
    import gothic_parts as gp  # noqa: E402
    import shape_kit as sk  # noqa: E402

VEC = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def kinds(cells, kind):
    return {p: c for p, c in cells.items() if c[0] == kind}


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class Counts(unittest.TestCase):
    def test_cells_box_is_exact(self):
        cells = sk.voxelize(sk.cells(0, 3, 0, 2, 0, 4))
        self.assertEqual(len(cells), 4 * 3 * 5)
        self.assertTrue(all(c == ("full",) for c in cells.values()))
        self.assertEqual(min(cells), (0, 0, 0))
        self.assertEqual(max(cells), (3, 2, 4))

    def test_cylinder_area(self):
        cells = sk.voxelize(sk.cylinder(5, 0, 4), edges=False)
        self.assertEqual({y for _, y, _ in cells}, {0, 1, 2, 3})
        per_layer = len(cells) / 4
        self.assertAlmostEqual(per_layer, math.pi * 25, delta=0.1 * math.pi * 25)

    def test_booleans(self):
        a, b = sk.cells(0, 3, 0, 0, 0, 0), sk.cells(2, 5, 0, 0, 0, 0)
        self.assertEqual(len(sk.voxelize(a | b)), 6)
        self.assertEqual(len(sk.voxelize(a & b)), 2)
        self.assertEqual(sorted(sk.voxelize(a - b)), [(0, 0, 0), (1, 0, 0)])

    def test_move(self):
        cells = sk.voxelize(sk.cells(0, 1, 0, 1, 0, 1).move(10, 3, -4))
        self.assertEqual(min(cells), (10, 3, -4))

    def test_every_primitive_voxelizes_and_is_square_symmetric(self):
        """Round shapes centred on a column come out the same under a quarter turn - no lopsided curves."""
        for name, shape in [("cone", sk.cone(5, 0, 8)), ("ellipsoid", sk.ellipsoid(5, 4, 5)),
                            ("onion", sk.onion_dome(5, 0, 12)), ("pointed", sk.spire(4, 0, 14, "pointed")),
                            ("ogee", sk.spire(4, 0, 14, "ogee")), ("torus", sk.torus(6, 1.5, 2)),
                            ("loft", sk.loft([(0, 5), (10, 3), (12, 0)]))]:
            cells = sk.voxelize(shape)
            self.assertGreater(len(cells), 20, name)
            turned = {}
            for (x, y, z), c in cells.items():
                if c[0] == "stairs":
                    c = ("stairs", gp.cw(c[1], 1), c[2])
                turned[(-z, y, x)] = c
            self.assertEqual(turned, cells, name)


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class Edges(unittest.TestCase):
    def test_ramp_up_to_north_is_bottom_stairs_facing_north(self):
        # a 45 degree ramp: solid below y = 4 - z (rising toward -z, north), 6 wide
        ramp = sk.Shape(lambda x, y, z: y - (4.0 - z), (-4, 4, 0, 10, -6, 5)) & sk.cells(-3, 3, 0, 8, -5, 4)
        st = kinds(sk.voxelize(ramp), "stairs")
        self.assertGreater(len(st), 10)
        self.assertEqual({c[1:] for c in st.values()}, {("north", "bottom")})

    def test_ramp_up_to_east(self):
        ramp = sk.Shape(lambda x, y, z: y - (4.0 + x), (-5, 5, 0, 10, -4, 4)) & sk.cells(-4, 4, 0, 8, -3, 3)
        st = kinds(sk.voxelize(ramp), "stairs")
        self.assertEqual({c[1:] for c in st.values()}, {("east", "bottom")})

    def test_overhang_underside_is_top_half(self):
        # solid ABOVE y = 2 + z: the underside comes lower toward the north, so the full lower half sits north
        under = sk.Shape(lambda x, y, z: (2.0 + z) - y, (-4, 4, -3, 9, -5, 5)) & sk.cells(-3, 3, -2, 8, -4, 4)
        st = kinds(sk.voxelize(under), "stairs")
        self.assertGreater(len(st), 10)
        self.assertEqual({c[2] for c in st.values()}, {"top"})
        self.assertEqual({c[1] for c in st.values()}, {"north"})

    def test_vertical_walls_take_no_stairs(self):
        cells = sk.voxelize(sk.cylinder(6.3, 0, 5))
        self.assertEqual(set(c[0] for c in cells.values()), {"full"})

    def test_dome_stairs_face_the_axis(self):
        cells = sk.voxelize(sk.ellipsoid(6, 6, 6, 0, 0, 0) & sk.box(-7, 7, 0, 7, -7, 7))
        st = kinds(cells, "stairs")
        self.assertGreater(len(st), 12)
        for (x, y, z), (_, facing, half) in st.items():
            dx, dz = VEC[facing]
            self.assertGreater(dx * -x + dz * -z, 0, ((x, y, z), facing))
            self.assertEqual(half, "bottom")

    def test_flat_crown_takes_slabs(self):
        cells = sk.voxelize(sk.ellipsoid(9, 2.5, 9) & sk.box(-10, 10, 0, 3, -10, 10))
        slabs = kinds(cells, "slab")
        self.assertIn((0, 2, 0), slabs)
        self.assertEqual({c[1] for c in slabs.values()}, {"bottom"})

    def test_edges_off_is_full_only(self):
        cells = sk.voxelize(sk.cone(5, 0, 6), edges=False)
        self.assertEqual(set(c[0] for c in cells.values()), {"full"})


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class Hollow(unittest.TestCase):
    def test_sphere_shell_is_closed_and_empty(self):
        solid = sk.voxelize(sk.ellipsoid(6, 6, 6, 0, 6, 0))
        shell = sk.voxelize(sk.ellipsoid(6, 6, 6, 0, 6, 0), hollow=1)
        self.assertLess(len(shell), len(solid) * 0.6)
        self.assertNotIn((0, 6, 0), shell)
        self.assertTrue(set(shell) <= set(solid))
        # flood the air from outside the bounding box (6-connected): it must not reach the cavity
        lo, hi = -8, 8
        seen, todo = set(), [(lo, 6 + lo, lo)]
        while todo:
            p = todo.pop()
            if p in seen or p in shell or not all(lo <= v - o <= hi for v, o in zip(p, (0, 6, 0))):
                continue
            seen.add(p)
            x, y, z = p
            todo += [(x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)]
        self.assertNotIn((0, 6, 0), seen)

    def test_thicker_shell(self):
        one = sk.voxelize(sk.cells(0, 8, 0, 8, 0, 8), hollow=1)
        two = sk.voxelize(sk.cells(0, 8, 0, 8, 0, 8), hollow=2)
        self.assertEqual(len(one), 9 ** 3 - 7 ** 3)
        self.assertEqual(len(two), 9 ** 3 - 5 ** 3)


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class Compatible(unittest.TestCase):
    def test_paints_a_part_with_roles(self):
        pal = gp.palette("slate")
        part = sk.build(sk.cone(4, 0, 5), "roof", gp.Part(pal))
        names = {n for n, _ in part.b.values()}
        self.assertIn(pal["roof"], names)
        self.assertIn(pal["roof_st"], names)
        st = [p for n, p in part.b.values() if n == pal["roof_st"]]
        self.assertTrue(all(set(p) == {"facing", "half", "shape", "waterlogged"} for p in st))
        rig = part.to_rig("cone")
        self.assertIsInstance(rig.structure(), gs.Structure)

    def test_role_callable_and_origin(self):
        part = sk.build(sk.cells(0, 3, 0, 0, 0, 0), lambda x, y, z: "trim" if x % 2 else "wall", at=(10, 0, 0))
        self.assertEqual(part.get(10, 0, 0), part.pal["wall"])
        self.assertEqual(part.get(11, 0, 0), part.pal["trim"])

    def test_missing_stair_role_is_an_error(self):
        with self.assertRaises(KeyError):
            sk.build(sk.cone(4, 0, 5), "no_such_role", gp.Part())

    def test_into_a_structure(self):
        s = gs.Structure(12, 8, 12)
        sk.build(sk.cone(4, 0, 5), "roof", s, at=(6, 0, 6), pal=gp.palette("red"))
        names = {s.palette[i][0] for i in s.blocks.values()}
        self.assertLessEqual({"bricks", "brick_stairs"}, names)
        self.assertLessEqual(names, {"bricks", "brick_stairs", "brick_slab"})

    def test_demos_build(self):
        for key, _, make in sk.DEMOS:
            part = make(gp.palette("slate"))
            self.assertGreater(len(part.b), 50, key)
            part.to_rig(key)




def _partial(name):
    return "stairs" in name or "slab" in name


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class Merging(unittest.TestCase):
    """A later shape's stair or slab never downgrades a full cell already in the target (shapes merge; a cone's
    edge stairs must not bite into the wall it sits on)."""

    def _check(self, target, names):
        sk.build(sk.cells(-3, 3, 0, 5, -3, 3), "wall", target, at=(4, 0, 4), edges=False)
        full = {p for p, n in names(target).items() if not _partial(n)}
        sk.build(sk.cone(5, 3, 6), "roof", target, at=(4, 0, 4))
        after = names(target)
        downgraded = sorted(p for p in full if _partial(after[p]))
        self.assertEqual(downgraded, [], "full cells replaced by a stair/slab: %d" % len(downgraded))

    def test_into_a_part(self):
        self._check(gp.Part(gp.palette()), lambda t: {p: n for p, (n, _) in t.b.items()})

    def test_into_a_structure(self):
        def names(t):
            return {p: t.palette[i][0] for p, i in t.blocks.items()}
        self._check(gs.Structure(9, 12, 9), names)


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class NoScipy(unittest.TestCase):
    def test_hollow_needs_no_scipy(self):
        src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "shape_kit.py")).read()
        self.assertNotIn("scipy", src)

if __name__ == "__main__":
    unittest.main()
