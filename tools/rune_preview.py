#!/usr/bin/env python3
"""Offline preview of the rune circles (barfx/RuneFxClient) as the RTS camera sees them - for the quality loop.

Renders the effect with the same per-circle maths as RuneFxClient (fan ring, counter-turning sigil, billboards, and the
v2 layers: stacked halo bands, faction motes, the release beam and ripple) through an orthographic camera at the mod's
yaw (135) and the pitch OrthoviewClientEvents gives a zoom (55 deg close -> 75 far, linear in log zoom), additively
blended the way BarFxRenderTypes.RUNES is (src.rgb * src.a added). Ground: blocky grass; casters: simple box figures.
Not shipped; CI never runs it. The game is the truth - eyes.yml overlay_runes confirms it in the real renderer.

  python tools/rune_preview.py OUTDIR [--style v1|v2]
numpy + pillow + stdlib only.
"""
import argparse
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_rune_textures as gen  # noqa: E402

CELL, GLYPH = gen.CELL, gen.GLYPH
TAU = 2 * math.pi

# RuneFxClient's numbers (keep in step with the Java)
DRAW_IN, FADE, SIGIL_DELAY, GLYPH_DELAY = 0.3, 0.35, 0.12, 0.2
RING_SPIN, SIGIL_SPIN, GLYPH_SPIN = 0.06, -0.15, 0.1
RIM_IN_CELL, SIGIL_SHARE = 0.94, 0.62
CAST_RADIUS = 1.6


def pitch_for(zoom, close=55.0, far=75.0):
    t = (math.log(max(zoom, 10)) - math.log(10)) / (math.log(135) - math.log(10))
    return close + (far - close) * min(1, max(0, t))


class Cam:
    def __init__(self, W, H, zoom, target=(0.0, 0.0, 0.0), yaw=135.0, pitch=None):
        self.W, self.H, self.ppb = W, H, H / zoom
        p = math.radians(pitch if pitch is not None else pitch_for(zoom))
        y = math.radians(yaw)
        self.f = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
        r = np.cross(self.f, [0, 1, 0])
        self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.r, self.f)
        self.t = np.array(target, float)

    def proj(self, P):
        d = np.asarray(P, float) - self.t
        return self.W / 2 + d @ self.r * self.ppb, self.H / 2 - d @ self.u * self.ppb

    def plane(self, h, x0, y0, x1, y1):
        """World x, z of the pixels (x0..x1, y0..y1) on the plane y = h."""
        ys, xs = np.mgrid[y0:y1, x0:x1].astype(np.float32)
        a = (xs + 0.5 - self.W / 2) / self.ppb
        b = -(ys + 0.5 - self.H / 2) / self.ppb
        P0 = self.t[None, None, :] + a[..., None] * self.r + b[..., None] * self.u
        k = (h - P0[..., 1]) / self.f[1]
        P = P0 + k[..., None] * self.f
        return P[..., 0], P[..., 2]


def sample(cell, u, v):
    """Bilinear sample of an RGBA float cell at u, v in [0, 1] (outside = transparent)."""
    h, w = cell.shape[:2]
    x = u * w - 0.5
    y = v * h - 0.5
    inside = (u >= 0) & (u <= 1) & (v >= 0) & (v <= 1)
    x0 = np.clip(np.floor(x).astype(int), 0, w - 1)
    y0 = np.clip(np.floor(y).astype(int), 0, h - 1)
    x1, y1 = np.clip(x0 + 1, 0, w - 1), np.clip(y0 + 1, 0, h - 1)
    fx, fy = np.clip(x - x0, 0, 1)[..., None], np.clip(y - y0, 0, 1)[..., None]
    c = (cell[y0, x0] * (1 - fx) * (1 - fy) + cell[y0, x1] * fx * (1 - fy) + cell[y1, x0] * (1 - fx) * fy
         + cell[y1, x1] * fx * fy)
    return c * inside[..., None]


