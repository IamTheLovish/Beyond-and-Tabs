#!/usr/bin/env python3
"""Draws the Tidewrought's unit skins and portrait icons (original pixel art, no source textures).

Reuses the skin toolkit of tools/gen_verdant_skins.py (box layouts, Skin, faces, robe/arms/legs helpers, Icon) so the
two factions are painted the same way; only the palette and the units differ. Every skin keeps the UV layout of the
model its renderer uses:
  - VillagerUnitModel (64x64): Shipwright (and the Admiral), Cutlass Raider, Reef Guard, Bombard Crew. Its head's
    8x12x8 "hat" box (32,0) is drawn wherever it is opaque: the corsairs use it for tricornes, a kerchief, a helmet.
  - vanilla WitchModel (64x128): Tide Priest (the pointed hat becomes a tall kelp mitre).
The Gull Spotter keeps Alex's Mobs' own seagull texture (RenderSeagull); it only gets a portrait here.

Art direction (claude/design-factions.md, the Tidewrought): corsairs and tide-priests in teal and brass - sea-teal
cloth, brass buckles and plate, weathered timber browns, kelp green, coral accents. Like the Court, the top faces (hats,
shoulders) carry the strongest shapes because the RTS camera looks down, with strong value steps and light noise.

usage: python3 tools/gen_tide_skins.py [--preview DIR]
  writes bar/.../textures/entities/<unit>_unit.png and bar/.../textures/mobheads/<unit>.png (16x16 art at 4x);
  --preview also writes a contact sheet (front/back mock-ups and the portraits) into DIR.
"""
import importlib.util, math, os, random, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("gen_verdant_skins", os.path.join(HERE, "gen_verdant_skins.py"))
V = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(V)

Skin, VILLAGER, WITCH, SIDES, Icon = V.Skin, V.VILLAGER, V.WITCH, V.SIDES, V.Icon
sh, villager_face, robe, arms, legs, shoulder_tops = V.sh, V.villager_face, V.robe, V.arms, V.legs, V.shoulder_tops
ENT, HEADS = V.ENT, V.HEADS

# ------------------------------------------------------------------ palette
TEAL_D, TEAL, TEAL_L = (16, 74, 72), (30, 127, 122), (72, 186, 176)
SEA = (60, 200, 190)
BRASS_D, BRASS, BRASS_L = (120, 88, 30), (201, 162, 62), (240, 212, 120)
WOOD_D, WOOD, WOOD_L = (54, 40, 30), (96, 70, 48), (140, 106, 72)
KELP_D, KELP, KELP_L = (30, 58, 30), (56, 98, 44), (98, 140, 60)
CORAL, CORAL_L = (220, 96, 92), (246, 150, 136)
SLATE_D, SLATE = (40, 48, 54), (70, 82, 90)
LINEN, LINEN_D = (222, 214, 190), (170, 160, 134)
RED, RED_D = (170, 40, 40), (110, 26, 26)
BLACK = (26, 26, 30)
SKIN_SUN, SKIN_BRONZE, SKIN_PALE, SKIN_DEEP = (214, 168, 124), (176, 126, 86), (232, 204, 176), (120, 84, 60)


def tricorne(s, c, trim, band=None):
    """A three-cornered hat on the hat box: brim on the top face with the three points, a crown, a trim line."""
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(c, 4))
    # the points: a lighter trim round the brim and three upturned corners (front-left, front-right, back)
    for i in range(8):
        s.fpx("hat", "top", i, 0, trim)
        s.fpx("hat", "top", i, 7, sh(c, 0.8))
    for j in range(8):
        s.fpx("hat", "top", 0, j, trim)
        s.fpx("hat", "top", 7, j, trim)
    for (i, j) in ((0, 0), (7, 0), (3, 7), (4, 7)):
        s.fpx("hat", "top", i, j, sh(trim, 1.15))
    # the crown sits on the head's upper rows round all four sides
    for f in SIDES:
        for i in range(8):
            for j in range(0, 3):
                s.fpx("hat", f, i, j, s.n(c, 4))
            s.fpx("hat", f, i, 3, trim)
    if band:
        for f in SIDES:
            for i in range(8):
                s.fpx("hat", f, i, 2, band)


def boots_and_breeches(s, cloth, boot, buckle=None):
    legs(s, cloth, boot)
    if buckle:
        for f in ("front",):
            s.fpx("leg", f, 1, 8, buckle)
            s.fpx("leg", f, 2, 8, buckle)


