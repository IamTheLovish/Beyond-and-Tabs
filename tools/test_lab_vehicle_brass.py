"""tools/lab_vehicle_brass.py: the Engine Shed T1 greybox on the T1 lab plot (design/building_tiers.md, Oct 28 2026:
17 x 25, h <= 26). Run:
python3 -m unittest discover -s tools -p 'test_lab_vehicle_brass.py'

The massing is round 3's (tower over the door, arcade halls, swept end wings: lovish kept it), rescaled. The tables
below were written BEFORE the rescale, from r3's own front outline (wing 2 / hall 5 / tower 7 / hall 5 / wing 2 columns
of 21, tops 12 / 8 / 11-15-19-21 / 8 / 12) scaled to 25 columns and 26 rows: wing 3 / hall 5 / tower 9 / hall 5 /
wing 3, so the tower (the signature) gets the extra resolution. They are exact: never a tolerance (pilot lesson 3).
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    import numpy  # noqa: F401  - the CI lint job may not install it; skip there rather than fail
    HAVE_NUMPY = True
except ImportError:
    HAVE_NUMPY = False
if HAVE_NUMPY:
    import lab_vehicle_brass as L  # noqa: E402

# the front (west) elevation: per z column, the top cell over every x - what the reference's own view sees
FRONT_T1 = [14, 12, 10,                                   # north wing: the swept roof rising to its outer end
            9, 9, 9, 9, 9,                                # north arcade hall's ridge
            11, 15, 19, 23, 25, 23, 19, 15, 11,           # the tower: ONE straight cone, 4 rows a column, 1-wide point
            # (re-mass r3, lovish chose option b, Oct 28: the old row 12 16 19 23 25 stepped 4 3 4 2 and its foot was
            # 11 wide over a vertical base, so the tower read as a block under a spire; ref18's tower is one cone
            # standing on the hall roof. This row is exactly what a straight pyramid voxelizes to - half-width 5.0 at
            # y 7.5, apex y 27, the apex one block over the plot as r3's rule - so no tolerance is needed)
            9, 9, 9, 9, 9,                                # south arcade hall
            10, 12, 14]                                   # south wing
SIZE_T1 = [17, 26, 25]                                    # x (depth), height, z (front width)
APEX = (4, 25, 12)                                        # the tower's point: x, y, z
LANE = (range(0, 9), range(10, 15), 8)                    # the great door: x, z, height (y 1..8), through the tower
# (r4, Oct 28: 5 x 8, the lane every other lab uses; the T1 rescale's 7 x 10 made the door the cone's whole foot -
# ref18's door is ~55 % of the foot's width and ~31 % of the tower's height: 5 / 9 and 8 / 25. T1 Brassworks vehicles
# fit 5 x 8.)


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedT1Greybox(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "brass_t1_greybox.nbt")
        cls.rig = L.write(L.greybox(), cls.out)
        cls.cells = {q for q, (n, _) in cls.rig.b.items() if n != "air"}

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def tops(self, axis):
        t = {}
        for q in self.cells:
            t[q[axis]] = max(t.get(q[axis], -1), q[1])
        return t

    def test_plot_is_the_t1_lab_size(self):
        self.assertEqual(list(self.rig.size), SIZE_T1)
        self.assertEqual((L.SX, L.H_MAX, L.SZ), tuple(SIZE_T1))

    def test_front_outline_column_by_column(self):
        t = self.tops(2)
        self.assertEqual([t.get(z, -1) for z in range(SIZE_T1[2])], FRONT_T1)

    def test_tower_point_is_one_column(self):
        top = [q for q in self.cells if q[1] == APEX[1]]
        self.assertEqual(top, [APEX])
        self.assertEqual(max(q[1] for q in self.cells), APEX[1])

    def test_tower_is_symmetric_above_the_halls(self):
        """Above the hall ridge the tower alone: mirror-symmetric about its axis in both x and z."""
        ax, _, az = APEX
        # the spawn column (8, *, 12) and its mirror cell (0, *, 12) are left out: re-mass r3's cone foot reaches x 8
        # at y 11, where the spawn column (air to the sky, a hard rule) cuts the back flank and the front keeps its cell
        sx, _, sz = L.SPAWN
        up = {q for q in self.cells if q[1] > max(FRONT_T1[3:8]) and abs(q[2] - az) <= 4 and q[0] <= 2 * ax
              and (q[2] != sz or q[0] not in (sx, 2 * ax - sx))}
        self.assertTrue(up)
        self.assertEqual(up, {(x, y, 2 * az - z) for x, y, z in up}, "not symmetric in z")
        self.assertEqual(up, {(2 * ax - x, y, z) for x, y, z in up}, "not symmetric in x")

    def test_great_door_and_spawn_column(self):
        xs, zs, h = LANE
        self.assertEqual((L.LANE_X, L.LANE_Z, L.LANE_H), (xs, zs, h))
        busy = [(x, y, z) for x in xs for z in zs for y in range(1, h + 1) if (x, y, z) in self.cells]
        self.assertEqual(busy, [])
        self.assertIn(L.SPAWN[0], xs)
        self.assertIn(L.SPAWN[2], zs)

    def test_build_checks_pass(self):
        """The script's own checks: foundation, lane, spawn column, nothing floating, no trapped pockets."""
        self.assertIn("lane clear", L.check(self.rig, self.out))


