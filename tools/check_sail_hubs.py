#!/usr/bin/env python3
"""
Sail-hub audit (lovish's clip: "windmill propellers are still f***ed" - the Stormcallers' sails floated beside and
above their towers). Every wind generator declares where its sails turn in WindGenerator.SAIL_HUBS; the renderer
rotates that hub with the placement. This check reads the table and each mill's structure and fails when:

  * a WindGenerator("<variant>") registered in Buildings.java has no SAIL_HUBS entry (or an entry has no mill);
  * the hub lies outside the structure's footprint, or the block there is not a horizontal hub log on the axis the
    rotor faces (the renderer would spin the sails round thin air);
  * the face the rotor sits on is not on the outside of the footprint (the sails would cut through the tower);
  * the blades are long enough to dig into the ground below the hub once tilted back (40 degrees).

No Minecraft needed: it runs in CI's safety-protocol step. Run: python3 tools/check_sail_hubs.py
"""
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from preview_structure import load_nbt  # noqa: E402

MAIN = os.path.join(HERE, "..", "bar", "src", "main")
JAVA = os.path.join(MAIN, "java", "com", "solegendary", "reignofnether")
MILL = os.path.join(JAVA, "building", "buildings", "shared", "WindGenerator.java")
BUILDINGS = os.path.join(JAVA, "building", "Buildings.java")
STRUCTURES = os.path.join(MAIN, "resources", "data", "reignofnether", "structures")

HUB_BLOCKS = {"stripped_spruce_log", "stripped_dark_oak_log", "stripped_crimson_stem", "bone_block",
              "stripped_oak_log", "stripped_mangrove_log", "stripped_cherry_log"}
STEP = {"NORTH": (0, -1), "SOUTH": (0, 1), "WEST": (-1, 0), "EAST": (1, 0)}
AXIS = {"NORTH": "z", "SOUTH": "z", "WEST": "x", "EAST": "x"}
ROTOR_TILT = math.radians(40)
BLADE_ROOT = 0.35          # WindmillRenderClientEvents: blades start 0.35 from the hub centre

HUB_RE = re.compile(r'"([a-z_]*)"\s*,\s*new SailHub\(new BlockPos\((\d+),\s*(\d+),\s*(\d+)\),\s*Direction\.(\w+),\s*([\d.]+)f\)')
MILL_RE = re.compile(r'new com\.solegendary\.reignofnether\.building\.buildings\.shared\.WindGenerator\(("([a-z_]*)")?\)')


def main():
    src = open(MILL, encoding="utf-8").read()
    hubs = {m[0]: (int(m[1]), int(m[2]), int(m[3]), m[4], float(m[5])) for m in HUB_RE.findall(src)}
    mills = {m[1] for m in MILL_RE.findall(open(BUILDINGS, encoding="utf-8").read())}
    bad = []
    if not hubs:
        bad.append("could not read WindGenerator.SAIL_HUBS")
    for v in sorted(mills - hubs.keys()):
        bad.append(f"wind_generator{v}: registered in Buildings.java but declares no SAIL_HUBS entry")
    for v in sorted(hubs.keys() - mills):
        bad.append(f"SAIL_HUBS has '{v}' but no such WindGenerator is registered")
    for v, (x, y, z, facing, length) in sorted(hubs.items()):
        name = f"wind_generator{v}"
        path = os.path.join(STRUCTURES, name + ".nbt")
        if not os.path.exists(path):
            bad.append(f"{name}: no structure file")
            continue
        nbt = load_nbt(path)
        sx, sy, sz = nbt["size"]
        blocks = {tuple(b["pos"]): nbt["palette"][b["state"]] for b in nbt["blocks"]}
        solid = {p for p, st in blocks.items() if st["Name"].split(":")[1] not in ("air", "structure_void")}
        xs = [p[0] for p in solid]
        zs = [p[2] for p in solid]
        if not (min(xs) <= x <= max(xs) and 0 <= y < sy and min(zs) <= z <= max(zs)):
            bad.append(f"{name}: hub ({x},{y},{z}) is outside the footprint x {min(xs)}..{max(xs)} z {min(zs)}..{max(zs)}")
            continue
        st = blocks.get((x, y, z))
        block = st["Name"].split(":")[1] if st else "air"
        axis = (st or {}).get("Properties", {}).get("axis")
        if block not in HUB_BLOCKS or axis != AXIS.get(facing):
            bad.append(f"{name}: hub ({x},{y},{z}) is {block} axis={axis}, not a hub log on the {AXIS.get(facing)} axis")
        if facing not in STEP:
            bad.append(f"{name}: rotor must face a horizontal direction, not {facing}")
            continue
        dx, dz = STEP[facing]
        if (x + dx, y, z + dz) in solid:
            bad.append(f"{name}: the block {facing} of the hub is part of the mill - the sails would cut through it")
        # tilted back, a blade pointing straight down reaches (root + length) * cos(tilt) below the hub centre
        drop = (BLADE_ROOT + length) * math.cos(ROTOR_TILT)
        if drop > y + 0.5:
            bad.append(f"{name}: {length}-block blades reach {drop:.2f} below a hub {y + 0.5} above the base - into the ground")
        if length > 3.2 or length < 1.5:
            bad.append(f"{name}: blade length {length} is outside 1.5..3.2 for a {sx}x{sz} mill")
    for b in bad:
        print(b)
    print(f"{len(hubs)} sail hubs checked, {len(bad)} problems")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
