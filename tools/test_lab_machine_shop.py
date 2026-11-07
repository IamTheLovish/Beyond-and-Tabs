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

    # ---- the depth round (Nov 4 2026, the Fable review of #489): design/architecture_rules.md 1.1 tectonics, 1.2
    # base-body-cap, 1.6 "depth, not colour", and the gates G5 / G7 on the two faces the RTS camera sees. On the
    # shipped 03720e49 every pier stood in the plinth's plane (G7 base fails on all four camera runs), every window was
    # flush infill (depth 0) and one plane held 52 / 48 % of the south / west wall band at L1 (G5): "flat walls".

    def gate_build(self, level):
        return self.b[level]

    def test_depth_on_both_camera_faces_at_every_level(self):
        """G5 on the wall band: no depth plane over 45 % and at least three planes over 10 %, seen from the south and
        from the west - the two faces the RTS camera sees - at L1, L2 and L3. Shipped: L1 south 52 % / west 48 % and L2
        47 / 47 % on one plane (L3 passed by dilution: a taller eave)."""
        for level in LEVELS:
            for face in ("south", "west"):
                with self.subTest(level=level, face=face):
                    d = G.depth(self.gate_build(level), face)
                    self.assertTrue(d["ok"], (level, face, d["planes"]))

    def test_piers_stand_on_the_plinth(self):
        """G7's base on every camera-facing wall run, whatever its length (a pier between two niches is a run of one):
        the course at y 1 is a plinth, not the pier's own block carried down to the ground (architecture_rules 1.1:
        piers carry, a paler heavier base at the ground). Shipped: the dark-oak piers ran from y 1, so the shop's yard
        face, its west end and the engine house's south end had no base course (base False on all three runs)."""
        for level in LEVELS:
            runs = [r for r in G.base_cap(self.gate_build(level))["runs"] if r["face"] in ("south", "west")]
            with self.subTest(level=level):
                self.assertTrue(runs, level)
                self.assertTrue(all(r["base"] for r in runs), (level, [r for r in runs if not r["base"]]))

    def test_windows_below_the_eave_are_reveals(self):
        """Every window in a wall below the eave sits exactly one block behind its frame (a reveal that throws a
        shadow: architecture_rules 1.6 three planes - recess, wall, projection). Glass at the eave and above (the
        gable's tympanum, the sawtooth's north lights, the tower's band) is the roof's business. Shipped: the two
        furnace bays and the west end's window were flush infill (depth 0); only the engine house's grille was set
        back."""
        for level in LEVELS:
            b = self.gate_build(level)
            _, _, eave = G._frame(b)
            flush = sorted((q, ds) for q, ds in G._glass_frames(b, all_depths=True)
                           if q[1] < eave and not any(d == 1 for d in ds))
            with self.subTest(level=level):
                self.assertEqual(flush, [], (level, eave))

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
