#!/usr/bin/env python3
"""The White Angel capitol's statue, offline: the drawn marble angel (design/models/white_angel_statue.json through
tools/dsl_to_game.py's painted atlas, as tools/game_preview.py draws a unit) close up - front, three-quarter, side,
the RTS angle, the face and a wing - posed as BuildingParts.statueWingFold poses it at a capitol level
(gen_angel_statue.fold_pose: the wings' roots turn back and droop, the hands fold the primaries, the halo's outer
ring shows from L6), and stood on the capitol's plinth (tools/capitol_white_angel_v5.py STATUE_ANCHOR) at the RTS
camera with our block pack's textures (tools/capitol_sheet.py) - so the statue is judged where it stands before a
CI run.

    python3 tools/angel_statue_preview.py --out design/buildings/angel_statue_v2.png [--title ...]

The RTS camera looks north-east from the south-west (the west and south faces and the roofs; south-east to its
right), pitched down 50 degrees. The statue faces that camera (gen_angel_statue.FACING).
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_concept                  # noqa: E402
import dsl_to_game as d2g              # noqa: E402
import game_preview as GPV             # noqa: E402
import gen_angel_statue as GA          # noqa: E402
import capitol_sheet as CS             # noqa: E402
from preview_model import view_matrix  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
MODEL = os.path.join(ROOT, "design", "models", "white_angel_statue.json")
HEIGHT_BLOCKS = GA.SCALE_BLOCKS      # BuildingParts.STATUES: soles to wing tips
SKY = CS.SKY
BG = (24, 24, 28)
Raster = CS.Raster


# ------------------------------------------------------------------ the statue -----------------------------------
_MODEL = {}


def statue_model():
    if not _MODEL:
        m = render_concept.load_model(MODEL)
        m["_path"] = MODEL
        _java, imgs, info = d2g.build(m, "Preview", [], "preview", d2g.DENSITY)
        from preview_model import posed_cubes
        ys = [p[1] for pts, _ in posed_cubes(m) for p in pts]
        _MODEL.update(m=m, imgs=imgs, root=info["root"], model_px=max(ys) - min(ys))
    return _MODEL


def statue_faces(level, detail=True):
    """The statue's faces in DSL px (y up from the soles), posed as at `level`, with Minecraft's light."""
    S = statue_model()
    m = S["m"]
    pose = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}
    for bone, rot in GA.fold_pose(level).items():
        cur = pose.get(bone, (0.0, 0.0, 0.0))
        add = d2g.mc_rot(rot)
        pose[bone] = tuple(cur[i] + add[i] for i in range(3))
    hide = () if level >= 6 else ("halo_outer",)
    out = []
    for verts, uvs in GPV.faces_of(S["root"], pose, detail, False, hide=hide):
        P = [np.array([v[0], 24.0 - v[1], v[2]]) for v in verts]
        n = np.cross(P[1] - P[0], P[2] - P[0])
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n = -n / nl                      # outward (Minecraft winds its polygons for y-down space)
        light = min(1.0, 0.6 * (max(0.0, n @ GPV.L0) + max(0.0, n @ GPV.L1)) + 0.4)
        out.append((P, n, uvs, light))
    return out


def draw_statue(r, R, faces, origin=(0.0, 0.0, 0.0), k=1.0):
    S = statue_model()
    base = np.asarray(S["imgs"]["base"], dtype=np.float32) / 255.0
    o = np.array(origin)
    for P, n, uvs, light in faces:
        if (R @ n)[2] > 0:
            continue
        r.quad([R @ (o + k * p) for p in P], uvs=uvs, tex=(base, d2g.DENSITY), light=light)


def statue_closeup(view, level=6, size=520, centre=None, zoom=1.0):
    """The statue alone from a (yaw, tilt) camera; centre (DSL px) and zoom for a detail crop (the face, a wing)."""
    faces = statue_faces(level)
    R = np.array(view_matrix(*view))
    pts = np.array([R @ p for P, _, _, _ in faces for p in P])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    sc = size / (max(hi[0] - lo[0], hi[1] - lo[1]) * 1.08) * zoom
    if centre is None:
        cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    else:
        c = R @ np.array(centre, dtype=float)
        cx, cy = c[0], c[1]
    r = Raster(size, size, sc, size / 2 - cx * sc, size / 2 + cy * sc, SKY)
    draw_statue(r, R, faces)
    return r.image()


# ------------------------------------------------------------------ the sheet ------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True)
    ap.add_argument("--title", default=None)
    ap.add_argument("--no-capitol", action="store_true", help="the statue alone (fast)")
    a = ap.parse_args()
    S = statue_model()
    nc = sum(len(b.get("cubes", [])) for b in S["m"]["bones"])
    # the statue is yawed to face the RTS camera: the close-up cameras turn with it, so "front" is its front
    f = GA.FACING - 90          # the front shows at the RTS camera's yaw (-45): the statue faces it
    close = [("front, L6", statue_closeup((f, 0))), ("three-quarter, L6", statue_closeup((f - 40, 18))),
             ("side, L6", statue_closeup((f + 90, 0))), ("the RTS angle, L6", statue_closeup((-45, 50))),
             ("the RTS angle, L1: wings closed", statue_closeup((-45, 50), 1)),
             ("front, L1", statue_closeup((f, 0), 1))]
    hy = GA.SHOULDER_Y + 1.4 + 2.2
    details = [("the face (front, bowed)", statue_closeup((f, -8), centre=(0, hy + 2.6, -1.5), zoom=5.0)),
               ("the face, three-quarter", statue_closeup((f - 35, 10), centre=(0, hy + 2.6, -1.5), zoom=5.0)),
               ("the left wing's feathers", statue_closeup((f, 0), centre=(GA.WING_ROOT[0] + 16, GA.WING_ROOT[1] + 16, 0),
                                                           zoom=2.4)),
               ("the skirt, armour and sword", statue_closeup((f - 25, 8), centre=(0, 22, -3), zoom=2.6))]
    cap = []
    if not a.no_capitol:
        import capitol_white_angel_v5 as CAP
        rigs = {lv: CAP.level(lv) for lv in (1, 6)}
        cells = {lv: CS.rig_cells(r) for lv, r in rigs.items()}
        looks_far, looks_near = CS.Looks(32), CS.Looks(64)
        R_rts = np.array(view_matrix(*CS.RTS_VIEW))
        ax, ay, az = CAP.STATUE_ANCHOR
        crown = ((ax, ay + 7, az), None)
        cap = [("L6 at the RTS camera (9 px/block)", CS.view(cells[6], R_rts, 9, looks_far, statue=(6, CAP.STATUE_ANCHOR))),
               ("the crown, L6 (22 px/block)", CS.view(cells[6], R_rts, 22, looks_near, crop=crown, size=640,
                                                       statue=(6, CAP.STATUE_ANCHOR))),
               ("the crown, L1: wings closed, no outer halo", CS.view(cells[1], R_rts, 22, looks_near, crop=crown,
                                                                      size=640, statue=(1, CAP.STATUE_ANCHOR))),
               ("full zoom-out (3.5 px/block)", CS.view(cells[6], R_rts, 3.5, looks_far, statue=(6, CAP.STATUE_ANCHOR)))]
    f1, f2 = CS.font(30), CS.font(20)
    pad = 24
    rows = [close, details] + ([cap] if cap else [])
    W = max(sum(im.width for _, im in row) + pad * (len(row) + 1) for row in rows)
    H = 70 + sum(max(im.height for _, im in row) + 30 + 20 for row in rows)
    sheet = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    title = a.title or ("White Angel capitol - the marble angel statue v2 (a drawn model, %d cubes, %.0f blocks to the "
                        "wing tips, on the dome's plinth, facing the RTS camera)" % (nc, HEIGHT_BLOCKS))
    d.text((pad, 18), title, fill=(235, 235, 235), font=f1)
    y = 70
    for row in rows:
        y += CS.label_row(sheet, d, y, row, f2, pad, align_bottom=False) + 20
    sheet.save(a.out, optimize=True)
    print(a.out, sheet.size)


if __name__ == "__main__":
    main()
