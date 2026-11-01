#!/usr/bin/env python3
"""The Verdant Court's army lab (grove*.nbt) as the RTS camera sees it - tools/lab_grove.py's tests.

The Stag Glade that shipped until Nov 2026 was "a mossy garden lost in grass" / "a small mossy ruin, a leaf clump"
(two fresh critics on eyes 41263811904: 4 / 4; luma median 0.406 on a 0.405 lawn). Blind massing rounds (eyes
41317615026, 41319848507 and later, PR body) picked one cherry crown over a ruined shrine, the capitol's family (#478),
and asked for: the crown one rounded mass seen from above, the trunk and roots where the camera looks, the plot's edge
not at grass value, a dark underside, no red-white target (a bullseye in a warm team hue). Each test here fails on the
shipped glade, or holds what the rounds won.
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import lab_grove as L  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
FILES = ("grove.nbt", "grove_l2.nbt", "grove_l3.nbt")
NBT = [os.path.join(RES, "data", "reignofnether", "structures", f) for f in FILES]
NBT_ASSETS = [os.path.join(RES, "assets", "reignofnether", "structures", f) for f in FILES]
STONE = ("mossy_stone_brick", "cracked_stone_brick", "chiseled_stone_brick", "mossy_cobblestone")
TRUNK = ("oak_log", "oak_wood", "dark_oak_log", "dark_oak_wood", "stripped_dark_oak_log", "stripped_dark_oak_wood")


class GroveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lv = [S.load(p) for p in NBT]
        cls.solid = [{q: n for q, (n, _) in b.blocks.items() if n not in S.AIR} for b in cls.lv]
        cls.hits = [G.shoot(b, "rts_close", step=4) for b in cls.lv]

    def _share(self, i, pred, ymin=0):
        hits = [(q, n) for (q, n, _f, _b) in self.hits[i].values()]
        good = [1 for (q, n) in hits if q[1] >= ymin and pred(n)]
        return len(good) / max(1, len(hits))

    def test_crown_is_cherry_from_the_rts_camera(self):
        """At every level the cherry crown is at least 30 % of what the RTS camera sees (the capitol's crown, the
        lab's separation from the lawn: shipped L1 0 %, its leaves at grass value)."""
        for i in range(3):
            s = self._share(i, lambda n: n.startswith("cherry_leaves"))
            self.assertGreaterEqual(s, 0.30, "L%d cherry %.2f" % (i + 1, s))

    def test_crown_spreads_over_the_plot(self):
        """Leaves stand over at least 45 % of the 9 x 8 plot at L1 (a crown, not the shipped two-deep curtain on the
        north edge that reads as a strip from above)."""
        cols = {(x, z) for (x, y, z), n in self.solid[0].items() if n.endswith("_leaves") and y >= 6}
        self.assertGreaterEqual(len(cols) / (L.SX * L.SZ), 0.45, len(cols))

    def test_crown_is_one_mass(self):
        """The leaf columns at L1 are one 4-connected patch from above (no floating clumps), and the crown's high
        point is at least 12 up (a single top, not a flat lid)."""
        cols = {(x, z) for (x, y, z), n in self.solid[0].items() if n.endswith("_leaves") and y >= 6}
        start = next(iter(cols))
        seen, todo = {start}, [start]
        while todo:
            x, z = todo.pop()
            for c in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                if c in cols and c not in seen:
                    seen.add(c)
                    todo.append(c)
        self.assertEqual(len(seen), len(cols))
        self.assertGreaterEqual(max(y for (x, y, z), n in self.solid[0].items() if n.endswith("_leaves")), 12)

    def test_trunk_is_seen_from_the_rts_camera(self):
        """Bark (oak or dark oak log / wood, stripped or not) is at least 4 % of the L1 RTS hits above the floor: the tree has a trunk and roots
        where the camera looks (critics, round 1: "no trunk or roots, the crown floats")."""
        s = self._share(0, lambda n: n in TRUNK, ymin=1)
        self.assertGreaterEqual(s, 0.04, "%.3f" % s)

    def test_shrine_stone_is_seen(self):
        """Shrine stone above the floor is at least 3 % of the L1 RTS hits (critics, massing: "the shrine hidden")."""
        s = self._share(0, lambda n: any(n.startswith(k) for k in STONE), ymin=1)
        self.assertGreaterEqual(s, 0.03, "%.3f" % s)

    def test_edge_is_not_lawn(self):
        """The plot's edge ring (y 0) is stone or podzol, not moss or grass: the shipped moss / grass fringe was the
        lawn's own value ("the moss rim melts into the grass on the south and east edges")."""
        ring = [(x, z) for x in range(L.SX) for z in range(L.SZ) if x in (0, L.SX - 1) or z in (0, L.SZ - 1)]
        lawn = sorted(c for c in ring if self.solid[0].get((c[0], 0, c[1]), "") in ("moss_block", "grass_block"))
        self.assertEqual(lawn, [])

    def test_crown_has_a_dark_underside(self):
        """At least half the leaf columns two or more deep end in dark oak leaves at their bottom at L1 (the green
        bands under the pink sat at grass value; the underside goes dark)."""
        cols = {}
        for (x, y, z), n in self.solid[0].items():
            if n.endswith("_leaves"):
                cols.setdefault((x, z), []).append((y, n))
        deep = [sorted(v) for v in cols.values() if len(v) >= 2]
        dark = [v for v in deep if v[0][1] == "dark_oak_leaves"]
        self.assertGreaterEqual(len(dark), len(deep) / 2, (len(dark), len(deep)))

    def test_no_target_blocks(self):
        """No target block at any level: red and white rings read as a bullseye in a warm team hue (all critics)."""
        for i in range(3):
            self.assertNotIn("target", set(self.solid[i].values()), "L%d" % (i + 1))

    def test_levels_pass_the_game_rules(self):
        """lab_grove's levels pass its own game checks (footprint, chain, spawn columns, walk-outs, pockets, support,
        palette), the shipped files are what it writes, and both resource trees carry the same bytes."""
        built = (L.l1(),) + tuple(L.levels())
        L.check_levels(*built)
        with tempfile.TemporaryDirectory() as d:
            for s, f, shipped in zip(built, FILES, self.lv):
                path = os.path.join(d, f)
                s.write(path)
                self.assertEqual(S.load(path).blocks, shipped.blocks, f)
        for a, b in zip(NBT, NBT_ASSETS):
            with open(a, "rb") as fa, open(b, "rb") as fb:
                self.assertEqual(fa.read(), fb.read(), os.path.basename(a))


if __name__ == "__main__":
    unittest.main()
