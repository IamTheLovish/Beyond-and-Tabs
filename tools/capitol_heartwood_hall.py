#!/usr/bin/env python3
"""The Verdant Court capitol, the Heartwood Hall, redrawn as THE WORLD-TREE COURT - the centrepiece of the base.

lovish, Oct 19 2026: capitols "can be a lot bigger than the current capital size, a lot taller, a lot more detailed",
"I want it to be the center piece of the base", everything "connects" to it with "fluidity", "don't break the game".
Detailed = fine-grained (stairs, slabs, walls, fences, small plants), not chunky blocks. The bar: the Sunforged Temple of
the Sun (tools/capitol_town_centre.py build_grand), the Gravebound Necropolis Cathedral (tools/capitol_mausoleum.py) and
the Brassworks Grand Foundry (tools/capitol_foundry_hall.py).

THE VERDANT STYLE KIT (every Verdant building follows it; the capitol is its fullest expression):
  - main blocks: MOSSY STONE (mossy / cracked / chiseled stone bricks, mossy cobble, their stairs, slabs and walls) and
    LIVING WOOD (oak logs and bark for the tree, stripped birch - the Court's silver - for the mask and antlers);
  - accent: LEAVES (azalea, flowering azalea, a cherry-blossom crown), persistent and carrying their true distance;
  - light: FROGLIGHTS and SHROOMLIGHT as glowing fruit and eyes, lanterns on the mossy walls;
  - silhouette motif: ROOTS AND CANOPIES - roots arching over the ground, a canopy in layered tiers;
  - signature detail: the STAG-DRYAD (the Root Spring extractor's spirit, tools/mex_verdant.py): a pale birch mask with
    green froglight eyes under dark-oak brows, and birch antlers that are the tree's own branches;
  - edge: moss, grass, rooted dirt and podzol with mossy stepping stones, ferns, moss carpets and flowers, fading into
    the ground the same on every side (players build in every direction: no road, no front).

THE DESIGN (inspiration only, never traced: ref 48 the sandstone gothic window with vines - depth by recessing, green
over carved stone; ref 61 the ruined gothic tower with broken arches; refs 23 / 83 overgrown nature; ref 108 the
overgrown air temple; ref 106 organic arches):
  - an old TEMPLE HALL of mossy stone, the same on all four sides: a stylobate whose stair rim steps up all round, on
    each face pier - lancet - pilaster - POINTED ARCH - pilaster - lancet - pier, three depths on every face (the
    pilasters and cornice stand out, the wall, the lancets recessed into the dark where the tree's legs stand); the
    parapet is broken (ruined crenels, leaves spilling over it);
  - the WORLD TREE grew up through it: four 2 x 2 legs stand in the hall's inner corners and lean together into one
    great trunk over the crossing - a banyan standing on its roots - so the hall under it is the tree's hollow heart
    (a verdant froglight glows in the trunk's underside) and the centre spawn walks out of any of the four arches;
  - ROOTS AS FLYING BUTTRESSES: from each leg a root bursts through the hall's corner pier and arches down over a
    MOONWELL (a water cauldron between bare calcite kerbs) to plant its foot in the garden corner;
  - the STAG-DRYAD FACE is carved in the trunk on every side over the arch (birch mask and muzzle, glowing eyes, dark
    brows), its ANTLERS branch out of the brow into the canopy as the tree's own boughs;
  - a VAST LAYERED CANOPY: a wide skirt, a middle tier and an upper tier on spreading boughs, gaps between them so they
    read as layers from the RTS camera, notched over each face so the stag looks out; glowing fruit (ochre / verdant
    froglights, shroomlight) set in the tier edges, a cherry-blossom crown on top - blossom as leaves, nothing hanging;
  - the GARDEN: the paving breaks up into moss and rooted dirt, stepping stones, ferns, carpets and flowers.

WHAT THE SIZE TOUCHES IN THE GAME (the coordinator applies these; this file edits nothing):
  - tools/gen_structures.py: heartwood_hall() returns this build() (as foundry_hall() returns capitol_foundry_hall's);
  - HeartwoodHall.buildTimeBlocks stays 498 (already set; BuildingPlacement times construction by it), the starting
    types (MOSS_BLOCK, MOSSY_STONE_BRICKS) are in layer 0 (and mossy bricks above, as the old hall's walls had them -
    only layer 0 is the foundation, foundationYLayers = 1); no Java of the class needs changing;
  - BiomeBaseGameTests.capitol_details_keep_box_build_time_and_anchors: add the row
    { Buildings.HEARTWOOD_HALL, 498, new int[] { 14, 21, 14 } };
  - BuildingAmbienceClientEvents.CAPITOL_SPOTS: "heartwood_hall" -> PETAL_SPOTS below (petals falling from under the
    canopy skirt's four corner lobes, and off the blossom crown);
  - CapitolLife: none. SkirmishGameTests.heartwood_hall_frees_its_seedshapers: the 15-wide footprint fits its 16-wide
    column (it stamps from x+1) and the 22-high clearing it makes.

RULES (asserted in check()): the box; the starting types in layer 0; nothing pops off (_SunRig.check_support); leaves
persistent with their true distance (mex_verdant._leaf_distances); no water (the moonwells are cauldrons), lava,
gravity blocks, redstone or fire; under 2000 blocks; the centre spawn (ProductionBuilding.getIndoorSpawnPoint: the box
centre walked down) lands on the hall floor with the heart's open column over it and walks out; no pockets
(tools/audit_structure_pockets.py audit() on the written nbt: __main__ does).

Usage: python3 tools/capitol_heartwood_hall.py [OUT.nbt]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import capitol_town_centre as tc   # noqa: E402  - the Cap canvas (a _SunRig addressed from the centre, 4-fold sides)
import gen_structures as gs        # noqa: E402
import mex_verdant as mv           # noqa: E402  - the leaf-distance pass shared with the Root Spring

R, H = 7, 22                       # 15 x 22 x 15
O4 = tc.O4
CORNERS = tc.CORNERS
inward, outward, along, hsh = tc.inward, tc.outward, tc.along, tc.hsh

LANTERN = dict(hanging="false", waterlogged="false")
DRY = dict(waterlogged="false")
# the kit's blocks, by role
MSB, CSB, CHB, MC = "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "mossy_cobblestone"
MSS, MSSL, MSW = "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall"
LOG, WOOD = "oak_log", "oak_wood"
BIRCH, BIRCH_W, BIRCH_S, BIRCH_F = "stripped_birch_log", "stripped_birch_wood", "birch_stairs", "birch_fence"
SOIL = ("moss_block", "grass_block", "rooted_dirt", "podzol")   # what a plant may stand on
FLOWERS = ("allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "cornflower", "fern", "fern", "moss_carpet")

STARTING_TYPES = {"moss_block", "mossy_stone_bricks"}       # HeartwoodHall.startingBlockTypes
# BuildingAmbienceClientEvents.CAPITOL_SPOTS["heartwood_hall"], Fx.PETAL (structure coordinates, block x spans x..x+1):
# under the canopy skirt's four corner lobes, and over the blossom crown
PETAL_SPOTS = ((2.5, 11.5, 4.5), (12.5, 11.5, 10.5), (4.5, 11.5, 12.5), (10.5, 11.5, 2.5), (7.5, 21.5, 7.5))


def leaf(c, dx, y, dz, name="azalea_leaves"):
    """A leaf (its distance is fixed once all the wood stands); never overwrites."""
    if c.get(dx, y, dz)[0] == "air":
        c.set(dx, y, dz, name, persistent="true", distance="7", waterlogged="false")


# ====================================================================================================== ground
def garden(c):
    """y0 everywhere and the plants on rings 6-7: the hall's paving (mossy and cracked bricks, a chiseled heart - the
    instant foundation is the moss and mossy bricks, HeartwoodHall.startingBlockTypes) breaks up on ring 6 into moss
    and cobble and on ring 7 into the forest floor - moss, grass, rooted dirt, podzol - with stepping stones, ferns,
    carpets and flowers, the same on all four sides."""
    for dx, dz in c.cells(R):
        ring = max(abs(dx), abs(dz))
        h = hsh(dx, dz)
        if ring <= 5:
            name = MSB if (h % 5) else CSB
            if ring <= 1:
                name = CHB if (dx, dz) == (0, 0) else MSB
        elif ring == 6:
            name = (MSB, "moss_block", MC, "moss_block", MSB, "rooted_dirt")[h % 6]
        else:
            name = ("moss_block", "grass_block", "moss_block", "rooted_dirt", "podzol", "grass_block", MC)[h % 7]
        props = {"snowy": "false"} if name in ("grass_block", "podzol") else {}
        c.set(dx, 0, dz, name, **props)
        if ring >= 6 and name not in (MSB, MC):
            k = (h // 7) % 11
            if k < len(FLOWERS):
                c.set(dx, 1, dz, FLOWERS[k])
            elif k == 9 and ring == 7:
                c.set(dx, 1, dz, "pink_petals", facing="north", flower_amount=str(1 + h % 4))
        elif ring >= 6 and name == MC and h % 2:
            c.set(dx, 1, dz, MSSL, type="bottom", **DRY)            # a stepping stone proud of the moss
    # the foundation types must sit in layer 0 even if the hash shuffles them away
    c.set(0, 0, 6, "moss_block")


def stylobate(c):
    """y1: the hall floor (rings <= 4, moss creeping over the bricks) and the stair rim on ring 5 climbing in all
    round - broken here and there into moss carpet - so the hall is entered from every side, not only at the arches."""
    for dx, dz in c.cells(4):
        h = hsh(dx + 3, dz)
        c.set(dx, 1, dz, "moss_block" if h % 4 == 0 and max(abs(dx), abs(dz)) >= 2 else MSB)
    for o in O4.values():
        c.put(o, 1, 0, 1, CHB)
        for t in range(-5, 6):
            if abs(t) == 5:
                continue                                         # the corners: the moonwells
            if hsh(*c.side(o, 5, t)) % 7 == 3 and abs(t) > 2:
                c.put(o, 5, t, 1, "moss_carpet")                 # a step the moss has eaten
            else:
                c.st(o, 5, t, 1, inward(o), name=MSS)
    c.set(0, 1, 0, CHB)


# ====================================================================================================== hall
def hall(c):
    """The ruined temple on ring 4, y2..7: on each face corner pier - lancet - pilaster - pointed arch - pilaster -
    lancet - corner pier. The pilasters stand on ring 5 (the outer plane) with a lantern on a wall cap, the lancets are
    open slots into the dark where the tree's legs stand (the recess), the cornice of upside-down stairs juts over the
    wall and the parapet above it is ruined - crenels missing, leaves spilling over."""
    for o in O4.values():
        for t in range(-3, 4):
            for y in range(2, 7):
                a_t = abs(t)
                if a_t <= 1 and y <= 4:
                    continue                                     # the arch's opening (3 wide, 3 high)
                if a_t == 3 and y in (3, 4, 5):
                    continue                                     # the lancets
                name = MSB if hsh(t * 7 + y, y + o[0] * 3 + o[1]) % 4 else CSB
                c.put(o, 4, t, y, name)
            c.st(o, 5, t, 6, inward(o), "top", name=MSS)         # the cornice
        # the pointed arch: springers (upside-down stairs, backs to the wall) and an open point under a keystone
        for s in (-1, 1):
            c.st(o, 4, s, 5, along(o, s), "top", name=MSS)
            c.st(o, 4, 3 * s, 2, outward(o), name=MSS)           # the lancet's sill, sloping out
            c.st(o, 4, 3 * s, 6, along(o, -s), "top", name=MSS)  # its pointed head
        c.put(o, 4, 0, 5, "air")
        c.put(o, 4, 0, 6, CHB)                                   # the keystone
        # the pilasters on ring 5 either side of the arch (the stair rim gives way to their chiseled bases)
        for s in (-1, 1):
            c.put(o, 5, 2 * s, 1, CHB)
            c.put(o, 5, 2 * s, 2, MSB)
            c.put(o, 5, 2 * s, 3, MSB)
            c.put(o, 5, 2 * s, 4, MSW)
            c.put(o, 5, 2 * s, 5, "lantern", **LANTERN)
        # the ruined parapet on y7: crenels on the even cells, some fallen; leaves draped where they are gone
        for t in range(-3, 4):
            if t % 2 == 0:
                if hsh(*c.side(o, 4, t)) % 3:
                    c.put(o, 4, t, 7, MSW)
                else:
                    x, z = c.side(o, 4, t)
                    leaf(c, x, 7, z, gs.leaf_for(x, 7, z, 3))
            else:
                c.put(o, 4, t, 7, MSSL, type="bottom", **DRY)
    # the corner piers (where the roots burst through), chiseled at the foot, cracked higher up
    for (sx, sz) in CORNERS:
        x, z = 4 * sx, 4 * sz
        c.set(x, 2, z, CHB)
        for y in (3, 4, 5):
            c.set(x, y, z, MSB if y != 4 else CSB)
        c.set(x, 6, z, MC)


# ====================================================================================================== tree
def legs_and_trunk(c):
    """Four 2 x 2 legs in the hall's inner corners (rings 2-3), leaning together at y9-10 into one trunk over the
    crossing from y11. The cross between the legs stays open from the floor to the trunk's underside: the hollow heart,
    where the centre spawn stands and walks out along any axis. A verdant froglight in the underside is its light."""
    for (sx, sz) in CORNERS:
        for (qx, qz) in ((2, 2), (3, 2), (2, 3), (3, 3)):
            top = 9 if (qx, qz) != (3, 3) else 8
            for y in range(2, top + 1):
                c.set(sx * qx, y, sz * qz, LOG, axis="y")
        # the lean: the legs' inner edges step in at y10 (bark), their outer cells taper as stairs
        c.set(sx * 2, 10, sz * 2, WOOD, axis="y")
        c.set(sx * 1, 10, sz * 2, WOOD, axis="y")
        c.set(sx * 2, 10, sz * 1, WOOD, axis="y")
        c.stair(sx * 3, 10, sz * 2, "east" if sx < 0 else "west", name="oak_stairs")
        c.stair(sx * 2, 10, sz * 3, "south" if sz < 0 else "north", name="oak_stairs")
        c.stair(sx * 3, 9, sz * 3, "east" if sx < 0 else "west", name="oak_stairs")
    # y10 arches between the legs over each arm (upside-down stairs - the trunk's vault), the heart's column open
    for o in O4.values():
        c.st(o, 2, 0, 10, outward(o), "top", name="oak_stairs")
    # the trunk's base y11: 5 x 5 of bark, the heart glowing in its underside
    for dx, dz in c.cells(2):
        c.set(dx, 11, dz, WOOD, axis="y")
    c.set(0, 11, 0, "verdant_froglight", axis="y")
    for (sx, sz) in CORNERS:
        c.stair(sx * 2, 12, sz * 2, "east" if sx < 0 else "west", name="oak_stairs")   # the base's rounded shoulders
    # the trunk: 3 x 3 from y12 to y19, rounded at the top, the leader to y20
    for y in range(12, 20):
        for dx, dz in c.cells(1):
            if y == 19 and dx and dz:
                continue
            c.set(dx, y, dz, LOG, axis="y")
    c.set(0, 20, 0, LOG, axis="y")


def face(c, o):
    """The stag-dryad on the trunk's face o (the Root Spring's spirit at the capitol's scale), on ring 2, y11-15: a pale
    birch mask, a muzzle and snout over the arch, verdant froglight eyes under overhanging dark-oak brows, a moss crown
    between the burrs - and the antlers rising out of the burrs as the tree's own birch boughs."""
    # three depths: the muzzle and brows stand out on ring 3, the mask on ring 2, the eyes sunk under the brows
    c.put(o, 3, 0, 11, BIRCH_W, axis="y")                        # the muzzle, pushed out over the arch's keystone
    c.st(o, 3, 0, 12, inward(o), name=BIRCH_S)                   # the snout sloping up into the blaze
    c.put(o, 2, 0, 11, BIRCH_W, axis="y")                        # the jaw behind it
    c.put(o, 2, 0, 12, BIRCH_W, axis="y")
    for s in (-1, 1):
        c.st(o, 3, s, 11, along(o, -s), "top", name=BIRCH_S)     # the cheeks, hollowed under
        c.st(o, 2, s, 12, along(o, -s), "top", name=BIRCH_S)
        c.put(o, 2, s, 13, "verdant_froglight", axis="y")        # the eyes, sunk in the mask
        c.st(o, 3, s, 14, inward(o), "top", name="dark_oak_stairs")   # the brows, jutting over the eyes
        c.st(o, 2, 2 * s, 14, along(o, -s), "top", name="dark_oak_stairs")   # the brow's outer sweep
        c.put(o, 2, s, 14, BIRCH_W, axis="y")
        c.put(o, 2, s, 15, BIRCH, axis="y")                      # the burrs
        leaf(c, *_xyz(c, o, 2, 2 * s, 13), name="flowering_azalea_leaves")   # leafy ears flicked out
    c.put(o, 2, 0, 13, BIRCH_W, axis="y")                        # the bridge of the nose
    c.put(o, 3, 0, 13, BIRCH_W, axis="y")                        # the blaze, standing proud between the eyes
    c.put(o, 2, 0, 14, BIRCH_W, axis="y")                        # the forehead
    c.put(o, 2, 0, 15, "moss_block")                             # the moss crown between the burrs
    # the antlers: burr -> a brow tine straight up, the beam sweeping out to a knuckle, tines forking up, out and
    # toward the viewer - solid birch only at the knuckles, fence between (an open, branching line, not a pillar)
    for s in (-1, 1):
        c.put(o, 2, s, 16, BIRCH_F)                              # the brow tine
        c.st(o, 2, 2 * s, 16, along(o, -s), "top", name=BIRCH_S) # the beam leaving the burr
        c.put(o, 2, 2 * s, 17, BIRCH_F)
        c.put(o, 2, 3 * s, 17, BIRCH_F)
        c.put(o, 2, 3 * s, 18, BIRCH, axis="y")                  # the knuckle
        c.put(o, 2, 3 * s, 19, BIRCH_F)                          # the top tine
        if s == 1:
            c.put(o, 2, 3 * s, 20, BIRCH_F)                      # one tine taller: the pair reads alive, not cast
        c.put(o, 3, 3 * s, 18, BIRCH_F)                          # a tine forking toward the viewer
        c.put(o, 2, 4 * s, 18, BIRCH_F)                          # and one sweeping out (a stub: fewer sticks read
                                                                 # as antlers, a forest of them as scaffolding)


