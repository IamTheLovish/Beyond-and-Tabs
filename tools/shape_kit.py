#!/usr/bin/env python3
"""A SHAPE KIT: domes, spires, curved roofs and tapering towers from clean geometry instead of hand-written block loops.

Hand-placed rings and courses (gothic_parts.cone, needle, ribbed_dome) come out as cube steps with the proportions the
loop happened to give. Here a building element is a signed-distance shape (negative inside, in blocks), so its
profile is a curve you choose - an onion's bulge and neck, a pointed arch's two arcs, a swept roof's sag - and the
blocks follow from it:

    import shape_kit as K
    dome = K.onion_dome(0, 0, 8, 5, 12) | K.sphere((0, 8, 0), 3)
    K.build(part, dome, "roof", shell=1)           # a gothic_parts.Part: roof / roof_st / roof_slab from its palette
    K.build(rig, K.cone(0, 0, 4, 0, 9), "deepslate_tiles")   # any target with .set(x, y, z, name, **props)

Coordinates are block cells: the integer point (x, y, z) is the centre of cell (x, y, z), y up, z south (the RTS
camera's side), as in gen_structures.Structure and gothic_parts.Part. A round shape of radius r takes the cells whose
centres lie strictly within r; box(lo, hi) takes the cells lo..hi inclusive.

PRIMITIVES: box, cylinder, cone, sphere, ellipsoid, onion_dome, spire (pointed arch or ogee profile), torus (rings),
loft (any radius profile round a vertical axis: tapering towers, bell roofs, drums), curved_roof (a roof along x with a
straight, swept or bowed section), radial_ribs (thin vertical fins round an axis: dome ribs). Combine with | (union),
- (subtraction), & (intersection); .moved(dx, dy, dz); .skin(t) keeps a t-thick layer inside the surface.

VOXELIZE: voxelize(shape, shell=None, edges=True) -> {(x, y, z): Cell(kind, facing, half)}, kind "full" / "stairs" /
"slab". shell=t keeps only the cells within t (26-neighbour steps) of the outside - a hollow dome or spire, closed (no
face-to-face leak from the inside out). The EDGE PASS reads the surface's normal: on a slope the outermost step becomes
a stair climbing toward the shape (half = "top" on an underside, as an onion's lower bulge), on a near-flat surface a
step that is about half filled becomes a slab, and walls stay full blocks - so curves read as curves at game zoom
rather than as cube steps. Stairs are written "straight"; gothic_parts.Part.to_rig() settles their corner shapes.

    python3 tools/shape_kit.py OUT_DIR    -> sample .nbt: onion dome, ribbed dome, pointed spire, round tower, swept roof
Render them: python3 tools/structure_render.py OUT_DIR/*.nbt -o sheet.png --px 32 --light
"""
import collections
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

Cell = collections.namedtuple("Cell", "kind facing half")
FULL = Cell("full", None, None)
EPS = 1e-6
FLAT = 0.85      # |normal.y| above this: a near-flat surface (slabs refine its steps)
STEEP = 0.2      # |normal.y| below this: a wall (full blocks)
STAIR = 0.8      # a surface cell less full than this (estimated) becomes a stair; a box's square rim stays full
H = 0.3          # the gradient's sample step


# ============================================================================================ shapes
class Shape:
    """A signed distance (approximate is fine: negative inside) over N x 3 points, with its bounds (lo, hi)."""

    def __init__(self, fn, lo, hi):
        self.fn, self.lo, self.hi = fn, np.asarray(lo, float), np.asarray(hi, float)

    def __call__(self, p):
        return self.fn(np.asarray(p, float).reshape(-1, 3))

    def __or__(self, o):
        return Shape(lambda p: np.minimum(self.fn(p), o.fn(p)), np.minimum(self.lo, o.lo), np.maximum(self.hi, o.hi))

    def __sub__(self, o):
        return Shape(lambda p: np.maximum(self.fn(p), -o.fn(p)), self.lo, self.hi)

    def __and__(self, o):
        return Shape(lambda p: np.maximum(self.fn(p), o.fn(p)), np.maximum(self.lo, o.lo), np.minimum(self.hi, o.hi))

    def moved(self, dx=0, dy=0, dz=0):
        d = np.array([dx, dy, dz], float)
        return Shape(lambda p: self.fn(p - d), self.lo + d, self.hi + d)

    def skin(self, t):
        """The t-thick layer just inside the surface (a distance-field shell: for decoration such as rib bands; a
        hollow you build in is voxelize(..., shell=t), which is closed)."""
        return Shape(lambda p: np.maximum(self.fn(p), -self.fn(p) - t), self.lo, self.hi)


