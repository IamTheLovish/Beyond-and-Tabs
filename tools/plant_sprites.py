"""Grass plants for the realistic pack (lovish Oct 28: "we might need more grass or more realistic grass to match";
plan item 1, his "yes" at 03:35): short grass, tall grass (bottom + top), fern and large fern (bottom + top).

Round 34 (fresh critic r33: tufts 4/10 default, 6/10 with the foliage-shadow preset; claude/research-grass-rts-zoom-
oct28.md): r29-r33 drew ~40 blades 4 texels wide at 128x. The default RTS camera reads the atlas at mip 2 (32 texels a
block), where a 4-texel blade is one texel and blurs into a smudge. Now a FEW BOLD blades, the way game grass that
reads from a strategy camera is drawn:
- short grass: 5-7 blades 12-16 texels wide at the base and >= 4 at 80% of their length, from one crown, so the lowest
  ~20% is one solid wedge; three height classes (~100 / 75 / 55% of the card); a 40-60 degree fan with a lean.
  22-30% of the card opaque, 60% of it in the central 60% of the width. Tall grass: 5 blades, 14-18 texels.
- ferns: 5-6 solid fronds, the leaflets merged into scallops >= 8 texels deep, no leaflet detail. r35: the one-block
  fern is a low rosette of frond cards (FERN_CARDS, fern_card); the large fern keeps its fan (fern).
- value, in the greyscale the biome tint multiplies (the lawn is 0.56): the body a bit lighter than the lawn, the top
  ~35% lighter still, the base no darker than the lawn - 0.06, a lighter rib along each blade; no dark outlines and no
  AO ramp (gen_blocks keeps the plants' AO light).
- three short-grass shapes, two ferns and two tall grasses (GRASS, FERN, TALL), so neighbouring tufts differ.

Round 35 (critic r34: tufts 5/10 RTS, 4/10 close - "double tuft density and vary scale (3 sizes)", "ferns and tall
grass are indistinguishable"): short grass in three SIZES, the short grass's body a step lighter (BODY_SHORT), and the
fern drawn as cards for a low rosette (FERN_CARDS).

Each sprite is GREYSCALE with a cut-out alpha. Drawn at SS x SS sub-samples a texel and box-filtered, cut at 0.5; the
colour under the transparent texels is the nearest blade's (bleed: MC-114265, mipmaps pull that colour into edges).
Returns float arrays: (H height 0..1, grey shade, alpha 0/1). Two-block plants are drawn on one 2n-tall canvas and
split, so blades run on from the bottom texture into the top one.
"""
import math

import numpy as np

SS = 4   # sub-samples per texel side (r34: 2 -> 4)

# values along a blade in the greyscale the biome colour tints; the lawn (grass_block_top) is 0.56. gen_blocks
# (m_plant) only scales them so the plant's mean is TARGETS' luma (r35 short grass 0.72, ~1.0x) and sets the
# warmth's saturation: these are the final values.
BASE, BODY, TIP, RIB = 0.585, 0.66, 0.80, 0.045
# r35: the short grass's body a step lighter (in game, r34 noon stills with the shipped shader preset, the blocks holding
# a short-grass tuft drew 0.958 of the lawn's luma - blades, their gaps and their shadows; ferns 0.960)
BODY_SHORT = 0.71
# r35 (critic r34: "vary scale (3 sizes)"): short grass in three sizes, of the full tuft's height and reach. Drawn
# smaller on the same card (not a smaller model): the blades keep their texel widths, so a small tuft's blades are as
# bold at the RTS mip as a big one's (a 0.55 model would be read a mip level further down)
SIZES = (1.0, 0.8, 0.55)

