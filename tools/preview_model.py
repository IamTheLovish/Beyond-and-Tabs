#!/usr/bin/env python3
"""A no-Blender preview of a concept model (design/models DSL): front, side, three-quarter and silhouette panels
drawn with PIL in a second or two. Flat shading, painter's order - enough to catch a floating hand, a plate inside
the body or a rotation with the wrong sign before the concepts workflow spends fifteen minutes on it.

    python3 tools/preview_model.py design/models/sunforged_paladin_templar.json --out /tmp/preview.png
"""
import argparse
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_models import rot_mat, apply, rotate_about  # noqa: E402

from PIL import Image, ImageDraw  # noqa: E402


def load(path):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import render_concept
    return render_concept.load_model(path)


def view_matrix(yaw, tilt):
    """The camera: turn the model by yaw about y, THEN pitch the camera down by tilt (tilt > 0 looks down on the
    model, as the RTS camera does). The viewer looks along +z."""
    T = rot_mat(-tilt, 0, 0)
    Y = rot_mat(0, yaw, 0)
    return tuple(tuple(sum(T[i][k] * Y[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def posed_cubes(m, upgraded=False):
    """Every cube as 8 world-space corners (posed) + colour; upgrade-tagged cubes only when upgraded."""
    bones = {b["name"]: b for b in m["bones"]}

    def fk(bone, p):
        b = bones[bone]
        while b is not None:
            rot = [b["rotation"][a] + m["pose"].get(b["name"], [0, 0, 0])[a] for a in range(3)]
            p = rotate_about(b["pivot"], rot, p)
            b = bones[b["parent"]] if b.get("parent") else None
        return p
    out = []
    for b in m["bones"]:
        for c in b.get("cubes", []):
            if c["upgrade"] and not upgraded:
                continue
            lo = [c["from"][a] - c["inflate"] for a in range(3)]
            hi = [c["from"][a] + c["size"][a] + c["inflate"] for a in range(3)]
            pts = [(x, y, z) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
            if any(c["rotation"]):
                pts = [rotate_about(c["pivot"], c["rotation"], p) for p in pts]
            pts = [fk(b["name"], p) for p in pts]
            out.append((pts, c["color"]))
    return out


# corner index: x bit 4, y bit 2, z bit 1 (lo=0, hi=1)
FACES = [  # (corner ids, outward normal in rest space)
    ((0, 1, 3, 2), (-1, 0, 0)), ((4, 6, 7, 5), (1, 0, 0)),
    ((0, 4, 5, 1), (0, -1, 0)), ((2, 3, 7, 6), (0, 1, 0)),
    ((0, 2, 6, 4), (0, 0, -1)), ((1, 5, 7, 3), (0, 0, 1)),
]


def hex_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def render(m, view, size=520, upgraded=False, silhouette=False):
    """view: a rotation (degrees) of the whole model about y, then a tilt about x; orthographic, y up."""
    yaw, tilt = view
    cubes = posed_cubes(m, upgraded)
    pal = {k: hex_rgb(v) for k, v in m["palette"].items()}
    emis = set(m["emissive"])
    R = view_matrix(yaw, tilt)
    light = (-0.45, 0.8, -0.55)   # from the front-left-top
    ln = math.sqrt(sum(v * v for v in light))
    light = tuple(v / ln for v in light)
    faces = []
    for pts, col in cubes:
        w = [apply(R, p) for p in pts]
        for ids, n in FACES:
            quad = [w[i] for i in ids]
            # the face normal in world space: from the rotated corners (works for rotated cubes too)
            a, b, c = quad[0], quad[1], quad[2]
            u = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
            v = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
            nx, ny, nz = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            nl = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
            nx, ny, nz = nx / nl, ny / nl, nz / nl
            # the viewer looks along +z (after the view rotation): cull faces pointing away
            if nz > 0:
                continue
            depth = sum(p[2] for p in quad) / 4
            shade = 0.55 + 0.45 * max(0, nx * light[0] + ny * light[1] + nz * light[2])
            faces.append((depth, quad, col, shade))
    faces.sort(key=lambda f: -f[0])   # far first
    xs = [p[0] for f in faces for p in f[1]]
    ys = [p[1] for f in faces for p in f[1]]
    if not xs:
        return Image.new("RGB", (size, size), (220, 220, 220))
    lo_x, hi_x, lo_y, hi_y = min(xs), max(xs), min(ys), max(ys)
    span = max(hi_x - lo_x, hi_y - lo_y) * 1.12
    scale = size / span
    ox = (size - (hi_x - lo_x) * scale) / 2 - lo_x * scale
    oy = size - ((size - (hi_y - lo_y) * scale) / 2) + lo_y * scale
    img = Image.new("RGB", (size, size), (255, 255, 255) if silhouette else (214, 212, 208))
    d = ImageDraw.Draw(img)
    if not silhouette:   # a ground line at y = 0
        gy = oy - 0 * scale
        d.line([(0, gy), (size, gy)], fill=(190, 186, 180), width=1)
    for depth, quad, col, shade in faces:
        poly = [(ox + p[0] * scale, oy - p[1] * scale) for p in quad]
        if silhouette:
            d.polygon(poly, fill=(0, 0, 0))
            continue
        r, g, b = pal[col]
        if col in emis:
            shade = 1.0
        d.polygon(poly, fill=(int(r * shade), int(g * shade), int(b * shade)))
    return img


def sheet(path, out, size=520):
    m = load(path)
    panels = [("front", (0, 0), False), ("side", (90, 0), False), ("quarter", (-40, 24), False),
              ("silhouette", (0, 0), True)]
    if m["upgrades"]:
        panels.append(("upgraded", (-40, 24), False))
    imgs = []
    for name, view, sil in panels:
        imgs.append((name, render(m, view, size, upgraded=(name == "upgraded"), silhouette=sil)))
    W = size * len(imgs) + 10 * (len(imgs) + 1)
    H = size + 60
    sh = Image.new("RGB", (W, H), (245, 243, 239))
    d = ImageDraw.Draw(sh)
    d.text((10, 8), f"{m['name']}   |   {m['role']}   |   {m['faction']}", fill=(30, 30, 30))
    for i, (name, im) in enumerate(imgs):
        x = 10 + i * (size + 10)
        sh.paste(im, (x, 30))
        d.text((x, 32 + size), name, fill=(80, 80, 80))
    sh.save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=520)
    a = ap.parse_args()
    print(sheet(a.model, a.out, a.size))


if __name__ == "__main__":
    main()
