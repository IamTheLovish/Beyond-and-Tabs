#!/usr/bin/env python3
"""
Pixel-art HUD icons for the shared BAR buildings, the idle-factory button and the three original factions' labs,
towers and capitols (lovish's clips: the extractor's build icon was a dirt-looking raw iron block, the wind
generator a flat iron square, the Drydock a missing texture, the Horde's Fortress a plain nether-brick face).
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
    "_brass": None,                                              # Brassworks: no sails - a steam boiler (steam_boiler)
}


def steam_boiler():
    """The Brassworks' energy building: a riveted brass boiler on a black firebox, a chimney puffing steam."""
    im = canvas()
    brass = (201, 138, 46, 255)
    soot = (42, 38, 34, 255)
    glow = (255, 150, 40, 255)
    # firebox with its glowing mouth
    rect(im, 3, 11, 12, 14, soot)
    outline(im, 3, 11, 12, 14, darker(soot, .6))
    rect(im, 6, 12, 9, 13, glow)
    px(im, 7, 12, (255, 220, 120, 255)); px(im, 8, 13, (230, 100, 30, 255))
    # the boiler drum: rounded brass with a darker band and rivets
    rect(im, 3, 5, 12, 10, brass)
    rect(im, 4, 4, 11, 4, brass)
    outline(im, 3, 5, 12, 10, darker(brass, .75))
    rect(im, 3, 7, 12, 7, darker(brass, .85))
    for x in (4, 7, 10):
        px(im, x, 5, lighter(brass, .5)); px(im, x, 9, lighter(brass, .5))
    # a round gauge
    px(im, 7, 8, (230, 230, 220, 255)); px(im, 8, 8, (230, 230, 220, 255))
    # chimney and steam
    rect(im, 10, 1, 11, 4, soot)
    for x, y in [(10, 0), (12, 0), (13, 1)]:
        px(im, x, y, (236, 236, 232, 255))
    rect(im, 0, 14, 15, 15, darker(soot, .8))   # the dais
    return im


def wind_generator(variant):
    if SAILS[variant] is None:
        return steam_boiler()
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


# ---------------------------------------------------------------- heaven's gate: a jade-roofed red gate under a gold sun
def heavens_gate():
    im = canvas()
    red = (168, 35, 28, 255)
    jade = (46, 150, 104, 255)
    gold = (212, 175, 55, 255)
    stone = (150, 150, 146, 255)
    # the plinth
    rect(im, 0, 14, 15, 15, stone)
    outline(im, 0, 14, 15, 15, darker(stone))
    # two red pillars
    rect(im, 2, 6, 3, 13, red); rect(im, 12, 6, 13, 13, red)
    px(im, 3, 7, darker(red)); px(im, 13, 7, darker(red))
    # the jade roof: a wide eave with gold ridge ends, a narrower top tier
    rect(im, 0, 5, 15, 5, jade)
    rect(im, 1, 4, 14, 4, jade)
    px(im, 0, 4, gold); px(im, 15, 4, gold)
    rect(im, 4, 3, 11, 3, jade)
    px(im, 3, 3, gold); px(im, 12, 3, gold)
    for x in (2, 6, 9, 13):
        px(im, x, 5, lighter(jade, .3))
    # the sun disc over the arch
    rect(im, 7, 0, 8, 1, gold)
    px(im, 6, 1, gold); px(im, 9, 1, gold)
    rect(im, 7, 2, 8, 2, lighter(gold, .3))
    # lanterns on the pillars
    px(im, 1, 9, gold); px(im, 14, 9, gold)
    px(im, 1, 10, (255, 190, 80, 255)); px(im, 14, 10, (255, 190, 80, 255))
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


# ======================================================================================================================
# Faction buildings whose icons were plain vanilla block faces (lovish's clip: the Horde T3 lab Fortress showed a
# nether-brick square). Each is a small silhouette of the building in its faction's palette, so the build bar reads
# at a glance: the three labs, the tower, and the capitol of each of the three original factions.

