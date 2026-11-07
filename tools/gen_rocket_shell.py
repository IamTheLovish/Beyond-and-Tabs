#!/usr/bin/env python3
"""Draws the Rocket Battery's bolt and rack textures (original pixel art, Pillow only).

  rocket_shell.png  32x32  RocketShellModel: the charged bolt - a white-hot core with a blue edge, a cyan-white nose and
                           needle, electric-blue halo fins and a jagged blue-white tail; all of it is drawn lit by the
                           renderer (the eyes render type), so the colours here are the colours on screen
  rocket_rack.png   64x32  RocketRackModel: the lightning-rod rack - a copper back frame, four copper rods with a verdigris
                           bloom and a collar near the tip, and (in their own corner, drawn lit) the charged orbs that sit
                           on the rod tips (electric blue, no white-hot middle)

The Stormcallers keep red lacquer, gold and jade on their units and buildings; the artillery reads as lightning on top of
that (lovish, Nov 5: "charged bolts with a jagged electric-blue trail"; Nov 7: "B: copper tubes"). The UV
regions are those of the models' CubeListBuilder boxes: a box of (w, h, d) at (u, v) covers 2d+2w by d+h texels, laid out
top | bottom | (left, front, right, back) as Minecraft does.

usage: python3 tools/gen_rocket_shell.py
"""
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "bar", "src", "main", "resources", "assets", "reignofnether", "textures", "entities")

# copper is a saturated orange-red metal: the old (176, 108, 52) tan read as wooden poles to the blind critic
COPPER_D, COPPER, COPPER_L = (86, 34, 16), (184, 82, 34), (236, 138, 70)
COPPER_GLINT = (255, 196, 140)
ORB_CORE, ORB_RIM, ORB_EDGE = (120, 205, 255), (70, 140, 255), (36, 64, 196)
VERDI_D, VERDI = (44, 110, 96), (70, 160, 138)
SOOT_D = (34, 32, 34)
BOLT_WHITE, BOLT_GLOW, BOLT_DEEP = (255, 255, 255), (170, 224, 255), (88, 150, 255)


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