REGIONS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "design", "vehicle_labs", "brass_t1_regions.json")


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedT1Detail(unittest.TestCase):
    """Detail round 1 on the approved greybox, to the fixed checklist (design/vehicle_labs/briefs/brass_t1_checklist.md):
    the greybox's exact front outline, the build checks, only the checklist's palette roles, and every region of
    brass_t1_regions.json passing facade_audit (>= 2 depth planes, >= 3 materials, no material over 40 %)."""
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "brass_t1.nbt")
        cls.rig = L.write(L.detail(), cls.out)
        cls.names = {q: n for q, (n, _) in cls.rig.b.items() if n != "air"}

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_size_and_front_outline_are_the_greybox_ones(self):
        self.assertEqual(list(self.rig.size), SIZE_T1)
        t = {}
        for x, y, z in self.names:
            t[z] = max(t.get(z, -1), y)
        self.assertEqual([t.get(z, -1) for z in range(SIZE_T1[2])], FRONT_T1)

    def test_build_checks_pass(self):
        """Lane, spawn column, nothing floating, no trapped pockets; no banned blocks."""
        self.assertIn("lane clear", L.check(self.rig, self.out))
        L.check_banned(self.rig, "brass_t1")

    def test_only_the_checklist_palette(self):
        stray = sorted(set(self.names.values()) - L.ALLOWED)
        self.assertEqual(stray, [])
        self.assertFalse([n for n in L.ALLOWED if any(k in n for k in L.BANNED)])

    def test_light_budget(self):
        """At most one small light per arcade bay; lanterns only at the door, the tower slot and the shed slot."""
        v = list(self.names.values())
        self.assertLessEqual(sum(n.endswith("candle") for n in v), len(L.ARCH_Z))
        self.assertLessEqual(v.count("lantern"), 3)

    # ---------------------------------------------------------------- re-mass r3 (lovish chose option b, Oct 28)
    # The art director on the r2 sheet: the tower read as "a dark rectangular block with a black door, capped by a
    # pale band, under a stepped spire" - black buttresses / jambs rising vertically over the hall roof, a pale ledge
    # across the tower at y 12-13, a dark door filling the base. ref18's tower is ONE mid-red cone standing on the hall
    # roof, dark arrises, its brightest spot at the door. These pin that read in cells.
    PALE = ("calcite", "quartz", "bone_block", "diorite")
    DARK = ("blackstone", "deepslate")

    def front_cell(self, y, z):
        """(x, name) of the front-most block the west elevation shows at (y, z), or None."""
        xs = [x for x in range(SIZE_T1[0]) if (x, y, z) in self.names]
        return (min(xs), self.names[(min(xs), y, z)]) if xs else None

    def tower_face(self, y):
        """The tower's front cells at row y >= 7, as {z: name}: the front-most cell of a column z 7..17 standing IN
        FRONT of the hall roof at that row (the hall's own front at a hall column, z 5; past its ridge the hall shows
        nothing), and on the door's rows the door's columns as the elevation shows them (the far wall through the lane).
        r3 left the door out of its rows; with the 5-wide lane a row beside the door then held only the 4 cone cells
        z 8 9 15 16, so the two jamb cells of any symmetric arch ring scored 50 % pale - the door is in the row."""
        hall = self.front_cell(y, 5)
        lim = hall[0] if hall else SIZE_T1[0]
        face = {}
        for z in range(7, 18):
            c = self.front_cell(y, z)
            if c and (c[0] < lim or (z in L.LANE_Z and y <= L.LANE_H)):
                face[z] = c[1]
        return face

    def test_tower_outline_has_no_vertical_edge_above_the_eave(self):
        """B: above the hall eave the tower's front outline is the cone's own staircase - its half-width never grows
        going up, is at most the cone's foot (4: z 8..16) from y 8, and holds for at most 4 rows before stepping in
        (the cone's step). The eave row y 7 is the hall roof's first course, where the cone stands and the buttresses
        may cap; a run is counted above it. r2's tower failed this: its dark jambs at z 8 / 16 ran y 7..12 (6 rows)."""
        w = {}
        for y in range(7, SIZE_T1[1]):
            face = self.tower_face(y)
            if face:
                w[y] = max(abs(z - APEX[2]) for z in face)
        ys = sorted(w)
        self.assertEqual([w[a] >= w[b] for a, b in zip(ys, ys[1:])], [True] * (len(ys) - 1), w)
        self.assertLessEqual(max(w[y] for y in ys if y >= 8), 4, w)
        runs = {}
        for y in ys:
            if y >= 8:
                runs[w[y]] = runs.get(w[y], 0) + 1
        self.assertEqual({k: v for k, v in runs.items() if v > 4}, {}, w)

    def test_no_pale_band_on_the_cone(self):
        """D / C: above y 6 no row of the tower's front face is more than 30 % pale (pale is single cells and lines,
        never a band - the r2 'lid' at y 12-13), and no full row of 3+ cells is all dark (no dark course either)."""
        for y in range(7, SIZE_T1[1]):
            face = self.tower_face(y)
            if not face:
                continue
            # pale ON the cone: the door's look-through (the far wall, 9 deep - it renders mid grey) widens the row
            # (r4) but is the door's light, not a band on the cone (r6 made the far wall's top calcite: critic on r5)
            pale = sum(any(k in n for k in self.PALE) for z, n in face.items()
                       if not (z in L.LANE_Z and y <= L.LANE_H and self.front_cell(y, z)[0] >= L.HALL_X[1]))
            self.assertLessEqual(pale / len(face), 0.30, "row %d: %s" % (y, face))
            if y > L.LANE_H and len(face) >= 3:
                dark = sum(any(k in n for k in self.DARK) for n in face.values())
                self.assertLess(dark, len(face), "row %d is a full dark course: %s" % (y, face))

    def test_cone_face_reaches_the_eave_beside_the_door(self):
        """r4: with the 5-wide lane the cone's face and dark arrises come down to the eave on both sides of the door
        (z 8..9 and 15..16 in the face plane x 0 from y 7), and over the door head the face runs on red - no shoulder."""
        for y in range(L.HALL_TOP + 1, L.LANE_H + 1):
            for z in (8, 9, 15, 16):
                self.assertEqual(self.front_cell(y, z)[0], 0, (y, z))
            for z in (8, 16):
                self.assertTrue(any(k in self.front_cell(y, z)[1] for k in self.DARK), (y, z))
        y = L.LANE_H + 2
        red = ("terracotta", "mangrove", "granite")
        row = {z: self.front_cell(y, z)[1] for z in range(9, 16)}
        self.assertEqual({z: n for z, n in row.items() if not any(k in n for k in red) and n != "lantern"}, {}, y)

    def test_door_is_the_bright_spot(self):
        """E: the door is the front's light spot (ref18), not a black hole - the lane's far wall (x 9) and its
        reveals (the lane's side walls, x 1..8) pale-dominant, ONE lantern in the door head; the lane itself air."""
        far = [(L.HALL_X[1], y, z) for z in L.LANE_Z for y in range(1, L.LANE_H + 1)]
        # the reveals inside the mouth (x 1..8): r5 moved the mouth's own jambs (x 0) to polished granite - the light
        # is a spot at the head, not a frame (test_door_light_is_a_spot_not_a_frame governs x 0)
        rev = [(x, y, z) for x in L.LANE_X[1:] for z in (L.LANE_Z[0] - 1, L.LANE_Z[-1] + 1)
               for y in range(1, L.LANE_H + 1)]
        cells = [self.names.get(q, "air") for q in far + rev]
        pale = sum(any(k in n for k in self.PALE) for n in cells)
        self.assertGreater(pale / len(cells), 0.5, "door region pale %d of %d" % (pale, len(cells)))
        floor = {self.names.get((x, 0, z)) for x in L.LANE_X for z in L.LANE_Z}
        # r5 (critic on r4): smooth stone on a tuff threshold - r3's polished diorite threshold read as a pale strip
        self.assertEqual(floor, {"smooth_stone_slab", "tuff"})
        self.assertEqual({self.names.get((0, 0, z)) for z in L.LANE_Z}, {"tuff"})
        head = [q for q, n in self.names.items() if n == "lantern" and q[0] <= 1 and
                L.LANE_H < q[1] <= L.LANE_H + 3 and q[2] in L.LANE_Z]
        self.assertEqual(len(head), 1, head)

    # ---------------------------------------------------------------- r5: the fresh critic's fixes on r4 (6 / 5)
    def test_door_light_is_a_spot_not_a_frame(self):
        """r4's door was a white U-frame (~20 calcite cells, luma ~199, round a dark mouth); r5's two pale voussoirs
        either side of a brass keystone read as an owl face (critic on r5). ref18's door light is ONE spot: in the
        door's mouth (x 0, z 9..15, y 1..9) pale only the quartz keystone (y 9, z 12) carrying the lantern, the rest of
        the head and the jambs polished granite; behind, a light field - the far wall's top (y 6..8) calcite, its
        rosette brass (r5's chiseled boss was the front's darkest cell, luma 18)."""
        mouth = {(y, z): self.front_cell(y, z)[1] for y in range(1, L.LANE_H + 2) for z in range(9, 16)
                 if self.front_cell(y, z)[0] == 0}
        self.assertEqual(sorted(q for q, n in mouth.items() if any(k in n for k in self.PALE) and q[0] < 8), [])
        for z in (9, 15):
            self.assertEqual({self.names[(0, y, z)] for y in range(2, 8)}, {"polished_granite"}, z)
        head = sorted(q for q, n in mouth.items() if any(k in n for k in self.PALE))
        self.assertEqual(head, [(L.LANE_H + 1, 12)])
        yh = L.LANE_H + 1
        self.assertEqual([self.names[(0, yh, z)] for z in range(10, 15)],
                         ["polished_granite"] * 2 + ["quartz_block"] + ["polished_granite"] * 2)
        self.assertEqual(self.names[(0, yh + 1, 12)], "lantern")
        far = {self.names[(L.HALL_X[1], y, z)] for y in (6, 7, 8) for z in L.LANE_Z}
        self.assertEqual(far - {"waxed_cut_copper"}, {"calcite"})

    def test_tier_corners_are_stairs_low_side_out(self):
        """r6 (critic on r5: the tiers read as stacked boxes): on each tier's top two rows (y 10-11, 14-15, 18-19,
        22-23) every cell of the outline column z = 12 +- half-width is a stair, its tall half inward (or an outer
        corner from the x run, east at the front and west at the back), so the cone's edge steps by half blocks.
        A pin: the greybox's voxelization already gave these stairs; settle() turns the corners outer."""
        for y in (10, 11, 14, 15, 18, 19, 22, 23):
            w = 4 - max(0, (y - 8) // 4)
            for z, inward in ((12 - w, "south"), (12 + w, "north")):
                run = [(x, self.names[(x, y, z)]) for x in range(SIZE_T1[0]) if (x, y, z) in self.names]
                self.assertTrue(run and all(n.endswith("_stairs") for _, n in run), (y, z, run))
                faces = {self.rig.b[(x, y, z)][1].get("facing") for x, _ in run}
                self.assertLessEqual(faces, {inward, "east", "west"}, (y, z))

    def test_top_tier_matches_the_body(self):
        """r6: the top tier (y 20..23) red terracotta with granite ticks - in r5 it rendered 43..48 against the body's
        60, darkening upward: full cells red terracotta, the outline's stair cells polished granite (the ticks), the
        centre's stairs mangrove (all-granite stair rows put granite over 40 % of R8)."""
        for y in range(20, 24):
            for z in (11, 12, 13):
                x, n = self.front_cell(y, z)
                stairs = n.endswith("_stairs")
                want = ("red_terracotta" if not stairs else "polished_granite_stairs" if z != 12 else "mangrove_stairs")
                self.assertEqual(n, want, (y, z))

    def test_shed_is_a_shallow_gable_with_two_lit_slots(self):
        """r6 (R9; critic on r5: the shed, ~40 % of the RTS view, a black box, median luma 24): a shallow gable of
        deepslate-tile shapes over x 11..16, z 2..22 - eaves at y 5 (x 11 / 16), the ridge at y 6 over x 13..14 -
        under the hall ridge; no parapet; two lit 1 x 3 slots in the south wall (z 24); the stack clear above."""
        for z in range(2, 23):
            tops = {x: max(y for y in range(SIZE_T1[1]) if (x, y, z) in self.names) for x in range(11, 17)}
            self.assertEqual(tops, {11: 5, 12: 6, 13: 6, 14: 6, 15: 6, 16: 5}, z)
            for x in (11, 16):
                self.assertTrue(self.names[(x, 5, z)].endswith("_stairs"), (x, z))
        self.assertEqual([q for q, n in self.names.items() if q[1] == 6 and q[2] == 24 and q[0] >= 11], [])
        lit = sorted(q for q, n in self.names.items() if n == "lantern" and q[0] >= 11)
        self.assertEqual(lit, [(12, 2, 24), (15, 2, 24)])
        for x, _, z in lit:
            self.assertEqual([self.names.get((x, y, z)) for y in (3, 4)], [None, None])
        self.assertGreater(max(y for (x, y, z) in self.names if 13 <= x <= 14 and z <= 1), 6)

    def test_eye_is_granite_round_a_red_heart(self):
        """r4's calcite eye stood +120 luma over the cone body and cut the cone into two tiers; target +40..50: the
        ring polished granite, the red-nether heart kept (the elevation cells of r2's eye)."""
        ring = [self.front_cell(y, z)[1] for y, z in ((14, 12), (15, 11), (15, 13), (16, 12))]
        self.assertEqual(ring, ["polished_granite"] * 4)
        self.assertEqual(self.front_cell(15, 12)[1], "red_nether_bricks")

    def test_upper_spire_is_red_and_arrises_dark(self):
        """r4's top tier was 13 of 16 cells dark - a dark cap, a third tier. The top tier (y 20..23, z 11..13) is one
        red field; the point (y 24) dark under the brass tip (y 25); below y 20 every row's outer columns (the
        arrises, z = 12 +- half-width) dark from the eave up."""
        red = ("terracotta", "mangrove", "granite")
        top = {(y, z): self.front_cell(y, z)[1] for y in range(20, 24) for z in (11, 12, 13)}
        self.assertEqual({q: n for q, n in top.items() if not any(k in n for k in red)}, {})
        self.assertEqual(self.front_cell(24, 12)[1], "blackstone")
        self.assertTrue(self.front_cell(25, 12)[1].startswith("waxed_cut_copper"))
        for y in range(7, 20):
            w = 4 - max(0, (y - 8) // 4)                      # the cone's half-width at row y (FRONT_T1's steps)
            for z in (12 - w, 12 + w):
                self.assertTrue(any(k in self.front_cell(y, z)[1] for k in self.DARK), (y, z))

    def test_every_region_passes_the_facade_audit(self):
        import json
        import facade_audit as FA
        with open(REGIONS) as f:
            regions = json.load(f)
        rep = FA.audit(self.out, regions)
        self.assertEqual(len(rep), 13)
        bad = ["%s: %s" % (r["name"], "; ".join(r["faults"])) for r in rep if r["faults"]]
        self.assertEqual(bad, [])


def committed_cells(path):
    """{(x, y, z): (name, props)} of git HEAD's copy of a design file (a regenerated file can't vouch for itself), or
    None outside git."""
    import subprocess
    import preview_structure as PS
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    try:
        data = subprocess.run(["git", "show", "HEAD:" + path], capture_output=True, check=True, cwd=root_dir).stdout
    except (OSError, subprocess.CalledProcessError):
        return None
    with tempfile.NamedTemporaryFile(suffix=".nbt") as f:
        f.write(data)
        f.flush()
        root = PS.load_nbt(f.name)
    pal = root["palette"]
    return {tuple(b["pos"]): (str(pal[b["state"]]["Name"]).split(":")[-1],
                              {k: str(v) for k, v in pal[b["state"]].get("Properties", {}).items()})
            for b in root["blocks"]}


# THE KEEPS (critic, round 1): boxes (x0, x1, y0, y1, z0, z1), inclusive, that later rounds must not change - each
# cell's block AND state equal to the committed design/vehicle_labs/brass_t1.nbt
KEEPS = {
    "R4 hall roof N": (0, 10, 7, 10, 3, 6), "R4 hall roof S": (0, 10, 7, 10, 18, 21),
    "R3 cornice + candles N": (0, 0, 6, 7, 0, 6), "R3 cornice + candles S": (0, 0, 6, 7, 18, 24),
}
# Re-mass r3 (lovish chose option b, Oct 28) moved three keeps on purpose, each still pinned:
# - the hall roofs' last columns z 7 / 17 (r2: the tower's base, its foot stairs and a buttress pinnacle to y 9) are
#   now the hall roof running into the cone's flank (brief F): each cell equals the committed hall roof one column
#   out (z 6 / 18), except the buttress cap AT the eave (0, 7, 7 / 17) where z 6 / 18 carry a bay candle;
# - the R7 eye keeps its ELEVATION cells (y 14..16, z 11..13: a pale ring round a deep-red heart), set into the
#   cone's new face planes (x 1 / 2 instead of 2 / 3);
# - the buttress pinnacles' brass tips (0, 9, 7 / 17) are gone (brief B: nothing on the tower rises above the eave).
HALL_EDGE = {7: 6, 17: 18}
BUTTRESS_CAPS = [(0, 7, 7), (0, 7, 17)]
EYE = (14, 16, 11, 13)                                    # y0, y1, z0, z1 of the R7 eye in the west elevation
PINNACLE_TIPS = [(0, 9, 7), (0, 9, 17)]
# r4 (Oct 28): the lane is 5 x 8, so the door's brass keystone moved from r3's head (0, 11, 12) to the new head
# (0, 9, 12) - test_door_is_the_bright_spot pins the lantern it carries
MOVED_BRASS = PINNACLE_TIPS + [(0, 11, 12), (0, 9, 12)]
# (r6: the keystone is quartz - the critic on r5 read pale | brass | pale as an owl face - and the rosette's boss
# (9, 6, 12) is brass now; the R9 shed slot keep is replaced by test_shed_is_a_shallow_gable_with_two_lit_slots)


def eye_roles(cells):
    """The west elevation over the R7 eye: per (y, z) the front-most cell's role - pale ring, deep-red heart, red
    (any red-role block or shape: the cone's tier rows turned some stairs into full cells)."""
    out = {}
    for y in range(EYE[0], EYE[1] + 1):
        for z in range(EYE[2], EYE[3] + 1):
            xs = [x for x in range(SIZE_T1[0]) if (x, y, z) in cells]
            n = cells[(min(xs), y, z)][0] if xs else "air"
            out[(y, z)] = ("ring" if any(k in n for k in ("calcite", "quartz", "diorite")) or n == "polished_granite" else
                           "heart" if n.startswith("red_nether") else
                           "red" if any(k in n for k in ("terracotta", "mangrove", "granite")) else n)
    return out


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedT1Keeps(unittest.TestCase):
    """Round 2 must not regress what round 1's critic kept: frozen cell for cell against git HEAD's brass_t1.nbt."""
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        out = os.path.join(cls.tmp.name, "brass_t1.nbt")
        cls.got = {q: (n, {k: str(v) for k, v in p.items()}) for q, (n, p) in L.write(L.detail(), out).b.items()}
        cls.want = committed_cells(L.OUT_T1)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def setUp(self):
        if self.want is None:
            self.skipTest("no committed brass_t1.nbt (not in git)")

    def test_keeps_cell_for_cell(self):
        for name, (x0, x1, y0, y1, z0, z1) in KEEPS.items():
            box = [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)]
            diff = [q for q in box if self.got.get(q) != self.want.get(q)]
            self.assertEqual(diff, [], name)

    def test_hall_roofs_run_into_the_cone(self):
        for z, zh in HALL_EDGE.items():
            box = [(x, y, z) for x in range(0, 11) for y in range(7, 11) if (x, y, z) not in BUTTRESS_CAPS]
            diff = [q for q in box if self.got.get(q) != self.want.get((q[0], q[1], zh))]
            self.assertEqual(diff, [], "z %d is not the hall roof of z %d" % (z, zh))

    def test_the_eye_keeps_its_elevation(self):
        """The eye's cells and heart stay where r2 put them; r5 turned its ring calcite -> polished granite (the
        critic: +120 luma over the body split the cone) - EngineShedT1Detail.test_eye_is_granite_round_a_red_heart."""
        got = eye_roles(self.got)
        self.assertEqual(sorted(q for q, r in got.items() if r == "ring"), [(14, 12), (15, 11), (15, 13), (16, 12)])
        self.assertEqual(got[(15, 12)], "heart")

    def test_the_eight_bay_candles(self):
        lit = sorted(q for q, (n, _) in self.got.items() if n.endswith("candle"))
        self.assertEqual(lit, [(0, 7, z) for z in L.ARCH_Z])

    def test_every_brass_cell_kept(self):
        brass = sorted(q for q, (n, _) in self.want.items() if n.startswith("waxed_cut_copper")
                       and q not in MOVED_BRASS)
        self.assertTrue(brass)
        self.assertEqual([q for q in brass if self.got.get(q) != self.want[q]], [])


if __name__ == "__main__":
    unittest.main()
