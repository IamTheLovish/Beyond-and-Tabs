#!/usr/bin/env python3
"""The VFX atlas (barfx/vfx, design/vfx.md) - every texture the data-driven effects draw, our own and procedural.

One 2048x2048 RGBA atlas, a 4x4 grid of 512px tiles, so the whole effects pass binds ONE texture and batches into one
buffer per render type (additive light, translucent smoke). RGB is white; the ALPHA is the mask - the vertex colour
(the effect's tint, from its JSON) times the alpha is what reaches the screen, added on top (light) or blended (smoke).
Shapes are drawn 2x supersampled and filtered down; sigils get a soft halo blurred from their own lines so a ring
reads as light spilling onto the ground, not a flat neon decal (lovish, Oct 22: "hyper-realism first, magic is an
accent").

The glyphs are OUR alphabet: each one is a vertical stem plus 1-3 angular branches picked by a fixed seed on a 3x5
lattice - read as carved runes at RTS distance, copied from nobody.

Tile index (row-major) - keep in sync with barfx/vfx/VfxAtlas.java (the GameTest/JUnit side checks the count):
    0 glow          soft light falloff (cores, flares, ground light pools)
    1 ring_glyph    double ring with a band of glyphs
    2 ring_thin     thin ring, tick marks and a dashed inner ring
    3 sigil_star    heptagram {7/3} in two circles with node dots
    4 sigil_tiles   a disc of glowing tiles with dark grout (the ground "tile sigil")
    5 sigil_oct     octagram {8/3}, outer glyph ring, centre eye
    6 beam          vertical light shaft: soft core, streaks (wraps vertically)
    7 smoke         soft billow (fBm), alpha only
    8 spark         stretched streak (velocity-aligned sparks)
    9 glint         four-point sparkle with a soft core (helix sparkles)
   10 trail         ribbon across V, filaments along U (wraps along U)
   11 shockwave     soft ring gradient (ground shock / expanding pulse)
   12 rune_disc     a disc of radial glyph segments (rune-disc ladders)
   13 ember         small hot irregular blob
   14 heat          faint wavy streaks (heat shimmer over fire - alpha-blended, very low)
   15 (empty)

Deterministic (fixed seeds). Writes bar/src/main/resources/assets/reignofnether/textures/vfx/vfx_atlas.png.
    python3 tools/gen_vfx_textures.py [--preview DIR] [--check]
--check regenerates in memory and fails if the committed atlas differs (CI-safe, no write).
"""
import argparse
import io
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "vfx", "vfx_atlas.png")
T = 512          # tile size
SS = 2           # supersampling
GRID = 4
NAMES = ["glow", "ring_glyph", "ring_thin", "sigil_star", "sigil_tiles", "sigil_oct", "beam", "smoke", "spark",
         "glint", "trail", "shockwave", "rune_disc", "ember", "heat", "empty"]


# ------------------------------------------------------------------ helpers

def coords(n):
    """Centred coordinates in [-1, 1] for an n x n tile."""
    a = (np.arange(n) + 0.5) / n * 2 - 1
    return np.meshgrid(a, a)


