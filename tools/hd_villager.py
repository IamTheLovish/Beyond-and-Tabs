#!/usr/bin/env python3
"""HD skins for units on the shared villager model (VillagerUnitModel: 64x64 box UV) through the generated bodies'
pipeline (tools/dsl_to_game.py's Painter, tools/materials.py, tools/unit_light.py) - so the Sunforged T1/T2 units that
still wore 64x64 paint (tools/gen_hd_skins.py) stand beside the Squire, Levy, Halberdier, Medic and Paladin in the
same materials, the same baked light and the same LabPBR maps (lovish, Oct 21: "we can improve the other skins as well").

The model and its UVs are fixed (one Java model is shared by many units), so only the paint changes: each texture is
8x (512x512, 8 texels per model pixel - DENSITY), every box face is painted in layers (a base material, then patches:
a breastplate over a gambeson, a tabard panel, a belt) with Painter.face - a patch takes its baked light from its place
on the whole face (light_rect) and rims only the edges that are really edges (rims=(top, bottom)) - and then the fine
marks are drawn at texel scale (eyes, brows, beard edges, an embossed sun, buttons, rivets).

Writes textures/entities/<unit>_unit.png, _team.png (TeamColourLayer), _n.png, _s.png (LabPBR for shader packs) and
the HUD portrait (textures/mobheads/<unit>.png, built from the finished skin), and with --sheet a review sheet:
front, back, side and top-down under one low sun (game_preview's LIGHT), team in two owner colours.

    python3 tools/hd_villager.py [--only evoker,field_mason] [--sheet design/skins/sunforged_hd_r1.png]

Rules kept: every 8x8 texel block is all painted or all empty (whole model pixels - the mip test in
UnitSkinGameTests); the _s blue channel stays below 65 (no subsurface glow) and its alpha is 255 (no emission).
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dsl_to_game as d2g  # noqa: E402
import materials  # noqa: E402
import unit_light  # noqa: E402
import hd_skinkit as K  # noqa: E402

D = d2g.DENSITY                      # 8 texels per model pixel
TEX = 64                             # the model's texture size (VillagerUnitModel: LayerDefinition 64x64)
ENT, HEADS = K.ENT, K.HEADS
# hd_skinkit names the box sides by the unit's own right/left; the Painter / BodyLight by the axis
AXIS = {"top": "top", "bottom": "bottom", "right": "west", "front": "north", "left": "east", "back": "south"}
SIDES = ("right", "front", "left", "back")
ALLF = ("top", "bottom") + SIDES
SPEC_B_MAX = 64                      # LabPBR _s blue >= 65 is subsurface: skin and wool glowed at the edges


# ------------------------------------------------------------------ the model (VillagerUnitModel.createBodyLayer)
def _box(part, name, x, y, z, w, h, d, uv, g=0.0, mirror=False):
    part.boxes.append((x, y, z, w, h, d, g, uv))
    part.meta = getattr(part, "meta", []) + [(name, mirror)]


def model(arms="crossed"):
    """The part tree (tools/dsl_to_game.Part, model space: y down, 24 = the feet) as the game builds it. arms:
    'crossed' (idle: the arms bar) or 'free' (working, casting: the two hanging arms)."""
    P = d2g.Part
    root = P("root", (0, 0, 0), (0, 0, 0))
    head = root.child("head")
    _box(head, "head", -4, -10, -4, 8, 10, 8, (0, 0))
    nose = head.child("nose", (0, -2, 0))
    _box(nose, "nose", -1, -1, -6, 2, 4, 2, (24, 0))
    body = root.child("body")
    _box(body, "body", -4, 0, -3, 8, 12, 6, (16, 20))
    jacket = body.child("jacket")
    _box(jacket, "jacket", -4, 0, -3, 8, 18, 6, (0, 38), g=0.5)
    if arms == "crossed":
        a = root.child("arms", (0, 3, -1), (-0.75, 0, 0))
        _box(a, "arm_up", -8, -2, -2, 4, 8, 4, (44, 22))
        _box(a, "arm_mid", -4, 2, -2, 8, 4, 4, (40, 38))
        ls = a.child("left_shoulder")
        _box(ls, "arm_up", 4, -2, -2, 4, 8, 4, (44, 22), mirror=True)
    else:
        ra = root.child("right_arm", (-5, 2, 0))
        _box(ra, "arm", -3, -2, -2, 4, 12, 4, (40, 46))
        la = root.child("left_arm", (5, 2, 0))
        _box(la, "arm", -1, -2, -2, 4, 12, 4, (40, 46), mirror=True)
    rl = root.child("right_leg", (-2, 12, 0))
    _box(rl, "leg", -2, 0, -2, 4, 12, 4, (0, 22))
    ll = root.child("left_leg", (2, 12, 0))
    _box(ll, "leg", -2, 0, -2, 4, 12, 4, (0, 22), mirror=True)
    return root


def _walk(p, M, t, out):
    M2 = M @ np.array(d2g.mat_zyx(p.rot))
    t2 = t + M @ np.array(p.offset, dtype=float)
    for k, (x, y, z, w, h, dd, g, uv) in enumerate(p.boxes):
        name, mirror = p.meta[k]
        out.append((name, mirror, M2, t2, (x - g, y - g, z - g, x + w + g, y + h + g, z + dd + g), (w, h, dd), uv))
    for c in p.children:
        _walk(c, M2, t2, out)


def boxes(root):
    out = []
    _walk(root, np.eye(3), np.zeros(3), out)
    return out


def corners_dsl(b):
    """A posed box's 8 corners in DSL space (y up), BodyLight's order: index x*4 + y*2 + z, lo = 0 (DSL lo y = the
    model's hi y)."""
    _n, _m, M, t, (x0, y0, z0, x1, y1, z1), _s, _uv = b
    pts = []
    for cx in (x0, x1):
        for cy in (y1, y0):                     # DSL low y first
            for cz in (z0, z1):
                v = t + M @ np.array([cx, cy, cz])
                pts.append((v[0], 24.0 - v[1], v[2]))
    return pts


def faces_for_preview(root):
    """game_preview.render's faces, with ModelPart.Cube's mirror (the left limbs: x swapped, polygons reversed)."""
    out = []
    for (name, mirror, M, t, (x0, y0, z0, x1, y1, z1), (w, h, dd), (u, v)) in boxes(root):
        if mirror:
            x0, x1 = x1, x0

        def V(a, b, c):
            return t + M @ np.array([a, b, c])
        v1, v2, v3, v4 = V(x0, y0, z0), V(x1, y0, z0), V(x1, y1, z0), V(x0, y1, z0)
        v5, v6, v7, v8 = V(x0, y0, z1), V(x1, y0, z1), V(x1, y1, z1), V(x0, y1, z1)
        u0, u1, u2, u3 = u, u + dd, u + dd + w, u + dd + w + w
        u4, u5 = u + dd + w + dd, u + dd + w + dd + w
        w0, w1, w2 = v, v + dd, v + dd + h
        quads = [((v6, v5, v1, v2), (u1, w0, u2, w1)), ((v3, v4, v8, v7), (u2, w1, u3, w0)),
                 ((v1, v5, v8, v4), (u0, w1, u1, w2)), ((v2, v1, v4, v3), (u1, w1, u2, w2)),
                 ((v6, v2, v3, v7), (u2, w1, u4, w2)), ((v5, v6, v7, v8), (u4, w1, u5, w2))]
        if mirror:   # the west / east strips trade places, each face's u runs the other way
            quads[2], quads[4] = (quads[2][0], quads[4][1]), (quads[4][0], quads[2][1])
        for verts, (a0, b0, a1, b1) in quads:
            uvs = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
            if mirror:
                uvs = [(a0, b0), (a1, b0), (a1, b1), (a0, b1)]
                verts = verts[::-1]
                uvs = uvs[::-1]
            out.append((verts, uvs))
    return out


# ------------------------------------------------------------------ the painter
class Skin:
    def __init__(self, name, palette, team=()):
        self.name = name
        self.size = (TEX * D, TEX * D)
        self.imgs = {k: Image.new("RGBA", self.size, (0, 0, 0, 0)) for k in ("base", "team", "glow")}
        self.paint = {k: d2g.Painter(self.imgs[k], D, keep_light=(k == "base"), mask=(k == "team"))
                      for k in ("base", "team")}
        self.pbr = d2g.PBRMaps(self.size)
        # an entry may be (colour, k): k scales the game value - measured from the top-down view, where a top face
        # takes the full sky and a palette judged from the side reads a stop or two too pale (steel above all)
        scale = {k: (v[1] if isinstance(v, tuple) else 1.0) for k, v in palette.items()}
        raw = {k: d2g.hex_rgb(v[0] if isinstance(v, tuple) else v) for k, v in palette.items()}
        self.raw = raw
        self.pal = {k: tuple(d2g.clamp8(c * scale[k]) for c in d2g.game_value(d2g.realistic_colour(k, rgb)))
                    for k, rgb in raw.items()}
        self.team = set(team)
        hi, lo = d2g.TEAM_DYE
        self.team_grey = {c: ((hi,) * 3 if "dark" not in c else (lo,) * 3) for c in team}
        self.seed = sum(ord(c) * (i + 1) for i, c in enumerate(name))
        self.n = 0
        # baked light: the idle body (arms crossed) for everything but the hanging arms, which get their own
        idle, free = boxes(model("crossed")), boxes(model("free"))
        self.light_idle = unit_light.BodyLight([corners_dsl(b) for b in idle], [True] * len(idle))
        self.light_free = unit_light.BodyLight([corners_dsl(b) for b in free], [True] * len(free))
        self.where = {}
        for bl, src in ((self.light_idle, idle), (self.light_free, free)):
            for k, b in enumerate(src):
                if b[0] not in self.where and not b[1]:
                    ys = [c[1] for c in corners_dsl(b)]
                    self.where[b[0]] = (bl, k, max(ys), min(ys), b[5])
        self.where["arm"] = next((self.light_free, k, max(c[1] for c in corners_dsl(b)), min(c[1] for c in corners_dsl(b)), b[5])
                                 for k, b in enumerate(free) if b[0] == "arm" and not b[1])

    # -- geometry of the atlas
    @staticmethod
    def face_rect(box, f):
        """(u, v, w, h) of a box face in model px."""
        return K.faces(K.VILLAGER[box])[f]

    def layer(self, box, faces, col, rect=None, rims=True, open_=False, seed=None, span_group=None):
        """Paint colour `col` on the given faces of a box, over the whole face or rect = (i0, j0, i1, j1) model px
        within it (floats; j down from the face's top edge). rims: True / False / (top, bottom) - the lit upper rim
        and the seam under, only where the patch has a real edge. open_: the patch's i borders are not edges (a
        stretch of the same surface). span_group: (top y, bottom y) DSL of the form the face shades along (a robe:
        the whole jacket), default the box itself."""
        if isinstance(faces, str):
            faces = {"sides": SIDES, "all": ALLF}.get(faces, (faces,))
        bl, cube_n, ytop, ybot, (w, h, d) = self.where[box]
        mat = materials.material_for(col)
        style = materials.style_for(col)
        legacy = d2g.material_of(col)
        rgb = self.pal[col]
        for f in faces:
            u, v, fw, fh = self.face_rect(box, f)
            i0, j0, i1, j1 = rect if rect is not None else (0, 0, fw, fh)
            i0, i1 = max(0.0, i0), min(float(fw), i1)
            j0, j1 = max(0.0, j0), min(float(fh), j1)
            if i1 <= i0 or j1 <= j0:
                continue
            kind = "top" if f == "top" else ("bottom" if f == "bottom" else "side")
            span = None
            if kind != "bottom":
                gtop, gbot = span_group or (ytop, ybot)
                if kind == "side":
                    span = (ytop - j0 * (ytop - ybot) / fh, ytop - j1 * (ytop - ybot) / fh, gtop, gbot)
                else:
                    span = (ytop, ytop, gtop, gbot)
            if isinstance(rims, tuple):
                rt, rb = rims
            else:
                rt = rb = bool(rims) and h >= d2g.RIM_MIN_HEIGHT
            if kind != "side":
                rt, rb = True, True      # a top face: the plain lit top (rims=False there means a slice's ledge)
            sd = seed if seed is not None else self.seed * 31 + self.n
            self.n += 1
            light = (bl, cube_n, AXIS[f])
            lr = (u, v, u + fw, v + fh)
            a = (u + i0, v + j0, u + i1, v + j1)
            panel = (d if f in ("front", "back") else w) <= d2g.PANEL_THIN
            kw = dict(span=span, rims=(rt, rb), mat=mat, seed=sd, light=light, open_i=open_ and kind == "side",
                      open_j=False, style=style, panel=panel, face_name=AXIS[f], cube=(w, h, d), light_rect=lr)
            self.paint["base"].face(*a, rgb, kind, legacy, _Rng(sd), pbr=self.pbr, **kw)
            if col in self.team:
                self.paint["team"].face(*a, self.team_grey[col], kind, legacy, _Rng(sd), **kw)
            else:   # a patch over team cloth takes the team off there
                x0, y0, x1, y1 = self.paint["base"].rect(*a)
                self.imgs["team"].paste((0, 0, 0, 0), (x0, y0, x1, y1))

    # -- texel marks
    def texels(self, box, f, rect):
        """Texel coordinates (x, y arrays) and face-local model px (fx, fy) of rect (i0, j0, i1, j1) on a face."""
        u, v, fw, fh = self.face_rect(box, f)
        i0, j0, i1, j1 = rect
        x0, x1 = int(math.floor((u + i0) * D)), int(math.ceil((u + i1) * D))
        y0, y1 = int(math.floor((v + j0) * D)), int(math.ceil((v + j1) * D))
        ys, xs = np.mgrid[y0:y1, x0:x1]
        return xs, ys, (xs + 0.5) / D - u, (ys + 0.5) / D - v

    def mark(self, box, f, rect, fn, spec=None, height=None):
        """fn(fx, fy, rgb float array (n, 3)) -> (weight (n,), rgb (n, 3)): blends the paint toward a colour per
        texel (fx, fy: model px on the face). spec: a material for the _s map where weight > 0.5; height: added to
        the relief where it paints (scaled by weight)."""
        xs, ys, fx, fy = self.texels(box, f, rect)
        base = np.asarray(self.imgs["base"]).copy()
        cur = base[ys, xs, :3].astype(np.float64)
        wgt, col = fn(fx, fy, cur)
        wgt = np.clip(np.broadcast_to(wgt, fx.shape), 0, 1)
        out = cur * (1 - wgt[..., None]) + np.asarray(col, dtype=np.float64) * wgt[..., None]
        on = (base[ys, xs, 3] > 0) & (wgt > 0.003)
        base[ys[on], xs[on], :3] = np.clip(np.rint(out[on]), 0, 255).astype(np.uint8)
        self.imgs["base"] = Image.fromarray(base, "RGBA").copy()
        self.paint["base"].px = self.imgs["base"].load()
        if spec is not None or height is not None:
            strong = on & (wgt > 0.5)
            if spec is not None:
                self.pbr.spec[ys[strong], xs[strong]] = materials.specular(spec)
            if height is not None:
                self.pbr.height[ys[on], xs[on]] = np.clip(self.pbr.height[ys[on], xs[on]] + height * wgt[on], 0, 1)
        team = np.asarray(self.imgs["team"]).copy()
        cut = on & (wgt > 0.5) & (team[ys, xs, 3] > 0)
        if cut.any():
            team[ys[cut], xs[cut]] = 0
            self.imgs["team"] = Image.fromarray(team, "RGBA").copy()
            self.paint["team"].px = self.imgs["team"].load()

    def shade(self, box, f, rect, fn):
        """Multiply the paint (and the team mask's grey) by fn(fx, fy) - shadows and occlusion at texel scale."""
        xs, ys, fx, fy = self.texels(box, f, rect)
        k = np.broadcast_to(fn(fx, fy), fx.shape)
        for key in ("base", "team"):
            a = np.asarray(self.imgs[key]).copy()
            on = a[ys, xs, 3] > 0
            px = a[ys, xs, :3].astype(np.float64) * k[..., None]
            a[ys[on], xs[on], :3] = np.clip(np.rint(px[on]), 0, 255).astype(np.uint8)
            self.imgs[key] = Image.fromarray(a, "RGBA").copy()
            self.paint[key].px = self.imgs[key].load()

    # -- output
    def seal(self):
        """Every 8x8 block all painted or all empty (the mip test): the empty texels of a half-painted block - where a
        patch took the team off - become alpha 1 in the block's mean colour (cutout drops them; the mips weigh them
        at nothing)."""
        for key in ("base", "team"):
            a = np.asarray(self.imgs[key]).copy()
            H, W = a.shape[:2]
            blk = a.reshape(H // 8, 8, W // 8, 8, 4)
            on = blk[..., 3] > 0
            cnt = on.sum(axis=(1, 3))
            mixed = (cnt > 0) & (cnt < 64)
            if not mixed.any():
                continue
            mean = (blk[..., :3] * on[..., None]).sum(axis=(1, 3)) / np.maximum(1, cnt)[..., None]
            for by, bx in np.argwhere(mixed):
                b = blk[by, :, bx, :, :]
                hole = b[..., 3] == 0
                b[hole, :3] = np.rint(mean[by, bx]).astype(np.uint8)
                b[hole, 3] = 1
            self.imgs[key] = Image.fromarray(blk.reshape(H, W, 4), "RGBA").copy()
            self.paint[key].px = self.imgs[key].load()

    def finish(self):
        n, s = self.pbr.images()
        s = np.asarray(s).copy()
        s[..., 2] = np.minimum(s[..., 2], SPEC_B_MAX)
        s[..., 3] = 255
        base = np.asarray(self.imgs["base"])
        opaque = base[..., 3] > 0
        n = np.asarray(n).copy()
        n[~opaque] = 0
        s[~opaque] = 0
        return Image.fromarray(n, "RGBA"), Image.fromarray(s, "RGBA")

    def save(self, out_dir=ENT):
        n, s = self.finish()
        stem = os.path.join(out_dir, f"{self.name}_unit")
        self.imgs["base"].save(stem + ".png", optimize=True)
        self.imgs["team"].save(stem + "_team.png", optimize=True)
        n.save(stem + "_n.png", optimize=True)
        s.save(stem + "_s.png", optimize=True)
        problems = check(self)
        if problems:
            raise SystemExit(f"{self.name}: " + "; ".join(problems))


class _Rng:
    """random.Random's .random() from a seed, without importing random's global state into the painter."""
    def __init__(self, seed):
        self.s = (seed * 2654435761) & 0xFFFFFFFF

    def random(self):
        self.s = (self.s * 1103515245 + 12345) & 0x7FFFFFFF
        return self.s / 0x7FFFFFFF


def check(sk):
    """The rules the game test and the shader packs hold us to."""
    out = []
    for key in ("base", "team"):
        a = np.asarray(sk.imgs[key])[..., 3]
        H, W = a.shape
        blk = (a > 0).reshape(H // 8, 8, W // 8, 8).sum(axis=(1, 3))
        mixed = np.argwhere((blk > 0) & (blk < 64))
        if len(mixed):
            y, x = mixed[0]
            out.append(f"{key}: half-painted 8x8 block at texel {x * 8},{y * 8}")
    n, s = sk.finish()
    s = np.asarray(s)
    if (s[..., 2] >= 65).any():
        out.append("_s blue >= 65 (subsurface)")
    if (s[..., 3] != 255)[np.asarray(sk.imgs["base"])[..., 3] > 0].any():
        out.append("_s alpha below 255 (emissive)")
    return out


# ------------------------------------------------------------------ shared marks (texel scale)
def _sm(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def _noise(seed, x, y, scale):
    return materials._value_v(np, seed, x * D, y * D, scale * D)


def face_features(sk, skin, eye=(70, 92, 112), brow=(70, 60, 50), age=0.0, socket=0.74):
    """The face on the head's front (8 x 10 model px; the nose box covers columns 3-5 from row 7 down): a brow ridge
    and eye sockets in shade - from the RTS camera the face reads as a lit forehead over two dark hollows, not as two
    painted dots - small eyes with a catch-light, brows, cheeks warmer, temples cooler, age lines."""
    S = np.array(skin, dtype=np.float64)

    def sockets(fx, fy):
        k = np.ones_like(fx)
        for cx in (2.15, 5.85):
            r = ((fx - cx) / 1.35) ** 2 + ((fy - 4.45) / 0.85) ** 2
            k *= 1 - (1 - socket) * (1 - _sm(0.35, 1.0, r))
        k *= 1 - 0.10 * (1 - _sm(0.0, 0.5, np.abs(fy - 3.65)))      # under the brow ridge
        k *= 1 + 0.07 * (1 - _sm(0.0, 1.6, np.abs(fy - 1.6)))       # the forehead catches the light
        k *= 1 - 0.10 * (_sm(2.6, 4.0, np.abs(fx - 4.0)) * _sm(3.0, 6.0, fy))   # the jaw turns away
        return k
    sk.shade("head", "front", (0, 0, 8, 10), sockets)

    def cheeks(fx, fy, cur):
        w = np.zeros_like(fx)
        for cx in (1.6, 6.4):
            w = np.maximum(w, 0.22 * (1 - _sm(0.3, 1.2, np.hypot((fx - cx) / 1.1, (fy - 6.0) / 0.8))))
        return w, S * np.array([1.08, 0.86, 0.80])
    sk.mark("head", "front", (0, 4, 8, 8), cheeks)

    def eyes(fx, fy, cur):
        w = np.zeros_like(fx)
        col = np.broadcast_to(np.array([200, 192, 180.0]), fx.shape + (3,)).copy()
        for cx in (2.2, 5.8):
            white = ((fx - cx) / 0.62) ** 2 + ((fy - 4.55) / 0.26) ** 2 < 1
            iris = np.hypot(fx - (cx + (0.08 if cx < 4 else -0.08)), fy - 4.55) < 0.27
            pupil = np.hypot(fx - (cx + (0.08 if cx < 4 else -0.08)), fy - 4.55) < 0.12
            lid = white & (fy < 4.4)
            w = np.where(white, 0.85, w)
            col[iris] = eye
            col[pupil] = (22, 18, 18)
            col[lid] = S * 0.55
            catch = np.hypot(fx - (cx + 0.02), fy - 4.47) < 0.07
            col[catch] = (236, 232, 222)
        return w, col
    sk.mark("head", "front", (1, 4, 7, 5.2), eyes)
    B = np.array(brow, dtype=np.float64)

    def brows(fx, fy, cur):
        w = np.zeros_like(fx)
        for cx, tilt in ((2.15, 0.12), (5.85, -0.12)):
            yy = 3.62 + tilt * (fx - cx) - 0.08 * (1 - ((fx - cx) / 0.95) ** 2)
            inside = np.abs(fx - cx) < 0.95
            w = np.where(inside, np.maximum(w, 0.9 * (1 - _sm(0.10, 0.22, np.abs(fy - yy)))), w)
        return w, B
    sk.mark("head", "front", (1, 3, 7, 4.2), brows)
    if age > 0:
        def lines(fx, fy, cur):
            w = np.zeros_like(fx)
            for yy in (1.3, 1.9):
                w = np.maximum(w, age * 0.35 * (1 - _sm(0.0, 0.08, np.abs(fy - yy - 0.1 * np.sin(fx * 1.3))))
                               * (np.abs(fx - 4) < 2.4))
            for cx in (0.9, 7.1):    # crow's feet
                w = np.maximum(w, age * 0.3 * (1 - _sm(0.0, 0.07, np.abs(fy - 4.6 - 0.35 * (fx - cx))))
                               * (np.abs(fx - cx) < 0.5))
            return w, S * 0.68
        sk.mark("head", "front", (0, 0, 8, 6), lines)


def beard(sk, col, style="full", seed=0):
    """Beard and moustache as hair (the 'beard' colour, painted with the hair material) cut by a ragged edge, below
    the cheekbones; the nose box hides the mouth, so the beard frames it."""
    if style == "full":
        sk.layer("head", "front", "beard", rect=(0, 6.6, 8, 10), rims=False, open_=True)
        sk.layer("head", "right", "beard", rect=(3.5, 6.0, 8, 10), rims=False, open_=True)     # i runs back -> front
        sk.layer("head", "left", "beard", rect=(0, 6.0, 4.5, 10), rims=False, open_=True)      # i runs front -> back
        sk.layer("head", "bottom", "beard", rims=False)
        top = lambda fx: 6.6 + 0.45 * np.abs(fx - 4) / 4
    elif style == "short":
        sk.layer("head", "front", "beard", rect=(0, 7.4, 8, 10), rims=False, open_=True)
        sk.layer("head", "bottom", "beard", rims=False)
        top = lambda fx: 7.4 + 0.2 * np.abs(fx - 4) / 4
    else:   # moustache and stubble
        top = None
    # the ragged upper edge back toward skin, and skin showing at the cheeks
    if top is not None:
        def edge(fx, fy, cur):
            t = top(fx) + 0.35 * _noise(seed + 7, fx, 0 * fx, 0.25)
            w = 1 - _sm(t - 0.05, t + 0.55, fy)
            return w * 0.9, np.array(sk.pal["skin"], dtype=np.float64) * 0.86
        sk.mark("head", "front", (0, 6, 8, 8.4), edge)
        # the hair's own shade under the chin and the lit upper strands
        sk.shade("head", "front", (0, 8.6, 8, 10), lambda fx, fy: 0.9 - 0.08 * _sm(8.6, 10, fy))
    if style in ("moustache", "full", "short"):
        Bc = np.array(sk.pal["beard"], dtype=np.float64)

        def stache(fx, fy, cur):
            w = np.zeros_like(fx)
            for side in (-1, 1):
                cx = 4 + side * 1.55
                yy = 7.05 + 0.32 * np.abs(fx - 4) - 0.2
                w = np.maximum(w, (np.abs(fx - cx) < 0.75) * (1 - _sm(0.18, 0.34, np.abs(fy - yy))))
            return w * 0.95, Bc * 0.92
        sk.mark("head", "front", (1.8, 6.5, 6.2, 8.2), stache)
    if style == "moustache":
        def stubble(fx, fy, cur):
            n = _noise(seed + 3, fx * 3.1, fy * 3.1, 0.06)
            return 0.30 * _sm(6.9, 7.6, fy) * (n > -0.1), np.array(sk.pal["beard"], dtype=np.float64) * 0.9
        sk.mark("head", "front", (0, 6.8, 8, 10), stubble)
        sk.mark("head", "right", (4, 6.5, 8, 10), stubble)
        sk.mark("head", "left", (0, 6.5, 4, 10), stubble)


def nose(sk):
    """The villager nose (2 x 4 x 2): skin, the tip and the nostrils' shade under it, lit along its bridge."""
    sk.layer("nose", "all", "skin", rims=False)
    sk.shade("nose", "front", (0, 0, 2, 4), lambda fx, fy: (1.06 - 0.16 * _sm(2.4, 4.0, fy))
             * (1 - 0.10 * _sm(0.55, 1.0, np.abs(fx - 1))))
    sk.shade("nose", "bottom", (0, 0, 2, 2), lambda fx, fy: 0.62 + 0 * fx)


def hair(sk, faces_rows, col="hair"):
    for f, rect in faces_rows:
        sk.layer("head", f, col, rect=rect, rims=False, open_=True)


def emboss_disc(sk, box, f, cx, cy, r, col, rays=8, ray_len=0.55, inlay=None):
    """A raised gilt sun (the Sunforged badge) at texel scale: a domed disc lit from the upper left with a dark
    contact line round it, and rays. Paint + relief + the gold specular."""
    G = np.array(sk.pal[col], dtype=np.float64)

    def fn(fx, fy, cur):
        dx, dy = fx - cx, fy - cy
        rr = np.hypot(dx, dy)
        ang = np.arctan2(dy, dx)
        ray = (np.cos(ang * rays) > 0.55) & (rr < r + ray_len) & (rr >= r * 0.95)
        disc = rr < r
        lit = 1.0 + 0.35 * np.clip(-(dx + dy) / (r * 1.4), -1, 1) * disc
        col_ = G * lit[..., None]
        if inlay is not None:
            core = rr < r * 0.42
            col_[core] = np.array(inlay, dtype=np.float64)
        ring = (np.abs(rr - r) < 0.10) & ~ray
        w = np.where(disc | ray, 1.0, 0.0)
        col_[ring] = cur[ring] * 0.55
        w = np.where(ring, 1.0, w)
        return w, col_
    sk.mark(box, f, (cx - r - ray_len - 0.2, cy - r - ray_len - 0.2, cx + r + ray_len + 0.2, cy + r + ray_len + 0.2),
            fn, spec="gold", height=0.25)


def studs(sk, box, f, pts, col, r=0.16):
    """Round rivets / buttons: a lit dome with a shadow under-right."""
    G = np.array(sk.pal[col], dtype=np.float64)
    for (cx, cy) in pts:
        def fn(fx, fy, cur, cx=cx, cy=cy):
            rr = np.hypot(fx - cx, fy - cy)
            sh = np.hypot(fx - cx - 0.09, fy - cy - 0.11) < r * 1.15
            lit = 1.0 + 0.5 * np.clip(-(fx - cx + fy - cy) / r, -1, 1)
            col_ = np.where((rr < r)[..., None], G * lit[..., None], cur * 0.6)
            return np.where(rr < r, 1.0, np.where(sh, 0.8, 0.0)), col_
        sk.mark(box, f, (cx - r - 0.2, cy - r - 0.2, cx + r + 0.3, cy + r + 0.3), fn, spec="gold", height=0.2)


def keel(sk, col="plate"):
    """A raised ridge front to back over a steel cap's crown, lit on one flank and shaded on the other - the one
    mark that turns a flat grey top into a forged dome from the RTS camera."""
    C = np.array(sk.pal[col], dtype=np.float64)

    def fn(fx, fy, cur):
        d = fx - 4.0
        w = 1 - _sm(0.16, 0.30, np.abs(d))
        k = np.where(d < 0, 1.18, 0.72)
        k = np.where(np.abs(d) < 0.06, 1.30, k)
        # the crown rounds off toward its rim; a polished bloom toward the sun's side (front left)
        dome = (1.0 - 0.20 * _sm(1.2, 4.0, np.abs(d)) - 0.12 * _sm(1.5, 4.0, np.abs(fy - 4.0))
                + 0.10 * (1 - _sm(0.0, 2.2, np.hypot(fx - 2.6, fy - 5.2))))
        return np.maximum(w, 0.999 * (1 - w)), np.where((w > 0)[..., None], C * k[..., None], cur * dome[..., None])
    sk.mark("head", "top", (0, 0, 8, 8), fn, height=0.0)
    sk.shade("head", "front", (3.7, 0, 4.3, 0.35), lambda fx, fy: 1.2 + 0 * fx)


def line(sk, box, f, rect, col, horizontal=True, width=0.12, k=1.0):
    """A thin seam / edge / cord along a rect's middle at texel scale."""
    C = np.array(sk.pal[col], dtype=np.float64) * k
    i0, j0, i1, j1 = rect

    def fn(fx, fy, cur):
        d = np.abs(fy - (j0 + j1) / 2) if horizontal else np.abs(fx - (i0 + i1) / 2)
        return 1 - _sm(width * 0.5, width * 0.5 + 0.08, d), C
    sk.mark(box, f, rect, fn)


def contact(sk, box, faces, j, k=0.72, depth=0.45):
    """The shadow a belt, a plate edge or a collar throws on what is under it: a soft dark band below row j."""
    for f in faces:
        fw, fh = Skin.face_rect(box, f)[2:]
        sk.shade(box, f, (0, j, fw, min(fh, j + depth + 0.2)), lambda fx, fy: k + (1 - k) * _sm(j, j + depth, fy))


# ------------------------------------------------------------------ the units
SKIN_FAIR, SKIN_WARM, SKIN_TAN, SKIN_WEATHER = "#c99c82", "#bf9174", "#b8886a", "#bc8e70"
TEAM = "#2f5fb3"           # the Sunforged default (unowned) - the halberdier's tabard


def evoker():
    """The Dome Cleric (Evoker, the Bastion Aegis caster): an old priest in an ivory wool chasuble with a gilt
    orphrey cross, over an under-robe in the owner's colour (it shows at the open sides, the hem and the shoulder
    cape), a linen skullcap with a gilt band (from above: an ivory dome ringed in gold over a team cape), a white
    beard, and a leather-bound book with brass corners held in both hands."""
    sk = Skin("evoker", {
        "skin": SKIN_FAIR, "hair": "#a6a29a", "beard": "#c2beb4",
        "robe_ivory": ("#b3aa96", 0.94), "linen_cap": ("#c2baa6", 0.84), "gold": "#c8973a", "gold_dark": "#8e6a26",
        "tabard": TEAM, "tabard_dark": "#244a8c", "leather": "#4e3524", "leather_book": "#5a2e22",
        "hose": "#3a3634", "boot": "#3e2a1c",
    }, team=("tabard", "tabard_dark"))
    # head: skin, hair at the sides and back, a skullcap
    sk.layer("head", "all", "skin", rims=False)
    hair(sk, [("back", (0, 1.4, 8, 6.5)), ("right", (0, 1.4, 5.5, 5.5)), ("left", (2.5, 1.4, 8, 5.5))])
    sk.layer("head", "top", "linen_cap", rims=False)
    sk.layer("head", "sides", "linen_cap", rect=(0, 0, 8, 1.4), rims=(False, True), open_=True)
    sk.layer("head", "top", "gold", rect=(3.4, 0, 4.6, 8), rims=False)
    sk.layer("head", "top", "gold", rect=(0, 3.4, 8, 4.6), rims=False)
    sk.layer("head", "sides", "gold", rect=(0, 1.1, 8, 1.45), rims=False, open_=True)
    face_features(sk, sk.pal["skin"], eye=(96, 128, 150), brow=sk.pal["beard"], age=1.0)
    beard(sk, "beard", "full", seed=1)
    nose(sk)
    # the under-robe (team) on the body and the jacket's sides and hem; the chasuble over front and back
    jt = (24.0, 6.0)
    sk.layer("body", "all", "tabard", rims=False)
    sk.layer("jacket", "all", "tabard", span_group=jt)
    sk.layer("jacket", ("front", "back"), "robe_ivory", rect=(0, 0, 8, 15.4), rims=(True, True), span_group=jt)
    sk.layer("jacket", "top", "robe_ivory", rims=False)
    # the orphrey: a gilt band down the front, a Y-cross on the back, a gilt hem
    sk.layer("jacket", "front", "gold", rect=(3.3, 0, 4.7, 15.4), rims=False, span_group=jt)
    sk.layer("jacket", "back", "gold", rect=(3.3, 2.6, 4.7, 15.4), rims=False, span_group=jt)
    for f, sgn in (("back", 1),):
        def ycross(fx, fy, cur):
            yy = 2.6 - 0.9 * np.abs(fx - 4.0)
            w = (np.abs(fy - yy) < 0.62) & (np.abs(fx - 4) < 3.6) & (fy < 3.2)
            return w * 1.0, np.array(sk.pal["gold"], dtype=np.float64) * (0.95 + 0.1 * (fy < yy))[..., None]
        sk.mark("jacket", f, (0, 0, 8, 3.4), ycross, spec="gold", height=0.12)
    sk.layer("jacket", ("front", "back"), "gold", rect=(0, 14.7, 8, 15.4), rims=False, span_group=jt)
    for f in ("front", "back"):
        line(sk, "jacket", f, (0, 15.3, 8, 15.6), "gold_dark", width=0.10)
    emboss_disc(sk, "jacket", "front", 4.0, 5.2, 0.55, "gold", rays=8, ray_len=0.35, inlay=sk.pal["gold_dark"])
    contact(sk, "jacket", ("right", "left"), 0.0, k=0.78, depth=1.0)
    # the shoulder cape (team, gilt edge) on the arms bar and the hanging arms; ivory sleeves under it
    for box in ("arm_up", "arm"):
        sk.layer(box, "all", "robe_ivory")
        sk.layer(box, "top", "tabard", rims=False)
        sk.layer(box, "sides", "tabard", rect=(0, 0, 4, 2.6), rims=(True, False), open_=True)
        line(sk, box, "front", (0, 2.45, 4, 2.75), "gold", width=0.22)
        for f in SIDES:
            line(sk, box, f, (0, 2.45, 4, 2.75), "gold", width=0.22)
        contact(sk, box, SIDES, 2.75, k=0.70, depth=0.6)
    sk.layer("arm_up", "sides", "gold", rect=(0, 6.8, 4, 7.4), rims=False, open_=True)
    sk.layer("arm", "sides", "gold", rect=(0, 8.0, 4, 8.6), rims=False, open_=True)
    sk.layer("arm", "sides", "skin", rect=(0, 8.6, 4, 12), rims=False, open_=True)
    sk.layer("arm", "bottom", "skin", rims=False)
    # the crossed forearms: sleeves, hands, and the book held between them
    sk.layer("arm_mid", "all", "robe_ivory")
    sk.layer("arm_mid", ("front", "top", "bottom"), "skin", rect=(2.2, 0, 5.8, 4), rims=False)
    sk.layer("arm_mid", "front", "leather_book", rect=(2.5, 0.2, 5.5, 3.6), rims=(True, True))
    sk.layer("arm_mid", "top", "leather_book", rect=(2.5, 0.4, 5.5, 3.6), rims=False)
    studs(sk, "arm_mid", "front", [(2.8, 0.5), (5.2, 0.5), (2.8, 3.3), (5.2, 3.3)], "gold", r=0.22)
    emboss_disc(sk, "arm_mid", "front", 4.0, 1.9, 0.42, "gold", rays=8, ray_len=0.25)
    for f in ("front",):
        sk.layer("arm_mid", f, "gold", rect=(0.9, 0, 1.4, 4), rims=False)
        sk.layer("arm_mid", f, "gold", rect=(6.6, 0, 7.1, 4), rims=False)
    # legs: dark hose and soft shoes under the hem
    sk.layer("leg", "all", "hose", rims=False)
    sk.layer("leg", "sides", "boot", rect=(0, 9.0, 4, 12), rims=(True, False), open_=True)
    sk.layer("leg", "bottom", "boot", rims=False)
    return sk


def royal_architect():
    """The Royal Architect (the T2 builder): a master mason-architect in a long oat-linen coat with brass buttons, a
    leather belt, a satchel of plans on a strap across the chest, a leather plan-case on the back, a flat wool cap in
    the owner's colour with a brass sun brooch, a short shoulder cape in the owner's colour, grey hair and a trimmed
    grey beard; he holds a rolled plan in both hands."""
    sk = Skin("royal_architect", {
        "skin": SKIN_WARM, "hair": "#7d7870", "beard": "#8e8a82",
        "linen_coat": "#a08c6c", "linen_plans": "#b8ab8c", "gold": "#c8973a", "gold_dark": "#8e6a26",
        "tabard": TEAM, "tabard_dark": "#244a8c", "leather": "#5b3f2a", "leather_dark": "#3f2b1c",
        "hose": "#3d3a36", "boot": "#43301f",
    }, team=("tabard", "tabard_dark"))
    sk.layer("head", "all", "skin", rims=False)
    hair(sk, [("back", (0, 1.2, 8, 6.5)), ("right", (0, 1.2, 6.5, 6.0)), ("left", (1.5, 1.2, 8, 6.0))])
    sk.layer("head", "top", "tabard", rims=False)                       # the flat cap
    sk.layer("head", "sides", "tabard", rect=(0, 0, 8, 1.5), rims=(False, True), open_=True)
    contact(sk, "head", SIDES, 1.5, k=0.74, depth=0.5)
    emboss_disc(sk, "head", "front", 6.0, 0.75, 0.42, "gold", rays=8, ray_len=0.2)
    face_features(sk, sk.pal["skin"], eye=(84, 100, 116), brow=sk.pal["hair"], age=0.8)
    beard(sk, "beard", "short", seed=2)
    nose(sk)
    jt = (24.0, 6.0)
    sk.layer("body", "all", "linen_coat", rims=False)
    sk.layer("jacket", "all", "linen_coat", span_group=jt)
    # the coat's front opening below the belt, brass buttons above it
    for f in ("front",):
        line(sk, "jacket", f, (3.9, 10.2, 4.1, 18), "leather_dark", horizontal=False, width=0.10, k=0.6)
        studs(sk, "jacket", f, [(4.0, 1.2 + 1.6 * k) for k in range(5)], "gold", r=0.18)
    # belt with a brass buckle
    sk.layer("jacket", "sides", "leather", rect=(0, 9.0, 8, 10.1), rims=(True, True), span_group=jt)
    contact(sk, "jacket", SIDES, 10.1, k=0.74)
    sk.layer("jacket", "front", "gold", rect=(3.45, 9.1, 4.55, 10.0), rims=False)
    sk.shade("jacket", "front", (3.65, 9.3, 4.35, 9.8), lambda fx, fy: 0.55 + 0 * fx)
    # the satchel strap from the left shoulder to the right hip, the satchel on the right side
    for f, rev in (("front", False), ("back", True)):
        def strap(fx, fy, cur, rev=rev):
            xx = (7.4 - fy * 0.72) if not rev else (0.6 + fy * 0.72)
            w = (np.abs(fx - xx) < 0.45) & (fy < 9.0)
            return w * 1.0, np.array(sk.pal["leather"], dtype=np.float64) * (0.9 + 0.2 * (np.abs(fx - xx) < 0.25))[..., None]
        sk.mark("jacket", f, (0, 0, 8, 9), strap, spec="leather")
    sk.layer("jacket", "right", "leather", rect=(0.6, 10.4, 5.4, 15.0), rims=(True, True))
    sk.layer("jacket", "right", "leather_dark", rect=(0.6, 10.4, 5.4, 11.4), rims=(True, False))   # the flap
    for k, cx in enumerate((1.6, 3.0, 4.4)):                                               # rolled plans in it
        sk.layer("jacket", "right", "linen_plans", rect=(cx - 0.45, 9.3, cx + 0.45, 10.6), rims=(True, False))
    studs(sk, "jacket", "right", [(3.0, 11.0)], "gold", r=0.2)
    # the plan-case on the back
    sk.layer("jacket", "back", "leather_dark", rect=(5.0, 0.6, 6.6, 13.0), rims=(True, True))
    sk.layer("jacket", "back", "gold", rect=(5.0, 0.6, 6.6, 1.3), rims=False)
    sk.layer("jacket", "back", "gold", rect=(5.0, 12.3, 6.6, 13.0), rims=False)
    contact(sk, "jacket", ("back",), 13.0, k=0.75)
    # the shoulder cape (team) and the sleeves
    for box in ("arm_up", "arm"):
        sk.layer(box, "all", "linen_coat")
        sk.layer(box, "top", "tabard", rims=False)
        sk.layer(box, "sides", "tabard", rect=(0, 0, 4, 2.2), rims=(True, True), open_=True)
        contact(sk, box, SIDES, 2.2, k=0.70, depth=0.6)
    sk.layer("arm_up", "sides", "leather", rect=(0, 6.6, 4, 7.6), rims=(True, True), open_=True)
    sk.layer("arm", "sides", "leather", rect=(0, 7.6, 4, 8.8), rims=(True, True), open_=True)
    sk.layer("arm", "sides", "skin", rect=(0, 8.8, 4, 12), rims=False, open_=True)
    sk.layer("arm", "bottom", "skin", rims=False)
    sk.layer("arm_mid", "all", "linen_coat")
    sk.layer("arm_mid", ("front", "top", "bottom"), "skin", rect=(2.2, 0, 5.8, 4), rims=False)
    sk.layer("arm_mid", ("front", "top"), "linen_plans", rect=(1.0, 1.1, 7.0, 2.7), rims=(True, True))
    for f in ("front",):
        line(sk, "arm_mid", f, (1.0, 1.75, 7.0, 2.05), "leather_dark", width=0.12, k=0.8)
        sk.layer("arm_mid", f, "leather", rect=(3.6, 1.1, 4.4, 2.7), rims=False)       # the tie round the roll
    sk.layer("leg", "all", "hose", rims=False)
    sk.layer("leg", "sides", "boot", rect=(0, 6.5, 4, 12), rims=(True, False), open_=True)
    sk.layer("leg", "sides", "leather_dark", rect=(0, 6.5, 4, 7.2), rims=False, open_=True)  # the turned top
    sk.layer("leg", "bottom", "boot", rims=False)
    return sk


def field_mason():
    """The Field Mason (combat engineer): a quilted gambeson dyed in the owner's colour under a steel breastplate
    with brass rivets, steel spaulders, a steel skullcap with a brass rim (from above: bright steel over a team
    gambeson), a leather tool apron with a trowel and a hammer, mason's gloves, a wooden pavise on the back painted
    with the sun, steel knee cops, a brown moustache and stubble."""
    sk = Skin("field_mason", {
        "skin": SKIN_TAN, "hair": "#4a3626", "beard": "#4f3a28",
        "cloth_gambeson": TEAM, "cloth_gambeson_dark": "#244a8c",
        "plate": ("#b9b4a8", 0.80), "steel": ("#8e939b", 0.9), "gold": "#b8893a", "leather": "#6a4a30", "leather_dark": "#45301f",
        "glove": "#7a5a3c", "wood": "#6e5034", "hose": "#4a4440", "boot": "#45301f", "linen_sun": "#c8973a",
    }, team=("cloth_gambeson", "cloth_gambeson_dark"))
    sk.layer("head", "all", "skin", rims=False)
    hair(sk, [("back", (0, 2.5, 8, 4.5)), ("right", (0, 2.5, 4.5, 4.0)), ("left", (3.5, 2.5, 8, 4.0))])
    sk.layer("head", "top", "plate", rims=False)                        # the skullcap
    sk.layer("head", "sides", "plate", rect=(0, 0, 8, 2.6), rims=(False, True), open_=True)
    sk.layer("head", "sides", "gold", rect=(0, 2.3, 8, 2.75), rims=False, open_=True)
    contact(sk, "head", SIDES, 2.75, k=0.70, depth=0.7)
    studs(sk, "head", "front", [(0.7, 2.5), (7.3, 2.5)], "gold", r=0.16)
    keel(sk)
    face_features(sk, sk.pal["skin"], eye=(70, 84, 96), brow=sk.pal["hair"], age=0.3, socket=0.68)
    beard(sk, "beard", "moustache", seed=3)
    nose(sk)
    jt = (24.0, 6.0)
    sk.layer("body", "all", "cloth_gambeson", rims=False)
    sk.layer("jacket", "all", "cloth_gambeson", span_group=jt)
    # the breastplate front and back, with a brass-riveted edge
    sk.layer("jacket", ("front", "back"), "plate", rect=(0.4, 0, 7.6, 8.6), rims=(True, True), span_group=jt)
    sk.layer("jacket", "top", "plate", rect=(0, 0, 8, 6), rims=False)
    for f in ("front", "back"):
        studs(sk, "jacket", f, [(0.9, 0.8), (7.1, 0.8), (0.9, 7.9), (7.1, 7.9)], "gold", r=0.17)
        line(sk, "jacket", f, (3.85, 0.6, 4.15, 8.2), "steel", horizontal=False, width=0.12, k=0.85)   # the keel
    contact(sk, "jacket", ("front", "back"), 8.6, k=0.66, depth=0.5)
    # belt and the tool apron
    sk.layer("jacket", "sides", "leather", rect=(0, 8.8, 8, 9.9), rims=(True, True), span_group=jt)
    sk.layer("jacket", "front", "gold", rect=(3.5, 8.9, 4.5, 9.8), rims=False)
    sk.shade("jacket", "front", (3.7, 9.1, 4.3, 9.6), lambda fx, fy: 0.55 + 0 * fx)
    contact(sk, "jacket", SIDES, 9.9, k=0.74)
    sk.layer("jacket", "front", "leather", rect=(1.0, 9.9, 7.0, 17.2), rims=(False, True), span_group=jt)
    for f in ("front",):
        line(sk, "jacket", f, (1.0, 12.4, 7.0, 12.7), "leather_dark", width=0.12)       # the pocket seam
        sk.layer("jacket", f, "steel", rect=(1.6, 10.6, 2.6, 12.3), rims=(True, False))   # the trowel blade
        sk.layer("jacket", f, "wood", rect=(1.9, 12.3, 2.3, 13.8), rims=False)             # its handle
        sk.layer("jacket", f, "wood", rect=(5.2, 10.4, 5.7, 14.6), rims=False)             # the hammer's haft
        sk.layer("jacket", f, "steel", rect=(4.6, 10.0, 6.3, 10.9), rims=(True, True))     # its head
    # the pavise on the back, over the plate's lower half and the skirt
    sk.layer("jacket", "back", "wood", rect=(0.6, 4.0, 7.4, 16.0), rims=(True, True), span_group=jt)
    sk.layer("jacket", "back", "steel", rect=(0.6, 4.0, 7.4, 4.5), rims=False)
    sk.layer("jacket", "back", "steel", rect=(0.6, 15.5, 7.4, 16.0), rims=False)
    for f in ("back",):
        line(sk, "jacket", f, (0.6, 4.0, 1.0, 16.0), "steel", horizontal=False, width=0.3, k=0.9)
        line(sk, "jacket", f, (7.0, 4.0, 7.4, 16.0), "steel", horizontal=False, width=0.3, k=0.9)
        for xx in (2.9, 5.1):
            line(sk, "jacket", f, (xx - 0.1, 4.5, xx + 0.1, 15.5), "leather_dark", horizontal=False, width=0.06, k=0.7)
    emboss_disc(sk, "jacket", "back", 4.0, 9.5, 1.4, "linen_sun", rays=12, ray_len=0.8)
    contact(sk, "jacket", ("back",), 16.0, k=0.72)
    # spaulders over gambeson sleeves, steel vambraces, gloves
    for box in ("arm_up", "arm"):
        sk.layer(box, "all", "cloth_gambeson")
        sk.layer(box, "top", "plate", rims=False)
        sk.layer(box, "sides", "plate", rect=(0, 0, 4, 3.0), rims=(True, True), open_=True)
        sk.layer(box, "sides", "plate", rect=(0, 1.5, 4, 3.0), rims=(True, True), open_=True)   # the second lame
        contact(sk, box, SIDES, 3.0, k=0.66, depth=0.6)
        studs(sk, box, "front", [(2.0, 0.8)], "gold", r=0.17)
    sk.layer("arm_up", "sides", "glove", rect=(0, 6.0, 4, 8), rims=(True, False), open_=True)
    sk.layer("arm", "sides", "steel", rect=(0, 5.2, 4, 8.4), rims=(True, True), open_=True)
    sk.layer("arm", "sides", "glove", rect=(0, 8.4, 4, 12), rims=(True, False), open_=True)
    sk.layer("arm", "bottom", "glove", rims=False)
    sk.layer("arm_mid", "all", "cloth_gambeson")
    sk.layer("arm_mid", ("front", "top", "bottom"), "glove", rect=(1.6, 0, 6.4, 4), rims=False)
    for f in ("front",):
        line(sk, "arm_mid", f, (3.9, 0.3, 4.1, 3.7), "leather_dark", horizontal=False, width=0.10, k=0.8)
    sk.layer("leg", "all", "hose", rims=False)
    sk.layer("leg", "sides", "steel", rect=(0, 3.8, 4, 5.6), rims=(True, True), open_=True)
    sk.layer("leg", "sides", "boot", rect=(0, 8.0, 4, 12), rims=(True, False), open_=True)
    sk.layer("leg", "bottom", "boot", rims=False)
    return sk


def dawn_paladin():
    """The Dawn Paladin (the Sunforged hero): a knight in steel plate - an open bascinet with a gilt brow band over a
    mail aventail, pauldrons with gilt edges, plate legs - under a surcoat in the owner's colour with a gilt hem and
    an embossed sun on the chest; a weathered face with a short dark beard."""
    sk = Skin("dawn_paladin", {
        "skin": SKIN_WEATHER, "hair": "#3a2c22", "beard": "#3e3024",
        "plate": ("#b9b4a8", 0.82), "plate_dark": ("#8d887e", 0.85), "mail": "#8a8e95", "gold": "#c8973a", "gold_dark": "#8e6a26",
        "tabard": TEAM, "tabard_dark": "#244a8c", "leather": "#4f3626", "visor": "#2e2a26",
    }, team=("tabard", "tabard_dark"))
    sk.layer("head", "all", "skin", rims=False)
    sk.layer("head", "top", "plate", rims=False)
    sk.layer("head", "sides", "plate", rect=(0, 0, 8, 3.2), rims=(False, True), open_=True)
    sk.layer("head", ("right", "left", "back"), "mail", rect=(0, 3.2, 8, 10), rims=False, open_=True)
    sk.layer("head", "front", "mail", rect=(0, 3.2, 1.0, 10), rims=False)
    sk.layer("head", "front", "mail", rect=(7.0, 3.2, 8, 10), rims=False)
    sk.layer("head", "front", "mail", rect=(0, 8.9, 8, 10), rims=False)
    sk.layer("head", "sides", "gold", rect=(0, 2.75, 8, 3.25), rims=False, open_=True)
    contact(sk, "head", SIDES, 3.25, k=0.66, depth=0.7)
    sk.layer("head", "bottom", "mail", rims=False)
    keel(sk)
    face_features(sk, sk.pal["skin"], eye=(80, 70, 56), brow=sk.pal["hair"], age=0.5, socket=0.62)
    # the face sits deep in the helm: its edges in shade
    sk.shade("head", "front", (1.0, 3.2, 7.0, 8.9), lambda fx, fy: 0.80 + 0.20 * _sm(0.0, 0.9, np.minimum(fx - 1.0, 7.0 - fx)))
    beard(sk, "beard", "short", seed=4)
    sk.layer("head", "front", "mail", rect=(0, 8.9, 8, 10), rims=False)
    emboss_disc(sk, "head", "front", 4.0, 2.9, 0.38, "gold", rays=8, ray_len=0.28)
    nose(sk)
    jt = (24.0, 6.0)
    sk.layer("body", "all", "mail", rims=False)
    sk.layer("jacket", "all", "mail", span_group=jt)
    sk.layer("jacket", ("front", "back"), "tabard", rect=(0.6, 0, 7.4, 17.6), rims=(True, True), span_group=jt)
    sk.layer("jacket", "top", "tabard", rims=False)
    for f in ("front", "back"):
        sk.layer("jacket", f, "gold", rect=(0.6, 16.9, 7.4, 17.6), rims=False, span_group=jt)
        line(sk, "jacket", f, (0.6, 0, 1.0, 17.6), "gold", horizontal=False, width=0.26)
        line(sk, "jacket", f, (7.0, 0, 7.4, 17.6), "gold", horizontal=False, width=0.26)
        # the split skirt for riding
        line(sk, "jacket", f, (3.9, 15.0, 4.1, 17.6), "tabard_dark", horizontal=False, width=0.12, k=0.55)
    # the sun below the belt: the crossed arms hide the chest from every view but the side
    emboss_disc(sk, "jacket", "front", 4.0, 12.6, 1.25, "gold", rays=12, ray_len=0.75, inlay=sk.pal["gold_dark"])
    emboss_disc(sk, "jacket", "back", 4.0, 4.8, 1.0, "gold", rays=12, ray_len=0.6)
    sk.layer("jacket", "sides", "leather", rect=(0, 9.0, 8, 10.0), rims=(True, True), span_group=jt)
    sk.layer("jacket", "front", "gold", rect=(3.5, 9.05, 4.5, 9.95), rims=False)
    sk.shade("jacket", "front", (3.7, 9.25, 4.3, 9.75), lambda fx, fy: 0.55 + 0 * fx)
    contact(sk, "jacket", SIDES, 10.0, k=0.72)
    for box in ("arm_up", "arm"):
        sk.layer(box, "all", "mail")
        sk.layer(box, "top", "plate", rims=False)
        sk.layer(box, "sides", "plate", rect=(0, 0, 4, 3.6), rims=(True, True), open_=True)
        sk.layer(box, "sides", "plate", rect=(0, 1.8, 4, 3.6), rims=(True, True), open_=True)
        sk.layer(box, "sides", "gold", rect=(0, 3.25, 4, 3.6), rims=False, open_=True)
        contact(sk, box, SIDES, 3.6, k=0.64, depth=0.6)
    sk.layer("arm_up", "sides", "plate", rect=(0, 5.4, 4, 8), rims=(True, False), open_=True)
    sk.layer("arm", "sides", "plate", rect=(0, 5.0, 4, 9.0), rims=(True, True), open_=True)
    sk.layer("arm", "sides", "plate_dark", rect=(0, 9.0, 4, 12), rims=(True, False), open_=True)
    sk.layer("arm", "bottom", "plate_dark", rims=False)
    sk.layer("arm_mid", "all", "plate")
    sk.layer("arm_mid", ("front", "top", "bottom"), "plate_dark", rect=(2.0, 0, 6.0, 4), rims=False)
    for f in ("front",):
        line(sk, "arm_mid", f, (2.0, 1.2, 6.0, 1.5), "visor", width=0.10, k=1.0)        # the knuckle plates' edge
        line(sk, "arm_mid", f, (3.9, 0.2, 4.1, 3.8), "visor", horizontal=False, width=0.10, k=1.0)
    sk.layer("leg", "all", "plate", rims=False)
    sk.layer("leg", "sides", "plate", rect=(0, 4.2, 4, 6.2), rims=(True, True), open_=True)
    sk.layer("leg", "sides", "plate", rect=(0, 6.2, 4, 10.4), rims=(True, True), open_=True)
    sk.layer("leg", "sides", "plate_dark", rect=(0, 10.4, 4, 12), rims=(True, False), open_=True)
    studs(sk, "leg", "front", [(2.0, 5.2)], "gold", r=0.2)
    sk.layer("leg", "bottom", "plate_dark", rims=False)
    return sk


UNITS = {"evoker": evoker, "royal_architect": royal_architect, "field_mason": field_mason, "dawn_paladin": dawn_paladin}
FACTION = "sunforged"


# ------------------------------------------------------------------ portraits and the review sheet
class _Small:
    """What hd_skinkit.portrait reads: a 64x64 skin (.im) - the HD paint box-filtered down, team in its default."""
    def __init__(self, sk):
        self.im = sk.imgs["base"].resize((TEX, TEX), Image.BOX)


def portrait(sk):
    bg_t, bg_b, frame = (88, 118, 170), (30, 40, 70), (226, 186, 80)     # gen_hd_skins.FACTIONS["sunforged"]
    return K.portrait(_Small(sk), bg_t, bg_b, frame)


VIEWS = (("front", (0, 0)), ("back", (180, 0)), ("side", (90, 0)), ("rts", (-35, 52)), ("top-down", (-35, 80)))


def sheet_row(imgs, size, owner, label, arms="crossed", views=VIEWS):
    import game_preview as gp
    fs = faces_for_preview(model(arms))
    cells = []
    for vname, view in views:
        im, _b = gp.render(fs, imgs, D, view, size, owner)
        cells.append(im)
    return cells


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", default="")
    ap.add_argument("--sheet", default="", help="write a before/after review sheet here (PNG)")
    ap.add_argument("--no-write", action="store_true", help="paint and check, but do not write the game textures")
    a = ap.parse_args()
    only = [n for n in a.only.split(",") if n] or list(UNITS)
    done = {}
    for name in only:
        old = {k: _old(name, k) for k in ("base", "team")}
        sk = UNITS[name]()
        sk.seal()
        probs = check(sk)
        if probs:
            raise SystemExit(f"{name}: " + "; ".join(probs))
        if not a.no_write:
            sk.save()
            portrait(sk).save(os.path.join(HEADS, f"{name}.png"))
            print(f"textures/entities/{name}_unit.png (+_team, _n, _s) 512x512, mobheads/{name}.png")
        done[name] = (old, sk)
    if a.sheet:
        write_sheet(a.sheet, done)


def _old(name, key):
    p = os.path.join(ENT, f"{name}_unit{'' if key == 'base' else '_team'}.png")
    if not os.path.exists(p):
        return Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    return Image.open(p).convert("RGBA").copy()


def write_sheet(path, done, size=220):
    """Per unit two rows (before: the 64x64 skin, after: the HD one), five views under game_preview's LIGHT (set
    LIGHT="-0.85,-0.35,0.30" for the shader lane's low sun), team in a red owner on the left half of the views and
    a blue one on... (one owner per row: red for before / after, blue on the extra rts cell)."""
    red, blue = (0.82, 0.22, 0.20), (0.24, 0.43, 0.86)
    rows = []
    for name, (old, sk) in done.items():
        before = {"base": old["base"], "team": old["team"], "glow": Image.new("RGBA", old["base"].size, (0, 0, 0, 0))}
        after = sk.imgs
        for lab, imgs, dens in (("before", before, old["base"].size[0] // TEX), ("after", after, D)):
            import game_preview as gp
            fs = faces_for_preview(model("crossed"))
            cells = [gp.render(fs, imgs, dens, v, size, red)[0] for _n, v in VIEWS]
            cells.append(gp.render(faces_for_preview(model("free")), imgs, dens, (-35, 52), size, blue)[0])
            rows.append((f"{name} - {lab}", cells))
    W = 8 + len(rows[0][1]) * (size + 4)
    H = 24 + len(rows) * (size + 22)
    out = Image.new("RGB", (W, H), (40, 42, 46))
    dr = ImageDraw.Draw(out)
    heads = [v for v, _ in VIEWS] + ["rts, arms free, blue"]
    dr.text((8, 6), "  |  ".join(heads) + "   (LIGHT=%s)" % os.environ.get("LIGHT", "minecraft"), fill=(255, 230, 150))
    y = 24
    for lab, cells in rows:
        dr.text((8, y), lab, fill=(255, 255, 255))
        for k, c in enumerate(cells):
            out.paste(c, (8 + k * (size + 4), y + 14))
        y += size + 22
    out.save(path, optimize=True)
    print("sheet", path)


if __name__ == "__main__":
    main()
