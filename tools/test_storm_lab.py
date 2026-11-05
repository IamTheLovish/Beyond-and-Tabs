"""Stormcallers vehicle lab (the Thunder Arsenal, tools/lab_vehicle_storm.py, design/vehicle_labs/storm_l3.nbt): the
r12 method's camera tests, written BEFORE the rebuild and run red on the old lab (Oct 31 2026).

Every look test runs on the eyes cameras' real views (tools/building_gates.shoot: first block per ray, the 3/4 still at
FOV 77) and reads colour from the camera table (tools/camera_colour.py: each block as the eyes stills show it), never
from block ids (lesson: ids lie about colour). Tones are bands with both edges (lesson: fixing one critic overshoots
into the next). Thresholds are fixed here, before the new lab exists; never lowered.

The faults these encode are round 1's fresh critic (4/10, Oct 28, design/vehicle_labs/SCORES.md): "a tidy toy: one
roof slab on a 17x17 box" - the tower only a bump with a floating armillary, no yard (all roofed), an unreadable gate,
flat roof language, gold / copper noise, scattered light. Plus lovish's size rule ("tier 1 buildings should be small and
get larger and larger per tier", Oct 28 04:32): the T1 lab's top level stays within the Engine Shed T1's 26, L1 within
16 (the Sunforged / Verdant / Gravebound labs' caps). And BAT - Readable at any zoom (R3): red is a TEAM colour, so the
fixed red stays a small, muted share.

Rubric and bands: the designer pass (itachi2-api/runs/20261030-234157-storm-design): the keep's lead 5..9 over every
mass outside its zone, three stepped eaves, the gate 5 x 8 on the west face, seen into and dark, an open court where
the cart is seen, roof field value 0.50-0.65 / saturation 0.25-0.40 / hue 150-175 (45-75 degrees from the lawn's
101), red 4-10 % of the building's pixels at saturation 0.25-0.45, one glow group (two at most), gold <= 1 %,
sub-blocks >= 15 % of every camera face, no run of more than 3 full blocks without relief.

Run: python3 -m unittest tools/test_storm_lab.py
"""
import colorsys
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

import schem_study as S  # noqa: E402

if HAVE_NUMPY:
    import building_gates as G  # noqa: E402
    import camera_colour as CC  # noqa: E402
    import lab_vehicle_storm as LV  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMITTED = os.path.join(ROOT, "design", "vehicle_labs", "storm_l3.nbt")