# ------------------------------------------------------------------ the units (villager model)
def shipwright():
    """The worker (and the Admiral, who adds the livery's brass cap and teal coat): a sea-teal smock, a leather apron
    with brass buckles and a mallet loop, rolled linen sleeves, a red kerchief knotted on the head (hat box top)."""
    s = Skin(VILLAGER, (64, 64), 51)
    villager_face(s, SKIN_SUN, (60, 110, 120), WOOD_D, hair=WOOD, beard=WOOD)
    robe(s, TEAL, TEAL_D, sash=8, sash_c=WOOD_D)
    # the leather apron down the front of the smock, brass buckles at the sash
    for j in range(9, 17):
        for i in range(2, 6):
            s.fpx("jacket", "front", i, j, s.n(WOOD_L, 4))
    for i in range(2, 6):
        s.fpx("jacket", "front", i, 16, WOOD)
    s.fpx("jacket", "front", 2, 8, BRASS_L); s.fpx("jacket", "front", 5, 8, BRASS_L)
    s.fpx("jacket", "front", 1, 10, BRASS); s.fpx("jacket", "front", 6, 10, BRASS)   # tool loops
    arms(s, LINEN, TEAL_D, SKIN_SUN)
    boots_and_breeches(s, WOOD, WOOD_D, BRASS)
    shoulder_tops(s, TEAL_L)
    # the kerchief: red on the crown (top face of the hat box) with a knot falling down the back
    for i in range(1, 7):
        for j in range(1, 7):
            s.fpx("hat", "top", i, j, s.n(RED, 5))
    for i in range(1, 7):
        s.fpx("hat", "front", i, 1, RED_D)
        s.fpx("hat", "front", i, 2, RED)
    for f in ("right", "left", "back"):
        for i in range(1, 7):
            s.fpx("hat", f, i, 2, RED)
    for j in range(2, 6):
        s.fpx("hat", "back", 3, j, RED_D); s.fpx("hat", "back", 4, j, RED)
    return s


def cutlass_raider():
    """The raider: a striped linen shirt under a teal sash, a black tricorne with a brass trim (the strongest shape
    from above), a red sash knot, bronzed sailor's skin and an eye patch."""
    s = Skin(VILLAGER, (64, 64), 52)
    villager_face(s, SKIN_BRONZE, (40, 40, 40), BLACK, hair=BLACK)
    for i in (1, 2):
        s.fpx("head", "front", i, 4, BLACK)          # the eye patch
    for i in range(4):
        s.fpx("head", "front", i, 3, BLACK)          # its strap across the brow
    # striped shirt
    s.fill("body", LINEN, 4)
    s.fill("jacket", LINEN, 4)
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for j in range(0, 12, 2):
            for i in range(w):
                s.fpx("jacket", f, i, j, s.n(TEAL_D, 3))
        x, y, w, h = s.face("body", f)
        for j in range(0, 10, 2):
            for i in range(w):
                s.fpx("body", f, i, j, s.n(TEAL_D, 3))
    # the sash and breeches below it
    s.band("jacket", 9, 11, RED)
    s.band("body", 9, 11, RED)
    s.fpx("jacket", "left", 2, 11, RED_D); s.fpx("jacket", "left", 2, 12, RED_D)
    s.band("jacket", 11, 18, TEAL_D)
    s.band("jacket", 17, 18, BLACK)
    s.vshade("jacket", 1.05, 0.82)
    arms(s, LINEN, TEAL, SKIN_BRONZE, bracer=WOOD_D)
    boots_and_breeches(s, TEAL_D, BLACK, BRASS)
    shoulder_tops(s, LINEN, accent=TEAL_D)
    tricorne(s, BLACK, BRASS)
    return s


def reef_guard():
    """The line: brass plate over a teal gambeson, studded with coral; a brass morion helmet (hat box) with a coral
    crest along the top - from above, a gold dome with a red ridge."""
    s = Skin(VILLAGER, (64, 64), 53)
    villager_face(s, SKIN_SUN, (50, 80, 90), WOOD_D, hair=WOOD_D, beard=WOOD)
    s.fill("body", BRASS, 5)
    s.fill("jacket", TEAL, 4)
    # the breastplate: brass over the upper robe, a darker rim and rivets, coral growing on it
    s.band("jacket", 0, 9, BRASS)
    s.band("jacket", 8, 9, BRASS_D)
    for f in SIDES:
        for i in (1, 6):
            s.fpx("jacket", f, i, 2, BRASS_L)
            s.fpx("jacket", f, i, 6, BRASS_L)
    for (i, j) in ((2, 3), (3, 4), (5, 2), (5, 5)):
        s.fpx("jacket", "front", i, j, CORAL)
    s.fpx("jacket", "front", 3, 3, CORAL_L)
    # tassets of teal quilting below
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for i in range(w):
            if i % 2 == 0:
                for j in range(10, 17):
                    s.fpx("jacket", f, i, j, TEAL_D)
    s.band("jacket", 17, 18, BRASS_D)
    s.vshade("jacket", 1.08, 0.8)
    arms(s, TEAL, BRASS, SKIN_SUN, bracer=BRASS_D)
    boots_and_breeches(s, TEAL_D, BRASS_D)
    # pauldrons: brass shoulder tops with a coral stud
    shoulder_tops(s, BRASS_L, accent=CORAL)
    for part in ("arm_up", "arm"):
        s.band(part, 0, 3, BRASS)
    # the morion: brass dome on the crown, brim round the sides, coral crest front to back
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(BRASS, 5))
    for j in range(8):
        s.fpx("hat", "top", 3, j, CORAL); s.fpx("hat", "top", 4, j, CORAL_L if j % 2 else CORAL)
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 0, BRASS_L)
            s.fpx("hat", f, i, 1, BRASS)
            s.fpx("hat", f, i, 2, BRASS_D)
    s.fpx("hat", "front", 3, 0, CORAL); s.fpx("hat", "front", 4, 0, CORAL)
    return s


