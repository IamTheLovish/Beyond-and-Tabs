"""Engine Shed T1 r10: camera-truth tests - what the four eyes stills SHOW, not what the generator wrote. Written
BEFORE the r10 generator and run red on r9 (3dd3712b). Run: python3 -m pytest -q tools/test_engine_shed_r10.py

Why (Oct 29): the fresh critic scored r9 realism 5 / detail 6 ("more parts, not more building") while every r9 gate
was green - r9's tests checked the generator's cells, the critic looked at the pictures. Every test here casts the
stills' rays (tools/building_gates.py shoot / STILLS: EyesDirector scene 15's rts, rts_close, 34 and dusk cameras -
the RTS camera orthographic from the south-west at the zoom's tilt, the 3/4 still first-person from the south-west
30 degrees up) into the built .nbt and asks what the first block on each ray is. The camera-facing faces are west
(x 0, the front) and south (z 24). lovish's look: red BRICK walls, grey SLATE roofs, weathered green COPPER on the
cone and spires; roof SHAPES wait for Macaw's Roofs (tone, eaves and cresting only).

The critic's r9 list, one test each: (a) brick walls on every camera face and a brick gable end with a quatrefoil
window; (b) no unoxidised copper, (b2) no exposed (orange-tan) copper in any still - lovish's copper is weathered
green; (c) no calcite / diorite / quartz anywhere (snow blotches, the white chimney); (d) the yard (apron, rails to
the plot edge, a chassis under a gantry, a coal stage, two lamp posts) in the rts and 34 pictures; (e) every bay
window shows glass to the 34 camera with a light behind it (dusk: r9's "lit windows" were dark slots); (f) the roofs
mid-tone slate (pack luma 0.28-0.36: r8b's ~0.22 read as a hole, r9's ~0.45 as pale striped concrete), slate family
only, no camera chequer, no lengthwise stripes away from ridges, valleys and eaves, the wing in the same slate;
(g) the cone's corner columns thin (critic: the 1-block dark corner columns make it a ziggurat).
"""
import os
import sys
import tempfile
import unittest
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    import numpy  # noqa: F401
    HAVE_NUMPY = True
except ImportError:
    HAVE_NUMPY = False
import building_gates as G  # noqa: E402
import gen_blocks as GB  # noqa: E402
import schem_study as S  # noqa: E402
if HAVE_NUMPY:
    import lab_vehicle_brass as L  # noqa: E402

BRICK = ("bricks", "brick_stairs", "brick_slab", "brick_wall",                 # the wall and its second tone
         "polished_granite", "polished_granite_stairs", "polished_granite_slab", "granite_wall", "red_terracotta")
RAW_COPPER = ("copper_block", "cut_copper", "cut_copper_stairs", "cut_copper_slab")
PALE_BANNED = ("calcite", "diorite", "quartz")
SLATE_FAMILY = ("deepslate_tiles", "deepslate_bricks", "cobbled_deepslate", "polished_deepslate", "deepslate",
                "tuff", "stone", "stone_bricks")      # dark slate + the lighter grey courses (no andesite / calcite)
ROOF_BAND = (0.28, 0.36)
PAVING = ("stone_bricks", "polished_andesite", "smooth_stone", "smooth_stone_slab", "stone_brick_slab",
          "polished_deepslate", "deepslate_tiles", "deepslate_bricks", "andesite", "stone", "cobbled_deepslate",
          "deepslate_brick_slab", "deepslate_tile_slab", "polished_andesite_slab")


def base(n):
    """The full block a stair / slab / wall is cut from (the texture the pack measured)."""
    for suf, full in (("_brick_stairs", "_bricks"), ("_brick_slab", "_bricks"), ("_brick_wall", "_bricks"),
                      ("_tile_stairs", "_tiles"), ("_tile_slab", "_tiles"), ("_tile_wall", "_tiles"),
                      ("_stairs", ""), ("_slab", ""), ("_wall", "")):
        if n.endswith(suf):
            return n[:-len(suf)] + full
    return n


def raw_copper(n):
    m = n[len("waxed_"):] if n.startswith("waxed_") else n
    return m in RAW_COPPER or n == "lightning_rod"


