"""tools/arch_kit.py - the faction-agnostic ARCHITECTURE KIT (recipes R1-R16 of
design/vehicle_labs/studies/detail_recipes.md as parametrised parts). Written BEFORE the kit (lovish's rule): each
test checks a part's architectural JOB against design/architecture_rules.md's gates, measured with
tools/building_gates.py / tools/schem_study.py - never a block count copied from the implementation.

Part frame (tools/gothic_parts.py's Part): the front faces SOUTH (+z), the wall's outer plane is z = 0 (proud is
z > 0, recessed z < 0), x runs along the face from 0, y = 0 is the part's floor.
Run: python3 -m pytest -q tools/test_arch_kit.py
"""
import os
import sys
import unittest
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import arch_kit as K  # noqa: E402
import building_gates as G  # noqa: E402
import roof_kit as R  # noqa: E402
import schem_study as S  # noqa: E402

SETS = (K.BRASSWORKS, K.SUNFORGED, K.GRAVEBOUND)
BW = K.BRASSWORKS


def build(part):
    return S.Build(dict(part.b))


def names(part):
    return Counter(n for n, _ in part.b.values() if n not in S.AIR)


def planes(part, face="south"):
    d = S.depth_layers(build(part), face)
    tot = sum(d.values())
    return {k: v / tot for k, v in d.items()}


def at(part, q):
    return part.b.get(q, ("air", {}))


def glyphs(part):
    return Counter(S.glyph(n) for n in names(part).elements())


def thin_share(part):
    g = glyphs(part)
    return sum(g[c] for c in "|=*g") / max(1, sum(g.values()))


def mat_ids(m):
    """Every id a material set names (its families, roof tones, glass, lights, timber, render)."""
    out = set()
    for f in (m.wall, m.wall2, m.trim, m.dark, m.metal):
        out |= {n for n in f if n}
    for fam in m.roof["tones"]:
        out |= set(fam)
    out |= set(m.roof["ridge"]) | set(m.roof["verge"]) | {m.roof["gutter"]}
    out |= set(m.glass) | set(m.light) | {n for n in m.timber if n} | {m.render}
    return out


def ring_box(w, d, h, m=BW):
    """A plain hollow box on a foundation (y 0), walls y 1..h of m.wall with an m.trim base course at y 1."""
    p = K.blank()
    for x in range(w):
        for z in range(d):
            p.set(x, 0, z, m.dark.full)
            if x in (0, w - 1) or z in (0, d - 1):
                for y in range(1, h + 1):
                    p.set(x, y, z, m.trim.full if y == 1 else m.wall.full)
    return p


# =====================================================================================================================
class MaterialSets(unittest.TestCase):
    def test_three_sets_every_id_vanilla(self):
        self.assertEqual(len({m.name for m in SETS}), 3)
        for m in SETS:
            self.assertEqual(G.unknown_ids(mat_ids(m)), [], m.name)

    def test_families_have_their_shapes(self):
        for m in SETS:
            for f in (m.wall, m.wall2, m.trim, m.dark):
                self.assertTrue(f.full and f.stairs.endswith("_stairs") and f.slab.endswith("_slab"), (m.name, f))
                self.assertTrue(f.wall and f.wall.endswith("_wall"), (m.name, f))
            self.assertTrue(m.metal.stairs.endswith("_stairs") and m.metal.slab.endswith("_slab"))
            self.assertTrue(m.glass[0].endswith("_pane"))

    def test_roof_and_wall_families_differ(self):
        """G4 by construction: a set's roof is a different material family from its walls."""
        for m in SETS:
            s = G.shares(S.Build(dict(K.demo_bay(m).b)))
            self.assertNotEqual(s["roof_family"], s["wall_family"], m.name)

    def test_brassworks_is_red_brick_grey_slate_green_copper(self):
        self.assertEqual(BW.wall.full, "bricks")
        self.assertEqual(BW.roof["tones"][0][0], "deepslate_tiles")
        self.assertIn("weathered", BW.metal.full)
        self.assertIs(BW.roof, R.SLATE)                  # composes with the roof kit, doesn't fork it


