#!/usr/bin/env python3
"""Our own architectural moulding blocks, slice 1 (Lane B, Oct 29 2026; lovish: the Engine Shed "looks blocky and
toy" - he approved "our own architectural blocks (custom models)"). ONE source for everything a block needs: block
models (elements at 1-2 px steps, so the profile is finer than any vanilla stair or slab), blockstates (four facings),
item models, loot tables, the en_us names, the Java registrar and collision table, and the demo wall .nbt.

Profiles (measured from the reference builds, design/arch_blocks/profiles.md):
  cornice_*        a full cornice in one course: fillet, ovolo, dentils, corona (the drip that throws the shadow),
                   cyma and a weathered top; 13 px proud. shape straight / outer / inner (mitred corners).
  string_course_*  a storey band: drip band 5 px proud, 7 px tall; shape straight / outer / inner.
  sill_*           a window sill under the opening: sloped top, 7 px proud, a drip groove under the lip.
  lintel_*         a flat lintel over the opening, 4 px proud with a chamfered soffit; keystone=true adds a tapered
                   keystone 6 px proud.
  arch_ring_*      a segmental arch over a 3-wide opening (rise 10 px, ring 6 px, 3 proud, keystone 5 proud): pieces
                   left_springer, left_haunch, crown, right_haunch, right_springer (left / right as you face the wall).
Every block hangs IN FRONT of the wall (its back against the wall face, culled by it) and faces away from it; models
are drawn facing north (front at small z, back at z 16). Textures are vanilla only (minecraft:block/...), so the
realistic resource pack's retexture of bricks, smooth stone, polished deepslate and copper reaches them.

    python3 tools/gen_arch_blocks.py        # writes every file below; tests: tools/test_arch_blocks.py,
                                            # ArchBlocksGameTests.java
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import schem_study as S  # noqa: E402

ASSETS = "bar/src/main/resources/assets/reignofnether"
DATA = "bar/src/main/resources/data/reignofnether"
JAVA_DIR = "bar/src/main/java/com/solegendary/reignofnether/blocks/arch"
JAVA_REGISTRAR = JAVA_DIR + "/ArchBlocks.java"
JAVA_SHAPES = JAVA_DIR + "/ArchShapes.java"
LANG = ASSETS + "/lang/en_us.json"
DEMO = "design/arch_blocks/demo_wall.nbt"
FACINGS = ("north", "east", "south", "west")
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}

# material -> (texture, Java properties of the vanilla block it is cut from, display name)
MATERIALS = {
    "stone": ("smooth_stone", "Blocks.SMOOTH_STONE", "Stone"),
    "brick": ("bricks", "Blocks.BRICKS", "Brick"),
    "slate": ("polished_deepslate", "Blocks.POLISHED_DEEPSLATE", "Slate"),
    "copper": ("weathered_cut_copper", "Blocks.WEATHERED_CUT_COPPER", "Weathered Copper"),
}
SHAPES = ["inner", "outer", "straight"]
PIECES = ["left_springer", "left_haunch", "crown", "right_haunch", "right_springer"]

BLOCKS = {}
for _m in ("stone", "brick", "slate", "copper"):
    BLOCKS["cornice_" + _m] = {"kind": "cornice", "mat": _m, "props": {"shape": SHAPES}}
for _m in ("stone", "brick", "slate"):
    BLOCKS["string_course_" + _m] = {"kind": "string_course", "mat": _m, "props": {"shape": SHAPES}}
    BLOCKS["sill_" + _m] = {"kind": "sill", "mat": _m, "props": {}}
    BLOCKS["lintel_" + _m] = {"kind": "lintel", "mat": _m, "props": {"keystone": ["false", "true"]}}
for _m in ("stone", "brick"):
    BLOCKS["arch_ring_" + _m] = {"kind": "arch_ring", "mat": _m, "props": {"piece": PIECES}}

NAMES = {"cornice": "Cornice", "string_course": "String Course", "sill": "Window Sill", "lintel": "Lintel",
         "arch_ring": "Arch Ring"}

# ---- profiles: (y0, y1, projection from the wall face in px[, teeth]) - bottom to top ---------------------------
TEETH = ((1, 3), (5, 7), (9, 11), (13, 15))      # dentils: 2 px blocks, 2 px gaps
CORNICE = [(0, 2, 2), (2, 4, 3), (4, 6, 5), (6, 9, 5, 8), (9, 10, 10), (10, 13, 13), (13, 14, 12), (14, 15, 10),
           (15, 16, 7)]
STRING_COURSE = [(4, 5, 2), (5, 6, 3), (6, 9, 5), (9, 10, 4), (10, 11, 2)]


def _band(layer, shape):
    """Boxes (x0, y0, z0, x1, y1, z1) of one profile layer for a straight run or a mitred corner (north-facing:
    the wall's face is the z 16 plane; an outer corner's wall corner is the cell's south-west point, an inner corner's
    walls are its south and west sides)."""
    y0, y1, d = layer[:3]
    out = []
    if shape == "straight":
        out.append((0, y0, 16 - d, 16, y1, 16))
    elif shape == "outer":
        out.append((0, y0, 16 - d, d, y1, 16))
    else:
        out.append((0, y0, 16 - d, 16, y1, 16))
        out.append((0, y0, 0, d, y1, 16 - d))
    if len(layer) > 3:                                   # dentils from the backing d out to t
        t = layer[3]
        for a, b in TEETH:
            if shape == "straight":
                out.append((a, y0, 16 - t, b, y1, 16 - d))
            elif shape == "outer":
                if b <= d:
                    out.append((a, y0, 16 - t, b, y1, 16 - d))      # north face
                    out.append((d, y0, 16 - b, t, y1, 16 - a))      # east face
            else:
                if a >= d:
                    out.append((a, y0, 16 - t, b, y1, 16 - d))      # the north run, east of the inner wall
                if b <= 16 - t:
                    out.append((d, y0, a, t, y1, b))                # the east-facing run along the west wall
        if shape == "outer":
            out.append((d, y0, 16 - t, t, y1, 16 - d))              # the corner dentil
    return out


def _profile(layers, shape):
    return [("all", b) for layer in layers for b in _band(layer, shape)]


def sill():
    return [("all", (0, 9, 9, 16, 10, 10)),            # the drip lip (a groove behind it)
            ("all", (0, 10, 9, 16, 14, 16)),           # the sill body, 7 px proud
            ("all", (0, 14, 10, 16, 15, 16)),          # the weathered top steps back up to the wall
            ("all", (0, 15, 12, 16, 16, 16))]


def lintel(key):
    boxes = [("all", (0, 0, 13, 16, 2, 16)),           # chamfered soffit
             ("all", (0, 2, 12, 16, 15, 16)),          # the face, 4 px proud
             ("all", (0, 15, 14, 16, 16, 16))]
    if key:
        boxes += [("key", (6, 0, 10, 10, 5, 13)), ("key", (5, 5, 10, 11, 11, 13)), ("key", (4, 11, 10, 12, 16, 13)),
                  ("key", (7, 2, 9, 9, 14, 10))]        # the keystone tapers up, a raised fillet down its face
    return boxes


# the segmental arch, in u (px from the left springer's left edge, as you face the wall) and y (px above springing)
ARCH_RISE, ARCH_RING, ARCH_SPAN, ARCH_PROUD = 10, 6, 48, 3
_R = (ARCH_SPAN ** 2 / 4 + ARCH_RISE ** 2) / (2 * ARCH_RISE)
_CY = ARCH_RISE - _R
_CU = 16 + ARCH_SPAN / 2
IN_WALL = ("left_haunch", "crown", "right_haunch")


def _arc(u, r):
    dx = abs(u - _CU)
    return _CY + math.sqrt(r * r - dx * dx) if dx < r else None


def arch(piece):
    """One cell of a segmental arch over a 3-wide opening (span 48 px, rise 10, ring 6, proud 3), in the row above
    the opening's straight jambs. The haunches and crown ARE the wall there (front z 0, the wall face; back z 16): the
    ring's voussoirs run through the wall's depth (their soffit shows from below), a brick spandrel fills above the
    extrados, the opening stays open under the intrados, and the ring stands 3 px proud of the face (drawn into the
    cell in front; it does not collide there). The crown carries a keystone 5 px proud. The springers hang in front of
    the piers (their back on the pier's face, like the other mouldings) and carry the ring's tail down to the
    springing line, proud by the same 3 px."""
    cell = PIECES.index(piece)
    boxes = []
    for s in range(0, 16, 2):
        uc = cell * 16 + s + 1
        yo = _arc(uc, _R + ARCH_RING)
        if yo is None or yo <= 0.5:
            continue
        hi = min(16, round(yo))
        x0, x1 = 16 - (s + 2), 16 - s                      # the viewer's right is the model's -x (it faces north)
        if piece in IN_WALL:
            lo = max(0, round(_arc(uc, _R)))
            key = piece == "crown" and 5 <= s < 11
            if hi > lo:
                boxes.append(("ring", (x0, lo, 0, x1, hi, 16)))
                if not key:
                    boxes.append(("ring", (x0, lo, -ARCH_PROUD, x1, hi, 0)))
            if hi < 16:
                boxes.append(("wall", (x0, hi, 0, x1, 16, 16)))
        else:
            boxes.append(("ring", (x0, 0, 16 - ARCH_PROUD, x1, hi, 16)))
    if piece == "crown":
        lo = round(_arc(_CU, _R)) - 1
        boxes += [("key", (5, lo, -5, 11, 16, 0)), ("key", (7, lo + 2, -6, 9, 15, -5))]
    return boxes


def geometry(bid, props):
    """[(texture key, box)] of a block's state, drawn facing north."""
    kind = BLOCKS[bid]["kind"]
    if kind == "cornice":
        return _profile(CORNICE, props["shape"])
    if kind == "string_course":
        return _profile(STRING_COURSE, props["shape"])
    if kind == "sill":
        return sill()
    if kind == "lintel":
        return lintel(props["keystone"] == "true")
    return arch(props["piece"])


def rotate(box, facing):
    """A north box turned to `facing`, a quarter clockwise (seen from above) at a time: (x, z) -> (16 - z, x)."""
    for _ in range(ROT[facing] // 90):
        x0, y0, z0, x1, y1, z1 = box
        box = (16 - z1, y0, x0, 16 - z0, y1, x1)
    return box


def collision(bid, props):
    """Collision boxes of a state (px, inside the cell): the model's own boxes clipped to the cell (a part drawn
    proud into the next cell does not collide there), turned to its facing."""
    out = []
    for _, b in geometry(bid, props):
        c = tuple(max(0, v) for v in b[:3]) + tuple(min(16, v) for v in b[3:])
        if c[0] < c[3] and c[1] < c[4] and c[2] < c[5]:
            out.append(rotate(c, props.get("facing", "north")))
    return out


# ---- models ------------------------------------------------------------------------------------------------------
FACE_DIR = {"north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1)}


def _uv(face, b):
    """The face's texture window; a box drawn outside the cell is mapped as if moved back into it (no atlas bleed)."""
    b = list(b)
    for i in range(3):
        if b[i] < 0:
            b[i], b[i + 3] = b[i] + 16, b[i + 3] + 16
        elif b[i + 3] > 16:
            b[i], b[i + 3] = b[i] - 16, b[i + 3] - 16
    x0, y0, z0, x1, y1, z1 = b
    return {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
            "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
            "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}[face]


def _covered(face, b, boxes):
    """A face hidden by another box pressed against it (same plane, facing the other way, covering it)."""
    axis, hi = FACE_DIR[face]
    plane = b[axis + 3] if hi else b[axis]
    others = [i for i in range(3) if i != axis]
    for _, o in boxes:
        if o is b:
            continue
        if (o[axis] if hi else o[axis + 3]) != plane:
            continue
        if all(o[i] <= b[i] and o[i + 3] >= b[i + 3] for i in others):
            return True
    return False


def model(bid, props):
    tex = MATERIALS[BLOCKS[bid]["mat"]][0]
    geo = geometry(bid, props)
    used = sorted({k for k, _ in geo})
    textures = {"particle": "minecraft:block/" + tex}
    for k in used:
        if k == "wall":
            textures[k] = "minecraft:block/bricks"
        elif k == "key" or (k == "ring" and BLOCKS[bid]["mat"] == "stone"):
            textures[k] = "minecraft:block/smooth_stone"
        else:
            textures[k] = "minecraft:block/" + tex
    elements = []
    for k, b in geo:
        faces = {}
        for face, (axis, hi) in FACE_DIR.items():
            if _covered(face, b, geo):
                continue
            f = {"uv": _uv(face, b), "texture": "#" + k}
            if (hi and b[axis + 3] == 16) or (not hi and b[axis] == 0):
                f["cullface"] = face
            faces[face] = f
        if faces:
            elements.append({"from": list(b[:3]), "to": list(b[3:]), "faces": faces})
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def model_name(bid, props):
    kind = BLOCKS[bid]["kind"]
    if kind in ("cornice", "string_course"):
        return bid + "_" + props["shape"]
    if kind == "lintel":
        return bid + ("_keystone" if props["keystone"] == "true" else "")
    if kind == "arch_ring":
        return bid + "_" + props["piece"]
    return bid


def _states(bid):
    out = [{}]
    for name in sorted(BLOCKS[bid]["props"]):
        out = [dict(s, **{name: v}) for s in out for v in BLOCKS[bid]["props"][name]]
    return out


def _dump(obj):
    return json.dumps(obj, indent=2) + "\n"


def files():
    """{repo-relative path: text} of every generated file (the en_us names are merged into the lang file instead)."""
    out = {}
    for bid in BLOCKS:
        variants = {}
        for st in _states(bid):
            out["%s/models/block/%s.json" % (ASSETS, model_name(bid, st))] = _dump(model(bid, st))
            for f in FACINGS:
                key = ",".join("%s=%s" % kv for kv in sorted(dict(st, facing=f).items()))
                v = {"model": "reignofnether:block/" + model_name(bid, st)}
                if ROT[f]:
                    v["y"] = ROT[f]
                variants[key] = v
        out["%s/blockstates/%s.json" % (ASSETS, bid)] = _dump({"variants": dict(sorted(variants.items()))})
        default = {k: ("straight" if k == "shape" else "crown" if k == "piece" else "true" if k == "keystone" else v[0])
                   for k, v in BLOCKS[bid]["props"].items()}
        out["%s/models/item/%s.json" % (ASSETS, bid)] = _dump({"parent": "reignofnether:block/"
                                                               + model_name(bid, default)})
        out["%s/loot_tables/blocks/%s.json" % (DATA, bid)] = _dump(
            {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
                                                   "entries": [{"type": "minecraft:item",
                                                                "name": "reignofnether:" + bid}]}]})
    out[JAVA_SHAPES] = java_shapes()
    out[JAVA_REGISTRAR] = java_registrar()
    return out


def lang_entries():
    return {"block.reignofnether." + bid: "%s %s" % (MATERIALS[b["mat"]][2], NAMES[b["kind"]])
            for bid, b in BLOCKS.items()}


# ---- Java --------------------------------------------------------------------------------------------------------
HEADER = "// GENERATED by tools/gen_arch_blocks.py - do not edit by hand; change the generator and re-run it.\n"


def shape_key(kind, st):
    """ArchShapes key of a state: "kind/<values in property-name order>" without facing (sill: "sill/")."""
    return kind + "/" + "/".join(st[k] for k in sorted(st) if k != "facing")


def java_shapes():
    rows = []
    done = set()
    for bid, b in BLOCKS.items():
        for st in _states(bid):
            key = shape_key(b["kind"], st)
            if key in done:
                continue
            done.add(key)
            boxes = ", ".join("{%s}" % ", ".join(str(v) for v in box) for box in collision(bid, dict(st, facing="north")))
            rows.append('        BOXES.put("%s", new double[][] { %s });' % (key, boxes))
    return HEADER + """package com.solegendary.reignofnether.blocks.arch;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/** Collision boxes of every moulding state, drawn facing north (px), and their VoxelShapes per facing. */
public final class ArchShapes {
    private ArchShapes() {}

    static final Map<String, double[][]> BOXES = new HashMap<>();
    private static final Map<String, VoxelShape> CACHE = new HashMap<>();

    static {
%s
    }

    /** The shape of `key` ("kind/prop values in property-name order") turned from north to `facing`. */
    public static synchronized VoxelShape get(String key, Direction facing) {
        return CACHE.computeIfAbsent(key + "@" + facing.getName(), k -> {
            double[][] boxes = BOXES.get(key);
            if (boxes == null)
                throw new IllegalStateException("no moulding shape " + key);
            int turns = (facing.get2DDataValue() + 2) %% 4;     // quarter turns clockwise from north: S 0 W 1 N 2 E 3
            VoxelShape s = Shapes.empty();
            for (double[] b : boxes) {
                double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
                for (int i = 0; i < turns; i++) {                  // (x, z) -> (16 - z, x)
                    double nx0 = 16 - z1, nx1 = 16 - z0;
                    z0 = x0;
                    z1 = x1;
                    x0 = nx0;
                    x1 = nx1;
                }
                s = Shapes.or(s, Block.box(x0, b[1], z0, x1, b[4], z1));
            }
            return s.optimize();
        });
    }
}
""" % "\n".join(rows)


def java_registrar():
    regs = []
    for bid, b in BLOCKS.items():
        cls = {"cornice": "CorniceBlock", "string_course": "CorniceBlock", "sill": "SillBlock", "lintel": "LintelBlock",
               "arch_ring": "ArchRingBlock"}[b["kind"]]
        regs.append('    public static final RegistryObject<Block> %s = register("%s", () -> new %s("%s", props(%s)));'
                    % (bid.upper(), bid, cls, b["kind"], MATERIALS[b["mat"]][1]))
    return HEADER + """package com.solegendary.reignofnether.blocks.arch;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.ItemRegistrar;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Our own architectural moulding blocks (slice 1: cornice, string course, sill, lintel + keystone, arch ring). Models
 * use vanilla textures only, so the realistic resource pack retextures them. Partial shapes: no occlusion (a cornice
 * never culls the wall face behind it, nothing shows through it either).
 */
public final class ArchBlocks {
    private ArchBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ReignOfNether.MOD_ID);

    /** The vanilla block's look and feel (map colour, sound, strength), without its random ticks or tool rule. */
    static BlockBehaviour.Properties props(Block like) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(like.defaultMapColor())
            .strength(like.defaultDestroyTime(), like.getExplosionResistance())
            .noOcclusion();
        return p.sound(like.defaultBlockState().getSoundType());
    }

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> b = BLOCKS.register(name, block);
        ItemRegistrar.ITEMS.register(name, () -> {
            BlockItem item = new BlockItem(b.get(), new Item.Properties());
            BlockRegistrar.blockItems.computeIfAbsent(CreativeModeTabs.BUILDING_BLOCKS, k -> new ArrayList<>()).add(item);
            return item;
        });
        return b;
    }

%s

    public static void init(FMLJavaModLoadingContext context) {
        BLOCKS.register(context.getModEventBus());
    }
}
""" % "\n".join(regs)


# ---- the demo wall -------------------------------------------------------------------------------------------------
BAY_W, BAY_H, BAY_D = 9, 7, 5
SPLIT_X = 12


def bay(kind):
    """One 9 x 7 brick bay, 5 deep with a slate roof, its face to the south (z 6, a 2-thick front wall z 5..6) and
    its mouldings in front at z 7: piers x 0, 2, 6, 8, a 1-wide window x 1 and x 7 (sill row y 2, glass y 3..5,
    lintel row y 6), a 3-wide door x 3..5 (y 1..5, its arch row y 6), a plinth y 1, an impost band y 4 on the piers
    and a cornice y 7. kind "ours" uses our moulding blocks; "vanilla" the best stairs / slabs / walls can do."""
    b = {}

    def put(x, y, z, name, **props):
        b[(x, y, z)] = (name, props)

    for x in range(BAY_W):
        for y in range(1, BAY_H + 1):
            for z in range(2, 7):
                if z in (5, 6) or x in (0, BAY_W - 1) or z == 2:
                    put(x, y, z, "bricks")
    for x in range(BAY_W):                                        # the slate roof stops at the wall head, so the
        for z in range(2, 7):                                     # cornice is the eave seen from above (r1 critic:
            put(x, BAY_H + 1, z, "deepslate_tile_slab", type="bottom", waterlogged="false")   # hidden under the roof)
    for x in range(1, BAY_W - 1):                                 # a dark room behind every opening (r1 critic: the
        put(x, 1, 4, "dark_oak_planks")                           # clear panes read as white wireframe on daylight)
        for y in range(2, BAY_H):
            if x in (1, 3, 4, 5, 7):
                put(x, y, 4, "dark_oak_planks")
    for x in (1, 7):                                              # windows: a 1-block reveal, the glass behind
        for y in (3, 4, 5):
            b.pop((x, y, 6), None)
            put(x, y, 5, "glass_pane", north="false", south="false", east="true", west="true", waterlogged="false")
    for x in (3, 4, 5):                       # the opening: a glazed screen behind a 1-block reveal (identical in both
        for y in range(1, 7):                 # bays, so only the mouldings differ), up into the arch row as a fanlight
            if y < 6:
                b.pop((x, y, 6), None)
            put(x, y, 5, "glass_pane", north="false", south="false", east=str(x < 5).lower(),
                west=str(x > 3).lower(), waterlogged="false")
    Z = 7
    if kind == "ours":
        for x in (0, 1, 2, 6, 7, 8):
            put(x, 1, Z, "reignofnether:string_course_stone", facing="south", shape="straight")
        for x in (0, 2, 6, 8):
            put(x, 4, Z, "reignofnether:string_course_stone", facing="south", shape="straight")
        for x in (0, 1, 2, 6, 7, 8):                              # sills run onto the jambs (r1: floating chips)
            put(x, 2, Z, "reignofnether:sill_stone", facing="south")
        for x, key in ((0, "false"), (1, "true"), (7, "true"), (8, "false")):   # lintels bear on the jambs too
            put(x, 6, Z, "reignofnether:lintel_stone", facing="south", keystone=key)
        for i, piece in enumerate(PIECES):                        # haunches + crown ARE the wall; springers in front
            put(2 + i, 6, 6 if piece in IN_WALL else Z, "reignofnether:arch_ring_stone", facing="south", piece=piece)
        for x in range(BAY_W):                                    # pale cut stone, never the wall's brick (r1)
            put(x, BAY_H, Z, "reignofnether:cornice_stone", facing="south", shape="straight")
        put(-1, BAY_H, Z, "reignofnether:cornice_stone", facing="south", shape="outer")
        put(BAY_W, BAY_H, Z, "reignofnether:cornice_stone", facing="east", shape="outer")
        for z in range(2, Z):
            put(-1, BAY_H, z, "reignofnether:cornice_stone", facing="west", shape="straight")
            put(BAY_W, BAY_H, z, "reignofnether:cornice_stone", facing="east", shape="straight")
    else:
        st = dict(half="top", shape="straight", waterlogged="false")
        for x in (0, 1, 2, 6, 7, 8):
            put(x, 1, Z, "stone_brick_stairs", facing="north", half="bottom", shape="straight", waterlogged="false")
        for x in (0, 2, 6, 8):
            put(x, 4, Z, "stone_brick_stairs", facing="north", **st)
        for x in (0, 1, 2, 6, 7, 8):
            put(x, 2, Z, "smooth_stone_slab", type="top", waterlogged="false")
        for x in range(BAY_W):
            put(x, 6, Z, "stone_brick_stairs", facing="north", **st)
        put(3, 5, 6, "stone_brick_stairs", facing="east", **st)        # the 1890-station arch: springer stairs
        put(5, 5, 6, "stone_brick_stairs", facing="west", **st)
        for x in range(-1, BAY_W + 1):
            put(x, BAY_H, Z, "stone_brick_stairs", facing="north", **st)
        for z in range(2, Z):
            put(-1, BAY_H, z, "stone_brick_stairs", facing="east", **st)
            put(BAY_W, BAY_H, z, "stone_brick_stairs", facing="west", **st)
    return S.Build(b)


def demo():
    """Both bays side by side on a grass pad: vanilla at x 2..10, ours at x 13..21 (SPLIT_X 12)."""
    from gen_structures import Structure
    s = Structure(24, 10, 11)
    s.fill(0, 0, 0, 23, 0, 10, "grass_block", snowy="false")
    for ox, kind in ((2, "vanilla"), (13, "ours")):
        for (x, y, z), (n, pr) in bay(kind).blocks.items():
            s.set(x + ox, y, z + 1, n, **pr)
    return s


def write(path, text):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def merge_lang():
    path = os.path.join(ROOT, LANG)
    with open(path, encoding="utf-8") as f:
        text = f.read()
    have = json.loads(text)
    add = {k: v for k, v in lang_entries().items() if have.get(k) != v}
    if not add:
        return
    body = text.rstrip().rstrip("}").rstrip()
    lines = ["  %s: %s" % (json.dumps(k), json.dumps(v)) for k, v in add.items()]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(body + ",\n" + ",\n".join(lines) + "\n}\n")


def main():
    for path, text in files().items():
        write(path, text)
    merge_lang()
    out = os.path.join(ROOT, DEMO)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    demo().write(out)
    print("%d blocks, %d files" % (len(BLOCKS), len(files())))


if __name__ == "__main__":
    main()
