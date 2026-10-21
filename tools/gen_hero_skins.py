#!/usr/bin/env python3
"""Draws the seven faction heroes' skins, portraits and ability icons (hero slice 2, design/hero_mode.md).

Deterministic (no randomness at all): run it again and every file comes out byte-identical.

Skins - a hero wears its body's UV layout (the unit it is built on, HeroRenderers), so each skin is DERIVED from that
unit's own texture in this repo: the clothing is re-dyed into the hero's palette by brightness (the shading and the
details of the source survive), the face is left alone (or, for the lich, given glowing eyes), and a hero mark is
painted where the RTS camera sees it - a crown band on the hat layer, a gilded rim on the top of the head, an emblem
on the chest. Sources and layouts:
  - VillagerUnitModel 64x64: Dawn Paladin <- vindicator_unit, Corsair Captain <- cutlass_raider_unit,
    Arch-Engineer <- rifle_squad_unit (head 0..32 x 0..18 kept, hat box at (32,0) gets the crown band);
  - vanilla SkeletonModel 64x32: Gravecaller <- shade_ranger_unit, Thornheart Druid <- thornbow_unit;
  - vanilla PiglinModel 64x64: Warchief <- bonewright_unit (head 0..36 x 0..16 kept, a gold rim on the head's top);
  - WindcallerModel 128x64: Storm Witch <- windcaller_unit (re-dyed whole, skin tones kept).

Portraits (textures/mobheads/<hero>.png): 16x16 busts at 4x on the faction-agnostic hero backdrop with a double gold
frame, so every hero reads as a hero on the HUD. Ability icons (textures/icons/abilities/hero_<ability>.png): 96x96,
a glyph per ability over the hero's colour, drawn with PIL primitives.

usage: python3 tools/gen_hero_skins.py
"""
import colorsys, math, os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures")
ENT, HEADS, ICONS = os.path.join(TEX, "entities"), os.path.join(TEX, "mobheads"), os.path.join(TEX, "icons/abilities")

GOLD_D, GOLD, GOLD_L = (150, 104, 30), (222, 172, 58), (255, 228, 140)


def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255.0


def grad(pal, t):
    """3-stop gradient dark -> mid -> light."""
    d, m, l = pal
    if t < 0.5:
        a, b, u = d, m, t / 0.5
    else:
        a, b, u = m, l, (t - 0.5) / 0.5
    return tuple(int(round(a[i] + (b[i] - a[i]) * u)) for i in range(3))


def is_skin(c):
    r, g, b = c[:3]
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return 0.02 <= h <= 0.12 and 0.18 <= s <= 0.6 and v >= 0.45 and r > g > b


def redye(im, pal, keep_rects=(), keep_skin=False, detail=0.18):
    out = im.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0:
                continue
            if any(x0 <= x < x1 and y0 <= y < y1 for (x0, y0, x1, y1) in keep_rects):
                continue
            if keep_skin and is_skin(c):
                continue
            # stretch the brightness a little so flat sources still get a dark-to-light range
            t = min(1.0, max(0.0, (lum(c) - 0.08) / 0.8))
            n = grad(pal, t)
            px[x, y] = tuple(int(round(n[i] * (1 - detail) + c[i] * detail)) for i in range(3)) + (c[3],)
    return out


def put(im, x, y, c):
    if 0 <= x < im.size[0] and 0 <= y < im.size[1]:
        im.putpixel((x, y), tuple(c[:3]) + (255,))


def rect(im, x, y, w, h, c):
    for i in range(w):
        for j in range(h):
            put(im, x + i, y + j, c)


def crown_band(im, u, v, w, d, row):
    """A gold band round a box's side faces at {row} (relative to the side faces' top), points on the front."""
    y = v + d + row
    for x in range(u, u + 2 * (w + d)):
        put(im, x, y, GOLD)
        put(im, x, y + 1, GOLD_D)
    front_x = u + d
    for i in range(0, w, 2):
        put(im, front_x + i, y - 1, GOLD_L)


