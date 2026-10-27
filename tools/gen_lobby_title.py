#!/usr/bin/env python3
"""
The title screen's lettering over the cathedral (design/splash.md, hud/LobbyBackdrop.drawTitle): "BEYOND / AND TABS" in
Cinzel Black and "Developed by Lovish" in UnifrakturCook, blood-red with a gold rim, a dark outline and a soft crimson
glow, the title's lower letters dripping. Drawn from the two OFL fonts in assets/reignofnether/font by code; the static
drip roots are in the texture, the falling drops are animated in game from the anchors this writes into
LobbyBackdrop.java (<gen:title>).

Used by tools/gen_lobby_art.py (step "title"); runs alone too: python3 tools/gen_lobby_title.py
"""
import math
import os
import re

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "bar/src/main/resources/assets/reignofnether/textures/gui/lobby")
FONT_DIR = os.path.join(REPO, "bar/src/main/resources/assets/reignofnether/font")
CINZEL = os.path.join(FONT_DIR, "cinzel_black.ttf")
FRAK = os.path.join(FONT_DIR, "unifrakturcook_bold.ttf")
JAVA = os.path.join(REPO, "bar/src/main/java/com/solegendary/reignofnether/hud/LobbyBackdrop.java")
SS = 2                      # supersampling


def _blur(a, s):
    return ndimage.gaussian_filter(a, s) if s > 0 else a


def _dilate(m, r):
    """Round dilation of a soft mask by r px."""
    if r <= 0:
        return m
    yy, xx = np.mgrid[-r:r + 1, -r:r + 1]
    fp = (xx * xx + yy * yy) <= r * r + 0.5
    return ndimage.grey_dilation(m, footprint=fp)


def _noise(h, w, s, seed):
    r = np.random.default_rng(seed)
    return _blur(r.standard_normal((h, w)).astype(np.float32), s) * s * 1.8


def _lay(dst_rgb, dst_a, rgb, a):
    """Composite a layer (straight rgb, alpha a) over the accumulating straight-alpha image."""
    a = np.clip(a, 0, 1)[..., None]
    out_a = a + dst_a[..., None] * (1 - a)
    num = rgb * a + dst_rgb * dst_a[..., None] * (1 - a)
    dst_rgb[:] = num / np.maximum(out_a, 1e-5)
    dst_a[:] = out_a[..., 0]


def _bevel(m, s, light=(-0.55, -0.83)):
    """Shading of a soft mask as a raised bevel lit from (lx, ly) (screen y down): -1..1."""
    hgt = _blur(m, s)
    gy, gx = np.gradient(hgt)
    n = np.hypot(gx, gy) + 1e-6
    # facing the light where the slope descends away from it
    return np.clip(-(gx * light[0] + gy * light[1]) / n * np.clip(n * s * 3.0, 0, 1), -1, 1)


def _strand(d, x, y0, length, wd, col):
    """A drip: a root widening into the letter, a tapering body, a round bead at its tip (PIL, mask value col)."""
    steps = max(4, int(length))
    for i in range(steps + 1):
        k = i / steps
        yy = y0 + k * length
        hw = wd * (1.15 - 0.55 * k + 0.9 * max(0.0, 0.15 - k) / 0.15)
        d.ellipse([x - hw, yy - hw * 0.6, x + hw, yy + hw * 0.6], fill=col)
    r = wd * 1.25
    d.ellipse([x - r, y0 + length - r * 0.7, x + r, y0 + length + r * 1.3], fill=col)


