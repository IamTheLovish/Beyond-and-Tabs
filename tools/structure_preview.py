#!/usr/bin/env python3
"""A faction building as the RTS camera sees it, drawn with OUR block pack - judge a palette before a CI run.

Reads a building structure (.nbt, bar/src/main/resources/assets/reignofnether/structures) and paints it in a
2:1 isometric view from above (roughly the RTS camera's default tilt), each block as three textured faces (top,
south, east) in painter's order. Textures come from resourcepacks/BeyondAndTabs-Realistic (generate them first:
`python3 tools/gen_blocks.py`); a block our pack does not cover is drawn in flat grey with a dark cross, so the
gaps show at a glance. Stairs, slabs, walls and fences are drawn as full blocks of their base texture - this judges
colour and material, not shape. Textures are GUESSED from block names (TOPS, ALIAS); tools/face_census.py reads the
game's own models and is the accurate coverage count (the guesses hid quartz and stripped-log tops until Oct 15).

    python3 tools/structure_preview.py OUT_DIR [--faction villagers] [--names town_centre,barracks] [--px 24]
Writes OUT_DIR/<structure>.png, OUT_DIR/<faction>_sheet.png and prints each building's share of covered blocks.
Needs nbtlib (pip install nbtlib). The same textures on the real shapes, from the RTS camera's side, a few buildings
to one sheet with their lights bright: python3 tools/structure_render.py NAME|FILE.nbt ... -o sheet.png [--px 24].
"""
import argparse
import collections
import math
import os
import re

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRUCT_DIR = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "structures")
BUILD_DIR = os.path.join(ROOT, "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "building",
                         "buildings")
PACK = os.path.join(ROOT, "resourcepacks", "BeyondAndTabs-Realistic", "assets", "minecraft", "textures", "block")
SKIP = {"air", "cave_air", "void_air", "structure_void", "barrier", "light"}
TINT = {"grass_block_top": (0.57, 0.74, 0.35), "oak_leaves": (0.47, 0.67, 0.18), "birch_leaves": (0.50, 0.65, 0.33),
        "spruce_leaves": (0.38, 0.55, 0.38), "jungle_leaves": (0.47, 0.67, 0.18), "acacia_leaves": (0.47, 0.67, 0.18),
        "dark_oak_leaves": (0.47, 0.67, 0.18), "water_still": (0.25, 0.46, 0.89)}
SUFFIXES = ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_trapdoor", "_door")
ALIAS = {"stone_brick": "stone_bricks", "polished_blackstone_brick": "polished_blackstone_bricks",
         "deepslate_brick": "deepslate_bricks", "deepslate_tile": "deepslate_tiles", "nether_brick": "nether_bricks",
         "red_nether_brick": "red_nether_bricks", "mossy_stone_brick": "mossy_stone_bricks",
         "prismarine_brick": "prismarine_bricks", "smooth_quartz": "quartz_block_bottom",
         "crimson_hyphae": "crimson_stem", "warped_hyphae": "warped_stem", "smooth_sandstone": "sandstone_top", "smooth_stone": "smooth_stone",
         "water": "water_still", "grass_block": "grass_block_top", "hay_block": "hay_block_side", "barrel": "barrel_side",
         "bone_block": "bone_block_side", "polished_basalt": "polished_basalt_side", "quartz_pillar": "quartz_pillar",
         "dirt_path": "dirt_path_top"}
# blocks whose top face differs from the side: name -> (top texture, side texture)
TOPS = {"grass_block": ("grass_block_top", "grass_block_side"), "barrel": ("barrel_top", "barrel_side"),
        "hay_block": ("hay_block_top", "hay_block_side"), "bone_block": ("bone_block_top", "bone_block_side"),
        "polished_basalt": ("polished_basalt_top", "polished_basalt_side"),
        "quartz_pillar": ("quartz_pillar_top", "quartz_pillar"), "sandstone": ("sandstone_top", "sandstone"),
        "deepslate": ("deepslate_top", "deepslate"), "dirt_path": ("dirt_path_top", "dirt_path_side"),
        "podzol": ("podzol_top", "podzol_side"), "basalt": ("basalt_top", "basalt_side"),
        "reinforced_deepslate": ("reinforced_deepslate_top", "reinforced_deepslate_side"),
        "oak_log": ("oak_log_top", "oak_log"), "spruce_log": ("spruce_log_top", "spruce_log"),
        "birch_log": ("birch_log_top", "birch_log"), "acacia_log": ("acacia_log_top", "acacia_log"),
        "jungle_log": ("jungle_log_top", "jungle_log"), "dark_oak_log": ("dark_oak_log_top", "dark_oak_log"),
        # the quartz block and its stairs / slabs draw quartz_block_top / _side (not quartz_block_bottom - that is
        # smooth quartz), blackstone has its own top, a stripped log its own end (resolve() adds those)
        "quartz_block": ("quartz_block_top", "quartz_block_side"), "quartz": ("quartz_block_top", "quartz_block_side"),
        "blackstone": ("blackstone_top", "blackstone"), "farmland": ("farmland_moist", "dirt"),
        "crimson_stem": ("crimson_stem_top", "crimson_stem"), "warped_stem": ("warped_stem_top", "warped_stem")}


