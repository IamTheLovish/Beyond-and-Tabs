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
SCRATCH = os.environ.get("LOBBY_SCRATCH", "/tmp")
LAYOUT = "--layout" in sys.argv     # quick geometry check: flat tones, no ink pass

# ---------------------------------------------------------------- shared layout (LobbyBackdrop.java mirrors these)
PX_PER_A = 1100.0
ART_W = 2560 / PX_PER_A                     # 2.327 A: a 21:9 screen, a hair narrower
MOON = (-0.27, 0.205, 0.14)                 # centre x, y, radius (upper left third)
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
RIVER = [(0.372, 0.800, 0.048), (0.330, 0.812, 0.064), (0.200, 0.826, 0.080), (0.020, 0.846, 0.098),
         (-0.160, 0.868, 0.118), (-0.285, 0.895, 0.130), (-0.300, 0.925, 0.160), (-0.160, 0.955, 0.200),
         (0.040, 0.985, 0.250), (0.170, 1.030, 0.320)]
HORIZON = 0.64                              # perspective for the river's flow coordinate
CASCADE = (0.300, 0.688, 0.016, 0.372, 0.802, 0.042)  # top x, top y, top half width, bottom x, bottom y, bottom half width

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
        self.wob = 1.3 * board.ss / 2   # the hand's wobble (work px): a smooth displacement field shared by every element
        self.jr = np.random.default_rng(4242)
        self.T = None                       # plane transform (u, v) -> artboard (x, y); None = draw in artboard coords
        self.course = board.mask()          # stone coursing joints, drawn in each wall's own plane (perspective-true)
        self.dco = ImageDraw.Draw(self.course)
        self.dirm = board.mask()            # the plane's horizontal direction on screen (0 = none, 1..255 = 0..pi)
        self.ddir = ImageDraw.Draw(self.dirm)
        self.lit = board.mask()             # how much a face turns toward the moon (side faces > front faces)
        self.dlit = ImageDraw.Draw(self.lit)
        self.face_lit = 0

    # planes: points are given in the plane's own (u, v): u along it, v = world height (y down)
    def plane(self, ox, oz, dx, dz, lit=None):
        self.T = lambda u, v: proj(ox + u * dx, v, oz + u * dz)
        if lit is not None:
            self.face_lit = lit

    def front(self, z=0.0, lit=0):
        self.plane(0.0, z, 1.0, 0.0, lit)

    def side(self, x, lit=150):
        self.plane(x, 0.0, 0.0, 1.0, lit)

    def flat(self):
        self.T = None

    def tp(self, ps):
        return list(ps) if self.T is None else [self.T(x, y) for x, y in ps]

    def _dir(self, ps):
        if self.T is None:
            return 0
        cx = sum(p[0] for p in ps) / len(ps)
        cy = sum(p[1] for p in ps) / len(ps)
        a0, a1 = self.T(cx, cy), self.T(cx + 0.01, cy)
        ang = math.atan2(a1[1] - a0[1], a1[0] - a0[0]) % math.pi
        return 1 + int(ang / math.pi * 254)

    def courses(self, u0, v0, u1, v1, ch=0.0068, v=255):
        """Stone coursing on a wall in the current plane: bed joints every `ch` and staggered head joints."""
        rows = int((v1 - v0) / ch)
        for i in range(1, rows + 1):
            y = v0 + i * ch
            self.dco.line(self.W(self.b.pts(self.tp([(u0, y), (u1, y)])), jit=0.3), fill=v, width=max(1, int(self.lw * 0.6)))
            L = ch * (2.0 + 0.8 * ((i * 7) % 3))
            x = u0 + ((i * 0.37) % 1.0) * L
            while x < u1:
                self.dco.line(self.b.pts(self.tp([(x, y), (x, y - ch)])), fill=v, width=max(1, int(self.lw * 0.6)))
                x += L * (0.8 + 0.4 * ((x * 977) % 1.0))

    def poly3(self, pts3, tone, edge=150, w=1.0, lit=None):
        """A face given by world (X, Y, Z) points."""
        T, self.T = self.T, None
        fl = self.face_lit
        if lit is not None:
            self.face_lit = lit
        self.poly([proj(*p) for p in pts3], tone, edge, w)
        self.T, self.face_lit = T, fl

    def ln3(self, pts3, v=130, w=1.0):
        T, self.T = self.T, None
        self.ln([proj(*p) for p in pts3], v, w)
        self.T = T

    def W(self, p, jit=0.0, closed=False):
        """Hand-drawn points: subdivide every segment to <= 7 px and displace by a smooth field (so fills, glass and
        lines stay registered), plus an optional per-stroke random walk (`jit` px) for free lines."""
        if len(p) < 2 or self.wob <= 0:
            return p
        pts = list(p) + ([p[0]] if closed else [])
        out = []
        for i in range(len(pts) - 1):
            (x0, y0), (x1, y1) = pts[i], pts[i + 1]
            n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 7))
            for k in range(n):
                t = k / n
                out.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
        if not closed:
            out.append(pts[-1])
        a = self.wob
        res = []
        walk_x = walk_y = 0.0
        for x, y in out:
            dx = a * (0.6 * math.sin(x * 0.011 + y * 0.004 + 1.3) + 0.4 * math.sin(x * 0.033 - y * 0.017 + 4.1))
            dy = a * (0.6 * math.sin(y * 0.012 - x * 0.005 + 2.2) + 0.4 * math.sin(y * 0.029 + x * 0.019 + 0.7))
            if jit:
                walk_x = walk_x * 0.8 + self.jr.normal(0, jit * 0.45)
                walk_y = walk_y * 0.8 + self.jr.normal(0, jit * 0.45)
            res.append((x + dx + walk_x, y + dy + walk_y))
        return res

    # primitives
    def poly(self, ps, tone, edge=150, w=1.0):
        dv = self._dir(ps)
        p = self.W(self.b.pts(self.tp(ps)), closed=True)
        self.ddir.polygon(p, fill=dv)
        self.dco.polygon(p, fill=0)
        self.dlit.polygon(p, fill=int(self.face_lit))
        self.ds.polygon(p, fill=255)
        self.dt.polygon(p, fill=int(tone))
        self.dl.polygon(p, fill=0)
        self.dgm.polygon(p, fill=0)
        self.dd.polygon(p, fill=255 if self.back else 0)
        if edge:
            self.dl.line(p + [p[0]], fill=int(edge), width=max(1, int(round(self.lw * w))), joint="curve")
        self.last = p

    def rect(self, x0, y0, x1, y1, tone, edge=150, w=1.0):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], tone, edge, w)

    def ln(self, ps, v=130, w=1.0):
        self.dl.line(self.W(self.b.pts(self.tp(ps)), jit=0.5), fill=int(v), width=max(1, int(round(self.lw * w))), joint="curve")

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
        p = self.W(self.b.pts(self.tp(ps)), closed=True)
        self.dco.polygon(p, fill=0)
        self.dg.polygon(p, fill=tuple(int(c) for c in rgb))
        self.dgm.polygon(p, fill=255)
        self.ds.polygon(p, fill=255)
        self.dl.polygon(p, fill=0)
        if which == "rose":
            self.dgr.polygon(p, fill=255)
        elif which in ("lancet", "door"):
            self.dgl.polygon(p, fill=255)

    def lead(self, ps, w=1.0, v=20):
        """Stone tracery / lead bars over glass: drawn dark into the glass and erasing the glass mask."""
        p = self.W(self.b.pts(self.tp(ps)))
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


# The cathedral is built in 3D and drawn in perspective (lovish Oct 21, 22:33: "less AI-looking, natural, asymmetric",
# "give the castle an ability to be 3D ... so it pops out and comes to life"): seen from a little to the left, so the
# near (left) tower is bigger, the far (right) tower foreshortened, and the long north flank with its aisle, buttress
# piers, flyers and transept recedes to the left; it stands off-centre on the right third, on a rise.
# World coordinates: X across the facade (0 = its centre), Y = height (y down, like the artboard; the foot at FOOT),
# Z = depth into the building (0 = the facade plane). proj() maps them to artboard A units.
CAM_YAW = math.radians(31)
CAM_D = 2.3                 # camera distance (perspective strength)
CAM_OX = 0.30               # artboard x of the facade centre
CAM_EYE_S = 0.615           # artboard y of the eye level (the horizon)
CAM_EYE_W = 0.665           # world height of the eye
CAM_SC = 0.90
FOOT = 0.745


def proj(X, Y, Z):
    c, s = math.cos(CAM_YAW), math.sin(CAM_YAW)
    xc = X * c - Z * s
    zc = X * s + Z * c + CAM_D
    k = CAM_D / zc * CAM_SC
    return (CAM_OX + xc * k, CAM_EYE_S + (Y - CAM_EYE_W) * k)


# Depth layers, back to front (each painted whole, so parallax never opens a hole; the rect is found from the art and
# written into LobbyBackdrop.java between the gen markers): name, parallax z (A per unit of camera offset), haze.
CATH_LAYERS = [
    ("cath_back", 0.013, 0.55),
    ("cath_nave", 0.017, 0.36),
    ("cath_aisle", 0.022, 0.20),
    ("cath_tfar", 0.025, 0.12),
    ("cath_facade", 0.029, 0.05),
    ("cath_gable", 0.032, 0.03),
    ("cath_tnear", 0.035, 0.0),
    ("cath_portal", 0.040, 0.0),
]
CATH_RECT = (-0.30, -0.02, 0.58, 0.80)      # the drawing board (artboard A units)
CATH_PX = 700 if LAYOUT else 1900                               # px per A in the textures
CATH_SIZE = (int((CATH_RECT[2] - CATH_RECT[0]) * CATH_PX), int((CATH_RECT[3] - CATH_RECT[1]) * CATH_PX))
CATH_BOXES = {}                              # name -> rect in A, filled by ink_stone
# bays of the north flank (pier centres along Z); the transept sits between the 5th and 6th
PIERS = (0.035, 0.158, 0.272, 0.392, 0.512, 0.665, 0.778, 0.884, 0.985)
TRANSEPT = (0.528, 0.650)


def tracery(g, cx, ys, hw, bottom, tone=30, edge=150):
    """Stone tracery in a dark lancet: a mullion, two sub-arches and an oculus with a quatrefoil, as lit stone bars."""
    t = hw * 0.16
    g.rect(cx - t, ys - hw * 0.2, cx + t, bottom, tone, edge, 0.6)
    for s in (-1, 1):
        sc = cx + s * hw * 0.5
        g.ln([(sc - hw * 0.5, ys + hw * 0.9)] + arch_pts(sc, ys + hw * 0.9, hw * 0.5, 1.0, 8) + [(sc + hw * 0.5, ys + hw * 0.9)],
             edge, 0.9)
    oy = ys - hw * 0.45
    g.ring(cx, oy, hw * 0.42, edge, 0.8)
    for ang in (0, 90, 180, 270):
        a_ = math.radians(ang)
        g.ring(cx + hw * 0.2 * math.cos(a_), oy + hw * 0.2 * math.sin(a_), hw * 0.16, edge * 0.9, 0.5)


def voussoirs(g, cx, ys, hw, bottom, n, edge):
    """Radial joints round an arch (its voussoirs) and little carved figures on the lowest stones."""
    pts = arch_pts(cx, ys, hw, 1.0, n)
    for i in range(1, len(pts) - 1, 2):
        x, y = pts[i]
        dx, dy = x - cx, y - ys
        L = math.hypot(dx, dy) + 1e-9
        g.ln([(x, y), (x - dx / L * hw * 0.12, y - dy / L * hw * 0.12)], edge, 0.5)


def box(g, x0, x1, z0, z1, y0, y1, tf, ts, E, course=True, lit_side=165):
    """A stone block seen from the front-left: its left face (X = x0, catching the moon) and its front face (Z = z0)."""
    g.side(x0, lit_side)
    g.rect(z0, y0, z1, y1, ts, E)
    if course and (y1 - y0) > 0.02:
        g.courses(z0, y0, z1, y1)
    g.front(z0, 0)
    g.rect(x0, y0, x1, y1, tf, E)
    if course and (y1 - y0) > 0.02:
        g.courses(x0, y0, x1, y1)


def spire3(g, x0, x1, z0, z1, base, tip, tone, E, rr, crock=True, broken=None, cross=True):
    """An octagon-ish spire as a pyramid in 3D (front and left faces), with crockets, bands, lucarnes and a finial.
    `broken` = the world height where the taper snapped off."""
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    top = tip if broken is None else broken
    k = (base - top) / (base - tip)
    ax = lambda x: x + (cx - x) * k
    az = lambda z: z + (cz - z) * k
    fl = [(x0, base, z0), (x1, base, z0)]
    sl = [(x0, base, z1), (x0, base, z0)]
    if broken is None:
        g.poly3(sl + [(cx, tip, cz)], tone + 8, E, lit=175)
        g.poly3(fl + [(cx, tip, cz)], tone, E, lit=0)
    else:
        jag = lambda a, b: [(a[0] + (b[0] - a[0]) * t, top + rr.uniform(-0.012, 0.006), a[2] + (b[2] - a[2]) * t)
                            for t in (0.2, 0.45, 0.7, 0.85)]
        a_s0, a_s1 = (ax(x0), top, az(z1)), (ax(x0), top + 0.004, az(z0))
        g.poly3(sl + [a_s1] + jag(a_s1, a_s0)[::-1] + [a_s0], tone + 8, E, lit=175)
        a_f0, a_f1 = (ax(x0), top + 0.004, az(z0)), (ax(x1), top - 0.01, az(z0))
        g.poly3(fl + [a_f1] + jag(a_f0, a_f1)[::-1] + [a_f0], tone, E, lit=0)
        g.ln3([(cx, top, cz), (cx + 0.003, top - 0.03, cz), (cx + 0.009, top - 0.036, cz)], E, 0.6)   # the iron rod
    # facet lines, bands and lucarnes
    h = base - top
    for f in (0.33, 0.66):
        g.ln3([(x0 + (x1 - x0) * f, base, z0), (ax(x0 + (x1 - x0) * f), top, az(z0))], E * 0.55, 0.7)
        g.ln3([(x0, base, z0 + (z1 - z0) * f), (ax(x0), top, az(z0 + (z1 - z0) * f))], E * 0.6, 0.7)
    for i in range(1, 6):
        y = base - (base - tip) * i / 7
        if y < top:
            break
        kk = (base - y) / (base - tip)
        xa, xb, za, zb = x0 + (cx - x0) * kk, x1 + (cx - x1) * kk, z0 + (cz - z0) * kk, z1 + (cz - z1) * kk
        g.ln3([(xa, y, zb), (xa, y, za), (xb, y, za)], E * 0.6, 0.7)
    lb = base - (base - tip) * 0.15
    kk = 0.15
    hw_ = (x1 - x0) * 0.16
    g.front(z0 + (cz - z0) * kk)
    g.gablet(cx, lb, hw_, (base - tip) * 0.09, tone + 8, E * 0.85)
    g.side(x0 + (cx - x0) * kk, 175)
    g.gablet(cz, lb, hw_ * 0.9, (base - tip) * 0.09, tone + 14, E * 0.85)
    g.flat()
    if crock:
        n = max(4, int(h / 0.011))
        A = [proj(x0, base, z0), proj(ax(x0), top, az(z0))]
        B = [proj(x1, base, z0), proj(ax(x1), top, az(z0))]
        C = [proj(x0, base, z1), proj(ax(x0), top, az(z1))]
        size = (x1 - x0) * 0.16
        g.crockets(A[0], A[1], n, size, tone, E * 0.85, -1)
        g.crockets(B[0], B[1], n, size * 0.9, tone, E * 0.85, 1)
        g.crockets(C[0], C[1], n, size * 0.7, tone, E * 0.7, -1)
    if broken is None:
        g.front(cz)
        g.finial(cx, tip + (base - tip) * 0.01, max((base - tip) * 0.1, 0.012), tone, E, cross)
    g.flat()


def pinnacle3(g, x, z, base, top, tip, hw, tone, E, cross=False, snapped=None):
    """A buttress pinnacle seen from the front-left: a shaft (left + front faces) and a front-facing spirelet."""
    box(g, x - hw, x + hw, z - hw, z + hw, top, base, tone, tone + 8, E, course=False, lit_side=170)
    g.front(z - hw)
    g.ln([(x - hw * 0.5, base - (base - top) * 0.05), (x - hw * 0.5, top + hw * 1.5)], E * 0.6, 0.6)
    g.ln([(x + hw * 0.5, base - (base - top) * 0.05), (x + hw * 0.5, top + hw * 1.5)], E * 0.6, 0.6)
    g.gablet(x, top, hw * 1.15, hw * 2.0, tone, E, crock=hw > 0.006)
    if snapped is None:
        g.spire(x, top - hw * 0.4, tip, hw * 0.75, tone, E, crock=True, bands=2, cross=cross)
    else:
        g.poly([(x - hw * 0.75, top - hw * 0.4), (x - hw * 0.5, snapped), (x - hw * 0.1, snapped - 0.004),
                (x + hw * 0.3, snapped + 0.003), (x + hw * 0.75, top - hw * 0.4)], tone, E)
    g.flat()


