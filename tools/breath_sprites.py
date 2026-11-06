#!/usr/bin/env python3
"""Flame sprites for a dragon's fire breath (barfx/BreathFx; lovish Nov 5: "the bone dragon should get a blue fire
breath attack from the air"; the blind Fable critic's round 3: untextured soft discs read as "a jet of blue liquid",
"soap foam" - "no tongues, no turbulence").

One 512 x 256 atlas, 4 x 2 frames of 128 x 128, original art drawn from numbers (no game's texture, no photo):
    frames 0-5   flame TONGUES lying along +u (the direction of motion; BarFxClient draws each quad along the
                 particle's screen velocity): a rounded head at +u, a tail that thins and frays toward -u, the edge
                 broken by fractal noise stretched along the flow, a hotter (brighter) core along the axis
    frames 6-7   ragged PUFFS (no direction): the splash and the floor fire, a turbulent blob with holes
Grey-white (luminance = how hot, the game tints it by the vertex colour) and alpha that falls to exactly 0 well inside
each frame's border, so the atlas never bleeds a neighbour in. Deterministic (fixed seeds). numpy + pillow + stdlib
only (CI has no scipy).

    python3 tools/breath_sprites.py           # writes bar/src/main/resources/assets/reignofnether/textures/fx/breath_flame.png
    python3 tools/breath_sprites.py --check   # exits 1 if the committed atlas differs from a fresh draw
"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "fx", "breath_flame.png")
N = 128
COLS, ROWS = 4, 2
TONGUES, PUFFS = 6, 2
SEED = 1105          # Nov 5


def value_noise(rng, h, w, cy, cx):
    """Smooth value noise on an h x w image from a (cy+1) x (cx+1) random lattice, smoothstep interpolated."""
    lat = rng.random((cy + 1, cx + 1))
    ty = np.linspace(0, cy, h, endpoint=False)
    tx = np.linspace(0, cx, w, endpoint=False)
    iy, ix = np.floor(ty).astype(int), np.floor(tx).astype(int)
    fy, fx = ty - iy, tx - ix
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    iy1, ix1 = np.minimum(iy + 1, cy), np.minimum(ix + 1, cx)
    a, b = lat[np.ix_(iy, ix)], lat[np.ix_(iy, ix1)]
    c, d = lat[np.ix_(iy1, ix)], lat[np.ix_(iy1, ix1)]
    fy, fx = fy[:, None], fx[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fractal(rng, cy, cx, octaves):
    out = np.zeros((N, N))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        out += amp * value_noise(rng, N, N, cy * 2 ** o, cx * 2 ** o)
        total += amp
        amp *= 0.5
    return out / total


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def window():
    """1 inside, falling to 0 before the frame's border (a soft round window)."""
    y, x = np.mgrid[0:N, 0:N]
    r = np.hypot((x - (N - 1) / 2) / (N / 2), (y - (N - 1) / 2) / (N / 2))
    return 1 - smoothstep(0.78, 0.96, r)


def tongue(k):
    """(luminance, alpha) of tongue k: head at +u (image right), tail toward -u (left)."""
    rng = np.random.default_rng(SEED + 31 * k)
    y, x = np.mgrid[0:N, 0:N]
    u = (x - (N - 1) / 2) / (N / 2)          # -1 tail .. +1 head
    v = (y - (N - 1) / 2) / (N / 2)
    # the body's half-width along u: round at the head (u 0.55), widest just behind it, thinning to the tail
    head = 0.55 + 0.08 * (k % 3) / 2
    half = np.where(u > head - 0.35, np.sqrt(np.clip(1 - ((u - (head - 0.35)) / 0.4) ** 2, 0, 1)) * 0.42,
                    0.42 * np.clip((u + 0.95) / (head + 0.6), 0, 1) ** 1.4)
    # turbulence stretched along the flow (fewer cells along u than across): it bends the edge and breaks the body
    bend = fractal(rng, 6, 2, 3) - 0.5
    wave = 0.12 * np.sin(u * (5 + k) + rng.random() * 6.28) * (1 - u) * 0.5
    vv = v + 0.35 * bend * (1.1 - u) * 0.6 + wave
    edge = np.abs(vv) / np.maximum(half, 1e-3)
    body = 1 - smoothstep(0.55, 1.0, edge)
    holes = fractal(rng, 8, 3, 4)
    frays = smoothstep(0.25, 0.75, holes + 0.35 * (u + 0.2))      # the tail frays into wisps, the head stays whole
    a = body * (0.35 + 0.65 * frays) * window()
    # hot core along the axis, toward the head
    core = np.exp(-(vv / np.maximum(half * 0.45, 1e-3)) ** 2) * smoothstep(-0.6, 0.4, u)
    lum = np.clip(0.55 + 0.45 * core + 0.12 * (holes - 0.5), 0, 1)
    return lum, np.clip(a / max(a.max(), 1e-9), 0, 1)


def puff(k):
    """(luminance, alpha) of puff k: a turbulent round flame with holes, no direction."""
    rng = np.random.default_rng(SEED + 977 * (k + 1))
    y, x = np.mgrid[0:N, 0:N]
    dx, dy = (x - (N - 1) / 2) / (N / 2), (y - (N - 1) / 2) / (N / 2)
    bend = fractal(rng, 3, 3, 3) - 0.5
    r = np.hypot(dx, dy) * (1 + 0.5 * bend)
    body = np.exp(-3.0 * r * r)
    holes = smoothstep(0.3, 0.7, fractal(rng, 5, 5, 4))
    a = body * (0.3 + 0.7 * holes) * window()
    lum = np.clip(0.5 + 0.5 * np.exp(-6 * r * r) + 0.1 * (holes - 0.5), 0, 1)
    return lum, np.clip(a / max(a.max(), 1e-9), 0, 1)


def frame(i):
    return tongue(i) if i < TONGUES else puff(i - TONGUES)


def draw():
    img = np.zeros((ROWS * N, COLS * N, 4), np.uint8)
    for i in range(COLS * ROWS):
        lum, a = frame(i)
        a[:2, :] = 0
        a[-2:, :] = 0
        a[:, :2] = 0
        a[:, -2:] = 0
        r, c = divmod(i, COLS)
        g = np.round(lum * 255).astype(np.uint8)
        tile = img[r * N:(r + 1) * N, c * N:(c + 1) * N]
        tile[..., 0], tile[..., 1], tile[..., 2] = g, g, g
        tile[..., 3] = np.round(a * 255).astype(np.uint8)
    return Image.fromarray(img, "RGBA")


def main(argv):
    im = draw()
    if "--check" in argv:
        same = os.path.isfile(OUT) and np.array_equal(np.asarray(Image.open(OUT).convert("RGBA")), np.asarray(im))
        print("breath sprites match" if same else "breath sprites differ from a fresh draw (run tools/breath_sprites.py)")
        return 0 if same else 1
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT, optimize=True)
    print("wrote", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