def value_noise(n, cells, rng, wrap=True):
    """Smooth periodic value noise, n x n, `cells` lattice cells per side."""
    g = rng.random((cells, cells))
    x = np.arange(n) / n * cells
    xi = np.floor(x).astype(int)
    xf = x - xi
    xf = xf * xf * (3 - 2 * xf)
    x0, x1 = xi % cells, (xi + 1) % cells
    a = g[np.ix_(x0, x0)]
    b = g[np.ix_(x0, x1)]
    c = g[np.ix_(x1, x0)]
    d = g[np.ix_(x1, x1)]
    fx = xf[None, :]
    fy = xf[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(n, rng, base=4, octaves=5):
    out = np.zeros((n, n))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += amp * value_noise(n, base * 2 ** o, rng)
        tot += amp
        amp *= 0.5
    return out / tot


def canvas():
    return Image.new("L", (T * SS, T * SS), 0)


def down(img):
    return np.asarray(img.resize((T, T), Image.LANCZOS), dtype=np.float64) / 255.0


def with_halo(sharp, radius=10, amount=0.45):
    """Lines plus a soft halo blurred from themselves: light that spills, the 'physical glow' look."""
    img = Image.fromarray(np.uint8(np.clip(sharp, 0, 1) * 255))
    blur = np.asarray(img.filter(ImageFilter.GaussianBlur(radius)), dtype=np.float64) / 255.0
    wide = np.asarray(img.filter(ImageFilter.GaussianBlur(radius * 3)), dtype=np.float64) / 255.0
    return np.clip(sharp + amount * blur * 1.6 + amount * 0.6 * wide * 2.0, 0, 1)


def circle(d, cx, cy, r, w, fill=255):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=fill, width=max(1, int(w)))


# ------------------------------------------------------------------ glyphs (our own alphabet)

LATTICE = [(x, y) for y in range(5) for x in range(3)]


def make_glyphs(count, seed):
    """Each glyph: a stem (1,0)-(1,4) plus 1-3 angular branches between lattice points. Deterministic."""
    rng = np.random.default_rng(seed)
    glyphs = []
    for _ in range(count):
        strokes = [((1, 0), (1, 4))]
        for _b in range(int(rng.integers(1, 4))):
            y0 = int(rng.integers(0, 5))
            side = 0 if rng.random() < 0.5 else 2
            y1 = int(np.clip(y0 + rng.integers(-2, 3), 0, 4))
            if y1 == y0 and rng.random() < 0.5:
                y1 = int(np.clip(y0 + 1, 0, 4))
            strokes.append(((1, y0), (side, y1)))
        if rng.random() < 0.3:
            y = int(rng.integers(1, 4))
            strokes.append(((0, y), (2, y)))
        glyphs.append(strokes)
    return glyphs


def draw_glyph(d, strokes, cx, cy, ang, h, w_line):
    """A glyph of height h centred at (cx, cy); its up axis points along the radius (angle ang), so the band reads
    outward like letters on a coin."""
    up = (math.cos(ang), math.sin(ang))
    rt = (-math.sin(ang), math.cos(ang))
    sx, sy = h * 0.28, h / 4

    def p(pt):
        lx, ly = (pt[0] - 1) * sx, (pt[1] - 2) * sy
        return (cx + rt[0] * lx + up[0] * ly, cy + rt[1] * lx + up[1] * ly)

    for a, b in strokes:
        d.line([p(a), p(b)], fill=255, width=int(w_line))


def glyph_band(d, c, r_mid, h, count, glyphs, w_line, offset=0.0):
    for i in range(count):
        ang = offset + 2 * math.pi * i / count
        draw_glyph(d, glyphs[i % len(glyphs)], c + math.cos(ang) * r_mid, c + math.sin(ang) * r_mid, ang, h, w_line)


# ------------------------------------------------------------------ tiles

def t_glow():
    x, y = coords(T)
    r = np.sqrt(x * x + y * y)
    # inverse-square-ish falloff with a soft hot core, tapered to exactly 0 at the tile edge (no square cut)
    v = 1.0 / (1.0 + (r / 0.16) ** 2)
    taper = np.clip((1 - r) / 0.25, 0, 1) ** 2
    return np.clip(v * taper * 1.05, 0, 1)


def t_ring_glyph():
    img = canvas()
    d = ImageDraw.Draw(img)
    n = T * SS
    c = n / 2
    R = n * 0.47
    circle(d, c, c, R, n * 0.010)
    circle(d, c, c, R * 0.80, n * 0.007)
    circle(d, c, c, R * 0.765, n * 0.003)
    glyph_band(d, c, R * 0.90, R * 0.13, 28, make_glyphs(28, 4401), n * 0.0055)
    # small diamond studs between the outer rings at the quarter points
    for k in range(8):
        a = k * math.pi / 4
        px, py = c + math.cos(a) * R * 0.80, c + math.sin(a) * R * 0.80
        s = n * 0.012
        d.polygon([(px - s, py), (px, py - s), (px + s, py), (px, py + s)], fill=255)
    return with_halo(down(img), 8, 0.4)


