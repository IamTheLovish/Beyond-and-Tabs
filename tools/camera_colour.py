"""What colour each block IS in the eyes stills: per (block, face seen) the median hue / saturation / value of the real
still pixels its rays hit (tools/building_gates.shoot casts the stills' rays into the .nbt the stills were shot of).
The table (design/vehicle_labs/camera_colours.json) lets a test ask what colour a region reads as on camera without
a game run - the r10 copper was green by block id and MINT in the pictures (sat ~0.43, lovish's palette: weathered).

Measure (appends / overwrites the blocks it saw; keeps the rest):
  python3 tools/camera_colour.py <built.nbt> <stills dir> <still prefix, e.g. brass_t1> [--source "<run id / sha>"]
Stills: bat_eyes_15_<prefix>_{rts,rts_close,34}.png (plain lane, Subtle look), downloaded from eyes-structures*.
"""
import colorsys
import json
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import schem_study as S  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE = os.path.join(ROOT, "design", "vehicle_labs", "camera_colours.json")
FACES = ("up", "west", "south")      # the sides the structure stills see
MIN_PIXELS = 40


def load():
    with open(TABLE) as f:
        return json.load(f)


def lookup(table, name, face):
    """(hue deg, sat, val) of a block seen from `face`, else from any measured face, else None."""
    e = table["blocks"].get(name)
    if not e:
        return None
    v = e.get(face) or next(iter(e.values()))
    return v["hue"], v["sat"], v["val"]


def measure(nbt, stills_dir, prefix, stills=("rts", "rts_close", "34")):
    import numpy as np
    from PIL import Image
    b = S.load(nbt)
    acc = defaultdict(list)
    for st in stills:
        im = np.asarray(Image.open(os.path.join(stills_dir, "bat_eyes_15_%s_%s.png" % (prefix, st))).convert("RGB"))
        im = im / 255.0
        for (px, py), h in G.shoot(b, st, 2).items():
            if h[2] in FACES:
                acc[(h[1], h[2])].append(colorsys.rgb_to_hsv(*im[py, px]))
    out = defaultdict(dict)
    for (name, face), px in acc.items():
        if len(px) < MIN_PIXELS:
            continue
        a = np.array(px)
        # circular (red stays at 0): the median of the unit vectors' cos and sin, each on its own - np.median of complex
        # numbers sorts them by real part and picks one pixel's hue (r12: weathered copper stairs measured 216, their
        # pixels' median is 151)
        w = 2 * np.pi * a[:, 0]
        hue = np.degrees(np.arctan2(np.median(np.sin(w)), np.median(np.cos(w))))
        out[name][face] = dict(hue=round(float(hue) % 360, 1), sat=round(float(np.median(a[:, 1])), 3),
                               val=round(float(np.median(a[:, 2])), 3), n=len(a))
    return out


def main(argv):
    nbt, stills_dir, prefix = argv[:3]
    source = argv[argv.index("--source") + 1] if "--source" in argv else prefix
    table = load() if os.path.exists(TABLE) else {"about": __doc__.split("\n")[0], "sources": [], "blocks": {}}
    got = measure(nbt, stills_dir, prefix)
    for name, faces in got.items():
        table["blocks"].setdefault(name, {}).update(faces)
    if source not in table["sources"]:
        table["sources"].append(source)
    table["blocks"] = dict(sorted(table["blocks"].items()))
    with open(TABLE, "w") as f:
        json.dump(table, f, indent=1, sort_keys=False)
        f.write("\n")
    print("%d blocks measured, %d in the table" % (len(got), len(table["blocks"])))


if __name__ == "__main__":
    main(sys.argv[1:])
