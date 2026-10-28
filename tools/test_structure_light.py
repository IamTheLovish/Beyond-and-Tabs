"""tools/structure_render.py --light: ambient occlusion and the sun's block shadows.
Run: python3 -m unittest discover -s tools -p 'test_*.py'"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import structure_render as SR  # noqa: E402


FULL = SR.PS.FULL[0]


def light_of(cells, sun=(-1.0, 0.5, 0.0)):
    """A Light over full cubes at `cells`."""
    return SR.Light([(c, FULL) for c in cells], sun)


def top_ao(L, cell):
    """The AO levels (0 darkest .. 3 open) of a full cube's top face, corners top-left, top-right, bottom-right,
    bottom-left as structure_render pastes it: (x0, z0), (x1, z0), (x1, z1), (x0, z1)."""
    return L.ao(cell, FULL, "top")


class AmbientOcclusion(unittest.TestCase):
    def test_open_floor_is_unoccluded(self):
        L = light_of({(4, 0, 4)})
        self.assertEqual(top_ao(L, (4, 0, 4)), (3, 3, 3, 3))

    def test_floor_against_a_wall_darkens_its_wall_side(self):
        # a floor cell with a wall on its west (-x): the top face's two west corners (tl, bl) occluded
        L = light_of({(4, 0, 4), (3, 1, 4), (3, 1, 3), (3, 1, 5)})
        tl, tr, br, bl = top_ao(L, (4, 0, 4))
        self.assertLess(tl, 3)
        self.assertLess(bl, 3)
        self.assertEqual((tr, br), (3, 3))

    def test_inner_corner_is_darkest(self):
        L = light_of({(4, 0, 4), (3, 1, 4), (4, 1, 3)})       # walls west and north: the tl corner is in both
        self.assertEqual(top_ao(L, (4, 0, 4))[0], 0)


class SunShadow(unittest.TestCase):
    def test_pillar_shades_the_floor_on_its_far_side(self):
        # the sun in the west, rising 1 in 2: a 2-tall pillar at x = 2 shades the floor east of it for 4 blocks
        cells = {(x, 0, 4) for x in range(9)} | {(2, y, 4) for y in (1, 2)}
        L = light_of(cells)
        lit = L.sunlit(np.array([[4.5, 1.01, 4.5], [0.5, 1.01, 4.5], [8.5, 1.01, 4.5]]))
        self.assertFalse(lit[0])     # just east of the pillar: in its shadow
        self.assertTrue(lit[1])      # west of it: in the sun
        self.assertTrue(lit[2])      # past the shadow's end (x = 7)

    def test_a_slab_roof_casts_too(self):
        # stairs and slabs are most roofs: a top slab over a floor shades it
        L = SR.Light([((x, 0, 4), FULL) for x in range(9)] + [((x, 2, 4), (0, 1, 0.5, 1, 0, 1)) for x in range(9)],
                     (-1.0, 0.5, 0.0))
        self.assertFalse(L.sunlit(np.array([[4.5, 1.01, 4.5]]))[0])

    def test_lit_faces_are_brighter_than_shaded(self):
        L = light_of({(4, 0, 4), (2, 1, 4), (2, 2, 4)})
        self.assertGreater(L.face_light((2, 2, 4), FULL, "top").min(), L.face_light((4, 0, 4), FULL, "top").max())


if __name__ == "__main__":
    unittest.main()
