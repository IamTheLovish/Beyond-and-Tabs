#!/usr/bin/env python3
"""Which block textures the RTS camera actually sees on our faction buildings, face by face - and which of them our
realistic pack (tools/gen_blocks.py) does not cover yet.

Why (Oct 15, the texture audit): tools/structure_preview.py guesses a block's textures from its name, and the guess
hid real gaps - a quartz block counted as covered by quartz_block_bottom (the game draws quartz_block_side and
quartz_block_top on it), a stripped log's top counted as its side (the game draws stripped_<wood>_log_top: vanilla
16x on every stripped log seen from above). This tool asks the game's own data instead: each structure block's
blockstate picks its model(s) (variants with their x / y rotation, or multipart), the model's parent chain gives the
elements and the texture of every face, and a face hidden by a full neighbour (its cullface) is not counted.

What is counted, per texture: the face area the RTS camera can see - up faces in full, side faces at half weight
(the camera looks down at ~45-60 degrees and turns), down faces never. A "partial" block is one whose seen faces mix
our textures with vanilla ones: a realistic side under a vanilla 16x top reads as a mistake from the camera, so
those come first in the report.

    python3 tools/face_census.py SOURCE [--out DIR] [--pack DIR] [--faction NAME]
SOURCE: the Minecraft client jar (1.20.1, official - the face-census workflow downloads it sha1-checked), or a
directory holding blocks_states.json and blocks_models.json (the PrismarineJS minecraft-assets data/<version> layout,
for a run without the jar). --pack: our generated pack's block folder (default: resourcepacks/...; when it does not
exist, the names gen_blocks.py generates). Writes DIR/census.md and DIR/census.json (default: no files, the report
is printed). Needs nbtlib (pip install nbtlib).
"""
import argparse
import collections
import json
import math
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from structure_preview import STRUCT_DIR, faction_of_structures   # noqa: E402

PACK = os.path.join(ROOT, "resourcepacks", "BeyondAndTabs-Realistic", "assets", "minecraft", "textures", "block")
SKIP = {"air", "cave_air", "void_air", "structure_void", "barrier", "light", "jigsaw", "structure_block"}
FLUIDS = {"water", "lava"}
# blocks that hide none of their neighbours' faces (see-through or cut-out): the game draws the face behind them.
# Anchored: packed_ice / blue_ice and muddy_mangrove_roots are opaque
SEE_THROUGH = re.compile(r"glass$|glass_pane$|leaves$|^(ice|frosted_ice)$|^slime_block$|^honey_block$|^spawner$|"
                         r"^beacon$|^mangrove_roots$|^scaffolding$")
# ...but two of the same kind side by side draw nothing between them (HalfTransparentBlock.skipRendering: glass,
# stained / tinted glass, ice, slime, honey); mangrove roots skip only their side faces between two roots
SAME_SKIP = re.compile(r"glass$|^(ice|frosted_ice)$|^slime_block$|^honey_block$")
HORIZONTAL = {"north", "south", "west", "east"}
OPPOSITE = {"up": "down", "down": "up", "north": "south", "south": "north", "west": "east", "east": "west"}
SIDE_WEIGHT = 0.5
DIRS = ("down", "up", "north", "south", "west", "east")
STEP = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0),
        "east": (1, 0, 0)}
# a blockstate's x rotation turns up -> north -> down -> south (piston facing=up is x=270 of the north-facing model);
# y turns north -> east -> south -> west (clockwise seen from above)
X_TURN = {"up": "north", "north": "down", "down": "south", "south": "up", "east": "east", "west": "west"}
Y_TURN = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}
# a structure palette entry may list no properties (hand-made or converted files: 30-odd walls, a bone block); the game
# then uses the block's default state. These are the defaults of the common shapes; a variant key naming a property
# the entry still lacks matches any value (the textures rarely depend on it)
DEFAULTS = {"axis": "y", "half": "bottom", "type": "bottom", "shape": "straight", "up": "true", "facing": "north"}


