#!/usr/bin/env python3
"""A PART LIBRARY for the Brassworks works buildings (first user: the Erecting Shop, tools/lab_vehicle_brass.py).

Same conventions as tools/gothic_parts.py (it IS a gothic_parts.Part underneath): a part is a dict of cells in its own
frame, its FRONT faces SOUTH (+z), y = 0 is its floor, Part.place(other, ox, oy, oz, rot) copies another part in turned
`rot` quarter turns clockwise (rot 1 turns a south front to face WEST). Every block goes through a ROLE that BRASS maps
to a vanilla 1.20.1 id (an id that is not a role passes straight through).

THE BRASSWORKS KIT (tools/lab_machine_shop.py, tools/capitol_foundry_hall.py), as a Victorian railway works builds it:
  - RED BRICK walls with BLUE-BRICK (soot: polished blackstone bricks, deepslate) plinths, bands and voussoirs - the
    polychrome brick of Swindon, Crewe and the Crossness engine house;
  - BRASS = waxed cut copper (housings, drums, caps, rims), VERDIGRIS = waxed oxidized cut copper (ribs, bosses, ridge
    rolls), SLATE = deepslate tiles (roof quarters), DARK OAK (posts, sleepers), IRON (bars, chains, rivet plates);
  - light: warm panes over shroomlight in sealed rooms, standing lanterns on posts and girders, furnace mouths, a
    glowing stack cap with a lit campfire on top (only on stack tops, no unit walks there); never glow on a floor.

THE PARTS
  arch_window(w, h, lit)   a tall round-headed window in a 2-thick brick wall: blue-brick sill, recessed pane, a
                           brick arch head with a blue keystone, glazing bars; a shroomlight behind when lit
  brick_front(L, h, ...)   a wall run: battered blue plinth, pilasters every `bay`, arched windows between, string
                           courses, a corbel-table cornice - no flat run longer than the bay
  pylon(w, d, h, cap)      a gate tower: corner pilasters, a recessed panel with a lit arched window, bands, a
                           corbelled cornice and a brass pyramid cap with a lantern and finial
  gate_arch(r)             the two-order brick arch with the RIVETED ROLLER SHUTTER rolled up in its brass drum, the
                           radial cast-iron fanlight above it and riveted guide channels
  barlow_roof(L, half)     the St Pancras / erecting-shop roof: a single-span arched shell along x, slate quarters,
                           glazed middle half, verdigris ribs every 3, a louvred smoke vent on the ridge
  stack(h, top)            a works chimney: blue plinth, brick shaft with soot bands, corbelled oversailing cap,
                           glowing throat and a smoking campfire
  runway / bridge_crane    riveted soot girders on corbels; the travelling bridge (twin girders, end trucks), the
                           brass trolley, the chain and the hook block with a spreader carrying a locomotive chassis
  flywheel(r)              a mill-engine flywheel standing in the wall plane: brass rim, iron spokes, hub, pedestal
  clock(r)                 a brass-ringed dial with a glowing face and iron hands
  lean_to(L, d, h0, h1)    a sealed aisle with a slate lean-to roof and a glazed strip
  turntable(r)             a roundhouse turntable inlaid in the floor (deck, ring rail, pivot) - layer 0 only
  props                    lamp_post, water_crane, coal_heap, crate_stack, wheelset, buffer_stop
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gothic_parts import Part, hsh, VEC, OPP   # noqa: E402,F401

BRASS = {
    "wall": "bricks", "wall_st": "brick_stairs", "wall_slab": "brick_slab", "wall_wall": "brick_wall",
    "blue": "polished_blackstone_bricks", "blue_st": "polished_blackstone_brick_stairs",
    "blue_slab": "polished_blackstone_brick_slab", "blue_wall": "polished_blackstone_brick_wall",
    "blue_carve": "chiseled_polished_blackstone", "blue_crack": "cracked_polished_blackstone_bricks",
    "base": "deepslate_bricks", "base_st": "deepslate_brick_stairs", "base_slab": "deepslate_brick_slab",
    "base_wall": "deepslate_brick_wall",
    "slate": "deepslate_tiles", "slate_st": "deepslate_tile_stairs", "slate_slab": "deepslate_tile_slab",
    "slate_wall": "deepslate_tile_wall",
    "brass": "waxed_cut_copper", "brass_st": "waxed_cut_copper_stairs", "brass_slab": "waxed_cut_copper_slab",
    "brass_b": "waxed_copper_block", "brass_e": "waxed_exposed_cut_copper", "brass_e_st": "waxed_exposed_cut_copper_stairs",
    "brass_w": "waxed_weathered_cut_copper", "brass_w_st": "waxed_weathered_cut_copper_stairs",
    "verd": "waxed_oxidized_cut_copper", "verd_st": "waxed_oxidized_cut_copper_stairs",
    "verd_slab": "waxed_oxidized_cut_copper_slab",
    "post": "stripped_dark_oak_log", "oak": "dark_oak_planks", "oak_st": "dark_oak_stairs", "oak_slab": "dark_oak_slab",
    "oak_fence": "dark_oak_fence",
    "iron": "iron_block", "bars": "iron_bars", "chain": "chain", "rod": "end_rod",
    "pane": "yellow_stained_glass_pane", "pane_dim": "orange_stained_glass_pane", "glass": "glass",
    "light": "shroomlight", "glow": "ochre_froglight", "band": "polished_blackstone_bricks", "dial": "white_concrete", "coal": "coal_block", "lantern": "lantern",
    # round 2 (the Erecting Shop): PALE STONE dressings on red brick (ref18's white banding) - string courses,
    # voussoirs, copings, sills, quoins - and the clock's parts
    "stone": "polished_diorite", "stone_st": "polished_diorite_stairs", "stone_slab": "polished_diorite_slab",
    "stone_wall": "diorite_wall", "stone_carve": "chiseled_stone_bricks", "stone_brick": "stone_bricks",
    "ring": "polished_blackstone_bricks", "hand": "gold_block", "pane_clear": "glass_pane", "bell": "bell",
}
CAMPFIRE = dict(lit="true", facing="south", signal_fire="false", waterlogged="false")


def new():
    return Part(dict(BRASS))


def chain(p, x, y, z, axis="y"):
    p.set(x, y, z, "chain", axis=axis, waterlogged="false")


def rod(p, x, y, z, facing="up"):
    p.set(x, y, z, "rod", facing=facing)


# ============================================================================================ windows and walls
def arch_window(w=2, h=5, lit=True):
    """A round-headed window opening in a 2-thick wall whose outer face is z = 0 (inner face z = -1), x 0..w-1, sill
    at y = 0: the opening is recessed (air at z 0), the pane sits at z -1 with a shroomlight behind it at z -2 when
    lit; the head is a brick arch (upside-down stairs at the shoulders) under a blue keystone; a blue sill stair
    projects at z 1. Glazing bars: iron bars mid-height across a 2+ wide light."""
    p = new()
    for x in range(w):
        p.stair(x, 0, 1, "north", "top", "blue_st")          # the sill, proud
        p.set(x, 0, 0, "blue")
        for y in range(1, h):
            p.set(x, y, 0, "air")
            p.set(x, y, -1, "pane" if lit else "pane_dim")
            if lit:
                p.set(x, y, -2, "light")
        # the arch head
        if w == 1:
            p.set(x, h, 0, "wall")
        elif x == 0:
            p.stair(x, h - 1, 0, "west", "top", "wall_st")
        elif x == w - 1:
            p.stair(x, h - 1, 0, "east", "top", "wall_st")
    for x in range(w):
        p.set(x, h, 0, "blue" if x in (w // 2, (w - 1) // 2) else "wall")
    if w >= 2:
        p.stair((w - 1) // 2, h + 0, 1, "north", "top", "blue_st")   # the keystone's drip, proud
    return p


def brick_front(L, h, bay=3, win=(1, 6), y_band=None, lit=True, cornice=True, plinth=2, piers=True):
    """A brick wall face along x 0..L-1, outer face z 0, inner face z -1, floor y 0, top course y h. A blue-brick plinth
    (y 0..plinth-1, battered with stairs at z 1 on the ground course), pilasters (z 1) every `bay` with blue caps,
    a 2-wide arched window in each bay (sill at win[0], `win[1]` high), a blue string course at y_band, a corbel-table
    cornice of upside-down stairs at the top (z 1)."""
    p = new()
    for x in range(L):
        for y in range(h + 1):
            p.set(x, y, 0, "blue" if y < plinth else "wall")
            p.set(x, y, -1, "blue" if y < plinth else "wall")
        p.stair(x, 0, 1, "north", "bottom", "base_st")         # the battered plinth
        if y_band is not None:
            p.set(x, y_band, 0, "blue")
            p.stair(x, y_band, 1, "north", "bottom", "blue_st")
        if cornice:
            p.stair(x, h, 1, "north", "top", "wall_st")
            if x % 2 == 0:
                p.stair(x, h - 1, 1, "north", "top", "blue_st")  # corbels under the cornice
    piers_at = [x for x in range(0, L, bay)] + ([L - 1] if (L - 1) % bay else [])
    if piers:
        for x in piers_at:
            for y in range(plinth, h):
                p.set(x, y, 1, "blue" if y in (plinth, ) or (y_band is not None and y == y_band) else "wall")
            p.set(x, 0, 1, "base")
            p.stair(x, 1, 1, "north", "bottom", "base_st") if plinth > 1 else None
            p.stair(x, 0, 2, "north", "bottom", "base_st")
            p.stair(x, h - 1, 2, "north", "top", "blue_st")
            p.set(x, h - 1, 1, "blue_carve")
    for i in range(len(piers_at) - 1):
        a, b = piers_at[i] + 1, piers_at[i + 1] - 1
        wn = b - a + 1
        if wn < 1:
            continue
        ww = min(2, wn)
        x0 = a + (wn - ww) // 2
        p.place(arch_window(ww, win[1], lit), x0, win[0], 0)
    return p


# ============================================================================================ the gate
def pylon(w=4, d=2, h=16, cap="pyramid", lit=True, seed=0, eave=0):
    """A gate tower, front plane z 0 (body z 0..-d+1), x 0..w-1, h courses: corner pilasters standing proud (z 1) in
    blue brick quoins, a recessed panel with a lit round-headed window, blue bands at 1/3 and 2/3, a corbel table and
    a cornice (z 1), then a brass pyramid cap with a verdigris ridge, a lantern on its finial post. Inside sealed (filled
    low, lit high)."""
    p = new()
    for x in range(w):
        for z in range(-d + 1, 1):
            for y in range(h + 1):
                p.set(x, y, z, "blue" if y < 2 else "wall")
    b1, b2 = h // 3 + 1, 2 * h // 3 + 1
    for x in (0, w - 1):                                       # quoined pilasters
        for y in range(h):
            p.set(x, y, 1, "blue" if (y // 2) % 2 == 0 or y < 2 else "wall")
        p.stair(x, 0, 2, "north", "bottom", "base_st")
    for x in range(w):
        p.stair(x, 0, 1, "north", "bottom", "base_st") if x not in (0, w - 1) else None
        for y in (b1, b2):
            p.set(x, y, 0, "blue")
            if x not in (0, w - 1):
                p.stair(x, y, 1, "north", "bottom", "blue_st")
        p.stair(x, h - 1, 1, "north", "top", "blue_st") if x not in (0, w - 1) else None
        p.stair(x, h, 1, "north", "top", "wall_st")
    if w >= 4:
        # the window in the middle bay, between the bands (lit)
        p.place(arch_window(w - 2, b2 - b1 - 2, lit), 1, b1 + 1, 0)
        p.place(arch_window(w - 2, b1 - 3, lit), 1, 2, 0)
    else:
        for y in range(b1 + 1, b2 - 1):
            p.set(1, y, 0, "air")
            p.set(1, y, -1, "pane")
            if d > 2:
                p.set(1, y, -2, "light")
        p.set(1, b2 - 1, 0, "blue_carve")
    # the cap
    if cap == "pyramid":
        y = h + 1
        lo, hi = -eave, w - 1 + eave
        zlo, zhi = -d + 1 - eave, 1
        k = 0
        while lo <= hi and zlo <= zhi:
            for x in range(lo, hi + 1):
                for z in range(zlo, zhi + 1):
                    edge_x, edge_z = x in (lo, hi), z in (zlo, zhi)
                    if not (edge_x or edge_z):
                        p.set(x, y, z, "brass")
                        continue
                    if edge_x and edge_z:
                        p.set(x, y, z, "verd_slab", type="bottom", waterlogged="false") if k else \
                            p.set(x, y, z, "verd")
                    elif edge_z:
                        p.stair(x, y, z, "south" if z == zlo else "north", "bottom", "brass_st")
                    else:
                        p.stair(x, y, z, "east" if x == lo else "west", "bottom", "brass_st")
            lo, hi, zlo, zhi, y, k = lo + 1, hi - 1, zlo + 1, zhi - 1, y + 1, k + 1
        cx, cz = (w - 1) // 2, (1 - d) // 2
        p.set(cx, y, cz, "verd")
        p.set(cx, y + 1, cz, "blue_wall")
        p.lantern(cx, y + 2, cz)
        rod(p, cx, y + 3, cz)
    return p


def gate_arch(r=3, depth=2):
    """The gate in a wall whose outer face is z 0, centred on x 0, springing at y = r * 2 + ... : the opening is
    x -(r-1)..(r-1), y 1..8 (clear 8 high); a two-order brick arch over it - the inner order (z -1) on the circle of
    radius r round (0, 9), the outer order (z 0, blue and red voussoirs alternating) one ring wider, a brass keystone;
    the radial cast-iron fanlight inside the inner order (bars at z -1, a warm pane at z -2); the ROLLED SHUTTER in
    its brass drum across the springing (y 9, z -1..-2) with a verdigris boss, riveted iron guide channels down the
    jambs (x +-r, z -1). Returns the part; anchors are given by the caller."""
    p = new()
    cy = 9
    for x in range(-r - 2, r + 3):
        for y in range(cy, cy + r + 3):
            d = math.hypot(x, y - cy)
            if r - 0.5 <= d < r + 0.5:
                p.set(x, y, -1, "wall" if (x + y) % 2 else "blue")
            elif r + 0.5 <= d < r + 1.5:
                p.set(x, y, 0, "blue" if int(math.atan2(y - cy, x) * 6) % 2 else "wall")
            elif d < r - 0.5 and y > cy:
                spoke = abs(x) == 0 or abs(x) == y - cy
                p.set(x, y, -1, "bars" if spoke else "air")
                p.set(x, y, -2, "pane")
    p.set(0, cy + r + 1, 0, "brass_b")                       # the keystone
    p.stair(0, cy + r + 2, 0, "north", "bottom", "brass_st")
    # the shutter drum
    for x in range(-r + 1, r):
        p.stair(x, cy, 0, "north", "top", "brass_st")         # its rounded front
        p.set(x, cy, -1, "verd" if x == 0 else "brass_b")
        p.set(x, cy, -2, "iron")                              # the shutter's bottom bar, rolled up
    for s in (-1, 1):                                         # the guide channels
        for y in range(1, cy):
            p.set(s * r, y, -1, "iron" if y % 3 == 0 else "blue_carve" if y % 3 == 1 else "blue")
        p.set(s * r, cy, -1, "iron")
    return p


# ============================================================================================ the roof
def roof_profile(half):
    """Heights above the eaves for |dz| = 0..half+1 of an arched (slightly pointed, St Pancras) roof: steep at the
    springing, flatter at the crown."""
    out = []
    for k in range(half + 2):
        t = 1 - k / (half + 1)
        out.append(int(round((half + 0.6) * math.sin(t * math.pi / 2) ** 0.8)))
    return out


def barlow_roof(L, half=4, glazed=3, rib_every=3, vent=True):
    """The arched roof along x 0..L-1 over a span z -half..half, the eaves (wall tops) at y 0: a stepped shell of
    stairs, slate quarters (|dz| > glazed), glazing over the middle, a verdigris rib every `rib_every` cells along x (and
    at both ends), a louvred smoke vent along the ridge. Returns (part, top height)."""
    p = new()
    prof = roof_profile(half)                    # prof[k] for |dz| = k, prof[half + 1] = 0 (the eave course)
    for x in range(L):
        rib = x % rib_every == 0 or x == L - 1
        for dz in range(-half - 1, half + 1 + 1):
            k = abs(dz)
            t = prof[k]
            lower = prof[k + 1] if k + 1 < len(prof) else -1
            face = "south" if dz < 0 else "north"           # stairs climb toward the ridge
            for y in range(lower + 1, t):
                p.set(x, y, dz, "verd" if rib else ("slate" if k > glazed else "glass"))
            if k == 0:
                p.set(x, t, dz, "verd" if rib else "glass")
                continue
            if rib:
                p.stair(x, t, dz, face, "bottom", "verd_st")
            elif k > glazed:
                p.stair(x, t, dz, face, "bottom", "slate_st")
            else:
                p.set(x, t, dz, "glass")
        # the eaves: an upturned stair gutter
        for s in (-1, 1):
            p.stair(x, 0, s * (half + 2), "north" if s > 0 else "south", "top", "blue_st")
    top = prof[0]
    if not vent:
        return p, top
    p.place(smoke_vent(L), 0, top + 1, 0)
    return p, top + 2


def smoke_vent(L):
    """The louvred smoke vent riding a roof ridge along x 1..L-2 (y 0 = the course over the crown): louvres (bars)
    either side under a brass slab roof, verdigris posts every 3."""
    p = new()
    top = -1
    for x in range(1, L - 1):
        for dz in (-1, 1):
            p.set(x, top + 1, dz, "bars" if x % 3 else "verd")
        p.set(x, top + 1, 0, "air")
        for dz in (-1, 0, 1):
            p.slab(x, top + 2, dz, "bottom", "brass_slab")
        if x % 3 == 0:
            p.slab(x, top + 2, 0, "bottom", "verd_slab")
    return p


# ============================================================================================ stacks
def stack(h=12, w=3, top="glow", rings=()):
    """A works chimney x 0..w-1, z 0..w-1 from y 0: a blue plinth with a battered skirt, a brick shaft banded in soot
    every 4, brass bands, a corbelled oversailing cap (stairs stepping out on all four sides), a glowing throat
    (shroomlight) and a lit campfire smoking on it."""
    p = new()
    for y in range(h):
        for x in range(w):
            for z in range(w):
                edge = x in (0, w - 1) or z in (0, w - 1)
                if not edge and y > 1:
                    p.set(x, y, z, "blue")
                    continue
                if y < 2:
                    nm = "blue"
                elif y % 4 == 0:
                    nm = "blue"
                elif y == h - 3:
                    nm = "brass"
                else:
                    nm = "wall"
                p.set(x, y, z, nm)
    # oversailing cap: stairs out one on all four sides at h-1 (upside down), a blue course on top
    y = h - 1
    for i in range(-1, w + 1):
        for (x, z, f) in ((i, -1, "south"), (i, w, "north"), (-1, i, "east"), (w, i, "west")):
            if 0 <= x < w or 0 <= z < w:
                p.stair(x, y, z, f, "top", "blue_st")
    for x in range(-1, w + 1):
        for z in range(-1, w + 1):
            if x in (-1, w) or z in (-1, w):
                p.slab(x, h, z, "bottom", "blue_slab")
    c = w // 2
    for x in range(w):
        for z in range(w):
            if (x, z) != (c, c):
                p.set(x, h, z, "blue")
    for yr in rings:                                       # corbel rings breaking the shaft (ref17)
        for i in range(w):
            for (x, z, f) in ((i, -1, "south"), (i, w, "north"), (-1, i, "east"), (w, i, "west")):
                p.stair(x, yr, z, f, "top", "blue_st")
                p.slab(x, yr + 1, z, "bottom", "blue_slab")
        for x in range(w):
            for z in range(w):
                if x in (0, w - 1) or z in (0, w - 1):
                    p.set(x, yr, z, "blue")
    p.set(c, h, c, "light" if top == "glow" else "coal")
    p.set(c, h + 1, c, "campfire", **CAMPFIRE)
    return p


# ============================================================================================ the crane
def bridge_crane(span=7, chassis=True, girder_gap=2):
    """The travelling bridge crane, local frame: the bridge spans z 0..span-1 at y 1 (its twin girders at x 0 and
    x girder_gap, riveted brass on soot end trucks at z 0 and span-1), the brass trolley across the girders at y 2 over
    the middle, the hook chain down from it, the hook block at y 0 under the trolley (x mid), and, when `chassis`, a
    locomotive frame slung one under it (y -1): blue frames, a brass boiler barrel, red buffer beams, iron buffers.
    Its y 0 is the runway's top. Returns the part."""
    p = new()
    mx, mz = girder_gap // 2, span // 2
    for z in range(span):
        for x in (0, girder_gap):
            end = z in (0, span - 1)
            p.set(x, 1, z, "blue_carve" if end else ("brass_b" if z % 2 else "brass"))
        if z in (0, span - 1):
            for x in range(1, girder_gap):
                p.set(x, 1, z, "blue")
    # the trolley
    for x in range(-1, girder_gap + 2):
        for z in (mz - 1, mz, mz + 1):
            if x in (-1, girder_gap + 1):
                p.stair(x, 2, z, "east" if x < 0 else "west", "bottom", "brass_st")
            else:
                p.set(x, 2, z, "brass_e" if (x, z) != (mx, mz) else "verd")
    p.slab(mx, 3, mz, "bottom", "verd_slab")
    chain(p, mx, 1, mz)
    p.set(mx, 0, mz, "verd")                                 # the hook block
    if chassis:
        # the spreader under the hook (a dark-oak beam along z) and the chassis slung below it at y -1
        for z in range(mz - 1, mz + 2):
            if z != mz:
                p.set(mx, 0, z, "post", axis="z")
        for z in range(mz - 2, mz + 3):
            for x in range(mx - 1, mx + 2):
                end = z in (mz - 2, mz + 2)
                if end:
                    p.slab(x, -1, z, "top", "red_nether_brick_slab")
                elif x == mx:
                    p.set(x, -1, z, "brass_b" if abs(z - mz) < 2 else "brass")
                else:
                    p.slab(x, -1, z, "top", "blue_slab")
        for z in (mz - 2, mz + 2):
            for x in (mx - 1, mx + 1):
                p.set(x, -1, z + (-1 if z < mz else 1), "iron_trapdoor", facing="north" if z < mz else "south",
                      half="top", open="true", powered="false", waterlogged="false")
    return p


