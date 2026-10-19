#!/usr/bin/env python3
"""The Gravebound (Death) ECONOMY SET - wind generator, energy converter, storage vault (variant "_dark") - redrawn as
one family with the Necropolis Cathedral (the capitol, tools/capitol_mausoleum.py), the Bone-Ribbed Well (the
extractor, tools/mex_grave.py) and the Ossuary Yard (the T1 lab, tools/lab_graveyard.py).

lovish (Oct 19 2026): "I want the buildings to look like they match each other so when bases get bigger they look
cohesive and beautiful". Players build these three in rows, so they must read as a SET side by side and each be
recognisable at a glance: generator = energy, converter = energy into metal, vault = storage.

THE GRAVEBOUND STYLE KIT (the same on every Gravebound building):
  - main blocks: deepslate (bricks / polished) and blackstone (polished bricks / chiseled / walls);
  - accent: BONE - bone blocks for the ribs, smooth-quartz stairs as the bone's rounded bends (the extractor's trick);
  - light: SOUL LANTERNS and a GREEN GLOW (verdant froglight behind green glass);
  - silhouette motif: SPIRES (the kit's spirelet: a thin blackstone-wall shaft with a soul lantern on top);
  - signature detail: CHAINS and SKULLS (skulls face the RTS camera, chains lashed across the ribs).

WHAT THE THREE SHARE (so a row reads as one set): the same grave-plot pad (blackstone corner stones, a polished
deepslate kerb) and the same chamfered blackstone plinth; a GREEN LANCET on each camera face (green glass over a
froglight heart - the extractor's green pit, the cathedral's rose windows; the vault's lancets sit over its barrels);
BONE RIBS with rounded quartz bends (the extractor's ribs) on the upper storey; spirelets with soul lanterns (the
generator and converter on the north-west and south-east corners - the two the RTS camera sees in profile - the vault
on its gable and ridge); skulls facing the camera; chains.

WHAT TELLS THEM APART (each recognisable at a glance in a row):
  - WIND GENERATOR -> THE SOUL-BELL OSSUARY (3 x 16 x 3): a tall bell tower. A lancet shaft with a green heart, an open
    belfry of four bone ribs that bend into pointed bone arches over the soul-bell (the drawn moving part:
    WindGenerator.MOTIONS "_dark", BELL), a glowing green lamp over the arches and a needle spire with a soul lantern.
    The tallest of the three: energy is the bell tolling and the green lamp burning.
  - ENERGY CONVERTER -> THE BONE CRUCIBLE (3 x 9 x 3): a font with ONE great bone arch over it (the extractor's back
    arch) and an iron cauldron hung from its keystone on a chain over a soul fire - soul fire (energy) under a pot of
    iron (metal) - soul fire on the arch's crown, an anvil (the metal made) on the corner nearest the camera, the
    furnace glowing green through its lancets. Open and mid-height: you see the fire and the pot under the arch.
  - STORAGE VAULT -> THE RELIQUARY CRYPT (3 x 10 x 3): a closed crypt - an iron door in a pointed portal on the south
    gable, barrels stacked behind green lancets, bone ribs clasping its corners, a steep tiled gable with a bone
    ridge, gable pinnacles with soul lanterns and a spirelet on the ridge. Closed and heavy: the one with no openings is the one that keeps things.

THE BUILDING STANDARD on a 3 x 3: depth from the plinth's chamfer, the corbelled cornices and the open belfry / cradle
behind the ribs; a broken top (spirelets, skulls on the rib tips, a needle); mixed materials per zone; dressing
(skulls, chains); light (soul lanterns, green glow).

PROTECTED BLOCKS AVOIDED (BiomePalette.PROTECTED - blocks other code identifies buildings by): the old take used full
deepslate_tiles (the Graveyard's overflow marker), blast furnaces (the Blacksmith's) and gilded blackstone (gold). None
of them is here - the kerb is polished deepslate; deepslate tile STAIRS / slabs / walls are other blocks.

RULES (asserted on every build): footprint 3 x 3; under 150 blocks; nothing pops off (_SunRig.check_support); no
water, lava, gravity blocks, redstone or protected blocks; no fire a unit can reach (the converter's soul fire sits
inside the cradle at y4, behind full blocks); the bell's cell empty under a solid keystone with its four sides clear
(check_sail_hubs.check_motion's BELL rule, run on the written nbt by __main__); no pockets
(audit_structure_pockets.audit, __main__). The three classes have no startingBlockTypes (STARTING_TYPES is empty) and
fixed buildTimeBlocks (WindGenerator 44, EnergyConverter 28, StorageVault 31) for every faction, so the extra blocks do
not slow construction.

Usage: python3 tools/eco_gravebound.py [OUT_DIR]   -> wind_generator_dark.nbt, energy_converter_dark.nbt,
storage_vault_dark.nbt with every check, the bell anchor check and the pocket audit.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

# WindGenerator.MOTIONS "_dark": the soul-bell swings in this empty cell, hung from the bone keystone over it
BELL = (1, 8, 1)
# BuildingAmbienceClientEvents.ENERGY_SPOTS "_dark": soul flames over the two spirelet lanterns and the needle's lantern
# (the same convention as the old spots: a lantern at block y -> y + 1.0)
SOUL_SPOTS = ((0.5, 12.0, 0.5), (2.5, 12.0, 2.5), (1.5, 16.0, 1.5))
STARTING_TYPES = set()          # WindGenerator / EnergyConverter / StorageVault declare none

DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
SKULL = dict(rotation=str(gs.TO_CAMERA))
DB, CDB, PD = "deepslate_bricks", "cracked_deepslate_bricks", "polished_deepslate"
PBB, CPB, PBW = "polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone_wall"
DBS, DTS, PBBS = "deepslate_brick_stairs", "deepslate_tile_stairs", "polished_blackstone_brick_stairs"
BONE, BEND = "bone_block", "smooth_quartz_stairs"
GLASS, GLOW = "green_stained_glass", "verdant_froglight"

CORNERS = gs.CORNERS3
EDGES = gs.EDGES3
INWARD = {(1, 0): "south", (0, 1): "east", (2, 1): "west", (1, 2): "north"}   # an edge cell -> facing the middle
CAMERA_EDGES = ((0, 1), (1, 2))          # the west and south faces: what the RTS camera sees
BACK_EDGES = ((1, 0), (2, 1))
SPIRE_CORNERS = ((0, 0), (2, 2))         # seen in profile from the south-west: the spirelets stand here
NEAR, FAR = (0, 2), (2, 0)               # the corner nearest the camera (a skull) and the one hidden behind

FORBIDDEN = {"deepslate_tiles", "chiseled_deepslate", "blast_furnace", "gilded_blackstone", "copper_block",
             "cut_copper", "waxed_copper_block", "chest", "iron_block", "gold_block", "water", "lava", "fire",
             "soul_fire", "sand", "gravel", "redstone_wire", "redstone_block"}


def _chain(axis):
    return dict(axis=axis, waterlogged="false")


# ---- the family's shared parts -------------------------------------------------------------------------------------

def pad(r, centre=PD):
    """The grave-plot pad every Gravebound 3x3 stands on (the extractor's plot, scaled): blackstone corner stones and a
    polished-deepslate kerb (not deepslate tiles: those are the Graveyard's protected overflow marker)."""
    for x, z in CORNERS:
        r.set(x, 0, z, PBB)
    for x, z in EDGES:
        r.set(x, 0, z, PD)
    r.set(1, 0, 1, centre)


def plinth(r, edges=EDGES, centre=PBB):
    """y1: the chamfered blackstone plinth - corner stones, stairs stepping down outward on the edges (a moulded base
    from outside, and a half step a unit simply walks past: the storey above is full blocks)."""
    for x, z in CORNERS:
        r.set(x, 1, z, "cracked_polished_blackstone_bricks" if (x, z) == FAR else PBB)
    for x, z in edges:
        r.stair(x, 1, z, INWARD[(x, z)], name=PBBS)
    if centre:
        r.set(1, 1, 1, centre)


def lancets(r, y0, y1, faces=CAMERA_EDGES, back=DB):
    """The green lancet: green glass on the camera faces over a froglight heart in the middle (the kit's green glow,
    the cathedral's rose windows in miniature), plain brick on the back faces; chiseled / brick corner piers."""
    for y in range(y0, y1 + 1):
        for x, z in CORNERS:
            r.set(x, y, z, CPB if (y - y0) % 2 == 0 else PBB)
        for e in EDGES:
            r.set(e[0], y, e[1], GLASS if e in faces else (back if y % 2 == 0 else CDB))
        r.set(1, y, 1, GLOW, axis="y")


def pointed_heads(r, y, centre=DB):
    """A band of upside-down brick stairs over the lancets - the pointed heads, and a corbel the storey above sits on."""
    for x, z in CORNERS:
        r.set(x, y, z, CPB)
    for x, z in EDGES:
        r.stair(x, y, z, INWARD[(x, z)], "top", name=DBS)
    if centre:
        r.set(1, y, 1, centre)


def spirelet(r, x, z, y0, top):
    """The kit's spirelet: a blackstone-wall shaft from y0 to top - 1, a soul lantern on top."""
    for y in range(y0, top):
        r.set(x, y, z, PBW)
    r.set(x, top, z, "soul_lantern", **LANTERN)


# ---- the wind generator: the Soul-Bell Ossuary ---------------------------------------------------------------------

def _wind(r):
    pad(r)
    plinth(r)
    lancets(r, 2, 3)
    pointed_heads(r, 4)
    # y5 the belfry floor: the ribs' roots on the corners, a green light in the middle that lights the bell from below
    for x, z in CORNERS:
        r.set(x, 5, z, BONE, axis="y")
    for x, z in EDGES:
        r.set(x, 5, z, PD)
    r.set(1, 5, 1, GLOW, axis="y")
    # y6-8 the open belfry: four bone ribs on the corners; on the camera's sills two skulls, on the back ones iron
    # railings and a chain lashed across (the kit's chains). The bell's cell (1, 8, 1) and its four sides stay air.
    for y in (6, 7):
        for x, z in CORNERS:
            r.set(x, y, z, BONE, axis="y")
    r.set(0, 6, 1, "skeleton_skull", **SKULL)
    r.set(1, 6, 2, "wither_skeleton_skull", **SKULL)
    r.set(1, 6, 0, "iron_bars")
    r.set(2, 6, 1, "iron_bars")
    for x, z in EDGES:                       # chains lashed across all four openings, under the bell's cell
        r.set(x, 7, z, "chain", **_chain("x" if z in (0, 2) else "z"))
    # y8 the ribs bend in (rounded quartz shoulders turned toward the openings' arches)
    for x, z in CORNERS:
        r.stair(x, 8, z, "east" if x == 0 else "west", name=BEND)
    # y9 the pointed bone arches over the four openings and the keystone the bell hangs from
    for x, z in EDGES:
        r.stair(x, 9, z, INWARD[(x, z)], "top", name=BEND)
    for x, z in CORNERS:
        r.set(x, 9, z, BONE, axis="y")
    r.set(1, 9, 1, BONE, axis="y")
    # y10 the rib tips: spirelets on the profile corners, skulls on the other two; a tiled hip round the lamp's plinth
    for x, z in EDGES:
        r.stair(x, 10, z, INWARD[(x, z)], name=DTS)
    r.set(1, 10, 1, CPB)
    for x, z in SPIRE_CORNERS:
        spirelet(r, x, z, 10, 11)
    r.set(NEAR[0], 10, NEAR[1], "wither_skeleton_skull", **SKULL)
    r.set(FAR[0], 10, FAR[1], "skeleton_skull", **SKULL)
    # y11 the green lamp - the soul the bell tolls for, the energy - then the needle: a chiseled die, a tile-wall and a
    # blackstone-wall shaft, a soul lantern at the tip (the base's tallest small light)
    r.set(1, 11, 1, GLOW, axis="y")
    r.set(1, 12, 1, CPB)
    r.set(1, 13, 1, "deepslate_tile_wall")
    r.set(1, 14, 1, PBW)
    r.set(1, 15, 1, "soul_lantern", **LANTERN)


# ---- the energy converter: the Bone Crucible -----------------------------------------------------------------------
# Its own silhouette in a row: low and open - a font with ONE great bone arch over it (the extractor's back arch, not the
# generator's four-rib belfry), an iron cauldron hung from the arch's keystone on a chain over a soul fire.

def _converter(r):
    pad(r)
    plinth(r)
    lancets(r, 2, 2)
    # y3 the crucible's font: a corbelled band (pointed heads over the lancets) round the soul-soil fire bed
    pointed_heads(r, 3, centre="soul_soil")
    # y4 the font: the arch's bone legs rise from the west and east rims, a low blackstone lip on the north and south,
    # the soul fire burning in the middle (the energy); spirelets on the profile corners, skulls on the other two
    r.set(0, 4, 1, BONE, axis="y")
    r.set(2, 4, 1, BONE, axis="y")
    for x, z in ((1, 0), (1, 2)):
        r.set(x, 4, z, "polished_blackstone_brick_slab", type="bottom", **DRY)
    r.set(1, 4, 1, "soul_campfire", lit="true", facing="south", signal_fire="false", **DRY)
    for x, z in SPIRE_CORNERS:
        spirelet(r, x, z, 4, 6)
    r.set(NEAR[0], 4, NEAR[1], "anvil", facing="west")        # the metal it makes, on the corner nearest the camera
    r.set(FAR[0], 4, FAR[1], "wither_skeleton_skull", **SKULL)
    # y5 the pot of iron over the fire (energy into metal); chains lashed from the legs to the spirelets
    r.set(0, 5, 1, BONE, axis="y")
    r.set(2, 5, 1, BONE, axis="y")
    r.set(1, 5, 1, "cauldron")
    # y6 the legs climb; the pot hangs on a chain from the keystone
    r.set(0, 6, 1, BONE, axis="y")
    r.set(2, 6, 1, BONE, axis="y")
    r.set(1, 6, 1, "chain", **_chain("y"))
    # y7 the arch: rounded bone shoulders bending in, a bone keystone across; y8 soul fire on its crown (the extractor's
    # crown fire - the set's sign that soul energy burns here)
    r.stair(0, 7, 1, "east", name=BEND)
    r.stair(2, 7, 1, "west", name=BEND)
    r.set(1, 7, 1, BONE, axis="x")
    r.set(1, 8, 1, "soul_campfire", lit="true", facing="south", signal_fire="false", **DRY)


# ---- the storage vault: the Reliquary Crypt ------------------------------------------------------------------------
# Its own silhouette in a row: closed and heavy - a strongroom clasped by bone ribs at its corners, a steep tiled gable
# with a bone ridge, the iron door on the south gable end (a camera face).

def _vault(r):
    pad(r)
    # y1 the plinth, chamfered like the set's; the iron door in the south face
    plinth(r, edges=((0, 1), (1, 0), (2, 1)), centre=BONE)
    r.set(1, 1, 2, "iron_door", facing="south", half="lower", hinge="left", open="false", powered="false")
    r.set(1, 2, 2, "iron_door", facing="south", half="upper", hinge="left", open="false", powered="false")
    # y2-3 bone ribs clasp the corners (the ribcage holding the hoard); barrels - the goods - on the west, north and
    # east faces under green lancets round a froglight heart; the door's pointed head
    for y in (2, 3):
        for x, z in CORNERS:
            r.set(x, y, z, BONE, axis="y")
    for (x, z), f in (((0, 1), "west"), ((1, 0), "north"), ((2, 1), "east")):
        r.set(x, 2, z, "barrel", facing=f, open="false")
        r.set(x, 3, z, GLASS)
    r.set(1, 2, 1, BONE, axis="x")
    r.stair(1, 3, 2, "north", "top", name=DBS)
    r.set(1, 3, 1, GLOW, axis="y")
    # y4 the cornice: chiseled capitals on the ribs, corbels between, a chiseled niche over the door
    pointed_heads(r, 4)
    r.set(1, 4, 2, CPB)
    # y5 the steep tiled gable (ridge along z), the front's chiseled apex; y6 the bone ridge
    for z in (0, 1, 2):
        if z < 2:
            r.stair(0, 5, z, "east", name=DTS)
            r.stair(2, 5, z, "west", name=DTS)
        r.set(1, 5, z, CPB if z == 2 else DB)
        r.set(1, 6, z, BONE, axis="z")
    # the front gable between two pinnacles with soul lanterns (a church front, the cathedral's west front in small)
    for x in (0, 2):
        r.set(x, 5, 2, PBW)
        r.set(x, 6, 2, "soul_lantern", **LANTERN)
    # y7 a wither skull on the ridge's front end; a spirelet on its back end
    r.set(1, 7, 2, "wither_skeleton_skull", **SKULL)
    spirelet(r, 1, 0, 7, 9)


# ---- building and checking -----------------------------------------------------------------------------------------

def _build(fn, h, tag):
    r = gs._SunRig(3, h, 3)
    fn(r)
    r.settle()
    r.check_support("gravebound " + tag)
    s = r.structure()
    check(s, tag)
    return s


def check(s, tag):
    assert s.size[0] == 3 and s.size[2] == 3, f"{tag}: footprint must stay 3 x 3"
    names = [s.palette[i][0] for i in s.blocks.values() if s.palette[i][0] != "air"]
    assert len(names) < 150, f"{tag}: {len(names)} blocks (keep under 150)"
    bad = sorted(set(names) & FORBIDDEN)
    assert not bad, f"{tag}: forbidden / protected blocks {bad}"
    layer0 = {(x, z) for (x, y, z), i in s.blocks.items() if y == 0 and s.palette[i][0] != "air"}
    assert len(layer0) == 9, f"{tag}: the pad must be full"
    for (x, y, z), i in s.blocks.items():
        n = s.palette[i][0]
        assert n not in STARTING_TYPES or y == 0, f"{tag}: starting type {n} above layer 0"
        if "campfire" in n:     # soul fire only where no unit can stand: inside the cradle, above full blocks
            assert y >= 3, f"{tag}: campfire at {(x, y, z)} is within reach"


def check_bell(s):
    """WindGenerator.MOTIONS "_dark" BELL rule (check_sail_hubs.check_motion / every_moving_part_sits_on_its_anchor):
    the cell empty, a solid block above, nothing on its four sides."""
    solid = {p for p, i in s.blocks.items() if s.palette[i][0] not in ("air", "cave_air")}
    x, y, z = BELL
    assert BELL not in solid, "the soul-bell's cell is taken"
    assert (x, y + 1, z) in solid, "nothing to hang the soul-bell from"
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        assert (x + dx, y, z + dz) not in solid, f"the soul-bell would swing into {(x + dx, y, z + dz)}"


def wind():
    """wind_generator_dark - the Soul-Bell Ossuary: a green-lanterned shaft, an open bone-ribbed belfry round the
    drawn soul-bell (BELL at (1, 8, 1)), a green lamp and a needle spire. 3 x 16 x 3."""
    s = _build(_wind, 16, "wind_generator_dark")
    check_bell(s)
    return s


def converter():
    """energy_converter_dark - the Bone Crucible: a green-glowing font, a soul fire under an iron cauldron hung from a
    great bone arch with soul fire on its crown, spirelets, an anvil and a skull on the corners. 3 x 9 x 3."""
    return _build(_converter, 9, "energy_converter_dark")


def vault():
    """storage_vault_dark - the Reliquary Crypt: an iron door in a pointed portal between gable pinnacles, barrels
    under green lancets, bone ribs clasping the corners, a steep tiled gable with a bone ridge and a spirelet. 3 x 10 x 3."""
    return _build(_vault, 10, "storage_vault_dark")


if __name__ == "__main__":
    import tempfile
    import audit_structure_pockets as asp
    out = sys.argv[1] if len(sys.argv) > 1 else tempfile.mkdtemp()
    for name, fn in (("wind_generator_dark", wind), ("energy_converter_dark", converter),
                     ("storage_vault_dark", vault)):
        s = fn()
        path = os.path.join(out, name + ".nbt")
        s.write(path)
        one_way, ground, roof, rooms, note = asp.audit(name, path)
        assert not one_way and not ground and "TRAPPED" not in note, (name, one_way, ground, note)
        n = sum(1 for i in s.blocks.values() if s.palette[i][0] != "air")
        h = max(y for (x, y, z), i in s.blocks.items() if s.palette[i][0] != "air") + 1
        print(f"{name}: 3 x {h} x 3, {n} blocks, no pockets{note}")
