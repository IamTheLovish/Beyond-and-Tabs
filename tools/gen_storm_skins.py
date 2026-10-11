#!/usr/bin/env python3
"""Draws the Stormcallers' unit skins and portrait icons (original pixel art).

Reuses the skin toolkit of tools/gen_verdant_skins.py (box layouts, Skin, faces, robe/arms/legs helpers, Icon), as
tools/gen_tide_skins.py does, so the factions are painted the same way; only the palette and the units differ. Every
skin keeps the UV layout of the model its renderer uses:
  - VillagerUnitModel (64x64): Geomancer (and the Thunder Sage), Blade Dancer, Fire Adept, Jade Guard, Wind Monk, and
    (slice 2) Frost Adept, Rocket Battery. Its head's 8x12x8 "hat" box (32,0) is drawn wherever it is opaque: hats, a
    topknot, a helmet crest.
  - The vanilla witch model (64x128, WitchRenderer): the Ink Scribe (slice 2) - the pointed hat recut as a scholar's cap.
  - Alex's Mobs' shoebill model (RenderShoebill): the Paper Crane. Only the OUTLINE of AM's texture is read (which
    pixels its UV islands cover, so the paint lands on the model); every colour is ours - folded white paper with
    crease lines and red seal stamps. AM's own pixels are never copied. If the AM texture can't be found (no jar
    around), the existing paper_crane_unit.png is left alone.

Art direction (claude/design-factions.md, the Stormcallers): a mage dynasty in red lacquer, jade and gold - deep red
robes, gold trim and diadems, jade sashes and plates, paper white. Like the other factions, the top faces (hats,
shoulders) carry the strongest shapes because the RTS camera looks down, with strong value steps and light noise.

usage: python3 tools/gen_storm_skins.py [--preview DIR] [--am-texture shoebill.png]
  writes bar/.../textures/entities/<unit>_unit.png and bar/.../textures/mobheads/<unit>.png (16x16 art at 4x);
  --am-texture points at Alex's Mobs' textures/entity/shoebill.png (default: read from an alexsmobs jar if one is
  found under ~/.gradle or /mnt); --preview writes a contact sheet into DIR.
"""
import glob, importlib.util, io, os, random, sys, zipfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("gen_verdant_skins", os.path.join(HERE, "gen_verdant_skins.py"))
V = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(V)

Skin, VILLAGER, WITCH, SIDES, Icon = V.Skin, V.VILLAGER, V.WITCH, V.SIDES, V.Icon
sh, villager_face, robe, arms, legs, shoulder_tops = V.sh, V.villager_face, V.robe, V.arms, V.legs, V.shoulder_tops
ENT, HEADS = V.ENT, V.HEADS

# ------------------------------------------------------------------ palette
RED_D, RED, RED_L = (96, 18, 16), (168, 35, 28), (214, 70, 52)
GOLD_D, GOLD, GOLD_L = (140, 100, 24), (212, 175, 55), (246, 222, 120)
JADE_D, JADE, JADE_L = (24, 104, 74), (46, 150, 104), (110, 214, 168)
SAFFRON, SAFFRON_D = (230, 150, 40), (176, 100, 24)
FLAME, FLAME_L = (236, 104, 36), (255, 190, 80)
PAPER, PAPER_D = (242, 238, 226), (204, 198, 182)
INK, BLACK = (40, 30, 30), (24, 22, 24)
SKIN_IVORY, SKIN_WARM, SKIN_TAN = (236, 206, 172), (214, 170, 128), (186, 136, 96)
ICE_D, ICE, ICE_L = (70, 120, 160), (120, 180, 220), (190, 230, 248)
SILVER, SILVER_L = (176, 184, 196), (224, 230, 238)
SOOT, SOOT_L = (56, 52, 52), (96, 90, 88)


