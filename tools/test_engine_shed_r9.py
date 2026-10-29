"""Engine Shed T1 r9 (detail): the architecture gates and the critic's / reviewer's r9 list, measured on the built
.nbt. Written BEFORE the r9 generator and run red on r8b (94bc09c0). Run:
python3 -m pytest -q tools/test_engine_shed_r9.py

The brief (Oct 29): lovish "its not looking realistic enough", "become a master architect"; envelope 17 x 25 x 26
kept; roof shapes wait for Macaw's Roofs (no reshaping beyond carrying r8a's wing). The fresh critic on the two r8
builds: r8b (5 / 5) over r8a (3 / 4); carry from r8a the wing's stepped roof with the chimney beside it, a lantern
row under the shed eave and r8a's darker ledge tone on the cone; no raw orange copper anywhere; roofs lighter. The
reviewer: lit windows at dusk, and a yard that says what the building makes - a paved apron at the gate, rails into
the lane, a part-built chassis, a coal stage, two lamp posts - with the lane and the spawn column clear.
"""
import os
import sys
import tempfile
import unittest

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

YARD_X = (10, 11)                     # between the hall's back wall (x 9) and the works shed's west wall
LEDGE_ROWS = ((11, 0), (15, 1), (19, 2))     # the cone's tier feet: (step row y, the front face's x)


def light(n):
    return S.glyph(n) == "*"


def base(n):
    """The full block a stair / slab / wall is cut from (the texture the pack measured)."""
    for suf, full in (("_brick_stairs", "_bricks"), ("_brick_slab", "_bricks"), ("_tile_stairs", "_tiles"),
                      ("_tile_slab", "_tiles"), ("_stairs", ""), ("_slab", ""), ("_wall", "")):
        if n.endswith(suf):
            return n[:-len(suf)] + full
    return n


