#!/usr/bin/env python3
"""Stormcallers vehicle lab (the Thunder Arsenal) - massing options for the blind greybox comparison (Oct 31 2026).

The r12 method (PR #411 Engine Shed 3-4 -> RTS 7; #456 Sunforged 4 -> 5.5; #457 Verdant 3 -> 6; #458 Gravebound 4 -> 6):
ONE primary reference whose scale fits the plot, imitated closely; the silhouette settled by a blind critic on real
eyes stills with the old build as the CONTROL; detail only after. Reference: ref08 (design/refs/vehicle_labs/
ref08_crag_compound_from_above.png) - a walled compound seen from high above like our camera: multi-tier green hip
roofs with flared eaves, a pavilion at the wall angles, an enclosed court, ONE tall keep as the landmark, conifers
breaking the outline. Parts only: ref42 (the pagoda's stacked eaves - the keep), ref27 (the open armillary crown at
L3). The designer pass (API run itachi2-api/runs/20261030-234157-storm-design) studied it and wrote options A, B, C.

Greybox only: role blocks, no ornament. Roles (the designer's palette by zone, by look): prismarine-brick roof fields
(measured 0.55-0.63, hue 159, sat 0.32), dark-prismarine eave lips (0.34-0.39) with a dark-oak underside, dark-oak
timber walls on a stone-brick / mossy plinth, the old lab's red colonnade with white panels on the south face (KEEP),
mud-brick court paving (warm mid), spruce conifers, a stone pagoda spire and an open ring crown at the top.

Plot 17 x 17 (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks north-east from the south-west and
sees the tops, the west face x 0 - the front - and the south face z 16). Hard rules (asserted): height <= 26
(y 0..25); the exit lane x 0..9, z 6..10, y 1..8 empty; the spawn column (8, y, 8) air to the sky; no floating blocks;
no sealed pockets (tools/audit_structure_pockets.py).

  A  ref08's ring, the keep on the gate axis (the designer's pick): a gatehouse on the west (x 0..2, two stacked
     eaves, ridge 13) over the lane; an open court x 3..9, z 4..12 (the yard, the spawn in it); the keep x 10..16,
     z 5..11 at the court's east end - three pagoda eaves (7 x 7 at 8, 5 x 5 at 12, a 5 x 5 hip at 16 to a point at
     18), a spire and the ring crown to 25; a north range (ridge 10), the low south colonnade range (ridge 8), a
     two-tier NW pavilion, a low SW pavilion, a two-tier SE pavilion, a conifer garden in the NE.
  B  the corner pagoda: a four-eave pagoda in the south-east corner (x 10..16, z 10..16), where both camera faces show
     it, to a point at 19 and the crown to 25; a long arsenal hall along the north (ridge 10), an east range, the
     gatehouse, the south colonnade (x 3..9), a SW pavilion; the court x 3..10, z 4..12.
  C  the L-hall with a gallery tower: a ref17 tower in the north-east (x 10..16, z 0..6: a pent eave at 9, a gallery
     ring at 14-15, a flared cap to 20, the crown to 25), a north hall and an east hall making an L, a freestanding
     paifang gate on the west, the south colonnade; the court x 2..10, z 5..13.
  control: design/vehicle_labs/storm_l3_round1.nbt (the old lab, tools/lab_vehicle_storm.py before Oct 31; 4/10 from
     the round-1 critic, Oct 28).

    python3 tools/storm_massing.py          # writes design/vehicle_labs/massing/storm_{a,b,c}.nbt and checks them
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import storm_lab_parts as P  # noqa: E402

SX = SZ = 17
H_MAX = 26
LANE_X, LANE_Z, LANE_H = range(0, 10), range(6, 11), 8
SPAWN = (8, 8)
OUT = "design/vehicle_labs/massing/storm_%s.nbt"
DRY = dict(waterlogged="false")

FIELD_ST, FIELD, FIELD_SL = "prismarine_brick_stairs", "prismarine_bricks", "prismarine_brick_slab"
LIP_ST = "dark_prismarine_stairs"
UNDER = "dark_oak_slab"
TIMBER = "dark_oak_planks"
PLINTH = "stone_bricks"
MOSS = "mossy_stone_bricks"
PAVE = "stone_bricks"
COURT = "mud_bricks"
RED = "stripped_mangrove_log"
PANEL = "white_stained_glass"
SPIRE = "stone_brick_wall"
RING = "prismarine_wall"
RING_FLAT = "dark_prismarine_slab"
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def guard(x, y, z):
    return (x in LANE_X and z in LANE_Z and 1 <= y <= LANE_H) or ((x, z) == SPAWN and y >= 1)


def canvas():
    return P.Canvas(SX, H_MAX, SZ, guard=guard)


# ============================================================================================ masses
def body(c, x1, x2, z1, z2, y1, y2, plinth=2):
    """A solid mass: a stone plinth (mossy in places) to y plinth, dark-oak timber above."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(y1, y2 + 1):
                if not c.free(x, y, z):
                    continue
                if y <= plinth:
                    c.put(x, y, z, MOSS if P.hsh(x, z, y) % 4 == 0 else PLINTH)
                else:
                    c.put(x, y, z, TIMBER)


