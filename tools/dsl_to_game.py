#!/usr/bin/env python3
"""Concept model -> game model (lovish, Oct 14 18:13: "all the concept art should help with detail, creation and
implementation of things for the game"). What is approved on a concept sheet is what walks in the game: this turns a
design/models DSL file into

  1. a Java model class (vanilla LayerDefinition / ModelPart, the same tree the sheet was drawn from):
       bar/src/main/java/com/solegendary/reignofnether/unit/modelling/models/gen/<Class>.java
     - every DSL bone becomes a ModelPart named after it (so animation code addresses "cape_1", "halo", "arms");
     - a cube with its own rotation becomes a one-cube child part (exactly as render_concept.py draws it);
     - cubes tagged with an upgrade go into a child part "upg_<tag>" per bone, hidden until the unit has it;
     - small detail cubes (thin lines, rivets, engravings; never a glowing one) go into a child part "lod" per bone,
       which the renderer hides when the camera is far (a 400-cube body is fine close up, wasteful as a dot at RTS
       zoom);
  2. a painted texture atlas at DENSITY texels per model pixel (the layer keeps UVs in model pixels, so a 4x texture
     simply draws finer - the same trick as an HD skin): every cube its own box-UV island at the exact float place the
     engine samples, each face shaded like a hand-painted Minecraft texture - lighter at the top, darker at the
     bottom, a lit rim along the upper edge and a dark seam along the lower one, a soft sheen band on polished metal,
     a fine weave on cloth - plus a grey-scale team mask (TeamColourLayer multiplies it by the owner's colour) and an
     emissive mask (drawn additively, full-bright, on top):
       bar/src/main/resources/assets/reignofnether/textures/entities/<texture>.png, _team.png, _glow.png

    python3 tools/dsl_to_game.py design/models/sunforged_paladin_radiant_v2.json --class RadiantPaladinModel \\
        --texture radiant_paladin_unit --team tabard,tabard_dark

Coordinates: the DSL is y-up from the feet with the unit facing -Z and +x its left; a Minecraft entity model is the
same with y flipped and the origin 24 px above the feet (model y = 24 - dsl y). Flipping y conjugates every
rotation: (x, y, z) degrees in the DSL -> (-x, +y, -z) radians in a PartPose, same Z*Y*X order.

Every export is CHECKED before anything is written: the part tree is posed the way Minecraft's ModelPart does it
(translate, then Z*Y*X, children inside parents) and every cube's eight corners must land within 0.01 px of where
the concept renderer puts them (tools/preview_model.posed_cubes) - a wrong sign or pivot fails the export, it does
not ship as a twisted unit.
"""
import argparse
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept  # noqa: E402  (bpy-free when imported outside Blender)
from preview_model import posed_cubes  # noqa: E402
import materials  # noqa: E402
import unit_light  # noqa: E402
import numpy as np  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
JAVA_DIR = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether/unit/modelling/models/gen")
TEX_DIR = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/entities")
PACKAGE = "com.solegendary.reignofnether.unit.modelling.models.gen"

LOD_MAX_VOLUME = 0.6      # px^3: a cube this small is detail (a rivet, an engraved line)
LOD_MAX_THIN = 0.36       # px: or this thin in two axes (a split, a fluting line)
DENSITY = 8               # texels per model pixel in the painted atlas: 8 = 128 per block, the density of a 128x
#                           realistic pack (Patrix) - was 4 until Oct 15 (lovish: "have the units match it")
NORMAL_STRENGTH = 8.0     # height slope -> normal tilt in the LabPBR _n map. Was 3.5: mean tilt 5-7 deg, ~3x flatter
#                           than the realistic world's blocks (Patrix 32x 12-27 deg, pack-study 38710336774), so under a
#                           shader the units read smooth / painted beside the ground. 8 gives 9-13 deg mean, p90 35-40
#                           on the five bodies (inside Patrix's p90 22-48); higher only steepens the rims (our heights
#                           are flat faces with sharp features - mid-frequency relief is the body rebuild's job, T3.13)
RIM_MIN_HEIGHT = 1.9      # px: a cube at least this tall gets a lit rim and a seam (thinner ones are slices of a form)
PANEL_THIN = 0.8          # px: a cube this thin behind a face makes the face a panel (a tabard's: a woven border)
METAL_HINTS = render_concept.METAL_HINTS
CLOTH_HINTS = ("tabard", "cloth", "cape", "sash", "leather", "linen", "wool", "silk")


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def f(v):
    """A Java float literal."""
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


def ident(name):
    out = "".join(c if c.isalnum() else "_" for c in name)
    return out if not out[0].isdigit() else "_" + out


# ------------------------------------------------------------------ texture atlas ----------------------------------
class Atlas:
    """Shelf packer for box-UV islands, in model pixels: an island for a cube of w x h x d (rounded up) is 2(w+d)
    wide and d+h tall."""

    def __init__(self, width):
        self.w, self.x, self.y, self.row_h = width, 0, 0, 0

    def place(self, iw, ih):
        if self.x + iw > self.w:
            self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        u, v = self.x, self.y
        self.x += iw
        self.row_h = max(self.row_h, ih)
        return u, v

    def height(self):
        return self.y + self.row_h


def clamp8(v):
    return max(0, min(255, int(round(v))))


# ---- value grouping for the game's light (claude/art-theory.md section 3; the art director's review, Oct 15) ----
# Minecraft lights an entity face with two fixed lights: a top face ~1.0, a front/back face ~0.74, a left/right face
# ~0.5 of its texel. Painted with the concept palette, cream plate landed at L 124-159 in game - the grass is ~147 -
# so the "white" order read grey and melted into the map. Each palette colour is moved AWAY from the grass band before
# painting: lights lifted toward warm white, darks pushed down, the steel greys lifted to polished; hue is kept.
VALUE_CURVE = ((0, 0), (60, 48), (100, 78), (130, 112), (150, 172), (170, 212), (205, 242), (255, 250))
# the realism pass (tools/materials.py REALISM): the lights stop short of chalk - polished steel is bright where its
# edges and highlights catch the light, not everywhere - the surface detail now carries the separation from the grass
VALUE_CURVE_REAL = ((0, 0), (60, 48), (100, 78), (130, 112), (150, 166), (170, 198), (205, 226), (255, 240))
# polished metal under Minecraft's flat entity light reads as metal by what it REFLECTS: a painted sky over a dark
# horizon over the ground's bounce, on every upright face (the realism pass; strength per material)
REFLECT = {"plate": 1.0, "blade": 1.25, "gold": 0.8}