def topknot(s, hair, pin):
    """Hair on the crown of the hat box gathered into a knot (from above: a dark disc with a gold pin)."""
    for i in range(2, 6):
        for j in range(2, 6):
            s.fpx("hat", "top", i, j, s.n(hair, 4))
    s.fpx("hat", "top", 3, 3, pin); s.fpx("hat", "top", 4, 4, pin)
    for f in SIDES:
        for i in range(2, 6):
            s.fpx("hat", f, i, 0, hair)


def diadem(s, band, gem):
    """A thin gold band round the brow on the hat box with a jewel in front."""
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 2, band)
    s.fpx("hat", "front", 3, 2, gem); s.fpx("hat", "front", 4, 2, gem)


# ------------------------------------------------------------------ the units (villager model)
def geomancer():
    """The worker (and the Thunder Sage, who adds the livery's gold-trimmed cap and robe): a deep red robe with a jade
    sash and gold hem, an earth-brown underrobe showing at the sleeves, a scholar's black cap with a jade bead (top)."""
    s = Skin(VILLAGER, (64, 64), 61)
    villager_face(s, SKIN_WARM, (60, 50, 40), INK, hair=BLACK, beard=(70, 64, 60))
    robe(s, RED, RED_D, trim=GOLD, sash=9, sash_c=JADE)
    s.band("jacket", 17, 18, GOLD_D)
    for (i, j) in ((2, 4), (5, 4), (2, 12), (5, 12)):
        s.fpx("jacket", "front", i, j, GOLD_L)       # embroidered clouds
    arms(s, RED, GOLD, SKIN_WARM, bracer=(110, 82, 56))
    legs(s, RED_D, BLACK)
    shoulder_tops(s, RED_L, accent=GOLD)
    # the scholar's cap: black crown, a jade bead on top, two short flaps at the back
    for i in range(1, 7):
        for j in range(1, 7):
            s.fpx("hat", "top", i, j, s.n(BLACK, 4))
    s.fpx("hat", "top", 3, 3, JADE_L); s.fpx("hat", "top", 4, 3, JADE)
    for f in SIDES:
        for i in range(1, 7):
            s.fpx("hat", f, i, 0, BLACK)
            s.fpx("hat", f, i, 1, sh(BLACK, 1.4))
    for j in range(2, 5):
        s.fpx("hat", "back", 1, j, BLACK); s.fpx("hat", "back", 6, j, BLACK)
    return s


def blade_dancer():
    """The raider: red silk with a gold sash tied at the hip, bare forearms with gold bands, a high topknot with a gold
    pin and a red ribbon trailing down the back - from above, a dark knot over red shoulders."""
    s = Skin(VILLAGER, (64, 64), 62)
    villager_face(s, SKIN_IVORY, (40, 30, 30), INK, hair=BLACK)
    s.fill("body", RED, 5)
    s.fill("jacket", RED_L, 5)
    s.band("jacket", 0, 8, RED)
    # the crossed silk front panels and the sash with its knot
    for j in range(8):
        s.fpx("jacket", "front", min(7, j), j, RED_D)
        s.fpx("jacket", "front", max(0, 7 - j), j, RED_D)
    s.band("jacket", 8, 10, GOLD)
    s.band("body", 8, 10, GOLD)
    s.fpx("jacket", "left", 1, 10, GOLD_D); s.fpx("jacket", "left", 1, 11, GOLD_D); s.fpx("jacket", "left", 2, 12, GOLD_D)
    s.band("jacket", 10, 18, RED_D)
    for f in SIDES:   # the skirt's slit panels
        x, y, w, h = s.face("jacket", f)
        for j in range(10, 18):
            s.fpx("jacket", f, 0, j, RED)
    s.vshade("jacket", 1.06, 0.8)
    arms(s, RED, GOLD, SKIN_IVORY, bracer=GOLD_D)
    legs(s, BLACK, RED_D)
    shoulder_tops(s, RED_L, accent=GOLD_L)
    topknot(s, BLACK, GOLD_L)
    for j in range(1, 6):
        s.fpx("hat", "back", 3, j, RED); s.fpx("hat", "back", 4, j, RED_L)   # the ribbon
    return s