def faction_of_structures():
    out = {}
    for fac in sorted(os.listdir(BUILD_DIR)):
        d = os.path.join(BUILD_DIR, fac)
        if not os.path.isdir(d):
            continue
        for dp, _, fs in os.walk(d):
            for f in fs:
                if f.endswith(".java"):
                    for m in re.finditer(r'structureName\s*=\s*"([a-z0-9_]+)"', open(os.path.join(dp, f)).read()):
                        out.setdefault(m.group(1), fac)
    return out


class Textures:
    def __init__(self, px):
        self.px = px
        self.cache = {}

    def have(self, name):
        return os.path.exists(os.path.join(PACK, name + ".png"))

    def resolve(self, block, axis="y"):
        """(top texture or None, side texture or None) of our pack for a block id without 'minecraft:'. axis: a log's
        or pillar's axis property - lying (x / z), its side texture is what faces up."""
        b = block[6:] if block.startswith("waxed_") else block
        whole = b
        for suf in SUFFIXES:   # stairs and slabs take their block's top and side (sandstone_stairs: sandstone_top)
            if b.endswith(suf):
                b = b[:-len(suf)]
                break
        # walls and fences draw their side texture everywhere (blackstone_wall has no blackstone_top on it)
        if not whole.endswith(("_wall", "_fence", "_fence_gate")) and (
                b in TOPS or (b.startswith("stripped_") and b.endswith("_log"))):
            t, s = TOPS.get(b, (b + "_top", b))
            if axis in ("x", "z"):
                t = s
            return (t if self.have(t) else None), (s if self.have(s) else None)
        for cand in (ALIAS.get(b), b, b + "_planks", b + "s", b[:-5] + "_log" if b.endswith("_wood") else None):
            if cand and self.have(cand):
                return cand, cand
        return None, None

    def get(self, name):
        if name not in self.cache:
            im = Image.open(os.path.join(PACK, name + ".png")).convert("RGBA")
            if im.height > im.width:                          # an animated strip: the first frame
                im = im.crop((0, 0, im.width, im.width))
            im = im.resize((self.px, self.px), Image.BOX)
            a = np.asarray(im, dtype=np.float64) / 255.0
            if name in TINT:
                a[..., :3] *= np.asarray(TINT[name])
            self.cache[name] = a
        return self.cache[name]


def missing_tile(px):
    a = np.zeros((px, px, 4))
    a[..., :3] = 0.55
    a[..., 3] = 1
    for i in range(px):
        a[i, i, :3] = a[i, px - 1 - i, :3] = 0.15
    return a


def face_image(tex, shade):
    t = tex.copy()
    t[..., :3] = np.clip(t[..., :3] * shade, 0, 1)
    return Image.fromarray((t * 255).astype(np.uint8), "RGBA")


def render(path, tx, px):
    import nbtlib
    nbt = nbtlib.load(path)
    names = [str(e["Name"]).replace("minecraft:", "") for e in nbt["palette"]]
    axes = [str(e.get("Properties", {}).get("axis", "y")) for e in nbt["palette"]]
    blocks = []
    for b in nbt["blocks"]:
        n = names[int(b["state"])]
        if n in SKIP:
            continue
        x, y, z = (int(v) for v in b["pos"])
        blocks.append((x, y, z, (n, axes[int(b["state"])])))
    if not blocks:
        return None, 0, 0
    w = px                                                   # a block's width on screen
    hw, qh, hh = w / 2, w / 4, w * 0.6                       # half width, quarter height (top rhombus), side height
    xs = [x for x, _, _, _ in blocks]
    zs = [z for _, _, z, _ in blocks]
    ys = [y for _, y, _, _ in blocks]
    ox = (max(zs) + 1) * hw + 4
    oy = (max(ys) + 1) * hh + 4
    W = int((max(xs) + max(zs) + 2) * hw + 8)
    H = int(oy + (max(xs) + max(zs) + 2) * qh + 8)
    canvas = Image.new("RGBA", (W, H), (24, 24, 28, 255))
    occupied = {(x, y, z) for x, y, z, _ in blocks}
    covered = 0
    miss = missing_tile(px)
    for x, y, z, n in sorted(blocks, key=lambda b: (b[0] + b[2], b[1], b[0])):
        top, side = tx.resolve(*n)
        covered += top is not None or side is not None
        t_top = tx.get(top) if top else miss
        t_side = tx.get(side) if side else miss
        sx = ox + (x - z) * hw
        sy = oy + (x + z) * qh - y * hh
        # top face: rhombus (sx, sy) -> (sx + hw, sy + qh) -> (sx, sy + 2 qh) -> (sx - hw, sy + qh)
        if (x, y + 1, z) not in occupied:
            quad(canvas, face_image(t_top, 1.0), [(sx - hw, sy + qh), (sx, sy), (sx + hw, sy + qh), (sx, sy + 2 * qh)])
        if (x, y, z + 1) not in occupied:   # south face (towards the viewer's left)
            quad(canvas, face_image(t_side, 0.78), [(sx - hw, sy + qh), (sx, sy + 2 * qh), (sx, sy + 2 * qh + hh),
                                                    (sx - hw, sy + qh + hh)])
        if (x + 1, y, z) not in occupied:   # east face (right)
            quad(canvas, face_image(t_side, 0.62), [(sx, sy + 2 * qh), (sx + hw, sy + qh), (sx + hw, sy + qh + hh),
                                                    (sx, sy + 2 * qh + hh)])
    return canvas, covered, len(blocks)