def t_ring_thin():
    img = canvas()
    d = ImageDraw.Draw(img)
    n = T * SS
    c = n / 2
    R = n * 0.47
    circle(d, c, c, R, n * 0.006)
    for i in range(72):
        a = 2 * math.pi * i / 72
        l = 0.06 if i % 6 == 0 else 0.03
        d.line([(c + math.cos(a) * R, c + math.sin(a) * R),
                (c + math.cos(a) * R * (1 - l), c + math.sin(a) * R * (1 - l))], fill=255, width=int(n * 0.004))
    # dashed inner ring
    for i in range(48):
        a0 = 2 * math.pi * i / 48
        d.arc([c - R * 0.86, c - R * 0.86, c + R * 0.86, c + R * 0.86], math.degrees(a0), math.degrees(a0) + 4.5,
              fill=255, width=int(n * 0.005))
    return with_halo(down(img), 6, 0.35)


def star_points(c, r, k, step, rot=-math.pi / 2):
    pts = [(c + math.cos(rot + 2 * math.pi * i / k) * r, c + math.sin(rot + 2 * math.pi * i / k) * r) for i in range(k)]
    order = [(i * step) % k for i in range(k + 1)]
    return pts, [pts[i] for i in order]


def t_sigil_star():
    img = canvas()
    d = ImageDraw.Draw(img)
    n = T * SS
    c = n / 2
    R = n * 0.47
    circle(d, c, c, R, n * 0.008)
    circle(d, c, c, R * 0.93, n * 0.004)
    pts, path = star_points(c, R * 0.93, 7, 3)
    d.line(path, fill=255, width=int(n * 0.006), joint="curve")
    circle(d, c, c, R * 0.36, n * 0.005)
    for (px, py) in pts:
        s = n * 0.016
        d.ellipse([px - s, py - s, px + s, py + s], fill=255)
    return with_halo(down(img), 9, 0.45)


def t_sigil_tiles():
    """Glowing floor tiles laid in rings round a centre stone; each tile a slightly different brightness, grout dark,
    the tile faces brightest at their edges (light leaking up through the joints) - a sigil set INTO the ground."""
    x, y = coords(T)
    r = np.sqrt(x * x + y * y)
    th = np.arctan2(y, x)
    rng = np.random.default_rng(4404)
    out = np.zeros((T, T))
    rings = [(0.00, 0.16, 1), (0.18, 0.40, 8), (0.42, 0.66, 14), (0.68, 0.94, 22)]
    for r0, r1, k in rings:
        inside = (r >= r0) & (r < r1)
        seg = np.floor(((th + math.pi) / (2 * math.pi)) * k).astype(int) % k
        bright = rng.uniform(0.35, 1.0, k)
        tile = bright[seg]
        # distance to the tile's own edges (radial and angular) -> grout and a rim glow
        dr = np.minimum(r - r0, r1 - r)
        if k > 1:
            fa = ((th + math.pi) / (2 * math.pi)) * k
            da = np.minimum(fa - np.floor(fa), np.ceil(fa) - fa) * (2 * math.pi / k) * np.maximum(r, 1e-3)
            de = np.minimum(dr, da)
        else:
            de = dr
        grout = np.clip((de - 0.006) / 0.006, 0, 1)
        rim = np.exp(-de / 0.025)
        v = tile * (0.35 + 0.65 * rim) * grout
        out = np.where(inside, v, out)
    out *= 0.85 + 0.15 * fbm(T, rng, 8, 3)
    return with_halo(np.clip(out, 0, 1), 6, 0.3)


