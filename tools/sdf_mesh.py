#!/usr/bin/env python3
"""Smooth sculpting without Blender: signed-distance fields (SDF) sampled on a grid and meshed with surface nets.

Why: lovish wants real sculpture (a Roman face, Bernini drapery and wings) and the mesh draw path (PR #6:
unit/modelling/mesh) draws any triangle/quad mesh with smooth normals - but no sculpt existed and the AI sandboxes
have no Blender. This module is the sculptor's clay: primitives (sphere, ellipsoid, capsule, round cone, box, torus,
a polygon sheet), smooth union / subtraction / intersection (the blends that make a form read as carved from one
block), domain deformations (bend, twist, mirror) and a meshing step that returns QUADS with normals from the field's
gradient - exactly what a .bmesh wants (tools/mesh_import.py writes one from the OBJ tools/sculpt_*.py emit).

Every primitive takes P, an (N, 3) float array of sample points, and returns (N,) signed distances (negative inside).
Meshing: surface nets (Gibson 1998) - one vertex per grid cell the surface crosses, at the mean of its edge crossings;
one quad per crossed grid edge, joining the four cells around it. No case tables, no slivers, already smooth; the
normals come from the SDF gradient, so shading is as smooth as the field. Resolution is the cell size h: a part is
meshed in its own box at its own h (the head finer than the robe), each becoming one .bmesh part.

    import sdf_mesh as S
    f = lambda P: S.smin(S.sphere(P, (0, 0, 0), 4), S.capsule(P, (0, 0, 0), (0, 8, 0), 1.5), 1.0)
    V, N, Q = S.mesh(f, lo=(-6, -6, -6), hi=(6, 12, 6), h=0.25)   # vertices, unit normals, quads (4 indices each)

Blender/OBJ/glTF sculpts go through tools/mesh_import.py too: this file is one way to make a sculpt, not the only one.
"""
import math

import numpy as np

F = np.float32


# ------------------------------------------------------------------ vectors ----------------------------------------
def v3(a):
    return np.asarray(a, dtype=np.float64).reshape(3)


def length(P):
    return np.sqrt((P * P).sum(axis=-1))


def normalize(v):
    v = v3(v)
    n = np.linalg.norm(v)
    return v / n if n > 0 else v


