#!/usr/bin/env python3
"""The Stormcallers capitol, the Jade Pagoda, redrawn as THE PAGODA OF A THOUSAND STORMS - the centrepiece of the base.

lovish, Oct 19 2026: capitols "can be a lot bigger than the current capital size, a lot taller, a lot more detailed",
"I want it to be the center piece of the base", everything "connects" to it with "fluidity", "don't break the game".
Detailed = fine-grained (stairs, slabs, walls, fences, chains), not chunky blocks. The bar is the Sunforged Temple of
the Sun (tools/capitol_town_centre.py build_grand) and the Gravebound Necropolis Cathedral (tools/capitol_mausoleum.py).

THE STORMCALLERS STYLE KIT (shared with the Jade Thunder Spire extractor, tools/mex_storm.py, the Lotus Academy, the
Celestial Observatory and Heaven's Bell - so a Stormcaller base reads as one place):
  - main blocks: JADE (waxed oxidized cut copper + its stairs / slabs: waxed, so it never weathers mid-match) and DARK
    OAK (logs, planks, stairs, slabs, fences); red lacquer columns (stripped mangrove logs) and red paper screens
    (red stained glass) as the pagoda's own colour;
  - trim: GOLD (column capitals, every eave corner, the crown);
  - light: PAPER LANTERNS (lanterns) - on the stone lanterns of the garden, on the roof bands, in the hall;
  - silhouette motif: CURVED PAGODA ROOFS - a jade stair lip, a flat band, a second stair course climbing to the
    storey above; every corner upturned (a gold block under a jade slab tip);
  - signature detail: HANGING CHIMES - a chain with a glowing end rod striker under every eave corner;
  - accent: AMETHYST, the storm-made crystal (the extractor's): the floating storm-crystal halo round the crown.

THE DESIGN (inspiration only, never traced: ref 78 the gothic castle on a crescent moon - silhouette first, a
celestial crown; ref 70 the crystal windmill with red spires - crystal against red; and the East Asian pagoda as a
rule set: odd tiers, each roof a ring narrower, red posts under deep eaves):
  - FIVE ROOFS stepping in (13, 11, 9, 7, 5 wide) over five storeys, each a ring narrower, so every roof shows its own
    eave and a strip of red lacquer and lit paper wall above it. Each roof is CURVED: the lip sags in the middle (a
    stair) and sweeps up at the ends (a raised block and slab), the gold corner carries an upturned jade tip, and a
    second stair course pitches it again onto the band (a blackstone gap ring at the next storey's foot parts the
    tiers from above, Nov 2026), and a CHIME (chain + end-rod striker) hangs from every corner;
  - the GROUND STOREY: a hall of red-lacquer posts, dark oak and red paper screens with a door on every side (players
    build in every direction) in a verandah of red columns on chiseled bases under gold capitals; the hall is an
    atrium up to a coffered ceiling with a pearlescent light - the spawn cell is its centre and walks out of any door;
  - a STEPPED TERRACE: a stone-brick step all round, a jade step, a dark oak floor with a jade cross to a gold heart;
  - the LIGHTNING CROWN: a jade cap on a gold boss, a lightning rod on it and on each of the top roof's four tips,
    and round them, floating free, the STORM-CRYSTAL HALO - a ring of violet glass with an amethyst shard (block +
    cluster) on each corner: the faction's accent, crystal the storm froze out of the air;
  - the GARDEN (no roads, the same on every side): a TORII on each side, kept low so sky shows between it and the
    first eave; STONE LANTERNS (tooro: a wall post, a lantern, a slab hood) and BLOSSOM TREES (dark oak, cherry
    leaves) either side of each torii; a JADE GUARDIAN (a crouching jade beast with a dragon's head and jade
    forepaws on a chiseled plinth) on each corner looking out; and raked stone, moss, petals, azalea and grass that
    fade into whatever ground the capitol stands on.

WHAT THE SIZE TOUCHES IN THE GAME (the coordinator applies these; this file edits nothing):
  - JadePagoda.buildTimeBlocks: unset (0), so BuildingPlacement would time the build by the block count (494 in the
    old pagoda, ~3x more now). Set `this.buildTimeBlocks = 494;` so the bigger capitol builds in the same time;
  - BiomeBaseGameTests.capitol_details_keep_box_build_time_and_anchors pins it like the others:
    { Buildings.JADE_PAGODA, 494, new int[] { 14, 21, 14 } };
  - BuildingAmbienceClientEvents.CAPITOL_SPOTS: "jade_pagoda" -> SPARK_SPOTS below (Fx.SPARK over the crown rod's tip
    and the top roof's four corner rods; same convention as the others: the block at int(y));
  - tools/gen_structures.py writes jade_pagoda.nbt from this build() (as gen_capitol_details does for the Temple);
  - JadePagoda.startingBlockTypes (POLISHED_ANDESITE, STONE_BRICKS) stay; both sit in layer 0 and nowhere above.

RULES (asserted in check()): the box (15 x 22 x 15); the starting types in layer 0 and nowhere above; nothing pops off
(_SunRig.check_support); no water, lava, gravity blocks (the raked "gravel" is stone - real gravel would fall),
redstone or fire (the lights are lanterns: a campfire burns units); under 2000 blocks; the centre spawn
(ProductionBuilding.getIndoorSpawnPoint: the box centre walked down) lands on the hall floor and walks out - and no
pockets (tools/audit_structure_pockets.py's audit(), run by __main__).

Usage: python3 tools/capitol_jade_pagoda.py [OUT.nbt]
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
JADE, JADE_ST, JADE_SLAB = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"
OAK, OAK_LOG, OAK_ST, OAK_SLAB = "dark_oak_planks", "dark_oak_log", "dark_oak_stairs", "dark_oak_slab"
RED = "stripped_mangrove_log"      # red lacquer posts
PAPER = "white_stained_glass"      # paper screens, lit from inside: pale (Fable critic A, Nov 4 - red glass beside the
                                   # red posts read as one maroon mass and passed no light at dusk)
GOLD = "gold_block"
SB_ST, SB_SLAB, SB_WALL = "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"
SKULL_CORNER = {(-1, 1): "2", (-1, -1): "6", (1, -1): "10", (1, 1): "14"}   # standing-head rotation out of a corner

# where the game looks (structure coordinates; the centre cell is (R, y, R))
# BuildingAmbienceClientEvents.CAPITOL_SPOTS["jade_pagoda"]: Fx.SPARK over the crown rod and four halo crystals
SPARK_SPOTS = ((7.5, 21.9, 7.5), (5.5, 18.5, 5.5), (9.5, 18.5, 5.5), (5.5, 18.5, 9.5), (9.5, 18.5, 9.5))
STARTING_TYPES = {"polished_andesite", "stone_bricks"}   # JadePagoda.startingBlockTypes

# THE PLAN, Nov 4 (lovish: "Pagoda: new plan"; Fable critic C F7 - the pagoda and the Ashborn capitol were the same
# three nested squares in flat black): a long hall under a RECTANGULAR first roof, square storeys and roofs above it.
# Half-widths (x, z) of the rectangles, from the centre cell:
LIP1 = (7, 5)                      # the first roof's lip: 15 x 11, to the box's east and west edges
STEP = (6, 4)                      # the stone-brick step round the terrace
VER = (5, 3)                       # the verandah's colonnade, on the jade step
HALL = (4, 2)                      # the hall's wall (its beam carries the first roof's band)


def rsides(A, B):
    """(o, a, tm) for each side of the A x B rectangle: the side's distance from the centre and its half-length."""
    for o in O4.values():
        yield (o, B, A) if o[0] == 0 else (o, A, B)