def top_rim(im, u, v, w, d, c=GOLD):
    """A rim round the top face of a box (seen from above by the RTS camera)."""
    x0, y0 = u + d, v
    for i in range(w):
        put(im, x0 + i, y0, c)
        put(im, x0 + i, y0 + d - 1, c)
    for j in range(d):
        put(im, x0, y0 + j, c)
        put(im, x0 + w - 1, y0 + j, c)


def emblem(im, cx, cy, c, size=2):
    for dx in range(-size, size + 1):
        put(im, cx + dx, cy, c)
    for dy in range(-size, size + 1):
        put(im, cx, cy + dy, c)


def src(name):
    return Image.open(os.path.join(ENT, name)).convert("RGBA")


# ------------------------------------------------------------------ skins

def dawn_paladin():
    im = redye(src("vindicator_unit.png"), ((120, 84, 28), (214, 170, 72), (250, 238, 200)), keep_rects=[(0, 0, 32, 19)])
    crown_band(im, 32, 0, 8, 8, 0)
    top_rim(im, 0, 0, 8, 8)
    emblem(im, 26, 31, (255, 244, 190))   # a sun on the chest (body front 22..30 x 26..38)
    return im


def corsair_captain():
    im = redye(src("cutlass_raider_unit.png"), ((26, 30, 58), (54, 74, 128), (196, 206, 226)), keep_rects=[(0, 0, 32, 19)])
    crown_band(im, 32, 0, 8, 8, 1)
    emblem(im, 26, 31, GOLD, 1)
    return im


def arch_engineer():
    im = redye(src("rifle_squad_unit.png"), ((70, 46, 20), (186, 128, 44), (246, 214, 140)), keep_rects=[(0, 0, 32, 19)])
    crown_band(im, 32, 0, 8, 8, 0)
    emblem(im, 26, 31, (255, 140, 40), 1)   # the furnace glow on the harness
    return im


def gravecaller():
    im = redye(src("shade_ranger_unit.png"), ((24, 12, 34), (70, 40, 96), (150, 220, 190)), keep_rects=[(0, 0, 32, 16)])
    for (x, y) in ((9, 12), (10, 12), (13, 12), (14, 12)):
        put(im, x, y, (110, 255, 170))   # lich eyes
    crown_band(im, 32, 0, 8, 8, 0)
    return im


def thornheart_druid():
    im = redye(src("thornbow_unit.png"), ((40, 30, 18), (70, 120, 46), (196, 230, 140)), keep_rects=[(0, 0, 32, 16)])
    crown_band(im, 32, 0, 8, 8, 0)
    # antler-leaf tips on the hat's front
    for i in (0, 2, 5, 7):
        put(im, 40 + i, 7, (140, 200, 80))
    return im


def horde_warchief():
    im = redye(src("bonewright_unit.png"), ((70, 18, 12), (150, 50, 30), (236, 190, 110)), keep_rects=[(0, 0, 36, 16)])
    top_rim(im, 0, 0, 10, 8, GOLD)
    for i in range(0, 10, 3):
        put(im, 8 + i, 8, GOLD_L)   # crown points along the front edge of the head's top
    return im


def storm_witch():
    return redye(src("windcaller_unit.png"), ((24, 30, 70), (70, 100, 190), (210, 230, 255)), keep_skin=True)


SKINS = {"dawn_paladin": dawn_paladin, "corsair_captain": corsair_captain, "arch_engineer": arch_engineer,
         "gravecaller": gravecaller, "thornheart_druid": thornheart_druid, "horde_warchief": horde_warchief,
         "storm_witch": storm_witch}

# ------------------------------------------------------------------ portraits (16x16 at 4x)

HERO_BG_T, HERO_BG_B = (58, 44, 24), (22, 16, 10)


def portrait(rows, pal):
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        t = y / 15
        c = tuple(int(HERO_BG_T[i] + (HERO_BG_B[i] - HERO_BG_T[i]) * t) for i in range(3))
        for x in range(16):
            im.putpixel((x, y), c + (255,))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in pal:
                im.putpixel((x, y), pal[ch] + (255,))
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            im.putpixel((x, y), GOLD + (255,))
    for i in range(1, 15):
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            if im.getpixel((x, y))[:3] == tuple(int(HERO_BG_T[k] + (HERO_BG_B[k] - HERO_BG_T[k]) * (y / 15)) for k in range(3)):
                im.putpixel((x, y), GOLD_D + (255,))
    return im.resize((64, 64), Image.NEAREST)


