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
    second stair course pitches it again onto the band; a paper lantern stands on each band's axis like a lamp set
    out on a balcony, and a CHIME (chain + end-rod striker) hangs from every corner - twenty of them;
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
    and the four corner crystals of the halo; same convention as the others: the block at int(y));
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
PAPER = "red_stained_glass"        # paper screens, lit from inside
GOLD = "gold_block"
SB_ST, SB_SLAB, SB_WALL = "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"
SKULL_CORNER = {(-1, 1): "2", (-1, -1): "6", (1, -1): "10", (1, 1): "14"}   # standing-head rotation out of a corner

# where the game looks (structure coordinates; the centre cell is (R, y, R))
# BuildingAmbienceClientEvents.CAPITOL_SPOTS["jade_pagoda"]: Fx.SPARK over the crown rod and four halo crystals
SPARK_SPOTS = ((7.5, 21.9, 7.5), (5.5, 18.5, 5.5), (9.5, 18.5, 5.5), (5.5, 18.5, 9.5), (9.5, 18.5, 9.5))
STARTING_TYPES = {"polished_andesite", "stone_bricks"}   # JadePagoda.startingBlockTypes


def rod(c, dx, y, dz, facing="up"):
    c.set(dx, y, dz, "lightning_rod", facing=facing, powered="false", **DRY)


def chime(c, dx, y, dz, links=1):
    """The kit's signature: a chain (links long) hanging from y, a glowing end-rod striker under it."""
    for i in range(links):
        c.set(dx, y - i, dz, "chain", axis="y", **DRY)
    c.set(dx, y - links, dz, "end_rod", facing="down")


# ======================================================================================================= ground
def garden(c):
    """Rings 6-7 at ground level: no kerb, no road. Ring 6 is the raked zen ground (stone and polished andesite in
    rings - real gravel would fall), stepping-stone flags of stone bricks (the instant foundation, layer 0) and moss;
    ring 7 breaks up into moss, grass and blossom, so whichever way the base grows the capitol's ground runs into it."""
    for dx, dz in c.cells(R, 6):
        ring = max(abs(dx), abs(dz))
        t = min(abs(dx), abs(dz))
        h = hsh(dx, dz)
        if ring == 6:
            name = ("andesite", "polished_andesite", "stone_bricks", "andesite", "moss_block", "polished_andesite")[h % 6]
            if t <= 1:
                name = "stone_bricks"                      # the flags at the foot of each stair
        else:
            name = ("moss_block", "grass_block", "andesite", "grass_block", "moss_block", "stone_bricks",
                    "grass_block", "mossy_cobblestone")[h % 8]
        c.set(dx, 0, dz, name, **({"snowy": "false"} if name == "grass_block" else {}))
    # the dressing (y1): petals, moss, azalea bushes on the soft cells of ring 6 between the stairs and the
    # guardians - single cells with ground round them (no pockets). Ring 7 belongs to the torii (t <= 3), the
    # blossom trees (t = 4), the stone lanterns (t = 5) and the guardians (t >= 6)
    for dx, dz in c.cells(6, 6):
        t = min(abs(dx), abs(dz))
        if t <= 1 or t >= 6:
            continue
        h = hsh(dx, dz)
        k = h % 5
        under = c.get(dx, 0, dz)[0]
        if under not in ("grass_block", "moss_block"):
            continue
        if k in (0, 1):
            c.set(dx, 1, dz, "pink_petals", facing="north", flower_amount=str(2 + h % 3))
        elif k == 2:
            c.set(dx, 1, dz, "flowering_azalea")
        elif k == 3:
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
    """A small blossom tree in the garden: a dark oak trunk, a two-cell cherry canopy over it (persistent leaves, so
    they never decay), petals at its foot. One cell wide with open ground round it: never a pocket."""
    leaf = dict(distance="1", persistent="true", waterlogged="false")
    c.set(x, 1, z, OAK_LOG, axis="y")
    c.set(x, 2, z, OAK_LOG, axis="y")
    c.set(x, 3, z, "cherry_leaves", **leaf)
    c.set(x, 4, z, "cherry_leaves", **dict(leaf, distance="2"))   # its true distance (on a leaf, not the log)


# ===================================================================================================== terrace
def terrace(c):
    """The stepped terrace: polished andesite under it all (the instant foundation), a moulded stone-brick step on
    ring 5, a jade step on ring 4, the hall floor of dark oak with a jade cross running in to a gold heart."""
    c.disc(5, 0, "polished_andesite")
    c.disc(4, 1, "stone")
    c.ring_stairs(5, 1, name=SB_ST)
    c.ring_stairs(4, 2, name=JADE_ST)
    c.disc(3, 2, OAK)
    for o in O4.values():
        for a in (1, 2, 3):
            c.put(o, a, 0, 2, JADE)
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
    c.set(x, 3, z, "dragon_head", rotation=SKULL_CORNER[(sx, sz)])
    c.stair(x - sx, 1, z, "east" if sx < 0 else "west", name=JADE_ST)     # the forepaws, either side
    c.stair(x, 1, z - sz, "south" if sz < 0 else "north", name=JADE_ST)


