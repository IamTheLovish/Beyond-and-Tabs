"""Measure eyes stills for the shader pack's slices (design/own_shader_pack.md, "How it is judged"): numbers, not eyeballs.

Each lane's stills are measured on the same crops with the same masks, so lanes (plain, the old pack, ours) compare
like for like:

  lawn       grass pixels (hue 60-150 degrees, saturation >= 0.25): median hue, median value (V), and blue / green
             of their mean - the olive-cast check (the old pack's stills lost a third of their blue)
  shade      lawn luma p10 / p75: grass in a building's shadow against grass in the sun (visible, never black)
  fill       the lawn's luma split in two (Otsu): mean of the darker class / mean of the lighter - grass in shadow against
             grass in the sun - and the darker class's share of the lawn; "floor" is the lawn's luma p1 / the lighter
             mean, the darkest shadow anywhere. Both are judged against two-sided bands per time of day (BANDS); a
             value outside its band is marked with "!" (too dark kills the read of units in shade, too light means no
             shadow at all)
  night      a night still's luma p50 against NIGHT_LUMA (two-sided): an absolute gate, since a ratio cannot tell a lit
             night from a noon
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

# (fill low, fill high, floor low, floor high) per still suffix: shadowed / sunlit grass on screen. Noon and morning
# keep a clear shadow with a strong fill (Erin, #386 r5: shadowed light ~35-45% of lit, i.e. ~0.62-0.70 on screen). The
# day fill's top edge is the design's 0.70 (design/own_shader_pack.md: shadow / lit 0.45-0.70); its low edge is r5's,
# tighter than the design's 0.45. Dusk and night may have a deeper shadow (fill from 0.45), but the darkest shadow
# (floor) keeps the same 0.35 at every time: units in shade must read at night too, and the old pack's floor is ~0.42.
# How dark the night is as a whole is NIGHT_LUMA's job, not the shadow ratio's
BANDS = {"day": (0.55, 0.70, 0.35, 0.75), "dusk": (0.45, 0.75, 0.35, 0.75), "night": (0.45, 0.80, 0.35, 0.80)}
# luma p50 of a night still: lit enough to play (lanterns, moon), dark enough to read as night (day stills sit ~0.37)
NIGHT_LUMA = (0.10, 0.30)


def band_for(still):
    if "_dusk" in still or "_t11800" in still:
        return "dusk"
    if "_t18000" in still:
        return "night"
    return "day"


def otsu_split(x):
    """The threshold that best splits x in two classes (Otsu, 256 bins)."""
    hist, edges = np.histogram(x, bins=256, range=(0.0, 1.0))
    p = hist.astype(np.float64) / max(hist.sum(), 1)
    mids = (edges[:-1] + edges[1:]) * 0.5
    w0 = np.cumsum(p)
    m0 = np.cumsum(p * mids)
    total = m0[-1]
    w1 = 1.0 - w0
    between = np.where((w0 > 0) & (w1 > 0), (total * w0 - m0) ** 2 / np.maximum(w0 * w1, 1e-12), 0.0)
    return float(edges[int(np.argmax(between)) + 1])


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
    grass = (h >= 60) & (h <= 150) & (s >= 0.25) & (v >= 0.02)   # dark enough to keep grass in deep shadow
    out["lawn_share"] = float(grass.mean())
    if grass.sum() >= 200:
        gl = luma[grass]
        mean = c[grass].mean(0)
        out["lawn_hue"] = float(np.median(h[grass]))
        out["lawn_v"] = float(np.median(v[grass]))
        out["lawn_bg"] = float(mean[2] / max(mean[1], 1e-6))
        out["shade"] = float(np.percentile(gl, 10) / max(np.percentile(gl, 75), 1e-6))
        t = otsu_split(gl)
        dark, lit = gl[gl < t], gl[gl >= t]
        out["shadow_share"] = float(dark.size / gl.size)
        if dark.size >= 100 and lit.size >= 100:
            out["fill"] = float(dark.mean() / max(lit.mean(), 1e-6))
            out["floor"] = float(np.percentile(gl, 1) / max(lit.mean(), 1e-6))
        else:
            out["fill"] = out["floor"] = None
    else:
        out["lawn_hue"] = out["lawn_v"] = out["lawn_bg"] = out["shade"] = out["fill"] = out["floor"] = None
        out["shadow_share"] = None
    white = (s < 0.15) & (v >= 0.55)
    out["white_p95"] = float(np.percentile(luma[white], 95)) if white.sum() >= 200 else None
    out["clipped"] = float((c.max(-1) >= 254 / 255).mean())
    blur = np.asarray(Image.fromarray((luma * 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(4))) / 255.0
    out["detail"] = float(np.abs(luma - blur).mean())
    return out


def fmt(x, digits=3):
    return "-" if x is None else ("%." + str(digits) + "f") % x


def fmt_band(x, lo, hi):
    """x to two places, marked "!" outside [lo, hi]."""
    return "-" if x is None else "%.2f%s" % (x, "" if lo <= x <= hi else "!")


def table(lanes, stills, hud):
    rows = ["| still | lane | luma p5 / p50 / p95 | lawn hue | lawn V | lawn B/G | shade | fill (share) | floor | white p95 "
            "| clipped | detail |",
            "|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for still in stills:
        for name, folder in lanes:
            p = os.path.join(folder, still)
            if not os.path.isfile(p):
                rows.append("| %s | %s | (missing) |||||||" % (still, name))
                continue
            m = measure(p, hud)
            band = band_for(still)
            lo, hi, flo, fhi = BANDS[band]
            p5, p50, p95 = m["luma"]
            luma = " / ".join((fmt(p5, 2), fmt_band(p50, *NIGHT_LUMA) if band == "night" else fmt(p50, 2), fmt(p95, 2)))
            rows.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
                still.replace("bat_eyes_", "").replace(".png", ""), name, luma,
                fmt(m["lawn_hue"], 0), fmt(m["lawn_v"], 2), fmt(m["lawn_bg"], 2), fmt(m["shade"], 2),
                fmt_band(m["fill"], lo, hi) + ("" if m["shadow_share"] is None else " (%d%%)" % round(100 * m["shadow_share"])),
                fmt_band(m["floor"], flo, fhi), fmt(m["white_p95"], 2), "%.2f%%" % (100 * m["clipped"]),
                fmt(m["detail"], 4)))
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
