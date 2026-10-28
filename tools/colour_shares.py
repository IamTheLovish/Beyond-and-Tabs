#!/usr/bin/env python3
"""Colour on PIXELS, on a named crop: the share of each colour class (red / dark / grey / pale; sky, grass and other
hues are left out) and the hue, saturation and luma medians.

Why (Oct 28): the Engine Shed pilot's critics argued colour from taste ("less grey", "grey-cream walls", "red-dominant")
and builds swung between them (design/vehicle_labs/LESSONS.md). The rule since: every colour claim cites a share or a
median measured on a named crop of the render or the in-game still, before anyone acts on it. The classes are the ones
the T1 checklist's targets were measured with (design/vehicle_labs/briefs/brass_t1_checklist.md: ref18 whole front
red 33 / dark 35 / grey 16 / pale 16).

    python3 tools/colour_shares.py IMAGE [X0,Y0,X1,Y1]      (crop in pixels; default the whole image)
"""
import colorsys
import sys

import numpy as np
from PIL import Image

CLASSES = ("red", "dark", "grey", "pale")


def _hsl(rgb):
    a = np.asarray(rgb, dtype=float).reshape(-1, 3) / 255.0
    luma = a @ [0.299, 0.587, 0.114]
    hsv = np.array([colorsys.rgb_to_hsv(*p) for p in a]) if len(a) else np.zeros((0, 3))
    return hsv[:, 0] * 360, hsv[:, 1], luma


def classes(rgb):
    """Boolean masks per class. Red: a red hue, saturated, not near-black (maroon reads dark from the camera)."""
    h, s, luma = _hsl(rgb)
    red = ((h < 25) | (h > 340)) & (s > 0.35) & (luma > 0.12)
    dark = (luma < 0.22) & ~red
    pale = (luma > 0.62) & (s < 0.3)
    grey = ~red & ~dark & ~pale & (s < 0.25)
    return {"red": red, "dark": dark, "grey": grey, "pale": pale}


def _crop(image, box):
    im = Image.open(image) if isinstance(image, str) else image
    im = im.convert("RGB")
    return np.asarray(im.crop(box) if box else im)


def shares(image, box=None):
    """% of the classed pixels in each class (integers; sky, grass and other hues excluded)."""
    c = classes(_crop(image, box))
    n = sum(int(v.sum()) for v in c.values())
    return {k: (round(100 * int(c[k].sum()) / n) if n else 0) for k in CLASSES}


def medians(image, box=None):
    """Hue (degrees, circular around red), saturation (0..1) and luma (0..255) medians of every pixel in the crop."""
    h, s, luma = _hsl(_crop(image, box))
    hue = np.where(h > 180, h - 360, h)            # red sits at 0 rather than splitting into 0 and 360
    return {"hue": float(np.median(hue)) % 360, "sat": float(np.median(s)), "luma": float(np.median(luma)) * 255}


def report(image, box=None):
    sh, m = shares(image, box), medians(image, box)
    return "%s %s: %s | hue %.0f sat %.2f luma %.0f" % (
        image, box or "whole", " ".join("%s %d" % (k, sh[k]) for k in CLASSES), m["hue"], m["sat"], m["luma"])


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    print(report(sys.argv[1], tuple(map(int, sys.argv[2].split(","))) if len(sys.argv) == 3 else None))