def rcells(A, B, inner=None):
    """The cells of the A x B rectangle, without the inner (A', B') one when given."""
    for dx in range(-A, A + 1):
        for dz in range(-B, B + 1):
            if inner is None or abs(dx) > inner[0] or abs(dz) > inner[1]:
                yield dx, dz


def rod(c, dx, y, dz, facing="up"):
    c.set(dx, y, dz, "lightning_rod", facing=facing, powered="false", **DRY)


def chime(c, dx, y, dz, links=1):
    """The kit's signature: a chain (links long) hanging from y, a glowing end-rod striker under it."""
    for i in range(links):
        c.set(dx, y - i, dz, "chain", axis="y", **DRY)
    c.set(dx, y - links, dz, "end_rod", facing="down")


# ======================================================================================================= ground
def garden(c):
    """The ground outside the terrace: no kerb, no road. Round the terrace's step a raked ring (moss, mossy cobblestone
    and mossy stone bricks - real gravel would fall), stone-brick flags at the foot of each stair (the instant
    foundation, layer 0); beyond it moss, grass and mossy stone, so whichever way the base grows the capitol's ground
    runs into it. Nov 4 (Fable critic C F2): the Stormcallers' ground is the GARDEN - the andesite rings were the
    same grey yard four other buildings of three factions stood on."""
    SA, SB = STEP
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            if abs(dx) <= SA and abs(dz) <= SB:
                continue
            h = hsh(dx, dz)
            raked = abs(dx) <= SA + 1 and abs(dz) <= SB + 1
            if raked:
                name = ("moss_block", "mossy_cobblestone", "moss_block", "mossy_stone_bricks", "moss_block",
                        "mossy_cobblestone")[h % 6]
                if min(abs(dx), abs(dz)) <= 1:
                    name = "stone_bricks"                      # the flags at the foot of each stair
            else:
                name = ("moss_block", "grass_block", "mossy_cobblestone", "grass_block", "moss_block", "stone_bricks",
                        "grass_block", "mossy_cobblestone")[h % 8]
            c.set(dx, 0, dz, name, **({"snowy": "false"} if name == "grass_block" else {}))
    # the dressing (y1): moss carpet and azalea bushes on the soft cells of the raked ring north and south, between
    # the stairs and the corners - single cells with ground round them (no pockets); no pink petals (pink is the
    # Verdant crown's and the academy's lotus, critic C F4). East and west the ring lies under the first roof's eave.
    for dx in range(-SA, SA + 1):
        for dz in (-(SB + 1), SB + 1):
            if not 2 <= abs(dx) <= 5:
                continue
            h = hsh(dx, dz)
            if c.get(dx, 0, dz)[0] not in ("grass_block", "moss_block"):
                continue
            if h % 5 == 2:
                c.set(dx, 1, dz, "flowering_azalea")
            elif h % 5 in (0, 3):
                c.set(dx, 1, dz, "moss_carpet")


