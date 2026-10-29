#!/usr/bin/env python3
"""The world look's colour path (bat_postfx.fsh) in numpy, with every constant read from the sources the game uses
(WorldLookPass.java's per-look tables, the shader's DISTANCE_COOL) - so a change to a look can be checked on measured
colours with no build and no eyes round (tools/test_world_look.py).

Ported, in the shader's order: distance haze, exposure, the S-curve, the split tone with its chroma cap, saturation,
vibrance, vignette. Left out: the edge smoothing (geometry edges only), the glow (a bright-pass: on an area darker
than GlowThreshold it adds nothing) and the 8-bit dither. Checked against the real shader on Mesa llvmpipe (Oct 28,
576 random colours per look through tools/postfx_check.py's renderer, with and without haze): within 1/255, which is
the shader's own 8-bit output and dither.

    python3 tools/world_look_model.py [R G B]   (0-255; default: the r34 look-off lawn) - each look's grade of it
"""
import ast
import os
import re
import sys

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BAR = os.path.join(ROOT, "bar", "src", "main")
PASS_JAVA = os.path.join(BAR, "java", "com", "solegendary", "reignofnether", "postfx", "WorldLookPass.java")
DISPLAY_JAVA = os.path.join(BAR, "java", "com", "solegendary", "reignofnether", "config", "DisplaySettings.java")
FSH = os.path.join(BAR, "resources", "assets", "reignofnether", "shaders", "core", "bat_postfx.fsh")

LUMA = np.array([0.2126, 0.7152, 0.0722])
# WorldLookPass table -> the uniform setUniforms gives it
UNIFORMS = {"HAZE_MAX": "HazeMax", "CONTRAST": "Contrast", "SPLIT_TONE": "SplitTone", "SATURATION": "Saturation",
            "VIGNETTE": "Vignette", "EXPOSURE": "Exposure", "VIBRANCE": "Vibrance", "GLOW": "Glow",
            "GLOW_THRESHOLD": "GlowThreshold", "GLOW_RADIUS": "GlowRadius", "SHADOW_TINT": "ShadowTint",
            "HIGHLIGHT_TINT": "HighlightTint"}

_NUM = r"-?\d+(?:\.\d*)?f?"


def _source(path):
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", text, flags=re.S))


def _num(s):
    s = s.rstrip("fF")
    return float(s) if "." in s else int(s)


def java_constants(path=PASS_JAVA):
    """Every numeric constant and numeric array initialised with literals: {"EXPOSURE": [1, 1, 0.86], ...}."""
    src = _source(path)
    out = {}
    for name, body in re.findall(r"\b(?:float|int)\s*\[\](?:\s*\[\])?\s+(\w+)\s*=\s*(\{.*?\})\s*;", src, re.S):
        try:
            out[name] = ast.literal_eval(re.sub(r"(\d)[fF]\b", r"\1", body.replace("{", "[").replace("}", "]")))
        except (ValueError, SyntaxError):   # not all literals (another class's constant): not ours to read
            pass
    for decl in re.findall(r"\b(?:float|int)\s+(\w+\s*=\s*%s(?:\s*,\s*\w+\s*=\s*%s)*)\s*;" % (_NUM, _NUM), src):
        for name, value in re.findall(r"(\w+)\s*=\s*(%s)" % _NUM, decl):
            out[name] = _num(value)
    return out


def look_index(look, path=PASS_JAVA):
    """The column of WorldLookPass's tables a look uses, read from render()'s setUniforms call (Subtle: the fallback)."""
    expr = re.search(r"setUniforms\(\s*evt\s*,(.*?),\s*main\s*\)", _source(path), re.S).group(1)
    named = {k.lower(): int(v) for k, v in re.findall(r"LOOK_(\w+)\s*\?\s*(\d+)", expr)}
    named.setdefault("subtle", int(re.search(r":\s*(\d+)\s*$", expr.strip()).group(1)))
    return named[look]


def uniforms(look="enchanted", path=PASS_JAVA):
    """The grade's uniforms for a look (subtle, full, enchanted), as WorldLookPass.setUniforms sets them at 100% haze."""
    k = java_constants(path)
    m = look_index(look, path)
    u = {UNIFORMS[name]: (tuple(k[name][m]) if isinstance(k[name][m], list) else k[name][m]) for name in UNIFORMS}
    u["HazeMax"] = min(u["HazeMax"], k["HAZE_MAX_LIMIT"])
    return u


def shader_vec3(name, path=FSH):
    m = re.search(r"const\s+vec3\s+%s\s*=\s*vec3\(([^)]*)\)" % name, _source(path))
    return np.array([float(x) for x in m.group(1).split(",")])


