#!/usr/bin/env python3
"""Study other builders' structures block by block (lovish, Oct 28 2026: "maybe you can download buildings to help
study them"). Reads vanilla structure .nbt, Sponge .schem (v2 and v3) and Litematica .litematic files and measures
how they are built - roof shapes, eave overhangs, wall depth planes, palette shares - and prints cross-sections, so
the lessons of great builds become numbers our generators are tested against.

    python3 tools/schem_study.py FILE [--section x=12] [--section z=5]

Downloaded builds are other people's work: study only. Never commit them, copy them block for block, or reuse their
textures; record the measurements and lessons instead. Tests: tools/test_schem_study.py.
"""
import argparse
import os
import sys
from collections import Counter, deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_sail_hubs import load_nbt  # noqa: E402

AIR = {"air", "cave_air", "void_air", "structure_void"}
GROUND = {"grass_block", "dirt", "coarse_dirt", "podzol", "mycelium", "rooted_dirt", "dirt_path", "farmland",
          "sand", "red_sand", "gravel", "water", "lava", "bedrock", "short_grass", "grass", "tall_grass", "fern",
          "large_fern", "snow", "snow_block", "mud"}
LIGHTS = ("lantern", "torch", "candle", "lamp", "froglight", "glowstone", "sea_lantern", "shroomlight", "end_rod")
PLANTS = ("leaves", "vine", "moss", "flower", "azalea", "grass", "fern", "bush", "sapling", "lily", "dripleaf")
FACES = {"west": (0, +1), "east": (0, -1), "north": (2, +1), "south": (2, -1)}   # axis, direction looked along


def _strip(name):
    return name[len("minecraft:"):] if name.startswith("minecraft:") else name


def parse_state(s):
    """'minecraft:oak_stairs[facing=east,half=bottom]' -> ('oak_stairs', {'facing': 'east', 'half': 'bottom'})."""
    if "[" in s:
        name, rest = s.split("[", 1)
        props = dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if kv)
    else:
        name, props = s, {}
    return _strip(name), props


class Build:
    """blocks: {(x, y, z): (name, props)} - air left out, names without the minecraft: namespace."""

    def __init__(self, blocks):
        self.blocks = blocks

    def solid(self):
        return {q for q, (n, _) in self.blocks.items() if n not in AIR}


def _keep(blocks, q, name, props):
    if name not in AIR:
        blocks[q] = (name, props)


def _varints(data):
    out, v, shift = [], 0, 0
    for b in data:
        b &= 0xFF
        v |= (b & 0x7F) << shift
        if b & 0x80:
            shift += 7
        else:
            out.append(v)
            v, shift = 0, 0
    return out