def _swash_j(d, x, base, cap, fill=255):
    """Our own blackletter capital J (UnifrakturCook's J reads as an I or a T at credit size): a broad-nib stem with a
    lozenge head and a hairline lead-in, falling below the baseline and curling left into a hook with a teardrop end,
    so it can only be a J. Drawn as broad-nib strokes (nib at 40 deg). Returns the advance width."""
    nw = cap * 0.19
    a = math.radians(40)
    nx, ny = math.cos(a) * nw / 2, -math.sin(a) * nw / 2

    def stroke(path, k=1.0):
        mx, my = nx * k, ny * k
        for (x0, y0), (x1, y1) in zip(path, path[1:]):
            p0, p1 = (x + x0 * cap, base + y0 * cap), (x + x1 * cap, base + y1 * cap)
            d.polygon([(p0[0] + mx, p0[1] + my), (p1[0] + mx, p1[1] + my), (p1[0] - mx, p1[1] - my),
                       (p0[0] - mx, p0[1] - my)], fill=fill)
    # hairline lead-in from the left, rising into the head
    stroke([(-0.20, -0.74), (-0.10, -0.86), (0.02, -0.95), (0.14, -0.99)], 0.22)
    # the lozenge head
    hx, hy, r = x + 0.20 * cap, base - 0.93 * cap, cap * 0.14
    d.polygon([(hx, hy - r), (hx + r * 0.85, hy), (hx, hy + r), (hx - r * 0.85, hy)], fill=fill)
    # the stem, down past the baseline, then the hook curling left and up
    path = [(0.21, -0.92), (0.22, -0.60), (0.22, -0.25), (0.21, 0.05), (0.18, 0.20), (0.11, 0.32), (0.00, 0.39),
            (-0.13, 0.40), (-0.24, 0.35), (-0.31, 0.27)]
    stroke(path)
    # the teardrop at the hook's end
    tx, ty, tr = x - 0.31 * cap, base + 0.24 * cap, cap * 0.085
    d.ellipse([tx - tr, ty - tr, tx + tr, ty + tr], fill=fill)
    # a thin spur off the stem (the blackletter's diamond notch on the right)
    stroke([(0.22, -0.52), (0.33, -0.58)], 0.35)
    return cap * 0.58


def _swash_l(d, x, base, cap, fill=255):
    """Our own blackletter capital L (UnifrakturCook's L reads as a C at credit size): a lozenge-headed broad-nib stem
    and a foot running right along the baseline, turning up at its end. Returns the advance width."""
    nw = cap * 0.19
    a = math.radians(40)
    nx, ny = math.cos(a) * nw / 2, -math.sin(a) * nw / 2

    def stroke(path, k=1.0):
        mx, my = nx * k, ny * k
        for (x0, y0), (x1, y1) in zip(path, path[1:]):
            p0, p1 = (x + x0 * cap, base + y0 * cap), (x + x1 * cap, base + y1 * cap)
            d.polygon([(p0[0] + mx, p0[1] + my), (p1[0] + mx, p1[1] + my), (p1[0] - mx, p1[1] - my),
                       (p0[0] - mx, p0[1] - my)], fill=fill)
    stroke([(-0.12, -0.70), (-0.04, -0.84), (0.06, -0.95), (0.16, -0.99)], 0.22)
    hx, hy, r = x + 0.20 * cap, base - 0.93 * cap, cap * 0.14
    d.polygon([(hx, hy - r), (hx + r * 0.85, hy), (hx, hy + r), (hx - r * 0.85, hy)], fill=fill)
    stroke([(0.21, -0.92), (0.22, -0.55), (0.21, -0.20), (0.19, -0.06), (0.26, 0.0)])
    stroke([(0.24, -0.02), (0.40, 0.0), (0.56, -0.01), (0.66, -0.05), (0.72, -0.16)], 0.75)
    stroke([(0.22, -0.50), (0.34, -0.56)], 0.35)
    return cap * 0.80


GLYPHS = {"J": (_swash_j, 0.58), "L": (_swash_l, 0.80)}


