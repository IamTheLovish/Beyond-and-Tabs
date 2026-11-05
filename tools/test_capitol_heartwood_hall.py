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

    def test_crown_keeps_inside_the_hall_outline(self):
        """The crown stands over the hall, not over the garden: at most 5 % of its cherry leaves stand beyond ring 5
        (the cornice), so from above the roofline and the garden frame the tree. Shipped: the lobes ran to the plot's
        edge (ring 7)."""
        crown = [q for q, n in self._solid().items() if n == "cherry_leaves"]
        out = sum(1 for q in crown if _ring(q) > 5) / max(1, len(crown))
        self.assertLessEqual(out, 0.05, out)

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
        side still read as "crate stacks at every corner" (Fable critic, round 1, Nov 5), so the roots now lie low as
        mangrove-root mats and no bark stands there at all."""
        cells = [(q, n, pr) for q, (n, pr) in self.b.blocks.items() if _bark(n) and _ring(q) >= 5 and q[1] <= 8]
        bad = [(q, n, pr.get("axis")) for q, n, pr in cells
               if not n.endswith("_log") or pr.get("axis") not in ("x", "z")]
        self.assertEqual(bad, [], "%d of %d root cells are cubes or stand on end" % (len(bad), len(cells)))

    def test_data_and_assets_copies_match(self):
        with open(NBT, "rb") as f, open(NBT_ASSETS, "rb") as g:
            self.assertEqual(f.read(), g.read())


if __name__ == "__main__":
    unittest.main()
