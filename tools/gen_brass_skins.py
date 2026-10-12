#!/usr/bin/env python3
"""Draws the Brassworks Artificers' unit skins and portrait icons (original pixel art, deterministic: seeded Skins).

Reuses the skin toolkit of tools/gen_verdant_skins.py (box layouts, Skin, faces, robe/arms/legs helpers, Icon), as the
Tidewrought and Stormcallers generators do, so the factions are painted the same way; only the palette and the units
differ. Every skin keeps the UV layout of the model its renderer uses:
  - VillagerUnitModel (64x64): Tinker (and the Guildmaster), Rifle Squad, Flak Cart, Mortar Crawler, Shield Projector,
    Sapper - dwarf crews: beards, goggles on the hat box, leather aprons, brass bracers and packs.
  - The vanilla iron golem model (128x128, the golem layout of tools/gen_t2t3_skins.py): the Iron Warden - brass plate
    with soot joints, rivet rows and a furnace door glowing on the chest.
  - The vanilla strider model (64x128): the Steam Runner - a riveted brass boiler drum with a furnace-orange firebox
    window on soot-black stilt legs. The sheet is painted whole (brass with plate bands) and the body / leg islands
    on top, so every pixel the model samples is brass or soot whatever the exact UV edge.
  - The vanilla parrot model (32x32): the Gyro Scout - a brass hull, copper rotor-wings, a glass eye; painted the same way.

Art direction (claude/design-factions.md, the Brassworks): brass, soot black and furnace orange - riveted plates,
leather and goggles, a glow in every firebox. The top faces (hats, shoulders, packs) carry the strongest shapes
because the RTS camera looks down.

usage: python3 tools/gen_brass_skins.py [--preview DIR]
  writes bar/.../textures/entities/<unit>_unit.png and bar/.../textures/mobheads/<unit>.png (16x16 art at 4x);
  --preview writes a contact sheet into DIR.
"""
import importlib.util, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("gen_verdant_skins", os.path.join(HERE, "gen_verdant_skins.py"))
V = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(V)

Skin, VILLAGER, SIDES, Icon = V.Skin, V.VILLAGER, V.SIDES, V.Icon
sh, villager_face, robe, arms, legs, shoulder_tops = V.sh, V.villager_face, V.robe, V.arms, V.legs, V.shoulder_tops
ENT, HEADS = V.ENT, V.HEADS

# ------------------------------------------------------------------ palette
BRASS_D, BRASS, BRASS_L = (134, 90, 30), (201, 138, 46), (236, 190, 100)
COPPER_D, COPPER = (150, 86, 50), (196, 120, 70)
SOOT_D, SOOT, SOOT_L = (24, 22, 22), (42, 38, 34), (74, 68, 62)
LEATHER_D, LEATHER = (76, 50, 28), (120, 82, 48)
FLAME_D, FLAME, FLAME_L = (200, 70, 20), (255, 138, 40), (255, 210, 110)
IRON_D, IRON, IRON_L = (90, 92, 98), (140, 144, 152), (190, 194, 200)
GLASS = (160, 220, 230)
INK, BLACK = (40, 30, 30), (24, 22, 24)
SKIN_RUDDY, SKIN_WARM = (222, 170, 130), (214, 170, 128)
BEARD_RED, BEARD_GREY, BEARD_BROWN = (150, 70, 30), (190, 186, 180), (90, 60, 36)

# the iron golem layout (tools/gen_t2t3_skins.py GOLEM): (u, v, w, h, d) per box of the vanilla model
GOLEM = {"head": (0, 0, 8, 10, 8), "nose": (24, 0, 2, 4, 2), "body": (0, 40, 18, 12, 11), "waist": (0, 70, 9, 5, 6),
         "arm_r": (60, 21, 4, 30, 6), "arm_l": (60, 58, 4, 30, 6), "leg_r": (37, 0, 6, 16, 5), "leg_l": (60, 0, 6, 16, 5)}
# the strider layout (StriderModel): the drum and the two legs (the bristles stay transparent: a machine has no hair)
STRIDER = {"body": (0, 32, 16, 14, 16), "leg_r": (0, 55, 4, 16, 4), "leg_l": (0, 55, 4, 16, 4)}
# the parrot layout (ParrotModel): hull, head, tail, wings, beak, legs
PARROT = {"body": (2, 8, 3, 6, 3), "head": (2, 2, 2, 3, 2), "head2": (10, 0, 2, 2, 4), "tail": (22, 1, 3, 4, 1),
          "wing": (19, 8, 1, 5, 3), "beak": (11, 7, 1, 2, 1), "beak2": (16, 7, 1, 1, 1), "leg": (14, 18, 1, 2, 1)}


def put_rows(s, part, f, rows, pal, ox=0, oy=0):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in pal:
                s.fpx(part, f, ox + i, oy + j, pal[ch])


def goggles(s, lens=GLASS, strap=LEATHER_D, rim=BRASS):
    """Brass-rimmed goggles pushed up on the hat box: two round lenses on the front, a strap round the sides and back."""
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 2, strap)
            s.fpx("hat", f, i, 3, sh(strap, 0.85))
    for ox in (1, 5):
        for (i, j) in ((ox, 1), (ox + 1, 1), (ox, 4), (ox + 1, 4), (ox - 1, 2), (ox - 1, 3), (ox + 2, 2), (ox + 2, 3)):
            s.fpx("hat", "front", i, j, rim)
        for (i, j) in ((ox, 2), (ox + 1, 2), (ox, 3), (ox + 1, 3)):
            s.fpx("hat", "front", i, j, lens)
        s.fpx("hat", "front", ox, 2, sh(lens, 1.15))


def cap(s, c, band=None, badge=None):
    """A flat worker's cap on the hat box: the crown on top, a short brim all round."""
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(c, 5))
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 0, c)
            s.fpx("hat", f, i, 1, band or sh(c, 0.8))
    if badge:
        s.fpx("hat", "front", 3, 1, badge); s.fpx("hat", "front", 4, 1, sh(badge, 1.2))