class EveryPart(unittest.TestCase):
    """Applies to every part in the catalogue, for all three material sets."""

    def each(self):
        for name, recipe, fn in K.CATALOGUE:
            for m in SETS:
                yield name, recipe, m, fn(m)

    def test_catalogue_covers_r1_to_r16(self):
        self.assertEqual({r for _, r, _ in K.CATALOGUE}, {"R%d" % i for i in range(1, 17)})

    def test_vanilla_1_20_1_ids_only(self):
        for name, _, m, p in self.each():
            self.assertEqual(G.unknown_ids(names(p)), [], (name, m.name))

    def test_blocks_come_from_the_material_set(self):
        """No Brassworks id leaks into the angels' or Gravebound's parts: every block is the set's own or one of the
        few universal fittings (bars, chain, lever, cauldron, rod, campfire)."""
        for name, _, m, p in self.each():
            stray = set(names(p)) - mat_ids(m) - K.UNIVERSAL
            self.assertEqual(stray, set(), (name, m.name))

    def test_rotations_keep_cells_and_turn_facings(self):
        for name, _, m, p in self.each():
            if m is not BW:
                continue
            for rot in range(4):
                q = K.normalised(p.rotated(rot))
                self.assertEqual(len(q.b), len(p.b), (name, rot))
                self.assertEqual(min(c[0] for c in q.b), 0, (name, rot))
                self.assertEqual(min(c[1] for c in q.b), 0, (name, rot))
                self.assertEqual(min(c[2] for c in q.b), 0, (name, rot))
            self.assertEqual(K.normalised(p.rotated(4)).b, K.normalised(p).b, name)
            south = sum(1 for n, pr in p.b.values() if pr.get("facing") == "south")
            west = sum(1 for n, pr in p.rotated(1).b.values() if pr.get("facing") == "west")
            self.assertEqual(south, west, name)

    def test_nothing_pops_off(self):
        """Settled as the game settles it, nothing hangs or would pop off (gen_structures' support check)."""
        for name, _, m, p in self.each():
            p.to_rig("%s/%s" % (name, m.name))


# ---- R1 / R2 / R3: eaves, verges, ridges ----------------------------------------------------------------------------
class R1Eave(unittest.TestCase):
    def flat_top(self, w=7, d=5, h=5):
        """A box whose four wall tops carry R1 eave runs (the lip and gutter run one cell past each end so the corners
        close), under a flat lead roof (m.dark slabs) that does NOT oversail: the lip is the only overhang."""
        p = ring_box(w, d, h)
        y = h - 1                                         # the eave band's y 0 = the course under the wall top
        p.place(K.eave(BW, w), 0, y, d - 1, 0)            # south (front +z)
        p.place(K.eave(BW, d), 0, y, 0, 1)                # west  (front turned to -x)
        p.place(K.eave(BW, w), w - 1, y, 0, 2)            # north
        p.place(K.eave(BW, d), w - 1, y, d - 1, 3)        # east
        for x in range(w):
            for z in range(d):
                p.set(x, h + 1, z, BW.dark.slab, type="bottom")
        return p

    def test_lip_closes_the_whole_perimeter(self):
        got, need = G.overhang(build(self.flat_top()))
        self.assertEqual(got, need)

    def test_red_without_the_band_no_overhang(self):
        p = ring_box(7, 5, 5)
        for x in range(7):
            for z in range(5):
                p.set(x, 6, z, BW.dark.slab, type="bottom")
        got, need = G.overhang(build(p))
        self.assertLess(got, need)

    def test_pale_lip_over_a_darker_course_with_a_gutter(self):
        e = K.eave(BW, 5)
        self.assertEqual(at(e, (2, 1, 1))[0], BW.trim.slab)             # the lip, 1 proud, at the wall top
        self.assertEqual(at(e, (2, 1, 1))[1]["type"], "top")
        self.assertEqual(at(e, (2, 1, 0))[0], BW.trim.full)             # the pale top course
        self.assertEqual(at(e, (2, 0, 0))[0], BW.wall2.full)            # the darker course under it
        self.assertEqual(at(e, (2, 2, 1))[0], BW.metal.stairs)          # the gutter on the lip
        self.assertEqual(at(e, (2, 2, 1))[1]["half"], "top")


