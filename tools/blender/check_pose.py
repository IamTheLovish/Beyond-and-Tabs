#!/usr/bin/env python3
"""Side-by-side pose check: dsl_render.py's --check still against tools/game_preview.py's own render of the same tick.

    python tools/blender/check_pose.py --model design/models/gravebound_spider_queen.json --clip walk --tick 12
        --view quarter --blender out/spiders/queen_check_walk_t12_quarter.png --out cmp.png [--repo <checkout>]

Both are orthographic on game_preview's view axes, so after cropping each silhouette to its bounding box they must
coincide. Prints the bbox aspect of each, the silhouette IoU after scaling one onto the other, and writes
[Blender | game_preview | overlay (red = Blender only, blue = game_preview only, grey = both)].
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw


def crop(mask):
    ys, xs = np.where(mask)
    return mask[ys.min():ys.max() + 1, xs.min():xs.max() + 1], (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--clip", required=True)
    ap.add_argument("--tick", type=int, required=True)
    ap.add_argument("--view", default="quarter")
    ap.add_argument("--blender", required=True)
    ap.add_argument("--repo", default=os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                                    "..", "..")))
    ap.add_argument("--team", default="tabard,tabard_dark")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    sys.path.insert(0, os.path.join(a.repo, "tools"))
    import render_concept                   # noqa: E402
    import dsl_to_game as d2g                # noqa: E402
    import game_preview as gp                # noqa: E402
    import arachnid_anim as aa               # noqa: E402

    key = os.path.splitext(os.path.basename(a.model))[0].replace("gravebound_spider_", "")
    _b, C = aa.clips_for(key)
    rows = aa.run(C[a.clip], a.tick)
    pose = rows[-1][1].to_dict(6)
    m = render_concept.load_model(a.model)
    m["_path"] = os.path.abspath(a.model)
    _java, imgs, info = d2g.build(m, "Preview", [t for t in a.team.split(",") if t], "preview", d2g.DENSITY)
    pose_mc = {n: d2g.mc_rot(r) for n, r in m["pose"].items()}
    for bone, r in pose.items():
        if bone.startswith("_"):
            continue
        cur = pose_mc.get(bone, (0.0, 0.0, 0.0))
        add = d2g.mc_rot(r)
        pose_mc[bone] = tuple(cur[i] + add[i] for i in range(3))
    fs = gp.faces_of(info["root"], pose_mc, True, False, set(), pose.get("_move"), pose.get("_scale"))
    gim, _box, gmask, _team = gp.render(fs, imgs, d2g.DENSITY, gp.VIEWS[a.view], 520, (0.23, 0.44, 0.82),
                                        with_mask=True)

    bl = Image.open(a.blender).convert("RGBA")
    bmask = np.asarray(bl)[..., 3] > 127
    bc, bbox = crop(bmask)
    gc, gbox = crop(gmask)
    H, W = bc.shape
    gr = np.asarray(Image.fromarray(gc.astype(np.uint8) * 255).resize((W, H), Image.NEAREST)) > 127
    inter, union = (bc & gr).sum(), (bc | gr).sum()
    iou = inter / float(union)
    print("blender bbox %dx%d aspect %.4f | game_preview bbox %dx%d aspect %.4f | silhouette IoU %.4f" % (
        W, H, W / float(H), gc.shape[1], gc.shape[0], gc.shape[1] / float(gc.shape[0]), iou))

    bcol = bl.crop(bbox)
    flat = Image.new("RGB", bcol.size, (214, 212, 208))
    flat.paste(bcol, (0, 0), bcol)
    gcol = gim.crop(gbox).resize((W, H), Image.NEAREST)
    ov = np.full((H, W, 3), 245, dtype=np.uint8)
    ov[bc & gr] = (120, 120, 120)
    ov[bc & ~gr] = (220, 40, 40)
    ov[~bc & gr] = (40, 90, 220)
    pad = 10
    S = Image.new("RGB", (3 * W + 4 * pad, H + 40), (245, 243, 239))
    for k, im in enumerate((flat, gcol, Image.fromarray(ov))):
        S.paste(im, (pad + k * (W + pad), pad))
    d = ImageDraw.Draw(S)
    d.text((pad, H + 16), "Blender (dsl_render --check)", fill=(30, 30, 30))
    d.text((2 * pad + W, H + 16), "game_preview.py", fill=(30, 30, 30))
    d.text((3 * pad + 2 * W, H + 16), "overlay IoU %.3f  %s t%d %s" % (iou, a.clip, a.tick, a.view), fill=(30, 30, 30))
    S.save(a.out)
    print(a.out)


if __name__ == "__main__":
    main()
