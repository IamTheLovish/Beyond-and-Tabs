"""tools/building_gates.py - the ONE module that measures design/architecture_rules.md's gates on a built structure.
Written before the module (lovish's rule: tests first). Every gate has a RED case first: a deliberately bad
mini-structure that must fail it, then a green one that must pass. Built on tools/schem_study.py.
Run: python3 -m pytest -q tools/test_building_gates.py
"""
import glob
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import roof_kit as R  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


# ---- mini structures (x across the south front, z depth, y up; y 0 is the foundation) -------------------------------
def box(w=9, d=7, h=5, body="bricks", base=None):
    """A hollow wall ring on a foundation: x 0..w-1, z 0..d-1, walls y 1..h; y 1 = `base` if given."""
    c = {}
    for x in range(w):
        for z in range(d):
            c[(x, 0, z)] = ("stone_bricks", {})
            if x in (0, w - 1) or z in (0, d - 1):
                for y in range(1, h + 1):
                    c[(x, y, z)] = (base if (base and y == 1) else body, {})
    return c


def gable_on(c, w, d, h, mats=R.SLATE, overhang=1, gables=True):
    """A roof_kit gable over box(w, d, h): span across z (u -> z), ridge along x; the gable walls (x 0, x w-1) rise
    under the slope so the roof sits on them."""
    roof = R.gable(d, w, axis="x", seed=1, mats=mats, overhang=overhang)
    y0 = h + 1
    for (x, y, z), v in roof.items():
        c[(x, y + y0, z)] = v
    if gables:
        for z in range(1, d - 1):
            rise = (min(z, d - 1 - z) + 1) // 2          # up to just under the course above (slab pairs)
            for x in (0, w - 1):
                for y in range(h + 1, h + 1 + rise):
                    c[(x, y, z)] = c[(x, h, z)]
    return c


def strip_verges(c, w):
    """Delete every roof cell outside the gable walls (the verge columns x -1 and x w)."""
    return {q: v for q, v in c.items() if q[0] not in (-1, w)}


def facade(c, w, h, light_every=None):
    """Pilasters every 4th x (thin wall 1 proud), a 2-wide window per bay recessed 1 (panes behind), a sill row
    proud under each window - on the south face (z = d-1 is the wall; proud is z = d)."""
    zf = max(q[2] for q in c if q[1] == 1)
    for x in range(w):
        if x % 4 == 0:
            for y in range(2, h + 1):
                c[(x, y, zf + 1)] = ("andesite_wall", {})
        elif x % 4 in (1, 2):
            for y in (3, 4):
                c.pop((x, y, zf), None)
                c[(x, y, zf - 1)] = ("glass_pane", {})
            c[(x, 2, zf + 1)] = ("polished_andesite_stairs", {"facing": "north", "half": "top"})
    if light_every:
        for x in range(2, w, light_every):
            c[(x, 3, zf - 2)] = ("lantern", {})
            c[(x, 2, zf - 2)] = ("stone_bricks", {})
    return c


def B(c):
    return S.Build(c)


# =====================================================================================================================
class G1Overhang(unittest.TestCase):
    def test_red_flat_top_has_no_overhang(self):
        got, need = G.overhang(B(box()))
        self.assertGreater(need, 0)
        self.assertEqual(got, 0)

    def test_red_roof_flush_with_the_walls(self):
        got, need = G.overhang(B(gable_on(box(), 9, 7, 5, overhang=0)))
        self.assertLess(got, need)

    def test_red_verges_deleted(self):
        """Every remaining overhang cell is still 1 block proud - schem_study's histogram would read {1: n} - but the
        gable-end perimeter is bare, so the count no longer equals the eave + verge perimeter."""
        c = strip_verges(gable_on(box(), 9, 7, 5), 9)
        self.assertEqual(set(S.eave_overhang(B(c))), {1})
        got, need = G.overhang(B(c))
        self.assertLess(got, need)

    def test_green_roof_kit_gable(self):
        got, need = G.overhang(B(gable_on(box(), 9, 7, 5)))
        self.assertEqual(need, 2 * (9 + 7) + 4)      # the Chebyshev ring round a 9 x 7 footprint
        self.assertEqual(got, need)

    def test_plinth_and_pilasters_do_not_move_the_wall_line(self):
        """A plinth 1 proud at y 1 and thin pilasters are not the wall: the ring is measured off the wall body."""
        c = facade(gable_on(box(base="polished_andesite"), 9, 7, 5), 9, 5)
        for x in range(9):
            c[(x, 1, 7)] = ("polished_andesite", {})
        got, need = G.overhang(B(c))
        self.assertEqual(need, 2 * (9 + 7) + 4)
        self.assertEqual(got, need)


