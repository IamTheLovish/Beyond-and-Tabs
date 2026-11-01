#!/usr/bin/env python3
"""The Stormcallers capitol (jade_pagoda.nbt) as the RTS camera sees it - tools/capitol_jade_pagoda.py's tests.

The pagoda that shipped until Nov 2026 had five roofs one ring apart (lips 6/5/4/3/2): from the RTS camera they merged
into "one four-sided pyramid of equal-height steps - no overhang, no shadow gap between tiers" (two fresh critics,
eyes 41263811904: 4 / 4, "a green ziggurat"). Two blind massing rounds (eyes 41275632354, 41277286939) put three roofs
two rings apart with tall storeys and a crown first. Each test here fails on the five-roof pagoda and holds that
massing. Camera tests cast the eyes stills' real rays (tools/building_gates.shoot) into the shipped structure.
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import gen_blocks as GB  # noqa: E402
import capitol_jade_pagoda as P  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
NBT = os.path.join(RES, "data", "reignofnether", "structures", "jade_pagoda.nbt")
NBT_ASSETS = os.path.join(RES, "assets", "reignofnether", "structures", "jade_pagoda.nbt")
AMBIENCE = os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "building",
                        "BuildingAmbienceClientEvents.java")
C = 7
JADE_FAMILY = ("waxed_oxidized_cut_copper",)
WALLS = ("stripped_mangrove_log", "red_stained_glass", "dark_oak_planks", "dark_oak_log")
COOL = ("amethyst", "purple", "magenta", "blue", "cyan", "light_blue")   # team-like cool hues (BAT - Readable at any zoom)


def _pack_luma(n):
    """The texture pack's luma of a block by its full-block family (stairs / slabs / waxed read as their block)."""
    n = n.replace("waxed_", "")
    for s in ("_stairs", "_slab"):
        if n.endswith(s):
            n = n[:-len(s)]
    if n.startswith("dark_oak"):
        n = "dark_oak_planks"
    return GB.TARGETS.get(n, {}).get("luma")


def _ring(q):
    return max(abs(q[0] - C), abs(q[2] - C))


class JadePagodaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.b = S.load(NBT)
        cls.solid = {q: n for q, (n, _) in cls.b.blocks.items() if n not in S.AIR}
        cls.rts = G.shoot(cls.b, "rts", step=4)

    def _lips(self):
        """(y, ring) where a roof starts: the jade's outer ring at y on the side axes (rings <= 6, y >= 5: the roofs,
        not the garden guardians or the terrace step) steps OUT from the row below. Measured on the axes, the eave's
        middle - not the corners: round 2's flared tips stand one ring past the outline on purpose (critics a / b on
        round 1, eyes 41279165687: "flared tips past the square outline"), and a tip is not a roof."""
        rmax = {}
        for q, n in self.solid.items():
            if q[1] >= 5 and _ring(q) <= 6 and C in (q[0], q[2]) and n.startswith(JADE_FAMILY):
                rmax[q[1]] = max(rmax.get(q[1], -1), _ring(q))
        return [(y, rmax[y]) for y in sorted(rmax) if rmax[y] > rmax.get(y - 1, -1)]

    def test_three_roofs_two_rings_apart(self):
        """Three roofs, each lip at least two rings in from the one below: the shipped five, one ring apart, were the
        ziggurat (both critics, baseline and both massing rounds)."""
        lips = self._lips()
        self.assertEqual(len(lips), 3, lips)
        for (y0, r0), (y1, r1) in zip(lips, lips[1:]):
            self.assertGreaterEqual(r0 - r1, 2, lips)
            self.assertGreaterEqual(y1 - y0, 5, lips)        # a tall storey between: the wall shows over the roof

    def test_storey_walls_show_from_the_rts_camera(self):
        """The red lacquer and paper storeys between the roofs are the pagoda's tier gaps: at least 1.2 % of the
        RTS pixels land on them above the hall. The five-roof pagoda: 0.02 % (its walls hide under the next eave); the
        picked greyboxes A / A2 (eyes 41277286939): 1.6 % / 2.0 % - the steep camera shows a wall strip, not a face."""
        walls = sum(1 for h in self.rts.values() if h[1] in WALLS and _ring(h[0]) <= 4 and h[0][1] >= 7)
        share = walls / max(1, len(self.rts))
        self.assertGreaterEqual(share, 0.012, share)

    def test_crown_leads(self):
        """A crown sized for the far read: gold on the top rows over the centre, a lightning rod on top."""
        top = max(q[1] for q in self.solid)
        crown = [n for q, n in self.solid.items() if q[1] >= top - 3 and _ring(q) <= 1]
        self.assertGreaterEqual(sum(1 for n in crown if n == "gold_block"), 2, crown)
        self.assertEqual(self.solid.get((C, top, C)), "lightning_rod")

    def test_no_cool_team_hue_in_the_fixed_palette(self):
        """Team colour is gameplay information (blue/cyan/purple for cool teams): no purple or blue in the building
        (the shipped halo of violet glass and amethyst, flagged by both baseline and massing critics)."""
        bad = sorted({n for n in self.solid.values() if any(k in n for k in COOL)})
        self.assertEqual(bad, [])

    def test_roofs_read_as_light_lips_over_dark_gaps(self):
        """Two-edged value band on the roofs' RTS pixels (rings <= 6, y >= 6): the lips light (pack luma >= 0.50) on
        at least 45 % and the gaps between the tiers dark (<= 0.36) on at least 25 %. Round 1 (eyes 41279165687):
        all-jade roofs, 91 % light / 6 % dark on this model - "tiers still nest from above, no dark gap ring", value 5 / 5
        (fresh critics a / b). Both edges: an all-dark roof would pass a dark-only test and lose the lips."""
        roof = [h for h in self.rts.values() if h[0][1] >= 6 and _ring(h[0]) <= 6]
        lum = [_pack_luma(h[1]) for h in roof]
        light = sum(1 for v in lum if v is not None and v >= 0.50) / max(1, len(roof))
        dark = sum(1 for v in lum if v is not None and v <= 0.36) / max(1, len(roof))
        self.assertGreaterEqual(light, 0.45, (light, dark))
        self.assertGreaterEqual(dark, 0.25, (light, dark))

    def test_crown_is_one_gold_leader(self):
        """One gold leader a size class over everything near it: a 3 x 3 gold cap or more over the centre and no gold
        on the top roof's corners (round 1's five equal gold squares read as "dice-five", critics a / b)."""
        top = max(q[1] for q in self.solid)
        gold = [q for q, n in self.solid.items() if n == "gold_block" and q[1] >= top - 5]
        self.assertGreaterEqual(sum(1 for q in gold if _ring(q) <= 1), 9, gold)
        self.assertEqual([q for q in gold if _ring(q) > 1], [])

    def test_shipped_structure_is_the_generator_build(self):
        with tempfile.TemporaryDirectory() as d:
            out = os.path.join(d, "jp.nbt")
            st = P.build()
            P.check(st)
            st.write(out)
            built = S.load(out).blocks
        for path in (NBT, NBT_ASSETS):
            self.assertEqual(S.load(path).blocks, built, path)

    def test_spark_spots_match_the_java_table(self):
        """BuildingAmbienceClientEvents' sparks for jade_pagoda sit on the generator's SPARK_SPOTS."""
        src = open(AMBIENCE, encoding="utf-8").read()
        i = src.index('"jade_pagoda", new Spots(new double[] {')
        nums = [float(v) for v in src[i:src.index("}", i)].split("{", 1)[1].split(",")]
        self.assertEqual([tuple(nums[k:k + 3]) for k in range(0, len(nums), 3)], [tuple(p) for p in P.SPARK_SPOTS])


if __name__ == "__main__":
    unittest.main()
