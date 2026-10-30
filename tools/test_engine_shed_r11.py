"""Engine Shed T1 r11: camera-truth tests on the r11 build (tools/engine_shed_r11.py detail(): lovish's pick, old a, with
the coordinator's three small fixes - tools/engine_shed_massing.py build_af - then detailed). Written BEFORE the r11
generator and run red on r10 (engine_shed_r11.detail as a stub returning lab_vehicle_brass.detail()).
Run: python3 -m pytest -q tools/test_engine_shed_r11.py

Every test casts the eyes stills' rays (building_gates.shoot: EyesDirector scene 15's rts, rts_close, 34 and dusk) into
the built .nbt and asks what the first block on each ray is - and, for colour, what that block LOOKS like in real eyes
stills (tools/camera_colour.py's table, design/vehicle_labs/camera_colours.json, measured per block and face on eyes
stills; a block not yet photographed fails the coverage check until it is).

Why: r10's stills (Oct 29) - the slate band reads right and the brick shows, but the green copper reads as saturated
MINT with black blotches (toy); lovish 16:55 "blocky and toy", "doesnt necessarily look like a factory"; 20:00 on old a:
"the towers on this building need more refinement". lovish's palette (Oct 28): red brick walls, grey slate roofs,
weathered green copper on towers. The r10 tests still marked expectedFailure "R11" (tools/test_engine_shed_r10.py: the
yard, dead planes, small life, the stack as landmark, roof ornament) are asserted here on the r11 build, thresholds
unchanged; r10's file stays the record of the r10 build.
"""
import os
import sys
import tempfile
import unittest
from collections import Counter

SZ = 25

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    import numpy  # noqa: F401
    HAVE_NUMPY = True
except ImportError:
    HAVE_NUMPY = False
import building_gates as G  # noqa: E402
import camera_colour as CC  # noqa: E402
import gen_blocks as GB  # noqa: E402
import schem_study as S  # noqa: E402
if HAVE_NUMPY:
    import engine_shed_r11 as E  # noqa: E402
    import lab_vehicle_brass as L  # noqa: E402
    from test_engine_shed_r10 import (BRICK, PAVING, ROOF_BAND, SLATE_FAMILY, Pictures, base, green,  # noqa: E402
                                      light, sub_block)

# Weathered green copper on camera, BOTH edges: the area colour of the copper roof (the mean of its pixels as the
# stills show them - what the eye sees at RTS distance): hue green to blue-green; saturation under r10's mint (0.41-0.44
# on camera) and over a grey that no longer reads as copper; darker streaking, no black blotches.
COPPER_HUE = (130.0, 185.0)
COPPER_SAT = (0.16, 0.30)
STREAK = (0.15, 0.45)           # share of the roof's pixels >= 20 % darker than its median value
BLOTCH = 0.05                   # at most this share under 45 % of the median value (r10's black blotches)
KNOWN = 0.95                    # the share of pixels whose block / face the camera table has measured
FACES2 = ("west", "south")


