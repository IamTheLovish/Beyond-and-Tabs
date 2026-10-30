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
SPEC2 = {  # slice 2 (lovish Oct 30 2026: "every one that reads" - 10 blocks; quoins replace the corner block)
    **{"pilaster_" + m: {"part"} for m in ("stone", "brick", "slate")},
    "quoin_stone": {"long_side"},
    **{"corbel_" + m: set() for m in ("stone", "brick", "copper")},
    **{"corbel_course_" + m: {"shape"} for m in ("brick", "stone", "slate")},
}
SOLID = {"quoin_stone"}   # full cubes that occlude (lovish Oct 30: quoins replace the corner block)
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
        self.assertEqual(sorted(A.BLOCKS), sorted({**SPEC, **SPEC2}))
        for bid, props in {**SPEC, **SPEC2}.items():
            self.assertEqual(set(A.BLOCKS[bid]["props"]), props, bid)
        self.assertEqual(A.BLOCKS["cornice_stone"]["props"]["shape"], ["inner", "outer", "straight"])
        self.assertEqual(A.BLOCKS["lintel_stone"]["props"]["keystone"], ["false", "true"])
        self.assertEqual(len(A.BLOCKS["arch_ring_stone"]["props"]["piece"]), 5)


class Files(unittest.TestCase):
    """The committed resources are what the generator makes (regenerate: python3 tools/gen_arch_blocks.py)."""

    def test_blockstates_cover_every_state_and_name_existing_models(self):
        for bid in A.BLOCKS:
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
        for bid in A.BLOCKS:
            item = json.loads(read(os.path.join(ASSETS, "models", "item", bid + ".json")))
            self.assertTrue(item["parent"].startswith("reignofnether:block/" + bid), item)
            loot = read(os.path.join(RES, "data", "reignofnether", "loot_tables", "blocks", bid + ".json"))
            self.assertIn('"reignofnether:%s"' % bid, loot)
            self.assertIn("block.reignofnether." + bid, lang)

    def test_registered_in_java(self):
        src = read(os.path.join(ROOT, A.JAVA_REGISTRAR))
        for bid in A.BLOCKS:
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

    def test_the_back_against_the_wall_is_culled_by_the_wall(self):
        """Models are drawn facing north; a front-mounted moulding's back is the z 16 plane against the wall: a face
        there must cull against the wall (cullface south), or it would z-fight the wall's own face."""
        for name, m in self.models().items():
            if not name.startswith(("cornice_", "string_course_", "pilaster_", "corbel_")):
                continue
            for e in m["elements"]:
                # a part that rises out of the cell (the bracket into the cornice, the crown's rim) is not culled by
                # this cell's neighbour: its back would leave a hole where the wall is not
                inside = all(0 <= e["from"][i] and e["to"][i] <= 16 for i in (0, 1))
                if inside and e["to"][2] == 16 and "south" in e["faces"]:
                    self.assertEqual(e["faces"]["south"].get("cullface"), "south", (name, e))


class Collision(unittest.TestCase):
    def test_every_state_has_a_partial_collision_inside_its_cell(self):
        for bid in A.BLOCKS:
            if bid in SOLID:
                continue
            for key in states(bid):
                props = dict(kv.split("=") for kv in key.split(","))
                boxes = A.collision(bid, props)
                self.assertTrue(boxes, (bid, key))
                vox = set()
                for b in boxes:
                    self.assertTrue(all(0 <= v <= 16 for v in b), (bid, key, b))
                    self.assertTrue(b[0] < b[3] and b[1] < b[4] and b[2] < b[5], (bid, key, b))
                    vox |= {(x, y, z) for x in range(int(b[0]), int(b[3])) for y in range(int(b[1]), int(b[4]))
                            for z in range(int(b[2]), int(b[5]))}
                self.assertLess(len(vox), 16 ** 3 * 0.9, (bid, key))      # the union, on a 1 px grid: never a cube

    def test_facings_turn_the_collision_a_quarter_at_a_time(self):
        def bounds(bs):
            return [min(b[i] for b in bs) for i in range(3)] + [max(b[i] for b in bs) for i in range(3, 6)]
        for bid in A.BLOCKS:
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

    def test_vanilla_stairs_are_half_block_steps_not_fine_mouldings(self):
        """Sub-block = relief finer than the half-block step every vanilla stair and slab makes (lesson 6: the Engine
        Shed read as blocky with stairs everywhere). Stairs, slabs and walls break a flat run but are not fine."""
        st = T.cell_kind("stone_brick_stairs", {"facing": "north", "half": "top"}, "south")
        self.assertEqual(st, "half")
        self.assertEqual(T.cell_kind("smooth_stone_slab", {"type": "bottom"}, "south"), "half")
        self.assertEqual(T.cell_kind("oak_trapdoor", {}, "south"), "fine")      # a 3 px panel is fine relief
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
        for bid in ("cornice_", "string_course_", "sill_", "lintel_", "arch_ring_"):    # each profile, any material
            self.assertTrue(any(n == "reignofnether:" + bid or n.startswith("reignofnether:" + bid) for n in ids), bid)
        left = S.Build({q: v for q, v in b.blocks.items() if q[0] < A.SPLIT_X})
        self.assertFalse(any(n.startswith("reignofnether:") for n, _ in left.blocks.values()))


