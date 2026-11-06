#!/usr/bin/env python3
"""The Brassworks Assembly Works (assembly_works.nbt, _l2, _l3) as the RTS camera sees it - tools/lab_assembly_works.py's
tests.

The all-factions blind Fable sweep (Nov 5 2026, eyes on main jar 42137577884, 62 buildings) scored the Works 3 / 10
(massing 2, detail 2): "one flat sheet of cut copper is ~70% of the silhouette at RTS; the rest is a deepslate box with
four windows", "a box with a lid", and at dusk "the copper roof turns a flat dull mauve and the building vanishes".
lovish's bar (the TomatoN castle): layered massing to one dominant landmark, dark steep roofs, broken ridges. Each art
test here fails on main's Works; the rule tests hold what AssemblyWorks.java and the placement read off the files.

Run: python3 tools/test_lab_assembly_works.py   (one small file; no Gradle)
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
FILES = ("assembly_works.nbt", "assembly_works_l2.nbt", "assembly_works_l3.nbt")
NBT = [os.path.join(RES, "data", "reignofnether", "structures", f) for f in FILES]
NBT_ASSETS = [os.path.join(RES, "assets", "reignofnether", "structures", f) for f in FILES]
SPAWN = (5, 5)     # 11 x 11: BuildingPlacement.centrePos is the middle cell, fixed at L1's box (changeStructure keeps it)
FORBIDDEN = ("sand", "gravel", "water", "lava", "redstone_wire")


def tops(solid):
    t = {}
    for (x, y, z) in solid:
        t[(x, z)] = max(t.get((x, z), -1), y)
    return t


def dark(n):
    return any(d in n for d in G.DARK)


def copper(n):
    return "copper" in n


class AssemblyWorksTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lv = [S.load(p) for p in NBT]
        cls.solid = [{q: n for q, (n, _) in b.blocks.items() if n not in S.AIR} for b in cls.lv]
        cls.props = [{q: p for q, (n, p) in b.blocks.items() if n not in S.AIR} for b in cls.lv]
        cls.hits = [list(G.shoot(b, "rts_close", step=4).values()) for b in cls.lv]

    def _share(self, i, pred):
        return sum(1 for (q, n, _f, _b) in self.hits[i] if pred(q, n)) / max(1, len(self.hits[i]))

    def test_the_roof_is_a_sawtooth_not_a_lid(self):
        """At every level the middle of the hall (columns x 4..7) runs north to south as a sawtooth: along each of those
        columns the roof's top climbs and falls at least 3 times (3 ridges), and no 3 neighbouring columns share a top
        (a slope, never a terrace). Main's Works: one copper deck at y 5 with a 1-high slab ridge - the tops along z are
        flat for 7 cells."""
        for i in range(3):
            t = tops(self.solid[i])
            for x in range(4, 8):
                col = [t.get((x, z), -1) for z in range(1, 10)]
                peaks = sum(1 for k in range(len(col)) if (k == 0 or col[k] > col[k - 1])
                            and (k == len(col) - 1 or col[k] >= col[k + 1]) and col[k] >= 6)
                self.assertGreaterEqual(peaks, 3, "L%d x %d tops along z %s" % (i + 1, x, col))
                runs = [col[k] for k in range(len(col) - 2) if col[k] == col[k + 1] == col[k + 2]]
                self.assertFalse(runs, "L%d x %d: a terrace in %s" % (i + 1, x, col))

    def test_the_camera_sees_dark_roofs_with_brass_stripes(self):
        """What the RTS camera (rts_close) sees: copper at most 40 % (brass is the accent stripe, not the whole
        building), darks (deepslate tiles, blackstone) at least 30 %. Main's Works: the copper deck is ~70 % of the
        silhouette (the sweep)."""
        for i in range(3):
            c = self._share(i, lambda q, n: copper(n))
            d = self._share(i, lambda q, n: dark(n))
            self.assertLessEqual(c, 0.40, "L%d copper %.2f" % (i + 1, c))
            self.assertGreaterEqual(d, 0.30, "L%d dark %.2f" % (i + 1, d))

    def test_one_chimney_leads(self):
        """L1: the tallest column is ONE 1 x 1 shaft standing at least 2 above every column that is not part of its own
        crown (a 3 x 3 round it) and at least 12 high, with a lit campfire on top (the smoke reads at RTS). Main's
        Works: two equal campfire stacks at y 8, a twin pair, no leader."""
        s, t = self.solid[0], tops(self.solid[0])
        top = max(t.values())
        cols = [c for c, y in t.items() if y == top]
        self.assertEqual(len(cols), 1, "tallest columns %s at y %d" % (cols, top))
        cx, cz = cols[0]
        self.assertGreaterEqual(top, 12)
        self.assertEqual(s[(cx, top, cz)], "campfire")
        rest = max(y for c, y in t.items() if abs(c[0] - cx) > 1 or abs(c[1] - cz) > 1)
        self.assertGreaterEqual(top - rest, 2, "chimney %d, next %d" % (top, rest))

    def test_the_silhouette_steps(self):
        """L1 is accreted volumes, not a box: at least 4 heights each hold the top of at least 3 columns (lean-tos, hall,
        office, chimney crown), and no one height holds the top of more than a third of the plot. Main's Works: y 5 holds
        108 of 121 tops, y 6 holds 11, y 8 two."""
        t = tops(self.solid[0])
        count = {}
        for y in t.values():
            if y >= 3:
                count[y] = count.get(y, 0) + 1
        self.assertGreaterEqual(sum(1 for c in count.values() if c >= 3), 4, "tops by height %s" % sorted(count.items()))
        self.assertLessEqual(max(count.values()), len(t) // 3, "tops by height %s" % sorted(count.items()))

    def test_a_forge_glows_on_the_camera_side(self):
        """At dusk the Works has a lit point at ground level the camera sees: at L1 a lit blast furnace faces south or
        west with nothing between it and the plot's edge. Main's: the blast furnace faces north, into the hall."""
        s, p = self.solid[0], self.props[0]
        ok = []
        for (x, y, z), n in s.items():
            if n != "blast_furnace" or p[(x, y, z)].get("lit") != "true":
                continue
            f = p[(x, y, z)].get("facing")
            ahead = [(x, y, k) for k in range(z + 1, 11)] if f == "south" else \
                [(k, y, z) for k in range(0, x)] if f == "west" else None
            if ahead is not None and not any(c in s for c in ahead):
                ok.append((x, y, z))
        self.assertTrue(ok, "no lit forge faces the camera")

    def test_the_works_grows(self):
        """Each level is taller and adds lit windows: top L1 < L2 < L3, each step at least 3; L3 at most 32 (the T2 size
        ladder; L1 at most 16); L2 and L3 each have more light blocks (shroomlight, lantern, campfire, lit furnace) than
        the level before."""
        t = [max(q[1] for q in s) for s in self.solid]
        self.assertLessEqual(t[0], 15)
        self.assertLessEqual(t[2], 31)
        self.assertGreaterEqual(t[1] - t[0], 3, t)
        self.assertGreaterEqual(t[2] - t[1], 3, t)
        lights = [sum(1 for n in s.values() if n in ("shroomlight", "lantern", "campfire", "blast_furnace")) for s in self.solid]
        self.assertGreater(lights[1], lights[0], lights)
        self.assertGreater(lights[2], lights[1], lights)

    def test_game_rules(self):
        """What AssemblyWorks.java and the placement read: 11 x 11 at every level; polished_blackstone and
        deepslate_bricks (startingBlockTypes) in L1's layer 0; a full foundation; each level keeps every cell of the one
        before (changeStructure only adds or replaces); the spawn column (the middle cell, centrePos)
        open from y 1 to one above each level's own centre height, and a 3-wide way out south from it to the plot's
        edge (the Steam Juggernaut, spawnRadiusOffset 2); no gravity blocks, fluids, doors or leaves; data and assets
        copies identical."""
        for i, s in enumerate(self.solid):
            # centrePos is the box's middle; a saved game reloaded at L2 / L3 measures it off that level's taller box
            # (the Barracks round 4 lesson), so each level's own centre height counts
            ytop = max(q[1] for q in s) // 2 + 1
            xs, zs = [q[0] for q in s], [q[2] for q in s]
            self.assertEqual((min(xs), max(xs), min(zs), max(zs)), (0, 10, 0, 10), FILES[i])
            self.assertTrue(all((x, 0, z) in s for x in range(11) for z in range(11)), FILES[i] + ": foundation hole")
            bad = sorted({n for n in s.values() if n in FORBIDDEN or n.endswith(("_door", "_leaves", "_concrete_powder"))})
            self.assertFalse(bad, FILES[i])
            blocked = [y for y in range(1, ytop + 1) if (SPAWN[0], y, SPAWN[1]) in s]
            self.assertFalse(blocked, "%s: spawn column blocked at %s" % (FILES[i], blocked))
            way = [(x, y, z) for x in (4, 5, 6) for y in (1, 2, 3) for z in range(SPAWN[1], 11)
                   if (x, y, z) in s and "rail" not in s[(x, y, z)]]
            self.assertFalse(way, "%s: the way out south is blocked at %s" % (FILES[i], way[:6]))
        layer0 = {n for q, n in self.solid[0].items() if q[1] == 0}
        self.assertTrue({"polished_blackstone", "deepslate_bricks"} <= layer0, sorted(layer0))
        for a, b in ((0, 1), (1, 2)):
            lost = sorted(set(self.solid[a]) - set(self.solid[b]))
            self.assertFalse(lost, "%s -> %s leaves blocks behind: %s" % (FILES[a], FILES[b], lost[:8]))
        for a, b in zip(NBT, NBT_ASSETS):
            with open(a, "rb") as fa, open(b, "rb") as fb:
                self.assertEqual(fa.read(), fb.read(), b)

    def test_shipped_is_the_generator(self):
        """assembly_works*.nbt are what tools/lab_assembly_works.py draws, and its check_levels passes."""
        import lab_assembly_works as L
        l2, l3 = L.levels()
        built = (L.l1(), l2, l3)
        with tempfile.TemporaryDirectory() as d:
            paths = []
            for s, f in zip(built, FILES):
                p = os.path.join(d, f)
                s.write(p)
                paths.append(p)
            L.check_levels(*built, paths=paths)
            for p, shipped in zip(paths, NBT):
                with open(p, "rb") as fa, open(shipped, "rb") as fb:
                    self.assertEqual(fa.read(), fb.read(), shipped)


if __name__ == "__main__":
    unittest.main()
