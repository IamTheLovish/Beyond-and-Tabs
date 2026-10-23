#!/usr/bin/env python3
"""The game's look, offline: draws what tools/dsl_to_game.py exports - the part tree, the painted 4x atlas through
Minecraft's box-UV mapping, the team mask in an owner's colour, the glow pass, Minecraft's two-light entity shading,
the "lod" detail on or off - so a unit is seen as the game will draw it before a 25-minute build and an eyes run.
It also catches what only a textured render shows: a face sampling outside its island (a hole: the cutout drops
transparent texels), a gradient running the wrong way, a team colour on the wrong cloth.

    python3 tools/game_preview.py design/models/sunforged_paladin_radiant_v2.json --team tabard,tabard_dark \\
        --owner "#d13a3a" --out paladin_game.png [--no-detail] [--upgrade] [--poses '{"strike": {"arms": [-22, 0, 0]}}']
        [--art-check]   (adds value / silhouette / far-read cells per view and prints the limb check -
                         claude/art-theory.md, tools/limb_check.py)

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
import limb_check  # noqa: E402
import contact_check  # noqa: E402

VIEWS = {"front": (0, 0), "quarter": (-40, 18), "quarter_l": (40, 18), "side": (90, 0), "back": (180, 0),
         "rts": (-35, 52), "topdown": (-35, 72)}   # topdown: the RTS camera tilted nearly straight down (a flier seen from above)
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])     # Minecraft's entity lights (y up)
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
# LIGHT="x,y,z" in the environment: ONE directional sun (the direction the light travels, DSL space, y up) in place of
# Minecraft's two fixed entity lights, with a flat sky fill - a shader pack's low sun (the eyes scenes: "-0.85,-0.35,0.30")
SUN = None
if os.environ.get("LIGHT"):
    SUN = -np.array([float(v) for v in os.environ["LIGHT"].split(",")], dtype=float)
    SUN = SUN / np.linalg.norm(SUN)


class FaceTag(tuple):
    """Where a face came from, for motion trails (tools/anim_sheet.py): `path` = the part names from the root down to the
    part that owns the box, `pivots` = the world pivot of every part on that path (model space, y down)."""
    __slots__ = ()

    def __new__(cls, path, pivots):
        return tuple.__new__(cls, (path, pivots))

    path = property(lambda self: self[0])
    pivots = property(lambda self: self[1])


def faces_of(root, pose_mc, detail, upgrade, hide=(), move=None, scale=None, alpha=None, tags=None):
    """[(4 model-space vertices in Polygon order, 4 (u, v) in model pixels)] for every visible box face. Bones named in
    hide are not drawn (with their children) - what a model subclass does with part.visible. move: {bone: [dx, dy, dz]
    DSL px} added to the part's offset (ModelPart.x/y/z += in the game, y flipped); scale: {bone: [sx, sy, sz]}
    applied after its rotation (ModelPart.xScale... - children inherit it), as ModelPart.translateAndRotate does.
    alpha: {bone name prefix: 0..1} - a part whose name starts so is drawn translucent (the Bone Dragon's sail, which
    BoneDragonRenderer draws in its own pass at SAIL_ALPHA); its faces come back as (verts, uvs, alpha) triples.
    tags: pass an empty list and it gets one FaceTag per returned face (same order) - which part drew it and where the
    parts' pivots are, so a vertex can be followed from pose to pose (the faces' order does not depend on the pose)."""
    move, scale = move or {}, scale or {}
    out = []
    I = np.eye(3)

    def walk(p, M, t, hidden, a, path=(), pivots=()):
        if alpha:
            for pre, av in alpha.items():
                if p.name.startswith(pre):
                    a = av
        if p.name == "lod" and not detail:
            hidden = True
        if p.name.startswith("upg_") and not upgrade:
            hidden = True
        if p.name in hide:
            hidden = True
        rot = list(p.rot)
        if p.name in pose_mc:
            rot = [rot[a] + pose_mc[p.name][a] for a in range(3)]
        off = np.array(p.offset, dtype=float)
        if p.name in move:
            dm = move[p.name]
            off = off + np.array([dm[0], -dm[1], dm[2]], dtype=float)
        t2 = t + M @ off
        M2 = M @ np.array(d2g.mat_zyx(rot))
        if p.name in scale:
            M2 = M2 @ np.diag([float(v) for v in scale[p.name]])
        path, pivots = path + (p.name,), pivots + (t2,)
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
                    out.append((verts, uvs) if a is None else (verts, uvs, a))
                    if tags is not None:
                        tags.append(FaceTag(path, pivots))
        for c in p.children:
            walk(c, M2, t2, hidden, a, path, pivots)

    walk(root, I, np.zeros(3), False, None)
    return out


_FLIP = np.array([1.0, -1.0, 1.0])
_LIFT = np.array([0.0, 24.0, 0.0])


def _project(faces, view):
    """The faces seen from `view`, back faces and degenerate ones dropped: (P (k,4,3) in view space, light (k,),
    kept (k,) indices into faces). One numpy pass for every face - the per-face Python of the first version (a cross
    product, an inverse and a dozen small arrays each) was 60% of a render, and the framing pass of a clip rendered
    every pose a second time just to read its bounding box (Oct 22, tools/anim_sheet.py: a sheet must take seconds)."""
    if not faces:
        return np.zeros((0, 4, 3)), np.zeros(0), np.zeros(0, dtype=int)
    R = np.array(view_matrix(view[0], view[1]))
    V = np.array([f[0] for f in faces], dtype=float)                # (n, 4, 3) model space, y down
    P = (V * _FLIP + _LIFT) @ R.T                                   # view space: R @ (flip @ v + (0, 24, 0))
    n = np.cross(P[:, 1] - P[:, 0], P[:, 2] - P[:, 0])
    nl = np.linalg.norm(n, axis=1)
    # Minecraft winds its polygons for y-down space: after the flip the outward normal is -cross; the viewer looks
    # along +z, so a face whose normal points away (+z) is not drawn
    keep = (nl >= 1e-9)
    with np.errstate(invalid="ignore", divide="ignore"):
        n = -n / nl[:, None]
    keep &= ~(n[:, 2] > 0)
    kept = np.nonzero(keep)[0]
    n = n[kept]
    nw = n @ np.linalg.inv(R).T                                     # back to DSL space for the lights
    if SUN is not None:
        light = np.minimum(1.0, 0.75 * np.maximum(0.0, nw @ SUN) + 0.35 + 0.10 * np.maximum(0.0, nw[:, 1]))
    else:
        light = np.minimum(1.0, 0.6 * (np.maximum(0.0, nw @ L0) + np.maximum(0.0, nw @ L1)) + 0.4)
    return P[kept], light, kept


def bbox(faces, view):
    """The (min x, max x, min y, max y) render() would frame this pose with, without drawing anything."""
    P, _, _ = _project(faces, view)
    if not len(P):
        return (0.0, 0.0, 0.0, 0.0)
    return (float(P[:, :, 0].min()), float(P[:, :, 0].max()), float(P[:, :, 1].min()), float(P[:, :, 1].max()))


def view_map(box, size):
    """(scale, ox, oy): view-space (x, y) -> pixel (ox + x * scale, oy - y * scale) for a render framed by `box`."""
    lo_x, hi_x, lo_y, hi_y = box
    span = max(hi_x - lo_x, hi_y - lo_y) * 1.08
    sc = size / span
    ox = (size - (hi_x - lo_x) * sc) / 2 - lo_x * sc
    oy = size - ((size - (hi_y - lo_y) * sc) / 2) + lo_y * sc
    return sc, ox, oy


def _atlas(imgs):
    """The three atlas layers as float arrays, converted ONCE per atlas (three 2048 x 1024 RGBA conversions were 70 ms of
    every render's 140), and whether the team / glow layers paint anything at all (when they do not, those passes are
    skipped - the result is the same, the arithmetic on all-transparent texels being a no-op)."""
    got = imgs.get("_f32")
    if got is None:
        base = np.asarray(imgs["base"], dtype=np.float32) / 255.0
        team = np.asarray(imgs["team"], dtype=np.float32) / 255.0
        glow = np.asarray(imgs["glow"], dtype=np.float32) / 255.0
        got = (base, team, glow, bool((team[..., 3] > 0.1).any()), bool((glow[..., 3] > 0).any()))
        imgs["_f32"] = got
    return got


def render(faces, imgs, density, view, size, owner, box=None, with_mask=False):
    base, team, glow, any_team, any_glow = _atlas(imgs)
    th, tw = base.shape[:2]
    P, light, kept = _project(faces, view)
    fa = np.array([(faces[i][2] if len(faces[i]) > 2 else 1.0) for i in kept], dtype=float)
    # the translucent faces last, far to near, over the finished opaque body (the game's own translucent pass)
    order = sorted(range(len(kept)), key=lambda i: (fa[i] < 1.0, -P[i, :, 2].max() if fa[i] < 1.0 else 0.0))
    if box is None:
        box = ((float(P[:, :, 0].min()), float(P[:, :, 0].max()), float(P[:, :, 1].min()), float(P[:, :, 1].max()))
               if len(P) else (0.0, 0.0, 0.0, 0.0))
    sc, ox, oy = view_map(box, size)
    img = np.zeros((size, size, 3), dtype=np.float32)
    img[:] = (0.50, 0.62, 0.36)                 # grass-ish, so cream plate is judged on a game-like ground
    zbuf = np.full((size, size), np.inf, dtype=np.float32)
    teamhit = np.zeros((size, size), dtype=bool)              # which pixels show team colour (art-check share)
    owner = np.array(owner, dtype=np.float32)
    # every face's screen coordinates and bounds in one go (the loop below only rasterises)
    SX = ox + P[:, :, 0] * sc
    SY = oy - P[:, :, 1] * sc
    SZ = P[:, :, 2]
    E1x, E1y = SX[:, 0] - SX[:, 1], SY[:, 0] - SY[:, 1]
    E2x, E2y = SX[:, 2] - SX[:, 1], SY[:, 2] - SY[:, 1]
    DET = E1x * E2y - E1y * E2x
    X0 = np.maximum(0, np.floor(SX.min(axis=1))).astype(int)
    X1 = np.minimum(size - 1, np.ceil(SX.max(axis=1))).astype(int)
    Y0 = np.maximum(0, np.floor(SY.min(axis=1))).astype(int)
    Y1 = np.minimum(size - 1, np.ceil(SY.max(axis=1))).astype(int)
    for i in order:
        det = DET[i]
        if abs(det) < 1e-9:
            continue
        x0, x1, y0, y1 = X0[i], X1[i], Y0[i], Y1[i]
        if x1 < x0 or y1 < y0:
            continue
        uvs = faces[kept[i]][1]
        # q = S1 + a (S0 - S1) + b (S2 - S1); (u, v) = uv1 + a (uv0 - uv1) + b (uv2 - uv1)
        qx = (np.arange(x0, x1 + 1) + 0.5)[None, :] - SX[i, 1]
        qy = (np.arange(y0, y1 + 1) + 0.5)[:, None] - SY[i, 1]
        e1x, e1y, e2x, e2y = E1x[i], E1y[i], E2x[i], E2y[i]
        a = (qx * e2y - qy * e2x) / det
        b = (e1x * qy - e1y * qx) / det
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        z = SZ[i, 1] + a * (SZ[i, 0] - SZ[i, 1]) + b * (SZ[i, 2] - SZ[i, 1])
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
        has_team = None
        if any_team:
            tm = team[tj, ti]
            has_team = tm[..., 3] > 0.1
            col[has_team] = tm[has_team][:, :3] * owner
        col = col * light[i]
        if any_glow:
            gl = glow[tj, ti]
            col = np.clip(col + gl[..., :3] * gl[..., 3:4] * 0.8, 0, 1)
        else:
            col = np.clip(col, 0, 1)
        region = img[y0:y1 + 1, x0:x1 + 1]
        if fa[i] < 1.0:
            region[ok] = region[ok] * (1.0 - fa[i]) + col[ok] * fa[i]
        else:
            region[ok] = col[ok]
        sub[ok] = z[ok]
        if has_team is not None:
            teamhit[y0:y1 + 1, x0:x1 + 1][ok] = has_team[ok]
    if with_mask:
        return Image.fromarray((img * 255).astype(np.uint8)), box, np.isfinite(zbuf), teamhit
    return Image.fromarray((img * 255).astype(np.uint8)), box


# ---- art checks (claude/art-theory.md): what the eye reads before colour and detail ----
FAR_PX = 36          # about how tall a soldier stands on screen at the usual RTS zoom
GROUND = (0.50, 0.62, 0.36)  # the grass render() paints behind the body
# the art director's pass marks (claude/art-theory.md section 15), judged on the RTS view (a quarter view shows the
# side Minecraft lights at ~0.5, so its light share is lower by nature): share of body pixels
MARK_LIGHT = 0.30    # at least this share brighter than L 180 (the plate must read light in game lighting)
MARK_GRASS = 0.20    # at most this share of the body's own paint within 25 of the ground's lightness
MARK_TEAM = 0.20     # at least this share in team colour (whose unit is it, at a glance)


def luminance(a):
    return a[..., 0] * 0.2126 + a[..., 1] * 0.7152 + a[..., 2] * 0.0722


def art_marks(im, mask, teamhit):
    """The art director's three numbers for one render: light share, near-grass share, team share (of body pixels)."""
    body = mask.sum()
    if body == 0:
        return None
    L = luminance(np.asarray(im, dtype=np.float32))
    g = luminance(np.array(GROUND, dtype=np.float32)) * 255.0
    light = float((L[mask] > 180).sum()) / body
    # the body's own paint near the grass value (team pixels are judged by their share and their hems instead: a
    # green team on green grass is the owner's colour, not a paint mistake)
    own = mask & ~teamhit
    grass = float((np.abs(L[own] - g) <= 25).sum()) / max(1, int(own.sum()))
    team = float(teamhit[mask].sum()) / body
    return light, grass, team


