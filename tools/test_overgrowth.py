"""tools/overgrowth.py: the overgrowth post-process (vines, moss, dark bushes) on a structure .nbt.

lovish, Oct 28 ("lets do it"): an overgrowth pilot after his Mediterranean-villa reference - vines down the walls, moss
on the stone base, dark bushes at the wall feet, nothing bare. These tests state the rules before the tool exists:
the same seed gives the same building, vines hang only from a full solid face with the matching facing property,
a unit's walkable space (>= 4 wide x 8 tall) stays open, nothing leaves the bounding box, no block that reads wrong
(azalea is lime, rooted dirt magenta) or that the game cannot load, and a block budget so a building is overgrown,
not buried. Run: python3 -m unittest discover -s tools -p 'test_*.py'
"""
import collections
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import overgrowth as OG  # noqa: E402
from preview_structure import load_nbt  # noqa: E402

REPO = os.path.dirname(HERE)
PILOT_BEFORE = os.path.join(REPO, "design", "eyes", "overgrowth_pilot_before.nbt")
PILOT_AFTER = os.path.join(REPO, "design", "eyes", "overgrowth_pilot_after.nbt")

HORIZ = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}

# Every id the tool may ADD, written out here independently of the tool: vanilla 1.20.1 ids (an unknown id loads as
# AIR). 1.20.1 calls short grass "grass" (renamed short_grass in 1.20.3).
VANILLA_ADDS = {
    "minecraft:vine", "minecraft:spruce_leaves", "minecraft:dark_oak_leaves", "minecraft:oak_leaves",
    "minecraft:grass", "minecraft:fern", "minecraft:potted_fern", "minecraft:potted_spruce_sapling",
    "minecraft:potted_dark_oak_sapling",
    "minecraft:mossy_cobblestone", "minecraft:mossy_cobblestone_stairs", "minecraft:mossy_cobblestone_slab",
    "minecraft:mossy_cobblestone_wall", "minecraft:mossy_stone_bricks", "minecraft:mossy_stone_brick_stairs",
    "minecraft:mossy_stone_brick_slab", "minecraft:mossy_stone_brick_wall",
}
BANNED = ("azalea", "rooted_dirt", "moss_block", "moss_carpet", "glow_lichen")
SOIL = {"minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol"}
# what the house's walls are made of - a vine may hang only from these (never glass, slabs or air)
WALL = {"minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:mossy_stone_bricks",
        "minecraft:mossy_cobblestone"}

DOOR_X = range(6, 10)       # the house's south door: 4 wide (x 6..9) x 8 tall (y 1..8) in the wall at z 12
DOOR_Y = range(1, 9)


def house():
    """A 16 x 14 x 16 test building: a cobblestone pad (with a grass bed along the west), a stone-brick house
    (x/z 3..12, walls y 1..9) with a 4 x 8 door in the south wall, a glass window in the west wall and a slab roof
    that overhangs the walls by one block (eaves)."""
    palette, index, blocks = [], {}, []

    def put(x, y, z, name, **props):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": name}
            if props:
                entry["Properties"] = dict(props)
            palette.append(entry)
        blocks.append({"pos": [x, y, z], "state": index[key]})

    for x in range(16):
        for z in range(16):
            put(x, 0, z, "minecraft:grass_block" if x in (1, 2) else "minecraft:cobblestone")
    for y in range(1, 10):
        for x in range(3, 13):
            for z in range(3, 13):
                if x not in (3, 12) and z not in (3, 12):
                    continue
                if z == 12 and x in DOOR_X and y in DOOR_Y:
                    continue
                if x == 3 and z in (7, 8) and y in (4, 5):
                    put(x, y, z, "minecraft:glass")
                else:
                    put(x, y, z, "minecraft:stone_bricks")
    for x in range(2, 14):
        for z in range(2, 14):
            put(x, 10, z, "minecraft:stone_brick_slab", type="bottom", waterlogged="false")
    return {"size": [16, 14, 16], "palette": palette, "blocks": blocks, "entities": [], "DataVersion": 3465}


def grid(root):
    pal = root["palette"]
    return {tuple(b["pos"]): (pal[b["state"]]["Name"], dict(pal[b["state"]].get("Properties", {})))
            for b in root["blocks"]}


def added(before, after):
    g0, g1 = grid(before), grid(after)
    return {p: v for p, v in g1.items() if p not in g0}


