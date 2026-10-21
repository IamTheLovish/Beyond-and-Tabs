#!/usr/bin/env python3
"""The White Angel block set: our own blocks where the capitol's shapes and stone can't be done well with vanilla,
Macaw's or Chipped (lovish, Oct 21: "If we need to create new blocks... can we" - yes). Every texture is drawn here
(original, procedural, 128x like our pack) with its LabPBR 1.3 _n and _s maps (tools/materials.py, the one encoder),
and the game's block JSON (blockstates, block and item models), loot tables and English names are written with it.
The blocks are registered in registrars/WhiteAngelBlocks.java - one set any White Angel building can reuse.

The set (the ones that show most first):
  - wa_ashlar (+ _stairs, _slab, _wall): THE WARM LIGHT GREY of lovish's option C - coursed limestone ashlar,
    calm, with soft per-stone tints and fine mortar joints. Vanilla has no warm light grey (polished andesite
    measures 125/125/122 - a cool mid grey - in our pack; this is ~182/173/160).
  - wa_dressed (+ _stairs, _slab): finely dressed stone a shade lighter, for sills, string courses, copings, ribs.
  - wa_tracery: a lit lancet panel - stone mullion and leaded amber glass in a diamond lattice, light level 10
    (the capitol's windows read as gothic tracery from the RTS camera, lit at dusk); wa_tracery_head: the pointed
    arch head with a trefoil, to crown a lancet.
  - wa_finial: a crocketed finial (a block model: base, tapering shaft, crockets, a fleuron) for every pinnacle and
    gable apex, in place of the end rods.
  - wa_slate (+ _stairs, _slab): A LIGHTER BLUE-GREY SLATE for the great roofs (lovish via the main session, Oct 21
    14:20: "the big black roof slopes still read as dark patches") - rectangular slates in staggered courses, each
    a little different in tone, a shadow line under every course; vanilla's deepslate tiles read black from the RTS
    camera.
  - wa_finial_gold: the finial gilded, for the spires' tips and the crown ("gold finials").
  - wa_gargoyle: a crouching stone gargoyle (a block model: haunches, a body leaning out, a horned head with its jaws
    open, folded wings) projecting from a wall's corner or an eaves (horizontal facing: the way it looks out).
  - wa_dedication_0 .. _7: THE DEDICATION STONE, a carved tablet 4 blocks wide and 2 high built into the capitol's
    foundation (lovish, Oct 21: credit SixWings) - "INSPIRED BY SIXWINGS / EMBERHEART CASTLE" in carved Roman
    capitals (Lora's capitals, OFL; the letters are cut into the texture, the font itself is not shipped).

    python3 tools/gen_wa_blocks.py [--preview DIR]
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import materials  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether")
DATA = os.path.join(ROOT, "bar/src/main/resources/data/reignofnether")
TEX = os.path.join(ASSETS, "textures/block")
RES = 128
FONT = "/usr/share/fonts/truetype/google-fonts/Lora-Variable.ttf"
DEDICATION = ("INSPIRED BY SIXWINGS", "EMBERHEART CASTLE")
DEDICATION_W, DEDICATION_H = 4, 2

ASHLAR = np.array([182, 173, 160], dtype=np.float32) / 255.0     # warm light grey limestone
SLATE = np.array([112, 117, 126], dtype=np.float32) / 255.0      # a blue-grey slate (deepslate tiles: ~ 60 / 60 / 66)
GOLD = np.array([222, 170, 72], dtype=np.float32) / 255.0
DRESSED = np.array([198, 190, 176], dtype=np.float32) / 255.0
MORTAR = np.array([160, 151, 139], dtype=np.float32) / 255.0
GLASS = np.array([238, 178, 86], dtype=np.float32) / 255.0
LEAD = np.array([70, 58, 46], dtype=np.float32) / 255.0


# ------------------------------------------------------------------ noise (tileable) -------------------------------
def value_noise(seed, cells, res=RES):
    """Smooth tileable value noise: a cells x cells lattice, bicubic-smoothed, -1..1."""
    r = np.random.RandomState(seed)
    g = r.uniform(-1, 1, (cells, cells))
    yy, xx = np.mgrid[0:res, 0:res].astype(np.float64) * cells / res
    x0, y0 = np.floor(xx).astype(int), np.floor(yy).astype(int)
    tx, ty = xx - x0, yy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
    x0, y0 = x0 % cells, y0 % cells
    a = g[y0, x0] * (1 - tx) + g[y0, x1] * tx
    b = g[y1, x0] * (1 - tx) + g[y1, x1] * tx
    return a * (1 - ty) + b * ty


def fbm(seed, base=4, octaves=4, res=RES):
    out = np.zeros((res, res))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += amp * value_noise(seed + o * 101, base * 2 ** o, res)
        tot += amp
        amp *= 0.5
    return out / tot


# ------------------------------------------------------------------ materials ----------------------------------------
def save_maps(name, albedo, height, smooth, f0=10, pore=60, emission=None, ao=None):
    """albedo (H, W, 3) 0..1, height 0..1, smooth 0..1 -> name.png, name_n.png, name_s.png (LabPBR 1.3)."""
    os.makedirs(TEX, exist_ok=True)
    a = np.clip(albedo, 0, 1)
    rgba = np.concatenate([a, np.ones(a.shape[:2] + (1,))], axis=2)
    Image.fromarray((rgba * 255).astype(np.uint8), "RGBA").save(os.path.join(TEX, name + ".png"))
    nx, ny, nz = materials.height_normals(height.astype(np.float32), 3.0, wrap=True)
    occ = np.ones_like(height) if ao is None else ao
    n = materials.encode_normal(nx, ny, occ.astype(np.float32), np.clip(height, 0, 1).astype(np.float32))
    sm = np.broadcast_to(np.asarray(smooth, dtype=np.float32) * 255, height.shape).astype(np.float32)
    f0a = np.broadcast_to(np.asarray(f0, dtype=np.float32), height.shape).astype(np.float32)
    pa = np.broadcast_to(np.asarray(pore, dtype=np.float32), height.shape).astype(np.float32)
    em = np.full(height.shape, 255, dtype=np.float32) if emission is None else emission.astype(np.float32)
    s = materials.encode_specular(sm, f0a, pa, em)
    probs = materials.check_labpbr(n, s)
    if probs:
        raise SystemExit("%s LabPBR: %s" % (name, "; ".join(probs)))
    Image.fromarray(n, "RGBA").save(os.path.join(TEX, name + "_n.png"))
    Image.fromarray(s, "RGBA").save(os.path.join(TEX, name + "_s.png"))


def ashlar():
    """Coursed limestone ashlar: two courses a block, stones of three lengths staggered, fine recessed joints with a
    soft chisel-darkened margin, a calm per-stone tint and large-scale cloudiness (never pepper)."""
    res = RES
    h = np.ones((res, res)) * 0.75
    alb = np.zeros((res, res, 3))
    rng = np.random.RandomState(41)
    yy, xx = np.mgrid[0:res, 0:res]
    cloud = fbm(7, 2, 3)
    grain = fbm(9, 16, 2)
    course_h = res // 2
    joint = 1.6
    stone_id = np.zeros((res, res), dtype=int)
    edge = np.full((res, res), 99.0)
    sid = 0
    for c in range(2):
        y0 = c * course_h
        x = (c * 40) % res
        widths = [48, 80] if c == 0 else [64, 64]
        start = x
        for w in widths * 2:
            if start >= x + res:
                break
            xs = (np.arange(start, start + w)) % res
            sel = np.zeros((res, res), dtype=bool)
            sel[y0:y0 + course_h, xs] = True
            stone_id[sel] = sid
            # the distance to this stone's edge (for joints and the chisel margin)
            ly = (yy - y0).astype(float)
            lx = ((xx - start) % res).astype(float)
            d = np.minimum(np.minimum(ly, course_h - 1 - ly), np.minimum(lx, w - 1 - lx))
            edge[sel] = d[sel]
            sid += 1
            start += w
    tints = rng.uniform(-0.035, 0.035, sid + 1)
    warm = rng.uniform(-0.012, 0.012, sid + 1)
    base = ASHLAR[None, None, :] * (1.0 + tints[stone_id][..., None])
    base[..., 0] += warm[stone_id]
    base = base * (1.0 + 0.045 * cloud[..., None] + 0.018 * grain[..., None])
    margin = np.clip((edge - joint) / 4.0, 0, 1)
    base = base * (0.93 + 0.07 * margin[..., None])
    jm = edge < joint
    base[jm] = MORTAR
    alb[:] = base
    h = 0.78 + 0.05 * cloud + 0.03 * grain
    h = h * (0.9 + 0.1 * margin)
    h[jm] = 0.45
    ao = np.where(jm, 0.75, 1.0)
    save_maps("wa_ashlar", alb, h, 0.18, pore=55, ao=ao)
    return alb


def dressed():
    """Finely dressed stone: one smooth face, the faintest tooling lines and a soft cloud, no joints (it is cut to the
    piece: sills, copings, string courses, ribs)."""
    res = RES
    yy, xx = np.mgrid[0:res, 0:res]
    cloud = fbm(13, 2, 3)
    tool = np.sin(xx * 2 * math.pi / 6.4 + 0.3 * fbm(17, 4, 2) * 6) * 0.5 + 0.5
    alb = DRESSED[None, None, :] * (1.0 + 0.035 * cloud[..., None] - 0.012 * tool[..., None])
    h = 0.8 + 0.04 * cloud - 0.02 * tool
    save_maps("wa_dressed", alb, h, 0.3, pore=45)
    return alb


def slate():
    """Roofing slate: four courses a block, rectangular slates staggered half a slate a course, every slate its own
    tone (blue-grey to a warmer grey, a few darker), its lower edge proud over the course below with a shadow line
    under it, faint cleavage streaks along each slate."""
    res = RES
    yy, xx = np.mgrid[0:res, 0:res]
    rng = np.random.RandomState(61)
    course = res // 4
    sw = res // 4
    alb = np.zeros((res, res, 3))
    h = np.zeros((res, res))
    cloud = fbm(63, 2, 3)
    streak = fbm(67, 16, 2)
    for c in range(4):
        y0 = c * course
        off = (c % 2) * sw // 2
        for k in range(5):
            x0 = (k * sw + off) % res
            xs = (np.arange(x0, x0 + sw)) % res
            sel = np.zeros((res, res), dtype=bool)
            sel[y0:y0 + course, xs] = True
            tone = rng.uniform(-0.07, 0.07)
            warm = rng.uniform(-0.025, 0.03)
            dark = 0.82 if rng.uniform() < 0.12 else 1.0
            col = SLATE * (1.0 + tone) * dark + np.array([warm, warm * 0.5, -warm * 0.4])
            alb[sel] = col
            ly = (yy - y0)[sel].astype(float) / course              # 0 at the course's top .. 1 at its lower edge
            h[sel] = 0.55 + 0.35 * ly
    alb = alb * (1.0 + 0.04 * cloud[..., None] + 0.03 * streak[..., None])
    # the joints between slates and the shadow under each course's lower edge
    lx = (xx - ((yy // course) % 2) * (sw // 2)) % sw
    joint = (lx < 1.2) | (lx > sw - 1.2)
    ly = yy % course
    under = ly < 2.5
    alb[joint] *= 0.82
    alb[under] *= 0.72
    h[joint] = 0.4
    h[under] = 0.3
    save_maps("wa_slate", alb, h, 0.42, pore=40, ao=np.where(under | joint, 0.7, 1.0))
    return alb


def finial_gold_tex():
    """The gilded finial: warm gold, burnished light and dark."""
    res = 32
    n = fbm(71, 2, 2, res)
    alb = GOLD[None, None, :] * (1.0 + 0.1 * n[..., None])
    save_maps("wa_finial_gold", alb, np.full((res, res), 0.8), 0.72, f0=231, pore=0)


def gargoyle_tex():
    """The gargoyle's stone: the ashlar's limestone weathered darker and greener by the water it spouts."""
    res = 32
    n = fbm(73, 3, 3, res)
    wet = np.clip(fbm(79, 2, 2, res) * 1.5, -1, 1)
    alb = ASHLAR[None, None, :] * (0.78 + 0.06 * n[..., None]) + np.array([-0.02, 0.0, -0.03]) * (wet[..., None] + 1)
    save_maps("wa_gargoyle", alb, 0.75 + 0.1 * n, 0.25, pore=60)


def _arch_mask(res, x0, x1, y_top, y_spring):
    """A pointed (equilateral) arch opening between x0 and x1, springing at y_spring, apex at y_top (y down)."""
    yy, xx = np.mgrid[0:res, 0:res].astype(np.float64)
    w = x1 - x0
    inside = (xx >= x0) & (xx <= x1) & (yy >= y_spring)
    # the two arcs: centred on the far springing points, radius = width
    r = w
    left = (xx - x1) ** 2 + (yy - y_spring) ** 2 <= r * r
    right = (xx - x0) ** 2 + (yy - y_spring) ** 2 <= r * r
    arch = (yy < y_spring) & (yy >= y_top) & left & right & (xx >= x0) & (xx <= x1)
    return inside | arch


def tracery(head=False):
    """A lit lancet panel: the dressed-stone frame and central mullion, leaded amber glass in a diamond lattice, a
    glow brightest at the middle (light level 10 in game; the _s map's emission channel for shader packs). head: the
    pointed arch that crowns a lancet - two small lancets under a trefoil in the spandrel."""
    res = RES
    yy, xx = np.mgrid[0:res, 0:res].astype(np.float64)
    stone = DRESSED[None, None, :] * (1.0 + 0.03 * fbm(23, 3, 3)[..., None])
    alb = stone.copy()
    h = np.full((res, res), 0.85)
    if not head:
        glass = ((xx >= 14) & (xx <= 58)) | ((xx >= 70) & (xx <= 113))
    else:
        glass = _arch_mask(res, 14, 58, 6, 52) | _arch_mask(res, 70, 113, 6, 52)
        foil = np.zeros((res, res), dtype=bool)
        for (cx, cy) in ((64, 22), (56, 34), (72, 34)):        # the trefoil in the spandrel
            foil |= (xx - cx) ** 2 + (yy - cy) ** 2 <= 7.5 ** 2
        glass = glass | foil
    # the lattice: diamonds of lead
    u = (xx + yy) % 16
    v = (xx - yy) % 16
    lead = (np.minimum(u, 16 - u) < 1.2) | (np.minimum(v, 16 - v) < 1.2)
    glow = np.exp(-(((xx - 64) / 70.0) ** 2 + ((yy - 64) / 80.0) ** 2))
    gcol = GLASS[None, None, :] * (0.82 + 0.25 * glow[..., None]) * (1.0 + 0.06 * fbm(29, 8, 2)[..., None])
    gcol[lead] = LEAD
    alb[glass] = gcol[glass]
    h[glass] = 0.55
    h[glass & lead] = 0.62
    # the mullion and frame's chamfer: a darker line where stone meets glass
    from scipy import ndimage as ndi  # noqa: E402  (the pack tools already use scipy)
    near = ndi.binary_dilation(glass, iterations=2) & ~glass
    alb[near] *= 0.82
    emission = np.where(glass & ~lead, 120 + 100 * glow, 255)
    name = "wa_tracery_head" if head else "wa_tracery"
    save_maps(name, alb, h, np.where(glass, 0.85, 0.3), f0=np.where(glass, 30, 10), pore=np.where(glass, 0, 45),
              emission=emission)
    return alb


def finial_tex():
    """The finial's stone: the dressed stone, a little brighter (it catches the sky)."""
    res = 32
    alb = DRESSED[None, None, :] * (1.04 + 0.03 * fbm(31, 2, 2, res)[..., None])
    save_maps("wa_finial", alb, np.full((res, res), 0.8), 0.3)


def dedication():
    """The carved tablet: a moulded border round 4 x 2 blocks, the inscription in two lines of Roman capitals cut
    V-section into the stone (dark in the cut, a lit lower edge where the chisel's second stroke catches the light),
    the faint guide lines a mason scribes. Split into eight block faces."""
    W, H_ = DEDICATION_W * RES, DEDICATION_H * RES
    yy, xx = np.mgrid[0:H_, 0:W].astype(np.float64)
    base = ASHLAR[None, None, :] * 1.03 * (1.0 + 0.03 * fbm(51, 4, 3, max(W, H_))[:H_, :W, None])
    alb = base.copy()
    h = np.full((H_, W), 0.8)
    # the moulding: a raised frame 10 px in, a sunk field inside
    d_edge = np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H_ - 1 - yy))
    frame = (d_edge >= 6) & (d_edge < 16)
    field = d_edge >= 16
    h[frame] = 0.95
    h[field] = 0.72
    alb[frame] *= 1.05
    alb[(d_edge >= 16) & (d_edge < 19)] *= 0.8                  # the shadow line inside the frame
    # the letters
    img = Image.new("L", (W, H_), 0)
    d = ImageDraw.Draw(img)
    for i, line in enumerate(DEDICATION):
        size = 50 if i == 0 else 56
        f = ImageFont.truetype(FONT, size)
        tw = d.textlength(line, font=f)
        while tw > W - 60 and size > 20:
            size -= 2
            f = ImageFont.truetype(FONT, size)
            tw = d.textlength(line, font=f)
        y = 58 if i == 0 else 146
        d.text(((W - tw) / 2, y), line, fill=255, font=f)
    cut = np.asarray(img, dtype=np.float32) / 255.0
    dist = np.asarray(img.filter(ImageFilter.GaussianBlur(1.6)), dtype=np.float32) / 255.0
    depth = dist * cut
    h = h - 0.35 * depth
    # light from the upper left: the cut's lower-right inner wall catches it, the upper-left falls in shadow
    gy, gx = np.gradient(dist)
    lit = np.clip(-(gx + gy) * 6.0, -1, 1)
    alb = alb * (1.0 - 0.45 * cut[..., None]) * (1.0 + 0.25 * (lit * cut)[..., None])
    alb = np.clip(alb, 0, 1)
    # scribed guide lines
    for y in (52, 112, 140, 206):
        alb[y, 30:W - 30] *= 0.93
    for k in range(DEDICATION_W * DEDICATION_H):
        bx, by = k % DEDICATION_W, k // DEDICATION_W
        sl = (slice(by * RES, (by + 1) * RES), slice(bx * RES, (bx + 1) * RES))
        save_maps("wa_dedication_%d" % k, alb[sl], h[sl], 0.22, pore=55)
    return alb