def fire_adept():
    """The skirmisher: a flame-orange robe fading to red at the hem with gold flame embroidery, a gold diadem with a
    ruby, red sleeves - from above, an orange body under a gold circlet."""
    s = Skin(VILLAGER, (64, 64), 63)
    villager_face(s, SKIN_TAN, (200, 90, 30), INK, hair=(60, 26, 20))
    robe(s, FLAME, RED, trim=GOLD)
    s.band("jacket", 12, 15, RED_L)
    for f in SIDES:   # flames licking up from the hem
        x, y, w, h = s.face("jacket", f)
        for i in range(w):
            for j in range(12 - (i * 3) % 4, 15):
                s.fpx("jacket", f, i, j, s.n(RED_L if (i + j) % 2 else FLAME_L, 4))
    s.vshade("jacket", 1.08, 0.82)
    arms(s, RED, GOLD, SKIN_TAN, bracer=RED_D)
    legs(s, RED_D, BLACK)
    shoulder_tops(s, FLAME_L, accent=GOLD)
    diadem(s, GOLD, (220, 30, 40))
    for i in range(2, 6):
        for j in range(2, 6):
            s.fpx("hat", "top", i, j, s.n((60, 26, 20), 4))   # the hair's crown inside the circlet
    return s


def jade_guard():
    """The line: red-lacquered lamellar (rows of plates with gold rivets) over a black tunic, jade shoulder plates, and
    a red helmet with a jade crest front to back - from above, a red dome split by a green ridge."""
    s = Skin(VILLAGER, (64, 64), 64)
    villager_face(s, SKIN_WARM, (50, 40, 30), INK, hair=BLACK, beard=BLACK)
    s.fill("body", BLACK, 4)
    s.fill("jacket", RED, 4)
    for f in SIDES:   # lamellar rows: a darker line under every second row, rivets
        x, y, w, h = s.face("jacket", f)
        for j in range(1, 14, 2):
            for i in range(w):
                s.fpx("jacket", f, i, j, RED_D)
            for i in range(0, w, 3):
                s.fpx("jacket", f, i, j - 1, GOLD)
    s.band("jacket", 14, 15, GOLD)
    s.band("jacket", 15, 18, BLACK)
    s.vshade("jacket", 1.08, 0.8)
    arms(s, RED, JADE, SKIN_WARM, bracer=JADE_D)
    legs(s, BLACK, RED_D)
    shoulder_tops(s, JADE_L, accent=GOLD)
    for part in ("arm_up", "arm"):
        s.band(part, 0, 3, JADE)
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(RED, 5))
    for j in range(8):
        s.fpx("hat", "top", 3, j, JADE); s.fpx("hat", "top", 4, j, JADE_L if j % 2 else JADE)
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 0, RED_L)
            s.fpx("hat", f, i, 1, RED)
            s.fpx("hat", f, i, 2, GOLD_D)
    s.fpx("hat", "front", 3, 0, JADE); s.fpx("hat", "front", 4, 0, JADE)
    return s


def wind_monk():
    """Anti-air: a saffron robe with a jade sash over one shoulder, a shaved head (no hair: the skin is the crown, the
    lightest top in the faction so monks are found at a glance) with a jade dot, prayer beads at the neck."""
    s = Skin(VILLAGER, (64, 64), 65)
    villager_face(s, SKIN_TAN, (60, 50, 40), (110, 80, 60))
    robe(s, SAFFRON, SAFFRON_D)
    for j in range(12):   # the jade sash across the chest from the left shoulder
        i = min(7, j)
        s.fpx("jacket", "front", i, j, JADE)
        if i + 1 < 8:
            s.fpx("jacket", "front", i + 1, j, JADE_D)
    for i in range(1, 7):
        s.fpx("jacket", "front", i, 0, (110, 70, 40) if i % 2 else (70, 40, 26))   # beads
    arms(s, SAFFRON, SAFFRON_D, SKIN_TAN)
    legs(s, SAFFRON_D, (110, 82, 56))
    shoulder_tops(s, (246, 186, 80), accent=JADE)
    s.fpx("head", "top", 3, 3, JADE_L); s.fpx("head", "top", 4, 4, JADE_L)   # the forehead mark seen from above
    return s