def runway(p, x0, x1, y, z, corbel_face=None):
    """A riveted soot crane runway girder along x at (y, z) with rivet plates; corbels under it every 2 on its wall
    side (upside-down stairs facing `corbel_face`)."""
    for x in range(x0, x1 + 1):
        p.set(x, y, z, "blue_carve" if x % 3 == 0 else "blue")
        if corbel_face and x % 2 == 0:
            p.stair(x, y - 1, z, corbel_face, "top", "blue_st")


# ============================================================================================ the engine
def flywheel(r=3):
    """A flywheel standing in the plane z 0 round (0, 0): a brass rim (blocks at the quarters, stairs between so it
    reads round), iron-bar spokes on 8 rays, an iron hub with a verdigris boss in front, the axle running back (z -1,
    -2) into a dark-oak bearing block."""
    p = new()
    for x in range(-r - 1, r + 2):
        for y in range(-r - 1, r + 2):
            d = math.hypot(x, y)
            if r - 0.5 <= d < r + 0.5:
                p.set(x, y, 0, "brass_b" if (x == 0 or y == 0) else "brass")
            elif 0 < d < r - 0.5:
                a = math.degrees(math.atan2(y, x)) % 45
                if a < 12 or a > 33:
                    p.set(x, y, 0, "bars")
    p.set(0, 0, 0, "iron")
    p.set(0, 0, -1, "iron")
    p.set(0, 0, -2, "post", axis="z")
    return p


