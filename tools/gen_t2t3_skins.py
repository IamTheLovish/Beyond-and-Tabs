#!/usr/bin/env python3
"""Draws the original factions' T2 workers and T3 experimentals their own skins and portraits (original pixel art).

Reuses the skin toolkit of tools/gen_verdant_skins.py (Skin, faces, villager face / robe / arms / legs helpers) so the
new skins are painted the same way as the Court's and the Tidewrought's. Every skin keeps the UV layout of the
model its renderer uses:
  - VillagerUnitModel (64x64): Royal Architect (RoyalArchitectRenderer) and Embalmer (ZombieVillagerUnitRenderer's
    per-type texture). The head's 8x12x8 "hat" box (32,0) is drawn wherever it is opaque: a gold circlet, a crown
    of bandage wraps.
  - vanilla PiglinModel (64x64 player layout + snout, tusks, ears): Bonewright (PiglinUnitRenderer's map). The
    jacket / sleeve / trouser overlay boxes stand 0.25 off the body, so the bone-plated apron sits on top of the
    tunic with real depth.
  - vanilla RavagerModel (128x128): War Mammoth. The body boxes are rotated 90 degrees about X, so their "back" faces
    are what the RTS camera sees: that is where the archer platform is painted.
  - vanilla IronGolemModel (128x128): Sun Colossus.
  - vanilla PhantomModel (64x64): Bone Dragon (cut-out render type, so the wing membranes can be torn).

Art direction (claude/design-factions.md): Sunforged = gold-trimmed white plate, sun banners; Ironhide Horde =
rust-red iron, bone plate, furs; Gravebound = black, bone, soul-blue. As for the other factions, the top faces carry
the strongest shapes (the RTS camera looks down), with strong value steps and light noise.

Portraits follow each faction's existing mob-head framing (frameless, the face fills the tile): the villager-bodied
heads are 12x16 art centred on a transparent 64x64 (like villager.png), the Bonewright 16x13 (like grunt.png, saved
64x51), the mammoth 15x16 (like ravager.png) plus its *_half variant cut with ravager_half.png's mask, the dragon a
full 16x16 (like phantom.png). All at 4x.

usage: python3 tools/gen_t2t3_skins.py [--preview DIR]
  writes bar/.../textures/entities/<unit>_unit.png and bar/.../textures/mobheads/<unit>.png;
  --preview also writes flat-UV sheets with mock-ups and a portrait sheet into DIR.
"""
import importlib.util, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("gen_verdant_skins", os.path.join(HERE, "gen_verdant_skins.py"))
V = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(V)

Skin, VILLAGER, SIDES, faces = V.Skin, V.VILLAGER, V.SIDES, V.faces
sh, mix, villager_face, robe, arms, legs, shoulder_tops = V.sh, V.mix, V.villager_face, V.robe, V.arms, V.legs, V.shoulder_tops
ENT, HEADS = V.ENT, V.HEADS

# ------------------------------------------------------------------ extra box layouts: (u, v, w, h, d)
PIGLIN = {"head": (0, 0, 10, 8, 8), "snout": (31, 1, 4, 4, 1), "tusk_r": (2, 0, 1, 2, 1), "tusk_l": (2, 4, 1, 2, 1),
          "ear_l": (51, 6, 1, 5, 4), "ear_r": (39, 6, 1, 5, 4),
          "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
          "arm_r": (40, 16, 4, 12, 4), "sleeve_r": (40, 32, 4, 12, 4),
          "arm_l": (32, 48, 4, 12, 4), "sleeve_l": (48, 48, 4, 12, 4),
          "leg_r": (0, 16, 4, 12, 4), "pants_r": (0, 32, 4, 12, 4),
          "leg_l": (16, 48, 4, 12, 4), "pants_l": (0, 48, 4, 12, 4)}
RAVAGER = {"head": (0, 0, 16, 20, 16), "nose": (0, 0, 4, 8, 4), "horn": (74, 55, 2, 14, 4),
           "mouth": (0, 36, 16, 3, 16), "body": (0, 55, 14, 16, 20), "body2": (0, 91, 12, 13, 18),
           "neck": (68, 73, 10, 10, 18), "leg_h": (96, 0, 8, 37, 8), "leg_f": (64, 0, 8, 37, 8)}
GOLEM = {"head": (0, 0, 8, 10, 8), "nose": (24, 0, 2, 4, 2), "body": (0, 40, 18, 12, 11), "waist": (0, 70, 9, 5, 6),
         "arm_r": (60, 21, 4, 30, 6), "arm_l": (60, 58, 4, 30, 6), "leg_r": (37, 0, 6, 16, 5), "leg_l": (60, 0, 6, 16, 5)}
PHANTOM = {"head": (0, 0, 7, 3, 5), "body": (0, 8, 5, 3, 9), "tail": (3, 20, 3, 2, 6), "tail_tip": (4, 29, 1, 1, 6),
           "wing": (23, 12, 6, 2, 9), "wing_tip": (16, 24, 13, 1, 9)}

# ------------------------------------------------------------------ palettes
WHITE, WHITE_D, IVORY = (238, 234, 222), (196, 190, 176), (226, 218, 196)
GOLD_D, GOLD, GOLD_L = (138, 98, 30), (212, 168, 58), (250, 222, 120)
ROYAL_D, ROYAL = (28, 40, 96), (44, 66, 150)
BLUEPRINT, BLUEPRINT_L = (40, 86, 160), (150, 196, 240)
LEATHER_D, LEATHER, LEATHER_L = (56, 38, 26), (96, 66, 42), (140, 100, 64)
SKIN_FAIR = (226, 190, 156)
RUBY = (220, 90, 40)

ROT_D, ROT, ROT_L = (62, 82, 58), (100, 128, 92), (138, 160, 120)
BLACK_D, BLACK, BLACK_L = (20, 20, 26), (36, 36, 44), (58, 58, 70)
LINEN, LINEN_D = (214, 206, 182), (160, 150, 124)
BONE_D, BONE, BONE_L = (170, 160, 130), (218, 210, 184), (244, 240, 224)
SOUL_D, SOUL, SOUL_L = (30, 110, 140), (70, 200, 230), (170, 240, 255)
CANVAS, CANVAS_D = (112, 104, 94), (76, 70, 64)

PIG, PIG_D, PIG_L = (232, 156, 140), (190, 110, 100), (246, 190, 170)
RUST_D, RUST, RUST_L = (92, 36, 22), (150, 62, 34), (196, 102, 58)
IRON_D, IRON, IRON_L = (58, 58, 64), (104, 104, 112), (160, 160, 168)
FUR_D, FUR, FUR_L = (60, 44, 32), (100, 76, 54), (146, 118, 86)

WOOL_D, WOOL, WOOL_L = (50, 32, 24), (88, 58, 40), (122, 86, 58)
HIDE_D, HIDE = (58, 48, 44), (100, 86, 78)
WOOD_D, WOOD, WOOD_L = (70, 46, 28), (116, 80, 48), (162, 120, 76)
BANNER, BANNER_D = (168, 42, 34), (110, 26, 22)

STONE_D, STONE, STONE_L = (170, 164, 150), (214, 208, 194), (240, 236, 226)


