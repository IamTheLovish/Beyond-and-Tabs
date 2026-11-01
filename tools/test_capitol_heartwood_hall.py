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
        """The ruined parapet (y7 on ring 4) never alternates wall / not-wall over five cells in a row: an every-other-
        cell rhythm reads as "a dotted necklace of tiny targets" at RTS zoom (critic a, massing r2)."""
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

    def test_data_and_assets_copies_match(self):
        with open(NBT, "rb") as f, open(NBT_ASSETS, "rb") as g:
            self.assertEqual(f.read(), g.read())


if __name__ == "__main__":
    unittest.main()
