#!/usr/bin/env python3
"""Draws a smooth mesh (the sculpts of tools/sdf_mesh.py, or any (V, N, UV, Q) set) offline with Gouraud shading -
per-vertex light from the vertex normals, interpolated across each face - the way the game's entity shader lights a
mesh body's vertices. A software z-buffer in numpy (no GL in the sandboxes), so a sculpt is judged before CI sees it.

    import mesh_preview as MP
    img = MP.render(groups, view=(yaw, tilt), size=600, tex=None, centre=None, zoom=1.0)

groups: [(name, V, N, UV, Q, material)] in DSL space (px, y up, the figure facing -z); view: (yaw, tilt) degrees as
tools/preview_model.view_matrix takes them; tex: the atlas (H, W, 3-4) float array (UVs 0..1, v down) or None for the
material's flat colour.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_model import view_matrix  # noqa: E402

SKY = np.array([0.80, 0.85, 0.90], dtype=np.float32)
# two sun lights, as Minecraft's entity shader (Light0 / Light1 directions) - from the upper front-left and front-right
L0 = np.array([0.2, 1.0, -0.7])
L0 = L0 / np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7])
L1 = L1 / np.linalg.norm(L1)


def mc_light(N):
    """Minecraft's entity light per vertex: 0.6 * (max(0, n.l0) + max(0, n.l1)) + 0.4, like the shader's minecraft_mix_light."""
    return np.minimum(1.0, 0.6 * (np.maximum(0.0, N @ L0) + np.maximum(0.0, N @ L1)) + 0.4)


class ZRaster:
    def __init__(self, w, h, bg):
        self.w, self.h = w, h
        self.img = np.zeros((h, w, 3), dtype=np.float32)
        self.img[:] = bg
        self.z = np.full((h, w), np.inf, dtype=np.float32)

    def triangles(self, S, C):
        """S: (m, 3, 3) screen-space triangles (x right, y down, z depth); C: (m, 3, 3) colours per corner."""
        for k in range(len(S)):
            s, c = S[k], C[k]
            x0, x1 = int(math.floor(s[:, 0].min())), int(math.ceil(s[:, 0].max()))
            y0, y1 = int(math.floor(s[:, 1].min())), int(math.ceil(s[:, 1].max()))
            x0, y0 = max(0, x0), max(0, y0)
            x1, y1 = min(self.w - 1, x1), min(self.h - 1, y1)
            if x1 < x0 or y1 < y0:
                continue
            e1x, e1y = s[1, 0] - s[0, 0], s[1, 1] - s[0, 1]
            e2x, e2y = s[2, 0] - s[0, 0], s[2, 1] - s[0, 1]
            det = e1x * e2y - e1y * e2x
            if abs(det) < 1e-9:
                continue
            gx = np.arange(x0, x1 + 1, dtype=np.float32)[None, :] + 0.5 - s[0, 0]
            gy = np.arange(y0, y1 + 1, dtype=np.float32)[:, None] + 0.5 - s[0, 1]
            a = (gx * e2y - gy * e2x) / det
            b = (e1x * gy - e1y * gx) / det
            inside = (a >= -1e-4) & (b >= -1e-4) & (a + b <= 1.0001)
            if not inside.any():
                continue
            z = s[0, 2] + a * (s[1, 2] - s[0, 2]) + b * (s[2, 2] - s[0, 2])
            sub = self.z[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (z < sub)
            if not ok.any():
                continue
            col = c[0][None, None, :] + a[..., None] * (c[1] - c[0])[None, None, :] + b[..., None] * (c[2] - c[0])[None, None, :]
            self.img[y0:y1 + 1, x0:x1 + 1][ok] = col[ok]
            sub[ok] = z[ok]

    def image(self):
        return Image.fromarray((np.clip(self.img, 0, 1) * 255).astype(np.uint8))


def sample_tex(tex, UV):
    th, tw = tex.shape[:2]
    u = np.clip((UV[:, 0] * tw).astype(int), 0, tw - 1)
    v = np.clip((UV[:, 1] * th).astype(int), 0, th - 1)
    return tex[v, u, :3]


def render(groups, view=(-30.0, 15.0), size=600, tex=None, centre=None, zoom=1.0, bg=SKY, colours=None,
           cull=True, margin=1.08, light=mc_light):
    """One picture of the groups from a (yaw, tilt) camera. centre (DSL px) and zoom crop to a detail."""
    R = np.array(view_matrix(*view))
    allp = np.concatenate([g[1] for g in groups if len(g[1])], axis=0)
    pv = allp @ R.T
    lo, hi = pv.min(axis=0), pv.max(axis=0)
    sc = size / (max(hi[0] - lo[0], hi[1] - lo[1]) * margin) * zoom
    if centre is None:
        cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    else:
        c = R @ np.asarray(centre, dtype=float)
        cx, cy = c[0], c[1]
    ox, oy = size / 2 - cx * sc, size / 2 + cy * sc
    r = ZRaster(size, size, bg)
    for g in groups:
        name, V, N, UV, Q = g[:5]
        mat = g[5] if len(g) > 5 else None
        if len(Q) == 0:
            continue
        Pv = V @ R.T
        Nv = N @ R.T
        lt = light(N)
        if tex is not None and UV is not None:
            base = sample_tex(tex, UV)
        else:
            col = np.asarray((colours or {}).get(mat, (0.86, 0.83, 0.78)), dtype=np.float32)
            base = np.broadcast_to(col, (len(V), 3))
        C = base * lt[:, None]
        Q = np.asarray(Q)
        tris = np.concatenate([Q[:, [0, 1, 2]], Q[:, [0, 2, 3]]], axis=0)
        tris = tris[(tris[:, 0] != tris[:, 1]) & (tris[:, 1] != tris[:, 2]) & (tris[:, 0] != tris[:, 2])]
        S = np.stack([ox + Pv[tris][..., 0] * sc, oy - Pv[tris][..., 1] * sc, Pv[tris][..., 2]], axis=-1)
        if cull:
            e1 = S[:, 1, :2] - S[:, 0, :2]
            e2 = S[:, 2, :2] - S[:, 0, :2]
            # screen y is down: an outward (counter-clockwise in view space) face has a negative screen cross here
            keep = (e1[:, 0] * e2[:, 1] - e1[:, 1] * e2[:, 0]) < 0
            # keep faces whose average view normal faces the camera too (thin sheets seen edge-on)
            nv = Nv[tris].mean(axis=1)
            keep |= nv[:, 2] < -0.02
            S, tris = S[keep], tris[keep]
        # draw the far faces first for the odd overlap at a silhouette
        order = np.argsort(-S[:, :, 2].mean(axis=1))
        r.triangles(S[order], C[tris[order]])
    return r.image()


def mirror_view(view):
    return view


if __name__ == "__main__":
    import sdf_mesh as S
    f = lambda P: S.smin(S.sphere(P, (0, 0, 0), 4), S.capsule(P, (0, 0, 0), (0, 8, 0), 1.5), 1.0)
    V, N, Q = S.mesh(f, (-6, -6, -6), (6, 12, 6), 0.25)
    im = render([("test", V, N, None, Q, "marble")], view=(-30, 15), size=300)
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mesh_preview_test.png"
    im.save(out)
    print(out, len(V), len(Q))
