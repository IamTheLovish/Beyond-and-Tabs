"""Ashborn vehicle lab (the Ember Pens, tools/lab_vehicle_ashborn.py, design/vehicle_labs/ashborn_l3.nbt): the r12
method's camera tests, written BEFORE the rebuild and run red on the round-2 lab (Oct 31 2026).

Every look test runs on the eyes cameras' real views (tools/building_gates.shoot: first block per ray, the 3/4 still at
FOV 77) and reads colour from the camera table (tools/camera_colour.py: each block as the eyes stills show it), never
from block ids (lesson: ids lie about colour). Tones are bands with both edges (lesson: fixing one critic overshoots
into the next). Thresholds are fixed here, before the new lab exists; never lowered.

The faults these encode are rounds 1 and 2's fresh critics (3/10 twice, Oct 28, design/vehicle_labs/SCORES.md and
design/vehicle_labs/briefs/ashborn_r3.md): no landmark (the giant a small lump / a pillar with copper blobs); no
readable gate; a cube, not a stepped temple (the tiers do not step from above); glow scattered everywhere; basalt,
deepslate and blackstone blend into one grey; rubbly ground. KEEP: the ordered basalt-column wall and the beast-pen row
with dark pilasters. Plus lovish's size rule ("tier 1 buildings should be small and get larger and larger per tier",
Oct 28 04:32): the T1 lab's top level stays within the Engine Shed T1's 26, L1 within 16. And the RTS value structure
(Art method): the ground sits in the mids (0.28-0.50), its darks 0.12-0.28, so the dark stones need value bands of
their own and light caps, or the lab sinks.

Rubric, roles and bands: the designer pass (itachi2-api/runs/20261031-001548-ashborn-design, ref29 the primary): the
plinth / pens / gate crest in basalt 0.22-0.32, the tier walls 0.12-0.20, the roofs / cones 0.15-0.25, the terrace
paving 0.32-0.44, the caps on every tier edge 0.48-0.58, with gaps plinth - walls >= 0.06, paving - walls >= 0.14,
caps - paving >= 0.06 ("never move the gap"); lead 6-10; the gate >= 3x the next opening; lights (value >= 0.50) 4-12 %
of the rts pixels; ONE glow (hue 15-45) 0.5-2 % of them, >= 90 % of it in the court / gate.

Run: python3 -m unittest tools/test_ashborn_lab.py
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
    import lab_vehicle_ashborn as LV  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMITTED = os.path.join(ROOT, "design", "vehicle_labs", "ashborn_l3.nbt")
COMMITTED_L1 = os.path.join(ROOT, "design", "vehicle_labs", "ashborn_l1.nbt")
SX = SZ = 17
H_MAX = 26                      # the Engine Shed T1's cap (17 x 25 x 26); lovish: T1 small, growing per tier
H_L1 = 16                       # L1 is the first stage: <= 16 tall (round 2's L1 was 20)
LANE_X, LANE_Z, LANE_H = range(0, 9), range(6, 11), 8
SPAWN = (8, 8)
LEAD = (6, 10)                  # the landmark's top over the tallest thing outside its zone (designer band)
ZONE = 3                        # the landmark's zone: columns within 3 (Chebyshev) of its top column
NEEDLE_RISE = 3                 # a needle / spike: a column >= 3 above every column within 2 of it
NEEDLE_UNDER = 6                # no needle outside the zone within 6 of the landmark's top
NEEDLES_MAX = 2                 # at most two subordinate needles (ref29's coned corner towers)
GATE_W, GATE_H = 5, 8
GATE_SEEN = 200                 # 34-still pixels (step 2) landing >= 3 deep inside the gate: the mouth is seen into
GATE_RATIO = 3.0                # the gate >= 3x any other opening on the camera faces (designer)
GATE_ZONE = (range(0, 5), range(3, 14))     # x, z of the gatehouse / crest: its stone is the plinth's role
PLINTH_Y = 6                    # the plinth (ground storey) is y 1..6
PLINTH_VAL = (0.22, 0.32)       # basalt plinth, pens and gate crest (designer)
WALL_VAL = (0.12, 0.20)         # tier walls / keep above the plinth (designer; deepslate tiles 0.13 / 0.18)
ROOF_VAL = (0.15, 0.25)         # roofs and cones from above (designer)
PAVE_VAL = (0.32, 0.44)         # terrace paving from above (designer; tuff 0.34)
CAP_VAL = (0.48, 0.58)          # the light caps on every tier edge (designer; andesite top 0.52)
GAPS = (("plinth", "walls", 0.06), ("paving", "walls", 0.14), ("caps", "paving", 0.06))
LIGHT_SHARE = (0.04, 0.12)      # rts building pixels at value >= 0.50 (designer): the lab owns some lights, not white
GLOW_HUE = (15.0, 45.0)         # the forge glow: orange (hue 15-45, sat >= 0.60, value >= 0.60)
GLOW_SHARE = (0.005, 0.02)      # of the rts building pixels (designer)
GLOW_IN = 0.90                  # >= 90 % of the glow pixels in the court / gate
PURPLE_MAX = 0.05               # polished blackstone's purple-grey (hue 255-300, sat >= 0.08): merges with deepslate
LOUD_MAX = 0.02                 # saturated non-light pixels (sat >= 0.35: copper, team-like hues)
SNOW = (0.70, 0.12)
SNOW_MAX = 0.05
KNOWN = 0.90                    # the share of pixels whose block / face the camera table has measured
SUB_SHARE = (0.15, 0.45)        # thin / partial blocks on each camera face: anti-toy floor; over 45 % is noise
RUN_MAX = 3
SET_BACK = 2                    # the second tier stands >= 2 back from the west and south edges (ref29's terraces)
CAPPED = 0.80                   # >= 80 % of the tier-edge tops are caps (slab / wall / stair / moulding)
FULL_LIGHTS = ("shroomlight", "glowstone", "sea_lantern", "ochre_froglight", "verdant_froglight",
               "pearlescent_froglight", "magma_block", "redstone_lamp")
STALLS_MIN = 4                  # KEEP: >= 4 barred pen stalls in the south face between pilasters


def sub_block(n):
    """A thin or partial block (stairs, slabs, walls, fences, bars, panes, trapdoors, chains, lanterns, roof-mod
    slopes, our mouldings): everything but a full cube."""
    if n.startswith("reignofnether:"):
        import gen_arch_blocks as A
        return n.split(":", 1)[1] in A.BLOCKS
    if n in FULL_LIGHTS:
        return False                    # a glowing full cube (shroomlight, froglight, glowstone) is a full block
    return (S.glyph(n) in "s_|=*gr" or n.endswith(("_button", "_pot", "rail", "_trapdoor", "_fence", "_rod"))
            or n.startswith(("potted_", "mcwroofs:", "mcwwindows:", "framedblocks:", "domum_ornamentum:"))
            or n in ("chain", "vine", "end_rod", "bell", "campfire"))


def full(n):
    return not sub_block(n) and S.glyph(n) == "#"


def light(n):
    return S.glyph(n) == "*" or any(k in n for k in ("lantern", "fire", "campfire", "froglight", "glowstone",
                                                      "sea_lantern", "shroomlight", "magma", "lava", "end_rod",
                                                      "candle", "torch"))


def roof(n):
    return n.startswith("mcwroofs:")


def tops(cells):
    top = {}
    for (x, y, z) in cells:
        top[(x, z)] = max(top.get((x, z), -1), y)
    return top


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class EmberPens(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "ashborn_l3.nbt")
        LV.build_level(3).structure().write(cls.out)
        cls.b = S.load(cls.out)
        cls.n = {q: n for q, (n, _) in cls.b.blocks.items() if n not in S.AIR}
        cls.size = tuple(max(q[i] for q in cls.n) + 1 for i in range(3))
        cls.hits = {st: G.shoot(cls.b, st, 2) for st in ("rts", "rts_close", "34")}
        cls.top = tops(cls.n)
        cls.table = CC.load()
        cls.out1 = os.path.join(cls.tmp.name, "ashborn_l1.nbt")
        LV.build_level(1).structure().write(cls.out1)
        cls.b1 = S.load(cls.out1)
        cls.n1 = {q: n for q, (n, _) in cls.b1.blocks.items() if n not in S.AIR}

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    # ------------------------------------------------------------------------------------------------ helpers
    def landmark(self):
        h = max(self.top.values())
        cols = [c for c, y in self.top.items() if y == h]
        mx = sum(c[0] for c in cols) / len(cols)
        mz = sum(c[1] for c in cols) / len(cols)
        return min(cols, key=lambda c: (c[0] - mx) ** 2 + (c[1] - mz) ** 2), h

    def in_zone(self, c, lm):
        return max(abs(c[0] - lm[0]), abs(c[1] - lm[1])) <= ZONE

    def rgb(self, h):
        """The block's colour on camera from the face the ray saw: that face's mean RGB, else that face's median HSV;
        only a face the table lacks falls back to another face (camera_colour.lookup_rgb alone would take another
        face's mean when this face was measured before r12 without one: tuff's up face read as its west, 0.19)."""
        e = self.table["blocks"].get(h[1], {}).get(h[2])
        if e:
            return tuple(e["rgb"]) if e.get("rgb") else colorsys.hsv_to_rgb(e["hue"] / 360.0, e["sat"], e["val"])
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

    def is_top(self, q):
        return self.top.get((q[0], q[2])) == q[1]

    def role(self, h):
        """The designer's stone roles by POSITION and SHAPE (never by colour): 'plinth' (a west / south face at
        y 1..6, or the gatehouse's crest), 'walls' (a west / south face above the plinth), 'roofs' (a Macaw's roof
        piece seen from above), 'caps' (a slab, wall, stair or moulding on top of its column), 'paving' (a full block
        on top of its column at y >= 5). Glass and light sources have no role."""
        (x, y, z), n, face = h[0], h[1], h[2]
        if S.glyph(n) in "g*v" or light(n) or n in ("iron_bars", "chain"):
            return None
        if roof(n):
            return "roofs" if face == "up" else None
        if face == "up":
            if not self.is_top(h[0]):
                return None
            if sub_block(n):
                return "caps"
            return "paving" if y >= 5 else None
        if face in ("west", "south") and y >= 1:
            if sub_block(n) and self.is_top(h[0]) and n.endswith(("_slab", "_stairs", "_wall")):
                return None                     # a cap's thin edge belongs to the cap, not to the wall under it
            if y <= PLINTH_Y or (x in GATE_ZONE[0] and z in GATE_ZONE[1]):
                return "plinth"
            return "walls"
        return None

    def role_value(self, st, role):
        hits = [h for h in self.hits[st].values() if self.role(h) == role]
        known, rgb = self.colour(hits)
        return hits, known, (max(rgb) if rgb else None)

    # ------------------------------------------------------------------------------------------------ the plot
    def test_footprint_and_height(self):
        """17 x 17, at most 26 tall at the top level (lovish, Oct 28 04:32: "tier 1 buildings should be small and get
        larger and larger per tier"; the Engine Shed T1 used 26)."""
        self.assertEqual((self.size[0], self.size[2]), (SX, SZ))
        self.assertLessEqual(self.size[1], H_MAX, self.size)

    def test_l1_is_the_first_stage(self):
        """L1 <= 16 tall (round 2's L1 was 20) and a subset of L3: L3 only adds or replaces, it never drops a cell."""
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
        """design/vehicle_labs/ashborn_l1.nbt and _l3.nbt are what the generator builds now."""
        for path, b in ((COMMITTED, self.b), (COMMITTED_L1, self.b1)):
            got = {q: v for q, v in S.load(path).blocks.items() if v[0] not in S.AIR}
            now = {q: v for q, v in b.blocks.items() if v[0] not in S.AIR}
            self.assertEqual(len(got), len(now), path)
            self.assertEqual(sorted(q for q in now if got.get(q, (None,))[0] != now[q][0])[:8], [], path)

    def test_nothing_floats(self):
        """Every block reaches the ground (y 0) through face-touching blocks, at L1 and L3."""
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
        """Rounds 1-2: "no landmark (the giant a small lump / a pillar with copper blobs)". ONE landmark: its top leads
        everything outside its zone (columns within 3 of it) by 6..10 blocks (designer band; under 6 it competes, over
        10 it is a pole on a box), it does not stand over the spawn column, and its top is seen in the rts and 34
        stills."""
        lm, h = self.landmark()
        rest = max(y for c, y in self.top.items() if not self.in_zone(c, lm))
        self.assertTrue(LEAD[0] <= h - rest <= LEAD[1], (lm, h, rest))
        self.assertNotEqual(lm, SPAWN)
        for st in ("rts", "34"):
            seen = {hh[0] for hh in self.hits[st].values()}
            self.assertTrue(any((lm[0], y, lm[1]) in seen for y in range(h - 2, h + 1)), st)

    def test_no_equal_spikes(self):
        """No needle (a column >= 3 above every column within 2 of it) outside the landmark's zone reaches within 6 of
        the landmark's top, and at most two subordinate needles stand outside it (ref29's coned towers)."""
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
        """Rounds 1-2: "no readable gate". In the west face: an opening from the ground >= 5 wide, every column open
        >= 8 tall under a head, on the lane; and the 34 still looks into it - >= 200 sampled pixels land on cells >= 3
        deep inside it."""
        front, best = self.gate_cells()
        self.assertGreaterEqual(len(best), GATE_W, (front, best))
        inside = [hh for hh in self.hits["34"].values() if front + 3 <= hh[0][0] <= 12
                  and best[0] - 1 <= hh[0][2] <= best[-1] + 1 and 0 <= hh[0][1] <= GATE_H]
        self.assertGreaterEqual(len(inside), GATE_SEEN, len(inside))

    def openings(self, face):
        """Holes in a camera face (west: the first block's x per (y, z); south: 16 - the last block's z per (x, y))
        lying >= 2 behind the nearest surface within 3 rows and columns, under a head - connected patches, largest
        first."""
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
        """The gate is the biggest framed opening: on the west and south faces no other opening (a hole >= 2 deep
        behind the surface round it, under a head) reaches a third of the gate's area (designer: gate >= 3x)."""
        front, best = self.gate_cells()
        holes = [(f, h) for f in ("west", "south") for h in self.openings(f)]
        gate = [h for f, h in holes if f == "west" and any(y <= GATE_H and z in best for (y, z) in h)]
        self.assertTrue(gate, "no gate opening on the west face")
        g = max(gate, key=len)
        big = [(f, len(h), min(h)) for f, h in holes if h is not g and len(h) * GATE_RATIO > len(g)]
        self.assertEqual(sorted(big, key=lambda t: -t[1])[:5], [], len(g))

    # ------------------------------------------------------------------------------------------------ value roles
    def test_stone_roles_in_their_value_bands(self):
        """Rounds 1-2: "basalt / deepslate / blackstone blend into one grey". Each stone role reads in its own band on
        camera (area mean of its pixels, rts_close and 34): plinth 0.22-0.32, walls 0.12-0.20, roofs 0.15-0.25, paving
        0.32-0.44, caps 0.48-0.58 (designer). A role the lab does not have fails: ref29's tiers read by their caps and
        dark cones."""
        bands = {"plinth": PLINTH_VAL, "walls": WALL_VAL, "roofs": ROOF_VAL, "paving": PAVE_VAL, "caps": CAP_VAL}
        bad = []
        for st in ("rts_close", "34"):
            for role, band in bands.items():
                hits, known, v = self.role_value(st, role)
                if len(hits) < 30:
                    bad.append((st, role, "too few pixels", len(hits)))
                elif known < KNOWN:
                    bad.append((st, role, "not photographed yet", self.unknown(hits)))
                elif not band[0] <= v <= band[1]:
                    bad.append((st, role, round(v, 3)))
        self.assertEqual(bad, [])

    def test_stone_roles_keep_their_gaps(self):
        """The designer's gaps, never moved: plinth - walls >= 0.06, paving - walls >= 0.14, caps - paving >= 0.06
        (rts_close and 34)."""
        bad = []
        for st in ("rts_close", "34"):
            vals = {r: self.role_value(st, r)[2] for r in ("plinth", "walls", "paving", "caps")}
            for a, b, gap in GAPS:
                if vals[a] is None or vals[b] is None or vals[a] - vals[b] < gap:
                    bad.append((st, a, b, vals[a] and round(vals[a], 3), vals[b] and round(vals[b], 3)))
        self.assertEqual(bad, [])

    def test_building_owns_some_lights(self):
        """Art method: an all-dark mass sinks into the RTS ground's darks (0.12-0.28). 4-12 % of the building's rts
        pixels read value >= 0.50 (the caps; designer) - both edges (a white box is the other failure)."""
        hits = list(self.hits["rts"].values())
        px = self.hsv_px(hits)
        self.assertGreaterEqual(len(px) / len(hits), KNOWN, self.unknown(hits))
        share = sum(1 for _, v in px if v[2] >= 0.50) / len(px)
        self.assertTrue(LIGHT_SHARE[0] <= share <= LIGHT_SHARE[1], round(share, 3))

    # ------------------------------------------------------------------------------------------------ light
    def in_court_or_gate(self, q):
        """The court (the open shaft round the spawn) or the gate (the lane, its mouth and crest)."""
        x, y, z = q
        court = max(abs(x - SPAWN[0]), abs(z - SPAWN[1])) <= 4 and y <= 12
        gate = x <= 12 and 4 <= z <= 12 and y <= GATE_H + 9
        return court or gate

    def test_one_glow_focus(self):
        """Rounds 1-2: "glow scattered everywhere". ONE glow (the forge, orange: hue 15-45, sat >= 0.60, value >= 0.60
        on camera) takes 0.5-2 % of the rts building pixels (it reads; it is not a lava field next to the warm team
        colours), >= 90 % of it in the court / gate; every light source any still sees sits in the court or the gate."""
        hits = list(self.hits["rts"].values())
        px = self.hsv_px(hits)
        self.assertGreaterEqual(len(px) / len(hits), KNOWN, self.unknown(hits))
        glow = [h for h, v in px if GLOW_HUE[0] <= v[0] <= GLOW_HUE[1] and v[1] >= 0.60 and v[2] >= 0.60]
        share = len(glow) / len(px)
        self.assertTrue(GLOW_SHARE[0] <= share <= GLOW_SHARE[1], round(share, 4))
        inside = sum(1 for h in glow if self.in_court_or_gate(h[0])) / max(1, len(glow))
        self.assertGreaterEqual(inside, GLOW_IN, round(inside, 3))
        stray = sorted({(st, h[0], h[1]) for st in ("rts", "rts_close", "34") for h in self.hits[st].values()
                        if light(h[1]) and not self.in_court_or_gate(h[0])})
        self.assertEqual(stray[:8], [], len(stray))

    def test_palette_by_look(self):
        """By how the blocks LOOK on camera (rts, 34): <= 5 % snow (value >= 0.70, sat <= 0.12); <= 5 % blackstone's
        purple-grey (hue 255-300, sat >= 0.08; designer: it merges with the deepslate); <= 2 % saturated pixels that
        are not light sources (sat >= 0.35: the copper blobs, team-like hues)."""
        bad = []
        for st in ("rts", "34"):
            hits = [h for h in self.hits[st].values() if h[0][1] >= 1]
            px = self.hsv_px(hits)
            if len(px) / max(1, len(hits)) < KNOWN:
                bad.append((st, "not photographed yet", self.unknown(hits)))
                continue
            n = len(px)
            snow = sum(1 for _, v in px if v[2] >= SNOW[0] and v[1] <= SNOW[1]) / n
            purple = sum(1 for _, v in px if 255 <= v[0] <= 300 and v[1] >= 0.08) / n
            loud = sum(1 for h, v in px if v[1] >= 0.35 and not light(h[1])) / n
            for k, got, cap in (("snow", snow, SNOW_MAX), ("purple", purple, PURPLE_MAX), ("loud", loud, LOUD_MAX)):
                if got > cap:
                    bad.append((st, k, round(got, 3)))
        self.assertEqual(bad, [])

    # ------------------------------------------------------------------------------------------------ the tiers
    def test_tiers_step_back_and_every_edge_is_capped(self):
        """Rounds 1-2: "a cube, not a stepped temple (the tiers do not step from above)". ref29's tiers read by a
        set-back and a light cap: (1) a second tier (tops >= 4 above the plinth's) stands >= 2 back from the west and
        south edges outside the gatehouse, with the plinth's top showing in front of it; (2) >= 80 % of the tier-edge
        tops (a top whose west or south neighbour column is >= 2 lower, y >= 5) are caps (slab, wall, stair,
        moulding)."""
        plinth = max(self.top.get((x, z), 0) for x in (0, 1) for z in range(SZ) if z not in GATE_ZONE[1])
        upper = {c for c, y in self.top.items() if y >= plinth + 4 and not (c[0] in GATE_ZONE[0] and c[1] in GATE_ZONE[1])}
        self.assertTrue(upper, plinth)
        west = min(c[0] for c in upper)
        south = SZ - 1 - max(c[1] for c in upper)
        self.assertGreaterEqual(min(west, south), SET_BACK, (plinth, west, south))
        edges = []
        for (x, z), y in self.top.items():
            if y < 5:
                continue
            for (a, b) in ((-1, 0), (0, 1)):
                nb = self.top.get((x + a, z + b), -1)
                if y - nb >= 2:
                    edges.append(((x, y, z), self.n[(x, y, z)]))
                    break
        capped = sum(1 for _, n in edges if sub_block(n) and not light(n)) / max(1, len(edges))
        self.assertGreaterEqual(capped, CAPPED, (round(capped, 3), Counter(n for _, n in edges).most_common(6)))

    # ------------------------------------------------------------------------------------------------ anti-toy, rhythm
    def faces(self, st):
        out = {}
        for h in self.hits[st].values():
            if h[0][1] >= 1:
                out.setdefault(h[2], Counter())[h[0]] += 1
        return out

    def test_sub_block_share_on_every_camera_face(self):
        """Thin / partial blocks are 15-45 % of the cells each still shows on each side it sees (west, south, up):
        the anti-toy floor (lessons H.6), and not a face of noise."""
        bad = []
        for st in ("rts", "rts_close", "34"):
            for face, cells in self.faces(st).items():
                if len(cells) < 30:
                    continue
                share = sum(sub_block(self.n[q]) for q in cells) / len(cells)
                if not SUB_SHARE[0] <= share <= SUB_SHARE[1]:
                    bad.append((st, face, round(share, 3)))
        self.assertEqual(bad, [])

    def test_no_run_over_3_full_blocks(self):
        """No run of more than 3 seen full blocks along a wall row (west: along z; south: along x), y >= 1: a pier,
        column joint, sill, string course or stall breaks it every <= 3."""
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

    # ------------------------------------------------------------------------------------------------ KEEP
    def test_keeps_the_beast_pens(self):
        """KEEP (rounds 1-2): the beast-pen row with dark pilasters. The south face shows >= 4 barred stalls (runs of
        iron bars in the plinth storey, y 1..4) separated by pilasters, seen in rts_close and 34, at L3 and L1."""
        for n in (self.n, self.n1):
            south = {}
            for (x, y, z), b in n.items():
                if 1 <= y <= 4 and z >= SZ - 3:
                    if (x, y) not in south or z > south[(x, y)][0]:
                        south[(x, y)] = (z, b)
            barred = sorted({x for (x, y), (z, b) in south.items() if b == "iron_bars"})
            stalls, run = 0, None
            for x in range(SX):
                if x in barred:
                    if run is None:
                        stalls += 1
                    run = x
                else:
                    run = None
            self.assertGreaterEqual(stalls, STALLS_MIN, barred)
        for st in ("rts_close", "34"):
            seen = sum(1 for h in self.hits[st].values() if h[1] == "iron_bars" and h[0][2] >= SZ - 3 and h[0][1] <= 4)
            self.assertGreater(seen, 40, st)


if __name__ == "__main__":
    unittest.main()
