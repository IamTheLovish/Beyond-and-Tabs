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
# option-screen switches (lib/settings.glsl `#define NAME value // [...]`) compiled at a non-default value as well: one
# variant per entry, each applied to the programs that read it
OPTION_VARIANTS = [{"MATERIALS": "0"}, {"WATER_REALISM": "0"}, {"CAUSTICS": "0"}, {"SSR": "0"}, {"CAUSTICS": "0", "SSR": "0"}]


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


def with_options(source, options):
    """The source with each option's `#define NAME value` line set to the given value (the loader rewrites the same
    line when a player changes the option)."""
    for name, value in options.items():
        source, n = re.subn(r"^(\s*#define\s+%s\s+)\S+" % re.escape(name), r"\g<1>" + value, source, flags=re.M)
        if n == 0:
            raise ValueError("option %s is not defined" % name)
    return source


def reads_option(source, name):
    """Whether the program tests the option anywhere past its definition."""
    return len(re.findall(r"\b%s\b" % re.escape(name), source)) > 1


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


def find_glslang(given=None):
    return given or shutil.which("glslang") or shutil.which("glslangValidator")


def check(glslang, options=None):
    """All problems found, as printable lines. options: option values every program is compiled at (default: the
    pack's own), on top of which OPTION_VARIANTS are tried."""
    problems = header_problems()
    for name in programs():
        base = expand(os.path.join(SHADERS, name))
        base = with_options(base, {k: v for k, v in (options or {}).items() if reads_option(base, k) or
                                   re.search(r"#define\s+%s\b" % re.escape(k), base)})
        sources = [("", base)]
        for opts in OPTION_VARIANTS:
            if all(reads_option(base, o) for o in opts):
                sources.append((", ".join("%s=%s" % kv for kv in opts.items()), with_options(base, opts)))
        for opt_label, source in sources:
            variants = [({}, source)]
            used = {m: v for m, v in LOADER_MACROS.items() if m in source}
            if used:
                variants.append((used, with_macros(source, used)))
            for macros, src in variants:
                err = compile_errors(glslang, name, src)
                if err:
                    label = name + (" with " + ", ".join(macros) if macros else "") + (
                        " [" + opt_label + "]" if opt_label else "")
                    problems.append("%s:\n%s" % (label, err))
    return problems


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--glslang", help="path to glslang (default: glslang or glslangValidator on PATH)")
    ap.add_argument("--option", action="append", default=[], metavar="NAME=VALUE",
                    help="compile every program with this option value (repeatable)")
    args = ap.parse_args()
    options = dict(o.split("=", 1) for o in args.option)
    settings = open(os.path.join(SHADERS, "lib", "settings.glsl"), encoding="utf-8").read()
    for o in options:
        if not re.search(r"^#define\s+%s\s" % re.escape(o), settings, re.M):
            print("no option %s in lib/settings.glsl" % o, file=sys.stderr)
            return 2
    glslang = find_glslang(args.glslang)
    if not glslang:
        print("glslang not found (apt-get install glslang-tools)", file=sys.stderr)
        return 2
    problems = check(glslang, options)
    for p in problems:
        print(p)
    print("%d programs, %d problems" % (len(programs()), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