SKIN = (222, 176, 136)
PORTRAITS = {
    "dawn_paladin": (["................",
                      "......YYYY......",
                      ".....YWWWWY.....",
                      "....GGGGGGGG....",
                      "....GSSSSSSG....",
                      "....GSESSESG....",
                      "....GSSNNSSG....",
                      ".....SSSSSS.....",
                      "......SSSS......",
                      "...GGGGGGGGGG...",
                      "..GGGGGYYGGGGG..",
                      "..GGGGYWWYGGGG..",
                      "..GGGGGYYGGGGG..",
                      "..GGGGGGGGGGGG..",
                      "................",
                      "................"],
                     {"Y": GOLD_L, "W": (255, 250, 230), "G": GOLD, "S": SKIN, "E": (40, 60, 120), "N": (196, 150, 110)}),
    "gravecaller": (["................",
                     "....PPPPPPPP....",
                     "...PPPPPPPPPP...",
                     "...PPBBBBBBPP...",
                     "...PBBBBBBBBP...",
                     "...PBGGBBGGBP...",
                     "...PBBBBBBBBP...",
                     "...PPBBKKBBPP...",
                     "....PBTBTBTP....",
                     "...PPPPPPPPPP...",
                     "..PPPPPGGPPPPP..",
                     "..PPPPPPPPPPPP..",
                     "..PPPPPPPPPPPP..",
                     "..PPPPPPPPPPPP..",
                     "................",
                     "................"],
                    {"P": (64, 34, 88), "B": (214, 210, 196), "G": (110, 255, 170), "K": (40, 30, 40), "T": (150, 146, 136)}),
    "horde_warchief": (["................",
                        "....Y.Y..Y.Y....",
                        "....YYYYYYYY....",
                        "...PPPPPPPPPP...",
                        "...PPEPPPPEPP...",
                        "...PPPPPPPPPP...",
                        "...PPPNNNNPPP...",
                        "...PPPNKKNPPP...",
                        "...PWPPPPPPWP...",
                        "....PPPPPPPP....",
                        "..FFFFFFFFFFFF..",
                        "..FFRRRRRRRRFF..",
                        "..RRRRRYYRRRRR..",
                        "..RRRRRRRRRRRR..",
                        "................",
                        "................"],
                       {"Y": GOLD, "P": (222, 148, 140), "E": (30, 20, 20), "N": (240, 172, 164), "K": (120, 60, 60),
                        "W": (250, 240, 220), "F": (150, 110, 70), "R": (150, 44, 30)}),
    "thornheart_druid": (["................",
                          "...L........L...",
                          "...LL......LL...",
                          "....LLLLLLLL....",
                          "....HHHHHHHH....",
                          "....HSSSSSSH....",
                          "....HSESSESH....",
                          "....HSSSSSSH....",
                          ".....SSSSSS.....",
                          "...MMMMMMMMMM...",
                          "..MMBMMMMMMBMM..",
                          "..MMMMMLLMMMMM..",
                          "..BBMMMMMMMMBB..",
                          "..MMMMMMMMMMMM..",
                          "................",
                          "................"],
                         {"L": (140, 200, 80), "H": (70, 50, 32), "S": (226, 196, 160), "E": (40, 120, 60),
                          "M": (70, 120, 46), "B": (100, 70, 40)}),
    "corsair_captain": (["................",
                         "..KKKKKKKKKKKK..",
                         "...KKKYYYYKKK...",
                         "....KKKKKKKK....",
                         "....SSSSSSSS....",
                         "....SESSSSES....",
                         "....SSSNNSSS....",
                         "....SBBBBBBS....",
                         ".....BBBBBB.....",
                         "...NNNNNNNNNN...",
                         "..NNYNNWWNNYNN..",
                         "..NNNNNWWNNNNN..",
                         "..NNYNNNNNNYNN..",
                         "..NNNNNNNNNNNN..",
                         "................",
                         "................"],
                        {"K": (30, 26, 34), "Y": GOLD, "S": (214, 168, 128), "E": (30, 30, 30), "N": (50, 70, 124),
                         "B": (70, 44, 30), "W": (230, 230, 220)}),
    "storm_witch": (["......KK........",
                     ".....KKKK.......",
                     "....KKKKKK......",
                     "...KKKKKKKK.....",
                     "..KKKKKKKKKKK...",
                     "....SSSSSSSS....",
                     "....SCSSSSCS....",
                     "....SSSSSSSS....",
                     ".....SSSSSS.....",
                     "...BBBBBBBBBB...",
                     "..BBBBBYYBBBBB..",
                     "..BBBBYLLYBBBB..",
                     "..WWWWWWWWWWWW..",
                     ".WWWWWWWWWWWWWW.",
                     "................",
                     "................"],
                    {"K": (40, 46, 96), "S": (226, 200, 176), "C": (120, 200, 255), "B": (70, 100, 190), "Y": GOLD,
                     "L": (255, 255, 160), "W": (200, 214, 236)}),
    "arch_engineer": (["................",
                       "....TTTTTTTT....",
                       "...TTGGTTGGTT...",
                       "...DDDDDDDDDD...",
                       "....SSSSSSSS....",
                       "....SESSSSES....",
                       "....SSSNNSSS....",
                       "....RRRNNRRR....",
                       "....RRRRRRRR....",
                       "..YYYOOOOOOYYY..",
                       ".YYYOYOOOOYOYYY.",
                       ".OOOOOOFFOOOOOO.",
                       ".OOOOYOOOOYOOOO.",
                       ".KKKKKKKKKKKKKK.",
                       "................",
                       "................"],
                      {"T": (60, 50, 40), "G": (160, 220, 230), "D": GOLD_D, "S": (222, 170, 130), "E": (30, 30, 30),
                       "N": (200, 150, 110), "R": (150, 70, 30), "Y": GOLD, "O": (186, 128, 44), "F": (255, 140, 40),
                       "K": (42, 38, 34)}),
}