def _load_schem(root):
    if "Schematic" in root:
        root = root["Schematic"]
    w, h, l = (root[k] & 0xFFFF for k in ("Width", "Height", "Length"))
    if "Blocks" in root:                                # Sponge v3
        pal, data = root["Blocks"]["Palette"], root["Blocks"]["Data"]
    else:                                               # Sponge v2
        pal, data = root["Palette"], root["BlockData"]
    by_id = {i: parse_state(s) for s, i in pal.items()}
    blocks = {}
    for i, v in enumerate(_varints(data)):
        x, z, y = i % w, (i // w) % l, i // (w * l)
        _keep(blocks, (x, y, z), *by_id[v])
    return Build(blocks)


def unpack_litematic(longs, bits, count):
    """Litematica packs palette indices LSB-first across 64-bit longs, a value may span two longs."""
    u = [v & ((1 << 64) - 1) for v in longs]
    mask = (1 << bits) - 1
    out = []
    for i in range(count):
        start = i * bits
        w, off = divmod(start, 64)
        v = u[w] >> off
        if off + bits > 64:
            v |= u[w + 1] << (64 - off)
        out.append(v & mask)
    return out


def _load_litematic(root):
    blocks = {}
    for region in root["Regions"].values():
        sx, sy, sz = (abs(region["Size"][k]) for k in ("x", "y", "z"))
        pos = region.get("Position", {"x": 0, "y": 0, "z": 0})
        ox, oy, oz = (pos[k] + min(region["Size"][k] + 1, 0) for k in ("x", "y", "z"))
        pal = [(_strip(e["Name"]), dict(e.get("Properties", {}))) for e in region["BlockStatePalette"]]
        bits = max(2, (len(pal) - 1).bit_length())
        for i, v in enumerate(unpack_litematic(region["BlockStates"], bits, sx * sy * sz)):
            x, z, y = i % sx, (i // sx) % sz, i // (sx * sz)
            _keep(blocks, (x + ox, y + oy, z + oz), *pal[v])
    if blocks:                                          # shift to a 0-based box
        mx, my, mz = (min(q[k] for q in blocks) for k in range(3))
        blocks = {(x - mx, y - my, z - mz): b for (x, y, z), b in blocks.items()}
    return Build(blocks)


def _load_vanilla(root):
    pal = [(_strip(p["Name"]), dict(p.get("Properties", {}))) for p in root["palette"]]
    blocks = {}
    for b in root["blocks"]:
        _keep(blocks, tuple(b["pos"]), *pal[b["state"]])
    return Build(blocks)


# ---- legacy MCEdit .schematic (pre-1.13 numeric ids) -----------------------------------------------------------
WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak")
COLOURS = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
           "purple", "blue", "brown", "green", "red", "black")
_SIMPLE = {
    2: "grass_block", 3: "dirt", 4: "cobblestone", 7: "bedrock", 8: "water", 9: "water", 10: "lava", 11: "lava",
    12: "sand", 13: "gravel", 14: "gold_ore", 15: "iron_ore", 16: "coal_ore", 19: "sponge", 20: "glass",
    21: "lapis_ore", 22: "lapis_block", 23: "dispenser", 25: "note_block", 26: "red_bed", 27: "powered_rail",
    28: "detector_rail", 30: "cobweb", 31: "grass", 32: "dead_bush", 37: "dandelion", 38: "poppy",
    39: "brown_mushroom", 40: "red_mushroom", 41: "gold_block", 42: "iron_block", 45: "bricks", 46: "tnt",
    47: "bookshelf", 48: "mossy_cobblestone", 49: "obsidian", 50: "torch", 51: "fire", 52: "spawner", 54: "chest",
    55: "redstone_wire", 56: "diamond_ore", 57: "diamond_block", 58: "crafting_table", 59: "wheat", 60: "farmland",
    61: "furnace", 62: "furnace", 63: "oak_sign", 64: "oak_door", 65: "ladder", 66: "rail", 68: "oak_wall_sign",
    69: "lever", 70: "stone_pressure_plate", 71: "iron_door", 72: "oak_pressure_plate", 73: "redstone_ore",
    74: "redstone_ore", 75: "redstone_torch", 76: "redstone_torch", 77: "stone_button", 78: "snow", 79: "ice",
    80: "snow_block", 81: "cactus", 82: "clay", 83: "sugar_cane", 84: "jukebox", 85: "oak_fence", 86: "pumpkin",
    87: "netherrack", 88: "soul_sand", 89: "glowstone", 90: "nether_portal", 91: "jack_o_lantern", 92: "cake",
    96: "oak_trapdoor", 97: "infested_stone", 99: "brown_mushroom_block", 100: "red_mushroom_block",
    101: "iron_bars", 102: "glass_pane", 103: "melon", 106: "vine", 107: "oak_fence_gate", 110: "mycelium",
    111: "lily_pad", 112: "nether_bricks", 113: "nether_brick_fence", 116: "enchanting_table", 117: "brewing_stand",
    118: "cauldron", 120: "end_portal_frame", 121: "end_stone", 122: "dragon_egg", 123: "redstone_lamp",
    124: "redstone_lamp", 127: "cocoa", 129: "emerald_ore", 130: "ender_chest", 131: "tripwire_hook", 132: "tripwire",
    133: "emerald_block", 137: "command_block", 138: "beacon", 140: "flower_pot", 141: "carrots", 142: "potatoes",
    143: "oak_button", 144: "skeleton_skull", 145: "anvil", 146: "trapped_chest", 147: "light_weighted_pressure_plate",
    148: "heavy_weighted_pressure_plate", 151: "daylight_detector", 152: "redstone_block", 153: "nether_quartz_ore",
    154: "hopper", 165: "slime_block", 166: "barrier", 167: "iron_trapdoor", 168: "prismarine", 169: "sea_lantern",
    170: "hay_block", 172: "terracotta", 173: "coal_block", 174: "packed_ice", 176: "white_banner",
    177: "white_wall_banner", 178: "daylight_detector", 179: "red_sandstone", 183: "spruce_fence_gate",
    184: "birch_fence_gate", 185: "jungle_fence_gate", 186: "dark_oak_fence_gate", 187: "acacia_fence_gate",
    188: "spruce_fence", 189: "birch_fence", 190: "jungle_fence", 191: "dark_oak_fence", 192: "acacia_fence",
    193: "spruce_door", 194: "birch_door", 195: "jungle_door", 196: "acacia_door", 197: "dark_oak_door",
    198: "end_rod", 199: "chorus_plant", 200: "chorus_flower", 201: "purpur_block", 202: "purpur_pillar",
    206: "end_stone_bricks", 208: "dirt_path", 213: "magma_block", 214: "nether_wart_block", 215: "red_nether_bricks",
    216: "bone_block", 251: "white_concrete", 252: "white_concrete_powder"}
_STAIRS = {53: "oak", 67: "cobblestone", 108: "brick", 109: "stone_brick", 114: "nether_brick", 128: "sandstone",
           134: "spruce", 135: "birch", 136: "jungle", 156: "quartz", 163: "acacia", 164: "dark_oak",
           180: "red_sandstone", 203: "purpur"}
_STONE = ("stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite")
_SLAB44 = ("smooth_stone", "sandstone", "oak", "cobblestone", "brick", "stone_brick", "nether_brick", "quartz")
_FULL43 = ("smooth_stone", "sandstone", "oak_planks", "cobblestone", "bricks", "stone_bricks", "nether_bricks",
           "quartz_block")


def legacy_block(i, d):
    """(numeric id, metadata) -> (1.13+ name, props); unknown ids keep a 'legacy_<id>' name rather than vanish."""
    if i == 0:
        return ("air", {})
    if i in _STAIRS:
        return (_STAIRS[i] + "_stairs", {"facing": ("east", "west", "south", "north")[d & 3],
                                         "half": "top" if d & 4 else "bottom"})
    half = {"type": "top" if d & 8 else "bottom"}
    if i == 44:
        return (_SLAB44[d & 7] + "_slab", half)
    if i == 126:
        return (WOODS[(d & 7) % 6] + "_slab", half)
    if i == 182:
        return ("red_sandstone_slab", half)
    if i == 205:
        return ("purpur_slab", half)
    if i == 43:
        return (_FULL43[d & 7], {})
    if i == 1:
        return (_STONE[d % 7], {})
    if i == 5:
        return (WOODS[d % 6] + "_planks", {})
    if i == 17:
        return (WOODS[d & 3] + "_log", {})
    if i == 162:
        return (WOODS[4 + (d & 1)] + "_log", {})
    if i == 18:
        return (WOODS[d & 3] + "_leaves", {})
    if i == 161:
        return (WOODS[4 + (d & 1)] + "_leaves", {})
    if i == 24:
        return (("sandstone", "chiseled_sandstone", "cut_sandstone")[d % 3], {})
    if i == 98:
        return (("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks")[d & 3], {})
    if i == 139:
        return (("cobblestone_wall", "mossy_cobblestone_wall")[d & 1], {})
    if i == 155:
        return (("quartz_block", "chiseled_quartz_block", "quartz_pillar")[min(d, 2)], {})
    if i == 35:
        return (COLOURS[d & 15] + "_wool", {})
    if i == 159:
        return (COLOURS[d & 15] + "_terracotta", {})
    if i == 95:
        return (COLOURS[d & 15] + "_stained_glass", {})
    if i == 160:
        return (COLOURS[d & 15] + "_stained_glass_pane", {})
    if i == 171:
        return (COLOURS[d & 15] + "_carpet", {})
    if i == 6:
        return (WOODS[d % 6] + "_sapling", {})
    if i == 175:
        return ("tall_grass", {})
    return (_SIMPLE.get(i, "legacy_%d" % i), {})


def _load_legacy(root):
    w, h, l = (root[k] & 0xFFFF for k in ("Width", "Height", "Length"))
    ids, data = root["Blocks"], root["Data"]
    add = root.get("AddBlocks")
    blocks = {}
    for k in range(w * h * l):
        i = ids[k] & 0xFF
        if add:
            nib = add[k >> 1] & 0xFF
            i |= ((nib & 0x0F) if k & 1 else (nib >> 4)) << 8
        x, z, y = k % w, (k // w) % l, k // (w * l)
        _keep(blocks, (x, y, z), *legacy_block(i, data[k] & 0x0F))
    return Build(blocks)


def load(path):
    root = load_nbt(path)
    if "Regions" in root:
        return _load_litematic(root)
    if "palette" in root and "blocks" in root:
        return _load_vanilla(root)
    if "Blocks" in root and "Data" in root and "Palette" not in root:
        return _load_legacy(root)
    return _load_schem(root)


# ---- measures ---------------------------------------------------------------------------------------------------

def is_roof_shape(name):
    return name.endswith("_stairs") or name.endswith("_slab") or "roof" in name


def roof_cells(b):
    """Sky-exposed roof shapes: stairs, slabs and roof-mod blocks with nothing above them in their column."""
    top = {}
    for (x, y, z), (n, _) in b.blocks.items():
        if n not in AIR and y > top.get((x, z), -10 ** 9):
            top[(x, z)] = y
    return {(x, y, z) for (x, y, z), (n, _) in b.blocks.items()
            if is_roof_shape(n) and top[(x, z)] == y}


def _footprint(cols):
    """The 2-D columns enclosed by `cols` (a ring of walls counts its hollow middle too)."""
    if not cols:
        return set()
    x0, x1 = min(c[0] for c in cols) - 1, max(c[0] for c in cols) + 1
    z0, z1 = min(c[1] for c in cols) - 1, max(c[1] for c in cols) + 1
    outside, todo = {(x0, z0)}, deque([(x0, z0)])
    while todo:
        x, z = todo.popleft()
        for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if x0 <= n[0] <= x1 and z0 <= n[1] <= z1 and n not in cols and n not in outside:
                outside.add(n)
                todo.append(n)
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)} - outside


