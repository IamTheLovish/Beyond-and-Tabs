#!/usr/bin/env python3
"""The Brassworks capitol (foundry_hall.nbt) as the RTS camera sees it - tools/capitol_foundry_hall.py's tests.

Each camera test fails on the Grand Foundry that shipped until Nov 2026 (two fresh critics on eyes 41263811904: 5 / 5,
"orange stepped pyramid with chimneys"; on screen 59 % of its pixels at hue 18 - team orange - and luma median 0.323
under the grass 0.413). Camera tests cast the eyes stills' real rays (tools/building_gates.shoot) into the shipped
structure.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
NBT = os.path.join(RES, "data", "reignofnether", "structures", "foundry_hall.nbt")
NBT_ASSETS = os.path.join(RES, "assets", "reignofnether", "structures", "foundry_hall.nbt")

# copper that LOOKS orange on camera (the unweathered and exposed stages, the polished block, lightning rods): the
# baseline still's dominant hue 18 came from these; weathered / oxidized copper reads teal-green (camera table hue
# 138-155) and is not counted
ORANGE = ("waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_copper_block", "copper_block", "cut_copper",
          "exposed_cut_copper", "exposed_copper", "lightning_rod")


def orange(name):
    n = name.split(":")[-1]
    return any(n == o or n.startswith(o + "_") for o in ORANGE)


class FoundryHallTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.b = S.load(NBT)
        cls.rts = G.shoot(cls.b, "rts", step=4)

    def test_team_orange_is_an_accent_not_the_building(self):
        """BAT - Readable at any zoom: team colour is the biggest saturated area; keep team-like hues out of fixed
        palettes. Orange-looking copper is the faction's brass ACCENT: 1-10 % of the RTS pixels (both edges: none at
        all loses the brass). The shipped hall reads ~50 % on the camera model (59 % on screen)."""
        share = sum(1 for h in self.rts.values() if orange(h[1])) / max(1, len(self.rts))
        self.assertTrue(0.01 <= share <= 0.10, share)

    def test_no_stepped_hip_roof(self):
        """Both baseline critics: "stepped pyramid" - the church/temple read lovish rejected on the Engine Shed ("doesnt
        necessarily look like a factory"), and four capitols shared it. No level carries stairs along all four sides of
        a square ring round the centre above the plinth (a side counts when at least half its cells are climbing,
        bottom-half stairs; upside-down cornice stairs are not a slope)."""
        R = 7
        slopes = {q for q, v in self.b.blocks.items()
                  if v[0].endswith("_stairs") and dict(v[1] or {}).get("half") != "top"}
        rings = []
        for y in range(3, 22):         # above the plinth's entry steps
            for k in range(1, R + 1):
                sides = (
                    [(R + t, y, R - k) for t in range(-k, k + 1)], [(R + t, y, R + k) for t in range(-k, k + 1)],
                    [(R - k, y, R + t) for t in range(-k, k + 1)], [(R + k, y, R + t) for t in range(-k, k + 1)])
                if all(sum(1 for q in s if q in slopes) * 2 >= len(s) for s in sides):
                    rings.append((y, k))
        self.assertEqual(rings, [])

    def test_shipped_is_the_generator_and_keeps_the_game_rules(self):
        """foundry_hall.nbt (data and assets) is what tools/capitol_foundry_hall.py draws, and its check() passes."""
        import capitol_foundry_hall as F
        with open(NBT, "rb") as a, open(NBT_ASSETS, "rb") as c:
            self.assertEqual(a.read(), c.read())
        built = F.build()
        F.check(built)
        drawn = {(x, y, z): built.palette[i] for (x, y, z), i in built.blocks.items() if built.palette[i][0] != "air"}
        shipped = {q: v for q, v in self.b.blocks.items() if v[0] not in S.AIR}
        self.assertEqual({q: v[0].split(":")[-1] for q, v in drawn.items()},
                         {q: v[0].split(":")[-1] for q, v in shipped.items()})


if __name__ == "__main__":
    unittest.main()
