#!/usr/bin/env python3
"""Picture regression for the eyes tour (R6, Oct 15 brainstorm): compare this run's stills with the last APPROVED set
and list what changed, with a side-by-side sheet - so a vanished unit, a building turned black or a moved HUD panel
is caught by a machine, not by lovish.

A battle never replays exactly (units move, fights differ), so pixel equality means nothing. Each still is reduced to
a small grey image and cut into a grid of cells; a cell "changed" when its mean brightness or its edge energy moved a
lot. A still is flagged when changed cells cover more than a share of the frame, or when its overall brightness
shifted (a black frame, a missing world). Missing and new stills are listed too.

    python3 tools/eyes_diff.py APPROVED_DIR NEW_DIR OUT_DIR [--threshold 0.25]
Writes OUT_DIR/eyes-diff.md (a table) and OUT_DIR/eyes-diff.jpg (approved | new | changed cells, flagged stills only).
Exit code 0 always: this reports, it does not fail the run (art changes on purpose all the time).
"""
import argparse
import os

import numpy as np
from PIL import Image, ImageDraw

W, H = 160, 90          # analysis size (16:9 like the tour's 1600x900)
GX, GY = 16, 9          # cell grid: 10 x 10 px cells


def grey(path):
    im = Image.open(path).convert("L").resize((W, H), Image.BILINEAR)
    return np.asarray(im, dtype=np.float64) / 255.0


def edges(a):
    gx = np.abs(np.diff(a, axis=1, prepend=a[:, :1]))
    gy = np.abs(np.diff(a, axis=0, prepend=a[:1, :]))
    return gx + gy


def cells(a):
    return a.reshape(GY, H // GY, GX, W // GX).mean(axis=(1, 3))


def compare(old, new):
    co, cn = cells(old), cells(new)
    eo, en = cells(edges(old)), cells(edges(new))
    d_bright = np.abs(co - cn)                                  # a cell got darker / brighter
    d_edge = np.abs(eo - en) / np.maximum(np.maximum(eo, en), 0.02)   # its detail appeared / vanished
    changed = (d_bright > 0.12) | (d_edge > 0.6)
    # a SOLID region (a 3x3 block of cells, ~1/5 of the frame's height, all changed) - a hole, a black building, a moved
    # panel - unlike scattered units moving, which leave isolated cells
    solid = changed[:-2, :-2].copy()
    for dy in range(3):
        for dx in range(3):
            solid &= changed[dy:dy + GY - 2, dx:dx + GX - 2]
    return {
        "changed_share": float(changed.mean()),
        "solid": bool(solid.any()),
        "brightness_shift": float(cn.mean() - co.mean()),
        "mask": changed,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("approved")
    ap.add_argument("new")
    ap.add_argument("out")
    ap.add_argument("--threshold", type=float, default=0.25, help="share of changed cells that flags a still")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    def stills(d):
        return {f for f in os.listdir(d) if f.endswith(".png") and f.startswith("bat_eyes_")} if os.path.isdir(d) else set()
    old, new = stills(a.approved), stills(a.new)
    rows, flagged = [], []
    for f in sorted(old & new):
        r = compare(grey(os.path.join(a.approved, f)), grey(os.path.join(a.new, f)))
        flag = r["changed_share"] > a.threshold or abs(r["brightness_shift"]) > 0.10 or r["solid"]
        rows.append((f, r, flag))
        if flag:
            flagged.append((f, r))
    missing, added = sorted(old - new), sorted(new - old)

    lines = ["# Eyes picture regression (tools/eyes_diff.py)", "",
             "Compared with the last approved stills. Flagged = more than %d%% of the frame changed, a solid region "
             "changed, or the overall brightness moved more than 0.10 - look at eyes-diff.jpg (approved | new | changed "
             "cells)." % (a.threshold * 100),
             "", "- compared: %d, flagged: %d, missing: %d, new: %d" % (len(rows), len(flagged), len(missing), len(added)), ""]
    if missing:
        lines += ["**Missing (approved, not in this run):** " + ", ".join(missing), ""]
    if added:
        lines += ["**New (not in the approved set):** " + ", ".join(added), ""]
    lines += ["| still | changed | solid region | brightness | flag |", "|---|---|---|---|---|"]
    for f, r, flag in rows:
        lines.append("| %s | %.0f%% | %s | %+.2f | %s |" % (f[9:-4], 100 * r["changed_share"], "yes" if r["solid"] else "",
                                                           r["brightness_shift"], "**yes**" if flag else ""))
    open(os.path.join(a.out, "eyes-diff.md"), "w").write("\n".join(lines) + "\n")

    if flagged:
        tw, th = 480, 270
        sheet = Image.new("RGB", (tw * 3, th * len(flagged) + 24 * len(flagged)), (16, 16, 16))
        d = ImageDraw.Draw(sheet)
        for i, (f, r) in enumerate(flagged):
            y = i * (th + 24)
            d.text((6, y + 4), "%s - %.0f%% changed, brightness %+.2f" % (f[9:-4], 100 * r["changed_share"],
                                                                       r["brightness_shift"]), fill=(230, 230, 230))
            o = Image.open(os.path.join(a.approved, f)).convert("RGB").resize((tw, th))
            n = Image.open(os.path.join(a.new, f)).convert("RGB").resize((tw, th))
            heat = n.copy()
            hd = ImageDraw.Draw(heat, "RGBA")
            cw, ch = tw / GX, th / GY
            for gy in range(GY):
                for gx in range(GX):
                    if r["mask"][gy, gx]:
                        hd.rectangle([gx * cw, gy * ch, (gx + 1) * cw - 1, (gy + 1) * ch - 1], fill=(255, 40, 40, 90))
            for k, img in enumerate((o, n, heat)):
                sheet.paste(img, (k * tw, y + 24))
        sheet.save(os.path.join(a.out, "eyes-diff.jpg"), quality=80)
    print("eyes-diff: %d compared, %d flagged, %d missing, %d new" % (len(rows), len(flagged), len(missing), len(added)))


if __name__ == "__main__":
    main()
