#!/usr/bin/env python3
"""The Stormcallers' army lab (lotus_academy*.nbt) as the RTS camera sees it - tools/lab_lotus_academy.py's tests.

The Lotus Court that shipped until Nov 2026 was "a flat yard with half a jade roof" with "four competing accents:
teal, pink, gold, red" (two fresh critics on eyes 41263811904: 4 / 4). A blind massing round (eyes 41292980809, critics
20261101-043559 / -043843: 6 / 6, shipped 4 / 4) picked the double-eaved hall over the court at L1, and asked for: the
court as one quiet stone under the hall's frame (a roofed edge), the lotus where the camera looks (on the roof), the
target block / chequer mosaic / gold corner studs gone. Each test here fails on the shipped court and holds that.
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import lab_lotus_academy as L  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
FILES = ("lotus_academy.nbt", "lotus_academy_l2.nbt", "lotus_academy_l3.nbt")
NBT = [os.path.join(RES, "data", "reignofnether", "structures", f) for f in FILES]
NBT_ASSETS = [os.path.join(RES, "assets", "reignofnether", "structures", f) for f in FILES]
JADE = "waxed_oxidized_cut_copper"
QUIET = ("stone", "stone_bricks", "polished_andesite", "stone_brick_stairs", "stone_brick_slab", "smooth_stone")
LOUD = ("cherry", "gold", "jade", "oxidized", "moss", "target", "petals", "froglight", "amethyst")


class LotusAcademyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lv = [S.load(p) for p in NBT]
        cls.solid = [{q: n for q, (n, _) in b.blocks.items() if n not in S.AIR} for b in cls.lv]
        cls.l1 = cls.solid[0]

    def test_l1_hall_is_double_eaved(self):
        """Two curved jade roofs over the hall at L1, a storey apart: the blind massing winner (option 4, 6 / 6 both
        critics) against the shipped single roof ("half a jade roof")."""
        lips = sorted(y for (x, y, z), n in self.l1.items() if (x, z) == (L.C, 3) and n.startswith(JADE))
        self.assertGreaterEqual(len(lips), 2, lips)
        self.assertGreaterEqual(lips[-1] - lips[0], 4, lips)

    def test_court_is_one_quiet_stone(self):
        """The court (z >= 4) at L1: its floor and everything on it below the roofs is plain stone, red lacquer, dark
        oak or a light - no cherry, gold, jade, moss, target or mosaic (critics a / b: "a chequer", "a literal
        bullseye", "four accents in the yard")."""
        loud = sorted({n for (x, y, z), n in self.l1.items() if z >= 4 and y <= 3 and any(k in n for k in LOUD)})
        self.assertEqual(loud, [])
        floor = {n for (x, y, z), n in self.l1.items() if z >= 4 and y == 0}
        self.assertTrue(floor <= set(QUIET), floor)

    def test_court_sits_under_the_halls_frame(self):
        """A roofed edge round the court: jade over at least 30 % of the court's cells (z >= 4) from above, so the plot
        reads as one compound and not "half building, half patio" (critic a, problem 1)."""
        court = [(x, z) for x in range(L.SX) for z in range(4, L.SZ)]
        roofed = [c for c in court if any(n.startswith(JADE) for (x, y, z), n in self.l1.items()
                                          if (x, z) == c and y >= 3)]
        self.assertGreaterEqual(len(roofed) / len(court), 0.30, len(roofed))

    def test_lotus_crowns_the_hall(self):
        """The academy's figure where the camera looks: a pearlescent heart in cherry petals among the top rows
        (critic b, problem 3: "no lotus anywhere ... put it on the roof crown at far-read size")."""
        top = max(q[1] for q in self.l1)
        heart = [q for q, n in self.l1.items() if n == "pearlescent_froglight" and q[1] >= top - 4]
        self.assertTrue(heart, "no lotus heart in the top rows")
        x, y, z = heart[0]
        petals = [q for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1))
                  if self.l1.get(q, "").startswith("cherry")]
        self.assertGreaterEqual(len(petals), 3, petals)

    def test_no_gold_studs_on_the_roofs(self):
        """Gold above the eaves only in the crown (within one cell of the lotus): the shipped gold eave corners read
        as "square gold studs" / "three gold dots" (critics a / b)."""
        top = max(q[1] for q in self.l1)
        hx, hy, hz = [q for q, n in self.l1.items() if n == "pearlescent_froglight" and q[1] >= top - 4][0] \
            if any(n == "pearlescent_froglight" and q[1] >= top - 4 for q, n in self.l1.items()) else (-9, -9, -9)
        studs = sorted(q for q, n in self.l1.items() if n == "gold_block" and q[1] >= 5
                       and max(abs(q[0] - hx), abs(q[2] - hz)) > 1)
        self.assertEqual(studs, [])

    def test_no_team_hues_at_any_level(self):
        """Team colour is gameplay information: no amethyst (cool) and no banners (the team system's own sign) at any
        level - the shipped L2 grew amethyst in the lotus and hung red banners."""
        bad = sorted({n for s in self.solid for n in s.values() if "amethyst" in n or n.endswith("banner")})
        self.assertEqual(bad, [])

    def test_l1_owns_more_of_the_camera_than_the_yard(self):
        """From the RTS camera's real rays, jade roofs are the largest material (>= 40 % of the building's hits) and
        the court floor (y 0, z >= 4) is under 15 %: shipped 35 % / 21 % (the yard competes)."""
        hits = G.shoot(self.lv[0], "rts", step=4)
        n = max(1, len(hits))
        jade = sum(1 for h in hits.values() if h[1].startswith(JADE)) / n
        yard = sum(1 for h in hits.values() if h[0][1] == 0 and h[0][2] >= 4) / n
        self.assertGreaterEqual(jade, 0.40, (jade, yard))
        self.assertLess(yard, 0.15, (jade, yard))

    def test_shipped_structures_are_the_generator_build(self):
        built = (L.l1(), L.l2(), L.l3())
        with tempfile.TemporaryDirectory() as d:
            paths = [os.path.join(d, f) for f in FILES]
            for s, p in zip(built, paths):
                s.write(p)
            L.check_levels(*built, paths=paths)
            want = [S.load(p).blocks for p in paths]
        for i, f in enumerate(FILES):
            for path in (NBT[i], NBT_ASSETS[i]):
                self.assertEqual(S.load(path).blocks, want[i], path)


if __name__ == "__main__":
    unittest.main()