# ------------------------------------------------------------------ ability icons (96x96)

HERO_TINT = {"dawn_paladin": ((120, 84, 28), (230, 186, 80)), "gravecaller": ((30, 14, 40), (96, 54, 128)),
             "horde_warchief": ((70, 16, 10), (170, 60, 30)), "thornheart_druid": ((24, 46, 22), (76, 128, 50)),
             "corsair_captain": ((14, 24, 54), (40, 90, 150)), "storm_witch": ((20, 24, 70), (80, 110, 200)),
             "arch_engineer": ((60, 40, 18), (180, 120, 40))}
ABILITIES = {
    "dawn_paladin": ["shield_charge", "judgement", "sun_banner", "aegis_of_dawn"],
    "gravecaller": ["bone_spear", "grave_step", "raise_the_fallen", "grave_sight"],
    "horde_warchief": ["savage_leap", "frenzy", "cleave", "war_drums"],
    "thornheart_druid": ["thorn_volley", "root_walk", "rejuvenate", "grove_seed"],
    "corsair_captain": ["pistol_shot", "grapple_hook", "riptide", "tidepool"],
    "storm_witch": ["chain_lightning", "cloud_ride", "frost_nova", "mana_font"],
    "arch_engineer": ["rivet_burst", "rocket_jump", "deploy_turret", "field_repair"],
}
W_, L_, D_ = (250, 246, 230), (255, 220, 120), (30, 24, 20)


