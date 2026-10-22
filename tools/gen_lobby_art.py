#!/usr/bin/env python3
"""
Lobby backdrop art for Beyond and Tabs: "the moonlit cathedral and the crimson river" (design/lobby-backdrop.md).

lovish (Oct 21) asked for different, animated lobby art, from two mood references (a black gothic cathedral with a
glowing rose window over a red sky and a field of red and ivory flowers; a pale full moon behind the spires and a
crimson river running from the doorway toward the viewer). Those images are someone else's: nothing here traces,
samples or embeds them. Every layer is drawn from code - polygons, signed distances and value noise - with our own
design (our facade plan, our tracery, our glass, our trees and flowers), keeping only the mood, palette and the idea of
the composition.

Everything is laid out on one ARTBOARD: x in A units from the screen centre, y in A units from the top, where A is the
cover-scaled screen height (A = max(h, w / ART_W); the artboard is bottom-anchored, so an ultra-wide screen loses a
sliver of sky, never the ground). The two FRAME layers are instead pinned to the screen's left and right edges, so
16:9 and 21:9 both get their framing. hud/LobbyBackdrop.java mirrors every rect and the river geometry below.

Output: bar/src/main/resources/assets/reignofnether/textures/gui/lobby/*.png
Previews: design/lobby_preview_16x9.png, design/lobby_preview_21x9.png (+ _lobby variants with the scrim and a mock
panel). Deterministic (fixed seeds). usage: python3 tools/gen_lobby_art.py [--previews-only]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "bar/src/main/resources/assets/reignofnether/textures/gui/lobby")
DESIGN = os.path.join(REPO, "design")

# ---------------------------------------------------------------- shared layout (LobbyBackdrop.java mirrors these)
PX_PER_A = 1100.0
ART_W = 2560 / PX_PER_A                     # 2.327 A: a 21:9 screen, a hair narrower
MOON = (0.0, 0.27, 0.20)                    # centre x, y, radius
RECTS = {                                   # x0, y0, x1, y1 in A (artboard layers)
    "sky": (-ART_W / 2, 0.0, ART_W / 2, 1.0),
    "clouds_far": (-ART_W / 2, 0.28, ART_W / 2, 0.80),     # wraps in x (tile = ART_W)
    "clouds_near": (-ART_W / 2, 0.46, ART_W / 2, 0.84),
    "spires_far": (-ART_W / 2, 0.12, ART_W / 2, 0.80),
    "cathedral": (-0.40, 0.0, 0.40, 0.80),
    "glass_rose": (-0.40, 0.0, 0.40, 0.80),
    "glass_lancets": (-0.40, 0.0, 0.40, 0.80),
    "mist": (-ART_W / 2, 0.60, ART_W / 2, 0.86),           # wraps in x
    "ground": (-ART_W / 2, 0.56, ART_W / 2, 1.0),
    "field_far": (-ART_W / 2, 0.62, ART_W / 2, 0.93),
    "field_near": (-ART_W / 2, 0.78, ART_W / 2, 1.0),
}
FRAME_W = 0.66                              # frame_left / frame_right: FRAME_W x 1 A, pinned to the screen edges
# the river: centreline points (x, y, half width), from the foot of the cascade toward the viewer
RIVER = [(0.000, 0.792, 0.030), (0.016, 0.806, 0.044), (-0.012, 0.822, 0.058), (-0.075, 0.842, 0.080),
         (-0.090, 0.866, 0.108), (-0.010, 0.893, 0.145), (0.120, 0.926, 0.195), (0.110, 0.962, 0.255),
         (-0.060, 1.010, 0.330)]
HORIZON = 0.70                              # perspective for the river's flow coordinate
CASCADE = (0.0, 0.736, 0.020, 0.797, 0.030)  # x, top y, top half width, bottom y, bottom half width

INK = np.array((6, 5, 8), np.float32) / 255
rng_global = np.random.default_rng(7)


# ---------------------------------------------------------------- helpers

def _box(a, r, axis):
    if r < 1:
        return a
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis, dtype=np.float64)
    n = a.shape[axis]
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return ((hi - lo) / (2 * r + 1)).astype(np.float32)


def blur(a, sigma):
    """Gaussian-ish blur: three box passes per axis (as tools/gen_splash_art.py)."""
    r = max(0, int(round(math.sqrt(sigma * sigma + 0.25) - 0.5)))
    a = np.asarray(a, np.float32)
    for _ in range(3):
        for axis in (0, 1):
            a = _box(a, r, axis)
    return a


def vnoise(h, w, cells_y, cells_x, seed, wrap_x=False, octaves=4, gain=0.5):
    """Value noise in [0,1] with smoothstep interpolation; exactly periodic in x when wrap_x."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    ys = np.arange(h, dtype=np.float32)[:, None]
    xs = np.arange(w, dtype=np.float32)[None, :]
    for o in range(octaves):
        cy, cx = max(1, int(cells_y * 2 ** o)), max(1, int(cells_x * 2 ** o))
        g = rng.random((cy + 2, cx + 1)).astype(np.float32)
        fy = ys / h * cy
        fx = xs / w * cx
        iy, ix = np.floor(fy).astype(np.int32), np.floor(fx).astype(np.int32)
        ty, tx = fy - iy, fx - ix
        ty, tx = ty * ty * (3 - 2 * ty), tx * tx * (3 - 2 * tx)
        if wrap_x:
            ix0, ix1 = ix % cx, (ix + 1) % cx
        else:
            ix0, ix1 = np.minimum(ix, cx), np.minimum(ix + 1, cx)
        iy1 = np.minimum(iy + 1, cy + 1)
        a = g[iy, ix0] * (1 - tx) + g[iy, ix1] * tx
        b = g[iy1, ix0] * (1 - tx) + g[iy1, ix1] * tx
        out += (a * (1 - ty) + b * ty) * amp
        tot += amp
        amp *= gain
    return out / tot


