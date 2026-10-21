#!/usr/bin/env python3
"""The White Angel capitol's review sheet, offline: every level drawn with OUR block pack's textures on the blocks' real
shapes (stairs, slabs, walls, panes...) from the RTS camera, a low front view (the castle reference's camera), the full
zoom-out, close crops of the focal points (the crown with the drawn angel, the hall front, the gate) - so a round of
the capitol is judged against design/ART-FAULTS.md before lovish or a CI run sees it.

Why its own tool: structure_render.py draws one fixed dimetric view in painter's order; this draws any camera with a
z-buffer (a z-buffered quad rasteriser, tools/angel_statue_preview.Raster), lights faces by their normal, brightens the
light sources, and stands the drawn marble angel (design/models/white_angel_statue.json through dsl_to_game's painted
atlas, posed as BuildingParts.statueWingFold poses it at that level) on the capitol's plinth. Blocks our pack lacks
(Macaw's, Chipped) take preview_structure's flat palette colour.

    python3 tools/capitol_sheet.py --out design/buildings/white_angel_capitol_v5.png [--gen capitol_white_angel_v5]
        [--title "..."] [--levels 1,3,6] [--quick]
    python3 tools/capitol_sheet.py --nbt L1.nbt L2.nbt ... --out sheet.png    (files instead of the generator)

The RTS camera looks north-east from the south-west, pitched down 50 degrees (OrthoviewClientEvents' default).
"""
import argparse
import importlib
import math
import os
import sys
import time

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_structure as PS                        # noqa: E402  - block shapes and the flat palette
import structure_preview as SP                        # noqa: E402  - our pack's textures by block name
from preview_model import view_matrix                 # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SKY = np.array([0.80, 0.85, 0.90], dtype=np.float32)
BG = (24, 24, 28)
RTS_VIEW = (-45.0, 50.0)          # the RTS camera (w' = (x, y, -z): the viewer looks along +z after the turn)
FRONT_VIEW = (-14.0, 11.0)        # the reference's camera: low, a little left of front
SUN = np.array([-0.45, 0.80, -0.55])  # from the upper south-west, where the RTS camera stands
SUN = SUN / np.linalg.norm(SUN)
GLOW = 1.35
LIGHTS = ("lantern", "froglight", "glowstone", "shroomlight", "end_rod", "torch", "campfire", "candle",
          "sea_lantern", "redstone_lamp", "beacon", "light")
# a translucent pane (the lit windows): drawn in its own warm colour over the glow block behind it
PANE_TINT = {"yellow_stained_glass_pane": (236, 196, 92), "orange_stained_glass_pane": (232, 150, 60),
             "gray_stained_glass_pane": (110, 112, 118), "black_stained_glass_pane": (40, 40, 44),
             "glass_pane": (190, 214, 230), "glass": (190, 214, 230), "white_stained_glass_pane": (225, 228, 232),
             "light_blue_stained_glass_pane": (150, 200, 235), "red_stained_glass": (190, 50, 50),
             "blue_stained_glass": (60, 80, 190), "yellow_stained_glass": (236, 196, 92),
             "purple_stained_glass": (130, 60, 170), "light_blue_stained_glass": (150, 200, 235),
             "white_stained_glass": (225, 228, 232), "orange_stained_glass": (232, 150, 60)}
FULL_BOX = (0, 1, 0, 1, 0, 1)


