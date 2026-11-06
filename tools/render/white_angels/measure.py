#!/usr/bin/env python3
"""White Angels lane: two numbers per body and kind, for the b2-vs-b4 call - the team-colour share of the body's
pixels (tools/game_preview.py's art mark, RTS and top-down views, rest pose, blue owner) and the halo's pixel count
in the FAR read (the body scaled to FAR_PX = 36 px tall, as game_preview's far cell): the halo's own pixels are the
silhouette with the halo minus the silhouette without it, scaled by (36 / body height px)^2.

    python3 tools/render/white_angels/measure.py --keys b2,b4 [--size 420] [--json out.json]
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import strips  # noqa: E402  (the model cache and the pose / face helpers)
import game_preview as gp  # noqa: E402
import dsl_to_game as d2g  # noqa: E402

OWNER = (0.23, 0.44, 0.82)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--keys", default="b2,b4")
    ap.add_argument("--size", type=int, default=420)
    ap.add_argument("--json")
    a = ap.parse_args()
    out = {}
    for key in a.keys.split(","):
        for kind in ("mender", "archer", "sword"):
            m, imgs, root = strips.model(key, kind)
            row = {}
            for vn in ("rts", "topdown"):
                V = gp.VIEWS[vn]
                fs = strips.faces(m, root, {"_hide": ["arrow"]})
                fh = strips.faces(m, root, {"_hide": ["halo", "arrow"]})
                bx = strips.bbox(fs, V)
                im, _, mask, team = gp.render(fs, imgs, d2g.DENSITY, V, a.size, OWNER, bx, with_mask=True)
                _, _, mask_h, _ = gp.render(fh, imgs, d2g.DENSITY, V, a.size, OWNER, bx, with_mask=True)
                body = int(mask.sum())
                ys = mask.any(axis=1).nonzero()[0]
                h = (ys[-1] - ys[0] + 1) if len(ys) else a.size
                halo = int((mask & ~mask_h).sum())
                k2 = (gp.FAR_PX / float(h)) ** 2
                row[vn] = {"team_share": round(float(team[mask].sum()) / max(1, body), 3),
                           "halo_px_far": round(halo * k2, 1), "body_px_far": round(body * k2, 1)}
            out["%s_%s" % (key, kind)] = row
            print("%-3s %-6s rts: team %4.1f%%  far halo %5.1f px (body %5.0f px) | topdown: team %4.1f%%  far halo %5.1f"
                  " px" % (key, kind, 100 * row["rts"]["team_share"], row["rts"]["halo_px_far"],
                           row["rts"]["body_px_far"], 100 * row["topdown"]["team_share"],
                           row["topdown"]["halo_px_far"]), flush=True)
    if a.json:
        json.dump(out, open(a.json, "w"), indent=1)


if __name__ == "__main__":
    main()
