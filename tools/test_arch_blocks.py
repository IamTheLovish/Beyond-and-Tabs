"""Tests for our own architectural moulding blocks, slice 1 (tools/gen_arch_blocks.py) and the anti-toy camera gate
(tools/anti_toy.py). Written before both (red on main). lovish, Oct 29 2026: the Engine Shed "looks blocky and toy";
he approved "our own architectural blocks (custom models)". Lesson 6 (claude/lessons-2026-10-29-itachi2-h.md):
blocky/toy = full-block planes; the gates are >= 15 % sub-block cells on the camera faces, no run of more than 3 full
blocks without relief, bevelled (sub-block) profiles.

The in-game half (registered, item, loot, lang, every texture exists in the game jar, real collision shapes per
facing, no occlusion) is ArchBlocksGameTests.java; this half checks the files and the camera picture offline."""
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import anti_toy as T  # noqa: E402
import building_gates as G  # noqa: E402
import gen_arch_blocks as A  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "bar", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "reignofnether")
SPEC = {   # the slice-1 spec, typed by hand: id -> its properties besides facing
    **{"cornice_" + m: {"shape"} for m in ("stone", "brick", "slate", "copper")},
    **{"string_course_" + m: {"shape"} for m in ("stone", "brick", "slate")},
    **{"sill_" + m: set() for m in ("stone", "brick", "slate")},
    **{"lintel_" + m: {"keystone"} for m in ("stone", "brick", "slate")},
    **{"arch_ring_" + m: {"piece"} for m in ("stone", "brick")},
}
ALLOWED_ANGLES = {-45, -22.5, 0, 22.5, 45}


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def states(bid):
    """Every state of a block as a sorted variant key, from the generator's property table."""
    props = dict(A.BLOCKS[bid]["props"], facing=["north", "east", "south", "west"])
    keys = [[]]
    for name in sorted(props):
        keys = [k + ["%s=%s" % (name, v)] for k in keys for v in props[name]]
    return [",".join(k) for k in keys]


class Spec(unittest.TestCase):
    def test_the_generator_makes_exactly_the_spec(self):
        self.assertEqual(sorted(A.BLOCKS), sorted(SPEC))
        for bid, props in SPEC.items():
            self.assertEqual(set(A.BLOCKS[bid]["props"]), props, bid)
        self.assertEqual(A.BLOCKS["cornice_stone"]["props"]["shape"], ["inner", "outer", "straight"])
        self.assertEqual(A.BLOCKS["lintel_stone"]["props"]["keystone"], ["false", "true"])
        self.assertEqual(len(A.BLOCKS["arch_ring_stone"]["props"]["piece"]), 5)


class Files(unittest.TestCase):
    """The committed resources are what the generator makes (regenerate: python3 tools/gen_arch_blocks.py)."""

    def test_blockstates_cover_every_state_and_name_existing_models(self):
        for bid in SPEC:
            bs = json.loads(read(os.path.join(ASSETS, "blockstates", bid + ".json")))
            self.assertEqual(sorted(bs["variants"]), sorted(states(bid)), bid)
            for v in bs["variants"].values():
                self.assertTrue(v["model"].startswith("reignofnether:block/"), v)
                name = v["model"].split("/", 1)[1]
                self.assertTrue(os.path.isfile(os.path.join(ASSETS, "models", "block", name + ".json")), name)
                self.assertIn(v.get("y", 0), (0, 90, 180, 270))

    def test_committed_files_match_the_generator(self):
        for rel, text in A.files().items():
            self.assertEqual(read(os.path.join(ROOT, rel)), text, rel + " is stale: run tools/gen_arch_blocks.py")

    def test_item_model_loot_and_name(self):
        lang = json.loads(read(os.path.join(ASSETS, "lang", "en_us.json")))
        for bid in SPEC:
            item = json.loads(read(os.path.join(ASSETS, "models", "item", bid + ".json")))
            self.assertTrue(item["parent"].startswith("reignofnether:block/" + bid), item)
            loot = read(os.path.join(RES, "data", "reignofnether", "loot_tables", "blocks", bid + ".json"))
            self.assertIn('"reignofnether:%s"' % bid, loot)
            self.assertIn("block.reignofnether." + bid, lang)

    def test_registered_in_java(self):
        src = read(os.path.join(ROOT, A.JAVA_REGISTRAR))
        for bid in SPEC:
            self.assertIn('"%s"' % bid, src)


