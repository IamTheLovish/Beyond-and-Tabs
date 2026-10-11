#!/usr/bin/env python3
"""Draws the Stormcallers' unit skins and portrait icons (original pixel art).

Reuses the skin toolkit of tools/gen_verdant_skins.py (box layouts, Skin, faces, robe/arms/legs helpers, Icon), as
tools/gen_tide_skins.py does, so the factions are painted the same way; only the palette and the units differ. Every
skin keeps the UV layout of the model its renderer uses:
  - VillagerUnitModel (64x64): Geomancer (and the Thunder Sage), Blade Dancer, Fire Adept, Jade Guard, Wind Monk, and
    (slice 2) Frost Adept, Rocket Battery. Its head's 8x12x8 "hat" box (32,0) is drawn wherever it is opaque: hats, a
    topknot, a helmet crest.
  - The vanilla witch model (64x128, WitchRenderer): the Ink Scribe (slice 2) - the pointed hat recut as a scholar's cap.
  - Alex's Mobs' shoebill model (RenderShoebill): the Paper Crane, and AM's spectre model (RenderSpectre): the
    Thunder Dragon (slice 3). Only the OUTLINE of AM's texture is read (which pixels its UV islands cover, so the paint
    lands on the model); every colour is ours - folded white paper with crease lines and red seal stamps, jade scales
    with a gold belly and mane. AM's own pixels are never copied. If an AM texture can't be found (no jar around), the
    existing png is left alone.
  - The vanilla iron golem model (128x128, the golem layout of tools/gen_t2t3_skins.py): the Earthshaker (slice 3).
  - Slice 3 on the villager model: Ley Master (the Geomancer's gold-robed senior), Gravity Monk, Mirror Mage.

Art direction (claude/design-factions.md, the Stormcallers): a mage dynasty in red lacquer, jade and gold - deep red
robes, gold trim and diadems, jade sashes and plates, paper white. Like the other factions, the top faces (hats,
shoulders) carry the strongest shapes because the RTS camera looks down, with strong value steps and light noise.

usage: python3 tools/gen_storm_skins.py [--preview DIR] [--am-texture shoebill.png] [--am-spectre spectre.png]
  writes bar/.../textures/entities/<unit>_unit.png and bar/.../textures/mobheads/<unit>.png (16x16 art at 4x);
  --am-texture / --am-spectre point at Alex's Mobs' textures/entity/shoebill.png / spectre.png (default: read from
  an alexsmobs jar if one is found under ~/.gradle or /mnt); --preview writes a contact sheet into DIR.
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
VIOLET_D, VIOLET, VIOLET_L = (52, 30, 92), (92, 58, 150), (150, 110, 206)
STONE_D, STONE, STONE_L = (74, 76, 72), (118, 120, 114), (160, 162, 154)
BRONZE_D, BRONZE, BRONZE_L = (120, 78, 36), (190, 130, 60), (236, 196, 120)

# the iron golem layout (tools/gen_t2t3_skins.py GOLEM): (u, v, w, h, d) per box of the vanilla model
GOLEM = {"head": (0, 0, 8, 10, 8), "nose": (24, 0, 2, 4, 2), "body": (0, 40, 18, 12, 11), "waist": (0, 70, 9, 5, 6),
         "arm_r": (60, 21, 4, 30, 6), "arm_l": (60, 58, 4, 30, 6), "leg_r": (37, 0, 6, 16, 5), "leg_l": (60, 0, 6, 16, 5)}


def put_rows(s, part, f, rows, pal, ox=0, oy=0):
    """Paint a small string map onto one face ('.' keeps what is there)."""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != '.' and ch in pal:
                s.fpx(part, f, ox + i, oy + j, pal[ch])


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


# ------------------------------------------------------------------ slice 3: the Celestial Observatory's units
def ley_master():
    """The T2 constructor: the Geomancer's senior - a gold robe with red trim and a jade sash, jade discs painted on the
    shoulders (the plan's floating discs, flattened onto the shoulder tops: the strongest shape from above), a tall
    black scholar's crown ringed in gold with a jade jewel; a grey beard."""
    s = Skin(VILLAGER, (64, 64), 71)
    villager_face(s, SKIN_WARM, (60, 50, 40), INK, hair=(200, 196, 190), beard=(210, 206, 200))
    robe(s, GOLD, GOLD_D, trim=RED, sash=9, sash_c=JADE)
    s.band("jacket", 17, 18, RED_D)
    for (i, j) in ((1, 3), (6, 3), (1, 12), (6, 12), (3, 6), (4, 6)):
        s.fpx("jacket", "front", i, j, RED)       # embroidered ley lines
    arms(s, GOLD, RED, SKIN_WARM, bracer=JADE_D)
    legs(s, GOLD_D, BLACK)
    shoulder_tops(s, JADE, accent=JADE_L)
    for part in ("arm_up", "arm"):
        s.band(part, 0, 2, JADE)
    # the crown: a black cylinder filling the hat box, a gold ring at the brow and the top, a jade jewel in front
    for i in range(1, 7):
        for j in range(1, 7):
            s.fpx("hat", "top", i, j, s.n(BLACK, 4))
    for f in SIDES:
        for i in range(1, 7):
            for j in range(0, 5):
                s.fpx("hat", f, i, j, s.n(BLACK, 4))
            s.fpx("hat", f, i, 0, GOLD)
            s.fpx("hat", f, i, 4, GOLD_D)
    s.fpx("hat", "front", 3, 2, JADE_L); s.fpx("hat", "front", 4, 2, JADE)
    s.fpx("hat", "top", 3, 3, GOLD_L); s.fpx("hat", "top", 4, 4, GOLD_L)
    return s


def gravity_monk():
    """The controller: a violet robe with a gold sash over one shoulder and gold orbit rings embroidered on the chest,
    bare forearms with gold bands, a shaved head (skin crown, as the Wind Monk) under a thin gold circlet with a violet
    stone - from above, a bare dome in a gold ring over violet shoulders."""
    s = Skin(VILLAGER, (64, 64), 72)
    villager_face(s, SKIN_TAN, (60, 40, 80), (110, 80, 60))
    robe(s, VIOLET, VIOLET_D, trim=GOLD)
    for j in range(12):   # the gold sash from the right shoulder
        i = 7 - min(7, j)
        s.fpx("jacket", "front", i, j, GOLD)
        if i - 1 >= 0:
            s.fpx("jacket", "front", i - 1, j, GOLD_D)
    # orbit rings on the chest
    for (i, j) in ((2, 5), (3, 4), (4, 4), (5, 5), (2, 7), (5, 7), (3, 8), (4, 8)):
        s.fpx("jacket", "front", i, j, GOLD_L)
    s.fpx("jacket", "front", 3, 6, VIOLET_L); s.fpx("jacket", "front", 4, 6, VIOLET_L)
    s.vshade("jacket", 1.06, 0.8)
    arms(s, VIOLET, GOLD, SKIN_TAN, bracer=GOLD_D)
    legs(s, VIOLET_D, (110, 82, 56))
    shoulder_tops(s, VIOLET_L, accent=GOLD)
    diadem(s, GOLD, VIOLET_L)
    s.fpx("head", "top", 3, 3, VIOLET_L); s.fpx("head", "top", 4, 4, VIOLET_L)   # the mark seen from above
    return s


def mirror_mage():
    """The decoy-maker: a silver-grey robe with red lacquer trim and a round bronze mirror on the chest (a disc with a
    bright centre), bronze bracers, a red hood drawn up on the hat box with a bronze clasp - from above, a red dome on
    grey shoulders."""
    s = Skin(VILLAGER, (64, 64), 73)
    villager_face(s, SKIN_IVORY, (40, 30, 30), INK, hair=BLACK)
    robe(s, SILVER, (120, 126, 136), trim=RED)
    # the mirror: a bronze disc on the chest with a pale centre
    put_rows(s, "jacket", "front",
             ["..BBBB..",
              ".BLLLLB.",
              ".BLWWLB.",
              ".BLWWLB.",
              ".BLLLLB.",
              "..BBBB.."],
             {"B": BRONZE_D, "L": BRONZE, "W": BRONZE_L}, oy=2)
    s.band("jacket", 9, 10, RED_D)
    s.vshade("jacket", 1.06, 0.82)
    arms(s, SILVER, RED, SKIN_IVORY, bracer=BRONZE)
    legs(s, (120, 126, 136), BLACK)
    shoulder_tops(s, SILVER_L, accent=RED)
    # the hood: red over the whole hat box, the face opening in front, a bronze clasp under the chin
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(RED, 5))
    for f in SIDES:
        for i in range(8):
            for j in range(8):
                if f == "front" and 1 <= i <= 6 and 2 <= j <= 7:
                    continue
                s.fpx("hat", f, i, j, s.n(RED if j < 6 else RED_D, 5))
    s.fpx("hat", "front", 3, 8, BRONZE); s.fpx("hat", "front", 4, 8, BRONZE_L)
    s.band("hat", 8, 12, RED_D)
    return s


