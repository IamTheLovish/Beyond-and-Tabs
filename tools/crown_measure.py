#!/usr/bin/env python3
"""Crown vs lawn brightness from an eyes leaf sweep - the one named measure of the crown lighting lane (Nov 6).

    python -P tools/crown_measure.py STILLS_DIR [--label 15_wood_sampler] [--view rts] [--json OUT.json]

lovish (Nov 6): "Crowns too dark: fix lighting". An eyes structure run with input `leaf_ao` (EyesDirector's leaf sweep)
leaves, per leaf AO shade v, bat_eyes_<label>_<view>_leafao<v>.png, and one mask still bat_eyes_<label>_<view>_leafmask.png
of the same framing with every leaf block swapped for magenta concrete. The mask tells exactly which pixels are crowns -
the whole silhouette, holes and interior faces included, as a player sees it - and which are open lawn, so the numbers
do not depend on hand-drawn boxes or on where the camera landed.

Per sweep still:
  crown    mean luma over every crown pixel (the mask, one pixel eroded off its edge)
  lawn     median luma of the open lawn: the mask still's green pixels inside the crowns' bounding box (widened by
           LAWN_MARGIN), at least LAWN_GAP px from any crown or other object (their shadows and AO), median so a
           shadow or a tuft does not move it
  ratio    crown / lawn - the lane's band, fixed before any lighting change: 0.75-0.88 (crown-lighting-notes.md)
  per crown, each connected crown of at least MIN_CROWN px: its own mean luma and ratio, left to right
Luma = Rec.709 on sRGB 0..1, as tools/lawn_measure.py.
"""
import argparse
import collections
import colorsys
import glob
import json
import os
import re
import sys

import numpy as np
from PIL import Image

LUMA = np.array([0.2126, 0.7152, 0.0722])
MIN_CROWN = 150          # px: a crown smaller than this is an edge fleck, not a crown
LAWN_GAP = 12            # px kept off any crown or object (~0.4 block at zoom 30)
LAWN_MARGIN = 0.10       # the crowns' bounding box widened by this share for the lawn
BAND = (0.75, 0.88)


def load(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=np.float64) / 255.0


def magenta(rgb):
    """Magenta concrete under any light: red and blue both well above green."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    return (r > 1.35 * g + 0.02) & (b > 1.25 * g + 0.02) & (r > 0.08)


def lawn_like(rgb):
    """Lit or shaded grass: green the strongest channel, hue 55-150 degrees, some saturation."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx = rgb.max(-1)
    mn = rgb.min(-1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-9), 0.0)
    return (g >= r) & (g >= b) & (sat > 0.15) & (mx > 0.05)


def shift(m, dy, dx):
    out = np.zeros_like(m)
    h, w = m.shape
    out[max(dy, 0):h + min(dy, 0), max(dx, 0):w + min(dx, 0)] = m[max(-dy, 0):h + min(-dy, 0), max(-dx, 0):w + min(-dx, 0)]
    return out


def erode(m, n=1):
    for _ in range(n):
        m = m & shift(m, 1, 0) & shift(m, -1, 0) & shift(m, 0, 1) & shift(m, 0, -1)
    return m


def dilate(m, n):
    for _ in range(n):
        m = m | shift(m, 1, 0) | shift(m, -1, 0) | shift(m, 0, 1) | shift(m, 0, -1)
    return m


