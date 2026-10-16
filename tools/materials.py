#!/usr/bin/env python3
"""What every palette colour is MADE OF, and the surface detail and light response that follow - so a body painted by
tools/dsl_to_game.py matches a realistic world (lovish, Oct 15 03:25: "can you make minecraft look more realistic like
that picture I showed you and have the units match it" - Patrix-style PBR textures under a shader pack).

Three things come out of one deterministic detail function per material, so they always agree:
- the ALBEDO detail on the painted atlas (brushed and scratched steel, worn gold, woven cloth, grained leather and wood,
  barbed feathers, riveted mail) - subtle in amplitude: Minecraft does not mipmap entity textures, so fine contrast
  would shimmer at RTS zoom; the strong detail lives in the maps below;
- the LabPBR 1.3 NORMAL map (<texture>_n.png): RG = the surface normal (tangent space; green along +v, DOWN the
  texture - the convention Complementary's own generated normals use), B = material AO (255 = none), A = height;
- the LabPBR 1.3 SPECULAR map (<texture>_s.png): R = perceptual smoothness, G = F0 (0-229 linear; 230 iron, 231
  gold), B = porosity 0-64 (wets and darkens in rain) or subsurface 65-255 (light through feathers), A = emission
  0-254 (255 = none).
Iris / Oculus loads <path>_n.png and <path>_s.png beside any plain entity texture when a shader pack asks for normals
or specular (Oculus 1.8.0 SimplePBRLoader / PBRType, Oct 15 research) - without a shader they are never read.
"""
import math

# name fragment -> material (first match wins; checked in this order)
_BY_NAME = (
    ("glow", "emissive"), ("sun_gold", "gold"), ("sun", "emissive"),
    ("potion", "glass"),
    ("gold", "gold"), ("brass", "gold"),
    ("steel_bright", "blade"), ("steel_edge", "blade"),
    ("mail", "mail"),
    ("plate", "plate"), ("steel", "plate"), ("visor", "plate"), ("iron", "plate"),
    ("feather", "feather"),
    ("string", "string"),
    ("wood", "wood"), ("haft", "wood"),
    ("leather", "leather"), ("boot", "leather"), ("glove", "leather"), ("strap", "leather"),
    ("tabard", "cloth"), ("cloth", "cloth"), ("robe", "cloth"), ("hose", "cloth"), ("cord", "cloth"),
    ("hood", "cloth"), ("cape", "cloth"), ("sash", "cloth"), ("linen", "cloth"), ("wool", "cloth"),
    ("dark", "plate"),
)

# smoothness 0-1, F0 code (LabPBR G), porosity / SSS code (LabPBR B), albedo detail strength, height relief
MATERIALS = {
    "plate":    dict(smooth=0.62, f0=230, pore=0,   alb=1.0, relief=1.0),   # polished white-steel plate: iron
    "blade":    dict(smooth=0.86, f0=230, pore=0,   alb=0.6, relief=0.6),
    "gold":     dict(smooth=0.70, f0=231, pore=0,   alb=1.0, relief=1.0),
    "mail":     dict(smooth=0.40, f0=230, pore=0,   alb=1.0, relief=1.6),
    "cloth":    dict(smooth=0.10, f0=10,  pore=52,  alb=1.0, relief=1.0),
    "string":   dict(smooth=0.20, f0=10,  pore=30,  alb=0.5, relief=0.5),
    "leather":  dict(smooth=0.42, f0=10,  pore=26,  alb=1.0, relief=1.0),
    "wood":     dict(smooth=0.26, f0=10,  pore=44,  alb=1.0, relief=1.0),
    "feather":  dict(smooth=0.22, f0=10,  pore=150, alb=1.0, relief=0.8),  # B >= 65: subsurface - light comes through
    "glass":    dict(smooth=0.94, f0=10,  pore=0,   alb=0.0, relief=0.0),
    "emissive": dict(smooth=0.50, f0=10,  pore=0,   alb=0.0, relief=0.0, emit=220),
    "plain":    dict(smooth=0.30, f0=10,  pore=10,  alb=0.6, relief=0.6),
}


# The realism pass (lovish, Oct 15 23:13: "i want the units to match the realistic minecraft"). Until Oct 15 the
# albedo detail stayed at 2-6% because entity textures had no mipmaps and finer contrast shimmered from the RTS
# camera; the unit atlases are mipmapped since 612f4b9, so the surface can carry what a realistic pack's textures
# carry: plate with worn bright edges and a dark crease inside them, tarnish and scratches; cloth with real folds and
# uneven dye; leather scuffs; wood grain. REALISM = False paints the pre-pass surfaces (comparison renders).
REALISM = True


