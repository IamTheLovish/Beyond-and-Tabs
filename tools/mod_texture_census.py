#!/usr/bin/env python3
"""Which texture files every building-block mod block draws with - numbers and paths only, no image leaves the run.

Why (lovish, Oct 30, via Itachi 2's research: "repaint mod blocks 256x"): the biggest cause of the "toy" look is 16x
mod blocks sitting beside our 128-256x LabPBR blocks. Before we paint anything we need to know, per mod block, which
texture files its models end on, and whether each lives in the vanilla namespace - covered by our pack once
tools/gen_blocks.py has that name - or the mod's own (a new texture of ours at that path). Macaw's, Chipped, Decorative
Blocks and the rest ship their models in their jars; the dev sandbox cannot download the jars, CI can (the block-ids
workflow already has them in the Gradle cache), so this runs there.

For every jar: blockstates -> the models each state uses (variants and multipart) -> the parent chain (models from
any scanned jar; vanilla parents such as block/cube_all only add texture variables) -> the final texture ids. Per
texture: its namespace, whether a jar ships it and its pixel size. Writes JSON:
  {"blocks": {"mcwroofs:thatch_steep_roof": ["mcwroofs:block/thatch", ...]},
   "textures": {"mcwroofs:block/thatch": {"ns": "mcwroofs", "shipped": true, "size": [16, 16]}},
   "ours": ["minecraft:block/stone", ...]  - the vanilla names our pack already paints (from tools/gen_blocks.py)}

Usage: python3 -I tools/mod_texture_census.py OUT.json JAR [JAR ...]
"""
import json
import os
import re
import struct
import sys
import zipfile

MODS = ("mcw", "chipped", "decorative_blocks", "domum_ornamentum", "framedblocks", "supplementaries", "reignofnether")


def ident(ref, default_ns="minecraft"):
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = default_ns, ref
    return ns, path


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    w, h = struct.unpack(">II", data[16:24])
    return [w, h]


def load_jars(paths):
    """namespace/kind/path -> bytes for blockstates, models and block textures across all jars."""
    files = {}
    for p in paths:
        try:
            z = zipfile.ZipFile(p)
        except (zipfile.BadZipFile, OSError):
            continue
        for name in z.namelist():
            m = re.match(r"assets/([^/]+)/(blockstates|models|textures)/(.+)$", name)
            if not m:
                continue
            ns, kind, rest = m.groups()
            if kind == "textures" and not rest.endswith(".png"):
                continue
            files[(ns, kind, rest)] = (p, name)
    return files


def read(files, key, cache={}):
    if key not in files:
        return None
    if key not in cache:
        p, name = files[key]
        with zipfile.ZipFile(p) as z:
            cache[key] = z.read(name)
    return cache[key]


def model_refs(state_json):
    out = set()
    for v in (state_json.get("variants") or {}).values():
        for e in (v if isinstance(v, list) else [v]):
            if isinstance(e, dict) and "model" in e:
                out.add(e["model"])
    for part in state_json.get("multipart") or []:
        a = part.get("apply")
        for e in (a if isinstance(a, list) else [a]):
            if isinstance(e, dict) and "model" in e:
                out.add(e["model"])
    return out


def textures_of(files, model_ref, depth=0):
    """The texture variables a model resolves to, following its parents (child variables override the parent's)."""
    if depth > 12:
        return {}
    ns, path = ident(model_ref)
    raw = read(files, (ns, "models", path + ".json"))
    if raw is None:
        return {}           # a vanilla parent (cube_all, stairs, ...): adds no texture values of its own
    try:
        j = json.loads(raw)
    except ValueError:
        return {}
    tex = {}
    if "parent" in j:
        tex.update(textures_of(files, j["parent"], depth + 1))
    tex.update(j.get("textures") or {})
    return tex


def resolve(tex):
    out = set()
    for k, v in tex.items():
        seen = 0
        while isinstance(v, str) and v.startswith("#") and seen < 10:
            v = tex.get(v[1:])
            seen += 1
        if isinstance(v, str) and not v.startswith("#") and k != "particle":
            ns, path = ident(v)
            out.add(ns + ":" + path)
    return out


def ours_vanilla():
    """Vanilla texture names our pack paints: the TARGETS keys of tools/gen_blocks.py (read as text - no import)."""
    here = os.path.dirname(os.path.abspath(__file__))
    src = open(os.path.join(here, "gen_blocks.py"), encoding="utf-8").read()
    names = set(re.findall(r'^\s+"([a-z0-9_]+)": dict\(luma=', src, re.M))
    names |= set(re.findall(r'^TARGETS\["([a-z0-9_]+)"\]', src, re.M))
    return sorted("minecraft:block/" + n for n in names)


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    out, jars = sys.argv[1], sys.argv[2:]
    files = load_jars(jars)
    blocks, textures = {}, {}
    for (ns, kind, rest) in list(files):
        if kind != "blockstates" or not ns.startswith(MODS):
            continue
        try:
            state = json.loads(read(files, (ns, kind, rest)))
        except ValueError:
            continue
        used = set()
        for mref in model_refs(state):
            used |= resolve(textures_of(files, mref))
        bid = ns + ":" + rest[:-len(".json")]
        blocks[bid] = sorted(used)
        for t in used:
            if t in textures:
                continue
            tns, tpath = t.split(":", 1)
            data = read(files, (tns, "textures", tpath + ".png"))
            textures[t] = {"ns": tns, "shipped": data is not None, "size": png_size(data) if data else None}
    json.dump({"blocks": blocks, "textures": textures, "ours": ours_vanilla()}, open(out, "w"), indent=1, sort_keys=True)
    mod_tex = sum(1 for t in textures.values() if t["ns"] != "minecraft")
    print("[Mod textures] %d blocks, %d textures (%d in the mods' own namespaces, %d vanilla)"
          % (len(blocks), len(textures), mod_tex, len(textures) - mod_tex))


if __name__ == "__main__":
    main()
