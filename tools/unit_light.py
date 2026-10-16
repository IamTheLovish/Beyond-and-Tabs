#!/usr/bin/env python3
"""Baked light for a generated unit body (the realism pass, lovish Oct 16 00:28: "improve it for rts pov so it looks
realistic").

From the RTS camera a body is ~40-80 px tall: the surface detail is gone in the mips and what tells a real figure from
a toy is LIGHT - the sky lighting it from above, dark crevices where plates and limbs meet, the parts under a pauldron
or a helm brim in shade, the legs dimmer than the shoulders. Minecraft lights an entity with two fixed lights and no
occlusion at all, so the painted albedo has to carry it (the baked ambient-occlusion maps of every realistic game).

BodyLight voxelises the body in its rest pose (tools/preview_model.posed_cubes, DSL space: y up, half-pixel voxels)
and gives every painted face a multiplier per texel:
  - ambient occlusion: the share of a cosine-weighted hemisphere round the face's normal that escapes the body within
    AO_REACH px (rays marched through the voxels);
  - sky occlusion: the share of a cone round straight up (the sun and sky over an RTS field) that escapes, RELATIVE to
    what a face with that normal sees with nothing else around (a side face sees half the cone by itself - Minecraft's
    side light already says so; only the shade OTHER parts throw is added here);
  - a height falloff: the upper body catches more of the sky than the legs (ground bounce is weaker than sky light).
Samples are taken once per model pixel and spread bilinearly over the texels (light is smooth; the mips keep it).

Used by tools/dsl_to_game.py when BAKED_LIGHT is on (the realism pass); the team mask gets the same light.
"""
import math

import numpy as np

RES = 0.5            # voxel size, DSL px
AO_REACH = 6.0       # px: how far a crevice's walls count (a torso is ~8 px wide: further and every side face is "a crevice")
SKY_REACH = 26.0     # px: how far up a shadow-throwing part counts (a pauldron over an arm, the body over the legs)
SKY_CONE = math.radians(50)
AO_FLOOR, SKY_FLOOR = 0.70, 0.78     # the darkest a fully enclosed / fully overhung texel gets (Oct 16: 0.52 / 0.66 sank the whole body)
HEIGHT_FALL = 0.08                   # the feet this much darker than the top of the body


def _fibonacci_hemisphere(n, cosine=True):
    """n directions over the +z hemisphere (cosine-weighted: the plain mean of their visibility is the AO)."""
    out = []
    for i in range(n):
        u = (i + 0.5) / n
        phi = i * math.pi * (3 - math.sqrt(5))
        r = math.sqrt(u) if cosine else math.sqrt(1 - (1 - u) ** 2)
        z = math.sqrt(max(0.0, 1 - r * r))
        out.append((r * math.cos(phi), r * math.sin(phi), z))
    return np.array(out, dtype=np.float32)


def _cone(n, half_angle):
    """n directions in a cone round +y, evenly spread in solid angle."""
    out = []
    c0 = math.cos(half_angle)
    for i in range(n):
        u = (i + 0.5) / n
        cz = 1 - u * (1 - c0)
        s = math.sqrt(max(0.0, 1 - cz * cz))
        phi = i * math.pi * (3 - math.sqrt(5))
        out.append((s * math.cos(phi), cz, s * math.sin(phi)))
    return np.array(out, dtype=np.float32)


HEMI = _fibonacci_hemisphere(32)
CONE = _cone(24, SKY_CONE)


def _basis(n):
    """(K, 3) normals -> (K, 3, 3) rows t, b, n (an orthonormal frame per normal)."""
    a = np.where(np.abs(n[:, 1:2]) < 0.9, np.array([[0.0, 1.0, 0.0]]), np.array([[1.0, 0.0, 0.0]]))
    t = np.cross(a, n)
    t /= np.linalg.norm(t, axis=1, keepdims=True)
    b = np.cross(n, t)
    return np.stack([t, b, n], axis=1)


