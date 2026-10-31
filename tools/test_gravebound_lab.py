"""Gravebound vehicle lab (the Charnel Works, tools/lab_vehicle_gravebound.py, design/vehicle_labs/gravebound_l3.nbt):
the r12 method's camera tests, written BEFORE the rebuild and run red on the round-1 lab (Oct 31 2026).

Every look test runs on the eyes cameras' real views (tools/building_gates.shoot: first block per ray, the 3/4 still at
FOV 77) and reads colour from the camera table (tools/camera_colour.py: each block as the eyes stills show it), never
from block ids (lesson: ids lie about colour). Tones are bands with both edges (lesson: fixing one critic overshoots
into the next). Thresholds are fixed here, before the new lab exists; never lowered.

The faults these encode are round 1's fresh critic (4/10, Oct 28, design/vehicle_labs/SCORES.md): "a dark lump with
bone stuck on" - five equal spikes and no landmark; the gate not the biggest opening, floating bone bits; noisy faces;
scattered cyan dots and stray lava; too many materials; a flat messy roof. KEEP: the bone rib-vault over the roof
(smaller at L1, growing at L3) and the pale bone gate posts. Plus lovish's size rule ("tier 1 buildings should be
small and get larger and larger per tier", Oct 28 04:32): the T1 lab's top level stays within the Engine Shed T1's 26,
L1 within 16. And the RTS value structure (Art method): the ground sits in the mids (0.28-0.50) with darks 0.12-0.28,
so an all-dark building sinks into the crown shadows - the landmark and the gate separate by value.

Rubric and bands: the designer pass (itachi2-api/runs/20261030-211330-gravebound-r1-design).

Run: python3 -m unittest tools/test_gravebound_lab.py
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
    import lab_vehicle_gravebound as LV  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMITTED = os.path.join(ROOT, "design", "vehicle_labs", "gravebound_l3.nbt")
COMMITTED_L1 = os.path.join(ROOT, "design", "vehicle_labs", "gravebound_l1.nbt")
SX = SZ = 17
H_MAX = 26                      # the Engine Shed T1's cap (17 x 25 x 26); lovish: T1 small, growing per tier
H_L1 = 16                       # L1 is the first stage: <= 16 tall (round 1's L1 was 26)
LANE_X, LANE_Z, LANE_H = range(0, 9), range(6, 11), 8
SPAWN = (8, 8)
LEAD = (4, 8)                   # the landmark's top over the tallest thing outside its zone (designer band): clear,
#                                 not a lone pole
ZONE = 3                        # the landmark's zone: columns within 3 (Chebyshev) of its top column
NEEDLE_RISE = 3                 # a needle / spike: a column >= 3 above every column within 2 of it
NEEDLE_UNDER = 8                # no needle outside the zone within 8 of the landmark's top ("five equal spikes")
NEEDLES_MAX = 2                 # at most two subordinate needles anywhere (the flanking towers)
GATE_W, GATE_H = 5, 8
GATE_SEEN = 200                 # 34-still pixels (step 2) landing >= 3 deep inside the gate: the dark mouth is seen
OTHER_OPENING = 0.40            # no other opening on the camera faces over 40 % of the gate's area ("not the biggest")
FRAME_VAL = (0.45, 0.80)        # the gate frame (its jamb / head cells seen) reads pale: bone, both edges (not snow)
FRAME_GAP = 0.20                # ... >= 0.20 lighter than the wall field round it: the gate separates by value
WALL_VAL = {"west": (0.15, 0.24), "south": (0.22, 0.32)}    # the dark wall field per face (designer bands; deepslate
#                                 bricks 0.18 / 0.26): the faction's black stone, not a hole, not a grey castle
ROOF_VAL = (0.16, 0.24)         # roofs seen from above: dark slate (designer band; Macaw's deepslate 0.17-0.20)
LIGHT_SHARE = (0.06, 0.25)      # rts building pixels at value >= 0.50 (bone, pale crag, lit green): the building owns
#                                 some lights so it does not sink into the crown shadows, and is not a white box
SUB_SHARE = 0.15
RUN_MAX = 3
SNOW = (0.70, 0.12)             # a pixel reads as snow / white plaster: value >= 0.70 and saturation <= 0.12
SNOW_MAX = 0.05
KNOWN = 0.90                    # the share of pixels whose block / face the camera table has measured
WARM_MAX = 0.03                 # warm browns (wood, dirt, rust: hue 15-60, sat >= 0.20) - round 1's dark-oak / dirt
GREEN = (80.0, 175.0)           # the one accent: soul-green light / glass (hue band, sat >= 0.20)
GREEN_RTS = (0.015, 0.04)       # 1.5-4 % of the building's rts pixels (designer band): the heart reads, one core only
CYAN_MAX = 0.005                # hue 170-230, sat > 0.40: soul fire / cyan dots <= 0.5 % (designer band)
LOUD_MAX = 0.02                 # any other saturated colour (sat >= 0.35: cyan dots, lava orange, team hues)
LIGHT_ZONES = "gate, rose, behind glass"
ROOF_SLOPE = 0.50               # >= 50 % of the roof-top pixels are sloped pieces (stairs, Macaw's roofs): not flat
RIB_MIN = 3                     # L3 keeps the bone rib-vault: >= 3 bone ribs arching over the roof
RIB_SPAN = 7                    # each rib a continuous bone arc >= 7 cells long in plan


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


def light(n):
    return S.glyph(n) == "*" or any(k in n for k in ("lantern", "fire", "campfire", "froglight", "glowstone",
                                                      "sea_lantern", "shroomlight", "magma", "lava", "end_rod",
                                                      "candle", "torch"))


def bone(n):
    return n.startswith("bone_block") or n.startswith("framedblocks:") and "bone" in n


def tops(cells):
    top = {}
    for (x, y, z) in cells:
        top[(x, z)] = max(top.get((x, z), -1), y)
    return top


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class CharnelWorks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "gravebound_l3.nbt")
        LV.build_level(3).structure().write(cls.out)
        cls.b = S.load(cls.out)
        cls.n = {q: n for q, (n, _) in cls.b.blocks.items() if n not in S.AIR}
        cls.size = tuple(max(q[i] for q in cls.n) + 1 for i in range(3))
        cls.hits = {st: G.shoot(cls.b, st, 2) for st in ("rts", "rts_close", "34")}
        cls.top = tops(cls.n)
        cls.table = CC.load()
        cls.out1 = os.path.join(cls.tmp.name, "gravebound_l1.nbt")
        LV.build_level(1).structure().write(cls.out1)
        cls.b1 = S.load(cls.out1)
        cls.n1 = {q: n for q, (n, _) in cls.b1.blocks.items() if n not in S.AIR}

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

    def rgb(self, h):
        p = CC.lookup_rgb(self.table, h[1], h[2])
        if p:
            return p
        v = CC.lookup(self.table, h[1], h[2])
        return v and colorsys.hsv_to_rgb(v[0] / 360.0, v[1], v[2])

    def colour(self, hits):
        """(known share, mean RGB) of the hits as the stills show their blocks."""
        px = [self.rgb(h) for h in hits]
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

    def unknown(self, hits):
        return Counter((h[1], h[2]) for h in hits if not CC.lookup(self.table, h[1], h[2])).most_common(6)

    def front(self):
        return min(x for (x, y, z) in self.n if y >= 1 and z in LANE_Z)

    def gate_cells(self):
        """The west gate's opening: the widest run of lane-side columns open y 1..8 at the front, under a head."""
        front = self.front()
        open_z = [z for z in range(SZ) if all((front, y, z) not in self.n for y in range(1, GATE_H + 1))
                  and any((front, y, z) in self.n for y in range(GATE_H + 1, GATE_H + 6))]
        best, run = [], []
        for z in open_z:
            run = run + [z] if run and z == run[-1] + 1 else [z]
            best = max(best, run, key=len)
        return front, best

    # ------------------------------------------------------------------------------------------------ the plot
    def test_footprint_and_height(self):
        """17 x 17, at most 26 tall at the top level (lovish, Oct 28 04:32: "tier 1 buildings should be small and get
        larger and larger per tier"; the Engine Shed T1 used 26 - the round-1 lab is 36)."""
        self.assertEqual((self.size[0], self.size[2]), (SX, SZ))
        self.assertLessEqual(self.size[1], H_MAX, self.size)

    def test_l1_is_the_first_stage(self):
        """L1 <= 16 tall (round 1's L1 was 26) and a subset of L3: L3 only adds or replaces, it never drops a cell."""
        h1 = max(q[1] for q in self.n1) + 1
        self.assertLessEqual(h1, H_L1, h1)
        lost = sorted(q for q in self.n1 if q not in self.n)
        self.assertEqual(lost[:8], [], len(lost))

    def test_lane_and_spawn_column_clear(self):
        for n in (self.n, self.n1):
            busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
            self.assertEqual(busy, [])
            col = [y for y in range(1, max(q[1] for q in n) + 1) if (SPAWN[0], y, SPAWN[1]) in n]
            self.assertEqual(col, [])

    def test_committed_nbt_is_current(self):
        """design/vehicle_labs/gravebound_l1.nbt and _l3.nbt are what the generator builds now."""
        for path, b in ((COMMITTED, self.b), (COMMITTED_L1, self.b1)):
            got = {q: v for q, v in S.load(path).blocks.items() if v[0] not in S.AIR}
            now = {q: v for q, v in b.blocks.items() if v[0] not in S.AIR}
            self.assertEqual(len(got), len(now), path)
            self.assertEqual(sorted(q for q in now if got.get(q, (None,))[0] != now[q][0])[:8], [], path)

    def test_nothing_floats(self):
        """Round 1: "floating bone bits". Every block reaches the ground (y 0) through face-touching blocks, at L1 and
        L3 - a bone piece is part of a frame, never a sticker."""
        for n in (self.n, self.n1):
            todo = [q for q in n if q[1] == 0]
            seen = set(todo)
            while todo:
                x, y, z = todo.pop()
                for a, b, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                    q = (x + a, y + b, z + c)
                    if q in n and q not in seen:
                        seen.add(q)
                        todo.append(q)
            loose = sorted(q for q in n if q not in seen)
            self.assertEqual(loose[:8], [], len(loose))

    # ------------------------------------------------------------------------------------------------ the landmark
    def test_one_landmark_clearly_tallest(self):
        """ONE landmark: its top leads everything outside its zone (columns within 3 of it) by 4..10 blocks - both
        edges: under 4 it competes (round 1: "five equal spikes, no landmark"), over 10 it is a pole on a box. Its top
        is seen in the rts and 34 stills."""
        lm, h = self.landmark()
        rest = max(y for c, y in self.top.items() if not self.in_zone(c, lm))
        self.assertTrue(LEAD[0] <= h - rest <= LEAD[1], (lm, h, rest))
        for st in ("rts", "34"):
            seen = {hh[0] for hh in self.hits[st].values()}
            self.assertTrue(any((lm[0], y, lm[1]) in seen for y in range(h - 2, h + 1)), st)

    def test_no_equal_spikes(self):
        """Round 1: "five equal spikes". No needle (a column >= 3 above every column within 2 of it) outside the
        landmark's zone reaches within 8 of the landmark's top, and at most two subordinate needles stand anywhere
        outside it (ref24's two flanking towers)."""
        lm, h = self.landmark()
        close, needles = [], []
        for c, y in self.top.items():
            if self.in_zone(c, lm):
                continue
            ring = [self.top.get((c[0] + a, c[1] + b), 0) for a in range(-2, 3) for b in range(-2, 3) if (a, b) != (0, 0)]
            if all(y - r >= NEEDLE_RISE for r in ring):
                needles.append((c, y))
                if y >= h - NEEDLE_UNDER:
                    close.append((c, y))
        self.assertEqual(sorted(close), [], (lm, h))
        self.assertLessEqual(len(needles), NEEDLES_MAX, sorted(needles))

    # ------------------------------------------------------------------------------------------------ the gate
    def test_gate_is_a_tall_opening_seen_into(self):
        """In the west face: an opening from the ground >= 5 wide, every column open >= 8 tall under a head, on the
        lane; and the 34 still looks into it - >= 200 sampled pixels land on cells >= 3 deep inside it."""
        front, best = self.gate_cells()
        self.assertGreaterEqual(len(best), GATE_W, (front, best))
        inside = [hh for hh in self.hits["34"].values() if front + 3 <= hh[0][0] <= 10
                  and best[0] - 1 <= hh[0][2] <= best[-1] + 1 and 0 <= hh[0][1] <= GATE_H]
        self.assertGreaterEqual(len(inside), GATE_SEEN, len(inside))

    def openings(self, face):
        """Holes in a camera face: cells of the face's depth map (west: the first block's x in each (y, z) row; south:
        16 - the last block's z in each (x, y) row) lying >= 2 behind the nearest surface within 3 rows and 3 columns,
        under a head (a cell of that surface above it within 6 rows) - as connected patches, largest first."""
        d = {}
        for (x, y, z) in self.n:
            if y < 1:
                continue
            k, v = ((y, z), x) if face == "west" else ((y, x), SZ - 1 - z)
            d[k] = min(d.get(k, 99), v)
        far = 99
        hole = set()
        for y in range(1, self.size[1]):
            for c in range(SX):
                v = d.get((y, c), far)
                near = min(d.get((y + a, c + b), far) for a in range(-3, 4) for b in range(-3, 4))
                if near >= far or v - near < 2:
                    continue
                if any(d.get((y + a, c), far) <= near + 1 for a in range(1, 7)):
                    hole.add((y, c))
        out = []
        while hole:
            seed = hole.pop()
            comp, todo = {seed}, [seed]
            while todo:
                a, b = todo.pop()
                for q in ((a + 1, b), (a - 1, b), (a, b + 1), (a, b - 1)):
                    if q in hole:
                        hole.discard(q)
                        comp.add(q)
                        todo.append(q)
            out.append(comp)
        return sorted(out, key=len, reverse=True)

    def test_gate_is_the_biggest_opening(self):
        """Round 1: "the gate not the biggest opening" (a south-face opening of similar weight). On the west and south
        faces, no opening (a hole >= 2 deep behind the surface round it, under a head) other than the gate exceeds 40 %
        of the gate's area."""
        front, best = self.gate_cells()
        holes = [(f, h) for f in ("west", "south") for h in self.openings(f)]
        gate = [h for f, h in holes if f == "west" and any(y <= GATE_H and z in best for (y, z) in h)]
        self.assertTrue(gate, "no gate opening on the west face")
        g = max(gate, key=len)
        big = [(f, len(h), min(h)) for f, h in holes if h is not g and len(h) > OTHER_OPENING * len(g)]
        self.assertEqual(sorted(big, key=lambda t: -t[1])[:5], [], len(g))

    def test_gate_frame_pale_against_dark_walls(self):
        """KEEP: the pale bone gate posts (enlarged). The gate frame - the seen cells of the two jamb columns (one out
        either side of the opening, y 1..9) and the head over it - reads pale on camera (value 0.45-0.80) and >= 0.20
        lighter than the wall field: the gate separates from the dark mass by value (rts_close, 34)."""
        front, best = self.gate_cells()
        frame = {(x, y, z) for x in range(front, front + 2) for y in range(1, GATE_H + 3)
                 for z in (best[0] - 1, best[-1] + 1)} | {(x, y, z) for x in range(front, front + 2)
                                                          for y in (GATE_H + 1, GATE_H + 2) for z in best}
        for st in ("rts_close", "34"):
            hits = [h for h in self.hits[st].values() if h[0] in frame]
            self.assertGreater(len(hits), 40, st)
            k, rgb = self.colour(hits)
            self.assertGreaterEqual(k, KNOWN, (st, self.unknown(hits)))
            wk, wrgb = self.colour(self.wall_hits(st))
            self.assertTrue(FRAME_VAL[0] <= max(rgb) <= FRAME_VAL[1], (st, round(max(rgb), 3)))
            self.assertGreaterEqual(max(rgb) - max(wrgb), FRAME_GAP, (st, round(max(rgb), 3), round(max(wrgb), 3)))

    # ------------------------------------------------------------------------------------------------ value and palette
    def wall_hits(self, st):
        return [h for h in self.hits[st].values() if h[2] in ("west", "south") and h[0][1] >= 1
                and S.glyph(h[1]) not in "g*v" and not h[1].startswith("mcwroofs:") and not bone(h[1])]

    def roof_hits(self, st):
        return [h for h in self.hits[st].values() if h[2] == "up" and h[0][1] >= 6
                and self.top.get((h[0][0], h[0][2])) == h[0][1] and S.glyph(h[1]) not in "g*v" and not bone(h[1])]

    def test_wall_field_dark_stone_band(self):
        """The walls the cameras see (y >= 1; glass, lights and bone aside) average, per face, value 0.15-0.24 on the
        west and 0.22-0.32 on the south on camera (the designer's bands, deepslate bricks 0.18 / 0.26): the faction's
        black stone, both edges (not a grey castle, not a black hole)."""
        for st in ("rts_close", "34"):
            for face, band in WALL_VAL.items():
                hits = [h for h in self.wall_hits(st) if h[2] == face]
                known, rgb = self.colour(hits)
                self.assertGreaterEqual(known, KNOWN, (st, face, "not photographed yet", self.unknown(hits)))
                v = max(rgb)
                self.assertTrue(band[0] <= v <= band[1], (st, face, round(v, 3)))

    def test_roofs_in_band_and_pitched(self):
        """Round 1: "a flat messy roof". Roofs seen from above (the top of their column, y >= 6, bone aside) average
        value 0.16-0.36, and >= 50 % of those pixels are sloped pieces (stairs, Macaw's roofs)."""
        for st in ("rts", "rts_close"):
            hits = self.roof_hits(st)
            known, rgb = self.colour(hits)
            self.assertGreaterEqual(known, KNOWN, (st, "not photographed yet", self.unknown(hits)))
            v = max(rgb)
            self.assertTrue(ROOF_VAL[0] <= v <= ROOF_VAL[1], (st, round(v, 3)))
            slope = sum(1 for h in hits if "stairs" in h[1] or h[1].startswith("mcwroofs:")) / max(1, len(hits))
            self.assertGreaterEqual(slope, ROOF_SLOPE, (st, round(slope, 3)))

    def test_building_owns_some_lights(self):
        """Art method: the RTS ground sits in the mids and its darks are 0.12-0.28, so an all-dark building sinks into
        the crown shadows. 6-25 % of the building's rts pixels read value >= 0.50 (bone, pale crag, lit green) - both
        edges (a white box is the other failure)."""
        hits = [h for h in self.hits["rts"].values()]
        px = self.hsv_px(hits)
        self.assertGreaterEqual(len(px) / len(hits), KNOWN, self.unknown(hits))
        share = sum(1 for _, v in px if v[2] >= 0.50) / len(px)
        self.assertTrue(LIGHT_SHARE[0] <= share <= LIGHT_SHARE[1], round(share, 3))

    def test_palette_by_look(self):
        """Round 1: "too many materials", "scattered cyan dots and stray lava". By how the blocks LOOK on camera
        (rts, 34): <= 5 % snow (value >= 0.70, sat <= 0.12); <= 3 % warm browns (hue 15-60, sat >= 0.20: wood,
        dirt); the one accent - soul green (hue 80-175, sat >= 0.20) - 1.5-4 % in rts (it reads, and it is one core),
        <= 4 % in 34; cyan (hue 170-230, sat > 0.40) <= 0.5 %; any other saturated colour (sat >= 0.35) <= 2 %."""
        for st in ("rts", "34"):
            hits = [h for h in self.hits[st].values() if h[0][1] >= 1]
            px = self.hsv_px(hits)
            self.assertGreaterEqual(len(px) / max(1, len(hits)), KNOWN, (st, "not photographed yet",
                                                                         self.unknown(hits)))
            n = len(px)
            snow = sum(1 for _, v in px if v[2] >= SNOW[0] and v[1] <= SNOW[1]) / n
            warm = sum(1 for _, v in px if 15 <= v[0] <= 60 and v[1] >= 0.20) / n
            green = sum(1 for _, v in px if GREEN[0] <= v[0] <= GREEN[1] and v[1] >= 0.20) / n
            loud = sum(1 for _, v in px if v[1] >= 0.35 and not (15 <= v[0] <= 60 or GREEN[0] <= v[0] <= GREEN[1])) / n
            self.assertLessEqual(snow, SNOW_MAX, (st, "snow", round(snow, 3)))
            self.assertLessEqual(warm, WARM_MAX, (st, "warm", round(warm, 3)))
            cyan = sum(1 for _, v in px if 170 <= v[0] <= 230 and v[1] > 0.40) / n
            self.assertLessEqual(green, GREEN_RTS[1], (st, "green", round(green, 3)))
            if st == "rts":
                self.assertGreaterEqual(green, GREEN_RTS[0], (st, "green", round(green, 3)))
            self.assertLessEqual(cyan, CYAN_MAX, (st, "cyan", round(cyan, 4)))
            self.assertLessEqual(loud, LOUD_MAX, (st, "loud", round(loud, 3)))

    def test_no_scattered_lights(self):
        """Round 1: "scattered cyan dots and stray lava". Every light source the stills see sits in the gate (the lane
        x <= 10, z 5..11, y <= 10), at the rose over it, or behind glass (lit rooms); no lava or magma anywhere."""
        self.assertEqual(sorted({n for n in self.n.values() if "lava" in n or "magma" in n}), [])
        front, best = self.gate_cells()
        bad = set()
        for st in ("rts", "rts_close", "34"):
            for h in self.hits[st].values():
                if not light(h[1]):
                    continue
                (x, y, z) = h[0]
                in_gate = x <= 10 and best[0] - 2 <= z <= best[-1] + 2 and y <= GATE_H + 2
                at_rose = x <= front + 2 and best[0] - 2 <= z <= best[-1] + 2 and y <= GATE_H + 9
                if not (in_gate or at_rose):
                    bad.add((st, h[0], h[1]))
        behind = set()
        for st in ("rts", "rts_close", "34"):
            for h in self.hits[st].values():
                if h[3] and light(h[3][1]):
                    behind.add(h[3][0])
        bad = {b for b in bad if b[1] not in behind}
        self.assertEqual(sorted(bad)[:8], [], len(bad))

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

    def test_no_run_over_3_full_blocks(self):
        """Round 1: "noisy faces" -> ordered bays. No run of more than 3 seen full blocks along a wall row (west:
        along z; south: along x), y >= 1: a buttress, pilaster, sill, string course or window breaks it every <= 3."""
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

    # ------------------------------------------------------------------------------------------------ the rib-vault
    def ribs(self, n):
        """Bone arcs over the roof: connected groups of bone cells (26-neighbour) that rise somewhere >= 3 above the
        roof under them (the highest non-bone block below, at y >= 6: a roof, not the ground or the lane) and run
        >= RIB_SPAN cells in plan along x or z."""
        solid = {q for q, v in n.items() if not bone(v)}
        bones = {q for q, v in n.items() if bone(v)}

        def under(x, y, z):
            """The highest non-bone block below (x, y, z): the roof the rib stands over (-1: none)."""
            return max([yy for yy in range(y) if (x, yy, z) in solid], default=-1)

        out = []
        while bones:
            seed = bones.pop()
            comp, todo = {seed}, [seed]
            while todo:
                x, y, z = todo.pop()
                for a in (-1, 0, 1):
                    for b in (-1, 0, 1):
                        for c in (-1, 0, 1):
                            q = (x + a, y + b, z + c)
                            if q in bones:
                                bones.discard(q)
                                comp.add(q)
                                todo.append(q)
            over = any(under(x, y, z) >= 6 and y >= under(x, y, z) + 3 for (x, y, z) in comp)
            span = max(max(q[0] for q in comp) - min(q[0] for q in comp),
                       max(q[2] for q in comp) - min(q[2] for q in comp)) + 1
            if over and span >= RIB_SPAN:
                out.append((min(comp), span, len(comp)))
        return out

    def test_l3_keeps_the_rib_vault_and_l1_starts_it(self):
        """KEEP: the bone rib-vault over the roof, smaller at L1 and grown at L3 (round 1's critic). L3 has >= 3 bone
        ribs arching over the roof (each >= 7 cells in plan, rising >= 3 over the roof under it); L1 has >= 1, and
        fewer bone cells in ribs than L3."""
        r3, r1 = self.ribs(self.n), self.ribs(self.n1)
        self.assertGreaterEqual(len(r3), RIB_MIN, r3)
        self.assertGreaterEqual(len(r1), 1, r1)
        self.assertLess(sum(r[2] for r in r1), sum(r[2] for r in r3), (r1, r3))

    # ------------------------------------------------------------------------------------------------ windows
    def test_windows_alive(self):
        """Light only from lit windows, the rose and the gate (round 2 fix list item 4): every glass cell the 34 still
        sees on the west and south faces has a light source within 2 blocks behind it."""
        lights = {q for q, n in self.n.items() if light(n)}
        glass = {h[0] for h in self.hits["34"].values() if h[2] in ("west", "south") and S.glyph(h[1]) == "g"
                 and h[0][1] >= 1}
        self.assertGreater(len(glass), 0)
        dead = []
        for (x, y, z) in glass:
            near = [(x + a, y + b, z + c) for a in range(0, 3) for b in (-1, 0, 1) for c in range(-2, 1)]
            if not any(q in lights for q in near):
                dead.append((x, y, z))
        self.assertEqual(sorted(dead)[:10], [], "%d of %d" % (len(dead), len(glass)))


if __name__ == "__main__":
    unittest.main()