def earthshaker():
    """The T2 heavy on the golem layout: grey stone with jade veins running down the arms and body, gold caps on the
    shoulders (the RTS view), a jade seal on the chest and jade eyes, a few darker cracks, a red lacquer band at the
    waist."""
    s = Skin(GOLEM, (128, 128), 74)

    def crack(part, f, pts):
        for (i, j) in pts:
            s.fpx(part, f, i, j, STONE_D)

    def vein(part, f, pts):
        for (i, j) in pts:
            s.fpx(part, f, i, j, JADE if (i + j) % 2 else JADE_D)

    for part in ("head", "nose", "body", "waist", "arm_r", "arm_l", "leg_r", "leg_l"):
        s.fill(part, STONE, 5)
        s.vshade(part, 1.05, 0.86)
    # head: a brow ridge, jade eyes, a gold band
    s.band("head", 0, 1, GOLD_D)
    put_rows(s, "head", "front",
             ["........",
              "........",
              "........",
              ".JJ..JJ.",
              ".JL..LJ.",
              "........",
              "........",
              "........",
              ".D....D.",
              "..DDDD.."],
             {"J": JADE, "L": JADE_L, "D": STONE_D})
    x, y, w, h = s.face("head", "top")
    for i in range(w):
        for j in range(h):
            s.put(x + i, y + j, s.n(STONE_L, 3))
    s.put(x + 3, y + 3, JADE); s.put(x + 4, y + 4, JADE)
    # body: gold shoulder caps on the top face, a jade seal on the chest, veins down the sides
    s.fill("body", GOLD, 4, which=("top",))
    x, y, w, h = s.face("body", "top")
    for i in range(4, 14):
        for j in range(2, 9):
            s.put(x + i, y + j, s.n(STONE_L, 3))
    put_rows(s, "body", "front",
             [".....JJJJ.......",
              "....J....J......",
              "....J.JJ.J......",
              "....J.JJ.J......",
              "....J....J......",
              ".....JJJJ......."],
             {"J": JADE}, ox=1, oy=3)
    vein("body", "right", [(2, 1), (3, 2), (3, 3), (4, 4), (4, 5), (5, 6), (5, 7), (6, 8)])
    vein("body", "left", [(8, 1), (7, 2), (7, 3), (6, 4), (6, 5), (5, 6), (5, 7), (4, 8)])
    vein("body", "back", [(4, 2), (5, 3), (5, 4), (6, 5), (12, 1), (11, 2), (11, 3), (10, 4), (10, 5)])
    crack("body", "front", [(14, 7), (15, 8), (15, 9), (2, 9), (3, 10)])
    s.band("body", 11, 12, STONE_D)
    # waist: a red lacquer belt with a gold buckle
    s.fill("waist", RED, 4)
    s.band("waist", 0, 1, GOLD_D)
    s.fpx("waist", "front", 4, 2, GOLD); s.fpx("waist", "front", 4, 3, GOLD_L)
    # arms: jade veins down the outer faces, gold knuckle bands
    for arm, outer in (("arm_r", "right"), ("arm_l", "left")):
        vein(arm, outer, [(2, 4), (3, 5), (3, 6), (2, 7), (2, 8), (3, 9), (3, 10), (2, 11), (2, 12), (3, 13), (3, 14)])
        vein(arm, "front", [(1, 6), (2, 7), (2, 8), (1, 9), (1, 10), (2, 11)])
        s.band(arm, 24, 26, GOLD_D)
        s.fill(arm, GOLD, 4, which=("top",))
        crack(arm, "front", [(0, 16), (1, 17), (3, 20)])
    # legs: darker shins, a gold anklet
    for leg in ("leg_r", "leg_l"):
        s.band(leg, 10, 16, sh(STONE, 0.9))
        s.band(leg, 13, 14, GOLD_D)
        crack(leg, "front", [(1, 3), (2, 4), (4, 8), (3, 9)])
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


