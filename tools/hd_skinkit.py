"""High-detail skin toolkit (original procedural pixel art, no source textures) used by tools/gen_hd_skins.py.

What it adds over the older per-faction kits (tools/gen_verdant_skins.py and friends):
  - 5-tone material ramps with hue-shifted shadows (cool) and highlights (warm), so a material reads as a volume and
    not as a flat fill + noise;
  - material painters that wrap continuously round a box (u runs right -> front -> left -> back): cloth folds,
    segmented plate with bevels / specular stripes / rivets, mail, leather grain, fur strands, bone with cracks, bark
    grain, red lacquer gloss, scales, quilting, silk, kelp;
  - embossed emblem stamps (one per faction) with a lit edge and a drop shadow;
  - a TEAM layer: every skin has a companion <name>_team.png of the same size where the owner-coloured cloth lives as
    grey-scale (values 160..255 carry the folds), drawn by TeamColourLayer in game multiplied by the owner colour.
    Under it the base skin carries the same cloth in the faction's default colour, for unowned units;
  - auto portraits (mobheads) built from the finished skin (head, hat, shoulders) so the HUD matches the unit;
  - front/back mock-ups for the contact sheets (with the team layer tinted in two sample colours).

All painting is deterministic: every random choice comes from a hash of the unit name and pixel coordinates.
"""
import math, os, zlib
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENT = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/entities")
HEADS = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/mobheads")
DESIGN = os.path.join(ROOT, "design")


# ------------------------------------------------------------------ colour
def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sh(c, f):
    return tuple(clamp(v * f) for v in c[:3])


COOL, WARM = (26, 28, 62), (255, 244, 212)


def ramp(c):
    """Five tones: deep shadow, shadow, base, light, highlight. Shadows lean cool, highlights warm."""
    c = tuple(c[:3])
    return (mix(sh(c, 0.40), COOL, 0.32), mix(sh(c, 0.68), COOL, 0.15), c,
            mix(sh(c, 1.20), WARM, 0.14), mix(sh(c, 1.42), WARM, 0.40))


def tone(R, t):
    t = max(0.0, min(4.0, t))
    i = int(t)
    if i >= 4:
        return R[4]
    return mix(R[i], R[i + 1], t - i)


def h01(*a):
    """Deterministic hash of ints -> [0, 1)."""
    x = 0x9E3779B1
    for v in a:
        x = ((x ^ (int(v) & 0xFFFFFFFF)) * 0x85EBCA6B) & 0xFFFFFFFF
        x ^= x >> 13
        x = (x * 0xC2B2AE35) & 0xFFFFFFFF
        x ^= x >> 16
    return (x & 0xFFFF) / 65536.0


# ------------------------------------------------------------------ layouts (u, v, w, h, d)
VILLAGER = {"head": (0, 0, 8, 10, 8), "hat": (32, 0, 8, 12, 8), "nose": (24, 0, 2, 4, 2),
            "body": (16, 20, 8, 12, 6), "jacket": (0, 38, 8, 18, 6),
            "arm_up": (44, 22, 4, 8, 4), "arm_mid": (40, 38, 8, 4, 4), "arm": (40, 46, 4, 12, 4),
            "leg": (0, 22, 4, 12, 4)}
# vanilla piglin (PlayerModel mesh, 64x64; head 10x8x8 with snout, ears; outer layer boxes)
PIGLIN = {"head": (0, 0, 10, 8, 8), "snout": (31, 1, 4, 4, 1), "tusk_r": (2, 4, 1, 2, 1), "tusk_l": (2, 0, 1, 2, 1),
          "ear_l": (51, 6, 1, 5, 4), "ear_r": (39, 6, 1, 5, 4),
          "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
          "arm_r": (40, 16, 4, 12, 4), "sleeve_r": (40, 32, 4, 12, 4),
          "arm_l": (32, 48, 4, 12, 4), "sleeve_l": (48, 48, 4, 12, 4),
          "leg_r": (0, 16, 4, 12, 4), "pants_r": (0, 32, 4, 12, 4),
          "leg_l": (16, 48, 4, 12, 4), "pants_l": (0, 48, 4, 12, 4)}