class R2Oversail(unittest.TestCase):
    def house(self, w=9, d=7, h=5, drop=True):
        p = ring_box(w, d, h)
        p.place(K.oversail(BW, d, w, drop=h if drop else 0), 0, h + 1, 0)
        return p

    def test_eave_and_verge_perimeter_complete(self):
        got, need = G.overhang(build(self.house()))
        self.assertEqual(got, need)

    def test_roof_is_the_roof_kit_slate(self):
        roof = K.oversail(BW, 7, 9)
        tones = {n for n, _ in roof.b.values() if R.family(n, R.SLATE) is not None}
        self.assertTrue(tones)
        self.assertIn(R.SLATE["ridge"][0], names(roof))

    def test_verge_brackets_under_the_bargeboard(self):
        """Under-verge brackets: upside-down pale stairs directly below verge slabs at both gable ends."""
        roof = K.oversail(BW, 7, 9)
        br = [(q, v) for q, v in roof.b.items() if v[0] == BW.trim.stairs and v[1].get("half") == "top"]
        self.assertGreaterEqual(len(br), 4)
        for (x, y, z), _ in br:
            self.assertIn(x, (-1, 9))
            self.assertIn(at(roof, (x, y + 1, z))[0], R.SLATE["verge"])

    def test_downpipes_drop_to_the_ground(self):
        p = self.house()
        pipes = [q for q, (n, _) in p.b.items() if n == BW.dark.wall]
        self.assertTrue(pipes)
        self.assertEqual(min(q[1] for q in pipes), 1)
        self.assertGreaterEqual(len({(q[0], q[2]) for q in pipes}), 2)


class R3Ridge(unittest.TestCase):
    def test_cresting_comb_on_the_ridge(self):
        roof = K.oversail(BW, 7, 9)
        ridge = {(x, z): y for (x, y, z), (n, _) in roof.b.items() if n == R.SLATE["ridge"][0]}
        c = K.cresting(roof, BW)
        comb = {(x, z) for (x, y, z), (n, _) in c.b.items() if n == "iron_bars" and ridge.get((x, z)) == y - 1}
        self.assertGreaterEqual(len(comb), 9)
        c2 = K.cresting(roof, BW, every=2)
        comb2 = [q for q, (n, _) in c2.b.items() if n == "iron_bars"]
        self.assertLess(len(comb2), len(comb))
        self.assertGreater(len(comb2), 2)
        self.assertEqual(sum(1 for n, _ in c.b.values() if n == "lightning_rod"), 2)   # a finial at each end

    def test_hip_roof_hips_in_the_other_tone(self):
        h = K.hip_roof(BW, 7)
        hips, faces = Counter(), Counter()
        for (x, y, z), (n, _) in h.b.items():
            if n == "lightning_rod":
                continue
            fam = R.family(n, BW.roof)
            (hips if abs(x - 3) == abs(z - 3) else faces)[n if fam is None else fam] += 1
        self.assertTrue(hips and faces)
        self.assertNotEqual(hips.most_common(1)[0][0], faces.most_common(1)[0][0])

    def test_hip_roof_oversails_its_tower(self):
        p = ring_box(7, 7, 5)
        p.place(K.hip_roof(BW, 7), 0, 6, 0)
        got, need = G.overhang(build(p))
        self.assertEqual(got, need)


# ---- R4 / R5: dormers, roof lights ---------------------------------------------------------------------------------
class R4Dormer(unittest.TestCase):
    def test_an_eye_with_cheeks_and_a_rake(self):
        d = K.dormer(BW)
        n = names(d)
        self.assertGreaterEqual(n[BW.glass[0]], 6)                              # 3 wide, 2 rows
        self.assertGreaterEqual(n[BW.timber.planks], 4)                         # timber cheeks
        rake = [v for v in d.b.values() if v[0] in R.SLATE["tones"][0] or v[0] in R.SLATE["verge"]]
        self.assertGreaterEqual(len(rake), 4)                                  # the little gable in roof material
        self.assertTrue(any(S.glyph(k) == "*" for k in n))                      # lit behind the glass

    def test_three_depth_planes(self):
        self.assertGreaterEqual(len([v for v in planes(K.dormer(BW)).values() if v >= 0.10]), 3)