def marks_text(m, judged=True):
    """The three numbers; the pass marks are defined for the RTS camera (how the game is mostly seen), so other views
    print the numbers without a verdict."""
    light, grass, team = m
    if not judged:
        return "light %d%% | own paint near grass %d%% | team %d%%" % (
            round(light * 100), round(grass * 100), round(team * 100))
    ok = lambda b: "ok" if b else "LOW"  # noqa: E731
    return "light %d%% %s | own paint near grass %d%% %s | team %d%% %s" % (
        round(light * 100), ok(light >= MARK_LIGHT), round(grass * 100), "ok" if grass <= MARK_GRASS else "HIGH",
        round(team * 100), ok(team >= MARK_TEAM))


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
    ap.add_argument("--translucent", default="", help="bone-name prefixes drawn translucent, 'mem_,web_:0.6'")
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
    alpha = None
    if a.translucent:
        pres, av = a.translucent.rsplit(":", 1)
        alpha = {p: float(av) for p in pres.split(",") if p}
    if a.art_check:
        # the anatomy before the paint (tools/limb_check.py): elbows, bends, feet - the rest pose, once per model
        for level, text in limb_check.check(m):
            print("limbs      %-4s %s" % (level, text))
        # what passes through what, at rest and at the animation's extremes (design/poses/<model>.json, from the
        # game's keys - tools/contact_check.py): the deepest per pose
        pf = os.path.join(os.path.dirname(os.path.abspath(a.model)), "..", "poses", os.path.basename(a.model))
        sweep = json.load(open(pf)) if os.path.exists(pf) else {}
        sweep = {"rest": sweep.pop("rest", {}), **sweep}
        for pname, spec in sweep.items():
            hits = contact_check.check(m, {k: v for k, v in spec.items() if not k.startswith("_")},
                                       spec.get("_hide", ()), spec.get("_move", {}))
            if hits:
                d, ba, ia, ca, bb, ib, cb = hits[0]
                print("contact    %-12s %2d over 0.6 px, deepest %.2f %s[%d] %s <-> %s[%d] %s" % (
                    pname, len(hits), d, ba, ia, ca, bb, ib, cb))
            else:
                print("contact    %-12s clean" % pname)
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
            fs = faces_of(root, pose_mc, not a.no_detail, extra.get("_upgrade", a.upgrade), hide,
                          extra.get("_move"), extra.get("_scale"), alpha)
            rendered.append((pname, fs))
        # one framing per row: the union of every pose's projection
        for pname, fs in rendered:
            _, b = render(fs, imgs, d2g.DENSITY, VIEWS[vname], 64, (1, 1, 1))
            box = b if box is None else (min(box[0], b[0]), max(box[1], b[1]), min(box[2], b[2]), max(box[3], b[3]))
        for pname, fs in rendered:
            im, _, mask, teamhit = render(fs, imgs, d2g.DENSITY, VIEWS[vname], a.size, owner, box, with_mask=True)
            cells.append(("%s - %s" % (pname, vname), im))
            if a.art_check:
                marks = art_marks(im, mask, teamhit)
                if marks:
                    print("%-10s %-8s %s" % (pname, vname, marks_text(marks, judged=(vname == "rts"))))
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
