#!/usr/bin/env python3
"""White Angels lane, motion round: frame strips of every clip tools/angel_anim.py drives, rendered the way
tools/game_preview.py draws the game (its own faces_of / render, the same pose semantics as --poses: DSL degrees
added to the rest pose, "_move" px, "_scale"), then a contact sheet and an animated GIF per clip (PIL).

    python3 tools/render/white_angels/strips.py --out out --key b2 [--views rts,quarter] [--size 220] [--jobs 4]

Per kind and clip it writes out/strips/<key>_<kind>_<clip>.png (rows = views, columns = frames, every 2nd tick;
every 4th when a clip would pass --max-frames), <key>_<kind>_<clip>.gif (the views side by side, real time: a
2-tick frame lasts 100 ms) and <key>_<kind>_<clip>_poses.json (the frames as game_preview --poses @file takes them).
Every frame of a clip and view shares ONE framing (the union of all its frames): a clip that travels (the arrival's
descent, the death's rise, the dive cut's lunge, the bob) shows its true displacement, never re-centred on the body.
The arrival / death light ("_fx" white, alpha) is composited on the frame's own silhouette (the halo's separate
late fade is not drawn).
"""
import argparse
import json
import os
import sys
from multiprocessing import Pool

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import angel_anim as A  # noqa: E402
import game_preview as gp  # noqa: E402
import render_concept  # noqa: E402
import dsl_to_game as d2g  # noqa: E402
from preview_model import view_matrix  # noqa: E402

LIGHT = np.array([1.0, 0.98, 0.9], dtype=np.float32)
_CACHE = {}


def model(key, kind):
    if (key, kind) not in _CACHE:
        path = os.path.join(ROOT, "design", "models", "sunforged_angel_%s_%s.json" % (key, kind))
        m = render_concept.load_model(path)
        m["_path"] = path
        _java, imgs, info = d2g.build(m, "Preview", ["tabard", "tabard_dark"], "preview", d2g.DENSITY)
        _CACHE[(key, kind)] = (m, imgs, info["root"])
    return _CACHE[(key, kind)]


def faces(m, root, extra):
    pose_mc = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}
    for bone, r in extra.items():
        if bone.startswith("_"):
            continue
        cur = pose_mc.get(bone, (0.0, 0.0, 0.0))
        add = d2g.mc_rot(r)
        pose_mc[bone] = tuple(cur[i] + add[i] for i in range(3))
    return gp.faces_of(root, pose_mc, True, False, set(extra.get("_hide", [])), extra.get("_move"),
                       extra.get("_scale"))


def bbox(fs, view):
    """The projected box game_preview.render frames to (same projection), without rasterising."""
    R = np.array(view_matrix(view[0], view[1]))
    flip = np.diag([1.0, -1.0, 1.0])
    V = np.array([v for f in fs for v in f[0]], dtype=float)
    P = (R @ (V @ flip.T + np.array([0, 24.0, 0])).T).T
    return P[:, 0].min(), P[:, 0].max(), P[:, 1].min(), P[:, 1].max()


