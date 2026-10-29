#!/usr/bin/env python3
"""Isometric preview renderer for Minecraft structure NBT files (no dependencies beyond Pillow).

Usage: python3 tools/preview_structure.py <file.nbt> [more.nbt ...] -o out.png [--cell 10] [--rts]

--rts mirrors x so the sheet shows the faces the in-game RTS camera sees (it looks north-east from the south-west,
so the -x and +z faces and the roofs; buildings' fronts face -z, away from it).

Renders each structure as shaded isometric cubes coloured by an approximate block palette, side by side,
so building changes can be eyeballed before a ~10 minute CI round trip. Unknown blocks fall back to a
hash-derived colour. Thin blocks (slabs, stairs, fences, walls, lanterns, banners, trapdoors, carpets ...) are drawn as
approximate boxes (shape_for) so detail work reads; it is still a silhouette/palette check, not a faithful render.
"""
import gzip
import struct
import sys
import hashlib
from PIL import Image, ImageDraw


# ---- minimal NBT reader ------------------------------------------------------
def _read(buf, pos, t):
    if t == 1:  return struct.unpack_from(">b", buf, pos)[0], pos + 1
    if t == 2:  return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if t == 3:  return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if t == 4:  return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if t == 5:  return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if t == 6:  return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if t == 7:
        n = struct.unpack_from(">i", buf, pos)[0]; pos += 4
        return buf[pos:pos + n], pos + n
    if t == 8:
        n = struct.unpack_from(">H", buf, pos)[0]; pos += 2
        return buf[pos:pos + n].decode("utf-8", "replace"), pos + n
    if t == 9:
        et = buf[pos]; n = struct.unpack_from(">i", buf, pos + 1)[0]; pos += 5
        out = []
        for _ in range(n):
            v, pos = _read(buf, pos, et); out.append(v)
        return out, pos
    if t == 10:
        out = {}
        while True:
            tt = buf[pos]; pos += 1
            if tt == 0:
                return out, pos
            nl = struct.unpack_from(">H", buf, pos)[0]; pos += 2
            name = buf[pos:pos + nl].decode("utf-8", "replace"); pos += nl
            out[name], pos = _read(buf, pos, tt)
    if t == 11:
        n = struct.unpack_from(">i", buf, pos)[0]; pos += 4
        return list(struct.unpack_from(">%di" % n, buf, pos)), pos + 4 * n
    if t == 12:
        n = struct.unpack_from(">i", buf, pos)[0]; pos += 4
        return list(struct.unpack_from(">%dq" % n, buf, pos)), pos + 8 * n
    raise ValueError("bad tag %d" % t)


def load_nbt(path):
    raw = open(path, "rb").read()
    try:
        raw = gzip.decompress(raw)
    except OSError:
        pass
    assert raw[0] == 10
    nl = struct.unpack_from(">H", raw, 1)[0]
    root, _ = _read(raw, 3 + nl, 10)
    return root