SX = SZ = 17
H_MAX, H1_MAX = 26, 16          # the Engine Shed T1's cap at the top level; L1 a first stage (lovish: T1 small)
LANE_X, LANE_Z, LANE_H = range(0, 10), range(6, 11), 8
SPAWN = (8, 8)
LEAD = (5, 9)                   # the keep's MASS top over every mass outside its zone; mass = a non-ornament block
#                                 standing on a block (crown rings, rods, posts, chains and overhanging lips aside)
ZONE = 3                        # the landmark's zone: columns within 3 (Chebyshev) of its top column
NEEDLE_RISE = 3                 # a needle / pinnacle: a column >= 3 above every column within 2 of it
NEEDLE_UNDER = 8                # no needle outside the zone within 8 of the landmark's top
EAVES = 3                       # the keep: >= 3 stepped eaves (ref42 / ref08's keep; round 1: "a bump")
EAVE_CELLS = 10                 # an eave: >= 10 roof-shape cells at one level in the zone with air under them
GATE_W, GATE_H = 5, 8
GATE_SEEN = 200                 # 34-still pixels (step 2) landing >= 3 deep inside the gate: the mouth is seen
GATE_DARK = (0.05, 0.18)        # the mean value (camera table) of what the 34 still sees inside the gate
COURT = (40, 80)                # court cells open to the sky (nothing above y 1 except low props <= y 4), connected
#                                 to the spawn - the designer's 72 counted the court's footprint; the eave lips round
#                                 it overhang its edge, so the cells the sky reaches are fewer. The old lab: roofed.
CART = 0.75                     # of the cart cells (x 7..9, z 7..10, y 0..3) the rts still sees (the yard reads)
ROOF_VAL = (0.50, 0.65)         # the roof FIELD (the commonest roof material seen from above): light jade
ROOF_SAT = (0.25, 0.40)         #   muted (the old jade, oxidized copper, sat 0.42-0.44 = toy mint), not grey
ROOF_HUE = (150, 175)           #   45-75 degrees from the lawn's hue 101: it separates from the ground
RED_SHARE = (0.04, 0.10)        # pixels reading red (hue <= 20 or >= 340, sat >= 0.25, val >= 0.12) in rts
RED_SAT = (0.25, 0.45)          # their mean saturation: lacquer, muted - team colour stays the loudest chroma
GOLD_MAX = 0.01                 # gold of the building's pixels in rts (round 1: "gold / copper noise")
GLOW_GROUPS = 2                 # light sources the cameras see bare form <= 2 groups (round 1: "scattered light")
SUB_SHARE = 0.15
RUN_MAX = 3
SNOW = (0.70, 0.12)             # a pixel reads as snow / white plaster: value >= 0.70 and saturation <= 0.12
SNOW_MAX = 0.05
LOUD_SAT = 0.45                 # a loud colour (team-colour territory); gold, glass and light sources aside
LOUD_MAX = 0.03
KNOWN = 0.90                    # the share of pixels whose block / face the camera table has measured
GOLD = ("gold", "honey")
ORNAMENT = ("_wall", "_rod", "chain", "end_rod", "lodestone", "_fence", "lantern", "_bars", "_banner")


def sub_block(n):
    """A thin or partial block (stairs, slabs, walls, fences, bars, panes, trapdoors, chains, lanterns, roof-mod
    slopes, our mouldings): everything but a full cube."""
    if n.startswith("reignofnether:"):
        import gen_arch_blocks as A
        return n.split(":", 1)[1] in A.BLOCKS
    return (S.glyph(n) in "s_|=*gr" or n.endswith(("_button", "_pot", "rail", "_trapdoor", "_fence", "_rod"))
            or n.startswith(("potted_", "mcwroofs:", "mcwwindows:", "framedblocks:", "domum_ornamentum:"))
            or n in ("chain", "vine", "end_rod", "bell"))


def full(n):
    return not sub_block(n) and S.glyph(n) == "#"


def roof_shape(n):
    return n.endswith(("_stairs", "_slab")) or n.startswith("mcwroofs:")


