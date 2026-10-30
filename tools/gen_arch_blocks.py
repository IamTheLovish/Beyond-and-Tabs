#!/usr/bin/env python3
"""Our own architectural moulding blocks, slice 1 (Lane B, Oct 29 2026; lovish: the Engine Shed "looks blocky and
toy" - he approved "our own architectural blocks (custom models)"). ONE source for everything a block needs: block
models (elements at 1-2 px steps, so the profile is finer than any vanilla stair or slab), blockstates (four facings),
item models, loot tables, the en_us names, the Java registrar and collision table, and the demo wall .nbt.

Profiles (measured from the reference builds, design/arch_blocks/profiles.md):
  cornice_*        a full cornice in one course: fillet, ovolo, dentils, corona (the drip that throws the shadow),
                   cyma and a weathered top; 13 px proud. shape straight / outer / inner (mitred corners).
  string_course_*  a storey band: drip band 5 px proud, 7 px tall; shape straight / outer / inner.
  sill_*           a window sill under the opening: sloped top, 5 px proud, a drip groove under the lip.
  lintel_*         a flat lintel over the opening, 3 px proud with a chamfered soffit; keystone=true adds a tapered
                   keystone 6 px proud.
  arch_ring_*      a segmental arch over a 3-wide opening (rise 10 px, ring 6 px, 3 proud, keystone 5 proud): pieces
                   left_springer, left_haunch, crown, right_haunch, right_springer (left / right as you face the wall).
Slice 2 (Oct 30 2026; lovish: "the better building blocks we can get as well as textures the better", "every one
that reads"; design pass itachi2-api arch2-design-b/-c):
  pilaster_*       a flat pier, 12 px shaft 4 proud, 16 px plinth / abacus 6 proud: part base / shaft / capital /
                   single, picked from the pilasters above and below.
  quoin_stone      the dressed corner: a FULL stone cube replacing the wall's corner block, stones 2 px proud on its
                   two faces, long (26 px, over the next block) / short alternating by long_side.
  corbel_*         an eave bracket: four 3 px steps up into the cornice's cell to the corona's soffit.
  corbel_course_*  a chimney crown course: three 2 px steps of the shaft's brick, a smooth stone coping 8 proud that
                   stands 2 px above the shaft's top; shape straight / outer / inner like the cornice.
Every moulding hangs IN FRONT of the wall (its back against the wall face, culled by it) and faces away from it; models
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
DEMO2 = "design/arch_blocks/demo_wall_2.nbt"
SAMPLER2 = "design/arch_blocks/sampler_2.nbt"
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
# slice 2 - optional keys: "tex" {texture key: vanilla texture}, "like" (the Java vanilla block it is cut from),
# "solid" (a full cube that occludes: registered without noOcclusion)
PARTS = ["base", "capital", "shaft", "single"]
LONG_SIDES = ["front", "side"]
COURSE_STEPS = {"brick": ("bricks", "Blocks.BRICKS"), "stone": ("stone_bricks", "Blocks.STONE_BRICKS"),
                "slate": ("deepslate_bricks", "Blocks.DEEPSLATE_BRICKS")}
for _m in ("stone", "brick", "slate"):
    BLOCKS["pilaster_" + _m] = {"kind": "pilaster", "mat": _m, "props": {"part": PARTS}}
BLOCKS["quoin_stone"] = {"kind": "quoin", "mat": "stone", "props": {"long_side": LONG_SIDES}, "solid": True}
for _m in ("stone", "brick", "copper"):
    BLOCKS["corbel_" + _m] = {"kind": "corbel", "mat": _m, "props": {}}
for _m in ("brick", "stone", "slate"):
    BLOCKS["corbel_course_" + _m] = {"kind": "corbel_course", "mat": _m, "props": {"shape": SHAPES},
                                     "tex": {"steps": COURSE_STEPS[_m][0], "coping": "smooth_stone"},
                                     "like": COURSE_STEPS[_m][1]}

NAMES = {"cornice": "Cornice", "string_course": "String Course", "sill": "Window Sill", "lintel": "Lintel",
         "arch_ring": "Arch Ring", "pilaster": "Pilaster", "quoin": "Quoin", "corbel": "Corbel",
         "corbel_course": "Corbel Course"}

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
    return [("all", (0, 10, 11, 16, 11, 12)),          # the drip lip (a groove behind it)
            ("all", (0, 11, 11, 16, 14, 16)),          # the sill body, 5 px proud (r2 critic: deeper read as shelves)
            ("all", (0, 14, 12, 16, 15, 16)),          # the weathered top steps back up to the wall
            ("all", (0, 15, 14, 16, 16, 16))]


def lintel(key):
    boxes = [("all", (0, 0, 14, 16, 2, 16)),           # chamfered soffit
             ("all", (0, 2, 13, 16, 15, 16)),          # the face, 3 px proud
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
                    px0, px1 = x0, x1
                    if piece == "crown":                   # stop at the keystone's sides: no top face shared with
                        px0, px1 = (max(x0, 11), x1) if x1 > 11 else (x0, min(x1, 5))   # it (z-fighting, slice 2)
                    if px1 > px0:
                        boxes.append(("ring", (px0, lo, -ARCH_PROUD, px1, hi, 0)))
            if hi < 16:
                boxes.append(("wall", (x0, hi, 0, x1, 16, 16)))
        else:
            boxes.append(("ring", (x0, 0, 16 - ARCH_PROUD, x1, hi, 16)))
    if piece == "crown":
        lo = round(_arc(_CU, _R)) - 1
        boxes += [("key", (5, lo, -5, 11, 16, 0)), ("key", (7, lo + 2, -6, 9, 15, -5))]
    return boxes


# slice 2 profiles (px, facing north, back on the wall at z 16) - design/arch_blocks/profiles.md, slice 2
PILASTER = {   # plinth 6 proud x 0-16, torus 5, shaft 4 x 2-14, astragal 5, neck 4, echinus 5, abacus 6
    "base": [(0, 0, 10, 16, 4, 16), (1, 4, 11, 15, 6, 16), (2, 6, 12, 14, 16, 16)],
    "shaft": [(2, 0, 12, 14, 16, 16)],
    "capital": [(2, 0, 12, 14, 7, 16), (1, 7, 11, 15, 9, 16), (2, 9, 12, 14, 11, 16), (1, 11, 11, 15, 13, 16),
                (0, 13, 10, 16, 16, 16)],
    "single": [(0, 0, 10, 16, 4, 16), (1, 4, 11, 15, 6, 16), (2, 6, 12, 14, 11, 16), (1, 11, 11, 15, 13, 16),
               (0, 13, 10, 16, 16, 16)],
}
# (y0, y1, x0, x1, proud): a console that steps out AND widens to the top, its foot 6 px above the cell's bottom
# (round-1 critic: four equal-width steps on the lintel's top read as timber knee braces between two beams)
CORBEL_STEPS = [(6, 10, 5, 11, 4), (10, 13, 4, 12, 8), (13, 16, 3, 13, 12)]
COURSE = [(0, 4, 2), (4, 8, 4), (8, 12, 6)]          # the crown's brick steps
COPING = [(12, 16, 8), (16, 18, 8)]                  # its coping; the rim above the cell stands over the shaft's top


def quoin(long_side):
    """The corner block itself (a stone cube) and its two stones, 2 px proud of its exposed faces - front (north, z 0)
    and side (west, x 0) - with 1 px joints top and bottom. The long stone wraps the arris and runs 8 px over the next
    block of wall (the north face's wall runs east, the west face's runs south); the short one stops at the corner."""
    if long_side == "front":
        stones = [(-2, 1, -2, 24, 15, 0), (-2, 1, 0, 0, 15, 16)]
    else:
        stones = [(-2, 1, -2, 0, 15, 24), (0, 1, -2, 16, 15, 0)]
    return [("core", (0, 0, 0, 16, 16, 16))] + [("stone", b) for b in stones]


def corbel():
    """A console: three steps out (4 / 8 / 12 proud) that widen as they rise (6 / 8 / 10 px), so it reads as a corbel
    front-on too (an inverted stepped wedge), its foot clear of the row below; then up into the cornice's cell to the
    corona's soffit, 1 px behind its face: no free top under the cornice (a lone top read as loose debris in slice 1)."""
    corona = max(CORNICE, key=lambda layer: layer[2])
    boxes = [("all", (x0, y0, 16 - d, x1, y1, 16)) for y0, y1, x0, x1, d in CORBEL_STEPS]
    y0, y1, x0, x1, d = CORBEL_STEPS[-1]
    return boxes + [("all", (x0, 16, 16 - (corona[2] - 1), x1, 16 + corona[0], 16))]