def glow_box(img, u, v, w, h, d, rng, core=BOLT_WHITE, rim=BOLT_GLOW, edge=BOLT_DEEP):
    """A lit box: the middle of every face white-hot, a glow ring round it, a deep blue outer edge."""
    for name, (x0, y0, x1, y1) in box_faces(u, v, w, h, d).items():
        fill(img, x0, y0, x1, y1, edge)
        if x1 - x0 > 2 and y1 - y0 > 2:
            fill(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, rim)
        if x1 - x0 > 4 and y1 - y0 > 4:
            fill(img, x0 + 2, y0 + 2, x1 - 2, y1 - 2, core)
        elif x1 - x0 > 2 and y1 - y0 > 2:
            fill(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, core)
        # a few darker pixels so a long face crackles instead of reading as a flat bar
        for _ in range(max(1, (x1 - x0) * (y1 - y0) // 14)):
            px, py = rng.randrange(x0, x1), rng.randrange(y0, y1)
            img.putpixel((px, py), shade(rim, rng.uniform(0.75, 0.95)) + (255,))


def rocket(rng):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    glow_box(img, 0, 0, 8, 2, 2, rng)                                                    # the core, 8x2x2
    glow_box(img, 0, 4, 2, 2, 2, rng, core=BOLT_WHITE, rim=BOLT_WHITE, edge=BOLT_GLOW)    # the nose, 2x2x2
    for name, (x0, y0, x1, y1) in box_faces(8, 4, 1, 1, 1).items():                       # the needle, 1x1x1
        fill(img, x0, y0, x1, y1, BOLT_WHITE)
    # halo fins: a flat plate 2x0.5x5 at (0, 8) and an upright one 2x5x0.5 at (14, 8), electric blue with a pale edge
    fill(img, 0, 8, 14, 14, BOLT_DEEP)
    fill(img, 14, 8, 19, 14, BOLT_DEEP)
    for y in range(8, 14):
        img.putpixel((0, y), BOLT_GLOW + (255,))
        img.putpixel((14, y), BOLT_GLOW + (255,))
    for x in range(0, 19):
        img.putpixel((x, 8), BOLT_GLOW + (255,))
    # tail 5x1.5x1.5 at (0, 16): deep blue streaming off, blue-white, white-hot where it meets the core
    for name, (x0, y0, x1, y1) in box_faces(0, 16, 5, 2, 2).items():
        for x in range(x0, x1):
            t = (x - x0) / max(1, (x1 - x0 - 1))
            c = BOLT_DEEP if t < 0.34 else BOLT_GLOW if t < 0.72 else BOLT_WHITE
            if name in ("left", "right"):
                c = BOLT_GLOW
            for y in range(y0, y1):
                img.putpixel((x, y), c + (255,))
    return img


def rack(rng):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # back plate 9x10x1 at (0, 0): darker copper with a verdigris bloom and a rivet row under the top edge
    for name, (x0, y0, x1, y1) in box_faces(0, 0, 9, 10, 1).items():
        k = {"top": 1.1, "bottom": 0.6}.get(name, 0.9)
        noisy(img, x0, y0, x1, y1, shade(COPPER_D, k), rng, 0.1)
        if name in ("front", "back"):
            for _ in range(10):
                px, py = rng.randrange(x0, x1), rng.randrange(y0 + 2, y1)
                img.putpixel((px, py), shade(VERDI_D, k) + (255,))
            for x in range(x0 + 1, x1, 3):
                img.putpixel((x, y0 + 1), shade(SOOT_D, 1.2) + (255,))
    # yoke 15x2x3 at (0, 17): copper with a lit top edge
    for name, (x0, y0, x1, y1) in box_faces(0, 17, 15, 2, 3).items():
        k = {"top": 1.2, "bottom": 0.65}.get(name, 1.0)
        noisy(img, x0, y0, x1, y1, shade(COPPER, k), rng)
        if name in ("front", "back", "left", "right"):
            fill(img, x0, y0, x1, y0 + 1, shade(COPPER_L, k))
    # tubes 3x12x3 at (22, 0): polished copper with a lit side and a glint, verdigris creeping up from the foot; the top
    # face (inside the ring) is the dark bore
    for name, (x0, y0, x1, y1) in box_faces(22, 0, 3, 12, 3).items():
        k = {"top": 1.0, "bottom": 0.6, "left": 0.8, "right": 1.15}.get(name, 1.0)
        if name == "top":
            fill(img, x0, y0, x1, y1, SOOT_D)
            continue
        noisy(img, x0, y0, x1, y1, shade(COPPER, k), rng, 0.07)
        if name in ("left", "front", "right", "back"):
            for y in range(y1 - 3, y1):
                fill(img, x0, y, x1, y + 1, shade(VERDI, k * (0.9 + 0.05 * (y - (y1 - 3)))))
            for y in range(y0, y1 - 3):
                img.putpixel((x0 + 1, y), (COPPER_GLINT if name in ("front", "right") else shade(COPPER_L, k)) + (255,))
    # muzzle rings 4x2x4 at (36, 0): bright copper bands; the top face a ring round a dark bore
    for name, (x0, y0, x1, y1) in box_faces(36, 0, 4, 2, 4).items():
        k = {"top": 1.25, "bottom": 0.7}.get(name, 1.05)
        noisy(img, x0, y0, x1, y1, shade(COPPER_L, k), rng, 0.05)
        if name == "top":
            fill(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, SOOT_D)
        else:
            fill(img, x0, y1 - 1, x1, y1, shade(COPPER, 0.8))
    # straps 4x2x7 at (36, 7): dark copper bands with a rivet each side
    for name, (x0, y0, x1, y1) in box_faces(36, 7, 4, 2, 7).items():
        k = {"top": 1.1, "bottom": 0.6}.get(name, 0.95)
        noisy(img, x0, y0, x1, y1, shade(COPPER_D, k * 1.15), rng, 0.06)
        if name in ("left", "right"):
            img.putpixel(((x0 + x1) // 2, y0), shade(COPPER_GLINT, 0.9) + (255,))
    # lit part, drawn full bright: the charge seated in each mouth (2x3x2 at (56, 0)), electric blue with a pale crown
    for name, (x0, y0, x1, y1) in box_faces(56, 0, 2, 3, 2).items():
        if name in ("top", "bottom"):
            fill(img, x0, y0, x1, y1, ORB_CORE)
            continue
        fill(img, x0, y0, x1, y1, ORB_RIM)
        fill(img, x0, y0, x1, y0 + 1, ORB_CORE)
        fill(img, x0, y1 - 1, x1, y1, ORB_EDGE)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    rng = random.Random(20261105)
    rocket(rng).save(os.path.join(OUT, "rocket_shell.png"))
    rack(rng).save(os.path.join(OUT, "rocket_rack.png"))
    print("wrote rocket_shell.png and rocket_rack.png to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
