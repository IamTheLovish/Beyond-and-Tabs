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
                self.assertNotIn("202", s, (seed, row))                               # nor a pale one (20202)

    def test_no_pale_chequer_over_many_seeds(self):
        """review, Oct 29: '202' (a pale chequer) in 900 of 2000 seeds - pale cells stand 2+ base cells apart."""
        bad = [seed for seed in range(2000) if any("202" in "".join(map(str, row)) for row in R.tones(5, 20, seed=seed))]
        self.assertEqual(bad, [])

    def test_off_target_raises(self):
        """After its attempts tones() / retone() returned an off-target grid silently; now they say so."""
        with self.assertRaises(ValueError):
            R.tones(1, 2, seed=0)                  # 2 cells: a base share of 0, 50 or 100 %, never 55-72 %
        with self.assertRaises(ValueError):
            R.retone({(x, 0, 0): ("bricks", {}) for x in range(2)}, R.SLATE)

    def test_even_span_oversails_all_round(self):
        """The works shed's 6-wide gable: the same 1-block ring as an odd span (eaves 2 x 12, verges 2 x 6, less the
        two-cell crest's ends)."""
        r = R.gable(6, 10, seed=0)
        self.assertEqual(S.eave_overhang(S.Build({**walls(6, 10), **r})), {1: 2 * 12 + 2 * 6 - 4})

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
        """Every eave and verge cell is 1 block proud: the overhang count is the whole ring round the walls (two
        eaves of length + 2, two verges of span), less the ridge cap's ends (a full block, not a roof shape). A roof
        with its verges or one eave missing also measures {1: n} - the count is what catches it (review, Oct 29)."""
        def over(r):
            return S.eave_overhang(S.Build({**walls(self.span, self.length), **r}))
        ring = 2 * (self.length + 2) + 2 * self.span - 2
        self.assertEqual(over(self.r), {1: ring})
        no_verges = {q: c for q, c in self.r.items() if q[2] not in (-1, self.length) or c[0] in R.SLATE["ridge"]}
        no_eave = {q: c for q, c in self.r.items() if q[0] != -1}
        self.assertNotEqual(over(no_verges), {1: ring})
        self.assertNotEqual(over(no_eave), {1: ring})

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

    def test_ridge_is_a_family(self):
        """r8: a sloping ridge (a hip falling from a gable) needs ridge stairs and slabs, not only the cap block."""
        for mats in (R.SLATE, R.COPPER):
            self.assertEqual([R.shape(n) for n in mats["ridge"]], [0, 1, 2])

    def test_even_span_has_a_two_wide_ridge(self):
        """r8: a span with no middle cell (the works shed, 6 wide) caps its two crest cells with the ridge block, at
        the crest's own height."""
        r = R.gable(6, 5, seed=0)
        ridge = {(u, y) for (u, y, v), (n, _) in r.items() if n in R.SLATE["ridge"]}
        self.assertEqual(ridge, {(2, 1), (3, 1)})
        self.assertFalse([q for q, (n, p) in r.items() if q[0] in (2, 3) and n not in R.SLATE["ridge"]
                          and q[2] not in (-1, 5)])

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

    def test_around_drops_risers_to_a_lower_roof_beside_it(self):
        """r8: a wing hip that lands on a lower hall roof shows its face down to that roof, not a hole."""
        H = {(x, 0): 11 for x in range(3)}
        bare = R.field(H, seed=1)
        self.assertNotIn((1, 9, 0), bare)
        r = R.field(H, seed=1, around={(x, 1): 8.5 for x in range(3)})
        self.assertEqual([R.shape(r[(1, y, 0)][0]) for y in (8, 9)], [0, 0])
        self.assertEqual(r[(1, 10, 0)], bare[(1, 10, 0)])

    def test_around_sets_the_facing_toward_a_higher_roof(self):
        """A strip of roof against a tower (around: the tower's columns) faces the tower, not a ridge cap."""
        H = {(1, z): 8 for z in range(3)}
        self.assertEqual({n for n, _ in R.field(H).values()}, {R.SLATE["ridge"][0]})
        r = R.field(H, around={(0, z): 20 for z in range(3)})
        self.assertEqual({p["facing"] for (x, y, z), (n, p) in r.items()}, {"west"})

    def test_regions_are_graded_on_their_own(self):
        """Each roof (a region of columns) holds the base tone 55-72 % by itself; fixed columns aren't graded."""
        H = {(x, z): 8 + 0.5 * min(x, 10 - x) for x in range(11) for z in range(12)}
        a = {(x, z) for x in range(11) for z in range(3)}
        b = set(H) - a
        fixed = {(x, 11): R.SLATE["verge"] for x in range(11)}
        for seed in range(10):
            r = R.field(H, seed=seed, fixed=fixed, regions=[a, b])
            for reg in (a, b - set(fixed)):
                top = [r[(x, max(q[1] for q in r if (q[0], q[2]) == (x, z)), z)][0] for x, z in reg]
                t = [R.family(n, R.SLATE) for n in top if R.family(n, R.SLATE) is not None]
                self.assertTrue(0.55 <= t.count(0) / len(t) <= 0.72, (seed, len(reg), t.count(0) / len(t)))

    def test_facing_looks_within_its_own_roof(self):
        """At a junction (a hall roof under a taller wing) a hall stair still faces up its own slope, and the wing's
        risers drop to the hall roof."""
        hall = {(x, z): 7.5 + 0.5 * min(x, 10 - x) for x in range(10) for z in range(3, 6)}
        wing = {(x, z): min(9 + 1.5 * min(x, 9 - x), 15 - 2 * z) for x in range(10) for z in range(3)}
        r = R.field({**hall, **wing}, regions=[set(hall), set(wing)], along=lambda c: (c[0], c[1]))
        self.assertEqual(r[(3, 8, 3)][1]["facing"], "east")               # hall: 9 at x 3, uphill x 4 (9.5)
        self.assertEqual(r[(7, 8, 3)][1]["facing"], "west")
        self.assertEqual([R.shape(r[(3, y, 2)][0]) for y in (9, 10)], [0, 1])   # the wing's riser over the hall

    def test_ridge_columns_are_not_graded(self):
        """The base share is of the tiles the roof shows: ridge-capped columns are left out of the count."""
        H = {(x, z): 8 + 0.5 * min(x, 6 - x) for x in range(7) for z in range(30)}
        for seed in range(15):
            r = R.field(H, seed=seed, along=lambda c: (c[0], c[1]))
            tops = [r[(x, max(q[1] for q in r if (q[0], q[2]) == (x, z)), z)][0] for x, z in H]
            t = [R.family(n, R.SLATE) for n in tops if R.family(n, R.SLATE) is not None]
            self.assertEqual(len(t), 6 * 30)
            self.assertTrue(0.55 <= t.count(0) / len(t) <= 0.72, (seed, t.count(0) / len(t)))

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
        r = R.retone(self.cells, R.SLATE, seed=2, along=lambda q: (q[2], q[0]))
        for y in range(10):
            row = "".join(str(R.family(r[q][0], R.SLATE)) for q in sorted(q for q in r if q[1] == y))
            self.assertNotIn("101", row, y)
            self.assertNotIn("1111", row, y)

    def test_wet_low_bleached_high(self):
        for seed in range(20):
            r = R.retone(self.cells, R.SLATE, seed=seed)
            fam = {q: R.family(n, R.SLATE) for q, (n, _) in r.items()}
            low = [t for q, t in fam.items() if q[1] < 5]
            high = [t for q, t in fam.items() if q[1] >= 5]
            self.assertGreater(low.count(1) / len(low), high.count(1) / len(high), seed)

    def test_course_and_along_keys(self):
        """A gable's courses run along its ridge: course = distance from the eave, along = the ridge axis."""
        cells = {(x, 7 + min(x, 6 - x) // 2, z): ("bricks", {}) for x in range(7) for z in range(20)}
        r = R.retone(cells, R.SLATE, seed=4, course=lambda q: min(q[0], 6 - q[0]), along=lambda q: (q[0], q[2]))
        for x in range(7):
            row = "".join(str(R.family(r[q][0], R.SLATE)) for q in sorted(q for q in r if q[0] == x))
            self.assertNotIn("101", row, x)
        eave = [R.family(n, R.SLATE) for q, (n, _) in r.items() if q[0] in (0, 6)]
        ridge = [R.family(n, R.SLATE) for q, (n, _) in r.items() if q[0] == 3]
        self.assertGreaterEqual(eave.count(1) / len(eave), ridge.count(1) / len(ridge))


if __name__ == "__main__":
    unittest.main()