def corbel_course(shape):
    return ([("steps", b) for layer in COURSE for b in _band(layer, shape)]
            + [("coping", b) for layer in COPING for b in _band(layer, shape)])


def geometry(bid, props):
    """[(texture key, box)] of a block's state, drawn facing north."""
    kind = BLOCKS[bid]["kind"]
    if kind == "pilaster":
        return [("all", b) for b in PILASTER[props["part"]]]
    if kind == "quoin":
        return quoin(props["long_side"])
    if kind == "corbel":
        return corbel()
    if kind == "corbel_course":
        return corbel_course(props["shape"])
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
        if k in BLOCKS[bid].get("tex", {}):
            textures[k] = "minecraft:block/" + BLOCKS[bid]["tex"][k]
        elif k == "wall":
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
            # a quoin's stone shows the whole texture once (smooth stone's frame round each stone), not a tiled run
            f = {"uv": [0, 0, 16, 16] if k == "stone" else _uv(face, b), "texture": "#" + k}
            inside = all(b[i] >= 0 and b[i + 3] <= 16 for i in range(3) if i != axis)
            if inside and ((hi and b[axis + 3] == 16) or (not hi and b[axis] == 0)):
                f["cullface"] = face             # only a face inside its cell's side; one past it is not hidden there
            faces[face] = f
        if faces:
            elements.append({"from": list(b[:3]), "to": list(b[3:]), "faces": faces})
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def model_name(bid, props):
    kind = BLOCKS[bid]["kind"]
    if kind in ("cornice", "string_course", "corbel_course"):
        return bid + "_" + props["shape"]
    if kind == "pilaster":
        return bid + "_" + props["part"]
    if kind == "quoin":
        return bid + "_" + props["long_side"]
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
        default = {k: {"shape": "straight", "piece": "crown", "keystone": "true", "part": "single",
                       "long_side": "front"}.get(k, v[0]) for k, v in BLOCKS[bid]["props"].items()}
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
               "arch_ring": "ArchRingBlock", "pilaster": "PilasterBlock", "quoin": "QuoinBlock",
               "corbel": "CorbelBlock", "corbel_course": "CorniceBlock"}[b["kind"]]
        regs.append('    public static final RegistryObject<Block> %s = register("%s", () -> new %s("%s", %s(%s)));'
                    % (bid.upper(), bid, cls, b["kind"], "solidProps" if b.get("solid") else "props",
                       b.get("like", MATERIALS[b["mat"]][1])))
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
 * Our own architectural moulding blocks (slice 1: cornice, string course, sill, lintel + keystone, arch ring; slice 2:
 * pilaster, quoin, corbel bracket, corbel course). Models use vanilla textures only, so the realistic resource pack
 * retextures them. Mouldings have partial shapes and no occlusion (a cornice never culls the wall face behind it,
 * nothing shows through it either); the quoin is a full stone cube that occludes like the wall block it replaces.
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

    /** The same, as a full block that occludes like the wall block it replaces (the quoins). */
    static BlockBehaviour.Properties solidProps(Block like) {
        return BlockBehaviour.Properties.of().mapColor(like.defaultMapColor())
            .strength(like.defaultDestroyTime(), like.getExplosionResistance())
            .sound(like.defaultBlockState().getSoundType());
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
            put(x, 1, Z, "reignofnether:string_course_slate", facing="south", shape="straight")   # dark base
        for x in (0, 2, 6, 8):
            put(x, 4, Z, "reignofnether:string_course_brick", facing="south", shape="straight")   # quiet impost
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


# ---- slice 2: the facade photographed front-on, the materials sampler --------------------------------------------
FACADE_W, FACADE_Z, FACADE_TOP = 21, 6, 8     # 21 wide, front wall at z 6 (mouldings at z 7), the cornice row y 8
WINDOWS = ((3, 5), (9, 11), (15, 17))
PILASTERS = (7, 13)
CHIMNEY = (14, 2)                             # the 2x2 stack's north-west cell
CHIMNEY_TOP = 12                              # the crown's row (the roof's ridge is y 10)
CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}