def boughs(c):
    """The boughs that carry the canopy tiers (horizontal bark on the diagonals, clear of the faces): to ring 4 under the
    skirt, ring 3 under the middle tier, ring 2 under the upper tier."""
    for (sx, sz) in CORNERS:
        for k in (2, 3, 4):
            c.set(sx * k, 12, sz * k, WOOD, axis="y")
        c.set(sx * 5, 13, sz * 4, WOOD, axis="y")
        c.set(sx * 4, 13, sz * 5, WOOD, axis="y")
        for k in (2, 3):
            c.set(sx * k, 15, sz * k, WOOD, axis="y")
        c.set(sx * 2, 18, sz * 2, WOOD, axis="y")


def in_face_zone(dx, y, dz):
    """Cells the canopy leaves clear so the stag looks out: the face and the notch in front of it down the skirt, and
    the antlers' room."""
    for o in O4.values():
        a = dx * o[0] + dz * o[1]
        t = -dx * o[1] + dz * o[0]
        if 2 <= a <= 4 and abs(t) <= 2 and 11 <= y <= 14:
            return True
        if a >= 5 and abs(t) <= 1 and 12 <= y <= 13:
            return True                                          # a V notch in the skirt: the stag looks out
        if 4 <= a <= 5 and abs(t) <= 1 and 15 <= y <= 16:
            return True                                          # the mid tier notched too: the face seen from above
        if 2 <= a <= 3 and abs(t) <= 4 and 15 <= y <= 20 and not (abs(t) <= 1 and y >= 18):
            return True
    return False


