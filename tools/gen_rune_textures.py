#!/usr/bin/env python3
"""Rune circle atlas for barfx/RuneFx (the shared rune magic effect, lovish Nov 5: "I want to add rune magic effects").

Our own designs, drawn from stroke rules: one rune alphabet per faction, so the ring's SHAPE names the faction even
in black and white, and its hue (FactionTraits.upgradeGlow) agrees with the faction's other light. Nothing is traced
or copied from another work.

Atlas (2048 x 1152, RGBA, colours baked in, alpha = brightness for the additive pass):
  column f = the BarFx.F_* faction id (0 neutral, 1 Sunforged, 2 Gravebound, 3 Horde, 4 Verdant, 5 Light Grid in the
  old Tidewrought slot, 6 Stormcallers, 7 Brassworks), 256 px wide;
  row 0 (y 0-255)      the outer ring: a crisp rim at the real radius, the glyph band, the faction's band ornament;
  row 1 (y 256-511)    the inner sigil (the counter-rotating layer, heavier strokes);
  rows y 512-767       16 floating glyphs of 64 px (4 x 4);
  row y 768-1023       the glyph band: two fine rims and twelve glyphs, the centre empty (a tier, the release ring);
  rows y 1024-1151     a plain ring (128) | the faction's mote, the beam strip (64) over a soft spark, the underlay's
                       soft disc (64, white, tinted dark by the vertex colour: the only cell not added as light).
Each cell: a core line in the faction's light tint plus a blurred halo in its hue (BAT readability: core 3-4 px and
halo 6-10 px at zoom 30). RuneFxClient maps the cells; tools/rune_preview.py renders them offline.

  python tools/gen_rune_textures.py            # writes the atlas into the mod's assets
  python tools/gen_rune_textures.py --mock DIR # also three concept stills (grass, scale, timing) into DIR
numpy + pillow + stdlib only (CI has no scipy).
"""
import argparse
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

CELL = 256
SS = 4                      # supersampling for antialiased strokes
GLYPH = 64
GLYPHS_PER_FACTION = 16
OUT = os.path.join(os.path.dirname(__file__), '..', 'bar', 'src', 'main', 'resources', 'assets', 'reignofnether',
                   'textures', 'fx', 'rune_atlas.png')

# F_* id -> (name, hue, accent). Hues = FactionTraits.upgradeGlow; the accent lights the glyphs and sigil.
FACTIONS = [
    ('neutral', 0xE8E8FF, 0xFFFFFF),
    ('sunforged', 0xFFE6A0, 0xFFFFFF),     # pale cream-gold, white core: the brightest, least saturated warm
    ('gravebound', 0x8FD000, 0xC8FF1E),    # acid chartreuse over a violet underglow (apart from Verdant's emerald)
    ('horde', 0xFF2A00, 0xFF5A1E),         # red-orange, its core orange too (a gold core read as Brassworks)
    ('verdant', 0x2FC860, 0xB8FFD0),       # emerald (a value step under Gravebound's acid), white-gold blossoms
    ('lightgrid', 0x00B8D0, 0x7FF4FF),     # cyan core, never white
    ('stormcallers', 0x7A3CFF, 0xB48CFF),  # violet, never white (a white ring reads as a selection circle)
    ('brassworks', 0xE0A020, 0xF0C060),    # amber, and dimmer (GAIN): the darkest warm
]


# brightness per faction (alpha scale on its light cells): Brassworks sits a value step under Sunforged and Horde
GAIN = [1.0, 0.85, 1.0, 1.0, 0.85, 0.85, 0.8, 0.85]


def rgb(c):
    return np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255], dtype=np.float32) / 255.0


class Pen:
    """Strokes on a supersampled square canvas, in cell units (0..size)."""

    def __init__(self, size, wmul=1.0):
        self.size = size
        self.wmul = wmul
        self.img = Image.new('L', (size * SS, size * SS), 0)
        self.d = ImageDraw.Draw(self.img)

    def line(self, pts, w):
        p = [(x * SS, y * SS) for x, y in pts]
        w *= self.wmul
        self.d.line(p, fill=255, width=max(1, int(round(w * SS))), joint='curve')
        r = w * SS / 2
        for x, y in (p[0], p[-1]):
            self.d.ellipse([x - r, y - r, x + r, y + r], fill=255)

    def dot(self, x, y, r):
        self.d.ellipse([(x - r) * SS, (y - r) * SS, (x + r) * SS, (y + r) * SS], fill=255)

    def circle(self, x, y, r, w, a0=0.0, a1=2 * math.pi, n=None):
        n = n or max(12, int(abs(a1 - a0) * r / 2))
        self.line([(x + r * math.cos(a0 + (a1 - a0) * i / n), y + r * math.sin(a0 + (a1 - a0) * i / n))
                   for i in range(n + 1)], w)

    def mask(self):
        return np.asarray(self.img.resize((self.size, self.size), Image.LANCZOS), dtype=np.float32) / 255.0