def apron(s, c, strap, bib=True):
    """A leather apron over the robe: the bib on the chest, straps over the shoulders, a tool loop at the hip."""
    for j in range(2 if bib else 7, 16):
        for i in range(1, 7):
            s.fpx("jacket", "front", i, j, s.n(c, 5))
    if bib:
        for j in range(0, 3):
            s.fpx("jacket", "front", 2, j, strap)
            s.fpx("jacket", "front", 5, j, strap)
            s.fpx("jacket", "back", 2, j, strap)
            s.fpx("jacket", "back", 5, j, strap)
    s.band("jacket", 7, 8, strap)
    s.band("body", 7, 8, strap)
    s.fpx("jacket", "front", 1, 9, BRASS_D); s.fpx("jacket", "front", 1, 10, BRASS)   # the buckle of the tool loop


def rivets(s, part, f, step=3, c=BRASS_L, oy=0):
    x, y, w, h = s.face(part, f)
    for j in range(oy, h, step):
        for i in range(0, w, step):
            s.put(x + i, y + j, c)


def pack(s, c, trim, rows):
    """A pack drawn on the back of the robe (the RTS camera sees backs and tops): a string map on the jacket's back."""
    put_rows(s, "jacket", "back", rows, {"B": c, "T": trim, "D": sh(c, 0.75), "F": FLAME, "L": FLAME_L, "G": GLASS, "S": SOOT})
    s.vshade("jacket", 1.06, 0.82)


# ------------------------------------------------------------------ the crews (villager model)
def tinker():
    """The constructor (and the Guildmaster): a ruddy dwarf with a red beard, goggles up on a leather cap, a soot coat
    under a long leather apron with brass buckles, brass bracers; a brass toolbox on the back."""
    s = Skin(VILLAGER, (64, 64), 701)
    villager_face(s, SKIN_RUDDY, (60, 50, 40), INK, hair=BEARD_RED, beard=BEARD_RED)
    robe(s, SOOT, SOOT_D)
    apron(s, LEATHER, LEATHER_D)
    pack(s, BRASS, BRASS_L, ["........", "........", "........", ".TTTTTT.", ".BBBBBB.", ".BDDDDB.", ".BBBBBB.", ".TTTTTT."])
    arms(s, SOOT, LEATHER_D, SKIN_RUDDY, bracer=BRASS)
    legs(s, SOOT_D, LEATHER_D)
    shoulder_tops(s, SOOT_L, accent=BRASS)
    cap(s, LEATHER, band=LEATHER_D, badge=BRASS)
    goggles(s)
    return s


def rifle_squad():
    """The musketeers: a grey-bearded dwarf in a soot coat with brass buttons and a powder-horn strap, a flat cap with a
    brass badge, leather bracers; a powder keg and ramrod on the back."""
    s = Skin(VILLAGER, (64, 64), 702)
    villager_face(s, SKIN_WARM, (60, 50, 40), INK, hair=BEARD_GREY, beard=BEARD_GREY)
    robe(s, SOOT, SOOT_D, trim=BRASS_D)
    for j in range(2, 14, 3):   # brass buttons down the coat
        s.fpx("jacket", "front", 3, j, BRASS_L)
    for j in range(9):          # the powder-horn strap
        s.fpx("jacket", "front", 7 - min(7, j), j, LEATHER)
    pack(s, SOOT_L, BRASS, ["........", "........", "..TTTT..", "..BBBB..", "..BDDB..", "..BBBB..", "..TTTT..", "........"])
    arms(s, SOOT, BRASS_D, SKIN_WARM, bracer=LEATHER)
    legs(s, SOOT_D, LEATHER_D)
    shoulder_tops(s, SOOT_L, accent=BRASS_L)
    cap(s, SOOT_L, band=SOOT_D, badge=BRASS)
    return s


def flak_cart():
    """The anti-air gunner: a brown-bearded dwarf in a leather coat, goggles down over the eyes, ear muffs, brass
    bracers; the brass flak pack - a drum with a glowing gauge - on the back and a shell belt across the chest."""
    s = Skin(VILLAGER, (64, 64), 703)
    villager_face(s, SKIN_WARM, (60, 50, 40), INK, hair=BEARD_BROWN, beard=BEARD_BROWN)
    robe(s, LEATHER, LEATHER_D, trim=SOOT_D)
    for i in range(1, 7):   # the shell belt
        s.fpx("jacket", "front", i, 6, BRASS if i % 2 else BRASS_L)
    pack(s, BRASS, BRASS_D, ["........", "..TTTT..", ".BBBBBB.", ".BDGGDB.", ".BDGGDB.", ".BBBBBB.", ".BTTTTB.", "........"])
    arms(s, LEATHER, SOOT, SKIN_WARM, bracer=BRASS)
    legs(s, SOOT, LEATHER_D)
    shoulder_tops(s, BRASS, accent=SOOT)
    cap(s, SOOT, band=SOOT_D)
    goggles(s, lens=SOOT_L)
    for f in ("right", "left"):   # ear muffs
        s.fpx("hat", f, 3, 4, SOOT_L); s.fpx("hat", f, 4, 4, SOOT_L); s.fpx("hat", f, 3, 5, SOOT_L); s.fpx("hat", f, 4, 5, SOOT_L)
    return s


