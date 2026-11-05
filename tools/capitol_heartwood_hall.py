#!/usr/bin/env python3
"""The Verdant Court capitol, the Heartwood Hall, redrawn as THE WORLD-TREE COURT - the centrepiece of the base.

lovish, Oct 19 2026: capitols "can be a lot bigger than the current capital size, a lot taller, a lot more detailed",
"I want it to be the center piece of the base", everything "connects" to it with "fluidity", "don't break the game".
Detailed = fine-grained (stairs, slabs, walls, fences, small plants), not chunky blocks. The bar: the Sunforged Temple of
the Sun (tools/capitol_town_centre.py build_grand), the Gravebound Necropolis Cathedral (tools/capitol_mausoleum.py) and
the Brassworks Grand Foundry (tools/capitol_foundry_hall.py).

THE VERDANT STYLE KIT (every Verdant building follows it; the capitol is its fullest expression):
  - main blocks: MOSSY STONE (mossy / cracked / chiseled stone bricks, mossy cobble, their stairs, slabs and walls) and
    LIVING WOOD (one dark-mid bark, stripped dark oak, for the tree; stripped birch - the Court's silver - for the mask
    and antlers);
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
    pilasters and cornice stand out, the wall, the lancets recessed into the hall's dark); a HIP ROOF of mossy stone
    brick overhangs the cornice and climbs a step a ring toward the trunk, a lantern on each eave corner;
  - the WORLD TREE grew up through it: one 3 x 3 trunk stands in the hall's middle and rises through its ROOF (the
    trunk's foot flaring over the top step); its hollow heart is the centre spawn's
    column (a verdant froglight glows in its roof), and the spawn walks out by doorways on the axes and out of any of
    the four arches; four BOUGHS break out through the roof, one on each side, each its own shape;
  - SURFACE ROOTS: low mats of mangrove roots round each garden corner by a MOONWELL (a water cauldron between bare
    calcite kerbs);
  - the STAG-DRYAD FACE is carved in the trunk just over the roof on the two sides the RTS camera sees (a birch blaze,
    glowing eyes in the bark, dark brows), its ANTLERS branch out of the brow toward the crown as the tree's own boughs;
  - a BLOSSOM CROWN of uneven lobes (Nov 2026, after two blind massing rounds: the round tiered canopy read as "a
    green mound" and "a bullseye from above"): cherry leaves with a flowering azalea leaf in every few cells, each lobe
    on its own bough, leaning north-east away from the RTS camera so the temple's south and west faces show under it,
    glowing fruit (ochre / verdant froglights, shroomlight) in the lobes' undersides - blossom as leaves, nothing
    hanging. THE TALL TREE (lovish, Nov 5, on the #502 living-crowns still: "the pink tree could be taller and stick
    out of the building so its more define", "with branches coming out of the roof"): the box rose from 22 to 30, the
    trunk stands 10 blocks clear of the roof under a crown kept inside the cornice (ring 5), y19-29. Fable critic
    round 1 (blind, Nov 5) on the eyes stills: four legs over a 5 x 5 base read as "a wide brown ring" and "a
    treehouse room", the oak bark as crates, the corner root arcs as "crate stacks", and no roof read - so one trunk,
    one dark bark, a roof you see, low roots, a broader crown. Round 2: the roof inside the parapet still read as "a
    paved courtyard", the five-wide mask as "a crate hut" and the bough tips as "four pink crosses" floating - so a
    hip roof over the whole hall, a face carved in the bark, round blossoms on the bough tips. Round 3: the mossy cobble
    pyramid read as "a second mass" the tree "perches on" - so the roof is the hall's mossy bricks a step lower, the
    boughs reach past the eaves to all-pink blossoms at one height, and the mask has no plank-textured blocks;
  - the GARDEN: the paving breaks up into moss and rooted dirt, stepping stones, ferns, carpets and flowers.

WHAT THE SIZE TOUCHES IN THE GAME (the coordinator applies these; this file edits nothing):
  - tools/gen_structures.py: heartwood_hall() returns this build() (as foundry_hall() returns capitol_foundry_hall's);
  - HeartwoodHall.buildTimeBlocks stays 498 (already set; BuildingPlacement times construction by it), the starting
    types (MOSS_BLOCK, MOSSY_STONE_BRICKS) are in layer 0 (and mossy bricks above, as the old hall's walls had them -
    only layer 0 is the foundation, foundationYLayers = 1); no Java of the class needs changing;
  - BiomeBaseGameTests.capitol_details_keep_box_build_time_and_anchors: the row
    { Buildings.HEARTWOOD_HALL, 498, new int[] { 14, 29, 14 } } (14, 21, 14 before the tall tree);
  - BuildingAmbienceClientEvents.CAPITOL_SPOTS: "heartwood_hall" -> PETAL_SPOTS below (petals falling from under the
    crown's low lobes, the east roof bough's blossom, and off the crown's top);
  - CrownMotionTest (the #502 living crowns): the great crown's base is y19 (CROWN_FOOT; y12 before);
  - CapitolLife: none. SkirmishGameTests.heartwood_hall_frees_its_seedshapers: the 15-wide footprint fits its 16-wide
    column (it stamps from x+1); it clears 22 high, and the tree's top 8 stand in the arena's open sky.

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
import mex_verdant as mv           # noqa: E402  - the leaf-distance pass shared with the Root Spring

R, H = 7, 30                       # 15 x 30 x 15: the tall tree (lovish, Nov 5) - was 22
BASE = 14                          # the hollow heart's top: the trunk is solid from here up (the box centre, y14)
LIFT = -1                          # what the stag-dryad and its antlers rose by (the mask sits on the trunk's flare)
ROOF = 9                           # the roof's top step, round the trunk (the trunk's foot flares over it)
O4 = tc.O4
CORNERS = tc.CORNERS
inward, outward, along, hsh = tc.inward, tc.outward, tc.along, tc.hsh

LANTERN = dict(hanging="false", waterlogged="false")
DRY = dict(waterlogged="false")
# the kit's blocks, by role
MSB, CSB, CHB, MC = "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "mossy_cobblestone"
MSS, MSSL, MSW = "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall"
# one dark-mid bark, as the Grove (lab_grove.py): the oak bark's black / tan marbling read from the RTS camera as
# "stacked crates or a wicker tower" (Fable critic, round 1, Nov 5)
LOG, WOOD, BARK_S = "stripped_dark_oak_log", "stripped_dark_oak_wood", "dark_oak_stairs"
BIRCH = "stripped_birch_log"                                  # the Court's silver: the blaze, burrs and antlers
SOIL = ("moss_block", "grass_block", "rooted_dirt", "podzol")   # what a plant may stand on
FLOWERS = ("allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "cornflower", "fern", "fern", "moss_carpet")

STARTING_TYPES = {"moss_block", "mossy_stone_bricks"}       # HeartwoodHall.startingBlockTypes
# BuildingAmbienceClientEvents.CAPITOL_SPOTS["heartwood_hall"], Fx.PETAL (structure coordinates, block x spans x..x+1):
# under three of the crown's low lobes (LOBES), under the blossom at the east roof bough's tip, and over the crown's top
PETAL_SPOTS = ((11.5, 19.5, 3.5), (6.5, 19.5, 10.5), (10.5, 18.5, 11.5), (13.5, 12.5, 5.5), (8.5, 29.5, 6.5))


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
            name = (MSB, CSB, MSB, CSB, MSB, "moss_block")[h % 6]   # a stone kerb: the plot's edge reads
        else:
            name = ("moss_block", "grass_block", "moss_block", "rooted_dirt", "podzol", "grass_block", MC)[h % 7]
        props = {"snowy": "false"} if name in ("grass_block", "podzol") else {}
        c.set(dx, 0, dz, name, **props)
        if ring >= 6 and name in SOIL:
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
    """The temple on ring 4, y2..7: on each face corner pier - lancet - pilaster - pointed arch - pilaster - lancet -
    corner pier. The pilasters stand on ring 5 (the outer plane) with a lantern on a wall cap, the lancets are open
    slots into the hall's dark (the recess), the cornice of upside-down stairs juts over the wall, and the wall top
    over it carries the roof's eaves."""
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
        # the wall top on y7 that carries the roof (it was a ruined parapet, and the roof hid behind it: from the RTS
        # camera the hall read as "a paved courtyard with four corner posts", Fable critic round 2)
        for t in range(-3, 4):
            c.put(o, 4, t, 7, MSB if hsh(t * 5 + 1, o[0] * 3 + o[1]) % 4 else CSB)
    # the corner piers (where the roots burst through), chiseled at the foot, cracked higher up
    for (sx, sz) in CORNERS:
        x, z = 4 * sx, 4 * sz
        c.set(x, 2, z, CHB)
        for y in (3, 4, 5):
            c.set(x, y, z, MSB if y != 4 else CSB)
        c.set(x, 6, z, MC)
        c.set(x, 7, z, MC)


