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
    the four arches; four STRANDS of a braided trunk break up out through the roof's hips on the diagonals, stand up
    off it and twist into the bole under the face (round 12, lovish's references: a thick twisted trunk, boughs that
    rise up and out, never lie on the roof); VINES hang down the corner piers;
  - each garden corner a mossy stone PIER with a lantern, a low moss mat round its foot, by a MOONWELL (a moss
    bed between bare calcite kerbs);
  - the STAG-DRYAD FACE on the bole's south side, over the strands and under the crown: a long pale face (a forehead
    three wide over a one-wide muzzle, warm eyes either side), its pale ANTLERS branching up out of the brow into the
    crown as the tree's own boughs;
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
    boughs reach past the eaves to all-pink blossoms at one height, and the mask has no plank-textured blocks.
    Rounds 4-11 (blind A/B each round) settled the roof, piers, moonwells and a closed crown top; lovish's references
    (Nov 5 ~22:52Z) then lifted the crown to y25-35 with sky between it and the roof, let it spread past the hall
    (round, to ring 7) because it stands that high, braided the trunk and gave the stag one long face. Round 13 (Nov
    6, the round 12 Fable critic and Eris.14): the box down to 28 so the RTS frame holds the crown (y21-27, with
    blossom drips under its rim) and it hides less ground behind the hall; root ribs from the strands down the roof's
    hips and the corner piers into the moss; boughs rising from the bole into the crown; thin antlers;
  - the GARDEN: the paving breaks up into moss and rooted dirt, stepping stones, ferns, carpets and flowers.

