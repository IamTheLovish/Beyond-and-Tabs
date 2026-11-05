"""Static checks of our shader pack (shaderpacks/BeyondAndTabs): every program compiles, and every file says it is ours.

The pack is loaded by Oculus in the game, which we never run on lovish's PC; this catches GLSL mistakes on a CI runner
in seconds instead of in a ten-minute eyes run. Each program is expanded the way the loader expands it (``#include
"/lib/x.glsl"`` from the pack's ``shaders/`` folder, nested), then compiled by Khronos's reference compiler, glslang,
as the loader's legacy GLSL 1.20 profile. Programs that read a loader macro (MC_RENDER_STAGE_STARS) compile both with
and without it.

    python3 tools/shader_pack_check.py [--glslang PATH]

Exit 0 when everything passes; each failure is printed with its program name.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "shaderpacks", "BeyondAndTabs")
SHADERS = os.path.join(ROOT, "shaders")
CLEAN_ROOM = "Original work, written clean-room"
INCLUDE = re.compile(r'^\s*#include\s+"([^"]+)"\s*$')
# a loader macro some programs test for, and a value for it (the loader defines the render stages as small ints)
LOADER_MACROS = {"MC_RENDER_STAGE_STARS": "5"}


def expand(path, seen=()):
    """The program's source with every #include replaced by the file's text, as the loader does it."""
    if path in seen:
        raise ValueError("include cycle: " + " -> ".join(seen + (path,)))
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = INCLUDE.match(line)
            if not m:
                out.append(line)
                continue
            target = m.group(1)
            if not target.startswith("/"):
                raise ValueError("%s: include %r is not absolute (the loader resolves from shaders/)" % (path, target))
            out.append(expand(os.path.join(SHADERS, target.lstrip("/")), seen + (path,)))
            out.append("\n")
    return "".join(out)


def programs():
    """The stage files the loader reads directly (shaders/*.vsh and *.fsh), sorted."""
    return sorted(n for n in os.listdir(SHADERS) if n.endswith((".vsh", ".fsh")))


def with_macros(source, macros):
    """The source with #defines added straight after its #version line."""
    lines = source.split("\n")
    extra = ["#define %s %s" % kv for kv in macros.items()]
    return "\n".join(lines[:1] + extra + lines[1:])


def _condition(expr, defines):
    """A #if / #elif condition as the GLSL preprocessor reads it: `defined X` / `defined(X)`, X == n, !, &&, ||."""
    expr = re.sub(r"defined\s*\(?\s*(\w+)\s*\)?", lambda m: "1" if m.group(1) in defines else "0", expr)
    expr = re.sub(r"\b[A-Za-z_]\w*\b", lambda m: defines.get(m.group(0)) or "0", expr)
    expr = expr.replace("&&", " and ").replace("||", " or ")
    expr = re.sub(r"!(?!=)", " not ", expr)
    if re.search(r"[^\w\s.()=<>!+\-*/]", expr):
        raise ValueError("cannot read #if %r" % expr)
    return bool(eval(expr, {"__builtins__": {}}, {}))


def preprocess(source):
    """An expanded program with its #define / #if / #ifdef / #ifndef / #elif / #else / #endif applied: the lines the
    GPU compiles. Enough for this pack's own directives (integer options, `defined`, &&, ||, !)."""
    defines, out, stack = {}, [], []          # stack: (this branch is on, a branch of this #if was taken, outer on)
    for line in source.split("\n"):
        d = re.match(r"\s*#\s*(\w+)\s*(.*)", line)
        word, rest = (d.group(1), d.group(2).split("//")[0].strip()) if d else (None, "")
        on = all(s[0] for s in stack)
        if word in ("if", "ifdef", "ifndef"):
            if word == "if":
                c = on and _condition(rest, defines)
            else:
                c = on and ((rest in defines) == (word == "ifdef"))
            stack.append((c, c, on))
        elif word == "elif":
            _, taken, outer = stack.pop()
            c = outer and not taken and _condition(rest, defines)
            stack.append((c, taken or c, outer))
        elif word == "else":
            _, taken, outer = stack.pop()
            stack.append((outer and not taken, True, outer))
        elif word == "endif":
            stack.pop()
        elif not on:
            continue
        elif word == "define":
            name, _, value = rest.partition(" ")
            defines[name] = value.strip() or "1"
        else:
            out.append(line)
    return "\n".join(out)


