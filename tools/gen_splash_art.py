#!/usr/bin/env python3
"""
Title / loading screen art for Beyond and Tabs: "the cathedral rose window" (design/splash.md).

Everything here is drawn from code - no photographs, no traced or copied images. The rose window, the arch, the
filigree, the candlelight and the 3D gold are signed-distance fields and polygons evaluated with numpy; the lettering is
set in two SIL Open Font License fonts that live next to this script with their licence files (tools/fonts/*-OFL.txt).
The fonts are only used here, at build time; the mod ships the rendered PNGs, never the font files.

Output (bar/src/main/resources/assets/reignofnether/textures/gui/splash/), separate layers so the title screen can
parallax and animate them (SplashArt.java draws them; the layout fractions below are mirrored there):
    background.png  1280x720   dark ashlar wall, candle pools, bokeh, vignette (cover-scaled to any aspect)
    frame.png       1600x1080  pointed-arch moulding with gold filigree, crockets and capitals (height = screen)
    window.png      1024x1024  the 12-fold stained-glass rose window
    rays.png         512x512   soft light beams, drawn additively and slowly rotated over the window (the shimmer)
    glow.png         256x256   a warm radial glow (window bloom and the flickering candles)
    title.png                  "BEYOND AND TABS" in extruded, bevelled 3D gold with a soft shadow
    byline.png                 "Developed by Lovish" in the same gold, between two filigree rules
Previews (design/splash_preview_*.png) compose the layers the way the game does, with mock buttons at GUI scale 2/4.

Deterministic: fixed seeds, no timestamps. usage: python3 tools/gen_splash_art.py [--previews-only]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "bar/src/main/resources/assets/reignofnether/textures/gui/splash")
DESIGN = os.path.join(REPO, "design")
TITLE_FONT = os.path.join(HERE, "fonts/GrenzeGotisch-Variable.ttf")     # OFL 1.1, Grenze Gotisch Project Authors
BYLINE_FONT = os.path.join(HERE, "fonts/UnifrakturMaguntia-Book.ttf")  # OFL 1.1, j. 'mach' wust / Peter Wiegel

# Layout as fractions of the screen height H, centred horizontally (SplashArt.java uses the same numbers)
WINDOW_CY, WINDOW_D = 0.47, 0.74
TITLE_CY, TITLE_H = 0.108, 0.19
BYLINE_CY, BYLINE_H = 0.226, 0.075
FRAME_W, FRAME_H = 1600, 1080           # frame.png is drawn at full screen height
ARCH_HALF_W, ARCH_SPRING_Y, ARCH_APEX_Y, ARCH_BAND = 430.0, 560.0, 30.0, 64.0   # in frame.png pixels

# palette: jewel-tone glass, lead, stone, gold
RUBY = (168, 20, 34)
RUBY_DEEP = (110, 10, 26)
SAPPHIRE = (26, 52, 168)
SAPPHIRE_DEEP = (16, 28, 104)
AMBER = (232, 160, 40)
GOLD_GLASS = (250, 206, 92)
EMERALD = (22, 120, 72)
VIOLET = (86, 34, 128)
PEARL = (255, 238, 196)
LEAD = (26, 22, 20)
STONE = (120, 106, 92)


# ---------------------------------------------------------------- numpy helpers

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
    """Gaussian-ish blur (three box passes; variance r(r+1) each) - precise floats, no scipy needed."""
    r = max(0, int(round(math.sqrt(sigma * sigma + 0.25) - 0.5)))
    for _ in range(3):
        for axis in (0, 1):
            a = _box(a, r, axis)
    return a


def smooth_inside(sdf, px):
    """Anti-aliased coverage of sdf <= 0, px = size of one pixel in sdf units."""
    return np.clip(0.5 - sdf / px, 0.0, 1.0).astype(np.float32)


def band(sdf, half, px):
    """Coverage of |sdf| <= half (a line along the shape's edge)."""
    return smooth_inside(np.abs(sdf) - half, px)


def mix(dst, col, m):
    """dst (H,W,3) lerped toward col (3-tuple or (H,W,3)) by mask m."""
    col = np.asarray(col, np.float32) / 255.0 if not isinstance(col, np.ndarray) else col
    return dst + (col - dst) * m[..., None]


def over(dst_rgb, dst_a, src_rgb, src_a):
    """Premultiplied 'over'."""
    return src_rgb + dst_rgb * (1 - src_a[..., None]), src_a + dst_a * (1 - src_a)


def noise(shape, scale, rng, octaves=3):
    """Smooth value noise in [0,1]: random grids upsampled bicubically and summed."""
    h, w = shape
    out = np.zeros(shape, np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        s = max(2, int(scale * (2 ** o)))
        g = rng.random((s + 1, int(s * w / h) + 2)).astype(np.float32)
        im = Image.fromarray((g * 255).astype(np.uint8), "L").resize((w, h), Image.BICUBIC)
        out += np.asarray(im, np.float32) / 255.0 * amp
        tot += amp
        amp *= 0.5
    return out / tot


def save_premul(rgb, a, path, size=None):
    """Un-premultiply, optionally downsample (premultiplied, so no dark fringes), save RGBA PNG."""
    if size is not None:
        layers = [Image.fromarray(np.clip(c * 255, 0, 255).astype(np.uint8), "L").resize(size, Image.LANCZOS)
                  for c in (rgb[..., 0], rgb[..., 1], rgb[..., 2], a)]
        arr = np.stack([np.asarray(l, np.float32) / 255 for l in layers], -1)
        rgb, a = arr[..., :3], arr[..., 3]
    safe = np.where(a > 1e-4, a, 1.0)[..., None]
    out = np.concatenate([np.clip(rgb / safe, 0, 1), a[..., None]], -1)
    img = Image.fromarray(np.round(out * 255).astype(np.uint8), "RGBA")
    img.save(path, optimize=True)
    return img


# ---------------------------------------------------------------- (a) the rose window

def rose_window(size=1024, ss=2):
    n = size * ss
    rng = np.random.default_rng(1212)
    px = 2.0 / n                                         # one pixel in window units (radius 1)
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    X = (xx - (n - 1) / 2) / (n / 2)
    Y = (yy - (n - 1) / 2) / (n / 2)
    r = np.hypot(X, Y)
    th = np.arctan2(X, -Y)                               # 0 = straight up, clockwise
    sector = 2 * math.pi / 12
    k = np.round(th / sector)
    a = th - k * sector
    parity = (k.astype(np.int32) % 2).astype(np.float32)
    lx, ly = r * np.cos(a), r * np.abs(np.sin(a))         # folded 12-fold dihedral frame, axis along +x
    b15 = math.radians(15)

    def circ(cx, cy, rad):
        return np.hypot(lx - cx, ly - cy) - rad

    def on_boundary(rho):                                 # a point on the line between two sectors
        return rho * math.cos(b15), rho * math.sin(b15)

    def lancet(xb, xs, w, rho):
        body = np.maximum.reduce([xb - lx, lx - xs, ly - w])
        arch = np.maximum(np.hypot(lx - xs, ly + (rho - w)) - rho, xs - lx)
        return np.minimum(body, arch)

    def vesica(x0, x1, w):
        L = (x1 - x0) / 2
        d = (L * L - w * w) / (2 * w)
        rho = w + d
        xm = (x0 + x1) / 2
        return np.maximum(np.hypot(lx - xm, ly + d) - rho, np.hypot(lx - xm, ly - d) - rho)

    ring = lambda rad: r - rad
    col = np.zeros((n, n, 3), np.float32)

    # glass by band (deepest first), then the panes inside each band
    col = mix(col, VIOLET, (r <= 1.0).astype(np.float32))
    col = mix(col, SAPPHIRE_DEEP, (r <= 0.80).astype(np.float32))
    col = mix(col, SAPPHIRE, (r <= 0.42).astype(np.float32))
    col = mix(col, AMBER, (r <= 0.13).astype(np.float32))

    # outer band 0.80-0.94: a ruby oculus on every axis and an amber one between, quatrefoils in the rubies
    oc = circ(0.87, 0, 0.052)
    col = mix(col, RUBY, smooth_inside(oc, px))
    ob = circ(*on_boundary(0.875), 0.034)
    col = mix(col, AMBER, smooth_inside(ob, px))
    quat = np.minimum.reduce([circ(0.87 + 0.022, 0, 0.021), circ(0.87 - 0.022, 0, 0.021),
                              circ(0.87, 0.022, 0.021), np.hypot(lx - 0.87, ly + 0.022) - 0.021])
    col = mix(col, GOLD_GLASS, smooth_inside(circ(0.87, 0, 0.016), px))

    # middle band 0.42-0.80: twelve lancets (ruby, gold border), spandrel roundels on the sector lines
    lan = lancet(0.455, 0.64, 0.064, 0.105)
    lan_in = lancet(0.47, 0.64, 0.050, 0.091)
    col = mix(col, GOLD_GLASS, smooth_inside(lan, px))
    inner_col = np.where(parity[..., None] > 0.5, np.float32(1), np.float32(0))
    lan_glass = np.asarray(RUBY, np.float32) / 255 * (1 - inner_col) + np.asarray(EMERALD, np.float32) / 255 * inner_col
    col = mix(col, lan_glass, smooth_inside(lan_in, px))
    tre = np.minimum.reduce([circ(0.676, 0, 0.022), circ(0.655, 0.018, 0.017), np.hypot(lx - 0.655, ly + 0.018) - 0.017])
    col = mix(col, PEARL, smooth_inside(tre, px))
    sp = circ(*on_boundary(0.725), 0.052)
    col = mix(col, AMBER, smooth_inside(sp, px))
    sp_in = circ(*on_boundary(0.725), 0.026)
    col = mix(col, RUBY, smooth_inside(sp_in, px))
    sp2 = circ(*on_boundary(0.50), 0.03)
    col = mix(col, GOLD_GLASS, smooth_inside(sp2, px))

    # inner band 0.13-0.42: twelve ruby petals with violet hearts, gold roundels between
    pet = vesica(0.15, 0.405, 0.056)
    col = mix(col, RUBY, smooth_inside(pet, px))
    pet_in = vesica(0.21, 0.35, 0.024)
    col = mix(col, VIOLET, smooth_inside(pet_in, px))
    pr = circ(*on_boundary(0.31), 0.03)
    col = mix(col, GOLD_GLASS, smooth_inside(pr, px))

    # centre: amber petals, pearl disc, a small ruby cross
    cpet = vesica(0.062, 0.128, 0.022)
    col = mix(col, RUBY_DEEP, smooth_inside(cpet, px))
    col = mix(col, PEARL, smooth_inside(ring(0.058), px))
    cross = np.minimum(np.maximum(np.abs(X) - 0.010, np.abs(Y) - 0.040), np.maximum(np.abs(Y + 0.006) - 0.010, np.abs(X) - 0.030))
    col = mix(col, RUBY, smooth_inside(cross, px))

    # light through the glass: brighter towards the centre, slow mottling, fine seeds and streaks
    mott = noise((n, n), 6, rng, 3)
    seeds = noise((n, n), 90, rng, 2)
    light = (0.62 + 0.42 * (1 - np.clip(r, 0, 1)) ** 1.3) * (0.80 + 0.40 * mott) * (0.92 + 0.16 * seeds)
    # every sector's glass came from a slightly different melt: a small brightness / warmth jitter per sector
    jit = rng.uniform(0.88, 1.10, 12).astype(np.float32)[k.astype(np.int32) % 12]
    col = col * (light * jit)[..., None]
    # lit glass a touch towards white where it is brightest (transmission bloom)
    col = col + (1 - col) * np.clip(light - 1.0, 0, 1)[..., None] * 0.6

    # quarry lead lattice inside the lancets and the outer band (diamond panes)
    lat = 0.042
    u, v = (lx + ly) / lat, (lx - ly) / lat
    lattice = np.minimum(np.abs(u - np.round(u)), np.abs(v - np.round(v))) * lat
    lead_m = smooth_inside(lattice - 0.0022, px) * smooth_inside(lan_in + 0.004, px) * smooth_inside(tre * -1 + 0.004, px)
    col = mix(col, LEAD, lead_m * 0.9)
    # the plain band glass is leaded too (finer diamonds, lighter cames), so no pane reads as a flat field of colour
    plain = ((r > 0.425) & (r < 0.935) & (np.abs(r - 0.80) > 0.012)).astype(np.float32)
    for s_ in (lan, sp, sp2, oc, ob):
        plain = plain * smooth_inside(-s_ - 0.004, px)
    lat2 = 0.034
    u2, v2 = (lx * 0.8 + ly) / lat2, (lx * 0.8 - ly) / lat2
    lattice2 = np.minimum(np.abs(u2 - np.round(u2)), np.abs(v2 - np.round(v2))) * lat2
    col = mix(col, LEAD, smooth_inside(lattice2 - 0.0016, px) * plain * 0.75)
    # bloom: the brightest glass bleeds a little light over its cames
    glass_lum = col.mean(-1) * (r <= 0.94)
    bloom = blur(np.clip(glass_lum - 0.35, 0, 1), 7 * ss)
    col = col + bloom[..., None] * np.array([0.55, 0.42, 0.30], np.float32)

    # lead cames along every pane edge
    lw = 0.0042
    lead = np.maximum.reduce([band(s, lw, px) for s in (oc, ob, lan, lan_in, tre, sp, sp_in, sp2, pet, pet_in, pr, cpet,
                                                        ring(0.058), cross, circ(0.87, 0, 0.016))])
    col = mix(col, LEAD, lead)

    # stone tracery: rings, the twelve spokes on the sector lines, lancet mullions - a rounded profile (cylinder light)
    spoke = np.abs(lx * math.sin(b15) - ly * math.cos(b15))
    spoke = np.where((r > 0.13) & (r < 0.94), spoke, 9.0)
    for c in (ob, sp, sp2, pr):                           # the spokes stop at the roundels they run into
        spoke = np.where(c < 0, 9.0, spoke)
    stone_parts = [(np.abs(r - 1.0 + 0.03) / 0.03, 0.03), (np.abs(ring(0.80)) / 0.011, 0.011),
                   (np.abs(ring(0.42)) / 0.012, 0.012), (np.abs(ring(0.13)) / 0.010, 0.010),
                   (spoke / 0.0085, 0.0085), (np.abs(lan) / 0.007, 0.007), (np.abs(oc) / 0.006, 0.006),
                   (np.abs(sp) / 0.006, 0.006), (np.abs(ob) / 0.005, 0.005)]
    stone_t = np.full((n, n), 9.0, np.float32)
    stone_w = np.zeros((n, n), np.float32)
    for t, w in stone_parts:
        better = t < stone_t
        stone_t = np.where(better, t, stone_t)
        stone_w = np.where(better, w, stone_w)
    stone_m = np.clip((1 - stone_t) * stone_w / px + 0.5, 0, 1)
    prof = np.sqrt(np.clip(1 - np.minimum(stone_t, 1) ** 2, 0, 1))
    grit = noise((n, n), 40, rng, 2)
    stone_col = np.asarray(STONE, np.float32) / 255 * (0.26 + 0.62 * prof * (0.85 + 0.3 * grit))[..., None]
    # warm candle light grazing the stone from below-left
    stone_col = stone_col * np.array([1.08, 0.98, 0.86], np.float32)
    col = mix(col, stone_col, stone_m)
    # a thin gold fillet on the outermost ring
    col = mix(col, (222, 176, 82), band(r - 0.995, 0.0045, px) * 0.9)

    alpha = smooth_inside(r - 1.0, px)
    return save_premul(col * alpha[..., None], alpha, os.path.join(OUT, "window.png"), (size, size))


def rays(size=512):
    n = size
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    X, Y = (xx - (n - 1) / 2) / (n / 2), (yy - (n - 1) / 2) / (n / 2)
    r, th = np.hypot(X, Y), np.arctan2(X, -Y)
    beams = (0.55 + 0.45 * np.cos(th * 12)) ** 6 * 0.7 + (0.5 + 0.5 * np.cos(th * 24 + 0.7)) ** 10 * 0.35
    fall = np.clip(1 - r, 0, 1) ** 1.6
    core = np.exp(-(r / 0.35) ** 2) * 0.5
    a = np.clip(beams * fall + core, 0, 1).astype(np.float32)
    rgb = np.ones((n, n, 3), np.float32) * np.array([1.0, 0.86, 0.62], np.float32)
    return save_premul(rgb * a[..., None], a, os.path.join(OUT, "rays.png"))


def glow(size=256):
    n = size
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    r = np.hypot((xx - (n - 1) / 2) / (n / 2), (yy - (n - 1) / 2) / (n / 2))
    a = (np.clip(1 - r, 0, 1) ** 2.2).astype(np.float32)
    rgb = np.ones((n, n, 3), np.float32) * np.array([1.0, 0.72, 0.36], np.float32)
    return save_premul(rgb * a[..., None], a, os.path.join(OUT, "glow.png"))


# ---------------------------------------------------------------- (b) the pointed-arch frame

def arch_geometry():
    w, ys, ya = ARCH_HALF_W, ARCH_SPRING_Y, ARCH_APEX_Y
    h = ys - ya
    rho = (h * h + w * w) / (2 * w)       # arc radius so the two arcs meet at the apex
    return w, ys, rho


def frame(ss=2):
    W, H = FRAME_W * ss, FRAME_H * ss
    rng = np.random.default_rng(1080)
    w, ys, rho = arch_geometry()
    cx = FRAME_W / 2
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    x, y = xx / ss, yy / ss
    dx = np.abs(x - cx)
    # signed distance to the opening (negative inside): arcs above the springing line, straight jambs below
    off = rho - w
    arc = np.hypot(dx + off, y - ys) - rho
    arc = np.where(dx + off < 0, -(ys - y), arc)
    jamb = dx - w
    d = np.where(y < ys, arc, jamb)
    px = 1.0 / ss
    B = ARCH_BAND
    inband = smooth_inside(np.abs(d - B / 2) - B / 2, px)
    t = np.clip(d / B, 0, 1)
    # moulding profile: three rolls separated by hollows (a cavetto between two tori and a fillet)
    prof = 0.5 + 0.5 * np.cos(t * math.pi * 6) * 0.55 + 0.25 * np.sin(t * math.pi)
    grit = noise((H, W), 30, rng, 3)
    stone = np.asarray(STONE, np.float32) / 255 * (0.30 + 0.55 * prof * (0.8 + 0.4 * grit))[..., None]
    stone = stone * np.array([1.06, 0.97, 0.88], np.float32)
    # fade the band into the dark at the bottom so it never ends on a hard line under the buttons
    fade = np.clip((FRAME_H - y) / 260.0, 0, 1)
    a = inband * (0.35 + 0.65 * fade)
    rgb = stone * a[..., None]

    gold_col = np.array([0.93, 0.74, 0.34], np.float32)
    gold_hi = np.array([1.0, 0.93, 0.66], np.float32)

    def gold_line(mask):
        nonlocal rgb, a
        sheen = 0.75 + 0.25 * np.cos((x + y * 0.6) / 37.0)
        c = (gold_col * (0.8 + 0.2 * sheen[..., None]) + (gold_hi - gold_col) * np.clip(sheen - 0.85, 0, 1)[..., None] * 4)
        rgb, a = over(rgb, a, c * mask[..., None], mask)

    gold_line(band(d - 1.5, 1.6, px) * (0.3 + 0.7 * fade))
    gold_line(band(d - (B - 1.5), 1.6, px) * (0.3 + 0.7 * fade))
    gold_line(band(d - B * 0.5, 0.7, px) * 0.55 * (0.3 + 0.7 * fade))

    # filigree: quatrefoils along the band and crockets on its outer edge, placed along the centre line
    img = Image.new("L", (W, H), 0)
    dr = ImageDraw.Draw(img)
    pts = arch_centerline(B * 0.5, step=46)
    for (px_, py_, nx, ny) in pts:
        rr = 8.5
        for ang in range(4):
            ox, oy = math.cos(ang * math.pi / 2 + math.pi / 4) * rr * 0.62, math.sin(ang * math.pi / 2 + math.pi / 4) * rr * 0.62
            box = [(px_ + ox - rr * 0.55) * ss, (py_ + oy - rr * 0.55) * ss, (px_ + ox + rr * 0.55) * ss, (py_ + oy + rr * 0.55) * ss]
            dr.ellipse(box, outline=255, width=max(1, int(1.6 * ss)))
        dr.ellipse([(px_ - 2) * ss, (py_ - 2) * ss, (px_ + 2) * ss, (py_ + 2) * ss], fill=255)
    for (px_, py_, nx, ny) in arch_centerline(B + 2, step=46, phase=23):
        # a crocket: a curled leaf leaning up the slope
        tx, ty = -ny, nx
        if ty > 0:
            tx, ty = -tx, -ty
        leaf = []
        for i in range(13):
            s = i / 12
            ang = s * math.pi * 1.35
            rad = 13 * (1 - 0.55 * s)
            lx_ = px_ + nx * (rad * math.sin(ang)) + tx * (rad * (1 - math.cos(ang)) * 0.8)
            ly_ = py_ + ny * (rad * math.sin(ang)) + ty * (rad * (1 - math.cos(ang)) * 0.8)
            leaf.append((lx_ * ss, ly_ * ss))
        dr.line(leaf, fill=255, width=int(2.6 * ss), joint="curve")
        e = leaf[-1]
        dr.ellipse([e[0] - 2.4 * ss, e[1] - 2.4 * ss, e[0] + 2.4 * ss, e[1] + 2.4 * ss], fill=255)
    # capitals where the arcs spring: a gold band with three leaves
    for side in (-1, 1):
        bx0, bx1 = cx + side * w, cx + side * (w + B)
        x0, x1 = min(bx0, bx1) - 6, max(bx0, bx1) + 6
        dr.rectangle([x0 * ss, (ys - 5) * ss, x1 * ss, (ys + 5) * ss], fill=255)
        dr.rectangle([x0 * ss, (ys + 22) * ss, x1 * ss, (ys + 26) * ss], fill=255)
        for i in range(3):
            lx0 = x0 + (x1 - x0) * (i + 0.5) / 3
            dr.chord([(lx0 - 10) * ss, (ys + 2) * ss, (lx0 + 10) * ss, (ys + 26) * ss], 0, 180, fill=255)
    fil = np.asarray(img, np.float32) / 255 * (0.25 + 0.75 * fade)
    gold_line(fil)

    # soft dark wall halo just outside the band so the moulding reads against the bokeh
    halo = np.clip(1 - (d - B) / 90.0, 0, 1) * (d > B) * 0.45 * fade
    rgb, a = over(np.zeros_like(rgb), halo.astype(np.float32), rgb, a)
    return save_premul(rgb, a, os.path.join(OUT, "frame.png"), (FRAME_W, FRAME_H))


def arch_centerline(dist, step, phase=0.0):
    """Points (x, y, outward normal) along the arch at distance dist outside the opening, every step px of arc."""
    w, ys, rho = arch_geometry()
    cx = FRAME_W / 2
    off = rho - w
    R = rho + dist
    phi_apex = math.acos(off / rho)                 # angle (from horizontal) where the arc meets the centre line
    pts = []
    for side in (-1, 1):
        ccx = cx - side * off                       # the right arc is centred left of the middle, and vice versa
        # arc from the springing line (angle 0) up to the apex
        L = R * phi_apex
        s = phase
        while s < L - 18:
            ang = s / R
            nx, ny = side * math.cos(ang), -math.sin(ang)
            pts.append((ccx + nx * R, ys + ny * R, nx, ny))
            s += step
        # jamb from the springing line down
        s = phase
        while ys + s < FRAME_H - 120:
            pts.append((cx + side * (w + dist), ys + s + step * 0.5, side, 0.0))
            s += step
    return pts


# ---------------------------------------------------------------- (c) 3D gold lettering

def gold_text(text, font_path, px_size, depth, path, weight=None, rules=False, ss=3, tracking=0.0):
    font = ImageFont.truetype(font_path, px_size * ss)
    if weight is not None:
        font.set_variation_by_axes([weight])
    # lay out glyph by glyph so tracking (letter-spacing) can open the blackletter caps up a little
    chars = list(text)
    advs = [font.getlength(c) for c in chars]
    kerned = [font.getlength(chars[i] + chars[i + 1]) - advs[i] - advs[i + 1] if i + 1 < len(chars) else 0
              for i in range(len(chars))]
    track = tracking * px_size * ss
    total = sum(advs) + sum(kerned) + track * (len(chars) - 1)
    asc, desc = font.getmetrics()
    pad = int(px_size * ss * 0.32) + depth * ss
    rule_w = int(px_size * ss * 2.2) if rules else 0
    Wt = int(total + 2 * pad + 2 * rule_w)
    Ht = int(asc + desc + 2 * pad)
    m = Image.new("L", (Wt, Ht), 0)
    dr = ImageDraw.Draw(m)
    xcur = pad + rule_w
    for i, c in enumerate(chars):
        dr.text((xcur, pad), c, font=font, fill=255)
        xcur += advs[i] + kerned[i] + track
    if rules:
        # filigree rules either side: a tapering line ending in a lozenge, with a small cross-flory at the inner end
        midy = pad + asc * 0.62
        for side in (-1, 1):
            x_in = (pad + rule_w - px_size * ss * 0.35) if side < 0 else (pad + rule_w + total + px_size * ss * 0.35)
            x_out = x_in + side * (rule_w - px_size * ss * 0.25)
            n_seg = 40
            for j in range(n_seg):
                s0, s1 = j / n_seg, (j + 1) / n_seg
                th = (1 - s0) * 0.07 * px_size * ss + 1.2 * ss
                xa, xb = x_in + (x_out - x_in) * s0, x_in + (x_out - x_in) * s1
                dr.rectangle([min(xa, xb), midy - th / 2, max(xa, xb), midy + th / 2], fill=255)
            lz = px_size * ss * 0.13
            dr.polygon([(x_out, midy - lz), (x_out + side * lz * 1.4, midy), (x_out, midy + lz), (x_out - side * lz * 1.4, midy)], fill=255)
            ci = x_in - side * px_size * ss * 0.02
            cr = px_size * ss * 0.09
            dr.ellipse([ci - cr, midy - cr, ci + cr, midy + cr], fill=255)
            for (ox, oy) in ((0, -1), (0, 1)):
                dr.ellipse([ci - cr * 0.55 + ox * cr * 1.5, midy - cr * 0.55 + oy * cr * 1.6,
                            ci + cr * 0.55 + ox * cr * 1.5, midy + cr * 0.55 + oy * cr * 1.6], fill=255)
            # scroll curls along the rule
            for q in (0.33, 0.66):
                qx = x_in + (x_out - x_in) * q
                for sgn in (-1, 1):
                    rr = px_size * ss * (0.10 if q < 0.5 else 0.08)
                    box = [qx - rr, midy + sgn * rr * 0.15 - (2 * rr if sgn < 0 else 0), qx + rr, midy + sgn * rr * 0.15 + (2 * rr if sgn > 0 else 0)]
                    dr.arc(box, 180 if sgn < 0 else 0, 360 if sgn < 0 else 180, fill=255, width=int(1.4 * ss) + 1)
    mask = np.asarray(m, np.float32) / 255.0
    H_, W_ = mask.shape
    D = depth * ss

    def shift(a_, dx_, dy_):
        out = np.zeros_like(a_)
        dx_, dy_ = int(round(dx_)), int(round(dy_))
        out[dy_:, dx_:] = a_[:H_ - dy_, :W_ - dx_]
        return out

    # bevel: a blurred height field lit from the upper left
    hgt = blur(mask, 2.2 * ss) * mask
    gy, gx = np.gradient(hgt)
    nz = np.full_like(hgt, 1.0 / (6.0 * ss))
    nrm = np.sqrt(gx * gx + gy * gy + nz * nz)
    lx_, ly_, lz_ = -0.55, -0.70, 0.45
    shade = (-gx * lx_ - gy * ly_ + nz * lz_) / nrm
    # metallic ramp down the cap height: pale gold, a deep band below the middle, a warm reflection near the foot
    ys_ = (np.arange(H_, dtype=np.float32) - pad) / max(1, asc)
    ramp = np.interp(ys_, [0.0, 0.18, 0.52, 0.62, 0.85, 1.05], [1.0, 0.92, 0.55, 0.42, 0.78, 0.6])
    base = np.array([1.0, 0.80, 0.40], np.float32)
    face = base[None, None, :] * (0.38 + 0.80 * ramp[:, None, None]) * (0.55 + 0.75 * shade[..., None])
    spec = np.clip(shade - 0.62, 0, 1) ** 1.5 * 2.6
    face = face + spec[..., None] * np.array([1.0, 0.96, 0.84], np.float32)
    face = np.clip(face, 0, 1)

    rgb = np.zeros((H_, W_, 3), np.float32)
    a = np.zeros((H_, W_), np.float32)
    # soft drop shadow of the whole extruded block
    block = mask.copy()
    for kk in range(1, D + 1, max(1, ss)):
        block = np.maximum(block, shift(mask, kk * 0.35, kk))
    # a wide dark halo first, so the gold stays legible over the bright glass behind it
    halo = np.clip(blur(block, px_size * ss * 0.16) * 2.2, 0, 1) * 0.62
    rgb, a = over(rgb, a, np.zeros_like(rgb), halo)
    sh = blur(shift(block, 3 * ss, 5 * ss), 5 * ss) * 0.82
    rgb, a = over(rgb, a, np.zeros_like(rgb), sh)
    # a dark outline (the letters' edge against the shadow)
    outline = np.clip(blur(block, 1.0 * ss) * 3.0, 0, 1)
    rgb, a = over(rgb, a, np.array([0.10, 0.06, 0.02], np.float32) * outline[..., None], outline)
    # extrusion, back to front: bronze getting darker with depth, a lit rim on each step's top edge
    for kk in range(D, 0, -1):
        mk = shift(mask, kk * 0.35, kk)
        tdep = kk / D
        c = np.array([0.62, 0.40, 0.12], np.float32) * (1 - 0.65 * tdep)
        rgb, a = over(rgb, a, c * mk[..., None], mk)
    rgb, a = over(rgb, a, face * mask[..., None], mask)
    img = save_premul(rgb, a, path, (W_ // ss, H_ // ss))
    return img


# ---------------------------------------------------------------- (d) background: ashlar, candle pools, bokeh

def background(w=1280, h=720):
    rng = np.random.default_rng(1919)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u, v = xx / w, yy / h
    base = np.array([0.075, 0.052, 0.045], np.float32)
    col = np.ones((h, w, 3), np.float32) * base
    # ashlar coursing, barely there
    course = 46.0
    row = np.floor(yy / course)
    joint_h = np.abs(yy / course - np.round(yy / course)) * course
    bw = 92.0
    offx = (row % 2) * bw / 2
    joint_v = np.abs((xx + offx) / bw - np.round((xx + offx) / bw)) * bw
    joints = np.clip(1.6 - np.minimum(joint_h, joint_v), 0, 1)
    blockvar = np.asarray(Image.fromarray((rng.random((int(h / course) + 2, int(w / bw) + 3)) * 255).astype(np.uint8), "L")
                          .resize((int(w / bw + 3) * int(bw), int(h / course + 2) * int(course)), Image.NEAREST),
                          np.float32)[:h, :w] / 255.0
    grit = noise((h, w), 30, rng, 3)
    col = col * (0.85 + 0.25 * blockvar + 0.25 * grit)[..., None] * (1 - 0.35 * joints)[..., None]
    # warm candle pools low on both sides and a cool glow where the window will be
    def pool(cx_, cy_, rad, c, k):
        d = np.hypot((u - cx_) * w / h, v - cy_) / rad
        return np.exp(-d * d * k)[..., None] * np.asarray(c, np.float32)
    col = col * 0.7 + pool(0.10, 1.0, 0.45, (0.30, 0.12, 0.035), 1.0) + pool(0.90, 1.0, 0.45, (0.30, 0.12, 0.035), 1.0)
    col = col + pool(0.5, 0.47, 0.55, (0.10, 0.06, 0.12), 1.2)
    # bokeh: out-of-focus candle flames, brighter rims, mostly low and to the sides
    bok = np.zeros((h, w, 3), np.float32)
    for i in range(46):
        side = rng.choice([-1, 1])
        cxp = 0.5 + side * (0.18 + 0.34 * rng.random() ** 0.8)
        cyp = 0.35 + 0.65 * rng.random() ** 0.7
        rad = (6 + 34 * rng.random() ** 2) * (h / 720)
        warm = rng.random()
        c = np.array([1.0, 0.62 + 0.25 * warm, 0.25 + 0.25 * warm], np.float32)
        if rng.random() < 0.12:                            # a few take the colour of the glass
            c = np.array([[0.9, 0.2, 0.25], [0.3, 0.4, 1.0], [1.0, 0.8, 0.3]][rng.integers(3)], np.float32)
        d = np.hypot(xx - cxp * w, yy - cyp * h) / rad
        disc = np.clip((1.0 - d) * rad * 0.35, 0, 1)
        rim = np.exp(-((d - 0.88) / 0.10) ** 2) * (d < 1.05)
        strength = 0.03 + 0.13 * rng.random()
        bok += ((disc * 0.55 + rim * 0.45) * strength)[..., None] * c
    col = col + blur(bok, 1.2)
    # vignette
    vig = np.clip(1 - (np.hypot((u - 0.5) * 1.15, (v - 0.47) * 1.0) / 0.78) ** 2.2, 0, 1)
    col = col * (0.12 + 0.88 * vig ** 1.4)[..., None]
    col = np.clip(col, 0, 1)
    img = Image.fromarray(np.round(col * 255).astype(np.uint8), "RGB")
    img.save(os.path.join(OUT, "background.png"), optimize=True)
    return img


# ---------------------------------------------------------------- previews (compose like SplashArt.java)

def load_layers():
    L = {k: Image.open(os.path.join(OUT, k + ".png")).convert("RGBA")
         for k in ("background", "frame", "window", "rays", "glow", "title", "byline")}
    return L


def compose(L, W, H, gui_scale=None, loading=False, progress=0.62):
    out = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    bg = L["background"]
    s = max(W / bg.width, H / bg.height)
    bgr = bg.resize((int(bg.width * s) + 1, int(bg.height * s) + 1), Image.BICUBIC)
    out.alpha_composite(bgr, ((W - bgr.width) // 2, (H - bgr.height) // 2))
    cx = W / 2

    def put(img, cx_, cy_, h_, add=False, alpha=1.0):
        w_ = int(round(img.width * h_ / img.height))
        im = img.resize((w_, int(round(h_))), Image.LANCZOS)
        if alpha < 1.0:
            arr = np.asarray(im, np.float32)
            arr[..., 3] *= alpha
            im = Image.fromarray(arr.astype(np.uint8), "RGBA")
        x0, y0 = int(round(cx_ - w_ / 2)), int(round(cy_ - h_ / 2))
        if add:
            base = np.asarray(out, np.float32)
            src = np.asarray(im, np.float32)
            xa, ya = max(0, x0), max(0, y0)
            xb, yb = min(W, x0 + w_), min(H, y0 + im.height)
            sa = src[ya - y0:yb - y0, xa - x0:xb - x0]
            base[ya:yb, xa:xb, :3] = np.clip(base[ya:yb, xa:xb, :3] + sa[..., :3] * sa[..., 3:4] / 255.0, 0, 255)
            out.paste(Image.fromarray(base.astype(np.uint8), "RGBA"))
        else:
            out.alpha_composite(im, (x0, y0)) if x0 >= 0 and y0 >= 0 else out.paste(im, (x0, y0), im)

    put(L["glow"], cx, WINDOW_CY * H, WINDOW_D * H * 1.5, add=True, alpha=0.35)
    put(L["frame"], cx, H / 2, H)
    put(L["window"], cx, WINDOW_CY * H, WINDOW_D * H, alpha=0.92)
    put(L["rays"], cx, WINDOW_CY * H, WINDOW_D * H * 1.25, add=True, alpha=0.22)
    put(L["glow"], 0.07 * W, 0.93 * H, 0.42 * H, add=True, alpha=0.35)
    put(L["glow"], 0.93 * W, 0.93 * H, 0.42 * H, add=True, alpha=0.35)
    put(L["title"], cx, TITLE_CY * H, TITLE_H * H)
    put(L["byline"], cx, BYLINE_CY * H, BYLINE_H * H)
    d = ImageDraw.Draw(out)
    if loading:
        bw, by = 0.34 * H * 1.6, 0.90 * H
        d.rectangle([cx - bw / 2 - 2, by - 4, cx + bw / 2 + 2, by + 4], fill=(20, 14, 10, 255), outline=(196, 150, 70, 255))
        d.rectangle([cx - bw / 2 + 1, by - 1, cx - bw / 2 + 1 + (bw - 2) * progress, by + 1], fill=(240, 196, 100, 255))
    if gui_scale:
        # mock vanilla buttons (200x20 GUI units) where TitleScreen and our Skirmish button put them
        g = gui_scale
        gh = H // g
        rows = [gh // 4 + 48 - 26, gh // 4 + 48, gh // 4 + 48 + 24, gh // 4 + 48 + 48, gh // 4 + 48 + 84]
        for i, ry in enumerate(rows):
            wbtn = 200 if i < 4 else 98
            for off in ((0,) if i < 4 else (-51, 51)):
                x0 = cx + (off - wbtn / 2) * g
                d.rectangle([x0, ry * g, x0 + wbtn * g, (ry + 20) * g], fill=(24, 20, 18, 235), outline=(186, 142, 62, 255), width=max(1, g // 2))
    return out.convert("RGB")


def previews():
    L = load_layers()
    compose(L, 1920, 1080, gui_scale=2).save(os.path.join(DESIGN, "splash_preview_title_16x9_gui2.png"), optimize=True)
    compose(L, 2560, 1080, gui_scale=4).save(os.path.join(DESIGN, "splash_preview_title_21x9_gui4.png"), optimize=True)
    compose(L, 1920, 1080, loading=True).save(os.path.join(DESIGN, "splash_preview_loading.png"), optimize=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    if "--previews-only" not in sys.argv:
        background()
        rays()
        glow()
        frame()
        rose_window()
        gold_text("BEYOND AND TABS", TITLE_FONT, 150, 9, os.path.join(OUT, "title.png"), weight=800, tracking=0.04)
        gold_text("Developed by Lovish", BYLINE_FONT, 74, 4, os.path.join(OUT, "byline.png"), rules=True)
    previews()
    total = 0
    for f in sorted(os.listdir(OUT)):
        sz = os.path.getsize(os.path.join(OUT, f))
        total += sz
        im = Image.open(os.path.join(OUT, f))
        print(f"{f:16s} {im.width:5d}x{im.height:<5d} {sz / 1024:8.1f} KiB")
    print(f"total {total / 1024 / 1024:.2f} MiB")


if __name__ == "__main__":
    main()
