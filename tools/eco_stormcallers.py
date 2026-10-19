#!/usr/bin/env python3
"""The Stormcallers economy set redrawn as one family: THE WIND-CHIME PAGODA (wind_generator_storm), THE LOTUS
CONDENSER (energy_converter_storm) and THE JADE TREASURY (storage_vault_storm).

lovish, Oct 19 2026: "I want the buildings to look like they match each other so when bases get bigger they look
cohesive and beautiful". Players build these three in rows behind the base, so they must read as a SET side by side
and each must still be recognisable at a glance: the generator is ENERGY (a tall pagoda whose top roof rides a spinning
vane crown, rods and chimes), the converter turns ENERGY INTO METAL (a lotus that catches the strike in its glowing
heart over a bed of raw gold), the vault is STORAGE (a squat, closed, double-roofed strongroom of stores with a gold
lock-boss). The old three were stone boxes with a lid (75 / 38 / 38 blocks): from the RTS camera red cubes under a
jade hat, the converter and the vault near twins.

THE STORMCALLERS STYLE KIT (the Pagoda of a Thousand Storms capitol, tools/capitol_jade_pagoda.py; the Jade Thunder
Spire extractor, tools/mex_storm.py; the Lotus Court academy, tools/lab_lotus_academy.py):
  - main blocks: JADE (waxed oxidized cut copper + stairs / slabs: waxed, so it never weathers mid-match), DARK OAK,
    RED LACQUER posts (stripped mangrove logs) and RED PAPER screens (red stained glass) lit from a glowing core;
    the podium is the court's stone (stone bricks, chiseled stone bricks, polished andesite);
  - trim: GOLD - the column capitals are the gold eave corners, the finials, the lock-boss;
  - light: PAPER LANTERNS and the PEARLESCENT glow behind the screens;
  - silhouette motif: CURVED PAGODA ROOFS - a jade stair lip on each side, a gold corner under an upturned jade slab tip;
  - signature detail: HANGING CHIMES - a chain with a glowing end-rod striker under eave corners; LIGHTNING RODS on top;
  - accent: AMETHYST (the storm-made crystal) and THE LOTUS (cherry-stair petals round a pearlescent heart).

ONE GRAMMAR FOR THE ROW (so three different buildings line up as a set): the same podium (chiseled corners, polished
andesite edges), the same ground storey (four red lacquer posts with the faction's paper / stores between them) and
the SAME FIRST EAVE at y 3 - in a row of mixed economy buildings the first jade roofs run as one continuous eave line,
like the verandah of one long temple. Above that line each building says what it is, and the heights step
(generator 14, converter 9, vault 8; 86, 52 and 52 blocks) so the row's skyline reads generator / converter / vault at a glance.

THE BUILDING STANDARD on a 3 x 3: three depths (the gold eave corners and chimes outside, the red posts and screens at
the wall plane, the lit core / lotus heart / stores set back); a broken top (stepped roofs, rods, crowns); mixed
materials (jade, red, dark oak, gold, stone, amethyst, cherry); dressing (chimes, crystals, lanterns on the roofs, as
the 3 x 3 has no yard); lights (the pearlescent cores, end-rod strikers, lanterns).

GAME RULES (asserted by check()):
  - footprint exactly 3 x 3 (BuildingPlacement and the bots pack them in rows); generator <= 16 high, the others <= 10;
    under 150 blocks each;
  - a full pad in layer 0 (the three classes declare no startingBlockTypes; the pad is still the foundation);
  - nothing pops off (_SunRig.check_support: lanterns on full blocks); no water, lava, fire, gravity blocks or redstone;
  - no pockets (tools/audit_structure_pockets.audit): the ground storey is closed on every side (no niche a unit can
    stand in), and nothing part-height sits on a walkable kerb;
  - the generator's moving part: WindGenerator.MOTIONS "_storm" is VANES round a vertical hub log over an empty ring
    (tools/check_sail_hubs.check_motion). The hub moves from (1, 8, 1) stripped_cherry_log to VANE_HUB below - a red
    lacquer (stripped mangrove, in HUB_BLOCKS) pillar between the second roof and the floating top roof, the pagoda's
    central pillar (shinbashira) turned by the wind; every other cell of its layer is air.

Usage: python3 tools/eco_stormcallers.py [OUT_DIR]   (writes the three .nbt files and runs every check)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
JADE, JADE_ST, JADE_SLAB = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"
OAK, OAK_LOG, OAK_ST, OAK_SLAB = "dark_oak_planks", "dark_oak_log", "dark_oak_stairs", "dark_oak_slab"
RED = "stripped_mangrove_log"      # red lacquer
PAPER = "red_stained_glass"        # paper screens
GOLD = "gold_block"
CORE = "pearlescent_froglight"
PETAL_ST, PETAL_SLAB = "cherry_stairs", "cherry_slab"

CORNERS = gs.CORNERS3
EDGES = gs.EDGES3
OUT = {(1, 0): "north", (0, 1): "west", (2, 1): "east", (1, 2): "south"}   # edge cell -> the face it looks out of
OPP = {"west": "east", "east": "west", "north": "south", "south": "north"}

EAVE_Y = 3                         # the shared first eave of the row
VANE_HUB = (1, 9, 1)               # WindGenerator.MOTIONS "_storm" (was (1, 8, 1))
VANE_LEN = 1.3
SPARK = (1.5, 14.0, 1.5)           # BuildingAmbienceClientEvents.ENERGY_SPOTS "_storm": the crown rod's tip
MAX_H = {"wind_generator_storm": 16, "energy_converter_storm": 10, "storage_vault_storm": 10}


class Rig(gs._SunRig):
    def __init__(self, sy):
        super().__init__(3, sy, 3)

    def red(self, x, y, z):
        self.set(x, y, z, RED, axis="y")

    def slab(self, x, y, z, name=JADE_SLAB, typ="bottom"):
        self.set(x, y, z, name, type=typ, **DRY)

    def rod(self, x, y, z, facing="up"):
        self.set(x, y, z, "lightning_rod", facing=facing, powered="false", **DRY)

    def lantern(self, x, y, z):
        self.set(x, y, z, "lantern", **LANTERN)

    def chime(self, x, y, z):
        """The kit's signature: a chain hung from the eave above y, an end-rod striker under it."""
        self.set(x, y, z, "chain", axis="y", **DRY)
        self.set(x, y - 1, z, "end_rod", facing="down")