def rts_haze(u, blocks_past_start, path=PASS_JAVA):
    """The RTS view's haze fraction h at a view depth this many blocks past the screen's bottom edge."""
    return u["HazeMax"] * (1 - np.exp(-max(blocks_past_start, 0.0) / java_constants(path)["RTS_HAZE_SCALE"]))


def noon_sky(path=PASS_JAVA):
    """The haze colour of the RTS view before vanilla's first fog event: WorldLookPass's plains-noon sky."""
    k = java_constants(path)
    return np.array([k["skyR"], k["skyG"], k["skyB"]])


def vignette_r2(x, y, w, h):
    """The shader's r2 (0 centre, 1 corner) at pixel (x, y) of a w x h frame."""
    aspect = w / h
    dx, dy = (x / w - 0.5) * aspect, y / h - 0.5
    return (dx * dx + dy * dy) / (0.25 * (aspect * aspect + 1))


def _smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def grade(c, u, h=0.0, haze_color=(0.75, 0.85, 1.0), r2=0.0, distance_cool=None):
    """bat_postfx.fsh's main() after the edge smoothing and glow, on colours c (..., 3) in 0..1. h: the haze fraction
    before the dark-pixel falloff (HazeMax * (1 - exp(...)); 0 = sky or no depth); r2: vignette radius term."""
    c = np.array(c, float)
    cool = shader_vec3("DISTANCE_COOL") if distance_cool is None else np.asarray(distance_cool, float)
    h = np.asarray(h, float) * (0.4 + 0.6 * _smoothstep(0.05, 0.4, c @ LUMA))
    c = c + ((c @ LUMA)[..., None] - c) * (h * 0.5)[..., None]
    c = c * (1 + (cool - 1) * h[..., None])
    c = c + (np.asarray(haze_color, float) - c) * h[..., None]

    c = c * u["Exposure"]
    l = c @ LUMA
    lc = l + u["Contrast"] * 8 * l * l * (1 - l) ** 2 * (2 * l - 1)
    c = c * (lc / np.maximum(l, 1e-4))[..., None]
    chroma = np.linalg.norm(c - lc[..., None], axis=-1)
    shadows = _smoothstep(0, 0.25, lc) - _smoothstep(0.25, 0.5, lc)
    highlights = _smoothstep(0.5, 0.75, lc) - _smoothstep(0.75, 1.0, lc)
    st = u["SplitTone"]
    c = c * (1 + (np.asarray(u["ShadowTint"]) - 1) * (shadows * st)[..., None]) \
        * (1 + (np.asarray(u["HighlightTint"]) - 1) * (highlights * st)[..., None])
    lt = c @ LUMA
    v = c - lt[..., None]
    cap = np.minimum(1.0, np.maximum(chroma, 0.1 * st * lt) / np.maximum(np.linalg.norm(v, axis=-1), 1e-5))
    c = lt[..., None] + v * cap[..., None]
    c = lt[..., None] + (c - lt[..., None]) * u["Saturation"]
    if u["Vibrance"] != 0:
        lv = c @ LUMA
        dv = c - lv[..., None]
        sat = np.linalg.norm(dv, axis=-1) / np.maximum(lv, 1e-3)
        c = lv[..., None] + dv * (1 + u["Vibrance"] * np.clip(1 - sat / 0.6, 0, 1))[..., None]
    c = c * (1 - u["Vignette"] * np.asarray(r2, float) ** 2)[..., None]
    return np.clip(c, 0, 1)


def hsv(c):
    """(hue deg, saturation, value) of one rgb colour in 0..1."""
    r, g, b = (float(x) for x in c)
    mx, mn = max(r, g, b), min(r, g, b)
    d = mx - mn
    if d < 1e-9:
        return 0.0, 0.0, mx
    if mx == r:
        hue = ((g - b) / d) % 6
    elif mx == g:
        hue = (b - r) / d + 2
    else:
        hue = (r - g) / d + 4
    return 60 * hue, d / mx, mx


def main():
    rgb = np.array([float(x) for x in sys.argv[1:4]] if len(sys.argv) >= 4 else [79, 100, 43]) / 255
    print("in        hue %5.1f  S %.3f  V %.3f" % hsv(rgb))
    for look in ("subtle", "full", "enchanted"):
        u = uniforms(look)
        print("%-9s hue %5.1f  S %.3f  V %.3f   (no haze)" % ((look,) + hsv(grade(rgb, u))))


if __name__ == "__main__":
    main()
