#!/usr/bin/env python3
"""
Lobby backdrop, round 6: the scene's depth planes drawn as an etching (design/lobby-backdrop.md, "Depth rules").

lovish (Oct 22): "I really want you to imitate the depths of those drawings" - many overlapping planes, a strong value
ladder (the nearest planes darkest, with the densest and finest high-contrast line work; each plane back lighter,
lower in contrast and hazier), linear perspective (the river widening toward the viewer and zig-zagging back to the
cathedral, flowers from big near blooms to specks in the distance), engraving-style rendering everywhere, the
cathedral a dark, intricate silhouette against a blood-red sky with the glass as the focal point, red the only
saturated colour. Only those qualities are taken from his references: every shape here is our own and drawn by code.

How a plane is drawn: a painter builds three maps at twice the output size - the light value V (0 black .. 1 lit), the
coverage alpha and the direction the burin follows (theta, radians: along the form) - plus a colour map. `etch()`
turns them into engraved lines on near-black: parallel lines along theta whose width swells with V (so the tone is
made only by line width, as on a copper plate), a second set across them where V is high (cross-hatching), flicked
dots where V barely rises above black (stipple), every line wobbling and broken by the paper's tooth like a hand's.
Then `plane()` applies the value ladder: the black level, the line brightness and the haze of that plane's depth.

Run by tools/gen_lobby_art.py (step "depth"); alone: python3 tools/lobby_depth.py [names...]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_lobby_art as G  # noqa: E402

OUT = G.OUT
SS = 2
ART_W = G.ART_W
PX = 1100.0                     # texture px per A (as the round 5 ground)

# ------------------------------------------------------------------ the value ladder (design/lobby-backdrop.md)
# per plane: the black between the lines, how bright a fully lit line gets, the haze colour and how much of it, the
# line period (output px) - near planes finer and harder, far planes softer and lifted toward the haze
LADDER = {
    "sky":     dict(black=(9, 6, 9), gain=0.95, haze=(0, 0, 0), hz=0.0, period=5.0),
    "cloud":   dict(black=(22, 8, 13), gain=0.85, haze=(90, 40, 48), hz=0.12, period=4.0),
    "far":     dict(black=(26, 12, 17), gain=0.62, haze=(62, 22, 30), hz=0.38, period=4.6),
    "hills":   dict(black=(18, 7, 11), gain=0.58, haze=(56, 16, 24), hz=0.24, period=4.2),
    "mid":     dict(black=(12, 9, 11), gain=0.80, haze=(46, 26, 30), hz=0.12, period=3.8),
    "ground":  dict(black=(8, 6, 8), gain=0.86, haze=(36, 20, 24), hz=0.05, period=3.6),
    "near":    dict(black=(4, 3, 5), gain=0.66, haze=(0, 0, 0), hz=0.0, period=3.2),
    "frame":   dict(black=(3, 3, 4), gain=0.62, haze=(0, 0, 0), hz=0.0, period=3.0),
}
BONE = np.array((214, 206, 192), np.float32) / 255      # the burin's light on stone and bark (warm bone)
BLOOD = np.array((205, 26, 38), np.float32) / 255       # the only saturated colour

MOON_DIR = np.array((-0.62, -0.78))                      # the light comes from the upper left (screen y down)


def smooth(e0, e1, x):
    return G.smoothstep(e0, e1, x)


def blur(a, s):
    return ndimage.gaussian_filter(np.asarray(a, np.float32), s) if s > 0 else np.asarray(a, np.float32)


def noise(h, w, cells, seed, octaves=4, wrap=False, aspect=None):
    U, V = np.meshgrid(np.linspace(0, 1, w, endpoint=False, dtype=np.float32),
                       np.linspace(0, 1, h, endpoint=False, dtype=np.float32))
    return G.fbm(U, V, cells, seed, wrap, octaves, 0.5, aspect if aspect is not None else w / h)


# ------------------------------------------------------------------ the burin

NDIR = 9


def _wobble(h, w, seed, wrap_w=None):
    """Two shared wobble fields (a hand's slow drift and its tremor) for every direction of one etch."""
    X = np.arange(w, dtype=np.float32)[None, :] * np.ones((h, 1), np.float32)
    Y = np.arange(h, dtype=np.float32)[:, None] * np.ones((1, w), np.float32)
    U, Vv = X / (wrap_w or w), Y / h
    asp = (wrap_w or w) / h
    wr = wrap_w is not None
    w1 = (G.fbm(U, Vv, 5, seed, wr, 3, 0.5, asp) - 0.5) + (G.fbm(U, Vv, 60, seed + 1, wr, 1, 0.5, asp) - 0.5) * 0.25
    w2 = (G.fbm(U, Vv, 7, seed + 2, wr, 3, 0.5, asp) - 0.5) + (G.fbm(U, Vv, 55, seed + 3, wr, 1, 0.5, asp) - 0.5) * 0.25
    return X, Y, w1, w2


def stripes(X, Y, ang, period, k, w1, w2, wob=0.8, wrap_w=None):
    """Distance (0 on a line .. 1 midway) to the nearest of parallel lines running at `ang`, wobbling like a hand's.
    `period` (px) may be a map (lines spreading toward the viewer). With wrap_w the field is periodic in x (the line
    count across the seam is rounded to a whole number)."""
    nx_, ny_ = -math.sin(ang), math.cos(ang)
    if wrap_w is not None and abs(nx_) > 1e-3:
        pm = float(np.mean(period))
        n = max(1, round(wrap_w * abs(nx_) / pm))
        nx_ = math.copysign(n * pm / wrap_w, nx_)
    wv = (w1 * math.cos(1.7 * k) + w2 * math.sin(1.7 * k)) * wob
    ph = (X * nx_ + Y * ny_) / period + wv + 0.31 * k
    return np.abs((ph % 1.0) - 0.5) * 2


def _dirset(theta, X, Y, period, w1, w2, wrap_w, value_to_hw, base_k=0):
    cov = np.zeros(theta.shape, np.float32)
    wsum = np.zeros(theta.shape, np.float32)
    for k in range(NDIR):
        phi = k * math.pi / NDIR
        wk = np.maximum(np.cos(2 * (theta - phi)), 0) ** 10
        if wk.max() < 1e-3:
            continue
        d = stripes(X, Y, phi, period, k + base_k, w1, w2, wrap_w=wrap_w)
        cov += wk * np.clip((value_to_hw - d) / 0.24 + 0.5, 0, 1)
        wsum += wk
    return cov / np.maximum(wsum, 1e-4)


def etch(V, A, theta, colour, period, seed, cross=0.62, stipple=0.10, wrap_w=None, tooth_amt=0.5, contour=1.0,
         fine=1.0):
    """Engrave a value map. Returns (rgb of the line light, line coverage 0..1). V, A, theta: HxW floats; colour:
    HxWx3 light colour of the lines; period in px of this (supersampled) canvas, a number or a map."""
    h, w = V.shape
    V = np.clip(V, 0, 1)
    X, Y, w1, w2 = _wobble(h, w, seed, wrap_w)
    # the main lines: their half width grows with the value (hairlines in the dark, fat lines in the light)
    hw = np.clip(V * 0.95 * fine, 0, 0.92)
    cov = _dirset(theta, X, Y, period, w1, w2, wrap_w, hw) * smooth(0.035, 0.10, V)
    # cross-hatching across them where the light is strongest
    if cross < 1 and V.max() > cross:
        hw2 = np.clip((V - cross) / (1 - cross) * 0.8, 0, 0.8)
        cc = _dirset(theta + math.pi / 2 + 0.35, X, Y, period * 1.15, w2, w1, wrap_w, hw2, 5)
        cov = np.maximum(cov, cc)
    # stipple: flicked dots where the value just rises from black
    if stipple > 0:
        r = np.random.default_rng(seed + 5)
        dz = (r.random((h, w)) > 0.992).astype(np.float32)
        dz = np.maximum(dz, ndimage.binary_dilation(dz > 0, iterations=1).astype(np.float32) * 0.55)
        cov = np.maximum(cov, dz * smooth(0.015, 0.06, V) * smooth(0.40, 0.10, V) * min(1.0, stipple * 6))
    # the paper's tooth breaks the lines
    tooth = np.random.default_rng(seed + 9).random((h // 2 + 1, w // 2 + 1)).astype(np.float32)
    tooth = np.asarray(Image.fromarray((tooth * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR),
                       np.float32)[:h, :w] / 255
    cov *= 1 - tooth_amt * smooth(0.6, 1.0, tooth)
    cov *= 0.82 + 0.36 * (w1 + 0.5).clip(0, 1) * 0.5
    rgb = colour * (0.55 + 0.45 * V)[..., None]
    # a bright rim where the silhouette faces the moon
    if contour > 0:
        edge = np.clip((A - blur(A, 2.5)) * 3.0, 0, 1)
        gy, gx = np.gradient(blur(A, 1.2))
        n = np.hypot(gx, gy) + 1e-6
        facing = np.clip((gx * -MOON_DIR[0] + gy * -MOON_DIR[1]) / n, 0, 1)
        cov = np.maximum(cov, edge * facing * contour * 0.9)
    return rgb, np.clip(cov, 0, 1)


def plane(rgb_lines, cov, A, ladder, tint_black=None):
    """The value ladder: black between the lines (this plane's), the lines at this plane's gain, the haze over all."""
    L = LADDER[ladder]
    black = np.array(L["black"], np.float32) / 255
    if tint_black is not None:
        black = black[None, None, :] * (1 - tint_black[1][..., None]) + tint_black[0] * tint_black[1][..., None]
    out = black + (rgb_lines * L["gain"] - black) * cov[..., None]
    haze = np.array(L["haze"], np.float32) / 255
    out = out * (1 - L["hz"]) + haze * L["hz"]
    return np.clip(out, 0, 1), A


def form_theta(V, A, default, sigma=6.0, coh_min=0.15):
    """The direction along the form: the structure tensor of the value/alpha (contours), falling back to `default`."""
    f = blur(V * A + A * 0.3, 2.0)
    gy, gx = np.gradient(f)
    jxx, jxy, jyy = blur(gx * gx, sigma), blur(gx * gy, sigma), blur(gy * gy, sigma)
    th = 0.5 * np.arctan2(2 * jxy, jxx - jyy) + math.pi / 2
    coh = np.sqrt((jxx - jyy) ** 2 + 4 * jxy ** 2) / (jxx + jyy + 1e-7)
    k = smooth(coh_min, coh_min + 0.3, coh)
    dv = default if np.ndim(default) else np.full_like(V, default)
    vx = np.cos(2 * th) * k + np.cos(2 * dv) * (1 - k)
    vy = np.sin(2 * th) * k + np.sin(2 * dv) * (1 - k)
    return 0.5 * np.arctan2(vy, vx)


def lit(mask, sigma, ambient=0.06, relief=1.0, light=(-0.55, -0.62, 0.56), facets=None):
    """Lambert shading of a mask as a raised form (a dome from its distance transform, `relief` px of height per px
    inward, plus optional facet noise) lit by the moon from the upper left and a little in front: 0..1."""
    m = mask > 0.5
    d = ndimage.distance_transform_edt(m).astype(np.float32)
    hgt = np.sqrt(d * sigma) * relief
    if facets is not None:
        hgt = hgt + facets
    hgt = blur(hgt, 1.5)
    gy, gx = np.gradient(hgt)
    L = np.array(light, np.float32)
    L /= np.linalg.norm(L)
    nz = 1.0 / np.sqrt(gx * gx + gy * gy + 1)
    lam = (-gx * L[0] - gy * L[1] + L[2]) * nz
    return np.clip(ambient + np.clip(lam, 0, 1) * (1 - ambient), 0, 1) * mask


def down(rgb, a, size):
    """Premultiplied downsample to the output size."""
    pm = np.concatenate([rgb * a[..., None], a[..., None]], -1)
    chans = [np.asarray(Image.fromarray(pm[..., i].astype(np.float32), "F").resize(size, Image.LANCZOS)) for i in range(4)]
    pm = np.stack(chans, -1)
    al = np.clip(pm[..., 3], 0, 1)
    return np.clip(pm[..., :3] / np.maximum(al, 1e-4)[..., None], 0, 1), al


def save(rgb, a, name, size):
    r, al = down(rgb, a, size)
    out = np.concatenate([r, al[..., None]], -1)
    Image.fromarray(np.round(out * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name + ".png"), optimize=True)
    print("  wrote", name, size, flush=True)


# ------------------------------------------------------------------ drawing helpers (PIL masks at SS)

class Canvas:
    """A layer in artboard coordinates: rect (x0, y0, x1, y1) in A, px per A, supersampled by SS."""

    def __init__(self, rect, px=PX, ss=SS):
        self.rect = rect
        self.k = px * ss
        self.W = int(round((rect[2] - rect[0]) * px)) * ss
        self.H = int(round((rect[3] - rect[1]) * px)) * ss
        self.size = (self.W // ss, self.H // ss)

    def P(self, x, y):
        return ((x - self.rect[0]) * self.k, (y - self.rect[1]) * self.k)

    def pts(self, ps):
        return [self.P(x, y) for x, y in ps]

    def mask(self):
        return Image.new("L", (self.W, self.H), 0)

    def grid(self):
        ys, xs = np.mgrid[0:self.H, 0:self.W].astype(np.float32)
        return self.rect[0] + xs / self.k, self.rect[1] + ys / self.k


def arr(m):
    return np.asarray(m, np.float32) / 255


def jag(ps, rr, amt, n=3):
    """Subdivide a polyline / polygon and jitter it (natural, broken edges)."""
    out = []
    for i in range(len(ps)):
        a, b = ps[i], ps[(i + 1) % len(ps)]
        for j in range(n):
            t = j / n
            x = a[0] + (b[0] - a[0]) * t
            y = a[1] + (b[1] - a[1]) * t
            if j:
                x += rr.normal(0, amt)
                y += rr.normal(0, amt)
            out.append((x, y))
    return out


def blob(cx, cy, rx, ry, rr, n=14, rough=0.22):
    ps = []
    ph = rr.uniform(0, 6.28)
    for i in range(n):
        a = i / n * 2 * math.pi
        r = 1 + rough * (rr.random() - 0.5) * 2 + 0.12 * math.sin(3 * a + ph)
        ps.append((cx + math.cos(a) * rx * r, cy + math.sin(a) * ry * r))
    return ps


def rock(cx, cy, r, rr, flat=0.45):
    """An angular rock outline: flat bottom, faceted top."""
    n = rr.integers(7, 11)
    ps = []
    for i in range(n):
        a = math.pi + i / (n - 1) * math.pi            # left .. over the top .. right
        rad = r * rr.uniform(0.72, 1.12)
        ps.append((cx + math.cos(a) * rad * rr.uniform(1.0, 1.35), cy + math.sin(a) * rad * flat * 2))
    ps.append((cx + r * 1.1, cy + r * 0.12))
    ps.append((cx - r * 1.1, cy + r * 0.12))
    return ps


# ------------------------------------------------------------------ shapes

def limb_mask(c, pts, w0, w1, rr=None, gnarl=0.0):
    """A tapering limb along a polyline (artboard pts), widths w0 -> w1 in A; knots swell it here and there."""
    m = Image.new("L", (c.W, c.H), 0)
    d = ImageDraw.Draw(m)
    n = len(pts)
    P = c.pts(pts)
    for i in range(n - 1):
        t0, t1 = i / (n - 1), (i + 1) / (n - 1)
        wa, wb = (w0 + (w1 - w0) * t0) * c.k, (w0 + (w1 - w0) * t1) * c.k
        if gnarl and rr is not None and rr.random() < gnarl:
            wa *= 1.25
        (xa, ya), (xb, yb) = P[i], P[i + 1]
        ang = math.atan2(yb - ya, xb - xa)
        nx_, ny_ = -math.sin(ang), math.cos(ang)
        d.polygon([(xa + nx_ * wa / 2, ya + ny_ * wa / 2), (xb + nx_ * wb / 2, yb + ny_ * wb / 2),
                   (xb - nx_ * wb / 2, yb - ny_ * wb / 2), (xa - nx_ * wa / 2, ya - ny_ * wa / 2)], fill=255)
        d.ellipse([xb - wb / 2, yb - wb / 2, xb + wb / 2, yb + wb / 2], fill=255)
    d.ellipse([P[0][0] - w0 * c.k / 2, P[0][1] - w0 * c.k / 2, P[0][0] + w0 * c.k / 2, P[0][1] + w0 * c.k / 2], fill=255)
    return m


def wander(x, y, ang, length, steps, rr, bend=0.25, up_bias=0.0):
    """A wandering path (a branch, a root, a stem) from (x, y) heading `ang` (radians, screen y down)."""
    pts = [(x, y)]
    seg = length / steps
    for _ in range(steps):
        ang += rr.normal(0, bend) + up_bias * math.sin(ang + math.pi / 2) * -0.0
        x += math.cos(ang) * seg
        y += math.sin(ang) * seg
        pts.append((x, y))
    return pts, ang


def tree(L, x, ybase, height, width, rr, lean=0.0, albedo=0.45, depth=4, crown=0.55, colour=BONE, roots=True,
         dead=False, side_bias=0.0):
    """A gnarled tree: the trunk (its own stamp), forking limbs (each stamped, so each is shaded and outlined on its
    own), roots spreading at the foot. Coordinates in A of the layer's canvas."""
    c = L.c
    limbs = []
    tpts, ang = wander(x, ybase, -math.pi / 2 + lean, height, 12, rr, 0.11)
    limbs.append((tpts, width, width * 0.42, 0))
    if roots:
        for k in range(rr.integers(3, 6)):
            a0 = math.pi * rr.uniform(0.04, 0.28)
            rp, _ = wander(x + rr.normal(0, width * 0.2), ybase - width * 0.15, a0 if k % 2 else math.pi - a0,
                           width * rr.uniform(0.9, 2.2), 5, rr, 0.25)
            limbs.append((rp, width * 0.42, width * 0.08, 1))

    def fork(pts, w, a, lvl):
        if lvl >= depth or w < 0.0025:
            return
        nb = 2 if lvl < 2 else rr.integers(1, 3)
        for b in range(nb):
            i = rr.integers(len(pts) // 2, len(pts))
            sx_, sy_ = pts[min(i, len(pts) - 1)]
            spread = rr.uniform(0.35, 0.95) * (1 if (b + lvl) % 2 else -1) + side_bias
            ln = height * crown * (0.62 ** lvl) * rr.uniform(0.7, 1.1)
            bp, ba = wander(sx_, sy_, a + spread, ln, 6, rr, 0.22)
            limbs.append((bp, w * 0.62, w * 0.62 * 0.35, 0))
            fork(bp, w * 0.62, ba, lvl + 1)
    fork(tpts, width * 0.7, ang, 0)
    for pts, w0, w1, kind in limbs:
        m = limb_mask(c, pts, w0, w1, rr, 0.15)
        th = None
        L.stamp(m, albedo=albedo * (0.85 if kind else 1.0), colour=colour, relief=2.6, theta=None, outline=1.0)
    return limbs


def foliage(L, cx, cy, rx, ry, rr, albedo=0.38, n=None, leaf=0.010, colour=None, droop=0.0):
    """A mass of leaves: a clump of overlapping leafy lobes (each a blob with a serrated rim), shaded as one mound with
    leaf-sized bumps."""
    c = L.c
    m = Image.new("L", (c.W, c.H), 0)
    d = ImageDraw.Draw(m)
    n = n or int(14 + 30 * rx * ry / (0.1 * 0.1))
    for _ in range(min(n, 160)):
        a = rr.uniform(0, 2 * math.pi)
        r = math.sqrt(rr.random())
        x, y = cx + math.cos(a) * rx * r, cy + math.sin(a) * ry * r + droop * r * r
        s_ = rr.uniform(0.5, 1.0) * min(rx, ry) * 0.42
        ps = blob(x, y, s_ * rr.uniform(0.9, 1.4), s_, rr, n=18, rough=0.35)
        d.polygon(c.pts(ps), fill=255)
    # serrate: leaves along the rim
    ma = arr(m)
    edge = (ma > 0.5) & ~ndimage.binary_erosion(ma > 0.5, iterations=3)
    ys, xs = np.nonzero(edge)
    if len(ys):
        sel = rr.choice(len(ys), size=min(len(ys), 900), replace=False)
        lk = leaf * c.k
        for i in sel:
            a = rr.uniform(0, 2 * math.pi)
            x, y = xs[i], ys[i]
            d.polygon([(x, y), (x + math.cos(a) * lk + math.cos(a + 1.3) * lk * 0.35, y + math.sin(a) * lk + math.sin(a + 1.3) * lk * 0.35),
                       (x + math.cos(a) * lk * 1.6, y + math.sin(a) * lk * 1.6),
                       (x + math.cos(a) * lk + math.cos(a - 1.3) * lk * 0.35, y + math.sin(a) * lk + math.sin(a - 1.3) * lk * 0.35)], fill=255)
    ma = arr(m)
    bumps = blur((np.random.default_rng(rr.integers(1 << 30)).random(ma.shape) > 0.985).astype(np.float32), leaf * c.k * 0.6)
    bumps = bumps / (bumps.max() + 1e-6) * leaf * c.k * 2.5
    L.stamp(ma, albedo=albedo, colour=colour if colour is not None else BONE * np.array((0.92, 0.95, 0.88), np.float32),
            relief=1.6, facets=bumps, outline=0.8, theta=leaf_theta(c, leaf, rr))


def leaf_theta(c, leaf, rr):
    """Burin directions for foliage: each leaf-sized patch cut in its own direction (a woodcut's leaves)."""
    cell = max(3, int(leaf * c.k * 1.4))
    gh, gw = c.H // cell + 2, c.W // cell + 2
    g = rr.uniform(-math.pi / 2, math.pi / 2, (gh, gw)).astype(np.float32)
    jit = (np.random.default_rng(rr.integers(1 << 30)).random((c.H, c.W)) * 0.6).astype(np.float32)
    ys = (np.arange(c.H)[:, None] / cell + jit[:, :1] * 0.0).astype(np.int32)
    xs = (np.arange(c.W)[None, :] / cell).astype(np.int32)
    # warp the grid so the patches are irregular
    wy = (blur(np.random.default_rng(rr.integers(1 << 30)).random((c.H, c.W)), cell) - 0.5) * cell * 6
    wx = (blur(np.random.default_rng(rr.integers(1 << 30)).random((c.H, c.W)), cell) - 0.5) * cell * 6
    yy = np.clip(((np.arange(c.H)[:, None] + wy) / cell).astype(np.int32), 0, gh - 1)
    xx = np.clip(((np.arange(c.W)[None, :] + wx) / cell).astype(np.int32), 0, gw - 1)
    return g[yy, xx]


def gravestone(L, x, ybase, wd, ht, rr, lean=0.0, kind=0, albedo=0.55):
    """A leaning gravestone: round, pointed or cross-topped, chipped, with a carved cross."""
    c = L.c
    ps = []
    if kind == 0:      # round top
        ps = [(x - wd / 2, ybase), (x - wd / 2, ybase - ht + wd / 2)]
        for i in range(9):
            a = math.pi + i / 8 * math.pi
            ps.append((x + math.cos(a) * wd / 2, ybase - ht + wd / 2 + math.sin(a) * wd / 2))
        ps += [(x + wd / 2, ybase)]
    elif kind == 1:    # gothic point
        ps = [(x - wd / 2, ybase), (x - wd / 2, ybase - ht * 0.72), (x - wd * 0.2, ybase - ht * 0.93), (x, ybase - ht),
              (x + wd * 0.2, ybase - ht * 0.93), (x + wd / 2, ybase - ht * 0.72), (x + wd / 2, ybase)]
    else:              # a cross
        a_, b_ = wd * 0.18, ht * 0.62
        ps = [(x - a_, ybase), (x - a_, ybase - b_), (x - wd / 2, ybase - b_), (x - wd / 2, ybase - b_ - 2 * a_),
              (x - a_, ybase - b_ - 2 * a_), (x - a_, ybase - ht), (x + a_, ybase - ht), (x + a_, ybase - b_ - 2 * a_),
              (x + wd / 2, ybase - b_ - 2 * a_), (x + wd / 2, ybase - b_), (x + a_, ybase - b_), (x + a_, ybase)]
    cs, sn = math.cos(lean), math.sin(lean)
    ps = [(x + (px - x) * cs - (py - ybase) * sn, ybase + (px - x) * sn + (py - ybase) * cs) for px, py in ps]
    ps = jag(ps, rr, wd * 0.025, 2)
    m = Image.new("L", (c.W, c.H), 0)
    ImageDraw.Draw(m).polygon(c.pts(ps), fill=255)
    L.stamp(m, albedo=albedo, relief=1.4, theta=math.pi / 2 + lean, outline=1.0)
    if kind != 2:
        # the carved cross: a dark incision
        cm = Image.new("L", (c.W, c.H), 0)
        dc = ImageDraw.Draw(cm)
        cxp, cyp = c.P(x + math.sin(-lean) * ht * 0.55, ybase - ht * 0.55)
        ww = max(2, wd * 0.07 * c.k)
        dc.line([(cxp, cyp - ht * 0.18 * c.k), (cxp, cyp + ht * 0.2 * c.k)], fill=255, width=int(ww))
        dc.line([(cxp - wd * 0.2 * c.k, cyp - ht * 0.06 * c.k), (cxp + wd * 0.2 * c.k, cyp - ht * 0.06 * c.k)], fill=255, width=int(ww))
        cma = arr(cm)
        L.ink = np.maximum(L.ink, cma * 0.9)


def fence(L, x0, x1, ybase, ht, rr, lean=0.0, broken=(), albedo=0.32):
    """Wrought iron railings: bars with spear tips, two rails, a bar or two missing or bent."""
    c = L.c
    m = Image.new("L", (c.W, c.H), 0)
    d = ImageDraw.Draw(m)
    n = int((x1 - x0) / (ht * 0.16))
    bw = max(2, int(ht * 0.022 * c.k))
    yb = lambda x: ybase + (x - x0) * lean
    for i in range(n + 1):
        if i in broken:
            continue
        x = x0 + (x1 - x0) * i / n
        tilt = rr.normal(0, 0.03) + (0.25 if rr.random() < 0.08 else 0)
        top = (x + math.sin(tilt) * ht, yb(x) - ht * math.cos(tilt) * rr.uniform(0.94, 1.02))
        d.line([c.P(x, yb(x)), c.P(*top)], fill=255, width=bw)
        tx, ty = c.P(*top)
        s_ = ht * 0.06 * c.k
        d.polygon([(tx, ty - s_ * 1.8), (tx + s_ * 0.7, ty), (tx, ty + s_ * 0.4), (tx - s_ * 0.7, ty)], fill=255)
    for f in (0.18, 0.80):
        d.line([c.P(x0, yb(x0) - ht * f), c.P(x1, yb(x1) - ht * f)], fill=255, width=bw)
    L.stamp(arr(m), albedo=albedo, relief=0.6, theta=math.pi / 2, outline=0.6)


def rocks(L, cx, cy, spread, n, r, rr, albedo=0.5, flat=0.45, colour=BONE):
    for _ in range(n):
        x = cx + rr.normal(0, spread)
        y = cy + abs(rr.normal(0, spread * 0.25))
        rad = r * rr.uniform(0.5, 1.3)
        m = Image.new("L", (L.c.W, L.c.H), 0)
        ImageDraw.Draw(m).polygon(L.c.pts(jag(rock(x, y, rad, rr, flat), rr, rad * 0.05, 2)), fill=255)
        fac = blur(np.random.default_rng(rr.integers(1 << 30)).random((L.c.H, L.c.W)).astype(np.float32), rad * L.c.k * 0.25) * rad * L.c.k * 3
        L.stamp(m, albedo=albedo * rr.uniform(0.8, 1.15), relief=2.4, facets=fac, colour=colour, outline=1.0, rim=0.45)


def grass(L, x0, x1, ybase, ht, n, rr, albedo=0.35, lean=0.0, colour=None, ytop_jit=0.3):
    """A row of grass blades and seed heads (thin tapering strokes)."""
    c = L.c
    m = Image.new("L", (c.W, c.H), 0)
    d = ImageDraw.Draw(m)
    for _ in range(n):
        x = rr.uniform(x0, x1)
        y = ybase + rr.normal(0, ht * 0.08)
        h_ = ht * rr.uniform(1 - ytop_jit, 1 + ytop_jit)
        a = -math.pi / 2 + lean + rr.normal(0, 0.28)
        pts, _ = wander(x, y, a, h_, 5, rr, 0.08)
        P = c.pts(pts)
        w0 = max(1.5, ht * 0.05 * c.k)
        for i in range(len(P) - 1):
            wd = w0 * (1 - i / (len(P) - 1))
            d.line([P[i], P[i + 1]], fill=255, width=max(1, int(round(wd))))
    L.stamp(arr(m), albedo=albedo, relief=0.5, theta=-math.pi / 2 + lean + math.pi, outline=0.3,
            colour=colour if colour is not None else BONE)


# ------------------------------------------------------------------ a layer being drawn

class Layer:
    """Shapes stamped back to front (painter's order) into the maps etch() needs: alpha, height (for the moon's
    shading), albedo, line colour, burin direction (NaN = follow the form), the black's tint, a value override."""

    def __init__(self, c):
        self.c = c
        h, w = c.H, c.W
        self.A = np.zeros((h, w), np.float32)
        self.Hg = np.zeros((h, w), np.float32)
        self.alb = np.zeros((h, w), np.float32)
        self.col = np.zeros((h, w, 3), np.float32)
        self.th = np.full((h, w), np.nan, np.float32)
        self.blk = np.zeros((h, w, 3), np.float32)      # tint of the black between the lines
        self.blk_k = np.zeros((h, w), np.float32)
        self.Vo = np.full((h, w), np.nan, np.float32)   # explicit value (water, sky)
        self.ink = np.zeros((h, w), np.float32)          # carved outlines between overlapping shapes

    def stamp(self, mask, albedo=0.6, colour=BONE, relief=2.0, facets=None, theta=None, alpha=1.0, outline=1.0,
              black=None, value=None, rim=0.8, bbox=None, soft=0.0):
        """Stamp one shape (a full-size L mask image or array). Its height is a dome from its own distance transform
        (relief px per px inward, plus facets), so it is shaded on its own and outlined against what lies behind."""
        m = arr(mask) if isinstance(mask, Image.Image) else mask
        if bbox is None:
            ys, xs = np.nonzero(m > 0.02)
            if len(ys) == 0:
                return
            pad = 6
            y0, y1 = max(0, ys.min() - pad), min(self.c.H, ys.max() + pad + 1)
            x0, x1 = max(0, xs.min() - pad), min(self.c.W, xs.max() + pad + 1)
        else:
            x0, y0, x1, y1 = bbox
        mm = m[y0:y1, x0:x1]
        k = mm * alpha
        sl = (slice(y0, y1), slice(x0, x1))
        if soft > 0:
            hg = blur(mm, soft) * soft * relief * 3.0
        else:
            d = ndimage.distance_transform_edt(mm > 0.5).astype(np.float32)
            hg = np.sqrt(d * 6.0) * relief
        if facets is not None:
            hg = hg + (facets[sl] if np.ndim(facets) == 2 and facets.shape == m.shape else facets)
        self.Hg[sl] = self.Hg[sl] * (1 - k) + hg * k
        self.alb[sl] = self.alb[sl] * (1 - k) + albedo * k if np.ndim(albedo) == 0 else \
            self.alb[sl] * (1 - k) + albedo[sl] * k
        cc = np.asarray(colour, np.float32)
        cc = cc[sl] if cc.ndim == 3 else cc
        self.col[sl] = self.col[sl] * (1 - k[..., None]) + cc * k[..., None]
        if theta is None:
            self.th[sl] = np.where(k > 0.5, np.nan, self.th[sl])
        else:
            tt = theta[sl] if np.ndim(theta) == 2 else theta
            self.th[sl] = np.where(k > 0.5, tt, self.th[sl])
        if black is not None:
            bc, bk = black
            self.blk[sl] = self.blk[sl] * (1 - k[..., None]) + np.asarray(bc, np.float32) * k[..., None]
            self.blk_k[sl] = self.blk_k[sl] * (1 - k) + bk * k
        else:
            self.blk_k[sl] *= 1 - k
        if value is not None:
            vv = value[sl] if np.ndim(value) == 2 else value
            self.Vo[sl] = np.where(k > 0.3, vv, self.Vo[sl])
        else:
            self.Vo[sl] = np.where(k > 0.3, np.nan, self.Vo[sl])
        # its outline: carved dark round the shape, except a rim of light on the side facing the moon
        if outline > 0 and mm.shape[0] > 8 and mm.shape[1] > 8:
            edge = np.clip((mm - blur(mm, 1.6)) * 3.2, 0, 1)
            gy, gx = np.gradient(blur(mm, 1.5))
            n = np.hypot(gx, gy) + 1e-6
            facing = np.clip((gx * -MOON_DIR[0] + gy * -MOON_DIR[1]) / n, 0, 1)
            self.ink[sl] = np.maximum(self.ink[sl] * (1 - k), edge * outline * (1 - facing * rim))
            self.rimm = getattr(self, "rimm", np.zeros_like(self.A))
            self.rimm[sl] = np.maximum(self.rimm[sl] * (1 - k), edge * facing * rim)
        self.A[sl] = np.maximum(self.A[sl], k)

    def render(self, ladder, period, seed, default_theta=0.0, shade_relief=1.0, cross=0.62, stipple=0.1,
               wrap_w=None, light=(-0.55, -0.62, 0.56), ambient=0.05, gamma=1.0, ladder_rows=None):
        """Shade (moon lambert on the height), etch and grade. Returns (rgb, alpha) at canvas size."""
        hg = blur(self.Hg, 1.2) * shade_relief
        gy, gx = np.gradient(hg)
        L = np.array(light, np.float32)
        L /= np.linalg.norm(L)
        nz = 1.0 / np.sqrt(gx * gx + gy * gy + 1)
        lam = np.clip((-gx * L[0] - gy * L[1] + L[2]) * nz, 0, 1)
        V = (ambient + lam * (1 - ambient)) * self.alb
        V = np.where(np.isnan(self.Vo), V, self.Vo)
        V = np.clip(V, 0, 1) ** gamma
        rim = getattr(self, "rimm", np.zeros_like(V))
        V = np.maximum(V * (1 - self.ink * 0.95), rim * 0.85)
        ft = form_theta(hg, np.ones_like(hg), default_theta, sigma=5)
        th = np.where(np.isnan(self.th), ft, self.th)
        rgb, cov = etch(V, self.A, th, self.col, period, seed, cross=cross, stipple=stipple, wrap_w=wrap_w, contour=0)
        if ladder_rows is None:
            out, _ = plane(rgb, cov, self.A, ladder, (self.blk, self.blk_k))
        else:
            out = ladder_rows(rgb, cov, self.blk, self.blk_k)
        return out, self.A


def plane_rows(rgb, cov, blk, blk_k, c, stops):
    """The value ladder varying down a ground layer: stops = [(artboard y, ladder name)], blended between."""
    ys = c.rect[1] + (np.arange(c.H, dtype=np.float32) + 0.5) / c.k
    keys = ("black", "gain", "haze", "hz")
    vals = {}
    for kk in keys:
        pts = [(y, np.array(LADDER[n][kk], np.float32) if kk in ("black", "haze") else LADDER[n][kk]) for y, n in stops]
        out = []
        for y in ys:
            if y <= pts[0][0]:
                out.append(pts[0][1])
                continue
            if y >= pts[-1][0]:
                out.append(pts[-1][1])
                continue
            for (ya, va), (yb, vb) in zip(pts, pts[1:]):
                if ya <= y <= yb:
                    t = (y - ya) / (yb - ya)
                    out.append(va * (1 - t) + vb * t)
                    break
        vals[kk] = np.array(out, np.float32)
    black = vals["black"][:, None, :] / 255
    black = black * (1 - blk_k[..., None]) + blk * blk_k[..., None]
    gain = vals["gain"][:, None, None]
    out = black + (rgb * gain - black) * cov[..., None]
    hz = vals["hz"][:, None, None]
    out = out * (1 - hz) + vals["haze"][:, None, :] / 255 * hz
    return np.clip(out, 0, 1)


# ------------------------------------------------------------------ sky, clouds, far hills

GLOW_C = (0.36, 0.47)          # the blood-red light behind the cathedral (artboard)


def sky_value(X, Y):
    """The sky's light: near-black overhead, a blood-red glow low behind the cathedral and along the horizon, the moon's
    pale halo upper left. Returns (V, red weight, moon weight)."""
    mx, my, mr = G.MOON
    gx, gy = GLOW_C
    glow = np.exp(-(((X - gx) / 0.50) ** 2 + ((Y - gy) / 0.36) ** 2) * 1.0)
    horizon = np.exp(-((Y - 0.64) / 0.12) ** 2) * 0.5
    red = np.clip(glow * 0.95 + horizon, 0, 1)
    dm = np.hypot(X - mx, (Y - my) * 1.05)
    moonw = np.clip(np.exp(-((dm - mr) / 0.10).clip(0) ** 2 * 1.0) * (dm > mr * 0.9), 0, 1)
    top = smooth(0.0, 0.55, Y)
    V = np.clip(0.03 + red * 0.85 * (0.30 + 0.70 * top) + moonw * 0.20, 0, 1)
    return V, red, moonw


def sky():
    c = Canvas((-ART_W / 2, 0.0, ART_W / 2, 1.0), px=1000)
    X, Y = c.grid()
    V, red, moonw = sky_value(X, Y)
    # streaks: thin horizontal bands of cloud-haze, sky lines bunching
    st = noise(c.H, c.W, 6, 501, 4, aspect=c.W / c.H * 0.18)
    V = V * (0.75 + 0.5 * st)
    pale = np.array((226, 214, 190), np.float32) / 255
    colour = BLOOD[None, None, :] * red[..., None] + pale * moonw[..., None] + np.array((120, 96, 100), np.float32) / 255 * \
        np.clip(1 - red - moonw, 0, 1)[..., None]
    # the burin's path: rings round the moon near it, long curves following the glow's contours elsewhere, so the sky
    # never reads as level scanlines
    mx, my, mr = G.MOON
    t_moon = np.arctan2(Y - my, X - mx) + math.pi / 2
    gx, gy = GLOW_C
    t_glow = np.arctan2((Y - gy) / 0.36 ** 2, (X - gx) / 0.50 ** 2) + math.pi / 2
    wm = np.exp(-(np.hypot(X - mx, Y - my) / 0.42) ** 2) * 0.0      # (rings round the moon made moire; left out)
    jit = (noise(c.H, c.W, 3, 502, 2) - 0.5) * 0.35
    vx = np.cos(2 * t_moon) * wm + np.cos(2 * t_glow) * (1 - wm) * 0.8 + 0.2 * (1 - wm)
    vy = np.sin(2 * t_moon) * wm + np.sin(2 * t_glow) * (1 - wm) * 0.8
    theta = 0.5 * np.arctan2(vy, vx) + jit
    A = np.ones_like(V)
    rgb, cov = etch(V, A, theta, colour, 4.2 * SS, 503, cross=0.95, stipple=0.35, contour=0, tooth_amt=0.65)
    blk = np.array((48, 6, 12), np.float32) / 255
    out, _ = plane(rgb, cov, A, "sky", (blk, np.clip(red * 1.1, 0, 1)))
    save(out, A, "sky", c.size)


def _cloud_bank(L, x, y, length, height, rr, wrap, n=None):
    """One bank in three tiers, laid back to front like a pen drawing of cumulus: small domes on top (behind), larger
    lobes in front, a long flat belly; each lobe stamped on its own, so each is shaded by the moon and inked against
    the one behind it."""
    c = L.c
    for tier in (2, 1, 0):
        span = length * (1 - 0.28 * tier)
        cnt = max(2, int(span / height * (1.3 - 0.25 * tier)))
        for i in range(cnt):
            t = (i + rr.uniform(0.2, 0.8)) / cnt
            cx = x + (t - 0.5) * span
            env = math.sin(math.pi * t) ** 0.5
            r = height * rr.uniform(0.45, 0.8) * (1 - 0.22 * tier) * (0.55 + 0.45 * env)
            cy = y - height * (0.15 + 0.42 * tier) * (0.4 + 0.6 * env) - r * 0.25
            for dx in ((-ART_W, 0.0, ART_W) if wrap else (0.0,)):
                ps = blob(cx + dx, cy, r * rr.uniform(1.05, 1.35), r, rr, 30, 0.05)
                if tier == 0:
                    ps = [(px_, min(py_, y)) for px_, py_ in ps]
                m = Image.new("L", (c.W, c.H), 0)
                ImageDraw.Draw(m).polygon(c.pts(ps), fill=255)
                if arr(m).max() == 0:
                    continue
                L.stamp(m, albedo=rr.uniform(0.9, 1.1) * (1.0 - 0.2 * (tier == 0)), relief=2.2, outline=0.55, rim=1.0,
                        soft=r * c.k * 0.45, theta=0.12 * (1 if tier % 2 else -1),
                        colour=np.array((200, 172, 170), np.float32) / 255, black=((0.085, 0.03, 0.045), 1.0))


def clouds():
    """Two wrapping cloud layers drawn as soft masses, not outlined lobes: a density field of overlapping domes (tiers
    of small domes over larger lobes over a flat belly), eaten at the edges by noise so the tops break into wisps; the
    moon lights their upper-left shoulders, the bellies go dark; the burin lays level lines that thicken with the light,
    the edges stippled away. The far banks sit darker than the red glow behind them, so they read as shapes."""
    for name, rect, seed, banks, lad, alpha_max in (
            ("clouds_far", (-ART_W / 2, 0.28, ART_W / 2, 0.80), 601,
             [(-0.80, 0.42, 0.70, 0.035), (0.80, 0.47, 0.80, 0.040), (-0.25, 0.30, 0.45, 0.022),
              (-0.55, 0.57, 0.60, 0.030), (0.95, 0.30, 0.40, 0.022)], "cloud", 0.90),
            ("clouds_near", (-ART_W / 2, 0.46, ART_W / 2, 0.84), 602,
             [(-0.70, 0.61, 0.70, 0.018), (0.95, 0.62, 0.60, 0.016)], "hills", 0.8)):
        rr = np.random.default_rng(seed)
        c = Canvas(rect, px=880)
        X, Y = c.grid()
        D = np.zeros_like(X)
        belly = np.zeros_like(X)
        for (x, y, length, height) in banks:
            # a torn stratus band: a long lens of cloud, thicker in the middle, its upper edge in low swells
            bank = np.zeros_like(X)
            for dx in (-ART_W, 0.0, ART_W):
                t = (X - x - dx) / (length / 2)
                env = np.clip(1 - t * t, 0, 1) ** 0.7
                swell = 1 + 0.6 * np.clip(np.sin((X - dx) * 38 + x * 9), 0, 1) ** 2 + 0.4 * np.sin((X - dx) * 11 + y * 7)
                yc = y - height * 0.3 * env
                bank = np.maximum(bank, env * np.exp(-((Y - yc) / (height * 0.55 * swell * (0.4 + 0.6 * env))) ** 2))
            D = np.maximum(D, bank)
        U = (X - rect[0]) / ART_W
        erode = G.fbm(U, (Y - rect[1]) / (rect[3] - rect[1]), 10, seed + 1, True, 4, 0.55, ART_W / (rect[3] - rect[1]))
        streak = G.fbm(U * 1.0, (Y - rect[1]) / (rect[3] - rect[1]) * 6.0, 6, seed + 4, True, 3, 0.5, ART_W / (rect[3] - rect[1]))
        Dn = D * (0.35 + 1.1 * erode) * (0.6 + 0.7 * streak)
        A = smooth(0.32, 0.62, Dn) * alpha_max
        hg = blur(np.minimum(Dn, 1.6), 14) * 1400
        gy, gx = np.gradient(hg)
        Lv = np.array((-0.35, -0.85, 0.40), np.float32)
        Lv /= np.linalg.norm(Lv)
        nz = 1 / np.sqrt(gx * gx + gy * gy + 1)
        lam = np.clip((-gx * Lv[0] - gy * Lv[1] + Lv[2]) * nz, 0, 1)
        V = np.clip(0.04 + 0.62 * lam ** 1.6, 0, 1) * smooth(0.25, 0.7, Dn)
        theta = (G.fbm(U, Y * 3, 3, seed + 2, True, 2) - 0.5) * 0.5
        colour = np.broadcast_to(np.array((206, 178, 176), np.float32) / 255, V.shape + (3,))
        rgb, cov = etch(V, A, theta, colour, 3.6 * SS, seed + 3, cross=0.6, stipple=0.3, wrap_w=c.W, contour=0.6)
        out, _ = plane(rgb, cov, A, lad)
        save(out, A, name, c.size)


def hills():
    """Far hills and a forest edge on the horizon, hazy: the plane between the far spires and the cathedral."""
    rr = np.random.default_rng(611)
    c = Canvas((-ART_W / 2, 0.50, ART_W / 2, 0.74), px=1000)
    L = Layer(c)
    # a far ridge (lighter, softer) and a nearer one carrying a forest edge
    for k, (base, amp, alb) in enumerate(((0.625, 0.060, 0.62), (0.672, 0.040, 0.46))):
        xs = np.linspace(-ART_W / 2 - 0.05, ART_W / 2 + 0.05, 140)
        ys = base - amp * (0.5 + 0.5 * np.sin(xs * (2.3 + k) + k * 1.7)) * (0.55 + 0.45 * np.sin(xs * 6.1 + 2 * k)) \
            - amp * 0.35 * np.clip(np.sin(xs * 13.0 + k), 0, 1) ** 3
        ys = ys - 0.006 * rr.random(len(xs))
        ps = list(zip(xs, ys)) + [(ART_W / 2 + 0.05, 0.75), (-ART_W / 2 - 0.05, 0.75)]
        m = Image.new("L", (c.W, c.H), 0)
        ImageDraw.Draw(m).polygon(c.pts(ps), fill=255)
        L.stamp(m, albedo=alb, relief=3.0, outline=0.7, colour=np.array((200, 176, 176), np.float32) / 255)
        if k == 1:
            # the forest edge: pointed firs and round crowns, small (far), in clumps with gaps
            for x in np.arange(-ART_W / 2, ART_W / 2, 0.0045):
                if math.sin(x * 9.0) + 0.6 * math.sin(x * 23 + 1) < -0.2:
                    continue
                yb = float(np.interp(x, xs, ys)) + 0.004
                ht = rr.uniform(0.012, 0.03) * (1.8 if math.sin(x * 4.0 + 1) > 0.6 else 1.0)
                m = Image.new("L", (c.W, c.H), 0)
                d = ImageDraw.Draw(m)
                if rr.random() < 0.6:
                    d.polygon(c.pts([(x - ht * 0.22, yb), (x, yb - ht), (x + ht * 0.22, yb)]), fill=255)
                else:
                    d.ellipse([*c.P(x - ht * 0.3, yb - ht * 0.8), *c.P(x + ht * 0.3, yb)], fill=255)
                L.stamp(m, albedo=0.16, relief=1.0, outline=0.6, rim=1.0, colour=np.array((190, 168, 168), np.float32) / 255,
                        black=((0.03, 0.012, 0.02), 1.0))
    out, a = L.render("hills", 4.0 * SS, 612, default_theta=0.0)
    save(out, a, "hills_far", c.size)


# ------------------------------------------------------------------ the ground, the river, the flowers

HOR = 0.62


def depth_s(y):
    """0 at the horizon .. 1 at the bottom edge: how near a point of the ground is (perspective scale)."""
    return np.clip((np.asarray(y, np.float32) - HOR) / (1.0 - HOR), 0.02, 1.4)


TERRAIN = [(-1.25, 0.716), (-1.05, 0.694), (-0.92, 0.702), (-0.70, 0.712), (-0.52, 0.690), (-0.36, 0.700),
           (-0.20, 0.706), (-0.04, 0.692), (0.06, 0.684), (0.12, 0.690), (0.30, 0.693), (0.48, 0.690),
           (0.56, 0.682), (0.66, 0.700), (0.78, 0.690), (0.92, 0.708), (1.06, 0.696), (1.25, 0.704)]


def terrain_top(x):
    xs, ys = zip(*TERRAIN)
    return np.interp(x, xs, ys)


GROUND_STOPS = [(0.66, "hills"), (0.71, "mid"), (0.80, "ground"), (0.95, "near")]


def ground():
    rr = np.random.default_rng(701)
    c = Canvas((-ART_W / 2, 0.56, ART_W / 2, 1.0), px=1100)
    L = Layer(c)
    X, Y = c.grid()
    ds = depth_s(Y)
    # --- the land: dark earth drawn in level lines that spread toward the viewer (the plane's perspective), moonlit
    # swells and dark hollows, its light falling off toward the viewer (the nearest plane darkest)
    top = terrain_top(X[0])
    land = blur((Y >= top[None, :]).astype(np.float32), 1.0)
    swell = G.fbm(X * 2.2, Y * 7.0, 2, 702, False, 4)
    alb = (0.46 - 0.34 * ds) * (0.30 + 1.1 * smooth(0.35, 0.75, swell))
    th = (G.fbm(X * 3, Y * 10, 2, 703, False, 3) - 0.5) * 0.35
    L.stamp(land, albedo=alb, colour=np.array((186, 180, 170), np.float32) / 255, relief=0.0, theta=th, outline=0.0)
    period = (2.4 + 7.0 * ds ** 1.5) * SS
    grass_tufts(L, 9000, rr, (-ART_W / 2, 0.69, ART_W / 2, 1.0))
    # --- a churchyard far off on the left: tiny gravestones, a railing, a dead tree, a far wood
    for k in range(18):
        gx = rr.uniform(-0.92, -0.42)
        gy = terrain_top(gx) + rr.uniform(0.004, 0.03)
        sc = float(depth_s(gy))
        gravestone(L, gx, gy, 0.022 * sc, 0.05 * sc, rr, rr.normal(0, 0.12), int(rr.integers(0, 3)), albedo=0.55)
    fence(L, -0.80, -0.55, terrain_top(-0.55) + 0.022, 0.03, rr, lean=-0.01)
    tree(L, -0.62, terrain_top(-0.62) + 0.012, 0.13, 0.006, rr, lean=0.08, albedo=0.5, depth=4, crown=0.6, roots=False)
    for k in range(10):
        tx = rr.uniform(-1.1, -0.2)
        foliage(L, tx, terrain_top(tx) - 0.008, rr.uniform(0.02, 0.05), rr.uniform(0.012, 0.02), rr, albedo=0.45,
                leaf=0.003, n=12)
    # --- the rise under the cathedral: ledges of rock in rows, larger as they come forward; the door's steps
    for row in range(8):
        y = 0.695 + row * 0.0135
        sc = float(depth_s(y))
        x0, x1 = -0.02 + row * -0.004, 0.60 - row * 0.002
        x = x0
        while x < x1:
            r = 0.016 * (0.6 + sc) * rr.uniform(0.7, 1.5)
            if not (0.255 < x < 0.345 and y < 0.76):
                m = Image.new("L", (c.W, c.H), 0)
                ImageDraw.Draw(m).polygon(c.pts(jag(rock(x, y + 0.008, r, rr, rr.uniform(0.5, 0.75)), rr, r * 0.06, 2)), fill=255)
                fac = blur(np.random.default_rng(int(rr.integers(1 << 30))).random((c.H, c.W)).astype(np.float32), r * c.k * 0.2) * r * c.k * 4
                L.stamp(m, albedo=rr.uniform(0.20, 0.36), relief=2.4, facets=fac, outline=1.0, rim=0.5)
            x += r * rr.uniform(1.1, 2.4)
    for k in range(7):                        # the steps down from the door, worn and broken
        y0 = 0.700 + k * 0.0085
        hw = 0.034 + k * 0.006
        m = Image.new("L", (c.W, c.H), 0)
        ImageDraw.Draw(m).polygon(c.pts(jag([(0.30 - hw, y0), (0.30 + hw, y0), (0.30 + hw * 1.03, y0 + 0.006),
                                             (0.30 - hw * 1.03, y0 + 0.006)], rr, 0.0015, 3)), fill=255)
        L.stamp(m, albedo=0.38, relief=0.8, theta=0.0, outline=1.0)
    # --- the river: a polygon from the centreline; banks of stones that zig-zag with it, sized by nearness
    ys_ = np.linspace(G.RIVER[0][1], 1.0, 160)
    cxs = np.interp(ys_, [p[1] for p in G.RIVER], [p[0] for p in G.RIVER])
    hws = np.interp(ys_, [p[1] for p in G.RIVER], [p[2] for p in G.RIVER])
    jl = [(x - w * (1 + 0.08 * math.sin(y * 300)), y) for x, y, w in zip(cxs, ys_, hws)]
    jr = [(x + w * (1 + 0.08 * math.sin(y * 260 + 1)), y) for x, y, w in zip(cxs, ys_, hws)]
    m = Image.new("L", (c.W, c.H), 0)
    ImageDraw.Draw(m).polygon(c.pts(jl + jr[::-1]), fill=255)
    wm = blur(arr(m), 1.2)
    # the water: horizontal lines spreading with nearness; the red sky's reflection in the middle, dark under the banks
    u, v, hw = G.river_uv(X, Y)
    streaks = G.fbm(X * 3.0, v * 2.2, 1, 705, False, 4)          # long horizontal bands of light on the current
    refl = np.exp(-(u * 0.8) ** 2) * (0.35 + 0.65 * smooth(0.35, 0.7, streaks))
    near_fall = np.exp(-((Y - 0.80) / 0.03) ** 2) * 0.35
    wv = np.clip(0.38 + 0.72 * refl + near_fall, 0, 1) * (1 - 0.50 * smooth(0.65, 1.0, np.abs(u)))
    wth = (G.fbm(X * 6, Y * 20, 3, 707, False, 2) - 0.5) * 0.25
    L.stamp(wm, albedo=1.0, colour=np.clip(BLOOD * 1.35, 0, 1), relief=0.0, theta=wth, outline=0.0, value=np.clip(wv * 1.15, 0, 1),
            black=((0.26, 0.02, 0.04), 1.0))
    for side, edge in ((-1, jl), (1, jr)):
        for i in range(0, len(edge), 3):
            x, y = edge[i]
            sc = float(depth_s(y))
            if rr.random() < 0.55:
                r = 0.016 * sc * rr.uniform(0.5, 1.4)
                rocks(L, x + side * r * 0.4, y + r * 0.2, r * 0.3, 1, r, rr, albedo=0.55)
    # stones breaking the surface (the ripple rings in game spread from these)
    for x, y, r in G.river_rocks():
        rocks(L, x, y, 0.0, 1, r * 0.9, rr, albedo=0.6, flat=0.32)
    rgb_cov = {}

    def rows(rgb, cov, blk, blk_k):
        return plane_rows(rgb, cov, blk, blk_k, c, GROUND_STOPS)
    out, a = L.render("ground", period, 708, default_theta=math.pi / 2, shade_relief=1.0, ladder_rows=rows, gamma=1.1)
    save(out, a, "ground", c.size)


def grass_tufts(L, n, rr, rect):
    """Tufts of grass over the land: little fans of blades, sized by nearness, thinned where the drift noise says
    bare ground."""
    c = L.c
    m = Image.new("L", (c.W, c.H), 0)
    d = ImageDraw.Draw(m)
    x0, y0, x1, y1 = rect
    for _ in range(n):
        x = rr.uniform(x0, x1)
        y = y0 + (y1 - y0) * rr.random() ** 0.8
        if y < terrain_top(x) + 0.002:
            continue
        if y > G.RIVER[0][1] - 0.004:
            uu, _, _ = G.river_uv(np.array([[x]]), np.array([[y]]))
            if abs(float(uu[0, 0])) < 1.05:
                continue
        sc = float(depth_s(y))
        h = 0.004 + 0.03 * sc ** 1.6
        px_, py_ = c.P(x, y)
        wd = max(1, int(round(h * c.k * 0.08)))
        for b in range(int(rr.integers(3, 7))):
            a = -math.pi / 2 + rr.normal(0, 0.35)
            hh = h * rr.uniform(0.6, 1.1) * c.k
            mx_ = px_ + math.cos(a) * hh * 0.5 + rr.normal(0, hh * 0.08)
            d.line([(px_, py_), (mx_, py_ + math.sin(a) * hh * 0.5), (px_ + math.cos(a) * hh, py_ + math.sin(a) * hh)],
                   fill=255, width=wd)
    X, Y = c.grid()
    ds = depth_s(Y)
    patch = G.fbm(X * 2.5, Y * 8.0, 2, 709, False, 3)
    alb = np.clip((0.66 - 0.56 * ds ** 1.1) * (0.55 + 0.8 * smooth(0.3, 0.7, patch)), 0.03, 1)
    L.stamp(arr(m), albedo=alb, relief=0.3, theta=math.pi / 2, outline=0.2, colour=np.array((196, 192, 178), np.float32) / 255)


def _flower_maps(c, flowers, rr):
    """Rasterise flowers (x, y, size, kind) into masks and a dome height map: petals (red / ivory), hearts, leaves."""
    red = Image.new("L", (c.W, c.H), 0)
    ivory = Image.new("L", (c.W, c.H), 0)
    heart = Image.new("L", (c.W, c.H), 0)
    leaf = Image.new("L", (c.W, c.H), 0)
    hgt = Image.new("L", (c.W, c.H), 0)
    dr, di, dh, dl, dg = (ImageDraw.Draw(m) for m in (red, ivory, heart, leaf, hgt))
    for x, y, sz, kind in flowers:
        px_, py_ = c.P(x, y)
        r = sz * c.k
        if r < 2.2:
            (dr if kind == 0 else di).ellipse([px_ - r, py_ - r * 0.7, px_ + r, py_ + r * 0.7], fill=255)
            dg.ellipse([px_ - r, py_ - r * 0.7, px_ + r, py_ + r * 0.7], fill=200)
            continue
        # a stem and a pair of lance leaves
        sl = r * rr.uniform(1.5, 3.0)
        dl.line([(px_, py_), (px_ + rr.normal(0, r * 0.3), py_ + sl)], fill=255, width=max(1, int(r * 0.12)))
        for sgn in (-1, 1):
            if rr.random() < 0.7:
                ly = py_ + sl * rr.uniform(0.3, 0.8)
                lx = px_ + sgn * r * rr.uniform(0.6, 1.2)
                dl.polygon([(px_, ly + r * 0.2), (lx, ly - r * 0.25), (lx + sgn * r * 0.4, ly - r * 0.5)], fill=255)
        n = 5 if kind == 1 else int(rr.integers(5, 8))
        ph = rr.uniform(0, 6.28)
        tilt = rr.uniform(0.45, 0.8)                   # seen from above at an angle: flattened
        tgt = dr if kind == 0 else di
        for i in range(n):
            a = ph + i / n * 2 * math.pi
            ex, ey = px_ + math.cos(a) * r * 0.55, py_ + math.sin(a) * r * 0.55 * tilt
            pr = r * (0.55 if kind == 0 else 0.42) * rr.uniform(0.85, 1.15)
            tgt.ellipse([ex - pr, ey - pr * tilt, ex + pr, ey + pr * tilt], fill=255)
            dg.ellipse([ex - pr, ey - pr * tilt, ex + pr, ey + pr * tilt], fill=150)
        for k, f in enumerate((0.8, 0.5, 0.25)):
            dg.ellipse([px_ - r * f, py_ - r * f * tilt, px_ + r * f, py_ + r * f * tilt], fill=150 + 35 * k)
        hr = r * (0.22 if kind == 0 else 0.18)
        dh.ellipse([px_ - hr, py_ - hr * tilt, px_ + hr, py_ + hr * tilt], fill=255)
    return arr(red), arr(ivory), arr(heart), arr(leaf), blur(arr(hgt), 1.5)


def draw_flowers(c, flowers, rr):
    """The blooms drawn one by one as small engravings, back to front: a dark stem and lance leaves, petals as inked
    teardrops filled dark and cut with bright radial lines (brighter on the petals that face the moon), a black heart
    ringed with stamens. Specks for the far ones. Returns (rgb, alpha) at canvas size."""
    im = Image.new("RGBA", (c.W, c.H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    mdx, mdy = MOON_DIR
    for x, y, sz, kind in flowers:
        px_, py_ = c.P(x, y)
        r = sz * c.k
        ds = float(depth_s(y))
        dim = 1.0 - 0.30 * smooth(0.75, 1.1, ds)          # the very nearest sit in the frame's shadow
        hue = rr.uniform(0.85, 1.1)
        if kind == 0:
            lit_c = (int(min(255, 214 * hue * dim)), int(28 * dim), int(40 * dim))
            dark_c = (int(70 * dim), 5, 12)
        else:
            lit_c = (int(232 * dim), int(222 * dim), int(198 * dim))
            dark_c = (int(64 * dim), int(58 * dim), int(52 * dim))
        if r < 2.6:
            k = max(0.8, r)
            d.ellipse([px_ - k, py_ - k * 0.7, px_ + k, py_ + k * 0.7], fill=dark_c + (255,))
            if r > 1.2:
                d.ellipse([px_ - k * 0.8, py_ - k * 0.75, px_ + k * 0.2, py_], fill=lit_c + (255,))
            else:
                d.point((px_, py_), fill=lit_c + (220,))
            continue
        ink = (8, 3, 5, 255)
        lw = max(1, int(round(r * 0.07)))
        # stem and leaves
        sl = r * rr.uniform(1.4, 2.6)
        bx = px_ + rr.normal(0, r * 0.25)
        d.line([(px_, py_), (bx, py_ + sl)], fill=(20, 22, 18, 255), width=max(1, int(r * 0.14)))
        for sgn in (-1, 1):
            if rr.random() < 0.75:
                ly = py_ + sl * rr.uniform(0.35, 0.85)
                ln = r * rr.uniform(0.9, 1.5)
                tip = (px_ + sgn * ln, ly - ln * 0.55)
                d.polygon([(px_, ly), (px_ + sgn * ln * 0.5, ly - ln * 0.45), tip, (px_ + sgn * ln * 0.55, ly - ln * 0.1)],
                          fill=(22, 26, 20, 255), outline=ink)
                d.line([(px_, ly), tip], fill=(96, 104, 88, 255), width=1)
        n = int(rr.integers(5, 7)) if kind == 0 else 6
        ph = rr.uniform(0, 6.28)
        tilt = rr.uniform(0.5, 0.8)
        petals = []
        for i in range(n):
            a = ph + i / n * 2 * math.pi
            ln = r * rr.uniform(0.85, 1.1)
            wd = r * (0.95 if kind == 0 else 0.45)
            ca, sa = math.cos(a), math.sin(a)
            tipx, tipy = px_ + ca * ln, py_ + sa * ln * tilt
            # a teardrop: base at the heart, widest two thirds out, rounded tip
            pts = [(px_, py_)]
            for k in range(9):
                t = -1 + 2 * k / 8
                along = 0.55 + 0.45 * math.cos(t * math.pi / 2)
                side = t * wd * 0.5 * math.sin((0.4 + 0.6 * along) * math.pi / 1.2)
                pts.append((px_ + ca * ln * along - sa * side, py_ + (sa * ln * along + ca * side) * tilt))
            facing = -(ca * mdx + sa * mdy)
            petals.append((facing, pts, a, ln, tipx, tipy))
        # the petals facing away first (they lie behind)
        for facing, pts, a, ln, tipx, tipy in sorted(petals, key=lambda p_: p_[0]):
            d.polygon(pts, fill=dark_c + (255,), outline=ink)
            lv = 0.35 + 0.65 * max(0.0, facing)
            col_ = tuple(int(dark_c[i] + (lit_c[i] - dark_c[i]) * lv) for i in range(3)) + (255,)
            nl = 3 + int(r / 6)
            for k in range(nl):
                off = (k / (nl - 1) - 0.5) * 0.7 if nl > 1 else 0
                ca, sa = math.cos(a + off * 0.5), math.sin(a + off * 0.5)
                f0, f1 = 0.25, rr.uniform(0.7, 0.92)
                d.line([(px_ + ca * ln * f0, py_ + sa * ln * f0 * tilt), (px_ + ca * ln * f1, py_ + sa * ln * f1 * tilt)],
                       fill=col_, width=max(1, int(round(r * 0.06 * (0.6 + lv)))))
            d.line(pts + [pts[0]], fill=ink, width=lw)
        hr = r * (0.24 if kind == 0 else 0.2)
        d.ellipse([px_ - hr, py_ - hr * tilt, px_ + hr, py_ + hr * tilt], fill=(6, 4, 6, 255))
        for k in range(int(r / 4) if r > 7 else 0):
            a = rr.uniform(0, 6.28)
            q = hr * rr.uniform(1.0, 1.35)
            sx_, sy_ = px_ + math.cos(a) * q, py_ + math.sin(a) * q * tilt
            d.ellipse([sx_ - lw * 0.5, sy_ - lw * 0.5, sx_ + lw * 0.5, sy_ + lw * 0.5], fill=(150, 120, 70, 255))
    a4 = np.asarray(im, np.float32) / 255
    return a4[..., :3], a4[..., 3]


def _scatter(rect, n, seed, drift_cells, red_frac, size_k, y_bias=1.0, keep_out=None):
    """Flowers in drifts: a noise field decides where they grow (clumps with bare ground between); sizes follow the
    perspective (specks at the horizon, big blooms in front); red and ivory each in their own drifts."""
    rr = np.random.default_rng(seed)
    out = []
    x0, y0, x1, y1 = rect
    tries = 0
    while len(out) < n and tries < n * 30:
        tries += 1
        x = rr.uniform(x0, x1)
        y = y0 + (y1 - y0) * rr.random() ** y_bias
        if y < terrain_top(x) + 0.004:
            continue
        sc = float(depth_s(y))
        dn = G.fbm(np.array([[x * drift_cells]]), np.array([[y * drift_cells * 2.5]]), 1, seed + 1, False, 3)[0, 0]
        u, _, _ = G.river_uv(np.array([[x]]), np.array([[y]]))
        au = abs(float(u[0, 0]))
        if au < 1.15:
            continue
        bank = math.exp(-((au - 1.6) / 0.8) ** 2) if y > G.RIVER[0][1] else 0.0     # drifts crowd the banks
        if rr.random() > min(1.0, smooth(0.50, 0.60, dn) + 0.6 * bank):
            continue
        if keep_out is not None and keep_out(x, y):
            continue
        kd = G.fbm(np.array([[x * 3.0]]), np.array([[y * 8.0]]), 1, seed + 2, False, 2)[0, 0]
        kind = 0 if kd < red_frac else 1
        sz = size_k * (0.15 + sc ** 1.7) * rr.uniform(0.7, 1.25)
        out.append((x, y, sz, kind))
    out.sort(key=lambda f: f[1])
    return out


def fields():
    """The flower fields on the ground plane: far drifts (specks to small blooms) and the near blooms; with the grass
    and stones of the near banks in front (field_near rides the ground's nearest depth)."""
    for name, rect, n, seed, size_k, lad in (("field_far", (-ART_W / 2, 0.62, ART_W / 2, 0.93), 4200, 801, 0.0090, "ground"),
                                             ("field_near", (-ART_W / 2, 0.78, ART_W / 2, 1.0), 440, 802, 0.034, "near")):
        rr = np.random.default_rng(seed)
        c = Canvas(rect, px=1100)
        L = Layer(c)
        lo = rect[1] + (0.04 if name == "field_near" else 0.0)
        fl = _scatter((rect[0], lo, rect[2], rect[3] - 0.01), n, seed, 6.0, 0.55, size_k,
                      keep_out=lambda x, y: (-0.02 < x < 0.62 and y < 0.80))
        if name == "field_near":
            # near grass along the bottom and the banks, under the blooms
            for k in range(28):
                gx = rr.uniform(rect[0], rect[2])
                gy = rr.uniform(0.90, 1.0)
                u, _, _ = G.river_uv(np.array([[gx]]), np.array([[gy]]))
                if abs(u[0, 0]) < 1.2:
                    continue
                grass(L, gx - 0.05, gx + 0.05, gy, 0.04 * float(depth_s(gy)), 60, rr, albedo=0.28)
        if name == "field_far":
            grass_tufts(L, 2500, rr, (rect[0], 0.70, rect[2], 0.93))
        if L.A.max() > 0:
            out, a = L.render(lad, 2.8 * SS, seed + 3, default_theta=math.pi / 2, gamma=1.0)
        else:
            out, a = np.zeros((c.H, c.W, 3), np.float32), np.zeros((c.H, c.W), np.float32)
        frgb, fa = draw_flowers(c, fl, rr)
        out = out * (1 - fa[..., None]) + frgb * fa[..., None]
        a = np.maximum(a, fa)
        save(out, a, name, c.size)


# ------------------------------------------------------------------ grading the round-5 layers into the ladder

MARK = "bat-depth-graded"


def _graded(path):
    try:
        return Image.open(path).info.get(MARK) == "1"
    except Exception:
        return False


def _save_marked(rgb, a, path):
    from PIL import PngImagePlugin
    info = PngImagePlugin.PngInfo()
    info.add_text(MARK, "1")
    out = np.concatenate([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]], -1)
    Image.fromarray(np.round(out * 255).astype(np.uint8), "RGBA").save(path, optimize=True, pnginfo=info)


CATH_HAZE = {"cath_back": 0.20, "cath_nave": 0.12, "cath_aisle": 0.07, "cath_tfar": 0.05}


def cathedral_grade():
    """The cathedral as a dark, intricate silhouette against the red sky (lovish's references): the stone darkened
    (the light lines stay, a little dimmer), the farther layers lifted toward the haze (atmospheric perspective), the
    glass pushed from orange to blood red and given a fine leaded texture so it does not read as flat colour. Runs once
    on what tools/gen_lobby_art.cathedral() wrote (a PNG text marker stops a second pass)."""
    for f in sorted(os.listdir(OUT)):
        if not f.startswith("cath_") or not f.endswith(".png"):
            continue
        path = os.path.join(OUT, f)
        if _graded(path):
            continue
        nm = f[:-4]
        a4 = np.asarray(Image.open(path).convert("RGBA"), np.float32) / 255
        rgb, a = a4[..., :3], a4[..., 3]
        sat = rgb.max(-1) - rgb.min(-1)
        glass = smooth(0.18, 0.40, sat)[..., None]
        h, w = a.shape
        if nm.endswith("_rim"):
            rgb = rgb * 0.62
        elif nm.endswith("_glow"):
            rgb = np.stack([rgb[..., 0], rgb[..., 1] * 0.72, rgb[..., 2] * 0.8], -1)
        else:
            base = nm
            lum = rgb @ np.array([0.30, 0.59, 0.11], np.float32)
            stone = rgb * 0.62 * (1 - 0.45 * smooth(0.18, 0.5, lum))[..., None]
            stone = stone ** 1.08
            hz = CATH_HAZE.get(base, 0.0)
            stone = stone * (1 - hz) + np.array((70, 38, 44), np.float32) / 255 * hz
            yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
            lines = 0.80 + 0.20 * (0.5 + 0.5 * np.cos((xx + 0.35 * yy) / 2.6))
            gl = np.stack([rgb[..., 0], rgb[..., 1] * 0.62, rgb[..., 2] * 0.85], -1) * lines[..., None]
            rgb = stone * (1 - glass) + gl * glass
        _save_marked(rgb, a, path)
        print("  graded", nm, flush=True)


def spires_grade():
    """The far abbey and spires dissolve into the haze: lifted toward it, contrast lowered."""
    path = os.path.join(OUT, "spires_far.png")
    if _graded(path):
        return
    a4 = np.asarray(Image.open(path).convert("RGBA"), np.float32) / 255
    rgb, a = a4[..., :3], a4[..., 3]
    m = rgb.mean()
    rgb = (rgb - m) * 0.65 + m
    rgb = rgb * 0.6 + np.array((58, 22, 30), np.float32) / 255 * 0.4
    _save_marked(rgb, a, path)


# ------------------------------------------------------------------ the framing (screen-pinned, design: a tunnel)

FRAME_W = 0.66


def frames():
    """The near framing on both sides, three depths each (LobbyBackdrop pins them to the screen edges):
    `frame_*_tree` (z .055) a tree a little way in, the first step of the tunnel; `frame_*` (z .065) the near trunks,
    rocks, gravestones and the railing, near-black; `frame_*_vines` (z .075) the canopy hanging in from the top."""
    for side in ("left", "right"):
        rr = np.random.default_rng(401 if side == "left" else 402)
        mir = side == "right"
        X = (lambda x: FRAME_W - x) if mir else (lambda x: x)
        c = Canvas((0, 0, FRAME_W, 1.0))
        # --- the tree a little way in (mid-dark, a hair of haze); kept to the outer part (the cathedral stands on
        # the right third, and further right still in the title layout)
        L = Layer(c)
        if not mir:
            tree(L, 0.36, 0.84, 0.58, 0.026, rr, lean=0.10, albedo=0.50, depth=5, crown=0.5, side_bias=-0.35)
            rocks(L, 0.36, 0.855, 0.04, 4, 0.025, rr, albedo=0.45)
            grass(L, 0.20, 0.46, 0.865, 0.03, 160, rr, albedo=0.38)
        else:
            tree(L, X(0.12), 0.86, 0.24, 0.016, rr, lean=0.12, albedo=0.48, depth=5, crown=0.5, side_bias=0.45)
            rocks(L, X(0.30), 0.85, 0.05, 6, 0.03, rr, albedo=0.45)
            grass(L, X(0.42), X(0.12), 0.865, 0.03, 200, rr, albedo=0.38)
        out, a = L.render("mid", 3.6 * SS, 410 + mir, default_theta=math.pi / 2, shade_relief=1.0)
        save(out, a, f"frame_{side}_tree", c.size)
        # --- the near framing, near-black: a massive trunk at the edge, roots, rocks, gravestones and the railing
        # (left) / a rock outcrop with a dead tree, low (right)
        L = Layer(c)
        if not mir:
            tree(L, 0.07, 1.02, 1.1, 0.10, rr, lean=0.03, albedo=0.34, depth=3, crown=0.32, side_bias=-0.45)
            rocks(L, 0.16, 0.96, 0.06, 6, 0.045, rr, albedo=0.32)
            for i, (gx, gw, gh, gl, gk) in enumerate(((0.27, 0.052, 0.12, -0.10, 0), (0.36, 0.046, 0.10, 0.14, 1),
                                                      (0.45, 0.038, 0.12, 0.05, 2), (0.20, 0.058, 0.09, 0.2, 0))):
                gravestone(L, gx, 0.99 - i * 0.012, gw, gh, rr, gl, gk, albedo=0.40)
            fence(L, 0.13, 0.52, 0.935, 0.15, rr, lean=-0.03, broken=(3, 7))
            # dark leaves massed on the trunk's lower boughs and an elder bush at its foot (the tunnel's left wall)
            foliage(L, 0.05, 0.36, 0.12, 0.10, rr, albedo=0.22, leaf=0.010)
            foliage(L, 0.13, 0.52, 0.09, 0.07, rr, albedo=0.22, leaf=0.010)
            foliage(L, 0.04, 0.74, 0.12, 0.09, rr, albedo=0.20, leaf=0.011)
            grass(L, 0.0, 0.56, 1.0, 0.05, 600, rr, albedo=0.26)
        else:
            m = Image.new("L", (c.W, c.H), 0)
            cliff = [(X(-0.01), 1.02), (X(-0.01), 0.60), (X(0.06), 0.62), (X(0.11), 0.68), (X(0.15), 0.72),
                     (X(0.19), 0.79), (X(0.26), 0.83), (X(0.30), 0.89), (X(0.38), 0.92), (X(0.44), 1.02)]
            ImageDraw.Draw(m).polygon(c.pts(jag(cliff, rr, 0.008, 3)), fill=255)
            fac = blur(np.random.default_rng(5).random((c.H, c.W)).astype(np.float32), 18) * 900
            L.stamp(m, albedo=0.32, relief=3.0, facets=fac, outline=1.0)
            for k in range(6):
                rocks(L, X(0.08 + 0.055 * k), 0.66 + k * 0.05, 0.02, 2, 0.035, rr, albedo=0.34)
            tree(L, X(0.10), 0.66, 0.40, 0.04, rr, lean=-0.25, albedo=0.32, depth=4, crown=0.5, side_bias=0.45, roots=True)
            # a near-black trunk up the very edge, leaning out, its crown off the top: the right wall of the tunnel
            tree(L, X(0.075), 1.03, 1.08, 0.075, rr, lean=0.05, albedo=0.26, depth=2, crown=0.28, side_bias=0.6, roots=False)
            foliage(L, X(0.06), 0.30, 0.08, 0.10, rr, albedo=0.22, leaf=0.010)
            foliage(L, X(0.07), 0.52, 0.07, 0.07, rr, albedo=0.20, leaf=0.010)
            rocks(L, X(0.30), 0.985, 0.06, 6, 0.05, rr, albedo=0.32)
            grass(L, X(0.48), X(0.06), 1.0, 0.07, 600, rr, albedo=0.26)
        out, a = L.render("frame", 3.2 * SS, 420 + mir, default_theta=math.pi / 2, gamma=1.3)
        save(out, a, f"frame_{side}", c.size)
        # --- the canopy hanging in from the top corner, and a few trailing tendrils
        L = Layer(c)
        foliage(L, X(0.08), 0.0, 0.22 if not mir else 0.20, 0.08, rr, albedo=0.30, droop=0.03)
        foliage(L, X(0.26 if not mir else 0.24), -0.01, 0.12, 0.045, rr, albedo=0.28)
        for k in range(6 if not mir else 4):
            x = X(0.06 + 0.045 * k + rr.uniform(0, 0.025))
            pts, _ = wander(x, 0.05, math.pi / 2 + rr.normal(0, 0.12), rr.uniform(0.10, 0.28 if not mir else 0.18), 8, rr, 0.10)
            L.stamp(limb_mask(c, pts, 0.005, 0.002), albedo=0.30, relief=0.6, outline=0.5)
            for (px_, py_) in pts[2:]:          # small leaves along the tendril, alternating sides
                if rr.random() < 0.7:
                    sg = 1 if rr.random() < 0.5 else -1
                    lm = Image.new("L", (c.W, c.H), 0)
                    ImageDraw.Draw(lm).polygon(c.pts([(px_, py_), (px_ + sg * 0.010, py_ + 0.004), (px_ + sg * 0.016, py_ + 0.012),
                                                      (px_ + sg * 0.006, py_ + 0.009)]), fill=255)
                    L.stamp(lm, albedo=0.32, relief=0.4, outline=0.6)
        out, a = L.render("frame", 3.0 * SS, 430 + mir, default_theta=0.3, gamma=1.3)
        save(out, a, f"frame_{side}_vines", c.size)


def fg():
    """The extreme foreground, nearest and darkest (LobbyBackdrop moves it fastest): boulders and brambles with a few
    big blooms in the bottom-left corner, tall grass and a fallen bough bottom-right, a leafy branch hanging in at the
    top left. Crisp near-black with the moon's rim on their upper-left edges."""
    rr = np.random.default_rng(901)
    # bottom left: 0.66 x 0.46 A (pinned: left edge -0.12 A, bottom +0.02 A)
    c = Canvas((0, 0, 0.66, 0.46), px=1100)
    L = Layer(c)
    rocks(L, 0.16, 0.44, 0.07, 5, 0.07, rr, albedo=0.30)
    for k in range(9):
        x0 = rr.uniform(0.05, 0.45)
        pts, _ = wander(x0, 0.46, -math.pi / 2 + rr.normal(0.25, 0.4), rr.uniform(0.12, 0.30), 9, rr, 0.18)
        L.stamp(limb_mask(c, pts, 0.008, 0.003), albedo=0.30, relief=0.8, outline=0.8)
        for (px_, py_) in pts[3::2]:          # thorns
            a = rr.uniform(0, 6.28)
            th_ = [(px_, py_), (px_ + math.cos(a) * 0.012, py_ + math.sin(a) * 0.012), (px_ + 0.003, py_ + 0.003)]
            m = Image.new("L", (c.W, c.H), 0)
            ImageDraw.Draw(m).polygon(c.pts(th_), fill=255)
            L.stamp(m, albedo=0.3, relief=0.3, outline=0.5)
        foliage(L, pts[-1][0], pts[-1][1], 0.03, 0.02, rr, albedo=0.28, n=8, leaf=0.008)
    grass(L, 0.0, 0.6, 0.47, 0.10, 220, rr, albedo=0.24, lean=0.15)
    out, a = L.render("frame", 3.0 * SS, 902, default_theta=math.pi / 2, gamma=1.3)
    fl = [(rr.uniform(0.15, 0.50), rr.uniform(0.18, 0.40), rr.uniform(0.022, 0.034), 0) for _ in range(5)]
    fl.sort(key=lambda f: f[1])
    frgb, fa = draw_flowers(c, fl, rr)
    out = out * (1 - fa[..., None]) + frgb * 0.8 * fa[..., None]
    save(out, np.maximum(a, fa), "fg_briar", (768, 512))
    # bottom right: 0.40 x 0.32 A
    c = Canvas((0, 0, 0.40, 0.32), px=1100)
    L = Layer(c)
    m = Image.new("L", (c.W, c.H), 0)
    pts = [(0.02, 0.31), (0.12, 0.25), (0.26, 0.22), (0.42, 0.20)]
    L.stamp(limb_mask(c, pts, 0.03, 0.022), albedo=0.30, relief=2.0, outline=1.0)
    rocks(L, 0.30, 0.31, 0.05, 3, 0.05, rr, albedo=0.28)
    grass(L, 0.02, 0.42, 0.33, 0.14, 260, rr, albedo=0.24, lean=-0.1)
    out, a = L.render("frame", 3.0 * SS, 903, default_theta=math.pi / 2, gamma=1.3)
    fl = [(rr.uniform(0.12, 0.36), rr.uniform(0.12, 0.24), rr.uniform(0.016, 0.024), 1) for _ in range(3)]
    frgb, fa = draw_flowers(c, fl, rr)
    out = out * (1 - fa[..., None]) + frgb * 0.75 * fa[..., None]
    save(out, np.maximum(a, fa), "fg_thorns", (512, 384))
    # top left: 0.42 x 0.42 A, a bough hanging in from the corner with leaves
    c = Canvas((0, 0, 0.42, 0.42), px=1100)
    L = Layer(c)
    pts, _ = wander(-0.02, 0.02, 0.35, 0.40, 10, rr, 0.10)
    L.stamp(limb_mask(c, pts, 0.03, 0.008), albedo=0.30, relief=2.0, outline=1.0)
    for i in range(2, len(pts), 2):
        bp, _ = wander(pts[i][0], pts[i][1], 1.2 + rr.normal(0, 0.3), rr.uniform(0.06, 0.14), 5, rr, 0.2)
        L.stamp(limb_mask(c, bp, 0.008, 0.003), albedo=0.30, relief=0.8, outline=0.8)
        for (qx, qy) in bp[1:]:
            for _ in range(2):
                a = rr.uniform(0.3, 2.8)
                ln = rr.uniform(0.018, 0.03)
                lm = Image.new("L", (c.W, c.H), 0)
                tip = (qx + math.cos(a) * ln, qy + math.sin(a) * ln)
                side = (math.cos(a + 1.5708) * ln * 0.28, math.sin(a + 1.5708) * ln * 0.28)
                ImageDraw.Draw(lm).polygon(c.pts([(qx, qy), (qx + math.cos(a) * ln * 0.5 + side[0], qy + math.sin(a) * ln * 0.5 + side[1]),
                                                  tip, (qx + math.cos(a) * ln * 0.5 - side[0], qy + math.sin(a) * ln * 0.5 - side[1])]), fill=255)
                L.stamp(lm, albedo=0.30, relief=0.8, outline=0.9, theta=a)
    out, a = L.render("frame", 3.0 * SS, 904, default_theta=0.4, gamma=1.3)
    save(out, a, "fg_branch", (512, 512))


if __name__ == "__main__":
    steps = sys.argv[1:] or ["frames"]
    for st in steps:
        print("depth:", st, flush=True)
        globals()[st]()
