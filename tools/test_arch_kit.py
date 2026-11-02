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
import math
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
    out |= set(m.glass) | set(m.light) | {n for n in m.timber if n} | {m.render, m.glaze, m.pier}
    out |= {n for n in m.rib if n}
    out |= {n for n in getattr(m, "reveal", ()) if n} | set(getattr(m, "weather", ()))
    return out


def pic(part, view):
    """The camera's picture of a part (tools/building_gates.camera): {cell: (q, name, props)}."""
    return G.camera(build(part), view)


def sheet_piece(name, m):
    import arch_kit_sheet as KS
    return KS.piece(name, m)


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
            for f in (m.metal, m.rib):
                self.assertTrue(f.stairs.endswith("_stairs") and f.slab.endswith("_slab"), (m.name, f))
            self.assertTrue(m.glass[0].endswith("_pane"))

    def test_roof_and_wall_families_differ(self):
        """G4 by construction: a set's roof is a different material family from its walls."""
        for m in SETS:
            s = G.shares(S.Build(dict(K.demo_bay(m).b)))
            self.assertNotEqual(s["roof_family"], s["wall_family"], m.name)

    def test_brassworks_is_red_brick_grey_slate_green_copper(self):
        """r5 rewrite: the roof kit's slate courses and ridge, its gutter recoloured and (r5) its verge a dark
        deepslate edge - roof_kit's dark oak bargeboard read orange (r4 critic). Was: verge == R.SLATE's."""
        self.assertEqual(BW.wall.full, "bricks")
        self.assertIn("weathered", BW.metal.full)
        for k in ("tones", "ridge"):
            self.assertEqual(BW.roof[k], R.SLATE[k])
        self.assertTrue(all(G.tone(n) == "dark" for n in BW.roof["verge"]), BW.roof["verge"])

    def test_no_snow_trim_and_no_wood_verges(self):
        """r5 (critic r4, kit-wide): calcite / diorite trim read as snow and marshmallow - no set names them; the
        verges are a dark stone (or the set's metal), never wood (the orange bargeboards); no wood shows on the roof
        pieces from any camera."""
        for m in SETS:
            self.assertFalse([n for n in mat_ids(m) if "calcite" in n or "diorite" in n], m.name)
            self.assertFalse([n for n in m.roof["verge"] if any(w in n for w in G.WOODS)], m.name)
            for name in ("oversail", "cresting", "dormer"):
                p = sheet_piece(name, m)
                for view in ("top", "34s", "34w", "south", "west"):
                    wood = {n for q, n, _ in pic(p, view).values() if any(w in n for w in G.WOODS)}
                    self.assertEqual(wood, set(), (m.name, name, view))

    def test_every_set_has_a_dark_third_tone(self):
        """A set reads at RTS zoom only with a dark tone against its walls (r3: the Sunforged sandstone-on-quartz set
        washed out): its dark family and its roof's base tone are dark blocks."""
        dark = ("deepslate", "blackstone", "dark_oak", "nether_brick", "basalt")
        for m in SETS:
            self.assertTrue(any(k in m.dark.full for k in dark), m.name)
            self.assertTrue(any(k in m.roof["tones"][0][0] for k in dark), m.name)


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

    def test_no_unoxidised_copper(self):
        """Palette rule: copper is weathered or oxidised; fresh orange copper reads as a toy (r3 critic). A lightning
        rod IS fresh copper - in 1.20.1 it never weathers - though its id does not say so (r4: the r3 test let the
        orange rods on the hip, spire and dome tips through)."""
        for name, _, m, p in self.each():
            bad = [n for n in names(p) if n == "lightning_rod" or "copper" in n and "weathered" not in n
                   and "oxidized" not in n]
            self.assertEqual(bad, [], (name, m.name))

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

    def test_pale_lip_over_a_darker_course_with_a_weathered_slab_gutter(self):
        """r6 rewrite (was test_pale_lip_over_a_darker_course_with_a_gutter: the gutter the set's METAL stair - an
        oxidised teal one on the angels and the Gravebound): the gutter on the lip is a weathered (never oxidised) cut
        copper SLAB in every set."""
        for m in SETS:
            e = K.eave(m, 5)
            self.assertEqual(at(e, (2, 1, 1))[0], m.trim.slab)          # the lip, 1 proud, at the wall top
            self.assertEqual(at(e, (2, 1, 1))[1]["type"], "top")
            self.assertEqual(at(e, (2, 1, 0))[0], m.trim.full)          # the pale top course
            self.assertEqual(at(e, (2, 0, 0))[0], m.wall2.full)         # the darker course under it
            g = at(e, (2, 2, 1))[0]                                     # the gutter on the lip
            self.assertTrue(g.endswith("_cut_copper_slab") and "weathered" in g and "oxidized" not in g, (m.name, g))
            self.assertEqual(at(e, (2, 2, 1))[1]["type"], "bottom")


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
        """Under-verge brackets: upside-down pale stairs directly below verge slabs at both gable ends (r5: the set's
        own verge - was R.SLATE's dark oak, the orange bargeboard the critic rejected)."""
        roof = K.oversail(BW, 7, 9)
        br = [(q, v) for q, v in roof.b.items() if v[0] == BW.trim.stairs and v[1].get("half") == "top"]
        self.assertGreaterEqual(len(br), 4)
        for (x, y, z), _ in br:
            self.assertIn(x, (-1, 9))
            self.assertIn(at(roof, (x, y + 1, z))[0], BW.roof["verge"])

    def test_downpipes_drop_to_the_ground(self):
        p = self.house()
        pipes = [q for q, (n, _) in p.b.items() if n == BW.dark.wall]
        self.assertTrue(pipes)
        self.assertEqual(min(q[1] for q in pipes), 1)
        self.assertGreaterEqual(len({(q[0], q[2]) for q in pipes}), 2)


class R3Ridge(unittest.TestCase):
    def test_cresting_iron_bars_and_pale_posts_only(self):
        """r5 rewrite (was test_cresting_comb_on_the_ridge, which demanded chains and dark iron finial posts - the r4
        critic: the dark cubes between the bars read as pigeons). ONE block tall, continuous, on a pale stone coping:
        iron bars only, a pale post (the trim wall) at both ends and every 4th cell, nothing dark but the bars and no
        full block - checked on what the cameras see over the ridge."""
        for m in SETS:
            roof = K.oversail(m, 7, 9)
            ridge = {(x, z): y for (x, y, z), (n, _) in roof.b.items() if n in m.roof["ridge"]}
            c = K.cresting(roof, m)
            comb = {(x, z): n for (x, y, z), (n, _) in c.b.items() if (x, z) in ridge and y == ridge[(x, z)] + 1}
            self.assertEqual(set(comb), set(ridge), m.name)                            # continuous
            self.assertFalse([q for q in c.b if (q[0], q[2]) in ridge and q[1] > ridge[(q[0], q[2])] + 1])   # 1 tall
            self.assertEqual(set(comb.values()), {"iron_bars", m.trim.wall}, m.name)
            run = sorted(comb)
            posts = [i for i, k in enumerate(run) if comb[k] == m.trim.wall]
            self.assertEqual(posts, sorted(set(range(0, len(run), 4)) | {len(run) - 1}), m.name)
            self.assertNotEqual(G.tone(m.trim.wall), "dark", m.name)
            coping = {c.b[(x, y, z)][0] for (x, z), y in ridge.items()}
            self.assertEqual(coping, {m.trim.full}, m.name)                            # pale coping, no dark teeth
            top = max(ridge.values())
            for view in ("south", "34s", "top", "west"):
                over = {n for q, n, _ in pic(c, view).values() if q[1] > top}
                self.assertEqual(over - {"iron_bars", m.trim.wall}, set(), (m.name, view))
                self.assertFalse([n for n in over if S.glyph(n) == "#"], (m.name, view))

    def test_hip_roof_hips_in_the_other_tone(self):
        h = K.hip_roof(BW, 7)
        hips, faces = Counter(), Counter()
        for (x, y, z), (n, _) in h.b.items():
            if S.glyph(n) == "|" or y < 0 or min(x + 1, 7 - x, z + 1, 7 - z) == 0:
                continue
            fam = R.family(n, BW.roof)
            (hips if abs(x - 3) == abs(z - 3) else faces)[n if fam is None else fam] += 1
        self.assertTrue(hips and faces)
        self.assertNotEqual(hips.most_common(1)[0][0], faces.most_common(1)[0][0])

    def test_hip_roof_no_chequer(self):
        """G6: the hip roof's visible courses never chequer (r1 sheet: wood hips one cell wide made 2x2 alternations
        down both diagonals) - measured by the gates module on the roof standing on its tower."""
        for m in SETS:
            for seed in range(6):
                p = ring_box(7, 7, 5, m)
                p.place(K.hip_roof(m, 7, seed=seed), 0, 6, 0)
                self.assertLessEqual(G.roof_chequer(build(p)), G.CHEQUER_MAX, (m.name, seed))

    def test_hip_roof_hips_one_dark_line_no_timber(self):
        """Hips are a 1-wide line of the dark T1 slate down the diagonals; no timber anywhere (r2: a brown X in a brown
        frame); each face course between eave and apex one continuous tone."""
        for m in SETS:
            h = K.hip_roof(m, 7)
            self.assertFalse([n for n in names(h) if (n in m.roof["verge"] or n in m.timber)
                              and n != m.roof["ridge"][0]], m.name)
            for (x, y, z), (n, _) in h.b.items():
                if y < 0 or n == m.roof["ridge"][0] or S.glyph(n) == "|":
                    continue
                hip = abs(x - 3) == abs(z - 3)
                cap = max(abs(x - 3), abs(z - 3)) <= 1          # r4: the dark cap where the four hips meet
                self.assertEqual(R.family(n, m.roof) == 1, hip or cap or min(x + 1, 7 - x, z + 1, 7 - z) == 0,
                                 (m.name, x, y, z, n))

    def test_hip_roof_oversails_its_tower(self):
        p = ring_box(7, 7, 5)
        p.place(K.hip_roof(BW, 7), 0, 6, 0)
        got, need = G.overhang(build(p))
        self.assertEqual(got, need)


