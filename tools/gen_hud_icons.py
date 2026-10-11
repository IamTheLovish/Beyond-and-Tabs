#!/usr/bin/env python3
"""
Pixel-art HUD icons for the shared BAR buildings and the idle-factory button (lovish's clip: the extractor's build
icon was a dirt-looking raw iron block, the wind generator a flat iron square, the Drydock a missing texture).
Writes 16x16 building icons to assets/reignofnether/textures/icons/buildings/ and the 32x32 idle_factory button icon
to textures/icons/items/. Deterministic: re-running it reproduces the same files.

    python3 tools/gen_hud_icons.py
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "bar", "src", "main", "resources", "assets",
                    "reignofnether", "textures", "icons")
BUILDINGS = os.path.join(ROOT, "buildings")
ITEMS = os.path.join(ROOT, "items")

T = (0, 0, 0, 0)


def canvas(n=16):
    return Image.new("RGBA", (n, n), T)


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def outline(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        px(im, x, y0, c); px(im, x, y1, c)
    for y in range(y0, y1 + 1):
        px(im, x0, y, c); px(im, x1, y, c)


def line(im, x0, y0, x1, y1, c):
    ImageDraw.Draw(im).line((x0, y0, x1, y1), fill=c)


def darker(c, f=0.7):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])


def lighter(c, f=0.3):
    return (int(c[0] + (255 - c[0]) * f), int(c[1] + (255 - c[1]) * f), int(c[2] + (255 - c[2]) * f), c[3])


# ---------------------------------------------------------------- extractor: a raw-iron chunk with a drill head on it
def metal_extractor():
    im = canvas()
    ore = (196, 150, 120, 255)
    rock = (110, 100, 96, 255)
    steel = (178, 186, 196, 255)
    # the ore chunk, bottom
    rect(im, 1, 9, 14, 14, rock)
    for x, y in [(2, 10), (3, 11), (5, 10), (6, 12), (8, 10), (9, 13), (11, 11), (12, 10), (13, 13), (4, 13)]:
        px(im, x, y, ore)
    outline(im, 1, 9, 14, 14, darker(rock))
    # derrick frame
    rect(im, 6, 1, 9, 2, steel)
    line(im, 6, 2, 3, 9, steel)
    line(im, 9, 2, 12, 9, steel)
    rect(im, 7, 2, 8, 8, darker(steel, .8))
    # drill bit biting the ore
    px(im, 7, 9, lighter(steel, .5)); px(im, 8, 9, lighter(steel, .5))
    px(im, 7, 10, steel); px(im, 8, 10, steel)
    px(im, 7, 11, darker(steel, .8)); px(im, 8, 11, darker(steel, .8))
    # sparks
    px(im, 5, 8, (255, 220, 120, 255)); px(im, 10, 8, (255, 220, 120, 255))
    return im


# ---------------------------------------------------------------- wind generator: a mast with a sail in the faction's cloth
SAILS = {
    "": ((236, 232, 220, 255), (120, 90, 60, 255)),            # Kingdom: plaster-white canvas, oak mast
    "_dark": ((70, 66, 78, 255), (40, 38, 44, 255)),            # The Fallen: tattered grey-violet, dark mast
    "_nether": ((214, 120, 48, 255), (60, 30, 30, 255)),        # Gilded Legion: ember orange, blackstone mast
    "_verdant": ((112, 185, 80, 255), (90, 60, 40, 255)),       # Verdant Court: leaf green
    "_tide": ((60, 200, 190, 255), (90, 70, 50, 255)),          # Tidewrought: sea-teal sailcloth, driftwood mast
    "_storm": ((120, 210, 170, 255), (200, 60, 70, 255)),       # Stormcallers: jade paper, lacquered red mast
}


def wind_generator(variant):
    sail, mast = SAILS[variant]
    im = canvas()
    # mast
    rect(im, 7, 1, 8, 14, mast)
    # base
    rect(im, 5, 14, 10, 15, darker(mast, .6))
    # three blades of sail around the hub at (8, 6)
    hub = (8, 6)
    # top blade
    for y in range(1, 6):
        w = 1 if y < 3 else 2
        rect(im, hub[0] - w, y, hub[0] + w - 1, y, sail)
    # right-down blade
    for i in range(1, 6):
        rect(im, hub[0] + i, hub[1] + i // 2, hub[0] + i, hub[1] + i // 2 + 1, sail)
    # left-down blade
    for i in range(1, 6):
        rect(im, hub[0] - 1 - i, hub[1] + i // 2, hub[0] - 1 - i, hub[1] + i // 2 + 1, sail)
    # shading along one edge of each blade
    for y in range(2, 6):
        px(im, hub[0] - 2 if y >= 3 else hub[0] - 1, y, darker(sail, .8))
    px(im, hub[0], hub[1], darker(mast, .5)); px(im, hub[0] - 1, hub[1], darker(mast, .5))
    return im


# ---------------------------------------------------------------- energy converter: a furnace with a bolt going in
def energy_converter():
    im = canvas()
    stone = (120, 120, 124, 255)
    rect(im, 2, 3, 13, 14, stone)
    outline(im, 2, 3, 13, 14, darker(stone))
    # firebox mouth
    rect(im, 5, 9, 10, 12, (30, 24, 20, 255))
    for x, y, c in [(6, 11, (255, 150, 40, 255)), (7, 10, (255, 200, 80, 255)), (8, 11, (255, 150, 40, 255)),
                    (9, 11, (230, 100, 30, 255)), (7, 11, (255, 230, 120, 255)), (8, 10, (255, 180, 60, 255))]:
        px(im, x, y, c)
    # energy bolt striking the top
    bolt = (240, 200, 60, 255)
    for x, y in [(9, 0), (8, 1), (7, 2), (8, 3), (7, 4), (6, 5), (7, 6), (6, 7)]:
        px(im, x, y, bolt)
    px(im, 8, 2, lighter(bolt, .5)); px(im, 7, 5, lighter(bolt, .5))
    # metal ingot coming out the side
    rect(im, 11, 6, 14, 7, (178, 186, 196, 255))
    px(im, 14, 6, lighter((178, 186, 196, 255), .5))
    return im


# ---------------------------------------------------------------- storage vault: a chest with gold trim
def storage_vault():
    im = canvas()
    wood = (120, 86, 48, 255)
    gold = (230, 184, 60, 255)
    rect(im, 1, 4, 14, 14, wood)
    rect(im, 1, 4, 14, 7, lighter(wood, .15))      # lid
    outline(im, 1, 4, 14, 14, darker(wood))
    rect(im, 1, 7, 14, 7, darker(wood))            # lid seam
    # gold banding and lock
    rect(im, 1, 4, 14, 4, gold); rect(im, 1, 14, 14, 14, gold)
    rect(im, 1, 4, 1, 14, gold); rect(im, 14, 4, 14, 14, gold)
    rect(im, 7, 6, 8, 9, gold)
    px(im, 7, 8, darker(gold, .5)); px(im, 8, 8, darker(gold, .5))
    return im


# ---------------------------------------------------------------- drydock: copper slab with an anchor
def drydock():
    im = canvas()
    copper = (190, 108, 70, 255)
    rect(im, 0, 11, 15, 15, copper)
    outline(im, 0, 11, 15, 15, darker(copper))
    for x in (2, 6, 10, 13):
        px(im, x, 13, lighter(copper, .25))
    iron = (205, 210, 216, 255)
    # anchor: ring, shank, stock, flukes
    outline(im, 6, 0, 9, 3, iron)
    rect(im, 7, 3, 8, 10, iron)
    rect(im, 4, 4, 11, 4, iron)
    line(im, 7, 10, 3, 7, iron); line(im, 8, 10, 12, 7, iron)
    px(im, 3, 6, iron); px(im, 12, 6, iron)
    px(im, 7, 5, darker(iron, .7)); px(im, 8, 6, darker(iron, .7))
    return im


# ---------------------------------------------------------------- idle factory button (32x32): a lab with a pause badge
def idle_factory():
    n = 32
    im = canvas(n)
    wall = (96, 104, 116, 255)
    roof = (70, 76, 86, 255)
    # body + sawtooth roof
    rect(im, 3, 14, 28, 29, wall)
    outline(im, 3, 14, 28, 29, darker(wall))
    for i, x0 in enumerate((3, 12, 21)):
        for k in range(6):
            rect(im, x0 + k, 14 - k, x0 + 7 - k if k == 0 else x0 + 7, 14 - k, roof if k else darker(roof))
    # chimney, no smoke: it's idle
    rect(im, 24, 5, 26, 13, darker(wall, .85))
    # door and windows
    rect(im, 14, 22, 17, 29, (40, 42, 48, 255))
    for x0 in (6, 22):
        rect(im, x0, 18, x0 + 3, 21, (60, 70, 90, 255))
    # amber pause badge, bottom-right
    badge = (244, 196, 48, 255)
    rect(im, 18, 18, 30, 30, darker(badge, .45))
    rect(im, 19, 19, 29, 29, badge)
    rect(im, 21, 21, 23, 27, (40, 30, 10, 255))
    rect(im, 25, 21, 27, 27, (40, 30, 10, 255))
    return im


# ---------------------------------------------------------------- BAR fire-state toggles (16x16 command-card icons)
def _crosshair(im, c, ring=True):
    """A reticle: ring + four ticks; the fire-at-will one adds a dot, the hold-fire one is struck through."""
    if ring:
        for x, y in ((6, 3), (7, 3), (8, 3), (9, 3), (4, 5), (4, 6), (4, 9), (4, 10), (11, 5), (11, 6), (11, 9),
                     (11, 10), (6, 12), (7, 12), (8, 12), (9, 12), (5, 4), (10, 4), (5, 11), (10, 11)):
            px(im, x, y, c)
    rect(im, 7, 0, 8, 2, c); rect(im, 7, 13, 8, 15, c)
    rect(im, 0, 7, 2, 8, c); rect(im, 13, 7, 15, 8, c)


def stance_hold_fire():
    im = canvas()
    _crosshair(im, (150, 150, 158, 255))
    red = (232, 70, 60, 255)
    line(im, 2, 13, 13, 2, red); line(im, 3, 13, 13, 3, red)   # struck through: no shooting
    return im


def stance_return_fire():
    im = canvas()
    _crosshair(im, (200, 200, 206, 255))
    amber = (244, 196, 48, 255)
    # a returning arrow across the lower half: shoots back only
    line(im, 12, 9, 5, 9, amber); line(im, 12, 10, 5, 10, amber)
    for k in range(3):
        px(im, 5 + k, 8 - k, amber); px(im, 5 + k, 11 + k, amber)
    return im


def stance_fire_at_will():
    im = canvas()
    _crosshair(im, (230, 230, 236, 255))
    green = (124, 224, 122, 255)
    rect(im, 6, 6, 9, 9, green)
    rect(im, 7, 7, 8, 8, lighter(green, .5))
    return im


def main():
    os.makedirs(BUILDINGS, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)
    out = {
        os.path.join(BUILDINGS, "metal_extractor.png"): metal_extractor(),
        os.path.join(BUILDINGS, "energy_converter.png"): energy_converter(),
        os.path.join(BUILDINGS, "storage_vault.png"): storage_vault(),
        os.path.join(BUILDINGS, "drydock.png"): drydock(),
        os.path.join(ITEMS, "idle_factory.png"): idle_factory(),
        os.path.join(ITEMS, "stance_hold_fire.png"): stance_hold_fire(),
        os.path.join(ITEMS, "stance_return_fire.png"): stance_return_fire(),
        os.path.join(ITEMS, "stance_fire_at_will.png"): stance_fire_at_will(),
    }
    for v in SAILS:
        out[os.path.join(BUILDINGS, "wind_generator" + v + ".png")] = wind_generator(v)
    for path, im in out.items():
        im.save(path, optimize=True)
        print("wrote", os.path.relpath(path, os.path.join(ROOT, "..", "..", "..", "..", "..", "..", "..")))


if __name__ == "__main__":
    main()