def torii(c, o):
    """A torii on the garden edge of side o (ring 7): red lacquer posts, a red tie beam with a gold tablet, the black
    top beam with its ends swept up. Kept to y4 so a gap of sky shows between it and the first eave (the gate reads
    on its own) while the tie beam at y3 still leaves a unit two cells of headroom. A gate on every side - the
    capitol has no front."""
    for s in (-1, 1):
        for y in range(1, 4):
            c.put(o, R, 2 * s, y, RED, axis="y")
        c.put(o, R, s, 3, "mangrove_slab", type="top", **DRY)       # the nuki, the tie beam
        c.st(o, R, 3 * s, 4, along(o, s), name=OAK_ST)              # the kasagi's swept-up ends
        c.put(o, R, 2 * s, 4, OAK_SLAB, type="top", **DRY)
        c.put(o, R, s, 4, OAK_SLAB, type="top", **DRY)
    c.put(o, R, 0, 3, GOLD)                                         # the gakuzuka, the name tablet
    c.put(o, R, 0, 4, OAK_SLAB, type="top", **DRY)


def stone_lantern(c, dx, dz):
    """A tooro: a stone wall post, a paper lantern, a slab hood over it."""
    c.set(dx, 1, dz, SB_WALL)
    c.set(dx, 2, dz, "lantern", **LANTERN)
    c.set(dx, 3, dz, SB_SLAB, type="bottom", **DRY)


