#!/usr/bin/env python3
"""Several generated bodies side by side at TRUE in-game size, in the game's look (tools/game_preview.py's renderer:
the exported part tree + painted atlas, team colour, glow, Minecraft's two entity lights) - the offline twin of the
Blender lineups (design/lineups/*.json), for judging the height ladder and the silhouettes as the game draws them.

    python3 tools/game_lineup.py --out lineup.png --views front,quarter,rts \\
        design/models/sunforged_halberdier_v2.json@0.95 design/models/sunforged_squire_runner_v2.json@0.95:sword,...

Each model is PATH[@renderer scale][:bones,to,hide]. One shared scale per view, so heights compare honestly.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dsl_to_game as d2g  # noqa: E402
import game_preview as gp  # noqa: E402
import render_concept  # noqa: E402


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("models", nargs="+")
    ap.add_argument("--team", default="tabard,tabard_dark")
    ap.add_argument("--owner", default="#3a6fd1")
    ap.add_argument("--views", default="front,quarter")
    ap.add_argument("--size", type=int, default=260)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    owner = tuple(int(a.owner.lstrip("#")[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
    team = [t for t in a.team.split(",") if t]
    entries = []
    for spec in a.models:
        path, _, hide = spec.partition(":")
        path, _, scale = path.partition("@")
        m = render_concept.load_model(path)
        m["_path"] = os.path.abspath(path)
        _java, imgs, info = d2g.build(m, "Preview", team, "preview", d2g.DENSITY)
        pose_mc = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}
        fs = gp.faces_of(info["root"], pose_mc, True, False, set(h for h in hide.split(",") if h))
        k = float(scale or 1.0)
        fs = [([np.asarray(v, dtype=float) * k for v in verts], uv) for verts, uv in fs]
        entries.append((m["name"].replace(" (v2)", ""), fs, imgs))
    rows = []
    for view in [v for v in a.views.split(",") if v]:
        box = None
        for _, fs, imgs in entries:
            _, b = gp.render(fs, imgs, d2g.DENSITY, gp.VIEWS[view], 64, (1, 1, 1))
            box = b if box is None else (min(box[0], b[0]), max(box[1], b[1]), min(box[2], b[2]), max(box[3], b[3]))
        rows.append([(name, gp.render(fs, imgs, d2g.DENSITY, gp.VIEWS[view], a.size, owner, box)[0], view)
                     for name, fs, imgs in entries])
    cols = len(entries)
    W, H = cols * (a.size + 6) + 6, len(rows) * (a.size + 22) + 6
    sheet = Image.new("RGB", (W, H), (245, 243, 239))
    d = ImageDraw.Draw(sheet)
    for r, cells in enumerate(rows):
        for c, (name, im, view) in enumerate(cells):
            x, y = 6 + c * (a.size + 6), 6 + r * (a.size + 22)
            sheet.paste(im, (x, y))
            d.text((x + 4, y + a.size + 4), "%s - %s" % (name, view), fill=(40, 40, 40))
    sheet.save(a.out)
    print(a.out)


if __name__ == "__main__":
    main()