# ------------------------------------------------------------------ the Paper Crane (AM shoebill UV outline)
def frost_adept():
    """Slice 2, the slow skirmisher: the Fire Adept's cut in ice - a pale blue robe fading to deep blue at the hem with
    red lacquer trim (still the dynasty), a silver diadem with a jade stone, frost patterns at the hem; from above, a
    pale body under a silver circlet, the opposite value of the Fire Adept so the two read apart at a glance."""
    s = Skin(VILLAGER, (64, 64), 66)
    villager_face(s, SKIN_IVORY, (90, 140, 190), INK, hair=(230, 234, 240))
    robe(s, ICE, ICE_D, trim=RED)
    s.band("jacket", 12, 15, ICE_D)
    for f in SIDES:   # frost crystals creeping up from the hem
        x, y, w, h = s.face("jacket", f)
        for i in range(w):
            for j in range(12 - (i * 3) % 4, 15):
                s.fpx("jacket", f, i, j, s.n(ICE_L if (i + j) % 2 else SILVER_L, 4))
    s.vshade("jacket", 1.08, 0.82)
    arms(s, ICE, RED, SKIN_IVORY, bracer=ICE_D)
    legs(s, ICE_D, BLACK)
    shoulder_tops(s, ICE_L, accent=RED)
    diadem(s, SILVER, JADE_L)
    for i in range(2, 6):
        for j in range(2, 6):
            s.fpx("hat", "top", i, j, s.n((230, 234, 240), 4))   # white hair inside the circlet
    return s


def rocket_battery():
    """Slice 2, the artillery: a soot-dark crew coat with red lacquer facings and a gold powder belt of little rockets
    across the chest, leather bracers, a red crew cap with a gold badge; the back carries the rocket rack - a row of red
    tubes with gold caps, the strongest shape from above and behind."""
    s = Skin(VILLAGER, (64, 64), 67)
    villager_face(s, SKIN_WARM, (60, 50, 40), INK, hair=BLACK)
    for (i, j) in ((5, 6), (6, 7), (1, 7)):
        s.fpx("head", "front", i, j, sh(SKIN_WARM, 0.7))   # soot smudges
    robe(s, SOOT, BLACK, trim=RED)
    s.band("jacket", 0, 1, RED)
    # the rocket belt: a bandolier of red tubes with gold caps across the chest
    for j in range(9):
        i = 7 - min(7, j)
        s.fpx("jacket", "front", i, j, RED_D)
        if j % 3 == 1:
            s.fpx("jacket", "front", i, j, GOLD)
    s.band("jacket", 9, 10, GOLD_D)
    # the rack on the back: four red tubes with gold caps standing up from the shoulders
    for i in (0, 2, 4, 6):
        for j in range(0, 10):
            s.fpx("jacket", "back", i, j, RED if j else GOLD_L)
            s.fpx("jacket", "back", i + 1, j, RED_D if j else GOLD)
    s.vshade("jacket", 1.06, 0.82)
    arms(s, SOOT, RED, SKIN_WARM, bracer=(110, 82, 56))
    legs(s, BLACK, SOOT_L)
    shoulder_tops(s, RED_L, accent=GOLD)
    for i in (1, 3, 5):   # the tube caps seen from above, behind the shoulders
        s.fpx("jacket", "top", i, 0, GOLD_L); s.fpx("jacket", "top", i + 1, 0, GOLD)
    # the cap: a red crown with a gold badge in front
    for i in range(1, 7):
        for j in range(1, 7):
            s.fpx("hat", "top", i, j, s.n(RED, 5))
    for f in SIDES:
        for i in range(1, 7):
            s.fpx("hat", f, i, 0, RED)
            s.fpx("hat", f, i, 1, RED_D)
    s.fpx("hat", "front", 3, 1, GOLD_L); s.fpx("hat", "front", 4, 1, GOLD)
    return s