def blossom_tree(c, x, z):
    """A small flowering tree in the garden: a dark oak trunk, a two-cell azalea canopy over it (persistent leaves, so
    they never decay). One cell wide with open ground round it: never a pocket. Azalea, not cherry (lovish, Nov 4:
    "Azalea instead" - pink is the Verdant crown's): green with blossom, it reads green from the RTS camera."""
    leaf = dict(distance="1", persistent="true", waterlogged="false")
    c.set(x, 1, z, OAK_LOG, axis="y")
    c.set(x, 2, z, OAK_LOG, axis="y")
    c.set(x, 3, z, "flowering_azalea_leaves", **leaf)
    c.set(x, 4, z, "azalea_leaves", **dict(leaf, distance="2"))   # its true distance (on a leaf, not the log)


# ===================================================================================================== terrace
def terrace(c):
    """The stepped terrace, a rectangle under the long hall: polished andesite under it (the instant foundation) and
    stone bricks under the step, a moulded stone-brick step round it, a jade step (the verandah's floor), the hall floor
    of dark oak with a jade cross running in to a gold heart."""
    for dx, dz in rcells(*STEP):
        inside = abs(dx) <= VER[0] and abs(dz) <= VER[1]
        c.set(dx, 0, dz, "polished_andesite" if inside else "stone_bricks")
    for dx, dz in rcells(*VER):
        c.set(dx, 1, dz, "stone")
    for o, a, tm in rsides(*STEP):
        for t in range(-tm, tm + 1):
            c.st(o, a, t, 1, inward(o), name=SB_ST)
    for o, a, tm in rsides(*VER):
        for t in range(-tm, tm + 1):
            c.st(o, a, t, 2, inward(o), name=JADE_ST)
    for dx, dz in rcells(*HALL):
        c.set(dx, 2, dz, OAK)
    for o, a, tm in rsides(*HALL):
        for k in range(1, a + 1):
            c.put(o, k, 0, 2, JADE)
    c.set(0, 2, 0, GOLD)
    for (sx, sz) in CORNERS:
        guardian(c, sx, sz)


def guardian(c, sx, sz):
    """A jade guardian on each garden corner, looking out of it (out from under the eaves, so the RTS camera sees it):
    a chiseled plinth, a crouching jade body (a stair: haunches high at the back), a dragon's head, jade forepaws
    (low stairs) on the cells either side."""
    x, z = sx * R, sz * R
    c.set(x, 1, z, "chiseled_stone_bricks")
    c.stair(x, 2, z, "east" if sx < 0 else "west", name=JADE_ST)
    c.set(x, 3, z, JADE)               # the head: jade, not a black dragon head (Fable critic A: base-corner noise)
    c.stair(x - sx, 1, z, "east" if sx < 0 else "west", name=JADE_ST)     # the forepaws, either side
    c.stair(x, 1, z - sz, "south" if sz < 0 else "north", name=JADE_ST)


# ======================================================================================================== pagoda
def ground_storey(c):
    """The verandah (red columns on the jade step, under the first roof's overhang) round the long hall: a door three
    wide on every side, so the spawn cell in the middle walks straight out whichever way the base lies."""
    for o, a, tm in rsides(*VER):
        cols = (-tm, -2, 2, tm) if tm >= 4 else (-tm, tm)
        for t in range(-tm, tm + 1):
            if t in cols:              # stone bases, red lacquer shafts, gold capitals (under the eave)
                c.put(o, a, t, 2, "chiseled_stone_bricks")
                for y in (3, 4):
                    c.put(o, a, t, y, RED, axis="y")
                c.put(o, a, t, 5, GOLD)
            else:
                c.put(o, a, t, 5, OAK_SLAB, type="top", **DRY)       # the architrave, a slab so it reads thin
    for o, a, tm in rsides(*HALL):
        # the hall wall: a dark oak dado, paper screens over it; the door with stair shoulders
        for t in range(-tm + 1, tm):
            if abs(t) >= 2:
                c.put(o, a, t, 3, OAK)
                c.put(o, a, t, 4, PAPER)
                c.put(o, a, t, 5, PAPER)
            elif t:
                c.st(o, a, t, 5, along(o, t), "top", OAK_ST)
            c.put(o, a, t, 6, OAK)                                    # the beam the first roof's band sits on
    for (sx, sz) in CORNERS:
        for y in range(3, 7):
            c.set(sx * HALL[0], y, sz * HALL[1], RED, axis="y")


