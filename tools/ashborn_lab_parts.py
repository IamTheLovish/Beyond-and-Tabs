#!/usr/bin/env python3
"""ASHBORN PART LIBRARY v2 - the Ember Pens (the Ashborn vehicle lab) and the Ashborn buildings after it.

Built on tools/gothic_parts.Part (its conventions, unchanged): a part is a dict of cells in its own frame, its FRONT
faces SOUTH (+z), y = 0 is its floor, and put() below turns it a quarter turn clockwise per `rot`. Roles go through
PAL, so a part asks for "dress" or "bronze" and the palette names the block; a plain block id passes straight through.

ROUND 2 (after the 3/10 critique of round 1, "a 17 x 17 block of noise"): the library is ORDERED now. Walls are laid
in courses (stone() with the *_o zones), never scattered; bronze is kept for the giant, the gate and the forge; glow
is kept for the forge heart, the gate mouth and a few lit windows. The one thing the critic liked - the basalt column
wall - is the ground story everywhere.

THE KIT, 1.20.1 only:
  - crag:    basalt columns (axis y), polished basalt joints, smooth basalt
  - tiers:   polished blackstone bricks with a polished blackstone course (T2); deepslate bricks with a deepslate tile
             course (T3) - the stand-ins for dressed tuff (1.20.1 has no tuff bricks)
  - bronze:  waxed cut copper (fresh), waxed exposed cut copper (worn), waxed copper block (the hammer's core)
  - glow:    magma, shroomlight - always in a VERTICAL face behind bars / a pane, never under a foot
  - ash:     coarse dirt, gravel, tuff, smooth basalt for the apron
  BANNED: gold, gilded blackstone, nether brick, crimson / warped wood, netherrack, banners, piglin heads.

PARTS (each a function returning a Part, front south):
  column_field  the Causeway: basalt prisms of stepped heights, jointed, cupped tops
  gate_pylon    the FISSURE GATE: two column fields climbing to a bronze-and-basalt lintel over a 5 x 8 mouth and a
                column crest with the FORGE HEART (a barred 3 x 3 glow in a bronze frame) over it
  hearth        the forge mouth at the lane's end: a barred fire under a bronze lintel between basalt jambs
  colossus      THE STONE GIANT, standing, 14 tall, the bronze hammer raised over his head
  winch         the chain drum on its posts (the hoist's drive, on the bridge top)

Usage: python3 tools/ashborn_lab_parts.py OUT_DIR   -> one .nbt per part (a quick look at the catalogue)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gothic_parts as gp   # noqa: E402

Part = gp.Part
hsh = gp.hsh
OPP = gp.OPP
VEC = gp.VEC

PAL = {
    "crag": "basalt", "crag_s": "smooth_basalt", "pier": "polished_basalt", "blk": "blackstone",
    "rubble": "cobbled_deepslate",
    "dress": "deepslate_bricks", "dress_c": "cracked_deepslate_bricks", "dress_st": "deepslate_brick_stairs",
    "dress_slab": "deepslate_brick_slab", "dress_wall": "deepslate_brick_wall",
    "tile": "deepslate_tiles", "tile_st": "deepslate_tile_stairs", "tile_slab": "deepslate_tile_slab",
    "tile_wall": "deepslate_tile_wall",
    "pd": "polished_deepslate", "pd_st": "polished_deepslate_stairs", "pd_slab": "polished_deepslate_slab",
    "pd_wall": "polished_deepslate_wall",
    "pbb": "polished_blackstone_bricks", "pbb_c": "cracked_polished_blackstone_bricks",
    "pbb_st": "polished_blackstone_brick_stairs", "pbb_slab": "polished_blackstone_brick_slab",
    "pbb_wall": "polished_blackstone_brick_wall", "pb": "polished_blackstone", "pb_st": "polished_blackstone_stairs",
    "pb_slab": "polished_blackstone_slab", "pb_wall": "polished_blackstone_wall",
    "blk_st": "blackstone_stairs", "blk_slab": "blackstone_slab", "blk_wall": "blackstone_wall",
    "carve": "chiseled_deepslate", "carve2": "chiseled_polished_blackstone", "tuff": "tuff",
    "bronze": "waxed_cut_copper", "bronze_w": "waxed_exposed_cut_copper", "bronze_p": "waxed_weathered_cut_copper",
    "bronze_st": "waxed_cut_copper_stairs", "bronze_w_st": "waxed_exposed_cut_copper_stairs",
    "bronze_slab": "waxed_cut_copper_slab", "bronze_w_slab": "waxed_exposed_cut_copper_slab",
    "bronze_block": "waxed_copper_block",
    "obs": "obsidian", "magma": "magma_block", "glow": "shroomlight",
    "pane": "orange_stained_glass_pane", "bars": "iron_bars", "chain": "chain",
    "hide": "black_wool", "hide2": "brown_terracotta",
    "ash": "coarse_dirt", "grav": "gravel",
}
GLOWS = ("magma_block", "shroomlight", "ochre_froglight")
DRY = dict(waterlogged="false")
ROD = dict(powered="false", waterlogged="false")


def new():
    return Part(dict(PAL))


def stone(p, x, y, z, zone="dress_o", seed=0):
    """A wall cell of an ORDERED zone - courses by height, never scatter ("calm because the detail is ordered"):
    'basalt_o' the Causeway column wall (basalt prisms, a polished-basalt joint every fourth course, staggered per
    column); 'black_o' polished blackstone bricks with a polished blackstone course every fourth; 'dress_o' deepslate
    bricks with a deepslate tile course every fourth; a rare cracked brick in both. '=role' is a plain zone."""
    if zone.startswith("="):
        p.set(x, y, z, zone[1:])
        return
    if zone == "basalt_o":
        if (y + hsh(x, z, seed) % 4) % 4 == 0:
            p.set(x, y, z, "pier", axis="y")
        else:
            p.set(x, y, z, "crag", axis="y")
        return
    course, field, crack = (("pb", "pbb", "pbb_c") if zone == "black_o" else ("tile", "dress", "dress_c"))
    if y % 4 == 0:
        p.set(x, y, z, course)
    else:
        p.set(x, y, z, crack if hsh(x + y, z - y, seed) % 19 == 0 else field)


def rod(p, x, y, z, facing="up"):
    p.set(x, y, z, "lightning_rod", facing=facing, **ROD)


def chain(p, x, y, z, axis="y"):
    p.set(x, y, z, "chain", axis=axis, **DRY)


def pier(p, x, y, z, axis="y"):
    p.set(x, y, z, "pier", axis=axis)


def put(dst, part, ox, oy, oz, rot=0):
    """Copies `part` into `dst` turned `rot` quarter turns clockwise, its turned bounding box's north-west-bottom corner
    at (ox, oy, oz) - so a part is placed by its corner whatever way it faces."""
    r = part.rotated(rot)
    if not r.b:
        return dst
    x0 = min(c[0] for c in r.b)
    z0 = min(c[2] for c in r.b)
    y0 = min(min(c[1] for c in r.b), 0)
    for (x, y, z), v in r.b.items():
        if v[0] not in ("air", "cave_air"):
            dst.b[(x - x0 + ox, y - y0 + oy, z - z0 + oz)] = v
    return dst


# ============================================================================================ the Causeway
def column_field(w, d, height, seed=0, caps=True, joint=4):
    """Basalt prisms on a w x d grid, column (x, z) `height(x, z)` cells tall (y 1..h). The Causeway's columns are
    jointed every few feet (a polished-basalt course, staggered per column) and their tops are cupped or cut: a
    polished slab, a stair leaning to the low side, or a bare polished end."""
    p = new()
    for x in range(w):
        for z in range(d):
            h = height(x, z)
            if h <= 0:
                continue
            off = hsh(x, z, seed) % joint
            for y in range(1, h + 1):
                if (y + off) % joint == 0:
                    pier(p, x, y, z)
                else:
                    p.set(x, y, z, "crag", axis="y")
            if not caps:
                continue
            k = hsh(x * 7, z * 3, seed + 1) % 6
            if k in (0, 1):
                p.slab(x, h + 1, z, "bottom", "pb_slab")
            elif k == 2:
                lo = min(((height(x + dx, z + dz) if 0 <= x + dx < w and 0 <= z + dz < d else -9), f)
                         for f, (dx, dz) in VEC.items())
                p.stair(x, h + 1, z, OPP[lo[1]], "bottom", "pb_st")
            elif k == 3:
                pier(p, x, h, z)
    return p


# ============================================================================================ the fissure gate
def gate_pylon(mouth_w=5, mouth_h=8, crest=(12, 13, 15, 16, 15, 13, 12), flank=(5, 7, 9), seed=1):
    """THE FISSURE GATE (front WEST in use: build it front south here and put() it with rot=1... no: it is built
    straight in the lab's frame to keep the heart's bars simple). Frame: x 0..1 (x 0 is the outer face), z 0..L-1,
    y 1 = the ground's top. Two column fields (heights `flank` from the ends) climb to the jambs (polished basalt with
    bronze bands) either side of a mouth `mouth_w` wide x `mouth_h` tall; a lintel of polished basalt and bronze
    spans it at y mouth_h+1 (its inner beam is the raised gate leaf's bottom rail); above the lintel a crest of
    columns (heights `crest`, one per lintel cell, jamb to jamb) rises to a peak over the mouth's middle, and the
    FORGE HEART - a 3 x 3 barred glow in a bronze frame with a bronze hood - sits in the crest's face right over the
    mouth (ref24: one glowing core over the gate, nothing else)."""
    p = new()
    nf = len(flank)
    L = nf * 2 + 2 + mouth_w
    j0, j1 = nf, nf + mouth_w + 1            # the jambs' z
    # the flank columns (two deep: the inner column a course taller - a field stepping up)
    for i, h in enumerate(flank):
        for z in (i, L - 1 - i):
            for x in (0, 1):
                hh = h + x
                off = hsh(x, z, seed) % 4
                for y in range(1, hh + 1):
                    if (y + off) % 4 == 0:
                        pier(p, x, y, z)
                    else:
                        p.set(x, y, z, "crag", axis="y")
                if x == 0:
                    p.slab(x, hh + 1, z, "bottom", "pb_slab")
    # the jambs: polished basalt with bronze bands (the leaf's guides) up to the crest
    jamb_top = mouth_h + 1 + crest[0]
    for z in (j0, j1):
        for x in (0, 1):
            for y in range(1, jamb_top + 1):
                pier(p, x, y, z)
            for y in (3, 6):
                p.set(x, y, z, "bronze_w")
            p.set(x, mouth_h, z, "bronze")
    # the lintel: y mouth_h+1 polished basalt beam (axis z) with bronze blocks at the ends and the keystone;
    # the face row above it a projecting bronze cornice, the inner row the leaf's bronze top rail
    yl = mouth_h + 1
    for z in range(j0, j1 + 1):
        mid = (j0 + j1) // 2
        p.set(0, yl, z, "bronze" if z in (j0, mid, j1) else "pier", **({} if z in (j0, mid, j1) else {"axis": "z"}))
        p.set(1, yl, z, "bronze_w" if z in (j0, j1) else "bronze")
        p.stair(0, yl + 1, z, "east", "top", "bronze_st") if z not in (j0, j1) else p.set(0, yl + 1, z, "carve2")
        p.set(1, yl + 1, z, "pbb")
    # the crest columns on the lintel
    for i, h in enumerate(crest):
        z = j0 + i
        for x in (0, 1):
            off = hsh(x, z, seed + 2) % 4
            for y in range(yl + 2, yl + 1 + h + 1):
                if (y + off) % 4 == 0:
                    pier(p, x, y, z)
                else:
                    p.set(x, y, z, "crag", axis="y")
            if x == 0 and i not in (0, len(crest) - 1):
                p.slab(x, yl + 2 + h, z, "bottom", "pb_slab")
    # the heart: a 3 x 3 NICHE over the mouth's middle, y yl+2 .. yl+4 - the face cells open, bars one cell in, the
    # glow (magma / shroomlight) one further, a bronze frame round the niche and a bronze hood over it: three
    # depth planes in the one glowing feature (ref24's core), capped so no glow shows from above
    mid = (j0 + j1) // 2
    for z in (mid - 1, mid, mid + 1):
        for y in range(yl + 2, yl + 5):
            p.clear(0, y, z)
            p.set(1, y, z, "bars")
            p.set(2, y, z, "magma" if (z == mid) == (y == yl + 3) else "glow")
        p.slab(2, yl + 5, z, "bottom", "pb_slab")
        p.set(2, yl + 1, z, "pbb")
    for y in range(yl + 2, yl + 5):
        p.set(0, y, mid - 2, "bronze" if y != yl + 3 else "bronze_w")
        p.set(0, y, mid + 2, "bronze" if y != yl + 3 else "bronze_w")
    for z in (mid - 1, mid, mid + 1):
        p.stair(0, yl + 5, z, "east", "top", "bronze_st")
        p.set(1, yl + 5, z, "bronze_w")
    p.set(0, yl + 5, mid - 2, "bronze_w")
    p.set(0, yl + 5, mid + 2, "bronze_w")
    p.set(0, yl + 6, mid, "carve2")
    # the peak's bronze spike
    rod(p, 1, yl + 2 + max(crest), mid)
    return p


# ============================================================================================ the forge
def hearth(w=5, h=5):
    """The FORGE MOUTH at the lane's end (front south): a wall w wide, h tall of basalt columns with a barred fire
    3 wide x 2 high in the middle (magma and shroomlight one cell behind the bars), a bronze lintel of upside-down
    stairs over it, polished basalt jambs with bronze bands, a chiseled panel over the lintel."""
    p = new()
    for x in range(w):
        for y in range(1, h + 1):
            for z in (0, 1):
                stone(p, x, y, z, "basalt_o", 7)
    m = w // 2
    for x in range(m - 1, m + 2):
        for y in (1, 2):
            p.set(x, y, 1, "bars")
            p.set(x, y, 0, "magma" if (x + y) % 2 else "glow")
        p.stair(x, 3, 1, "north", "top", "bronze_st")
    p.set(m, 4, 1, "carve")
    for x in (m - 2, m + 2):
        for y in (1, 2, 3):
            pier(p, x, y, 1)
        p.set(x, 2, 1, "bronze_w")
    return p


# ============================================================================================ the Stone Giant
def colossus():
    """THE STONE GIANT SMITH, STANDING - the Ember Pens' landmark, 11 tall (the lab's height cap: L1 <= 20 with his
    feet on the second tier's deck at y 9). Frame: front south (+z); x 0..6 is the body (pauldrons at x 0 and x 6,
    torso x 1..5, head x 2..4), the maul x -1..1; z 1..2 the body, z 3 the chest plate, brow and toes, z 0..2 the
    maul; y 0 = the deck he stands on, y 10 his top. Polished deepslate and polished basalt flesh (the joints of a
    basalt column), bronze greaves, belt, chest plate and pauldrons; a blackstone hood over a deepslate face under a
    jutting brow (no glowing eyes: the forge heart is the lab's only glow). His RIGHT arm (west, x 0) is raised
    straight up, the bronze maul (3 x 3, a waxed copper block core - the brightest metal in the build) held over his
    hood; his left arm (x 6) hangs with the tongs."""
    p = new()

    def body(x, y, z, seed=0):
        k = hsh(x * 3 + y, z * 5 - y, seed + 70) % 7
        if k < 4:
            p.set(x, y, z, "pier", axis="y")
        elif k < 6:
            p.set(x, y, z, "crag_s")
        else:
            p.set(x, y, z, "pd")

    # legs y 0..1 (two 2 x 2 columns), bronze greaves, toes
    for x in (1, 2, 4, 5):
        for z in (1, 2):
            for y in range(2):
                body(x, y, z)
        p.set(x, 0, 2, "bronze_w")
        p.stair(x, 0, 3, "north", "bottom", "pd_st")
    # the belt (y 2) and its buckle
    for x in range(1, 6):
        for z in (1, 2):
            p.set(x, 2, z, "bronze" if (x + z) % 2 else "bronze_w")
    p.set(3, 2, 3, "carve2")
    # torso y 3..5, the bronze chest plate jutting at z 3
    for x in range(1, 6):
        for z in (1, 2):
            for y in range(3, 6):
                body(x, y, z, 2)
    for x in (2, 3, 4):
        p.stair(x, 5, 3, "north", "top", "bronze_st")
        p.stair(x, 4, 3, "north", "bottom", "bronze_w_st")
    for x in (1, 5):
        p.stair(x, 5, 3, "north", "top", "pd_st")
    # shoulders y 6 with the pauldrons at x 0 and x 6
    for x in range(0, 7):
        for z in (1, 2):
            if x in (0, 6):
                p.set(x, 6, z, "bronze")
            else:
                body(x, 6, z, 3)
    p.stair(6, 7, 1, "west", "bottom", "bronze_st")
    p.stair(6, 7, 2, "west", "bottom", "bronze_st")
    # the head y 7..8: blackstone hood, deepslate face, the brow, the beard; the hood's peak at y 9
    for x in (2, 3, 4):
        for z in (1, 2):
            for y in (7, 8):
                p.set(x, y, z, "blk")
        p.set(x, 7, 2, "pd")
        p.set(x, 8, 2, "pd")
        p.stair(x, 8, 3, "north", "top", "pd_st")             # the brow jutting over the eyes
    p.set(3, 7, 3, "pbb_wall")                                # the beard
    p.set(3, 9, 1, "blk")
    p.stair(2, 9, 1, "east", "bottom", "blk_st")
    p.stair(4, 9, 1, "west", "bottom", "blk_st")
    p.stair(3, 9, 2, "south", "bottom", "blk_st")             # the hood's lip
    # the RIGHT arm (x 0) raised straight up: upper arm, bracer, fist, the maul held across over the hood
    body(0, 7, 1, 4)
    p.set(0, 8, 1, "bronze_w")                                # the bracer
    p.set(0, 9, 1, "crag_s")                                  # the fist
    p.set(0, 9, 2, "crag_s")
    for x in (-1, 0, 1):
        for z in (0, 1, 2):
            core = x == 0 and z == 1
            p.set(x, 10, z, "bronze_block" if core else "bronze" if (x + z) % 2 else "bronze_w")
    p.stair(-1, 9, 1, "east", "top", "bronze_st")             # the maul's flared underside
    p.stair(1, 9, 1, "west", "top", "bronze_st")
    p.set(-1, 9, 0, "bronze_w")
    p.set(-1, 9, 2, "bronze_w")
    # the LEFT arm (x 6) hangs, the fist holds the tongs down
    for y in (4, 5):
        body(6, y, 1, 5)
    p.set(6, 3, 1, "bronze_w")
    p.set(6, 2, 1, "crag_s")
    rod(p, 6, 1, 1, "down")
    return p


# ============================================================================================ the winch
def winch(span=5):
    """The chain drum (polished basalt, axis z, bronze bosses) between two bronze-capped posts, y 0 = the deck."""
    p = new()
    for z in (0, span - 1):
        p.set(0, 0, z, "pbb")
        p.set(0, 1, z, "bronze_w")
    for z in range(1, span - 1):
        pier(p, 0, 1, z, axis="z")
    p.set(0, 1, 1, "bronze")
    p.set(0, 1, span - 2, "bronze")
    return p


def catalogue():
    return {
        "columns": column_field(5, 4, lambda x, z: 2 + (x + z) % 4),
        "gate": gate_pylon(),
        "hearth": hearth(),
        "colossus": colossus(),
        "winch": winch(),
    }


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/ashborn_parts"
    os.makedirs(out, exist_ok=True)
    for k, part in catalogue().items():
        part.to_rig(k).structure().write(os.path.join(out, k + ".nbt"))
        print(k, len(part.b))