# ------------------------------------------------------------------ witch model (64x128)
def ink_scribe():
    """Slice 2, the support: a court calligrapher - a paper-white robe with ink-black trim and a red seal mark on the
    chest, a black silk sash, ink stains at the sleeves; the witch hat recut as a scholar's black cap: a low flat brim,
    a short black crown with a gold bead at the tip (from above: a black square with a gold dot on white shoulders)."""
    s = Skin(WITCH, (64, 128), 68)
    villager_face(s, SKIN_IVORY, (40, 30, 30), INK, hair=BLACK)
    s.fill("mole", INK, 0)
    robe(s, PAPER, PAPER_D, trim=INK)
    s.band("jacket", 8, 10, BLACK)
    s.fpx("jacket", "front", 3, 9, RED); s.fpx("jacket", "front", 4, 9, RED)
    # the seal stamp on the chest and a vertical brush line of script
    for (i, j) in ((2, 3), (3, 3), (2, 4), (3, 4)):
        s.fpx("jacket", "front", i, j, RED)
    for j in range(2, 8):
        s.fpx("jacket", "front", 6, j, INK if j % 2 else sh(INK, 1.6))
    s.band("jacket", 18, 20, PAPER_D)
    arms(s, PAPER, INK, SKIN_IVORY, bracer=INK)
    legs(s, BLACK, PAPER_D)
    shoulder_tops(s, (250, 248, 240), accent=INK)
    # the cap: a black brim ring, a black crown, no cone (the upper cone boxes stay black and thin), a gold tip
    s.fill("hat1", BLACK, 3)
    x, y, w, h = s.face("hat1", "top")
    for i in range(w):
        for j in range(h):
            if i in (0, w - 1) or j in (0, h - 1):
                s.put(x + i, y + j, sh(BLACK, 1.5))
    s.fill("hat2", BLACK, 3)
    s.band("hat2", 3, 4, GOLD_D)
    s.fill("hat3", BLACK, 3)
    s.fill("hat4", GOLD_L, 0)
    return s


def _find_am_texture(cli):
    if cli and os.path.exists(cli):
        return Image.open(cli).convert("RGBA")
    pats = [os.path.expanduser("~/.gradle/**/alexsmobs*.jar"), "/mnt/**/alexsmobs*.jar"]
    for pat in pats:
        for jar in glob.glob(pat, recursive=True):
            try:
                with zipfile.ZipFile(jar) as z:
                    return Image.open(io.BytesIO(z.read("assets/alexsmobs/textures/entity/shoebill.png"))).convert("RGBA")
            except (KeyError, zipfile.BadZipFile, OSError):
                continue
    return None