def storey(c, k, y0, y1):
    """A storey on ring k from y0 to y1: red lacquer corner posts, red paper screens between dark oak mullions, a
    dark oak beam under the roof. Ring 0 is the spire's core."""
    if k == 0:
        for y in range(y0, y1 + 1):
            c.set(0, y, 0, RED, axis="y")
        return
    for o in O4.values():
        for t in range(-k + 1, k):
            for y in range(y0, y1 + 1):
                c.put(o, k, t, y, PAPER if (t + k) % 2 == 0 and y < y1 else OAK)
    for (sx, sz) in CORNERS:
        for y in range(y0, y1 + 1):
            c.set(sx * k, y, sz * k, RED, axis="y")


CHIMES = []
# The roof treatment, picked blind (round 3, eyes 41281536246, critics a / b both rank it first, 6 / 6, over the
# dark-oak round 2 4.5 / 4.5, jade-only 5.5 / 5 and horns-without-sweep 5 / 5.5): value "jade" = jade roofs with one
# thin blackstone gap ring at each storey's foot ("r2" = the dark oak band + course); horn = (round 3 only) the rising
# diagonal arm past each corner; sweep = the eave rises one block over its last two cells to each corner (two at it)
STYLE = dict(value="jade", horn=2, sweep=True)
GAP, GAP_ST = "polished_blackstone", "polished_blackstone_stairs"


def roof(c, A, B, y, course=True, flare=False):
    """One pagoda roof (the kit's motif) with its lip on the A x B rectangle at y (a square when A == B):
    - the CURVE: the lip is a jade stair (low edge out) along the middle of each side, and over the two cells next to
      each corner a second stair climbs on the lip so the eave sweeps up as a stair run to the corner - a jade block
      under an upturned jade slab tip (Nov 4, Fable critic C F6: the sweep's full cubes read as a crenellated pixel
      edge; critic A fix 3: no gold on the corners, gold is the crown's spire alone);
    - the ring inside the lip is the flat band (jade, on the storey's beam below) and with `course` a second stair
      course climbs onto it one up in the dark gap stair: with the BLACKSTONE GAP ring inside the band each roof shows
      a 2-cell dark ring from the RTS camera and the tiers part (critic A fix 2: the 1-cell gap was too thin);
    - `flare` (squares only): the corner tip flares one cell past the outline as a solid jade wedge with an upturned
      slab;
    - dark-oak rafter ends under the lip, and a chime under each corner (placed last, see chimes())."""
    sw = STYLE["sweep"] and min(A, B) >= 3
    for o, a, tm in rsides(A, B):
        for t in range(-tm + 1, tm):
            c.st(o, a, t, y, inward(o), name=JADE_ST)
            if sw and abs(t) >= tm - 2:
                c.st(o, a, t, y + 1, inward(o), name=JADE_ST)
                if abs(t) == tm - 1:
                    c.put(o, a, t, y + 2, JADE_SLAB, type="bottom", **DRY)
            if min(A, B) >= 3 and abs(t) % 2 == 0 and abs(t) < tm - 1:
                c.st(o, a, t, y - 1, inward(o), "top", OAK_ST)         # rafter ends, every other cell
    if min(A, B) >= 2:
        for dx, dz in rcells(A - 1, B - 1, inner=(A - 3, B - 3)):
            c.set(dx, y, dz, JADE)                                       # the band
        if min(A, B) >= 3:
            for dx, dz in rcells(A - 2, B - 2, inner=(A - 3, B - 3)):
                c.set(dx, y, dz, GAP)                                    # the gap ring at the storey's foot
    lift = 1 if STYLE["sweep"] else 0
    for (sx, sz) in CORNERS:
        x, z = sx * A, sz * B
        c.set(x, y, z, JADE)
        if lift:
            c.set(x, y + 1, z, JADE)
        c.set(x, y + 1 + lift, z, JADE_SLAB, type="bottom", **DRY)
        if flare and A == B:
            L = A
            # the flared tip (round 4): a solid jade wedge one cell out, face-joined to the corner (the diagonal cell
            # and its two side cells at the corner tip's height) with an upturned slab on its point - round 3's
            # diagonal arm cells touched nothing and read as floating dots (both blind critics)
            ty = y + 1 + lift
            for hx, hz in ((L + 1, L + 1), (L + 1, L), (L, L + 1)):
                if c.get(sx * hx, ty, sz * hz)[0] == "air":
                    c.set(sx * hx, ty, sz * hz, JADE if (hx, hz) == (L + 1, L + 1) else JADE_SLAB,
                          **({} if (hx, hz) == (L + 1, L + 1) else dict(type="bottom", **DRY)))
            if c.get(sx * (L + 1), ty + 1, sz * (L + 1))[0] == "air":
                c.set(sx * (L + 1), ty + 1, sz * (L + 1), JADE_SLAB, type="bottom", **DRY)
        CHIMES.append((x, y - 1, z))
    if course and min(A, B) >= 3:
        # the second course: dark stairs climbing onto the band, so the roof pitches twice and its dark ring is two
        # cells deep; its corners stay open for the chimes of the roof above
        for o, a, tm in rsides(A - 1, B - 1):
            for t in range(-tm + 1, tm):
                c.st(o, a, t, y + 1, inward(o), name=GAP_ST)


