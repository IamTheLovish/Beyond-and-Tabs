#!/usr/bin/env python3
"""The Tidewrought economy set, redrawn as one family with the Admiral's Beacon (the capitol,
tools/capitol_wreck_harbour.py), the Tide-Pool Pump (the extractor, tools/mex_tide.py) and the Slip (the T1 lab,
tools/lab_slipway.py):

  wind_generator_tide   THE TIDE-MILL LIGHT  - a striped lighthouse tower on a driftwood mill house; the waterwheel
                                               turns on its west face and lights the lamp room on top (energy)
  energy_converter_tide THE SALVAGE SMELTER  - a striped bottle kiln: two glowing furnace mouths, the stored light
                                               behind blue glass, teal smoke out of the neck and a block of cast iron
                                               on a barrel at its foot (energy into metal)
  storage_vault_tide    THE HULL STORE       - a striped strongroom of barrels under an old hull turned keel-up for a
                                               roof, the way shore folk store gear under a beached boat (storage)

lovish, Oct 19 2026: "I want the buildings to look like they match each other so when bases get bigger they look
cohesive and beautiful". Players build these three in rows, so side by side they must read as a set and each must be
told apart at a glance: a TALL tower with a wheel (energy), a MID house with fire and smoke (conversion), a LOW broad
hull (storage). The old ones (gen_structures.tide_wind_generator / tide_energy_converter / tide_storage_vault) were
red-post boxes with spruce, copper and wool that shared nothing with the revamped capitol, extractor and lab.

THE TIDEWROUGHT STYLE KIT (the capitol's, the extractor's and the slipway's):
  - main blocks: PRISMARINE (bricks / dark), SANDSTONE (smooth / cut / chiseled) and DRIFTWOOD (stripped dark oak and
    stripped mangrove logs, dark oak and mangrove planks);
  - accent and light: SEA LANTERNS (the lamp room, the portholes, the smelter's core, the store's door), lanterns
    on posts;
  - silhouette motif: the STRIPED LIGHTHOUSE TOWER - two-row bands of cut sandstone and prismarine bricks over a dark
    prismarine foot (mex_tide.BAND / lab_slipway.BAND): the generator IS one, the kiln's neck and the store's walls
    carry the same bands;
  - signature detail: weathered ROPE and CHAIN (the generator's lamp chains, the kiln's rope lamp post, the hull's
    rope lashing posts);
  - the fleet's colours: light-blue banners and blue glass (the striped sailcloth stays on the capitol's and the
    slipway's masts: a 3 x 3 has no room for a mast and a sail would hide the wheel);
  - the pad: the extractor's and the slipway's - a prismarine-brick kerb with chiseled sandstone corners round smooth
    sandstone, so a row of them reads as one quay.

THE BUILDING STANDARD on a 3 x 3 (the RTS camera looks from the south-west: the west face x = 0, the south face z = 2
and the tops; every show is put there):
  1. three depths: the corner posts (driftwood logs standing proud in colour), the banded wall, and recesses made
     with upside-down stairs (corbels), glass and the hull's lashing posts; the wheel and the lamp chains stand
     off the faces;
  2. a broken top: the lamp room, cap and vane; the kiln's shoulders, neck and smoke; the hull's keel
     with its stem and sternpost curling up;
  3. mixed materials: 3-4 blocks per zone, stairs on every edge, fences / walls / chains as the thin members;
  4-5. the faction's harbour architecture, dressed with rope, chain, barrels, banners and sea lanterns;
  7. lights: sea lanterns (lamp room, portholes, the smelter's core, the store's door), lanterns on the posts, the
     furnace mouths.

HARD RULES (asserted in check()): footprint exactly 3 x 3; a full layer 0 (the instant foundation; the three classes
declare no startingBlockTypes, so the whole pad is the foundation); nothing pops off (_SunRig.check_support plus
dead coral); no pockets and nothing a unit can stand in (tools/audit_structure_pockets.py audit()); no water, lava,
fire where units walk, gravity blocks or redstone; under 150 blocks; the generator's WHEEL anchor sits on a
horizontal x-axis hub log with nothing west of it and a radius that clears the ground (tools/check_sail_hubs.py
check_motion, run against the new nbt); no grass (bury_grass is called anyway, per the lab brief).

WATER: none. A waterwheel wants a race, but the wheel turns OUTSIDE the west face (WindmillRenderClientEvents draws
it just west of the axle), so a source under it would be open to the west and flow; a source inside the 3 x 3 would
have to be walled on every side and capped by a rope boom (the slipway's trick) - a cell the camera barely sees.
The race is drawn in stone instead (a dark prismarine sill under the wheel) and BuildingAmbienceClientEvents already
throws SPLASH particles there.

Usage: python3 tools/eco_tidewrought.py [OUT_DIR]   (writes wind_generator_tide.nbt, energy_converter_tide.nbt and
       storage_vault_tide.nbt and checks them; default OUT_DIR: the scratch folder - shipping is the coordinator's step)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures as gs   # noqa: E402

DRY = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
DP, PR, PB = "dark_prismarine", "prismarine", "prismarine_bricks"
SS, CS, CHS = "smooth_sandstone", "cut_sandstone", "chiseled_sandstone"
DOP, MAP = "dark_oak_planks", "mangrove_planks"
SDL, SML = "stripped_dark_oak_log", "stripped_mangrove_log"
ROPE = "dark_oak_fence"
LAMP = "sea_lantern"
# the lighthouse stripes (mex_tide.BAND, lab_slipway.BAND): two rows each over a dark prismarine foot
BAND = {1: DP, 2: CS, 3: CS, 4: PB, 5: PB, 6: CS, 7: CS, 8: PB, 9: PB, 10: CS, 11: CS}

# WindGenerator.MOTIONS "_tide": the waterwheel's axle (structure coordinates), the face it turns outside, its radius.
# The axle rose from (0, 1, 1) to (0, 2, 1): the wheel (radius 1.4, z 0.1-2.9) now spans y 1.1-3.9 - clear of the
# ground with room to spare and centred on the mill house's west face instead of scraping the pad.
WHEEL_AXLE = (0, 2, 1)
WHEEL_OUT = "WEST"
WHEEL_RADIUS = 1.4

CORNERS = [(0, 0), (2, 0), (0, 2), (2, 2)]
EDGES = [(1, 0), (0, 1), (2, 1), (1, 2)]
INWARD = {(1, 0): "south", (0, 1): "east", (2, 1): "west", (1, 2): "north"}     # an edge cell's face toward the centre
OUTWARD = {k: {"south": "north", "north": "south", "east": "west", "west": "east"}[v] for k, v in INWARD.items()}


class Rig(gs._SunRig):
    def __init__(self, sy):
        super().__init__(3, sy, 3)

    def st(self, x, y, z, facing, half="bottom", name="dark_prismarine_stairs"):
        self.stair(x, y, z, facing, half, name)

    def slab(self, x, y, z, name, typ="bottom"):
        self.set(x, y, z, name, type=typ, **DRY)

    def log(self, x, y, z, name=SDL, axis="y"):
        self.set(x, y, z, name, axis=axis)

    def lantern(self, x, y, z):
        self.set(x, y, z, "lantern", **LANTERN)

    def chain(self, x, y, z, axis="y"):
        self.set(x, y, z, "chain", axis=axis, **DRY)


def pad(r):
    """Layer 0, the instant foundation: the extractor's and the slipway's pad - prismarine-brick kerb, chiseled
    sandstone corners, smooth sandstone in the middle - so a row of economy buildings reads as one quay."""
    for x in range(3):
        for z in range(3):
            corner = (x, z) in CORNERS
            r.set(x, 0, z, CHS if corner else SS if (x, z) == (1, 1) else PB)


# ======================================================================================================== generator
def draw_wind(r):
    """THE TIDE-MILL LIGHT. A driftwood-framed mill house (dark-oak corner posts with lanterns on their heads, the
    banded stone between them) carries a striped lighthouse shaft that steps in to a plus (so it reads as a slim round
    tower, not a 3 x 3 pillar), a corbelled gallery, the lamp room of blue glass round a sea lantern and a dark
    prismarine cap with a copper vane: the faction's silhouette motif, and the tallest of the three so a row of
    generators reads as a line of lights. The waterwheel (drawn by WindmillRenderClientEvents) turns on the west face
    round the axle at WHEEL_AXLE, over a dark prismarine race sill."""
    pad(r)
    r.set(0, 0, 1, DP)                                    # the race sill under the wheel
    # the mill house, y1-3: dark-oak posts, banded stone faces, a solid core (no closed air a unit could land in)
    for y in range(1, 4):
        for x in range(3):
            for z in range(3):
                r.log(x, y, z, SDL) if (x, z) in CORNERS else r.set(x, y, z, BAND[y])
    r.log(*WHEEL_AXLE, name=SDL, axis="x")               # <- the waterwheel's axle (WindGenerator.MOTIONS "_tide")
    r.set(1, 2, 2, LAMP)                                  # a lit porthole on the south face
    for (x, z) in CORNERS:
        r.lantern(x, 4, z)                                # the mill's lamps on the post heads
    # the lamp chains: from three gallery corners down to the post lanterns (the kit's chain, thin members that
    # break the shaft's outline); the back corner is left open
    for (x, z) in ((0, 0), (0, 2), (2, 2)):
        for y in range(5, 10):
            r.chain(x, y, z)
    # the shaft y4-9: a plus of the lighthouse bands, slit windows to the camera with the lamp core behind
    shaft = {4: CS, 5: CS, 6: PB, 7: PB, 8: CS, 9: CS}
    for y in range(4, 10):
        for (x, z) in EDGES + [(1, 1)]:
            r.set(x, y, z, shaft[y])
    r.set(0, 7, 1, "light_blue_stained_glass")
    r.set(1, 7, 2, "light_blue_stained_glass")
    r.set(1, 7, 1, LAMP)
    # the gallery y10: corbels (upside-down stairs) on the corners over the stepped-in shaft, a rope rail on y11
    for (x, z) in CORNERS:
        r.st(x, 10, z, "east" if x == 0 else "west", "top")
    for (x, z) in EDGES + [(1, 1)]:
        r.set(x, 10, z, DP)
    # the lamp room y11-12: prismarine wall posts on the corners, blue glass panes on the faces, a sea lantern core
    for y in (11, 12):
        for x in range(3):
            for z in range(3):
                if (x, z) == (1, 1):
                    r.set(x, y, z, LAMP)
                elif (x, z) in CORNERS:
                    r.set(x, y, z, "prismarine_wall")
                else:
                    r.set(x, y, z, "light_blue_stained_glass_pane")
    # the cap: a ring of stairs, a dark prismarine crown, the vane
    for (x, z) in EDGES + CORNERS:
        r.st(x, 13, z, INWARD.get((x, z)) or ("east" if x == 0 else "west"))
    r.set(1, 13, 1, DP)
    r.set(1, 14, 1, "prismarine_wall")
    r.set(1, 15, 1, "lightning_rod", facing="up", powered="false", **DRY)


# ======================================================================================================== converter
def draw_converter(r):
    """THE SALVAGE SMELTER (energy into metal): a BOTTLE KILN, the shape every shore smelter and glassworks has - a
    broad hearth that shoulders in to a neck - so beside the slim generator tower and the low hull store it reads at a
    glance as the furnace. Its hearth stands on dark-oak posts with two lit blast furnaces glowing out of the camera
    faces (west and south); blue glass above them (the lamp room's) shows the stored light (a sea-lantern core) -
    the energy going in;
    a block of cast iron stands on a barrel at the front corner - the metal coming out. The bottle is banded like the
    lighthouse (cut sandstone and prismarine over a dark prismarine foot), its shoulders are stairs, its lip a ring of
    dark prismarine walls, and a soul campfire breathes teal smoke out of the neck. A lantern on a rope post marks it
    at night."""
    pad(r)
    for (x, z) in CORNERS[:3]:
        for y in (1, 2):
            r.log(x, y, z, SDL)
    r.set(2, 1, 2, "barrel", facing="up", open="false")
    r.set(2, 2, 2, "iron_block")                          # the cast metal, out of the kiln
    r.set(0, 1, 1, "blast_furnace", facing="west", lit="true")
    r.set(1, 1, 2, "blast_furnace", facing="south", lit="true")
    for (x, z) in ((1, 0), (2, 1), (1, 1)):
        r.set(x, 1, z, DP)
    for (x, z) in EDGES:
        r.set(x, 2, z, "light_blue_stained_glass_pane")   # the lamp room's blue glass
    r.set(1, 2, 1, LAMP)                                  # the stored light, glowing through it
    # the bottle's foot band y3: cut sandstone corners, corbelled faces
    for (x, z) in EDGES:
        r.st(x, 3, z, INWARD[(x, z)], "top", "prismarine_brick_stairs")
    for (x, z) in CORNERS:
        r.set(x, 3, z, CS)
    r.set(1, 3, 1, PB)
    # the bottle: y4 a plus of prismarine bricks with its first shoulder on the corners, y5 the second shoulder on
    # the faces - the shoulders in dark prismarine stairs (prismarine-brick stairs read as a noisy lump at RTS scale;
    # the dark ones draw the taper as one clean slope, like the generator's cap) - then a banded neck to the smoke
    for (x, z) in EDGES + [(1, 1)]:
        r.set(x, 4, z, PB)
    for (x, z) in CORNERS:
        if (x, z) != (2, 2):
            r.st(x, 4, z, "east" if x == 0 else "west")
    for (x, z) in EDGES:
        r.st(x, 5, z, INWARD[(x, z)])
    r.set(1, 5, 1, DP)
    for y, blk in ((6, CS), (7, CS), (8, DP)):
        r.set(1, y, 1, blk)
    r.set(1, 9, 1, "soul_campfire", lit="true", facing="north", signal_fire="false", **DRY)
    # a lantern on a rope post over the output corner
    r.set(2, 4, 2, ROPE)
    r.lantern(2, 5, 2)


# ============================================================================================================ vault
def draw_vault(r):
    """THE HULL STORE (storage). A strongroom on dark-oak posts in the lighthouse's bands (a dark prismarine foot, cut
    sandstone) with barrels set in its faces and a sea lantern over the door; over it an old hull turned keel-up for a
    roof, the way shore folk keep their gear dry under a beached boat - a dark wale, red mangrove planking sloping up
    to a dark-oak keel that dips at the bow (west, the camera's end) and carries the fleet's light-blue banner at the
    stern. Low and long: beside the tower and the kiln it reads as the store."""
    pad(r)
    for (x, z) in CORNERS:
        r.set(x, 1, z, DP)
        r.set(x, 2, z, ROPE)                              # the hull's lashing posts
    for (x, z) in EDGES:
        r.set(x, 1, z, "barrel", facing=OUTWARD[(x, z)], open="false")
        r.set(x, 2, z, CS)
    r.set(1, 1, 1, "dried_kelp_block")
    r.set(1, 2, 1, "barrel", facing="up", open="false")
    r.set(1, 2, 2, LAMP)
    # the hull keel-up, along x: the wale (y3), the planking sloping up (y4), the keel (y5)
    for x in range(3):
        r.set(x, 3, 0, DOP)
        r.set(x, 3, 2, DOP)
        r.set(x, 3, 1, MAP)
        r.st(x, 4, 0, "south", "bottom", "mangrove_stairs")
        r.st(x, 4, 2, "north", "bottom", "mangrove_stairs")
        r.set(x, 4, 1, MAP)
    r.st(0, 5, 1, "east", "bottom", "dark_oak_stairs")    # the keel dipping to the bow
    r.log(1, 5, 1, SDL, "x")
    r.log(2, 5, 1, SDL, "x")
    r.set(2, 6, 1, "light_blue_banner", rotation=str(gs.TO_CAMERA))


DRAW = {"wind_generator_tide": draw_wind, "energy_converter_tide": draw_converter, "storage_vault_tide": draw_vault}


def _build(name):
    r = Rig(17)
    DRAW[name](r)
    r.bury_grass()
    r.settle()
    r.check_support(name)
    top = max(y for (x, y, z), (n, p) in r.b.items() if n != "air")
    s = gs.Structure(3, top + 1, 3)
    for (x, y, z), (n, p) in sorted(r.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        s.set(x, y, z, n, **p)
    return s


def wind():
    """wind_generator_tide: THE TIDE-MILL LIGHT (WindGenerator("_tide"), MOTIONS WHEEL at WHEEL_AXLE)."""
    return _build("wind_generator_tide")


def converter():
    """energy_converter_tide: THE SALVAGE SMELTER (EnergyConverter("_tide"))."""
    return _build("energy_converter_tide")


def vault():
    """storage_vault_tide: THE HULL STORE (StorageVault("_tide"))."""
    return _build("storage_vault_tide")


# ============================================================================================================ checks
FORBIDDEN = ("lava", "sand", "red_sand", "gravel", "_concrete_powder", "anvil", "redstone", "repeater",
             "comparator", "observer", "piston", "lever", "_button", "_pressure_plate", "tnt", "dragon_egg",
             "scaffolding", "pointed_dripstone", "_door", "_carpet", "candle", "sea_pickle", "kelp", "seagrass",
             "water", "grass_block", "mycelium")
FORBIDDEN_EXACT = ("fire", "soul_fire")


def blocks_of(s):
    return {p: (s.palette[i][0], dict(s.palette[i][1])) for p, i in s.blocks.items()
            if s.palette[i][0] not in ("air", "cave_air")}


def check_wheel(s):
    """tools/check_sail_hubs.py check_motion's WHEEL rules, run on this structure (it reads the shipped nbt by name;
    this is the same logic on the new one), plus the renderer's footprint: the wheel stays inside the z span."""
    b = blocks_of(s)
    x, y, z = WHEEL_AXLE
    n, p = b.get((x, y, z), ("air", {}))
    hubs = {"stripped_spruce_log", "stripped_dark_oak_log", "stripped_crimson_stem", "bone_block", "stripped_oak_log",
            "stripped_mangrove_log", "stripped_cherry_log", "polished_basalt"}
    assert n in hubs and p.get("axis") == "x", "wheel axle %s is %s %s" % (WHEEL_AXLE, n, p)
    assert (x - 1, y, z) not in b, "the block west of the axle is part of the building"
    assert WHEEL_RADIUS <= y + 0.5 - 0.05, "a %s wheel round an axle %s up digs in" % (WHEEL_RADIUS, y + .5)
    assert 0 <= z + 0.5 - WHEEL_RADIUS and z + 0.5 + WHEEL_RADIUS <= 3, "the wheel overhangs the 3-wide face"