def hip(c, x1, x2, z1, z2, y, rings=99, soft=True):
    """A flared hip roof, its lip the ring [x1..x2] x [z1..z2] at y (clipped to the plot): ring 0 the dark-prismarine
    lip with upturned corner tips and a dark-oak underside, each ring in one higher in prismarine-brick stairs, to a
    ridge line / a peaked pair / a point. `rings` stops it early (a pent eave round a body that rises through it).
    Inside every ring a hidden solid fill (no pockets). Returns the top y drawn."""
    def put(x, yy, z, name, **p):
        if soft and not c.free(x, yy, z):
            return
        c.put(x, yy, z, name, **p)

    k, yy, top = 0, y, y
    while k < rings:
        a1, a2, b1, b2 = x1 + k, x2 - k, z1 + k, z2 - k
        if a1 > a2 or b1 > b2:
            break
        w, d = a2 - a1 + 1, b2 - b1 + 1
        st = LIP_ST if k == 0 else FIELD_ST
        top = yy
        if min(w, d) == 1:
            for x in range(a1, a2 + 1):
                for z in range(b1, b2 + 1):
                    put(x, yy, z, FIELD)
            break
        if min(w, d) == 2:
            for x in range(a1, a2 + 1):
                for z in range(b1, b2 + 1):
                    f = ("north" if z == b1 else "south") if w >= d else ("west" if x == a1 else "east")
                    put(x, yy, z, st, facing=OPP[f], half="bottom", shape="straight", **DRY)
            break
        for (x, z, f) in P.rect_ring(a1, a2, b1, b2):
            if f is None:
                fz = "south" if z == b1 else "north"
                put(x, yy, z, st, facing=fz, half="bottom", shape="straight", **DRY)
                if k == 0:
                    put(x, yy + 1, z, st, facing=OPP[fz], half="top", shape="straight", **DRY)
            else:
                put(x, yy, z, st, facing=OPP[f], half="bottom", shape="straight", **DRY)
            if k == 0 and c.free(x, yy - 1, z):
                c.slab(x, yy - 1, z, UNDER, "top")
        for x in range(a1 + 1, a2):
            for z in range(b1 + 1, b2):
                put(x, yy, z, TIMBER)
        k += 1
        yy += 1
    return top


def colonnade(c, x1, x2, z, y1=1, y2=5):
    """The KEPT face: the old lab's red colonnade with white panels on the south face z - red posts every other
    cell, white panels between (y1 + 1 .. y2 - 1), a stone sill and a dark-oak lintel."""
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            if x % 2 == 0 or x in (x1, x2):
                c.log(x, y, z, RED)
            elif y == y1:
                c.put(x, y, z, PLINTH)
            elif y == y2:
                c.log(x, y, z, "dark_oak_log", axis="x")
            else:
                c.put(x, y, z, PANEL)


CROWN_Y = 23


def spire_and_crown(c, cx, cz, y0):
    """The pagoda's spire (a stone post from y0 up to the crown) and the L3 crown: an open vertical ring (radius 2) in
    the x-y plane round a lodestone at y 23, a flat ring (radius 2) at the same height - mass-less from above (ref27);
    the ring's top at 25, the plot's cap."""
    cy = CROWN_Y
    for y in range(y0, cy - 2):
        c.put(cx, y, cz, SPIRE, up="true", north="none", south="none", east="none", west="none", **DRY)
    for (u, v) in ((-2, -1), (-2, 0), (-2, 1), (2, -1), (2, 0), (2, 1), (-1, -2), (0, -2), (1, -2), (-1, 2), (0, 2),
                   (1, 2)):
        c.put(cx + u, cy + v, cz, RING, up="true", north="none", south="none", east="none", west="none", **DRY)
    for (u, w) in ((-2, -1), (-2, 1), (2, -1), (2, 1), (-1, -2), (0, -2), (1, -2), (-1, 2), (0, 2), (1, 2)):
        c.slab(cx + u, cy, cz + w, RING_FLAT, "bottom")
    c.chain(cx, cy - 1, cz)
    c.put(cx, cy, cz, "lodestone")
    c.chain(cx, cy + 1, cz)


def ground(c, court):
    for x in range(SX):
        for z in range(SZ):
            c.put(x, 0, z, COURT if (x, z) in court else PAVE)