# ---- colours -----------------------------------------------------------------
COLOURS = {
    "vine": (52, 92, 38), "hanging_roots": (120, 88, 62),   # ivy and roots on rock (were hash colours: cyan)
    "stone": (125, 125, 125), "cobblestone": (110, 110, 110), "andesite": (135, 135, 135),
    "deepslate": (70, 70, 75), "blackstone": (42, 36, 42), "gilded": (190, 150, 50),
    "sandstone": (216, 202, 155), "mud": (140, 105, 80), "terracotta": (160, 90, 65),
    "white_terracotta": (210, 180, 160), "oak": (160, 130, 80), "spruce": (110, 80, 50),
    "dark_oak": (66, 43, 20), "birch": (200, 185, 130), "crimson": (125, 55, 80), "warped": (45, 110, 105),
    "jungle": (155, 110, 75), "acacia": (170, 90, 50), "mangrove": (115, 55, 50), "bamboo": (190, 175, 90),
    "cherry": (225, 175, 170), "log": (100, 80, 50), "planks": (160, 130, 80), "brick": (150, 85, 70),
    "iron": (215, 215, 215), "gold": (245, 205, 60), "copper": (190, 110, 80), "raw_iron": (180, 150, 120),
    "glass": (190, 220, 230), "wool": (230, 230, 230), "hay": (210, 180, 60), "grass": (95, 150, 60),
    "dirt": (130, 95, 65), "farmland": (100, 70, 45), "gravel": (135, 128, 125), "leaves": (60, 110, 40),
    "lantern": (250, 200, 100), "torch": (250, 200, 100), "campfire": (230, 120, 40), "fire": (240, 120, 30),
    "soul": (90, 200, 210), "water": (60, 90, 200), "lava": (230, 100, 20), "quartz": (235, 230, 225),
    "purpur": (170, 125, 170), "prismarine": (90, 160, 150), "obsidian": (25, 20, 35), "netherrack": (110, 50, 50),
    "nether_brick": (55, 28, 33), "red_nether": (90, 20, 20), "basalt": (80, 80, 85), "bone": (225, 220, 200),
    "moss": (90, 120, 50), "polished": (120, 120, 125), "smooth_stone": (160, 160, 160), "chain": (60, 65, 75),
    "bars": (100, 100, 100), "white": (235, 235, 235), "black": (25, 25, 30), "red": (160, 40, 35),
    "blue": (50, 70, 160), "yellow": (240, 200, 50), "purple": (120, 50, 160), "green": (85, 110, 30),
    "gray": (70, 75, 80), "light_gray": (150, 150, 145), "brown": (110, 75, 45), "orange": (230, 120, 30),
    "cyan": (30, 130, 145), "lime": (110, 185, 30), "pink": (225, 130, 160), "magenta": (180, 70, 170),
    "light_blue": (60, 170, 215),
    # modded decor (Macaw's etc.)
    "thatch_steep_roof": (196, 168, 86), "thatch": (196, 168, 86), "_window": (165, 200, 215), "_gothic": (120, 150, 170),
    "steep_roof": (100, 100, 100), "concrete": (220, 220, 220),
    # Verdant Court
    "azalea_leaves": (95, 130, 45), "flowering_azalea_leaves": (140, 120, 95), "flowering_azalea": (190, 110, 170), "froglight": (200, 235, 160),
    "calcite": (225, 225, 220), "rooted_dirt": (145, 105, 75), "mangrove_roots": (90, 60, 40),
    "oak_wood": (105, 85, 50), "oak_log": (105, 85, 50), "iron_ore": (150, 140, 130), "fern": (70, 120, 45),
    "target": (225, 200, 170), "oxidized": (85, 165, 135), "smoker": (95, 90, 85),
    # economy detail pass (lights, banners, skulls...)
    "soul_lantern": (90, 210, 220), "soul_campfire": (90, 210, 220), "end_rod": (250, 245, 225), "sea_lantern": (200, 235, 230), "lapis": (35, 65, 160), "lightning_rod": (200, 120, 85),
    "exposed": (165, 120, 95), "weathered": (105, 150, 110),   # weathered copper is green-grey, not copper "shroomlight": (245, 150, 70), "magma": (190, 80, 30), "skull": (215, 210, 195),
    "wither_skeleton_skull": (35, 35, 35), "soul_soil": (80, 62, 50), "crying_obsidian": (90, 30, 150),
    "blast_furnace": (100, 100, 105), "barrel": (135, 100, 60), "smithing": (60, 50, 50), "diorite": (200, 200, 200),
    "raw_gold": (210, 160, 60), "chiseled_quartz": (225, 220, 210),
    "ochre_froglight": (240, 205, 130), "pearlescent_froglight": (235, 205, 225), "glowstone": (250, 215, 130),
}
SKIP = {"air", "cave_air", "void_air", "structure_void", "barrier", "light"}


def invisible(name):
    """Blocks a structure file carries that nobody sees: air, structure voids, barriers and the light block. Matched on
    the whole id: the suffix test this replaced ("ends in light") dropped every froglight and shroomlight too."""
    ns, _, path = name.rpartition(":")
    return ns in ("", "minecraft") and path in SKIP


def colour_for(name):
    n = name.split(":")[-1]
    best, best_len = None, 0
    for key, col in COLOURS.items():
        if key in n and len(key) > best_len:
            best, best_len = col, len(key)
    if best:
        return best
    h = hashlib.md5(n.encode()).digest()
    return (80 + h[0] % 140, 80 + h[1] % 140, 80 + h[2] % 140)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