class R5RoofLight(unittest.TestCase):
    def test_clerestory_framed(self):
        c = K.clerestory(BW, 10)
        n = names(c)
        self.assertGreaterEqual(n[BW.glass[0]], 10)
        heads = [v for v in c.b.values() if v[0] == BW.dark.stairs and v[1].get("half") == "top"]
        sills = [v for v in c.b.values() if v[0] == BW.dark.stairs and v[1].get("half") == "bottom"]
        self.assertGreaterEqual(len(heads), 6)
        self.assertGreaterEqual(len(sills), 6)
        posts = [q for q, (k, _) in c.b.items() if k == BW.dark.full and q[2] == 0 and 1 <= q[1] <= 2]
        self.assertGreaterEqual(len({q[0] for q in posts}), 3)                 # glass between posts, never a bare row

    def test_glazed_barrel(self):
        g = K.roof_light(BW, 9)
        n = names(g)
        glass = n[BW.glass[1]] + n[BW.glass[0]]
        self.assertGreater(glass / sum(n.values()), 0.6)
        crown = max(y for (x, y, z) in g.b)
        self.assertEqual(at(g, (4, crown, 0))[0], BW.metal.full)                # the purlin at the crown
        prof = [max([y for (x, y, z) in g.b if x == 1 and z == k] or [-1]) for k in range(-3, 4)]
        self.assertEqual(prof, sorted(prof[:4]) + sorted(prof[4:], reverse=True))   # rises then falls


# ---- R6 / R7 / R8 / R9: walls, piers, openings ---------------------------------------------------------------------
class R6Cornice(unittest.TestCase):
    def test_steps_out_as_it_rises(self):
        c = K.cornice(BW, 8)
        reach = [max(z for (x, y, z) in c.b if y == k) for k in range(3)]
        self.assertEqual(reach, [0, 1, 1])
        teeth = [x for x in range(8) if S.glyph(at(c, (x, 1, 1))[0]) == "|"]
        self.assertTrue(3 <= len(teeth) <= 5)                                  # dentils: every other cell
        self.assertTrue(all(b - a >= 2 for a, b in zip(teeth, teeth[1:])))
        self.assertTrue(all(at(c, (x, 2, 1))[0] == BW.trim.slab for x in range(8)))   # ledge along the whole run

    def test_string_course_projects_a_drip(self):
        s = K.string_course(BW, 6)
        self.assertTrue(all(at(s, (x, 0, 1))[0] == BW.trim.stairs and at(s, (x, 0, 1))[1]["half"] == "top"
                            for x in range(6)))
        self.assertTrue(all(at(s, (x, 0, 0))[0] == BW.wall2.full for x in range(6)))


class R7Pier(unittest.TestCase):
    def test_base_shaft_capital(self):
        p = K.pier(BW, 5)
        self.assertEqual(at(p, (0, 0, 1))[0], BW.trim.full)                     # the base, a block wider
        face = [y for y in range(5) if at(p, (0, y, 1))[0] == BW.trim.wall]
        self.assertGreaterEqual(len(face), 2)                                  # the thin pilaster face
        cap = [at(p, (0, y, 1)) for y in (3, 4)]
        self.assertEqual([c[0] for c in cap], [BW.trim.stairs] * 2)             # the stair-pair capital
        self.assertEqual([c[1]["half"] for c in cap], ["bottom", "top"])
        bands = {y for y in range(1, 4) if at(p, (0, y, 0))[0] == BW.wall2.full}
        self.assertTrue(bands and len(bands) < 3)                              # banded rustication, not solid

    def test_lantern_on_a_bracket(self):
        p = K.pier(BW, 5, lantern=True)
        lights = [q for q, (n, _) in p.b.items() if n == BW.light[0]]
        self.assertEqual(len(lights), 1)
        self.assertGreaterEqual(lights[0][2], 2)                               # in front of the pilaster


