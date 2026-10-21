#!/usr/bin/env python3
"""Draws a smooth mesh body offline - a sculpt from tools/sculpt_angel.py, or any .bmesh / OBJ through
tools/mesh_import.py - so a sculpt is judged before a CI run. A vectorised numpy rasteriser (no GL in the sandboxes):
every triangle's covered pixels at once, the nearest one kept per pixel, then per-pixel shading.

Two looks, because the testers play both ways:
  - "game": exactly what vanilla's entity shader does with a mesh body - per-VERTEX light from the vertex normals
    (minecraft_mix_light: two world-fixed lights, 0.4 ambient), Gouraud-interpolated, times the vertex colour (the
    baked marble and occlusion, tools/sculpt_angel.py) times the atlas texel; the lightmap at full day.
  - "shader": what a shader pack (Oculus + Complementary) adds - per-PIXEL normals, a sun with real cast shadows (a
    shadow map), sky light times the baked occlusion, a soft specular by the material's smoothness.

    import mesh_preview as MP
    img = MP.render(parts, view=(yaw, tilt), size=600, look="game")            # PIL image
    parts: [Part(V, N, C, Q, smooth)] in DSL space (px, y up, the figure facing -z); C: (n, 3) vertex colours 0..1

The camera is orthographic, as the RTS camera (preview_model.view_matrix: turn by yaw, pitch down by tilt).
"""
import math
import os
import sys
from collections import namedtuple

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_model import view_matrix  # noqa: E402

SKY = np.array([0.80, 0.85, 0.90], dtype=np.float32)
# vanilla's entity lights (Lighting.DIFFUSE_LIGHT_0 / _1), world-fixed
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
# the shader look's sun: from the upper south-west quarter where the RTS camera stands, a little to its left
SUN = np.array([-0.62, 0.72, -0.32])
SUN = SUN / np.linalg.norm(SUN)

Part = namedtuple("Part", "V N C Q smooth")


def mc_light(N):
    """minecraft_mix_light: 0.6 * (max(0, n.l0) + max(0, n.l1)) + 0.4, capped at 1 - per vertex."""
    return np.minimum(1.0, 0.6 * (np.maximum(0.0, N @ L0) + np.maximum(0.0, N @ L1)) + 0.4)


# ------------------------------------------------------------------ rasterising ---------------------------------
def triangles(Q):
    """Quads (a b c d; a triangle stored a b c c) as triangles a b c + a c d, degenerate ones dropped."""
    Q = np.asarray(Q)
    T = np.concatenate([Q[:, [0, 1, 2]], Q[:, [0, 2, 3]]], axis=0)
    return T[(T[:, 0] != T[:, 1]) & (T[:, 1] != T[:, 2]) & (T[:, 0] != T[:, 2])]