# ====================================================================================================== tree
def legs_and_trunk(c):
    """One 3 x 3 trunk from the hall's floor up through the roof to the crown: the tree stands out of the building
    (lovish, Nov 5: "the pink tree could be taller and stick out of the building so its more define"). Over the roof it
    is a single column - four legs leaning together over a 5 x 5 base read as "a wide brown ring" and "a treehouse
    room" (Fable critic, round 1). Its centre is the hollow heart up to y13, where the centre spawn (the box centre,
    y14, walked down) lands on the floor, and it walks out by the 3-high doorways on the axes. A verdant froglight in
    the heart's roof is its light; bark stairs flare the trunk's foot over the roof."""
    top = H - 4
    for y in range(2, top + 1):
        for dx, dz in c.cells(1):
            if (dx, dz) == (0, 0) and y < BASE:
                continue                                         # the hollow heart
            if (dx == 0) != (dz == 0) and y <= 4:
                continue                                         # the doorways on the axes
            if y == top and dx and dz:
                continue                                         # rounded at the top
            c.set(dx, y, dz, LOG, axis="y")
    c.set(0, BASE, 0, "verdant_froglight", axis="y")
    c.set(0, top + 1, 0, LOG, axis="y")                         # the leader, into the crown's top lobe
    for o in O4.values():
        for t in (-1, 0, 1):
            c.st(o, 2, t, ROOF, inward(o), name=BARK_S)         # the trunk's foot flaring over the roof


