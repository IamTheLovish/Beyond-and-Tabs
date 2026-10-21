#!/usr/bin/env python3
"""The White Angel statue v3 (the smooth marble mesh of tools/sculpt_angel.py) on one review sheet: the whole figure
square on and from the RTS camera, the wings closed (L1) to spread (L6), close-ups of the Roman face, the wing's
feathers, the drapery and the hands - in the game's own light (vanilla's per-vertex entity light on the baked vertex
colours) and a shader pack's (per-pixel, sun shadows) - beside the cube statue v2 it replaces.

    python3 tools/angel_mesh_sheet.py --out design/buildings/angel_statue_v3_r1.png [--title ...] [--rebuild]

Builds are cached in local/sculpt/ by a hash of the sculpt's source (tools/sculpt_angel.py, tools/sdf_mesh.py).
"""
import argparse
import hashlib
import os
import pickle
import sys
import time

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sculpt_angel as A           # noqa: E402
import mesh_preview as MP          # noqa: E402

ROOT = A.ROOT
CACHE = os.path.join(ROOT, "local", "sculpt")
RTS_VIEW = (-45.0, 50.0)           # tools/capitol_sheet.RTS_VIEW: the default RTS camera
BG = (24, 24, 28)


def _source_hash():
    h = hashlib.sha1()
    for f in ("sculpt_angel.py", "sdf_mesh.py"):
        h.update(open(os.path.join(ROOT, "tools", f), "rb").read())
    return h.hexdigest()[:12]


_HASH = _source_hash()      # taken when the sculpt is imported: the code that builds is the code that is hashed


def _hash():
    return _HASH


_SCENE = {}


def statue_meshes(lod, rebuild=False):
    """The sculpt at a LOD, baked (cached by the sources' hash)."""
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, "cache_lod%d_%s.pkl" % (lod, _hash()))
    if os.path.exists(path) and not rebuild:
        with open(path, "rb") as fh:
            return pickle.load(fh)
    if "scene" not in _SCENE:
        _SCENE["scene"] = A.SceneField()
    ms = A.build(lod, scene=_SCENE["scene"])
    with open(path, "wb") as fh:
        pickle.dump(ms, fh)
    return ms


def model_px(meshes):
    """The mesh's height at L6 from the soles (what BuildingParts.STATUES' MESH_MODEL_PX holds)."""
    p = A.posed(meshes, level=6, facing=0.0)
    return float(max(m.V[:, 1].max() for m in p))


def parts_of(meshes):
    return [MP.Part(m.V, m.N, m.C, m.Q, m.smoothness()) for m in meshes]


def font(sz):
    import capitol_sheet as CS
    return CS.font(sz)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True)
    ap.add_argument("--title", default=None)
    ap.add_argument("--rebuild", action="store_true")
    ap.add_argument("--quick", action="store_true", help="LOD1 everywhere, fewer panels")
    a = ap.parse_args()
    t0 = time.time()
    hi = statue_meshes(1 if a.quick else 0, a.rebuild)
    lo = statue_meshes(1, a.rebuild)
    far = statue_meshes(2, a.rebuild) if not a.quick else lo
    print("meshes ready %.1fs" % (time.time() - t0))
    counts = {n: sum(len(m.Q) for m in ms) for n, ms in (("LOD0", hi), ("LOD1", lo), ("LOD2", far))}
    mpx = model_px(hi)
    print("quads:", counts, "model px %.1f" % mpx)

    def pose(ms, level, facing=0.0):
        return parts_of(A.posed(ms, level=level, facing=facing))

    L6 = pose(hi, 6)
    L6f = pose(hi, 6, A.FACING)
    L1f = pose(hi, 1, A.FACING)
    L6all = np.concatenate([p.V for p in L6])
    S = 470
    panels = []
    row1 = [("front, L6 (in game: vanilla light)", MP.render(L6, view=(0, 4), size=S, look="game")),
            ("three-quarter, L6 (shader pack)", MP.render(L6, view=(-38, 8), size=S, look="shader")),
            ("side, L6", MP.render(L6, view=(-90, 4), size=S, look="game")),
            ("back, L6", MP.render(L6, view=(180, 8), size=S, look="game")),
            ("the RTS camera, L6", MP.render(L6f, view=RTS_VIEW, size=S, look="game")),
            ("the RTS camera, L1: wings closed", MP.render(L1f, view=RTS_VIEW, size=S, look="game", fit=L6all))]
    panels.append(row1)
    head = A.HEAD_C
    row2 = [("the face (front, bowed)", MP.render(L6, view=(0, 0), size=S, look="shader", zoom=6.2, centre=head + (0, -0.6, 0))),
            ("the face, three-quarter", MP.render(L6, view=(-35, 4), size=S, look="shader", zoom=6.2, centre=head + (0, -0.6, 0))),
            ("the Greek profile", MP.render(L6, view=(-90, 0), size=S, look="shader", zoom=6.2, centre=head + (0, -0.6, -1.5))),
            ("the face in game light", MP.render(L6, view=(-20, 6), size=S, look="game", zoom=6.2, centre=head + (0, -0.6, 0))),
            ("the left wing's feathers", MP.render(L6, view=(-20, 8), size=S, look="shader", zoom=2.3, centre=(18.0, 58.0, 6.0))),
            ("drapery, hands and sword", MP.render(L6, view=(-30, 10), size=S, look="shader", zoom=2.0, centre=(-1.0, 20.0, 0.0)))]
    panels.append(row2)
    row3 = []
    for lv in range(1, 7):
        row3.append(("L%d at the RTS camera" % lv, MP.render(pose(lo, lv, A.FACING), view=RTS_VIEW, size=300, look="game",
                                                           fit=L6all)))
    old = os.path.join(ROOT, "design", "buildings", "angel_statue_v2_r1.png")
    if os.path.exists(old):
        im = Image.open(old).convert("RGB")
        row3.append(("before: the cube statue v2 (r1)", im.crop((14, 60, 326, 372)).resize((300, 300))))
        row3.append(("before: its face", im.crop((14, 402, 326, 714)).resize((300, 300))))
    panels.append(row3)
    row4 = [("far LOD2 at 3.5 px/block (%d quads)" % counts["LOD2"],
             MP.render(pose(far, 6, A.FACING), view=RTS_VIEW, size=120, look="game")),
            ("LOD1 at 11 px/block (%d quads)" % counts["LOD1"],
             MP.render(pose(lo, 6, A.FACING), view=RTS_VIEW, size=260, look="game"))]
    panels.append(row4)

    f = font(15)
    ft = font(22)
    pad = 16
    W = max(sum(im.width for _, im in row) + pad * (len(row) + 1) for row in panels)
    H = 54 + sum(max(im.height for _, im in row) + 30 + pad for row in panels)
    sheet = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    title = a.title or "White Angel capitol - the marble angel v3 (smooth mesh sculpt)"
    d.text((pad, 14), "%s  |  LOD0 %d / LOD1 %d / LOD2 %d quads, %.0f px = 20 blocks to the wing tips" %
           (title, counts["LOD0"], counts["LOD1"], counts["LOD2"], mpx), fill=(235, 235, 240), font=ft)
    y = 54
    for row in panels:
        x = pad
        h = max(im.height for _, im in row)
        for label, im in row:
            d.text((x, y), label, fill=(230, 200, 120), font=f)
            sheet.paste(im, (x, y + 24 + (h - im.height)))
            x += im.width + pad
        y += h + 30 + pad
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    sheet.save(a.out)
    print("wrote %s %dx%d in %.0fs" % (a.out, W, H, time.time() - t0))


if __name__ == "__main__":
    main()