def cathedral():
    W_, H_ = CATH_SIZE
    b = Board(CATH_RECT, W_, H_, ss=2)
    G = {nm: Gothic(b) for nm, *_ in CATH_LAYERS}
    rr = np.random.default_rng(601)
    E = 175      # edge brightness of the near stone
    F = FOOT

    # ------------------------------------------------------------ back: the chevet (apse, radiating chapels, pinnacles)
    g = G["cath_back"]
    g.back = True
    ZA, RA = 1.0, 0.088
    n = 7
    ring = [(RA * math.cos(math.pi - math.pi * i / (n - 1)), ZA + RA * math.sin(math.pi * i / (n - 1))) for i in range(n)]
    ring2 = [(0.17 * math.cos(math.pi - math.pi * i / (n - 1)), ZA + 0.15 * math.sin(math.pi * i / (n - 1))) for i in range(n)]
    for (xa, za), (xb, zb) in list(zip(ring2, ring2[1:]))[::-1]:          # radiating chapels, low, conical roofs
        g.poly3([(xa, 0.58, za), (xb, 0.58, zb), (xb, F, zb), (xa, F, za)], 24, 120, lit=120)
        g.poly3([(xa, 0.58, za), ((xa + xb) / 2, 0.53, (za + zb) / 2), (xb, 0.58, zb)], 20, 120)
    for (xa, za), (xb, zb) in list(zip(ring, ring[1:]))[::-1]:            # the apse wall and its tall windows
        g.poly3([(xa, 0.31, za), (xb, 0.31, zb), (xb, F, zb), (xa, F, za)], 28, 130, lit=130)
        g.plane(xa, za, xb - xa, zb - za, 130)
        g.dark(lancet_pts(0.5, 0.36, 0.17, 0.50), 6, 130)
        g.glass_poly(lancet_pts(0.5, 0.365, 0.11, 0.497), GLASS_DEEP, "lancet")
        g.flat()
    for i, (x, z) in enumerate(ring):                                      # pinnacles round the chevet, all different
        tip = 0.22 - 0.035 * ((i * 5) % 3) - (0.03 if i == 3 else 0)
        pinnacle3(g, x - 0.012, z, 0.40, 0.29 + 0.012 * (i % 2), tip, 0.0085, 24, 130, snapped=(0.27 if i == 5 else None))
    # the apse roof: a half cone
    for (xa, za), (xb, zb) in zip(ring, ring[1:]):
        g.poly3([(xa, 0.31, za), (xb, 0.31, zb), (0.0, 0.21, ZA)], 18, 110)
    g.back = False

    # ------------------------------------------------------------ the nave: clerestory, roof, crossing tower, flèche
    g = G["cath_nave"]
    box(g, -0.088, 0.088, 0.02, ZA, 0.30, F, 34, 42, 150)
    g.side(-0.088, 160)
    bays = [((PIERS[i] + PIERS[i + 1]) / 2, PIERS[i + 1] - PIERS[i]) for i in range(len(PIERS) - 1)]
    for zc, bw in bays:
        if TRANSEPT[0] < zc < TRANSEPT[1]:
            continue
        hw = bw * 0.26
        g.dark(lancet_pts(zc, 0.325, hw, 0.43), 5, 160)
        g.glass_poly(lancet_pts(zc, 0.33, hw * 0.7, 0.426), GLASS_DEEP, "lancet")
        g.lead([(zc, 0.32), (zc, 0.426)], 0.7)
        g.trefoil_lead(zc, 0.325, hw * 0.22, 0.5)
    g.corbel_table(0.02, ZA, 0.305, 40, 40, 150)
    g.flat()
    # the roof's north slope with lead rolls and a cresting of little finials along the ridge
    g.poly3([(-0.093, 0.30, 0.0), (0.0, 0.205, 0.0), (0.0, 0.205, ZA), (-0.093, 0.30, ZA)], 20, 140, lit=200)
    zz = 0.03
    while zz < ZA:
        g.ln3([(-0.091, 0.299, zz), (-0.002, 0.207, zz)], 90, 0.6)
        zz += 0.022 + 0.004 * math.sin(zz * 40)
    zz = 0.02
    while zz < ZA:
        g.front(zz)
        g.finial(0.0, 0.205, 0.016 + 0.004 * math.sin(zz * 31), 22, 140)
        zz += 0.05
    g.flat()
    # the crossing tower: an open belfry stage, then a slender lead flèche
    cz0, cz1 = TRANSEPT
    box(g, -0.07, 0.07, cz0 + 0.005, cz1 - 0.005, 0.12, 0.25, 36, 44, 160)
    for face, lit in (("side", 170), ("front", 0)):
        if face == "side":
            g.side(-0.07, lit)
            us = (cz0 + 0.03, cz1 - 0.03)
        else:
            g.front(cz0 + 0.005, lit)
            us = (-0.035, 0.035)
        for u in us:
            g.dark(lancet_pts(u, 0.15, 0.012, 0.235), 4, 170)
            tracery(g, u, 0.15, 0.012, 0.235, 30, 160)
        g.corbel_table(us[0] - 0.03, us[1] + 0.03, 0.125, 8, 40, 150)
    g.flat()
    spire3(g, -0.034, 0.034, cz0 + 0.035, cz1 - 0.035, 0.125, 0.0, 30, 160, rr)
    for (x, z) in ((-0.07, cz0 + 0.005), (0.07, cz0 + 0.005), (-0.07, cz1 - 0.005)):
        pinnacle3(g, x, z, 0.25, 0.13, 0.065 + rr.uniform(-0.01, 0.01), 0.0075, 34, 160)

    # ------------------------------------------------------------ the north aisle, transept, buttress piers and flyers
    g = G["cath_aisle"]
    XA, XP = -0.205, -0.226          # aisle wall, pier faces
    def aisle_bay(z0, z1):
        box(g, XA, -0.09, z0, z1, 0.50, F, 34, 44, 160)
        g.side(XA, 175)
        zc, hw = (z0 + z1) / 2, (z1 - z0) * 0.27
        g.dark(lancet_pts(zc, 0.55, hw, 0.69), 5, 165)
        g.glass_poly(lancet_pts(zc, 0.555, hw * 0.74, 0.686), GLASS_DEEP, "lancet")
        g.lead([(zc, 0.545), (zc, 0.686)], 0.7)
        for yy in (0.595, 0.64):
            g.lead([(zc - hw * 0.72, yy), (zc + hw * 0.72, yy)], 0.6)
        g.trefoil_lead(zc, 0.55, hw * 0.22, 0.5)
        g.blind_arcade(z0 + 0.004, z1 - 0.004, 0.70, 0.742, 4, 120)
        g.flat()
        g.poly3([(XA - 0.004, 0.50, z0), (-0.09, 0.425, z0), (-0.09, 0.425, z1), (XA - 0.004, 0.50, z1)], 22, 140, lit=190)
    def pier(z, k, broken_fly=False, snapped=None, fallen=False):
        hw = 0.012
        box(g, XP - 0.007, XA, z - hw - 0.003, z + hw + 0.003, 0.60, F, 40, 50, 170)      # stepped base
        box(g, XP, XA, z - hw, z + hw, 0.42, 0.60, 38, 48, 170)
        g.side(XP - 0.007, 180)
        g.poly([(z - hw - 0.003, 0.60), (z + hw + 0.003, 0.60), (z + hw, 0.585), (z - hw, 0.585)], 46, 170, 0.7)
        g.side(XP, 180)
        g.statue_niches(z - hw * 0.8, z + hw * 0.8, 0.465, 0.53, 1, 42, 165)
        g.flat()
        # the flyer, in the pier's own plane, from the pier top to the clerestory
        g.front(z, 0)
        pts_top, pts_bot = [], []
        for i in range(15):
            t = i / 14
            x = XP + 0.006 + (-0.092 - XP - 0.006) * t
            y = 0.425 - 0.075 * math.sin(t * math.pi * 0.5) - 0.01 * t
            pts_top.append((x, y))
            pts_bot.append((x, y + 0.009 + 0.012 * math.sin(t * math.pi)))
        if broken_fly:
            m = 9
            g.poly(pts_top[:m] + [(pts_top[m][0], pts_top[m][1] + 0.004)] + [(pts_bot[m - 1][0] + 0.004, pts_bot[m - 1][1] - 0.002)]
                   + pts_bot[:m - 1][::-1], 34, 165, 0.9)
            g.poly([pts_top[13], pts_top[14], pts_bot[14], pts_bot[13], (pts_top[12][0], pts_top[12][1] + 0.006)], 34, 165, 0.9)
        else:
            g.poly(pts_top + pts_bot[::-1], 34, 165, 0.9)
            mx_, my_ = pts_top[7]
            g.ring(mx_, my_ + 0.0075, 0.003, 150, 0.6)
            g.crockets(pts_top[2], pts_top[13], 7, 0.0035, 34, 150, -1)
        g.gargoyle(XP, 0.43, -1, 0.011, 40, 175)
        g.flat()
        if not fallen:
            pinnacle3(g, XP + 0.012, z, 0.425, 0.345 - 0.012 * k, 0.27 - 0.02 * k, 0.0095, 38, 165, snapped=snapped)
    # far to near: bays beyond the transept, the transept arm, then the nearer bays (painter's order)
    order_far = [i for i in range(len(PIERS) - 1) if PIERS[i] >= TRANSEPT[1] - 0.02][::-1]
    for i in order_far:
        aisle_bay(PIERS[i], PIERS[i + 1])
    for i in range(len(PIERS) - 1, -1, -1):
        if PIERS[i] >= TRANSEPT[1] - 0.02:
            pier(PIERS[i], (i * 3) % 4, broken_fly=(i == 6), snapped=(0.36 if i == 7 else None))
    # the transept's north arm: a gable end with a rose, its own portal, corner turrets
    t0, t1 = TRANSEPT
    XT = -0.32
    box(g, XT, -0.09, t0, t1, 0.30, F, 34, 46, 165, lit_side=185)
    g.side(XT, 185)
    tc = (t0 + t1) / 2
    g.gablet(tc, 0.30, (t1 - t0) / 2, 0.085, 46, 170)
    g.rect(t0, 0.295, t1, 0.305, 52, 170, 0.8)
    rose(g, tc, 0.40, 0.036)
    g.ring(tc, 0.40, 0.042, 190, 0.9)
    g.blind_arcade(t0 + 0.006, t1 - 0.006, 0.47, 0.56, 6, 140)
    for i, (hw, tone) in enumerate(((0.028, 40), (0.023, 30), (0.018, 20))):
        g.poly(lancet_pts(tc, 0.635 + i * 0.003, hw, F), tone, 165 - i * 10, 0.9)
    g.dark(lancet_pts(tc, 0.66, 0.0135, F), 3, 170)
    g.glass_poly([(tc - 0.011, 0.672), (tc + 0.011, 0.672), (tc + 0.011, F), (tc - 0.011, F)], (110, 16, 18), "door")
    g.flat()
    g.poly3([(XT, 0.30, t0), (XT, 0.215, tc), (-0.09, 0.215, tc), (-0.09, 0.30, t0)], 20, 140, lit=200)   # its roof
    for z in (t0, t1):
        pinnacle3(g, XT - 0.006, z, 0.32, 0.215, 0.15 + (0.02 if z == t1 else 0), 0.009, 38, 170)
    for i in range(len(PIERS) - 1, -1, -1):
        if PIERS[i] < TRANSEPT[1] - 0.02 and i < len(PIERS) - 1 and PIERS[i + 1] <= TRANSEPT[0] + 0.02:
            aisle_bay(PIERS[i], PIERS[i + 1])
    for i in range(len(PIERS) - 1, -1, -1):
        if PIERS[i] <= TRANSEPT[0] + 0.02:
            pier(PIERS[i], (i * 3) % 4, fallen=(i == 2), snapped=(0.33 if i == 3 else None))

    # ------------------------------------------------------------ the far (south-west) tower: never finished, a crane
    g = G["cath_tfar"]
    x0t, x1t = 0.090, 0.172
    cx = (x0t + x1t) / 2
    hw = (x1t - x0t) / 2
    top_pts = [(x0t, 0.262), (x0t + 0.01, 0.255), (x0t + 0.022, 0.262), (cx - 0.004, 0.25), (cx + 0.01, 0.258),
               (x1t - 0.02, 0.248), (x1t - 0.012, 0.232), (x1t - 0.004, 0.226), (x1t, 0.21)]
    g.front(0.0, 0)
    g.poly(top_pts + [(x1t, F), (x0t, F)], 38, E)
    g.courses(x0t, 0.27, x1t, F)
    g.rect(x1t - 0.014, 0.206, x1t, 0.262, 42, E)                     # the stair turret, risen a stage higher
    g.poly([(x1t - 0.014, 0.206), (x1t - 0.007, 0.192), (x1t, 0.206)], 42, E, 0.8)
    for y in (0.300, 0.405, 0.525, 0.645):
        g.rect(x0t - 0.003, y, x1t + 0.003, y + 0.006, 50, E, 0.8)
        g.corbel_table(x0t, x1t, y + 0.0125, 7, 40, 150)
    for k, (ys, ye) in enumerate(((0.28, 0.296), (0.28, 0.296))):
        x = cx + (-0.016 if k == 0 else 0.017)
        g.dark(lancet_pts(x, 0.31, 0.0092, 0.395), 5, 165)
        if k == 0:
            tracery(g, x, 0.31, 0.0092, 0.395, 30, 160)
        else:          # its tracery fell out: an empty, dark-sky opening with one broken bar
            g.ln([(x - 0.003, 0.395), (x - 0.001, 0.36), (x + 0.004, 0.352)], 150, 0.7)
    ry = 0.465
    g.circle(cx, ry, 0.019, 10, 170)
    g.glass_poly([(cx + 0.014 * math.cos(a / 16 * 2 * math.pi), ry + 0.014 * math.sin(a / 16 * 2 * math.pi)) for a in range(16)],
                 GLASS_EMBER, "rose")
    for a in range(5):
        an = a / 5 * math.pi + 0.2
        g.lead([(cx - 0.014 * math.cos(an), ry - 0.014 * math.sin(an)), (cx + 0.014 * math.cos(an), ry + 0.014 * math.sin(an))], 0.7)
    g.ring(cx, ry, 0.019, 190, 0.9)
    for x in (cx - 0.017, cx + 0.015):
        g.dark(lancet_pts(x, 0.545, 0.0078, 0.632), 5, 160)
        g.glass_poly(lancet_pts(x, 0.549, 0.005, 0.629), GLASS_DEEP, "lancet")
        g.lead([(x, 0.545), (x, 0.629)], 0.6)
    g.statue_niches(x0t + 0.004, x1t - 0.004, 0.655, 0.735, 3, 40, 165)
    g.blind_arcade(x0t + 0.003, x1t - 0.003, 0.415, 0.445, 5, 130)
    # the medieval crane left on the unfinished top: a timber frame, a jib, a rope and a hanging stone
    for x in (x0t + 0.014, x0t + 0.04):
        g.poly([(x - 0.0016, 0.258), (x + 0.0016, 0.258), (x + 0.001, 0.205), (x - 0.001, 0.205)], 58, 150, 0.6)
    g.poly([(x0t + 0.012, 0.207), (x0t + 0.044, 0.203), (x0t + 0.044, 0.209), (x0t + 0.012, 0.212)], 58, 150, 0.6)
    g.poly([(x0t + 0.026, 0.205), (x0t - 0.035, 0.168), (x0t - 0.036, 0.172), (x0t + 0.024, 0.211)], 58, 150, 0.6)
    g.ln([(x0t + 0.014, 0.258), (x0t + 0.04, 0.205)], 130, 0.6)
    g.ln([(x0t + 0.04, 0.258), (x0t + 0.014, 0.205)], 130, 0.6)
    g.ln([(x0t - 0.033, 0.171), (x0t - 0.032, 0.232)], 120, 0.5)
    g.rect(x0t - 0.037, 0.232, x0t - 0.027, 0.240, 46, 150, 0.6)
    g.circle(x0t + 0.027, 0.245, 0.010, 30, 150, 0.6)        # the treadwheel
    for a in range(6):
        g.ln([(x0t + 0.027, 0.245), (x0t + 0.027 + 0.010 * math.cos(a), 0.245 + 0.010 * math.sin(a))], 110, 0.4)
    # its buttresses (only the left faces show)
    for x in (x0t, x1t - 0.002):
        box(g, x - 0.007, x + 0.007, -0.022, 0.0, 0.33 if x == x0t else 0.30, F, 44, 52, E)
        g.front(-0.022)
        g.gablet(x, 0.33 if x == x0t else 0.30, 0.008, 0.018, 44, E)
        g.flat()
    pinnacle3(g, x0t, -0.011, 0.33, 0.27, 0.215, 0.0065, 44, E)

    # ------------------------------------------------------------ the central facade: gallery of kings, three lancets
    g = G["cath_facade"]
    g.front(0.0, 0)
    g.rect(-0.092, 0.268, 0.092, F, 36, E)
    g.courses(-0.092, 0.268, 0.092, F)
    for x in (-0.088, -0.072, 0.072, 0.088):
        g.ln([(x + 0.003, 0.27), (x + 0.003, F)], 120, 0.7)
    g.rect(-0.092, 0.402, 0.092, 0.43, 42, E)
    kings = rr.permutation(9)
    for i in range(9):
        x = -0.076 + i * 0.019
        g.dark(lancet_pts(x, 0.411, 0.0065, 0.428), 8, 150, 0.7)
        if kings[i] == 4:          # one king long gone: an empty niche
            continue
        hh = 0.0022 * (0.9 + 0.2 * rr.random())
        g.rect(x - hh, 0.414, x + hh, 0.428, 44, 150, 0.6)
        if kings[i] != 7:          # and one lost his head
            g.circle(x, 0.4115, 0.0024, 44, 150, 0.6)
            g.poly([(x - 0.0028, 0.4105), (x, 0.4075), (x + 0.0028, 0.4105)], 46, 160, 0.4)
    g.poly(lancet_pts(0.0, 0.47, 0.066, 0.60), 26, E)
    voussoirs(g, 0.0, 0.47, 0.066, 0.60, 22, 150)
    g.poly(lancet_pts(0.0, 0.475, 0.060, 0.595), 12, E - 20, 0.9)
    lancets(g)
    g.rect(-0.092, 0.60, 0.092, 0.611, 46, E)
    g.blind_arcade(-0.09, 0.09, 0.612, 0.66, 12, 140)
    g.flat()

    # ------------------------------------------------------------ the gable plane (set a little forward): rose, gable
    g = G["cath_gable"]
    g.front(-0.012, 0)
    g.rect(-0.094, 0.272, 0.094, 0.282, 38, E)
    g.gablet(0.0, 0.274, 0.092, 0.078, 36, E)
    g.corbel_table(-0.092, 0.092, 0.284, 16, 36, E)
    for i, (hw, tone) in enumerate(((0.074, 34), (0.068, 26), (0.062, 18), (0.056, 12))):
        g.front(-0.012 + i * 0.004, 0)
        g.poly(lancet_pts(0.0, 0.338, hw, 0.402), tone, E - i * 12, 1.0)
    g.front(-0.012, 0)
    voussoirs(g, 0.0, 0.338, 0.074, 0.402, 26, 150)
    g.front(0.004, 0)
    rose(g, 0.0, 0.338, 0.050)
    g.front(-0.012, 0)
    g.rect(-0.094, 0.398, 0.094, 0.404, 46, E, 0.8)
    g.flat()
    pinnacle3(g, -0.05, -0.016, 0.282, 0.25, 0.195, 0.0042, 40, 150)
    pinnacle3(g, 0.05, -0.016, 0.282, 0.25, 0.212, 0.0042, 40, 150, snapped=0.236)

    # ------------------------------------------------------------ the near (north-west) tower: whole, with its spire
    g = G["cath_tnear"]
    x0t, x1t, z1t = -0.180, -0.092, 0.088
    cx = (x0t + x1t) / 2
    hw = (x1t - x0t) / 2
    box(g, x0t, x1t, 0.0, z1t, 0.205, F, 40, 50, E, lit_side=185)
    for face in ("side", "front"):
        if face == "side":
            g.side(x0t, 185)
            c, h_ = z1t / 2, z1t / 2
        else:
            g.front(0.0, 0)
            c, h_ = cx, hw
        for x in (c - h_ * 0.55, c + h_ * 0.55):
            g.ln([(x, 0.21), (x, F)], 105, 0.6)
        for y in (0.295, 0.405, 0.525, 0.645):
            g.rect(c - h_ - 0.003, y, c + h_ + 0.003, y + 0.006, 50, E, 0.8)
            g.corbel_table(c - h_, c + h_, y + 0.0125, 8, 40, 150)
        for k in ((-1, 1) if face == "front" else (-1, 1)):
            x = c + k * h_ * 0.4
            g.dark(lancet_pts(x, 0.232, 0.0098, 0.29), 5, 165)
            tracery(g, x, 0.232, 0.0098, 0.29, 30, 160)
            g.dark(lancet_pts(x, 0.335, 0.0103, 0.40), 5, 165)
            g.glass_poly(lancet_pts(x, 0.339, 0.0068, 0.397), GLASS_DEEP, "lancet")
            g.lead([(x, 0.33), (x, 0.397)], 0.7)
            for yy in (0.355, 0.375):
                g.lead([(x - 0.0068, yy), (x + 0.0068, yy)], 0.6)
            g.trefoil_lead(x, 0.339 - 0.005, 0.002, 0.5)
            if face == "front" or k < 0:
                g.dark(lancet_pts(x, 0.545, 0.0082, 0.63), 5, 160)
                tracery(g, x, 0.545, 0.0082, 0.63, 28, 150)
        if face == "front":
            ry = 0.465
            g.circle(c, ry, 0.019, 10, 170)
            g.glass_poly([(c + 0.0138 * math.cos(a / 16 * 2 * math.pi), ry + 0.0138 * math.sin(a / 16 * 2 * math.pi)) for a in range(16)],
                         GLASS_EMBER, "rose")
            for a in range(6):
                an = a / 6 * math.pi
                g.lead([(c - 0.0138 * math.cos(an), ry - 0.0138 * math.sin(an)), (c + 0.0138 * math.cos(an), ry + 0.0138 * math.sin(an))], 0.7)
            g.ring(c, ry, 0.0055, 40, 0.8)
            g.ring(c, ry, 0.019, 190, 0.9)
            g.statue_niches(c - h_ + 0.003, c + h_ - 0.003, 0.655, 0.735, 4, 34, 165)
        else:
            g.statue_niches(c - h_ + 0.006, c + h_ - 0.006, 0.655, 0.735, 2, 42, 170)
        g.blind_arcade(c - h_ + 0.003, c + h_ - 0.003, 0.415, 0.445, 5, 130)
    g.flat()
    spire3(g, x0t + 0.008, x1t - 0.008, 0.008, z1t - 0.008, 0.21, 0.015, 36, E, rr)
    for (x, z) in ((x0t, 0.0), (x1t, 0.0), (x0t, z1t)):
        pinnacle3(g, x, z, 0.215, 0.165 + rr.uniform(-0.006, 0.006), 0.10 + rr.uniform(-0.01, 0.012), 0.0078, 42, E)
    # clasping buttresses up the tower's corners
    for (bx, lit) in ((x0t - 0.004, 190), (x1t - 0.006, 0)):
        box(g, bx - 0.006, bx + 0.008, -0.02, 0.0, 0.33, F, 44, 52, E)
        g.front(-0.02)
        for y in (0.42, 0.55, 0.66):
            g.poly([(bx - 0.006, y), (bx + 0.008, y), (bx + 0.008, y - 0.006)], 50, E, 0.7)
        g.gablet(bx + 0.001, 0.33, 0.008, 0.02, 44, E)
        g.flat()

    # ------------------------------------------------------------ the portal (nearest): archivolts in depth, figures, steps
    g = G["cath_portal"]
    for bx in (-0.092, 0.092):                       # the great buttresses between the towers and the nave
        box(g, bx - 0.008, bx + 0.008, -0.042, 0.0, 0.45, F, 46, 54, E)
        g.front(-0.042)
        for y in (0.52, 0.60, 0.68):
            g.rect(bx - 0.0085, y, bx + 0.0085, y + 0.004, 54, E, 0.7)
        g.flat()
        pinnacle3(g, bx, -0.03, 0.45, 0.33, 0.235 + (0.02 if bx > 0 else 0), 0.0085, 46, E)
    for i, (hw, tone) in enumerate(((0.066, 38), (0.060, 32), (0.053, 26), (0.046, 20), (0.039, 14), (0.032, 9))):
        z = -0.034 + i * 0.0055
        g.front(z, 0)
        g.poly(lancet_pts(0.0, 0.692 - i * 0.003, hw, F), tone, E - i * 10, 0.9)
        voussoirs(g, 0.0, 0.692 - i * 0.003, hw, F, 16 + 2 * i, 160)
        if i < 4:
            for s in (-1, 1):
                x = s * (hw - 0.0035)
                if s > 0 and i == 1:
                    continue                                  # one jamb statue is missing
                g.rect(x - 0.0022, 0.70, x + 0.0022, 0.728, tone + 16, 160, 0.6)
                g.circle(x, 0.6975, 0.0026, tone + 16, 160, 0.6)
                g.poly([(x - 0.003, 0.6955), (x, 0.6915), (x + 0.003, 0.6955)], tone + 10, 150, 0.5)
                g.rect(x - 0.003, 0.728, x + 0.003, 0.732, tone + 20, 150, 0.5)
    g.front(-0.036, 0)
    g.gablet(0.0, 0.662, 0.068, 0.054, 32, E)
    g.front(0.0, 0)
    g.dark(lancet_pts(0.0, 0.708, 0.025, F), 3, 170)
    g.tympanum(0.0, 0.716, 0.682, 0.024, 22, 185)
    g.glass_poly([(-0.021, 0.7165), (0.021, 0.7165), (0.021, F), (-0.021, F)], (140, 18, 20), "door")
    g.rect(-0.0028, 0.716, 0.0028, F, 30, 170, 0.7)
    g.circle(0.0, 0.722, 0.0022, 44, 170, 0.5)
    g.rect(-0.0018, 0.724, 0.0018, 0.738, 44, 160, 0.5)
    for s in (-1, 1):                                  # the porch's flanking piers
        x = s * 0.074
        box(g, x - 0.008, x + 0.008, -0.05, -0.036, 0.64, F, 44, 52, E, course=False)
        g.front(-0.05)
        g.statue_niches(x - 0.008, x + 0.008, 0.655, 0.725, 1, 44, 170)
        g.gablet(x, 0.64, 0.009, 0.02, 44, E)
        g.spire(x, 0.622, 0.585 + (0.012 if s > 0 else 0), 0.004, 44, E, bands=1)
    g.flat()
    # the steps: five worn treads widening down, their left ends and fronts
    for i in range(5):
        z0 = -0.05 - i * 0.013
        z1 = z0 + 0.013
        y0 = F + i * 0.0075
        w0 = 0.082 + i * 0.012
        g.poly3([(-w0, y0, z0), (w0, y0, z0), (w0, y0, z1), (-w0, y0, z1)], 60 - i * 4, 150, lit=60)   # tread (we see its top)
        g.side(-w0, 150)
        g.rect(z0, y0, z1, y0 + 0.0075, 40, 150, 0.8)
        g.front(z0, 0)
        g.rect(-w0, y0, w0, y0 + 0.0075, 30, 160, 0.8)
        for k in range(-7, 8):
            x = k * w0 / 7.5 + rr.uniform(-0.002, 0.002)
            g.ln([(x, y0 + 0.001), (x, y0 + 0.0068)], 80, 0.5)
        g.flat()
    # two statues on plinths before the steps, not a pair: a hooded figure with a sword, and a broken angel
    g.front(-0.125, 0)
    for (sx_, broken) in ((-0.125, False), (0.118, True)):
        g.rect(sx_ - 0.016, 0.70, sx_ + 0.016, F + 0.04, 44, E)
        g.rect(sx_ - 0.019, 0.695, sx_ + 0.019, 0.702, 50, E, 0.8)
        if not broken:
            g.poly([(sx_ - 0.011, 0.695), (sx_ - 0.008, 0.64), (sx_ - 0.006, 0.625), (sx_, 0.615), (sx_ + 0.006, 0.625),
                    (sx_ + 0.008, 0.64), (sx_ + 0.012, 0.695)], 40, E, 0.8)
            g.poly([(sx_ - 0.006, 0.626), (sx_, 0.612), (sx_ + 0.006, 0.626), (sx_ + 0.004, 0.632), (sx_ - 0.004, 0.632)], 20, E, 0.7)
            g.rect(sx_ + 0.0005, 0.636, sx_ + 0.0025, 0.69, 60, 190, 0.6)            # the sword, point down
            g.rect(sx_ - 0.005, 0.64, sx_ + 0.008, 0.6425, 60, 190, 0.5)
            for k in range(4):
                g.ln([(sx_ - 0.006 + k * 0.004, 0.645), (sx_ - 0.008 + k * 0.0055, 0.693)], 90, 0.5)
        else:
            g.poly([(sx_ - 0.012, 0.695), (sx_ - 0.009, 0.655), (sx_ - 0.004, 0.648), (sx_ + 0.003, 0.652), (sx_ + 0.008, 0.646),
                    (sx_ + 0.012, 0.695)], 40, E, 0.8)
            g.poly([(sx_ - 0.004, 0.65), (sx_ - 0.03, 0.618), (sx_ - 0.024, 0.64), (sx_ - 0.034, 0.636), (sx_ - 0.012, 0.66)], 36, E, 0.7)
            for k in range(4):
                g.ln([(sx_ - 0.006 + k * 0.004, 0.66), (sx_ - 0.009 + k * 0.006, 0.693)], 90, 0.5)
    g.flat()

    ivy = {"cath_tnear": [(proj(x0t, F, 0.02), 0.30, 0.05), (proj(x0t, F, 0.07), 0.22, 0.03)],
           "cath_aisle": [(proj(XP, F, 0.10), 0.20, 0.06), (proj(XA, F, 0.22), 0.13, 0.05), (proj(XA, F, 0.34), 0.08, 0.03)],
           "cath_portal": [(proj(-0.10, F, -0.04), 0.12, 0.02)]}
    for li, (nm, z, haze) in enumerate(CATH_LAYERS):
        iv = ivy_overlay(b, rr, [(p[0], p[1], up, sp) for p, up, sp in ivy[nm]]) if nm in ivy else None
        ink_stone(b, G[nm], nm, haze, ivy=iv, seed=700 + li)
        G[nm] = None


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


