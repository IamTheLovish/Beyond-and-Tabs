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
        """r8 (lovish 22:59): the allowlist is brick walls with stone trim, roof_kit SLATE and COPPER, the dark trim,
        brass, the lights - r7's red-roof palette (mangrove, red nether shapes on roofs) is gone from it."""
        import roof_kit as R
        stray = sorted(set(self.names.values()) - L.ALLOWED)
        self.assertEqual(stray, [])
        kit = {n for m in (R.SLATE, R.COPPER) for f in m["tones"] for n in f}
        self.assertTrue(kit <= L.ALLOWED)
        self.assertFalse({n for n in self.names.values() if n.startswith("mangrove")})
        self.assertFalse([n for n in L.ALLOWED if any(k in n for k in L.BANNED)])

    def test_light_budget(self):
        """At most one small light per arcade bay; lanterns only the door's glow (r7: two, on the jamb tops - one on
        the keystone lit nothing the camera sees at dusk) and the shed's two lit slots (r6)."""
        v = list(self.names.values())
        self.assertLessEqual(sum(n.endswith("candle") for n in v), len(L.ARCH_Z))
        self.assertLessEqual(v.count("lantern"), 4)

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
        # r8: the hall's eave oversails to x 0 at y 7, level with the cone's foot course, so row 7 holds only the
        # door's look-through - the rule is counted from the eave up, as this docstring always said
        ys = sorted(y for y in w if y >= 8)
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
            # r8 (ref66, lovish 22:59): each tier change (y 11 15 19 23) IS a dark ledge course - the step's shadow
            # line; every other row still never a full dark course
            if y > L.LANE_H and len(face) >= 3 and y not in (11, 15, 19, 23):
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
        y = L.LANE_H + 2             # r8: over the head the face runs on in copper on its dark corbels (r7: red)
        row = {z: self.front_cell(y, z)[1] for z in range(9, 16)}
        self.assertEqual({z: n for z, n in row.items() if "copper" not in n and "blackstone" not in n}, {}, y)

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
        for z in (9, 15):
            self.assertEqual({self.names[(0, y, z)] for y in range(2, 8)}, {"polished_granite"}, z)
        head = sorted(q for q, n in mouth.items() if any(k in n for k in self.PALE))
        self.assertEqual(head, [(L.LANE_H + 1, 12)])
        yh = L.LANE_H + 1
        self.assertEqual([self.names[(0, yh, z)] for z in range(10, 15)],
                         ["polished_granite"] * 2 + ["quartz_block"] + ["polished_granite"] * 2)
        self.assertNotEqual(self.names.get((0, yh + 1, 12)), "lantern")   # r7: the lanterns stand on the jamb tops
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
        """r8 (r7's red needle rejected): the top tier (y 20..21) copper in the middle (z 12) between its dark ribs
        (z 11 / 13); the needle - y 22 every cell but the 1-wide core (x 4, z 12) a copper stair, tall half toward the
        core; y 23 the top ledge, dark stairs the same way; y 24 a blackstone-brick wall post; the brass tip y 25."""
        for y in (20, 21):
            self.assertIn("copper", self.front_cell(y, 12)[1], y)
            for z in (11, 13):
                self.assertIn("blackstone", self.front_cell(y, z)[1], (y, z))
        for y, k in ((22, "copper"), (23, "blackstone")):
            ring = [(q, self.rig.b[q]) for q in self.rig.b if q[1] == y and q != (4, y, 12) and self.rig.b[q][0] != "air"]
            self.assertTrue(ring)
            for (x, _, z), (n, p) in ring:
                self.assertTrue(n.endswith("_stairs") and k in n, (x, y, z, n))
                want = {"east" if x < 4 else "west" if x > 4 else None, "south" if z < 12 else "north" if z > 12 else None}
                self.assertIn(p["facing"], want - {None}, (x, y, z))
        self.assertEqual(self.names[(4, 24, 12)], "polished_blackstone_brick_wall")
        self.assertTrue(self.front_cell(25, 12)[1].startswith("waxed_cut_copper"))

    def test_no_granite_tick_stands_on_the_tiers(self):
        """r7 (critic on r6, in game: the ziggurat read is NOT resolution - the granite ticks at every tier corner put
        a pink rim on each 4-row box): on the tiers' step rows (y 11, 15, 19, 23) polished granite only as a slab on
        the tread, never a stair or a full block; the eye's ring (frozen) aside."""
        eye = {(1, 15, 11), (1, 15, 13)}
        for (x, y, z), n in self.names.items():
            if y in (11, 15, 19, 23) and x <= 8 and 8 <= z <= 16 and "granite" in n and (x, y, z) not in eye:
                self.assertTrue(n.endswith("_slab"), ((x, y, z), n))

    def test_shed_is_a_shallow_gable_with_two_lit_slots(self):
        """r8: the shed's gable in roof_kit slate at the 26.5 degree slate pitch - the eave a slab over the yard
        (x 10, y 5, oversailing the wall x 11), the ridge cap at y 6 over x 13..14, under the hall ridge (y 9); walls
        y 1..4; two lit 1 x 2 slots in the south wall (z 24); the stack clear above. (r6-r7: eaves at y 5 on the wall
        line x 11 / 16, a slab ridge - no overhang.)"""
        import roof_kit as R
        for z in range(2, 23):
            tops = {x: max(y for y in range(SIZE_T1[1]) if (x, y, z) in self.names) for x in range(10, 17)}
            self.assertEqual(tops, {10: 5, 11: 5, 12: 6, 13: 6, 14: 6, 15: 6, 16: 5}, z)
            self.assertEqual(self.rig.b[(10, 5, z)][1].get("type"), "bottom", z)
            self.assertEqual({self.names[(x, 6, z)] for x in (13, 14)}, {R.SLATE["ridge"][0]}, z)
            self.assertNotIn((10, 1, z), self.names)
        lit = sorted(q for q, n in self.names.items() if n == "lantern" and q[0] >= 11)
        self.assertEqual(lit, [(12, 2, 24), (15, 2, 24)])
        for x, _, z in lit:
            self.assertEqual([self.names.get((x, y, z)) for y in (3,)], [None])
        self.assertGreater(max(y for (x, y, z) in self.names if 13 <= x <= 14 and z <= 1), 6)

    def test_eye_is_granite_round_a_red_heart(self):
        """r4's calcite eye stood +120 luma over the cone body and cut the cone into two tiers; target +40..50: the
        ring polished granite, the red-nether heart kept (the elevation cells of r2's eye)."""
        ring = [self.front_cell(y, z)[1] for y, z in ((14, 12), (15, 11), (15, 13), (16, 12))]
        self.assertEqual(ring, ["polished_granite"] * 4)
        self.assertEqual(self.front_cell(15, 12)[1], "red_nether_bricks")

    # r8: test_upper_spire_is_red_and_arrises_dark retired (the red spire is rejected): its dark arrises are now
    # EngineShedT1R8.test_cone_is_green_copper_with_dark_ribs, its dark point and brass tip test_top_tier_matches_the_body

    def test_every_region_passes_the_facade_audit(self):
        import json
        import facade_audit as FA
        with open(REGIONS) as f:
            regions = json.load(f)
        rep = FA.audit(self.out, regions)
        self.assertEqual(len(rep), 13)
        # r8: a slate roof is 55-72 % its base tone by design (roof_kit / architecture_rules G6, measured on the studied
        # builds) - the checklist's 40 % cap on one material no longer applies to the roof regions seen from above;
        # their tiling is test_slate_roofs_are_courses_with_ridge_verge_and_gutters. Every other rule stands.
        # the cone is a copper roof (grammar B5) - its regions R7 / R8 likewise (EngineShedT1R8 tests its courses)
        roof = lambda r: (r["face"] == "up" and r["name"].startswith(("R4", "R5", "R9"))   # noqa: E731
                          or r["name"].startswith(("R7", "R8")))
        bad = ["%s: %s" % (r["name"], "; ".join(f for f in r["faults"] if not (roof(r) and "%" in f)))
               for r in rep if [f for f in r["faults"] if not (roof(r) and "%" in f)]]
        self.assertEqual(bad, [])