def _lettering(lines, pad, seed, drips_static, drips_anim, out_name, glow=1.0, rim_r=7, outline_r=11,
               fill_top=(236, 84, 92), fill_mid=(186, 20, 32), fill_bot=(104, 6, 16), hatch=True,
               rim_top=(255, 230, 158), rim_bot=(158, 97, 31), bloom_rgb=(140, 8, 15), outline_rgb=(33, 3, 8),
               seam_rgb=(56, 5, 10), gloss_rgb=(255, 191, 191), drip_mid=None, drip_bot=None, swash_j=False):
    """lines: [(parts, gap_before)], parts = [(text, font_file, size_px, tracking)] laid on one baseline, each line
    centred. Returns (texture info dict)."""
    # ---- measure
    def adv(ch, f):
        if swash_j and ch in GLYPHS:
            return (f.getbbox("H")[3] - f.getbbox("H")[1]) * GLYPHS[ch][1]
        return f.getlength(ch)

    def part_w(text, f, track):
        return sum(adv(ch, f) for ch in text) + track * (len(text) - 1)
    laid = []
    y = pad
    width = 0
    for parts, gap in lines:
        fonts = [(t, ImageFont.truetype(ff, int(sz * SS)), tr * SS) for t, ff, sz, tr in parts]
        cap = max(f.getbbox("H")[3] - f.getbbox("H")[1] for _, f, _ in fonts)
        lw = sum(part_w(t, f, tr) for t, f, tr in fonts)
        y += gap * SS
        base = y + cap
        laid.append((fonts, lw, base, cap))
        width = max(width, lw)
        y = base
    W = int(width + 2 * pad)
    H = int(y + pad * 1.6)
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    bottoms = []                      # per glyph: its x range and the baseline (for drips)
    for fonts, lw, base, cap in laid:
        x = (W - lw) / 2
        for t, f, tr in fonts:
            for ch in t:
                if swash_j and ch in GLYPHS:
                    a_ = GLYPHS[ch][0](d, x + cap * (0.32 if ch == "J" else 0.10), base, cap)
                    bottoms.append((x, x + a_, base, cap))
                    x += a_ + tr
                    continue
                if ch != " ":
                    d.text((x, base), ch, font=f, fill=255, anchor="ls")
                    bb = f.getbbox(ch, anchor="ls")
                    bottoms.append((x + bb[0], x + bb[2], base, cap))
                x += f.getlength(ch) + tr
    letters = np.asarray(m, np.float32) / 255

    # ---- drip anchors: columns where a glyph's foot sits on the baseline (stems, serifs), on the last line only
    rng = np.random.default_rng(seed)
    last_base = laid[-1][2]
    cand = []
    for x0, x1, base, cap in bottoms:
        if abs(base - last_base) > 1:
            continue
        for xi in range(int(x0) + 2, int(x1) - 2):
            col = letters[int(base - cap * 0.5):int(base + cap * 0.08), xi]
            if col.size and col[-int(cap * 0.08) - 2:].max() > 0.6 and col[: int(cap * 0.3)].mean() > 0.5:
                cand.append(xi)
    cand = np.array(sorted(set(cand)))
    picks = []
    order = rng.permutation(len(cand))
    for i in order if drips_static + drips_anim else []:
        x = cand[i]
        if all(abs(x - p) > W * 0.03 for p in picks):
            picks.append(x)
        if len(picks) >= drips_static + drips_anim:
            break
    picks = sorted(picks)
    rng.shuffle(picks)
    stat, anim = sorted(picks[:drips_static]), sorted(picks[drips_static:])

    def foot(x):
        colm = letters[:, int(x)]
        ys = np.nonzero(colm > 0.5)[0]
        ys = ys[ys < last_base + laid[-1][3] * 0.1]
        return float(ys.max()) if len(ys) else last_base

    cap_last = laid[-1][3]
    dm = Image.new("L", (W, H), 0)
    dd = ImageDraw.Draw(dm)
    for x in stat:
        L = cap_last * rng.uniform(0.28, 0.62)
        _strand(dd, x, foot(x) - cap_last * 0.04, L, cap_last * rng.uniform(0.042, 0.06), 255)
    # the animated ones get a short, wet root in the texture so the strand grows out of something
    anchors = []
    for x in anim:
        y0 = foot(x) - cap_last * 0.04
        wd = cap_last * rng.uniform(0.038, 0.05)
        _strand(dd, x, y0, cap_last * 0.06, wd, 255)
        anchors.append((x / W, (y0 + cap_last * 0.07) / H, wd / H, rng.uniform(0.25, 0.55) * cap_last / H))
    drips = np.asarray(dm, np.float32) / 255
    drips = _blur(drips, 0.6 * SS)
    allm = np.maximum(letters, drips)

    # ---- layers
    rgb = np.zeros((H, W, 3), np.float32)
    a = np.zeros((H, W), np.float32)
    ys = np.arange(H, dtype=np.float32)[:, None] * np.ones((1, W), np.float32)
    # crimson bloom behind, then a soft dark shadow
    bloom = _blur(_dilate(allm, int(8 * SS)), 22 * SS) * 0.55 * glow
    _lay(rgb, a, np.broadcast_to(np.array(bloom_rgb, np.float32) / 255, rgb.shape), bloom)
    sh = _blur(_dilate(allm, int(outline_r * SS * 0.9)), 6 * SS)
    sh = ndimage.shift(sh, (5 * SS, 3 * SS), order=1)
    _lay(rgb, a, np.zeros_like(rgb), sh * 0.85)
    # dark outline (letters and drips)
    ol = np.clip(_blur(_dilate(allm, int(outline_r * SS * 0.5)), 0.8 * SS) * 1.4, 0, 1)
    _lay(rgb, a, np.broadcast_to(np.array(outline_rgb, np.float32) / 255, rgb.shape), ol)
    # gold rim (letters only): a bevel, light gold at each line's top to deep bronze at its foot, a few nicks
    rim = np.clip(_blur(_dilate(letters, int(rim_r * SS * 0.5)), 0.7 * SS) * 1.4, 0, 1)
    vrel = np.zeros_like(ys)
    for fonts, lw, base, cap in laid:
        band = (ys > base - cap * 1.25) & (ys < base + cap * 0.6)
        vrel = np.where(band, np.clip((ys - (base - cap)) / cap, 0, 1), vrel)
    g_top, g_bot = np.array(rim_top) / 255, np.array(rim_bot) / 255
    grgb = g_top * (1 - vrel[..., None]) + g_bot * vrel[..., None]
    bev = _bevel(rim, 2.2 * SS)
    grgb = grgb * (0.78 + 0.42 * bev[..., None])
    grgb += np.clip(bev - 0.55, 0, 1)[..., None] * (g_top * 0.6) * 1.4       # bright catch lights
    grgb *= (0.92 + 0.08 * np.clip(_noise(H, W, 1.2 * SS, seed + 3), -1, 1))[..., None]
    _lay(rgb, a, np.clip(grgb, 0, 1).astype(np.float32), rim)
    # the dark seam between the gold and the blood
    seam = np.clip(_blur(_dilate(letters, int(1.5 * SS)), 0.6 * SS) * 1.5, 0, 1)
    _lay(rgb, a, np.broadcast_to(np.array(seam_rgb, np.float32) / 255, rgb.shape), seam)
    # the blood: three-band gradient per line, a raised wet bevel, a gloss streak near the top, ink hatching low down
    ft, fm, fb = (np.array(c, np.float32) / 255 for c in (fill_top, fill_mid, fill_bot))
    k = vrel[..., None]
    frgb = np.where(k < 0.45, ft * (1 - k / 0.45) + fm * (k / 0.45), fm * (1 - (k - 0.45) / 0.55) + fb * ((k - 0.45) / 0.55))
    # drips: their own gradient, darker as they hang
    dk = np.clip((ys - last_base) / (cap_last * 0.5), 0, 1)[..., None]
    frgb = np.where((drips > letters)[..., None], fm * (1 - dk) + fb * dk, frgb)
    bv = _bevel(allm, 3.0 * SS)
    frgb = frgb * (0.82 + 0.30 * bv[..., None])
    gloss = np.clip(bv - 0.35, 0, 1) * np.clip(1.2 - k[..., 0] * 1.6, 0, 1)
    frgb += gloss[..., None] * np.array(gloss_rgb, np.float32) / 255 * 0.9
    if hatch:
        u = (np.arange(W)[None, :] + ys) / (3.2 * SS)
        hl = (0.5 + 0.5 * np.sin(u * math.pi * 2)) ** 6
        frgb *= (1 - 0.22 * hl * np.clip(k[..., 0] * 1.4 - 0.35, 0, 1))[..., None]
    frgb *= (0.94 + 0.06 * np.clip(_noise(H, W, 0.9 * SS, seed + 7), -1, 1))[..., None]
    _lay(rgb, a, np.clip(frgb, 0, 1).astype(np.float32), allm)

    # ---- the glow fades out before the texture's edge (no visible box round a glowing name)
    ex = np.minimum(np.arange(W), np.arange(W)[::-1])[None, :] / (pad * 0.9)
    ey = np.minimum(np.arange(H), np.arange(H)[::-1])[:, None] / (pad * 0.9)
    win = np.clip(np.minimum(ex, ey), 0, 1)
    a = np.maximum(a * win * win * (3 - 2 * win), allm * (a > 0))
    # ---- downsample (premultiplied) and save straight RGBA
    pm = np.concatenate([rgb * a[..., None], a[..., None]], -1)
    out_w, out_h = W // SS, H // SS
    chans = [np.asarray(Image.fromarray(pm[..., i].astype(np.float32), "F").resize((out_w, out_h), Image.LANCZOS))
             for i in range(4)]
    pm = np.stack(chans, -1)
    al = np.clip(pm[..., 3], 0, 1)
    col = np.clip(pm[..., :3] / np.maximum(al, 1e-4)[..., None], 0, 1)
    img = Image.fromarray(np.round(np.concatenate([col, al[..., None]], -1) * 255).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, out_name + ".png"), optimize=True)
    text_w = width / W
    first_base, first_cap = laid[0][2], laid[0][3]
    return {"w": out_w, "h": out_h, "text_w": text_w, "top_v": (first_base - first_cap) / H,
            "bot_v": last_base / H, "anchors": anchors}