def compile_errors(glslang, name, source):
    """glslang's complaints about one program ('' when it compiles)."""
    stage = "vert" if name.endswith(".vsh") else "frag"
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, name + "." + stage)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(source)
        r = subprocess.run([glslang, path], capture_output=True, text=True)
    if r.returncode == 0:
        return ""
    return (r.stdout + r.stderr).replace(path, name).strip()


def header_problems():
    """Every source file of the pack names itself as ours and clean-room."""
    problems = []
    for dirpath, _, files in os.walk(SHADERS):
        for n in files:
            if n.endswith((".vsh", ".fsh", ".glsl", ".properties")):
                p = os.path.join(dirpath, n)
                with open(p, encoding="utf-8") as f:
                    head = f.read(600)
                if CLEAN_ROOM not in head or "GPL v2 or later" not in head:
                    problems.append("%s: no licence / clean-room header" % os.path.relpath(p, ROOT))
    if not os.path.isfile(os.path.join(ROOT, "LICENSE")):
        problems.append("LICENSE missing")
    return problems


# final.fsh constant -> WorldLookPass.java array, for the grade's one-table check (test_shader_pack.py)
GRADE_TABLE = {"CONTRAST": "CONTRAST", "SPLIT_TONE": "SPLIT_TONE", "SATURATION": "SATURATION", "VIGNETTE": "VIGNETTE",
               "EXPOSURE": "EXPOSURE", "VIBRANCE": "VIBRANCE", "GLOW": "GLOW", "GLOW_THRESHOLD": "GLOW_THRESHOLD",
               "GLOW_RADIUS": "GLOW_RADIUS", "SHADOW_TINT": "SHADOW_TINT", "HIGHLIGHT_TINT": "HIGHLIGHT_TINT"}


def _numbers(text):
    return [float(x) for x in re.findall(r"-?\d+(?:\.\d+)?", text)]


def enchanted_pairs(java, fsh):
    """[(name, WorldLookPass's Enchanted value, final.fsh's value)] per number, or None when the Java table has no
    Enchanted column (a third entry per array) yet."""
    pairs = []
    for const, arr in GRADE_TABLE.items():
        m = re.search(r"\[\]\s+%s\s*=\s*\{(.*?)\};" % arr, java, re.S)
        f = re.search(r"const (?:float|vec3) %s = ([^;]*);" % const, fsh)
        if not m or not f:
            return None
        rows = re.findall(r"\{([^{}]*)\}", m.group(1) + "}")   # a table of rows: { { r, g, b }, ... }
        columns = [_numbers(r) for r in rows] if rows else [[x] for x in _numbers(m.group(1))]
        if len(columns) < 3:
            return None
        want, got = columns[2], _numbers(f.group(1).replace("vec3", ""))
        if len(got) != len(want):
            return None
        pairs += [("%s[%d]" % (const, i), w, g) for i, (w, g) in enumerate(zip(want, got))]
    return pairs


def vec3_const(source, name):
    """A `const vec3 NAME = vec3(r, g, b) [* k];` (or `vec3(x)`) from a shader's source, as three floats."""
    m = re.search(r"const vec3 %s = vec3\(([^)]*)\)\s*(?:\*\s*(-?\d+(?:\.\d+)?))?\s*;" % name, source)
    if not m:
        raise ValueError("no const vec3 %s" % name)
    xs = _numbers(m.group(1))
    xs = xs * 3 if len(xs) == 1 else xs
    k = float(m.group(2)) if m.group(2) else 1.0
    return [x * k for x in xs]


def float_const(source, name, default=None):
    """A `const float NAME = x;` from a shader's source; `default` when the source has none."""
    m = re.search(r"const float %s = (-?\d+(?:\.\d+)?);" % name, source)
    if not m:
        if default is None:
            raise ValueError("no const float %s" % name)
        return default
    return float(m.group(1))


LUMA = (0.2126, 0.7152, 0.0722)


def code_of(source):
    """A shader's source without its comments, every run of whitespace one space: the lines the GPU runs, so a test that
    looks for a statement is not satisfied by a comment quoting it."""
    source = re.sub(r"/\*.*?\*/", " ", source, flags=re.S)
    source = re.sub(r"//[^\n]*", " ", source)
    return re.sub(r"\s+", " ", source)


