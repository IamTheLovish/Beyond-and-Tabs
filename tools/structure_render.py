#!/usr/bin/env python3
"""Buildings as the RTS camera sees them: OUR block pack's textures on the blocks' real shapes (stairs, slabs, walls,
fences, lanterns, banners...), several buildings to one sheet - judge a building's look before a CI run.

A tool of its own rather than a structure_preview.py flag: that one audits the pack's coverage per faction (full
cubes, missing-texture crosses, a folder of sheets), this one shows a few named buildings as built, so it takes
preview_structure.py's arguments (files or names -> one PNG) and borrows both tools' parts.

Shapes are preview_structure.shape_for's boxes; textures are structure_preview's guesses from
resourcepacks/BeyondAndTabs-Realistic (generate it first: python3 tools/gen_blocks.py), mapped the way Minecraft maps
a model element's faces, and a block the pack lacks takes preview_structure's flat palette colour. The view is the
RTS camera's default: it looks north-east from the south-west, so it sees the roofs and the west (-x) and south (+z)
faces with south-east to its right - drawn as that rotation, not as a mirror (preview_structure --rts), so a feature
on a building's east side stays on the right. Light sources (lanterns, froglights, sea lanterns, glowstone,
shroomlight, end rods, torches, lit candles and campfires...) skip the face shading and are drawn brighter, so a
base's night lights show where they are.

    python3 tools/structure_render.py metal_extractor metal_extractor_dark [NAME | FILE.nbt ...] -o sheet.png [--px 24]
Prints each building's size and share of blocks textured from the pack.
"""
import argparse
import collections
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_structure as PS   # noqa: E402  - the NBT reader, block shapes and flat palette
import structure_preview as SP   # noqa: E402  - our pack's textures (by name) and the textured-quad painter

# Minecraft's own face shading (up 1.0, north / south 0.8, east / west 0.6), so faces darken as they do in game
SHADE = {"top": 1.0, "south": 0.8, "west": 0.6}
GLOW = 1.35   # a light source's brightening, on every face (capped per texel where a channel would clip)
LIGHTS = ("lantern", "froglight", "glowstone", "shroomlight", "end_rod", "torch", "campfire", "candle", "fire", "lava",
          "crying_obsidian", "magma_block", "redstone_lamp", "beacon")
# pack textures laid out as a model's sheet rather than one face: where the model takes its faces (u0 v0 u1 v1)
_LANTERN = {"side": (0, 2 / 16, 6 / 16, 9 / 16), "top": (0, 9 / 16, 6 / 16, 15 / 16)}
SHEET_UV = {"lantern": _LANTERN, "soul_lantern": _LANTERN}
BACKGROUND = (24, 24, 28)

# one palette entry as drawn: top / side are pack texture names or a flat (r, g, b); opaque: a full cube that hides
# what is behind it; textured: our pack covers it
Look = collections.namedtuple("Look", "name shape top side opaque light textured")


def is_light(name, props):
    """A light source that is lit (candles, campfires and redstone lamps carry lit=false when out)."""
    return name.endswith(LIGHTS) and props.get("lit", "true") == "true"


def look_of(entry, tx):
    """How a palette entry is drawn. Only minecraft: blocks look in our pack (it retextures vanilla); a modded block
    (Macaw's roofs and windows) takes its flat palette colour."""
    name = entry.get("Name", "minecraft:air")
    props = entry.get("Properties", {})
    n = name.split(":")[-1]
    shape = PS.shape_for(n, props)
    top, side = tx.resolve(n, props.get("axis", "y")) if name.startswith("minecraft:") else (None, None)
    top, side = top or side, side or top
    textured = top is not None
    if not textured:
        top = side = PS.colour_for(name)
    opaque = shape is PS.FULL and all(isinstance(t, tuple) or tx.get(t)[..., 3].min() > 0.99 for t in (top, side))
    return Look(name, shape, top, side, opaque, is_light(n, props), textured)