class G2G3G4Shares(unittest.TestCase):
    def test_red_plain_brick_box(self):
        s = G.shares(B(gable_on(box(), 9, 7, 5)))
        self.assertLess(s["thin"], 0.08)
        self.assertGreater(s["top_share"], 0.25)

    def test_red_roof_same_family_as_walls(self):
        brick_roof = {"tones": (("bricks", "brick_stairs", "brick_slab"),) * 3, "ridge": ("bricks",),
                      "verge": ("bricks", "brick_stairs", "brick_slab"), "gutter": "brick_stairs"}
        s = G.shares(B(gable_on(box(), 9, 7, 5, mats=brick_roof)))
        self.assertEqual(s["roof_family"], s["wall_family"])

    def test_green_slate_over_brick_and_detail(self):
        c = facade(gable_on(box(base="polished_andesite"), 9, 7, 5), 9, 5, light_every=4)
        s = G.shares(B(c))
        self.assertNotEqual(s["roof_family"], s["wall_family"])
        self.assertEqual(s["wall_family"], "brick")
        self.assertGreater(s["thin"], 0.0)

    def test_ground_does_not_count(self):
        c = box()
        for x in range(-5, 15):
            for z in range(-5, 15):
                c.setdefault((x, -1, z), ("grass_block", {}))
        a, b = G.shares(B(box())), G.shares(B(c))
        self.assertAlmostEqual(a["top_share"], b["top_share"])


class G5Depth(unittest.TestCase):
    def stepped(self):
        """A blind-walled box under a stepped (ziggurat) roof: from the south the WHOLE face has several planes,
        the wall band only one."""
        c = box(9, 9, 5)
        for k in range(4):
            for x in range(-1 + k, 10 - k):
                for z in range(-1 + k, 10 - k):
                    c[(x, 6 + k, z)] = ("deepslate_tiles", {})
        return c

    def test_red_blind_box_under_a_stepped_roof(self):
        b = B(self.stepped())
        self.assertGreaterEqual(len(S.depth_layers(b, "south")), 3)    # the whole face has planes - from the roof
        d = G.depth(b, "south")
        self.assertFalse(d["ok"], d)

    def test_green_piers_windows_sills(self):
        c = facade(gable_on(box(base="polished_andesite"), 9, 7, 5), 9, 5)
        d = G.depth(B(c), "south")
        self.assertTrue(d["ok"], d)
        self.assertLessEqual(max(d["planes"].values()), 0.45)


class G7BaseCap(unittest.TestCase):
    def test_red_no_base_course(self):
        r = G.base_cap(B(gable_on(box(), 9, 7, 5)))
        self.assertFalse(r["ok"])
        self.assertTrue(any(not run["base"] for run in r["runs"]))

    def test_red_no_cap_line(self):
        r = G.base_cap(B(box(base="polished_andesite")))
        self.assertFalse(r["ok"])
        self.assertTrue(all(not run["cap"] for run in r["runs"]))

    def test_green_base_and_roof(self):
        r = G.base_cap(B(gable_on(box(base="polished_andesite"), 9, 7, 5)))
        self.assertTrue(r["ok"], r)
        self.assertEqual(len(r["runs"]), 4)        # four wall runs, each with a base course and a cap line