def paper_crane(am):
    """Folded paper on the shoebill's UV islands: warm white with faint crease lines every few pixels, darker paper on
    the edges of each island (the folds), and a few red seal stamps. Only AM's alpha (where its islands are) is read."""
    w, h = am.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    src, px = am.load(), out.load()
    rng = random.Random(66)
    for y in range(h):
        for x in range(w):
            if src[x, y][3] < 128:
                continue
            edge = any(not (0 <= x + dx < w and 0 <= y + dy < h) or src[x + dx, y + dy][3] < 128
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            c = PAPER_D if edge else PAPER
            if (x + y) % 7 == 0 or (x - y) % 11 == 0:
                c = sh(c, 0.93)   # creases
            k = rng.randint(-4, 4)
            px[x, y] = tuple(max(0, min(255, v + k)) for v in c) + (255,)
    # seal stamps: 2x2 red squares on a few interior pixels, spread over the sheet
    stamps = 0
    for (fx, fy) in ((0.3, 0.3), (0.7, 0.25), (0.5, 0.6), (0.2, 0.75), (0.8, 0.7), (0.45, 0.15)):
        x, y = int(w * fx), int(h * fy)
        if all(0 <= x + i < w and 0 <= y + j < h and src[x + i, y + j][3] >= 128 for i in (0, 1) for j in (0, 1)):
            for i in (0, 1):
                for j in (0, 1):
                    px[x + i, y + j] = RED + (255,)
            stamps += 1
    return out


UNITS = {
    "geomancer": (geomancer, "villager"),
    "blade_dancer": (blade_dancer, "villager"),
    "fire_adept": (fire_adept, "villager"),
    "jade_guard": (jade_guard, "villager"),
    "wind_monk": (wind_monk, "villager"),
    # slice 2
    "frost_adept": (frost_adept, "villager"),
    "rocket_battery": (rocket_battery, "villager"),
    "ink_scribe": (ink_scribe, "witch"),
}

# ------------------------------------------------------------------ portraits (16x16 art, saved at 4x = 64x64)
BG_T, BG_B = (96, 30, 30), (30, 10, 12)   # one red-lacquer dusk backdrop for the whole faction
FRAME = (212, 175, 55)                     # a gold frame
ICONS = {}


def icon(fn):
    ICONS[fn.__name__.replace("icon_", "")] = fn
    return fn


W_ = (236, 236, 228)


@icon
def icon_geomancer():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "......JJ........",
           ".....KKKKKK.....",
           "....KKKKKKKK....",
           "....HSSSSSSH....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SBSNNSBS....",
           ".....BBBBBB.....",
           "..RRRRRRRRRRRR..",
           ".RRRRRYYRRRRRRR.",
           ".RRRRRYYRRRRRRR.",
           ".GGGGGGGGGGGGGG.",
           ".RRRRRYYRRRRRRR.",
           ".RRRRRYYRRRRRRR.",
           "................"],
          {"J": JADE_L, "K": BLACK, "H": BLACK, "S": SKIN_WARM, "W": W_, "E": (60, 50, 40), "N": sh(SKIN_WARM, 0.88),
           "B": (70, 64, 60), "R": RED, "Y": GOLD, "G": JADE})
    i.frame(FRAME)
    return i


@icon
def icon_paper_crane():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           ".......PP.......",
           "......PPPR......",
           ".......PPPP.....",
           "........PPDD....",
           "PP......PP......",
           ".PPP...PPPP...PP",
           "..PPPPPPPPPPPPP.",
           "...DPPPPPPPPPD..",
           ".....PPPRPPP....",
           ".......PPP......",
           "........P.......",
           "................",
           "................",
           "................"],
          {"P": PAPER, "D": PAPER_D, "R": RED})
    i.frame(FRAME)
    return i


@icon
def icon_blade_dancer():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".......KK.......",
           "......KYYK..RR..",
           ".....KKKKKKRR...",
           "....KSSSSSSK....",
           "....SWESSEWS....",
           "....SSSNNSSS..C.",
           ".....SSNNSS..C..",
           ".C...SSSSSS.C...",
           "..C.RRRRRRRRR...",
           "...CRDRRRRDRRR..",
           ".RRRRRDRRDRRRR..",
           ".RRRRRRDDRRRRR..",
           ".YYYYYYYYYYYYY..",
           ".DDDDDDDDDDDDD..",
           "................"],
          {"K": BLACK, "Y": GOLD, "S": SKIN_IVORY, "W": W_, "E": (40, 30, 30), "N": sh(SKIN_IVORY, 0.88),
           "R": RED, "D": RED_D, "C": (230, 220, 160)})
    i.frame(FRAME)
    return i


