#!/usr/bin/env python3
"""Stormcallers metal extractor: THE JADE THUNDER SPIRE.

lovish (Oct 19 2026): "the metal extractors could be fountains with angels or something more creative, the current
metal extractor building looks really ugly". The old Stormcaller extractor was a squat red-post shrine under one jade
hip roof - a box with a lid from the RTS camera. This one tells the sky-mages' story (the Jade Pagoda, the Celestial
Observatory, Heaven's Bell): they do not dig, they call the storm down. A slim jade spire stands over the ore on four
thin dark-oak legs, its lightning rod is the tallest thing on the patch, and the rod's stroke runs down the spire's
heart (a second rod points DOWN out of the first eave into the ore) and has crystallised the ground: amethyst grows
round a glowing pearlescent core on a raw-gold seam, and the pad's floor is amethyst inside a prismarine kerb.

STYLE KIT (Stormcallers, shared by every building of the faction so a base reads as one place):
  main blocks: jade (waxed oxidized cut copper + its stairs / slabs), dark oak (logs, planks, fences), prismarine;
  accent: amethyst (the storm-made crystal);  trim: gold (the eave corners, the finial);
  light: paper lanterns (lanterns);  silhouette motif: curved pagoda roofs - jade stair rings with gold corners and
  upturned slab tips;  signature detail: hanging chimes - a chain with a glowing end rod as the striker.

THE BUILDING STANDARD on a 5 x 5 patch:
  1. three depths: the eave corners and chimes outside, the legs and crystals at the wall plane, the glowing core
     recessed in the open cage under the first roof (the RTS camera sees it between the legs - the shared "ore core"
     tell);
  2. a broken top: stepped eaves (5 wide, then 3, then a 3-wide cap) narrowing to a gold finial and a two-block rod,
     so the spire reads as a pagoda needle, not a pillar;
  3. mixed materials: jade, dark oak, gold, prismarine, amethyst - stairs on every eave edge, fences / chains / rods as
     the thin members that keep it airy;
  4. Stormcaller style: an eastern pagoda with upturned eaves and wind chimes;
  5. dressing: amethyst outcrops on the camera faces (west, south), a lantern post on a fence;
  6. tier 2 (the Storm Needle): a third roof tier raised on a dark-oak log with four more chimes under it and a taller
     rod; the spire's second storey gains a copper band (the waxed_copper_block tier marker: the game detects tier 2
     by it) and a copper heart under the middle roof; rods on all eight eave corners (the storm is caught everywhere);
     more chimes on the first eave, a second lantern post and another crystal outcrop;
  7. lights: the pearlescent core, the chime strikers (end rods) and the lanterns.

Hard rules kept: 5 x 5, 14 tall canvas for both tiers; no water, no gravity blocks; lanterns stand on fences or full
blocks (check_support); the core cage is open on the west and south so no unit is trapped
(tools/audit_structure_pockets.py: 0 pockets); check_mex_tiers asserts T1 has no tier marker and T2 keeps every T1 block
and holds the waxed copper. T1 129 blocks, T2 158.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs  # noqa: E402

H = 14
DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
JADE = "waxed_oxidized_cut_copper"
JADE_ST = "waxed_oxidized_cut_copper_stairs"
JADE_SLAB = "waxed_oxidized_cut_copper_slab"
OAK = "dark_oak_planks"
OAK_LOG = "dark_oak_log"
OAK_ST = "dark_oak_stairs"
OPP = {"west": "east", "east": "west", "north": "south", "south": "north"}
OUT = {(-1, 0): "west", (1, 0): "east", (0, -1): "north", (0, 1): "south"}


def rod(r, x, y, z, facing="up"):
    r.set(x, y, z, "lightning_rod", facing=facing, powered="false", **DRY)


def chime(r, x, y, z):
    """A hanging chime under an eave at y+1: a chain and a glowing end rod as the striker."""
    r.set(x, y, z, "chain", axis="y", **DRY)
    r.set(x, y - 1, z, "end_rod", facing="down")


def crystal(r, x, y, z, name="amethyst_cluster", facing="up"):
    r.set(x, y, z, name, facing=facing, **DRY)


def eave(r, y, lo, hi, tips=True):
    """A pagoda roof ring over [lo, hi]^2 at y: jade stairs climbing inward, gold corner blocks with an upturned tip."""
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if lo < x < hi and lo < z < hi:
                continue
            if x in (lo, hi) and z in (lo, hi):
                r.set(x, y, z, "gold_block")
                if tips:
                    r.set(x, y + 1, z, JADE_SLAB, type="bottom", **DRY)
                continue
            f = "east" if x == lo else "west" if x == hi else "south" if z == lo else "north"
            r.stair(x, y, z, f, name=JADE_ST)


def _t1(r):
    # the pad: the storm's glass - amethyst floor inside a prismarine kerb, raw gold (the ore) under the core
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            r.set(x, 0, z, "dark_prismarine" if corner else "prismarine_bricks" if edge else "amethyst_block")
    r.set(2, 0, 2, "raw_gold_block")
    # the cage: four thin dark-oak legs (fences, not logs: the core must show between them) under oak lintels
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        for y in (1, 2, 3):
            r.set(x, y, z, "dark_oak_fence")
    for (x, z) in [(2, 1), (1, 2), (3, 2), (2, 3)]:
        r.set(x, 3, z, "dark_oak_slab", type="top", **DRY)
    # the ore core: two pearlescent froglights, a rod pointing down into them (the strike's path), crystals grown
    # on its back sides so the camera faces stay open
    r.set(2, 1, 2, "pearlescent_froglight", axis="y")
    r.set(2, 2, 2, "pearlescent_froglight", axis="y")
    rod(r, 2, 3, 2, "down")
    r.set(2, 1, 1, "amethyst_block")
    r.set(3, 1, 2, "amethyst_block")
    crystal(r, 2, 2, 1, "amethyst_cluster")
    crystal(r, 3, 2, 2, "amethyst_cluster")
    # crystal outcrops and a lantern post on the camera faces
    r.set(0, 1, 1, "amethyst_block")
    crystal(r, 0, 2, 1, "large_amethyst_bud")
    r.set(3, 1, 4, "amethyst_block")
    crystal(r, 3, 2, 4, "amethyst_cluster")
    r.set(0, 1, 3, "dark_oak_fence")
    r.set(0, 2, 3, "lantern", **LANTERN)
    # the first pagoda roof, its four corners hung with chimes
    eave(r, 4, 0, 4)
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            r.set(x, 4, z, JADE)
    for (x, z) in gs.CORNERS5:
        chime(r, x, 3, z)
    # the spire's storey: a jade band then dark oak round a log heart, lanterns in the eave's lap, two more chimes
    for y in (5, 6):
        r.set(2, y, 2, OAK_LOG, axis="y")
        for (dx, dz) in OUT:
            r.set(2 + dx, y, 2 + dz, JADE if y == 5 else OAK)
    r.set(1, 5, 3, "lantern", **LANTERN)
    r.set(3, 5, 1, "lantern", **LANTERN)
    chime(r, 1, 6, 1)
    chime(r, 3, 6, 3)
    # the second roof, narrower: the stepping is the pagoda silhouette
    eave(r, 7, 1, 3)
    r.set(2, 7, 2, JADE)
    # the cap and finial: inward jade stairs, a gold knob and the two-block lightning rod - the tallest point
    r.set(2, 8, 2, OAK_LOG, axis="y")
    for (dx, dz), f in OUT.items():
        r.stair(2 + dx, 9, 2 + dz, OPP[f], name=JADE_ST)
    r.set(2, 9, 2, JADE)
    r.set(2, 10, 2, "gold_block")
    rod(r, 2, 11, 2)
    rod(r, 2, 12, 2)


def _t2(r):
    _t1(r)
    # the copper band and heart: the tier-2 marker set as ornament
    r.set(2, 8, 2, "waxed_copper_block")
    for (dx, dz) in OUT:
        r.set(2 + dx, 6, 2 + dz, "waxed_copper_block")
    # the third roof tier lifted on a log, chimes under it, a gold knob and a taller rod
    r.set(2, 11, 2, OAK_LOG, axis="y")
    for (dx, dz) in OUT:
        chime(r, 2 + dx, 11, 2 + dz)
    for (dx, dz), f in OUT.items():
        r.stair(2 + dx, 12, 2 + dz, OPP[f], name=JADE_ST)
    r.set(2, 12, 2, "gold_block")
    rod(r, 2, 13, 2)
    # rods on every eave corner and more chimes, a second lantern post, another crystal outcrop
    for (x, z) in gs.CORNERS5:
        rod(r, x, 6, z)
    for (x, z) in [(2, 0), (4, 2)]:
        chime(r, x, 3, z)
    r.set(1, 1, 4, "dark_oak_fence")
    r.set(1, 2, 4, "lantern", **LANTERN)
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        rod(r, x, 9, z)
    r.set(4, 1, 1, "amethyst_block")
    crystal(r, 4, 2, 1, "amethyst_cluster")


def _build(fn):
    r = gs._SunRig(5, H, 5)
    fn(r)
    r.settle()
    return r


def t1():
    r = _build(_t1)
    r.check_support("storm T1")
    return r.structure()


def t2():
    r = _build(_t2)
    r.check_support("storm T2")
    s = r.structure()
    gs.check_mex_tiers(t1(), s, "stormcallers")
    return s


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for name, fn in (("t1", t1), ("t2", t2)):
        s = fn()
        s.write(os.path.join(out, "mex_storm_%s.nbt" % name))
        print(name, sum(1 for i in s.blocks.values() if s.palette[i][0] != "air"))
