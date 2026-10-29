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
        """At most one small light per arcade bay; lanterns only the door's glow (r7: two, on the jamb tops - one on
        the keystone lit nothing the camera sees at dusk) and the shed's two lit slots (r6)."""
        v = list(self.names.values())
        self.assertLessEqual(sum(n.endswith("candle") for n in v), len(L.ARCH_Z))
        # r9 (the reviewer on r8: no lit windows at dusk; building_gates' light floor, >= 1 per camera bay; r8a's
        # lantern row): lanterns light the windows, piers, yard and gate - still a light budget, one per window,
        # pier or post (test_engine_shed_r9.test_every_camera_window_is_lit pins where they stand)
        self.assertLessEqual(v.count("lantern"), 64)

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
        # from the hall's wall plane (x 1) back: r8's eave course oversails to x 0 (G1), level with the cone's foot
        lim = max(hall[0], L.HALL_X[0]) if hall else SIZE_T1[0]
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
        (z 8..9 and 15..16 in the face plane x 0 from y 7), and over the door head the face runs on - no shoulder.
        r8 (lovish, Oct 28 22:59: the cone is weathered green COPPER): the row over the head is copper or the R10
        corbels under the tier's ledge (r7 asked for red here)."""
        for y in range(L.HALL_TOP + 1, L.LANE_H + 1):
            for z in (8, 9, 15, 16):
                self.assertEqual(self.front_cell(y, z)[0], 0, (y, z))
            for z in (8, 16):
                self.assertTrue(any(k in self.front_cell(y, z)[1] for k in self.DARK), (y, z))
        y = L.LANE_H + 2
        row = {z: self.front_cell(y, z)[1] for z in range(9, 16)}
        self.assertEqual({z: n for z, n in row.items() if "copper" not in n and n != CORBEL and n != "lantern"}, {}, y)

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
        # r5 (critic on r4): smooth stone on a tuff threshold - r3's polished diorite threshold read as a pale strip.
        # r9 (the reviewer): rails in the lane on two lines, the apron's stone bricks at its mouth (x 0)
        self.assertEqual(floor, {"smooth_stone_slab", "tuff", "rail", "stone_bricks"})
        self.assertEqual({self.names.get((0, 0, z)) for z in L.LANE_Z}, {"stone_bricks", "rail"})
        head = sorted(q for q, n in self.names.items() if n == "lantern" and q[0] <= 1 and q[1] > L.LANE_H)
        self.assertEqual(head, [(0, L.LANE_H + 1, L.LANE_Z[0] - 1), (0, L.LANE_H + 1, L.LANE_Z[-1] + 1)])

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
        for z in (9, 15):     # r9 (G1 at the gate): the jambs one back (x 1) under y 7, so the hood oversails them
            self.assertEqual({self.names[(L.DOOR_X, y, z)] for y in range(2, 7)} | {self.names[(0, 7, z)]},
                             {"polished_granite"}, z)
        head = sorted(q for q, n in mouth.items() if any(k in n for k in self.PALE))
        self.assertEqual(head, [(L.LANE_H + 1, 12)])
        yh = L.LANE_H + 1
        self.assertEqual([self.names[(0, yh, z)] for z in range(10, 15)],
                         ["polished_granite"] * 2 + ["quartz_block"] + ["polished_granite"] * 2)
        self.assertNotEqual(self.names.get((0, yh + 1, 12)), "lantern")   # r7: the lanterns stand on the jamb tops
        far = {self.names[(L.HALL_X[1], y, z)] for y in (6, 7, 8) for z in L.LANE_Z}
        self.assertEqual(far - {L.FAM["brass"][0]}, {"calcite"})     # r9: the brass role is exposed copper

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
        """r6: the top tier (y 20..21) in the cone's body material; r7 (critic in game on r6: the top a 3 x 3 stub
        under a copper block): over it a needle - y 22..23 every cell but the 1-wide core (x 4, z 12) a stair, tall
        half toward the core; y 24 a blackstone-brick wall post; the copper tip at y 25. r8: the body is COPPER (r7:
        red terracotta / mangrove)."""
        for y in (20, 21):
            for z in (11, 12, 13):
                x, n = self.front_cell(y, z)
                self.assertTrue(n.startswith("waxed_") and "copper" in n, (y, z, n))
        for y in (22, 23):
            ring = [(q, self.rig.b[q]) for q in self.rig.b if q[1] == y and q != (4, y, 12) and self.rig.b[q][0] != "air"]
            self.assertTrue(ring)
            for (x, _, z), (n, p) in ring:
                self.assertTrue(n.endswith("_stairs") and "copper" in n, (x, y, z, n))
                want = {"east" if x < 4 else "west" if x > 4 else None, "south" if z < 12 else "north" if z > 12 else None}
                self.assertIn(p["facing"], want - {None}, (x, y, z))
        self.assertEqual(self.names[(4, 24, 12)], "polished_blackstone_brick_wall")

    def test_no_granite_tick_stands_on_the_tiers(self):
        """r7 (critic on r6, in game: the ziggurat read is NOT resolution - the granite ticks at every tier corner put
        a pink rim on each 4-row box): on the tiers' step rows (y 11, 15, 19, 23) polished granite only as a slab on
        the tread, never a stair or a full block; the eye's ring (frozen) aside."""
        eye = {(1, 15, 11), (1, 15, 13)}
        for (x, y, z), n in self.names.items():
            if y in (11, 15, 19, 23) and x <= 8 and 8 <= z <= 16 and "granite" in n and (x, y, z) not in eye:
                self.assertTrue(n.endswith("_slab"), ((x, y, z), n))

    def test_shed_is_a_shallow_gable_with_two_lit_slots(self):
        """r6 (R9; critic on r5: the shed, ~40 % of the RTS view, a black box, median luma 24): a shallow gable over
        x 11..16 - eaves at y 5 (x 11 / 16), the ridge at y 6 over x 13..14 - under the hall ridge; no parapet; two
        lit 1 x 3 slots in the south wall; the stack clear above. r8 (G1, the brief: "walls may step back 1 from the
        plot edge so eaves oversail"): the gable runs on to z 24 as its verge, the south wall one back at z 23, so the
        slots and their lanterns are at z 23 (r7: z 24, x 12 / 15); over the verge only stairs and slabs (no parapet)."""
        for z in range(2, 25):
            tops = {x: max(y for y in range(SIZE_T1[1]) if (x, y, z) in self.names) for x in range(11, 17)}
            self.assertEqual(tops, {11: 5, 12: 6, 13: 6, 14: 6, 15: 6, 16: 5}, z)
            for x in (11, 16):
                self.assertTrue(self.names[(x, 5, z)].endswith("_stairs"), (x, z))
        verge = {q: n for q, n in self.names.items() if q[2] == 24 and q[0] >= 11 and q[1] >= 5}
        self.assertEqual(len(verge), 10)
        self.assertEqual([n for n in verge.values() if not n.endswith(("_stairs", "_slab"))], [])
        self.assertEqual([q for q in self.names if q[2] == 24 and q[0] >= 11 and 1 <= q[1] <= 4], [])
        lit = sorted(q for q, n in self.names.items() if n == "lantern" and q[0] >= 11 and q[2] == 23)
        self.assertEqual(lit, [(13, 2, 23), (14, 2, 23)])     # the side walls step back too (x 12..15): the slots
        for x, _, z in lit:                                    # between the corners, 1 x 2 under the G7 cap at y 4
            self.assertIsNone(self.names.get((x, 3, z)))
            self.assertTrue(pale(self.names[(x, 4, z)]), (x, z))
        self.assertGreater(max(y for (x, y, z) in self.names if 13 <= x <= 14 and z <= 1), 6)

    def test_eye_is_granite_round_a_red_heart(self):
        """r4's calcite eye stood +120 luma over the cone body and cut the cone into two tiers; target +40..50: the
        ring polished granite, the red-nether heart kept (the elevation cells of r2's eye)."""
        ring = [self.front_cell(y, z)[1] for y, z in ((14, 12), (15, 11), (15, 13), (16, 12))]
        self.assertEqual(ring, ["polished_granite"] * 4)
        self.assertEqual(self.front_cell(15, 12)[1], "red_nether_bricks")

    def test_upper_spire_is_one_field_and_arrises_dark(self):
        """r4's top tier was 13 of 16 cells dark - a dark cap, a third tier. The top tier (y 20..23, z 11..13) is one
        field, never dark; the point (y 24) dark under the brass tip (y 25); below y 20 every row's outer columns (the
        arrises, z = 12 +- half-width) dark from the eave up - r8: the dark RIBS of R12 on a copper cone (r7: red)."""
        top = {(y, z): self.front_cell(y, z)[1] for y in range(20, 24) for z in (11, 12, 13)}
        self.assertEqual({q: n for q, n in top.items() if "copper" not in n or any(k in n for k in self.DARK)}, {})
        self.assertTrue(self.front_cell(24, 12)[1].startswith("polished_blackstone_brick"))
        self.assertTrue(self.front_cell(25, 12)[1].startswith(L.FAM["brass"][0]))     # r9: no raw copper
        for y in range(7, 20):
            w = 4 - max(0, (y - 8) // 4)                      # the cone's half-width at row y (FRONT_T1's steps)
            for z in (12 - w, 12 + w):
                self.assertTrue(any(k in self.front_cell(y, z)[1] for k in self.DARK), (y, z))

    def test_every_region_passes_the_facade_audit(self):
        import json
        import facade_audit as FA
        with open(REGIONS) as f:
            regions = json.load(f)
        self.assertEqual(len(regions), 13)
        # r8: the roof faces (face "up": R4 hall roofs, R5 wing roofs, R9 shed roof) are gated by G6 instead -
        # roof_kit courses hold the base tone at 55-72 % (design/architecture_rules.md), which this audit's 40 %
        # top-share rule forbids; every wall and tower face keeps the audit
        rep = FA.audit(self.out, [r for r in regions if r["face"] != "up"])
        self.assertEqual(len(rep), 8)
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


CORBEL = "deepslate_brick_stairs"     # r8: R10's corbels under each tier's ledge, a dark stone apart from the ribs


# Re-mass r3 (lovish chose option b, Oct 28) moved keeps on purpose, each still pinned:
# - the R7 eye keeps its ELEVATION cells (y 14..16, z 11..13: a pale ring round a deep-red heart), set into the
#   cone's new face planes (x 1 / 2 instead of 2 / 3);
# - the buttress pinnacles' brass tips (0, 9, 7 / 17) are gone (brief B: nothing on the tower rises above the eave).
EYE = (14, 16, 11, 13)                                    # y0, y1, z0, z1 of the R7 eye in the west elevation
PINNACLE_TIPS = [(0, 9, 7), (0, 9, 17)]
# r4 (Oct 28): the lane is 5 x 8, so the door's brass keystone moved from r3's head (0, 11, 12) to the new head
# (0, 9, 12) - test_door_is_the_bright_spot pins the lantern it carries
MOVED_BRASS = PINNACLE_TIPS + [(0, 11, 12), (0, 9, 12)]
# r9 (critic on the r8 builds: r8b's wing a parapet billboard): r8a's half-hip wing, its apex at x 4 - the wing
# finials move from the front verge's top (0, 14, 0 / 24) to the apex (4, 14, 0 / 24)
MOVED_BRASS += [(0, 14, 0), (0, 14, 24)]
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
    """Round 2 must not regress what round 1's critic kept: frozen cell for cell against git HEAD's brass_t1.nbt.
    Retired in r8 (lovish, Oct 28 22:59: slate roofs, copper cone, brick walls - the red look rejected):
    test_keeps_cell_for_cell (froze r7's red R4 hall roofs and the R3 quartz cornice + candles, now slate courses and
    the R1 eave lip + gutter), test_hall_roofs_run_into_the_cone (froze z 7 to r7's red hall roof at z 6),
    test_the_eight_bay_candles (the candles stood on that cornice, where the gutter runs now)."""
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

    def test_the_eye_keeps_its_elevation(self):
        """The eye's cells and heart stay where r2 put them; r5 turned its ring calcite -> polished granite (the
        critic: +120 luma over the body split the cone) - EngineShedT1Detail.test_eye_is_granite_round_a_red_heart."""
        got = eye_roles(self.got)
        self.assertEqual(sorted(q for q, r in got.items() if r == "ring"), [(14, 12), (15, 11), (15, 13), (16, 12)])
        self.assertEqual(got[(15, 12)], "heart")

    def test_every_brass_cell_kept(self):
        """r9 (no unoxidised copper anywhere): a brass cell stays brass in its place and shape - the role is exposed
        copper since r9 (r1-r8 raw waxed cut copper), the tier ledges weathered / exposed."""
        def role(n):
            n = n.replace("waxed_cut_copper", "waxed_exposed_cut_copper").replace("weathered", "exposed")
            return n[:-len("_stairs")] + "_stairs" if n.endswith("_stairs") else n
        brass = sorted(q for q, (n, _) in self.want.items() if n in ("waxed_cut_copper", L.FAM["brass"][0])
                       and q not in MOVED_BRASS and q[1] >= 1)    # r1-r8's raw brass blocks or r9's exposed ones
        self.assertTrue(brass)
        moved = [q for q in brass if q not in self.got or role(self.got[q][0]) != role(self.want[q][0])
                 or self.got[q][1] != self.want[q][1]]
        self.assertEqual(moved, [])


# ==================================================================================== r8 gates (Oct 29 2026)
# lovish, Oct 28 22:42 "its not looking realistic enough", 22:59: red BRICK walls, grey SLATE roofs on the halls /
# shed / wings, weathered green COPPER on the cone; 23:44 "become a master architect". The gates of
# design/architecture_rules.md (G1 eaves, G3 no block over 25 %, G4 roof family != wall family, G6 roof_kit courses +
# ridge + verges + gutters, G7 base course + cap line) and the palette, measured with tools/schem_study.py on the
# built .nbt. Written before the r8 generator and run red on r7. Notes on the measures:
# - the y 0 pad is the ground (every lab has one), not a wall: G1 measures y >= 1, or the pad's footprint makes
#   every column "inside the walls" and no eave can ever oversail (r7's {} was partly this);
# - G3 counts the EXPOSED blocks (a 6-neighbour is air): the greybox's halls, wings, shed and cone are solid, so a
#   raw count is the hidden core's material, not what anyone sees (the 26 measured builds are hollow);
# - the camera faces are west (the front) and south (design/vehicle_labs/engine_shed_detail_grammar.md).
import schem_study as S  # noqa: E402
import roof_kit as R  # noqa: E402

PALE = ("andesite", "smooth_stone", "stone_brick", "diorite", "calcite", "quartz")


def pale(n):
    """Pale dressed stone (plinths, caps, string courses): not the dark stones whose names contain 'stone_brick'."""
    return any(k in n for k in PALE) and not any(k in n for k in ("blackstone", "deepslate", "nether"))


def brick(n):
    return n in ("bricks", "brick_stairs", "brick_slab", "brick_wall")


def in_cone(q):
    return 8 <= q[2] <= 16 and q[0] <= 8 and q[1] >= 7


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedR8Gates(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        out = os.path.join(cls.tmp.name, "brass_t1.nbt")
        L.write(L.detail(), out)
        cls.b = S.load(out)
        cls.n = {q: n for q, (n, _) in cls.b.blocks.items()}
        cls.up = S.Build({q: c for q, c in cls.b.blocks.items() if q[1] >= 1})
        cls.over = S.overhang_cells(cls.up)
        cls.roofs = S.roof_cells(cls.up)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def exposed(self):
        n = self.n
        return [q for q in n if q[1] >= 1 and any(
            (q[0] + a, q[1] + b, q[2] + c) not in n for a, b, c in
            ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]

    def top(self, x, z):
        ys = [y for (xx, y, zz) in self.n if (xx, zz) == (x, z)]
        return (x, max(ys), z) if ys else None

    def band(self, face):
        """The wall band between the plinth (y 1) and each run's cap, seen from a camera face: per column the front-
        most cell within 2 of the wall plane, openings (glass, lights) left out. West: the arcade z 1..6, 18..23 (cap
        y 6; the tower base and the door, z 7..17, are their own region, R6). South: the wings x 1..8 and the hall's
        corner x 9 (cap y 6, the west front's cap line carried round the corner), the shed end x 12..15 (cap y 4);
        x 10..11 is the yard."""
        out = {}
        # r9: the west wall plane steps back to x 2 (the kit pier's pilaster at x 1, its lantern and the railing at
        # x 0, under the eave that now oversails by 2) and the wing gables to z 23 (r8a's verge oversails z 24) - the
        # band is read from the wall planes back, so the railing and downpipes in front of them aren't the wall
        if face == "west":
            cols = [(z, 6, (1, 2, 3)) for z in list(range(1, 7)) + list(range(18, 24))]
        else:
            cols = [(x, 6 if x <= 9 else 4, (23, 22, 21)) for x in list(range(2, 10)) + list(range(12, 16))]
        for u, cap, depths in cols:
            for y in range(2, cap):
                for d in depths:
                    q = (d, y, u) if face == "west" else (u, y, d)
                    if q in self.n:
                        if any(k in self.n[q] for k in ("bars", "chain", "blackstone_brick_wall")):
                            continue                  # r9: a rail / pipe in front: the wall is behind it
                        if not any(k in self.n[q] for k in ("glass", "lantern", "candle")):
                            out[(u, y)] = self.n[q]
                        break
        return out

    # ---------------------------------------------------------------- G1
    def test_g1_eaves_and_verges_oversail_on_the_camera_faces(self):
        """Every camera-facing eave and verge 1 proud of its wall: the hall eaves' first course (x 0, y 7, over the
        gutter) between wing and buttress, the wings' front verges (their top cell at x 0), the hall's corner
        (9, 7, 24) over the corner stepped back to z 23, the works shed's south verge (z 24) and west eave (x 11).
        Not the wings' south end: the frozen outline (z 23 tops 12, z 24 tops 14) makes it a tall gable wall whose top
        IS the ridge - it cannot oversail inside the 17 x 25 plot."""
        want = [(0, 7, z) for z in (5, 6, 18, 19)]
        want += [self.top(0, z) for z in list(range(0, 5)) + list(range(20, 25))]
        want += [(9, 7, 24)]
        want += [self.top(x, 24) for x in range(11, 17)] + [(11, 5, z) for z in range(2, 25)]
        missing = [q for q in want if self.over.get(q, 0) < 1]
        self.assertEqual(missing, [])

    # ---------------------------------------------------------------- G3
    def test_g3_no_block_over_a_quarter_of_what_is_seen(self):
        ex = self.exposed()
        counts = {}
        for q in ex:
            counts[self.n[q]] = counts.get(self.n[q], 0) + 1
        name = max(counts, key=counts.get)
        self.assertLessEqual(counts[name] / len(ex), 0.25, (name, counts[name], len(ex)))

    # ---------------------------------------------------------------- G4 + the palette
    def test_g4_slate_and_copper_roofs_over_brick_walls(self):
        def fam(n):
            if R.family(n, L.SLATE) is not None or n in L.SLATE["ridge"] + L.SLATE["verge"]:
                return "slate"
            return "copper" if "copper" in n else n
        halls = [fam(self.n[q]) for q in self.roofs if not in_cone(q)]
        cone = [fam(self.n[q]) for q in self.roofs if in_cone(q)]
        walls = list(self.band("west").values()) + list(self.band("south").values())
        self.assertGreaterEqual(halls.count("slate") / len(halls), 0.6, sorted(set(halls)))
        self.assertGreaterEqual(cone.count("copper") / len(cone), 0.6, sorted(set(cone)))
        self.assertGreaterEqual(sum(map(brick, walls)) / len(walls), 0.6)
        self.assertNotIn("slate", [fam(n) for n in walls if brick(n)])

    def test_palette_red_terracotta_at_most_five_percent(self):
        v = list(self.n.values())
        self.assertLessEqual(v.count("red_terracotta") / len(v), 0.05)

    def test_walls_brick_with_a_structured_second_tone(self):
        """Brick 60-90 % of each camera face's wall band (lovish: ~80 / 20 with a second tone), the second tone in
        courses and bands - never a chequer: at most 10 % of the band's cells have no same-material 4-neighbour."""
        import facade_audit as FA
        for face in ("west", "south"):
            band = self.band(face)
            share = sum(map(brick, band.values())) / len(band)
            self.assertTrue(0.6 <= share <= 0.9, (face, share))
            lone = [k for k, n in band.items() if not any(
                FA.material(band.get((k[0] + a, k[1] + b), "")) == FA.material(n)
                for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
            self.assertLessEqual(len(lone) / len(band), 0.10, (face, lone))

    # ---------------------------------------------------------------- G6
    def courses(self, cells, along):
        """Assert roof_kit's course rule on a set of exposed roof cells: three slate tones, T0 55-72 %, and every line
        of cells along `along` (x or z) at one height free of chequers ('101', '202') and 4+ dark runs."""
        tone = {q: R.family(self.n[q], L.SLATE) for q in cells}
        tone = {q: t for q, t in tone.items() if t is not None}
        self.assertEqual(set(tone.values()), {0, 1, 2})
        share = sum(t == 0 for t in tone.values()) / len(tone)
        self.assertTrue(0.55 <= share <= 0.72, share)
        a = {"x": 0, "z": 2}[along]
        lines = {}
        for q in sorted(tone, key=lambda q: q[a]):
            lines.setdefault(tuple(v for i, v in enumerate(q) if i != a), []).append(q)
        for k, qs in lines.items():
            run, row = [], ""
            for q in qs:                                   # a gap in the line starts a new course
                if run and q[a] != run[-1][a] + 1:
                    row += "|"
                run.append(q)
                row += str(tone[q])
            for bad in ("101", "202", "1111"):
                self.assertNotIn(bad, row, (k, row))

    def test_g6_hall_roofs_in_slate_courses(self):
        """The hall roofs where they show between wing and tower (z 5..7, 17..19): every exposed cell in roof_kit's
        slate courses running along the ridge (z); the ridge cap on the hall ridge (x 5)."""
        ex = set(self.exposed())
        hall = [q for q in ex if q[0] <= 9 and q[1] >= 7 and (5 <= q[2] <= 7 or 17 <= q[2] <= 19) and not in_cone(q)]
        self.courses(hall, "z")
        for z in (5, 6, 18, 19):      # r9: the kit's cresting on the hall ridge - an iron comb on a pale coping
            self.assertIn(self.n[self.top(5, z)], ("iron_bars", "chain"), z)
            self.assertTrue(pale(self.n[(5, self.top(5, z)[1] - 1, z)]), z)

    def test_g6_wing_roofs_in_slate_courses(self):
        """The wing roofs (x 1..7 between the verges, z 1..4 / 20..23, y >= 8; the outer gable faces z 0 / 24 are
        walls): every exposed cell in slate courses running across the slope (x) - the camera's nearest roofs."""
        ex = set(self.exposed())
        wing = [q for q in ex if 1 <= q[0] <= 7 and q[1] >= 8 and (1 <= q[2] <= 4 or 20 <= q[2] <= 23)]
        self.courses(wing, "x")

    def test_g6_verges_ridges_and_gutters(self):
        """Dark verges on the wings' front edge (x 0; the brass finials at the ridge ends z 0 / 24 kept) and the
        shed's south end; the wings' ridges (y 14 at z 0 / 24) and the shed ridge in the ridge stone; copper gutters
        under the hall eaves (x 0, y 6: under the oversailing eave course, as roof_kit hangs them) and both shed
        eaves (y 4)."""
        # r9 (critic on the r8 builds: r8b's wing read as a parapet billboard): r8a's half-hip - its verge the gable's
        # own edge at z 0 / 24 (dark oak over a pale bracket line), the brass finial at its apex (x 4, y 14); the
        # front (x 0) is the hip's eave, over the gutter
        for x in [x for x in range(0, 10) if x != 4]:
            for z in (0, 24):
                self.assertIn(self.n[self.top(x, z)], L.SLATE["verge"], (x, z))
        for z in (0, 24):
            self.assertEqual(self.n[(4, 14, z)], L.FAM["brass"][0], z)
        for x in (11, 12, 15, 16):
            self.assertIn(self.n[self.top(x, 24)], L.SLATE["verge"], x)
        for z in range(2, 24):
            self.assertIn(self.n[self.top(13, z)], L.SLATE["ridge"], z)
        gut = [(0, 6, z) for z in (5, 6, 18, 19)] + [(x, 4, z) for x in (11, 16) for z in range(2, 24)]
        self.assertEqual([q for q in gut if self.n.get(q) != L.SLATE["gutter"]], [])

    def test_g6_cone_in_copper_courses_with_ledges(self):
        """The cone: roof_kit's copper tones on its body (T0 55-72 % of the copper-tone cells seen), dark ribs on the
        arrises (EngineShedT1Detail.test_upper_spire_is_one_field_and_arrises_dark), and R10 at each tier foot:
        the step row (y 11 / 15 / 19) a brass ledge (waxed cut copper stairs) across the face, the row under it
        carrying upside-down dark corbels on every other cell (the teeth of shadow) - the eye's cells aside."""
        ex = set(self.exposed())
        tones = [R.family(n, R.COPPER) for q, n in self.n.items() if in_cone(q) and q in ex
                 and R.family(n, R.COPPER) is not None]
        self.assertEqual(set(tones), {0, 1, 2})
        self.assertTrue(0.55 <= tones.count(0) / len(tones) <= 0.72, tones.count(0) / len(tones))
        eye = {(14, 12), (15, 11), (15, 12), (15, 13), (16, 12)}
        b = self.b.blocks
        for ys, fx, zs in ((11, 0, range(9, 16)), (15, 1, range(10, 15)), (19, 2, range(11, 14))):
            ledge = [(fx, ys, z) for z in zs if (ys, z) not in eye]
            self.assertTrue(ledge)
            # r9 (critic on the r8 builds: raw orange ledges): weathered / exposed copper stairs
            self.assertEqual([q for q in ledge if self.n.get(q) not in (L.FAM["brass"][1], L.FAM["weathered"][1])],
                             [], ys)
            row = [(fx, ys - 1, z) for z in zs if (ys - 1, z) not in eye]
            teeth = [q for q in row if self.n.get(q) == CORBEL and b[q][1].get("half") == "top"]
            self.assertGreaterEqual(len(teeth), len(row) // 2, ys)
            self.assertFalse(any((x, y, z + 1) in teeth for x, y, z in teeth), ys)

    # ---------------------------------------------------------------- G7
    def test_g7_base_course_and_cap_line_on_every_wall_run(self):
        """A pale base course at y 1 and a cap line on every camera-face wall run: the west arcade (x 1, z 1..6,
        18..23: base y 1, cap y 6, a gutter or a pale lip 1 proud over it at x 0); the south face - the wings (z 24,
        x 1..8: base y 1, the cap line y 6 carried round the corner from the west front), the hall's corner and the
        shed end, both stepped back to z 23 (x 9: cap y 6; x 12..15: cap y 4)."""
        bad = []
        # r9: the west wall plane is x 2 (the kit pier's pilaster at x 1), its cap the kit string course - a second-
        # tone course (y 6) with the pale drip 1 proud (x 1); the wings' gables z 1 / 23 (r8a's half-hip verge
        # oversails z 0 / 24), their cap carried round the corner the same way
        for z in list(range(2, 7)) + list(range(18, 23)):
            for q in ((L.WALL_X, 1, z), (L.WALL_X - 1, 6, z)):
                if not pale(self.n.get(q, "air")):
                    bad.append(q)
            lip = self.n.get((0, 6, z), "air")
            if not (pale(lip) or lip == L.SLATE["gutter"] or z <= 4 or z >= 20):
                bad.append((0, 6, z))
        runs = [(x, 23, 6) for x in range(2, 10)] + [(x, 23, 4) for x in range(12, 16)]
        for x, z, cap in runs:
            for q in ((x, 1, z), (x, cap, z + 1) if x <= 9 else (x, cap, z)):
                if not pale(self.n.get(q, "air")):
                    bad.append(q)
        self.assertEqual(bad, [])

if __name__ == "__main__":
    unittest.main()