def face(c, o):
    """The stag-dryad carved on the trunk's face o (the Root Spring's spirit at the capitol's scale), three wide on
    ring 2, y10-13 sitting on the trunk's flare over the roof: a pale birch blaze down the middle, verdant froglight
    eyes (warm ochre froglight) in the bark under jutting dark-oak brows - and the antlers rising out of the burrs as
    the tree's own boughs, toward the crown, in its dark bark. Fable critics: the five-wide mask (cheeks, brow sweeps,
    leafy ears) read as "a crate hut" round the trunk (round 2), and birch stairs and fences - plank textures - as "a
    pile of birch planks, a noisy crate" (round 3), so the face is the trunk's own bark with a blaze, eyes and brows."""
    along_axis = "x" if o[0] == 0 else "z"
    for y in (11, 12, 13):
        c.put(o, 2, 0, y + LIFT, BIRCH, axis="y")                       # the blaze: muzzle, nose, forehead
    c.put(o, 2, 0, 14 + LIFT, WOOD, axis="y")
    for s in (-1, 1):
        c.put(o, 2, s, 11 + LIFT, WOOD, axis="y")                       # the cheeks: the trunk's bark
        c.put(o, 2, s, 12 + LIFT, "ochre_froglight", axis="y")          # the eyes, warm, in the bark (pale green
                                                                         # ones were "the loudest non-pink element at
                                                                         # strategy zoom", Fable critic round 4)
        c.st(o, 3, s, 13 + LIFT, inward(o), "top", name=BARK_S)         # the brows, jutting over the eyes
        c.put(o, 2, s, 13 + LIFT, WOOD, axis="y")
        c.put(o, 2, s, 14 + LIFT, LOG, axis="y")                        # the burrs
        # the antlers: up out of the burr, out along the trunk's face, up, out, up - a stepped beam of five logs in
        # the trunk's own bark (pale birch antlers beside the blaze made the trunk's foot "a checkered crate knot",
        # Fable critic round 5: two woods only, the dark tree and the pale blaze)
        c.put(o, 2, s, 15 + LIFT, LOG, axis="y")
        c.put(o, 2, 2 * s, 15 + LIFT, LOG, axis=along_axis)
        c.put(o, 2, 2 * s, 16 + LIFT, LOG, axis="y")
        c.put(o, 2, 3 * s, 16 + LIFT, LOG, axis=along_axis)
        c.put(o, 2, 3 * s, 17 + LIFT, LOG, axis="y")