INK = (9, 6, 9)


def _crop_box(rect, full_rect, w, h):
    x0, y0, x1, y1 = full_rect
    sx, sy = w / (x1 - x0), h / (y1 - y0)
    return (int(round((rect[0] - x0) * sx)), int(round((rect[1] - y0) * sy)),
            int(round((rect[2] - x0) * sx)), int(round((rect[3] - y0) * sy)))


def hatch_fields(xs, ys, period, seed):
    """Distance-to-line fields for 6 hatch directions (0..1, 0 on a line), each wobbling like a hand's line."""
    H, W = xs.shape
    U, V = xs / W, ys / H
    out = []
    for k in range(6):
        phi = k * math.pi / 6                       # the line direction
        nx_, ny_ = -math.sin(phi), math.cos(phi)    # its normal
        wob = (fbm(U, V, 7, seed + k, False, 2) - 0.5) * 0.9 + (fbm(U, V, 40, seed + 20 + k, False, 1) - 0.5) * 0.25
        ph = (xs * nx_ + ys * ny_) / period + wob + 0.37 * k
        out.append(np.abs((ph % 1.0) - 0.5) * 2)
    return out


def _dir_weights(theta):
    """Weights of the 6 hatch directions for a field of line angles (axial), normalised."""
    ws = [np.maximum(np.cos(2 * (theta - k * math.pi / 6)), 0) ** 6 for k in range(6)]
    tot = sum(ws) + 1e-6
    return [w_ / tot for w_ in ws]