# ---- R4 / R5: dormers, roof lights ---------------------------------------------------------------------------------
class R4Dormer(unittest.TestCase):
    def test_a_three_by_two_light_in_wall_brick_under_a_gablet(self):
        """r6 rewrite (was test_an_eye_with_dark_cheeks_and_a_gablet: one tall light between DARK stone cheeks - the
        r5 critic's bunker): a 3 x 2 light (>= 6 panes) in a frame of the wall's own stone, the gablet in the roof's
        stairs, lit."""
        for m in SETS:
            d = K.dormer(m)
            n = names(d)
            self.assertGreaterEqual(n[m.glass[0]], 6, m.name)
            self.assertFalse([k for k in n if k in m.timber], m.name)
            self.assertGreaterEqual(n[m.wall.full], 6, m.name)                     # the cheeks / frame
            gablet = [v for v in d.b.values() if v[0] in m.roof["tones"][1] and v[0].endswith("_stairs")]
            self.assertGreaterEqual(len(gablet), 4, m.name)
            self.assertTrue(any(S.glyph(k) == "*" for k in n), m.name)

    def test_three_depth_planes(self):
        self.assertGreaterEqual(len([v for v in planes(K.dormer(BW)).values() if v >= 0.10]), 3)

    def test_gable_silhouette_with_eaves(self):
        """Its front reads as a little roofed house: the outline narrows row by row over the window head (r5: a 3-wide
        stair gablet over the 3-wide frame - its shoulders as wide as the cheeks, oversailing 1 forward; was >= 3 rows
        with the rake 1 past timber cheeks), and it breaks the line of the slope it sits on (r2: unreadable). r6: the
        cheeks are the wall's stone (was m.dark.full - the bunker)."""
        for m in SETS:
            d = K.dormer(m)
            panes = [q for q, (n, _) in d.b.items() if n == m.glass[0]]
            head = max(q[1] for q in panes) + 1
            width = {y: max(abs(x) for (x, yy, z) in d.b if yy == y) for y in range(head, max(q[1] for q in d.b) + 1)}
            ys = sorted(width)
            self.assertGreaterEqual(len(ys), 2, m.name)
            self.assertTrue(all(width[a] > width[b] for a, b in zip(ys, ys[1:])), (m.name, width))
            cheek = max(abs(x) for (x, y, z), (n, _) in d.b.items() if n == m.wall.full and y < head)
            self.assertEqual(width[ys[0]], cheek, m.name)
            self.assertEqual(max(z for (x, y, z) in d.b if y == ys[0]), max(z for (x, y, z) in d.b if 0 < y < head) + 1)
            r = K.dormer_on_roof(m)
            top_d = max(y for (x, y, z) in r.b if x == 0 and z == -2)
            top_r = max(y for (x, y, z) in r.b if x == 4 and z == -2)
            self.assertGreaterEqual(top_d - top_r, 2, m.name)


class R5RoofLight(unittest.TestCase):
    def test_roof_light_tinted_panels_in_a_dark_stair_frame(self):
        """r6 rewrite (replaces test_glazed_barrel: a barrel of light-grey / white panes the r5 critic read as snow on
        the roof): each light is a 2 x 3 panel of tinted glass, every cell round it a dark stair - the kerb."""
        for m in SETS:
            g = K.roof_light(m, 2)
            glass = {(x, z) for (x, y, z), (n, _) in g.b.items() if S.glyph(n) == "g"}
            self.assertEqual({n for n, _ in g.b.values() if S.glyph(n) == "g"}, {"tinted_glass"}, m.name)
            panels, left = [], set(glass)
            while left:                                                   # the glass in 4-connected panels
                todo, comp = [left.pop()], set()
                while todo:
                    c = todo.pop()
                    comp.add(c)
                    for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        k = (c[0] + a, c[1] + b)
                        if k in left:
                            left.discard(k)
                            todo.append(k)
                panels.append(comp)
            self.assertEqual(len(panels), 2, m.name)
            for comp in panels:
                xs, zs = {c[0] for c in comp}, {c[1] for c in comp}
                self.assertEqual((len(xs), len(zs), len(comp)), (2, 3, 6), (m.name, sorted(comp)))
            frame = [n for (x, y, z), (n, _) in g.b.items() if S.glyph(n) != "g"]
            self.assertTrue(frame and all(n.endswith("_stairs") and G.tone(n) == "dark" for n in frame), m.name)
            for (x, z) in glass:                                          # framed on every side
                for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    self.assertIn((x + a, 0, z + b), g.b, (m.name, x, z))

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