def reflection(t, horizon, strong=False):
    """Brightness of a polished upright surface at t (0 top - 1 bottom of the bone): the sky down to the horizon, a
    sharp dark band under it, the ground's bounce lightening toward the bottom. strong: the steel palette's deeper
    mirror (real polished plate shows the dark horizon hard; the first pass at 0.76 was lost under the detail)."""
    sky0, sky1, band, ground = (1.14, 1.04, 0.56, 0.30) if strong else (1.10, 1.02, 0.76, 0.16)
    if t < horizon - 0.02:
        return sky0 - (sky0 - sky1) * (t / max(1e-6, horizon))
    if t < horizon + 0.02:                      # the horizon itself: a quick fall over ~4% of the height
        f = (t - (horizon - 0.02)) / 0.04
        return sky1 * (1 - f) + band * f
    return band + ground * (t - horizon) / max(1e-6, 1 - horizon)


# what the steel mirrors, as colour (STEEL_PALETTE): a cool sky above the horizon, the warm ground's bounce below it
SKY_TINT, GROUND_TINT, MIRROR_TINT = (196, 214, 236), (112, 104, 70), 0.22
SKY_MIRROR = 0.45          # how far an upward plate face goes toward the bright sky (times REFLECT, times its sky light)


# the realism pass's palette (STEEL_PALETTE, option C for lovish - Oct 15 23:50): the cream "white steel" read as
# enamel / plastic next to the realistic ground; real plate is a cool, light steel, real gilding a deeper brass
STEEL_PALETTE = True
STEEL, BRASS = (188, 192, 197), (178, 142, 72)


def realistic_colour(name, rgb):
    """The palette colour moved toward its real material when STEEL_PALETTE is on (plate -> steel, gold -> brass)."""
    if not (materials.REALISM and STEEL_PALETTE):
        return rgb
    mat = materials.material_for(name)
    target, t = {"plate": (STEEL, 0.6), "gold": (BRASS, 0.35)}.get(mat, (None, 0))
    if target is None:
        return rgb
    return tuple(int(round(c + (g - c) * t)) for c, g in zip(rgb, target))


# the owner's colour on a generated body is a DYED cloth under the world's light, not a UI swatch: a quarter of the way
# to its own grey, at 0.85 of its light. Mirrored in Java (TeamColourLayer.DYE_GREY / DYE_LIGHT) - the game, the
# previews and the HUD portraits must agree. Outlines, circles and the minimap keep the full colour.
OWNER_DYE = (0.25, 0.85)


def owner_dye(rgb):
    """The owner's colour (floats 0-1) as the game dyes a generated body's team cloth (STEEL_PALETTE only)."""
    if not (materials.REALISM and STEEL_PALETTE):
        return tuple(rgb)
    grey = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
    return tuple((c + (grey - c) * OWNER_DYE[0]) * OWNER_DYE[1] for c in rgb)


# baked sky light + occlusion (tools/unit_light.py; lovish Oct 16 00:28 "improve it for rts pov so it looks realistic"):
# from the RTS camera what reads a small figure as real is light - crevices, overhangs, the legs under the body
BAKED_LIGHT = True

MUD = (96, 80, 58)          # dirt on the boots and the hems: the realism pass, below MUD_HEIGHT DSL px
MUD_HEIGHT = 4.5
TEAM_DYE = (205, 150)       # the team mask's two greys under the realism pass: a dyed cloth, not a neon panel
SATURATED = 0.35   # HSV saturation above which game_value only scales (keeps golds and team-like hues saturated)


def luma(rgb):
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def game_value(rgb):
    """The palette colour with its lightness remapped by VALUE_CURVE: darkened by scaling, lightened by blending toward
    white (both keep the hue; luminance is linear in RGB, so the target lightness is hit exactly)."""
    L = luma(rgb)
    pts = VALUE_CURVE_REAL if materials.REALISM else VALUE_CURVE
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        if L <= x1:
            T = y0 + (y1 - y0) * (L - x0) / max(1e-6, x1 - x0)
            break
    else:
        T = pts[-1][1]
    if T <= L:
        k = T / max(1e-6, L)
        return tuple(clamp8(c * k) for c in rgb)
    # lighter: scale first (keeps a gold gold - blending toward white first would wash it to cream), but only until the
    # brightest channel reaches 255 - scaling past that clamps it while the others keep growing, which turns the hue
    # (the Oct 15 review: sun_gold #f2cb57 drifted 13 degrees toward lemon); whatever lightness is still missing comes
    # from a blend toward white, which keeps the hue exactly
    lo, hi = 1.0, 255.0 / max(1.0, max(rgb))
    for _ in range(40):
        mid = (lo + hi) / 2
        if luma(tuple(min(255.0, c * mid) for c in rgb)) < T:
            lo = mid
        else:
            hi = mid
    scaled = tuple(min(255.0, c * hi) for c in rgb)
    Ls = luma(scaled)
    # a saturated colour stops here: no hue at full saturation reaches the top of the curve (a pure gold peaks near
    # L 191), and the white blend that would get it there washes gold to cream - a gold that reads gold beats the curve
    sat = (max(rgb) - min(rgb)) / max(1.0, max(rgb))
    if Ls >= T - 0.5 or sat > SATURATED:
        return tuple(clamp8(c) for c in scaled)
    t = (T - Ls) / max(1e-6, 255.0 - Ls)
    return tuple(clamp8(c + (255 - c) * t) for c in scaled)