# vanilla zombie (HumanoidModel, 64x64 since 1.8 layout: hat at (32,0); legs/arms mirrored from the right ones)
HUMANOID = {"head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4),
            "arm": (40, 16, 4, 12, 4), "leg": (0, 16, 4, 12, 4)}
# vanilla skeleton (64x32)
SKELETON = {"head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4),
            "arm": (40, 16, 2, 12, 2), "leg": (0, 16, 2, 12, 2)}

SIDES = ("right", "front", "left", "back")
ALL = ("top", "bottom") + SIDES
LIGHT = {"top": 0.45, "bottom": -0.85, "front": 0.0, "right": -0.12, "left": -0.12, "back": -0.22}


def faces(box):
    u, v, w, h, d = box
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


# ------------------------------------------------------------------ materials
# A material is f(s, face, i, j, w, h, u) -> rgb. u wraps round the four sides, so stripes and folds continue.

def _grad(f, j, h, g):
    if f in ("top", "bottom"):
        return 0.0
    return g * (0.5 - j / max(1, h - 1))


def cloth(c, folds=4, grad=0.7, n=0.12, hem=0):
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.0 + LIGHT[f] + _grad(f, j, h, grad)
        if f in SIDES:
            t += 0.45 * math.sin(2 * math.pi * (u + 0.7 * math.sin(j * 0.45 + u * 0.3)) / folds)
            if hem and j >= h - hem:
                t -= 0.55
        t += (h01(s.seed, u, j, 1) - 0.5) * 2 * n
        return tone(R, t)
    return m