def fbm(U, V, cells, seed, wrap=False, octaves=5, gain=0.5, aspect=1.0):
    """Value-noise fbm sampled at arbitrary coordinates U (0..1 across, periodic when wrap) and V (0..1 down)."""
    rng = np.random.default_rng(seed)
    out = np.zeros(U.shape, np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        cx = max(1, int(round(cells * aspect * 2 ** o)))
        cy = max(1, int(round(cells * 2 ** o)))
        g = rng.random((cy + 3, cx + 2)).astype(np.float32)
        fx, fy = U * cx, V * cy + 1
        ix, iy = np.floor(fx).astype(np.int64), np.floor(fy).astype(np.int64)
        tx, ty = fx - ix, fy - iy
        tx, ty = tx * tx * tx * (tx * (tx * 6 - 15) + 10), ty * ty * ty * (ty * (ty * 6 - 15) + 10)
        if wrap:
            ix0, ix1 = ix % cx, (ix + 1) % cx
        else:
            ix0, ix1 = np.clip(ix, 0, cx + 1), np.clip(ix + 1, 0, cx + 1)
        iy0, iy1 = np.clip(iy, 0, cy + 2), np.clip(iy + 1, 0, cy + 2)
        a = g[iy0, ix0] * (1 - tx) + g[iy0, ix1] * tx
        b = g[iy1, ix0] * (1 - tx) + g[iy1, ix1] * tx
        out += (a * (1 - ty) + b * ty) * amp
        tot += amp
        amp *= gain
    return out / tot


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def col(c):
    return np.asarray(c, np.float32) / 255


def lerp(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim and t.ndim == 2:
        t = t[..., None]
    return a + (b - a) * t


def grain(h, w, seed, amount):
    r = np.random.default_rng(seed)
    g = r.standard_normal((h, w)).astype(np.float32)
    return g * amount


def save_rgba(rgb, a, name, size=None):
    """Straight (un-premultiplied) RGBA. rgb/a are premultiplied-free floats. Downsamples premultiplied (no fringes)."""
    rgb = np.clip(rgb, 0, 1)
    a = np.clip(a, 0, 1)
    if size is not None:
        pm = np.concatenate([rgb * a[..., None], a[..., None]], -1)
        chans = [Image.fromarray((pm[..., i] * 65535).astype(np.int32), "I").convert("F").resize(size, Image.LANCZOS)
                 for i in range(4)]
        pm = np.stack([np.asarray(c, np.float32) / 65535 for c in chans], -1)
        a = np.clip(pm[..., 3], 0, 1)
        rgb = np.clip(pm[..., :3] / np.maximum(a, 1e-4)[..., None], 0, 1)
    out = np.concatenate([rgb, a[..., None]], -1)
    img = Image.fromarray(np.round(out * 255).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, name + ".png"), optimize=True)
    return img


def save_rgb(rgb, name, size=None):
    img = Image.fromarray(np.round(np.clip(rgb, 0, 1) * 255).astype(np.uint8), "RGB")
    if size is not None:
        img = img.resize(size, Image.LANCZOS)
    img.save(os.path.join(OUT, name + ".png"), optimize=True)
    return img


class Board:
    """Supersampled drawing in artboard A units onto float layers, via PIL masks."""

    def __init__(self, rect, w, h, ss=2):
        self.x0, self.y0, self.x1, self.y1 = rect
        self.W, self.H, self.ss = w * ss, h * ss, ss
        self.sx = self.W / (self.x1 - self.x0)
        self.sy = self.H / (self.y1 - self.y0)

    def P(self, x, y):
        return ((x - self.x0) * self.sx, (y - self.y0) * self.sy)

    def L(self, a):
        return a * self.sx

    def pts(self, ps):
        return [self.P(x, y) for x, y in ps]

    def mask(self):
        return Image.new("L", (self.W, self.H), 0)

    def grid(self):
        ys, xs = np.mgrid[0:self.H, 0:self.W].astype(np.float32)
        return xs / self.sx + self.x0, ys / self.sy + self.y0


def arr(im):
    return np.asarray(im, np.float32) / 255


def down(a, w, h):
    """Box-ish downsample of a float array to (h, w)."""
    im = Image.fromarray((np.clip(a, -4, 4) * 8192 + 32768).astype(np.int32), "I").convert("F")
    return (np.asarray(im.resize((w, h), Image.LANCZOS), np.float32) - 32768) / 8192


def arch_pts(cx, ys, hw, sharp=1.0, n=18):
    """Pointed arch from the left springing (cx-hw, ys) over the apex to (cx+hw, ys). sharp 1 = equilateral."""
    r = 2 * hw * sharp
    left_c = cx - hw + r
    apex_dy = math.sqrt(max(r * r - (r - hw) ** 2, 1e-12))
    out = []
    a0 = math.pi
    a1 = math.pi - math.atan2(apex_dy, r - hw)
    for i in range(n + 1):
        a = a0 + (a1 - a0) * i / n
        out.append((left_c + r * math.cos(a), ys - r * math.sin(a)))
    right = [(2 * cx - x, y) for x, y in reversed(out[:-1])]
    return out + right


def lancet_pts(cx, ys, hw, bottom, sharp=1.0):
    return [(cx - hw, bottom)] + arch_pts(cx, ys, hw, sharp) + [(cx + hw, bottom)]


# ---------------------------------------------------------------- the gothic toolkit (etched stone)

class Gothic:
    """Layers: sil (coverage), tone (stone value), line (etched light lines), glass (rgb) + gmask, lead (dark bars)."""

    def __init__(self, board):
        self.b = board
        self.sil = board.mask()
        self.tone = board.mask()
        self.line = board.mask()
        self.gmask = board.mask()
        self.grose = board.mask()
        self.glan = board.mask()
        self.glass = Image.new("RGB", (board.W, board.H), (0, 0, 0))
        self.ds = ImageDraw.Draw(self.sil)
        self.dt = ImageDraw.Draw(self.tone)
        self.dl = ImageDraw.Draw(self.line)
        self.dgm = ImageDraw.Draw(self.gmask)
        self.dg = ImageDraw.Draw(self.glass)
        self.dgr = ImageDraw.Draw(self.grose)
        self.dgl = ImageDraw.Draw(self.glan)
        self.lw = board.ss * 1.4    # a little over one output pixel: the etching must survive downscaling

    # primitives
    def poly(self, ps, tone, edge=150, w=1.0):
        p = self.b.pts(ps)
        self.ds.polygon(p, fill=255)
        self.dt.polygon(p, fill=int(tone))
        self.dl.polygon(p, fill=0)
        self.dgm.polygon(p, fill=0)
        if edge:
            self.dl.line(p + [p[0]], fill=int(edge), width=max(1, int(round(self.lw * w))), joint="curve")

    def rect(self, x0, y0, x1, y1, tone, edge=150, w=1.0):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], tone, edge, w)

    def ln(self, ps, v=130, w=1.0):
        self.dl.line(self.b.pts(ps), fill=int(v), width=max(1, int(round(self.lw * w))), joint="curve")

    def dark(self, ps, tone=4, edge=110, w=1.0):
        """A recess / opening: very dark fill, light rim."""
        self.poly(ps, tone, edge, w)

    def circle(self, cx, cy, r, tone, edge=150, w=1.0):
        n = max(16, int(self.b.L(r) * 0.8))
        self.poly([(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)],
                  tone, edge, w)

    def ring(self, cx, cy, r, v=150, w=1.0):
        n = max(24, int(self.b.L(r) * 0.9))
        self.ln([(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n + 1)], v, w)

    def glass_poly(self, ps, rgb, which=None):
        p = self.b.pts(ps)
        self.dg.polygon(p, fill=tuple(int(c) for c in rgb))
        self.dgm.polygon(p, fill=255)
        self.ds.polygon(p, fill=255)
        self.dl.polygon(p, fill=0)
        if which == "rose":
            self.dgr.polygon(p, fill=255)
        elif which == "lancet":
            self.dgl.polygon(p, fill=255)

    def lead(self, ps, w=1.0, v=20):
        """Stone tracery / lead bars over glass: drawn dark into the glass and erasing the glass mask."""
        p = self.b.pts(ps)
        width = max(1, int(round(self.lw * w)))
        self.dg.line(p, fill=(v, v - 4, v - 6), width=width, joint="curve")
        self.dgm.line(p, fill=60, width=width, joint="curve")

    # composite elements
    def crockets(self, a, b, n, size, tone, edge, side):
        """Small leaf hooks along an edge from a to b, sticking out to `side` (-1 left, +1 right)."""
        ax, ay = a
        bx, by = b
        dx, dy = bx - ax, by - ay
        L = math.hypot(dx, dy)
        nx, ny = -dy / L * side, dx / L * side
        if nx * side < 0:
            nx, ny = -nx, -ny
        for i in range(1, n):
            t = i / n
            px, py = ax + dx * t, ay + dy * t
            s = size * (0.75 + 0.25 * (1 - t))
            tip = (px + nx * s * 1.1 - dx / L * s * 0.7, py + ny * s * 1.1 - dy / L * s * 0.7)
            self.poly([(px - dx / L * s * 0.5, py - dy / L * s * 0.5), tip, (px + dx / L * s * 0.4, py + dy / L * s * 0.4)],
                      tone, edge, 0.8)

    def finial(self, x, y, h, tone, edge, cross=False):
        w = h * 0.07
        self.poly([(x - w, y), (x, y - h), (x + w, y)], tone, edge, 0.8)
        if cross:
            c = y - h * 0.62
            self.rect(x - h * 0.16, c - w * 0.9, x + h * 0.16, c + w * 0.9, tone, edge, 0.8)
            self.rect(x - w * 1.1, y - h * 0.95, x + w * 1.1, y - h * 0.25, tone, edge, 0.8)
        else:
            self.circle(x, y - h * 0.32, h * 0.09, tone, edge, 0.8)

    def spire(self, cx, base, tip, hw, tone, edge=150, crock=True, bands=3, cross=False, fin=True):
        self.poly([(cx - hw, base), (cx, tip), (cx + hw, base)], tone, edge)
        h = base - tip
        for f in (-0.42, 0.42):           # octagonal facets
            self.ln([(cx + hw * f, base), (cx, tip)], edge * 0.55, 0.7)
        for i in range(1, bands + 1):
            y = base - h * i / (bands + 1) * 0.85
            k = (y - tip) / h
            self.ln([(cx - hw * k, y), (cx + hw * k, y)], edge * 0.6, 0.7)
        # small lucarne gablet low on the spire
        lb = base - h * 0.16
        k = (lb - tip) / h
        self.poly([(cx - hw * k * 0.45, lb), (cx, lb - h * 0.09), (cx + hw * k * 0.45, lb)], tone + 6, edge * 0.8, 0.7)
        if crock:
            n = max(4, int(h / 0.012))
            self.crockets((cx - hw, base), (cx, tip), n, hw * 0.28, tone, edge * 0.85, -1)
            self.crockets((cx + hw, base), (cx, tip), n, hw * 0.28, tone, edge * 0.85, 1)
        if fin:
            self.finial(cx, tip + h * 0.01, max(h * 0.12, 0.012), tone, edge, cross)

    def gablet(self, cx, base, hw, h, tone, edge=150, crock=True):
        self.poly([(cx - hw, base), (cx, base - h), (cx + hw, base)], tone, edge)
        if crock:
            n = max(3, int(h / 0.010))
            self.crockets((cx - hw, base), (cx, base - h), n, hw * 0.16, tone, edge * 0.85, -1)
            self.crockets((cx + hw, base), (cx, base - h), n, hw * 0.16, tone, edge * 0.85, 1)
        # a trefoil in the gable (three small rings) and a blind arch under it
        if h > 0.02:
            r = hw * 0.12
            cy = base - h * 0.42
            for ang in (-90, 30, 150):
                a = math.radians(ang)
                self.ring(cx + r * 1.1 * math.cos(a), cy + r * 1.1 * math.sin(a), r, edge * 0.75, 0.7)
            self.ring(cx, cy, r * 2.5, edge * 0.6, 0.6)
            aw = hw * 0.32
            self.ln([(cx - aw, base)] + arch_pts(cx, base - h * 0.12, aw) + [(cx + aw, base)], edge * 0.6, 0.6)

    def blind_arcade(self, x0, x1, y0, y1, n, edge=120):
        """A row of narrow pointed panels (blind tracery) - the vertical linework of a gothic wall."""
        w = (x1 - x0) / n
        for i in range(n):
            cx = x0 + w * (i + 0.5)
            hw = w * 0.36
            ys = y0 + (y1 - y0) * 0.0 + hw * 1.9
            self.ln([(cx - hw, y1)] + arch_pts(cx, ys, hw) + [(cx + hw, y1)], edge, 0.7)

    def pinnacle(self, cx, base, top, tip, hw, tone, edge=150, cross=False):
        self.rect(cx - hw, top, cx + hw, base, tone, edge)
        # set-offs (the shaft steps in as it rises)
        for k in (0.35, 0.68):
            y = base - (base - top) * k
            self.ln([(cx - hw, y), (cx + hw, y)], edge * 0.7, 0.8)
        # a long panel on the shaft
        self.ln([(cx - hw * 0.45, base - (base - top) * 0.05)] + arch_pts(cx, top + hw * 2.4, hw * 0.45)
                + [(cx + hw * 0.45, base - (base - top) * 0.05)], edge * 0.6, 0.6)
        if hw > 0.006 and base - top > 0.12:
            # a niche with a small statue part way up the shaft
            ny = top + (base - top) * 0.28
            self.dark([(cx - hw * 0.6, ny + hw * 3.2)] + arch_pts(cx, ny, hw * 0.6) + [(cx + hw * 0.6, ny + hw * 3.2)], 5, edge * 0.9, 0.7)
            self.rect(cx - hw * 0.22, ny + hw * 0.35, cx + hw * 0.22, ny + hw * 3.0, tone + 18, edge, 0.5)
            self.circle(cx, ny + hw * 0.1, hw * 0.24, tone + 18, edge, 0.5)
            self.poly([(cx - hw * 0.75, ny - hw * 0.9), (cx, ny - hw * 2.2), (cx + hw * 0.75, ny - hw * 0.9)], tone, edge * 0.85, 0.6)
        self.gablet(cx, top, hw * 1.15, hw * 2.0, tone, edge, crock=hw > 0.006)
        self.spire(cx, top - hw * 0.4, tip, hw * 0.72, tone, edge, crock=True, bands=2, cross=cross)


# ---------------------------------------------------------------- sky, moon, clouds

def sky(w=1280, h=550):
    rect = RECTS["sky"]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    X = xs / w * (rect[2] - rect[0]) + rect[0]
    Y = ys / h
    top = col((5, 5, 10))
    mid = col((40, 6, 12))
    low = col((112, 16, 22))
    rgb = lerp(top, mid, smoothstep(0.08, 0.50, Y))
    rgb = lerp(rgb, low, smoothstep(0.42, 0.74, Y))
    # red sky climbs higher at the sides, cool blue-grey shadow low at the sides (behind the far spires)
    side = smoothstep(0.35, 1.05, np.abs(X))
    rgb = lerp(rgb, col((92, 12, 18)), side * smoothstep(0.10, 0.45, Y) * 0.7)
    rgb = lerp(rgb, col((36, 40, 58)), smoothstep(0.45, 0.95, np.abs(X)) * smoothstep(0.50, 0.80, Y) * 0.65)
    # a warm haze around the moon
    mx, my, mr = MOON
    d = np.hypot(X - mx, Y - my)
    rgb += col((70, 52, 36)) * (np.exp(-((d / (mr * 1.9)) ** 2)) * 0.55)[..., None]
    # faint stars, fewer near the moon and below the cloud line
    r = np.random.default_rng(11)
    n = 1500
    sxs, sys_ = r.random(n) * w, r.random(n) ** 1.6 * h * 0.6
    star = np.zeros((h, w), np.float32)
    mag = r.random(n) ** 3
    for x, y, m in zip(sxs, sys_, mag):
        xi, yi = int(x), int(y)
        star[yi, xi] += 0.25 + 0.75 * m
    star = np.maximum(star, blur(star, 0.8) * 1.5)
    fade = smoothstep(mr * 1.15, mr * 1.9, d) * (1 - smoothstep(0.30, 0.55, Y))
    rgb += (star * fade)[..., None] * col((230, 222, 210))
    n = vnoise(h, w, 3, 7, 12)
    rgb *= (0.9 + 0.2 * n)[..., None]
    return save_rgb(rgb, "sky")


def moon(size=768):
    n = size * 2
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32)
    X = (xs - n / 2 + 0.5) / (n / 2 * 0.96)
    Y = (ys - n / 2 + 0.5) / (n / 2 * 0.96)
    r = np.hypot(X, Y)
    disc = np.clip((1 - r) * n / 2 * 0.96 / 2 + 0.5, 0, 1)
    z = np.sqrt(np.clip(1 - r * r, 0, 1))
    base = col((236, 220, 186))
    rgb = np.broadcast_to(base, (n, n, 3)).copy()
    # maria: soft darker continents from layered noise
    m = vnoise(n, n, 3, 3, 21, octaves=5, gain=0.55)
    mare = smoothstep(0.50, 0.62, m)
    rgb = lerp(rgb, col((168, 146, 112)), mare * 0.75)
    m2 = vnoise(n, n, 10, 10, 22, octaves=3)
    rgb = lerp(rgb, col((196, 176, 140)), smoothstep(0.45, 0.75, m2) * 0.5)
    # craters: dark floor, bright rim on the lit side, etched outline
    rr = np.random.default_rng(23)
    for i in range(90):
        cx, cy = rr.uniform(-0.9, 0.9, 2)
        if cx * cx + cy * cy > 0.82:
            continue
        cr = 0.015 + rr.random() ** 3 * 0.11
        dd = np.hypot(X - cx, Y - cy) / cr
        floor = smoothstep(1.0, 0.75, dd)
        rim = np.exp(-((dd - 1.0) / 0.12) ** 2)
        lit = np.clip(-(X - cx) * 0.7 - (Y - cy) * 0.7, -1, 1) / cr
        rgb = lerp(rgb, col((150, 128, 98)), floor * 0.5)
        rgb += (rim * np.clip(lit * 0.5 + 0.2, 0, 1) * 0.18)[..., None] * col((255, 240, 210))
        rgb = lerp(rgb, col((110, 92, 70)), rim * np.clip(-lit * 0.5, 0, 1) * 0.35)
    # engraved hatching that curves with the sphere: lines of latitude, visible in the maria
    lat = np.arcsin(np.clip(Y, -1, 1))
    hatch = np.clip((np.sin(lat * 620 + X * 4 + m2 * 6) - 0.5) / 0.5, 0, 1)
    rgb = lerp(rgb, col((112, 92, 70)), hatch * (0.06 + 0.4 * mare))
    stip = np.random.default_rng(24).random((n, n)).astype(np.float32)
    rgb = lerp(rgb, col((140, 120, 90)), (stip > 0.93) * (0.2 + 0.4 * mare))
    # limb darkening and a warm rim
    rgb *= (0.62 + 0.38 * z ** 0.5)[..., None]
    rgb = lerp(rgb, col((214, 170, 120)), smoothstep(0.82, 1.0, r) * 0.35)
    return save_rgba(rgb, disc, "moon", (size, size))


