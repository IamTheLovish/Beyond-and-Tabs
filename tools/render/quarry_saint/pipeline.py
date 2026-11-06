#!/usr/bin/env python3
"""The Quarry Saint's offline pipeline, one step per call, repo-relative (CI: .github/workflows/anim-render.yml on the
render branch; locally only when lovish allows local jobs). Every step appends "[seconds] message" to OUT/pipeline.log
and its seconds to OUT/timings.json.

    python tools/render/quarry_saint/pipeline.py --out OUT gen                       # design/models json (the body)
    python tools/render/quarry_saint/pipeline.py --out OUT keys --only rest,ready    # solve key poses -> OUT/keys_<tag>.json
    python tools/render/quarry_saint/pipeline.py --out OUT keyfile --parts DIR       # merge key parts -> design/fixtures keys
    python tools/render/quarry_saint/pipeline.py --out OUT bake --clip slam          # per-tick arms -> OUT/bake_slam.json
    python tools/render/quarry_saint/pipeline.py --out OUT mergebake --parts DIR     # bakes into the key file
    python tools/render/quarry_saint/pipeline.py --out OUT fixture export            # clip fixture, game model + atlas

The repo root is two folders above this file's folder (tools/render/quarry_saint -> repo), or --root.
"""
import argparse
import glob
import json
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
# what the solved keys depend on: their git blob ids (the same as `git ls-files -s` of the committed files)
KEY_INPUTS = ("tools/gen_quarry_saint.py", "tools/quarry_saint_poses.py", "tools/gen_models.py", "tools/contact_check.py")


