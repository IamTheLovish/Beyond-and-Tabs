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
OPTIONS = ("a", "b", "c", "c2", "c3", "a2", "ac")   # c2 / c3: the critic's pick (c) revised; a2 / ac: r11b, Oct 29
REVISED = ("c3", "a2", "ac")          # r11b (lovish 19:33: "can we make them better than even that?"): C', A', AC
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

    # ---- r11b: C' (c3), A' (a2), AC (ac). c3 = the fresh critic's pick (Oct 29 ~23:15: c = factory read 8, massing
    # 7) with its three required changes and the borrows; a2 = (a) with its faults fixed (the coordinator, 19:40: the
    # corner stacks fight, the teal tower is a dead cube); ac = (c)'s hierarchy with (a)'s broad north lights. Each rule
    # its own test, written before the builders -------------------------------------------------------------------------
    def rev(self, o):
        return solid(self.built[o][1]), M.OPTIONS[o]

    def ridge_and_peaks(self, n, m):
        t = tops(n)
        ridge = max(t[c] for c in cols(m["hall"]["box"]) if c in t and c != SPAWN_XZ)
        other = cols(m["hall"]["box"]) | cols(m["boiler"]["box"])
        peaks = {w["name"]: max(t[c] for c in cols(w["box"]) - other if c in t) for w in m["wings"]}
        return ridge, peaks

    def test_r11b_long_shed_with_a_bay_door(self):
        """C' (critic change 1) and A' (the coordinator: stretch to a long shed): the central hall stretched toward the
        gate - its gable end >= 7 blocks in front of every wing (c: 3) so the plan reads as a long shed with the bay
        door in its end; hall length >= 1.5 x its width; its ridge exactly 2 above the highest saw peaks. AC keeps (c)'s
        hierarchy instead: the hall the full depth, its ridge >= 4 over the wings."""
        for o in REVISED:
            n, m = self.rev(o)
            x0, x1, z0, z1 = m["hall"]["box"]
            self.assertGreaterEqual((x1 - x0 + 1), 1.5 * (z1 - z0 + 1), o)
            ridge, peaks = self.ridge_and_peaks(n, m)
            self.assertEqual(ridge, m["hall"]["ridge"], o)
            if o == "ac":
                self.assertEqual(x1, SX - 1)
                self.assertGreaterEqual(ridge - max(peaks.values()), 4, peaks)
                continue
            for w in m["wings"]:
                self.assertGreaterEqual(w["box"][0] - x0, 7, (o, w))
            self.assertEqual(ridge - max(peaks.values()), 2, (o, peaks))

    def test_r11b_c3_one_wing_dominates(self):
        """C' (critic change 2): the back-left (north) short sawtooth wing cut down one row - its peaks 1 lower than
        the south wing's - and the smaller of the two in plan."""
        n, m = self.rev("c3")
        _, pk = self.ridge_and_peaks(n, m)
        w = {w["name"]: w for w in m["wings"]}
        self.assertEqual(pk["south"] - pk["north"], 1, pk)
        self.assertLess(len(cols(w["north"]["box"])), len(cols(w["south"]["box"])))

    def test_r11b_gate_tower_beside_the_gate_above_the_ridge(self):
        """The gate tower directly beside the gate (3 x 3, touching the gate's jamb, its front <= 3 from the door
        plane), topping the ridge by 3-4; no copper over the door itself (lovish rejected a copper-topped block over
        the door; the coordinator: (a)'s teal-topped tower was a dead cube); both stacks taller than the tower."""
        for o in REVISED:
            n, m = self.rev(o)
            t = tops(n)
            x0, x1, z0, z1 = m["tower"]["box"]
            self.assertEqual((x1 - x0 + 1, z1 - z0 + 1), (3, 3), o)
            self.assertLessEqual(x0 - m["door_x"], 3, o)
            jz = (m["door_z"][0] - 1, m["door_z"][-1] + 1)
            self.assertTrue(z1 + 1 == jz[0] or z0 - 1 == jz[1], (o, m["tower"]["box"], jz))
            top = max(t[c] for c in cols(m["tower"]["box"]))
            self.assertIn(top - m["hall"]["ridge"], (3, 4), (o, top))
            self.assertFalse([q for q, v in n.items() if q[0] <= m["door_x"] + 2 and q[2] in m["door_z"]
                              and "copper" in v], o)
            self.assertTrue(all(s["top"] > top for s in m["stacks"]), o)

    def test_r11b_twin_stacks_grouped_on_a_boiler_house(self):
        """C' (critic change 3) and A' (the coordinator: (a)'s corner stacks fought for the landmark): the two stacks
        2-3 blocks apart, both on the same side of the hall, rising from one shared boiler-house block (a walled,
        roofed mass 6-10 tall round both shafts - not two plinths). The landmark ratio, as the refs measure (the
        Victorian Factory's stacks: 57 over a median roof of 29, ~2 x): the taller stack >= 1.5 x the hall ridge and
        >= 2 x the median roof top."""
        for o in REVISED:
            n, m = self.rev(o)
            t = tops(n)
            st = m["stacks"]
            self.assertEqual(len(st), 2, o)
            hz0, hz1 = m["hall"]["box"][2:]
            sides = {("n" if s["shaft"][3] < hz0 else "s" if s["shaft"][2] > hz1 else "?") for s in st}
            self.assertEqual(len(sides), 1, o)
            self.assertNotIn("?", sides, o)
            a, b = sorted((s["shaft"] for s in st), key=lambda s: (s[2], s[0]))
            gap = max(b[2] - a[3], b[0] - a[1]) - 1
            self.assertIn(gap, (2, 3), (o, a, b))
            house = cols(m["boiler"]["box"])
            shafts = cols(a) | cols(b)
            self.assertTrue(shafts <= house, o)
            ring = {(x + i, z + k) for x, z in shafts for i in (-1, 0, 1) for k in (-1, 0, 1)} - shafts
            self.assertTrue(all(6 <= t.get(c, -1) <= 10 for c in ring & house), (o, {c: t.get(c) for c in ring & house}))
            self.assertFalse([c for c in ring & house if n.get((c[0], t[c], c[1])) == M.BASE], o)
            self.assertTrue(all(n.get((x, 4, z)) == M.WALL for x, z in house - shafts if (x, 4, z) in n), o)
            top = max(s["top"] for s in st)
            roofs = sorted(h for c, h in t.items() if c not in shafts and h >= 3)
            self.assertGreaterEqual(top, 1.5 * m["hall"]["ridge"], o)
            self.assertGreaterEqual(top, 2 * roofs[len(roofs) // 2], (o, roofs[len(roofs) // 2]))

    def test_r11b_tall_gate_in_a_wide_bay_door_gable(self):
        """Borrowed from (a): the wide bay-door gable at the shed end and its tall ground-level gate - 7 wide, open from
        the ground >= 7 at the edges and >= 8 at the crown (a segmental head; a works gate about as tall as wide, the
        Midland loco-shed door), an iron head over every column, a glazed fan-light (>= 3 panes) in the gable over it."""
        for o in REVISED:
            n, m = self.rev(o)
            dx, dz = m["door_x"], m["door_z"]
            self.assertEqual(len(dz), 7, o)
            open_h = {z: min(y for y in range(1, H_MAX) if (dx, y, z) in n) - 1 for z in dz}
            self.assertTrue(all(open_h[z] >= 7 for z in dz), (o, open_h))
            self.assertTrue(all(open_h[z] >= 8 for z in dz[1:-1]), (o, open_h))
            self.assertGreater(open_h[dz[3]], open_h[dz[0]], o)
            for z in dz:
                self.assertEqual(n[(dx, open_h[z] + 1, z)], M.IRON, (o, z))
            glass = [q for q, v in n.items() if q[0] == dx and q[2] in dz and q[1] > open_h[q[2]] + 1 and "glass" in v]
            self.assertGreaterEqual(len(glass), 3, o)

    def test_r11b_c3_open_frame_water_tank(self):
        """C', borrowed from (b) only: an open-frame water tank in the yard - a tank body (>= 9 cells, y >= 7) on four
        legs from the ground, air under it between the legs."""
        n, m = self.rev("c3")
        x0, x1, z0, z1 = m["tank"]["box"]
        y0 = m["tank"]["y"]
        self.assertGreaterEqual(y0, 7)
        body_cells = [(x, y0, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if (x, y0, z) in n]
        self.assertGreaterEqual(len(body_cells), 9)
        for x in (x0, x1):
            for z in (z0, z1):
                self.assertTrue(all((x, y, z) in n for y in range(1, y0)), (x, z))
        under = [(x, y, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1) for y in range(1, y0 - 1)]
        self.assertTrue(under)
        self.assertFalse([q for q in under if q in n])

    def test_r11b_a2_and_ac_keep_the_broad_north_lights(self):
        """A' and AC keep (a)'s strength: the north-light field spread across the plot - saw teeth whose glass faces
        south (the camera), the sawtooth roofs >= 40 % of the roof plan; A' a field of >= 5 teeth in one section (as
        (a)); AC >= 2 teeth on each flank of the hall (its 9-wide hall, framing the 7-wide gate, leaves 8 a side: two
        teeth at the 4-block pitch)."""
        for o in ("a2", "ac"):
            n, m = self.rev(o)
            self.assertTrue(all(st["axis"] == "z" and st["face"] == 1 for st in m["sawtooth"]), o)
            if o == "a2":
                self.assertGreaterEqual(max(len(st["teeth"]) for st in m["sawtooth"]), 5, o)
            else:
                hz0, hz1 = m["hall"]["box"][2:]
                for side in (lambda u: u < hz0, lambda u: u > hz1):
                    self.assertGreaterEqual(sum(side(u) for st in m["sawtooth"] for u in st["teeth"]), 2, o)
            hall = cols(m["hall"]["box"])
            saw = set().union(*(cols(w["box"]) for w in m["wings"])) - hall - cols(m["boiler"]["box"])
            plan = {(x, z) for (x, y, z) in n if y >= 2}
            self.assertGreaterEqual(len(saw & plan) / len(plan), 0.4, o)

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