# ------------------------------------------------------------------ the game's JSON ----------------------------------
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")


def ns(n):
    return "reignofnether:block/" + n


def cube_all(name, tex=None):
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": ns(name)}}})
    write_json(os.path.join(ASSETS, "models/block", name + ".json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": ns(tex or name)}})
    item(name)


def item(name, parent=None):
    write_json(os.path.join(ASSETS, "models/item", name + ".json"), {"parent": parent or ns(name)})
    write_json(os.path.join(DATA, "loot_tables/blocks", name + ".json"), {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "reignofnether:" + name}],
                                              "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def stairs(name, tex):
    t = {"bottom": ns(tex), "top": ns(tex), "side": ns(tex)}
    for suf, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write_json(os.path.join(ASSETS, "models/block", name + suf + ".json"), {"parent": "minecraft:block/" + parent, "textures": t})
    variants = {}
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    for facing in ("east", "north", "south", "west"):
        for half in ("bottom", "top"):
            for shape in ("inner_left", "inner_right", "outer_left", "outer_right", "straight"):
                model = ns(name + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else ""))
                y = rot[facing]
                if shape in ("inner_left", "outer_left"):
                    y = (y + 270) % 360
                if half == "top":
                    if shape in ("inner_right", "outer_right"):
                        y = (y + 90) % 360
                    elif shape in ("inner_left", "outer_left"):
                        y = (y + 90) % 360
                v = {"model": model}
                if half == "top":
                    v["x"] = 180
                if y:
                    v["y"] = y
                if half == "top" or y:
                    v["uvlock"] = True
                variants["facing=%s,half=%s,shape=%s" % (facing, half, shape)] = v
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
    item(name)