# ======================================================================================================== the grammar
def podium(r, heart):
    """The court's stone: chiseled corners under the posts, polished andesite edges, the building's own heart."""
    for x in range(3):
        for z in range(3):
            r.set(x, 0, z, "chiseled_stone_bricks" if (x, z) in CORNERS else "polished_andesite")
    r.set(1, 0, 1, heart)


def posts(r, top=2):
    """Four red lacquer posts, y 1..top: the family's columns (their gold capitals are the eave's corners)."""
    for (x, z) in CORNERS:
        for y in range(1, top + 1):
            r.red(x, y, z)


def eave(r, y, tips=True, centre=JADE, corners=True):
    """The kit's curved roof on a 3 x 3: a jade stair lip on each side (low edge out), a gold corner under an upturned
    jade slab tip (the sweep), a jade (or other) centre."""
    for (x, z) in (CORNERS if corners else ()):
        r.set(x, y, z, GOLD)
        if tips:
            r.slab(x, y + 1, z)
    for (x, z), f in OUT.items():
        r.stair(x, y, z, OPP[f], name=JADE_ST)
    if centre:
        r.set(1, y, 1, centre)


# ======================================================================================================== generator
def _wind(r):
    # podium with a gold heart; the ground storey: paper screens on a dark-oak dado, lit by a pearlescent core
    podium(r, GOLD)
    posts(r)
    for (x, z), f in OUT.items():
        r.stair(x, 1, z, OPP[f], name=OAK_ST)    # a dark oak sill, its low step out: a second depth under the screen
        r.set(x, 2, z, PAPER)
    r.set(1, 1, 1, CORE, axis="y")
    r.set(1, 2, 1, CORE, axis="y")
    eave(r, EAVE_Y)
    # storey 2 (y 4..6): a red-and-paper plus; its open corners hold the chimes hung from the second roof
    for y in (4, 5, 6):
        r.red(1, y, 1)
        for (x, z) in EDGES:
            if y == 5:
                r.lantern(x, y, z)      # a paper lantern lit in each face's window, under the oak lintel
            else:
                r.set(x, y, z, OAK)
    for (x, z) in CORNERS:
        r.chime(x, 6, z)
    eave(r, 7)
    # the neck and the VANE HUB: a storm crystal (the kit's amethyst accent) shows in the gap the vanes sweep, a red
    # lacquer pillar alone in its layer is the hub they spin round
    r.set(1, 8, 1, "amethyst_block")
    r.red(*VANE_HUB)
    # the floating top roof, a ring narrower (a plus: no corners), carried by the hub alone; gold boss, the rod
    eave(r, 10, corners=False)
    r.set(1, 11, 1, GOLD)
    r.rod(1, 12, 1)
    r.rod(1, 13, 1)


