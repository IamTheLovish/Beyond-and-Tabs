"""Engine Shed stills, measured where the camera sees them (r12: lovish's picks, Oct 30). Casts each eyes still's rays
into the .nbt it photographs (building_gates.shoot) and reads the real still's pixels on two regions:

- the tower roof (every cell over the cornice within the tower's oversail) in rts_close / 34 / rts_t6000 / 34_t6000:
  its AREA colour - the mean of its pixels' RGB (what the eye sees at RTS distance), as hue / saturation / value -
  the number lovish's band (copper saturation 0.16-0.30) is about;
- the camera windows' glass at dusk (every glass hit on a wall y 1..eave): the mean colour of those pixels - the
  window light's hue (lovish: "warm orange", softer) - and the share of them brighter than value 0.75 (a glowing face).

  python3 tools/engine_shed_measure.py <built.nbt> <stills dir> <still prefix, e.g. brass_t1_r11>
"""
import colorsys
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import building_gates as G  # noqa: E402
import schem_study as S  # noqa: E402

CORNICE_Y, EAVE = 15, 6
TOWER = (1, 3, 5, 7)


def in_tower(q, pad=1):
    x0, x1, z0, z1 = TOWER
    return x0 - pad <= q[0] <= x1 + pad and z0 - pad <= q[2] <= z1 + pad


def area(px):
    """(hue deg, sat, val, n) of the mean RGB of pixels (n x 3, 0..1)."""
    import numpy as np
    if not len(px):
        return None
    m = np.asarray(px).mean(0)
    h, s, v = colorsys.rgb_to_hsv(*m)
    return round(h * 360, 1), round(s, 3), round(v, 3), len(px)


def still_camera_kind(name):
    """The camera a still was shot from: rts_t6000 is the rts view, 34_t6000 the 3/4 view."""
    return name.split("_t")[0] if "_t" in name and name.split("_t")[-1].isdigit() else name


def measure(nbt, stills_dir, prefix, step=2):
    import numpy as np
    from PIL import Image
    b = S.load(nbt)
    out = {}
    for still in ("rts_close", "34", "rts_t6000", "34_t6000", "dusk", "rts"):
        path = os.path.join(stills_dir, "bat_eyes_15_%s_%s.png" % (prefix, still))
        if not os.path.exists(path):
            continue
        im = np.asarray(Image.open(path).convert("RGB")) / 255.0
        assert im.shape[:2] == (G.SCREEN[1], G.SCREEN[0]), (path, im.shape)
        hits = G.shoot(b, still_camera_kind(still), step)
        roof = [im[py, px] for (px, py), h in hits.items() if in_tower(h[0]) and h[0][1] > CORNICE_Y]
        glass = [im[py, px] for (px, py), h in hits.items() if S.glyph(h[1]) == "g" and 1 <= h[0][1] <= EAVE
                 and not in_tower(h[0], 0)]
        bright = float(np.mean([colorsys.rgb_to_hsv(*p)[2] > 0.75 for p in glass])) if glass else None
        out[still] = dict(tower_roof=area(roof), window_glass=area(glass),
                          glass_bright=None if bright is None else round(bright, 3), building_px=len(hits))
    return out


if __name__ == "__main__":
    import json
    print(json.dumps(measure(*sys.argv[1:4]), indent=1))