def _find_am_texture(cli, name="shoebill.png"):
    if cli and os.path.exists(cli):
        return Image.open(cli).convert("RGBA")
    pats = [os.path.expanduser("~/.gradle/**/alexsmobs*.jar"), "/mnt/**/alexsmobs*.jar"]
    for pat in pats:
        for jar in glob.glob(pat, recursive=True):
            try:
                with zipfile.ZipFile(jar) as z:
                    return Image.open(io.BytesIO(z.read("assets/alexsmobs/textures/entity/" + name))).convert("RGBA")
            except (KeyError, zipfile.BadZipFile, OSError):
                continue
    return None


def thunder_dragon(am):
    """Jade scales on the spectre's UV islands: a scale pattern (lighter scale centres on a 3-pixel lattice, dark
    edges) in jade, the lower third of every island column warmed to a gold belly, gold spines along the top rows, a
    few red lacquer marks and a darker outline. Only AM's alpha (where its islands are) is read."""
    w, h = am.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    src, px = am.load(), out.load()
    rng = random.Random(74)
    # per column: the island's vertical extent, so the belly (bottom third) and the spine (top rows) can be placed
    tops, bots = [None] * w, [None] * w
    for x in range(w):
        ys = [y for y in range(h) if src[x, y][3] >= 128]
        if ys:
            tops[x], bots[x] = min(ys), max(ys)
    for y in range(h):
        for x in range(w):
            if src[x, y][3] < 128:
                continue
            edge = any(not (0 <= x + dx < w and 0 <= y + dy < h) or src[x + dx, y + dy][3] < 128
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            span = max(1, bots[x] - tops[x])
            t = (y - tops[x]) / span
            if t > 0.66:
                base, dark, light = GOLD, GOLD_D, GOLD_L      # the belly
            else:
                base, dark, light = JADE, JADE_D, JADE_L      # the scales
            if edge:
                c = dark
            elif x % 3 == 1 and y % 3 == 1:
                c = light                                      # scale centre
            elif x % 3 == 0 or y % 3 == 0:
                c = sh(base, 0.86)                             # scale edge
            else:
                c = base
            if t < 0.08 and not edge:
                c = GOLD_L if (x % 2) else GOLD                # the spine
            k = rng.randint(-5, 5)
            px[x, y] = tuple(max(0, min(255, v + k)) for v in c) + (255,)
    # red lacquer marks: 2x2 squares on a few interior pixels
    for (fx, fy) in ((0.25, 0.3), (0.6, 0.25), (0.45, 0.55), (0.8, 0.6), (0.3, 0.75)):
        x, y = int(w * fx), int(h * fy)
        if all(0 <= x + i < w and 0 <= y + j < h and src[x + i, y + j][3] >= 128 for i in (0, 1) for j in (0, 1)):
            for i in (0, 1):
                for j in (0, 1):
                    px[x + i, y + j] = RED + (255,)
    return out


def thunder_dragon_glow(am_glow):
    """The eye glow: AM's glow texture only says where the eyes are; ours paints them gold."""
    w, h = am_glow.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    src, px = am_glow.load(), out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] >= 128:
                px[x, y] = GOLD_L + (255,)
    return out


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
    # slice 3
    "ley_master": (ley_master, "villager"),
    "gravity_monk": (gravity_monk, "villager"),
    "mirror_mage": (mirror_mage, "villager"),
    "earthshaker": (earthshaker, "golem"),
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


