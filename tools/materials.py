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


def material_for(colour_name):
    n = colour_name.lower()
    for frag, mat in _BY_NAME:
        if frag in n:
            return mat
    return "plain"


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
    along: along j for side faces, along the longer edge for tops."""

    def __init__(self, material, fw, fh, density, seed, kind="side"):
        self.m, self.fw, self.fh, self.D, self.seed = material, fw, fh, density, seed
        self.p = MATERIALS[material]
        self.along_j = kind == "side" or fh >= fw
        self.scratches = _scratches(seed, fw, fh, density) if material in ("plate", "gold", "blade") else ()
        self.bevel = max(1.0, density * 0.25)            # a rounded edge a quarter of a model pixel wide

    def at(self, i, j):
        m, D, s = self.m, self.D, self.seed
        if m in ("glass", "emissive"):
            return 1.0, 1.0, 0.0
        x, y = i + 0.5, j + 0.5
        # distance to the face's border, in texels: the bevel (rounded plate edges catch the light)
        e = min(x, y, self.fw - x, self.fh - y)
        bev = min(1.0, e / self.bevel)
        h = 0.62 + 0.38 * math.sqrt(bev)
        a, sm = 1.0, 0.0
        u, v = (x, y) if self.along_j else (y, x)        # v runs along the grain / hang; u across it
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
            sm += 0.12 * blotch                              # worn, shinier patches
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
            shaft = max(0.0, 1.0 - abs(u - mid) / max(0.8, D * 0.12))   # the rachis down the middle
            barb = math.sin((v + abs(u - mid) * 0.9) / max(1.5, D * 0.22) * math.pi * 2)
            a *= 1.0 + 0.05 * barb + 0.06 * shaft
            h = 0.55 + 0.15 * barb + 0.25 * shaft
        else:
            a *= 1.0 + 0.02 * _value(s, x, y, 2.0)
        relief = self.p["relief"]
        h = 0.5 + (h - 0.5) * relief
        return a, max(0.0, min(1.0, h)), sm


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
    linear, 230-237 named metals, 238-255 a metal with its albedo as F0), B porosity 0-64 or subsurface 65-255, A
    emission 0-254 (255 = none)."""
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
    a = s[..., 3][ms]
    if a.size and (a < 255).mean() > 0.5:
        probs.append("specular: emission on %.0f%% of texels (255 = none) - is A unset?" % (100 * (a < 255).mean()))
    return probs