# ======================================================================================================== converter
def _converter(r):
    # podium with a raw-gold heart: the metal the condenser makes, stacked behind the dado
    podium(r, "raw_gold_block")
    posts(r)
    for (x, z), f in OUT.items():
        r.set(x, 1, z, "raw_gold_block")
        r.set(x, 2, z, PAPER)
    r.set(1, 1, 1, "raw_gold_block")
    r.set(1, 2, 1, CORE, axis="y")
    eave(r, EAVE_Y)
    # THE LOTUS in the eave's lap, open to the sky: cherry petals (high back out: the cup opens up) round a glowing
    # heart, a gold seed head and the rod that takes the strike; four rods on the eave tips catch the storm too
    r.set(1, 4, 1, CORE, axis="y")
    for (x, z), f in OUT.items():
        r.stair(x, 4, z, f, name=PETAL_ST)
    for (x, z) in CORNERS:
        r.rod(x, 5, z)
    r.rod(1, 5, 1)
    r.rod(1, 6, 1)
    r.rod(1, 7, 1)


# ======================================================================================================== vault
def _vault(r):
    # the stores: two barrels stacked on every face between the red posts (barrels = storage in every faction's
    # vault), jade (emerald) treasure at the heart
    podium(r, "emerald_block")
    posts(r)
    for (x, z), f in OUT.items():
        r.set(x, 1, z, "barrel", facing=f, open="false")
        r.set(x, 2, z, "barrel", facing=f, open="false")
    r.set(1, 1, 1, "emerald_block")
    r.set(1, 2, 1, "emerald_block")
    eave(r, EAVE_Y, centre=OAK)
    # the strongroom's attic: a dark oak post carrying a narrower closed jade roof (a plus: one ring in), the kit's
    # chime strikers hung under each of its four lips, the gold lock-boss and a lantern on top
    r.set(1, 4, 1, OAK_LOG, axis="y")
    eave(r, 5, corners=False)
    for (x, z) in EDGES:
        r.set(x, 4, z, "end_rod", facing="down")
    r.set(1, 6, 1, GOLD)
    r.lantern(1, 7, 1)


# ======================================================================================================== checks
FORBIDDEN = ("water", "lava", "fire", "sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "tnt", "campfire", "candle", "_carpet", "sea_pickle")


def _build(fn, sy, name):
    r = Rig(sy)
    fn(r)
    r.bury_grass()
    r.settle()
    r.check_support(name)
    s = r.structure()
    check(s, name)
    return s


def solid(s):
    return {p: s.palette[i] for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}


def check(s, name):
    b = solid(s)
    xs, ys, zs = zip(*b)
    assert (min(xs), max(xs), min(zs), max(zs)) == (0, 2, 0, 2), "%s: footprint is not 3 x 3" % name
    assert max(ys) + 1 <= MAX_H[name], "%s: %d high" % (name, max(ys) + 1)
    assert len(b) < 150, "%s: %d blocks" % (name, len(b))
    assert all((x, 0, z) in b for x in range(3) for z in range(3)), "%s: the pad is not full" % name
    for p, (n, _) in b.items():
        assert not any(n == f or n.endswith(f) for f in FORBIDDEN), "%s: %s at %s" % (name, n, p)
        assert "grass_block" not in n


def check_vanes(s):
    """tools/check_sail_hubs.check_motion's VANES rule against this structure: an upright hub log, an empty ring."""
    import check_sail_hubs as csh
    b = solid(s)
    x, y, z = VANE_HUB
    n, p = b.get(VANE_HUB, ("air", {}))
    assert n in csh.HUB_BLOCKS and dict(p).get("axis") == "y", "vane hub %s is %s %s" % (VANE_HUB, n, p)
    ring = [(x + dx, y, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if dx or dz]
    assert not [c for c in ring if c in b], "the vanes would sweep through %s" % [c for c in ring if c in b]
    assert 0.8 <= VANE_LEN <= 1.6


def wind():
    """wind_generator_storm: THE WIND-CHIME PAGODA, 3 x 14 x 3."""
    s = _build(_wind, 14, "wind_generator_storm")
    check_vanes(s)
    return s


def converter():
    """energy_converter_storm: THE LOTUS CONDENSER, 3 x 9 x 3."""
    return _build(_converter, 9, "energy_converter_storm")


def vault():
    """storage_vault_storm: THE JADE TREASURY, 3 x 8 x 3."""
    return _build(_vault, 8, "storage_vault_storm")


BUILDERS = (("wind_generator_storm", wind), ("energy_converter_storm", converter), ("storage_vault_storm", vault))


if __name__ == "__main__":
    import audit_structure_pockets as asp
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/eco_storm"
    os.makedirs(out, exist_ok=True)
    bad = 0
    for name, fn in BUILDERS:
        s = fn()
        path = os.path.join(out, name + ".nbt")
        s.write(path)
        one_way, ground, roof, rooms, note = asp.audit(name, path)
        bad += len(one_way) + len(ground)
        print("%-24s %2d high %3d blocks  pockets: one-way %d ground %d roof %d%s"
              % (name, s.size[1] if hasattr(s, "size") else 0, len(solid(s)), len(one_way), len(ground), len(roof), note))
    assert not bad, "pockets"
