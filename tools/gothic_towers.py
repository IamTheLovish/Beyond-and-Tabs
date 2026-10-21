#!/usr/bin/env python3
"""TOWERS for the gothic part library (tools/gothic_parts.py): the parts lovish judges first.

lovish, Oct 20 2026, on the v3 capitol: "the towers don't look realistic enough". What makes a stone tower read as
real, and what every tower here does:
  - PROPORTION AND TAPER: slender (height 4-6x its width); a BATTERED foot - three courses stepping out and sloped with
    stairs - that flares into a skirt of living rock; the wall visibly STEPS IN (a weathered set-off with a drip course
    under it) part way up, so the upper shaft is thinner than the lower.
  - MASONRY, NOT STRIPES: walls two thick; QUOINS at the corners of square and polygonal towers (alternate long and
    short stones of a slightly different tone); calm zones of a second, subtle tone (large patches, never pepper); a
    darker plinth; MOSS low and on the windward (west) side, thinning with height, clean stone high up. The only
    horizontal lines are the ones a real tower has: the set-off and the crown's corbels.
  - OPENINGS FOLLOW FUNCTION: windows climb the tower in a spiral (the stair inside), so no two are level; arrow slits
    low, small windows in the middle, tall pointed windows high; every window set one block in (a deep reveal) with a
    projecting sill and a lintel; a lit room behind the glass; a door with an arched porch at the foot.
  - THE CROWN: two courses of corbels with brackets between (machicolations) carrying either a crenellated wall-walk
    round an inner drum, or a half-timbered gallery jettied out over them; then the roof with real EAVES overhanging
    the wall, steep (a cone, an octagonal needle or a square spire) with lucarnes (small lit dormers) part way up, and
    a finial or a banner.
  - VARIETY: round, octagonal and square plans; a stair turret clasped to the side; bartizans hung off the corners;
    pilasters breaking a round shaft; a tiered tower (stone, timber gallery, skirt roof, a second smaller stage, a
    needle) like the tall keep in his castle reference.

Every tower is sealed (the door is closed); its front faces south, toward the RTS camera; y = 0 is the ground.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gothic_parts import Part, palette, hsh, toward, VEC, OPP, LANTERN   # noqa: E402

DIAG = ((1, 1), (1, -1), (-1, 1), (-1, -1))


# ============================================================================================ plans
def plan(shape, r, cx=0, cz=0):
    """The filled cells of a plan of 'radius' r: round, oct (octagon), square."""
    out = set()
    for x in range(-r - 1, r + 2):
        for z in range(-r - 1, r + 2):
            if shape == "round":
                ok = math.hypot(x, z) < r + 0.5
            elif shape == "oct":
                ok = abs(x) <= r and abs(z) <= r and abs(x) + abs(z) <= r + (r + 1) // 2
            else:
                ok = abs(x) <= r and abs(z) <= r
            if ok:
                out.add((cx + x, cz + z))
    return out


def normals(cells, c):
    x, z = c
    return [f for f, (vx, vz) in VEC.items() if (x + vx, z + vz) not in cells]


def outline(cells):
    return {c for c in cells if normals(cells, c)}


def corners(shape, cells):
    """The vertex cells of a polygonal plan (two or more open sides); a round plan has none."""
    if shape == "round":
        return set()
    return {c for c in cells if len(normals(cells, c)) >= 2}


def GT_ring(shape, r):
    return ring_of(shape, r + 1, r)


def ring_of(shape, r_out, r_in, cx=0, cz=0):
    return plan(shape, r_out, cx, cz) - plan(shape, r_in, cx, cz)


def angle_of(c, cx=0, cz=0):
    return math.atan2(c[1] - cz, c[0] - cx)


# ============================================================================================ masonry
class Mason:
    """Chooses the stone for a wall cell: plinth, calm tone zones, moss low and windward, quoins at the corners."""

    def __init__(self, p, h, seed=0, plinth=3):
        self.p, self.h, self.seed, self.plinth = p, h, seed, plinth

    def mossy(self, x, y, z):
        """Weathering: thin vertical streaks (rain runs down a wall), thickest low on the windward west, gone high up."""
        windward = 1.0 if x < 0 else 0.5 if z < 0 else 0.3
        reach = 0.28 * self.h
        if y > reach:
            return False
        streak = hsh(x * 3 + z * 5, (y + hsh(x, z, 2) % 3) // 4, self.seed + 9) / 101
        return streak < windward * (1 - y / reach) * 0.42

    def stone(self, x, y, z):
        if y < self.plinth:
            return "base_alt" if self.mossy(x, y, z) else "base"
        if self.mossy(x, y, z):
            return "base_alt" if y < self.plinth + 3 else "wall_moss"
        zone = hsh((x + z + 40) // 4, (y + 2) // 5, self.seed + 3) % 100
        return "wall_alt" if zone < 24 else "wall"

    def put(self, x, y, z, quoin=False):
        self.p.set(x, y, z, "quoin" if quoin and y >= self.plinth else self.stone(x, y, z))


def quoin_cells(shape, cells, y):
    """Corner cells, plus the long stone of the course running one cell along alternate faces (2-course stones)."""
    out = set()
    long_course = (y // 2) % 2 == 0
    for c in corners(shape, cells):
        out.add(c)
        ns = normals(cells, c)
        for f in ns:
            vx, vz = VEC[f]
            # step along the face perpendicular to f: alternate courses run along x faces, then z faces
            along = (vz, vx) if long_course == (f in ("north", "south")) else None
            if along:
                for s in (1, -1):
                    n = (c[0] + along[0] * s, c[1] + along[1] * s)
                    if n in cells and n not in corners(shape, cells) and normals(cells, n):
                        out.add(n)
    return out


# ============================================================================================ roofs
def spire(p, shape, R, y0, per, role="roof", lucarne_at=None, finial="spike", eaves=True):
    """A steep roof on plan `shape` from radius R at y0: EAVES (a course of stairs one beyond R, overhanging the wall
    head), then rings stepping in every `per` courses (stairs on each ring's last course), lucarnes - small gabled lit
    dormers - on the four sides at ring `lucarne_at`, and a finial: a slim shaft, a gold knop, a spike or a banner."""
    rst = role + "_st"
    if eaves:
        for (x, z) in ring_of(shape, R + 1, R):
            p.stair(x, y0, z, toward(x, z), "bottom", rst)
    y = y0
    for i, rr in enumerate(range(R, 0, -1)):
        cells = plan(shape, rr)
        inner = plan(shape, rr - 1)
        for k in range(per - 1):
            for (x, z) in cells:
                p.set(x, y + k, z, role)
        for (x, z) in inner:
            p.set(x, y + per - 1, z, role)
        for (x, z) in cells - inner:
            p.stair(x, y + per - 1, z, toward(x, z), "bottom", rst)
        at = lucarne_at if isinstance(lucarne_at, (tuple, list)) else (lucarne_at,)
        if lucarne_at is not None and i in at and rr >= 2:
            for f, (vx, vz) in VEC.items():
                _lucarne(p, vx, vz, f, rr, y, role)
        y += per
    p.set(0, y, 0, role)
    p.set(0, y + 1, 0, role + "_wall")
    p.set(0, y + 2, 0, role + "_wall")
    p.set(0, y + 3, 0, "gold")
    if finial == "banner":
        p.set(0, y + 4, 0, "banner_top", rotation="4")
        return y + 5
    p.set(0, y + 4, 0, "chain", axis="y")
    p.set(0, y + 5, 0, "spike", facing="up")
    return y + 5


TAPER = [lambda: plan("oct", 2), lambda: {(x, z) for x in range(-2, 3) for z in range(-2, 3) if math.hypot(x, z) < 2.0},
         lambda: plan("square", 1), lambda: {(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)}, lambda: {(0, 0)}]


def needle_taper(p, y0, per, role="roof", start=0, finial="spike"):
    """A slim needle that tapers in half-steps (an octagon 5 wide, a rounded 5, a square 3, a cross, a single shaft),
    `per` courses each, stairs where each stage steps in - the smooth copper needles of his castle reference."""
    rst = role + "_st"
    y = y0
    stages = [f() for f in TAPER[start:]]
    for i, cells in enumerate(stages):
        nxt = stages[i + 1] if i + 1 < len(stages) else set()
        for k in range(per):
            for (x, z) in cells:
                last = k == per - 1 and (x, z) not in nxt
                if last and (x, z) != (0, 0):
                    p.stair(x, y + k, z, toward(x, z), "bottom", rst)
                else:
                    p.set(x, y + k, z, role)
        y += per
    p.set(0, y, 0, role + "_wall")
    p.set(0, y + 1, 0, "gold")
    if finial == "banner":
        p.set(0, y + 2, 0, "banner_top", rotation="4")
        return y + 3
    p.set(0, y + 2, 0, "chain", axis="y")
    p.set(0, y + 3, 0, "spike", facing="up")
    return y + 3


def _lucarne(p, vx, vz, f, rr, y, role):
    """A lucarne on a spire's face: a little gable three wide standing proud of ring rr, a lit window in it."""
    lx, lz = vx * (rr + 1), vz * (rr + 1)
    px_, pz_ = vz, vx                                     # along the face
    rst = role + "_st"
    for s in (-1, 0, 1):
        cx, cz = lx + px_ * s, lz + pz_ * s
        p.set(cx, y, cz, "trim")
        p.set(cx, y + 1, cz, "trim" if s else "win_lit")
        p.stair(cx, y + 2, cz, OPP[f], "bottom", rst) if s else p.set(cx, y + 2, cz, "trim")
    p.stair(lx, y + 3, lz, OPP[f], "bottom", rst)
    p.set(lx - vx, y + 1, lz - vz, "light")


def skirt(p, shape, r_in, r_out, y0, role="needle"):
    """A flared skirt roof round a tower (his keep's tiers): eaves at r_out, well beyond the wall below, climbing one
    ring a course to the stage above at r_in; solid under each step."""
    rst = role + "_st"
    for k, rr in enumerate(range(r_out, r_in, -1)):
        for (x, z) in ring_of(shape, rr, rr - 1):
            for y in range(y0, y0 + k):
                p.set(x, y, z, role)
            p.stair(x, y0 + k, z, toward(x, z), "bottom", rst)
    for (x, z) in plan(shape, r_in):
        p.set(x, y0, z, role)
    return y0 + (r_out - r_in)


# ============================================================================================ the shaft
def shaft(p, shape, r, y0, y1, mason, cx=0, cz=0, thick=2, floors=5):
    """A tower shaft of plan (shape, r) from y0 to y1: walls `thick` deep, masonry with quoins, an interior hollow with a
    floor every `floors` courses (a glowing boss in each ceiling)."""
    cells = plan(shape, r, cx, cz)
    inner = plan(shape, r - thick, cx, cz) if r - thick >= 0 else set()
    for y in range(y0, y1 + 1):
        qs = quoin_cells(shape, cells, y)
        for c in cells - inner:
            mason.put(c[0], y, c[1], quoin=c in qs)
        for c in inner:
            if (y - y0) % floors == 0:
                p.set(c[0], y, c[1], "light" if c == (cx, cz) else "floor")
            else:
                p.clear(c[0], y, c[1])
    return cells


def battered_foot(p, shape, r, mason, rock=True, cx=0, cz=0, seed=0):
    """The battered foot: y 0 at r + 2, y 1 at r + 1 with a sloped course of stairs outside it, y 2 sloping into the
    shaft; under it (y < 0) a skirt of rock flaring out, strata in big zones, moss and grass on its top."""
    for (x, z) in plan(shape, r + 2, cx, cz):
        p.set(x, 0, z, "base")
    for (x, z) in plan(shape, r + 1, cx, cz):
        p.set(x, 1, z, mason.stone(x, 1, z) if mason.stone(x, 1, z) != "wall" else "base")
    for (x, z) in ring_of(shape, r + 2, r + 1, cx, cz):
        p.stair(x, 1, z, toward(x, z, cx, cz), "bottom", "base_st")
    for (x, z) in ring_of(shape, r + 1, r, cx, cz):
        p.stair(x, 2, z, toward(x, z, cx, cz), "bottom", "base_st")
    if not rock:
        return
    for d, y in enumerate((-1, -2, -3)):
        rr = r + 3 + d
        for (x, z) in plan("round", rr + 1, cx, cz):
            dist = math.hypot(x - cx, z - cz)
            wob = (hsh(x // 2, z // 2, seed + d) % 3) - 1
            if dist > rr + 0.5 + wob:
                continue
            band = (y + 3 + (hsh(x // 4, z // 4, seed + 1) % 2)) // 2
            p.set(x, y, z, ("rock1", "rock2", "rock3", "rock1")[band % 4])
    for (x, z) in plan("round", r + 6, cx, cz):
        for y in (-1, -2, -3):
            if p.get(x, y, z) != "air" and p.get(x, y + 1, z) == "air":
                if hsh(x, z, seed + 7) % 3:
                    p.set(x, y, z, "moss" if hsh(x, z, seed + 8) % 4 == 0 else "grass", **(
                        {} if hsh(x, z, seed + 8) % 4 == 0 else {"snowy": "false"}))
                break


def set_off(p, shape, r_low, r_up, y, mason, cx=0, cz=0):
    """The wall steps in at y: a drip course (stairs hung under the ledge, one out), the ledge weathered with stairs."""
    for (x, z) in ring_of(shape, r_low + 1, r_low, cx, cz):
        p.stair(x, y - 1, z, toward(x, z, cx, cz), "top", "band_st")
    for (x, z) in ring_of(shape, r_low, r_up, cx, cz):
        p.stair(x, y, z, toward(x, z, cx, cz), "bottom", "band_st")


# ============================================================================================ openings
def face_cells(cells, skip=()):
    """Outline cells with exactly one open side: (cell, outward direction)."""
    out = []
    for c in cells:
        ns = normals(cells, c)
        if len(ns) == 1 and c not in skip:
            out.append((c, ns[0]))
    return out


def window(p, c, f, y, kind, cells, cx=0, cz=0):
    """One opening at outline cell c facing f with its sill at y.
    slit: a two-high slit flush in the face, opening into the room.
    small: a deep reveal (the face cell open, glass one in), two high, projecting sill, a lintel.
    tall: the same three high under a pointed head (a carved keystone and a hood stair over it)."""
    vx, vz = VEC[f]
    x, z = c
    ix, iz = x - vx, z - vz                     # one in: the glass
    bx, bz = x - 2 * vx, z - 2 * vz             # two in: the room's glow
    hgt = {"slit": 2, "small": 2, "tall": 3}[kind]
    if kind == "slit":
        for k in range(hgt):
            p.set(x, y + k, z, "slit")
            p.clear(ix, y + k, iz) if (ix, iz) in cells and p.get(ix, y + k, iz) != "air" else None
        return
    lit = hsh(x * 7 + y, z, 11) % 3 != 0                     # most rooms lit, a few dark
    for k in range(hgt):
        p.clear(x, y + k, z)
        p.set(ix, y + k, iz, "win_lit" if lit else "win_dark")
    if lit and (bx, bz) in cells:
        p.set(bx, y, bz, "light")
    p.slab(x + vx, y - 1, z + vz, "top", "trim_slab")           # the sill, proud of the face
    p.set(x, y - 1, z, "trim")
    p.set(x, y + hgt, z, "carve" if kind == "tall" else "trim")  # lintel / keystone
    if kind == "tall":
        p.stair(x + vx, y + hgt, z + vz, OPP[f], "top", "trim_st")  # the hood


def spiral_windows(p, shape, cells_at, y_from, y_to, h, seed=0, skip=(), cx=0, cz=0, step=2.3, turn=1.35):
    """Windows climbing round the tower with the stair: each one `step` higher and `turn` radians further round, slits
    low, small windows in the middle, tall ones high. cells_at(y) gives the plan at that height."""
    y = float(y_from)
    a = 1.2 + seed * 0.7
    while y < y_to:
        yi = int(round(y))
        cells = cells_at(yi)
        faces = face_cells(cells, skip)
        best = min(faces, key=lambda cf: abs(math.remainder(angle_of(cf[0], cx, cz) - a, 2 * math.pi)))
        frac = yi / h
        kind = "slit" if frac < 0.33 else "small" if frac < 0.7 else "tall"
        window(p, best[0], best[1], yi, kind, cells, cx, cz)
        y += step + (hsh(yi, seed) % 3) * 0.4
        a += turn
    return


def storey_windows(p, cells_at, y_from, y_to, h, every=4, skip=(), twin_from=7):
    """The rooms' own windows: every storey (every `every` courses) a window centred on each main face - slits in the
    lowest third, small windows in the middle, tall pointed ones high - paired lancets either side of a mullion where a
    face is `twin_from` or more wide. Skips cells already opened or in `skip`."""
    for y in range(y_from, y_to, every):
        cells = cells_at(y)
        frac = y / h
        kind = "slit" if frac < 0.3 else "small" if frac < 0.6 else "tall"
        for f, (vx, vz) in VEC.items():
            line = [c for c, ff in face_cells(cells, skip) if ff == f]
            if not line:
                continue
            width = len(line)
            mid = [c for c in line if (c[0] == 0 if vz else c[1] == 0)]
            if not mid:
                continue
            c = mid[0]
            if width >= twin_from and kind != "slit":
                picks = [(c[0] + vz, c[1] + vx), (c[0] - vz, c[1] - vx)]
            else:
                picks = [c]
            for q in picks:
                if q in cells and q not in skip and normals(cells, q) == [f]:
                    here = p.get(q[0], y, q[1])
                    if here != "air" and "pane" not in here and "door" not in here:
                        window(p, q, f, y, kind, cells)


def door(p, r, mason):
    """A closed door on the south face at the foot, recessed in an arched porch cut through the battered foot, a step."""
    z = r
    p.set(0, 0, z, "base")
    p.set(0, 1, z, "door", facing="north", half="lower", hinge="left", open="false", powered="false")
    p.set(0, 2, z, "door", facing="north", half="upper", hinge="left", open="false", powered="false")
    p.set(0, 3, z, "carve")
    for dz in (1, 2):
        for y in (1, 2, 3):
            p.clear(0, y, z + dz)
        p.set(0, 0, z + dz, "path")
    for s, f in ((-1, "west"), (1, "east")):
        for y in (1, 2):
            p.set(s, y, z + 1, "quoin")
        p.stair(s, 3, z + 1, f, "top", "trim_st")
    p.set(0, 4, z + 1, "carve")
    p.stair(0, 0, z + 3, "north", "bottom", "base_st")
    for s in (-1, 1):
        p.set(s * 2, 1, z + 2, "rail")
        p.lantern(s * 2, 2, z + 2)


def pilasters(p, r, y0, y1, mason, angles=(45, 135, 225, 315)):
    """Flat pilasters breaking a round shaft (one block proud), weathered at the top with a stair."""
    for a in angles:
        t = math.radians(a)
        x, z = int(round(math.cos(t) * (r + 0.7))), int(round(math.sin(t) * (r + 0.7)))
        for y in range(y0, y1):
            mason.put(x, y, z, quoin=(y // 2) % 2 == 0)
        p.stair(x, y1, z, toward(x, z), "bottom", "trim_st")


# ============================================================================================ crowns
def machicolations(p, shape, r, y):
    """Two courses of corbels stepping out from radius r (stairs hung upside down against the wall), brackets of
    stone between them below, the walk's edge on top. Returns the crown's outer radius (r + 2)."""
    for (x, z) in ring_of(shape, r + 1, r):
        p.stair(x, y - 1, z, toward(x, z), "top", "band_st")
        if hsh(x, z) % 2 == 0:
            p.set(x, y - 2, z, "base_wall")
        p.set(x, y, z, "trim")
    for (x, z) in ring_of(shape, r + 2, r + 1):
        p.stair(x, y, z, toward(x, z), "top", "band_st")
    return r + 2


def crenels(p, shape, R, y, h=2):
    """A crenellated parapet on ring R at y: merlons (h high, slab capped, a slit in some) and embrasures (a slab)."""
    ring = sorted(ring_of(shape, R, R - 1), key=lambda c: angle_of(c))
    for i, (x, z) in enumerate(ring):
        p.set(x, y - 1, z, "trim")
        if (i // 2) % 2 == 0:
            for k in range(h):
                p.set(x, y + k, z, "wall")
            p.slab(x, y + h, z, "bottom", "band_slab")
        else:
            p.slab(x, y, z, "bottom", "band_slab")


def timber_storey(p, shape, R, y, storey=5, windows_every=3):
    """A half-timbered storey on plan (shape, R) from y: a sill beam, dark posts at the corners and every third cell,
    a rail at mid height, white plaster panels, small lit windows with open shutters, a floor and a glowing ceiling."""
    cells = plan(shape, R)
    ol = outline(cells)
    cs = corners(shape, cells)
    ring = sorted(ol, key=lambda c: angle_of(c))
    for (x, z) in cells - ol:
        p.set(x, y, z, "floor")
        p.set(x, y + storey - 1, z, "light" if (x + z) % 3 == 0 else "floor")
    for i, (x, z) in enumerate(ring):
        post = (x, z) in cs or i % windows_every == 0
        p.set(x, y, z, "timber_beam", axis="x")
        for k in range(1, storey):
            yy = y + k
            if post:
                p.set(x, yy, z, "timber", axis="y")
            elif k == 2:
                p.set(x, yy, z, "timber", axis="x")
            elif k in (3, 4) and i % windows_every != 0 and len(normals(cells, (x, z))) == 1:
                p.set(x, yy, z, "win_lit")
                p.set(x - VEC[normals(cells, (x, z))[0]][0], yy, z - VEC[normals(cells, (x, z))[0]][1], "light")
            else:
                p.set(x, yy, z, "plaster")
    return y + storey


def jetty(p, shape, r_from, y):
    """The jetty: a row of timber brackets (upside-down stairs) carrying a storey one out over the wall below."""
    for (x, z) in ring_of(shape, r_from + 1, r_from):
        p.stair(x, y, z, toward(x, z), "top", "timber_st")


def bartizan(p, cx, cz, y, h=6, role="roof"):
    """A bartizan hung off a corner: a corbelled foot (a bracket, then stairs out), a little round body with slits, a
    lit centre, eaves and a cone with a finial."""
    p.set(cx, y - 2, cz, "base_wall")
    p.set(cx, y - 1, cz, "trim")
    for (x, z) in plan("round", 1, cx, cz) - {(cx, cz)}:
        p.stair(x, y - 1, z, toward(x, z, cx, cz), "top", "band_st")
    p.set(cx, y, cz, "trim")
    for k in range(1, h):
        for (x, z) in plan("round", 1, cx, cz) - {(cx, cz)}:
            p.set(x, y + k, z, "wall_alt" if (x + z + k) % 5 else "wall")
        p.set(cx, y + k, cz, "light")
    for f, (vx, vz) in VEC.items():
        p.set(cx + vx, y + 3, cz + vz, "slit")
    top = y + h
    for (x, z) in plan("round", 2, cx, cz) - plan("round", 1, cx, cz):
        p.stair(x, top, z, toward(x, z, cx, cz), "bottom", role + "_st")
    for (x, z) in plan("round", 1, cx, cz):
        p.set(x, top, z, role)
        p.set(x, top + 1, z, role)
        p.set(x, top + 2, z, role) if (x, z) == (cx, cz) else p.stair(x, top + 2, z, toward(x, z, cx, cz), "bottom",
                                                                         role + "_st")
    p.set(cx, top + 3, cz, role)
    p.set(cx, top + 4, cz, role + "_wall")
    p.set(cx, top + 5, cz, "spike", facing="up")


# ============================================================================================ the towers
def stone_tower(shape="round", r=4, h=34, setoff=0.42, crown="crenel", roof="cone", per=4, lucarnes=True,
                finial="spike", stair_turret=False, bartizans=False, pils=None, door_at_foot=True, seed=0,
                pal=None, rock=True, galleries=1, eaves=True, roof_r=None):
    """The general stone tower. crown: 'crenel' (corbels, a crenellated walk round an inner drum carrying the roof),
    'timber' (corbels carrying `galleries` jettied half-timbered storeys under the roof), 'plain' (corbels, the roof
    straight on top). roof: 'cone' (on the plan's own shape, `per` courses a ring), 'needle' (slate, tall)."""
    p = Part(pal)
    m = Mason(p, h, seed)
    r_up = r - 1 if setoff else r
    y_set = int(h * setoff) if setoff else h + 1
    battered_foot(p, shape, r, m, rock=rock, seed=seed)
    shaft(p, shape, r, 2, y_set - 1, m)
    shaft(p, shape, r_up, y_set, h, m)
    if setoff:
        set_off(p, shape, r, r_up, y_set, m)
    if pils is None:
        pils = shape == "round" and r >= 3
    if pils:
        pilasters(p, r, 3, y_set - 1, m)
        pilasters(p, r_up, y_set + 1, h - 3, m, angles=(70, 160, 250, 340))
    turret_cells = set()
    if stair_turret:
        st = stair_tower(r=2, h=int(h * 0.74), pal=p.pal, seed=seed + 5, rock=False, footing=False,
                         role="needle" if roof == "needle" else "roof", crown=False)
        ox, oz = -(r + 1), -(r - 1)                       # clasped to the west flank, behind the front
        p.place(st, ox, 0, oz)
        turret_cells = {(x + ox, z + oz) for (x, z) in plan("oct", 2)}

    def cells_at(y):
        return plan(shape, r if y < y_set else r_up)
    skip = set(turret_cells) | {c for c in plan(shape, r) if abs(c[0]) <= 1 and c[1] > 0}
    spiral_windows(p, shape, cells_at, 4, h - 3, h, seed=seed, skip=skip, step=2.6)
    storey_windows(p, cells_at, 6, h - 6, h, skip=set(turret_cells))
    # the top storey: tall windows on the four main faces, under the corbels
    for (c, f) in face_cells(plan(shape, r_up)):
        if (c[0] == 0 or c[1] == 0) and f in ("south", "west", "north", "east") and \
                p.get(c[0], h - 5, c[1]) != "air" and "pane" not in p.get(c[0], h - 5, c[1]) and \
                c not in turret_cells:
            window(p, c, f, h - 5, "tall", plan(shape, r_up))
    if door_at_foot:
        door(p, r, m)
    if crown == "none":                                    # a slim needle straight off the wall head, a collar
        for (x, z) in GT_ring(shape, r_up):
            p.stair(x, h, z, toward(x, z), "top", "band_st")
        R = r_up + 1
    else:
        R = machicolations(p, shape, r_up, h)
    y = h + 1
    role_roof = "needle" if roof == "needle" else "roof"
    if crown == "crenel":
        for (x, z) in plan(shape, R - 1):
            p.set(x, h, z, "path")
        crenels(p, shape, R, y)
        drum = max(1, R - 2)                               # the drum stands on the shaft's wall head
        for k in range(0, 5):
            for c in plan(shape, drum) - plan(shape, drum - 2):
                m.put(c[0], y + k, c[1], quoin=False)
        for (c, f) in face_cells(plan(shape, drum)):
            if c[0] == 0 or c[1] == 0:
                p.set(c[0], y + 2, c[1], "win_lit")
                p.set(c[0] - VEC[f][0], y + 2, c[1] - VEC[f][1], "light")
        rb, yb = drum, y + 5
    elif crown == "timber":
        rr = R - 1
        for g in range(galleries):
            if g:
                jetty(p, shape, rr, y)
                rr += 1
                y += 1
            y = timber_storey(p, shape, rr, y)
        rb, yb = rr, y
    else:
        rb, yb = (r_up, h + 1) if crown == "none" else (R - 1, y)
    if bartizans:
        for (sx, sz) in DIAG:
            k = R if shape == "square" else int(round((R) / math.sqrt(2)))
            bartizan(p, sx * k, sz * k, h - 1, h=6 + (crown == "crenel"), role=role_roof)
    if roof_r == "taper":                                  # a slim tapering needle off a collar
        for (x, z) in plan(shape, rb) - TAPER[0]():
            p.slab(x, yb, z, "bottom", "band_slab")
        needle_taper(p, yb, per, "needle" if roof == "needle" else "roof", finial=finial)
        return p
    if roof_r is not None:                                 # a slimmer needle than the crown (a collar round it)
        for (x, z) in plan(shape, rb) - plan(shape, roof_r):
            p.slab(x, yb, z, "bottom", "band_slab")
        rb = roof_r
    if roof == "needle":
        spire(p, shape if shape != "round" else "oct", rb, yb, per, "needle", lucarne_at=(0, 2) if lucarnes else None,
              finial=finial)
    else:
        spire(p, shape, rb, yb, per, "roof", lucarne_at=(0, 2) if lucarnes else None, finial=finial, eaves=eaves)
    return p


def stair_tower(r=2, h=24, pal=None, seed=1, rock=True, footing=True, role="needle", crown=True):
    """(d) An octagonal stair turret: slits and small windows climbing round it (the stair), quoins on its eight
    corners, a corbelled crown and a slate needle."""
    p = Part(pal)
    m = Mason(p, h, seed)
    if footing:
        battered_foot(p, "oct", r, m, rock=rock, seed=seed)
    shaft(p, "oct", r, 0 if not footing else 2, h, m, thick=r + 1, floors=99)
    cells = plan("oct", r)
    y = 3.0
    a = 2.4
    while y < h - 2:
        yi = int(y)
        best = min(face_cells(cells), key=lambda cf: abs(math.remainder(angle_of(cf[0]) - a, 2 * math.pi)))
        window(p, best[0], best[1], yi, "slit" if yi < h * 0.6 else "small", cells)
        y += 3.2
        a += 1.25
    if crown:
        R = machicolations(p, "oct", r, h)
        for (x, z) in plan("oct", R - 1):
            p.set(x, h, z, "trim")
        spire(p, "oct", R - 1, h + 1, 4, role, lucarne_at=None)
    else:
        spire(p, "oct", r, h + 1, 3, role, lucarne_at=None)
    return p


def wall_tower(r=4, h=17, pal=None, seed=2):
    """(k) A round tower of the curtain wall: battered, pilasters, slits, machicolations, a crenellated walk round a
    drum, a cone."""
    return stone_tower("round", r, h, setoff=None, crown="crenel", roof="cone", per=3, lucarnes=False, seed=seed,
                       pal=pal, door_at_foot=False)


def tiered_tower(pal=None, seed=4):
    """(o) The tiered keep of his castle reference: a square stone shaft, a jettied timber gallery, a flared slate
    skirt, a smaller square stone stage with its own timber band and skirt, then a tall slate needle with lucarnes and
    four corner pinnacles - the tower that reads from across the map."""
    r, h1 = 4, 24
    p = Part(pal)
    m = Mason(p, h1 + 18, seed)
    battered_foot(p, "square", r, m, seed=seed)
    shaft(p, "square", r, 2, h1, m)
    spiral_windows(p, "square", lambda y: plan("square", r), 4, h1 - 2, h1 + 18, seed=seed, step=2.6,
                   skip={c for c in plan("square", r) if abs(c[0]) <= 1 and c[1] > 0})
    storey_windows(p, lambda y: plan("square", r), 6, h1 - 3, h1 + 18)
    door(p, r, m)
    R = machicolations(p, "square", r, h1)
    y = timber_storey(p, "square", R - 1, h1 + 1)
    skirt(p, "square", r - 1, R + 1, y)
    # stage 2: stone, smaller, its own quoins
    r2 = r - 1
    y2 = y + 1
    shaft(p, "square", r2, y2, y2 + 7, m)
    for (c, f) in face_cells(plan("square", r2)):
        if c[0] == 0 or c[1] == 0:
            window(p, c, f, y2 + 3, "tall", plan("square", r2))
    jetty(p, "square", r2, y2 + 8)
    y3 = timber_storey(p, "square", r2 + 1, y2 + 9, storey=4)
    skirt(p, "square", r2 - 1, r2 + 3, y3)
    for (sx, sz) in DIAG:
        x, z = sx * (r2 + 1), sz * (r2 + 1)
        p.set(x, y3 + 1, z, "carve")
        p.set(x, y3 + 2, z, "rail")
        p.set(x, y3 + 3, z, "needle_wall")
        p.set(x, y3 + 4, z, "spike", facing="up")
    spire(p, "oct", r2, y3 + 1, 5, "needle", lucarne_at=0, finial="banner", eaves=False)
    return p


def tower_round_keep(pal=None):
    """(c) The round tower: battered into rock, pilasters, a set-off, windows climbing the stair turret clasped to its
    side, machicolations, a crenellated walk round a drum, a steep red cone with lucarnes."""
    return stone_tower("round", 4, 36, crown="crenel", roof="cone", per=4, stair_turret=True, seed=0, pal=pal)


def tower_oct_gallery(pal=None):
    """(n) The octagonal tower crowned by two jettied half-timbered gallery storeys under a slate needle (his tall dark
    needles), quoins on the eight corners."""
    return stone_tower("oct", 4, 32, crown="timber", galleries=2, roof="needle", per=4, seed=1, finial="banner",
                       pal=pal)


def tower_square_bartizans(pal=None):
    """The square tower: quoins, a set-off, machicolations and a parapet, four bartizans hung off the corners, a
    steep square spire with lucarnes."""
    return stone_tower("square", 3, 30, crown="crenel", roof="cone", per=4, bartizans=True, seed=2, pal=pal)


def tower_slender_turret(pal=None):
    """A slender round turret (5 wide, 28 tall): no set-off, corbels, a jettied timber storey, a red cone."""
    return stone_tower("round", 2, 26, setoff=None, crown="timber", roof="cone", per=4, lucarnes=False, seed=3,
                       pal=pal, pils=False)


TOWERS = [
    ("t1_round_keep", "(c) round tower: batter, pilasters, stair turret, crenels, cone", tower_round_keep),
    ("t2_oct_gallery", "(n) octagonal tower: quoins, jettied timber galleries, needle", tower_oct_gallery),
    ("t3_square_bartizans", "square tower: quoins, parapet, bartizans, spire", tower_square_bartizans),
    ("t4_tiered_keep", "(o) tiered keep: timber, slate skirts, needle", tiered_tower),
    ("t5_slender_turret", "slender turret: timber storey, cone", tower_slender_turret),
    ("d_stair_tower", "(d) octagonal stair tower", lambda pal: stair_tower(2, 24, pal=pal)),
    ("k_wall_tower", "(k) round wall tower", lambda pal: wall_tower(4, 17, pal=pal)),
]


def skyline(pal=None):
    """The towers stood together at castle spacing (for the low view beside his references)."""
    p = Part(pal)
    p.place(tiered_tower(pal), 0, 4, 0)
    p.place(tower_round_keep(pal), -16, 0, 9)
    p.place(tower_oct_gallery(pal), 15, 2, 7)
    p.place(tower_square_bartizans(pal), 4, -2, 18)
    p.place(tower_slender_turret(pal), -8, 6, -8)
    return p


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp"
    pal = palette(os.environ.get("BAT_ROOF", "red"), os.environ.get("BAT_STONE", "warm"))
    for key, label, make in TOWERS + [("skyline", "skyline", skyline)]:
        rig = make(pal).to_rig(key)
        rig.structure().write(os.path.join(out, key + ".nbt"))
        print("%-22s %6d blocks %s" % (key, len(rig.b), "x".join(map(str, rig.size))))