class R8Windows(unittest.TestCase):
    def test_three_sizes(self):
        self.assertEqual(set(K.KINDS), {"tall", "medium", "small"})

    def test_sill_proud_pane_recessed_head_other_stone(self):
        for kind in K.KINDS:
            w = K.window(BW, kind)
            panes = [q for q, (n, _) in w.b.items() if n == BW.glass[0]]
            sills = [q for q, (n, p) in w.b.items() if n.endswith("_stairs") and q[1] == 0]
            self.assertTrue(panes and sills, kind)
            self.assertTrue(max(q[2] for q in panes) < 0, kind)                   # panes behind the wall plane
            self.assertTrue(min(q[2] for q in sills) > max(q[2] for q in panes), kind)
            top = max(q[1] for q in panes)
            head = {n for (x, y, z), (n, _) in w.b.items() if y == top + 1 and z == 0}
            self.assertTrue(head & {BW.wall2.full, BW.wall2.stairs, BW.trim.stairs, BW.trim.full}, (kind, head))

    def test_lit_behind_the_glass(self):
        for kind in K.KINDS:
            w = K.window(BW, kind)
            self.assertTrue(any(n in BW.light for n in names(w)), kind)
            self.assertFalse(any(n in BW.light for n in names(K.window(BW, kind, lit=False))), kind)

    def test_proportions(self):
        """Storey 4-5; the opening is 1/2 - 2/3 of a bay (window + one 1-wide pier) for the tall and medium sizes."""
        for kind in K.KINDS:
            W, H = K.KINDS[kind]
            self.assertTrue(4 <= H <= 5, kind)
            if kind == "small":
                continue
            w = K.window(BW, kind)
            open_x = {x for (x, y, z), (n, _) in w.b.items() if n == BW.glass[0]}
            self.assertTrue(0.5 <= len(open_x) / (W + 1) <= 2 / 3 + 1e-9, (kind, open_x, W))

    def test_three_depth_planes_in_a_bay(self):
        """A pier bay (pier + tall / medium window, the bay a facade repeats) shows >= 3 planes, none over 45 %; the
        small house window makes its own 3 planes (sill proud, wall, pane)."""
        for kind in ("tall", "medium"):
            pl = planes(K.bay(BW, kind))
            self.assertGreaterEqual(len([v for v in pl.values() if v >= 0.10]), 3, (kind, pl))
            self.assertLessEqual(max(pl.values()), 0.45 + 1e-9, (kind, pl))
        pl = planes(K.window(BW, "small"))
        self.assertGreaterEqual(len([v for v in pl.values() if v >= 0.10]), 3, pl)


class R9Portal(unittest.TestCase):
    def test_nested_orders_each_deeper_narrower_finer(self):
        p = K.portal(BW, w=3, h=4, orders=3)
        widths, mats = [], []
        for k in range(3):
            row = [x for (x, y, z), (n, _) in p.b.items() if z == -k and y == 1 and n not in S.AIR]
            gap = [x for x in range(min(row), max(row) + 1) if x not in row]
            widths.append(len(gap))
            edge = at(p, (min(gap) - 1, 1, -k))[0]
            mats.append(edge)
        self.assertEqual(widths, [7, 5, 3])
        self.assertEqual(len(set(mats)), 3)

    def test_three_depth_planes_step_and_lights(self):
        p = K.portal(BW)
        self.assertGreaterEqual(len([v for v in planes(p).values() if v >= 0.10]), 3)
        lights = [q for q, (n, _) in p.b.items() if n == BW.light[0]]
        self.assertEqual(len(lights), 2)
        xs = sorted(q[0] for q in lights)
        mid = (min(q[0] for q in p.b) + max(q[0] for q in p.b)) / 2
        self.assertTrue(xs[0] < mid < xs[1])                                   # one each side
        step = [q for q, (n, _) in p.b.items() if q[1] == 0 and q[2] >= 1]
        self.assertGreaterEqual(len(step), 7)


# ---- R10 / R11 / R12: towers, chimneys, domes ----------------------------------------------------------------------
class R10CorbelStage(unittest.TestCase):
    def test_corbels_every_other_cell_under_a_proud_course(self):
        t = K.corbel_stage(BW, 5)
        front = [at(t, (x, 1, 1)) for x in range(5)]
        kinds = ["s" if n.endswith("_stairs") and pr.get("half") == "top" else "." for n, pr in front]
        self.assertIn("".join(kinds), ("s.s.s", ".s.s."))
        self.assertTrue(all(at(t, (x, 2, 1))[0] not in S.AIR for x in range(-1, 6)))   # the oversail, full run
        upper = max(z for (x, y, z) in t.b if y >= 3)
        lower = max(z for (x, y, z) in t.b if y == 0)
        self.assertEqual(upper - lower, 1)                                     # the next stage is a block wider