def quoin_long_face(facing, long_side):
    """The world face a quoin state carries its long stone on: the facing (front) or its counter-clockwise turn."""
    return facing if long_side == "front" else CCW[facing]


def crown_cells(x0, z0):
    """{(x, z): (facing, shape)} of a corbel course ring round a 2x2 shaft whose cells are (x0 + 1..2, z0 + 1..2):
    straights face away from the shaft, the outer corners sit on the diagonals (one-handed like the cornice)."""
    out = {}
    for i in (1, 2):
        out[(x0 + i, z0)] = ("north", "straight")
        out[(x0 + i, z0 + 3)] = ("south", "straight")
        out[(x0, z0 + i)] = ("west", "straight")
        out[(x0 + 3, z0 + i)] = ("east", "straight")
    out[(x0 + 3, z0)] = ("north", "outer")
    out[(x0 + 3, z0 + 3)] = ("east", "outer")
    out[(x0, z0 + 3)] = ("south", "outer")
    out[(x0, z0)] = ("west", "outer")
    return out


def _pane(x, lo, hi, **kw):
    return dict(north="false", south="false", east=str(x < hi).lower(), west=str(x > lo).lower(), waterlogged="false")


def flue(put, x0, y, z0):
    """A 2x2 shaft's top row inside its crown left open, soot on its floor: a dark flue in a pale rim from above
    (round-1 critic: a filled top read as a pedestal or planter from the RTS camera)."""
    for x in (x0, x0 + 1):
        for z in (z0, z0 + 1):
            put(x, y, z, "black_carpet")


