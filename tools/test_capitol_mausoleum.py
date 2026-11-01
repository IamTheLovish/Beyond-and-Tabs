#!/usr/bin/env python3
"""The Gravebound capitol (mausoleum.nbt) as the RTS camera sees it - tools/capitol_mausoleum.py's tests.

Each camera test fails on the Necropolis Cathedral that shipped until Nov 2026 (two fresh critics on eyes 41263811904:
5 / 5, "dark stepped tomb", "dark grey castle heap"; median luma 0.246 in the darks band, under the grass 0.41).
Camera tests cast the eyes stills' real rays (tools/building_gates.shoot) into the shipped structure; colours by how
blocks LOOK on camera (design/vehicle_labs/camera_colours.json), not by id.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import camera_colour as CC  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
NBT = os.path.join(RES, "data", "reignofnether", "structures", "mausoleum.nbt")
NBT_ASSETS = os.path.join(RES, "assets", "reignofnether", "structures", "mausoleum.nbt")
SHAPES = ("_stairs", "_slab", "_wall")


def value(table, name, face):
    """The camera value of a block; a stair, slab or wall of a measured material reads as that material's face."""
    v = CC.lookup(table, name, face)
    if v is None:
        for s in SHAPES:
            if name.endswith(s):
                base = name[:-len(s)]
                v = CC.lookup(table, base, face) or CC.lookup(table, base + "s", face)   # deepslate_tile -> _tiles
                break
    return None if v is None else v[2]


class MausoleumTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.b = S.load(NBT)
        cls.rts = G.shoot(cls.b, "rts", step=4)
        table = CC.load()
        cls.vals = [value(table, h[1], h[2]) for h in cls.rts.values()]

    def test_lights_own_the_top(self):
        """BAT - Art method, value: lights 0.50+ belong to buildings; the capitol is not a dark hole in the lawn.
        25-55 % of the RTS pixels read value >= 0.50 (both edges: under 25 % it is the dark heap, over 55 % a white
        box with no darks). Shipped: ~12 %."""
        light = sum(1 for v in self.vals if v is not None and v >= 0.50) / max(1, len(self.vals))
        self.assertTrue(0.25 <= light <= 0.55, light)

    def test_darks_stay_a_group(self):
        """The faction's deepslate and blackstone stay the dark group: >= 20 % of the RTS pixels under 0.28."""
        dark = sum(1 for v in self.vals if v is not None and v < 0.28) / max(1, len(self.vals))
        self.assertGreaterEqual(dark, 0.20)

    def test_every_camera_block_has_a_measured_look(self):
        """The value tests only count blocks the camera table knows: <= 12 % of the RTS pixels may be unmeasured."""
        unk = sum(1 for v in self.vals if v is None) / max(1, len(self.vals))
        self.assertLessEqual(unk, 0.12)

    def test_shipped_is_the_generator_and_keeps_the_game_rules(self):
        """mausoleum.nbt (data and assets) is what tools/capitol_mausoleum.py draws, and its check() passes."""
        import capitol_mausoleum as M
        with open(NBT, "rb") as a, open(NBT_ASSETS, "rb") as c:
            self.assertEqual(a.read(), c.read())
        built = M.build()
        M.check(built)
        drawn = {(x, y, z): built.palette[i] for (x, y, z), i in built.blocks.items() if built.palette[i][0] != "air"}
        shipped = {q: v for q, v in self.b.blocks.items() if v[0] not in S.AIR}
        self.assertEqual(len(drawn), len(shipped))
        self.assertEqual({q: v[0].split(":")[-1] for q, v in drawn.items()},
                         {q: v[0].split(":")[-1] for q, v in shipped.items()})


if __name__ == "__main__":
    unittest.main()