# ---- R6 / R7 / R8 / R9: walls, piers, openings ---------------------------------------------------------------------
class R6Cornice(unittest.TestCase):
    def test_steps_out_as_it_rises(self):
        """Pale dentils half proud, an upside-down pale stair bracket, a pale slab ledge over it: every projecting
        cell is the TRIM family (never the wall's), exactly 1 proud (r1 sheet: wall-material cornice, no shadow)."""
        for m in SETS:
            c = K.cornice(m, 8)
            self.assertNotIn(m.trim.full, m.wall)
            front = {(x, y): v for (x, y, z), v in c.b.items() if z >= 1}
            self.assertEqual(max(z for (x, y, z) in c.b), 1, m.name)
            self.assertTrue(all(v[0] in m.trim for v in front.values()), m.name)
            top = max(y for (x, y) in front)
            self.assertTrue(all(front[(x, top)][0] == m.trim.slab for x in range(-1, 9)), m.name)
            br = [front[(x, top - 1)] for x in range(-1, 9)]
            self.assertTrue(all(v[0] == m.trim.stairs and v[1]["half"] == "top" for v in br), m.name)
            teeth = [x for x in range(8) if (x, top - 2) in front]
            self.assertTrue(3 <= len(teeth) <= 5 and all(b - a >= 2 for a, b in zip(teeth, teeth[1:])), teeth)

    def test_cornice_closes_the_perimeter(self):
        """Four cornice runs on a box's wall tops under a flat roof oversail the whole perimeter by 1 (G1)."""
        w, d, h = 7, 5, 6
        p = ring_box(w, d, h)
        y = h - 2
        p.place(K.cornice(BW, w), 0, y, d - 1, 0)
        p.place(K.cornice(BW, d), 0, y, 0, 1)
        p.place(K.cornice(BW, w), w - 1, y, 0, 2)
        p.place(K.cornice(BW, d), w - 1, y, d - 1, 3)
        for x in range(w):
            for z in range(d):
                p.set(x, h + 1, z, BW.dark.slab, type="bottom")
        got, need = G.overhang(build(p))
        self.assertEqual(got, need)

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

    def test_no_bright_quartz_piers(self):
        """Piers in calcite / smooth stone, not pure quartz (r1 sheet: the Sunforged piers glared)."""
        for m in SETS:
            if m is K.GRAVEBOUND:
                continue                                                       # bone dressings are its identity
            self.assertFalse([n for n in names(K.pier(m, 5, lantern=True)) if "quartz" in n], m.name)

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
            sills = [(q, v) for q, v in w.b.items() if q[1] == 0 and q[2] >= 1]
            self.assertTrue(panes and sills, kind)
            self.assertTrue(max(q[2] for q in panes) == -1, kind)                 # panes one back
            self.assertTrue(all(v[0] == BW.trim.slab and v[1]["type"] == "top" for _, v in sills), kind)
            top = max(q[1] for q in panes)
            head = {n for (x, y, z), (n, _) in w.b.items() if y == top + 1 and z == 0}
            self.assertTrue(head & {BW.wall2.full, BW.wall2.stairs, BW.trim.stairs, BW.trim.full}, (kind, head))

    def test_no_doors_and_pane_heights(self):
        """Windows are glass, never doors or shutters (r1 sheet: open dark-oak trapdoors read as doors); openings
        2 tall (3 for the tall hall window)."""
        for kind in K.KINDS:
            w = K.window(BW, kind)
            self.assertFalse([n for n in names(w) if "door" in n], kind)
            rows = {q[1] for q, (n, _) in w.b.items() if n == BW.glass[0]}
            self.assertEqual(len(rows), 3 if kind == "tall" else 2, kind)

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
            mats.append(at(p, (min(gap) - 2, 1, -k))[0])                        # the order's body
        self.assertEqual(widths, [7, 5, 3])
        self.assertEqual(len(set(mats)), 3)

    def test_orders_contrast_with_their_pale_reveals(self):
        """Every order's body is a stone of another TONE than the set's reveal family, so its stair reveal reads against
        it (r2: pale on pale; r3: the Sunforged portal - sandstone reveals on quartz - unreadable)."""
        for m in SETS:
            p = K.portal(m, w=3, h=4, orders=3)
            for k in range(3):
                row = [x for (x, y, z), (n, _) in p.b.items() if z == -k and y == 2 and n not in S.AIR]
                gap = [x for x in range(min(row), max(row) + 1) if x not in row]
                body = at(p, (min(gap) - 2, 2, -k))[0]
                self.assertNotIn(body, m.reveal, (m.name, k, body))
                self.assertNotEqual(G.tone(body), G.tone(m.reveal.full), (m.name, k, body))
                self.assertEqual(at(p, (min(gap) - 1, 2, -k))[0], m.reveal.stairs, (m.name, k))

    def test_concentric_pale_reveals_and_hood(self):
        """Each order's jambs are lined with reveal stairs (a chamfered reveal) and its head with an upside-down reveal
        stair; a stepped hood over the outer order reads from above (r1 sheet: a square hole)."""
        for m in SETS:
            p = K.portal(m, w=3, h=4, orders=3)
            for k in range(3):
                row = [x for (x, y, z), (n, _) in p.b.items() if z == -k and y == 2 and n not in S.AIR]
                gap = [x for x in range(min(row), max(row) + 1) if x not in row]
                for x in (min(gap) - 1, max(gap) + 1):
                    self.assertEqual(at(p, (x, 2, -k))[0], m.reveal.stairs, (m.name, k, x))
                head = max(y for (x, y, z) in p.b if z == -k and x == 0 and at(p, (0, y - 1, -k))[0] in S.AIR)
                v = at(p, (0, head, -k))
                self.assertEqual((v[0], v[1].get("half")), (m.reveal.stairs, "top"), (m.name, k))
            top = max(y for (x, y, z) in p.b if z == 0 and abs(x) <= 3 and at(p, (x, y, z))[0] not in S.AIR
                      and at(p, (x, y, z))[0] != m.light[0])
            wall_top = max(y for (x, y, z), (n, _) in p.b.items() if z == -1)
            self.assertGreaterEqual(top - wall_top, 2, m.name)                   # the hood rises 2+ over the wall
            widths = [len([x for (x, y, z) in p.b if y == yy and z == 0]) for yy in range(wall_top + 1, top + 1)]
            self.assertEqual(widths, sorted(widths, reverse=True), m.name)

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
        x0 = min(x for (x, y, z) in c.b if y == 0)
        x1 = min(x for (x, y, z) in c.b if y == 5)
        self.assertLess(x0, x1)                                                # the plinth is wider
        band = [y for y in range(2, 10) if sum(1 for (x, yy, z), (n, _) in c.b.items()
                                                if yy == y and n == BW.trim.full) >= 8]
        self.assertTrue(band)                                                  # a pale band round the shaft
        top = max(y for (x, y, z) in c.b)
        self.assertEqual({n for (x, y, z), (n, _) in c.b.items() if y == top}, {BW.dark.slab})   # dark cap
        ring = [q for q, (n, _) in c.b.items() if q[1] == top - 1 and n in BW.trim]
        self.assertGreaterEqual(len(ring), 12)                                 # a pale ring under it, proud
        self.assertTrue(any(q[0] < 0 or q[2] < 0 for q in ring))

    def test_campfire_sealed_smoke_only(self):
        """The fire is inside the flue: walled on all four sides, covered by the cap (nothing to see from above or the
        side), with an air gap under the cap that opens to the outside so the smoke gets out (r1 sheet: an exposed
        campfire on top of the stack)."""
        c = K.chimney(BW, 10)
        fires = [q for q, (n, _) in c.b.items() if n == "campfire"]
        self.assertEqual(len(fires), 1)
        x, y, z = fires[0]
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = at(c, (x + a, y, z + b))[0]
            self.assertTrue(n not in S.AIR and S.glyph(n) not in "|*g", n)
        self.assertTrue(any(q[0] == x and q[2] == z and q[1] > y + 1 for q in c.b))   # covered
        self.assertIn(at(c, (x, y + 1, z))[0], S.AIR)
        vent = [(x + a, y + 1, z + b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        self.assertTrue(any(at(c, (vx + a, vy, vz + b))[0] in S.AIR for vx, vy, vz in vent
                            if at(c, (vx, vy, vz))[0] in S.AIR for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))))

    def test_flashing_skirt_where_it_leaves_the_roof(self):
        c = K.chimney(BW, 10, roof_y=4)
        skirt = [q for q, (n, p) in c.b.items() if q[1] == 4 and n == BW.trim.stairs and p.get("half") == "bottom"]
        self.assertGreaterEqual(len(skirt), 8)
        self.assertGreaterEqual(sum(1 for q, (n, _) in c.b.items() if q[1] == 5 and n == BW.metal.full), 8)

    def test_wraps_the_brass_stack(self):
        """The shaft below the crown is brass_lab_parts.stack's."""
        import brass_lab_parts as bp
        base = {q for q in bp.stack(10, 3).b if q[1] < 10}
        self.assertTrue(base <= set(K.chimney(BW, 10).b))


class R12DomeSpire(unittest.TestCase):
    def test_spire_needle_on_a_corbelled_base(self):
        """A stair-tapered needle about twice the r2 spire's height (>= 20) on a corbelled base: a tower stub, a ring
        of upside-down corbel stairs carrying a wider course, then a needle whose width never grows and steps in with
        stairs at each step, a lightning-rod tip (r2: a stepped pyramid on a cube)."""
        for m in SETS:
            sp = K.spire(m)
            width = {}
            for (x, y, z) in sp.b:
                width[y] = max(width.get(y, 0), abs(x), abs(z))
            ys = sorted(width)
            self.assertGreaterEqual(ys[-1] - ys[0] + 1, 20, m.name)
            self.assertEqual(at(sp, (0, ys[-1], 0))[0], m.rib.wall)                  # a dark finial (r3: no rods)
            corb = [y for y in ys if sum(1 for (x, yy, z), (n, p) in sp.b.items()
                                         if yy == y and n.endswith("_stairs") and p.get("half") == "top") >= 8]
            self.assertTrue(corb, m.name)
            c = corb[0]
            self.assertGreater(width[c + 1], width[c - 1], m.name)                # the course over it oversails
            needle = [y for y in ys if y > c + 2]                                  # above the pale coping
            self.assertTrue(all(width[a] >= width[b] for a, b in zip(needle, needle[1:])), (m.name, width))
            steps = [b for a, b in zip(needle, needle[1:]) if width[b] < width[a]]
            self.assertGreaterEqual(len(steps), 3, m.name)
            for y in steps:                                                   # each step-in row ends in stairs
                ring = [v for (x, yy, z), v in sp.b.items() if yy == y - 1 and max(abs(x), abs(z)) == width[y - 1]]
                self.assertTrue(ring and sum(1 for v in ring if v[0].endswith("_stairs")) >= len(ring) / 2,
                                (m.name, y))

    def dome_courses(self, m):
        d = K.dome(m, 4)
        cornice = max(y for (x, y, z), (n, _) in d.b.items() if max(abs(x), abs(z)) == 5)
        courses = {}
        for (x, y, z), v in d.b.items():
            if y > cornice and not (x == z == 0 and S.glyph(v[0]) == "|"):
                courses.setdefault(y, {})[(x, z)] = v[0]
        return d, cornice, courses

    def test_dome_continuous_meridian_ribs(self):
        """4 meridian ribs (r4: 8 on a radius-4 dome close up to every other cell near the crown - the chequer of r2
        and r3), each exactly one rib cell per course, tracked along its meridian as a connected path, one roof tone
        between them (r2: per-cell ribs chequered the dome into a 'panda ball'). r6 rewrite (was: each rib a PALE trim
        stair - the r5 critic's starfish / pinwheel of pale ribs on a dark ball): a rib is a stair of the roof's slate
        family one shade off the shell (another of its tones), a dark stone, never the trim."""
        for m in SETS:
            d, cornice, courses = self.dome_courses(m)
            ribs = {k: [] for k in range(0, 8, 2)}
            shell_fam = R.family(at(d, (1, cornice + 1, 1))[0], m.roof)
            for y in sorted(courses):
                cells = courses[y]
                pale = [(x, z) for (x, z), n in cells.items() if S.glyph(n) != "|" and n.endswith("_stairs")
                        and R.family(n, m.roof) not in (None, shell_fam)]            # the rib cells
                self.assertFalse([n for n in cells.values() if n in m.trim or G.tone(n) == "pale"], (m.name, y))
                self.assertTrue(all(G.tone(cells[q]) == "dark" for q in pale), (m.name, y))
                if all(n in m.metal for n in cells.values()):
                    continue                                                  # the metal crown / lantern base
                self.assertEqual(len(pale), 4, (m.name, y, pale))
                for (x, z) in pale:
                    a = math.degrees(math.atan2(z, x)) % 360
                    k = int(round(a / 90)) * 2 % 8
                    self.assertLessEqual(abs((a - 45 * k + 180) % 360 - 180), 22.5, (m.name, y, x, z))
                    ribs[k].append((x, y, z))
                between = {n for q, n in cells.items() if q not in pale and q != (0, 0) and S.glyph(n) not in "|*"}
                self.assertEqual(len({R.family(n, m.roof) for n in between}), 1, (m.name, y, between))
            for k, path in ribs.items():
                self.assertGreaterEqual(len(path), 3, (m.name, k))
                for a, b in zip(path, path[1:]):
                    self.assertLessEqual(max(abs(a[0] - b[0]), abs(a[1] - b[1]), abs(a[2] - b[2])), 1, (m.name, k, a, b))

    def test_dome_no_chequer_stone_drum_four_pinnacles(self):
        for m in SETS:
            d, cornice, _ = self.dome_courses(m)
            self.assertLessEqual(G.roof_chequer(build(d)), G.CHEQUER_MAX, m.name)
            drum = [n for (x, y, z), (n, _) in d.b.items() if y < cornice]
            self.assertFalse([n for n in drum if "log" in n or "planks" in n], m.name)
            self.assertGreaterEqual(sum(1 for n in drum if n in (m.wall.full, m.wall2.full)) / len(drum), 0.5)
            posts = {(x, z) for (x, y, z), (n, _) in d.b.items() if y > cornice and S.glyph(n) == "|"
                     and max(abs(x), abs(z)) >= 4}
            self.assertLessEqual(len(posts), 4, m.name)
            self.assertGreaterEqual(len(posts), 1, m.name)


