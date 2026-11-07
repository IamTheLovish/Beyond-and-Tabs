#!/usr/bin/env python3
"""A texture as the RTS camera sees it, offline: judge a ground texture before a CI eyes run.

The RTS camera is orthographic at ~27-36 px per block, so Minecraft samples a small mip of a 256x texture there. This
tool box-mips the albedo to PX texels per block (default 32), multiplies the biome tint (the lawn is grey albedo x the
battlefield biome's grass colour #6FA64A), tiles it N x N blocks (each block its own random variant and rotation, as
the game does), then optionally applies a per-channel TRANSFER fitted from a real in-game crop - so its colour lands
where our shader lane would put it.

Measures, on any RGB image (sim or in-game crop), in 0..1 sRGB:
  L mean / p5 / p95 / std (Rec.709 luma), sat (HSV saturation of the mean colour), hue (degrees, of the mean colour),
  micro = mean |L - blur1.5|, meso = mean |blur2.5 - blur7| (features ~1/8-1/2 block at the RTS camera),
  macro = mean |blur5 - blur12|.

Calibration (Nov 3-4, ground sampler, rts t5000, build 41630072473): sim std ~= in-game std x 0.85-1.2 on the plain
lane, meso ~ x0.8. Fit the transfer from a crop of the SAME texture in game (--fit-crop) before trusting colour.

    python3 tools/texture_camera_sim.py grass_block_top --tint 6FA64A -o lawn.png [--blocks 10] [--px 32]
    python3 tools/texture_camera_sim.py --measure still.png --box 620,300,780,380
    python3 tools/texture_camera_sim.py grass_block_top --tint 6FA64A --fit-crop still.png:620,300,780,380
"""
import argparse
import colorsys
import glob
import os
import sys

import numpy as np
from PIL import Image

PACK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepacks", "BeyondAndTabs-Realistic",
                    "assets", "minecraft", "textures", "block")
LAWN_TINT = "6FA64A"   # battlefield.json grass_color 7317066


def hex_rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255.0


def box_mip(img, px):
    """Average-pool an (n, n, 3) image to (px, px, 3); n must be a multiple of px (Minecraft's mips are box filters)."""
    n = img.shape[0]
    if n % px:
        raise ValueError(f"texture size {n} is not a multiple of {px}")
    k = n // px
    return img.reshape(px, k, px, k, img.shape[2]).mean(axis=(1, 3))


def load(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=float) / 255.0


def variants(name, pack=PACK):
    """The block's base texture plus its _vN variants (albedo only, not _n / _s)."""
    base = os.path.join(pack, name + ".png")
    out = [base] if os.path.exists(base) else []
    out += sorted(p for p in glob.glob(os.path.join(pack, name + "_v*.png"))
                  if not p.endswith(("_n.png", "_s.png")))
    if not out:
        raise FileNotFoundError(f"no texture {name} in {pack} (run tools/gen_blocks.py)")
    return out


def tile(textures, blocks, px, tint=None, seed=0, rotate=True):
    """blocks x blocks field, each block a random texture from `textures` (arrays), randomly rotated; mipped to px."""
    rng = np.random.default_rng(seed)
    mips = [box_mip(t, px) for t in textures]
    field = np.zeros((blocks * px, blocks * px, 3))
    for by in range(blocks):
        for bx in range(blocks):
            m = mips[rng.integers(len(mips))]
            if rotate:
                m = np.rot90(m, rng.integers(4))
            field[by * px:(by + 1) * px, bx * px:(bx + 1) * px] = m
    if tint is not None:
        field = field * np.asarray(tint)
    return np.clip(field, 0, 1)


def world_tint(blocks, px=32, base=LAWN_TINT):
    """The lawn's world colour as the game draws it, 0..1 RGB of shape (blocks * px, blocks * px, 3): postfx/GrassTint
    on the battlefield grass colour per block (tools/grass_tint_preview.py, an exact port that reads the Java file's
    constants), blended per vertex corner as Embeddium does (each corner the mean of the four blocks sharing it,
    bilinear across the face). Multiply an UNTINTED albedo field by it to see texture + world field together, as the
    player does (lovish Nov 5: the calm-patch and speckle checks measure that)."""
    import grass_tint_preview as gtp
    k = gtp.constants()
    rgb = int(base, 16)
    bx, bz = np.meshgrid(np.arange(blocks + 2) - 1.0, np.arange(blocks + 2) - 1.0)
    blk = gtp.apply(rgb, bx, bz, 1, k) / 255.0          # GrassTint.GRASS = 1; rows are z, columns x
    corner = 0.25 * (blk[:-1, :-1] + blk[1:, :-1] + blk[:-1, 1:] + blk[1:, 1:])
    t = (np.arange(blocks * px) + 0.5) / px
    bi = np.floor(t).astype(int)
    fr = t - bi
    fx, fz = fr[None, :, None], fr[:, None, None]
    a, b = corner[bi][:, bi], corner[bi][:, bi + 1]
    c, d = corner[bi + 1][:, bi], corner[bi + 1][:, bi + 1]
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