def mortar_crawler():
    """The mortar crew: a soot-smudged dwarf in a leather apron over a soot coat, a brass helmet, brass bracers; the
    mortar carriage on the back - a brass tube with a glowing breech and a row of shells."""
    s = Skin(VILLAGER, (64, 64), 704)
    villager_face(s, SKIN_WARM, (60, 50, 40), INK, hair=BEARD_BROWN, beard=BEARD_BROWN)
    for (i, j) in ((5, 6), (6, 7), (1, 7)):
        s.fpx("head", "front", i, j, sh(SKIN_WARM, 0.7))   # soot smudges
    robe(s, SOOT, SOOT_D)
    apron(s, LEATHER, LEATHER_D, bib=False)
    pack(s, BRASS, BRASS_L, ["..TBBT..", "..BBBB..", "..BBBB..", "..BFFB..", "..BLLB..", ".SSSSSS.", ".SBSBSB.", "........"])
    arms(s, SOOT, LEATHER_D, SKIN_WARM, bracer=BRASS)
    legs(s, SOOT_D, LEATHER_D)
    shoulder_tops(s, BRASS_D, accent=BRASS_L)
    # a brass helmet: a domed crown with a rivet ridge
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(BRASS, 5))
    for f in SIDES:
        for i in range(8):
            for j in range(0, 4):
                s.fpx("hat", f, i, j, s.n(BRASS, 5) if j < 3 else BRASS_D)
    for i in range(0, 8, 2):
        s.fpx("hat", "top", i, 3, BRASS_L)
    return s


def shield_projector():
    """The support: a grey-bearded dwarf in a brass-trimmed soot coat with a glass dial on the chest, goggles up on a
    leather cap, brass bracers; the projector pack - a brass drum with a glass lens - on the back."""
    s = Skin(VILLAGER, (64, 64), 705)
    villager_face(s, SKIN_RUDDY, (60, 50, 40), INK, hair=BEARD_GREY, beard=BEARD_GREY)
    robe(s, SOOT, SOOT_D, trim=BRASS)
    for (i, j) in ((3, 5), (4, 5), (3, 6), (4, 6)):   # the chest dial
        s.fpx("jacket", "front", i, j, GLASS)
    s.fpx("jacket", "front", 3, 5, sh(GLASS, 1.15))
    pack(s, BRASS, BRASS_D, ["........", "..TTTT..", ".BBBBBB.", ".BGGGGB.", ".BGGGGB.", ".BBBBBB.", "..TTTT..", "........"])
    arms(s, SOOT, BRASS_D, SKIN_RUDDY, bracer=BRASS)
    legs(s, SOOT_D, LEATHER_D)
    shoulder_tops(s, BRASS, accent=GLASS)
    cap(s, LEATHER, band=LEATHER_D)
    goggles(s)
    return s


def sapper():
    """The demolition dwarf: a soot apron over a leather coat, a red beard, a soot cap with a brass lamp, leather
    bracers; the charge satchel on the back - soot canvas with a row of brass-capped charges and a fuse."""
    s = Skin(VILLAGER, (64, 64), 706)
    villager_face(s, SKIN_RUDDY, (60, 50, 40), INK, hair=BEARD_RED, beard=BEARD_RED)
    robe(s, LEATHER, LEATHER_D)
    apron(s, SOOT, SOOT_D)
    pack(s, SOOT_L, BRASS, ["........", "........", ".TTTTTT.", ".BSBSBS.", ".BSBSBS.", ".BBBBBB.", ".DDDDDD.", ".F......"])
    arms(s, LEATHER, SOOT, SKIN_RUDDY, bracer=LEATHER_D)
    legs(s, SOOT_D, LEATHER_D)
    shoulder_tops(s, SOOT, accent=BRASS)
    cap(s, SOOT, band=SOOT_D, badge=FLAME_L)   # the lamp
    return s


# ------------------------------------------------------------------ the machines
def iron_warden():
    """The line golem on the golem layout at 0.8x: brass plates with soot joints, rows of rivets, a furnace door glowing
    on the chest, glass eyes, iron fists and knees."""
    s = Skin(GOLEM, (128, 128), 707)
    for part in ("head", "nose", "body", "waist", "arm_r", "arm_l", "leg_r", "leg_l"):
        s.fill(part, BRASS, 5)
        s.vshade(part, 1.05, 0.84)
    # head: a brass helm with a soot visor slit and glass eyes, a rivet ridge
    s.band("head", 3, 5, SOOT)
    put_rows(s, "head", "front", ["........", "........", "........", "SGGSSGGS", "SGLSSLGS", "........", "........", "....D...", "...DDD..", "........"],
             {"S": SOOT, "G": GLASS, "L": sh(GLASS, 1.15), "D": SOOT_D})
    x, y, w, h = s.face("head", "top")
    for i in range(w):
        for j in range(h):
            s.put(x + i, y + j, s.n(BRASS_L if (i + j) % 2 else BRASS, 3))
    s.fill("nose", SOOT, 3)
    # body: soot shoulder joints on the top face, the furnace door glowing on the chest, rivets round the plates
    s.fill("body", BRASS_L, 4, which=("top",))
    x, y, w, h = s.face("body", "top")
    for i in range(0, 4):
        for j in range(h):
            s.put(x + i, y + j, s.n(SOOT, 3)); s.put(x + w - 1 - i, y + j, s.n(SOOT, 3))
    put_rows(s, "body", "front", ["......SSSSSS......", "......SFFFFS......", "......SFLLFS......", "......SFLLFS......", "......SFFFFS......", "......SSSSSS......"],
             {"S": SOOT_D, "F": FLAME, "L": FLAME_L}, oy=3)
    for f in SIDES:
        rivets(s, "body", f, step=3, oy=1)
    s.band("body", 11, 12, SOOT)
    # waist: a soot belt with a brass buckle
    s.fill("waist", SOOT, 4)
    s.fpx("waist", "front", 4, 2, BRASS); s.fpx("waist", "front", 4, 1, BRASS_L)
    # arms: brass plate with rivet rows, soot elbow bands, iron fists
    for arm in ("arm_r", "arm_l"):
        rivets(s, arm, "front", step=4, oy=2)
        rivets(s, arm, "right" if arm == "arm_r" else "left", step=4, oy=2)
        s.band(arm, 12, 14, SOOT)
        s.band(arm, 24, 30, IRON)
        s.fill(arm, IRON_D, 3, which=("bottom",))
        s.fill(arm, BRASS_L, 4, which=("top",))
    # legs: soot knees, iron feet
    for leg in ("leg_r", "leg_l"):
        s.band(leg, 0, 2, SOOT)
        s.band(leg, 12, 16, IRON)
        s.fill(leg, IRON_D, 3, which=("bottom",))
        rivets(s, leg, "front", step=3, oy=3)
    return s