def overhang_cells(b):
    """{(x, y, z): distance} for roof cells outside the walls' footprint below them (Chebyshev blocks to the
    footprint). Ground blocks and roof shapes (stairs, slabs, gutters) don't count as walls, so every course of an
    oversailing verge is measured, not only the lowest."""
    roofs = roof_cells(b)
    walls = sorted((q for q, (n, _) in b.blocks.items() if n not in AIR and n not in GROUND and not is_roof_shape(n)),
                   key=lambda q: q[1])
    out = {}
    for y in sorted({q[1] for q in roofs}):
        cols = {(q[0], q[2]) for q in walls if q[1] < y}
        fp = _footprint(cols)
        if not fp:
            continue
        for (x, yy, z) in roofs:
            if yy != y or (x, z) in fp:
                continue
            out[(x, y, z)] = min(max(abs(x - fx), abs(z - fz)) for fx, fz in fp)
    return out


def eave_overhang(b):
    """{distance: cells} over overhang_cells - how far real eaves throw their shadow."""
    return dict(Counter(overhang_cells(b).values()))


def depth_layers(b, face):
    """{blocks back from the face's outermost plane: cells} seen from `face` (west = looking east from -x...)."""
    axis, sign = FACES[face]
    other = [a for a in (0, 2) if a != axis][0]
    best = {}
    for q in b.solid():
        k = (q[other], q[1])
        v = q[axis] * sign
        if k not in best or v < best[k]:
            best[k] = v
    if not best:
        return {}
    edge = min(best.values())
    return dict(Counter(v - edge for v in best.values()))