# ---- Sunforged Kingdom: plaster, oak and stone with gold
PLASTER = (226, 214, 190, 255)
OAK = (132, 96, 56, 255)
STONE = (140, 140, 136, 255)
GOLD = (230, 184, 60, 255)
RED = (190, 50, 44, 255)


def _battlements(im, x0, x1, y, c, step=2):
    """A row of merlons along the top of a wall: every other column one pixel higher."""
    for x in range(x0, x1 + 1, step):
        px(im, x, y, c)


def _flag(im, x, y, c, pole=(60, 50, 40, 255)):
    rect(im, x, y, x, y + 3, pole)
    rect(im, x + 1, y, x + 3, y + 1, c)


def barracks():
    # a long oak hall with a tiled roof, a red banner over the door and crossed swords on the gable
    im = canvas()
    rect(im, 1, 8, 14, 14, PLASTER)
    outline(im, 1, 8, 14, 14, darker(OAK, .8))
    for x in (4, 8, 11):   # timber frame
        rect(im, x, 9, x, 13, OAK)
    for i in range(5):     # pitched roof
        rect(im, 1 + i, 7 - i, 14 - i, 7 - i, darker(RED, .75) if i % 2 else RED)
    rect(im, 6, 11, 8, 14, darker(OAK, .6))   # door
    # crossed swords on the gable
    steel = (210, 214, 222, 255)
    line(im, 5, 1, 10, 6, steel); line(im, 10, 1, 5, 6, steel)
    px(im, 7, 3, lighter(steel, .5)); px(im, 8, 3, lighter(steel, .5))
    _flag(im, 12, 0, GOLD)
    return im


def arcane_tower():
    # a slim stone spire with a glowing amethyst crown and runes down its side
    im = canvas()
    amethyst = (160, 110, 220, 255)
    rect(im, 5, 5, 10, 15, STONE)
    outline(im, 5, 5, 10, 15, darker(STONE))
    rect(im, 4, 14, 11, 15, darker(STONE, .8))   # plinth
    rect(im, 4, 4, 11, 4, darker(STONE))           # parapet
    for y in (7, 10, 13):
        px(im, 7, y, amethyst); px(im, 8, y, darker(amethyst, .8))
    # the crystal crown
    rect(im, 6, 2, 9, 3, amethyst)
    rect(im, 7, 0, 8, 1, lighter(amethyst, .4))
    px(im, 5, 3, lighter(amethyst, .2)); px(im, 10, 3, lighter(amethyst, .2))
    px(im, 3, 1, lighter(amethyst, .6)); px(im, 12, 1, lighter(amethyst, .6))   # sparks
    return im


def castle():
    # a grey keep between two towers, merlons along the walls, a gold banner on the keep
    im = canvas()
    rect(im, 0, 6, 15, 15, STONE)
    rect(im, 0, 3, 3, 15, lighter(STONE, .1)); rect(im, 12, 3, 15, 15, lighter(STONE, .1))   # towers
    rect(im, 5, 4, 10, 15, darker(STONE, .9))   # the keep
    _battlements(im, 0, 3, 2, STONE); _battlements(im, 12, 15, 2, STONE)
    _battlements(im, 5, 10, 3, darker(STONE, .9))
    outline(im, 0, 3, 15, 15, darker(STONE))
    rect(im, 6, 11, 9, 15, (50, 40, 32, 255))   # gate
    px(im, 7, 11, (30, 24, 20, 255)); px(im, 8, 11, (30, 24, 20, 255))
    px(im, 1, 7, (30, 30, 34, 255)); px(im, 14, 7, (30, 30, 34, 255))   # arrow slits
    _flag(im, 7, 0, GOLD)
    return im