class R11Chimney(unittest.TestCase):
    def test_plinth_bands_thin_corners_crown_smoke(self):
        c = K.chimney(BW, 10)
        rows = {}
        for (x, y, z), (n, _) in c.b.items():
            rows.setdefault(y, Counter())[S.glyph(n)] += 1
        shaft = [y for y in range(3, 8) if rows.get(y, {}).get("|", 0) >= 4]
        self.assertGreaterEqual(len(shaft), 3)                                 # thin corners up the shaft
        self.assertIn("campfire", names(c))
        x0 = min(x for (x, y, z) in c.b if y == 0)
        x1 = min(x for (x, y, z) in c.b if y == 5)
        self.assertLess(x0, x1)                                                # the plinth is wider
        self.assertTrue(any(n == BW.metal.full for n in names(c)))              # a metal band

    def test_flashing_skirt_where_it_leaves_the_roof(self):
        c = K.chimney(BW, 10, roof_y=4)
        skirt = [q for q, (n, p) in c.b.items() if q[1] == 4 and n == BW.trim.stairs and p.get("half") == "bottom"]
        self.assertGreaterEqual(len(skirt), 8)

    def test_wraps_the_brass_stack(self):
        import brass_lab_parts as bp
        base = {q for q in bp.stack(10, 3).b}
        self.assertTrue(base <= set(K.chimney(BW, 10).b))


class R12DomeSpire(unittest.TestCase):
    def test_spire_bulbs_and_necks(self):
        s = K.spire(BW)
        width = {}
        for (x, y, z) in s.b:
            width[y] = max(width.get(y, 0), abs(x), abs(z))
        ys = sorted(width)
        necks = [y for y in ys[1:-1] if width[y] < width[y - 1] and width[y] <= width.get(y + 1, 0)]
        self.assertTrue(necks)                                                 # a neck between two bulbs
        self.assertEqual(at(s, (0, ys[-1], 0))[0], "lightning_rod")
        metal = {BW.metal.full, BW.metal.stairs, BW.metal.slab}
        bulb = [y for y in ys if width[y] >= 2 and at(s, (1, y, width[y]))[0] in metal]
        self.assertGreaterEqual(len(bulb), 3)                                  # copper faces on the bulbs
        for y in bulb:                                                         # the meridian rib is another tone
            self.assertNotIn(at(s, (0, y, width[y]))[0], metal, y)

    def test_dome_wraps_the_gothic_ribbed_dome(self):
        import gothic_parts as gp
        d = K.dome(BW, 4)
        ref, _ = gp.ribbed_dome(4, pal=K.gothic_pal(BW))
        self.assertEqual(set(d.b), set(ref.b))
        self.assertIn(BW.trim.full, names(d))


# ---- R13 / R14 / R15 / R16: railings, timber, tone, machinery ------------------------------------------------------
class R13Balustrade(unittest.TestCase):
    def test_posts_show_above_the_coping(self):
        b = K.balustrade(BW, 9, every=4)
        posts = sorted({x for (x, y, z), (n, _) in b.b.items() if n == BW.trim.wall and y == 1})
        self.assertEqual(posts, [0, 4, 8])
        self.assertTrue(all(at(b, (x, 1, 0))[0] == BW.trim.slab for x in range(9) if x not in posts))
        self.assertGreater(thin_share(b), 0.6)

    def test_solid_parapet_variant(self):
        b = K.balustrade(BW, 9, panel="solid")
        self.assertNotIn("iron_bars", names(b))
        self.assertTrue(any(n == BW.wall.full for n in names(b)))


class R14HalfTimber(unittest.TestCase):
    def test_frame_carries_infill_set_back_jetty(self):
        t = K.half_timber(BW, 10)
        posts = {x for (x, y, z), (n, p) in t.b.items() if n == BW.timber.log and p.get("axis") == "y"}
        self.assertTrue({0, 9} <= posts)
        self.assertTrue(all(b - a <= 3 for a, b in zip(sorted(posts), sorted(posts)[1:])))
        plate = [x for (x, y, z), (n, p) in t.b.items() if n == BW.timber.log and p.get("axis") == "x"]
        self.assertGreaterEqual(len(plate), 10)
        post_z = max(z for (x, y, z), (n, p) in t.b.items() if n == BW.timber.log and p.get("axis") == "y")
        infill_z = max(z for (x, y, z), (n, _) in t.b.items() if n == BW.render)
        self.assertLess(infill_z, post_z)                                      # infill behind the frame
        lower = max(z for (x, y, z) in t.b if y == 1)
        self.assertEqual(post_z - lower, 1)                                    # the upper storey oversails by 1
        braces = [v for v in t.b.values() if v[0] == BW.timber.stairs]
        self.assertGreaterEqual(len(braces), 2)