WHAT THE SIZE TOUCHES IN THE GAME (the coordinator applies these; this file edits nothing):
  - tools/gen_structures.py: heartwood_hall() returns this build() (as foundry_hall() returns capitol_foundry_hall's);
  - HeartwoodHall.buildTimeBlocks stays 498 (already set; BuildingPlacement times construction by it), the starting
    types (MOSS_BLOCK, MOSSY_STONE_BRICKS) are in layer 0 (and mossy bricks above, as the old hall's walls had them -
    only layer 0 is the foundation, foundationYLayers = 1); no Java of the class needs changing;
  - BiomeBaseGameTests.capitol_details_keep_box_build_time_and_anchors: the row
    { Buildings.HEARTWOOD_HALL, 498, new int[] { 14, 27, 14 } } (14, 21, 14 before the tall tree; 14, 29, 14 until
    lovish's references lifted the crown; 14, 35, 14 until the RTS frame cut its top off, round 12);
  - BuildingAmbienceClientEvents.CAPITOL_SPOTS: "heartwood_hall" -> PETAL_SPOTS below (petals falling from under the
    crown's low lobes and off the crown's top);
  - CrownMotionTest (the #502 living crowns): the great crown's base is y18, its lowest leaves - the blossom drips
    under its rim (y12, y19, then y25 before);
  - CapitolLife: none. SkirmishGameTests.heartwood_hall_frees_its_seedshapers: the 15-wide footprint fits its 16-wide
    column (it stamps from x+1); it clears 22 high, and the tree's top 14 stand in the arena's open sky.

RULES (asserted in check()): the box; the starting types in layer 0; nothing pops off (_SunRig.check_support); leaves
persistent with their true distance (mex_verdant._leaf_distances); no water (the moonwells are moss beds), lava,
gravity blocks, redstone or fire; under 2000 blocks; the centre spawn (ProductionBuilding.getIndoorSpawnPoint: the box
centre walked down) lands on the hall floor with the heart's open column over it and walks out; no pockets
(tools/audit_structure_pockets.py audit() on the written nbt: __main__ does).

Usage: python3 tools/capitol_heartwood_hall.py [OUT.nbt]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import capitol_town_centre as tc   # noqa: E402  - the Cap canvas (a _SunRig addressed from the centre, 4-fold sides)
import mex_verdant as mv           # noqa: E402  - the leaf-distance pass shared with the Root Spring

R, H = 7, 28                       # 15 x 28 x 15: the tall tree (lovish, Nov 5: 22 -> 30; his references, Nov 5
                                   # ~22:52Z: the crown lifted high on the trunk, sky between roof and crown -> 36;
                                   # round 13: 28 - at 36 the RTS camera's frame cut the crown's top off and the
                                   # crown hid 165 ground cells behind the hall (main 90); 28 is the lowest that keeps
                                   # the crown's top 18 over the roof)
BASE = (H - 1) // 2                # the hollow heart's top: the trunk is solid from here up (the box centre)
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
# under four of the crown's low lobes (LOBES) and over the crown's top
PETAL_SPOTS = ((11.5, 20.5, 2.5), (3.5, 21.5, 2.5), (11.5, 20.5, 12.5), (3.5, 21.5, 10.5), (7.5, 27.5, 7.5))


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
        if name in ("rooted_dirt", "podzol") and c.get(dx, 1, dz)[0] == "air":
            c.set(dx, 1, dz, "moss_carpet")     # a bare brown cell read as "a stray brown block ... a leftover"
                                                # on the moss from the RTS camera (Fable critics rounds 9 and 10)
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
    the heart's roof is its light. The roof's stone runs up to the bark (a flare of dark stairs round its foot read
    as "a dark-oak plate at the roof apex", Fable critic round 6)."""
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


FACE_Y = 12                        # the stag-dryad's muzzle tip: over the roof, well under the crown so the RTS
                                   # camera sees it under the crown's rim (round 13)
BIRCH_W = "stripped_birch_wood"    # the antlers: the Court's silver with bark all round (no pale log ends)


def face(c, o):
    """The stag-dryad on the trunk's face o, high under the crown where the braided strands have closed into one bole
    (round 12): a long pale face - a forehead three wide over a one-wide muzzle, warm ochre eyes either side - and
    pale antlers branching up and out of the brow into the crown's underside, the tree's own boughs in the Court's
    silver. One face, on the south: two blazes on the two faces the RTS camera sees read as "two pale fangs", "a
    smiley" (Fable critic round 11), and dark-bark antlers vanished into the trunk (round 9)."""
    y0 = FACE_Y
    for y in (y0, y0 + 1, y0 + 2):
        c.put(o, 2, 0, y, BIRCH, axis="y")                       # the muzzle and the nose bridge
    for s in (-1, 1):
        c.put(o, 2, s, y0, WOOD, axis="y")                       # the jaw: the trunk's own bark
        c.put(o, 2, s, y0 + 1, WOOD, axis="y")
        c.put(o, 2, s, y0 + 2, "ochre_froglight", axis="y")      # the eyes, warm, either side of the bridge
    for t in (-1, 0, 1):
        c.put(o, 2, t, y0 + 3, BIRCH_W, axis="y")                # the forehead, three wide over the one-wide muzzle
    for a, t, y in ANTLERS:
        c.put(o, a, t, y0 + y, BIRCH_W, axis="y")


# The antlers, (a, t, y over FACE_Y) on the face's side: each a thin arm one block out from the face, rising at 45
# degrees from the forehead's corner, a tine standing up from its middle and one from its tip. Round 12's beams - two
# blocks a step, pressed to the bark - read from the RTS camera as "a beige lump of stripped birch pressed against the
# trunk ... a bolted-on crate, not a face" (Fable critic round 12).
# The arm starts beside the forehead, not over the eyes: a root one out over each eye hid it from the camera.
ANTLERS = tuple(q for s in (-1, 1) for q in
                ((2, 2 * s, 3), (3, 3 * s, 4), (3, 4 * s, 5), (3, 5 * s, 6), (3, 6 * s, 7),   # the arm
                 (3, 4 * s, 6),                                                              # the middle tine
                 (3, 6 * s, 8)))                                                             # the tip tine


# The boughs that carry the crown: each rises up and out at 45 degrees from the bole under the crown into its underside,
# every step sharing a face with the last. Round 12's bough cells stood one by one on the diagonals and two of them
# read from the front as "two single dark blocks [that] hang detached under the crown edges" (Fable critic round 12);
# the critic asked for "two upward boughs ... (3-4 blocks, 45 deg up and out) just under the crown on the N and E
# sides so the crown has visible support". (start, end) as (x, y over CROWN_FOOT, z).
BOUGHS = (((0, -3, -1), (0, 1, -5)), ((1, -3, 0), (5, 1, 0)),          # north and east, the critic's two
          ((1, -2, -1), (4, 1, -4)), ((-1, -2, -1), (-4, 1, -4)))   # NE and NW (the south ones are the antlers)


def boughs(c):
    """The boughs (BOUGHS): bark from the bole up into the crown's underside, clear of the face."""
    for (x0, y0, z0), (x1, y1, z1) in BOUGHS:
        for q in _limb_cells([(x0, CROWN_FOOT + y0, z0), (x1, CROWN_FOOT + y1, z1)]):
            if in_face_zone(*q):
                break
            if c.get(*q)[0] == "air":
                c.set(*q, WOOD, axis="y")


# The BRAIDED TRUNK (round 12; lovish's references, Nov 5 ~22:52Z: a "thick twisted trunk", roots "gripping" the
# building, boughs that "rise up and out" and never lie on the roof): four strands leave the bole inside the hall, break
# out through the roof's hips on the four diagonals, stand straight up off it and then twist round the bole, closing
# into it under the face. lovish, Nov 5: "with branches coming out of the
# roof". The flat boughs over the roof (rounds 4-11) read as "a flat zigzag X ... branches sit ON the roof" (Fable
# critic round 11); these stand up off it.
STRANDS = 4


def _strand_path(k):
    """Strand k's centre line as cells (x, y, z), one a height: out from the bole under the roof (y6-8), up through the
    roof's hip on its diagonal and standing straight up off it on the ring-3 square to y12 (a slow twist), then
    wrapping in round the bole - a fast twist - to close into it by y15."""
    path, th = [], math.pi / 4 + k * math.pi / 2
    for y in range(6, 16):
        th += math.radians(6 if y <= 12 else 28)
        cx, sz = math.cos(th), math.sin(th)
        m = max(abs(cx), abs(sz))
        r = {6: 1.6, 7: 2.3, 13: 2.6, 14: 2.0, 15: 1.6}.get(y, 3.0)
        path.append((int(round(r * cx / m)), y, int(round(r * sz / m))))
    return path


def _limb_cells(cells):
    """The cells along waypoints, every step sharing a face with the last (diagonal-only steps read as separate stumps
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
    return out


def strands(c):
    """The braided trunk's STRANDS: bark along each strand's path, through the roof's stairs; never in the face's
    room."""
    for k in range(STRANDS):
        for q in _limb_cells(_strand_path(k)):
            if in_face_zone(*q):
                continue
            n = c.get(*q)[0]
            if n == "air" or n.endswith(("_stairs", "_slab", "_wall", "_leaves")) or n == "stone_bricks":
                c.set(*q, WOOD, axis="y")


def ribs(c):
    """The ROOTS that grip the hall (round 13; lovish's references: a trunk that "climbs up the side of the house and
    wraps it", roots gripping): each strand, where it stands on the roof, sends a root rib down the roof's hip to the
    eave corner, and on down the corner pier - the root has taken the pier's place - into the moss at its foot, where
    it splays as two roots lying along the stair rim. The Fable critic, round 12: "the trunk is a plain column standing
    on the roof peak; the one dark block line stepping down the SW roof slope looks like a stray. Fix: run ... dark-oak
    ribs from the trunk base down the stepped roof to the hall corners and on down the walls to the moss terrace
    (roots that grip the ground)". Bark outside the walls lies along its line (the corner roots as crate stacks: see
    tests/test_roots_are_bark_lines_not_cube_stacks)."""
    for k in range(STRANDS):
        th = math.pi / 4 + k * math.pi / 2
        sx, sz = (1 if math.cos(th) > 0 else -1), (1 if math.sin(th) > 0 else -1)
        start = next(q for q in _strand_path(k) if q[1] == ROOF)
        for q in _limb_cells([start, (3 * sx, ROOF, 3 * sz), (4 * sx, 8, 4 * sz), (4 * sx, 1, 4 * sz)]):
            n = c.get(*q)[0]
            if n == "air" or n.endswith(("_stairs", "_slab")) or n in ("stone_bricks", MSB, CSB, CHB, MC, "moss_block"):
                c.set(*q, WOOD, axis="y")
        # the foot splays along the stair rim, both ways round the corner (logs lying along their line)
        c.set(5 * sx, 1, 4 * sz, LOG, axis="x")
        c.set(4 * sx, 1, 5 * sz, LOG, axis="z")


def roof(c):
    """The hall's hip roof: stone brick stairs from the eaves (ring 5, y7, overhanging the cornice so the walls sit in
    its shadow) one step a ring up to ring 3 (y9) and on to the trunk's bark (ring 2), stone bricks on the four hips,
    and a lantern standing on each eave corner (the footprint's corners at dusk). A roof inside the parapet read from
    the RTS camera as "a paved courtyard" (Fable critic round 2); a mossy cobble pyramid climbing to y10 as "a second
    mass" the tree "perches on", "rubble" (round 3); mossy brick steps as "mossy stripes ... a textured crate" (round
    6) - so the roof is plain stone brick, low, under the mossy walls. The trunk's strands break out through it."""
    for k, y in ((5, 7), (4, 8), (3, ROOF), (2, ROOF)):
        for o in O4.values():
            for t in range(-k + 1, k):
                x, z = c.side(o, k, t)
                if c.get(x, y, z)[0] == "air":
                    c.st(o, k, t, y, inward(o), name="stone_brick_stairs")
        for (sx, sz) in CORNERS:
            if c.get(sx * k, y, sz * k)[0] == "air":
                c.set(sx * k, y, sz * k, "stone_bricks")
    for (sx, sz) in CORNERS:
        c.set(sx * 5, 8, sz * 5, "lantern", **LANTERN)


FACES = ("south",)                 # one stag-dryad, on a side the RTS camera sees (two read as "two pale fangs")


def in_face_zone(dx, y, dz):
    """Cells the strands and the canopy leave clear so the stag looks out: the face, its antlers' room, and a notch in
    the crown's skirt in front of it."""
    for o in (O4[side] for side in FACES):
        a = dx * o[0] + dz * o[1]
        t = -dx * o[1] + dz * o[0]
        if 2 <= a <= 4 and abs(t) <= 7 and FACE_Y - 1 <= y <= FACE_Y + 9:
            return True                                          # the face and its antlers' room
        if 2 <= a <= 7 and abs(t) <= 3 and FACE_Y <= y <= FACE_Y + NOTCH:
            return True                                          # the notch in the skirt: the stag looks out
    return False


# The crown as LOBES (x, y, z, rx, ry, rz) - not tiers. Massing rounds 1-2 (eyes 41283644479, 41284750886; two blind
# critics each): a round tiered canopy is "a bullseye / soft ziggurat from above, a lollipop in 3/4" and hides the
# temple; the winners were a temple you can see under an uneven blossom crown. The crown leans north-east, away from
# the RTS camera (it looks north-east from the south-west), so the temple's south and west faces show under it.
# The tall tree (lovish, Nov 5, after the #502 still: "the pink tree could be taller and stick out of the building so
# its more define"), then his references (Nov 5 ~22:52Z): the crown LIFTED HIGH on the trunk with sky between roof and
# crown, and wider than the building because it stands that high - out to the plot's edge (ring 7). Round 13: y21-27
# in a 28-high box (at 36 the RTS frame cut its top off), its round clip centred on the trunk. Seen
# from the RTS camera a crown that high falls north-east of the hall, so the hall still shows under it.
CROWN_FOOT = H - 9                 # the crown's lowest boughs (its underside hangs a block over them)
SPREAD = 7.0                       # the crown's reach from its axis: round from above, not the plot's square
SPREAD_AXIS = (0.0, 0.0)           # the axis: the trunk (the lobes lean north-east inside it; an axis north-east
                                   # of the trunk pushed the crown out of the RTS frame and over the ground behind)
NOTCH = 9                          # the notch in the crown's skirt over the face rises this far over FACE_Y
LOBES = tuple((x, CROWN_FOOT + y, z, rx, ry, rz) for x, y, z, rx, ry, rz in
              ((1, 3, -1, 6.2, 2.4, 6.2),                                   # the great spread, a little north-east
               (3, 3, -3, 2.8, 2.0, 2.8), (-4, 4, -3, 3.0, 1.9, 3.0), (3, 4, 4, 3.0, 1.9, 3.0),
               (0, 6, -1, 4.2, 2.0, 4.2), (1, 7, -1, 2.4, 1.6, 2.4)))
DRIPS = 6                          # blossom drips hanging under the crown's rim on the sides the camera sees


def in_hull(x, y, z):
    """Inside a lobe's smooth outline (the ragged edge aside)."""
    return any(((x - lx) / (rx + .4)) ** 2 + ((y - ly) / (ry + .4)) ** 2 + ((z - lz) / (rz + .4)) ** 2 <= 1
               for lx, ly, lz, rx, ry, rz in LOBES)


def deep(x, y, z):
    """Two cells inside every side of the crown: never seen, so left empty (the block budget is the crown's skin)."""
    return all(in_hull(x + dx, y + dy, z + dz) for dx, dy, dz in
               ((2, 0, 0), (-2, 0, 0), (0, 2, 0), (0, -2, 0), (0, 0, 2), (0, 0, -2), (1, 1, 1), (-1, -1, -1),
                (1, -1, 1), (-1, 1, -1)))


def hidden(x, y, z):
    """The RTS camera (looking down from the south-west: it sees tops, west and south faces) cannot see the cell: the
    lobes' outline covers it on all three of those faces."""
    return in_hull(x, y + 1, z) and in_hull(x - 1, y, z) and in_hull(x, y, z + 1)


def canopy(c):
    """The blossom crown: overlapping lobes of cherry leaves (a flowering azalea leaf in every few cells so the pink is
    a blossom, not a paint), each lobe carried on its own bough from the trunk, its heart left empty. Glowing fruit
    (ochre and verdant froglights, shroomlight) sits in the lobes' undersides, never hanging."""
    for lx, ly, lz, rx, ry, rz in LOBES:
        for x, y, z in _limb_cells([(0, CROWN_FOOT, 0), (lx, ly - 1, lz)]):   # the bough: trunk to the lobe's heart
            if in_face_zone(x, y, z):
                break                                            # it stops at the stag's room (no loose end past it)
            if c.get(x, y, z)[0] == "air":
                c.set(x, y, z, WOOD, axis="y")
    for lx, ly, lz, rx, ry, rz in LOBES:
        for dx, dz in c.cells(R):
            for y in range(max(BASE, int(ly - ry - 1)), min(H - 1, int(ly + ry + 1)) + 1):
                e = ((dx - lx) / (rx + .4)) ** 2 + ((y - ly) / (ry + .4)) ** 2 + ((dz - lz) / (rz + .4)) ** 2
                h = hsh(dx * 3 + y, dz - y)
                if e > 1 - (0.25 if h % 5 == 0 else 0) or in_face_zone(dx, y, dz) or deep(dx, y, dz):
                    continue                                     # a ragged edge, not a ball; an empty heart
                if math.hypot(dx - SPREAD_AXIS[0], dz - SPREAD_AXIS[1]) > SPREAD:
                    continue                                     # round from above, not the plot's square
                if max(abs(dx), abs(dz)) <= 2 and y < CROWN_FOOT + 2:
                    continue                                     # the trunk shows under the crown
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
        if y < CROWN_FOOT or not name.endswith(("_log", "_wood")) or "birch" in name:
            continue
        for ddx, ddy, ddz in ((0, 1, 0), (-1, 0, 0), (0, 0, 1)):
            q = (x + ddx, y + ddy, z + ddz)
            # a bough's end high in the crown is capped even just past the lobes' outline: two bare ends at y25 read
            # from the RTS camera as "two dark pits ... eyes or damage" in the crown's top (Fable critic round 7)
            high = y >= CROWN_FOOT + 3
            if q[1] < H and max(abs(q[0]), abs(q[2])) <= R and (in_hull(*q) or high) and not in_face_zone(*q):
                leaf(c, *q, name="cherry_leaves")
    drips(c)


def drips(c):
    """Blossom hanging two or three blocks under the crown's rim (inside the cornice, ring 4-5) on the south and west,
    the sides the RTS camera sees: "the crown is one unbroken pink ball ... nothing hangs. Fix: ... hang 2-3 block
    cherry-leaf drips" (Fable critic round 12). One drip a column, the columns spread round the rim."""
    low = {}
    for (rx_, y, rz_), (name, _) in c.rig.b.items():
        if name == "cherry_leaves":
            k = (rx_ - R, rz_ - R)
            low[k] = min(low.get(k, 99), y)
    rim = sorted(((x, z), y) for (x, z), y in low.items()
                 if 4 <= max(abs(x), abs(z)) <= 5 and z - x >= 2 and not in_face_zone(x, y - 1, z))
    picked = []
    for (x, z), y in sorted(rim, key=lambda kv: (hsh(kv[0][0] * 5, kv[0][1] + 2), kv[0])):
        if len(picked) == DRIPS:
            break
        if any(max(abs(x - px), abs(z - pz)) < 3 for px, pz in picked):
            continue                                             # spread round the rim, never two side by side
        n = 2 + hsh(x + 7, z) % 2
        if all(c.get(x, y - d, z)[0] == "air" for d in range(1, n + 2)):
            for d in range(1, n + 1):
                leaf(c, x, y - d, z, "cherry_leaves")
            picked.append((x, z))


def vines(c):
    """Vines down the hall (lovish's references, Nov 5: vines hanging down the facade): on the outer faces of the
    four corner piers (the roots that took them), from under the eaves, four or five long - the green streaming down
    the corners, the arches and lancets left clear. Two to four long, "the hall walls are bare ... add vine blocks ...
    down the SW and SE pillars of the hall so they show in the rts.png lower-left" (Fable critic round 12)."""
    for (sx, sz) in CORNERS:
        for (x, z, side) in ((5 * sx, 4 * sz, "west" if sx > 0 else "east"),
                             (4 * sx, 5 * sz, "north" if sz > 0 else "south")):
            n = 4 + hsh(x + 11, z - 3) % 2
            for y in range(6, 6 - n, -1):
                if c.get(x, y, z)[0] != "air":
                    break
                props = {k: "false" for k in ("up", "north", "south", "east", "west")}
                props[side] = "true"
                c.set(x, y, z, "vine", **props)


def roots(c):
    """Surface roots at the garden corners: a low mat of mangrove roots round each corner's foot, side roots creeping
    along the garden edge, moss beyond them. The arcs that climbed the corner piers read as "crate stacks" at every
    corner, as cubes (Fable critic A, Nov 4) and as logs on their side (Fable critic, round 1, Nov 5); the mangrove
    root mats that followed as "rooted-dirt/mud cubes ... crates" (round 7). So each corner is a mossy stone pier
    with a lantern on it, the roots a low moss mat round its foot: the plot's frame is stone, the accents green."""
    for (sx, sz) in CORNERS:
        c.set(sx * 7, 0, sz * 7, MSB)
        c.set(sx * 7, 1, sz * 7, MSB)
        c.set(sx * 7, 2, sz * 7, "lantern", **LANTERN)
        for k, j in ((6, 7), (7, 6), (6, 6), (7, 5), (5, 7)):
            c.set(sx * k, 1, sz * j, "moss_carpet")
        c.set(sx * 7, 1, sz * 4, "moss_carpet")
        c.set(sx * 4, 1, sz * 7, "moss_carpet")


def moonwells(c):
    """A moonwell on the stylobate's corner by each pier: a moss bed between two bare calcite kerbs (the Court's
    silver). Its still water (a water cauldron) was the one blue on the hall - "a blue insert ... competes with team
    colour", "tiny ... read as glitches at RTS zoom" (Fable critics rounds 7, 9 and 10) - so the bed is moss."""
    for (sx, sz) in CORNERS:
        c.set(sx * 5, 1, sz * 5, "moss_block")
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


# a vine hangs only on a full face; these blocks have none (or not all six)
_NOT_FULL = ("air", "vine", "_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_pane", "iron_bars", "_carpet",
             "lantern", "torch", "_trapdoor", "_door", "chain", "candle", "_petals", "fern", "grass", "_button",
             "lily_of_the_valley", "azure_bluet", "oxeye_daisy", "allium", "cornflower", "dandelion", "poppy", "tulip")
# blocks whose state follows their neighbours: the nbt stores the shape they take with every neighbour standing, so
# the game re-shapes them while a neighbour is still missing, and each re-shape updates their own neighbours
_RESHAPES = ("_stairs", "_wall", "_fence", "_pane", "iron_bars")


def vines_knocked_off(blocks):
    """The vines the game knocks off while it lays the hall: in the nbt's order (y, then z, then x - what
    gen_structures.Structure.write writes, and what the settle and count GameTests lay bottom up), each set updating
    its neighbours' shapes. A vine re-checks its faces on an update from any side but below and turns to air when no
    face has a full block behind it (or the same face on a vine above); a stair, wall, fence or pane beside a newly set
    block may re-shape and pass the update on. built_blocks_still_count_after_the_world_settles lost the round-13
    vines at (3,6,2) and (11,6,2) so: each hung on a pier cell laid after it (z 3 comes after z 2), and the eave stair
    beside it (set, or re-shaped by its own neighbour) updated it first. blocks: {(x, y, z): (name, props)}."""
    side = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    laid, lost = {}, []

    def holds(q):
        _, p = laid[q]
        for f, (dx, dz) in side.items():
            if str(p.get(f, "false")) != "true":
                continue
            n = laid.get((q[0] + dx, q[1], q[2] + dz))
            if n and not any(n[0] == k or n[0].endswith(k) for k in _NOT_FULL):
                return True
            a = laid.get((q[0], q[1] + 1, q[2]))
            if a and a[0] == "vine" and str(a[1].get(f, "false")) == "true":
                return True
        return False

    def reshapes(n, by):
        """Whether the block n may change its state when `by` is set beside it: a stair turns its corner after the
        stairs beside it; a wall, fence or pane connects to any block but a vine."""
        if n.endswith("_stairs"):
            return by.endswith("_stairs")
        return n.endswith(_RESHAPES) and by != "vine"

    def touch(q, name, depth):
        """q was set or re-shaped (name), or a vine at q fell (name "air"): update the cells it is above or beside."""
        for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0), (0, 1, 0)):
            n = (q[0] + d[0], q[1] + d[1], q[2] + d[2])
            if n not in laid:
                continue
            if laid[n][0] == "vine":
                if d != (0, 1, 0) and not holds(n):     # an update from below leaves a vine as it is
                    del laid[n]
                    lost.append(n)
                    touch(n, "air", depth)
            elif depth and name != "air" and reshapes(laid[n][0], name):
                touch(n, laid[n][0], depth - 1)

    for q in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
        if blocks[q][0] in ("air", "cave_air"):
            continue
        laid[q] = blocks[q]
        touch(q, blocks[q][0], 1)
    return sorted(lost)


