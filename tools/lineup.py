#!/usr/bin/env python3
"""A scale lineup: several design/models DSL files stood side by side in ONE DSL file, at their true size, with a
block ruler at the left - so concepts.yml renders them in one frame and a new unit is judged against the others
(height, bulk, how it reads at the same distance) instead of alone in a frame that always fills it.

Tabs (claude/avatar.md) is the measuring stick: put him first and every unit is seen against the same young man.

    python3 tools/lineup.py --out design/lineups/sunforged_t1.json \\
        design/avatar/tabs_thumbs_up.json design/models/sunforged_crossbow_levy.json ...
    gh workflow run concepts.yml --ref main -f models=design/lineups/sunforged_t1.json

Each model keeps its own palette (names prefixed per model), bones and pose; it is moved along x so the posed
figures stand GAP px apart, centred on z = 0. The ruler is a column of 1-block (16 px) cubes, alternating two
greys, RULER_BLOCKS high, with a thin tick every half block - 2 blocks is a door, 1.8 a player's height.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_model import posed_cubes  # noqa: E402
import render_concept  # noqa: E402

GAP = 8.0
RULER_BLOCKS = 3


def posed_extent(path):
    m = render_concept.load_model(path)
    xs, zs = [], []
    for pts, _ in posed_cubes(m):
        xs += [p[0] for p in pts]
        zs += [p[2] for p in pts]
    return (min(xs), max(xs)), (min(zs), max(zs))


def shift(cube_or_bone, key, dx, dz):
    v = cube_or_bone.get(key)
    if v is not None:
        cube_or_bone[key] = [round(v[0] + dx, 3), v[1], round(v[2] + dz, 3)]


def build(paths, name):
    out = {"name": name, "faction": "lineup", "role": "scale lineup (tools/lineup.py)", "scale_px_per_block": 16,
           "palette": {"ruler_a": "#e4e0d8", "ruler_b": "#9a958c", "tick": "#3a3833"}, "emissive": [], "bones": [],
           "pose": {}}
    s = 16.0
    ruler = {"name": "ruler", "pivot": [0, 0, 0], "cubes": []}
    rx = -s * 0.5 - GAP          # the ruler's right edge sits GAP left of the first figure (moved below)
    for k in range(RULER_BLOCKS):
        ruler["cubes"].append({"from": [rx - 4, k * s, -2], "size": [4, s, 4], "color": "ruler_a" if k % 2 == 0 else "ruler_b"})
        for half in (0.0, 0.5):
            ruler["cubes"].append({"from": [rx - 6, k * s + half * s - 0.15, -0.5], "size": [2.2 if half == 0 else 1.4, 0.3, 1],
                                   "color": "tick"})
    out["bones"].append(ruler)
    x = 0.0
    for i, path in enumerate(paths):
        with open(path) as f:
            m = json.load(f)
        (x0, x1), (z0, z1) = posed_extent(path)
        dx, dz = x - x0, -(z0 + z1) / 2
        x += (x1 - x0) + GAP
        pre = "m%d_" % i
        for k, v in m["palette"].items():
            out["palette"][pre + k] = v
        out["emissive"] += [pre + e for e in m.get("emissive", ["sun"]) if e in m["palette"]]
        for b in m["bones"]:
            nb = dict(b)
            nb["name"] = pre + b["name"]
            if b.get("parent"):
                nb["parent"] = pre + b["parent"]
            shift(nb, "pivot", dx, dz)
            nb["cubes"] = []
            for c in b.get("cubes", []):
                nc = dict(c)
                nc["color"] = pre + c["color"]
                shift(nc, "from", dx, dz)
                shift(nc, "pivot", dx, dz)
                nb["cubes"].append(nc)
            out["bones"].append(nb)
        for k, r in (m.get("pose") or {}).items():
            out["pose"][pre + k] = r
        print(f"  {m['name']:<28} x {dx:7.1f}  width {x1 - x0:5.1f} px")
    # centre the whole row on x = 0 so the camera's target is the middle of the lineup
    lo = rx - 6
    mid = (lo + x - GAP) / 2
    for b in out["bones"]:
        shift(b, "pivot", -mid, 0)
        for c in b["cubes"]:
            shift(c, "from", -mid, 0)
            shift(c, "pivot", -mid, 0)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("models", nargs="+")
    ap.add_argument("--out", required=True)
    ap.add_argument("--name", default=None)
    a = ap.parse_args()
    name = a.name or "Lineup - " + ", ".join(os.path.splitext(os.path.basename(p))[0] for p in a.models)
    m = build(a.models, name[:120])
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w") as f:
        json.dump(m, f, indent=1)
    render_concept.load_model(a.out)   # validate what we wrote
    print(f"wrote {a.out}: {len(m['bones'])} bones, {sum(len(b['cubes']) for b in m['bones'])} cubes")


if __name__ == "__main__":
    main()