def raster(S, T, W, H, max_candidates=6_000_000):
    """S: (n, 3) screen points (x right, y down, in pixels; z depth, smaller nearer); T: (m, 3) triangles.
    Returns (tri (H, W) int, -1 where empty; bary (H, W, 3) weights of the triangle's corners; depth (H, W))."""
    A, B, C = S[T[:, 0]], S[T[:, 1]], S[T[:, 2]]
    x0 = np.ceil(np.minimum(np.minimum(A[:, 0], B[:, 0]), C[:, 0]) - 0.5).astype(np.int64)
    x1 = np.floor(np.maximum(np.maximum(A[:, 0], B[:, 0]), C[:, 0]) - 0.5).astype(np.int64)
    y0 = np.ceil(np.minimum(np.minimum(A[:, 1], B[:, 1]), C[:, 1]) - 0.5).astype(np.int64)
    y1 = np.floor(np.maximum(np.maximum(A[:, 1], B[:, 1]), C[:, 1]) - 0.5).astype(np.int64)
    x0, y0 = np.maximum(x0, 0), np.maximum(y0, 0)
    x1, y1 = np.minimum(x1, W - 1), np.minimum(y1, H - 1)
    det = (B[:, 0] - A[:, 0]) * (C[:, 1] - A[:, 1]) - (C[:, 0] - A[:, 0]) * (B[:, 1] - A[:, 1])
    live = (x1 >= x0) & (y1 >= y0) & (np.abs(det) > 1e-12)
    ids = np.nonzero(live)[0]
    ext = np.maximum(x1 - x0 + 1, y1 - y0 + 1)[ids]
    pix_all, z_all, t_all, a_all, b_all = [], [], [], [], []
    lo = 0
    for Bsz in (1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 2048, 4096):
        sel = ids[(ext > lo) & (ext <= Bsz)]
        lo = Bsz
        if not len(sel):
            continue
        oy, ox = np.mgrid[0:Bsz, 0:Bsz]
        ox, oy = ox.ravel(), oy.ravel()
        per = max(1, max_candidates // (Bsz * Bsz))
        for s in range(0, len(sel), per):
            t = sel[s:s + per]
            px = x0[t][:, None] + ox[None, :]
            py = y0[t][:, None] + oy[None, :]
            ok = (px <= x1[t][:, None]) & (py <= y1[t][:, None])
            fx, fy = px + 0.5 - A[t, 0][:, None], py + 0.5 - A[t, 1][:, None]
            d = det[t][:, None]
            a = (fx * (C[t, 1] - A[t, 1])[:, None] - fy * (C[t, 0] - A[t, 0])[:, None]) / d
            b = ((B[t, 0] - A[t, 0])[:, None] * fy - (B[t, 1] - A[t, 1])[:, None] * fx) / d
            ok &= (a >= -1e-6) & (b >= -1e-6) & (a + b <= 1 + 1e-6)
            if not ok.any():
                continue
            z = A[t, 2][:, None] + a * (B[t, 2] - A[t, 2])[:, None] + b * (C[t, 2] - A[t, 2])[:, None]
            tt = np.broadcast_to(t[:, None], ok.shape)
            pix_all.append((py * W + px)[ok])
            z_all.append(z[ok])
            t_all.append(tt[ok])
            a_all.append(a[ok])
            b_all.append(b[ok])
    tri = np.full(H * W, -1, dtype=np.int64)
    bary = np.zeros((H * W, 3), dtype=np.float64)
    depth = np.full(H * W, np.inf)
    if pix_all:
        pix, z = np.concatenate(pix_all), np.concatenate(z_all)
        tt, aa, bb = np.concatenate(t_all), np.concatenate(a_all), np.concatenate(b_all)
        order = np.lexsort((z, pix))
        ps = pix[order]
        first = np.ones(len(ps), dtype=bool)
        first[1:] = ps[1:] != ps[:-1]
        k = order[first]
        tri[pix[k]] = tt[k]
        bary[pix[k], 0] = 1.0 - aa[k] - bb[k]
        bary[pix[k], 1] = aa[k]
        bary[pix[k], 2] = bb[k]
        depth[pix[k]] = z[k]
    return tri.reshape(H, W), bary.reshape(H, W, 3), depth.reshape(H, W)


def interp(attr, T, tri, bary):
    """A per-vertex attribute (n, k) at every covered pixel (H, W, k); zeros elsewhere."""
    out = np.zeros(tri.shape + (attr.shape[1],))
    m = tri >= 0
    corners = T[tri[m]]                                   # (p, 3)
    w = bary[m]                                           # (p, 3)
    out[m] = (attr[corners] * w[..., None]).sum(axis=1)
    return out


# ------------------------------------------------------------------ the scene ------------------------------------
def merge(parts):
    """All parts as one (V, N, C, T, smooth per vertex)."""
    Vs, Ns, Cs, Ts, Ss = [], [], [], [], []
    base = 0
    for p in parts:
        if len(p.Q) == 0:
            continue
        Vs.append(np.asarray(p.V, dtype=np.float64))
        Ns.append(np.asarray(p.N, dtype=np.float64))
        Cs.append(np.asarray(p.C, dtype=np.float64)[:, :3])
        sm = p.smooth if np.ndim(p.smooth) else np.full(len(p.V), float(p.smooth))
        Ss.append(np.asarray(sm, dtype=np.float64))
        Ts.append(triangles(p.Q) + base)
        base += len(p.V)
    return np.concatenate(Vs), np.concatenate(Ns), np.concatenate(Cs), np.concatenate(Ts), np.concatenate(Ss)


def frame_for(V, R, size, zoom=1.0, centre=None, margin=1.06, aspect=1.0):
    """The screen transform (scale, ox, oy, W, H) that fits V (or zooms on centre)."""
    pv = V @ R.T
    lo, hi = pv.min(axis=0), pv.max(axis=0)
    W = size
    H = int(round(size * aspect))
    sc = min(W / ((hi[0] - lo[0]) * margin), H / ((hi[1] - lo[1]) * margin)) * zoom
    if centre is None:
        cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    else:
        c = R @ np.asarray(centre, dtype=float)
        cx, cy = c[0], c[1]
    return sc, W / 2 - cx * sc, H / 2 + cy * sc, W, H


def to_screen(V, R, sc, ox, oy):
    pv = V @ R.T
    return np.stack([ox + pv[:, 0] * sc, oy - pv[:, 1] * sc, pv[:, 2]], axis=1)


def shadow_lookup(V, T, Pw, light_dir, res=1400):
    """1 where a sun ray reaches the world points Pw (p, 3), 0 in shadow (a shadow map, 3x3 percentage-closer)."""
    up = np.array([0.0, 1.0, 0.0]) if abs(light_dir[1]) < 0.95 else np.array([1.0, 0.0, 0.0])
    zax = -light_dir                                     # the light looks along -light_dir
    xax = np.cross(up, zax)
    xax /= np.linalg.norm(xax)
    yax = np.cross(zax, xax)
    R = np.stack([xax, yax, zax])
    pv = V @ R.T
    lo, hi = pv.min(axis=0), pv.max(axis=0)
    sc = (res - 4) / max(hi[0] - lo[0], hi[1] - lo[1])
    ox, oy = 2 - lo[0] * sc, 2 + hi[1] * sc
    S = np.stack([ox + pv[:, 0] * sc, oy - pv[:, 1] * sc, pv[:, 2]], axis=1)
    _, _, depth = raster(S, T, res, res)
    q = Pw @ R.T
    sx, sy = ox + q[:, 0] * sc, oy - q[:, 1] * sc
    bias = 2.2 / sc + 0.08
    lit = np.zeros(len(Pw))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            ix = np.clip((sx + dx).astype(int), 0, res - 1)
            iy = np.clip((sy + dy).astype(int), 0, res - 1)
            lit += (q[:, 2] <= depth[iy, ix] + bias)
    return lit / 9.0


def render(parts, view=(-30.0, 12.0), size=600, look="game", zoom=1.0, centre=None, bg=SKY, ss=2, aspect=1.0,
           fit=None, sun=SUN, frame=None):
    """One picture of the parts. look: "game" (vanilla's per-vertex entity light) or "shader" (per-pixel, sun
    shadows). fit: points to frame instead of the parts (to keep several pictures at one scale). frame: an explicit
    (scale, ox, oy, W, H) at the final size. ss: supersampling (2 = 4 samples a pixel)."""
    V, N, C, T, SM = merge(parts)
    R = np.array(view_matrix(*view))
    if frame is None:
        sc, ox, oy, W, H = frame_for(V if fit is None else np.asarray(fit), R, size, zoom, centre, aspect=aspect)
    else:
        sc, ox, oy, W, H = frame
    sc, ox, oy, W, H = sc * ss, ox * ss, oy * ss, W * ss, H * ss
    S = to_screen(V, R, sc, ox, oy)
    tri, bary, _ = raster(S, T, W, H)
    m = tri >= 0
    img = np.zeros((H, W, 3))
    img[:] = bg
    if look == "game":
        lt = mc_light(N)
        col = interp(C * lt[:, None], T, tri, bary)
        img[m] = col[m]
    else:
        n = interp(N, T, tri, bary)[m]
        n /= np.maximum(np.linalg.norm(n, axis=1), 1e-9)[:, None]
        alb = interp(C, T, tri, bary)[m]
        sm = interp(SM[:, None], T, tri, bary)[m][:, 0]
        Pw = interp(V, T, tri, bary)[m]
        # the baked colour carries the occlusion: the albedo's own brightness is the cavity term here
        sh = shadow_lookup(V, T, Pw + n * 0.05, sun)
        ndl = np.maximum(0.0, n @ sun)
        sky = 0.5 + 0.5 * n[:, 1]
        vdir = -R[2]                                     # toward the viewer, world space
        hvec = sun + vdir
        hvec /= np.linalg.norm(hvec)
        spec = (np.maximum(0.0, n @ hvec) ** (8 + 120 * sm ** 2)) * (0.06 + 0.5 * sm ** 3) * sh
        sun_c = np.array([1.0, 0.95, 0.86])
        sky_c = np.array([0.42, 0.47, 0.56])
        lit = alb * (sun_c[None, :] * (ndl * sh)[:, None] * 1.05 + sky_c[None, :] * (0.55 + 0.45 * sky)[:, None])
        lit = lit + spec[:, None] * sun_c[None, :]
        img[m] = 1.0 - np.exp(-lit * 1.6)                  # a soft filmic shoulder
    out = img.reshape(H // ss, ss, W // ss, ss, 3).mean(axis=(1, 3))
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


if __name__ == "__main__":
    import sdf_mesh as SM_
    f = lambda P: SM_.smin(SM_.sphere(P, (0, 0, 0), 4), SM_.capsule(P, (0, 0, 0), (0, 8, 0), 1.5), 1.0)
    V, N, Q = SM_.mesh(f, (-6, -6, -6), (6, 12, 6), 0.25)
    C = np.full((len(V), 3), 0.9)
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mesh_preview_test.png"
    a = render([Part(V, N, C, Q, 0.6)], look="game", size=300)
    b = render([Part(V, N, C, Q, 0.6)], look="shader", size=300)
    sheet = Image.new("RGB", (600, 300))
    sheet.paste(a, (0, 0))
    sheet.paste(b, (300, 0))
    sheet.save(out)
    print(out, len(V), len(Q))