def clock(r=1):
    """A dial in the plane z 0 round (0, 0): a brass ring (copper block quarters, verdigris corners), a glowing face, an
    iron hand plate; a blue drip over it at z 1."""
    p = new()
    for x in range(-r - 1, r + 2):
        for y in range(-r - 1, r + 2):
            edge = max(abs(x), abs(y)) == r + 1
            if edge:
                corner = abs(x) == r + 1 and abs(y) == r + 1
                p.set(x, y, 0, "verd" if corner else "brass_b")
            else:
                p.set(x, y, 0, "dial")
    p.set(0, 0, 0, "iron")                                     # the arbor
    p.set(0, 0, 1, "chain", axis="y", waterlogged="false")     # the hands (hour + minute) proud of the face
    p.set(0, 1, 1, "chain", axis="y", waterlogged="false")
    p.stair(0, r + 2, 1, "north", "top", "blue_st")
    return p


def lean_to(L, d, h0, h1, lit=True, bay=3, win_h=4, sill=2):
    """A sealed aisle x 0..L-1, front face z 0, back z -(d-1) against a taller wall: a brick_front, its body filled
    (sealed, never a pocket), a slate lean-to roof from the eaves (h0, over the pilasters at z 1) rising to h1 at the
    back, every fourth course a verdigris batten, the rows' ends finished with blue gable stairs."""
    p = new()
    p.place(brick_front(L, h0, bay=bay, win=(sill, win_h), lit=lit), 0, 0, 0)
    for x in range(L):
        for z in range(-d + 1, -1):
            for y in range(0, h0 + 1):
                p.set(x, y, z, "wall" if y > 1 else "blue")
    rows = list(range(1, -d, -1))                    # z 1 (over the pilasters) back to -(d-1)
    n = len(rows)
    for i, z in enumerate(rows):
        y = h0 + 1 + (i * (h1 - h0)) // max(1, n - 1)
        for x in range(0, L):
            p.stair(x, y, z, "north", "bottom", "verd_st" if x % 4 == 0 else "slate_st")
            for yy in range(h0 + 1, y):
                p.set(x, yy, z, "slate")
    return p


