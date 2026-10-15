#!/usr/bin/env python3
"""The game's look, offline: draws what tools/dsl_to_game.py exports - the part tree, the painted 4x atlas through
Minecraft's box-UV mapping, the team mask in an owner's colour, the glow pass, Minecraft's two-light entity shading,
the "lod" detail on or off - so a unit is seen as the game will draw it before a 25-minute build and an eyes run.
It also catches what only a textured render shows: a face sampling outside its island (a hole: the cutout drops
transparent texels), a gradient running the wrong way, a team colour on the wrong cloth.

    python3 tools/game_preview.py design/models/sunforged_paladin_radiant_v2.json --team tabard,tabard_dark \\
        --owner "#d13a3a" --out paladin_game.png [--no-detail] [--upgrade] [--poses '{"strike": {"arms": [-22, 0, 0]}}']
        [--art-check]   (adds value / silhouette / far-read cells per view - claude/art-theory.md)

Faces follow ModelPart.Cube (1.20.1): top at (u+d, v), bottom at (u+d+w, v), then from (u, v+d) the -x, front (-z),
+x and back (+z) sides; each Polygon puts its 4 vertices on (u1,v0) (u0,v0) (u0,v1) (u1,v1).
"""
import argparse
import copy
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept  # noqa: E402
import dsl_to_game as d2g  # noqa: E402
from preview_model import view_matrix  # noqa: E402

VIEWS = {"front": (0, 0), "quarter": (-40, 18), "side": (90, 0), "back": (180, 0), "rts": (-35, 52)}
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])     # Minecraft's entity lights (y up)
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


def faces_of(root, pose_mc, detail, upgrade, hide=()):
    """[(4 model-space vertices in Polygon order, 4 (u, v) in model pixels)] for every visible box face. Bones named in
    hide are not drawn (with their children) - what a model subclass does with part.visible."""
    out = []
    I = np.eye(3)

    def walk(p, M, t, hidden):
        if p.name == "lod" and not detail:
            hidden = True
        if p.name.startswith("upg_") and not upgrade:
            hidden = True
        if p.name in hide:
            hidden = True
        rot = list(p.rot)
        if p.name in pose_mc:
            rot = [rot[a] + pose_mc[p.name][a] for a in range(3)]
        t2 = t + M @ np.array(p.offset, dtype=float)
        M2 = M @ np.array(d2g.mat_zyx(rot))
        if not hidden:
            for (x, y, z, w, h, dd, g, (u, v)) in p.boxes:
                x0, y0, z0, x1, y1, z1 = x - g, y - g, z - g, x + w + g, y + h + g, z + dd + g

                def V(a, b, c):
                    return t2 + M2 @ np.array([a, b, c])
                v1, v2, v3, v4 = V(x0, y0, z0), V(x1, y0, z0), V(x1, y1, z0), V(x0, y1, z0)
                v5, v6, v7, v8 = V(x0, y0, z1), V(x1, y0, z1), V(x1, y1, z1), V(x0, y1, z1)
                u0, u1, u2, u3 = u, u + dd, u + dd + w, u + dd + w + w
                u4, u5 = u + dd + w + dd, u + dd + w + dd + w
                w0, w1, w2 = v, v + dd, v + dd + h
                for verts, (a0, b0, a1, b1) in (
                        ((v6, v5, v1, v2), (u1, w0, u2, w1)),     # DOWN in model space = the visual top
                        ((v3, v4, v8, v7), (u2, w1, u3, w0)),     # UP = the visual bottom
                        ((v1, v5, v8, v4), (u0, w1, u1, w2)),     # WEST (-x)
                        ((v2, v1, v4, v3), (u1, w1, u2, w2)),     # NORTH (-z, the front)
                        ((v6, v2, v3, v7), (u2, w1, u4, w2)),     # EAST (+x)
                        ((v5, v6, v7, v8), (u4, w1, u5, w2))):    # SOUTH (+z, the back)
                    uvs = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
                    out.append((verts, uvs))
        for c in p.children:
            walk(c, M2, t2, hidden)

    walk(root, I, np.zeros(3), False)
    return out


