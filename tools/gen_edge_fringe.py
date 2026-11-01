#!/usr/bin/env python3
"""The edge-overlay fringe sprites (lovish, Oct 30: "build edge overlays", from his reference of a cracked dry-earth
path fraying into the grass): ragged grass that spills over the top of a path / dirt / sand / gravel block where it
meets grass, so the border stops reading as a hard square tile line from the RTS camera.

environment/edge/EdgeOverlayBakedModel draws them (EdgeOverlayRules decides which piece goes where). Each sprite is
rooted along its TOP edge (v = 0; the model turns the quad, never the sprite) and keeps every texel within REACH
(5/16 of the block) of its root, the quad's size:
    edge_{a,b,c}   a ragged strip along one grass side: a band 6-25% deep (the same depth at both ends, so strips
                   of different variants join along a long edge), single blades and clover clumps reaching past it
    outer_{a,b}    the corner where two grass sides meet (sprite NW corner): a ragged fillet that rounds the inside
                   of the two strips' L
    inner_{a,b}    a small tuft in a corner whose only grass neighbour is the diagonal one
Each with its LabPBR 1.3 maps through tools/materials.py, the encoder gen_blocks.py uses (<name>_n.png: RG normal,
B AO - darker at the roots, A height; <name>_s.png: R smoothness, G F0 10, B porosity, A 255 = no emission), and the
grass top's material numbers (gen_blocks.TARGETS["grass_block_top"]: luma, smoothness, porosity, tilt).

The albedo is GREYSCALE like the grass top: the model gives the quads FogTintingBlockColor.EDGE_GRASS_TINT_INDEX, which
paints them the lawn's own colour at that block (BiomeColors -> GrassTint noise, canopy shade, fog) - so the fringe is
the green of the grass beside it, patches and fog included.

Deterministic (fixed seeds); writes bar/src/main/resources/assets/reignofnether/textures/block/edge/ (the 1.20 block
atlas takes every texture under textures/block/ of every namespace - no atlas json needed).
    python3 tools/gen_edge_fringe.py [--preview DIR] [--check] [--sampler]
--check regenerates into a temp dir and fails if the committed files differ (byte for byte).
--sampler writes design/eyes/edge_sampler.nbt, the eyes structure-photo layout for the overlays (see edge_sampler).
"""
import argparse
import filecmp
import math
import os
import sys
import tempfile

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import materials  # noqa: E402  - the one LabPBR encoder
import gen_blocks as gb  # noqa: E402  - Field, the grass top's material targets, the calibration helpers

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "block", "edge")

N = 128
REACH = 5 / 16          # EdgeOverlayRules.REACH: the quad's depth; nothing may be drawn past it
END_DEPTH = 0.15        # every strip's band depth at both ends, so neighbouring variants join
GRASS = gb.TARGETS["grass_block_top"]

# name -> (kind, seed); seeds are fixed per name (adding a sprite never re-rolls the others)
SPRITES = {
    "edge_a": ("edge", 9101),
    "edge_b": ("edge", 9102),
    "edge_c": ("edge", 9103),
    "outer_a": ("outer", 9201),
    "outer_b": ("outer", 9202),
    "inner_a": ("inner", 9301),
    "inner_b": ("inner", 9302),
}


def strip_depth(F):
    """The band's depth along u (tile units, 0..1): broad bays and tongues at 1/2 - 1/6 block with finer fray, 6-25%
    deep, pinned to END_DEPTH at both ends."""
    u = np.arange(N) / N
    broad = F.noise(1.5, 5, 0.8)[0]
    fray = F.noise(8, 24, 0.3)[0]
    d = END_DEPTH + 0.07 * broad + 0.02 * fray
    w = gb.smoothstep(0.0, 0.14, u) * gb.smoothstep(0.0, 0.14, 1 - u)
    d = END_DEPTH + (d - END_DEPTH) * w
    return np.clip(d, 0.06, 0.25)


def stroke(H, x0, y0, ang, length, width, top=1.0, falloff=0.6):
    """A blade from (x0, y0) (texels) along ang, `width` texels wide, height top at the root falling by `falloff` to
    the tip. No wrap along v (the sprite's edges are its quad's edges)."""
    dx, dy = math.cos(ang), math.sin(ang)
    steps = int(length * 2) + 1
    for s in range(steps):
        t = s / max(1, steps - 1)
        x, y = x0 + dx * t * length, y0 + dy * t * length
        h = top * (1.0 - falloff * t)
        for w in range(width):
            xx = int(round(x - dy * (w - (width - 1) / 2))) % N
            yy = int(round(y + dx * (w - (width - 1) / 2)))
            if 0 <= yy < N:
                H[yy, xx] = max(H[yy, xx], h)


def blob(H, cx, cy, r, top=0.9):
    """A clover clump: a rounded dome of radius r texels."""
    yy, xx = np.mgrid[0:N, 0:N]
    dx = (xx - cx + N / 2) % N - N / 2
    d = np.sqrt(dx * dx + (yy - cy) ** 2) / r
    H[:] = np.maximum(H, np.where(d < 1, top * np.sqrt(np.clip(1 - d * d, 0, 1)), 0))