def noon_light(light_source, upness, ndl, sun_through=1.0):
    """Luma of lib/light.glsl's surfaceLight at the lobby's noon (the high-sun row) for a face in full sky light with
    no ambient occlusion: `upness` its normal's dot with up, `ndl` with the sun, `sun_through` the shadow (1 = lit).
    Mirrors sunFacing (Lambert eased SUN_FACING_EASE of the way to its square root), skyFill's (SKY_FILL_SIDE +
    (1 - SKY_FILL_SIDE) x upness) and MIN_LIGHT, with the constants read from the source (test_noon_light_leaves_open_
    ground_its_colour pins the two expressions themselves)."""
    def lum(v):
        return sum(a * b for a, b in zip(v, LUMA))
    sun, sky = lum(vec3_const(light_source, "SUN_HIGH")), lum(vec3_const(light_source, "SKY_HIGH"))
    ease, side = float_const(light_source, "SUN_FACING_EASE"), float_const(light_source, "SKY_FILL_SIDE")
    ndl = max(ndl, 0.0)
    facing = ndl + (ndl ** 0.5 - ndl) * ease
    return (sun * facing * sun_through + sky * (side + (1.0 - side) * upness)
            + lum(vec3_const(light_source, "MIN_LIGHT")))


def ao_depth_tolerance(ao_source, base, view_depth, ortho, zoom=30.0, view_height=1080.0):
    """lib/ao.glsl's aoDepthTolerance in blocks, evaluated from the GLSL itself: the `return` under its
    `if (aoOrtho(projection))` (the RTS view) or the one after it (first person), with the file's float constants filled
    in and the RTS view's projection[1][1] = 2 / zoom (zoom = blocks of view height)."""
    m = re.search(r"float aoDepthTolerance\([^)]*\) \{\s*if \(aoOrtho\(projection\)\)\s*return ([^;]*);\s*return ([^;]*);"
                  r"\s*\}", ao_source)
    if not m:
        raise ValueError("lib/ao.glsl's aoDepthTolerance is not `if (aoOrtho(projection)) return ...; return ...;`")
    expr = (m.group(1) if ortho else m.group(2)).replace("projection[1][1]", "p11")
    consts = dict(re.findall(r"const float (\w+) = (-?\d+(?:\.\d+)?);", ao_source))
    expr = re.sub(r"\b[A-Z][A-Z0-9_]*\b", lambda c: consts[c.group(0)], expr)
    if re.search(r"[^\w\s.,()+\-*/]", expr) or set(re.findall(r"[A-Za-z_]\w*", expr)) - {
            "max", "base", "viewDepth", "viewHeight", "p11"}:
        raise ValueError("cannot evaluate %r" % expr)
    return eval(expr, {"__builtins__": {}}, {"max": max, "base": base, "viewDepth": view_depth,
                                            "viewHeight": view_height, "p11": 2.0 / zoom})


def _smoothstep(a, b, x):
    t = min(max((x - a) / (b - a), 0.0), 1.0)
    return t * t * (3.0 - 2.0 * t)


def split_tone_shadows(source, lc):
    """final.fsh's split-tone shadow weight at luma lc: its own `float shadows = ...;` line, with the file's float
    constants filled in, evaluated as GLSL would (smoothstep only)."""
    m = re.search(r"float shadows = ([^;]*);", source)
    if not m:
        raise ValueError("final.fsh has no `float shadows = ...;` line")
    consts = {k: v for k, v in re.findall(r"const float (\w+) = (-?\d+(?:\.\d+)?);", source)}
    expr = re.sub(r"\b[A-Z][A-Z0-9_]*\b", lambda c: consts[c.group(0)], m.group(1))
    if re.search(r"[^\w\s.,()+\-*]", expr) or set(re.findall(r"[A-Za-z_]\w*", expr)) - {"smoothstep", "lc"}:
        raise ValueError("cannot evaluate %r" % expr)
    return eval(expr, {"__builtins__": {}}, {"smoothstep": _smoothstep, "lc": lc})


def find_glslang(given=None):
    return given or shutil.which("glslang") or shutil.which("glslangValidator")


def check(glslang):
    """All problems found, as printable lines."""
    problems = header_problems()
    for name in programs():
        source = expand(os.path.join(SHADERS, name))
        variants = [({}, source)]
        used = {m: v for m, v in LOADER_MACROS.items() if m in source}
        if used:
            variants.append((used, with_macros(source, used)))
        for macros, src in variants:
            err = compile_errors(glslang, name, src)
            if err:
                label = name + (" with " + ", ".join(macros) if macros else "")
                problems.append("%s:\n%s" % (label, err))
    return problems


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--glslang", help="path to glslang (default: glslang or glslangValidator on PATH)")
    args = ap.parse_args()
    glslang = find_glslang(args.glslang)
    if not glslang:
        print("glslang not found (apt-get install glslang-tools)", file=sys.stderr)
        return 2
    problems = check(glslang)
    for p in problems:
        print(p)
    print("%d programs, %d problems" % (len(programs()), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
