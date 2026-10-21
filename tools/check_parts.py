#!/usr/bin/env python3
"""
Extractor-rig anchor audit (design/moving_buildings.md slice 1). The drawn moving parts of the Gravebound metal
extractor (PartRenderClientEvents) sit on anchors declared twice: in tools/gen_structures.py (GRAVE_RIG_ANCHORS - what
the structures were built round) and in bar/.../building/BuildingParts.java (RIGS - what the
renderer reads). This check fails when:

  * a row of RIGS no longer equals its gen_structures.py anchors (hub, axis, clear radius, wheel radius, axle, skip
    shaft, drum, pennant pole and how the pennant flies), or a tier has no row;
  * a structure named by a row is missing, or its committed .nbt buries a part: the wheel's disc, the skip's shaft and
    the chain's run to the hub, a drum's rope column, the pennant's 5x5 layer round its pole must be air; the axle, drum and
    pole must stand; the wheel drawn (wheel radius + BuildingParts.RIM_OUTSET) must fit its disc.

The GameTest every_extractor_rig_part_has_room_to_move checks the same room in the game's own structure loader.

It also checks the drawn STATUES (BuildingParts.STATUES, StatueRenderClientEvents): the White Angel capitol's row
must stand on tools/capitol_white_angel_v5.py's STATUE_ANCHOR, that cell must be air on the plinth at every level
of the generated structure (the capitol is not in the game yet, so no GameTest can load it), and the row's model
height must be the generated model's (design/models/white_angel_statue.json, soles to wing tips) - else the
statue would be drawn at the wrong size. No Minecraft needed: it runs in CI's safety-protocol step.
Run: python3 tools/check_parts.py
"""
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_structures as gs            # noqa: E402  (import-safe: its writer runs under __main__ only)
from check_sail_hubs import structure  # noqa: E402

PARTS = os.path.join(HERE, "..", "bar", "src", "main", "java", "com", "solegendary", "reignofnether", "building",
                     "BuildingParts.java")

CELL =re.compile(r"cell\((-?\d+),\s*(-?\d+),\s*(-?\d+)\)")


def parse_rigs(src):
    """The RIGS rows of BuildingParts.java as dicts."""
    body = src[src.index("RIGS = List.of("):]
    body = body[:body.index(");") + 2]
    rows = []
    for m in re.finditer(r"new Rig\(", body):
        depth, i = 1, m.end()
        while depth:
            depth += {"(": 1, ")": -1}.get(body[i], 0)
            i += 1
        args = CELL.sub(lambda c: "cell:%s:%s:%s" % c.groups(), body[m.end():i - 1])
        a = [x.strip() for x in args.split(",")]

        def cell(s):
            if s == "null":
                return None
            _, x, y, z = s.split(":")
            return (int(x), int(y), int(z))
        rows.append(dict(structure=a[0].strip('"'), skin=a[1].split(".")[-1],
                         hub=tuple(float(v.rstrip("f")) for v in a[2:5]), axis=a[5].split(".")[-1].lower(),
                         clear=float(a[6].rstrip("f")), wheel=float(a[7].rstrip("f")), axle=cell(a[8]),
                         skip_bottom=cell(a[9]), skip_top=cell(a[10]), drum=cell(a[11]), pole=cell(a[12]),
                         flag_above=a[13] == "true", tip=a[14].split(".")[-1], stroke=int(a[15])))
    return rows


def expected():
    """What gen_structures.py declares, in BuildingParts' terms (hub cells become cell centres)."""
    out = {}
    # (the Sunforged extractor is the Angel Fountain since Oct 19: no drawn machinery, no row)
    for tier, name in (("T1", "metal_extractor_dark"), ("T2", "metal_extractor_t2_dark")):
        a = gs.GRAVE_RIG_ANCHORS[tier]
        cx, cy, cz = a["sheave_hub"]
        out[name] = dict(hub=(cx + 0.5, cy + 0.5, cz + 0.5), axis=a["sheave_axis"], clear=a["sheave_radius"],
                         axle=tuple(a["sheave_axle"]), skip_bottom=tuple(a["skip_bottom"]),
                         skip_top=tuple(a["skip_top"]), drum=None, pole=tuple(a["pennant_pole_top"]), flag_above=True,
                         outward=a["sheave_outward"])
    return out