def box(lo, hi):
    """The cells lo..hi inclusive (corners in either order)."""
    a = np.minimum(lo, hi) - 0.5
    b = np.maximum(lo, hi) + 0.5
    c, e = (a + b) / 2, (b - a) / 2

    def fn(p):
        q = np.abs(p - c) - e
        return np.linalg.norm(np.maximum(q, 0), axis=1) + np.minimum(q.max(axis=1), 0)
    return Shape(fn, a, b)


def ellipsoid(c, radii):
    c, r = np.asarray(c, float), np.asarray(radii, float)
    return Shape(lambda p: (np.linalg.norm((p - c) / r, axis=1) - 1) * r.min(), c - r, c + r)


def sphere(c, r):
    c = np.asarray(c, float)
    return Shape(lambda p: np.linalg.norm(p - c, axis=1) - r, c - r, c + r)


def torus(c, R, r):
    """A horizontal ring round the vertical axis through c: ring radius R, tube radius r (0.75+ for a closed ring)."""
    c = np.asarray(c, float)

    def fn(p):
        rho = np.hypot(p[:, 0] - c[0], p[:, 2] - c[2])
        return np.hypot(rho - R, p[:, 1] - c[1]) - r
    e = np.array([R + r, r, R + r])
    return Shape(fn, c - e, c + e)


def loft(cx, cz, y0, y1, profile):
    """A solid of revolution round the vertical axis (cx, cz) from the cell y0 to the cell y1: profile(t) is the radius
    at t = 0 (y0) .. 1 (y1), or a list of (t, radius) points joined by straight lines."""
    if not callable(profile):
        ts, rs = zip(*profile)
        prof = lambda t: np.interp(t, ts, rs)   # noqa: E731
    else:
        prof = profile
    span = max(y1 - y0, EPS)

    def radius(y):
        return np.maximum(prof(np.clip((y - y0) / span, 0.0, 1.0)), 0.0)

    def fn(p):
        y = p[:, 1]
        rho = np.hypot(p[:, 0] - cx, p[:, 2] - cz)
        slope = (radius(y + 0.05) - radius(y - 0.05)) / 0.1
        side = (rho - radius(y)) / np.sqrt(1 + slope * slope)
        return np.maximum(side, np.maximum(y0 - 0.5 - y, y - y1 - 0.5))
    rmax = float(np.max(radius(np.linspace(y0, y1, 64))))
    return Shape(fn, (cx - rmax, y0 - 0.5, cz - rmax), (cx + rmax, y1 + 0.5, cz + rmax))


def cylinder(cx, cz, r, y0, y1):
    return loft(cx, cz, y0, y1, lambda t: np.full_like(t, r, dtype=float))


def cone(cx, cz, r, y0, y1):
    """Radius r at the cell y0 to a point at y1."""
    return loft(cx, cz, y0, y1, lambda t: r * (1 - t))


def spire(cx, cz, r, y0, y1, kind="pointed"):
    """A spire from radius r at y0 to a point at y1. kind "pointed": the gothic pointed-arch section (two arcs, full
    at the foot, sharp at the tip - a needle with a belly); "ogee": an S-curve (swelling then drawn out to a fine
    point); "cone": straight."""
    h = max(y1 - y0, 1)
    if kind == "pointed":
        rho = (h * h + r * r) / (2.0 * r)          # the arc through (r, 0) and (0, h), centred on the base line

        def prof(t):
            return (r - rho) + np.sqrt(np.maximum(rho * rho - (t * h) ** 2, 0))
    elif kind == "ogee":
        def prof(t):
            return r * 0.5 * (1 + np.cos(np.pi * t))
    else:
        def prof(t):
            return r * (1 - t)
    return loft(cx, cz, y0, y1, prof)


