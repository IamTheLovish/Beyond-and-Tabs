#!/usr/bin/env python3
"""A generated unit as the RTS camera draws it - at real screen size, with and without its mipmaps - and how much it
shimmers when it moves. Judge a body's texture before a 13-minute build and an eyes run (the tool idea of the Tier 3.12
session, claude/now.md; the blocks' version is tools/gen_blocks.py rts_view()).

At the default RTS zoom a block is ~36 screen px (1080p), so one model pixel is ~2.3 px and one texel of our 8x atlas
~0.28 px: the GPU samples one texel per screen pixel. Without mipmaps (vanilla entities) that texel is a different one
every time the unit or the camera moves a fraction of a pixel - the texture "crawls". With them (612f4b9:
MipmappedTextures, 3 levels, NEAREST_MIPMAP_LINEAR) the sampled level holds the average of the texels under the
pixel. This draws the unit three ways at the same size - the ideal (8 x 8 samples per pixel, box-filtered), the
game without mips, the game with mips - over N sub-pixel positions of a slow walk, and prints:
  crawl    mean over pixels of the frame-to-frame spread of (game - ideal), luma: the shimmer the eye sees in motion;
  busy     luma std of the ideal over the body: how busy the paint itself reads at this size (cf. the blocks' rts_view).

    python3 tools/unit_rts_view.py design/models/sunforged_halberdier_v2.json --team tabard,tabard_dark \\
        [--ppb 36] [--frames 16] [--owner "#3a6fd1"] [--view rts] [--out halberdier_rts.png]
    python3 tools/unit_rts_view.py --all [--out-dir DIR]     # every body of tools/export_bodies.py, one line each

The sheet (4x, nearest): ideal | no mips | mips for the first frame, then the per-pixel shimmer maps (brighter =
more crawl) for no mips and mips. The crawl is reported over the whole body, inside faces (a pixel whose 4 neighbours
show the same face in every frame - where the TEXTURE is what moves) and on edges and seams (the silhouette and the
borders between boxes: geometry aliasing, the same with or without mips - the game has no MSAA). Sampling follows the game: a texel's (u, v) by Minecraft's box UV
(tools/game_preview.py faces_of), team mask in the owner's colour, glow added, the two-light entity shading; mips as
util/TextureMips builds them (2x2 average in linear light, weighted by alpha, at most 3 levels), the level from the
pixel's texel footprint (the larger UV derivative, as GL does), nearest texel in the two nearest levels, blended.
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept  # noqa: E402
import dsl_to_game as d2g  # noqa: E402
import game_preview as gp  # noqa: E402
from preview_model import view_matrix  # noqa: E402

MAX_LEVELS = 3          # util/TextureMips.MAX_LEVELS
SS = 8                  # samples per pixel side for the ideal picture
GROUND = np.array(gp.GROUND, dtype=np.float32)
LUMA = np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)


def to_linear(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def to_srgb(c):
    return np.where(c <= 0.0031308, 12.92 * c, 1.055 * np.power(np.clip(c, 0, None), 1 / 2.4) - 0.055)


def mip_chain(rgba):
    """Level 0 plus up to MAX_LEVELS halvings exactly as util/TextureMips.halve: linear-light 2x2 mean weighted by
    alpha, alpha the plain mean, a block with no alpha stays empty."""
    levels = [rgba]
    a = rgba
    while len(levels) <= MAX_LEVELS and a.shape[0] % 2 == 0 and a.shape[1] % 2 == 0 and a.shape[0] > 1:
        al = a[..., 3]
        lin = to_linear(a[..., :3]) * al[..., None]
        s = lambda x: x[0::2, 0::2] + x[1::2, 0::2] + x[0::2, 1::2] + x[1::2, 1::2]   # noqa: E731
        asum = s(al)
        col = np.where(asum[..., None] > 0, s(lin) / np.maximum(asum, 1e-9)[..., None], 0)
        a = np.concatenate([to_srgb(col), (asum / 4.0)[..., None]], -1).astype(np.float32)
        # 8-bit like the GPU texture
        a = np.round(np.clip(a, 0, 1) * 255) / 255.0
        levels.append(a)
    return levels


def project(faces, view):
    R = np.array(view_matrix(view[0], view[1]))
    flip = np.diag([1.0, -1.0, 1.0])
    out = []
    for verts, uvs in faces:
        P = [R @ (flip @ v + np.array([0, 24.0, 0])) for v in verts]
        n = np.cross(P[1] - P[0], P[2] - P[0])
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n = -n / nl
        if n[2] > 0:
            continue
        nw = np.linalg.inv(R) @ n
        light = min(1.0, 0.6 * (max(0.0, nw @ gp.L0) + max(0.0, nw @ gp.L1)) + 0.4)
        out.append((P, uvs, light))
    return out


def draw(proj, tex, density, sc, ox, oy, size, owner, mode):
    """One frame. mode: 'nearest' (level 0 only, the vanilla entity), 'mips' (NEAREST_MIPMAP_LINEAR over the chain).
    tex: {'base': [levels], 'team': [levels], 'glow': [levels]} (float RGBA, 0..1). Returns (rgb, body mask)."""
    img = np.empty((size, size, 3), dtype=np.float32)
    img[:] = GROUND
    zbuf = np.full((size, size), np.inf, dtype=np.float32)
    fid = np.full((size, size), -1, dtype=np.int32)          # which face each pixel shows (edges vs interiors)
    nlev = len(tex["base"])
    for fi, (P, uvs, light) in enumerate(proj):
        S = [np.array([ox + p[0] * sc, oy - p[1] * sc, p[2]]) for p in P]
        e1, e2 = S[0][:2] - S[1][:2], S[2][:2] - S[1][:2]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-12:
            continue
        xs, ys = [s[0] for s in S], [s[1] for s in S]
        x0, x1 = max(0, int(math.floor(min(xs)))), min(size - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(size - 1, int(math.ceil(max(ys))))
        if x1 < x0 or y1 < y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        qx, qy = gx - S[1][0], gy - S[1][1]
        a = (qx * e2[1] - qy * e2[0]) / det
        b = (e1[0] * qy - e1[1] * qx) / det
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        du_a, du_b = uvs[0][0] - uvs[1][0], uvs[2][0] - uvs[1][0]
        dv_a, dv_b = uvs[0][1] - uvs[1][1], uvs[2][1] - uvs[1][1]
        z = S[1][2] + a * (S[0][2] - S[1][2]) + b * (S[2][2] - S[1][2])
        u = (uvs[1][0] + a * du_a + b * du_b) * density          # texel coordinates at level 0
        v = (uvs[1][1] + a * dv_a + b * dv_b) * density
        if mode == "mips":
            # the texel footprint of one screen pixel (an affine face: constant derivatives)
            dadx, dady, dbdx, dbdy = e2[1] / det, -e2[0] / det, -e1[1] / det, e1[0] / det
            dudx, dudy = (du_a * dadx + du_b * dbdx) * density, (du_a * dady + du_b * dbdy) * density
            dvdx, dvdy = (dv_a * dadx + dv_b * dbdx) * density, (dv_a * dady + dv_b * dbdy) * density
            rho = max(math.hypot(dudx, dvdx), math.hypot(dudy, dvdy))
            lod = min(max(math.log2(rho), 0.0), nlev - 1) if rho > 0 else 0.0
        else:
            lod = 0.0
        l0, l1 = int(math.floor(lod)), min(int(math.floor(lod)) + 1, nlev - 1)
        f = lod - l0

        def sample(name, lv):
            t = tex[name][lv]
            th, tw = t.shape[:2]
            k = 2 ** lv
            ti = np.clip(np.floor(u / k).astype(int), 0, tw - 1)
            tj = np.clip(np.floor(v / k).astype(int), 0, th - 1)
            return t[tj, ti]

        def blended(name):
            s0 = sample(name, l0)
            return s0 if f <= 0 or l1 == l0 else s0 * (1 - f) + sample(name, l1) * f

        base = blended("base")
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        ok = inside & (base[..., 3] > 0.1) & (z < sub)      # the cutout's alpha test
        if not ok.any():
            continue
        col = base[..., :3].copy()
        tm = blended("team")
        ta = tm[..., 3:4]
        col = col * (1 - ta) + tm[..., :3] * owner * ta
        col = col * light
        gl = blended("glow")
        col = np.clip(col + gl[..., :3] * gl[..., 3:4] * 0.8, 0, 1)
        region = img[y0:y1 + 1, x0:x1 + 1]
        region[ok] = col[ok]
        sub[ok] = z[ok]
        fid[y0:y1 + 1, x0:x1 + 1][ok] = fi
    return img, fid


def measure(model_path, team, owner_hex, ppb, frames, view_name, out=None):
    m = render_concept.load_model(model_path)
    m["_path"] = os.path.abspath(model_path)
    _java, imgs, info = d2g.build(m, "Preview", [t for t in team.split(",") if t], "preview", d2g.DENSITY)
    owner = np.array([int(owner_hex.lstrip("#")[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], dtype=np.float32)
    pose_mc = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}
    faces = gp.faces_of(info["root"], pose_mc, True, False)
    proj = project(faces, gp.VIEWS[view_name])
    tex = {k: mip_chain(np.asarray(imgs[k].convert("RGBA"), dtype=np.float32) / 255.0) for k in ("base", "team", "glow")}
    sc = ppb / 16.0                                          # screen px per model pixel
    xs = [p[0] for P, _, _ in proj for p in P]
    ys = [p[1] for P, _, _ in proj for p in P]
    size = int(math.ceil(max(max(xs) - min(xs), max(ys) - min(ys)) * sc)) + 8
    ox0 = (size - (max(xs) - min(xs)) * sc) / 2 - min(xs) * sc
    oy0 = size - (size - (max(ys) - min(ys)) * sc) / 2 + min(ys) * sc
    tex0 = {k: v[:1] for k, v in tex.items()}
    ideal, game, gmip, inner, body, fid0 = [], [], [], None, None, None
    for i in range(frames):
        # a slow walk: a little over one pixel along a diagonal, in sub-pixel steps
        dx, dy = 1.25 * i / frames, 0.6 * i / frames
        big, _ = draw(proj, tex0, d2g.DENSITY, sc * SS, (ox0 + dx) * SS, (oy0 + dy) * SS, size * SS, owner, "nearest")
        # box filter in linear light, like the mips (sRGB means would darken every edge)
        ideal.append(to_srgb(to_linear(big).reshape(size, SS, size, SS, 3).mean(axis=(1, 3))).astype(np.float32))
        g, fid = draw(proj, tex, d2g.DENSITY, sc, ox0 + dx, oy0 + dy, size, owner, "nearest")
        gm, _ = draw(proj, tex, d2g.DENSITY, sc, ox0 + dx, oy0 + dy, size, owner, "mips")
        game.append(g)
        gmip.append(gm)
        # a face interior pixel: its 4 neighbours show the same face (in every frame, and the same face throughout)
        pad = np.pad(fid, 1, constant_values=-2)
        same = (fid >= 0) & (pad[:-2, 1:-1] == fid) & (pad[2:, 1:-1] == fid) & (pad[1:-1, :-2] == fid) & \
            (pad[1:-1, 2:] == fid)
        if inner is None:
            inner, body, fid0 = same, fid >= 0, fid
        else:
            inner &= same & (fid == fid0)
            body &= fid >= 0
    ideal, game, gmip = (np.stack(x) @ LUMA for x in (ideal, game, gmip))
    res = {}
    for name, frames_l in (("no mips", game), ("mips", gmip)):
        err = frames_l - ideal
        # inside a face only: the silhouette and the seams between faces alias the same with or without mips
        # (no MSAA in game), so the texture's own crawl is judged where one face covers the 3x3 neighbourhood
        sd = err.std(axis=0)
        res[name] = {"alias": float(np.abs(err)[:, inner].mean()) if inner.any() else 0.0,
                     "shimmer": float(sd[inner].mean()) if inner.any() else 0.0,
                     "edges": float(sd[body & ~inner].mean()), "all": float(sd[body].mean()), "map": sd}
    res["busy"] = float(ideal[0][body].std())
    res["inner"] = float(inner.sum()) / max(1, int(body.sum()))
    res["px_tall"] = size
    if out:
        k = 4
        cells = []
        for arr in (ideal[0], game[0], gmip[0]):
            cells.append(Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), "L").convert("RGB"))
        for name in ("no mips", "mips"):
            sm = np.clip(res[name]["map"] / 0.15, 0, 1)
            cells.append(Image.fromarray((sm * 255).astype(np.uint8), "L").convert("RGB"))
        # the colour frames too (first frame): ideal / no mips / mips
        sheet = Image.new("RGB", (len(cells) * (size * k + 6) + 6, size * k + 24), (245, 243, 239))
        d = ImageDraw.Draw(sheet)
        labels = ("ideal (luma)", "no mips", "mips", "shimmer: no mips", "shimmer: mips")
        for i, (c, lab) in enumerate(zip(cells, labels)):
            x = 6 + i * (size * k + 6)
            sheet.paste(c.resize((size * k, size * k), Image.NEAREST), (x, 6))
            d.text((x + 2, size * k + 9), lab, fill=(40, 40, 40))
        sheet.save(out)
    return res


def line(name, r):
    nm, mp = r["no mips"], r["mips"]
    return ("%-24s %3d px  busy %.3f | crawl over the whole body %.3f -> %.3f with mips | inside faces (%2.0f%% of "
            "the body) %.3f -> %.3f (%+.0f%%) | on edges and seams %.3f -> %.3f\n") % (
        name, r["px_tall"], r["busy"], nm["all"], mp["all"], 100 * r["inner"], nm["shimmer"], mp["shimmer"],
        100.0 * (mp["shimmer"] / max(nm["shimmer"], 1e-9) - 1), nm["edges"], mp["edges"])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model", nargs="?")
    ap.add_argument("--team", default="")
    ap.add_argument("--owner", default="#3a6fd1")
    ap.add_argument("--ppb", type=float, default=36.0, help="screen px per block (36 = the RTS default at 1080p)")
    ap.add_argument("--frames", type=int, default=16)
    ap.add_argument("--view", default="rts", choices=sorted(gp.VIEWS))
    ap.add_argument("--out")
    ap.add_argument("--all", action="store_true", help="every body in tools/export_bodies.py")
    ap.add_argument("--out-dir")
    a = ap.parse_args()
    if a.all:
        import export_bodies
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        for model, _cls, texture, team in export_bodies.BODIES:
            out = os.path.join(a.out_dir, texture + "_rts.png") if a.out_dir else None
            if out:
                os.makedirs(a.out_dir, exist_ok=True)
            r = measure(os.path.join(root, model), team, a.owner, a.ppb, a.frames, a.view, out)
            print(line(texture, r))
        return
    if not a.model:
        ap.error("a model, or --all")
    r = measure(a.model, a.team, a.owner, a.ppb, a.frames, a.view, a.out)
    print(line(os.path.basename(a.model), r))
    if a.out:
        print(a.out)


if __name__ == "__main__":
    main()