def quad(canvas, img, pts):
    """Pastes img mapped onto the parallelogram pts = [p0 (img top-left), p1 (top-right)?, ...] - corners in order
    top-left, top-right, bottom-right, bottom-left of the texture."""
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = pts
    minx, miny = int(math.floor(min(x0, x1, x2, x3))), int(math.floor(min(y0, y1, y2, y3)))
    maxx, maxy = int(math.ceil(max(x0, x1, x2, x3))), int(math.ceil(max(y0, y1, y2, y3)))
    w, h = img.size
    # affine map from output pixel (u, v) to texture: solve with three corners (top-left, top-right, bottom-left)
    a = np.array([[x1 - x0, x3 - x0], [y1 - y0, y3 - y0]], dtype=np.float64)
    inv = np.linalg.inv(a)
    # texture coords (s, t) in 0..1: [s, t] = inv @ ([X, Y] - p0); PIL wants input = M * output (+ offset)
    m = inv * np.array([[w], [h]])
    ox, oy = x0 - minx, y0 - miny
    # sampled from a copy with a one-texel border repeating the edge (hence the + 1): pixels on a face's outline map
    # just past the texture, and transparent there they left a dotted seam between neighbouring faces
    img = Image.fromarray(np.pad(np.asarray(img), ((1, 1), (1, 1), (0, 0)), mode="edge"))
    coeffs = (m[0, 0], m[0, 1], 1 - (m[0, 0] * ox + m[0, 1] * oy), m[1, 0], m[1, 1], 1 - (m[1, 0] * ox + m[1, 1] * oy))
    size = (maxx - minx + 1, maxy - miny + 1)
    warped = img.transform(size, Image.AFFINE, coeffs, resample=Image.NEAREST)
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).polygon([(x - minx, y - miny) for x, y in pts], fill=255)
    alpha = Image.fromarray((np.asarray(warped)[..., 3].astype(np.uint16) * np.asarray(mask) // 255).astype(np.uint8))
    canvas.paste(warped, (minx, miny), alpha)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--faction")
    ap.add_argument("--names")
    ap.add_argument("--px", type=int, default=24, help="screen pixels per block (24-36 = the RTS default zoom)")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    fac = faction_of_structures()
    names = a.names.split(",") if a.names else sorted(n for n, f in fac.items() if not a.faction or f == a.faction)
    tx = Textures(a.px)
    by_fac = collections.defaultdict(list)
    for n in names:
        p = os.path.join(STRUCT_DIR, n + ".nbt")
        if not os.path.exists(p):
            print("no structure", n)
            continue
        img, cov, tot = render(p, tx, a.px)
        if img is None:
            continue
        img.save(os.path.join(a.out, n + ".png"))
        by_fac[fac.get(n, "?")].append((n, img))
        print("%-28s %-10s %4d blocks, %3.0f%% in our pack" % (n, fac.get(n, "?"), tot, 100.0 * cov / tot))
    for f, items in by_fac.items():
        cols = 4
        cw = max(i.width for _, i in items)
        ch = max(i.height for _, i in items)
        rows = math.ceil(len(items) / cols)
        sheet = Image.new("RGB", (cols * cw, rows * ch), (24, 24, 28))
        d = ImageDraw.Draw(sheet)
        for k, (n, img) in enumerate(items):
            x, y = (k % cols) * cw, (k // cols) * ch
            sheet.paste(img.convert("RGB"), (x, y))
            d.text((x + 4, y + 4), n, fill=(230, 230, 230))
        sheet.save(os.path.join(a.out, f + "_sheet.png"))


if __name__ == "__main__":
    main()