def fx_frame(im, mask, fx):
    a = np.asarray(im, dtype=np.float32) / 255.0
    w, al = float(fx.get("white", 0.0)), float(fx.get("alpha", 1.0))
    if w <= 0.001 and al >= 0.999:
        return im
    ground = np.array(gp.GROUND, dtype=np.float32)
    col = a * (1.0 - w) + LIGHT * w
    out = a.copy()
    out[mask] = ground * (1.0 - al) + col[mask] * al
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def task(args):
    key, kind, clip, views, size, every, max_frames, outdir = args
    m, imgs, root = model(key, kind)
    fr = A.run(kind, clip)
    step = every
    while len(range(0, len(fr), step)) > max_frames:
        step *= 2
    picks = [(t, p) for t, p in fr if t % step == 0]
    with open(os.path.join(outdir, "%s_%s_%s_poses.json" % (key, kind, clip)), "w") as f:
        json.dump({"t%02d" % t: A.rnd(p) for t, p in picks}, f, separators=(",", ":"))
    allf = [(t, p, faces(m, root, p)) for t, p in picks]
    rows = []
    values = {}
    for vn in views:
        V = gp.VIEWS[vn]
        bx = None
        for _, _, fs in allf:
            b = bbox(fs, V)
            bx = b if bx is None else (min(bx[0], b[0]), max(bx[1], b[1]), min(bx[2], b[2]), max(bx[3], b[3]))
        cells = []
        for t, p, fs in allf:
            im, _, mask, _ = gp.render(fs, imgs, d2g.DENSITY, V, size, (0.23, 0.44, 0.82), bx, with_mask=True)
            # the wings' value: the pixels the wings change (a second render with both wings hidden), mean Rec.709
            # luminance 0-255 at the frame's own size (a far read keeps the mean)
            nw = dict(p)
            nw["_hide"] = list(p.get("_hide", [])) + ["wing_l_arm", "wing_r_arm"]
            im2, _ = gp.render(faces(m, root, nw), imgs, d2g.DENSITY, V, size, (0.23, 0.44, 0.82), bx)
            a1 = np.asarray(im, dtype=np.float32)
            a2 = np.asarray(im2, dtype=np.float32)
            wing = mask & (np.abs(a1 - a2).max(axis=2) > 2.0)
            lum = a1[..., 0] * 0.2126 + a1[..., 1] * 0.7152 + a1[..., 2] * 0.0722
            val = float(lum[wing].mean()) if wing.any() else float("nan")
            values.setdefault(vn, []).append((t, round(val, 1), int(wing.sum())))
            cells.append((t, fx_frame(im, mask, p.get("_fx", {}))))
        rows.append((vn, cells))
    # contact sheet: a title line, then one row per view
    n = len(allf)
    W, H = 8 + n * (size + 4) + 70, 26 + len(rows) * (size + 18) + 4
    sheet = Image.new("RGB", (W, H), (245, 243, 239))
    d = ImageDraw.Draw(sheet)
    ev = (" event t%d" % A.EVENT[kind]) if clip == "attack" else ""
    d.text((8, 6), "%s %s - %s, every %d ticks (20 tps)%s; one framing per row (true displacement)" % (
        kind, clip, key, step, ev), fill=(30, 30, 30))
    for r, (vn, cells) in enumerate(rows):
        y = 26 + r * (size + 18)
        d.text((8, y + size // 2), vn, fill=(30, 30, 30))
        for c, (t, im) in enumerate(cells):
            x = 70 + c * (size + 4)
            sheet.paste(im, (x, y))
            mark = " *" if (clip == "attack" and t == A.EVENT[kind]) else ""
            d.text((x + 3, y + size + 2), "t%d%s" % (t, mark), fill=(30, 30, 30))
    base = os.path.join(outdir, "%s_%s_%s" % (key, kind, clip))
    sheet.save(base + ".png")
    gif = []
    for c in range(n):
        g = Image.new("RGB", (len(rows) * (size + 4) + 4, size + 18), (245, 243, 239))
        gd = ImageDraw.Draw(g)
        for r, (vn, cells) in enumerate(rows):
            g.paste(cells[c][1], (4 + r * (size + 4), 4))
        gd.text((6, size + 5), "%s %s t%d" % (kind, clip, cells[c][0]), fill=(30, 30, 30))
        gif.append(g)
    gif[0].save(base + ".gif", save_all=True, append_images=gif[1:], duration=int(50 * step), loop=0)
    with open(base + "_wing_value.json", "w") as f:
        json.dump(values, f)
    mins = " ".join("%s min %.0f@t%d" % (vn, *min(((v, t) for t, v, _ in vals if v == v), default=(float("nan"), -1)))
                    for vn, vals in values.items())
    return "%s %s %s: %d frames (every %d), %d views; wing value (0-255) %s" % (key, kind, clip, n, step, len(rows),
                                                                               mins)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--key", default="b2")
    ap.add_argument("--views", default="rts,quarter")
    ap.add_argument("--size", type=int, default=220)
    ap.add_argument("--every", type=int, default=2)
    ap.add_argument("--max-frames", type=int, default=16)
    ap.add_argument("--jobs", type=int, default=max(1, min(4, os.cpu_count() or 1)))
    ap.add_argument("--kinds", default=",".join(A.KINDS))
    ap.add_argument("--clips", default=",".join(A.CLIPS))
    a = ap.parse_args()
    outdir = os.path.join(os.path.abspath(a.out), "strips")
    os.makedirs(outdir, exist_ok=True)
    tasks = [(a.key, k, c, a.views.split(","), a.size, a.every, a.max_frames, outdir)
             for k in a.kinds.split(",") for c in a.clips.split(",")]
    if a.jobs > 1:
        with Pool(a.jobs) as pool:
            for line in pool.imap_unordered(task, tasks):
                print(line, flush=True)
    else:
        for t in tasks:
            print(task(t), flush=True)


if __name__ == "__main__":
    main()
