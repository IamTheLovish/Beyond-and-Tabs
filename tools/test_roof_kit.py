"""tools/roof_kit.py - the roof tiling system (lovish, Oct 28 2026: "There isnt a cool roof tiling system"; decided
22:59: red brick walls, grey slate roofs on halls / shed / wings, weathered green copper on the tower and spires).
Written before the kit. Run: python3 -m unittest discover -s tools -p 'test_roof_kit.py'

Frame: u runs across the span (walls at u 0..span-1), v along the ridge (0..length-1), y up from the eave (0).
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import roof_kit as R  # noqa: E402
import schem_study as S  # noqa: E402


def runs(row, tone):
    out, n = [], 0
    for t in row + [None]:
        if t == tone:
            n += 1
        elif n:
            out.append(n)
            n = 0
    return out


class Tones(unittest.TestCase):
    def test_deterministic(self):
        self.assertEqual(R.tones(5, 20, seed=3), R.tones(5, 20, seed=3))
        self.assertNotEqual(R.tones(5, 20, seed=3), R.tones(5, 20, seed=4))

    def test_base_share_and_no_stripes(self):
        for seed in range(30):
            g = R.tones(5, 20, seed=seed)
            flat = [t for row in g for t in row]
            self.assertTrue(0.55 <= flat.count(0) / len(flat) <= 0.72, (seed, flat.count(0) / len(flat)))
            for row in g:
                self.assertTrue(all(n <= 3 for n in runs(row, 1)), (seed, row))       # no dark stripe of 4+
                self.assertTrue(all(n == 1 for n in runs(row, 2)), (seed, row))       # pale cells single
                s = "".join(map(str, row))
                self.assertNotIn("101", s, (seed, row))                               # never a chequer

    def test_wet_eaves_bleached_ridge(self):
        dark_low = dark_high = pale_low = pale_high = 0
        for seed in range(40):
            g = R.tones(6, 24, seed=seed)
            dark_low += sum(r.count(1) for r in g[:3])
            dark_high += sum(r.count(1) for r in g[3:])
            pale_low += sum(r.count(2) for r in g[:3])
            pale_high += sum(r.count(2) for r in g[3:])
        self.assertGreater(dark_low, dark_high * 1.3)
        self.assertGreater(pale_high, pale_low)


def walls(span, length, h=3):
    """A plain wall ring under the roof: u 0..span-1, v 0..length-1, y -h..-1."""
    cells = {}
    for u in range(span):
        for v in range(length):
            if u in (0, span - 1) or v in (0, length - 1):
                for y in range(-h, 0):
                    cells[(u, y, v)] = ("bricks", {})
    return cells


class Gable(unittest.TestCase):
    def setUp(self):
        self.span, self.length = 9, 7
        self.r = R.gable(self.span, self.length, seed=1)

    def test_eaves_overhang_one_block(self):
        b = S.Build({**walls(self.span, self.length), **self.r})
        hist = S.eave_overhang(b)
        self.assertEqual(set(hist), {1})                          # every eave and verge cell is 1 block proud

    def test_slate_pitch_rises_half_a_block_per_block(self):
        tops = {}
        for (u, y, v), (n, p) in self.r.items():
            if v == 3 and (n.endswith("_slab") or n.endswith("_stairs")) and "gutter" not in n:
                h = y + (0.5 if p.get("type") == "bottom" else 1.0)
                tops[u] = max(tops.get(u, -9), h)
        front = [tops[u] for u in range(-1, 4)]
        self.assertEqual(front, [0.5, 1.0, 1.5, 2.0, 2.5])        # 26.5 degrees: the Victorian slate pitch
        self.assertEqual([tops[u] for u in range(5, 10)], front[::-1])   # the back slope mirrors

    def test_ridge_cap_and_mirror(self):
        ridge = {(u, v) for (u, y, v), (n, _) in self.r.items() if n in R.SLATE["ridge"]}
        self.assertEqual({u for u, _ in ridge}, {4})
        self.assertEqual({v for _, v in ridge}, set(range(-1, self.length + 1)))

    def test_gutters_under_both_eaves(self):
        gut = {(u, y, v) for (u, y, v), (n, p) in self.r.items() if n == R.SLATE["gutter"]}
        self.assertEqual({(u, y) for u, y, _ in gut}, {(-1, -1), (self.span, -1)})
        self.assertEqual(len(gut), 2 * (self.length + 2))

    def test_verges_are_the_dark_bargeboard(self):
        for (u, y, v), (n, _) in self.r.items():
            if v in (-1, self.length) and n not in (R.SLATE["ridge"] + (R.SLATE["gutter"],)):
                self.assertIn(n, R.SLATE["verge"], (u, y, v, n))

    def test_courses_carry_three_tones(self):
        names = [n for (u, y, v), (n, _) in self.r.items() if 0 <= v < self.length and u != 4 and y >= 0]
        fam = {R.family(n, R.SLATE) for n in names}
        self.assertEqual(fam, {0, 1, 2})

    def test_axis_x_puts_the_ridge_along_x(self):
        r = R.gable(5, 4, axis="x", seed=0)
        self.assertEqual({q[2] for q in r}, set(range(-1, 6)))    # across the span now in z
        f = {p["facing"] for (x, y, z), (n, p) in r.items() if n.endswith("_stairs") and z < 2 and "type" not in p
             and n != R.SLATE["gutter"]}
        self.assertTrue(f <= {"south"}, f)

    def test_copper_palette(self):
        r = R.gable(7, 3, mats=R.COPPER, seed=2)
        self.assertTrue(any("oxidized" in n for n, _ in r.values()))
        self.assertTrue(all(n.startswith("waxed_") or n in R.COPPER["ridge"] + R.COPPER["verge"]
                            + (R.COPPER["gutter"],) for n, _ in r.values()))


def wing(x, z):
    """The r8 wing's half-pyramid (a hip roof cut by its gable at z 0): 1.5 up per block across x from the eave at
    x 0, 2 up per block along z from the gable."""
    return min(9 + 1.5 * min(x, 8 - x), 15 - 2 * z)


class Field(unittest.TestCase):
    """r8 (Oct 29): a roof over any height field - hips, pyramid faces, half-hips - from the same course rule."""
    def setUp(self):
        self.H = {(x, z): wing(x, z) for x in range(9) for z in range(4)}
        self.r = R.field(self.H, seed=3)

    def top(self, x, z):
        y = max(q[1] for q in self.r if (q[0], q[2]) == (x, z))
        return y, self.r[(x, y, z)]

    def test_heights_become_shapes(self):
        y, (n, p) = self.top(1, 0)                                # 10.5: a bottom slab at y 10
        self.assertEqual((y, R.shape(n), p.get("type")), (10, 2, "bottom"))
        y, (n, p) = self.top(0, 2)                                # 9 at the eave: a stair at y 8, uphill east
        self.assertEqual((y, R.shape(n), p["facing"]), (8, 1, "east"))
        y, (n, p) = self.top(3, 1)                                # 13 under the gable's 13.5: uphill north
        self.assertEqual((y, R.shape(n), p["facing"]), (12, 1, "north"))
        y, (n, _) = self.top(4, 0)                                # 15, nothing higher round it: the ridge cap
        self.assertEqual((y, n), (14, R.SLATE["ridge"][0]))

    def test_every_column_tops_at_its_height(self):
        for (x, z), h in self.H.items():
            y, (n, p) = self.top(x, z)
            self.assertEqual(y + (0.5 if p.get("type") == "bottom" else 1.0), h, (x, z))

    def test_risers_only_where_a_lower_neighbour_shows_them(self):
        self.assertEqual(self.r[(1, 9, 0)][0], R.SLATE["tones"][R.family(self.r[(1, 9, 0)][0], R.SLATE)][0])
        self.assertNotIn((1, 8, 0), self.r)                       # the eave's stair tops at 9: y 8 is hidden
        for (x, y, z), (n, p) in self.r.items():
            h = self.H[(x, z)]
            if y + 1 < h - 0.5:                                   # a cell under the top: some neighbour shows it
                self.assertTrue(any(self.H.get((x + a, z + b), 99) < y + 1
                                    for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))), (x, y, z))

    def test_fixed_families_and_three_tones(self):
        stone = ("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab")
        r = R.field(self.H, seed=3, fixed={(x, 0): stone for x in range(9)})
        self.assertTrue(all(n in stone for (x, y, z), (n, _) in r.items() if z == 0))
        self.assertEqual({R.family(n, R.SLATE) for (x, y, z), (n, _) in r.items() if z > 0} - {None}, {0, 1, 2})

    def test_oversails_its_walls(self):
        b = S.Build({**{(x, y, z): ("bricks", {}) for x in range(1, 9) for z in range(4) for y in range(1, 8)
                        if x in (1, 8) or z in (0, 3)}, **self.r})
        self.assertEqual(set(S.eave_overhang(b)), {1})            # the x 0 eave course, 1 proud of the wall


class Retone(unittest.TestCase):
    """r8: courses laid on cells that already have their shapes (the tower cone's frozen voxels, a clipped gable)."""
    def setUp(self):
        self.cells = {}
        for y in range(10):                                       # a stepped face: rows of full blocks and stairs
            for z in range(-8 + y // 2, 9 - y // 2):
                self.cells[(y // 2, y, z)] = (("bricks", {}) if y % 2 == 0 else
                                              ("brick_stairs", {"facing": "east", "half": "bottom"}))

    def test_keeps_shapes_and_states(self):
        r = R.retone(self.cells, R.COPPER, seed=5)
        self.assertEqual(set(r), set(self.cells))
        for q, (n, p) in r.items():
            self.assertEqual(R.shape(n), R.shape(self.cells[q][0]), q)
            self.assertEqual(p, self.cells[q][1], q)
            self.assertIsNotNone(R.family(n, R.COPPER), n)

    def test_base_tone_share_holds_on_the_cells_themselves(self):
        for seed in range(20):
            r = R.retone(self.cells, R.COPPER, seed=seed)
            share = sum(R.family(n, R.COPPER) == 0 for n, _ in r.values()) / len(r)
            self.assertTrue(0.55 <= share <= 0.72, (seed, share))

    def test_no_chequer_along_a_course(self):
        r = R.retone(self.cells, R.SLATE, seed=2)
        for y in range(10):
            row = "".join(str(R.family(r[q][0], R.SLATE)) for q in sorted(q for q in r if q[1] == y))
            self.assertNotIn("101", row, y)
            self.assertNotIn("1111", row, y)


if __name__ == "__main__":
    unittest.main()
