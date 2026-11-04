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

    # ---- the form pass (Nov 4 review, PR #493's open massing list): a ship, not a crate; value order on the skyline
    def camera(self, name, face):
        """What the block IS on the eyes stills (design/vehicle_labs/camera_colours.json): its value 0..1 on `face`."""
        import json
        table = json.load(open(os.path.join(ROOT, "design", "vehicle_labs", "camera_colours.json")))["blocks"]
        entry = table.get(name.split(":")[-1], {})
        if face not in entry:
            face = next(iter(entry))                  # the nearest face it was measured on
        return entry[face]["val"]

    def cell(self, level, x, y, z):
        v = self.b[level].blocks.get((x, y, z))
        return "air" if v is None or v[0] in S.AIR else v[0].split(":")[-1]

    def test_pale_ribs_stand_on_a_dark_wale(self):
        """Critics on the palette pass: 'pale frames merge with the sandstone yard - a dark strake between the frames
        would edge them'. Every L1 rib (the frame foot at y 4, x 2 / 4 / 6, both sides) is bleached driftwood standing
        on a strake at least 0.25 darker on the camera (the capitol's hull: a dark wale under the pale timber).
        Shipped: birch ribs (south 0.60) on birch planks (0.52) - a 0.07 step, one pale slab."""
        for x in (2, 4, 6):
            for z in (0, 3):
                with self.subTest(x=x, z=z):
                    rib, wale = self.cell("slipway", x, 4, z), self.cell("slipway", x, 3, z)
                    self.assertIn("birch", rib, (x, z, rib))
                    step = self.camera(rib, "south") - self.camera(wale, "south")
                    self.assertGreaterEqual(step, 0.25, (x, z, rib, wale, round(step, 3)))

    def rail_top(self, level, x, z):
        """The highest timber of a column (lanterns and chains are not the rail)."""
        tops = [y for (cx, y, cz), v in self.b[level].blocks.items() if (cx, cz) == (x, z) and v[0] not in S.AIR
                and not v[0].endswith(("lantern", "chain"))]
        return max(tops)

    def test_hull_has_a_sheer_line(self):
        """Critics: 'the hull reads as a row of posts, not a ship (no bow / stern taper)'. On the camera side (z 3) the
        frame heads rise toward the bow (x 2) and the stern (x 6) above the midship frame (x 4): a sheer, the one line
        every ship has. Shipped: 6 / 6 / 6, a flat crate."""
        bow, mid, stern = (self.rail_top("slipway", x, 3) for x in (2, 4, 6))
        self.assertGreater(bow, mid, (bow, mid, stern))
        self.assertGreater(stern, mid, (bow, mid, stern))

    def test_the_crane_is_bleached_driftwood_not_a_dark_gallows(self):
        """Critics: 'the crane reads as a gallows and its dark-oak beam tops the silhouette (value inverted)'. Weathering
        logic (architecture_rules 8): dark and wet low, bleached high. Every timber of L1 above the tower roof (y >= 8:
        the crane's mast and jib) reads pale on the camera (top value >= 0.5). Shipped: stripped dark oak, 0.22."""
        high = [(q, v[0].split(":")[-1]) for q, v in self.b["slipway"].blocks.items()
                if q[1] >= 8 and v[0].endswith("_log")]
        self.assertTrue(high, "no crane timbers above y 8")
        for q, name in high:
            with self.subTest(cell=q):
                self.assertGreaterEqual(self.camera(name, "up"), 0.5, (q, name))

    def test_l2_bulwark_is_broken_into_bays_by_the_ribs(self):
        """L2 on the camera: two lit portholes under one continuous dark band (the gunwale cap at z 4 hides the bulwark
        behind it) read as a face - eyes under a brow. The ribs rise through the bulwark AND the cap as timberheads
        (pale logs at x 2 / 4 / 6 y 6 z 3, and proud of the cap at x 2 / 6 z 4), so the camera side is a rhythm of
        bays - post, lit bay, post (architecture_rules 4) - not a brow. Shipped: dark oak planks x 2..6 behind a dark
        oak cap x 1, 2, 3, 5, 6."""
        for x in (2, 4, 6):
            with self.subTest(x=x, z=3):
                self.assertIn("birch", self.cell("slipway_l2", x, 6, 3), self.cell("slipway_l2", x, 6, 3))
        for x in (2, 6):
            with self.subTest(x=x, z=4):
                self.assertIn("birch", self.cell("slipway_l2", x, 6, 4), self.cell("slipway_l2", x, 6, 4))
        for x in (1, 3, 5):
            with self.subTest(x=x, z=4):
                self.assertEqual(self.cell("slipway_l2", x, 6, 4), "dark_oak_stairs")

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
