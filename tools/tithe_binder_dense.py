#!/usr/bin/env python3
"""The Tithe Binder's DENSE fixture: every tick of the short events the every-5th-tick fixture
(design/fixtures/tithe_binder_clips.json) steps over, from the clip driver itself (tools/tithe_binder_anim.py run(),
the same harness: seed 1, 40 idle ticks of warm-up). TitheBinderRigTest replays these ticks too. Regenerate it with
the driver, never by hand:

    python tools/tithe_binder_dense.py --out design/fixtures/tithe_binder_dense.json

Windows (inclusive tick ranges per CLIPS entry):
  hit         t0-5    the t0 kicks, the t1-2 flinch peak + spoke kick + lantern gutter, the t1-3 halo dip
  bind        t14-18  the lash whip (t14-16), the orb's overshoot (t15), the flash (t16-17), the forge start (t18)
  volley_N10  t12-14  the snap at t13; t20-27: the first two impact flashes, every age 0-3
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tithe_binder_anim as A  # noqa: E402

WINDOWS = {"hit": [[0, 5]], "bind": [[14, 18]], "volley_N10": [[12, 14], [20, 27]]}


def dense():
    out = {}
    for name, wins in WINDOWS.items():
        keep = {t for a, b in wins for t in range(a, b + 1)}
        out[name] = {"n": A.CLIPS[name]["n"], "ticks": A.CLIPS[name]["ticks"], "windows": wins,
                     "poses": {str(t): p for t, p in A.run(name) if t in keep}}
    return {"generator": "tools/tithe_binder_dense.py (tools/tithe_binder_anim.py run())", "every": 1,
            "clips": out}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, help="write the dense fixture (JSON) here")
    a = ap.parse_args()
    with open(a.out, "w") as f:
        json.dump(dense(), f, separators=(",", ":"))
    print("wrote", a.out)


if __name__ == "__main__":
    main()
