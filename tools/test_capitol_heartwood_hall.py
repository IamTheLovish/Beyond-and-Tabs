#!/usr/bin/env python3
"""The Verdant capitol (heartwood_hall.nbt) as the RTS camera sees it - tools/capitol_heartwood_hall.py's tests.

The World-Tree Court that shipped until Nov 2026 hid its temple under a round, tiered, mostly green canopy: two fresh
critics on the RTS stills (eyes 41263811904) gave it 4 / 4, "an overgrown green mound melting into grass - no trunk or
canopy read". Two blind massing rounds (eyes 41283644479, 41284750886; critics a / b each round) picked a temple you
can see under an uneven blossom crown that leans away from the camera. Each test here fails on the shipped hall and
holds that massing. Camera tests cast the eyes stills' real rays (tools/building_gates.shoot) into the shipped nbt.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
NBT = os.path.join(RES, "data", "reignofnether", "structures", "heartwood_hall.nbt")
NBT_ASSETS = os.path.join(RES, "assets", "reignofnether", "structures", "heartwood_hall.nbt")
C = 7
STONE = ("stone_brick", "cobblestone", "calcite")
HIDDEN_MAX = 125                   # ground cells the hall may hide from the RTS camera (main 90, round 12 165)


def _ring(q):
    return max(abs(q[0] - C), abs(q[2] - C))


def _bark(n):
    """The tree's wood (logs and wood blocks of any bark), not the stag-dryad's birch."""
    return n.endswith(("_log", "_wood")) and "birch" not in n


class HeartwoodHallTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.b = S.load(NBT)
        cls.names = {q: n for q, (n, _) in cls.b.blocks.items()}
        cls.rts = G.shoot(cls.b, "rts", step=4)

    def _share(self, pred):
        return sum(1 for h in self.rts.values() if pred(h)) / max(1, len(self.rts))

    def test_blossom_crown_leads_but_does_not_fill_the_plot(self):
        """The cherry crown is the faction's mark from the RTS camera: 25-45 % of the rays land on cherry leaves. The
        shipped hall: 3 % (a green canopy on green grass); the round-1 greybox with a plot-wide pink canopy: 59 %, which
        both critics flagged as competing with the team colours (team colour must stay the loudest)."""
        share = self._share(lambda h: "cherry_leaves" in h[1])
        self.assertGreaterEqual(share, 0.25, share)
        self.assertLessEqual(share, 0.45, share)

    def test_temple_stone_shows_from_the_rts_camera(self):
        """The temple is half the read ("a great tree over a temple"): at least 18 % of the RTS rays land on its stone
        above the ground. Shipped: 16.4 % (the canopy skirt covers it); greyboxes D / F that showed it: 33 % / 55 %."""
        share = self._share(lambda h: h[0][1] >= 1 and any(k in h[1] for k in STONE))
        self.assertGreaterEqual(share, 0.18, share)

    def test_crown_is_lobed_not_concentric(self):
        """From above the crown is uneven lobes, not rings: of the columns with leaves, at least 40 % have a different
        top height from the column mirrored through the centre. Shipped: 18 % (concentric tiers - "a bullseye / soft
        ziggurat from above", critics a / b on both massing rounds); the lobed greyboxes D / F: 68 % / 76 %."""
        top = {}
        for q, n in self.names.items():
            if n.endswith("_leaves"):
                top[(q[0], q[2])] = max(top.get((q[0], q[2]), -1), q[1])
        asym = sum(1 for (x, z), y in top.items() if top.get((2 * C - x, 2 * C - z)) != y) / max(1, len(top))
        self.assertGreaterEqual(asym, 0.40, asym)

    def test_crown_leans_away_from_the_camera(self):
        """The RTS camera looks north-east from the south-west: the crown's leaves stand more over the north-east half
        (x > z) than over the south-west half, so the temple's south and west faces show under it."""
        ne = sum(1 for q, n in self.names.items() if n.endswith("_leaves") and q[1] >= 11 and q[0] - C > q[2] - C)
        sw = sum(1 for q, n in self.names.items() if n.endswith("_leaves") and q[1] >= 11 and q[0] - C < q[2] - C)
        self.assertGreater(ne, 1.25 * sw, (ne, sw))

    def test_plot_edge_is_a_stone_kerb(self):
        """Ring 6 of the ground is a stone kerb (>= 75 % stone): the edge steps up from the grass. Shipped: half moss
        and earth, so the pad's rim melted into the grass (critic b, massing r2)."""
        r6 = [n for q, n in self.names.items() if q[1] == 0 and _ring(q) == 6]
        share = sum(1 for n in r6 if any(k in n for k in STONE)) / max(1, len(r6))
        self.assertGreaterEqual(share, 0.75, share)

    def test_parapet_has_no_merlon_rhythm(self):
        """The wall top (y7 on ring 4: a ruined parapet until the hip roof) never alternates wall / not-wall over five
        cells in a row: an every-other-cell rhythm reads as "a dotted necklace of tiny targets" at RTS zoom (critic a,
        massing r2)."""
        for side in range(4):
            cells = []
            for t in range(-3, 4):
                x, z = ((C - 4, C + t), (C + 4, C + t), (C + t, C - 4), (C + t, C + 4))[side]
                cells.append(self.names.get((x, 7, z), "air").endswith("_wall"))
            run = best = 1
            for a, b in zip(cells, cells[1:]):
                run = run + 1 if a != b else 1
                best = max(best, run)
            self.assertLess(best, 5, (side, cells))

    # ------------------------------------------------------------------ the tall tree (lovish, Nov 5)
    # After the living-crowns still (#502): "the pink tree could be taller and stick out of the building so its more
    # define" and "with branches coming out of the roof". The shipped crown sat on the parapet as a mound as wide as the
    # plot (crown underside y13 over a y8 roofline, leaves to the plot's edge), so from the RTS camera the tree and the
    # hall read as one pink lump. These hold the tall tree: a trunk seen above the roof, a crown standing clear of it
    # and inside the hall's outline, and boughs breaking out through a roof.

    def _solid(self):
        return {q: n for q, n in self.names.items() if n not in S.AIR}

    def _roofline(self):
        """The top of the hall: the highest stone over its footprint (ring <= 5)."""
        return max(q[1] for q, n in self._solid().items() if _ring(q) <= 5 and any(k in n for k in STONE))

    def _underside(self):
        """The crown's underside: the height of its lowest 5 % of cherry leaves."""
        ys = sorted(q[1] for q, n in self._solid().items() if n == "cherry_leaves")
        return ys[len(ys) // 20], ys[-1]

    def test_crown_stands_clear_above_the_roof(self):
        """The crown's underside stands at least 7 blocks over the roofline, so the trunk shows between them, and its
        top at least 18 over it. Shipped: roofline 8, underside 13, top 21."""
        roof = self._roofline()
        under, top = self._underside()
        self.assertGreaterEqual(under - roof, 7, (roof, under))
        self.assertGreaterEqual(top - roof, 18, (roof, top))

    def test_crown_spreads_past_the_hall_only_high_over_it(self):
        """The crown may be wider than the hall only because it stands high over it (lovish's reference images, Nov 5
        ~22:52Z, in Eris.14's description of them: a crown "lifted HIGH above the roof on the trunk" that "spreads wider
        than the house", sky between roof and crown). Every cherry leaf beyond ring 5 (the cornice) stands at least 12
        blocks over the roofline, and 10-35 % of the crown's cherry leaves stand there: wider than the hall, still a
        round crown over it, not a plot-wide lid. Band re-set from "at most 5 % beyond ring 5" (Nov 5, when the crown
        stood 7-10 over the roof and a wide crown would have hidden the temple). The crown kept inside the cornice
        (round 11): 0 % - too narrow for the references. Height re-set from 14 to 12 (Nov 6, Eris.14 on the round 12
        critic): the box came down from 36 to 28 so the RTS frame holds the crown and it hides less ground behind the
        hall; 28 keeps the crown's top 18 over the roof (test_crown_stands_clear_above_the_roof), and the spread starts
        12 over it."""
        roof = self._roofline()
        crown = [q for q, n in self._solid().items() if n == "cherry_leaves"]
        out = [q for q in crown if _ring(q) > 5]
        share = len(out) / max(1, len(crown))
        self.assertGreaterEqual(share, 0.10, share)
        self.assertLessEqual(share, 0.35, share)
        low = min((q[1] for q in out), default=99)
        self.assertGreaterEqual(low - roof, 12, (roof, low))

    def test_trunk_shows_between_roof_and_crown(self):
        """From the RTS camera the tree has a trunk: at least 4 % of the rays land on its bark (oak, the birch mask and
        antlers) between the roofline and the crown's underside. Shipped: 2.5 % (the crown sat on the parapet)."""
        roof = self._roofline()
        under, _ = self._underside()
        share = self._share(lambda h: roof < h[0][1] < under and ("oak" in h[1] or "birch" in h[1]))
        self.assertGreaterEqual(share, 0.04, share)

    def test_boughs_break_out_through_the_roof(self):
        """The hall has a roof (stone inside the parapet, ring <= 3, y >= 7) and boughs come out through it on at
        least three sides: a bark cell in the roof with roof stone on two sides, not on a leg that stands on the floor,
        whose bough (outside the trunk, ring >= 3, once over the roof) climbs at least 4 over the roofline. Shipped: no
        roof (the hall was open to the crown)."""
        solid = self._solid()
        roof = {q for q, n in solid.items() if _ring(q) <= 3 and q[1] >= 7 and any(k in n for k in STONE)}
        self.assertTrue(roof, "the hall has no roof")
        line = self._roofline()
        bark = {q for q, n in solid.items() if _bark(n)}
        legs = {(q[0], q[2]) for q in bark if q[1] == 2}
        sides = set()
        for q in bark:
            if (q[0], q[2]) in legs:
                continue
            if sum((q[0] + dx, q[1], q[2] + dz) in roof for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) < 2:
                continue
            seen, todo, top = {q}, [q], q[1]
            while todo:
                p = todo.pop()
                for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                    n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
                    if n in bark and n not in seen and n[1] >= q[1] and (n[1] <= line or _ring(n) >= 3) \
                            and (n[0], n[2]) not in legs:
                        seen.add(n)
                        todo.append(n)
                        top = max(top, n[1])
            if top >= line + 4:
                dx, dz = q[0] - C, q[2] - C
                sides.add(("x+" if dx > 0 else "x-") if abs(dx) >= abs(dz) else ("z+" if dz > 0 else "z-"))
        self.assertGreaterEqual(len(sides), 3, sides)

    def test_roots_are_bark_lines_not_cube_stacks(self):
        """No crate stacks at the hall's corners: any bark outside the walls (ring >= 5) up to the parapet (y <= 8) is
        a log lying along its line (axis x or z), so the camera sees bark on top, never a pale log end. Shipped: full
        oak_wood cubes standing on end - "four diagonal stacks of full oak_wood cubes ... a zipper of pale-topped cubes
        - the crate stacks" (Fable critic A, Nov 4, 4/10: the most distracting element on the still). Logs on their
        side still read as "crate stacks at every corner" (Fable critic, round 1, Nov 5), and mangrove-root mats as
        "crates" (round 7), so the roots lie low as moss mats round each corner pier's foot - and they are there: at
        least 4 moss carpets on ring 5-7 of each garden corner (#522 review N3: without this a hall with no roots
        passed)."""
        cells = [(q, n, pr) for q, (n, pr) in self.b.blocks.items() if _bark(n) and _ring(q) >= 5 and q[1] <= 8]
        bad = [(q, n, pr.get("axis")) for q, n, pr in cells
               if not n.endswith("_log") or pr.get("axis") not in ("x", "z")]
        self.assertEqual(bad, [], "%d of %d root cells are cubes or stand on end" % (len(bad), len(cells)))
        for sx, sz in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
            mat = [q for q, n in self.names.items() if n == "moss_carpet" and q[1] == 1 and _ring(q) >= 5
                   and (q[0] - C) * sx >= 4 and (q[2] - C) * sz >= 4]
            self.assertGreaterEqual(len(mat), 4, ((sx, sz), mat))

    # ------------------------------------------------------------------ round 13 (Eris.14 on the round 12 critic)
    def test_the_rts_view_holds_the_whole_tree(self):
        """At the RTS camera's normal zoom (the rts still: OrthoviewClientEvents' frame, 30 blocks tall, centred on
        the footprint at 0.3 of the height) the whole building is in the frame with a margin: its highest corner stays
        under 95 % of the half-height. Round 12 (box 36): the frame's top edge cut the crown's top off ("crown is
        clipped by the frame top; it extends above the normal camera view", Fable critic round 12)."""
        import itertools
        import numpy as np
        solid = list(self._solid())
        size = tuple(max(q[i] for q in solid) + 1 for i in range(3))
        _, c, _, r, u, hw, hh = G.still_camera(size, "rts")
        top = max((np.array(q, float) + d - c) @ u for q in solid for d in itertools.product((0, 1), repeat=3))
        self.assertLessEqual(top / hh, 0.95, (size, top / hh))

    def test_the_crown_hides_little_ground_behind_the_hall(self):
        """A unit standing behind the hall stays in sight: of the ground cells outside the footprint, at most
        HIDDEN_MAX have the RTS camera's ray to a unit's head (1.8 over the ground) blocked by the building. The hall on
        main (box 22): 90; round 11 (30): 124; round 12 (36): 165 - the crown lifted high over a lean that falls
        away from the camera hid units behind it (Eris.14 on the round 12 critic, Nov 6)."""
        import math
        p = math.radians(G.rts_pitch(30.0))
        back = (-math.cos(p) / math.sqrt(2), math.sin(p), math.cos(p) / math.sqrt(2))   # towards the camera
        solid = set(self._solid())
        top = max(q[1] for q in solid)
        hidden = 0
        for gx in range(-12, 28):
            for gz in range(-12, 28):
                if 0 <= gx < 15 and 0 <= gz < 15:
                    continue
                t = 0.0
                while 1.8 + back[1] * t <= top + 1.5:
                    t += 0.25
                    q = (math.floor(gx + 0.5 + back[0] * t), math.floor(1.8 + back[1] * t),
                         math.floor(gz + 0.5 + back[2] * t))
                    if q in solid:
                        hidden += 1
                        break
        self.assertLessEqual(hidden, HIDDEN_MAX, hidden)

    def test_every_vine_survives_the_lay(self):
        """Laid bottom up in the nbt's order with block updates (what built_blocks_still_count_after_the_world_settles
        and blocks_standing_before_the_count_still_finish_the_building do), no vine falls off: round 13's build 42139034765
        lost the vines at (3,6,2) and (11,6,2), each hung on a pier cell laid after it, and the building never counted
        as built. The replay is capitol_heartwood_hall.vines_knocked_off."""
        import capitol_heartwood_hall as hh
        self.assertEqual(hh.vines_knocked_off(self.b.blocks), [])

    def test_the_tree_is_one_piece(self):
        """Every bark cell, and the stag's birch face and antlers, joins the trunk face to face: round 12 had two loose
        bough cells, and round 13's diagonal antler arms met only at their edges, "stripped-birch stubs ... detached/
        floating" (Fable critic round 13)."""
        import capitol_heartwood_hall as hh
        wood = {q for q, n in self.names.items() if n in (hh.LOG, hh.WOOD, hh.BIRCH, hh.BIRCH_W)}
        start = (C, hh.BASE + 1, C)
        self.assertIn(start, wood)
        seen, todo = {start}, [start]
        while todo:
            p = todo.pop()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
                if q in wood and q not in seen:
                    seen.add(q)
                    todo.append(q)
        self.assertEqual(sorted(wood - seen), [])

    def test_data_and_assets_copies_match(self):
        with open(NBT, "rb") as f, open(NBT_ASSETS, "rb") as g:
            self.assertEqual(f.read(), g.read())


if __name__ == "__main__":
    unittest.main()