def canopy(c):
    """The layered canopy: a wide skirt (y12-13, out to the footprint's edge, round), a middle tier (y15-16), an upper
    tier (y18-19) and a cherry-blossom crown (y20-21), with a gap between tiers so each reads as a layer. Glowing fruit
    (ochre and verdant froglights, shroomlight) sits in the tiers' undersides and edges, never hanging."""
    tiers = [(12, 7.3, "skirt"), (13, 6.4, "skirt"), (15, 5.6, "mid"), (16, 4.7, "mid"), (18, 3.9, "up"),
             (19, 3.0, "up"), (20, 2.2, "crown"), (21, 1.2, "crown")]
    for y, r, tier in tiers:
        for dx, dz in c.cells(R):
            d = (dx * dx + dz * dz) ** 0.5
            h = hsh(dx * 3 + y, dz - y)
            rr = r - (0.6 if h % 5 == 0 else 0)                  # a ragged edge, not a disc
            if d > rr or in_face_zone(dx, y, dz):
                continue
            if tier == "skirt" and d < 4.3:
                continue                                         # the skirt is a ring: the trunk and faces show
            if tier == "crown":
                name = "cherry_leaves" if h % 3 else "flowering_azalea_leaves"
            elif tier == "up":
                name = "flowering_azalea_leaves" if h % 3 == 0 else ("cherry_leaves" if h % 7 == 0 else "azalea_leaves")
            else:
                name = gs.leaf_for(dx, y, dz, 4)
            edge = d > rr - 1.1
            lower = tier in ("skirt", "mid") and y in (12, 15)
            if edge and lower and h % 9 == 0 and c.get(dx, y, dz)[0] == "air":
                c.set(dx, y, dz, ("ochre_froglight", "shroomlight", "verdant_froglight")[h % 3],
                      **({} if h % 3 == 1 else {"axis": "y"}))   # glowing fruit in the edge
                continue
            leaf(c, dx, y, dz, name)
    c.set(0, 21, 0, "shroomlight")                               # the crown's heart


