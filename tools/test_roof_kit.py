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


if __name__ == "__main__":
    unittest.main()