# ---- slice 2: pilaster, quoin, corbel bracket, corbel course (design: design/arch_blocks/profiles.md, slice 2) -----

def geo(bid, **props):
    return A.geometry(bid, props)


def profile(bid, **props):
    """{y px row (0..15): (x0, x1, proud)} of the most proud box covering that row inside the cell."""
    out = {}
    for _, b in geo(bid, **props):
        for y in range(max(0, b[1]), min(16, b[4])):
            cand = (b[0], b[3], 16 - b[2])
            if y not in out or cand[2] > out[y][2]:
                out[y] = cand
    return out


def proud_rows(bid, **props):
    """The projection of every px row inside the cell, bottom to top."""
    p = profile(bid, **props)
    return [p[y][2] for y in range(16)]


def textures(bid, **props):
    return A.model(bid, props)["textures"]


class Slice2Spec(unittest.TestCase):
    def test_property_values(self):
        self.assertEqual(sorted(A.BLOCKS["pilaster_stone"]["props"]["part"]), ["base", "capital", "shaft", "single"])
        self.assertEqual(sorted(A.BLOCKS["quoin_stone"]["props"]["long_side"]), ["front", "side"])
        self.assertEqual(A.BLOCKS["corbel_stone"]["props"], {})
        self.assertEqual(sorted(A.BLOCKS["corbel_course_brick"]["props"]["shape"]), ["inner", "outer", "straight"])

    def test_state_counts_stay_sane(self):
        """lovish's pick (Oct 30): every material that reads - 10 blocks; each under 40 states."""
        total = 0
        for bid in SPEC2:
            n = len(states(bid))
            self.assertLessEqual(n, 40, bid)
            total += n
        self.assertEqual(total, 3 * 16 + 8 + 3 * 4 + 3 * 12)


class Pilaster(unittest.TestCase):
    """A flat pier: a 12 px shaft 4 px proud (2 px of wall each side), a 16 px plinth / abacus 6 proud at its ends
    and a torus / astragal / echinus at 5 (design pass arch2-design-b: the flare at both ends makes it a pilaster
    rather than a stripe, the recessed neck makes it carving)."""

    def test_the_shaft_is_12_wide_and_4_proud_the_whole_cell(self):
        self.assertEqual([b for _, b in geo("pilaster_stone", part="shaft")], [(2, 0, 12, 14, 16, 16)])

    def test_base_capital_and_single_profiles(self):
        self.assertEqual(proud_rows("pilaster_stone", part="base"), [6] * 4 + [5] * 2 + [4] * 10)
        self.assertEqual(proud_rows("pilaster_stone", part="capital"), [4] * 7 + [5] * 2 + [4] * 2 + [5] * 2 + [6] * 3)
        self.assertEqual(proud_rows("pilaster_stone", part="single"), [6] * 4 + [5] * 2 + [4] * 5 + [5] * 2 + [6] * 3)

    def test_the_ends_flare_two_px_each_side_of_the_shaft(self):
        base, cap = profile("pilaster_stone", part="base"), profile("pilaster_stone", part="capital")
        self.assertEqual(base[0][:2], (0, 16))
        self.assertEqual(cap[15][:2], (0, 16))
        self.assertEqual(base[15][:2], (2, 14))
        self.assertEqual(cap[0][:2], (2, 14))

    def test_the_capital_leaves_a_lit_ledge_under_the_cornice(self):
        """Pilaster under the cornice: the abacus (6 proud) stands 4 px out past the cornice's lowest fillet."""
        self.assertGreaterEqual(proud_rows("pilaster_stone", part="capital")[15] - A.CORNICE[0][2], 4)

    def test_materials(self):
        for m, tex in (("stone", "smooth_stone"), ("brick", "bricks"), ("slate", "polished_deepslate")):
            self.assertEqual(set(textures("pilaster_" + m, part="shaft").values()), {"minecraft:block/" + tex}, m)