# r34 shapes. pos: blades across the fan, left to right; `straw` = which of them is a dead blade (the untinted layer)
GRASS = (
    dict(blades=6, fan=52, lean=7, straw=4, width=(13, 16)),
    dict(blades=5, fan=44, lean=-5, straw=0, width=(15, 16)),
    dict(blades=7, fan=58, lean=-9, straw=5, width=(12, 14)),
)
TALL = (
    dict(blades=5, fan=24, lean=5, straw=1, width=(14, 18)),
    dict(blades=5, fan=20, lean=-6, straw=3, width=(14, 18)),
)
CLASSES = (1.0, 0.75, 0.55)   # the three blade heights, of the card
# r35 (critic r34: "ferns and tall grass are indistinguishable... give ferns a distinct silhouette"): a fern is a LOW
# ROSETTE (gen_blocks FERN_ROSETTES): near-flat cards hinged at the crown and tilted 22.5 (LOW) or 45 (MID) degrees up
# from the ground, one per direction, so the RTS camera sees the fronds face-on - wider than tall, where the grass is
# upright blades. Each card carries a few fronds from its root (bottom centre): (angle from the card's axis in
# degrees, length of the card, widest half width in texels, sideways arch). On a card tilted t, an angle a reads as
# atan(tan a / cos t) from above: LOW's +-23 reads +-25, MID's +-35 reads +-45 - with the four directions a 16-arm star.
FERN_CARDS = (
    dict(fronds=((-23, 0.62, 13.0, 0.30), (23, 0.58, 13.0, 0.30), (0, 0.97, 17.0, 0.12)), period=19.0),   # LOW
    dict(fronds=((0, 0.42, 10.0, 0.10), (-35, 0.92, 15.5, 0.30), (35, 0.88, 15.5, 0.30)), period=18.0),    # MID
)
LARGE_FERN = dict(fronds=6, spread=60, lean=2)


def _smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