def ink_stone(b, g, name, haze, ivy=None, seed=0):
    """An etched drawing of one cathedral depth layer: black stone worked in light lines (scratchboard / etching), with
    coloured pencil light, cropped to its art (the rect, in A, goes to CATH_BOXES and LobbyBackdrop.java):
    - one light logic: the moon behind and to the left (a silver rim on every edge facing it, grazing silver on the
      north faces that turn toward it), the red sky (crimson on the edges facing the far side and low down), the glass
      (amber spill on the mouldings round each window), occlusion in every recess, the shadow side of each moulding;
    - the light is laid as hatching that follows the form: along each plane's receding horizontals on the north faces,
      vertical on the west front, along the edges of spires and gables (structure tensor), dense where lit, sparse in
      shadow, cross-hatched where the light is strongest; every line wobbles and breaks like a pen stroke;
    - stone coursing drawn in each wall's own plane, weathering streaks, cracks; contour lines of varied weight;
    - atmospheric depth: `haze` greys, blues and softens the farther layers.
    Saves <name>.png, <name>_rim.png (the moon rim alone, additive) and <name>_glow.png (its glass)."""
    from scipy import ndimage
    sil_full = np.asarray(g.sil)
    if not sil_full.any():
        return
    ss = b.ss
    ys0, xs0 = np.nonzero(sil_full > 8)
    pad = 24
    r0 = max(0, (ys0.min() - pad) // (ss * 6) * ss * 6)
    r1 = min(b.H, -(-(ys0.max() + pad) // (ss * 6)) * ss * 6)
    c0 = max(0, (xs0.min() - pad) // (ss * 6) * ss * 6)
    c1 = min(b.W, -(-(xs0.max() + pad) // (ss * 6)) * ss * 6)
    rect = (b.x0 + c0 / b.sx, b.y0 + r0 / b.sy, b.x0 + c1 / b.sx, b.y0 + r1 / b.sy)
    CATH_BOXES[name] = rect
    out_w, out_h = (c1 - c0) // ss, (r1 - r0) // ss
    crop = lambda im: arr(im)[r0:r1, c0:c1]
    sil, tone, line, gm, course = crop(g.sil), crop(g.tone), crop(g.line), crop(g.gmask), crop(g.course)
    lit = crop(g.lit)
    dirm = np.asarray(g.dirm, np.float32)[r0:r1, c0:c1]
    glass = np.asarray(g.glass, np.float32)[r0:r1, c0:c1] / 255
    ys_, xs_ = np.mgrid[r0:r1, c0:c1].astype(np.float32)
    X, Y = xs_ / b.sx + b.x0, ys_ / b.sy + b.y0
    Hh_, Ww_ = X.shape
    if LAYOUT:
        v = np.clip(tone * 255 / 46, 0, 1) * 0.35 + line * 0.6 + lit * 0.15
        rgb = np.stack([v, v, v * 1.1], -1)
        rgb = lerp(rgb, glass, gm)
        if haze:
            rgb = lerp(rgb, col((70, 60, 80)), haze * 0.5)
        save_rgba(rgb, sil, name, (out_w, out_h))
        return
    tnn = np.clip(tone * 255 / 46, 0, 1.1)
    mx, my, mr = MOON
    rr = np.random.default_rng(seed)
    U01 = (X - b.x0) / (b.x1 - b.x0)
    V01 = (Y - b.y0) / (b.y1 - b.y0)
    gl = np.clip(sil - gm, 0, 1)                              # stone

    # --- the relief and its normals
    hgt = blur(tnn * sil, ss * 1.2) * 0.45 + blur(tnn * sil, ss * 4) * 0.35 + blur(sil, ss * 9) * 0.5
    gy, gx = np.gradient(hgt)
    k_ = 14.0
    nx, ny = -gx * k_, -gy * k_
    nl = np.sqrt(nx * nx + ny * ny + 1)
    nx, ny, nz = nx / nl, ny / nl, 1 / nl
    L1 = np.array((-0.55, -0.55, 0.62), np.float32)
    L1 /= np.linalg.norm(L1)
    diffuse = np.clip(nx * L1[0] + ny * L1[1] + nz * L1[2], 0, 1)
    del gx, gy, nl

    # --- the moon behind: a rim on edges facing it (silhouette and inner edges)
    def normals(field, sigma):
        f = blur(field, sigma)
        gy_, gx_ = np.gradient(f)
        mag = np.hypot(gx_, gy_) + 1e-6
        return -gx_ / mag, -gy_ / mag, mag
    snx, sny, emag = normals(sil, ss * 1.2)
    edge_sil = np.clip(emag * ss * 4.0, 0, 1)
    tx_, ty_, tmag = normals(tnn, ss * 1.0)
    edge_in = np.clip(tmag * ss * 3.0, 0, 1) * smoothstep(0.15, 0.5, tnn)
    dmx, dmy = mx - X, my - Y
    dl = np.hypot(dmx, dmy) + 1e-6
    dmx, dmy = dmx / dl, dmy / dl
    near_moon = 1 - smoothstep(mr * 0.9, mr * 5.0, dl)
    high = 1 - smoothstep(0.12, 0.55, Y)                    # spires and pinnacles stand up into the moonlight
    rim = np.clip(snx * dmx + sny * dmy, 0, 1) ** 0.6 * edge_sil * (0.45 + 0.55 * near_moon) * (0.7 + 0.6 * high)
    rim_in = np.clip(tx_ * dmx + ty_ * dmy, 0, 1) * edge_in * (0.30 + 0.70 * near_moon) * (0.55 + 0.45 * high)
    # the red sky behind the far side: edges facing right and down pick up crimson
    red = (np.clip(snx * 0.8 + sny * 0.3, 0, 1) * edge_sil + np.clip(tx_ * 0.8 + ty_ * 0.2, 0, 1) * edge_in * 0.6)
    red = np.clip(red, 0, 1) * (0.45 + 0.55 * smoothstep(0.25, 0.70, Y))
    del snx, sny, tx_, ty_, tmag

    # --- the glass: its spill on the stone round it
    glum = glass.max(-1) * gm
    spill = np.clip(blur(glum, ss * 5) * 1.5 + blur(glum, ss * 20) * 1.1, 0, 1) * (1 - gm)

    # --- occlusion and drop shadows under ledges (the glass light comes from above-front)
    recess = (1 - smoothstep(0.10, 0.35, tnn)) * sil * (1 - gm)
    ao = blur(recess, ss * 3.0)
    shift = max(1, int(b.sy * 0.003))
    above = np.roll(tnn, shift, axis=0)
    above[:shift] = 0
    drop = blur(np.clip(above - tnn, 0, 1), ss * 2.0)
    occl = np.clip(1 - ao * 0.55 - drop * 0.8, 0.1, 1)
    del recess, ao, above

    # --- the light value
    big = fbm(U01, V01, 4, seed + 1, False, 3)
    V = (0.04 + 0.16 * np.clip(tnn, 0, 1) + 0.30 * (diffuse - 0.45) + 0.30 * lit
         + 0.06 * (big - 0.5) + 0.10 * near_moon * (0.4 + lit)) * occl
    V = V + rim * 0.55 + rim_in * 0.35 + spill * 0.8 + red * 0.18 + 0.14 * (lit - 0.4)
    V = np.where(tnn < 0.14, V * 0.3, V)
    V = np.clip(V, 0, 1)

    # --- the hatching direction: the plane's receding horizontal on the north faces, vertical on the west faces,
    # along the relief where it has a clear edge direction (spires, gables, flyers)
    hb = blur(hgt, ss * 1.5)
    gy2, gx2 = np.gradient(hb)
    jxx, jxy, jyy = blur(gx2 * gx2, ss * 4), blur(gx2 * gy2, ss * 4), blur(gy2 * gy2, ss * 4)
    theta_t = 0.5 * np.arctan2(2 * jxy, jxx - jyy) + math.pi / 2
    coh = np.sqrt((jxx - jyy) ** 2 + 4 * jxy ** 2) / (jxx + jyy + 1e-9)
    kk = smoothstep(0.45, 0.85, coh) * smoothstep(1e-7, 1e-5, jxx + jyy)
    del jxx, jxy, jyy, gx2, gy2, hb, coh
    pa = np.where(dirm > 0, (dirm - 1) / 254 * math.pi, math.pi / 2)
    side_face = smoothstep(0.3, 0.6, lit)
    theta_p = np.where(side_face > 0.5, pa, math.pi / 2)
    kk = kk * (1 - side_face * 0.8)
    vx = np.cos(2 * theta_t) * kk + np.cos(2 * theta_p) * (1 - kk)
    vy = np.sin(2 * theta_t) * kk + np.sin(2 * theta_p) * (1 - kk)
    theta = 0.5 * np.arctan2(vy, vx)
    del vx, vy, theta_t, theta_p, pa, kk

    yl, xl = np.mgrid[0:Hh_, 0:Ww_].astype(np.float32)
    period = 8.0 * ss                 # about 5 px on a 1080p screen, 6-7 at 1440p: read as pen lines, not grain
    D = hatch_fields(xl, yl, period, seed * 10 + 3)
    Dk = hatch_fields(xl, yl, 6.5 * ss, seed * 10 + 4)     # the ink hatching in the shadows, its own lines
    del xl, yl
    aa = 1.0 / period
    tooth = fbm(U01, V01, 260, seed + 4, False, 2)
    brk = smoothstep(0.30, 0.44, fbm(U01, V01, 110, seed + 5, False, 2))       # pen lifts
    press = 0.75 + 0.5 * fbm(U01, V01, 80, seed + 6, False, 2)
    lightl = np.zeros_like(V)
    # light lines: a first direction from a low value, a cross direction where it is lit, a third in the highlights
    for off, v_lo, v_hi, wmax in ((0.0, 0.20, 0.85, 0.55), (math.pi / 3, 0.48, 0.95, 0.45), (-math.pi / 3, 0.70, 1.0, 0.4)):
        wf = np.clip((V - v_lo) / (v_hi - v_lo), 0, 1) * wmax * press
        ws = _dir_weights(theta + off)
        cov = np.zeros_like(V)
        for k in range(6):
            cov += ws[k] * np.clip((wf - D[k]) / aa + 0.5, 0, 1)
        cov *= np.where(wf > 0.3, 1.0, brk)
        lightl = 1 - (1 - lightl) * (1 - cov)
    del D, cov, ws
    lightl *= (0.70 + 0.30 * smoothstep(0.35, 0.6, tooth))
    # dark ink hatching that follows the same form directions where the stone turns from the light: one direction in
    # the half shadow, a cross direction deeper in, a third in the recesses
    aak = 1.0 / (6.5 * ss)
    inkl = np.zeros_like(V)
    shade = (1 - diffuse) * 0.6 + (1 - occl) * 0.8 + (1 - smoothstep(0.05, 0.35, V)) * 0.5
    for off, s_lo, s_hi, wmax in ((0.0, 0.35, 0.85, 0.42), (math.pi / 2.6, 0.55, 1.0, 0.36), (-math.pi / 4, 0.75, 1.2, 0.3)):
        wf = np.clip((shade - s_lo) / (s_hi - s_lo), 0, 1) * wmax * press
        ws = _dir_weights(theta + off)
        cov = np.zeros_like(V)
        for k in range(6):
            cov += ws[k] * np.clip((wf - Dk[k]) / aak + 0.5, 0, 1)
        inkl = 1 - (1 - inkl) * (1 - cov * brk)
    del Dk, cov, ws

    # --- the colour of the light at each point (silver moon, crimson sky, amber glass, cool stone)
    tint = np.broadcast_to(col((120, 118, 136)), V.shape + (3,)).copy()
    tint = lerp(tint, col((206, 214, 236)), np.clip(rim * 1.3 + rim_in + lit * near_moon * 0.6, 0, 1)[..., None])
    tint = lerp(tint, col((196, 56, 50)), np.clip(red * 1.2, 0, 1)[..., None] * (1 - spill[..., None]))
    tint = lerp(tint, col((246, 160, 76)), np.clip(spill * 1.2, 0, 1)[..., None])
    # --- the stone: near-black ground, a thin coloured-pencil fill where it is lit, the light lines over it
    base = lerp(col((14, 12, 17)), col((34, 30, 40)), np.clip(tnn * 0.5 + diffuse * 0.5, 0, 1)[..., None] * occl[..., None])
    pencil = (0.5 + 0.5 * smoothstep(0.35, 0.6, tooth)) * np.clip(V * 0.55, 0, 1)
    rgb = lerp(base, tint * 0.55, pencil[..., None])
    rgb = lerp(rgb, col((4, 3, 6)), (inkl * 0.95 * gl)[..., None])
    rgb = lerp(rgb, tint, (lightl * 0.85)[..., None])

    # stone coursing (drawn in each plane): dark joints where lit, a faint light lip under each where it is dark
    cj = np.clip(course, 0, 1) * gl * (0.55 + 0.45 * smoothstep(0.25, 0.6, fbm(U01, V01, 60, seed + 8, False, 2)))
    rgb = lerp(rgb, col((4, 3, 6)), (cj * 0.8)[..., None])
    lip = np.clip(np.roll(cj, ss, axis=0) - cj, 0, 1)
    rgb = lerp(rgb, tint * 0.9, (lip * (0.25 + 0.6 * np.clip(V * 1.5, 0, 1)))[..., None])
    # each block its own tone (worn, replaced, stained): a coarse cell noise broken by the joints
    blk = fbm(U01 * 3.0, V01 * 9.0, 40, seed + 12, False, 1)
    rgb *= (0.86 + 0.28 * blk)[..., None] * (1 - 0.0 * cj[..., None])
    # weathering streaks running down from ledges, cracks
    streak = smoothstep(0.55, 0.8, fbm(U01, V01 * 0.05, 120, seed + 9, False, 3)) * gl * smoothstep(0.3, 0.75, Y)
    rgb *= (1 - streak * 0.45)[..., None]
    ck = Image.new("L", (Ww_, Hh_), 0)
    dc = ImageDraw.Draw(ck)
    for i in range(int(260 * (rect[2] - rect[0]) * (rect[3] - rect[1]) / 0.4)):
        x, y = rr.uniform(rect[0], rect[2]), rr.uniform(rect[1], rect[3])
        pts = [((x - rect[0]) * b.sx, (y - rect[1]) * b.sy)]
        ang = rr.uniform(0.3, 2.8)
        for k in range(rr.integers(3, 9)):
            ang += rr.uniform(-0.7, 0.7)
            x += math.cos(ang) * 0.0025
            y += abs(math.sin(ang)) * 0.0035
            pts.append(((x - rect[0]) * b.sx, (y - rect[1]) * b.sy))
        dc.line(pts, fill=255, width=max(1, ss))
    crack = arr(ck) * gl * smoothstep(0.1, 0.3, V)
    rgb = lerp(rgb, col((5, 4, 6)), (crack * 0.8)[..., None])

    # --- contours: each moulding edge as a light etched line, brighter where a light catches it, with a dark
    # shadow line just below / right of it (the moulding's own shadow) - varied weight by the light
    lb_ = np.clip(line * 1.4, 0, 1)
    catch = np.clip(0.30 + rim_in * 1.6 + spill * 1.4 + lit * 0.35 + red * 0.6, 0, 1)
    sh = np.roll(np.roll(lb_, ss, axis=0), ss // 2 + 1, axis=1) * (1 - lb_)
    rgb = lerp(rgb, col((3, 2, 4)), (sh * 0.75 * gl)[..., None])
    rgb = lerp(rgb, tint * (0.65 + 0.5 * catch[..., None]), (lb_ * (0.45 + 0.5 * catch))[..., None])
    # the silhouette: silver gel where the moon catches it, a firm ink edge elsewhere
    sb = blur(sil, ss * 0.9)
    outer = np.clip((sil - sb) * 3.5, 0, 1)
    rgb = lerp(rgb, col((236, 238, 250)), np.clip(outer * rim * 1.8 + lb_ * rim_in * 1.2, 0, 1)[..., None] * 0.9)
    rgb = lerp(rgb, col((248, 176, 96)), np.clip(lb_ * spill * 1.5, 0, 1)[..., None] * 0.8)
    rgb = lerp(rgb, col((4, 3, 5)), (outer * (1 - np.clip(rim * 2.5, 0, 1)) * 0.7)[..., None])

    # the glass: glowing panes, mottled, with their leading
    nz_ = fbm(U01, V01, 60, seed + 11, False, 3)
    gl_rgb = glass * (0.62 + 0.5 * nz_[..., None])
    rgb = lerp(rgb, gl_rgb, gm[..., None])
    # deeper at the foot, where the mist pools
    rgb *= (1 - 0.35 * smoothstep(FOOT - 0.09, FOOT, Y))[..., None]
    # atmospheric depth
    if haze > 0:
        lumi = rgb.mean(-1, keepdims=True)
        rgb = lerp(rgb, lumi * 0.6 + col((40, 38, 58)) * 0.7, haze * 0.8)
    a = sil
    if ivy is not None:
        iv = np.asarray(ivy, np.float32)[r0:r1, c0:c1] / 255
        iv[..., 3] *= np.clip(blur(sil, ss * 2) * 1.5, 0, 1) * (1 - gm)
        ivr = iv[..., :3] * (1 + 1.2 * rim[..., None] + 0.8 * spill[..., None])
        rgb = lerp(rgb, ivr, iv[..., 3:4])
        a = np.clip(a + iv[..., 3], 0, 1)
    save_rgba(rgb, a, name, (out_w, out_h))
    # the moon rim alone (additive; the game swells it on the breath) at half resolution
    rim_rgb = col((214, 220, 240)) * np.clip(rim * 0.9 + rim_in * 0.5 + outer * near_moon * rim * 0.6, 0, 1)[..., None]
    ra = np.clip(rim_rgb.max(-1), 0, 1)
    save_rgba(rim_rgb / np.maximum(ra, 1e-4)[..., None], ra, name + "_rim", (out_w // 2, out_h // 2))
    # the glass's glow (additive; pulses and carries the breath's light outward)
    if glum.max() > 0.05:
        glow_rgb = glass * gm[..., None]
        soft = np.stack([blur(glow_rgb[..., i], ss * 3) for i in range(3)], -1)
        wide = np.stack([blur(glow_rgb[..., i], ss * 14) for i in range(3)], -1)
        out = glow_rgb * 0.35 + soft * 0.6 + wide * 1.2
        al = np.clip(out.max(-1), 0, 1)
        save_rgba(out / np.maximum(al, 1e-4)[..., None], al, name + "_glow", (out_w // 3, out_h // 3))
    else:
        p = os.path.join(OUT, name + "_glow.png")
        if os.path.exists(p):
            os.remove(p)


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
    """Far off, in the haze: a ruined abbey on a hill to the left (its spire crossing the moon), a lone needle beyond,
    a small town's towers far to the right, and the low hills they stand on - nothing mirrored."""
    b = Board(RECTS["spires_far"], w, h, ss=2)
    g = Gothic(b)
    r = np.random.default_rng(61)
    # the hills: an irregular ridge line
    xs = np.linspace(-ART_W / 2, ART_W / 2, 200)
    ridge = 0.705 - 0.03 * np.exp(-((xs + 0.42) / 0.22) ** 2) - 0.018 * np.exp(-((xs - 0.78) / 0.25) ** 2) \
        + (fbm(xs / ART_W + 0.5, xs * 0 + 0.2, 12, 62, False, 3) - 0.5) * 0.02
    g.poly([(x, y) for x, y in zip(xs, ridge)] + [(xs[-1], 0.80), (xs[0], 0.80)], 30, 80)
    # the abbey: a tall west tower with its spire, a long roofless nave, a broken gable
    ax, base = -0.355, 0.672
    g.rect(ax - 0.022, 0.40, ax + 0.022, base, 40, 110)
    g.spire(ax, 0.402, 0.235, 0.019, 40, 110, bands=4, cross=True)
    for k in (-1, 1):
        g.pinnacle(ax + k * 0.022, base, 0.43, 0.36 + r.uniform(0, 0.02), 0.005, 40, 100)
    for yy in (0.45, 0.52, 0.59):
        g.dark(lancet_pts(ax, yy, 0.0065, yy + 0.04), 8, 110)
    g.poly([(ax + 0.022, 0.55), (ax + 0.10, 0.55), (ax + 0.13, 0.58), (ax + 0.16, 0.565), (ax + 0.19, 0.61), (ax + 0.19, base),
            (ax + 0.022, base)], 34, 100)
    for k in range(5):
        x = ax + 0.04 + k * 0.03
        g.dark(lancet_pts(x, 0.585 + (0.02 if k > 2 else 0), 0.006, 0.635), 8, 100)
    g.poly([(ax - 0.022, 0.58), (ax - 0.07, 0.55), (ax - 0.085, 0.565), (ax - 0.11, 0.62), (ax - 0.11, base), (ax - 0.022, base)], 32, 100)
    # a lone needle far to the left, half lost in the haze
    g.rect(-0.86, 0.50, -0.84, 0.70, 36, 90)
    g.spire(-0.85, 0.502, 0.36, 0.009, 36, 90, bands=3)
    # the town far to the right: a squat tower, a thin spire, a broken keep
    for cx, top, tip, hw in ((0.66, 0.56, 0.47, 0.016), (0.73, 0.52, 0.40, 0.010), (0.97, 0.58, None, 0.02)):
        g.rect(cx - hw, top, cx + hw, 0.70, 38, 100)
        if tip is not None:
            g.spire(cx, top + 0.002, tip, hw * 0.85, 38, 100, bands=2, cross=r.random() < 0.5)
        else:
            g.poly([(cx - hw, top), (cx - hw * 0.4, top - 0.02), (cx, top - 0.008), (cx + hw * 0.6, top - 0.03), (cx + hw, top)], 38, 100)
        g.dark(lancet_pts(cx, top + 0.03, hw * 0.3, top + 0.06), 8, 100)
    g.poly([(0.60, 0.66), (0.64, 0.62), (0.70, 0.64), (0.76, 0.60), (0.83, 0.645), (0.90, 0.63), (0.95, 0.66), (0.95, 0.70), (0.60, 0.70)], 32, 90)
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


def foot_line(xs):
    """Artboard y of the cathedral's lowest visible foot at each x (nan where it does not stand): the ground must
    meet the building there (the rise it stands on)."""
    F = FOOT
    segs = [((-0.233, F, -0.02), (-0.233, F, 1.0)), ((-0.32, F, 0.528), (-0.233, F, 0.528)),
            ((-0.32, F, 0.528), (-0.32, F, 0.65)), ((-0.19, F, -0.05), (0.18, F, -0.05)),
            ((-0.13, F + 0.0375, -0.115), (0.13, F + 0.0375, -0.115)),
            ((-0.141, F + 0.04, -0.125), (-0.109, F + 0.04, -0.125)), ((0.102, F + 0.04, -0.125), (0.134, F + 0.04, -0.125))]
    out = np.full(xs.shape, np.nan, np.float32)
    for a, b_ in segs:
        t = np.linspace(0, 1, 400)
        P = [proj(a[0] + (b_[0] - a[0]) * k, a[1] + (b_[1] - a[1]) * k, a[2] + (b_[2] - a[2]) * k) for k in t]
        px = np.array([p[0] for p in P])
        py = np.array([p[1] for p in P])
        o = np.argsort(px)
        px, py = px[o], py[o]
        inside = (xs >= px[0]) & (xs <= px[-1])
        yv = np.interp(xs, px, py)
        out = np.where(inside, np.fmax(out, yv), out)
    return out


def terrain_top(xs):
    """The ground's top edge: the rise under the cathedral (meeting its foot), falling away irregularly to a low,
    misty hollow on the left and a broken scarp on the right, low hills beyond."""
    kx = [-1.25, -1.0, -0.82, -0.66, -0.52, -0.40, -0.30, -0.235, -0.205, 0.46, 0.50, 0.56, 0.62, 0.70, 0.86, 1.05, 1.25]
    ky = [0.700, 0.708, 0.722, 0.737, 0.744, 0.735, 0.712, 0.690, 0.676, 0.727, 0.733, 0.745, 0.766, 0.760, 0.752, 0.742, 0.736]
    base = np.interp(xs, kx, ky)
    j = (fbm(xs / 2.6 + 0.5, xs * 0 + 0.3, 14, 72, False, 4) - 0.5) * 0.018 + \
        (fbm(xs / 2.6 + 0.5, xs * 0 + 0.7, 60, 73, False, 2) - 0.5) * 0.006
    fl = foot_line(xs) - 0.0035
    k = np.isnan(fl)
    top = np.where(k, base + j, fl)
    # ease the rise into the building's ends
    top = np.where(~k, top, top)
    return top


_TT = {}


def terrain_at(x):
    if "x" not in _TT:
        _TT["x"] = np.linspace(-1.3, 1.3, 5201)
        _TT["y"] = terrain_top(_TT["x"])
    return float(np.interp(x, _TT["x"], _TT["y"]))


def drift_n(x, y, seed):
    """Low-frequency patchiness (0..1) for the flower drifts, in artboard coords."""
    return float(fbm(np.array([[x / 2.6 + 0.5]]), np.array([[y * 2.2]]), 9, seed + 500, False, 3)[0, 0])


def ground(w=2560, h=484):
    b = Board(RECTS["ground"], w, h, ss=2)
    X, Y = b.grid()
    rr = np.random.default_rng(71)
    u, v, hw = river_uv(X, Y)
    U01 = (X - b.x0) / (b.x1 - b.x0)
    V01 = (Y - b.y0) / (b.y1 - b.y0)
    xs = X[0]
    top1 = terrain_top(xs)
    top = np.broadcast_to(top1, X.shape)
    land = smoothstep(top - 0.0015, top + 0.0015, Y)
    below = Y - top                                         # depth below the terrain edge (A)
    near_c = np.exp(-((X - 0.16) / 0.42) ** 2)              # how much the rise's slope shows here
    # the rise: a lit plateau band along the top, then a dark rocky scarp falling to the river and the lowland
    scarp_bot = 0.80 + 0.02 * np.exp(-((X - 0.36) / 0.2) ** 2) - 0.035 * smoothstep(0.1, -0.5, X)
    on_scarp = smoothstep(0.012, 0.022, below) * (1 - smoothstep(scarp_bot - 0.01, scarp_bot + 0.006, Y)) * near_c
    n1 = fbm(U01, V01, 30, 73, False, 4)
    depth = smoothstep(0.70, 1.0, Y)
    rgb = lerp(col((50, 44, 54)), col((16, 13, 15)), depth)
    rgb *= (0.7 + 0.6 * n1)[..., None]
    plateau = (1 - smoothstep(0.0, 0.016, below)) * smoothstep(-0.5, 0.0, X) * (1 - smoothstep(0.5, 0.6, X))
    rgb = lerp(rgb, col((70, 62, 74)), plateau * 0.6)
    # grass: short strokes, longer nearer; none on the scarp's rock
    gr = Image.new("L", (b.W, b.H), 0)
    dg = ImageDraw.Draw(gr)
    for i in range(30000):
        x = rr.uniform(b.x0, b.x1)
        t_ = float(np.interp(x, xs, top1))
        y = rr.uniform(t_, 1.0)
        L = 0.003 + 0.022 * max(0.0, y - 0.66) / 0.34
        lean = rr.uniform(-0.4, 0.4) * L
        dg.line([b.P(x, y), b.P(x + lean, y - L)], fill=int(rr.uniform(60, 160)), width=max(1, int(1 + 3 * (y - 0.66) / 0.34)))
    grass = arr(gr) * (1 - on_scarp)
    rgb = lerp(rgb, col((72, 62, 58)), grass * 0.5)

    # strata of the scarp: ledges of rock in courses that follow the slope, sizes by distance
    rk = Image.new("L", (b.W, b.H), 0)     # coverage
    rt = Image.new("L", (b.W, b.H), 0)     # tone
    rl = Image.new("L", (b.W, b.H), 0)     # etched lines
    dk, dt, dl2 = ImageDraw.Draw(rk), ImageDraw.Draw(rt), ImageDraw.Draw(rl)
    places = []
    for row in range(6):
        for x in np.arange(-0.30, 0.62, 0.026 + row * 0.004):
            x += rr.uniform(-0.006, 0.006)
            t_ = float(np.interp(x, xs, top1))
            sb = float(0.80 + 0.02 * math.exp(-((x - 0.36) / 0.2) ** 2) - 0.035 * min(1, max(0, (0.1 - x) / 0.6)))
            y = t_ + 0.02 + (sb - t_ - 0.02) * (row / 5) + rr.uniform(-0.006, 0.006)
            if y > sb or rr.random() < 0.55:
                continue
            if abs(x - (0.30 + (y - 0.688) / 0.112 * 0.072)) < 0.018 + (y - 0.688) * 0.25:
                continue                      # the cascade's channel stays clear
            places.append((x, y, 0.008 + 0.014 * rr.uniform(0.6, 1.3) * (0.6 + row * 0.1)))
    for y in np.arange(0.80, 1.03, 0.0045):         # boulders along both banks
        c = river_at(y)
        if c is None:
            continue
        cx, hwr = c
        persp = (y - HORIZON) / 0.36
        for side in (-1, 1):
            if rr.random() < 0.30:
                off = hwr * rr.uniform(0.92, 1.25)
                places.append((cx + side * off, y, 0.005 + 0.028 * persp * rr.uniform(0.5, 1.1)))
        if rr.random() < 0.15:
            places.append((cx + rr.uniform(-1.4, 1.4) * hwr * 2.4, y, 0.004 + 0.018 * persp * rr.random()))
    in_river = river_rocks()
    places += in_river
    for x, y, r in sorted(places, key=lambda p: p[1]):
        ps = b.pts(rock_poly(x, y, r, rr))
        tone = int(rr.uniform(24, 60))
        dk.polygon(ps, fill=255)
        dt.polygon(ps, fill=tone)
        dl2.polygon(ps, fill=0)
        dl2.line(ps[:-2], fill=int(rr.uniform(150, 230)), width=max(2, int(b.L(r) * 0.06)))
        for j in range(rr.integers(2, 5)):
            a0 = rr.uniform(0.15, 0.85)
            p0 = ps[int(a0 * (len(ps) - 3))]
            dl2.line([p0, ((p0[0] + b.P(x, y)[0]) / 2 + rr.uniform(-1, 1) * b.L(r) * 0.3, (p0[1] + b.P(x, y)[1]) / 2 + b.L(r) * 0.2)],
                     fill=int(rr.uniform(70, 140)), width=max(1, int(b.L(r) * 0.03)))

    # the churchyard on the left slope: leaning gravestones of every shape, a broken wall, a fallen pinnacle
    def stone(ps, tone, e=200):
        p = b.pts(ps)
        dk.polygon(p, fill=255)
        dt.polygon(p, fill=int(tone))
        dl2.polygon(p, fill=0)
        dl2.line(p + [p[0]], fill=int(e), width=2)

    def rot(ps, cx, cy, ang):
        c_, s_ = math.cos(ang), math.sin(ang)
        return [(cx + (x - cx) * c_ - (y - cy) * s_, cy + (x - cx) * s_ + (y - cy) * c_) for x, y in ps]
    graves = []
    for i in range(26):
        x = rr.uniform(-0.95, -0.12) if i < 21 else rr.uniform(0.50, 0.85)
        t_ = float(np.interp(x, xs, top1))
        y = t_ + 0.006 + rr.random() ** 1.5 * 0.07
        c = river_at(y)
        if c is not None and abs(x - c[0]) < c[1] * 1.6:
            continue
        graves.append((y, x))
    for y, x in sorted(graves):
        s = 0.010 + 0.022 * (y - 0.68) / 0.12
        kind = rr.integers(0, 5)
        hgt = s * rr.uniform(1.3, 2.2)
        if kind == 0:     # round-headed slab
            ps = [(x - s * 0.45, y)] + [(x + s * 0.45 * math.cos(a), y - hgt + s * 0.45 - s * 0.45 * math.sin(a))
                                       for a in np.linspace(math.pi, 0, 10)] + [(x + s * 0.45, y)]
        elif kind == 1:   # a cross
            ps = [(x - s * 0.1, y), (x - s * 0.1, y - hgt * 0.62), (x - s * 0.38, y - hgt * 0.62), (x - s * 0.38, y - hgt * 0.76),
                  (x - s * 0.1, y - hgt * 0.76), (x - s * 0.1, y - hgt), (x + s * 0.1, y - hgt), (x + s * 0.1, y - hgt * 0.76),
                  (x + s * 0.38, y - hgt * 0.76), (x + s * 0.38, y - hgt * 0.62), (x + s * 0.1, y - hgt * 0.62), (x + s * 0.1, y)]
        elif kind == 2:   # snapped slab
            ps = [(x - s * 0.42, y), (x - s * 0.42, y - hgt * 0.55), (x - s * 0.1, y - hgt * 0.62), (x + s * 0.12, y - hgt * 0.45),
                  (x + s * 0.42, y - hgt * 0.5), (x + s * 0.42, y)]
        elif kind == 3:   # gothic pointed stone
            ps = [(x - s * 0.4, y)] + arch_pts(x, y - hgt + s * 0.6, s * 0.4, 1.0, 8) + [(x + s * 0.4, y)]
        else:             # a low tomb chest, seen a little from above
            ps = [(x - s * 0.9, y), (x - s * 0.9, y - s * 0.55), (x - s * 0.6, y - s * 0.75), (x + s * 1.0, y - s * 0.75),
                  (x + s * 0.8, y - s * 0.55), (x + s * 0.8, y)]
        ang = rr.uniform(-0.35, 0.35) if kind != 4 else rr.uniform(-0.05, 0.05)
        stone(rot(ps, x, y, ang), rr.uniform(34, 70))
        if kind in (0, 3):     # an incised cross on the face
            cxp = rot([(x, y - hgt * 0.62), (x, y - hgt * 0.35), (x - s * 0.12, y - hgt * 0.53), (x + s * 0.12, y - hgt * 0.53)], x, y, ang)
            dl2.line(b.pts(cxp[:2]), fill=90, width=2)
            dl2.line(b.pts(cxp[2:]), fill=90, width=2)
    # the churchyard wall: courses of blocks along the slope, broken through in the middle and crumbling at the end
    wall_x = np.linspace(-0.86, -0.24, 60)
    for i in range(len(wall_x) - 1):
        xa, xb = wall_x[i], wall_x[i + 1]
        if -0.64 < xa < -0.52:
            continue
        ta = float(np.interp(xa, xs, top1)) + 0.004
        hh = 0.024 * (0.5 + 0.5 * math.sin(i * 0.7) ** 2) * (0.3 if xa > -0.32 else 1.0) * (0.4 if -0.70 < xa < -0.64 else 1.0)
        ya = ta + 0.018
        for k in range(int(hh / 0.006) + 1):
            y0 = ya - k * 0.006
            stone([(xa + rr.uniform(-0.001, 0.001), y0), (xb, y0), (xb, y0 - 0.0058), (xa, y0 - 0.0058)], rr.uniform(30, 64), 170)
    # the fallen pinnacle, lying in the grass below the bay it came from
    px_, py_ = proj(-0.30, FOOT, 0.25)
    shaft = [(px_ - 0.045, py_ + 0.006), (px_ + 0.02, py_ - 0.002), (px_ + 0.024, py_ + 0.008), (px_ - 0.044, py_ + 0.015)]
    stone(shaft, 52, 200)
    stone([(px_ + 0.02, py_ - 0.003), (px_ + 0.062, py_ + 0.004), (px_ + 0.024, py_ + 0.009)], 46, 190)
    for k in range(6):
        xk = px_ - 0.035 + k * 0.012
        stone([(xk, py_ + 0.0015 - k * 0.0011), (xk + 0.004, py_ - 0.004 - k * 0.0011), (xk + 0.006, py_ - k * 0.0011)], 50, 180)

    rcov, rtone, rline = arr(rk), arr(rt), arr(rl)
    hat = np.clip((np.sin((X * b.sx - Y * b.sy) / b.ss * 2 * math.pi / 3.5) - 0.4) / 0.6, 0, 1)
    rock_rgb = lerp(col((7, 7, 10)), col((50, 48, 58)), rtone * 1.6)
    rock_rgb = lerp(rock_rgb, col((110, 106, 122)), hat * rtone * 0.5)
    rock_rgb = lerp(rock_rgb, col((196, 190, 200)), rline * 0.85)
    gl_ = fbm(U01, V01, 300, 79, False, 2)
    rock_rgb = lerp(rock_rgb, col((255, 236, 236)), rline * smoothstep(0.62, 0.72, gl_) * 0.9)
    rock_rgb = lerp(rock_rgb, col((150, 24, 30)), np.clip(1.6 - np.abs(u), 0, 1)[..., None][..., 0] * 0.35)
    # the scarp's own rock face between the ledges: dark, hatched along the slope
    face = on_scarp * (1 - rcov)
    rgb = lerp(rgb, col((22, 19, 24)) + col((40, 36, 46)) * hat[..., None] * 0.6, face * 0.85)

    # the river
    edge_n = fbm(U01, V01, 40, 74, False, 3)
    au = np.abs(u)
    water = 1 - smoothstep(0.90 + edge_n * 0.16, 1.02 + edge_n * 0.16, au)
    water *= smoothstep(RIVER[0][1] - 0.004, RIVER[0][1] + 0.004, Y)
    # a gravel sandbar on the inside of the bend
    sbx, sby = -0.165, 0.913
    bar = np.exp(-(((X - sbx) / 0.07) ** 2 + ((Y - sby) / 0.008) ** 2) * 1.0) + 0.6 * np.exp(-(((X - sbx - 0.05) / 0.035) ** 2 + ((Y - sby - 0.004) / 0.006) ** 2))
    bar = smoothstep(0.45, 0.55, bar + (edge_n - 0.5) * 0.3)
    uu = np.clip(u * 0.5 + 0.5, 0, 1)
    sw = fbm(uu * 0.5, v * 0.5, 5, 75, False, 5)
    wrgb = lerp(col((178, 22, 32)), col((70, 4, 10)), np.clip(au ** 2 * 0.6 + (0.55 - sw) * 1.4, 0, 1))
    dash = fbm(uu * 0.5, v * 2.0, 14, 76, False, 3, aspect=0.35)
    wrgb = lerp(wrgb, col((226, 70, 74)), smoothstep(0.66, 0.74, dash) * 0.6)
    wrgb = lerp(wrgb, col((30, 1, 4)), smoothstep(0.30, 0.24, dash) * 0.5)
    lines_ = np.clip((np.sin(v * 700 + sw * 20) - 0.85) / 0.15, 0, 1)
    wrgb = lerp(wrgb, col((190, 40, 46)), lines_ * 0.18)
    wrgb += (np.exp(-((X - MOON[0]) / 0.035) ** 2) * smoothstep(0.84, 0.95, Y) * 0.12)[..., None] * col((255, 210, 170))
    wrgb *= (1 - smoothstep(0.7, 1.0, au) * 0.35)[..., None]
    # the current drawn in ink: lines running with the flow, parallel to the banks (closer together near them, where
    # the water slows and folds), broken like pen strokes; a pale crimson line beside each dark one
    flow_n = fbm(uu * 0.5, v * 0.6, 9, 79, False, 3)
    fu = u * (6.0 + 5.0 * au) + (flow_n - 0.5) * 1.6
    fl_d = np.abs((fu % 1.0) - 0.5) * 2
    pen = smoothstep(0.38, 0.5, fbm(uu * 0.5, v * 3.0, 20, 80, False, 2))
    dark_l = smoothstep(0.16, 0.04, fl_d) * pen * (0.35 + 0.65 * smoothstep(0.2, 0.9, au))
    lite_l = smoothstep(0.12, 0.03, np.abs(((fu + 0.5) % 1.0) - 0.5) * 2) * pen * smoothstep(0.75, 0.2, au)
    wrgb = lerp(wrgb, col((34, 2, 6)), dark_l * 0.55)
    wrgb = lerp(wrgb, col((232, 96, 96)), lite_l * 0.22)
    # the banks and the rocks mirrored darkly in the water near them
    wrgb *= (1 - 0.45 * smoothstep(0.55, 0.95, au) * (0.6 + 0.4 * flow_n))[..., None]
    speck = fbm(U01, V01, 260, 78, False, 2)
    bar_rgb = lerp(col((96, 80, 74)), col((150, 132, 118)), smoothstep(0.4, 0.7, speck)) * (0.8 + 0.3 * smoothstep(sby + 0.006, sby - 0.006, Y))[..., None]
    bar_rgb = lerp(bar_rgb, col((40, 30, 32)), (speck > 0.72) * 0.6)

    # the fallen log across the near bank, half in the water
    lg, lgl = Image.new("L", (b.W, b.H), 0), Image.new("L", (b.W, b.H), 0)
    dlg, dlgl = ImageDraw.Draw(lg), ImageDraw.Draw(lgl)
    lx0, ly0, lx1, ly1 = 0.15, 0.838, 0.30, 0.852
    n_ = 24
    top_, bot_ = [], []
    for i in range(n_ + 1):
        t = i / n_
        x = lx0 + (lx1 - lx0) * t
        y = ly0 + (ly1 - ly0) * t + 0.002 * math.sin(t * 7)
        r = 0.0055 + 0.003 * t
        top_.append((x, y - r))
        bot_.append((x, y + r))
    dlg.polygon(b.pts(top_ + bot_[::-1]), fill=255)
    for (x, y), (x2, y2) in zip(top_[::3], bot_[::3]):
        dlgl.line(b.pts([(x, y), (x + 0.003, (y + y2) / 2), (x + 0.001, y2)]), fill=90, width=2)
    dlgl.line(b.pts(top_), fill=220, width=3)
    for (x, y), ang, L in (((0.18, 0.839), -1.1, 0.03), ((0.22, 0.842), -0.6, 0.022), ((0.27, 0.846), -1.4, 0.04)):
        p2 = (x + math.cos(ang) * L, y + math.sin(ang) * L)
        dlg.line(b.pts([(x, y), p2]), fill=255, width=5)
        dlgl.line(b.pts([(x, y), p2]), fill=160, width=2)
    dlgl.ellipse(b.pts([(lx1 - 0.004, ly1 - 0.009), (lx1 + 0.006, ly1 + 0.009)]), outline=200, width=2)    # the cut end's rings
    logc, logl = arr(lg), arr(lgl)
    log_rgb = lerp(col((14, 11, 12)), col((150, 140, 150)), logl)
    log_rgb = lerp(log_rgb, col((120, 20, 26)), smoothstep(0.84, 0.856, Y) * 0.5)

    # the skull: half sunk near the middle of the stream, the moon on its crown
    sk = Image.new("L", (b.W, b.H), 0)
    skl = Image.new("L", (b.W, b.H), 0)
    dsk, dskl = ImageDraw.Draw(sk), ImageDraw.Draw(skl)
    sx, sy, srad = -0.03, 0.972, 0.019
    dsk.ellipse(b.pts([(sx - srad, sy - srad * 1.05), (sx + srad, sy + srad * 0.9)]), fill=150)
    dsk.polygon(b.pts([(sx - srad * 0.7, sy + srad * 0.3), (sx + srad * 0.7, sy + srad * 0.3), (sx + srad * 0.45, sy + srad * 0.95), (sx - srad * 0.45, sy + srad * 0.95)]), fill=130)
    for ex in (-0.42, 0.42):
        dsk.ellipse(b.pts([(sx + srad * ex - srad * 0.27, sy + srad * 0.05), (sx + srad * ex + srad * 0.27, sy + srad * 0.5)]), fill=10)
    dsk.polygon(b.pts([(sx, sy + srad * 0.5), (sx - srad * 0.1, sy + srad * 0.72), (sx + srad * 0.1, sy + srad * 0.72)]), fill=10)
    dskl.arc(b.pts([(sx - srad, sy - srad * 1.05), (sx + srad, sy + srad * 0.9)]), 190, 330, fill=230, width=3)
    dskl.line(b.pts([(sx - srad * 0.15, sy - srad * 0.9), (sx + srad * 0.05, sy - srad * 0.4), (sx - srad * 0.2, sy - srad * 0.1)]), fill=110, width=2)
    skv, sklv = arr(sk), arr(skl)
    sk_cov = (skv > 0).astype(np.float32) * (1 - smoothstep(sy + srad * 0.55, sy + srad * 0.62, Y))
    sk_rgb = lerp(col((20, 16, 18)), col((150, 138, 124)), skv)
    sk_rgb = lerp(sk_rgb, col((235, 218, 190)), sklv * 0.8)
    sk_rgb = lerp(sk_rgb, col((120, 20, 26)), smoothstep(sy, sy + srad * 0.6, Y) * 0.5)
    dd = np.hypot((X - sx) / 2.6, (Y - (sy + srad * 0.58)))
    rings = np.clip((np.sin(dd / srad * 14) - 0.5) / 0.5, 0, 1) * smoothstep(srad * 1.6, srad * 0.7, dd) * smoothstep(srad * 0.3, srad * 0.5, dd)

    # foam: round the rocks in the river, along the log, and churned at the foot of the fall
    foam = np.zeros_like(X)
    for (fx, fy, fr) in in_river:
        dx_, dy_ = (X - fx) / (fr * 2.2), (Y - fy - fr * 0.25) / (fr * 0.9)
        d2 = np.hypot(dx_, dy_)
        foam += np.exp(-((d2 - 1.0) / 0.25) ** 2) * smoothstep(-0.3, 0.6, dy_)
    cxt, ty, thw, cxb, by, bhw = CASCADE
    d2 = np.hypot((X - cxb) / (bhw * 2.2), (Y - by - 0.004) / 0.012)
    foam += np.exp(-d2 ** 2 * 1.5) * smoothstep(0.45, 0.65, speck) * 1.5
    foam += blur(logc, b.ss * 3) * 1.5 * (1 - logc) * smoothstep(0.4, 0.6, speck)
    foam = np.clip(foam, 0, 1) * water * (1 - bar)
    # the glass's amber light spilling down the steps, the scarp and onto the head of the river
    spillw = np.exp(-(((X - 0.33) / 0.12) ** 2 + ((Y - 0.74) / 0.06) ** 2))
    rgb = rgb + col((200, 120, 50)) * (spillw * 0.35 * land)[..., None]
    wrgb = wrgb + col((230, 120, 60)) * (np.exp(-(((X - 0.33) / 0.14) ** 2 + ((Y - 0.80) / 0.04) ** 2)) * 0.4)[..., None]
    # contact shadow along the cathedral's foot
    fl = foot_line(xs)
    has = ~np.isnan(fl)
    flb = np.broadcast_to(np.where(has, fl, -1), X.shape)
    rgb *= (1 - 0.65 * (np.broadcast_to(has, X.shape)) * smoothstep(flb - 0.006, flb - 0.002, Y) * (1 - smoothstep(flb + 0.002, flb + 0.02, Y)))[..., None]
    rshadow = blur(np.roll(rcov, int(b.sy * 0.006), axis=0), b.ss * 4)
    out = lerp(rgb, wrgb, water)
    out = lerp(out, bar_rgb, water * bar)
    out *= (1 - rshadow * 0.65)[..., None]
    out = lerp(out, col((220, 70, 70)), rings * water * 0.4)
    out = lerp(out, col((255, 196, 186)), foam * 0.55)
    out = lerp(out, sk_rgb, sk_cov)
    out = lerp(out, rock_rgb, rcov)
    out = lerp(out, log_rgb, logc)
    # the cascade's channel down the scarp: dark wet red (the animated fall is drawn over it)
    k = np.clip((Y - ty) / (by - ty), 0, 1)
    ch = (np.abs(X - (cxt + (cxb - cxt) * k)) < thw + (bhw - thw) * k) & (Y > ty - 0.002) & (Y < by + 0.002)
    out = lerp(out, col((80, 6, 12)), ch.astype(np.float32) * 0.92 * land)
    alpha = np.clip(land + rcov + water + logc, 0, 1)
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
            if c is not None and abs(x - c[0]) < c[1] * 1.12 + size * 1.6:
                skip = True
        if skip:
            continue
        t_ = terrain_at(x)
        if y < t_ + 0.004 + size * 0.3:          # nothing floating above the ground line
            continue
        if -0.12 < x < 0.58 and y < 0.80 + 0.02 * math.exp(-((x - 0.36) / 0.2) ** 2) and rr.random() < 0.9:
            continue                              # the scarp under the cathedral is bare rock
        xc = CASCADE[0] + (CASCADE[3] - CASCADE[0]) * min(1, max(0, (y - CASCADE[1]) / (CASCADE[4] - CASCADE[1])))
        if abs(x - xc) < 0.03 and y < CASCADE[4] + 0.01:
            continue
        # drifts: flowers grow in patches with bare ground between, red and ivory in their own drifts
        dens = drift_n(x, y, seed)
        if rr.random() > smoothstep(0.40, 0.62, dens) * 0.97 + 0.03:
            continue
        items.append((y, x))
    items.sort()
    for y, x in items:
        p = (y - y_from) / (y_to - y_from)
        sc = scale_far + (scale_near - scale_far) * p
        kind = rr.random()
        shade = 0.55 + 0.45 * p
        ivory = drift_n(x * 1.7 + 3.1, y * 1.7, seed + 9)
        if kind < 0.18:
            leafy_tuft(d, b, x, y, sc * 1.6, rr, shade)
        elif ivory > 0.56 or kind < 0.24:
            blossom(d, b, x, y, sc * rr.uniform(0.7, 1.7), rr, min(1.0, shade * 1.15))
        else:
            red_cluster(d, b, x, y, sc * rr.uniform(0.6, 1.5), rr, shade)
    if name == "field_near":
        for side, nb in ((-1, 8), (1, 3)):
            for k in range(nb):
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
    flower_layer("field_far", 2560, 341, 81, 0.69, 0.93, 0.004, 0.016, 9000)
    flower_layer("field_near", 2560, 242, 82, 0.86, 1.05, 0.012, 0.055, 1100)


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


def frame_side(name, mirror, seed, variant="ruin"):
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

    if variant == "ruin":
        limb(0.30, 1.03, -0.15, 0.36, 0.036, 3)
        limb(0.30, 0.78, 0.55, 0.30, 0.017, 3)
        limb(0.28, 0.86, -0.75, 0.22, 0.015, 2)
        limb(0.05, 0.20, 0.95, 0.32, 0.013, 3)
    else:
        # one old oak at the very edge, leaning in, its crown thrown over the top toward the cathedral;
        # a long dead bough crossing the upper corner, bare
        limb(0.07, 1.03, 0.10, 0.42, 0.046, 3)
        limb(0.11, 0.66, 1.15, 0.22, 0.020, 2)
        limb(0.10, 0.74, 0.55, 0.20, 0.015, 2)
        limb(0.14, 0.50, -0.35, 0.20, 0.012, 2)
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
        if variant != "ruin" and cen[0][1] > 0.55:
            continue                         # the oak's crown is high and one-sided
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
    if variant != "ruin":
        for p_ in prof:
            p_[0] = 0.03 + 0.15 * smoothstep(0.72, 1.0, p_[1]) + (p_[0] - 0.09) * 0.3
    shape([(-0.05, -0.01)] + [(p_[0] - 0.006, p_[1]) for p_ in prof] + [(-0.05, 1.01)], 18, 0)
    for i in range(len(prof) - 1):
        y0_, y1_ = prof[i][1], prof[i + 1][1]
        blocks(-0.02, prof[i][0], y0_, y1_, 0.025, 0.15)
    if variant == "ruin":
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
    for i in range(22 if variant == "ruin" else 9):
        x, y, r = rr.uniform(0.0, 0.45 if variant == "ruin" else 0.22), rr.uniform(0.88, 1.01), rr.uniform(0.010, 0.035)
        shape(rock_poly(x, y, r, rr), rr.uniform(40, 80), 200, 2)
    ruin_rgb = shade_etched(b, arr(rc), arr(rt), arr(rl), 0.85, -0.5, (232, 228, 240), (196, 60, 54), seed + 2, 0.12)

    # ---------------- vines in front, hanging from the overhang and the arch
    vine = Image.new("RGBA", (b.W, b.H), (0, 0, 0, 0))
    dv = ImageDraw.Draw(vine)
    for k in range(20 if variant == "ruin" else 0):
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
    for i in range(170 if variant == "ruin" else 70):
        y = 0.80 + rr.random() ** 0.7 * 0.22
        x = rr.uniform(0.02, 0.36 if variant == "ruin" else 0.2) * (0.5 + 0.5 * (y - 0.8) / 0.22)
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
    frame_side("frame_right", True, 92, "tree")


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
    """Additive glints for the river strip (u across bank to bank, v along, wrapping): wave crests drawn as short pen
    strokes across the flow, bowed downstream, in loose rows - a hatched surface that the game scrolls toward the
    viewer; brighter toward the middle of the stream, thinning at the banks."""
    rr = np.random.default_rng(121)
    ss = 3
    W, H = w * ss, h * ss
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    rows = 34
    for r in range(rows):
        y0 = (r + rr.uniform(-0.25, 0.25)) / rows * H
        x = rr.uniform(-0.05, 0.05) * W
        while x < W:
            L = rr.uniform(0.04, 0.16) * W
            mid = (x + L / 2) / W
            keep = 1 - abs(mid - 0.5) * 1.5
            if rr.random() < keep:
                bow = rr.uniform(0.15, 0.45) * H / rows
                pts = [(x + L * k / 8, y0 + bow * (1 - ((k / 4) - 1) ** 2)) for k in range(9)]
                for dy in (-H, 0, H):                     # wrap in v
                    d.line([(px, py + dy) for px, py in pts], fill=int(rr.uniform(120, 255)), width=ss)
            x += L + rr.uniform(0.02, 0.12) * W
    a = np.asarray(m.resize((w, h), Image.LANCZOS), np.float32) / 255
    U = (np.arange(w, dtype=np.float32) + 0.5) / w
    a *= (smoothstep(0.0, 0.18, U) * smoothstep(1.0, 0.82, U))[None, :]
    rgb = np.broadcast_to(col((255, 132, 120)), (h, w, 3)).copy()
    save_rgba(rgb, np.clip(a * 1.1, 0, 1), "ripples")


def streak(w=64, h=256):
    """The moon's reflection, a path of glints: short silver dashes across the flow, tight and bright in the middle,
    broken and scattered toward the edges, with a few sparks; centred in u (clamped), wrapping in v."""
    rr = np.random.default_rng(131)
    ss = 4
    W, H = w * ss, h * ss
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    y = 0.0
    while y < H:
        spread = abs(rr.normal(0, 0.16))
        cxp = 0.5 + rr.normal(0, 0.05 + spread * 0.5)
        L = (0.10 + 0.5 * rr.random() * (1 - min(spread * 3, 0.9))) * W
        th = max(1, int(ss * rr.uniform(0.6, 1.4)))
        v = int(255 * (1 - min(spread * 2.5, 0.75)) * rr.uniform(0.6, 1.0))
        for dy in (-H, 0, H):
            d.line([(cxp * W - L / 2, y + dy), (cxp * W + L / 2, y + dy + rr.uniform(-1, 1) * ss)], fill=v, width=th)
            if rr.random() < 0.12:
                sx_ = cxp * W + rr.uniform(-0.3, 0.3) * W
                d.ellipse([sx_ - ss, y + dy - ss, sx_ + ss, y + dy + ss], fill=255)
        y += rr.uniform(0.6, 2.2) * ss * 2
    a = np.asarray(m.resize((w, h), Image.LANCZOS), np.float32) / 255
    U = (np.arange(w, dtype=np.float32) + 0.5) / w
    a *= (np.exp(-((U - 0.5) / 0.3) ** 2) * smoothstep(0.0, 0.08, U) * smoothstep(1.0, 0.92, U))[None, :]
    rgb = np.broadcast_to(col((255, 232, 206)), (h, w, 3)).copy()
    save_rgba(rgb, np.clip(a * 1.2, 0, 1), "streak")


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


def shafts(w=256, h=512):
    """Soft light shafts fanning out of the rose window and down through the mist (additive; the game anchors the
    narrow top at the rose, and breathes and slowly shifts them)."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / w, ys / h
    a_ = (U - 0.5) / (0.06 + 0.94 * V)                 # angular coordinate in the fan
    r = np.random.default_rng(151)
    rays = np.zeros_like(U)
    for i in range(9):
        c, wd, k = r.uniform(-0.42, 0.42), r.uniform(0.02, 0.07), r.uniform(0.4, 1.0)
        rays += k * np.exp(-((a_ - c) / wd) ** 2)
    n = fbm(a_ * 0.5 + 0.5, V, 6, 152, False, 3)
    rays *= (0.6 + 0.8 * n) * smoothstep(0.0, 0.10, V) * (1 - smoothstep(0.55, 1.0, V)) * smoothstep(0.55, 0.3, np.abs(a_))
    rgb = lerp(col((255, 196, 120)), col((236, 90, 70)), smoothstep(0.1, 0.9, V))
    save_rgba(rgb, np.clip(rays * 0.55, 0, 1), "shafts")


def fg_plants(name, w, h, seed, kind):
    """Extreme-foreground pop-outs, out of focus (they sit between the viewer and the scene and move fastest):
    'briar' - thorny canes arching up from a bottom corner with a few red roses; 'branch' - a dead twig hanging in
    from the top edge."""
    ss = 2
    W, H = w * ss, h * ss
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    rim = Image.new("L", (W, H), 0)
    d, dr = ImageDraw.Draw(img), ImageDraw.Draw(rim)
    r = np.random.default_rng(seed)
    def cane(x, y, ang, L, wd, depth, down=False):
        pts = []
        for k in range(26):
            t = k / 25
            pts.append((x, y))
            ang += r.uniform(-0.09, 0.09) + (0.045 if not down else -0.02)
            x += math.cos(ang) * L / 25
            y += math.sin(ang) * L / 25
        for k in range(len(pts) - 1):
            ww = max(2, int(wd * (1 - k / 30)))
            d.line([pts[k], pts[k + 1]], fill=(12, 8, 9, 255), width=ww)
            dr.line([(pts[k][0], pts[k][1] - ww * 0.4), (pts[k + 1][0], pts[k + 1][1] - ww * 0.4)], fill=180, width=max(1, ww // 4))
            if k % 3 == 1:                              # thorns
                px, py = pts[k]
                sd = r.choice([-1, 1])
                tl = ww * r.uniform(0.8, 1.4)
                d.polygon([(px - ww * 0.3, py), (px + sd * tl * 0.3, py - sd * tl), (px + ww * 0.3, py)], fill=(14, 9, 10, 255))
            if k % 5 == 3 and r.random() < 0.7:          # leaves
                px, py = pts[k]
                la = ang + r.uniform(-1.5, 1.5)
                L2 = wd * r.uniform(2.5, 4.0)
                tip = (px + math.cos(la) * L2, py + math.sin(la) * L2)
                m1 = ((px + tip[0]) / 2 - math.sin(la) * L2 * 0.25, (py + tip[1]) / 2 + math.cos(la) * L2 * 0.25)
                m2 = ((px + tip[0]) / 2 + math.sin(la) * L2 * 0.25, (py + tip[1]) / 2 - math.cos(la) * L2 * 0.25)
                d.polygon([(px, py), m1, tip, m2], fill=(30, 26, 18, 255))
                dr.line([(px, py), m1, tip], fill=110, width=2)
        if depth > 0:
            for _ in range(2):
                j = r.integers(8, 20)
                cane(pts[j][0], pts[j][1], ang + r.uniform(-1.2, 0.4), L * r.uniform(0.35, 0.6), wd * 0.6, depth - 1, down)
        return pts
    if kind == "briar":
        tips = []
        for i in range(5):
            x0 = r.uniform(-0.05, 0.25) * W
            pts = cane(x0, H * 1.02, -math.pi / 2 + r.uniform(0.15, 0.9), r.uniform(0.5, 0.95) * H, r.uniform(10, 18), 2)
            tips.append(pts[r.integers(12, 24)])
        for (x, y) in tips[:3]:                          # roses, dark red with a crimson rim
            R = r.uniform(18, 30)
            for k in range(7, 0, -1):
                rr_ = R * k / 7
                a0 = r.uniform(0, math.pi)
                c = (int(60 + 120 * (k % 2) * (k / 7)), int(6 + 8 * k / 7), int(12 + 8 * k / 7), 255)
                d.pieslice([x - rr_, y - rr_ * 0.85, x + rr_, y + rr_ * 0.85], math.degrees(a0), math.degrees(a0) + 300, fill=c)
            dr.arc([x - R, y - R * 0.85, x + R, y + R * 0.85], 190, 330, fill=255, width=3)
    else:
        pts = cane(W * 0.15, -H * 0.05, math.pi * 0.35, H * 0.9, 16, 3, down=True)
    a = np.asarray(img, np.float32) / 255
    rm = np.asarray(rim, np.float32) / 255
    rgb = a[..., :3] * 1.6 + rm[..., None] * col((190, 46, 40)) * 1.5
    al = a[..., 3]
    # out of focus: blur the premultiplied colour and the alpha together
    sig = 3.0 * ss
    pm = [blur(rgb[..., i] * al, sig) for i in range(3)]
    al2 = blur(al, sig)
    rgb2 = np.stack(pm, -1) / np.maximum(al2, 1e-4)[..., None]
    save_rgba(rgb2, np.clip(al2 * 1.05, 0, 1), name, (w, h))


def mist_pool(w=1024, h=256):
    """Mist lying in the low ground: a soft, lumpy bank thicker at the bottom (placed in the hollow on the left and
    along the river; the game drifts it and thickens it on the breath)."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    U, V = xs / w, ys / h
    n = fbm(U, V, 3, 161, False, 5, aspect=4.0)
    prof = smoothstep(0.0, 0.7, V) * smoothstep(0.0, 0.25, U) * smoothstep(1.0, 0.75, U)
    a = smoothstep(0.3, 0.7, n * 0.7 + V * 0.5) * prof * 0.6
    rgb = lerp(col((150, 120, 132)), col((90, 70, 90)), V)
    save_rgba(rgb, a, "mist_pool")


def sprites():
    glow(); sparkle(); bats(); petal(); leaf(); ring(); cascade(); ripples(); streak(); grain_tex(); vignette(); mist()
    shafts(); mist_pool()
    fg_plants("fg_briar", 768, 512, 171, "briar")
    fg_plants("fg_thorns", 512, 384, 172, "briar")
    fg_plants("fg_branch", 512, 512, 173, "branch")


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


def hand_drawn_all(only=None):
    def hd(name, *a, **kw):
        groups = {"sky": "sky", "clouds_far": "clouds", "clouds_near": "clouds", "moon": "moon", "spires_far": "spires",
                  "ground": "ground", "field_far": "fields", "field_near": "fields"}
        grp = groups.get(name, "frames")
        if only is None or grp in only:
            print("hand-drawing", name, flush=True)
            hand_drawn(name, *a, **kw)
    hd("sky", 0.0, pencil=0.9, ink=0.0, gel=0.0, L=14, seed=201, keep=0.6)
    hd("clouds_far", 0.08, pencil=1.0, ink=0.4, gel=0.3, L=12, seed=202, keep=0.4)
    hd("clouds_near", 0.05, pencil=1.0, ink=0.4, gel=0.0, L=12, seed=203, keep=0.4)
    hd("moon", 0.0, pencil=1.2, ink=0.6, gel=0.0, stipple=0.8, L=7, seed=204, keep=0.5,
       burnish=lambda l, a, s: smoothstep(0.55, 0.8, l) * 0.6)
    hd("spires_far", math.pi / 2, pencil=0.8, ink=0.7, gel=0.4, L=8, seed=205, chalk=1.0, keep=0.5)
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

    def place(self, im, x0, y0, x1, y1, mode="over", tint=(1, 1, 1), alpha=1.0, wrap_dx=None, wrap_w=None):
        """Draw image im (PIL RGBA/RGB) into the screen rect (screen px)."""
        w, h = int(round(x1 - x0)), int(round(y1 - y0))
        if wrap_w is not None:
            w = int(round(wrap_w))
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
STREAK_HW = 0.05                            # the moon reflection's half width (A)
CASCADE_TILES, CASCADE_SPEED = 1.6, 0.9      # v tiles over the fall; tiles per second
STARS = [(-0.92, 0.06), (-0.71, 0.13), (-0.55, 0.04), (-0.48, 0.19), (-0.08, 0.07), (0.03, 0.16), (0.62, 0.12),
         (0.58, 0.03), (0.77, 0.09), (0.86, 0.22), (1.02, 0.05), (-1.05, 0.20), (0.69, 0.27), (-0.83, 0.27)]


def sample_strip(S, tex, t, kind, ox=lambda z: 0.0):
    """Additive river overlays (ripples / moon streak) the way the Java strip maps them (riding the ground's depth)."""
    ta = np.asarray(tex, np.float32) / 255
    th, tw = ta.shape[:2]
    y0 = int(max(0, S.sy(RIVER[0][1]) - 40))
    ys, xs = np.mgrid[y0:S.H, 0:S.W].astype(np.float32)
    Y = (ys - S.top) / S.A
    shift = np.vectorize(lambda y: ox(zg(y)))(Y[:, :1])
    X = (xs - S.W / 2 - shift) / S.A
    u, v, hw = river_uv(X, Y)
    inside = (np.abs(u) < 1.0) & (Y >= RIVER[0][1])
    if kind == "ripples":
        uu = np.clip((u * 0.5 + 0.5), 0, 0.999)
        vv = (v * RIPPLE_K + t * RIPPLE_SPEED) % 1.0
        alpha_k = 0.6
    else:
        uu = np.clip(0.5 + (X - MOON[0]) / (2 * STREAK_HW), 0, 0.999)
        vv = (v * RIPPLE_K * 0.7 + t * 0.12) % 1.0
        alpha_k = 0.75 * smoothstep(0.84, 0.88, Y) * (1 - smoothstep(0.95, 0.99, Y))
    px = ta[(vv * th).astype(int).clip(0, th - 1), (uu * tw).astype(int)]
    add = px[..., :3] * (px[..., 3] * inside * alpha_k)[..., None]
    S.img[y0:] += add


def write_java_cath():
    """Write the cathedral layers' rects/depths, the river rocks, the rose and the door into LobbyBackdrop.java."""
    path = os.path.join(REPO, "bar/src/main/java/com/solegendary/reignofnether/hud/LobbyBackdrop.java")
    src = open(path).read()
    a = src.index("    // <gen:cath>")
    b = src.index("    // </gen:cath>")
    f = lambda v: f"{v:.4f}f"
    names = [nm for nm, *_ in CATH_LAYERS]
    rects = ", ".join("{" + ", ".join(f(v) for v in CATH_BOXES[nm]) + "}" for nm in names)
    rx, ry = proj(0.0, 0.338, 0.004)
    dx, dy = proj(0.0, 0.73, 0.0)
    rocks = ", ".join("{" + ", ".join(f(v) for v in r) + "}" for r in river_rocks())
    body = ("    // <gen:cath> written by tools/gen_lobby_art.py - do not edit by hand\n"
            f"    private static final String[] CATH = {{{', '.join(chr(34) + n + chr(34) for n in names)}}};\n"
            f"    private static final float[][] CATH_RECT = {{{rects}}};\n"
            f"    private static final float[] CATH_Z = {{{', '.join(f(z) for _, z, _ in CATH_LAYERS)}}};\n"
            "    /** the rocks breaking the river's surface (x, y, r) - gen_lobby_art.river_rocks() */\n"
            f"    private static final float[][] RIVER_ROCKS = {{{rocks}}};\n"
            "    /** the rose window and the portal (artboard), where the light comes from */\n"
            f"    private static final float ROSE_X = {f(rx)}, ROSE_Y = {f(ry)}, DOOR_X = {f(dx)}, DOOR_Y = {f(dy)};\n")
    open(path, "w").write(src[:a] + body + src[b:])
    CATH_BOXES["_rose"] = (rx, ry)
    CATH_BOXES["_door"] = (dx, dy)


def load_boxes():
    """Read the cathedral rects back from LobbyBackdrop.java (for --previews-only)."""
    path = os.path.join(REPO, "bar/src/main/java/com/solegendary/reignofnether/hud/LobbyBackdrop.java")
    src = open(path).read()
    import re
    m = re.search(r"CATH_RECT = \{(.*?)\};\n", src)
    rows = re.findall(r"\{([^{}]*)\}", m.group(1))
    for (nm, *_), row in zip(CATH_LAYERS, rows):
        CATH_BOXES[nm] = tuple(float(v.strip().rstrip("f")) for v in row.split(","))
    m = re.search(r"ROSE_X = ([-\d.]+)f, ROSE_Y = ([-\d.]+)f, DOOR_X = ([-\d.]+)f, DOOR_Y = ([-\d.]+)f", src)
    CATH_BOXES["_rose"] = (float(m.group(1)), float(m.group(2)))
    CATH_BOXES["_door"] = (float(m.group(3)), float(m.group(4)))


def zg(y):
    """LobbyBackdrop.zg: the ground plane's parallax depth at artboard y."""
    d = max(0.3, (y - 0.615) / 0.072)
    return min(0.062, 0.029 * d ** 0.6)


def compose(W, H, t=0.0, lobby=False, cam=(0.0, 0.0), motion=True, title=False):
    """The scene as LobbyBackdrop.draw paints it (no sway; the ground and fields in 24 depth bands)."""
    L = {f[:-4]: Image.open(os.path.join(OUT, f)) for f in os.listdir(OUT) if f.endswith(".png")}
    S = Screen(W, H)
    cx_, cy_ = cam
    zoom = 1.05 if motion else 1.0
    # render at the zoomed size about the centre: enlarge A and recentre
    S.A *= zoom
    S.top = H / 2 - (H / 2 - (H - S.A / zoom)) * zoom
    ox = lambda z: -cx_ * z * S.A
    oy = lambda z: -cy_ * z * 0.4 * S.A
    xs = [0.0]                       # LobbyBackdrop.xs: the title layout's shifts (sky left, cathedral right)
    sx = lambda x: W / 2 + (x + xs[0]) * S.A
    sy = lambda y: S.top + y * S.A
    shift = min(0.22, max(0.0, ART_W / 2 - W / (2 * S.A) - (0.065 if motion else 0.0))) if title else 0.0
    xs[0] = -shift

    def art(im, rect, z, **kw):
        x0, y0, x1, y1 = rect
        S.place(im, sx(x0) + ox(z), sy(y0) + oy(z), sx(x1) + ox(z), sy(y1) + oy(z), **kw)

    def banded(im, rect, rows=24, **kw):
        x0, y0, x1, y1 = rect
        a = im.convert("RGBA")
        for j in range(rows):
            va, vb = j / rows, (j + 1) / rows
            ya, yb = y0 + (y1 - y0) * va, y0 + (y1 - y0) * vb
            z = zg((ya + yb) / 2)
            crop = a.crop((0, int(round(va * a.height)), a.width, int(round(vb * a.height))))
            S.place(crop, sx(x0) + ox(z), sy(ya) + oy(z), sx(x1) + ox(z), sy(yb) + oy(z) + 1.5, **kw)

    def wrapped(im, y0, y1, drift, z, alpha):
        S.place(im, 0, sy(y0) + oy(z), 0, sy(y1) + oy(z), alpha=alpha, wrap_dx=drift * S.A - ox(z), wrap_w=ART_W * S.A)

    S.place(L["sky"], sx(-ART_W / 2) + ox(0.003), sy(0) + oy(0.003), sx(ART_W / 2) + ox(0.003), sy(1) + oy(0.003))
    S.shift = shift
    sp = L["sparkle"]
    for i, (x, y) in enumerate(STARS):
        tw_ = 0.35 + 0.65 * max(0.0, math.sin(t * (0.7 + 0.13 * i) + i * 1.7)) ** 3
        sz = S.A * (0.012 + 0.006 * (i % 3))
        S.place(sp, sx(x) - sz + ox(0.004), sy(y) - sz + oy(0.004), sx(x) + sz + ox(0.004), sy(y) + sz + oy(0.004),
                mode="add", tint=(1, 0.95, 0.88), alpha=tw_)
    mx, my, mr = MOON
    hz = mr * 2.9
    art(L["glow"], (mx - hz, my - hz, mx + hz, my + hz), 0.005, mode="add", tint=(1, 0.82, 0.62), alpha=0.32)
    art(L["moon"], (mx - mr, my - mr, mx + mr, my + mr), 0.005)
    wrapped(L["clouds_far"], 0.28, 0.80, t * 0.006, 0.007, 1.0)
    art(L["spires_far"], RECTS["spires_far"], 0.011)
    wrapped(L["clouds_near"], 0.46, 0.84, t * 0.014, 0.012, 1.0)
    breath = 0.5
    xs[0] = shift
    for i, (nm, z, hz_) in enumerate(CATH_LAYERS):
        r = CATH_BOXES[nm]
        art(L[nm], r, z)
        art(L[nm + "_rim"], r, z, mode="add", alpha=0.22 + 0.38 * breath)
        if nm + "_glow" in L:
            art(L[nm + "_glow"], r, z, mode="add", alpha=0.30 + 0.30 * breath)
        if i == 1:
            wrapped(L["mist"], 0.50, 0.72, t * 0.006 + 0.3, 0.019, 0.30 + 0.15 * breath)
        if i == 3:
            wrapped(L["mist"], 0.56, 0.76, t * 0.008 + 0.7, 0.027, 0.28 + 0.15 * breath)
    rx, ry = CATH_BOXES["_rose"]
    dx, dy = CATH_BOXES["_door"]
    # the shafts (a fan from the rose): drawn as a warped quad
    sh = np.asarray(L["shafts"].convert("RGBA"), np.float32) / 255
    x_t, y_t = sx(rx) + ox(0.036), sy(ry) + oy(0.036)
    xl, xr, yb = sx(rx - 0.22) + ox(0.045), sx(rx + 0.20) + ox(0.045), sy(0.86) + oy(0.045)
    hw = 0.03 * S.A
    yy0, yy1 = int(max(0, y_t)), int(min(H, yb))
    for yy in range(yy0, yy1):
        v = (yy - y_t) / (yb - y_t)
        xa = (x_t - hw) + (xl - (x_t - hw)) * v
        xb = (x_t + hw) + (xr - (x_t + hw)) * v
        if xb - xa < 2:
            continue
        xs_ = np.arange(max(0, int(xa)), min(W, int(xb)))
        if len(xs_) == 0:
            continue
        u = (xs_ - xa) / (xb - xa)
        px = sh[min(sh.shape[0] - 1, int(v * sh.shape[0])), np.clip((u * sh.shape[1]).astype(int), 0, sh.shape[1] - 1)]
        S.img[yy, xs_] += px[:, :3] * px[:, 3:4] * (0.20 + 0.22 * breath)
    hr = 0.10 * (0.9 + 0.2 * breath)
    S.place(L["glow"], sx(dx - hr) + ox(0.04), sy(dy - hr * 0.7) + oy(0.04), sx(dx + hr) + ox(0.04), sy(dy + hr * 0.7) + oy(0.04),
            mode="add", tint=(1, 0.55, 0.3), alpha=0.10 + 0.20 * breath)
    wrapped(L["mist"], 0.60, 0.80, t * 0.01 + breath * 0.012, 0.031, 0.55 + 0.30 * breath)
    banded(L["ground"], RECTS["ground"])
    # the cascade (slanted trapezoid)
    cxt, ty, thw, cxb, by, bhw = CASCADE
    ca = np.asarray(L["cascade"].convert("RGBA"), np.float32) / 255
    pa, pb = sy(ty) + oy(zg(ty)), sy(by) + oy(zg(by))
    for yy in range(int(pa), int(pb)):
        k = (yy - pa) / (pb - pa)
        yv = ty + (by - ty) * k
        xc = sx(cxt + (cxb - cxt) * k) + ox(zg(yv))
        hwp = (thw + (bhw - thw) * k) * S.A
        xs_ = np.arange(max(0, int(xc - hwp)), min(W, int(xc + hwp)))
        if len(xs_) == 0 or not (0 <= yy < H):
            continue
        u = (xs_ - (xc - hwp)) / (2 * hwp)
        vv = (k * CASCADE_TILES - t * CASCADE_SPEED) % 1.0
        px = ca[int(vv * (ca.shape[0] - 1)), np.clip((u * ca.shape[1]).astype(int), 0, ca.shape[1] - 1)]
        al = px[:, 3:4] * 0.95
        S.img[yy, xs_] = S.img[yy, xs_] * (1 - al) + px[:, :3] * al
    sample_strip(S, L["ripples"], t, "ripples", ox)
    sample_strip(S, L["streak"], t, "streak", ox)
    S.place(L["mist_pool"], sx(-1.0) + ox(0.03), sy(0.655), sx(-0.12) + ox(0.03), sy(0.785), alpha=0.55 + 0.35 * breath)
    S.place(L["mist_pool"], sx(-0.55) + ox(0.045), sy(0.78), sx(0.62) + ox(0.045), sy(0.88), alpha=(0.55 + 0.35 * breath) * 0.35)
    banded(L["field_far"], RECTS["field_far"])
    banded(L["field_near"], RECTS["field_near"])
    bt = L["bats"]
    fw = bt.width // 3
    for i, (x, y, f, s) in enumerate(((-0.02, 0.17, 0, 0.042), (0.06, 0.21, 2, 0.034), (0.62, 0.12, 1, 0.03))):
        fr = bt.crop((f * fw, 0, (f + 1) * fw, bt.height))
        art(fr, (x - s, y - s / 2, x + s, y + s / 2), 0.014)
    fwpx = FRAME_W * S.A
    off = 0.03 * S.A
    for part, z in (("_tree", 0.055), ("", 0.065), ("_vines", 0.075)):
        S.place(L["frame_left" + part], -off + ox(z), H - S.A, fwpx - off + ox(z), H)
        S.place(L["frame_right" + part], W - fwpx + off + ox(z), H - S.A, W + off + ox(z), H)
    pt = L["petal"]
    r = np.random.default_rng(5)
    for i in range(10):
        x, y = r.uniform(-0.8, 0.8), r.uniform(0.35, 0.95)
        s = 0.008 + 0.006 * r.random()
        z = 0.05 + 0.06 * r.random()
        art(pt.rotate(r.uniform(0, 360), expand=True), (x - s, y - s, x + s, y + s), z)
    for i in range(8):                     # embers over the door
        x, y = dx + r.uniform(-0.03, 0.08), dy - r.uniform(0.0, 0.2)
        s = 0.006
        art(sp, (x - s, y - s, x + s, y + s), 0.06, mode="add", tint=(1, 0.55, 0.22), alpha=0.8)
    A_ = S.A
    oyf = oy(0.10)
    S.place(L["fg_briar"], -0.12 * A_ + ox(0.13), H - 0.44 * A_ + oyf, 0.54 * A_ + ox(0.13), H + 0.02 * A_ + oyf)
    S.place(L["fg_thorns"], W - 0.30 * A_ + ox(0.12), H - 0.30 * A_ + oyf, W + 0.10 * A_ + ox(0.12), H + 0.02 * A_ + oyf)
    S.place(L["fg_branch"], -0.08 * A_ + ox(0.11), -0.06 * A_ + oyf, 0.34 * A_ + ox(0.11), 0.36 * A_ + oyf)
    S.place(L["vignette"], 0, 0, S.W, S.H)
    g = np.asarray(L["grain"], np.float32) / 255
    reps = (int(math.ceil(S.H / g.shape[0])), int(math.ceil(S.W / g.shape[1])))
    gt = np.tile(g, (reps[0], reps[1], 1))[:S.H, :S.W]
    S.img = S.img * (1 - gt[..., 3:4]) + gt[..., :3] * gt[..., 3:4]
    if lobby:
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
    m = smoothstep(0.22, 0.78, m)
    base = lerp(col((30, 25, 24)), col((88, 76, 68)), m)
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
        X0, Y0 = max(0, round(x0 * g)), max(0, round(y0 * g))
        X1, Y1 = min(self.img.width, round(x1 * g)), min(self.img.height, round(y1 * g))
        if X1 > X0 and Y1 > Y0:
            self.img.alpha_composite(Image.new("RGBA", (X1 - X0, Y1 - Y0), c), (X0, Y0))

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
            for i in range(5, 0, -1):
                al = int((22 + 26 * 0.8) * (6 - i) / 5)
                cv.fill(x0 - i - 1, y0 - i, x1 + i + 1, y1 + i, (al << 24) | 0xB00A18)
        return
    if pas == 1:
        tex = tex or Image.open(os.path.join(OUT, "plate.png")).convert("RGB")
        uw, vh = min(1024, bw * 4), min(96, bh * 4)
        u = (_h32(_jhash(label)) % max(1, (1024 - uw) // 4)) * 4
        lit = 0.55 if not active else 1.18 if hot else 1.0
        cv.blit(tex, x0, y0, bw, bh, u, (96 - vh) / 2, uw, vh, (lit, lit * (0.93 if hot else 1), lit * (0.92 if hot else 1)))
        for r_ in ((x0, y0, x1, y0 + 1), (x0, y1 - 1, x1, y1), (x0, y0, x0 + 1, y1), (x1 - 1, y0, x1, y1)):
            cv.fill(*r_, 0xFF050304)
        cv.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0xE0C8B49C if hot else 0xC0A89886)
        cv.fill(x0 + 1, y0 + 2, x1 - 1, y0 + 3, 0x50A09080)
        cv.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 2, 0x806E625A)
        cv.fill(x0 + 1, y1 - 3, x1 - 1, y1 - 1, 0xD0080506)
        cv.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 1, 0xA0080506)
        cv.fill(x0 + 4, y0 + 4, x1 - 4, y0 + 5, 0x90000000)
        cv.fill(x0 + 4, y0 + 5, x0 + 5, y1 - 5, 0x60000000)
        cv.fill(x0 + 4, y1 - 5, x1 - 4, y1 - 4, 0x58C0A890)
        if hot:
            cv.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, 0x50B01020)
        gold = 0xFF5C4A2C if not active else 0xFFF0CC72 if hot else 0xFFB89040
        cv.outline(x0 + 3, y0 + 4, bw - 6, bh - 7, 0xC0000000)
        cv.outline(x0 + 3, y0 + 3, bw - 6, bh - 7, gold)
        if active:
            stud = 0xFFFFE6A0 if hot else 0xFFD8B060
            for sx, sy in ((x0 + 2, y0 + 2), (x1 - 5, y0 + 2), (x0 + 2, y1 - 6), (x1 - 5, y1 - 6)):
                cv.fill(sx, sy, sx + 3, sy + 3, stud)
                cv.fill(sx + 1, sy + 1, sx + 2, sy + 2, 0xFF5A3A14)
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


def _plate_drips(cv, x0, y, bw, label, t, hot, burst=False):
    """GothicText.plateDrips (mirrors the Java)."""
    BLOOD, DARK, HI = 0xFFA8101C, 0xFF5A0610, 0xFFF06070

    def strand(x, L, wd):
        if L <= 0:
            return
        cv.fill(x - 1, y, x + wd + 1, y + 2, BLOOD)
        cv.fill(x, y, x + wd, y + L, BLOOD)
        cv.fill(x + wd - 1, y + 2, x + wd, y + L, DARK) if wd > 1 else None
        cv.fill(x - 1, y + L - 1, x + 2, y + L + 2, BLOOD)
        cv.fill(x - 1, y + L - 1, x, y + L, HI)
        cv.fill(x + 1, y + L + 1, x + 2, y + L + 2, DARK)
        cv.fill(x, y + L + 2, x + 1, y + L + 3, DARK)

    n = 4 if bw >= 150 else 3 if bw >= 90 else 2
    seed = (_jhash(label) * 31 + 7) & 0xFFFFFFFF
    maxlen = 12.0 if hot else 7.0
    for i in range(n):
        h = _h32((seed + i * 7919) & 0xFFFFFFFF)
        fx = (i + 0.25 + 0.5 * ((h >> 3) % 1000) / 1000) / n
        sx = x0 + 6 + round(fx * (bw - 12))
        wd = 2 if h % 3 == 0 else 1
        ln = maxlen * (0.55 + 0.45 * (_h32(h + 3) % 100) / 100)
        cv.fill(sx - 2 - h % 5, y - 1, sx + wd + 2 + (h >> 5) % 5, y, BLOOD)
        period = (3 + 3 * (h % 1000) / 1000) / (3 if hot else 1)
        p = (t / period + (_h32(h) % 1000) / 1000) % 1
        if burst:
            p = max(p, 0.70 + 0.30 * ((t * 2.5 + i * 0.21) % 1))
        if p < 0.55:
            k = p / 0.55
            strand(sx, round(ln * (0.35 + 0.65 * k * k * (3 - 2 * k))), wd)
        elif p < 0.70:
            L = round(ln)
            strand(sx, L, wd)
            if (p - 0.55) / 0.15 > 0.5:
                cv.fill(sx - 1, y + L + 2, sx + 2, y + L + 3, BLOOD)
        else:
            k = (p - 0.70) / 0.30
            strand(sx, round(ln * (0.35 + 0.15 * (1 - k))), wd)
            dy = round(ln + 4 + k * k * 22)
            da = round(255 * (1 - k * k))
            cv.fill(sx, y + dy - 1, sx + 1, y + dy, (da << 24) | (BLOOD & 0xFFFFFF))
            cv.fill(sx - 1, y + dy, sx + 2, y + dy + 2, (da << 24) | (BLOOD & 0xFFFFFF))
            cv.fill(sx - 1, y + dy, sx, y + dy + 1, (da << 24) | (HI & 0xFFFFFF))


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
    for gs in (2, 4):
        bg = title_screen(1920, 1080, gs=gs)
        gw, gh = 1920 // gs, 1080 // gs
        cx, y = gw // 2, gh // 4 + 48
        box = ((cx - 116) * gs, (y - 34) * gs, (cx + 116) * gs, (y + 84 + 34) * gs)
        if gs == 2:
            bg.save(os.path.join(DESIGN, "title_preview_16x9.png"), optimize=True)
        else:
            bg.save(os.path.join(DESIGN, "title_preview_16x9_gui4.png"), optimize=True)
        bg.crop(box).save(os.path.join(DESIGN, f"lobby_buttons_gui{gs}.png"), optimize=True)
    loading_screen(1920, 1080).save(os.path.join(DESIGN, "loading_preview_16x9.png"), optimize=True)

    gs = 2
    gh = 1080 // gs
    S = compose(1920, 1080, lobby=True)
    img = Image.fromarray(np.round(np.clip(S.img, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")
    pw = 620
    x0 = (960 // gs - pw // 2)
    top = max(24, gh // 2 - 150)
    _blood_text(img, "SKIRMISH", CAPS_TTF, 15, x0 * gs, (top - 21) * gs, gs,
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


TITLE_Y0, TITLE_BLOCK_H, CREDIT_Y0, CREDIT_H, CREDIT_GAP = 0.034, 0.150, 0.220, 0.037, 1.4     # LobbyBackdrop.lettering


def _over(img, tex, x0, y0, x1, y1, tint=None, alpha=1.0):
    """Alpha-composite a PIL RGBA texture into the float image (screen px), optionally tinted (rgb) and faded."""
    S = Screen(img.shape[1], img.shape[0])
    S.img = img
    S.place(tex, x0, y0, x1, y1, tint=tint or (1, 1, 1), alpha=alpha)


def lettering(img, t=1.3, motion=True):
    """LobbyBackdrop.lettering on a float image (title layout): the title, the byline, the strands and their drops."""
    sys.path.insert(0, HERE)
    import gen_lobby_title as GT
    ti, bi, ji = GT.read_java()
    H, W = img.shape[:2]
    T = Image.open(os.path.join(OUT, "title.png")).convert("RGBA")
    B = Image.open(os.path.join(OUT, "byline.png")).convert("RGBA")
    J = Image.open(os.path.join(OUT, "credit_joisco.png")).convert("RGBA")
    sc = Image.open(os.path.join(OUT, "scrim.png")).convert("RGBA")
    th = TITLE_BLOCK_H * H / (ti["bot_v"] - ti["top_v"])
    tw = th * T.width / T.height
    if tw * ti["text_w"] > 0.86 * W:
        k = 0.86 * W / (tw * ti["text_w"]); th *= k; tw *= k
    tx0, ty0 = W / 2 - tw / 2, TITLE_Y0 * H - ti["top_v"] * th
    ch = CREDIT_H * H / (bi["bot_v"] - bi["top_v"])
    lw, jw, gap = ch * B.width / B.height, ch * J.width / J.height, CREDIT_GAP * CREDIT_H * H
    text_w = lw * bi["text_w"] + gap + jw * ji["text_w"]
    if text_w > 0.92 * W:
        k = 0.92 * W / text_w; ch *= k; lw *= k; jw *= k; gap *= k; text_w *= k
    cy0 = CREDIT_Y0 * H - bi["top_v"] * ch
    l_text0 = W / 2 - text_w / 2
    j_text0 = l_text0 + lw * bi["text_w"] + gap
    lx0, jx0 = l_text0 - lw * (1 - bi["text_w"]) / 2, j_text0 - jw * (1 - ji["text_w"]) / 2
    pad = max(tw * ti["text_w"], text_w) * 0.58
    _over(img, sc, W / 2 - pad, TITLE_Y0 * H - 0.05 * H, W / 2 + pad, CREDIT_Y0 * H + CREDIT_H * H + 0.05 * H,
          tint=(0, 0, 0), alpha=0.5)
    _over(img, T, tx0, ty0, tx0 + tw, ty0 + th)
    _over(img, B, lx0, cy0, lx0 + lw, cy0 + ch)
    _over(img, J, jx0, cy0, jx0 + jw, cy0 + ch)
    mx, my, lz = l_text0 + lw * bi["text_w"] + gap / 2, CREDIT_Y0 * H + CREDIT_H * H * 0.5, CREDIT_H * H * 0.16
    yy, xx = np.mgrid[0:H, 0:W]
    dd = ((xx - mx) / (lz * 1.5)) ** 2 + ((yy - my) / (lz * 1.5)) ** 2
    img[dd < 1] = img[dd < 1] * 0.2 + np.array((0.05, 0.02, 0.03), np.float32) * 0.8
    dd = ((xx - mx) / (lz * 0.75)) ** 2 + ((yy - my) / lz) ** 2
    img[dd < 1] = np.array((0.86, 0.70, 0.36), np.float32)
    pil = Image.fromarray(np.round(np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")
    lay = Image.new("RGBA", (W * 4, H * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    for i, (u, v, hw, reach) in enumerate(ti["anchors"]):
        x, y, hw, reach = (tx0 + u * tw) * 4, (ty0 + v * th) * 4, max(0.6, hw * th) * 4, reach * th * 4
        frac_ = lambda q: q - math.floor(q)
        drop = -1
        if not motion:
            ln = reach * (0.3 + 0.3 * frac_(i * 0.618))
        else:
            per = 4.5 + 3 * frac_(i * 0.618 + 0.1)
            p_ = frac_(t / per + i * 0.37)
            if p_ < 0.8:
                q = p_ / 0.8; ln = reach * (0.12 + 0.88 * q * q * (3 - 2 * q))
            else:
                q = (p_ - 0.8) / 0.2; ln = reach * (0.12 + 0.88 * (1 - q) ** 3); drop = q
        tip = y + ln
        d.polygon([(x - hw, y), (x - hw * 0.55, tip), (x + hw * 0.55, tip), (x + hw, y)], fill=(117, 6, 15, 255))
        r = hw * 1.25
        d.ellipse([x - r, tip + r * 0.3 - r * 1.2, x + r, tip + r * 0.3 + r * 1.2], fill=(133, 6, 16, 255))
        d.ellipse([x - r * 0.65, tip - r * 0.3, x - r * 0.05, tip + r * 0.3], fill=(255, 153, 153, 200))
        if drop >= 0:
            dy = y + reach + hw + drop * drop * 0.12 * th * 4
            rr = hw * 1.15 * (1 - 0.3 * drop)
            d.ellipse([x - rr, dy - rr * 1.35, x + rr, dy + rr * 1.35], fill=(128, 5, 13, int(255 * (1 - drop * drop))))
    pil.alpha_composite(lay.resize((W, H), Image.LANCZOS))
    img[:] = np.asarray(pil.convert("RGB"), np.float32) / 255


def title_screen(W=1920, H=1080, gs=2, t=1.3):
    """The title screen as TitleScreenMixin draws it: the cathedral in the title layout, the lettering, the soft pad
    behind the button column and the stone-plate buttons (GUI scale gs). Returns a PIL RGB image."""
    S = compose(W, H, t=t, title=True)
    lettering(S.img, t)
    gw, gh = W // gs, H // gs
    y = gh // 4 + 48
    x0, y0, x1, y1 = (gw // 2 - 100) * gs, (y - 26) * gs, (gw // 2 + 100) * gs, (y + 104) * gs
    fx, fy = (x1 - x0) * 0.30 + 14 * gs, (y1 - y0) * 0.30 + 14 * gs
    _over(S.img, Image.open(os.path.join(OUT, "scrim.png")).convert("RGBA"), x0 - fx, y0 - fy, x1 + fx, y1 + fy,
          tint=(0, 0, 0), alpha=0.62)
    img = Image.fromarray(np.round(np.clip(S.img, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")
    title_buttons(img, gs, t=t)
    return img.convert("RGB")


def loading_screen(W=1920, H=1080, progress=0.62, t=1.3):
    """SplashArt.renderLoading: the cathedral (title layout, lettering) and the gold progress rule on its pad."""
    S = compose(W, H, t=t, title=True)
    lettering(S.img, t)
    cx, bw, by, bt = W / 2, H * 0.55, H * 0.905, max(2.0, H * 0.006)
    fx, fy = bw * 0.30 + 14, bt * 8 * 0.30 + 14
    _over(S.img, Image.open(os.path.join(OUT, "scrim.png")).convert("RGBA"), cx - bw / 2 - fx, by - bt * 4 - fy,
          cx + bw / 2 + fx, by + bt * 4 + fy, tint=(0, 0, 0), alpha=0.8)
    im = S.img
    def fill(x0, y0, x1, y1, c):
        im[int(round(y0)):int(round(y1)), int(round(x0)):int(round(x1))] = np.array(c, np.float32)
    fill(cx - bw / 2 - bt, by - bt * 1.5, cx + bw / 2 + bt, by + bt * 1.5, (0.75, 0.58, 0.27))
    fill(cx - bw / 2, by - bt * 0.75, cx + bw / 2, by + bt * 0.75, (0.07, 0.05, 0.04))
    fill(cx - bw / 2, by - bt * 0.75, cx - bw / 2 + bw * progress, by + bt * 0.75, (0.96, 0.80, 0.42))
    return Image.fromarray(np.round(np.clip(im, 0, 1) * 255).astype(np.uint8), "RGB")


def previews():
    if "cath_back" not in CATH_BOXES:
        load_boxes()
    compose(1920, 1080, t=2.0).save(os.path.join(DESIGN, "lobby_preview_16x9.png"))
    compose(2520, 1080, t=2.0).save(os.path.join(DESIGN, "lobby_preview_21x9.png"))
    # three moments of the camera drift side by side: the depth layers slide against each other
    fr = [compose(960, 540, t=2.0, cam=c).img for c in ((-1.0, -0.5), (0.0, 0.0), (1.0, 0.5))]
    sep = np.ones((540, 6, 3), np.float32)
    big = np.concatenate([fr[0], sep, fr[1], sep, fr[2]], 1)
    Image.fromarray(np.round(np.clip(big, 0, 1) * 255).astype(np.uint8), "RGB").save(
        os.path.join(DESIGN, "lobby_parallax_3frames.png"), optimize=True)
    if "--no-text" not in sys.argv:
        text_previews()


SMOOTH = {"title", "byline", "credit_joisco", "scrim", "plate", "glow", "mist", "vignette", "grain", "sparkle", "ring", "streak", "ripples", "shafts", "mist_pool",
          "fg_briar", "fg_thorns", "fg_branch"}


def compact():
    """Palette the textured layers (256 colours, Floyd-Steinberg): the pencil grain hides the quantisation and the files
    shrink 3-5x. Minecraft's NativeImage (stb_image) reads paletted PNGs with tRNS alpha. Smooth glows stay RGBA."""
    for f in sorted(os.listdir(OUT)):
        nm = f[:-4]
        path = os.path.join(OUT, f)
        if nm in SMOOTH or nm.endswith(("_rim", "_glow")) or nm.startswith("cath_") or os.path.getsize(path) < 120_000:
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
    if LAYOUT:
        global CATH_PX
        cathedral()
        S = Screen(1920, 1080)
        S.img[:] = col((70, 14, 18))
        mx, my, mr = MOON
        yy, xx = np.mgrid[0:1080, 0:1920]
        d = np.hypot(xx - S.sx(mx), yy - S.sy(my))
        S.img[d < mr * S.A] = col((200, 190, 160))
        for nm, z, hz in CATH_LAYERS:
            r = CATH_BOXES[nm]
            S.place(Image.open(os.path.join(OUT, nm + ".png")), S.sx(r[0]), S.sy(r[1]), S.sx(r[2]), S.sy(r[3]))
        S.save(os.path.join(SCRATCH, "layout.png"))
        return
    if "--previews-only" not in sys.argv:
        only = [a[7:] for a in sys.argv if a.startswith("--only=")]
        steps = {"sky": sky, "moon": moon, "clouds": all_clouds, "spires": spires_far, "cathedral": cathedral,
                 "ground": ground, "fields": fields, "frames": frames, "sprites": sprites, "plate": plate}
        for k, fn in steps.items():
            if not only or k in only[0].split(","):
                print("painting", k, flush=True)
                fn()
                if k in ("cathedral", "ground"):
                    if "cath_back" not in CATH_BOXES:
                        load_boxes()
                    write_java_cath()
        hand_drawn_all(only[0].split(",") if only else None)
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