def slab(name, tex, full):
    t = {"bottom": ns(tex), "top": ns(tex), "side": ns(tex)}
    write_json(os.path.join(ASSETS, "models/block", name + ".json"), {"parent": "minecraft:block/slab", "textures": t})
    write_json(os.path.join(ASSETS, "models/block", name + "_top.json"), {"parent": "minecraft:block/slab_top", "textures": t})
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
        "type=bottom": {"model": ns(name)}, "type=top": {"model": ns(name + "_top")}, "type=double": {"model": ns(full)}}})
    item(name)


def wall(name, tex):
    for suf, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                        ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")):
        write_json(os.path.join(ASSETS, "models/block", name + suf + ".json"),
                   {"parent": "minecraft:block/" + parent, "textures": {"wall": ns(tex)}})
    mp = [{"when": {"up": "true"}, "apply": {"model": ns(name + "_post")}}]
    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for hgt, suf in (("low", "_side"), ("tall", "_side_tall")):
            a = {"model": ns(name + suf), "uvlock": True}
            if y:
                a["y"] = y
            mp.append({"when": {d: hgt}, "apply": a})
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"multipart": mp})
    item(name, ns(name + "_inventory"))


def horizontal(name, front_tex, side_tex):
    """A block with one carved face (north in the model) turned by its facing."""
    write_json(os.path.join(ASSETS, "models/block", name + ".json"), {
        "parent": "minecraft:block/orientable", "textures": {"front": ns(front_tex), "side": ns(side_tex), "top": ns(side_tex)}})
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
        "facing=north": {"model": ns(name)}, "facing=east": {"model": ns(name), "y": 90},
        "facing=south": {"model": ns(name), "y": 180}, "facing=west": {"model": ns(name), "y": 270}}})
    item(name)


