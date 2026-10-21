#!/usr/bin/env python3
"""The White Angel capitol's statue, offline: the drawn marble angel (design/models/white_angel_statue.json through
tools/dsl_to_game.py's painted atlas, as tools/game_preview.py draws a unit) close up, and stood on the capitol's
plinth (tools/capitol_white_angel_v4.py STATUE_ANCHOR) at the RTS camera - at the scale BuildingParts.STATUES gives it
and with its wings folded as BuildingParts.statueWingFold folds them at that level - so the statue is judged where it
stands before a CI run. The capitol is drawn in flat block colours on the blocks' shapes (preview_structure's palette
and shape_for); the statue is drawn textured with Minecraft's two entity lights.

    python3 tools/angel_statue_preview.py --out design/buildings/angel_statue_v1.png

The RTS camera looks north-east from the south-west (the west and south faces and the roofs; south-east to its
right), pitched down 50 degrees.
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept                  # noqa: E402
import dsl_to_game as d2g              # noqa: E402
import game_preview as GPV             # noqa: E402
import preview_structure as PS         # noqa: E402
import capitol_white_angel_v4 as CAP   # noqa: E402
from preview_model import view_matrix  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
MODEL = os.path.join(ROOT, "design", "models", "white_angel_statue.json")
HEIGHT_BLOCKS = 16.0         # BuildingParts.STATUES: soles to wing tips
FOLD_MAX = 34.0              # BuildingParts.WING_FOLD_MAX
SKY = np.array([0.80, 0.85, 0.90], dtype=np.float32)
BG = (24, 24, 28)


def wing_fold(level):
    """BuildingParts.statueWingFold settled at a level, the breath at its zero (degrees)."""
    lv = max(1, min(6, level))
    return (1.0 - (0.5 + 0.1 * (lv - 1))) * FOLD_MAX + 1.2


class Raster:
    """A z-buffered rasteriser of quads in view space (x right, y up, the viewer looking along +z)."""

    def __init__(self, w, h, scale, ox, oy, bg):
        self.w, self.h, self.sc, self.ox, self.oy = w, h, scale, ox, oy
        self.img = np.zeros((h, w, 3), dtype=np.float32)
        self.img[:] = bg
        self.z = np.full((h, w), np.inf, dtype=np.float32)

    def quad(self, P, colour=None, uvs=None, tex=None, light=1.0):
        S = [np.array([self.ox + p[0] * self.sc, self.oy - p[1] * self.sc, p[2]]) for p in P]
        e1, e2 = S[0][:2] - S[1][:2], S[2][:2] - S[1][:2]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            return
        xs, ys = [s[0] for s in S], [s[1] for s in S]
        x0, x1 = max(0, int(math.floor(min(xs)))), min(self.w - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(self.h - 1, int(math.ceil(max(ys))))
        if x1 < x0 or y1 < y0:
            return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        qx, qy = gx - S[1][0], gy - S[1][1]
        a = (qx * e2[1] - qy * e2[0]) / det
        b = (e1[0] * qy - e1[1] * qx) / det
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
            texel = base[np.clip((v * density).astype(int), 0, th - 1), np.clip((u * density).astype(int), 0, tw - 1)]
            ok = inside & (texel[..., 3] > 0.1) & (z < sub)
            col = texel[..., :3] * light
        else:
            ok = inside & (z < sub)
            col = np.broadcast_to(np.array(colour, dtype=np.float32) * light, ok.shape + (3,))
        if not ok.any():
            return
        self.img[y0:y1 + 1, x0:x1 + 1][ok] = col[ok]
        sub[ok] = z[ok]

    def image(self):
        return Image.fromarray((np.clip(self.img, 0, 1) * 255).astype(np.uint8))


# ------------------------------------------------------------------ the statue -----------------------------------
_MODEL = {}


def statue_model():
    if not _MODEL:
        m = render_concept.load_model(MODEL)
        m["_path"] = MODEL
        _java, imgs, info = d2g.build(m, "Preview", [], "preview", d2g.DENSITY)
        from preview_model import posed_cubes
        ys = [p[1] for pts, _ in posed_cubes(m) for p in pts]
        _MODEL.update(m=m, imgs=imgs, root=info["root"], model_px=max(ys) - min(ys))
    return _MODEL


def statue_faces(level, detail=True):
    """The statue's faces in DSL px (y up from the soles), its wings folded as at `level`, with Minecraft's light."""
    S = statue_model()
    m = S["m"]
    pose = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}
    f = wing_fold(level)
    for bone, s in (("wing_left", -1), ("wing_right", 1)):
        cur = pose.get(bone, (0.0, 0.0, 0.0))
        add = d2g.mc_rot([0, s * f, 0])
        pose[bone] = tuple(cur[i] + add[i] for i in range(3))
    out = []
    for verts, uvs in GPV.faces_of(S["root"], pose, detail, False):
        P = [np.array([v[0], 24.0 - v[1], v[2]]) for v in verts]
        n = np.cross(P[1] - P[0], P[2] - P[0])
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n = -n / nl                      # outward (Minecraft winds its polygons for y-down space)
        light = min(1.0, 0.6 * (max(0.0, n @ GPV.L0) + max(0.0, n @ GPV.L1)) + 0.4)
        out.append((P, n, uvs, light))
    return out


def draw_statue(r, R, faces, origin=(0.0, 0.0, 0.0), k=1.0):
    S = statue_model()
    base = np.asarray(S["imgs"]["base"], dtype=np.float32) / 255.0
    o = np.array(origin)
    for P, n, uvs, light in faces:
        if (R @ n)[2] > 0:
            continue
        r.quad([R @ (o + k * p) for p in P], uvs=uvs, tex=(base, d2g.DENSITY), light=light)


def statue_closeup(view, level=6, size=520):
    faces = statue_faces(level)
    R = np.array(view_matrix(*view))
    pts = np.array([R @ p for P, _, _, _ in faces for p in P])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    sc = size / (max(hi[0] - lo[0], hi[1] - lo[1]) * 1.08)
    r = Raster(size, size, sc, size / 2 - (lo[0] + hi[0]) / 2 * sc, size / 2 + (lo[1] + hi[1]) / 2 * sc, SKY)
    draw_statue(r, R, faces)
    return r.image()


# ------------------------------------------------------------------ the capitol ----------------------------------
WORLD_VIEW = (-45.0, 50.0)      # w' = (x, y, -z): the RTS camera looking north-east, pitched down 50


def capitol_quads(level):
    """The capitol's visible faces (top, west, south) as (corners in w', colour, light) - flat palette colours."""
    rig = CAP.level(level)
    cells = rig.b
    full = {p for p, (nm, pr) in cells.items() if PS.shape_for(nm, pr) == PS.FULL and not nm.endswith(
        ("glass", "glass_pane", "leaves", "leaves2", "bars", "vine", "lantern"))}
    out = []
    for (x, y, z), (name, props) in cells.items():
        if PS.invisible(name):
            continue
        col = np.array(PS.colour_for(name), dtype=np.float32) / 255.0
        for (x0, x1, y0, y1, z0, z1) in PS.shape_for(name, props):
            X0, X1, Y0, Y1, Z0, Z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
            if not (y1 >= 1 and (x, y + 1, z) in full):
                out.append(([(X0, Y1, -Z0), (X1, Y1, -Z0), (X1, Y1, -Z1), (X0, Y1, -Z1)], col, 1.0))
            if not (x0 <= 0 and (x - 1, y, z) in full):
                out.append(([(X0, Y0, -Z0), (X0, Y0, -Z1), (X0, Y1, -Z1), (X0, Y1, -Z0)], col, 0.62))
            if not (z1 >= 1 and (x, y, z + 1) in full):
                out.append(([(X0, Y0, -Z1), (X1, Y0, -Z1), (X1, Y1, -Z1), (X0, Y1, -Z1)], col, 0.8))
    return out


def capitol_view(level, px_per_block, crop=None, size=None):
    """The capitol at a level with the statue on its plinth, at the RTS camera. crop: (centre in w', half width in
    blocks) for a close crop round the crown."""
    R = np.array(view_matrix(*WORLD_VIEW))
    quads = capitol_quads(level)
    S = statue_model()
    k = HEIGHT_BLOCKS / S["model_px"]               # DSL px -> blocks (the game: height * 16 / modelPx, / 16 a px)
    ax, ay, az = CAP.STATUE_ANCHOR
    anchor = np.array([ax + 0.5, ay, -(az + 0.5)])
    faces = statue_faces(level, detail=px_per_block >= 20)
    if crop is None:
        pts = np.array([R @ np.array(p) for q, _, _ in quads for p in q] + [R @ (anchor + k * p) for P, _, _, _ in faces
                                                                             for p in P])
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        W, H = int((hi[0] - lo[0]) * px_per_block) + 24, int((hi[1] - lo[1]) * px_per_block) + 24
        cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    else:
        c = R @ np.array(crop[0])
        W = H = size
        cx, cy = c[0], c[1]
    r = Raster(W, H, px_per_block, W / 2 - cx * px_per_block, H / 2 + cy * px_per_block, SKY)
    for q, col, light in quads:
        P = [R @ np.array(p) for p in q]
        r.quad(P, colour=col, light=light)
    draw_statue(r, R, faces, anchor, k)
    return r.image()


# ------------------------------------------------------------------ the sheet ------------------------------------
def font(sz):
    for path in (os.path.join(ROOT, "tools", "fonts"),):
        if os.path.isdir(path):
            for f in sorted(os.listdir(path)):
                if f.endswith(".ttf"):
                    return ImageFont.truetype(os.path.join(path, f), sz)
    return ImageFont.load_default()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    S = statue_model()
    nc = sum(len(b.get("cubes", [])) for b in S["m"]["bones"])
    close = [("front", statue_closeup((0, 0))), ("three-quarter", statue_closeup((-40, 18))),
             ("side", statue_closeup((90, 0))), ("RTS angle (L1: wings half folded)", statue_closeup((-35, 52), 1))]
    ax, ay, az = CAP.STATUE_ANCHOR
    crown = ((ax + 0.5, ay + 6.0, -(az + 0.5)), None)
    l6 = capitol_view(6, 9)
    l6_crop = capitol_view(6, 26, crop=crown, size=640)
    l1_crop = capitol_view(1, 26, crop=crown, size=640)
    far = capitol_view(6, 3.5)
    f1, f2 = font(30), font(20)
    pad = 24
    row1_h = 520 + 40
    row2_h = max(l6.height, 640) + 40
    W = max(4 * (520 + pad) + pad, l6.width + 2 * (640 + pad) + far.width + 4 * pad)
    H = 70 + row1_h + row2_h + 20
    sheet = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((pad, 18), "White Angel capitol - the marble angel statue v1 (a drawn model, %d cubes, %.0f blocks to the wing "
                      "tips, on the dome's plinth)" % (nc, HEIGHT_BLOCKS), fill=(235, 235, 235), font=f1)
    y = 70
    for i, (label, im) in enumerate(close):
        x = pad + i * (520 + pad)
        d.text((x, y), label, fill=(230, 200, 120), font=f2)
        sheet.paste(im, (x, y + 30))
    y += row1_h
    x = pad
    for label, im in (("L6 at the RTS camera (9 px/block)", l6), ("the crown, L6 (26 px/block)", l6_crop),
                      ("the crown, L1 - wings half folded (they unfold level by level)", l1_crop),
                      ("full zoom-out (3.5 px/block)", far)):
        d.text((x, y), label, fill=(230, 200, 120), font=f2)
        sheet.paste(im, (x, y + 30))
        x += im.width + pad
    sheet.save(a.out, optimize=True)
    print(a.out, sheet.size)


if __name__ == "__main__":
    main()
