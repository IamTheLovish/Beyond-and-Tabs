#!/usr/bin/env python3
"""A building's faces, region by region: depth planes, block variety, the biggest single block's share, relief
shapes and speckle - checked against the per-region rules of a building checklist.

Why (lovish, Oct 28 16:57, on the Engine Shed T1 greybox): "just needs more block variety to help with texturing ...
More dimensions and architecture details". The pilot's critics drifted because nothing was measured
(design/vehicle_labs/LESSONS.md, "Pilot rounds 1-5"); this turns "more variety, more depth" into numbers a builder can
run on every build and a critic can cite.

A region is {"name", "face", "box": [x0, x1, y0, y1, z0, z1]} (inclusive cells). "face" is where it is SEEN from:
west (from -x: the front of a lab), east, north (from -z), south (from +z: the side the RTS camera sees), up (roofs).
In each cell of the face's plane, the first non-air block inside the box along the view is what that cell shows.
  planes      depths holding at least PLANE_MIN % of the face's cells (a lone ornament is not a plane)
  blocks      distinct MATERIALS seen (a block and its stairs / slab / wall are one); top_block / top_share the
              biggest, in %
  shapes      % of cells that are stairs, slabs or walls (the relief that reads as cornices, drips and arrises)
  speckle     % of cells with no 4-neighbour of the same block (random scatter reads as noise at 32 px; courses,
              frames and panels do not)
Rules (design/vehicle_labs/briefs/brass_t1_checklist.md): planes >= 2, blocks >= 3, top_share <= 40.

    python3 tools/facade_audit.py BUILDING.nbt REGIONS.json     (prints a line per region and its faults)
"""
import json
import os
import sys

PLANE_MIN = 5               # % of a face's cells before a depth counts as a plane
MIN_PLANES, MIN_BLOCKS, MAX_SHARE = 2, 3, 40
SHAPES = ("_stairs", "_slab", "_wall")
# face -> (depth axis, +1 if the viewer stands on the high side, the two plane axes)
def material(name):
    """The texture a block shows: its stairs, slab and wall are the same material (variety counts materials, or one
    brick in three shapes would pass "3 blocks" - the greybox's wing roofs did)."""
    for suffix in SHAPES + ("_planks", "_block"):
        if name.endswith(suffix):
            name = name[:-len(suffix)]
    return name[:-1] if name.endswith("s") and not name.endswith("ss") else name


VIEW = {"west": (0, -1, (2, 1)), "east": (0, 1, (2, 1)), "north": (2, -1, (0, 1)), "south": (2, 1, (0, 1)),
        "up": (1, 1, (0, 2))}


def audit_region(cells, region):
    """cells: {(x, y, z): block name}. Returns the region's numbers."""
    x0, x1, y0, y1, z0, z1 = region["box"]
    axis, side, (ua, va) = VIEW[region["face"]]
    seen = {}                                      # (u, v) -> (depth, name): the nearest cell to the viewer
    for (x, y, z), n in cells.items():
        if n == "air" or not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
            continue
        q = (x, y, z)
        key, d = (q[ua], q[va]), q[axis]
        old = seen.get(key)
        if old is None or (d - old[0]) * side > 0:
            seen[key] = (d, n)
    total = len(seen)
    r = {"name": region["name"], "face": region["face"], "cells": total}
    if not total:
        return dict(r, planes=0, depths={}, blocks=0, top_block=None, top_share=0, shapes=0, speckle=0)
    pct = lambda k: round(100 * k / total)                                            # noqa: E731
    depths, names = {}, {}
    for d, n in seen.values():
        depths[d] = depths.get(d, 0) + 1
        names[material(n)] = names.get(material(n), 0) + 1
    top = max(sorted(names), key=names.get)
    lone = sum(1 for (u, v), (_, n) in seen.items()
               if not any(material(seen.get((u + a, v + b), (0, ""))[1]) == material(n)
                          for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))))
    return dict(r, planes=sum(1 for k in depths.values() if 100 * k >= PLANE_MIN * total),
                depths={d: pct(k) for d, k in sorted(depths.items())}, blocks=len(names), top_block=top,
                top_share=pct(names[top]), shapes=pct(sum(1 for _, n in seen.values() if n.endswith(SHAPES))),
                speckle=pct(lone))


def faults(r):
    out = []
    if r["planes"] < MIN_PLANES:
        out.append("planes %d < %d" % (r["planes"], MIN_PLANES))
    if r["blocks"] < MIN_BLOCKS:
        out.append("blocks %d < %d" % (r["blocks"], MIN_BLOCKS))
    if r["top_share"] > MAX_SHARE:
        out.append("%s %d %% > %d %%" % (r["top_block"], r["top_share"], MAX_SHARE))
    return out


def load_cells(path):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import preview_structure as PS
    root = PS.load_nbt(path)
    pal = root["palette"]
    return {tuple(b["pos"]): str(pal[b["state"]]["Name"]).split(":")[-1] for b in root["blocks"]}


def audit(path, regions):
    cells = load_cells(path)
    out = []
    for reg in regions:
        r = audit_region(cells, reg)
        out.append(dict(r, faults=faults(r)))
    return out


def format_report(rep):
    lines = []
    for r in rep:
        lines.append("%-22s %-5s cells %4d  planes %d  blocks %2d  top %-28s %3d %%  shapes %3d %%  speckle %3d %%  %s" % (
            r["name"], r["face"], r["cells"], r["planes"], r["blocks"], r["top_block"], r["top_share"], r["shapes"],
            r["speckle"], "OK" if not r["faults"] else "FAIL: " + "; ".join(r["faults"])))
    bad = sum(1 for r in rep if r["faults"])
    lines.append("%d of %d regions pass" % (len(rep) - bad, len(rep)))
    return "\n".join(lines)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    with open(sys.argv[2]) as f:
        print(format_report(audit(sys.argv[1], json.load(f))))
