"""Sunforged vehicle lab (the Sunwain Court, tools/lab_vehicle_sunforged.py, design/vehicle_labs/sunforged_l3.nbt): the
r12 method's camera tests, written BEFORE the rebuild and run red on the round-2 lab (Oct 30 2026).

Every look test runs on the eyes cameras' real views (tools/building_gates.shoot: first block per ray, the 3/4 still at
FOV 77) and reads colour from the camera table (tools/camera_colour.py: each block as the eyes stills show it), never
from block ids (lesson: ids lie about colour). Tones are bands with both edges (lesson: fixing one critic overshoots
into the next). Thresholds are fixed here, before the new lab exists; never lowered.

The faults these encode are round 1's fresh critic (4/10, Oct 28, design/vehicle_labs/SCORES.md): no single landmark
(the dome boxed in, the needles compete); a weak gate; no bay rhythm on the camera faces; the roof blob reads as debris;
materials mixed, not zoned; messy ground; dead windows. Plus lovish's size rule ("tier 1 buildings should be small and
get larger and larger per tier", Oct 28 04:32): the T1 lab's top level stays within the Engine Shed T1's 26.

Rubric and bands: the designer pass (itachi2-api/runs/20261030-162547-sunforged-design, -163025-sunforged-design-cont):
landmark lead 4..10 over everything else, gate 5 x 8 on the west face and seen into, sub-blocks >= 15 % of every camera
face, no run of more than 3 full blocks without relief, palette by look.

Run: python3 -m unittest tools/test_sunforged_lab.py
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
    import lab_vehicle_sunforged as LV  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMITTED = os.path.join(ROOT, "design", "vehicle_labs", "sunforged_l3.nbt")
SX = SZ = 17
H_MAX = 26                      # the Engine Shed T1's cap (17 x 25 x 26); lovish: T1 small, growing per tier
LANE_X, LANE_Z, LANE_H = range(0, 10), range(6, 11), 8
SPAWN = (8, 8)
LEAD = (4, 10)                  # the landmark's top over the tallest thing outside its zone: clear, not a lone pole
ZONE = 3                        # the landmark's zone: columns within 3 (Chebyshev) of its top column
NEEDLE_RISE = 3                 # a needle / pinnacle: a column >= 3 above every column within 2 of it
NEEDLE_UNDER = 8                # no needle outside the zone within 8 of the landmark's top ("the needles compete")
GATE_W, GATE_H = 5, 8
GATE_SEEN = 200                 # 34-still pixels (step 2) landing >= 3 deep inside the gate, its side walls included:
#                                 the dark mouth is seen (round 2: 145; the massing greybox A: 71)
WALL_VAL = (0.28, 0.50)         # the wall field's area value (mean of its pixels): mid stone; not snow, not a hole
ROOF_VAL = (0.30, 0.55)         # roofs and terraces seen from above
SUB_SHARE = 0.15
RUN_MAX = 3
SNOW = (0.70, 0.12)             # a pixel reads as snow / white plaster: value >= 0.70 and saturation <= 0.12
SNOW_MAX = 0.05                 # of the building's pixels in each still
LOUD_SAT = 0.45                 # a loud colour (team-colour territory); gold, glass and light sources aside
LOUD_MAX = 0.03
KNOWN = 0.90                    # the share of pixels whose block / face the camera table has measured
GROUND_MAIN = 0.70              # the paving's commonest material covers >= 70 % of the seen ground ("messy ground")
ROOF_CLEAN = 0.90               # >= 90 % of the seen roof-top pixels are roof / terrace / parapet stone ("debris")
DEBRIS = ("wool", "_log", "_wood", "chain", "planks", "fence", "_carpet", "hay", "barrel")
GOLD = ("gold", "honey")
FIELD_VAL = (0.45, 0.68)        # round 2: the wall FIELD (the commonest wall block) reads light - above the ground mids
#                                 (Art method: lights 0.50+ belong to buildings) and under the snow cut, both edges
TRIM_GAP = 0.10                 # the dressings (quoins, strings, cornices) read >= 0.10 darker than the field: zones
TRIM = ("stone_brick", "reignofnether:")    # the dressings: stone-brick quoins / strings / heads, our mouldings
GOLD_MAX = 0.015
SPIRE_VAL = (0.30, 0.45)        # round 3: the spire a mid tone - round 1's granite 0.55 read "salmon", round 2's
SPIRE_SAT = (0.10, 0.35)        #   deepslate 0.17-0.20 "an overshoot into the environment darks" (critics); muted
LADDER = 3                      # round 3: no wall column flips field / dressing 3+ times in 4 courses (round 2's zebra)                # gold (the one accent) <= 1.5 % of the building's pixels in the rts still


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


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class SunwainCourt(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "sunforged_l3.nbt")
        LV.build(3).structure().write(cls.out)
        cls.b = S.load(cls.out)
        cls.n = {q: n for q, (n, _) in cls.b.blocks.items() if n not in S.AIR}
        cls.size = tuple(max(q[i] for q in cls.n) + 1 for i in range(3))
        cls.hits = {st: G.shoot(cls.b, st, 2) for st in ("rts", "rts_close", "34")}
        cls.top = {}
        for (x, y, z) in cls.n:
            cls.top[(x, z)] = max(cls.top.get((x, z), -1), y)
        cls.table = CC.load()

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    # ------------------------------------------------------------------------------------------------ helpers
    def landmark(self):
        """(column, top y) of the tallest column (ties: nearest the tied columns' centre)."""
        h = max(self.top.values())
        cols = [c for c, y in self.top.items() if y == h]
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
        out = []
        for h in hits:
            v = CC.lookup(self.table, h[1], h[2])
            if v:
                out.append((h, v))
        return out

    # ------------------------------------------------------------------------------------------------ the plot
    def test_footprint_and_height(self):
        """17 x 17, at most 26 tall at the top level (lovish, Oct 28 04:32: "tier 1 buildings should be small and get
        larger and larger per tier"; the Engine Shed T1 used 26 - the round-2 lab is 28)."""
        self.assertEqual((self.size[0], self.size[2]), (SX, SZ))
        self.assertLessEqual(self.size[1], H_MAX, self.size)

    def test_lane_and_spawn_column_clear(self):
        busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in self.n]
        self.assertEqual(busy, [])
        col = [y for y in range(1, self.size[1]) if (SPAWN[0], y, SPAWN[1]) in self.n]
        self.assertEqual(col, [])

    def test_committed_nbt_is_current(self):
        """design/vehicle_labs/sunforged_l3.nbt is what the generator builds now."""
        got = {q: v for q, v in S.load(COMMITTED).blocks.items() if v[0] not in S.AIR}
        now = {q: v for q, v in self.b.blocks.items() if v[0] not in S.AIR}
        self.assertEqual(len(got), len(now))
        self.assertEqual(sorted(q for q in now if got.get(q, (None,))[0] != now[q][0])[:8], [])

    # ------------------------------------------------------------------------------------------------ the landmark
    def test_one_landmark_clearly_tallest(self):
        """ONE landmark: its top leads everything outside its zone (columns within 3 of it) by 4..10 blocks - both
        edges: under 4 it competes (round 1: "no single landmark"), over 10 it is a pole on a box. Its top is seen in
        the rts and 34 stills."""
        lm, h = self.landmark()
        rest = max(y for c, y in self.top.items() if not self.in_zone(c, lm))
        self.assertTrue(LEAD[0] <= h - rest <= LEAD[1], (lm, h, rest))
        for st in ("rts", "34"):
            seen = {hh[0] for hh in self.hits[st].values()}
            self.assertTrue(any((lm[0], y, lm[1]) in seen for y in range(h - 2, h + 1)), st)

    def test_no_needles_compete(self):
        """Round 1: "the needles compete". No needle or pinnacle (a column >= 3 above every column within 2 of it)
        outside the landmark's zone reaches within 8 of the landmark's top."""
        lm, h = self.landmark()
        bad = []
        for c, y in self.top.items():
            if self.in_zone(c, lm) or y < h - NEEDLE_UNDER:
                continue
            ring = [self.top.get((c[0] + a, c[1] + b), 0) for a in range(-2, 3) for b in range(-2, 3) if (a, b) != (0, 0)]
            if all(y - r >= NEEDLE_RISE for r in ring):
                bad.append((c, y))
        self.assertEqual(sorted(bad), [], (lm, h))

    # ------------------------------------------------------------------------------------------------ the gate
    def test_gate_is_a_tall_opening_seen_into(self):
        """Round 1: "weak gate". In the west face: an opening from the ground >= 5 wide, every column open >= 8 tall
        under a head, on the lane; and the 34 still looks into it - >= 200 sampled pixels land on cells >= 3 deep
        inside it (x 3..10, between and on its jambs' inner faces): the gate reads as the dark mouth of the building.
        The 3/4 camera looks in at 45 degrees in plan, so a straight 5-wide passage shows mostly its north wall."""
        front = min(x for (x, y, z) in self.n if y >= 1 and z in LANE_Z)
        open_z = [z for z in range(SZ) if all((front, y, z) not in self.n for y in range(1, GATE_H + 1))
                  and any((front, y, z) in self.n for y in range(GATE_H + 1, GATE_H + 5))]
        best, run = [], []
        for z in open_z:
            run = run + [z] if run and z == run[-1] + 1 else [z]
            best = max(best, run, key=len)
        self.assertGreaterEqual(len(best), GATE_W, (front, open_z))
        inside = [hh for hh in self.hits["34"].values() if front + 3 <= hh[0][0] <= 10
                  and best[0] - 1 <= hh[0][2] <= best[-1] + 1 and 0 <= hh[0][1] <= GATE_H]
        self.assertGreaterEqual(len(inside), GATE_SEEN, len(inside))

    # ------------------------------------------------------------------------------------------------ value and palette
    def wall_hits(self, st):
        return [h for h in self.hits[st].values() if h[2] in ("west", "south") and h[0][1] >= 1
                and S.glyph(h[1]) not in "g*v" and not h[1].startswith("mcwroofs:")]

    def roof_hits(self, st):
        return [h for h in self.hits[st].values() if h[2] == "up" and h[0][1] >= 6
                and self.top.get((h[0][0], h[0][2])) == h[0][1] and S.glyph(h[1]) not in "g*v"]

    def test_wall_field_in_the_mid_stone_band(self):
        """The walls the cameras see (west and south faces, y >= 1, glass and lights aside) average value 0.28-0.50 on
        camera - mid stone, both edges (round 1: "materials mixed"; calcite / quartz read as snow, blackstone as a
        hole)."""
        for st in ("rts_close", "34"):
            known, rgb = self.colour(self.wall_hits(st))
            self.assertGreaterEqual(known, KNOWN, (st, "not photographed yet", Counter(
                (h[1], h[2]) for h in self.wall_hits(st) if not CC.lookup(self.table, h[1], h[2])).most_common(6)))
            v = max(rgb)
            self.assertTrue(WALL_VAL[0] <= v <= WALL_VAL[1], (st, round(v, 3)))

    def test_roofs_in_band(self):
        """Roofs and terraces seen from above (the top of their column, y >= 6) average value 0.30-0.55."""
        for st in ("rts", "rts_close"):
            known, rgb = self.colour(self.roof_hits(st))
            self.assertGreaterEqual(known, KNOWN, (st, "not photographed yet", Counter(
                (h[1], h[2]) for h in self.roof_hits(st) if not CC.lookup(self.table, h[1], h[2])).most_common(6)))
            v = max(rgb)
            self.assertTrue(ROOF_VAL[0] <= v <= ROOF_VAL[1], (st, round(v, 3)))

    def test_r2_wall_field_light_and_trim_darker(self):
        """Round 1's two fresh critics (runs 20261030-185320 / -185323, both 5/10): the walls (andesite, 0.29 / 0.38)
        sit in the ground's mid band, "the building no longer owns the lights", and "andesite field and stone-brick
        trim are near one value", so the trim is lost. The field (the commonest full block of the west and south wall
        hits that is not a dressing)
        averages value 0.45-0.68 on camera (rts_close, 34) and the dressings (stone bricks, string courses, cornices)
        average >= 0.10 darker."""
        for st in ("rts_close", "34"):
            hits = self.wall_hits(st)
            field = Counter(h[1] for h in hits if full(h[1]) and not h[1].startswith(TRIM)).most_common(1)[0][0]
            fk, frgb = self.colour([h for h in hits if h[1] == field])
            trim = [h for h in hits if h[1].startswith(TRIM)]
            tk, trgb = self.colour(trim)
            self.assertGreaterEqual(min(fk, tk), KNOWN, (st, field, "not photographed yet"))
            self.assertTrue(FIELD_VAL[0] <= max(frgb) <= FIELD_VAL[1], (st, field, round(max(frgb), 3)))
            self.assertGreaterEqual(max(frgb) - max(trgb), TRIM_GAP, (st, round(max(frgb), 3), round(max(trgb), 3)))

    def test_r2_one_gold_accent(self):
        """Round 1's critics: the 3 x 3 gold halo ring on the terrace "is the first thing seen" from the RTS camera,
        "a square donut, not a halo". Gold is the one small accent: <= 1.5 % of the building's pixels in rts."""
        hits = [h for h in self.hits["rts"].values() if h[0][1] >= 1]
        gold = sum(1 for h in hits if any(k in h[1] for k in GOLD)) / len(hits)
        self.assertLessEqual(gold, GOLD_MAX, round(gold, 4))

    def test_r3_spire_a_muted_mid_tone(self):
        """Round 2's critics (runs 20261030-195225 / -195226): the dark slate spire (0.17-0.20) "overshoots into the
        environment's dark band", round 1's granite (0.55, sat 0.37) was "salmon, a second saturated accent". The spire
        (the roof-mod pieces above the landmark's shaft, y >= 17) averages value 0.30-0.45 and saturation 0.10-0.35 on
        camera (rts_close, 34) - both edges."""
        for st in ("rts_close", "34"):
            hits = [h for h in self.hits[st].values() if h[0][1] >= 17 and h[1].startswith("mcwroofs:")]
            self.assertGreater(len(hits), 100, st)
            known, rgb = self.colour(hits)
            self.assertGreaterEqual(known, KNOWN, (st, "not photographed yet", Counter(h[1] for h in hits).most_common(3)))
            _, sat, val = colorsys.rgb_to_hsv(*rgb)
            self.assertTrue(SPIRE_VAL[0] <= val <= SPIRE_VAL[1], (st, "value", round(val, 3)))
            self.assertTrue(SPIRE_SAT[0] <= sat <= SPIRE_SAT[1], (st, "sat", round(sat, 3)))

    def test_r3_no_ladder_stripes(self):
        """Round 2's critics: "ladder / zebra stripes on all towers" - the long-and-short quoins flipped field stone /
        stone brick every course. No seen wall column (west / south faces, 34 and rts_close) flips between the field
        block and a dressing 3 or more times within 4 consecutive courses."""
        bad = []
        for st in ("rts_close", "34"):
            hits = self.wall_hits(st)
            field = Counter(h[1] for h in hits if full(h[1]) and not h[1].startswith(TRIM)).most_common(1)[0][0]
            cells = {h[0]: ("f" if h[1] == field else "t" if h[1].startswith(TRIM) else "o") for h in hits}
            for (x, y, z), k in cells.items():
                run = [cells.get((x, y + i, z)) for i in range(4)]
                if None in run or "o" in run:
                    continue
                if sum(run[i] != run[i + 1] for i in range(3)) >= LADDER:
                    bad.append((st, (x, y, z)))
        self.assertEqual(sorted(bad)[:10], [], len(bad))

    def test_palette_by_look_no_snow_no_loud_colour(self):
        """By how the blocks LOOK on camera: <= 5 % of the building's pixels read as snow (value >= 0.70, sat <=
        0.12) and <= 3 % as a loud colour (sat >= 0.45 - team-colour territory; gold, glass and lights aside)."""
        for st in ("rts", "34"):
            hits = [h for h in self.hits[st].values() if h[0][1] >= 1]
            px = self.hsv_px(hits)
            self.assertGreaterEqual(len(px) / max(1, len(hits)), KNOWN, (st, "not photographed yet", Counter(
                (h[1], h[2]) for h in hits if not CC.lookup(self.table, h[1], h[2])).most_common(6)))
            snow = sum(1 for _, v in px if v[2] >= SNOW[0] and v[1] <= SNOW[1]) / len(px)
            loud = sum(1 for h, v in px if v[1] >= LOUD_SAT and S.glyph(h[1]) not in "g*"
                       and not any(k in h[1] for k in GOLD)) / len(px)
            self.assertLessEqual(snow, SNOW_MAX, (st, "snow", round(snow, 3)))
            self.assertLessEqual(loud, LOUD_MAX, (st, "loud", round(loud, 3)))

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
        """Round 1: "no bay rhythm on camera faces". No run of more than 3 seen full blocks along a wall row (west:
        along z; south: along x), y >= 1: a pier, pilaster, sill, string course or window breaks it every <= 3."""
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

    # ------------------------------------------------------------------------------------------------ roof and ground
    def test_roof_reads_clean_not_debris(self):
        """Round 1: "the roof blob reads as debris". >= 90 % of the roof-top pixels (seen from above, the top of their
        column, y >= 6) are roof, terrace or parapet stone - no wool, logs, planks, chains or fences up there."""
        for st in ("rts", "rts_close"):
            hits = self.roof_hits(st)
            debris = [h for h in hits if any(k in h[1] for k in DEBRIS)]
            self.assertGreaterEqual(1 - len(debris) / max(1, len(hits)), ROOF_CLEAN,
                                    (st, Counter(h[1] for h in debris).most_common(5)))

    def test_calm_ground(self):
        """Round 1: "messy ground". The seen paving (y 0 and slabs at y 1 off the masses, from above in rts): its
        commonest material covers >= 70 %."""
        hits = [h for h in self.hits["rts"].values() if h[2] == "up" and h[0][1] <= 1
                and self.top.get((h[0][0], h[0][2])) == h[0][1]]
        cnt = Counter(h[1].replace("_slab", "").replace("_stairs", "") for h in hits)
        self.assertGreater(sum(cnt.values()), 200)
        self.assertGreaterEqual(cnt.most_common(1)[0][1] / sum(cnt.values()), GROUND_MAIN, cnt.most_common(5))

    # ------------------------------------------------------------------------------------------------ windows
    def test_windows_alive(self):
        """Round 1: "dead windows". Every glass cell the 34 still sees on the west and south faces has a light source
        within 2 blocks behind it (inside, along the view into the building), and no light source is seen bare on
        those faces (light inside the rooms, not glowing faces - lovish's dusk rule on the Engine Shed, Oct 30)."""
        lights = {q for q, n in self.n.items() if S.glyph(n) == "*"}
        glass = {h[0] for h in self.hits["34"].values() if h[2] in ("west", "south") and S.glyph(h[1]) == "g"
                 and h[0][1] >= 1}
        self.assertGreater(len(glass), 0)
        dead = []
        for (x, y, z) in glass:
            near = [(x + a, y + b, z + c) for a in range(0, 3) for b in (-1, 0, 1) for c in range(-2, 1)]
            if not any(q in lights for q in near):
                dead.append((x, y, z))
        self.assertEqual(sorted(dead)[:10], [], "%d of %d" % (len(dead), len(glass)))
        bare = {h[0] for h in self.hits["34"].values() if S.glyph(h[1]) == "*" and h[2] in ("west", "south")
                and not h[1].endswith(("lantern", "candle", "end_rod"))}
        self.assertEqual(sorted(bare)[:8], [])


if __name__ == "__main__":
    unittest.main()