def roots(c):
    """The roots as flying buttresses: from each leg out through the corner pier, arching down over the moonwell to a
    foot planted in the garden corner, with side roots creeping out from the foot."""
    # the arc as (x, z, y) in the +x +z corner, every step sharing a face with the last (diagonal-only steps read as
    # separate stumps from the camera): out of the leg at y9, through the pier top, over the well, down to the foot
    ARC = ((3, 3, 9), (4, 4, 8), (4, 4, 7), (5, 4, 7), (4, 5, 7), (5, 5, 7), (5, 5, 6), (6, 5, 6), (5, 6, 6),
           (6, 5, 5), (5, 6, 5), (6, 6, 5), (6, 6, 4), (7, 6, 4), (6, 7, 4), (7, 6, 3), (6, 7, 3), (7, 7, 3),
           (7, 7, 2), (7, 7, 1))
    for (sx, sz) in CORNERS:
        for (k, j, y) in ARC:
            c.set(sx * k, y, sz * j, WOOD, axis="y")
        c.set(sx * 7, 0, sz * 7, "rooted_dirt")
        # bark stairs on the arc's back so its top line slopes rather than steps
        c.stair(sx * 5, 8, sz * 5, "east" if sx < 0 else "west", name="oak_stairs")
        c.stair(sx * 6, 7, sz * 6, "east" if sx < 0 else "west", name="oak_stairs")
        c.stair(sx * 7, 5, sz * 7, "east" if sx < 0 else "west", name="oak_stairs")
        # side roots creeping out of the foot along the garden edge, a carpet of moss beyond them
        c.set(sx * 7, 1, sz * 5, "mangrove_roots", **DRY)
        c.set(sx * 5, 1, sz * 7, "mangrove_roots", **DRY)
        c.set(sx * 7, 1, sz * 4, "moss_carpet")
        c.set(sx * 4, 1, sz * 7, "moss_carpet")