# ======================================================================================================== pagoda
def ground_storey(c):
    """The verandah (red columns on ring 4, under the first roof's 1-block overhang) round the hall (ring 3): a door
    three wide on every side, so the spawn cell in the middle walks straight out whichever way the base lies."""
    for o in O4.values():
        # the colonnade: stone bases, red lacquer shafts, gold capitals, a dark-oak architrave between them
        for t in (-4, -2, 2, 4):
            c.put(o, 4, t, 2, "chiseled_stone_bricks")
            for y in (3, 4):
                c.put(o, 4, t, y, RED, axis="y")
            c.put(o, 4, t, 5, GOLD)
        for t in (-3, -1, 0, 1, 3):
            c.put(o, 4, t, 5, OAK_SLAB, type="top", **DRY)             # the architrave, a slab so it reads thin
        # the hall wall on ring 3: a dark oak dado, red paper screens over it; the door with stair shoulders
        for s in (-1, 1):
            c.put(o, 3, 2 * s, 3, OAK)
            c.put(o, 3, 2 * s, 4, PAPER)
            c.put(o, 3, 2 * s, 5, PAPER)
            c.st(o, 3, s, 5, along(o, s), "top", OAK_ST)
        for t in range(-3, 4):
            c.put(o, 3, t, 6, OAK)                                    # the beam the first roof's band sits on
    for (sx, sz) in CORNERS:
        for y in range(3, 7):
            c.set(sx * 3, y, sz * 3, RED, axis="y")


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


def roof(c, L, y, course=True):
    """One pagoda roof (the kit's motif) with its lip on ring L at y:
    - the CURVE: the lip is a jade stair (low edge out) along the middle of each side, but the two cells next to each
      corner lift a block (a jade block under a slab) and the corner itself is a gold block under an upturned jade
      slab tip - so the eave sags in the middle and sweeps up to its corners;
    - ring L-1 is the flat band (full jade, sitting on the storey's beam below), and with `course` a second stair
      course climbs onto ring L-1 one up, against the next storey's wall;
    - dark-oak rafter ends under the lip, and a chime under each corner (placed last, see chimes())."""
    for o in O4.values():
        for t in range(-L + 1, L):
            if L >= 3 and abs(t) == L - 1:
                c.put(o, L, t, y, JADE)
                c.put(o, L, t, y + 1, JADE_SLAB, type="bottom", **DRY)
            else:
                c.st(o, L, t, y, inward(o), name=JADE_ST)
            if L >= 3 and abs(t) % 2 == 0 and abs(t) < L - 1:
                c.st(o, L, t, y - 1, inward(o), "top", OAK_ST)         # rafter ends, every other cell
    if L >= 2:
        c.disc(L - 1, y, JADE, k0=max(L - 2, 0))
    for (sx, sz) in CORNERS:
        x, z = sx * L, sz * L
        c.set(x, y, z, GOLD)
        c.set(x, y + 1, z, JADE_SLAB, type="bottom", **DRY)
        CHIMES.append((x, y - 1, z))
    if course and L >= 3:
        # the second course: jade stairs climbing onto the band, so the roof pitches twice (the curve read from the
        # side); its corners stay open for the chimes of the roof above, and on each axis a paper lantern stands on
        # the band instead, like a lamp set out on a balcony
        for o in O4.values():
            for t in range(-L + 2, L - 1):
                if t == 0:
                    c.put(o, L - 1, 0, y + 1, "lantern", **LANTERN)
                else:
                    c.st(o, L - 1, t, y + 1, inward(o), name=JADE_ST)


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
    roof(c, 6, 6)                                              # lip 6 @ y6, band rings 4-5, course ring 5 @ y7
    storey(c, 3, 7, 11)                                        # storey 2 on the hall's walls, shows y7-11
    c.disc(2, 10, OAK)                                         # the atrium's ceiling
    c.set(0, 10, 0, "pearlescent_froglight", axis="y")         # the hall's light, over the spawn
    roof(c, 4, 12)                                             # lip 4 @ y12, band ring 3, course ring 3 @ y13
    c.disc(2, 12, JADE, k0=2)
    c.ring_stairs(2, 13, name=JADE_ST)                         # course 2: ring 2 @ y13, against storey 3
    storey(c, 1, 12, 16)
    for y in (14, 15, 16):
        c.set(0, y, 0, "pearlescent_froglight", axis="y")      # glows through the paper of storey 3
    roof(c, 2, 17, course=False)                               # lip 2 @ y17, the band ring 1
    # the crown: a jade hip on a three-high gold boss, a lightning rod on it; the top roof's four upturned tips are
    # rods too - a crown of five rods, the storm's mark (the violet-glass and amethyst halo is gone: purple is a cool
    # team hue in a fixed palette, both baseline and massing critics)
    for o in O4.values():
        c.st(o, 1, 0, 18, inward(o), name=JADE_ST)
    for (sx, sz) in CORNERS:
        c.slab(sx, 18, sz, JADE_SLAB)
    for y in (18, 19, 20):
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
    for o in O4.values():
        torii(c, o)
        for s in (-1, 1):
            x, z = c.side(o, R, 5 * s)
            stone_lantern(c, x, z)
    for o in O4.values():
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
