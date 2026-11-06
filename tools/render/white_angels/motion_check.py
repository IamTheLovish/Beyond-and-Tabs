#!/usr/bin/env python3
"""White Angels lane, the motion self-check (no grading): every pose tools/angel_anim.py writes for every clip of every
kind, on a body (b2 / b4), through tools/contact_check.py - what passes through what. A pair of parts that already
touches in the rest pose (the halo behind the hair, the humerus in the pauldron, a wing's layered feathers) counts
only the depth it adds over its rest depth. Prints per clip the worst NEW penetration (the worst clearance) between
two different parts, and separately inside one wing (feathers sliding over feathers as the wrist folds), then the
attachment checks (no wing or prop bone is ever moved off its joint: the driver rotates them only) and the lag
checks (every secondary channel peaks AFTER its driver: tips after the hand, cloth after the body, the hem after the
robe, the halo after the head - the lag in ticks at the best cross-correlation).

    python3 tools/render/white_angels/motion_check.py --key b2 [--every 2] [--json out.json]
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import angel_anim as A  # noqa: E402
import contact_check as C  # noqa: E402
import render_concept  # noqa: E402

FIXED = ("wing_", "chain", "lantern", "bow", "sword", "shield", "quiver", "left_", "right_", "head")


def lag(x, y, maxlag=8):
    """The lag (ticks) of y behind x at the best correlation of their tick-to-tick changes."""
    dx = [b - a for a, b in zip(x, x[1:])]
    dy = [b - a for a, b in zip(y, y[1:])]
    if not any(abs(v) > 1e-6 for v in dx) or not any(abs(v) > 1e-6 for v in dy):
        return None
    best, bl = -1e9, 0
    for L in range(-maxlag, maxlag + 1):
        s = 0.0
        for i in range(len(dx)):
            j = i + L
            if 0 <= j < len(dy):
                s += dx[i] * dy[j]
        if s > best:
            best, bl = s, L
    return bl


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", default="b2")
    ap.add_argument("--every", type=int, default=2)
    ap.add_argument("--report", type=float, default=0.3, help="px over the rest depth that counts")
    ap.add_argument("--json")
    a = ap.parse_args()
    out = {}
    worst_all = (0.0, "")
    worst_vis = (0.0, "")        # without the two humerus blocks meeting inside the back (the spine, hidden)
    for kind in A.KINDS:
        m = render_concept.load_model(os.path.join(ROOT, "design", "models", "sunforged_angel_%s_%s.json" % (a.key, kind)))
        rest = {}
        for h in C.check(m, {}, (), {}, threshold=0.0):
            pr = frozenset((h[1], h[4]))
            rest[pr] = max(rest.get(pr, 0.0), h[0])
        for clip in A.CLIPS:
            fr = A.run(kind, clip)
            cross, inwing = {}, {}
            moved = set()
            for t, pz in fr:
                for n in pz.get("_move", {}):
                    if n.startswith(FIXED):
                        moved.add(n)
                if t % a.every and t != len(fr) - 1:
                    continue
                extra = {k: v for k, v in pz.items() if not k.startswith("_")}
                for h in C.check(m, extra, pz.get("_hide", ()), pz.get("_move", {}), threshold=a.report):
                    pr = frozenset((h[1], h[4]))
                    d = h[0] - rest.get(pr, 0.0)
                    if d <= a.report:
                        continue
                    x, y = sorted(pr)
                    same = x.startswith("wing_") and x[:7] == y[:7]
                    tgt = inwing if same else cross
                    key = "%s<->%s" % (x, y)
                    if d > tgt.get(key, (0.0,))[0]:
                        tgt[key] = (round(float(d), 2), t)
            # lag checks on the full tick series
            ser = {n: [] for n in ("hand", "tips", "body", "cloth", "robe", "hem", "head", "halo")}
            for t, pz in fr:
                ser["hand"].append(pz["wing_l_arm"][2] + pz["wing_l_fore"][2] + pz["wing_l_hand"][2])
                ser["tips"].append(ser["hand"][-1] + pz["wing_l_tips"][2])
                ser["body"].append(pz["body"][0])
                cl = pz.get("tabard_left", pz.get("robe_left", [0, 0, 0]))
                ser["cloth"].append(pz["body"][0] + cl[0])
                ser["robe"].append(pz.get("robe_front", [0, 0, 0])[0])
                ser["hem"].append(pz.get("hem_front", [0, 0, 0])[0])
                ser["head"].append(pz["head"][1])
                ser["halo"].append(pz["halo"][1])
            lags = {"tips/hand": lag(ser["hand"], ser["tips"]), "cloth/body": lag(ser["body"], ser["cloth"]),
                    "halo/head": lag(ser["head"], ser["halo"])}
            if kind == "mender":
                lags["hem/robe"] = lag(ser["robe"], ser["hem"])
            cw = sorted(cross.items(), key=lambda kv: -kv[1][0])
            iw = sorted(inwing.items(), key=lambda kv: -kv[1][0])
            print("%-6s %-7s cross %-44s in-wing %-34s moved %-5s lags %s" % (
                kind, clip, ("%s %.2f@%d" % (cw[0][0], cw[0][1][0], cw[0][1][1])) if cw else "clean",
                ("%s %.2f@%d" % (iw[0][0], iw[0][1][0], iw[0][1][1])) if iw else "clean",
                ",".join(sorted(moved)) or "none", " ".join("%s %s" % (k2, "n/a" if v is None else "%+d" % v) for k2, v in lags.items())))
            for k2, (d, t) in cw[1:4]:
                print("%17s %s %.2f@%d" % ("", k2, d, t))
            if cw and cw[0][1][0] > worst_all[0]:
                worst_all = (cw[0][1][0], "%s %s %s @%d" % (kind, clip, cw[0][0], cw[0][1][1]))
            vis = [kv for kv in cw if kv[0] != "wing_l_arm<->wing_r_arm"]
            if vis and vis[0][1][0] > worst_vis[0]:
                worst_vis = (vis[0][1][0], "%s %s %s @%d" % (kind, clip, vis[0][0], vis[0][1][1]))
            out["%s/%s" % (kind, clip)] = {"cross": cw, "inwing": iw[:3], "moved": sorted(moved), "lags": lags}
    print("worst new penetration (part through part) on %s: %.2f px  %s" % (a.key, worst_all[0], worst_all[1]))
    print("worst without the spine (wing_l_arm<->wing_r_arm) on %s: %.2f px  %s" % (a.key, worst_vis[0], worst_vis[1]))
    if a.json:
        json.dump(out, open(a.json, "w"), indent=1)


if __name__ == "__main__":
    main()