def watchtower():
    # a narrow stone tower with a wooden hoarding and a lit brazier on top
    im = canvas()
    rect(im, 6, 6, 9, 15, STONE)
    outline(im, 6, 6, 9, 15, darker(STONE))
    rect(im, 4, 4, 11, 6, OAK)                   # the hoarding
    rect(im, 4, 3, 11, 3, darker(OAK, .7))
    _battlements(im, 4, 11, 2, darker(OAK, .7))
    px(im, 7, 9, (30, 30, 34, 255)); px(im, 7, 12, (30, 30, 34, 255))
    fire = (255, 170, 50, 255)
    rect(im, 7, 1, 8, 2, fire); px(im, 7, 0, (255, 230, 120, 255)); px(im, 8, 0, (230, 100, 30, 255))
    return im


def town_centre():
    # a wide plaster hall with a bell tower, the Kingdom's seat
    im = canvas()
    rect(im, 1, 8, 14, 15, PLASTER)
    outline(im, 1, 8, 14, 15, darker(OAK, .8))
    for i in range(3):
        rect(im, 1 + i, 7 - i, 14 - i, 7 - i, darker(OAK, .75) if i % 2 else OAK)   # oak-shingle roof
    rect(im, 6, 11, 8, 15, darker(OAK, .6))      # door
    rect(im, 3, 10, 4, 11, (90, 120, 160, 255)); rect(im, 11, 10, 12, 11, (90, 120, 160, 255))   # windows
    # bell tower
    rect(im, 6, 2, 9, 5, STONE)
    rect(im, 5, 1, 10, 1, darker(STONE))
    px(im, 7, 3, GOLD); px(im, 8, 3, GOLD); px(im, 7, 4, darker(GOLD, .7)); px(im, 8, 4, darker(GOLD, .7))
    return im


# ---- Gravebound: deepslate, dead grass, soul-fire teal and bone
SLATE = (74, 74, 80, 255)
BONE = (222, 214, 196, 255)
SOUL = (80, 200, 210, 255)
DEADGRASS = (70, 86, 50, 255)


def graveyard():
    # three headstones on dead grass under a dead tree
    im = canvas()
    rect(im, 0, 12, 15, 15, DEADGRASS)
    for x in (2, 7, 12):
        rect(im, x, 7, x + 2, 12, BONE)
        px(im, x + 1, 6, BONE)
        px(im, x + 1, 9, darker(BONE, .7))   # the engraving
        px(im, x, 8, darker(BONE, .8))
    # the dead tree, back left
    bark = (46, 36, 30, 255)
    rect(im, 5, 1, 5, 7, bark); line(im, 5, 3, 3, 1, bark); line(im, 5, 4, 8, 2, bark); px(im, 9, 1, bark)
    px(im, 14, 2, (120, 120, 130, 255))   # a crow? no: a sliver of moon
    return im


def dungeon():
    # an iron-barred cell with a skull behind the bars and a torch
    im = canvas()
    iron = (120, 124, 130, 255)
    rect(im, 1, 1, 14, 14, (30, 28, 34, 255))
    outline(im, 1, 1, 14, 14, SLATE)
    outline(im, 0, 0, 15, 15, darker(SLATE))
    # the skull
    rect(im, 6, 5, 9, 8, BONE); rect(im, 7, 9, 8, 9, BONE)
    px(im, 6, 6, (20, 16, 20, 255)); px(im, 9, 6, (20, 16, 20, 255))
    px(im, 7, 9, darker(BONE, .6)); px(im, 8, 9, darker(BONE, .6))
    for x in (3, 7, 11):   # bars
        rect(im, x, 1, x, 14, iron)
    rect(im, 1, 7, 14, 7, iron)
    px(im, 13, 3, (255, 170, 50, 255)); px(im, 13, 4, (255, 230, 120, 255))   # torch
    return im


