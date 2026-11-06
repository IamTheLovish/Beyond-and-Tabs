#!/usr/bin/env python3
"""The Quarry Saint's clip driver (tools/quarry_saint_anim.py of a checkout) in the fixture form that
tools/blender/export_frames.py reads (--clips fixture:PATH --unit quarry_saint):
{"units": {"quarry_saint": {clip: {"ticks", "loop", "samples": {t: {bone: rot, "_move": {bone: off}}}}}}}.
Every tick is written; export_frames samples them with --step. In place: the walk's root travel is not drawn.

    python tools/render/quarry_saint/to_blender_fixture.py <checkout root> <out.json>
"""
import json
import os
import sys

repo, out = os.path.abspath(sys.argv[1]), sys.argv[2]
sys.path.insert(0, os.path.join(repo, "tools"))
import quarry_saint_anim as A  # noqa: E402

units = {}
for name, c in A.CLIPS.items():
    samples = {}
    for t, pose in A.run(name):
        d, mv = {}, {}
        for n, b in pose["bones"].items():
            if any(abs(v) > 1e-9 for v in b["rot"]):
                d[n] = b["rot"]
            if any(abs(v) > 1e-9 for v in b["off"]):
                mv[n] = b["off"]
        if mv:
            d["_move"] = mv
        samples[str(t)] = d
    units[name] = {"ticks": c["ticks"], "loop": name in ("idle", "walk"), "samples": samples}
os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
json.dump({"generator": "to_blender_fixture.py", "units": {"quarry_saint": units}}, open(out, "w"))
print("wrote", out, {k: len(v["samples"]) for k, v in units.items()})
