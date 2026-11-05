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
        12-55 % of the RTS pixels read value >= 0.50 (both edges: under 12 % it is the dark heap, over 55 % a white
        box with no darks). The camera model reads the shipped cathedral at 5.5 % (eyes 41263811904: 6 %, critics
        5 / 5 "dark heap"). Re-set from 25 % on screen evidence, Nov 3: every critic-backed winner of four blind rounds
        sat at 15-16 % on screen (eyes 41340659537, 41586495970, 41588222195; the final 6.5 / 6.5 at 16 %, model
        16.9 %), and the paler options at 18 % were ranked lower ("snow", "white box")."""
        light = sum(1 for v in self.vals if v is not None and v >= 0.50) / max(1, len(self.vals))
        self.assertTrue(0.12 <= light <= 0.55, light)

    def test_darks_stay_a_group(self):
        """The faction's deepslate and blackstone stay the dark group: >= 20 % of the RTS pixels under 0.28."""
        dark = sum(1 for v in self.vals if v is not None and v < 0.28) / max(1, len(self.vals))
        self.assertGreaterEqual(dark, 0.20)

    def test_every_camera_block_has_a_measured_look(self):
        """The value tests only count blocks the camera table knows: <= 12 % of the RTS pixels may be unmeasured."""
        unk = sum(1 for v in self.vals if v is None) / max(1, len(self.vals))
        self.assertLessEqual(unk, 0.12)

    def test_the_cross_is_the_outline(self):
        """lovish: the capitols must not share the square stepped-pyramid outline. Two critics on every round: the
        corner towers, buttresses and podium between the arms drew the square / diamond from the RTS camera. Nothing
        stands above layer 0 in the cross's re-entrant corners (cells 3+ from both axes), and at most a few
        headstones (<= 10 %) sit in layer 0 there - the field's own lawn shows (layer 0 sits a block over the field)."""
        R = 7
        corner = {(x, z) for x in range(15) for z in range(15) if min(abs(x - R), abs(z - R)) >= 3}
        above = sorted({(x, y, z) for (x, y, z), v in self.b.blocks.items()
                        if (x, z) in corner and y >= 1 and v[0] not in S.AIR})
        self.assertEqual(above[:5], [], "%d blocks stand in the corners" % len(above))
        floor = [q for q, v in self.b.blocks.items() if (q[0], q[2]) in corner and q[1] == 0 and v[0] not in S.AIR]
        self.assertLessEqual(len(floor), 0.10 * len(corner), len(floor))

    # ---- the Fable critics' fixes (Nov 4, eyes 41792408905: 6 / 10; critic-a.md, critic-c.md F5) ----
    def _solid(self):
        return {q: v[0] for q, v in self.b.blocks.items() if v[0] not in S.AIR}

    def test_crossing_spire_leads(self):
        """The crossing's spire tops the arms' needles by at least 4 (critic A fix 1: the crossing lantern at y 21 was
        one pixel over the arm skulls at y 20 - "the needles are equal posts", the skyline did not climb to the
        middle)."""
        s = self._solid()
        top = max(y for (x, y, z) in s if (x, z) == (7, 7))
        arms = max(y for (x, y, z) in s if max(abs(x - 7), abs(z - 7)) >= 3 and min(abs(x - 7), abs(z - 7)) == 0)
        self.assertGreaterEqual(top - arms, 4, (top, arms))

    def test_corners_are_a_blight_lawn(self):
        """The cross's re-entrant corners (cells 3+ from both axes) are the Gravebound BLIGHT at layer 0 - podzol,
        coarse dirt, soul soil, rooted dirt, with graves laid flat in it - on at least 90 % of their cells, and still
        nothing above layer 0 (critic A fix 2: the empty corners made the capitol look like a T2 building and the
        headstones float; the blight aura is the faction's ground and was not drawn)."""
        s = self._solid()
        corner = [(x, z) for x in range(15) for z in range(15) if min(abs(x - 7), abs(z - 7)) >= 3]
        blight = ("podzol", "coarse_dirt", "soul_soil", "rooted_dirt", "deepslate_tile_wall", "deepslate_brick_slab",
                  "bone_block")
        lawn = [c for c in corner if s.get((c[0], 0, c[1]), "") in blight]
        self.assertGreaterEqual(len(lawn) / len(corner), 0.90, len(lawn))
        above = sorted(q for q in s if (q[0], q[2]) in set(corner) and q[1] >= 1)
        self.assertEqual(above[:5], [])

    def test_crossing_cap_is_bone_not_snow(self):
        """The crossing's top course (ring 2, y 14) and the spire's cap block are bone (cream, the kit's own accent on
        the ribs and skulls), not polished diorite (critic A fix 3: the white crossing read as "a snow cap / white box",
        the Art method's calcite / diorite snow): no full polished diorite block at y >= 14."""
        s = self._solid()
        snow = sorted(q for q, n in s.items() if q[1] >= 14 and n == "polished_diorite")
        self.assertEqual(snow, [])
        cornice = [q for q, n in s.items() if q[1] == 14 and max(abs(q[0] - 7), abs(q[2] - 7)) == 2 and n == "bone_block"]
        self.assertGreaterEqual(len(cornice), 8, cornice)

    def test_one_strong_cold_light_on_the_crossing(self):
        """A full-strength cold light in the crossing's spire (a sea lantern, light 15, |d| <= 1, y >= 17), so at dusk
        the Gravebound capitol is a dark mass with a cold top and the Ashborn one a dark mass with a warm top (critic C
        F5: the two dark capitols were dusk twins; five soul lanterns, light 10, did not read from the RTS camera)."""
        s = self._solid()
        cold = [q for q, n in s.items() if n == "sea_lantern" and q[1] >= 17 and max(abs(q[0] - 7), abs(q[2] - 7)) <= 1]
        self.assertTrue(cold)

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
