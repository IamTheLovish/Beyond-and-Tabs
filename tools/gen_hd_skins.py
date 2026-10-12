#!/usr/bin/env python3
"""High-detail unit skins (original procedural pixel art) for the villager-model units of every faction, with
owner-coloured team regions and portraits regenerated from the skins. See design/unit_skins.md.

Each unit below is its own painter on the toolkit in tools/hd_skinkit.py: 5-tone material ramps, wrapped material
textures (cloth folds, plate lames, mail, leather grain, fur, bone cracks, bark grain, lacquer gloss, scales), faction
emblems on the chest, and a TEAM region (tabard / sash / mantle / plume / cape / hood) written to
textures/entities/<unit>_unit_team.png. In game TeamColourLayer multiplies that grey-scale cloth by the owner's colour
(the same colour as the outline), so every unit carries its side on its body from the RTS camera.

Art direction (claude/design-factions.md):
  Sunforged   gold-trimmed white plate, sun banners          Gravebound  black, bone, soul-blue
  Ironhide    rust-red iron, bone plate, furs                Verdant     green / silver, living wood, moss, lanterns
  Tidewrought teal + brass, kelp, coral                      Stormcallers red lacquer, jade, gold
  Brassworks  brass, soot black, furnace orange
The RTS camera looks down, so the hat box (helmets, hoods, plumes) and the shoulder tops carry the strongest shapes;
team colour goes where it reads from above and from the side (shoulders, tabards, plumes, capes).

This generator supersedes the older per-faction painters for the units it lists (it writes the same texture paths,
so no renderer reference changes); re-running tools/gen_{verdant,tide,storm,brass,t2t3}_skins.py would overwrite
these with the older flat skins - run this one after them.

usage: python3 tools/gen_hd_skins.py [--only unit,unit] [--no-preview]
  writes textures/entities/<unit>_unit.png (+ _team.png), textures/mobheads/<unit>.png and the contact sheets
  design/skins_preview_<faction>.png.
"""
import os, sys
import importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("hd_skinkit", os.path.join(HERE, "hd_skinkit.py"))
K = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(K)

HD, ramp, tone, sh, mix = K.HD, K.ramp, K.tone, K.sh, K.mix
cloth, silk, plate, mail, leather, fur, hairm, bone, bark, lacquer, scales, quilt, skinmat, flat, stripes = (
    K.cloth, K.silk, K.plate, K.mail, K.leather, K.fur, K.hair, K.bone, K.bark, K.lacquer, K.scales, K.quilt, K.skinmat,
    K.flat, K.stripes)
SIDES, ALL = K.SIDES, K.ALL

# ------------------------------------------------------------------ palettes
WHITE_PLATE, GOLD, GOLD_D, ROYAL = (226, 224, 216), (214, 172, 56), (150, 108, 30), (62, 96, 182)
STEEL, IRON = (150, 156, 166), (96, 98, 104)
BLACK_CLOTH, BONE, SOUL, GRAVE_SKIN = (36, 34, 42), (214, 204, 176), (70, 206, 226), (120, 140, 112)
RUST, RUST_D, FUR_BROWN, PIG = (150, 62, 34), (96, 40, 26), (110, 82, 56), (222, 150, 130)
MOSS, LEAF, BARK_C, SILVER = (60, 104, 50), (96, 156, 66), (92, 64, 42), (186, 194, 202)
TEAL, BRASS, KELP, CORAL = (32, 128, 122), (198, 154, 62), (64, 104, 52), (226, 96, 86)
NAVY, LINEN = (34, 44, 74), (222, 214, 190)
LAC_RED, JADE, PAPER = (170, 34, 28), (48, 156, 108), (238, 232, 218)
SOOT, FURNACE, COPPER, LEATHER_C = (48, 44, 42), (240, 132, 40), (184, 104, 62), (112, 74, 46)
SKIN_FAIR, SKIN_WARM, SKIN_TAN, SKIN_DARK, SKIN_RUDDY = (232, 196, 160), (214, 164, 124), (182, 130, 92), (122, 84, 58), (220, 150, 120)

FACTIONS = {
    #             default team cloth, portrait bg top / bottom, frame
    "sunforged": ((58, 96, 190), (88, 118, 170), (30, 40, 70), (226, 186, 80)),
    "gravebound": ((60, 180, 200), (40, 56, 66), (12, 14, 20), (170, 210, 220)),
    "ironhide": ((160, 50, 32), (110, 58, 40), (34, 20, 16), (200, 150, 110)),
    "verdant": ((74, 140, 60), (52, 84, 56), (16, 30, 22), (180, 196, 200)),
    "tidewrought": ((28, 132, 128), (38, 90, 104), (10, 26, 40), (206, 166, 76)),
    "stormcallers": ((176, 36, 30), (120, 40, 36), (30, 12, 14), (220, 180, 70)),
    "brassworks": ((214, 120, 36), (92, 74, 60), (24, 20, 18), (206, 160, 70)),
}

UNITS = {}   # name -> (faction, painter, model kind)


def unit(faction, kind="villager"):
    def deco(fn):
        UNITS[fn.__name__] = (faction, fn, kind)
        return fn
    return deco


def new(name, faction, layout=K.VILLAGER, size=(64, 64)):
    return HD(name, layout=layout, size=size, team_default=FACTIONS[faction][0])


