#!/usr/bin/env python3
"""Offline proof of the branch wind (design/branch_wind.md): frames of one tree template with the wind transform of
environment/wind/WindField applied to its sway blocks, drawn with tools/preview_structure.py's isometric painter.
The eyes clips hold each shot still at ~1 fps, so the motion cannot be judged there; this shows it on paper.

    python3 tools/sway_proof.py design/trees/oak_1.nbt -o /tmp/wind [--frames 8] [--cell 24] [--gain 1]
                                                                   [--wind 1.0] [--tiers]

Writes <name>_tiers.png (the wood alone: trunk grey, limbs coloured by sway tier 1-4), <name>_f00..07.png (natural colours, the
offsets at true scale x gain) and <name>_strip.png (the frames side by side). --gain 4 exaggerates the motion four
times so a 0.1-block sway reads at this size; the title of every frame says the gain. The field is a port of
WindField.java (same constants) so the numbers are the game's: a mismatch between the two is a bug in one of them.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw                                      # noqa: E402
from preview_structure import FULL, colour_for, invisible, shape_for, shade   # noqa: E402
from sway_trees import classify, read_blocks                        # noqa: E402

# ---- WindField.java, ported --------------------------------------------------------------------------------------
BASE_AMP, MAX_WIND_MUL, MAX_TIER = 0.0195, 1.7, 4
BASE_HEADING, TURN, TURN_PERIOD = 0.6, 0.35, 140.0
L1, V1, L2, V2, FLUTTER_HZ = 41.0, 6.5, 17.0, 10.0, 1.6
TWO_PI = math.pi * 2
M64 = (1 << 64) - 1


def heading(seconds):
    return BASE_HEADING + TURN * math.sin(TWO_PI * seconds / TURN_PERIOD)


def gust(x, z, seconds, h):
    d = x * math.cos(h) + z * math.sin(h)
    swell = math.sin(TWO_PI * (d / L1 - seconds * V1 / L1))
    ripple = math.sin(TWO_PI * (d / L2 - seconds * V2 / L2) + 1.7)
    return 0.3 + 0.7 * (0.6 * swell + 0.4 * ripple)


def phase(x, y, z):
    h = ((x * 0x9E3779B97F4A7C15) ^ (y * 0xC2B2AE3D27D4EB4F) ^ (z * 0x165667B19E3779F9)) & M64
    h ^= h >> 29
    h = (h * 0xBF58476D1CE4E5B9) & M64
    h ^= h >> 32
    return (h & 0xFFFF) / 65536.0 * TWO_PI


def offset(x, y, z, tier, seconds, wind_mul=1.0, fade=1.0):
    if tier <= 0 or fade <= 0:
        return 0.0, 0.0, 0.0
    amp = BASE_AMP * tier * min(max(wind_mul, 0), MAX_WIND_MUL) * min(fade, 1)
    hd = heading(seconds)
    g = gust(x + 0.5, z + 0.5, seconds, hd)
    flutter = 0.12 * math.sin(TWO_PI * FLUTTER_HZ * seconds + phase(x, y, z))
    h = amp * (g + flutter)
    return math.cos(hd) * h, -0.2 * abs(h), math.sin(hd) * h


# ---- drawing -------------------------------------------------------------------------------------------------------
TIER_COLOURS = {0: (120, 120, 120), 1: (70, 110, 200), 2: (70, 180, 160), 3: (230, 190, 60), 4: (230, 80, 60)}


def draw(blocks, size, tiers, cell, seconds, gain, wind_mul, title, by_tier=False, origin=(0, 0, 0)):
    sx, sy, sz = size
    voxels = []
    for (x, y, z), (name, props) in blocks.items():
        if invisible(name) or name.endswith("structure_void"):
            continue
        t = tiers.get((x, y, z), 0)
        c = TIER_COLOURS[t] if by_tier else colour_for(name.split(":")[-1].replace("sway_", ""))
        if by_tier and (x, y, z) not in tiers:
            continue                                                     # the tier map shows the wood alone
        dx, dy, dz = offset(x + origin[0], y + origin[1], z + origin[2], t, seconds, wind_mul) if t else (0, 0, 0)
        voxels.append((x + dx * gain, y + dy * gain, z + dz * gain, c, shape_for(name.split(":")[-1].replace("sway_", ""), props), (x, y, z)))
    w = int((sx + sz) * cell * 0.87) + cell * 4
    h = int((sx + sz) * cell * 0.5 + sy * cell) + cell * 7
    img = Image.new("RGB", (w, h), (34, 38, 44))
    d = ImageDraw.Draw(img)
    ox, oy = w // 2, int(sy * cell) + cell * 4

    def P(X, Y, Z):
        return (ox + (X - Z) * cell * 0.87, oy + (X + Z - 1) * cell * 0.5 - (Y - 1) * cell)

    full = {v[5] for v in voxels if v[4] is FULL}
    boxes = []
    for x, y, z, c, shape, cellpos in voxels:
        for (x0, x1, y0, y1, z0, z1) in shape:
            boxes.append((x + x0, x + x1, y + y0, y + y1, z + z0, z + z1, c, cellpos, shape is FULL))
    boxes.sort(key=lambda bx: (bx[0] + bx[1] + bx[4] + bx[5], bx[2] + bx[3]))
    for (X0, X1, Y0, Y1, Z0, Z1, c, (x, y, z), isfull) in boxes:
        if not (isfull and (x, y + 1, z) in full):
            d.polygon([P(X0, Y1, Z0), P(X1, Y1, Z0), P(X1, Y1, Z1), P(X0, Y1, Z1)], fill=shade(c, 1.1))
        if not (isfull and (x, y, z + 1) in full):
            d.polygon([P(X0, Y1, Z1), P(X1, Y1, Z1), P(X1, Y0, Z1), P(X0, Y0, Z1)], fill=shade(c, 0.78))
        if not (isfull and (x + 1, y, z) in full):
            d.polygon([P(X1, Y1, Z0), P(X1, Y1, Z1), P(X1, Y0, Z1), P(X1, Y0, Z0)], fill=shade(c, 0.62))
    d.text((6, 4), title, fill=(230, 230, 230))
    return img


def main(argv):
    out, frames, cell, gain, wind_mul, files = ".", 8, 24, 1.0, 1.0, []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "-o":
            out = argv[i + 1]; i += 2
        elif a == "--frames":
            frames = int(argv[i + 1]); i += 2
        elif a == "--cell":
            cell = int(argv[i + 1]); i += 2
        elif a == "--gain":
            gain = float(argv[i + 1]); i += 2
        elif a == "--wind":
            wind_mul = float(argv[i + 1]); i += 2
        else:
            files.append(a); i += 1
    if not files:
        print(__doc__)
        return 2
    os.makedirs(out, exist_ok=True)
    for f in files:
        name = os.path.basename(f).replace(".nbt", "")
        blocks, size = read_blocks(f)
        tiers = classify(blocks)
        n = sum(1 for t in tiers.values() if t)
        draw(blocks, size, tiers, cell, 0, 0, wind_mul, "%s  sway tiers: grey trunk, 1 blue 2 teal 3 yellow 4 red  (%d sway blocks)" % (name, n),
             by_tier=True).save(os.path.join(out, "%s_tiers.png" % name))
        imgs = []
        peak = 0.0
        for k in range(frames):
            seconds = 100.0 + k * 0.25                         # 5 ticks apart: two seconds of wind over 8 frames
            for (x, y, z), t in tiers.items():
                if t:
                    dx, dy, dz = offset(x, y, z, t, seconds, wind_mul)
                    peak = max(peak, math.sqrt(dx * dx + dy * dy + dz * dz))
            img = draw(blocks, size, tiers, cell, seconds, gain, wind_mul,
                       "%s  t=%.2fs  wind x%.1f  motion x%g" % (name, seconds, wind_mul, gain))
            img.save(os.path.join(out, "%s_f%02d.png" % (name, k)))
            imgs.append(img)
        strip = Image.new("RGB", (sum(im.width for im in imgs) + 4 * (len(imgs) + 1), imgs[0].height + 8), (20, 22, 26))
        x = 4
        for im in imgs:
            strip.paste(im, (x, 4)); x += im.width + 4
        strip.save(os.path.join(out, "%s_strip.png" % name))
        print("%s: %d sway blocks, peak offset %.3f block over the frames (wind x%.1f), written to %s" % (name, n, peak, wind_mul, out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