def silk(c, grad=0.6):
    """Smooth cloth with a soft sheen band: few folds, a light diagonal glint."""
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.0 + LIGHT[f] + _grad(f, j, h, grad)
        if f in SIDES:
            t += 0.3 * math.sin(2 * math.pi * u / 6.0)
            if (u + j // 2) % 9 == 0:
                t += 0.8
        t += (h01(s.seed, u, j, 2) - 0.5) * 0.12
        return tone(R, t)
    return m


def plate(c, lame=3, grad=0.5, spec=6, rivet=None, n=0.08):
    """Segmented plate: each lame lit on its top row and shadowed on its lip, a vertical specular stripe every
    `spec` columns, rivets (colour) at both ends of the lame's middle row."""
    R = ramp(c)
    RR = ramp(rivet) if rivet else None

    def m(s, f, i, j, w, h, u):
        t = 2.15 + LIGHT[f] + _grad(f, j, h, grad)
        if f in SIDES:
            k = j % lame
            if k == 0:
                t += 0.9
            elif k == lame - 1:
                t -= 0.85
            if spec and u % spec == 1:
                t += 0.75
            elif spec and u % spec == 2:
                t += 0.3
            if RR and lame >= 3 and k == 1 and (i == 0 or i == w - 1):
                return tone(RR, 3.3)
        else:
            t += 0.25 if (i + j) % 4 == 0 else 0
        t += (h01(s.seed, u, j, 3) - 0.5) * 2 * n
        return tone(R, t)
    return m


def mail(c, grad=0.5):
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.0 + LIGHT[f] + _grad(f, j, h, grad)
        t += 0.75 if (u + (j % 2)) % 2 == 0 else -0.55
        return tone(R, t)
    return m


def leather(c, grad=0.6, stitch=None):
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.0 + LIGHT[f] + _grad(f, j, h, grad)
        r = h01(s.seed, u, j, 4)
        if r < 0.16:
            t -= 0.55
        elif r > 0.93:
            t += 0.55
        if stitch is not None and f in SIDES and i in (0, w - 1) and j % 2 == 0:
            t = 3.4
        return tone(R, t)
    return m


def fur(c, grad=0.4):
    """Hanging strands: each column its own phase; dark partings every third strand and light tips."""
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        ph = h01(s.seed, u, 5)
        t = 2.0 + LIGHT[f] + _grad(f, j, h, grad)
        t += 0.75 * math.sin((j + ph * 6) * 1.25) * (0.5 + ph)
        if u % 3 == 0:
            t -= 0.5
        if f == "top":
            t += 0.6 * math.sin(i * 1.7 + j * 2.1)
        return tone(R, t)
    return m


def hair(c, grad=0.5):
    """Hair and beards: soft locks (each column a gentle wave), a darker parting every third strand, lit crown."""
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        ph = h01(s.seed, u, 12)
        t = 2.0 + LIGHT[f] * 0.8 + _grad(f, j, h, grad)
        t += 0.35 * math.sin((j + ph * 5) * 0.9)
        if u % 3 == 2:
            t -= 0.45
        if f == "top":
            t += 0.3 + 0.35 * math.sin(i * 1.3 + j * 0.9)
        return tone(R, t)
    return m


def bone(c, grad=0.5, cracks=0.6):
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.45 + LIGHT[f] + _grad(f, j, h, grad) + (h01(s.seed, u, j, 6) - 0.5) * 0.45
        d = u - j
        if d % 5 == 0 and h01(s.seed, d // 5, j // 2, 7) < cracks:
            t = 0.9
        elif (u + j) % 7 == 0 and h01(s.seed, u, j, 8) < 0.3:
            t -= 0.6
        return tone(R, t)
    return m


def bark(c, grad=0.5):
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.0 + LIGHT[f] + _grad(f, j, h, grad)
        if f in SIDES:
            t += 0.85 * math.sin(u * 1.7 + 1.3 * math.sin(j * 0.35 + u * 0.7))
            if h01(s.seed, u // 3, j // 4, 9) > 0.93:
                t = 0.6   # a knot
        else:
            r = math.hypot(i - w / 2 + 0.5, j - h / 2 + 0.5)
            t += 0.6 * math.sin(r * 2.2)   # growth rings on the end grain
        return tone(R, t)
    return m


def lacquer(c, grad=0.6):
    """Glossy lacquer: smooth, deep, with a bright diagonal glint every 8 columns."""
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.0 + LIGHT[f] + _grad(f, j, h, grad)
        g = (u + j // 2) % 8
        if f in SIDES and g == 0:
            t += 1.4
        elif f in SIDES and g == 1:
            t += 0.6
        return tone(R, t)
    return m


def scales(c, sw=2, sh_=2, grad=0.5):
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        row = j // sh_
        cell = (u + (row % 2) * (sw // 2)) % sw
        k = j % sh_
        t = 2.0 + LIGHT[f] + _grad(f, j, h, grad)
        if k == 0 and cell == 0:
            t += 0.9
        elif k == sh_ - 1:
            t -= 0.7
        return tone(R, t)
    return m


def quilt(c, grad=0.6):
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.15 + LIGHT[f] + _grad(f, j, h, grad)
        if (u + j) % 4 == 0 or (u - j) % 4 == 0:
            t -= 0.75
        return tone(R, t)
    return m


def skinmat(c, grad=0.45):
    R = ramp(c)

    def m(s, f, i, j, w, h, u):
        t = 2.1 + LIGHT[f] * 0.8 + _grad(f, j, h, grad) + (h01(s.seed, u, j, 10) - 0.5) * 0.12
        return tone(R, t)
    return m


def flat(c):
    def m(s, f, i, j, w, h, u):
        return tuple(c[:3])
    return m


def stripes(a, b, period=2, horizontal=True):
    """Two materials alternating (sailor's shirt, banded robe)."""
    def m(s, f, i, j, w, h, u):
        k = j if horizontal else u
        return (a if (k // period) % 2 == 0 else b)(s, f, i, j, w, h, u)
    return m


# ------------------------------------------------------------------ emblems (5x5; X = metal/paint, o = dark inlay)
EMBLEMS = {
    "sun": ["X.X.X", ".XXX.", "XXoXX", ".XXX.", "X.X.X"],
    "skull": [".XXX.", "XXXXX", "XoXoX", "XXXXX", ".X.X."],
    "tusk": ["X...X", "X...X", ".XXX.", "XoXoX", ".XXX."],
    "leaf": ["..X..", ".XXX.", "XXoXX", ".XoX.", "..o.."],
    "trident": ["X.X.X", "X.X.X", "XXXXX", "..X..", "..X.."],
    "bolt": ["...XX", "..XX.", ".XXXX", "..XX.", ".XX.."],
    "coin": [".XXX.", "XXXXX", "XXoXX", "XXXXX", ".XXX."],
    "gear": [".X.X.", "XXXXX", ".XoX.", "XXXXX", ".X.X."],
    "anchor": ["..X..", ".XXX.", "..X..", "X.X.X", ".XXX."],
    "moon": [".XX..", "XX...", "XX...", "XX...", ".XX.."],
    "star": ["..X..", "X.X.X", ".XXX.", "X.X.X", "..X.."],
    "cross": [".XXX.", "XXXXX", ".XXX.", "..X..", "..X.."],
    "shell": [".XXX.", "XoXoX", "XoXoX", ".XXX.", "..X.."],
    "flame": ["..X..", ".XX..", ".XXX.", "XXoXX", ".XXX."],
}


# ------------------------------------------------------------------ the skin
class HD:
    def __init__(self, name, layout=VILLAGER, size=(64, 64), team_default=(120, 120, 120)):
        self.name = name
        self.L = layout
        self.size = size
        self.seed = zlib.crc32(name.encode()) & 0xFFFF
        self.im = Image.new("RGBA", size, (0, 0, 0, 0))
        self.px = self.im.load()
        self.team = Image.new("RGBA", size, (0, 0, 0, 0))
        self.tp = self.team.load()
        self.team_default = team_default

    # -- raw pixels
    def put(self, x, y, c, keep_team=False):
        W, H = self.size
        if 0 <= x < W and 0 <= y < H and c is not None:
            self.px[x, y] = (tuple(c[:3]) + (255,)) if len(c) == 3 else tuple(c)
            if not keep_team:
                self.tp[x, y] = (0, 0, 0, 0)

    def get(self, x, y):
        return self.px[x, y]

    def face(self, part, f):
        return faces(self.L[part])[f]

    def fpx(self, part, f, i, j, c, keep_team=False):
        x, y, w, h = self.face(part, f)
        if 0 <= i < w and 0 <= j < h:
            self.put(x + i, y + j, c, keep_team)

    def fget(self, part, f, i, j):
        x, y, w, h = self.face(part, f)
        return self.px[x + i, y + j]

    def fclear(self, part, f, i, j):
        x, y, w, h = self.face(part, f)
        if 0 <= i < w and 0 <= j < h:
            self.px[x + i, y + j] = (0, 0, 0, 0)
            self.tp[x + i, y + j] = (0, 0, 0, 0)

    def clear_part(self, part, sides=ALL, rows=None):
        for f, i, j, w, h, u, x, y in self.coords(part, sides, rows):
            self.px[x, y] = (0, 0, 0, 0)
            self.tp[x, y] = (0, 0, 0, 0)

    def coords(self, part, sides=ALL, rows=None):
        """Every pixel of the given faces: (face, i, j, w, h, u, x, y); u wraps round the four sides."""
        fs = faces(self.L[part])
        uoff, acc = {}, 0
        for f in SIDES:
            uoff[f] = acc
            acc += fs[f][2]
        out = []
        for f in sides:
            x, y, w, h = fs[f]
            j0, j1 = (0, h) if rows is None or f in ("top", "bottom") else (max(0, rows[0]), min(h, rows[1]))
            for j in range(j0, j1):
                for i in range(w):
                    out.append((f, i, j, w, h, uoff.get(f, 0) + i, x + i, y + j))
        return out

    # -- painting
    def paint(self, part, mat, sides=ALL, rows=None, where=None):
        for f, i, j, w, h, u, x, y in self.coords(part, sides, rows):
            if where and not where(f, i, j, w, h):
                continue
            self.put(x, y, mat(self, f, i, j, w, h, u))

    def shade(self, part, k, sides=SIDES, rows=None, where=None):
        """Darken (k < 1) or lighten existing pixels - ambient occlusion under belts, collars, plate lips."""
        for f, i, j, w, h, u, x, y in self.coords(part, sides, rows):
            if where and not where(f, i, j, w, h):
                continue
            p = self.px[x, y]
            if p[3]:
                self.px[x, y] = (sh(p, k) if k < 1 else mix(p, WARM, k - 1)) + (p[3],)

    def team_paint(self, part, sides=ALL, rows=None, where=None, folds=4, grad=0.6, default=None):
        """Owner-coloured cloth: grey-scale folds into the team image, the same cloth in the faction's default colour
        into the base skin (seen on unowned units and in the inventory)."""
        R = ramp(default or self.team_default)
        for f, i, j, w, h, u, x, y in self.coords(part, sides, rows):
            if where and not where(f, i, j, w, h):
                continue
            t = 2.0 + LIGHT[f] * 0.7 + _grad(f, j, h, grad)
            if f in SIDES and folds:
                t += 0.4 * math.sin(2 * math.pi * (u + 0.6 * math.sin(j * 0.5)) / folds)
            t += (h01(self.seed, u, j, 11) - 0.5) * 0.15
            t = max(0.6, min(3.4, t))
            self.put(x, y, tone(R, t))
            v = clamp(118 + 40 * t)
            self.tp[x, y] = (v, v, v, 255)

    def band(self, part, j0, j1, mat, sides=SIDES):
        self.paint(part, mat, sides, rows=(j0, j1))

    def rows_map(self, part, f, rows, pal, ox=0, oy=0, keep_team=False):
        """A string map onto one face: '.' keeps, ' ' clears, other letters index pal (rgb or material)."""
        x0, y0, w, h = self.face(part, f)
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                ii, jj = ox + i, oy + j
                if not (0 <= ii < w and 0 <= jj < h) or ch == ".":
                    continue
                if ch == " ":
                    self.fclear(part, f, ii, jj)
                elif ch in pal:
                    c = pal[ch]
                    if callable(c):
                        c = c(self, f, ii, jj, w, h, ii)
                    self.fpx(part, f, ii, jj, c, keep_team)

    def emblem(self, part, f, ox, oy, name, metal, inlay=None):
        """An embossed 5x5 stamp: lit on its top-left edges, a drop shadow under its bottom-right."""
        R = ramp(metal)
        rows = EMBLEMS[name]
        dark = inlay or tone(R, 0.3)
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "." and ((j > 0 and i > 0 and rows[j - 1][i - 1] != ".")):
                    p = self.fget(part, f, ox + i, oy + j) if self._inside(part, f, ox + i, oy + j) else None
                    if p and p[3]:
                        self.fpx(part, f, ox + i, oy + j, sh(p, 0.62))
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "X":
                    lit = (j == 0 or rows[j - 1][i] == ".") or (i == 0 or row[i - 1] == ".")
                    shadow = (j == 4 or rows[j + 1][i] == ".") or (i == 4 or row[i + 1] == ".")
                    t = 3.4 if lit and not shadow else (1.4 if shadow and not lit else 2.4)
                    self.fpx(part, f, ox + i, oy + j, tone(R, t))
                elif ch == "o":
                    self.fpx(part, f, ox + i, oy + j, dark)

    def _inside(self, part, f, i, j):
        x, y, w, h = self.face(part, f)
        return 0 <= i < w and 0 <= j < h

    def outline_rows(self, part, j, c, sides=SIDES):
        for f in sides:
            w = self.face(part, f)[2]
            for i in range(w):
                self.fpx(part, f, i, j, c)

    def save(self, base_name=None):
        n = base_name or self.name
        self.im.save(os.path.join(ENT, f"{n}_unit.png"))
        tpath = os.path.join(ENT, f"{n}_unit_team.png")
        if self.team.getbbox():
            self.team.save(tpath)
        elif os.path.exists(tpath):
            os.remove(tpath)


# ------------------------------------------------------------------ preview / portraits
def tinted(s, rgb):
    """The skin with its team layer multiplied by rgb (what TeamColourLayer draws)."""
    out = s.im.copy()
    op, tp = out.load(), s.tp
    W, H = s.size
    for y in range(H):
        for x in range(W):
            t = tp[x, y]
            if t[3]:
                op[x, y] = (t[0] * rgb[0] // 255, t[1] * rgb[1] // 255, t[2] * rgb[2] // 255, 255)
    return out


def _blit_face(dst, src, box, f, ox, oy, mirror=False):
    x, y, w, h = faces(box)[f]
    region = src.crop((x, y, x + w, y + h))
    if mirror:
        region = region.transpose(Image.FLIP_LEFT_RIGHT)
    dst.alpha_composite(region, (ox, oy))


def mock_villager(img, back=False):
    """Front (or back) view of the villager model: 16 x 34 texels (head 10, body 12, legs 12)."""
    L = VILLAGER
    c = Image.new("RGBA", (16, 34), (0, 0, 0, 0))
    f = "back" if back else "front"
    _blit_face(c, img, L["leg"], f, 4, 22)
    _blit_face(c, img, L["leg"], f, 8, 22, mirror=True)
    _blit_face(c, img, L["body"], f, 4, 10)
    _blit_face(c, img, L["jacket"], f, 4, 10)
    _blit_face(c, img, L["arm"], f, 0, 10)
    _blit_face(c, img, L["arm"], f, 12, 10, mirror=True)
    _blit_face(c, img, L["head"], f, 4, 0)
    if not back:
        _blit_face(c, img, L["nose"], "front", 7, 7)
    _blit_face(c, img, L["hat"], f, 4, 0)
    return c


def mock_humanoid(img, layout, back=False):
    """Front/back view of a humanoid (zombie / skeleton / piglin): 16 x 32 texels."""
    L = layout
    c = Image.new("RGBA", (18, 32), (0, 0, 0, 0))
    f = "back" if back else "front"
    hw = L["head"][2]
    aw = L["arm"][2] if "arm" in L else 4
    if "leg" in L:
        lw = L["leg"][2]
        _blit_face(c, img, L["leg"], f, 9 - lw, 20)
        _blit_face(c, img, L["leg"], f, 9, 20, mirror=True)
    else:
        _blit_face(c, img, L["leg_r"], f, 5, 20); _blit_face(c, img, L["pants_r"], f, 5, 20)
        _blit_face(c, img, L["leg_l"], f, 9, 20); _blit_face(c, img, L["pants_l"], f, 9, 20)
    _blit_face(c, img, L["body"], f, 5, 8)
    if "jacket" in L:
        _blit_face(c, img, L["jacket"], f, 5, 8)
    if "arm" in L:
        _blit_face(c, img, L["arm"], f, 5 - aw, 8)
        _blit_face(c, img, L["arm"], f, 13, 8, mirror=True)
    else:
        _blit_face(c, img, L["arm_r"], f, 1, 8); _blit_face(c, img, L["sleeve_r"], f, 1, 8)
        _blit_face(c, img, L["arm_l"], f, 13, 8); _blit_face(c, img, L["sleeve_l"], f, 13, 8)
    _blit_face(c, img, L["head"], f, 9 - hw // 2, 0)
    if "hat" in L:
        _blit_face(c, img, L["hat"], f, 9 - hw // 2, 0)
    if "snout" in L and not back:
        _blit_face(c, img, L["snout"], "front", 7, 4)
    return c


def portrait(s, bg_top, bg_bottom, frame, layout="villager", out_size=(64, 64)):
    """A 32x32 head-and-shoulders bust built from the finished skin (head and hat at 2x), saved at 2x."""
    img = s.im
    c = Image.new("RGBA", (32, 32))
    d = c.load()
    for y in range(32):
        col = mix(bg_top, bg_bottom, y / 31)
        for x in range(32):
            d[x, y] = col + (255,)
    # a soft halo behind the head so dark hoods still read on dark backdrops
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 15.5, y - 12)
            if r < 13:
                p = d[x, y]
                d[x, y] = mix(p, sh(bg_top, 1.6), 0.25 * (1 - r / 13)) + (255,)
    if layout == "villager":
        L = VILLAGER
        bust = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        _blit_face(bust, img, L["arm_up"], "front", 0, 10)
        _blit_face(bust, img, L["arm_up"], "front", 12, 10)
        _blit_face(bust, img, L["body"], "front", 4, 10)
        _blit_face(bust, img, L["jacket"], "front", 4, 10)
        _blit_face(bust, img, L["head"], "front", 4, 0)
        _blit_face(bust, img, L["nose"], "front", 7, 7)
        _blit_face(bust, img, L["hat"], "front", 4, 0)
        c.alpha_composite(bust.resize((32, 32), Image.NEAREST), (0, 4))
    else:
        L = layout
        hw, hh = L["head"][2], L["head"][3]
        bust = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        ox = 8 - hw // 2
        _blit_face(bust, img, L["body"], "front", 4, hh)
        if "jacket" in L:
            _blit_face(bust, img, L["jacket"], "front", 4, hh)
        if "arm" in L:
            _blit_face(bust, img, L["arm"], "front", 0, hh)
            _blit_face(bust, img, L["arm"], "front", 12, hh, mirror=True)
        if "arm_r" in L:
            _blit_face(bust, img, L["arm_r"], "front", 0, hh); _blit_face(bust, img, L["sleeve_r"], "front", 0, hh)
            _blit_face(bust, img, L["arm_l"], "front", 12, hh); _blit_face(bust, img, L["sleeve_l"], "front", 12, hh)
        _blit_face(bust, img, L["head"], "front", ox, 0)
        if "hat" in L:
            _blit_face(bust, img, L["hat"], "front", ox, 0)
        if "snout" in L:
            _blit_face(bust, img, L["snout"], "front", 6, 4)
        c.alpha_composite(bust.resize((32, 32), Image.NEAREST), (0, 6))
    for i in range(32):
        for (x, y) in ((i, 0), (i, 31), (0, i), (31, i)):
            d[x, y] = frame + (255,)
    for i in range(1, 31):
        for (x, y) in ((i, 1), (i, 30), (1, i), (30, i)):
            d[x, y] = sh(frame, 0.55) + (255,)
    out = c.resize((64, 64), Image.NEAREST)
    if out_size != (64, 64):
        out = out.resize(out_size, Image.NEAREST)
    return out


SAMPLE_TEAMS = ((220, 50, 50), (60, 110, 230))


def contact_sheet(path, title, entries):
    """entries: list of (name, HD, mock_fn, portrait_img). Each tile: flat UV (base + team in red), front and back
    mock-ups in two team colours, the portrait, and a 1x mock (roughly the RTS-camera size)."""
    SC = 3
    tile_w, tile_h = 64 * SC + 4 * (18 * 5) + 64 + 60, max(64 * SC, 34 * 5) + 26
    cols = 2
    rows = (len(entries) + cols - 1) // cols
    sheet = Image.new("RGBA", (tile_w * cols + 8, tile_h * rows + 30), (52, 54, 58, 255))
    dr = ImageDraw.Draw(sheet)
    dr.text((8, 8), title, fill=(255, 230, 150))
    for k, (name, s, mock, por) in enumerate(entries):
        ox, oy = 8 + (k % cols) * tile_w, 30 + (k // cols) * tile_h
        dr.rectangle((ox, oy, ox + tile_w - 6, oy + tile_h - 6), fill=(70, 72, 78, 255))
        red = tinted(s, SAMPLE_TEAMS[0])
        blue = tinted(s, SAMPLE_TEAMS[1])
        W, H = s.size
        sc = SC * 64 // max(W, H)
        sheet.alpha_composite(red.resize((W * sc, H * sc), Image.NEAREST), (ox + 2, oy + 2))
        x = ox + 64 * SC + 8
        for img, back in ((red, False), (red, True), (blue, False), (blue, True)):
            m = mock(img, back)
            sheet.alpha_composite(m.resize((m.width * 5, m.height * 5), Image.NEAREST), (x, oy + 2))
            x += 18 * 5
        sheet.alpha_composite(por, (x + 4, oy + 2))
        small = mock(red, False)
        sheet.alpha_composite(small, (x + 24, oy + 74))
        sheet.alpha_composite(mock(blue, False), (x + 44, oy + 74))
        dr.text((ox + 4, oy + tile_h - 20), name, fill=(255, 255, 255))
    sheet.convert("RGB").save(path, optimize=True)