def bombard_crew():
    """The gunner: a slate coat with brass buttons, a powder belt of little casks, a soot-smudged face and a brass
    gunner's cap with a teal band."""
    s = Skin(VILLAGER, (64, 64), 54)
    villager_face(s, SKIN_PALE, (70, 70, 80), SLATE_D, hair=WOOD)
    for (i, j) in ((5, 6), (6, 7), (1, 7)):
        s.fpx("head", "front", i, j, sh(SKIN_PALE, 0.7))   # soot
    robe(s, SLATE, SLATE_D, trim=BRASS_D)
    for j in (2, 5, 8):
        s.fpx("jacket", "front", 3, j, BRASS_L); s.fpx("jacket", "front", 4, j, BRASS_L)
    # the powder belt: a bandolier of casks across the chest, a dark belt at the waist
    for j in range(9):
        i = 7 - min(7, j)
        s.fpx("jacket", "front", i, j, WOOD_D)
        if j % 3 == 1:
            s.fpx("jacket", "front", i, j, WOOD_L)
    s.band("jacket", 9, 10, BLACK)
    s.fpx("jacket", "front", 3, 9, BRASS); s.fpx("jacket", "front", 4, 9, BRASS)
    arms(s, SLATE, TEAL, SKIN_PALE, bracer=WOOD_D)
    boots_and_breeches(s, SLATE_D, BLACK)
    shoulder_tops(s, sh(SLATE, 1.2), accent=BRASS)
    # the cap: brass crown with a teal band (no brim: the crew sights along the barrel)
    for i in range(1, 7):
        for j in range(1, 7):
            s.fpx("hat", "top", i, j, s.n(BRASS, 5))
    s.fpx("hat", "top", 3, 3, BRASS_L); s.fpx("hat", "top", 4, 4, BRASS_L)
    for f in SIDES:
        for i in range(1, 7):
            s.fpx("hat", f, i, 0, BRASS)
            s.fpx("hat", f, i, 1, TEAL)
    return s


def tide_priest():
    """The support: kelp-green over sea-teal robes with a shell belt and a coral pendant; the witch hat recut as a
    tall kelp mitre with a brass band and a pale shell at its tip (a big readable cone from above)."""
    s = Skin(WITCH, (64, 128), 55)
    SHELL, SHELL_D = (236, 226, 206), (190, 172, 150)
    villager_face(s, SKIN_DEEP, (90, 210, 200), KELP_D, hair=KELP_D)
    s.fill("mole", CORAL, 0)
    robe(s, TEAL, TEAL_D)
    # the overrobe of kelp strands over the upper body, hanging in fronds
    s.band("jacket", 0, 8, KELP)
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for i in range(w):
            for j in range(8, 8 + (i * 5) % 6):
                s.fpx("jacket", f, i, j, s.n(KELP_D, 4))
    # shell belt and a coral pendant
    s.band("jacket", 11, 12, WOOD_D)
    for (f, i) in (("front", 1), ("front", 4), ("front", 6), ("right", 2), ("left", 2), ("back", 3)):
        s.fpx("jacket", f, i, 11, SHELL)
    s.fpx("jacket", "front", 3, 5, CORAL); s.fpx("jacket", "front", 4, 5, CORAL_L); s.fpx("jacket", "front", 3, 6, CORAL)
    s.band("jacket", 18, 20, SEA)
    arms(s, KELP, SEA, SKIN_DEEP)
    legs(s, TEAL_D, WOOD_D)
    shoulder_tops(s, KELP_L, accent=SEA)
    # the mitre: brim teal, cone kelp with a brass band, tip a pale shell
    s.fill("hat1", TEAL, 4)
    x, y, w, h = s.face("hat1", "top")
    for i in range(w):
        for j in range(h):
            if i in (0, w - 1) or j in (0, h - 1):
                s.put(x + i, y + j, SEA)
    s.fill("hat2", KELP, 4)
    s.band("hat2", 3, 4, BRASS)
    s.fill("hat3", KELP_D, 4)
    s.band("hat3", 0, 1, BRASS_L)
    s.fill("hat4", SHELL, 0)
    s.fpx("hat2", "front", 3, 1, SHELL_D)
    return s


OILSKIN_D, OILSKIN, OILSKIN_L = (150, 112, 24), (217, 178, 58), (244, 214, 110)
ROPE, ROPE_D = (190, 160, 110), (130, 104, 66)