def rot_x(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def frame(P, centre, R=None):
    """Points moved into a local frame: q = R^T (P - centre). R: a 3x3 rotation (local axes as columns), or None."""
    q = P - v3(centre)
    return q if R is None else q @ np.asarray(R)


# ------------------------------------------------------------------ primitives -------------------------------------
def sphere(P, c, r):
    return length(P - v3(c)) - r


def ellipsoid(P, c, radii, R=None):
    """Inigo Quilez's bound-preserving ellipsoid distance (exact on the axes, a close bound elsewhere)."""
    q = frame(P, c, R)
    r = v3(radii)
    k0 = length(q / r)
    k1 = length(q / (r * r))
    return np.where(k1 > 1e-9, k0 * (k0 - 1.0) / np.maximum(k1, 1e-9), -r.min())


def capsule(P, a, b, r):
    a, b = v3(a), v3(b)
    ab = b - a
    t = np.clip(((P - a) @ ab) / max(1e-9, ab @ ab), 0.0, 1.0)
    return length(P - a - t[:, None] * ab) - r


def round_cone(P, a, b, ra, rb):
    """A capsule whose radius runs from ra at a to rb at b (a limb, a nose, a finger)."""
    a, b = v3(a), v3(b)
    ab = b - a
    l2 = max(1e-9, ab @ ab)
    t = np.clip(((P - a) @ ab) / l2, 0.0, 1.0)
    return length(P - a - t[:, None] * ab) - (ra + (rb - ra) * t)


def bezier(p0, p1, p2, p3, n=48):
    """A cubic Bezier sampled into n + 1 points (for a curved capsule chain or a wing's leading edge)."""
    t = np.linspace(0, 1, n + 1)[:, None]
    p0, p1, p2, p3 = (v3(p) for p in (p0, p1, p2, p3))
    return ((1 - t) ** 3) * p0 + 3 * ((1 - t) ** 2) * t * p1 + 3 * (1 - t) * (t ** 2) * p2 + (t ** 3) * p3


def tube(P, pts, radii):
    """A capsule chain along pts with a radius per point (a curved limb, a lock of hair, a cord)."""
    pts = np.asarray(pts, dtype=np.float64)
    radii = np.broadcast_to(np.asarray(radii, dtype=np.float64), (len(pts),))
    d = np.full(len(P), np.inf)
    for i in range(len(pts) - 1):
        d = np.minimum(d, round_cone(P, pts[i], pts[i + 1], radii[i], radii[i + 1]))
    return d


def box(P, c, half, R=None, r=0.0):
    q = np.abs(frame(P, c, R)) - (v3(half) - r)
    return length(np.maximum(q, 0.0)) + np.minimum(q.max(axis=-1), 0.0) - r


def torus(P, c, R_major, r_minor, R=None):
    """A ring in the local x-z plane (its axis local y)."""
    q = frame(P, c, R)
    s = np.sqrt(q[:, 0] ** 2 + q[:, 2] ** 2) - R_major
    return np.sqrt(s * s + q[:, 1] ** 2) - r_minor


def plane(P, point, normal):
    n = normalize(normal)
    return (P - v3(point)) @ n


def polygon2d(xy, poly):
    """Signed distance of 2D points (N, 2) to a closed polygon (M, 2): negative inside (even-odd winding)."""
    poly = np.asarray(poly, dtype=np.float64)
    d2 = np.full(len(xy), np.inf)
    inside = np.zeros(len(xy), dtype=bool)
    M = len(poly)
    for i in range(M):
        a, b = poly[i], poly[(i + 1) % M]
        e = b - a
        w = xy - a
        t = np.clip((w @ e) / max(1e-12, e @ e), 0.0, 1.0)
        dd = w - t[:, None] * e
        d2 = np.minimum(d2, (dd * dd).sum(axis=1))
        cond = ((xy[:, 1] >= a[1]) != (xy[:, 1] >= b[1]))
        xint = a[0] + (xy[:, 1] - a[1]) * e[0] / (e[1] if abs(e[1]) > 1e-12 else 1e-12)
        inside ^= cond & (xy[:, 0] < xint)
    return np.where(inside, -np.sqrt(d2), np.sqrt(d2))


def sheet(P, poly, thickness, c=(0, 0, 0), R=None):
    """A polygon in the local x-y plane extruded +-thickness/2 along local z (a wing, a flying drape); thickness may
    be an array per point (a tapering, ridged sheet)."""
    q = frame(P, c, R)
    d2 = polygon2d(q[:, :2], poly)
    dz = np.abs(q[:, 2]) - np.asarray(thickness) / 2.0
    return np.where((d2 > 0) & (dz > 0), np.sqrt(d2 * d2 + dz * dz), np.maximum(d2, dz))


# ------------------------------------------------------------------ operations -------------------------------------
def smin(a, b, k):
    """Smooth union (polynomial): the fillet a sculptor leaves where two masses meet; k the blend width."""
    if k <= 0:
        return np.minimum(a, b)
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b + (a - b) * h - k * h * (1.0 - h)


def smax(a, b, k):
    return -smin(-a, -b, k)


def union(*ds):
    out = ds[0]
    for d in ds[1:]:
        out = np.minimum(out, d)
    return out


def subtract(a, b, k=0.0):
    """a minus b (b carved out of a), with a soft edge of k."""
    return smax(a, -b, k)


def intersect(a, b, k=0.0):
    return smax(a, b, k)


def onion(d, t):
    """A shell of thickness t round a surface (hollow cloth)."""
    return np.abs(d) - t


def mirror_x(P):
    Q = P.copy()
    Q[:, 0] = np.abs(Q[:, 0])
    return Q


def bend_y(P, k, c=(0, 0, 0)):
    """Bends the domain about local z: a column along +y at x = 0 curves toward +x (k radians per unit of y)."""
    q = P - v3(c)
    a = k * q[:, 1]
    ca, sa = np.cos(a), np.sin(a)
    out = q.copy()
    out[:, 0] = ca * q[:, 0] - sa * q[:, 1]
    out[:, 1] = sa * q[:, 0] + ca * q[:, 1]
    return out + v3(c)


def twist_y(P, k, c=(0, 0, 0)):
    q = P - v3(c)
    a = k * q[:, 1]
    ca, sa = np.cos(a), np.sin(a)
    out = q.copy()
    out[:, 0] = ca * q[:, 0] - sa * q[:, 2]
    out[:, 2] = sa * q[:, 0] + ca * q[:, 2]
    return out + v3(c)


# ------------------------------------------------------------------ sampling and meshing ---------------------------
def sample(f, lo, hi, h, chunk=400000):
    """f sampled on the grid lo..hi at spacing h; returns (field, origin, h) with the box padded by one cell."""
    lo, hi = v3(lo) - h, v3(hi) + h
    n = np.maximum(2, np.ceil((hi - lo) / h).astype(int) + 1)
    xs = lo[0] + h * np.arange(n[0])
    ys = lo[1] + h * np.arange(n[1])
    zs = lo[2] + h * np.arange(n[2])
    X, Y, Z = np.meshgrid(xs, ys, zs, indexing="ij")
    P = np.stack([X.ravel(), Y.ravel(), Z.ravel()], axis=1)
    out = np.empty(len(P), dtype=np.float64)
    for s in range(0, len(P), chunk):
        out[s:s + chunk] = f(P[s:s + chunk])
    return out.reshape(tuple(n)), lo, h


def surface_nets(field, origin, h):
    """Quads of the zero level set. Returns (V (n, 3), Q (m, 4) int, G (n, 3) field gradient at each vertex)."""
    f = field
    nx, ny, nz = f.shape
    inside = f < 0
    cx, cy, cz = nx - 1, ny - 1, nz - 1
    vsum = np.zeros((cx, cy, cz, 3), dtype=np.float64)
    vcnt = np.zeros((cx, cy, cz), dtype=np.int32)

    def crossings(axis):
        if axis == 0:
            f0, f1 = f[:-1, :, :], f[1:, :, :]
        elif axis == 1:
            f0, f1 = f[:, :-1, :], f[:, 1:, :]
        else:
            f0, f1 = f[:, :, :-1], f[:, :, 1:]
        s = (f0 < 0) != (f1 < 0)
        denom = f0 - f1
        t = np.where(np.abs(denom) > 1e-12, f0 / np.where(np.abs(denom) > 1e-12, denom, 1.0), 0.5)
        t = np.clip(t, 0.0, 1.0)
        return s, t

    sx, tx = crossings(0)   # (cx, ny, nz)
    sy, ty = crossings(1)   # (nx, cy, nz)
    sz, tz = crossings(2)   # (nx, ny, cz)
    I = np.arange(nx)[:, None, None]
    J = np.arange(ny)[None, :, None]
    K = np.arange(nz)[None, None, :]
    px = np.stack(np.broadcast_arrays(I[:cx] + tx, J, K), axis=-1)          # (cx, ny, nz, 3)
    py = np.stack(np.broadcast_arrays(I, J[:, :cy] + ty, K), axis=-1)       # (nx, cy, nz, 3)
    pz = np.stack(np.broadcast_arrays(I, J, K[:, :, :cz] + tz), axis=-1)    # (nx, ny, cz, 3)
    for dj in (0, 1):
        for dk in (0, 1):
            m = sx[:, dj:dj + cy, dk:dk + cz]
            vsum += m[..., None] * px[:, dj:dj + cy, dk:dk + cz]
            vcnt += m
    for di in (0, 1):
        for dk in (0, 1):
            m = sy[di:di + cx, :, dk:dk + cz]
            vsum += m[..., None] * py[di:di + cx, :, dk:dk + cz]
            vcnt += m
    for di in (0, 1):
        for dj in (0, 1):
            m = sz[di:di + cx, dj:dj + cy, :]
            vsum += m[..., None] * pz[di:di + cx, dj:dj + cy, :]
            vcnt += m
    active = vcnt > 0
    vid = np.full((cx, cy, cz), -1, dtype=np.int64)
    n_active = int(active.sum())
    vid[active] = np.arange(n_active)
    V = vsum[active] / vcnt[active][:, None]
    quads = []
    # an x-edge at (i, j, k), j in 1..ny-2, k in 1..nz-2: the cells (i, j-1..j, k-1..k) around it
    ii, jj, kk = np.nonzero(sx[:, 1:ny - 1, 1:nz - 1])
    jj, kk = jj + 1, kk + 1
    if len(ii):
        c0, c1, c2, c3 = vid[ii, jj - 1, kk - 1], vid[ii, jj, kk - 1], vid[ii, jj, kk], vid[ii, jj - 1, kk]
        flip = inside[ii, jj, kk]       # the lower endpoint inside: the surface normal points +x
        quads.append(np.where(flip[:, None], np.stack([c0, c3, c2, c1], 1), np.stack([c0, c1, c2, c3], 1)))
    ii, jj, kk = np.nonzero(sy[1:nx - 1, :, 1:nz - 1])
    ii, kk = ii + 1, kk + 1
    if len(ii):
        c0, c1, c2, c3 = vid[ii - 1, jj, kk - 1], vid[ii, jj, kk - 1], vid[ii, jj, kk], vid[ii - 1, jj, kk]
        flip = inside[ii, jj, kk]
        quads.append(np.where(flip[:, None], np.stack([c0, c1, c2, c3], 1), np.stack([c0, c3, c2, c1], 1)))
    ii, jj, kk = np.nonzero(sz[1:nx - 1, 1:ny - 1, :])
    ii, jj = ii + 1, jj + 1
    if len(ii):
        c0, c1, c2, c3 = vid[ii - 1, jj - 1, kk], vid[ii, jj - 1, kk], vid[ii, jj, kk], vid[ii - 1, jj, kk]
        flip = inside[ii, jj, kk]
        quads.append(np.where(flip[:, None], np.stack([c0, c3, c2, c1], 1), np.stack([c0, c1, c2, c3], 1)))
    Q = np.concatenate(quads, axis=0) if quads else np.zeros((0, 4), dtype=np.int64)
    Q = Q[(Q >= 0).all(axis=1)]
    # the gradient at each vertex (trilinear): the smooth normal
    gx, gy, gz = np.gradient(f)
    G = np.stack([_trilinear(g, V) for g in (gx, gy, gz)], axis=1)
    Vw = v3(origin) + V * h
    # orient every quad outward (its cross product along the gradient), whatever the edge case above decided
    if len(Q):
        e1 = Vw[Q[:, 1]] - Vw[Q[:, 0]]
        e2 = Vw[Q[:, 2]] - Vw[Q[:, 0]]
        n = np.cross(e1, e2)
        g = G[Q].mean(axis=1)
        wrong = (n * g).sum(axis=1) < 0
        Q[wrong] = Q[wrong][:, ::-1]
    return Vw, Q, G


def _trilinear(a, V):
    """a sampled at fractional grid coordinates V (n, 3)."""
    n = np.array(a.shape) - 1
    p = np.clip(V, 0, n - 1e-6)
    i0 = np.floor(p).astype(int)
    t = p - i0
    i1 = np.minimum(i0 + 1, n)
    out = np.zeros(len(V))
    for dx in (0, 1):
        wx = t[:, 0] if dx else 1 - t[:, 0]
        ix = i1[:, 0] if dx else i0[:, 0]
        for dy in (0, 1):
            wy = t[:, 1] if dy else 1 - t[:, 1]
            iy = i1[:, 1] if dy else i0[:, 1]
            for dz in (0, 1):
                wz = t[:, 2] if dz else 1 - t[:, 2]
                iz = i1[:, 2] if dz else i0[:, 2]
                out += wx * wy * wz * a[ix, iy, iz]
    return out


def smooth(V, Q, iterations=2, lam=0.5):
    """Laplacian relaxation on the quad mesh's edges (light: surface nets are nearly smooth already)."""
    if iterations <= 0 or len(Q) == 0:
        return V
    import scipy.sparse as sp
    n = len(V)
    a = np.concatenate([Q[:, 0], Q[:, 1], Q[:, 2], Q[:, 3]])
    b = np.concatenate([Q[:, 1], Q[:, 2], Q[:, 3], Q[:, 0]])
    A = sp.coo_matrix((np.ones(len(a) * 2), (np.concatenate([a, b]), np.concatenate([b, a]))), shape=(n, n)).tocsr()
    A.data[:] = 1.0
    deg = np.asarray(A.sum(axis=1)).ravel()
    deg[deg == 0] = 1
    out = V.copy()
    for _ in range(iterations):
        mean = (A @ out) / deg[:, None]
        out = out + lam * (mean - out)
    return out


def mesh(f, lo, hi, h, relax=0, normals_from=None):
    """Sample f on lo..hi at h and mesh it. Returns (V, N, Q): positions, unit normals, quads. normals_from: a field
    function used for the normals instead of f (a sharper field under a soft one)."""
    field, origin, h = sample(f, lo, hi, h)
    V, Q, G = surface_nets(field, origin, h)
    if relax:
        V = smooth(V, Q, relax)
    if normals_from is not None and len(V):
        eps = h * 0.5
        G = np.stack([(normals_from(V + np.eye(3)[k] * eps) - normals_from(V - np.eye(3)[k] * eps)) for k in range(3)],
                     axis=1)
    ln = np.linalg.norm(G, axis=1)
    N = G / np.where(ln > 1e-12, ln, 1.0)[:, None]
    return V, N, Q


# ------------------------------------------------------------------ OBJ out ----------------------------------------
def write_obj(path, groups, comment=""):
    """groups: [(name, V, N, UV or None, Q)] -> one OBJ with an 'o' per group (what tools/mesh_import.py reads back
    as parts). Quads stay quads; UVs may be None."""
    base_v = base_n = base_t = 1
    with open(path, "w") as fh:
        fh.write("# %s\n" % comment)
        for name, V, N, UV, Q in groups:
            fh.write("o %s\n" % name)
            for p in V:
                fh.write("v %.4f %.4f %.4f\n" % tuple(p))
            for n in N:
                fh.write("vn %.4f %.4f %.4f\n" % tuple(n))
            if UV is not None:
                for t in UV:
                    fh.write("vt %.5f %.5f\n" % (t[0], 1.0 - t[1]))
            for q in Q:
                idx = [int(i) for i in q]
                if len(idx) == 4 and idx[2] == idx[3]:
                    idx = idx[:3]
                if UV is not None:
                    fh.write("f " + " ".join("%d/%d/%d" % (i + base_v, i + base_t, i + base_n) for i in idx) + "\n")
                else:
                    fh.write("f " + " ".join("%d//%d" % (i + base_v, i + base_n) for i in idx) + "\n")
            base_v += len(V)
            base_n += len(N)
            if UV is not None:
                base_t += len(UV)