# ---- R13 / R14 / R15 / R16: railings, timber, tone, machinery ------------------------------------------------------
class R13Balustrade(unittest.TestCase):
    def test_wall_balusters_between_posts_under_a_slab_coping(self):
        """r6 rewrite (was test_balusters_between_rails: a wall baluster every other cell with air between and thin
        wall newels - the r5 critic: a garden wall / bench). From the front camera: a pale base rail, solid stone POSTS
        every `every` cells and at both ends rising half a block through the coping, a run of stone wall BALUSTERS in
        every cell between them, a slab COPING over the balusters."""
        for m in SETS:
            b = K.balustrade(m, 9, every=4)
            p = {k: n for k, (q, n, _) in pic(b, "south").items()}
            self.assertTrue(all(p[(x, 0)] == m.trim.full for x in range(9)), m.name)
            posts = [x for x in range(9) if p[(x, 1)] == m.trim.full and p[(x, 2)] == m.trim.full]
            self.assertEqual(posts, [0, 4, 8], m.name)
            for x in range(9):
                if x in posts:
                    continue
                self.assertEqual(p[(x, 1)], m.trim.wall, (m.name, x))               # balusters: wall blocks
                self.assertEqual(S.glyph(p[(x, 1)]), "|", (m.name, x))
                self.assertEqual(p[(x, 2)], m.trim.slab, (m.name, x))               # the coping: slabs
                self.assertEqual(at(b, (x, 2, 0))[1].get("type"), "bottom", (m.name, x))
            r = G.camera_chequer(build(b), ("south", "34s", "top"))
            self.assertEqual(r, {k: 0 for k in r}, m.name)

    def test_bars_and_solid_variants(self):
        self.assertIn("iron_bars", names(K.balustrade(BW, 9, panel="bars")))
        b = K.balustrade(BW, 9, panel="solid")
        self.assertNotIn("iron_bars", names(b))
        self.assertTrue(any(n == BW.wall.full for n in names(b)))


class R14HalfTimber(unittest.TestCase):
    def test_frame_carries_infill_set_back_jetty(self):
        t = K.half_timber(BW, 10)
        posts = {x for (x, y, z), (n, p) in t.b.items() if n == BW.timber.log and p.get("axis") == "y"}
        self.assertTrue({0, 9} <= posts)
        self.assertTrue(all(b - a <= 5 for a, b in zip(sorted(posts), sorted(posts)[1:])))
        # r6 rewrite (was: a log PLATE along the jetty, axis x, >= 10 - with the log head plate the r5 critic's
        # 2-wide dark oak bands): the jetty beam is a dark trapdoor BRESSUMMER, a thin plate under the posts
        plate = [x for (x, y, z), (n, p) in t.b.items() if n == BW.timber.trapdoor and y == 4]
        self.assertEqual(sorted(plate), list(range(10)))
        self.assertFalse([q for q, (n, p) in t.b.items() if n == BW.timber.log and p.get("axis") == "x"])
        post_z = max(z for (x, y, z), (n, p) in t.b.items() if n == BW.timber.log and p.get("axis") == "y")
        infill_z = max(z for (x, y, z), (n, _) in t.b.items() if n == BW.render)
        self.assertLess(infill_z, post_z)                                      # infill behind the frame
        lower = max(z for (x, y, z) in t.b if y == 1)
        self.assertEqual(post_z - lower, 1)                                    # the upper storey oversails by 1
        braces = [v for v in t.b.values() if v[0] == BW.timber.stairs]
        self.assertGreaterEqual(len(braces), 2)

    def test_timber_is_the_frame_not_the_fill(self):
        """Seen from the front, the upper storey is mostly pale render with glass, the timber only a frame; from above
        it is not a timber lid; the brick base shows below the jetty (r1 sheet: a dark crate). r6 rewrite (was timber
        <= 30 % of the upper PANEL - the r2 post spacing of 4; the r6 posts stand 3 apart, so the 30 % is measured on
        the whole face in test_half_timber_thin_posts_three_apart_on_a_trapdoor_bressummer): 1-wide posts 3 apart are
        at most 40 % of the panel, and its pale infill and glass outnumber the timber."""
        for m in SETS:
            t = K.half_timber(m, 10)
            face = {}
            for (x, y, z), (n, _) in t.b.items():
                if (x, y) not in face or z > face[(x, y)][0]:
                    face[(x, y)] = (z, n)
            up = Counter(S.glyph(n) if S.glyph(n) == "g" else ("t" if n in m.timber else n)
                         for (x, y), (z, n) in face.items() if 5 <= y <= 7)
            tot = sum(up.values())
            self.assertLessEqual(up["t"] / tot, 0.40, (m.name, up))            # 1-wide posts, 1 column in 3
            pale = sum(v for k, v in up.items() if k == "g" or k != "t" and G.tone(k) == "pale")
            self.assertGreater(pale, up["t"], (m.name, up))                    # pale infill dominant
            self.assertGreater(up["g"], 0, m.name)
            low = Counter(n for (x, y), (z, n) in face.items() if 1 <= y <= 3)
            self.assertGreaterEqual(low[m.wall.full] / sum(low.values()), 0.6, (m.name, low))
            topc = {}
            for (x, y, z), (n, _) in t.b.items():
                if (x, z) not in topc or y > topc[(x, z)][0]:
                    topc[(x, z)] = (y, n)
            timber_top = sum(1 for y, n in topc.values() if n in m.timber)
            self.assertLessEqual(timber_top / len(topc), 0.5, m.name)


def chequer(grid):
    """Any 2x2 window that alternates (a b / b a, a != b)?"""
    for (x, y), a in grid.items():
        b = grid.get((x + 1, y))
        if b is not None and b != a and grid.get((x, y + 1)) == b and grid.get((x + 1, y + 1)) == a:
            return True
    return False


class R15ToneWall(unittest.TestCase):
    def test_never_a_chequer_and_never_scattered(self):
        """The face never chequers, and every tone sits in runs of >= 2 along its row (never a lone cell)."""
        for m in SETS:
            for seed in range(25):
                w = K.tone_wall(m, 12, 7, seed=seed)
                face = {(x, y): n for (x, y, z), (n, _) in w.b.items() if z == 0 and n not in S.AIR}
                self.assertFalse(chequer(face), (m.name, seed))
                for (x, y), n in face.items():
                    self.assertTrue(face.get((x - 1, y)) == n or face.get((x + 1, y)) == n, (m.name, seed, x, y))

    def test_water_logic(self):
        """Darker where the water goes: in every column the tone never gets paler going down, the dark plinth at y 0,
        the clean wall at the top (r2: pale patches mid-wall)."""
        for m in SETS:
            stain, moss = m.weather
            order = [m.wall.full, stain, moss, m.dark.full]
            for seed in range(25):
                w = K.tone_wall(m, 12, 7, seed=seed)
                for x in range(12):
                    col = [order.index(at(w, (x, y, 0))[0]) for y in range(6, -1, -1)]
                    self.assertEqual(col, sorted(col), (m.name, seed, x, col))
                    self.assertEqual(col[0], 0, (m.name, seed, x))
                    self.assertEqual(col[-1], 3, (m.name, seed, x))