def harpooner():
    """Slice 2 skirmisher, the whaler: a yellow oilskin coat and sou'wester (the brightest top in the faction, so a
    Harpooner is found at a glance), a rope coil slung across the chest, teal breeches, a weathered bearded face."""
    s = Skin(VILLAGER, (64, 64), 56)
    villager_face(s, SKIN_SUN, (50, 90, 100), WOOD_D, hair=WOOD_D, beard=LINEN_D)
    robe(s, OILSKIN, OILSKIN_D, trim=OILSKIN_D)
    # the coat's toggles and a darker hem; the rope coil runs shoulder to hip across the front
    for j in (2, 5, 8):
        s.fpx("jacket", "front", 3, j, WOOD_D)
    for j in range(10):
        i = min(7, j)
        s.fpx("jacket", "front", i, j, ROPE if j % 2 else ROPE_D)
        if i + 1 < 8:
            s.fpx("jacket", "front", i + 1, j, ROPE_D)
    s.band("jacket", 17, 18, OILSKIN_D)
    s.vshade("jacket", 1.06, 0.84)
    arms(s, OILSKIN, OILSKIN_D, SKIN_SUN, bracer=WOOD_D)
    boots_and_breeches(s, TEAL_D, BLACK)
    shoulder_tops(s, OILSKIN_L, accent=ROPE)
    # the sou'wester: a wide brim on the hat box top, a crown of oilskin, the long back flap
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(OILSKIN, 5))
    for i in range(8):
        s.fpx("hat", "top", i, 0, OILSKIN_L)
        s.fpx("hat", "top", i, 7, OILSKIN_D)
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 0, OILSKIN)
            s.fpx("hat", f, i, 1, OILSKIN_D)
    for i in range(8):
        for j in range(2, 6):
            s.fpx("hat", "back", i, j, s.n(OILSKIN, 4))
    return s


def flak_swivel():
    """Slice 2 anti-air, the gun crew: a short teal jacket with brass buttons, a cartridge belt of brass shells across
    the waist, round brass goggles pushed up on a leather cap (from above, a dark cap with two bright lenses)."""
    s = Skin(VILLAGER, (64, 64), 57)
    villager_face(s, SKIN_BRONZE, (40, 60, 70), BLACK, hair=BLACK)
    robe(s, TEAL, TEAL_D, trim=BRASS_D)
    for j in (1, 3, 5, 7):
        s.fpx("jacket", "front", 2, j, BRASS_L); s.fpx("jacket", "front", 5, j, BRASS_L)
    # the cartridge belt: brass shells standing in a dark leather band
    s.band("jacket", 9, 11, WOOD_D)
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for i in range(0, w, 2):
            s.fpx("jacket", f, i, 9, BRASS_L)
            s.fpx("jacket", f, i, 10, BRASS)
    s.band("jacket", 11, 18, SLATE)
    s.vshade("jacket", 1.05, 0.82)
    arms(s, TEAL, BRASS_D, SKIN_BRONZE, bracer=WOOD_D)
    boots_and_breeches(s, SLATE_D, BLACK, BRASS)
    shoulder_tops(s, TEAL_L, accent=BRASS)
    # the leather cap and its goggles: two brass rings with sea-glass lenses on the front of the crown and its top
    for i in range(1, 7):
        for j in range(1, 7):
            s.fpx("hat", "top", i, j, s.n(WOOD, 5))
    for f in SIDES:
        for i in range(1, 7):
            s.fpx("hat", f, i, 0, WOOD)
            s.fpx("hat", f, i, 1, WOOD_D)
    for (i0, lens) in ((1, SEA), (4, SEA)):
        s.fpx("hat", "front", i0, 0, BRASS_L); s.fpx("hat", "front", i0 + 1, 0, BRASS_L)
        s.fpx("hat", "front", i0, 1, lens); s.fpx("hat", "front", i0 + 1, 1, BRASS)
        s.fpx("hat", "top", i0, 6, BRASS_L); s.fpx("hat", "top", i0 + 1, 6, lens)
    return s


# ------------------------------------------------------------------ slice 3: the Drydock's units
NAVY_D, NAVY, NAVY_L = (16, 30, 54), (30, 52, 88), (62, 92, 136)
ABYSS_D, ABYSS, ABYSS_L = (22, 16, 40), (44, 32, 74), (84, 64, 130)
SUCKER = (214, 150, 190)
PEARL = (236, 240, 244)


def harbourmaster():
    """Slice 3 T2 constructor: the Shipwright's body in a long navy harbour coat with two rows of brass buttons and brass
    epaulettes (the shoulders are what the RTS camera sees), a white peaked cap with a black peak and a brass anchor
    badge, a neat grey beard - the yard's master, told from a Shipwright at a glance."""
    s = Skin(VILLAGER, (64, 64), 58)
    villager_face(s, SKIN_PALE, (50, 90, 110), SLATE, hair=LINEN_D, beard=LINEN_D)
    robe(s, NAVY, NAVY_D, sash=8, sash_c=BLACK)
    for j in range(0, 18):
        s.fpx("jacket", "front", 3, j, sh(NAVY, 0.8))      # the coat's opening
    for j in (1, 3, 5, 7, 10, 12):
        s.fpx("jacket", "front", 2, j, BRASS_L)
        s.fpx("jacket", "front", 5, j, BRASS_L)
    s.fpx("jacket", "front", 3, 8, BRASS); s.fpx("jacket", "front", 4, 8, BRASS)   # the belt buckle
    s.band("jacket", 16, 18, BRASS_D)
    arms(s, NAVY, BRASS, SKIN_PALE)
    boots_and_breeches(s, NAVY_D, BLACK, BRASS)
    shoulder_tops(s, BRASS, accent=BRASS_L)
    # the peaked cap: a white crown on the hat box top, a black band and peak at the front, the brass badge
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(LINEN, 3))
    for i in range(8):
        s.fpx("hat", "top", i, 0, BLACK)                   # the peak, seen from above
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 0, LINEN)
            s.fpx("hat", f, i, 1, BLACK)
    s.fpx("hat", "front", 3, 0, BRASS_L); s.fpx("hat", "front", 4, 0, BRASS_L)
    s.fpx("hat", "top", 3, 2, BRASS); s.fpx("hat", "top", 4, 2, BRASS)
    return s