# ---- shapes ------------------------------------------------------------------
# Each block is drawn as one or more boxes in its cell (x0, x1, y0, y1, z0, z1 in 0..1), so slabs, stairs, fences,
# lanterns, banners and trapdoors read as what they are - detail passes (design/building_detail.md) are mostly made of
# those. Shapes are approximations; anything unknown is a full cube.
FULL = [(0, 1, 0, 1, 0, 1)]
DIR = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
PLANTS = ("poppy", "dandelion", "allium", "azure_bluet", "cornflower", "oxeye_daisy", "lily_of_the_valley",
          "wither_rose", "blue_orchid", "grass", "fern", "dead_bush", "tall_grass", "large_fern")


def _side_box(facing, t, y0=0.0, y1=1.0):
    """A plate of thickness t against the cell side `facing` points to."""
    dx, dz = DIR.get(facing, (0, 1))
    if dx == 1:
        return (1 - t, 1, y0, y1, 0, 1)
    if dx == -1:
        return (0, t, y0, y1, 0, 1)
    if dz == 1:
        return (0, 1, y0, y1, 1 - t, 1)
    return (0, 1, y0, y1, 0, t)


def shape_for(name, props):
    n = name.split(":")[-1]
    half = props.get("half", "bottom")
    facing = props.get("facing", "south")
    if n.endswith("_slab"):
        t = props.get("type", "bottom")
        return FULL if t == "double" else [(0, 1, 0.5, 1, 0, 1)] if t == "top" else [(0, 1, 0, 0.5, 0, 1)]
    if n.endswith("_stairs"):
        lo, hi = (0.5, 1) if half == "top" else (0, 0.5)
        up = (0, 0.5) if half == "top" else (0.5, 1)
        return [(0, 1, lo, hi, 0, 1), _side_box(facing, 0.5, *up)]
    if n.endswith("_carpet") or n in ("moss_carpet", "snow"):
        return [(0, 1, 0, 0.07, 0, 1)]
    if n.endswith("_trapdoor"):
        if props.get("open") == "true":
            return [_side_box(OPP.get(facing, "north"), 0.19)]
        return [(0, 1, 0.81, 1, 0, 1)] if half == "top" else [(0, 1, 0, 0.19, 0, 1)]
    if n.endswith("_door"):
        return [_side_box(OPP.get(facing, "north"), 0.19)]
    if n.endswith("_wall_banner"):
        return [_side_box(OPP.get(facing, "north"), 0.1, -0.6, 0.9)]
    if n.endswith("_banner"):
        return [(0.45, 0.55, 0, 1.0, 0.45, 0.55), (0.1, 0.9, 0.2, 1.8, 0.45, 0.55)]
    if n.endswith("_wall_torch"):
        dx, dz = DIR[OPP.get(facing, "north")]
        cx, cz = 0.5 + dx * 0.3, 0.5 + dz * 0.3
        return [(cx - 0.08, cx + 0.08, 0.2, 0.8, cz - 0.08, cz + 0.08)]
    if n in ("torch", "soul_torch", "redstone_torch"):
        return [(0.42, 0.58, 0, 0.65, 0.42, 0.58)]
    if n in ("lantern", "soul_lantern"):
        if props.get("hanging") == "true":
            return [(0.3, 0.7, 0.1, 0.6, 0.3, 0.7), (0.45, 0.55, 0.6, 1, 0.45, 0.55)]
        return [(0.3, 0.7, 0, 0.55, 0.3, 0.7)]
    if n.endswith("_fence") or n in ("chain", "end_rod", "lightning_rod", "iron_bars") or n.endswith("_pane"):
        w = 0.06 if n == "chain" else 0.12
        return [(0.5 - w, 0.5 + w, 0, 1, 0.5 - w, 0.5 + w)]
    if n.endswith("_wall"):
        return [(0.25, 0.75, 0, 1, 0.25, 0.75)]
    if n.endswith("_fence_gate"):
        return [(0, 1, 0.3, 0.9, 0.4, 0.6)]
    if n.endswith("_skull") or n.endswith("_head"):
        return [(0.25, 0.75, 0, 0.5, 0.25, 0.75)]
    if n in ("campfire", "soul_campfire"):
        return [(0, 1, 0, 0.44, 0, 1)]
    if n == "candle" or n.endswith("_candle"):
        return [(0.38, 0.62, 0, 0.4, 0.38, 0.62)]
    if n == "bell":
        return [(0.25, 0.75, 0.2, 0.8, 0.25, 0.75)]
    if n.endswith("_pressure_plate"):
        return [(0.06, 0.94, 0, 0.06, 0.06, 0.94)]
    if n.endswith("_button"):
        return [(0.35, 0.65, 0.35, 0.65, 0.35, 0.65)]
    if n in PLANTS or n.endswith(("_coral", "_coral_fan", "_sapling", "_tulip")) or "flower" in n and "leaves" not in n:
        return [(0.3, 0.7, 0, 0.7, 0.3, 0.7)]
    if n in ("anvil", "grindstone", "brewing_stand", "cauldron", "composter"):
        return [(0.1, 0.9, 0, 0.9, 0.1, 0.9)]
    return FULL


