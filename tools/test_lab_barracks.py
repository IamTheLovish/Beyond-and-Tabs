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

# The floor blocks' values from ABOVE on today's stills (tools/camera_colour.measure, the median 'up' value of the
# pixels each block's rays hit): eyes 41792408905 (Nov 4, main: the Fable critics' run) and 41973122578 (Nov 5, this
# lane's Lotus before / after). design/vehicle_labs/camera_colours.json was measured Oct 30 on the block pack of then
# and no longer matches the stills (smooth stone 0.33 there, 0.63 on screen; tuff 0.34 there, 0.19 on the Ashborn
# capitol's terrace): the first court fix, smooth stone, passed on the old table and read LIGHTER than the shipped
# court on the after still (court crop luma 0.56 -> 0.60). The court test reads this measured model instead.
COURT_UP = {"polished_andesite": 0.59, "polished_andesite_stairs": 0.55, "smooth_stone": 0.63, "stone": 0.40,
            "stone_bricks": 0.44, "stone_brick_stairs": 0.41, "chiseled_stone_bricks": 0.42, "dark_oak_planks": 0.31,
            "tuff": 0.19, "pearlescent_froglight": 0.96}


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

    # ---- the Fable critics' fixes (Nov 4, eyes 41792408905: L1 5 / 10, L3 4 / 10; critic-b.md, critic-c.md F6) ----
    def test_court_in_the_mids_on_camera(self):
        """The court the RTS camera sees (layer 0) reads in the mids at every level: the median of what each floor
        cell looks like from above (COURT_UP, measured on today's stills) sits in the Art method's mids, 0.28-0.50,
        under the white masses (0.7) and near the lawn (grass 0.36), not in the lights. Shipped: a polished andesite
        court at 0.59 - on the noon still luma 0.56, lighter than the keep's south face (0.51). A floor block the model
        lacks fails. And at most 35 % of the court's pixels are in the lights (>= 0.50: the pale path and the
        start-type steps only) - a median alone passed the shipped Lotus court on its stone-brick border."""
        for i in range(3):
            floor = [n for (q, n, _f, _b) in self.hits[i] if q[1] == 0]
            self.assertEqual(sorted({n for n in floor if n not in COURT_UP}), [], "L%d: unmeasured floor" % (i + 1))
            vals = sorted(COURT_UP[n] for n in floor)
            self.assertTrue(vals, "L%d: no floor seen" % (i + 1))
            med = vals[len(vals) // 2]
            self.assertTrue(0.28 <= med < 0.50, "L%d court median %.2f" % (i + 1, med))
            light = sum(1 for v in vals if v >= 0.50) / len(vals)
            self.assertLessEqual(light, 0.35, "L%d court in the lights %.2f" % (i + 1, light))

    def test_keep_faces_have_a_dark_band_and_windows(self):
        """The keep's two camera faces (west x 6, south z 3) are no 8-high blank planes: in every column of a face,
        looking in from the camera (the first solid cell at depth 0 or 1), no run of more than 4 pale cells from y 1
        to the wall top, and at least 4 dark cells above the gate's lintel (y >= 5: a dark string course and
        recessed windows). Shipped: chiseled quartz at y 5, the same value as the bricks - 8 pale in a row."""
        for i in range(3):
            s = self.solid[i]
            faces = {"west": [((L.KX0, y, z), (L.KX0 + 1, y, z)) for z in range(L.KZ0, L.KZ1 + 1)
                              for y in range(1, L.KW + 1)],
                     "south": [((x, y, L.KZ1), (x, y, L.KZ1 - 1)) for x in range(L.KX0, L.KX1 + 1)
                               for y in range(1, L.KW + 1)]}
            for face, cells in faces.items():
                seen = {}
                for front, back in cells:
                    seen[front] = s.get(front) or s.get(back) or "air"
                dark = [q for q, n in seen.items() if q[1] >= 5 and any(d in n for d in G.DARK)]
                self.assertGreaterEqual(len(dark), 4, "L%d %s face: dark above the lintel %s" % (i + 1, face, dark))
                for col in {(q[0], q[2]) for q in seen}:
                    run = best = 0
                    for y in range(1, L.KW + 1):
                        n = seen[(col[0], y, col[1])]
                        run = run + 1 if any(p in n for p in G.PALE) else 0
                        best = max(best, run)
                    self.assertLessEqual(best, 4, "L%d %s face column %s: %d pale in a row" % (i + 1, face, col, best))

    def test_crown_has_a_point_not_a_gold_cube(self):
        """No 2 x 2 x 2 gold cube at any level (the shipped crown: 'a gold box on a white chimney', critic B), and the
        L1 keep ends in ONE cell (a white spike with a gold finial and its lantern), with a light on the keep for dusk."""
        for i in range(3):
            g = {q for q, n in self.solid[i].items() if n == "gold_block"}
            cubes = [q for q in g if all((q[0] + a, q[1] + b, q[2] + c) in g
                                         for a in (0, 1) for b in (0, 1) for c in (0, 1))]
            self.assertFalse(cubes, "L%d gold cube at %s" % (i + 1, cubes))
        s = self.solid[0]
        top = max(q[1] for q in s)
        self.assertEqual(len([q for q in s if q[1] == top]), 1, "L1 top layer %s" % [q for q in s if q[1] == top])
        lights = [q for q, n in s.items() if n == "lantern" and L.KX0 <= q[0] <= L.KX1 and q[1] > L.KW]
        self.assertTrue(lights, "L1: no lantern on the keep")

    def test_gold_only_on_the_keep(self):
        """Gold stays the keep's (critic B L3 fix 4): no gold stud outside the keep's crown (x >= 4, z <= 5) at any
        level, and no row of three gold cells above the keep's wall (shipped L3: the angel reads as a gold cross,
        gold at x 6..8 on y 15; the west tower's knop and the east pavilion's boss are studs)."""
        for i in range(3):
            g = {q for q, n in self.solid[i].items() if n == "gold_block"}
            out = sorted(q for q in g if q[0] < 4 or q[2] > 5)
            self.assertFalse(out, "L%d gold studs %s" % (i + 1, out))
            rows = [q for q in g if q[1] > L.KW and ({(q[0] - 1, q[1], q[2]), (q[0] + 1, q[1], q[2])} <= g
                                                      or {(q[0], q[1], q[2] - 1), (q[0], q[1], q[2] + 1)} <= g)]
            self.assertFalse(rows, "L%d gold crossbar at %s" % (i + 1, rows))

    def test_l3_keep_leads_over_a_battlemented_hall(self):
        """L3 (critic B L3 fixes 1-2, lovish Nov 4 "Barracks L3: wider keep"): the keep widens into a 6 x 6 crown
        tier over the court (one layer at y >= 9 covers at least 30 of the 36 cells x 4..9, z 0..5) and the north
        wing west of it is a battlemented hall at most 11 high with dark slits on its camera face (z 1) at y 8..9.
        Shipped: the wing rose to y 12 (the crown's height), a blank white slab with no dark."""
        s = self.solid[2]
        cover = max(sum(1 for x in range(4, 10) for z in range(0, 6) if (x, y, z) in s) for y in range(9, 14))
        self.assertGreaterEqual(cover, 30, "L3 keep crown tier covers %d of 36" % cover)
        wing = [q for q in s if q[0] <= 3 and q[2] <= 1]
        self.assertLessEqual(max(q[1] for q in wing), 11, "L3 north wing top")
        slits = [q for q, n in s.items() if q[0] <= 3 and q[2] in (0, 1) and q[1] in (8, 9)
                 and any(d in n for d in G.DARK)]
        self.assertGreaterEqual(len(slits), 2, "L3 north wing slits %s" % slits)

    def test_wing_fronts_have_a_dark_hem(self):
        """The wings' deck course facing the camera and the court (north wing front z 1, east wing front x 8, the west
        wing's outer wall x 0, at y 4) is dark oak at every level (critic C F6): pale units in the court stand against
        a dark line, not white; shipped: polished diorite, the wings' white."""
        for i in range(3):
            s = self.solid[i]
            hem = [(x, 4, 1) for x in range(0, 6)] + [(8, 4, z) for z in (5, 6)] + [(0, 4, z) for z in (2, 3)]
            pale = sorted(q for q in hem if not any(d in s.get(q, "") for d in G.DARK))
            self.assertFalse(pale, "L%d hem not dark at %s" % (i + 1, pale))

    # ---- round 2 (the all-factions blind Fable sweep, Nov 6, eyes on main jar 42137577884: barracks 3 / 10, the
    # game's weakest; #526's own blind round asked the same): "flat polished-quartz slab roofs, no pitch and no ridge -
    # three white rectangles", "a bare quartz pillar with a gold cube"; the bar (lovish's TomatoN castle) wants dark
    # steep roofs and slim towers with crown bands. ----
    def test_wing_roofs_are_dark_and_pitched(self):
        """At L1 every wing has a dark pitched roof: on each wing the back cell's top is higher than the front cell's
        (a slope down to the court, the ridge on the outer wall), and both tops are dark. Main: a white diorite deck
        with a slab parapet over the front - the front is the higher one and nothing on top is dark."""
        t = tops(self.solid[0])
        s = self.solid[0]
        wings = [((x, 0), (x, 1)) for x in range(0, 6)] + [((0, z), (1, z)) for z in (2, 3)] + \
            [((9, z), (8, z)) for z in (5, 6)]
        flat = [(b, f) for b, f in wings if not t.get(b, -1) > t.get(f, -1)]
        self.assertFalse(flat, "L1 wing cells not pitched (back, front): %s" % flat)
        pale = sorted(c for b, f in wings for c in (b, f)
                      if not any(d in s.get((c[0], t[c], c[1]), "") for d in G.DARK + ("lantern",)))
        self.assertFalse(pale, "L1 wing tops not dark: %s" % pale)

    def test_keep_wears_a_dark_roof_over_a_gold_band(self):
        """L1 and L2: the keep ends in a dark steep roof - of its 16 columns at least 12 have a dark top (the rest:
        the finial) - over a gold crown band, the wall's top course (y KW) gold all the way round its two camera faces
        (west x 6, south z 3). Main: a white crenellated tier and a gold base, the band's cells quartz."""
        for i in (0, 1):
            s = self.solid[i]
            t = tops(s)
            keep = [(x, z) for x in range(L.KX0, L.KX1 + 1) for z in range(L.KZ0, L.KZ1 + 1)]
            dark = [c for c in keep if any(d in s.get((c[0], t[c], c[1]), "") for d in G.DARK)]
            self.assertGreaterEqual(len(dark), 12, "L%d keep tops dark %d of 16" % (i + 1, len(dark)))
            band = [(L.KX0, L.KW, z) for z in range(L.KZ0, L.KZ1 + 1)] + \
                [(x, L.KW, L.KZ1) for x in range(L.KX0, L.KX1 + 1)]
            bare = sorted(q for q in band if s.get(q) != "gold_block")
            self.assertFalse(bare, "L%d crown band not gold at %s" % (i + 1, bare))

    def test_l3_keeps_its_dark_roofs_and_the_spire_climbs(self):
        """Round 3 (blind r2 on eyes 42170353722: round 2 won 5 / 4 but L1 -> L3 fell 5 -> 3, "L3 removes the dark roofs
        entirely, an all-white crenellated box"; "the tower roof is three flat steps barely above the wings, no
        spire"): at L3 at least 24 of the crown tier's 36 cells (x 4..9, z 0..5) and every north wing cell west of it
        (x 0..3, z 0..1) have a dark top; at L1 the keep's roof climbs at least 3 cells from its eaves to its peak
        (the spire), under the finial."""
        s, t = self.solid[2], tops(self.solid[2])
        tier = [(x, z) for x in range(4, 10) for z in range(0, 6)]
        dark = [c for c in tier if any(d in s.get((c[0], t[c], c[1]), "") for d in G.DARK)]
        self.assertGreaterEqual(len(dark), 24, "L3 crown tier dark tops %d of 36" % len(dark))
        wing = sorted(c for c in [(x, z) for x in range(0, 4) for z in (0, 1)]
                      if not any(d in s.get((c[0], t[c], c[1]), "") for d in G.DARK))
        self.assertFalse(wing, "L3 north wing tops not dark at %s" % wing)
        t1 = tops(self.solid[0])
        roof = {c: t1[c] for c in [(x, z) for x in range(L.KX0, L.KX1 + 1) for z in range(L.KZ0, L.KZ1 + 1)]
                if c != L.FINIAL}
        self.assertGreaterEqual(max(roof.values()) - min(roof.values()), 3, "L1 keep roof eaves -> peak %s" % roof)

    # ---- round 4 (blind three-way r3 on eyes 42173016052, #564: 5 / 10): "the candle rack reads as an altar, no
    # barracks cues"; "the L3 tower roof is blunter than the L1 spire" - the bar's "layered massing stepping up to one
    # dominant spire". ----
    def test_court_is_a_drill_yard_not_an_altar(self):
        """No end rods at any level (the lance rack's white rods read as candles on an altar) and at least two straw
        targets in the court at L1: a hay block on a dark oak post, clear of the spawn columns and the exit corner."""
        for i in range(3):
            rods = sorted(q for q, n in self.solid[i].items() if n == "end_rod" and q[1] <= L.KW)
            self.assertFalse(rods, "L%d end rods in the court %s" % (i + 1, rods))
        s = self.solid[0]
        targets = [q for q, n in s.items() if n == "hay_block" and "dark_oak" in s.get((q[0], q[1] - 1, q[2]), "")
                   and (q[0], q[2]) not in L.CENTRE and not (q[0] <= 3 and q[2] >= 4)]
        self.assertGreaterEqual(len(targets), 2, "L1 straw targets %s" % targets)

    def test_l3_tower_rises_into_the_dominant_spire(self):
        """L3 stands at least 6 higher than L1, and its high part is slim: the columns within 6 of its top are at most
        9 cells (one 3 x 3 tower, not the 6 x 6 tier), under a dark roof (the columns just below the finial dark).
        #564's L3: 4 over L1, the spire 2 cells over a flat skirt."""
        s1, s3 = self.solid[0], self.solid[2]
        top1, top3 = max(q[1] for q in s1), max(q[1] for q in s3)
        self.assertGreaterEqual(top3 - top1, 6, "L3 top %d, L1 top %d" % (top3, top1))
        t = tops(s3)
        high = {c for c, y in t.items() if y >= top3 - 6}
        self.assertLessEqual(len(high), 9, "L3 high columns %s" % sorted(high))
        roofs = [c for c in high if any(d in s3.get((c[0], y, c[1]), "") for y in range(top3 - 8, top3 - 3)
                                        for d in G.DARK)]
        self.assertGreaterEqual(len(roofs), 5, "L3 tower roof dark in %s" % sorted(roofs))

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