class Frame:
    def __init__(self, cam, at):
        self.cam = cam
        self.at = at.astype(np.float32) / 255
        self.img = np.zeros((cam.H, cam.W, 3), np.float32)
        self.quads = 0
        # the units' depth (along the camera's forward): effects behind a unit are hidden, as the game's depth test does
        self.udepth = np.full((cam.H, cam.W), np.inf, np.float32)

    # -- atlas cells
    def cell(self, f, row):
        y = {0: 0, 1: CELL, 3: gen.ROW_HALO}[row]
        return self.at[y:y + CELL, f * CELL:(f + 1) * CELL]

    def small(self, f, kind, i=0):
        if kind == 'glyph':
            y = 2 * CELL + (i // 4) * GLYPH
            x = f * CELL + (i % 4) * GLYPH
        elif kind == 'tier':
            return self.at[gen.ROW_FX:gen.ROW_FX + 2 * GLYPH, f * CELL:f * CELL + 2 * GLYPH]
        else:
            col, row = {'mote': (2, 0), 'beam': (3, 0), 'spark': (2, 1), 'shade': (3, 1)}[kind]
            y = gen.ROW_FX + row * GLYPH
            x = f * CELL + col * GLYPH
        return self.at[y:y + GLYPH, x:x + GLYPH]

    def depth(self, P):
        return float((np.asarray(P, float) - self.cam.t) @ self.cam.f)

    def add(self, x0, y0, rgba, alpha, depth=None):
        h, w = rgba.shape[:2]
        H, W = self.img.shape[:2]
        xs, ys, xe, ye = max(0, x0), max(0, y0), min(W, x0 + w), min(H, y0 + h)
        if xe <= xs or ye <= ys:
            return
        sub = rgba[ys - y0:ye - y0, xs - x0:xe - x0]
        vis = 1.0 if depth is None else (depth <= self.udepth[ys:ye, xs:xe] + 0.05)[..., None]
        self.img[ys:ye, xs:xe] += sub[..., :3] * sub[..., 3:4] * alpha * vis

    # -- primitives, as RuneFxClient draws them
    def disc(self, cell, cx, cy, cz, r, spin, alpha, reveal=1.0, seg=32, shade=False, start=-math.pi / 2, feather=0,
             tint=(0.0, 0.0, 0.0), pull=0.0):
        """fan(): a flat disc of radius r at height cy, the cell mapped on it turned by spin, swept in by reveal."""
        if alpha <= 0.01:
            return
        self.quads += seg
        pts = [self.cam.proj((cx + r * math.cos(a), cy, cz + r * math.sin(a))) for a in np.linspace(0, TAU, 17)]
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        x0, x1 = int(max(0, min(xs) - 2)), int(min(self.cam.W, max(xs) + 3))
        y0, y1 = int(max(0, min(ys) - 2)), int(min(self.cam.H, max(ys) + 3))
        if x1 <= x0 or y1 <= y0:
            return
        wx, wz = self.cam.plane(cy, x0, y0, x1, y1)
        lx, lz = wx - cx, wz - cz
        th = np.arctan2(lz, lx)
        rho = np.sqrt(lx * lx + lz * lz) / r
        if feather > 0:   # the sweep's leading slices fade in (RuneFxClient.fan: slice j at (lit - j) / feather)
            lit_f = reveal * (seg + feather)
            j = np.floor(((th - start) % TAU) / (TAU / seg))
            mask = np.clip((lit_f - j) / feather, 0, 1) * (rho <= 1)
        else:
            lit = max(1, min(seg, math.ceil(reveal * seg)))
            mask = ((((th - start) % TAU) < lit * TAU / seg) & (rho <= 1)).astype(np.float32)
        k = th - spin
        u = 0.5 + 0.5 * rho * np.cos(k)
        v = 0.5 + 0.5 * rho * np.sin(k)
        c = sample(cell, u, v) * mask[..., None]
        f = self.cam.f
        d = (wx - self.cam.t[0]) * f[0] + (cy - self.cam.t[1]) * f[1] + (wz - self.cam.t[2]) * f[2]
        c = c * (d - pull <= self.udepth[y0:y1, x0:x1] + 0.05)[..., None]
        if shade:   # ordinary alpha blending of a dark disc (tint): the underlay
            k = c[..., 3:4] * alpha
            self.img[y0:y1, x0:x1] = self.img[y0:y1, x0:x1] * (1 - k) + np.array(tint, np.float32) * k
        else:
            self.img[y0:y1, x0:x1] += c[..., :3] * c[..., 3:4] * alpha

    def stroke(self, cx, cy, cz, r, w, rgb, alpha, reveal=1.0, start=-math.pi / 2, seg=32, feather=2, shade=False,
               pull=0.0):
        """RuneFxClient.stroke(): a flat ring of light of radius r, full within w / 2 of it, fading to 0 at w; drawn as
        geometry (no texture), so its width is whatever the zoom needs."""
        if alpha <= 0.01:
            return
        self.quads += 3 * seg
        ro = r + w
        pts = [self.cam.proj((cx + ro * math.cos(a), cy, cz + ro * math.sin(a))) for a in np.linspace(0, TAU, 17)]
        xs, ys = [q[0] for q in pts], [q[1] for q in pts]
        x0, x1 = int(max(0, min(xs) - 2)), int(min(self.cam.W, max(xs) + 3))
        y0, y1 = int(max(0, min(ys) - 2)), int(min(self.cam.H, max(ys) + 3))
        if x1 <= x0 or y1 <= y0:
            return
        wx, wz = self.cam.plane(cy, x0, y0, x1, y1)
        lx, lz = wx - cx, wz - cz
        th = np.arctan2(lz, lx)
        dist = np.abs(np.sqrt(lx * lx + lz * lz) - r)
        a = np.clip((w - dist) / (w / 2), 0, 1)
        if reveal < 1:
            lit_f = reveal * (seg + feather)
            j = np.floor(((th - start) % TAU) / (TAU / seg))
            a = a * np.clip((lit_f - j) / feather, 0, 1)
        f = self.cam.f
        d = (wx - self.cam.t[0]) * f[0] + (cy - self.cam.t[1]) * f[1] + (wz - self.cam.t[2]) * f[2]
        a = (a * (d - pull <= self.udepth[y0:y1, x0:x1] + 0.05) * alpha)[..., None]
        col = np.array(gen.rgb(rgb), np.float32) / (255 if max(gen.rgb(rgb)) > 1 else 1)
        if shade:
            k = np.clip(a, 0, 1)
            self.img[y0:y1, x0:x1] = self.img[y0:y1, x0:x1] * (1 - k) + col * k
        else:
            self.img[y0:y1, x0:x1] += col * a

    def billboard(self, cell, P, half, alpha, tint=None):
        if alpha <= 0.01:
            return
        self.quads += 1
        sx, sy = self.cam.proj(P)
        size = max(2, int(round(2 * half * self.cam.ppb)))
        im = Image.fromarray((np.clip(cell, 0, 1) * 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)
        a = np.asarray(im, np.float32) / 255
        if tint is not None:
            a = a.copy()
            a[..., :3] *= tint
        self.add(int(round(sx - size / 2)), int(round(sy - size / 2)), a, alpha, self.depth(P))

    def beam(self, cell, P, height, half_w, a_base, a_top):
        """A camera-facing column (vertical quad turned about y), alpha a_base at its foot, a_top at its head."""
        if max(a_base, a_top) <= 0.01:
            return
        self.quads += 1
        sx, sy0 = self.cam.proj(P)
        _, sy1 = self.cam.proj((P[0], P[1] + height, P[2]))
        w = max(2, int(round(2 * half_w * self.cam.ppb)))
        h = max(2, int(round(sy0 - sy1)))
        im = Image.fromarray((np.clip(cell, 0, 1) * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)
        a = np.asarray(im, np.float32) / 255
        ramp = np.linspace(a_top, a_base, h, dtype=np.float32)[:, None, None]
        a = a.copy()
        a[..., 3:4] *= ramp
        self.add(int(round(sx - w / 2)), int(round(sy1)), a, 1.0, self.depth(P) - 0.3)


# ---------------------------------------------------------------- the effect

def strength(t, life, end_at=None):
    i = min(1, t / DRAW_IN * 1.2)
    o = (life - t) / FADE if end_at is None else 1 - (t - end_at) / 0.25
    return max(0, min(i, min(1, o)))


def v1(fr, f, P, t, life=1.5, R=CAST_RADIUS, cid=0):
    """RuneFxClient.circle as #527's first version draws it."""
    s = strength(t, life)
    if s <= 0.01:
        return
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = 0.92 + 0.08 * math.sin(t * TAU * 1.5)
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    fr.disc(fr.cell(f, 0), cx, cy, cz, R * grow / RIM_IN_CELL, t * RING_SPIN * TAU, 0.95 * s * breath, min(1, t / DRAW_IN))
    sa = s * max(0, min(1, (t - SIGIL_DELAY) / 0.2))
    fr.disc(fr.cell(f, 1), cx, cy + 0.01, cz, R * grow * SIGIL_SHARE / RIM_IN_CELL, t * SIGIL_SPIN * TAU, 0.9 * sa, 1, 16)
    ga = s * max(0, min(1, (t - GLYPH_DELAY) / 0.2))
    half = 0.24 * max(1, R / CAST_RADIUS)
    for k in range(4):
        a = t * GLYPH_SPIN * TAU + k * math.pi / 2 + (cid & 7)
        gx, gz = cx + math.cos(a) * R * 0.8, cz + math.sin(a) * R * 0.8
        gy = cy + 0.45 + 0.18 * math.sin(t * 3 + k) + 0.7 * ending
        fr.billboard(fr.small(f, 'glyph', (cid + k) & 7), (gx, gy, gz), half, ga * 0.95)


# v2: the reference's language (stacked rings, motes, a column of light at the release), in our own shapes
HALO_H = (1.5, 2.9)       # heights of the two stacked bands, in blocks at the T1 radius
HALO_R = (0.62, 0.36)       # their radii, shares of R
HALO_SPIN = (-0.22, 0.3)    # rev/s: each turns against the one below it
MOTES = 20
RISE = 2.6                  # how high the motes and glyphs climb, blocks at the T1 radius
FLASH = 0.22                # s: the release flare after the draw-in
BEAM_H = 5.5


def ease(x):
    x = min(1, max(0, x))
    return 1 - (1 - x) ** 3


def hash01(n):
    n = (n * 0x9E3779B1) & 0xFFFFFFFF
    n ^= n >> 15
    return (n & 0xFFFF) / 65535.0


BRASS_ORBIT = ((0.9, 0.5), (1.7, 0.28))   # the tiers the cogs orbit (h, r share)


def mote(f, k, t, cid, R):
    """Where mote k of a circle is at time t, its size and alpha: each faction moves its own way. (x, y, z, half, a)."""
    ph = k / MOTES + hash01(cid * 31 + k)
    sc = R / CAST_RADIUS
    if f == 2:   # Gravebound: ash sinking from the top band onto the rim
        u = (t * 0.55 + ph) % 1
        ang = TAU * (ph + t * 0.05)
        r, y = R * (0.5 + 0.45 * u), RISE * sc * (1 - u)
    elif f == 3:  # Horde: embers thrown up and out, falling back
        u = (t * 0.9 + ph) % 1
        ang = TAU * ph * 3.1
        r, y = R * (0.35 + 0.85 * u), sc * (2.6 * u - 2.0 * u * u) + 0.1
    elif f == 4:  # Verdant: petals on a wide slow spiral
        u = (t * 0.45 + ph) % 1
        ang = TAU * (ph + t * 0.25 + 0.4 * u)
        r, y = R * (0.7 + 0.35 * math.sin(TAU * (u + ph))), RISE * sc * u
    elif f == 5:  # Light Grid: pixels climbing straight lines
        u = (t * 0.8 + ph) % 1
        ang = TAU * round(ph * 12) / 12
        r, y = R * 0.82, RISE * sc * u
    elif f == 6:  # Stormcallers: sparks zigzagging up fast
        u = (t * 1.25 + ph) % 1
        ang = TAU * (ph + 0.06 * (1 if math.sin(t * 40 + k * 7) > 0 else -1))
        r, y = R * (0.9 - 0.45 * u), RISE * 1.15 * sc * u
    elif f == 7:  # Brassworks: cogs orbiting the two bands
        u = 0.5
        lvl = k & 1
        ang = TAU * (ph + t * 0.55 * (1 if lvl else -1))
        r, y = R * BRASS_ORBIT[lvl][1] * 1.18, BRASS_ORBIT[lvl][0] * sc
    elif f == 1:  # Sunforged: stars rising straight round the rim
        u = (t * 0.7 + ph) % 1
        ang = TAU * ph
        r, y = R * 0.88, RISE * sc * u
    else:         # neutral: a plain helix
        u = (t * 0.7 + ph) % 1
        ang = TAU * (ph + t * 0.35)
        r, y = R * (0.95 - 0.5 * u), RISE * sc * u
    a = math.sin(math.pi * u) if f != 7 else 0.85
    return math.cos(ang) * r, y, math.sin(ang) * r, 0.17 * max(1, sc ** 0.5), a


def v2(fr, f, P, t, life=1.5, R=CAST_RADIUS, cid=0):
    s = strength(t, life)
    if s <= 0.01:
        return
    sc = R / CAST_RADIUS
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = 0.92 + 0.08 * math.sin(t * TAU * 1.5)
    flare = math.exp(-max(0, t - DRAW_IN) / FLASH) * min(1, max(0, (t - DRAW_IN + 0.05) / 0.05))
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    # the ground ring and sigil, as v1, flaring at the release
    fr.disc(fr.cell(f, 0), cx, cy, cz, R * grow / RIM_IN_CELL, t * RING_SPIN * TAU,
            0.95 * s * breath * (1 + 0.5 * flare), min(1, t / DRAW_IN))
    sa = s * max(0, min(1, (t - SIGIL_DELAY) / 0.2))
    fr.disc(fr.cell(f, 1), cx, cy + 0.01, cz, R * grow * SIGIL_SHARE / RIM_IN_CELL, t * SIGIL_SPIN * TAU, 0.9 * sa, 1, 16)
    # the ripple: one band thrown out from the rim at the release
    if t >= DRAW_IN:
        q = (t - DRAW_IN) / 0.4
        if q < 1:
            fr.disc(fr.cell(f, 3), cx, cy + 0.02, cz, R * (1 + 0.55 * ease(q)) / RIM_IN_CELL, 0, 0.8 * s * (1 - q), 1, 24)
    # the two stacked bands, rising into place one after the other, turning against each other
    for k in range(2):
        rise = ease((t - 0.06 - 0.1 * k) / 0.32)
        a = s * rise
        if a > 0.01:
            fr.disc(fr.cell(f, 3), cx, cy + HALO_H[k] * sc * rise + 0.25 * ending, cz, R * HALO_R[k] * grow / RIM_IN_CELL,
                    t * HALO_SPIN[k] * TAU + k, 0.85 * a * (1 + 0.6 * flare), 1, 24)
    # the column of light: a flare at the release, then a faint thread while the cast holds
    ba = s * (0.3 + 0.9 * flare)
    if t >= DRAW_IN - 0.05:
        fr.beam(fr.small(f, 'beam'), (cx, cy, cz), BEAM_H * sc * (0.7 + 0.3 * ease((t - DRAW_IN) / 0.15)),
                R * (0.14 + 0.3 * flare), ba, 0.0)
    # motes, each faction its own way
    ma = s * min(1, max(0, (t - 0.1) / 0.2))
    if ma > 0.01:
        for k in range(MOTES):
            x, y, z, half, a = mote(f, k, t, cid, R)
            fr.billboard(fr.small(f, 'mote'), (cx + x, cy + y, cz + z), half, ma * a)
    # glyphs climbing the column
    ga = s * max(0, min(1, (t - GLYPH_DELAY) / 0.2))
    for k in range(5):
        u = (t * 0.42 + k / 5 + hash01(cid + k * 3)) % 1
        ang = t * GLYPH_SPIN * TAU + k * TAU / 5 + (cid & 7)
        rr = R * 0.5
        gy = cy + 0.5 * sc + RISE * 0.95 * sc * u
        fr.billboard(fr.small(f, 'glyph', (cid + k) & 15), (cx + math.cos(ang) * rr, gy, cz + math.sin(ang) * rr),
                     0.2 * max(1, sc), ga * 0.95 * math.sin(math.pi * u))


# v3 (round 2): the round-1 critic's fixes - three clean ground layers on a dark underlay, a short flash on the ring
# only, a 1.4x shock ring, a real three-tier column, a pillar from the head up, alphas that keep overlaps under 1
SIGIL_SHARE3 = 0.44         # the sigil's reach, share of R (0.7 of 1.6)
UNDER_R, UNDER_A = 1.06, 0.3
RING_A, SIGIL_A, FLASH_A, FLASH_T = 0.8, 0.95, 0.6, 0.12
SHOCK_R, SHOCK_T, SHOCK_A = 1.4, 0.25, 0.5
TIER_H = (0.6, 1.4, 2.2)    # blocks at the T1 radius
TIER_R = (0.625, 0.44, 0.28)   # shares of R: 1.0, 0.7, 0.45 blocks at R 1.6
TIER_AT = (0.2, 0.3, 0.4)   # s: each sweeps up from the one below
TIER_SPIN = 0.25            # rev/s, alternating
TIER_A = 0.55
PILLAR_FROM, PILLAR_H, PILLAR_HW, PILLAR_A, PILLAR_T = 2.0, 2.0, 0.15, 0.7, 0.25
MOTES3, MOTE_A3 = 16, 0.8
flash_scale = 1.0           # the frame's cap: dimmed when many casters flash together


def v3(fr, f, P, t, life=1.5, R=CAST_RADIUS, cid=0):
    s = strength(t, life)
    if s <= 0.01:
        return
    sc = R / CAST_RADIUS
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = 0.94 + 0.06 * math.sin(t * TAU * 1.5)
    rel = t - DRAW_IN
    flash = flash_scale * (max(0, 1 - rel / FLASH_T) if rel >= 0 else 0)
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    reveal = min(1, t / DRAW_IN)
    fr.disc(fr.small(f, 'shade'), cx, cy - 0.01, cz, R * UNDER_R * grow, 0, UNDER_A * s, reveal, 16, shade=True)
    ring_r = R * grow / RIM_IN_CELL
    fr.disc(fr.cell(f, 0), cx, cy, cz, ring_r, t * RING_SPIN * TAU, RING_A * s * breath, reveal)
    if flash > 0.01:
        fr.disc(fr.cell(f, 0), cx, cy, cz, ring_r, t * RING_SPIN * TAU, FLASH_A * flash * s)
    sa = s * max(0, min(1, (t - SIGIL_DELAY) / 0.2))
    fr.disc(fr.cell(f, 1), cx, cy + 0.01, cz, R * grow * SIGIL_SHARE3 / RIM_IN_CELL, t * SIGIL_SPIN * TAU,
            SIGIL_A * sa, 1, 16)
    if 0 <= rel < SHOCK_T:
        q = rel / SHOCK_T
        fr.disc(fr.small(f, 'tier'), cx, cy + 0.02, cz, R * (1 + (SHOCK_R - 1) * ease(q)) / RIM_IN_CELL, 0,
                SHOCK_A * (1 - q) * s, 1, 24)
    for k in range(3):
        rise = ease((t - TIER_AT[k]) / 0.15)
        if rise <= 0.01:
            continue
        base = TIER_H[k - 1] * sc if k else 0.0
        h = base + (TIER_H[k] * sc - base) * rise + 0.2 * ending
        cell = fr.cell(f, 3) if k == 0 else fr.small(f, 'tier')
        fr.disc(cell, cx, cy + h, cz, R * TIER_R[k] * grow / RIM_IN_CELL, (1 if k % 2 else -1) * t * TIER_SPIN * TAU + k,
                TIER_A * s * rise, 1, 24 if k == 0 else 16)
    if 0 <= rel < PILLAR_T + 0.05:
        pa = PILLAR_A * flash_scale * s * max(0, 1 - rel / PILLAR_T) * min(1, rel / 0.03 + 0.3)
        fr.beam(fr.small(f, 'beam'), (cx, cy + PILLAR_FROM * sc, cz), PILLAR_H * sc, PILLAR_HW * sc, pa, 0.0)
    ma = s * min(1, max(0, (t - 0.2) / 0.2)) * MOTE_A3
    if ma > 0.01:
        for k in range(MOTES3):
            x, y, z, half, a = mote(f, k, t, cid, R)
            fr.billboard(fr.small(f, 'mote'), (cx + x, cy + y, cz + z), half * 0.85, ma * a)
    ga = s * max(0, min(1, (t - 0.4) / 0.2))
    for k in range(3):
        u = (t * 0.42 + k / 3 + hash01(cid + k * 3)) % 1
        ang = t * GLYPH_SPIN * TAU + k * TAU / 3 + (cid & 7)
        gy = cy + 0.9 * sc + 1.8 * sc * u
        fr.billboard(fr.small(f, 'glyph', (cid + k) & 15), (cx + math.cos(ang) * R * 0.75, gy, cz + math.sin(ang) * R * 0.75),
                     0.17 * max(1, sc), ga * 0.7 * math.sin(math.pi * u))


# v4 (round 3): the round-2 critic's fixes - warm trio apart (atlas), the tiers taller and bolder sweeping in after the
# ground ring and fading top first, a crisp glyph band thrown out at the release, the sweep starting on the far side
# with a feathered edge, a livelier hold (faster ring, quick motes), a dark rim line under the ring, Gravebound's violet
# underglow, crowded circles dimmed, a shorter flash
RING_SPIN4 = 0.17           # rev/s (61 deg/s): the hold must change between stills
FLASH_T4 = 0.1
TIERS4 = ((0.9, 0.5, 0.3, 0.7, -0.167, 'band'), (1.7, 0.28, 0.34, 0.55, 0.25, 'tier'))   # h, r share, at, alpha, spin
SHOCK_R4, SHOCK_T4, SHOCK_A4 = 1.55, 0.2, 0.8
PILLAR4 = (2.0, 2.5, 0.125, 0.5, 0.12)   # from, height, half width, alpha, life (from the release)
RIM_DARK_A = 0.35
UNDER_TINT = {2: ((0.165, 0.04, 0.23), 0.35)}   # Gravebound: a dark violet underglow; the rest black at UNDER_A
MOTES4, MOTE_SPEED4 = 24, 2.0
FEATHER = 2                 # slices
crowd = {}                  # cid -> dimming for circles crowded within 4 blocks (the scene fills it)


def sweep_start(cam):
    """The ground angle of the camera's far side: the sweep starts there, so its straight edge sits behind the unit."""
    return math.atan2(cam.f[2], cam.f[0])


def v4(fr, f, P, t, life=1.5, R=CAST_RADIUS, cid=0):
    s = strength(t, life)
    if s <= 0.01:
        return
    s_light = s * crowd.get(cid, 1.0)
    sc = R / CAST_RADIUS
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = 0.94 + 0.06 * math.sin(t * TAU * 1.5)
    rel = t - DRAW_IN
    flash = flash_scale * (max(0, 1 - rel / FLASH_T4) if rel >= 0 else 0)
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    reveal = min(1, t / DRAW_IN)
    a0 = sweep_start(fr.cam)
    tint, ua = UNDER_TINT.get(f, ((0.0, 0.0, 0.0), UNDER_A))
    ring_r = R * grow / RIM_IN_CELL
    fr.disc(fr.small(f, 'shade'), cx, cy - 0.01, cz, R * UNDER_R * grow, 0, ua * s, reveal, 16, shade=True, start=a0,
            feather=FEATHER, tint=tint)
    fr.disc(fr.small(f, 'tier'), cx, cy - 0.005, cz, ring_r * 1.03, 0, RIM_DARK_A * s, reveal, 32, shade=True, start=a0,
            feather=FEATHER)
    fr.disc(fr.cell(f, 0), cx, cy, cz, ring_r, t * RING_SPIN4 * TAU, RING_A * s_light * breath, reveal, start=a0,
            feather=FEATHER)
    if flash > 0.01:
        fr.disc(fr.cell(f, 0), cx, cy, cz, ring_r, t * RING_SPIN4 * TAU, FLASH_A * flash * s_light)
    sa = s_light * max(0, min(1, (t - SIGIL_DELAY) / 0.2))
    fr.disc(fr.cell(f, 1), cx, cy + 0.01, cz, R * grow * SIGIL_SHARE3 / RIM_IN_CELL, t * SIGIL_SPIN * TAU,
            SIGIL_A * sa, 1, 16)
    if 0 <= rel < SHOCK_T4:   # a crisp glyph band thrown out, ease-out, fading fast
        q = rel / SHOCK_T4
        fr.disc(fr.cell(f, 3), cx, cy + 0.02, cz, R * (1 + (SHOCK_R4 - 1) * ease(q)) / RIM_IN_CELL, t * 0.3,
                SHOCK_A4 * (1 - q) ** 2 * s_light, 1, 32)
    left = life - t
    for k, (h, rs, at, ta, spin, kind) in enumerate(TIERS4):
        rise = ease((t - at) / 0.12)
        top_first = min(1, max(0, (left - (0.15 if k == 1 else 0.0)) / 0.15))
        a = ta * s_light * rise * top_first
        if a <= 0.01:
            continue
        cell = fr.cell(f, 3) if kind == 'band' else fr.small(f, 'tier')
        fr.disc(cell, cx, cy + h * sc * rise, cz, R * rs * grow / RIM_IN_CELL, t * spin * TAU + k, a, 1,
                24 if kind == 'band' else 16)
    p_from, p_h, p_hw, p_a, p_t = PILLAR4
    if 0 <= rel < p_t:
        pa = p_a * flash_scale * s_light * (1 - rel / p_t)
        fr.beam(fr.small(f, 'beam'), (cx, cy + p_from * sc, cz), p_h * sc, p_hw * sc, pa, 0.0)
    ma = s_light * min(1, max(0, (t - 0.2) / 0.2)) * MOTE_A3
    if ma > 0.01:
        tm = t if f == 7 else t * MOTE_SPEED4
        for k in range(MOTES4):
            x, y, z, half, a = mote(f, k, tm, cid, R)
            fr.billboard(fr.small(f, 'mote'), (cx + x, cy + y, cz + z), half * 0.85, ma * a)
    ga = s_light * max(0, min(1, (t - 0.4) / 0.2))
    for k in range(3):
        u = (t * 0.42 + k / 3 + hash01(cid + k * 3)) % 1
        ang = t * GLYPH_SPIN * TAU + k * TAU / 3 + (cid & 7)
        gy = cy + 0.9 * sc + 1.8 * sc * u
        fr.billboard(fr.small(f, 'glyph', (cid + k) & 15), (cx + math.cos(ang) * R * 0.75, gy, cz + math.sin(ang) * R * 0.75),
                     0.17 * max(1, sc), ga * 0.7 * math.sin(math.pi * u))


# v5 (round 4): the round-3 critic's fixes - a hold as bright as the release's aftermath (ring alpha 1, no dark rim
# line), a light underlay inside the ring tinted by the faction (none for the light factions), an expanding pulse ring
# readable at zoom 30, a glyph at the sweep's tip, a real crown tier (the glyph band over the head), motes kept below
# 1.5 blocks, crisp Brassworks cogs orbiting at head height; Gravebound / Verdant further apart (atlas)
RING_A5 = 1.0
UNDER5 = {0: 0.15, 1: 0.0, 2: 0.2, 3: 0.15, 4: 0.15, 5: 0.0, 6: 0.15, 7: 0.15}   # alpha per faction
UNDER_R5 = 0.85             # share of the ring: never shows outside it
UNDER_VALUE = 0.25          # the tint: the faction's hue at this value (Gravebound: its violet)
PULSE5 = (0.625, 1.375, 0.25, 0.5)   # a plain ring from 1.0 to 2.2 blocks over 0.25 s, alpha 0.5 -> 0
TIERS5 = ((0.8, 0.62, 0.30, 0.6, -0.2, 'tier'), (1.7, 0.5, 0.36, 0.75, 0.25, 'band'))
TIP_HALF = 0.16
RISE5 = 1.5


def under_tint(f):
    if f == 2:
        return (0.165, 0.04, 0.23)
    hue = gen.rgb(gen.FACTIONS[f][1])
    return tuple(float(c) for c in hue / max(hue.max(), 1e-3) * UNDER_VALUE)


def v5(fr, f, P, t, life=1.5, R=CAST_RADIUS, cid=0):
    global RISE
    s = strength(t, life)
    if s <= 0.01:
        return
    s_light = s * crowd.get(cid, 1.0)
    sc = R / CAST_RADIUS
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = 0.94 + 0.06 * math.sin(t * TAU * 1.5)
    rel = t - DRAW_IN
    flash = flash_scale * (max(0, 1 - rel / FLASH_T4) if rel >= 0 else 0)
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    reveal = min(1, t / DRAW_IN)
    a0 = sweep_start(fr.cam)
    ring_r = R * grow / RIM_IN_CELL
    if UNDER5[f] > 0:
        fr.disc(fr.small(f, 'shade'), cx, cy - 0.01, cz, R * UNDER_R5 * grow, 0, UNDER5[f] * s, reveal, 16, shade=True,
                start=a0, feather=FEATHER, tint=under_tint(f))
    fr.disc(fr.cell(f, 0), cx, cy, cz, ring_r, t * RING_SPIN4 * TAU, RING_A5 * s_light * breath, reveal, start=a0,
            feather=FEATHER)
    if t < DRAW_IN:   # the glyph at the sweep's tip: the circle is being written
        ang = a0 + reveal * TAU
        fr.billboard(fr.small(f, 'glyph', cid & 15), (cx + math.cos(ang) * R * 0.92, cy + 0.25, cz + math.sin(ang) * R * 0.92),
                     TIP_HALF * max(1, sc), s_light)
    if flash > 0.01:
        fr.disc(fr.cell(f, 0), cx, cy, cz, ring_r, t * RING_SPIN4 * TAU, FLASH_A * flash * s_light)
    sa = s_light * max(0, min(1, (t - SIGIL_DELAY) / 0.2))
    fr.disc(fr.cell(f, 1), cx, cy + 0.01, cz, R * grow * SIGIL_SHARE3 / RIM_IN_CELL, t * SIGIL_SPIN * TAU,
            SIGIL_A * sa, 1, 16)
    if 0 <= rel < SHOCK_T4:   # the glyph band bursting outward
        q = rel / SHOCK_T4
        fr.disc(fr.cell(f, 3), cx, cy + 0.02, cz, R * (1 + (SHOCK_R4 - 1) * ease(q)) / RIM_IN_CELL, t * 0.3,
                SHOCK_A4 * (1 - q) ** 2 * s_light, 1, 32)
    p0, p1, pt, pa = PULSE5
    if 0 <= rel < pt:         # and a plain pulse ring, the moment read at zoom 30
        q = rel / pt
        fr.disc(fr.small(f, 'tier'), cx, cy + 0.03, cz, R * (p0 + (p1 - p0) * ease(q)) / RIM_IN_CELL, 0,
                pa * (1 - q) * s_light, 1, 24)
    left = life - t
    for k, (h, rs, at, ta, spin, kind) in enumerate(TIERS5):
        rise = ease((t - at) / 0.12)
        top_first = min(1, max(0, (left - (0.15 if k == 1 else 0.0)) / 0.15))
        a = ta * s_light * rise * top_first
        if a <= 0.01:
            continue
        cell = fr.cell(f, 3) if kind == 'band' else fr.small(f, 'tier')
        fr.disc(cell, cx, cy + h * sc * rise, cz, R * rs * grow / RIM_IN_CELL, t * spin * TAU + k, a, 1,
                24 if kind == 'band' else 16)
    p_from, p_h, p_hw, p_a, p_t = PILLAR4
    if 0 <= rel < p_t:
        fr.beam(fr.small(f, 'beam'), (cx, cy + p_from * sc, cz), p_h * sc, p_hw * sc,
                p_a * flash_scale * s_light * (1 - rel / p_t), 0.0)
    ma = s_light * min(1, max(0, (t - 0.2) / 0.2)) * MOTE_A3
    if ma > 0.01:
        keep, RISE = RISE, RISE5
        try:
            if f == 7:   # cogs: ten, orbiting at head height, 0.6 rev/s
                for k in range(10):
                    ang = TAU * (k / 10 + t * 0.6)
                    fr.billboard(fr.small(f, 'mote'), (cx + math.cos(ang) * R * 0.75, cy + 1.6 * sc,
                                                       cz + math.sin(ang) * R * 0.75), 0.18, ma)
            else:
                for k in range(MOTES4):
                    x, y, z, half, a = mote(f, k, t * MOTE_SPEED4, cid, R)
                    fr.billboard(fr.small(f, 'mote'), (cx + x, cy + y, cz + z), half * 0.85, ma * a)
        finally:
            RISE = keep
    ga = s_light * max(0, min(1, (t - 0.4) / 0.2))
    for k in range(3):
        u = (t * 0.42 + k / 3 + hash01(cid + k * 3)) % 1
        ang = t * GLYPH_SPIN * TAU + k * TAU / 3 + (cid & 7)
        gy = cy + 0.4 * sc + 1.1 * sc * u
        fr.billboard(fr.small(f, 'glyph', (cid + k) & 15), (cx + math.cos(ang) * R * 0.75, gy, cz + math.sin(ang) * R * 0.75),
                     0.17 * max(1, sc), ga * 0.7 * math.sin(math.pi * u))


# v6 (round 5): the round-4 critic's fixes - the faction sigil off the ground centre (the unit hides it from this pitch)
# to a marker spinning over the head, the waist band carrying the glyphs, a seed of light at the feet the moment the
# cast starts, a 2 Hz breath in the hold, a dimmer flash; Storm / Light Grid / Sunforged lose their additive white-out
# and Horde its gold (atlas: deeper cores, per-faction gain)
SIGIL_H6, SIGIL_R6, SIGIL_SPIN6 = 2.45, 0.38, 60 / 360   # blocks over the feet (T1), share of R (0.6 block), rev/s
TIERS6 = ((0.8, 0.62, 0.30, 0.7, -0.2, 'band'),)
SEED6 = (0.5, 0.25)          # the seed: half size in blocks, life in s
FLASH_A6 = 0.45
BREATH6 = (0.9, 0.1, 2.0)    # base, depth, Hz


def v6(fr, f, P, t, life=1.5, R=CAST_RADIUS, cid=0):
    global RISE
    s = strength(t, life)
    if s <= 0.01:
        return
    s_light = s * crowd.get(cid, 1.0)
    sc = R / CAST_RADIUS
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = BREATH6[0] + BREATH6[1] * math.sin(t * TAU * BREATH6[2])
    rel = t - DRAW_IN
    flash = flash_scale * (max(0, 1 - rel / FLASH_T4) if rel >= 0 else 0)
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    reveal = min(1, t / DRAW_IN)
    a0 = sweep_start(fr.cam)
    ring_r = R * grow / RIM_IN_CELL
    if UNDER5[f] > 0:
        fr.disc(fr.small(f, 'shade'), cx, cy - 0.01, cz, R * UNDER_R5 * grow, 0, UNDER5[f] * s, reveal, 16, shade=True,
                start=a0, feather=FEATHER, tint=under_tint(f))
    if t < SEED6[1]:   # the seed: light gathers at the feet the moment the cast starts
        fr.billboard(fr.small(f, 'spark'), (cx, cy + 0.15, cz), SEED6[0] * max(1, sc), 1 - t / SEED6[1])
    fr.disc(fr.cell(f, 0), cx, cy, cz, ring_r, t * RING_SPIN4 * TAU, RING_A5 * s_light * breath, reveal, start=a0,
            feather=FEATHER)
    if t < DRAW_IN:
        ang = a0 + reveal * TAU
        fr.billboard(fr.small(f, 'glyph', cid & 15), (cx + math.cos(ang) * R * 0.92, cy + 0.25, cz + math.sin(ang) * R * 0.92),
                     TIP_HALF * max(1, sc), s_light)
    if flash > 0.01:
        fr.disc(fr.cell(f, 0), cx, cy, cz, ring_r, t * RING_SPIN4 * TAU, FLASH_A6 * flash * s_light)
    if 0 <= rel < SHOCK_T4:
        q = rel / SHOCK_T4
        fr.disc(fr.cell(f, 3), cx, cy + 0.02, cz, R * (1 + (SHOCK_R4 - 1) * ease(q)) / RIM_IN_CELL, t * 0.3,
                SHOCK_A4 * (1 - q) ** 2 * s_light, 1, 32)
    p0, p1, pt, pa = PULSE5
    if 0 <= rel < pt:
        q = rel / pt
        fr.disc(fr.small(f, 'tier'), cx, cy + 0.03, cz, R * (p0 + (p1 - p0) * ease(q)) / RIM_IN_CELL, 0,
                pa * (1 - q) * s_light, 1, 24)
    for k, (h, rs, at, ta, spin, kind) in enumerate(TIERS6):
        rise = ease((t - at) / 0.12)
        a = ta * s_light * rise
        if a > 0.01:
            fr.disc(fr.cell(f, 3), cx, cy + h * sc * rise, cz, R * rs * grow / RIM_IN_CELL, t * spin * TAU + k, a, 1, 24)
    # the faction's sigil over the head: grows in with the draw-in, spins, shrinks away with the fade
    grow_in = ease((t - 0.08) / 0.22)
    shrink = min(1, max(0, (life - t) / 0.4))
    k_s = grow_in * shrink
    if k_s > 0.01:
        fr.disc(fr.cell(f, 1), cx, cy + SIGIL_H6 * sc, cz, R * SIGIL_R6 * k_s / RIM_IN_CELL, t * SIGIL_SPIN6 * TAU,
                SIGIL_A * s_light * min(1, k_s * 1.5), 1, 16)
    p_from, p_h, p_hw, p_a, p_t = PILLAR4
    if 0 <= rel < p_t:
        fr.beam(fr.small(f, 'beam'), (cx, cy + p_from * sc, cz), p_h * sc, p_hw * sc,
                p_a * flash_scale * s_light * (1 - rel / p_t), 0.0)
    ma = s_light * min(1, max(0, (t - 0.2) / 0.2)) * MOTE_A3
    if ma > 0.01:
        keep, RISE = RISE, RISE5
        try:
            if f == 7:
                for k in range(10):
                    ang = TAU * (k / 10 + t * 0.6)
                    fr.billboard(fr.small(f, 'mote'), (cx + math.cos(ang) * R * 0.75, cy + 1.6 * sc,
                                                       cz + math.sin(ang) * R * 0.75), 0.18, ma)
            else:
                for k in range(MOTES4):
                    x, y, z, half, a = mote(f, k, t * MOTE_SPEED4, cid, R)
                    fr.billboard(fr.small(f, 'mote'), (cx + x, cy + y, cz + z), half * 0.85, ma * a)
        finally:
            RISE = keep
    ga = s_light * max(0, min(1, (t - 0.4) / 0.2))
    for k in range(3):
        u = (t * 0.42 + k / 3 + hash01(cid + k * 3)) % 1
        ang = t * GLYPH_SPIN * TAU + k * TAU / 3 + (cid & 7)
        gy = cy + 0.4 * sc + 1.1 * sc * u
        fr.billboard(fr.small(f, 'glyph', (cid + k) & 15), (cx + math.cos(ang) * R * 0.75, gy, cz + math.sin(ang) * R * 0.75),
                     0.17 * max(1, sc), ga * 0.7 * math.sin(math.pi * u))


# v7 (round 6, the first judged on in-game stills: 21/60): the textured rim was ~1 px on screen, grass blades cut it,
# Verdant and Sunforged sank into the grass, 48 overlapping circles made a haze at zoom 45. Now: a rim stroke drawn as
# geometry (a near-white core, a faction halo, a dark line under both) never thinner than 3 px; the ground layer pulled
# 1 block toward the camera (grass no longer cuts it, units still hide it); radius 1.3 (was 1.6), at most x1.5 with the
# zoom (was x2); the crowd dims less; the release is the rim thrown out plus a white burst (no smudged glyph disc);
# no climbing glyphs; past zoom 33 the details fade, past 45 only the rim, its dark line and the release are drawn.
CAST_RADIUS7, SCALE_MAX7 = 1.3, 1.5
CROWD7 = (3.0, 0.75)          # the crowd: radius in blocks, the least the light is dimmed to
CORE7 = {0: 0xFFFFFF, 1: 0xFFF1B0, 2: 0xE2FF6A, 3: 0xFFB080, 4: 0x9CFFC8, 5: 0xA8F8FF, 6: 0xD0B8FF, 7: 0xFFE0A0}
HALO7 = {0: 0xC8C8FF, 1: 0xFF9A2E, 2: 0x8FD000, 3: 0xFF2A00, 4: 0x2AE890, 5: 0x00D8F0, 6: 0x9050FF, 7: 0xE0A020}
DARK7 = {0: 0x202040, 1: 0x3A1E00, 2: 0x1A2A00, 3: 0x3A0800, 4: 0x0B3D1E, 5: 0x002A3A, 6: 0x1E0A40, 7: 0x3A2400}
CORE_W7 = (0.05, 1.5)         # the rim core's half width: at least this many blocks, at least this many px
HALO_K7, HALO_A7, CORE_A7 = 3.0, 0.35, 0.95
DARK_K7, DARK_A7, UNDER7 = 2.2, 0.55, 0.18
LOD7 = (33.0, 45.0)           # zoom: the details fade out from the first to the second
PULL7 = 1.0                   # blocks toward the camera, the ground layer only
PULSE7 = (1.0, 1.35, 0.2)     # the rim thrown out at the release: from, to (shares of R), s
BURST7 = (0.6, 0.1, 0.8)      # the white burst at the release: half size (blocks), s, alpha
SIGIL_SPIN7 = 0.25
MOTE_HALF7, MOTE_A7 = 0.19, 1.0


def radius_for(style, zoom):
    base, top = {'v7': (CAST_RADIUS7, SCALE_MAX7), 'v8': (CAST_RADIUS7, SCALE_MAX8), 'v9': (CAST_RADIUS7, SCALE_MAX8)}.get(style, (CAST_RADIUS, 2.0))
    return base * max(1.0, min(top, zoom / 30))


def v7(fr, f, P, t, life=1.5, R=CAST_RADIUS7, cid=0):
    global RISE
    s = strength(t, life)
    if s <= 0.01:
        return
    s_light = s * crowd.get(cid, 1.0)
    zoom = 1080 / fr.cam.ppb
    near = min(1.0, max(0.0, (LOD7[1] - zoom) / (LOD7[1] - LOD7[0])))
    wc = max(CORE_W7[0], CORE_W7[1] / fr.cam.ppb)
    sc = R / CAST_RADIUS7
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = BREATH6[0] + BREATH6[1] * math.sin(t * TAU * BREATH6[2])
    rel = t - DRAW_IN
    flash = flash_scale * (max(0, 1 - rel / FLASH_T4) if rel >= 0 else 0)
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    reveal = min(1, t / DRAW_IN)
    a0 = sweep_start(fr.cam)
    rr = R * grow
    # the dark ground: the underlay, and a dark line under the rim (alpha blended)
    fr.disc(fr.small(f, 'shade'), cx, cy - 0.01, cz, rr * UNDER_R5, 0, UNDER7 * s, reveal, 16, shade=True, start=a0,
            feather=FEATHER, tint=under_tint(f), pull=PULL7)
    fr.stroke(cx, cy, cz, rr, wc * DARK_K7, DARK7[f], DARK_A7 * s, reveal, a0, 24, shade=True, pull=PULL7)
    if t < SEED6[1]:
        fr.billboard(fr.small(f, 'spark'), (cx, cy + 0.15, cz), SEED6[0] * max(1, sc), 1 - t / SEED6[1])
    if near > 0.01:   # the rune ring's detail inside the rim
        fr.disc(fr.cell(f, 0), cx, cy, cz, rr / RIM_IN_CELL, t * RING_SPIN4 * TAU, RING_A5 * s_light * breath * near,
                reveal, start=a0, feather=FEATHER, pull=PULL7)
    fr.stroke(cx, cy, cz, rr, wc * HALO_K7, HALO7[f], HALO_A7 * s_light * (1 + flash), reveal, a0, pull=PULL7)
    fr.stroke(cx, cy, cz, rr, wc, CORE7[f], min(1.0, CORE_A7 * s_light * breath + 0.5 * flash * s_light), reveal, a0,
              pull=PULL7)
    if t < DRAW_IN and near > 0.01:
        ang = a0 + reveal * TAU
        fr.billboard(fr.small(f, 'glyph', cid & 15), (cx + math.cos(ang) * rr * 0.92, cy + 0.25, cz + math.sin(ang) * rr * 0.92),
                     TIP_HALF * max(1, sc), s_light * near)
    p0, p1, pt = PULSE7
    if 0 <= rel < pt:   # the release: the rim thrown out
        q = rel / pt
        rp = rr * (p0 + (p1 - p0) * ease(q))
        fr.stroke(cx, cy + 0.02, cz, rp, wc * HALO_K7, HALO7[f], HALO_A7 * (1 - q) * s_light, pull=PULL7)
        fr.stroke(cx, cy + 0.02, cz, rp, wc, CORE7[f], (1 - q) * s_light, pull=PULL7)
    bh, bt, ba = BURST7
    if 0 <= rel < bt:
        fr.billboard(fr.small(f, 'spark'), (cx, cy + 0.15, cz), bh * max(1, sc), ba * flash_scale * (1 - rel / bt))
    if near <= 0.01:
        return
    for k, (h, rs, at, ta, spin, kind) in enumerate(TIERS6):
        rise = ease((t - at) / 0.12)
        a = ta * s_light * rise * near
        if a > 0.01:
            fr.disc(fr.cell(f, 3), cx, cy + h * sc * rise, cz, R * rs * grow / RIM_IN_CELL, t * spin * TAU + k, a, 1, 24)
    grow_in = ease((t - 0.08) / 0.22)
    shrink = min(1, max(0, (life - t) / 0.4))
    k_s = grow_in * shrink
    if k_s > 0.01:
        sa = SIGIL_A * s_light * min(1, k_s * 1.5) * near
        sr = R * SIGIL_R6 * k_s
        fr.disc(fr.cell(f, 1), cx, cy + SIGIL_H6 * sc, cz, sr / RIM_IN_CELL, t * SIGIL_SPIN7 * TAU, sa, 1, 16)
        fr.stroke(cx, cy + SIGIL_H6 * sc, cz, sr * 1.04, wc * 0.8, CORE7[f], sa * 0.8)
    p_from, p_h, p_hw, p_a, p_t = PILLAR4
    if 0 <= rel < p_t:
        fr.beam(fr.small(f, 'beam'), (cx, cy + p_from * sc, cz), p_h * sc, p_hw * sc,
                p_a * flash_scale * s_light * (1 - rel / p_t) * near, 0.0)
    ma = s_light * min(1, max(0, (t - 0.2) / 0.2)) * MOTE_A7 * near
    if ma > 0.01:
        keep, RISE = RISE, RISE5
        try:
            if f == 7:
                for k in range(10):
                    ang = TAU * (k / 10 + t * 0.6)
                    fr.billboard(fr.small(f, 'mote'), (cx + math.cos(ang) * R * 0.75, cy + 1.6 * sc,
                                                       cz + math.sin(ang) * R * 0.75), 0.18 * 1.2, ma)
            else:
                for k in range(MOTES4):
                    x, y, z, half, a = mote(f, k, t * MOTE_SPEED4, cid, R)
                    fr.billboard(fr.small(f, 'mote'), (cx + x, cy + y, cz + z), MOTE_HALF7 * max(1, math.sqrt(sc)),
                                 ma * a)
        finally:
            RISE = keep


# v8 (round 7: v7 beat v6 38 to 29, blind): the critic's next fixes - a second stroke weight (an inner line at 0.68 of
# the rim, 0.6 of its width), the rim settling to 0.7 over the hold, the underlay darker (the dark line's colour, 0.32,
# the full ring), the sigil's ring in the faction's hue (white read as a selection marker), fewer bigger motes, the zoom
# growth capped at 1.2 (less overlap in a dense army).
UNDER8 = 0.32
SETTLE8 = (0.5, 1.3, 0.3)     # the rim's core: from t, to t, how much it settles
INNER8 = (0.68, 0.6, 0.75)    # the inner line: radius share, width share of the core's, alpha
SIGIL_RING_A8 = 0.6
MOTES8, MOTE_HALF8 = 14, 0.26
SCALE_MAX8 = 1.2


def v8(fr, f, P, t, life=1.5, R=CAST_RADIUS7, cid=0):
    global RISE, MOTES
    s = strength(t, life)
    if s <= 0.01:
        return
    s_light = s * crowd.get(cid, 1.0)
    zoom = 1080 / fr.cam.ppb
    near = min(1.0, max(0.0, (LOD7[1] - zoom) / (LOD7[1] - LOD7[0])))
    wc = max(CORE_W7[0], CORE_W7[1] / fr.cam.ppb)
    sc = R / CAST_RADIUS7
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = BREATH6[0] + BREATH6[1] * math.sin(t * TAU * BREATH6[2])
    rel = t - DRAW_IN
    flash = flash_scale * (max(0, 1 - rel / FLASH_T4) if rel >= 0 else 0)
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    reveal = min(1, t / DRAW_IN)
    a0 = sweep_start(fr.cam)
    rr = R * grow
    # the dark ground: the underlay, and a dark line under the rim (alpha blended)
    fr.disc(fr.small(f, 'shade'), cx, cy - 0.01, cz, rr, 0, UNDER8 * s, reveal, 16, shade=True, start=a0,
            feather=FEATHER, tint=tuple(float(c) for c in gen.rgb(DARK7[f])), pull=PULL7)
    fr.stroke(cx, cy, cz, rr, wc * DARK_K7, DARK7[f], DARK_A7 * s, reveal, a0, 24, shade=True, pull=PULL7)
    if t < SEED6[1]:
        fr.billboard(fr.small(f, 'spark'), (cx, cy + 0.15, cz), SEED6[0] * max(1, sc), 1 - t / SEED6[1])
    if near > 0.01:   # the rune ring's detail inside the rim
        fr.disc(fr.cell(f, 0), cx, cy, cz, rr / RIM_IN_CELL, t * RING_SPIN4 * TAU, RING_A5 * s_light * breath * near,
                reveal, start=a0, feather=FEATHER, pull=PULL7)
    fr.stroke(cx, cy, cz, rr, wc * HALO_K7, HALO7[f], HALO_A7 * s_light * (1 + flash), reveal, a0, pull=PULL7)
    settle = 1 - SETTLE8[2] * min(1.0, max(0.0, (t - SETTLE8[0]) / (SETTLE8[1] - SETTLE8[0])))
    fr.stroke(cx, cy, cz, rr, wc, CORE7[f], min(1.0, CORE_A7 * s_light * breath * settle + 0.5 * flash * s_light),
              reveal, a0, pull=PULL7)
    if near > 0.01:   # the inner line: the second weight under the rim
        fr.stroke(cx, cy, cz, rr * INNER8[0], wc * INNER8[1], CORE7[f], INNER8[2] * s_light * near, reveal, a0,
                  pull=PULL7)
    if t < DRAW_IN and near > 0.01:
        ang = a0 + reveal * TAU
        fr.billboard(fr.small(f, 'glyph', cid & 15), (cx + math.cos(ang) * rr * 0.92, cy + 0.25, cz + math.sin(ang) * rr * 0.92),
                     TIP_HALF * max(1, sc), s_light * near)
    p0, p1, pt = PULSE7
    if 0 <= rel < pt:   # the release: the rim thrown out
        q = rel / pt
        rp = rr * (p0 + (p1 - p0) * ease(q))
        fr.stroke(cx, cy + 0.02, cz, rp, wc * HALO_K7, HALO7[f], HALO_A7 * (1 - q) * s_light, pull=PULL7)
        fr.stroke(cx, cy + 0.02, cz, rp, wc, CORE7[f], (1 - q) * s_light, pull=PULL7)
    bh, bt, ba = BURST7
    if 0 <= rel < bt:
        fr.billboard(fr.small(f, 'spark'), (cx, cy + 0.15, cz), bh * max(1, sc), ba * flash_scale * (1 - rel / bt))
    if near <= 0.01:
        return
    for k, (h, rs, at, ta, spin, kind) in enumerate(TIERS6):
        rise = ease((t - at) / 0.12)
        a = ta * s_light * rise * near
        if a > 0.01:
            fr.disc(fr.cell(f, 3), cx, cy + h * sc * rise, cz, R * rs * grow / RIM_IN_CELL, t * spin * TAU + k, a, 1, 24)
    grow_in = ease((t - 0.08) / 0.22)
    shrink = min(1, max(0, (life - t) / 0.4))
    k_s = grow_in * shrink
    if k_s > 0.01:
        sa = SIGIL_A * s_light * min(1, k_s * 1.5) * near
        sr = R * SIGIL_R6 * k_s
        fr.disc(fr.cell(f, 1), cx, cy + SIGIL_H6 * sc, cz, sr / RIM_IN_CELL, t * SIGIL_SPIN7 * TAU, sa, 1, 16)
        fr.stroke(cx, cy + SIGIL_H6 * sc, cz, sr * 1.04, wc * 0.8, HALO7[f], sa * SIGIL_RING_A8)
    p_from, p_h, p_hw, p_a, p_t = PILLAR4
    if 0 <= rel < p_t:
        fr.beam(fr.small(f, 'beam'), (cx, cy + p_from * sc, cz), p_h * sc, p_hw * sc,
                p_a * flash_scale * s_light * (1 - rel / p_t) * near, 0.0)
    ma = s_light * min(1, max(0, (t - 0.2) / 0.2)) * MOTE_A7 * near
    if ma > 0.01:
        keep, RISE = RISE, RISE5
        keep_n, MOTES = MOTES, MOTES8
        try:
            if f == 7:
                for k in range(10):
                    ang = TAU * (k / 10 + t * 0.6)
                    fr.billboard(fr.small(f, 'mote'), (cx + math.cos(ang) * R * 0.75, cy + 1.6 * sc,
                                                       cz + math.sin(ang) * R * 0.75), 0.18 * 1.2, ma)
            else:
                for k in range(MOTES8):
                    x, y, z, half, a = mote(f, k, t * MOTE_SPEED4, cid, R)
                    fr.billboard(fr.small(f, 'mote'), (cx + x, cy + y, cz + z), MOTE_HALF8 * max(1, math.sqrt(sc)),
                                 ma * a)
        finally:
            RISE = keep
            MOTES = keep_n


# v9 (round 8: v8 beat v7 on the dense army, 32 = 32): the hold beats (+25 % every 0.4 s), the release flares the halo
# half as much (Storm and Horde whited out), the yellows pulled apart (Sunforged white with a gold glow, Brassworks
# amber, Gravebound greener), the underlay's soft edge just past the rim.
CORE9 = {**CORE7, 1: 0xFFFFF0, 2: 0xD0FF60, 7: 0xFFC870}
HALO9 = {**HALO7, 1: 0xFFE070, 2: 0x7CE000, 7: 0xE08A10}
BEAT9 = (0.5, 0.4, 0.1, 0.25)
UNDER_R9 = 1.1


def v9(fr, f, P, t, life=1.5, R=CAST_RADIUS7, cid=0):
    global RISE, MOTES
    s = strength(t, life)
    if s <= 0.01:
        return
    s_light = s * crowd.get(cid, 1.0)
    zoom = 1080 / fr.cam.ppb
    near = min(1.0, max(0.0, (LOD7[1] - zoom) / (LOD7[1] - LOD7[0])))
    wc = max(CORE_W7[0], CORE_W7[1] / fr.cam.ppb)
    sc = R / CAST_RADIUS7
    ending = max(0, 1 - (life - t) / FADE)
    grow = 1 + 0.12 * ending
    breath = BREATH6[0] + BREATH6[1] * math.sin(t * TAU * BREATH6[2])
    rel = t - DRAW_IN
    flash = flash_scale * (max(0, 1 - rel / FLASH_T4) if rel >= 0 else 0)
    cx, cy, cz = P[0], P[1] + 0.05, P[2]
    reveal = min(1, t / DRAW_IN)
    a0 = sweep_start(fr.cam)
    rr = R * grow
    # the dark ground: the underlay, and a dark line under the rim (alpha blended)
    fr.disc(fr.small(f, 'shade'), cx, cy - 0.01, cz, rr * UNDER_R9, 0, UNDER8 * s, reveal, 16, shade=True, start=a0,
            feather=FEATHER, tint=tuple(float(c) for c in gen.rgb(DARK7[f])), pull=PULL7)
    fr.stroke(cx, cy, cz, rr, wc * DARK_K7, DARK7[f], DARK_A7 * s, reveal, a0, 24, shade=True, pull=PULL7)
    if t < SEED6[1]:
        fr.billboard(fr.small(f, 'spark'), (cx, cy + 0.15, cz), SEED6[0] * max(1, sc), 1 - t / SEED6[1])
    if near > 0.01:   # the rune ring's detail inside the rim
        fr.disc(fr.cell(f, 0), cx, cy, cz, rr / RIM_IN_CELL, t * RING_SPIN4 * TAU, RING_A5 * s_light * breath * near,
                reveal, start=a0, feather=FEATHER, pull=PULL7)
    fr.stroke(cx, cy, cz, rr, wc * HALO_K7, HALO9[f], HALO_A7 * s_light * (1 + 0.5 * flash), reveal, a0, pull=PULL7)
    settle = 1 - SETTLE8[2] * min(1.0, max(0.0, (t - SETTLE8[0]) / (SETTLE8[1] - SETTLE8[0])))
    b0, every, bt, amt = BEAT9
    if t > b0:   # the hold's beat
        settle *= 1 + amt * max(0.0, 1 - (((t - b0) / every) % 1) * every / bt)
    fr.stroke(cx, cy, cz, rr, wc, CORE9[f], min(1.0, CORE_A7 * s_light * breath * settle + 0.5 * flash * s_light),
              reveal, a0, pull=PULL7)
    if near > 0.01:   # the inner line: the second weight under the rim
        fr.stroke(cx, cy, cz, rr * INNER8[0], wc * INNER8[1], CORE9[f], INNER8[2] * s_light * near, reveal, a0,
                  pull=PULL7)
    if t < DRAW_IN and near > 0.01:
        ang = a0 + reveal * TAU
        fr.billboard(fr.small(f, 'glyph', cid & 15), (cx + math.cos(ang) * rr * 0.92, cy + 0.25, cz + math.sin(ang) * rr * 0.92),
                     TIP_HALF * max(1, sc), s_light * near)
    p0, p1, pt = PULSE7
    if 0 <= rel < pt:   # the release: the rim thrown out
        q = rel / pt
        rp = rr * (p0 + (p1 - p0) * ease(q))
        fr.stroke(cx, cy + 0.02, cz, rp, wc * HALO_K7, HALO9[f], HALO_A7 * (1 - q) * s_light, pull=PULL7)
        fr.stroke(cx, cy + 0.02, cz, rp, wc, CORE9[f], (1 - q) * s_light, pull=PULL7)
    bh, bt, ba = BURST7
    if 0 <= rel < bt:
        fr.billboard(fr.small(f, 'spark'), (cx, cy + 0.15, cz), bh * max(1, sc), ba * flash_scale * (1 - rel / bt))
    if near <= 0.01:
        return
    for k, (h, rs, at, ta, spin, kind) in enumerate(TIERS6):
        rise = ease((t - at) / 0.12)
        a = ta * s_light * rise * near
        if a > 0.01:
            fr.disc(fr.cell(f, 3), cx, cy + h * sc * rise, cz, R * rs * grow / RIM_IN_CELL, t * spin * TAU + k, a, 1, 24)
    grow_in = ease((t - 0.08) / 0.22)
    shrink = min(1, max(0, (life - t) / 0.4))
    k_s = grow_in * shrink
    if k_s > 0.01:
        sa = SIGIL_A * s_light * min(1, k_s * 1.5) * near
        sr = R * SIGIL_R6 * k_s
        fr.disc(fr.cell(f, 1), cx, cy + SIGIL_H6 * sc, cz, sr / RIM_IN_CELL, t * SIGIL_SPIN7 * TAU, sa, 1, 16)
        fr.stroke(cx, cy + SIGIL_H6 * sc, cz, sr * 1.04, wc * 0.8, HALO9[f], sa * SIGIL_RING_A8)
    p_from, p_h, p_hw, p_a, p_t = PILLAR4
    if 0 <= rel < p_t:
        fr.beam(fr.small(f, 'beam'), (cx, cy + p_from * sc, cz), p_h * sc, p_hw * sc,
                p_a * flash_scale * s_light * (1 - rel / p_t) * near, 0.0)
    ma = s_light * min(1, max(0, (t - 0.2) / 0.2)) * MOTE_A7 * near
    if ma > 0.01:
        keep, RISE = RISE, RISE5
        keep_n, MOTES = MOTES, MOTES8
        try:
            if f == 7:
                for k in range(10):
                    ang = TAU * (k / 10 + t * 0.6)
                    fr.billboard(fr.small(f, 'mote'), (cx + math.cos(ang) * R * 0.75, cy + 1.6 * sc,
                                                       cz + math.sin(ang) * R * 0.75), 0.18 * 1.2, ma)
            else:
                for k in range(MOTES8):
                    x, y, z, half, a = mote(f, k, t * MOTE_SPEED4, cid, R)
                    fr.billboard(fr.small(f, 'mote'), (cx + x, cy + y, cz + z), MOTE_HALF8 * max(1, math.sqrt(sc)),
                                 ma * a)
        finally:
            RISE = keep
            MOTES = keep_n


# ---------------------------------------------------------------- the scene

def ground(fr, seed=4):
    """Blocky grass on y = 0: one shade per block, the same 16 x 16 texel tile in every block, a few dirt blocks."""
    W, H = fr.cam.W, fr.cam.H
    wx, wz = fr.cam.plane(0.0, 0, 0, W, H)
    bx, bz = np.floor(wx).astype(np.int64), np.floor(wz).astype(np.int64)
    rng = np.random.default_rng(seed)
    shades = rng.random((64, 64)).astype(np.float32)
    tile = rng.random((16, 16)).astype(np.float32)
    dtile = rng.random((16, 16)).astype(np.float32)
    block = shades[bx % 64, bz % 64]
    tx, tz = np.floor((wx - bx) * 16).astype(np.int64) % 16, np.floor((wz - bz) * 16).astype(np.int64) % 16
    tex = tile[tz, tx]
    base = np.array([0.36, 0.55, 0.24], np.float32)
    img = base * (0.86 + 0.12 * block[..., None]) * (0.84 + 0.26 * tex[..., None])
    dirt = (block > 0.95)[..., None]
    img = np.where(dirt, np.array([0.47, 0.36, 0.25], np.float32) * (0.82 + 0.3 * dtile[tz, tx][..., None]), img)
    fr.img[:] = np.clip(img, 0, 1) * 0.92


def tufts(fr, seed=5, density=0.45):
    """Grass blades (as the game's grass and ferns): thin, 0.35-0.9 block tall, depth tested and depth writing, so a
    ring at ground height is cut where they stand in front of it. Drawn after the units."""
    W, H = fr.cam.W, fr.cam.H
    wx, wz = fr.cam.plane(0.0, 0, 0, W, H)
    rng = random.Random(seed)
    px = fr.cam.ppb
    bw = max(1, int(round(0.07 * px)))
    for bx in range(int(math.floor(wx.min())), int(math.ceil(wx.max()))):
        for bz in range(int(math.floor(wz.min())), int(math.ceil(wz.max()))):
            if rng.random() > density:
                continue
            fx, fz = bx + rng.random(), bz + rng.random()
            sx, sy = fr.cam.proj((fx, 0, fz))
            if not (-20 <= sx <= W + 20 and -20 <= sy <= H + 80):
                continue
            g = 0.75 + 0.4 * rng.random()
            col = np.array([0.26, 0.47, 0.17], np.float32) * g
            for _ in range(3):
                h = 0.35 + 0.55 * rng.random()
                lx, lz = (rng.random() - 0.5) * 0.4, (rng.random() - 0.5) * 0.4
                bx0, bz0 = fx + (rng.random() - 0.5) * 0.3, fz + (rng.random() - 0.5) * 0.3
                steps = max(2, int(h * px * 0.9))
                for k in range(steps + 1):
                    u = k / steps
                    Q = (bx0 + lx * u, h * u, bz0 + lz * u)
                    qx, qy = fr.cam.proj(Q)
                    d = fr.depth(Q)
                    w = max(1, int(round(bw * (1 - 0.7 * u))))
                    x0, y0 = int(qx) - w // 2, int(qy)
                    x1, y1 = x0 + w, y0 + 1
                    if x1 <= 0 or y1 <= 0 or x0 >= W or y0 >= H:
                        continue
                    x0, y0 = max(0, x0), max(0, y0)
                    m = d <= fr.udepth[y0:y1, x0:x1]
                    fr.img[y0:y1, x0:x1][m] = col * (0.8 + 0.35 * u)
                    fr.udepth[y0:y1, x0:x1][m] = d


def box(fr, P, sx, sy, sz, col):
    """A lit box from P (its foot centre), drawn with its three faces that face the camera."""
    x, y, z = P
    hx, hz = sx / 2, sz / 2
    corners = {}
    for i in (0, 1):
        for j in (0, 1):
            for k in (0, 1):
                corners[i, j, k] = fr.cam.proj((x + (2 * i - 1) * hx, y + j * sy, z + (2 * k - 1) * hz))
    im = Image.fromarray((np.clip(fr.img, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    col = np.array(gen.rgb(col))
    # faces: top, and the two side faces whose normals point at the camera (-forward)
    faces = [((0, 1, 0), [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], 1.0)]
    for n, quad, shade in (((1, 0, 0), [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], 0.78),
                           ((-1, 0, 0), [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], 0.78),
                           ((0, 0, 1), [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)], 0.62),
                           ((0, 0, -1), [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], 0.62)):
        if np.dot(n, fr.cam.f) < 0:
            faces.append((n, quad, shade))
    mk = Image.new('L', (fr.cam.W, fr.cam.H), 0)
    dm = ImageDraw.Draw(mk)
    for _, quad, shade in faces:
        c = tuple(int(v) for v in np.clip(col * shade, 0, 1) * 255)
        d.polygon([corners[q] for q in quad], fill=c, outline=tuple(int(v * 0.6) for v in c))
        dm.polygon([corners[q] for q in quad], fill=255)
    fr.img[:] = np.asarray(im, np.float32) / 255
    m = np.asarray(mk) > 0
    fr.udepth[m] = np.minimum(fr.udepth[m], fr.depth((x, y + sy / 2, z)))


def caster(fr, P, robe):
    x, y, z = P
    box(fr, (x, y, z), 0.5, 0.75, 0.3, 0x3A3A44)
    box(fr, (x, y + 0.75, z), 0.55, 0.7, 0.35, robe)
    box(fr, (x, y + 1.45, z), 0.45, 0.45, 0.45, 0xC8A07A)


def soldier(fr, P, col):
    x, y, z = P
    box(fr, (x, y, z), 0.45, 0.7, 0.28, 0x2E2E36)
    box(fr, (x, y + 0.7, z), 0.5, 0.65, 0.3, col)
    box(fr, (x, y + 1.35, z), 0.42, 0.42, 0.42, 0xB89070)


ROBES = {1: 0xE0C060, 2: 0x40604A, 3: 0x8A3A20, 4: 0x4E8A3A, 5: 0x2E6E8A, 6: 0x5A4A9A, 7: 0x9A6A30, 0: 0x888888}
NAMES = {1: 'Sunforged', 2: 'Gravebound', 3: 'Horde', 4: 'Verdant', 5: 'Light Grid', 6: 'Stormcallers', 7: 'Brassworks'}


def save(fr, path, title=None, labels=()):
    img = Image.fromarray((np.clip(fr.img, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    for text, xy in ([(title, (14, 10))] if title else []) + list(labels):
        d.text((xy[0] + 1, xy[1] + 1), text, fill=(0, 0, 0))
        d.text(xy, text, fill=(255, 255, 255))
    img.save(path)


def scene_lineup(at, draw, out, t=0.75, zoom=15):
    """Every faction's caster mid-cast in a row at zoom 15 (close)."""
    W, H = 1920, 1080
    cam = Cam(W, H, zoom, target=(0, 0.8, 0))
    fr = Frame(cam, at)
    ground(fr)
    ids = [1, 2, 3, 4, 6, 7, 5]
    labels = []
    # spread along the camera's screen-right axis, flattened to the ground
    rx, rz = cam.r[0], cam.r[2]
    n = math.hypot(rx, rz)
    rx, rz = rx / n, rz / n
    for i, f in enumerate(ids):
        off = (i - 3) * 3.6
        P = (rx * off, 0.0, rz * off)
        caster(fr, P, ROBES[f])
        sx, sy = cam.proj(P)
        labels.append((NAMES[f], (int(sx) - 30, int(sy) + 70)))
    tufts(fr, 3)
    for i, f in enumerate(ids):
        off = (i - 3) * 3.6
        draw(fr, f, (rx * off, 0.0, rz * off), t, cid=i * 5 + 1)
    save(fr, out, f'{os.path.basename(out)} - zoom {zoom}, t = {t:.2f} s of a 1.5 s cast', labels)


def scene_timing(at, draw, out, f=6, zoom=15):
    times = [0.08, 0.2, 0.34, 0.5, 0.9, 1.3]
    W, H = 1920, 640
    cam = Cam(W, H, zoom * H / 1080, target=(0, 1.0, 0))
    fr = Frame(cam, at)
    ground(fr, 7)
    rx, rz = cam.r[0], cam.r[2]
    n = math.hypot(rx, rz)
    rx, rz = rx / n, rz / n
    labels = []
    for i, t in enumerate(times):
        off = (i - 2.5) * 4.6
        P = (rx * off, 0.0, rz * off)
        caster(fr, P, ROBES[f])
        sx, sy = cam.proj(P)
        labels.append((f't = {t:.2f} s', (int(sx) - 28, H - 40)))
    tufts(fr, 8)
    for i, t in enumerate(times):
        off = (i - 2.5) * 4.6
        draw(fr, f, (rx * off, 0.0, rz * off), t, cid=7)
    save(fr, out, f'{os.path.basename(out)} - {NAMES[f]}, one 1.5 s cast, zoom {zoom}', labels)


def scene_battle(at, draw, out, zoom=30, seed=9):
    """Two armies at the default zoom: 6 casters each mid-fight, at random moments of their casts, among soldiers."""
    W, H = 1920, 1080
    cam = Cam(W, H, zoom, target=(0, 0, 0))
    fr = Frame(cam, at)
    ground(fr, 2)
    rng = random.Random(seed)
    rx, rz = cam.r[0], cam.r[2]
    n = math.hypot(rx, rz)
    rx, rz = rx / n, rz / n
    fx, fz = -rz, rx    # the screen's up, flattened

    def place(side_x, spread):
        a = side_x + (rng.random() - 0.5) * spread
        b = (rng.random() - 0.5) * 22
        return (rx * a + fx * b, 0.0, rz * a + fz * b)
    units = []
    for k in range(40):
        side = -1 if k % 2 else 1
        units.append(('s', place(side * 7, 12), 0x3A6FE0 if side < 0 else 0xC8402E))
    casts = []
    for k in range(12):
        side = -1 if k < 6 else 1
        f = 4 if side < 0 else 2
        P = place(side * 12, 8)
        units.append(('c', P, ROBES[f]))
        casts.append((f, P, rng.random() * 1.45 + 0.02, k))
    # far units first so near ones overlap them
    units.sort(key=lambda u: -np.dot(np.array(u[1]) - cam.t, cam.f))
    for kind, P, col in units:
        (caster if kind == 'c' else soldier)(fr, P, col)
    tufts(fr, 2)
    global flash_scale
    flashing = sum(1 for _, _, t, _ in casts if 0 <= t - DRAW_IN < FLASH_T)
    flash_scale = min(1.0, 3.0 / max(1, flashing))
    crowd.clear()
    for f, P, t, k in casts:
        n = sum(1 for _, Q, _, j in casts if j != k and math.hypot(P[0] - Q[0], P[2] - Q[2]) < 4)
        crowd[k * 3 + 1] = max(0.55, 1 / math.sqrt(1 + n))
    for f, P, t, k in casts:
        draw(fr, f, P, t, cid=k * 3 + 1)
    flash_scale = 1.0
    crowd.clear()
    save(fr, out, f'{os.path.basename(out)} - zoom {zoom} (default), Verdant (left) vs Gravebound (right), 12 casters')
    return fr.quads


def scene_army(at, draw, out, style, zoom=15, seed=12):
    """The eyes overlays scene, in small: a block of units 1.5 apart, casters of every faction among them, casts at
    random moments, at most 48 circles live; the radius and the crowd as the style's own client draws them."""
    W, H = 1920, 1080
    cam = Cam(W, H, zoom, target=(0, 0, 0))
    fr = Frame(cam, at)
    ground(fr, 6)
    rng = random.Random(seed)
    n = 22
    units, casts = [], []
    k = 0
    for i in range(n):
        for j in range(n):
            P = ((i - n / 2) * 1.5 + (rng.random() - 0.5) * 0.4, 0.0, (j - n / 2) * 1.5 + (rng.random() - 0.5) * 0.4)
            if rng.random() < 0.2:
                f = rng.choice([1, 2, 3, 4, 5, 6, 7])
                units.append(('c', P, ROBES[f]))
                t = rng.random() * 3.0
                if t < 1.48:
                    casts.append((f, P, t + 0.02, k))
                    k += 1
            else:
                units.append(('s', P, 0x2A3A78))
    casts = casts[:48]
    units.sort(key=lambda u: -np.dot(np.array(u[1]) - cam.t, cam.f))
    for kind, P, col in units:
        (caster if kind == 'c' else soldier)(fr, P, col)
    tufts(fr, 4)
    R = radius_for(style, zoom)
    cr, cmin = CROWD7 if style in ('v7', 'v8', 'v9') else (4.0, 0.55)
    global flash_scale
    flashing = sum(1 for _, _, t, _ in casts if 0 <= t - DRAW_IN < FLASH_T)
    flash_scale = min(1.0, 3.0 / max(1, flashing))
    crowd.clear()
    for f, P, t, kk in casts:
        nn = sum(1 for _, Q, _, j in casts if j != kk and math.hypot(P[0] - Q[0], P[2] - Q[2]) < cr)
        crowd[kk * 3 + 1] = max(cmin, 1 / math.sqrt(1 + nn))
    for f, P, t, kk in casts:
        draw(fr, f, P, t, R=R, cid=kk * 3 + 1)
    flash_scale = 1.0
    crowd.clear()
    save(fr, out, f'{os.path.basename(out)} - zoom {zoom}, {len(units)} units, {len(casts)} circles live')
    return fr.quads


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('outdir')
    ap.add_argument('--style', default='v2', choices=['v1', 'v2', 'v3', 'v4', 'v5', 'v6', 'v7', 'v8', 'v9'])
    ap.add_argument('--tag', default='')
    args = ap.parse_args()
    os.makedirs(args.outdir, exist_ok=True)
    at = gen.atlas()
    draw = {'v1': v1, 'v2': v2, 'v3': v3, 'v4': v4, 'v5': v5, 'v6': v6, 'v7': v7, 'v8': v8, 'v9': v9}[args.style]
    tag = args.tag or args.style
    scene_lineup(at, draw, os.path.join(args.outdir, f'{tag}_1_lineup_zoom15.png'))
    scene_timing(at, draw, os.path.join(args.outdir, f'{tag}_2_timing_storm.png'), 6)
    scene_timing(at, draw, os.path.join(args.outdir, f'{tag}_3_timing_horde.png'), 3)
    q = scene_battle(at, draw, os.path.join(args.outdir, f'{tag}_4_battle_zoom30.png'))
    qa = scene_army(at, draw, os.path.join(args.outdir, f'{tag}_5_army_zoom15.png'), args.style, 15)
    qb = scene_army(at, draw, os.path.join(args.outdir, f'{tag}_6_army_zoom45.png'), args.style, 45)
    print('wrote', args.outdir, tag, 'battle quads', q, 'army quads z15', qa, 'z45', qb)


if __name__ == '__main__':
    main()