def moonwells(c):
    """A moonwell under each root arch on the stylobate's corner: a still basin (a water cauldron - no flowing water)
    between two bare calcite kerbs (the Court's silver)."""
    for (sx, sz) in CORNERS:
        c.set(sx * 5, 1, sz * 5, "water_cauldron", level="3")
        c.set(sx * 6, 1, sz * 5, "calcite")
        c.set(sx * 5, 1, sz * 6, "calcite")
        # no candles on the kerbs: the kerbs sit on the corner's way out, and a unit leaving the Court stood on a candle
        # there and could not step off (heartwood_hall_frees_its_seedshapers, Oct 19) - bare calcite is a clean step


def hall_dressing(c):
    """Inside the heart: moss carpet creeping over the floor (passable) at the legs' feet, ferns by the arches."""
    for (sx, sz) in CORNERS:
        c.set(sx * 1, 2, sz * 3, "moss_carpet")
        c.set(sx * 3, 2, sz * 1, "moss_carpet")
    for o in O4.values():
        for s in (-1, 1):
            x, _, z = _xyz(c, o, 6, 2 * s, 1)
            # a fern needs soil under it (it pops off brick): plant it only where the garden left moss or earth
            if c.get(x, 1, z)[0] == "air" and c.get(x, 0, z)[0] in SOIL:
                c.set(x, 1, z, "fern")