def stronghold():
    # a brooding deepslate fortress, soul-fire windows, bone spikes along the top
    im = canvas()
    rect(im, 0, 5, 15, 15, SLATE)
    rect(im, 4, 3, 11, 15, darker(SLATE, .85))   # the keep
    outline(im, 0, 5, 15, 15, darker(SLATE, .6))
    for x in (1, 4, 7, 10, 13):   # spikes
        px(im, x, 2 if 4 <= x <= 11 else 4, BONE)
        px(im, x, 3 if 4 <= x <= 11 else 5, darker(BONE, .8))
    for x, y in ((2, 8), (13, 8), (6, 6), (9, 6)):   # soul windows
        px(im, x, y, SOUL); px(im, x, y + 1, darker(SOUL, .6))
    rect(im, 6, 11, 9, 15, (14, 12, 16, 255))   # gate
    px(im, 7, 11, SOUL); px(im, 8, 11, SOUL)
    return im


def dark_watchtower():
    # a crooked slate tower with a soul-flame beacon
    im = canvas()
    rect(im, 6, 5, 9, 15, SLATE)
    outline(im, 6, 5, 9, 15, darker(SLATE, .6))
    rect(im, 4, 4, 11, 5, darker(SLATE, .8))   # the top ledge
    _battlements(im, 4, 11, 3, darker(SLATE, .8))
    px(im, 7, 9, SOUL); px(im, 7, 12, SOUL)
    rect(im, 7, 1, 8, 2, SOUL); px(im, 7, 0, lighter(SOUL, .5)); px(im, 8, 0, darker(SOUL, .7))
    return im


def mausoleum():
    # a crypt with a stone gable, a dark door glowing from inside and dead ivy
    im = canvas()
    rect(im, 1, 7, 14, 15, SLATE)
    outline(im, 1, 7, 14, 15, darker(SLATE, .6))
    for i in range(5):   # the gable
        rect(im, 2 + i, 6 - i, 13 - i, 6 - i, darker(SLATE, .8) if i % 2 else lighter(SLATE, .12))
    px(im, 7, 1, BONE); px(im, 8, 1, BONE); px(im, 7, 0, BONE)   # the finial
    rect(im, 6, 10, 9, 15, (14, 12, 16, 255))   # the door
    px(im, 7, 11, (110, 200, 120, 255)); px(im, 8, 12, (80, 160, 90, 255))   # something glows in there
    for x, y in ((2, 8), (2, 9), (3, 10), (12, 8), (13, 9)):   # ivy
        px(im, x, y, DEADGRASS)
    return im


# ---- Gilded Horde: blackstone, nether brick, portal purple and gold
BLACKSTONE = (44, 40, 46, 255)
NBRICK = (72, 36, 40, 255)
PORTAL = (150, 70, 220, 255)
HGOLD = (236, 190, 70, 255)
LAVA = (255, 130, 40, 255)


def _portal(im, x0, y0, x1, y1, frame):
    rect(im, x0, y0, x1, y1, frame)
    rect(im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, PORTAL)
    for k, (x, y) in enumerate(((x0 + 1, y0 + 2), (x1 - 1, y0 + 4), (x0 + 2, y1 - 2), (x1 - 2, y0 + 1))):
        if x0 < x < x1 and y0 < y < y1:
            px(im, x, y, lighter(PORTAL, .4) if k % 2 else darker(PORTAL, .7))


def portal_military():
    # an obsidian portal with a crossed-axes banner: where the Horde's fighters come through
    im = canvas()
    obs = (28, 20, 40, 255)
    _portal(im, 3, 2, 12, 15, obs)
    rect(im, 3, 2, 12, 2, lighter(obs, .2)); rect(im, 3, 15, 12, 15, lighter(obs, .2))
    # the banner across the top
    rect(im, 1, 0, 14, 1, RED)
    px(im, 1, 2, darker(RED)); px(im, 14, 2, darker(RED))
    # a gold axe head in the glow
    rect(im, 7, 6, 8, 10, HGOLD)
    rect(im, 5, 7, 6, 9, lighter(HGOLD, .3)); rect(im, 9, 7, 10, 9, lighter(HGOLD, .3))
    return im


