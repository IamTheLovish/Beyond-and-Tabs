"""tools/brass_lab_parts.py's relief parts (Engine Shed T1 detail round 1, Oct 28): pilaster, pinnacle, emblem. Run:
python3 -m unittest discover -s tools -p 'test_brass_lab_parts_relief.py'

Written before the parts. Part frame (gothic_parts.Part): the front faces SOUTH (+z), the wall behind is at -z.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import brass_lab_parts as B  # noqa: E402


class Pilaster(unittest.TestCase):
    def test_base_shaft_capital(self):
        p = B.pilaster(5)
        self.assertEqual(sorted(p.b), [(0, y, 0) for y in range(5)])
        self.assertEqual(p.b[(0, 0, 0)][0], "polished_andesite")
        self.assertEqual({p.b[(0, y, 0)][0] for y in (1, 2, 3)}, {"polished_blackstone_bricks"})
        name, props = p.b[(0, 4, 0)]
        self.assertEqual(name, "polished_andesite_stairs")
        self.assertEqual((props["half"], props["facing"]), ("top", "north"))   # overhangs the front, back to the wall

    def test_turned_to_face_west(self):
        name, props = B.pilaster(4).rotated(1).b[(0, 3, 0)]
        self.assertEqual(props["facing"], "east")


class Pinnacle(unittest.TestCase):
    def test_wall_shaft_and_tip(self):
        p = B.pinnacle(3)
        self.assertEqual([p.b[(0, y, 0)][0] for y in range(3)],
                         ["polished_blackstone_brick_wall", "polished_blackstone_brick_wall", "waxed_cut_copper_slab"])


class Emblem(unittest.TestCase):
    def test_frame_proud_cross_recessed(self):
        p = B.emblem()
        frame = {q for q, (n, _) in p.b.items() if n == "blackstone"}
        self.assertEqual(len(frame), 16)
        self.assertTrue(all(q[2] == 0 and (abs(q[0]) == 2 or abs(q[1]) == 2) for q in frame))
        cross = {q for q, (n, _) in p.b.items() if n == "red_terracotta"}
        self.assertEqual(cross, {(0, -1, -1), (0, 0, -1), (0, 1, -1), (-1, 0, -1), (1, 0, -1)})
        self.assertEqual(sum(n == "quartz_bricks" for n, _ in p.b.values()), 4)
        self.assertEqual({q for q, (n, _) in p.b.items() if n == "air"},     # the field in front of the panel is open
                         {(x, y, 0) for x in (-1, 0, 1) for y in (-1, 0, 1)})


if __name__ == "__main__":
    unittest.main()
