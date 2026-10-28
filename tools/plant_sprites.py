"""Grass plants for the realistic pack (lovish Oct 28: "we might need more grass or more realistic grass to match";
plan item 1, his "yes" at 03:35): short grass, tall grass (bottom + top), fern and large fern (bottom + top), drawn
as tapered blade strokes instead of vanilla's 16x pixel tufts.

Each sprite is GREYSCALE (the biome grass colour tints it in game, so GrassTint's dry / lush patches carry onto the
plants) with a cut-out alpha. Rules, from the block pack's lessons (tools/gen_blocks.py, design/ART-FAULTS.md):
- Readable when minified: blades are >= 4 texels wide at the base at 128x (a thread survives mip 2, the RTS zoom),
  tips taper to ~1 texel (they may fade out with distance - real grass does too).
- Form light baked in, no flat fill: each blade is darker at the base (the clump's own shade, AO ~0.55) and lighter
  toward the tip, with a pale midrib and darker edges; blades in front are a touch lighter than those behind.
- Value range inside 55..220 after the fit; a few blades lighter and drier (they read as straw under the tint).
- Clumps, not wallpaper: blades rise from 1-3 roots near the bottom middle and fan out with a lean, varied heights,
  some crossing; nothing touches the tile's side edges much (a cross model shows the sprite twice, crossed).

The drawing works at 2x and is box-filtered down, so edges are anti-aliased in the alpha before the cut-out threshold.
Returns float arrays: (H height 0..1, grey albedo HxWx3, alpha 0/1). Two-block plants are drawn on one 2n-tall canvas
and split, so blades run on from the bottom texture into the top one.
"""
import math

import numpy as np