def facade2():
    """A 21 x 8 brick facade, 7 deep, its face to the south (front wall z 6, mouldings at z 7), for a FRONT-on still:
    quoins up both corners (long on the facade in the odd courses, so the plinth's string course dies against the
    long stone), two stone pilasters (base y 1, capital y 7) dividing three bays, a slate plinth string course, sills
    y 2 and lintels y 6 bearing on the jambs, a segmental arch over the middle window, brackets every other block under
    the cornice (the capitals take their slots), a slate roof behind the cornice to a ridge at y 10 and a 2x2 brick
    chimney (x 14-15, z 2-3) with the corbel course crown at y 12. Windows are 3 tall (y 3-5): no run of more than 3
    plain bricks anywhere on the camera face (the anti-toy gate)."""
    b = {}
    W, Z, T = FACADE_W, FACADE_Z, FACADE_TOP
    F = Z + 1

    def put(x, y, z, name, **props):
        b[(x, y, z)] = (name, props)

    for x in range(W):
        for y in range(1, T + 1):
            for z in range(Z + 1):
                put(x, y, z, "bricks")
    for lo, hi in WINDOWS:                                        # a reveal, the glass, a dark room behind
        for x in range(lo, hi + 1):
            for y in range(3, 6 + (lo == 9)):                     # the arched one up into its arch row
                if y < 6:
                    b.pop((x, y, Z))
                put(x, y, Z - 1, "glass_pane", **_pane(x, lo, hi))
                put(x, y, Z - 2, "dark_oak_planks")
    for y in range(1, T):                                         # quoins: long on the facade in the odd courses
        odd = y % 2 == 1
        put(0, y, Z, "reignofnether:quoin_stone", facing="west", long_side="side" if odd else "front")
        put(W - 1, y, Z, "reignofnether:quoin_stone", facing="south", long_side="front" if odd else "side")
        put(0, y, 0, "reignofnether:quoin_stone", facing="north", long_side="side" if odd else "front")
        put(W - 1, y, 0, "reignofnether:quoin_stone", facing="east", long_side="front" if odd else "side")
    for x in PILASTERS:
        for y in range(1, T):
            put(x, y, F, "reignofnether:pilaster_stone", facing="south",
                part="base" if y == 1 else "capital" if y == T - 1 else "shaft")
    for x in range(2, W - 2):                                     # the plinth: x 1 / 19 carry the long quoin stone
        if x not in PILASTERS:
            put(x, 1, F, "reignofnether:string_course_slate", facing="south", shape="straight")
    for x in range(1, W - 1):
        if x in PILASTERS:
            continue
        put(x, 2, F, "reignofnether:sill_stone", facing="south")
        if x < 7 or x > 13:                                       # lintels bear on the jambs, keystones mid-window
            put(x, 6, F, "reignofnether:lintel_stone", facing="south", keystone=str(x in (4, 16)).lower())
    for i, piece in enumerate(PIECES):                            # the middle window's arch, as in slice 1
        put(8 + i, 6, Z if piece in IN_WALL else F, "reignofnether:arch_ring_stone", facing="south", piece=piece)
    for x in range(3, W - 3, 2):                                  # brackets every other block, capitals between
        if x not in PILASTERS:
            put(x, T - 1, F, "reignofnether:corbel_stone", facing="south")
    for x in range(W):
        put(x, T, F, "reignofnether:cornice_stone", facing="south", shape="straight")
    put(-1, T, F, "reignofnether:cornice_stone", facing="south", shape="outer")
    put(W, T, F, "reignofnether:cornice_stone", facing="east", shape="outer")
    for z in range(Z + 1):
        put(-1, T, z, "reignofnether:cornice_stone", facing="west", shape="straight")
        put(W, T, z, "reignofnether:cornice_stone", facing="east", shape="straight")
    for x in range(W):                                            # the slate roof, eaves behind the cornice
        stairs = dict(half="bottom", shape="straight", waterlogged="false")
        put(x, T + 1, Z, "deepslate_tile_stairs", facing="north", **stairs)
        put(x, T + 1, 0, "deepslate_tile_stairs", facing="south", **stairs)
        for z in range(1, Z):
            put(x, T + 1, z, "deepslate_tiles")
        put(x, T + 2, Z - 1, "deepslate_tile_stairs", facing="north", **stairs)
        put(x, T + 2, 1, "deepslate_tile_stairs", facing="south", **stairs)
        for z in range(2, Z - 1):
            put(x, T + 2, z, "deepslate_tile_slab", type="bottom", waterlogged="false")
    cx, cz = CHIMNEY
    for y in range(T + 1, CHIMNEY_TOP):                           # the top row stays open: the flue (round-1 critic:
                                                                  # a filled top read as a pedestal from above)
        for x in (cx, cx + 1):
            for z in (cz, cz + 1):
                put(x, y, z, "bricks")
    for (x, z), (facing, shape) in crown_cells(cx - 1, cz - 1).items():
        put(x, CHIMNEY_TOP, z, "reignofnether:corbel_course_brick", facing=facing, shape=shape)
    flue(put, cx, CHIMNEY_TOP, cz)
    return S.Build(b)


