#!/usr/bin/env python3
"""What every palette colour is MADE OF, and the surface detail and light response that follow - so a body painted by
tools/dsl_to_game.py matches a realistic world (lovish, Oct 15 03:25: "can you make minecraft look more realistic like
that picture I showed you and have the units match it" - Patrix-style PBR textures under a shader pack).

Three things come out of one deterministic detail function per material, so they always agree:
- the ALBEDO detail on the painted atlas (brushed and scratched steel, worn gold, woven cloth, grained leather and wood,
  barbed feathers, riveted mail) - a brightness multiplier per texel, and for some materials a slight colour shift
  (uneven dye, warm and cool skin, warm gold highlights);
- the LabPBR 1.3 NORMAL map (<texture>_n.png): RG = the surface normal (tangent space; green along +v, DOWN the
  texture - the convention Complementary's own generated normals use), B = material AO (255 = none), A = height;
- the LabPBR 1.3 SPECULAR map (<texture>_s.png): R = perceptual smoothness, G = F0 (0-229 linear; 230 iron, 231
  gold), B = porosity 0-64 (wets and darkens in rain) or subsurface 65-255 (light through feathers and skin), A =
  emission 0-254 (255 = none).
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
    ("marble", "stone"),     # carved statue stone (the White Angel capitol's statue, tools/gen_angel_statue.py)
    ("string", "string"),
    ("wood", "wood"), ("haft", "wood"),
    ("leather", "leather"), ("boot", "leather"), ("glove", "leather"), ("strap", "leather"),
    ("tabard", "cloth"), ("cloth", "cloth"), ("robe", "cloth"), ("hose", "cloth"), ("cord", "cloth"),
    ("hood", "cloth"), ("cape", "cloth"), ("sash", "cloth"), ("linen", "cloth"), ("wool", "cloth"),
    ("skin", "skin"), ("lip", "skin"),
    ("fur_light", "furl"), ("fur_pale", "furl"), ("fur", "fur"), ("moss", "moss"), ("root", "wood"), ("berry_rim", "plain"),
    ("beard", "hair"), ("hair", "hair"),
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
    "skin":     dict(smooth=0.40, f0=7,   pore=140, alb=1.0, relief=0.6),  # F0 2.8%; subsurface: skin glows at its edges
    "hair":     dict(smooth=0.34, f0=10,  pore=30,  alb=1.0, relief=0.8),
    "fur":      dict(smooth=0.22, f0=10,  pore=60,  alb=1.0, relief=1.2),   # a beast's coat: matted strands, subsurface
    "furl":     dict(smooth=0.22, f0=10,  pore=60,  alb=1.6, relief=1.3),   # the pale cape: the same tufts, more contrast
    "moss":     dict(smooth=0.08, f0=10,  pore=120, alb=1.0, relief=1.4),   # a living cushion: clumps, deep pores
    "stone":    dict(smooth=0.48, f0=10,  pore=70,  alb=0.8, relief=0.7),  # honed marble: B >= 65, light enters it
    "glass":    dict(smooth=0.94, f0=10,  pore=0,   alb=0.0, relief=0.0),
    "emissive": dict(smooth=0.50, f0=10,  pore=0,   alb=0.0, relief=0.0, emit=220),
    "plain":    dict(smooth=0.30, f0=10,  pore=10,  alb=0.6, relief=0.6),
}


# The realism pass (lovish, Oct 15 23:13: "i want the units to match the realistic minecraft"). Until Oct 15 the
# albedo detail stayed at 2-6% because entity textures had no mipmaps and finer contrast shimmered from the RTS
# camera; the unit atlases are mipmapped since 612f4b9, so the surface can carry what a realistic pack's textures
# carry. REALISM = False paints the pre-pass surfaces (comparison renders).
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
# how a leather is used: a boot scuffs at the toe and heel, a glove creases at the knuckles, a strap at its bends
_LEATHER_STYLE = (("boot", "boot"), ("glove", "glove"))


def style_for(colour_name):
    """The make of a colour: for a cloth 'quilt' (a padded gambeson), 'twill' (a tabard or cape), 'linen', 'knit' or
    'cord'; for a leather 'boot', 'glove' or 'strap'; None for every other material (tools/dsl_to_game.py passes it to
    Face)."""
    mat = material_for(colour_name)
    n = colour_name.lower()
    if mat == "cloth":
        for frag, st in _CLOTH_STYLE:
            if frag in n:
                return st
        return "linen"
    if mat == "leather":
        for frag, st in _LEATHER_STYLE:
            if frag in n:
                return st
        return "strap"
    return None


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


# the same two on whole arrays (numpy): the realism pass paints a face at once - the same hash, bit for bit
def _hash_v(np, *vals):
    arrs = [np.asarray(v) for v in vals]
    M = np.uint64(0xffffffff)
    h = np.full(np.broadcast(*arrs).shape, 2166136261, dtype=np.uint64)
    for a in arrs:
        h = ((h ^ (a.astype(np.int64).astype(np.uint64) & M)) * np.uint64(16777619)) & M
    h ^= h >> np.uint64(13)
    h = (h * np.uint64(0x5bd1e995)) & M
    h ^= h >> np.uint64(15)
    return h.astype(np.float64) / 4294967295.0


def _value_v(np, seed, x, y, scale):
    fx = np.asarray(x, dtype=np.float64) / scale
    fy = np.asarray(y, dtype=np.float64) / scale
    fx, fy = np.broadcast_arrays(fx, fy)
    x0, y0 = np.floor(fx), np.floor(fy)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    xi, yi = x0.astype(np.int64), y0.astype(np.int64)
    a = _hash_v(np, seed, xi, yi)
    b = _hash_v(np, seed, xi + 1, yi)
    c = _hash_v(np, seed, xi, yi + 1)
    d = _hash_v(np, seed, xi + 1, yi + 1)
    top = a + (b - a) * tx
    return 2.0 * (top + ((c + (d - c) * tx) - top) * ty) - 1.0


def _line_v(np, d, half):
    """1 on a line at distance d = 0, falling smoothly to 0 at |d| = half (a soft line ~2 x half texels wide)."""
    return np.clip(1.0 - (d / half) ** 2, 0.0, 1.0)


def _smooth_v(np, t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


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


def _seg_dist_v(np, px, py, seg):
    x0, y0, x1, y1 = seg
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = np.zeros_like(px) if L2 == 0 else np.clip(((px - x0) * dx + (py - y0) * dy) / L2, 0.0, 1.0)
    return np.hypot(px - (x0 + t * dx), py - (y0 + t * dy))


# which cube axis a face's texel columns (i) and rows (j) run along, and its normal - ModelPart.Cube's box UV (1.20.1):
# top / bottom i along x, j along z; west / east i along z; north / south i along x; sides j down y
_FACE_AXES = {"top": ("x", "z", "y"), "bottom": ("x", "z", "y"), "west": ("z", "y", "x"), "east": ("z", "y", "x"),
              "north": ("x", "y", "z"), "south": ("x", "y", "z")}

# one ring of mail, drawn as a lit torus (the realism pass), in texels at 8x: ring spacing across (a model pixel) and
# down, the ring's radii, how far alternate rows lean, the wire (centre and half width, in ring radii), the hole's
# brightness. Rings of 6 texels with this overlap read as slanted dashes (Oct 16 lab); 8 reads as rings at close zoom
_MAIL = dict(cw=8.0, rh=5.5, rx=4.2, ry=3.4, lean=0.2, q0=0.70, qw=0.30, hole=0.38)
_MAIL_LIGHT = (-0.30, -0.62, 0.72)            # from above and a little to the left, toward the viewer (y DOWN the face)
_MAIL_MEAN = 0.876                            # the mesh's mean brightness: what the earlier ring grid averaged to (the far read)
_mail_cal = {}

# each material's mean brightness kept where the Oct 16 surfaces had it (the value groups the art direction tuned:
# light share, nothing near the grass, the team cloth's tone - tools/game_preview.py --art-check): the new detail moves
# light about inside a face, not the face's tone. Two on purpose a little above it: plate (+~4%: its lit bevels are what
# keeps a steel body off the grass value at the RTS camera) and twill (+~2%: the paint darkens linearly but lightens
# toward white, so deeper folds alone would sink the owner's colour)
_GAIN = {("cloth", "quilt"): 1.01, ("cloth", "twill"): 1.035, ("cloth", "linen"): 1.015, ("cloth", "knit"): 1.015,
         ("cloth", "cord"): 0.982, ("leather", "strap"): 1.015, ("leather", "glove"): 0.962, ("leather", "boot"): 0.982,
         "plate": 1.03, "gold": 1.03, "blade": 0.969, "mail": 0.997, "wood": 1.0, "skin": 1.01}


# ------------------------------------------------------------------ one face ----------------------------------------
class Face:
    """The detail of one painted face: texel(i, j) for each texel (i across, j down, both from the face's top-left)
    -> (albedo multiplier, height 0..1, smoothness offset, colour multiplier (r, g, b) or None); at(i, j) gives the
    first three.

    The realism pass paints at two scales (lovish, Oct 16: "the units need more realism and texture", "no felt
    texture"):
    - the MODEL PIXEL's: drapery folds that gather under a belt and a stitched hem on cloth (bunched at the elbow and
      knee ends of a sleeve or a leg), creases, scuffed edges, toe and heel wear and seams on leather, plate bevels lit
      (most along the top) with a dark crease inside them, dents, rivets and scratches, grain, knots and end rings in
      wood, mail rings drawn as lit tori - this is what the height (-> the shader's normal map) carries, never a
      one-texel pattern (those came back as felt under the shader's sun, Oct 16 04:15);
    - the TEXEL's, in the albedo only and always DIRECTIONAL: warp threads and slubs along the hang of a cloth, a twill's
      diagonal, brush lines along a plate, crinkles in leather, pores along a grain. Felt is isotropic fuzz; woven,
      brushed and grained surfaces have a direction, and the mips average it away from the RTS camera.
    The direction is the cube's LONG axis (face + cube from tools/dsl_to_game.py): a crossbow stock's grain runs along
    it on every face, and its end faces show the rings; cloth on a side face follows the hang (down the face)."""

    def __init__(self, material, fw, fh, density, seed, kind="side", open_i=False, open_j=False, style=None,
                 panel=False, face=None, cube=None):
        """open_i / open_j: that pair of borders is a JOINT, not an edge (one segment of a chained arc - the prod of
        the Crossbow Levy): no worn edge, crease or bevel there, so the chain reads as one continuous piece.
        style: the make (style_for). panel: the face is the broad side of a thin cube (a tabard's or a cape's panel) -
        a woven border runs round it. face: 'top', 'bottom', 'west', 'north', 'east' or 'south'; cube: its (w, h, d)
        in model px - together they give the grain direction and which end of a boot is the toe."""
        self.m, self.fw, self.fh, self.D, self.seed = material, fw, fh, density, seed
        self.open_i, self.open_j = open_i, open_j
        self.p = MATERIALS[material]
        self.style, self.panel, self.kind = style, panel, kind
        self.face, self.cube = face, cube
        self.bevel = max(1.0, density * 0.25)            # a rounded edge a quarter of a model pixel wide
        self.along_j = kind == "side" or fh >= fw
        self.end = False
        if not REALISM:
            self.scratches = _scratches(seed, fw, fh, density) if material in ("plate", "gold", "blade") else ()
            return
        self._grain_axis()
        A, H, S, T = self._paint()
        self._A, self._H, self._S = A.tolist(), H.tolist(), S.tolist()
        self._T = None if T is None else T.tolist()

    def _grain_axis(self):
        """along_j: the grain / hang runs down the face (j); else across it (i). end: the face is the END of a long
        cube (wood shows its rings there)."""
        if self.m in ("cloth", "string", "feather", "mail", "hair", "skin") or not self.face or not self.cube:
            return                         # the hang down a side face; the longer edge of a top
        w, h, d = self.cube
        ext = {"x": w, "y": h, "z": d}
        ai, aj, an = _FACE_AXES[self.face]
        order = sorted(ext.values(), reverse=True)
        long_ax = max(ext, key=ext.get) if order[0] >= 1.25 * order[1] else None
        if long_ax == ai:
            self.along_j = False
        elif long_ax == aj:
            self.along_j = True
        else:
            self.along_j = self.fh >= self.fw
            self.end = long_ax == an and order[0] >= 1.6 * order[1]

    def at(self, i, j):
        if REALISM:
            return self._A[j][i], self._H[j][i], self._S[j][i]
        return self._legacy_at(i, j)

    def texel(self, i, j):
        if REALISM:
            return self._A[j][i], self._H[j][i], self._S[j][i], (None if self._T is None else self._T[j][i])
        return self._legacy_at(i, j) + (None,)

    # ---- the realism pass: one face at once ----
    def _paint(self):
        import numpy as np
        m = self.m
        fw, fh = self.fw, self.fh
        jj, ii = np.mgrid[0:fh, 0:fw].astype(np.float64)
        x, y = ii + 0.5, jj + 0.5
        big = np.full(x.shape, 1e9)
        g = _Grid()
        g.np, g.D, g.x, g.y, g.ii, g.jj = np, float(self.D), x, y, ii, jj
        g.el, g.er = (big, big) if self.open_i else (x, fw - x)          # distance to each border, texels
        g.et, g.eb = (big, big) if self.open_j else (y, fh - y)
        g.e = np.minimum(np.minimum(g.el, g.er), np.minimum(g.et, g.eb))
        g.u, g.v = (x, y) if self.along_j else (y, x)                     # v along the grain / hang, u across
        g.wu, g.wv = (fw, fh) if self.along_j else (fh, fw)
        g.A = np.ones(x.shape)
        g.S = np.zeros(x.shape)
        g.T = None
        if m in ("glass", "emissive"):
            return g.A, np.ones(x.shape), g.S, None
        g.H = 0.62 + 0.38 * np.sqrt(np.clip(g.e / self.bevel, 0.0, 1.0))
        if m in ("plate", "blade", "gold"):
            self._metal(g)
        elif m == "mail":
            self._mail(g)
        elif m in ("cloth", "string"):
            self._cloth(g)
        elif m == "leather":
            self._leather(g)
        elif m == "wood":
            self._wood(g)
        elif m == "skin":
            self._skin(g)
        elif m == "hair":
            self._hair(g)
        elif m in ("fur", "furl"):
            self._fur(g)
        elif m == "moss":
            self._moss(g)
        elif m == "feather":
            self._feather(g)
        elif m == "stone":
            self._stone(g)
        else:
            g.A *= 1.0 + 0.04 * _value_v(np, self.seed, x, y, 0.5 * g.D)
        H = 0.5 + (g.H - 0.5) * self.p["relief"]
        A = g.A * _GAIN.get((m, self.style), _GAIN.get(m, 1.0))
        return A, np.clip(H, 0.0, 1.0), g.S, g.T

    def _tint(self, g, w, col):
        """Blend the colour multiplier toward col by weight w (array 0..1)."""
        np = g.np
        if g.T is None:
            g.T = np.ones(g.x.shape + (3,))
        g.T += w[..., None] * (np.asarray(col, dtype=np.float64) - g.T)

    def _edges(self, g, top, side, bottom, inner, up):
        """A worn bevel: the outer texels lit (top / side / bottom borders of a side face; every border of an upward
        face, `up`), the texel inside it dark (the crease at the bevel's foot holds grime), the next a little dark."""
        np, s, D = g.np, self.seed, g.D
        stack = np.stack([g.et, g.eb, g.el, g.er])
        near = np.argmin(stack, axis=0)                   # 0 top, 1 bottom, 2 left, 3 right
        if self.kind == "side":
            hl = np.choose(near, [top, bottom, side, side])
        else:
            hl = np.full(g.x.shape, up if self.kind == "top" else side)
        along = np.where(near < 2, g.x, g.y)
        wear = 0.75 + 0.5 * (0.5 + 0.5 * _value_v(np, s + 9, along, near * 37.0, 0.9 * D))
        e = g.e
        # the bevel is a quarter of a model pixel (2 texels at 8x) where the face is big enough to hold it: its outer
        # texel brightest, the next a little less; one texel on a small face
        bw = 2.0 if min(self.fw, self.fh) >= 8 else 1.0
        o = e < bw
        grade = np.where(e < 1.0, 1.0, 0.55)
        g.A = np.where(o, g.A * (1.0 + (hl - 1.0) * wear * grade), g.A)
        g.S += np.where(o, 0.10 * grade, 0.0)
        if min(self.fw, self.fh) >= 5 and inner < 1.0:
            ring = (e >= bw) & (e < bw + 1.0)
            k = np.where(near == 1, 1.0 - (1.0 - inner) * 0.5, inner) if self.kind == "side" else inner
            g.A = np.where(ring, g.A * k, g.A)
            g.H -= np.where(ring, 0.04, 0.0)
            g.S -= np.where(ring, 0.06, 0.0)
            g.A = np.where((e >= bw + 1.0) & (e < bw + 2.0), g.A * (1.0 - (1.0 - inner) * 0.5), g.A)
        return near

    def _metal(self, g):
        np, m, D, s = g.np, self.m, g.D, self.seed
        x, y, u, v = g.x, g.y, g.u, g.v
        fw, fh = self.fw, self.fh
        # brushed along the long axis: fine lines a texel across and ~15 long over a broader polish (directional - an
        # isotropic grain read as felt under the shader's sun, Oct 16)
        fine = _value_v(np, s, u, v * 0.07, 1.0)
        broad = _value_v(np, s + 1, u * 0.3, v * 0.04, 1.0)
        grime = _value_v(np, s + 2, x, y, 3.0 * D)                    # broad tonal drift over a plate
        tarnish = np.maximum(0.0, _value_v(np, s + 5, x, y, 1.4 * D))  # duller patches
        ab = {"plate": 0.085, "blade": 0.045, "gold": 0.04}[m]
        if self.kind != "side":
            ab *= 0.5            # an upward face mirrors the open sky (tools/dsl_to_game.py): the brushing shows less
        g.A *= 1.0 + ab * (0.65 * fine + 0.35 * broad) + 0.06 * grime - 0.05 * tarnish
        g.S += 0.08 * grime + 0.05 * fine - 0.12 * tarnish
        # the bevel: lit along the top, a little along the sides, shadowed underneath; a dark crease inside it
        if m == "blade":
            self._edges(g, 1.14, 1.08, 0.97, 0.92, 1.10)
        elif m == "gold":
            self._edges(g, 1.10, 1.05, 0.94, 0.84, 1.15)   # warm more than bright on a side (the tint below)
        else:
            self._edges(g, 1.22, 1.12, 1.03, 0.86, 1.12)       # the lower bevel still catches the ground's bounce
        if m == "plate" and min(fw, fh) >= 2.5 * D:
            # hand-hammered: a few shallow dents, each a model pixel or so across
            nd = int(fw * fh / (D * D) / 14.0 + _hash(s, 51) * 1.2)
            for k in range(min(nd, 4)):
                cx, cy, r = fw * _hash(s, k, 52), fh * _hash(s, k, 53), D * (0.35 + 0.35 * _hash(s, k, 54))
                q = ((x - cx) ** 2 + (y - cy) ** 2) / (r * r)
                w = np.where(q < 1.0, (1.0 - np.minimum(q, 1.0)) ** 2, 0.0)
                g.H -= 0.05 * w
                g.A *= 1.0 + 0.07 * w * (y - cy) / r              # its upper wall in shade, the lower one lit
                g.S += 0.05 * w
        if m == "gold" and min(fw, fh) >= 1.5 * D:
            # an engraved line round the face, the rim outside it burnished
            ins = max(3.0, min(5.0, 0.3 * min(fw, fh)))
            lp = _line_v(np, g.e - ins, 1.15)
            g.A *= 1.0 - 0.30 * lp
            g.H -= 0.07 * lp
            g.S -= 0.12 * lp
            rim = g.e < ins - 1.0
            g.A = np.where(rim, g.A * 1.03, g.A)
            g.S += np.where(rim, 0.06, 0.0)
        if m == "plate" and self.kind == "side" and min(fw, fh) >= 2.2 * D and fw >= 3.0 * D:
            ins = 0.55 * D
            ys = [fh / 2.0] if (fh < 4.0 * D or self.open_j) else [ins, fh - ins]
            xs = [] if self.open_i else [ins, fw - ins]
            R = 0.27 * D
            for ry in ys:
                for rx in xs:
                    dx, dy = x - rx, y - ry
                    rr = np.hypot(dx, dy) / R
                    head = rr < 1.0
                    ring = (rr >= 1.0) & (rr < 1.5)
                    # a domed head in a little countersunk ring, lit from the upper left, a glint on it
                    glint = np.exp(-((dx + 0.35 * R) ** 2 + (dy + 0.4 * R) ** 2) / (0.3 * R) ** 2)
                    g.H = np.where(head, g.H - 0.08 * rr * rr, np.where(ring, g.H - 0.08 * (1.5 - rr) / 0.5, g.H))
                    g.A = np.where(head, g.A * (1.04 - 0.22 * dy / R + 0.30 * glint),
                                   np.where(ring, g.A * (0.80 + 0.14 * (rr - 1.0) / 0.5), g.A))
                    g.S += np.where(head, 0.12, 0.0)
        # scratches: short, mostly along the brushing; a groove (height), bright bare metal (albedo), rough (specular)
        area = fw * fh / (D * D)
        per = {"plate": 3.0, "blade": 5.0, "gold": 9.0}[m]
        cap = {"plate": 14, "blade": 8, "gold": 5}[m]
        n = min(cap, int(area / per + _hash(s, 7) * 1.5))
        base = math.pi / 2 if self.along_j else 0.0
        for k in range(n):
            x0, y0 = _hash(s, k, 1) * fw, _hash(s, k, 2) * fh
            ang = base + (_hash(s, k, 3) - 0.5) * 0.9 if _hash(s, k, 5) < 0.7 else _hash(s, k, 3) * math.pi
            ln = D * (0.25 + 0.9 * _hash(s, k, 4))
            seg = (x0, y0, x0 + math.cos(ang) * ln, y0 + math.sin(ang) * ln)
            w = np.clip(1.0 - _seg_dist_v(np, x, y, seg) / 0.75, 0.0, 1.0) * (0.6 + 0.4 * _hash(s, k, 6))
            g.A *= 1.0 + 0.22 * w
            g.H -= 0.05 * w
            g.S -= 0.25 * w
        # colour: gold warm in its highlights and deep, browner in its recesses; steel a touch cooler in the light and
        # warmer where it is dull. Hue only - each shift keeps the texel's luminance (within 1%): the brightness is A's
        t = np.clip((g.A - 1.0) / 0.15, -1.0, 1.0)
        hi, lo = ((1.03, 0.995, 0.86), (1.08, 0.985, 0.76)) if m == "gold" else ((0.985, 1.0, 1.025), (1.012, 1.0, 0.975))
        self._tint(g, np.maximum(t, 0.0), hi)
        self._tint(g, np.maximum(-t, 0.0), lo)

    def _mail(self, g):
        np, D = g.np, g.D
        k = D / 8.0
        cal = _mail_cal.get(self.D)
        if cal is None:
            ii, jj = np.mgrid[0:48, 0:48].astype(np.float64)
            b, _h, _s = _mail_rings(np, ii, jj, k)
            cal = _mail_cal[self.D] = _MAIL_MEAN / float(b.mean())
        b, h, sm = _mail_rings(np, g.ii, g.jj, k)
        g.A *= b * cal
        g.H = h
        g.S += sm

    def _cloth(self, g):
        np, D, s, st = g.np, g.D, self.seed, self.style
        x, y, u, v = g.x, g.y, g.u, g.v
        fw, fh = self.fw, self.fh
        side = self.kind == "side"
        H = np.full(x.shape, 0.55)
        A = g.A
        if self.m == "string":
            # a bowstring: each length of it a little lighter or darker (waxed, frayed, in the light or not), twisted
            fold = np.sin(u / (D * 2.2) * math.pi * 2 + _hash(s, 3) * 6.28)
            A *= (1.0 + 0.15 * fold + 0.025 * _value_v(np, s, x, y, 0.5 * D) + 0.06 * _value_v(np, s + 3, x, y, 2.0 * D)) \
                * (1.0 + 0.03 * (1.0 - 2.0 * np.abs(((v + u * 1.5) % 3.0) / 3.0 - 0.5)) - 0.015)
            g.H = H + 0.20 * fold
            return
        # the weave, in the albedo only: warp threads along the hang, thicker slubs here and there, the weft across
        warp = _value_v(np, s + 20, u, v * 0.10, 1.0)
        slub = np.maximum(0.0, _value_v(np, s + 21, u * 0.8, v * 0.045, 1.0)) ** 2
        weft = np.cos(v * math.pi)
        A *= 1.0 + 0.06 * warp + 0.09 * slub + 0.025 * weft - 0.015
        if st == "twill":
            tri = 1.0 - 2.0 * np.abs(((u + v) % 4.0) / 4.0 - 0.5)      # the twill's diagonal wale
            A *= 1.0 + 0.05 * (tri - 0.5)
        elif st == "knit" and g.wu >= 0.9 * D:
            A *= 1.0 + 0.04 * np.cos(u / 4.0 * 2 * math.pi)           # ribbed hose, ~2 ribs a model pixel
            ph = ((v + 1.2 * np.abs((u % 4.0) - 2.0)) % 2.5) / 2.5    # and the knit's V in each rib
            A *= 1.0 + 0.03 * (1.0 - 2.0 * np.abs(ph - 0.5) - 0.5)
        elif st == "cord":
            ph = ((v + u) % 4.0) / 4.0
            A *= 1.0 + 0.22 * (1.0 - 2.0 * np.abs(ph - 0.5)) - 0.11   # twisted strands, each lit along its crown
        drape = side and g.wv >= 1.2 * D and g.wu >= 1.6 * D and st != "cord"
        if not drape and st != "cord":
            # a narrow strip of cloth (a sleeve's side, a strap) or a face lying flat (a shoulder) is ONE fold: lit or
            # in shade as a whole, rolling across it (the Oct 16 cloth's fold, kept where no drape fits)
            stiff = st == "quilt" and g.wu >= 0.8 * D and g.wv >= 1.2 * D
            f1 = np.sin(u / (D * 2.2) * math.pi * 2 + _hash(s, 3) * 6.28)
            A *= 1.0 + (0.05 if stiff else 0.15) * f1
            H += (0.06 if stiff else 0.20) * f1
        # folds: hanging cloth on a side face. They gather at the top (under a belt, at a shoulder) and fan out down
        # the face; ridges broad, creases narrow and dark
        if drape:
            fz, fa = {"quilt": (0.05, 0.10), "knit": (0.06, 0.10), "linen": (0.22, 0.42)}.get(st, (0.26, 0.85))
            gather = np.exp(-v / (1.2 * D)) if not self.open_j else np.zeros(x.shape)
            uc = g.wu * 0.5
            ug = uc + (u - uc) * (1.0 + 0.4 * gather)
            mea = 0.35 * D * _value_v(np, s + 30, 0.0, v, 2.5 * D)
            nz = 0.75 * _value_v(np, s + 31, ug + mea, 0.0, 1.7 * D) + 0.25 * _value_v(np, s + 32, ug + 0.5 * mea, 7.0, 0.7 * D)
            # the drape's height across the face - every panel folded as deeply (noise alone left some flat)
            F = np.clip((nz - nz.mean()) / max(0.15, float(nz.std())) * 0.6, -1.3, 1.3)
            # each fold lit on the side toward the light (upper left) and in shade on the other, the hollows between
            # them darkest - shaded like drapery is painted, not drawn as lines (symmetric dark lines read as cracks)
            dF = np.gradient(F, axis=1 if self.along_j else 0) if min(F.shape) > 1 else np.zeros(F.shape)
            slope = np.clip(dF * 0.6 * D, -1.0, 1.0)
            amp = 0.8 + 0.25 * gather
            H += fz * amp * F
            hollow = np.clip((-0.55 - F) / 0.6, 0.0, 1.0)
            fold = 1.0 + fa * amp * (0.30 * slope + 0.22 * F - 0.30 * hollow)
            A *= fold / max(1e-6, float(fold.mean()))                 # folds move light about; the cloth keeps its tone
        if side and g.wv >= 1.2 * D and st not in ("cord", "quilt"):      # a padded jack is stiff: it never bunches
            if not self.open_j and fh >= 1.5 * D:
                # the cloth tucked under what holds it: darker and bunched just under the top
                top = np.exp(-y / (0.45 * D))
                zone = np.exp(-y / (0.8 * D))
                bn = _value_v(np, s + 40, u * 0.25, y, 1.6)
                rl = np.clip(1.0 - np.abs(bn) / 0.22, 0.0, 1.0) ** 2 * zone
                A *= (1.0 - 0.06 * top) * (1.0 - 0.10 * rl)
                H -= 0.05 * top + 0.04 * rl
            if not self.open_j and fh >= 2.0 * D and (st == "knit" or g.wu < 2.5 * D):
                # a sleeve or a leg: the cloth bunches where the limb bends - at the elbow and knee ends of the arm and
                # leg pieces (the anatomy under the cloth, lovish Oct 16), hose at the ankle
                zone = np.exp(-(fh - y) / (0.8 * D))
                bn = _value_v(np, s + 41, u * 0.25, y, 1.4)
                rl = np.clip(1.0 - np.abs(bn) / 0.25, 0.0, 1.0) ** 2 * zone
                A *= 1.0 - 0.14 * rl
                H -= 0.05 * rl
        if st == "quilt" and g.wu >= 0.8 * D and g.wv >= 1.2 * D:
            # a padded jack: the stitching pulls the cloth down in lines, the padding puffs up between them; each
            # channel its own fullness, the lines not ruler-straight
            P = g.wu / max(1, int(round(g.wu / (1.1 * D))))
            nch = int(round(g.wu / P))
            uq = u + 0.35 * _value_v(np, s + 33, 0.0, v, 1.5 * D)
            q = (uq % P) / P
            puff = np.sin(math.pi * q) ** 0.7
            ch = np.floor(uq / P)
            full = 1.0 + 0.04 * (_hash_v(np, s, ch, 34) - 0.5)
            H += 0.20 * puff - 0.10
            A *= (0.84 + 0.21 * puff) * full
            kk = np.round(uq / P)
            line = (kk > 0) & (kk < nch) & (np.abs(uq - kk * P) < 0.5)
            dash = (v % 4.0) < 2.5
            A = np.where(line, A * np.where(dash, 0.74, 0.88), A)
        if st == "twill" and self.panel and fw >= 3.0 * D and fh >= 3.0 * D:
            # a woven border round a panel, stitched along its inner line
            b0, b1 = round(0.30 * D), round(0.80 * D)
            e = g.e
            band = _smooth_v(np, e - b0 + 0.5) * _smooth_v(np, b1 - e + 0.5)
            edge = np.maximum(_line_v(np, e - b0, 1.0), _line_v(np, e - b1, 1.0))
            along = np.where(np.minimum(x, fw - x) < np.minimum(y, fh - y), y, x)
            chev = ((along + np.abs(e - (b0 + b1) * 0.5) * 1.6) % 6.0) / 6.0
            near = e < b1 + 1.5
            A = np.where(near, A * (1.0 + 0.14 * band) * (1.0 - 0.32 * edge) * (1.0 - 0.10 * band * (chev < 0.4)), A)
            H += np.where(near, 0.05 * band, 0.0)
            stitch = (np.abs(e - b1 - 1.4) < 0.55) & ((along % 4.0) < 2.4)
            A = np.where(stitch, A * 0.84, A)
        elif side and not self.open_j and fh >= 2.5 * D and g.wu >= 2.5 * D and st in ("linen", "twill", "quilt"):
            # the hem of a skirt, a robe, a tabard: the cloth turned up and stitched, its fold rounding under at the
            # bottom edge (a limb's piece bunches at the joint instead)
            hb = 0.45 * D
            yb = fh - y
            band = yb < hb - 0.5
            A = np.where(band, A * 0.97, A)
            H += np.where(band, 0.04, 0.0)
            A = np.where(yb < 1.0, A * 0.90, A)
            sl = _line_v(np, yb - hb, 0.9)
            A *= 1.0 - 0.10 * sl
            H -= 0.05 * sl
            A = np.where((np.abs(yb - hb) < 0.6) & ((x % 4.0) < 2.4), A * 0.82, A)
        # uneven dye and wear: a broad drift (lighter where faded, darker where it soaked more dye or dirt) and streaks
        # along the warp, a little warmer or cooler
        dn = 0.7 * _value_v(np, s + 50, x, y, 2.5 * D) + 0.3 * _value_v(np, s + 51, u * 0.5, v * 0.06, 1.0)
        g.A = A * (1.0 + 0.06 * _value_v(np, s + 3, x, y, 2.0 * D))      # (the same drift the Oct 16 cloth had)
        g.H = H * (0.85 + 0.15 * np.clip(g.e / self.bevel, 0.0, 1.0))     # cloth edges are soft, not bevelled
        g.T = 1.0 + 0.035 * dn[..., None] * np.array([1.0, 0.2, -0.8])

    def _leather(self, g):
        np, D, s, st = g.np, g.D, self.seed, self.style
        x, y, u, v, e = g.x, g.y, g.u, g.v, g.e
        fw, fh = self.fw, self.fh
        # tone: oiled darker and shinier in patches
        blotch = _value_v(np, s + 2, x, y, 1.5 * D)
        oil = np.maximum(0.0, blotch)
        g.A *= 1.0 + 0.12 * blotch - 0.08 * oil
        g.S += 0.14 * oil
        # the grain: a fine network of crinkles, in the albedo and the specular only
        cn = _value_v(np, s + 60, x, y, 1.7)
        crk = np.clip(1.0 - np.abs(cn) / 0.14, 0.0, 1.0)
        g.A *= 1.0 - 0.12 * crk
        g.S -= 0.05 * crk
        # creases across the long axis where it bends (more toward its ends): dark in the fold, its lip lighter
        if g.wv >= 1.5 * D and g.wu >= 0.6 * D:
            nc = min(6, int(g.wv / (1.2 * D) + _hash(s, 21) + 0.5))
            for k in range(nc):
                side_k = 1.0 if _hash(s, k, 25) < 0.5 else -1.0
                cv = g.wv * (0.5 + side_k * 0.44 * _hash(s, k, 22) ** 0.6)
                tilt = (_hash(s, k, 23) - 0.5) * 0.35
                bow = (_hash(s, k, 26) - 0.5) * 0.9 * D
                cu = g.wu * (0.25 + 0.5 * _hash(s, k, 27))
                L = g.wu * (0.45 + 0.5 * _hash(s, k, 28))
                stg = 0.55 + 0.45 * _hash(s, k, 24)
                uu = (u - cu) / max(1.0, g.wu)
                d = v - (cv + tilt * (u - cu) + bow * uu * uu)
                f = np.clip(1.0 - ((u - cu) / L) ** 2, 0.0, 1.0)
                cr = _line_v(np, d, 1.4) * stg * f
                lip = _line_v(np, d - 1.9, 1.0) * stg * f
                g.A *= (1.0 - 0.42 * cr) * (1.0 + 0.16 * lip)
                g.H += -0.07 * cr + 0.025 * lip
                g.S -= 0.06 * cr
        # scuffed edges: rubbed lighter and burnished, more in places
        wn = 0.4 + 0.6 * np.clip(0.5 + 0.7 * _value_v(np, s + 61, x + y, 0.0, 0.9 * D), 0.0, 1.0)
        sw = max(1.0, min(2.2, 0.22 * min(fw, fh)))     # a finger's narrow face is not all edge
        sc = np.clip(1.0 - e / sw, 0.0, 1.0) * wn
        g.A *= 1.0 + 0.36 * sc
        g.S += 0.08 * sc
        if st == "boot" and self.kind == "side" and self.face:
            # toe and heel: scuffed lighter and matte, from the ground up
            front = {"west": fw - x, "east": x, "north": np.zeros(x.shape), "south": np.full(x.shape, 1e9)}[self.face]
            back = {"west": x, "east": fw - x, "north": np.full(x.shape, 1e9), "south": np.zeros(x.shape)}[self.face]
            endw = np.exp(-np.minimum(front, back) / (0.9 * D))
            low = np.clip((y / max(1.0, fh) - 0.40) / 0.60, 0.0, 1.0)
            wear = endw * low * (0.6 + 0.4 * _value_v(np, s + 62, x, y, 0.6 * D))
            g.A *= 1.0 + 0.45 * wear
            g.S -= 0.16 * wear
        # stitched seams where two panels meet: along the long edges of a strap, a bracer, a boot's shaft (round every
        # border of every face it read as a postage stamp - the Oct 16 lab)
        long_x = self.along_j
        seam_x = not self.open_i and long_x and fw >= 1.2 * D and fh >= 1.5 * D
        seam_y = not self.open_j and not long_x and fh >= 1.2 * D and fw >= 1.5 * D
        if seam_x or seam_y:
            big = np.full(x.shape, 1e9)
            ex = np.minimum(x, fw - x) if seam_x else big
            ey = np.minimum(y, fh - y) if seam_y else big
            ds = np.minimum(ex, ey) - 0.32 * D
            gr = _line_v(np, ds, 1.3)
            g.A *= 1.0 - 0.16 * gr                      # the seam pressed into the leather
            g.H -= 0.05 * gr
            along = np.where(ex < ey, y, x)
            thread = (np.abs(ds) < 0.55) & ((along % 3.0) < 1.8)
            g.A = np.where(thread, g.A * 1.16, g.A)     # the waxed linen thread
            g.S += np.where(thread, -0.1, 0.0)
            self._tint(g, thread.astype(np.float64) * 0.5, (1.03, 1.0, 0.88))

    def _wood(self, g):
        np, D, s = g.np, g.D, self.seed
        x, y, u, v = g.x, g.y, g.u, g.v
        A, S = g.A, g.S
        H = g.H
        P = 0.38 * D                                        # a growth ring every ~3 texels
        if self.end:
            # the end of a long piece: its rings round a pith off to one side, a check or two running out
            cx = self.fw * (0.15 + 0.7 * _hash(s, 81)) + (self.fw * 0.6 if _hash(s, 82) < 0.5 else -self.fw * 0.6)
            cy = self.fh * (0.2 + 0.6 * _hash(s, 83))
            r = np.hypot(x - cx, y - cy)
            r = r + 0.6 * _value_v(np, s + 84, x, y, 2.0)
            f = (r / P) % 1.0
            ln = _line_v(np, np.minimum(f, 1.0 - f) * P, 0.9)
            A *= 0.86 * (1.0 - 0.18 * ln) * (1.0 + 0.05 * _value_v(np, s + 85, x, y, 1.5))
            H += -0.03 * ln
            S -= 0.06
            ang = np.arctan2(y - cy, x - cx)
            for k in range(2):
                a0 = _hash(s, k, 86) * 2 * math.pi
                da = np.abs(((ang - a0 + math.pi) % (2 * math.pi)) - math.pi) * r
                ck = _line_v(np, da, 0.7) * (r > 1.5)
                A *= 1.0 - 0.25 * ck
                H -= 0.04 * ck
            g.A, g.H, g.S = A, H, S
            return
        gg = u.copy()
        if g.wu >= 3.5 * D and g.wv >= 3.5 * D:                 # boards: a pavise, a shield
            Pp = g.wu / max(2, int(round(g.wu / (1.7 * D))))
            nb = int(round(g.wu / Pp))
            kb = np.minimum(np.floor(u / Pp), nb - 1)
            gg = gg + 13.7 * kb                                   # each board its own grain and tone
            A *= 1.0 + 0.10 * (_hash_v(np, s, kb, 41) - 0.5)
            kk = np.round(u / Pp)
            joint = (kk > 0) & (kk < nb)
            sd = np.abs(u - kk * Pp)
            A *= np.where(joint, 1.0 - 0.38 * _line_v(np, sd, 1.3), 1.0)
            H -= np.where(joint, 0.12 * _line_v(np, sd, 1.8), 0.0)
        # the grain: long, gently wavy lines along the piece, rings of uneven width
        gg = gg + 0.45 * D * _value_v(np, s, u * 0.12, v, 3.0 * D)
        core = np.full(x.shape, 9.0)
        if g.wu >= 0.85 * D and g.wv >= 3.0 * D:
            nk = int(g.wu * g.wv / (D * D) / 24.0 + _hash(s, 11) * 1.3)
            for k in range(min(nk, 3)):
                ku, kv, kr = (g.wu * (0.22 + 0.56 * _hash(s, k, 12)), g.wv * (0.10 + 0.80 * _hash(s, k, 13)),
                              D * (0.20 + 0.12 * _hash(s, k, 14)))
                du, dv = u - ku, (v - kv) / 2.4
                r2 = (du * du + dv * dv) / (kr * kr)
                gg = gg + np.where(r2 < 16.0, kr * 1.4 * np.exp(-r2 * 0.30) * np.where(du >= 0, 1.0, -1.0), 0.0)
                core = np.minimum(core, np.where(r2 < 2.1, np.sqrt(r2), 9.0))
        band = np.floor(gg / P)
        f = (gg / P) % 1.0
        ln = _line_v(np, np.minimum(f, 1.0 - f) * P, 0.9) * (0.5 + 0.9 * _hash_v(np, s, band, 31))
        pores = _value_v(np, s + 90, u, v * 0.08, 1.0)          # open pores in streaks along the grain
        A *= (1.0 + 0.05 - 0.10 * f - 0.22 * ln) * (1.0 + 0.07 * pores) * (1.0 + 0.06 * _value_v(np, s + 1, u * 0.3, v, 2.5 * D))
        H += 0.02 * _value_v(np, s + 2, u * 0.3, v, 2.0 * D) - 0.035 * ln
        S -= 0.05 * ln
        kc = core < 1.0
        A = np.where(kc, A * (0.60 + 0.22 * core), np.where(core < 1.45, A * (0.84 + 0.16 * (core - 1.0) / 0.45), A))
        H += np.where(kc, 0.05 * (1.0 - core * core), 0.0)
        S += np.where(kc, 0.08, 0.0)
        # handled and knocked edges a little lighter
        A *= np.where(g.e < 1.0, 1.06, 1.0)
        g.A, g.H, g.S = A, H - 0.02, S

    def _skin(self, g):
        np, D, s = g.np, g.D, self.seed
        x, y, e = g.x, g.y, g.e
        # the form rolls off toward each edge (a cube head is a round one, simplified) and reddens there - the light
        # that travels under the skin; fine mottling; warm and cool patches; an oily sheen in places
        roll = np.clip(e / max(1.0, min(0.5 * D, 0.3 * min(self.fw, self.fh))), 0.0, 1.0)
        roll = roll * roll * (3 - 2 * roll)
        mott = 0.02 * _value_v(np, s + 70, x, y, 1.3) + 0.035 * _value_v(np, s + 71, x, y, 0.8 * D)
        g.A *= (0.91 + 0.09 * roll) * (1.0 + mott)
        g.H += 0.02 * _value_v(np, s + 71, x, y, 0.8 * D)
        g.S += 0.08 * _value_v(np, s + 73, x, y, 1.0 * D)
        wc = np.clip(_value_v(np, s + 72, x, y, 1.0 * D) / 0.6, -1.0, 1.0)
        self._tint(g, np.maximum(wc, 0.0), (1.06, 0.98, 0.94))
        self._tint(g, np.maximum(-wc, 0.0) * 0.8, (0.96, 1.0, 1.035))
        self._tint(g, (1.0 - roll) * 0.7, (1.05, 0.97, 0.95))

    def _hair(self, g):
        np, s = g.np, self.seed
        x, y = g.x, g.y
        # strands down the face, clumped, the tips a little lighter
        streak = _value_v(np, s + 80, x, y * 0.12, 1.0)
        clump = _value_v(np, s + 81, x * 0.35, y * 0.08, 1.0)
        g.A *= (1.0 + 0.10 * streak + 0.08 * clump) * (1.0 + 0.06 * (y / max(1.0, self.fh) - 0.5))
        g.H += 0.05 * clump
        g.S += 0.10 * streak

    def _fur(self, g):
        """A beast's coat (the Mossback Bison's cape): CLUMPS, not lines. Staggered rows of short tufts down the hang
        of every face, each a soft-edged blob two to four texels long, tapered and curled at its tip, in four values,
        overlapping like shingles with a dark gap under each row; the rows wobble so no straight line survives; low
        contrast, warmed toward tawny. (The first version drew long streaks: it read as wood grain.)"""
        np, s, D = g.np, self.seed, g.D
        u, v = g.x, g.y                                    # the hang is down the face
        pitch = max(3.0, D * 0.5)                          # a row of tufts every half model pixel
        tw = max(2.0, D * 0.42)                            # a tuft ~3 texels wide
        wob = _value_v(np, s + 90, u * 0.35, v * 0.35, max(2.0, D * 0.6))
        rowf = (v + 0.9 * wob * pitch) / pitch
        r = np.floor(rowf)
        t = rowf - r                                       # 0 at the tuft's root .. 1 at its tip
        colf = (u + (np.mod(r, 2) * 0.5 + 0.35 * wob) * tw) / tw
        c = np.floor(colf)
        du = colf - c - 0.5                                # -0.5 .. 0.5 across the tuft
        hv = _hash_v(np, s + 91, r, c)
        curl = (hv - 0.5) * 0.5 * t * t                    # the tip curls one way or the other
        taper = 1.0 - 0.45 * t
        blob = np.clip(1.0 - np.abs(du + curl) * 2.0 / np.maximum(0.25, taper), 0.0, 1.0)
        blob = blob * blob * (3 - 2 * blob)                # soft edges
        k = self.p["alb"]                                  # the pale cape carries more contrast (furl)
        level = 1.0 + (0.21 * np.floor(hv * 4.0) / 3.0 - 0.105) * k     # four values per tuft
        gap = np.clip((t - 0.78) / 0.22, 0.0, 1.0)         # the dark line under each row
        g.A *= level * (1.0 + (0.09 * blob - 0.045) * k) * (1.0 - 0.10 * k * gap) * (1.0 - 0.05 * (1.0 - blob))
        g.A *= 1.0 + 0.06 * (0.5 - v / max(1.0, float(self.fh)))      # a little lighter along the top
        g.H = 0.40 + 0.35 * blob * (1.0 - 0.5 * t) + 0.10 * (level - 1.0) / 0.21
        g.S += 0.04 * blob
        self._tint(g, 0.55 * np.ones_like(blob), (1.05, 0.97, 0.80))          # tawny, away from bare wood
        self._tint(g, 0.20 * (1.0 - blob), (0.90, 0.86, 0.82))               # the cooler undercoat between tufts

    def _moss(self, g):
        """Living moss with FORM: lumpy blobs three to five texels across on a jittered grid, each with a bright
        yellow-green top, deep green sides, a highlight texel near its crown, dark gaps between them, and a few tufts
        standing up - a cushion, not a camouflage print."""
        np, s, D = g.np, self.seed, g.D
        x, y = g.x, g.y
        cell = max(3.0, D * 0.68)
        wob = _value_v(np, s + 95, x * 0.4, y * 0.4, max(2.0, D * 0.9))
        cx = np.floor((x + 0.4 * wob * cell) / cell)
        cy = np.floor((y + 0.4 * wob * cell) / cell)
        jx = (_hash_v(np, s + 96, cx, cy) - 0.5) * 0.7
        jy = (_hash_v(np, s + 97, cx, cy) - 0.5) * 0.7
        dx = (x + 0.4 * wob * cell) / cell - cx - 0.5 - jx
        dy = (y + 0.4 * wob * cell) / cell - cy - 0.5 - jy
        rad = 0.50 + 0.26 * _hash_v(np, s + 98, cx, cy)
        d = np.sqrt(dx * dx + dy * dy) / rad
        blob = np.clip(1.0 - d, 0.0, 1.0)
        dome = np.sqrt(np.clip(1.0 - d * d, 0.0, 1.0))   # the blob's height
        top = np.clip(1.0 - (dy / rad + 0.6) / 0.9, 0.0, 1.0) * blob          # the crown faces up (toward -y)
        hi = np.clip(1.0 - np.sqrt((dx / rad + 0.25) ** 2 + (dy / rad + 0.35) ** 2) / 0.22, 0.0, 1.0)   # the highlight
        gapv = (blob <= 0.0).astype(np.float64)
        tuft = (_hash_v(np, s + 99, np.floor(x), np.floor(y)) > 0.93).astype(np.float64) * blob
        g.A *= (0.60 + 0.36 * dome + 0.22 * top) * (1.0 - 0.22 * gapv) * (1.0 + 0.35 * hi) * (1.0 + 0.20 * tuft)
        g.H = 0.18 + 0.62 * dome + 0.20 * tuft
        g.S += -0.05 + 0.05 * hi
        self._tint(g, 0.55 * top, (1.10, 1.16, 0.72))                         # the yellow-green crowns
        self._tint(g, 0.45 * blob * (1.0 - top), (0.78, 0.92, 0.66))         # deep green sides
        self._tint(g, 0.55 * gapv, (0.58, 0.66, 0.46))                        # the dark gaps
        self._tint(g, 0.30 * hi, (1.15, 1.18, 0.95))

    def _feather(self, g):
        np, D = g.np, g.D
        u, v, y = g.u, g.v, g.y
        mid = (self.fw if self.along_j else self.fh) / 2.0
        shaft = np.maximum(0.0, 1.0 - np.abs(u - mid) / max(0.8, D * 0.12))   # the rachis down the middle
        barb = np.sin((v + np.abs(u - mid) * 0.9) / max(1.5, D * 0.22) * math.pi * 2)
        # a wing reads by the shade each row of feathers throws on the next: dark where the row above overlaps (the
        # face's top), the vane lighter toward its free tip, the barbs and shaft stronger
        overlap = np.minimum(1.0, y / max(1.0, D * 0.7))
        g.A *= (0.74 + 0.26 * overlap) * (1.0 + 0.08 * barb + 0.09 * shaft) * (0.97 + 0.06 * y / max(1, self.fh))
        g.H = 0.55 + 0.15 * barb + 0.25 * shaft

    def _stone(self, g):
        """Carved marble: a soft cloud in the stone, a few thin grey veins wandering across the face, the arrises a
        little worn (the bevel the base height already gives), a faint tooled grain - a statue read at 10 blocks, not
        a polished counter (the White Angel capitol's statue, lovish Oct 20)."""
        np, D, s = g.np, g.D, self.seed
        x, y = g.x, g.y
        cloud = _value_v(np, s + 90, x, y, 2.5 * D)
        wob = 1.6 * D * _value_v(np, s + 91, x, y, 1.5 * D)
        vein = np.maximum(0.0, 1.0 - np.abs(np.sin((x * 0.55 + y * 0.8 + wob) / (2.2 * D) * math.pi)) / 0.06)
        grain = _value_v(np, s + 92, x * 0.25, y, 0.35 * D)
        g.A *= (1.0 + 0.035 * cloud) * (1.0 - 0.10 * vein) * (1.0 + 0.012 * grain)
        g.H += 0.03 * cloud - 0.04 * vein + 0.01 * grain
        g.S += 0.06 * cloud

    # ---- the pre-pass surfaces (REALISM = False): comparison renders ----
    def _legacy_at(self, i, j):
        m, D, s = self.m, self.D, self.seed
        if m in ("glass", "emissive"):
            return 1.0, 1.0, 0.0
        x, y = i + 0.5, j + 0.5
        e = min(1e9 if self.open_i else min(x, self.fw - x), 1e9 if self.open_j else min(y, self.fh - y))
        bev = min(1.0, e / self.bevel)
        h = 0.62 + 0.38 * math.sqrt(bev)
        a, sm = 1.0, 0.0
        u, v = (x, y) if self.along_j else (y, x)
        if m in ("plate", "blade", "gold"):
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
            a *= 1.0 + 0.030 * (weave - 0.5) * self.p["alb"] + 0.040 * fold + 0.015 * nz
            h = 0.55 + 0.12 * weave + 0.18 * fold + 0.05 * nz
            if bev < 1.0:
                h *= 0.85 + 0.15 * bev                         # cloth edges are soft, not bevelled
        elif m == "leather":
            grain = _value(s, x, y, 1.6)
            blotch = _value(s + 2, x, y, D * 1.5)
            a *= 1.0 + 0.035 * grain + 0.055 * blotch
            h += 0.06 * grain
            sm += 0.12 * blotch
            # stitching: a dotted line one bevel in from each edge
            if abs(e - self.bevel * 1.6) < 0.5 and int(x + y) % 3 == 0:
                a *= 0.84
                h -= 0.12
        elif m == "wood":
            ring = math.sin((u + 1.5 * _value(s, u, v * 0.15, 4.0)) / max(1.2, D * 0.3) * math.pi * 2)
            a *= 1.0 + 0.060 * ring + 0.03 * _value(s + 1, x, y, 2.0)
            h = 0.6 + 0.14 * ring
        elif m == "feather":
            mid = (self.fw if self.along_j else self.fh) / 2.0
            shaft = max(0.0, 1.0 - abs(u - mid) / max(0.8, D * 0.12))
            barb = math.sin((v + abs(u - mid) * 0.9) / max(1.5, D * 0.22) * math.pi * 2)
            a *= 1.0 + 0.05 * barb + 0.06 * shaft
            h = 0.55 + 0.15 * barb + 0.25 * shaft
        else:
            a *= 1.0 + 0.02 * _value(s, x, y, 2.0)
        relief = self.p["relief"]
        h = 0.5 + (h - 0.5) * relief
        return a, max(0.0, min(1.0, h)), sm


class _Grid:
    """The arrays one face is painted with (Face._paint)."""


def _mail_rings(np, ii, jj, k):
    """Mail as the close camera can hold it: rows of rings, each a lit torus a little over half a model pixel across,
    alternate rows leaning opposite ways (4-in-1), the lower row lying over the upper one, the padding dark through
    the holes. 2 x 2 samples a texel. -> (brightness, height, smoothness offset) arrays."""
    cw, rh, R, Ry = _MAIL["cw"] * k, _MAIL["rh"] * k, _MAIL["rx"] * k, _MAIL["ry"] * k
    q0, qw, ln = _MAIL["q0"], _MAIL["qw"], _MAIL["lean"]
    L = np.array(_MAIL_LIGHT) / np.linalg.norm(_MAIL_LIGHT)
    acc_b, acc_h, acc_s = np.zeros(ii.shape), np.zeros(ii.shape), np.zeros(ii.shape)
    for ox, oy in ((0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)):
        px, py = ii + ox, jj + oy
        row = np.floor(py / rh)
        best = np.full(ii.shape, -1e9)
        b = np.full(ii.shape, _MAIL["hole"])               # the hole: the dark padding under the mail
        h = np.full(ii.shape, 0.40)
        sm = np.full(ii.shape, -0.25)
        for dr in (-1, 0, 1):
            r = row + dr
            cy = r * rh + rh / 2.0
            off = (cw / 2.0) * (r % 2)
            col = np.floor((px - off) / cw)
            lean = np.where(r % 2 == 0, ln, -ln)
            for dc in (-1, 0, 1):
                c = col + dc
                cx = c * cw + off + cw / 2.0
                dx, dy = px - cx, py - cy
                lx = dx - lean * dy
                q = np.hypot(lx / R, dy / Ry)
                t = (q - q0) / qw                          # -1 inside edge .. 1 outside edge of the wire
                on = np.abs(t) < 1.0
                layer = r + 0.5 * ((c + r) % 2)
                take = on & (layer > best)
                rad = np.hypot(lx, dy) + 1e-6
                nz = np.sqrt(np.clip(1.0 - t * t, 0.0, 1.0))
                nx, ny = lx / rad * t, dy / rad * t
                lam = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0.0, 1.0)
                bright = 0.30 + 0.60 * lam + 0.60 * lam ** 12
                # the part of a ring that runs under the next row sits in its shadow
                shade = 1.0 - 0.30 * np.clip((dy / Ry - 0.15) / 0.6, 0.0, 1.0)
                best = np.where(take, layer, best)
                b = np.where(take, bright * shade, b)
                h = np.where(take, 0.44 + 0.12 * nz + 0.02 * ((c + r) % 2), h)
                sm = np.where(take, -0.10 + 0.25 * lam, sm)
        acc_b += b
        acc_h += h
        acc_s += sm
    return acc_b / 4.0, acc_h / 4.0, acc_s / 4.0


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