SS = 2   # supersampling


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w * SS, h * SS
        self.cov = np.zeros((self.h, self.w))
        self.shade = np.zeros((self.h, self.w))
        self.height = np.zeros((self.h, self.w))
        self.layer = np.zeros((self.h, self.w))     # 0 = green (tinted), 1 = straw (untinted, its own colour)

    def stroke(self, pts, widths, shade_fn, depth, layer=0):
        """A tapered stroke through pts (list of (x, y) in output texels, y down), width per point (texels). Paints
        coverage, shade (shade_fn(t, s) with t 0..1 along, s -1..1 across) and height, over what is there (painter's
        order: later strokes are in front)."""
        pts = np.asarray(pts, float) * SS
        widths = np.asarray(widths, float) * SS
        seg = np.linalg.norm(np.diff(pts, axis=0), axis=1)
        total = seg.sum()
        if total <= 0:
            return
        steps = max(2, int(total * 1.5))
        cum = np.concatenate([[0], np.cumsum(seg)])
        ts = np.linspace(0, total, steps)
        xs = np.interp(ts, cum, pts[:, 0])
        ys = np.interp(ts, cum, pts[:, 1])
        ws = np.interp(ts, cum, widths)
        dx, dy = np.gradient(xs), np.gradient(ys)
        ln = np.maximum(np.hypot(dx, dy), 1e-6)
        nx, ny = -dy / ln, dx / ln
        for i in range(steps):
            r = ws[i] / 2
            if r < 0.35:
                continue
            x0, x1 = int(max(0, math.floor(xs[i] - r - 1))), int(min(self.w, math.ceil(xs[i] + r + 2)))
            y0, y1 = int(max(0, math.floor(ys[i] - r - 1))), int(min(self.h, math.ceil(ys[i] + r + 2)))
            if x0 >= x1 or y0 >= y1:
                continue
            gy, gx = np.mgrid[y0:y1, x0:x1] + 0.5
            ox, oy = gx - xs[i], gy - ys[i]
            across = (ox * nx[i] + oy * ny[i]) / max(r, 1e-6)          # -1..1 across the blade
            d = np.hypot(ox, oy)
            m = d <= r
            if not m.any():
                continue
            t = ts[i] / total
            sh = shade_fn(t, np.clip(across, -1, 1))
            sub = (slice(y0, y1), slice(x0, x1))
            self.cov[sub] = np.where(m, 1.0, self.cov[sub])
            self.shade[sub] = np.where(m, sh, self.shade[sub])
            hh = depth * (1 - 0.6 * np.abs(np.clip(across, -1, 1)))      # a rounded blade, crest on the midrib
            self.height[sub] = np.where(m, hh, self.height[sub])
            self.layer[sub] = np.where(m, float(layer), self.layer[sub])

    def result(self):
        def down(a):
            return a.reshape(self.h // SS, SS, self.w // SS, SS).mean((1, 3))
        cov = down(self.cov)
        alpha = (cov >= 0.5).astype(np.float64)
        w = np.maximum(down(self.cov), 1e-6)
        shade = down(self.shade * self.cov) / w
        height = down(self.height * self.cov) / w
        return np.where(alpha > 0, height, 0.0), np.where(alpha > 0, shade, 0.0), alpha

    def layers(self):
        """(H, shade, green alpha, straw alpha): the cut-out split by which layer owns each texel (the majority of
        its covered sub-samples), so the two masks never overlap - the green (tinted) and straw (untinted) quads are
        coplanar and would z-fight where both are opaque."""
        H, shade, alpha = self.result()
        cov = self.cov.reshape(self.h // SS, SS, self.w // SS, SS).sum((1, 3))
        straw = (self.layer * self.cov).reshape(self.h // SS, SS, self.w // SS, SS).sum((1, 3))
        is_straw = straw * 2 > np.maximum(cov, 1e-6)
        return H, shade, alpha * (~is_straw), alpha * is_straw


def _blade_shade(base, tip, tone, dry):
    """Shade along a blade: dark at its root, lighter to the tip; pale midrib, darker edges (rounded, lit from the
    upper left: the left edge a little lighter than the right)."""
    def fn(t, s):
        along = base + (tip - base) * (t ** 0.7)
        rib = 0.10 * np.exp(-(s / 0.28) ** 2)
        edge = -0.10 * np.abs(s) ** 2 - 0.035 * s
        return (along + rib + edge) * tone * (1.18 if dry else 1.0)
    return fn


def _spike_shade(t, s):
    """A seed head: pale, its spikelets as bumps along it (a rough, grainy spike, not a smooth spindle)."""
    return (0.84 + 0.14 * t + 0.10 * np.sin(t * 46.0 + 2.0 * s) - 0.10 * np.abs(s) ** 2) * 1.12


def _curve(x0, y0, length, ang, bend, n_pts=14, rng=None, wobble=0.0):
    """Points of a blade from its root (x0, y0) going up at angle ang (radians from vertical, + = right), bending
    further by `bend` radians over its length (gravity / a lean), with a slight wobble."""
    pts = []
    x, y = x0, y0
    step = length / (n_pts - 1)
    for i in range(n_pts):
        pts.append((x, y))
        t = i / (n_pts - 1)
        a = ang + bend * t * t + (wobble * (rng.random() - 0.5) if rng is not None else 0)
        x += math.sin(a) * step
        y -= math.cos(a) * step
    return pts


def _fit(x0, y0, length, ang, bend, n_pts, w, rng=None, wobble=0.0, margin=2.0):
    """_curve, shortened until the whole blade stays inside the tile's width [margin, w - margin] and above its top:
    a blade cut off by the tile edge shows as a hard vertical line where the crossed quads meet."""
    for _ in range(12):
        pts = _curve(x0, y0, length, ang, bend, n_pts, rng, wobble)
        if all(margin <= x <= w - margin and y >= margin for x, y in pts):
            return pts
        length *= 0.85
    return pts


def grass_clump(n, rng, tall=False, blades=None):
    """Short grass (one tile) or tall grass (a 2n-tall canvas): blades from 1-3 roots near the bottom middle fanning
    out; tall grass gets longer blades and some seed heads (a panicle of spikelets) above the leaves."""
    h = 2 * n if tall else n
    cv = Canvas(n, h)
    roots = [(n * (0.5 + 0.30 * (rng.random() - 0.5)), h - 1) for _ in range(rng.integers(3, 5))]
    count = blades or (int(rng.integers(34, 42)) if tall else int(rng.integers(38, 48)))
    lean = math.copysign(0.17 + 0.09 * rng.random(), rng.random() - 0.5)   # the clump leans 10-15 degrees (critic r1)
    order = []
    for _ in range(count):
        rx, ry = roots[rng.integers(len(roots))]
        rx += (rng.random() - 0.5) * n * 0.20
        ang = (rng.random() - 0.5) * 1.15 + lean                # fan: about +-33 degrees round the lean
        ang += 0.25 * math.copysign(rng.random() ** 2, ang)      # a few lean out further
        hmax = (1.85 if tall else 0.92) * n
        length = hmax * (0.40 + 0.60 * rng.random() ** 0.8)    # 40-100%: many short blades, fewer long ones
        bend = math.copysign(0.25 + 0.75 * rng.random(), ang + 1e-3) * (0.7 if tall else 1.0)
        width = n * (0.034 + 0.018 * rng.random()) * (0.8 if tall else 1.0)   # 4-7 texels at 128x
        depth = rng.random()                                      # 0 = behind, 1 = in front
        straw = rng.random() < 0.30                               # dead blades: shorter, low in the clump (critic r1)
        if straw:
            length *= 0.55 + 0.3 * rng.random()
            depth *= 0.6
        order.append((depth, rx, ry, ang, length, bend, width, straw))
    order.sort()
    broken = set(rng.choice(len(order), size=min(len(order), int(rng.integers(2, 4))), replace=False).tolist())
    for j, (depth, rx, ry, ang, length, bend, width, straw) in enumerate(order):
        if j in broken:                                           # a broken blade: folds over a third of the way up
            a = _fit(rx, ry, length * 0.45, ang, 0.1 * bend, 8, n, rng, 0.02)
            k_ang = ang + math.copysign(1.5 + 0.5 * rng.random(), ang + 1e-3)
            pts = a + _fit(*a[-1], length * 0.4, k_ang, 0.2, 7, n)[1:]
        else:
            pts = _fit(rx, ry, length, ang, bend, 16, n, rng, 0.04)
        k = len(pts)
        widths = [width * (1 - (i / (k - 1)) ** 1.6) * 0.92 + 0.6 for i in range(k)]
        tone = 0.90 + 0.14 * rng.random() + 0.06 * depth
        if straw:
            cv.stroke(pts, widths, _blade_shade(0.62, 1.0, tone, False), 0.3 + 0.6 * depth, layer=1)
        else:
            cv.stroke(pts, widths, _blade_shade(0.50, 0.90, tone, False), 0.35 + 0.65 * depth)
    if tall:                                                     # seed heads: a stalk and its spikelets
        for _ in range(int(rng.integers(3, 6))):
            rx, ry = roots[rng.integers(len(roots))]
            ang = (rng.random() - 0.5) * 0.4
            length = n * (1.25 + 0.35 * rng.random())
            bend = math.copysign(0.08 + 0.2 * rng.random(), ang + 1e-3)   # stalks stand nearly upright
            pts = _fit(rx, ry, length, ang, bend, 24, n, rng, 0.02, margin=n * 0.12)
            cv.stroke(pts, [n * 0.016 + 0.8] * len(pts), _blade_shade(0.6, 0.9, 1.0, False), 0.6, layer=1)
            head = _curve(*pts[-1], n * (0.16 + 0.08 * rng.random()), ang + bend, 0.08, 10)
            k = len(head)
            cv.stroke(head, [n * 0.055 * math.sin(math.pi * (0.12 + 0.8 * j / (k - 1))) + 0.8 for j in range(k)],
                      _spike_shade, 0.75, layer=1)
    return cv.layers()


def fern(n, rng, large=False):
    """Fern (one tile) or large fern (2n tall): 4-6 fronds from one crown, each an arched rachis with alternating
    pinnae that shorten toward the tip; pinnae are small tapered blades."""
    h = 2 * n if large else n
    cv = Canvas(n, h)
    cx, cy = n * 0.5, h - 1
    fronds = int(rng.integers(4, 8)) if large else int(rng.integers(3, 7))
    order = sorted(rng.random(fronds))
    for k, depth in enumerate(order):
        ang = (k / max(1, fronds - 1) - 0.5) * (0.8 if large else 1.5) + 0.15 * (rng.random() - 0.5)
        length = (1.75 if large else 0.86) * n * (0.75 + 0.25 * rng.random())
        bend = math.copysign((0.35 if large else 0.7) + 0.4 * rng.random(), ang + 1e-3)
        bend += math.copysign(0.45 * min(1.0, abs(ang) / 0.6), ang + 1e-3)   # outer fronds droop 20-30 degrees more
        P = 48
        pts = _fit(cx + (rng.random() - 0.5) * n * 0.06, cy, length, ang, bend, P, n, margin=n * 0.06)
        tone = 0.95 + 0.1 * rng.random() + 0.05 * depth
        cv.stroke(pts, [n * 0.024 * (1 - 0.7 * i / (P - 1)) + 0.8 for i in range(P)],
                  _blade_shade(0.36, 0.64, tone, False), 0.4 + 0.5 * depth)
        for i in range(5, P - 1):                                 # pinnae, alternating, overlapping
            t = i / (P - 1)
            side = 1 if i % 2 else -1
            x0, y0 = pts[i]
            x1, y1 = pts[i + 1]
            heading = math.atan2(x1 - x0, -(y1 - y0))
            a = heading + side * (0.95 + 0.15 * rng.random())
            # lanceolate: pinnae longest a third of the way up, short at the base and the tip
            pl = n * (0.20 if large else 0.16) * math.sin(math.pi * min(1.0, 0.15 + 0.95 * t)) ** 0.8 \
                * (0.85 + 0.3 * rng.random())
            if pl < 2:
                continue
            pp = _curve(x0, y0, pl, a, side * 0.35, 7)
            pw = n * (0.050 if large else 0.044) * (1 - 0.4 * t)
            cv.stroke(pp, [pw * (0.55 + 0.45 * math.sin(math.pi * min(1, j / 5 + 0.15))) * (1 - j / 7) + 0.5
                           for j in range(7)],
                      _blade_shade(0.50 + 0.20 * t, 0.98, tone, False), 0.45 + 0.5 * depth)   # dark at the rachis
    return cv.result()


def split2(res):
    """A 2n-tall tuple of arrays -> (top, bottom) halves."""
    n = res[0].shape[1]
    return tuple(a[:n] for a in res), tuple(a[n:] for a in res)