def palette_shares(b, top=None):
    c = Counter(n for n, _ in b.blocks.values() if n not in AIR)
    total = sum(c.values())
    rows = sorted(((n, k / total) for n, k in c.items()), key=lambda r: (-r[1], r[0]))
    return rows[:top] if top else rows


def glyph(name):
    if name in AIR:
        return "."
    if "roof" in name:
        return "r"
    if name.endswith("_stairs"):
        return "s"
    if name.endswith("_slab"):
        return "_"
    if "glass" in name or "window" in name:
        return "g"
    if any(k in name for k in LIGHTS):
        return "*"
    if name.endswith("_wall") or "fence" in name or "bars" in name or name == "chain":
        return "|"
    if "trapdoor" in name:
        return "="
    if any(k in name for k in PLANTS):
        return "v"
    return "#"


def section(b, axis, k):
    """ASCII cut at x = k (axis 'x': columns run along z) or z = k (columns along x); top row first."""
    a = {"x": 0, "z": 2}[axis]
    run = 2 if a == 0 else 0
    xs = [q[run] for q in b.blocks]
    ys = [q[1] for q in b.blocks]
    rows = []
    for y in range(max(ys), min(ys) - 1, -1):
        row = ""
        for u in range(min(xs), max(xs) + 1):
            q = [0, y, 0]
            q[a], q[run] = k, u
            row += glyph(b.blocks.get(tuple(q), ("air", {}))[0])
        rows.append(row)
    return rows


