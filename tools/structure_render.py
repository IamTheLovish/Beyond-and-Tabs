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

LIGHTING (on by default; --flat draws the old unlit look, Minecraft's fixed 1.0 / 0.8 / 0.6 face shades): lovish's
references are shader screenshots, and flat renders made good shapes look flat to builders and critics
(design/vehicle_labs/LESSONS.md on claude/vehicle-labs, point 7). So every visible pixel is lit like a shader would:
  - sun: a directional light (--sun AZIMUTH,ELEVATION in degrees; azimuth from north, clockwise) shades each face by
    the angle it makes with the sun, so towers have a lit side and a shade side;
  - shadows: the building is voxelised at a quarter block and the sun's shadow is swept down through it layer by layer
    (no per-pixel ray march), so towers throw shadows on roofs, walls and the ground (--no-shadows to skip);
  - ambient occlusion: Minecraft's smooth lighting, from the solid blocks around each face corner and interpolated
    across the face, so inner corners and the foot of a wall darken (--ao-strength, 0 = off);
  - sky tint: the ambient light is sky blue and the sun warm (--no-sky-tint for white light);
  - light sources stay bright: they take no sun, shadow or occlusion.
--ground N lays a grass plane under the building (N blocks around it) to catch its shadow; --ref IMAGE puts a reference
picture beside the renders at the same height, the way critics judge (LESSONS.md point 3).

    python3 tools/structure_render.py metal_extractor metal_extractor_dark [NAME | FILE.nbt ...] -o sheet.png [--px 24]
        [--flat | --sun 250,45 --ao-strength 0.8 --no-shadows --no-sky-tint] [--ground 4] [--ref picture.png ...]
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

# the lit look. sun: unit vector toward the sun (x east, y up, z south); ao: how much a fully enclosed corner darkens;
# shadows / sky: the sun's shadows and the sky-blue ambient + warm sun on or off
Light = collections.namedtuple("Light", "sun ao shadows sky")
AMBIENT, SUN = 0.5, 0.7                     # a face in shadow keeps the ambient half; a top in full sun comes to ~1.0
SKY_COLOUR, SUN_COLOUR = (0.86, 0.92, 1.08), (1.06, 1.0, 0.88)
VOXEL = 4                                   # shadow / occlusion cells per block edge (a quarter block: slabs, stairs)
NORMALS = np.array([(0, 1, 0), (0, 0, 1), (-1, 0, 0)], dtype=np.float64)   # top, south, west - the faces drawn

# one palette entry as drawn: top / side are pack texture names or a flat (r, g, b); opaque: a full cube that hides
# what is behind it; textured: our pack covers it
Look = collections.namedtuple("Look", "name shape top side opaque light textured")


def sun_vector(azimuth, elevation):
    """Unit vector toward the sun from compass degrees: azimuth 0 north (-z), 90 east (+x); elevation above the
    horizon."""
    a, e = math.radians(azimuth), math.radians(elevation)
    return (math.sin(a) * math.cos(e), math.sin(e), -math.cos(a) * math.cos(e))


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


def render(path, tx, faces, px, light=None, ground=0):
    """One building on its own canvas (cropped to it; None when the file holds only air), with its size (x, y, z), the
    number of blocks drawn and how many of them took a texture from our pack. light: a Light, or None for the flat
    look; ground: a grass plane this many blocks around the building (0 = none)."""
    return render_root(PS.load_nbt(path), tx, faces, px, light, ground)


def render_root(root, tx, faces, px, light=None, ground=0):
    """render() of a structure already read: {"size", "palette", "blocks"} as in the .nbt."""
    looks = [look_of(e, tx) for e in root.get("palette") or (root.get("palettes") or [[]])[0]]
    blocks = [(tuple(b["pos"]), looks[b["state"]]) for b in root.get("blocks", [])
              if not PS.invisible(looks[b["state"]].name)]
    if not blocks:
        return None, root["size"], 0, 0
    counted = len(blocks), sum(1 for _, lk in blocks if lk.textured)
    if ground > 0:
        grass = look_of({"Name": "minecraft:grass_block"}, tx)
        sx, _, sz = root["size"]
        blocks += [((x, -1, z), grass) for x in range(-ground, sx + ground) for z in range(-ground, sz + ground)]
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

    # lit: faces are painted at full brightness and each pixel remembers which face it shows (ids), for the light pass
    ids = np.full((canvas.height, canvas.width), -1, dtype=np.int32) if light else None
    face_rec = []                                  # per face id: kind (0 top, 1 south, 2 west), box bounds, emissive

    def paint(img, pts, kind, bounds, emissive):
        at = SP.quad(canvas, img, pts)
        if ids is None:
            return
        minx, miny, alpha = at
        a = np.asarray(alpha) > 0      # translucent glass takes its own light, not the hall's behind
        region = ids[miny:miny + a.shape[0], minx:minx + a.shape[1]]
        region[a[:region.shape[0], :region.shape[1]]] = len(face_rec)
        face_rec.append((kind,) + bounds + (emissive,))

    # painter's order: far (north-east, small z - x) to near, then low to high - by each box's centre
    boxes.sort(key=lambda b: (2 * (b[0][2] - b[0][0]) + b[1][4] + b[1][5] - b[1][0] - b[1][1],
                              2 * b[0][1] + b[1][2] + b[1][3]))
    for (x, y, z), (x0, x1, y0, y1, z0, z1), lk, (s_top, s_south, s_west) in boxes:
        X0, X1, Y0, Y1, Z0, Z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
        bounds = (X0, X1, Y0, Y1, Z0, Z1)
        # Minecraft's default element UVs, from the box in its cell: up (x, z), south (x, 1 - y), west (z, 1 - y)
        model_uv = SHEET_UV.get(lk.side)
        shade = dict.fromkeys(SHADE) if lk.light else dict.fromkeys(SHADE, 1.0) if light else SHADE
        if s_top:
            uv = model_uv["top"] if model_uv else _uv(x0, z0, x1, z1)
            paint(faces.get(lk.top, uv, shade["top"]),
                  [S(X0, Y1, Z0), S(X1, Y1, Z0), S(X1, Y1, Z1), S(X0, Y1, Z1)], 0, bounds, lk.light)
        if s_south:
            uv = model_uv["side"] if model_uv else _uv(x0, 1 - y1, x1, 1 - y0)
            paint(faces.get(lk.side, uv, shade["south"]),
                  [S(X0, Y1, Z1), S(X1, Y1, Z1), S(X1, Y0, Z1), S(X0, Y0, Z1)], 1, bounds, lk.light)
        if s_west:
            uv = model_uv["side"] if model_uv else _uv(z0, 1 - y1, z1, 1 - y0)
            paint(faces.get(lk.side, uv, shade["west"]),
                  [S(X0, Y1, Z0), S(X0, Y1, Z1), S(X0, Y0, Z1), S(X0, Y0, Z0)], 2, bounds, lk.light)
    if light:
        canvas = lit(canvas, ids, np.asarray(face_rec, dtype=np.float64), blocks, light, (hw, qh, hh, left, top))
    canvas.info["origin"] = (left, top)   # world (X, Y, Z) is at P(X, Y, Z) - origin on this canvas (the tests use it)
    return canvas, root["size"], counted[0], counted[1]


# ======================================================================================================= lighting
def voxels(blocks):
    """The blocks' boxes as solid cells, VOXEL to a block edge: (grid [x, y, z] bool, the grid's origin in blocks)."""
    lo = np.min([p for p, _ in blocks], axis=0)
    hi = np.max([p for p, _ in blocks], axis=0) + 1
    v = VOXEL
    blk = np.zeros(tuple(hi - lo), dtype=bool)          # full cubes, a block a cell, upsampled at the end
    grid = np.zeros(tuple((hi - lo) * v), dtype=bool)
    for (x, y, z), lk in blocks:
        if lk.shape is PS.FULL:
            blk[x - lo[0], y - lo[1], z - lo[2]] = True
            continue
        for x0, x1, y0, y1, z0, z1 in lk.shape:
            cells = []
            for c, a0, a1, lim in ((x - lo[0], x0, x1, grid.shape[0]), (y - lo[1], y0, y1, grid.shape[1]),
                                   (z - lo[2], z0, z1, grid.shape[2])):
                i0 = int(math.floor((c + a0) * v + 0.5))
                i1 = max(i0 + 1, int(math.floor((c + a1) * v + 0.5)))   # a thin post still fills one cell
                cells.append(slice(max(0, i0), min(lim, i1)))
            grid[tuple(cells)] = True
    grid |= blk.repeat(v, 0).repeat(v, 1).repeat(v, 2)
    return grid, lo.astype(np.float64)


def sun_shadow(grid, sun):
    """Each cell in the sun's shadow (True) or lit: swept from the top layer down, a cell is shadowed when the cell a
    layer above it toward the sun is solid or shadowed. The sideways step a layer is the sun's slope, rounded per layer
    (Bresenham), so it never drifts."""
    nx, ny, nz = grid.shape
    kx, kz = sun[0] / sun[1], sun[2] / sun[1]
    shad = np.zeros_like(grid)
    for y in range(ny - 2, -1, -1):
        above = grid[:, y + 1, :] | shad[:, y + 1, :]
        dx = round((y + 1) * kx) - round(y * kx)
        dz = round((y + 1) * kz) - round(y * kz)
        # shad[x, z] = above[x + dx, z + dz] (out of the grid: open sky)
        dst = shad[:, y, :]
        xs, xd = (slice(dx, nx), slice(0, nx - dx)) if dx >= 0 else (slice(0, nx + dx), slice(-dx, nx))
        zs, zd = (slice(dz, nz), slice(0, nz - dz)) if dz >= 0 else (slice(0, nz + dz), slice(-dz, nz))
        dst[xd, zd] = above[xs, zs]
    return shad


def trilinear(field, pts):
    """field sampled at pts (n x 3, in cells, cell centres at i + 0.5); outside the field reads 0."""
    f = pts - 0.5
    i0 = np.floor(f).astype(np.int64)
    t = f - i0
    out = np.zeros(len(pts))
    shape = np.array(field.shape)
    for corner in range(8):
        o = np.array([(corner >> 2) & 1, (corner >> 1) & 1, corner & 1])
        idx = i0 + o
        ok = np.all((idx >= 0) & (idx < shape), axis=1)
        w = np.prod(np.where(o, t, 1 - t), axis=1)
        val = np.zeros(len(pts))
        val[ok] = field[idx[ok, 0], idx[ok, 1], idx[ok, 2]]
        out += w * val
    return out


def lit(canvas, ids, rec, blocks, light, view):
    """The light pass: every pixel's world point from its face and screen position, then sun, shadow, occlusion and
    tint on it. rec rows: kind, X0, X1, Y0, Y1, Z0, Z1, emissive."""
    hw, qh, hh, left, top = view
    ys, xs = np.nonzero(ids >= 0)
    f = rec[ids[ys, xs]]
    kind = f[:, 0].astype(np.int64)
    # screen -> world on the face's plane, inverting P: a = X + Z, b = Z - X - Y * hh / qh
    a = (xs + 0.5 + left) / hw
    sy = ys + 0.5 + top
    X, Y, Z = np.empty(len(xs)), np.empty(len(xs)), np.empty(len(xs))
    t = kind == 0                                   # top: Y fixed
    Y[t] = f[t, 4]
    b = (sy[t] + Y[t] * hh) / qh
    X[t], Z[t] = (a[t] - b) / 2, (a[t] + b) / 2
    s = kind == 1                                   # south: Z fixed
    Z[s] = f[s, 6]
    X[s] = a[s] - Z[s]
    w = kind == 2                                   # west: X fixed
    X[w] = f[w, 1]
    Z[w] = a[w] - X[w]
    sw = s | w
    Y[sw] = ((Z[sw] - X[sw]) * qh - sy[sw]) / hh
    p = np.stack([np.clip(X, f[:, 1], f[:, 2]), np.clip(Y, f[:, 3], f[:, 4]), np.clip(Z, f[:, 5], f[:, 6])], axis=1)
    n = NORMALS[kind]

    grid, origin = voxels(blocks)
    v = VOXEL
    sun = np.asarray(light.sun)
    ndl = np.clip(n @ sun, 0, None)
    vis = np.ones(len(xs))
    if light.shadows:
        dark = sun_shadow(grid, sun) | grid
        vis = 1 - trilinear(dark.astype(np.float32), (p + n * (0.5 / v) - origin) * v)
    ao = np.ones(len(xs))
    if light.ao > 0:
        # Minecraft's smooth lighting: how solid the block layer in front of the face is around this point - at a
        # corner the four blocks touching it, inside the face interpolated between the corners
        nb = np.array(grid.shape) // v
        solid = grid.reshape(nb[0], v, nb[1], v, nb[2], v).mean(axis=(1, 3, 5))
        ao = 1 - light.ao * trilinear(solid, p + n * 0.5 - origin)
    sky, sun_c = (np.asarray(SKY_COLOUR), np.asarray(SUN_COLOUR)) if light.sky else (np.ones(3), np.ones(3))
    gain = (AMBIENT * ao)[:, None] * sky + (SUN * ndl * vis * (0.6 + 0.4 * ao))[:, None] * sun_c
    gain[f[:, 7] > 0] = 1.0                         # light sources: as painted
    rgba = np.asarray(canvas).astype(np.float64)
    rgba[ys, xs, :3] = np.clip(rgba[ys, xs, :3] * gain, 0, 255)
    return Image.fromarray(rgba.round().astype(np.uint8), "RGBA")


def parse_sun(text):
    try:
        az, el = (float(t) for t in text.split(","))
    except ValueError:
        raise argparse.ArgumentTypeError("--sun wants AZIMUTH,ELEVATION in degrees, e.g. 250,45") from None
    if not 5 <= el <= 90:
        raise argparse.ArgumentTypeError("--sun elevation must be 5..90 degrees")
    return az, el


def main():
    ap = argparse.ArgumentParser(description="Buildings with our block pack on their real shapes, from the RTS side.")
    ap.add_argument("structures", nargs="+", metavar="NAME_OR_NBT",
                    help="structure names (bar/.../assets/reignofnether/structures/<name>.nbt) or .nbt paths")
    ap.add_argument("-o", "--out", default="structure_render.png", help="the sheet PNG to write")
    ap.add_argument("--px", type=int, default=24, help="screen pixels per block (24-36 = the RTS default zoom)")
    ap.add_argument("--flat", action="store_true", help="the old unlit look: Minecraft's fixed face shades, no sun")
    ap.add_argument("--sun", type=parse_sun, default=(250.0, 45.0), metavar="AZ,EL",
                    help="sun direction in degrees: azimuth from north clockwise, elevation (default 250,45: from the "
                         "west-south-west, shadows fall to the right)")
    ap.add_argument("--ao-strength", type=float, default=0.8, help="ambient occlusion: 0 off .. 1 strongest")
    ap.add_argument("--shadows", action=argparse.BooleanOptionalAction, default=True, help="cast sun shadows")
    ap.add_argument("--sky-tint", action=argparse.BooleanOptionalAction, default=True,
                    help="sky-blue ambient light and a warm sun (off: white light)")
    ap.add_argument("--ground", type=int, default=0, metavar="N",
                    help="a grass plane under the building, N blocks around it, to catch its shadow (default none)")
    ap.add_argument("--ref", action="append", default=[], metavar="IMAGE",
                    help="a reference picture to put beside the renders at their height (repeatable)")
    a = ap.parse_args()
    if not os.path.isdir(SP.PACK):
        sys.exit("structure_render: no block pack at %s - generate it: python3 tools/gen_blocks.py" % SP.PACK)
    light = None if a.flat else Light(sun_vector(*a.sun), max(0.0, min(1.0, a.ao_strength)), a.shadows, a.sky_tint)
    tx = SP.Textures(a.px)
    faces = Faces(tx)
    shots = []
    for arg in a.structures:
        path = structure_path(arg)
        label = os.path.splitext(os.path.basename(path))[0]
        img, size, total, textured = render(path, tx, faces, a.px, light, a.ground)
        size = "x".join(str(v) for v in size)
        if img is None:
            print("%-28s %-9s only air - skipped" % (label, size))
            continue
        print("%-28s %-9s %5d blocks, %3.0f%% from our pack" % (label, size, total, 100.0 * textured / total))
        shots.append(("%s  %s" % (label, size), img))
    if not shots:
        sys.exit("structure_render: nothing to draw")
    if a.ref:
        # references beside the renders, scaled to the tallest render (judge the building against the picture)
        tall = max(img.height for _, img in shots)
        for ref in a.ref:
            pic = Image.open(ref).convert("RGBA")
            pic = pic.resize((max(1, round(pic.width * tall / pic.height)), tall), Image.LANCZOS)
            shots.append(("reference: %s" % os.path.basename(ref), pic))
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