def turntable(p, cx, cz, r=3):
    """A roundhouse turntable inlaid at y 0 round (cx, cz): a ring rail of iron, a dark-oak deck, its two girders
    (blue) along x, a brass pivot."""
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            d = math.hypot(x - cx, z - cz)
            if r - 0.5 <= d < r + 0.5:
                p.set(x, 0, z, "iron" if (x + z) % 2 else "polished_deepslate")
            elif d < r - 0.5:
                if z in (cz - 1, cz + 1):
                    p.set(x, 0, z, "blue")
                else:
                    p.set(x, 0, z, "post", axis="x")
    p.set(cx, 0, cz, "brass_b")


# ============================================================================================ yard props
def lamp_post(p, x, y, z, h=3):
    for k in range(h):
        p.set(x, y + k, z, "blue_wall" if k else "blue")
    p.set(x, y + h, z, "brass")
    p.lantern(x, y + h + 1, z)


def water_crane(p, x, y, z, arm="east", h=4):
    """A cast-iron water crane: a blue plinth, a brass column, the swinging arm (a stair and a slab), the leather bag
    (a chain)."""
    p.set(x, y, z, "base")
    for k in range(1, h):
        p.set(x, y + k, z, "brass_b" if k == 1 else "blue_wall")
    p.set(x, y + h, z, "brass")
    vx, vz = VEC[arm]
    p.stair(x + vx, y + h, z + vz, OPP[arm], "top", "brass_st")
    chain(p, x + vx, y + h - 1, z + vz)