def bake(core, hue, accent_core=None, glow_r=7.0, glow=0.65):
    """core (HxW 0..1) -> RGBA: white-hot where the line is, the faction hue in the halo."""
    h, w = core.shape
    g = Image.fromarray((core * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(glow_r))
    halo = np.asarray(g, dtype=np.float32) / 255.0
    halo = np.clip(halo * 1.8, 0, 1)
    a = np.clip(core + glow * halo, 0, 1)
    hot = np.clip(core * 0.75, 0, 1)[..., None]
    base = rgb(hue)[None, None, :]
    top = (rgb(accent_core) if accent_core is not None else np.ones(3, np.float32))[None, None, :]
    col = base * (1 - hot) + top * hot     # the core line in the faction's light tint, the halo in its hue
    out = np.concatenate([col, a[..., None]], axis=2)
    return (np.clip(out, 0, 1) * 255).astype(np.uint8)


# ---------------------------------------------------------------- glyph grammars (local u, v in [-1, 1], +v = up)

def g_sun(rng):
    s = [[(0, -1), (0, 1)]]
    for v in rng.sample([-0.65, -0.1, 0.45], rng.choice([1, 2])):
        hw = rng.choice([0.45, 0.7])
        s.append([(-hw, v), (hw, v)])
    if rng.random() < 0.6:
        s.append([(-0.5, 0.75), (0, 1.0), (0.5, 0.75)])
    if rng.random() < 0.5:
        s.append([(-0.75, -0.4), (-0.75, 0.2)])
        s.append([(0.75, -0.4), (0.75, 0.2)])
    return s, [(0, 1.25, 0.16)] if rng.random() < 0.5 else []


def g_grave(rng):
    side = rng.choice([-1, 1])
    s = [[(0, -1), (0, 0.45), (0.3 * side, 0.9), (0.75 * side, 0.75)]]
    if rng.random() < 0.8:
        s.append([(-0.55, -0.35 + rng.random() * 0.3), (0.5, 0.0 + rng.random() * 0.3)])
    if rng.random() < 0.5:
        s.append([(-0.6 * side, -1), (-0.6 * side, -0.55)])
    eye = [(-0.5 * side, 0.45, 0.22)] if rng.random() < 0.6 else []
    return s, [], eye


def g_horde(rng):
    n = rng.choice([2, 3, 3, 4])
    s = []
    for i in range(n):
        x = (i - (n - 1) / 2) * 0.45
        s.append([(x - 0.28, -0.9), (x + 0.28, 0.9)])
    if rng.random() < 0.6:
        y = rng.choice([-1.0, 1.0])
        s.append([(-0.75, y * 0.55), (0, y * 1.0), (0.75, y * 0.55)])
    return s, []


def g_verdant(rng):
    side = rng.choice([-1, 1])
    stem = [(0.25 * side * math.sin(t * 2.4), -1 + 1.8 * t) for t in [i / 10 for i in range(11)]]
    s = [stem]
    cx, cy = stem[-1]
    spiral = [(cx + side * 0.4 * (1 - t) * math.cos(t * 5.5), cy - 0.05 + 0.4 * (1 - t) * math.sin(t * 5.5))
              for t in [i / 14 for i in range(15)]]
    s.append(spiral)
    if rng.random() < 0.8:
        ly = -0.25 + rng.random() * 0.4
        leaf_a = [(-side * 0.1, ly), (-side * 0.45, ly + 0.35), (-side * 0.8, ly + 0.25)]
        leaf_b = [(-side * 0.1, ly), (-side * 0.5, ly - 0.05), (-side * 0.8, ly + 0.25)]
        s += [leaf_a, leaf_b]
    return s, []


def g_storm(rng):
    m = rng.choice([-1, 1])
    s = [[(-0.5 * m, 1), (0.3 * m, 0.2), (-0.3 * m, -0.05), (0.5 * m, -1)]]
    for _ in range(rng.choice([1, 2])):
        y = rng.choice([0.7, -0.6, 0.15])
        x = rng.choice([-0.7, 0.55])
        s.append([(x - 0.25, y), (x + 0.25, y)])
    return s, []


def g_brass(rng):
    s = []
    dots = []
    cy = rng.choice([0.35, 0.0])
    r = 0.42
    ring = [(r * math.cos(2 * math.pi * i / 20), cy + r * math.sin(2 * math.pi * i / 20)) for i in range(21)]
    s.append(ring)
    n = rng.choice([4, 6])
    for i in range(n):
        a = 2 * math.pi * i / n + 0.3
        s.append([(r * math.cos(a), cy + r * math.sin(a)), (1.45 * r * math.cos(a), cy + 1.45 * r * math.sin(a))])
    if cy > 0:
        s.append([(-0.6, -0.55), (0.6, -0.55), (0.6, -0.95), (-0.6, -0.95), (-0.6, -0.55)])
        s.append([(0, -0.55), (0, -0.95)])
    else:
        dots.append((0, 0, 0.12))
    return s, dots


def g_grid(rng):
    x0 = rng.choice([-0.7, -0.4])
    x1 = rng.choice([0.3, 0.7])
    ym = rng.choice([-0.2, 0.2])
    s = [[(x0, -1), (x0, ym), (x1, ym), (x1, 1)]]
    if rng.random() < 0.6:
        s.append([(x1, -1), (x1, -0.5), (0.0, -0.5)])
    sq = rng.random() < 0.5
    if sq:
        s.append([(-0.3, 0.5), (0.0, 0.5), (0.0, 0.8), (-0.3, 0.8), (-0.3, 0.5)])
    return s, [(x0, -1, 0.15), (x1, 1, 0.15)]


def g_neutral(rng):
    return [[(0, -1), (0, 1)], [(-0.5, 0.3), (0.5, -0.3)]], [(0, 1.2, 0.15)]


GRAMMAR = [g_neutral, g_sun, g_grave, g_horde, g_verdant, g_grid, g_storm, g_brass]
WIDTH = [1.0, 0.9, 0.9, 1.35, 0.9, 0.85, 0.95, 0.9]   # stroke weight per faction (the Horde cuts heavy)


def glyph(f, rng):
    out = GRAMMAR[f](rng)
    strokes, dots = out[0], out[1]
    eyes = out[2] if len(out) > 2 else []
    return strokes, dots, eyes


def draw_glyph(pen, f, g, place, w):
    """place(u, v) -> (x, y) in cell units; w = stroke width in cell units."""
    strokes, dots, eyes = g
    for st in strokes:
        pts = []
        for (u0, v0), (u1, v1) in zip(st[:-1], st[1:]):   # densify so strokes bend along a ring
            for k in range(6):
                t = k / 6
                pts.append(place(u0 + (u1 - u0) * t, v0 + (v1 - v0) * t))
        pts.append(place(*st[-1]))
        pen.line(pts, w)
    for u, v, r in dots:
        x, y = place(u, v)
        x2, y2 = place(u + r, v)
        pen.dot(x, y, max(w * 0.7, math.hypot(x2 - x, y2 - y)))
    for u, v, r in eyes:
        cx, cy = place(u, v)
        pts = [place(u + r * math.cos(a), v + r * math.sin(a)) for a in [2 * math.pi * i / 16 for i in range(17)]]
        pen.line(pts, w * 0.8)


# ---------------------------------------------------------------- the ring and the sigil per faction

def ring_cell(f, seed=11):
    rng = random.Random(seed * 31 + f)
    c = CELL / 2
    R = CELL * 0.47           # the outer rim: RuneFx draws the quad so this radius = the circle's real radius
    pen = Pen(CELL)
    rim_w = 5.0
    band_in, band_out = 0.68 * R, 0.86 * R

    def polar(r, a):
        return c + r * math.cos(a), c + r * math.sin(a)

    # rim (Gravebound: broken; Brassworks: gear teeth; Horde: rough)
    if f == 2:
        for i in range(7):
            a0 = 2 * math.pi * i / 7 + 0.12
            pen.circle(c, c, R, rim_w, a0, a0 + 2 * math.pi / 7 - 0.24)
            pen.line([polar(R - 6, a0 - 0.06), polar(R + 4, a0 - 0.12)], 3)
    elif f == 7:
        pen.circle(c, c, R - 5, rim_w * 0.8)
        teeth = 28
        for i in range(teeth):
            a0, a1 = 2 * math.pi * (i - 0.22) / teeth, 2 * math.pi * (i + 0.22) / teeth
            pen.line([polar(R - 5, a0), polar(R + 1, a0), polar(R + 1, a1), polar(R - 5, a1)], 3.2)
    elif f == 3:
        pts = []
        for i in range(97):
            a = 2 * math.pi * i / 96
            pts.append(polar(R + (rng.random() - 0.5) * 3, a))
        pen.line(pts, rim_w * 1.4)
        for i in range(12):
            a = 2 * math.pi * (i + 0.5) / 12
            pen.line([polar(R + 2, a - 0.07), polar(R + 9 + rng.random() * 4, a), polar(R + 2, a + 0.07)], 3.0)
    else:
        pen.circle(c, c, R, rim_w)
    # inner line of the glyph band
    if f == 4:   # Verdant: two woven vines instead of plain lines
        for ph in (0, math.pi):
            pen.line([polar(band_in - 3 + 4 * math.sin(a * 9 + ph), a) for a in [2 * math.pi * i / 180 for i in range(181)]], 2.6)
        for i in range(9):
            a = 2 * math.pi * i / 9
            x, y = polar(band_in - 8, a)
            pen.dot(x, y, 3.2)
    elif f == 6:   # Stormcallers: segmented arcs, sparks between
        for i in range(8):
            a0 = 2 * math.pi * i / 8 + 0.1
            pen.circle(c, c, band_in, 3.4, a0, a0 + 2 * math.pi / 8 - 0.2)
            am = a0 - 0.1
            pen.line([polar(band_in - 6, am - 0.05), polar(band_in, am + 0.03), polar(band_in + 6, am - 0.03)], 2.2)
    elif f == 5:   # Light Grid: traces with node dots
        pen.circle(c, c, band_in, 2.6)
        for i in range(16):
            a = 2 * math.pi * i / 16
            pen.line([polar(band_in, a), polar(band_in - 9, a), polar(band_in - 9, a + 0.12)], 2.2)
            x, y = polar(band_in - 9, a + 0.12)
            pen.dot(x, y, 2.6)
    else:
        pen.circle(c, c, band_in, 3.0 if f != 3 else 4.2)
    if f == 1:   # Sunforged: a second fine rim and sun rays between the band and the rim
        pen.circle(c, c, R - 9, 2.2)
        for i in range(24):
            a = 2 * math.pi * (i + 0.5) / 24
            pen.line([polar(band_out + 1, a), polar(R - 11, a)], 1.8)
    if f == 7:   # Brassworks: dial ticks inside the band
        for i in range(60):
            a = 2 * math.pi * i / 60
            pen.line([polar(band_in - 2, a), polar(band_in - (8 if i % 5 == 0 else 4), a)], 1.6 if i % 5 else 2.4)
    # the glyph band: 8 glyphs, upright = pointing outward
    n = 8
    gw = WIDTH[f] * 3.2
    for i in range(n):
        a = 2 * math.pi * (i + 0.5) / n
        g = glyph(f, rng)
        rm = (band_in + band_out) / 2
        hh = (band_out - band_in) / 2 * 0.9
        hw = hh * 0.75

        def place(u, v, a=a, rm=rm, hh=hh, hw=hw):
            r = rm + v * hh
            return c + r * math.cos(a) - u * hw * math.sin(a), c + r * math.sin(a) + u * hw * math.cos(a)
        draw_glyph(pen, f, g, place, gw)
        # a small separator between glyphs
        b = a + math.pi / n
        x, y = polar(rm, b)
        pen.dot(x, y, 1.8 * WIDTH[f])
    return pen.mask()


def sigil_cell(f):
    c = CELL / 2
    R = CELL * 0.40
    pen = Pen(CELL, 1.5)   # heavier than the ring: drawn smaller (0.44 of it), it must still read at zoom 30

    def polar(r, a):
        return c + r * math.cos(a), c + r * math.sin(a)
    w = 3.6
    if f == 1:      # two squares -> an eight-point star, a sun disc
        for rot in (0, math.pi / 4):
            pen.line([polar(R, rot + math.pi / 4 + k * math.pi / 2) for k in range(5)], w)
        pen.circle(c, c, R * 0.38, w)
        for i in range(8):
            a = i * math.pi / 4
            pen.line([polar(R * 0.42, a), polar(R * 0.62, a)], 2.4)
    elif f == 2:    # five-spoke bone wheel with ribs
        pen.circle(c, c, R * 0.92, w * 0.8, 0.2, 2 * math.pi - 0.2)
        for i in range(5):
            a = -math.pi / 2 + i * 2 * math.pi / 5
            pen.line([polar(R * 0.2, a), polar(R * 0.92, a)], w)
            for k in (0.45, 0.65):
                pen.line([polar(R * k, a - 0.18), polar(R * k, a + 0.18)], 2.4)
        pen.circle(c, c, R * 0.2, w)
    elif f == 3:    # spiked triangle
        tri = [polar(R * 0.85, -math.pi / 2 + k * 2 * math.pi / 3) for k in range(4)]
        pen.line(tri, w * 1.4)
        for k in range(3):
            a = -math.pi / 2 + k * 2 * math.pi / 3
            pen.line([polar(R * 0.85, a), polar(R * 1.05, a)], w * 1.2)
            am = a + math.pi / 3
            pen.line([polar(R * 0.42, am), polar(R * 0.72, am)], w)
        pen.circle(c, c, R * 0.22, w)
    elif f == 4:    # six leaves round a seed
        for i in range(6):
            a = i * math.pi / 3
            tip = polar(R * 0.92, a)
            for side in (-1, 1):
                mid = polar(R * 0.55, a + side * 0.32)
                pen.line([polar(R * 0.18, a), mid, tip], 2.8)
        pen.circle(c, c, R * 0.16, w)
    elif f == 5:    # hex lattice
        for ring in (0.35, 0.7):
            pen.line([polar(R * ring, k * math.pi / 3) for k in range(7)], 2.8)
        for k in range(6):
            pen.line([polar(R * 0.35, k * math.pi / 3), polar(R * 0.7, k * math.pi / 3)], 2.4)
            x, y = polar(R * 0.7, k * math.pi / 3)
            pen.dot(x, y, 4.0)
        pen.dot(c, c, 5.0)
    elif f == 6:    # three lightning arms, a turning spiral
        for k in range(3):
            a = k * 2 * math.pi / 3
            pts = [polar(R * 0.12, a), polar(R * 0.45, a + 0.35), polar(R * 0.55, a + 0.05), polar(R * 0.92, a + 0.55)]
            pen.line(pts, w)
        pen.circle(c, c, R * 0.12, w)
    elif f == 7:    # cog with spokes
        teeth = 12
        pts = []
        for i in range(teeth * 4 + 1):
            k = i % 4
            a = 2 * math.pi * (i // 4 + (0, 0.15, 0.5, 0.65)[k]) / teeth
            r = R * (0.7 if k in (0, 3) else 0.86)
            pts.append(polar(r, a))
        pen.line(pts, 3.0)
        pen.circle(c, c, R * 0.3, w)
        for i in range(6):
            a = i * math.pi / 3
            pen.line([polar(R * 0.3, a), polar(R * 0.66, a)], 2.6)
    else:
        pen.circle(c, c, R * 0.6, w)
        pen.dot(c, c, 5)
    return pen.mask()


def glyph_sheet(f, seed=5):
    rng = random.Random(seed * 101 + f)
    sheet = np.zeros((GLYPH * 4, CELL), np.float32)
    for i in range(GLYPHS_PER_FACTION):
        pen = Pen(GLYPH)
        g = glyph(f, rng)
        draw_glyph(pen, f, g, lambda u, v: (GLYPH / 2 + u * GLYPH * 0.26, GLYPH / 2 - v * GLYPH * 0.33), WIDTH[f] * 3.6)
        gx, gy = (i % 4) * GLYPH, (i // 4) * GLYPH
        sheet[gy:gy + GLYPH, gx:gx + GLYPH] = pen.mask()
    return sheet


def halo_cell(f, seed=23):
    """A floating band: two fine rims and twelve small glyphs between them, the centre empty (the stacked rings)."""
    rng = random.Random(seed * 37 + f)
    c = CELL / 2
    R = CELL * 0.47
    r_in = CELL * 0.34
    pen = Pen(CELL)

    def polar(r, a):
        return c + r * math.cos(a), c + r * math.sin(a)

    if f == 2:   # Gravebound: the rims broken like the ground ring's
        for i in range(9):
            a0 = 2 * math.pi * i / 9 + 0.1
            pen.circle(c, c, R, 3.0, a0, a0 + 2 * math.pi / 9 - 0.2)
            pen.circle(c, c, r_in, 2.2, a0 + 0.3, a0 + 2 * math.pi / 9 - 0.05)
    elif f == 7:  # Brassworks: a toothed outer rim
        pen.circle(c, c, R - 3, 2.4)
        for i in range(36):
            a0, a1 = 2 * math.pi * (i - 0.2) / 36, 2 * math.pi * (i + 0.2) / 36
            pen.line([polar(R - 3, a0), polar(R + 2, a0), polar(R + 2, a1), polar(R - 3, a1)], 2.2)
        pen.circle(c, c, r_in, 2.2)
    elif f == 6:  # Stormcallers: segmented rims
        for i in range(12):
            a0 = 2 * math.pi * i / 12 + 0.08
            pen.circle(c, c, R, 3.0, a0, a0 + 2 * math.pi / 12 - 0.16)
        pen.circle(c, c, r_in, 2.0)
    else:
        pen.circle(c, c, R, 3.0)
        pen.circle(c, c, r_in, 2.2)
    n = 12
    rm, hh = (R + r_in) / 2, (R - r_in) / 2 * 0.62
    for i in range(n):
        a = 2 * math.pi * (i + 0.5) / n
        g = glyph(f, rng)

        def place(u, v, a=a):
            r = rm + v * hh
            return c + r * math.cos(a) - u * hh * 0.8 * math.sin(a), c + r * math.sin(a) + u * hh * 0.8 * math.cos(a)
        draw_glyph(pen, f, g, place, WIDTH[f] * 2.4)
    return pen.mask()


def mote_cell(f):
    """The faction's mote, the small shape its motes are made of (64 px): star, ash, ember, petal, pixel, spark, cog."""
    pen = Pen(GLYPH)
    c = GLYPH / 2
    if f == 1:    # Sunforged: a four-point star
        for a in (0, math.pi / 2):
            pen.line([(c - 22 * math.cos(a), c - 22 * math.sin(a)), (c + 22 * math.cos(a), c + 22 * math.sin(a))], 3)
        pen.dot(c, c, 6)
    elif f == 2:  # Gravebound: an ash flake
        pen.line([(c - 10, c - 4), (c + 2, c - 12), (c + 12, c + 3), (c - 2, c + 11), (c - 10, c - 4)], 4)
    elif f == 3:  # Horde: an ember, a teardrop with its tail up
        pen.dot(c, c + 5, 9)
        pen.line([(c, c + 5), (c, c - 18)], 5)
    elif f == 4:  # Verdant: a petal
        pts = [(c + 18 * math.cos(a) * 0.5, c + 18 * math.sin(a)) for a in [2 * math.pi * i / 24 for i in range(25)]]
        pen.line(pts, 4)
        pen.line([(c, c - 14), (c, c + 14)], 2)
    elif f == 5:  # Light Grid: a square pixel
        pen.line([(c - 9, c - 9), (c + 9, c - 9), (c + 9, c + 9), (c - 9, c + 9), (c - 9, c - 9)], 5)
        pen.dot(c, c, 5)
    elif f == 6:  # Stormcallers: a forked spark
        pen.line([(c - 4, c - 22), (c + 6, c - 4), (c - 6, c + 4), (c + 4, c + 22)], 3.4)
    elif f == 7:  # Brassworks: a cog
        pen.circle(c, c, 11, 4)
        for i in range(8):
            a = 2 * math.pi * i / 8
            pen.line([(c + 11 * math.cos(a), c + 11 * math.sin(a)), (c + 18 * math.cos(a), c + 18 * math.sin(a))], 4.5)
    else:
        pen.dot(c, c, 9)
    return pen.mask()


def beam_cell():
    """A column of light across its width (64 x 64): white core, soft edges; the length fade is the vertex alpha's."""
    x = (np.arange(GLYPH, dtype=np.float32) + 0.5) / GLYPH * 2 - 1
    core = np.clip(1 - np.abs(x) / 0.18, 0, 1) ** 1.5
    soft = np.exp(-(x / 0.5) ** 2)
    return np.tile(np.clip(core * 0.9 + soft * 0.55, 0, 1)[None, :], (GLYPH, 1))


def spark_cell():
    """A soft round spark (64 px): the generic glow dot."""
    yy, xx = np.mgrid[0:GLYPH, 0:GLYPH].astype(np.float32)
    d = np.sqrt((xx - GLYPH / 2 + 0.5) ** 2 + (yy - GLYPH / 2 + 0.5) ** 2) / (GLYPH / 2)
    return np.clip(np.exp(-(d / 0.32) ** 2) * 1.2, 0, 1)


def tier_cell():
    """A plain ring (128 px), one clean stroke: the upper tiers of the stacked column."""
    pen = Pen(2 * GLYPH)
    pen.circle(GLYPH, GLYPH, GLYPH * 0.94, 5.0)
    return pen.mask()


def shade_cell():
    """A soft-edged disc (64 px) for the dark underlay: flat to 0.75 of its radius, then easing out."""
    yy, xx = np.mgrid[0:GLYPH, 0:GLYPH].astype(np.float32)
    d = np.sqrt((xx - GLYPH / 2 + 0.5) ** 2 + (yy - GLYPH / 2 + 0.5) ** 2) / (GLYPH / 2)
    return np.clip((1 - d) / 0.25, 0, 1)


ROW_HALO = 2 * CELL + GLYPH * 4   # y 768: the halo bands (the lowest tier, with glyphs)
ROW_FX = ROW_HALO + CELL          # y 1024, 128 high: tier ring (128) | mote, beam (64) over spark, shade (64)


def atlas():
    out = np.zeros((ROW_FX + 2 * GLYPH, CELL * len(FACTIONS), 4), np.uint8)
    for f, (_, hue, acc) in enumerate(FACTIONS):
        x = f * CELL
        out[0:CELL, x:x + CELL] = bake(ring_cell(f), hue, acc)
        out[CELL:2 * CELL, x:x + CELL] = bake(sigil_cell(f), hue, acc)
        out[2 * CELL:ROW_HALO, x:x + CELL] = bake(glyph_sheet(f), hue, acc, glow_r=3.0)
        out[ROW_HALO:ROW_FX, x:x + CELL] = bake(halo_cell(f), hue, acc, glow_r=5.0)
        out[ROW_FX:ROW_FX + 2 * GLYPH, x:x + 2 * GLYPH] = bake(tier_cell(), hue, acc, glow_r=3.0)
        y0, y1 = ROW_FX, ROW_FX + GLYPH
        out[y0:y1, x + 2 * GLYPH:x + 3 * GLYPH] = bake(mote_cell(f), hue, acc, glow_r=2.0 if f == 7 else 4.0)
        out[y0:y1, x + 3 * GLYPH:x + 4 * GLYPH] = bake(beam_cell(), hue, acc, glow_r=0.1, glow=0.0)
        out[y1:y1 + GLYPH, x + 2 * GLYPH:x + 3 * GLYPH] = bake(spark_cell(), hue, acc, glow_r=2.0, glow=0.3)
        sh = shade_cell()
        # white: the vertex colour tints it (black, or Gravebound's violet)
        out[y1:y1 + GLYPH, x + 3 * GLYPH:x + 4 * GLYPH] = np.dstack([np.ones_like(sh)] * 3 + [sh]) * 255
        if GAIN[f] != 1:   # every light cell but the underlay's disc
            col = out[:, x:x + CELL]
            keep = col[y1:y1 + GLYPH, 3 * GLYPH:4 * GLYPH, 3].copy()
            col[..., 3] = (col[..., 3].astype(np.float32) * GAIN[f]).astype(np.uint8)
            col[y1:y1 + GLYPH, 3 * GLYPH:4 * GLYPH, 3] = keep
    return out


# ---------------------------------------------------------------- concept stills (not shipped)

def grass(w, h, seed=3):
    rng = np.random.default_rng(seed)
    base = np.array([0.30, 0.46, 0.20], np.float32)
    n = rng.random((h // 8 + 1, w // 8 + 1)).astype(np.float32)
    n = np.asarray(Image.fromarray((n * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), np.float32) / 255
    fine = rng.random((h, w)).astype(np.float32)
    img = base[None, None, :] * (0.78 + 0.32 * n[..., None]) * (0.9 + 0.18 * fine[..., None])
    # dirt patches
    d = rng.random((h // 40 + 1, w // 40 + 1)).astype(np.float32)
    d = np.asarray(Image.fromarray((d * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), np.float32) / 255
    dirt = np.clip((d - 0.62) * 6, 0, 1)[..., None]
    img = img * (1 - dirt) + np.array([0.42, 0.33, 0.22], np.float32) * (0.85 + 0.2 * fine[..., None]) * dirt
    return np.clip(img, 0, 1)


def add_sprite(canvas, rgba, cx, cy, size, angle=0.0, alpha=1.0, sweep=1.0):
    """Additive blend of an atlas cell, rotated, scaled to `size` px; sweep < 1 shows only that share of the angle."""
    im = Image.fromarray(rgba)
    im = im.rotate(math.degrees(angle), resample=Image.BICUBIC).resize((size, size), Image.LANCZOS)
    a = np.asarray(im, np.float32) / 255
    if sweep < 1:
        yy, xx = np.mgrid[0:size, 0:size]
        ang = (np.arctan2(yy - size / 2, xx - size / 2) + math.pi / 2) % (2 * math.pi)
        a[..., 3] *= np.clip((sweep * 2 * math.pi - ang) / 0.25, 0, 1)
    x0, y0 = int(cx - size / 2), int(cy - size / 2)
    h, w = canvas.shape[:2]
    xs, ys = max(0, x0), max(0, y0)
    xe, ye = min(w, x0 + size), min(h, y0 + size)
    if xe <= xs or ye <= ys:
        return
    sub = a[ys - y0:ye - y0, xs - x0:xe - x0]
    canvas[ys:ye, xs:xe] += sub[..., :3] * sub[..., 3:4] * alpha


def darken_disc(canvas, cx, cy, r, k=0.25):
    h, w = canvas.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
    canvas *= (1 - k * np.clip(1.2 - d, 0, 1)[..., None])


def unit_dot(canvas, cx, cy, r, col):
    h, w = canvas.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    m = ((xx - cx) ** 2 + (yy - cy) ** 2) < r * r
    canvas[m] = rgb(col)
    ring = (((xx - cx) ** 2 + (yy - cy) ** 2) < (r + 1.5) ** 2) & ~m
    canvas[ring] = 0.08


def cell(at, f, row):
    return at[row * CELL:(row + 1) * CELL, f * CELL:(f + 1) * CELL]


def glyph_cell(at, f, i):
    y = 2 * CELL + (i // 4) * GLYPH
    x = f * CELL + (i % 4) * GLYPH
    return at[y:y + GLYPH, x:x + GLYPH]


def circle_at(canvas, at, f, cx, cy, px_per_block, t, life=1.5, radius=1.6):
    """One rune circle as RuneFxClient draws it at time t (s) of its life."""
    size = int(2 * radius / 0.94 * px_per_block)   # the rim sits at 0.47 of the cell
    draw_in = min(1.0, t / 0.3)
    fade = max(0.0, min(1.0, (life - t) / 0.35))
    a = min(draw_in * 1.2, 1.0) * fade
    grow = 1 + 0.12 * (1 - fade)
    darken_disc(canvas, cx, cy, size * 0.5, 0.22 * a)
    add_sprite(canvas, cell(at, f, 0), cx, cy, int(size * grow), angle=t * 0.06 * 2 * math.pi, alpha=a * 0.95,
               sweep=draw_in)
    add_sprite(canvas, cell(at, f, 1), cx, cy, int(size * 0.62 * grow), angle=-t * 0.15 * 2 * math.pi,
               alpha=a * 0.9 * min(1, max(0, (t - 0.12) / 0.2)))
    if t > 0.2:
        for k in range(4):
            ang = t * 0.1 * 2 * math.pi + k * math.pi / 2
            rr = radius * 0.8 * px_per_block
            lift = (0.5 + 0.3 * math.sin(t * 3 + k)) * px_per_block * 0.6 + (1 - fade) * px_per_block * 0.6
            gx, gy = cx + rr * math.cos(ang), cy + rr * math.sin(ang) * 0.75 - lift
            add_sprite(canvas, glyph_cell(at, f, k), gx, gy, int(px_per_block * 0.55), alpha=a * 0.95)


def label(img, text, xy, size=1):
    d = ImageDraw.Draw(img)
    d.text((xy[0] + 1, xy[1] + 1), text, fill=(0, 0, 0))
    d.text(xy, text, fill=(255, 255, 255))


def mocks(at, outdir):
    os.makedirs(outdir, exist_ok=True)
    names = ['Sunforged', 'Gravebound', 'Horde', 'Verdant', 'Stormcallers', 'Brassworks', 'Light Grid']
    ids = [1, 2, 3, 4, 6, 7, 5]
    # A: the seven alphabets side by side at zoom 15 (72 px/block), mid-cast
    W, H = 1680, 420
    can = grass(W, H, 1)
    for i, f in enumerate(ids):
        cx, cy = 120 + i * 240, 200
        circle_at(can, at, f, cx, cy, 72, 0.8)
        unit_dot(can, cx, cy, 14, 0xD0C8B8)
    img = Image.fromarray((np.clip(can, 0, 1) * 255).astype(np.uint8))
    for i, n in enumerate(names):
        label(img, n, (70 + i * 240, 380))
    label(img, 'A - one rune alphabet per faction (zoom 15, mid-cast)', (12, 10))
    img.save(os.path.join(outdir, 'rune_concept_A_alphabets.png'))
    # B: timing strip for the Stormcallers - draw-in sweep, spin, fade (1.5 s cast)
    times = [0.05, 0.15, 0.3, 0.75, 1.2, 1.42]
    W, H = 1440, 300
    can = grass(W, H, 2)
    for i, t in enumerate(times):
        cx = 120 + i * 240
        circle_at(can, at, 6, cx, 150, 64, t)
        unit_dot(can, cx, 150, 12, 0xD0C8B8)
    img = Image.fromarray((np.clip(can, 0, 1) * 255).astype(np.uint8))
    for i, t in enumerate(times):
        label(img, f't = {t:.2f} s', (85 + i * 240, 270))
    label(img, 'B - one cast, 1.5 s: sweep in 0.3 s, ring and sigil counter-turn, glyphs rise, fade 0.35 s', (12, 10))
    img.save(os.path.join(outdir, 'rune_concept_B_timing.png'))
    # C: real RTS scale - zoom 30 (36 px/block), 1080p crop, two armies' casters casting at once
    W, H = 1280, 720
    can = grass(W, H, 4)
    rng = random.Random(9)
    for k in range(10):
        f = 4 if k < 5 else 2
        cx = (200 + rng.random() * 300) if k < 5 else (780 + rng.random() * 300)
        cy = 150 + rng.random() * 420
        circle_at(can, at, f, cx, cy, 36, 0.3 + rng.random() * 1.0)
    for k in range(30):
        unit_dot(can, 150 + rng.random() * 980, 100 + rng.random() * 520, 6, 0x3A6FE0 if k % 2 else 0xE0503A)
    img = Image.fromarray((np.clip(can, 0, 1) * 255).astype(np.uint8))
    label(img, 'C - zoom 30, true scale: Verdant (left) vs Gravebound (right) casters, both greens on a lawn', (12, 10))
    img.save(os.path.join(outdir, 'rune_concept_C_zoom30.png'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mock', default='', help='also write three concept stills into this folder')
    ap.add_argument('--out', default=OUT)
    args = ap.parse_args()
    at = atlas()
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    Image.fromarray(at, 'RGBA').save(args.out, optimize=True)
    print('wrote', os.path.abspath(args.out), at.shape)
    if args.mock:
        mocks(at, args.mock)
        print('mocks in', args.mock)


if __name__ == '__main__':
    main()