def steam_runner():
    """The raider on the strider layout at 0.8x: the whole sheet painted brass with darker plate bands (so every
    sampled pixel is plate), the drum's firebox window glowing on the front, rivet rows, soot-black legs with brass
    knee bands. The bristle islands are left clear: a boiler has no hair."""
    s = Skin(STRIDER, (64, 128), 708)
    W, H = s.im.size
    for x in range(W):
        for y in range(H):
            c = BRASS_D if (y // 4) % 3 == 2 else BRASS
            s.put(x, y, s.n(c, 4))
    # the bristles (StriderModel: texOffs 16,0 / 16,15, 12 wide) stay transparent so no "hair" shows
    for x in range(16, 40):
        for y in range(0, 32):
            s.clear(x, y)
    for part in ("body",):
        s.fill(part, BRASS, 4)
        s.vshade(part, 1.06, 0.84)
        for f in SIDES:
            rivets(s, part, f, step=4, oy=1)
        s.band(part, 6, 8, BRASS_D)
    put_rows(s, "body", "front", ["......SSSS......", "......SFFS......", "......SLLS......", "......SFFS......", "......SSSS......"],
             {"S": SOOT_D, "F": FLAME, "L": FLAME_L}, oy=4)
    # a chimney stub and a gauge on the top face
    x, y, w, h = s.face("body", "top")
    for i in range(6, 10):
        for j in range(6, 10):
            s.put(x + i, y + j, s.n(SOOT, 3))
    s.put(x + 3, y + 3, GLASS); s.put(x + 12, y + 12, GLASS)
    # the legs: soot with brass knee bands and iron feet
    s.fill("leg_r", SOOT, 4)
    s.band("leg_r", 5, 7, BRASS)
    s.band("leg_r", 13, 16, IRON)
    s.fill("leg_r", IRON_D, 3, which=("bottom",))
    return s


def gyro_scout():
    """The scout on the parrot layout at 1.4x: a brass hull with a soot rotor housing on top, copper rotor-wings and
    tail vanes, a glass eye and a brass beak-prow. The sheet is painted whole, so every sampled pixel is metal."""
    s = Skin(PARROT, (32, 32), 709)
    W, H = s.im.size
    for x in range(W):
        for y in range(H):
            s.put(x, y, s.n(BRASS if (x + y) % 5 else BRASS_D, 4))
    for part in ("body", "head", "head2"):
        s.fill(part, BRASS, 4)
        s.vshade(part, 1.06, 0.82)
    s.fill("head2", SOOT, 3)          # the rotor housing on top of the head
    s.fill("head2", BRASS_L, 2, which=("top",))
    s.fpx("head", "front", 0, 0, GLASS); s.fpx("head", "front", 1, 0, GLASS)
    s.fpx("head", "front", 0, 1, sh(GLASS, 0.8))
    for part in ("wing", "tail"):
        s.fill(part, COPPER, 4)
        s.vshade(part, 1.05, 0.85)
    s.fill("beak", BRASS_D, 2)
    s.fill("beak2", BRASS_D, 2)
    s.fill("leg", SOOT, 2)
    s.fpx("body", "front", 1, 2, FLAME); s.fpx("body", "front", 1, 3, FLAME_L)   # the boiler's glow on the belly
    return s


# ------------------------------------------------------------------ slice 2: the Assembly Works' T2
# the ravager layout (RavagerModel): head, jaw, horns, neck, body (two boxes), legs
RAVAGER = {"head": (0, 0, 16, 20, 16), "mouth": (0, 36, 16, 3, 16), "horn": (74, 55, 2, 14, 4), "neck": (68, 73, 10, 10, 18),
           "body": (0, 55, 14, 16, 20), "belly": (0, 91, 12, 13, 18), "leg_r": (96, 0, 8, 37, 8), "leg_l": (64, 0, 8, 37, 8)}
# the spider layout (SpiderModel, also the cave spider's): head, neck, abdomen, legs
SPIDER = {"head": (32, 4, 8, 8, 8), "neck": (0, 0, 6, 6, 6), "abdomen": (0, 12, 10, 8, 12), "leg": (18, 0, 16, 2, 2)}
# the silverfish layout (SilverfishModel): seven body segments and three back plates
SILVERFISH = {"seg0": (0, 0, 3, 2, 2), "seg1": (0, 4, 4, 3, 2), "seg2": (0, 9, 6, 4, 3), "seg3": (0, 16, 3, 3, 3),
              "seg4": (0, 22, 2, 2, 3), "seg5": (11, 0, 2, 1, 2), "seg6": (13, 4, 1, 1, 2),
              "plate0": (20, 0, 10, 8, 3), "plate1": (20, 11, 6, 4, 3), "plate2": (20, 18, 6, 5, 2)}
TESLA_BLUE, TESLA_L = (110, 200, 255), (200, 240, 255)
LAMP_G, LAMP_GL = (90, 200, 90), (170, 250, 160)


def plate_sheet(s, a=BRASS, b=BRASS_D, band=4):
    """Paints the whole sheet brass with darker plate bands, so every pixel any UV samples is metal (the Steam Runner's way)."""
    W, H = s.im.size
    for x in range(W):
        for y in range(H):
            s.put(x, y, s.n(b if (y // band) % 3 == 2 else a, 4))


def engineer_captain():
    """The T2 constructor: an officer of the guild - a grey-bearded dwarf in a long soot coat with brass epaulettes and
    a double row of brass buttons, a peaked soot cap with a brass badge and goggles, brass bracers; a brass toolbox with
    a gauge on the back."""
    s = Skin(VILLAGER, (64, 64), 711)
    villager_face(s, SKIN_RUDDY, (60, 50, 40), INK, hair=BEARD_GREY, beard=BEARD_GREY)
    robe(s, SOOT, SOOT_D, trim=BRASS)
    for j in range(2, 14, 3):
        s.fpx("jacket", "front", 2, j, BRASS_L)
        s.fpx("jacket", "front", 5, j, BRASS_L)
    pack(s, BRASS, BRASS_L, ["........", "........", ".TTTTTT.", ".BBGGBB.", ".BBGGBB.", ".BDDDDB.", ".TTTTTT.", "........"])
    arms(s, SOOT, BRASS_D, SKIN_RUDDY, bracer=BRASS)
    legs(s, SOOT_D, LEATHER_D)
    shoulder_tops(s, BRASS, accent=BRASS_L)   # epaulettes
    cap(s, SOOT_L, band=BRASS_D, badge=BRASS_L)
    goggles(s)
    return s


def tesla_walker():
    """The arc-thrower's crew: a brown-bearded dwarf in a rubber-black coat with copper trim, goggles down (blue lenses),
    copper bracers; the coil pack - a copper drum wound with brass and a crackling blue core - on the back."""
    s = Skin(VILLAGER, (64, 64), 712)
    villager_face(s, SKIN_WARM, (60, 50, 40), INK, hair=BEARD_BROWN, beard=BEARD_BROWN)
    robe(s, SOOT_D, BLACK, trim=COPPER)
    for i in range(1, 7):   # a copper cable across the chest
        s.fpx("jacket", "front", i, 4 + (i % 2), COPPER)
    put_rows(s, "jacket", "back", ["..TTTT..", ".PBPBPB.", ".PVVVVP.", ".PVLLVP.", ".PVVVVP.", ".PBPBPB.", "..TTTT..", "........"],
             {"T": BRASS_L, "P": COPPER, "B": BRASS, "V": TESLA_BLUE, "L": TESLA_L})
    s.vshade("jacket", 1.06, 0.82)
    arms(s, SOOT_D, COPPER_D, SKIN_WARM, bracer=COPPER)
    legs(s, BLACK, LEATHER_D)
    shoulder_tops(s, COPPER, accent=TESLA_BLUE)
    cap(s, SOOT, band=COPPER_D)
    goggles(s, lens=TESLA_BLUE)
    return s


def steam_juggernaut():
    """The tank on the ravager layout at 0.8x: riveted brass plates over the whole hull, a soot face-plate with glass
    eyes and a furnace grille for a mouth, iron horns, soot knees and iron feet, a boiler drum glowing on the back."""
    s = Skin(RAVAGER, (128, 128), 713)
    plate_sheet(s)
    for part in ("head", "neck", "body", "belly"):
        s.fill(part, BRASS, 4)
        s.vshade(part, 1.06, 0.84)
        for f in SIDES:
            rivets(s, part, f, step=4, oy=1)
    # the face-plate: soot with glass eyes, a furnace grille low on the front
    x, y, w, h = s.face("head", "front")
    for i in range(w):
        for j in range(h):
            s.put(x + i, y + j, s.n(SOOT, 3))
    put_rows(s, "head", "front", ["................", "................", "................", "................",
                                  "..GG........GG..", "..GL........LG..", "................", "................",
                                  "................", "................", "................", "................",
                                  "....SSSSSSSS....", "....SFAFAFAS....", "....SFFFFFFS....", "....SSSSSSSS...."],
             {"G": GLASS, "L": sh(GLASS, 1.15), "S": SOOT_D, "F": FLAME, "A": FLAME_L})
    s.fill("mouth", SOOT_D, 3)
    s.fill("horn", IRON, 3)
    s.vshade("horn", 1.1, 0.8)
    # the boiler drum on the back: the body's top face soot ringed with brass, a glowing hatch in the middle
    x, y, w, h = s.face("body", "top")
    for i in range(w):
        for j in range(h):
            s.put(x + i, y + j, s.n(BRASS_L if (i in (0, w - 1) or j in (0, h - 1)) else SOOT, 3))
    for i in range(w // 2 - 2, w // 2 + 2):
        for j in range(h // 2 - 2, h // 2 + 2):
            s.put(x + i, y + j, FLAME if (i + j) % 2 else FLAME_L)
    for leg in ("leg_r", "leg_l"):
        s.fill(leg, BRASS, 4)
        s.band(leg, 14, 18, SOOT)
        s.band(leg, 31, 37, IRON)
        s.fill(leg, IRON_D, 3, which=("bottom",))
        rivets(s, leg, "front", step=4, oy=2)
    return s


def gyrocopter_gunship():
    """The gunship on the parrot layout at 1.6x: a soot-black hull with brass bands, brass rotor-wings, a long gun-barrel
    beak in iron, a glass canopy and a furnace-orange exhaust on the tail."""
    s = Skin(PARROT, (32, 32), 714)
    W, H = s.im.size
    for x in range(W):
        for y in range(H):
            s.put(x, y, s.n(SOOT if (x + y) % 5 else BRASS_D, 4))
    for part in ("body", "head", "head2"):
        s.fill(part, SOOT, 3)
        s.vshade(part, 1.08, 0.85)
    s.band("body", 2, 3, BRASS)
    s.band("body", 4, 5, BRASS)
    s.fill("head2", BRASS, 3)          # the rotor hub
    s.fpx("head", "front", 0, 0, GLASS); s.fpx("head", "front", 1, 0, GLASS)
    s.fpx("head", "front", 0, 1, sh(GLASS, 0.8)); s.fpx("head", "front", 1, 1, sh(GLASS, 0.8))
    s.fill("wing", BRASS, 4)
    s.vshade("wing", 1.05, 0.85)
    s.fill("tail", SOOT_D, 3)
    s.fpx("tail", "back", 1, 2, FLAME); s.fpx("tail", "back", 1, 3, FLAME_L)
    s.fill("beak", IRON_D, 2)
    s.fill("beak2", IRON, 2)
    s.fill("leg", SOOT_D, 2)
    return s


def drill_burrower():
    """The raider on the silverfish layout at 2.5x: brass rings with darker seams on every segment, an iron drill head
    on the front segment, furnace-orange vents on the back plates."""
    s = Skin(SILVERFISH, (64, 32), 715)
    plate_sheet(s, band=2)
    for k in range(7):
        part = f"seg{k}"
        s.fill(part, BRASS if k % 2 else BRASS_D, 4)
        s.vshade(part, 1.06, 0.84)
    s.fill("seg0", IRON, 4)
    s.fill("seg1", IRON_D, 4)
    for f in SIDES:
        rivets(s, "seg2", f, step=2, oy=1)
    for k, part in enumerate(("plate0", "plate1", "plate2")):
        s.fill(part, SOOT if k != 1 else BRASS, 3)
        x, y, w, h = s.face(part, "top")
        for i in range(1, w - 1, 3):
            for j in range(1, h - 1, 2):
                s.put(x + i, y + j, FLAME if (i + j) % 2 else FLAME_L)
    return s


def repair_crawler():
    """The support on the spider layout (cave spider at 0.65x): a brass shell with a green repair lamp on the back,
    copper welding arms for legs with iron tips, glass eyes."""
    s = Skin(SPIDER, (64, 32), 716)
    plate_sheet(s, a=COPPER, b=COPPER_D)
    for part in ("head", "neck", "abdomen"):
        s.fill(part, BRASS, 4)
        s.vshade(part, 1.06, 0.84)
    put_rows(s, "head", "front", ["........", "........", ".GG..GG.", ".GL..LG.", "........", "..SSSS..", "........", "........"],
             {"G": GLASS, "L": sh(GLASS, 1.2), "S": SOOT})
    x, y, w, h = s.face("abdomen", "top")
    for i in range(w):
        for j in range(h):
            s.put(x + i, y + j, s.n(BRASS_L if (i in (0, w - 1) or j in (0, h - 1)) else BRASS, 3))
    for i in range(w // 2 - 2, w // 2 + 2):
        for j in range(h // 2 - 2, h // 2 + 2):
            s.put(x + i, y + j, LAMP_GL if (i + j) % 2 else LAMP_G)
    s.fill("leg", COPPER, 4)
    x, y, w, h = s.face("leg", "front")
    for j in range(h):
        s.put(x, y + j, IRON); s.put(x + 1, y + j, IRON)
    return s


def clockwork_spider():
    """The turret walker on the spider layout: a riveted brass abdomen with the twin-barrelled soot turret on top, a
    soot head with furnace-orange lamps, jointed legs in soot with brass knuckles."""
    s = Skin(SPIDER, (64, 32), 717)
    plate_sheet(s, a=SOOT, b=SOOT_L)
    s.fill("abdomen", BRASS, 4)
    s.vshade("abdomen", 1.06, 0.84)
    for f in SIDES:
        rivets(s, "abdomen", f, step=3, oy=1)
    x, y, w, h = s.face("abdomen", "top")
    for i in range(w):
        for j in range(h):
            s.put(x + i, y + j, s.n(BRASS, 3))
    for i in range(w // 2 - 2, w // 2 + 2):          # the turret ring
        for j in range(2, h - 2):
            s.put(x + i, y + j, s.n(SOOT, 3))
    for j in range(0, 4):                              # the twin barrels poking forward
        s.put(x + w // 2 - 2, y + j, IRON); s.put(x + w // 2 + 1, y + j, IRON)
    s.fill("head", SOOT, 3)
    put_rows(s, "head", "front", ["........", "........", ".FF..FF.", ".FA..AF.", "........", "..IIII..", "........", "........"],
             {"F": FLAME, "A": FLAME_L, "I": IRON})
    s.fill("neck", BRASS_D, 3)
    s.fill("leg", SOOT, 4)
    x, y, w, h = s.face("leg", "front")
    for i in range(0, w, 5):
        for j in range(h):
            s.put(x + i, y + j, BRASS)
    return s


UNITS = {
    "tinker": (tinker, "villager"),
    "rifle_squad": (rifle_squad, "villager"),
    "flak_cart": (flak_cart, "villager"),
    "mortar_crawler": (mortar_crawler, "villager"),
    "shield_projector": (shield_projector, "villager"),
    "sapper": (sapper, "villager"),
    "iron_warden": (iron_warden, "golem"),
    "steam_runner": (steam_runner, "strider"),
    "gyro_scout": (gyro_scout, "parrot"),
    # slice 2
    "engineer_captain": (engineer_captain, "villager"),
    "tesla_walker": (tesla_walker, "villager"),
    "steam_juggernaut": (steam_juggernaut, "ravager"),
    "gyrocopter_gunship": (gyrocopter_gunship, "parrot"),
    "drill_burrower": (drill_burrower, "silverfish"),
    "repair_crawler": (repair_crawler, "spider"),
    "clockwork_spider": (clockwork_spider, "spider"),
}

# ------------------------------------------------------------------ portraits (16x16 art, saved at 4x = 64x64)
BG_T, BG_B = (70, 44, 24), (24, 16, 12)   # one foundry-glow backdrop for the whole faction
FRAME = (201, 138, 46)                    # a brass frame
ICONS = {}


def icon(fn):
    ICONS[fn.__name__.replace("icon_", "")] = fn
    return fn


W_ = (236, 236, 228)
PAL = {"Y": BRASS_L, "B": BRASS, "D": BRASS_D, "S": SKIN_RUDDY, "W": W_, "E": (60, 50, 40), "N": sh(SKIN_RUDDY, 0.88),
       "K": SOOT_D, "O": SOOT, "L": LEATHER, "R": BEARD_RED, "G": BEARD_GREY, "H": BEARD_BROWN, "C": GLASS, "F": FLAME,
       "A": FLAME_L, "I": IRON, "P": COPPER, "T": SOOT_L}


@icon
def icon_tinker():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....LLLLLLLL....",
           "...LBCCLLCCBL...",
           "...LBCCLLCCBL...",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....RRRNNRRR....",
           "....RRRRRRRR....",
           ".....RRRRRR.....",
           "..OOOLLLLLLOOO..",
           ".OOOOLLBBLLOOOO.",
           ".BOOOLLLLLLOOOB.",
           ".BOOOLLLLLLOOOB.",
           ".LLLLLLLLLLLLLL.",
           ".KKKKKKKKKKKKKK."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_gyro_scout():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "..PPPPPPPPPPPP..",
           "......KKKK......",
           "......KKKK......",
           ".....BBBBBB.....",
           "....BBCCBBBB....",
           "...BBBCCBBBBB...",
           "..PPBBBBBBBBPP..",
           ".PPPBBBBBBBBPPP.",
           "..PPBBBFABBBPP..",
           "...BBBBFBBBBB...",
           "....BBBBBBBB....",
           ".....BBBBBB.....",
           "......KKKK......",
           ".....KK..KK.....",
           "................"], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_steam_runner():
    i = Icon(BG_T, BG_B)
    i.art(["......KK........",
           "......KK..W.....",
           "....BBBBBBBB....",
           "...BBBBBBBBBB...",
           "...BDDDDDDDDB...",
           "...BBKKKKKKBB...",
           "...BBKFAAFKBB...",
           "...BBKFFFFKBB...",
           "...BBKKKKKKBB...",
           "...BBBBBBBBBB...",
           "....BBBBBBBB....",
           ".....KK..KK.....",
           ".....KK..KK.....",
           ".....BB..BB.....",
           ".....KK..KK.....",
           "....III..III...."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_rifle_squad():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....TTTTTTTT....",
           "...TTTTYTTTTT...",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....GGGNNGGG....",
           "....GGGGGGGG....",
           ".....GGGGGG.....",
           "..OOOOOYOOOOOO..",
           ".OOOOOOYOOOOOOK.",
           ".LOOOOOYOOOOOKK.",
           ".LOOOOOYOOOOKKI.",
           ".OOOOOOOOOOKKI..",
           ".KKKKKKKKKKKKK..",
           ".KKKKKKKKKKKKKK."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_iron_warden():
    i = Icon(BG_T, BG_B)
    i.art(["....BBBBBBBB....",
           "...BBBBBBBBBB...",
           "...BKCCBBCCKB...",
           "...BBBBBBBBBB...",
           "....BBBKKBBB....",
           ".BBBBBBBBBBBBBB.",
           "BBYBBBKKKKBBBYBB",
           "BBBBBBKFFKBBBBBB",
           "BBBBBBKAAKBBBBBB",
           "BBYBBBKFFKBBBYBB",
           "BBBBBBKKKKBBBBBB",
           "KKKBBBBBBBBBBKKK",
           "III.BBKKKKBB.III",
           "III.BBBBBBBB.III",
           "....BBB..BBB....",
           "....III..III...."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_flak_cart():
    i = Icon(BG_T, BG_B)
    i.art(["..........BB....",
           ".........BB.....",
           "....OOOOBB......",
           "...OKCCKKCC.....",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....HHHNNHHH....",
           "....HHHHHHHH....",
           ".....HHHHHH.....",
           "..LLLBBBBBBLLL..",
           ".LLLLBDCCDBLLLL.",
           ".BLLLBDCCDBLLLB.",
           ".BLLLBBBBBBLLLB.",
           ".LLLLLLLLLLLLLL.",
           ".KKKKKKKKKKKKKK."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_mortar_crawler():
    i = Icon(BG_T, BG_B)
    i.art(["..........BBB...",
           ".........BBBB...",
           "....BBBBBBBB....",
           "...BBBBYBBBB....",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....HHHNNHHH....",
           "....HHHHHHHH....",
           ".....HHHHHH.....",
           "..OOOLLLLLLOOO..",
           ".OOOOLLFFLLOOOO.",
           ".BOOOLLAALLOOOB.",
           ".BOOOLLLLLLOOOB.",
           ".LLLLLLLLLLLLLL.",
           ".KKKKKKKKKKKKKK."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_shield_projector():
    i = Icon(BG_T, BG_B)
    i.art(["......CCCC......",
           "....CC....CC....",
           "...CLLLLLLLLC...",
           "...LBCCLLCCBL...",
           "..C.SSSSSSSS.C..",
           "..C.SWESSEWS.C..",
           "..C.SSSNNSSS.C..",
           "..C.GGGNNGGG.C..",
           "...CGGGGGGGGC...",
           "...C.GGGGGG.C...",
           "..OOOOBCCBOOOO..",
           ".OOOOOBCCBOOOOO.",
           ".BOOOOBBBBOOOOB.",
           ".BOOOOOOOOOOOOB.",
           ".OOOOOOOOOOOOOO.",
           ".KKKKKKKKKKKKKK."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_sapper():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....OOOOOOOO....",
           "...OOOOAOOOOO...",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....RRRNNRRR....",
           "....RRRRRRRR....",
           ".....RRRRRR.....",
           "..LLLOOOOOOLLL..",
           ".LLLLOOOOOOLLLL.",
           ".LLLLOBOBOBLLLL.",
           ".LLLLOOOOOOLLLL.",
           ".LLLLOOOOOOLLLA.",
           ".LLLLLLLLLLLLLF.",
           ".KKKKKKKKKKKKKK."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_deployed_turret():
    # the Guildmaster's temporary turret: a brass musket on a soot tripod with a riveted shield plate (no face:
    # nobody crews it, it is a summon, but its portrait and queue tile still blit mobheads/deployed_turret.png)
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           "................",
           "..........YYYYY.",
           "......BBBBBBBDDK",
           ".....YBBBBBBB...",
           "....YBDDDBBK....",
           "....BDYDYDBK....",
           "....BDDDDDBK....",
           ".....BBBBBK.....",
           ".......II.......",
           "......IOOI......",
           ".....IO..OI.....",
           "....IO....OI....",
           "...KO......OK...",
           "..KK........KK.."], PAL)
    i.frame(FRAME)
    return i


# slice 2 portraits: the Assembly Works' T2
PAL.update({"V": TESLA_BLUE, "U": TESLA_L, "M": LAMP_G, "Q": LAMP_GL})


@icon
def icon_engineer_captain():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....TTTTTTTT....",
           "...TTTTYYTTTT...",
           "...DDDDDDDDDD...",
           "....SCCSSCCS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....GGGNNGGG....",
           "....GGGGGGGG....",
           ".....GGGGGG.....",
           "..YYYOOOOOOYYY..",
           ".YYYOYOOOOYOYYY.",
           ".OOOOOOOOOOOOOO.",
           ".OOOOYOOOOYOOOO.",
           ".OOOOOOOOOOOOOO.",
           ".KKKKKKKKKKKKKK."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_tesla_walker():
    i = Icon(BG_T, BG_B)
    i.art(["......U.U.......",
           ".......U........",
           "......UYU.......",
           "......PYP.......",
           "......PYP.......",
           "....OOOOOOOO....",
           "....SVVSSVVS....",
           "....SSSNNSSS....",
           "....HHHNNHHH....",
           "....HHHHHHHH....",
           "..PPPKKKKKKPPP..",
           ".PPPKPKPKPKKPPP.",
           ".KKKKVVVVVVKKKK.",
           ".KKKKKKKKKKKKKK.",
           ".KKKKKKKKKKKKKK.",
           ".OOOOOOOOOOOOOO."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_steam_juggernaut():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".II..........II.",
           ".II..........II.",
           "..IBBBBBBBBBBI..",
           "..BKKKKKKKKKKB..",
           "..BKCCKKKKCCKB..",
           "..BKKKKKKKKKKB..",
           "..BKKKKKKKKKKB..",
           "..BKKFAFAFAKKB..",
           "..BKKFFFFFFKKB..",
           "..BBBBBBBBBBBB..",
           ".YBBBYBBBBYBBBY.",
           ".BBBBBBBBBBBBBB.",
           ".DDDDDDDDDDDDDD.",
           "..KK..KK..KK..K.",
           "..II..II..II..I."], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_gyrocopter_gunship():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".YYYYYYBYYYYYYY.",
           ".......B........",
           "......OOO.......",
           ".....OCCOO......",
           "....OOOOOOO.....",
           "IIIIOOBBOOOO....",
           "....OOOOOOOOO...",
           ".....BBBBBBOOO..",
           ".....OOOOOOOOOFA",
           "......OOOOO.....",
           ".......K.K......",
           "......KK.KK.....",
           "................",
           "................",
           "................"], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_drill_burrower():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           "................",
           ".......FAF......",
           "....DBBBBBBD....",
           "..IDBBBBBBBBD...",
           ".IIBBYBBYBBYBD..",
           "IIIBBBBBBBBBBBD.",
           ".IIBBYBBYBBYBD..",
           "..IDBBBBBBBBD...",
           "....DBBBBBBD....",
           "................",
           "...LLLLLLLLLLL..",
           "..LLLLLLLLLLLLL.",
           "................",
           "................"], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_repair_crawler():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           "................",
           "......MQQM......",
           ".....BMQQMB.....",
           "...YBBBBBBBBY...",
           "..BBBBBBBBBBBB..",
           ".PBBBBBBBBBBBBP.",
           "P.BBCCBBBBCCBB.P",
           "P..BBBBBBBBBB..P",
           ".P.P..P..P..P.P.",
           ".I.P..P..P..P.I.",
           "...I..I..I..I...",
           "................",
           "................",
           "................"], PAL)
    i.frame(FRAME)
    return i


@icon
def icon_clockwork_spider():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "......I..I......",
           "......I..I......",
           ".....OOOOOO.....",
           ".....OKKKKO.....",
           "...YBBBBBBBBY...",
           "..BBBYBBBBYBBB..",
           ".OBBBBBBBBBBBBO.",
           "O..BBBBBBBBBB..O",
           "O.O.OFAOOAFO.O.O",
           ".O.O.OOOOOO.O.O.",
           ".Y.Y..O..O..Y.Y.",
           ".O.O........O.O.",
           "O...O......O...O",
           "................",
           "................"], PAL)
    i.frame(FRAME)
    return i


def preview(outdir, skins, icons):
    os.makedirs(outdir, exist_ok=True)
    SC = 6
    tiles = []
    for name, (s, kind) in skins.items():
        if kind == "villager":
            tiles.append((V.front_mock(s, kind), V.front_mock(s, kind, back=True)))
    sheet = Image.new("RGBA", (max(len(tiles) * (40 * SC + 30), 900), 36 * SC + 420), (90, 110, 120, 255))
    for k, (fm, bm) in enumerate(tiles):
        sheet.alpha_composite(fm, (k * (40 * SC + 30) + 4, 4))
        sheet.alpha_composite(bm, (k * (40 * SC + 30) + 20 * SC + 12, 4))
    for k, n in enumerate(icons):
        sheet.alpha_composite(icons[n].im.resize((64, 64), Image.NEAREST), (k * 72 + 4, 36 * SC + 20))
    y = 36 * SC + 100
    for name in ("iron_warden", "steam_runner", "gyro_scout"):
        im = skins[name][0].im
        sheet.alpha_composite(im.resize((im.width * 2, im.height * 2), Image.NEAREST), (4 + 270 * (y // 10000), y))
        y += im.height * 2 + 8
        if y > sheet.height - 40:
            break
    sheet.convert("RGB").save(os.path.join(outdir, "brass_skins_sheet.png"))


def main():
    out_prev = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    skins = {}
    for name, (fn, kind) in UNITS.items():
        s = fn()
        s.im.save(os.path.join(ENT, f"{name}_unit.png"))
        skins[name] = (s, kind)
        print(f"textures/entities/{name}_unit.png {s.im.size}")
    icons = {}
    for name, fn in ICONS.items():
        ic = fn()
        ic.save(os.path.join(HEADS, f"{name}.png"))
        icons[name] = ic
        print(f"textures/mobheads/{name}.png")
    if out_prev:
        preview(out_prev, skins, icons)
        print("preview in", out_prev)


if __name__ == "__main__":
    main()