def fringe_blades(F, H, root_at, count, reach):
    """Blades and clumps along a ragged root line: root_at(i) -> (x, y, outward angle) in texels for i in 0..1."""
    rng = F.rng
    for _ in range(count):
        x, y, out = root_at(rng.random())
        ang = out + (rng.random() * 2 - 1) * math.radians(38)
        ln = reach * N * (0.25 + 0.75 * rng.random() ** 1.6)          # mostly short, a few long single blades
        stroke(H, x, y - 1.5 * math.sin(out), ang, ln, 2 if rng.random() < 0.55 else 1, top=0.75, falloff=0.55)
    for _ in range(count // 9):
        x, y, out = root_at(rng.random())
        r = 2.5 + 3.5 * rng.random()
        off = r * (0.2 + 0.6 * rng.random())
        blob(H, x + math.cos(out) * off, y + math.sin(out) * off, r, top=0.8)


def lawn(F):
    """The grass top's own surface inside the band: its blades and soft tufts (gen_blocks.m_grass_top's recipe)."""
    blades = gb.grass_blades(F, int(9 * N), N / 36, 1)
    clumps = F.fbm(3, 4, 0.55)
    fine = F.noise(16, 40, 0.0)
    tufts = F.noise(3, 14, 1.2)
    H = 0.55 + 0.25 * blades + 0.12 * clumps + 0.04 * fine
    shade = 0.85 + 0.12 * gb.norm01(tufts) + 0.015 * gb.norm01(blades + 0.5 * fine)
    return H, shade


def sprite(kind, seed):
    """(height 0..1, shade, alpha 0/1) of one sprite, rooted at its top edge (edge) or NW corner (outer / inner)."""
    F = gb.Field(N, seed)
    yy, xx = np.mgrid[0:N, 0:N]
    u, v = (xx + 0.5) / N, (yy + 0.5) / N
    Hl, shade = lawn(F)
    tips = np.zeros((N, N))
    if kind == "edge":
        d = strip_depth(F)
        band = v < d[None, :]
        # the band thins out towards its edge: its height falls over the last ~3 texels, so a shader lights a soft
        # rounded rim instead of a cut
        rim = np.clip((d[None, :] - v) * N / 3.0, 0, 1)

        def root(i):
            x = i * N
            return x, d[int(x) % N] * N, math.pi / 2                 # pointing down the sprite: onto the path
        fringe_blades(F, tips, root, 260, 0.075)
    else:
        r = np.sqrt(u * u + v * v)
        a = np.arctan2(v, u)                                          # 0 along the top edge, pi/2 down the left one
        base, amp, knots = (0.24, 0.035, 9) if kind == "outer" else (0.13, 0.03, 7)
        kv = F.rng.standard_normal(knots)
        kx = np.linspace(0, math.pi / 2, knots)

        def rad(ang):
            return base + amp * np.interp(ang, kx, kv)
        radius = rad(a)
        rim = np.clip((radius - r) * N / 3.0, 0, 1)
        band = r < radius
        if kind == "outer":
            # the two strips' union reaches END_DEPTH along both edges; the fillet bulges past it into the inside
            # corner of their L, ragged with the angle
            strips = (v < END_DEPTH + 0.02 * np.sin(u * 37 + seed)) | (u < END_DEPTH + 0.02 * np.sin(v * 41 + seed))
            band |= strips
            rim = np.maximum(rim, strips.astype(np.float64))

        def root(i):
            ang = i * math.pi / 2
            rr = float(rad(ang))
            return math.cos(ang) * rr * N, math.sin(ang) * rr * N, ang
        fringe_blades(F, tips, root, 70 if kind == "outer" else 45, 0.06 if kind == "outer" else 0.07)
    alpha = band | (tips > 0)
    # nothing past the quad (REACH, one texel of margin) or across the corner pieces' other edges
    inside = v < REACH - 1 / N
    if kind != "edge":
        inside &= u < REACH - 1 / N
    alpha &= inside
    H = np.where(band, (0.35 + 0.65 * rim) * Hl, 0.30 * tips + 0.12)
    H = np.where(alpha, H, 0.0)
    return H, shade, alpha


def maps(H, shade, alpha):
    """Albedo (grey, fitted to the grass top's luma), _n and _s - transparent texels flat and neutral."""
    vis = alpha
    alb = np.clip(np.stack([shade] * 3, -1) * 0.62, 0, 1)
    alb = gb.fit_albedo(alb, GRASS, vis)
    # the roots sit in the shade of the blades over them: a soft AO at the band's inner rim and under the clumps
    blur = np.real(np.fft.ifft2(np.fft.fft2(H) * np.exp(-2 * (math.pi ** 2) * 2.0 ** 2 *
                                                        ((np.fft.fftfreq(N)[None, :]) ** 2 +
                                                         (np.fft.fftfreq(N)[:, None]) ** 2))))
    cav = np.clip(blur - H, 0, None)
    ao = 1 - np.clip(cav * 2.5, 0, 0.40)
    ao = np.where(vis, ao, 1.0)
    k = gb.solve_strength(H, GRASS["tilt"], vis)
    nx, ny, _ = materials.height_normals(H, k, wrap=False)
    n = materials.encode_normal(np.where(vis, nx, 0), np.where(vis, ny, 0), ao, np.where(vis, H, 1.0))
    n[..., 3] = np.where(vis, n[..., 3], 255)
    smooth = np.full(H.shape, float(GRASS["smooth"]))
    s = materials.encode_specular(smooth, f0=10, porosity=GRASS["pore"], emission=255, shape=H.shape)
    probs = materials.check_labpbr(n, s, vis)
    if probs:
        raise SystemExit("LabPBR check failed: " + "; ".join(probs))
    return alb, n, s


def write(out_dir, preview=None):
    os.makedirs(out_dir, exist_ok=True)
    sheet = []
    for name, (kind, seed) in SPRITES.items():
        H, shade, alpha = sprite(kind, seed)
        alb, n, s = maps(H, shade, alpha)
        a = alpha.astype(np.float64)
        gb.to_png(alb, a).save(os.path.join(out_dir, name + ".png"), optimize=True)
        Image.fromarray(n, "RGBA").save(os.path.join(out_dir, name + "_n.png"), optimize=True)
        Image.fromarray(s, "RGBA").save(os.path.join(out_dir, name + "_s.png"), optimize=True)
        cover = float(alpha[: int(REACH * N)].mean()) if kind == "edge" else float(
            alpha[: int(REACH * N), : int(REACH * N)].mean())
        print("%-8s cover %.2f of its quad, albedo luma %.3f" % (
            name, cover, float((alb[..., 1] * 0.7152 + alb[..., 0] * 0.2126 + alb[..., 2] * 0.0722)[alpha].mean())))
        if preview:
            sheet.append((name, alb, alpha))
    if preview:
        os.makedirs(preview, exist_ok=True)
        lawn_rgb = np.array([0x6F, 0x96, 0x43]) / 255.0            # a mid battlefield lawn, for the preview only
        path_rgb = np.array([0x94, 0x7A, 0x4E]) / 255.0
        tiles = []
        for name, alb, alpha in sheet:
            img = np.where(alpha[..., None], alb * lawn_rgb / GRASS["luma"], path_rgb)
            tiles.append(np.clip(img, 0, 1))
        row = np.concatenate([np.pad(t, ((4, 4), (4, 4), (0, 0)), constant_values=1) for t in tiles], 1)
        Image.fromarray((row * 255).astype(np.uint8), "RGB").save(os.path.join(preview, "edge_fringe_sheet.png"))
        print("preview", os.path.join(preview, "edge_fringe_sheet.png"))


SAMPLER = os.path.join(ROOT, "design", "eyes", "edge_sampler.nbt")


def edge_sampler():
    """The eyes layout (17 x 1 x 17, one layer of ground on the photo pad): a lawn of grass_block with every case the
    rules draw - a dirt_path cross (strips, the crossing's inner tufts, its 4 dead ends with outer corners), a single
    path block (all 4 strips + 4 outer corners), an L of dirt_path (an inside corner: the inner tuft), a 4 x 4 sand
    patch, a 3 x 3 gravel patch, a coarse_dirt diagonal (inner tufts only on diagonal neighbours) and a farmland pair."""
    import gen_structures as gs
    S = gs.Structure(17, 1, 17)
    S.fill(0, 0, 0, 16, 0, 16, "grass_block")
    for i in range(1, 16):
        S.set(i, 0, 8, "dirt_path")                  # east-west arm
        S.set(8, 0, i, "dirt_path")                  # north-south arm
    S.set(14, 0, 2, "dirt_path")                     # single path block
    for z in range(11, 15):
        S.set(2, 0, z, "dirt_path")                  # the L
    for x in range(3, 6):
        S.set(x, 0, 14, "dirt_path")
    S.fill(11, 0, 11, 14, 0, 14, "sand")
    S.fill(2, 0, 2, 4, 0, 4, "gravel")
    for d in range(3):
        S.set(11 + d, 0, 6 - d, "coarse_dirt")       # touching only at corners
    S.set(5, 0, 11, "farmland")
    S.set(6, 0, 11, "farmland")
    S.write(SAMPLER)
    print("wrote", SAMPLER)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--sampler", action="store_true")
    args = ap.parse_args()
    if args.sampler:
        edge_sampler()
        return
    if args.check:
        with tempfile.TemporaryDirectory() as d:
            write(d)
            bad = [f for f in sorted(os.listdir(d))
                   if not os.path.exists(os.path.join(OUT_DIR, f)) or not filecmp.cmp(
                       os.path.join(d, f), os.path.join(OUT_DIR, f), shallow=False)]
        if bad:
            raise SystemExit("edge fringe sprites differ from the generator: " + ", ".join(bad))
        print("edge fringe sprites match the generator")
        return
    write(OUT_DIR, args.preview)


if __name__ == "__main__":
    main()
