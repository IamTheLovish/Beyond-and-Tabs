"""tools/engine_shed_massing.py: three greybox MASSINGS for the Brassworks T1 vehicle lab "Engine Shed", round r11
(Oct 29 2026). lovish 16:55: "looks blocky and toy", "doesnt necessarily look like a factory"; 16:58: rework the shape
into a recognisable factory - sawtooth north-light roofs, chimney stacks as the landmark, big hangar doors; the stepped
copper tower shrinks to a clock / water tower. Written BEFORE the generator. Run:
python3 -m unittest discover -s tools -p 'test_engine_shed_massing.py'

Plot kept: 17 (x, depth; the front / door is the west face x 0) x 25 (z) x <= 26. Vehicles leave through the lane
x 0..8, z 10..14, y 1..8; the spawn column (8, *, 12) stays air to the sky.
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
import schem_study as S  # noqa: E402
if HAVE_NUMPY:
    import engine_shed_massing as M  # noqa: E402
    import lab_vehicle_brass as L  # noqa: E402

SX, SZ, H_MAX = 17, 25, 26
LANE_X, LANE_Z, LANE_H = range(0, 9), range(10, 15), 8
SPAWN_XZ = (8, 12)
OPTIONS = "abc"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def solid(b):
    return {q: n for q, (n, _) in b.blocks.items() if n not in S.AIR}


def tops(cells, skip=()):
    t = {}
    for (x, y, z) in cells:
        if (x, z) not in skip:
            t[(x, z)] = max(t.get((x, z), -1), y)
    return t


def cols(box):
    x0, x1, z0, z1 = box
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedMassing(unittest.TestCase):
    built = {}

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        for o in OPTIONS:
            path = os.path.join(cls.tmp.name, "r11_%s.nbt" % o)
            cls.built[o] = (L.write(M.build(o), path), S.load(path), path)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_envelope(self):
        for o in OPTIONS:
            rig, b, _ = self.built[o]
            self.assertEqual((rig.size[0], rig.size[2]), (SX, SZ), o)
            self.assertLessEqual(rig.size[1], H_MAX, o)
            n = solid(b)
            self.assertTrue(all(0 <= x < SX and 0 <= z < SZ and 0 <= y < H_MAX for x, y, z in n), o)
            self.assertFalse([(x, z) for x in range(SX) for z in range(SZ) if (x, 0, z) not in n], o + ": foundation")

    def test_lane_and_spawn_clear(self):
        for o in OPTIONS:
            n = solid(self.built[o][1])
            busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
            self.assertFalse(busy, o + ": lane")
            col = [y for y in range(1, H_MAX + 1) if (SPAWN_XZ[0], y, SPAWN_XZ[1]) in n]
            self.assertFalse(col, o + ": spawn column")

    def test_hangar_door_on_the_lane(self):
        """A real door, not a gap: an opening >= 5 wide x 6 tall in a wall plane facing the lane, jambs both sides,
        a head over every column of it."""
        for o in OPTIONS:
            n = solid(self.built[o][1])
            dx = M.OPTIONS[o]["door_x"]
            self.assertIn(dx, LANE_X, o)
            for y in range(1, 7):
                self.assertIn((dx, y, LANE_Z[0] - 1), n, "%s: north jamb y %d" % (o, y))
                self.assertIn((dx, y, LANE_Z[-1] + 1), n, "%s: south jamb y %d" % (o, y))
            for z in LANE_Z:
                self.assertTrue(any((dx, y, z) in n for y in range(LANE_H + 1, LANE_H + 5)), "%s: head z %d" % (o, z))
            opening = [(y, z) for y in range(1, 7) for z in LANE_Z if (dx, y, z) not in n]
            self.assertEqual(len(opening), 6 * 5, o)
            # nothing in front of the door: the face is open to the west edge
            self.assertFalse([q for q in n if q[0] < dx and q[2] in LANE_Z and 1 <= q[1] <= LANE_H], o)

    def test_stack_is_the_tallest_non_tower_element(self):
        for o in OPTIONS:
            n = solid(self.built[o][1])
            meta = M.OPTIONS[o]
            self.assertGreaterEqual(len(meta["stacks"]), 1, o)
            stack_cols = set().union(*(cols(s["box"]) for s in meta["stacks"]))
            tower_cols = cols(meta["tower"]["box"]) if meta.get("tower") else set()
            t_all = tops(n)
            stack_top = max(t_all[c] for c in stack_cols if c in t_all)
            for s in meta["stacks"]:                     # each stack really reaches its claimed top
                x0, x1, z0, z1 = s["shaft"]
                self.assertEqual(max(t_all[(x, z)] for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)),
                                 s["top"], o)
                self.assertLessEqual((x1 - x0 + 1) * (z1 - z0 + 1), 9, o + ": a stack is slender (<= 3 x 3)")
            rest = tops(n, skip=stack_cols | tower_cols)
            self.assertLess(max(rest.values()), stack_top, o + ": a non-tower, non-stack element tops the stacks")
            if tower_cols:                               # the clock / water tower is subordinate to the landmark
                self.assertLess(max(t_all[c] for c in tower_cols if c in t_all), stack_top, o + ": tower")
            # the stack is a landmark: at least 1.6 x the main hall's eave
            self.assertGreaterEqual(stack_top, 1.6 * meta["eave"], o)

    def test_sawtooth_north_lights_where_claimed(self):
        claimed = [o for o in OPTIONS if M.OPTIONS[o].get("sawtooth")]
        self.assertGreaterEqual(len(claimed), 2)
        for o in claimed:
            n = solid(self.built[o][1])
            for st in M.OPTIONS[o]["sawtooth"]:
                axis, at, face, teeth = st["axis"], st["at"], st["face"], st["teeth"]
                self.assertGreaterEqual(len(teeth), 2, o)

                def q(u, y):
                    return (at, y, u) if axis == "z" else (u, y, at)
                lit = 0
                for u in teeth:                          # each tooth: >= 2 glass cells in its steep face, open on the
                    rows = [y for y in range(1, H_MAX) if "glass" in n.get(q(u, y), "")]   # claimed side
                    exposed = [y for y in rows if q(u + face, y) not in n]
                    self.assertGreaterEqual(len(exposed), 2, "%s tooth %s %d: %s %s" % (o, axis, u, rows, exposed))
                    lit += 1
                # the profile along the run is a saw: one peak per tooth
                prof = [max([y for y in range(H_MAX) if q(u, y) in n] or [-1]) for u in range(st["run"][0],
                                                                                         st["run"][1] + 1)]
                peaks = sum(1 for i in range(len(prof)) if prof[i] >= max(prof[max(0, i - 1):i + 2])
                            and (i + 1 >= len(prof) or prof[i + 1] < prof[i]))
                self.assertGreaterEqual(peaks, len(teeth), "%s: profile %s" % (o, prof))
                self.assertEqual(lit, len(teeth))

    def test_base_course(self):
        """Base - body - cap: no body brick touches the ground (a base course runs under every wall)."""
        for o in OPTIONS:
            n = solid(self.built[o][1])
            self.assertFalse([q for q, v in n.items() if q[1] == 1 and v == M.WALL], o)

    def test_three_distinct_silhouettes(self):
        prof = {o: tops(solid(self.built[o][1])) for o in OPTIONS}
        for a, b in (("a", "b"), ("a", "c"), ("b", "c")):
            diff = sum(1 for c in prof[a] if abs(prof[a][c] - prof[b].get(c, -1)) >= 2)
            self.assertGreater(diff, 0.3 * SX * SZ, "%s vs %s" % (a, b))

    def test_build_is_sound_and_committed(self):
        import preview_structure as PS
        for o in OPTIONS:
            rig, _, path = self.built[o]
            L.check(rig, path)
            committed = os.path.join(ROOT, M.OUT % o)
            self.assertTrue(os.path.exists(committed), committed)
            want, got = PS.load_nbt(committed), PS.load_nbt(path)
            pal_w, pal_g = want["palette"], got["palette"]
            cw = {tuple(b["pos"]): str(pal_w[b["state"]]["Name"]) for b in want["blocks"]}
            cg = {tuple(b["pos"]): str(pal_g[b["state"]]["Name"]) for b in got["blocks"]}
            self.assertEqual(cw, cg, o + ": committed .nbt is stale - rerun tools/engine_shed_massing.py")


if __name__ == "__main__":
    unittest.main()
