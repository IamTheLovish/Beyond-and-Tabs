"""Measure eyes stills for the shader pack's slices (design/own_shader_pack.md, "How it is judged"): numbers, not eyeballs.

Each lane's stills are measured on the same crops with the same masks, so lanes (plain, the old pack, ours) compare
like for like:

  lawn       grass pixels (hue 60-150 degrees, saturation >= 0.25): median hue, median value (V), and blue / green
             of their mean - the olive-cast check (the old pack's stills lost a third of their blue)
  shade      lawn luma p10 / p75: grass in a building's shadow against grass in the sun (visible, never black)
  white      near-white pixels (saturation < 0.15, V >= 0.55): luma p95 - the capitol's walls, white but unclipped
  clipped    share of pixels with any channel at 254 or more
  detail     mean absolute difference from a 9-pixel box blur, on luma: texture and edge contrast
  luma       p5 / p50 / p95 over the crop

    python3 tools/shader_judge.py --lane plain=DIR --lane ours=DIR [--lane old=DIR] [--stills a.png,b.png] [--hud]
    python3 tools/shader_judge.py --fetch eyes-keep-123 DIR     # a lane's stills from its eyes branch (gh api)

Stills named in --stills (default: every bat_eyes_*.png in the first lane's folder) are looked up in each lane's
folder by name. --hud crops the tour's HUD away (top 10%, bottom 28%); structure photos hide the HUD and use the
whole frame minus a 5% border. Prints a Markdown table.
"""

import argparse
import base64
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageFilter

LUMA = np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)


def crop(img, hud):
    w, h = img.size
    box = (int(w * 0.1), int(h * 0.1), int(w * 0.9), int(h * 0.72)) if hud else \
        (int(w * 0.05), int(h * 0.05), int(w * 0.95), int(h * 0.95))
    return img.crop(box)


def hsv(rgb):
    """Hue in degrees, saturation and value of an (..., 3) float array in 0..1."""
    mx, mn = rgb.max(-1), rgb.min(-1)
    d = mx - mn
    s = np.where(mx > 0, d / np.maximum(mx, 1e-6), 0.0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    safe = np.maximum(d, 1e-6)
    h = np.where(mx == r, ((g - b) / safe) % 6, np.where(mx == g, (b - r) / safe + 2, (r - g) / safe + 4)) * 60.0
    return np.where(d > 0, h, 0.0), s, mx


def measure(path, hud=False):
    """The numbers of one still, as a dict (None where a mask is empty)."""
    img = crop(Image.open(path).convert("RGB"), hud)
    c = np.asarray(img).astype(np.float32) / 255.0
    luma = c @ LUMA
    h, s, v = hsv(c)
    out = {"luma": tuple(float(np.percentile(luma, q)) for q in (5, 50, 95))}
    grass = (h >= 60) & (h <= 150) & (s >= 0.25) & (v >= 0.08)
    out["lawn_share"] = float(grass.mean())
    if grass.sum() >= 200:
        gl = luma[grass]
        mean = c[grass].mean(0)
        out["lawn_hue"] = float(np.median(h[grass]))
        out["lawn_v"] = float(np.median(v[grass]))
        out["lawn_bg"] = float(mean[2] / max(mean[1], 1e-6))
        out["shade"] = float(np.percentile(gl, 10) / max(np.percentile(gl, 75), 1e-6))
    else:
        out["lawn_hue"] = out["lawn_v"] = out["lawn_bg"] = out["shade"] = None
    white = (s < 0.15) & (v >= 0.55)
    out["white_p95"] = float(np.percentile(luma[white], 95)) if white.sum() >= 200 else None
    out["clipped"] = float((c.max(-1) >= 254 / 255).mean())
    blur = np.asarray(Image.fromarray((luma * 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(4))) / 255.0
    out["detail"] = float(np.abs(luma - blur).mean())
    return out


def fmt(x, digits=3):
    return "-" if x is None else ("%." + str(digits) + "f") % x


def table(lanes, stills, hud):
    rows = ["| still | lane | luma p5 / p50 / p95 | lawn hue | lawn V | lawn B/G | shade | white p95 | clipped | detail |",
            "|---|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for still in stills:
        for name, folder in lanes:
            p = os.path.join(folder, still)
            if not os.path.isfile(p):
                rows.append("| %s | %s | (missing) |||||||" % (still, name))
                continue
            m = measure(p, hud)
            rows.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
                still.replace("bat_eyes_", "").replace(".png", ""), name, " / ".join(fmt(x, 2) for x in m["luma"]),
                fmt(m["lawn_hue"], 0), fmt(m["lawn_v"], 2), fmt(m["lawn_bg"], 2), fmt(m["shade"], 2),
                fmt(m["white_p95"], 2), "%.2f%%" % (100 * m["clipped"]), fmt(m["detail"], 4)))
    return "\n".join(rows)


def fetch(branch, folder, repo="IamTheLovish/Beyond-and-Tabs"):
    """Every bat_eyes_*.png of an eyes branch into folder (top level and one folder down), through the REST API."""
    os.makedirs(folder, exist_ok=True)
    listing = json.loads(subprocess.check_output(["gh", "api", "repos/%s/contents/?ref=%s" % (repo, branch)]))
    got = 0
    for e in listing:
        if e["type"] == "file" and e["name"].startswith("bat_eyes_") and e["name"].endswith(".png"):
            blob = json.loads(subprocess.check_output(["gh", "api", "repos/%s/git/blobs/%s" % (repo, e["sha"])]))
            with open(os.path.join(folder, e["name"]), "wb") as f:
                f.write(base64.b64decode(blob["content"]))
            got += 1
    return got


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--lane", action="append", default=[], help="NAME=DIR, in table order")
    ap.add_argument("--stills", default="", help="comma-separated file names (default: all in the first lane)")
    ap.add_argument("--hud", action="store_true", help="tour stills: crop the HUD away")
    ap.add_argument("--fetch", nargs=2, metavar=("BRANCH", "DIR"))
    args = ap.parse_args()
    if args.fetch:
        print("%d stills" % fetch(*args.fetch))
        return 0
    lanes = [tuple(x.split("=", 1)) for x in args.lane]
    if not lanes:
        ap.error("give at least one --lane NAME=DIR")
    stills = [x for x in args.stills.split(",") if x] or sorted(
        n for n in os.listdir(lanes[0][1]) if n.startswith("bat_eyes_") and n.endswith(".png"))
    print(table(lanes, stills, args.hud))
    return 0


if __name__ == "__main__":
    sys.exit(main())
