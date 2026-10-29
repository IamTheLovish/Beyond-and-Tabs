#!/usr/bin/env python3
"""The ARCHITECTURE KIT SHEETS for the Eyes structure photos: six compact pads (SHEETS, each <= 25 x 25 so every
part fills the 3/4 and dusk stills - the r1 single 95-wide sheet put the camera too far out to read a dormer) -
roofs, towers, walls, details in the Brassworks set, then the Sunforged and Gravebound sets - and the demo bay
(plinth, pilasters, windows, cornice, eave over a slate roof) alone, to show the parts compose. Wall parts stand in a
short wall of their set (reveals, sills and lips read against a wall); roof parts sit on a plain wall box. Fronts
face south, toward the camera.

    python3 tools/arch_kit_sheet.py      -> design/arch_kit/arch_kit_<sheet>.nbt + design/arch_kit/demo_bay.nbt
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import arch_kit as K  # noqa: E402
import gen_structures as gs  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEMO = os.path.join(ROOT, "design", "arch_kit", "demo_bay.nbt")   # alone: small enough for the 3/4 and dusk stills
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
    if name == "dormer":
        return _on_walls(m, K.dormer_on_roof(m))
    if name == "chimney":
        return _chimney_on_roof(m)
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


def _on_walls(m, roof, h=4):
    """A roof piece standing on the plain walls it covers: the wall box under the columns its eave course spans."""
    eave = [q for q in roof.b if q[1] == min(y for (_, y, _) in roof.b)]
    xs, zs = [q[0] for q in eave], [q[2] for q in roof.b]
    p = K.blank()
    x0, x1, z0, z1 = min(xs) + 1, max(xs) - 1, min(zs), max(zs) - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                for y in range(h):
                    p.set(x, y, z, m.trim.full if y == 0 else m.wall.full)
    for (x, y, z), v in roof.b.items():
        p.b[(x, y + h - min(q[1] for q in eave), z)] = v
    return p


def _chimney_on_roof(m):
    """The house chimney (R11) rising through the slope of a small gable over a plain wall box, its stack carried down
    to the wall top inside the roof."""
    host = mounted(m, K.oversail(m, 7, 7), 7, 7)
    x, z = 4, 1
    roof_y = max(y for (xx, y, zz) in host.b if (xx, zz) == (x, z))
    stack = K.roof_chimney(m)
    for a, b in {(q[0], q[2]) for q in stack.b}:
        for y in range(4, roof_y + 1):
            host.set(x + a, y, z + b, m.wall.full)
    for (a, y, b), v in stack.b.items():
        host.b[(x + a, roof_y + 1 + y, z + b)] = v
    return host


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


SIZE = 25        # a compact sheet is at most 25 x 25, so every part fills the 3/4 and dusk stills
SHEETS = {       # name -> [(set, part)], packed in shelves back (north) to front (south)
    "arch_kit_roofs": [("brassworks", "oversail"), ("brassworks", "cresting"), ("brassworks", "hip_roof"),
                       ("brassworks", "dormer")],
    "arch_kit_towers": [("brassworks", "dome"), ("brassworks", "roof_light"), ("brassworks", "spire"),
                        ("brassworks", "stack"), ("brassworks", "corbel_stage")],
    "arch_kit_walls": [("brassworks", "half_timber"), ("brassworks", "portal"), ("brassworks", "bay_tall"),
                       ("brassworks", "window_medium"), ("brassworks", "window_small"), ("brassworks", "pier"),
                       ("brassworks", "cornice"), ("brassworks", "eave")],
    "arch_kit_details": [("brassworks", "gear"), ("brassworks", "machinery"), ("brassworks", "clerestory"),
                         ("brassworks", "tone_wall"), ("brassworks", "balustrade"), ("brassworks", "parapet"),
                         ("brassworks", "string_course"), ("brassworks", "window_tall")],
    "arch_kit_sunforged": [("sunforged", "hip_roof"), ("sunforged", "portal"), ("sunforged", "half_timber"),
                           ("sunforged", "tone_wall"), ("sunforged", "bay_tall"), ("sunforged", "balustrade")],
    "arch_kit_gravebound": [("gravebound", "portal"), ("gravebound", "corbel_stage"), ("gravebound", "chimney"),
                            ("gravebound", "spire"), ("gravebound", "bay_tall"), ("gravebound", "balustrade")],
}


R3 = {           # round 3 reshoots only the reworked parts (r3 critic), each sheet close enough to judge
    "arch_kit_r3_roofs": [("brassworks", "hip_roof"), ("brassworks", "dormer"), ("brassworks", "chimney"),
                          ("brassworks", "roof_light")],
    "arch_kit_r3_towers": [("brassworks", "dome"), ("brassworks", "spire"), ("gravebound", "spire")],
    "arch_kit_r3_walls": [("brassworks", "half_timber"), ("brassworks", "portal"), ("brassworks", "tone_wall"),
                          ("brassworks", "gear"), ("brassworks", "machinery")],
    "arch_kit_sunforged": SHEETS["arch_kit_sunforged"],
}
SHEETS.update(R3)

R4 = {           # round 4 reshoots the parts the r4 critic named, close enough to judge
    "arch_kit_r4_roofs": [("brassworks", "hip_roof"), ("brassworks", "chimney"), ("brassworks", "dormer"),
                          ("brassworks", "roof_light")],
    "arch_kit_r4_towers": [("brassworks", "dome"), ("brassworks", "spire")],
    "arch_kit_r4_walls": [("brassworks", "tone_wall"), ("brassworks", "gear"), ("brassworks", "machinery"),
                          ("brassworks", "bay_tall"), ("brassworks", "window_medium")],
    "arch_kit_sunforged": SHEETS["arch_kit_sunforged"],
}
SHEETS.update(R4)

R5 = {           # round 5 reshoots the parts the r4 critic named, plus the trim swap on the kept ones
    "arch_kit_r5_roofs": [("brassworks", "cresting"), ("brassworks", "chimney"), ("brassworks", "dormer"),
                          ("brassworks", "oversail")],
    "arch_kit_r5_towers": [("brassworks", "dome"), ("brassworks", "spire")],
    "arch_kit_r5_walls": [("brassworks", "tone_wall"), ("brassworks", "gear"), ("brassworks", "half_timber"),
                          ("brassworks", "balustrade"), ("brassworks", "portal")],
    "arch_kit_sunforged": SHEETS["arch_kit_sunforged"],
}
SHEETS.update(R5)


def _pack(parts):
    """Shelf-pack normalised parts into SIZE x SIZE: (part, x, z) placements; raises if they don't fit."""
    placed, x, z, shelf = [], 1, 1, 0
    for p in parts:
        _, x1, _, _, _, z1 = p.bbox()
        w, d = x1 + 1, z1 + 1
        if x + w > SIZE:
            x, z, shelf = 1, z + shelf + 1, 0
        assert x + w <= SIZE and z + d <= SIZE, "part %dx%d does not fit the sheet" % (w, d)
        placed.append((p, x, z))
        x += w + 1
        shelf = max(shelf, d)
    return placed


def build(name):
    """One compact sheet: its parts on a stone pad, fronts facing south."""
    sets = {m.name: m for m in K.SETS}
    out = K.blank()
    for x in range(SIZE):
        for z in range(SIZE):
            out.set(x, 0, z, PAD)
    for p, ox, oz in _pack([K.normalised(piece(n, sets[s_])) for s_, n in SHEETS[name]]):
        for (x, y, z), v in p.b.items():
            if v[0] not in ("air", "cave_air"):
                out.b[(x + ox, y + 1, z + oz)] = v
    return out


def write(out, s):
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
    for name in SHEETS:
        out = os.path.join(ROOT, "design", "arch_kit", name + ".nbt")
        r = write(out, build(name))
        print(name, r.size, len(r.b), "blocks ->", os.path.relpath(out, ROOT))
    r = write(DEMO, demo())
    print("demo bay", r.size, len(r.b), "blocks ->", os.path.relpath(DEMO, ROOT))