def t_sigil_oct():
    img = canvas()
    d = ImageDraw.Draw(img)
    n = T * SS
    c = n / 2
    R = n * 0.47
    circle(d, c, c, R, n * 0.007)
    # an octagram {8/3} (not a hexagram or pentagram: no religious sign reads into our circles)
    _, oct_path = star_points(c, R * 0.70, 8, 3)
    d.line(oct_path, fill=255, width=int(n * 0.006), joint="curve")
    circle(d, c, c, R * 0.70, n * 0.004)
    glyph_band(d, c, R * 0.85, R * 0.12, 20, make_glyphs(20, 4405), n * 0.005, offset=0.1)
    circle(d, c, c, R * 0.13, n * 0.006)
    d.ellipse([c - R * 0.04, c - R * 0.04, c + R * 0.04, c + R * 0.04], fill=255)
    return with_halo(down(img), 8, 0.4)


def t_beam():
    """Vertical shaft: a gaussian core across U, long soft streaks along V (wraps in V so it scrolls upward)."""
    rng = np.random.default_rng(4406)
    x, _ = coords(T)
    core = np.exp(-(x / 0.22) ** 2) * 0.75 + np.exp(-(x / 0.06) ** 2) * 0.35
    streak_cols = value_noise(T, 24, rng)[0:1, :]           # per-column brightness
    along = value_noise(T, 6, rng)                            # slow variation down the shaft
    streaks = 0.75 + 0.25 * (streak_cols * 0.6 + along.T * 0.4)
    edge = np.clip((1 - np.abs(x)) / 0.15, 0, 1)
    return np.clip(core * streaks * edge, 0, 1)


def t_smoke():
    rng = np.random.default_rng(4407)
    x, y = coords(T)
    r = np.sqrt(x * x + y * y)
    n = fbm(T, rng, 3, 5)
    puff = np.clip(1 - r / 0.95, 0, 1)
    v = np.clip((n - 0.22) * 1.7 + 0.25 * puff, 0, 1) * puff
    return np.clip(v * 1.15, 0, 1)


def t_spark():
    x, y = coords(T)
    # long along U (x), thin across V (y); hot head at +x, tail fading to -x
    across = np.exp(-(y / 0.07) ** 2)
    along = np.clip((x + 1) / 2, 0, 1) ** 1.6 * np.clip((1 - x) / 0.12, 0, 1)
    return np.clip(across * along * 1.1, 0, 1)


def t_glint():
    x, y = coords(T)
    r = np.sqrt(x * x + y * y)
    core = 1.0 / (1.0 + (r / 0.06) ** 2)
    spikes = np.exp(-np.abs(y) / 0.012) * np.exp(-np.abs(x) / 0.45) + np.exp(-np.abs(x) / 0.012) * np.exp(-np.abs(y) / 0.45)
    taper = np.clip((1 - r) / 0.3, 0, 1)
    return np.clip((core + 0.8 * spikes) * taper, 0, 1)


def t_trail():
    rng = np.random.default_rng(4410)
    _, y = coords(T)
    across = np.exp(-(y / 0.35) ** 2) * 0.7 + np.exp(-(y / 0.10) ** 2) * 0.3
    fil = value_noise(T, 16, rng)
    fil = 0.7 + 0.3 * fil
    return np.clip(across * fil, 0, 1)


def t_shockwave():
    x, y = coords(T)
    r = np.sqrt(x * x + y * y)
    ring = np.exp(-((r - 0.85) / 0.06) ** 2) + 0.25 * np.exp(-((r - 0.85) / 0.2) ** 2)
    inner = np.clip(r / 0.85, 0, 1) ** 4 * 0.15
    return np.clip((ring + inner) * np.clip((1 - r) / 0.06, 0, 1), 0, 1)


