#!/usr/bin/env python3
"""The Tidewrought Slipway (slipway.nbt, _l2, _l3) as the RTS camera sees it - tools/lab_slipway.py's tests.

The camera test fails on the Slipway that shipped until Nov 2026 (critics 5 / 5 in the buildings lane: the red mangrove
hull frames read like a team colour). It casts the eyes stills' real rays (tools/building_gates.shoot) into the shipped
structures: red mangrove wood is 13.0 / 4.6 / 2.5 % of the RTS pixels at L1 / L2 / L3. The palette pass (birch driftwood,
build 41712771034 red) brings it to 0 %; BiomePalette keeps the birch for Tidewrought in every biome.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
LEVELS = ("slipway", "slipway_l2", "slipway_l3")


def red(name):
    n = name.split(":")[-1]
    return "mangrove" in n or n.startswith("red_")


def nbt(name, tree="data"):
    return os.path.join(RES, tree, "reignofnether", "structures", name + ".nbt")


class SlipwayTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.b = {n: S.load(nbt(n)) for n in LEVELS}
        cls.rts = {n: G.shoot(b, "rts", step=4) for n, b in cls.b.items()}

    def share(self, level, pred):
        hits = self.rts[level]
        return sum(1 for h in hits.values() if pred(h[1])) / max(1, len(hits))

    def test_no_team_red_wood_at_any_level(self):
        """BAT - Readable at any zoom: team colour is the biggest saturated area; keep team-like hues out of fixed
        palettes. Red mangrove wood at most 1 % of the RTS pixels at each level. Shipped: 13.0 / 4.6 / 2.5 %."""
        for level in LEVELS:
            with self.subTest(level=level):
                self.assertLessEqual(self.share(level, red), 0.01, (level, self.share(level, red)))

    def test_shipped_is_the_generator(self):
        """slipway*.nbt (data and assets) is what tools/lab_slipway.py draws, and its checks pass."""
        import lab_slipway as M
        built = dict(zip(LEVELS, (M.l1(), M.l2(), M.l3())))
        M.check_levels(*built.values())
        for level, s in built.items():
            with open(nbt(level), "rb") as a, open(nbt(level, "assets"), "rb") as c:
                self.assertEqual(a.read(), c.read(), level)
            drawn = {q: s.palette[i][0].split(":")[-1] for q, i in s.blocks.items() if s.palette[i][0] != "air"}
            shipped = {q: v[0].split(":")[-1] for q, v in self.b[level].blocks.items() if v[0] not in S.AIR}
            self.assertEqual(drawn, shipped, level)


if __name__ == "__main__":
    unittest.main()
