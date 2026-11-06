#!/usr/bin/env python3
"""White Angels lane: offline stills on a GitHub runner (wa-render.yml). Reads jobs.json beside this file:
[{"name": str, "model": path, "team": "a,b", "owner": "#rrggbb", "views": "front,quarter,side,rts",
  "poses": {name: {bone: [x,y,z]}} or "@file", "args": [extra game_preview args]}]
and runs tools/game_preview.py for each (red and blue owners unless "owner" is given). Optional "pre": a list of
commands (argv lists) run first from the repo root (generators). Every step's exit code is logged; one failing job
fails the run at the end."""
import argparse
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))


def run(argv):
    print("$", " ".join(argv), flush=True)
    return subprocess.run(argv, cwd=ROOT).returncode


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    out = os.path.abspath(a.out)
    os.makedirs(out, exist_ok=True)
    spec = json.load(open(os.path.join(HERE, "jobs.json")))
    bad = 0
    for cmd in spec.get("pre", []):
        bad += run([sys.executable if c == "python" else c for c in cmd]) != 0
    for j in spec.get("jobs", []):
        owners = [j["owner"]] if j.get("owner") else ["#d13a3a", "#3a6fd1"]
        for o in owners:
            tag = "red" if o == "#d13a3a" else ("blue" if o == "#3a6fd1" else o.strip("#"))
            argv = [sys.executable, "tools/game_preview.py", j["model"], "--owner", o,
                    "--views", j.get("views", "front,quarter,side,rts"),
                    "--out", os.path.join(out, f"{j['name']}_{tag}.png")]
            if j.get("team"):
                argv += ["--team", j["team"]]
            if j.get("poses"):
                p = j["poses"]
                argv += ["--poses", p if isinstance(p, str) else json.dumps(p)]
            argv += j.get("args", [])
            bad += run(argv) != 0
    for cmd in spec.get("post", []):
        bad += run([sys.executable if c == "python" else c for c in cmd]) != 0
    print(f"done, {bad} failing step(s)")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
