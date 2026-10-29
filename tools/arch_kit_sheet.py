#!/usr/bin/env python3
"""The ARCHITECTURE KIT SHEET: every tools/arch_kit.py part stamped on one pad for the Eyes structure photos -
Brassworks rows (roofs and towers at the back, openings and walls in front), one row of the same parts in the
Sunforged set, and the demo bay (plinth, pilasters, windows, string course, cornice, eave over a slate roof) to show
the parts compose. Wall parts stand in a short wall of their set (so reveals, sills and lips read against a wall);
roof parts sit on a plain wall box. Fronts face south, toward the camera.

    python3 tools/arch_kit_sheet.py [OUT]      -> design/arch_kit/kit_sheet.nbt (+ design/arch_kit/demo_bay.nbt)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import arch_kit as K  # noqa: E402
import gen_structures as gs  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "design", "arch_kit", "kit_sheet.nbt")
DEMO = os.path.join(ROOT, "design", "arch_kit", "demo_bay.nbt")   # alone: small enough for the 3/4 and dusk stills
GAP = 3
PAD = "stone_bricks"
WALLED = {"eave", "cornice", "string_course", "pier", "window_tall", "window_medium", "window_small", "bay_tall",
          "machinery"}


def stamp(host, part, ox, oy, oz):
    """Copies part in INCLUDING its air cells (a reveal carves the wall it stands in)."""
    for (x, y, z), v in part.b.items():
        host.b[(x + ox, y + oy, z + oz)] = v


def walled(m, part):
    """The part standing in a short wall of its set: the wall fills z -2..0 under and round the part's bbox."""
    x0, x1, y0, y1, z0, z1 = part.bbox()
    p = K.blank()
    for x in range(x0 - 1, x1 + 2):
        for y in range(min(0, y0), y1 + 1):
            for z in (-2, -1, 0):
                p.set(x, y, z, m.wall.full)
    stamp(p, part, 0, 0, 0)
    return p


def mounted(m, roof, w, d, h=4):
    """A roof part on a plain wall box x 0..w-1, z 0..d-1, walls y 0..h-1 (pale base course), the roof's y 0 at y h."""
    p = K.blank()
    for x in range(w):
        for z in range(d):
            if x in (0, w - 1) or z in (0, d - 1):
                for y in range(h):
                    p.set(x, y, z, m.trim.full if y == 0 else m.wall.full)
    for (x, y, z), v in roof.b.items():
        if v[0] not in ("air", "cave_air"):
            p.b[(x, y + h, z)] = v
    return p


def piece(name, m):
    if name == "oversail":
        return mounted(m, K.oversail(m, 7, 9, drop=4), 9, 7)
    if name == "cresting":
        return mounted(m, K.cresting(K.oversail(m, 7, 9), m), 9, 7)
    if name == "hip_roof":
        return mounted(m, K.hip_roof(m, 7), 7, 7, h=6)
    if name == "roof_light":
        return _barrel(m)
    if name == "eave":                        # the band on a 3-high wall: its y 0 is the course under the wall top
        w = K.blank()
        for x in range(7):
            for y in range(3):
                for z in (-2, -1, 0):
                    w.set(x, y, z, m.trim.full if y == 0 else m.wall.full)
        for (x, y, z), v in K.eave(m, 7).b.items():
            w.b[(x, y + 2, z)] = v
        return w
    p = dict((n, f) for n, _, f in K.CATALOGUE)[name](m)
    return walled(m, p) if name in WALLED else p


def _barrel(m):
    """The glazed barrel (R5) over a lane between two low walls, its ends closed by gable walls."""
    p = K.blank()
    for x in range(9):
        for z in (-3, 3):
            for y in range(3):
                p.set(x, y, z, m.trim.full if y == 0 else m.wall.full)
    for (x, y, z), v in K.roof_light(m, 9).b.items():
        p.b[(x, y + 3, z)] = v
    return p


ROWS = [   # back (north) to front (south)
    ("brassworks", ["oversail", "cresting", "hip_roof", "chimney", "spire", "dome"]),
    ("brassworks", ["dormer", "clerestory", "roof_light", "corbel_stage", "half_timber", "portal"]),
    ("brassworks", ["eave", "cornice", "string_course", "pier", "window_tall", "window_medium", "window_small",
                    "bay_tall"]),
    ("brassworks", ["balustrade", "parapet", "tone_wall", "machinery", "gear"]),
    ("sunforged", ["bay_tall", "portal", "spire", "half_timber", "chimney", "balustrade", "cornice"]),
]


def sheet():
    sets = {m.name: m for m in K.SETS}
    placed = []                      # (part, x, z) in sheet coords, part normalised to its own 0
    z = 2
    width = 0
    for set_name, names in ROWS:
        m = sets[set_name]
        x = 2
        depth = 0
        for n in names:
            p = K.normalised(piece(n, m))
            x0, x1, _, _, z0, z1 = p.bbox()
            placed.append((p, x, z))
            x += x1 + 1 + GAP
            depth = max(depth, z1 + 1)
        width = max(width, x)
        z += depth + GAP
    demo = K.normalised(K.demo_bay(K.BRASSWORKS))
    dx = width + 1
    placed.append((demo, dx, 2))
    w = dx + demo.bbox()[1] + 3
    d = max(z, 2 + demo.bbox()[5] + 3)
    out = K.blank()
    for x in range(w):
        for zz in range(d):
            out.set(x, 0, zz, PAD)
    for p, ox, oz in placed:
        oy = 0 if p is demo else 1
        for (x, y, zz), v in p.b.items():
            if v[0] not in ("air", "cave_air"):
                out.b[(x + ox, y + oy, zz + oz)] = v
            else:
                out.b.pop((x + ox, y + oy, zz + oz), None)
    return out


def write(out=OUT, s=None):
    s = s or sheet()
    x0, x1, y0, y1, z0, z1 = s.bbox()
    rig = gs._SunRig(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    for (x, y, z), (n, props) in s.b.items():
        if n not in ("air", "cave_air"):
            rig.set(x - x0, y - y0, z - z0, n, **props)
    rig.bury_grass()
    rig.settle()
    rig.check_support("arch_kit_sheet")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    rig.structure().write(out)
    return rig


def demo():
    """The demo bay alone on a 3-block stone apron."""
    d = K.normalised(K.demo_bay(K.BRASSWORKS))
    out = K.blank()
    x1, z1 = d.bbox()[1], d.bbox()[5]
    for x in range(x1 + 7):
        for z in range(z1 + 7):
            out.set(x, 0, z, PAD)
    for (x, y, z), v in d.b.items():
        if v[0] not in ("air", "cave_air"):
            out.b[(x + 3, y, z + 3)] = v
    return out


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    r = write(out)
    print("kit sheet", r.size, len(r.b), "blocks ->", os.path.relpath(out, ROOT))
    r = write(DEMO, demo())
    print("demo bay", r.size, len(r.b), "blocks ->", os.path.relpath(DEMO, ROOT))