class Quoin(unittest.TestCase):
    """The dressed corner: a full stone cube (it replaces the wall's corner block) whose two exposed faces (front =
    the model's north, side = its west) carry stones 2 px proud with 1 px joints top and bottom; the long stone runs
    8 px over the next block of wall and wraps the arris, the short one stops at the corner."""

    def boxes(self, long_side):
        return [b for k, b in geo("quoin_stone", long_side=long_side) if k != "core"]

    def test_the_core_is_a_full_stone_cube(self):
        cores = [b for k, b in geo("quoin_stone", long_side="front") if k == "core"]
        self.assertEqual(cores, [(0, 0, 0, 16, 16, 16)])
        self.assertEqual(textures("quoin_stone", long_side="front")["core"], "minecraft:block/smooth_stone")

    def test_long_and_short_stones(self):
        front = self.boxes("front")
        self.assertIn((-2, 1, -2, 24, 15, 0), front)        # the long stone on the front, round the arris
        self.assertIn((-2, 1, 0, 0, 15, 16), front)         # the short one on the side, stopping at the corner
        side = self.boxes("side")
        self.assertIn((-2, 1, -2, 0, 15, 24), side)
        self.assertIn((0, 1, -2, 16, 15, 0), side)
        for b in front + side:
            self.assertEqual((b[1], b[4]), (1, 15))           # 1 px joints: 2 px between courses
            self.assertEqual(min(b[0], b[2]), -2)             # 2 px proud

    def test_each_stone_shows_the_whole_texture_once(self):
        """The stretched face makes each stone one dressed block (smooth stone's frame round it), not a tiled run."""
        for ls in ("front", "side"):
            for e in A.model("quoin_stone", {"long_side": ls})["elements"]:
                if e["from"] == [0, 0, 0] and e["to"] == [16, 16, 16]:
                    continue
                for f in e["faces"].values():
                    self.assertEqual(f["uv"], [0, 0, 16, 16], (ls, e))

    def test_it_collides_and_registers_as_a_solid_cube(self):
        for ls in ("front", "side"):
            self.assertEqual(A.collision("quoin_stone", {"long_side": ls, "facing": "east"}), [(0, 0, 0, 16, 16, 16)])
        src = read(os.path.join(ROOT, A.JAVA_REGISTRAR))
        self.assertIn('register("quoin_stone", () -> new QuoinBlock("quoin", solidProps(Blocks.SMOOTH_STONE)))', src)

    def test_the_long_face_in_the_world(self):
        """long_side names a face of the model; in the world the long stone is on the facing, or on its
        counter-clockwise turn (the side)."""
        self.assertEqual(A.quoin_long_face("north", "front"), "north")
        self.assertEqual(A.quoin_long_face("north", "side"), "west")
        self.assertEqual(A.quoin_long_face("west", "side"), "south")
        self.assertEqual(A.quoin_long_face("south", "front"), "south")


