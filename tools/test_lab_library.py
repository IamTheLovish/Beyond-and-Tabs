#!/usr/bin/env python3
"""The Sunforged Library (library.nbt, library_grand.nbt) as the RTS camera sees it - tools/lab_library.py's tests.

The all-factions blind Fable sweep (Nov 6 2026, eyes on main jar 42137577884, 62 buildings) scored RoN's library 3 / 10,
among the game's weakest: "no roof, no walls, four bookshelf posts and glass-pane fences; the copper altar is the wrong
faction colour". lovish's bar (the TomatoN castle): one dominant spire, crown bands and dark steep roofs. Each test here
fails on RoN's library, or holds a game rule the redraw must keep.
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
FILES = ("library.nbt", "library_grand.nbt")
NBT = [os.path.join(RES, "data", "reignofnether", "structures", f) for f in FILES]
NBT_ASSETS = [os.path.join(RES, "assets", "reignofnether", "structures", f) for f in FILES]
SUNFORGED_WHITE = ("quartz", "calcite")


def tops(solid):
    t = {}
    for (x, y, z) in solid:
        t[(x, z)] = max(t.get((x, z), -1), y)
    return t


def dark(n):
    return any(d in n for d in G.DARK)


class LibraryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lv = [S.load(p) for p in NBT]
        cls.solid = [{q: n for q, (n, _) in b.blocks.items() if n not in S.AIR} for b in cls.lv]
        cls.hits = [list(G.shoot(b, "rts_close", step=4).values()) for b in cls.lv]

    def _share(self, i, pred):
        return sum(1 for (q, n, _f, _b) in self.hits[i] if pred(q, n)) / max(1, len(self.hits[i]))

    def test_a_dark_pitched_roof_is_the_main_face(self):
        """At both levels at least half of the 49 columns have a dark top above the walls (y >= 5), and the roof
        climbs at least 2 cells from its eaves to its ridge. RoN's: no roof - the columns end in glass panes, bookshelves
        and oak at y <= 4."""
        for i in range(2):
            s, t = self.solid[i], tops(self.solid[i])
            roof = {c: y for c, y in t.items() if y >= 5 and dark(s[(c[0], y, c[1])])}
            self.assertGreaterEqual(len(roof), 25, "L%d dark roof columns %d of 49" % (i + 1, len(roof)))
            self.assertGreaterEqual(max(roof.values()) - min(roof.values()), 2, "L%d roof %s" % (i + 1, roof))

    def test_one_tower_leads_with_a_crown_band(self):
        """L1: the columns at least 9 high are one patch of at least 6 cells under a top at least 11 high (the tower,
        not four equal posts), and the tower's crown band - gold - shows on the camera: 1-8 % of what the RTS camera
        sees is gold (a band and a finial, not a gold building). RoN's library: top y 4, no gold."""
        t = tops(self.solid[0])
        high = {c for c, y in t.items() if y >= 9}
        self.assertGreaterEqual(len(high), 6, sorted(high))
        xs, zs = [c[0] for c in high], [c[1] for c in high]
        self.assertLessEqual((max(xs) - min(xs) + 1) * (max(zs) - min(zs) + 1), 9, "one tower, not scattered posts")
        self.assertGreaterEqual(max(t.values()), 11)
        g = self._share(0, lambda q, n: n.startswith("gold"))
        self.assertTrue(0.01 <= g <= 0.08, "L1 gold %.3f" % g)

    def test_sunforged_walls_and_no_copper(self):
        """The walls are the Sunforged kit's white: of what the RTS camera sees of the building above the floor (y >= 1),
        at least 25 % is quartz or calcite; and no copper at any level (the sweep: "the copper altar is the wrong
        faction colour"; copper is the Brass faction's). RoN's: oak, glass and bookshelves, a waxed copper altar."""
        for i in range(2):
            w = self._share(i, lambda q, n: q[1] >= 1 and any(k in n for k in SUNFORGED_WHITE))
            self.assertGreaterEqual(w, 0.25, "L%d white %.2f" % (i + 1, w))
            self.assertFalse(sorted({n for n in self.solid[i].values() if "copper" in n}), "L%d copper" % (i + 1))

    def test_the_grand_library_grows_and_glows(self):
        """The upgrade reads from far: the grand stands at least 2 higher than L1 and has a light at its top (a lantern
        or glowstone above y 9). RoN's grand is one block higher and its glowstone lies in the floor."""
        t1, t2 = max(q[1] for q in self.solid[0]), max(q[1] for q in self.solid[1])
        self.assertGreaterEqual(t2 - t1, 2, "grand top %d, L1 top %d" % (t2, t1))
        lit = [q for q, n in self.solid[1].items() if n in ("lantern", "glowstone") and q[1] > 9]
        self.assertTrue(lit, "grand: no light high on the building")

    def test_game_rules(self):
        """What Library.java and RoN's placement read off the structures (unchanged by the redraw): both 7 x 7; L1 has no
        glowstone and the grand has some (Library.getUpgradeLevel: any glowstone = the Grand Library); oak_log and
        spruce_stairs (startingBlockTypes) in L1's layer 0; an enchanting table at both levels; the grand keeps every
        L1 cell (changeStructure only adds or replaces); no gravity blocks, fluids or redstone."""
        for i, b in enumerate(self.lv):
            xs = [q[0] for q in self.solid[i]]
            zs = [q[2] for q in self.solid[i]]
            self.assertEqual((min(xs), max(xs), min(zs), max(zs)), (0, 6, 0, 6), FILES[i])
            self.assertIn("enchanting_table", self.solid[i].values(), FILES[i])
            bad = sorted({n for n in self.solid[i].values() if n in ("sand", "gravel", "water", "lava", "redstone_wire")
                          or n.endswith(("_door", "_leaves", "_concrete_powder"))})
            self.assertFalse(bad, FILES[i])
        self.assertNotIn("glowstone", self.solid[0].values())
        self.assertIn("glowstone", self.solid[1].values())
        layer0 = {n for q, n in self.solid[0].items() if q[1] == 0}
        self.assertTrue({"oak_log", "spruce_stairs"} <= layer0, sorted(layer0))
        lost = sorted(set(self.solid[0]) - set(self.solid[1]))
        self.assertFalse(lost, "L1 -> grand would leave blocks behind: %s" % lost[:8])

    def test_shipped_is_the_generator(self):
        """library*.nbt (data and assets) are what tools/lab_library.py draws, and its check_levels passes."""
        import lab_library as L
        built = (L.l1(), L.grand())
        L.check_levels(*built)
        with tempfile.TemporaryDirectory() as d:
            for s, f, a, b in zip(built, FILES, NBT, NBT_ASSETS):
                p = os.path.join(d, f)
                s.write(p)
                want = {q: n for q, (n, _) in S.load(p).blocks.items() if n not in S.AIR}
                for shipped in (a, b):
                    got = {q: n for q, (n, _) in S.load(shipped).blocks.items() if n not in S.AIR}
                    self.assertEqual(got, want, shipped)


if __name__ == "__main__":
    unittest.main()