def chimes(c):
    """The kit's signature under every eave corner: a chain with a glowing end-rod striker - two links where the roof
    below leaves room, else the striker alone."""
    for (x, y, z) in CHIMES:
        if c.get(x, y, z)[0] != "air":
            continue
        if c.get(x, y - 1, z)[0] == "air" and c.get(x, y - 2, z)[0] == "air":
            c.set(x, y, z, "chain", axis="y", **DRY)
            c.set(x, y - 1, z, "end_rod", facing="down")
        else:
            c.set(x, y, z, "end_rod", facing="down")


def pagoda(c):
    """THREE roofs two rings apart over tall storeys (Nov 2026; the five roofs one ring apart read from the RTS camera
    as "one four-sided pyramid of equal-height steps", two fresh critics 4 / 4; two blind massing rounds, eyes
    41275632354 / 41277286939, picked three deep roofs, tall storeys and a crown):
      the hall (ring 3, y3-6) in its verandah (ring 4)   | roof 1: lip ring 6 @ y6, course 5 @ 7 (a gutter at 4)
      storey 2 ring 3 y7-11 (shows y7-11 over roof 1)     | roof 2: lip ring 4 @ y12, courses 3 @ 13, 2 @ 13
      storey 3 ring 1 y12-16 (shows y14-16 over roof 2)   | roof 3: lip ring 2 @ y17, the crown y18-21
    Each storey stands one ring inside the lip above it: the steep RTS camera then sees a strip of red wall over
    every roof (a 2-ring overhang hid them: storey walls 0.1 % of the RTS view, the shipped five roofs 0.02 %).
    The centre column stays open from the hall floor (y3) to y9: getIndoorSpawnPoint walks down from the box's
    mid-height (y10) and must land on the floor, so the hall is an atrium up into storey 2."""
    ground_storey(c)
    roof(c, LIP1[0], LIP1[1], 6)                               # the long first roof, lip 15 x 11 @ y6, no flare
                                                               # (its east and west lips are the box's edges)
    for t in (-1, 0, 1):                                       # beams over the hall under storey 2's east / west walls
        for sx in (-1, 1):
            c.set(sx * 3, 6, t, OAK)
    storey(c, 3, 7, 11)                                        # storey 2, square, shows y7-11
    c.disc(2, 10, OAK)                                         # the atrium's ceiling
    c.set(0, 10, 0, "pearlescent_froglight", axis="y")         # the hall's light, over the spawn
    roof(c, 4, 4, 12, flare=True)                              # lip 4 @ y12, band ring 3, course ring 3 @ y13
    jade = STYLE["value"] == "jade"
    c.disc(2, 12, GAP if jade else OAK, k0=2)
    c.ring_stairs(2, 13, name=GAP_ST if jade else OAK_ST)      # course 2: ring 2 @ y13, against storey 3
    storey(c, 1, 12, 16)
    for y in (14, 15, 16):
        c.set(0, y, 0, "pearlescent_froglight", axis="y")      # glows through the paper of storey 3
    roof(c, 2, 2, 17, course=False, flare=True)               # lip 2 @ y17, the band ring 1; jade corners
    # the crown: a 3 x 3 jade cap with a thin two-high gold spire and the storm's lightning rod on it, over the top
    # roof's four jade tips, which carry small rods. Nov 4 (Fable critic C F1): the cap was gold - a gold apex cube is
    # the Sunforged's, and three buildings of two factions put one on top; the Stormcallers' top is the rod (the
    # violet-glass and amethyst halo went earlier: purple is a cool team hue)
    c.disc(1, 18, JADE)                                        # the jade cap
    for y in (19, 20):
        c.set(0, y, 0, GOLD)
    rod(c, 0, 21, 0)
    for (sx, sz) in CORNERS:
        rod(c, sx * 2, 18, sz * 2)
    # inside the hall: lanterns in its corners
    for (sx, sz) in CORNERS:
        c.set(sx * 2, 3, sz * 2, "lantern", **LANTERN)
    chimes(c)