def onion_dome(cx, cz, y0, r, h, base=0.6, waist=0.35, neck=1.8):
    """An onion: from base * r at y0 it swells to r at waist * h, then an ogee draws it in - a round shoulder, a
    hollowed neck (neck > 1 deepens it) - to a fine point at y0 + h."""
    def prof(t):
        lo = r * np.sqrt(np.maximum(1 - ((waist - t) / waist) ** 2 * (1 - base * base), 0))
        s = np.clip((t - waist) / (1 - waist), 0, 1)
        hi = r * (0.5 * (1 + np.cos(np.pi * s))) ** neck
        return np.where(t < waist, lo, hi)
    return loft(cx, cz, y0, y0 + h, prof)


def curved_roof(x0, x1, y0, height, cz, half_width, curve=1.0):
    """A roof running along x (cells x0..x1): eaves at y0, z = cz -+ half_width, ridge at y0 + height over cz. curve 1
    is a straight gable, above 1 a swept roof (steep at the ridge, flaring flat to the eaves - the pagoda line), below 1
    a bowed (convex) one."""
    hw = half_width + 0.5

    def top(z):
        t = np.clip(np.abs(z - cz) / hw, 0, 1)
        return y0 + 0.5 + height * (1 - t) ** curve

    def fn(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        slope = (top(z + 0.05) - top(z - 0.05)) / 0.1
        roof = (y - top(z)) / np.sqrt(1 + slope * slope)
        return np.maximum.reduce([roof, y0 - 0.5 - y, np.abs(z - cz) - hw,
                                  np.maximum(x0 - 0.5 - x, x - x1 - 0.5)])
    return Shape(fn, (x0 - 0.5, y0 - 0.5, cz - hw), (x1 + 0.5, y0 + height + 0.5, cz + hw))


def radial_ribs(cx, cz, n, width, y0, y1, r, phase=0.0):
    """n thin vertical fins radiating from the axis (cx, cz) out to radius r, width blocks thick (intersect them with a
    dome's skin for ribs)."""
    step = 2 * math.pi / n

    def fn(p):
        dx, dz = p[:, 0] - cx, p[:, 2] - cz
        a = np.arctan2(dz, dx) - phase
        off = np.remainder(a + step / 2, step) - step / 2
        rho = np.hypot(dx, dz)
        fin = np.where(np.cos(off) > 0, rho * np.abs(np.sin(off)), rho) - width / 2
        return np.maximum.reduce([fin, rho - r, y0 - 0.5 - p[:, 1], p[:, 1] - y1 - 0.5])
    return Shape(fn, (cx - r, y0 - 0.5, cz - r), (cx + r, y1 + 0.5, cz + r))


# ============================================================================================ voxels
DIRS = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def _erode(a):
    """One 26-neighbour erosion step of a boolean grid (outside the grid counts as empty)."""
    p = np.pad(a, 1)
    out = a.copy()
    n = a.shape
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                out &= p[1 + dx:1 + dx + n[0], 1 + dy:1 + dy + n[1], 1 + dz:1 + dz + n[2]]
    return out


def _fill(d, n):
    """How full a cell is (0..1) when the surface lies d from its centre along the unit normal n (the plane's linear
    estimate)."""
    return np.clip(0.5 - d / np.abs(n).sum(axis=1), 0, 1)


def voxelize(shape, shell=None, edges=True, around=()):
    """{(x, y, z): Cell} for a shape - full blocks, with stairs and slabs on its slopes when edges (see the module
    doc). shell: a hollow of that thickness. around: cells already built that count as filled for the edge pass (a
    stair is not set where something stands on it)."""
    lo = np.floor(shape.lo).astype(int) - 1
    hi = np.ceil(shape.hi).astype(int) + 1
    axes = [np.arange(lo[i], hi[i] + 1) for i in range(3)]
    gx, gy, gz = np.meshgrid(*axes, indexing="ij")
    pts = np.stack([gx.ravel(), gy.ravel(), gz.ravel()], axis=1).astype(float)
    inside = (shape(pts) < -EPS).reshape(gx.shape)
    keep = inside
    if shell:
        core = inside
        for _ in range(int(shell)):
            core = _erode(core)
        keep = inside & ~core
    cells = {tuple(int(v) for v in pts[i]): FULL for i in np.flatnonzero(keep.ravel())}
    if not edges or not cells:
        return cells
    around = set(around)

    def occ(c):
        return c in cells or c in around

    # every solid cell open above or below, and every empty cell above or below one: their normals and fills
    cand = set()
    for (x, y, z) in cells:
        for dy in (1, -1):
            q = (x, y + dy, z)
            if not occ(q):
                cand.add((x, y, z))
                cand.add(q)
    cand = sorted(cand)
    if not cand:
        return cells
    c = np.asarray(cand, float)
    d = shape(c)
    grad = np.stack([shape(c + e * H) - shape(c - e * H) for e in np.eye(3)], axis=1)
    norm = np.linalg.norm(grad, axis=1, keepdims=True)
    n = np.divide(grad, norm, out=np.tile([0.0, 1.0, 0.0], (len(c), 1)), where=norm > EPS)
    fill = _fill(d, n)
    info = {cand[i]: (n[i], fill[i]) for i in range(len(cand))}

    out = dict(cells)
    for pos, (nv, f) in info.items():
        x, y, z = pos
        vs = 1 if nv[1] >= 0 else -1
        half = "bottom" if vs > 0 else "top"
        v = (x, y + vs, z)
        if pos in cells:
            if occ(v):
                continue
            if abs(nv[1]) > FLAT:
                if f < 0.7:
                    out[pos] = Cell("slab", None, half)
            elif abs(nv[1]) > STEEP:
                hd = (int(np.sign(nv[0])), 0) if abs(nv[0]) >= abs(nv[2]) else (0, int(np.sign(nv[2])))
                if occ((x + hd[0], y, z + hd[1])):
                    continue
                facing = OPP[DIRS[hd]]
                if f < STAIR:
                    out[pos] = Cell("stairs", facing, half)
                elif v in info and 0.35 <= info[v][1] < 0.5 and not occ((x + hd[0], y + vs, z + hd[1])):
                    out[v] = Cell("stairs", facing, half)
        else:
            # an empty cell outside the shape (not a hollow's inside), resting on (or hanging under) a full block of a
            # near-flat surface: a slab if about half filled
            below = (x, y - vs, z)
            if abs(nv[1]) > FLAT and below in cells and 0.3 <= f < 0.5 and pos not in out:
                out[pos] = Cell("slab", None, half)
    return out


# ============================================================================================ building
_TRIM = (("bricks", "brick"), ("tiles", "tile"), ("_planks", ""), ("_block", ""))


def variants(block):
    """(stairs, slab) ids for a full block's id by vanilla's naming (deepslate_tiles -> deepslate_tile_stairs)."""
    b = block
    for suf, rep in _TRIM:
        if b.endswith(suf):
            b = b[: -len(suf)] + rep
            break
    return b + "_stairs", b + "_slab"


def build(target, shape, role, shell=None, edges=True, at=(0, 0, 0), stairs=None, slab=None):
    """Voxelizes shape into target at offset `at`. target: a gothic_parts.Part (role, role_st and role_slab looked up
    in its palette; a role without _st / _slab there gets full blocks for its stairs and loses its added slabs) or
    anything with .set(x, y, z, name, **props) and a .b or .blocks dict (gen_structures.Structure, _SunRig): there role
    is a block id and stairs / slab default to its vanilla variants. Returns the cells placed (shape coordinates)."""
    pal = getattr(target, "pal", None)
    if pal is not None:
        st = stairs or (role + "_st" if role + "_st" in pal else None)
        sl = slab or (role + "_slab" if role + "_slab" in pal else None)
    else:
        st0, sl0 = variants(role)
        st, sl = stairs or st0, slab or sl0
    built = getattr(target, "b", None)
    if built is None:
        built = getattr(target, "blocks", {})
    ox, oy, oz = at
    around = {(x - ox, y - oy, z - oz) for (x, y, z) in built}
    cells = voxelize(shape, shell, edges, around)
    keys = list(cells)
    inside = dict(zip(keys, shape(np.asarray(keys, float)) < 0)) if keys else {}
    for (x, y, z), c in cells.items():
        p = (x + ox, y + oy, z + oz)
        if c.kind == "stairs" and st:
            target.set(*p, st, facing=c.facing, half=c.half, shape="straight", waterlogged="false")
        elif c.kind == "slab" and sl:
            target.set(*p, sl, type=c.half, waterlogged="false")
        elif c.kind == "full" or inside[(x, y, z)]:     # no variant for this role: the shape's own cells stay full
            target.set(*p, role)
    return cells


# ============================================================================================ demo
def _samples():
    import gothic_parts as G
    out = []

    # an onion dome on a drum: windowed drum, a string course, the bulb hollow, a gold finial
    p = G.Part(G.palette("teal", "pale"))
    build(p, cylinder(0, 0, 4.5, 0, 6), "wall", shell=1)
    build(p, torus((0, 6, 0), 4.6, 0.8), "trim")
    build(p, onion_dome(0, 0, 7, 7, 15), "roof", shell=1)
    top = max(y for (x, y, z) in p.b if (x, z) == (0, 0))
    p.set(0, top + 1, 0, "gold")
    p.set(0, top + 2, 0, "spike", facing="up")
    out.append(("shape_onion_dome", p))

    # a ribbed dome: a slate hemisphere with eight pale ribs inlaid in it, on a drum with a cornice ring
    p = G.Part(G.palette("slate", "pale"))
    build(p, cylinder(0, 0, 6.5, 0, 5), "wall", shell=1)
    build(p, torus((0, 5, 0), 6.5, 0.9), "trim")
    dome = sphere((0, 6, 0), 7) & box((-8, 6, -8), (8, 14, 8))
    build(p, dome, "needle", shell=1)
    ribs = radial_ribs(0, 0, 8, 1.2, 6, 14, 9) & dome
    for pos in voxelize(ribs, shell=1):          # the ribs replace the dome's own cells, flush with it
        p.clear(*pos)
    build(p, ribs, "trim", shell=1)
    p.set(0, 14, 0, "gold")
    out.append(("shape_ribbed_dome", p))

    # a pointed (gothic arch section) slate spire on an octagonal-ish base with an ogee lantern cap below it
    p = G.Part(G.palette("slate", "grey"))
    build(p, cylinder(0, 0, 4.5, 0, 7), "wall", shell=1)
    build(p, torus((0, 7, 0), 4.6, 0.8), "trim")
    build(p, spire(0, 0, 5.5, 8, 30, "pointed"), "needle", shell=1)
    p.set(0, 30, 0, "gold")
    p.set(0, 31, 0, "spike", facing="up")
    out.append(("shape_pointed_spire", p))

    # a tapering round tower (a slight batter, then a corbelled lip) under an overhanging red cone
    p = G.Part(G.palette("red", "warm"))
    build(p, loft(0, 0, 0, 16, [(0, 5.5), (0.85, 4.6), (1, 4.6)]), "wall", shell=1)
    build(p, torus((0, 12, 0), 4.9, 0.7), "band")
    build(p, torus((0, 16, 0), 5.3, 0.9), "trim")
    build(p, cone(0, 0, 6.8, 17, 29), "roof", shell=1)
    p.set(0, 29, 0, "gold")
    p.set(0, 30, 0, "spike", facing="up")
    out.append(("shape_round_tower_cone", p))

    # a swept (pagoda-line) roof over a walled hall, gables closed by the shell
    p = G.Part(G.palette("teal", "warm"))
    build(p, box((-8, 0, -4), (8, 4, 4)), "wall", shell=1)
    build(p, curved_roof(-10, 10, 5, 7, 0, 7, curve=1.8), "roof", shell=1)
    out.append(("shape_swept_roof", p))
    return out


if __name__ == "__main__":
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out_dir, exist_ok=True)
    for key, part in _samples():
        rig = part.to_rig(key)
        path = os.path.join(out_dir, key + ".nbt")
        rig.structure().write(path)