def sampler2():
    """The other slice-2 materials, faces to the south: a stone-brick wall (x 0-8, z 1-2, y 1-5) with a slate and a
    brick pilaster and a single stone one, a brick cornice on brick and weathered copper brackets; three 2x2 stacks
    (brick, stone brick, deepslate brick; z 1-2, y 1-5) each with its own corbel course crown at y 5."""
    b = {}

    def put(x, y, z, name, **props):
        b[(x, y, z)] = (name, props)

    for x in range(9):
        for y in range(1, 6):
            for z in (1, 2):
                put(x, y, z, "stone_bricks")
    for x, mat in ((1, "slate"), (4, "brick")):
        for y in range(1, 5):
            put(x, y, 3, "reignofnether:pilaster_" + mat, facing="south",
                part="base" if y == 1 else "capital" if y == 4 else "shaft")
    put(7, 2, 3, "reignofnether:pilaster_stone", facing="south", part="single")
    put(2, 4, 3, "reignofnether:corbel_brick", facing="south")
    put(6, 4, 3, "reignofnether:corbel_copper", facing="south")
    for x in range(9):
        put(x, 5, 3, "reignofnether:cornice_brick", facing="south", shape="straight")
    for x0, shaft, mat in ((10, "bricks", "brick"), (15, "stone_bricks", "stone"), (20, "deepslate_bricks", "slate")):
        for y in range(1, 5):                                     # the top row open inside the crown: the flue
            for x in (x0 + 1, x0 + 2):
                for z in (1, 2):
                    put(x, y, z, shaft)
        for (x, z), (facing, shape) in crown_cells(x0, 0).items():
            put(x, 5, z, "reignofnether:corbel_course_" + mat, facing=facing, shape=shape)
        flue(put, x0 + 1, 5, 1)
    return S.Build(b)