class Corbel(unittest.TestCase):
    """The eave bracket: a console of three steps 4 / 8 / 12 proud that widen as they rise (6 / 8 / 10 px), its foot
    6 px clear of the row below, then up into the cornice's cell to the corona's soffit, 1 px behind the corona's face
    - it carries the cornice, no free top to read as a loose block. (Round-1 critic, run 41039900270: the first
    profile - four equal 8 px steps from the cell's bottom - read front-on as timber knee braces standing on the
    lintels; a front-on corbel reads by its widening, its foot clear of the lintel.)"""

    def test_a_console_that_widens_as_it_steps_out(self):
        p = profile("corbel_stone")
        self.assertNotIn(5, p)                                     # the foot clears the row below by 6 px
        self.assertEqual([p[y][2] for y in range(6, 16)], [4] * 4 + [8] * 3 + [12] * 3)
        self.assertEqual([p[y][1] - p[y][0] for y in (6, 10, 13)], [6, 8, 10])
        for y in range(6, 16):
            self.assertEqual(p[y][0] + p[y][1], 16, y)             # centred

    def test_it_rises_to_the_corona_soffit(self):
        corona = max(A.CORNICE, key=lambda layer: layer[2])
        top = max(b[4] for _, b in geo("corbel_stone"))
        self.assertEqual(top, 16 + corona[0])
        self.assertEqual(max(16 - b[2] for _, b in geo("corbel_stone")), corona[2] - 1)

    def test_materials(self):
        for m, tex in (("stone", "smooth_stone"), ("brick", "bricks"), ("copper", "weathered_cut_copper")):
            self.assertEqual(set(textures("corbel_" + m).values()), {"minecraft:block/" + tex}, m)


class CorbelCourse(unittest.TestCase):
    """The chimney crown (Engine Shed r12, lovish Oct 30: "ONE corbelled crown"): three 4 px steps 2 px out each,
    then a smooth stone coping 8 proud whose top stands 2 px above the shaft's top - a pale rim round a recessed
    flue. Straight / outer / inner like slice 1's cornice; 4 outer + 8 straight close a 2x2 shaft's ring."""

    def test_steps_and_coping(self):
        g = geo("corbel_course_brick", shape="straight")
        self.assertEqual(sorted(b for k, b in g if k == "steps"),
                         [(0, 0, 14, 16, 4, 16), (0, 4, 12, 16, 8, 16), (0, 8, 10, 16, 12, 16)])
        self.assertEqual(sorted(b for k, b in g if k == "coping"), [(0, 12, 8, 16, 16, 16), (0, 16, 8, 16, 18, 16)])

    def test_step_and_coping_textures(self):
        for m, steps in (("brick", "bricks"), ("stone", "stone_bricks"), ("slate", "deepslate_bricks")):
            t = textures("corbel_course_" + m, shape="straight")
            self.assertEqual(t["steps"], "minecraft:block/" + steps, m)
            self.assertEqual(t["coping"], "minecraft:block/smooth_stone", m)

    def test_the_outer_corner_is_the_square_at_the_shaft_corner(self):
        for _, b in geo("corbel_course_brick", shape="outer"):
            self.assertTrue(b[0] == 0 and b[3] <= 8 and b[2] >= 8 and b[5] == 16, b)

    def test_a_ring_round_a_2x2_shaft_closes_without_gaps_or_overlaps(self):
        """The coping's top faces (y 18) of a ring from A.crown_cells: exactly the 48 px square round the 32 px
        shaft, every px once (a px twice is two coplanar faces: z-fighting)."""
        cells = A.crown_cells(0, 0)
        self.assertEqual(len(cells), 12)
        self.assertEqual(sorted(s for _, s in cells.values()).count("outer"), 4)
        hits = {}
        for (cx, cz), (facing, shape) in cells.items():
            for k, b in A.geometry("corbel_course_brick", {"shape": shape}):
                if k != "coping" or b[4] != 18:
                    continue
                x0, _, z0, x1, _, z1 = A.rotate(b, facing)
                for x in range(x0, x1):
                    for z in range(z0, z1):
                        q = (cx * 16 + x, cz * 16 + z)
                        hits[q] = hits.get(q, 0) + 1
        self.assertEqual(max(hits.values()), 1)
        # the shaft is cells (1..2, 1..2) = px 16..48; the coping 8 px round it
        ring = {(x, z) for x in range(8, 56) for z in range(8, 56)}
        ring -= {(x, z) for x in range(16, 48) for z in range(16, 48)}
        self.assertEqual(set(hits), ring)