def chequer(grid):
    """Any 2x2 window that alternates (a b / b a, a != b)?"""
    for (x, y), a in grid.items():
        b = grid.get((x + 1, y))
        if b is not None and b != a and grid.get((x, y + 1)) == b and grid.get((x + 1, y + 1)) == a:
            return True
    return False


class R15ToneWall(unittest.TestCase):
    def test_never_a_chequer_and_second_tone_10_to_25_percent(self):
        for m in SETS:
            for seed in range(25):
                w = K.tone_wall(m, 12, 6, seed=seed)
                face = {(x, y): n for (x, y, z), (n, _) in w.b.items() if z == 0}
                self.assertFalse(chequer(face), (m.name, seed))
                body = [n for (x, y), n in face.items() if y >= 1]
                second = sum(1 for n in body if n != m.wall.full) / len(body)
                self.assertTrue(0.10 <= second <= 0.25, (m.name, seed, second))

    def test_second_tone_is_structured(self):
        """Second-tone cells sit on whole band rows or in horizontal runs of >= 2 at the wall foot - never scattered."""
        for seed in range(25):
            w = K.tone_wall(BW, 12, 6, seed=seed)
            face = {(x, y): n for (x, y, z), (n, _) in w.b.items() if z == 0}
            for (x, y), n in face.items():
                if y == 0 or n == BW.wall.full:
                    continue
                row = [face.get((k, y)) for k in range(12)]
                run = face.get((x - 1, y)) == n or face.get((x + 1, y)) == n
                self.assertTrue(row.count(n) == 12 or (run and y == 1), (seed, x, y, n))

    def test_plinth_course(self):
        w = K.tone_wall(BW, 8, 5)
        self.assertTrue(all(at(w, (x, 0, 0))[0] == BW.trim.full for x in range(8)))


class R16Machinery(unittest.TestCase):
    def test_run_joint_fitting(self):
        g = K.machinery(BW, 9)
        n = names(g)
        self.assertGreaterEqual(n[BW.dark.wall], 6)                            # pipe runs
        self.assertGreaterEqual(n[BW.dark.full], 1)                            # an elbow
        self.assertIn("lever", n)                                              # a valve
        self.assertGreaterEqual(n[BW.metal.full], 4)                           # a tank
        self.assertIn("lightning_rod", n)                                      # a vent
        self.assertGreater(thin_share(g), 0.3)
        for (x, y, z), (k, p) in g.b.items():                                  # every valve sits on a pipe
            if k == "lever":
                self.assertTrue(any(at(g, (x + a, y, z + c))[0] in (BW.dark.wall, BW.dark.full)
                                    for a, c in ((1, 0), (-1, 0), (0, -1))))

    def test_gear_wraps_the_flywheel(self):
        import brass_lab_parts as bp
        self.assertEqual(set(K.gear(BW, 2).b), set(bp.flywheel(2).b))
        self.assertNotIn("waxed_cut_copper", names(K.gear(K.GRAVEBOUND, 2)))


# ---- the parts compose ---------------------------------------------------------------------------------------------
class DemoBay(unittest.TestCase):
    """The demo bay (plinth, pilasters, windows, string course, cornice, eave over a slate roof) passes every gate the
    rulebook sets, for every material set - measured by the one gates module."""

    def test_gates(self):
        for m in SETS:
            r = G.gates(S.Build(dict(K.demo_bay(m).b)), faces=("south", "west"), bay=3)
            bad = {k: v for k, v in r.items() if not v["ok"]}
            self.assertEqual(bad, {}, (m.name, {k: v for k, v in bad.items() if k != "G7"}))

    def test_red_the_same_massing_without_the_kit(self):
        """The demo's massing as plain walls under the same roof fails detail, depth and light."""
        r = G.gates(S.Build(dict(K.demo_bay(BW, plain=True).b)), faces=("south", "west"), bay=3)
        self.assertFalse(r["G2"]["ok"])
        self.assertFalse(r["G5"]["ok"])
        self.assertFalse(r["light"]["ok"])

    def test_writes_as_a_structure(self):
        rig = K.demo_bay(BW).to_rig("demo")
        self.assertGreater(len(rig.b), 500)


if __name__ == "__main__":
    unittest.main()