def clouds(name, w, h, seed, dark, lite, lo, hi, profile, streaks=1.0, alpha_max=1.0):
    """Billowing, engraved red cloud banks; exactly periodic in x (the layer scrolls and wraps)."""
    rect = RECTS[name]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / w, ys / h
    Y = V * (rect[3] - rect[1]) + rect[1]
    asp = (rect[2] - rect[0]) / (rect[3] - rect[1])
    # domain warp (periodic in U) makes the masses billow instead of sitting on a grid
    wu = fbm(U, V, 2, seed + 1, True, 4, aspect=asp)
    wv = fbm(U, V, 2, seed + 2, True, 4, aspect=asp)
    U2 = (U + (wu - 0.5) * 0.06) % 1.0
    V2 = V + (wv - 0.5) * 0.35
    big = fbm(U2, V2, 2.2, seed, True, 6, 0.52, aspect=asp * 0.55)
    det = fbm(U2, V2 * 1.0, 9, seed + 3, True, 3, aspect=asp * 0.7)
    dens = big * 0.8 + det * 0.2 + profile(Y)
    a = smoothstep(lo, hi, dens)
    # light from above and from the moon: tops lit, bellies dark
    dy = max(2, int(h * 0.03))
    shift = np.roll(dens, dy, axis=0)            # the density just above
    light = np.clip((dens - shift) * 5 + 0.55, 0, 1)
    rgb = lerp(col(dark), col(lite), np.clip(light * 0.9 + (det - 0.5) * 0.6, 0, 1))
    # engraved streaks: fine lines following the billows
    phase = V2 * h * 0.55 + big * 60
    st = np.clip((np.sin(phase) - 0.55) / 0.45, 0, 1)
    rgb = lerp(rgb, np.clip(rgb * 1.5 + 0.03, 0, 1), st * light * 0.4 * streaks)
    rgb = lerp(rgb, rgb * 0.55, st * (1 - light) * 0.45 * streaks)
    # a bright etched rim where a cloud's edge catches the light
    rim = smoothstep(lo, lo + 0.04, dens) * (1 - smoothstep(lo + 0.04, lo + 0.12, dens)) * light
    rgb = lerp(rgb, np.clip(col(lite) * 1.25, 0, 1), rim * 0.5)
    fade = smoothstep(rect[1], rect[1] + 0.06, Y) * (1 - smoothstep(rect[3] - 0.07, rect[3], Y))
    return save_rgba(rgb, a * alpha_max * fade, name)


def all_clouds():
    clouds("clouds_far", 1280, 286, 31, (60, 6, 12), (206, 40, 40), 0.46, 0.66,
           lambda Y: 0.22 * smoothstep(0.34, 0.56, Y) - 0.18 * smoothstep(0.70, 0.80, Y) - 0.12)
    clouds("clouds_near", 1280, 210, 41, (30, 5, 9), (140, 24, 30), 0.50, 0.68,
           lambda Y: 0.20 * smoothstep(0.50, 0.64, Y) - 0.25 * smoothstep(0.74, 0.84, Y) - 0.08, streaks=0.8,
           alpha_max=0.88)


# ---------------------------------------------------------------- the cathedral

GLASS_RUBY = (170, 20, 30)
GLASS_DEEP = (98, 8, 20)
GLASS_AMBER = (222, 128, 40)
GLASS_GOLD = (240, 192, 104)
GLASS_EMBER = (200, 70, 28)


def cathedral(size=1024):
    b = Board(RECTS["cathedral"], size, size, ss=2)
    g = Gothic(b)
    E = 175      # edge brightness of the front
    BT = 20      # back tone

    # --- the far back: a forest of needles behind the facade (darker, fewer lines)
    for cx, top, tip, hw in ((-0.058, 0.25, 0.10, 0.008), (0.058, 0.25, 0.10, 0.008), (-0.20, 0.30, 0.165, 0.009),
                             (0.20, 0.30, 0.165, 0.009), (-0.105, 0.24, 0.125, 0.007), (0.105, 0.24, 0.125, 0.007),
                             (-0.165, 0.27, 0.145, 0.007), (0.165, 0.27, 0.145, 0.007), (-0.25, 0.40, 0.27, 0.008),
                             (0.25, 0.40, 0.27, 0.008), (-0.30, 0.50, 0.38, 0.008), (0.30, 0.50, 0.38, 0.008),
                             (-0.035, 0.26, 0.15, 0.006), (0.035, 0.26, 0.15, 0.006)):
        g.pinnacle(cx, 0.62, top, tip, hw, BT, 105)

    # --- outer chapels and aisles (step down to the sides)
    for s in (-1, 1):
        lo, hi = sorted((s * 0.225, s * 0.285))
        g.rect(lo, 0.50, hi, 0.745, 28, 140)
        g.poly([(lo, 0.50), ((lo + hi) / 2, 0.46), (hi, 0.50)], 28, 140)
        g.blind_arcade(lo + 0.005, hi - 0.005, 0.53, 0.735, 3, 125)
        for k in (0.30, 0.70):
            x = lo + (hi - lo) * k
            g.dark(lancet_pts(x, 0.565, 0.007, 0.64), 6, 150)
            g.glass_poly(lancet_pts(x, 0.568, 0.0045, 0.637), GLASS_DEEP)
        lo2, hi2 = sorted((s * 0.172, s * 0.225))
        g.rect(lo2, 0.37, hi2, 0.745, 32, 150)
        g.poly([(s * 0.172, 0.37), (s * 0.225, 0.335), (s * 0.225, 0.37)], 32, 150)
        # aisle windows: dark lancets with a red ember of glass
        for k in (0.186, 0.211):
            x = s * k
            g.dark(lancet_pts(x, 0.43, 0.0085, 0.56), 6, 160)
            g.glass_poly(lancet_pts(x, 0.434, 0.0055, 0.556), GLASS_DEEP, "lancet")
            g.lead([(x, 0.425), (x, 0.556)], 0.6)
            for yy in (0.46, 0.49, 0.52):
                g.lead([(x - 0.0055, yy), (x + 0.0055, yy)], 0.6)
        g.blind_arcade(lo2 + 0.003, hi2 - 0.003, 0.585, 0.665, 4, 130)
        g.blind_arcade(lo2 + 0.003, hi2 - 0.003, 0.675, 0.74, 5, 125)
        # flying buttresses from the aisle pinnacles to the towers
        for j, (y0, dy) in enumerate(((0.38, 0.05), (0.49, 0.045))):
            pts_top, pts_bot = [], []
            for k in range(15):
                t = k / 14
                x = s * (0.225 - 0.05 * t)
                y = y0 - dy * math.sin(t * math.pi * 0.5) - 0.012 * t
                pts_top.append((x, y))
                pts_bot.append((x, y + 0.008 + 0.006 * math.sin(t * math.pi)))
            g.poly(pts_top + pts_bot[::-1], 30, 150, 0.9)
        g.pinnacle(s * 0.285, 0.745, 0.46, 0.355, 0.0085, 32, 160)
        g.pinnacle(s * 0.225, 0.745, 0.335, 0.215, 0.0095, 34, 165)

    # --- the central body, gable and spire
    g.spire(0.0, 0.215, 0.016, 0.026, 32, E, bands=5, cross=True)
    g.rect(-0.088, 0.268, 0.088, 0.745, 36, E)
    for x in (-0.088, -0.072, 0.072, 0.088):       # tall shafts up the facade
        g.ln([(x + 0.003, 0.27), (x + 0.003, 0.745)], 120, 0.7)
    g.gablet(0.0, 0.272, 0.088, 0.074, 36, E)
    # rose window frame: a deep pointed arch with three orders of moulding
    for i, (hw, tone) in enumerate(((0.071, 32), (0.065, 24), (0.059, 16))):
        g.poly(lancet_pts(0.0, 0.338, hw, 0.405), tone, E - i * 12, 1.0)
    rose(g, 0.0, 0.338, 0.050)
    # a gallery of kings: niches with little figures under the rose
    g.rect(-0.088, 0.402, 0.088, 0.43, 42, E)
    for i in range(9):
        x = -0.076 + i * 0.019
        g.dark(lancet_pts(x, 0.411, 0.0065, 0.428), 8, 150, 0.7)
        g.rect(x - 0.0022, 0.414, x + 0.0022, 0.428, 44, 150, 0.6)
        g.circle(x, 0.4115, 0.0024, 44, 150, 0.6)
    # the three lancets inside one great arch
    g.poly(lancet_pts(0.0, 0.47, 0.066, 0.60), 26, E)
    g.poly(lancet_pts(0.0, 0.475, 0.060, 0.595), 12, E - 20, 0.9)
    lancets(g)
    g.rect(-0.088, 0.60, 0.088, 0.611, 46, E)
    # the portal: receding archivolts, a crocketed wimperg and a red-lit doorway
    for i, (hw, tone) in enumerate(((0.058, 36), (0.051, 28), (0.044, 21), (0.037, 15), (0.031, 10))):
        g.poly(lancet_pts(0.0, 0.692 - i * 0.003, hw, 0.745), tone, E - i * 10, 0.9)
        if i < 3:
            for s in (-1, 1):
                x = s * (hw - 0.0035)
                g.rect(x - 0.0022, 0.70, x + 0.0022, 0.728, tone + 16, 150, 0.6)
                g.circle(x, 0.6975, 0.0026, tone + 16, 150, 0.6)
    g.gablet(0.0, 0.662, 0.062, 0.048, 32, E)
    g.dark(lancet_pts(0.0, 0.708, 0.025, 0.745), 3, 170)
    g.glass_poly(lancet_pts(0.0, 0.711, 0.021, 0.745), (126, 14, 18))   # the lit nave seen through the door
    g.lead([(0.0, 0.684), (0.0, 0.745)], 1.6, 12)                         # trumeau

    # --- the two west towers
    for s in (-1, 1):
        cx = s * 0.132
        hw = 0.040
        g.spire(cx, 0.21, 0.036, 0.031, 34, E, bands=5)
        g.rect(cx - hw, 0.205, cx + hw, 0.745, 40, E)
        for x in (cx - hw * 0.55, cx + hw * 0.55):
            g.ln([(x, 0.21), (x, 0.745)], 105, 0.6)
        for y in (0.295, 0.405, 0.525, 0.645):
            g.rect(cx - hw - 0.003, y, cx + hw + 0.003, y + 0.006, 50, E, 0.8)
        for k in (-1, 1):
            x = cx + k * 0.016
            g.dark(lancet_pts(x, 0.232, 0.0095, 0.29), 5, 165)
            g.lead([(x, 0.225), (x, 0.29)], 0.8, 30)
            g.dark(lancet_pts(x, 0.335, 0.010, 0.40), 5, 165)
            g.glass_poly(lancet_pts(x, 0.339, 0.0065, 0.397), GLASS_DEEP, "lancet")
            g.lead([(x, 0.33), (x, 0.397)], 0.7)
            for yy in (0.355, 0.375):
                g.lead([(x - 0.0065, yy), (x + 0.0065, yy)], 0.6)
        g.blind_arcade(cx - hw + 0.003, cx + hw - 0.003, 0.53, 0.64, 4, 135)
        g.blind_arcade(cx - hw + 0.003, cx + hw - 0.003, 0.65, 0.742, 6, 128)
        # a small rose on each tower
        ry = 0.465
        g.circle(cx, ry, 0.0185, 10, 170)
        g.glass_poly([(cx + 0.0135 * math.cos(a / 16 * 2 * math.pi), ry + 0.0135 * math.sin(a / 16 * 2 * math.pi))
                      for a in range(16)], GLASS_EMBER, "rose")
        for a in range(6):
            an = a / 6 * math.pi
            g.lead([(cx - 0.0135 * math.cos(an), ry - 0.0135 * math.sin(an)),
                    (cx + 0.0135 * math.cos(an), ry + 0.0135 * math.sin(an))], 0.7)
        g.ring(cx, ry, 0.0055, 40, 0.8)
        g.ring(cx, ry, 0.0185, 190, 0.9)
        g.blind_arcade(cx - hw + 0.003, cx + hw - 0.003, 0.415, 0.445, 5, 130)
        # buttress pinnacles at the towers' corners and a needle in the middle
        g.pinnacle(cx - s * hw, 0.745, 0.205, 0.105, 0.0075, 42, E)
        g.pinnacle(cx + s * hw, 0.745, 0.205, 0.10, 0.0075, 42, E)
        g.pinnacle(cx, 0.21, 0.198, 0.172, 0.0035, 38, 150)
        # the buttress between tower and nave
        g.pinnacle(s * 0.092, 0.745, 0.235, 0.088, 0.0085, 46, E)
    for x in (-0.048, 0.048):
        g.pinnacle(x, 0.275, 0.25, 0.20, 0.0038, 40, 150)
    return finish_stone(b, g, "cathedral", size, size)