class NoZFight(unittest.TestCase):
    """No two visible faces lie in one plane facing the same way and overlap (they would flicker: z-fighting) -
    inside a model or between neighbouring blocks - on the slice-2 demo wall and sampler, where every new block
    meets slice 1's and the wall. Faces a full neighbour culls are not visible."""

    def check(self, b):
        fights = A.zfights(b)
        self.assertEqual(fights[:5], [], "%d coplanar overlapping faces" % len(fights))

    def test_the_demo_wall(self):
        self.check(A.facade2())

    def test_the_sampler(self):
        self.check(A.sampler2())


class Demo2(unittest.TestCase):
    """The slice-2 stills: a 21-wide facade photographed FRONT-on (and from the RTS camera) and a sampler of the
    other materials, both generated (design/arch_blocks/demo_wall_2.nbt, sampler_2.nbt)."""
    KINDS = ("pilaster", "quoin", "corbel", "corbel_course")

    def test_every_slice2_block_is_in_a_still(self):
        ids = set()
        for path in (A.DEMO2, A.SAMPLER2):
            ids |= {n for n, _ in S.load(os.path.join(ROOT, path)).blocks.values()}
        for bid, b in A.BLOCKS.items():
            if b["kind"] in self.KINDS:
                self.assertIn("reignofnether:" + bid, ids)

    def test_the_committed_structures_are_what_the_generator_makes(self):
        for path, fn in ((A.DEMO2, A.facade2), (A.SAMPLER2, A.sampler2)):
            have = {n for n, _ in S.load(os.path.join(ROOT, path)).blocks.values()}
            want = {n for n, _ in fn().blocks.values()}
            self.assertTrue(want - S.AIR <= have, (path, sorted(want - have)))

    def test_the_facade_passes_the_anti_toy_gate(self):
        g = T.toy_gates(A.facade2())
        self.assertTrue(g["ok"], g)

    def test_quoins_run_up_both_front_corners_in_step(self):
        b = A.facade2().blocks
        for y in range(1, A.FACADE_TOP):
            long_on_facade = []
            for x in (0, A.FACADE_W - 1):
                n, p = b[(x, y, A.FACADE_Z)]
                self.assertEqual(n, "reignofnether:quoin_stone", (x, y))
                long_on_facade.append(A.quoin_long_face(p["facing"], p["long_side"]) == "south")
            self.assertEqual(long_on_facade[0], long_on_facade[1], y)
            # long on the facade every other course, from the plinth up (the string course dies against it)
            self.assertEqual(long_on_facade[0], y % 2 == 1, y)

    def test_brackets_and_capitals_carry_the_cornice_every_other_block(self):
        b = A.facade2().blocks
        row, front = A.FACADE_TOP - 1, A.FACADE_Z + 1
        carried = sorted(x for x in range(A.FACADE_W)
                         if b.get((x, row, front), ("",))[0].startswith(("reignofnether:corbel_",
                                                                         "reignofnether:pilaster_")))
        self.assertEqual(carried, list(range(3, A.FACADE_W - 3, 2)))
        for x in carried:
            n, p = b[(x, row, front)]
            if n.startswith("reignofnether:pilaster_"):
                self.assertEqual(p["part"], "capital")
            self.assertTrue(b[(x, A.FACADE_TOP, front)][0].startswith("reignofnether:cornice_"))

    def test_the_chimney_wears_the_crown_round_an_open_flue(self):
        b = A.facade2().blocks
        crown = [q for q, (n, _) in b.items() if n.startswith("reignofnether:corbel_course_")]
        self.assertEqual(len(crown), 12)
        y = crown[0][1]
        cx, cz = A.CHIMNEY
        for x in (cx, cx + 1):          # round-1 critic: the shaft's top inside the crown is open, soot on its floor
            for z in (cz, cz + 1):
                self.assertEqual(b[(x, y, z)][0], "black_carpet")
                self.assertEqual(b[(x, y - 1, z)][0], "bricks")
        roof = max(q[1] for q, (n, _) in b.items() if "deepslate_tile" in n)
        self.assertGreaterEqual(max(q[1] for q in crown) - roof, 2)      # the stack stands clear of the ridge


if __name__ == "__main__":
    unittest.main()