# ------------------------------------------------------------------ the rasteriser ------------------------------
class Raster:
    """A z-buffered rasteriser of quads in view space (x right, y up, the viewer looking along +z); textures are
    sampled per pixel (nearest texel), so a 64x stone reads as stone at 26 px a block and calms down at 6."""

    def __init__(self, w, h, scale, ox, oy, bg):
        self.w, self.h, self.sc, self.ox, self.oy = w, h, scale, ox, oy
        self.img = np.zeros((h, w, 3), dtype=np.float32)
        self.img[:] = bg
        self.z = np.full((h, w), np.inf, dtype=np.float32)

    def quad(self, P, colour=None, uvs=None, tex=None, light=1.0):
        S = [(self.ox + p[0] * self.sc, self.oy - p[1] * self.sc, p[2]) for p in P]
        e1x, e1y = S[0][0] - S[1][0], S[0][1] - S[1][1]
        e2x, e2y = S[2][0] - S[1][0], S[2][1] - S[1][1]
        det = e1x * e2y - e1y * e2x
        if abs(det) < 1e-9:
            return
        xs = (S[0][0], S[1][0], S[2][0], S[3][0])
        ys = (S[0][1], S[1][1], S[2][1], S[3][1])
        x0, x1 = max(0, int(math.floor(min(xs)))), min(self.w - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(self.h - 1, int(math.ceil(max(ys))))
        if x1 < x0 or y1 < y0:
            return
        gx = np.arange(x0, x1 + 1, dtype=np.float32)[None, :] + 0.5 - S[1][0]
        gy = np.arange(y0, y1 + 1, dtype=np.float32)[:, None] + 0.5 - S[1][1]
        a = (gx * e2y - gy * e2x) / det
        b = (e1x * gy - e1y * gx) / det
        inside = (a >= -1e-3) & (a <= 1.001) & (b >= -1e-3) & (b <= 1.001)
        if not inside.any():
            return
        z = S[1][2] + a * (S[0][2] - S[1][2]) + b * (S[2][2] - S[1][2])
        sub = self.z[y0:y1 + 1, x0:x1 + 1]
        if tex is not None:
            base, density = tex
            th, tw = base.shape[:2]
            u = uvs[1][0] + a * (uvs[0][0] - uvs[1][0]) + b * (uvs[2][0] - uvs[1][0])
            v = uvs[1][1] + a * (uvs[0][1] - uvs[1][1]) + b * (uvs[2][1] - uvs[1][1])
            texel = base[np.clip((v * density).astype(np.int32), 0, th - 1), np.clip((u * density).astype(np.int32), 0, tw - 1)]
            ok = inside & (texel[..., 3] > 0.1) & (z < sub)
            if not ok.any():
                return
            col = texel[..., :3] * light
        else:
            ok = inside & (z < sub)
            if not ok.any():
                return
            col = np.broadcast_to(np.asarray(colour, dtype=np.float32) * light, ok.shape + (3,))
        self.img[y0:y1 + 1, x0:x1 + 1][ok] = col[ok]
        sub[ok] = z[ok]

    def image(self):
        return Image.fromarray((np.clip(self.img, 0, 1) * 255).astype(np.uint8))


# ------------------------------------------------------------------ the blocks --------------------------------
class Looks:
    """How each block id is drawn: pack textures (top, side) as float arrays at T px, or a flat colour; whether it is
    a light; whether a full cube of it hides what is behind."""

    def __init__(self, T=32):
        self.T = T
        self.tx = SP.Textures(T)
        self.cache = {}

    def of(self, name, props):
        key = (name, props.get("axis", "y"))
        if key in self.cache:
            return self.cache[key]
        n = name.split(":")[-1]
        light = n.endswith(LIGHTS) and props.get("lit", "true") == "true"
        top = side = None
        if (name.startswith("minecraft:") or ":" not in name) and n not in PANE_TINT:
            top, side = self.tx.resolve(n, props.get("axis", "y"))
            top, side = top or side, side or top
        if top is not None:
            ta, sa = self.tx.get(top).astype(np.float32), self.tx.get(side).astype(np.float32)
            col = None
            opaque = ta[..., 3].min() > 0.99 and sa[..., 3].min() > 0.99
        else:
            ta = sa = None
            c = PANE_TINT.get(n) or PS.colour_for(name)
            col = np.array(c, dtype=np.float32) / 255.0
            opaque = n not in PANE_TINT and not n.endswith(("leaves", "_pane", "bars", "vine"))
        look = (ta, sa, col, light, opaque)
        self.cache[key] = look
        return look


def shape_of(name, props):
    """preview_structure's box shapes, plus the thin plants it draws as full cubes: a vine is a plate on the face it
    hangs from, hanging roots a thin cross."""
    n = name.split(":")[-1]
    if n == "vine":
        for f, box in (("north", (0, 1, 0, 1, 0, 0.06)), ("south", (0, 1, 0, 1, 0.94, 1)),
                       ("west", (0, 0.06, 0, 1, 0, 1)), ("east", (0.94, 1, 0, 1, 0, 1))):
            if props.get(f) == "true":
                return [box]
        return [(0, 1, 0.94, 1, 0, 1)]
    if n in ("hanging_roots", "fern", "grass", "tall_grass"):
        return [(0.3, 0.7, 0, 0.9, 0.3, 0.7)]
    return PS.shape_for(name, props)


def faces_of_rig(cells, looks, R):
    """The faces the camera sees, as (corners in w', normal in w', look, uv rect, which texture). cells: {(x, y, z):
    (name, props)}. A full cube's face against an opaque full cube is dropped; faces turned away from the camera too."""
    shapes = {}
    full = set()
    for p, (name, props) in cells.items():
        if PS.invisible(name):
            continue
        sh = shape_of(name, props)
        shapes[p] = (sh, name, props)
        if sh is PS.FULL and looks.of(name, props)[4]:
            full.add(p)
    view_dir = np.array([0.0, 0.0, 1.0])                 # the viewer looks along +z in view space
    # the six face normals in w' = (x, y, -z): north (-z world) is +z' ... keep world labels, convert at the end
    out = []
    for (x, y, z), (sh, name, props) in shapes.items():
        look = looks.of(name, props)
        for (x0, x1, y0, y1, z0, z1) in sh:
            X0, X1, Y0, Y1, Z0, Z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
            # (normal world, hidden-test neighbour, corners (world), uv, which)
            cand = (
                ((0, 1, 0), (x, y + 1, z) if y1 >= 1 else None,
                 [(X0, Y1, Z0), (X1, Y1, Z0), (X1, Y1, Z1), (X0, Y1, Z1)], (x0, z0, x1, z1), "top"),
                ((0, -1, 0), (x, y - 1, z) if y0 <= 0 else None,
                 [(X0, Y0, Z1), (X1, Y0, Z1), (X1, Y0, Z0), (X0, Y0, Z0)], (x0, z0, x1, z1), "top"),
                ((0, 0, 1), (x, y, z + 1) if z1 >= 1 else None,
                 [(X0, Y1, Z1), (X1, Y1, Z1), (X1, Y0, Z1), (X0, Y0, Z1)], (x0, 1 - y1, x1, 1 - y0), "side"),
                ((0, 0, -1), (x, y, z - 1) if z0 <= 0 else None,
                 [(X1, Y1, Z0), (X0, Y1, Z0), (X0, Y0, Z0), (X1, Y0, Z0)], (x0, 1 - y1, x1, 1 - y0), "side"),
                ((1, 0, 0), (x + 1, y, z) if x1 >= 1 else None,
                 [(X1, Y1, Z1), (X1, Y1, Z0), (X1, Y0, Z0), (X1, Y0, Z1)], (z0, 1 - y1, z1, 1 - y0), "side"),
                ((-1, 0, 0), (x - 1, y, z) if x0 <= 0 else None,
                 [(X0, Y1, Z0), (X0, Y1, Z1), (X0, Y0, Z1), (X0, Y0, Z0)], (z0, 1 - y1, z1, 1 - y0), "side"),
            )
            for nrm, nb, corners, uv, which in cand:
                if nb is not None and nb in full:
                    continue
                nw = np.array([nrm[0], nrm[1], -nrm[2]], dtype=np.float32)
                if (R @ nw) @ view_dir > 1e-6:
                    continue                                    # faces away from the camera
                out.append((corners, nw, look, uv, which))
    return out


DUSK = np.array([0.30, 0.34, 0.46], dtype=np.float32)   # the unlit world at dusk: dim, blue
DUSK_SKY = np.array([0.22, 0.24, 0.36], dtype=np.float32)
WARM = np.array([1.0, 0.86, 0.62], dtype=np.float32)      # a lit window's or lantern's own light


def draw_cells(r, R, faces, T, dusk=False):
    """Draws prepared faces: sun light by the normal (top faces brightest), light sources at GLOW unshaded. At dusk
    everything unlit goes dim and blue while the lights, the lit panes and the glow blocks keep their warmth - the
    'light is detail' test (lovish's lit cottage reference)."""
    for corners, nw, (ta, sa, col, light, opaque), (u0, v0, u1, v1), which in faces:
        P = [R @ np.array([c[0], c[1], -c[2]], dtype=np.float32) for c in corners]
        if light:
            lt = GLOW if not dusk else GLOW * WARM
        else:
            d = float(nw @ SUN)
            lt = 0.42 + 0.58 * max(0.0, d) + (0.08 if nw[1] > 0.5 else 0.0)
            lt = min(1.0, lt)
            if dusk:
                lit_pane = col is not None and ta is None and (col[0] > 0.8 and col[1] > 0.6 and col[2] < 0.5)
                lt = (WARM * 1.15) if lit_pane else DUSK * (0.6 + 0.4 * lt)
        if ta is not None:
            tex = ta if which == "top" else sa
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            r.quad(P, uvs=uvs, tex=(tex, T), light=lt)
        else:
            r.quad(P, colour=col, light=lt)


# ------------------------------------------------------------------ the statue --------------------------------
def statue_overlay(r, R, level, anchor, px):
    """The drawn angel on its plinth (tools/angel_statue_preview: the statue's faces posed at `level`)."""
    import angel_statue_preview as ASP
    S = ASP.statue_model()
    k = ASP.HEIGHT_BLOCKS / S["model_px"]
    ax, ay, az = anchor
    o = np.array([ax + 0.5, ay, -(az + 0.5)])
    faces = ASP.statue_faces(level, detail=px >= 18)
    ASP.draw_statue(r, R, faces, o, k)


# ------------------------------------------------------------------ views --------------------------------------
def view(cells, R, px, looks, crop=None, size=None, statue=None, pad=12, dusk=False):
    """One view of a cell dict. crop: (centre in world (x, y, z), None) for a square close-up of `size` px."""
    faces = faces_of_rig(cells, looks, R)
    if crop is None:
        pts = [R @ np.array([c[0], c[1], -c[2]]) for f in faces for c in f[0]]
        if statue is not None:                            # the statue's reach above its anchor stays in the frame
            ax, ay, az = statue[1]
            pts += [R @ np.array([ax + dx, ay + dy, -(az + dz)]) for dx in (-12, 12) for dy in (0, 22) for dz in (-12, 12)]
        pts = np.array(pts)
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        W, H = int((hi[0] - lo[0]) * px) + 2 * pad, int((hi[1] - lo[1]) * px) + 2 * pad
        cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    else:
        c = R @ np.array([crop[0][0], crop[0][1], -crop[0][2]])
        W = H = size
        cx, cy = c[0], c[1]
    r = Raster(W, H, px, W / 2 - cx * px, H / 2 + cy * px, DUSK_SKY if dusk else SKY)
    draw_cells(r, R, faces, looks.T, dusk=dusk)
    if statue is not None:
        statue_overlay(r, R, statue[0], statue[1], px)
    return r.image()


def rig_cells(rig):
    return {p: (nm if ":" in nm else "minecraft:" + nm, pr) for p, (nm, pr) in rig.b.items()}


def nbt_cells(path):
    root = PS.load_nbt(path)
    pal = root.get("palette") or (root.get("palettes") or [[]])[0]
    out = {}
    for b in root.get("blocks", []):
        e = pal[b["state"]]
        out[tuple(b["pos"])] = (e.get("Name", "minecraft:air"), e.get("Properties", {}))
    return out


def font(sz):
    d = os.path.join(ROOT, "tools", "fonts")
    if os.path.isdir(d):
        for f in sorted(os.listdir(d)):
            if f.endswith(".ttf"):
                return ImageFont.truetype(os.path.join(d, f), sz)
    return ImageFont.load_default()


def label_row(sheet, d, y, items, f, pad=24, align_bottom=True):
    """Pastes (label, image) pairs in a row; returns the row's height."""
    x = pad
    h = max(im.height for _, im in items) + 30
    for label, im in items:
        d.text((x, y), label, fill=(230, 200, 120), font=f)
        sheet.paste(im, (x, y + 30 + (h - 30 - im.height if align_bottom else 0)))
        x += im.width + pad
    return h


# ------------------------------------------------------------------ the sheet ----------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True)
    ap.add_argument("--gen", default="capitol_white_angel_v5", help="the generator module (tools/<name>.py)")
    ap.add_argument("--nbt", nargs="*", help="level .nbt files instead of the generator (L1 first)")
    ap.add_argument("--title", default=None)
    ap.add_argument("--levels", default="1,2,3,4,5,6")
    ap.add_argument("--quick", action="store_true", help="the RTS views only (a fast look between edits)")
    ap.add_argument("--parts", default="rts,front,far,dusk,growth,crops", help="which panels to draw (a fast look)")
    ap.add_argument("--no-statue", action="store_true")
    a = ap.parse_args()
    t0 = time.time()
    levels = [int(s) for s in a.levels.split(",")]
    if a.nbt:
        cells = {n + 1: nbt_cells(p) for n, p in enumerate(a.nbt)}
        anchor = None
        title = a.title or "capitol levels"
    else:
        G = importlib.import_module(a.gen)
        rigs = [G.level(n) for n in range(1, max(levels) + 1)]
        if hasattr(G, "check"):
            print("check:", G.check(rigs))
        cells = {n + 1: rig_cells(r) for n, r in enumerate(rigs)}
        anchor = getattr(G, "STATUE_ANCHOR", None)
        title = a.title or ("%s - %s x %s x %s, six levels" % (a.gen, rigs[-1].size[0], rigs[-1].size[1], rigs[-1].size[2]))
    if a.no_statue:
        anchor = None
    print("levels built %.1fs" % (time.time() - t0))
    top = max(levels)
    looks_far, looks_near = Looks(32), Looks(64)
    R_rts = np.array(view_matrix(*RTS_VIEW))
    R_front = np.array(view_matrix(*FRONT_VIEW))

    def st(lv):
        return None if anchor is None else (lv, anchor)

    parts = set(a.parts.split(",")) if not a.quick else {"rts", "growth"}
    panels = []
    if "rts" in parts:
        big = view(cells[top], R_rts, 11, looks_far, statue=st(top))
        panels.append(("L%d at the RTS camera (11 px/block)" % top, big))
        print("rts %.1fs" % (time.time() - t0))
    if "front" in parts:
        front = view(cells[top], R_front, 7, looks_far, statue=st(top))
        panels.append(("L%d from a low front camera (7 px/block)" % top, front))
    if "far" in parts:
        far = view(cells[top], R_rts, 3.5, looks_far, statue=st(top))
        panels.append(("full zoom-out (3.5 px/block)", far))
        print("front+far %.1fs" % (time.time() - t0))
    if "dusk" in parts:
        dk = view(cells[top], R_rts, 7, looks_far, statue=st(top), dusk=True)
        panels.append(("L%d at dusk: light as detail (7 px/block)" % top, dk))
    growth = []
    if "growth" in parts:
        for lv in levels:
            growth.append(("L%d" % lv, view(cells[lv], R_rts, 6, looks_far, statue=st(lv))))
        print("growth %.1fs" % (time.time() - t0))
    crops = []
    if "crops" in parts:
        G = None if a.nbt else importlib.import_module(a.gen)
        spots = getattr(G, "FOCAL_POINTS", None) if G else None
        if spots:
            for spot in spots:
                label, centre = spot[0], spot[1]
                px = spot[2] if len(spot) > 2 else 26
                crops.append((label, view(cells[top], R_rts, px, looks_near, crop=(centre, None), size=640,
                                          statue=st(top))))
                if anchor is not None and "crown" in label:
                    crops.append((label.replace("L6", "L1") + " at L1", view(cells[1], R_rts, px, looks_near,
                                                                              crop=(centre, None), size=640, statue=st(1))))
        print("crops %.1fs" % (time.time() - t0))
    f1, f2 = font(30), font(20)
    pad = 24
    W = max(sum(im.width for _, im in row) + pad * (len(row) + 1) for row in (panels, growth, crops) if row)
    H = 70
    sheet_rows = [r for r in (panels, growth, crops) if r]
    H += sum(max(im.height for _, im in row) + 30 + 20 for row in sheet_rows)
    sheet = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((pad, 18), title, fill=(235, 235, 235), font=f1)
    y = 70
    for row in sheet_rows:
        y += label_row(sheet, d, y, row, f2, pad) + 20
    sheet.save(a.out, optimize=True)
    print(a.out, sheet.size, "%.1fs" % (time.time() - t0))


if __name__ == "__main__":
    main()