# ---- render ------------------------------------------------------------------
def render(path, cell=10, rts=False):
    root = load_nbt(path)
    palette = root.get("palette") or (root.get("palettes") or [[]])[0]
    names = [p.get("Name", "minecraft:air") for p in palette]
    props_of = [p.get("Properties", {}) for p in palette]
    sx, sy, sz = root["size"]
    voxels = []
    for b in root.get("blocks", []):
        name = names[b["state"]]
        if invisible(name):
            continue
        x, y, z = b["pos"]
        shape = shape_for(name, props_of[b["state"]])
        if rts:
            x = sx - 1 - x
            if shape is not FULL:
                shape = [(1 - x1, 1 - x0, y0, y1, z0, z1) for (x0, x1, y0, y1, z0, z1) in shape]
        voxels.append((x, y, z, colour_for(name), shape))
    w = int((sx + sz) * cell * 0.87) + cell * 4
    h = int((sx + sz) * cell * 0.5 + sy * cell) + cell * 7
    img = Image.new("RGB", (w, h), (34, 38, 44))
    d = ImageDraw.Draw(img)
    ox, oy = w // 2, int(sy * cell) + cell * 4

    def P(X, Y, Z):
        return (ox + (X - Z) * cell * 0.87, oy + (X + Z - 1) * cell * 0.5 - (Y - 1) * cell)

    full = {(v[0], v[1], v[2]) for v in voxels if v[4] is FULL}
    boxes = []
    for x, y, z, c, shape in voxels:
        for (x0, x1, y0, y1, z0, z1) in shape:
            boxes.append((x + x0, x + x1, y + y0, y + y1, z + z0, z + z1, c, (x, y, z), shape is FULL))
    # painter's order: far to near (the view looks from +x +z), low to high
    boxes.sort(key=lambda bx: (bx[0] + bx[1] + bx[4] + bx[5], bx[2] + bx[3]))
    for (X0, X1, Y0, Y1, Z0, Z1, c, (x, y, z), isfull) in boxes:
        if not (isfull and (x, y + 1, z) in full):
            d.polygon([P(X0, Y1, Z0), P(X1, Y1, Z0), P(X1, Y1, Z1), P(X0, Y1, Z1)], fill=shade(c, 1.1))
        if not (isfull and (x, y, z + 1) in full):
            d.polygon([P(X0, Y1, Z1), P(X1, Y1, Z1), P(X1, Y0, Z1), P(X0, Y0, Z1)], fill=shade(c, 0.78))
        if not (isfull and (x + 1, y, z) in full):
            d.polygon([P(X1, Y1, Z0), P(X1, Y1, Z1), P(X1, Y0, Z1), P(X1, Y0, Z0)], fill=shade(c, 0.62))
    label = path.split("/")[-1].replace(".nbt", "") + f"  {sx}x{sy}x{sz}  {len(voxels)} blocks"
    d.text((6, 4), label, fill=(230, 230, 230))
    return img


def main(argv):
    out = "preview.png"
    files = []
    cell = 10
    rts = False
    i = 0
    while i < len(argv):
        if argv[i] == "-o":
            out = argv[i + 1]; i += 2
        elif argv[i] == "--rts":
            rts = True; i += 1
        elif argv[i] == "--cell":
            cell = int(argv[i + 1]); i += 2
        else:
            files.append(argv[i]); i += 1
    imgs = [render(f, cell, rts) for f in files]
    W = sum(im.width for im in imgs) + 8 * (len(imgs) + 1)
    H = max(im.height for im in imgs) + 16
    sheet = Image.new("RGB", (W, H), (20, 22, 26))
    x = 8
    for im in imgs:
        sheet.paste(im, (x, 8)); x += im.width + 8
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main(sys.argv[1:])