def flame_sanctuary():
    # a blackstone altar carrying one great flame
    im = canvas()
    rect(im, 2, 11, 13, 15, BLACKSTONE)
    outline(im, 2, 11, 13, 15, darker(BLACKSTONE, .5))
    rect(im, 4, 10, 11, 10, HGOLD)   # the gilded rim
    flame = ((255, 230, 120, 255), (255, 180, 60, 255), LAVA, (220, 70, 30, 255))
    for i, (x0, x1) in enumerate(((6, 9), (5, 10), (5, 10), (6, 9), (6, 9), (7, 8), (7, 8), (7, 7))):
        rect(im, x0, 9 - i, x1, 9 - i, flame[min(3, i // 2)] if i < 6 else flame[0])
    px(im, 7, 7, flame[0]); px(im, 8, 6, flame[0])
    return im


def fortress():
    # the Horde's nether-brick fortress: a wide wall, a taller central keep, gold-capped towers and lava slits
    im = canvas()
    rect(im, 0, 7, 15, 15, NBRICK)
    rect(im, 0, 4, 3, 15, lighter(NBRICK, .1)); rect(im, 12, 4, 15, 15, lighter(NBRICK, .1))   # towers
    rect(im, 5, 2, 10, 15, darker(NBRICK, .85))   # the keep
    _battlements(im, 0, 3, 3, HGOLD); _battlements(im, 12, 15, 3, HGOLD)   # gilded merlons
    _battlements(im, 5, 10, 1, HGOLD)
    outline(im, 0, 4, 15, 15, darker(NBRICK, .5))
    for x, y in ((1, 8), (14, 8), (7, 5), (8, 5)):   # lava slits
        px(im, x, y, LAVA)
    rect(im, 6, 11, 9, 15, (20, 12, 14, 255))   # gate
    px(im, 7, 11, LAVA); px(im, 8, 11, darker(LAVA, .7))
    return im


def bastion():
    # a squat blackstone bastion with gilded trim: the Horde's tower
    im = canvas()
    rect(im, 4, 5, 11, 15, BLACKSTONE)
    outline(im, 4, 5, 11, 15, darker(BLACKSTONE, .5))
    rect(im, 3, 4, 12, 4, HGOLD)                   # the gilded band
    _battlements(im, 3, 12, 3, BLACKSTONE)
    rect(im, 2, 14, 13, 15, darker(BLACKSTONE, .7))   # plinth
    px(im, 7, 8, LAVA); px(im, 8, 8, LAVA); px(im, 7, 11, LAVA)   # slits
    px(im, 5, 1, lighter(HGOLD, .4)); px(im, 10, 1, lighter(HGOLD, .4))   # gold spikes
    px(im, 5, 2, HGOLD); px(im, 10, 2, HGOLD)
    return im


def central_portal():
    # the great gilded portal: the Horde's capitol
    im = canvas()
    _portal(im, 2, 1, 13, 15, darker(HGOLD, .8))
    outline(im, 2, 1, 13, 15, HGOLD)
    rect(im, 0, 14, 15, 15, BLACKSTONE)   # the dais
    px(im, 7, 0, lighter(HGOLD, .4)); px(im, 8, 0, lighter(HGOLD, .4))   # crest
    px(im, 1, 13, HGOLD); px(im, 14, 13, HGOLD)
    return im


# ---- Stormcallers capitol: a three-tier pagoda, red columns under jade roofs with gold ridge ends (heavens_gate's palette)
def jade_pagoda():
    im = canvas()
    red = (168, 35, 28, 255)
    jade = (46, 150, 104, 255)
    gold = (212, 175, 55, 255)
    stone = (150, 150, 146, 255)
    rect(im, 0, 14, 15, 15, stone)
    outline(im, 0, 14, 15, 15, darker(stone))
    # three tiers, each narrower: columns then a wide jade eave with gold ends
    for i, (x0, x1, y) in enumerate(((2, 13, 11), (3, 12, 7), (5, 10, 3))):
        rect(im, x0 + 1, y + 1, x0 + 1, y + 2, red); rect(im, x1 - 1, y + 1, x1 - 1, y + 2, red)
        rect(im, x0, y, x1, y, jade)
        rect(im, x0 + 1, y - 1, x1 - 1, y - 1, jade)
        px(im, x0, y - 1, gold); px(im, x1, y - 1, gold)
        for x in range(x0 + 2, x1 - 1, 3):
            px(im, x, y, lighter(jade, .3))
    rect(im, 7, 0, 8, 1, gold)   # the finial
    px(im, 7, 13, (255, 190, 80, 255)); px(im, 8, 13, (255, 190, 80, 255))   # the lit doorway
    return im


# ---- Stormcallers T1 lab: a walled courtyard with a round moon gate and a pink lotus over it (lovish's clip: the
# Lotus Academy's build tile and its multi-selection tiles were a plain cherry-planks face, "rows of pink squares")
def lotus_academy():
    im = canvas()
    red = (168, 35, 28, 255)
    jade = (46, 150, 104, 255)
    gold = (212, 175, 55, 255)
    stone = (150, 150, 146, 255)
    wall = (196, 92, 70, 255)
    pink = (236, 140, 180, 255)
    rect(im, 0, 14, 15, 15, stone)
    outline(im, 0, 14, 15, 15, darker(stone))
    # the courtyard wall with a jade coping and gold corner caps
    rect(im, 1, 8, 14, 13, wall)
    rect(im, 0, 7, 15, 7, jade)
    px(im, 0, 6, gold); px(im, 15, 6, gold)
    for x in (3, 7, 11):
        px(im, x, 7, lighter(jade, .3))
    # the round moon gate, dark inside, with the training ring's sand showing through
    for y in range(9, 14):
        for x in range(5, 11):
            if (x - 7.5) ** 2 + (y - 11.5) ** 2 <= 7.0:
                px(im, x, y, (40, 30, 30, 255))
    rect(im, 6, 13, 9, 13, (214, 190, 140, 255))
    # red posts either side of the gate
    rect(im, 3, 9, 3, 13, red); rect(im, 12, 9, 12, 13, red)
    # the lotus over the gate: three pink petals on a jade pad
    rect(im, 5, 5, 10, 5, jade)
    px(im, 7, 2, pink); px(im, 8, 2, pink)
    rect(im, 6, 3, 9, 4, pink)
    px(im, 5, 3, lighter(pink, .4)); px(im, 10, 3, lighter(pink, .4))
    px(im, 7, 4, gold); px(im, 8, 4, gold)
    return im


# ---- Brassworks capitol: a deepslate hall under a brass dome, a smokestack and a glowing furnace mouth
def foundry_hall():
    im = canvas()
    brass = (201, 138, 46, 255)
    stone = (74, 76, 82, 255)
    soot = (42, 38, 34, 255)
    glow = (255, 150, 40, 255)
    rect(im, 0, 14, 15, 15, darker(stone, .8))
    # the hall
    rect(im, 2, 8, 13, 13, stone)
    outline(im, 2, 8, 13, 13, darker(stone, .7))
    # the brass dome: three steps
    rect(im, 3, 7, 12, 7, brass)
    rect(im, 4, 6, 11, 6, brass)
    rect(im, 5, 5, 10, 5, brass)
    rect(im, 6, 4, 9, 4, lighter(brass, .3))
    px(im, 7, 3, lighter(brass, .5)); px(im, 8, 3, lighter(brass, .5))
    px(im, 7, 2, (236, 236, 232, 255))   # the finial's glint
    # the smokestack and its smoke
    rect(im, 12, 1, 13, 7, soot)
    px(im, 12, 0, (220, 220, 216, 255)); px(im, 14, 0, (220, 220, 216, 255))
    # the furnace mouth and two windows
    rect(im, 6, 11, 9, 13, glow)
    px(im, 7, 11, (255, 220, 120, 255)); px(im, 8, 12, (230, 100, 30, 255))
    px(im, 4, 10, glow); px(im, 11, 10, glow)
    return im


# ---- Brassworks T1 lab: an open workshop under a brass roof with an anvil and a glowing crucible
def machine_shop():
    im = canvas()
    brass = (201, 138, 46, 255)
    stone = (74, 76, 82, 255)
    soot = (42, 38, 34, 255)
    glow = (255, 150, 40, 255)
    rect(im, 0, 14, 15, 15, darker(stone, .8))
    # posts
    for x in (2, 13):
        rect(im, x, 5, x, 13, stone)
    # the brass roof with its ridge
    rect(im, 1, 4, 14, 5, brass)
    rect(im, 2, 3, 13, 3, lighter(brass, .25))
    rect(im, 6, 2, 9, 2, lighter(brass, .4))
    outline(im, 1, 4, 14, 5, darker(brass, .75))
    # the anvil on its block
    rect(im, 4, 11, 7, 13, soot)
    rect(im, 3, 10, 8, 10, darker(soot, .6))
    px(im, 5, 11, (90, 90, 96, 255)); px(im, 6, 11, (90, 90, 96, 255))
    # the crucible, glowing
    rect(im, 9, 10, 12, 13, darker(stone, .7))
    rect(im, 10, 9, 11, 9, glow)
    px(im, 10, 8, (255, 220, 120, 255))
    # a gear on the back wall
    px(im, 7, 7, brass); px(im, 8, 7, brass); px(im, 7, 8, brass); px(im, 8, 8, brass)
    px(im, 6, 7, darker(brass, .8)); px(im, 9, 8, darker(brass, .8))
    return im


FACTION_ICONS = {
    "jade_pagoda": jade_pagoda,
    "lotus_academy": lotus_academy,
    "foundry_hall": foundry_hall, "machine_shop": machine_shop,
    "barracks": barracks, "arcane_tower": arcane_tower, "castle": castle, "watchtower": watchtower,
    "town_centre": town_centre,
    "graveyard": graveyard, "dungeon": dungeon, "stronghold": stronghold, "dark_watchtower": dark_watchtower,
    "mausoleum": mausoleum,
    "portal_military": portal_military, "flame_sanctuary": flame_sanctuary, "fortress": fortress,
    "bastion": bastion, "central_portal": central_portal,
}


def main():
    os.makedirs(BUILDINGS, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)
    out = {
        os.path.join(BUILDINGS, "metal_extractor.png"): metal_extractor(),
        os.path.join(BUILDINGS, "energy_converter.png"): energy_converter(),
        os.path.join(BUILDINGS, "storage_vault.png"): storage_vault(),
        os.path.join(BUILDINGS, "drydock.png"): drydock(),
        os.path.join(BUILDINGS, "heavens_gate.png"): heavens_gate(),
        os.path.join(ITEMS, "idle_factory.png"): idle_factory(),
        os.path.join(ITEMS, "stance_hold_fire.png"): stance_hold_fire(),
        os.path.join(ITEMS, "stance_return_fire.png"): stance_return_fire(),
        os.path.join(ITEMS, "stance_fire_at_will.png"): stance_fire_at_will(),
    }
    for v in SAILS:
        out[os.path.join(BUILDINGS, "wind_generator" + v + ".png")] = wind_generator(v)
    for name, fn in FACTION_ICONS.items():
        out[os.path.join(BUILDINGS, name + ".png")] = fn()
    for path, im in out.items():
        im.save(path, optimize=True)
        print("wrote", os.path.relpath(path, os.path.join(ROOT, "..", "..", "..", "..", "..", "..", "..")))


if __name__ == "__main__":
    main()
