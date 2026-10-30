#!/usr/bin/env python3
"""Mist sprites for the living valley mist (ambient/mist/MistParticle; lovish Oct 28: "can we make the fog more
animated and alive or rich").

Four soft noisy blobs, 128 x 128 (the particle atlas samples nearest-texel: at 64 px a 6-block sprite showed ~3.4
screen pixels per texel at RTS zoom, stair-steps in a gradient this faint), original art drawn from numbers:
    mist_0 .. mist_3   a round-ish blob whose edge wanders (two octaves of value noise bend the radius) and whose body
                       is broken by softer fractal noise, so overlapping sprites never show a disc
Every pixel carries the same near-white colour (the game tints it cool grey or dusk-warm, MistMath.colour); only the
alpha changes, falling smoothly to exactly 0 well inside the border - "premultiplied-looking": no dark fringe where
the atlas filters a transparent texel into a visible one. Peak alpha ~0.9: the game's own alpha (0.05-0.18) does the
thinning. Deterministic (a fixed seed per variant). numpy + pillow + stdlib only (CI has no scipy).

    python3 tools/mist_sprites.py           # writes bar/src/main/resources/assets/reignofnether/textures/particle/mist_*.png
    python3 tools/mist_sprites.py --check   # exits 1 if the committed files differ from a fresh draw
"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "particle")
N = 128
VARIANTS = 4
SEED = 2810          # Oct 28
RGB = (236, 238, 240)   # near white, a hair cool; never 255 (the game's tint keeps it grey)
PEAK = 0.9


def value_noise(rng, n, cells):
    """Smooth value noise on an n x n image from a (cells+1)^2 random lattice, bicubic-smoothstep interpolated (no
    scipy). Tiles nothing; 0..1."""
    lat = rng.random((cells + 1, cells + 1))
    t = np.linspace(0, cells, n, endpoint=False) + 0.5 * cells / n
    i = np.floor(t).astype(int)
    f = t - i
    f = f * f * (3 - 2 * f)
    i1 = np.minimum(i + 1, cells)
    a = lat[np.ix_(i, i)]
    b = lat[np.ix_(i, i1)]
    c = lat[np.ix_(i1, i)]
    d = lat[np.ix_(i1, i1)]
    fy, fx = f[:, None], f[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fractal(rng, n, base_cells, octaves):
    out = np.zeros((n, n))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        out += amp * value_noise(rng, n, base_cells * 2 ** o)
        total += amp
        amp *= 0.5
    return out / total


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def blob_alpha(k):
    """Alpha 0..1 of variant k."""
    rng = np.random.default_rng(SEED + 97 * k)
    y, x = np.mgrid[0:N, 0:N]
    cx = cy = (N - 1) / 2
    dx, dy = (x - cx) / (N / 2), (y - cy) / (N / 2)
    stretch = 1.0 + 0.18 * (k % 2)                 # two of the four a little oval
    r = np.hypot(dx / stretch, dy)
    # the edge wanders: a low-frequency field bends the radius by up to ~18 %
    bend = fractal(rng, N, 3, 2) - 0.5
    r = r * (1 + 0.36 * bend)
    # body: a broad soft falloff, zero from r 0.92 on
    # r5 (eyes run 40821790726): r4's broad plateau (1 - smoothstep(0.15, 0.92)) ** 1.3 drew a faint rim round every
    # wisp where many overlap - a gaussian has no shoulder, so a veil of them shows no outlines
    body = np.exp(-3.4 * r * r)
    # broken up by softer fractal noise: wisps, never a hard hole
    wisp = fractal(rng, N, 4, 4)
    wisp = smoothstep(0.2, 0.8, wisp)
    # a round window that always closes before the border: the bend and the oval can pull the body's edge out to it
    window = 1 - smoothstep(0.7, 0.94, np.hypot(dx, dy))
    a = body * (0.55 + 0.45 * wisp) * window
    a = a / max(a.max(), 1e-9) * PEAK
    # the border ring always clear, so no texel bleeds into a neighbour sprite in the atlas
    a[:2, :] = 0
    a[-2:, :] = 0
    a[:, :2] = 0
    a[:, -2:] = 0
    return a


def draw(k):
    a = np.round(blob_alpha(k) * 255).astype(np.uint8)
    img = np.zeros((N, N, 4), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = RGB
    img[..., 3] = a
    return Image.fromarray(img, "RGBA")


def names():
    return ["mist_%d" % k for k in range(VARIANTS)]


def main(argv):
    check = "--check" in argv
    os.makedirs(OUT, exist_ok=True)
    bad = []
    for k, name in enumerate(names()):
        im = draw(k)
        path = os.path.join(OUT, name + ".png")
        if check:
            if not os.path.isfile(path) or not np.array_equal(np.asarray(Image.open(path).convert("RGBA")), np.asarray(im)):
                bad.append(name)
        else:
            im.save(path, optimize=True)
    if check:
        if bad:
            print("mist sprites differ from a fresh draw:", ", ".join(bad))
            return 1
        print("mist sprites match")
        return 0
    print("wrote", ", ".join(names()), "to", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