# ------------------------------------------------------------------ shared villager-model pieces
def head(s, skin, eyes=(70, 112, 64), brow=None, hair=None, style="short", beard=None, beard_style="full",
         glow=None, scar=False, paint=None, wrinkles=False):
    """The illager head: sculpted skin (lit forehead, shadowed temples and jaw), brows, two-tone eyes (or a glow),
    strand-textured hair and beard, optional scar / war paint / wrinkles, the long nose."""
    R = ramp(skin)
    s.paint("head", skinmat(skin))
    s.shade("head", 1.08, ("front",), rows=(0, 3))
    s.shade("head", 0.86, ("front",), where=lambda f, i, j, w, h: i in (0, 7) and j >= 3)
    s.shade("head", 0.9, ("front",), rows=(8, 10))
    if wrinkles:
        for i in (2, 3, 4, 5):
            s.fpx("head", "front", i, 1 + (i % 2), tone(R, 1.3))
    bc = brow or sh(hair or skin, 0.5)
    for i in (1, 2, 5, 6):
        s.fpx("head", "front", i, 3, bc)
    if glow:
        for i in (1, 6):
            s.fpx("head", "front", i, 4, sh(glow, 0.65))
        for i in (2, 5):
            s.fpx("head", "front", i, 4, mix(glow, (255, 255, 255), 0.35))
    else:
        for i, c in ((1, (234, 232, 222)), (2, eyes), (5, eyes), (6, (234, 232, 222))):
            s.fpx("head", "front", i, 4, c)
    for i in (1, 2, 5, 6):
        s.fpx("head", "front", i, 5, tone(R, 1.5))
    for i in (1, 6):
        s.fpx("head", "front", i, 6, mix(tone(R, 2), (214, 104, 96), 0.18))
    for i in (2, 5):
        s.fpx("head", "front", i, 9, tone(R, 0.9))
    if scar:
        for k in range(5):
            s.fpx("head", "front", 6 - k // 2, 2 + k, tone(R, 3.5))
    if paint:
        pc, style_p = paint
        P = ramp(pc)
        if style_p == "stripes":
            for i in (0, 1, 6, 7):
                s.fpx("head", "front", i, 6, tone(P, 2.2))
                s.fpx("head", "front", i, 7, tone(P, 1.6))
        elif style_p == "mask":
            for i in range(8):
                if i not in (2, 5):
                    s.fpx("head", "front", i, 4, tone(P, 1.8 if i in (0, 7) else 2.3))
                s.fpx("head", "front", i, 3, tone(P, 2.0))
        elif style_p == "dot":
            s.fpx("head", "front", 3, 1, tone(P, 2.6)); s.fpx("head", "front", 4, 1, tone(P, 2.2))
    if hair:
        H = hairm(hair)
        s.paint("head", H, ("top",))
        if style in ("short", "long", "topknot"):
            s.paint("head", H, ("back",), rows=(0, 10 if style == "long" else 7))
            s.paint("head", H, ("right", "left"), rows=(0, 3))
            s.paint("head", H, ("right",), rows=(3, 10 if style == "long" else 7), where=lambda f, i, j, w, h: i < 4)
            s.paint("head", H, ("left",), rows=(3, 10 if style == "long" else 7), where=lambda f, i, j, w, h: i > 3)
            s.paint("head", H, ("front",), rows=(0, 2))
            s.fpx("head", "front", 0, 2, tone(ramp(hair), 1.5)); s.fpx("head", "front", 7, 2, tone(ramp(hair), 1.5))
            if style == "long":
                s.paint("head", H, ("front",), rows=(2, 10), where=lambda f, i, j, w, h: i in (0, 7))
        elif style == "cropped":
            s.paint("head", H, ("front", "right", "left", "back"), rows=(0, 1))
    elif style == "bald":
        s.fpx("head", "top", 2, 2, tone(R, 3.6)); s.fpx("head", "top", 3, 2, tone(R, 3.2))
        s.fpx("head", "top", 2, 3, tone(R, 3.2))
    if beard:
        B = hairm(beard)
        if beard_style in ("full", "long"):
            s.paint("head", B, ("front",), rows=(7, 10))
            for i in (0, 1, 6, 7):
                s.fpx("head", "front", i, 6, B(s, "front", i, 6, 8, 10, i))
            s.paint("head", B, ("right",), rows=(5, 10), where=lambda f, i, j, w, h: i >= 5)
            s.paint("head", B, ("left",), rows=(5, 10), where=lambda f, i, j, w, h: i <= 2)
            s.paint("head", B, ("bottom",))
        elif beard_style == "moustache":
            for i in (1, 2, 5, 6):
                s.fpx("head", "front", i, 7, B(s, "front", i, 7, 8, 10, i))
            s.fpx("head", "front", 1, 8, B(s, "front", 1, 8, 8, 10, 1)); s.fpx("head", "front", 6, 8, B(s, "front", 6, 8, 8, 10, 6))
        elif beard_style == "goatee":
            s.paint("head", B, ("front",), rows=(8, 10), where=lambda f, i, j, w, h: 2 <= i <= 5)
    if "nose" in s.L:
        s.paint("nose", skinmat(sh(skin, 0.97)))
        s.shade("nose", 0.82, ("front",), rows=(3, 4))
        s.shade("nose", 1.1, ("front",), rows=(0, 1))


def beard_chest(s, beard, rows=4):
    """A long beard flowing onto the chest (after the garment)."""
    s.paint("jacket", hairm(beard), ("front",), rows=(0, rows), where=lambda f, i, j, w, h: 1 <= i <= 6 and (j < rows - 1 or 2 <= i <= 5))


def robe(s, mat, rows=18, body=None, hem=None, hem_rows=2):
    s.paint("body", body or mat)
    s.paint("jacket", mat, ("top",) + SIDES, rows=(0, rows))
    if rows < 18:
        s.clear_part("jacket", SIDES, rows=(rows, 18))
        s.clear_part("jacket", ("bottom",))
    else:
        s.paint("jacket", mat, ("bottom",))
    if hem:
        s.band("jacket", rows - hem_rows, rows, hem)
    s.shade("jacket", 0.78, rows=(rows - 1, rows))
    s.shade("jacket", 0.85, rows=(0, 1))   # the collar's shadow under the chin


def open_robe(s, outer, inner, gap=(3, 5), rows=18):
    """A robe open down the front showing an under-layer."""
    robe(s, outer, rows)
    s.paint("jacket", inner, ("front",), rows=(1, rows), where=lambda f, i, j, w, h: gap[0] <= i < gap[1])
    s.shade("jacket", 0.7, ("front",), where=lambda f, i, j, w, h: i in (gap[0] - 1, gap[1]) and j > 0)


def sleeves(s, mat, hand, cuff=None, glove=None, bracer=None, bare_from=None):
    """Sleeves on the crossed arms (upper boxes + the forearm bar) and the free arms; cuffs, bracers, hands.
    bare_from: rows from which the free arm is bare skin (rolled sleeves)."""
    hands = glove or skinmat(hand)
    s.paint("arm_up", mat)
    if cuff:
        s.band("arm_up", 6, 8, cuff)
    if bracer:
        s.band("arm_up", 5, 8, bracer)
    if "arm" in s.L:   # the free arms (the witch model only has crossed arms)
        s.paint("arm", mat)
        if bare_from is not None:
            s.band("arm", bare_from, 12, skinmat(hand))
        if cuff:
            s.band("arm", 7, 9, cuff)
        if bracer:
            s.band("arm", 5, 9, bracer)
        s.band("arm", 9, 12, hands)
        s.paint("arm", hands, ("bottom",))
        s.shade("arm", 0.75, rows=(8, 9))
    # the crossed forearms: sleeve ends at both sides, hands meeting in the middle
    s.paint("arm_mid", bracer or mat)
    s.paint("arm_mid", hands, ("front", "top", "bottom"), where=lambda f, i, j, w, h: 2 <= i <= 5)
    if cuff:
        s.paint("arm_mid", cuff, ("front",), where=lambda f, i, j, w, h: i in (1, 6))
    s.shade("arm_mid", 0.8, ("front",), where=lambda f, i, j, w, h: i in (3, 4) and j == 3)


def pauldrons(s, mat, rows=3, team_edge=False, spikes=None):
    """Shoulder plates: top faces and the upper rows of both arm boxes - the RTS camera sees these first."""
    for p in [q for q in ("arm_up", "arm") if q in s.L]:
        s.paint(p, mat, ("top",))
        s.paint(p, mat, SIDES, rows=(0, rows))
        s.shade(p, 0.62, rows=(rows, rows + 1))
        if team_edge:
            s.team_paint(p, SIDES, rows=(rows - 1, rows), folds=0)
        if spikes:
            for (i, j) in ((1, 1), (2, 2)):
                s.fpx(p, "top", i, j, tone(ramp(spikes), 3.5))


def legs(s, mat, boot, boot_rows=4, knee=None, wrap=None):
    s.paint("leg", mat)
    if wrap:
        for j in range(1, 12 - boot_rows, 2):
            s.band("leg", j, j + 1, wrap)
    s.band("leg", 12 - boot_rows, 12, boot)
    s.paint("leg", boot, ("bottom",))
    s.shade("leg", 1.18, rows=(12 - boot_rows, 12 - boot_rows + 1))
    if knee:
        s.band("leg", 4, 6, knee)


def belt(s, j, mat, buckle=None, parts=("jacket", "body"), pouches=None):
    for p in parts:
        s.band(p, j, j + 2, mat)
        s.shade(p, 0.72, rows=(j + 2, j + 3))
    if buckle:
        B = ramp(buckle)
        for (i, jj, t) in ((3, 0, 3.5), (4, 0, 2.8), (3, 1, 2.4), (4, 1, 1.4)):
            s.fpx("jacket", "front", i, j + jj, tone(B, t))
    if pouches:
        P = ramp(pouches)
        for (f, i) in (("right", 2), ("left", 3), ("back", 2), ("back", 5)):
            for jj in range(3):
                s.fpx("jacket", f, i, j + 2 + jj, tone(P, 2.6 - jj * 0.6))


def strap(s, mat, width=1, reverse=False, j0=0, j1=14, studs=None):
    """A diagonal strap (baldric, quiver strap, bandolier) across the jacket front and back."""
    def on(f, i, j, w, h):
        if not (j0 <= j < j1) or f not in ("front", "back"):
            return False
        k = (j - j0) * 7.0 / max(1, (j1 - j0 - 1))
        x = (7 - k) if (reverse != (f == "back")) else k
        return abs(i - x) < 0.5 + (width - 1) * 0.6
    s.paint("jacket", mat, ("front", "back"), where=on)
    if studs:
        S = ramp(studs)
        for f, i, j, w, h, u, x, y in s.coords("jacket", ("front", "back"), (j0, j1)):
            if on(f, i, j, w, h) and j % 3 == 1:
                s.put(x, y, tone(S, 3.4))


# -- team regions
def tabard(s, j0=1, j1=15, cols=(2, 6), point=True, back=True, trim=None):
    def on(f, i, j, w, h):
        if not (j0 <= j < j1 and cols[0] <= i < cols[1]):
            return False
        return not (point and j == j1 - 1 and i in (cols[0], cols[1] - 1))
    s.team_paint("jacket", ("front", "back") if back else ("front",), where=on, folds=3)
    if trim:
        T = ramp(trim)
        for f in (("front", "back") if back else ("front",)):
            for j in range(j0, j1 - (1 if point else 0)):
                s.fpx("jacket", f, cols[0] - 1, j, tone(T, 2.8 if j % 3 else 3.5))
                s.fpx("jacket", f, cols[1], j, tone(T, 2.2))
            for i in range(cols[0], cols[1]):
                s.fpx("jacket", f, i, j0 - 1 if j0 > 0 else 0, tone(T, 3.0))


def sash(s, width=2, reverse=False, j0=0, j1=12, parts=("jacket",)):
    def on(f, i, j, w, h):
        if not (j0 <= j < j1) or f not in ("front", "back"):
            return False
        k = (j - j0) * 7.0 / max(1, (j1 - j0 - 1))
        x = (7 - k) if (reverse != (f == "back")) else k
        return abs(i - x) < width * 0.55
    for p in parts:
        s.team_paint(p, ("front", "back"), where=on, folds=0)


def waist_sash(s, j, rows=2, tail=True):
    s.team_paint("jacket", SIDES, rows=(j, j + rows), folds=0)
    if tail:   # the knot's tails hang at the left hip
        s.team_paint("jacket", ("left",), rows=(j + rows, j + rows + 5), where=lambda f, i, jj, w, h: i in (2, 3))


def mantle(s, rows=3, collar=True):
    for p in [q for q in ("arm_up", "arm") if q in s.L]:
        s.team_paint(p, ("top",), folds=0)
        s.team_paint(p, SIDES, rows=(0, rows), folds=0)
        s.shade(p, 0.7, rows=(rows, rows + 1))
    if collar:
        s.team_paint("jacket", ("front", "back", "right", "left"), rows=(0, 2), folds=0)


def cape(s, j1=17, body=True):
    s.team_paint("jacket", ("back",), rows=(0, j1), folds=3)
    s.team_paint("jacket", ("right",), rows=(0, j1), where=lambda f, i, j, w, h: i == 0)
    s.team_paint("jacket", ("left",), rows=(0, j1), where=lambda f, i, j, w, h: i == w - 1)
    if body:
        s.team_paint("body", ("back",), folds=3)


def plume(s, length=7, back_rows=4, cols=(3, 5)):
    s.team_paint("hat", ("top",), where=lambda f, i, j, w, h: cols[0] <= i < cols[1] and j < length, folds=0)
    s.team_paint("hat", ("back",), rows=(0, back_rows), where=lambda f, i, j, w, h: cols[0] <= i < cols[1], folds=0)
    s.team_paint("hat", ("front",), rows=(0, 1), where=lambda f, i, j, w, h: cols[0] <= i < cols[1], folds=0)


def sleeve_band(s, j0, j1):
    for p in [q for q in ("arm_up", "arm") if q in s.L]:
        s.team_paint(p, SIDES, rows=(j0, j1), folds=0)


def scarf(s, rows=2, tail_back=6):
    s.team_paint("jacket", SIDES, rows=(0, rows), folds=0)
    s.team_paint("jacket", ("back",), rows=(rows, rows + tail_back), where=lambda f, i, j, w, h: i in (5, 6), folds=0)


# -- hats (the 8x12x8 hat box, drawn wherever it is opaque)
def hood(s, mat=None, team=False, inner=(22, 18, 22), opening=(1, 6, 2, 9), point=True, trim=None, collar=True):
    s.clear_part("hat")
    if team:
        s.team_paint("hat", ALL, folds=3)
    else:
        s.paint("hat", mat)
    x0, x1, y0, y1 = opening
    for j in range(y0, y1 + 1):
        for i in range(x0, x1 + 1):
            s.fclear("hat", "front", i, j)
    for j in range(y0, y1 + 1):
        s.fpx("hat", "front", x0 - 1, j, inner); s.fpx("hat", "front", x1 + 1, j, inner)
    for i in range(x0 - 1, x1 + 2):
        s.fpx("hat", "front", i, y0 - 1, inner)
    if not collar:
        s.clear_part("hat", SIDES, rows=(10, 12))
    for i in range(1, 7):
        for j in range(1, 7):
            s.fclear("hat", "bottom", i, j)
    if trim:
        T = ramp(trim)
        for j in range(y0 - 1, 12 if collar else 10):
            s.fpx("hat", "front", x0 - 1 if j >= y0 else 0, j, tone(T, 3.0))
            s.fpx("hat", "front", x1 + 1 if j >= y0 else 7, j, tone(T, 2.2))
        for i in range(x0 - 1, x1 + 2):
            s.fpx("hat", "front", i, y0 - 1, tone(T, 3.3))
    if point:
        for j in range(1, 8):
            for i in (3, 4):
                p = s.fget("hat", "back", i, j)
                s.fpx("hat", "back", i, j, sh(p, 0.8), keep_team=True)


def helmet(s, mat, rows=4, style="kettle", crest=None, cheek=False, nasal=False, visor_c=(18, 16, 20), trim=None):
    s.clear_part("hat")
    s.paint("hat", mat, ("top",))
    if style == "great":
        s.paint("hat", mat, SIDES, rows=(0, 11))
        for i in range(1, 7):
            if i not in (3, 4):
                s.fpx("hat", "front", i, 4, visor_c)
        for i in (2, 5):
            s.fpx("hat", "front", i, 7, visor_c)
            s.fpx("hat", "front", i, 8, visor_c)
        for i in range(8):
            s.fpx("hat", "front", i, 5, tone(ramp(mat(s, "front", 0, 0, 8, 12, 0)), 1.2))
        for j in range(5, 11):
            s.fpx("hat", "front", 3, j, sh(s.fget("hat", "front", 3, j), 1.2))
        s.paint("hat", mat, ("bottom",), where=lambda f, i, j, w, h: i in (0, 7) or j in (0, 7))
    else:
        s.paint("hat", mat, SIDES, rows=(0, rows))
        s.shade("hat", 1.2, rows=(rows - 1, rows))   # the rolled brim catches the light
        if style == "sallet":   # the visor slit under the brow of the sallet
            s.shade("hat", 0.6, sides=("front",), rows=(rows - 1, rows), where=lambda f, i, j, w, h: 2 <= i <= 5)
        if cheek:
            s.paint("hat", mat, ("front",), rows=(rows, 9), where=lambda f, i, j, w, h: i in (0, 7))
            s.paint("hat", mat, ("right",), rows=(rows, 9), where=lambda f, i, j, w, h: i >= 5)
            s.paint("hat", mat, ("left",), rows=(rows, 9), where=lambda f, i, j, w, h: i <= 2)
        if nasal:
            s.paint("hat", mat, ("front",), rows=(rows, 7), where=lambda f, i, j, w, h: i in (3, 4))
        if style == "sallet":   # a long tail over the neck
            s.paint("hat", mat, ("back",), rows=(rows, 9))
            s.paint("hat", mat, ("right",), rows=(rows, 8), where=lambda f, i, j, w, h: i <= 2)
            s.paint("hat", mat, ("left",), rows=(rows, 8), where=lambda f, i, j, w, h: i >= 5)
    if trim:
        T = ramp(trim)
        for f in SIDES:
            w = s.face("hat", f)[2]
            for i in range(w):
                s.fpx("hat", f, i, rows - 1 if style != "great" else 0, tone(T, 3.0 if i % 2 else 2.4))
    if crest:
        C = ramp(crest)
        for j in range(8):
            for i in (3, 4):
                s.fpx("hat", "top", i, j, tone(C, 3.2 if i == 3 else 2.2))
        for j in range(0, 3):
            s.fpx("hat", "back", 3, j, tone(C, 2.6)); s.fpx("hat", "back", 4, j, tone(C, 1.8))
        s.fpx("hat", "front", 3, 0, tone(C, 3.2)); s.fpx("hat", "front", 4, 0, tone(C, 2.6))


def cap(s, mat, rows=2, band=None, badge=None, peak=None):
    s.clear_part("hat")
    s.paint("hat", mat, ("top",))
    s.paint("hat", mat, SIDES, rows=(0, rows))
    if band:
        s.band("hat", rows - 1, rows, band)
    if peak:
        s.paint("hat", peak, ("front",), rows=(rows, rows + 1))
    if badge:
        B = ramp(badge)
        s.fpx("hat", "front", 3, rows - 2 if rows > 1 else 0, tone(B, 3.5))
        s.fpx("hat", "front", 4, rows - 2 if rows > 1 else 0, tone(B, 2.6))


def circlet(s, metal, gem=None, row=2, clear=True):
    if clear:
        s.clear_part("hat")
    M = ramp(metal)
    for f in SIDES:
        w = s.face("hat", f)[2]
        for i in range(w):
            s.fpx("hat", f, i, row, tone(M, 3.2 if (i + row) % 3 == 0 else 2.3))
    if gem:
        G = ramp(gem)
        s.fpx("hat", "front", 3, row, tone(G, 3.4)); s.fpx("hat", "front", 4, row, tone(G, 2.0))
        s.fpx("hat", "front", 3, row - 1, tone(M, 3.0)); s.fpx("hat", "front", 4, row - 1, tone(M, 2.2))


def topknot(s, hair, pin):
    H = hairm(hair)
    s.paint("hat", H, ("top",), where=lambda f, i, j, w, h: 2 <= i <= 5 and 2 <= j <= 5)
    P = ramp(pin)
    s.fpx("hat", "top", 2, 3, tone(P, 3.5)); s.fpx("hat", "top", 5, 4, tone(P, 2.6))
    for f in SIDES:
        s.fpx("hat", f, 3, 0, H(s, f, 3, 0, 8, 12, 3)); s.fpx("hat", f, 4, 0, H(s, f, 4, 0, 8, 12, 4))


def goggles(s, rim, lens, row=1, down=False):
    """Brass-rimmed goggles on the hat box: a strap all round and two round lenses on the front."""
    R, Lr = ramp(rim), ramp(lens)
    r = 4 if down else row
    for f in SIDES:
        w = s.face("hat", f)[2]
        for i in range(w):
            s.fpx("hat", f, i, r, tone(ramp(LEATHER_C), 1.6))
    for cx in (1, 5):
        for (i, j, t) in ((0, -1, 3.4), (1, -1, 3.0), (0, 1, 1.6), (1, 1, 1.4), (-1, 0, 2.4), (2, 0, 2.0)):
            s.fpx("hat", "front", cx + i, r + j, tone(R, t))
        s.fpx("hat", "front", cx, r, tone(Lr, 3.6)); s.fpx("hat", "front", cx + 1, r, tone(Lr, 2.2))


def back_pack(s, rows, pal, oy=2):
    s.rows_map("jacket", "back", rows, pal, oy=oy)


# ======================================================================= SUNFORGED (white plate, gold, sun banners)
@unit("sunforged")
def vindicator():
    """Halberdier: full white plate with gold-edged lames over a mail skirt, a blue-and-team tabard with a gold sun,
    a sallet with a team plume (the strongest shape from above), a stern clean-shaven face."""
    s = new("vindicator", "sunforged")
    P = plate(WHITE_PLATE, lame=3, rivet=GOLD)
    head(s, SKIN_WARM, eyes=(60, 90, 140), hair=(92, 64, 40), style="cropped", scar=True)
    robe(s, P, rows=11, body=P)
    s.paint("jacket", mail(STEEL), SIDES, rows=(11, 16))
    s.shade("jacket", 0.75, rows=(15, 16))
    s.clear_part("jacket", SIDES, rows=(16, 18))
    tabard(s, j0=1, j1=16, cols=(2, 6), trim=GOLD)
    s.emblem("jacket", "front", 2, 3, "sun", GOLD)
    belt(s, 9, leather(LEATHER_C), buckle=GOLD)
    sleeves(s, P, SKIN_WARM, cuff=plate(GOLD, lame=2), glove=plate(STEEL, lame=2))
    pauldrons(s, plate(WHITE_PLATE, lame=2, rivet=GOLD), rows=3)
    for p in ("arm_up", "arm"):
        s.band(p, 2, 3, plate(GOLD, lame=1))
    legs(s, plate(WHITE_PLATE, lame=4), plate(STEEL, lame=2), knee=plate(GOLD, lame=2))
    helmet(s, plate(WHITE_PLATE, lame=4, rivet=GOLD), rows=4, style="sallet", trim=GOLD, nasal=True)
    plume(s, length=8, back_rows=5)
    return s


@unit("sunforged")
def pillager():
    """Crossbow Levy: a quilted gambeson in cream under a white breastplate, a kettle hat with a gold rim, a team
    sash across the chest, a bolt quiver strap, leather bracers; lighter kit than the Halberdier at a glance."""
    s = new("pillager", "sunforged")
    Q = quilt((214, 204, 176))
    head(s, SKIN_FAIR, eyes=(70, 110, 70), hair=(150, 104, 56), style="short", beard=(150, 104, 56), beard_style="moustache")
    robe(s, Q, rows=14)
    s.paint("jacket", plate(WHITE_PLATE, lame=3, rivet=GOLD), ("front", "right", "left"), rows=(1, 8),
            where=lambda f, i, j, w, h: f == "front" or 1 <= i <= 4)
    s.emblem("jacket", "front", 2, 2, "sun", GOLD)
    sash(s, width=2, j0=0, j1=13)
    strap(s, leather(LEATHER_C), reverse=True, j0=0, j1=13, studs=GOLD)
    belt(s, 9, leather((96, 64, 40)), buckle=GOLD, pouches=LEATHER_C)
    sleeves(s, Q, SKIN_FAIR, bracer=leather(LEATHER_C, stitch=True), glove=leather((90, 60, 38)))
    pauldrons(s, plate(WHITE_PLATE, lame=2), rows=2)
    legs(s, cloth((80, 92, 140), folds=3), leather((80, 54, 34)), boot_rows=4)
    helmet(s, plate(STEEL, lame=4), rows=3, style="kettle", trim=GOLD)
    # the quiver of bolts on the back
    back_pack(s, ["..BB....", "..WW....", ".LLLL...", ".LLLL...", ".LGGL...", ".LLLL...", ".LLLL..."],
              {"B": GOLD, "W": (236, 232, 220), "L": leather(LEATHER_C), "G": tone(ramp(GOLD), 3.2)}, oy=1)
    return s


@unit("sunforged")
def evoker():
    """Dome Cleric: white and gold vestments over a blue under-robe, a tall white mitre with a gold sun and team
    lappets down the back, a gold stole, a book on the belt; from above a bright white dome."""
    s = new("evoker", "sunforged")
    head(s, SKIN_FAIR, eyes=(120, 160, 200), hair=(200, 196, 188), style="short", beard=(220, 216, 206),
         beard_style="full", wrinkles=True)
    open_robe(s, silk((236, 232, 222)), cloth(ROYAL, folds=3), gap=(3, 5))
    s.band("jacket", 15, 18, plate(GOLD, lame=3))
    for f in ("front",):
        for j in range(1, 15):
            s.fpx("jacket", f, 2, j, tone(ramp(GOLD), 3.0 if j % 2 else 2.4))
            s.fpx("jacket", f, 5, j, tone(ramp(GOLD), 2.4 if j % 2 else 1.8))
    tabard(s, j0=1, j1=15, cols=(2, 6), point=False, back=True)
    s.paint("jacket", cloth(ROYAL, folds=3), ("front",), rows=(1, 15), where=lambda f, i, j, w, h: 3 <= i < 5)
    s.emblem("jacket", "back", 2, 4, "sun", GOLD)
    beard_chest(s, (220, 216, 206), rows=3)
    sleeves(s, silk((236, 232, 222)), SKIN_FAIR, cuff=plate(GOLD, lame=2))
    mantle(s, rows=2, collar=False)
    legs(s, cloth(ROYAL), leather((120, 96, 60)), boot_rows=3)
    # mitre: a tall white cap with a gold band and a gold sun in front
    s.clear_part("hat")
    s.paint("hat", silk((240, 236, 226)), ("top",))
    s.paint("hat", silk((240, 236, 226)), SIDES, rows=(0, 3))
    s.band("hat", 2, 3, plate(GOLD, lame=1))
    s.fpx("hat", "front", 3, 0, tone(ramp(GOLD), 3.5)); s.fpx("hat", "front", 4, 0, tone(ramp(GOLD), 2.6))
    s.fpx("hat", "front", 3, 1, tone(ramp(GOLD), 2.6)); s.fpx("hat", "front", 4, 1, tone(ramp(GOLD), 2.0))
    s.team_paint("hat", ("back",), rows=(3, 9), where=lambda f, i, j, w, h: i in (2, 5), folds=0)
    s.team_paint("hat", ("top",), where=lambda f, i, j, w, h: i in (3, 4), folds=0)
    return s


@unit("sunforged")
def royal_architect():
    """Royal Architect: an elder master-builder in white robes with a gold front trim, a blueprint satchel slung
    across the chest with rolled plans, a gold circlet with a sun-gem, a team mantle over the shoulders."""
    s = new("royal_architect", "sunforged")
    head(s, SKIN_WARM, eyes=(80, 100, 120), hair=(210, 206, 196), style="short", beard=(220, 216, 206), wrinkles=True)
    robe(s, cloth((232, 228, 216), folds=3), hem=plate(GOLD, lame=2))
    for j in range(0, 16):
        s.fpx("jacket", "front", 3, j, tone(ramp(GOLD), 3.0 if j % 2 else 2.5))
        s.fpx("jacket", "front", 4, j, tone(ramp(GOLD), 2.2))
    strap(s, leather(LEATHER_C), j0=0, j1=12)
    s.rows_map("jacket", "right", ["BBBB", "BWWB", "BBBB"], {"B": leather((120, 84, 50)), "W": (90, 130, 200)}, oy=10)
    belt(s, 9, leather((100, 70, 44)), buckle=GOLD, pouches=(150, 130, 90))
    s.emblem("jacket", "back", 2, 4, "sun", GOLD)
    sleeves(s, cloth((232, 228, 216)), SKIN_WARM, cuff=plate(GOLD, lame=2))
    mantle(s, rows=3)
    legs(s, cloth((180, 170, 150)), leather((96, 70, 44)), boot_rows=3)
    circlet(s, GOLD, gem=(240, 120, 40), row=2)
    return s


# ======================================================================= GRAVEBOUND (black, bone, soul-blue)
@unit("gravebound")
def zombie_villager():
    """Gravedigger: a grey-green corpse in a torn black smock with bone buttons, a soul-blue lantern glow in the eyes,
    a leather spade belt, a team shroud (hood-cowl) on the shoulders, stitched wounds."""
    s = new("zombie_villager", "gravebound")
    head(s, GRAVE_SKIN, glow=SOUL, hair=(52, 56, 48), style="cropped", scar=True)
    robe(s, cloth(BLACK_CLOTH, folds=3), rows=16, hem=cloth((60, 56, 64)))
    for j in range(13, 16):   # the hem is torn: notches show the legs
        for i in (1, 5):
            s.fclear("jacket", "front", i, j); s.fclear("jacket", "back", i + 1, j)
    for j in (3, 6, 9):
        s.fpx("jacket", "front", 3, j, tone(ramp(BONE), 3.4)); s.fpx("jacket", "front", 4, j, tone(ramp(BONE), 2.2))
    belt(s, 10, leather((70, 52, 36)), buckle=IRON)
    sleeves(s, cloth(BLACK_CLOTH), GRAVE_SKIN, bare_from=6)
    for (i, j) in ((1, 7), (2, 8), (1, 9)):   # stitches on the bare forearm
        s.fpx("arm", "front", i, j, (40, 30, 30))
    mantle(s, rows=3)
    legs(s, cloth((70, 66, 60)), leather((50, 40, 32)), boot_rows=3, wrap=cloth((150, 140, 120)))
    s.clear_part("hat")
    return s


@unit("gravebound")
def embalmer():
    """Embalmer: black robes under a waxed bone-white apron with bone buttons and a pocket of hooks, bandages round
    the crown and forearms, soul-blue eyes and hems, a soul lantern at the hip, a team stole."""
    s = new("embalmer", "gravebound")
    head(s, (126, 136, 118), glow=SOUL, style="bald")
    robe(s, cloth(BLACK_CLOTH, folds=4), hem=cloth((40, 120, 140)))
    s.paint("jacket", leather((206, 196, 168)), ("front",), rows=(2, 16), where=lambda f, i, j, w, h: 1 <= i <= 6)
    for j in (4, 8, 12):
        s.fpx("jacket", "front", 1, j, tone(ramp(BONE), 3.4)); s.fpx("jacket", "front", 6, j, tone(ramp(BONE), 3.4))
    s.rows_map("jacket", "front", ["IoIo", "oIoI"], {"I": (160, 166, 176), "o": (90, 80, 70)}, ox=2, oy=10)
    tabard(s, j0=0, j1=14, cols=(0, 1), point=False, back=False)
    s.team_paint("jacket", ("front",), rows=(0, 14), where=lambda f, i, j, w, h: i == 7, folds=0)
    belt(s, 8, leather((60, 44, 30)), buckle=BONE)
    s.rows_map("jacket", "left", ["..G..", ".GSG.", ".SSS.", ".GGG."], {"G": IRON, "S": SOUL}, oy=10)
    sleeves(s, cloth(BLACK_CLOTH), (126, 136, 118), bracer=stripes(cloth((210, 200, 176)), cloth((170, 160, 140)), 1))
    legs(s, cloth((40, 38, 44)), leather((50, 40, 32)), boot_rows=3)
    # bandages round the crown on the hat box
    s.clear_part("hat")
    s.paint("hat", stripes(cloth((214, 204, 180)), cloth((176, 166, 144)), 1), ("top",))
    s.paint("hat", stripes(cloth((214, 204, 180)), cloth((176, 166, 144)), 1), SIDES, rows=(0, 3))
    s.paint("hat", cloth((214, 204, 180)), ("front",), rows=(3, 6), where=lambda f, i, j, w, h: i in (5, 6, 7) and j != 4)
    return s


# ======================================================================= VERDANT COURT (green/silver, wood, moss, lanterns)
@unit("verdant")
def seedshaper():
    """Seedshaper (worker): a mossy hooded smock in leaf green over linen, a seed-gold sash with pouches, bark bracers,
    a sprouting hood tip; the hood is the team colour (the whole top reads as the owner from above)."""
    s = new("seedshaper", "verdant")
    head(s, SKIN_FAIR, eyes=(70, 130, 60), hair=(120, 86, 50), style="short")
    robe(s, cloth(MOSS, folds=3), rows=16, hem=bark(BARK_C))
    s.paint("jacket", cloth(LINEN), ("front",), rows=(1, 9), where=lambda f, i, j, w, h: 2 <= i <= 5)
    s.emblem("jacket", "front", 2, 2, "leaf", (80, 150, 60))
    belt(s, 9, cloth((200, 160, 60)), buckle=SILVER, pouches=(130, 96, 60))
    sleeves(s, cloth(MOSS), SKIN_FAIR, bracer=bark(BARK_C))
    legs(s, cloth((90, 74, 52)), bark((74, 52, 36)), boot_rows=4)
    hood(s, team=True, opening=(1, 6, 2, 9))
    s.fpx("hat", "top", 3, 3, tone(ramp(LEAF), 3.4)); s.fpx("hat", "top", 4, 4, tone(ramp(LEAF), 2.4))
    s.fpx("hat", "top", 4, 3, tone(ramp(LEAF), 2.8))
    return s


@unit("verdant")
def elder_druid():
    """Elder Druid (T2 worker): a white-bearded druid in a deep forest robe trimmed silver with moss growing up the
    hem, an antler circlet of bone, a moonstone at the brow, bark bracers with silver runes, a team stole."""
    s = new("elder_druid", "verdant")
    head(s, SKIN_WARM, eyes=(110, 150, 120), hair=(220, 218, 210), style="long", beard=(232, 230, 222), wrinkles=True)
    robe(s, cloth((36, 66, 40), folds=4), hem=cloth(MOSS))
    for f, i, j, w, h, u, x, y in s.coords("jacket", SIDES, (12, 18)):
        if K.h01(s.seed, u, j, 30) < 0.25 + (j - 12) * 0.1:
            s.put(x, y, tone(ramp(LEAF), 1.6 + K.h01(s.seed, u, j, 31) * 1.6))
    for j in range(18):
        s.fpx("jacket", "front", 0, j, tone(ramp(SILVER), 3.0)); s.fpx("jacket", "front", 7, j, tone(ramp(SILVER), 2.2))
    tabard(s, j0=4, j1=16, cols=(2, 6), point=True, back=False)
    beard_chest(s, (232, 230, 222), rows=5)
    sleeves(s, cloth((36, 66, 40)), SKIN_WARM, bracer=bark(BARK_C))
    for (i, j) in ((1, 6), (2, 7)):
        s.fpx("arm", "front", i, j, tone(ramp(SILVER), 3.5))
    legs(s, cloth((50, 40, 30)), bark((70, 50, 34)), boot_rows=3)
    circlet(s, BONE, gem=(180, 210, 240), row=1)
    # antlers: bone tines rising on the hat top corners and sides
    Bn = ramp(BONE)
    for (i, j) in ((0, 1), (1, 0), (0, 2), (7, 1), (6, 0), (7, 2), (1, 3), (6, 3)):
        s.fpx("hat", "top", i, j, tone(Bn, 3.2 if j < 2 else 2.4))
    for f in ("right", "left"):
        s.fpx("hat", f, 3, 0, tone(Bn, 3.0)); s.fpx("hat", f, 4, 0, tone(Bn, 2.4))
    return s


@unit("verdant")
def bloom_priestess():
    """Bloom Priestess: a linen-and-blossom robe with petals scattered down it, green sleeves, a pale team veil under
    a ring of pink flowers (the ring reads on the top face), a silver lantern at the hip."""
    s = new("bloom_priestess", "verdant")
    head(s, SKIN_FAIR, eyes=(90, 150, 90), hair=(200, 150, 80), style="long")
    robe(s, silk((236, 226, 210)), hem=cloth((220, 140, 170)))
    for f, i, j, w, h, u, x, y in s.coords("jacket", SIDES, (2, 15)):
        if K.h01(s.seed, u, j, 40) < 0.1:
            s.put(x, y, tone(ramp((236, 130, 170)), 2.8))
    waist_sash(s, 8)
    sleeves(s, cloth(LEAF), SKIN_FAIR, cuff=silk((236, 226, 210)))
    legs(s, cloth((200, 190, 170)), leather((150, 120, 90)), boot_rows=2)
    s.rows_map("jacket", "left", ["..S..", ".SLS.", ".LYL.", ".SSS."], {"S": SILVER, "L": (230, 240, 200), "Y": (255, 230, 120)}, oy=10)
    hood(s, team=True, opening=(1, 6, 1, 10), point=False)
    P = ramp((236, 120, 160))
    for (i, j) in ((0, 0), (2, 0), (5, 0), (7, 0), (0, 3), (7, 4), (0, 6), (7, 7), (2, 7), (5, 7)):
        s.fpx("hat", "top", i, j, tone(P, 3.3));
    for (i, j) in ((1, 0), (6, 0), (0, 5), (7, 2), (3, 7), (4, 7)):
        s.fpx("hat", "top", i, j, tone(ramp(LEAF), 2.4))
    for f in SIDES:
        for i in range(0, s.face("hat", f)[2], 2):
            s.fpx("hat", f, i, 0, tone(P, 3.0 if i % 4 else 2.2))
    return s


@unit("verdant")
def leafblade():
    """Leafblade: fitted bark-brown leathers, layered leaf pauldrons (on the shoulder tops), a team scarf mask over
    the lower face with its tails down the back, a cropped leaf crest, silver bracers, twin blade scabbards."""
    s = new("leafblade", "verdant")
    head(s, SKIN_TAN, eyes=(150, 200, 90), hair=(60, 44, 30), style="cropped")
    robe(s, leather(BARK_C), rows=13)
    s.paint("jacket", scales(LEAF, 2, 2), ("front", "back"), rows=(1, 8), where=lambda f, i, j, w, h: 1 <= i <= 6)
    belt(s, 9, leather((60, 44, 30)), buckle=SILVER)
    strap(s, leather((70, 50, 30)), j0=0, j1=12, studs=SILVER)
    sleeves(s, leather(BARK_C), SKIN_TAN, bracer=plate(SILVER, lame=2), glove=leather((60, 44, 30)))
    pauldrons(s, scales(LEAF, 2, 2), rows=3)
    for p in ("arm_up", "arm"):
        for (i, j) in ((0, 0), (3, 3), (1, 2)):
            s.fpx(p, "top", i, j, tone(ramp((140, 196, 84)), 3.2))
    legs(s, leather((70, 54, 38)), leather((50, 38, 26)), boot_rows=5)
    scarf(s, rows=2, tail_back=7)
    # the mask: team cloth over the lower face (head rows 6..9 drawn on the hat box so it stands off the face)
    s.clear_part("hat")
    s.team_paint("hat", ("front",), rows=(6, 10), folds=0)
    s.team_paint("hat", ("right",), rows=(6, 10), where=lambda f, i, j, w, h: i >= 4, folds=0)
    s.team_paint("hat", ("left",), rows=(6, 10), where=lambda f, i, j, w, h: i <= 3, folds=0)
    for (i, j) in ((3, 0), (4, 1), (3, 2), (4, 3), (3, 4)):
        s.fpx("hat", "top", i, j, tone(ramp(LEAF), 3.0))
    return s


@unit("verdant")
def hive_keeper():
    """Hive Keeper: a broad straw hat with a mesh veil, a honey-gold smock banded dark, a wax-sealed hive pot at the
    belt, moss gloves; team bands on the hat crown and the sleeves."""
    s = new("hive_keeper", "verdant")
    head(s, SKIN_RUDDY, eyes=(80, 70, 40), hair=(140, 96, 50), style="short", beard=(150, 100, 50), beard_style="goatee")
    robe(s, stripes(cloth((216, 168, 58)), cloth((70, 52, 30)), 3), rows=16)
    belt(s, 10, leather((80, 56, 34)), buckle=SILVER)
    s.rows_map("jacket", "front", [".WW.", "HHHH", "HhHH", ".HH."], {"W": (240, 220, 160), "H": (200, 140, 50), "h": (120, 80, 30)}, ox=2, oy=12)
    sleeves(s, cloth((216, 168, 58)), SKIN_RUDDY, glove=leather(MOSS))
    sleeve_band(s, 3, 5)
    legs(s, cloth((90, 70, 44)), leather(MOSS), boot_rows=4)
    s.clear_part("hat")
    straw = stripes(cloth((226, 196, 120)), cloth((190, 156, 86)), 1, horizontal=False)
    s.paint("hat", straw, ("top",))
    s.paint("hat", straw, SIDES, rows=(0, 3))
    s.team_paint("hat", SIDES, rows=(2, 3), folds=0)
    for f in SIDES:   # the mesh veil
        w = s.face("hat", f)[2]
        for j in range(3, 10):
            for i in range(w):
                if (i + j) % 2 == 0 and not (f == "front" and 1 <= i <= 6 and 3 <= j <= 6 and (i + j) % 4):
                    s.fpx("hat", f, i, j, (40, 36, 30))
    return s


# ======================================================================= TIDEWROUGHT (teal + brass, kelp, coral)
@unit("tidewrought")
def shipwright():
    """Shipwright (worker): a sea-teal smock with rolled linen sleeves, a leather apron with brass buckles and a
    mallet loop, a team kerchief knotted on the head (its tails down the back)."""
    s = new("shipwright", "tidewrought")
    head(s, SKIN_TAN, eyes=(60, 110, 130), hair=(70, 50, 34), style="short", beard=(80, 56, 36), beard_style="full")
    robe(s, cloth(TEAL, folds=3), rows=15)
    s.paint("jacket", leather((130, 90, 56), stitch=True), ("front",), rows=(3, 15), where=lambda f, i, j, w, h: 1 <= i <= 6)
    for i in (1, 6):
        s.fpx("jacket", "front", i, 3, tone(ramp(BRASS), 3.4))
    s.paint("jacket", leather((110, 76, 46)), ("front", "back"), rows=(0, 3), where=lambda f, i, j, w, h: i in (1, 6))
    belt(s, 9, leather((90, 62, 40)), buckle=BRASS, pouches=(120, 84, 50))
    s.rows_map("jacket", "left", ["WWW", ".W.", ".W.", ".W."], {"W": bark((140, 100, 60))}, oy=10)
    sleeves(s, cloth(LINEN), SKIN_TAN, bare_from=5)
    legs(s, cloth((70, 80, 90)), leather((60, 42, 30)), boot_rows=4)
    s.clear_part("hat")
    s.team_paint("hat", ("top",), folds=0)
    s.team_paint("hat", SIDES, rows=(0, 2), folds=0)
    s.team_paint("hat", ("back",), rows=(2, 6), where=lambda f, i, j, w, h: i in (5, 6), folds=0)
    return s


@unit("tidewrought")
def harbourmaster():
    """Harbourmaster (T2 worker): a long navy harbour coat with two rows of brass buttons and brass epaulettes, a
    white peaked cap with a black peak and a brass anchor badge, a team cap band and lanyard, a neat grey beard."""
    s = new("harbourmaster", "tidewrought")
    head(s, SKIN_WARM, eyes=(60, 90, 120), hair=(160, 160, 160), style="cropped", beard=(176, 176, 176), wrinkles=True)
    robe(s, cloth(NAVY, folds=3), hem=cloth((26, 34, 58)))
    for j in range(2, 15, 3):
        for i in (2, 5):
            s.fpx("jacket", "front", i, j, tone(ramp(BRASS), 3.4)); s.fpx("jacket", "front", i, j + 1, tone(ramp(BRASS), 1.6))
    s.paint("jacket", cloth((26, 34, 58)), ("front",), rows=(0, 16), where=lambda f, i, j, w, h: i in (3, 4))
    belt(s, 9, leather((40, 34, 30)), buckle=BRASS)
    sash(s, width=1, j0=0, j1=9)
    sleeves(s, cloth(NAVY), SKIN_WARM, cuff=plate(BRASS, lame=2))
    pauldrons(s, plate(BRASS, lame=2, rivet=(255, 230, 150)), rows=2)
    for p in ("arm_up", "arm"):
        for i in range(4):
            s.fpx(p, "front", i, 2, tone(ramp(BRASS), 2.6 if i % 2 else 1.6))
    legs(s, cloth((40, 46, 66)), leather((30, 26, 24)), boot_rows=5)
    cap(s, silk((236, 236, 232)), rows=3, peak=leather((24, 24, 28)))
    s.team_paint("hat", SIDES, rows=(2, 3), folds=0)
    s.emblem("hat", "front", 2, 0, "anchor", BRASS)
    return s


@unit("tidewrought")
def cutlass_raider():
    """Cutlass Raider: a striped linen shirt, a team sash knotted at the hip, a black tricorne trimmed brass, a
    leather baldric with a brass buckle, an eye patch on a bronzed face."""
    s = new("cutlass_raider", "tidewrought")
    head(s, SKIN_TAN, eyes=(70, 100, 110), hair=(36, 30, 26), style="long", beard=(40, 32, 26), beard_style="goatee", scar=True)
    # the eye patch over the left eye and its cord running round to the back
    s.fpx("head", "front", 5, 4, (20, 18, 18)); s.fpx("head", "front", 6, 4, (20, 18, 18))
    s.fpx("head", "front", 5, 5, (34, 30, 28)); s.fpx("head", "front", 6, 3, (30, 26, 24)); s.fpx("head", "front", 7, 3, (30, 26, 24))
    for i in range(8):
        s.fpx("head", "left", i, 3, (30, 26, 24))
    robe(s, stripes(cloth(LINEN), cloth((40, 90, 110)), 2), rows=12)
    waist_sash(s, 8, rows=2, tail=True)
    strap(s, leather((70, 48, 30)), reverse=True, j0=0, j1=9, studs=BRASS)
    sleeves(s, stripes(cloth(LINEN), cloth((40, 90, 110)), 2), SKIN_TAN, cuff=cloth(LINEN), bare_from=8)
    legs(s, cloth((60, 52, 44)), leather((40, 30, 24)), boot_rows=5)
    s.clear_part("hat")
    s.paint("hat", leather((28, 26, 30)), ("top",))
    s.paint("hat", leather((28, 26, 30)), SIDES, rows=(0, 3))
    s.band("hat", 2, 3, plate(BRASS, lame=1))
    s.paint("hat", leather((28, 26, 30)), ("top",), where=lambda f, i, j, w, h: True)
    for (i, j) in ((0, 0), (7, 0), (3, 7), (4, 7)):   # the three corners from above
        s.fpx("hat", "top", i, j, tone(ramp(BRASS), 3.2))
    for (i, j) in ((3, 2), (4, 2), (3, 3)):   # a team feather cockade
        s.team_paint("hat", ("top",), where=lambda f, ii, jj, w, h, i=i, j=j: ii == i and jj == j, folds=0)
    return s


@unit("tidewrought")
def reef_guard():
    """Reef Guard: brass plate studded with coral over a teal gambeson, a team tabard with a trident, a brass
    morion with a coral crest - from above a gold dome with a red ridge."""
    s = new("reef_guard", "tidewrought")
    head(s, SKIN_WARM, eyes=(50, 110, 120), hair=(60, 44, 30), style="cropped", beard=(60, 44, 30), beard_style="full")
    P = plate(BRASS, lame=3, rivet=(250, 226, 150))
    robe(s, P, rows=10, body=P)
    s.paint("jacket", quilt(TEAL), SIDES, rows=(10, 16))
    s.clear_part("jacket", SIDES, rows=(16, 18))
    tabard(s, j0=1, j1=16, cols=(2, 6), trim=CORAL)
    s.emblem("jacket", "front", 2, 3, "trident", BRASS)
    for (f, i, j) in (("right", 1, 3), ("left", 4, 5), ("back", 1, 2), ("back", 6, 6)):
        s.fpx("jacket", f, i, j, tone(ramp(CORAL), 3.2)); s.fpx("jacket", f, i, j + 1, tone(ramp(CORAL), 1.8))
    belt(s, 9, leather((70, 50, 34)), buckle=BRASS)
    sleeves(s, quilt(TEAL), SKIN_WARM, glove=plate(BRASS, lame=2), bracer=plate(BRASS, lame=2))
    pauldrons(s, plate(BRASS, lame=2, rivet=CORAL), rows=3)
    legs(s, quilt(TEAL), plate(BRASS, lame=2), boot_rows=4, knee=plate(BRASS, lame=2))
    helmet(s, plate(BRASS, lame=5), rows=4, style="kettle", crest=CORAL, cheek=True)
    return s


@unit("tidewrought")
def bombard_crew():
    """Bombard Crew: a slate coat with brass buttons, a powder belt of little casks, a soot-smudged face, a brass
    gunner's cap with a team band, a team sleeve band; a powder keg on the back."""
    s = new("bombard_crew", "tidewrought")
    head(s, SKIN_WARM, eyes=(60, 80, 90), hair=(60, 50, 40), style="short", beard=(66, 54, 42), beard_style="moustache")
    for (i, j) in ((0, 6), (1, 7), (6, 5), (7, 6)):
        s.fpx("head", "front", i, j, (80, 70, 64))
    robe(s, cloth((84, 94, 104), folds=3), rows=15)
    for j in range(2, 14, 3):
        s.fpx("jacket", "front", 4, j, tone(ramp(BRASS), 3.4))
    belt(s, 9, leather((70, 50, 34)), buckle=BRASS)
    for i in (0, 2, 5, 7):   # powder casks
        s.fpx("jacket", "front", i, 11, tone(ramp((140, 96, 56)), 2.8)); s.fpx("jacket", "front", i, 12, tone(ramp((140, 96, 56)), 1.8))
    sleeves(s, cloth((84, 94, 104)), SKIN_WARM, cuff=cloth((60, 66, 74)), glove=leather((60, 46, 34)))
    sleeve_band(s, 2, 4)
    legs(s, cloth((70, 64, 56)), leather((40, 32, 26)), boot_rows=4)
    cap(s, plate(BRASS, lame=3), rows=3)
    s.team_paint("hat", SIDES, rows=(1, 3), folds=0)
    back_pack(s, [".KKKK.", "KkKKkK", "KKKKKK", "KkKKkK", ".KKKK."], {"K": bark((130, 90, 54)), "k": plate(IRON, lame=1)}, oy=3)
    return s


@unit("tidewrought")
def harpooner():
    """Harpooner: a yellow oilskin coat and sou'wester (the brightest top in the faction), a rope coil slung across the
    chest, teal breeches, a weathered bearded face, a team scarf at the throat."""
    s = new("harpooner", "tidewrought")
    OIL = silk((232, 196, 52))
    head(s, SKIN_TAN, eyes=(60, 100, 120), hair=(120, 100, 80), style="short", beard=(130, 110, 86), beard_style="full")
    robe(s, OIL, rows=16, hem=silk((200, 160, 40)))
    for j in range(2, 15, 3):
        s.fpx("jacket", "front", 4, j, tone(ramp((60, 50, 30)), 2.0))
    strap(s, stripes(cloth((200, 180, 140)), cloth((150, 130, 96)), 1), width=2, j0=0, j1=13)
    scarf(s, rows=2, tail_back=4)
    sleeves(s, OIL, SKIN_TAN, glove=leather((90, 70, 40)))
    legs(s, cloth(TEAL), leather((50, 40, 30)), boot_rows=5)
    s.clear_part("hat")
    s.paint("hat", OIL, ("top",))
    s.paint("hat", OIL, SIDES, rows=(0, 3))
    s.paint("hat", OIL, ("back",), rows=(3, 6))
    s.paint("hat", OIL, ("right",), rows=(3, 5), where=lambda f, i, j, w, h: i < 4)
    s.paint("hat", OIL, ("left",), rows=(3, 5), where=lambda f, i, j, w, h: i > 3)
    return s


@unit("tidewrought")
def flak_swivel():
    """Flak Swivel: a short teal jacket with brass buttons, a cartridge belt of brass shells, round brass goggles on a
    leather cap (from above a dark cap with two bright lenses), team shoulder cords."""
    s = new("flak_swivel", "tidewrought")
    head(s, SKIN_FAIR, eyes=(60, 100, 120), hair=(150, 110, 60), style="short")
    robe(s, cloth(TEAL, folds=3), rows=12)
    for j in (2, 5, 8):
        s.fpx("jacket", "front", 2, j, tone(ramp(BRASS), 3.4)); s.fpx("jacket", "front", 5, j, tone(ramp(BRASS), 3.4))
    belt(s, 9, leather((70, 50, 34)), buckle=BRASS)
    for i in range(8):
        if i % 2 == 0:
            s.fpx("jacket", "front", i, 9, tone(ramp(BRASS), 3.4)); s.fpx("jacket", "back", i, 9, tone(ramp(BRASS), 3.0))
    sleeves(s, cloth(TEAL), SKIN_FAIR, glove=leather((60, 46, 34)))
    mantle(s, rows=2, collar=False)
    legs(s, cloth((70, 64, 56)), leather((40, 32, 26)), boot_rows=4)
    cap(s, leather((70, 50, 34)), rows=2)
    goggles(s, BRASS, (120, 210, 230), row=1)
    return s


@unit("tidewrought")
def kraken_caller():
    """Kraken Caller: deep-sea robes of abyssal violet over teal, a mantle of tentacles with pale suckers over the
    shoulders, a team hood whose face glows sea-teal, a pearl clasp at the throat."""
    s = new("kraken_caller", "tidewrought")
    head(s, (110, 120, 130), glow=(90, 230, 210), style="bald")
    robe(s, cloth((54, 36, 84), folds=4), hem=cloth(TEAL))
    s.paint("jacket", cloth(TEAL), ("front",), rows=(2, 18), where=lambda f, i, j, w, h: 3 <= i <= 4)
    TN = ramp((110, 70, 130))
    for f, i, j, w, h, u, x, y in s.coords("jacket", SIDES, (0, 10)):   # tentacles hang from the shoulders
        if u % 4 in (0, 1) and j < 4 + (u * 7 % 5):
            s.put(x, y, tone(TN, 2.6 if u % 4 == 0 else 1.6))
            if j % 2 == 0 and u % 4 == 0:
                s.put(x, y, (226, 206, 220))
    s.fpx("jacket", "front", 3, 1, (240, 240, 236)); s.fpx("jacket", "front", 4, 1, (190, 196, 206))
    sleeves(s, cloth((54, 36, 84)), (110, 120, 130), cuff=cloth(TEAL))
    for p in ("arm_up", "arm"):
        s.paint(p, scales((110, 70, 130), 2, 2), ("top",))
    legs(s, cloth((40, 30, 60)), cloth((30, 24, 44)), boot_rows=3)
    hood(s, team=True, inner=(10, 30, 36))
    return s


# ======================================================================= STORMCALLERS (red lacquer, jade, gold)
@unit("stormcallers")
def geomancer():
    """Geomancer (worker): a deep red robe with a jade sash and gold hem, an earth-brown under-robe at the sleeves, a
    scholar's black cap with a team tassel cord, a gold coin emblem."""
    s = new("geomancer", "stormcallers")
    head(s, SKIN_WARM, eyes=(40, 34, 30), hair=(30, 26, 24), style="short", beard=(36, 30, 26), beard_style="moustache")
    robe(s, silk(LAC_RED), hem=plate(GOLD, lame=2))
    s.paint("jacket", cloth(JADE), SIDES, rows=(8, 10))
    s.shade("jacket", 0.7, rows=(10, 11))
    s.emblem("jacket", "front", 2, 2, "coin", GOLD)
    sleeves(s, silk(LAC_RED), SKIN_WARM, cuff=cloth((120, 84, 50)))
    legs(s, cloth((110, 80, 50)), leather((40, 30, 26)), boot_rows=3)
    cap(s, silk((30, 28, 30)), rows=2)
    s.team_paint("hat", ("top",), where=lambda f, i, j, w, h: 3 <= i <= 4 and 3 <= j <= 4, folds=0)
    s.team_paint("hat", ("back",), rows=(0, 6), where=lambda f, i, j, w, h: i == 4, folds=0)
    sleeve_band(s, 0, 2)
    return s


@unit("stormcallers")
def ley_master():
    """Ley Master (T2 worker): a gold robe with red lacquer trim and a jade sash, jade discs on the shoulders, a tall
    black scholar's crown ringed in gold with a jade jewel, a grey beard, a team stole."""
    s = new("ley_master", "stormcallers")
    head(s, SKIN_WARM, eyes=(40, 34, 30), hair=(150, 150, 150), style="short", beard=(170, 170, 170), beard_style="long", wrinkles=True)
    robe(s, silk(GOLD), hem=lacquer(LAC_RED))
    s.paint("jacket", cloth(JADE), SIDES, rows=(8, 10))
    tabard(s, j0=1, j1=16, cols=(1, 2), point=False, back=False)
    s.team_paint("jacket", ("front",), rows=(1, 16), where=lambda f, i, j, w, h: i == 6, folds=0)
    beard_chest(s, (170, 170, 170), rows=5)
    sleeves(s, silk(GOLD), SKIN_WARM, cuff=lacquer(LAC_RED))
    for p in ("arm_up", "arm"):
        J = ramp(JADE)
        for i in range(4):
            for j in range(4):
                r = abs(i - 1.5) + abs(j - 1.5)
                if r < 2.6:
                    s.fpx(p, "top", i, j, tone(J, 3.4 if r < 1 else 2.2))
    legs(s, cloth((120, 40, 30)), leather((40, 30, 26)), boot_rows=3)
    cap(s, silk((26, 24, 28)), rows=4, band=plate(GOLD, lame=1))
    s.fpx("hat", "front", 3, 2, tone(ramp(JADE), 3.5)); s.fpx("hat", "front", 4, 2, tone(ramp(JADE), 2.2))
    return s


@unit("stormcallers")
def blade_dancer():
    """Blade Dancer: red silk with a team sash tied at the hip, bare forearms with gold bands, a high topknot with a
    gold pin and a team ribbon trailing down the back - from above, a dark knot over red shoulders."""
    s = new("blade_dancer", "stormcallers")
    head(s, SKIN_FAIR, eyes=(40, 30, 26), hair=(24, 20, 22), style="short")
    robe(s, silk(LAC_RED), rows=14)
    s.paint("jacket", silk((130, 24, 22)), ("front",), rows=(0, 14), where=lambda f, i, j, w, h: abs(i - (1 + j * 0.45)) < 0.7)
    waist_sash(s, 8, tail=True)
    sleeves(s, silk(LAC_RED), SKIN_FAIR, bare_from=5)
    for p in ("arm",):
        s.band(p, 6, 7, plate(GOLD, lame=1)); s.band(p, 8, 9, plate(GOLD, lame=1))
    legs(s, silk((40, 30, 32)), cloth((30, 24, 26)), boot_rows=3, wrap=cloth((200, 190, 170)))
    s.clear_part("hat")
    topknot(s, (24, 20, 22), GOLD)
    s.team_paint("hat", ("back",), rows=(1, 10), where=lambda f, i, j, w, h: i in (3, 4) and (j < 6 or i == 3), folds=0)
    return s


@unit("stormcallers")
def jade_guard():
    """Jade Guard: red-lacquered lamellar (rows of plates, gold rivets) over a black tunic, jade shoulder plates, a
    red helmet with a jade crest, a team tabard with a gold coin."""
    s = new("jade_guard", "stormcallers")
    head(s, SKIN_WARM, eyes=(40, 34, 30), hair=(26, 22, 22), style="cropped", beard=(30, 26, 24), beard_style="moustache")
    L = plate(LAC_RED, lame=2, rivet=GOLD, spec=4)
    robe(s, L, rows=11, body=L)
    s.paint("jacket", cloth((30, 28, 30)), SIDES, rows=(11, 16))
    s.paint("jacket", plate(LAC_RED, lame=2, spec=4), SIDES, rows=(11, 16), where=lambda f, i, j, w, h: i % 3 != 1)
    s.clear_part("jacket", SIDES, rows=(16, 18))
    tabard(s, j0=4, j1=16, cols=(3, 5), trim=GOLD)
    belt(s, 9, leather((40, 30, 26)), buckle=GOLD)
    s.emblem("jacket", "front", 2, 0, "coin", GOLD)
    sleeves(s, cloth((30, 28, 30)), SKIN_WARM, bracer=plate(LAC_RED, lame=2), glove=leather((40, 30, 26)))
    pauldrons(s, plate(JADE, lame=2, rivet=GOLD), rows=4)
    legs(s, cloth((30, 28, 30)), lacquer((50, 30, 26)), boot_rows=4)
    helmet(s, lacquer(LAC_RED), rows=4, style="kettle", crest=JADE, cheek=True, trim=GOLD)
    return s


@unit("stormcallers")
def fire_adept():
    """Fire Adept: a flame-orange robe fading to red at the hem with gold flame embroidery, a gold diadem with a ruby,
    team sleeve cuffs, a flame emblem."""
    s = new("fire_adept", "stormcallers")
    head(s, SKIN_FAIR, eyes=(200, 90, 30), hair=(110, 30, 20), style="long")
    robe(s, silk((236, 120, 40)))
    for f, i, j, w, h, u, x, y in s.coords("jacket", SIDES, (9, 18)):
        p = s.get(x, y)
        s.put(x, y, mix(p, (150, 24, 20), (j - 9) / 9.0))
        if j >= 13 and (u + j) % 3 == 0 and K.h01(s.seed, u, j, 50) < 0.5:
            s.put(x, y, tone(ramp(GOLD), 3.2))
    s.emblem("jacket", "front", 2, 2, "flame", GOLD)
    sleeves(s, silk((210, 70, 30)), SKIN_FAIR)
    sleeve_band(s, 5, 8)
    legs(s, cloth((120, 30, 24)), leather((50, 30, 24)), boot_rows=3)
    circlet(s, GOLD, gem=(220, 30, 40), row=2)
    s.team_paint("hat", ("back",), rows=(3, 9), where=lambda f, i, j, w, h: i in (3, 4), folds=0)
    return s


@unit("stormcallers")
def frost_adept():
    """Frost Adept: the Fire Adept's cut in ice - a pale blue robe fading to deep blue with red lacquer trim, frost
    crystals at the hem, a silver diadem with a jade stone, team sleeve cuffs."""
    s = new("frost_adept", "stormcallers")
    head(s, SKIN_FAIR, eyes=(90, 170, 220), hair=(200, 220, 236), style="long")
    robe(s, silk((190, 222, 240)), hem=lacquer(LAC_RED), hem_rows=1)
    for f, i, j, w, h, u, x, y in s.coords("jacket", SIDES, (8, 17)):
        p = s.get(x, y)
        s.put(x, y, mix(p, (50, 90, 160), (j - 8) / 9.0))
        if j >= 12 and (u * 3 + j) % 5 == 0:
            s.put(x, y, (236, 248, 255))
    s.emblem("jacket", "front", 2, 2, "star", SILVER)
    sleeves(s, silk((170, 210, 236)), SKIN_FAIR, cuff=lacquer(LAC_RED))
    sleeve_band(s, 5, 7)
    legs(s, cloth((60, 90, 140)), leather((40, 40, 50)), boot_rows=3)
    circlet(s, SILVER, gem=JADE, row=2)
    s.team_paint("hat", ("back",), rows=(3, 9), where=lambda f, i, j, w, h: i in (3, 4), folds=0)
    return s


@unit("stormcallers")
def wind_monk():
    """Wind Monk: a saffron robe with a team sash over one shoulder, a shaved head with a jade dot (the lightest top
    in the faction), prayer beads at the neck, wrapped shins."""
    s = new("wind_monk", "stormcallers")
    head(s, SKIN_WARM, eyes=(40, 34, 30), style="bald", paint=(JADE, "dot"))
    robe(s, cloth((232, 150, 40), folds=3), rows=16, hem=cloth((190, 110, 30)))
    sash(s, width=3, j0=0, j1=12)
    for i in range(1, 7):
        s.fpx("jacket", "front", i, 0 if i in (1, 6) else 1, tone(ramp((120, 70, 40)), 3.0 if i % 2 else 2.0))
    sleeves(s, cloth((232, 150, 40)), SKIN_WARM, bare_from=6)
    legs(s, cloth((200, 120, 40)), cloth((120, 90, 60)), boot_rows=2, wrap=cloth((230, 220, 196)))
    s.clear_part("hat")
    return s


@unit("stormcallers")
def gravity_monk():
    """Gravity Monk: a violet robe with gold orbit rings embroidered on the chest, a team sash over one shoulder, bare
    forearms with gold bands, a shaved head under a thin gold circlet with a violet stone."""
    s = new("gravity_monk", "stormcallers")
    head(s, SKIN_TAN, eyes=(60, 40, 90), style="bald")
    robe(s, cloth((92, 58, 150), folds=3), rows=16, hem=cloth((52, 30, 92)))
    G = ramp(GOLD)
    for (i, j) in ((1, 3), (2, 2), (3, 2), (4, 2), (5, 2), (6, 3), (6, 4), (5, 5), (4, 5), (3, 5), (2, 5), (1, 4)):
        s.fpx("jacket", "front", i, j + 1, tone(G, 3.0))
    s.fpx("jacket", "front", 3, 4, tone(ramp((150, 110, 206)), 3.4)); s.fpx("jacket", "front", 4, 4, tone(G, 3.6))
    sash(s, width=2, reverse=True, j0=0, j1=12)
    sleeves(s, cloth((92, 58, 150)), SKIN_TAN, bare_from=6)
    s.band("arm", 7, 8, plate(GOLD, lame=1))
    legs(s, cloth((60, 40, 100)), cloth((40, 30, 60)), boot_rows=2, wrap=cloth((200, 190, 170)))
    circlet(s, GOLD, gem=(150, 110, 206), row=3)
    return s


@unit("stormcallers")
def mirror_mage():
    """Mirror Mage: a silver-grey robe with red lacquer trim and a round bronze mirror on the chest, bronze bracers, a
    team hood drawn up with a bronze clasp - from above a team-coloured dome on grey shoulders."""
    s = new("mirror_mage", "stormcallers")
    head(s, SKIN_FAIR, eyes=(80, 80, 90), hair=(40, 36, 36), style="short")
    robe(s, silk((176, 180, 190)), hem=lacquer(LAC_RED))
    for j in range(18):
        s.fpx("jacket", "front", 0, j, tone(ramp(LAC_RED), 2.4)); s.fpx("jacket", "front", 7, j, tone(ramp(LAC_RED), 1.8))
    Bz = ramp((190, 130, 60))
    for (i, j) in ((3, 2), (4, 2), (2, 3), (5, 3), (2, 4), (5, 4), (3, 5), (4, 5)):
        s.fpx("jacket", "front", i, j, tone(Bz, 2.6))
    s.fpx("jacket", "front", 3, 3, (250, 250, 255)); s.fpx("jacket", "front", 4, 3, (200, 220, 240))
    s.fpx("jacket", "front", 3, 4, (170, 190, 220)); s.fpx("jacket", "front", 4, 4, (130, 150, 190))
    sleeves(s, silk((176, 180, 190)), SKIN_FAIR, bracer=plate((190, 130, 60), lame=2))
    legs(s, cloth((90, 90, 100)), leather((40, 36, 36)), boot_rows=3)
    hood(s, team=True, inner=(30, 20, 20), trim=(190, 130, 60))
    return s


@unit("stormcallers")
def seer():
    """Seer: a midnight-blue robe strewn with gold stars, a gold star chart on the chest, a flat scholar's cap in blue
    with a gold button and team lappets, a lantern cage at the hip."""
    s = new("seer", "stormcallers")
    head(s, SKIN_WARM, eyes=(60, 60, 110), hair=(200, 200, 200), style="short", beard=(210, 210, 210), beard_style="long", wrinkles=True)
    robe(s, silk((30, 40, 90)), hem=plate(GOLD, lame=2))
    for f, i, j, w, h, u, x, y in s.coords("jacket", SIDES, (2, 16)):
        if K.h01(s.seed, u, j, 60) < 0.07:
            s.put(x, y, tone(ramp(GOLD), 3.6))
    s.emblem("jacket", "front", 2, 5, "star", GOLD)
    beard_chest(s, (210, 210, 210), rows=4)
    sleeves(s, silk((30, 40, 90)), SKIN_WARM, cuff=plate(GOLD, lame=2))
    mantle(s, rows=2, collar=False)
    s.rows_map("jacket", "left", ["..G..", ".GFG.", ".GFG.", ".GGG."], {"G": GOLD, "F": FURNACE}, oy=9)
    legs(s, cloth((30, 34, 70)), leather((30, 26, 30)), boot_rows=3)
    cap(s, silk((36, 46, 100)), rows=2, badge=GOLD)
    s.team_paint("hat", ("back",), rows=(2, 8), where=lambda f, i, j, w, h: i in (1, 6), folds=0)
    return s


@unit("stormcallers")
def rocket_battery():
    """Rocket Battery: a soot-dark crew coat with red lacquer facings, a gold powder belt of little rockets across the
    chest, leather bracers, a team crew cap with a gold badge; the rocket rack on the back."""
    s = new("rocket_battery", "stormcallers")
    head(s, SKIN_WARM, eyes=(40, 34, 30), hair=(26, 22, 22), style="short", beard=(30, 26, 24), beard_style="goatee")
    robe(s, cloth((52, 46, 48), folds=3), rows=15)
    s.paint("jacket", lacquer(LAC_RED), ("front",), rows=(0, 15), where=lambda f, i, j, w, h: i in (0, 7))
    strap(s, plate(GOLD, lame=1), width=1, j0=1, j1=11)
    sleeves(s, cloth((52, 46, 48)), SKIN_WARM, bracer=leather(LEATHER_C))
    legs(s, cloth((60, 50, 46)), leather((36, 30, 28)), boot_rows=4)
    s.clear_part("hat")
    s.team_paint("hat", ("top",), folds=0)
    s.team_paint("hat", SIDES, rows=(0, 2), folds=0)
    s.fpx("hat", "front", 3, 1, tone(ramp(GOLD), 3.5)); s.fpx("hat", "front", 4, 1, tone(ramp(GOLD), 2.4))
    back_pack(s, ["GRGRGRG.", "RRRRRRR.", "RRRRRRR.", "RRRRRRR.", "LLLLLLL."], {"G": GOLD, "R": lacquer(LAC_RED), "L": leather(LEATHER_C)}, oy=1)
    return s


# ======================================================================= BRASSWORKS (brass, soot black, furnace orange)
def dwarf(s, skin=SKIN_RUDDY, beard=(170, 70, 30), hair=None, braids=True):
    head(s, skin, eyes=(60, 80, 100), hair=hair or beard, style="short", beard=beard, beard_style="full")
    if braids:
        for j in (7, 9):
            s.fpx("head", "front", 2, j, tone(ramp(BRASS), 3.2)); s.fpx("head", "front", 5, j, tone(ramp(BRASS), 3.2))


@unit("brassworks")
def tinker():
    """Tinker (worker): a ruddy dwarf with a braided red beard, goggles up on a leather cap, a soot coat under a long
    leather apron with brass buckles, brass bracers, a team neckerchief; a brass toolbox on the back."""
    s = new("tinker", "brassworks")
    dwarf(s)
    robe(s, cloth(SOOT, folds=3), rows=15)
    s.paint("jacket", leather((134, 92, 54), stitch=True), ("front",), rows=(2, 15), where=lambda f, i, j, w, h: 1 <= i <= 6)
    for i in (1, 6):
        s.fpx("jacket", "front", i, 2, tone(ramp(BRASS), 3.4))
    beard_chest(s, (170, 70, 30), rows=4)
    belt(s, 9, leather((70, 50, 34)), buckle=BRASS, pouches=(100, 70, 44))
    scarf(s, rows=1, tail_back=0)
    sleeves(s, cloth(SOOT), SKIN_RUDDY, bracer=plate(BRASS, lame=2), glove=leather((70, 50, 34)))
    legs(s, cloth((70, 60, 54)), leather((44, 34, 28)), boot_rows=4)
    cap(s, leather((90, 64, 40)), rows=2)
    goggles(s, BRASS, (130, 200, 220), row=1)
    back_pack(s, [".BBBB.", "BbBBbB", "BBGGBB", "BBBBBB", "BbBBbB"], {"B": plate(BRASS, lame=2), "b": tone(ramp(SOOT), 2), "G": FURNACE}, oy=3)
    return s


@unit("brassworks")
def engineer_captain():
    """Engineer Captain (T2 worker): a grey-bearded dwarf officer in a long soot coat with brass epaulettes and a
    double row of brass buttons, a peaked soot cap with a brass gear badge and a team band, a team sash."""
    s = new("engineer_captain", "brassworks")
    dwarf(s, skin=SKIN_WARM, beard=(170, 170, 166))
    robe(s, cloth(SOOT, folds=3), hem=cloth((36, 32, 30)))
    for j in range(5, 16, 3):
        for i in (2, 5):
            s.fpx("jacket", "front", i, j, tone(ramp(BRASS), 3.4)); s.fpx("jacket", "front", i, j + 1, tone(ramp(BRASS), 1.6))
    beard_chest(s, (170, 170, 166), rows=5)
    sash(s, width=2, j0=0, j1=12)
    belt(s, 10, leather((50, 38, 30)), buckle=BRASS)
    sleeves(s, cloth(SOOT), SKIN_WARM, cuff=plate(BRASS, lame=2), glove=leather((50, 38, 30)))
    pauldrons(s, plate(BRASS, lame=2, rivet=FURNACE), rows=2)
    legs(s, cloth((54, 48, 46)), leather((36, 30, 26)), boot_rows=5)
    cap(s, cloth((40, 36, 34)), rows=3, peak=leather((24, 22, 22)))
    s.team_paint("hat", SIDES, rows=(2, 3), folds=0)
    s.emblem("hat", "front", 2, 0, "gear", BRASS)
    return s


@unit("brassworks")
def rifle_squad():
    """Rifle Squad: a grey-bearded dwarf in a soot coat with brass buttons, a powder-horn strap, a flat cap with a
    team crown and brass badge, leather bracers; a powder keg and ramrod on the back."""
    s = new("rifle_squad", "brassworks")
    dwarf(s, skin=SKIN_WARM, beard=(140, 136, 130))
    robe(s, cloth((62, 58, 56), folds=3), rows=15)
    for j in range(2, 14, 3):
        s.fpx("jacket", "front", 4, j, tone(ramp(BRASS), 3.4))
    strap(s, leather((90, 64, 40)), reverse=True, j0=0, j1=12, studs=BRASS)
    s.rows_map("jacket", "left", ["WW..", ".WWW", "..BW"], {"W": bone((220, 210, 180)), "B": BRASS}, oy=11)
    belt(s, 9, leather((60, 44, 32)), buckle=BRASS)
    sleeves(s, cloth((62, 58, 56)), SKIN_WARM, bracer=leather(LEATHER_C))
    legs(s, cloth((70, 60, 54)), leather((40, 32, 26)), boot_rows=4)
    cap(s, cloth((50, 46, 44)), rows=2, badge=BRASS)
    s.team_paint("hat", ("top",), folds=0)
    back_pack(s, ["......I.", ".KKKK.I.", "KkKKkKI.", "KKKKKKI.", "KkKKkK..", ".KKKK..."], {"K": bark((130, 90, 54)), "k": plate(IRON, lame=1), "I": plate(IRON, lame=1)}, oy=2)
    return s


@unit("brassworks")
def sapper():
    """Sapper: a soot apron over a leather coat, a red beard, a soot cap with a brass lamp burning furnace orange, a
    team armband; the charge satchel on the back - brass-capped charges and a fuse."""
    s = new("sapper", "brassworks")
    dwarf(s, beard=(190, 80, 30))
    robe(s, leather((110, 76, 48)), rows=14)
    s.paint("jacket", leather((44, 40, 38)), ("front",), rows=(2, 14), where=lambda f, i, j, w, h: 1 <= i <= 6)
    beard_chest(s, (190, 80, 30), rows=4)
    belt(s, 9, leather((60, 44, 32)), buckle=BRASS)
    sleeves(s, leather((110, 76, 48)), SKIN_RUDDY, bracer=leather((70, 50, 34)))
    sleeve_band(s, 1, 4)
    legs(s, cloth((70, 60, 54)), leather((40, 32, 26)), boot_rows=4)
    cap(s, cloth(SOOT), rows=2)
    s.fpx("hat", "front", 3, 0, tone(ramp(BRASS), 3.0)); s.fpx("hat", "front", 4, 0, tone(ramp(BRASS), 3.0))
    s.fpx("hat", "front", 3, 1, (255, 200, 90)); s.fpx("hat", "front", 4, 1, FURNACE)
    back_pack(s, [".....F..", "SSSSSF..", "SBSBSS..", "SCSCSS..", "SCSCSS..", "SSSSSS.."], {"S": cloth((60, 56, 52)), "B": BRASS, "C": (170, 40, 30), "F": (220, 200, 160)}, oy=2)
    return s


@unit("brassworks")
def flak_cart():
    """Flak Cart: a brown-bearded dwarf in a leather coat, goggles down over the eyes, ear muffs, brass bracers, a
    shell belt across the chest, team shoulder cloths; the flak drum with a glowing gauge on the back."""
    s = new("flak_cart", "brassworks")
    dwarf(s, skin=SKIN_WARM, beard=(110, 74, 44))
    robe(s, leather((120, 84, 52)), rows=14)
    strap(s, plate(BRASS, lame=1), width=2, j0=0, j1=12)
    belt(s, 9, leather((60, 44, 32)), buckle=BRASS)
    sleeves(s, leather((120, 84, 52)), SKIN_WARM, bracer=plate(BRASS, lame=2), glove=leather((60, 44, 32)))
    mantle(s, rows=2, collar=False)
    legs(s, cloth((70, 60, 54)), leather((40, 32, 26)), boot_rows=4)
    cap(s, leather((70, 50, 34)), rows=2)
    goggles(s, BRASS, (240, 170, 60), down=True)
    for f in ("right", "left"):
        for (i, j) in ((3, 4), (4, 4), (3, 5), (4, 5)):
            s.fpx("hat", f, i, j, tone(ramp((60, 56, 52)), 2.4 if j == 4 else 1.6))
    back_pack(s, [".DDDD.", "DDDDDD", "DDGGDD", "DDGgDD", "DDDDDD", ".DDDD."], {"D": plate(BRASS, lame=2), "G": (230, 230, 210), "g": (200, 40, 30)}, oy=2)
    return s


@unit("brassworks")
def shield_projector():
    """Shield Projector: a grey-bearded dwarf in a brass-trimmed soot coat with a glass dial on the chest, goggles up
    on a leather cap, brass bracers, a team mantle; the projector drum with a blue lens on the back."""
    s = new("shield_projector", "brassworks")
    dwarf(s, skin=SKIN_WARM, beard=(150, 146, 140))
    robe(s, cloth(SOOT, folds=3), rows=15)
    for j in range(15):
        s.fpx("jacket", "front", 0, j, tone(ramp(BRASS), 2.8)); s.fpx("jacket", "front", 7, j, tone(ramp(BRASS), 2.0))
    s.rows_map("jacket", "front", [".BB.", "BGgB", "BggB", ".BB."], {"B": BRASS, "G": (200, 240, 255), "g": (100, 170, 220)}, ox=2, oy=6)
    belt(s, 11, leather((60, 44, 32)), buckle=BRASS)
    sleeves(s, cloth(SOOT), SKIN_WARM, bracer=plate(BRASS, lame=2))
    mantle(s, rows=3)
    legs(s, cloth((60, 54, 50)), leather((40, 32, 26)), boot_rows=4)
    cap(s, leather((90, 64, 40)), rows=2)
    goggles(s, BRASS, (120, 200, 240), row=1)
    back_pack(s, [".BBBB.", "BBBBBB", "BBLLBB", "BBLlBB", "BBBBBB", ".BBBB."], {"B": plate(BRASS, lame=2), "L": (190, 236, 255), "l": (80, 150, 220)}, oy=2)
    return s


@unit("brassworks")
def mortar_crawler():
    """Mortar Crawler: a soot-smudged dwarf in a leather apron over a soot coat, a brass helmet with a team plume,
    brass bracers; the mortar tube with a glowing breech and a row of shells on the back."""
    s = new("mortar_crawler", "brassworks")
    dwarf(s, beard=(140, 90, 40))
    robe(s, cloth(SOOT, folds=3), rows=15)
    s.paint("jacket", leather((120, 84, 52)), ("front",), rows=(3, 15), where=lambda f, i, j, w, h: 1 <= i <= 6)
    beard_chest(s, (140, 90, 40), rows=4)
    belt(s, 10, leather((60, 44, 32)), buckle=BRASS)
    sleeves(s, cloth(SOOT), SKIN_RUDDY, bracer=plate(BRASS, lame=2))
    legs(s, cloth((60, 54, 50)), leather((40, 32, 26)), boot_rows=4)
    helmet(s, plate(BRASS, lame=4, rivet=FURNACE), rows=3, style="kettle")
    plume(s, length=6, back_rows=3)
    back_pack(s, ["..TT..", ".TTTT.", ".TTTT.", ".TFFT.", "SSSSSS", "SsSsSs"], {"T": plate(BRASS, lame=2), "F": FURNACE, "S": plate(IRON, lame=1), "s": tone(ramp(IRON), 1)}, oy=1)
    return s


@unit("brassworks")
def tesla_walker():
    """Tesla Walker: a brown-bearded dwarf in a rubber-black coat with copper trim, blue-lensed goggles down, copper
    bracers, a team sash; the coil pack - copper drum wound with brass and a crackling blue core - on the back."""
    s = new("tesla_walker", "brassworks")
    dwarf(s, skin=SKIN_WARM, beard=(100, 70, 44))
    robe(s, leather((30, 30, 34)), rows=15)
    for j in range(15):
        s.fpx("jacket", "front", 3, j, tone(ramp(COPPER), 3.0 if j % 2 else 2.2))
    sash(s, width=2, reverse=True, j0=0, j1=12)
    belt(s, 10, leather((40, 36, 34)), buckle=COPPER)
    sleeves(s, leather((30, 30, 34)), SKIN_WARM, bracer=plate(COPPER, lame=2), glove=leather((24, 24, 26)))
    legs(s, cloth((44, 42, 46)), leather((30, 28, 28)), boot_rows=4)
    cap(s, leather((40, 36, 34)), rows=2)
    goggles(s, COPPER, (110, 200, 255), down=True)
    back_pack(s, [".CCCC.", "CbCbCC", "CCZZCC", "CbZZbC", "CCCCCC", ".CCCC."], {"C": plate(COPPER, lame=1), "b": BRASS, "Z": (170, 230, 255)}, oy=2)
    return s



# ======================================================================= other bodies: skeleton (64x32) and witch (64x128)
def slim_limbs(s, arm_mat, leg_mat, hand, boot, glove=None, bracer=None, boot_rows=4):
    """The skeleton model's 2x12x2 arms and legs."""
    s.paint("arm", arm_mat)
    if bracer:
        s.band("arm", 6, 9, bracer)
    s.band("arm", 9, 12, glove or skinmat(hand))
    s.paint("arm", glove or skinmat(hand), ("bottom",))
    s.paint("leg", leg_mat)
    s.band("leg", 12 - boot_rows, 12, boot)
    s.paint("leg", boot, ("bottom",))
    s.shade("leg", 1.18, rows=(12 - boot_rows, 12 - boot_rows + 1))


@unit("verdant", kind="skeleton")
def thornbow():
    """Thornbow: an elf archer in a team-coloured hood with a sprig of thorn-berries, a leaf-scale jerkin over a
    bark-brown tunic, a quiver strap with silver studs, berry-red fletching at the shoulder, soft boots."""
    s = new("thornbow", "verdant", layout=K.SKELETON, size=(64, 32))
    head(s, (236, 212, 184), eyes=(90, 170, 90), hair=(214, 196, 150), style="long")
    s.paint("body", leather(BARK_C))
    s.paint("body", scales(LEAF, 2, 2), ("front", "back"), rows=(0, 7), where=lambda f, i, j, w, h: 1 <= i <= 6)
    s.band("body", 8, 9, leather((60, 44, 30)))
    s.shade("body", 0.72, rows=(9, 10))
    s.fpx("body", "front", 3, 8, tone(ramp(SILVER), 3.4)); s.fpx("body", "front", 4, 8, tone(ramp(SILVER), 2.4))
    for j in range(12):   # the quiver strap, shoulder to hip, front and back
        for f in ("front", "back"):
            i = j * 7 // 11 if f == "front" else 7 - j * 7 // 11
            s.fpx("body", f, i, j, tone(ramp((70, 50, 30)), 3.2 if j % 3 == 1 else 2.0))
    s.rows_map("body", "back", ["RR.....", "WW.....", "LL.....", "LL.....", "LL.....", "LL....."],
               {"R": (200, 40, 50), "W": (236, 230, 210), "L": leather((110, 76, 46))}, ox=5, oy=0)
    slim_limbs(s, leather(BARK_C), cloth((60, 70, 50)), (236, 212, 184), leather((80, 60, 40)), bracer=bark(BARK_C))
    s.clear_part("hat")
    hood(s, team=True, opening=(1, 6, 2, 7), collar=False)
    for (i, j, c) in ((3, 2, LEAF), (4, 3, LEAF), (3, 3, (200, 40, 50)), (5, 2, (200, 40, 50))):
        s.fpx("hat", "top", i, j, tone(ramp(c), 3.0))
    return s


@unit("verdant", kind="skeleton")
def shade_ranger():
    """Shade Ranger: a deep dusk-violet cloak and peaked hood with the face lost in shadow (two teal eyes glow), a
    silver crescent clasp at the throat, moss-grey wraps; a team mantle across the shoulders and down the back."""
    s = new("shade_ranger", "verdant", layout=K.SKELETON, size=(64, 32))
    head(s, (150, 132, 140), glow=(90, 230, 200), style="bald")
    s.shade("head", 0.45, ("front", "right", "left"))
    for i in (2, 5):
        s.fpx("head", "front", i, 4, (110, 250, 220))
    s.paint("body", cloth((60, 44, 78), folds=3))
    s.team_paint("body", ("back",), rows=(0, 10), folds=3)
    s.team_paint("body", SIDES, rows=(0, 2), folds=0)
    K_ = ramp(SILVER)
    for (i, j, t) in ((3, 1, 3.5), (4, 1, 2.6), (3, 2, 2.6), (4, 2, 1.6)):
        s.fpx("body", "front", i, j, tone(K_, t))
    slim_limbs(s, cloth((60, 44, 78)), cloth((90, 96, 86)), (150, 132, 140), leather((50, 44, 40)),
               glove=leather((40, 36, 40)), bracer=stripes(cloth((110, 116, 104)), cloth((80, 86, 76)), 1))
    hood(s, mat=cloth((52, 38, 70), folds=3), inner=(10, 8, 14), opening=(1, 6, 2, 7), collar=False)
    return s


def witch_hat(s, mats, band=None, tip=None):
    """The witch's four stacked hat boxes: brim (hat1), crown (hat2, hat3), tip (hat4), each its own material."""
    for part, m in zip(("hat1", "hat2", "hat3", "hat4"), mats):
        if m == "team":
            s.team_paint(part, ALL, folds=0)
        elif m is not None:
            s.paint(part, m)
    if band:
        s.band("hat2", 3, 4, band)
    if tip:
        s.paint("hat4", tip)


@unit("tidewrought", kind="witch")
def tide_priest():
    """Tide Priest: kelp-green over sea-teal robes hung with kelp fronds, a shell belt and a coral pendant; the hat
    recut as a tall team mitre on a brass brim with a pale shell at its tip."""
    s = new("tide_priest", "tidewrought", layout=K.WITCH, size=(64, 128))
    head(s, SKIN_TAN, eyes=(60, 140, 140), hair=(40, 70, 60), style="long")
    robe(s, cloth(TEAL, folds=4), rows=20, hem=cloth((22, 90, 86)))
    for f, i, j, w, h, u, x, y in s.coords("jacket", SIDES, (0, 20)):
        if u % 5 == 0 and j < 6 + (u * 3) % 9:
            s.put(x, y, tone(ramp(KELP), 1.8 + 0.6 * ((j + u) % 2)))
    belt(s, 9, leather((80, 60, 40)))
    for i in range(0, 8, 2):
        s.fpx("jacket", "front", i, 9, tone(ramp((236, 226, 206)), 3.4))
    s.emblem("jacket", "front", 2, 3, "shell", CORAL)
    sleeves(s, cloth(KELP), SKIN_TAN, cuff=plate(BRASS, lame=2))
    legs(s, cloth((30, 80, 80)), leather((50, 40, 30)), boot_rows=2)
    witch_hat(s, (plate(BRASS, lame=2), "team", "team", None), tip=bone((236, 226, 206)))
    return s


@unit("stormcallers", kind="witch")
def ink_scribe():
    """Ink Scribe: a paper-white robe with ink-black trim and a red seal on the chest, a black silk sash, ink stains at
    the sleeves; the hat recut as a scholar's black cap with a team crown and a gold bead at the tip."""
    s = new("ink_scribe", "stormcallers", layout=K.WITCH, size=(64, 128))
    head(s, SKIN_FAIR, eyes=(40, 34, 30), hair=(24, 20, 22), style="short", beard=(30, 26, 24), beard_style="goatee")
    robe(s, cloth(PAPER, folds=4), rows=20, hem=silk((30, 28, 30)))
    for j in range(20):
        s.fpx("jacket", "front", 0, j, tone(ramp((30, 28, 30)), 2.0)); s.fpx("jacket", "front", 7, j, tone(ramp((30, 28, 30)), 1.6))
    s.band("jacket", 9, 11, silk((30, 28, 30)))
    s.rows_map("jacket", "front", ["RRR", "RrR", "RRR"], {"R": (200, 30, 30), "r": (250, 220, 200)}, ox=3, oy=4)
    sleeves(s, cloth(PAPER), SKIN_FAIR, cuff=silk((30, 28, 30)))
    for (i, j) in ((1, 6), (2, 7), (0, 5)):
        s.fpx("arm_up", "front", i, j, (30, 28, 36))
    legs(s, cloth((60, 56, 60)), leather((30, 28, 30)), boot_rows=2)
    witch_hat(s, (silk((30, 28, 30)), "team", silk((30, 28, 30)), None), tip=plate(GOLD, lame=1))
    return s


@unit("verdant", kind="witch")
def moonwell_bearer():
    """Moonwell Bearer: a moon-silver robe over moss, a belt of three little moon lanterns, a pale crescent on the
    chest; the hat recut as a tall team cowl on a silver brim with a moon at its tip."""
    s = new("moonwell_bearer", "verdant", layout=K.WITCH, size=(64, 128))
    head(s, SKIN_FAIR, eyes=(120, 160, 200), hair=(220, 224, 230), style="long")
    robe(s, silk((196, 204, 214)), rows=20, hem=cloth(MOSS))
    for f, i, j, w, h, u, x, y in s.coords("jacket", SIDES, (14, 18)):
        if K.h01(s.seed, u, j, 70) < 0.3:
            s.put(x, y, tone(ramp(MOSS), 2.2))
    belt(s, 10, leather((70, 56, 40)))
    for i in (1, 4, 6):
        s.fpx("jacket", "front", i, 12, tone(ramp(SILVER), 3.4)); s.fpx("jacket", "front", i, 13, (250, 240, 170))
    s.emblem("jacket", "front", 2, 3, "moon", SILVER)
    sleeves(s, cloth(MOSS), SKIN_FAIR, cuff=silk((196, 204, 214)))
    legs(s, cloth((60, 80, 56)), leather((60, 50, 40)), boot_rows=2)
    witch_hat(s, (plate(SILVER, lame=2), "team", "team", None), tip=flat((250, 246, 210)))
    return s


# ======================================================================= core-faction bodies on vanilla models
# Ghoul (zombie model), Bone Legionary (husk = zombie model), Grave Archer (skeleton 64x32), Berserker (wither
# skeleton 64x32) and the Horde's piglins. These used vanilla textures; their renderers now point at these skins
# (ZombieUnitRenderer, HuskUnitRenderer, SkeletonUnitRenderer, WitherSkeletonUnitRenderer, PiglinUnitRenderer).

def skull_head(s, bone_c, socket=(20, 18, 22), glow=None, jaw=True):
    """A skull on an 8x8 head: cracked bone, deep sockets (optionally glowing), a nasal hole and a row of teeth."""
    s.paint("head", bone(bone_c, cracks=0.4))
    R = ramp(bone_c)
    for (i, j) in ((1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4)):
        s.fpx("head", "front", i, j, socket)
    if glow:
        s.fpx("head", "front", 2, 4, glow); s.fpx("head", "front", 5, 4, glow)
    s.fpx("head", "front", 3, 5, socket); s.fpx("head", "front", 4, 5, tone(R, 0.8))
    s.shade("head", 1.12, ("front",), rows=(0, 3))
    s.shade("head", 0.8, ("front",), where=lambda f, i, j, w, h: i in (0, 7) and j > 2)
    if jaw:
        for i in range(1, 7):
            s.fpx("head", "front", i, 6, tone(R, 3.2 if i % 2 else 1.0))
            s.fpx("head", "front", i, 7, tone(R, 1.6))


def ribcage(s, part, bone_c, gap=(26, 22, 26), rows=(1, 9)):
    """Ribs on the body front and back: bone bars with dark gaps, a spine down the middle."""
    R = ramp(bone_c)
    for f in ("front", "back"):
        w = s.face(part, f)[2]
        for j in range(rows[0], rows[1]):
            for i in range(w):
                if i in (3, 4):
                    c = tone(R, 2.6 if j % 2 else 2.0)
                elif j % 2 == 0:
                    c = gap
                else:
                    c = tone(R, 3.0 if i in (2, 5) else 2.2)
                s.fpx(part, f, i, j, c)


def diag(f, i, j, j0, j1, w=8, reverse=False, width=2):
    """Is (i, j) on a diagonal band from one shoulder to the other hip (front), mirrored on the back?"""
    if not (j0 <= j < j1):
        return False
    k = (j - j0) * (w - 1.0) / max(1, (j1 - j0 - 1))
    x = (w - 1 - k) if (reverse != (f == "back")) else k
    return abs(i - x) < width * 0.55


@unit("gravebound", kind="humanoid")
def zombie():
    """Ghoul: grey-green rotting flesh with darker bruising, a torn black burial shirt with bone buttons, a rope belt,
    a ragged team shroud-strip down the chest and back, soul-blue eyes, bandaged shins."""
    s = new("zombie", "gravebound", layout=K.HUMANOID)
    sk = (104, 128, 96)
    R = ramp(sk)
    s.paint("head", skinmat(sk))
    for f, i, j, w, h, u, x, y in s.coords("head", ALL):
        if K.h01(s.seed, u, j, 80) < 0.12:
            s.put(x, y, tone(R, 1.2))
    for i in (1, 2, 5, 6):
        s.fpx("head", "front", i, 3, tone(R, 0.8))
    s.fpx("head", "front", 2, 4, (120, 236, 250)); s.fpx("head", "front", 1, 4, (30, 70, 80))
    s.fpx("head", "front", 5, 4, (120, 236, 250)); s.fpx("head", "front", 6, 4, (30, 70, 80))
    for i in range(2, 6):
        s.fpx("head", "front", i, 6, tone(R, 0.6 if i % 2 else 1.0))
    s.paint("head", hairm((48, 52, 44)), ("top",), where=lambda f, i, j, w, h: K.h01(s.seed, i, j, 81) < 0.6)
    s.paint("body", cloth(BLACK_CLOTH, folds=3))
    for j in (2, 5, 8):
        s.fpx("body", "front", 4, j, tone(ramp(BONE), 3.4))
    s.band("body", 9, 10, stripes(cloth((150, 130, 96)), cloth((110, 96, 70)), 1, horizontal=False))
    s.team_paint("body", ("front", "back"), rows=(0, 12), where=lambda f, i, j, w, h: 1 <= i <= 2 and not (j > 9 and i == 2), folds=0)
    for f, i, j, w, h, u, x, y in s.coords("body", SIDES, (10, 12)):   # the ragged hem shows the flesh
        if (u + j) % 3 == 0:
            s.put(x, y, skinmat(sk)(s, f, i, j, w, h, u))
    s.paint("arm", cloth(BLACK_CLOTH), SIDES, rows=(0, 5))
    s.paint("arm", cloth(BLACK_CLOTH), ("top",))
    s.paint("arm", skinmat(sk), SIDES, rows=(5, 12))
    s.paint("arm", skinmat(sk), ("bottom",))
    for (i, j) in ((1, 7), (2, 8), (1, 9)):
        s.fpx("arm", "front", i, j, (50, 34, 34))
    s.paint("leg", cloth((66, 62, 58)))
    for j in range(5, 12, 2):
        s.band("leg", j, j + 1, cloth((176, 166, 140)))
    s.paint("leg", leather((50, 40, 32)), ("bottom",))
    s.clear_part("hat")
    return s


@unit("gravebound", kind="humanoid")
def husk():
    """Bone Legionary: dried sand-dark flesh under a legionary's bone-plate cuirass and greaves, a black-iron helm
    with a team crest, a kilt of team and leather strips over the hips, soul-blue eyes."""
    s = new("husk", "gravebound", layout=K.HUMANOID)
    sk = (150, 126, 92)
    R = ramp(sk)
    s.paint("head", skinmat(sk))
    for i in (2, 5):
        s.fpx("head", "front", i, 4, (120, 236, 250))
    for i in (1, 6):
        s.fpx("head", "front", i, 4, tone(R, 0.7))
    for i in range(2, 6):
        s.fpx("head", "front", i, 6, tone(R, 0.8))
    s.paint("body", plate(BONE, lame=2, rivet=IRON))
    s.emblem("body", "front", 2, 1, "skull", (230, 222, 196), inlay=(40, 160, 190))
    s.team_paint("body", SIDES, rows=(9, 12), where=lambda f, i, j, w, h: i % 2 == 0, folds=0)
    s.paint("body", leather((60, 44, 30)), SIDES, rows=(9, 12), where=lambda f, i, j, w, h: i % 2 == 1)
    s.paint("arm", skinmat(sk))
    s.paint("arm", plate(BONE, lame=2), SIDES, rows=(0, 3))
    s.paint("arm", plate(BONE, lame=2), ("top",))
    s.band("arm", 6, 10, plate(IRON, lame=2))
    s.paint("leg", skinmat(sk))
    s.band("leg", 5, 12, plate(BONE, lame=3))
    s.paint("leg", leather((50, 40, 30)), ("bottom",))
    s.clear_part("hat")
    H = plate((58, 58, 66), lame=3)
    s.paint("hat", H, ("top",))
    s.paint("hat", H, SIDES, rows=(0, 3))
    s.paint("hat", H, ("front",), rows=(3, 7), where=lambda f, i, j, w, h: i in (0, 7))
    s.paint("hat", H, ("back",), rows=(3, 6))
    s.team_paint("hat", ("top",), where=lambda f, i, j, w, h: i in (3, 4), folds=0)
    s.team_paint("hat", ("back",), rows=(0, 3), where=lambda f, i, j, w, h: i in (3, 4), folds=0)
    return s


@unit("gravebound", kind="skeleton")
def skeleton():
    """Grave Archer: weathered, cracked bone, a ribcage, a black hooded cowl on the hat layer, soul-blue sparks in the
    sockets, a team rag sash across the ribs, a leather grip wrap."""
    s = new("skeleton", "gravebound", layout=K.SKELETON, size=(64, 32))
    skull_head(s, BONE, glow=(110, 230, 246))
    s.paint("body", bone(BONE))
    ribcage(s, "body", BONE)
    s.paint("body", bone(sh(BONE, 0.9)), SIDES, rows=(9, 12))
    s.team_paint("body", ("front", "back"), where=lambda f, i, j, w, h: diag(f, i, j, 0, 12), folds=0)
    s.paint("arm", bone(BONE, cracks=0.3))
    s.band("arm", 5, 6, bone(sh(BONE, 0.8)))
    s.band("arm", 9, 10, leather((70, 50, 34)))
    s.paint("leg", bone(BONE, cracks=0.3))
    s.band("leg", 5, 6, bone(sh(BONE, 0.8)))
    hood(s, mat=cloth(BLACK_CLOTH, folds=3), inner=(8, 8, 12), opening=(1, 6, 2, 7), collar=False)
    return s


@unit("ironhide", kind="skeleton")
def wither_skeleton():
    """Berserker: soot-black scorched bone, a spiked rust-iron collar and bracers, a fur loin-wrap, a team war sash at
    the waist and team war paint across the brow and crown."""
    s = new("wither_skeleton", "ironhide", layout=K.SKELETON, size=(64, 32))
    dark = (58, 56, 58)
    skull_head(s, dark, socket=(10, 8, 8), glow=(250, 120, 40))
    s.team_paint("head", ("front",), rows=(1, 2), where=lambda f, i, j, w, h: i in (1, 2, 5, 6), folds=0)
    s.team_paint("head", ("top",), where=lambda f, i, j, w, h: i in (3, 4), folds=0)
    s.paint("body", bone(dark))
    ribcage(s, "body", (64, 62, 64), gap=(14, 12, 12))
    s.band("body", 0, 2, plate(RUST, lame=2))
    for f in SIDES:
        s.fpx("body", f, 1, 0, tone(ramp(STEEL), 3.4))
    s.team_paint("body", SIDES, rows=(8, 10), folds=0)
    s.paint("body", fur(FUR_BROWN), SIDES, rows=(10, 12))
    s.paint("arm", bone(dark, cracks=0.3))
    s.band("arm", 6, 10, plate(RUST, lame=2))
    s.paint("leg", bone(dark, cracks=0.3))
    s.band("leg", 0, 4, fur(FUR_BROWN))
    s.clear_part("hat")
    return s


def piglin_head(s, skin=PIG, bristle=(90, 50, 34), eyes=(40, 30, 20), scar=False, paint=None, tusks=BONE):
    """The piglin head (10x8x8): sculpted pink hide, heavy brow, small eyes, a bristle crest, the snout box with its
    nostrils, bone tusks and floppy ears."""
    R = ramp(skin)
    s.paint("head", skinmat(skin))
    s.shade("head", 1.1, ("front",), rows=(0, 2))
    s.shade("head", 0.86, ("front",), rows=(6, 8))
    for i in range(1, 9):
        s.fpx("head", "front", i, 2, tone(R, 1.4))
    for (i, c) in ((2, (236, 230, 220)), (3, eyes), (6, eyes), (7, (236, 230, 220))):
        s.fpx("head", "front", i, 3, c)
    for i in (2, 3, 6, 7):
        s.fpx("head", "front", i, 4, tone(R, 1.6))
    if scar:
        for k in range(4):
            s.fpx("head", "front", 7 + k % 2, 1 + k, tone(R, 3.5))
    if paint:
        P = ramp(paint)
        for i in (0, 1, 8, 9):
            s.fpx("head", "front", i, 4, tone(P, 2.2)); s.fpx("head", "front", i, 5, tone(P, 1.6))
    B = hairm(bristle)
    s.paint("head", B, ("top",), where=lambda f, i, j, w, h: 3 <= i <= 6)
    s.paint("head", B, ("back",), rows=(0, 4), where=lambda f, i, j, w, h: 3 <= i <= 6)
    s.paint("head", B, ("front",), rows=(0, 1), where=lambda f, i, j, w, h: 3 <= i <= 6)
    s.paint("snout", skinmat(sh(skin, 1.04)))
    for (i, j) in ((1, 1), (2, 1), (1, 2), (2, 2)):
        s.fpx("snout", "front", i, j, tone(R, 0.7 if j == 2 else 1.1))
    s.shade("snout", 1.12, ("front",), rows=(0, 1))
    for t in ("tusk_r", "tusk_l"):
        s.paint(t, bone(tusks, cracks=0))
    for e in ("ear_r", "ear_l"):
        s.paint(e, skinmat(sh(skin, 0.94)))
        s.shade(e, 0.8, rows=(3, 5))


def piglin_limbs(s, arm, leg, hand, boot, bracer=None, boot_rows=4):
    for a in ("arm_r", "arm_l"):
        s.paint(a, arm)
        if bracer:
            s.band(a, 6, 10, bracer)
        s.band(a, 10, 12, skinmat(hand))
        s.paint(a, skinmat(hand), ("bottom",))
    for l in ("leg_r", "leg_l"):
        s.paint(l, leg)
        s.band(l, 12 - boot_rows, 12, boot)
        s.paint(l, boot, ("bottom",))
        s.shade(l, 1.18, rows=(12 - boot_rows, 12 - boot_rows + 1))


@unit("ironhide", kind="piglin")
def grunt():
    """Clan Builder (worker): pink hide with a bristle crest, a shaggy fur vest (outer layer) open over a leather
    shirt, a belt with a bone toggle, a team sash across the vest, leather bracers."""
    s = new("grunt", "ironhide", layout=K.PIGLIN)
    piglin_head(s)
    s.paint("body", leather((120, 82, 50)))
    s.band("body", 9, 11, leather((70, 50, 32)))
    s.fpx("body", "front", 3, 9, tone(ramp(BONE), 3.4)); s.fpx("body", "front", 4, 9, tone(ramp(BONE), 2.4))
    s.paint("jacket", fur(FUR_BROWN), SIDES, rows=(0, 8), where=lambda f, i, j, w, h: not (f == "front" and 2 <= i <= 5))
    s.paint("jacket", fur(FUR_BROWN), ("top",))
    s.team_paint("jacket", ("front", "back"), where=lambda f, i, j, w, h: diag(f, i, j, 0, 9), folds=0)
    piglin_limbs(s, leather((120, 82, 50)), cloth((96, 70, 50)), PIG, leather((60, 44, 30)), bracer=leather((80, 56, 36)))
    for sl in ("sleeve_r", "sleeve_l"):
        s.paint(sl, fur(FUR_BROWN), SIDES, rows=(0, 4))
        s.paint(sl, fur(FUR_BROWN), ("top",))
    return s


@unit("ironhide", kind="piglin")
def brute():
    """Shieldbearer: rust-red iron plate (riveted lames) over a mail shirt, bone pauldrons on the sleeves, a fur
    collar, a team tabard with a bone tusk emblem, a scarred face."""
    s = new("brute", "ironhide", layout=K.PIGLIN)
    piglin_head(s, skin=(212, 140, 120), bristle=(60, 36, 26), scar=True)
    s.paint("body", mail(IRON))
    s.paint("jacket", plate(RUST, lame=3, rivet=STEEL), SIDES, rows=(0, 9))
    s.paint("jacket", fur(FUR_BROWN), SIDES, rows=(0, 2))
    s.paint("jacket", fur(FUR_BROWN), ("top",))
    tabard(s, j0=2, j1=12, cols=(2, 6), point=True, back=True, trim=BONE)
    s.emblem("jacket", "front", 2, 3, "tusk", BONE)
    piglin_limbs(s, mail(IRON), cloth((70, 50, 40)), (212, 140, 120), plate(RUST, lame=2), bracer=plate(RUST, lame=2))
    for sl in ("sleeve_r", "sleeve_l"):
        s.paint(sl, plate(BONE, lame=2), SIDES, rows=(0, 4))
        s.paint(sl, plate(BONE, lame=2), ("top",))
        for f in SIDES:
            s.fpx(sl, f, 1, 0, tone(ramp(BONE), 3.6))
    for pl in ("pants_r", "pants_l"):
        s.paint(pl, plate(RUST, lame=3), SIDES, rows=(3, 7))
    return s


@unit("ironhide", kind="piglin")
def headhunter():
    """Spear Thrower: a fur cloak over the back and shoulders (outer layer), a bone necklace, black war paint, a team
    sash and team arm bands, bone bracers, striped leg wraps."""
    s = new("headhunter", "ironhide", layout=K.PIGLIN)
    piglin_head(s, skin=(216, 146, 124), bristle=(40, 30, 24), paint=(40, 30, 30))
    s.paint("body", leather((96, 66, 42)))
    for i in range(8):
        s.fpx("body", "front", i, 1 if i in (0, 7) else 2, tone(ramp(BONE), 3.3 if i % 2 else 2.0))
    F = fur((92, 74, 60))
    s.paint("jacket", F, ("back", "right", "left"), rows=(0, 12))
    s.paint("jacket", F, ("top",))
    s.paint("jacket", F, ("front",), rows=(0, 2), where=lambda f, i, j, w, h: i in (0, 1, 6, 7))
    s.team_paint("jacket", ("front",), where=lambda f, i, j, w, h: diag(f, i, j, 2, 11, reverse=True), folds=0)
    piglin_limbs(s, leather((96, 66, 42)), stripes(cloth((90, 70, 50)), cloth((60, 46, 34)), 2), (216, 146, 124),
                 leather((50, 38, 28)), bracer=bone(BONE, cracks=0.2), boot_rows=3)
    for sl in ("sleeve_r", "sleeve_l"):
        s.team_paint(sl, SIDES, rows=(1, 3), folds=0)
        s.paint(sl, F, ("top",))
    return s


CHEVRON = ["......", "X....X", "XX..XX", ".XXXX.", "..XX..", "......", "..XX..", "......"]


def captain_banner():
    """BackBannerLayer's texture (32x32): the pole (box 1x26x1 at 0,0) in dark wood with a gold finial and binding,
    and the flag (box 8x10x1 at 4,0) in grey-scale cloth - multiplied by the owner colour in game - with a pale border,
    a pale chevron, folds and a darker fringe, so the colour reads as cloth and not as a flat card."""
    from PIL import Image
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    W = ramp((96, 66, 40))
    G = ramp(GOLD)
    for (x, y, w, h) in ((0, 1, 1, 26), (1, 1, 1, 26), (2, 1, 1, 26), (3, 1, 1, 26), (1, 0, 1, 1), (2, 0, 1, 1)):
        for j in range(h):
            for i in range(w):
                t = 2.0 + (0.5 if x == 1 else -0.3 if x in (0, 2) else -0.6) + 0.3 * ((j // 2) % 2)
                c = tone(W, t)
                if j < 2 or j in (12, 13):   # finial and a gold binding where the strap holds it
                    c = tone(G, 3.2 if x == 1 else 2.2)
                px[x + i, y + j] = c + (255,)
    for (x, y, w, h, face) in ((5, 1, 8, 10, "front"), (14, 1, 8, 10, "back"), (4, 1, 1, 10, "side"), (13, 1, 1, 10, "side"),
                               (5, 0, 8, 1, "top"), (13, 0, 8, 1, "top")):
        for j in range(h):
            for i in range(w):
                t = 2.2 - 0.5 * (j / 9.0) + 0.35 * K.math.sin(i * 1.4)
                if face in ("front", "back"):
                    if i in (0, w - 1) or j == 0:
                        t = 3.3   # pale border
                    elif 1 <= j <= 8 and CHEVRON[j - 1][i - 1] == "X":
                        t = 3.6   # a pale chevron over a pale stud
                    if j == h - 1:
                        t = 1.2 if i % 2 else 0.8   # fringe
                v = K.clamp(118 + 40 * max(0.6, min(3.6, t)))
                px[x + i, y + j] = (v, v, v, 255)
    im.save(os.path.join(K.ENT, "captain_banner.png"))
    return im


# ======================================================================= main
def main():
    only = None
    if "--only" in sys.argv:
        only = set(sys.argv[sys.argv.index("--only") + 1].split(","))
    preview = "--no-preview" not in sys.argv and only is None   # a partial run would overwrite the sheets with fewer units
    by_faction = {}
    captain_banner()
    for name, (faction, fn, kind) in UNITS.items():
        if only and name not in only:
            continue
        s = fn()
        s.save()
        team, bg_t, bg_b, frame = FACTIONS[faction]
        if kind == "villager":
            por, mock = K.portrait(s, bg_t, bg_b, frame), K.mock_villager
        elif kind == "witch":
            por, mock = K.portrait(s, bg_t, bg_b, frame, layout="witch"), K.mock_witch
        else:
            L = s.L
            por = K.portrait(s, bg_t, bg_b, frame, layout=L)
            mock = (lambda img, back, L=L: K.mock_humanoid(img, L, back))
        # keep each portrait at the size the HUD already uses for it
        hp = os.path.join(K.HEADS, f"{name}.png")
        if os.path.exists(hp):
            from PIL import Image as _I
            old = _I.open(hp).size
            if old != por.size:
                por = por.resize(old, _I.NEAREST)
        por.save(hp)
        by_faction.setdefault(faction, []).append((name, s, mock, por))
        print(f"{faction:13s} textures/entities/{name}_unit.png{' (+team)' if s.team.getbbox() else ''}")
    if preview:
        for faction, entries in by_faction.items():
            path = os.path.join(K.DESIGN, f"skins_preview_{faction}.png")
            K.contact_sheet(path, f"{faction} - flat UV (team in red) | front/back red | front/back blue | portrait | 1x", entries)
            print("preview", os.path.relpath(path, K.ROOT))


if __name__ == "__main__":
    main()