def turn(d, x, y):
    for _ in range((x // 90) % 4):
        d = X_TURN[d]
    for _ in range((y // 90) % 4):
        d = Y_TURN[d]
    return d


def strip(ref):
    """'minecraft:block/stone' -> 'block/stone'."""
    return ref.split(":", 1)[1] if ":" in ref else ref


class Vanilla:
    """The game's blockstates and block models, from the client jar or a PrismarineJS data directory."""

    def __init__(self, source):
        self.models_cache = {}
        if os.path.isdir(source):
            self.states = json.load(open(os.path.join(source, "blocks_states.json")))
            self.models = {"block/" + k: v for k, v in json.load(open(os.path.join(source, "blocks_models.json"))).items()}
            self.label = "PrismarineJS data " + os.path.basename(os.path.normpath(source))
        else:
            z = zipfile.ZipFile(source)
            self.states, self.models = {}, {}
            for n in z.namelist():
                m = re.match(r"assets/minecraft/blockstates/([a-z0-9_]+)\.json$", n)
                if m:
                    self.states[m.group(1)] = json.loads(z.read(n))
                m = re.match(r"assets/minecraft/models/(block/[a-z0-9_/]+)\.json$", n)
                if m:
                    self.models[m.group(1)] = json.loads(z.read(n))
            self.label = os.path.basename(source)

    def model(self, ref):
        """(textures, elements, builtin) of a model reference, the parent chain merged: the child's textures win, the
        nearest model with elements gives them; builtin = 'entity' when a block entity renderer draws the block."""
        ref = strip(ref)
        if ref in self.models_cache:
            return self.models_cache[ref]
        textures, elements, builtin, seen = {}, None, None, set()
        cur = ref
        while cur and cur not in seen:
            seen.add(cur)
            if cur.startswith("builtin/"):
                builtin = cur.split("/", 1)[1]
                break
            m = self.models.get(cur)
            if m is None:
                break
            for k, v in m.get("textures", {}).items():
                textures.setdefault(k, v)
            if elements is None and "elements" in m:
                elements = m["elements"]
            cur = strip(m["parent"]) if "parent" in m else None
        self.models_cache[ref] = (textures, elements, builtin)
        return self.models_cache[ref]

    def applied(self, name, props):
        """The model entries ({model, x, y}) a block state draws."""
        st = self.states.get(name)
        if st is None:
            return []
        out = []
        if "variants" in st:
            given = props
            props = dict(DEFAULTS, **props)
            for key, v in st["variants"].items():
                if key == "" or key == "normal" or all(
                        props.get(k) == val or (k not in given and k not in DEFAULTS)
                        for k, val in (kv.split("=", 1) for kv in key.split(","))):
                    out.append(v[0] if isinstance(v, list) else v)
                    break
            if not out and not given:   # a default that this block does not use (lightning_rod faces up)
                v = next(iter(st["variants"].values()))
                out.append(v[0] if isinstance(v, list) else v)
        props = dict(DEFAULTS, **props) if "multipart" in st and not props else props
        for part in st.get("multipart", []):
            if "when" not in part or self._when(part["when"], props):
                v = part["apply"]
                out.append(v[0] if isinstance(v, list) else v)
        return out

    def _when(self, cond, props):
        if "OR" in cond:
            return any(self._when(c, props) for c in cond["OR"])
        if "AND" in cond:
            return all(self._when(c, props) for c in cond["AND"])
        return all(props.get(k) in str(v).split("|") for k, v in cond.items())


def texture_of(textures, ref):
    """Follows '#key' references to the final texture name ('block/stone' -> 'stone'), or None."""
    for _ in range(8):
        if ref is None or not ref.startswith("#"):
            break
        ref = textures.get(ref[1:])
    if ref is None:
        return None
    ref = strip(ref)
    return ref[6:] if ref.startswith("block/") else None


def face_area(el, d):
    f, t = el["from"], el["to"]
    dx, dy, dz = (abs(t[i] - f[i]) / 16.0 for i in range(3))
    return {"up": dx * dz, "down": dx * dz, "north": dx * dy, "south": dx * dy, "west": dz * dy, "east": dz * dy}[d]


def solid_sides(elements):
    """The model directions on which the elements TOGETHER cover the whole block face (a slab's bottom, a stair's back
    - two elements - a full cube's six): a neighbour's face against such a side is hidden (the game culls by the union
    of a block's face shapes, not by full cubes). Rasterised on the 16 x 16 model grid; rotated elements never count."""
    import numpy as np
    sides = {d: np.zeros((16, 16), bool) for d in OPPOSITE}
    for el in elements:
        if "rotation" in el and el["rotation"].get("angle", 0) != 0:
            continue
        f, t = el["from"], el["to"]
        for d, axis, at_low in (("down", 1, True), ("up", 1, False), ("north", 2, True), ("south", 2, False),
                                ("west", 0, True), ("east", 0, False)):
            if not (f[axis] <= 0 if at_low else t[axis] >= 16):
                continue
            a, b = [i for i in range(3) if i != axis]
            ra = slice(max(0, math.ceil(f[a])), min(16, math.floor(t[a])))
            rb = slice(max(0, math.ceil(f[b])), min(16, math.floor(t[b])))
            sides[d][ra, rb] = True
    return {d for d, g in sides.items() if g.all()}


class Faces:
    """Per block state: its seen faces as (direction after rotation, cullface after rotation or None, texture,
    area); 'entity' when it has no geometry of its own (a block entity renderer draws it: chests, banners, signs,
    heads); the sides on which it hides its neighbours' faces; and 'missing' (no blockstate - a modded block)."""

    def __init__(self, vanilla):
        self.v = vanilla
        self.cache = {}

    def of(self, name, props):
        key = (name, tuple(sorted(props.items())))
        if key in self.cache:
            return self.cache[key]
        faces, solid, missing, any_geometry = [], set(), False, False
        entries = self.v.applied(name, props)
        if not entries:
            missing = name not in FLUIDS
        for e in entries:
            textures, elements, builtin = self.v.model(e["model"])
            if builtin == "entity" or not elements:
                continue
            any_geometry = True
            x, y = int(e.get("x", 0)), int(e.get("y", 0))
            solid |= {turn(d, x, y) for d in solid_sides(elements)}
            for el in elements:
                for d, face in el.get("faces", {}).items():
                    tex = texture_of(textures, face.get("texture"))
                    if tex is None:
                        continue
                    cull = face.get("cullface")
                    if cull and cull == OPPOSITE[d]:
                        continue   # an inner face of a cut-out shell (seen only through its holes): not counted twice
                    faces.append((turn(d, x, y), turn(cull, x, y) if cull else None, tex, face_area(el, d)))
        entity = bool(entries) and not any_geometry and name not in FLUIDS
        if SEE_THROUGH.search(name):
            solid = set()
        self.cache[key] = (faces, entity, solid, missing)
        return self.cache[key]


def load_structure(path):
    import nbtlib
    nbt = nbtlib.load(path)
    pal = nbt["palette"] if "palette" in nbt else nbt["palettes"][0]
    states = [(str(e["Name"]).replace("minecraft:", ""),
               {str(k): str(v) for k, v in e.get("Properties", {}).items()}) for e in pal]
    grid = {}
    for b in nbt["blocks"]:
        name, props = states[int(b["state"])]
        if name in SKIP:
            continue
        grid[tuple(int(v) for v in b["pos"])] = (name, props)
    return grid


def census(vanilla, structures, ours):
    faces = Faces(vanilla)
    seen = collections.Counter()               # texture -> seen weight
    top = collections.Counter()                # texture -> up-face area
    users = collections.defaultdict(collections.Counter)   # texture -> block name -> weight
    per_block = collections.defaultdict(collections.Counter)   # block name -> texture -> weight
    entity, missing = collections.Counter(), collections.Counter()
    per_struct = {}
    for sname, path in structures:
        grid = load_structure(path)
        mine = collections.Counter()
        for pos, (name, props) in grid.items():
            def neighbour(d):
                s = STEP[d]
                return grid.get((pos[0] + s[0], pos[1] + s[1], pos[2] + s[2]))

            def hidden(d, me=name):
                """Is my face towards d hidden by the neighbour there?"""
                n = neighbour(d)
                if n is None:
                    return False
                if OPPOSITE[d] in faces.of(*n)[2]:
                    return True
                return n[0] == me and (SAME_SKIP.search(me) is not None
                                       or (me == "mangrove_roots" and d in HORIZONTAL))
            if name in FLUIDS:   # fluids are drawn by the fluid renderer: still texture on top, flowing on the sides
                for d in ("up", "north", "south", "west", "east"):
                    n = neighbour(d)
                    if n is not None and (n[0] == name or OPPOSITE[d] in faces.of(*n)[2]):
                        continue
                    tex = name + ("_still" if d == "up" else "_flow")
                    w = 1.0 if d == "up" else SIDE_WEIGHT
                    seen[tex] += w
                    top[tex] += w if d == "up" else 0
                    users[tex][name] += w
                    per_block[name][tex] += w
                    mine[tex in ours] += w
                continue
            fs, ent, _, miss = faces.of(name, props)
            if ent:
                entity[name] += 1
            if miss:
                missing[name] += 1
            for d, cull, tex, area in fs:
                if d == "down" or area <= 0:
                    continue
                if cull and hidden(cull):
                    continue
                w = area * (1.0 if d == "up" else SIDE_WEIGHT)
                seen[tex] += w
                if d == "up":
                    top[tex] += area
                users[tex][name] += w
                per_block[name][tex] += w
                mine[tex in ours] += w
        tot = mine[True] + mine[False]
        per_struct[sname] = round(mine[True] / tot, 3) if tot else None
    return seen, top, users, per_block, entity, missing, per_struct


def our_textures(pack):
    if os.path.isdir(pack):
        return {f[:-4] for f in os.listdir(pack) if f.endswith(".png") and not re.search(r"_[ns]\.png$", f)}, pack
    import gen_blocks
    return set(gen_blocks.TARGETS) | {"water_still", "water_flow"}, "tools/gen_blocks.py TARGETS"


def report(vanilla, ours, src, seen, top, users, per_block, entity, missing, per_struct, faction_of):
    total = sum(seen.values())
    covered = sum(w for t, w in seen.items() if t in ours)
    L = ["# Face census: the block textures the RTS camera sees on our buildings", "",
         "Source: %s; our pack: %s (%d textures). Seen weight = up-face area + %.1f x side-face area (block faces "
         "hidden by a full neighbour not counted)." % (vanilla.label, src, len(ours), SIDE_WEIGHT), "",
         "**Our pack covers %.1f%% of the seen faces** (%d structures)." % (100 * covered / max(total, 1e-9),
                                                                          len(per_struct)), ""]
    partial = []
    for b, tw in per_block.items():
        ourw = sum(w for t, w in tw.items() if t in ours)
        vanw = {t: w for t, w in tw.items() if t not in ours}
        if ourw > 0 and vanw:
            partial.append((sum(vanw.values()), b, ourw, vanw))
    partial.sort(reverse=True)
    L += ["## Partial blocks: our textures on some faces, vanilla on others (fix these first)", "",
          "| block | vanilla faces (weight) | our faces (weight) | vanilla textures |", "|---|---:|---:|---|"]
    for vw, b, ourw, vanw in partial[:60]:
        L.append("| %s | %.0f | %.0f | %s |" % (b, vw, ourw, ", ".join(
            "%s %.0f" % (t, w) for t, w in sorted(vanw.items(), key=lambda kv: -kv[1]))))
    L += ["", "## Uncovered textures by seen weight", "",
          "| texture | seen | of it up faces | share of all seen | blocks using it |", "|---|---:|---:|---:|---|"]
    for t, w in seen.most_common():
        if t in ours:
            continue
        if w < 0.0005 * total:
            break
        L.append("| %s | %.0f | %.0f | %.2f%% | %s |" % (t, w, top[t], 100 * w / total, ", ".join(
            b for b, _ in users[t].most_common(4))))
    L += ["", "## Covered textures by seen weight", "", "| texture | seen | share |", "|---|---:|---:|"]
    for t, w in seen.most_common():
        if t in ours:
            L.append("| %s | %.0f | %.2f%% |" % (t, w, 100 * w / total))
    unused = sorted(ours - set(seen))
    L += ["", "Our textures no building shows (terrain, trees and the map use them): " + (", ".join(unused) or "none")]
    if entity:
        L += ["", "Drawn by block entity renderers (entity textures, outside a block pack): " + ", ".join(
            "%s %d" % kv for kv in entity.most_common())]
    if missing:
        L += ["", "No blockstate found (modded or renamed blocks): " + ", ".join("%s %d" % kv for kv in missing.most_common())]
    fac = collections.defaultdict(list)
    for s, share in per_struct.items():
        if share is not None:
            fac[faction_of.get(s, "unreferenced")].append(share)
    L += ["", "## Coverage by faction (mean share of seen faces per building)", "", "| faction | buildings | mean | lowest |",
          "|---|---:|---:|---:|"]
    for f, v in sorted(fac.items()):
        L.append("| %s | %d | %.0f%% | %.0f%% |" % (f, len(v), 100 * sum(v) / len(v), 100 * min(v)))
    data = {"source": vanilla.label, "ours": src, "covered_share": round(covered / max(total, 1e-9), 4),
            "partial": [{"block": b, "vanilla": {t: round(w, 1) for t, w in vanw.items()}, "ours": round(ourw, 1)}
                        for _, b, ourw, vanw in partial],
            "uncovered": {t: round(w, 1) for t, w in seen.most_common() if t not in ours},
            "covered": {t: round(w, 1) for t, w in seen.most_common() if t in ours},
            "entity": dict(entity), "missing": dict(missing), "per_structure": per_struct}
    return "\n".join(L) + "\n", data


def _norm(x):
    """A model JSON with every 'minecraft:' prefix dropped (vanilla writes both 'block/x' and 'minecraft:block/x')."""
    if isinstance(x, dict):
        return {k: _norm(v) for k, v in x.items()}
    if isinstance(x, list):
        return [_norm(v) for v in x]
    if isinstance(x, str) and x.startswith("minecraft:"):
        return x[len("minecraft:"):]
    return x


def check_overrides(vanilla):
    """gen_blocks.MODEL_OVERRIDES copy vanilla block models by hand to add a render type; each must equal the game's
    own model in everything else, or the pack would change a block's shape. Returns the list of differences."""
    import gen_blocks
    bad = []
    for name, model in gen_blocks.MODEL_OVERRIDES.items():
        theirs = vanilla.models.get("block/" + name)
        if theirs is None:
            bad.append("%s: no such vanilla model" % name)
        elif _norm(model) != _norm(theirs):
            bad.append("%s: differs from the game's model" % name)
    return bad


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source")
    ap.add_argument("--out")
    ap.add_argument("--pack", default=PACK)
    ap.add_argument("--faction")
    ap.add_argument("--check-overrides", action="store_true",
                    help="only compare gen_blocks.MODEL_OVERRIDES with the game's models; exit 1 on a difference")
    a = ap.parse_args()
    vanilla = Vanilla(a.source)
    if a.check_overrides:
        bad = check_overrides(vanilla)
        import gen_blocks
        print("model overrides vs %s: %d of %d match" % (vanilla.label, len(gen_blocks.MODEL_OVERRIDES) - len(bad),
                                                         len(gen_blocks.MODEL_OVERRIDES)))
        for b in bad:
            print("  " + b)
        sys.exit(1 if bad else 0)
    ours, src = our_textures(a.pack)
    faction_of = faction_of_structures()
    names = sorted(n for n, f in faction_of.items() if not a.faction or f == a.faction)
    structures = [(n, os.path.join(STRUCT_DIR, n + ".nbt")) for n in names
                  if os.path.exists(os.path.join(STRUCT_DIR, n + ".nbt"))]
    md, data = report(vanilla, ours, src, *census(vanilla, structures, ours), faction_of)
    if a.out:
        os.makedirs(a.out, exist_ok=True)
        open(os.path.join(a.out, "census.md"), "w").write(md)
        json.dump(data, open(os.path.join(a.out, "census.json"), "w"), indent=1)
    print(md)


if __name__ == "__main__":
    main()