def luma(rgb):
    return rgb[..., 0] * 0.2126 + rgb[..., 1] * 0.7152 + rgb[..., 2] * 0.0722


def _blur(L, r):
    """Separable Gaussian (sigma r), edges reflected; numpy only (PIL's blur refuses float images)."""
    k = int(np.ceil(3 * r))
    x = np.arange(-k, k + 1)
    w = np.exp(-x * x / (2.0 * r * r))
    w /= w.sum()
    out = np.pad(L, k, mode="reflect")
    out = np.apply_along_axis(lambda v: np.convolve(v, w, mode="valid"), 0, out)
    return np.apply_along_axis(lambda v: np.convolve(v, w, mode="valid"), 1, out)


def measure(rgb):
    L = luma(rgb)
    mean = rgb.reshape(-1, 3).mean(0)
    h, s, _ = colorsys.rgb_to_hsv(*mean)
    return dict(L=float(L.mean()), p5=float(np.percentile(L, 5)), p95=float(np.percentile(L, 95)),
                std=float(L.std()), sat=float(s), hue=float(h * 360), rgb=[float(c) for c in mean],
                micro=float(np.abs(L - _blur(L, 1.5)).mean()),
                meso=float(np.abs(_blur(L, 2.5) - _blur(L, 7)).mean()),
                macro=float(np.abs(_blur(L, 5) - _blur(L, 12)).mean()))


def fit_transfer(sim_rgb, game_rgb):
    """Per-channel gain mapping the sim's mean colour onto the in-game crop's (one material -> one gain per channel)."""
    s = sim_rgb.reshape(-1, 3).mean(0)
    g = game_rgb.reshape(-1, 3).mean(0)
    return g / np.maximum(s, 1e-6)


def apply_transfer(rgb, gain):
    return np.clip(rgb * np.asarray(gain), 0, 1)


def crop(path, box):
    x0, y0, x1, y1 = (int(v) for v in box.split(","))
    return load(path)[y0:y1, x0:x1]


def fmt(m):
    return ("L %.3f p5 %.3f p95 %.3f std %.3f sat %.2f hue %.0f micro %.3f meso %.4f macro %.4f"
            % (m["L"], m["p5"], m["p95"], m["std"], m["sat"], m["hue"], m["micro"], m["meso"], m["macro"]))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("name", nargs="?", help="block texture name in the pack, or a .png path")
    ap.add_argument("--pack", default=PACK)
    ap.add_argument("--tint", help="hex biome tint, e.g. 6FA64A for the lawn")
    ap.add_argument("--blocks", type=int, default=10)
    ap.add_argument("--px", type=int, default=32)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--gain", help="per-channel transfer r,g,b")
    ap.add_argument("--fit-crop", help="STILL.png:x0,y0,x1,y1 - fit and apply the transfer from this in-game crop")
    ap.add_argument("--measure", help="measure an image instead (with --box)")
    ap.add_argument("--box")
    ap.add_argument("-o", "--out")
    ap.add_argument("--scale", type=int, default=1, help="nearest upscale of the saved PNG")
    a = ap.parse_args(argv)

    if a.measure:
        img = crop(a.measure, a.box) if a.box else load(a.measure)
        print(fmt(measure(img)))
        return 0
    if not a.name:
        ap.error("name or --measure required")
    paths = [a.name] if a.name.endswith(".png") else variants(a.name, a.pack)
    field = tile([load(p) for p in paths], a.blocks, a.px, hex_rgb(a.tint) if a.tint else None, a.seed)
    print("sim  ", fmt(measure(field)), f"({len(paths)} variants)")
    gain = None
    if a.fit_crop:
        still, box = a.fit_crop.rsplit(":", 1)
        game = crop(still, box)
        gain = fit_transfer(field, game)
        print("game ", fmt(measure(game)))
        print("gain ", ",".join("%.3f" % g for g in gain))
    elif a.gain:
        gain = np.array([float(v) for v in a.gain.split(",")])
    if gain is not None:
        field = apply_transfer(field, gain)
        print("lane ", fmt(measure(field)))
    if a.out:
        im = Image.fromarray((field * 255 + 0.5).astype(np.uint8))
        if a.scale > 1:
            im = im.resize((im.width * a.scale, im.height * a.scale), Image.NEAREST)
        im.save(a.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