def in_cone(q):
    return 8 <= q[2] <= 16 and q[0] <= 8 and q[1] >= 7


def light(n):
    return S.glyph(n) == "*"


class Pictures:
    """The built .nbt and its four stills (rays every `step` pixels), shared by the tests."""

    def __init__(self, path, step=2):
        self.b = S.load(path)
        self.n = {q: n for q, (n, _) in self.b.blocks.items() if n not in S.AIR}
        self.hits = {st: G.shoot(self.b, st, step) for st in G.STILLS}
        self.size = tuple(max(q[i] for q in self.n) + 1 for i in range(3))
        self.fp, _, self.eave = G._frame(self.b)
        self.top = {}
        for (x, y, z) in self.n:
            self.top[(x, z)] = max(self.top.get((x, z), -1), y)

    def cells(self, still, pred=lambda h: True):
        """{cell: pixels} of the hits of one still passing pred(hit)."""
        return dict(Counter(h[0] for h in self.hits[still].values() if pred(h)))

    def near_fp(self, q):
        return any((q[0] + a, q[2] + c) in self.fp for a in (-1, 0, 1) for c in (-1, 0, 1))

    def wall(self, still, face):
        """The visible wall cells of a camera face: hits on that side of a block, y 1..7, at the footprint."""
        return self.cells(still, lambda h: h[2] == face and 1 <= h[0][1] <= L.WING_TOP and self.near_fp(h[0]))

    def roof_cell(self, h):
        """A visible roof cell: a hit from above on the top of its column, at the shed eave (y 5) or higher, off the
        cone and the chimney (its 2 x 2 and the ring round it), not a thin piece or a light, over a building (3+
        blocks in the column above the pad)."""
        x, y, z = h[0]
        sx0, sx1, sz0, sz1, _ = L.STACK
        return (h[2] == "up" and y >= L.SHED_TOP and self.top.get((x, z)) == y and not in_cone(h[0])
                and not (sx0 - 1 <= x <= sx1 + 1 and sz0 - 1 <= z <= sz1 + 1)
                and S.glyph(h[1]) not in "|*g=" and sum((x, yy, z) in self.n for yy in range(1, y + 1)) >= 3)

    def roofs(self, still):
        return {h[0]: h[1] for h in self.hits[still].values() if self.roof_cell(h)}


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedR10Camera(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        out = os.path.join(cls.tmp.name, "brass_t1.nbt")
        L.write(L.detail(), out)
        cls.p = Pictures(out)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    # ------------------------------------------------------------------ (a) brick
    def test_a_brick_on_every_camera_face(self):
        """Red brick (and its second tone) >= 35 % of the wall cells each camera face shows in the 34 and rts_close
        stills (r9: brick pilasters behind pale bases, capitals, sills, railings and lanterns - the critic saw pale
        piers and no brick courses between them)."""
        p = self.p
        for still in ("34", "rts_close"):
            for face in ("west", "south"):
                w = p.wall(still, face)
                share = sum(p.n[q] in BRICK for q in w) / max(1, len(w))
                self.assertGreater(len(w), 40, (still, face))
                self.assertGreaterEqual(share, 0.35, (still, face, round(share, 3),
                                                      Counter(p.n[q] for q in w).most_common(6)))

    def test_a_brick_gable_end_with_a_lit_quatrefoil(self):
        """The south wing's gable end (x 1..8, z >= 21, y 8 up) as the 34 still shows it: brick >= 50 % of its cells
        and a quatrefoil window in it - four glass lobes round a centre (left, right, over, under), each lobe's glass
        seen by the 34 camera, a light behind the glass (r8b's gable had one; r9's was plain slate and stone)."""
        p = self.p
        hits = [h for h in p.hits["34"].values() if 1 <= h[0][0] <= 8 and h[0][2] >= 21 and h[0][1] >= 8
                and h[2] == "south"]
        cells = Counter(h[0] for h in hits)
        brick = sum(p.n[q] in BRICK for q in cells) / max(1, len(cells))
        self.assertGreaterEqual(brick, 0.5, Counter(p.n[q] for q in cells).most_common(6))
        glass = {h[0] for h in hits if S.glyph(h[1]) == "g"}
        lit = {h[0] for h in hits if S.glyph(h[1]) == "g" and h[3] and light(h[3][1])}
        foils = [(x, y, z) for (x, y, z) in p.n if all((x + a, y + c, z) in glass for a, c in
                                                       ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        self.assertTrue(foils, sorted(glass))
        self.assertTrue(lit, "no light behind the gable's glass")

    # ------------------------------------------------------------------ (b) copper
    def test_b_no_unoxidised_copper_visible(self):
        """No plain or waxed-plain copper (copper_block, cut_copper and its shapes, their waxed_ forms - all orange)
        nor a lightning rod in any still."""
        seen = {h[1] for st in G.STILLS for h in self.p.hits[st].values() if raw_copper(h[1])}
        self.assertEqual(sorted(seen), [])

    def test_b2_no_exposed_copper_visible(self):
        """No exposed copper (incl. waxed_exposed_*) in any still: its texture is orange-tan, and the critic saw orange
        on cone tier 2, the finial, the wing finials and the hall caps - all r9's 'brass' role, waxed exposed cut
        copper. lovish's copper is weathered GREEN: weathered / oxidized only (the brass role renamed in look)."""
        seen = Counter(h[1] for st in G.STILLS for h in self.p.hits[st].values() if "exposed" in h[1]
                       and "copper" in h[1])
        self.assertEqual(dict(seen), {})

    # ------------------------------------------------------------------ (c) pale stone
    def test_c_no_calcite_diorite_or_quartz_anywhere(self):
        """No calcite, diorite (plain or polished) or quartz in the build: they read as snow (the critic: calcite
        blotches on the roof, a white quartz chimney) and are outside the Brassworks set (arch_kit r5: smooth stone)."""
        bad = Counter(n for n in self.p.n.values() if any(k in n for k in PALE_BANNED))
        self.assertEqual(dict(bad), {})

    # ------------------------------------------------------------------ (d) the yard, in frame
    def test_d_apron_seen(self):
        """A paved apron the RTS and 34 cameras see: >= 50 paved ground cells (y 0) in each of rts and 34 - a
        forecourt, not a kerb (r9's apron was a 1-wide strip under the arcade eave: 27 / 30 cells, which the critic
        read as "a 3-wide lane with rail stubs")."""
        for still in ("rts", "34"):
            paved = [q for q in self.p.cells(still) if q[1] == 0 and self.p.n[q] in PAVING]
            self.assertGreaterEqual(len(paved), 50, (still, len(paved)))

    def test_d_rails_to_the_plot_edge_seen(self):
        """Rails the cameras see: >= 8 rail cells in each of rts and 34, one of them on the plot's edge (r9: two rail
        stubs in the lane's mouth, the rest under the tower)."""
        for still in ("rts", "34"):
            rails = [q for q in self.p.cells(still) if self.p.n[q].endswith("rail")]
            self.assertGreaterEqual(len(rails), 8, (still, len(rails)))
            edge = [q for q in rails if q[0] in (0, self.p.size[0] - 1) or q[2] in (0, self.p.size[2] - 1)]
            self.assertTrue(edge, still)

    def test_d_chassis_under_a_gantry_seen(self):
        """The part-built chassis (4+ grindstone wheels, a frame on them) under a gantry (a beam 2+ over the frame on
        posts): in each of rts and 34 >= 2 wheels, >= 3 frame cells and >= 2 beam cells seen."""
        p = self.p
        wheels = {q for q, n in p.n.items() if n == "grindstone"}
        self.assertGreaterEqual(len(wheels), 4)
        xs, zs = {q[0] for q in wheels}, {q[2] for q in wheels}
        box = (min(xs), max(xs), min(zs), max(zs))
        wy = min(q[1] for q in wheels)
        frame = {q for q in p.n if q[1] == wy + 1 and box[0] <= q[0] <= box[1] and box[2] <= q[2] <= box[3]}
        beam = {q for q in p.n if q[1] >= wy + 3 and box[0] - 1 <= q[0] <= box[1] + 1
                and box[2] - 1 <= q[2] <= box[3] + 1 and not light(p.n[q])}
        self.assertGreaterEqual(len(frame), 6)
        self.assertGreaterEqual(len(beam), 4)
        for still in ("rts", "34"):
            vis = p.cells(still)
            self.assertGreaterEqual(len(wheels & set(vis)), 2, (still, "wheels"))
            self.assertGreaterEqual(len(frame & set(vis)), 3, (still, "frame"))
            self.assertGreaterEqual(len(beam & set(vis)), 2, (still, "beam"))

    def test_d_coal_stage_seen(self):
        """>= 4 coal blocks on a raised stage, >= 3 of them seen in each of rts and 34."""
        coal = {q for q, n in self.p.n.items() if n == "coal_block" and q[1] >= 2}
        self.assertGreaterEqual(len(coal), 4)
        self.assertTrue(all((x, y - 1, z) in self.p.n for x, y, z in coal))
        for still in ("rts", "34"):
            self.assertGreaterEqual(len(coal & set(self.p.cells(still))), 3, still)

    def test_d_two_lamp_posts_seen(self):
        """2+ lamp posts (a light on 2+ stacked thin posts from the ground) whose lights both cameras see, outside the
        lane."""
        p = self.p
        posts = [(x, y, z) for (x, y, z), n in p.n.items() if light(n) and y >= 3
                 and all(S.glyph(p.n.get((x, yy, z), "")) == "|" for yy in range(1, y))
                 and not (x in L.LANE_X and z in L.LANE_Z)]
        for still in ("rts", "34"):
            vis = set(p.cells(still))
            self.assertGreaterEqual(len([q for q in posts if q in vis]), 2, (still, posts))

    # ------------------------------------------------------------------ (e) lit bay windows
    def test_e_every_bay_window_shows_glass_with_a_light_behind(self):
        """Every bay window on the camera faces (glass in the west front x <= 3 or the south front z >= 21, y 1..7;
        panes grouped by touching) shows glass to the 34 camera, and >= 25 % of those glass pixels have a light as
        the first block behind the glass - the dusk read (r9: dark slots; the r9 34 still showed 2 glass pixels)."""
        p = self.p
        panes = {q for q, n in p.n.items() if S.glyph(n) == "g" and 1 <= q[1] <= L.WING_TOP
                 and (q[0] <= 3 or q[2] >= 21) and q[0] <= L.HALL_X[1]}
        self.assertTrue(panes)
        groups, seen = [], set()
        for s in sorted(panes):
            if s in seen:
                continue
            todo, g = [s], set()
            while todo:
                q = todo.pop()
                if q in g or q not in panes:
                    continue
                g.add(q)
                todo += [(q[0] + a, q[1] + b, q[2] + c) for a, b, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0),
                                                                        (0, 0, 1), (0, 0, -1))]
            seen |= g
            groups.append(g)
        hits = [h for h in p.hits["34"].values() if S.glyph(h[1]) == "g"]
        bad = []
        for g in groups:
            mine = [h for h in hits if h[0] in g]
            lit = [h for h in mine if h[3] and light(h[3][1])]
            if not mine or len(lit) < 0.25 * len(mine):
                bad.append((min(g), len(mine), len(lit)))
        self.assertEqual(bad, [], "%d windows" % len(groups))

    # ------------------------------------------------------------------ (f) slate roofs
    def luma(self, names):
        lum = [GB.TARGETS[base(n)]["luma"] for n in names if base(n) in GB.TARGETS]
        return sum(lum) / len(lum), len(lum) / max(1, len(names))

    def regions(self, roofs):
        return {"hall": {q: n for q, n in roofs.items() if q[0] <= L.HALL_X[1] + 1 and 5 <= q[2] <= 19},
                "wing": {q: n for q, n in roofs.items() if q[0] <= L.HALL_X[1] + 1 and (q[2] <= 4 or q[2] >= 20)},
                "shed": {q: n for q, n in roofs.items() if q[0] > L.HALL_X[1] + 1}}

    def test_f_roofs_mid_tone_slate_in_every_roof(self):
        """The roof pixels of rts and rts_close, per roof (hall, wing, shed), average pack luma 0.28-0.36 (both ends:
        r8b ~0.22 read as a hole - r8 critic; r9 ~0.45 read as pale striped concrete - r9 critic), every one a slate-
        family stone (SLATE_FAMILY: deepslate tiles / bricks / cobbled, a lighter tuff or stone course) or the ridge's
        iron, >= 90 % of them measured by the pack."""
        for still in ("rts", "rts_close"):
            pix = [h for h in self.p.hits[still].values() if self.p.roof_cell(h)]
            for name, reg in self.regions({h[0]: h[1] for h in pix}).items():
                names = [h[1] for h in pix if h[0] in reg]
                self.assertGreater(len(names), 50, (still, name))
                lum, known = self.luma(names)
                self.assertGreaterEqual(known, 0.9, (still, name))
                self.assertTrue(ROOF_BAND[0] <= lum <= ROOF_BAND[1], (still, name, round(lum, 3)))
                other = Counter(base(n) for n in names if base(n) not in SLATE_FAMILY)
                self.assertEqual(dict(other), {}, (still, name))

    def test_f_no_roof_chequer_in_the_pictures(self):
        """The roofs as the rts_close camera sees them, laid out on the plan (x, z), shaded by block id: no chequer
        (building_gates.alternations: a b a b a runs or a 3 x 2 a b a / b a b)."""
        roofs = self.p.roofs("rts_close")
        bad = G.alternations({(q[0], q[2]): n for q, n in roofs.items()})
        self.assertEqual(len(bad), 0, sorted(bad)[:12])

    def test_f_no_lengthwise_stripes(self):
        """No stripe down a slope: a run of 5+ roof cells in a line (along x or z), one tone class (dark < 0.27 <=
        mid < 0.40 <= pale, by pack luma), every cell mid-slope (its height strictly between its two cross
        neighbours' - not a ridge, valley or eave) and both cross neighbours another class (r9: dark deepslate-tile
        'wet runs' along the hall between stone-brick courses)."""
        roofs = self.p.roofs("rts_close")
        top = self.p.top

        def cls(n):
            m = base(n)
            v = GB.TARGETS.get(m, {}).get("luma")
            return "?" if v is None else "dark" if v < 0.27 else "mid" if v < 0.40 else "pale"
        plan = {(q[0], q[2]): cls(n) for q, n in roofs.items()}
        stripes = []
        for a in (0, 1):
            for (u, v), c in plan.items():
                prev = (u - 1, v) if a == 0 else (u, v - 1)
                if plan.get(prev) == c:
                    continue
                run = []
                k = (u, v)
                while plan.get(k) == c:
                    cross = [(k[0], k[1] - 1), (k[0], k[1] + 1)] if a == 0 else [(k[0] - 1, k[1]), (k[0] + 1, k[1])]
                    hs = [top.get(cc, -1) for cc in cross]
                    mid = all(cc in plan for cc in cross) and min(hs) <= top[k] <= max(hs) and min(hs) < max(hs)
                    if not (mid and all(plan[cc] != c for cc in cross)):
                        break
                    run.append(k)
                    k = (k[0] + 1, k[1]) if a == 0 else (k[0], k[1] + 1)
                if len(run) >= 5:
                    stripes.append((run[0], len(run), c))
        self.assertEqual(stripes, [])

    # ------------------------------------------------------------------ (g) the cone
    def test_g_cone_corner_columns_are_thin(self):
        """The cone's corner columns (each row's arrises, lab_vehicle_brass.cone_rows) as the 34 and rts_close stills
        show them: no full dark block - a dark stair or post at most (the critic: 1-block deepslate corner columns
        make the cone a ziggurat)."""
        p = self.p
        rows = {}
        for (x, y, z) in p.n:
            if in_cone((x, y, z)):
                rows.setdefault(y, set()).add((x, z))
        corner = set()
        for y, cells in rows.items():
            za, zb = min(z for _, z in cells), max(z for _, z in cells)
            for x, z in cells:
                col = [xx for xx, zz in cells if zz == z]
                if z in (za, zb) and x in (min(col), max(col)):
                    corner.add((x, y, z))
        for still in ("34", "rts_close"):
            full = sorted(q for q in p.cells(still) if q in corner and G.tone(p.n[q]) == "dark"
                          and S.glyph(p.n[q]) == "#")
            self.assertEqual(full, [], still)


if __name__ == "__main__":
    unittest.main()