class Faces:
    """Texture crops shaded and ready to paste, cached: a building repeats a few dozen (texture, crop, shade) triples
    thousands of times."""

    def __init__(self, tx):
        self.tx = tx
        self.cache = {}

    def get(self, tex, uv, shade):
        """tex: a pack texture name or an (r, g, b) flat colour; uv: (u0, v0, u1, v1) in 0..1, v down the texture;
        shade: the face's factor, or None for a light source - each texel brightened up to GLOW but never past full in
        any channel, so the three froglights keep their ochre / green / pink apart and a lantern's frame stays dark."""
        key = (tex, uv, shade)
        if key not in self.cache:
            if isinstance(tex, tuple):
                a = np.ones((2, 2, 4))
                a[..., :3] = np.asarray(tex) / 255.0
            else:
                a = self.tx.get(tex)
            h, w = a.shape[:2]
            c0, r0 = min(int(uv[0] * w), w - 1), min(int(uv[1] * h), h - 1)
            c1, r1 = max(c0 + 1, math.ceil(uv[2] * w)), max(r0 + 1, math.ceil(uv[3] * h))
            a = a[r0:r1, c0:c1].copy()
            if shade is None:
                a[..., :3] /= np.maximum(a[..., :3].max(axis=-1, keepdims=True), 1 / GLOW)
            self.cache[key] = SP.face_image(a, 1.0 if shade is None else shade)
        return self.cache[key]


def structure_path(arg):
    """A .nbt path as given, else a structure name in the mod's structures folder."""
    if os.path.isfile(arg):
        return arg
    p = os.path.join(SP.STRUCT_DIR, arg if arg.endswith(".nbt") else arg + ".nbt")
    if not os.path.isfile(p):
        sys.exit("structure_render: %r is neither a file nor a structure in %s" % (arg, SP.STRUCT_DIR))
    return p


def _uv(lo_u, lo_v, hi_u, hi_v):
    return tuple(round(min(1.0, max(0.0, c)), 3) for c in (lo_u, lo_v, hi_u, hi_v))


def render(path, tx, faces, px):
    """One building on its own canvas (cropped to it; None when the file holds only air), with its size (x, y, z), the
    number of blocks drawn and how many of them took a texture from our pack."""
    root = PS.load_nbt(path)
    looks = [look_of(e, tx) for e in root.get("palette") or (root.get("palettes") or [[]])[0]]
    blocks = [(tuple(b["pos"]), looks[b["state"]]) for b in root.get("blocks", [])
              if not PS.invisible(looks[b["state"]].name)]
    if not blocks:
        return None, root["size"], 0, 0
    full = {pos: lk for pos, lk in blocks if lk.shape is PS.FULL}

    def hidden(pos, lk):
        """A full cube's face is hidden by an opaque cube, or by the same block (glass on glass, water in water)."""
        nb = full.get(pos)
        return lk.shape is PS.FULL and nb is not None and (nb.opaque or nb.name == lk.name)

    boxes = []
    for (x, y, z), lk in blocks:
        # the faces the camera sees: top, south (+z), west (-x)
        show = (not hidden((x, y + 1, z), lk), not hidden((x, y, z + 1), lk), not hidden((x - 1, y, z), lk))
        if any(show):
            boxes += [((x, y, z), box, lk, show) for box in lk.shape]
    hw, qh, hh = px / 2, px / 4, px * 0.6       # half a block's width, a quarter (the top rhombus), a side's height

    def P(X, Y, Z):
        # +x (east) runs up-right on screen and +z (south) down-right: the camera's right is south-east
        return (X + Z) * hw, (Z - X) * qh - Y * hh

    corners = [P(x + bx, y + by, z + bz) for (x, y, z), (x0, x1, y0, y1, z0, z1), *_ in boxes
               for bx in (x0, x1) for by in (y0, y1) for bz in (z0, z1)]
    left, top = min(c[0] for c in corners) - 4, min(c[1] for c in corners) - 4
    canvas = Image.new("RGBA", (int(max(c[0] for c in corners) - left) + 5, int(max(c[1] for c in corners) - top) + 5),
                       BACKGROUND + (255,))

    def S(X, Y, Z):
        sx, sy = P(X, Y, Z)
        return sx - left, sy - top

    # painter's order: far (north-east, small z - x) to near, then low to high - by each box's centre
    boxes.sort(key=lambda b: (2 * (b[0][2] - b[0][0]) + b[1][4] + b[1][5] - b[1][0] - b[1][1],
                              2 * b[0][1] + b[1][2] + b[1][3]))
    for (x, y, z), (x0, x1, y0, y1, z0, z1), lk, (s_top, s_south, s_west) in boxes:
        X0, X1, Y0, Y1, Z0, Z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
        # Minecraft's default element UVs, from the box in its cell: up (x, z), south (x, 1 - y), west (z, 1 - y)
        model_uv = SHEET_UV.get(lk.side)
        shade = dict.fromkeys(SHADE) if lk.light else SHADE
        if s_top:
            uv = model_uv["top"] if model_uv else _uv(x0, z0, x1, z1)
            SP.quad(canvas, faces.get(lk.top, uv, shade["top"]),
                    [S(X0, Y1, Z0), S(X1, Y1, Z0), S(X1, Y1, Z1), S(X0, Y1, Z1)])
        if s_south:
            uv = model_uv["side"] if model_uv else _uv(x0, 1 - y1, x1, 1 - y0)
            SP.quad(canvas, faces.get(lk.side, uv, shade["south"]),
                    [S(X0, Y1, Z1), S(X1, Y1, Z1), S(X1, Y0, Z1), S(X0, Y0, Z1)])
        if s_west:
            uv = model_uv["side"] if model_uv else _uv(z0, 1 - y1, z1, 1 - y0)
            SP.quad(canvas, faces.get(lk.side, uv, shade["west"]),
                    [S(X0, Y1, Z0), S(X0, Y1, Z1), S(X0, Y0, Z1), S(X0, Y0, Z0)])
    return canvas, root["size"], len(blocks), sum(1 for _, lk in blocks if lk.textured)