def put_rows(s, part, f, rows, pal, ox=0, oy=0):
    """Paint a small string map onto one face ('.' keeps what is there, ' ' clears to transparent)."""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == ' ':
                s.fclear(part, f, ox + i, oy + j)
            elif ch != '.' and ch in pal:
                s.fpx(part, f, ox + i, oy + j, pal[ch])


def shag(s, part, f, base, dark, light, along="v", seed_step=3, tip=True):
    """Shaggy fur on one face: strands running along 'v' (texture rows, hanging down) or 'u' (texture columns),
    three tones only - long locks in the base, a darker parting every few strands, light tips - so it reads as wool
    from a distance instead of noise."""
    x, y, w, h = s.face(part, f)
    n_str, n_len = (w, h) if along == "v" else (h, w)
    for k in range(n_str):
        dark_strand = (k % seed_step == 0)
        off = s.rng.randint(0, 3)
        for t in range(n_len):
            c = dark if dark_strand and (t + off) % 5 != 0 else base
            if tip and not dark_strand and (t + off) % 9 == 8:
                c = light
            px, py = (x + k, y + t) if along == "v" else (x + t, y + k)
            s.put(px, py, s.n(c, 4))


# ================================================================== T2 workers
def royal_architect():
    """The Sunforged T2 constructor: an elder master-builder in white robes with a gold trim down the front, a gold sun
    on the back, a gold circlet with a sun-gem (a gold ring seen from above), a blueprint satchel slung across the
    chest with rolled plans poking out, and a brass plumb-bob hanging from the belt."""
    s = Skin(VILLAGER, (64, 64), 61)
    villager_face(s, SKIN_FAIR, (70, 110, 160), (230, 226, 216), hair=(214, 210, 200), beard=(236, 232, 224))
    # a trimmed white goatee: clear the beard back to skin at the cheeks so it reads as a goatee, not a full beard
    for j in range(7, 10):
        for i in (0, 1, 6, 7):
            s.fpx("head", "front", i, j, SKIN_FAIR)
    s.fpx("head", "front", 5, 4, GOLD_L); s.fpx("head", "front", 7, 4, GOLD_L)    # a gold monocle rim round the eye
    s.fpx("head", "front", 6, 3, GOLD_L); s.fpx("head", "front", 6, 5, GOLD_L)
    s.fpx("head", "front", 7, 6, GOLD); s.fpx("head", "front", 7, 7, GOLD_D)      # its chain
    robe(s, WHITE, GOLD, trim=GOLD_L, sash=8, sash_c=GOLD_D)
    # the sun on the back of the robe: a gold disc with eight rays (reads from behind and from the raised camera)
    sun = ["...Y....",
           ".Y.Y.Y..",
           "..YYY...",
           "YYYOYYY.",
           "..YYY...",
           ".Y.Y.Y..",
           "...Y...."]
    put_rows(s, "jacket", "back", sun, {"Y": GOLD, "O": RUBY}, ox=0, oy=2)
    # the satchel strap: blueprint blue from the right shoulder to the left hip, across front and back
    for j in range(9):
        s.fpx("jacket", "front", 7 - min(7, j), j, BLUEPRINT)
        s.fpx("body", "front", 7 - min(7, j), j, BLUEPRINT)
        s.fpx("jacket", "back", min(7, j), j, BLUEPRINT)
    # the satchel on the left hip: leather with a brass clasp, two rolled plans standing out of it
    for j in range(8, 13):
        for i in range(1, 6):
            s.fpx("jacket", "left", i, j, s.n(LEATHER, 4))
    for i in range(1, 6):
        s.fpx("jacket", "left", i, 8, LEATHER_D)
    s.fpx("jacket", "left", 3, 10, GOLD_L)
    for j in range(5, 8):
        s.fpx("jacket", "left", 2, j, BLUEPRINT_L); s.fpx("jacket", "left", 4, j, BLUEPRINT)
    s.fpx("jacket", "left", 2, 4, WHITE); s.fpx("jacket", "left", 4, 5, BLUEPRINT_L)
    # the plumb-bob: a gold cord down the front right of the robe ending in a heavy pointed brass bob
    for j in range(9, 13):
        s.fpx("jacket", "front", 1, j, GOLD_L)
    put_rows(s, "jacket", "front", ["Y.", "YD", "D."], {"Y": GOLD_L, "D": GOLD_D}, ox=0, oy=13)
    s.fpx("jacket", "front", 1, 13, GOLD); s.fpx("jacket", "front", 1, 14, GOLD_D)
    arms(s, WHITE, GOLD, SKIN_FAIR, bracer=None)
    for part in ("arm_up", "arm"):
        s.band(part, 0, 1, GOLD_D)
    # crossed arms hold a rolled blueprint across the chest
    for i in range(0, 8):
        s.fpx("arm_mid", "front", i, 1, BLUEPRINT if i not in (0, 7) else BLUEPRINT_L)
    legs(s, WHITE_D, LEATHER_D)
    s.band("leg", 8, 9, GOLD_D)
    shoulder_tops(s, GOLD_L, accent=WHITE)
    # the circlet on the hat box: a gold band at the brow (row 1) with a sun-gem at the front; from above a gold ring
    s.band("hat", 1, 2, GOLD)
    for f in SIDES:
        for i in range(0, 8, 3):
            s.fpx("hat", f, i, 0, GOLD_L)       # small points rising off the band
    s.fpx("hat", "front", 3, 1, RUBY); s.fpx("hat", "front", 4, 1, RUBY)
    s.fpx("hat", "front", 3, 0, GOLD_L); s.fpx("hat", "front", 4, 0, GOLD_L)
    for i in range(8):
        for j in range(8):
            if i in (0, 7) or j in (0, 7):
                s.fpx("hat", "top", i, j, GOLD if (i + j) % 2 else GOLD_L)
    s.fpx("hat", "top", 3, 7, RUBY); s.fpx("hat", "top", 4, 7, RUBY)
    return s