def scrim(size=256):
    """A rounded soft-edged dark pad (alpha only): full in the middle, feathered over the outer 45 % (superellipse)."""
    ys, xs = (np.mgrid[0:size, 0:size].astype(np.float32) + 0.5) / size * 2 - 1
    r = (np.abs(xs) ** 3.5 + np.abs(ys) ** 3.5) ** (1 / 3.5)
    k = np.clip((1.0 - r) / 0.45, 0, 1)
    a = k * k * (3 - 2 * k)
    img = np.zeros((size, size, 4), np.uint8)
    img[..., 3] = np.round(a * 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(os.path.join(OUT, "scrim.png"), optimize=True)


def title(write_java=True):
    scrim()
    s = 220
    t = _lettering([([("B", CINZEL, s * 1.16, 4), ("EYOND", CINZEL, s, 4)], 0),
                    ([("AND ", CINZEL, s * 0.64, 6), ("T", CINZEL, s * 0.86, 3), ("ABS", CINZEL, s * 0.78, 3)], s * 0.26)],
                   pad=60 * SS, seed=17, drips_static=4, drips_anim=6, out_name="title", rim_r=10, outline_r=15)
    # the credits (lovish, Oct 22: "Joisco is also a developer"): "Developed by Lovish & Joisco", both names billed
    # equally - same face, same size, same pad, so one scale draws every piece. "Developed by" in bone, Lovish in
    # blood and gold with drips, a small gold "&", Joisco in sapphire into violet with a platinum rim and a cool glow.
    F = 84
    b = _lettering([([("Developed by", FRAK, F, 1)], 0)], pad=44 * SS, seed=23, drips_static=0, drips_anim=0,
                   out_name="byline", glow=0.35, rim_r=4, outline_r=9, fill_top=(246, 236, 214), fill_mid=(214, 198, 170),
                   fill_bot=(150, 132, 108), hatch=False, rim_top=(120, 96, 60), rim_bot=(70, 52, 30),
                   bloom_rgb=(40, 26, 20), outline_rgb=(12, 8, 6), seam_rgb=(40, 30, 22), gloss_rgb=(255, 250, 236))
    lv = _lettering([([("Lovish", FRAK, F, 1)], 0)], pad=44 * SS, seed=31, drips_static=3, drips_anim=0,
                    out_name="credit_lovish", glow=0.6, rim_r=5, outline_r=9, fill_top=(255, 186, 160),
                    fill_mid=(236, 78, 74), fill_bot=(170, 26, 32), hatch=False, swash_j=True)
    am = _lettering([([("&", FRAK, F * 0.78, 0)], 0)], pad=44 * SS, seed=37, drips_static=0, drips_anim=0,
                    out_name="credit_amp", glow=0.4, rim_r=3, outline_r=8, fill_top=(255, 236, 170),
                    fill_mid=(222, 172, 84), fill_bot=(150, 98, 34), hatch=False, rim_top=(255, 246, 210),
                    rim_bot=(120, 80, 30), bloom_rgb=(120, 80, 20), outline_rgb=(24, 14, 4), seam_rgb=(60, 40, 12),
                    gloss_rgb=(255, 250, 220))
    j = _lettering([([("Joisco", FRAK, F, 1)], 0)], pad=44 * SS, seed=29, drips_static=0, drips_anim=0,
                   out_name="credit_joisco", glow=0.9, rim_r=6, outline_r=9, fill_top=(220, 232, 255),
                   fill_mid=(132, 162, 255), fill_bot=(178, 118, 250), hatch=False, rim_top=(250, 252, 255),
                   rim_bot=(150, 160, 186), bloom_rgb=(64, 110, 240), outline_rgb=(6, 6, 30), seam_rgb=(14, 10, 52),
                   gloss_rgb=(220, 232, 255), swash_j=True)
    if write_java:
        f = lambda v: f"{v:.4f}f"
        anchors = ", ".join("{" + ", ".join(f(v) for v in an) + "}" for an in t["anchors"])
        body = ("    // <gen:title> written by tools/gen_lobby_title.py - do not edit by hand\n"
                f"    private static final float TITLE_TEXT_W = {f(t['text_w'])}, TITLE_TOP_V = {f(t['top_v'])}, "
                f"TITLE_BOT_V = {f(t['bot_v'])};\n"
                "    /** the credits' pieces (texture width fractions of the text, cap top and baseline as v) */\n"
                + "".join(f"    private static final float {k}_TEXT_W = {f(v['text_w'])}, {k}_TOP_V = {f(v['top_v'])}, "
                          f"{k}_BOT_V = {f(v['bot_v'])};\n"
                          for k, v in (("BYLINE", b), ("LOVISH", lv), ("AMP", am), ("JOISCO", j)))
                +
                "    /** the title's animated drips: u, v (texture), half width and reach (in texture heights) */\n"
                f"    private static final float[][] TITLE_DRIPS = {{{anchors}}};\n")
        src = open(JAVA).read()
        i0, i1 = src.index("    // <gen:title>"), src.index("    // </gen:title>")
        open(JAVA, "w").write(src[:i0] + body + src[i1:])
    return t, b, lv, am, j


def read_java():
    """The numbers above, read back from LobbyBackdrop.java (for the previews)."""
    src = open(JAVA).read()
    g = lambda k: float(re.search(k + r" = ([-\d.]+)f", src).group(1))
    rows = re.findall(r"\{([^{}]*)\}", re.search(r"TITLE_DRIPS = \{(.*?)\};\n", src).group(1))
    return ({"text_w": g("TITLE_TEXT_W"), "top_v": g("TITLE_TOP_V"), "bot_v": g("TITLE_BOT_V"),
             "anchors": [tuple(float(v.strip().rstrip("f")) for v in r.split(",")) for r in rows]},
            *({"text_w": g(k + "_TEXT_W"), "top_v": g(k + "_TOP_V"), "bot_v": g(k + "_BOT_V")}
              for k in ("BYLINE", "LOVISH", "AMP", "JOISCO")))

if __name__ == "__main__":
    print(*title(), sep="\n")