def gargoyle_model():
    """The gargoyle as a block model, turned by its facing (north as modelled)."""
    els = []
    for f, to, rot in GARGOYLE:
        e = {"from": f, "to": to, "faces": {k: {"texture": "#stone"} for k in ("north", "south", "east", "west", "up", "down")}}
        if rot:
            e["rotation"] = rot
        els.append(e)
    write_json(os.path.join(ASSETS, "models/block", "wa_gargoyle.json"),
               {"parent": "minecraft:block/block", "textures": {"stone": ns("wa_gargoyle"), "particle": ns("wa_gargoyle")},
                "elements": els, "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]}}})
    write_json(os.path.join(ASSETS, "blockstates", "wa_gargoyle.json"), {"variants": {
        "facing=north": {"model": ns("wa_gargoyle")}, "facing=east": {"model": ns("wa_gargoyle"), "y": 90},
        "facing=south": {"model": ns("wa_gargoyle"), "y": 180}, "facing=west": {"model": ns("wa_gargoyle"), "y": 270}}})
    item("wa_gargoyle")


def finial_model(name="wa_finial"):
    """A crocketed finial: a square base, a tapering shaft, crockets curling out at two heights, a fleuron cap."""
    t = "#stone"

    def box(f, to, rot=None):
        e = {"from": f, "to": to, "faces": {k: {"texture": t} for k in ("north", "south", "east", "west", "up", "down")}}
        if rot:
            e["rotation"] = rot
        return e
    els = [box([5, 0, 5], [11, 3, 11]), box([6, 3, 6], [10, 9, 10]), box([6.5, 9, 6.5], [9.5, 14, 9.5]),
           box([7.25, 14, 7.25], [8.75, 16, 8.75])]
    for y, o in ((5, 1.5), (10.5, 1.0)):
        els.append(box([8 - o, y, 4.5], [8 + o, y + 1.5, 11.5]))           # crockets: a cross of knobs
        els.append(box([4.5, y, 8 - o], [11.5, y + 1.5, 8 + o]))
    els.append(box([7, 13.5, 6], [9, 14.5, 10], {"angle": 45, "axis": "y", "origin": [8, 14, 8]}))   # the fleuron
    els.append(box([6, 13.5, 7], [10, 14.5, 9], {"angle": 45, "axis": "y", "origin": [8, 14, 8]}))
    write_json(os.path.join(ASSETS, "models/block", name + ".json"),
               {"parent": "minecraft:block/block", "textures": {"stone": ns(name), "particle": ns(name)},
                "elements": els})
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": ns(name)}}})
    item(name)


# the gargoyle (facing north = looking out to -z; its back against the wall at z = 16): the boxes the game model and
# tools/capitol_sheet.py draw - (from, to, rotation or None)
GARGOYLE = [
    ([4.5, 2, 9], [11.5, 6, 16], None),                        # the haunches against the wall
    ([5.5, 4, 3.5], [10.5, 9, 13], {"angle": -22.5, "axis": "x", "origin": [8, 6, 13]}),   # the body leaning out
    ([5.75, 7, 0], [10.25, 11.5, 4.5], None),                  # the head
    ([6.25, 5, -1.5], [9.75, 7, 3.5], None),                   # the lower jaw, open
    ([6.75, 8, -2], [9.25, 10, 0], None),                      # the snout
    ([5.25, 10.5, 2.5], [6.5, 14, 3.75], {"angle": 22.5, "axis": "z", "origin": [6, 11, 3]}),   # the horns
    ([9.5, 10.5, 2.5], [10.75, 14, 3.75], {"angle": -22.5, "axis": "z", "origin": [10, 11, 3]}),
    ([3.5, 6, 7], [5, 12.5, 15], None),                        # the folded wings
    ([11, 6, 7], [12.5, 12.5, 15], None),
    ([5, 0, 5], [7, 3, 8], None),                              # the forepaws gripping the ledge
    ([9, 0, 5], [11, 3, 8], None),
]


NAMES = {
    "wa_ashlar": "White Angel Ashlar", "wa_ashlar_stairs": "White Angel Ashlar Stairs",
    "wa_ashlar_slab": "White Angel Ashlar Slab", "wa_ashlar_wall": "White Angel Ashlar Wall",
    "wa_dressed": "White Angel Dressed Stone", "wa_dressed_stairs": "White Angel Dressed Stone Stairs",
    "wa_dressed_slab": "White Angel Dressed Stone Slab", "wa_tracery": "White Angel Lit Tracery",
    "wa_tracery_head": "White Angel Lit Tracery Head", "wa_finial": "White Angel Crocketed Finial",
    "wa_slate": "White Angel Slate", "wa_slate_stairs": "White Angel Slate Stairs", "wa_slate_slab": "White Angel Slate Slab",
    "wa_finial_gold": "White Angel Gilded Finial", "wa_gargoyle": "White Angel Gargoyle",
}
for _k in range(DEDICATION_W * DEDICATION_H):
    NAMES["wa_dedication_%d" % _k] = "White Angel Dedication Stone (%d/%d)" % (_k + 1, DEDICATION_W * DEDICATION_H)


def lang():
    """The English names, inserted into en_us.json as text after its last block name (the file's other lines, their
    escapes and blank lines untouched); a name already there is left alone."""
    path = os.path.join(ASSETS, "lang/en_us.json")
    src = open(path, encoding="utf-8").read()
    data = json.loads(src)
    missing = [(k, v) for k, v in NAMES.items() if "block.reignofnether." + k not in data]
    if not missing:
        return
    lines = src.split("\n")
    at = max(i for i, ln in enumerate(lines) if ln.strip().startswith('"block.reignofnether.'))
    if not lines[at].rstrip().endswith(","):
        lines[at] = lines[at].rstrip() + ","
    new = ['  "block.reignofnether.%s": %s,' % (k, json.dumps(v)) for k, v in missing]
    rest_has_entries = any(ln.strip().startswith('"') for ln in lines[at + 1:])
    if not rest_has_entries:
        new[-1] = new[-1].rstrip(",")
    lines[at + 1:at + 1] = new
    out = "\n".join(lines)
    json.loads(out)                                          # still valid JSON
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--preview", default=None)
    a = ap.parse_args()
    previews = {"wa_ashlar": ashlar(), "wa_dressed": dressed(), "wa_tracery": tracery(False),
                "wa_tracery_head": tracery(True), "dedication": dedication()}
    finial_tex()
    finial_gold_tex()
    gargoyle_tex()
    previews["wa_slate"] = slate()
    cube_all("wa_slate")
    stairs("wa_slate_stairs", "wa_slate")
    slab("wa_slate_slab", "wa_slate", "wa_slate")
    finial_model("wa_finial_gold")
    gargoyle_model()
    cube_all("wa_ashlar")
    stairs("wa_ashlar_stairs", "wa_ashlar")
    slab("wa_ashlar_slab", "wa_ashlar", "wa_ashlar")
    wall("wa_ashlar_wall", "wa_ashlar")
    cube_all("wa_dressed")
    stairs("wa_dressed_stairs", "wa_dressed")
    slab("wa_dressed_slab", "wa_dressed", "wa_dressed")
    cube_all("wa_tracery")
    cube_all("wa_tracery_head")
    finial_model()
    for k in range(DEDICATION_W * DEDICATION_H):
        horizontal("wa_dedication_%d" % k, "wa_dedication_%d" % k, "wa_ashlar")
    lang()
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        for n, im in previews.items():
            Image.fromarray((np.clip(im, 0, 1) * 255).astype(np.uint8)).save(os.path.join(a.preview, n + ".png"))
    print("wrote the White Angel block set: %d blocks" % len(NAMES))


if __name__ == "__main__":
    main()