def material_for(colour_name):
    n = colour_name.lower()
    for frag, mat in _BY_NAME:
        if frag in n:
            return mat
    return "plain"


# how a cloth is MADE (lovish, Oct 16 07:11: "add texture over the units", "look at the concept art and the detail it
# holds"): the surface detail of a cloth colour, at a model pixel's scale - never a texel's (the felt, Oct 16 04:15)
_CLOTH_STYLE = (
    ("tabard", "twill"), ("cape", "twill"), ("sash", "twill"),     # a dyed wool: folds, a woven border round a panel
    ("robe", "linen"), ("linen", "linen"), ("hood", "linen"),      # folds and a stitched hem
    ("hose", "knit"), ("wool", "knit"),                            # ribbed
    ("cord", "cord"),                                              # twisted
    ("cloth", "quilt"),                                            # the levy's and squire's padded jacks: quilted
)


def style_for(colour_name):
    """The make of a cloth colour - 'quilt' (a padded gambeson), 'twill' (a tabard or cape), 'linen', 'knit' or 'cord' -
    or None for every other material (tools/dsl_to_game.py passes it to Face)."""
    if material_for(colour_name) != "cloth":
        return None
    n = colour_name.lower()
    for frag, st in _CLOTH_STYLE:
        if frag in n:
            return st
    return "linen"


# one ring of mail as the RTS camera can hold it, 6 x 4 texels (0.75 x 0.5 px at 8 texels a pixel; rows staggered by
# half a ring): the upper arc lit, the hole dark, the lower arc under the next row's overlap
_MAIL_RING = (
    (0.30, 0.62, 1.00, 1.00, 0.62, 0.30),
    (0.62, 0.95, 0.26, 0.26, 0.95, 0.62),
    (0.50, 0.72, 0.20, 0.20, 0.72, 0.50),
    (0.22, 0.38, 0.55, 0.55, 0.38, 0.22),
)


def _smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def _line(d, half):
    """1 on a line at distance d = 0, falling smoothly to 0 at |d| = half (a soft line ~2 x half texels wide)."""
    return max(0.0, 1.0 - (d / half) ** 2) if abs(d) < half else 0.0


# ------------------------------------------------------------------ deterministic noise -----------------------------
def _hash(*ints):
    h = 2166136261
    for v in ints:
        h = ((h ^ (v & 0xffffffff)) * 16777619) & 0xffffffff
    h ^= h >> 13
    h = (h * 0x5bd1e995) & 0xffffffff
    h ^= h >> 15
    return h / 4294967295.0


def _value(seed, x, y, scale):
    """Smooth value noise in [-1, 1] at a feature size of `scale` texels."""
    fx, fy = x / scale, y / scale
    x0, y0 = math.floor(fx), math.floor(fy)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = _hash(seed, x0, y0)
    b = _hash(seed, x0 + 1, y0)
    c = _hash(seed, x0, y0 + 1)
    d = _hash(seed, x0 + 1, y0 + 1)
    return 2.0 * ((a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty) - 1.0


def _scratches(seed, fw, fh, density):
    """A few short straight scratches per face (count by area), as segments in face texels."""
    area = fw * fh / float(density * density)          # in model px^2
    n = int(area / 9.0 + _hash(seed, 7) * 1.5)
    out = []
    for k in range(min(n, 12)):
        x, y = _hash(seed, k, 1) * fw, _hash(seed, k, 2) * fh
        ang = _hash(seed, k, 3) * math.pi
        length = density * (0.4 + 0.9 * _hash(seed, k, 4))
        out.append((x, y, x + math.cos(ang) * length, y + math.sin(ang) * length))
    return out


def _seg_dist(px, py, seg):
    x0, y0, x1, y1 = seg
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / L2))
    return math.hypot(px - (x0 + t * dx), py - (y0 + t * dy))


