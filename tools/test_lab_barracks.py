#!/usr/bin/env python3
"""The Sunforged army lab (barracks*.nbt) as the RTS camera sees it - tools/lab_barracks.py's tests.

The Training Hall that shipped until Nov 2026 was "a white villa with blue banners" / "a white box house, sandy yard"
(two fresh critics on eyes 41263811904: 5 / 5): a bell turret and a hall of near-equal boxes, lapis bands and blue
banners in a cool-team hue, two red-white targets (a bullseye in a warm-team hue). Blind massing rounds (eyes 41328607331,
41330052484, PR body) picked a drill court open to the south-west under one keep on the north-east with a tiered
gold crown, and asked for: one mass that leads, a crown that reads from far, real darks (a deep gate), a yard floor in
the mids, the exit corner open to the sky. Each test here fails on the shipped hall, or holds what the rounds won.
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import lab_barracks as L  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
FILES = ("barracks.nbt", "barracks_l2.nbt", "barracks_l3.nbt")
NBT = [os.path.join(RES, "data", "reignofnether", "structures", f) for f in FILES]
NBT_ASSETS = [os.path.join(RES, "assets", "reignofnether", "structures", f) for f in FILES]
KEEP_Y = 9          # a column whose top is at least this high belongs to the leading mass
PALE = ("smooth_sandstone", "sandstone", "calcite", "quartz", "diorite")


def tops(solid):
    t = {}
    for (x, y, z) in solid:
        t[(x, z)] = max(t.get((x, z), -1), y)
    return t


def patches(cells):
    out, seen = [], set()
    for c in sorted(cells):
        if c in seen:
            continue
        stack, comp = [c], {c}
        seen.add(c)
        while stack:
            u = stack.pop()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                v = (u[0] + dx, u[1] + dz)
                if v in cells and v not in seen:
                    seen.add(v)
                    comp.add(v)
                    stack.append(v)
        out.append(comp)
    return out


class BarracksTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lv = [S.load(p) for p in NBT]
        cls.solid = [{q: n for q, (n, _) in b.blocks.items() if n not in S.AIR} for b in cls.lv]
        cls.hits = [list(G.shoot(b, "rts_close", step=4).values()) for b in cls.lv]

    def _share(self, i, pred):
        return sum(1 for (q, n, _f, _b) in self.hits[i] if pred(q, n)) / max(1, len(self.hits[i]))

    def _keep(self, i):
        t = tops(self.solid[i])
        return patches({c for c, y in t.items() if y >= KEEP_Y}), max(t.values())

    def test_no_bullseye_and_little_team_blue(self):
        """No target block at any level (a red-white bullseye, a warm-team hue) and at most 2 % of what the RTS camera
        sees is lapis or blue glass / banners (a cool-team hue; shipped L1 8.9 %)."""
        for i in range(3):
            self.assertFalse([q for q, n in self.solid[i].items() if n.endswith("target")], "L%d target" % (i + 1))
            s = self._share(i, lambda q, n: "lapis" in n or "blue_" in n)
            self.assertLessEqual(s, 0.02, "L%d blue %.3f" % (i + 1, s))

    def test_one_keep_leads(self):
        """At L1 the columns at least 9 high are ONE patch of at least 9 cells (a keep, not the shipped turret and
        hall of equal 3 x 3 tops), and the top is at least 11 high."""
        keep, top = self._keep(0)
        self.assertEqual(len(keep), 1, [sorted(k) for k in keep])
        self.assertGreaterEqual(len(keep[0]), 9)
        self.assertGreaterEqual(top, 11)

    def test_keep_stands_behind_the_yard(self):
        """The keep stands on the north-east (centre x >= 5.5, z <= 2.5): the RTS camera looks from the south-west,
        so it looks over the drill court at the keep instead of the keep hiding the court."""
        keep, _ = self._keep(0)
        cx = sum(x for x, _ in keep[0]) / len(keep[0])
        cz = sum(z for _, z in keep[0]) / len(keep[0])
        self.assertGreaterEqual(cx, 5.5)
        self.assertLessEqual(cz, 2.5)

    def test_crown_is_tiered_and_gold(self):
        """The keep's top steps in at least 3 levels at or above 9 (the capitol's tiered crown, not a flat lid), and
        gold is 2.5-10 % of what the RTS camera sees at L1 (a crown that reads from far; not a gold building)."""
        keep, _ = self._keep(0)
        t = tops(self.solid[0])
        levels = {t[c] for c in keep[0]}
        self.assertGreaterEqual(len(levels), 3, sorted(levels))
        g = self._share(0, lambda q, n: n.startswith("gold"))
        self.assertTrue(0.025 <= g <= 0.10, "L1 gold %.3f" % g)
        for i in (1, 2):
            g = self._share(i, lambda q, n: n.startswith("gold"))
            self.assertLessEqual(g, 0.12, "L%d gold %.3f" % (i + 1, g))

    def test_keep_has_a_deep_gate(self):
        """At L1 the keep's yard face has a gate at least two cells deep under a lintel (air at y 1..3 in two cells
        one behind the other inside the keep's footprint, solid over them): the dark the white masses lack."""
        keep, _ = self._keep(0)
        s = self.solid[0]
        deep = [(x, z) for (x, z) in keep[0] if (x, z + 1) in keep[0]
                and all((x, y, z) not in s and (x, y, z + 1) not in s for y in (1, 2, 3))
                and (x, 4, z) in s and (x, 4, z + 1) in s]
        self.assertTrue(deep, "no deep gate in the keep")

    def test_yard_floor_in_the_mids(self):
        """Of the floor the RTS camera sees (layer 0), at most 30 % is pale stone (sandstone, calcite, quartz,
        diorite): the court sits in the mids so the white masses own the lights (shipped: a sand yard at wall value)."""
        for i in range(3):
            floor = [n for (q, n, _f, _b) in self.hits[i] if q[1] == 0]
            pale = sum(1 for n in floor if any(p in n for p in PALE)) / max(1, len(floor))
            self.assertLessEqual(pale, 0.30, "L%d pale floor %.2f" % (i + 1, pale))

    def test_exit_corner_open_to_the_sky(self):
        """At every level nothing stands higher than y 2 in the south-west quarter (x <= 3, z >= 4): the spawn's way
        out and the camera's view into the court (shipped L2 roofed it with a pavilion)."""
        for i in range(3):
            high = sorted(q for q in self.solid[i] if q[0] <= 3 and q[2] >= 4 and q[1] > 2)
            self.assertFalse(high, "L%d %s" % (i + 1, high[:6]))

    def test_shipped_is_the_generator_and_keeps_the_game_rules(self):
        """barracks*.nbt (data and assets) are what tools/lab_barracks.py draws, and its check_levels passes on them
        (footprint, the upgrade chain, the start type, the spawn columns, no pockets, every spawn walks out)."""
        built = (L.l1(), L.l2(), L.l3())
        with tempfile.TemporaryDirectory() as d:
            paths = [os.path.join(d, f) for f in FILES]
            for s, p in zip(built, paths):
                s.write(p)
            L.check_levels(*built, paths=paths)
            for p, a, b in zip(paths, NBT, NBT_ASSETS):
                want = {q: n for q, (n, _) in S.load(p).blocks.items() if n not in S.AIR}
                for shipped in (a, b):
                    got = {q: n for q, (n, _) in S.load(shipped).blocks.items() if n not in S.AIR}
                    self.assertEqual(got, want, shipped)


if __name__ == "__main__":
    unittest.main()
