#!/usr/bin/env python3
"""Verdant Court metal extractor: THE ROOT SPRING (the Stag-Dryad's Well).

lovish (Oct 19 2026): "the metal extractors could be fountains with angels or something more creative, the current
metal extractor building looks really ugly". The old Verdant extractor was four oak roots round a raw-iron core - a
squat brown-and-green lump that read as a stump from the RTS camera. This one tells the Court's story instead: the
Court does not dig, it asks the forest. A ruined, mossy stone well holds still water over the ore (raw iron glinting
on the well floor), and a living tree has grown out of the well's back rim and claimed it: its roots claw across the
patch and grip the broken stones, its trunk swells into the face of a stag-dryad - pale birch mask, glowing green
eyes under bark brows, a muzzle that drips a sheet of water back into the well - and its antlers ARE its branches,
pale birch bone carrying leaves and glowing blossoms. The metal is "drawn up" by the roots into the spirit.

Inspiration (never traced): the sandstone gothic window with vines (depth by recessing, green growing over carved
stone -> the broken arch springer and the mossy pillar stumps gripped by roots), the overgrown air temple (a carved
face half-swallowed by the garden) and organic arches (the antlers branching like ribs); the Forest Spirit's antlered
head as the "sculpted creature that still reads" (art note on the gargoyle reference).

THE BUILDING STANDARD on a 5 x 5 patch:
  1. three depths on the camera faces (west x=0, south z=4): broken pillars and the stepped kerb outside, the water
     recessed one block in, the stag's mask behind it with the eyes sunk under overhanging dark-oak brows;
  2. a broken top: the antlers - birch knuckles joined by fence, bare tines - fork against the sky, never a solid
     column (a log column read as a pillar in the first pass);
  3. mixed materials: mossy / cracked / chiseled stone bricks and mossy cobble for the ruin, oak bark and oak stairs
     for the trunk, stripped birch for the mask and antlers (the Court's silver), azalea leaves, froglights;
  4. Verdant style: grown, not built - nothing square except the old stones the tree is eating;
  5. dressing: a fern, pink petals on the rooted dirt, moss carpet on the snapped pillar, a lantern, lime candles;
  6. tier 2 (the Elder Spring): the tree's leader rises between the antlers into a flowering crown worn behind them
     like a cloak and spilling forward as a hood, with a shroomlight heart and glowing fruit; taller tines; the west
     pillar regrown into a lantern post, the east one taller; the arch springer gains a stone; the fern becomes a
     flowering azalea. The ore under the water becomes a copper vein: the waxed_copper_block tier marker, hidden in
     plain sight as the richer metal glinting under the spring (the game detects tier 2 by it);
  7. lights: the green froglight eyes and the jewel between the antlers, the candles and the lantern (+ fruit at T2).

Hard rules kept: 5 x 5 footprint, 14 tall; the water is three still sources in a row, each walled on every side
(kerb stairs in front, kerb blocks at the ends, roots and the trunk foot behind) on a solid floor, and the muzzle's
"drip" is a glass pane (nothing flows); nothing hangs (the build runs bottom-up) and no vines (a vine placed before
its support pops off); leaves are persistent and carry their true distance to the wood (_leaf_distances: vanilla
recomputes it, RoN compares placed states); no gravity blocks. check_mex_tiers asserts T1 has no tier marker, T2 has
the waxed copper and keeps every T1 block. T1 108 blocks, T2 154.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

H = 14                        # canvas height (T1 and T2 share it so check_mex_tiers compares like with like)
DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
LOGS = ("_log", "_wood", "_stem", "_hyphae")


def _leaf(r, x, y, z, name="azalea_leaves"):
    """A leaf placeholder; its distance is fixed by _leaf_distances once the wood stands."""
    if (x, y, z) not in r.b:
        r.set(x, y, z, name, persistent="true", distance="7", waterlogged="false")


def _leaf_distances(r):
    """Give every leaf its true distance to the nearest log (vanilla LeavesBlock rules: log = 0, leaf = 1 + min of
    its six neighbours, capped at 7), so the placed state is the state the game settles on."""
    dist = {}
    frontier = [p for p, (n, _) in r.b.items() if n.endswith(LOGS)]
    for p in frontier:
        dist[p] = 0
    while frontier:
        nxt = []
        for (x, y, z) in frontier:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                q = (x + dx, y + dy, z + dz)
                blk = r.b.get(q)
                if blk and blk[0].endswith("_leaves") and q not in dist and dist[(x, y, z)] < 7:
                    dist[q] = dist[(x, y, z)] + 1
                    nxt.append(q)
        frontier = nxt
    for p, (n, props) in list(r.b.items()):
        if n.endswith("_leaves"):
            r.b[p] = (n, dict(props, distance=str(min(dist.get(p, 7), 7))))


# ---- tier 1: the Root Spring ------------------------------------------------------------------------------------
# Layout (the camera looks from the south-west: the west face x=0 and the south face z=4 are the show):
#   z=4  front kerb of the well, broken pillars on the corners       (outer plane)
#   z=3  the still water, three sources, over the ore                 (recess)
#   z=2  roots drinking at the back of the well; the stag face above  (wall plane)
#   z=0-1 the trunk, the skull and the antlers; the old arch stub     (back mass)

def _antler(r, x_in, x_out, out_dir, top):
    """One antler in the x-y plane at z = 1 (spread across the camera's view, like the Angel's wings). Solid birch
    only at the burr and the knuckles, birch fence between and for the tines: a pale, open, branching line against
    the sky - a full log column read as a pillar, not an antler. `out_dir` is the side it sweeps toward."""
    r.set(x_in, 7, 1, "stripped_birch_log", axis="y")     # the burr, on the brow
    r.set(x_in, 8, 1, "birch_fence")                       # the brow tine, forking straight up
    r.stair(x_out, 8, 1, out_dir, "top", name="birch_stairs")  # the beam leaves the burr and sweeps outward
    r.set(x_out, 9, 1, "birch_fence")
    r.set(x_out, 10, 1, "stripped_birch_log", axis="y")   # the knuckle
    r.set(x_out, 10, 2, "birch_fence")                     # a tine forking toward the camera (depth)
    for y in range(11, top + 1):
        r.set(x_out, y, 1, "birch_fence")                  # the top tine


def _spring_t1(r):
    # the pad: an old flagged court the moss has mostly won - rooted dirt / mossy cobble corners, a broken kerb of
    # mixed mossy and cracked stone, moss inside, and the ore under the well (raw iron: the metal the tree drinks)
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            if corner:
                b = "mossy_cobblestone"
            elif edge:
                b = ("mossy_stone_bricks", "cracked_stone_bricks", "mossy_cobblestone")[(x * 3 + z) % 3]
            else:
                b = "moss_block"
            r.set(x, 0, z, b)
    r.set(0, 0, 0, "rooted_dirt")                          # where the roots broke the flags
    r.set(4, 0, 0, "rooted_dirt")
    for x in (1, 3):
        r.set(x, 0, 3, "mossy_cobblestone")                # the well floor ...
    r.set(2, 0, 3, "raw_iron_block")                       # ... with the ore glinting through the water

    # the well: a stepped kerb on the camera side (stairs rising toward the water, so the rim reads as a moulding),
    # three still sources walled on every side (kerb in front and at the ends, roots and trunk foot behind)
    for x in (1, 2, 3):
        r.stair(x, 1, 4, "north", name="mossy_stone_brick_stairs")
        r.set(x, 1, 3, "water", level="0")
    r.set(0, 1, 3, "mossy_stone_bricks")                  # the kerb's ends: square blocks the candles stand on
    r.set(4, 1, 3, "cracked_stone_bricks")
    r.set(1, 1, 2, "mangrove_roots", **DRY)                # roots reaching into the water: the tree drinks the ore
    r.set(3, 1, 2, "mangrove_roots", **DRY)
    r.set(2, 1, 2, "oak_wood", axis="y")                   # the trunk's foot, standing in the well
    # broken pillars on the front corners: chiseled base, the west one snapped short (moss on it), the east one
    # whole enough to carry a lantern - the ruin the forest is eating
    r.set(0, 1, 4, "chiseled_stone_bricks")
    r.set(0, 2, 4, "moss_carpet")
    r.set(4, 1, 4, "chiseled_stone_bricks")
    r.set(4, 2, 4, "mossy_stone_brick_wall")
    r.set(4, 3, 4, "lantern", **LANTERN)
    # lit candles on the kerb's ends, framing the water
    r.set(0, 2, 3, "lime_candle", candles="3", lit="true", **DRY)
    r.set(4, 2, 3, "lime_candle", candles="2", lit="true", **DRY)

    # the trunk and its claws: a gnarled oak foot, buttress roots clawing out to the west and east edges as oak
    # stairs (bark rising toward the trunk), a mangrove root creeping down the west face toward the camera
    for x in (1, 2, 3):
        r.set(x, 1, 1, "oak_wood", axis="y")
    r.set(2, 1, 0, "oak_wood", axis="y")
    r.stair(0, 1, 1, "east", name="oak_stairs")
    r.stair(4, 1, 1, "west", name="oak_stairs")
    r.stair(1, 1, 0, "east", name="oak_stairs")
    r.stair(3, 1, 0, "west", name="oak_stairs")
    r.set(0, 1, 2, "mangrove_roots", **DRY)
    for y in (2, 3):
        r.set(2, y, 1, "oak_log", axis="y")                # the neck: one trunk, open sides (the head reads apart)
    r.set(2, 2, 0, "oak_wood", axis="y")                   # a back buttress
    r.set(2, 2, 2, "oak_stairs", facing="south", half="top", shape="straight", **DRY)  # the throat, over the roots

    # the stag-dryad's head on the trunk, facing south over the water: an oak skull (z = 0..1) and a pale birch mask
    # (z = 2). Eyes are verdant froglights set IN the mask between the muzzle and the brow (glowing sockets, not
    # lamps stuck on), the snout pushes out over the water and drips a thread of water back into the well
    for x in (1, 2, 3):
        for y in (3, 4, 5, 6):
            if y == 3 and x != 2:
                continue
            r.set(x, y, 1, "oak_wood", axis="y")
    r.stair(1, 3, 2, "east", "top", name="oak_stairs")     # the jaw line, hollowed under
    r.stair(3, 3, 2, "west", "top", name="oak_stairs")
    r.set(2, 3, 2, "stripped_birch_wood", axis="y")        # the muzzle
    r.stair(2, 4, 3, "north", name="birch_stairs")         # the snout over the water, sloping down to the nose
    r.set(2, 3, 3, "light_blue_stained_glass_pane", **DRY)  # water dripping from the muzzle
    r.set(2, 2, 3, "light_blue_stained_glass_pane", **DRY)
    r.stair(1, 4, 2, "east", "top", name="birch_stairs")   # cheeks
    r.stair(3, 4, 2, "west", "top", name="birch_stairs")
    r.set(2, 4, 2, "stripped_birch_wood", axis="y")
    r.set(1, 5, 2, "verdant_froglight", axis="y")          # the eyes
    r.set(3, 5, 2, "verdant_froglight", axis="y")
    r.set(2, 5, 2, "stripped_birch_wood", axis="y")        # the bridge of the nose
    r.stair(1, 6, 2, "south", "top", name="dark_oak_stairs")  # the brow, overhanging the eyes (dark bark)
    r.stair(3, 6, 2, "south", "top", name="dark_oak_stairs")
    r.set(2, 6, 2, "stripped_birch_wood", axis="y")        # the forehead blaze
    # leafy ears flicked out from the back of the skull, behind the brow (the dryad half of the stag)
    _leaf(r, 0, 6, 1, "flowering_azalea_leaves")
    _leaf(r, 4, 6, 1, "azalea_leaves")

    # the antlers are the tree's branches: pale birch bone spreading across the whole patch
    _antler(r, 1, 0, "east", 11)
    _antler(r, 3, 4, "west", 11)
    # between them, the moss crown of the head and a glowing bud - the dryad's jewel, the spring's night light
    r.set(2, 7, 1, "moss_block")
    r.set(2, 8, 1, "verdant_froglight", axis="y")
    r.set(2, 7, 0, "oak_wood", axis="y")
    # young leaves budding along the antler beams (on their backs, so the tines stay bare against the sky), and a
    # glowing blossom on each - the spring is in flower
    for x in (0, 4):
        _leaf(r, x, 9, 0, "flowering_azalea_leaves" if x == 0 else "azalea_leaves")
        _leaf(r, x, 10, 0, "azalea_leaves")

    # the old well-house the tree grew through: an arch springer stub at the back-west corner, gripped by a root
    r.set(0, 1, 0, "mossy_stone_bricks")
    r.set(0, 2, 0, "cracked_stone_bricks")
    r.stair(0, 3, 0, "west", "top", name="mossy_stone_brick_stairs")   # the springer, leaning toward the tree
    # undergrowth on the free cells (walkable, never trapping): ferns, flowers, moss carpet
    r.set(4, 1, 2, "fern")
    r.set(4, 1, 0, "pink_petals", facing="north", flower_amount="3")   # on the rooted dirt where the roots broke in


# ---- tier 2: the Elder Spring -----------------------------------------------------------------------------------

def _spring_t2(r):
    _spring_t1(r)
    # the tier marker: the ore under the well has become a copper vein (seen through the water as the richer ore -
    # old green-gold metal under the spring), the only waxed_copper_block in the building
    r.set(2, 0, 3, "waxed_copper_block")
    # the antlers grow a tine taller, and the tree's leader rises between them into a flowering crown BEHIND the
    # antlers (z <= 1, y >= 9): the stag wears the tree like a cloak, the face below stays clear and the pale tines
    # stand out in front of the leaves (the broken top). A shroomlight heart and two pieces of glowing fruit.
    for x in (0, 4):
        r.set(x, 12, 1, "birch_fence")
        r.set(x, 13, 1, "birch_fence")
    for y in (9, 10, 11):
        r.set(2, y, 1, "oak_log", axis="y")                # the leader
    r.set(1, 11, 0, "oak_log", axis="x")                   # boughs spreading from it
    r.set(3, 11, 0, "oak_log", axis="x")
    r.set(2, 11, 0, "oak_wood", axis="y")
    r.set(2, 12, 1, "shroomlight")                         # the great fruit at the heart of the crown
    for y, rad in [(9, 1.6), (10, 2.3), (11, 2.6), (12, 3.0), (13, 1.8)]:
        for x in range(5):
            for z in range(4 if y >= 12 else 3):          # the top spills forward over the face like a hood
                if ((x - 2) ** 2 + ((z - 0.2) * 1.5) ** 2) ** 0.5 <= rad and r.get(x, y, z)[0] == "air":
                    if z == 1 and x in (0, 4) or (z == 2 and y < 12):
                        continue                           # the antler tines stay bare, the face stays clear
                    _leaf(r, x, y, z, gs.leaf_for(x, y, z, 4))
    r.set(1, 12, 1, "ochre_froglight", axis="y")           # glowing fruit in the leaves
    r.set(3, 10, 0, "ochre_froglight", axis="y")
    r.set(2, 13, 0, "verdant_froglight", axis="y")
    r.set(1, 12, 3, "ochre_froglight", axis="y")           # fruit on the hood's front edge, toward the camera
    r.set(3, 13, 2, "verdant_froglight", axis="y")
    # the fern by the east root has grown into a flowering azalea bush, the petals have spread
    r.set(4, 1, 2, "flowering_azalea")
    r.set(4, 1, 0, "pink_petals", facing="north", flower_amount="4")
    # the west pillar regrown into a lantern post (a root climbing it), the east one taller
    r.set(0, 2, 4, "mossy_stone_brick_wall")
    r.set(0, 3, 4, "lantern", **LANTERN)
    r.set(4, 3, 4, "mossy_stone_brick_wall")
    r.set(4, 4, 4, "lantern", **LANTERN)
    r.set(4, 2, 4, "mossy_stone_bricks")
    # the springer grows its next stone and leans into the trunk: the arch the tree swallowed
    r.set(0, 3, 0, "mossy_stone_bricks")
    r.stair(1, 3, 0, "west", "top", name="mossy_stone_brick_stairs")
    # more candles, and flowers in the moss
    r.set(0, 2, 3, "lime_candle", candles="4", lit="true", **DRY)
    r.set(4, 2, 3, "lime_candle", candles="4", lit="true", **DRY)


def _build(fn, tag):
    r = gs._SunRig(5, H, 5)
    fn(r)
    _leaf_distances(r)
    r.settle()
    r.check_support(tag)
    _check_water(r, tag)
    return r


def _check_water(r, tag):
    for (x, y, z), (n, _) in r.b.items():
        if n == "water":
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                assert r.get(x + dx, y, z + dz)[0] != "air", f"verdant {tag}: water open at {(x + dx, y, z + dz)}"
            assert gs._sun_sturdy(r.get(x, y - 1, z), "up"), f"verdant {tag}: water not floored"


def t1():
    """T1 'Root Spring': a ruined mossy well of still water over raw iron, claimed by a living stag-dryad tree."""
    return _build(_spring_t1, "T1").structure()


def t2():
    """T2 'Elder Spring': the T1 grown - a flowering crown with glowing fruit in taller antlers, lanterns on two
    ruined pillars, a copper vein (waxed copper, the tier marker) under the water."""
    s2 = _build(_spring_t2, "T2").structure()
    gs.check_mex_tiers(t1(), s2, "verdant")
    return s2


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    a, b = t1(), t2()
    a.write(os.path.join(out, "metal_extractor_verdant.nbt"))
    b.write(os.path.join(out, "metal_extractor_t2_verdant.nbt"))