def in_cone(q):
    return 8 <= q[2] <= 16 and q[0] <= 8 and q[1] >= 7


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedR9(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "brass_t1.nbt")
        cls.rig = L.write(L.detail(), cls.out)
        cls.b = S.load(cls.out)
        cls.n = {q: n for q, (n, _) in cls.b.blocks.items() if n not in S.AIR}
        cls.g = G.gates(cls.b)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def top(self, x, z):
        ys = [y for (xx, y, zz) in self.n if (xx, zz) == (x, z)]
        return max(ys) if ys else -1

    # ------------------------------------------------------------------ the gates (tools/building_gates.py)
    def test_g1_every_eave_and_verge_oversails_but_the_spawn_shaft(self):
        """G1 on the whole ring: every column 1 outside the walls carries an oversailing piece at the eave - except the
        spawn column (8, *, 12), which lovish keeps open to the sky (a hard rule, so nothing may stand in it)."""
        fp, _, eave = G._frame(self.b)
        ring = {(x + a, z + c) for (x, z) in fp for a in (-1, 0, 1) for c in (-1, 0, 1)} - fp
        over = {(x, z) for (x, y, z), n in self.n.items() if (x, z) in ring and y >= eave - 1
                and n not in S.GROUND and S.glyph(n) not in G.THIN}
        self.assertEqual(sorted(ring - over), [(L.SPAWN[0], L.SPAWN[2])])

    def gate(self, k):
        self.assertTrue(self.g[k]["ok"], {a: b for a, b in self.g[k].items() if a != "runs"})

    def test_g2_detail_density(self):
        self.gate("G2")

    def test_g3_no_block_over_a_quarter(self):
        self.gate("G3")

    def test_g4_roof_family_is_not_the_wall_family(self):
        self.gate("G4")

    def test_g5_depth_on_the_camera_faces(self):
        self.gate("G5")

    def test_g6_no_roof_chequer(self):
        self.gate("G6")

    def test_g7_base_and_cap_on_every_wall_run(self):
        bad = [r for r in self.g["G7"]["runs"] if not (r["base"] and r["cap"])]
        self.assertEqual(bad, [])

    def test_light_floor_one_light_per_camera_bay(self):
        self.gate("light")

    def test_only_vanilla_ids(self):
        self.gate("ids")

    # ------------------------------------------------------------------ 1. carried from r8a
    def test_wing_roof_is_a_stepped_half_hip_not_a_parapet(self):
        """r8b's wing read as a dark parapet billboard: its roof stood 14 high along the whole outer end, the back
        verge a wall. r8a's: per row from the outer end the roof rises from both sides to a peak in the middle and
        falls to a low back (x 8 at most 9), so the shed and its chimney show behind it: per row x 0..4 never falls,
        x 4..8 never rises, the middle over both ends."""
        for zs in ((0, 1, 2), (24, 23, 22)):
            for z in zs:
                tops = [self.top(x, z) for x in range(0, 9)]
                self.assertEqual(tops[:5], sorted(tops[:5]), (z, tops))
                self.assertEqual(tops[4:], sorted(tops[4:], reverse=True), (z, tops))
                self.assertGreater(tops[4], max(tops[0], tops[8]), (z, tops))
                self.assertLessEqual(tops[8], 9, (z, tops))

    def test_chimney_stands_clear_beside_the_wing(self):
        """The works chimney beside the north wing (x >= 11, z <= 1) rises 5+ over the shed ridge and 3+ over the
        wing's back, banded (2+ materials in its shaft) under a cap."""
        cols = [(x, z) for x in range(11, 17) for z in (0, 1)]
        top = max(self.top(x, z) for x, z in cols)
        self.assertGreaterEqual(top, max(self.top(13, z) for z in range(4, 20)) + 5)
        self.assertGreaterEqual(top, max(self.top(8, z) for z in (0, 1, 2)) + 3)
        shaft = {self.n[(x, y, z)] for x, z in cols for y in range(7, top) if (x, y, z) in self.n}
        self.assertGreaterEqual(len(shaft), 2, shaft)

    def test_lantern_row_under_the_shed_eave(self):
        """r8a's lantern row: 4+ lights under the works shed's yard eave (x 10..11, below its eave at y 5), spread
        along the yard (12+ blocks between the first and the last)."""
        zs = sorted(z for (x, y, z), n in self.n.items() if light(n) and x in YARD_X and y <= 4)
        self.assertGreaterEqual(len(zs), 4, zs)
        self.assertGreaterEqual(zs[-1] - zs[0], 12, zs)

    def test_cone_ledges_weathered_copper_over_dark_treads(self):
        """The tier feet: the front ledges (r8b: raw orange cut copper) a weathered + exposed copper mix; r8a's darker
        tone on the rest of each tier's tread - the step row's cells the tier above leaves open, off the front face -
        a dashed darker line: >= 40 % of those cells darker than the oxidized body (weathered copper, the wet tone, or a
        dark stone). (First draft: >= 80 % dark stone - r8a's whole blackstone ring left the cone 27 % copper from
        above, against r8b's EngineShedR8Gates 60 %; refined to the dashed line before the generator went green.)"""
        for y, fx in LEDGE_ROWS:
            ledge = [n for (x, yy, z), n in self.n.items() if yy == y and x == fx and 9 <= z <= 15 and in_cone((x, y, z))
                     and n.endswith("_stairs") and "copper" in n]
            self.assertTrue(ledge, y)
            self.assertEqual({("weathered" in n) or ("exposed" in n) for n in ledge}, {True}, (y, ledge))
            self.assertTrue(any("weathered" in n for n in ledge) and any("exposed" in n for n in ledge), (y, ledge))
            tread = [n for (x, yy, z), n in self.n.items() if yy == y and x > fx and in_cone((x, y, z))
                     and (x, y + 1, z) not in self.n]
            dark = sum(any(k in n for k in ("blackstone", "deepslate", "weathered")) for n in tread)
            self.assertGreaterEqual(dark / len(tread), 0.4, (y, tread))

    # ------------------------------------------------------------------ 2. colour
    def test_no_unoxidised_copper_anywhere(self):
        """No raw orange copper: every copper block exposed, weathered or oxidized (lightning rods stay orange in
        1.20.1, so none)."""
        raw = sorted({n for n in self.n.values() if "copper" in n and not any(
            k in n for k in ("exposed", "weathered", "oxidized"))} | {n for n in self.n.values() if n == "lightning_rod"})
        self.assertEqual(raw, [])

    def test_slate_roofs_are_mid_grey(self):
        """r8's roofs read as a hole (roof luma 61 < walls 78): the hall, wing and shed roofs' cells seen from above
        (every roof shape off the cone whose material the pack has measured, gen_blocks.TARGETS) average >= 0.40
        luma (r8b's slate mix ~0.22)."""
        up = S.Build({q: c for q, c in self.b.blocks.items() if q[1] >= 1})
        lum = [GB.TARGETS[m]["luma"] for q in S.roof_cells(up) if not in_cone(q)
               for m in [base(self.n[q])] if m in GB.TARGETS]
        self.assertGreater(len(lum), 100)
        self.assertGreaterEqual(sum(lum) / len(lum), 0.40)

    # ------------------------------------------------------------------ 4. lit windows
    def test_every_camera_window_is_lit(self):
        """Each window on the camera faces (west front x <= 3, the wing gable ends z <= 3 / >= 21; panes below the
        eave) has a light within 2 blocks of its glass - lit windows at dusk."""
        panes = {q for q, n in self.n.items() if "glass" in n and q[1] <= 6 and q[0] <= 8 and (
            q[0] <= 3 and not 7 <= q[2] <= 17 or q[2] <= 3 or q[2] >= 21)}
        self.assertTrue(panes)
        lights = [q for q, n in self.n.items() if light(n)]
        groups, seen = [], set()
        for p in sorted(panes):
            if p in seen:
                continue
            todo, g = [p], set()
            while todo:
                q = todo.pop()
                if q in g:
                    continue
                g.add(q)
                todo += [(q[0] + a, q[1] + b, q[2] + c) for a, b, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0),
                                                                        (0, -1, 0), (0, 0, 1), (0, 0, -1))
                         if (q[0] + a, q[1] + b, q[2] + c) in panes]
            seen |= g
            groups.append(g)
        dark = [min(g) for g in groups if not any(max(abs(a - c) for a, c in zip(p, q)) <= 2 for p in g for q in lights)]
        self.assertEqual(dark, [])

    # ------------------------------------------------------------------ 5. the yard
    def test_paved_apron_before_the_gate_and_rails_into_the_lane(self):
        """In front of the gate (x 0, z 5..19) a paved apron - not the tuff foundation, not grass - and rails (y 0,
        below the units' floor, so the lane stays clear) on 2+ lines running from the apron through the lane."""
        apron = [self.n.get((0, 0, z)) for z in range(5, 20)]
        self.assertEqual([n for n in apron if n in (None, "tuff", "grass_block", "moss_block")], [], apron)
        lines = [z for z in L.LANE_Z if all(self.n.get((x, 0, z), "").endswith("rail") for x in L.LANE_X)]
        self.assertGreaterEqual(len(lines), 2, lines)

    def test_a_part_built_chassis_in_the_yard(self):
        """What the shed makes, half built in its yard: 4+ wheels (grindstones) at y 1 and a frame of 6+ cells on
        them at y 2."""
        yard = {q: n for q, n in self.n.items() if q[0] in YARD_X and 1 <= q[1] <= 3}
        wheels = [q for q, n in yard.items() if n == "grindstone" and q[1] == 1]
        self.assertGreaterEqual(len(wheels), 4)
        z0, z1 = min(q[2] for q in wheels), max(q[2] for q in wheels)
        frame = [q for q in yard if q[1] == 2 and z0 <= q[2] <= z1]
        self.assertGreaterEqual(len(frame), 6)

    def test_a_coal_stage_in_the_yard(self):
        """4+ coal blocks on a raised stage (standing on blocks, not the ground) behind a rail of 3+ thin cells."""
        coal = [q for q, n in self.n.items() if n == "coal_block" and q[0] in YARD_X and q[1] >= 2]
        self.assertGreaterEqual(len(coal), 4)
        self.assertTrue(all((x, y - 1, z) in self.n for x, y, z in coal))
        zs = {q[2] for q in coal}
        rail = [q for q, n in self.n.items() if S.glyph(n) == "|" and q[0] in YARD_X and min(zs) - 1 <= q[2] <= max(zs) + 1]
        self.assertGreaterEqual(len(rail), 3)

    def test_two_lamp_posts(self):
        """2+ lamp posts: a light on 2+ stacked thin posts standing on the ground (y 1 up), outside the lane."""
        posts = [(x, y, z) for (x, y, z), n in self.n.items() if light(n)
                 and all(S.glyph(self.n.get((x, yy, z), "")) == "|" for yy in range(1, y))
                 and y >= 3 and not (x in L.LANE_X and z in L.LANE_Z)]
        self.assertGreaterEqual(len(posts), 2, posts)

    def test_lane_and_spawn_column_clear(self):
        self.assertIn("lane clear", L.check(self.rig, self.out))
        self.assertEqual([y for y in range(1, 26) if (L.SPAWN[0], y, L.SPAWN[2]) in self.n], [])


if __name__ == "__main__":
    unittest.main()