def shade(rgb, k):
    """Brightness k applied the way paint darkens: multiply, and for k > 1 lift toward a warm white."""
    if k <= 1.0:
        return tuple(clamp8(c * k) for c in rgb) + (255,)
    t = min(1.0, (k - 1.0) * 1.6)
    return tuple(clamp8(c + (255 - c) * t * 0.55) for c in rgb) + (255,)


class Painter:
    """Paints islands at DENSITY, at the float places ModelPart.Cube samples (box UV: top at (u+d, v), bottom at
    (u+d+w, v), then the four sides from (u, v+d): -x, front -z, +x, back +z; row 0 of a side is its upper edge)."""

    def __init__(self, img, density, keep_light=False, mask=False):
        self.px, self.D = img.load(), density
        self.mask = mask           # the team mask: grey, multiplied by the owner's colour in game
        self.W, self.H = img.size
        # keep_light: light colours lose less light down the bone (the base paint's plate); the team mask keeps the
        # full gradient so the owner's colour stays shaded, not flat neon
        self.keep_light = keep_light

    def rect(self, a0, b0, a1, b1):
        D = self.D
        x0, y0 = int(math.floor(a0 * D + 1e-6)), int(math.floor(b0 * D + 1e-6))
        x1, y1 = max(x0 + 1, int(math.ceil(a1 * D - 1e-6))), max(y0 + 1, int(math.ceil(b1 * D - 1e-6)))
        return x0, y0, min(x1, self.W), min(y1, self.H)

    def fill(self, a0, b0, a1, b1, colour):
        x0, y0, x1, y1 = self.rect(a0, b0, a1, b1)
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px[x, y] = colour

    def face(self, a0, b0, a1, b1, rgb, kind, material, rng, span=None, rims=True, mat=None, seed=0, pbr=None,
             light=None, open_i=False, open_j=False, style=None, panel=False, face_name=None, cube=None):
        """span: (cube top y, cube bottom y, bone top y, bone bottom y) in DSL px - the side faces then shade by HEIGHT
        IN THE BONE, so a stack of slices (a cuirass, a dome) is one continuous gradient instead of a stripe per cube;
        rims: the lit upper rim and the dark seam, only on cubes tall enough to be a plate of their own.
        light: (tools/unit_light.BodyLight, cube index, face name) - the baked sky light and occlusion (BAKED_LIGHT).
        style / panel: the make (a cloth's, a leather's) and whether the face is a thin cube's broad side; face_name /
        cube: which face of a cube of (w, h, d) px this is - the material runs its grain along the cube's long axis
        (tools/materials.Face)."""
        x0, y0, x1, y1 = self.rect(a0, b0, a1, b1)
        fw, fh = x1 - x0, y1 - y0
        lightmap = None
        if light is not None and fw > 0 and fh > 0:
            body, cube_n, light_face = light
            fu = np.clip(((np.arange(x0, x1) + 0.5) / self.D - a0) / max(1e-6, a1 - a0), 0, 1)
            fv = np.clip(((np.arange(y0, y1) + 0.5) / self.D - b0) / max(1e-6, b1 - b0), 0, 1)
            lightmap = body.face(cube_n, light_face, a1 - a0, b1 - b0, fw, fh, fu, fv)
        # the material's surface (tools/materials.py): albedo detail here, height + light response into the PBR maps
        detail = (materials.Face(mat, fw, fh, self.D, seed, kind, open_i, open_j, style=style, panel=panel,
                                 face=face_name, cube=cube) if mat else None)
        if pbr is not None:
            pbr.faces.append((x0, y0, x1, y1))
        for j in range(fh):
            for i in range(fw):
                edge_x = (i == 0 or i == fw - 1) and fw > 2 and not open_i
                t = j / max(1, fh - 1)
                if span is not None:
                    yy = span[0] - (j + 0.5) / fh * (span[0] - span[1])
                    tb = (span[2] - yy) / max(1e-6, span[2] - span[3])     # 0 at the bone's top, 1 at its bottom
                else:
                    tb = t
                if kind == "top":
                    if span is not None and not rims:
                        # the ledge of a slice in a stack (a cuirass, a dome): Minecraft lights an upward face ~1.35x a
                        # side, so a ledge painted like its side flashes as a bright stripe from the RTS camera;
                        # painted at 0.74 of the side it reads as the same curved surface continuing
                        k = (1.07 - 0.20 * tb) * 0.74
                    else:
                        k = 1.10 - 0.08 * (tb if span is not None else 0.0)
                        if (edge_x or ((j == 0 or j == fh - 1) and fh > 2 and not open_j)):
                            k -= 0.08
                elif kind == "bottom":
                    k = 0.70
                else:
                    fall = 0.06 if (self.keep_light and luma(rgb) >= 200) else 0.20   # light plate stays light
                    k = 1.07 - fall * tb - (0.05 * t if span is not None else 0.0)
                    if materials.REALISM and mat in REFLECT and span is not None:
                        # one horizon for the whole bone (tb): a stack of slices reflects one continuous world - only
                        # on cubes that are part of the bone's upright surface (a rotated slab or edge segment had its
                        # own horizon each: the halberd's edge read as a dashed line, Oct 16)
                        k *= 1.0 + REFLECT[mat] * (reflection(tb, 0.46, STEEL_PALETTE) - 1.0)
                    elif material == "metal":
                        k += 0.07 * math.exp(-((t - 0.28) / 0.14) ** 2)      # the polish band
                    if rims and fh > 3 and j == 0:
                        k = max(k, 1.0) * 1.12                                # the lit upper rim
                    elif rims and fh > 3 and j == fh - 1:
                        k *= 0.72                                             # the seam underneath
                    if edge_x:
                        k *= 0.93
                if material == "cloth" and not materials.REALISM:
                    # a fine weave - per-texel noise; the realism pass leaves cloth to tools/materials.py (it read as felt)
                    k *= 0.96 + 0.06 * rng.random() - (0.03 if (i + j) % 2 == 0 else 0.0)
                elif material == "metal" and kind == "side" and detail is None:
                    k *= 0.985 + 0.03 * rng.random()                          # brushed, barely
                hue = None
                if detail is not None:
                    a, hgt, sm, hue = detail.texel(i, j)
                    k *= a
                    if pbr is not None:
                        pbr.put(x0 + i, y0 + j, hgt, materials.specular(mat, sm))
                if lightmap is not None:
                    k *= lightmap[j, i]
                out = shade(rgb, k)
                if hue is not None and not self.mask:
                    # the material's colour shift (uneven dye, warm and cool skin, warm gold): the paint only - the team
                    # mask stays grey, so the owner's colour is never shifted
                    out = (clamp8(out[0] * hue[0]), clamp8(out[1] * hue[1]), clamp8(out[2] * hue[2]), 255)
                if STEEL_PALETTE and materials.REALISM and mat in ("plate", "blade") and kind == "side" and not self.mask:
                    tint = SKY_TINT if tb < 0.46 else GROUND_TINT
                    out = tuple(clamp8(c + (tint[n] * k - c) * MIRROR_TINT) for n, c in enumerate(out[:3])) + (255,)
                elif STEEL_PALETTE and materials.REALISM and mat in REFLECT and kind == "top" and not self.mask:
                    # seen from the RTS camera an upward face of polished metal mirrors the open sky: the helm crowns,
                    # pauldrons and shoulders are the brightest, coolest marks on the body - what keeps a steel-clad
                    # unit readable on the grass once its colours are real (the Oct 16 tours: C read darker from above)
                    sky = tuple(min(255.0, t * 1.12) for t in SKY_TINT)
                    w = SKY_MIRROR * REFLECT[mat] * (lightmap[j, i] if lightmap is not None else 1.0)
                    out = tuple(clamp8(c + (sky[n] - c) * w) for n, c in enumerate(out[:3])) + (255,)
                if materials.REALISM and span is not None and kind != "bottom":
                    yy = span[0] if kind == "top" else span[0] - (j + 0.5) / fh * (span[0] - span[1])
                    mud = max(0.0, min(1.0, (MUD_HEIGHT - yy) / MUD_HEIGHT)) ** 1.5 * 0.45
                    if mud > 0:
                        if self.mask:      # the team mask only darkens (the owner's colour, muddied)
                            out = tuple(clamp8(c * (1 - 0.6 * mud)) for c in out[:3]) + (255,)
                        else:
                            out = tuple(clamp8(c * (1 - mud) + MUD[n] * k * mud) for n, c in enumerate(out[:3])) + (255,)
                self.px[x0 + i, y0 + j] = out

    def island(self, u, v, w, h, d, rgb, material="plain", seed=0, flat=False, span=None, mat=None, pbr=None,
               light=None, open_z=False, style=None):
        """w, h, d are the cube's float sizes; (u, v) its integer texOffs. flat: one colour, no shading (masks).
        span: see face(). mat: the tools/materials.py material (surface detail); pbr: a PBRMaps to fill.
        style: the make of a cloth (tools/materials.style_for) - a quilted jack, a twill tabard..."""
        rng = random.Random(seed)
        uw, uh, ud = (int(math.ceil(s - 1e-6)) or 1 for s in (w, h, d))
        base = (tuple(rgb) + (255,)) if flat else shade(rgb, 0.92)
        # a safety net under the whole packed rectangle: whatever a sliver of a face samples is this cube's colour
        self.fill(u, v, u + 2 * (uw + ud), v + ud + uh, base)
        if pbr is not None and mat:
            pbr.fill(*self.rect(u, v, u + 2 * (uw + ud), v + ud + uh), materials.specular(mat))
        if flat:
            return
        rims = h >= RIM_MIN_HEIGHT
        top_span = None if span is None else (span[0], span[0], span[2], span[3])
        kw = dict(mat=mat, pbr=pbr, style=style, cube=(w, h, d))
        thin = PANEL_THIN       # a face whose cube is this thin behind it is a panel (a tabard's, a cape's)

        def lit(name):     # light: (BodyLight, cube index) -> per face
            return None if light is None else (light[0], light[1], name)
        # open_z (the cube's "open": "z"): its two z ends are joints in a chain - the top/bottom faces' v borders and
        # the west/east faces' u borders get no edge treatment
        self.face(u + d, v, u + d + w, v + d, rgb, "top", material, rng, top_span, rims, seed=seed * 8,
                  light=lit("top"), open_j=open_z, panel=h <= thin, face_name="top", **kw)
        self.face(u + d + w, v, u + d + 2 * w, v + d, rgb, "bottom", material, rng, None, rims, seed=seed * 8 + 1,
                  light=lit("bottom"), open_j=open_z, panel=h <= thin, face_name="bottom", **kw)
        for n, (a0, a1) in enumerate(((u, u + d), (u + d, u + d + w), (u + d + w, u + 2 * d + w),
                                      (u + 2 * d + w, u + 2 * d + 2 * w))):
            name = ("west", "north", "east", "south")[n]
            self.face(a0, v + d, a1, v + d + h, rgb, "side", material, rng, span, rims, seed=seed * 8 + 2 + n,
                      light=lit(name), open_i=open_z and n in (0, 2), panel=(w if n in (0, 2) else d) <= thin,
                      face_name=name, **kw)