def gatehouse(c):
    """The west gatehouse over the lane (x 0..2, z 4..12): walls to 9 with the 5 x 8 opening, red jamb posts, a lower
    pent eave (lip y 10, x 0..3), an upper storey and the upper hip (lip y 12) to its ridge at 13."""
    body(c, 0, 2, 4, 12, 1, 9)
    for z in (5, 11):
        for y in range(1, 9):
            c.log(0, y, z, RED)
    hip(c, 0, 3, 4, 12, 10, rings=1)
    body(c, 1, 2, 5, 11, 10, 11)
    hip(c, 0, 3, 5, 11, 12)


def spruce(c, x, z, h):
    P.spruce_tree(c, x, 1, z, h=h)


# ============================================================================================ options
def build_a():
    """A: ref08's ring, the keep on the gate axis."""
    c = canvas()
    court = {(x, z) for x in range(3, 10) for z in range(4, 13)}
    ground(c, court)
    for x in range(10, 17):
        for z in range(0, 5):
            c.put(x, 0, z, "grass_block")
    # the keep (centre (13, 8)): 7 x 7 eave at 8, 5 x 5 pent at 12, a 5 x 5 hip at 16 to a point at 18
    body(c, 11, 15, 6, 10, 1, 7)
    hip(c, 10, 16, 5, 11, 8, rings=2)
    body(c, 12, 14, 7, 9, 9, 11)
    hip(c, 11, 15, 6, 10, 12, rings=1)
    body(c, 12, 14, 7, 9, 12, 15)
    top = hip(c, 11, 15, 6, 10, 16)
    spire_and_crown(c, 13, 8, top + 1)
    gatehouse(c)
    # the NW pavilion (two tiers), the north range
    body(c, 0, 3, 0, 3, 1, 6)
    hip(c, 0, 4, 0, 3, 7, rings=1)
    body(c, 1, 2, 1, 2, 7, 9)
    hip(c, 0, 3, 0, 3, 10)
    body(c, 5, 10, 0, 3, 1, 7)
    hip(c, 4, 11, 0, 4, 8)
    # the SE pavilion (two tiers), the SW pavilion (low), the south colonnade range
    body(c, 13, 16, 13, 16, 1, 6)
    hip(c, 12, 16, 12, 16, 7, rings=2)
    body(c, 14, 15, 14, 15, 8, 9)
    hip(c, 13, 16, 13, 16, 10)
    body(c, 0, 3, 13, 16, 1, 5)
    hip(c, 0, 4, 12, 16, 6)
    colonnade(c, 4, 12, 16)
    body(c, 4, 12, 13, 15, 1, 5)
    hip(c, 3, 13, 12, 16, 6)
    # the wall walk east of the court's south side (z 12), the NE conifer garden
    for x in range(11, 17):
        for y in (1, 2, 3):
            if c.free(x, y, 12):
                c.put(x, y, 12, PLINTH)
        if c.free(x, 4, 12):
            c.slab(x, 4, 12, "stone_brick_slab", "bottom")
    for x in range(10, 17):
        for y in (1, 2):
            if c.free(x, y, 4):
                c.put(x, y, 4, PLINTH)
    spruce(c, 12, 1, 11)
    spruce(c, 15, 2, 9)
    return c


def build_b():
    """B: the corner pagoda in the south-east."""
    c = canvas()
    court = {(x, z) for x in range(3, 11) for z in range(4, 13)}
    ground(c, court)
    # the pagoda (centre (13, 13)): eaves 7 x 7 at 6, 5 x 5 pents at 10 and 14, a 5 x 5 hip at 17 to a point at 19
    body(c, 11, 15, 11, 15, 1, 5)
    hip(c, 10, 16, 10, 16, 6, rings=2)
    body(c, 12, 14, 12, 14, 7, 9)
    hip(c, 11, 15, 11, 15, 10, rings=1)
    body(c, 12, 14, 12, 14, 10, 13)
    hip(c, 11, 15, 11, 15, 14, rings=1)
    body(c, 12, 14, 12, 14, 14, 16)
    top = hip(c, 11, 15, 11, 15, 17)
    spire_and_crown(c, 13, 13, top + 1)
    gatehouse(c)
    # the long arsenal hall on the north, the east range
    body(c, 0, 16, 0, 3, 1, 7)
    hip(c, 0, 16, 0, 4, 8)
    body(c, 11, 16, 4, 9, 1, 6)
    hip(c, 10, 16, 4, 9, 7)
    # the south colonnade (x 3..9), the SW pavilion
    colonnade(c, 3, 9, 16)
    body(c, 3, 9, 13, 15, 1, 5)
    hip(c, 3, 10, 12, 16, 6)
    body(c, 0, 2, 13, 16, 1, 5)
    hip(c, 0, 3, 12, 16, 6)
    hip(c, 0, 2, 13, 16, 8)
    return c