def full(n):
    return S.glyph(n) == "#"


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EngineShedR11(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "brass_t1_r11.nbt")
        cls.rig = L.write(E.detail(), cls.out)
        cls.p = Pictures(cls.out)
        cls.m = E.META
        cls.table = CC.load()

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    # ---------------------------------------------------------------- helpers: where the tower and the stacks stand
    def in_tower(self, q, pad=1):
        x0, x1, z0, z1 = self.m["tower"]
        return x0 - pad <= q[0] <= x1 + pad and z0 - pad <= q[2] <= z1 + pad

    def in_stack(self, q, s, pad=1):
        x0, x1, z0, z1 = s["base"]
        return x0 - pad <= q[0] <= x1 + pad and z0 - pad <= q[2] <= z1 + pad and q[1] >= self.m["stack_from"]

    def roof_cell(self, h):
        """A visible roof cell: a hit from above on the top of its column, y >= 6, off the tower and the stacks, not a
        thin piece, light or glass, with 3+ blocks in its column (a gantry is no roof)."""
        x, y, z = h[0]
        return (h[2] == "up" and y >= 6 and self.p.top.get((x, z)) == y and not self.in_tower(h[0])
                and not any(self.in_stack(h[0], s) for s in self.m["stacks"]) and S.glyph(h[1]) not in "|*g="
                and sum((x, yy, z) in self.p.n for yy in range(1, y + 1)) >= 3)

    # ---------------------------------------------------------------- copper
    def test_copper_only_on_the_tower_roof(self):
        """lovish's copper is the tower's roof (the coordinator, 20:00: "weathered green copper only on its roof"):
        every copper cell in the build stands over the tower's cornice, within its oversail."""
        bad = sorted(q for q, n in self.p.n.items() if "copper" in n
                     and not (self.in_tower(q) and q[1] > self.m["cornice"]))
        self.assertEqual(bad, [])

    def test_copper_roof_reads_weathered_on_camera(self):
        """The tower roof (every cell over the cornice within the tower's oversail) as the rts_close and 34 stills show
        it, each pixel coloured as the eyes stills show its block and face: the area colour (the mean of the pixels)
        hue 130-185 and saturation 0.16-0.30 - both edges (r10's 0.41-0.44 read as mint; under 0.16 it is grey, not
        copper); 15-45 % of the pixels streaked >= 20 % darker than the median value; <= 5 % blotch (< 45 %)."""
        import colorsys
        import numpy as np
        for still in ("rts_close", "34"):
            hits = [h for h in self.p.hits[still].values() if self.in_tower(h[0]) and h[0][1] > self.m["cornice"]]
            self.assertGreater(len(hits), 100, (still, "tower-roof pixels"))
            px = [CC.lookup(self.table, h[1], h[2]) for h in hits]
            self.assertGreaterEqual(sum(1 for q in px if q) / len(px), KNOWN, (still, "not photographed yet", Counter(
                (h[1], h[2]) for h in hits if not CC.lookup(self.table, h[1], h[2])).most_common(5)))
            hsv = np.array([q for q in px if q])
            rgb = np.array([colorsys.hsv_to_rgb(h / 360.0, s, v) for h, s, v in hsv]).mean(0)
            hue, sat, _ = colorsys.rgb_to_hsv(*rgb)
            self.assertTrue(COPPER_HUE[0] <= hue * 360 <= COPPER_HUE[1], (still, round(hue * 360)))
            self.assertTrue(COPPER_SAT[0] <= sat <= COPPER_SAT[1], (still, "sat", round(sat, 3)))
            med = float(np.median(hsv[:, 2]))
            streak = float(np.mean(hsv[:, 2] <= 0.8 * med))
            blotch = float(np.mean(hsv[:, 2] < 0.45 * med))
            self.assertTrue(STREAK[0] <= streak <= STREAK[1], (still, "streak", round(streak, 3)))
            self.assertLessEqual(blotch, BLOTCH, (still, "blotch", round(blotch, 3)))

    # ---------------------------------------------------------------- slate roofs
    def roof_components(self, still):
        """The seen roof cells grouped into roofs: plan-adjacent cells whose tops differ by <= 1 (a slope steps 1 per
        block; a wall drop or a saw tooth's glass face splits two roofs)."""
        roofs = {h[0]: h[1] for h in self.p.hits[still].values() if self.roof_cell(h)}
        plan = {(q[0], q[2]): q for q in roofs}
        comps, seen = [], set()
        for s in plan:
            if s in seen:
                continue
            todo, comp = [s], set()
            while todo:
                c = todo.pop()
                if c in comp:
                    continue
                comp.add(c)
                for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nb = (c[0] + d[0], c[1] + d[1])
                    if nb in plan and nb not in comp and abs(plan[nb][1] - plan[c][1]) <= 1:
                        todo.append(nb)
            seen |= comp
            comps.append({plan[c] for c in comp})
        return roofs, comps

    def test_every_roof_in_the_slate_band(self):
        """Every roof the rts and rts_close cameras show (each connected roof of >= 30 seen pixels) averages pack luma
        0.28-0.36 (ROOF_BAND, both edges - r8b's 0.22 read as a hole, r9's 0.45 as concrete), >= 90 % of its pixels
        measured by the pack, slate family only (the copper is the tower's)."""
        bad = []
        for still in ("rts", "rts_close"):
            hits = [h for h in self.p.hits[still].values() if self.roof_cell(h)]
            _, comps = self.roof_components(still)
            self.assertGreaterEqual(len(comps), 3, still)
            for comp in comps:
                names = [h[1] for h in hits if h[0] in comp]
                if len(names) < 30:
                    continue
                lum = [GB.TARGETS[base(n)]["luma"] for n in names if base(n) in GB.TARGETS]
                if len(lum) < 0.9 * len(names):
                    bad.append((still, min(comp), "unmeasured", Counter(n for n in names
                                                                        if base(n) not in GB.TARGETS).most_common(3)))
                    continue
                mean = sum(lum) / len(lum)
                if not ROOF_BAND[0] <= mean <= ROOF_BAND[1]:
                    bad.append((still, min(comp), round(mean, 3)))
                other = Counter(base(n) for n in names if base(n) not in SLATE_FAMILY)
                if other:
                    bad.append((still, min(comp), dict(other)))
        self.assertEqual(bad, [])

    def test_no_roof_chequer_in_the_pictures(self):
        """The roofs as rts_close sees them, on the plan, by block id: no chequer (building_gates.alternations)."""
        roofs, _ = self.roof_components("rts_close")
        bad = G.alternations({(q[0], q[2]): n for q, n in roofs.items()})
        self.assertEqual(len(bad), 0, sorted(bad)[:12])

    def test_no_green_on_the_roofs(self):
        """r10's test_f_roof_ornament_at_most_2_percent, on r11: green copper at most 2 % of the slate roofs' pixels."""
        for still in ("rts", "rts_close"):
            names = [h[1] for h in self.p.hits[still].values() if self.roof_cell(h)]
            self.assertLessEqual(sum(green(n) for n in names) / max(1, len(names)), 0.02, still)

    def test_north_light_glazing_seen_from_the_rts_camera(self):
        """The sawtooth roofs' glazing faces the camera: in the rts still >= 4 separate runs of glass seen above the
        eave line (y >= 6), each under a slate roof cell within 2 above it - the north lights, not wall windows."""
        p = self.p
        glass = {h[0] for h in p.hits["rts"].values() if S.glyph(h[1]) == "g" and h[0][1] >= 6}
        lit = {q for q in glass if any(base(p.n.get((q[0], q[1] + k, q[2]), "")) in SLATE_FAMILY for k in (1, 2))}
        runs, seen = 0, set()
        for q in sorted(lit):
            if q in seen:
                continue
            runs += 1
            todo = [q]
            while todo:
                c = todo.pop()
                if c in seen or c not in lit:
                    continue
                seen.add(c)
                todo += [(c[0] + a, c[1] + b, c[2] + d) for a, b, d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0),
                                                                        (0, 0, 1), (0, 0, -1))]
        self.assertGreaterEqual(runs, 4, sorted(lit)[:10])

    # ---------------------------------------------------------------- stacks and tower
    def test_stacks_are_the_tallest_and_both_seen_from_rts(self):
        """lovish 16:58: the stacks are the landmark (re-targeted to old a, the coordinator 19:5x: no grouping rule).
        Each stack's top is higher than every other cell of the build; in the rts still each stack shows >= 3 courses
        of shaft over its boiler house; in the 34 still each one's crown course is seen; each smokes (a campfire in its
        flue). (The rts still is framed on the footprint's middle: at a's back-north corner it crops above ~y 17, so
        the north stack's crown - a's 25 - is out of that frame; it was in old a's stills too.)"""
        p = self.p
        rts = {h[0] for h in p.hits["rts"].values()}
        s34 = {h[0] for h in p.hits["34"].values()}
        for s in self.m["stacks"]:
            shaft = {q[1] for q in rts if self.in_stack(q, s) and q[1] > self.m["stack_from"] + 5}
            self.assertGreaterEqual(len(shaft), 3, (s, sorted(shaft)))
            crown = [q for q in s34 if self.in_stack(q, s) and q[1] >= s["top"] - 1]
            self.assertTrue(crown, (s, "crown not seen in 34"))
            self.assertTrue([q for q, n in p.n.items() if "campfire" in n and self.in_stack(q, s, 0)], s)
        rest = [q[1] for q in p.n if not any(self.in_stack(q, s) for s in self.m["stacks"])]
        self.assertLess(max(rest), min(s["top"] for s in self.m["stacks"]))

    def stack_widths(self, s, stills=("34", "rts_close")):
        """{y: seen width} of a stack above the boiler-house roof: per row, the extent of its seen cells across the
        view (x + z spread - the 3/4 camera sees two faces)."""
        cells = {h[0] for st in stills for h in self.p.hits[st].values() if self.in_stack(h[0], s)
                 and h[0][1] > self.m["stack_from"]}
        out = {}
        for y in sorted({q[1] for q in cells}):
            row = [q for q in cells if q[1] == y]
            out[y] = (max(q[0] for q in row) - min(q[0] for q in row) + 1) + (max(q[2] for q in row) -
                                                                              min(q[2] for q in row) + 1)
        return out

    def test_stacks_step_in_and_have_an_overhanging_crown(self):
        """lovish 20:00 / the coordinator: no constant square post with a cube cap - as the 34 and rts_close stills show
        each stack: its seen width narrows at least once up the shaft (a step-in), and its crown course (the widest
        row in its top 3) is wider than the shaft row under it (an oversailing crown)."""
        for s in self.m["stacks"]:
            w = self.stack_widths(s)
            ys = sorted(w)
            self.assertGreaterEqual(len(ys), 8, (s, w))
            shaft = [y for y in ys if y < s["top"] - 2]
            steps = [y for a, y in zip(shaft, shaft[1:]) if w[y] < w[a]]
            self.assertTrue(steps, (s["top"], w))
            crown = max((y for y in ys if y >= s["top"] - 2), key=lambda y: (w[y], y))
            below = max(y for y in ys if y < crown and w[y] < w[crown]) if any(
                y < crown and w[y] < w[crown] for y in ys) else None
            self.assertIsNotNone(below, (s["top"], w))
            self.assertTrue(all(w[y] <= w[below] for y in ys if below - 3 <= y < crown), (s["top"], w))

    def test_stacks_banded(self):
        """towers.md (the Victorian Factory's stack: a band every ~15 of 57 courses; Industrial Area: rings at 38 and
        44): >= 2 projecting bands between the stack's step-in and its crown - a course that sticks out of the shaft
        under it and over it."""
        p = self.p
        for s in self.m["stacks"]:
            sx0, sx1, sz0, sz1 = s["shaft"]
            bands = []
            for y in range(self.m["stack_from"] + 2, s["top"] - 2):
                ring = [(x, y, z) for x in range(sx0 - 1, sx1 + 2) for z in range(sz0 - 1, sz1 + 2)
                        if not (sx0 <= x <= sx1 and sz0 <= z <= sz1)]
                if sum(q in p.n for q in ring) >= 6 and all(
                        not any((x, y + d, z) in p.n for (x, _, z) in ring) for d in (-1, 1)):
                    bands.append(y)
            self.assertGreaterEqual(len(bands), 2, (s["top"], bands))

    def test_tower_shows_three_stages(self):
        """The tower reads as base / shaft / clock stage / roof (lovish 20:00): its seen cells in the rts and 34 stills,
        row by row, by material family (pale stone, brick, glass, dark iron, copper / slate roof) - >= 3 different
        families in vertical runs of >= 2 rows each, and the cornice oversail seen."""
        def fam(n):
            if "copper" in n or base(n) in SLATE_FAMILY and "stone_brick" not in n:
                return "roof"
            if S.glyph(n) in "g*":
                return "glass"
            if "brick" in n and "stone" not in n and "blackstone" not in n and "deepslate" not in n:
                return "brick"
            if "blackstone" in n or "deepslate" in n:
                return "dark"
            return "pale"
        for still in ("rts", "34"):
            cells = {h[0] for h in self.p.hits[still].values() if self.in_tower(h[0], 0) or
                     (self.in_tower(h[0]) and h[0][1] >= self.m["cornice"])}
            rows = {}
            for q in cells:
                rows.setdefault(q[1], Counter())[fam(self.p.n[q])] += 1
            seq = [rows[y].most_common(1)[0][0] for y in sorted(rows)]
            runs = []
            for f in seq:
                if runs and runs[-1][0] == f:
                    runs[-1][1] += 1
                else:
                    runs.append([f, 1])
            stages = {f for f, k in runs if k >= 2}
            self.assertGreaterEqual(len(stages), 3, (still, runs))
            ring = [q for q in cells if q[1] == self.m["cornice"] and not self.in_tower(q, 0)]
            self.assertTrue(ring, (still, "cornice oversail"))

    def test_tower_and_stack_faces_have_relief(self):
        """No tower or stack face has a run of more than 3 seen full blocks without relief - along a row (as r10's dead
        plane rule) and up a column (a band, a slit, a course) - in the rts_close and 34 stills."""
        bad = []
        for still in ("rts_close", "34"):
            for face, a in (("west", 2), ("south", 0)):
                cells = {h[0] for h in self.p.hits[still].values() if h[2] == face and full(self.p.n[h[0]]) and (
                    self.in_tower(h[0], 0) or any(self.in_stack(h[0], s, 0) and h[0][1] > self.m["stack_from"]
                                                  for s in self.m["stacks"]))}
                for axis in (a, 1):
                    for q in cells:
                        prev = list(q)
                        prev[axis] -= 1
                        if tuple(prev) in cells:
                            continue
                        k, r = list(q), 0
                        while tuple(k) in cells:
                            r += 1
                            k[axis] += 1
                        if r > 3:
                            bad.append((still, face, "row" if axis != 1 else "column", q, r))
        self.assertEqual(sorted(bad)[:10], [], len(bad))

    # ---------------------------------------------------------------- the gate
    def test_tall_gate_aperture_seen(self):
        """A tall gate the 34 still looks into. In the front (west) face plane, x 0: an opening from the ground >= 5
        columns wide, every column open >= 7 tall under a head; and the 34 still sees into it - >= 300 sampled pixels hit
        cells >= 3 deep inside the gate (x 3..8, between its jambs), the dark mouth of the shed. Re-targeted to old a
        (lovish's pick, the coordinator: keep A's tall gate aperture): its gate is the lane, 5 wide x 8 tall; the 7-wide
        rule encoded r11b's C', rejected by the blind critic (Oct 29)."""
        p = self.p
        open_z = [z for z in range(SZ) if all((0, y, z) not in p.n for y in range(1, 8))
                  and any((0, y, z) in p.n for y in range(8, 12))]
        best, run = [], []
        for z in open_z:
            run = run + [z] if run and z == run[-1] + 1 else [z]
            best = max(best, run, key=len)
        self.assertGreaterEqual(len(best), 5, open_z)
        inside = [h for h in p.hits["34"].values() if 3 <= h[0][0] <= 8 and best[0] <= h[0][2] <= best[-1]]
        self.assertGreaterEqual(len(inside), 300, len(inside))

    # ---------------------------------------------------------------- walls and windows
    def wall_cells(self, still, face):
        """Visible wall cells of a camera face, y 1..eave, at the footprint; openings, lights and roof left out."""
        p = self.p
        return {h[0] for h in p.hits[still].values() if h[2] == face and 1 <= h[0][1] <= self.m["eave"]
                and p.near_fp(h[0]) and S.glyph(h[1]) not in "g*|=v" and h[0] not in p.roof
                and not self.in_tower(h[0]) and h[0][1] > 0}

    def test_brick_on_every_camera_face(self):
        """r10 test (a): red brick >= 35 % of the wall cells each camera face shows in the 34 and rts_close stills."""
        for still in ("34", "rts_close"):
            for face in FACES2:
                w = self.wall_cells(still, face)
                self.assertGreater(len(w), 40, (still, face))
                share = sum(self.p.n[q] in BRICK for q in w) / len(w)
                self.assertGreaterEqual(share, 0.35, (still, face, round(share, 3),
                                                      Counter(self.p.n[q] for q in w).most_common(5)))

    def camera_windows(self):
        p = self.p

        def out_west(q):
            return all((x, q[1], q[2]) not in p.n or S.glyph(p.n[(x, q[1], q[2])]) in "|v" for x in range(q[0]))

        def out_south(q):
            return all((q[0], q[1], z) not in p.n or S.glyph(p.n[(q[0], q[1], z)]) in "|v"
                       for z in range(q[2] + 1, p.size[2]))
        panes = {q for q, n in p.n.items() if S.glyph(n) == "g" and 1 <= q[1] <= self.m["eave"]}
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
            if any(out_west(q) or out_south(q) for q in g):
                groups.append(g)
        return groups

    def test_every_camera_window_shows_a_light_behind(self):
        """r10 test (e): every window on the camera faces shows glass to the 34 camera, and >= 25 % of those glass
        pixels have a light as the first block behind the glass (dusk)."""
        groups = self.camera_windows()
        self.assertGreaterEqual(len(groups), 6)
        hits = [h for h in self.p.hits["34"].values() if S.glyph(h[1]) == "g"]
        bad = []
        for g in groups:
            mine = [h for h in hits if h[0] in g]
            lit = [h for h in mine if h[3] and light(h[3][1])]
            if not mine or len(lit) < 0.25 * len(mine):
                bad.append((min(g), len(mine), len(lit)))
        self.assertEqual(bad, [], "%d windows" % len(groups))

    def test_window_glass_sits_within_1_of_its_frame(self):
        """r10 test (e2): building_gates.window_glass_set - no camera-face pane sunk 2+ or proud of its frame."""
        p = self.p

        def outward(q):
            return (all((x, q[1], q[2]) not in p.n or S.glyph(p.n[(x, q[1], q[2])]) in "|v" for x in range(q[0])) or
                    all((q[0], q[1], z) not in p.n or S.glyph(p.n[(q[0], q[1], z)]) in "|v"
                        for z in range(q[2] + 1, p.size[2])))
        bad = [w for w in G.window_glass_set(p.b) if outward(w[0]) and w[0][1] <= self.m["eave"]]
        self.assertEqual(bad, [])

    # ---------------------------------------------------------------- the yard (r10's R11 list)
    def test_apron_seen(self):
        """r10 (d): >= 50 paved ground cells (y 0) in each of rts and 34."""
        for still in ("rts", "34"):
            paved = [q for q in self.p.cells(still) if q[1] == 0 and self.p.n[q] in PAVING]
            self.assertGreaterEqual(len(paved), 50, (still, len(paved)))

    def test_rails_to_the_plot_edge_seen(self):
        """r10 (d): >= 8 rail cells seen in each of rts and 34, one on the plot's edge."""
        for still in ("rts", "34"):
            rails = [q for q in self.p.cells(still) if self.p.n[q].endswith("rail")]
            self.assertGreaterEqual(len(rails), 8, (still, len(rails)))
            edge = [q for q in rails if q[0] in (0, self.p.size[0] - 1) or q[2] in (0, self.p.size[2] - 1)]
            self.assertTrue(edge, still)

    def test_chassis_under_a_gantry_seen(self):
        """r10 (d): 4+ grindstone wheels, a frame on them, a beam 2+ over the frame; in rts and 34 >= 2 wheels,
        >= 3 frame cells and >= 2 beam cells seen."""
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
            vis = set(p.cells(still))
            self.assertGreaterEqual(len(wheels & vis), 2, (still, "wheels"))
            self.assertGreaterEqual(len(frame & vis), 3, (still, "frame"))
            self.assertGreaterEqual(len(beam & vis), 2, (still, "beam"))

    def test_coal_stage_seen(self):
        """r10 (d): >= 4 coal blocks on a raised stage, >= 3 of them seen in each of rts and 34."""
        coal = {q for q, n in self.p.n.items() if n == "coal_block" and q[1] >= 2}
        self.assertGreaterEqual(len(coal), 4)
        self.assertTrue(all((x, y - 1, z) in self.p.n for x, y, z in coal))
        for still in ("rts", "34"):
            self.assertGreaterEqual(len(coal & set(self.p.cells(still))), 3, still)

    def test_two_lamp_posts_seen(self):
        """r10 (d): 2+ lamp posts (a light on 2+ stacked thin posts from the ground), both seen, outside the lane."""
        p = self.p
        posts = [(x, y, z) for (x, y, z), n in p.n.items() if light(n) and y >= 3
                 and all(S.glyph(p.n.get((x, yy, z), "")) == "|" for yy in range(1, y))
                 and not (x in L.LANE_X and z in L.LANE_Z)]
        for still in ("rts", "34"):
            vis = set(p.cells(still))
            self.assertGreaterEqual(len([q for q in posts if q in vis]), 2, (still, posts))

    # ---------------------------------------------------------------- anti-toy (r10's gates, on r11)
    def faces(self, still):
        out = {}
        for h in self.p.hits[still].values():
            out.setdefault(h[2], Counter())[h[0]] += 1
        return out

    def test_sub_block_share_on_every_camera_face(self):
        """r10 anti-toy 1: thin / partial blocks >= 15 % of the cells each still shows on each side it sees."""
        bad = []
        for still in ("rts", "rts_close", "34"):
            for face, cells in self.faces(still).items():
                if len(cells) < 30:
                    continue
                share = sum(sub_block(self.p.n[q]) for q in cells) / len(cells)
                if share < 0.15:
                    bad.append((still, face, round(share, 3)))
        self.assertEqual(bad, [])

    def test_no_dead_planes(self):
        """r10 anti-toy 2: no run of more than 3 seen full blocks along a wall row (west: along z; south: along x), on
        the walls (y >= 1): the y 0 course is the plot's ground layer, whose edge the stamped pad shows."""
        runs = []
        for still in ("rts_close", "34"):
            fc = self.faces(still)
            for face, a in (("west", 2), ("south", 0)):
                cells = {q for q in fc.get(face, ()) if full(self.p.n[q]) and q[1] >= 1}
                for q in cells:
                    prev = list(q)
                    prev[a] -= 1
                    if tuple(prev) in cells:
                        continue
                    k, r = list(q), 0
                    while tuple(k) in cells:
                        r += 1
                        k[a] += 1
                    if r > 3:
                        runs.append((still, face, q, r))
        self.assertEqual(sorted(runs)[:12], [], len(runs))

    def test_bevelled_silhouette(self):
        """r10 anti-toy 3: a seen full block with air above and air on its west or south side is <= 5 % of the seen
        top cells in rts and 34."""
        p = self.p
        for still in ("rts", "34"):
            tops = [q for q in p.cells(still) if (q[0], q[1] + 1, q[2]) not in p.n and q[1] >= 1]
            bare = [q for q in tops if full(p.n[q]) and ((q[0] - 1, q[1], q[2]) not in p.n
                                                         or (q[0], q[1], q[2] + 1) not in p.n)]
            self.assertLessEqual(len(bare) / max(1, len(tops)), 0.05, (still, len(bare), len(tops), sorted(bare)[:8]))

    def test_small_scale_life_on_the_facades(self):
        """r10 anti-toy 4: >= 40 seen cells of shutters, chains, lanterns, grilles, buttons, pots and vines on the west
        and south faces (y 1..8, rts_close and 34), in >= 4 of those kinds."""
        kinds = {"trapdoor": "_trapdoor", "chain": "chain", "lantern": "lantern", "bars": "iron_bars",
                 "button": "_button", "pot": "potted_", "vine": "vine"}
        cells = {h[0] for st in ("rts_close", "34") for h in self.p.hits[st].values()
                 if h[2] in FACES2 and 1 <= h[0][1] <= 8}
        seen = Counter(k for q in cells for k, key in kinds.items() if key in self.p.n[q])
        self.assertGreaterEqual(sum(seen.values()), 40, dict(seen))
        self.assertGreaterEqual(len(seen), 4, dict(seen))

    # ---------------------------------------------------------------- round 2: the round-1 critic's diagnosis (fresh
    # fable on before / after stills, Oct 29: RTS 6, close 5, towers 6), each fault as what the stills show
    def test_r2_no_glow_block_seen_bare(self):
        """Critic r1: "orange fire-looking blocks sit in the right wing roof" - the lit rooms' glow block (shroomlight)
        seen straight, not through glass, in any still: none."""
        bare = sorted({h[0] for st in G.STILLS for h in self.p.hits[st].values() if h[1] == "shroomlight"})
        self.assertEqual(bare, [])

    def test_r2_roof_tones_run_in_courses(self):
        """Critic r1: "every roof plane has random 1- to 3-block patches ... use long continuous ridge / eave bands
        instead of random speckle". In rts_close, each roof's cells whose tone class (dark < 0.27 <= mid < 0.40 <=
        pale, pack luma) differs from the roof's main class all lie on one of its courses that is entirely that class
        (a band along the eave or the ridge), never scattered."""
        def cls(n):
            v = GB.TARGETS.get(base(n), {}).get("luma")
            return "?" if v is None else "dark" if v < 0.27 else "mid" if v < 0.40 else "pale"
        roofs, comps = self.roof_components("rts_close")
        bad = []
        for comp in comps:
            if len(comp) < 6:
                continue
            c = {q: cls(roofs[q]) for q in comp}
            main = Counter(c.values()).most_common(1)[0][0]
            for q, k in c.items():
                if k == main:
                    continue
                course = [r for r in comp if r[1] == q[1]]
                if any(c[r] != k for r in course):
                    bad.append((q, k, roofs[q]))
        self.assertEqual(sorted(bad)[:10], [], len(bad))

    def test_r2_gate_interior_reads_lit(self):
        """Critic r1: "the big vehicle door is still a flat black void". The pixels the 34 still shows inside the gate's
        opening (x 1..9 between its jambs, y 0..8 - the lane; the roof over it is not the opening) are >= 25 % a light,
        a rail, or a floor cell within 3 blocks (Manhattan, light level >= 12) of a light block - what the renderer
        shows lit. (As first written the region took in the bay roof over the lane, y > 8: fixed before it was ever
        green, threshold unchanged.)"""
        lights = [q for q, n in self.p.n.items() if light(n)]
        inside = [h for h in self.p.hits["34"].values() if 1 <= h[0][0] <= 9 and 10 <= h[0][2] <= 14
                  and h[0][1] <= 8]
        self.assertGreater(len(inside), 200)

        def lit(q):
            return any(abs(q[0] - a[0]) + abs(q[1] - a[1]) + abs(q[2] - a[2]) <= 3 for a in lights)
        good = [h for h in inside if light(h[1]) or h[1].endswith("rail") or lit(h[0])]
        self.assertGreaterEqual(len(good) / len(inside), 0.25, (len(good), len(inside)))

    def test_r2_tower_roof_is_no_chequer(self):
        """Critic r1: "the corner tower's copper roof is a checkerboard of green and black blocks". The tower roof's top
        cells on the plan (rts_close), by block id: no chequer (building_gates.alternations)."""
        top = {}
        for h in self.p.hits["rts_close"].values():
            q = h[0]
            if self.in_tower(q) and q[1] > self.m["cornice"] and q[1] >= top.get((q[0], q[2]), (-1,))[0]:
                top[(q[0], q[2])] = (q[1], h[1])
        bad = G.alternations({c: v[1] for c, v in top.items()})
        self.assertEqual(sorted(bad), [])

    def test_r2_stack_bands_are_not_dark_drums(self):
        """Critic r1: the stacks' "alternating stone / black rings, like stacked drums". No dark-family (blue brick,
        blackstone, deepslate) cell of a stack below its crown course is seen from above in rts or rts_close."""
        bad = []
        for st in ("rts", "rts_close"):
            for h in self.p.hits[st].values():
                for s in self.m["stacks"]:
                    if h[2] == "up" and self.in_stack(h[0], s, 0) and h[0][1] < s["top"] - 1 and (
                            "blackstone" in h[1] or "deepslate" in h[1]):
                        bad.append((st, h[0], h[1]))
        self.assertEqual(sorted(set(bad))[:8], [])

    # ---------------------------------------------------------------- round 3: the round-2 critic's diagnosis (fresh
    # fable, before / after round 2: RTS 5 / close 4 / towers 4 - the roofs better, the south stack worse)
    def test_r3_stack_bands_spaced(self):
        """Critic r2: the right chimney "a brick column with a corbel ring every two blocks ... a stacked pagoda". A
        stack's projecting courses (bands and crown) above its step-in stand >= 3 courses apart. (As first written the
        count began inside the foot, whose solid courses read as rings 1 apart - fixed before it was ever green; the
        3-course rule unchanged.)"""
        p = self.p
        for s in self.m["stacks"]:
            sx0, sx1, sz0, sz1 = s["shaft"]

            def ring_n(y):
                return sum((x, y, z) in p.n for x in range(sx0 - 1, sx1 + 2) for z in range(sz0 - 1, sz1 + 2)
                           if not (sx0 <= x <= sx1 and sz0 <= z <= sz1))
            step = next(y for y in range(self.m["stack_from"], s["top"]) if ring_n(y) == 0)
            rings = []
            for y in range(step, s["top"] + 1):
                ring = [(x, y, z) for x in range(sx0 - 1, sx1 + 2) for z in range(sz0 - 1, sz1 + 2)
                        if not (sx0 <= x <= sx1 and sz0 <= z <= sz1)]
                if sum(q in p.n for q in ring) >= 6:
                    rings.append(y)
            gaps = [b - a for a, b in zip(rings, rings[1:])]
            self.assertTrue(all(d >= 3 for d in gaps), (s["top"], rings))

    def test_r3_no_fire_seen_in_a_stack_mouth(self):
        """Critic r2: "its open top shows a red block". No campfire and no red nether brick seen in any still."""
        seen = sorted({(h[0], h[1]) for st in G.STILLS for h in self.p.hits[st].values()
                       if "campfire" in h[1] or "nether" in h[1]})
        self.assertEqual(seen, [])

    def test_r3_no_black_block_on_the_tower_cap(self):
        """Critic r2: the copper cap "with one black block at its base corner". No dark cell (deepslate tiles,
        blackstone, coal) on the tower roof's eave-ring corners, seen in any still."""
        x0, x1, z0, z1 = self.m["tower"]
        corners = {(x, z) for x in (x0 - 1, x1 + 1) for z in (z0 - 1, z1 + 1)}
        bad = sorted({h[0] for st in G.STILLS for h in self.p.hits[st].values() if (h[0][0], h[0][2]) in corners
                      and h[0][1] > self.m["cornice"] and any(k in h[1] for k in ("deepslate_tile", "blackstone",
                                                                                  "coal"))})
        self.assertEqual(bad, [])

    def test_r3_smoke_vent_framed_on_the_ridge(self):
        """Critic r2: "a single dark square sits in the middle of the main roof near the ridge and reads as a missing
        block". The spawn column (a smoke-vent shaft, kept open to the sky) is framed where it leaves the roof: the four
        cells round it one over the ridge are an iron vent (bars or a wall), and rts_close sees >= 3 of them."""
        sx, _, sz = L.SPAWN
        ring = [(sx + a, 15, sz + c) for a, c in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        self.assertTrue(all(S.glyph(self.p.n.get(q, "")) == "|" for q in ring), [self.p.n.get(q) for q in ring])
        seen = {h[0] for h in self.p.hits["rts_close"].values()}
        self.assertGreaterEqual(sum(q in seen for q in ring), 3)

    def test_r3_lit_rooms_not_orange(self):
        """Critic r2: "a row of orange dots (lamps or fire) that read as scattered sparks" - the lit rooms' shroomlight
        (orange) seen through the windows. No shroomlight in the build: the rooms' light is a pale gaslight."""
        self.assertEqual(sorted({q for q, n in self.p.n.items() if n == "shroomlight"})[:5], [])

    def test_build_is_sound(self):
        """Lane and spawn column clear, no floating block, no pocket, nothing that pops off, no banned block."""
        L.check(self.rig, self.out)
        L.check_banned(self.rig, "brass_t1_r11")

    def test_committed_nbt_is_current(self):
        """design/vehicle_labs/brass_t1_r11.nbt (what the eyes run photographs) is this build, cell for cell."""
        import preview_structure as PS
        path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), E.OUT)
        self.assertTrue(os.path.exists(path), path)
        want, got = PS.load_nbt(path), PS.load_nbt(self.out)
        cw = {tuple(b["pos"]): str(want["palette"][b["state"]]["Name"]) for b in want["blocks"]}
        cg = {tuple(b["pos"]): str(got["palette"][b["state"]]["Name"]) for b in got["blocks"]}
        self.assertEqual(cw, cg, "stale - rerun python3 tools/engine_shed_r11.py")


if __name__ == "__main__":
    unittest.main()