def room(row, rim, bad):
    """The parts' room in the committed structure (the GameTest's rules)."""
    name = row["structure"]
    got = structure(name)
    if got is None:
        bad.append(f"{name}: no structure file")
        return
    _, blocks, solid = got
    hx, hy, hz = row["hub"]
    along_x = row["axis"] == "x"
    hu, hv, rr = (hz if along_x else hx), hy, row["clear"]
    layer = math.floor(hx if along_x else hz)
    clear = []
    for u in range(math.floor(hu - rr) - 1, math.floor(hu + rr) + 2):
        for v in range(math.floor(hv - rr) - 1, math.floor(hv + rr) + 2):
            du = max(u - hu, 0, hu - (u + 1))
            dv = max(v - hv, 0, hv - (v + 1))
            if du * du + dv * dv < rr * rr:
                clear.append((layer, v, u) if along_x else (u, v, layer))
    if row["wheel"] + rim > rr:
        bad.append(f"{name}: the wheel drawn (radius {row['wheel']} + rim {rim}) is wider than its clear disc ({rr})")
    hub_cell = (math.floor(hx), math.floor(hy), math.floor(hz))
    bx, by, bz = row["skip_bottom"]
    clear += [(bx, y, bz) for y in range(by, hub_cell[1] + 1)]
    if row["drum"]:
        dx, dy, dz = row["drum"]
        clear += [(dx, y, dz) for y in range(dy + 1, hub_cell[1] + 1)]
        if row["drum"] not in solid:
            bad.append(f"{name}: no winding drum at {row['drum']}")
    px, py, pz = row["pole"]
    fy = py + 1 if row["flag_above"] else py
    span = (-2, -1, 0, 1, 2)   # the pennant streams with the world's wind, any way round, up to 1.7 blocks out
    clear += [(px + dx, fy, pz + dz) for dx in span for dz in span if row["flag_above"] or dx or dz]
    for c in dict.fromkeys(clear):
        if c in solid:
            bad.append(f"{name}: {c} must stay air for the moving parts, holds {blocks[c]['Name']}")
    for what in ("axle", "pole"):
        if row[what] not in solid:
            bad.append(f"{name}: no {what} block at {row[what]}")


STATUE_ROW = re.compile(r'new Statue\("([^"]+)",\s*cell\((-?\d+),\s*(-?\d+),\s*(-?\d+)\),\s*([\d.]+)f,\s*([\d.]+)f,'
                        r'\s*([\d.]+)f\)')
STATUE_SOURCES = {
    # structure base name -> (the generator module that declares STATUE_ANCHOR, the statue model's DSL file)
    "capitol_white_angel": ("capitol_white_angel_v5", "white_angel_statue.json"),
}


def model_corners(path):
    """Every cube corner of a DSL model in its posed rest (bones, cube rotations, the "pose" tilts) - plain Python
    (gen_models.rotate_about), so CI's bare python3 runs it without PIL."""
    import json
    from gen_models import rotate_about
    m = json.load(open(path))
    bones = {b["name"]: b for b in m["bones"]}
    pose = m.get("pose", {})

    def fk(name, p):
        b = bones[name]
        while b is not None:
            rot = [b.get("rotation", [0, 0, 0])[a] + pose.get(b["name"], [0, 0, 0])[a] for a in range(3)]
            p = rotate_about(b["pivot"], rot, p)
            b = bones[b["parent"]] if b.get("parent") else None
        return p
    out = []
    for b in m["bones"]:
        for c in b["cubes"]:
            g = c.get("inflate", 0.0)
            lo = [c["from"][a] - g for a in range(3)]
            hi = [c["from"][a] + c["size"][a] + g for a in range(3)]
            for x in (lo[0], hi[0]):
                for y in (lo[1], hi[1]):
                    for z in (lo[2], hi[2]):
                        q = (x, y, z)
                        if c.get("rotation"):
                            piv = c.get("pivot") or [c["from"][a] + c["size"][a] / 2 for a in range(3)]
                            q = rotate_about(piv, c["rotation"], q)
                        out.append(fk(b["name"], q))
    return out