def inputs_id(root):
    import hashlib
    ids = []
    for p in KEY_INPUTS:
        data = open(os.path.join(root, p), "rb").read().replace(b"\r\n", b"\n")
        ids.append(hashlib.sha1(b"blob %d\x00" % len(data) + data).hexdigest())
    return ",".join(ids)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("steps", nargs="+")
    ap.add_argument("--root", default=os.path.normpath(os.path.join(HERE, "..", "..", "..")))
    ap.add_argument("--out", required=True)
    ap.add_argument("--only", default="", help="keys: the key names to solve (default: all)")
    ap.add_argument("--tag", default="all", help="keys: the part's file tag")
    ap.add_argument("--clip", default="", help="bake: sweep | slam | gatefall")
    ap.add_argument("--parts", default="", help="keyfile / mergebake: the folder holding the parts")
    a = ap.parse_args()
    root = os.path.abspath(a.root)
    out = os.path.abspath(a.out)
    os.makedirs(out, exist_ok=True)
    sys.path.insert(0, os.path.join(root, "tools"))
    os.chdir(root)
    logf = open(os.path.join(out, "pipeline.log"), "a")
    tpath = os.path.join(out, "timings.json")
    timings = json.load(open(tpath)) if os.path.exists(tpath) else {}

    def log(msg, t0):
        dt = time.time() - t0
        line = "[%7.1fs] %s" % (dt, msg)
        print(line, flush=True)
        logf.write(line + "\n")
        logf.flush()
        return dt

    for step in a.steps:
        t0 = time.time()
        if step == "gen":
            import gen_quarry_saint as q
            m, pitch = q.build()
            with open(os.path.join(root, "design", "models", "sunforged_quarry_saint.json"), "w") as f:
                json.dump(m.to_json(), f, indent=1)
            msg = "gen: %d cubes, rest maul pitch %.1f" % (sum(len(b.cubes) for b in m.bones.values()), pitch)
        elif step == "keys":
            import quarry_saint_poses as P
            only = [k for k in a.only.split(",") if k] or None
            poses, report = P.solve_all(only)
            json.dump({"poses": poses, "report": report}, open(os.path.join(out, "keys_%s.json" % a.tag), "w"))
            msg = "keys %s: %d solved (%s)" % (a.tag, len(poses), ",".join(poses))
        elif step == "need-keys":
            # the key solve (35 min of runner time) only when its inputs changed since the committed key file
            kf = os.path.join(root, "design", "fixtures", "quarry_saint_keys.json")
            have = json.load(open(kf)).get("_inputs") if os.path.exists(kf) else None
            need = have != inputs_id(root)
            if os.environ.get("GITHUB_OUTPUT"):
                with open(os.environ["GITHUB_OUTPUT"], "a") as f:
                    f.write("solve=%s\n" % ("true" if need else "false"))
            msg = "need-keys: %s (committed %s, now %s)" % (need, have, inputs_id(root))
        elif step == "keyfile":
            import quarry_saint_poses as P
            import quarry_saint_anim as A
            poses, report = {}, {}
            for f in sorted(glob.glob(os.path.join(a.parts or out, "**", "keys_*.json"), recursive=True)):
                d = json.load(open(f))
                poses.update(d["poses"])
                report.update(d["report"])
            cache = os.path.join(HERE, "cache", "key_report.json")
            if not poses:
                # no solved parts (the inputs are unchanged): the committed key file and its cached report stand
                if os.path.exists(cache):
                    json.dump(json.load(open(cache)), open(os.path.join(out, "key_report.json"), "w"), indent=1)
                msg = "keyfile: no parts - the committed key file is used (inputs unchanged)"
            else:
                missing = [k for k in P.KEYS if k not in poses]
                if missing:
                    raise SystemExit("keyfile: missing keys %s" % missing)
                poses = {k: poses[k] for k in P.KEYS}
                report = {k: report[k] for k in P.KEYS}
                json.dump(poses, open(os.path.join(out, "poses.json"), "w"), indent=1)
                json.dump(report, open(os.path.join(out, "key_report.json"), "w"), indent=1)
                os.makedirs(os.path.dirname(cache), exist_ok=True)
                json.dump(report, open(cache, "w"), indent=1)
                A.build_keys(os.path.join(out, "poses.json"))
                keys = json.load(open(A.KEYS_FILE))
                keys = dict([("_inputs", inputs_id(root))] + list(keys.items()))
                json.dump(keys, open(A.KEYS_FILE, "w"), indent=1)
                msg = "keyfile: %d keys -> design/fixtures/quarry_saint_keys.json" % len(poses)
        elif step == "bake":
            import importlib
            import quarry_saint_anim as A
            A = importlib.reload(A)                 # the key file may have been rebuilt by an earlier step
            bake = A.bake_clip(a.clip)
            json.dump({a.clip: bake}, open(os.path.join(out, "bake_%s.json" % a.clip), "w"))
            msg = "bake %s: %d ticks" % (a.clip, len(bake))
            step = "bake_" + a.clip
        elif step == "mergebake":
            import quarry_saint_anim as A
            keys = json.load(open(A.KEYS_FILE))
            keys["_bake"] = {}
            for f in sorted(glob.glob(os.path.join(a.parts or out, "**", "bake_*.json"), recursive=True)):
                keys["_bake"].update(json.load(open(f)))
            json.dump(keys, open(A.KEYS_FILE, "w"), indent=1)
            msg = "mergebake: %s" % sorted(keys["_bake"])
        elif step == "fixture":
            r = subprocess.run([sys.executable, "tools/quarry_saint_anim.py", "--fixture",
                                "design/fixtures/quarry_saint_clips.json"], capture_output=True, text=True)
            if r.returncode:
                raise SystemExit(r.stderr)
            msg = "fixture: " + r.stdout.strip()
        elif step == "export":
            r = subprocess.run([sys.executable, "tools/dsl_to_game.py", "design/models/sunforged_quarry_saint.json",
                                "--class", "QuarrySaintModel", "--texture", "quarry_saint_unit", "--team",
                                "tabard,tabard_dark"], capture_output=True, text=True)
            if r.returncode:
                raise SystemExit(r.stderr)
            msg = "export: " + r.stdout.strip().splitlines()[0]
        else:
            raise SystemExit("unknown step %r" % step)
        timings[step] = round(log(msg, t0), 1)
        json.dump(timings, open(tpath, "w"), indent=1)


if __name__ == "__main__":
    main()
