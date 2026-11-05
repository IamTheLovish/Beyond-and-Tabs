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
WALLS = ("stripped_mangrove_log", "red_stained_glass", "white_stained_glass", "dark_oak_planks", "dark_oak_log")
# white_stained_glass: the paper screens since Nov 4 (Fable critic A fix 1; definition re-set in its own commit)
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
        at least 45 % and a real dark gap ring (pack luma <= 0.25, i.e. under 0.2 on screen) on at least 6 %. Round 1
        (eyes 41279165687): all-jade roofs, no dark gap - "tiers still nest from above", value 5 / 5 (critics a / b).
        Re-set Nov 1 on screen (eyes 41280515001 / 41281536246): the first cut (dark <= 0.36, >= 25 %) counted dark oak,
        which shows at 0.25-0.3 on screen - a brown field, not a gap (critics a / b on round 2: "jade-rimmed brown
        diamonds"; the model's 41 % dark against 10 % under 0.2 on screen). The blackstone gap ring the blind pick
        chose (option 3) shows 13 % under 0.2 on screen, round 2's oak 10 %; on this model 9 % / 0 %. Both edges: an
        all-dark roof would pass a dark-only test and lose the lips."""
        roof = [h for h in self.rts.values() if h[0][1] >= 6 and _ring(h[0]) <= 6]
        lum = [_pack_luma(h[1]) for h in roof]
        light = sum(1 for v in lum if v is not None and v >= 0.50) / max(1, len(roof))
        dark = sum(1 for v in lum if v is not None and v <= 0.25) / max(1, len(roof))
        self.assertGreaterEqual(light, 0.45, (light, dark))
        self.assertGreaterEqual(dark, 0.06, (light, dark))

    def test_crown_is_one_gold_leader(self):
        """One gold leader: the crown's thin gold spire on the centre column (1-2 cells) is the only gold above the
        verandah - no gold cap and no gold eave corners. Re-set Nov 4 in its own commit (was: a 3 x 3 gold cap or more,
        no gold on the top roof's corners): Fable critic C F1 - a gold apex cube is the Sunforged's (the barracks' 2 x 2
        x 2 crown), the pagoda and the academy put the same gold cube on top, so factions blur from the RTS camera;
        critic A fix 3 - nine gold squares (the cap and eight eave corners) and the crown did not lead. Still guards
        round 1's "dice-five": no gold on any roof corner. Red on the shipped pagoda (a 3 x 3 cap, gold corners)."""
        gold = [q for q, n in self.solid.items() if n == "gold_block" and q[1] >= 6]
        spire = [q for q in gold if (q[0], q[2]) == (C, C)]
        self.assertTrue(1 <= len(spire) <= 2, gold)
        self.assertEqual(sorted(set(gold) - set(spire)), [])

    def test_nothing_floats_on_the_roofs(self):
        """Every block of the roofs and crown (y >= 6) touches another block by a face: round 3's rising corner arms
        were diagonal cells, "detached corner cubes that read as floating dots / sparkles" (both blind critics,
        eyes 41281536246). A tip must grow out of its eave."""
        def faces(q):
            x, y, z = q
            return [(x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)]
        floating = sorted(q for q in self.solid if q[1] >= 6 and not any(f in self.solid for f in faces(q)))
        self.assertEqual(floating, [])

    # ---- the Fable critics' fixes (Nov 4, eyes 41792408905: 6 / 10; critic-a.md, critic-c.md F1 F2 F4 F6 F7) ----
    def test_paper_screens_are_pale(self):
        """The storeys' paper screens are pale (white stained glass), not red glass: red glass beside the red posts read
        as one maroon mass and passed no light, so the top storey was dead at dusk (critic A fix 1)."""
        red = sorted(q for q, n in self.solid.items() if n == "red_stained_glass")
        self.assertEqual(red, [])
        self.assertGreaterEqual(sum(1 for n in self.solid.values() if n == "white_stained_glass"), 20)

    def test_every_roof_has_a_dark_second_course(self):
        """Each roof's second course (the stairs one up on the band, y 7 and y 13) is the dark gap stair, so with the
        gap ring under it every roof shows a 2-cell dark ring from above and the tiers part (critic A fix 2: the
        1-cell gap ring was too thin at 30 px a block; three teal tiers merged into one pyramid)."""
        inside = {7: (6, 4), 13: (3, 3)}      # inside each roof's lip (the eave's own sweep stairs sit on the lip)
        jade = sorted(q for q, n in self.solid.items() if q[1] in inside and n == JADE_FAMILY[0] + "_stairs"
                      and abs(q[0] - C) <= inside[q[1]][0] and abs(q[2] - C) <= inside[q[1]][1])
        self.assertEqual(jade, [])

    def test_eaves_sweep_as_stair_runs(self):
        """The eave sweeps up to each corner as a run of stairs, not stepping full cubes: one above each lip (y 7,
        y 13) only the four corner lifts are full jade blocks (critic C F6: "the eave tips are 1-cell jade cubes
        stepping down", a crenellated pixel edge at RTS zoom; shipped 12 per roof)."""
        for y in (7, 13):
            cubes = sorted(q for q, n in self.solid.items() if q[1] == y and n == JADE_FAMILY[0])
            self.assertLessEqual(len(cubes), 4, "y%d %s" % (y, cubes))

    def test_garden_is_moss_not_the_shared_grey(self):
        """The garden floor (y 0 outside the terrace) is the Stormcallers' GARDEN - moss, mossy stone, grass - with
        no andesite and no polished andesite (critic C F2: five buildings of three factions stood on the same grey
        yard); stone-brick flags only as the starting type's stepping stones."""
        garden = {q: n for q, n in self.solid.items() if q[1] == 0 and _ring(q) >= 6 and n != "stone_bricks"}
        grey = sorted({n for n in garden.values() if "andesite" in n})
        self.assertEqual(grey, [])
        green = sum(1 for n in garden.values() if "moss" in n or n == "grass_block")
        self.assertGreaterEqual(green / max(1, len(garden)), 0.6, green)

    def test_no_pink_and_no_black_heads_at_the_corners(self):
        """No cherry pink at all (lovish, Nov 4: the garden trees "Azalea instead"; critic C F4: pink is the Verdant
        crown's and the academy's lotus) and no black dragon heads on the guardians (critic A fix 4: base-corner
        noise - "four black cubes with faces")."""
        bad = sorted({n for n in self.solid.values() if "cherry" in n or "pink" in n or n == "dragon_head"})
        self.assertEqual(bad, [])

    def test_plan_is_a_rectangle_under_squares(self):
        """lovish, Nov 4 ("Pagoda: new plan"), critic C F7: the first roof is a long rectangle (its outline from
        above at least 1.3 x as wide as deep) under square upper roofs (y >= 12: as wide as deep), so the plan no
        longer twins the Ashborn capitol's concentric squares. Shipped: 13 x 13 under 9 x 9 under 5 x 5."""
        def extent(cells):
            xs = [q[0] for q in cells]
            zs = [q[2] for q in cells]
            return max(xs) - min(xs) + 1, max(zs) - min(zs) + 1
        roof1 = [q for q, n in self.solid.items() if q[1] in (5, 6) and n.startswith(JADE_FAMILY)]
        w, d = extent(roof1)
        self.assertGreaterEqual(max(w, d) / min(w, d), 1.3, (w, d))
        upper = [q for q, n in self.solid.items() if q[1] == 12 and n.startswith(JADE_FAMILY)]
        w2, d2 = extent(upper)
        self.assertEqual(w2, d2, (w2, d2))

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