def _xyz(c, o, a, t, y):
    x, z = c.side(o, a, t)
    return x, y, z


# ====================================================================================================== build
def build():
    c = tc.Cap(R, H)
    garden(c)
    stylobate(c)
    hall(c)
    legs_and_trunk(c)
    for o in O4.values():
        face(c, o)
    boughs(c)
    roots(c)
    moonwells(c)
    canopy(c)
    hall_dressing(c)
    mv._leaf_distances(c.rig)
    c.rig.settle()
    c.rig.check_support("heartwood_hall")
    return c.rig.structure()


# ===================================================================================================== rules
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "soul_sand", "magma_block", "vine", "hanging_roots",
             "spore_blossom", "suspicious_")


def check(s, box=(2 * R + 1, H, 2 * R + 1)):
    """The rules the game needs, on the finished Structure (the support check ran in build())."""
    assert s.size == box, "the box is %s, expected %s" % (s.size, box)
    full = {(x, y, z): s.palette[i] for (x, y, z), i in s.blocks.items() if s.palette[i][0] != "air"}
    names = {p: v[0] for p, v in full.items()}
    n = len(names)
    assert n < 2000, n
    assert max(p[0] for p in names) == box[0] - 1 and max(p[1] for p in names) == box[1] - 1 \
        and max(p[2] for p in names) == box[2] - 1, "the drawn blocks do not fill the box"
    layer0 = {nm for (x, y, z), nm in names.items() if y == 0}
    assert STARTING_TYPES <= layer0, "starting types missing from layer 0: %s" % (STARTING_TYPES - layer0)
    bad = sorted({nm for nm in names.values() if any(nm == f or nm.endswith(f) or nm.startswith(f) for f in FORBIDDEN)
                  and nm != "water_cauldron"})
    assert not bad, "forbidden blocks: %s" % bad
    # plants (flowers, ferns, petals) stand on soil, or vanilla pops them off
    for (x, y, z), nm in names.items():
        if nm in FLOWERS + ("pink_petals",) and nm != "moss_carpet":
            assert names.get((x, y - 1, z)) in SOIL, "a %s at %s stands on %s" % (nm, (x, y, z), names.get((x, y - 1, z)))
    # leaves: persistent, and the distance the game would compute
    for p, (nm, props) in full.items():
        if nm.endswith("_leaves"):
            pr = dict(props)
            assert pr.get("persistent") == "true", p
    # the spawn: the box centre walked down while the block below is air lands on the hall floor, with the heart's
    # column open over it up to the trunk's underside
    cx, cz = box[0] // 2, box[2] // 2
    y = (box[1] - 1) // 2
    while y > 0 and (cx, y - 1, cz) not in names:
        y -= 1
    assert y == 2 and all((cx, yy, cz) not in names for yy in range(2, 11)), "the centre spawn lands at y%d" % y
    # petal spots: under a leaf or fruit within two cells above (the skirt's corner lobes), the crown spot over it
    for (x, y, z) in PETAL_SPOTS:
        bx, by, bz = int(x), int(y), int(z)
        assert any((bx, by + d, bz) in names for d in range(-1, 3)), "nothing at the petal spot %s" % ((x, y, z),)
    return n


def audit_pockets(path):
    import audit_structure_pockets as asp
    return asp.audit("heartwood_hall", path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/heartwood_hall.nbt"
    st_ = build()
    print("blocks:", check(st_))
    st_.write(out)
    one_way, ground, roof, rooms, note = audit_pockets(out)
    print("pockets: one-way %d, ground %d, roof-only %d, rooms %d%s" % (len(one_way), len(ground), len(roof),
                                                                        len(rooms), note))
    assert not one_way and not ground and "TRAPPED" not in note, (one_way[:8], ground[:8], note)
    print("wrote", out)
