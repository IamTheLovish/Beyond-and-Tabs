#!/usr/bin/env python3
"""The ground scars' decal textures (claude/world-that-remembers.md 3.3, slice 1) - our own, procedural, in the
gen_blocks.py style: a periodic height field, the albedo from it, LabPBR _n / _s maps through materials.py's one
encoder so Complementary reads them like a unit's.

Each tile covers 4x4 blocks at 128 texels per block (512x512) and wraps, so the mesh samples it with world-space
coordinates (x/4, z/4) and neighbouring cells join without a seam. The RGB is kept LIGHT and nearly neutral: the
mesh's vertex colour (ash, crater floor, thrown soil tinted by the local ground) multiplies it, so the texture carries
the material - grain, char flecks, stubble, pebbles - and the colour comes from the world. The alpha is the organic
mask: holes and thinning that make the mark patchy at first-person distance while its mean stays high enough that the
RTS read keeps the darkening the vertex alpha asks for.

    scorch.png  ash with char flecks and burnt stubble
    crater.png  churned soil with pebbles and debris specks (the floor and the rim share it; the vertex colour tells
                them apart)

Deterministic (fixed seeds). Writes bar/src/main/resources/assets/reignofnether/textures/decals/ (in git: the mod's
own assets) and, with --preview DIR, the tiles over grass at RTS scale for review.
    python3 tools/gen_scars.py [--preview DIR]
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import materials  # noqa: E402
from gen_blocks import Field  # noqa: E402  - periodic noise, Voronoi, blur (everything wraps)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "decals")
N = 512            # 4 blocks x 128 texels
SEEDS = {"scorch": 7101, "crater": 7102}


def clip01(a):
    return np.clip(a, 0.0, 1.0)


def smoothstep(e0, e1, x):
    t = clip01((x - e0) / max(1e-9, e1 - e0))
    return t * t * (3 - 2 * t)


def scorch(seed):
    """Ash over the ground: fine grey grain, char flecks, short burnt-stubble strokes, and thinning where the fire
    missed - irregular, never an even camouflage of blobs (round 1 read as camo at 128). Height: the ash lies flat
    with the char standing up a little."""
    f = Field(N, seed)
    grain = f.fbm(24, 4, 0.55)                        # 1/6 .. 1/48 block: ash texture the RTS mip keeps
    fine = f.fbm(64, 2, 0.5)                          # texel-level ash dust
    drift = f.fbm(3, 3, 0.6)                          # broad drifts of thicker / thinner ash
    # char flecks: the peaks of a high-frequency noise, so each bit is its own irregular shape (Voronoi discs read as
    # coins), 1/40 .. 1/16 block across; densest where the fire was fierce, none elsewhere
    fleck = smoothstep(1.25, 1.9, f.fbm(36, 2, 0.6) + 0.35 * f.fbm(90, 1))
    fleck = np.maximum(fleck, smoothstep(1.55, 2.1, f.fbm(13, 2, 0.6) + 0.3 * f.fbm(40, 1)))   # a few bigger pieces
    fleck *= smoothstep(-0.3, 0.5, f.fbm(6, 2))
    stubble = np.abs(f.noise(30, 70, 0.3, sx=1.0, sy=5.0))   # strokes long along v: burnt grass lying down
    stubble = smoothstep(1.3, 2.4, stubble) * smoothstep(-0.2, 0.5, f.fbm(5, 2))

    luma = 0.76 + 0.11 * grain + 0.05 * fine + 0.05 * drift
    luma = luma * (1 - 0.70 * fleck)
    luma = luma * (1 - 0.12 * stubble)
    luma = clip01(luma)
    # a faint warmth in the char (ember brown), a cool grey in the ash
    r = luma * (1.0 + 0.07 * fleck)
    g = luma * (1.0 - 0.02 * fleck)
    b = luma * (0.97 - 0.10 * fleck)
    # alpha: an almost continuous blanket (the mesh's corner alpha and noise threshold shape the mark at block scale;
    # a texture full of mid-sized holes read as camouflage): fine thinning with the grain, a soft broad drift, and a
    # few small ragged gaps where the ground shows; char is always solid
    thin = smoothstep(0.3, 1.2, f.fbm(2, 2, 0.6))
    gaps = smoothstep(1.15, 1.45, f.fbm(14, 2, 0.5) * (0.7 + 0.3 * f.fbm(5, 2)))
    alpha = 0.93 + 0.07 * grain + 0.04 * fine - 0.22 * thin - 0.5 * gaps
    alpha = np.maximum(alpha, fleck)
    alpha = clip01(alpha)
    height = clip01(0.4 + 0.08 * grain + 0.1 * drift + 0.3 * fleck + 0.1 * stubble)
    return r, g, b, alpha, height, dict(smooth=22, f0=10, pore=56)


def crater(seed):
    """Churned soil: lumps of thrown earth with a fine grain, pale pebbles and dark debris specks, a few thin cracks.
    Height: lumpy, pebbles standing proud, cracks cut in."""
    f = Field(N, seed)
    grain = f.fbm(32, 4, 0.55)
    lumps = f.fbm(6, 3, 0.55)
    clods = smoothstep(0.6, 1.4, f.fbm(12, 2, 0.6))                 # clods of earth between the lumps
    # pebbles (pale, irregular) and debris specks (dark, small), both noise peaks so no two are the same shape
    pebble = smoothstep(1.45, 2.0, f.fbm(22, 2, 0.6) + 0.3 * f.fbm(60, 1)) * smoothstep(-0.4, 0.4, f.fbm(7, 2))
    speck = smoothstep(1.55, 2.1, f.fbm(64, 2, 0.6) + 0.3 * f.fbm(140, 1)) * smoothstep(-0.3, 0.5, f.fbm(9, 2))
    crack = smoothstep(0.035, 0.0, np.abs(f.noise(5, 12, 0.5))) * smoothstep(0.1, 0.7, f.fbm(4, 2))

    luma = 0.68 + 0.14 * grain + 0.10 * lumps + 0.06 * clods
    luma = luma * (1 - 0.45 * speck)
    luma = np.where(pebble > 0, luma * (1 - pebble) + 0.88 * pebble, luma)
    luma = luma * (1 - 0.25 * crack)
    luma = clip01(luma)
    r = luma * (1.0 + 0.04 * lumps)
    g = luma * (1.0 - 0.01 * lumps)
    b = luma * (0.95 - 0.03 * lumps - 0.04 * pebble)
    # alpha: near-solid soil with a few small gaps where the old ground shows between clods
    gaps = smoothstep(1.05, 1.35, f.fbm(12, 2, 0.5) * (0.7 + 0.3 * f.fbm(5, 2)))
    alpha = clip01(0.92 + 0.06 * grain - 0.55 * gaps)
    alpha = np.maximum(alpha, np.maximum(pebble, speck))
    height = clip01(0.35 + 0.10 * grain + 0.22 * lumps + 0.12 * clods + 0.3 * pebble + 0.08 * speck - 0.25 * crack)
    return r, g, b, alpha, height, dict(smooth=30, f0=10, pore=56)


def write(name, maps, out_dir, preview_dir):
    r, g, b, alpha, height, spec = maps
    rgba = np.empty((N, N, 4), dtype=np.uint8)
    rgba[..., 0] = np.clip(r * 255, 0, 255).astype(np.uint8)
    rgba[..., 1] = np.clip(g * 255, 0, 255).astype(np.uint8)
    rgba[..., 2] = np.clip(b * 255, 0, 255).astype(np.uint8)
    rgba[..., 3] = np.clip(alpha * 255, 0, 255).astype(np.uint8)
    os.makedirs(out_dir, exist_ok=True)
    Image.fromarray(rgba, "RGBA").save(os.path.join(out_dir, name + ".png"), optimize=True)
    # LabPBR: normals from the height (the tile wraps), AO from the height's cavities, the height for parallax
    nx, ny, nz = materials.height_normals(height * 6.0, 1.0, wrap=True)
    ao = clip01(0.75 + 0.25 * (height - height.min()) / max(1e-6, height.max() - height.min()))
    n = materials.encode_normal(nx, ny, ao, height)
    s = materials.encode_specular(spec["smooth"], spec["f0"], spec["pore"], 255, shape=(N, N))
    problems = materials.check_labpbr(n, s, opaque=alpha > 0.02)
    if problems:
        raise SystemExit("%s: LabPBR check failed: %s" % (name, "; ".join(problems)))
    Image.fromarray(n, "RGBA").save(os.path.join(out_dir, name + "_n.png"), optimize=True)
    Image.fromarray(s, "RGBA").save(os.path.join(out_dir, name + "_s.png"), optimize=True)
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        preview(name, rgba, preview_dir)
    mean_a = float(alpha.mean())
    print("%s: %dx%d, alpha mean %.2f, luma mean %.2f" % (name, N, N, mean_a, float((0.3 * r + 0.59 * g + 0.11 * b).mean())))


def preview(name, rgba, preview_dir):
    """The tile composited over a grass-green plate with the mesh's own vertex colours and alpha, at 128 and at the
    RTS camera's ~36 px per block, so the material can be judged at the scale it is seen."""
    grass = np.array([123, 179, 79], dtype=np.float64)
    if name == "scorch":
        tint = np.array([64, 57, 50], dtype=np.float64)
        va = 0.66
    else:
        tint = np.array([150, 112, 78], dtype=np.float64)
        va = 0.62
    tex = rgba.astype(np.float64) / 255.0
    col = tex[..., :3] * tint / 255.0
    a = tex[..., 3:4] * va
    out = col * a * 255 + grass * (1 - a)
    big = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB")
    big.save(os.path.join(preview_dir, name + "_on_grass_128.png"))
    # 4 blocks at ~36 px each
    small = big.resize((4 * 36, 4 * 36), Image.LANCZOS)
    small.resize((4 * 36 * 4, 4 * 36 * 4), Image.NEAREST).save(os.path.join(preview_dir, name + "_on_grass_rts.png"))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=OUT_DIR)
    ap.add_argument("--preview", default="")
    args = ap.parse_args()
    write("scorch", scorch(SEEDS["scorch"]), args.out, args.preview)
    write("crater", crater(SEEDS["crater"]), args.out, args.preview)


if __name__ == "__main__":
    main()