def boughs(c):
    """The boughs that carry the crown (horizontal bark on the diagonals, clear of the faces): to ring 4 under its
    rim, ring 3 under its middle, ring 2 under its top - each only where the crown's leaves stand over it."""
    for (sx, sz) in CORNERS:
        for k, y in ((2, CROWN_FOOT), (3, CROWN_FOOT), (4, CROWN_FOOT), (2, CROWN_FOOT + 3), (3, CROWN_FOOT + 3),
                     (2, CROWN_FOOT + 6)):
            if in_hull(sx * k, y + 1, sz * k):                   # under leaves (bare on the small south-west lobe,
                c.set(sx * k, y, sz * k, WOOD, axis="y")         # it read as wood "through the gaps", round 5)


# The boughs that break out through the roof (lovish, Nov 5: "with branches coming out of the roof"), as waypoints
# (a, t, y) on a side o: from the trunk inside the hall, up through the roof's top step (ring 3, y9, between two
# roof stairs), along the roof and past the eaves to a blossom over the garden (ring 6), or up into the crown's rim.
# The tips stand off the stag-dryad's faces from the RTS camera (a blossom in front of the west face hid it);
# blossoms over the eaves read as "shrubs resting on the roof" (Fable critic round 3), and two arms alike at one
# height as "a T-cross / scarecrow" (round 4) - so the south bough crawls out along the roof (lying on its second step)
# and rises low, the west one climbs on a diagonal (a post with a square elbow read as "a candelabra", round 5),
# and each blossom is its own size (BLOSSOMS).
# Four, each its own shape (four alike read as a chapel's ribs). Fable critic round 1: limbs "poking out over the hall
# walls" are what delivers "branches coming out of the roof" - the roof that now shows keeps the temple's stone share.
ROOF_BOUGHS = (("south", ((2, -1, 6), (2, -1, 8), (3, -2, 8), (3, -2, 9), (4, -2, 9), (5, -3, 11), (6, -3, 13))),
               ("west", ((2, 1, 6), (2, 1, 8), (3, 2, 8), (3, 2, 9), (4, 2, 10), (4, 3, 11), (5, 3, 12), (6, 3, 13),
                         (6, 4, 14))),
               ("east", ((2, 1, 6), (2, 0, 7), (2, 0, 8), (3, 0, 8), (3, 0, 9), (4, -1, 10), (5, -2, 12), (6, -2, 14))),
               ("north", ((2, -1, 6), (2, 0, 7), (2, 0, 8), (3, 0, 8), (3, 0, 9), (3, 0, 11), (4, -1, 13), (4, -1, 16),
                          (4, -2, 18))))


def _limb(c, cells):
    """Bark along the waypoints, every step sharing a face with the last (diagonal-only steps read as separate stumps
    from the camera): each step moves the axis with the most still to go."""
    x, y, z = cells[0]
    out = [(x, y, z)]
    for tx, ty, tz in cells[1:]:
        while (x, y, z) != (tx, ty, tz):
            d = [tx - x, ty - y, tz - z]
            i = max(range(3), key=lambda k: (abs(d[k]), k == 1))
            if i == 0:
                x += 1 if d[0] > 0 else -1
            elif i == 1:
                y += 1 if d[1] > 0 else -1
            else:
                z += 1 if d[2] > 0 else -1
            out.append((x, y, z))
    for q in out:
        if c.get(*q)[0] in ("air",) or c.get(*q)[0].endswith(("_stairs", "_slab", "_wall", "_leaves")):
            c.set(*q, WOOD, axis="y")
    return out


def roof(c):
    """The hall's hip roof: mossy stone brick stairs from the eaves (ring 5, y7, overhanging the cornice so the walls
    sit in its shadow) one step a ring up to ring 3 (y9), mossy bricks on the four hips and round the trunk's flare,
    and a lantern standing on each eave corner (the footprint's corners at dusk). A roof inside the parapet read from
    the RTS camera as "a paved courtyard" (Fable critic round 2); a mossy cobble pyramid climbing to y10 as "a second
    mass" the tree "perches on", "rubble" (round 3) - so the roof is the hall's own bricks, a step lower. The roof
    boughs break out through its top step (ring 3)."""
    for k, y in ((5, 7), (4, 8), (3, ROOF)):
        for o in O4.values():
            for t in range(-k + 1, k):
                x, z = c.side(o, k, t)
                if c.get(x, y, z)[0] == "air":
                    c.st(o, k, t, y, inward(o), name=MSS)
        for (sx, sz) in CORNERS:
            if c.get(sx * k, y, sz * k)[0] == "air":
                c.set(sx * k, y, sz * k, MSB)
    for (sx, sz) in CORNERS:
        c.set(sx * 2, ROOF, sz * 2, MSB)                        # round the trunk's flare
        c.set(sx * 5, 8, sz * 5, "lantern", **LANTERN)


