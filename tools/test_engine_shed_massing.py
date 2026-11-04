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
from collections import Counter
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
OPTIONS = ("a", "b", "c", "c2", "af")   # c2: the critic's pick (c) revised; af: lovish's pick (a) fixed, Oct 29 19:5x
# r11b's C' / A' / AC (c3, a2, ac) stay in tools/engine_shed_massing.py but out of these tests: rejected by a blind
# critic Oct 29 (ranked below old a / c: C' 4 / 3, A' 5 / 4, AC 6 / 5); lovish picked old a.
REVISED = ()
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
        for o in "abc":
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
                if "peaks" in st:                        # c2: teeth tall enough to read as saw, not lean-tos
                    for u in teeth:
                        top = max(y for y in range(H_MAX) if q(u, y) in n)
                        self.assertIn(top, range(st["peaks"][0], st["peaks"][1] + 1), "%s tooth %d top %d" % (o, u, top))

    def test_base_course(self):
        """Base - body - cap: no body brick touches the ground (a base course runs under every wall)."""
        for o in OPTIONS:
            n = solid(self.built[o][1])
            self.assertFalse([q for q, v in n.items() if q[1] == 1 and v == M.WALL], o)

    # ---- c2 (Oct 29, fresh critic on a / b / c): keep (c), fix the turret, the stacks, the door, the teeth ----------
    def test_c2_no_turret_over_the_door(self):
        """lovish rejected a copper-topped block over the door: nothing of the tower over the gate gable; a clock, if
        any, is a 3 x 3 at the front-south corner, top <= 16."""
        n = solid(self.built["c2"][1])
        meta = M.OPTIONS["c2"]
        over_door = [q for q, v in n.items() if q[0] <= 3 and 8 <= q[2] <= 16 and "copper" in v]
        self.assertFalse(over_door)
        t = meta.get("tower")
        if t:
            x0, x1, z0, z1 = t["box"]
            self.assertEqual((x1 - x0 + 1, z1 - z0 + 1), (3, 3))
            self.assertLessEqual(x1, 4)
            self.assertGreaterEqual(z0, 20)
            self.assertLessEqual(max(tops(n)[c] for c in cols(t["box"])), 16)

    def test_c2_asymmetric_stacks(self):
        """22 and 19 (24 crops at the RTS frame top); the shorter one rises from a low boiler-house mass mid-depth on
        the south flank, not from the ground."""
        n = solid(self.built["c2"][1])
        t = tops(n)
        st = sorted(M.OPTIONS["c2"]["stacks"], key=lambda s: -s["top"])
        self.assertEqual([s["top"] for s in st], [22, 19])
        x0, x1, z0, z1 = st[1]["shaft"]
        self.assertTrue(5 <= x0 and x1 <= 11 and z0 >= 17, st[1]["shaft"])
        ring = {(x, z) for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2)} - cols(st[1]["shaft"])
        self.assertTrue(all(6 <= t.get(c, -1) <= 10 for c in ring), {c: t.get(c) for c in ring})
        self.assertFalse([c for c in ring if n.get((c[0], t[c], c[1])) == M.BASE], "a boiler house, not a plinth")

    def test_c2_big_arched_door(self):
        """7 wide x 8 tall, (b)'s segmental iron head: the middle columns open higher than the edges; jambs and a head;
        the gable over it is not a blank brick face (a glazed fan-light over the arch)."""
        n = solid(self.built["c2"][1])
        dx = M.OPTIONS["c2"]["door_x"]
        self.assertEqual(dx, 0)
        door_z = range(9, 16)
        self.assertFalse([(y, z) for y in range(1, 9) for z in door_z if (dx, y, z) in n])
        for y in range(1, 9):
            self.assertIn((dx, y, 8), n)
            self.assertIn((dx, y, 16), n)
        self.assertIn((dx, 9, 9), n)                     # the edges' head at 9 ...
        self.assertNotIn((dx, 9, 12), n)                 # ... the crown higher: a segmental arch
        for z in door_z:
            head = min(y for y in range(9, H_MAX) if (dx, y, z) in n)
            self.assertEqual(n[(dx, head, z)], M.IRON, z)
        glass = [q for q, v in n.items() if q[0] == dx and q[1] >= 11 and q[2] in door_z and "glass" in v]
        self.assertGreaterEqual(len(glass), 4)
        self.assertFalse([q for q in n if q[0] < dx])

    # ---- r11b: C' (c3), A' (a2), AC (ac) - RETIRED. Rejected by blind critic Oct 29, ranked below old a / c; lovish
    # picked old a (19:5x). Their rules (a hall projecting 7 before the wings, the tower beside the gate, two stacks
    # 2 apart on one boiler house, a 7-wide gate, C''s water tank, A' / AC's north-light spread) were the r11b critic-
    # prescribed changes that made c read worse (two stacks 2 apart fused into one post; the tower beside the gate
    # fought the door). The builders stay in the tool (SPECS) for the record.

    # ---- af: lovish's pick (old a), small fixes only (coordinator, Oct 29 ~20:00, from the blind critic's diagnosis of
    # a: the tower a detached post, the south stack's pale plinth box on a camera face, a blank left flank; lovish
    # 20:00: "the towers on this building need more refinement"). Written before build_af. -------------------------------
    def af(self):
        return solid(self.built["af"][1]), M.OPTIONS["af"]

    def test_af_keeps_a(self):
        """Everything but the three fixes is old a's: the erecting bay (box, eave, ridge), the hangar door cell for cell,
        the sawtooth teeth (same columns, glass to the south) and both stacks' shaft tops (25, 22)."""
        n, m = self.af()
        na, ma = solid(self.built["a"][1]), M.OPTIONS["a"]
        x0, x1, z0, z1 = m["bay"]
        door = lambda q: q[0] == 0 and 8 <= q[2] <= 16 and q[1] <= 10   # noqa: E731
        self.assertEqual({q: v for q, v in n.items() if door(q)}, {q: v for q, v in na.items() if door(q)})
        self.assertEqual(sorted(s["top"] for s in m["stacks"]), sorted(s["top"] for s in ma["stacks"]))
        ta, tf = tops(na), tops(n)
        tx0, tx1, tz0, tz1 = m["tower"]["box"]
        own = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if (x, z) != SPAWN_XZ
               and not (tx0 - 1 <= x <= tx1 + 1 and tz0 - 1 <= z <= tz1 + 1)]   # the tower's cornice oversails it
        self.assertEqual(max(tf[c] for c in own), max(ta[c] for c in own))

    def test_af_tower_engaged_with_the_hall(self):
        """Fix 1: the tower is no detached post - its shaft shares a wall with the erecting bay (a tower cell beside a
        bay wall cell, y 2..9) and with the shop (beside a shop wall cell); no air gap between them at ground level.
        A's height relationship: top 3-4 over the bay ridge, under both stacks."""
        n, m = self.af()
        t = tops(n)
        tx0, tx1, tz0, tz1 = m["tower"]["box"]
        tower = {(x, z) for x in range(tx0, tx1 + 1) for z in range(tz0, tz1 + 1)}
        bx0, bx1, bz0, bz1 = m["bay"]
        bay = {(x, z) for x in range(bx0, bx1 + 1) for z in range(bz0, bz1 + 1)}
        shop = {(x, z) for x in range(4, SX) for z in range(SZ)} - bay - tower

        def touches(region):
            return any((x + a, z + c) in region and (x + a, 5, z + c) in n and (x, 5, z) in n
                       for x, z in tower for a, c in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        self.assertTrue(touches(bay), "the tower shares no wall with the bay")
        self.assertTrue(touches(shop), "the tower shares no wall with the shop")
        top = max(t[c] for c in tower)
        ridge = max(t[c] for c in bay if c != SPAWN_XZ and not (tx0 - 1 <= c[0] <= tx1 + 1 and tz0 - 1 <= c[1] <= tz1 + 1))
        self.assertIn(top - ridge, (3, 4), (top, ridge))
        self.assertTrue(all(s["top"] > top for s in m["stacks"]))

    def test_af_tower_stages(self):
        """lovish 20:00 ("the towers need more refinement"): base / shaft / clock stage / roof - a pale base course
        at y 1-2, a brick shaft, a clock stage of a different face material, a cornice that oversails the shaft by 1,
        and green copper only on the roof (above the cornice), nowhere else on the tower."""
        n, m = self.af()
        tx0, tx1, tz0, tz1 = m["tower"]["box"]
        st = m["tower"]
        col = lambda y: [n.get((x, y, z), "") for x in range(tx0, tx1 + 1) for z in range(tz0, tz1 + 1)]   # noqa: E731
        self.assertTrue(all(v and "brick" not in v for v in col(1)), col(1))
        shaft = [v for y in range(3, st["clock"][0]) for v in col(y)]
        self.assertGreaterEqual(sum("brick" in v for v in shaft) / len(shaft), 0.6)
        clock = Counter(v for y in range(*st["clock"]) for v in col(y))
        self.assertNotEqual(clock.most_common(1)[0][0], Counter(shaft).most_common(1)[0][0])
        cy = st["cornice"]
        ring = [(x, cy, z) for x in range(tx0 - 1, tx1 + 2) for z in range(tz0 - 1, tz1 + 2)
                if not (tx0 <= x <= tx1 and tz0 <= z <= tz1)]
        self.assertGreaterEqual(sum(q in n for q in ring), 10, "the cornice oversails")
        cop = [q for q, v in n.items() if "copper" in v and tx0 - 1 <= q[0] <= tx1 + 1 and tz0 - 1 <= q[2] <= tz1 + 1]
        self.assertTrue(cop)
        self.assertTrue(all(q[1] > cy for q in cop), sorted(cop)[:4])

    def test_af_stacks_rise_from_boiler_houses_off_the_camera_faces(self):
        """Fix 2: no pale plinth box on a camera face - each stack rises from a brick boiler house (the cells round its
        lowest shaft course: brick walls, tops 6-10 under a slate roof, not andesite), its shaft >= 2 blocks in from
        the west and south faces; no polished-andesite cell above y 2 within 2 of either stack."""
        n, m = self.af()
        t = tops(n)
        for s in m["stacks"]:
            x0, x1, z0, z1 = s["base"]
            self.assertGreaterEqual(x0, 2, s)
            self.assertLessEqual(z1, SZ - 3, s)
            ring = {(x, z) for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2)
                    if 0 <= x < SX and 0 <= z < SZ} - {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}
            self.assertTrue(all(6 <= t.get(c, -1) <= 10 for c in ring), {c: t.get(c) for c in ring})
            self.assertTrue(all(n.get((c[0], 4, c[1])) == M.WALL for c in ring if (c[0], 4, c[1]) in n
                                and n.get((c[0], 4, c[1])) != "glass"), [n.get((c[0], 4, c[1])) for c in ring])
            pale = [q for q, v in n.items() if v == M.BASE and q[1] > 2 and x0 - 2 <= q[0] <= x1 + 2
                    and z0 - 2 <= q[2] <= z1 + 2]
            self.assertFalse(pale)

    def test_af_stacks_step_in_with_a_crown(self):
        """lovish 20:00: the stacks are no constant square posts with a cube cap - each one's shaft narrows at least
        once up its height (a step-in) and its top course is a crown wider than the shaft just under it (oversails)."""
        n, m = self.af()
        for s in m["stacks"]:
            x0, x1, z0, z1 = s["base"]
            widths = []
            for y in range(s["above_roof"], s["top"] + 1):
                cells = [(x, z) for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2) if (x, y, z) in n]
                widths.append(len(cells))
            steps = [i for i in range(1, len(widths) - 2) if widths[i] < widths[i - 1]]
            self.assertTrue(steps, widths)
            self.assertGreater(widths[-2], widths[-3], ("crown", widths))

    def test_af_left_flank_has_bays(self):
        """Fix 3: the blank left flank (the shop's west wall either side of the bay, x 4) gets windows in the hall's
        rhythm - no 3 brick columns in a row at window height (y 3), >= 3 windows."""
        n, m = self.af()
        wx = m["flank_x"]
        wins = 0
        for zs in (range(0, m["bay"][2]), range(m["bay"][3] + 1, SZ)):
            run = 0
            for z in zs:
                if m["tower"]["box"][2] - 1 <= z <= m["tower"]["box"][3] + 1:
                    run = 0
                    continue
                v = n.get((wx, 3, z), "")
                if "glass" in v:
                    wins += 1
                    run = 0
                else:
                    run += 1
                    self.assertLess(run, 3, (wx, z))
        self.assertGreaterEqual(wins, 3)

    def test_three_distinct_silhouettes(self):
        prof = {o: tops(solid(self.built[o][1])) for o in OPTIONS}
        for a, b in (("a", "b"), ("a", "c"), ("b", "c")):     # (af is a fixed: its silhouette is a's on purpose)
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