def kraken_caller():
    """Slice 3 controller: deep-sea robes of abyssal violet over teal, a mantle of tentacles with pale suckers falling
    over the shoulders and down the front (the top view is a ring of curling arms), a dark hood whose face glows sea-teal,
    a pearl clasp at the throat."""
    s = Skin(VILLAGER, (64, 64), 59)
    villager_face(s, SKIN_DEEP, SEA, ABYSS_D, hair=ABYSS_D)
    robe(s, ABYSS, ABYSS_D, trim=TEAL)
    # the tentacle mantle: arms of violet hanging to different lengths round the robe, suckers in pale pink
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for i in range(w):
            if i % 3 == 1:
                continue
            ln = 5 + (i * 7 + len(f)) % 6
            for j in range(ln):
                s.fpx("jacket", f, i, j, s.n(ABYSS_L, 5))
            if i % 3 == 0:
                for j in range(1, ln, 2):
                    s.fpx("jacket", f, i, j, SUCKER)
    s.fpx("jacket", "front", 3, 1, PEARL); s.fpx("jacket", "front", 4, 1, PEARL)
    s.band("jacket", 16, 18, TEAL_D)
    s.vshade("jacket", 1.04, 0.84)
    arms(s, ABYSS, TEAL, SKIN_DEEP, bracer=ABYSS_L)
    legs(s, ABYSS_D, BLACK)
    shoulder_tops(s, ABYSS_L, accent=SUCKER)
    # the hood: dark over the hat box with a sea-glow round the face opening, curling arms on its crown
    V.hood(s, "hat", ABYSS_D, (60, 200, 190), collar=ABYSS, point=True)
    for (i, j) in ((1, 1), (2, 2), (5, 1), (6, 2), (3, 5), (4, 6), (1, 5), (6, 5)):
        s.fpx("hat", "top", i, j, ABYSS_L)
    s.fpx("hat", "top", 2, 1, SUCKER); s.fpx("hat", "top", 5, 2, SUCKER)
    return s