class BodyLight:
    def __init__(self, corners, occluder):
        """corners: per cube its 8 posed corners (DSL space, index x*4 + y*2 + z, lo = 0); occluder: per cube whether
        it throws shade (upgrade-only and glowing cubes do not)."""
        self.corners = [np.asarray(c, dtype=np.float64) for c in corners]
        occ = [c for c, o in zip(self.corners, occluder) if o]
        allp = np.concatenate(occ)
        self.lo = allp.min(0) - AO_REACH - 2
        hi = allp.max(0) + max(AO_REACH, SKY_REACH) + 2
        self.shape = tuple(int(math.ceil(v)) for v in (hi - self.lo) / RES)
        grid = np.zeros(self.shape, dtype=bool)
        for c in occ:
            o, ex, ey, ez = c[0], c[4] - c[0], c[2] - c[0], c[1] - c[0]
            lens = np.array([np.linalg.norm(ex), np.linalg.norm(ey), np.linalg.norm(ez)])
            i0 = np.floor((c.min(0) - self.lo) / RES - 1).astype(int).clip(0)
            i1 = np.ceil((c.max(0) - self.lo) / RES + 1).astype(int)
            i1 = np.minimum(i1, self.shape)
            axes = [np.arange(i0[k], i1[k]) for k in range(3)]
            X, Y, Z = np.meshgrid(*axes, indexing="ij")
            P = np.stack([X, Y, Z], -1).reshape(-1, 3) * RES + self.lo + RES / 2 - o
            inside = np.ones(len(P), dtype=bool)
            for e, L in zip((ex, ey, ez), lens):
                if L < 1e-9:
                    continue
                f = P @ (e / L)
                m = RES * 0.5                      # thin plates (0.2 px capes) still occupy a voxel
                inside &= (f >= -m) & (f <= L + m)
            idx = (P[inside] + o - self.lo - RES / 2) / RES
            idx = np.rint(idx).astype(int)
            grid[idx[:, 0], idx[:, 1], idx[:, 2]] = True
        self.grid = grid
        ys = allp[:, 1]
        self.y0, self.y1 = float(np.percentile(ys, 1)), float(np.percentile(ys, 97))

    def _escape(self, P, D, reach):
        """(K, 3) starts, (K, R, 3) directions -> (K, R) bool: the ray leaves the body within reach."""
        steps = np.arange(RES, reach + 1e-6, RES * 1.5, dtype=np.float32)
        hit = np.zeros(D.shape[:2], dtype=bool)
        for s in steps:
            Q = P[:, None, :] + D * s
            idx = np.floor((Q - self.lo) / RES).astype(np.int32)
            ok = np.all((idx >= 0) & (idx < np.array(self.shape)), axis=-1)
            idx = np.where(ok[..., None], idx, 0)
            hit |= ok & self.grid[idx[..., 0], idx[..., 1], idx[..., 2]]
        return ~hit

    def samples(self, P, N):
        """(K, 3) surface points and outward normals -> (K,) light multipliers."""
        P = np.asarray(P, dtype=np.float32)
        N = np.asarray(N, dtype=np.float32)
        N = N / np.maximum(1e-9, np.linalg.norm(N, axis=1, keepdims=True))
        start = P + N * 0.75                         # off the face's own voxels
        B = _basis(N.astype(np.float64)).astype(np.float32)
        D = np.einsum("rj,kjc->krc", HEMI, B)        # hemisphere round each normal
        ao = self._escape(start, D, AO_REACH).mean(1)
        Dc = np.broadcast_to(CONE, (len(P),) + CONE.shape)
        facing = (Dc @ N[:, :, None])[..., 0] > 0.05  # cone rays the face itself can see
        sky_vis = (self._escape(start, Dc, SKY_REACH) & facing).sum(1)
        expect = facing.sum(1)
        sky = np.where(expect > 0, sky_vis / np.maximum(1, expect), 1.0)
        h = np.clip((P[:, 1] - self.y0) / max(1.0, self.y1 - self.y0), 0, 1) ** 0.7
        return ((AO_FLOOR + (1 - AO_FLOOR) * ao) * (SKY_FLOOR + (1 - SKY_FLOOR) * sky)
                * (1 - HEIGHT_FALL + HEIGHT_FALL * h))

    # the six faces of a box in its texture: model-local fractions (x, y down from the top, z) for (s along u, t along
    # v) - from ModelPart.Cube's polygons (1.20.1): top / bottom rows run from +z to -z, the -x side from +z to -z,
    # the front (-z) and back (+z) sides along +x / -x, the +x side from -z to +z; every side's row 0 is its top
    FACE = {
        "top": (lambda s, t: (s, 0 * s, 1 - t), (0, 1, 0)),
        "bottom": (lambda s, t: (s, 1 + 0 * s, 1 - t), (0, -1, 0)),
        "west": (lambda s, t: (0 * s, t, 1 - s), (-1, 0, 0)),
        "north": (lambda s, t: (s, t, 0 * s), (0, 0, -1)),
        "east": (lambda s, t: (1 + 0 * s, t, s), (1, 0, 0)),
        "south": (lambda s, t: (1 - s, t, 1 + 0 * s), (0, 0, 1)),
    }

    def face(self, cube, face, su, sv, fw, fh, frac_u, frac_v):
        """The light of one painted face as an (fh, fw) array. su, sv: its size in model px (samples once per px);
        frac_u / frac_v: each texel column's / row's fraction across the face."""
        c = self.corners[cube]
        o, ex, ey, ez = c[0], c[4] - c[0], c[2] - c[0], c[1] - c[0]
        fn, (nx, ny, nz) = self.FACE[face]
        nu, nv = max(2, int(math.ceil(su)) + 1), max(2, int(math.ceil(sv)) + 1)
        gu, gv = np.linspace(0.0, 1.0, nu), np.linspace(0.0, 1.0, nv)
        S, T = np.meshgrid(gu, gv)
        fx, fy_down, fz = fn(S.ravel(), T.ravel())
        # model y runs down from the box's top; DSL y runs up: the DSL fraction is 1 - fy_down
        P = o + np.outer(fx, ex) + np.outer(1 - fy_down, ey) + np.outer(fz, ez)
        def unit(v):
            L = np.linalg.norm(v)
            return v / L if L > 1e-9 else v
        n = nx * unit(ex) + ny * unit(ey) + nz * unit(ez)
        lit = self.samples(P, np.repeat(n[None, :], len(P), axis=0)).reshape(nv, nu)
        # bilinear over the texels
        rows = np.array([np.interp(frac_u, gu, lit[r]) for r in range(nv)])          # (nv, fw)
        return np.array([np.interp(frac_v, gv, rows[:, i]) for i in range(len(frac_u))]).T   # (fh, fw)