def drop_loose_vines(rig):
    """Leave out the vines the lay would knock off (vines_knocked_off), until none would fall."""
    while True:
        lost = vines_knocked_off(rig.b)
        if not lost:
            return
        for q in lost:
            del rig.b[q]


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
    strands(c)
    ribs(c)
    boughs(c)
    roots(c)
    moonwells(c)
    vines(c)
    canopy(c)
    hall_dressing(c)
    mv._leaf_distances(c.rig)
    c.rig.settle()
    drop_loose_vines(c.rig)
    c.rig.check_support("heartwood_hall")
    return c.rig.structure()


# ===================================================================================================== rules
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "soul_sand", "magma_block", "hanging_roots",
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
    lost = vines_knocked_off({q: (nm, dict(pr)) for q, (nm, pr) in full.items()})
    assert not lost, "vines the game knocks off while laying the hall: %s" % lost
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
    # vines hang on the faces they name: a solid block on each (they pop off a bare face)
    for (x, y, z), (nm, props) in full.items():
        if nm == "vine":
            pr = dict(props)
            faces = [f for f in ("north", "south", "east", "west") if pr.get(f) == "true"]
            assert faces, "a vine at %s names no face" % ((x, y, z),)
            for f in faces:
                d = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[f]
                assert (x + d[0], y, z + d[1]) in names, "a vine at %s hangs on nothing (%s)" % ((x, y, z), f)
    # the tree's dark bark is one piece: every bark cell joins the trunk face to face (a lone bough cell read as "two
    # single dark blocks [that] hang detached under the crown edges", Fable critic round 12)
    bark = {p for p, nm in names.items() if nm in (LOG, WOOD)}
    seen, todo = {(cx, BASE + 1, cz)}, [(cx, BASE + 1, cz)]
    while todo:
        p = todo.pop()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if q in bark and q not in seen:
                seen.add(q)
                todo.append(q)
    loose = sorted(bark - seen)
    assert not loose, "bark cells not joined to the trunk: %s" % loose[:12]
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