def sky_corsair():
    """Slice 3 bomber (interim ghast body, 64x32 ghast UV): the cube is a canvas balloon of teal and linen gores, belted
    with brass at its waist, a timber gondola band with brass portholes round its lower edge, a linen skull-and-bones on
    the front, and the ghast's tentacles drawn as hanging rope rigging. From above: gores radiating from a brass crown."""
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = im.load()
    rng = random.Random(60)

    def n(c, a=5):
        k = rng.randint(-a, a)
        return tuple(max(0, min(255, v + k)) for v in c[:3]) + (255,)

    # the tentacle UV (box 2 x len x 2 at 0,0): ropes with a darker twist every third row
    for x in range(0, 8):
        for y in range(0, 16):
            px[x, y] = n(ROPE_D if (y + x) % 3 == 0 else ROPE, 4)

    # the body box (16 cube at 0,0): top (16,0), bottom (32,0), right (0,16), front (16,16), left (32,16), back (48,16)
    def side(ox, oy, emblem=False):
        for i in range(16):
            for j in range(16):
                c = TEAL if (i // 4) % 2 == 0 else LINEN       # the gores
                if j in (8, 9):
                    c = BRASS if j == 8 else BRASS_D          # the brass belt
                if j >= 12:
                    c = WOOD if j < 15 else WOOD_D             # the gondola band
                px[ox + i, oy + j] = n(sh(c, 1.06 - j * 0.015))
        for i in (3, 7, 11):                                   # portholes in the gondola
            px[ox + i, oy + 13] = n(BRASS_L, 3)
            px[ox + i + 1, oy + 13] = n(SEA, 3)
        if emblem:   # a linen skull over crossed bones on the front
            art = ["..XXXX..", ".XXXXXX.", ".XBXXBX.", ".XXXXXX.", "..XBBX..", "X..XX..X", ".XX..XX."]
            for jj, row in enumerate(art):
                for ii, ch in enumerate(row):
                    if ch == "X":
                        px[ox + 4 + ii, oy + 1 + jj] = n(PEARL, 3)
                    elif ch == "B":
                        px[ox + 4 + ii, oy + 1 + jj] = n(BLACK, 2)
    side(0, 16)
    side(16, 16, emblem=True)
    side(32, 16)
    side(48, 16)
    # the top: gores radiating from a brass crown ring
    for i in range(16):
        for j in range(16):
            dx, dz = i - 7.5, j - 7.5
            a = (math.atan2(dz, dx) + math.pi) / (2 * math.pi)
            c = TEAL if int(a * 8) % 2 == 0 else LINEN
            if dx * dx + dz * dz < 5:
                c = BRASS_L if dx * dx + dz * dz < 2 else BRASS
            px[16 + i, j] = n(sh(c, 1.08))
    # the bottom: the gondola's deck planks
    for i in range(16):
        for j in range(16):
            px[32 + i, j] = n(WOOD_D if j % 4 == 0 else WOOD, 4)
    return im


UNITS = {
    "shipwright": (shipwright, "villager"),
    "cutlass_raider": (cutlass_raider, "villager"),
    "reef_guard": (reef_guard, "villager"),
    "bombard_crew": (bombard_crew, "villager"),
    "tide_priest": (tide_priest, "witch"),
    "harpooner": (harpooner, "villager"),
    "flak_swivel": (flak_swivel, "villager"),
    "harbourmaster": (harbourmaster, "villager"),
    "kraken_caller": (kraken_caller, "villager"),
}
# textures for non-illager models (written as they are, no contact-sheet mock-up)
RAW_TEXTURES = {
    "sky_corsair": sky_corsair,
}

# ------------------------------------------------------------------ portraits (16x16 art, saved at 4x = 64x64)
BG_T, BG_B = (28, 70, 78), (10, 26, 34)   # one deep-sea dusk backdrop for the whole faction
FRAME = (150, 122, 58)                     # a brass frame

ICONS = {}


def icon(fn):
    ICONS[fn.__name__.replace("icon_", "")] = fn
    return fn


@icon
def icon_shipwright():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           ".....RRRRRR.....",
           "....RRRRRRRR....",
           "....RHHHHHHR..R.",
           "....HSSSSSSH.RR.",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SBSNNSBS....",
           "....SBBBBBBS....",
           ".....BBBBBB.....",
           "..TTTTTTTTTTTT..",
           ".TTTTAAAAAATTTT.",
           ".TTTTAYAAYATTTT.",
           ".TTTTAAAAAATTTT.",
           "................"],
          {"R": RED, "H": WOOD, "S": SKIN_SUN, "W": (236, 236, 228), "E": (60, 110, 120), "N": sh(SKIN_SUN, 0.88),
           "B": WOOD_L, "T": TEAL, "A": WOOD_L, "Y": BRASS_L})
    i.frame(FRAME)
    return i


@icon
def icon_gull_spotter():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           "................",
           "......WWW.......",
           ".....WWWWW......",
           ".....WEWWWOO....",
           ".....WWWWWOY....",
           "GG...WWWWW....GG",
           ".GGG.WWWWW..GGG.",
           "..GGGWWWWWGGGG..",
           "...GGGWWWGGGG...",
           ".....GWWWG......",
           "......WWW.......",
           ".......W........",
           "................",
           "................"],
          {"W": (240, 240, 236), "E": BLACK, "O": (240, 180, 40), "Y": (220, 60, 40), "G": (150, 160, 170)})
    i.frame(FRAME)
    return i


@icon
def icon_cutlass_raider():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....BBBBBBBB....",
           "..BBYYYYYYYYBB..",
           "...BBBBBBBBBB...",
           "....HSSSSSSH....",
           "....SKKSSEWS....",
           "....SKKSSSSS....",
           "....SSSNNSSS....",
           ".....SSNNSS...C.",
           ".....SSSSSS..C..",
           "..LLLLLLLLLLC...",
           ".LTTLLLLLLTCLL..",
           ".LLLRRRRRRCLLL..",
           ".TTTTTTTTTTTTT..",
           ".LLLLLLLLLLLLL..",
           "................"],
          {"B": BLACK, "Y": BRASS, "H": BLACK, "S": SKIN_BRONZE, "K": BLACK, "E": (40, 40, 40), "W": (236, 236, 228),
           "N": sh(SKIN_BRONZE, 0.88), "L": LINEN, "T": TEAL_D, "R": RED, "C": (210, 216, 222)})
    i.frame(FRAME)
    return i


@icon
def icon_reef_guard():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".......CC.......",
           ".....YYCCYY.....",
           "....YYYYYYYY....",
           "...DDDDDDDDDD...",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SHSNNSHS....",
           ".....HHHHHH.....",
           "..YYYYYYYYYYYY..",
           ".YYYYCYYYYYYYYY.",
           ".YYYYYYYCYYYYYY.",
           ".TYYYYYYYYYYYYT.",
           ".TTTTTTTTTTTTTT.",
           "................"],
          {"C": CORAL, "Y": BRASS, "D": BRASS_D, "S": SKIN_SUN, "W": (236, 236, 228), "E": (50, 80, 90),
           "N": sh(SKIN_SUN, 0.88), "H": WOOD, "T": TEAL})
    i.frame(FRAME)
    return i