def check_statues(src, bad):
    """The STATUES rows against their structure generators and their models."""
    import importlib
    body = src[src.index("STATUES = List.of("):]
    body = body[:body.index(");") + 2]
    rows = STATUE_ROW.findall(body)
    if not rows:
        bad.append("BuildingParts.STATUES: no rows parsed")
    for (name, x, y, z, height, model_px, half_span) in rows:
        anchor = (int(x), int(y), int(z))
        if name not in STATUE_SOURCES:
            bad.append(f"{name}: a statue row with no generator in check_parts.STATUE_SOURCES")
            continue
        mod_name, model_file = STATUE_SOURCES[name]
        mod = importlib.import_module(mod_name)
        if tuple(mod.STATUE_ANCHOR) != anchor:
            bad.append(f"{name}: BuildingParts says the statue stands at {anchor}, {mod_name}.STATUE_ANCHOR "
                       f"{tuple(mod.STATUE_ANCHOR)}")
        for n in range(1, mod.LEVELS + 1):
            cells = mod.level(n).b
            ax, ay, az = mod.STATUE_ANCHOR
            if cells.get((ax, ay, az), ("air",))[0] not in ("air", "cave_air"):
                bad.append(f"{name} L{n}: the statue's anchor {mod.STATUE_ANCHOR} holds {cells[(ax, ay, az)][0]}")
            if cells.get((ax, ay - 1, az), ("air",))[0] in ("air", "cave_air"):
                bad.append(f"{name} L{n}: nothing under the statue's anchor {mod.STATUE_ANCHOR} to stand on")
        pts = model_corners(os.path.join(HERE, "..", "design", "models", model_file))
        ys = [p[1] for p in pts]
        span = max(math.hypot(p[0], p[2]) for p in pts)      # the reach in any direction: the statue may be yawed
        if abs((max(ys) - min(ys)) - float(model_px)) > 0.3:
            bad.append(f"{name}: BuildingParts says the model is {model_px} px tall, {model_file} is "
                       f"{max(ys) - min(ys):.1f}")
        k = float(height) / (float(model_px) / 16.0)
        if span * k / 16.0 > float(half_span) + 0.05:
            bad.append(f"{name}: the statue reaches {span * k / 16.0:.2f} blocks out, its culling half span is {half_span}")
        reach = getattr(mod, "STATUE_REACH", None)        # the generator keeps this much clear round the anchor
        if reach is not None and span * k / 16.0 > reach + 0.5:
            bad.append(f"{name}: the statue reaches {span * k / 16.0:.2f} blocks out, the structure keeps only {reach} clear")
    return len(rows)


def main():
    src = open(PARTS).read()
    rows = parse_rigs(src)
    rim = float(re.search(r"RIM_OUTSET\s*=\s*([\d.]+)f", src).group(1))
    want = expected()
    bad = []
    by_name = {r["structure"]: r for r in rows}
    for name, w in want.items():
        r = by_name.get(name)
        if r is None:
            bad.append(f"{name}: anchors in gen_structures.py but no row in BuildingParts.RIGS")
            continue
        for k in ("hub", "axis", "clear", "axle", "skip_bottom", "skip_top", "drum", "pole", "flag_above"):
            if r[k] != w[k]:
                bad.append(f"{name}: BuildingParts says {k} = {r[k]}, gen_structures.py {w[k]}")
        if "wheel" in w and r["wheel"] != w["wheel"]:
            bad.append(f"{name}: BuildingParts says wheel radius {r['wheel']}, gen_structures.py {w['wheel']}")
        if "outward" in w:   # the wheel turns on the axle block's outward face: the axle is on the other side
            step = {"west": (-1, 0), "east": (1, 0), "north": (0, -1), "south": (0, 1)}[w["outward"]]
            hub_cell = tuple(math.floor(v) for v in r["hub"])
            if r["axle"] != (hub_cell[0] - step[0], hub_cell[1], hub_cell[2] - step[1]):
                bad.append(f"{name}: the axle {r['axle']} is not behind the hub's {w['outward']} face")
    for r in rows:
        if r["structure"] not in want:
            bad.append(f"{r['structure']}: a row in BuildingParts.RIGS with no anchors in gen_structures.py")
        room(r, rim, bad)
    statues = check_statues(src, bad)
    for b in bad:
        print("FAIL", b)
    print(f"check_parts: {len(rows)} rigs, {statues} statues, {len(bad)} problems")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