class R16Machinery(unittest.TestCase):
    def test_run_joint_fitting(self):
        g = K.machinery(BW, 9)
        n = names(g)
        self.assertGreaterEqual(n[BW.dark.wall], 4)                            # pipe runs
        self.assertGreaterEqual(n[BW.dark.full], 1)                            # an elbow
        self.assertIn("lever", n)                                              # a valve
        self.assertIn("grindstone", n)                                         # a pump
        vent = [q for q, (k, _) in g.b.items() if k == BW.rib.wall and q[0] == 8]
        self.assertTrue(vent and at(g, (8, vent[0][1] - 1, vent[0][2]))[0] == BW.dark.full)   # a vent on the elbow
        self.assertGreaterEqual(min(q[2] for q in g.b), 1)                     # all of it against the wall
        self.assertTrue(all(at(g, (x, y, 1))[0] == BW.trim.full for x in range(9) for y in range(5)))   # a pale bed
        for (x, y, z), (k, p) in g.b.items():                                  # every valve sits on a pipe
            if k == "lever":
                self.assertTrue(any(at(g, (x + a, y, z + c))[0] in (BW.dark.wall, BW.dark.full)
                                    for a, c in ((1, 0), (-1, 0), (0, -1), (0, 1))))
                self.assertTrue(any(at(g, (x, y + b, z))[0] in (BW.dark.wall, BW.dark.full) for b in (1, -1)))

    def test_band_reads_at_rts_zoom(self):
        """The band covers >= 40 % of its wall strip (x 0..L-1, y 0..4) seen from the front - a machine, not a few
        sticks on a wall (r2: unreadable at RTS zoom)."""
        for m in SETS:
            g = K.machinery(m, 9)
            cover = {(x, y) for (x, y, z) in g.b if z >= 1 and 0 <= x < 9 and 0 <= y <= 4}
            self.assertGreaterEqual(len(cover) / 45, 0.4, (m.name, len(cover)))