@icon
def icon_bombard_crew():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           ".....YYYYYY.....",
           "....YYYLYYYY....",
           "....TTTTTTTT....",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SSDNNSDS....",
           ".....SSSSSS.....",
           "..GGGGGGGGGGGG..",
           ".GGGGGGYYGGGWGG.",
           ".GGGGGWGGGGWGGG.",
           ".GGGGWGGYYWGGGG.",
           ".GGGWGGGGWGGGGG.",
           "................"],
          {"Y": BRASS, "L": BRASS_L, "T": TEAL, "S": SKIN_PALE, "W": (236, 236, 228), "E": (70, 70, 80),
           "N": sh(SKIN_PALE, 0.88), "D": sh(SKIN_PALE, 0.7), "G": SLATE})
    # the bandolier drawn over the coat in wood brown (the "W" diagonal above is re-tinted)
    for x in range(16):
        for y in range(10, 15):
            if i.px[x, y][:3] == (236, 236, 228):
                i.p(x, y, WOOD_L)
    i.frame(FRAME)
    return i


@icon
def icon_tide_priest():
    i = Icon(BG_T, BG_B)
    i.art(["......PP........",
           "......KK........",
           ".....KKKK.......",
           ".....YYYY.......",
           "....KKKKKK......",
           "..AAAAAAAAAA....",
           "....SSSSSSS.....",
           "....SWESEWS.....",
           "....SSSNSSS.....",
           ".....SSNSS......",
           "..KKKKKKKKKKK...",
           ".KKKKCKKKKKKK.P.",
           ".TTTKCCKKKTTTPP.",
           ".TTTTTTTTTTTTPP.",
           ".TTTTTTTTTTTTT..",
           "................"],
          {"P": (236, 226, 206), "K": KELP, "Y": BRASS, "A": SEA, "S": SKIN_DEEP, "W": (236, 236, 228),
           "E": (90, 210, 200), "N": sh(SKIN_DEEP, 0.88), "C": CORAL, "T": TEAL})
    i.frame(FRAME)
    return i


@icon
def icon_harpooner():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "...OOOOOOOOOO...",
           "..OOLLLLLLLLOO..",
           "....OOOOOOOO..H.",
           "....SSSSSSSS..H.",
           "....SWESSEWS..H.",
           "....SSSNNSSS..H.",
           "....SBBNNBBS..H.",
           ".....BBBBBB...H.",
           "..OOOOOOOOOOOOHO",
           ".OORROOOOOOOOOHO",
           ".OOORROOOOOOOOHO",
           ".OOOOORROOOOOOHO",
           ".TTTTTTRRTTTTTHT",
           ".TTTTTTTTTTTTTHT",
           "................"],
          {"O": OILSKIN, "L": OILSKIN_L, "S": SKIN_SUN, "W": (236, 236, 228), "E": (50, 90, 100),
           "N": sh(SKIN_SUN, 0.88), "B": LINEN_D, "R": ROPE, "T": TEAL_D, "H": (200, 206, 212)})
    i.frame(FRAME)
    return i


@icon
def icon_flak_swivel():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           ".....HHHHHH.....",
           "....YGYHHYGY....",
           "....HHHHHHHH....",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           ".....SSSSSS.....",
           "..TTTTTTTTTTTT..",
           ".TTYTTTTTTTTYTT.",
           ".TTTTTTTTTTTTTT.",
           ".KYKYKYKYKYKYKK.",
           ".DDDDDDDDDDDDDD.",
           ".DDDDDDDDDDDDDD.",
           "................"],
          {"H": WOOD, "Y": BRASS_L, "G": SEA, "S": SKIN_BRONZE, "W": (236, 236, 228), "E": (40, 60, 70),
           "N": sh(SKIN_BRONZE, 0.88), "T": TEAL, "K": WOOD_D, "D": SLATE})
    i.frame(FRAME)
    return i


@icon
def icon_mudskipper_sloop():
    # Alex's Mobs' mudskipper keeps its own texture (RenderMudskipper, scaled); this is only its portrait
    i = Icon(BG_T, BG_B)
    MUD_D, MUD, MUD_L = (70, 58, 40), (112, 94, 64), (150, 130, 92)
    i.art(["................",
           "................",
           "......EE..EE....",
           ".....EKE..EKE...",
           "....MMMMMMMMMM..",
           "...MLLMMMMMMMMM.",
           "..MLLLMMMMMMMMMM",
           "..MLLMMMMMMMMMMM",
           "..PPPPPPPPMMMMMM",
           "...PPPPPPMMMMMMD",
           "....MMMMMMMMMMD.",
           ".....FFMMMMMDD..",
           "....FF..FF......",
           "................",
           "AAAAAAAAAAAAAAAA",
           "AWAAAAWAAAAAWAAA"],
          {"E": MUD_L, "K": BLACK, "M": MUD, "L": MUD_L, "P": (226, 200, 160), "D": MUD_D, "F": MUD_D,
           "A": SEA, "W": (220, 240, 236)})
    i.frame(FRAME)
    return i