def glyph(d, name):
    """One symbol per ability, drawn on a 96x96 canvas."""
    if name == "shield_charge":
        d.polygon([(30, 20), (66, 20), (66, 52), (48, 76), (30, 52)], fill=L_, outline=D_)
        d.line([(10, 48), (26, 48)], fill=W_, width=5); d.line([(70, 48), (88, 48)], fill=W_, width=5)
    elif name == "judgement":
        d.rectangle([40, 6, 56, 80], fill=(255, 250, 200)); d.ellipse([28, 70, 68, 90], fill=L_)
    elif name == "sun_banner":
        d.line([(30, 10), (30, 88)], fill=D_, width=5); d.rectangle([32, 12, 74, 46], fill=L_)
        d.ellipse([44, 20, 62, 38], fill=W_)
    elif name == "aegis_of_dawn":
        d.ellipse([12, 12, 84, 84], outline=L_, width=6); d.polygon([(32, 26), (64, 26), (64, 52), (48, 70), (32, 52)], fill=W_)
    elif name == "bone_spear":
        d.line([(14, 82), (80, 16)], fill=W_, width=9); d.polygon([(80, 16), (62, 20), (76, 34)], fill=W_)
    elif name == "grave_step":
        d.ellipse([18, 60, 78, 84], fill=D_); d.arc([24, 10, 72, 70], 200, 340, fill=(140, 255, 190), width=6)
        d.polygon([(70, 26), (78, 40), (62, 38)], fill=(140, 255, 190))
    elif name == "raise_the_fallen":
        d.ellipse([28, 14, 68, 50], fill=W_); d.rectangle([36, 44, 60, 58], fill=W_)
        d.ellipse([34, 26, 44, 36], fill=D_); d.ellipse([52, 26, 62, 36], fill=D_)
        d.line([(16, 84), (32, 66)], fill=W_, width=5); d.line([(80, 84), (64, 66)], fill=W_, width=5)
    elif name == "grave_sight":
        d.ellipse([10, 30, 86, 66], fill=W_); d.ellipse([34, 30, 62, 66], fill=(110, 255, 170)); d.ellipse([42, 40, 54, 56], fill=D_)
    elif name == "savage_leap":
        d.arc([10, 20, 86, 100], 190, 350, fill=W_, width=7); d.polygon([(82, 52), (88, 70), (70, 64)], fill=W_)
        d.line([(30, 84), (66, 84)], fill=L_, width=4)
    elif name == "frenzy":
        for x in (24, 44, 64):
            d.polygon([(x, 20), (x + 12, 20), (x + 6, 60)], fill=W_)
        d.ellipse([20, 64, 76, 84], fill=(220, 40, 30))
    elif name == "cleave":
        d.pieslice([10, 10, 86, 86], 200, 340, fill=W_); d.pieslice([24, 24, 72, 72], 200, 340, fill=(140, 50, 30))
    elif name == "war_drums":
        d.ellipse([20, 22, 76, 38], fill=W_); d.rectangle([20, 30, 76, 72], fill=(150, 90, 50)); d.ellipse([20, 64, 76, 80], fill=(110, 64, 36))
        d.line([(16, 10), (40, 30)], fill=W_, width=4); d.line([(80, 10), (56, 30)], fill=W_, width=4)
    elif name == "thorn_volley":
        for i in range(5):
            x = 14 + i * 16
            d.polygon([(x, 84), (x + 8, 84), (x + 4, 24 + (i % 2) * 14)], fill=(150, 210, 90))
    elif name == "root_walk":
        d.ellipse([30, 8, 66, 30], fill=(196, 230, 140))
        for x, s in ((20, -1), (48, 0), (76, 1)):
            d.line([(48, 30), (x, 86)], fill=(110, 76, 44), width=6)
    elif name == "rejuvenate":
        d.polygon([(48, 82), (14, 44), (28, 20), (48, 34), (68, 20), (82, 44)], fill=(255, 120, 140))
        d.line([(48, 40), (48, 64)], fill=W_, width=5); d.line([(36, 52), (60, 52)], fill=W_, width=5)
    elif name == "grove_seed":
        d.line([(48, 86), (48, 40)], fill=(110, 76, 44), width=6); d.ellipse([22, 14, 58, 46], fill=(120, 190, 70))
        d.ellipse([42, 22, 76, 52], fill=(90, 160, 56))
    elif name == "pistol_shot":
        d.rectangle([14, 36, 70, 48], fill=(70, 50, 34)); d.rectangle([20, 48, 34, 74], fill=(110, 74, 44))
        d.ellipse([72, 30, 90, 52], fill=L_)
    elif name == "grapple_hook":
        d.line([(16, 84), (60, 30)], fill=W_, width=4); d.arc([50, 12, 86, 48], 90, 360, fill=(190, 190, 200), width=7)
    elif name == "riptide":
        for k in range(3):
            d.arc([6 + k * 8, 26 + k * 12, 90 - k * 8, 96 + k * 12], 200, 340, fill=(170, 220, 255), width=6)
    elif name == "tidepool":
        d.ellipse([12, 40, 84, 80], fill=(60, 140, 200)); d.ellipse([28, 48, 68, 72], outline=(200, 240, 255), width=4)
        d.polygon([(48, 8), (36, 34), (60, 34)], fill=(170, 220, 255))
    elif name == "chain_lightning":
        d.line([(16, 14), (44, 44), (32, 50), (64, 84)], fill=(255, 255, 170), width=7)
        d.line([(44, 44), (80, 30)], fill=(200, 230, 255), width=4)
    elif name == "cloud_ride":
        for (x, y, r) in ((30, 52, 18), (50, 44, 22), (70, 54, 16)):
            d.ellipse([x - r, y - r, x + r, y + r], fill=W_)
        d.line([(40, 78), (34, 90)], fill=(255, 255, 170), width=4)
    elif name == "frost_nova":
        for a in range(0, 180, 30):
            dx, dy = 38 * math.cos(math.radians(a)), 38 * math.sin(math.radians(a))
            d.line([(48 - dx, 48 - dy), (48 + dx, 48 + dy)], fill=(210, 240, 255), width=5)
        d.ellipse([40, 40, 56, 56], fill=W_)
    elif name == "mana_font":
        d.polygon([(48, 8), (70, 50), (48, 88), (26, 50)], fill=(120, 200, 255)); d.polygon([(48, 26), (58, 50), (48, 70), (38, 50)], fill=W_)
    elif name == "rivet_burst":
        for (x, y) in ((30, 30), (60, 40), (40, 64)):
            d.ellipse([x - 9, y - 9, x + 9, y + 9], fill=(200, 200, 210), outline=D_)
        d.line([(10, 86), (26, 70)], fill=L_, width=4)
    elif name == "rocket_jump":
        d.polygon([(48, 8), (62, 30), (62, 62), (34, 62), (34, 30)], fill=(200, 200, 210))
        d.polygon([(38, 62), (58, 62), (48, 90)], fill=(255, 140, 40))
    elif name == "deploy_turret":
        d.rectangle([30, 50, 66, 78], fill=(186, 128, 44)); d.ellipse([34, 34, 62, 60], fill=(150, 100, 34))
        d.rectangle([56, 42, 88, 50], fill=(70, 60, 50)); d.line([(30, 86), (66, 86)], fill=D_, width=5)
    elif name == "field_repair":
        d.line([(22, 74), (62, 34)], fill=(200, 200, 210), width=10); d.arc([52, 14, 84, 46], 120, 420, fill=(200, 200, 210), width=8)
        d.line([(66, 66), (82, 82)], fill=(255, 220, 90), width=4)


