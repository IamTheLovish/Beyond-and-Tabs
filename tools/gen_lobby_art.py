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
RIVER = [(0.000, 0.836, 0.050), (0.020, 0.850, 0.070), (0.010, 0.866, 0.095), (-0.070, 0.886, 0.130),
         (-0.140, 0.910, 0.175), (-0.100, 0.938, 0.225), (0.060, 0.966, 0.285), (0.200, 0.995, 0.350),
         (0.240, 1.030, 0.420)]
HORIZON = 0.70                              # perspective for the river's flow coordinate
CASCADE = (0.0, 0.736, 0.020, 0.840, 0.046)  # x, top y, top half width, bottom y, bottom half width

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
        self.depth = board.mask()            # 255 = set back (atmospheric perspective: greyer, bluer)
        self.dd = ImageDraw.Draw(self.depth)
        self.back = False
        self.lw = board.ss * 1.4    # a little over one output pixel: the etching must survive downscaling

    # primitives
    def poly(self, ps, tone, edge=150, w=1.0):
        p = self.b.pts(ps)
        self.ds.polygon(p, fill=255)
        self.dt.polygon(p, fill=int(tone))
        self.dl.polygon(p, fill=0)
        self.dgm.polygon(p, fill=0)
        self.dd.polygon(p, fill=255 if self.back else 0)
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

    def gargoyle(self, x, y, side, size, tone, edge=170):
        """A little beast leaning out from a buttress top: body, neck, a gaping head, a spout."""
        s = size
        body = [(x, y - s * 0.3), (x + side * s * 1.0, y - s * 0.45), (x + side * s * 1.5, y - s * 0.7),
                (x + side * s * 1.95, y - s * 0.55), (x + side * s * 1.9, y - s * 0.25), (x + side * s * 1.55, y - s * 0.2),
                (x + side * s * 1.2, y + s * 0.05), (x + side * s * 0.6, y + s * 0.2), (x, y + s * 0.3)]
        self.poly(body, tone, edge, 0.7)
        self.poly([(x + side * s * 1.55, y - s * 0.65), (x + side * s * 1.6, y - s * 0.95), (x + side * s * 1.75, y - s * 0.62)], tone, edge, 0.6)
        self.ln([(x + side * s * 0.5, y + s * 0.15), (x + side * s * 0.45, y + s * 0.55), (x + side * s * 0.6, y + s * 0.6)], edge * 0.8, 0.6)
        self.ln([(x + side * s * 1.9, y - s * 0.4), (x + side * s * 2.3, y - s * 0.35)], edge * 0.6, 0.6)

    def corbel_table(self, x0, x1, y, n, tone, edge=150):
        """A carved corbel table: a row of little arches on brackets under a cornice."""
        self.rect(x0, y - 0.0035, x1, y, tone + 8, edge, 0.8)
        w = (x1 - x0) / n
        for i in range(n):
            cx = x0 + w * (i + 0.5)
            self.ln(arch_pts(cx, y + w * 0.15, w * 0.42, 0.6, 8), edge * 0.85, 0.6)
            bx = x0 + w * i
            self.poly([(bx - w * 0.08, y), (bx + w * 0.08, y), (bx + w * 0.03, y + w * 0.5), (bx - w * 0.03, y + w * 0.5)],
                      tone + 10, edge, 0.5)

    def statue_niches(self, x0, x1, y0, y1, n, tone, edge=160):
        """A row of canopied niches, each with a robed figure on a corbel."""
        w = (x1 - x0) / n
        for i in range(n):
            cx = x0 + w * (i + 0.5)
            hw = w * 0.38
            ys = y0 + hw * 1.8
            self.dark([(cx - hw, y1)] + arch_pts(cx, ys, hw) + [(cx + hw, y1)], 4, edge, 0.7)
            # canopy gablet
            self.poly([(cx - hw * 1.15, ys + hw * 0.2), (cx, y0 - hw * 0.6), (cx + hw * 1.15, ys + hw * 0.2)], tone, edge * 0.9, 0.6)
            fh = (y1 - ys) * 0.82
            fb = y1 - (y1 - ys) * 0.06
            self.circle(cx, fb - fh + hw * 0.32, hw * 0.28, tone + 22, edge * 0.9, 0.5)
            self.poly([(cx - hw * 0.3, fb - fh + hw * 0.6), (cx + hw * 0.3, fb - fh + hw * 0.6), (cx + hw * 0.55, fb),
                       (cx - hw * 0.55, fb)], tone + 22, edge * 0.9, 0.5)
            self.ln([(cx - hw * 0.1, fb - fh * 0.62), (cx + hw * 0.25, fb - fh * 0.15)], edge * 0.6, 0.4)
            self.rect(cx - hw * 0.6, fb, cx + hw * 0.6, y1, tone + 12, edge * 0.8, 0.5)

    def broken_spire(self, cx, base, cut, hw, tip_full, tone, edge=150, rr=None):
        """A spire whose top has fallen: the taper stops at `cut` in a jagged break, a stump of the next stage above."""
        h = base - tip_full
        k = (cut - tip_full) / h
        jag = [(cx + hw * k * f, cut + (rr.uniform(-0.006, 0.004) if rr is not None else 0)) for f in (0.9, 0.5, 0.1, -0.3, -0.7)]
        self.poly([(cx - hw, base)] + [(cx - hw * k, cut + 0.002)] + jag[::-1] + [(cx + hw * k, cut)] + [(cx + hw, base)], tone, edge)
        for f in (-0.42, 0.42):
            self.ln([(cx + hw * f, base), (cx + hw * f * k, cut)], edge * 0.55, 0.7)
        n = max(4, int((base - cut) / 0.012))
        self.crockets((cx - hw, base), (cx - hw * k, cut), n, hw * 0.28, tone, edge * 0.85, -1)
        self.crockets((cx + hw, base), (cx + hw * k, cut), n, hw * 0.28, tone, edge * 0.85, 1)
        # the exposed iron rod and a fallen-away edge
        self.ln([(cx, cut), (cx + hw * 0.1, cut - 0.02), (cx + hw * 0.25, cut - 0.024)], edge, 0.6)

    def tympanum(self, cx, lintel, apex, hw, tone, edge=170):
        """The sculpted tympanum over the door: a mandorla with an enthroned figure, angels each side, a carved lintel."""
        self.rect(cx - hw, lintel - 0.006, cx + hw, lintel, tone + 14, edge, 0.8)
        for i in range(7):                      # lintel figures
            x = cx - hw + hw * 2 * (i + 0.5) / 7
            self.circle(x, lintel - 0.0045, 0.0011, tone + 26, edge, 0.4)
            self.ln([(x, lintel - 0.0034), (x, lintel - 0.0005)], edge * 0.8, 0.5)
        my = (lintel - 0.006 + apex) / 2 + 0.002
        mh = (lintel - 0.006 - apex) * 0.42
        mw = hw * 0.36
        ves = [(cx + mw * math.sin(a), my - mh * math.cos(a)) for a in np.linspace(0, 2 * math.pi, 28)]
        self.poly(ves, tone + 10, edge, 0.7)
        self.circle(cx, my - mh * 0.45, mw * 0.24, tone + 28, edge, 0.5)
        self.poly([(cx - mw * 0.3, my - mh * 0.25), (cx + mw * 0.3, my - mh * 0.25), (cx + mw * 0.55, my + mh * 0.7),
                   (cx - mw * 0.55, my + mh * 0.7)], tone + 28, edge, 0.5)
        for k in range(-3, 4):
            self.ln([(cx + k * mw * 0.12, my - mh * 0.15), (cx + k * mw * 0.18, my + mh * 0.68)], edge * 0.55, 0.35)
        for side in (-1, 1):
            ax = cx + side * hw * 0.62
            self.circle(ax, my - mh * 0.1, mw * 0.17, tone + 22, edge * 0.9, 0.45)
            self.poly([(ax - mw * 0.18, my), (ax + mw * 0.18, my), (ax + mw * 0.25, my + mh * 0.8), (ax - mw * 0.25, my + mh * 0.8)],
                      tone + 22, edge * 0.9, 0.45)
            self.poly([(ax, my - mh * 0.05), (ax - side * mw * 0.55, my - mh * 0.6), (ax - side * mw * 0.15, my + mh * 0.3)],
                      tone + 16, edge * 0.8, 0.4)

    def trefoil_lead(self, cx, cy, r, w=0.6):
        for ang in (-90, 30, 150):
            a = math.radians(ang)
            pts = [(cx + r * 1.0 * math.cos(a) + r * 0.9 * math.cos(t), cy + r * 1.0 * math.sin(a) + r * 0.9 * math.sin(t))
                   for t in np.linspace(0, 2 * math.pi, 14)]
            self.lead(pts, w, 24)

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
        if hw > 0.006 and base - top > 0.2 and not self.back:
            side = 1 if cx >= 0 else -1
            self.gargoyle(cx + side * hw, top + (base - top) * 0.12, side, hw * 1.1, tone, edge)
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


def cathedral(size=1536):
    b = Board(RECTS["cathedral"], size, size, ss=2)
    g = Gothic(b)
    rr = np.random.default_rng(601)
    E = 175      # edge brightness of the front
    BT = 20      # back tone

    # --- the far back: a forest of needles behind the facade (darker, fewer lines)
    for cx, top, tip, hw in ((-0.058, 0.25, 0.10, 0.008), (0.058, 0.25, 0.10, 0.008), (-0.20, 0.30, 0.165, 0.009),
                             (0.20, 0.30, 0.165, 0.009), (-0.105, 0.24, 0.125, 0.007), (0.105, 0.24, 0.125, 0.007),
                             (-0.165, 0.27, 0.145, 0.007), (0.165, 0.27, 0.145, 0.007), (-0.25, 0.40, 0.27, 0.008),
                             (0.25, 0.40, 0.27, 0.008), (-0.30, 0.50, 0.38, 0.008), (0.30, 0.50, 0.38, 0.008),
                             (-0.035, 0.26, 0.15, 0.006), (0.035, 0.26, 0.15, 0.006)):
        g.back = True
        g.pinnacle(cx, 0.62, top, tip, hw, BT, 105)
    g.back = False

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
    g.corbel_table(-0.088, 0.088, 0.276, 16, 36, E)
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
    g.tympanum(0.0, 0.716, 0.682, 0.024, 22, 185)
    g.glass_poly([(-0.021, 0.7165), (0.021, 0.7165), (0.021, 0.745), (-0.021, 0.745)], (140, 18, 20))   # the lit nave
    g.rect(-0.0028, 0.716, 0.0028, 0.745, 30, 170, 0.7)                                              # trumeau
    g.circle(0.0, 0.722, 0.0022, 44, 170, 0.5)
    g.rect(-0.0018, 0.724, 0.0018, 0.738, 44, 160, 0.5)

    # --- the two west towers
    for s in (-1, 1):
        cx = s * 0.132
        hw = 0.040
        if s > 0:
            g.broken_spire(cx, 0.21, 0.085, 0.031, 0.036, 34, E, rr)     # the right spire lost its top long ago
        else:
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
        g.statue_niches(cx - hw + 0.003, cx + hw - 0.003, 0.655, 0.735, 4, 34, 165)
        for y in (0.299, 0.409, 0.529):
            g.corbel_table(cx - hw, cx + hw, y + 0.0125, 8, 40, 150)
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
    ivy = ivy_overlay(b, rr, [(-0.27, 0.745, 0.20, 0.03), (-0.20, 0.745, 0.13, 0.02), (-0.15, 0.745, 0.09, 0.02),
                              (0.28, 0.745, 0.06, 0.01)])
    return finish_stone(b, g, "cathedral", size, size, ivy=ivy, rr=rr)


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
                dy_ = max(0.0, ys - y)
                lim = (math.sqrt(max(4 * hw * hw - dy_ * dy_, 0)) - hw) if dy_ > 0 else hw
                if abs(x - cx) > lim - 0.0025:
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
        g.trefoil_lead(cx, ys - hw * 0.95, hw * 0.28, 0.5)
        g.lead(body + [body[0]], 1.2, 24)