# ============================================================================== round 8 (Oct 29): roofs and walls
# lovish on r7 in game (Oct 28 22:30-22:42): "its missing enough detail... There isnt a cool roof tiling system... think
# more like an artichetcture professional", "its not looking realistic enough"; decided 22:59: red BRICK walls, grey
# SLATE roofs on the halls / shed / wings, weathered GREEN COPPER on the tower. Studies: design/vehicle_labs/
# engine_shed_detail_grammar.md (B1-B5, refs 45-70), design/architecture_rules.md (G1, G3, G4, G6, G7),
# design/vehicle_labs/studies/detail_recipes.md (R1 R2 eaves, R10 corbels, R12 copper ribs). Written before the r8 builder.
BAYS_R8 = (2, 4, 6, 18, 20, 22)          # the arcade's bays now the wall plane is x 1 (z 0 / 24 are the eaves' verges)
ROOFS_R8 = {                             # (x0, x1, z0, z1) of each slate roof's plan, the cone and the door's z 7 / 17 out
    "hall N": (0, 9, 3, 6), "hall S": (0, 9, 18, 21), "wing N": (0, 9, 0, 2), "wing S": (0, 9, 22, 24),
    "shed": (10, 16, 2, 22)}
STONE = ("polished_andesite", "smooth_stone", "stone_brick")
DARK_R8 = ("blackstone", "dark_oak")


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedT1R8(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import roof_kit
        import schem_study
        cls.R, cls.S = roof_kit, schem_study
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "brass_t1.nbt")
        cls.rig = L.write(L.detail(), cls.out)
        cls.blocks = {q: (n, {k: str(v) for k, v in p.items()}) for q, (n, p) in cls.rig.b.items() if n != "air"}
        cls.names = {q: n for q, (n, _) in cls.blocks.items()}

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def top(self, x, z):
        ys = [y for y in range(SIZE_T1[1]) if (x, y, z) in self.names]
        return (max(ys), self.names[(x, max(ys), z)]) if ys else (None, None)

    def tone(self, n, mats):
        return self.R.family(n, mats)

    # 1 -------------------------------------------------------------------------------------------- slate roofs
    def test_slate_roofs_are_courses_with_ridge_verge_and_gutters(self):
        """Every hall / wing / shed roof's top surface is roof_kit SLATE: its tones (T0 55-72 % of the tile cells, no
        chequer, no dark stripe of 4+ along a course), a ridge cap, dark verges; nothing else shows from above."""
        fixed = set(self.R.SLATE["ridge"]) | set(self.R.SLATE["verge"]) | {self.R.SLATE["gutter"]}
        for name, (x0, x1, z0, z1) in ROOFS_R8.items():
            tops = {(x, z): self.top(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}
            tops = {c: t for c, t in tops.items() if not (c[0] in (13, 14) and c[1] <= 1)}      # the stack
            other = {c: n for c, (y, n) in tops.items() if self.tone(n, self.R.SLATE) is None and n not in fixed}
            self.assertEqual(other, {}, name)
            tiles = [self.tone(n, self.R.SLATE) for _, n in tops.values() if self.tone(n, self.R.SLATE) is not None]
            self.assertTrue(0.55 <= tiles.count(0) / len(tiles) <= 0.72, (name, tiles.count(0) / len(tiles)))
            self.assertTrue(any(n in self.R.SLATE["ridge"] for _, n in tops.values()), name)
        for x in range(0, 11):                                 # the hall's courses run along z at one x and height
            for z0, z1 in ((3, 7), (17, 21)):
                row = "".join(str(self.tone(self.top(x, z)[1], self.R.SLATE)) for z in range(z0, z1 + 1)
                              if self.tone(self.top(x, z)[1], self.R.SLATE) is not None)
                self.assertNotIn("101", row, (x, z0))
                self.assertNotIn("1111", row, (x, z0))
        for z in (0, 24):                                      # the camera's south end and its mirror: dark verges
            verge = [self.top(x, z)[1] for x in range(0, 11) if x != 4]
            self.assertTrue(all(any(k in n for k in DARK_R8) for n in verge), (z, verge))

    def test_wet_eaves(self):
        """Weathering where water runs: the eave courses darker (T1) than the courses at the ridge, over all the
        slate roofs."""
        eave, high = [], []
        for name, (x0, x1, z0, z1) in ROOFS_R8.items():
            cols = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
            hs = {c: self.top(*c)[0] for c in cols if self.tone(self.top(*c)[1], self.R.SLATE) is not None}
            lo, hi = min(hs.values()), max(hs.values())
            eave += [self.tone(self.top(*c)[1], self.R.SLATE) for c, h in hs.items() if h == lo]
            high += [self.tone(self.top(*c)[1], self.R.SLATE) for c, h in hs.items() if h == hi]
        self.assertGreater(eave.count(1) / len(eave), high.count(1) / len(high))

    def test_copper_gutters_under_the_camera_eaves(self):
        """A waxed copper gutter under every west eave (hall x 0 y 6, wing x 0 y 7) and the shed's yard eave (x 10)."""
        g = self.R.SLATE["gutter"]
        for z in list(range(3, 7)) + list(range(18, 22)):
            self.assertEqual(self.names.get((0, 6, z)), g, z)
        for z in (0, 1, 2, 22, 23, 24):
            self.assertEqual(self.names.get((0, 7, z)), g, z)
        for z in range(2, 23):
            self.assertEqual(self.names.get((10, 4, z)), g, z)

    # 2 -------------------------------------------------------------------------------------------- the eaves
    def test_eaves_oversail_the_walls_the_camera_sees(self):
        """G1: the roof oversails its wall by >= 1 block on the west eaves (hall x 0 at z 3..6 / 18..21, wings x 0 at
        z 0..2 / 22..24) and on the south end (z 24, the wing's verge x 0..8 bar the apex and the hall's x 9..10) -
        schem_study.eave_cells on the built structure (r7: none)."""
        e = self.S.eave_cells(self.S.Build(self.blocks))
        want = [(0, z) for z in list(range(3, 7)) + list(range(18, 22)) + [0, 1, 2, 22, 23, 24]]
        want += [(x, 24) for x in (0, 1, 2, 3, 5, 6, 7, 8, 9)]
        missing = [c for c in want if (c[0], self.top(*c)[0], c[1]) not in e]
        self.assertEqual(missing, [])
        self.assertTrue(all(d >= 1 for d in e.values()))

    def test_walls_step_back_under_the_eaves(self):
        """Under the west eave (x 0) only thin things stand - downpipes, lights - above the plinth; the south end
        z 24 has no wall under its verge (the halls and wings' walls stop at z 23)."""
        for z in list(range(0, 7)) + list(range(18, SIZE_T1[2])):
            for y in range(1, 6):
                n = self.names.get((0, y, z))
                self.assertTrue(n is None or n == "lightning_rod" or n.endswith(("_wall", "candle", "lantern")),
                                (0, y, z, n))
        for x in range(1, 10):
            for y in range(1, 7):
                self.assertNotIn((x, y, 24), self.names, (x, y))

    # 3 -------------------------------------------------------------------------------------------- the cone
    def cone(self):
        rows = {}
        for (x, y, z), n in self.names.items():
            if y >= 7 and 8 <= z <= 16 and x <= 8 and not any(k in n for k in ("lantern", "candle")):
                rows.setdefault(y, set()).add((x, z))
        return rows

    EYE_R8 = {(1, 14, 12), (1, 15, 11), (1, 15, 13), (2, 15, 12), (2, 16, 12)}

    def test_cone_is_green_copper_with_dark_ribs(self):
        """The cone's faces in roof_kit COPPER courses, oxidized the most-used tone, over its visible cells from the
        door head up (y 10..22); the four arrises (each row's corner cells) dark from the eave to the top tier; the
        eye aside."""
        rows = self.cone()
        face, ribs = [], []
        for y, cells in rows.items():
            if y > 22:
                continue
            za, zb = min(z for _, z in cells), max(z for _, z in cells)
            for x, z in cells:
                col = [xx for xx, zz in cells if zz == z]
                q = (x, y, z)
                seen = any((x + a, y + b, z + c) not in self.names for a, b, c in
                           ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))
                if (z in (za, zb) and x in (min(col), max(col))) and y <= 21:
                    ribs.append((q, self.names[q]))
                elif q not in self.EYE_R8 and y >= L.LANE_H + 2 and seen:
                    face.append(self.names[q])
        self.assertEqual([q for q, n in ribs if not any(k in n for k in DARK_R8)], [])
        cu = [self.tone(n, self.R.COPPER) for n in face]
        hidden = [n for n, t in zip(face, cu) if t is None and "blackstone" not in n]
        self.assertLessEqual(len(hidden), len(face) // 10, hidden[:10])
        cu = [t for t in cu if t is not None]
        self.assertGreater(len(cu), 0.5 * len(face))          # the field; the structure lines (ledges, corbels) the rest
        self.assertEqual(max((0, 1, 2), key=cu.count), 0)
        self.assertGreaterEqual(cu.count(0) / len(cu), 0.5)

    def test_each_tier_change_is_a_dark_ledge(self):
        """ref66: at each tier change (y 11, 15, 19, 23) the tier's top row, where the next tier steps in, is a dark
        ledge course - every cell of the row the next tier doesn't cover (the eye's frozen cells aside) - inside the
        frozen outline, and the row under it carries dark corbels (upside-down stairs) every other cell (R10)."""
        rows = self.cone()
        for y in (11, 15, 19, 23):
            ledge = [(x, y, z) for x, z in rows[y] if (x, z) not in rows.get(y + 1, set())
                     and (x, y, z) not in self.EYE_R8]
            self.assertTrue(ledge, y)
            self.assertEqual([(q, self.names[q]) for q in ledge if not any(k in self.names[q] for k in DARK_R8)], [], y)
        for y in (10, 14, 18):
            w = 4 - (y - 10) // 4
            front = [(min(x for x in range(9) if (x, y, z) in self.names), y, z) for z in range(12 - w + 1, 12 + w)]
            corb = [q for q in front if self.blocks[q][0].endswith("_stairs") and self.blocks[q][1].get("half") == "top"
                    and "blackstone" in self.blocks[q][0]]
            self.assertGreaterEqual(len(corb), len(front) // 2 - 1, (y, front))
            self.assertLessEqual(len(corb), (len(front) + 1) // 2, (y, front))

    # 4 -------------------------------------------------------------------------------------------- the walls
    def test_brick_walls_on_a_stone_plinth_under_a_string_course(self):
        """G7: the west wall plane (x 1) and the south end (z 23): a stone base course at y 1 the whole run, the
        piers / wall in bricks, a stone string / cap course; red terracotta <= 5 % of the blocks; G4 the roofs' main
        family (slate) is not the walls' (brick)."""
        for z in list(range(1, 7)) + list(range(18, 24)):
            self.assertTrue(any(k in self.names.get((1, 1, z), "") for k in STONE), (1, 1, z))
            course = [self.names.get((1, y, z), "") for y in (5, 6)]
            self.assertTrue(any(any(k in n for k in STONE) for n in course), (z, course))
            if z % 2:
                self.assertEqual([self.names.get((1, y, z)) for y in (2, 3, 4)], ["bricks"] * 3, z)
        for x in range(1, 10):          # the south gable: the plinth, and the impost string course at y 5 (the
            #                             emblem sits over it, y 6..10)
            self.assertTrue(any(k in self.names.get((x, 1, 23), "") for k in STONE), (x, 1, 23))
            self.assertTrue(any(k in self.names.get((x, 5, 23), "") for k in STONE), (x, 5, 23))
        v = list(self.names.values())
        self.assertLessEqual(v.count("red_terracotta") / len(v), 0.05)
        walls = [self.names[(1, y, z)] for z in range(SIZE_T1[2]) for y in range(1, 7) if (1, y, z) in self.names]
        self.assertEqual(max(set(walls), key=walls.count), "bricks")

    def test_no_block_dominates_what_the_camera_sees(self):
        """G3 on the visible cells (a cell with an air neighbour; the solid core is never seen): no block over 25 %;
        and over every block, no worse than r7's 41 %."""
        vis = [n for (x, y, z), n in self.names.items() if y > 0 and any(
            (x + a, y + b, z + c) not in self.names for a, b, c in
            ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
        top = max(set(vis), key=vis.count)
        self.assertLessEqual(vis.count(top) / len(vis), 0.25, top)
        v = [n for (x, y, z), n in self.names.items() if y > 0]
        top = max(set(v), key=v.count)
        self.assertLessEqual(v.count(top) / len(v), 0.41, top)

    def test_pale_bracket_under_the_dark_verge_and_a_wet_course_under_the_eave(self):
        """Pass 2 (coordinator, from the studied factory / recipes R2 + R15): under the dark bargeboard of the end
        verges (z 0 / 24) a pale stone bracket line (stone brick stairs, upside-down), so the slate reads against the
        brick; the wall's top course under the west eave (x 1, y 6) the darker brick tone (red terracotta) - the wet
        line under the lip, structured, never scattered."""
        for z in (0, 24):
            br = [self.blocks[(x, y, z)] for x in range(1, 9) if x != 4 for y in range(7, 15)
                  if (x, y, z) in self.blocks and self.blocks[(x, y, z)][0] == "stone_brick_stairs"]
            self.assertGreaterEqual(len(br), 6, z)
            self.assertTrue(all(p.get("half") == "top" for _, p in br), z)
        for z in list(range(1, 7)) + list(range(18, 24)):
            self.assertEqual(self.names.get((1, 6, z)), "red_terracotta", z)

    # 5 -------------------------------------------------------------------------------------------- keeps working
    def test_every_arcade_bay_is_an_open_lit_recess(self):
        """The arcade bays (on the x 1 wall plane now): open at x 1 y 2..3 under a dark head, one candle at x 2."""
        for z in BAYS_R8:
            self.assertEqual([self.names.get((1, y, z)) for y in (2, 3)], [None, None], z)
            self.assertTrue(self.names.get((2, 2, z), "").endswith("candle"), z)
            self.assertIn("blackstone", self.names.get((1, 4, z), ""), z)


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
# r8: the brass caps r5-r7 put on the wing roofs' front ends (0, 14, 0 / 24) go with the red swept roofs - the wing's
# front end is its slate eave over a copper gutter now (the gutters carry the brass on the roofs)
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

    # r8: test_keeps_cell_for_cell (round 1's R4 red hall roofs and R3 quartz cornice + candles) and
    # test_hall_roofs_run_into_the_cone (z 7 / 17 the red hall roof of z 6 / 18) retired: lovish rejected that look
    # (Oct 28 22:30-22:59) - the roofs are roof_kit slate now (EngineShedT1R8), the cornice the eave and its gutter

    def test_the_eye_keeps_its_elevation(self):
        """The eye's cells and heart stay where r2 put them; r5 turned its ring calcite -> polished granite (the
        critic: +120 luma over the body split the cone) - EngineShedT1Detail.test_eye_is_granite_round_a_red_heart."""
        got = eye_roles(self.got)
        self.assertEqual(sorted(q for q, r in got.items() if r == "ring"), [(14, 12), (15, 11), (15, 13), (16, 12)])
        self.assertEqual(got[(15, 12)], "heart")

    def test_the_bay_candles(self):
        """r8: one candle in each arcade bay, on its sill inside the recess (x 2, y 2) - r7's candles stood on the
        cornice (x 0, y 7), where the eave is now; the end bays z 0 / 24 are the verges' open ends."""
        lit = sorted(q for q, (n, _) in self.got.items() if n.endswith("candle"))
        self.assertEqual(lit, [(2, 2, z) for z in BAYS_R8])

    def test_every_brass_cell_kept(self):
        brass = sorted(q for q, (n, _) in self.want.items() if n.startswith("waxed_cut_copper")
                       and q not in MOVED_BRASS)
        self.assertTrue(brass)
        self.assertEqual([q for q in brass if self.got.get(q) != self.want[q]], [])


if __name__ == "__main__":
    unittest.main()