class PBRMaps:
    """The LabPBR 1.3 maps of one atlas (tools/materials.py has the format): height and specular per texel while the
    faces are painted, then the normal map derived from the height FACE BY FACE (a slope never leaks across an island's
    border) - written beside the texture as <name>_n.png and <name>_s.png for shader packs (Iris / Oculus)."""

    def __init__(self, size):
        w, h = size
        self.height = np.ones((h, w), dtype=np.float32)
        self.spec = np.zeros((h, w, 4), dtype=np.uint8)
        self.spec[..., 3] = 255                                     # A 255 = no emission
        self.faces = []

    def fill(self, x0, y0, x1, y1, spec):
        self.spec[y0:y1, x0:x1] = spec

    def put(self, x, y, height, spec):
        self.height[y, x] = height
        self.spec[y, x] = spec

    def images(self, strength=NORMAL_STRENGTH):
        h, w = self.height.shape
        n = np.zeros((h, w, 4), dtype=np.uint8)
        n[..., 0] = n[..., 1] = 128
        n[..., 2] = n[..., 3] = 255
        for (x0, y0, x1, y1) in self.faces:
            if x1 - x0 < 1 or y1 - y0 < 1:
                continue
            hh = self.height[y0:y1, x0:x1]
            if materials.REALISM and hh.shape[0] > 2 and hh.shape[1] > 2:
                # a one-texel blur before the slopes: relief at a model-pixel scale and up survives, texel noise (the
                # felt lovish saw under Complementary, Oct 16) does not
                pad = np.pad(hh, 1, mode="edge")
                hh = (pad[1:-1, 1:-1] * 4 + pad[:-2, 1:-1] + pad[2:, 1:-1] + pad[1:-1, :-2] + pad[1:-1, 2:]) / 8.0
            # through the ONE LabPBR encoder (tools/materials.py), shared with the block pack (tools/gen_blocks.py)
            nx, ny, _ = materials.height_normals(hh, strength)
            n[y0:y1, x0:x1] = materials.encode_normal(nx, ny, 0.78 + 0.22 * hh, hh)     # AO: low = occluded
        probs = materials.check_labpbr(n, self.spec)
        if probs:
            raise SystemExit("LabPBR check failed: " + "; ".join(probs))
        return Image.fromarray(n, "RGBA"), Image.fromarray(self.spec, "RGBA")


