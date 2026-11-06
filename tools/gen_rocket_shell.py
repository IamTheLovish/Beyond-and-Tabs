#!/usr/bin/env python3
"""Draws the Rocket Battery's rocket and rack textures (original pixel art, Pillow only).

  rocket_shell.png  32x32  RocketShellModel: red-lacquer body with a gold band, a gold nose cone, dark fins, and the
                           exhaust flame (bright, drawn lit by the renderer) in its own corner
  rocket_rack.png   64x32  RocketRackModel: a lacquered back plate, four dark iron tubes with a gold rim, and a rocket
                           nose (red, gold tip) in the mouth of each

Both follow the Stormcallers palette of tools/gen_storm_skins.py (red lacquer, gold trim, soot iron). The UV regions are
those of the models' CubeListBuilder boxes: a box of (w, h, d) at (u, v) covers 2d+2w by d+h texels, laid out
top | bottom | (left, front, right, back) as Minecraft does.

usage: python3 tools/gen_rocket_shell.py
"""
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "entities")

RED_D, RED, RED_L = (96, 18, 16), (168, 35, 28), (214, 70, 52)
GOLD_D, GOLD, GOLD_L = (140, 100, 24), (212, 175, 55), (246, 222, 120)
SOOT_D, SOOT, SOOT_L = (34, 32, 34), (56, 52, 52), (96, 90, 88)
FLAME_HOT, FLAME_MID, FLAME_OUT = (255, 246, 200), (255, 190, 80), (236, 104, 36)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def fill(img, x0, y0, x1, y1, c):
    """Fills the texel box [x0, x1) x [y0, y1)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), c + (255,))


def noisy(img, x0, y0, x1, y1, base, rng, amount=0.08):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), shade(base, 1 + rng.uniform(-amount, amount)) + (255,))


def box_faces(u, v, w, h, d):
    """The six faces of a box at (u, v): name -> (x0, y0, x1, y1) in texels (w along x, h along y, d along z)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + 2 * d + w, v + d),
        "left": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "right": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def rocket(rng):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # body 8x2x2 at (0, 0): red lacquer, lit on top, darker underneath, a gold band 2 texels wide round the middle
    for name, (x0, y0, x1, y1) in box_faces(0, 0, 8, 2, 2).items():
        k = {"top": 1.15, "bottom": 0.7}.get(name, 0.95)
        noisy(img, x0, y0, x1, y1, shade(RED, k), rng)
        if name in ("top", "bottom", "front", "back"):
            fill(img, x0 + 3, y0, x0 + 5, y1, shade(GOLD, k))
    # nose cone 2x2x2 at (0, 4) and its tip 1x1x1 at (8, 4): gold, the tip bright
    for name, (x0, y0, x1, y1) in box_faces(0, 4, 2, 2, 2).items():
        noisy(img, x0, y0, x1, y1, shade(GOLD, 1.1 if name == "top" else 0.9), rng, 0.05)
    for name, (x0, y0, x1, y1) in box_faces(8, 4, 1, 1, 1).items():
        fill(img, x0, y0, x1, y1, GOLD_L)
    # fins: a flat plate 2x0.5x5 at (0, 8) and an upright one 2x5x0.5 at (14, 8), dark with a red leading edge
    fill(img, 0, 8, 14, 14, SOOT)
    fill(img, 14, 8, 19, 14, SOOT)
    for y in range(8, 14):
        img.putpixel((0, y), SOOT_L + (255,))
        img.putpixel((14, y), SOOT_L + (255,))
    # flame 5x1.5x1.5 at (0, 16): white-hot at the nozzle (x = 4, the rocket end), orange at the tail
    for name, (x0, y0, x1, y1) in box_faces(0, 16, 5, 2, 2).items():
        for x in range(x0, x1):
            t = (x - x0) / max(1, (x1 - x0 - 1))   # 0 at the tail end of a long face, 1 at the nozzle end
            c = FLAME_OUT if t < 0.34 else FLAME_MID if t < 0.72 else FLAME_HOT
            if name in ("left", "right"):
                c = FLAME_MID
            for y in range(y0, y1):
                img.putpixel((x, y), c + (255,))
    return img


def rack(rng):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # plate 9x10x1 at (0, 0): red lacquer with a gold edge strip across the top
    for name, (x0, y0, x1, y1) in box_faces(0, 0, 9, 10, 1).items():
        k = {"top": 1.15, "bottom": 0.7}.get(name, 1.0)
        noisy(img, x0, y0, x1, y1, shade(RED_D, k), rng)
        if name in ("front", "back"):
            fill(img, x0, y0, x1, y0 + 1, shade(GOLD, k))
    # tubes 3.5x11x3 at (0, 12): soot iron, a gold rim at the mouth (the top rows of the side faces), soot_l highlights
    for name, (x0, y0, x1, y1) in box_faces(0, 12, 4, 11, 3).items():
        k = {"top": 1.2, "bottom": 0.6}.get(name, 1.0)
        noisy(img, x0, y0, x1, y1, shade(SOOT, k), rng, 0.1)
        if name in ("front", "back", "left", "right"):
            fill(img, x0, y0, x1, y0 + 1, shade(GOLD_D, k))
            for y in range(y0 + 3, y1, 4):   # riveted bands
                fill(img, x0, y, x1, y + 1, shade(SOOT_L, k))
    # rocket noses 2.5x2x2.5 at (14, 12): red, the top face a gold tip
    for name, (x0, y0, x1, y1) in box_faces(14, 12, 3, 2, 3).items():
        if name == "top":
            fill(img, x0, y0, x1, y1, GOLD_L)
        else:
            noisy(img, x0, y0, x1, y1, shade(RED_L, 0.95), rng, 0.05)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    rng = random.Random(20261105)
    rocket(rng).save(os.path.join(OUT, "rocket_shell.png"))
    rack(rng).save(os.path.join(OUT, "rocket_rack.png"))
    print("wrote rocket_shell.png and rocket_rack.png to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