@icon
def icon_ley_master():
    i = Icon(BG_T, BG_B)
    i.art(["......YY........",
           ".....KKKKKK.....",
           ".....KKJJKK.....",
           ".....YYYYYY.....",
           "....HSSSSSSH....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SBSNNSBS....",
           ".....BBBBBB.....",
           "..GGYYYYYYYYGG..",
           ".GGYYYRRYYYYYGG.",
           ".JJYYYRRYYYYYJJ.",
           ".JJJJJJJJJJJJJJ.",
           ".YYYYYRRYYYYYYY.",
           ".RRRRRRRRRRRRRR.",
           "................"],
          {"Y": GOLD, "K": BLACK, "J": JADE_L, "H": (200, 196, 190), "S": SKIN_WARM, "W": W_, "E": (60, 50, 40),
           "N": sh(SKIN_WARM, 0.88), "B": (210, 206, 200), "G": JADE, "R": RED})
    i.frame(FRAME)
    return i


@icon
def icon_thunder_dragon():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....C...........",
           "...CLC..JJJ.....",
           "....C..JJGJJJ...",
           ".......JYYJJJJ..",
           "......JJJJJJJJJ.",
           ".....JGGJJJJJJJJ",
           "....JJJJJYYYYYJ.",
           "...JJJJJYYYY....",
           "..JJJJYYYY......",
           ".JJJYYYY.....C..",
           ".JJYYY......CLC.",
           ".JYY.........C..",
           "..Y.............",
           "................",
           "................"],
          {"J": JADE, "G": GOLD_L, "Y": GOLD, "C": (230, 236, 255), "L": (255, 255, 200)})
    i.frame(FRAME)
    return i