def build_c():
    """C: the L-hall with a ref17 gallery tower in the north-east."""
    c = canvas()
    court = {(x, z) for x in range(2, 11) for z in range(5, 14)}
    ground(c, court)
    # the tower (centre (13, 3)): a 7 x 7 pent at 9, a gallery ring at 14 / 15, a flared 5 x 5 cap at 18 to 20
    body(c, 11, 15, 1, 5, 1, 8)
    hip(c, 10, 16, 0, 6, 9, rings=2)
    body(c, 12, 14, 2, 4, 10, 17)
    for (x, z, f) in P.rect_ring(11, 15, 1, 5):
        c.slab(x, 14, z, "dark_oak_slab", "top")
        c.put(x, 15, z, "dark_oak_fence")
    for x in range(12, 15):
        for z in range(2, 5):
            if c.free(x, 13, z):
                c.put(x, 13, z, TIMBER)
    top = hip(c, 11, 15, 1, 5, 18)
    spire_and_crown(c, 13, 3, top + 1)
    # the north hall, the east hall (the L)
    body(c, 0, 9, 0, 4, 1, 7)
    hip(c, 0, 10, 0, 5, 8)
    body(c, 11, 16, 7, 13, 1, 7)
    hip(c, 10, 16, 6, 14, 8)
    # the paifang gate (freestanding posts round the lane), a low wall closing the west side
    for z in (5, 11):
        for x in (0, 1):
            c.put(x, 1, z, "chiseled_stone_bricks")
            for y in range(2, 10):
                c.log(x, y, z, RED)
    for z in range(5, 12):
        for x in (0, 1):
            c.log(x, 9, z, "dark_oak_log", axis="z") if (x, z) not in ((0, 5), (0, 11), (1, 5), (1, 11)) else None
    hip(c, 0, 2, 4, 12, 10)
    for z in (12, 13):
        for y in (1, 2):
            c.put(0, y, z, PLINTH)
    # the south colonnade
    colonnade(c, 2, 10, 16, y2=4)
    body(c, 2, 10, 14, 15, 1, 4)
    hip(c, 1, 11, 13, 16, 5)
    for z in (14, 15, 16):
        for y in (1, 2):
            c.put(0, y, z, PLINTH)
            c.put(1, y, z, PLINTH)
    return c


BUILDERS = {"a": build_a, "b": build_b, "c": build_c}


# ============================================================================================ write + check
def prune(c):
    while True:
        lone = [q for q in c.b if q[1] > 0 and not any((q[0] + a, q[1] + b, q[2] + d) in c.b for a, b, d in
                                                       ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1),
                                                        (0, 0, -1)))]
        if not lone:
            return
        for q in lone:
            del c.b[q]


def write(c, out):
    prune(c)
    c.leaves()
    c.settle()
    c.check_support("storm massing")
    c.size = (SX, max(q[1] for q in c.b) + 1, SZ)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    c.structure().write(out)
    return c


def landmark_lead(n):
    """(top y, top column, tallest column top more than 3 (Chebyshev) from the top column)."""
    top = {}
    for (x, y, z) in n:
        top[(x, z)] = max(top.get((x, z), -1), y)
    h = max(top.values())
    cols = [cc for cc, y in top.items() if y == h]
    mx, mz = sum(q[0] for q in cols) / len(cols), sum(q[1] for q in cols) / len(cols)
    col = min(cols, key=lambda q: (q[0] - mx) ** 2 + (q[1] - mz) ** 2)
    rest = max(y for cc, y in top.items() if max(abs(cc[0] - col[0]), abs(cc[1] - col[1])) > 3)
    return h, col, rest


def check(c, out):
    import audit_structure_pockets as ap
    name = os.path.splitext(os.path.basename(out))[0]
    n = {q: b[0] for q, b in c.b.items()}
    assert c.size[1] <= H_MAX, (name, c.size)
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "the exit lane is narrowed: %s" % busy[:8]
    col = [y for y in range(1, c.size[1]) if (SPAWN[0], y, SPAWN[1]) in n]
    assert not col, "the spawn column is blocked at %s" % col
    one_way, ground_, roof, rooms, note = ap.audit(name, out)
    assert not one_way and not ground_ and "TRAPPED" not in note, (one_way[:8], ground_[:8], note)
    h, tc, rest = landmark_lead(n)
    return "%s %s, %d blocks, no pockets%s | top %d at %s, next outside its zone %d (lead %d)" % (
        name, "x".join(map(str, c.size)), len(n), note, h, tc, rest, h - rest)


def main(argv):
    for o in (argv or sorted(BUILDERS)):
        out = OUT % o
        c = write(BUILDERS[o](), out)
        print(check(c, out))


if __name__ == "__main__":
    main(sys.argv[1:])
