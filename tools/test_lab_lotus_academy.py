#!/usr/bin/env python3
"""The Stormcallers' army lab (lotus_academy*.nbt) as the RTS camera sees it - tools/lab_lotus_academy.py's tests.

The Lotus Court that shipped until Nov 2026 was "a flat yard with half a jade roof" with "four competing accents:
teal, pink, gold, red" (two fresh critics on eyes 41263811904: 4 / 4). A blind massing round (eyes 41292980809, critics
20261101-043559 / -043843: 6 / 6, shipped 4 / 4) picked the double-eaved hall over the court at L1, and asked for: the
court as one quiet stone under the hall's frame (a roofed edge), the lotus where the camera looks (on the roof), the
target block / chequer mosaic / gold corner studs gone. Each test here fails on the shipped court and holds that.
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import lab_lotus_academy as L  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
FILES = ("lotus_academy.nbt", "lotus_academy_l2.nbt", "lotus_academy_l3.nbt")
NBT = [os.path.join(RES, "data", "reignofnether", "structures", f) for f in FILES]
NBT_ASSETS = [os.path.join(RES, "assets", "reignofnether", "structures", f) for f in FILES]
JADE = "waxed_oxidized_cut_copper"
QUIET = ("stone", "stone_bricks", "polished_andesite", "stone_brick_stairs", "stone_brick_slab", "smooth_stone")
LOUD = ("cherry", "gold", "jade", "oxidized", "moss", "target", "petals", "froglight", "amethyst")

# The floor blocks' values from ABOVE on today's stills (tools/camera_colour.measure, the median 'up' value of the
# pixels each block's rays hit): eyes 41792408905 (Nov 4, main: the Fable critics' run) and 41973122578 (Nov 5, this
# lane's Lotus before / after). design/vehicle_labs/camera_colours.json was measured Oct 30 on the block pack of then
# and no longer matches the stills (smooth stone 0.33 there, 0.63 on screen; tuff 0.34 there, 0.19 on the Ashborn
# capitol's terrace): the first court fix, smooth stone, passed on the old table and read LIGHTER than the shipped
# court on the after still (court crop luma 0.56 -> 0.60). The court test reads this measured model instead.
COURT_UP = {"polished_andesite": 0.59, "polished_andesite_stairs": 0.55, "smooth_stone": 0.63, "stone": 0.40,
            "stone_bricks": 0.44, "stone_brick_stairs": 0.41, "chiseled_stone_bricks": 0.42, "dark_oak_planks": 0.31,
            "tuff": 0.19, "pearlescent_froglight": 0.96}


class LotusAcademyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lv = [S.load(p) for p in NBT]
        cls.solid = [{q: n for q, (n, _) in b.blocks.items() if n not in S.AIR} for b in cls.lv]
        cls.l1 = cls.solid[0]

    def test_l1_hall_is_double_eaved(self):
        """Two curved jade roofs over the hall at L1, a storey apart: the blind massing winner (option 4, 6 / 6 both
        critics) against the shipped single roof ("half a jade roof")."""
        lips = sorted(y for (x, y, z), n in self.l1.items() if (x, z) == (L.C, 3)
                      and (n.startswith(JADE) or (n == "dark_oak_stairs"
                                                   and self.lv[0].blocks[(x, y, z)][1].get("half") == "bottom")))
        self.assertGreaterEqual(len(lips), 2, lips)
        self.assertGreaterEqual(lips[-1] - lips[0], 4, lips)

    def test_court_is_one_quiet_stone(self):
        """The court (z >= 4) at L1: its floor and everything on it below the roofs is plain stone, red lacquer, dark
        oak or a light - no cherry, gold, jade, moss, target or mosaic (critics a / b: "a chequer", "a literal
        bullseye", "four accents in the yard")."""
        loud = sorted({n for (x, y, z), n in self.l1.items() if z >= 4 and y <= 3 and any(k in n for k in LOUD)})
        self.assertEqual(loud, [])
        floor = {n for (x, y, z), n in self.l1.items() if z >= 4 and y == 0}
        self.assertTrue(floor <= set(QUIET), floor)

    def test_court_sits_under_the_halls_frame(self):
        """A roofed edge round the court: jade over at least 30 % of the court's cells (z >= 4) from above, so the plot
        reads as one compound and not "half building, half patio" (critic a, problem 1)."""
        court = [(x, z) for x in range(L.SX) for z in range(4, L.SZ)]
        roofed = [c for c in court if any(n.startswith(JADE) for (x, y, z), n in self.l1.items()
                                          if (x, z) == c and y >= 3)]
        self.assertGreaterEqual(len(roofed) / len(court), 0.30, len(roofed))

    def test_lotus_crowns_the_hall(self):
        """The academy's figure where the camera looks: a pearlescent heart in cherry petals among the top rows
        (critic b, problem 3: "no lotus anywhere ... put it on the roof crown at far-read size")."""
        top = max(q[1] for q in self.l1)
        heart = [q for q, n in self.l1.items() if n == "pearlescent_froglight" and q[1] >= top - 4]
        self.assertTrue(heart, "no lotus heart in the top rows")
        x, y, z = heart[0]
        petals = [q for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1))
                  if self.l1.get(q, "").startswith("cherry")]
        self.assertGreaterEqual(len(petals), 3, petals)

    def test_no_gold_studs_on_the_roofs(self):
        """Gold above the eaves only in the crown (within one cell of the lotus): the shipped gold eave corners read
        as "square gold studs" / "three gold dots" (critics a / b)."""
        top = max(q[1] for q in self.l1)
        hx, hy, hz = [q for q, n in self.l1.items() if n == "pearlescent_froglight" and q[1] >= top - 4][0] \
            if any(n == "pearlescent_froglight" and q[1] >= top - 4 for q, n in self.l1.items()) else (-9, -9, -9)
        studs = sorted(q for q, n in self.l1.items() if n == "gold_block" and q[1] >= 5
                       and max(abs(q[0] - hx), abs(q[2] - hz)) > 1)
        self.assertEqual(studs, [])

    def test_no_team_hues_at_any_level(self):
        """Team colour is gameplay information: no amethyst (cool) and no banners (the team system's own sign) at any
        level - the shipped L2 grew amethyst in the lotus and hung red banners."""
        bad = sorted({n for s in self.solid for n in s.values() if "amethyst" in n or n.endswith("banner")})
        self.assertEqual(bad, [])

    def test_l1_owns_more_of_the_camera_than_the_yard(self):
        """From the RTS camera's real rays, the roofs - jade and their dark oak eave stairs - are the largest material
        (>= 40 % of the building's hits) and the court floor (y 0, z >= 4) is under 15 %: shipped 35 % / 21 % (the yard
        competes). Definition re-set Nov 4 (its own commit): the dark oak hem the Fable critics asked for on every eave
        is the roofs' edge, so its stairs count as roof; the band is unchanged and the shipped L1 still fails it."""
        hits = G.shoot(self.lv[0], "rts", step=4)
        n = max(1, len(hits))
        jade = sum(1 for h in hits.values() if h[1].startswith(JADE) or h[1] == "dark_oak_stairs") / n
        yard = sum(1 for h in hits.values() if h[0][1] == 0 and h[0][2] >= 4) / n
        self.assertGreaterEqual(jade, 0.40, (jade, yard))
        self.assertLess(yard, 0.15, (jade, yard))

    # ---- the Fable critics' fixes (Nov 4, eyes 41792408905: L1 5 / 10, L3 5.5 / 10; critic-b.md, critic-c.md F1/F3) ----
    def test_court_in_the_mids_on_camera(self):
        """The court the RTS camera sees (layer 0, z >= 4) reads in the mids at every level: the median of what each
        floor cell looks like from above (COURT_UP, measured on today's stills) sits in the Art method's mids,
        0.28-0.50, under the lit jade roofs (0.61). Shipped: polished andesite at 0.59 - the lightest large area of the
        building on the noon still (critic B: "the floor owns the lights"). A floor block the model lacks fails. And at most 35 % of the court's pixels are in the lights (>= 0.50: the pale path and the
        start-type steps only) - a median alone passed the shipped Lotus court on its stone-brick border."""
        for i in range(3):
            hits = G.shoot(self.lv[i], "rts", step=4)
            floor = [n for (q, n, _f, _b) in hits.values() if q[1] == 0 and q[2] >= 4]
            self.assertEqual(sorted({n for n in floor if n not in COURT_UP}), [], "L%d: unmeasured floor" % (i + 1))
            vals = sorted(COURT_UP[n] for n in floor)
            self.assertTrue(vals, "L%d: no court seen" % (i + 1))
            med = vals[len(vals) // 2]
            self.assertTrue(0.28 <= med < 0.50, "L%d court median %.2f" % (i + 1, med))
            light = sum(1 for v in vals if v >= 0.50) / len(vals)
            self.assertLessEqual(light, 0.35, "L%d court in the lights %.2f" % (i + 1, light))

    def test_every_eave_has_a_dark_hem(self):
        """Every eave lip - the galleries' and the gate's (y 4), each hall roof's (R1, R2, R3) - is dark oak, not jade,
        at every level (critic B: the round-4 hem, the one detail round that won, was on the first roof only, "a stripe
        on one roof, not an outline of the building")."""
        lips = (4, L.R1, L.R2, L.R3)
        for i in range(3):
            jade = sorted(q for q, n in self.solid[i].items() if q[1] in lips and n == JADE + "_stairs")
            self.assertEqual(jade, [], "L%d jade eave stairs" % (i + 1))

    def test_one_gold_boss_on_the_gate(self):
        """The torii (z 8) carries at most one gold block at every level (critic B: gate_l2 / gate_l3 put three to six
        gold blocks back on the gate the camera sees first; gold studs had been taken off the eaves on Nov 1)."""
        for i in range(3):
            gold = sorted(q for q, n in self.solid[i].items() if q[2] == L.SZ - 1 and n == "gold_block")
            self.assertLessEqual(len(gold), 1, "L%d gate gold %s" % (i + 1, gold))

    def test_crown_one_gold_seed_and_a_light(self):
        """The crown keeps ONE gold cell (the seed head) and its boss is not gold (critic C F1: a gold apex is the
        Sunforged's; the pagoda and the academy both put a gold cube on top), and a lantern stands at the top for dusk
        (critic B: the shipped crown is "a faint pink smudge" at dusk)."""
        for i in range(3):
            s = self.solid[i]
            top = max(q[1] for q in s)
            gold = sorted(q for q, n in s.items() if n == "gold_block" and q[1] >= top - 5 and q[2] < L.SZ - 1)
            self.assertLessEqual(len(gold), 1, "L%d crown gold %s" % (i + 1, gold))
            light = [q for q, n in s.items() if n == "lantern" and q[1] >= top - 1]
            self.assertTrue(light, "L%d: no lantern on the crown" % (i + 1))

    def test_no_pink_crumbs_below_the_crown(self):
        """Pink only in the crown: every cherry cell at every level sits within one row of the top heart (critic B: the
        L1 crown's petal tips and the L2 crown's slab petals stay behind as pink crumbs on the tower - y 13 and y 16 at
        L3; the chain only adds or replaces)."""
        for i in range(3):
            s = self.solid[i]
            hy = max(q[1] for q, n in s.items() if n == "pearlescent_froglight")
            low = sorted(q for q, n in s.items() if n.startswith("cherry") and abs(q[1] - hy) > 1)
            self.assertEqual(low, [], "L%d pink crumbs" % (i + 1))

    # ---- lovish's pick D (Nov 5, via Eris.14: "pitched jade + stone apron"; Fable critic r1: "flat, light teal
    # slabs" where the quality bar's roofs are "the darkest, steepest shapes", and "the plinth sits on grass like a
    # crate") ----
    ROOFS = ((0, L.R1, 0, 8), (0, L.R2, 1, 7), (1, L.R3, 2, 6))   # (first level, lip y, x1, x2), each z 0..3

    def test_roofs_climb_two_steps_to_a_dark_ridge(self):
        """Each roof climbs from its lip in jade stair steps to a dark oak ridge. The side galleries (a lean-to, z 5..6):
        hem at the court, a jade stair one up, a dark oak ridge on the outer wall the camera sees. Each hall roof
        (hipped, the north side flush): the cells two in from the west, south and east edges are dark oak two up, or
        the storey or crown that rises off them - never jade. Shipped: flat jade bands, a jade ridge."""
        for i in range(3):
            s, b = self.solid[i], self.lv[i].blocks
            for lip, step, ridge, f in ((2, 1, 0, "west"), (6, 7, 8, "east")):
                for z in (5, 6):
                    self.assertEqual(s.get((step, 5, z)), JADE + "_stairs", "L%d gallery step %s" % (i + 1, (step, z)))
                    self.assertEqual(b[(step, 5, z)][1].get("facing"), f, "L%d gallery step facing" % (i + 1))
                    self.assertTrue(s.get((ridge, 5, z), "").startswith("dark_oak"),
                                    "L%d gallery ridge %s: %s" % (i + 1, (ridge, z), s.get((ridge, 5, z))))
        for lv, y, x1, x2 in self.ROOFS:
            for i in range(lv, 3):
                s = self.solid[i]
                crest = [(x, z) for x in range(x1, x2 + 1) for z in range(0, 4) if min(x - x1, x2 - x, 3 - z) >= 2]
                bad = sorted((x, z, s.get((x, y + 2, z))) for (x, z) in crest
                             if s.get((x, y + 2, z), "air").startswith(JADE) or (x, y + 2, z) not in s)
                self.assertEqual(bad, [], "L%d roof %d crest" % (i + 1, y))

    def test_dark_ridges_show_on_camera(self):
        """The ridges are seen at L1: the RTS camera's rays land on the top face of dark oak on the galleries' outer
        walls (y 5) and on the second hall roof's crest (two above its lip)."""
        hits = G.shoot(self.lv[0], "rts", step=4)
        up = [q for (q, n, f, _b) in hits.values() if n.startswith("dark_oak") and f == "up"]
        self.assertTrue([q for q in up if q[1] == 5 and q[0] in (0, L.SX - 1) and q[2] >= 4], "no gallery ridge seen")
        self.assertTrue([q for q in up if q[1] == L.R2 + 2 and q[2] <= 3], "no hall ridge seen")

    def test_seated_on_a_kerb_and_a_south_way(self):
        """The footprint never grows (BuildingLevels), so the apron is the plot's own edge: at every level layer 0's
        outer ring is a dressed stone-brick kerb, and the gate's three-wide way out (x 3..5, z 7..8) is the pale
        polished andesite path running in from the south edge. Shipped: the court's plain stone ran to the edge."""
        for i in range(3):
            s = self.solid[i]
            way = {(x, z) for x in (3, 4, 5) for z in (7, 8)}
            ring = [(x, z) for x in range(L.SX) for z in range(L.SZ)
                    if (x in (0, L.SX - 1) or z in (0, L.SZ - 1)) and (x, z) not in way]
            bad = sorted((x, z, s.get((x, 0, z))) for (x, z) in ring if s.get((x, 0, z)) != "stone_bricks")
            self.assertEqual(bad, [], "L%d kerb" % (i + 1))
            off = sorted((x, z, s.get((x, 0, z))) for (x, z) in way if s.get((x, 0, z)) != "polished_andesite")
            self.assertEqual(off, [], "L%d south way" % (i + 1))

    def test_shipped_structures_are_the_generator_build(self):
        built = (L.l1(), L.l2(), L.l3())
        with tempfile.TemporaryDirectory() as d:
            paths = [os.path.join(d, f) for f in FILES]
            for s, p in zip(built, paths):
                s.write(p)
            L.check_levels(*built, paths=paths)
            want = [S.load(p).blocks for p in paths]
        for i, f in enumerate(FILES):
            for path in (NBT[i], NBT_ASSETS[i]):
                self.assertEqual(S.load(path).blocks, want[i], path)


if __name__ == "__main__":
    unittest.main()