# ---- r4: what the CAMERA sees (r3 critic: 'still reads as isolated samples'; the dome failed two rounds with green
# intent tests). Each test looks at the part's picture from the photo cameras, never at the generator's bookkeeping.
class CameraReads(unittest.TestCase):
    def test_dome_slate_ribs_no_pale_above_the_drum(self):
        """r6 rewrite (was test_dome_one_dark_shell_ribs_single_pale_stair_lines, which demanded PALE stair ribs - the
        r5 critic's starfish / pinwheel). The dome seen from above, the sides and the 3/4 still: no a-b-a alternation
        anywhere (r2, r3: a chequer); above the drum cornice NO pale cell at all - every visible cell is the shell (the
        roof's base slate, stairs or slabs), a rib (a stair of another slate tone) or the iron finial / pinnacles."""
        for m in SETS:
            d = K.dome(m, 4)
            cornice = max(y for (x, y, z), (n, _) in d.b.items() if max(abs(x), abs(z)) == 5)
            r = G.camera_chequer(build(d), ("top", "south", "west", "34s", "34w"))
            self.assertEqual(r, {k: 0 for k in r}, m.name)
            for view in ("top", "34s", "south"):
                seen = Counter()
                for k, (q, n, _) in pic(d, view).items():
                    if q[1] <= cornice:
                        continue
                    self.assertFalse(n in m.trim or G.tone(n) == "pale", (m.name, view, q, n))
                    if R.family(n, m.roof) not in (None, 0):
                        self.assertTrue(n.endswith("_stairs") and G.tone(n) == "dark", (m.name, view, q, n))
                        seen["rib"] += 1
                    elif R.family(n, m.roof) == 0:
                        self.assertTrue(n.endswith(("_stairs", "_slab")), (m.name, view, q, n))
                        seen["shell"] += 1
                    else:
                        self.assertTrue(n in m.metal or S.glyph(n) == "|", (m.name, view, q, n))
                        seen["crown"] += max(abs(q[0]), abs(q[2])) <= 1               # not the cornice pinnacles
                self.assertGreater(seen["shell"], 2 * seen["rib"], (m.name, view, seen))
                self.assertLessEqual(seen["crown"], 9, (m.name, view, seen))

    def test_roof_light_no_pale_from_the_top_camera(self):
        """r6 rewrite (was test_roof_light_glass_rows_under_thin_dark_bars: a barrel of light-grey panes under iron
        bars - the r5 critic: pale squares read as snow). On its roof, from the top and 3/4 cameras, the roof light is
        tinted glass and its dark stair kerb only: no pale cell, >= 30 % glass, never a chequer."""
        for m in SETS:
            g = K.roof_light(m, 2)
            p = sheet_piece("roof_light", m)
            for view in ("top", "34s"):
                seen = pic(p, view)
                glass = [k for k, (q, n, _) in seen.items() if S.glyph(n) == "g"]
                u0, u1 = min(k[0] for k in glass) - 1, max(k[0] for k in glass) + 1
                v0, v1 = min(k[1] for k in glass) - 1, max(k[1] for k in glass) + 1
                cells = [n for k, (q, n, _) in seen.items() if u0 <= k[0] <= u1 and v0 <= k[1] <= v1]
                self.assertFalse([n for n in cells if G.tone(n) == "pale" or n in m.trim], (m.name, view))
                self.assertGreaterEqual(len(glass) / len(cells), 0.3, (m.name, view))
            r = G.camera_chequer(build(g), ("top", "34s"))
            self.assertEqual(r, {k: 0 for k in r}, m.name)

    def test_machinery_dark_parts_read_against_a_pale_ground(self):
        """The machinery band from the front: dark iron against a pale ground - most dark cells touch pale, and the
        band is not mostly dark (r3: a deepslate blob)."""
        for m in SETS:
            p = {k: n for k, (q, n, _) in pic(K.machinery(m, 9), "south").items()}
            dark = [k for k, n in p.items() if G.tone(n) == "dark"]
            self.assertLessEqual(len(dark) / len(p), 0.5, m.name)
            edged = [k for k in dark if any(G.tone(p.get((k[0] + a, k[1] + b), "air")) == "pale"
                                            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
            self.assertGreaterEqual(len(edged) / len(dark), 0.6, (m.name, len(edged), len(dark)))
            r = G.camera_chequer(build(sheet_piece("machinery", m)), ("south", "34s", "top"))
            self.assertEqual(r, {k: 0 for k in r}, m.name)

    def test_dormer_gable_face_with_a_lit_window_proud_of_the_slope(self):
        """The dormer on its roof faces the camera DOWN the slope (r3: it looked up the roof, a cut-out): the main slope
        falls toward the front, the 3/4 camera sees its glass and its dark frame, and it stands >= 4 proud of the slope
        where it meets it; a light behind the glass. r5 rewrite: was >= 3 glass cells and a second-tone (pale) face at
        3/4 - the r4 white-blue box; the lintel hides the top panes at 45 degrees, so >= 2. r6 rewrite: its frame the
        wall's brick seen over the slope (was the dark stone frame - the r5 bunker)."""
        for m in SETS:
            r = K.dormer_on_roof(m)
            col = {}
            for (x, y, z) in r.b:
                if x == 4:
                    col[z] = max(col.get(z, -99), y)
            hs = [col[z] for z in sorted(col)]
            self.assertEqual(hs, sorted(hs, reverse=True), (m.name, hs))
            p = pic(r, "34s")
            glass = [k for k, (q, n, _) in p.items() if n == m.glass[0]]
            self.assertGreaterEqual(len(glass), 2, m.name)
            face = [k for k, (q, n, _) in p.items() if n == m.wall.full and abs(q[0]) <= 2]
            self.assertGreaterEqual(len(face), 3, m.name)                 # r6: its frame the wall brick (was dark)
            panes = [q for q, (n, _) in r.b.items() if n == m.glass[0]]
            zf = max(q[2] for q in panes)
            top = max(y for (x, y, z) in r.b if abs(x) <= 1 and z <= zf + 1)
            self.assertGreaterEqual(top - col.get(zf, col[max(col)]), 4, m.name)
            self.assertTrue(any(at(r, (x, y, z - k))[0] == m.light[0] for (x, y, z) in panes for k in (1, 2)), m.name)

    def test_tone_wall_three_tones_by_row_a_gradient(self):
        """R15 seen from the front: the wall darkens row by row toward the foot - the clean wall on top, the stain
        through the middle, moss and the dark at the foot - a gradient over >= 4 distinct rows, never one band (r3:
        one black band), never an alternation."""
        for m in SETS:
            stain, moss = m.weather
            level = {m.dark.full: 0, moss: 1, stain: 2, m.wall.full: 3}
            for seed in range(25):
                w = K.tone_wall(m, 12, 7, seed=seed)
                face = {k: n for k, (q, n, _) in pic(w, "south").items() if q[2] == 0}
                self.assertFalse(G.alternations(face), (m.name, seed))
                self.assertEqual(set(face.values()) - set(level), set(), (m.name, seed))
                rows = [sum(level[face[(x, y)]] for x in range(12)) / 12 for y in range(7)]
                self.assertEqual(rows, sorted(rows), (m.name, seed, rows))            # never darker higher up
                self.assertGreaterEqual(len(set(round(v, 2) for v in rows)), 4, (m.name, seed, rows))
                share = Counter(level[n] for n in face.values())
                tot = sum(share.values())
                self.assertGreaterEqual((share[0] + share[1]) / tot, 0.12, (m.name, seed, share))   # the foot
                self.assertGreaterEqual(share[2] / tot, 0.2, (m.name, seed, share))                  # the stain
                self.assertGreaterEqual(share[3] / tot, 0.15, (m.name, seed, share))                 # clean wall
                self.assertGreaterEqual(share[3], share[2], (m.name, seed, share))   # r6: pale top over the stain
                self.assertEqual(rows[-1], 3, (m.name, seed))                                        # clean top

    def test_hip_roof_stair_slopes_half_block_risers(self):
        """Every slope cell of the hip roof is a stair (half-block risers) - no full-block or slab rings seen from any
        camera (r3: a ziggurat of rings) - and its hips stay one dark line from above."""
        for m in SETS:
            h = K.hip_roof(m, 7)
            for (x, y, z), (n, _) in h.b.items():
                if y >= 0 and n != m.roof["ridge"][0] and S.glyph(n) != "|":
                    self.assertTrue(n.endswith("_stairs"), (m.name, x, y, z, n))
            p = ring_box(7, 7, 5, m)
            p.place(h, 0, 6, 0)
            for view in ("south", "34s", "top"):
                for k, (q, n, _) in pic(p, view).items():
                    if q[1] >= 6 and n != m.roof["ridge"][0] and S.glyph(n) != "|":
                        self.assertTrue(n.endswith("_stairs"), (m.name, view, q, n))
            top = {k: n for k, (q, n, _) in pic(h, "top").items()}
            dark = {k for k, n in top.items() if R.family(n, m.roof) == 1}
            ring = {k for k in dark if min(k[0] + 1, 7 - k[0], k[1] + 1, 7 - k[1]) == 0}
            for (x, z) in dark - ring:
                self.assertTrue(abs(x - 3) == abs(z - 3) or max(abs(x - 3), abs(z - 3)) <= 1, (m.name, x, z))

    def test_spire_stone_drum_and_a_stair_taper(self):
        """The spire stands on a ROUND stone drum (wall stone, >= 4 rows, its corners cut), and from the coping up the
        top camera sees only stairs (each stage steps in by a stair ring, no flat cake tier) and the dark finial; from
        the side its outline never steps in by more than 1 (r3: a wedding cake at 3/4)."""
        for m in SETS:
            sp = K.spire(m)
            corb = min(y for y in {q[1] for q in sp.b} if sum(1 for (x, yy, z), (n, pr) in sp.b.items() if yy == y
                       and n.endswith("_stairs") and pr.get("half") == "top") >= 8)
            drum = [y for y in range(corb) if (3, 0, 0) in {(abs(x), yy - y, abs(z)) for (x, yy, z) in sp.b if yy == y}
                    and at(sp, (3, y, 3))[0] in S.AIR]
            self.assertGreaterEqual(len(drum), 4, m.name)
            body = [at(sp, (x, y, z))[0] for y in drum for (x, z) in ((3, 0), (-3, 0), (0, 3), (0, -3), (3, 1))]
            self.assertGreaterEqual(sum(1 for n in body if n in (m.wall.full, m.wall2.full, m.trim.full)) / len(body),
                                    0.5, m.name)
            for k, (q, n, _) in pic(sp, "top").items():
                if q[1] > corb:
                    self.assertTrue(n.endswith("_stairs") or S.glyph(n) == "|", (m.name, q, n))
            rows = {}
            for (u, v), (q, n, _) in pic(sp, "south").items():
                rows[v] = max(rows.get(v, 0), abs(u))
            up = [rows[v] for v in sorted(rows) if v > corb + 1]
            self.assertTrue(all(a - b <= 1 for a, b in zip(up, up[1:])), (m.name, up))
            tip = max(q[1] for q in sp.b)
            self.assertEqual(G.tone(at(sp, (0, tip, 0))[0]), "dark", m.name)

    def test_every_set_reads_dark_against_pale_in_posts_reveals_and_base(self):
        """A set's REVEAL family (base course, pier strips, opening reveals) and its half-timber posts contrast in tone
        with the wall and the render they sit on, seen by the camera (r3: the Sunforged walls flat beige, its dark tone
        only in the roof; its half-timber and portal unreadable)."""
        for m in SETS:
            self.assertNotEqual(G.tone(m.reveal.full), G.tone(m.wall.full), m.name)
            ht = {k: n for k, (q, n, _) in pic(K.half_timber(m, 10), "south").items()}
            posts = {n for n in ht.values() if n in m.timber}
            self.assertTrue(posts, m.name)
            self.assertTrue(all(G.tone(n) != G.tone(m.render) for n in posts), (m.name, posts, m.render))
            self.assertNotEqual(G.tone(ht[(0, 0)]), G.tone(m.wall.full), m.name)               # the base course
            pier = {k: n for k, (q, n, _) in pic(K.pier(m, 5), "south").items()}
            self.assertTrue(all(G.tone(pier[(0, y)]) != G.tone(m.wall.full) for y in range(5)), (m.name, pier))
            p = {k: n for k, (q, n, _) in pic(K.portal(m), "south").items()}
            jambs = [k for k, n in p.items() if n == m.reveal.stairs]
            self.assertGreaterEqual(len(jambs), 12, m.name)
            for (x, y) in jambs:                                         # every reveal reads against its order body
                side = [p.get((x + a, y)) for a in (-1, 1)]
                body = [n for n in side if n and n not in m.reveal and n not in m.timber and S.glyph(n) == "#"]
                self.assertTrue(all(G.tone(n) != G.tone(m.reveal.full) for n in body), (m.name, x, y, body))

    def test_windows_panes_one_back_on_a_pale_sill(self):
        """Every glazed wall opening: the pane sits one behind the face of the wall round it, with air in front (the
        reveal), and a pale slab sill under the opening (r3: black voids with no sill)."""
        for name in ("window_tall", "window_medium", "window_small", "bay_tall", "half_timber", "dormer"):
            for m in SETS:
                p = sheet_piece(name, m)
                panes = [q for q, (n, _) in p.b.items() if n == m.glass[0]]
                self.assertTrue(panes, (name, m.name))
                for (x, y, z) in panes:
                    self.assertIn(at(p, (x, y, z + 1))[0], S.AIR, (name, m.name, x, y, z))
                    beside = [zz for (xx, yy, zz), (n, _) in p.b.items() if yy == y and abs(xx - x) <= 3
                              and n not in S.AIR and n != m.glass[0] and S.glyph(n) == "#"]
                    self.assertGreaterEqual(max(beside), z + 1, (name, m.name, x, y, z))
                for x in {q[0] for q in panes}:
                    low = min(q for q in panes if q[0] == x)
                    sill = [at(p, (x, low[1] - 1, low[2] + k)) for k in (1, 2)]
                    self.assertTrue(any(n == m.trim.slab for n, _ in sill), (name, m.name, x, sill))

    def test_no_lightning_rods_iron_finials(self):
        """The hip, spire and dome tips end in a dark iron finial, never an orange lightning rod (r3 critic)."""
        for fn in (lambda m: K.hip_roof(m, 7), K.spire, lambda m: K.dome(m, 4)):
            for m in SETS:
                p = fn(m)
                self.assertNotIn("lightning_rod", names(p), m.name)
                tip = max(q[1] for q in p.b)
                self.assertEqual({G.tone(n) for (x, y, z), (n, _) in p.b.items() if y == tip}, {"dark"}, m.name)

    # ---- r5 (critic r4): what still read as a toy, tested on the camera picture -----------------------------------
    def test_spire_one_dark_stair_taper_on_an_octagon_copper_only_at_the_tip(self):
        """r5: one straight taper of DARK stair courses on an octagonal plan, copper only in the top 3 blocks under an
        iron finial (r4: square stepped stages of full oxidised copper with pale slab rings - a Christmas tree)."""
        for m in SETS:
            sp = K.spire(m)
            tip = max(q[1] for q in sp.b)
            copper = [q for q, (n, _) in sp.b.items() if n in m.metal and S.glyph(n) == "#"]
            self.assertTrue(all(q[1] >= tip - 3 for q in copper), (m.name, sorted(copper)[:3]))
            coping = max(y for (x, y, z) in sp.b if max(abs(x), abs(z)) == 4)
            rows = {}
            for (u, v), (q, n, _) in pic(sp, "south").items():
                rows.setdefault(q[1], []).append((abs(u), n))
            needle = [y for y in sorted(rows) if coping < y < tip - 3]
            self.assertGreaterEqual(len(needle), 9, m.name)
            width = [max(a for a, _ in rows[y]) for y in needle]
            self.assertEqual(width, sorted(width, reverse=True), (m.name, width))          # never grows
            self.assertTrue(all(a - b <= 1 for a, b in zip(width, width[1:])), (m.name, width))
            runs = Counter(width)
            self.assertLessEqual(max(runs.values()) - min(runs.values()), 1, (m.name, width))   # one straight taper
            for view in ("south", "34s", "west"):
                for k, (q, n, _) in pic(sp, view).items():
                    if coping < q[1] < tip - 3:
                        self.assertTrue(n.endswith("_stairs") and G.tone(n) == "dark", (m.name, view, q, n))
            for y in needle:                                                    # an octagon: corners cut
                r = max(abs(x) for (x, yy, z) in sp.b if yy == y)
                if r >= 2:
                    self.assertIn((r, y, 0), sp.b, (m.name, y))
                    self.assertNotIn((r, y, r), sp.b, (m.name, y))
            self.assertEqual(G.camera_chequer(build(sp), ("south", "34s", "top")), {"south": 0, "34s": 0, "top": 0})

    def test_gear_a_solid_dark_disc_rim_teeth_bar_spokes(self):
        """r6 rewrite (replaces test_gear_a_hollow_ring_of_dark_stairs_on_a_wall, the r5 hollow ring on a pale disc -
        the r5 critic: a broken dark horseshoe with pale blobs). From the facing camera: a FILLED round silhouette -
        every row one unbroken run, symmetric, no pale cell and no hole - of a dark disc, single stair TEETH on its rim
        (each tooth one cell, touching the disc, never touching another tooth) and iron-bar SPOKES on its face."""
        for m in SETS:
            g = K.gear(m)
            p = {k: n for k, (q, n, _) in pic(g, "south").items()}
            self.assertFalse([n for n in p.values() if G.tone(n) == "pale" or n in m.trim], m.name)
            rows = {}
            for (u, v) in p:
                rows.setdefault(v, []).append(u)
            for v, us in rows.items():
                self.assertEqual(sorted(us), list(range(min(us), max(us) + 1)), (m.name, v))   # filled, no hole
                self.assertEqual(min(us), -max(us), (m.name, v))                              # round, centred
            widths = [max(rows[v]) for v in sorted(rows)]
            self.assertGreaterEqual(len(widths), 7, m.name)
            teeth = [k for k, n in p.items() if n.endswith("_stairs")]
            body = {k for k in p if k not in teeth}
            self.assertGreaterEqual(len(teeth), 8, m.name)
            for (u, v) in teeth:
                nb = [(u + a, v + b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))]
                self.assertTrue(any(k in body for k in nb), (m.name, u, v))
                self.assertFalse([k for k in nb if k in teeth], (m.name, u, v))
                self.assertGreaterEqual(math.hypot(u, v), max(math.hypot(*k) for k in body if k[0] * u >= 0
                                                              and k[1] * v >= 0) - 1, (m.name, u, v))   # on the rim
            self.assertTrue(all(G.tone(p[k]) == "dark" or p[k] in m.metal for k in body), m.name)
            spokes = [k for k in body if p[k] == "iron_bars"]
            self.assertGreaterEqual(len(spokes), 8, m.name)
            self.assertGreaterEqual(len({(k[0] > 0) - (k[0] < 0) + 3 * ((k[1] > 0) - (k[1] < 0)) for k in spokes}), 4)
            r = G.camera_chequer(build(g), ("south", "34s", "top"))
            self.assertEqual(r, {k: 0 for k in r}, m.name)

    def test_roof_chimney_brick_stack_flush_band_trapdoor_cap(self):
        """r5 rewrite (replaces test_roof_chimney_a_slender_stack, which demanded the 1-wide stack under a projecting
        pale slab ring - the r4 critic's clover / snow cap): a 2 x 1 stack of the wall stone, the pale band a FULL
        flush course, a trapdoor cap; nothing projects beyond the stack's footprint, from any camera."""
        for m in SETS:
            c = K.roof_chimney(m)
            fp = {(x, z) for (x, y, z) in c.b}
            self.assertEqual(len(fp), 2, (m.name, fp))
            self.assertEqual(len({x for x, z in fp}) * len({z for x, z in fp}), 2, m.name)
            ys = sorted({q[1] for q in c.b})
            top = ys[-1]
            self.assertTrue(all("trapdoor" in at(c, (x, top, z))[0] for x, z in fp), m.name)
            pale = [y for y in ys if any(G.tone(at(c, (x, y, z))[0]) == "pale" or at(c, (x, y, z))[0] in m.trim
                                         for x, z in fp)]
            self.assertEqual(len(pale), 1, (m.name, pale))
            self.assertTrue(all(at(c, (x, pale[0], z))[0] == m.trim.full for x, z in fp), m.name)   # full, flush
            body = [y for y in ys if y not in pale and y != top]
            self.assertGreaterEqual(len(body), 4, m.name)
            self.assertNotEqual(G.tone(at(c, (min(fp)[0], body[0], min(fp)[1]))[0]), "pale", m.name)
            for view in ("south", "west", "34s"):                                # no row wider than the stack
                rows = Counter(q[1] for q, n, _ in pic(c, view).values())
                self.assertLessEqual(max(rows.values()), 1 if view == "west" else 2, (m.name, view, rows))
            self.assertEqual(len(pic(c, "top")), 2, m.name)
            self.assertNotIn("campfire", names(c), m.name)

    def test_dormer_own_stair_gablet_and_a_brick_frame(self):
        """r6 rewrite (was test_dormer_own_stair_gablet_and_a_dark_frame: one tall light in a DARK stone frame - the r5
        critic's bunker). Seen from the front: >= 6 glass cells, a 3 x 2 light, framed left, right and above by the
        wall's own brick, under the dormer's own stair gablet - an outline whose apex stands above its stair shoulders,
        the shoulders as wide as the frame and oversailing its face by 1 forward; nothing pale on its face but the
        sill."""
        for m in SETS:
            d = K.dormer(m)
            p = {k: (q, n) for k, (q, n, _) in pic(d, "south").items()}
            glass = [k for k, (q, n) in p.items() if n == m.glass[0]]
            self.assertGreaterEqual(len(glass), 6, m.name)
            self.assertEqual((len({u for u, v in glass}), len({v for u, v in glass})), (3, 2), m.name)
            for (u, v) in glass:
                for k in ((u - 1, v), (u + 1, v), (u, v + 1)):
                    n = p[k][1] if k in p else "air"
                    self.assertTrue(k in glass or n == m.wall.full, (m.name, k, n))
            head = max(v for u, v in glass) + 1
            width = {v: max(abs(u) for (u, vv) in p if vv == v) for v in range(head, max(v for _, v in p) + 1)}
            ys = sorted(width)
            self.assertGreaterEqual(len(ys), 2, (m.name, width))
            self.assertTrue(all(width[a] > width[b] for a, b in zip(ys, ys[1:])), (m.name, width))
            frame = max(abs(u) for (u, v) in p if v < head)
            self.assertEqual(width[ys[0]], frame, (m.name, width, frame))                # shoulders as wide as it
            self.assertTrue(all(p[k][1].endswith("_stairs") for k in p if k[1] == ys[0] and abs(k[0]) == width[ys[0]]))
            face_z = max(q[2] for k, (q, n) in p.items() if k[1] < head and n == m.wall.full)
            shoulder_z = max(q[2] for k, (q, n) in p.items() if k[1] == ys[0])
            self.assertEqual(shoulder_z - face_z, 1, m.name)                            # and 1 forward
            pale = [k for k, (q, n) in p.items() if (G.tone(n) == "pale" or n in m.trim or n == m.render)
                    and n != m.wall.full]                                         # the frame is the wall, even pale
            self.assertTrue(all(n == m.trim.slab for k, (q, n) in p.items() if k in pale), (m.name, pale))

    def test_tone_wall_ragged_edges_never_a_chequer(self):
        """r5 (r4 critic: two hard bands): each tone edge wanders over >= 3 rows in runs of >= 2 columns (~25 % of the
        neighbour tone dithered into each band, structured), and camera_chequer stays clean from every camera."""
        for m in SETS:
            stain, moss = m.weather
            for seed in range(25):
                w = K.tone_wall(m, 12, 7, seed=seed)
                clean = [min(y for y in range(7) if at(w, (x, y, 0))[0] == m.wall.full) for x in range(12)]
                foot = [min(y for y in range(1, 8) if at(w, (x, y, 0))[0] != moss) for x in range(12)]
                for edge in (clean, foot):
                    self.assertGreaterEqual(len(set(edge)), 3, (m.name, seed, edge))
                    self.assertGreaterEqual(max(edge) - min(edge), 2, (m.name, seed, edge))
                r = G.camera_chequer(build(w), ("south", "34s", "top", "west"))
                self.assertEqual(r, {k: 0 for k in r}, (m.name, seed))

    def test_dome_a_single_iron_finial_no_copper_crown(self):
        """r5 (r4 critic: the green clover crown): over the top shell course stands ONE dark iron finial block - no
        metal crown on the dome, from any camera."""
        for m in SETS:
            d = K.dome(m, 4)
            cornice = max(y for (x, y, z), (n, _) in d.b.items() if max(abs(x), abs(z)) == 5)
            self.assertFalse([q for q, (n, _) in d.b.items() if n in m.metal and q[1] > cornice], m.name)
            tip = max(q[1] for q in d.b)
            self.assertEqual([q for q in d.b if q[1] == tip], [(0, tip, 0)], m.name)
            self.assertEqual(at(d, (0, tip, 0))[0], m.rib.wall, m.name)
            self.assertTrue(at(d, (0, tip - 1, 0))[0].endswith("_stairs"), m.name)

    # ---- r6 (critic r5): the last part-level fixes, each on the camera picture ----------------------------------
    def test_half_timber_thin_posts_three_apart_on_a_trapdoor_bressummer(self):
        """r6 (r5 critic: 2-wide dark oak bands, ~60 % wood - a storefront): from the front and 3/4 cameras timber is
        <= 30 % of the whole face; in the upper storey every post is 1 wide and they stand 3 apart; the jetty is a thin
        trapdoor bressummer, the infill pale."""
        for m in SETS:
            t = K.half_timber(m, 10)
            for view in ("south", "34s"):
                p = {k: n for k, (q, n, _) in pic(t, view).items()}
                wood = [k for k, n in p.items() if n in m.timber]
                self.assertLessEqual(len(wood) / len(p), 0.30, (m.name, view, len(wood), len(p)))
            p = {k: (q, n) for k, (q, n, _) in pic(t, "south").items()}
            for v in (5, 6, 7):
                row = [u for u in range(10) if p[(u, v)][1] in m.timber]
                self.assertFalse([u for u in row if u + 1 in row], (m.name, v, row))         # 1 wide
                self.assertEqual([b - a for a, b in zip(row, row[1:])], [3] * (len(row) - 1), (m.name, v, row))
                self.assertTrue(all(G.tone(p[(u, v)][1]) == "pale" for u in range(10) if u not in row
                                    and S.glyph(p[(u, v)][1]) != "g"), (m.name, v))
            jetty = {p[(u, 4)][1] for u in range(10)}
            self.assertEqual(jetty, {m.timber.trapdoor}, (m.name, jetty))

    def test_spire_one_stair_orientation_per_face_no_full_fill(self):
        """r6 (r5 critic: fill visible between the stairs; the faces mix stair orientations): from the coping to the
        copper tip every cell the side, 3/4 and top cameras see is a stair; every tread the TOP camera sees is a RIM
        stair of its course (r5: the plus-shaped top stage left the r2 stage's inner fill showing on the step, its
        stairs turned east-west among north-south treads); on each face of a course (the four axis faces and the cut
        corners) the stairs all face one way."""
        for m in SETS:
            sp = K.spire(m)
            tip = max(q[1] for q in sp.b)
            coping = max(y for (x, y, z) in sp.b if max(abs(x), abs(z)) == 4)
            courses = {}
            for (x, y, z), (n, pr) in sp.b.items():
                if coping < y < tip - 3:
                    courses.setdefault(y, {})[(x, z)] = pr.get("facing")
            for view in ("south", "34s", "west", "34w", "top"):
                for k, (q, n, _) in pic(sp, view).items():
                    if coping < q[1] < tip - 3:
                        self.assertTrue(n.endswith("_stairs"), (m.name, view, q, n))
            for k, (q, n, _) in pic(sp, "top").items():
                if coping < q[1] < tip - 3:
                    c = courses[q[1]]
                    self.assertTrue(any((q[0] + a, q[2] + b) not in c for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))),
                                    (m.name, q, "inner fill seen from above"))
            for y, c in courses.items():
                R_ = max(max(abs(x), abs(z)) for x, z in c)
                faces = {}
                for (x, z), f in c.items():
                    if all((x + a, z + b) in c for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                        continue
                    key = ("d", x > 0, z > 0) if abs(x) == abs(z) else ("x", x) if abs(x) == R_ else \
                        ("z", z) if abs(z) == R_ else ("d", x > 0, z > 0)
                    faces.setdefault(key, set()).add(f)
                self.assertTrue(all(len(f) == 1 for f in faces.values()), (m.name, y, faces))

    def test_tone_wall_pale_on_top_dark_at_the_foot(self):
        """r6 (r5 critic: the stain reached the top rows): from the front camera the upper half of the wall is >= 75 %
        clean wall and the two foot rows >= 75 % dark plinth and moss."""
        for m in SETS:
            stain, moss = m.weather
            for seed in range(25):
                w = K.tone_wall(m, 12, 7, seed=seed)
                face = {k: n for k, (q, n, _) in pic(w, "south").items()}
                upper = [n for (u, v), n in face.items() if v >= 4]
                foot = [n for (u, v), n in face.items() if v <= 1]
                self.assertGreaterEqual(upper.count(m.wall.full) / len(upper), 0.75, (m.name, seed))
                self.assertGreaterEqual(sum(1 for n in foot if n in (m.dark.full, moss)) / len(foot), 0.75)

    def test_compact_photo_sheets_r6(self):
        """Round 6 (the last part-level round) reshoots the reworked parts on close sheets (<= 25 wide) and the
        Sunforged set."""
        import arch_kit_sheet as KS
        reworked = {"dome", "gear", "dormer", "roof_light", "half_timber", "balustrade", "spire", "tone_wall", "eave"}
        r6 = {part for name in KS.R6 for _, part in KS.SHEETS[name]}
        self.assertEqual(reworked - r6, set())
        self.assertEqual(set(KS.R6), {"arch_kit_r6_roofs", "arch_kit_r6_towers", "arch_kit_r6_walls",
                                      "arch_kit_sunforged"})
        for name in KS.R6:
            x0, x1, _, _, z0, z1 = KS.build(name).bbox()
            self.assertLessEqual(max(x1 - x0 + 1, z1 - z0 + 1), 25, name)

    def test_compact_photo_sheets_r5(self):
        """Round 5 reshoots the reworked parts on close sheets (<= 25 wide) and the Sunforged set."""
        import arch_kit_sheet as KS
        reworked = {"spire", "cresting", "gear", "chimney", "dormer", "tone_wall", "dome", "oversail", "half_timber",
                    "balustrade", "portal"}
        r5 = {part for name in KS.R5 for _, part in KS.SHEETS[name]}
        self.assertEqual(reworked - r5, set())
        self.assertEqual(set(KS.R5), {"arch_kit_r5_roofs", "arch_kit_r5_towers", "arch_kit_r5_walls",
                                      "arch_kit_sunforged"})
        for name in KS.R5:
            x0, x1, _, _, z0, z1 = KS.build(name).bbox()
            self.assertLessEqual(max(x1 - x0 + 1, z1 - z0 + 1), 25, name)

    def test_compact_photo_sheets_r4(self):
        """Round 4 reshoots the reworked parts on close sheets (<= 25 wide) and the Sunforged set."""
        import arch_kit_sheet as KS
        reworked = {"dome", "roof_light", "gear", "machinery", "chimney", "dormer", "tone_wall", "hip_roof", "spire",
                    "half_timber", "portal", "bay_tall"}
        r4 = {part for name in KS.R4 for _, part in KS.SHEETS[name]}
        self.assertEqual(reworked - r4, set())
        self.assertEqual(set(KS.R4), {"arch_kit_r4_roofs", "arch_kit_r4_towers", "arch_kit_r4_walls",
                                      "arch_kit_sunforged"})
        for name in KS.R4:
            x0, x1, _, _, z0, z1 = KS.build(name).bbox()
            self.assertLessEqual(max(x1 - x0 + 1, z1 - z0 + 1), 25, name)


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

    def test_compact_photo_sheets_r3(self):
        """Round 3 reshoots only the reworked parts: they sit on their own sheets."""
        import arch_kit_sheet as KS
        reworked = {"hip_roof", "dome", "spire", "roof_light", "gear", "machinery", "tone_wall", "half_timber",
                    "dormer", "portal", "chimney"}
        r3 = {part for name in KS.R3 for _, part in KS.SHEETS[name]}
        self.assertEqual(reworked - r3, set())
        self.assertIn("arch_kit_sunforged", KS.R3)

    def test_compact_photo_sheets(self):
        """The photo sheets are small (<= 25 x 25 so a part fills the 3/4 still) and between them show every part."""
        import arch_kit_sheet as KS
        shown = set()
        for name, items in KS.SHEETS.items():
            s = KS.build(name)
            x0, x1, _, _, z0, z1 = s.bbox()
            self.assertLessEqual(max(x1 - x0 + 1, z1 - z0 + 1), 25, name)
            shown |= {part for _, part in items}
        self.assertEqual({n for n, _, _ in K.CATALOGUE} - shown, set())

    def test_writes_as_a_structure(self):
        rig = K.demo_bay(BW).to_rig("demo")
        self.assertGreater(len(rig.b), 500)


if __name__ == "__main__":
    unittest.main()