def embalmer():
    """The Gravebound T2 constructor: a grey-green corpse in black robes and a waxed embalming apron with bone buttons
    and a pocket of hooks, bandage wraps round the crown, one eye and the forearms (pale stripes that read from
    above against the black), soul-blue glowing eyes, a soul lantern at the hip and soul-blue hems."""
    s = Skin(VILLAGER, (64, 64), 62)
    villager_face(s, ROT, SOUL_L, ROT_D, hair=ROT_D)
    s.fill("nose", sh(ROT, 0.9), 3)
    for i in (1, 6):
        s.fpx("head", "front", i, 4, SOUL)        # the whites glow soul-blue too
    for i in range(1, 7):
        s.fpx("head", "front", i, 5, ROT_D)       # sunken under-eyes
    s.fpx("head", "front", 2, 8, BLACK_D); s.fpx("head", "front", 5, 8, BLACK_D)   # stitched lips
    for i in range(2, 6):
        s.fpx("head", "front", i, 7, sh(ROT, 0.75))
    # bandage strips across the head (head box itself, under the hat wraps): one across the left eye
    for (i, j) in ((4, 2), (5, 3), (6, 4), (7, 5), (5, 2), (6, 3), (7, 4)):
        s.fpx("head", "front", i, j, LINEN if (i + j) % 2 else LINEN_D)
    s.fpx("head", "front", 5, 4, LINEN)
    robe(s, BLACK, BLACK_D, sash=9, sash_c=LEATHER_D)
    s.band("jacket", 17, 18, SOUL_D)
    # the embalming apron: waxed canvas over the front, darker border, bone buttons, a hook pocket, fluid stains
    for j in range(1, 17):
        for i in range(1, 7):
            s.fpx("jacket", "front", i, j, s.n(CANVAS, 3))
    for j in range(1, 17):
        s.fpx("jacket", "front", 1, j, CANVAS_D); s.fpx("jacket", "front", 6, j, CANVAS_D)
    for i in range(1, 7):
        s.fpx("jacket", "front", i, 16, CANVAS_D)
    for j in (2, 5):
        s.fpx("jacket", "front", 3, j, BONE_L)
    put_rows(s, "jacket", "front", ["DDD", "DID", "DID"], {"D": CANVAS_D, "I": (190, 196, 204)}, ox=3, oy=11)
    for (i, j) in ((2, 8), (2, 9), (5, 13), (5, 14), (4, 7)):
        s.fpx("jacket", "front", i, j, SOUL_D)   # embalming fluid stains glow faintly
    s.fpx("jacket", "front", 2, 10, SOUL)
    # neck strap of the apron over the shoulders (on the body box)
    for j in range(0, 3):
        s.fpx("body", "front", 1, j, CANVAS_D); s.fpx("body", "front", 6, j, CANVAS_D)
    # the soul lantern on the right hip: an iron cage round a blue flame
    put_rows(s, "jacket", "right", [".K.", "KSK", "KLK", "KSK", ".K."],
             {"K": IRON_D, "S": SOUL, "L": SOUL_L}, ox=1, oy=9)
    s.fpx("jacket", "right", 2, 8, IRON)
    # a column of bone buttons down the back and a bandage across the shoulders
    for j in range(2, 15, 3):
        s.fpx("jacket", "back", 4, j, BONE)
    arms(s, BLACK, SOUL_D, ROT)
    # bandaged forearms: diagonal linen stripes over the lower sleeves
    for part in ("arm_up", "arm"):
        h = s.face(part, "front")[3]
        cols = s.wrap_cols(part)
        for k, (f, i) in enumerate(cols):
            for j in range(h - 8, h - 3):
                s.fpx(part, f, i, j, LINEN if (k + j) % 3 else LINEN_D)
    for i in (0, 1, 6, 7):
        for j in range(4):
            s.fpx("arm_mid", "front", i, j, LINEN if (i + j) % 3 else LINEN_D)
    legs(s, BLACK, BLACK_D)
    for j in (3, 7):
        s.band("leg", j, j + 1, LINEN_D)
    shoulder_tops(s, BONE, accent=SOUL)
    # the crown wraps on the hat box: diagonal bandage stripes on top, strips round the brow, a loose end at the back
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, LINEN if (i + j) % 3 else LINEN_D)
    s.fpx("hat", "top", 3, 4, SOUL); s.fpx("hat", "top", 4, 3, SOUL)    # a soul-blue sigil painted on the crown
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 0, LINEN_D if (i % 3 == 0) else LINEN)
            s.fpx("hat", f, i, 1, LINEN if (i % 3 == 1) else LINEN_D)
    for j in range(2, 7):
        s.fpx("hat", "back", 5, j, LINEN if j % 2 else LINEN_D)
    for (i, j) in ((6, 2), (7, 2), (7, 3)):
        s.fpx("hat", "front", i, j, LINEN)       # the eye wrap continues onto the wraps
    return s