def coal_heap(p, cells, y=1):
    """A heap of coal on the given (x, z) cells: coal blocks, blackstone stairs and slabs round the edge."""
    s = set(cells)
    for (x, z) in cells:
        inner = all((x + a, z + b) in s for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if inner:
            p.set(x, y, z, "coal")
            p.slab(x, y + 1, z, "bottom", "blackstone_slab")
        else:
            out = next(f for f, (a, b) in VEC.items() if (x + a, z + b) not in s)
            p.stair(x, y, z, OPP[out], "bottom", "blackstone_stairs")


# ============================================================================================ relief parts (T1 detail)
# The Engine Shed T1's relief vocabulary (design/vehicle_labs/briefs/brass_t1_checklist.md, part refs ref39-ref44):
# small, literal-block parts a face is built from - a column with a base and a capital, a pinnacle, a framed emblem.
def pilaster(h, shaft="polished_blackstone_bricks", trim="polished_andesite"):
    """A 1-wide column h tall standing in front of its wall (ref39): a trim base, the shaft, a capital of an
    upside-down trim stair overhanging the front (+z), its tall back against the wall."""
    p = new()
    p.set(0, 0, 0, trim)
    for y in range(1, h - 1):
        p.set(0, y, 0, shaft)
    p.stair(0, h - 1, 0, "north", "top", trim + "_stairs")
    return p


def pinnacle(h=3, shaft="polished_blackstone_brick_wall", tip="waxed_cut_copper_slab"):
    """A buttress pinnacle (ref40): a wall shaft h - 1 tall with a brass slab point."""
    p = new()
    for y in range(h - 1):
        p.set(0, y, 0, shaft)
    p.slab(0, h - 1, 0, "bottom", tip)
    return p


def emblem(frame="blackstone", field="quartz_bricks", cross="red_terracotta", recess=True):
    """The pavilion emblem (ref39's pediment panel, ref18's gable star): a 5 x 5 dark frame in the wall's face plane
    (z 0) round a 3 x 3 panel: a red cross on a cream field. recess: the panel set one back (z -1) with air in front
    of it, so placing it carves the wall it lands on; recess=False sets it flush in the frame's plane (round 2: the
    recessed panel read dark-in-dark from the camera)."""
    p = new()
    for x in range(-2, 3):
        for y in range(-2, 3):
            if abs(x) == 2 or abs(y) == 2:
                p.set(x, y, 0, frame)
                continue
            if recess:
                p.set(x, y, 0, "air")
            p.set(x, y, -1 if recess else 0, cross if x == 0 or y == 0 else field)
    return p
