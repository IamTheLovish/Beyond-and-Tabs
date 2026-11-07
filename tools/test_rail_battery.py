#!/usr/bin/env python3
"""The Brassworks Rail Battery (rail_battery.nbt) as the RTS camera sees it - tools/def_rail_battery.py's tests.

The all-factions blind Fable sweep (Nov 5 2026, eyes on main jar 42137577884, 62 buildings) scored the Rail Battery
3 / 10 (one-second read): "a 3x3 deepslate pedestal with a heap of copper blocks on top; even for a small battery it says
nothing". lovish's bar (the TomatoN castle): slim towers with crown bands, one dominant landmark, real darks, light
from openings. The footprint stays 3 x 3, the size of every small defence (the Gatling Posts): the battery grows up,
not out. Each art test here fails on main's battery; the rule tests hold what RailBattery.java and the placement read
off the file.

Run: python3 tools/test_rail_battery.py   (one small file; no Gradle)
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
NBT = os.path.join(RES, "data", "reignofnether", "structures", "rail_battery.nbt")
NBT_ASSETS = os.path.join(RES, "assets", "reignofnether", "structures", "rail_battery.nbt")
START = ("polished_blackstone", "deepslate_bricks")    # RailBattery.startingBlockTypes
FORBIDDEN = ("sand", "gravel", "water", "lava", "redstone_wire")
LIGHTS = ("shroomlight", "lantern", "campfire", "end_rod", "sea_lantern", "froglight")


def lit(n, p):
    if n == "redstone_lamp":
        return p.get("lit") == "true"
    return any(k in n for k in LIGHTS) and p.get("lit", "true") == "true"


class RailBatteryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.b = S.load(NBT)
        cls.solid = {q: n for q, (n, _) in cls.b.blocks.items() if n not in S.AIR}
        cls.props = {q: p for q, (n, p) in cls.b.blocks.items() if n not in S.AIR}
        cls.top = max(q[1] for q in cls.solid)
        cls.layer = {y: [q for q in cls.solid if q[1] == y] for y in range(cls.top + 1)}
        cls.hits = list(G.shoot(cls.b, "rts_close", step=4).values())

    def test_it_stands_tall(self):
        """A T2 defence outranks the T1 Gatling Posts (5-6 high) and stays under the other T2 defences (Lighthouse
        Battery 14, Storm Oak 15): the top block at y 9..12. Main's battery: top at y 5, a 6-high stump."""
        self.assertGreaterEqual(self.top, 9)
        self.assertLessEqual(self.top, 12)

    def test_a_slim_mast_over_a_plinth(self):
        """Base + body + cap: a full 3 x 3 plinth at y 0 and 1, then a mast - at least 4 layers in a row above y 2 that
        each hold at most 5 of the 9 cells (open sides, so it reads as a column, not a block) - and a point: the top
        layer holds at most 3 cells. Main's battery: the 3 x 3 runs full to y 2, then a 3-layer heap."""
        self.assertEqual(len(self.layer[0]), 9)
        self.assertEqual(len(self.layer[1]), 9)
        best = run = 0
        for y in range(3, self.top + 1):
            run = run + 1 if 0 < len(self.layer[y]) <= 5 else 0
            best = max(best, run)
        self.assertGreaterEqual(best, 4, "cells per layer %s" % [len(self.layer[y]) for y in range(self.top + 1)])
        self.assertLessEqual(len(self.layer[self.top]), 3)

    def test_a_crown_band(self):
        """The mast ends in a band: one full 3 x 3 layer at y 6 or higher (the crown under the emitter, what the camera
        reads from above). Main's battery: its highest full layer is y 2, the pedestal."""
        full = [y for y in range(6, self.top + 1) if len(self.layer[y]) == 9]
        self.assertTrue(full, "cells per layer %s" % [len(self.layer[y]) for y in range(self.top + 1)])

    def test_coils_glow_to_the_camera(self):
        """At dusk the battery owns its light: at least 3 lit blocks at 3 different heights from y 3 up, each with its
        west (x - 1) or south (z + 1) neighbour open or off the plot, so the camera sees the glow. Main's battery: one
        redstone lamp, unlit, buried in the heap."""
        seen = []
        for (x, y, z), n in self.solid.items():
            if y < 3 or not lit(n, self.props[(x, y, z)]):
                continue
            if x == 0 or (x - 1, y, z) not in self.solid or z == 2 or (x, y, z + 1) not in self.solid:
                seen.append(y)
        self.assertGreaterEqual(len(set(seen)), 3, "lit heights the camera sees: %s" % sorted(seen))

    def test_the_camera_sees_dark_with_brass(self):
        """What rts_close sees: copper at most 45 % (the brass is the coil and the crown, not the whole read), darks
        (blackstone, deepslate, chain) at least 40 %. Main's battery: copper 59 %, dark 34 %."""
        n = max(1, len(self.hits))
        c = sum(1 for (q, b, _f, _x) in self.hits if "copper" in b) / n
        d = sum(1 for (q, b, _f, _x) in self.hits if any(k in b for k in G.DARK)) / n
        self.assertLessEqual(c, 0.45, "copper %.2f" % c)
        self.assertGreaterEqual(d, 0.40, "dark %.2f" % d)

    def test_game_rules(self):
        """What RailBattery.java and the placement read: 3 x 3; a full foundation; a starting block type in layer 0
        (BuildingServerEvents queues the starting types of the foundation first); no gravity blocks, fluids, doors or
        leaves; nothing a unit could stand inside; data and assets copies identical."""
        xs, zs = [q[0] for q in self.solid], [q[2] for q in self.solid]
        self.assertEqual((min(xs), max(xs), min(zs), max(zs)), (0, 2, 0, 2))
        self.assertTrue(all((x, 0, z) in self.solid for x in range(3) for z in range(3)), "foundation hole")
        self.assertTrue({n for q, n in self.solid.items() if q[1] == 0} & set(START), "no starting type in layer 0")
        bad = sorted({n for n in self.solid.values() if n in FORBIDDEN or n.endswith(("_door", "_leaves", "_concrete_powder"))})
        self.assertFalse(bad)
        import audit_structure_pockets as ap
        one_way, ground, _roof, _rooms, note = ap.audit("rail_battery", NBT)
        self.assertFalse(one_way or ground or "TRAPPED" in note, (one_way[:6], ground[:6], note))
        with open(NBT, "rb") as fa, open(NBT_ASSETS, "rb") as fb:
            self.assertEqual(fa.read(), fb.read())

    def test_shipped_is_the_generator(self):
        """rail_battery.nbt is what tools/def_rail_battery.py draws."""
        import def_rail_battery as R
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "rail_battery.nbt")
            R.build().write(p)
            with open(p, "rb") as fa, open(NBT, "rb") as fb:
                self.assertEqual(fa.read(), fb.read())


if __name__ == "__main__":
    unittest.main()