def bonewright():
    """The Horde T2 constructor: a piglin smith in a dark leather tunic under a bone-plated apron (overlay layer, so it
    stands off the body), a hoglin skull worn as a cap with its tusks curling down past the cheeks, a fur mantle on
    the shoulders, a belt of hammers and rust-iron bracers."""
    s = Skin(PIGLIN, (64, 64), 63)
    # head: piglin skin, darker brow, small eyes, then the skull cap over the crown
    s.fill("head", PIG, 4)
    s.vshade("head", 1.04, 0.9)
    put_rows(s, "head", "front",
             ["..........",
              "..........",
              "..........",
              ".DDD..DDD.",
              ".WE....EW.",
              "..........",
              "..........",
              ".s......s."],
             {"D": PIG_D, "W": (240, 236, 228), "E": (60, 26, 20), "s": PIG_D})
    s.fill("snout", PIG_L, 3)
    s.fpx("snout", "front", 1, 2, PIG_D); s.fpx("snout", "front", 2, 2, PIG_D)
    s.fpx("snout", "front", 0, 1, PIG_D); s.fpx("snout", "front", 3, 1, PIG_D)
    s.fill("tusk_r", BONE_L, 0); s.fill("tusk_l", BONE_L, 0)
    s.fill("ear_l", PIG, 3); s.fill("ear_r", PIG, 3)
    for ear in ("ear_l", "ear_r"):
        s.band(ear, 3, 5, FUR)                    # fur tufts on the ear tips
    # the skull cap: bone over the top face and the top rows, eye sockets above the brow, tusks curving down
    s.fill("head", BONE, 5, which=("top",))
    x, y, w, h = s.face("head", "top")
    for i in range(w):
        s.put(x + i, y + h // 2, BONE_D)           # the skull's suture down... across the crown
    for j in range(h):
        s.put(x + w // 2, y + j, BONE_D)
    s.band("head", 0, 3, BONE)
    s.band("head", 2, 3, BONE_D)
    put_rows(s, "head", "front",
             ["..KK..KK..",
              ".KRKBBKRK.",
              "T........T",
              "T........T",
              "TT......TT"],
             {"K": (40, 30, 26), "R": (230, 120, 40), "B": BONE_D, "T": BONE_L})
    for f in ("right", "left"):
        s.fpx("head", f, 1, 3, BONE_L); s.fpx("head", f, 1, 4, BONE_L)
    for i in range(10):
        s.fpx("head", "back", i, 3, FUR_D if i % 2 else FUR)
    # body: dark leather tunic, a fur mantle on top
    s.fill("body", LEATHER_D, 4)
    s.fill("body", FUR, 5, which=("top",))
    s.band("body", 0, 2, FUR)
    s.band("body", 8, 9, LEATHER)
    # the apron (jacket overlay): bone plates in rows on the front, leather straps behind, belt of hammers
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for j in range(h):
            for i in range(w):
                s.clear(x + i, y + j)
    plates = ["........",
              ".PPLPPL.",
              ".PDPPDP.",
              ".LLLLLL.",
              "PPLPPLPP",
              "PDPPDPPD",
              "BBBBBBBB",
              "BIBBBBIB",
              "LPPLPPLP",
              "PPDPPDPP",
              "LLLLLLLL",
              ".P.PP.P."]
    put_rows(s, "jacket", "front", plates,
             {"P": BONE, "D": BONE_D, "L": LEATHER_D, "B": LEATHER, "I": RUST_L})
    for f in ("right", "left"):
        put_rows(s, "jacket", f, ["....", "....", "....", "....", "....", "....", "BBBB", "BHHB", ".HH.", ".WW.", ".WW.", "...."],
                 {"B": LEATHER, "H": IRON_L, "W": WOOD})   # a hammer hanging off each hip
    put_rows(s, "jacket", "back", ["L....L", ".L..L.", "..LL..", "..LL..", ".L..L.", "L....L", "BBBBBB"],
             {"L": LEATHER_D, "B": LEATHER}, ox=1, oy=0)
    # arms: piglin skin, leather wraps and rust bracers (sleeve overlay), fur pauldrons on top
    for arm, slv in (("arm_r", "sleeve_r"), ("arm_l", "sleeve_l")):
        s.fill(arm, PIG, 4)
        s.fill(arm, FUR, 4, which=("top",))
        s.band(arm, 0, 3, FUR)
        s.band(arm, 10, 12, PIG_D)
        for f in faces(PIGLIN[slv]):
            x, y, w, h = s.face(slv, f)
            for j in range(h):
                for i in range(w):
                    s.clear(x + i, y + j)
        s.band(slv, 5, 9, RUST)
        s.band(slv, 5, 6, RUST_L)
        s.band(slv, 8, 9, RUST_D)
        for f in SIDES:
            s.fpx(slv, f, 1, 7, IRON_L)
        s.fill(slv, FUR_L, 3, which=("top",))
        s.band(slv, 0, 2, FUR_L)
        for f in SIDES:
            for i in range(4):
                if (i + 1) % 2:
                    s.fpx(slv, f, i, 2, FUR)
    # legs: dark trousers, rust knee plates, fur-wrapped boots
    for leg, pants in (("leg_r", "pants_r"), ("leg_l", "pants_l")):
        s.fill(leg, LEATHER_D, 4)
        s.band(leg, 8, 12, FUR_D)
        s.band(leg, 8, 9, FUR)
        for f in faces(PIGLIN[pants]):
            x, y, w, h = s.face(pants, f)
            for j in range(h):
                for i in range(w):
                    s.clear(x + i, y + j)
        put_rows(s, pants, "front", ["....", "....", "....", "....", ".RR.", "RLLR", ".RR."], {"R": RUST, "L": RUST_L})
    return s


# ================================================================== T3 experimentals
def war_mammoth():
    """The Horde T3: a woolly titan. Shaggy brown wool hanging down every side, a leathery face under a rust-iron
    chanfron studded with bone, small amber eyes, a wrinkled trunk, ivory horns; on its back the archer platform -
    a timber deck with rust-iron rails and a red war-blanket with bone fringes hanging down its flanks."""
    s = Skin(RAVAGER, (128, 128), 64)
    # ---- legs: wool down to the knee, leathery lower leg, pale toenails
    for leg in ("leg_h", "leg_f"):
        for f in SIDES:
            shag(s, leg, f, WOOL, WOOL_D, WOOL_L)
        for f in SIDES:
            for j in range(26, 37):
                for i in range(8):
                    s.fpx(leg, f, i, j, s.n(HIDE if j < 33 else HIDE_D, 3))
            for i in range(8):
                s.fpx(leg, f, i, 25 + (i * 3) % 3, WOOL_D)      # the wool's ragged lower edge
        for i in (1, 3, 5):
            s.fpx(leg, "front", i, 35, BONE); s.fpx(leg, "front", i + 1, 35, BONE)   # toenails
            s.fpx(leg, "front", i, 36, BONE_D)
        s.fill(leg, HIDE, 3, which=("top",))
        s.fill(leg, HIDE_D, 2, which=("bottom",))
    # ---- body (rotated): side faces have the vertical axis along u (col 0 = spine on the right face, col 19 = spine
    # on the left face), the back face is the top
    for part in ("body", "body2"):
        for f in ("right", "left"):
            shag(s, part, f, WOOL, WOOL_D, WOOL_L, along="u")
        shag(s, part, "front", WOOL_D, HIDE_D, WOOL, along="u")       # belly: darker
        shag(s, part, "top", WOOL, WOOL_D, WOOL_L, along="v")         # chest / rear
        shag(s, part, "bottom", WOOL, WOOL_D, WOOL_L, along="v")
    # the archer platform on body's back face (14 wide, 16 long, row 0 at the head end)
    deck = ["RRRRRRRRRRRRRR",
            "RWwWwWwWwWwWwR",
            "RwWwWwWwWwWwWR",
            "RWwWwWwWwWwWwR",
            "RwWwWwWwWwWwWR",
            "RWwWwWwWwWwWwR",
            "RIwWwWwWwWwWIR",
            "RRRRRRRRRRRRRR",
            "RWwWwWwWwWwWwR",
            "RwWwWwWwWwWwWR",
            "RWwWwWwWwWwWwR",
            "RwWwWwWwWwWwWR",
            "RWwWwWwWwWwWwR",
            "RIwWwWwWwWwWIR",
            "RwWwWwWwWwWwWR",
            "RRRRRRRRRRRRRR"]
    put_rows(s, "body", "back", deck, {"R": RUST, "W": WOOD, "w": WOOD_L, "I": RUST_L})
    for j in (0, 7, 15):
        for i in (0, 4, 9, 13):
            s.fpx("body", "back", i, j, BONE_L)                  # bone rivets at the rail corners
    s.fpx("body", "back", 6, 3, BANNER); s.fpx("body", "back", 7, 3, BANNER)    # a war-drum on deck
    s.fpx("body", "back", 6, 4, BANNER_D); s.fpx("body", "back", 7, 4, BANNER_D)
    # the war-blanket: hangs off the platform down both flanks (spine-side columns of the side faces)
    for f, cols in (("right", range(0, 6)), ("left", range(14, 20))):
        for j in range(16):
            for i in cols:
                edge = (i == 5) if f == "right" else (i == 14)
                c = BONE_L if edge and j % 2 == 0 else (GOLD_D if edge else (BANNER if (j // 2) % 2 else BANNER_D))
                s.fpx("body", f, i, j, s.n(c, 3))
        for j in range(16):
            s.fpx("body", f, 0 if f == "right" else 19, j, RUST)  # the rail edge
    # rear half of the back: wool with a strap holding the platform
    shag(s, "body2", "back", WOOL, WOOL_D, WOOL_L, along="v")
    for i in range(12):
        s.fpx("body2", "back", i, 1, LEATHER_D); s.fpx("body2", "back", i, 2, LEATHER)
    s.fpx("body2", "back", 5, 1, RUST_L); s.fpx("body2", "back", 6, 1, RUST_L)
    # ---- neck (seen from above in front of the platform): wool with a fringe of the blanket
    for f in SIDES + ("top", "bottom"):
        shag(s, "neck", f, WOOL, WOOL_D, WOOL_L, along="v" if f not in ("right", "left") else "v")
    x, y, w, h = s.face("neck", "top")
    for i in range(w):
        s.put(x + i, y + h - 1, BANNER_D)
        s.put(x + i, y + h - 2, BANNER if i % 2 else GOLD_D)
    # ---- head: a woolly dome over a leathery face, rust chanfron, amber eyes
    for f in ("right", "left", "back"):
        shag(s, "head", f, WOOL, WOOL_D, WOOL_L)
    shag(s, "head", "top", WOOL_D, (40, 26, 18), WOOL, along="v")
    shag(s, "head", "bottom", WOOL_D, HIDE_D, WOOL, along="v")
    face = ["WWwWWWWwWWWWwWWW",
            "WwWRRRRRRRRRRwWW",
            "WWRrIIIIIIIIrRwW",
            "wWRIIIBBBBIIIRWw",
            "WWRIIBKBBKBIIRWW",
            "WwRIIIBBBBIIIRwW",
            "WWRrIIBIIBIIrRWW",
            "WwWRRIIIIIIRRWwW",
            "WWwWHRRRRRRHWwWW",
            "WwwHHHHHHHHHHwwW",
            "WKAHHHHHHHHHHAKW",
            "WHHhHHHHHHHHhHHW",
            "WHHHHhHHHHhHHHHW",
            "wHHHHHhTThHHHHHw",
            "WWHHHHTTTTHHHHWW",
            "WWHHHhTTTThHHHWW",
            "wWWHHHTTTTHHHWWw",
            "WwWHHhTTTThHHWwW",
            "WWwWHHTTTTHHWwWW",
            "WwWWwHHHHHHwWWwW"]
    put_rows(s, "head", "front", face,
             {"W": WOOL, "w": WOOL_D, "R": RUST_D, "r": RUST_L, "I": RUST, "B": BONE_L, "H": HIDE, "h": HIDE_D,
              "K": (24, 18, 16), "A": (236, 168, 56), "T": (128, 112, 100)})
    # the trunk (nose box): hide with wrinkle rings, darker tip
    s.fill("nose", (128, 112, 100), 3)
    for j in range(1, 8, 2):
        s.band("nose", j, j + 1, HIDE_D)
    s.fill("nose", (30, 24, 22), 0, which=("bottom",))
    # lower jaw: dark hide, the mouth line pale
    s.fill("mouth", HIDE_D, 3)
    s.band("mouth", 0, 1, (30, 22, 20))
    s.fill("mouth", WOOL_D, 3, which=("bottom",))
    # horns -> ivory with a hide root, rust rings
    s.fill("horn", BONE, 4)
    s.band("horn", 0, 4, BONE_L)
    s.band("horn", 11, 14, HIDE_D)
    s.band("horn", 8, 9, RUST)
    s.fill("horn", BONE_L, 0, which=("top",))
    return s


def sun_colossus():
    """The Sunforged T3: a gilded paladin statue. White stone with gold plate - a crowned helm with glowing gold eyes,
    gold pauldrons over the shoulders (the RTS view), a great gold sun on the chest, a royal-blue mantle down the
    back, gold gauntlets and sabatons, a few fine cracks so it reads as carved stone, not metal."""
    s = Skin(GOLEM, (128, 128), 65)

    def crack(part, f, pts):
        for (i, j) in pts:
            s.fpx(part, f, i, j, STONE_D)

    # ---- head: stone with a gold crowned band; nose stone
    s.fill("head", STONE, 4)
    s.vshade("head", 1.04, 0.9)
    s.band("head", 0, 2, GOLD)
    s.band("head", 2, 3, GOLD_D)
    for f in SIDES:
        for i in range(0, 8, 2):
            s.fpx("head", f, i, 0, GOLD_L)
    put_rows(s, "head", "front",
             ["Y..YY..Y",
              "YYYRRYYY",
              "DDDDDDDD",
              "........",
              ".SS..SS.",
              ".GL..LG.",
              "........",
              "........",
              ".D....D.",
              "..DDDD.."],
             {"Y": GOLD_L, "R": RUBY, "D": GOLD_D, "S": STONE_D, "G": GOLD, "L": (255, 244, 170)})
    s.fill("head", GOLD, 4, which=("top",))
    x, y, w, h = s.face("head", "top")
    for i in range(1, w - 1):
        for j in range(1, h - 1):
            s.put(x + i, y + j, s.n(STONE_L, 3))
    s.put(x + 3, y + 3, RUBY); s.put(x + 4, y + 4, RUBY); s.put(x + 3, y + 4, GOLD_L); s.put(x + 4, y + 3, GOLD_L)
    s.fill("nose", STONE, 3)
    # ---- body: stone torso, gold shoulder plates, the sun on the chest, a blue mantle on the back
    s.fill("body", STONE, 4)
    s.fill("body", GOLD, 4, which=("top",))
    x, y, w, h = s.face("body", "top")
    for i in range(5, 13):
        for j in range(2, 9):
            s.put(x + i, y + j, s.n(STONE_L, 3))
    for i in range(w):
        s.put(x + i, y, GOLD_L)
        s.put(x + i, y + h - 1, GOLD_D)
    for j in range(h):
        s.put(x + 4, y + j, GOLD_D); s.put(x + 13, y + j, GOLD_D)
    s.band("body", 0, 2, GOLD)
    s.band("body", 2, 3, GOLD_D)
    sun = ["......Y.Y.......",
           "...Y...Y...Y....",
           "....Y.YYY.Y.....",
           "......YLY.......",
           ".Y.YYYLRLYYY.Y..",
           "......YLY.......",
           "....Y.YYY.Y.....",
           "...Y...Y...Y....",
           "................"]
    put_rows(s, "body", "front", sun, {"Y": GOLD, "L": GOLD_L, "R": RUBY}, ox=1, oy=3)
    s.band("body", 11, 12, GOLD_D)
    for j in range(2, 12):
        for i in range(2, 16):
            s.fpx("body", "back", i, j, s.n(ROYAL if j < 11 else ROYAL_D, 4))
    for i in range(2, 16):
        s.fpx("body", "back", i, 2, GOLD)
    put_rows(s, "body", "back", ["..Y..", ".YYY.", "YYLYY", ".YYY.", "..Y.."], {"Y": GOLD, "L": GOLD_L}, ox=7, oy=4)
    crack("body", "right", [(3, 4), (4, 5), (4, 6), (5, 7)])
    crack("body", "front", [(15, 9), (16, 10)])
    # ---- waist: gold belt with a blue tasset
    s.fill("waist", ROYAL, 4)
    s.band("waist", 0, 2, GOLD)
    s.fpx("waist", "front", 4, 0, RUBY)
    s.band("waist", 4, 5, GOLD_D)
    # ---- arms: stone upper arm with a gold band, gold bracer + gauntlet over the lower third
    for arm in ("arm_r", "arm_l"):
        s.fill(arm, STONE, 4)
        s.fill(arm, GOLD_L, 3, which=("top",))
        s.band(arm, 0, 4, GOLD)
        s.band(arm, 3, 4, GOLD_D)
        s.band(arm, 18, 30, GOLD)
        s.band(arm, 18, 19, GOLD_L)
        s.band(arm, 24, 25, GOLD_D)
        for f in SIDES:
            w = s.face(arm, f)[2]
            for i in range(0, w, 2):
                s.fpx(arm, f, i, 28, GOLD_D)               # knuckle plates
            s.fpx(arm, f, w // 2, 21, RUBY if f == "front" else GOLD_L)
        s.fill(arm, GOLD_D, 2, which=("bottom",))
        s.vshade(arm, 1.05, 0.88)
    crack("arm_r", "right", [(2, 8), (3, 9), (3, 10)])
    crack("arm_l", "left", [(3, 12), (2, 13)])
    # ---- legs: stone greaves, gold knee-cop and sabatons
    for leg in ("leg_r", "leg_l"):
        s.fill(leg, STONE, 4)
        s.band(leg, 5, 7, GOLD)
        s.band(leg, 6, 7, GOLD_D)
        s.band(leg, 12, 16, GOLD)
        s.band(leg, 12, 13, GOLD_L)
        s.fill(leg, GOLD_D, 2, which=("bottom",))
        s.vshade(leg, 1.04, 0.86)
    crack("leg_r", "front", [(1, 2), (2, 3)])
    return s


def bone_dragon():
    """The Gravebound T3: bleached bone - a spine of vertebrae and ribs down the back with soul-fire glowing between
    them, a horned skull with blue-flame sockets, wing spars of bone over tattered dark membranes (cut-out holes along
    the trailing edge) edged in soul-blue, a vertebral tail."""
    s = Skin(PHANTOM, (64, 64), 66)
    MEM, MEM_D = (52, 48, 60), (34, 30, 42)
    # ---- body: ribs on the sides, vertebrae on the top (5 wide x 9 long), soul glow between ribs
    s.fill("body", BONE, 4)
    x, y, w, h = s.face("body", "top")
    for j in range(h):
        for i in range(w):
            c = BONE if j % 2 == 0 else (SOUL_D if i in (0, w - 1) else BONE_D)
            s.put(x + i, y + j, s.n(c, 3))
        s.put(x + 2, y + j, BONE_L if j % 2 == 0 else BONE)
    s.put(x + 2, y + 4, SOUL_L)
    for f in ("right", "left"):
        fx, fy, fw, fh = s.face("body", f)
        for i in range(fw):
            for j in range(fh):
                s.put(fx + i, fy + j, s.n(BONE if i % 2 == 0 else (SOUL if j == 1 else BLACK), 3))
    s.fill("body", BONE_D, 3, which=("bottom",))
    # ---- head: a skull - bone, horns back along the top edge, blue-flame sockets, teeth
    s.fill("head", BONE, 4)
    put_rows(s, "head", "front", ["KLKBKLK", "KSKBKSK", "TtTtTtT"],
             {"K": BLACK_D, "L": SOUL_L, "S": SOUL, "B": BONE_L, "T": BONE_L, "t": BLACK})
    x, y, w, h = s.face("head", "top")
    for i in range(w):
        for j in range(h):
            s.put(x + i, y + j, s.n(BONE_L if 1 < i < w - 2 else BONE, 3))
    for j in range(h):
        s.put(x, y + j, BONE_D); s.put(x + w - 1, y + j, BONE_D)       # horn ridges
    s.put(x + 3, y + 2, SOUL)                                          # a soul-fire crack in the crown
    s.put(x + 3, y + 3, SOUL_D)
    for f in ("right", "left"):
        put_rows(s, "head", f, ["BBBBB", "BKSKB", "BTtTB"], {"B": BONE, "K": BLACK_D, "S": SOUL, "T": BONE_L, "t": BLACK})
    s.fill("head", BONE_D, 3, which=("bottom",))
    # ---- tail: vertebrae (alternating bone and dark joints), the tip a soul-blue barb
    for part in ("tail", "tail_tip"):
        for f, (fx, fy, fw, fh) in faces(PHANTOM[part]).items():
            for i in range(fw):
                for j in range(fh):
                    k = i if f in ("right", "left") else j
                    s.put(fx + i, fy + j, s.n(BONE if k % 2 == 0 else BONE_D, 3))
    fx, fy, fw, fh = s.face("tail_tip", "top")
    s.put(fx, fy + fh - 1, SOUL_L)
    # ---- wing base: a thick bone arm along the leading edge, membrane behind (top face: last row = leading edge)
    x, y, w, h = s.face("wing", "top")
    for i in range(w):
        for j in range(h):
            if j >= h - 3:
                c = BONE_L if j == h - 2 else BONE
            elif i in (1, 4) and j >= 1:
                c = BONE_D            # finger bones running back
            else:
                c = MEM if (i + j) % 4 else MEM_D
            s.put(x + i, y + j, s.n(c, 3))
    x, y, w, h = s.face("wing", "bottom")
    for i in range(w):
        for j in range(h):
            c = BONE if j < 3 else (BONE_D if i in (1, 4) else MEM_D)
            s.put(x + i, y + j, s.n(c, 3))
    for f in SIDES:
        fx, fy, fw, fh = s.face("wing", f)
        for i in range(fw):
            for j in range(fh):
                s.put(fx + i, fy + j, s.n(BONE_D, 3))
    # ---- wing tip: 13 wide x 9 deep - the spar along the leading edge, finger bones fanning out to the tip, a
    # soul-blue glow along each finger, ragged holes cut along the trailing edge (cut-out render type)
    x, y, w, h = s.face("wing_tip", "top")
    for i in range(w):
        for j in range(h):
            if j >= h - 2:
                c = BONE_L if j == h - 1 else BONE
            else:
                c = MEM if (i * 3 + j) % 5 else MEM_D
            s.put(x + i, y + j, s.n(c, 3))
    fingers = [(2, 0), (6, 0), (10, 0)]
    for (fi, _) in fingers:
        for j in range(0, h - 2):
            ii = min(w - 1, fi + (h - 3 - j) // 3)
            s.put(x + ii, y + j, BONE_D)
            if j % 3 == 1:
                s.put(x + min(w - 1, ii + 1), y + j, SOUL_D)
    s.put(x + w - 1, y + h - 1, SOUL_L)                # a soul-fire claw at the wing's tip
    holes = [(4, 0), (5, 0), (8, 0), (8, 1), (11, 0), (12, 0), (12, 1), (12, 2), (1, 0), (9, 0)]
    for (i, j) in holes:
        s.clear(x + i, y + j)
    for i in range(w):
        if (i, 0) not in holes:
            s.put(x + i, y, SOUL_D if i % 3 == 0 else MEM_D)   # soul-blue fraying on the trailing edge
    x, y, w, h = s.face("wing_tip", "bottom")
    for i in range(w):
        for j in range(h):
            c = BONE if j < 2 else (MEM_D if (i + j) % 3 else MEM)
            s.put(x + i, y + j, s.n(c, 3))
    for (i, j) in holes:
        s.clear(x + i, y + h - 1 - j)
    for f in SIDES:
        fx, fy, fw, fh = s.face("wing_tip", f)
        for i in range(fw):
            for j in range(fh):
                s.put(fx + i, fy + j, s.n(BONE_D, 3))
    return s


UNITS = {
    "royal_architect": (royal_architect, "villager"),
    "embalmer": (embalmer, "villager"),
    "bonewright": (bonewright, "piglin"),
    "war_mammoth": (war_mammoth, "ravager"),
    "sun_colossus": (sun_colossus, "golem"),
    "bone_dragon": (bone_dragon, "phantom"),
}


# ================================================================== portraits
class Portrait:
    """A transparent 16x16 canvas (the original factions' heads are frameless crops); saved at 4x."""

    def __init__(self):
        self.im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.px = self.im.load()

    def p(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = (c + (255,)) if len(c) == 3 else c

    def art(self, rows, pal, ox=0, oy=0):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != "." and ch in pal:
                    self.p(ox + x, oy + y, pal[ch])

    def big(self):
        return self.im.resize((64, 64), Image.NEAREST)


ICONS = {}


def icon(fn):
    ICONS[fn.__name__.replace("icon_", "")] = fn
    return fn


@icon
def icon_royal_architect():
    i = Portrait()
    i.art([".YLYYLLYYLY.",
           "YYYYYRRYYYYY",
           "HDDDDDDDDDDH",
           "HSSSSSSSSSSH",
           "SvvvSSSSvvvS",
           "SWESSSSSMEWM",
           "SssSSNNSSMsS",
           "SSSSSNNSSSMS",
           "SSSSSNNSSSSS",
           "SSSSSNNSSSSS",
           "SSSSGGGGSSSS",
           "SSSSGGGGSSSS",
           "WWWSSGGSSWWW",
           "WWYWWWWWWYPW",
           "LWYWWBBWWYPB",
           "LLYWBWWWWYPB"],
          {"Y": GOLD, "L": GOLD_L, "R": RUBY, "D": GOLD_D, "H": (214, 210, 200), "S": SKIN_FAIR,
           "s": sh(SKIN_FAIR, 0.88), "B": BLUEPRINT, "W": WHITE, "E": (70, 110, 160), "M": GOLD_L,
           "N": sh(SKIN_FAIR, 0.9), "G": (236, 232, 224), "P": BLUEPRINT_L, "v": (150, 140, 128)}, ox=2)
    return i


@icon
def icon_embalmer():
    i = Portrait()
    i.art(["LlLLlLLlLLlL",
           "lLLlLLlLLlLL",
           "LlSLLlLLlLLl",
           "RRRRRRRlLLlR",
           "RDDDRRRRlLLR",
           "RCURRRRRRlLR",
           "RddRRNNRRRlR",
           "RRRRRNNRRRRR",
           "RRRRRNNRRRRR",
           "RRRRRNNRRRRR",
           "RRKdKdKdKRRR",
           "RRRRRRRRRRRR",
           "BBBRRRRRRBBB",
           "BOBBAAAAABOB",
           "BBBBAWAAABBB",
           "BBBBAAAWABBB"],
          {"L": LINEN, "l": LINEN_D, "S": SOUL, "R": ROT, "D": ROT_D, "d": sh(ROT, 0.7), "C": SOUL, "U": SOUL_L,
           "N": sh(ROT, 0.88), "K": BLACK_D, "B": BLACK, "O": BONE, "A": CANVAS, "W": BONE_L}, ox=2)
    return i


@icon
def icon_bonewright():
    i = Portrait()
    i.art(["BBbBBBBBBbBB....",
           "BBBBbBBbBBBB....",
           "BKKBBBBBBKKB....",
           "BKRBbbbbBKRB....",
           "TbbbbbbbbbbT....",
           "TPDDDPPDDDPT....",
           "TPWEPPPPEWPT....",
           "TTPPPLLPPPTT....",
           "PTPPLnLLnPTP....",
           "PTPPLLLLLPTP....",
           "PPPPPPPPPPPP....",
           "FFFFPPPPPFFF....",
           "FFFFFRRFFFFF...."],
          {"B": BONE, "b": BONE_D, "K": (40, 30, 26), "R": (230, 120, 40), "T": BONE_L, "P": PIG, "D": PIG_D,
           "W": (240, 236, 228), "E": (60, 26, 20), "L": PIG_L, "n": PIG_D, "F": FUR})
    # stretch the 12-wide face to the full 16-wide piglin framing: widen columns symmetrically (nearest)
    src = i.im.crop((0, 0, 12, 13)).resize((16, 13), Image.NEAREST)
    i.im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    i.im.paste(src, (0, 0))
    i.px = i.im.load()
    # the fur collar gets a rust buckle and hammer handle in the corners
    i.p(7, 12, RUST_L); i.p(8, 12, RUST_L)
    return i


@icon
def icon_war_mammoth():
    i = Portrait()
    i.art([".wWWWwWWWwWWWw.",
           "wWWWWWWWWWWWWWw",
           "WWWRRRRRRRRRWWW",
           "WWRrIIBBBIIrRWW",
           "WWRIIBKBKBIIRWW",
           "WHRRIIBBBIIRRHW",
           "WHHHRRBRBRRHHHW",
           "WHKAHHHTHHHAKHW",
           "WHHHHHTTTHHHHHW",
           "WwHHHHhhhHHHHwW",
           "OwWHHHTTTHHHWwO",
           "OOwWHHhhhHHWwOO",
           ".OOWWHTTTHWWOO.",
           "..OOWHhhhHWOO..",
           "...OOWTTTWOO...",
           "....O.kkk.O...."],
          {"W": WOOL, "w": WOOL_D, "R": RUST_D, "r": RUST_L, "I": RUST, "B": BONE_L, "H": HIDE, "h": HIDE_D,
           "K": (24, 18, 16), "A": (236, 168, 56), "O": BONE_L, "k": (30, 24, 22), "T": (128, 112, 100)}, ox=0)
    return i


@icon
def icon_sun_colossus():
    i = Portrait()
    i.art(["L.YLLYYLLY.L",
           "LYYYYRRYYYYL",
           "YYYYYYYYYYYY",
           "DDDDDDDDDDDD",
           "SSSSSSSSSSSS",
           "SKKKSSSSKKKS",
           "SGLGSSSSGLGS",
           "SSSSSNNSSSSS",
           "SSSSSNNSSSSS",
           "SsSSSNNSSSSS",
           "SsSSDDDDSSSs",
           "SSSSSSSSSSsS",
           "YYYSSSSSSYYY",
           "YLYYYRYYYYLY",
           "YYDYLRLYYDYY",
           "YYYDYLYYDYYY"],
          {"Y": GOLD, "L": GOLD_L, "R": RUBY, "D": GOLD_D, "S": STONE, "s": STONE_D, "K": STONE_D, "G": GOLD,
           "N": sh(STONE, 0.9)}, ox=2)
    i.p(2, 0, (255, 244, 170)); i.p(13, 0, (255, 244, 170))
    return i


@icon
def icon_bone_dragon():
    i = Portrait()
    # full-tile like phantom.png: a dark soul-lit backdrop behind the skull
    for y in range(16):
        for x in range(16):
            i.p(x, y, mix((24, 40, 56), (10, 14, 22), y / 15))
    i.art(["b..............b",
           "Bb............bB",
           ".BB..BBBBBB..BB.",
           "..BBBBBBBBBBBB..",
           "...BLLLLLLLLB...",
           "..BBKKKBBKKKBB..",
           "..BKKSUBBUSKKB..",
           "..BKKKKBBKKKKB..",
           "..BBBBBBBBBBBB..",
           "...BBBdBBdBBB...",
           "...BBBBBBBBBB...",
           "..TtTtTtTtTtTt..",
           "..KKKKKKKKKKKK..",
           "...tTtTtTtTtT...",
           "....BBBBBBBB....",
           ".....SSSSSS....."],
          {"B": BONE, "b": BONE_D, "L": BONE_L, "K": BLACK_D, "S": SOUL, "U": SOUL_L, "d": BONE_D,
           "T": BONE_L, "t": BLACK})
    return i


def save_icon(name, ic):
    big = ic.big()
    if name == "bonewright":
        big = big.crop((0, 0, 64, 51))                       # grunt.png framing: 64x51
    big.save(os.path.join(HEADS, f"{name}.png"))
    if name == "war_mammoth":
        # the "carrying passengers" variant: ravager_half.png's diagonal mask, so the passenger's head fits the corner
        mask = Image.open(os.path.join(HEADS, "ravager_half.png")).convert("RGBA").split()[3]
        half = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        half.paste(big, (0, 0), mask.point(lambda a: 255 if a else 0))
        half.save(os.path.join(HEADS, "war_mammoth_half.png"))
    return big


# ================================================================== previews
def piglin_mock(s, back=False):
    f = "back" if back else "front"
    im = Image.new("RGBA", (18, 33), (0, 0, 0, 0))

    def blit(part, ox, oy, flip=False):
        x, y, w, h = s.face(part, f)
        reg = s.im.crop((x, y, x + w, y + h))
        im.alpha_composite(reg, (ox, oy))

    blit("leg_r", 5, 20); blit("pants_r", 5, 20); blit("leg_l", 9, 20); blit("pants_l", 9, 20)
    blit("body", 5, 8); blit("jacket", 5, 8)
    blit("arm_r", 1, 8); blit("sleeve_r", 1, 8); blit("arm_l", 13, 8); blit("sleeve_l", 13, 8)
    blit("head", 4, 0)
    if not back:
        blit("snout", 7, 4)
    return im


def top_mock(s, kind):
    """What the RTS camera sees: the top faces assembled roughly in place."""
    if kind == "ravager":
        im = Image.new("RGBA", (24, 64), (0, 0, 0, 0))

        def tb(part, f, ox, oy, rot=False):
            x, y, w, h = s.face(part, f)
            reg = s.im.crop((x, y, x + w, y + h))
            if rot:
                reg = reg.transpose(Image.ROTATE_180)
            im.alpha_composite(reg, (ox, oy))
        tb("head", "top", 4, 0, rot=True)
        tb("neck", "top", 7, 14, rot=True)
        tb("body", "back", 5, 30)
        tb("body2", "back", 6, 46)
        return im
    if kind == "golem":
        im = Image.new("RGBA", (28, 12), (0, 0, 0, 0))
        for part, ox in (("arm_r", 0), ("body", 5), ("arm_l", 24)):
            x, y, w, h = s.face(part, "top")
            im.alpha_composite(s.im.crop((x, y, x + w, y + h)), (ox, 0))
        x, y, w, h = s.face("head", "top")
        im.alpha_composite(s.im.crop((x, y, x + w, y + h)), (10, 2))
        return im
    if kind == "phantom":
        im = Image.new("RGBA", (46, 20), (0, 0, 0, 0))
        x, y, w, h = s.face("wing_tip", "top"); tip = s.im.crop((x, y, x + w, y + h))
        x, y, w, h = s.face("wing", "top"); base = s.im.crop((x, y, x + w, y + h))
        im.alpha_composite(tip, (33, 2)); im.alpha_composite(base, (27, 2))
        im.alpha_composite(base.transpose(Image.FLIP_LEFT_RIGHT), (14, 2))
        im.alpha_composite(tip.transpose(Image.FLIP_LEFT_RIGHT), (0, 2))
        x, y, w, h = s.face("body", "top"); im.alpha_composite(s.im.crop((x, y, x + w, y + h)), (20, 2))
        x, y, w, h = s.face("head", "top"); im.alpha_composite(s.im.crop((x, y, x + w, y + h)).transpose(Image.ROTATE_180), (19, 11))
        x, y, w, h = s.face("tail", "top"); im.alpha_composite(s.im.crop((x, y, x + w, y + h)), (21, 0))
        return im
    return None


def preview(outdir, skins, icons):
    os.makedirs(outdir, exist_ok=True)
    SC = 5
    BG = (78, 84, 90, 255)
    paths = []
    for name, (s, kind) in skins.items():
        flat = s.im.resize((s.im.width * SC if s.im.width <= 64 else s.im.width * 3,
                            s.im.height * SC if s.im.width <= 64 else s.im.height * 3), Image.NEAREST)
        mocks = []
        if kind == "villager":
            mocks = [V.front_mock(s, "villager"), V.front_mock(s, "villager", back=True)]
        elif kind == "piglin":
            mocks = [piglin_mock(s), piglin_mock(s, back=True)]
        tm = top_mock(s, kind)
        if tm is not None:
            mocks.append(tm)
        mocks = [m.resize((m.width * 6, m.height * 6), Image.NEAREST) for m in mocks]
        w = flat.width + sum(m.width + 16 for m in mocks) + 16
        h = max([flat.height] + [m.height for m in mocks]) + 16
        sheet = Image.new("RGBA", (w, h), BG)
        sheet.alpha_composite(flat, (8, 8))
        ox = flat.width + 24
        for m in mocks:
            sheet.alpha_composite(m, (ox, 8))
            ox += m.width + 16
        p = os.path.join(outdir, f"t2t3_{name}_sheet.png")
        sheet.convert("RGB").save(p)
        paths.append(p)
    # the portraits at 2x, beside the faction's existing heads for comparison, then a row at true 64px size
    ref = {"royal_architect": "villager", "embalmer": "zombie_villager", "bonewright": "grunt",
           "war_mammoth": "ravager", "sun_colossus": "iron_golem", "bone_dragon": "phantom"}
    names = list(icons) + ["war_mammoth_half"]
    sheet = Image.new("RGBA", (len(names) * 140 + 10, 380), (52, 56, 60, 255))
    for k, n in enumerate(names):
        mine = Image.open(os.path.join(HEADS, f"{n}.png")).convert("RGBA")
        sheet.alpha_composite(mine.resize((mine.width * 2, mine.height * 2), Image.NEAREST), (10 + k * 140, 10))
        if n in ref:
            old = Image.open(os.path.join(HEADS, f"{ref[n]}.png")).convert("RGBA")
            sheet.alpha_composite(old.resize((old.width * 2, old.height * 2), Image.NEAREST), (10 + k * 140, 150))
        sheet.alpha_composite(mine, (10 + k * 140, 300))
    p = os.path.join(outdir, "t2t3_portraits_sheet.png")
    sheet.convert("RGB").save(p)
    paths.append(p)
    return paths


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
    icons = {}
    for name, fn in ICONS.items():
        ic = fn()
        save_icon(name, ic)
        icons[name] = ic
        print(f"textures/mobheads/{name}.png")
    if out_prev:
        for p in preview(out_prev, skins, icons):
            print("preview", p)


if __name__ == "__main__":
    main()