def render(faces, imgs, density, view, size, owner, box=None, with_mask=False):
    base = np.asarray(imgs["base"], dtype=np.float32) / 255.0
    team = np.asarray(imgs["team"], dtype=np.float32) / 255.0
    glow = np.asarray(imgs["glow"], dtype=np.float32) / 255.0
    th, tw = base.shape[:2]
    R = np.array(view_matrix(view[0], view[1]))
    flip = np.diag([1.0, -1.0, 1.0])           # model space (y down) -> DSL space (y up)
    proj = []
    for verts, uvs in faces:
        P = [R @ (flip @ v + np.array([0, 24.0, 0])) for v in verts]
        n = np.cross(P[1] - P[0], P[2] - P[0])
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n = -n / nl     # Minecraft winds its polygons for y-down space: after the flip the outward normal is -cross
        # the viewer looks along +z
        if n[2] > 0:
            continue
        nw = np.linalg.inv(R) @ n                # back to DSL space for the lights
        light = min(1.0, 0.6 * (max(0.0, nw @ L0) + max(0.0, nw @ L1)) + 0.4)
        proj.append((P, uvs, light))
    if box is None:
        xs = [p[0] for P, _, _ in proj for p in P]
        ys = [p[1] for P, _, _ in proj for p in P]
        box = (min(xs), max(xs), min(ys), max(ys))
    lo_x, hi_x, lo_y, hi_y = box
    span = max(hi_x - lo_x, hi_y - lo_y) * 1.08
    sc = size / span
    ox = (size - (hi_x - lo_x) * sc) / 2 - lo_x * sc
    oy = size - ((size - (hi_y - lo_y) * sc) / 2) + lo_y * sc
    img = np.zeros((size, size, 3), dtype=np.float32)
    img[:] = (0.50, 0.62, 0.36)                 # grass-ish, so cream plate is judged on a game-like ground
    zbuf = np.full((size, size), np.inf, dtype=np.float32)
    owner = np.array(owner, dtype=np.float32)
    for P, uvs, light in proj:
        S = [np.array([ox + p[0] * sc, oy - p[1] * sc, p[2]]) for p in P]
        # q = S1 + a (S0 - S1) + b (S2 - S1); (u, v) = uv1 + a (uv0 - uv1) + b (uv2 - uv1)
        e1, e2 = S[0][:2] - S[1][:2], S[2][:2] - S[1][:2]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        xs = [s[0] for s in S]
        ys = [s[1] for s in S]
        x0, x1 = max(0, int(math.floor(min(xs)))), min(size - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(size - 1, int(math.ceil(max(ys))))
        if x1 < x0 or y1 < y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        qx, qy = gx - S[1][0], gy - S[1][1]
        a = (qx * e2[1] - qy * e2[0]) / det
        b = (e1[0] * qy - e1[1] * qx) / det
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        z = S[1][2] + a * (S[0][2] - S[1][2]) + b * (S[2][2] - S[1][2])
        u = uvs[1][0] + a * (uvs[0][0] - uvs[1][0]) + b * (uvs[2][0] - uvs[1][0])
        v = uvs[1][1] + a * (uvs[0][1] - uvs[1][1]) + b * (uvs[2][1] - uvs[1][1])
        ti = np.clip((u * density).astype(int), 0, tw - 1)
        tj = np.clip((v * density).astype(int), 0, th - 1)
        texel = base[tj, ti]
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        ok = inside & (texel[..., 3] > 0.1) & (z < sub)
        if not ok.any():
            continue
        col = texel[..., :3].copy()
        tm = team[tj, ti]
        has_team = tm[..., 3] > 0.1
        col[has_team] = tm[has_team][:, :3] * owner
        col = col * light
        gl = glow[tj, ti]
        col = np.clip(col + gl[..., :3] * gl[..., 3:4] * 0.8, 0, 1)
        region = img[y0:y1 + 1, x0:x1 + 1]
        region[ok] = col[ok]
        sub[ok] = z[ok]
    if with_mask:
        return Image.fromarray((img * 255).astype(np.uint8)), box, np.isfinite(zbuf)
    return Image.fromarray((img * 255).astype(np.uint8)), box


# ---- art checks (claude/art-theory.md): what the eye reads before colour and detail ----
FAR_PX = 36          # about how tall a soldier stands on screen at the usual RTS zoom


def value_view(im):
    """Greyscale by perceived luminance (Rec. 709), ground included: does the body separate from the grass, and do its
    light / mid / dark groups read without colour?"""
    a = np.asarray(im, dtype=np.float32) / 255.0
    y = a[..., 0] * 0.2126 + a[..., 1] * 0.7152 + a[..., 2] * 0.0722
    return Image.fromarray((np.clip(y, 0, 1) * 255).astype(np.uint8)).convert("RGB")


def silhouette_view(mask):
    """The body as one flat black shape: does the role read from the outline alone?"""
    out = np.full(mask.shape + (3,), 236, dtype=np.uint8)
    out[mask] = (18, 18, 22)
    return Image.fromarray(out)


def far_view(im, mask, size):
    """The colour render shrunk until the body is ~FAR_PX tall (the far RTS read), then blown up pixel for pixel:
    team colour, signature part and silhouette must survive this."""
    ys = np.where(mask.any(axis=1))[0]
    h = (ys[-1] - ys[0] + 1) if len(ys) else size
    k = FAR_PX / float(h)
    small = im.resize((max(1, int(size * k)), max(1, int(size * k))), Image.BILINEAR)
    return small.resize((size, size), Image.NEAREST)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model")
    ap.add_argument("--team", default="")
    ap.add_argument("--owner", default="#3a6fd1", help="the owner's display colour for the team mask")
    ap.add_argument("--views", default="front,quarter,side,rts")
    ap.add_argument("--poses", default='{"rest": {}}', help="JSON {name: {bone: [x, y, z] DSL degrees}} or @file")
    ap.add_argument("--no-detail", action="store_true")
    ap.add_argument("--upgrade", action="store_true")
    ap.add_argument("--hide", default="", help="bones not drawn (comma list); a pose may add its own with \"_hide\"")
    ap.add_argument("--art-check", action="store_true",
                    help="per view and pose: colour, value (greyscale), silhouette and the far read (claude/art-theory.md)")
    ap.add_argument("--size", type=int, default=420)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    m = render_concept.load_model(a.model)
    m["_path"] = os.path.abspath(a.model)
    team = [t for t in a.team.split(",") if t]
    _java, imgs, info = d2g.build(m, "Preview", team, "preview", d2g.DENSITY)
    root = info["root"]
    owner = tuple(int(a.owner.lstrip("#")[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
    poses = json.load(open(a.poses[1:])) if a.poses.startswith("@") else json.loads(a.poses)
    views = [v for v in a.views.split(",") if v]
    rows = []
    for vname in views:
        cells = []
        box = None
        rendered = []
        for pname, extra in poses.items():
            pose_mc = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}
            hide = set(h for h in a.hide.split(",") if h) | set(extra.get("_hide", []))
            for bone, r in extra.items():
                if bone.startswith("_"):
                    continue
                cur = pose_mc.get(bone, (0.0, 0.0, 0.0))
                add = d2g.mc_rot(r)
                pose_mc[bone] = tuple(cur[i] + add[i] for i in range(3))
            fs = faces_of(root, pose_mc, not a.no_detail, extra.get("_upgrade", a.upgrade), hide)
            rendered.append((pname, fs))
        # one framing per row: the union of every pose's projection
        for pname, fs in rendered:
            _, b = render(fs, imgs, d2g.DENSITY, VIEWS[vname], 64, (1, 1, 1))
            box = b if box is None else (min(box[0], b[0]), max(box[1], b[1]), min(box[2], b[2]), max(box[3], b[3]))
        for pname, fs in rendered:
            im, _, mask = render(fs, imgs, d2g.DENSITY, VIEWS[vname], a.size, owner, box, with_mask=True)
            cells.append(("%s - %s" % (pname, vname), im))
            if a.art_check:
                cells.append(("value", value_view(im)))
                cells.append(("silhouette", silhouette_view(mask)))
                cells.append(("far (~%d px)" % FAR_PX, far_view(im, mask, a.size)))
        rows.append(cells)
    cols = max(len(r) for r in rows)
    W, H = cols * (a.size + 8) + 8, len(rows) * (a.size + 22) + 8
    sheet = Image.new("RGB", (W, H), (245, 243, 239))
    d = ImageDraw.Draw(sheet)
    for r, cells in enumerate(rows):
        for c, (label, im) in enumerate(cells):
            x, y = 8 + c * (a.size + 8), 8 + r * (a.size + 22)
            sheet.paste(im, (x, y))
            d.text((x + 4, y + a.size + 4), label, fill=(40, 40, 40))
    sheet.save(a.out)
    print(a.out)


if __name__ == "__main__":
    main()
