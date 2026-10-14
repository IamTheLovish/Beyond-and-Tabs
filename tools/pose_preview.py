#!/usr/bin/env python3
"""Animation keyframes on paper before they go into Java: a design/models DSL file drawn in several extra poses (bone
-> [x, y, z] DSL degrees ADDED to the model's own pose, exactly what a model subclass adds to the parts each frame),
side by side at ONE fixed scale and from the same views, so a wind-up, a strike and a recovery can be judged as a
sequence - does the blade clear the helm, do the hands stay on the grip, does the cape swing the right way.

    python3 tools/pose_preview.py design/models/sunforged_paladin_radiant_v2.json --out strip.png \\
        --poses '{"rest": {}, "wind-up": {"arms": [80, 0, 0]}, "strike": {"arms": [-25, 0, 0], "body": [-14, 0, 0]}}'

DSL angles: x > 0 swings a hanging part's foot FORWARD (-z) and tips a standing part BACK; in the game a model
part takes (-x, +y, -z) in radians (tools/dsl_to_game.py).
"""
import argparse
import copy
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept  # noqa: E402
from preview_model import posed_cubes, FACES, hex_rgb, view_matrix  # noqa: E402
from gen_models import apply  # noqa: E402

VIEWS = {"side": (90, 0), "quarter": (-40, 18), "front": (0, 0), "top": (-30, 55)}


def faces_for(m, view):
    R = view_matrix(view[0], view[1])
    light = (-0.45, 0.8, -0.55)
    ln = math.sqrt(sum(v * v for v in light))
    light = tuple(v / ln for v in light)
    out = []
    for pts, col in posed_cubes(m, False):
        w = [apply(R, p) for p in pts]
        for ids, _n in FACES:
            quad = [w[i] for i in ids]
            a, b, c = quad[0], quad[1], quad[2]
            u = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
            v = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
            nx, ny, nz = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            nl = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
            nx, ny, nz = nx / nl, ny / nl, nz / nl
            if nz > 0:
                continue
            depth = sum(p[2] for p in quad) / 4
            shade = 0.55 + 0.45 * max(0, nx * light[0] + ny * light[1] + nz * light[2])
            out.append((depth, quad, col, shade))
    out.sort(key=lambda f: -f[0])
    return out


def draw(m, faces, box, size):
    lo_x, hi_x, lo_y, hi_y = box
    span = max(hi_x - lo_x, hi_y - lo_y) * 1.08
    scale = size / span
    ox = (size - (hi_x - lo_x) * scale) / 2 - lo_x * scale
    oy = size - ((size - (hi_y - lo_y) * scale) / 2) + lo_y * scale
    img = Image.new("RGB", (size, size), (214, 212, 208))
    d = ImageDraw.Draw(img)
    d.line([(0, oy), (size, oy)], fill=(190, 186, 180), width=1)
    pal = {k: hex_rgb(v) for k, v in m["palette"].items()}
    emis = set(m["emissive"])
    for _depth, quad, col, shade in faces:
        r, g, b = pal[col]
        if col in emis:
            shade = 1.0
        d.polygon([(ox + p[0] * scale, oy - p[1] * scale) for p in quad], fill=(int(r * shade), int(g * shade), int(b * shade)))
    return img


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model")
    ap.add_argument("--poses", required=True, help="JSON: {name: {bone: [x, y, z]}} or @file.json")
    ap.add_argument("--views", default="side,quarter")
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=360)
    a = ap.parse_args()
    base = render_concept.load_model(a.model)
    poses = json.load(open(a.poses[1:])) if a.poses.startswith("@") else json.loads(a.poses)
    views = [v for v in a.views.split(",") if v]
    rows = []
    for vname in views:
        per = []
        for pname, extra in poses.items():
            m = copy.deepcopy(base)
            for bone, r in extra.items():
                if bone not in {b["name"] for b in m["bones"]}:
                    raise SystemExit("pose %s: no bone %r" % (pname, bone))
                cur = m["pose"].get(bone, [0, 0, 0])
                m["pose"][bone] = [cur[i] + r[i] for i in range(3)]
            per.append((pname, m, faces_for(m, VIEWS[vname])))
        xs = [p[0] for _, _, fs in per for f in fs for p in f[1]]
        ys = [p[1] for _, _, fs in per for f in fs for p in f[1]]
        box = (min(xs), max(xs), min(ys), max(ys))
        rows.append((vname, [(pn, draw(m, fs, box, a.size)) for pn, m, fs in per]))
    cols = len(poses)
    W, H = cols * (a.size + 8) + 8, len(rows) * (a.size + 22) + 8
    sheet = Image.new("RGB", (W, H), (245, 243, 239))
    d = ImageDraw.Draw(sheet)
    for r, (vname, imgs) in enumerate(rows):
        for c, (pn, im) in enumerate(imgs):
            x, y = 8 + c * (a.size + 8), 8 + r * (a.size + 22)
            sheet.paste(im, (x, y))
            d.text((x + 4, y + a.size + 4), "%s - %s" % (pn, vname), fill=(40, 40, 40))
    sheet.save(a.out)
    print(a.out)


if __name__ == "__main__":
    main()