def check(s, tag, max_h):
    import audit_structure_pockets as ap
    import tempfile
    b = blocks_of(s)
    assert (s.size[0], s.size[2]) == (3, 3), "%s: footprint %s" % (tag, s.size)
    assert s.size[1] <= max_h, "%s: %d tall (max %d)" % (tag, s.size[1], max_h)
    assert all((x, 0, z) in b for x in range(3) for z in range(3)), "%s: a hole in the foundation" % tag
    assert len(b) < 150, "%s: %d blocks" % (tag, len(b))
    names = {n for n, _ in b.values()}
    bad = sorted(n for n in names if n in FORBIDDEN_EXACT or any(n == f or n.endswith(f) for f in FORBIDDEN))
    assert not bad, "%s: forbidden blocks %s" % (tag, bad)
    for (x, y, z), (n, _) in b.items():
        if n.endswith(("_coral", "_coral_fan")):
            below = b.get((x, y - 1, z), ("air", {}))
            assert gs._sun_sturdy(below, "up"), "%s: coral at %s pops off" % (tag, (x, y, z))
        if n.endswith("campfire"):
            assert y >= 3, "%s: a campfire at %s where units walk" % (tag, (x, y, z))
    path = os.path.join(tempfile.mkdtemp(), tag + ".nbt")
    s.write(path)
    one_way, ground, roof, rooms, note = ap.audit(tag, path)
    assert not one_way and not ground and "TRAPPED" not in note, "%s: pockets %s %s %s" % (tag, one_way, ground, note)
    # nothing a unit can stand IN or ON over the footprint: every standable cell a ground unit reaches from outside
    # is off the footprint (the pocket audit's own flood, from the margin)
    pad_ = ap.Pad(ap.load_nbt(path))
    import lab_slipway
    on = sorted(c for c in lab_slipway._flood_in(pad_, ap) if 0 <= c[0] < 3 and 0 <= c[2] < 3)
    assert not on, "%s: units can walk onto the building at %s" % (tag, on)
    return "%s 3x%dx3, %d blocks: no pockets, nothing walkable on the footprint (%d roof-only cells)%s" % (
        tag, s.size[1], len(b), len(roof), note)


def check_all():
    w, c, v = wind(), converter(), vault()
    check_wheel(w)
    return [check(w, "wind_generator_tide", 16), check(c, "energy_converter_tide", 10),
            check(v, "storage_vault_tide", 10)], (w, c, v)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/claude-0/-home-claude/9aa3ec76-5285-5d94-a971-fdcb4e858c09/scratchpad/eco_tide"
    os.makedirs(out, exist_ok=True)
    lines, built = check_all()
    for name, s in zip(("wind_generator_tide", "energy_converter_tide", "storage_vault_tide"), built):
        s.write(os.path.join(out, name + ".nbt"))
    for line in lines:
        print(line)
