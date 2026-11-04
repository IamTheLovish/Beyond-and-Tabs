#!/usr/bin/env python3
"""The Brassworks Machine Shop (machine_shop.nbt, _l2, _l3) as the RTS camera sees it - tools/lab_machine_shop.py's tests.

The camera test fails on the Machine Shop that shipped until Nov 2026 (two fresh critics on eyes 41263811904: 5 / 6,
read 7 but colour 3, value 4 / 5 - "orange-roofed workshop with chimney"; on screen 65 % of its pixels at hue 18, team
orange, and the luma median 0.339 under the grass 0.409). It casts the eyes stills' real rays (tools/building_gates.shoot)
into the shipped structures.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
LEVELS = ("machine_shop", "machine_shop_l2", "machine_shop_l3")

# copper that LOOKS orange on camera (the unweathered and exposed stages, the polished block, lightning rods);
# weathered / oxidized copper reads teal-green and is not counted (tools/test_capitol_foundry_hall.py's list)
ORANGE = ("waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_copper_block", "copper_block", "cut_copper",
          "exposed_cut_copper", "exposed_copper", "lightning_rod")


def orange(name):
    n = name.split(":")[-1]
    return any(n == o or n.startswith(o + "_") for o in ORANGE)


def nbt(name, tree="data"):
    return os.path.join(RES, tree, "reignofnether", "structures", name + ".nbt")


class MachineShopTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.b = {n: S.load(nbt(n)) for n in LEVELS}
        cls.rts = {n: G.shoot(b, "rts", step=4) for n, b in cls.b.items()}

    def share(self, level, pred):
        hits = self.rts[level]
        return sum(1 for h in hits.values() if pred(h[1].split(":")[-1])) / max(1, len(hits))

    def test_brass_is_an_accent_at_every_level(self):
        """BAT - Readable at any zoom / Art method: team colour is the biggest saturated area; keep team-like hues out
        of fixed palettes; muted base + ONE accent. Orange-looking copper is the faction's brass ACCENT: 1-10 % of the
        RTS pixels at each level (both edges: none at all loses the brass). Shipped: 40 / 44 / 46 % (camera model)."""
        for level in LEVELS:
            with self.subTest(level=level):
                self.assertTrue(0.01 <= self.share(level, orange) <= 0.10, (level, self.share(level, orange)))

    def test_shipped_is_the_generator(self):
        """machine_shop*.nbt (data and assets) is what tools/lab_machine_shop.py draws, and its checks pass."""
        import lab_machine_shop as M
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