class LightFloor(unittest.TestCase):
    def test_red_one_light_for_three_bays(self):
        c = facade(gable_on(box(13, 7, 5, base="polished_andesite"), 13, 7, 5), 13, 5)
        c[(2, 3, 4)] = ("lantern", {})
        c[(2, 2, 4)] = ("stone_bricks", {})
        r = G.lights(B(c), faces=("south",), bay=4)
        self.assertFalse(r["ok"], r)

    def test_green_a_light_in_every_bay(self):
        c = facade(gable_on(box(13, 7, 5, base="polished_andesite"), 13, 7, 5), 13, 5, light_every=4)
        r = G.lights(B(c), faces=("south",), bay=4)
        self.assertTrue(r["ok"], r)


class G6NoChequer(unittest.TestCase):
    def chequer_roof(self):
        """A flat roof of two slabs alternating cell by cell - the r1-r7 fault, and what a diagonal hip line of
        single contrasting cells reads as from above."""
        c = box(9, 7, 5, base="polished_andesite")
        for x in range(-1, 10):
            for z in range(-1, 8):
                n = "deepslate_tile_slab" if (x + z) % 2 else "dark_oak_slab"
                c[(x, 6, z)] = (n, {"type": "bottom"})
        return c

    def test_red_alternating_roof(self):
        r = G.roof_chequer(B(self.chequer_roof()))
        self.assertGreater(r, 0.5)
        self.assertFalse(G.gates(B(self.chequer_roof()))["G6"]["ok"])

    def test_red_a_chequer_patch_in_a_slate_roof(self):
        """A 4 x 4 chequer patch of wood and slate set into an otherwise plain slate roof (r1's hip_roof patches)."""
        c = gable_on(box(9, 9, 5, base="polished_andesite"), 9, 9, 5)
        top = {}
        for (x, y, z), (n, _) in c.items():
            if y > top.get((x, z), (-1, None))[0]:
                top[(x, z)] = (y, n)
        for (x, z), (y, n) in top.items():
            if 1 <= x <= 4 and 0 <= z <= 3 and n.endswith("_slab"):
                c[(x, y, z)] = ("dark_oak_slab" if (x + z) % 2 else "deepslate_tile_slab", c[(x, y, z)][1])
        self.assertGreater(G.roof_chequer(B(c)), 0.0)
        r = G.roof_chequer(B(c))
        c2 = dict(c)
        for (x, z), (y, n) in top.items():
            if 1 <= x <= 4 and 0 <= z <= 3 and n.endswith("_slab"):
                c2[(x, y, z)] = ("deepslate_tile_slab", c[(x, y, z)][1])
        self.assertGreater(r, G.roof_chequer(B(c2)))

    def test_green_a_diagonal_hip_line_is_a_line(self):
        """A pyramid roof in one slate with its four hips one cell wide in a darker slate is a drawn line, not a
        chequer (r3 critic: hips = a 1-wide line of the dark T1 slate)."""
        c = box(9, 9, 5, base="polished_andesite")
        for x in range(-1, 10):
            for z in range(-1, 10):
                d = min(x + 1, 9 - x, z + 1, 9 - z)
                hip = abs(x - 4) == abs(z - 4)
                c[(x, 6 + d // 2, z)] = ("polished_blackstone_slab" if hip else "deepslate_tile_slab",
                                         {"type": "bottom" if d % 2 == 0 else "top"})
        self.assertLessEqual(G.roof_chequer(B(c)), G.CHEQUER_MAX)

    def test_green_roof_kit_gables(self):
        """The roof kit's three-tone courses stay under the line for every seed (they were built never to chequer)."""
        for seed in range(30):
            c = box(9, 7, 5, base="polished_andesite")
            for (x, y, z), v in R.gable(7, 9, axis="x", seed=seed).items():
                c[(x, y + 6, z)] = v
            self.assertLessEqual(G.roof_chequer(B(c)), G.CHEQUER_MAX, seed)


class GlassInFrames(unittest.TestCase):
    """r10: G5 counts a pane recessed 1 behind its frame as relief, a pane in its frame's plane as infill (it is left
    out); window_glass_set fails glass sunk 2+ behind its frame or with none."""

    def wall(self, pane_z):
        """A 7-wide brick wall 3 thick (z -2..0, y 1..4) on a stone plinth, 1-wide piers 1 proud (z 1) at x 1 and 5
        framing a 3 x 2 window (x 2..4, y 2..3) whose panes stand at z pane_z, the opening open in front of them and
        brick (the room) behind."""
        b = {(x, 0, z): ("stone", {}) for x in range(7) for z in (-2, -1, 0, 1)}
        for x in range(7):
            for y in range(1, 5):
                for z in (-2, -1, 0):
                    b[(x, y, z)] = ("bricks", {})
                if x in (1, 5):
                    b[(x, y, 1)] = ("bricks", {})
        for x in (2, 3, 4):
            for y in (2, 3):
                for z in range(pane_z, 1):
                    b.pop((x, y, z), None)
                b[(x, y, pane_z)] = ("glass_pane", {"east": "true", "west": "true"})
        return S.Build(b)

    def test_red_a_pane_sunk_two_behind_its_frame(self):
        self.assertTrue(G.window_glass_set(self.wall(-1)))

    def test_green_a_pane_one_behind_or_in_its_frame(self):
        self.assertEqual(G.window_glass_set(self.wall(0)), [])
        self.assertEqual(G.window_glass_set(self.wall(1)), [])

    def bare(self, b):
        return S.Build({q: v for q, v in b.blocks.items() if v[0] != "glass_pane"})

    def test_a_flush_pane_is_infill_a_recessed_pane_relief(self):
        """The pane in the piers' plane (z 1) is infill: G5 reads the wall as if it were not there. The same pane 1
        back (z 0) is a reveal: G5 counts it (the wall without it reads differently)."""
        flush, recessed = self.wall(1), self.wall(0)
        self.assertEqual(G.depth(flush, "south")["planes"], G.depth(self.bare(flush), "south")["planes"])
        self.assertNotEqual(G.depth(recessed, "south")["planes"], G.depth(self.bare(recessed), "south")["planes"])


class Camera(unittest.TestCase):
    """What the camera sees (r4: the dome passed '8 connected ribs' and G6 two rounds running while every photo showed
    a chequer): the first block per column from above / from a side, every cell shaded by its id."""

    def face(self, pattern):
        """A wall at z 0 from a list of rows (top row first), one character per block."""
        ids = {"a": "deepslate_tiles", "b": "polished_diorite", "s": "deepslate_tile_stairs", "c": "bricks"}
        h = len(pattern)
        return S.Build({(x, h - 1 - y, 0): (ids[ch], {}) for y, row in enumerate(pattern) for x, ch in enumerate(row)})

    def test_sees_the_first_block_per_column(self):
        b = S.Build({(0, 0, 0): ("bricks", {}), (0, 0, 1): ("glass_pane", {}), (0, 2, 0): ("air", {}),
                     (0, 1, 0): ("stone", {})})
        self.assertEqual(G.camera(b, "south")[(0, 0)][1], "glass_pane")
        self.assertEqual(G.camera(b, "north")[(0, 0)][1], "bricks")
        self.assertEqual(G.camera(b, "top")[(0, 0)][:2], ((0, 1, 0), "stone"))
        self.assertEqual(G.camera(b, "top")[(0, 1)][1], "glass_pane")

    def test_the_34_still_looks_down_from_the_south(self):
        """A ridge in front of a window at the same height hides it from the 3/4 camera, not from the side camera."""
        win = {(0, 2, -2): ("glass_pane", {})}
        ridge = {**win, (0, 3, -1): ("deepslate_tiles", {})}
        self.assertEqual(G.camera(S.Build(win), "34s")[(0, 4)][1], "glass_pane")
        self.assertEqual(G.camera(S.Build(ridge), "34s")[(0, 4)][1], "deepslate_tiles")
        self.assertEqual(G.camera(S.Build(ridge), "south")[(0, 2)][1], "glass_pane")

    def test_red_a_chequered_face(self):
        self.assertGreater(G.camera_chequer(self.face(["ababab", "bababa", "ababab"]), ("south",))["south"], 0)

    def test_red_one_alternating_row(self):
        """The r3 dome's top view: a course of slate with a pale rib every other cell - c a b a b a c."""
        self.assertGreater(G.camera_chequer(self.face(["caaaaaac", "cababac", "caaaaaac"]), ("south",))["south"], 0)

    def test_red_shape_is_a_shade(self):
        """Stairs and full blocks of ONE stone alternating cell by cell still chequer (they shade differently)."""
        self.assertGreater(G.camera_chequer(self.face(["asasas", "asasas"]), ("south",))["south"], 0)

    def test_green_lines_are_not_a_chequer(self):
        """One pale line across a field (a b a) and two hip lines meeting at an apex block are drawn lines."""
        self.assertEqual(G.camera_chequer(self.face(["aabaa", "aabaa", "aabaa"]), ("south",))["south"], 0)
        self.assertEqual(G.camera_chequer(self.face(["baaaaab", "abaaaba", "aabcbaa"]), ("south",))["south"], 0)

    def test_tone_classes(self):
        self.assertEqual([G.tone(n) for n in ("smooth_quartz", "polished_deepslate", "bricks", "glass_pane",
                                              "lantern", "stripped_birch_log", "dark_oak_planks", "smooth_stone_slab")],
                         ["pale", "dark", "mid", "glass", "light", "pale", "dark", "pale"])


class VanillaIds(unittest.TestCase):
    def test_red_ids_that_do_not_exist_in_1_20_1(self):
        bad = ["smooth_stone_stairs", "polished_andesite_wall", "quartz_brick_stairs", "calcite_stairs",
               "tuff_wall", "bone_block_stairs", "polished_granite_wall", "smooth_stone_wall", "copper_stairs"]
        self.assertEqual(G.unknown_ids(bad), sorted(bad))

    def test_green_common_ids(self):
        ok = ["bricks", "brick_wall", "smooth_stone_slab", "andesite_wall", "deepslate_tile_stairs", "iron_bars",
              "waxed_oxidized_cut_copper_stairs", "minecraft:lantern", "stripped_dark_oak_log", "white_terracotta"]
        self.assertEqual(G.unknown_ids(ok), [])

    def test_every_vanilla_id_the_repo_already_ships_is_known(self):
        """The list is checked against the game's own data we have: every minecraft: id in the committed
        structures (they load in CI's GameTests) must be in it."""
        seen = set()
        for f in glob.glob(os.path.join(ROOT, "bar/src/main/resources/data/reignofnether/structures/**/*.nbt"),
                           recursive=True):
            try:
                seen |= {n for n, _ in S.load(f).blocks.values() if ":" not in n}
            except Exception:       # noqa: BLE001 - a structure schem_study can't read is not this test's business
                continue
        self.assertGreater(len(seen), 200)
        self.assertEqual(G.unknown_ids(seen), [])


class AllGates(unittest.TestCase):
    def test_report_names_every_gate(self):
        r = G.gates(B(gable_on(box(), 9, 7, 5)))
        self.assertEqual(set(r), {"G1", "G2", "G3", "G4", "G5", "G6", "G7", "light", "ids"})
        self.assertFalse(r["G2"]["ok"])
        self.assertTrue(r["G1"]["ok"])


if __name__ == "__main__":
    unittest.main()