def rose(g, cx, cy, R):
    """Our rose: 16 petals in two rings, an ember-gold centre with a crowned star - leaded and traceried."""
    n = 64
    circ = lambda r: [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]
    g.poly(circ(R * 1.06), 12, 170)
    g.glass_poly(circ(R), GLASS_DEEP, "rose")
    # outer ring: 16 lancet petals, ruby and amber alternating
    for i in range(16):
        a = 2 * math.pi * i / 16
        pts = []
        for j in range(9):
            t = j / 8
            ang = a + (t - 0.5) * (2 * math.pi / 16) * 0.82
            pts.append((cx + R * 0.94 * math.cos(ang), cy + R * 0.94 * math.sin(ang)))
        inner = [(cx + R * 0.58 * math.cos(a + 0.12), cy + R * 0.58 * math.sin(a + 0.12)),
                 (cx + R * 0.52 * math.cos(a), cy + R * 0.52 * math.sin(a)),
                 (cx + R * 0.58 * math.cos(a - 0.12), cy + R * 0.58 * math.sin(a - 0.12))]
        g.glass_poly(pts + inner, GLASS_RUBY if i % 2 == 0 else (132, 14, 26), "rose")
        # a gold bead in each petal
        bx, by = cx + R * 0.76 * math.cos(a), cy + R * 0.76 * math.sin(a)
        g.glass_poly([(bx + R * 0.06 * math.cos(k / 8 * 2 * math.pi), by + R * 0.06 * math.sin(k / 8 * 2 * math.pi))
                      for k in range(8)], GLASS_GOLD if i % 2 == 0 else GLASS_AMBER, "rose")
    # inner ring: 8 trefoil-ish lobes
    for i in range(8):
        a = 2 * math.pi * (i + 0.5) / 8
        bx, by = cx + R * 0.38 * math.cos(a), cy + R * 0.38 * math.sin(a)
        g.glass_poly([(bx + R * 0.13 * math.cos(k / 12 * 2 * math.pi), by + R * 0.13 * math.sin(k / 12 * 2 * math.pi))
                      for k in range(12)], GLASS_EMBER if i % 2 else GLASS_RUBY, "rose")
    # the centre: a gold disc with an eight-pointed ruby star
    g.glass_poly(circ(R * 0.24), GLASS_GOLD, "rose")
    star = []
    for k in range(16):
        rr = R * (0.20 if k % 2 == 0 else 0.09)
        a = 2 * math.pi * k / 16 - math.pi / 2
        star.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    g.glass_poly(star, (200, 40, 34), "rose")
    g.glass_poly(circ(R * 0.05), (255, 236, 180), "rose")
    # tracery: radial bars and rings, drawn last
    for i in range(16):
        a = 2 * math.pi * (i + 0.5) / 16
        g.lead([(cx + R * 0.56 * math.cos(a), cy + R * 0.56 * math.sin(a)), (cx + R * math.cos(a), cy + R * math.sin(a))], 1.6, 26)
    for r in (0.24, 0.56, 1.0):
        pts = circ(R * r)
        g.lead(pts + [pts[0]], 1.8 if r > 0.9 else 1.3, 26)
    for i in range(8):
        a = 2 * math.pi * i / 8
        g.lead([(cx + R * 0.24 * math.cos(a), cy + R * 0.24 * math.sin(a)), (cx + R * 0.56 * math.cos(a), cy + R * 0.56 * math.sin(a))], 1.1, 26)
    g.ring(cx, cy, R * 1.06, 190, 1.0)
    g.ring(cx, cy, R * 1.0, 120, 0.7)


def lancets(g):
    """Three tall lancets: diamond quarries of ruby and crimson, gold medallions, a robed figure with a halo in the middle."""
    for cx, hw, ys in ((-0.036, 0.0135, 0.478), (0.0, 0.0155, 0.468), (0.036, 0.0135, 0.478)):
        g.dark(lancet_pts(cx, ys, hw + 0.0035, 0.595), 6, 180)
        body = lancet_pts(cx, ys, hw, 0.592)
        g.glass_poly(body, GLASS_RUBY, "lancet")
        step = 0.0068
        y = ys - hw * 1.7
        row = 0
        while y < 0.592:
            for k in range(-3, 4):
                x = cx + k * step + (step / 2 if row % 2 else 0)
                if abs(x - cx) > hw - 0.0015:
                    continue
                c = GLASS_DEEP if (k + row) % 3 == 0 else ((196, 36, 36) if (k * 7 + row) % 5 else GLASS_EMBER)
                g.glass_poly([(x, y - step * 0.42), (x + step * 0.42, y), (x, y + step * 0.42), (x - step * 0.42, y)], c, "lancet")
            y += step * 0.5
            row += 1
        if cx == 0.0:
            # the figure: halo, head, a long robe widening down, hands joined - in gold and ember
            hy = 0.47
            g.glass_poly([(cx + 0.0085 * math.cos(a / 16 * 2 * math.pi), hy + 0.0085 * math.sin(a / 16 * 2 * math.pi))
                          for a in range(16)], GLASS_GOLD, "lancet")
            g.glass_poly([(cx + 0.0038 * math.cos(a / 12 * 2 * math.pi), hy + 0.001 + 0.0045 * math.sin(a / 12 * 2 * math.pi))
                          for a in range(12)], (250, 222, 170), "lancet")
            g.glass_poly([(cx - 0.005, hy + 0.007), (cx + 0.005, hy + 0.007), (cx + 0.0105, 0.586), (cx - 0.0105, 0.586)], (186, 92, 34), "lancet")
            g.glass_poly([(cx - 0.0028, hy + 0.01), (cx + 0.0028, hy + 0.01), (cx + 0.0042, 0.586), (cx - 0.0042, 0.586)], (150, 40, 26), "lancet")
            g.glass_poly([(cx - 0.0035, 0.505), (cx + 0.0035, 0.505), (cx, 0.515)], GLASS_GOLD, "lancet")
        else:
            for my in (0.47, 0.51, 0.548, 0.578):
                g.glass_poly([(cx + 0.0068 * math.cos(a / 12 * 2 * math.pi), my + 0.0068 * math.sin(a / 12 * 2 * math.pi))
                              for a in range(12)], GLASS_GOLD if my not in (0.51, 0.578) else GLASS_AMBER, "lancet")
                g.glass_poly([(cx + 0.0032 * math.cos(a / 8 * 2 * math.pi), my + 0.0032 * math.sin(a / 8 * 2 * math.pi))
                              for a in range(8)], GLASS_EMBER, "lancet")
        for k in (-0.5, 0.5):
            g.lead([(cx + hw * k, ys - hw * 0.6), (cx + hw * k, 0.592)], 0.5, 30)
        y = ys
        while y < 0.592:
            g.lead([(cx - hw, y), (cx + hw, y)], 0.7, 24)
            y += 0.013
        g.lead(body + [body[0]], 1.2, 24)


