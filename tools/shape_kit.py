#!/usr/bin/env python3
"""SHAPE KIT: curved building masses (domes, spires, cones, tapering towers, swept roofs) described as signed-distance
shapes, combined with booleans and voxelized into a gothic_parts.Part (or a gen_structures.Structure) through palette
ROLES, with stairs and slabs laid on the slopes so a curve reads smooth instead of as a staircase of cubes.

    import shape_kit as sk
    dome = sk.onion_dome(5, 0, 12) - sk.ellipsoid(3, 4, 3, 0, 3, 0)    # shapes: | union, & intersect, - subtract
    part = sk.build(dome, "roof", gp.Part(pal))                         # "roof" -> roof / roof_st / roof_slab
    part = sk.build(shape, lambda x, y, z: "trim" if y % 4 == 0 else "wall", part, at=(0, 6, 0), hollow=1)

    python3 tools/shape_kit.py OUT_DIR      -> one .nbt per demo (render: tools/structure_render.py OUT_DIR/*.nbt --px 32)

CONVENTIONS (gothic_parts): +x east, +z south (toward the RTS camera), y up, y = 0 the floor. Cell (x, y, z) spans
[x - 0.5, x + 0.5] x [y, y + 1] x [z - 0.5, z + 0.5]: a round shape centred on (cx, cz) is centred on that column, so it
comes out square-symmetric. A shape is any f(x, y, z) on numpy arrays that is negative inside, plus a box that holds it;
only its sign and its gradient's direction are used, so a revolved profile need not be an exact distance.

VOXELIZING. Every cell is sampled 4 x 4 x 4; each of its eight octants gets its filled share. The cell takes the block
whose octants match best: full, empty, a slab, or a stair (a stair = its half's slab + the two octants above / below it
on the side it faces - vanilla's shape). The surface normal gates the choice: steeper than ~78 degrees only full or
empty (a wall stays a wall), slabs only below ~60 degrees, no stairs flatter than ~23 degrees (a slab on a crown),
and on a slope a stair wins near-ties (STAIR_BIAS); a
surface facing down (an overhang, an onion's underside) takes top-half stairs and slabs. A stair faces up the slope.
Where two facings tie (exactly on a diagonal) the clockwise one wins, so a shape and its quarter turn match.
hollow=t keeps only the full cells within t of a non-full cell (plus every stair and slab): a closed shell t thick.
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gothic_parts as gp  # noqa: E402

# |normal.y| (1 flat, 0 a wall): below STAIR_MIN full / empty only (a wall stays a wall); slabs from SLAB_MIN; no
# stairs above FLAT (a crown); on a slope a stair's error is cut by STAIR_BIAS (of 8 octants) - a plane through a cell
# fits a stair and a slab (or a full block) equally well, and the stair is what makes the slope read as a slope
STAIR_MIN, SLAB_MIN, FLAT, STAIR_BIAS = 0.2, 0.5, 0.92, 0.6
SUB = 4                         # samples per cell per axis
DIRS = ("north", "east", "south", "west")


# ============================================================================================ shapes
class Shape:
    """f(x, y, z) -> negative inside, on numpy arrays; bounds (x0, x1, y0, y1, z0, z1) holds every inside point."""

    def __init__(self, f, bounds):
        self.f = f
        self.bounds = tuple(float(v) for v in bounds)

    def __call__(self, x, y, z):
        return self.f(x, y, z)

    def __or__(self, o):
        a, b = self.bounds, o.bounds
        return Shape(lambda x, y, z: np.minimum(self.f(x, y, z), o.f(x, y, z)),
                     [min(a[i], b[i]) if i % 2 == 0 else max(a[i], b[i]) for i in range(6)])

    def __and__(self, o):
        a, b = self.bounds, o.bounds
        return Shape(lambda x, y, z: np.maximum(self.f(x, y, z), o.f(x, y, z)),
                     [max(a[i], b[i]) if i % 2 == 0 else min(a[i], b[i]) for i in range(6)])

    def __sub__(self, o):
        return Shape(lambda x, y, z: np.maximum(self.f(x, y, z), -o.f(x, y, z)), self.bounds)

    def move(self, dx=0, dy=0, dz=0):
        b = self.bounds
        return Shape(lambda x, y, z: self.f(x - dx, y - dy, z - dz),
                     (b[0] + dx, b[1] + dx, b[2] + dy, b[3] + dy, b[4] + dz, b[5] + dz))


def box(x0, x1, y0, y1, z0, z1):
    """A box in continuous coordinates (x0 < x1 ...)."""
    c = np.array([x0 + x1, y0 + y1, z0 + z1]) / 2
    h = np.array([x1 - x0, y1 - y0, z1 - z0]) / 2

    def f(x, y, z):
        qx, qy, qz = abs(x - c[0]) - h[0], abs(y - c[1]) - h[1], abs(z - c[2]) - h[2]
        out = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2 + np.maximum(qz, 0) ** 2)
        return out + np.minimum(np.maximum(qx, np.maximum(qy, qz)), 0)
    return Shape(f, (x0, x1, y0, y1, z0, z1))


def cells(x0, x1, y0, y1, z0, z1):
    """The box that is exactly the cells x0..x1, y0..y1, z0..z1 (inclusive), as Part.fill counts them."""
    return box(x0 - 0.5, x1 + 0.5, y0, y1 + 1, z0 - 0.5, z1 + 0.5)


def _rho(x, z, cx, cz, section):
    dx, dz = abs(x - cx), abs(z - cz)
    if section == "square":
        return np.maximum(dx, dz)
    if section == "octagon":
        return np.maximum(np.maximum(dx, dz), (dx + dz) / math.sqrt(2))
    return np.hypot(dx, dz)


def revolve(radius, y0, h, cx=0, cz=0, section="round", rmax=None):
    """A solid of revolution: radius(t) for t = 0 (at y0) .. 1 (at y0 + h), t a numpy array. section: round, square
    or octagon (then 'radius' is the half-width)."""
    rmax = rmax if rmax is not None else float(np.max(radius(np.linspace(0, 1, 257))))

    def f(x, y, z):
        t = np.clip((y - y0) / h, 0, 1)
        return np.maximum(_rho(x, z, cx, cz, section) - radius(t), np.maximum(y0 - y, y - y0 - h))
    return Shape(f, (cx - rmax, cx + rmax, y0, y0 + h, cz - rmax, cz + rmax))


def cylinder(r, y0, h, cx=0, cz=0, section="round"):
    return revolve(lambda t: np.full_like(t, float(r)), y0, h, cx, cz, section, rmax=r)


def cone(r, y0, h, cx=0, cz=0, section="round"):
    """Base radius r at y0, apex at y0 + h."""
    return revolve(lambda t: r * (1 - t), y0, h, cx, cz, section, rmax=r)


def loft(profile, cx=0, cz=0, section="round"):
    """A lofted taper: profile [(y, radius), ...] (y rising), straight between the stations."""
    ys = np.array([p[0] for p in profile], dtype=float)
    rs = np.array([p[1] for p in profile], dtype=float)
    y0, h = ys[0], ys[-1] - ys[0]
    return revolve(lambda t: np.interp(y0 + t * h, ys, rs), y0, h, cx, cz, section, rmax=rs.max())


def ellipsoid(rx, ry, rz, cx=0, cy=0, cz=0):
    def f(x, y, z):
        k = np.sqrt(((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2)
        return (k - 1) * min(rx, ry, rz)
    return Shape(f, (cx - rx, cx + rx, cy - ry, cy + ry, cz - rz, cz + rz))


def sphere(r, cx=0, cy=0, cz=0):
    return ellipsoid(r, r, r, cx, cy, cz)


def onion_dome(r, y0, h, cx=0, cz=0, neck=0.78, belly=0.3):
    """An onion: a neck of neck * r at y0 swelling to r at belly * h, then an S-curve drawn in to a flared point."""
    def radius(t):
        lo = r * (neck + (1 - neck) * np.sin(np.pi / 2 * np.clip(t / belly, 0, 1)))
        s = np.clip((t - belly) / (1 - belly), 0, 1)
        return np.where(t < belly, lo, r * np.cos(np.pi / 2 * s) ** 2)
    return revolve(radius, y0, h, cx, cz, rmax=r)


def spire(r, y0, h, kind="pointed", cx=0, cz=0, section="round"):
    """pointed: a gothic two-centred arch turned round (full-bellied, meeting at a point); ogee: convex low, concave
    high, a flared needle tip; straight: a cone."""
    if kind == "pointed":
        return revolve(lambda t: r * (np.sqrt(4 - 3 * t * t) - 1), y0, h, cx, cz, section, rmax=r)
    if kind == "ogee":
        return revolve(lambda t: r * (1 + np.cos(np.pi * t)) / 2, y0, h, cx, cz, section, rmax=r)
    return cone(r, y0, h, cx, cz, section)


def torus(big_r, r, y, cx=0, cz=0):
    """A ring of tube radius r round the column (cx, cz) at radius big_r, its tube centred at height y."""
    def f(x, yy, z):
        return np.hypot(np.hypot(x - cx, z - cz) - big_r, yy - y) - r
    R = big_r + r
    return Shape(f, (cx - R, cx + R, y - r, y + r, cz - R, cz + R))


def swept_roof(length, width, h, y0, cx=0, cz=0, sweep=1.7, hip=True, kick=1.6, thick=None):
    """A concave East-Asian roof over a length (x) x width (z) footprint: steep at the ridge, flattening toward the
    eaves (height ~ (1 - s) ** sweep), whose last fifth turns UP by `kick` - more at the hip corners, the swept
    corners of a pagoda roof. hip: hipped ends, else gabled. thick: a sheet that thick (vertically) - the overhanging
    eaves, with their underside showing; None: solid underneath (build it hollow)."""
    hw, hl = width / 2, length / 2

    def f(x, y, z):
        sz = abs(z - cz) / hw
        sx = (abs(x - cx) - (hl - hw)) / hw if hip else np.zeros_like(sz)
        s = np.clip(np.maximum(sz, sx), 0, None)
        corner = np.clip(np.minimum(sz, sx), 0, 1) ** 2
        lift = kick * (0.4 + 1.6 * corner) * np.clip((s - 0.75) / 0.25, 0, 1) ** 2
        top = y0 + h * np.clip(1 - s, 0, 1) ** sweep + lift
        bottom = y0 if thick is None else top - thick
        inside = np.maximum(np.maximum(s - 1, bottom - y), abs(x - cx) - hl)
        return np.maximum(y - top, inside)
    lo = y0 if thick is None else y0 - thick
    return Shape(f, (cx - hl, cx + hl, lo, y0 + max(h, 2 * kick), cz - hw, cz + hw))


# ============================================================================================ voxelizing
def _masks():
    """The candidate blocks as octant masks [ox (0 west), oy (0 low), oz (0 north)] and their cells' descriptions."""
    o = np.indices((2, 2, 2))
    side = {"north": o[2] == 0, "south": o[2] == 1, "east": o[0] == 1, "west": o[0] == 0}
    cand = [(("full",), np.ones((2, 2, 2), bool)), (("empty",), np.zeros((2, 2, 2), bool)),
            (("slab", "bottom"), o[1] == 0), (("slab", "top"), o[1] == 1)]
    for half, low in (("bottom", o[1] == 0), ("top", o[1] == 1)):
        for d in DIRS:
            cand.append((("stairs", d, half), low | side[d]))
    return [c for c, _ in cand], np.array([m for _, m in cand], dtype=float)


