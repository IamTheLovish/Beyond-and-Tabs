"""Verdant vehicle lab (Rooting Hollow, tools/lab_vehicle_verdant.py, design/vehicle_labs/verdant_l3.nbt): the r12
method's camera tests, written BEFORE the rebuild and run red on the round-1/2 lab (Oct 31 2026).

Every look test runs on the eyes cameras' real views (tools/building_gates.shoot: first block per ray, the 3/4 still at
FOV 77) and reads colour from the camera table (tools/camera_colour.py: each block as the eyes stills show it), never
from block ids (lesson: ids lie about colour). Tones are bands with both edges (lesson: fixing one critic overshoots
into the next). Thresholds are fixed here, before the new lab exists; never lowered.

The faults these encode are round 1's fresh critic (3/10, Oct 28, design/vehicle_labs/SCORES.md): "a mossy stone box
with a toy tree on the lid" - no real tree (thin column, no twist, flare or bark), a flat canopy slab, an unreadable
gate, scattered lime lights, stone faces without order, roof clutter. Plus lovish's size rule ("tier 1 buildings should
be small and get larger and larger per tier", Oct 28 04:32): the T1 lab's top level stays within the Engine Shed T1's
and the Sunforged lab's 26, L1 within 16. And the RTS value structure (Art method): the ground sits in the mids and is
green, so the canopy must separate from the lawn by value AND hue (the brief: "a green canopy over mossy stone can
vanish into the terrain").

Rubric and bands: the designer pass (itachi2-api/runs/20261030-204240-verdant-design): tree lead 5-10 over every built
mass, canopy top value 0.42-0.60 with its hue outside the lawn's 41-161 and saturation 0.20-0.45, canopy 30-50 % of the
top view, trunk + roots 15-28 % of the camera pixels, trunk value 0.32-0.45, stone walls 0.30-0.42, roofs 0.34-0.46,
one emissive cluster, sub-blocks >= 15 % of every camera face, no run of more than 3 full blocks without relief.

The tree is every log / wood / mangrove-root / leaf cell; "built" is everything else above the paving. Note
schem_study.glyph() files mossy stone under plants ("moss"), so these tests name their own classes.

Run: python3 -m unittest tools/test_verdant_lab.py
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
    import lab_vehicle_verdant as LV  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMITTED = os.path.join(ROOT, "design", "vehicle_labs", "verdant_l3.nbt")
SX = SZ = 17
H_MAX = 26                      # the Engine Shed T1's and the Sunforged lab's cap; lovish: T1 small, growing per tier
H1_MAX = 16                     # L1: the first stage (round 1/2's L1 is 20)
LANE_X, LANE_Z, LANE_H = range(0, 9), range(6, 11), 8
SPAWN = (8, 8)
LEAD = (5, 10)                  # the tree's top over every built mass: clear, not a pole on a box
GATE_W, GATE_H = 5, 8
GATE_SEEN = 200                 # 34-still pixels (step 2) landing >= 3 deep inside the gate: the dark mouth is seen
CANOPY_VAL = (0.60, 0.82)       # the canopy's top (leaf column tops seen from above): lit, well above the lawn's 0.26,
#                                 under near-white. RE-SET Oct 31 (was 0.42-0.60): see test_canopy_separates_from_the_lawn
CANOPY_LAWN_HUE = (41, 161)     # the lawn's hue 101 +/- 60: the canopy's mean hue lies outside it
CANOPY_SAT = (0.20, 0.45)       # muted (team colour is the loud thing), not grey
CANOPY_SHARE = (0.30, 0.50)     # of the building's pixels in the rts still
TRUNK_SHARE = (0.15, 0.28)      # trunk + roots (log / wood / mangrove roots) of the building's pixels (rts_close, 34)
TRUNK_VAL = (0.32, 0.45)        # the trunk's mean value: light bark with dark grooves (dark oak alone, 0.17-0.27,
#                                 sinks into the ground's darks)
WALL_VAL = (0.30, 0.42)         # the built walls the cameras see (west and south faces): mid stone
ROOF_VAL = (0.34, 0.46)         # built roofs seen from above (the top of their column, y >= 6)
LIGHT_CLUSTERS = 2              # one heart-glow + one warm light in the gate (round 1: "scattered lime lights")
LIGHT_CELLS = 6
SUB_SHARE = 0.15
RUN_MAX = 3
SNOW = (0.70, 0.12)             # a pixel reads as snow / white plaster: value >= 0.70 and saturation <= 0.12
SNOW_MAX = 0.05
LOUD_SAT = 0.45                 # a loud colour (team-colour territory); glass and light sources aside
LOUD_MAX = 0.03
KNOWN = 0.90                    # the share of pixels whose block / face the camera table has measured
ROOF_CLEAN = 0.90               # >= 90 % of the built roof-top pixels are roof / stone / moss (round 1: "roof clutter")
DEBRIS = ("fence", "chain", "potted_", "hopper", "_button", "lantern", "candle", "_rod", "barrel", "bars", "_wall",
          "birch")
LIGHT_NAMES = S.LIGHTS + ("jack_o_lantern", "redstone_lamp", "campfire", "magma_block", "beacon")


def is_tree(n):
    return n.endswith(("_log", "_wood", "_leaves")) or n == "mangrove_roots"


def is_leaf(n):
    return n.endswith("_leaves")


def is_light(n):
    return any(k in n for k in LIGHT_NAMES)


def small_plant(n):
    return S.glyph(n) == "v" and not n.endswith(("_block", "_bricks", "_leaves", "cobblestone")) and "mossy" not in n


def sub_block(n):
    """A thin or partial block (stairs, slabs, walls, fences, bars, panes, trapdoors, chains, lanterns, roof-mod
    slopes, our mouldings): everything but a full cube."""
    if n.startswith("reignofnether:"):
        import gen_arch_blocks as A
        return n.split(":", 1)[1] in A.BLOCKS
    return (S.glyph(n) in "s_|=gr" or n.endswith(("_button", "_pot", "rail", "_trapdoor", "_fence", "_rod", "lantern",
                                                   "candle"))
            or n.startswith(("potted_", "mcwroofs:", "mcwwindows:", "framedblocks:", "domum_ornamentum:"))
            or n in ("chain", "end_rod", "bell", "mangrove_roots"))


def full(n):
    """A full cube of the built masses (stone, planks, mud bricks...): not partial, not glass or a light, not a plant."""
    return not sub_block(n) and S.glyph(n) not in "g*" and not small_plant(n)


@unittest.skipUnless(HAVE_NUMPY, "numpy not installed")
class RootingHollow(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = os.path.join(cls.tmp.name, "verdant_l3.nbt")
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
    def colour(self, hits):
        """(known share, mean RGB) of the hits as the stills show their blocks."""
        px = [CC.lookup_rgb(self.table, h[1], h[2]) or (lambda v: v and colorsys.hsv_to_rgb(v[0] / 360.0, v[1], v[2]))(
            CC.lookup(self.table, h[1], h[2])) for h in hits]
        got = [p for p in px if p]
        if not got:
            return 0.0, None
        return len(got) / len(px), tuple(sum(p[i] for p in got) / len(got) for i in range(3))

    def unknown(self, hits):
        return Counter((h[1], h[2]) for h in hits if not CC.lookup(self.table, h[1], h[2])).most_common(6)

    def building(self, st):
        return [h for h in self.hits[st].values() if h[0][1] >= 1]

    def canopy_top_hits(self, st):
        return [h for h in self.hits[st].values() if h[2] == "up" and is_leaf(h[1])
                and self.top.get((h[0][0], h[0][2])) == h[0][1]]

    def wall_hits(self, st):
        return [h for h in self.hits[st].values() if h[2] in ("west", "south") and h[0][1] >= 1
                and not is_tree(h[1]) and S.glyph(h[1]) not in "g*" and not small_plant(h[1])
                and not h[1].startswith("mcwroofs:")]

    def roof_hits(self, st):
        return [h for h in self.hits[st].values() if h[2] == "up" and h[0][1] >= 6 and not is_tree(h[1])
                and self.top.get((h[0][0], h[0][2])) == h[0][1] and S.glyph(h[1]) not in "g*"
                and not small_plant(h[1])]

    # ------------------------------------------------------------------------------------------------ the plot
    def test_footprint_and_height(self):
        """17 x 17, at most 26 tall at L3 and 16 at L1 (lovish, Oct 28 04:32: "tier 1 buildings should be small and get
        larger and larger per tier"; the Engine Shed T1 and the Sunforged lab use 26 - round 1/2's Verdant is 28 / 20)."""
        self.assertEqual((self.size[0], self.size[2]), (SX, SZ))
        self.assertLessEqual(self.size[1], H_MAX, self.size)
        l1 = LV.build(1)
        self.assertLessEqual(max(y for (_, y, _) in l1.b) + 1, H1_MAX)

    def test_l1_is_a_subset_of_l3(self):
        """L3 only adds or replaces: every L1 cell is filled at L3."""
        l1 = {q for q, v in LV.build(1).b.items() if v[0] not in S.AIR}
        self.assertEqual(sorted(q for q in l1 if q not in self.n)[:8], [])

    def test_lane_and_spawn_column_clear(self):
        busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in self.n]
        self.assertEqual(busy, [])
        col = [y for y in range(1, self.size[1]) if (SPAWN[0], y, SPAWN[1]) in self.n]
        self.assertEqual(col, [])

    def test_committed_nbt_is_current(self):
        """design/vehicle_labs/verdant_l3.nbt is what the generator builds now."""
        got = {q: v for q, v in S.load(COMMITTED).blocks.items() if v[0] not in S.AIR}
        now = {q: v for q, v in self.b.blocks.items() if v[0] not in S.AIR}
        self.assertEqual(len(got), len(now))
        self.assertEqual(sorted(q for q in now if got.get(q, (None,))[0] != now[q][0])[:8], [])

    # ------------------------------------------------------------------------------------------------ the tree
    def test_tree_is_the_one_landmark(self):
        """The great tree is THE landmark: its top leads every built mass (stone, timber, roofs, ornament) by 5..10 -
        both edges: under 5 it competes with the hall, over 10 it is a pole on a box (round 1: "a toy tree on the
        lid"). Its top is seen in the rts and 34 stills."""
        tree = max(q[1] for q, n in self.n.items() if is_tree(n))
        built = max(q[1] for q, n in self.n.items() if not is_tree(n))
        self.assertTrue(LEAD[0] <= tree - built <= LEAD[1], (tree, built))
        for st in ("rts", "34"):
            self.assertTrue(any(h[0][1] >= tree - 1 and is_tree(h[1]) for h in self.hits[st].values()), st)

    def test_trunk_and_roots_read(self):
        """Round 1: "no real tree (thin column, no twist, flare or bark)". The trunk and its roots (log, wood and
        mangrove-root cells) are 15-28 % of the building's pixels in rts_close and 34 - seen, not hidden under the
        canopy or behind the stone, and not a log pile."""
        for st in ("rts_close", "34"):
            hits = self.building(st)
            share = sum(1 for h in hits if is_tree(h[1]) and not is_leaf(h[1])) / len(hits)
            self.assertTrue(TRUNK_SHARE[0] <= share <= TRUNK_SHARE[1], (st, round(share, 3)))

    def test_trunk_value_light_bark(self):
        """The trunk's seen faces (log / wood, rts_close and 34) average value 0.32-0.45 on camera: light bark with
        dark grooves, above the ground's darks (dark oak alone reads 0.17-0.27) and under the lights."""
        for st in ("rts_close", "34"):
            hits = [h for h in self.building(st) if h[1].endswith(("_log", "_wood"))]
            self.assertGreater(len(hits), 50, st)
            known, rgb = self.colour(hits)
            self.assertGreaterEqual(known, KNOWN, (st, "not photographed yet", self.unknown(hits)))
            self.assertTrue(TRUNK_VAL[0] <= max(rgb) <= TRUNK_VAL[1], (st, round(max(rgb), 3)))

    def test_canopy_separates_from_the_lawn(self):
        """The brief: a green canopy over mossy stone vanishes into the green terrain. The canopy's top (leaf columns
        seen from above, rts and rts_close) averages value 0.60-0.82, saturation 0.20-0.45, and its mean hue lies
        outside the lawn's 41-161 (grass top: hue 101, value 0.26). Two-sided on every axis: lime (azalea, hue 91,
        sat 0.62) fails on hue and saturation, a near-white overshoot fails on value (and the snow screen), grey fails
        the saturation floor; the saturation cap 0.45 keeps the highest chroma for team colours (Art method).

        THRESHOLD RE-SET (Oct 31, before detail round 1 relies on it; API Commander's overnight decision under
        lovish's "decide the best option"): the first value band, 0.42-0.60, was the designer's estimate written
        before cherry leaves were measured (run 20261030-204240-verdant-design: "cherry unmeasured, sample first").
        Measured in eyes 41152348776: cherry leaves, the only vanilla leaf off the lawn's hue, read value 0.85 on
        their top (mean RGB 0.80, sat 0.26, hue 336); darkening them with spruce leaves (0.26) greys the mix under
        sat 0.20 (60/40: 0.15); darkening them with azalea makes it lime-loud (azalea sat 0.62). No vanilla leaf mix
        meets 0.42-0.60 with the saturation floor, so the band is set from the measurement: 0.60-0.82 (cherry tops
        with a few dark gaps), both edges kept. The fixed rubric's 9 for separation is "the tree is the brightest,
        clearest shape in the frame"."""
        for st in ("rts", "rts_close"):
            hits = self.canopy_top_hits(st)
            self.assertGreater(len(hits), 200, st)
            known, rgb = self.colour(hits)
            self.assertGreaterEqual(known, KNOWN, (st, "not photographed yet", self.unknown(hits)))
            hue, sat, val = colorsys.rgb_to_hsv(*rgb)
            self.assertTrue(CANOPY_VAL[0] <= val <= CANOPY_VAL[1], (st, "value", round(val, 3)))
            self.assertTrue(CANOPY_SAT[0] <= sat <= CANOPY_SAT[1], (st, "sat", round(sat, 3)))
            self.assertFalse(CANOPY_LAWN_HUE[0] <= hue * 360 <= CANOPY_LAWN_HUE[1], (st, "hue", round(hue * 360)))

    def test_canopy_share_of_the_top_view(self):
        """The canopy is the landmark from above: 30-50 % of the building's pixels in the rts still - a crown, not a
        lid over everything and not a tuft."""
        hits = self.building("rts")
        share = sum(1 for h in hits if is_leaf(h[1])) / len(hits)
        self.assertTrue(CANOPY_SHARE[0] <= share <= CANOPY_SHARE[1], round(share, 3))

    # ------------------------------------------------------------------------------------------------ the gate
    def test_gate_is_a_tall_opening_seen_into(self):
        """Round 1: "an unreadable gate". In the west face: an opening from the ground >= 5 wide, every column open
        >= 8 tall under a head, on the lane; and the 34 still looks into it - >= 200 sampled pixels land on cells >= 3
        deep inside it: the gate reads as the dark mouth of the building."""
        front = min(x for (x, y, z) in self.n if 1 <= y <= GATE_H + 4 and z in LANE_Z and not small_plant(self.n[(x, y, z)]))
        open_z = [z for z in range(SZ) if all((front, y, z) not in self.n or small_plant(self.n[(front, y, z)])
                                              for y in range(1, GATE_H + 1))
                  and any((front, y, z) in self.n for y in range(GATE_H + 1, GATE_H + 5))]
        best, run = [], []
        for z in open_z:
            run = run + [z] if run and z == run[-1] + 1 else [z]
            best = max(best, run, key=len)
        self.assertGreaterEqual(len(best), GATE_W, (front, open_z))
        inside = [hh for hh in self.hits["34"].values() if front + 3 <= hh[0][0] <= 10
                  and best[0] - 1 <= hh[0][2] <= best[-1] + 1 and 0 <= hh[0][1] <= GATE_H]
        self.assertGreaterEqual(len(inside), GATE_SEEN, len(inside))

    # ------------------------------------------------------------------------------------------------ light
    def test_one_heart_glow(self):
        """Round 1: "scattered lime lights". The light sources form at most 2 clusters (cells within 2 of each other:
        the heart-glow and one warm light in the gate), at most 6 cells in all."""
        lights = sorted(q for q, n in self.n.items() if is_light(n))
        clusters = []
        for q in lights:
            near = [c for c in clusters if any(max(abs(q[i] - p[i]) for i in range(3)) <= 2 for p in c)]
            merged = [q] + [p for c in near for p in c]
            clusters = [c for c in clusters if c not in near] + [merged]
        self.assertLessEqual(len(lights), LIGHT_CELLS, Counter(self.n[q] for q in lights))
        self.assertLessEqual(len(clusters), LIGHT_CLUSTERS, [c[0] for c in clusters])

    # ------------------------------------------------------------------------------------------------ value and palette
    def test_walls_in_the_mid_stone_band(self):
        """The built walls the cameras see (west and south faces, y >= 1; the tree, glass, lights and plants aside)
        average value 0.30-0.42 on camera - mid stone, both edges."""
        for st in ("rts_close", "34"):
            hits = self.wall_hits(st)
            known, rgb = self.colour(hits)
            self.assertGreaterEqual(known, KNOWN, (st, "not photographed yet", self.unknown(hits)))
            self.assertTrue(WALL_VAL[0] <= max(rgb) <= WALL_VAL[1], (st, round(max(rgb), 3)))

    def test_roofs_in_band(self):
        """Built roofs seen from above (the top of their column, y >= 6, the tree aside) average value 0.34-0.46."""
        for st in ("rts", "rts_close"):
            hits = self.roof_hits(st)
            known, rgb = self.colour(hits)
            self.assertGreaterEqual(known, KNOWN, (st, "not photographed yet", self.unknown(hits)))
            self.assertTrue(ROOF_VAL[0] <= max(rgb) <= ROOF_VAL[1], (st, round(max(rgb), 3)))

    def test_palette_by_look_no_snow_no_loud_colour(self):
        """By how the blocks LOOK on camera: <= 5 % of the building's pixels read as snow (value >= 0.70, sat <=
        0.12) and <= 3 % as a loud colour (sat >= 0.45 - team-colour territory; glass and lights aside)."""
        for st in ("rts", "34"):
            hits = self.building(st)
            px = [(h, CC.lookup(self.table, h[1], h[2])) for h in hits]
            px = [(h, v) for h, v in px if v]
            self.assertGreaterEqual(len(px) / max(1, len(hits)), KNOWN, (st, "not photographed yet", self.unknown(hits)))
            snow = sum(1 for _, v in px if v[2] >= SNOW[0] and v[1] <= SNOW[1]) / len(px)
            loud = sum(1 for h, v in px if v[1] >= LOUD_SAT and S.glyph(h[1]) not in "g*") / len(px)
            self.assertLessEqual(snow, SNOW_MAX, (st, "snow", round(snow, 3)))
            self.assertLessEqual(loud, LOUD_MAX, (st, "loud", round(loud, 3)))

    # ------------------------------------------------------------------------------------------------ anti-toy, rhythm
    def faces(self, st):
        out = {}
        for h in self.hits[st].values():
            if h[0][1] >= 1 and not is_tree(h[1]):
                out.setdefault(h[2], Counter())[h[0]] += 1
        return out

    def test_sub_block_share_on_every_camera_face(self):
        """Thin / partial blocks >= 15 % of the built cells each still shows on each side it sees (west, south, up)."""
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
        """Round 1: "stone faces without order". No run of more than 3 seen full blocks along a built wall row (west:
        along z; south: along x), y >= 1: a pier, pilaster, sill, string course, niche or root breaks it every <= 3."""
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

    def test_roof_reads_clean_not_clutter(self):
        """Round 1: "roof clutter" (slabs, grey blocks, potted birches, hoppers, birch antlers). >= 90 % of the built
        roof-top pixels (seen from above, the top of their column, y >= 6) are roof, stone or moss - no fences, walls,
        pots, hoppers, chains, lanterns or birch up there."""
        for st in ("rts", "rts_close"):
            hits = self.roof_hits(st)
            debris = [h for h in hits if any(k in h[1] for k in DEBRIS)]
            self.assertGreaterEqual(1 - len(debris) / max(1, len(hits)), ROOF_CLEAN,
                                    (st, Counter(h[1] for h in debris).most_common(5)))


if __name__ == "__main__":
    unittest.main()