@icon
def icon_earthshaker():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".....SSSSSS.....",
           "....SSSSSSSS....",
           "....SJJSSJJS....",
           "....SSSSSSSS....",
           "....SSDDDDSS....",
           "..YYYSSSSSSYYY..",
           ".SSSSSSJJSSSSSS.",
           ".SJSSSJSSJSSSJS.",
           ".SJSSSJSSJSSSJS.",
           ".SSJSSSJJSSSJSS.",
           ".SSJSRRRRRRSJSS.",
           ".SSSSSSSSSSSSSS.",
           "..SS..SSSS..SS..",
           "..SS..SSSS..SS..",
           "..DD..DDDD..DD.."],
          {"S": STONE, "J": JADE, "D": STONE_D, "Y": GOLD, "R": RED})
    i.frame(FRAME)
    return i


@icon
def icon_gravity_monk():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "........SS......",
           "......SSSSSS....",
           ".....YYYYYYYY...",
           ".....SSSVSSSS...",
           ".....SWESSEWS...",
           ".C...SSSNNSSS...",
           "C.C..SSSNNSSS...",
           ".C...BBBBBBBB...",
           "...PPPPYPPPPPPP.",
           "..PPPYPPPPPPPPP.",
           "..PPYPPGGPPPPPP.",
           "..PYPPGPPGPPPPP.",
           "..YPPPPGGPPPPPP.",
           "..DDDDDDDDDDDDD.",
           "................"],
          {"C": VIOLET_L, "S": SKIN_TAN, "Y": GOLD, "V": VIOLET_L, "W": W_, "E": (60, 40, 80), "N": sh(SKIN_TAN, 0.88),
           "B": (90, 56, 34), "P": VIOLET, "G": GOLD_L, "D": VIOLET_D})
    i.frame(FRAME)
    return i


@icon
def icon_mirror_mage():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".....RRRRRR.....",
           "....RRRRRRRR....",
           "....RSSSSSSR....",
           "....RWESSEWR....",
           "....RSSNNSSR....",
           "....RSSNNSSR....",
           ".....SSSSSS.....",
           "..GGGGGBBGGGGG..",
           ".GGGGGBLLBGGGGG.",
           ".GGGGBLWWLBGGGG.",
           ".GGGGBLWWLBGGGG.",
           ".GGGGGBLLBGGGGG.",
           ".GGGGGGBBGGGGGG.",
           ".DDDDDDDDDDDDDD.",
           "................"],
          {"R": RED, "S": SKIN_IVORY, "W": W_, "E": (40, 30, 30), "N": sh(SKIN_IVORY, 0.88), "G": SILVER,
           "B": BRONZE_D, "L": BRONZE, "D": (120, 126, 136)})
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
    spectre_path = sys.argv[sys.argv.index("--am-spectre") + 1] if "--am-spectre" in sys.argv else None
    skins = {}
    for name, (fn, kind) in UNITS.items():
        s = fn()
        s.im.save(os.path.join(ENT, f"{name}_unit.png"))
        if kind != "golem":   # the contact sheet's front mock only knows the villager and witch layouts
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
    spectre = _find_am_texture(spectre_path, "spectre.png")
    # the glow outline sits beside spectre.png: next to a given --am-spectre file, else in the jar
    glow = None
    if spectre_path and os.path.exists(os.path.join(os.path.dirname(spectre_path), "spectre_glow.png")):
        glow = Image.open(os.path.join(os.path.dirname(spectre_path), "spectre_glow.png")).convert("RGBA")
    elif spectre_path is None:
        glow = _find_am_texture(None, "spectre_glow.png")
    if spectre is not None:
        dragon = thunder_dragon(spectre)
        dragon.save(os.path.join(ENT, "thunder_dragon_unit.png"))
        print(f"textures/entities/thunder_dragon_unit.png {dragon.size}")
        if glow is not None:
            thunder_dragon_glow(glow).save(os.path.join(ENT, "thunder_dragon_glow.png"))
            print("textures/entities/thunder_dragon_glow.png")
    else:
        print("Alex's Mobs spectre texture not found: thunder_dragon_unit.png left as it is")
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