CANDS, MASKS = _masks()
STAIR_INDEX = {c[1:]: i for i, c in enumerate(CANDS) if c[0] == "stairs"}


def _grid(shape):
    x0, x1, y0, y1, z0, z1 = shape.bounds
    xs = np.arange(math.floor(x0 + 0.5), math.ceil(x1 - 0.5) + 1)
    ys = np.arange(math.floor(y0), math.ceil(y1))
    zs = np.arange(math.floor(z0 + 0.5), math.ceil(z1 - 0.5) + 1)
    return xs, ys, zs


def _octants(shape, xs, ys, zs):
    """Each cell's eight octants' filled share, shape (nx, ny, nz, 2, 2, 2); in y slices to bound memory."""
    off = (np.arange(SUB) + 0.5) / SUB
    sx = (xs[:, None] - 0.5 + off).ravel()
    sz = (zs[:, None] - 0.5 + off).ravel()
    out = np.empty((len(xs), len(ys), len(zs), 2, 2, 2))
    step = max(1, 2_000_000 // max(1, len(sx) * len(sz) * SUB))
    for j in range(0, len(ys), step):
        yy = ys[j:j + step]
        sy = (yy[:, None] + off).ravel()
        X, Y, Z = np.meshgrid(sx, sy, sz, indexing="ij")
        inside = np.asarray(shape(X, Y, Z) < 0, dtype=float)
        h = SUB // 2
        o = inside.reshape(len(xs), 2, h, len(yy), 2, h, len(zs), 2, h).mean(axis=(2, 5, 8))
        out[:, j:j + step] = o.transpose(0, 2, 4, 1, 3, 5)
    return out


def _normal_y(shape, xs, ys, zs):
    """normal.y at each cell centre (central differences): +1 a floor-like top, -1 an underside, 0 a wall."""
    X, Y, Z = np.meshgrid(xs.astype(float), ys + 0.5, zs.astype(float), indexing="ij")
    e = 0.25
    gx = shape(X + e, Y, Z) - shape(X - e, Y, Z)
    gy = shape(X, Y + e, Z) - shape(X, Y - e, Z)
    gz = shape(X, Y, Z + e) - shape(X, Y, Z - e)
    n = np.sqrt(gx * gx + gy * gy + gz * gz)
    return np.where(n > 1e-12, gy / np.maximum(n, 1e-12), 0.0)


def voxelize(shape, edges=True, hollow=0):
    """{(x, y, z): ("full",) | ("slab", half) | ("stairs", facing, half)} for the shape's cells. edges=False: full
    cells only (a cell is in when at least half of it is). hollow=t: a closed shell t cells thick."""
    xs, ys, zs = _grid(shape)
    if not (len(xs) and len(ys) and len(zs)):
        return {}
    occ = _octants(shape, xs, ys, zs)
    err = np.abs(occ[..., None, :, :, :] - MASKS).sum(axis=(-3, -2, -1))      # (nx, ny, nz, K)
    allowed = np.zeros(err.shape, bool)
    allowed[..., :2] = True
    if edges:
        mixed = (occ.min(axis=(-3, -2, -1)) < 1) & (occ.max(axis=(-3, -2, -1)) > 0)
        ny = np.where(mixed, _normal_y(shape, xs, ys, zs), 0.0)
        up, down, gentle = ny >= STAIR_MIN, ny <= -STAIR_MIN, np.abs(ny) < FLAT
        allowed[..., 2] = ny >= SLAB_MIN
        allowed[..., 3] = ny <= -SLAB_MIN
        for i, c in enumerate(CANDS):
            if c[0] == "stairs":
                allowed[..., i] = gentle & (up if c[2] == "bottom" else down)
                err[..., i] -= STAIR_BIAS
    # ties go to the simpler block (candidate order: full, empty, slabs, stairs)
    pick = np.argmin(np.where(allowed, err + np.arange(len(CANDS)) * 1e-9, np.inf), axis=-1)
    out = {}
    for (i, j, k) in zip(*np.nonzero(pick != 1)):
        c = CANDS[pick[i, j, k]]
        if c[0] == "stairs":                     # on an exact diagonal the clockwise facing wins
            nxt = STAIR_INDEX[(gp.cw(c[1], 1), c[2])]
            if allowed[i, j, k, nxt] and abs(err[i, j, k, nxt] - err[i, j, k, pick[i, j, k]]) < 1e-6:
                c = CANDS[nxt]
        out[(int(xs[i]), int(ys[j]), int(zs[k]))] = c
    if hollow:
        out = _hollow(out, hollow)
    return out


def _hollow(vox, t):
    from scipy import ndimage
    full = [p for p, c in vox.items() if c[0] == "full"]
    if not full:
        return vox
    lo = np.min(full, axis=0) - 1
    hi = np.max(full, axis=0) + 1
    grid = np.zeros(tuple(hi - lo + 1), bool)
    for p in full:
        grid[tuple(np.subtract(p, lo))] = True
    depth = ndimage.distance_transform_edt(grid)          # distance to the nearest non-full cell
    return {p: c for p, c in vox.items() if c[0] != "full" or depth[tuple(np.subtract(p, lo))] <= t + 1e-9}


# ============================================================================================ painting
def build(shape, role, target=None, at=(0, 0, 0), pal=None, edges=True, hollow=0):
    """Voxelizes shape into target (a gothic_parts.Part - the default, a new one - or a gen_structures.Structure /
    _SunRig) at offset `at`. role: a palette role, or f(x, y, z) -> role in the shape's own coordinates; a full cell
    takes the role, a stair role + "_st", a slab role + "_slab" (a KeyError if the palette lacks one it needs)."""
    pal = pal or getattr(target, "pal", None) or gp.palette()
    if target is None:
        target = gp.Part(pal)
    is_part = isinstance(target, gp.Part)
    ox, oy, oz = at
    for (x, y, z), c in voxelize(shape, edges, hollow).items():
        r = role(x, y, z) if callable(role) else role
        if c[0] == "stairs":
            key, props = r + "_st", dict(facing=c[1], half=c[2], shape="straight", waterlogged="false")
        elif c[0] == "slab":
            key, props = r + "_slab", dict(type=c[1], waterlogged="false")
        else:
            key, props = r, {}
        name = pal[key]
        if is_part:
            target.set(x + ox, y + oy, z + oz, key, **props)
        else:
            target.set(x + ox, y + oy, z + oz, name, **props)
    return target


# ============================================================================================ demos
def _finial(p, x, y, z, h=2):
    for k in range(h):
        p.set(x, y + k, z, "gold")
    p.set(x, y + h, z, "spike", facing="up")


def demo_onion(pal):
    """An onion dome on a drum: banded round drum, a torus collar, the teal onion (hollow) and a gold finial."""
    p = gp.Part(pal)
    build(cylinder(4.4, 0, 7), lambda x, y, z: "trim" if y in (0, 6) else "wall", p, edges=False, hollow=1)
    build(torus(4.4, 0.9, 7.0), "trim", p)
    onion = onion_dome(6.6, 7, 15, neck=0.68)
    build(onion, "roof", p, hollow=1)
    top = max(y for (x, y, z) in p.b if (x, z) == (0, 0))
    _finial(p, 0, top + 1, 0)
    return p


def demo_ribbed(pal):
    """A ribbed dome: a hemisphere shell, eight pale ribs over dark slate, a lantern-crowned top."""
    p = gp.Part(pal)
    build(cylinder(6.4, 0, 4), "base", p, edges=False, hollow=1)
    dome = ellipsoid(7, 7.5, 7, 0, 4, 0) & box(-8, 8, 4, 12, -8, 8)

    def rib(x, y, z):
        if math.hypot(x, z) < 1.5:
            return "trim"
        a = math.atan2(z, x)
        d = min(abs(math.remainder(a - k * math.pi / 4, 2 * math.pi)) for k in range(8))
        return "trim" if d * math.hypot(x, z) < 0.6 else "needle"
    build(dome, rib, p, hollow=1)
    top = max(y for (x, y, z) in p.b if (x, z) == (0, 0))
    _finial(p, 0, top + 1, 0, 1)
    return p


def demo_spire(pal):
    """A square tower with an octagonal ogee spire and four pointed pinnacle spirelets on its corners."""
    p = gp.Part(pal)
    build(cells(-4, 4, 0, 9, -4, 4), lambda x, y, z: "trim" if y == 9 or abs(x) == abs(z) == 4 else "wall", p,
          edges=False, hollow=1)
    build(spire(4.2, 10, 18, "ogee", section="octagon"), "needle", p, hollow=1)
    for sx in (-4, 4):
        for sz in (-4, 4):
            build(spire(1.3, 10, 6, "pointed", sx, sz), "roof", p)
    top = max(y for (x, y, z) in p.b if (x, z) == (0, 0))
    _finial(p, 0, top + 1, 0)
    return p


def demo_tower(pal):
    """A round tower tapering 6.4 -> 4.8 in three set-offs (a slow taper voxelizes as ragged streaks; set-offs read
    as built), a corbel ring, and a flared cone roof."""
    p = gp.Part(pal)
    build(loft([(0, 6.4), (5, 6.4), (5.01, 5.6), (12, 5.6), (12.01, 4.8), (18, 4.8)]),
          lambda x, y, z: "base" if y < 5 else "trim" if y == 11 else "wall", p, edges=False, hollow=1)
    build(torus(4.8, 0.8, 18.2), "trim", p)
    roof = revolve(lambda t: 6.2 * (1 - t) ** 1.25, 18.5, 12)
    build(roof, "roof", p, hollow=1)
    top = max(y for (x, y, z) in p.b if (x, z) == (0, 0))
    _finial(p, 0, top + 1, 0)
    return p


def demo_swept(pal):
    """A hall under a swept hipped roof: concave slopes from the ridge, eaves kicking out past the walls."""
    p = gp.Part(pal)
    build(cells(-8, 8, 0, 5, -4, 4), lambda x, y, z: "pillar" if abs(x) in (4, 8) and abs(z) == 4 else "wall", p,
          edges=False, hollow=1)
    eaves = swept_roof(25, 17, 9, 6, sweep=1.8, thick=1.4)
    body = swept_roof(25, 17, 9, 6, sweep=1.8) & cells(-8, 8, 6, 20, -4, 4)
    build(eaves | body, "roof", p, hollow=1)
    ridge = max(y for (x, y, z) in p.b if (x, z) == (0, 0))
    for x in range(-4, 5):                     # the hip ridge: (length - width) / 2 either side of the middle
        p.set(x, ridge, 0, "trim")
    for x in (-4, 4):
        p.stair(x, ridge + 1, 0, "east" if x < 0 else "west", role="trim_st")
    return p


DEMOS = [
    ("onion_dome", "onion dome on a drum", demo_onion),
    ("ribbed_dome", "ribbed hemisphere", demo_ribbed),
    ("ogee_spire", "tower with an ogee spire", demo_spire),
    ("tapered_tower", "tapering round tower, cone roof", demo_tower),
    ("swept_roof", "hall under a swept hipped roof", demo_swept),
]


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    pal = gp.palette(os.environ.get("BAT_ROOF", "teal"), os.environ.get("BAT_STONE", "warm"))
    for key, label, make in DEMOS:
        rig = make(pal).to_rig(key)
        rig.structure().write(os.path.join(out, key + ".nbt"))
        print("%-14s %-34s %s" % (key, label, "x".join(map(str, rig.size))))