@icon
def icon_harbourmaster():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....WWWWWWWW....",
           "...WWWWWWWWWW...",
           "...KKKKYYKKKK...",
           "....KKKKKKKK....",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....GGSNNSGG....",
           "....GGGGGGGG....",
           "..YYNNNNNNNNYY..",
           ".YYNNYNNNNYNNYY.",
           ".NNNNNNNNNNNNNN.",
           ".NNNNYNNNNYNNNN.",
           ".NNNNNNNNNNNNNN.",
           "................"],
          {"W": LINEN, "K": BLACK, "Y": BRASS_L, "S": SKIN_PALE, "E": (50, 90, 110), "N": NAVY,
           "G": LINEN_D})
    i.frame(FRAME)
    return i


@icon
def icon_kraken_caller():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".....DDDDDD.....",
           "....DDDDDDDD....",
           "...DDGGGGGGDD...",
           "...DGSSSSSSGD...",
           "...DGSEESEESG...",
           "...DGSSSSSSGD...",
           "....DDSSSSDD....",
           "..A.DDDPPDDD.A..",
           ".AA.VVVVVVVV.AA.",
           ".A.AVAVVVVAVA.A.",
           ".AAVVAVVVVAVVAA.",
           "..AVVVVVVVVVVA..",
           "..VVAVVVVVVAVV..",
           ".VVVVVVVVVVVVVV.",
           "................"],
          {"D": ABYSS_D, "G": SEA, "S": SKIN_DEEP, "E": SEA, "P": PEARL, "V": ABYSS, "A": ABYSS_L})
    i.frame(FRAME)
    return i


@icon
def icon_mortar_galley():
    # Alex's Mobs' snapping turtle keeps its own texture (RenderAlligatorSnappingTurtle, scaled); this is its portrait
    i = Icon(BG_T, BG_B)
    SH_D, SH, SH_L = (52, 58, 40), (82, 92, 60), (120, 132, 86)
    i.art(["................",
           "......KKK.......",
           ".....KYYYK......",
           "......KYK.......",
           "....SSSYSSS.....",
           "...SLSSSSSLS....",
           "..SSSLSSSLSSS...",
           "..SLSSSLSSSLSHH.",
           "..SSSSSSSSSSSHEH",
           "...DDDDDDDDDDHHH",
           "...FF......FF...",
           "................",
           "AAAAAAAAAAAAAAAA",
           "AWAAAAAWAAAAAWAA",
           "AAAAAAAAAAAAAAAA",
           "................"],
          {"K": BLACK, "Y": BRASS, "S": SH, "L": SH_L, "D": SH_D, "H": (96, 92, 70), "E": (200, 160, 40),
           "F": SH_D, "A": SEA, "W": (220, 240, 236)})
    i.frame(FRAME)
    return i


@icon
def icon_sky_corsair():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....TTLLTTLL....",
           "...TTLLTTLLTT...",
           "..TTLLTWWLLTTL..",
           "..TTLLWBWBLTTL..",
           "..TTLLTWWLLTTL..",
           "..YYYYYYYYYYYY..",
           "..TTLLTTLLTTLL..",
           "...HHHHHHHHHH...",
           "...HGHHGHHGHH...",
           "....HHHHHHHH....",
           "....R..R..R.....",
           "....R..R..R.....",
           ".....K..K.......",
           "................",
           "................"],
          {"T": TEAL, "L": LINEN, "W": PEARL, "B": BLACK, "Y": BRASS_L, "H": WOOD, "G": SEA, "R": ROPE,
           "K": BLACK})
    i.frame(FRAME)
    return i


def preview(outdir, skins, icons):
    os.makedirs(outdir, exist_ok=True)
    SC = 6
    tiles = []
    for name, (s, kind) in skins.items():
        fm = V.front_mock(s, kind).resize((20 * SC, 36 * SC), Image.NEAREST)
        bm = V.front_mock(s, kind, back=True).resize((20 * SC, 36 * SC), Image.NEAREST)
        tiles.append((fm, bm))
    sheet = Image.new("RGBA", (len(tiles) * (40 * SC + 30), 36 * SC + 100), (90, 110, 120, 255))
    for k, (fm, bm) in enumerate(tiles):
        sheet.alpha_composite(fm, (k * (40 * SC + 30) + 4, 4))
        sheet.alpha_composite(bm, (k * (40 * SC + 30) + 20 * SC + 12, 4))
    for k, n in enumerate(icons):
        sheet.alpha_composite(icons[n].im.resize((64, 64), Image.NEAREST), (k * 72 + 4, 36 * SC + 20))
    sheet.convert("RGB").save(os.path.join(outdir, "tide_skins_sheet.png"))


def main():
    out_prev = None
    if "--preview" in sys.argv:
        out_prev = sys.argv[sys.argv.index("--preview") + 1]
    skins = {}
    for name, (fn, kind) in UNITS.items():
        s = fn()
        s.im.save(os.path.join(ENT, f"{name}_unit.png"))
        skins[name] = (s, kind)
        print(f"textures/entities/{name}_unit.png {s.im.size}")
    for name, fn in RAW_TEXTURES.items():
        im = fn()
        im.save(os.path.join(ENT, f"{name}_unit.png"))
        print(f"textures/entities/{name}_unit.png {im.size}")
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