def main():
    ap = argparse.ArgumentParser(description="Buildings with our block pack on their real shapes, from the RTS side.")
    ap.add_argument("structures", nargs="+", metavar="NAME_OR_NBT",
                    help="structure names (bar/.../assets/reignofnether/structures/<name>.nbt) or .nbt paths")
    ap.add_argument("-o", "--out", default="structure_render.png", help="the sheet PNG to write")
    ap.add_argument("--px", type=int, default=24, help="screen pixels per block (24-36 = the RTS default zoom)")
    a = ap.parse_args()
    if not os.path.isdir(SP.PACK):
        sys.exit("structure_render: no block pack at %s - generate it: python3 tools/gen_blocks.py" % SP.PACK)
    tx = SP.Textures(a.px)
    faces = Faces(tx)
    shots = []
    for arg in a.structures:
        path = structure_path(arg)
        label = os.path.splitext(os.path.basename(path))[0]
        img, size, total, textured = render(path, tx, faces, a.px)
        size = "x".join(str(v) for v in size)
        if img is None:
            print("%-28s %-9s only air - skipped" % (label, size))
            continue
        print("%-28s %-9s %5d blocks, %3.0f%% from our pack" % (label, size, total, 100.0 * textured / total))
        shots.append(("%s  %s" % (label, size), img))
    if not shots:
        sys.exit("structure_render: nothing to draw")
    # one row, bottoms on one line (each building's front corner at ground level) and one scale, so sizes compare
    font = ImageFont.load_default(size=max(12, int(a.px * 0.6)))
    gap = max(12, a.px)
    widths = [max(img.width, int(font.getlength(label)) + 4) for label, img in shots]
    height = 2 * font.size + max(img.height for _, img in shots) + gap
    sheet = Image.new("RGB", (sum(widths) + gap * (len(shots) + 1), height), BACKGROUND)
    d = ImageDraw.Draw(sheet)
    x = gap
    for (label, img), w in zip(shots, widths):
        sheet.paste(img.convert("RGB"), (x + (w - img.width) // 2, sheet.height - gap - img.height))
        d.text((x, gap // 2), label, fill=(230, 230, 230), font=font)
        x += w + gap
    sheet.save(a.out)
    print("wrote", a.out, sheet.size)


if __name__ == "__main__":
    main()
