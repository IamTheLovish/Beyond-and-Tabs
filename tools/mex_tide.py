#!/usr/bin/env python3
"""Tidewrought metal extractor: THE TIDE-POOL PUMP (T1) / THE LIGHTHOUSE PUMP (T2).

lovish (Oct 19 2026): "the current metal extractor building looks really ugly"; his brainstorm picked the Tidewrought
extractor: "tide-pool pump with a waterwheel (T2 lighthouse pump tower)". The old one was a 'dredge rig', four mangrove
posts and a beam over a well: a box frame from the RTS camera. This one tells the wreck-salvagers' story (Wreck Harbour,
Slipway, Drydock, Lighthouse Battery): they "bring the sea with them". They wall in a tide pool over the ore, and a big
driftwood WATERWHEEL standing in the pool turns a pump in a small striped sea-wall house. A crane made from a ship's
mast swings the lifted ore down a timber chute into the salvage barrels. At T2 the pump house becomes a lighthouse,
the same striped tower as the Lighthouse Battery, so an upgraded mine reads from across the map as a light.

STYLE KIT (Tidewrought; every building of the faction is built from it so a base reads as one harbour):
  main blocks: prismarine (prismarine bricks / dark prismarine), sandstone (cut / smooth / chiseled), driftwood
  (stripped dark oak and stripped mangrove logs, dark oak planks);  accent: sea lanterns;  trim: driftwood and rope
  (dark oak fences, chains);  light: sea lanterns;  silhouette motif: the striped lighthouse tower;
  signature detail: weathered rope and chain (the chains holding the wheel to its posts, the wheel's chain spokes, the
  crane's chain).

THE BUILDING STANDARD on a 5 x 5 patch (the camera looks from the south-west: the west face x=0 and south face z=4):
  1. three depths: the kerb (z=4), the pool and chute (z=3), the wheel's plane (z=2), the pump
     house behind (z=0-1); on the west face: the post / wheel rim, the house, the crane mast;
  2. a broken top: at T1 the stepped house roof with its lantern finial, the crane mast and jib with a hanging chain;
     at T2 the lighthouse lamp room, copper cap and rod, the gallery fence posts and the jib beside it;
  3. mixed materials: the striped prismarine/sandstone house, the two-tone wheel (mangrove rim, dark-oak paddle
     boards), stairs on the roof edges and the wheel's top corners, fences / chains as the thin members;
  4. Tidewrought style: harbour stone and ship timber, with a lighthouse at T2;
  5. dressing: a dead coral fan on the kerb, the salvage barrel with raw iron on it, sea lanterns in the kerb;
  6. tier 2 (the Lighthouse Pump): the pump house rises into a striped lighthouse with a copper band, a gallery and a
     lamp room of cyan glass round a sea lantern under a waxed-copper cap (the waxed_copper_block tier marker is the
     cap's centre), a lantern post on the kerb and a lit doorway;
  7. lights: the glowing ore core in the pool, the sea lanterns, the lanterns on the posts and the crane.

THE ORE CORE (the shared tell of every extractor): an ochre froglight sits in the middle of the pool, between two
still water sources over raw iron. The RTS camera sees it in front of the wheel.

Hard rules kept: 5 x 5, both tiers on a 14-tall canvas; the two water blocks are still sources, each walled on every
side (the dark-prismarine kerb end, the froglight, the kerb stair in front, the wheel's bottom rim behind) on a raw-iron
floor, so nothing flows and no unit can step into open water (tools/audit_structure_pockets.py: 0 pockets); no gravity
blocks (cut / smooth / chiseled sandstone, never sand); coral is dead (live coral dies out of water); lanterns stand on
walls, fences or full blocks (check_support); check_mex_tiers asserts T1 holds no copper_block / cut_copper /
waxed_copper_block, and T2 keeps every T1 block and holds the waxed copper. T1 136 blocks, T2 184 (y = 0 pad included).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

H = 14
DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
ROD = dict(powered="false", waterlogged="false")
RIM = "stripped_mangrove_log"
RIM_ST = "mangrove_stairs"
POST = "stripped_dark_oak_log"
SPOKE = "dark_oak_fence"
PB, DP = "prismarine_bricks", "dark_prismarine"
CUT, SMOOTH = "cut_sandstone", "smooth_sandstone"
WZ = 2                        # the wheel's plane: face-on to the camera's south side, at 45 degrees from the RTS view
# the lighthouse stripes, two rows each (the Lighthouse Battery's bands), dark prismarine at the foot
BAND = {1: DP, 2: CUT, 3: CUT, 4: PB, 5: PB, 6: CUT, 7: PB, 8: PB}


def _t1(r):
    # the pad: a prismarine kerb with chiseled sandstone corners round a smooth sandstone apron, the Wreck Harbour's
    # and the Slipway's floor; raw iron (the ore) under the pool
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            r.set(x, 0, z, "chiseled_sandstone" if corner else PB if edge else SMOOTH)
    for x in (1, 2, 3):
        r.set(x, 0, 3, "raw_iron_block")
    # the tide pool: a stepped prismarine kerb in front (stairs rising toward the water, so the rim reads as a
    # moulding), dark prismarine ends; two still sources with the glowing ore core between them
    r.set(0, 1, 4, CUT)
    r.set(4, 1, 4, CUT)
    for x in (1, 2, 3):
        r.stair(x, 1, 4, "north", name="prismarine_brick_stairs")
    r.set(0, 1, 3, DP)
    r.set(4, 1, 3, DP)
    r.set(1, 1, 3, "water", level="0")
    r.set(3, 1, 3, "water", level="0")
    r.set(2, 1, 3, "ochre_froglight", axis="y")
    # the waterwheel, upright in the x-y plane at z = 2 with its bottom rim in the pool. The rim is stripped
    # mangrove (the red driftwood reads apart from the striped stone behind it), each side's middle is a dark-oak
    # paddle board, the top corners are rounded with stairs; fence spokes run to a copper hub and chain spokes fill the
    # diagonals, so the wheel is open and airy, not a solid disc. Its axle runs back into the pump house.
    for x in (1, 2, 3):
        for y in (1, 5):
            r.set(x, y, WZ, "dark_oak_planks" if x == 2 else RIM, axis="x")
    for y in (2, 3, 4):
        for x in (0, 4):
            r.set(x, y, WZ, "dark_oak_planks" if y == 3 else RIM, axis="y")
    r.stair(0, 5, WZ, "east", name="dark_oak_stairs")
    r.stair(4, 5, WZ, "west", name="dark_oak_stairs")
    r.set(0, 1, WZ, DP)
    r.set(4, 1, WZ, DP)
    r.set(2, 3, WZ, "waxed_cut_copper")
    for (x, y) in [(1, 3), (3, 3), (2, 2), (2, 4)]:
        r.set(x, y, WZ, SPOKE)
    for (x, y) in [(1, 2), (3, 2), (1, 4), (3, 4)]:
        r.set(x, y, WZ, "chain", axis="y", **DRY)
    r.set(2, 3, 1, POST, axis="z")
    # the pump house behind the wheel: a striped stub of a lighthouse (the faction's silhouette motif) with a sea
    # lantern window, a stepped dark-prismarine roof and a lantern finial
    for y in range(1, 7):
        for x in (1, 2, 3):
            for zz in (0, 1):
                if (x, y, zz) != (2, 3, 1):
                    r.set(x, y, zz, BAND.get(y, CUT))
    r.set(1, 4, 1, "sea_lantern")
    for zz in (0, 1):
        r.stair(1, 7, zz, "east", name="dark_prismarine_stairs")
        r.stair(3, 7, zz, "west", name="dark_prismarine_stairs")
        r.set(2, 7, zz, DP)
    r.set(2, 8, 0, "prismarine_wall")
    r.set(2, 9, 0, "lantern", **LANTERN)
    # the west driftwood post holds the wheel by a chain (the signature rope-and-chain), a lantern on its fence
    for y in range(1, 5):
        r.set(0, y, 0, POST, axis="y")
    r.set(0, 5, 0, SPOKE)
    r.set(0, 6, 0, "lantern", **LANTERN)
    for x in (0, 4):
        r.set(x, 4, 1, "chain", axis="z", **DRY)
    # the crane: the east back post rises as a mast, a jib reaches south over the chute, a chain lowers to it
    for y in range(1, 9):
        r.set(4, y, 0, POST, axis="y")
    for z in (1, 2, 3):
        r.set(4, 9, z, POST, axis="z")
    r.set(4, 9, 0, POST, axis="y")
    r.set(4, 10, 0, SPOKE)
    r.set(4, 11, 0, "lantern", **LANTERN)
    r.set(4, 8, 3, "chain", axis="y", **DRY)
    r.set(4, 7, 3, "chain", axis="y", **DRY)
    # the chute: a timber slide on fence legs, from under the crane's hook down to the salvage barrel on the kerb
    r.stair(4, 6, 3, "north", name="dark_oak_stairs")
    for y in (2, 3, 4, 5):
        r.set(4, y, 3, SPOKE)
    r.set(4, 2, 4, "barrel", facing="up", open="false")
    r.set(4, 3, 4, "raw_iron_block")          # the lifted ore, on the barrel
    # the kit's sea lanterns set flush in the kerb's front corners (a lamp post there stood in front of the ore core
    # or the wheel), dead coral on the kerb's west end
    r.set(0, 1, 4, "sea_lantern")
    r.set(4, 1, 4, "sea_lantern")
    r.set(0, 2, 3, "dead_tube_coral_fan", **DRY)


def _t2(r):
    """The T2 over the T1 (only adds or replaces): the Lighthouse Pump."""
    _t1(r)
    # a lantern post on the kerb, a lit doorway, a copper band round the house where the roof was (the stepped roof
    # is replaced by the tower's next stripes)
    r.set(0, 2, 4, "prismarine_wall")             # a thin lamp post on the west kerb corner
    r.set(0, 3, 4, "lantern", **LANTERN)
    r.set(1, 1, 0, "sea_lantern")                 # a lit door at the lighthouse's foot (west face)
    for x in (1, 2, 3):
        r.set(x, 6, 0, "waxed_cut_copper")
    r.set(3, 6, 1, "waxed_cut_copper")
    r.set(1, 6, 1, "waxed_cut_copper")
    # the lighthouse: two more stripes, a dark-prismarine gallery overhanging west with fence rails, a lamp room of
    # cyan glass round a sea lantern (the Lighthouse Battery's lamp), a stepped waxed-copper cap whose centre is the
    # waxed_copper_block tier marker, and a lightning-rod finial
    for y in (7, 8):
        for x in (1, 2, 3):
            for zz in (0, 1):
                r.set(x, y, zz, BAND[y])
    for x in range(4):
        for zz in range(3):
            if x in (1, 2, 3):
                r.set(x, 9, zz, DP)
            else:
                r.set(x, 9, zz, "dark_prismarine_slab", type="top", **DRY)
    for (x, zz) in [(0, 0), (0, 2)]:
        r.set(x, 10, zz, SPOKE)
    for y in (10, 11):
        for x in (1, 2, 3):
            for zz in (0, 1, 2):
                r.set(x, y, zz, "sea_lantern" if (x, zz) == (2, 1) else "cyan_stained_glass")
    for x in (1, 2, 3):
        for zz in (0, 1, 2):
            if (x, zz) == (2, 1):
                r.set(x, 12, zz, "waxed_copper_block")
            elif zz == 1:
                r.stair(x, 12, zz, "east" if x == 1 else "west", name="waxed_cut_copper_stairs")
            else:
                r.stair(x, 12, zz, "south" if zz == 0 else "north", name="waxed_cut_copper_stairs")
    r.set(2, 13, 1, "lightning_rod", facing="up", **ROD)


def _build(fn):
    """The rig canvas from gen_structures (settles stairs / walls / fences into their joined shapes)."""
    r = gs._SunRig(5, H, 5)
    fn(r)
    r.settle()
    return r


def t1():
    """T1 'Tide-Pool Pump': a walled tide pool over a glowing ore core, a driftwood waterwheel, a striped pump house, a
    mast crane and a chute to the salvage barrel. 5 x 14 x 5."""
    r = _build(_t1)
    r.check_support("tide T1")
    return r.structure()


def t2():
    """T2 'Lighthouse Pump': the T1 with the pump house risen into a striped lighthouse (gallery, cyan lamp room, waxed
    copper cap) and more sea lanterns. Every T1 block stays a block (check_mex_tiers). 5 x 14 x 5."""
    r = _build(_t2)
    r.check_support("tide T2")
    s = r.structure()
    gs.check_mex_tiers(t1(), s, "tidewrought")
    return s


if __name__ == "__main__":
    # python3 tools/mex_tide.py OUT_DIR -> OUT_DIR/mex_tide_t1.nbt, mex_tide_t2.nbt, prints the block counts
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for name, fn in (("t1", t1), ("t2", t2)):
        s = fn()
        s.write(os.path.join(out, "mex_tide_%s.nbt" % name))
        print(name, sum(1 for i in s.blocks.values() if s.palette[i][0] != "air"))
