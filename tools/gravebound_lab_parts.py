#!/usr/bin/env python3
"""GRAVEBOUND PARTS: the dark part library for the Charnel Works (tools/lab_vehicle_gravebound.py) and any later
Gravebound building drawn from parts. Built on tools/gothic_parts.py (Part, roles -> palette, place with a quarter turn)
and tools/gothic_towers.py (Mason, plans, spires, windows) with the Gravebound kit as the palette:

  - DEEPSLATE masonry (bricks in the field, tiles in calm zones, cracked where it weathers), POLISHED BLACKSTONE
    BRICKS for plinths, bands and the black spires;
  - BONE: bone-block quoins on every corner, bone ribs and jamb shafts, smooth-quartz stairs as the bone's rounded
    bends (the extractor's trick), skulls on ledges (floor skulls only - wall skulls pop off a moved wall);
  - LIGHT (round 2, amendment item 3): ONLY lit windows (verdant froglight behind lime glass), the rose and the gate's
    soul braziers - no lanterns on posts or pinnacles;
  - GROUND: a PALE crag (calcite / diorite / andesite, ref26) under the dark walls, dark soil, headstones.

Round 2 (parts 13-18, after the 4/10 critique "a dark lump with bone stuck on"): keep (the landmark, ref21's corbelled
crown), pinnacle turret, gate pilaster, rib arc (the rib-vault), brazier, lancet slot - ordered bays and ONE hierarchy.

CONVENTIONS (gothic_parts'): a part's front faces SOUTH (+z), y = 0 is its floor; turn() rotates it a quarter turn
clockwise per step (rot 1: the front faces WEST) and turns skull / banner `rotation` too, which Part.rotated does not.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gothic_parts as GP          # noqa: E402
import gothic_towers as GT         # noqa: E402
from gothic_parts import Part, VEC, OPP, toward   # noqa: E402

LANTERN = dict(hanging="false", waterlogged="false")
DRY = dict(waterlogged="false")
BONE_ST = "smooth_quartz_stairs"
BONE_SLAB = "smooth_quartz_slab"

# ============================================================================================ the palette
GB = dict(GP.palette("slate", "warm"))
GB.update({
    "wall": "deepslate_bricks", "wall_alt": "deepslate_tiles", "wall_moss": "cracked_deepslate_bricks",
    "quoin": "polished_blackstone_bricks", "trim": "polished_deepslate", "trim_st": "polished_deepslate_stairs",
    "trim_slab": "polished_deepslate_slab", "carve": "chiseled_deepslate", "pillar": "bone_block",
    "rail": "deepslate_tile_wall", "quartz_st": BONE_ST,
    "band": "polished_blackstone_bricks", "band_st": "polished_blackstone_brick_stairs",
    "band_slab": "polished_blackstone_brick_slab",
    "base": "polished_blackstone_bricks", "base_alt": "cracked_polished_blackstone_bricks",
    "base_st": "polished_blackstone_brick_stairs", "base_slab": "polished_blackstone_brick_slab",
    "base_wall": "polished_blackstone_brick_wall",
    "roof": "deepslate_tiles", "roof_st": "deepslate_tile_stairs", "roof_slab": "deepslate_tile_slab",
    "roof_wall": "deepslate_tile_wall",
    "needle": "polished_blackstone_bricks", "needle_st": "polished_blackstone_brick_stairs",
    "needle_slab": "polished_blackstone_brick_slab", "needle_wall": "polished_blackstone_brick_wall",
    "accent": "bone_block", "accent_st": BONE_ST, "accent_slab": BONE_SLAB,
    "glass": "lime_stained_glass_pane", "win_lit": "lime_stained_glass_pane", "win_dark": "black_stained_glass_pane",
    "slit": "black_stained_glass_pane", "bars": "iron_bars", "light": "verdant_froglight",
    "lantern": "soul_lantern", "spike": "polished_blackstone_wall", "gold": "chiseled_polished_blackstone", "chain": "chain",
    "floor": "polished_deepslate", "carpet": "green_carpet", "shelf": "bookshelf", "candle": "white_candle",
    "timber": "dark_oak_log", "timber_beam": "dark_oak_log", "timber_st": "dark_oak_stairs",
    "timber_slab": "dark_oak_slab", "timber_fence": "dark_oak_fence", "door": "iron_door",
    "banner": "black_banner", "banner_top": "black_banner",
    "rock1": "deepslate", "rock2": "cobbled_deepslate", "rock3": "tuff", "rock4": "blackstone",
    "rock5": "cobbled_deepslate", "grass": "podzol", "moss": "coarse_dirt", "path": "deepslate_tiles",
    "soil1": "podzol", "soil2": "coarse_dirt", "soil3": "rooted_dirt", "soil4": "soul_soil",
})


def pal():
    return dict(GB)


def turn(part, rot):
    """Part.rotated, plus skull / banner `rotation` (16ths of a turn, clockwise seen from above)."""
    out = part.rotated(rot)
    for k, (n, p) in list(out.b.items()):
        if "rotation" in p:
            q = dict(p)
            q["rotation"] = str((int(q["rotation"]) + 4 * (rot % 4)) % 16)
            out.b[k] = (n, q)
    return out


def put(dst, part, ox, oy, oz, rot=0):
    dst.place(turn(part, rot), ox, oy, oz, 0)
    return dst


def skull(p, x, y, z, rot="0", wither=False):
    p.set(x, y, z, "wither_skeleton_skull" if wither else "skeleton_skull", rotation=str(rot))


def lantern(p, x, y, z):
    p.set(x, y, z, "lantern", **LANTERN)


def chain(p, x, y, z, axis="y"):
    p.set(x, y, z, "chain", axis=axis, **DRY)


def bone(p, x, y, z, axis="y"):
    p.set(x, y, z, "bone_block", axis=axis)


# ============================================================================================ (1) obelisk finial
def obelisk(h=4, big=False, cap="lantern", pal=None):
    """A spiky obelisk finial (Highgate's gate obelisks gone black): a chiseled die, a blackstone shaft of walls
    (thin posts read as a tapering needle), and a cap: a soul lantern (the capitol's pinnacles), a bone tip under an
    end-rod spike ("rod") or nothing ("post"). big: a 3 x 3 stepped base of stairs round the die."""
    p = Part(pal or pal_default())
    y = 0
    if big:
        for x in (-1, 0, 1):
            for z in (-1, 0, 1):
                if x or z:
                    p.stair(x, 0, z, toward(x, z), "bottom", "band_st")
        p.set(0, 0, 0, "band")
        y = 1
    p.set(0, y, 0, "chiseled_polished_blackstone")
    for k in range(1, h):
        p.set(0, y + k, 0, "needle_wall")
    if cap == "lantern":
        p.set(0, y + h, 0, "lantern", **LANTERN)
    elif cap == "rod":
        p.set(0, y + h, 0, "needle_wall")
    return p


def pal_default():
    return pal()


# ============================================================================================ (2) skull post
def skull_post(h=8, pal=None):
    """A gate post of the catacomb: a blackstone pier 1 wide banded with bone courses (the Paris catacomb's walls of
    femurs and skull bands), a corbel cap of stairs, a skull pyramid (Sedlec) and a soul lantern on top."""
    p = Part(pal or pal_default())
    for y in range(0, h):
        if y in (2, 5):
            bone(p, 0, y, 0, "z")                                   # the femur ends face the camera
        elif y in (3, 6):
            p.set(0, y, 0, "chiseled_polished_blackstone")
        else:
            p.set(0, y, 0, "base")
    p.set(0, h, 0, "band")
    for f in ("north", "south", "east", "west"):
        vx, vz = VEC[f]
        p.stair(vx, h, vz, OPP[f], "top", "band_st")
        skull(p, vx, h + 1, vz, {"south": 0, "west": 4, "north": 8, "east": 12}[f])
    p.set(0, h + 1, 0, "bone_block", axis="y")
    lantern(p, 0, h + 2, 0)
    return p


# ============================================================================================ (3) skull pyramid
def skull_pyramid(pal=None, wither_top=True):
    """Sedlec's pyramids of skulls at the scale of a prop: a 3 x 3 bone and blackstone die, a ring of skulls looking
    out from its top, a bone core rising through them, a wither skull crowning it, a candle at its foot."""
    p = Part(pal or pal_default())
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            p.set(x, 0, z, "bone_block" if (x + z) % 2 else "band", **({"axis": "y"} if (x + z) % 2 else {}))
    rots = {(0, 1): 0, (-1, 1): 2, (-1, 0): 4, (-1, -1): 6, (0, -1): 8, (1, -1): 10, (1, 0): 12, (1, 1): 14}
    for (x, z), r in rots.items():
        skull(p, x, 1, z, r)
    bone(p, 0, 1, 0)
    skull(p, 0, 2, 0, 0, wither=wither_top)
    return p


# ============================================================================================ (4) headstone
def headstone(kind=0, pal=None):
    """One grave: a headstone (blackstone wall with a chiseled or slab head, or a bone cross), a grave slab in front."""
    p = Part(pal or pal_default())
    if kind == 0:
        p.set(0, 0, 0, "polished_blackstone_wall")
        p.set(0, 1, 0, "polished_blackstone_slab", type="bottom", **DRY)
    elif kind == 1:
        p.set(0, 0, 0, "chiseled_deepslate")
        p.set(0, 1, 0, "deepslate_tile_wall")
    elif kind == 2:
        p.set(0, 0, 0, "deepslate_brick_wall")
        p.set(0, 1, 0, "deepslate_brick_wall")
        p.set(0, 2, 0, "end_rod", facing="up")
    else:
        p.set(0, 0, 0, "polished_deepslate")
        p.set(0, 1, 0, "white_candle", candles="2", lit="true", **DRY)
    return p


# ============================================================================================ (5) dark tower
def dark_tower(r=2, h=14, seed=0, spire_per=3, corner_spires=True, windows=("west", "south"), pal=None,
               foot=True, lantern_corner=(-1, 1)):
    """The Gravebound tower (the strongest castle ref: a black castle of clustered spires with lit windows): a square
    shaft (2r + 1 wide), deepslate masonry with blackstone quoins, a battered blackstone foot, PILASTERS of blackstone
    wall standing a cell proud on every face centre (a depth plane, a vertical line), string courses of upside-down
    stairs every four courses, lit pale-green lancets climbing the camera faces (the stair inside), a corbel table, a
    parapet of alternating merlons and wall posts, a black spire with lucarnes and a bone finial, and obelisk
    spirelets on the parapet corners (the cluster). Returns (part, top y)."""
    p = Part(pal or pal_default())
    m = GT.Mason(p, h + 10, seed, plinth=2)
    cells = GT.plan("square", r)
    if foot:
        for (x, z) in GT.ring_of("square", r + 1, r):
            p.stair(x, 1, z, toward(x, z), "bottom", "base_st")
    GT.shaft(p, "square", r, 0, h, m, thick=min(2, r), floors=4)
    # string courses
    for y in range(4, h - 1, 4):
        for (x, z) in GT.ring_of("square", r + 1, r):
            if abs(x) == r + 1 and abs(z) == r + 1:
                continue
            p.stair(x, y, z, toward(x, z), "top", "band_st")
    # pilasters on the face centres, broken by the string courses
    for f, (vx, vz) in VEC.items():
        x, z = vx * (r + 1), vz * (r + 1)
        for y in range(2, h - 1):
            if p.get(x, y, z) == "air":
                p.set(x, y, z, "base_wall")
    # lancets climbing the camera faces, beside the pilasters
    fc = [(c, f) for (c, f) in GT.face_cells(cells) if f in windows and (c[0] != 0 and c[1] != 0)]
    fc.sort(key=lambda cf: (cf[1] != windows[0], cf[0]))
    y, i = 2, seed
    while y < h - 3 and fc:
        c, f = fc[i % len(fc)]
        GT.window(p, c, f, y, "tall" if y > h * 0.45 else "small", cells)
        y += 2 if len(fc) > 2 else 3
        i += 1
    # the corbel table and parapet
    R = r + 1
    for (x, z) in GT.ring_of("square", R, r):
        p.stair(x, h, z, toward(x, z), "top", "band_st")
    for (x, z) in GT.plan("square", R):
        p.set(x, h + 1, z, "band")
    for (x, z) in GT.ring_of("square", R, r):
        if abs(x) == R and abs(z) == R:
            continue
        if (x + z) % 2:
            p.set(x, h + 2, z, "base_wall")
        else:
            p.slab(x, h + 2, z, "bottom", "band_slab")
    if corner_spires:
        for (sx, sz) in GT.DIAG:
            cap = "lantern" if (sx, sz) == lantern_corner else "rod"
            p.place(obelisk(3, cap=cap), sx * R, h + 2, sz * R)
    top = GT.spire(p, "square", r, h + 2, spire_per, "needle", lucarne_at=0 if r >= 2 else None, eaves=False)
    return p, top


# ============================================================================================ (6) buttress pier
def buttress_pier(h=10, depth=2, pal=None):
    """A buttress against a wall at z = 0 (the wall not included): one wide, `depth` deep, stepping back once at a
    weathered set-off, bone quoins on its outer arris, a soul-lantern niche, an obelisk pinnacle with a chain."""
    p = Part(pal or pal_default())
    mid = h // 2 + 1
    for z in range(1, depth + 1):
        top = h if z == 1 else mid
        for y in range(0, top + 1):
            q = (z == depth and y % 3 == 2) or (z == 1 and y > mid and y % 3 == 2)
            p.set(0, y, z, "bone_block" if q else ("base" if y < 2 else "wall"),
                  **({"axis": "y"} if q else {}))
        p.stair(0, top + 1, z, "north", "bottom", "band_st") if z == depth else None
    p.stair(0, mid - 3, depth + 1, "north", "top", "band_st")            # a drip under the lower stage
    p.stair(0, 1, depth + 1, "north", "bottom", "base_st")              # the battered foot
    p.set(0, 0, depth + 1, "base")
    p.place(obelisk(4), 0, h + 1, 1)
    return p


# ============================================================================================ (7) charnel bay
def charnel_bay(w=5, wall_h=9, lit=True, pal=None):
    """One bay of the ossuary range, front south, the wall face at z = 0, w wide (odd): a blackstone plinth with an
    iron VAULT DOOR between bone pilasters and an inverted torch over it (Highgate's Egyptian Avenue vault doors);
    a tall lancet of pale green glass with a mullion, a moulded sill, jamb shafts and a hood; a steep crocketed gable
    over the bay with a bone oculus round a wither skull, an obelisk finial; a lit room behind."""
    p = Part(pal or pal_default())
    hw = w // 2
    m = GT.Mason(p, wall_h + 6, 3, plinth=3)
    # the wall, two thick
    for x in range(-hw, hw + 1):
        for z in (0, -1):
            for y in range(0, wall_h + 1):
                m.put(x, y, z)
    # the plinth course and the vault door
    for x in range(-hw, hw + 1):
        p.stair(x, 3, 1, "north", "bottom", "band_st")
    p.set(0, 1, 0, "door", facing="south", half="lower", hinge="left", open="false", powered="false")
    p.set(0, 2, 0, "door", facing="south", half="upper", hinge="left", open="false", powered="false")
    p.set(0, 0, 0, "band")
    p.set(0, 3, 0, "chiseled_polished_blackstone")
    p.set(0, 3, 1, "end_rod", facing="down")                             # the inverted torch
    for s in (-1, 1):
        for y in (1, 2):
            bone(p, s, y, 1)                                             # bone pilasters, a cell proud
        p.stair(s, 3, 1, "east" if s < 0 else "west", "top", BONE_ST)    # their capitals bending in
    # the lancet: y 5..8, mullion, pointed head
    yl, ht = 5, 4
    if w >= 5:
        for y in range(yl, yl + ht):
            for x in (-1, 0, 1):
                p.set(x, y, 0, "rail" if x == 0 else "win_lit")
                p.set(x, y, -1, "air")
            if lit:
                p.set(0, y, -1, "light") if y in (yl, yl + 2) else None
        p.set(0, yl + ht, 0, "win_lit")
        p.stair(-1, yl + ht, 0, "east", "top", "trim_st")
        p.stair(1, yl + ht, 0, "west", "top", "trim_st")
        p.set(0, yl + ht + 1, 0, "carve")
        for x in (-1, 0, 1):
            p.slab(x, yl - 1, 1, "top", "trim_slab")                     # the sill
        for s in (-1, 1):
            for y in range(yl, yl + ht):
                bone(p, 2 * s, y, 1)                                     # bone jamb shafts
            p.stair(2 * s, yl + ht, 1, "east" if s < 0 else "west", "bottom", "band_st")
            p.stair(s, yl + ht + 1, 1, "east" if s < 0 else "west", "bottom", "band_st")
        p.set(0, yl + ht + 2, 1, "band")                                 # the hood's apex
    # the gable: steep (two courses a step), the rake a cell proud, crockets
    yg = wall_h + 1
    for k in range(hw + 1):
        xl, xr = -hw + k, hw - k
        for yy in (yg + 2 * k, yg + 2 * k + 1):
            for x in range(xl, xr + 1):
                p.set(x, yy, 0, "wall")
                p.set(x, yy, -1, "wall")
        if xl < xr:
            for yy in (yg + 2 * k, yg + 2 * k + 1):
                p.stair(xl, yy, 1, "east", "bottom", "roof_st") if yy % 2 == yg % 2 else p.set(xl, yy, 1, "roof")
                p.stair(xr, yy, 1, "west", "bottom", "roof_st") if yy % 2 == yg % 2 else p.set(xr, yy, 1, "roof")
            if k % 2 == 1:
                p.set(xl - 1, yg + 2 * k, 1, "needle_wall")              # crockets
                p.set(xr + 1, yg + 2 * k, 1, "needle_wall")
    apex = yg + 2 * hw + 1
    p.set(0, apex + 1, 0, "chiseled_polished_blackstone")
    p.set(0, apex + 1, 1, "roof")
    p.place(obelisk(3), 0, apex + 2, 0)
    # the bone oculus round a wither skull, in the gable a cell recessed behind a bone ring
    yo = yg + 2
    for (dx, dy) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        bone(p, dx, yo + dy, 0, "z")
    p.set(0, yo, 0, "air")
    p.set(0, yo, -1, "light")
    p.slab(0, yo - 1, 0, "top", BONE_SLAB) if False else None
    # sill under the gable: a string course of blackstone stairs across the bay
    for x in range(-hw, hw + 1):
        p.stair(x, wall_h, 1, "north", "top", "band_st") if abs(x) > 1 else None
    if lit:
        for x in range(-hw + 1, hw):
            p.set(x, 4, -1, "floor")
    return p


# ============================================================================================ (8) the ribcage rib
RIB_PATH = ((0, 9), (0, 10), (1, 11), (2, 12), (3, 13), (4, 14))     # (in from the leg, y) up to the apex


def rib(half=4, y_spring=9, pal=None, path=RIB_PATH):
    """One bone RIB of the gantry's ribcage at x = 0, its legs at z = 0 and z = 2 * half, curving in over the bay to
    the apex at z = half: legs of bone (axis y) from y 1, the curve a chain of bone blocks with smooth-quartz stairs as
    the rounded bends - an upside-down stair under each step (the soffit), a stair over it (the back) - and a skull boss
    under the apex (Sedlec's skull swags)."""
    p = Part(pal or pal_default())
    for zz, s in ((0, 1), (2 * half, -1)):
        f = "south" if s > 0 else "north"                    # the curve climbs toward the apex
        for y in range(1, y_spring + 1):
            bone(p, 0, y, zz)
        prev = None
        for (dz, y) in path:
            z = zz + s * dz
            if dz == half and s < 0:
                continue                                       # the apex is drawn once
            bone(p, 0, y, z, "y" if dz == 0 else "z")
            if prev is not None and dz != prev and dz != half:
                p.stair(0, y - 1, z, OPP[f], "top", BONE_ST)   # soffit
                p.stair(0, y + 1, z, f, "bottom", BONE_ST)     # back
            prev = dz
    return p


# ============================================================================================ (9) the hearse hull
def hearse_hull(pal=None):
    """The war-wagon's hull on the hoist (3 wide z, 4 long x, front west): a dark-oak bed with bone ribs bowed over it
    like a hearse's canopy frame, black glass panels, a coffin of polished blackstone inside, soul lanterns on the
    corner posts, a skull on the prow. Frame: x 0..3, y 0..3, z -1..1."""
    p = Part(pal or pal_default())
    for x in range(0, 4):
        for z in (-1, 0, 1):
            p.set(x, 0, z, "dark_oak_planks")
    for z in (-1, 0, 1):
        p.stair(-1, 0, z, "west", "top", "dark_oak_stairs")
    for x in (0, 3):
        for z in (-1, 1):
            bone(p, x, 1, z)
            bone(p, x, 2, z)
    for x in (1, 2):
        for z in (-1, 1):
            p.set(x, 1, z, "black_stained_glass_pane")
            p.set(x, 2, z, "black_stained_glass_pane")
    for z in (-1, 0, 1):
        p.set(0, 1, z, "black_stained_glass_pane") if z == 0 else None
        p.set(3, 1, z, "black_stained_glass_pane") if z == 0 else None
    p.set(1, 1, 0, "polished_blackstone_slab", type="top", **DRY)
    p.set(2, 1, 0, "polished_blackstone_slab", type="top", **DRY)
    # the bowed bone canopy
    for x in range(0, 4):
        p.stair(x, 3, -1, "south", "bottom", BONE_ST)
        p.stair(x, 3, 1, "north", "bottom", BONE_ST)
        bone(p, x, 3, 0, "x") if x in (0, 3) else p.set(x, 3, 0, "black_wool")
    skull(p, -1, 1, 0, 4)                                  # the prow skull looks west down the lane
    lantern(p, 0, 4, 0)
    lantern(p, 3, 4, 0)
    return p


# ============================================================================================ (10) winch
def winch(pal=None):
    """The ribcage's winch on the spine (front south, axis across z): a dark-oak drum (log, axis z) between two
    blackstone cheeks on the spine, a crank of fence, chains wound on the drum, a bone counter-weight."""
    p = Part(pal or pal_default())
    for z in (-1, 0, 1):
        p.set(0, 1, z, "dark_oak_log", axis="z")
    for z in (-2, 2):
        p.set(0, 0, z, "band")
        p.set(0, 1, z, "polished_blackstone_wall")
        p.set(0, 2, z, "polished_blackstone_slab", type="bottom", **DRY)
    p.set(1, 1, 2, "dark_oak_fence")
    p.set(1, 2, 2, "dark_oak_fence")
    return p


# ============================================================================================ (11) front spire
def front_spire(h=10, windows=("west", "south"), pal=None):
    """One of the TWIN SPIRES of the west front (ref05's silhouette in black): a slim 3 x 3 shaft - blackstone corner
    shafts, deepslate faces - with a lit pale-green lancet in the centre of each camera face at two storeys (a sill
    slab and a hood one cell proud), string courses of upside-down stairs at y 4 and 8, a corbel table, a parapet with
    four obelisk pinnacles, and a black needle with a bone knop. Returns (part, top y)."""
    p = Part(pal or pal_default())
    cells = GT.plan("square", 1)
    for y in range(0, h + 1):
        for (x, z) in cells:
            if (x, z) == (0, 0):
                if y % 4 == 0:
                    p.set(0, y, 0, "polished_deepslate")
                continue
            corner = abs(x) == 1 and abs(z) == 1
            if y <= 1:
                p.set(x, y, z, "base")
            elif corner:
                p.set(x, y, z, "chiseled_polished_blackstone" if y % 4 == 2 else "base")
            else:
                p.set(x, y, z, "deepslate_tiles" if (y // 3) % 2 else "deepslate_bricks")
    for y in (4, 8):
        if y < h:
            for (x, z) in GT.ring_of("square", 2, 1):
                if abs(x) == 2 and abs(z) == 2:
                    continue
                p.stair(x, y, z, toward(x, z), "top", "band_st")
    for f in windows:
        vx, vz = VEC[f]
        for (y0, tall) in ((5, False), (9, True)):
            if y0 + 1 >= h:
                continue
            for k in range(2 if not tall else 2):
                p.set(vx, y0 + k, vz, "win_lit")
                p.set(0, y0 + k, 0, "light") if k == 0 else p.clear(0, y0 + k, 0)
            p.slab(2 * vx, y0 - 1, 2 * vz, "top", "band_slab") if p.get(2 * vx, y0 - 1, 2 * vz) == "air" else None
            p.stair(2 * vx, y0 + 2, 2 * vz, OPP[f], "top", "band_st")
            p.set(vx, y0 + 2, vz, "carve")
    for (x, z) in GT.ring_of("square", 2, 1):
        p.stair(x, h, z, toward(x, z), "top", "band_st")
    for (x, z) in GT.plan("square", 2):
        p.set(x, h + 1, z, "band")
    for (x, z) in GT.ring_of("square", 2, 1):
        if abs(x) == 2 and abs(z) == 2:
            p.place(obelisk(2, cap="rod"), x, h + 2, z)
        elif (x + z) % 2:
            p.set(x, h + 2, z, "base_wall")
        else:
            p.slab(x, h + 2, z, "bottom", "band_slab")
    y = h + 2
    for (x, z) in cells:
        for k in range(3):
            p.set(x, y + k, z, "needle")
    for (x, z) in GT.ring_of("square", 1, 0):
        p.stair(x, y + 3, z, toward(x, z), "bottom", "needle_st")
    p.set(0, y + 3, 0, "needle")
    p.set(0, y + 4, 0, "needle")
    p.set(0, y + 5, 0, "needle_wall")
    p.set(0, y + 6, 0, "bone_block", axis="y")
    p.set(0, y + 7, 0, "spike", facing="up")
    return p, y + 7


# ============================================================================================ (12) dead tree
def dead_tree(seed=0, pal=None):
    """A twisted dead tree for the crag (ref16's gnarled trees): root flares of dark-oak stairs, a trunk that kinks
    (dark oak and stripped logs), two bare branches turning out, twig ends of fence. Frame: trunk at (0, 0, 0)."""
    p = Part(pal or pal_default())
    for f, (vx, vz) in VEC.items():
        if GP.hsh(vx, vz, seed) % 3:
            p.stair(vx, 0, vz, OPP[f], "bottom", "dark_oak_stairs")
    p.set(0, 0, 0, "dark_oak_log", axis="y")
    p.set(0, 1, 0, "dark_oak_log", axis="y")
    sx = 1 if seed % 2 == 0 else -1
    p.set(0, 2, 0, "dark_oak_log", axis="y")
    p.set(sx, 3, 0, "dark_oak_log", axis="x")
    p.set(0, 3, 0, "stripped_dark_oak_log", axis="y")
    p.set(sx, 4, 0, "dark_oak_log", axis="y")
    p.set(sx, 5, 0, "stripped_dark_oak_log", axis="y")
    p.set(2 * sx, 5, 0, "dark_oak_fence")
    p.set(2 * sx, 6, 0, "dark_oak_fence")
    p.set(0, 4, 1, "stripped_dark_oak_log", axis="z")
    p.set(-sx, 4, 0, "dark_oak_fence")
    p.set(0, 5, 1, "dark_oak_fence")
    p.set(0, 4, 2, "dark_oak_fence")
    p.set(sx, 6, 0, "dark_oak_fence")
    p.set(sx, 6, -1, "dark_oak_fence")
    return p


# ============================================================================================ (13) the keep
def keep(h=13, r=2, seed=2, pal=None):
    """THE LANDMARK (ref21: a buttressed gothic tower with CORBELLED CORNER BARTIZANS, a crenellated parapet and a steep
    spire): a 5 x 5 shaft of polished blackstone bricks (the keep is darker and smoother than the deepslate hall, so it
    reads as one mass), a bone string course every four courses, lit 1 x 3 lancets in the face centres (two storeys),
    a corbel table (upside-down stairs) carrying a crown one cell wider, bartizan posts on the four corners (a wall
    post, a chiseled die, a bone tip), merlons between, and a black spire with a BONE-TIPPED finial. Centre (0, 0),
    floor y 0. Returns (part, top y). Height h + 10 with r 2."""
    p = Part(pal or pal_default())
    cells = GT.plan("square", r)
    R = r + 1
    for y in range(0, h + 1):
        for (x, z) in cells:
            if abs(x) < r and abs(z) < r:
                p.clear(x, y, z)                                             # hollow: lit rooms, not a core
                continue
            corner = abs(x) == r and abs(z) == r
            if y % 4 == 3 and not corner and y < h - 1:
                p.set(x, y, z, "bone_block", axis="x" if abs(z) == r else "z")   # the bone string course
            elif corner and y % 4 == 1:
                p.set(x, y, z, "chiseled_polished_blackstone")
            else:
                p.set(x, y, z, "cracked_polished_blackstone_bricks" if GP.hsh(x * 3 + y, z, seed) % 11 == 0 else
                      "polished_blackstone_bricks")
    # the hollow's floors: a lit room at each window storey
    for y in (0, 4, 8):
        for (x, z) in GT.plan("square", r - 1):
            p.set(x, y, z, "polished_deepslate")
    # lancets: 1 x 3 in every face centre, two storeys (y 5..7 and 9..11), lime glass with the glow behind
    for f, (vx, vz) in VEC.items():
        for y0 in (1, 5, 9):
            if y0 + 3 > h:
                continue
            for k in range(3):
                p.set(vx * r, y0 + k, vz * r, "win_lit")
            p.set(vx * (r - 1), y0 + 1, vz * (r - 1), "light")
            p.slab(vx * R, y0 - 1, vz * R, "top", "band_slab")              # the sill, proud
            p.stair(vx * R, y0 + 3, vz * R, OPP[f], "top", "band_st")       # the hood, proud
    # the corbel table and the crown one wider (ref21's machicolated head)
    for (x, z) in GT.ring_of("square", R, r):
        p.stair(x, h, z, toward(x, z), "top", "band_st")
    for (x, z) in GT.plan("square", R):
        p.set(x, h + 1, z, "band")
    for (x, z) in GT.ring_of("square", R, r):
        corner = abs(x) == R and abs(z) == R
        if corner:
            p.set(x, h + 2, z, "polished_blackstone_wall")
            p.set(x, h + 3, z, "chiseled_polished_blackstone")
            p.set(x, h + 4, z, "polished_blackstone_wall")
            p.set(x, h + 5, z, "bone_block", axis="y")
        elif (x + z) % 2:
            p.set(x, h + 2, z, "polished_blackstone_wall")
        else:
            p.slab(x, h + 2, z, "bottom", "band_slab")
    # the spire: 5 wide, stepping in every 2 courses, a bone-tipped finial
    y = h + 2
    for (x, z) in cells:
        p.set(x, y, z, "needle")
        p.set(x, y + 1, z, "needle")
    for (x, z) in GT.ring_of("square", r, r - 1):
        p.stair(x, y + 1, z, toward(x, z), "bottom", "needle_st")
    for (x, z) in GT.plan("square", 1):
        p.set(x, y + 2, z, "needle")
        p.set(x, y + 3, z, "needle")
    for (x, z) in GT.ring_of("square", 1, 0):
        p.stair(x, y + 3, z, toward(x, z), "bottom", "needle_st")
    p.set(0, y + 4, 0, "needle")
    p.set(0, y + 5, 0, "needle_wall")
    p.set(0, y + 6, 0, "bone_block", axis="y")
    p.set(0, y + 7, 0, "spike", facing="up")
    return p, y + 7


# ============================================================================================ (14) pinnacle turret
def pinnacle(h=6, pal=None):
    """A small 3 x 3 pinnacle turret (ref14/16's ring of coned towers, stepping up to the keep): blackstone-brick
    corner shafts round a deepslate core, one lit slot in its south and west faces, a corbel ring, and a slate cone
    with a bone tip. Centre (0, 0), floor y 0. Returns (part, top y)."""
    p = Part(pal or pal_default())
    for y in range(0, h + 1):
        for (x, z) in GT.plan("square", 1):
            corner = abs(x) == 1 and abs(z) == 1
            if (x, z) == (0, 0):
                p.set(0, y, 0, "light" if y == h // 2 else "polished_deepslate")
            elif corner:
                p.set(x, y, z, "chiseled_polished_blackstone" if y == h - 1 else "polished_blackstone_bricks")
            else:
                p.set(x, y, z, "deepslate_bricks")
    for f in ("south", "west"):
        vx, vz = VEC[f]
        for y in range(h // 2 - 1, h // 2 + 2):
            p.set(vx, y, vz, "win_lit")
    for (x, z) in GT.ring_of("square", 2, 1):
        if abs(x) == 2 and abs(z) == 2:
            continue
        p.stair(x, h + 1, z, toward(x, z), "top", "band_st")
    for (x, z) in GT.plan("square", 1):
        p.set(x, h + 1, z, "band")
        p.set(x, h + 2, z, "roof")
    for (x, z) in GT.ring_of("square", 1, 0):
        p.stair(x, h + 3, z, toward(x, z), "bottom", "roof_st")
    p.set(0, h + 3, 0, "roof")
    p.set(0, h + 4, 0, "roof")
    p.set(0, h + 5, 0, "roof_wall")
    p.set(0, h + 6, 0, "bone_block", axis="y")
    return p, h + 6


# ============================================================================================ (15) bone gate pilaster
def gate_pilaster(h=8, pal=None):
    """One of the gate's two THICK BONE PILASTERS (2 wide, 2 deep, the catacomb's femur walls): bone blocks with the
    ends out, a chiseled blackstone band every third course, a corbel cap of smooth-quartz stairs and a bone die, a
    skull on its cap looking down the lane. Frame: x 0..1 (west-east), z 0..1, y 0..h+1; the lane side is -z or +z
    as placed. The cap's skull is at (0, h+2, 0)."""
    p = Part(pal or pal_default())
    for x in (0, 1):
        for z in (0, 1):
            for y in range(0, h + 1):
                if y % 3 == 2:
                    p.set(x, y, z, "chiseled_polished_blackstone")
                else:
                    bone(p, x, y, z, "z" if x == 0 else "y")
    for x in (0, 1):
        for z in (0, 1):
            p.set(x, h + 1, z, "bone_block", axis="y")
    p.stair(-1, h + 1, 0, "east", "top", BONE_ST)
    p.stair(-1, h + 1, 1, "east", "top", BONE_ST)
    skull(p, 0, h + 2, 0, 4)
    skull(p, 0, h + 2, 1, 4)
    return p


# ============================================================================================ (16) rib arc
def rib_arc(span=11, rise=5, y0=0, pal=None):
    """One bone RIB of the rib-vault, in the plane x = 0, springing from its two feet at z 0 and z span - 1 (y0) and
    bowing up `rise` cells to the apex over the middle: bone blocks where it climbs, smooth-quartz stairs as the
    rounded back on every step (ref10's ribbed dome in bone). Nothing under the apex is drawn, so the spawn column
    stays clear when the vault spans the lane. Returns the part."""
    import math
    p = Part(pal or pal_default())
    n = span - 1
    mid = n / 2.0
    prev = None
    for i in range(span):
        t = abs(i - mid) / mid
        y = y0 + round(rise * math.sqrt(max(0.0, 1 - t * t)))
        if prev is not None:
            lo, hi = sorted((prev, y))
            for yy in range(lo, hi + 1):
                bone(p, 0, yy, i, "y" if hi > lo else "z")
            if hi > lo:
                f = "south" if i <= mid else "north"
                p.stair(0, hi + 1, i, f, "bottom", BONE_ST)
                p.stair(0, lo - 1, i, OPP[f], "top", BONE_ST) if lo - 1 > y0 else None
        else:
            bone(p, 0, y, i, "y")
        prev = y
    return p


# ============================================================================================ (17) soul brazier
def brazier(pal=None):
    """A soul brazier beside the gate: a chiseled blackstone pedestal, a blackstone-wall stem, a cauldron-less bowl of
    four upside-down stairs round soul soil with soul fire on it (the only open flame, sealed by the bowl's rim - a
    unit cannot stand on a stair rim beside a wall). Centre (0, 0), y 0..3."""
    p = Part(pal or pal_default())
    p.set(0, 0, 0, "chiseled_polished_blackstone")
    p.set(0, 1, 0, "polished_blackstone_wall")
    p.set(0, 2, 0, "soul_soil")
    p.set(0, 3, 0, "soul_fire")
    for f, (vx, vz) in VEC.items():
        p.stair(vx, 2, vz, OPP[f], "top", "band_st")
    return p


# ============================================================================================ (18) lancet slot
def lancet(p, x, y0, z, f, h=3, lit=True):
    """A 1 x h LIT WINDOW SLOT in a wall whose outside is in direction f, sill at y0: lime glass in the face cell, the
    glow behind (one in), a proud bone sill slab and a proud blackstone hood stair outside."""
    vx, vz = VEC[f]
    for k in range(h):
        p.set(x, y0 + k, z, "win_lit")
    if lit:
        p.set(x - vx, y0 + 1, z - vz, "light")
    p.slab(x + vx, y0 - 1, z + vz, "top", BONE_SLAB)
    p.stair(x + vx, y0 + h, z + vz, OPP[f], "top", "band_st")