def t_rune_disc():
    img = canvas()
    d = ImageDraw.Draw(img)
    n = T * SS
    c = n / 2
    R = n * 0.47
    circle(d, c, c, R, n * 0.012)
    circle(d, c, c, R * 0.55, n * 0.008)
    glyphs = make_glyphs(12, 4412)
    for i in range(12):
        a = 2 * math.pi * i / 12
        d.line([(c + math.cos(a) * R * 0.55, c + math.sin(a) * R * 0.55), (c + math.cos(a) * R, c + math.sin(a) * R)],
               fill=255, width=int(n * 0.004))
        am = a + math.pi / 12
        draw_glyph(d, glyphs[i], c + math.cos(am) * R * 0.78, c + math.sin(am) * R * 0.78, am, R * 0.24, n * 0.006)
    circle(d, c, c, R * 0.2, n * 0.01)
    return with_halo(down(img), 8, 0.4)


def t_ember():
    rng = np.random.default_rng(4413)
    x, y = coords(T)
    r = np.sqrt(x * x + y * y)
    n = fbm(T, rng, 4, 3)
    v = 1.0 / (1.0 + (r / (0.12 + 0.05 * n)) ** 3)
    return np.clip(v * np.clip((1 - r) / 0.3, 0, 1), 0, 1)


def t_heat():
    rng = np.random.default_rng(4414)
    x, y = coords(T)
    wob = value_noise(T, 8, rng)
    lines = 0.5 + 0.5 * np.sin((x + 0.15 * wob) * 18 * math.pi)
    fade = np.exp(-(x / 0.6) ** 2) * np.clip((1 - np.abs(y)) / 0.4, 0, 1)
    return np.clip(lines * fade * 0.35, 0, 1)


TILES = [t_glow, t_ring_glyph, t_ring_thin, t_sigil_star, t_sigil_tiles, t_sigil_oct, t_beam, t_smoke, t_spark,
         t_glint, t_trail, t_shockwave, t_rune_disc, t_ember, t_heat, None]


def build():
    alpha = np.zeros((T * GRID, T * GRID))
    for i, fn in enumerate(TILES):
        if fn is None:
            continue
        a = fn()
        assert a.shape == (T, T), (NAMES[i], a.shape)
        # a 2px clear border on every tile except the wrapping ones, so bilinear filtering never bleeds a neighbour in
        if NAMES[i] not in ("beam", "trail"):
            a[:2, :] = 0; a[-2:, :] = 0; a[:, :2] = 0; a[:, -2:] = 0
        row, col = divmod(i, GRID)
        alpha[row * T:(row + 1) * T, col * T:(col + 1) * T] = a
    rgba = np.zeros((T * GRID, T * GRID, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.uint8(np.round(np.clip(alpha, 0, 1) * 255))
    return Image.fromarray(rgba, "RGBA")


def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--preview", help="also write each tile over a dark ground, tinted, for review")
    ap.add_argument("--check", action="store_true", help="fail if the committed atlas differs from a fresh build")
    args = ap.parse_args()
    img = build()
    if args.check:
        if not os.path.exists(OUT):
            print("vfx atlas missing: run tools/gen_vfx_textures.py")
            return 1
        old = np.asarray(Image.open(OUT).convert("RGBA"))
        new = np.asarray(img)
        diff = int(np.abs(old.astype(int) - new.astype(int)).max())
        if diff > 1:
            print("vfx atlas is stale (max diff %d): run tools/gen_vfx_textures.py" % diff)
            return 1
        print("vfx atlas up to date")
        return 0
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(png_bytes(img))
    print("wrote %s (%d KB)" % (os.path.relpath(OUT, ROOT), os.path.getsize(OUT) // 1024))
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
        a = np.asarray(img)[..., 3].astype(np.float64) / 255
        tint = np.array([1.0, 0.78, 0.42])
        ground = np.full(a.shape + (3,), 0.12)
        rgb = np.clip(ground + a[..., None] * tint, 0, 1)
        Image.fromarray(np.uint8(rgb * 255)).resize((1024, 1024), Image.LANCZOS).save(
            os.path.join(args.preview, "vfx_atlas_preview.png"))
        print("preview in %s" % args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main())