def changed(before, after):
    g0, g1 = grid(before), grid(after)
    return {p: (g0[p], v) for p, v in g1.items() if p in g0 and g0[p] != v}


class Overgrowth(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = house()
        cls.after = OG.overgrow(cls.before, seed=7)
        cls.g1 = grid(cls.after)

    def test_same_seed_same_building_other_seed_differs(self):
        self.assertEqual(grid(OG.overgrow(house(), seed=7)), self.g1)
        self.assertNotEqual(grid(OG.overgrow(house(), seed=8)), self.g1)

    def test_it_actually_overgrows(self):
        """Not a no-op: vines, bushes, soil plants and mossy stone all appear on the test house."""
        names = collections.Counter(n for n, _ in added(self.before, self.after).values())
        self.assertGreaterEqual(names["minecraft:vine"], 12, names)
        self.assertGreaterEqual(sum(v for n, v in names.items() if n.endswith("_leaves")), 6, names)
        self.assertGreaterEqual(names["minecraft:grass"] + names["minecraft:fern"], 2, names)
        mossy = [p for p, (_, (n, _)) in changed(self.before, self.after).items() if "mossy" in n]
        self.assertGreaterEqual(len(mossy), 8)

    def test_vines_hang_only_from_full_solid_faces_with_the_right_facing(self):
        vines = {p: props for p, (n, props) in self.g1.items() if n == "minecraft:vine"}
        self.assertTrue(vines)
        for (x, y, z), props in vines.items():
            faces = [f for f in HORIZ if props.get(f) == "true"]
            self.assertTrue(faces, "vine at %s attached to nothing: %s" % ((x, y, z), props))
            for f in HORIZ:   # every face the vine claims is a wall block on that side - and only those
                dx, dy, dz = HORIZ[f]
                nb = self.g1.get((x + dx, y + dy, z + dz), ("minecraft:air", {}))[0]
                if props.get(f) == "true":
                    self.assertIn(nb, WALL, "vine at %s claims %s but there is %s" % ((x, y, z), f, nb))
            if props.get("up") == "true":
                self.assertIn(self.g1.get((x, y + 1, z), ("minecraft:air", {}))[0], WALL)
            for k in ("north", "south", "east", "west", "up"):
                self.assertIn(props.get(k), ("true", "false"), "vine state must spell every face: %s" % props)

    def test_vine_runs_are_one_to_five_long(self):
        cols = collections.defaultdict(set)
        for (x, y, z), (n, _) in self.g1.items():
            if n == "minecraft:vine":
                cols[(x, z)].add(y)
        for (x, z), ys in cols.items():
            run = 0
            for y in range(0, 15):
                run = run + 1 if y in ys else 0
                self.assertLessEqual(run, 5, "vine column at %s longer than 5" % ((x, z),))

    def test_the_door_and_its_approach_stay_clear(self):
        for p in added(self.before, self.after):
            x, y, z = p
            self.assertFalse(x in DOOR_X and y in DOOR_Y and 11 <= z <= 13, "%s placed in the door at %s"
                             % (self.g1[p][0], p))

    def test_walkable_space_stays_four_wide_eight_tall(self):
        """Every 4 x 4 x 8 unit footprint reachable from outside before stays reachable, or one a block beside it
        does (a bush at a wall foot is fine; a bush that closes a passage is not). Checked with the tool's own
        window search - and the house's interior must be reachable at all (the door works)."""
        r0 = OG.reachable_windows(self.before)
        r1 = OG.reachable_windows(self.after)
        self.assertIn((6, 1, 6), r0, "the test house's interior should be reachable through its door")
        for (x, y, z) in r0:
            near = any((x + dx, y, z + dz) in r1 for dx in (-1, 0, 1) for dz in (-1, 0, 1))
            self.assertTrue(near, "window at %s lost its passage" % ((x, y, z),))

    def test_nothing_outside_the_bounding_box(self):
        self.assertEqual(self.after["size"], self.before["size"])
        sx, sy, sz = self.after["size"]
        for x, y, z in self.g1:
            self.assertTrue(0 <= x < sx and 0 <= y < sy and 0 <= z < sz, (x, y, z))

    def test_walls_on_the_box_edge_grow_nothing_outside(self):
        """A building that fills its box (most do): a ledge at the box edge must not drape a vine past it."""
        palette = [{"Name": "minecraft:stone_bricks"}]
        blocks = [{"pos": [x, y, z], "state": 0} for x in range(6) for z in range(6) for y in range(6)]
        blocks += [{"pos": [x, y, z], "state": 0} for x in (2, 3) for z in (2, 3) for y in (6, 7)]
        root = {"size": [6, 10, 6], "palette": palette, "blocks": blocks, "entities": [], "DataVersion": 3465}
        for seed in range(8):
            out = OG.overgrow(root, seed=seed, density=3.0, budget=1.0)
            for x, y, z in grid(out):
                self.assertTrue(0 <= x < 6 and 0 <= y < 10 and 0 <= z < 6, (seed, (x, y, z)))

    def test_no_banned_or_unknown_blocks(self):
        for p, (n, props) in added(self.before, self.after).items():
            self.assertIn(n, VANILLA_ADDS, "added %s at %s" % (n, p))
            self.assertFalse(any(b in n for b in BANNED), n)
            if n.endswith("_leaves"):
                self.assertEqual(props.get("persistent"), "true", "leaves must not decay: %s" % (p,))
        for p, ((n0, p0), (n1, p1)) in changed(self.before, self.after).items():
            self.assertIn(n1, VANILLA_ADDS, "swapped %s -> %s at %s" % (n0, n1, p))
            self.assertEqual(n1.replace("mossy_", ""), n0, "a swap is only a mossy twin: %s -> %s" % (n0, n1))
            self.assertEqual(p0, p1, "a mossy swap keeps the shape (stairs facing, slab type)")

    def test_greens_stay_dark(self):
        """spruce leaves carry a fixed dark green (no biome tint) - they are the bulk; azalea is banned above."""
        leaves = collections.Counter(n for n, _ in added(self.before, self.after).values() if n.endswith("_leaves"))
        self.assertGreater(leaves["minecraft:spruce_leaves"], sum(leaves.values()) / 2, leaves)

    def test_soil_plants_stand_on_soil_and_moss_stays_low(self):
        for (x, y, z), (n, _) in added(self.before, self.after).items():
            if n in ("minecraft:grass", "minecraft:fern"):
                self.assertIn(self.g1.get((x, y - 1, z), ("minecraft:air", {}))[0], SOIL, (x, y, z))
        for (x, y, z), (_, (n, _)) in changed(self.before, self.after).items():
            self.assertLessEqual(y, 3, "moss climbs the base only: %s at %s" % (n, (x, y, z)))

    def test_block_budget(self):
        """Overgrown, not buried: additions plus swaps stay within the budget share of the original block count."""
        n0 = len(self.before["blocks"])
        n_add = len(added(self.before, self.after))
        n_swap = len(changed(self.before, self.after))
        self.assertLessEqual(n_add + n_swap, OG.BUDGET * n0)
        small = OG.overgrow(house(), seed=7, budget=0.02)
        self.assertLessEqual(len(added(self.before, small)) + len(changed(self.before, small)), 0.02 * n0)

    def test_original_blocks_are_kept(self):
        g0 = grid(self.before)
        for p, (n, props) in g0.items():
            self.assertIn(p, self.g1, "original block removed at %s" % (p,))

    def test_round_trip_through_nbt(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "x.nbt")
            OG.write_nbt(self.after, path)
            self.assertEqual(grid(load_nbt(path)), self.g1)

    def test_refuses_block_entities_it_cannot_carry(self):
        root = house()
        root["blocks"][0]["nbt"] = {"id": "minecraft:banner"}
        with self.assertRaises(ValueError):
            OG.overgrow(root, seed=1)


@unittest.skipUnless(os.path.isfile(PILOT_BEFORE), "pilot files not generated")
class Pilot(unittest.TestCase):
    def test_pilot_after_is_the_tool_output_of_pilot_before(self):
        """design/eyes/overgrowth_pilot_after.nbt is reproducible: the tool on the committed before at PILOT_SEED."""
        before = load_nbt(PILOT_BEFORE)
        self.assertEqual(grid(load_nbt(PILOT_AFTER)), grid(OG.overgrow(before, seed=OG.PILOT_SEED, density=OG.PILOT_DENSITY)))

    def test_pilot_keeps_every_passage(self):
        before, after = load_nbt(PILOT_BEFORE), load_nbt(PILOT_AFTER)
        r0, r1 = OG.reachable_windows(before), OG.reachable_windows(after)
        for (x, y, z) in r0:
            self.assertTrue(any((x + dx, y, z + dz) in r1 for dx in (-1, 0, 1) for dz in (-1, 0, 1)), (x, y, z))


if __name__ == "__main__":
    unittest.main()