def build():
    c = tc.Cap(R, H)
    CHIMES.clear()
    garden(c)
    for o in (O4["north"], O4["south"]):     # east and west the long first roof runs to the box's edge
        torii(c, o)
        for s in (-1, 1):
            x, z = c.side(o, R, 5 * s)
            stone_lantern(c, x, z)
        x, z = c.side(o, R, 4)
        blossom_tree(c, x, z)
    terrace(c)
    pagoda(c)
    c.rig.bury_grass()   # no grass under the posts and piers: it would die to dirt and stall the build
    c.rig.settle()
    c.rig.check_support("jade_pagoda")
    return c.rig.structure()


# ===================================================================================================== rules
FORBIDDEN = ("water", "lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "fire", "campfire",
             "dragon_egg", "scaffolding", "pointed_dripstone", "soul_sand", "magma_block")


def check(s, box=(2 * R + 1, H, 2 * R + 1)):
    """The rules the game needs, on the finished Structure (the support check ran in build())."""
    assert s.size == box, (s.size, box)
    names = {(x, y, z): s.palette[i][0] for (x, y, z), i in s.blocks.items()
             if s.palette[i][0] not in ("air", "cave_air")}
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
    # the spawn: the box centre walked down while the block below is air, onto the hall floor
    cx, cz = box[0] // 2, box[2] // 2
    y = (box[1] - 1) // 2
    while y > 0 and (cx, y - 1, cz) not in names:
        y -= 1
    assert y == 3 and (cx, y, cz) not in names and (cx, y + 1, cz) not in names, "the centre spawn lands at y%d" % y
    for (x, y, z) in SPARK_SPOTS:
        assert names.get((int(x), int(y), int(z))) in ("lightning_rod", "amethyst_cluster"), \
            "nothing under the spark spot %s" % ((x, y, z),)
    return n


def audit_pockets(path):
    import audit_structure_pockets as asp
    return asp.audit("jade_pagoda", path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/jade_pagoda.nbt"
    st = build()
    print("blocks:", check(st))
    st.write(out)
    one_way, ground, roof_cells, rooms, note = audit_pockets(out)
    print("pockets: one-way %d, ground %d, roof-only %d, rooms %d%s" % (len(one_way), len(ground), len(roof_cells),
                                                                        len(rooms), note))
    assert not one_way and not ground and "TRAPPED" not in note, (one_way[:8], ground[:8], note)
    print("wrote", out)
