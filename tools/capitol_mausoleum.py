#!/usr/bin/env python3
"""The Gravebound (Death) capitol, the Mausoleum, redrawn as a NECROPOLIS CATHEDRAL - the centrepiece of the base.

lovish, Oct 19 2026: capitols "can be a lot bigger than the current capital size, a lot taller, a lot more detailed",
"I want it to be the center piece of the base", everything "connects" to it with "fluidity", "don't break the game".
Detailed = fine-grained (stairs, slabs, walls, chains, skulls), not chunky blocks. The bar is the Sunforged Temple of
the Sun (tools/capitol_town_centre.py build_grand, 15 x 20 x 15).

THE GRAVEBOUND STYLE KIT (every Gravebound building follows it; the capitol is its fullest expression):
  - main blocks: deepslate (bricks / tiles / polished) and blackstone (polished / bricks / chiseled);
  - accent: BONE (bone-block ribs on the crossing tower, bone door posts at every portal, bones in the graves);
  - light: SOUL LANTERNS on every pinnacle and a GREEN GLOW (verdant froglight hearts behind green glass);
  - silhouette motif: SPIRES - a forest of them, a tall one in the middle;
  - signature detail: CHAINS and SKULLS (chains hang under every flying buttress, skulls in the ossuary niches, on
    the portal gables, on the gargoyles, in the graves);
  - edge: an ossuary garden that fades into dark soil (podzol, rooted dirt, soul soil), the same on every side.

THE DESIGN (inspiration only, never traced: ref 42 the grey gothic castle - layered depth in one wall, stepped spires;
ref 73 the cathedral with flying buttresses and spires at every corner; ref 44 the dark brick wall - pier, bay, pier
with chains, soul lanterns and finials; ref 47 the gargoyles sculpted in blocks; refs 90-91 the dark skylines):
  - a CRYPT PODIUM (11 x 11, two blocks high) whose wall runs pier - ossuary niche (a skull in the dark) - pier; a
    grand stair climbs it on each axis between bone door posts and soul-lantern cheek walls;
  - a Greek-cross cathedral on it, the same on all four sides (players build in every direction): each arm ends in a
    gothic WEST FRONT - a pointed portal under a gable hood (a wimperg with a wither skull), a ROSE WINDOW of green
    glass round a glowing verdant heart, a steep tile gable with a lit apex finial - flanked by two corner towers;
  - four corner TOWERS (2 x 2), lancets glowing green, each crowned by a needle spire with a soul lantern and a
    crouching GARGOYLE on its outer corner (stair body, upside-down-stair wings, a wither skull for a head);
  - FLYING BUTTRESSES: eight piers stand out on the ground ring and leap over the podium terrace to the towers - pier,
    bay, pier on every face - each pier a lit pinnacle, a chain hanging under each arch;
  - the CROSSING TOWER rises over the four roofs on bone-rib corners with tall green lancets, carries the open soul
    BELFRY (the bell the game swings, CapitolLife) and the tallest spire;
  - the OSSUARY GARDEN: the ground ring of paving breaks up into graves (headstones, slabs, skulls, bones, a few grave
    lights) and dark soil, so whichever way the base grows the capitol's ground runs on into it.

WHAT THE SIZE TOUCHES IN THE GAME (the coordinator applies these; this file edits nothing):
  - BiomeBaseGameTests.capitol_details_keep_box_build_time_and_anchors pins the box: {9,12,9} -> {14,21,14};
  - CapitolLife.MOTIONS: the bell moves from (4, 8, 4) to BELL below; the test checks its cell is empty, its ceiling
    solid and its four sides clear (check() asserts the same here);
  - BuildingAmbienceClientEvents.CAPITOL_SPOTS["mausoleum"]: the soul spots move to SOUL_SPOTS below;
  - tools/gen_capitol_details.py mausoleum() returns this build() (as town_centre() returns the Temple);
  - Mausoleum.buildTimeBlocks stays 364 (BuildingPlacement times construction by it, not the block count), the
    starting types (STONE, STONE_BRICKS, STONE_BRICK_STAIRS) stay and sit in layer 0, foundationYLayers stays 4.

RULES (asserted in check()): the box; the starting types in layer 0 and nowhere above; nothing pops off
(_SunRig.check_support); the bell's cell; no water, lava, gravity blocks, redstone or fire (no campfires: soul
lanterns are the light); under 2000 blocks; the centre spawn (ProductionBuilding.getIndoorSpawnPoint: the box centre
walked down) lands on the nave floor under a tall open crossing and walks out of any of the four portals - and no
pockets (run tools/audit_structure_pockets.py's audit() on the written nbt: __main__ does).

Usage: python3 tools/capitol_mausoleum.py [OUT.nbt]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import capitol_town_centre as tc   # noqa: E402  - the Cap canvas (a _SunRig addressed from the centre, 4-fold sides)

R, H = 7, 22                       # 15 x 22 x 15
O4 = tc.O4
CORNERS = tc.CORNERS
inward, outward, along, hsh = tc.inward, tc.outward, tc.along, tc.hsh

LANTERN = dict(hanging="false", waterlogged="false")
DRY = dict(waterlogged="false")
# the kit's blocks, by role
DB, DT, PD = "deepslate_bricks", "deepslate_tiles", "polished_deepslate"
PBB, CPB, PB = "polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone"
PBW, DTW = "polished_blackstone_wall", "deepslate_tile_wall"
DTS, DBS, PBS = "deepslate_tile_stairs", "deepslate_brick_stairs", "polished_blackstone_stairs"
GLASS = "green_stained_glass"
# the value roles (the massing/value rounds swap these; tools/capitol_mausoleum_massing.py)
WALL = DB                          # the church's walls: arms, fronts, towers, buttress piers, crossing corners
CROWN_WALL = DT                    # the crossing tower's walls over the roofs
ROOF, ROOFS = DT, DTS              # roof ridges / spire caps, and the roof and spire stairs
TOWER_TOP = 12                     # the corner towers' band (their spires rise 4 over it)
GLOW = "verdant_froglight"
# standing skull rotation (16ths of a turn, 0 = facing south): facing out of each side, and out of each corner
SKULL_OUT = {"south": "0", "west": "4", "north": "8", "east": "12"}
SKULL_CORNER = {(-1, 1): "2", (-1, -1): "6", (1, -1): "10", (1, 1): "14"}

# where the game looks (structure coordinates: the centre cell is (R, y, R))
BELL = (R, 15, R)                  # CapitolLife.MOTIONS["mausoleum"]: the belfry's empty cell under the spire
# BuildingAmbienceClientEvents.CAPITOL_SPOTS["mausoleum"]: Fx.SOUL over the four tower-spire lanterns and the crown
SOUL_SPOTS = ((4.5, 16.7, 4.5), (10.5, 16.7, 4.5), (4.5, 16.7, 10.5), (10.5, 16.7, 10.5), (7.5, 21.7, 7.5))
STARTING_TYPES = {"stone", "stone_bricks", "stone_brick_stairs"}   # Mausoleum.startingBlockTypes


def skull(c, dx, y, dz, rot, wither=True):
    c.set(dx, y, dz, "wither_skeleton_skull" if wither else "skeleton_skull", rotation=rot)


# ======================================================================================================= ground
def ossuary_garden(c):
    """Rings 6-7 at ground level: the paving round the podium breaks up into graves and dark soil - no kerb, no road,
    the same on every side. The starting types (the instant foundation, Mausoleum.startingBlockTypes) are the old grey
    flags and stones in it: stone-brick top stairs as the worn approach flags on the axes, stone bricks and stone
    scattered through the soil."""
    for dx, dz in c.cells(R, 6):
        ring = max(abs(dx), abs(dz))
        t = min(abs(dx), abs(dz))
        h = hsh(dx, dz)
        if ring == 6:
            name = (DT, "cracked_deepslate_tiles", PD, DT, "stone_bricks", "podzol")[h % 6]
        else:
            name = ("podzol", "cobbled_deepslate", "soul_soil", "podzol", "stone", "soul_soil", "stone_bricks", "podzol")[h % 8]
        if t <= 1:
            c.stair(dx, 0, dz, tc.inward_of(dx, dz), "top", "stone_brick_stairs")   # the approach flags
            continue
        c.set(dx, 0, dz, name, **({"snowy": "false"} if name == "podzol" else {}))
        if ring == 7 and t >= 2:
            grave(c, dx, dz, h)


def grave(c, dx, dz, h):
    """One cell of the ossuary on the outer ring: a headstone, a grave slab, a skull, a bone, a grave light or bare
    soil. Single thin members with soil round them: never a closed cell (no pockets)."""
    o = (1 if dx == R else -1 if dx == -R else 0, 1 if dz == R else -1 if dz == -R else 0)
    if 0 in o:
        face = tc.FACE[o]
        rot = SKULL_OUT[face]
    else:
        rot = SKULL_CORNER[o]
    k = h % 9
    t = min(abs(dx), abs(dz))
    if t in (3, 5) and k in (0, 3, 5, 7):
        k = 2                     # the way out of the bays between the piers stays open: a skull, not a stone
    if k in (0, 5):
        c.set(dx, 1, dz, DTW)                                   # a headstone
    elif k == 1:
        c.slab(dx, 1, dz, "deepslate_tile_slab")                # a grave slab
    elif k == 2:
        skull(c, dx, 1, dz, rot, wither=False)
    elif k == 3:
        c.set(dx, 1, dz, "bone_block", axis="x" if dz in (R, -R) else "z")   # a bone half-sunk in the soil
    elif k == 7 and abs(dx) != abs(dz):
        c.set(dx, 1, dz, "soul_lantern", **LANTERN)             # a grave light


# ====================================================================================================== podium
def podium(c):
    """The crypt podium (rings <= 5, y1-2) and its face on ring 5: pier - niche - pier, an ossuary skull in each niche,
    the grand stair on each axis."""
    c.disc(5, 0, "stone")                                       # under the podium: the instant foundation
    for y in (1, 2):
        c.disc(5, y, PD if y == 2 else DB)
    for o in O4.values():
        for t in range(-5, 6):
            c.put(o, 5, t, 1, PBB if abs(t) in (2, 4, 5) else DB)
            c.put(o, 5, t, 2, PD)
        # ossuary niches between the piers: a skull in the dark under the terrace's lip (one cell high: no unit fits)
        for s in (-1, 1):
            c.put(o, 5, 3 * s, 1, "air")
            x, z = c.side(o, 5, 3 * s)
            skull(c, x, 1, z, SKULL_OUT[outward(o)], wither=False)
            c.st(o, 5, 3 * s, 2, outward(o), "top", DBS)        # the niche lintel, a moulded lip
        # the grand stair: two flights on the axis, cheek walls with soul lanterns
        for t in (-1, 0, 1):
            c.st(o, 6, t, 1, inward(o), name=DBS)
            c.put(o, 5, t, 1, DB)
            c.st(o, 5, t, 2, inward(o), name=DBS)
        for s in (-1, 1):
            c.put(o, 6, 2 * s, 1, PBW)
            c.put(o, 6, 2 * s, 2, "soul_lantern", **LANTERN)
    # the podium corners: an ossuary obelisk on the ground ring's corner
    for (sx, sz) in CORNERS:
        x, z = sx * 6, sz * 6
        c.set(x, 1, z, PBB)
        c.set(x, 2, z, CPB)
        c.set(x, 3, z, "bone_block", axis="y")
        skull(c, x, 4, z, SKULL_CORNER[(sx, sz)])


# ====================================================================================================== church
def arms(c):
    """The four arms of the cross (rings 0..4, five wide): side walls on |t| = 2, a steep tile gable roof, and the
    gothic front on ring 4 - portal, wimperg, rose window, gable, apex finial."""
    for o in O4.values():
        for a in range(2, 5):
            for s in (-1, 1):
                for y in range(3, 11):
                    c.put(o, a, 2 * s, y, WALL)
            # the roof: tile stairs climbing in from the eaves, a tile ridge, blackstone cresting
            for s in (-1, 1):
                c.st(o, a, 2 * s, 11, along(o, -s), name=ROOFS)
                c.st(o, a, 1 * s, 12, along(o, -s), name=ROOFS)
            c.put(o, a, 0, 13, ROOF)
            c.put(o, a, 0, 14, PBW)
        # the front wall, ring 4
        for t in range(-1, 2):
            for y in range(3, 12):
                c.put(o, 4, t, y, WALL)
        c.put(o, 4, 0, 12, WALL)
        # the portal: 3 wide, a pointed head (upside-down stair shoulders, a chiseled keystone)
        for t in (-1, 0, 1):
            for y in (3, 4):
                c.put(o, 4, t, y, "air")
        c.put(o, 4, 0, 5, "air")
        for s in (-1, 1):
            c.st(o, 4, s, 5, along(o, s), "top", PBS)
        c.put(o, 4, 0, 6, CPB)
        # the wimperg (the gable hood over the portal) on ring 5, carried by bone door posts
        for s in (-1, 1):
            c.put(o, 5, 2 * s, 3, "bone_block", axis="y")
            c.put(o, 5, 2 * s, 4, "bone_block", axis="y")
            c.st(o, 5, 2 * s, 5, along(o, -s), name=PBS)
            c.st(o, 5, s, 6, along(o, -s), name=PBS)
        c.put(o, 5, 0, 7, CPB)
        x, z = c.side(o, 5, 0)
        skull(c, x, 8, z, SKULL_OUT[outward(o)])
        # the rose window: green glass round a glowing verdant heart, its corners rounded by stairs
        c.put(o, 4, 0, 9, GLOW, axis="y")
        for (t, y) in ((-1, 9), (1, 9), (0, 8), (0, 10)):
            c.put(o, 4, t, y, GLASS)
        for s in (-1, 1):
            c.st(o, 4, s, 10, along(o, s), "top", DBS)
            c.st(o, 4, s, 8, along(o, s), name=DBS)
        # the apex finial: a lit soul lantern on the cresting's end
        c.put(o, 4, 0, 14, CPB)
        c.put(o, 4, 0, 15, "soul_lantern", **LANTERN)


def towers(c):
    """The four corner towers (2 x 2 on rings 3-4): deepslate with a blackstone quoin, green lancets lit from a verdant
    heart, a blackstone band, then a needle spire with a soul lantern on the inner cell and a GARGOYLE crouched on the
    outer corner, leaning out over the terrace (ref 47): a stair body, upside-down-stair wings on ring 5, a corbel
    and a wither-skull head out on the diagonal. Lower than the crossing so the skyline steps up to the middle."""
    top = TOWER_TOP
    for (sx, sz) in CORNERS:
        out_x, out_z = ("east" if sx > 0 else "west"), ("south" if sz > 0 else "north")
        for qx in (3, 4):
            for qz in (3, 4):
                for y in range(3, top):
                    c.set(sx * qx, y, sz * qz, WALL)
                c.set(sx * qx, top, sz * qz, PBB)              # the band
        for y in range(3, top):
            c.set(sx * 4, y, sz * 4, CPB if y % 4 == 0 else PBB)   # the quoin
        for y in (6, 7, 10):
            c.set(sx * 3, y, sz * 3, GLOW, axis="y")            # the light behind both lancets
            c.set(sx * 4, y, sz * 3, GLASS)
            c.set(sx * 3, y, sz * 4, GLASS)
        for y in (5, 9):
            c.stair(sx * 4, y, sz * 3, out_x, "bottom", DBS)    # sills
            c.stair(sx * 3, y, sz * 4, out_z, "bottom", DBS)
        # the spire: stairs sloping off the band, a needle of walls on the inner cell, a soul lantern
        c.stair(sx * 4, top + 1, sz * 3, inward((sx, 0)), "bottom", ROOFS)
        c.stair(sx * 3, top + 1, sz * 4, inward((0, sz)), "bottom", ROOFS)
        c.set(sx * 3, top + 1, sz * 3, ROOF)
        for y in (top + 2, top + 3):
            c.set(sx * 3, y, sz * 3, PBW)
        c.set(sx * 3, top + 4, sz * 3, "soul_lantern", **LANTERN)
        # the gargoyle
        c.stair(sx * 4, top + 1, sz * 4, "north" if sz > 0 else "south", "bottom", PBS)
        c.stair(sx * 5, top, sz * 4, out_x, "top", PBS)         # wings, spread over the terrace
        c.stair(sx * 4, top, sz * 5, out_z, "top", PBS)
        c.stair(sx * 5, top, sz * 5, out_z, "top", PBS)         # the corbel the head leans out on
        skull(c, sx * 5, top + 1, sz * 5, SKULL_CORNER[(sx, sz)], wither=False)   # bone-white: reads on black
        for y in (top - 1, top - 2):
            c.set(sx * 5, y, sz * 5, "chain", axis="y", waterlogged="false")   # a chain from its jaw


def buttresses(c):
    """Eight flying buttresses: a pier on the ground ring (ring 6) leaps over the podium terrace (ring 5) to the tower;
    a chain hangs under each arch; each pier ends in a lit pinnacle."""
    for o in O4.values():
        for s in (-1, 1):
            t = 4 * s
            for y in range(1, 9):
                c.put(o, 6, t, y, PBB if y in (1, 5) else WALL)
            c.put(o, 6, t, 9, PBW)
            c.put(o, 6, t, 10, "soul_lantern", **LANTERN)
            c.st(o, 5, t, 8, outward(o), "top", DBS)            # the arch's underside, springing from the pier
            c.st(o, 5, t, 9, inward(o), name=DBS)               # the flyer's back, rising to the tower
            for y in (6, 7):
                c.put(o, 5, t, y, "chain", axis="y", waterlogged="false")   # the kit's chains, under every arch


def crossing(c):
    """The crossing tower over the four roofs: ring 2 from y11, bone ribs on its corners rising into lit pinnacles, a
    green lancet on each face; the open soul BELFRY on ring 1 at y15 (the bell the game swings hangs in its middle,
    CapitolLife: an empty cell, a beam over it, nothing beside it); then the TALLEST SPIRE - tile eaves, a stepped
    tile cone and a blackstone needle to a soul lantern at the very top, so the skyline climbs to the middle from
    every side (buttress pinnacles y10 -> gable finials and tower spires y15-16 -> rib pinnacles y16 -> spire y21)."""
    for o in O4.values():
        for t in range(-1, 2):
            for y in range(11, 14):
                c.put(o, 2, t, y, CROWN_WALL)
        c.put(o, 2, 0, 12, GLASS)
        c.st(o, 3, 0, 11, outward(o), "top", DBS)               # the lancet's sill over the ridge
        for t in (-1, 0, 1):
            c.st(o, 3, t, 14, inward(o), "top", DBS)            # the cornice corbels
            c.put(o, 2, t, 14, PBB)
    for (sx, sz) in CORNERS:
        for y in range(3, 11):
            c.set(sx * 2, y, sz * 2, WALL)
        for y in range(11, 15):
            c.set(sx * 2, y, sz * 2, "bone_block", axis="y")    # the bone ribs
        c.set(sx * 2, 15, sz * 2, PBW)                          # unlit needles: the tower spires beside them carry
        c.set(sx * 2, 16, sz * 2, PBW)                          # the light, one lantern per spire cluster
    # the belfry floor (the crossing's vault) with the hall's green light under it
    c.disc(1, 14, PD)
    c.set(0, 13, 0, GLOW, axis="y")
    # the belfry: bone posts on ring 1's corners; the bell cell and its four sides stay empty
    for (sx, sz) in CORNERS:
        c.set(sx, 15, sz, "bone_block", axis="y")
    c.ring_stairs(1, 16, "top", DTS, out=True)                  # the belfry's eaves
    c.set(0, 16, 0, PBB)                                        # the beam the bell hangs from
    # the spire
    c.ring_stairs(1, 17, name=ROOFS)
    c.set(0, 17, 0, ROOF)
    c.set(0, 18, 0, CPB)
    for y in (19, 20):
        c.set(0, y, 0, PBW)
    c.set(0, 21, 0, "soul_lantern", **LANTERN)


def nave(c):
    """The cross-shaped nave inside: open from the floor (y2) up; a verdant inlay cross on the floor."""
    for dx, dz in c.cells(3):
        if min(abs(dx), abs(dz)) <= 1:
            for y in range(3, 11):
                c.set(dx, y, dz, "air")
    for o in O4.values():
        for a in (1, 2, 3):
            c.put(o, a, 0, 2, PBB)


def build():
    c = tc.Cap(R, H)
    ossuary_garden(c)
    podium(c)
    arms(c)
    nave(c)
    towers(c)
    buttresses(c)
    crossing(c)
    # the nave's cleared cells stay as explicit air entries: placing them clears whatever stood in the hall
    c.rig.settle()
    c.rig.check_support("mausoleum")
    return c.rig.structure()


# ===================================================================================================== rules
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "soul_sand", "magma_block")


def check(s, box=(2 * R + 1, H, 2 * R + 1)):
    """The rules the game needs, on the finished Structure (the support check ran in build())."""
    assert s.size == box, "the box is %s, expected %s" % (s.size, box)
    names = {(x, y, z): s.palette[i][0] for (x, y, z), i in s.blocks.items() if s.palette[i][0] != "air"}
    n = len(names)
    assert n < 2000, n
    assert max(p[0] for p in names) == box[0] - 1 and max(p[1] for p in names) == box[1] - 1 \
        and max(p[2] for p in names) == box[2] - 1, "the drawn blocks do not fill the box"
    layer0 = {nm for (x, y, z), nm in names.items() if y == 0}
    assert STARTING_TYPES <= layer0, "starting types missing from layer 0: %s" % (STARTING_TYPES - layer0)
    above = {nm for (x, y, z), nm in names.items() if y > 0} & STARTING_TYPES
    assert not above, "a starting type above layer 0 would join the instant foundation queue: %s" % above
    bad = sorted({nm for nm in names.values() if any(nm == f or nm.endswith(f) for f in FORBIDDEN)})
    assert not bad, "forbidden blocks: %s" % bad
    # the bell (BiomeBaseGameTests / tools/check_sail_hubs.py): empty cell, solid above, nothing beside it
    bx, by, bz = BELL
    assert (bx, by, bz) not in names and (bx, by + 1, bz) in names, "the bell cell / beam"
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        assert (bx + dx, by, bz + dz) not in names, "the bell would swing into %s" % ((bx + dx, by, bz + dz),)
    # the spawn: the box centre walked down while the block below is air, onto the nave floor
    cx, cz = box[0] // 2, box[2] // 2
    y = (box[1] - 1) // 2
    while y > 0 and (cx, y - 1, cz) not in names:
        y -= 1
    assert y == 3 and (cx, y, cz) not in names and (cx, y + 1, cz) not in names, "the centre spawn lands at y%d" % y
    for (x, y, z) in SOUL_SPOTS:
        assert names.get((int(x), int(y), int(z))) == "soul_lantern", "no soul lantern under the spot %s" % ((x, y, z),)
    return n


def audit_pockets(path):
    import audit_structure_pockets as asp
    one_way, ground, roof, rooms, note = asp.audit("mausoleum", path)
    return one_way, ground, roof, rooms, note


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mausoleum.nbt"
    st = build()
    print("blocks:", check(st))
    st.write(out)
    one_way, ground, roof, rooms, note = audit_pockets(out)
    print("pockets: one-way %d, ground %d, roof-only %d, rooms %d%s" % (len(one_way), len(ground), len(roof),
                                                                        len(rooms), note))
    assert not one_way and not ground and "TRAPPED" not in note, (one_way[:8], ground[:8], note)
    print("wrote", out)