# ------------------------------------------------------------------ one face ----------------------------------------
class Face:
    """The detail of one painted face: call at(i, j) for each texel (i across, j down, both from the face's top-left)
    -> (albedo multiplier, height 0..1, smoothness offset). `vertical` is the direction cloth hangs and wood grows
    along: along j for side faces, along the longer edge for tops (the realism pass: wood and leather follow the longer
    edge on every face - a crossbow stock lies flat, its grain runs along it).

    The realism pass's surface detail lives at a MODEL PIXEL's scale (lovish, Oct 16 07:11: "add texture over the
    units", "sleeker, with more detail, and depth"): quilting channels on a padded jack, a woven border on a tabard's
    panels, grain lines and knots and plank seams in wood, rivets and a few hammered dents on plate, an engraved line
    round gold, stitched seams and creases in leather, rows of rings in mail. Big faces carry it; a face under ~1.5 px
    stays nearly plain. The HEIGHT (-> the shader's normal map) only ever gets the broad part of a feature - a groove,
    a puff, a dome - never a one-texel pattern: those came back as felt under the shader's sun (Oct 16 04:15)."""

    def __init__(self, material, fw, fh, density, seed, kind="side", open_i=False, open_j=False, style=None,
                 panel=False):
        """open_i / open_j: that pair of borders is a JOINT, not an edge (one segment of a chained arc - the prod of
        the Crossbow Levy): no worn edge, crease or bevel there, so the chain reads as one continuous piece.
        style: the make of a cloth (style_for: quilt, twill, linen, knit, cord). panel: the face is the broad side of
        a thin cube (a tabard's or a cape's panel) - a woven border runs round it."""
        self.m, self.fw, self.fh, self.D, self.seed = material, fw, fh, density, seed
        self.open_i, self.open_j = open_i, open_j
        self.p = MATERIALS[material]
        self.style, self.panel = style, panel
        self.along_j = kind == "side" or fh >= fw
        if REALISM and material in ("wood", "leather"):
            self.along_j = fh >= fw
        self.scratches = _scratches(seed, fw, fh, density) if material in ("plate", "gold", "blade") else ()
        self.bevel = max(1.0, density * 0.25)            # a rounded edge a quarter of a model pixel wide
        D = float(density)
        wu, wv = (fw, fh) if self.along_j else (fh, fw)   # texels across / along the hang or the grain
        self.wu, self.wv = wu, wv
        self.quilt = self.border = self.hem = self.planks = self.engrave = None
        self.rivets, self.knots, self.creases, self.dents = [], [], [], []
        if not REALISM:
            return
        big = min(fw, fh) >= 1.5 * D
        if material == "cloth":
            if style == "quilt" and wu >= 0.8 * D and wv >= 1.2 * D:
                # channels ~1.1 px wide, a whole number of them across the face: a stitch line at both borders (a
                # sleeve's narrow face is one puffed channel)
                self.quilt = wu / max(1, int(round(wu / (1.1 * D))))
            if style == "twill" and panel and fw >= 3.0 * D and fh >= 3.0 * D:
                self.border = (round(0.30 * D), round(0.80 * D))        # the band's outer / inner inset, texels
            if style == "linen" and kind == "side" and fh >= 4.0 * D and fw >= 1.5 * D and not open_j:
                self.hem = 0.62 * D                                     # a stitched hem this far above the bottom
        elif material == "wood":
            if wu >= 3.5 * D and wv >= 3.5 * D:                         # boards: a pavise, a shield
                self.planks = wu / max(2, int(round(wu / (1.7 * D))))
            if wu >= 0.85 * D and wv >= 3.0 * D:
                nk = int(wu * wv / (D * D) / 24.0 + _hash(seed, 11) * 1.3)
                for k in range(min(nk, 3)):
                    self.knots.append((wu * (0.22 + 0.56 * _hash(seed, k, 12)), wv * (0.10 + 0.80 * _hash(seed, k, 13)),
                                       D * (0.20 + 0.12 * _hash(seed, k, 14))))
        elif material == "plate":
            if min(fw, fh) >= 2.5 * D:
                nd = int(fw * fh / (D * D) / 14.0 + _hash(seed, 51) * 1.2)
                for k in range(min(nd, 4)):
                    self.dents.append((fw * _hash(seed, k, 52), fh * _hash(seed, k, 53),
                                       D * (0.35 + 0.35 * _hash(seed, k, 54))))
            if kind == "side" and min(fw, fh) >= 2.2 * D and fw >= 3.0 * D:
                ins = 0.55 * D
                ys = [fh / 2.0] if (fh < 4.0 * D or open_j) else [ins, fh - ins]
                xs = [] if open_i else [ins, fw - ins]
                self.rivets = [(rx, ry) for ry in ys for rx in xs]
        elif material == "gold":
            if big:
                self.engrave = max(3.0, min(5.0, 0.3 * min(fw, fh)))   # the engraved line's inset, texels
        elif material == "leather":
            if wv >= 2.0 * D and wu >= 0.8 * D:
                nc = int(wv / (1.4 * D) + _hash(seed, 21))
                for k in range(min(nc, 5)):
                    self.creases.append((wv * (0.12 + 0.76 * _hash(seed, k, 22)), (_hash(seed, k, 23) - 0.5) * 0.30,
                                         0.55 + 0.45 * _hash(seed, k, 24)))
            # the stitched seam: along the long edges of a strap, a bracer, a boot; round all four on a big face
            long_x = self.along_j                                # the long edges are the left / right ones
            full = min(fw, fh) >= 2.5 * D
            self.seam_x = not open_i and (full or (long_x and fw >= 1.2 * D and fh >= 1.5 * D))
            self.seam_y = not open_j and (full or (not long_x and fh >= 1.2 * D and fw >= 1.5 * D))
        elif material == "mail":
            self.ring = (max(4, 2 * int(round(D * 0.375))), max(3, int(round(D * 0.5))))   # a ring's cell: 6 x 4

    def at(self, i, j):
        m, D, s = self.m, self.D, self.seed
        if m in ("glass", "emissive"):
            return 1.0, 1.0, 0.0
        x, y = i + 0.5, j + 0.5
        # distance to the face's border, in texels: the bevel (rounded plate edges catch the light)
        e = min(1e9 if self.open_i else min(x, self.fw - x), 1e9 if self.open_j else min(y, self.fh - y))
        bev = min(1.0, e / self.bevel)
        h = 0.62 + 0.38 * math.sqrt(bev)
        a, sm = 1.0, 0.0
        u, v = (x, y) if self.along_j else (y, x)        # v runs along the grain / hang; u across it
        if m in ("plate", "blade", "gold") and REALISM:
            # brushed along the long axis at a model-pixel scale, not a texel's: texel-scale streaks and grain read as
            # felt under the shader's sun (lovish, Oct 16: "almost like theres felt over the unit")
            streak = _value(s, u * 0.6, 0.0, 2.0)
            grime = _value(s + 1, x, y, 3.0 * D)          # broad tonal drift over a plate
            tarnish = max(0.0, _value(s + 5, x, y, 1.4 * D))   # duller patches
            a *= 1.0 + 0.025 * streak * self.p["alb"] + 0.07 * grime - 0.06 * tarnish
            sm += 0.10 * grime - 0.04 * abs(streak) - 0.15 * tarnish
            if m == "plate":
                # hand-hammered: a few shallow dents, each a model pixel or so across (a dent field everywhere cost
                # the normal map twice its bytes - a flat plate compresses to nothing; Oct 16)
                for (cx, cy, r) in self.dents:
                    q = ((x - cx) ** 2 + (y - cy) ** 2) / (r * r)
                    if q < 1.0:
                        w = (1.0 - q) ** 2
                        h -= 0.05 * w
                        a *= 1.0 + 0.06 * w * (y - cy) / r     # its upper wall in shade, the lower one lit
                        break
            # the worn, polished edge, rubbed brighter in places along it (its width stays one texel: a wear noise in
            # the width cost the normal and specular maps ~12% more bytes for a difference the camera never holds)
            if e < 1.0:
                a *= (1.10 if m != "blade" else 1.05) + 0.07 * max(0.0, _value(s + 9, x, y, 0.8 * D))
                sm += 0.08
            elif bev < 1.0 and e < self.bevel + 1.0:
                a *= 0.90                                     # the crease just inside it holds grime
                h -= 0.06
            elif y < self.bevel * 1.2:
                a *= 1.04
            if self.engrave is not None:
                # gold: an engraved line round the face, the rim outside it burnished
                lp = _line(e - self.engrave, 1.15)
                a *= 1.0 - 0.30 * lp
                h -= 0.07 * lp
                sm -= 0.12 * lp
                if e < self.engrave - 1.0:
                    a *= 1.03
                    sm += 0.06
            for (rx, ry) in self.rivets:
                dx, dy = x - rx, y - ry
                rr = math.hypot(dx, dy) / (0.27 * D)
                if rr < 1.5:
                    # a domed head in a little countersunk ring (the plate's face is already the top of the height
                    # range, so the dome is carved round, not raised: the absolute height of the plate is unchanged)
                    if rr < 1.0:
                        h -= 0.08 * rr * rr
                        a *= 1.06 - 0.20 * (dy / (0.27 * D))  # lit from above
                        sm += 0.12
                    else:
                        h -= 0.08 * (1.5 - rr) / 0.5
                        a *= 0.84 + 0.10 * (rr - 1.0) / 0.5   # the dark ring round it
                    break
            for seg in self.scratches:
                dd = _seg_dist(x, y, seg)
                if dd < 0.8:
                    a *= 1.10
                    h -= 0.03 * (0.8 - dd)
                    sm -= 0.20
                    break
        elif m in ("plate", "blade", "gold"):
            streak = _value(s, u * 3.0, 0.0, 2.0)          # brushed along the long axis
            grime = _value(s + 1, x, y, 3.0 * D)
            a *= 1.0 + 0.020 * streak * self.p["alb"] + 0.035 * grime
            sm += 0.10 * grime - 0.05 * abs(streak)
            h += 0.02 * streak
            if y < self.bevel * 1.2:
                a *= 1.06 if m != "blade" else 1.03          # the lit top edge
            elif bev < 1.0:
                a *= 1.0 - 0.05 * (1.0 - bev)                 # the other edges: a little grime
            for seg in self.scratches:
                dd = _seg_dist(x, y, seg)
                if dd < 0.8:
                    a *= 1.07
                    h -= 0.10 * (0.8 - dd)
                    sm -= 0.25
                    break
        elif m == "mail" and REALISM:
            # rows of rings, each row lying over the top of the one below: a ring is lit across its upper arc and in
            # shadow where the next row overlaps it. A regular pattern (no per-ring noise - random rings read as grit)
            cw, rh = self.ring
            row = int(y // rh)
            fy = (y - row * rh) / rh                         # 0 at the top of the row, 1 at its bottom
            off = (cw // 2) * (row % 2)
            ring = _MAIL_RING[min(3, int(fy * 4))][min(5, int(((x - off) % cw) / cw * 6))]
            a *= 0.56 + 0.60 * ring
            h = 0.40 + 0.10 * (1.0 - fy) ** 0.7 + 0.03 * ring
            sm += 0.20 * ring - 0.12
        elif m == "mail":
            period = max(2.0, D * 0.45)                     # rows of riveted rings
            row = math.floor(y / period)
            cx = (math.floor(x / period + 0.5 * (row % 2)) - 0.5 * (row % 2) + 0.5) * period
            cy = (row + 0.5) * period
            r = math.hypot(x - cx, y - cy) / (period * 0.5)
            ring = max(0.0, 1.0 - abs(r - 0.62) * 3.0)
            h = 0.35 + 0.6 * ring
            a *= 0.82 + 0.30 * ring
            sm += 0.15 * ring - 0.1
        elif m in ("cloth", "string"):
            weave = ((int(u) // 1) + (int(v) // 1)) % 2      # over / under, one texel threads
            fold = math.sin(u / (D * 2.2) * math.pi * 2 + _hash(s, 3) * 6.28)
            nz = _value(s, x, y, 1.5)
            if REALISM:   # deep folds, uneven dye - and no texel-scale weave: at 8 texels a model pixel a one-texel
                # checker is finer than any real cloth and, in the height map, the shader lit it as felt (Oct 16)
                nz = _value(s, x, y, 0.5 * D)
                fa, fz = (0.05, 0.06) if self.quilt else (0.15, 0.20)   # a padded jack hangs stiff: shallow folds
                a *= 1.0 + fa * fold + 0.025 * nz + 0.06 * _value(s + 3, x, y, 2.0 * D)
                h = 0.55 + fz * fold + 0.03 * nz
                a, h = self._cloth(x, y, u, v, e, a, h)
            else:
                a *= 1.0 + 0.030 * (weave - 0.5) * self.p["alb"] + 0.040 * fold + 0.015 * nz
                h = 0.55 + 0.12 * weave + 0.18 * fold + 0.05 * nz
            if bev < 1.0:
                h *= 0.85 + 0.15 * bev                         # cloth edges are soft, not bevelled
        elif m == "leather":
            grain = _value(s, x, y, 0.45 * D if REALISM else 1.6)   # realism: a model-pixel grain, not texel fuzz
            blotch = _value(s + 2, x, y, D * 1.5)
            if REALISM:   # scuffed lighter where it rubs, darker where oiled
                a *= 1.0 + 0.06 * grain + 0.12 * blotch + (0.08 if blotch > 0.55 else 0.0)
                h += 0.02 * grain
                sm += 0.12 * blotch                          # worn, shinier patches
                a, h, sm = self._leather(x, y, u, v, a, h, sm)
            else:
                a *= 1.0 + 0.035 * grain + 0.055 * blotch
                h += 0.06 * grain
                sm += 0.12 * blotch
                # stitching: a dotted line one bevel in from each edge
                if abs(e - self.bevel * 1.6) < 0.5 and int(x + y) % 3 == 0:
                    a *= 0.84
                    h -= 0.12
        elif m == "wood":
            if REALISM:
                a, h, sm = self._wood(u, v)
            else:
                ring = math.sin((u + 1.5 * _value(s, u, v * 0.15, 4.0)) / max(1.2, D * 0.3) * math.pi * 2)
                a *= 1.0 + 0.060 * ring + 0.03 * _value(s + 1, x, y, 2.0)
                h = 0.6 + 0.14 * ring
        elif m == "feather":
            mid = (self.fw if self.along_j else self.fh) / 2.0
            shaft = max(0.0, 1.0 - abs(u - mid) / max(0.8, D * 0.12))   # the rachis down the middle
            barb = math.sin((v + abs(u - mid) * 0.9) / max(1.5, D * 0.22) * math.pi * 2)
            if REALISM:
                # a wing reads by the shade each row of feathers throws on the next: dark where the row above
                # overlaps (the face's top), the vane lighter toward its free tip, the barbs and shaft stronger
                overlap = min(1.0, y / max(1.0, D * 0.7))
                a *= (0.74 + 0.26 * overlap) * (1.0 + 0.08 * barb + 0.09 * shaft) * (0.97 + 0.06 * y / max(1, self.fh))
            else:
                a *= 1.0 + 0.05 * barb + 0.06 * shaft
            h = 0.55 + 0.15 * barb + 0.25 * shaft
        else:
            a *= 1.0 + (0.04 * _value(s, x, y, 0.5 * D) if REALISM else 0.02 * _value(s, x, y, 2.0))
        relief = self.p["relief"]
        h = 0.5 + (h - 0.5) * relief
        return a, max(0.0, min(1.0, h)), sm

    # ---- the realism pass's made surfaces ----
    def _cloth(self, x, y, u, v, e, a, h):
        D, st = self.D, self.style
        if self.quilt:
            # a padded jack: the stitching pulls the cloth down in lines, the padding puffs up between them
            P = self.quilt
            q = (u % P) / P
            puff = math.sin(math.pi * q) ** 0.7
            h += 0.20 * puff - 0.10
            a *= 0.85 + 0.19 * puff
            k = int(round(u / P))
            if 0 < k < int(round(self.wu / P)) and -0.5 <= u - k * P < 0.5 and (v % 5.0) < 3.0:
                a *= 0.74                                     # the stitches, dashed along the line
        elif st == "twill":
            # a dyed wool: its folds, and a woven border round a panel (a diagonal twill was tried - 3% ribs four texels
            # apart: invisible from the RTS camera and +50% on the team mask's bytes)
            if self.border:
                b0, b1 = self.border
                if e < b1 + 1.5:
                    band = _smooth(e - b0 + 0.5) * _smooth(b1 - e + 0.5)
                    edge = max(_line(e - b0, 1.0), _line(e - b1, 1.0))
                    # the braid between the lines: chevrons along the edge
                    along = y if min(x, self.fw - x) < min(y, self.fh - y) else x
                    chev = ((along + abs(e - (b0 + b1) * 0.5) * 1.6) % 6.0) / 6.0
                    a *= (1.0 + 0.14 * band) * (1.0 - 0.32 * edge) * (1.0 - 0.10 * band * (chev < 0.4))
                    h += 0.05 * band
        elif st == "knit" and self.wu >= 0.9 * D:
            a *= 1.0 + 0.04 * math.cos(u / 4.0 * math.pi * 2)    # ribbed hose, ~2 ribs a model pixel
        elif st == "cord":
            ph = ((v + u) % 4.0) / 4.0
            a *= 1.0 + 0.10 * (1.0 - 2.0 * abs(ph - 0.5)) - 0.05  # twisted strands
        if self.hem is not None:
            yb = self.fh - y                                  # texels above the bottom edge
            if yb < self.hem - 0.6:
                a *= 0.93                                     # the turned-up hem: doubled cloth
                h += 0.04
            if abs(yb - self.hem) < 0.6 and (x % 5.0) < 3.0:
                a *= 0.82                                     # its stitches
        return a, h

    def _leather(self, x, y, u, v, a, h, sm):
        D = self.D
        for (cv, tilt, st) in self.creases:
            d = v - (cv + tilt * (u - self.wu * 0.5))
            if -2.0 < d < 3.2:
                cr = _line(d, 1.6) * st
                a *= (1.0 - 0.16 * cr) * (1.0 + 0.05 * st * _line(d - 2.2, 1.0))   # a dark fold, its lip lighter
                h -= 0.05 * cr
        ex = min(x, self.fw - x) if self.seam_x else 1e9
        ey = min(y, self.fh - y) if self.seam_y else 1e9
        es = min(ex, ey)
        ds = es - 0.38 * D
        if abs(ds) < 1.6:
            g = _line(ds, 1.5)
            a *= 1.0 - 0.14 * g                               # the seam pressed into the leather
            h -= 0.05 * g
            along = y if ex < ey else x
            if abs(ds) < 0.6 and (along % 4.0) < 2.4:
                a *= 1.22                                     # the waxed thread
        return a, h, sm

    def _wood(self, u, v):
        D, s = self.D, self.seed
        a, sm = 1.0, 0.0
        x, y = (u, v) if self.along_j else (v, u)
        e = min(1e9 if self.open_i else min(x, self.fw - x), 1e9 if self.open_j else min(y, self.fh - y))
        h = 0.62 + 0.38 * math.sqrt(min(1.0, e / self.bevel))
        g = u
        if self.planks:
            P = self.planks
            k = min(int(u // P), int(round(self.wu / P)) - 1)
            g += 13.7 * k                                     # each board its own grain and tone
            a *= 1.0 + 0.10 * (_hash(s, k, 41) - 0.5)
            kk = int(round(u / P))
            if 0 < kk < int(round(self.wu / P)):
                sd = abs(u - kk * P)
                a *= 1.0 - 0.38 * _line(sd, 1.3)              # the joint between two boards
                h -= 0.12 * _line(sd, 1.8)
        # the grain: long, gently wavy lines along the piece, a couple per model pixel
        g += 0.45 * D * _value(s, u * 0.12, v, 3.0 * D)
        core = None
        for (ku, kv, kr) in self.knots:
            du, dv = u - ku, (v - kv) / 2.4
            r2 = (du * du + dv * dv) / (kr * kr)
            if r2 < 16.0:
                g += kr * 1.4 * math.exp(-r2 * 0.30) * (1.0 if du >= 0 else -1.0)   # the grain flows round it
                if r2 < 2.1:
                    core = math.sqrt(r2)
        Pg = 0.5 * D
        f = (g / Pg) % 1.0
        band = int(math.floor(g / Pg))
        ln = _line(min(f, 1.0 - f) * Pg, 1.1) * (0.6 + 0.8 * _hash(s, band, 31))
        a *= (1.0 + 0.05 - 0.10 * f - 0.18 * ln) * (1.0 + 0.06 * _value(s + 1, u * 0.3, v, 2.5 * D))
        h += 0.02 * _value(s + 2, u * 0.3, v, 2.0 * D) - 0.03 * ln
        sm -= 0.05 * ln
        if core is not None:
            if core < 1.0:                                    # the knot: a dark heart, standing a little proud
                a *= 0.60 + 0.22 * core
                h += 0.05 * (1.0 - core * core)
                sm += 0.08
            else:
                a *= 0.84 + 0.16 * (core - 1.0) / 0.45
        return a, h - 0.02, sm


def specular(material, smooth_offset=0.0):
    """(R, G, B, A) of the LabPBR specular map for a texel of this material."""
    p = MATERIALS[material]
    sm = max(0.0, min(1.0, p["smooth"] + smooth_offset))
    emit = p.get("emit")
    return (int(round(sm * 255)), p["f0"], p["pore"], emit if emit is not None else 255)


# ------------------------------------------------------------------ LabPBR 1.3 encoding: ONE encoder ---------------
# Units (tools/dsl_to_game.py) and blocks (tools/gen_blocks.py) both encode through these, so the two can never drift
# apart on the format (green direction, AO / height / emission conventions) - the Oct 15 brainstorm's Tier 1 item.
# numpy is imported here, not at the top: the per-texel palette code above stays importable without it.

def height_normals(h, strength, wrap=False):
    """Tangent-space unit normals of a height field: x right, y DOWN the texture (+v), z out of the surface. wrap: the
    field tiles (a block texture); otherwise its edges are one-sided (an atlas face - a slope never leaks across)."""
    import numpy as np
    if wrap:
        gy, gx = np.gradient(np.pad(h, 1, mode="wrap"))
        gy, gx = gy[1:-1, 1:-1], gx[1:-1, 1:-1]
    elif min(h.shape) > 1:
        gy, gx = np.gradient(h)
    else:
        gy, gx = np.zeros_like(h), np.zeros_like(h)
    nx, ny, nz = -gx * strength, -gy * strength, np.ones_like(h)
    ln = np.sqrt(nx * nx + ny * ny + nz * nz)
    return nx / ln, ny / ln, nz / ln


def encode_normal(nx, ny, ao, height):
    """RGBA uint8 of a LabPBR _n map: RG the normal's x / y (-1..1), B ambient occlusion (1 = none -> 255), A height
    (0..1 -> 1..255: LabPBR reads 0 as 'no height', and parallax needs every opaque texel >= 1)."""
    import numpy as np
    out = np.empty(np.shape(nx) + (4,), dtype=np.uint8)
    out[..., 0] = np.clip((nx * 0.5 + 0.5) * 255, 0, 255).astype(np.uint8)
    out[..., 1] = np.clip((ny * 0.5 + 0.5) * 255, 0, 255).astype(np.uint8)
    out[..., 2] = np.clip(np.asarray(ao) * 255, 0, 255).astype(np.uint8)
    out[..., 3] = np.clip(1 + np.asarray(height) * 254, 1, 255).astype(np.uint8)
    return out


def encode_specular(smooth, f0=10, porosity=0, emission=255, shape=None):
    """RGBA uint8 of a LabPBR _s map from CODES (0-255 each, scalars or arrays): R perceptual smoothness, G F0 (0-229
    linear, 230-237 named metals, 255 a metal with its albedo as F0; 238-254 are undefined), B porosity 0-64 or
    subsurface 65-255, A emission 0-254 (255 = none)."""
    import numpy as np
    shp = shape if shape is not None else np.shape(smooth)
    out = np.empty(tuple(shp) + (4,), dtype=np.uint8)
    for i, v in enumerate((smooth, f0, porosity, emission)):
        out[..., i] = np.clip(np.rint(v), 0, 255).astype(np.uint8)
    return out


def check_labpbr(n, s, opaque=None):
    """Range self-check of a pair of LabPBR maps (uint8 RGBA arrays, same size or s may be smaller); returns a list of
    problems, empty when the maps are valid. opaque: bool mask of texels the albedo actually shows (default: all)."""
    import numpy as np
    probs = []
    m = np.ones(n.shape[:2], bool) if opaque is None else opaque
    x = n[..., 0].astype(np.float64) / 127.5 - 1
    y = n[..., 1].astype(np.float64) / 127.5 - 1
    bad = m & (x * x + y * y > 1.03)
    if bad.any():
        probs.append("normal: %d texels outside the unit disc (no valid z)" % int(bad.sum()))
    if (m & (n[..., 3] == 0)).any():
        probs.append("normal: height 0 on %d opaque texels (LabPBR: 0 = no height; parallax breaks)" % int((m & (n[..., 3] == 0)).sum()))
    ms = m if s.shape[:2] == n.shape[:2] else np.ones(s.shape[:2], bool)
    g = s[..., 1][ms]
    if g.size and ((g > 59) & (g < 230)).mean() > 0.01:
        probs.append("specular: dielectric F0 above 59 (23%%) on %.0f%% of texels - real dielectrics sit at 2-10%%"
                     % (100 * ((g > 59) & (g < 230)).mean()))
    if g.size and ((g >= 238) & (g < 255)).any():
        probs.append("specular: F0 code 238-254 on %d texels - undefined in LabPBR 1.3 (230-237 named metals, 255 = "
                     "albedo as F0)" % int(((g >= 238) & (g < 255)).sum()))
    a = s[..., 3][ms]
    if a.size and (a < 255).mean() > 0.5:
        probs.append("specular: emission on %.0f%% of texels (255 = none) - is A unset?" % (100 * (a < 255).mean()))
    return probs