def ability_icon(hero, name):
    t, b = HERO_TINT[hero]
    im = Image.new("RGB", (96, 96))
    px = im.load()
    for y in range(96):
        for x in range(96):
            u = (y + x * 0.3) / 124
            px[x, y] = tuple(int(b[i] + (t[i] - b[i]) * u) for i in range(3))
    d = ImageDraw.Draw(im)
    glyph(d, name)
    d.rectangle([0, 0, 95, 95], outline=GOLD, width=3)
    return im


def main():
    for name, fn in SKINS.items():
        if name == "dawn_paladin":
            continue   # painted at 8x by tools/hd_villager.py now (its portrait too)
        im = fn()
        im.save(os.path.join(ENT, f"{name}_unit.png"))
        print(f"textures/entities/{name}_unit.png {im.size}")
    for name, (rows, pal) in PORTRAITS.items():
        if name == "dawn_paladin":
            continue
        portrait(rows, pal).save(os.path.join(HEADS, f"{name}.png"))
        print(f"textures/mobheads/{name}.png")
    for hero, names in ABILITIES.items():
        for n in names:
            ability_icon(hero, n).save(os.path.join(ICONS, f"hero_{n}.png"))
            print(f"textures/icons/abilities/hero_{n}.png")


if __name__ == "__main__":
    main()