def components(m):
    """4-connected components of a boolean mask: a list of (ys, xs) arrays, largest first."""
    seen = np.zeros_like(m)
    h, w = m.shape
    out = []
    for y0, x0 in zip(*np.nonzero(m)):
        if seen[y0, x0]:
            continue
        seen[y0, x0] = True
        q = collections.deque([(y0, x0)])
        ys, xs = [], []
        while q:
            y, x = q.popleft()
            ys.append(y)
            xs.append(x)
            for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= ny < h and 0 <= nx < w and m[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    q.append((ny, nx))
        out.append((np.array(ys), np.array(xs)))
    out.sort(key=lambda c: -len(c[0]))
    return out


def masks(mask_rgb):
    """(crowns, lawn, crown components) from the mask still."""
    crown_full = magenta(mask_rgb)
    crowns = erode(crown_full, 1)
    comps = [c for c in components(crowns) if len(c[0]) >= MIN_CROWN]
    comps.sort(key=lambda c: c[1].mean())          # left to right
    if not comps:
        raise ValueError("the mask still has no magenta crown of %d px or more" % MIN_CROWN)
    ys = np.concatenate([c[0] for c in comps])
    xs = np.concatenate([c[1] for c in comps])
    h, w = crowns.shape
    my, mx = (ys.max() - ys.min()) * LAWN_MARGIN, (xs.max() - xs.min()) * LAWN_MARGIN
    box = np.zeros_like(crowns)
    box[max(0, int(ys.min() - my)):min(h, int(ys.max() + my) + 1), max(0, int(xs.min() - mx)):min(w, int(xs.max() + mx) + 1)] = True
    green = lawn_like(mask_rgb)
    # objects: what is not grass, without the specks (dark texels, tuft shadows) - an opening drops anything under 5 px
    objects = dilate(erode(~green, 2), 2)
    lawn = green & box & ~dilate(crown_full | objects, LAWN_GAP)
    return crowns, lawn, comps


def luma(rgb):
    return rgb @ LUMA


def measure(still_rgb, crowns, lawn, comps):
    L = luma(still_rgb)
    if lawn.sum() < 100:
        raise ValueError("only %d open lawn px - the framing has no clear lawn" % lawn.sum())
    lawn_l = float(np.median(L[lawn]))
    crown_l = float(L[crowns].mean())
    per = []
    for ys, xs in comps:
        c = float(L[ys, xs].mean())
        h, _, _ = colorsys.rgb_to_hsv(*still_rgb[ys, xs].mean(0))
        per.append({"x": int(xs.mean()), "y": int(ys.mean()), "px": int(len(ys)), "crown": round(c, 4),
                    "ratio": round(c / lawn_l, 4), "hue": round(h * 360.0, 1)})
    return {"crown": round(crown_l, 4), "lawn": round(lawn_l, 4), "ratio": round(crown_l / lawn_l, 4),
            "crown_px": int(crowns.sum()), "lawn_px": int(lawn.sum()), "crowns": per}


def sweep_stills(folder, label, view):
    """{shade: path} for bat_eyes_<label>_<view>_leafao<v>.png in the folder."""
    out = {}
    pat = re.compile(re.escape("bat_eyes_%s_%s_leafao" % (label, view)) + r"([0-9.]+)\.png$")
    for p in glob.glob(os.path.join(folder, "bat_eyes_%s_%s_leafao*.png" % (label, view))):
        m = pat.search(os.path.basename(p))
        if m:
            out[float(m.group(1))] = p
    return dict(sorted(out.items()))


def run(folder, label="15_wood_sampler", view="rts"):
    mask_path = os.path.join(folder, "bat_eyes_%s_%s_leafmask.png" % (label, view))
    if not os.path.isfile(mask_path):
        raise FileNotFoundError(mask_path)
    crowns, lawn, comps = masks(load(mask_path))
    stills = sweep_stills(folder, label, view)
    if not stills:
        raise FileNotFoundError("no bat_eyes_%s_%s_leafao*.png in %s" % (label, view, folder))
    return {"%.2f" % v: measure(load(p), crowns, lawn, comps) for v, p in stills.items()}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("folder")
    ap.add_argument("--label", default="15_wood_sampler")
    ap.add_argument("--view", default="rts", help="rts (zoom 30, the band's still) or rts_close")
    ap.add_argument("--json")
    a = ap.parse_args(argv)
    res = run(a.folder, a.label, a.view)
    print("crown_measure %s %s (band %.2f-%.2f)" % (a.label, a.view, BAND[0], BAND[1]))
    for v, r in res.items():
        inband = BAND[0] <= r["ratio"] <= BAND[1]
        print("  leaf AO %s: crown %.3f lawn %.3f ratio %.3f %s (crown %d px, lawn %d px)" % (
            v, r["crown"], r["lawn"], r["ratio"], "IN BAND" if inband else "out", r["crown_px"], r["lawn_px"]))
        print("    per crown: " + "  ".join("%.2f" % c["ratio"] for c in r["crowns"]))
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump(res, f, indent=1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