def engrave(X, Y, b, Lv, period=4.0):
    """Engraving tone: hatch lines that appear in layers as the light value Lv rises (verticals, then one diagonal,
    then the other). Returns line coverage 0..1. `period` is in output pixels."""
    px = b.sx / b.ss
    xs, ys = X * px, Y * px
    def lines(ang, ph):
        c, s_ = math.cos(ang), math.sin(ang)
        d = (xs * c + ys * s_) / period
        wob = 0.0
        return np.clip((np.sin((d + ph) * 2 * math.pi) - 0.55) / 0.45, 0, 1)
    v = lines(0.0, 0.0) * smoothstep(0.06, 0.18, Lv)
    d1 = lines(math.radians(62), 0.3) * smoothstep(0.24, 0.42, Lv)
    d2 = lines(math.radians(118), 0.6) * smoothstep(0.45, 0.65, Lv)
    return np.clip(v + d1 + d2, 0, 1)


def finish_stone(b, g, name, w, h, ivy=None, rr=None):
    """Turn the Gothic layers into an etched, moonlit stone image with one light logic:
    the moon BEHIND = cool silver rim on every edge facing it; the red sky = crimson fill on side-facing edges;
    the glass = amber spill on the stone round it; occlusion in every recess; soft drop shadows under ledges."""
    X, Y = b.grid()
    ss = b.ss
    sil = arr(g.sil)
    tone = arr(g.tone)
    line = arr(g.line)
    gm = arr(g.gmask)
    dep = arr(g.depth)
    glass = np.asarray(g.glass, np.float32) / 255
    tn = np.clip(tone * 2.0, 0, 1)
    mx, my, mr = MOON

    # --- normals of the silhouette and of the element boundaries (outward = down the blurred coverage)
    def normals(field, sigma):
        f = blur(field, sigma)
        gy, gx = np.gradient(f)
        mag = np.hypot(gx, gy) + 1e-6
        return -gx / mag, -gy / mag, mag
    nx, ny, emag = normals(sil, ss * 1.2)
    edge_sil = np.clip(emag * ss * 4.0, 0, 1)
    tx_, ty_, tmag = normals(tn, ss * 1.0)
    edge_in = np.clip(tmag * ss * 6.0, 0, 1) * smoothstep(0.08, 0.25, tn)
    # direction to the moon
    dmx, dmy = mx - X, my - Y
    dl = np.hypot(dmx, dmy) + 1e-6
    dmx, dmy = dmx / dl, dmy / dl
    near_moon = 1 - smoothstep(mr * 0.9, mr * 2.4, dl)
    rim = np.clip(nx * dmx + ny * dmy, 0, 1) ** 0.7 * edge_sil * (0.35 + 0.65 * near_moon)
    rim_in = np.clip(tx_ * dmx + ty_ * dmy, 0, 1) * edge_in * (0.25 + 0.75 * near_moon) * 0.8
    side = np.sign(X)
    fill = (np.clip(nx * side, 0, 1) * edge_sil + np.clip(tx_ * side, 0, 1) * edge_in * 0.6) \
        * smoothstep(0.03, 0.30, np.abs(X)) * smoothstep(0.15, 0.6, Y)
    # --- glass spill (amber light on the mouldings and sills round each window)
    glum = (glass.max(-1) * gm)
    spill = blur(glum, ss * 6) * 1.6 + blur(glum, ss * 22) * 1.2
    spill = np.clip(spill, 0, 1) * (1 - gm)
    # --- occlusion: dark recesses darken their neighbours; ledges drop soft shadows downward
    recess = (1 - smoothstep(0.05, 0.16, tn)) * sil * (1 - gm)
    ao = blur(recess, ss * 3.0)
    shift = max(1, int(b.sy * 0.004))
    above = np.roll(tn, shift, axis=0)
    above[:shift] = 0
    drop = blur(np.clip(above - tn, 0, 1), ss * 2.5)
    occl = np.clip(1 - ao * 0.55 - drop * 0.9, 0.15, 1)

    # --- the light value that drives the engraving
    base_light = 0.15 + 0.22 * tn
    # broad modelling: the moon's glow wraps over the upper masses, a cooler darkness pools low; a slow mottling
    big = fbm(X * 0 + (X - b.x0) / (b.x1 - b.x0), (Y - b.y0) / (b.y1 - b.y0), 4, 57, False, 3)
    model = 0.10 * (1 - smoothstep(0.15, 0.70, Y)) + 0.10 * (big - 0.5) + 0.06 * near_moon
    Lv = (base_light + model + rim * 0.95 + rim_in * 0.7 + fill * 0.55 + spill * 1.3) * occl
    Lv = np.clip(Lv, 0, 1)
    # --- textures in the stone: ashlar courses, weathering, cracks, damp moss
    U01 = (X - b.x0) / (b.x1 - b.x0)
    V01 = (Y - b.y0) / (b.y1 - b.y0)
    mott = fbm(U01, V01, 16, 53, False, 5)
    drip = fbm(U01, V01 * 0.06, 90, 54, False, 3)
    course_h = 0.0055
    row = np.floor(Y / course_h)
    joint_h = 1 - smoothstep(0.0, 0.16, np.abs((Y / course_h) - np.round(Y / course_h)))
    off = (row % 2) * 0.5
    bx_ = X / (course_h * 2.2) + off
    joint_v = 1 - smoothstep(0.0, 0.08, np.abs(bx_ - np.round(bx_)))
    joints = np.clip(joint_h + joint_v, 0, 1) * sil * (1 - gm)
    moss = smoothstep(0.58, 0.72, fbm(U01, V01, 26, 55, False, 4)) * smoothstep(0.50, 0.74, Y)
    # stone body: near-black, cool; mid greys only where lit
    stone = lerp(col((6, 6, 9)), col((30, 29, 36)), tn * 0.8 + mott * 0.25)
    stone = lerp(stone, col((18, 22, 14)), moss * 0.45)
    stone *= (0.75 + 0.5 * drip * mott)[..., None]
    # the lit colour mixes the three lights
    wsum = rim + rim_in + fill + spill + 1e-4
    light_rgb = (col((206, 212, 228))[None, None] * (rim + rim_in)[..., None]
                 + col((196, 58, 52))[None, None] * fill[..., None]
                 + col((246, 170, 86))[None, None] * spill[..., None]) / wsum[..., None]
    light_rgb = lerp(col((96, 96, 112)), light_rgb, np.clip(wsum * 2.5, 0, 1))
    eng = engrave(X, Y, b, Lv, 3.4)
    rgb = lerp(stone, light_rgb * 0.8, eng * np.clip(0.16 + Lv * 0.9, 0, 1))
    rgb = rgb + light_rgb * (Lv ** 2 * 0.35)[..., None]
    # block joints: dark on the lit stone, faintly pale in shadow
    rgb = lerp(rgb, rgb * 0.45, joints * smoothstep(0.1, 0.4, Lv) * 0.8)
    rgb = lerp(rgb, col((40, 40, 48)), joints * (1 - smoothstep(0.1, 0.4, Lv)) * 0.25)
    # carved edges (mouldings): bright where lit, dim in shadow - value structure stays dark
    lc = lerp(col((88, 90, 104)), light_rgb * 1.15, np.clip(Lv * 1.6, 0, 1))
    rgb = lerp(rgb, lc, line * (0.5 + 0.5 * np.clip(Lv * 1.8 + 0.15, 0, 1)) * (0.75 + 0.25 * mott))
    # cracks
    if rr is not None:
        ck = Image.new("L", (b.W, b.H), 0)
        dc = ImageDraw.Draw(ck)
        for i in range(260):
            x, y = rr.uniform(b.x0, b.x1), rr.uniform(0.2, 0.78)
            pts = [b.P(x, y)]
            ang = rr.uniform(0.3, 2.8)
            for k in range(rr.integers(3, 8)):
                ang += rr.uniform(-0.7, 0.7)
                x += math.cos(ang) * 0.003
                y += abs(math.sin(ang)) * 0.004
                pts.append(b.P(x, y))
            dc.line(pts, fill=255, width=max(1, ss))
        rgb *= (1 - arr(ck) * 0.6 * sil)[..., None]
    rgb *= occl[..., None] ** 0.6
    # atmospheric perspective: set-back masses greyer and bluer
    rgb = lerp(rgb, rgb * 0.6 + col((34, 38, 56)) * 0.5, dep * 0.8)
    # glass: glowing panes, darker leading, mottled
    nz = fbm(U01, V01, 60, 56, False, 3)
    gl = glass * (0.66 + 0.45 * nz[..., None])
    rgb = lerp(rgb, gl, gm)
    # the silhouette's outer rim against the moon (crisp silver) and the sides (crimson)
    sb = blur(sil, ss * 1.0)
    outer = np.clip((sil - sb) * 3, 0, 1)
    rgb = lerp(rgb, col((236, 236, 246)), outer * near_moon * 0.85)
    rgb = lerp(rgb, col((200, 64, 58)), outer * (1 - near_moon) * smoothstep(0.1, 0.35, np.abs(X)) * 0.5)
    # deeper at the foot, where the mist and the ground meet it
    rgb *= (1 - 0.3 * smoothstep(0.64, 0.75, Y))[..., None]
    # the rim light alone, as an additive layer the game swells and fades with the cathedral's breath
    rim_rgb = light_rgb * np.clip(rim * 0.9 + rim_in * 0.5, 0, 1)[..., None]
    rim_rgb = rim_rgb + col((236, 236, 246)) * (outer * near_moon * 0.8)[..., None]
    ra = np.clip(rim_rgb.max(-1), 0, 1)
    save_rgba(rim_rgb / np.maximum(ra, 1e-4)[..., None], ra, "cathedral_rim", (w, h))
    a = sil
    if ivy is not None:
        iv = np.asarray(ivy, np.float32) / 255
        iv[..., 3] *= np.clip(blur(sil, ss * 2) * 1.5, 0, 1) * (1 - gm)
        rgb = lerp(rgb, iv[..., :3], iv[..., 3])
        a = np.clip(a + iv[..., 3], 0, 1)
    save_rgba(rgb, a, name, (w, h))
    # the two glow layers for the additive pulse: the glass alone, softened
    for m, nm in ((arr(g.grose), "glass_rose"), (arr(g.glan), "glass_lancets")):
        if not m.any():
            continue
        glow_rgb = glass * m[..., None]
        soft = np.stack([blur(glow_rgb[..., i], ss * 3) for i in range(3)], -1)
        wide = np.stack([blur(glow_rgb[..., i], ss * 14) for i in range(3)], -1)
        out = glow_rgb * 0.35 + soft * 0.6 + wide * 1.2
        al = np.clip(out.max(-1), 0, 1)
        save_rgba(out / np.maximum(al, 1e-4)[..., None], al, nm, (512, 512))