class Models(unittest.TestCase):
    def models(self):
        out = {}
        for rel, text in A.files().items():
            if "/models/block/" in rel:
                out[os.path.basename(rel)[:-5]] = json.loads(text)
        self.assertGreater(len(out), len(SPEC))
        return out

    def test_textures_are_vanilla_block_textures_only(self):
        for name, m in self.models().items():
            self.assertTrue(m["textures"], name)
            for k, v in m["textures"].items():
                self.assertTrue(v.startswith("minecraft:block/"), (name, k, v))
                # every texture is named after a vanilla 1.20.1 block (the GameTest checks the png is in the game jar)
                self.assertIn(v.split("/", 1)[1], G.VANILLA, (name, v))
            self.assertNotIn("reignofnether:", json.dumps(m["textures"]))

    def test_elements_stay_in_the_model_box_with_legal_rotations_and_uvs(self):
        for name, m in self.models().items():
            self.assertTrue(m["elements"], name)
            for e in m["elements"]:
                for a in e["from"] + e["to"]:
                    self.assertTrue(-16 <= a <= 32, (name, e))
                for i in range(3):
                    self.assertLess(e["from"][i], e["to"][i], (name, e))
                if "rotation" in e:
                    self.assertIn(e["rotation"]["angle"], ALLOWED_ANGLES, (name, e))
                self.assertTrue(e["faces"], (name, e))
                for f in e["faces"].values():
                    self.assertTrue(all(0 <= u <= 16 for u in f["uv"]), (name, f))   # no atlas bleed
                    self.assertTrue(f["texture"].startswith("#"), (name, f))
                    self.assertIn(f["texture"][1:], m["textures"], (name, f))

    def test_no_face_lies_on_the_wall_plane_behind_the_moulding(self):
        """A face at the model's back (z 0, facing north) would z-fight the wall it is mounted on."""
        for name, m in self.models().items():
            if not name.startswith(("cornice_", "string_course_")):
                continue
            for e in m["elements"]:
                if e["from"][2] == 0:
                    self.assertNotIn("north", e["faces"], (name, e))


class Collision(unittest.TestCase):
    def test_every_state_has_a_partial_collision_inside_its_cell(self):
        for bid in SPEC:
            for key in states(bid):
                props = dict(kv.split("=") for kv in key.split(","))
                boxes = A.collision(bid, props)
                self.assertTrue(boxes, (bid, key))
                vol = 0
                for b in boxes:
                    self.assertTrue(all(0 <= v <= 16 for v in b), (bid, key, b))
                    self.assertTrue(b[0] < b[3] and b[1] < b[4] and b[2] < b[5], (bid, key, b))
                    vol += (b[3] - b[0]) * (b[4] - b[1]) * (b[5] - b[2])
                self.assertLess(vol, 16 ** 3 * 0.9, (bid, key))      # never a full cube (overlaps count twice)

    def test_facings_turn_the_collision_a_quarter_at_a_time(self):
        def bounds(bs):
            return [min(b[i] for b in bs) for i in range(3)] + [max(b[i] for b in bs) for i in range(3, 6)]
        for bid in SPEC:
            props = {k: v[0] for k, v in A.BLOCKS[bid]["props"].items()}
            n = bounds(A.collision(bid, dict(props, facing="north")))
            e = bounds(A.collision(bid, dict(props, facing="east")))
            # a quarter turn clockwise seen from above: (x, z) -> (16 - z, x)
            self.assertEqual(e, [16 - n[5], n[1], n[0], 16 - n[2], n[4], n[3]], bid)


class AntiToyGate(unittest.TestCase):
    """The camera-truth gate (tools/anti_toy.py) on what the photo camera sees: building_gates.camera() picks the
    first block on each ray; each cell is then judged by that block's own silhouette from the camera side."""

    def wall(self, cells):
        return S.Build({q: (n, p) for q, (n, p) in cells.items()})

    def test_a_flat_brick_wall_fails(self):
        b = self.wall({(x, y, 0): ("bricks", {}) for x in range(9) for y in range(1, 8)})
        g = T.toy_gates(b)
        self.assertFalse(g["ok"])
        self.assertEqual(g["sub"], 0)
        self.assertEqual(g["run"], 9)

    def test_a_run_of_four_full_blocks_fails_even_with_trim_round_it(self):
        cells = {(x, y, 0): ("bricks", {}) for x in range(4) for y in range(1, 3)}
        cells.update({(x, 3, 1): ("reignofnether:cornice_stone", {"facing": "south", "shape": "straight"})
                      for x in range(4)})
        g = T.toy_gates(self.wall(cells))
        self.assertEqual(g["run"], 4)
        self.assertFalse(g["ok"])

    def test_vanilla_stairs_are_sub_block_but_not_fine_mouldings(self):
        st = T.cell_kind("stone_brick_stairs", {"facing": "north", "half": "top"}, "south")
        self.assertEqual(st, "sub")
        self.assertEqual(T.cell_kind("bricks", {}, "south"), "full")
        self.assertEqual(T.cell_kind("reignofnether:cornice_stone", {"facing": "south", "shape": "straight"},
                                     "south"), "fine")
        # turned away from the camera the cornice's back is all the camera sees of it - not a flat face either
        self.assertNotEqual(T.cell_kind("glass", {}, "south"), "full")

    def test_the_demo_wall_ours_passes_and_vanilla_fails(self):
        ours, vanilla = T.toy_gates(A.bay("ours")), T.toy_gates(A.bay("vanilla"))
        self.assertTrue(ours["ok"], ours)
        self.assertGreaterEqual(ours["sub"], 0.15)
        self.assertLessEqual(ours["run"], 3)
        self.assertFalse(vanilla["ok"], vanilla)

    def test_the_demo_structure_holds_both_bays_side_by_side(self):
        b = S.load(os.path.join(ROOT, A.DEMO))
        ids = {n for n, _ in b.blocks.values()}
        for bid in ("cornice_brick", "sill_stone", "lintel_stone", "arch_ring_stone"):
            self.assertTrue(any(n == "reignofnether:" + bid or n.startswith("reignofnether:" + bid) for n in ids), bid)
        left = S.Build({q: v for q, v in b.blocks.items() if q[0] < A.SPLIT_X})
        self.assertFalse(any(n.startswith("reignofnether:") for n, _ in left.blocks.values()))


if __name__ == "__main__":
    unittest.main()