def is_red(v):
    h, s, val = v
    return (h <= 20 or h >= 340) and s >= 0.25 and val >= 0.12


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class ThunderArsenal(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "storm_l3.nbt")
        LV.build(3).structure().write(cls.out)
        cls.load(S.load(cls.out))

    @classmethod
    def load(cls, b):
        cls.b = b
        cls.n = {q: n for q, (n, _) in cls.b.blocks.items() if n not in S.AIR}
        cls.size = tuple(max(q[i] for q in cls.n) + 1 for i in range(3))
        cls.hits = {st: G.shoot(cls.b, st, 2) for st in ("rts", "rts_close", "34")}
        cls.top, cls.mass_top = {}, {}
        for (x, y, z), n in cls.n.items():
            cls.top[(x, z)] = max(cls.top.get((x, z), -1), y)
            if not n.endswith(ORNAMENT) and (x, y - 1, z) in cls.n:
                cls.mass_top[(x, z)] = max(cls.mass_top.get((x, z), -1), y)
        cls.table = CC.load()

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    # ------------------------------------------------------------------------------------------------ helpers
    def landmark(self, top=None):
        """(column, top y) of the tallest column (ties: nearest the tied columns' centre)."""
        top = top or self.top
        h = max(top.values())
        cols = [c for c, y in top.items() if y == h]
        mx = sum(c[0] for c in cols) / len(cols)
        mz = sum(c[1] for c in cols) / len(cols)
        return min(cols, key=lambda c: (c[0] - mx) ** 2 + (c[1] - mz) ** 2), h

    def in_zone(self, c, lm):
        return max(abs(c[0] - lm[0]), abs(c[1] - lm[1])) <= ZONE

    def colour(self, hits):
        """(known share, mean RGB) of the hits as the stills show their blocks."""
        px = [CC.lookup_rgb(self.table, h[1], h[2]) or (lambda v: v and colorsys.hsv_to_rgb(v[0] / 360.0, v[1], v[2]))(
            CC.lookup(self.table, h[1], h[2])) for h in hits]
        got = [p for p in px if p]
        if not got:
            return 0.0, None
        return len(got) / len(px), tuple(sum(p[i] for p in got) / len(got) for i in range(3))

    def hsv_px(self, hits):
        return [(h, v) for h in hits for v in [CC.lookup(self.table, h[1], h[2])] if v]

    def building(self, st):
        return [h for h in self.hits[st].values() if h[0][1] >= 1]

    def not_measured(self, hits):
        return Counter((h[1], h[2]) for h in hits if not CC.lookup(self.table, h[1], h[2])).most_common(6)

    # ------------------------------------------------------------------------------------------------ the plot
    def test_footprint_and_height(self):
        """17 x 17, at most 26 tall at the top level (lovish, Oct 28 04:32: "tier 1 buildings should be small and get
        larger and larger per tier"; the Engine Shed T1 used 26 - the old lab is 27), L1 at most 16 (old: 20)."""
        self.assertEqual((self.size[0], self.size[2]), (SX, SZ))
        self.assertLessEqual(self.size[1], H_MAX, self.size)
        c1 = LV.build(1)
        h1 = max(q[1] for q in c1.b) + 1
        self.assertLessEqual(h1, H1_MAX, h1)

    def test_l1_is_a_subset_of_l3(self):
        """L3 only adds or replaces: every L1 cell is built at L3."""
        n1 = set(LV.build(1).b)
        self.assertEqual(sorted(q for q in n1 if q not in self.n)[:8], [])

    def test_lane_and_spawn_column_clear(self):
        busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in self.n]
        self.assertEqual(busy, [])
        col = [y for y in range(1, self.size[1]) if (SPAWN[0], y, SPAWN[1]) in self.n]
        self.assertEqual(col, [])

    def test_committed_nbt_is_current(self):
        """design/vehicle_labs/storm_l3.nbt is what the generator builds now."""
        got = {q: v for q, v in S.load(COMMITTED).blocks.items() if v[0] not in S.AIR}
        now = {q: v for q, v in self.b.blocks.items() if v[0] not in S.AIR}
        self.assertEqual(len(got), len(now))
        self.assertEqual(sorted(q for q in now if got.get(q, (None,))[0] != now[q][0])[:8], [])

    # ------------------------------------------------------------------------------------------------ the landmark
    def test_one_landmark_clearly_tallest(self):
        """ONE landmark, the keep: its MASS top (blocks standing on a block; crown rings, rods, posts and chains aside) leads every mass outside its
        zone (columns within 3 of it) by 5..9 - under 5 it competes (round 1: "the tower only a bump"), over 9 it is a
        pole on a box; the overall top (the crown) stands in the same zone. Its top is seen in rts and 34."""
        lm, h = self.landmark(self.mass_top)
        rest = max(y for c, y in self.mass_top.items() if not self.in_zone(c, lm))
        self.assertTrue(LEAD[0] <= h - rest <= LEAD[1], (lm, h, rest))
        top_col, _ = self.landmark()
        self.assertTrue(self.in_zone(top_col, lm), (top_col, lm))
        for st in ("rts", "34"):
            seen = {hh[0] for hh in self.hits[st].values()}
            self.assertTrue(any((lm[0], y, lm[1]) in seen for y in range(h - 2, h + 1)), st)

    def test_no_needles_compete(self):
        """No needle or pinnacle (a column >= 3 above every column within 2 of it) outside the landmark's zone reaches
        within 8 of the landmark's top (round 1: rods and finials on every range)."""
        lm, h = self.landmark(self.mass_top)
        bad = []
        for c, y in self.top.items():
            if self.in_zone(c, lm) or y < h - NEEDLE_UNDER:
                continue
            ring = [self.top.get((c[0] + a, c[1] + b), 0) for a in range(-2, 3) for b in range(-2, 3) if (a, b) != (0, 0)]
            if all(y - r >= NEEDLE_RISE for r in ring):
                bad.append((c, y))
        self.assertEqual(sorted(bad), [], (lm, h))

    def test_keep_has_three_stepped_eaves(self):
        """The keep is a pagoda (ref42 / ref08's keep), not a bump: >= 3 eave levels in its zone - a level with >= 10
        roof-shape cells (stairs, slabs, roof-mod pieces) that have air under them (the overhang's shadow) - each
        eave's lowest level at least 3 above the one below."""
        lm, h = self.landmark(self.mass_top)
        levels = []
        for y in range(2, h + 1):
            cells = [(x, z) for x in range(lm[0] - ZONE, lm[0] + ZONE + 1) for z in range(lm[1] - ZONE, lm[1] + ZONE + 1)
                     if roof_shape(self.n.get((x, y, z), "air")) and (x, y - 1, z) not in self.n]
            if len(cells) >= EAVE_CELLS and (not levels or y - levels[-1] >= 3):
                levels.append(y)
        self.assertGreaterEqual(len(levels), EAVES, levels)

    # ------------------------------------------------------------------------------------------------ the gate
    def gate_run(self):
        front = min(x for (x, y, z) in self.n if y >= 1 and z in LANE_Z)
        open_z = [z for z in range(SZ) if all((front, y, z) not in self.n for y in range(1, GATE_H + 1))
                  and any((front, y, z) in self.n for y in range(GATE_H + 1, GATE_H + 5))]
        best, run = [], []
        for z in open_z:
            run = run + [z] if run and z == run[-1] + 1 else [z]
            best = max(best, run, key=len)
        return front, open_z, best

    def test_gate_is_a_tall_opening_seen_into(self):
        """Round 1: "an unreadable gate". In the west face: an opening from the ground >= 5 wide, every column open >= 8
        tall under a head, on the lane; and the 34 still looks into it - >= 200 sampled pixels land on cells >= 3 deep
        inside it (between and on its jambs' inner faces, under its head)."""
        front, open_z, best = self.gate_run()
        self.assertGreaterEqual(len(best), GATE_W, (front, open_z))
        inside = [hh for hh in self.hits["34"].values() if front + 3 <= hh[0][0] <= 10
                  and best[0] - 1 <= hh[0][2] <= best[-1] + 1 and 0 <= hh[0][1] <= GATE_H]
        self.assertGreaterEqual(len(inside), GATE_SEEN, len(inside))

    def test_gate_reads_dark(self):
        """The gate is the darkest opening on the west face (designer: interior 0.05-0.18): what the 34 still sees
        inside it (cells 1..3 deep behind its front, between its jambs, y 0..8 - the passage's walls, head and floor)
        averages value 0.05-0.18 on the camera table."""
        front, open_z, best = self.gate_run()
        self.assertGreaterEqual(len(best), GATE_W, (front, open_z))
        inside = [hh for hh in self.hits["34"].values() if front + 1 <= hh[0][0] <= front + 3
                  and best[0] - 1 <= hh[0][2] <= best[-1] + 1 and 0 <= hh[0][1] <= GATE_H]
        self.assertGreater(len(inside), 50)
        known, rgb = self.colour(inside)
        self.assertGreaterEqual(known, KNOWN, ("not photographed yet", self.not_measured(inside)))
        self.assertTrue(GATE_DARK[0] <= max(rgb) <= GATE_DARK[1], round(max(rgb), 3))

    # ------------------------------------------------------------------------------------------------ the court
    def test_open_court_where_the_cart_is_seen(self):
        """Round 1: "no yard (all roofed)". The court (ref08): 40..80 cells open to the sky (nothing above y 4, nothing
        above y 1 but low props), off the plot's rim, connected to the spawn cell; and the rts still sees >= 75 % of
        the cart's cells (x 7..9, z 7..10, y 0..3; the designer's) - the yard is where the vehicle is built, seen from the camera."""
        def open_(x, z):
            ys = [y for y in range(1, self.size[1]) if (x, y, z) in self.n]
            return not ys or max(ys) <= 4 and all(not full(self.n[(x, y, z)]) or y <= 1 for y in ys)
        seen, stack = {SPAWN}, [SPAWN]
        while stack:
            x, z = stack.pop()
            for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                if q not in seen and 1 <= q[0] <= SX - 2 and 1 <= q[1] <= SZ - 2 and open_(*q):
                    seen.add(q)
                    stack.append(q)
        self.assertTrue(COURT[0] <= len(seen) <= COURT[1], len(seen))
        cart = {(x, y, z) for x in range(7, 10) for z in range(7, 11) for y in range(0, 4)}
        cart_cols = {(x, z) for x in range(7, 10) for z in range(7, 11)}
        hit = {hh[0][0::2] for hh in self.hits["rts"].values() if hh[0] in cart}
        self.assertGreaterEqual(len(hit) / len(cart_cols), CART, sorted(cart_cols - hit))

    # ------------------------------------------------------------------------------------------------ roofs, colour
    def roof_hits(self, st):
        return [h for h in self.hits[st].values() if h[2] == "up" and h[0][1] >= 5
                and self.top.get((h[0][0], h[0][2])) == h[0][1] and S.glyph(h[1]) not in "g*v"
                and (roof_shape(h[1]) or full(h[1]))]

    def test_roof_field_light_muted_jade_off_the_lawn(self):
        """Round 1: "flat roof language", toy-mint copper. The roof FIELD - the commonest roof material (stairs / slabs
        / blocks of one stem, by id) on the roof tops seen from above (rts, rts_close) - reads light jade on camera:
        value 0.50-0.65, saturation 0.25-0.40, hue 150-175 (45-75 degrees off the lawn's 101) - all two-sided."""
        for st in ("rts", "rts_close"):
            hits = self.roof_hits(st)
            stem = Counter(h[1].replace("_stairs", "").replace("_slab", "").rstrip("s") for h in hits).most_common(1)
            field = [h for h in hits if h[1].replace("_stairs", "").replace("_slab", "").rstrip("s") == stem[0][0]]
            known, rgb = self.colour(field)
            self.assertGreaterEqual(known, KNOWN, (st, stem, "not photographed yet", self.not_measured(field)))
            hue, sat, val = colorsys.rgb_to_hsv(*rgb)
            self.assertTrue(ROOF_VAL[0] <= val <= ROOF_VAL[1], (st, stem, "value", round(val, 3)))
            self.assertTrue(ROOF_SAT[0] <= sat <= ROOF_SAT[1], (st, stem, "sat", round(sat, 3)))
            self.assertTrue(ROOF_HUE[0] <= hue * 360 <= ROOF_HUE[1], (st, stem, "hue", round(hue * 360, 1)))

    def test_red_a_small_muted_share(self):
        """Red is a TEAM colour (BAR's rule; BAT - Readable at any zoom R3: team colour the highest chroma on screen).
        The fixed red - lacquer posts, the kept colonnade - is 4-10 % of the building's pixels in rts (under 4 the
        colonnade the critic kept is gone), and those pixels average saturation 0.25-0.45 (muted lacquer)."""
        hits = self.building("rts")
        px = self.hsv_px(hits)
        self.assertGreaterEqual(len(px) / len(hits), KNOWN, ("not photographed yet", self.not_measured(hits)))
        red = [v for _, v in px if is_red(v)]
        share = len(red) / len(px)
        self.assertTrue(RED_SHARE[0] <= share <= RED_SHARE[1], round(share, 3))
        sat = sum(v[1] for v in red) / len(red)
        self.assertTrue(RED_SAT[0] <= sat <= RED_SAT[1], round(sat, 3))

    def test_gold_one_small_accent(self):
        """Round 1: "gold / copper noise" (gold corners on every eave). Gold <= 1 % of the building's rts pixels."""
        hits = self.building("rts")
        gold = sum(1 for h in hits if any(k in h[1] for k in GOLD)) / len(hits)
        self.assertLessEqual(gold, GOLD_MAX, round(gold, 4))

    def test_palette_by_look_no_snow_no_loud_colour(self):
        """By how the blocks LOOK on camera: <= 5 % of the building's pixels read as snow (value >= 0.70, sat <=
        0.12) and <= 3 % as a loud colour (sat >= 0.45 - team-colour territory; gold, glass and lights aside)."""
        for st in ("rts", "34"):
            hits = self.building(st)
            px = self.hsv_px(hits)
            self.assertGreaterEqual(len(px) / max(1, len(hits)), KNOWN, (st, "not photographed yet",
                                                                         self.not_measured(hits)))
            snow = sum(1 for _, v in px if v[2] >= SNOW[0] and v[1] <= SNOW[1]) / len(px)
            loud = sum(1 for h, v in px if v[1] >= LOUD_SAT and S.glyph(h[1]) not in "g*"
                       and not any(k in h[1] for k in GOLD)) / len(px)
            self.assertLessEqual(snow, SNOW_MAX, (st, "snow", round(snow, 3)))
            self.assertLessEqual(loud, LOUD_MAX, (st, "loud", round(loud, 3)))

    def test_one_glow_group(self):
        """Round 1: "scattered light" (lamps at every door, chimes on every eave corner). The light sources the cameras
        see bare (rts, rts_close, 34) form at most 2 groups (cells within 3 blocks of each other join a group); light
        behind panes and paper is not counted (it is seen through the glass, not bare)."""
        cells = {hh[0] for st in ("rts", "rts_close", "34") for hh in self.hits[st].values() if S.glyph(hh[1]) == "*"}
        groups = []
        for q in sorted(cells):
            near = [g for g in groups if any(max(abs(q[i] - p[i]) for i in range(3)) <= 3 for p in g)]
            merged = {q}.union(*near) if near else {q}
            groups = [g for g in groups if g not in near] + [merged]
        self.assertLessEqual(len(groups), GLOW_GROUPS, [sorted(g)[:3] for g in groups])

    # ------------------------------------------------------------------------------------------------ anti-toy, rhythm
    def faces(self, st):
        out = {}
        for h in self.hits[st].values():
            if h[0][1] >= 1:
                out.setdefault(h[2], Counter())[h[0]] += 1
        return out

    def test_sub_block_share_on_every_camera_face(self):
        """Thin / partial blocks >= 15 % of the cells each still shows on each side it sees (west, south, up)."""
        bad = []
        for st in ("rts", "rts_close", "34"):
            for face, cells in self.faces(st).items():
                if len(cells) < 30:
                    continue
                share = sum(sub_block(self.n[q]) for q in cells) / len(cells)
                if share < SUB_SHARE:
                    bad.append((st, face, round(share, 3)))
        self.assertEqual(bad, [])

    def test_bay_rhythm_no_run_over_3_full_blocks(self):
        """No run of more than 3 seen full blocks along a wall row (west: along z; south: along x), y >= 1: a post,
        sill, string course or window breaks it every <= 3."""
        runs = []
        for st in ("rts_close", "34"):
            fc = self.faces(st)
            for face, a in (("west", 2), ("south", 0)):
                cells = {q for q in fc.get(face, ()) if full(self.n[q])}
                for q in cells:
                    prev = list(q)
                    prev[a] -= 1
                    if tuple(prev) in cells:
                        continue
                    k, r = list(q), 0
                    while tuple(k) in cells:
                        r += 1
                        k[a] += 1
                    if r > RUN_MAX:
                        runs.append((st, face, q, r))
        self.assertEqual(sorted(runs)[:12], [], len(runs))


if __name__ == "__main__":
    unittest.main()