def ivy_overlay(b, rr, paths):
    """Ivy climbing the stone: leaf clumps along wandering stems (dark green, a pale vein-lit edge)."""
    img = Image.new("RGBA", (b.W, b.H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for (x, y, up, spread) in paths:
        for stem in range(6):
            sx_, sy_ = x + rr.uniform(-spread, spread) * 0.3, y
            ang = -math.pi / 2 + rr.uniform(-0.5, 0.5)
            pts = [(sx_, sy_)]
            for k in range(int(up / 0.006)):
                ang += rr.uniform(-0.45, 0.45)
                ang = max(-math.pi + 0.4, min(-0.4, ang))
                sx_ += math.cos(ang) * 0.006
                sy_ += math.sin(ang) * 0.006
                pts.append((sx_, sy_))
            d.line(b.pts(pts), fill=(24, 30, 20, 255), width=b.ss)
            for (px, py) in pts:
                for j in range(rr.integers(1, 4)):
                    lx, ly = px + rr.normal(0, 0.003), py + rr.normal(0, 0.003)
                    r = rr.uniform(0.0016, 0.0032)
                    tone = rr.uniform(0.6, 1.2)
                    leaf = [(lx, ly - r), (lx + r * 0.9, ly - r * 0.2), (lx + r * 0.5, ly + r * 0.7), (lx, ly + r * 0.4),
                            (lx - r * 0.5, ly + r * 0.7), (lx - r * 0.9, ly - r * 0.2)]
                    d.polygon(b.pts(leaf), fill=(int(22 * tone), int(34 * tone), int(22 * tone), 255),
                              outline=(int(70 * tone), int(84 * tone), int(60 * tone), 255))
    return img


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


RIVER_ROCKS = ((0.875, 0.35, 0.010), (0.905, -0.45, 0.013), (0.93, 0.55, 0.016), (0.955, -0.30, 0.018),
               (0.985, 0.70, 0.024), (1.0, -0.65, 0.026))   # (y, across -1..1, radius)


def river_rocks():
    """(x, y, r) of the rocks that break the river's surface (LobbyBackdrop.RINGS mirrors these for the ripple rings)."""
    out = []
    for yy, uu, rad in RIVER_ROCKS:
        c = river_at(yy)
        out.append((c[0] + uu * c[1], yy, rad))
    return out


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
        ds.polygon(b.pts([(-hwid, y0), (hwid, y0), (hwid + 0.004, y0 + 0.0105), (-hwid - 0.004, y0 + 0.0105)]), fill=90 - i * 10)
        dl.line(b.pts([(-hwid, y0), (hwid, y0)]), fill=200, width=3)
        for k in range(-6, 7):
            x = k * hwid / 6.5
            dl.line(b.pts([(x, y0 + 0.001), (x * 1.03, y0 + 0.0095)]), fill=90, width=2)
    stv, slv = arr(st), arr(sl)
    rgb = lerp(rgb, lerp(col((24, 22, 28)), col((70, 66, 76)), stv)[...], (stv > 0) * 1.0)
    rgb = lerp(rgb, col((120, 106, 110)), slv * 0.5)

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
            if rr.random() < 0.35:
                off = hwr * rr.uniform(0.92, 1.25)
                places.append((cx + side * off, y, 0.006 + 0.03 * persp * rr.uniform(0.5, 1.1)))
        if rr.random() < 0.25:
            places.append((cx + rr.uniform(-1.4, 1.4) * hwr * 2.4, y, 0.004 + 0.02 * persp * rr.random()))
    # stepped rock ledges below the stair: the fall breaks over them on its way to the river
    for row, (yy, span, rad) in enumerate(((0.806, 0.11, 0.010), (0.822, 0.13, 0.012), (0.838, 0.15, 0.013))):
        for k in range(-7, 8):
            x = k * span / 7 + rr.uniform(-0.004, 0.004)
            if abs(x) < 0.02 + row * 0.008:
                continue
            places.append((x, yy + rr.uniform(-0.003, 0.003), rad * rr.uniform(0.8, 1.2)))
    # wet rocks breaking the river's surface
    in_river = []
    in_river = river_rocks()
    places += in_river
    for x, y, r in sorted(places, key=lambda p: p[1]):
        ps = b.pts(rock_poly(x, y, r, rr))
        tone = int(rr.uniform(24, 60))
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
    # wet gloss: sharp specular glints along the lit top edges
    gl_ = fbm(U01, V01, 300, 79, False, 2)
    rock_rgb = lerp(rock_rgb, col((255, 236, 236)), rline * smoothstep(0.62, 0.72, gl_) * 0.9)
    # river-facing faces pick up the crimson
    rock_rgb = lerp(rock_rgb, col((150, 24, 30)), np.clip(1.6 - np.abs(u), 0, 1)[..., None][..., 0] * 0.35)

    # the river
    edge_n = fbm(U01, V01, 40, 74, False, 3)
    au = np.abs(u)
    water = 1 - smoothstep(0.92 + edge_n * 0.12, 1.02 + edge_n * 0.12, au)
    water *= smoothstep(RIVER[0][1] - 0.004, RIVER[0][1] + 0.004, Y)
    uu = np.clip(u * 0.5 + 0.5, 0, 1)
    sw = fbm(uu * 0.5, v * 0.5, 5, 75, False, 5)
    wrgb = lerp(col((178, 22, 32)), col((70, 4, 10)), np.clip(au ** 2 * 0.6 + (0.55 - sw) * 1.4, 0, 1))
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
    wrgb *= (1 - smoothstep(0.7, 1.0, au) * 0.35)[..., None]

    # the skull: half sunk, a little off the line of the river, catching the moon on its crown
    sk = Image.new("L", (b.W, b.H), 0)
    skl = Image.new("L", (b.W, b.H), 0)
    dsk, dskl = ImageDraw.Draw(sk), ImageDraw.Draw(skl)
    sx, sy, srad = 0.02, 0.962, 0.019
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

    # foam: round the rocks in the river (downstream streaks) and in a churned ring at the foot of the fall
    foam = np.zeros_like(X)
    for (fx, fy, fr) in in_river:
        dx_, dy_ = (X - fx) / (fr * 2.2), (Y - fy - fr * 0.25) / (fr * 0.9)
        dd = np.hypot(dx_, dy_)
        foam += np.exp(-((dd - 1.0) / 0.25) ** 2) * smoothstep(-0.3, 0.6, dy_)
    cx_, ty, thw, by, bhw = CASCADE
    dd = np.hypot((X - cx_) / (bhw * 2.2), (Y - by - 0.004) / 0.012)
    speck = fbm(U01, V01, 220, 78, False, 2)
    foam += np.exp(-dd ** 2 * 1.5) * smoothstep(0.45, 0.65, speck) * 1.5
    foam = np.clip(foam, 0, 1) * water
    # the glass's amber light spilling down the steps and onto the head of the river
    spillw = np.exp(-((X / 0.11) ** 2 + ((Y - 0.76) / 0.06) ** 2))
    rgb = rgb + col((200, 120, 50)) * (spillw * 0.35 * land)[..., None]
    wrgb = wrgb + col((230, 120, 60)) * (spillw * 0.4)[..., None]
    # contact shadows: under the cathedral's foot and under every rock
    rgb *= (1 - 0.6 * smoothstep(0.738, 0.744, Y) * (1 - smoothstep(0.744, 0.760, Y)) * (np.abs(X) < 0.30))[..., None]
    rshadow = blur(np.roll(rcov, int(b.sy * 0.006), axis=0), b.ss * 4)
    out = lerp(rgb, wrgb, water)
    out *= (1 - rshadow * 0.65)[..., None]
    out = lerp(out, col((220, 70, 70)), rings * water * 0.4)
    out = lerp(out, col((255, 196, 186)), foam * 0.55)
    out = lerp(out, sk_rgb, sk_cov)
    out = lerp(out, rock_rgb, rcov)
    # the cascade's channel down the steps: dark wet red (the animated fall is drawn over it)
    cx_, ty, thw, by, bhw = CASCADE
    k = np.clip((Y - ty) / (by - ty), 0, 1)
    ch = (np.abs(X - cx_) < thw + (bhw - thw) * k) & (Y > ty - 0.002) & (Y < by + 0.002)
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
    if name == "field_near":
        for side in (-1, 1):
            for k in range(7):
                briar(d, b, side * rr.uniform(0.42, 0.75), rr.uniform(0.985, 1.0), rr.uniform(0.10, 0.17), rr, -side)
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
    """Red blooms on dark stems: each petal lighter at its rim and darker toward a near-black heart; near the
    viewer (big s) a few large single blooms, far away a dense cluster of small ones."""
    for i in range(rr.integers(2, 5)):
        lx = x + rr.uniform(-1, 1) * s
        d.polygon(b.pts([(lx, y), (lx - s * 0.25, y - s * 0.5), (lx + s * 0.1, y - s * 0.9), (lx + s * 0.2, y - s * 0.4)]),
                  fill=c8((18, 24, 18), shade, 0.3, rr))
    big = s > 0.04
    n = rr.integers(2, 4) if big else rr.integers(5, 14)
    for i in range(n):
        fx = x + rr.normal(0, 0.55) * s
        fy = y - s * 0.5 - abs(rr.normal(0, 0.45)) * s
        r = s * (rr.uniform(0.22, 0.34) if big else rr.uniform(0.16, 0.30))
        base = (rr.uniform(150, 205), rr.uniform(12, 30), rr.uniform(20, 34))
        d.line(b.pts([(fx, fy), (fx + rr.uniform(-0.1, 0.1) * s, y)]), fill=c8((26, 34, 24), shade), width=2 if not big else 4)
        npet = 5 if not big else 7
        for k in range(npet):
            a = 2 * math.pi * k / npet + rr.uniform(0, 1)
            px, py = fx + math.cos(a) * r * 0.55, fy + math.sin(a) * r * 0.45
            rim = (min(255, base[0] * 1.3), base[1] * 1.6 + 10, base[2] * 1.4)
            d.ellipse(b.pts([(px - r * 0.55, py - r * 0.45), (px + r * 0.55, py + r * 0.45)]), fill=c8(rim, shade * 0.95, 0.12, rr))
            ix, iy = fx + math.cos(a) * r * 0.42, fy + math.sin(a) * r * 0.34
            d.ellipse(b.pts([(ix - r * 0.40, iy - r * 0.32), (ix + r * 0.40, iy + r * 0.32)]), fill=c8(base, shade * 0.75, 0.1, rr))
            if big:
                d.line(b.pts([(fx, fy), (fx + math.cos(a) * r * 0.85, fy + math.sin(a) * r * 0.7)]), fill=c8((90, 8, 14), shade), width=2)
        d.ellipse(b.pts([(fx - r * 0.5, fy - r * 0.4), (fx + r * 0.5, fy + r * 0.25)]), fill=c8((base[0] * 0.7, base[1] * 0.6, base[2] * 0.7), shade, 0.1, rr))
        d.ellipse(b.pts([(fx - r * 0.2, fy - r * 0.15), (fx + r * 0.2, fy + r * 0.15)]), fill=c8((28, 4, 6), 1.0))
        if big:
            for k in range(8):
                a = rr.uniform(0, 2 * math.pi)
                d.ellipse(b.pts([(fx + math.cos(a) * r * 0.22 - r * 0.03, fy + math.sin(a) * r * 0.18 - r * 0.03),
                                 (fx + math.cos(a) * r * 0.22 + r * 0.03, fy + math.sin(a) * r * 0.18 + r * 0.03)]),
                          fill=c8((210, 160, 80), shade))


def briar(d, b, x, y, s, rr, side):
    """A thorny briar: arching dark canes with hooked thorns, a few leaves and a closed bud."""
    for k in range(rr.integers(3, 6)):
        L = s * rr.uniform(0.7, 1.3)
        lean = side * rr.uniform(0.1, 0.6)
        pts = []
        for j in range(24):
            t = j / 23
            pts.append((x + lean * L * t * t + rr.uniform(-0.0005, 0.0005), y - L * math.sin(t * 1.4) * 0.9))
        d.line(b.pts(pts), fill=(16, 14, 14, 255), width=5)
        d.line(b.pts(pts), fill=(70, 50, 46, 255), width=1)
        for j in range(2, 23, 3):
            px, py = pts[j]
            sd = 1 if j % 2 else -1
            d.polygon(b.pts([(px, py - 0.002), (px + sd * 0.006, py - 0.004), (px, py + 0.002)]), fill=(20, 16, 16, 255))
        for j in range(4, 22, 6):
            px, py = pts[j]
            sd = rr.choice([-1, 1])
            r = 0.007
            d.polygon(b.pts([(px, py), (px + sd * r, py - r * 0.5), (px + sd * r * 1.6, py + r * 0.1), (px + sd * r * 0.6, py + r * 0.5)]),
                      fill=(22, 30, 20, 255), outline=(66, 78, 54, 255))
        tx, ty = pts[-1]
        d.ellipse(b.pts([(tx - 0.004, ty - 0.008), (tx + 0.004, ty + 0.002)]), fill=(120, 12, 22, 255), outline=(40, 50, 30, 255))


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
    flower_layer("field_far", 2560, 341, 81, 0.69, 0.93, 0.004, 0.016, 5600)
    flower_layer("field_near", 2560, 242, 82, 0.84, 1.05, 0.014, 0.085, 1300)


# ---------------------------------------------------------------- framing: ruined walls, gnarled trees, vines

def shade_etched(b, sil, tone, line, ldx, ldy, rim_col, fill_col, seed, base=0.14):
    """Etched shading for the framing ruins and trees: rim light from direction (ldx, ldy) (toward the light),
    a softer fill from above, occlusion under ledges, engraving lines that thicken with the light."""
    X, Y = b.grid()
    ss = b.ss
    f = blur(sil, ss * 1.3)
    gy, gx = np.gradient(f)
    mag = np.hypot(gx, gy) + 1e-6
    nx, ny = -gx / mag, -gy / mag
    edge = np.clip(mag * ss * 4, 0, 1)
    tn = np.clip(tone * 1.6, 0, 1)
    t2 = blur(tn, ss)
    ty, tx = np.gradient(t2)
    tm = np.hypot(tx, ty) + 1e-6
    edge_in = np.clip(tm * ss * 6, 0, 1)
    rim = np.clip(nx * ldx + ny * ldy, 0, 1) * edge + np.clip((-tx / tm) * ldx + (-ty / tm) * ldy, 0, 1) * edge_in * 0.6
    top = np.clip(-ny, 0, 1) * edge * 0.5
    shift = max(1, int(b.sy * 0.006))
    above = np.roll(tn, shift, axis=0)
    above[:shift] = 0
    drop = blur(np.clip(above - tn, 0, 1), ss * 3)
    U01, V01 = X / max(b.x1, 1e-6), Y
    mott = fbm(U01, V01, 18, seed, False, 5)
    Lv = np.clip((base + 0.16 * tn + 0.12 * (mott - 0.5) + rim * 0.9 + top * 0.4) * (1 - drop * 0.8), 0, 1)
    stone = lerp(col((5, 5, 7)), col((34, 32, 38)), tn * 0.7 + mott * 0.3)
    lc = lerp(col((90, 90, 104)), lerp(col(fill_col), col(rim_col), np.clip(rim * 1.5, 0, 1)), np.clip(Lv * 2, 0, 1))
    eng = engrave(X, Y, b, Lv, 3.6)
    rgb = lerp(stone, lc * 0.85, eng * np.clip(0.12 + Lv, 0, 1))
    rgb = rgb + lc * (rim ** 2 * 0.3)[..., None]
    rgb = lerp(rgb, lc * 1.1, line * (0.45 + 0.55 * np.clip(Lv * 2, 0, 1)))
    return rgb


def frame_side(name, mirror, seed):
    w, h = 726, 1100
    b = Board((0.0, 0.0, FRAME_W, 1.0), w, h, ss=2)
    rr = np.random.default_rng(seed)

    # ---------------- the tree (behind the ruins)
    tc, tt, tl = b.mask(), b.mask(), b.mask()
    dtc, dtt, dtl = ImageDraw.Draw(tc), ImageDraw.Draw(tt), ImageDraw.Draw(tl)
    segs_all = []

    def limb(x0, y0, ang, L, wdt, depth):
        n = 8
        pl, pr, cen = [], [], []
        x, y, a = x0, y0, ang
        kids = []
        for k in range(n + 1):
            ww = wdt * (1 - k / n * 0.8)
            nx_, ny_ = math.cos(a), math.sin(a)
            pl.append((x - ny_ * ww * 0 - nx_ * ww, y - ny_ * ww))
            pr.append((x + nx_ * ww, y + ny_ * ww))
            cen.append((x, y, ww))
            if k < n:
                a += rr.uniform(-0.32, 0.32)
                x += math.sin(a) * L / n
                y -= math.cos(a) * L / n
            if 1 < k < n - 1 and depth > 0 and rr.random() < 0.5:
                kids.append((x, y, a + rr.choice([-1, 1]) * rr.uniform(0.45, 1.0), L * rr.uniform(0.45, 0.7), ww * 0.75, depth - 1))
        poly = b.pts(pl + pr[::-1])
        dtc.polygon(poly, fill=255)
        dtt.polygon(poly, fill=int(rr.uniform(45, 70)))
        dtl.polygon(poly, fill=0)
        dtl.line(poly + [poly[0]], fill=170, width=2)
        # bark fissures: several wavering lines along the limb
        for f_ in np.linspace(-0.7, 0.7, 5 if wdt > 0.008 else 2):
            pts = []
            for (cx_, cy_, ww), (l_, r_) in zip(cen, zip(pl, pr)):
                jit = rr.uniform(-0.12, 0.12)
                pts.append((cx_ + (r_[0] - cx_) * (f_ + jit), cy_ + (r_[1] - cy_) * (f_ + jit)))
            dtl.line(b.pts(pts), fill=int(rr.uniform(70, 130)), width=2)
        segs_all.append(cen)
        for kd in kids:
            limb(*kd)

    limb(0.30, 1.03, -0.15, 0.36, 0.036, 3)
    limb(0.30, 0.78, 0.55, 0.30, 0.017, 3)
    limb(0.28, 0.86, -0.75, 0.22, 0.015, 2)
    limb(0.05, 0.20, 0.95, 0.32, 0.013, 3)
    # twigs
    for cen in segs_all:
        x, y, ww = cen[-1]
        for k in range(rr.integers(2, 6)):
            a = rr.uniform(-1.3, 1.5)
            L = rr.uniform(0.015, 0.05)
            dtl.line(b.pts([(x, y), (x + math.sin(a) * L * 0.5 + rr.uniform(-0.004, 0.004), y - math.cos(a) * L * 0.5),
                            (x + math.sin(a) * L, y - math.cos(a) * L)]), fill=110, width=2)
            dtc.line(b.pts([(x, y), (x + math.sin(a) * L, y - math.cos(a) * L)]), fill=255, width=2)
    tree_rgb = shade_etched(b, arr(tc), arr(tt), arr(tl), 0.8, -0.6, (214, 120, 96), (150, 44, 40), seed + 1, 0.10)

    # foliage: clumps placed ALONG the outer limbs, leaves oriented, dark under-shade, bright copper on top
    fol = Image.new("RGBA", (b.W, b.H), (0, 0, 0, 0))
    df = ImageDraw.Draw(fol)
    for cen in segs_all:
        if cen[0][2] > 0.02:
            continue                         # the trunk stays bare
        for (x, y, ww) in cen[2::2]:
            if rr.random() < 0.45:
                continue
            ccx, ccy = x + rr.normal(0, 0.008), y - rr.uniform(0.0, 0.012)
            R = rr.uniform(0.010, 0.022)
            for k in range(int(R * 2400)):
                lx, ly = ccx + rr.normal(0, R * 0.5), ccy + rr.normal(0, R * 0.38)
                lit = np.clip(0.5 - (ly - ccy) / R * 0.9 + rr.uniform(-0.25, 0.25), 0.08, 1.2)
                r = rr.uniform(0.0016, 0.0030)
                a = rr.uniform(0, math.pi)
                ca, sa = math.cos(a), math.sin(a)
                leaf = [(lx + ca * r, ly + sa * r), (lx - sa * r * 0.4, ly + ca * r * 0.4), (lx - ca * r, ly - sa * r),
                        (lx + sa * r * 0.4, ly - ca * r * 0.4)]
                c = (int(min(255, 176 * lit + 8)), int(min(255, 82 * lit + 6)), int(min(255, 44 * lit + 4)), 255)
                df.polygon(b.pts(leaf), fill=c)
    # a few leaves already falling
    for k in range(14):
        lx, ly = rr.uniform(0.15, 0.6), rr.uniform(0.25, 0.8)
        r = 0.0024
        a = rr.uniform(0, math.pi)
        ca, sa = math.cos(a), math.sin(a)
        df.polygon(b.pts([(lx + ca * r, ly + sa * r), (lx - sa * r * 0.4, ly + ca * r * 0.4), (lx - ca * r, ly - sa * r),
                          (lx + sa * r * 0.4, ly - ca * r * 0.4)]), fill=(180, 86, 46, 255))

    # ---------------- the ruins (in front of the tree)
    rc, rt, rl = b.mask(), b.mask(), b.mask()
    drc, drt, drl = ImageDraw.Draw(rc), ImageDraw.Draw(rt), ImageDraw.Draw(rl)

    def shape(ps, t, e=170, wdt=2):
        p = b.pts(ps)
        drc.polygon(p, fill=255)
        drt.polygon(p, fill=int(t))
        drl.polygon(p, fill=0)
        if e:
            drl.line(p + [p[0]], fill=int(e), width=wdt)

    def ln(ps, v, wdt=2):
        drl.line(b.pts(ps), fill=int(v), width=wdt)

    def blocks(x0, x1, y0, y1, bh, chip=0.25):
        """Masonry: courses of blocks with irregular, chipped outlines and their own tones."""
        y = y0
        row = 0
        while y < y1:
            x = x0 - (bh * 0.9 if row % 2 else 0)
            while x < x1:
                bw = bh * rr.uniform(1.4, 2.6)
                xa, xb = max(x, x0), min(x + bw, x1)
                if xb - xa > bh * 0.3:
                    j = lambda: rr.uniform(-chip, chip) * bh * 0.3
                    ps = [(xa + j(), y + j()), (xb + j(), y + j()), (xb + j(), y + bh + j()), (xa + j(), y + bh + j())]
                    if rr.random() < 0.25:     # a chipped corner
                        ps.insert(1, ((xa + xb) / 2, y + bh * rr.uniform(0.2, 0.5)))
                    shape(ps, rr.uniform(30, 80), int(rr.uniform(120, 200)), 2)
                x += bw
            y += bh
            row += 1

    # the cliff of old masonry hugging the edge: thick at the bottom and over the top corner
    prof = []
    for i in range(41):
        y = i / 40
        wdt = 0.09 + 0.24 * smoothstep(0.62, 1.0, y) + 0.17 * (1 - smoothstep(0.0, 0.26, y))
        prof.append([wdt, y])
    jag = np.convolve(rr.uniform(-1, 1, len(prof)), np.ones(5) / 5, mode="same") * 0.05
    for p_, j in zip(prof, jag):
        p_[0] += j
    # a dark core behind the blocks (no holes), then the blocks course by course
    shape([(-0.05, -0.01)] + [(p_[0] - 0.006, p_[1]) for p_ in prof] + [(-0.05, 1.01)], 18, 0)
    for i in range(len(prof) - 1):
        y0_, y1_ = prof[i][1], prof[i + 1][1]
        blocks(-0.02, prof[i][0], y0_, y1_, 0.025, 0.15)
    # a tall broken arch springing inward (the ruin of an older church), voussoirs drawn block by block
    ax, ays, ahw = 0.20, 0.40, 0.15
    outer = arch_pts(ax + ahw, ays, ahw + 0.035, 1.0, 30)
    inner = arch_pts(ax + ahw, ays, ahw - 0.002, 1.0, 30)
    cut = 17
    for k in range(cut):
        shape([outer[k], outer[k + 1], inner[k + 1], inner[k]], rr.uniform(40, 75), 190, 2)
    # broken end: a jagged stump
    shape([outer[cut], (outer[cut][0] + 0.012, outer[cut][1] + 0.006), inner[cut]], 50, 190, 2)
    # its pier with clustered colonettes and a capital
    shape([(ax - 0.04, ays - 0.004), (ax + 0.006, ays - 0.004), (ax + 0.012, 1.02), (ax - 0.046, 1.02)], 52, 190, 2)
    for xo in (-0.030, -0.017, -0.005):
        shape([(ax + xo - 0.004, ays + 0.02), (ax + xo + 0.004, ays + 0.02), (ax + xo + 0.0045, 1.0), (ax + xo - 0.0045, 1.0)], 70, 180, 2)
    shape([(ax - 0.048, ays - 0.004), (ax + 0.012, ays - 0.004), (ax + 0.006, ays + 0.022), (ax - 0.042, ays + 0.022)], 66, 200, 2)
    for k in range(5):
        ln([(ax - 0.044 + k * 0.012, ays + 0.002), (ax - 0.04 + k * 0.012, ays + 0.018)], 140)
    # a ruined low wall with an empty lancet window further in
    wx0, wx1, wtop = 0.24, 0.40, 0.70
    edge_pts = [(wx0, 1.02), (wx0, wtop + 0.03), (wx0 + 0.03, wtop), (wx0 + 0.07, wtop + 0.015), (wx0 + 0.10, wtop - 0.01),
                (wx1 - 0.03, wtop + 0.04), (wx1, wtop + 0.09), (wx1 + 0.01, 1.02)]
    shape(edge_pts, 44, 190, 2)
    blocks(wx0 + 0.004, wx1 - 0.004, wtop + 0.03, 1.0, 0.021, 0.35)
    win = lancet_pts(wx0 + 0.085, wtop + 0.075, 0.025, 0.93)
    p = b.pts(win)
    drc.polygon(p, fill=0)
    drl.line(p + [p[0]], fill=200, width=2)
    # rubble at the foot
    for i in range(22):
        x, y, r = rr.uniform(0.0, 0.45), rr.uniform(0.88, 1.01), rr.uniform(0.010, 0.035)
        shape(rock_poly(x, y, r, rr), rr.uniform(40, 80), 200, 2)
    ruin_rgb = shade_etched(b, arr(rc), arr(rt), arr(rl), 0.85, -0.5, (232, 228, 240), (196, 60, 54), seed + 2, 0.12)

    # ---------------- vines in front, hanging from the overhang and the arch
    vine = Image.new("RGBA", (b.W, b.H), (0, 0, 0, 0))
    dv = ImageDraw.Draw(vine)
    for k in range(20):
        if k < 12:
            x0 = rr.uniform(0.02, 0.30)
            y0 = float(np.interp(x0, [0, 0.3], [0.06, 0.15])) + rr.uniform(-0.03, 0.04)
        else:
            j = rr.integers(2, cut)
            x0, y0 = inner[j]
        L = rr.uniform(0.10, 0.45)
        pts = []
        for jj in range(30):
            t = jj / 29
            pts.append((x0 + math.sin(t * 5 + k) * 0.007 + t * 0.012 * (1 if k % 2 else -1), y0 + t * L))
        dv.line(b.pts(pts), fill=(22, 26, 18, 255), width=3)
        dv.line(b.pts(pts), fill=(70, 78, 56, 255), width=1)
        for jj in range(1, 30):
            if rr.random() < 0.55:
                px, py = pts[jj]
                side_ = rr.choice([-1, 1])
                r = rr.uniform(0.003, 0.006)
                lit = rr.uniform(0.5, 1.2)
                dv.polygon(b.pts([(px, py), (px + side_ * r, py + r * 0.3), (px + side_ * r * 0.7, py + r * 1.2), (px, py + r * 0.6)]),
                           fill=(int(26 * lit), int(36 * lit), int(22 * lit), 255), outline=(int(76 * lit), int(88 * lit), int(60 * lit), 255))
    # flowers spilling over the rubble
    flo = Image.new("RGBA", (b.W, b.H), (0, 0, 0, 0))
    dfl = ImageDraw.Draw(flo)
    for i in range(170):
        y = 0.80 + rr.random() ** 0.7 * 0.22
        x = rr.uniform(0.02, 0.36) * (0.5 + 0.5 * (y - 0.8) / 0.22)
        sc = 0.018 + 0.03 * (y - 0.8) / 0.22
        k = rr.random()
        if k < 0.3:
            blossom(dfl, b, x, y, sc * rr.uniform(0.9, 1.4), rr, 0.95)
        elif k < 0.42:
            leafy_tuft(dfl, b, x, y, sc * 1.5, rr, 0.9)
        else:
            red_cluster(dfl, b, x, y, sc * rr.uniform(0.9, 1.3), rr, 0.9)

    # ---------------- three layers so the game can sway the tree and the vines while the stone stays still:
    # <name>_tree (tree + foliage, behind), <name> (ruins + flowers), <name>_vines (in front)
    X, Y = b.grid()
    rcov = arr(rc)
    tcov = arr(tc)
    fa = np.asarray(fol, np.float32) / 255
    edge_dark = (0.55 + 0.45 * smoothstep(0.0, 0.2, X))[..., None]

    def out(rgb, a, nm):
        rgb = rgb * edge_dark
        if mirror:
            rgb, a = rgb[:, ::-1], a[:, ::-1]
        save_rgba(rgb, a, nm, (w, h))

    trgb = lerp(tree_rgb, fa[..., :3], fa[..., 3])
    out(trgb, np.clip(tcov + fa[..., 3], 0, 1), name + "_tree")
    rgb = ruin_rgb.copy()
    a = rcov.copy()
    la = np.asarray(flo, np.float32) / 255
    rgb = lerp(rgb * rcov[..., None], la[..., :3], la[..., 3])
    a = np.clip(a + la[..., 3], 0, 1)
    rgb = rgb / np.maximum(a, 1e-4)[..., None] * np.maximum(a, 1e-4)[..., None]
    out(np.where(a[..., None] > 0, rgb, 0), a, name)
    va = np.asarray(vine, np.float32) / 255
    out(va[..., :3], va[..., 3], name + "_vines")


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
    a = smoothstep(0.35, 0.75, n) * prof * 0.38
    rgb = np.broadcast_to(col((120, 70, 78)), (h, w, 3)).copy()
    rgb = lerp(rgb, col((70, 64, 86)), V * 0.6)
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
    """The red fall from the doorway: wraps in v (scrolls down); ragged sides, pale foam threads, darker folds."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / w, ys / h
    streak = fbm(V, U, 8, 111, True, 4, aspect=0.25)     # long vertical streaks, periodic in V
    fine = fbm(V, U, 22, 112, True, 3, aspect=0.2)
    rag = fbm(V, U * 0 + 0.5, 6, 113, True, 3, aspect=1.0)
    rgb = lerp(col((96, 6, 14)), col((226, 46, 52)), np.clip((streak - 0.5) * 3.0 + 0.5 + (fine - 0.5) * 1.2, 0, 1))
    foam = np.clip((fine - 0.66) / 0.14, 0, 1)
    rgb = lerp(rgb, col((255, 196, 186)), foam * 0.8)
    # vertical pencil strokes through the sheet
    lines = np.clip((np.sin(U * w * 0.9 + fine * 9) - 0.6) / 0.4, 0, 1)
    rgb = lerp(rgb, rgb * 0.55, lines * 0.5)
    edge = smoothstep(0.02 + rag * 0.12, 0.14 + rag * 0.12, U) * smoothstep(0.98 - rag[:, ::-1] * 0.12, 0.86 - rag[:, ::-1] * 0.12, U)
    a = edge * (0.8 + 0.2 * streak)
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


def leaf(size=32):
    ss = 4
    img = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    n = size * ss
    pts = [(n * 0.08, n * 0.5), (n * 0.35, n * 0.22), (n * 0.7, n * 0.24), (n * 0.94, n * 0.5), (n * 0.7, n * 0.74),
           (n * 0.35, n * 0.76)]
    d.polygon(pts, fill=(172, 82, 40, 255), outline=(70, 30, 16, 255))
    d.line([(n * 0.08, n * 0.5), (n * 0.94, n * 0.5)], fill=(96, 40, 20, 255), width=ss)
    for k in range(3):
        x = n * (0.3 + 0.2 * k)
        d.line([(x, n * 0.5), (x + n * 0.1, n * 0.3)], fill=(110, 48, 24, 255), width=ss // 2)
        d.line([(x, n * 0.5), (x + n * 0.1, n * 0.7)], fill=(110, 48, 24, 255), width=ss // 2)
    img.resize((size, size), Image.LANCZOS).save(os.path.join(OUT, "leaf.png"), optimize=True)


def ring(w=128, h=64):
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    X, Y = (xs - w / 2 + 0.5) / (w / 2), (ys - h / 2 + 0.5) / (h / 2)
    r = np.hypot(X, Y)
    a = np.exp(-((r - 0.85) / 0.06) ** 2) * (0.6 + 0.4 * np.clip(-Y, 0, 1))
    save_rgba(np.ones((h, w, 3), np.float32), np.clip(a, 0, 1), "ring")


def sprites():
    glow(); sparkle(); bats(); petal(); leaf(); ring(); cascade(); ripples(); streak(); grain_tex(); vignette(); mist()


# ---------------------------------------------------------------- the hand-drawn pass: ink pen + coloured pencil

PAPER = (22, 16, 17)            # warm dark-toned paper


def _strokes(mask_img, W, H, weight, along, L, count, rr, jitter=0.25, width=2, val=(150, 255), curve=0.18, angle_add=0.0):
    """Draw `count` (maximum) strokes into an L image. Start points are accepted with probability `weight`, each runs
    along the direction field `along` (+ angle_add), tapers at both ends (thin tips, full width in the middle), wobbles
    a little, and has its own pressure (value)."""
    d = ImageDraw.Draw(mask_img)
    xs = rr.uniform(0, W - 1, count)
    ys = rr.uniform(0, H - 1, count)
    xi, yi = xs.astype(np.int32), ys.astype(np.int32)
    keep = rr.random(count) < weight[yi, xi]
    xs, ys, xi, yi = xs[keep], ys[keep], xi[keep], yi[keep]
    n = len(xs)
    ang = along[yi, xi] + angle_add + rr.normal(0, jitter, n)
    ln_ = L * rr.uniform(0.55, 1.35, n)
    ca, sa = np.cos(ang), np.sin(ang)
    bend = rr.normal(0, curve, n) * ln_
    v = rr.integers(val[0], val[1] + 1, n)
    tip = max(1, width // 2)
    for i in range(n):
        hx, hy = ca[i] * ln_[i] * 0.5, sa[i] * ln_[i] * 0.5
        mx_, my_ = xs[i] - sa[i] * bend[i] * 0.5, ys[i] + ca[i] * bend[i] * 0.5
        p0 = (xs[i] - hx, ys[i] - hy)
        p1 = (mx_ - hx * 0.45, my_ - hy * 0.45)
        p2 = (mx_ + hx * 0.45, my_ + hy * 0.45)
        p3 = (xs[i] + hx, ys[i] + hy)
        vv = int(v[i])
        d.line([p0, p1], fill=vv, width=tip)
        d.line([p1, p2], fill=vv, width=width)
        d.line([p2, p3], fill=vv, width=tip)
    return n


def hand_drawn(name, default_angle=math.pi / 2, pencil=1.0, ink=1.0, gel=1.0, stipple=0.0, L=9.0, seed=0,
               burnish=None, coherence_min=0.25, keep=0.45, chalk=0.0):
    """Re-render a painted layer as an ink-and-coloured-pencil drawing on dark paper:
    - coloured pencil: three passes of soft strokes along the form (the structure tensor's edge direction, falling back
      to default_angle where the form is vague) deposit the layer's colour only where the paper's tooth catches it;
    - ink: contour-following hatching where the value is dark, cross-hatching (2-4 directions) as it darkens further,
      stippling for texture;
    - gel pen: short white strokes on the brightest lit edges (the moon's rim light, glints).
    `burnish` (a function of the work-resolution lum/alpha) marks focal areas where the pigment is laid dense and smooth.
    L is the stroke length in output pixels; the work resolution is 2x."""
    path = os.path.join(OUT, name + ".png")
    im = Image.open(path).convert("RGBA")
    w0, h0 = im.size
    ss = 2
    W, H = w0 * ss, h0 * ss
    a4 = np.asarray(im.resize((W, H), Image.BICUBIC), np.float32) / 255
    src, al = np.clip(a4[..., :3], 0, 1), np.clip(a4[..., 3], 0, 1)
    rr = np.random.default_rng(seed)
    lum = src @ np.array([0.30, 0.59, 0.11], np.float32)
    inten = src.max(-1)
    # direction field: along the edges of the form
    lb = blur(lum * al + (1 - al) * 0.0, ss * 1.5)
    gy, gx = np.gradient(lb)
    jxx, jxy, jyy = blur(gx * gx, ss * 5), blur(gx * gy, ss * 5), blur(gy * gy, ss * 5)
    theta = 0.5 * np.arctan2(2 * jxy, jxx - jyy)
    coh = np.sqrt((jxx - jyy) ** 2 + 4 * jxy ** 2) / (jxx + jyy + 1e-7)
    along = theta + math.pi / 2
    k = smoothstep(coherence_min, coherence_min + 0.3, coh)
    # blend toward the default as doubled-angle vectors (directions are axial)
    vx = np.cos(2 * along) * k + math.cos(2 * default_angle) * (1 - k)
    vy = np.sin(2 * along) * k + math.sin(2 * default_angle) * (1 - k)
    along = 0.5 * np.arctan2(vy, vx)
    Ls = L * ss
    area = W * H
    # paper tooth: pigment only on the raised grain
    tooth = rr.random((H // 2 + 1, W // 2 + 1)).astype(np.float32)
    tooth = np.asarray(Image.fromarray((tooth * 255).astype(np.uint8)).resize((W, H), Image.BILINEAR), np.float32)[:H, :W] / 255
    tooth = np.clip((tooth - 0.25) * 1.8, 0, 1)
    # --- coloured pencil passes
    pig = np.zeros((H, W), np.float32)
    wpen = np.clip(inten * 1.4, 0, 1) ** 0.8 * al
    for k_, add in enumerate((0.0, 0.42, -0.42)):
        m = Image.new("L", (W, H), 0)
        _strokes(m, W, H, wpen * (0.9 if k_ == 0 else 0.6), along, Ls, int(area / (Ls * ss * 1.6) * pencil), rr,
                 0.22, ss, (110, 255), 0.15, add)
        pig += np.asarray(m, np.float32) / 255
    dep = np.clip(pig * 0.55, 0, 1) * (0.35 + 0.95 * tooth)
    dep = np.clip(dep, 0, 1)
    paper = col(PAPER)
    base = paper * 0.5 + src * keep
    pen_col = np.clip(src * 1.25 + 0.02, 0, 1)
    out = lerp(base, pen_col, dep)
    # --- light pencil on the dark paper: grey/silver hatching that follows the form, denser where the stone is lit
    if chalk > 0:
        cm = Image.new("L", (W, H), 0)
        wch = smoothstep(0.03, 0.22, lum) * al * (1 - smoothstep(0.5, 0.8, inten - lum))
        for add in (0.0, 0.5):
            _strokes(cm, W, H, wch * (1.0 if add == 0 else smoothstep(0.10, 0.3, lum)), along, Ls * 1.1,
                     int(area / (Ls * ss * 1.4) * chalk), rr, 0.07, max(1, ss - 1), (90, 220), 0.06, add)
        cv = np.asarray(cm, np.float32) / 255 * (0.4 + 0.8 * tooth)
        chalk_col = np.clip(src * 1.6 + col((40, 40, 46)) + src.mean(-1, keepdims=True) * 0.8, 0, 1)
        out = lerp(out, chalk_col, np.clip(cv, 0, 1) * 0.65)
    if burnish is not None:
        bm = np.clip(burnish(lum, al, src), 0, 1)
        out = lerp(out, np.clip(src * 1.08, 0, 1), bm * 0.85)
    # --- ink: hatching that follows the form, cross-hatching as it darkens, stipple
    if ink > 0:
        inkm = np.zeros((H, W), np.float32)
        for th_hi, th_lo, add, dens in ((0.42, 0.20, 0.0, 1.0), (0.26, 0.10, math.pi / 2, 0.8), (0.16, 0.05, math.pi / 4, 0.7),
                                         (0.10, 0.02, -math.pi / 4, 0.6)):
            wgt = smoothstep(th_hi, th_lo, lum) * al
            m = Image.new("L", (W, H), 0)
            _strokes(m, W, H, wgt * dens, along, Ls * 1.1, int(area / (Ls * ss * 2.2) * ink), rr, 0.08, max(1, ss - 1),
                     (120, 230), 0.05, add)
            inkm = np.maximum(inkm, np.asarray(m, np.float32) / 255)
        out *= (1 - inkm * 0.55)[..., None]
        # contour: an ink line round every silhouette, heavier where the shape is dark (the shadow side)
        band = np.clip((al - blur(al, ss * 0.9)) * 4, 0, 1)
        out = lerp(out, col((6, 4, 5)), band * smoothstep(0.35, 0.05, lum) * 0.8 * ink)
    if stipple > 0:
        st = Image.new("L", (W, H), 0)
        dst = ImageDraw.Draw(st)
        n = int(area / 60 * stipple)
        px_ = rr.uniform(0, W - 1, n)
        py_ = rr.uniform(0, H - 1, n)
        wv = (smoothstep(0.03, 0.12, lum) * smoothstep(0.45, 0.2, lum) * al)[py_.astype(int), px_.astype(int)]
        sel = rr.random(n) < wv
        for x, y in zip(px_[sel], py_[sel]):
            dst.ellipse([x - 0.9, y - 0.9, x + 0.9, y + 0.9], fill=255)
        stv = np.asarray(st, np.float32) / 255
        out = lerp(out, out * 0.45, stv * 0.7)
    # --- gel pen on the brightest lit edges
    if gel > 0:
        gm_ = Image.new("L", (W, H), 0)
        edge = np.clip(np.hypot(*np.gradient(blur(lum, ss))) * ss * 9, 0, 1)
        wg = smoothstep(0.45, 0.8, lum) * edge * al
        _strokes(gm_, W, H, wg, along, Ls * 0.8, int(area / (Ls * ss * 3) * gel), rr, 0.1, ss, (150, 255), 0.05)
        gv = np.asarray(gm_, np.float32) / 255
        out = lerp(out, col((246, 240, 232)), gv * 0.75)
    # back to size (premultiplied, so no fringes)
    save_rgba(out, al, name, (w0, h0))


def hand_drawn_all():
    hd = hand_drawn
    hd("sky", 0.0, pencil=0.9, ink=0.0, gel=0.0, L=14, seed=201, keep=0.6)
    hd("clouds_far", 0.08, pencil=1.0, ink=0.4, gel=0.3, L=12, seed=202, keep=0.4)
    hd("clouds_near", 0.05, pencil=1.0, ink=0.4, gel=0.0, L=12, seed=203, keep=0.4)
    hd("moon", 0.0, pencil=1.2, ink=0.6, gel=0.0, stipple=0.8, L=7, seed=204, keep=0.5,
       burnish=lambda l, a, s: smoothstep(0.55, 0.8, l) * 0.6)
    hd("spires_far", math.pi / 2, pencil=0.8, ink=0.7, gel=0.4, L=8, seed=205, chalk=1.0, keep=0.5)
    hd("cathedral", math.pi / 2, pencil=1.0, ink=1.0, gel=1.0, stipple=0.6, L=7, seed=206, chalk=1.0, keep=0.55,
       burnish=lambda l, a, s: smoothstep(0.25, 0.45, s[..., 0] - s[..., 2]) * smoothstep(0.3, 0.5, l))
    hd("ground", 0.0, pencil=1.0, ink=1.0, gel=0.8, stipple=0.5, L=8, seed=207, chalk=1.0, keep=0.5,
       burnish=lambda l, a, s: smoothstep(0.30, 0.5, s[..., 0] - s[..., 1]) * 0.5)
    hd("field_far", math.pi / 2, pencil=1.0, ink=0.7, gel=0.4, L=5, seed=208, keep=0.55)
    hd("field_near", math.pi / 2, pencil=1.0, ink=0.9, gel=0.7, L=7, seed=209, keep=0.55)
    for nm, sd in (("frame_left", 210), ("frame_right", 211)):
        hd(nm, math.pi / 2, pencil=1.0, ink=1.0, gel=0.6, stipple=0.6, L=8, seed=sd, chalk=1.0, keep=0.5)
        hd(nm + "_tree", math.pi / 2, pencil=1.0, ink=0.9, gel=0.5, L=6, seed=sd + 10, chalk=0.8, keep=0.55)


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
    S.art(L["cathedral_rim"], "cathedral", mode="add", alpha=0.45)
    S.art(L["glass_rose"], "glass_rose", mode="add", alpha=0.5)
    S.art(L["glass_lancets"], "glass_lancets", mode="add", alpha=0.45)
    S.art(L["mist"], "mist", wrap_dx=t * 0.01 * S.A, alpha=0.9)
    S.art(L["ground"], "ground")
    hz = 0.09
    S.place(L["glow"], S.sx(-hz), S.sy(0.735 - hz * 0.6), S.sx(hz), S.sy(0.735 + hz * 0.6), mode="add", tint=(1, 0.55, 0.3), alpha=0.25)
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
    off = 0.03 * S.A
    for part in ("_tree", "", "_vines"):
        S.place(L["frame_left" + part], -off, S.H - S.A, fwpx - off, S.H)
        S.place(L["frame_right" + part], S.W - fwpx + off, S.H - S.A, S.W + off, S.H)
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


# ---------------------------------------------------------------- the gothic lettering, previewed (GothicText.java)

FONT_DIR = os.path.join(REPO, "bar/src/main/resources/assets/reignofnether/font")


def _blood_text(img, text, font_file, size_gui, x, y, gs, rim=(184, 137, 47), drips=0, drip_len=4, t=1.3, seed=0):
    """GothicText.blood + drips at GUI scale gs, as the game draws them (top-left x, y in screen px)."""
    from PIL import ImageFont
    f = ImageFont.truetype(font_file, int(round(size_gui * gs * 1.25)))
    d = ImageDraw.Draw(img)
    bb = d.textbbox((0, 0), text, font=f)
    ox, oy = x - bb[0], y - bb[1]
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    d.text((ox + gs, oy + 2 * gs), text, font=f, fill=(0, 0, 0, 190))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if dx or dy:
                d.text((ox + dx * gs, oy + dy * gs), text, font=f, fill=rim + (255,))
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text((ox, oy), text, font=f, fill=(126, 8, 18, 255))
    for frac, c in ((0.62, (184, 20, 31)), (0.30, (232, 80, 90))):
        band = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(band).text((ox, oy), text, font=f, fill=c + (255,))
        m = Image.new("L", img.size, 0)
        ImageDraw.Draw(m).rectangle([0, 0, img.size[0], y + th * frac], fill=255)
        layer = Image.composite(band, layer, Image.composite(band.getchannel("A"), Image.new("L", img.size, 0), m))
    img.alpha_composite(layer)
    r = np.random.default_rng(seed)
    for i in range(drips):
        sx = x + tw * (i + 0.3 + 0.4 * r.random()) / drips
        L = drip_len * gs * r.uniform(0.6, 1.0)
        base = y + th - gs
        d.rectangle([sx, base, sx + gs * (2 if drip_len > 6 else 1), base + L], fill=(138, 10, 20, 255))
        d.rectangle([sx - gs * 0.5, base + L - gs, sx + gs * 1.5 * (2 if drip_len > 6 else 1), base + L + gs], fill=(138, 10, 20, 255))
        if i % 2 == 0:
            dy = L + drip_len * gs * 1.6
            d.rectangle([sx, base + dy, sx + gs * 1.5, base + dy + gs * 2], fill=(138, 10, 20, 200))
    return tw


def plate(w=1024, h=96):
    """The title / lobby buttons' carved dark stone (GothicText.plate samples a bw x bh window of it at 4 px per GUI
    unit, at a hashed offset). Opaque; the bevel, gold inlay and blood are drawn in game on top. Ink and pencil: mottled
    basalt, a lighter worn face toward the top, diagonal ink hatching that thickens in the low spots, a few hairline
    cracks with lit lips, pitting, chipped nicks."""
    r = np.random.default_rng(91)
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / h, ys / h
    m = fbm(U, V, 3, 911, octaves=5) * 0.6 + fbm(U, V, 14, 912, octaves=3) * 0.4          # 0..1 mottling
    base = lerp(col((24, 19, 18)), col((50, 42, 38)), m)
    face = smoothstep(1.0, 0.15, ys / h)[..., None]                                           # lit toward the top
    rgb = base * (0.78 + 0.34 * face)
    rgb = rgb + (fbm(U, V, 2, 913, octaves=2)[..., None] - 0.5) * col((14, 6, 4))         # warm / cool drift
    # ink hatching (45 degrees), denser and darker where the stone is low
    low = smoothstep(0.62, 0.30, m)
    for ang, period, k in ((0.78, 5.0, 1.0), (-0.78, 6.5, 0.6)):
        u = xs * math.cos(ang) + ys * math.sin(ang)
        wob = fbm(U, V, 6, 920 + int(period), octaves=2) * 3.0
        line = smoothstep(0.38, 0.0, np.abs(((u + wob) / period) % 1.0 - 0.5) * 2 - 0.62)
        broken = fbm(U * 3, V, 9, 930 + int(period), octaves=2) > 0.42
        dens = low * k if k < 1 else (0.25 + 0.75 * low)
        rgb = rgb * (1 - (0.45 * line * dens * broken)[..., None])
    # pencil tooth: light grey specks on the raised grain
    tooth = r.random((h, w)).astype(np.float32)
    rgb = rgb + ((tooth > 0.93) * m * 0.07)[..., None]
    rgb = rgb + grain(h, w, 94, 0.018)[..., None]
    img = Image.fromarray(np.round(np.clip(rgb, 0, 1) * 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(img)
    # hairline cracks with a lit lower lip
    for c in range(9):
        x, y = r.uniform(0, w), r.uniform(h * 0.15, h * 0.85)
        ang = r.uniform(-0.6, 0.6) + (math.pi if r.random() < 0.5 else 0)
        pts = [(x, y)]
        for _ in range(int(r.integers(6, 14))):
            ang += r.normal(0, 0.45)
            x += math.cos(ang) * r.uniform(4, 10)
            y += math.sin(ang) * r.uniform(2, 6)
            pts.append((x, y))
        d.line([(px, py + 1) for px, py in pts], fill=(78, 68, 62), width=1)
        d.line(pts, fill=(6, 4, 5), width=2 if c % 3 == 0 else 1)
    # pits and chipped nicks
    for _ in range(140):
        x, y, rr = r.uniform(0, w), r.uniform(0, h), r.uniform(0.6, 1.8)
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(8, 6, 6))
        d.point((x, y + rr + 0.5), fill=(70, 60, 54))
    for _ in range(26):
        x, y = r.uniform(0, w), r.choice([r.uniform(0, 8), r.uniform(h - 8, h)])
        rr = r.uniform(2, 4.5)
        d.polygon([(x - rr, y), (x, y - rr * 0.7), (x + rr, y + 0.3), (x + rr * 0.3, y + rr * 0.8)], fill=(12, 9, 9))
    img.save(os.path.join(OUT, "plate.png"), optimize=True)
    return img


# GothicText's label metrics (mirrors the Java): provider size 25 Cinzel Black; stbtt scales ascent - descent (976 + 372)
# to the size, so the capitals (700 units) are 13 GUI tall and their tops 276 units below the drawString y.
BTN_SIZE = 25.0
BTN_CAP = BTN_SIZE * 700 / 1348
BTN_CAP_TOP = BTN_SIZE * 276 / 1348
BTN_ASC = BTN_SIZE * 976 / 1348


def _h32(x):
    """GothicText.hash (Java int arithmetic)."""
    x = (x * 0x9E3779B1) & 0xFFFFFFFF
    x ^= x >> 15
    x = (x * 0x85EBCA77) & 0xFFFFFFFF
    x ^= x >> 13
    return x & 0x7FFFFFFF


def _jhash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - (1 << 32) if h >= 1 << 31 else h


class GuiCanvas:
    """GuiGraphics.fill / blit / drawString at a GUI scale onto a PIL RGBA image (coords in GUI units)."""

    def __init__(self, img, gs):
        self.img, self.gs = img, gs
        self.d = ImageDraw.Draw(img, "RGBA")

    def fill(self, x0, y0, x1, y1, argb):
        if x1 <= x0 or y1 <= y0:
            return
        a = (argb >> 24) & 255
        c = ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, a)
        g = self.gs
        self.d.rectangle([round(x0 * g), round(y0 * g), round(x1 * g) - 1, round(y1 * g) - 1], fill=c)

    def outline(self, x, y, w, h, argb):
        self.fill(x, y, x + w, y + 1, argb)
        self.fill(x, y + h - 1, x + w, y + h, argb)
        self.fill(x, y + 1, x + 1, y + h - 1, argb)
        self.fill(x + w - 1, y + 1, x + w, y + h - 1, argb)

    def blit(self, tex, x, y, w, h, u, v, uw, vh, tint=(1, 1, 1)):
        g = self.gs
        crop = tex.crop((int(u), int(v), int(u + uw), int(v + vh))).resize((w * g, h * g), Image.BILINEAR)
        if tint != (1, 1, 1):
            a = np.asarray(crop, np.float32) * np.asarray(tint, np.float32)
            crop = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB")
        self.img.paste(crop.convert("RGBA"), (x * g, y * g))

    def text(self, text, font_file, size, x, y, s, argb, clip=None):
        """drawString of a TTF provider (size `size`, ascent from hhea) at (x, y) scaled by s."""
        from PIL import ImageFont
        g = self.gs
        f = ImageFont.truetype(font_file, max(1, int(round(size * s * g * 1000 / 1348))))
        base = (y + BTN_ASC * s) * g
        a = (argb >> 24) & 255
        c = ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, a)
        if clip is None:
            self.d.text((x * g, base), text, font=f, fill=c, anchor="ls")
            return
        layer = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).text((x * g, base), text, font=f, fill=c, anchor="ls")
        m = Image.new("L", self.img.size, 0)
        ImageDraw.Draw(m).rectangle([clip[0] * g, clip[1] * g, clip[2] * g - 1, clip[3] * g - 1], fill=255)
        layer.putalpha(Image.composite(layer.getchannel("A"), Image.new("L", self.img.size, 0), m))
        self.img.alpha_composite(layer)

    def width(self, text, font_file, size):
        from PIL import ImageFont
        return ImageFont.truetype(font_file, 1000).getlength(text) / 1000 * size * 1000 / 1348


CAPS_TTF = os.path.join(FONT_DIR, "cinzel_black.ttf")


def plate_button(cv, label, x0, y0, bw, bh, hot=False, active=True, t=1.3, tex=None):
    """GothicText.restyleButtons for one button, all three passes (glow, plate, blood + label) - mirrors the Java."""
    plate_button_pass(cv, 0, label, x0, y0, bw, bh, hot, active, t, tex)
    plate_button_pass(cv, 1, label, x0, y0, bw, bh, hot, active, t, tex)
    plate_button_pass(cv, 2, label, x0, y0, bw, bh, hot, active, t, tex)


def plate_button_pass(cv, pas, label, x0, y0, bw, bh, hot, active, t, tex):
    x1, y1 = x0 + bw, y0 + bh
    if pas == 0:
        if hot:
            for i in range(4, 0, -1):
                al = int((14 + 16 * 0.8) * (5 - i) / 4)
                cv.fill(x0 - i - 1, y0 - i, x1 + i + 1, y1 + i, (al << 24) | 0xA00A18)
        return
    if pas == 1:
        tex = tex or Image.open(os.path.join(OUT, "plate.png")).convert("RGB")
        uw, vh = min(1024, bw * 4), min(96, bh * 4)
        u = (_h32(_jhash(label)) % max(1, (1024 - uw) // 4)) * 4
        lit = 0.55 if not active else 1.18 if hot else 1.0
        cv.blit(tex, x0, y0, bw, bh, u, (96 - vh) / 2, uw, vh, (lit, lit * (0.93 if hot else 1), lit * (0.92 if hot else 1)))
        for r_ in ((x0, y0, x1, y0 + 1), (x0, y1 - 1, x1, y1), (x0, y0, x0 + 1, y1), (x1 - 1, y0, x1, y1)):
            cv.fill(*r_, 0xFF050304)
        cv.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0xD8B8A08A if hot else 0xB08E8274)
        cv.fill(x0 + 1, y0 + 2, x1 - 1, y0 + 3, 0x40A09080)
        cv.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 2, 0x705E544C)
        cv.fill(x0 + 1, y1 - 3, x1 - 1, y1 - 1, 0xD0080506)
        cv.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 1, 0xA0080506)
        gold = 0xFF5C4A2C if not active else 0xFFF0CC72 if hot else 0xFFB08A3E
        cv.outline(x0 + 3, y0 + 4, bw - 6, bh - 7, 0xC0000000)
        cv.outline(x0 + 3, y0 + 3, bw - 6, bh - 7, gold)
        if active:
            stud = 0xFFFFE6A0 if hot else 0xFFD8B060
            for sx, sy in ((x0 + 2, y0 + 2), (x1 - 5, y0 + 2), (x0 + 2, y1 - 6), (x1 - 5, y1 - 6)):
                cv.fill(sx, sy, sx + 3, sy + 3, stud)
        if hot:
            cv.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, 0x30C01020)
        if active:
            cv.fill(x0 + 1, y1 - 1, x1 - 1, y1, 0xFF5E0610)
        return
    # pass 2: blood, then the label
    if active:
        _plate_drips(cv, x0, y1, bw, label, t, hot)
    text = label.upper()
    w = cv.width(text, CAPS_TTF, BTN_SIZE)
    s = min(1.0, (bw - 14) / max(1, w), bh * 0.68 / BTN_CAP)
    cap = BTN_CAP * s
    tx = x0 + (bw - w * s) / 2
    ty = y0 + (bh - cap) / 2 - 0.5 - BTN_CAP_TOP * s
    if not active:
        cv.text(text, CAPS_TTF, BTN_SIZE, tx + 0.5, ty + 0.75, s, 0xD0000000)
        cv.text(text, CAPS_TTF, BTN_SIZE, tx, ty, s, 0xFF6E6152)
        return
    cv.text(text, CAPS_TTF, BTN_SIZE, tx + 1.2, ty + 1.8, s, 0xC0000000)
    ol = 0xFF6A0612 if hot else 0xFF3A0309
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if dx or dy:
                cv.text(text, CAPS_TTF, BTN_SIZE, tx + dx * 0.75, ty + dy * 0.75, s, ol)
    top = ty + BTN_CAP_TOP * s
    bands = [0xFFE8B850, 0xFFF6D890, 0xFFFFF2D8, 0xFFFFFAEE] if hot else [0xFFA87428, 0xFFD2A04A, 0xFFEFD08A, 0xFFF8EED2]
    cut = [2.0, 0.80, 0.55, 0.30]
    for k in range(4):
        clip = None if k == 0 else (x0, y0 - 2, x0 + bw, round(top + cap * cut[k]))
        cv.text(text, CAPS_TTF, BTN_SIZE, tx, ty, s, bands[k], clip)


def _plate_drips(cv, x0, y, bw, label, t, hot):
    BLOOD, DARK, HI = 0xFF9C0E1A, 0xFF5E0610, 0xFFE0505A

    def strand(x, L, wd):
        if L <= 0:
            return
        cv.fill(x - 1, y, x + wd + 1, y + max(1, L // 3), DARK)
        cv.fill(x, y, x + wd, y + L, BLOOD)
        cv.fill(x, y, x + 1, y + max(1, L - 1), (int(255 * 0.7) << 24) | (HI & 0xFFFFFF))
        cv.fill(x - 1, y + L - 1, x + wd + 1, y + L + 2, BLOOD)
        cv.fill(x, y + L + 2, x + wd, y + L + 3, DARK)

    n = 4 if bw >= 150 else 3 if bw >= 90 else 2
    seed = (_jhash(label) * 31 + 7) & 0xFFFFFFFF
    maxlen = 9.0 if hot else 5.5
    for i in range(n):
        h = _h32((seed + i * 7919) & 0xFFFFFFFF)
        fx = (i + 0.25 + 0.5 * ((h >> 3) % 1000) / 1000) / n
        sx = x0 + 6 + round(fx * (bw - 12))
        wd = 1 if h % 3 == 0 else 2
        ln = maxlen * (0.6 + 0.4 * (_h32(h + 3) % 100) / 100)
        cv.fill(sx - 1, y - 1, sx + wd + 1, y + 1, BLOOD)
        period = (3 + 3 * (h % 1000) / 1000) / (3 if hot else 1)
        p = (t / period + (_h32(h) % 1000) / 1000) % 1
        if p < 0.55:
            k = p / 0.55
            strand(sx, round(ln * (0.3 + 0.7 * k * k * (3 - 2 * k))), wd)
        elif p < 0.70:
            L = round(ln)
            strand(sx, L, wd)
            bead = 1 if (p - 0.55) / 0.15 > 0.5 else 0
            cv.fill(sx - 1 - bead, y + L - 1, sx + wd + 1 + bead, y + L + 2 + bead, BLOOD)
        else:
            k = (p - 0.70) / 0.30
            strand(sx, round(ln * (0.3 + 0.15 * (1 - k))), wd)
            dy = round(ln + 2 + k * k * 22)
            da = round(255 * (1 - k * k))
            cv.fill(sx - 1, y + dy, sx + wd + 1, y + dy + 3, (da << 24) | (BLOOD & 0xFFFFFF))
            cv.fill(sx, y + dy - 1, sx + wd, y + dy, (da << 24) | (BLOOD & 0xFFFFFF))


def title_buttons(img, gs, t=1.3, hot_label="Singleplayer"):
    """The title screen's buttons (TitleScreenMixin layout: 200 x 20 rows 24 apart, Options / Quit 98 wide) at GUI
    scale gs on img; returns the crop box around them in px."""
    cv = GuiCanvas(img, gs)
    gw, gh = img.width // gs, img.height // gs
    cx = gw // 2
    y = gh // 4 + 48
    btns = [("Skirmish", cx - 100, y - 26, 200), ("Singleplayer", cx - 100, y, 200), ("Multiplayer", cx - 100, y + 24, 200),
            ("Mods", cx - 100, y + 48, 200), ("Options...", cx - 100, y + 84, 98), ("Quit Game", cx + 2, y + 84, 98)]
    tex = Image.open(os.path.join(OUT, "plate.png")).convert("RGB")
    for pas in range(3):
        for lab, bx, by, bw in btns:
            plate_button_pass(cv, pas, lab, bx, by, bw, 20, lab == hot_label, True, t, tex)
    return ((cx - 116) * gs, (y - 34) * gs, (cx + 116) * gs, (y + 84 + 34) * gs)


def text_previews():
    """The title screen's buttons (full screen at GUI scale 2, 100 % crops at 2 and 4) and the skirmish lobby with its
    blackletter heading and plate buttons."""
    sys.path.insert(0, HERE)
    import gen_splash_art as SA
    layers = SA.load_layers()
    for gs in (2, 4):
        try:
            bg = SA.compose(layers, 1920, 1080, gui_scale=gs).convert("RGBA")
        except Exception as e:               # never fail a regen on the splash preview
            print("splash preview unavailable:", e)
            bg = Image.new("RGBA", (1920, 1080), (20, 14, 14, 255))
        box = title_buttons(bg, gs)
        if gs == 2:
            bg.convert("RGB").save(os.path.join(DESIGN, "lobby_text_preview_title.png"), optimize=True)
        bg.crop(box).convert("RGB").save(os.path.join(DESIGN, f"lobby_buttons_gui{gs}.png"), optimize=True)

    gs = 2
    gh = 1080 // gs
    S = compose(1920, 1080, lobby=True)
    img = Image.fromarray(np.round(np.clip(S.img, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")
    pw = 620
    x0 = (960 // gs - pw // 2)
    top = max(24, gh // 2 - 150)
    _blood_text(img, "Skirmish", os.path.join(FONT_DIR, "unifrakturcook_bold.ttf"), 16, x0 * gs, (top - 21) * gs, gs,
                rim=(154, 116, 40), drips=3, drip_len=9, seed=3)
    cv = GuiCanvas(img, gs)
    by = top + 300 - 22
    btns = (("Start Battle", 110), ("Spectate", 70), ("Back", 60))
    for pas in range(3):
        bx = x0 + 230
        for k, (lab, wdt) in enumerate(btns):
            plate_button_pass(cv, pas, lab, bx, by, wdt, 20, k == 0, True, 1.3, None)
            bx += wdt + 4
    img.convert("RGB").save(os.path.join(DESIGN, "lobby_preview_16x9_lobby.png"), optimize=True)


def previews():
    compose(1920, 1080).save(os.path.join(DESIGN, "lobby_preview_16x9.png"))
    compose(2520, 1080).save(os.path.join(DESIGN, "lobby_preview_21x9.png"))
    text_previews()


SMOOTH = {"plate", "glow", "mist", "vignette", "glass_rose", "glass_lancets", "grain", "sparkle", "ring", "streak", "ripples",
          "cathedral_rim"}


def compact():
    """Palette the textured layers (256 colours, Floyd-Steinberg): the pencil grain hides the quantisation and the files
    shrink 3-5x. Minecraft's NativeImage (stb_image) reads paletted PNGs with tRNS alpha. Smooth glows stay RGBA."""
    for f in sorted(os.listdir(OUT)):
        nm = f[:-4]
        path = os.path.join(OUT, f)
        if nm in SMOOTH or os.path.getsize(path) < 120_000:
            continue
        im = Image.open(path)
        if im.mode == "P":
            continue
        q = im.convert("RGBA").quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG)
        q.save(path, optimize=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    if "--plate" in sys.argv:
        plate()
        text_previews()
        return
    if "--previews-only" not in sys.argv:
        sky(); moon(); all_clouds(); spires_far(); cathedral(); ground(); fields(); frames(); sprites(); plate()
        hand_drawn_all()
        compact()
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