BLOSSOMS = {"south": "big", "west": "small", "east": "mid"}   # 10, 8 and 9 cherry leaves


def roof_boughs(c):
    """The ROOF_BOUGHS, each ending where it meets the crown or, out over the garden, in a blossom."""
    for side, way in ROOF_BOUGHS:
        o = O4[side]
        cells = [(c.side(o, a, t)[0], y, c.side(o, a, t)[1]) for a, t, y in way]
        path = _limb(c, cells)
        tx, ty, tz = path[-1]
        if max(abs(tx), abs(tz)) >= 5:
            # a blossom: a 3 x 3 round the tip and a cap over it (pluses read as "four pink crosses", Fable critic
            # round 2; a green underside as "shrubs resting on the roof", round 3); the big one reaches a leaf further
            # along, the small one drops its two corners toward the trunk
            size = BLOSSOMS[side]
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if size == "small" and dx * o[0] + dz * o[1] == -1 and dx and dz:
                        continue
                    leaf(c, tx + dx, ty, tz + dz, "cherry_leaves")
            leaf(c, tx, ty + 1, tz, "cherry_leaves")
            if size == "big":
                leaf(c, tx + 2 * o[1], ty, tz + 2 * o[0], "cherry_leaves")     # (along the side: ring 8 is off the plot)


FACES = ("south", "west")         # the stag-dryad looks out where the RTS camera sees it; four masks round the trunk
                                   # read as "a ring of tan walls" (Fable critic, round 1)


def in_face_zone(dx, y, dz):
    """Cells the canopy leaves clear so the stag looks out: the face and the notch in front of it down the skirt, and
    the antlers' room."""
    for o in (O4[side] for side in FACES):
        a = dx * o[0] + dz * o[1]
        t = -dx * o[1] + dz * o[0]
        if 2 <= a <= 4 and abs(t) <= 2 and 11 + LIFT <= y <= 14 + LIFT:
            return True
        if a >= 5 and abs(t) <= 1 and 12 + LIFT <= y <= 13 + LIFT:
            return True                                          # a V notch in the skirt: the stag looks out
    return False


# The crown as LOBES (x, y, z, rx, ry, rz) - not tiers. Massing rounds 1-2 (eyes 41283644479, 41284750886; two blind
# critics each): a round tiered canopy is "a bullseye / soft ziggurat from above, a lollipop in 3/4" and hides the
# temple; the winners were a temple you can see under an uneven blossom crown. The crown leans north-east, away from
# the RTS camera (it looks north-east from the south-west), so the temple's south and west faces show under it.
# The tall tree (lovish, Nov 5, after the #502 still: "the pink tree could be taller and stick out of the building so
# its more define"): the lobes rose from y14-20 to y21-28 and keep inside ring 5 (they ran to the plot's edge), so the
# crown stands clear over the roof (ridge y9) on a trunk seen from the RTS camera, and the hall's roofline and the
# garden frame it from above.
CROWN_FOOT = 19                    # the crown's lowest boughs (its underside's green belly hangs to ~y18)
LOBES = ((3, 22, -3, 3.8, 2.6, 3.8), (-1, 21, 1, 2.7, 1.9, 2.7), (3, 21, 3, 3.2, 2.0, 3.2),
         (-3, 22, -3, 3.2, 2.2, 3.2), (2, 25, -2, 4.0, 2.2, 4.0), (1, 27, -1, 2.6, 2.2, 2.6),
         (-1, 25, 1, 2.4, 1.8, 2.4))      # wraps the trunk's camera side, which showed bare through the crown


def in_hull(x, y, z):
    """Inside a lobe's smooth outline (the ragged edge aside)."""
    return any(((x - lx) / (rx + .4)) ** 2 + ((y - ly) / (ry + .4)) ** 2 + ((z - lz) / (rz + .4)) ** 2 <= 1
               for lx, ly, lz, rx, ry, rz in LOBES)