def stamp(build, margin=3):
    """A build on a grass pad, `margin` blocks round it (the eyes structure photos stamp it on a flat site)."""
    from gen_structures import Structure
    qs = list(build.blocks)
    x0, y0, z0 = (min(q[i] for q in qs) for i in range(3))
    x1, y1, z1 = (max(q[i] for q in qs) for i in range(3))
    s = Structure(x1 - x0 + 1 + 2 * margin, y1 - min(y0, 1) + 3, z1 - z0 + 1 + 2 * margin)
    s.fill(0, 0, 0, s.size[0] - 1, 0, s.size[2] - 1, "grass_block", snowy="false")
    for (x, y, z), (n, pr) in build.blocks.items():
        s.set(x - x0 + margin, y, z - z0 + margin, n, **pr)
    return s


# ---- z-fighting: coplanar faces facing the same way ---------------------------------------------------------------
TURN = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}
STEP = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0),
        "down": (0, -1, 0)}


def _turned(face, facing):
    for _ in range(ROT[facing] // 90):
        face = TURN[face]
    return face


def _occludes(cell):
    """A block that hides the faces culled against it: a vanilla full cube or our solid quoin."""
    if cell is None:
        return False
    n, pr = cell
    if n.startswith("reignofnether:"):
        return bool(BLOCKS.get(n.split(":", 1)[1], {}).get("solid"))
    import anti_toy
    return anti_toy.cell_kind(n, pr) == "full"


def _world_faces(build):
    """Every face a camera could see: (owner, world face, plane px, rect px) - our models' elements turned to their
    facing, vanilla full cubes' six sides; a face whose cullface points at an occluding neighbour is left out."""
    out = []
    for q, (n, pr) in build.blocks.items():
        if n.startswith("reignofnether:"):
            bid = n.split(":", 1)[1]
            facing = pr.get("facing", "north")
            st = {k: v for k, v in pr.items() if k != "facing"}
            elems = [(tuple(e["from"] + e["to"]), e["faces"]) for e in model(bid, st)["elements"]]
        elif _occludes((n, pr)):
            facing = "north"
            elems = [((0, 0, 0, 16, 16, 16), {f: {"cullface": f} for f in FACE_DIR})]
        else:
            continue
        for i, (box, faces) in enumerate(elems):
            wb = rotate(box, facing)
            for face, spec in faces.items():
                wf = _turned(face, facing)
                cull = spec.get("cullface")
                if cull:
                    d = STEP[_turned(cull, facing)]
                    if _occludes(build.blocks.get((q[0] + d[0], q[1] + d[1], q[2] + d[2]))):
                        continue
                axis, hi = FACE_DIR[wf]
                plane = q[axis] * 16 + (wb[axis + 3] if hi else wb[axis])
                rect = tuple((q[a] * 16 + wb[a], q[a] * 16 + wb[a + 3]) for a in range(3) if a != axis)
                out.append(((q, i, n), wf, plane, rect))
    return out


def zfights(build):
    """[(face, owner a, owner b)] of visible faces in one plane, facing the same way, overlapping by an area - they
    flicker (z-fighting). At least one of the two is ours (vanilla against vanilla is the game's own business)."""
    groups = {}
    for owner, wf, plane, rect in _world_faces(build):
        groups.setdefault((wf, plane), []).append((owner, rect))
    out = []
    for (wf, plane), faces in groups.items():
        for i in range(len(faces)):
            for j in range(i + 1, len(faces)):
                (a, ra), (c, rc) = faces[i], faces[j]
                if not (a[2].startswith("reignofnether:") or c[2].startswith("reignofnether:")):
                    continue
                if all(min(ra[k][1], rc[k][1]) > max(ra[k][0], rc[k][0]) for k in (0, 1)):
                    out.append((wf, plane, a, c))
    return out


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
    stamp(facade2()).write(os.path.join(ROOT, DEMO2))
    stamp(sampler2()).write(os.path.join(ROOT, SAMPLER2))
    print("%d blocks, %d files" % (len(BLOCKS), len(files())))


if __name__ == "__main__":
    main()
