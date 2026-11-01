#!/usr/bin/env python3
"""CONCEPT (rebuild): the Gravebound VEHICLE LAB, THE CHARNEL WORKS - where the Gravebound war-wagons are made (the
bone-ribbed hearse drawn by skeletal horses, the bone ballista; bodies up to 3 x 3 x 4). L1 and L3 on a 17 x 17
footprint, assembled from parts (tools/gravebound_lab_parts.py on tools/gothic_parts.py / gothic_towers.py), one family
with the Necropolis Cathedral (tools/capitol_mausoleum.py) and the Graveyard (tools/lab_graveyard.py). Writes
design/vehicle_labs/gravebound_l1.nbt and gravebound_l3.nbt only - NOT into the game's structure trees.

THE REFERENCE, STUDIED
  - SEDLEC OSSUARY (Kutna Hora): a small gothic chapel over a crypt whose inside is built of bone - a chandelier said
    to hold every bone of the body hanging in the middle, chains/garlands of skulls swagged from the vault, four
    PYRAMIDS of stacked skulls round the chandelier (vertical rows of skulls on their sides), a coat of arms of bones
    in a corner, heaps of bones behind mesh. What makes it read: bone used as STRUCTURE (ribs, swags, rows), ivory
    against black, repetition of one module (the skull) in rows, light hung in the middle of a dark space.
  - THE PARIS CATACOMBS: the entrance a small dark lodge; the ossuary walls are femurs stacked ends-out with BANDS of
    skulls every metre - a wall striped bone/black horizontally; a lintel over the passage carved with its motto.
  - HIGHGATE CEMETERY: the EGYPTIAN AVENUE - a massive gate (an arch with a heavy cornice, a winged disc on it,
    lotus-bud columns either side, OBELISKS flanking), behind it a once-roofed passage lined with eight family VAULTS a
    side, each a heavy cast-iron door with an INVERTED TORCH; it climbs to the CIRCLE OF LEBANON, a sunken ring of
    vaults round a raised mound carrying an old cedar. What reads: doors in a rhythm, set in deep battered walls,
    heavy cornices, the ground cut down so the vaults sit in a trench, obelisks as the vertical accent.
  - lovish's strongest castle ref (the black gothic castle): a mass of CLUSTERED BLACK SPIRES of different heights
    rising from one body, lit windows scattered up the towers, the silhouette stepping up to one tallest needle.
  - The synthesis: a black deepslate-and-blackstone works whose bay is roofed by a RIBCAGE of bone arches (the
    chandelier-and-swag vault of Sedlec turned into a crane frame), flanked by a vault range with Highgate's iron
    doors and inverted torches, a crypt pit (the trench of the Circle of Lebanon) with the hoist over it, a gate of
    skull posts and obelisks with a heavy cornice, and a cluster of black spires at the back stepping up to the
    north-east needle - ivory on black, cyan soul light, pale green glass.

ANCHORS (structure-local block coords x, y, z; in game these become client-drawn moving parts):
  gantry ribs      x 4 / 7 / 10    three bone ribs on legs at z 5 / z 11 (y 1..9), bowed to the spine (static frame)
  gantry spine     (3..11, 13, 8)  the bone spine from the gate's gable to the keep
  gantry winch     (8, 14, 8)      dark-oak drum between blackstone cheeks riding the spine (travels x 4..10); its
                                   chain (8, 10..12, 8) and bone hook (8, 9, 8) drop to the lane floor
  hoist beam       (5..9, 12, 14)  bone beam over the crypt pit on the piers (4, 1..12, 14) / (10, 1..12, 14)
  hoist cradle     (5..8, 4..7, 13..15)  the HEARSE HULL (gravebound_lab_parts.hearse_hull) on chains (6, 8..11, 14) /
                                   (7, 8..11, 14); rises out of the sealed pit (soul fire under an iron grate at y 2)
                                   and swings onto the lane when done
  gate leaf        (2, 9..10, 6..10) the CRYPT SLAB drawn RAISED into the gate head on chains (2, 11, 7) / (2, 11, 9),
                                   a green glow (2, 9, 8) under it; drops to y 1..2 to close the lane
  rose             (1, 14, 8)      the soul-green rose over the gate (the one glowing core; a skull at its heart)
  spawn cell       (8, 1, 8)       the lane floor under the ribcage
  L3 adds: two great ribs (x 4 / 7) from the range ridge (z 2, y 15) and pit-wall piers (z 16, y 13) to an upper
  spine (3..9, 20, 8); the BONE CHANDELIER (6, 16..19, 8) hung from it (it turns); the upper winch (5, 21, 8); the
  keep's second stage over y 14 (the landmark grows to y 35).

THE PLAN (x 0..16 west -> east, z 0..16 north -> south; the RTS camera looks from the south-west at the roofs, the
west face x = 0 and the south face z = 16). The hierarchy (amendment item 2), back to front:
  (a) THE LANDMARK: the black KEEP (5 x 5, x 11..15, z 6..10) on the lane's axis at the end of the bay - pilasters,
      string courses, lit lancets climbing, a corbelled parapet with obelisk spirelets, a black spire to y 25 (L1).
  (b) THE GATE: the WEST FRONT (x 0..3, z 5..11, ref05 in black): twin spires (x 1..3, z 2..4 / 12..14) framing a
      pointed catacomb arch of bone over the lane (5 wide, 8 clear), a string course, the soul-green ROSE with bone
      spokes and a skull heart, a steep crocketed gable with a wither skull, soul lanterns on the gable's feet.
  (c) secondary masses stepping down: the VAULT RANGE (x 4..10, z 0..4; lit lancet bays between the rib legs, a slate
      roof with pinnacles on the ridge), the NE turret (x 13..15, z 1..3), the SE turret on its graveyard terrace;
      the CRYPT PIT (x 4..10, z 12..16) with the hoist and hull, its south wall a row of barred vault grilles
      (Highgate's vault doors) between bone-banded pilasters.
  (d) the calm ground: a low PALE crag (calcite / diorite / andesite under the dark walls, ref26) round the west and
      south feet, two twisted dead trees (ref16), worn flags with polished wheel ruts in the lane, dark soil.
  Light: only the lit lancets, the rose, the gate's glow and the two gable lanterns (amendment item 3).

Usage: python3 tools/lab_vehicle_gravebound.py   (writes both .nbt and checks them)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs                 # noqa: E402
import gothic_parts as GP                   # noqa: E402
import gothic_towers as GT                  # noqa: E402
import gravebound_lab_parts as GL           # noqa: E402
from gothic_parts import Part, toward, OPP  # noqa: E402

SX, SZ = 17, 17
SY1, SY3 = 26, 36
LANE_Z = range(6, 11)
LANE_X = range(0, 9)
LANE_H = 8
SPAWN = (8, 1, 8)
OUT1 = "design/vehicle_labs/gravebound_l1.nbt"
OUT3 = "design/vehicle_labs/gravebound_l3.nbt"
FINE = ("stairs", "slab", "_wall", "fence", "pane", "bars", "trapdoor", "button", "chain", "lantern", "candle", "rod",
        "door", "carpet", "banner")
DRY = GL.DRY


def hsh(x, y, z=0):
    return ((x + 31) * 73856093 ^ (y + 7) * 83492791 ^ (z + 17) * 19349663) % 97


# ============================================================================================ ground
def ground(p):
    """y 0, calm: worn deepslate flags in the lane with polished wheel ruts, dark brick under the masses, dark soil at
    the edges."""
    for x in range(SX):
        for z in range(SZ):
            h = hsh(x, 0, z)
            if z in LANE_Z and x <= 10:
                n = "polished_deepslate" if z in (7, 9) else ("cracked_deepslate_tiles" if h % 6 == 0 else
                                                              "deepslate_tiles")
            elif x <= 1 or z >= 15 or z <= 0:
                n = ("podzol", "coarse_dirt", "podzol", "rooted_dirt", "podzol", "soul_soil")[h % 6]
            else:
                n = "deepslate_bricks" if h % 5 else "cobbled_deepslate"
            p.set(x, 0, z, n, **({"snowy": "false"} if n == "podzol" else {}))


def masonry(p, x, y, z, seed=0):
    """Deepslate in big calm zones (bricks, a zone of tiles), a blackstone plinth two courses high."""
    if y <= 2:
        p.set(x, y, z, "polished_blackstone_bricks" if hsh(x, y, z) % 9 else "cracked_polished_blackstone_bricks")
        return
    zone = hsh((x + z) // 5, y // 5, seed) % 100
    n = "deepslate_tiles" if zone < 25 else "deepslate_bricks"
    if n == "deepslate_bricks" and hsh(x, y, z) % 13 == 0:
        n = "cracked_deepslate_bricks"
    p.set(x, y, z, n)


def crag(p):
    """The ground story: the lab stands on a low crag of deepslate (ref14's mound) - battered rock round the camera
    faces stepping down in stairs to soil, the lane cutting through it as the road; twisted dead trees (ref16)."""
    rock = ("calcite", "diorite", "andesite", "calcite", "tuff")       # PALE rock under dark walls (ref26)
    for x in range(SX):
        for z in range(SZ):
            if z in LANE_Z and x <= 10:
                continue
            edge = x == 0 or z == 16 or (x == 1 and (z <= 1 or z >= 15)) or (z == 15 and x <= 3)
            if not edge or p.get(x, 1, z) != "air":
                continue
            h = 1 + (hsh(x, 1, z) % 2 if (z <= 1 or z >= 15) else 0)
            for y in range(1, h + 1):
                p.set(x, y, z, rock[hsh(x, y, z) % 5])
    # the crag's top dressed with soil, its outer lip in stairs
    for x in range(SX):
        for z in range(SZ):
            n = p.get(x, 1, z)
            if n in ("calcite", "diorite", "andesite", "tuff"):
                top = 2 if p.get(x, 2, z) != "air" else 1
                if p.get(x, top + 1, z) == "air":
                    out = "west" if x == 0 else "south" if z == 16 else None
                    if out and hsh(x, 3, z) % 3 == 0:
                        p.stair(x, top, z, OPP[out], "bottom", "diorite_stairs")
                    elif hsh(x, 4, z) % 3 == 0:
                        p.set(x, top, z, "podzol", snowy="false")
    GL.put(p, GL.dead_tree(1), 0, 1, 1)
    GL.put(p, GL.dead_tree(2), 1, 2, 15)


# ============================================================================================ the twin spires
def spires(p):
    """The west front's twin spires (ref05, in black): NW at (2, 3), SW at (2, 13), 3 x 3, lit lancets up their camera
    faces, a battered foot of stairs."""
    tops = []
    for (cz, win) in ((3, ("west",)), (13, ("west", "south"))):
        sp, top = GL.front_spire(10, win)
        p.place(sp, 2, 0, cz)
        for (x, z) in GT.ring_of("square", 2, 1):
            if p.get(2 + x, 1, cz + z) == "air" and 0 <= 2 + x and not (cz + z in LANE_Z):
                p.stair(2 + x, 1, cz + z, toward(x, z), "bottom", "polished_blackstone_brick_stairs")
        tops.append(top)
    return tops


# ============================================================================================ the west front + gate
def west_front(p):
    """THE CATACOMB GATE and the ROSE over it (ref24: one glowing core, the gate glowing beneath). The block x 1..3,
    z 5..11 between the spires, its wall plane x 1; x 0 carries the outer order: bone jamb shafts, a pointed arch of
    bone voussoirs over the lane, a string course, the rose's bone ring with a skull at its heart, the gable's crockets.
    Inside the head (x 2) the CRYPT SLAB hangs drawn up on chains, a green glow under it."""
    for x in (1, 2, 3):
        for z in range(5, 12):
            for y in range(1, 18):
                if z in LANE_Z and y <= LANE_H:
                    continue
                masonry(p, x, y, z, 2)
    # arch profiles: (x, set of open (y, z) above the lane, soffit stairs, keystone)
    def arch(x, apex):
        if apex >= 10:
            for z in (7, 8, 9):
                p.clear(x, 9, z)
            p.stair(x, 9, 6, "north", "top", "polished_blackstone_brick_stairs")
            p.stair(x, 9, 10, "south", "top", "polished_blackstone_brick_stairs")
        if apex >= 11:
            p.clear(x, 10, 8)
            p.stair(x, 10, 7, "north", "top", "polished_blackstone_brick_stairs")
            p.stair(x, 10, 9, "south", "top", "polished_blackstone_brick_stairs")
        p.set(x, apex, 8, "chiseled_polished_blackstone")
    arch(1, 10)
    arch(3, 11)
    # the slab, raised, on two chains; the hollow over it; the glow under it
    for z in range(6, 11):
        p.set(2, 9, z, "polished_deepslate")
        p.set(2, 10, z, "polished_deepslate")
        p.clear(2, 11, z)
    p.set(2, 9, 8, "verdant_froglight", axis="y")
    for z in (7, 9):
        GL.chain(p, 2, 11, z)
    # the outer order at x 0: bone jamb shafts, capitals, the pointed bone arch, spandrels
    for z in (5, 11):
        p.set(0, 1, z, "polished_blackstone_bricks")
        for y in range(2, 8):
            GL.bone(p, 0, y, z)
        p.set(0, 8, z, "chiseled_polished_blackstone")
        p.set(0, 9, z, "polished_blackstone_bricks")
    for z, f in ((6, "north"), (10, "south")):
        p.stair(0, 9, z, f, "top", GL.BONE_ST)
    for z, f in ((7, "north"), (9, "south")):
        p.stair(0, 10, z, f, "top", GL.BONE_ST)
        p.set(0, 10, z - 1 if z == 7 else z + 1, "bone_block", axis="x")
    p.set(0, 11, 8, "bone_block", axis="x")
    for z in (7, 9):
        p.stair(0, 11, z, "north" if z == 7 else "south", "bottom", "polished_blackstone_brick_stairs")
    # pilaster lines framing the front (x 0, z 5 / 11) up to the gable
    for z in (5, 11):
        for y in range(10, 17):
            p.set(0, y, z, "polished_blackstone_brick_wall" if y % 4 else "chiseled_polished_blackstone")
    # the string course under the rose
    for z in range(6, 11):
        p.stair(0, 12, z, "east", "top", "polished_blackstone_brick_stairs")
    # THE ROSE: centre (y 15, z 8), pale green glass at x 1 with bone spokes, a bone ring at x 0, a skull heart,
    # froglights behind (x 2) - the one glowing core of the front
    cy = 14
    for dz in range(-3, 4):
        for dy in range(-3, 4):
            d = (dz * dz + dy * dy) ** 0.5
            y, z = cy + dy, 8 + dz
            if d <= 2.3:
                spoke = (dz == 0 or dy == 0) and d > 0.5
                p.set(1, y, z, "bone_block" if spoke else ("chiseled_deepslate" if d < 0.5 else
                                                          "lime_stained_glass_pane"), **({"axis": "x"} if spoke else {}))
                if not spoke and d >= 0.5:
                    p.set(2, y, z, "verdant_froglight", axis="y")
            elif d <= 3.2 and abs(dz) <= 3:
                if abs(dz) == 3 or abs(dy) == 3:
                    p.stair(0, y, z, OPP[toward(dz, dy)] if False else "east", "top" if dy > 0 else "bottom",
                            GL.BONE_ST)
                else:
                    GL.bone(p, 0, y, z, "x")
    GL.skull(p, 0, cy, 8, 4)
    # the gable: x 0..3, steep rake stairs climbing from z 5 / 11 to the apex at z 8, crockets at x 0
    yg = 17
    for k in range(4):
        y = yg + k
        zl, zr = 5 + k, 11 - k
        for x in range(0, 4):
            for z in range(zl + 1, zr):
                p.set(x, y, z, "deepslate_bricks" if x else "deepslate_tiles")
            if zl < zr:
                p.stair(x, y, zl, "south", "bottom", "deepslate_tile_stairs")
                p.stair(x, y, zr, "north", "bottom", "deepslate_tile_stairs")
            else:
                p.set(x, y, zl, "deepslate_tiles")
        for z in range(zl + 1, zr):
            p.set(0, y, z, "deepslate_bricks")
    for x in range(0, 4):
        p.set(x, 21, 8, "deepslate_tile_wall")
    p.set(0, 21, 8, "chiseled_polished_blackstone")
    GL.skull(p, 0, 22, 8, 4, wither=True)
    for (z, y) in ((5, 17), (11, 17)):
        p.place(GL.obelisk(3, cap="lantern"), 0, y, z)               # the front's two soul lights
    for z in (6, 10):
        p.set(0, 18, z, "polished_blackstone_brick_wall")             # crockets on the rake
    # the gable's inner face toward the ribcage (x 3) gets a lit lancet pair
    for z in (7, 9):
        for y in (12, 13):
            p.set(3, y, z, "lime_stained_glass_pane")


# ============================================================================================ the ribcage gantry
RIB_X = (4, 7, 10)
SPINE_Y = 13
RIB_L1 = ((0, 9), (0, 10), (1, 11), (2, 12), (3, 13))


def ribcage(p):
    """THE RIBCAGE: three bone ribs on legs at z 5 / z 11 bowed over the bay to a spine at y 13 running from the gate's
    gable (x 3) to the keep (x 11), vertebrae down the spine, the winch at x 8 with its chain and bone hook."""
    for x in RIB_X:
        GL.put(p, GL.rib(3, 9, path=RIB_L1), x, 0, 5)
        for z in (5, 11):
            p.set(x, 1, z, "chiseled_polished_blackstone")
    for x in range(3, 12):
        if x not in RIB_X:
            GL.bone(p, x, SPINE_Y, 8, "x")
        if 4 < x < 10 and x not in RIB_X:
            p.stair(x, SPINE_Y, 7, "south", "top", GL.BONE_ST)
            p.stair(x, SPINE_Y, 9, "north", "top", GL.BONE_ST)
    GL.skull(p, 4, SPINE_Y + 1, 8, 4, wither=True)
    GL.skull(p, 7, SPINE_Y + 1, 8, 4, wither=True)
    p.set(8, SPINE_Y + 1, 8, "dark_oak_log", axis="z")
    p.set(8, SPINE_Y + 1, 7, "polished_blackstone_wall")
    p.set(8, SPINE_Y + 1, 9, "polished_blackstone_wall")
    p.set(9, SPINE_Y + 1, 8, "dark_oak_fence")
    for y in (10, 11, 12):
        GL.chain(p, 8, y, 8)
    p.stair(8, 9, 8, "west", "top", GL.BONE_ST)                 # the hook


# ============================================================================================ the vault range (north)
def vault_range(p):
    """x 4..10, z 0..4: walls to y 9, a lit room (floor y 4) behind ordered bays on the bay side - the rib legs are its
    pilasters, each bay a lancet pair with sill and hood over a plinth course - and a steep slate roof (two courses a
    step) to a ridge at z 2 bristling with pinnacles (ref10)."""
    for x in range(4, 11):
        for z in range(0, 5):
            for y in range(1, 10):
                if z in (0, 4) or x in (4, 10) or y <= 3:
                    masonry(p, x, y, z, 5)
                elif y == 4:
                    p.set(x, y, z, "polished_deepslate")
            p.set(x, 10, z, "deepslate_tiles") if 0 < z < 4 else None
    for k in range(2):
        for x in range(4, 11):
            for z in (k, 4 - k):
                p.set(x, 10 + 2 * k, z, "deepslate_tiles")
                p.stair(x, 11 + 2 * k, z, "south" if z < 2 else "north", "bottom", "deepslate_tile_stairs")
            if k == 1:
                for z in (1, 2, 3):
                    p.set(x, 11, z, "deepslate_tiles") if z == 2 else None
    for x in range(4, 11):
        p.set(x, 12, 2, "deepslate_tiles")
        p.set(x, 13, 2, "deepslate_tiles")
        if x in (5, 8):
            p.place(GL.obelisk(2, cap="rod"), x, 14, 2)
        else:
            p.set(x, 14, 2, "deepslate_tile_wall")
    # eaves over the bay (z 5), pierced by the ribs
    for x in range(4, 11):
        if x not in RIB_X:
            p.stair(x, 10, 5, "north", "bottom", "deepslate_tile_stairs")
            p.stair(x, 9, 5, "north", "top", "polished_blackstone_brick_stairs")
    # the bays
    for x in (5, 6, 8, 9):
        for y in (5, 6, 7):
            p.set(x, y, 4, "lime_stained_glass_pane")
        p.set(x, 8, 4, "polished_deepslate_stairs", facing="east" if x in (5, 8) else "west", half="top",
              shape="straight", waterlogged="false")
        p.slab(x, 4, 5, "top", "polished_deepslate_slab")
        p.stair(x, 3, 5, "north", "bottom", "polished_blackstone_brick_stairs")
    for x in (5, 9):
        p.set(x, 5, 3, "verdant_froglight", axis="y")


# ============================================================================================ the keep (landmark)
def keep(p, l3=False):
    """THE LANDMARK at the end of the bay on the lane's axis: a 5 x 5 black keep, pilasters, string courses, lit
    lancets climbing its west and south faces, a corbelled parapet with obelisk spirelets, a black spire with lucarnes
    and a bone finial - the tallest thing in a Gravebound base after the capitol. L3: a second stage over it."""
    t, top = GL.dark_tower(r=2, h=12, seed=2, spire_per=3, foot=False, lantern_corner=None)
    p.place(t, 13, 0, 8)
    if l3:
        t2, top2 = GL.dark_tower(r=2, h=7, seed=4, spire_per=4, foot=False, lantern_corner=None)
        p.place(t2, 13, 14, 8)
        return top2 + 14
    return top


def turrets(p):
    """The ring of lesser towers stepping down to the camera (ref14): the NE turret (h 10) and the SE turret on its
    graveyard terrace (h 6 over y 3)."""
    t, _ = GL.dark_tower(r=1, h=10, seed=5, foot=False, lantern_corner=None)
    p.place(t, 14, 0, 2)
    # the SE graveyard terrace: a retaining wall with a coping, soil on top, graves
    for x in range(11, 17):
        for z in range(12, 17):
            for y in (1, 2):
                masonry(p, x, y, z, 11)
            p.set(x, 3, z, "podzol", snowy="false")
    for x in range(11, 17):
        p.stair(x, 3, 16, "north", "bottom", "polished_blackstone_brick_stairs")
        p.stair(x, 2, 16, "north", "top", "polished_blackstone_brick_stairs") if False else None
    for z in range(12, 16):
        p.stair(11, 3, z, "east", "bottom", "polished_blackstone_brick_stairs")
    t, _ = GL.dark_tower(r=1, h=7, seed=6, foot=False, lantern_corner=None)
    p.place(t, 14, 3, 14)
    GL.put(p, GL.headstone(1), 12, 4, 14)
    GL.put(p, GL.headstone(0), 12, 4, 13)


# ============================================================================================ the crypt pit and hoist
def crypt_pit(p):
    """A sealed pit (x 5..9, z 13..15): soul fire on soul soil under an iron grate (y 2), a kerb of blackstone walls; its
    south wall (z 16) barred vault grilles between pilasters on the rib planes (x 4, 7, 10); the hoist piers (x 4 / 10,
    z 14) carry a bone beam at y 12; the hearse hull hangs on two chains, rising out of the pit."""
    for x in range(4, 11):
        for z in range(12, 17):
            inner = 5 <= x <= 9 and 13 <= z <= 15
            if inner:
                p.set(x, 0, z, "soul_soil")
                if (x + z) % 3 == 0:
                    p.set(x, 1, z, "soul_fire")
                p.set(x, 2, z, "iron_bars")
            elif z == 16:
                pil = x in RIB_X
                for y in (1, 2, 3, 4):
                    if pil:
                        p.set(x, y, z, "bone_block" if y == 3 else "polished_blackstone_bricks",
                              **({"axis": "y"} if y == 3 else {}))
                    else:
                        p.set(x, y, z, "iron_bars" if y in (2, 3) else "deepslate_bricks")
                if pil:
                    p.set(x, 5, z, "chiseled_polished_blackstone")
                    p.stair(x, 6, z, "north", "bottom", "polished_blackstone_brick_stairs")
                else:
                    p.slab(x, 5, z, "bottom", "polished_blackstone_brick_slab")
            else:
                p.set(x, 1, z, "polished_blackstone_brick_wall")
                p.set(x, 2, z, "polished_blackstone_brick_wall")
    GL.put(p, GL.hearse_hull(), 5, 4, 14)
    for x in (4, 10):
        for y in range(1, 12):
            if y % 4 == 3:
                GL.bone(p, x, y, 14)
            else:
                p.set(x, y, 14, "polished_blackstone_bricks")
        p.set(x, 12, 14, "chiseled_polished_blackstone")
        for z in (13, 15):
            p.stair(x, 11, z, "south" if z == 13 else "north", "top", "polished_blackstone_brick_stairs")
    for x in range(5, 10):
        GL.bone(p, x, 12, 14, "x")
    for x in (6, 7):
        for y in (8, 9, 10, 11):
            GL.chain(p, x, y, 14)


# ============================================================================================ L3 additions
def arc(p, x, zl, yl, za, ya):
    """A bone arc in the plane x from a leg top (zl, yl) up to the apex (za, ya): a quarter ellipse, bone where it is
    steep, smooth-quartz stairs as the rounded back and soffit where it flattens."""
    import math
    n = abs(za - zl)
    s = 1 if za > zl else -1
    prev = yl
    for i in range(n + 1):
        z = zl + s * i
        y = yl + round((ya - yl) * math.sin(math.pi / 2 * i / n))
        for yy in range(prev, y + 1):
            GL.bone(p, x, yy, z, "y" if y - prev > 0 else "z")
        if 0 < i < n and y - prev <= 1:
            p.stair(x, y + 1, z, "south" if s > 0 else "north", "bottom", GL.BONE_ST)
            p.stair(x, prev - 1 if y > prev else y - 1, z, "north" if s > 0 else "south", "top", GL.BONE_ST)
        prev = y


def l3_adds(p):
    """The great ribcage: two taller ribs (x 4, x 7) springing from the vault range's ridge (z 2) and from piers that
    rise out of the pit wall's pilasters (z 16) to an upper spine at y 20; the BONE CHANDELIER (Sedlec's centrepiece)
    hung from it at x 6, a crown of skulls and soul lanterns round a green heart; an upper winch."""
    for x in (4, 7):
        for y in range(5, 13):
            p.set(x, y, 16, "polished_blackstone_bricks" if y % 4 else "chiseled_polished_blackstone")
        p.stair(x, 12, 15, "south", "top", "polished_blackstone_brick_stairs")
        arc(p, x, 16, 13, 8, 20)
        arc(p, x, 2, 15, 8, 20)
        GL.skull(p, x, 21, 8, 4, wither=True)
    for x in range(3, 10):
        if x not in (4, 7):
            GL.bone(p, x, 20, 8, "x")
            p.stair(x, 20, 7, "south", "top", GL.BONE_ST)
            p.stair(x, 20, 9, "north", "top", GL.BONE_ST)
    p.set(3, 20, 8, "chiseled_polished_blackstone")
    p.set(3, 19, 8, "deepslate_bricks")
    # the chandelier at x 6
    GL.chain(p, 6, 19, 8)
    GL.chain(p, 6, 18, 8)
    p.set(6, 17, 8, "verdant_froglight", axis="y")
    for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        p.stair(6 + dx, 17, 8 + dz, f, "top", GL.BONE_ST)
        GL.lantern(p, 6 + dx, 18, 8 + dz)
    for (dx, dz, r) in ((1, 1, 14), (-1, 1, 2), (1, -1, 10), (-1, -1, 6)):
        p.slab(6 + dx, 17, 8 + dz, "top", "polished_blackstone_brick_slab")
        GL.skull(p, 6 + dx, 18, 8 + dz, r)
    p.stair(6, 16, 8, "west", "top", GL.BONE_ST)
    p.set(5, 21, 8, "dark_oak_log", axis="z")
    p.set(5, 21, 7, "polished_blackstone_wall")
    p.set(5, 21, 9, "polished_blackstone_wall")


# ============================================================================================ articulation
PLAIN = ("deepslate_bricks", "deepslate_tiles", "cracked_deepslate_bricks")


def articulate(p, ymax=40):
    """ref28's density on the plain wall zones the camera sees (west, south and the bay's walls): wherever a run of
    plain deepslate faces open air, a blackstone pilaster of walls stands proud every third cell and a string course
    of upside-down stairs runs every fourth course - so no plain run is longer than two cells and every face has a
    proud plane. Never in the lane, never over glass, bone or openings."""
    adds = {}
    for (x, y, z), (n, _) in list(p.b.items()):
        if n not in PLAIN or y < 3 or y > ymax:
            continue
        for f in ("west", "south", "north"):
            vx, vz = GP.VEC[f]
            ox, oz = x + vx, z + vz
            if not (0 <= ox < SX and 0 <= oz < SZ):
                continue
            if p.get(ox, y, oz) != "air" or (oz in LANE_Z and ox in LANE_X and y <= LANE_H + 1):
                continue
            if f == "north" and not (4 <= oz <= 11):
                continue
            along = z if f == "west" else x
            if y % 4 == 3:
                adds[(ox, y, oz)] = ("polished_blackstone_brick_stairs",
                                     dict(facing=OPP[f], half="top", shape="straight", waterlogged="false"))
            elif along % 3 == 0:
                adds[(ox, y, oz)] = ("polished_blackstone_brick_wall", {})
    # coping: every exposed wall head of plain masonry takes a slab (ref12: trim on every edge)
    for (x, y, z), (n, _) in list(p.b.items()):
        if n in PLAIN + ("polished_blackstone_bricks",) and y >= 3 and (x, y + 1, z) not in p.b \
                and (x, y + 1, z) not in adds and y + 1 < 35:
            adds[(x, y + 1, z)] = ("polished_blackstone_brick_slab", dict(type="bottom", waterlogged="false"))
    for k, v in adds.items():
        if k not in p.b:
            p.b[k] = v


# ============================================================================================ assemble
def clear_lane(p):
    for x in LANE_X:
        for z in LANE_Z:
            for y in range(1, LANE_H + 1):
                p.clear(x, y, z)


def draw(level):
    p = Part(GL.pal())
    ground(p)
    spires(p)
    vault_range(p)
    keep(p)
    turrets(p)
    crypt_pit(p)
    west_front(p)
    ribcage(p)
    crag(p)
    articulate(p)
    clear_lane(p)
    if level == 3:
        l1 = dict(p.b)
        keep(p, l3=True)
        l3_adds(p)
        articulate(p)
        clear_lane(p)
        for k, v in l1.items():                      # L3 keeps every L1 cell non-air
            if p.b.get(k, ("air",))[0] in ("air", "cave_air"):
                p.b[k] = v
    return p


OOB = []


def to_rig(p, sy):
    rig = gs._SunRig(SX, sy, SZ)
    for (x, y, z), (n, props_) in p.b.items():
        if n in ("air", "cave_air"):
            continue
        if not (0 <= x < SX and 0 <= z < SZ and 0 <= y < sy):
            OOB.append(((x, y, z), n)); continue
        rig.set(x, y, z, n, **props_)
    return rig


def seal_pockets(p, sy, path, tag):
    """Cells a unit could stand in but never leave (inside thick masses, under the roofs): fill their feet with stone
    and repeat until the pocket audit is clean (a hollow wall core, not a room anyone sees)."""
    import audit_structure_pockets as ap
    for _ in range(12):
        rig = to_rig(p, sy)
        rig.settle()
        rig.structure().write(path)
        one_way, ground_, roof, rooms, note = ap.audit(tag, path)
        if not ground_ and not one_way:
            return rig, roof, note
        for (x, y, z) in ground_ + one_way:
            p.set(x, y, z, "deepslate_bricks")
    raise AssertionError("%s: pockets will not seal: %s %s" % (tag, one_way[:8], ground_[:8]))


def check(p, rig, path, sy, tag):
    import audit_structure_pockets as ap
    n = {k: v[0] for k, v in rig.b.items()}
    assert all((x, 0, z) in n for x in range(SX) for z in range(SZ)), "a hole in the foundation"
    busy = [(x, y, z, n[(x, y, z)]) for x in LANE_X for z in LANE_Z for y in range(1, LANE_H + 1) if (x, y, z) in n]
    assert not busy, "%s: the exit lane is narrowed: %s" % (tag, busy[:8])
    bad = sorted({v for v in n.values() if any(f in v for f in (
        "water", "lava", "sand", "gravel", "_concrete_powder", "redstone", "_leaves", "copper", "coral", "sapling",
        "vine", "torch", "cobweb", "tuff_bricks", "polished_tuff"))})
    assert not bad, "forbidden: %s" % bad
    fires = [k for k, v in n.items() if "fire" in v]
    assert all(4 <= x <= 9 and 13 <= z <= 15 and y == 1 for (x, y, z) in fires), "fire outside the sealed pit"
    rig.check_support(tag)
    one_way, ground_, roof, rooms, note = ap.audit(tag, path)
    # the game's spawn is the L1 box centre walked down (changeStructure never moves it): checked on L1; on L3 the
    # audit's own walk from the taller box's mid-height lands on the ribcage, which no unit reaches
    assert not one_way and not ground_ and (sy != SY1 or "TRAPPED" not in note), (one_way, ground_, note)
    pad = ap.Pad(ap.load_nbt(path))
    seen, stack, out_west = {SPAWN}, [SPAWN], False
    while stack:
        c = stack.pop()
        if c[0] < 0 and c[2] in LANE_Z:
            out_west = True
            break
        for nb in ap.neighbours(pad, *c):
            if nb not in seen and -3 <= nb[0] <= SX + 2 and -3 <= nb[2] <= SZ + 2 and 1 <= nb[1] <= sy:
                seen.add(nb)
                stack.append(nb)
    assert out_west, "the spawn does not walk out of the west face"
    fine = sum(1 for v in n.values() if any(f in v for f in FINE))
    top = max(y for (_, y, _) in n)
    return "%s 17x%dx17 (top y %d), %d blocks, %.0f%% fine, %d roof-only cells; lane clear, no pockets, spawn walks " \
           "out west" % (tag, sy, top, len(n), 100.0 * fine / len(n), len(roof)), n


def build():
    os.makedirs(os.path.dirname(OUT1), exist_ok=True)
    p1 = draw(1)
    rig1, _, _ = seal_pockets(p1, SY1, OUT1, "gravebound_l1")
    msg1, n1 = check(p1, rig1, OUT1, SY1, "gravebound_l1")
    p3 = draw(3)
    for k in p1.b:                                   # the sealed cores carry into L3
        if p1.b[k][0] not in ("air", "cave_air") and p3.b.get(k, ("air",))[0] in ("air", "cave_air"):
            p3.b[k] = p1.b[k]
    rig3, _, _ = seal_pockets(p3, SY3, OUT3, "gravebound_l3")
    msg3, n3 = check(p3, rig3, OUT3, SY3, "gravebound_l3")
    lost = [k for k in n1 if k not in n3]
    assert not lost, "L3 drops L1 cells: %s" % lost[:8]
    print(msg1)
    print(msg3)


if __name__ == "__main__":
    build()