def material_of(name):
    n = name.lower()
    if any(hh in n for hh in METAL_HINTS):
        return "metal"
    if any(hh in n for hh in CLOTH_HINTS):
        return "cloth"
    return "plain"


# ------------------------------------------------------------------ the part tree ----------------------------------
class Part:
    """A ModelPart to be: offset from the parent's pivot (model space), rotation in radians (MC signs), cubes as
    (x, y, z, w, h, d, inflate, uv) relative to its own pivot, children."""

    def __init__(self, name, offset, rot):
        self.name, self.offset, self.rot = name, tuple(offset), tuple(rot)
        self.boxes, self.children = [], []

    def child(self, name, offset=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
        p = Part(name, offset, rot)
        self.children.append(p)
        return p


def is_detail(c, emissive):
    if c["color"] in emissive:
        return False          # light is what reads at a distance
    sx, sy, sz = c["size"]
    vol = sx * sy * sz
    thin = sum(1 for v in (sx, sy, sz) if v <= LOD_MAX_THIN)
    return vol <= LOD_MAX_VOLUME or thin >= 2


def to_model(p):
    return (p[0], 24.0 - p[1], p[2])


def mc_rot(deg):
    return (-math.radians(deg[0]), math.radians(deg[1]), -math.radians(deg[2]))


def build_tree(m, uvs, emissive):
    by_parent = {}
    for b in m["bones"]:
        by_parent.setdefault(b.get("parent"), []).append(b)
    root = Part("root", (0, 0, 0), (0, 0, 0))

    def box(c, origin, uv):
        x0, y0, z0 = c["from"]
        sx, sy, sz = c["size"]
        return (x0 - origin[0], (24.0 - (y0 + sy)) - origin[1], z0 - origin[2], sx, sy, sz, c["inflate"], uv)

    def emit(b, parent_part, parent_pivot):
        pv = to_model(b["pivot"])
        part = parent_part.child(b["name"], tuple(pv[a] - parent_pivot[a] for a in range(3)), mc_rot(b["rotation"]))
        lod = None
        upg = {}
        for i, c in enumerate(b.get("cubes", [])):
            uv = uvs[(b["name"], i)]
            tag = c.get("upgrade") or b.get("upgrade")
            holder = part
            if tag:
                if tag not in upg:
                    upg[tag] = part.child("upg_" + tag)
                holder = upg[tag]
            elif is_detail(c, emissive):
                if lod is None:
                    lod = part.child("lod")
                holder = lod
            if any(abs(a) > 1e-9 for a in c["rotation"]):
                cp = to_model(c["pivot"])
                r = holder.child("%s_%s%d" % (b["name"], "u" if tag else "r", i),
                                 tuple(cp[a] - pv[a] for a in range(3)), mc_rot(c["rotation"]))
                r.boxes.append(box(c, cp, uv))
            else:
                holder.boxes.append(box(c, pv, uv))
        for ch in by_parent.get(b["name"], []):
            emit(ch, part, pv)

    for b in by_parent.get(None, []):
        emit(b, root, (0.0, 0.0, 0.0))
    return root


# ------------------------------------------------------------------ the check -----------------------------------------
def matmul(A, B):
    return tuple(tuple(sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def mat_zyx(r):
    cx, sx = math.cos(r[0]), math.sin(r[0])
    cy, sy = math.cos(r[1]), math.sin(r[1])
    cz, sz = math.cos(r[2]), math.sin(r[2])
    Rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    Ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    Rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return matmul(Rz, matmul(Ry, Rx))


def mc_corners(root, pose):
    """Every box's 8 corners, posed exactly like ModelPart.render: translate(offset) then rotate Z*Y*X, parents first.
    pose: part name -> extra (x, y, z) radians added to the part's rotation (restPose)."""
    out = []
    I = ((1, 0, 0), (0, 1, 0), (0, 0, 1))

    def walk(p, M, t):
        rot = p.rot
        if p.name in pose:
            rot = tuple(rot[a] + pose[p.name][a] for a in range(3))
        t2 = tuple(t[i] + sum(M[i][k] * p.offset[k] for k in range(3)) for i in range(3))
        M2 = matmul(M, mat_zyx(rot))
        for (x, y, z, w, h, d, g, _uv) in p.boxes:
            lo, hi = (x - g, y - g, z - g), (x + w + g, y + h + g, z + d + g)
            pts = []
            for cx in (lo[0], hi[0]):
                for cy in (lo[1], hi[1]):
                    for cz in (lo[2], hi[2]):
                        v = (cx, cy, cz)
                        pts.append(tuple(t2[i] + sum(M2[i][k] * v[k] for k in range(3)) for i in range(3)))
            out.append(pts)
        for c in p.children:
            walk(c, M2, t2)

    walk(root, I, (0.0, 0.0, 0.0))
    return out


def check(m, root):
    """The game tree must put every cube where the concept renderer does (upgrade parts included)."""
    pose = {n: mc_rot(r) for n, r in m["pose"].items()}
    game = mc_corners(root, pose)
    concept = [pts for pts, _ in posed_cubes(m, upgraded=True)]
    if len(game) != len(concept):
        raise SystemExit("check: %d cubes in the game tree, %d in the concept" % (len(game), len(concept)))

    def key(pts):
        # model space -> DSL space, then an order-free signature (sorted corners)
        return sorted((round(p[0], 2), round(24.0 - p[1], 2), round(p[2], 2)) for p in pts)

    worst = 0.0
    g_sorted = sorted(key(p) for p in game)
    c_sorted = sorted(sorted((round(p[0], 2), round(p[1], 2), round(p[2], 2)) for p in pts) for pts in concept)
    for a, b in zip(g_sorted, c_sorted):
        for pa, pb in zip(a, b):
            worst = max(worst, max(abs(pa[i] - pb[i]) for i in range(3)))
    if worst > 0.02:
        raise SystemExit("check: the game tree moves a cube corner %.3f px away from the concept - not written" % worst)
    return worst


# ------------------------------------------------------------------ Java ------------------------------------------------
def java_tree(root):
    lines = []

    def pose(p):
        o, r = p.offset, p.rot
        if any(abs(a) > 1e-9 for a in r):
            return "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (f(o[0]), f(o[1]), f(o[2]), f(r[0]), f(r[1]), f(r[2]))
        if any(abs(a) > 1e-9 for a in o):
            return "PartPose.offset(%s, %s, %s)" % (f(o[0]), f(o[1]), f(o[2]))
        return "PartPose.ZERO"

    counter = [0]

    def emit(p, parent_var):
        cubes = "CubeListBuilder.create()" + "".join(
            ".texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))" % (
                uv[0], uv[1], f(x), f(y), f(z), f(w), f(h), f(d), f(g)) for (x, y, z, w, h, d, g, uv) in p.boxes)
        call = '%s.addOrReplaceChild("%s", %s, %s)' % (parent_var, p.name, cubes, pose(p))
        if p.children:
            counter[0] += 1
            var = "p%d_%s" % (counter[0], ident(p.name))
            lines.append("PartDefinition %s = %s;" % (var, call))
            for c in p.children:
                emit(c, var)
        else:
            lines.append(call + ";")

    for c in root.children:
        emit(c, "root")
    return lines


def collect_lines(root, m):
    """Java that files every bone, "lod" and "upg_<tag>" part by its path from the root."""
    bone_names = {b["name"] for b in m["bones"]}
    out = []

    def walk(p, expr):
        for c in p.children:
            e = '%s.getChild("%s")' % (expr, c.name)
            if c.name in bone_names:
                out.append('bones.put("%s", %s);' % (c.name, e))
                walk(c, e)
            elif c.name == "lod":
                out.append("lodParts.add(%s);" % e)
            elif c.name.startswith("upg_"):
                out.append('upgradeParts.computeIfAbsent("%s", k -> new ArrayList<>()).add(%s);' % (c.name[4:], e))
    walk(root, "root")
    return out


def pass_lines(root, colour_of_uv, team, emissive):
    """Java that files every part whose OWN cubes carry team-mask or glow texels (any part: bones, lod, upgrade and
    one-cube rotated children). TeamColourLayer and the glow layer draw only those parts' cubes - the rest of the body
    has no texel in their textures, so drawing it again was pure cost (tools/body_budget.py: ~3x the vertices of one
    pass per unit, ~55% of them for nothing)."""
    out = []

    def walk(p, expr):
        for c in p.children:
            e = '%s.getChild("%s")' % (expr, c.name)
            colours = {colour_of_uv[bx[7]] for bx in c.boxes}
            if colours & team:
                out.append("teamParts.add(%s);" % e)
            if colours & emissive:
                out.append("glowParts.add(%s);" % e)
            walk(c, e)
    walk(root, "root")
    return out


def count_parts(root):
    return 1 + sum(count_parts(c) for c in root.children)


def build(m, cls, team_colours, layer, density):
    raw = {k: hex_rgb(v) for k, v in m["palette"].items()}
    # the painted base uses the game-value palette; the glow keeps its own colours (it is drawn full-bright)
    pal = {k: (rgb if k in set(m.get("emissive", [])) else game_value(realistic_colour(k, rgb))) for k, rgb in raw.items()}
    emissive = set(m.get("emissive", []))

    # 1. UV islands: one per cube, packed in model pixels
    cubes = []
    for b in m["bones"]:
        for i, c in enumerate(b.get("cubes", [])):
            dims = [max(1, int(math.ceil(v - 1e-6))) for v in c["size"]]
            cubes.append((b["name"], i, c, dims))
    width = 128
    while True:
        atlas = Atlas(width)
        uvs = {}
        for (bn, i, c, (w, h, d)) in sorted(cubes, key=lambda t: (-(t[3][2] + t[3][1]), t[0], t[1])):
            uvs[(bn, i)] = atlas.place(2 * (w + d), d + h)
        if atlas.height() <= width or width >= 1024:
            break
        width *= 2
    tw, th = width, 1 << max(5, math.ceil(math.log2(max(32, atlas.height()))))
    D = density
    imgs = {k: Image.new("RGBA", (tw * D, th * D), (0, 0, 0, 0)) for k in ("base", "team", "glow")}
    paint = {k: Painter(img, D, keep_light=(k == "base"), mask=(k == "team")) for k, img in imgs.items()}
    pbr = PBRMaps(imgs["base"].size)
    hi, lo = TEAM_DYE if materials.REALISM else (255, 190)
    team_grey = {col: ((hi,) * 3 if k == 0 else (lo,) * 3) for k, col in enumerate(team_colours)}
    # each bone's vertical extent (its unrotated cubes): the side faces shade by height in the bone
    extent = {}
    for b in m["bones"]:
        ys = [(c["from"][1], c["from"][1] + c["size"][1]) for c in b.get("cubes", []) if not any(c["rotation"])]
        if ys:
            extent[b["name"]] = (max(y1 for _, y1 in ys), min(y0 for y0, _ in ys))
    body_light = None
    if BAKED_LIGHT and materials.REALISM:
        bones = {b["name"]: b for b in m["bones"]}
        corners = [pts for pts, _ in posed_cubes(m, upgraded=True)]      # the same order as `cubes`
        assert len(corners) == len(cubes)
        body_light = unit_light.BodyLight(corners, [not (c.get("upgrade") or bones[bn].get("upgrade"))
                                                    and c["color"] not in emissive for (bn, _i, c, _d) in cubes])
    for n, (bn, i, c, _dims) in enumerate(cubes):
        u, v = uvs[(bn, i)]
        w, h, d = c["size"]
        col = c["color"]
        span = None
        if not any(c["rotation"]) and bn in extent and extent[bn][0] - extent[bn][1] > 2.0:
            span = (c["from"][1] + h, c["from"][1], extent[bn][0], extent[bn][1])
        mat = materials.material_for(col)
        style = materials.style_for(col)
        light = None if body_light is None or col in emissive else (body_light, n)
        oz = c.get("open") == "z"
        paint["base"].island(u, v, w, h, d, pal[col], material_of(col), seed=n, span=span, mat=mat, pbr=pbr,
                             light=light, open_z=oz, style=style)
        if col in team_grey:
            # the mask carries the same light, shade and weave as the paint, in grey, so the owner's colour is too
            paint["team"].island(u, v, w, h, d, team_grey[col], material_of(col), seed=n, span=span, mat=mat,
                                 light=light, open_z=oz, style=style)
        if col in emissive:
            paint["glow"].island(u, v, w, h, d, raw[col], flat=True)

    # 2. the part tree, checked against the concept renderer
    root = build_tree(m, uvs, emissive)
    worst = check(m, root)

    pose_lines = []
    for name, r in m["pose"].items():
        rr = mc_rot(r)
        pose_lines.append('add("%s", %s, %s, %s);' % (name, f(rr[0]), f(rr[1]), f(rr[2])))
    upgrade_tags = sorted({c.get("upgrade") or b.get("upgrade") for b in m["bones"] for c in b.get("cubes", [])
                           if c.get("upgrade") or b.get("upgrade")})
    bone_names = [b["name"] for b in m["bones"]]
    # pass_lines finds a cube's colour by its atlas place: every island must have its own
    assert len(set(uvs.values())) == len(uvs), "two cubes share an atlas place"
    java = JAVA_TEMPLATE.format(
        package=PACKAGE, cls=cls, layer=layer, src=os.path.relpath(m["_path"], ROOT).replace(os.sep, "/"), name=m["name"],
        defs="\n        ".join(java_tree(root)), tw=tw, th=th,
        part_names=", ".join('"%s"' % n for n in bone_names),
        pose="\n        ".join(pose_lines) if pose_lines else "// no rest pose",
        upgrades=", ".join('"%s"' % t for t in upgrade_tags), density=D,
        cubes=len(cubes), parts=count_parts(root) - 1, collect="\n        ".join(collect_lines(root, m)),
        passes="\n        ".join(pass_lines(root, {uvs[(bn, i)]: c["color"] for (bn, i, c, _d) in cubes},
                                            set(team_colours), emissive)),
        parents=",\n        ".join('Map.entry("%s", "%s")' % (b["name"], b.get("parent") or "") for b in m["bones"]))
    imgs["base_n"], imgs["base_s"] = pbr.images()
    return java, imgs, dict(root=root, tw=tw, th=th, cubes=len(cubes), parts=count_parts(root) - 1, worst=worst,
                            lod=sum(len(p.boxes) for p in walk_parts(root) if p.name == "lod" or in_lod(root, p)))


def walk_parts(p):
    yield p
    for c in p.children:
        yield from walk_parts(c)


def in_lod(root, target):
    def find(p, inside):
        if p is target:
            return inside
        for c in p.children:
            r = find(c, inside or p.name == "lod")
            if r is not None:
                return r
        return None
    return bool(find(root, False))


JAVA_TEMPLATE = '''package {package};

// GENERATED by tools/dsl_to_game.py from {src} ("{name}") - do not edit by hand: change the concept model (its
// generator) and run the tool again. {cubes} cubes in {parts} parts; the painted texture is {density}x (UVs stay in
// model pixels). The animation lives in a hand-written subclass, not here.

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.modelling.models.SplitPasses;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class {cls}<T extends Entity> extends HierarchicalModel<T> implements SplitPasses {{
    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "{layer}"), "main");
    /** The concept model's bones, by name (the same names as the DSL file). */
    public static final List<String> BONES = List.of({part_names});
    /** Upgrade tags whose parts ("upg_<tag>" under a bone) stay hidden until the unit has the upgrade. */
    public static final List<String> UPGRADES = List.of({upgrades});

    private final ModelPart root;
    private final List<ModelPart> allParts;
    /** Bone name -> part. */
    public final Map<String, ModelPart> bones = new HashMap<>();
    /** Every "lod" part (the small detail), and every "upg_<tag>" part by tag. */
    public final List<ModelPart> lodParts = new ArrayList<>();
    public final Map<String, List<ModelPart>> upgradeParts = new HashMap<>();
    /** The parts whose own cubes carry team-mask / glow texels - all the team and glow passes need to draw. */
    private final List<ModelPart> teamParts = new ArrayList<>();
    private final List<ModelPart> glowParts = new ArrayList<>();

    public {cls}(ModelPart root) {{
        this.root = root;
        this.allParts = root.getAllParts().toList();   // once: restPose runs every frame for every unit
        collect(root);
    }}

    /** The tree's named parts, by their generated paths (no lookups by search at run time). */
    private void collect(ModelPart root) {{
        {collect}
        {passes}
    }}

    @Override
    public List<ModelPart> teamParts() {{
        return teamParts;
    }}

    @Override
    public List<ModelPart> glowParts() {{
        return glowParts;
    }}

    @Override
    public void drawOnly(List<ModelPart> keep) {{
        for (int i = 0, n = allParts.size(); i < n; i++)
            allParts.get(i).skipDraw = true;
        for (int i = 0, n = keep.size(); i < n; i++)
            keep.get(i).skipDraw = false;
    }}

    @Override
    public void drawAll() {{
        for (int i = 0, n = allParts.size(); i < n; i++)
            allParts.get(i).skipDraw = false;
    }}

    public ModelPart bone(String name) {{
        return bones.get(name);
    }}

    /** Bone name -> its parent bone ("" for a bone on the root). */
    public static final Map<String, String> PARENTS = Map.ofEntries({parents});

    /**
     * A bone and its ancestors, root side first: apply translateAndRotate of all but the last, then render the last,
     * to draw one bone where it stands in the whole body (an additive glow pass over a held weapon, say).
     */
    public ModelPart[] path(String name) {{
        List<ModelPart> out = new ArrayList<>();
        for (String b = name; b != null && !b.isEmpty(); b = PARENTS.get(b)) {{
            ModelPart p = bones.get(b);
            if (p == null)
                return new ModelPart[0];
            out.add(0, p);
        }}
        return out.toArray(new ModelPart[0]);
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        {defs}
        return LayerDefinition.create(mesh, {tw}, {th});
    }}

    @Override
    public ModelPart root() {{
        return root;
    }}

    private void add(String bone, float x, float y, float z) {{
        ModelPart p = bones.get(bone);
        if (p != null) {{
            p.xRot += x;
            p.yRot += y;
            p.zRot += z;
        }}
    }}

    /** Back to the concept's rest pose: the bones' own angles are baked into the parts, "pose" adds the idle tilt. */
    public void restPose() {{
        for (int i = 0, n = allParts.size(); i < n; i++)
            allParts.get(i).resetPose();
        {pose}
    }}

    /** The small detail on or off (off when the camera is far). */
    public void setDetail(boolean on) {{
        for (ModelPart p : lodParts)
            p.visible = on;
    }}

    /** An upgrade's parts on or off. */
    public void setUpgrade(String tag, boolean on) {{
        for (ModelPart p : upgradeParts.getOrDefault(tag, List.of()))
            p.visible = on;
    }}

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {{
        restPose();
    }}
}}
'''


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model")
    ap.add_argument("--class", dest="cls", required=True)
    ap.add_argument("--texture", required=True)
    ap.add_argument("--team", default="", help="palette names drawn as team colour (first = full, others = darker)")
    ap.add_argument("--layer", default=None)
    ap.add_argument("--density", type=int, default=DENSITY)
    ap.add_argument("--check-only", action="store_true", help="build and check, write nothing")
    a = ap.parse_args()
    m = render_concept.load_model(a.model)
    m["_path"] = os.path.abspath(a.model)
    team = [t for t in a.team.split(",") if t]
    for t in team:
        if t not in m["palette"]:
            raise SystemExit("--team: %r is not in the palette" % t)
    java, imgs, info = build(m, a.cls, team, a.layer or a.texture + "_layer", a.density)
    print("%s: %d cubes in %d parts (%d detail cubes in lod parts); atlas %dx%d UV at %dx = %dx%d px; "
          "check: worst corner %.4f px" % (a.cls, info["cubes"], info["parts"], info["lod"], info["tw"], info["th"],
                                           a.density, info["tw"] * a.density, info["th"] * a.density, info["worst"]))
    if a.check_only:
        return 0
    os.makedirs(JAVA_DIR, exist_ok=True)
    with open(os.path.join(JAVA_DIR, a.cls + ".java"), "w") as fh:
        fh.write(java)
    imgs["base"].save(os.path.join(TEX_DIR, a.texture + ".png"), optimize=True)
    if team:
        imgs["team"].save(os.path.join(TEX_DIR, a.texture + "_team.png"), optimize=True)
    imgs["glow"].save(os.path.join(TEX_DIR, a.texture + "_glow.png"), optimize=True)
    # the LabPBR maps for shader packs: the base's, and the same maps again for the team overlay (drawn over the cloth
    # with its own texture, so a shader binds ITS maps there). Full size: the half-size copies (until Oct 15) dropped
    # every other texel's normal, so the owner's cloth lit flatter and blockier than the paint under it
    imgs["base_n"].save(os.path.join(TEX_DIR, a.texture + "_n.png"), optimize=True)
    imgs["base_s"].save(os.path.join(TEX_DIR, a.texture + "_s.png"), optimize=True)
    if team:
        imgs["base_n"].save(os.path.join(TEX_DIR, a.texture + "_team_n.png"), optimize=True)
        imgs["base_s"].save(os.path.join(TEX_DIR, a.texture + "_team_s.png"), optimize=True)
    print("wrote %s.java, %s.png%s, _glow.png, _n.png, _s.png%s" % (
        a.cls, a.texture, ", _team.png" if team else "", ", _team_n.png, _team_s.png" if team else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