@icon
def icon_fire_adept():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           "......L.........",
           "....YYYRYYY.....",
           "....HSSSSSSH..F.",
           "....SWESSEWS.FLF",
           "....SSSNNSSS.FLF",
           "....SSSNNSSS..B.",
           ".....SSSSSS...B.",
           "..OOOOOOOOOOOOB.",
           ".OOOOOYYOOOOOOB.",
           ".OOOOOYYOOOOOO..",
           ".OFOOFYYOFOOFO..",
           ".FRFFRYYFRFFRF..",
           ".RRRRRYYRRRRRR..",
           "................"],
          {"L": FLAME_L, "Y": GOLD, "R": RED, "H": (60, 26, 20), "S": SKIN_TAN, "W": W_, "E": (200, 90, 30),
           "N": sh(SKIN_TAN, 0.88), "O": FLAME, "F": FLAME_L, "B": GOLD_D})
    i.frame(FRAME)
    return i


@icon
def icon_jade_guard():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".......JJ.......",
           ".....RRJJRR.....",
           "....RRRRRRRR....",
           "...YYYYYYYYYY...",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SKSNNSKS..GG",
           ".....KKKKKK..GJJG",
           "..JJRRRRRRRRJGJJG",
           ".JJRYRRYRRYRRGJJG",
           ".RDDDDDDDDDDDGJJG",
           ".RRYRRYRRYRRRGGGG",
           ".DDDDDDDDDDDDD..",
           "................"],
          {"J": JADE, "R": RED, "Y": GOLD, "S": SKIN_WARM, "W": W_, "E": (50, 40, 30), "N": sh(SKIN_WARM, 0.88),
           "K": BLACK, "D": RED_D, "G": JADE_D})
    i.frame(FRAME)
    return i


@icon
def icon_wind_monk():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "..C.............",
           ".C.C....SS......",
           "C...C.SSSSSS....",
           ".....SSSJSSSS...",
           ".....SWESSEWS...",
           "..C..SSSNNSSS...",
           ".C.C.SSSNNSSS...",
           "C....BBBBBBBB...",
           "...OOOOOOOOOOOO.",
           "..OJJOOOOOOOOOO.",
           "..OOJJOOOOOOOOO.",
           "..OOOOJJOOOOOOO.",
           "..OOOOOOJJOOOOO.",
           "..DDDDDDDDJJDDD.",
           "................"],
          {"C": (220, 230, 236), "S": SKIN_TAN, "J": JADE, "W": W_, "E": (60, 50, 40), "N": sh(SKIN_TAN, 0.88),
           "B": (90, 56, 34), "O": SAFFRON, "D": SAFFRON_D})
    i.frame(FRAME)
    return i


@icon
def icon_frost_adept():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           "......L.........",
           "....VVVJVVV.....",
           "....HSSSSSSH..C.",
           "....SWESSEWS.CLC",
           "....SSSNNSSS.CLC",
           "....SSSNNSSS..B.",
           ".....SSSSSS...B.",
           "..IIIIIIIIIIIIB.",
           ".IIIIIRRIIIIIIB.",
           ".IIIIIRRIIIIII..",
           ".ILIILRRILIILI..",
           ".LDLLDRRLDLLDL..",
           ".DDDDDRRDDDDDD..",
           "................"],
          {"L": ICE_L, "V": SILVER, "J": JADE_L, "R": RED, "H": (230, 234, 240), "S": SKIN_IVORY, "W": W_,
           "E": (90, 140, 190), "N": sh(SKIN_IVORY, 0.88), "I": ICE, "C": SILVER_L, "B": ICE_D, "D": ICE_D})
    i.frame(FRAME)
    return i


