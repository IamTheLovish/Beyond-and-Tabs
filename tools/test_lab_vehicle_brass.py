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
            12, 16, 19, 23, 25, 23, 19, 16, 12,           # the tower: one steep pyramid, a 1-wide point at y 25
            # (first written 12 15 19 22 25: a floor() guess; no straight pyramid voxelizes to it - shape_kit's
            # stair pass rounds the flanks up near the point. r3's own rule, the apex one block over the plot,
            # gives this row: a 2-row last step, as r3's 19 / 21)
            9, 9, 9, 9, 9,                                # south arcade hall
            10, 12, 14]                                   # south wing
SIZE_T1 = [17, 26, 25]                                    # x (depth), height, z (front width)
APEX = (4, 25, 12)                                        # the tower's point: x, y, z
LANE = (range(0, 9), range(9, 16), 10)                    # the great door: x, z, height (y 1..10), through the tower


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
        up = {q for q in self.cells if q[1] > max(FRONT_T1[3:8]) and abs(q[2] - az) <= 4 and q[0] <= 2 * ax}
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
    "R4 hall roof N": (0, 10, 7, 10, 3, 7), "R4 hall roof S": (0, 10, 7, 10, 17, 21),
    "R3 cornice + candles N": (0, 0, 6, 7, 0, 6), "R3 cornice + candles S": (0, 0, 6, 7, 18, 24),
    "R7 eye": (2, 3, 14, 16, 11, 13),
    "R9 shed lit slot": (11, 16, 1, 4, 23, 24),
}


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

    def test_the_eight_bay_candles(self):
        lit = sorted(q for q, (n, _) in self.got.items() if n.endswith("candle"))
        self.assertEqual(lit, [(0, 7, z) for z in L.ARCH_Z])

    def test_every_brass_cell_kept(self):
        brass = sorted(q for q, (n, _) in self.want.items() if n.startswith("waxed_cut_copper"))
        self.assertTrue(brass)
        self.assertEqual([q for q in brass if self.got.get(q) != self.want[q]], [])


if __name__ == "__main__":
    unittest.main()