def finish_stone(b, g, name, w, h, warm_rim=True):
    """Turn the Gothic layers into an etched, moonlit stone image."""
    X, Y = b.grid()
    sil = arr(g.sil)
    tone = arr(g.tone)
    line = arr(g.line)
    gm = arr(g.gmask)
    glass = np.asarray(g.glass, np.float32) / 255
    # stone base: ink to dark slate, a little cooler low down
    tn = np.clip(tone * 2.0, 0, 1)
    stone = lerp(col((7, 7, 10)), col((74, 70, 82)), tn)
    # weathering: mottled value, streaks running down from ledges
    mott = fbm(X * 0 + (X - b.x0) / (b.x1 - b.x0), (Y - b.y0) / (b.y1 - b.y0), 14, 53, False, 5)
    drip = fbm((X - b.x0) / (b.x1 - b.x0), (Y - b.y0) / (b.y1 - b.y0) * 0.08, 60, 54, False, 3)
    stone *= (0.62 + 0.62 * mott * (0.7 + 0.6 * drip))[..., None]
    # engraved cross-hatching in the darker stone (darker lines across the grey)
    d1 = np.clip((np.sin((X * b.sx + Y * b.sy) / b.ss * 2 * math.pi / 4.2) - 0.55) / 0.45, 0, 1)
    stone *= (1 - d1 * (1 - mott) * 0.5)[..., None]
    # engraved hatching: fine verticals that show more on lighter stone
    hx = np.clip((np.sin(X * b.sx / b.ss * 2 * math.pi / 3.0) - 0.35) / 0.65, 0, 1)
    nz = vnoise(b.H, b.W, 30, 30, 51, octaves=3)
    stone = stone + (hx * np.clip(tone * 2.2, 0, 1) * (0.5 + nz) * 0.13)[..., None] * col((150, 150, 170))
    # ashlar courses
    cy = np.clip((np.sin(Y * b.sy / b.ss * 2 * math.pi / 7.0) - 0.92) / 0.08, 0, 1)
    stone *= (1 - cy * 0.35)[..., None]
    # the etched lines: cool slate, turning warm bone where the moon is behind, red at the low sides
    mx, my, mr = MOON
    dm = np.hypot(X - mx, Y - my)
    moonlit = (1 - smoothstep(mr * 0.7, mr * 1.35, dm)) if warm_rim else np.zeros_like(X)
    redlit = smoothstep(0.08, 0.36, np.abs(X)) * smoothstep(0.30, 0.70, Y) * 0.8
    lc = lerp(col((120, 122, 140)), col((236, 210, 168)), moonlit * 0.85)
    lc = lerp(lc, col((178, 70, 62)), redlit * (1 - moonlit))
    rgb = lerp(stone, lc, line * (0.55 + 0.45 * nz))
    # a moonlit rim: the outer edge of the silhouette catches the light
    sb = blur(sil, b.ss * 1.4)
    rim = np.clip((sil - sb) * 3, 0, 1)
    rgb = lerp(rgb, col((250, 228, 186)), rim * moonlit * 0.9)
    rgb = lerp(rgb, col((190, 60, 54)), rim * redlit * 0.6)
    # glass on top, with a soft inner glow and darker lead
    gl = glass * (0.62 + 0.3 * nz[..., None])
    rgb = lerp(rgb, gl, gm)
    # deeper at the foot (the mist and ground meet it)
    rgb *= (1 - 0.35 * smoothstep(0.62, 0.75, Y))[..., None]
    rgb += grain(b.H, b.W, 52, 0.018)[..., None] * sil[..., None]
    save_rgba(rgb, sil, name, (w, h))
    # the two glow layers for the additive pulse: the glass alone, softened
    for which, m, nm in ((g.grose, arr(g.grose), "glass_rose"), (g.glan, arr(g.glan), "glass_lancets")):
        if not m.any():
            continue
        glow_rgb = glass * m[..., None]
        soft = np.stack([blur(glow_rgb[..., i], b.ss * 3) for i in range(3)], -1)
        wide = np.stack([blur(glow_rgb[..., i], b.ss * 14) for i in range(3)], -1)
        out = glow_rgb * 0.35 + soft * 0.6 + wide * 1.2
        a = np.clip(out.max(-1), 0, 1)
        save_rgba(out / np.maximum(a, 1e-4)[..., None], a, nm, (w // 2, h // 2))
    return None


# ---------------------------------------------------------------- far spires (cool, hazy, behind the near clouds)

def spires_far(w=2560, h=748):
    b = Board(RECTS["spires_far"], w, h, ss=2)
    g = Gothic(b)
    r = np.random.default_rng(61)
    for s in (-1, 1):
        # two distant churches each side and a scatter of lone needles
        for cx, base, top, tip, hw in ((0.50, 0.78, 0.42, 0.26, 0.030), (0.66, 0.78, 0.50, 0.38, 0.022),
                                       (0.86, 0.78, 0.36, 0.20, 0.034), (1.02, 0.78, 0.47, 0.33, 0.026)):
            x = s * (cx + r.uniform(-0.02, 0.02))
            g.rect(x - hw, top, x + hw, base, 40, 110)
            g.spire(x, top + 0.002, tip, hw * 0.8, 40, 110, bands=3, cross=r.random() < 0.4)
            for k in (-1, 1):
                g.pinnacle(x + k * hw, base, top + 0.03, top - 0.05 - r.uniform(0, 0.04), hw * 0.22, 40, 100)
            for yy in np.arange(top + 0.04, base - 0.05, 0.07):
                g.dark(lancet_pts(x, yy + 0.02, hw * 0.28, yy + 0.06), 8, 110)
            g.blind_arcade(x - hw, x + hw, top + 0.01, base, 4, 90)
        # a long nave roof between them
        g.poly([(s * 0.47, 0.60), (s * 0.58, 0.55), (s * 0.70, 0.58), (s * 0.84, 0.54), (s * 1.0, 0.58), (s * 1.16, 0.56),
                (s * 1.16, 0.80), (s * 0.47, 0.80)], 30, 90)
        for x in np.arange(0.48, 1.16, 0.035):
            g.pinnacle(s * x, 0.62, 0.575 - r.uniform(0, 0.03), 0.52 - r.uniform(0, 0.06), 0.004, 34, 90)
    X, Y = b.grid()
    sil, tone, line = arr(g.sil), arr(g.tone), arr(g.line)
    haze = smoothstep(0.50, 0.76, Y)
    rgb = lerp(col((26, 28, 40)), col((58, 60, 78)), tone * 2)
    rgb = lerp(rgb, col((130, 130, 156)), line * 0.6)
    rgb = lerp(rgb, col((92, 70, 84)), haze * 0.6)
    sb = blur(sil, b.ss * 1.5)
    rim = np.clip((sil - sb) * 3, 0, 1)
    rgb = lerp(rgb, col((186, 70, 66)), rim * 0.5)
    a = sil * (1 - smoothstep(0.66, 0.79, Y) * 0.9)
    save_rgba(rgb, a, "spires_far", (w, h))


# ---------------------------------------------------------------- the river geometry (shared with the Java strip)

def river_at(y):
    """Centre x and half width of the river at artboard y (None above its head)."""
    if y < RIVER[0][1]:
        return None
    for (x0, y0, w0), (x1, y1, w1) in zip(RIVER, RIVER[1:]):
        if y <= y1:
            t = (y - y0) / (y1 - y0)
            return x0 + (x1 - x0) * t, w0 + (w1 - w0) * t
    return RIVER[-1][0], RIVER[-1][2]


def river_uv(X, Y):
    """Per-pixel river coordinates: u in -1..1 across (|u| > 1 is the bank), v the perspective distance."""
    ys = np.array([p[1] for p in RIVER])
    xs = np.array([p[0] for p in RIVER])
    ws = np.array([p[2] for p in RIVER])
    cx = np.interp(Y, ys, xs)
    hw = np.interp(Y, ys, ws)
    u = (X - cx) / hw
    u = np.where(Y < ys[0], 9.0, u)
    v = 0.08 / np.maximum(Y - HORIZON, 0.02)
    return u.astype(np.float32), v.astype(np.float32), hw


# ---------------------------------------------------------------- the ground: steps, banks, rocks, the crimson river

def rock_poly(cx, cy, r, rr, flat=0.35, n=11):
    pts = []
    for i in range(n):
        a = math.pi + math.pi * i / (n - 1)          # upper half, left to right
        k = 1 + rr.uniform(-0.22, 0.18)
        pts.append((cx + r * math.cos(a) * k * 1.25, cy + r * math.sin(a) * k))
    pts.append((cx + r * 1.2, cy + r * flat))
    pts.append((cx - r * 1.2, cy + r * flat))
    return pts


def ground(w=2560, h=484):
    b = Board(RECTS["ground"], w, h, ss=2)
    X, Y = b.grid()
    rr = np.random.default_rng(71)
    u, v, hw = river_uv(X, Y)
    U01 = (X - b.x0) / (b.x1 - b.x0)
    V01 = (Y - b.y0) / (b.y1 - b.y0)
    # terrain top: the plinth under the cathedral, low hills rising to the sides
    hill = fbm(U01, U01 * 0 + 0.3, 7, 72, False, 4)
    top = 0.742 - 0.07 * smoothstep(0.30, 1.1, np.abs(X)) + (hill - 0.5) * 0.05 * smoothstep(0.25, 0.5, np.abs(X))
    land = smoothstep(top - 0.002, top + 0.002, Y)
    # earth: near-black with cool haze far off, mossy grain, short grass strokes
    n1 = fbm(U01, V01, 30, 73, False, 4)
    depth = smoothstep(0.70, 1.0, Y)
    rgb = lerp(col((52, 46, 58)), col((14, 12, 14)), depth)
    rgb *= (0.7 + 0.6 * n1)[..., None]
    gr = Image.new("L", (b.W, b.H), 0)
    dg = ImageDraw.Draw(gr)
    for i in range(26000):
        x, y = rr.uniform(b.x0, b.x1), rr.uniform(0.70, 1.0)
        L = 0.004 + 0.02 * (y - 0.70) / 0.30
        lean = rr.uniform(-0.4, 0.4) * L
        p0, p1 = b.P(x, y), b.P(x + lean, y - L)
        dg.line([p0, p1], fill=int(rr.uniform(60, 160)), width=max(1, int(1 + 3 * (y - 0.7) / 0.3)))
    grass = arr(gr)
    rgb = lerp(rgb, col((70, 62, 58)), grass * 0.5)

    # the steps of the plinth, crossing the cascade's channel
    st = Image.new("L", (b.W, b.H), 0)
    sl = Image.new("L", (b.W, b.H), 0)
    ds, dl = ImageDraw.Draw(st), ImageDraw.Draw(sl)
    for i in range(5):
        y0 = 0.745 + i * 0.0105
        hwid = 0.075 + i * 0.012
        ds.polygon(b.pts([(-hwid, y0), (hwid, y0), (hwid + 0.004, y0 + 0.0105), (-hwid - 0.004, y0 + 0.0105)]), fill=150 - i * 12)
        dl.line(b.pts([(-hwid, y0), (hwid, y0)]), fill=200, width=3)
        for k in range(-6, 7):
            x = k * hwid / 6.5
            dl.line(b.pts([(x, y0 + 0.001), (x * 1.03, y0 + 0.0095)]), fill=90, width=2)
    stv, slv = arr(st), arr(sl)
    rgb = lerp(rgb, lerp(col((24, 22, 28)), col((70, 66, 76)), stv)[...], (stv > 0) * 1.0)
    rgb = lerp(rgb, col((150, 140, 150)), slv * 0.6)

    # rocks: big dark wet boulders along both banks, sizes by distance
    rk = Image.new("L", (b.W, b.H), 0)     # coverage
    rt = Image.new("L", (b.W, b.H), 0)     # tone
    rl = Image.new("L", (b.W, b.H), 0)     # etched lines
    dk, dt, dl2 = ImageDraw.Draw(rk), ImageDraw.Draw(rt), ImageDraw.Draw(rl)
    places = []
    for y in np.arange(0.775, 1.03, 0.0045):
        c = river_at(y)
        if c is None:
            continue
        cx, hwr = c
        persp = (y - HORIZON) / 0.30
        for side in (-1, 1):
            if rr.random() < 0.55:
                off = hwr * rr.uniform(0.85, 1.25)
                places.append((cx + side * off, y, 0.006 + 0.03 * persp * rr.uniform(0.5, 1.1)))
        if rr.random() < 0.25:
            places.append((cx + rr.uniform(-1.4, 1.4) * hwr * 2.4, y, 0.004 + 0.02 * persp * rr.random()))
    # rocks round the foot of the steps (the cascade breaks over them)
    for k in range(-9, 10):
        places.append((k * 0.016 + rr.uniform(-0.004, 0.004), 0.797 + rr.uniform(-0.004, 0.006), 0.008 + rr.random() * 0.006))
    for x, y, r in sorted(places, key=lambda p: p[1]):
        ps = b.pts(rock_poly(x, y, r, rr))
        tone = int(rr.uniform(40, 90))
        dk.polygon(ps, fill=255)
        dt.polygon(ps, fill=tone)
        dl2.polygon(ps, fill=0)
        dl2.line(ps[:-2], fill=int(rr.uniform(150, 230)), width=max(2, int(b.L(r) * 0.06)))
        # facets and cracks
        for j in range(rr.integers(2, 5)):
            a0 = rr.uniform(0.15, 0.85)
            p0 = ps[int(a0 * (len(ps) - 3))]
            dl2.line([p0, ((p0[0] + b.P(x, y)[0]) / 2 + rr.uniform(-1, 1) * b.L(r) * 0.3, (p0[1] + b.P(x, y)[1]) / 2 + b.L(r) * 0.2)],
                     fill=int(rr.uniform(70, 140)), width=max(1, int(b.L(r) * 0.03)))
    rcov, rtone, rline = arr(rk), arr(rt), arr(rl)
    # hatching on rocks: diagonal strokes, more on the lit tops
    hat = np.clip((np.sin((X * b.sx - Y * b.sy) / b.ss * 2 * math.pi / 3.5) - 0.4) / 0.6, 0, 1)
    rock_rgb = lerp(col((7, 7, 10)), col((50, 48, 58)), rtone * 1.6)
    rock_rgb = lerp(rock_rgb, col((110, 106, 122)), hat * rtone * 0.5)
    rock_rgb = lerp(rock_rgb, col((196, 190, 200)), rline * 0.85)
    # river-facing faces pick up the crimson
    rock_rgb = lerp(rock_rgb, col((150, 24, 30)), np.clip(1.6 - np.abs(u), 0, 1)[..., None][..., 0] * 0.35)

    # the river
    edge_n = fbm(U01, V01, 40, 74, False, 3)
    au = np.abs(u)
    water = 1 - smoothstep(0.92 + edge_n * 0.12, 1.02 + edge_n * 0.12, au)
    water *= smoothstep(RIVER[0][1] - 0.004, RIVER[0][1] + 0.004, Y)
    uu = np.clip(u * 0.5 + 0.5, 0, 1)
    sw = fbm(uu * 0.5, v * 0.5, 5, 75, False, 5)
    wrgb = lerp(col((134, 12, 22)), col((52, 3, 8)), np.clip(au ** 2 * 0.7 + (0.55 - sw) * 1.6, 0, 1))
    # glints and dark troughs: short horizontal dashes that stretch with distance (the engraving of the water)
    dash = fbm(uu * 0.5, v * 2.0, 14, 76, False, 3, aspect=0.35)
    glint = smoothstep(0.66, 0.74, dash)
    wrgb = lerp(wrgb, col((226, 70, 74)), glint * 0.6)
    trough = smoothstep(0.30, 0.24, dash)
    wrgb = lerp(wrgb, col((30, 1, 4)), trough * 0.5)
    lines_ = np.clip((np.sin(v * 700 + sw * 20) - 0.85) / 0.15, 0, 1)
    wrgb = lerp(wrgb, col((190, 40, 46)), lines_ * 0.18)
    # a broad soft moon sheen down the middle
    wrgb += (np.exp(-(X / 0.035) ** 2) * smoothstep(0.82, 0.95, Y) * 0.12)[..., None] * col((255, 210, 170))
    # dark wet shadow under the banks
    wrgb *= (1 - smoothstep(0.6, 1.0, au) * 0.45)[..., None]

    # the skull: half sunk, a little off the line of the river, catching the moon on its crown
    sk = Image.new("L", (b.W, b.H), 0)
    skl = Image.new("L", (b.W, b.H), 0)
    dsk, dskl = ImageDraw.Draw(sk), ImageDraw.Draw(skl)
    sx, sy, srad = 0.112, 0.948, 0.019
    dsk.ellipse(b.pts([(sx - srad, sy - srad * 1.05), (sx + srad, sy + srad * 0.9)]), fill=150)
    dsk.polygon(b.pts([(sx - srad * 0.7, sy + srad * 0.3), (sx + srad * 0.7, sy + srad * 0.3), (sx + srad * 0.45, sy + srad * 0.95), (sx - srad * 0.45, sy + srad * 0.95)]), fill=130)
    for ex in (-0.42, 0.42):
        dsk.ellipse(b.pts([(sx + srad * ex - srad * 0.27, sy + srad * 0.05), (sx + srad * ex + srad * 0.27, sy + srad * 0.5)]), fill=10)
    dsk.polygon(b.pts([(sx, sy + srad * 0.5), (sx - srad * 0.1, sy + srad * 0.72), (sx + srad * 0.1, sy + srad * 0.72)]), fill=10)
    dskl.arc(b.pts([(sx - srad, sy - srad * 1.05), (sx + srad, sy + srad * 0.9)]), 190, 330, fill=230, width=3)
    dskl.line(b.pts([(sx - srad * 0.15, sy - srad * 0.9), (sx + srad * 0.05, sy - srad * 0.4), (sx - srad * 0.2, sy - srad * 0.1)]), fill=110, width=2)
    skv, sklv = arr(sk), arr(skl)
    sk_cov = (skv > 0).astype(np.float32) * (1 - smoothstep(sy + srad * 0.55, sy + srad * 0.62, Y))   # the waterline
    sk_rgb = lerp(col((20, 16, 18)), col((150, 138, 124)), skv)
    sk_rgb = lerp(sk_rgb, col((235, 218, 190)), sklv * 0.8)
    sk_rgb = lerp(sk_rgb, col((120, 20, 26)), smoothstep(sy, sy + srad * 0.6, Y) * 0.5)
    hatch_s = np.clip((np.sin((X * b.sx + Y * b.sy * 0.3) / b.ss * 2 * math.pi / 3) - 0.3) / 0.7, 0, 1)
    sk_rgb *= (1 - hatch_s * 0.3 * (1 - skv))[..., None]
    # ripple rings round the skull
    dd = np.hypot((X - sx) / 2.6, (Y - (sy + srad * 0.58)))
    rings = np.clip((np.sin(dd / srad * 14) - 0.5) / 0.5, 0, 1) * smoothstep(srad * 1.6, srad * 0.7, dd) * smoothstep(srad * 0.3, srad * 0.5, dd)

    out = lerp(rgb, wrgb, water)
    out = lerp(out, col((220, 70, 70)), rings * water * 0.4)
    out = lerp(out, sk_rgb, sk_cov)
    out = lerp(out, rock_rgb, rcov)
    # the cascade's channel down the steps: dark wet red (the animated fall is drawn over it)
    cx_, ty, thw, by, bhw = CASCADE
    k = np.clip((Y - ty) / (by - ty), 0, 1)
    ch = (np.abs(X - cx_) < thw + (bhw - thw) * k) & (Y > ty - 0.002) & (Y < by + 0.006)
    out = lerp(out, col((80, 6, 12)), ch.astype(np.float32) * 0.92)
    alpha = np.clip(land + rcov + water, 0, 1)
    save_rgba(out, alpha, "ground", (w, h))


# ---------------------------------------------------------------- flower fields (two depths, sway in game)

def flower_layer(name, w, h, seed, y_from, y_to, scale_far, scale_near, count):
    b = Board(RECTS[name], w, h, ss=2)
    rr = np.random.default_rng(seed)
    img = Image.new("RGBA", (b.W, b.H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    items = []
    for i in range(count):
        y = y_from + (y_to - y_from) * rr.random() ** 0.8
        x = rr.uniform(b.x0, b.x1)
        p_ = (y - y_from) / (y_to - y_from)
        size = scale_far + (scale_near - scale_far) * p_
        skip = False
        for yy in (y, y - size, y - size * 2):
            c = river_at(yy)
            if c is not None and abs(x - c[0]) < c[1] * 1.08 + size * 1.2:
                skip = True
        if skip:
            continue
        if abs(x) < 0.12 and y < 0.80:          # keep the steps clear
            continue
        if y < 0.745 and abs(x) < 0.30:         # nothing in front of the facade's foot
            continue
        items.append((y, x))
    items.sort()
    for y, x in items:
        p = (y - y_from) / (y_to - y_from)
        sc = scale_far + (scale_near - scale_far) * p
        kind = rr.random()
        shade = 0.55 + 0.45 * p
        if kind < 0.22:
            leafy_tuft(d, b, x, y, sc * 1.6, rr, shade)
        elif kind < 0.40:
            blossom(d, b, x, y, sc * rr.uniform(0.9, 1.6), rr, min(1.0, shade * 1.15))
        else:
            red_cluster(d, b, x, y, sc * rr.uniform(0.8, 1.4), rr, shade)
    a = np.asarray(img, np.float32) / 255
    rgb, al = a[..., :3], a[..., 3]
    # etched shading: fine diagonal lines darken the lower parts of each clump
    X, Y = b.grid()
    hat = np.clip((np.sin((X * b.sx + Y * b.sy) / b.ss * 2 * math.pi / 3.2) - 0.5) / 0.5, 0, 1)
    rgb *= (1 - hat * 0.25)[..., None]
    save_rgba(rgb, al, name, (w, h))


def c8(rgb, shade, j=0.0, rr=None):
    k = shade * (1 + (rr.uniform(-j, j) if rr is not None else 0))
    return tuple(int(max(0, min(255, c * k))) for c in rgb) + (255,)


def leafy_tuft(d, b, x, y, s, rr, shade):
    for i in range(rr.integers(4, 9)):
        ang = rr.uniform(-1.2, 1.2)
        L = s * rr.uniform(0.6, 1.2)
        tip = (x + math.sin(ang) * L, y - math.cos(ang) * L)
        mid = ((x + tip[0]) / 2 + math.cos(ang) * L * 0.12, (y + tip[1]) / 2)
        mid2 = ((x + tip[0]) / 2 - math.cos(ang) * L * 0.12, (y + tip[1]) / 2)
        d.polygon(b.pts([(x, y), mid, tip, mid2]), fill=c8((22, 30, 22), shade, 0.3, rr))
        d.line(b.pts([(x, y), tip]), fill=c8((70, 84, 62), shade, 0.2, rr), width=2)


def red_cluster(d, b, x, y, s, rr, shade):
    # stems and dark leaves first
    for i in range(rr.integers(2, 5)):
        lx = x + rr.uniform(-1, 1) * s
        d.polygon(b.pts([(lx, y), (lx - s * 0.25, y - s * 0.5), (lx + s * 0.1, y - s * 0.9), (lx + s * 0.2, y - s * 0.4)]),
                  fill=c8((18, 24, 18), shade, 0.3, rr))
    n = rr.integers(5, 14)
    for i in range(n):
        fx = x + rr.normal(0, 0.55) * s
        fy = y - s * 0.5 - abs(rr.normal(0, 0.45)) * s
        r = s * rr.uniform(0.16, 0.30)
        base = (rr.uniform(150, 205), rr.uniform(12, 30), rr.uniform(20, 34))
        d.line(b.pts([(fx, fy), (fx + rr.uniform(-0.1, 0.1) * s, y)]), fill=c8((26, 34, 24), shade), width=2)
        # five petals round a dark heart, a lit rim on top
        for k in range(5):
            a = 2 * math.pi * k / 5 + rr.uniform(0, 1)
            px, py = fx + math.cos(a) * r * 0.55, fy + math.sin(a) * r * 0.45
            d.ellipse(b.pts([(px - r * 0.55, py - r * 0.45), (px + r * 0.55, py + r * 0.45)]), fill=c8(base, shade * 0.85, 0.15, rr))
        d.ellipse(b.pts([(fx - r * 0.7, fy - r * 0.55), (fx + r * 0.7, fy + r * 0.2)]), fill=c8((base[0] * 1.15, base[1] * 2.2, base[2] * 1.6), shade, 0.1, rr))
        d.ellipse(b.pts([(fx - r * 0.18, fy - r * 0.12), (fx + r * 0.18, fy + r * 0.14)]), fill=c8((40, 6, 8), 1.0))


def blossom(d, b, x, y, s, rr, shade):
    """A clump of ivory umbels (elder-like): several small domes of florets, each shaded from the top."""
    for i in range(rr.integers(2, 4)):
        lx = x + rr.uniform(-0.8, 0.8) * s
        d.polygon(b.pts([(lx, y), (lx - s * 0.35, y - s * 0.45), (lx + s * 0.05, y - s * 0.75), (lx + s * 0.3, y - s * 0.3)]),
                  fill=c8((20, 26, 20), shade, 0.3, rr))
    for u_ in range(rr.integers(3, 7)):
        cxs = x + rr.normal(0, 0.45) * s
        cys = y - s * (0.55 + abs(rr.normal(0, 0.35)))
        R = s * rr.uniform(0.18, 0.32)
        d.line(b.pts([(cxs, cys), (cxs + rr.uniform(-0.1, 0.1) * s, y)]), fill=c8((30, 38, 26), shade), width=2)
        d.ellipse(b.pts([(cxs - R * 1.05, cys - R * 0.7), (cxs + R * 1.05, cys + R * 0.75)]), fill=c8((60, 50, 40), shade))
        for i in range(int(14 + 900 * R * R / 0.0001 * 0.01)):
            a = rr.uniform(0, 2 * math.pi)
            rad = R * math.sqrt(rr.random())
            fx, fy = cxs + math.cos(a) * rad, cys + math.sin(a) * rad * 0.7
            lit = 1 - (fy - (cys - R * 0.7)) / (R * 1.4)
            tone = 0.6 + 0.55 * lit + rr.uniform(-0.15, 0.12)
            col_ = (242 * tone, 228 * tone, 192 * tone * 0.95)
            fr = max(R * 0.16, 0.0007) * rr.uniform(0.7, 1.2)
            d.ellipse(b.pts([(fx - fr, fy - fr * 0.8), (fx + fr, fy + fr * 0.8)]), fill=c8(col_, shade))


def fields():
    flower_layer("field_far", 2560, 341, 81, 0.69, 0.93, 0.006, 0.016, 5200)
    flower_layer("field_near", 2560, 242, 82, 0.84, 1.05, 0.016, 0.060, 1500)


# ---------------------------------------------------------------- framing: ruined walls, gnarled trees, vines

def frame_side(name, mirror, seed):
    w, h = 726, 1100
    b = Board((0.0, 0.0, FRAME_W, 1.0), w, h, ss=2)
    rr = np.random.default_rng(seed)
    cov = b.mask(); tone = b.mask(); line = b.mask()
    dc, dt, dl = ImageDraw.Draw(cov), ImageDraw.Draw(tone), ImageDraw.Draw(line)
    fol = Image.new("RGBA", (b.W, b.H), (0, 0, 0, 0))
    df = ImageDraw.Draw(fol)

    def shape(ps, t, e=170, wdt=2):
        p = b.pts(ps)
        dc.polygon(p, fill=255)
        dt.polygon(p, fill=int(t))
        dl.polygon(p, fill=0)
        if e:
            dl.line(p + [p[0]], fill=int(e), width=wdt)

    def ln(ps, v, wdt=2):
        dl.line(b.pts(ps), fill=int(v), width=wdt)

    # x is measured from the screen edge inward (0 = edge). The ruin: a jagged cliff of masonry hugging the edge,
    # thick at the bottom and over the top corner (a cave mouth), thinner in between.
    prof = []
    for i in range(41):
        y = i / 40
        wdt = 0.10 + 0.26 * smoothstep(0.55, 1.0, y) + 0.16 * (1 - smoothstep(0.0, 0.28, y))
        prof.append((wdt, y))
    jag = rr.uniform(-1, 1, len(prof))
    jag = np.convolve(jag, np.ones(5) / 5, mode="same") * 0.05
    prof = [(w_ + j, y) for (w_, y), j in zip(prof, jag)]
    cliff = [(-0.05, -0.01)] + [(x, y) for x, y in prof] + [(-0.05, 1.01)]
    shape(cliff, 46, 160)
    # masonry blocks inside the cliff
    for y in np.arange(0.0, 1.0, 0.022):
        wdt = np.interp(y, [p[1] for p in prof], [p[0] for p in prof])
        x = -0.01 + (0.02 if int(y / 0.022) % 2 else 0)
        while x < wdt - 0.02:
            bw = rr.uniform(0.03, 0.06)
            ln([(x, y), (min(x + bw, wdt - 0.01), y)], 110)
            ln([(x, y), (x, y + 0.022)], 90)
            x += bw
    # a broken pointed arch springing from the wall (the ruin of an older church)
    ax, ays, ahw = 0.20, 0.43, 0.13
    arch_outer = arch_pts(ax + ahw, ays, ahw + 0.03, 1.0, 24)
    arch_inner = arch_pts(ax + ahw, ays, ahw - 0.005, 1.0, 24)
    cut = 15                                         # broken off part way over
    shape(arch_outer[:cut] + arch_inner[:cut][::-1], 52, 180)
    for k in range(0, cut, 2):
        ln([arch_outer[k], arch_inner[k]], 120)
    # its pier, with a colonette
    shape([(ax - 0.035, ays), (ax + 0.005, ays), (ax + 0.012, 1.01), (ax - 0.04, 1.01)], 50, 170)
    ln([(ax - 0.012, ays + 0.02), (ax - 0.010, 1.0)], 140)
    for y in np.arange(ays + 0.02, 1.0, 0.05):
        ln([(ax - 0.035, y), (ax + 0.005, y)], 100)
    # rubble at the bottom
    for i in range(18):
        x, y, r = rr.uniform(0.0, 0.42), rr.uniform(0.86, 1.0), rr.uniform(0.012, 0.04)
        shape(rock_poly(x, y, r, rr), rr.uniform(40, 70), 180)

    # the gnarled tree: a twisted trunk from the bottom, limbs reaching up and in, bare twigs
    def limb(x0, y0, ang, L, wdt, depth):
        segs = 6
        pts_l, pts_r = [], []
        x, y, a = x0, y0, ang
        ends = []
        for k in range(segs + 1):
            ww = wdt * (1 - k / segs * 0.75)
            nx, ny = math.cos(a), math.sin(a)
            pts_l.append((x - ny * ww, y + nx * ww))
            pts_r.append((x + ny * ww, y - nx * ww))
            if k < segs:
                a += rr.uniform(-0.35, 0.35)
                x += math.sin(a) * L / segs
                y -= math.cos(a) * L / segs
            if 1 < k < segs and depth > 0 and rr.random() < 0.55:
                ends.append((x, y, a + rr.choice([-1, 1]) * rr.uniform(0.5, 1.1), L * rr.uniform(0.45, 0.7), ww * 0.7, depth - 1))
        shape(pts_l + pts_r[::-1], 62, 190, 3)
        # bark: etched lines along the limb
        for f in (-0.5, 0.0, 0.45):
            ln([((l[0] + r_[0]) / 2 + (l[0] - r_[0]) * f * 0.5, (l[1] + r_[1]) / 2 + (l[1] - r_[1]) * f * 0.5)
                for l, r_ in zip(pts_l, pts_r)], 100, 2)
        tips.append((x, y))
        for e in ends:
            limb(*e)

    tips = []
    limb(0.30, 1.02, -0.12, 0.34, 0.034, 3)
    limb(0.30, 0.80, 0.55, 0.30, 0.016, 3)
    limb(0.27, 0.86, -0.75, 0.22, 0.014, 2)
    limb(0.07, 0.22, 0.9, 0.30, 0.012, 3)      # a branch reaching out over the top corner
    # twigs at every tip
    for (x, y) in list(tips):
        for k in range(rr.integers(3, 7)):
            a = rr.uniform(-1.4, 1.6)
            L = rr.uniform(0.02, 0.06)
            ln([(x, y), (x + math.sin(a) * L * 0.5 + rr.uniform(-0.005, 0.005), y - math.cos(a) * L * 0.5),
                (x + math.sin(a) * L, y - math.cos(a) * L)], 120, 2)
    # sparse copper foliage round the tips
    for (x, y) in tips:
        for k in range(rr.integers(60, 160)):
            fx, fy = x + rr.normal(0, 0.026), y + rr.normal(0, 0.02)
            r = rr.uniform(0.0012, 0.0032)
            t = rr.uniform(0.35, 1.1) if rr.random() < 0.85 else rr.uniform(1.1, 1.4)
            c = (int(min(255, 160 * t)), int(min(255, 74 * t)), int(min(255, 42 * t)), 255)
            df.ellipse(b.pts([(fx - r, fy - r * 0.7), (fx + r, fy + r * 0.7)]), fill=c)
    # hanging vines from the cave-mouth overhang and the arch
    for k in range(16):
        x0 = rr.uniform(0.03, 0.30)
        y0 = np.interp(x0, [0, 0.3], [0.05, 0.16]) + rr.uniform(-0.03, 0.05)
        L = rr.uniform(0.12, 0.42)
        pts = []
        for j in range(25):
            t = j / 24
            pts.append((x0 + math.sin(t * 5 + k) * 0.008 + t * 0.015, y0 + t * L))
        ln(pts, 130, 2)
        for j in range(2, 25, 2):
            px, py = pts[j]
            side = 1 if j % 4 else -1
            df.polygon(b.pts([(px, py), (px + side * 0.007, py + 0.004), (px + side * 0.003, py + 0.009)]),
                       fill=(30 + int(rr.uniform(0, 20)), 44, 28, 255))

    # red and ivory flowers spilling over the rubble and up the ledges
    for i in range(160):
        y = 0.80 + rr.random() ** 0.7 * 0.22
        x = rr.uniform(0.02, 0.36) * (0.5 + 0.5 * (y - 0.8) / 0.22)
        sc = 0.018 + 0.03 * (y - 0.8) / 0.22
        k = rr.random()
        if k < 0.3:
            blossom(df, b, x, y, sc * rr.uniform(0.9, 1.4), rr, 0.95)
        elif k < 0.45:
            leafy_tuft(df, b, x, y, sc * 1.5, rr, 0.9)
        else:
            red_cluster(df, b, x, y, sc * rr.uniform(0.9, 1.3), rr, 0.9)

    X, Y = b.grid()
    cv, tv, lv = arr(cov), arr(tone), arr(line)
    nz = fbm(X / FRAME_W, Y, 18, seed + 5, False, 4)
    rgb = lerp(col((8, 8, 11)), col((92, 88, 100)), tv * 1.5)
    rgb *= (0.6 + 0.7 * nz)[..., None]
    hat = np.clip((np.sin((X * b.sx - Y * b.sy) / b.ss * 2 * math.pi / 3.6) - 0.5) / 0.5, 0, 1)
    rgb *= (1 - hat * 0.35)[..., None]
    # etched lines: cool grey, warmer where the red sky reaches them
    lc = lerp(col((128, 126, 140)), col((176, 84, 72)), smoothstep(0.2, 0.7, Y)[..., None][..., 0] * 0.6)
    rgb = lerp(rgb, lc * 1.25, lv * (0.6 + 0.4 * nz))
    # darker toward the screen edge (the frame recedes into shadow)
    rgb *= (0.6 + 0.4 * smoothstep(0.0, 0.22, X))[..., None]
    fa = np.asarray(fol, np.float32) / 255
    rgb = lerp(rgb, fa[..., :3], fa[..., 3])
    a = np.clip(cv + fa[..., 3], 0, 1)
    if mirror:
        rgb, a = rgb[:, ::-1], a[:, ::-1]
    save_rgba(rgb, a, name, (w, h))


def frames():
    frame_side("frame_left", False, 91)
    frame_side("frame_right", True, 92)


# ---------------------------------------------------------------- mist, vignette, sprites

def mist(w=1024, h=286):
    rect = RECTS["mist"]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / w, ys / h
    n = fbm(U, V, 2, 101, True, 5, aspect=(rect[2] - rect[0]) / (rect[3] - rect[1]) * 0.5)
    prof = smoothstep(0.0, 0.45, V) * (1 - smoothstep(0.65, 1.0, V))
    a = smoothstep(0.35, 0.75, n) * prof * 0.55
    rgb = np.broadcast_to(col((150, 116, 126)), (h, w, 3)).copy()
    rgb = lerp(rgb, col((96, 100, 124)), V * 0.6)
    save_rgba(rgb, a, "mist")


def vignette(w=512, h=288):
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    X, Y = (xs / w - 0.5) * 2, (ys / h - 0.45) * 2
    d = np.sqrt((X * 0.85) ** 2 + (Y * 1.05) ** 2)
    a = smoothstep(0.6, 1.5, d) * 0.75 + smoothstep(0.7, 1.0, ys / h) * 0.15
    save_rgba(np.zeros((h, w, 3), np.float32), np.clip(a, 0, 1), "vignette")


def glow(size=256):
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    r = np.hypot(xs - size / 2 + 0.5, ys - size / 2 + 0.5) / (size / 2)
    a = np.exp(-(r / 0.42) ** 2) * (1 - smoothstep(0.9, 1.0, r))
    save_rgba(np.ones((size, size, 3), np.float32), a, "glow")


def sparkle(size=64):
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    X, Y = (xs - size / 2 + 0.5) / (size / 2), (ys - size / 2 + 0.5) / (size / 2)
    r = np.hypot(X, Y)
    a = np.exp(-(r / 0.12) ** 2) + 0.6 * np.exp(-(np.abs(X) / 0.035) - (np.abs(Y) / 0.6)) + 0.6 * np.exp(-(np.abs(Y) / 0.035) - (np.abs(X) / 0.6))
    a *= (1 - smoothstep(0.85, 1.0, r))
    save_rgba(np.ones((size, size, 3), np.float32), np.clip(a, 0, 1), "sparkle")


def bats(fw=128, fh=64):
    """Three wing frames (up, level, down) side by side: a small black bat with scalloped membranes."""
    ss = 4
    img = Image.new("L", (fw * 3 * ss, fh * ss), 0)
    d = ImageDraw.Draw(img)
    for f, lift in enumerate((-0.85, 0.0, 0.75)):
        ox = f * fw * ss
        cx, cy = ox + fw * ss / 2, fh * ss * 0.55
        S = fw * ss / 2 * 0.92
        for side in (-1, 1):
            # wing: shoulder, wrist, three finger tips with scallops between
            sh = (cx + side * S * 0.10, cy - S * 0.05)
            wr = (cx + side * S * 0.45, cy - S * 0.28 * (1 + lift * 0.0) + lift * S * -0.32 * -1)
            tips = [(cx + side * S * 0.98, cy + lift * S * 0.45 - S * 0.05), (cx + side * S * 0.78, cy + lift * S * 0.35 + S * 0.16),
                    (cx + side * S * 0.52, cy + lift * S * 0.18 + S * 0.24)]
            pts = [sh, wr, tips[0]]
            prev = tips[0]
            for t in tips[1:] + [(cx + side * S * 0.12, cy + S * 0.16)]:
                mx, my = (prev[0] + t[0]) / 2, (prev[1] + t[1]) / 2
                pts.append((mx - side * S * 0.02, my - S * 0.07))
                pts.append(t)
                prev = t
            d.polygon(pts, fill=255)
        # body, head, ears
        d.ellipse([cx - S * 0.10, cy - S * 0.14, cx + S * 0.10, cy + S * 0.24], fill=255)
        d.ellipse([cx - S * 0.075, cy - S * 0.24, cx + S * 0.075, cy - S * 0.08], fill=255)
        d.polygon([(cx - S * 0.07, cy - S * 0.19), (cx - S * 0.05, cy - S * 0.34), (cx - S * 0.01, cy - S * 0.2)], fill=255)
        d.polygon([(cx + S * 0.07, cy - S * 0.19), (cx + S * 0.05, cy - S * 0.34), (cx + S * 0.01, cy - S * 0.2)], fill=255)
    a = np.asarray(img.resize((fw * 3, fh), Image.LANCZOS), np.float32) / 255
    rgb = np.broadcast_to(col((14, 8, 10)), a.shape + (3,)).copy()
    save_rgba(rgb, a, "bats")


def petal(size=32):
    ss = 4
    img = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    n = size * ss
    pts = []
    for i in range(40):
        t = i / 39 * 2 * math.pi
        r = 0.42 * (0.6 + 0.4 * math.cos(t)) * (1 - 0.15 * math.cos(2 * t))
        pts.append((n / 2 + math.cos(t) * r * n * 1.0 - n * 0.08, n / 2 + math.sin(t) * r * n * 0.6))
    d.polygon(pts, fill=(196, 26, 36, 255))
    d.line([(n * 0.18, n / 2), (n * 0.78, n / 2 - n * 0.03)], fill=(120, 10, 20, 255), width=ss)
    d.ellipse([n * 0.45, n * 0.36, n * 0.72, n * 0.48], fill=(236, 70, 70, 255))
    img.resize((size, size), Image.LANCZOS).save(os.path.join(OUT, "petal.png"), optimize=True)


def cascade(w=128, h=512):
    """The red fall from the doorway: wraps in v (scrolls down), soft edges in u."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / w, ys / h
    streak = fbm(U * 0 + V, U, 8, 111, True, 4, aspect=0.25)    # long vertical streaks (periodic in V)
    fine = fbm(V, U, 20, 112, True, 3, aspect=0.2)
    rgb = lerp(col((70, 4, 10)), col((220, 44, 50)), np.clip((streak - 0.5) * 3.0 + 0.5 + (fine - 0.5) * 1.2, 0, 1))
    hi = np.clip((fine - 0.62) / 0.2, 0, 1)
    rgb = lerp(rgb, col((255, 120, 110)), hi * 0.6)
    edge = smoothstep(0.0, 0.18, U) * smoothstep(1.0, 0.82, U)
    a = edge * (0.75 + 0.25 * streak)
    save_rgba(rgb, a, "cascade")


def ripples(w=256, h=512):
    """Additive glints for the river strip: u across (bank to bank), v along (wraps)."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / w, ys / h
    dash = fbm(U, V, 3, 121, True, 3, aspect=1.2)
    dash2 = fbm(V, U, 10, 122, True, 2, aspect=0.15)          # periodic in V
    lines = np.clip((np.sin(V * 2 * math.pi * 26 + dash * 7) - 0.82) / 0.18, 0, 1)
    a = lines * smoothstep(0.5, 0.75, dash2) * smoothstep(0.0, 0.2, U) * smoothstep(1.0, 0.8, U)
    rgb = np.broadcast_to(col((255, 120, 110)), (h, w, 3)).copy()
    save_rgba(rgb, np.clip(a, 0, 1), "ripples")


def streak(w=64, h=256):
    """The moon's reflection: broken bright dashes, centred in u (clamped), wrapping in v."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / w, ys / h
    br = fbm(U * 0.3, V, 6, 131, False, 2)
    br = fbm(V, U * 0.3, 14, 131, True, 3, aspect=0.1)
    lines = np.clip((np.sin(V * 2 * math.pi * 18 + br * 4) - 0.3) / 0.7, 0, 1)
    across = np.exp(-((U - 0.5) / 0.16) ** 2) * smoothstep(0.0, 0.1, U) * smoothstep(1.0, 0.9, U)
    a = lines * across * (0.4 + 0.6 * br)
    rgb = np.broadcast_to(col((255, 226, 196)), (h, w, 3)).copy()
    save_rgba(rgb, np.clip(a, 0, 1), "streak")


def grain_tex(size=256):
    r = np.random.default_rng(141).standard_normal((size, size)).astype(np.float32)
    r = r * 0.7 + blur(r, 0.8) * 0.6
    light = (r > 0).astype(np.float32)
    a = np.clip(np.abs(r) * 0.06, 0, 0.16)
    rgb = np.stack([light] * 3, -1)
    save_rgba(rgb, a, "grain")


def sprites():
    glow(); sparkle(); bats(); petal(); cascade(); ripples(); streak(); grain_tex(); vignette(); mist()


# ---------------------------------------------------------------- preview compositor (mirrors LobbyBackdrop.java at t=0)

class Screen:
    def __init__(self, W, H):
        self.W, self.H = W, H
        self.A = max(H, W / ART_W)
        self.top = H - self.A
        self.img = np.zeros((H, W, 3), np.float32)

    def sx(self, x):
        return self.W / 2 + x * self.A

    def sy(self, y):
        return self.top + y * self.A

    def place(self, im, x0, y0, x1, y1, mode="over", tint=(1, 1, 1), alpha=1.0, wrap_dx=None):
        """Draw image im (PIL RGBA/RGB) into the screen rect (screen px)."""
        w, h = int(round(x1 - x0)), int(round(y1 - y0))
        if w <= 0 or h <= 0:
            return
        if wrap_dx is not None:
            # a horizontally wrapping layer: tile it across, offset by wrap_dx (in screen px)
            tile = np.asarray(im.convert("RGBA").resize((w, h), Image.LANCZOS), np.float32) / 255
            reps = int(math.ceil(self.W / w)) + 2
            big = np.concatenate([tile] * reps, 1)
            off = int(round(wrap_dx)) % w
            big = big[:, off:off + self.W + 0]
            self._blend(big, 0, int(round(y0)), mode, tint, alpha)
            return
        arr_ = np.asarray(im.convert("RGBA").resize((w, h), Image.LANCZOS), np.float32) / 255
        self._blend(arr_, int(round(x0)), int(round(y0)), mode, tint, alpha)

    def _blend(self, a, x0, y0, mode, tint, alpha):
        h, w = a.shape[:2]
        sx0, sy0 = max(0, x0), max(0, y0)
        sx1, sy1 = min(self.W, x0 + w), min(self.H, y0 + h)
        if sx1 <= sx0 or sy1 <= sy0:
            return
        src = a[sy0 - y0:sy1 - y0, sx0 - x0:sx1 - x0]
        rgb = src[..., :3] * np.asarray(tint, np.float32)
        al = src[..., 3:4] * alpha
        dst = self.img[sy0:sy1, sx0:sx1]
        if mode == "add":
            dst += rgb * al
        else:
            dst[:] = dst * (1 - al) + rgb * al

    def art(self, im, name, **kw):
        x0, y0, x1, y1 = RECTS[name]
        self.place(im, self.sx(x0), self.sy(y0), self.sx(x1), self.sy(y1), **kw)

    def save(self, path):
        Image.fromarray(np.round(np.clip(self.img, 0, 1) * 255).astype(np.uint8), "RGB").save(path, optimize=True)


def load(name):
    p = os.path.join(OUT, name + ".png")
    return Image.open(p) if os.path.exists(p) else None


# animation constants (LobbyBackdrop.java mirrors these)
RIPPLE_K, RIPPLE_SPEED = 8.0, 0.35          # ripple tiles per unit of v; tiles per second
STREAK_HW = 0.035                            # the moon reflection's half width (A)
CASCADE_TILES, CASCADE_SPEED = 1.6, 0.9      # v tiles over the fall; tiles per second
STARS = [(-0.92, 0.06), (-0.71, 0.13), (-0.55, 0.04), (-0.38, 0.17), (-0.30, 0.07), (0.31, 0.05), (0.42, 0.15),
         (0.58, 0.08), (0.77, 0.03), (0.86, 0.16), (1.02, 0.09), (-1.05, 0.20), (0.66, 0.21), (-0.83, 0.25)]


def sample_strip(S, tex, t, kind):
    """Additive river overlays (ripples / moon streak) the way the Java strip maps them."""
    ta = np.asarray(tex, np.float32) / 255
    th, tw = ta.shape[:2]
    y0 = int(max(0, S.sy(RIVER[0][1])))
    ys, xs = np.mgrid[y0:S.H, 0:S.W].astype(np.float32)
    X = (xs - S.W / 2) / S.A
    Y = (ys - S.top) / S.A
    u, v, hw = river_uv(X, Y)
    inside = (np.abs(u) < 1.0) & (Y >= RIVER[0][1])
    if kind == "ripples":
        uu = np.clip((u * 0.5 + 0.5), 0, 0.999)
        vv = (v * RIPPLE_K + t * RIPPLE_SPEED) % 1.0
        alpha_k = 0.7
    else:
        uu = np.clip(0.5 + X / (2 * STREAK_HW), 0, 0.999)
        vv = (v * RIPPLE_K * 0.7 + t * 0.12) % 1.0
        alpha_k = 0.9 * (smoothstep(0.80, 0.90, Y))
    px = ta[(vv * th).astype(int).clip(0, th - 1), (uu * tw).astype(int)]
    add = px[..., :3] * (px[..., 3] * inside * alpha_k)[..., None]
    S.img[y0:] += add


def compose(W, H, t=0.0, lobby=False):
    L = {f[:-4]: Image.open(os.path.join(OUT, f)) for f in os.listdir(OUT) if f.endswith(".png")}
    S = Screen(W, H)
    S.art(L["sky"], "sky")
    sp = L["sparkle"]
    for i, (x, y) in enumerate(STARS):
        tw_ = 0.35 + 0.65 * max(0.0, math.sin(t * (0.7 + 0.13 * i) + i * 1.7)) ** 3
        sz = S.A * (0.012 + 0.006 * (i % 3))
        S.place(sp, S.sx(x) - sz, S.sy(y) - sz, S.sx(x) + sz, S.sy(y) + sz, mode="add", tint=(1, 0.95, 0.88), alpha=tw_)
    mx, my, mr = MOON
    hz = mr * 2.9
    S.place(L["glow"], S.sx(mx - hz), S.sy(my - hz), S.sx(mx + hz), S.sy(my + hz), mode="add", tint=(1, 0.82, 0.62), alpha=0.32)
    S.place(L["moon"], S.sx(mx - mr), S.sy(my - mr), S.sx(mx + mr), S.sy(my + mr))
    S.art(L["clouds_far"], "clouds_far", wrap_dx=t * 0.006 * S.A)
    S.art(L["spires_far"], "spires_far")
    S.art(L["clouds_near"], "clouds_near", wrap_dx=t * 0.014 * S.A)
    S.art(L["cathedral"], "cathedral")
    S.art(L["glass_rose"], "glass_rose", mode="add", alpha=0.5)
    S.art(L["glass_lancets"], "glass_lancets", mode="add", alpha=0.45)
    S.art(L["mist"], "mist", wrap_dx=t * 0.01 * S.A, alpha=0.9)
    S.art(L["ground"], "ground")
    cx_, ty, thw, by, bhw = CASCADE
    S.place(L["cascade"], S.sx(cx_ - bhw), S.sy(ty), S.sx(cx_ + bhw), S.sy(by), alpha=0.95)
    sample_strip(S, L["ripples"], t, "ripples")
    sample_strip(S, L["streak"], t, "streak")
    S.art(L["field_far"], "field_far")
    S.art(L["field_near"], "field_near")
    # two bats crossing the left sky, mid flap
    bt = L["bats"]
    fw = bt.width // 3
    for i, (x, y, f, s) in enumerate(((-0.62, 0.20, 0, 0.05), (-0.52, 0.26, 2, 0.038), (0.48, 0.12, 1, 0.03))):
        fr = bt.crop((f * fw, 0, (f + 1) * fw, bt.height))
        S.place(fr, S.sx(x - s), S.sy(y - s / 2), S.sx(x + s), S.sy(y + s / 2))
    fwpx = FRAME_W * S.A
    S.place(L["frame_left"], 0, S.H - S.A, fwpx, S.H)
    S.place(L["frame_right"], S.W - fwpx, S.H - S.A, S.W, S.H)
    pt = L["petal"]
    r = np.random.default_rng(5)
    for i in range(10):
        x, y = r.uniform(-1.0, 1.0), r.uniform(0.35, 0.95)
        s = 0.008 + 0.006 * r.random()
        S.place(pt.rotate(r.uniform(0, 360), expand=True), S.sx(x - s), S.sy(y - s), S.sx(x + s), S.sy(y + s))
    S.place(L["vignette"], 0, 0, S.W, S.H)
    g = np.asarray(L["grain"], np.float32) / 255
    reps = (int(math.ceil(S.H / g.shape[0])), int(math.ceil(S.W / g.shape[1])))
    gt = np.tile(g, (reps[0], reps[1], 1))[:S.H, :S.W]
    S.img = S.img * (1 - gt[..., 3:4]) + gt[..., :3] * gt[..., 3:4]
    if lobby:
        # the scrim behind the lobby panel and a mock panel, as the lobby screens draw them (GUI scale 2)
        gs = 2
        pw, ph = min(W - 40 * gs, 620 * gs), H - 80 * gs
        x0, y0 = (W - pw) // 2, 40 * gs
        a = np.zeros((H, W), np.float32)
        a[y0 - 24 * gs:y0 + ph + 30 * gs, x0 - 12 * gs:x0 + pw + 12 * gs] = 1
        a = blur(a, 8 * gs) * 0.62
        S.img *= (1 - a)[..., None]
        S.img[y0:y0 + ph, x0:x0 + pw] = S.img[y0:y0 + ph, x0:x0 + pw] * 0.35 + col((16, 18, 22)) * 0.65 * 0.9
    return S


def previews():
    compose(1920, 1080).save(os.path.join(DESIGN, "lobby_preview_16x9.png"))
    compose(2520, 1080).save(os.path.join(DESIGN, "lobby_preview_21x9.png"))
    compose(1920, 1080, lobby=True).save(os.path.join(DESIGN, "lobby_preview_16x9_lobby.png"))


def main():
    os.makedirs(OUT, exist_ok=True)
    if "--previews-only" not in sys.argv:
        sky(); moon(); all_clouds(); spires_far(); cathedral(); ground(); fields(); frames(); sprites()
    previews()
    total = 0
    for f in sorted(os.listdir(OUT)):
        sz = os.path.getsize(os.path.join(OUT, f))
        total += sz
        im = Image.open(os.path.join(OUT, f))
        print(f"{f:20s} {im.width:5d}x{im.height:<5d} {sz / 1024:8.1f} KiB")
    print(f"total {total / 1024 / 1024:.2f} MiB")


if __name__ == "__main__":
    main()