class Canvas:
    def __init__(self, w, h):
        self.n, self.nh = w, h
        self.w, self.h = w * SS, h * SS
        self.cov = np.zeros((self.h, self.w))
        self.shade = np.zeros((self.h, self.w))
        self.height = np.zeros((self.h, self.w))
        self.layer = np.zeros((self.h, self.w))     # 0 = green (tinted), 1 = straw (untinted, its own colour)

    def ribbon(self, pts, hw_fn, shade_fn, depth, wmax, layer=0):
        """A filled ribbon along the polyline pts ((x, y) in texels, y down, root first): a sub-sample belongs to it
        when its distance across the line (s, texels, + = right of the direction of travel) is within hw_fn(t, s),
        t = 0 at the root .. 1 at the tip. Paints coverage, shade (shade_fn(t, s / hw, s)) and height over what is
        there (painter's order: later ribbons are in front). wmax bounds hw_fn."""
        pts = np.asarray(pts, float)
        seg = np.linalg.norm(np.diff(pts, axis=0), axis=1)
        cum = np.concatenate([[0.0], np.cumsum(seg)])
        total = cum[-1]
        if total <= 0:
            return
        k = max(3, int(math.ceil(total * SS)) + 1)               # one point per sub-sample along the line
        ts = np.linspace(0, total, k)
        P = np.stack([np.interp(ts, cum, pts[:, 0]), np.interp(ts, cum, pts[:, 1])], -1)
        T = np.gradient(P, axis=0)
        T /= np.maximum(np.linalg.norm(T, axis=1, keepdims=True), 1e-9)
        Nr = np.stack([-T[:, 1], T[:, 0]], -1)
        x0 = max(0, int((P[:, 0].min() - wmax - 1) * SS))
        x1 = min(self.w, int((P[:, 0].max() + wmax + 1) * SS) + 1)
        y0 = max(0, int((P[:, 1].min() - wmax - 1) * SS))
        y1 = min(self.h, int((P[:, 1].max() + wmax + 1) * SS) + 1)
        if x0 >= x1 or y0 >= y1:
            return
        d, idx = _nearest_local(P, x0, x1, y0, y1, wmax + 1)
        ok = d < wmax + 1                                     # (was a k-d tree's distance_upper_bound)
        gy, gx = np.nonzero(ok)
        gy, gx, idx = gy + y0, gx + x0, idx[ok]
        pix = np.stack([(gx + 0.5) / SS, (gy + 0.5) / SS], -1)
        v = pix - P[idx]
        s = (v * Nr[idx]).sum(1)
        a = (v * T[idx]).sum(1)
        t = ts[idx] / total
        hw = hw_fn(t, s)
        m = (np.abs(s) <= hw) & ~((idx == k - 1) & (a > 1e-6)) & ~((idx == 0) & (a < -1e-6))
        gy, gx, t, s, hw = gy[m], gx[m], t[m], s[m], hw[m]
        across = np.clip(s / np.maximum(hw, 1e-6), -1, 1)
        self.cov[gy, gx] = 1.0
        self.shade[gy, gx] = shade_fn(t, across, s)
        # a blade folded along its rib (a shallow V, so no texel of its edge tilts more than the half it is on - a
        # rounded blade lit its far edge as a dark rim); a blade in front only a little higher than the one behind
        self.height[gy, gx] = 0.5 + 0.12 * depth - 0.12 * np.abs(across)
        self.layer[gy, gx] = float(layer)

    def _down(self, a):
        return a.reshape(self.h // SS, SS, self.w // SS, SS).mean((1, 3))

    def result(self):
        cov = self._down(self.cov)
        alpha = (cov >= 0.5).astype(np.float64)
        w = np.maximum(cov, 1e-6)
        shade = self._down(self.shade * self.cov) / w
        height = self._down(self.height * self.cov) / w
        return np.where(alpha > 0, height, 0.0), np.where(alpha > 0, shade, 0.0), alpha

    def layers(self):
        """(H, shade, green alpha, straw alpha): the cut-out split by which layer owns each texel (the majority of
        its covered sub-samples), so the two masks never overlap - the green (tinted) and straw (untinted) quads are
        coplanar and would z-fight where both are opaque."""
        H, shade, alpha = self.result()
        cov = self._down(self.cov)
        straw = self._down(self.layer * self.cov)
        is_straw = straw * 2 > np.maximum(cov, 1e-6)
        return H, shade, alpha * (~is_straw), alpha * is_straw


def bleed(img, alpha):
    """The value under every transparent texel = its nearest opaque texel's (MC-114265: mipmaps blend the colour of
    the transparent texels into a cut-out's edges, black by default - a dark rim round every blade from afar)."""
    solid = alpha > 0.5
    if not solid.any():
        return np.full_like(img, 0.5)
    iy, ix = _edt_indices(solid)
    return np.where(solid[..., None] if img.ndim == 3 else solid, img, img[iy, ix])


def _edt_indices(solid):
    """For every texel, the (row, col) of its nearest solid texel by Euclidean distance - an exact two-pass distance
    transform (Felzenszwalb & Huttenlocher's lower envelope of parabolas), numpy + plain loops, no scipy."""
    h, w = solid.shape
    big = h + w + 1
    # pass 1, down each column: the nearest solid row in the same column, and the gap to it
    near_row = np.full((h, w), -1, dtype=np.int64)
    last = np.full(w, -big, dtype=np.int64)
    for y in range(h):
        last = np.where(solid[y], y, last)
        near_row[y] = last
    nxt = np.full(w, 2 * big, dtype=np.int64)
    for y in range(h - 1, -1, -1):
        nxt = np.where(solid[y], y, nxt)
        near_row[y] = np.where(nxt - y < y - near_row[y], nxt, near_row[y])
    g2 = (np.arange(h)[:, None] - near_row).astype(np.float64) ** 2
    # pass 2, along each row: min over columns c of (x - c)^2 + g2[c]
    iy = np.empty((h, w), dtype=np.int64)
    ix = np.empty((h, w), dtype=np.int64)
    inf = float("inf")
    for y in range(h):
        f = g2[y].tolist()
        v = [0] * w
        z = [0.0] * (w + 1)
        k = 0
        z[0], z[1] = -inf, inf
        for q in range(1, w):
            while True:
                p = v[k]
                sq = ((f[q] + q * q) - (f[p] + p * p)) / (2 * q - 2 * p)
                if sq <= z[k] and k > 0:
                    k -= 1
                    continue
                if sq <= z[k]:                       # k == 0: the new parabola replaces the first
                    v[0] = q
                    z[1] = inf
                    break
                k += 1
                v[k] = q
                z[k] = sq
                z[k + 1] = inf
                break
        k = 0
        for x in range(w):
            while z[k + 1] < x:
                k += 1
            ix[y, x] = v[k]
        iy[y] = near_row[y, ix[y]]
    return iy, ix


def _nearest_local(points, x0, x1, y0, y1, r):
    """(distance, index) of the nearest of `points` (texels) to each sub-sample centre of the box [y0:y1, x0:x1]
    (sub-samples), for every sub-sample within r of some point; inf (index -1) for the rest. Numpy only (CI installs
    numpy + pillow, no scipy); ties go to the lowest index. r35: each point updates only the sub-samples within r of
    it - the same answer as comparing every sub-sample with every point (r34's chunked _nearest) wherever the
    distance is under r, ~100x faster (a tall grass took 22 s, now 0.15 s)."""
    best = np.full((y1 - y0, x1 - x0), np.inf)
    idx = np.full((y1 - y0, x1 - x0), -1, dtype=np.int64)
    cx = (np.arange(x0, x1) + 0.5) / SS
    cy = (np.arange(y0, y1) + 0.5) / SS
    for i, (px, py) in enumerate(points):
        a = max(x0, int(math.floor((px - r) * SS - 0.5))) - x0
        b = min(x1, int(math.ceil((px + r) * SS - 0.5)) + 1) - x0
        c = max(y0, int(math.floor((py - r) * SS - 0.5))) - y0
        e = min(y1, int(math.ceil((py + r) * SS - 0.5)) + 1) - y0
        if a >= b or c >= e:
            continue
        d2 = (cx[None, a:b] - px) ** 2 + (cy[c:e, None] - py) ** 2
        win = best[c:e, a:b]
        closer = d2 < win
        win[closer] = d2[closer]
        idx[c:e, a:b][closer] = i
    return np.sqrt(best), idx


def _blade_shade(tone, body=BODY):
    """Along a blade: the base a touch above the lawn, the body lighter, the top ~35% lighter still; a lighter rib;
    the blade folded along the rib, lit from the upper left (left half a touch lighter). No dark edges."""
    def fn(t, across, s):
        v = BASE + (body - BASE) * _smooth(0.0, 0.3, t) + (TIP - body) * _smooth(0.55, 0.95, t)
        veins = 0.012 * np.cos(s * 2.4)          # fine parallel veins (~2.6 texels): close-ups only, gone by mip 2
        return (v + RIB * np.exp(-(across / 0.22) ** 2) - 0.025 * across + veins) * tone
    return fn


def _curve(x0, y0, length, ang, bend, n_pts=28):
    """Points of a line from its root (x0, y0) going up at angle ang (radians from vertical, + = right), bending
    further by `bend` radians over its length (gravity / a lean)."""
    pts = []
    x, y = x0, y0
    step = length / (n_pts - 1)
    for i in range(n_pts):
        pts.append((x, y))
        a = ang + bend * (i / (n_pts - 1)) ** 2
        x += math.sin(a) * step
        y -= math.cos(a) * step
    return pts


def _reach(x0, y0, top, ang, bend, w, hw, margin=2.0):
    """A blade from (x0, y0) whose highest point is at y = top, leaning less until it (with its width hw(t)) stays
    inside [margin, w - margin]: a blade cut by the tile edge shows as a hard line where the crossed quads meet."""
    for _ in range(24):
        length = (y0 - top) / max(0.3, math.cos(ang))
        for _ in range(8):
            pts = _curve(x0, y0, length, ang, bend)
            hi = y0 - min(p[1] for p in pts)
            length *= (y0 - top) / max(hi, 1e-6)
        pts = _curve(x0, y0, length, ang, bend)
        k = len(pts)
        if all(margin + hw(i / (k - 1)) <= x <= w - margin - hw(i / (k - 1)) for i, (x, _) in enumerate(pts)):
            return pts
        ang *= 0.88
        bend *= 0.88
    return pts


def _blade_hw(width):
    """Half width along a blade: full at the root, tapering evenly (0.67 of it half way, 0.41 at 80% - >= 4 texels
    for a 12-texel blade) to a rounded point."""
    def hw(t, s=None):
        t = np.asarray(t, float)
        return 0.5 * width * (1 - 0.65 * t) * np.sqrt(np.clip(1 - t ** 6, 0, 1))
    return hw


def grass_clump(n, rng, tall=False, variant=0, size=1.0):
    """Short grass (one tile) or tall grass (a 2n-tall canvas): a few bold blades from one crown (GRASS / TALL).
    size (r35, short grass; SIZES): the tuft's height and reach, of the full one - the blades' tops come down and the
    crown narrows; their widths shrink only a little (0.8 + 0.2 size), so >= 4 texels at 80% of a blade stays true."""
    spec = (TALL if tall else GRASS)[variant]
    h = 2 * n if tall else n
    cv = Canvas(n, h)
    k = spec["blades"]
    pos = np.linspace(-1, 1, k) + (rng.random(k) - 0.5) * 0.12
    # height classes: the central blades tallest, the outer ones shortest (a jittered rank, so not a symmetric fan)
    rank = np.argsort(np.argsort(np.abs(pos - 0.01 * spec["lean"]) + 0.35 * rng.random(k)))
    cls = [CLASSES[min(2, int(3 * r / k))] for r in rank]
    depth = rng.permutation(k) / max(1, k - 1)
    blades = []
    for i in range(k):
        straw = i == spec["straw"]
        lo, hi = spec["width"]
        width = lo - 2.0 if straw else lo + (hi - lo) * rng.random()     # a dead blade is narrower
        width *= 0.8 + 0.2 * size
        c = min(cls[i], 0.75) if straw else cls[i]                  # a dead blade is never the tallest
        top = h - size * c * (h - 3) * (0.96 + 0.06 * rng.random())
        ang = math.radians(spec["lean"] + pos[i] * spec["fan"] / 2 + 6 * (rng.random() - 0.5))
        bend = math.copysign(0.10 + 0.35 * abs(pos[i]), ang + 1e-3) * (1.25 if c == 1.0 else 1.0)
        if tall:
            bend *= 1.4                                               # long leaves arch over
        hw = _blade_hw(width)
        crown = size                                                   # a smaller tuft from a narrower crown
        root = n / 2 + pos[i] * 4.5 * crown + (rng.random() - 0.5) * 1.5 * crown
        pts = _reach(root, h + 1.0, top, ang, bend, n, hw)
        tone = 0.97 + 0.06 * depth[i] + 0.04 * (rng.random() - 0.5)
        blades.append((-1.0 if straw else depth[i], pts, hw, tone, width, straw))
    body = BODY if tall else BODY_SHORT
    for d, pts, hw, tone, width, straw in sorted(blades, key=lambda b: b[0]):   # back to front, the straw at the back
        cv.ribbon(pts, hw, _blade_shade(tone, body), 0.3 + 0.7 * max(0.0, d), width / 2 + 1, layer=1 if straw else 0)
    return cv.layers()


def _frond_hw(length, wmax, rach, period, phase0, t0=0.12, sweep=0.7, floor=0.12):
    """Half width of a fern frond: a bare stalk (rach) to t0, then a lanceolate blade (widest ~40% up) whose edge is
    cut into rounded lobes - the leaflets, merged - alternating left and right and swept toward the tip; a notch goes
    down to `floor` of the width, so with wmax ~14 a scallop is ~10 texels deep."""
    def hw(t, s):
        u = np.clip((t - t0) / (1 - t0), 0, 1)
        env = np.sin(np.pi * u ** 0.8) ** 0.8
        ph = (length / period) * (t + 0.3 * t * t) - sweep * np.abs(s) / period + phase0 + np.where(s >= 0, 0.5, 0)
        lobe = floor + (1 - floor) * (1 - np.abs(2 * (ph % 1.0) - 1)) ** 0.7    # pointed leaflet ends
        return rach + (wmax - rach) * env * lobe
    return hw


def _frond_shade(tone, wmax):
    """A frond: the blade values along it, a lighter rachis, the lobes' outer ends a touch lighter, no veins."""
    def fn(t, across, s):
        v = BASE + (BODY - BASE) * _smooth(0.0, 0.3, t) + (TIP - 0.03 - BODY) * _smooth(0.55, 0.95, t)
        rib = RIB * np.exp(-(s / 1.8) ** 2)
        return (v + rib + 0.03 * np.clip(np.abs(s) / wmax, 0, 1) - 0.02 * np.sign(s)) * tone
    return fn


def fern_card(n, rng, variant=0):
    """r35: one card of the fern rosette (FERN_CARDS: 0 = LOW, 1 = MID): solid scalloped fronds from the root at the
    bottom centre, each arching sideways away from the card's axis, drawn back to front in the listed order."""
    spec = FERN_CARDS[variant]
    cv = Canvas(n, n)
    k = len(spec["fronds"])
    for i, (deg, frac, wmax, arch) in enumerate(spec["fronds"]):
        ang = math.radians(deg + 4 * (rng.random() - 0.5))
        side = math.copysign(1.0, deg) if deg else (1.0 if rng.random() < 0.5 else -1.0)
        bend = side * arch * (0.85 + 0.3 * rng.random())
        length = frac * n * (0.94 + 0.06 * rng.random())
        hw = _frond_hw(length, wmax, 2.5, spec["period"], rng.random())
        root = n / 2 + 0.6 * side * (1 if deg else 0)
        bound = (lambda t, w=wmax: w * min(1.0, 2.5 * t))
        pts = _curve(root, n + 1.0, length, ang, bend)
        for _ in range(24):
            kk = len(pts)
            if all(2 + bound(j / (kk - 1)) <= x <= n - 2 - bound(j / (kk - 1)) and y >= 2
                   for j, (x, y) in enumerate(pts)):
                break
            ang *= 0.92
            bend *= 0.9
            length *= 0.98
            pts = _curve(root, n + 1.0, length, ang, bend)
        tone = 0.97 + 0.06 * (i / max(1, k - 1)) + 0.04 * (rng.random() - 0.5)
        cv.ribbon(pts, hw, _frond_shade(tone, wmax), 0.3 + 0.7 * i / max(1, k - 1), wmax + 1)
    return cv.result()


def fern(n, rng, large=True):
    """The large fern (2n tall): 6 solid fronds from one crown, arching out, the outer ones drooping. (Until r34 this
    drew the one-block fern too, as 5-6 fronds on crossed quads; r35 draws that as rosette cards, fern_card.)"""
    spec = LARGE_FERN
    h = 2 * n
    cv = Canvas(n, h)
    k = spec["fronds"]
    pos = np.linspace(-1, 1, k) + (rng.random(k) - 0.5) * 0.15
    depth = rng.permutation(k) / max(1, k - 1)
    wmax, rach, period = 14.0, 3.0, 19.0
    fronds = []
    for i in range(k):
        ang = math.radians(spec["lean"] + pos[i] * spec["spread"] / 2 + 6 * (rng.random() - 0.5))
        bend = math.copysign(0.35 + 0.55 * abs(pos[i]), ang + 1e-3)
        # the central fronds long and upright, the outer ones shorter and drooping: a fan, not a V of two
        length = 1.80 * n * (1.0 - 0.40 * abs(pos[i]) ** 1.5) * (0.92 + 0.08 * rng.random())
        hw = _frond_hw(length, wmax, rach, period, rng.random())
        root = n / 2 + pos[i] * 3.0
        bound = (lambda t, w=wmax: w * min(1.0, 2.5 * t))   # the frond's reach across, for the edge check
        pts = _curve(root, h + 1.0, length, ang, bend)
        for _ in range(24):
            kk = len(pts)
            if all(2 + bound(j / (kk - 1)) <= x <= n - 2 - bound(j / (kk - 1)) and y >= 2
                   for j, (x, y) in enumerate(pts)):
                break
            ang *= 0.9
            bend *= 0.9
            length *= 0.98
            pts = _curve(root, h + 1.0, length, ang, bend)
        tone = 0.97 + 0.06 * depth[i] + 0.04 * (rng.random() - 0.5)
        fronds.append((depth[i], pts, hw, tone))
    for d, pts, hw, tone in sorted(fronds, key=lambda f: f[0]):
        cv.ribbon(pts, hw, _frond_shade(tone, wmax), 0.3 + 0.7 * d, wmax + 1)
    return cv.result()


def split2(res):
    """A 2n-tall tuple of arrays -> (top, bottom) halves."""
    n = res[0].shape[1]
    return tuple(a[:n] for a in res), tuple(a[n:] for a in res)