def report(b, sections=()):
    sol = b.solid()
    size = [max(q[i] for q in sol) - min(q[i] for q in sol) + 1 for i in range(3)]
    out = ["size %d x %d x %d, %d blocks" % (size[0], size[1], size[2], len(sol)), "", "palette (top 20):"]
    out += ["  %5.1f%%  %s" % (100 * s, n) for n, s in palette_shares(b, 20)]
    roofs = roof_cells(b)
    rc = Counter(b.blocks[q][0] for q in roofs)
    out += ["", "roof cells: %d (%.0f%% of the top surface)" % (len(roofs), 100 * len(roofs) / max(1, len(
        {(q[0], q[2]) for q in sol})))]
    out += ["  %5.1f%%  %s" % (100 * v / max(1, len(roofs)), n) for n, v in rc.most_common(8)]
    out += ["", "eave overhang (blocks: roof cells): %s" % dict(sorted(eave_overhang(b).items()))]
    for f in FACES:
        layers = depth_layers(b, f)
        tot = sum(layers.values())
        out.append("depth layers %-5s: %s" % (f, ", ".join("%d:%.0f%%" % (d, 100 * n / tot)
                                                            for d, n in sorted(layers.items())[:8])))
    for spec in sections:
        axis, k = spec.split("=")
        out += ["", "section %s:" % spec] + ["  " + r for r in section(b, axis, int(k))]
    out += ["", "legend: # full  s stairs  _ slab  r roof-mod  g glass/window  * light  | wall/fence/bars/chain",
            "        = trapdoor  v plant  . air"]
    return "\n".join(out)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("file")
    ap.add_argument("--section", action="append", default=[], help="x=N or z=N")
    a = ap.parse_args(argv)
    print(report(load(a.file), a.section))


if __name__ == "__main__":
    main()