@icon
def icon_rocket_battery():
    i = Icon(BG_T, BG_B)
    i.art(["....Y.Y.Y.Y.....",
           "....R.R.R.R.....",
           "....R.R.R.R.....",
           ".....RRRRRR.....",
           "....RRRYRRRR....",
           "....HSSSSSSH....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SKSNNSKS....",
           ".....KKKKKK.....",
           "..OOOORRRROOOO..",
           ".OOOORYOYROOOOO.",
           ".OOORYOOOYROOOO.",
           ".OORYOOOOOYROOO.",
           ".GGGGGGGGGGGGGG.",
           ".KKKKKKKKKKKKKK."],
          {"Y": GOLD_L, "R": RED, "S": SKIN_WARM, "W": W_, "E": (60, 50, 40), "N": sh(SKIN_WARM, 0.88), "H": BLACK,
           "K": BLACK, "O": SOOT, "G": GOLD_D})
    i.frame(FRAME)
    return i


@icon
def icon_ink_scribe():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".......G........",
           ".....KKKKKK.....",
           "....KKKKKKKK....",
           "...KKKKKKKKKK...",
           "....HSSSSSSH....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SSSNNSSS..I.",
           ".....SSSSSS...I.",
           "..PPPPPPPPPPPPI.",
           ".PPRRPPPPPPIPPI.",
           ".PPRRPPPPPPIPPP.",
           ".PKKKKKKKKKKKPP.",
           ".PPPPPPPPPPIPPP.",
           ".DDDDDDDDDDDDDD."],
          {"G": GOLD_L, "K": BLACK, "H": BLACK, "S": SKIN_IVORY, "W": W_, "E": (40, 30, 30), "N": sh(SKIN_IVORY, 0.88),
           "P": PAPER, "R": RED, "I": INK, "D": PAPER_D})
    i.frame(FRAME)
    return i


def preview(outdir, skins, icons, crane):
    os.makedirs(outdir, exist_ok=True)
    SC = 6
    tiles = []
    for name, (s, kind) in skins.items():
        fm = V.front_mock(s, kind).resize((20 * SC, 36 * SC), Image.NEAREST)
        bm = V.front_mock(s, kind, back=True).resize((20 * SC, 36 * SC), Image.NEAREST)
        tiles.append((fm, bm))
    sheet = Image.new("RGBA", (max(len(tiles) * (40 * SC + 30), 800), 36 * SC + 260), (90, 110, 120, 255))
    for k, (fm, bm) in enumerate(tiles):
        sheet.alpha_composite(fm, (k * (40 * SC + 30) + 4, 4))
        sheet.alpha_composite(bm, (k * (40 * SC + 30) + 20 * SC + 12, 4))
    for k, n in enumerate(icons):
        sheet.alpha_composite(icons[n].im.resize((64, 64), Image.NEAREST), (k * 72 + 4, 36 * SC + 20))
    if crane is not None:
        sheet.alpha_composite(crane.resize((crane.width * 2, crane.height * 2), Image.NEAREST), (4, 36 * SC + 100))
    sheet.convert("RGB").save(os.path.join(outdir, "storm_skins_sheet.png"))


def main():
    out_prev = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    am_path = sys.argv[sys.argv.index("--am-texture") + 1] if "--am-texture" in sys.argv else None
    skins = {}
    for name, (fn, kind) in UNITS.items():
        s = fn()
        s.im.save(os.path.join(ENT, f"{name}_unit.png"))
        skins[name] = (s, kind)
        print(f"textures/entities/{name}_unit.png {s.im.size}")
    am = _find_am_texture(am_path)
    crane = None
    if am is not None:
        crane = paper_crane(am)
        crane.save(os.path.join(ENT, "paper_crane_unit.png"))
        print(f"textures/entities/paper_crane_unit.png {crane.size}")
    else:
        print("Alex's Mobs shoebill texture not found: paper_crane_unit.png left as it is")
    icons = {}
    for name, fn in ICONS.items():
        ic = fn()
        ic.save(os.path.join(HEADS, f"{name}.png"))
        icons[name] = ic
        print(f"textures/mobheads/{name}.png")
    if out_prev:
        preview(out_prev, skins, icons, crane)
        print("preview in", out_prev)


if __name__ == "__main__":
    main()