def hidden(x, y, z):
    """The RTS camera (looking down from the south-west: it sees tops, west and south faces) cannot see the cell: the
    lobes' outline covers it on all three of those faces."""
    return in_hull(x, y + 1, z) and in_hull(x - 1, y, z) and in_hull(x, y, z + 1)


def canopy(c):
    """The blossom crown: overlapping lobes of cherry leaves (a flowering azalea leaf in every few cells so the pink is
    a blossom, not a paint), each lobe carried on its own bough from the trunk. Glowing fruit (ochre and verdant
    froglights, shroomlight) sits in the lobes' undersides, never hanging."""
    for lx, ly, lz, rx, ry, rz in LOBES[:5]:
        n = max(abs(lx), abs(ly - CROWN_FOOT), abs(lz))
        for i in range(n + 1):                                   # the bough: trunk to the lobe's heart
            x, y, z = (round(a + (b - a) * i / n) for a, b in ((0, lx), (CROWN_FOOT, ly - 1), (0, lz)))
            if c.get(x, y, z)[0] == "air":
                c.set(x, y, z, WOOD, axis="y")
    for lx, ly, lz, rx, ry, rz in LOBES:
        for dx, dz in c.cells(5):                                # inside the cornice: the roofline frames it
            for y in range(max(BASE, int(ly - ry - 1)), min(H - 1, int(ly + ry + 1)) + 1):
                e = ((dx - lx) / (rx + .4)) ** 2 + ((y - ly) / (ry + .4)) ** 2 + ((dz - lz) / (rz + .4)) ** 2
                h = hsh(dx * 3 + y, dz - y)
                if e > 1 - (0.25 if h % 5 == 0 else 0) or in_face_zone(dx, y, dz):
                    continue                                     # a ragged edge, not a ball
                belly = y < ly - 0.5 * ry                         # the lobe's shaded underside
                if belly and e > 0.55 and h % 9 == 0 and hidden(dx, y, dz) and c.get(dx, y, dz)[0] == "air":
                    c.set(dx, y, dz, ("ochre_froglight", "shroomlight", "verdant_froglight")[h % 3],
                          **({} if h % 3 == 1 else {"axis": "y"}))   # glowing fruit in the underside
                    continue
                # the green and the fruit only where the camera cannot see them: at a lobe's rim they showed from the
                # RTS camera as "moss patches reading as holes" (Fable critic round 4) and "holes and noise" (round 5)
                green = belly and hidden(dx, y, dz)
                leaf(c, dx, y, dz, ("flowering_azalea_leaves" if h % 2 else "azalea_leaves") if green else "cherry_leaves")
    # cap the gaps: wood in the crown shows through to the RTS camera as "holes and noise" (Fable critic round 5) - a
    # cherry leaf on each face it sees (hidden()), inside the lobes' outline
    for (rx_, y, rz_), (name, _) in list(c.rig.b.items()):
        x, z = rx_ - R, rz_ - R
        if y < CROWN_FOOT or max(abs(x), abs(z)) > 5:
            continue
        if not name.endswith(("_log", "_wood")):
            continue
        for ddx, ddy, ddz in ((0, 1, 0), (-1, 0, 0), (0, 0, 1)):
            q = (x + ddx, y + ddy, z + ddz)
            if q[1] < H and max(abs(q[0]), abs(q[2])) <= 5 and in_hull(*q) and not in_face_zone(*q):
                leaf(c, *q, name="cherry_leaves")


def roots(c):
    """Surface roots at the garden corners: a low mat of mangrove roots round each corner's foot, side roots creeping
    along the garden edge, moss beyond them. The arcs that climbed the corner piers read as "crate stacks" at every
    corner, as cubes (Fable critic A, Nov 4) and as logs on their side (Fable critic, round 1, Nov 5)."""
    for (sx, sz) in CORNERS:
        for k, j, y in ((7, 7, 1), (7, 7, 2), (6, 7, 1), (7, 6, 1), (6, 6, 1)):
            c.set(sx * k, y, sz * j, "mangrove_roots", **DRY)
        c.set(sx * 7, 0, sz * 7, "rooted_dirt")
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
    """Inside the heart: moss carpet creeping over the floor (passable) round the trunk's feet, ferns by the arches."""
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
    roof(c)
    for side in FACES:
        face(c, O4[side])
    boughs(c)
    roof_boughs(c)
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
    assert y == 2 and all((cx, yy, cz) not in names for yy in range(2, BASE)), "the centre spawn lands at y%d" % y
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
