#!/usr/bin/env python3
"""Generates the custom building structures (economy buildings, houses, Verdant / Tidewrought buildings) as vanilla
structure NBT files. Run from the repo root; writes into bar/.../structures/.

Design notes:
- Footprints are fixed (extractors 5x5, wind 3x3) because placement + metal-patch validation
  depend on them.
- metal_extractor_t2 must contain copper_block, cut_copper or waxed_copper_block (and T1 none of them):
  MetalExtractor.getUpgradeLevel() reads the placed blocks as the save-proof tier marker.
- Omitted positions are left untouched by the game (sparse structures), so shapes can be open.
"""
import gzip, struct, io, os

# Structures must exist in BOTH trees: the client reads buildings through its resource manager (assets/) and the
# integrated/dedicated server through its data-pack manager (data/). Missing from data/ = the server can't place it.
OUT = "bar/src/main/resources/assets/reignofnether/structures"
OUT_DATA = "bar/src/main/resources/data/reignofnether/structures"
DATA_VERSION = 3465


# ---- minimal NBT writer ----------------------------------------------------
def tag(t, name, payload):
    return bytes([t]) + struct.pack(">H", len(name)) + name.encode() + payload

def t_string(s):
    return struct.pack(">H", len(s)) + s.encode()

def t_compound(pairs):     # pairs: list of (type, name, payload)
    return b"".join(tag(t, n, p) for t, n, p in pairs) + b"\x00"

def t_list(elem_type, payloads):
    return bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)

def t_int(v):
    return struct.pack(">i", v)


class Structure:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.palette = []       # list of (name, props-dict-frozenset)
        self.pal_index = {}
        self.blocks = {}        # (x,y,z) -> palette index

    def pal(self, name, **props):
        key = (name, tuple(sorted(props.items())))
        if key not in self.pal_index:
            self.pal_index[key] = len(self.palette)
            self.palette.append(key)
        return self.pal_index[key]

    def set(self, x, y, z, name, **props):
        sx, sy, sz = self.size
        assert 0 <= x < sx and 0 <= y < sy and 0 <= z < sz, (x, y, z)
        self.blocks[(x, y, z)] = self.pal(name, **props)

    def fill(self, x1, y1, z1, x2, y2, z2, name, **props):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **props)

    def write(self, path):
        pal_payloads = []
        for name, props in self.palette:
            pairs = [(8, "Name", t_string(name if ":" in name else "minecraft:" + name))]
            if props:
                prop_pairs = [(8, k, t_string(str(v))) for k, v in props]
                pairs.insert(0, (10, "Properties", t_compound(prop_pairs)))
            pal_payloads.append(t_compound(pairs))
        block_payloads = []
        for (x, y, z), idx in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            block_payloads.append(t_compound([
                (3, "state", t_int(idx)),
                (9, "pos", t_list(3, [t_int(x), t_int(y), t_int(z)])),
            ]))
        root = t_compound([
            (9, "size", t_list(3, [t_int(v) for v in self.size])),
            (9, "entities", t_list(0, [])),
            (9, "blocks", t_list(10, block_payloads)),
            (9, "palette", t_list(10, pal_payloads)),
            (3, "DataVersion", t_int(DATA_VERSION)),
        ])
        raw = tag(10, "", root)
        # mtime=0: a regenerated file is byte-identical unless its blocks changed, so reruns don't churn git
        with open(path, "wb") as fh, gzip.GzipFile(fileobj=fh, mode="wb", mtime=0) as f:
            f.write(raw)
        print(f"{path}: {self.size} {len(self.blocks)} blocks, {len(self.palette)} palette entries")


# ---- faction palettes -------------------------------------------------------
# Variants keep their file suffixes (the building classes pick them), but the looks follow the factions in
# claude/design-factions.md. Sunforged (""), Gravebound ("_dark") and Horde ("_nether") have bespoke builders (see
# BESPOKE_ECONOMY below); the palette-driven generic builders serve:
#   "_verdant" Verdant Court: moss and mossy stone, living oak, verdant froglights, lanterns, iron/oxidised-copper silver
#   "_tide"   Tidewrought: sandstone apron, prismarine, mangrove/spruce ship timber, WAXED copper, dead coral, lanterns
#   "_storm"  Stormcallers: andesite and stone-brick plinths, red-terracotta posts, jade roofs (WAXED oxidised copper), gold
#   "_brass"  Brassworks: blackstone and deepslate-brick plinths, brass (WAXED cut copper), soot, furnace orange
# accent/lamp are Chipped blocks. They are decoration only (a frieze, hanging lamps): if Chipped is absent they
# load as air and the building still stands and works. Structural and foundation blocks stay vanilla.
PALETTES = {
    # accent/lamp are vanilla here: froglights glow green from the RTS camera, lanterns are the Court's silver light.
    # The hub is a stripped oak log (WindmillRenderClientEvents gives it leaf-green sails on birch arms).
    "_verdant": dict(
        pad="moss_block", pad_corner="rooted_dirt", ring="mossy_stone_bricks",
        stair="mossy_stone_brick_stairs", wall="mossy_stone_brick_wall", slab="mossy_stone_brick_slab",
        metal="iron_block", body="stripped_birch_wood", cap="oak_planks",
        cap_stair="oak_stairs", cap_slab="oak_slab", hub="stripped_oak_log",
        furnace="smoker", bars="iron_bars",
        accent="verdant_froglight", lamp="lantern",
        core="campfire", pillar="stripped_oak_log", band="oxidized_copper",
    ),
    # Tidewrought (design/tidewrought_plan.md section 5). Everything must stay as built for a whole match: copper is
    # WAXED (unwaxed copper oxidises on random ticks and would re-colour the building), coral is DEAD (live coral dies
    # out of water - a block change mid-match), no kelp/sea pickles (they pop off dry ground). The hub is a stripped
    # mangrove log (WindmillRenderClientEvents gives it teal sails on spruce spars); the stove core is a soul campfire
    # for a sea-teal flame. 1.20.1 has no copper grates, so the bars stay iron.
    "_tide": dict(
        pad="smooth_sandstone", pad_corner="packed_mud", ring="prismarine_bricks",
        stair="prismarine_brick_stairs", wall="prismarine_wall", slab="prismarine_brick_slab",
        metal="waxed_cut_copper", body="stripped_spruce_wood", cap="spruce_planks",
        cap_stair="spruce_stairs", cap_slab="spruce_slab", hub="stripped_mangrove_log",
        furnace="blast_furnace", bars="iron_bars",
        accent="dead_brain_coral_block", lamp="lantern",
        core="soul_campfire", pillar="stripped_mangrove_log", band="waxed_cut_copper",
    ),
    # Stormcallers (design/stormcallers_plan.md section 6). "Jade" is WAXED oxidised cut copper (unwaxed copper weathers
    # on random ticks and would re-colour the building mid-match) with green glazed terracotta accents; gold blocks for
    # the bands, chains and standing lanterns for lamps. No cherry leaves (they shed petals and decay). The hub is a
    # stripped cherry log (WindmillRenderClientEvents gives it jade vanes on red-lacquer spars: a wind-chime tower); the
    # Mana Condenser's core is a soul campfire for a cold jade-blue flame. The T1 extractor's "metal" is a gold block -
    # never copper_block / cut_copper / waxed_copper_block, which MetalExtractor reads as the tier 2 marker.
    "_storm": dict(
        pad="polished_andesite", pad_corner="smooth_stone", ring="stone_bricks",
        stair="stone_brick_stairs", wall="stone_brick_wall", slab="stone_brick_slab",
        metal="gold_block", body="red_terracotta", cap="waxed_oxidized_cut_copper",
        cap_stair="waxed_oxidized_cut_copper_stairs", cap_slab="waxed_oxidized_cut_copper_slab", hub="stripped_cherry_log",
        furnace="blast_furnace", bars="chain",
        accent="green_glazed_terracotta", lamp="lantern",
        core="soul_campfire", pillar="red_terracotta", band="gold_block",
    ),
    # Brassworks (design/brassworks_plan.md section 6). "Brass" is WAXED cut copper (unwaxed copper weathers on random
    # ticks); soot is polished blackstone and deepslate bricks; furnace orange is a lit blast furnace and a campfire core.
    # The T1 extractor's "metal" is waxed EXPOSED cut copper - never copper_block / cut_copper / waxed_copper_block, which
    # MetalExtractor reads as the tier 2 marker. The energy building is bespoke (brass_steam_boiler: no rotor hub, so no
    # sails are drawn), the hub entry is unused.
    "_brass": dict(
        pad="polished_blackstone", pad_corner="blackstone", ring="deepslate_bricks",
        stair="deepslate_brick_stairs", wall="deepslate_brick_wall", slab="deepslate_brick_slab",
        metal="waxed_exposed_cut_copper", body="polished_blackstone_bricks", cap="waxed_cut_copper",
        cap_stair="waxed_cut_copper_stairs", cap_slab="waxed_cut_copper_slab", hub="stripped_spruce_log",
        furnace="blast_furnace", bars="iron_bars",
        accent="waxed_exposed_cut_copper", lamp="lantern",
        core="campfire", pillar="polished_blackstone", band="waxed_cut_copper",
    ),
}


# ---- metal extractor T1: open drill derrick over the patch -----------------
def metal_extractor(P):
    s = Structure(5, 5, 5)
    # worked pad: gravel corners, stone brick cross
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 0, z, P["pad_corner"])
    for (x, z) in [(1, 0), (3, 0), (0, 1), (0, 3), (4, 1), (4, 3), (1, 4), (3, 4)]:
        s.set(x, 0, z, P["ring"])
    # stair skirt pointing outward at edge midpoints (facing = direction the stair climbs toward)
    s.set(2, 0, 0, P["stair"], facing="south", half="bottom")
    s.set(2, 0, 4, P["stair"], facing="north", half="bottom")
    s.set(0, 0, 2, P["stair"], facing="east", half="bottom")
    s.set(4, 0, 2, P["stair"], facing="west", half="bottom")
    s.set(2, 0, 2, P["pad"])
    # four derrick legs leaning in: walls at corners, then top frame
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 1, z, P["wall"])
        s.set(x, 2, z, P["wall"])
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        s.set(x, 3, z, P["slab"], type="bottom")
    # crossbeams at the top of the legs
    s.set(2, 3, 1, P["slab"], type="bottom")
    s.set(2, 3, 3, P["slab"], type="bottom")
    s.set(1, 3, 2, P["slab"], type="bottom")
    s.set(3, 3, 2, P["slab"], type="bottom")
    # the drill itself: iron column + rod bit, hanging from the frame center
    s.set(2, 1, 2, P["metal"])
    s.set(2, 2, 2, P["metal"])
    s.set(2, 3, 2, P["metal"])
    s.set(2, 4, 2, "lightning_rod", facing="up")
    # a furnace shed corner so it reads "industry" (front faces outward, -z)
    s.set(1, 1, 0, P["furnace"], facing="north")
    s.set(3, 1, 0, P["ring"])
    # faction lamp standing on the shed post. Standing, not hanging: the build order only reaches this spot
    # once the full block under it exists, so it is always supported (a hanging lamp could pop off mid-build)
    s.set(3, 2, 0, P["lamp"], hanging="false", waterlogged="false")
    return s


# ---- metal extractor T2: tall copper drill tower ---------------------------
def metal_extractor_t2(P):
    s = Structure(5, 8, 5)
    # same pad language as T1 so the family reads together
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 0, z, P["pad_corner"])
    for (x, z) in [(1, 0), (3, 0), (0, 1), (0, 3), (4, 1), (4, 3), (1, 4), (3, 4)]:
        s.set(x, 0, z, P["ring"])
    s.set(2, 0, 0, P["stair"], facing="south", half="bottom")
    s.set(2, 0, 4, P["stair"], facing="north", half="bottom")
    s.set(0, 0, 2, P["stair"], facing="east", half="bottom")
    s.set(4, 0, 2, P["stair"], facing="west", half="bottom")
    s.set(2, 0, 2, "cut_copper")
    # flared copper base: stairs facing in on all four sides of the tower foot
    s.set(2, 1, 1, "cut_copper_stairs", facing="south", half="bottom")
    s.set(2, 1, 3, "cut_copper_stairs", facing="north", half="bottom")
    s.set(1, 1, 2, "cut_copper_stairs", facing="east", half="bottom")
    s.set(3, 1, 2, "cut_copper_stairs", facing="west", half="bottom")
    # tower shaft: cut copper with copper block banding
    s.set(2, 1, 2, "copper_block")
    s.set(2, 2, 2, "cut_copper")
    s.set(2, 3, 2, "cut_copper")
    s.set(2, 4, 2, "copper_block")
    s.set(2, 5, 2, "cut_copper")
    # machinery head: copper cap with stair cornice + drill rod
    s.set(1, 6, 2, "cut_copper_stairs", facing="east", half="top")
    s.set(3, 6, 2, "cut_copper_stairs", facing="west", half="top")
    s.set(2, 6, 1, "cut_copper_stairs", facing="south", half="top")
    s.set(2, 6, 3, "cut_copper_stairs", facing="north", half="top")
    s.set(2, 6, 2, "copper_block")
    s.set(2, 7, 2, "lightning_rod", facing="up")
    # side pipework: iron columns at two corners feeding the head
    for y in (1, 2, 3):
        s.set(0, y, 0, P["bars"])
        s.set(4, y, 4, P["bars"])
    s.set(0, 4, 0, "copper_block")
    s.set(4, 4, 4, "copper_block")
    # furnace shed carried over from T1 (front faces outward, -z)
    s.set(1, 1, 0, P["furnace"], facing="north")
    s.set(3, 1, 0, P["ring"])
    # faction lamp standing on the shed post. Standing, not hanging: the build order only reaches this spot
    # once the full block under it exists, so it is always supported (a hanging lamp could pop off mid-build)
    s.set(3, 2, 0, P["lamp"], hanging="false", waterlogged="false")
    return s


# ---- wind generator: a proper mill - plastered tower, wooden cap, hub log --
# The sails are NOT blocks: WindmillRenderClientEvents finds the horizontal
# stripped spruce log below and renders four spinning blades around it.
def wind_generator(P):
    s = Structure(3, 8, 3)
    # footing: full pad with a cobble flare at the corners
    s.fill(0, 0, 0, 2, 0, 2, P["pad"])
    s.set(1, 0, 1, P["ring"])
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 1, z, P["wall"])
    # plastered tower body: plus-shaped shaft (reads as a round mill from the RTS camera)
    arms = [(1, 1), (1, 0), (1, 2), (0, 1), (2, 1)]
    for y in range(1, 5):
        for (x, z) in arms:
            s.set(x, y, z, P["body"] if (y != 4 or (x, z) == (1, 1)) else P["accent"])   # carved frieze under the cap
    # spruce trim band + doorway shadow at the foot
    s.set(1, 1, 0, P["cap"])
    s.set(1, 3, 0, "glass_pane", north="false", south="false", east="false", west="false")
    # cap base: wooden ring with the rotor hub sticking out the front (-z)
    for (x, z) in arms:
        s.set(x, 5, z, P["cap"])
    s.set(1, 5, 0, P["hub"], axis="z")   # <- rotor hub (found by the renderer)
    # rounded cap: stairs leaning in, slab peak
    s.set(1, 6, 1, P["cap"])
    s.set(0, 6, 1, P["cap_stair"], facing="east", half="bottom")
    s.set(2, 6, 1, P["cap_stair"], facing="west", half="bottom")
    s.set(1, 6, 2, P["cap_stair"], facing="north", half="bottom")
    s.set(1, 6, 0, P["cap_stair"], facing="south", half="bottom")
    s.set(1, 7, 1, P["cap_slab"], type="bottom")
    return s


# ---- energy converter: a 3x3 crucible/furnace/smelter with its fire visible from the RTS camera --------
def energy_converter(P):
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, P["pad"])
    s.set(1, 0, 1, P["ring"])
    # four posts carrying a band, open sides so the core glows through
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 1, z, P["pillar"], **({"axis": "y"} if P["pillar"].endswith(("log", "basalt", "pillar")) else {}))
        s.set(x, 2, z, P["pillar"], **({"axis": "y"} if P["pillar"].endswith(("log", "basalt", "pillar")) else {}))
    s.set(1, 1, 1, P["furnace"], facing="north", lit="true")
    core = {"lit": "true", "facing": "north", "signal_fire": "false", "waterlogged": "false"} if "campfire" in P["core"] else {}
    s.set(1, 2, 1, P["core"], **core)
    # band ring on top of the posts with a slab rim, chimney above the core
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 3, z, P["band"])
    for (x, z) in [(1, 0), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 3, z, P["slab"], type="bottom")
    s.set(1, 4, 1, P["bars"], **({"axis": "y"} if P["bars"] == "chain" else
                                 {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}))
    return s


# ---- storage vault: a squat 3x3 strongroom - barrels on every face and the faction's hoard showing in the middle ----
# what each faction keeps in its store: Sunforged treasury gold, Gravebound ossuary bones, Horde raw plunder,
# Verdant seed bales, Tidewrought kelp-packed bilge stores (all inert vanilla blocks: nothing changes mid-match)
HOARD = {"": "gold_block", "_dark": "bone_block", "_nether": "raw_gold_block", "_verdant": "hay_block",
         "_tide": "dried_kelp_block", "_storm": "emerald_block",   # the Mana Cistern: a block of jade
         "_brass": "raw_copper_block"}                              # the Strongbox: raw copper for the foundry

def storage_vault(P, variant):
    s = Structure(3, 4, 3)
    s.fill(0, 0, 0, 2, 0, 2, P["pad"])
    s.set(1, 0, 1, P["ring"])
    pillar_props = {"axis": "y"} if P["pillar"].endswith(("log", "basalt", "pillar")) else {}
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 1, z, P["pillar"], **pillar_props)
        s.set(x, 2, z, P["pillar"], **pillar_props)
        s.set(x, 3, z, P["band"])
    # barrels facing out of each side, slab lids over them
    for (x, z, facing) in [(1, 0, "north"), (0, 1, "west"), (2, 1, "east"), (1, 2, "south")]:
        s.set(x, 1, z, "barrel", facing=facing, open="false")
        s.set(x, 2, z, P["slab"], type="bottom")
    # the hoard in the middle, open to the sky so it reads from the RTS camera, a barrel stacked on it
    hoard = HOARD.get(variant, "gold_block")
    s.set(1, 1, 1, hoard, **({"axis": "y"} if hoard in ("bone_block", "hay_block") else {}))
    s.set(1, 2, 1, "barrel", facing="up", open="false")
    return s


# ---- Sunforged / Gravebound / Horde economy: bespoke faction takes -------------------------------------------
# lovish wants every building intricate and individual from the RTS camera, which looks down at ~45 degrees from the
# SOUTH-WEST (OrthoviewClientEvents starts at camRotX 135: facing -z,+x). So what sells a building is its roofline and
# its +z / -x faces; fronts face -z, away from the camera. Each faction gets its own silhouette on the same footprints
# (placement, metal-patch checks and the GameTests depend on them):
#   Sunforged  white quartz, gold finials, lapis and blue banners, glowing end-rod pinnacles: little cathedrals
#   Gravebound deepslate and blackstone, bone ribs, soul fire and soul lanterns, chains, crooked leaning spires
#   Horde      blackstone and nether brick, rusted (WAXED) copper, bone spikes, chains, skulls on posts: a war camp
# Rules every one of them keeps (the build order is bottom-up and RoN compares placed blockstates EXACTLY):
# - nothing hangs and nothing needs support it might not have yet: lanterns stand on full blocks, banners and skulls
#   stand on top of blocks, chains need nothing;
# - nothing changes by itself during a match: copper is waxed, no leaves/vines, no hay under a campfire (it would
#   flip the campfire's signal_fire state);
# - T1 extractors hold no copper_block / cut_copper / waxed_copper_block: MetalExtractor.getUpgradeLevel() reads any
#   of those as the tier 2 marker. T2s carry a waxed_copper_block (waxed so it never oxidises out of its placed state);
# - the wind generators' horizontal hub (stripped spruce / stripped dark oak / bone block) must stay the ONLY
#   horizontal block of those kinds in the structure: WindmillRenderClientEvents spins the sails round the first one
#   it finds. The sails tilt back 40 degrees from a hub at y=4, so their tips reach y~7 over the back of the tower:
#   y=5-6 keep only the four corner posts and every roof starts at y=7.
UP = dict(facing="up")
DRY_ = dict(waterlogged="false")
LANTERN = dict(hanging="false", waterlogged="false")
TO_CAMERA = 2           # standing banner / skull rotation that faces the RTS camera (south-west)
CORNERS5 = [(0, 0), (4, 0), (0, 4), (4, 4)]
CORNERS3 = [(0, 0), (2, 0), (0, 2), (2, 2)]
EDGES3 = [(1, 0), (0, 1), (2, 1), (1, 2)]


def ring_stairs(s, y, lo, hi, name, inward=True, half="bottom"):
    """A square ring of stairs at height y over [lo, hi] x [lo, hi], facing the middle: half=bottom is a hip roof
    climbing inward, half=top a corbelled lintel / cornice (its lower step tucked against the wall). Corners get the facing of their x side;
    the game rounds the shape from the neighbours and RoN's isPlaced ignores stair shape."""
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if x not in (lo, hi) and z not in (lo, hi):
                continue
            if x == lo:
                f = "east" if inward else "west"
            elif x == hi:
                f = "west" if inward else "east"
            elif z == lo:
                f = "south" if inward else "north"
            else:
                f = "north" if inward else "south"
            s.set(x, y, z, name, facing=f, half=half, shape="straight", **DRY_)


def pillar(s, x, z, y1, y2, name="quartz_pillar"):
    for y in range(y1, y2 + 1):
        s.set(x, y, z, name, axis="y")


# -- Sunforged ------------------------------------------------------------------------------------------------
# The Sunforged economy buildings. _SunRig below is their block canvas (stairs, walls and panes settle into their
# connected shapes; check_support asserts nothing would pop off). The metal extractor was an open cathedral headframe
# with a drawn sheave wheel (Oct 16, design/moving_buildings.md slice 1) until the Angel Fountain replaced it (Oct 19,
# lovish); its anchors and the rig room check stay for any later drawn machinery.
_SUN_DIR = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
            "down": (0, -1, 0)}
_SUN_OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
_SUN_CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}
# blocks with no full face anywhere (they hold nothing on their sides, and walls / panes don't join them)
_SUN_THIN = ("_wall", "_fence", "_pane", "iron_bars", "chain", "lantern", "torch", "_banner", "end_rod",
             "lightning_rod", "candle", "bell", "_button", "_carpet", "_pressure_plate", "hopper", "cauldron",
             "flower_pot", "_head", "_skull", "campfire", "grindstone", "anvil")


def _sun_is(blk, *suffixes):
    return any(blk[0] == s or blk[0].endswith(s) for s in suffixes)


def _sun_sturdy(blk, face):
    """Roughly vanilla's isFaceSturdy (full support): does `blk` show a full face on `face`?"""
    n, p = blk
    if n in ("air", "cave_air"):
        return False
    if n.endswith("_slab"):
        t = p.get("type", "bottom")
        return t == "double" or (face == "up" and t == "top") or (face == "down" and t == "bottom")
    if n.endswith("_stairs"):
        half = p.get("half", "bottom")
        if face in ("up", "down"):
            return face == ("up" if half == "top" else "down")
        return p.get("shape", "straight") == "straight" and face == p.get("facing")   # the tall back
    if n.endswith("_trapdoor"):
        if p.get("open") == "true":
            return face == _SUN_OPP[p["facing"]]
        return face == ("up" if p.get("half") == "top" else "down")
    return not _sun_is(blk, *_SUN_THIN)


def _sun_centre_up(blk):
    """Vanilla's canSupportCenter(UP): can a lantern or candle stand on `blk`?"""
    if _sun_sturdy(blk, "up"):
        return True
    n, p = blk
    if n.endswith(("_wall", "_fence", "_pane")) or n in ("iron_bars",):
        return True
    if n == "chain":
        return p.get("axis", "y") == "y"
    if n in ("end_rod", "lightning_rod"):
        return p.get("facing", "up") in ("up", "down")
    return False


def _sun_solid(blk):
    """Vanilla's BlockState.isSolid() (what a standing banner needs under it): any collision box tall or big enough."""
    n, p = blk
    if n in ("air", "cave_air") or _sun_is(blk, "_banner", "_carpet", "_button", "_pressure_plate", "lantern",
                                           "candle", "torch", "_trapdoor", "bell"):
        return False
    return True


class _SunRig:
    """The Sunforged rig drawn on a dict canvas: blocks are (name, props). settle() gives stairs, walls and panes the
    states the game itself gives them once their neighbours stand, so the look doesn't depend on the build order
    (RoN's isPlaced is lenient for those blocks either way); check() asserts nothing would pop off, nothing hangs and
    the moving parts' room is air."""

    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.b = {}

    def set(self, x, y, z, name, **props):
        sx, sy, sz = self.size
        assert 0 <= x < sx and 0 <= y < sy and 0 <= z < sz, (x, y, z, name)
        self.b[(x, y, z)] = (name, props)

    def stair(self, x, y, z, facing, half="bottom", name="quartz_stairs"):
        self.set(x, y, z, name, facing=facing, half=half, shape="straight", waterlogged="false")

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air", {}))

    def nb(self, pos, d):
        dx, dy, dz = _SUN_DIR[d]
        return self.get(pos[0] + dx, pos[1] + dy, pos[2] + dz)

    def _stair_shape(self, pos, blk):
        f, half = blk[1]["facing"], blk[1]["half"]

        def stairs(q):
            return q[0].endswith("_stairs") and q[1].get("half") == half

        def can_take(d):
            q = self.nb(pos, d)
            return not q[0].endswith("_stairs") or q[1].get("facing") != f or q[1].get("half") != half
        def axis(d):
            return "x" if d in ("east", "west") else "z"
        front = self.nb(pos, f)                          # vanilla StairBlock.getStairsShape
        if stairs(front):
            fd = front[1]["facing"]
            if axis(fd) != axis(f) and can_take(_SUN_OPP[fd]):
                return "outer_left" if fd == _SUN_CCW[f] else "outer_right"
        back = self.nb(pos, _SUN_OPP[f])
        if stairs(back):
            bd = back[1]["facing"]
            if axis(bd) != axis(f) and can_take(bd):
                return "inner_left" if bd == _SUN_CCW[f] else "inner_right"
        return "straight"

    def _joins(self, blk, q, d):
        """Does the wall / pane / fence `blk` join its neighbour `q` that lies toward d?"""
        if q[0].endswith("_leaves") or q[0] in ("air", "cave_air"):
            return False
        if blk[0].endswith("_wall"):
            return q[0].endswith(("_wall", "_pane")) or q[0] == "iron_bars" or _sun_sturdy(q, _SUN_OPP[d])
        if blk[0].endswith("_pane") or blk[0] == "iron_bars":
            return q[0].endswith(("_wall", "_pane")) or q[0] == "iron_bars" or _sun_sturdy(q, _SUN_OPP[d])
        same = q[0].endswith("_fence") and (q[0] == "nether_brick_fence") == (blk[0] == "nether_brick_fence")
        return same or _sun_sturdy(q, _SUN_OPP[d])

    def settle(self):
        for pos, blk in list(self.b.items()):
            if blk[0].endswith("_stairs"):
                self.b[pos] = (blk[0], dict(blk[1], shape=self._stair_shape(pos, blk)))
        # walls top-down: a wall's post depends on the wall above it
        for pos in sorted(self.b, key=lambda p: -p[1]):
            name, props = self.b[pos]
            if not (name.endswith(("_wall", "_pane", "_fence")) or name == "iron_bars"):
                continue
            sides = {d: self._joins((name, props), self.nb(pos, d), d) for d in ("north", "south", "east", "west")}
            if not name.endswith("_wall"):
                self.b[pos] = (name, dict({d: str(v).lower() for d, v in sides.items()}, waterlogged="false"))
                continue
            above = self.nb(pos, "up")
            an = above[0]
            full_above = _sun_sturdy(above, "down")
            h = {}
            for d, joined in sides.items():
                arm = an.endswith(("_wall", "_fence", "_pane")) and above[1].get(d) not in (None, "none", "false")
                h[d] = "none" if not joined else "tall" if full_above or arm else "low"
            n, s_, e, w = (h[d] == "none" for d in ("north", "south", "east", "west"))
            if an.endswith("_wall") and above[1].get("up") == "true":
                up = True
            elif (n and s_ and e and w) or n != s_ or e != w:
                up = True
            elif (h["north"] == "tall" and h["south"] == "tall") or (h["east"] == "tall" and h["west"] == "tall"):
                up = False
            else:   # a straight run: a post only if something stands on its middle
                up = an not in ("air", "cave_air") and not (
                    an.endswith(("_slab", "_stairs", "_trapdoor")) and "top" in (above[1].get("type"), above[1].get("half"))
                    or an.endswith("_trapdoor") and above[1].get("open") == "true")
            self.b[pos] = (name, dict(h, up=str(up).lower(), waterlogged="false"))

    def check(self, anchors, tier):
        """Nothing pops off or hangs, and the moving parts' room is air."""
        self.check_support(tier)
        self._check_room(anchors, tier)

    def check_support(self, tier):
        """Nothing would pop off or hang (lanterns, candles, carpets, banners, bells need what vanilla needs under them)."""
        bad = []
        for pos, blk in self.b.items():
            n, p = blk
            below = self.nb(pos, "down")
            if n in ("lantern", "soul_lantern"):
                if p.get("hanging") == "true" or not _sun_centre_up(below):
                    bad.append((pos, n))
            elif n.endswith("candle"):
                if not _sun_centre_up(below):
                    bad.append((pos, n))
            elif n.endswith("_carpet"):
                if below[0] in ("air", "cave_air"):
                    bad.append((pos, n))
            elif n.endswith("_banner") and "_wall_" not in n:
                if not _sun_solid(below):
                    bad.append((pos, n))
            elif n == "bell":
                if p.get("attachment") != "floor" or not _sun_sturdy(below, "up"):
                    bad.append((pos, n))
            elif "wall_" in n or n.endswith(("_button", "ladder")) or n in ("torch", "soul_torch"):
                bad.append((pos, n + " (needs a side or floor: not used on the rig)"))
        assert not bad, "Sunforged rig %s: would pop off / hang: %s" % (tier, bad)

    def _check_room(self, anchors, tier):
        a = anchors
        x, y0, z = a["skip_bottom"]
        clear = [(x, y, z) for y in range(y0, a["skip_top"][1] + 1)]
        hx, hy, hz = a["hub"]
        x, y, z = a["drum"]
        clear += [(x, yy, z) for yy in range(y + 1, int(hy))]      # the rope's back fall, drum to wheel rim
        rr = a["clear_radius"]
        sx, sy, sz = self.size
        for y in range(sy):
            for u in range(sx if a["axis"] == "z" else sz):
                hu = hx if a["axis"] == "z" else hz
                dy = max(y - hy, 0, hy - (y + 1))
                du = max(u - hu, 0, hu - (u + 1))
                if dy * dy + du * du < rr * rr:
                    clear.append((u, y, int(hz)) if a["axis"] == "z" else (int(hx), y, u))
        px, py, pz = a["pennant_top"]
        pole = (int(px), int(py) - 1, int(pz))
        assert self.get(*pole)[0] in ("lightning_rod", "end_rod", "chain"), "pennant pole missing at %s" % (pole,)
        clear += [(pole[0] + dx, pole[1], pole[2] + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        busy = [(c, self.get(*c)[0]) for c in clear if self.get(*c)[0] not in ("air", "cave_air")]
        assert not busy, "Sunforged rig %s: the moving parts' room is taken: %s" % (tier, busy)

    def structure(self):
        s = Structure(*self.size)
        for (x, y, z), (n, p) in sorted(self.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            s.set(x, y, z, n, **p)
        return s


# ---- the Sunforged metal extractor: the Angel Fountain (lovish, Oct 19 2026: "the metal extractors could be
# fountains with angels or something more creative, the current metal extractor building looks really ugly") ----
# Inspiration (never traced): reference 75 (a statue fountain - figures round a pool, sheets of falling water), 68 (a
# tiered fountain - a column with a bowl, candles round the base) and the White Angel angels 33 / 35 (spread wings, a
# halo, the raised arm). The arms are end rods: thin white limbs of light lifting the sun. THE BUILDING STANDARD (art/README) on a 5 x 5 patch: three depths (the stepped kerb, the
# water, the column), a broken top (the wing tips and the lifted sun), mixed materials (quartz, calcite, diorite, gold,
# lapis, copper at T2), a classical style for the White Angel's tiers 1-2, and lights - the sun in the angel's hands
# and the corner candles are the warm points at night.
# The basin holds REAL still water (sources only, every side walled by the kerb or the column; Reign of Nether's
# Healing Fountain does the same and BuildingPlacement clears fluids first when a building falls). The falling
# sheets are light-blue panes joined to the column, so nothing flows. The angel faces south (a camera side); its
# wings spread east-west behind it, so the RTS camera sees the figure framed by them.
# The fountain has no drawn machinery: the headframe's sheave wheel, skip and pennant (BuildingParts.RIGS) leave the
# Sunforged extractor with it (moving_buildings.md slice 1 stays on the Gravebound headframe).

def _sun_fountain_t1(r):
    """Every T1 block of the Angel Fountain (the T2 draws over them: it only adds or replaces)."""
    # the parvis: chiseled corners, a smooth quartz kerb with lapis thresholds, diorite flags and the gold sun - the
    # metal the fountain draws - under the water
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            r.set(x, 0, z, "chiseled_quartz_block" if corner else "smooth_quartz" if edge else "polished_diorite")
    for (x, z) in [(2, 0), (0, 2), (4, 2), (2, 4)]:
        r.set(x, 0, z, "lapis_block")
    r.set(2, 0, 2, "gold_block")
    # the basin: chiseled corner pedestals and a stepped quartz kerb (stairs rising toward the water, so the rim reads
    # as a moulding from outside), still water inside round the column's base
    for (x, z) in CORNERS5:
        r.set(x, 1, z, "chiseled_quartz_block")
    for i in (1, 2, 3):
        r.stair(i, 1, 0, "south")
        r.stair(i, 1, 4, "north")
        r.stair(0, 1, i, "east")
        r.stair(4, 1, i, "west")
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            if (x, z) != (2, 2):
                r.set(x, 1, z, "water", level="0")
    r.set(2, 1, 2, "quartz_bricks")
    # lit candles on the corner pedestals (ref 68's ring of candles)
    for (x, z) in CORNERS5:
        r.set(x, 2, z, "white_candle", candles="4", lit="true", waterlogged="false")
    # the column, and four sheets of falling water from the bowl into the basin (panes joined to the column)
    for y in (2, 3, 4):
        r.set(2, y, 2, "quartz_pillar", axis="y")
    for (x, z) in [(2, 1), (1, 2), (3, 2), (2, 3)]:
        r.set(x, 2, z, "light_blue_stained_glass_pane", waterlogged="false")
    # the bowl: upside-down stairs round the column (their lip overhangs outward), top slabs in the corners round it
    r.stair(2, 3, 1, "south", "top")
    r.stair(2, 3, 3, "north", "top")
    r.stair(1, 3, 2, "east", "top")
    r.stair(3, 3, 2, "west", "top")
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        r.set(x, 3, z, "smooth_quartz_slab", type="top", waterlogged="false")
    # the angel. The robe's hem flares on the bowl (stairs rising into the figure), the robe and the torso are
    # quartz, the shoulders upside-down stairs, the head chiseled quartz; both arms (diorite colonnettes) lift the sun
    # over the head - the Sunforged emblem, the angel's halo and the fountain's night light in one
    r.stair(1, 4, 2, "east")
    r.stair(3, 4, 2, "west")
    r.stair(2, 4, 3, "north")
    r.set(2, 4, 2, "quartz_pillar", axis="y")
    r.set(2, 5, 2, "smooth_quartz")
    r.stair(1, 5, 2, "east", "top")
    r.stair(3, 5, 2, "west", "top")
    r.set(2, 6, 2, "chiseled_quartz_block")
    for y in (6, 7):
        r.set(1, y, 2, "end_rod", facing="up")
        r.set(3, y, 2, "end_rod", facing="up")
    r.set(2, 7, 2, "ochre_froglight", axis="y")
    # the wings, spread behind the figure (z = 1) from a calcite back: broad rather than tall - each a two-wide fan
    # from the root at the shoulder blade to the outer edge, its top edge stepping up outward in stairs to the tip,
    # the lowest feathers hanging under it as upside-down stairs
    r.set(2, 5, 1, "calcite")
    r.set(2, 6, 1, "calcite")
    for x, root, inward, outward in ((0, 1, "east", "west"), (4, 3, "west", "east")):
        r.set(root, 5, 1, "calcite")
        r.stair(x, 5, 1, inward, "top")           # the lowest feathers, hanging
        r.set(root, 6, 1, "smooth_quartz")
        r.set(x, 6, 1, "smooth_quartz")
        r.stair(root, 7, 1, outward)              # the leading edge, rising outward
        r.set(x, 7, 1, "calcite")
        r.stair(x, 8, 1, inward)                  # the tip, curling in


def _sun_fountain_t2(r):
    """The T2 over the T1: copper in the parvis corners (the tier 2 marker), the candles grown into lantern posts with
    gold caps, lit wing tips and a crown of rays over the sun."""
    _sun_fountain_t1(r)
    for (x, z) in CORNERS5:
        r.set(x, 0, z, "waxed_copper_block")
        r.set(x, 2, z, "diorite_wall")
        r.set(x, 3, z, "diorite_wall")
        r.set(x, 4, z, "gold_block")
        r.set(x, 5, z, "lantern", **LANTERN)
    # the wing tips light up: a glowing rod on each
    for x in (0, 4):
        r.set(x, 9, 1, "end_rod", facing="up")
    for x in (1, 2, 3):
        r.set(x, 8, 2, "end_rod", facing="up")


def sun_metal_extractor():
    """T1 'Angel Fountain': a stepped quartz basin of still water round a column with a bowl and four falling sheets,
    and on it a white angel with spread wings and a gold halo lifting the glowing sun. Gold, no copper (that is the
    tier 2 marker). 5 x 11 x 5."""
    r = _SunRig(5, 11, 5)
    _sun_fountain_t1(r)
    r.settle()
    r.check_support("T1")
    return r.structure()


def sun_metal_extractor_t2():
    """T2 'Gilded Angel Fountain': the T1 with copper in the parvis, gold-capped lantern posts on the corners, lit wing
    tips and a crown of rays over the sun. Every T1 block stays a block (check_mex_tiers). 5 x 11 x 5."""
    r = _SunRig(5, 11, 5)
    _sun_fountain_t2(r)
    r.settle()
    r.check_support("T2")
    t1 = _SunRig(5, 11, 5)
    _sun_fountain_t1(t1)
    t1.settle()
    check_mex_tiers(t1.structure(), r.structure(), "sunforged")
    return r.structure()


def sun_wind_generator():
    """The Gilded Sun Mill (design/lively_buildings.md): a white quartz tower on a diorite parvis, quartz-pillar corners,
    blue lancets lit from inside by a glowstone heart, a gold band and a quartz corbel under the spruce hub at y=4
    (WindmillRenderClientEvents turns white sails on GILDED spars round it), lit white candles on the front shoulders,
    a gold bell standing in the open belfry, a quartz hip roof and, crowning it, a sun disc - a ring of gold round an
    ochre-froglight core facing the camera side - that glows at night. The sails tilt back 40 degrees: y=5-6 keep the
    four belfry posts and the bell in the middle column only, and the roof starts at y=7 (the blade tips reach ~6.8).
    3 x 12 x 3."""
    s = Structure(3, 12, 3)
    for x in range(3):
        for z in range(3):
            s.set(x, 0, z, "chiseled_quartz_block" if (x, z) in CORNERS3 else "polished_diorite")
    s.set(1, 0, 1, "lapis_block")
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 3 if z == 0 else 4)
    for (x, z) in EDGES3:
        s.set(x, 1, z, "smooth_quartz")
        s.set(x, 2, z, "blue_stained_glass")
        s.set(x, 3, z, "gold_block")
    s.set(1, 1, 1, "quartz_bricks")
    s.set(1, 2, 1, "glowstone")                          # lights the lancets from inside at night
    s.set(1, 3, 1, "quartz_bricks")
    s.set(1, 3, 0, "quartz_stairs", facing="south", half="top", shape="straight", **DRY_)   # corbel under the hub
    s.set(1, 4, 0, "stripped_spruce_log", axis="z")     # <- rotor hub (WindGenerator.SAIL_HUBS)
    for (x, z) in [(1, 1), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 4, z, "quartz_bricks")
    for x in (0, 2):                                     # the front shoulders: lit candles on chiseled quartz
        s.set(x, 4, 0, "white_candle", candles="3", lit="true", **DRY_)
    for (x, z) in CORNERS3:                              # the belfry posts the sails turn between
        for y in (5, 6):
            s.set(x, y, z, "quartz_pillar" if z == 2 else "diorite_wall", **({"axis": "y"} if z == 2 else {}))
    s.set(1, 5, 1, "bell", attachment="floor", facing="west", powered="false")   # a gold bell in the open belfry
    # the hip roof: smooth quartz stairs round a lapis cap, a gold boss
    ring_stairs(s, 7, 0, 2, "smooth_quartz_stairs")
    s.set(1, 7, 1, "lapis_block")
    s.set(1, 8, 1, "gold_block")
    s.set(0, 8, 2, "blue_banner", rotation=str(TO_CAMERA))
    # the sun disc: a 3x3 face in the x-y plane, gold rim, yellow glass corners, an ochre froglight sun
    for x in range(3):
        for y in (9, 10, 11):
            if (x, y) == (1, 10):
                s.set(x, y, 1, "ochre_froglight", axis="y")
            elif x == 1 or y == 10:
                s.set(x, y, 1, "gold_block")
            else:
                s.set(x, y, 1, "yellow_stained_glass")
    return s


def sun_energy_converter():
    """A sun crucible: four quartz piers round a lit blast furnace with a lava basin on top glowing up at the
    camera through the open crown, gold capitals with end-rod pinnacles, a quartz lintel ring with sea-lantern
    keystones. 3 x 5 x 3."""
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, "smooth_quartz")
    s.set(1, 0, 1, "gold_block")
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 2)
        s.set(x, 3, z, "gold_block")
        s.set(x, 4, z, "end_rod", **UP)
    s.set(1, 1, 1, "blast_furnace", facing="west", lit="true")
    s.set(1, 2, 1, "lava_cauldron")
    for (x, z) in EDGES3:
        s.set(x, 1, z, "quartz_stairs", facing={(1, 0): "south", (0, 1): "east", (2, 1): "west", (1, 2): "north"}[(x, z)],
              half="bottom", shape="straight", **DRY_)
        s.set(x, 3, z, "sea_lantern" if (x, z) in ((0, 1), (1, 2)) else "quartz_bricks")
    return s


def sun_storage_vault():
    """The royal treasury: quartz piers and barrels on every face, a quartz hip roof round an open gold hoard,
    gold capitals and a blue banner on each camera-facing corner. 3 x 5 x 3."""
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_diorite")
    s.set(1, 0, 1, "lapis_block")
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 2)
        s.set(x, 3, z, "gold_block")
    for (x, z, f) in [(1, 0, "north"), (0, 1, "west"), (2, 1, "east"), (1, 2, "south")]:
        s.set(x, 1, z, "barrel", facing=f, open="false")
        s.set(x, 2, z, "smooth_quartz")
        s.set(x, 3, z, "quartz_stairs", facing={"north": "south", "south": "north", "west": "east", "east": "west"}[f],
              half="bottom", shape="straight", **DRY_)
    s.set(1, 1, 1, "gold_block")
    s.set(1, 2, 1, "gold_block")                 # the hoard, heaped up to the roof's mouth
    s.set(0, 4, 2, "blue_banner", rotation=str(TO_CAMERA))
    s.set(2, 4, 2, "blue_banner", rotation="0")
    s.set(0, 4, 0, "blue_banner", rotation="4")
    s.set(2, 4, 0, "lantern", **LANTERN)
    return s


# -- Gravebound -----------------------------------------------------------------------------------------------
# The extractors are the "soul hoist" (design/moving_buildings.md section 1: the extractor rig, Gravebound skin).
# lovish (Oct 16) showed Minecraft builds to refine them from: a dark-fantasy pillar (blackstone corner piers, a recessed
# shaft of another texture, a stepped base, a capital flaring out in tiers with soul lanterns hung under it), a gothic
# castle (deep reveals, pointed arches, layered slab / stair / wall trims, spires of stacked shrinking tiers) and a
# fairy-tale castle (a crowd of pinnacles). So the rig is four of those pillars on the inner 3x3 - a stepped plinth,
# dark colonnettes round a recessed basalt shaft, a corbelled capital with a soul lantern hung under its corner - braced
# with bone ribs and chains on the back (north / east) bays only, so the camera's west and south faces stay open and hide
# no units. On the west face, between the two west pillars, the ore skip rides a clear shaft up to the sheave wheel,
# which turns just outside a gallows on the west pillars (dark posts, a dark oak beam, a bone winch drum for the axle);
# flying buttresses run down from the gallows to the back capitals, pinnacles with soul fire stand on the corners and
# the far corner carries the owner's pennant. The T2 (soul spire) raises the west order, the gallows and the wheel and
# builds a gothic spire with a verdigris roof over the back half.
# The moving parts are drawn by code (a PartRenderer), never blocks: GRAVE_RIG_ANCHORS says where, in the structure's
# own unrotated block cells (like WindGenerator.SAIL_HUBS): the sheave's hub cell (the wheel turns in the vertical
# plane through it, round `sheave_axis`, on the `sheave_outward` face of the axle block beside it, its disc of
# `sheave_radius` - the hub's 3x3 in that plane - kept clear), the skip shaft's clear column (bottom and top cell,
# inclusive; the skip's chain winds on the drum, so the shaft is right under the hub) and the pennant pole's top block
# (the flag flies from its top face). _grave_rig_check asserts the clearances on every build; change a structure,
# change its anchors.
GRAVE_RIG_ANCHORS = {
    "T1": {"sheave_hub": (0, 9, 2), "sheave_axis": "x", "sheave_outward": "west", "sheave_radius": 1.5,
           "sheave_axle": (1, 9, 2), "skip_bottom": (0, 1, 2), "skip_top": (0, 7, 2), "pennant_pole_top": (4, 10, 0)},
    "T2": {"sheave_hub": (0, 11, 2), "sheave_axis": "x", "sheave_outward": "west", "sheave_radius": 1.5,
           "sheave_axle": (1, 11, 2), "skip_bottom": (0, 1, 2), "skip_top": (0, 9, 2), "pennant_pole_top": (3, 13, 2)},
}
GRAVE_PIERS = {(1, 1): (-1, -1), (3, 1): (1, -1), (1, 3): (-1, 1), (3, 3): (1, 1)}   # pier -> its outward (dx, dz)


def _gr_stairs(s, x, y, z, name, facing, half="bottom"):
    s.set(x, y, z, name, facing=facing, half=half, shape="straight", **DRY_)


def _gr_slab(s, x, y, z, name, kind="bottom"):
    s.set(x, y, z, name, type=kind, **DRY_)


def _gr_toward(dx, dz):
    """The facing that points from a cell offset (dx, dz) from its pier back at the pier (a corbel / plinth step)."""
    return "east" if dx < 0 else "west" if dx > 0 else "south" if dz < 0 else "north"


_GR_SIDES = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}
_GR_OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
_GR_THIN = ("_wall", "_fence", "iron_bars", "_pane", "chain", "lantern", "_trapdoor", "_button", "_skull", "_slab",
            "_stairs", "end_rod", "lightning_rod", "candle", "campfire", "_banner", "torch", "air")


def _gr_block(s, p):
    i = s.blocks.get(p)
    return (None, {}) if i is None else (s.palette[i][0], dict(s.palette[i][1]))


def _gr_face_full(s, p, side):
    """Whether the block at p shows a full face on `side` (what a wall or bars on that side connects to)."""
    n, props = _gr_block(s, p)
    if n is None:
        return False
    if n.endswith(("_wall", "_pane")) or n == "iron_bars":
        return True
    if n.endswith("_stairs"):
        return props.get("facing") == side
    if n.endswith("_slab"):
        return props.get("type") == "double"
    return not any(t in n for t in _GR_THIN)


def _gr_connect(s):
    """Walls and iron bars carry the connection states the game gives them once their neighbours stand (a wall placed
    after its neighbours keeps the state it is placed with; RoN's isPlaced is lenient for both, the preview is not)."""
    for (x, y, z), i in list(s.blocks.items()):
        n = s.palette[i][0]
        if not (n.endswith("_wall") or n == "iron_bars"):
            continue
        conn = {d: _gr_face_full(s, (x + dx, y, z + dz), _GR_OPP[d]) for d, (dx, dz) in _GR_SIDES.items()}
        if n == "iron_bars":
            s.set(x, y, z, n, **{d: "true" if c else "false" for d, c in conn.items()}, **DRY_)
            continue
        above, _ = _gr_block(s, (x, y + 1, z))
        tall = above is not None and (above.endswith("_wall") or not any(t in above for t in _GR_THIN))
        on = {d for d, c in conn.items() if c}
        straight = on in ({"north", "south"}, {"east", "west"})
        s.set(x, y, z, n, up="false" if straight and not tall else "true",
              **{d: ("tall" if tall else "low") if c else "none" for d, c in conn.items()}, **DRY_)


def _grave_rig_check(s, tier):
    """The rig's moving parts have their room (GRAVE_RIG_ANCHORS) and nothing on it pops off: standing lanterns,
    candles and torches need a floor, hanging lanterns a ceiling or a chain (the build runs bottom-up, so a hanging
    lantern goes in before its ceiling - vanilla only re-checks it when the block above is placed, by which time it
    holds)."""
    a = GRAVE_RIG_ANCHORS[tier]
    hx, hy, hz = a["sheave_hub"]
    r = int(a["sheave_radius"])            # the cells the wheel sweeps: radius 1.5 from the hub cell's centre = its 3x3
    disc = [(hx, hy + dv, hz + du) if a["sheave_axis"] == "x" else (hx + du, hy + dv, hz)
            for du in range(-r, r + 1) for dv in range(-r, r + 1)]
    bx, by, bz = a["skip_bottom"]
    shaft = [(bx, y, bz) for y in range(by, a["skip_top"][1] + 1)]
    for p in disc + shaft:
        assert _gr_block(s, p)[0] in (None, "air"), f"grave rig {tier}: {p} must stay clear for the moving parts"
    # the middle column stays open up to the T1's centre height: the indoor spawn point is the T1 centre walked down
    # through air (ProductionBuilding.getIndoorSpawnPoint; centrePos is not recomputed on the upgrade)
    for y in range(1, 6):
        assert _gr_block(s, (2, y, 2))[0] in (None, "air"), f"grave rig {tier}: the spawn column is blocked at y {y}"
    assert _gr_block(s, a["sheave_axle"])[0] not in (None, "air"), f"grave rig {tier}: no sheave axle"
    assert _gr_block(s, a["pennant_pole_top"])[0] not in (None, "air"), f"grave rig {tier}: no pennant pole"
    for (x, y, z), i in s.blocks.items():
        n, props = s.palette[i][0], dict(s.palette[i][1])
        if n in ("lantern", "soul_lantern") and props.get("hanging") == "true":
            above, ap = _gr_block(s, (x, y + 1, z))
            ok = above is not None and (above in ("chain",) or above.endswith(("_wall", "_fence")) or above == "iron_bars"
                                        or (above.endswith("_slab") and ap.get("type") in ("bottom", "double"))
                                        or (above.endswith("_stairs") and ap.get("half") == "bottom")
                                        or not any(t in above for t in _GR_THIN))
            assert ok, f"grave rig {tier}: the hanging lantern at {(x, y, z)} has nothing to hang from"
        elif n in ("lantern", "soul_lantern", "soul_torch", "torch") or n.endswith("candle"):
            below, bp = _gr_block(s, (x, y - 1, z))
            ok = below is not None and (below.endswith(("_wall", "_fence")) or below == "chain"
                                        or (below.endswith("_slab") and bp.get("type") in ("top", "double"))
                                        or (below.endswith("_stairs") and bp.get("half") == "top")
                                        or not any(t in below for t in _GR_THIN))
            assert ok, f"grave rig {tier}: the {n} at {(x, y, z)} has no floor"


def grave_pad5(s):
    """The 5x5 grave plot: a deepslate tile kerb with polished blackstone corner stones, tile-stair skirts on the open
    sides, a cobbled deepslate floor and a crying obsidian heart over the patch."""
    _pad5(s, "polished_blackstone_bricks", "deepslate_tiles", "deepslate_tile_stairs", "cobbled_deepslate",
          "crying_obsidian")
    for (x, z) in GRAVE_PIERS:
        s.set(x, 0, z, "polished_deepslate")


def grave_pier(s, px, pz, dx, dz):
    """One of the four reference pillars, on its inner-3x3 corner (px, pz) with its outward side (dx, dz): a stepped
    blackstone plinth, a recessed polished-basalt shaft between two polished-blackstone colonnettes on the outer faces
    (the corner left open, so the shaft reads as set back), a deepslate-brick capital that flares from the colonnettes
    to corbels to a full abacus, and a soul lantern hung under the capital's corner block."""
    a, b, c = (px + dx, pz), (px, pz + dz), (px + dx, pz + dz)
    tx, tz = _gr_toward(dx, 0), _gr_toward(0, dz)
    # the plinth: a 2x2 course with its corner sloped, then two stairs stepping back to a chiseled die
    s.set(px, 1, pz, "polished_blackstone_bricks")
    s.set(a[0], 1, a[1], "cracked_polished_blackstone_bricks")
    s.set(b[0], 1, b[1], "polished_blackstone_bricks")
    _gr_stairs(s, c[0], 1, c[1], "polished_blackstone_brick_stairs", tx)
    s.set(px, 2, pz, "chiseled_polished_blackstone")
    _gr_stairs(s, a[0], 2, a[1], "polished_blackstone_brick_stairs", tx)
    _gr_stairs(s, b[0], 2, b[1], "polished_blackstone_brick_stairs", tz)
    # the shaft: a recessed basalt core between dark colonnettes
    for y in (3, 4, 5):
        s.set(px, y, pz, "polished_basalt", axis="y")
        s.set(a[0], y, a[1], "polished_blackstone_wall")
        s.set(b[0], y, b[1], "polished_blackstone_wall")
    # the capital: corbels over the colonnettes and a corner block holding the soul lantern, then the abacus
    s.set(px, 6, pz, "polished_blackstone_bricks")
    _gr_stairs(s, a[0], 6, a[1], "deepslate_brick_stairs", tx, "top")
    _gr_stairs(s, b[0], 6, b[1], "deepslate_brick_stairs", tz, "top")
    s.set(c[0], 6, c[1], "deepslate_bricks")
    s.set(c[0], 5, c[1], "soul_lantern", hanging="true", waterlogged="false")
    s.set(px, 7, pz, "deepslate_tiles")
    s.set(a[0], 7, a[1], "deepslate_bricks")
    s.set(b[0], 7, b[1], "cracked_deepslate_bricks")
    _gr_stairs(s, c[0], 7, c[1], "deepslate_brick_stairs", tx, "top")


def grave_gallows(s, y, beam=True):
    """The frame that holds the sheave on the west piers' line (x=1), right behind the wheel: two dark posts at y-2 and
    y-1 and the bone winch drum between them at y-1 (the axle that carries the wheel out on the west face,
    GRAVE_RIG_ANCHORS), a chain under the drum; with `beam`, the gallows' dark oak beam over them at y with blackstone
    corbel ends and chains dropping from those ends."""
    for z in (1, 3):
        s.set(1, y - 2, z, "polished_blackstone_wall")
        s.set(1, y - 1, z, "polished_blackstone_wall")
    s.set(1, y - 1, 2, "bone_block", axis="x")
    s.set(1, y - 2, 2, "chain", axis="y", **DRY_)
    if beam:
        for z in (1, 2, 3):
            s.set(1, y, z, "dark_oak_log", axis="z")
        _gr_stairs(s, 1, y, 0, "polished_blackstone_brick_stairs", "south", "top")
        _gr_stairs(s, 1, y, 4, "polished_blackstone_brick_stairs", "north", "top")
        for z in (0, 4):
            s.set(1, y - 1, z, "chain", axis="y", **DRY_)


def grave_hoist(s):
    """The T1 soul hoist, which the T2 keeps whole (every T1 block stays a block in the T2: check_mex_tiers)."""
    grave_pad5(s)
    for (px, pz), (dx, dz) in GRAVE_PIERS.items():
        grave_pier(s, px, pz, dx, dz)
    # keystones between the capitals: bone at the back over arch heads layered with a slab; over the south arch (the
    # camera's) an ossuary gate - a bone lintel, a dark keystone and a skull watching the camera; the west gap is the
    # skip's
    s.set(2, 7, 0, "bone_block", axis="x")
    s.set(4, 7, 2, "bone_block", axis="z")
    for (x, z) in [(2, 0), (4, 2)]:
        _gr_slab(s, x, 6, z, "polished_blackstone_brick_slab", "top")
    s.set(2, 6, 4, "bone_block", axis="x")
    s.set(2, 7, 4, "chiseled_polished_blackstone")
    s.set(2, 8, 4, "skeleton_skull", rotation=str(TO_CAMERA))
    s.set(4, 8, 2, "wither_skeleton_skull", rotation=str(TO_CAMERA))
    # back braces only: bone ribs across the north and east bays under lintels, chains hung between them
    for (x, z, ax) in [(2, 1, "x"), (3, 2, "z")]:
        s.set(x, 3, z, "bone_block", axis=ax)
        s.set(x, 5, z, "bone_block", axis=ax)
        s.set(x, 4, z, "chain", axis="y", **DRY_)
        s.set(x, 6, z, "chain", axis="y", **DRY_)
        s.set(x, 7, z, "deepslate_tiles")
    grave_gallows(s, 10)
    # the back legs: two flying buttresses stepping down from the gallows posts onto the back capitals
    for z in (1, 3):
        _gr_stairs(s, 2, 9, z, "polished_blackstone_brick_stairs", "west")
        _gr_stairs(s, 3, 8, z, "polished_blackstone_brick_stairs", "west")
    # caps over the capitals where the wheel leaves room; pinnacles on the outer corners: a soul brazier beside the
    # wheel, a short lantern on the camera's corner (the wheel stays in view), a lantern post at the south-east, and
    # the far corner carries the pennant pole
    for (x, z) in [(1, 0), (3, 0), (4, 1), (4, 3)]:
        _gr_slab(s, x, 8, z, "deepslate_brick_slab")
    for x in (1, 3):                                               # the frieze over the south arch: black skulls
        s.set(x, 8, 4, "wither_skeleton_skull", rotation=str(TO_CAMERA))   # flanking the white one
    s.set(0, 8, 0, "deepslate_brick_wall")
    s.set(0, 9, 0, "soul_campfire", lit="true", facing="north", signal_fire="false", **DRY_)
    s.set(0, 8, 4, "soul_lantern", hanging="false", waterlogged="false")
    s.set(4, 8, 4, "deepslate_brick_wall")
    s.set(4, 9, 4, "soul_lantern", hanging="false", waterlogged="false")
    for y in (8, 9, 10):
        s.set(4, y, 0, "polished_blackstone_wall")                 # the pennant pole
    s.set(0, 2, 4, "skeleton_skull", rotation=str(TO_CAMERA))


def grave_metal_extractor():
    """T1 'soul hoist': an open gallows headframe on four dark-fantasy pillars over the grave pit (see the notes over
    GRAVE_RIG_ANCHORS). No copper of any kind: copper_block / cut_copper / waxed_copper_block are the T2 marker
    (MetalExtractor.getUpgradeLevel), and verdigris is the T2's look. 5 x 11 x 5."""
    s = Structure(5, 11, 5)
    grave_hoist(s)
    _gr_connect(s)
    _grave_rig_check(s, "T1")
    return s


VERDIGRIS = "waxed_oxidized_cut_copper"     # aged copper, waxed so it keeps its colour all match


def grave_metal_extractor_t2():
    """T2 'soul spire': the hoist grows a second order and a spire, so the opponent reads the tier from afar (3 blocks
    taller, a verdigris crown, twice the lights). The west piers rise (new colonnettes and a second corbelled capital
    beside the T1 posts), the winch drum and the wheel move up two blocks between taller posts with lantern finials, so
    the skip rides a longer shaft; over the back half a gothic spire takes the flying buttresses' place: a blackstone
    drum with lancets lit by a soul lantern inside, a corbelled cornice, corner pinnacles with soul lanterns, a
    verdigris copper roof and the needle that carries the pennant. Every capital's corner is capped in verdigris (aged,
    waxed copper - the tier rule's copper as old metal). Carries the waxed_copper_block tier marker, sealed out of
    sight. Every T1 block stays a block here (check_mex_tiers). 5 x 14 x 5."""
    s = Structure(5, 14, 5)
    grave_hoist(s)
    s.set(3, 0, 1, "waxed_copper_block")         # tier 2 marker (getUpgradeLevel), sealed under the back pier
    for (px, pz), (dx, dz) in GRAVE_PIERS.items():   # every capital's corner capped in verdigris
        _gr_stairs(s, px + dx, 7, pz + dz, VERDIGRIS + "_stairs", _gr_toward(dx, 0), "top")
    # the west piers' second order: colonnettes beside the old posts, a second corbelled capital, the old beam's ends
    # kept as brackets and the old drum as a verdigris collar
    for z in (1, 3):
        s.set(0, 8, z, "polished_blackstone_wall")
        _gr_stairs(s, 0, 9, z, "deepslate_brick_stairs", "east", "top")
    s.set(1, 9, 2, VERDIGRIS)
    grave_gallows(s, 12, beam=False)              # new posts where the old beam was, the new drum two blocks up
    for z in (1, 3):
        s.set(1, 12, z, "soul_lantern", hanging="false", waterlogged="false")
    # the pinnacles beside the wheel grow: a tall one with a soul brazier at the back, a lantern post at the front
    for y in (9, 10, 11):
        s.set(0, y, 0, "polished_blackstone_wall")
    s.set(0, 12, 0, "soul_campfire", lit="true", facing="north", signal_fire="false", **DRY_)
    s.set(0, 8, 4, "polished_blackstone_wall")
    s.set(0, 9, 4, "polished_blackstone_wall")
    s.set(0, 10, 4, "soul_lantern", hanging="false", waterlogged="false")
    # the spire's drum (x 2-4, z 1-3, y 8-9) on the back capitals: blackstone corners, lancets of iron bars on its
    # west and south faces lit by a soul lantern inside, solid back faces
    for x in (2, 3, 4):
        for z in (1, 2, 3):
            for y in (8, 9):
                corner = x != 3 and z != 2
                if (x, z) == (3, 2):
                    s.set(x, y, z, "soul_lantern" if y == 8 else "polished_blackstone_bricks",
                          **({"hanging": "false", "waterlogged": "false"} if y == 8 else {}))
                elif corner:
                    s.set(x, y, z, "polished_blackstone_bricks" if y == 8 else "chiseled_polished_blackstone")
                elif (x, z) in ((2, 2), (3, 3)):
                    s.set(x, y, z, "iron_bars")
                else:
                    s.set(x, y, z, "deepslate_bricks")
    # the cornice (y 10): corbels flaring out on every side, blackstone corners carrying the pinnacles
    for x in (2, 3, 4):
        for z in (1, 2, 3):
            if (x, z) == (3, 2):
                s.set(x, 10, z, "deepslate_tiles")
            elif x != 3 and z != 2:
                s.set(x, 10, z, "polished_blackstone_bricks")
            else:
                f = {(2, 2): "east", (4, 2): "west", (3, 1): "south", (3, 3): "north"}[(x, z)]
                _gr_stairs(s, x, 10, z, "deepslate_brick_stairs", f, "top")
    # tier 2 (y 11): pinnacles with soul lanterns on the corners, a verdigris hip between them round the core
    for (x, z) in [(2, 1), (4, 1), (2, 3), (4, 3)]:
        s.set(x, 11, z, "polished_blackstone_wall")
        s.set(x, 12, z, "soul_lantern", hanging="false", waterlogged="false")
    for (x, z, f) in [(3, 1, "south"), (3, 3, "north"), (2, 2, "east"), (4, 2, "west")]:
        _gr_stairs(s, x, 11, z, VERDIGRIS + "_stairs", f)
    s.set(3, 11, 2, VERDIGRIS)
    # tier 3 (y 12-13): the verdigris cap and the needle (the pennant pole)
    _gr_slab(s, 3, 12, 2, VERDIGRIS + "_slab", "double")
    s.set(3, 13, 2, "polished_blackstone_wall")
    s.set(4, 11, 0, "soul_lantern", hanging="false", waterlogged="false")
    _gr_connect(s)
    _grave_rig_check(s, "T2")
    check_mex_tiers(grave_metal_extractor(), s, "_dark")
    return s


# The rest of the Gravebound economy set in the soul hoist's family (lovish, Oct 16: "use Minecraft inspired pictures to
# create extremely detailed buildings ... keep improving the detail and realism"). Each 3x3 takes the hoist's pillar -
# a stepped blackstone plinth, a recessed polished-basalt shaft between dark colonnettes, a deepslate-brick capital that
# flares in tiers, soul lanterns under it, bone and skulls for accents - and grows its own silhouette from it, so the
# four read apart at RTS zoom, each with its white in its own place: the wind generator a tall campanile (an open
# belfry, a needle spire, skull pinnacles), the converter a squat chalice (a soul-fire brazier on a waist of bone), the
# vault a buttressed crypt (a church-front gable under a bone spine). Lights sit on the outer faces and corners: a
# 3x3's deep slots hide whatever burns inside them from the camera. Same rules as the hoist: nothing hangs that has no
# ceiling or chain, nothing stands that has no floor (grave_holds), every state stays as placed, ground cells open to
# the outside; every block has a BiomePalette decision (deepslate, blackstone, basalt, bone, soul fire: kept).
_GR_IN = {(0, 1): "east", (2, 1): "west", (1, 0): "south", (1, 2): "north"}     # an edge cell -> facing the middle
_GR_OUT = {k: _GR_OPP[v] for k, v in _GR_IN.items()}                          # an edge cell -> facing outward


def grave_pad3(s, centre):
    """The 3x3 grave plot (the hoist's pad, scaled): polished blackstone corner stones, a deepslate tile kerb, and
    `centre` in the middle."""
    for (x, z) in CORNERS3:
        s.set(x, 0, z, "polished_blackstone_bricks")
    for (x, z) in EDGES3:
        s.set(x, 0, z, "deepslate_tiles")
    s.set(1, 0, 1, centre)


def grave_corbel(s, x, y, z, name="deepslate_brick_stairs"):
    """A corner corbel: an upside-down stair stepping back toward the middle on the x axis (its soffit faces out)."""
    _gr_stairs(s, x, y, z, name, "east" if x == 0 else "west", "top")


def grave_holds(s, what):
    """Nothing on the building pops off once built bottom-up (design/building_detail.md): standing lanterns, candles
    and torches need a floor that holds their centre, hanging lanterns a ceiling or a chain; nothing hangs on a wall
    (wall torches, buttons, wall skulls, ladders), and no campfire sits on hay (it would flip signal_fire)."""
    for (x, y, z), i in s.blocks.items():
        n, props = s.palette[i][0], dict(s.palette[i][1])
        below, bp = _gr_block(s, (x, y - 1, z))
        if n in ("lantern", "soul_lantern") and props.get("hanging") == "true":
            above, ap = _gr_block(s, (x, y + 1, z))
            ok = above is not None and (above == "chain" or above.endswith(("_wall", "_fence")) or above == "iron_bars"
                                        or (above.endswith("_slab") and ap.get("type") in ("bottom", "double"))
                                        or (above.endswith("_stairs") and ap.get("half") == "bottom")
                                        or not any(t in above for t in _GR_THIN))
            assert ok, f"{what}: the hanging lantern at {(x, y, z)} has nothing to hang from"
        elif n in ("lantern", "soul_lantern", "soul_torch", "torch") or n.endswith("candle"):
            ok = below is not None and (below.endswith(("_wall", "_fence")) or below in ("chain", "iron_bars")
                                        or (below.endswith("_slab") and bp.get("type") in ("top", "double"))
                                        or (below.endswith("_stairs") and bp.get("half") == "top")
                                        or not any(t in below for t in _GR_THIN))
            assert ok, f"{what}: the {n} at {(x, y, z)} has no floor"
        assert not ("wall_" in n or n.endswith(("_button", "ladder"))), f"{what}: {n} at {(x, y, z)} hangs on a wall"
        if n.endswith("campfire"):
            assert below != "hay_block", f"{what}: a campfire on hay at {(x, y, z)} would signal"


# WindGenerator.MOTIONS "_dark": the soul-bell swings in this empty cell, hung from the bell beam over it
GRAVE_BELL = (1, 9, 1)


def grave_wind_generator():
    """The Soul-Bell Campanile (design/lively_buildings.md; the soul hoist's pillar grown into a bell tower). 3 x 16 x 3:
    a blackstone plinth with chamfered sills; four basalt corner piers with chiseled capitals round lantern niches on
    the camera's faces and barred lancets on the back ones, pointed heads of upside-down stairs; a fire waist - soul
    braziers on the back pier heads (BuildingAmbienceClientEvents' soul spots), soul lanterns on the front ones - under
    a corbelled capital and a tiled abacus; an open belfry on blackstone colonnettes, black skulls on the camera's sills
    and iron railings in the back openings, where the bone bell swings (WindGenerator.MOTIONS "_dark" at GRAVE_BELL,
    drawn by WindmillRenderClientEvents; its cell and four neighbours stay empty) from a stripped dark oak beam between
    pointed heads; then a spire of shrinking tiles - skull-tipped pinnacles, open lucarnes with soul lanterns on the
    camera's faces, a tiled core, a tile-wall cap and a blackstone needle."""
    s = Structure(3, 16, 3)
    grave_pad3(s, "soul_soil")
    # y1 the plinth: corner stones, chamfered sills
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "cracked_polished_blackstone_bricks" if (x, z) == (0, 2) else "polished_blackstone_bricks")
    for (x, z), f in _GR_IN.items():
        _gr_stairs(s, x, 1, z, "polished_blackstone_brick_stairs", f)
    s.set(1, 1, 1, "polished_blackstone_bricks")
    # y2-4 the shaft: basalt corner piers with chiseled capitals; on the camera's faces lantern niches (a soul lantern
    # on the sill, an upside-down stair for the pointed head), on the back faces barred lancets
    for (x, z) in CORNERS3:
        for y in (2, 3):
            s.set(x, y, z, "polished_basalt", axis="y")
        s.set(x, 4, z, "chiseled_polished_blackstone")
    for (x, z), f in _GR_IN.items():
        s.set(x, 2, z, "deepslate_bricks" if (x, z) in ((0, 1), (1, 2)) else "cracked_deepslate_bricks")
        if (x, z) in ((0, 1), (1, 2)):
            s.set(x, 3, z, "soul_lantern", **LANTERN)
        else:
            s.set(x, 3, z, "iron_bars")
        _gr_stairs(s, x, 4, z, "deepslate_brick_stairs", f, "top")
    for y in (2, 3, 4):
        s.set(1, y, 1, "deepslate_bricks")
    # y5 the fire waist: soul braziers on the back piers, soul lanterns on the front ones, a brick cross between
    for (x, z) in [(0, 0), (2, 0)]:
        s.set(x, 5, z, "soul_campfire", lit="true", facing="north", signal_fire="false", **DRY_)
    for (x, z) in [(0, 2), (2, 2)]:
        s.set(x, 5, z, "soul_lantern", **LANTERN)
    for (x, z) in EDGES3:
        s.set(x, 5, z, "deepslate_bricks" if (x, z) in ((0, 1), (1, 2)) else "cracked_deepslate_bricks")
    s.set(1, 5, 1, "deepslate_bricks")
    # y6 the capital: corner corbels over the fires, an undercut band; y7 the abacus the belfry stands on
    for (x, z) in CORNERS3:
        grave_corbel(s, x, 6, z)
    for (x, z), f in _GR_IN.items():
        _gr_stairs(s, x, 6, z, "deepslate_brick_stairs", f, "top")
    s.set(1, 6, 1, "deepslate_bricks")
    for (x, z) in CORNERS3:
        s.set(x, 7, z, "polished_blackstone_bricks")
    for (x, z) in EDGES3:
        s.set(x, 7, z, "polished_deepslate")
    s.set(1, 7, 1, "deepslate_tiles")
    # y8-9 the open belfry: blackstone colonnettes, skulls on the camera's sills, railings in the back openings
    for (x, z) in CORNERS3:
        s.set(x, 8, z, "polished_blackstone_wall")
        s.set(x, 9, z, "polished_blackstone_wall")
    s.set(0, 8, 1, "wither_skeleton_skull", rotation=str(TO_CAMERA))
    s.set(1, 8, 2, "wither_skeleton_skull", rotation=str(TO_CAMERA))
    for (x, z) in [(1, 0), (2, 1)]:
        s.set(x, 8, z, "iron_bars")                      # railings in the back openings
    # y10 the bell beam, the openings' pointed heads, chiseled corners
    s.set(1, 10, 1, "stripped_dark_oak_log", axis="x")
    for (x, z), f in _GR_IN.items():
        _gr_stairs(s, x, 10, z, "deepslate_brick_stairs", f, "top")
    for (x, z) in CORNERS3:
        s.set(x, 10, z, "chiseled_polished_blackstone")
    # y11-15 the spire: skull-tipped pinnacles; on the camera's faces open lucarnes with soul lanterns (the open cell
    # over the south one is where the ambience's spire soul flames gutter, (1.5, 12.0, 2.5)), tile slopes behind
    for (x, z) in CORNERS3:
        s.set(x, 11, z, "deepslate_tile_wall")
        s.set(x, 12, z, "skeleton_skull" if (x, z) in ((0, 2), (2, 0)) else "wither_skeleton_skull",
              rotation=str(TO_CAMERA))
    for (x, z) in [(0, 1), (1, 2)]:
        s.set(x, 11, z, "soul_lantern", **LANTERN)
    for (x, z) in [(1, 0), (2, 1)]:
        _gr_stairs(s, x, 11, z, "deepslate_tile_stairs", _GR_IN[(x, z)])
        _gr_stairs(s, x, 12, z, "deepslate_tile_stairs", _GR_IN[(x, z)])
    s.set(1, 11, 1, "deepslate_tiles")
    s.set(1, 12, 1, "deepslate_tiles")
    s.set(1, 13, 1, "deepslate_tiles")
    s.set(1, 14, 1, "deepslate_tile_wall")
    s.set(1, 15, 1, "polished_blackstone_wall")
    _gr_connect(s)
    grave_holds(s, "wind_generator_dark")
    bx, by, bz = GRAVE_BELL
    assert (bx, by, bz) not in s.blocks and (bx, by + 1, bz) in s.blocks, "the soul-bell needs its cell and its beam"
    for dx, dz in _GR_SIDES.values():
        assert (bx + dx, by, bz + dz) not in s.blocks, "the soul-bell would swing into a block"
    # BuildingAmbienceClientEvents.ENERGY_SPOTS "_dark": soul flames over the two braziers and the spire lantern
    for (x, y, z) in [(0, 5, 0), (2, 5, 0)]:
        assert _gr_block(s, (x, y, z))[0] == "soul_campfire", f"the ambience's brazier at {(x, y, z)} is gone"
    assert _gr_block(s, (1, 11, 2))[0] == "soul_lantern" and (1, 12, 2) not in s.blocks, "the spire's soul spot"
    return s


def grave_energy_converter():
    """The Soul Crucible: the hoist's pillar as a brazier column. 3 x 8 x 3: a firebox - soul fire burning in the
    mouths on the west and east faces under slab lintels, lit blast furnaces in the south and north under hoods - and a
    plinth stepping back to the waist: a cross of bone round a basalt core with soul lanterns hung in its corners under
    a flared deepslate capital; on it the brazier - a soul campfire on soul soil inside a low blackstone rim, corner
    posts carrying soul lanterns, a skull and a cluster of lit black candles (low on the front post, so the fire stays
    in view)."""
    s = Structure(3, 8, 3)
    grave_pad3(s, "soul_soil")
    # y1 the firebox
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "cracked_polished_blackstone_bricks" if (x, z) == (2, 0) else "polished_blackstone_bricks")
    for (x, z) in [(0, 1), (2, 1)]:
        s.set(x, 1, z, "soul_campfire", lit="true", facing="south", signal_fire="false", **DRY_)
    s.set(1, 1, 2, "blast_furnace", facing="south", lit="true")
    s.set(1, 1, 0, "blast_furnace", facing="north", lit="true")
    s.set(1, 1, 1, "polished_blackstone_bricks")
    # y2 the plinth steps back: corner slopes, slab lintels over the fire mouths, hoods over the furnaces
    for (x, z) in CORNERS3:
        _gr_stairs(s, x, 2, z, "polished_blackstone_brick_stairs", "east" if x == 0 else "west")
    for (x, z) in [(0, 1), (2, 1)]:
        _gr_slab(s, x, 2, z, "polished_blackstone_brick_slab", "top")
    for (x, z) in [(1, 0), (1, 2)]:
        _gr_stairs(s, x, 2, z, "polished_blackstone_brick_stairs", _GR_IN[(x, z)], "top")
    s.set(1, 2, 1, "polished_blackstone_bricks")
    # y3-4 the waist: a cross of bone round a basalt core, soul lanterns hung in its corners under the capital
    for y in (3, 4):
        for (x, z) in EDGES3:
            s.set(x, y, z, "bone_block", axis="y")
        s.set(1, y, 1, "polished_basalt", axis="y")
    for (x, z) in CORNERS3:
        s.set(x, 4, z, "soul_lantern", hanging="true", waterlogged="false")
    # y5 the capital flares out over the lanterns
    for (x, z) in CORNERS3:
        s.set(x, 5, z, "cracked_deepslate_bricks" if (x, z) == (2, 0) else "deepslate_bricks")
    for (x, z), f in _GR_IN.items():
        _gr_stairs(s, x, 5, z, "deepslate_brick_stairs", f, "top")
    s.set(1, 5, 1, "soul_soil")
    # y6 the brazier: a low blackstone rim round the soul fire, blackstone posts on the corners; y7 soul lanterns and a
    # skull on the back posts, a cluster of black candles on the front one (low, so the fire stays in view)
    for (x, z) in EDGES3:
        _gr_slab(s, x, 6, z, "polished_blackstone_brick_slab")
    for (x, z) in CORNERS3:
        s.set(x, 6, z, "polished_blackstone_wall")
    s.set(1, 6, 1, "soul_campfire", lit="true", facing="south", signal_fire="false", **DRY_)
    for (x, z) in [(0, 0), (2, 2)]:
        s.set(x, 7, z, "soul_lantern", **LANTERN)
    s.set(2, 7, 0, "skeleton_skull", rotation=str(TO_CAMERA))
    s.set(0, 7, 2, "black_candle", candles="3", lit="true", **DRY_)
    _gr_connect(s)
    grave_holds(s, "energy_converter_dark")
    return s


def grave_storage_vault():
    """The Ossuary Crypt: a buttressed blackstone strongroom under a tiled gable with a bone spine. 3 x 8 x 3: corner
    piers with chiseled capitals and a corbelled deepslate frieze over them; on the south gable end (a camera side) a
    church front - an iron door at the back of a deep doorway, a soul lantern hung in its head from a gilded tympanum
    (the treasury), a chiseled apex under the bone ridge's end and a black skull, between two pinnacles carrying soul
    lanterns; on the other three faces a barrel under a niche where a skull sits before the ossuary's stacked bones, an
    upside-down stair for its head; deepslate tile slopes."""
    s = Structure(3, 8, 3)
    grave_pad3(s, "deepslate_tiles")
    # y1-3 the corner piers
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "cracked_polished_blackstone_bricks" if (x, z) == (0, 2) else "polished_blackstone_bricks")
        s.set(x, 2, z, "polished_blackstone_bricks")
        s.set(x, 3, z, "chiseled_polished_blackstone")
    # the south front: the iron door deep in its reveal, a soul lantern hung in the head from the tympanum
    s.set(1, 1, 2, "iron_door", facing="south", half="lower", hinge="left", open="false", powered="false")
    s.set(1, 2, 2, "iron_door", facing="south", half="upper", hinge="left", open="false", powered="false")
    s.set(1, 3, 2, "soul_lantern", hanging="true", waterlogged="false")
    s.set(1, 4, 2, "gilded_blackstone")
    # the other faces: a barrel, over it a niche with a skull before the stacked bones, its head an upside-down stair
    for (x, z), skull in [((0, 1), "wither_skeleton_skull"), ((1, 0), "skeleton_skull"),
                          ((2, 1), "wither_skeleton_skull")]:
        s.set(x, 1, z, "barrel", facing=_GR_OUT[(x, z)], open="false")
        s.set(x, 2, z, skull, rotation=str(TO_CAMERA))
        _gr_stairs(s, x, 3, z, "deepslate_brick_stairs", _GR_IN[(x, z)], "top")
        _gr_stairs(s, x, 4, z, "deepslate_brick_stairs", _GR_IN[(x, z)], "top")   # the frieze's corbels
    s.set(1, 1, 1, "bone_block", axis="y")
    s.set(1, 2, 1, "bone_block", axis="x")                # the ossuary: bone ends behind the west niche
    s.set(1, 3, 1, "deepslate_bricks")
    # y4 the frieze: deepslate brick blocks over the capitals, corbels between, a tiled ceiling
    for (x, z) in CORNERS3:
        s.set(x, 4, z, "cracked_deepslate_bricks" if (x, z) == (2, 0) else "deepslate_bricks")
    s.set(1, 4, 1, "deepslate_tiles")
    # y5 the tiled slopes, the pinnacles flanking the front, the gable's chiseled apex; y6 the bone spine
    for z in (0, 1):
        _gr_stairs(s, 0, 5, z, "deepslate_tile_stairs", "east")
        _gr_stairs(s, 2, 5, z, "deepslate_tile_stairs", "west")
    for x in (0, 2):
        s.set(x, 5, 2, "polished_blackstone_wall")
        s.set(x, 6, 2, "soul_lantern", **LANTERN)
    s.set(1, 5, 2, "chiseled_polished_blackstone")
    s.set(1, 5, 1, "deepslate_tiles")
    s.set(1, 5, 0, "deepslate_bricks")
    for z in range(3):
        s.set(1, 6, z, "bone_block", axis="z")
    s.set(1, 7, 2, "wither_skeleton_skull", rotation=str(TO_CAMERA))
    s.set(1, 7, 0, "skeleton_skull", rotation=str(TO_CAMERA))
    _gr_connect(s)
    grave_holds(s, "storage_vault_dark")
    return s


# -- Horde ----------------------------------------------------------------------------------------------------
RUST = "waxed_exposed_cut_copper"          # rust-brown, and waxed so it stays that colour all match


def horde_pad5(s):
    """5x5 war-camp yard: polished blackstone brick kerb, nether brick floor, blackstone corners, a magma heart."""
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            s.set(x, 0, z, "blackstone" if corner else "polished_blackstone_bricks" if edge else "nether_bricks")
    s.set(2, 0, 2, "magma_block")


def horde_rig_base(s):
    """The extractor family's ground floor: bone spikes (bone posts with pale end-rod tips) on the four corners,
    a nether-brick-fence derrick with chains swinging under a rusted copper platform, a lit blast furnace on the
    camera-facing west side, barrels, and a skull impaled on a post at the back."""
    horde_pad5(s)
    for (x, z) in CORNERS5:
        s.set(x, 1, z, "bone_block", axis="y")
        s.set(x, 2, z, "bone_block", axis="y")
        s.set(x, 3, z, "end_rod", **UP)
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        for y in (1, 2, 3):
            s.set(x, y, z, "nether_brick_fence", **DRY_)
    for (x, z) in [(2, 1), (2, 3), (1, 2), (3, 2)]:
        s.set(x, 3, z, "chain", axis="y", **DRY_)
    # the platform: rusted copper slabs with full blocks on two corners to carry lanterns
    for x in range(1, 4):
        for z in range(1, 4):
            if (x, z) == (2, 2):
                continue
            if (x, z) in ((1, 3), (3, 1)):
                s.set(x, 4, z, RUST)
                s.set(x, 5, z, "lantern", **LANTERN)
            else:
                s.set(x, 4, z, RUST + "_slab", type="bottom", **DRY_)
    # a blackstone kerb in the four slots between the fence posts at the drill's foot: open, each slot was a hollow a
    # unit stepped down into off the furnace or a barrel and could never jump out of under the chain above it
    for (x, z) in [(2, 1), (2, 3), (1, 2), (3, 2)]:
        s.set(x, 1, z, "polished_blackstone_brick_slab", type="bottom", **DRY_)
    s.set(0, 1, 2, "blast_furnace", facing="west", lit="true")
    s.set(4, 1, 2, "barrel", facing="east", open="false")
    s.set(2, 1, 4, "nether_brick_fence", **DRY_)
    s.set(2, 2, 4, "skeleton_skull", rotation="0")
    s.set(2, 1, 0, "barrel", facing="up", open="false")


def horde_metal_extractor():
    """T1 'war rig': a fence derrick over a magma-hearted yard, bone spikes on the corners, chains, a rusted copper
    platform with lanterns, the drill a blackstone column with a magma bit and a skull-topped mast. 5 x 7 x 5."""
    s = Structure(5, 7, 5)
    horde_rig_base(s)
    s.set(2, 1, 2, "polished_blackstone")
    s.set(2, 2, 2, "polished_blackstone")
    s.set(2, 3, 2, "polished_blackstone")
    s.set(2, 4, 2, "gilded_blackstone")
    s.set(2, 5, 2, "nether_brick_fence", **DRY_)
    s.set(2, 6, 2, "skeleton_skull", rotation=str(TO_CAMERA))
    return s


def horde_metal_extractor_t2():
    """T2 'rust furnace': the rig carries a copper smelting stack - a waxed copper chimney flared with rusted stairs,
    banded in red nether brick, horned with bone spikes at its foot and a magma mouth with a campfire smoking at the
    top. The stack's waxed_copper_block is the tier 2 marker. 5 x 11 x 5."""
    s = Structure(5, 11, 5)
    horde_rig_base(s)
    s.set(2, 1, 2, "magma_block")
    s.set(2, 2, 2, "polished_blackstone")
    s.set(2, 3, 2, "polished_blackstone")
    s.set(2, 4, 2, "waxed_copper_block")         # tier 2 marker (getUpgradeLevel)
    for (x, z) in [(1, 1), (3, 3)]:              # bone horns on the platform's open corners
        s.set(x, 5, z, "bone_block", axis="y")
        s.set(x, 6, z, "end_rod", **UP)
    for (x, z, f) in [(2, 1, "south"), (2, 3, "north"), (1, 2, "east"), (3, 2, "west")]:
        s.set(x, 5, z, RUST + "_stairs", facing=f, half="bottom", shape="straight", **DRY_)
    s.set(2, 5, 2, "waxed_copper_block")
    s.set(2, 6, 2, "red_nether_bricks")
    s.set(2, 7, 2, "waxed_cut_copper")
    for (x, z, f) in [(2, 1, "north"), (2, 3, "south"), (1, 2, "west"), (3, 2, "east")]:
        s.set(x, 8, z, "nether_brick_stairs", facing={"north": "south", "south": "north", "west": "east",
                                                      "east": "west"}[f], half="top", shape="straight", **DRY_)
        s.set(x, 7, z, "chain", axis="y", **DRY_)
    s.set(2, 8, 2, "magma_block")
    s.set(2, 9, 2, "campfire", lit="true", facing="north", signal_fire="false", **DRY_)
    s.set(2, 9, 3, "skeleton_skull", rotation=str(TO_CAMERA))
    s.set(1, 9, 2, "skeleton_skull", rotation="4")
    return s


def horde_wind_generator():
    """The Smoke-Belching War Mill (design/lively_buildings.md): a blackstone-cornered nether brick tower with forge glow
    in its windows, a rusted copper band, the bone hub at y=4 (WindmillRenderClientEvents turns brown HIDE sails on
    BONE spars round it), lanterns hung under the front fence posts, a smoker bellows on the camera side, and a
    blackstone chimney up the back corner belching campfire smoke over the bone-spiked roof and the skull totem.
    The chimney stands at x=2, z=2: the tilted blades sweep x 1.1-1.9 and z < 1.5, so they never touch it.
    3 x 10 x 3."""
    s = Structure(3, 10, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_blackstone")
    s.set(1, 0, 1, "magma_block")
    for (x, z) in CORNERS3:
        for y in range(1, 4 if z == 0 else 5):
            s.set(x, y, z, "polished_blackstone_bricks")
    for y in (1, 2, 3):
        for (x, z) in EDGES3 + [(1, 1)]:
            s.set(x, y, z, RUST if y == 3 and (x, z) != (1, 1) else "nether_bricks")
    s.set(1, 2, 2, "shroomlight")                # forge-glow out of the windows the camera sees
    s.set(0, 2, 1, "shroomlight")
    s.set(0, 1, 1, "smoker", facing="west", lit="true")   # the bellows' fire mouth
    s.set(1, 4, 0, "bone_block", axis="z")      # <- rotor hub (WindGenerator.SAIL_HUBS)
    for (x, z) in [(1, 1), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 4, z, "nether_bricks")
    for x in (0, 2):
        s.set(x, 4, 0, "lantern", hanging="true", waterlogged="false")   # hung under the fence posts
    for (x, z) in [(0, 0), (2, 0), (0, 2)]:
        for y in (5, 6):
            s.set(x, y, z, "nether_brick_fence", **DRY_)
    for y in range(5, 9):                         # the chimney stack up the back corner
        s.set(2, y, 2, "polished_blackstone_bricks")
    s.set(2, 9, 2, "campfire", **CAMPFIRE)        # belches smoke
    for (x, z) in EDGES3:
        s.set(x, 7, z, "nether_brick_stairs", facing={(1, 0): "south", (0, 1): "east", (2, 1): "west",
                                                      (1, 2): "north"}[(x, z)], half="bottom", shape="straight",
              **DRY_)
    for (x, z) in [(0, 0), (2, 0), (0, 2)]:
        s.set(x, 7, z, "bone_block", axis="y")
        s.set(x, 8, z, "end_rod", **UP)
    s.set(1, 7, 1, "red_nether_bricks")
    s.set(1, 8, 1, "bone_block", axis="y")
    s.set(1, 9, 1, "skeleton_skull", rotation=str(TO_CAMERA))
    return s


def horde_energy_converter():
    """A slag forge: blackstone posts with bone spikes, chains in the bays, a lit blast furnace under a magma bed with
    a campfire smoking off it, framed by a rusted copper slab rim. 3 x 5 x 3."""
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_blackstone")
    s.set(1, 0, 1, "magma_block")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "polished_blackstone_bricks")
        s.set(x, 2, z, "polished_blackstone_bricks")
        s.set(x, 3, z, "bone_block", axis="y")
        s.set(x, 4, z, "end_rod", **UP)
    s.set(1, 1, 1, "blast_furnace", facing="west", lit="true")
    s.set(1, 2, 1, "magma_block")
    s.set(1, 3, 1, "campfire", lit="true", facing="north", signal_fire="false", **DRY_)
    for (x, z) in EDGES3:
        s.set(x, 1, z, "nether_brick_slab", type="bottom", **DRY_)
        s.set(x, 2, z, "chain", axis="y", **DRY_)
        s.set(x, 3, z, RUST + "_slab", type="bottom", **DRY_)
    return s


def horde_storage_vault():
    """The plunder pile: blackstone posts with skulls, barrels on every face, rusted copper rim and a heap of raw and
    gilded gold open to the sky, a lantern on the camera-facing corner. 3 x 5 x 3."""
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_blackstone")
    s.set(1, 0, 1, "nether_bricks")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "polished_blackstone_bricks")
        s.set(x, 2, z, "polished_blackstone_bricks")
        s.set(x, 3, z, "bone_block", axis="y")
    for (x, z, f) in [(1, 0, "north"), (0, 1, "west"), (2, 1, "east"), (1, 2, "south")]:
        s.set(x, 1, z, "barrel", facing=f, open="false")
        s.set(x, 2, z, RUST + "_slab", type="bottom", **DRY_)
    s.set(1, 1, 1, "gilded_blackstone")
    s.set(1, 2, 1, "raw_gold_block")
    s.set(1, 3, 1, "gold_block")                 # the top of the heap catches the eye from the camera
    for (x, z, r) in [(2, 0, "8"), (2, 2, "0")]:
        s.set(x, 4, z, "skeleton_skull", rotation=r)
    s.set(0, 4, 0, "skeleton_skull", rotation="4")
    s.set(0, 4, 2, "lantern", **LANTERN)
    return s


BESPOKE_ECONOMY = {
    "": (sun_metal_extractor, sun_metal_extractor_t2, sun_wind_generator, sun_energy_converter, sun_storage_vault),
    "_dark": (grave_metal_extractor, grave_metal_extractor_t2, grave_wind_generator, grave_energy_converter,
              grave_storage_vault),
    "_nether": (horde_metal_extractor, horde_metal_extractor_t2, horde_wind_generator, horde_energy_converter,
                horde_storage_vault),
}


# ---- faction houses (7 wide x 5 deep footprint, same as Reign of Nether's) ----
# y=0 must contain the class's startingBlockTypes (placed instantly as the foundation).

def fill_air(s):
    sx, sy, sz = s.size
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                if (x, y, z) not in s.blocks:
                    s.set(x, y, z, "air")


# Macaw's windows are axis-aligned like panes: facing is only ever "north" (x-axis walls) or "east" (z-axis).
def kingdom_house():
    """Half-timbered Tudor cottage: cobble sill, white plaster between oak posts, a thatched steep roof
    (Macaw's Roofs) with ridge cap, spruce casement windows (Macaw's Windows) and a brick chimney with a
    live hearth. Front faces -z. Modded blocks load as air if their mod is absent, so this never crashes."""
    s = Structure(7, 9, 5)
    W, D = 7, 5
    # y0: plank floor, cobble sill front/back, oak-log corner posts (startingBlockTypes: planks + oak log)
    for x in range(W):
        for z in range(D):
            s.set(x, 0, z, "oak_planks")
    for x in range(W):
        s.set(x, 0, 0, "cobblestone"); s.set(x, 0, D - 1, "cobblestone")
    for (x, z) in [(0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)]:
        s.set(x, 0, z, "oak_log", axis="y")
    s.set(3, 0, 0, "oak_planks")   # threshold
    # y1-3: timber frame - corner posts, plaster panels, a stripped-oak beam band at y3
    for y in (1, 2, 3):
        for x in range(W):
            for z in range(D):
                if not (x in (0, W - 1) or z in (0, D - 1)):
                    continue
                if x in (0, W - 1) and z in (0, D - 1):
                    s.set(x, y, z, "oak_log", axis="y")
                elif y == 3:
                    s.set(x, y, z, "stripped_oak_log", axis="x" if z in (0, D - 1) else "z")
                else:
                    s.set(x, y, z, "white_concrete")
    # open doorway under a spruce awning; casement windows on every face
    s.set(3, 1, 0, "air"); s.set(3, 2, 0, "air")
    s.set(3, 3, 0, "spruce_trapdoor", facing="north", half="top", open="false")
    for x in (1, 5):
        s.set(x, 2, 0, "mcwwindows:spruce_window", facing="north", part="base")
        s.set(x, 2, D - 1, "mcwwindows:spruce_window", facing="north", part="base")
    s.set(0, 2, 2, "mcwwindows:spruce_window", facing="east", part="base")
    s.set(W - 1, 2, 2, "mcwwindows:spruce_window", facing="east", part="base")
    # y4-6: thatched steep gable along x, ridge cap over z=2; plastered gable ends with a king post
    for x in range(W):
        s.set(x, 4, 0, "mcwroofs:thatch_steep_roof", facing="south", shape="straight", half="bottom")
        s.set(x, 4, D - 1, "mcwroofs:thatch_steep_roof", facing="north", shape="straight", half="bottom")
        s.set(x, 5, 1, "mcwroofs:thatch_steep_roof", facing="south", shape="straight", half="bottom")
        s.set(x, 5, D - 2, "mcwroofs:thatch_steep_roof", facing="north", shape="straight", half="bottom")
        s.set(x, 6, 2, "mcwroofs:thatch_top_roof", part="switched_90")
    for x in (0, W - 1):
        for z in (1, 2, 3):
            s.set(x, 4, z, "white_concrete")
        s.set(x, 5, 2, "oak_log", axis="y")
    # brick chimney up the back-right through the thatch, a lit hearth on top (real smoke)
    for y in range(4, 8):
        s.set(5, y, 3, "bricks")
    s.set(5, 8, 3, "campfire", lit="true", signal_fire="false", facing="north")
    # a lantern glowing just inside the doorway
    s.set(3, 1, 1, "lantern", hanging="false")
    fill_air(s)
    return s


def fallen_house():
    """Gothic manor: deepslate sill, dark-oak posts over deepslate-tile walls, tall blackstone gothic windows
    (Macaw's Windows), a steep deepslate roof (Macaw's Roofs) and a narrow bell spire lit by a soul lantern;
    cobwebs in the eaves. Front faces -z."""
    s = Structure(7, 11, 5)
    W, D = 7, 5
    for x in range(W):
        for z in range(D):
            s.set(x, 0, z, "spruce_planks")
    for (x, z) in [(0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)]:
        s.set(x, 0, z, "dark_oak_log", axis="y")
    for x in range(1, W - 1):
        s.set(x, 0, 0, "polished_deepslate"); s.set(x, 0, D - 1, "polished_deepslate")
    s.set(3, 0, 0, "spruce_planks")
    # y1-4: tall walls - dark oak posts, deepslate tiles, a dark-oak band under the eaves
    for y in (1, 2, 3, 4):
        for x in range(W):
            for z in range(D):
                if not (x in (0, W - 1) or z in (0, D - 1)):
                    continue
                if x in (0, W - 1) and z in (0, D - 1):
                    s.set(x, y, z, "dark_oak_log", axis="y")
                elif y == 4:
                    s.set(x, y, z, "dark_oak_planks")
                else:
                    s.set(x, y, z, "deepslate_tiles")
    # doorway with a pointed arch; tall gothic windows either side, narrow dark-oak windows elsewhere
    s.set(3, 1, 0, "air"); s.set(3, 2, 0, "air")
    s.set(3, 3, 0, "deepslate_tile_stairs", facing="north", half="top")
    for x in (1, 5):
        s.set(x, 2, 0, "mcwwindows:blackstone_brick_gothic", facing="north", part="bottom", open="false")
        s.set(x, 3, 0, "mcwwindows:blackstone_brick_gothic", facing="north", part="top", open="false")
        s.set(x, 2, D - 1, "mcwwindows:dark_oak_window", facing="north", part="base")
    s.set(0, 2, 2, "mcwwindows:dark_oak_window", facing="east", part="base")
    s.set(W - 1, 2, 2, "mcwwindows:dark_oak_window", facing="east", part="base")
    # a crooked buttress leaning on the left wall
    s.set(0, 1, 1, "cobbled_deepslate_wall"); s.set(0, 1, 3, "cobbled_deepslate_wall")
    # y5-7: steep deepslate gable with ridge cap; dark-oak gable ends
    for x in range(W):
        s.set(x, 5, 0, "mcwroofs:deepslate_steep_roof", facing="south", shape="straight", half="bottom")
        s.set(x, 5, D - 1, "mcwroofs:deepslate_steep_roof", facing="north", shape="straight", half="bottom")
        s.set(x, 6, 1, "mcwroofs:deepslate_steep_roof", facing="south", shape="straight", half="bottom")
        s.set(x, 6, D - 2, "mcwroofs:deepslate_steep_roof", facing="north", shape="straight", half="bottom")
        s.set(x, 7, 2, "mcwroofs:deepslate_top_roof", part="switched_90")
    for x in (0, W - 1):
        for (y, z) in [(5, 1), (5, 2), (5, 3), (6, 2)]:
            s.set(x, y, z, "dark_oak_planks")
    # the bell spire over the front-right, topped by a soul lantern
    for y in range(7, 10):
        s.set(5, y, 1, "deepslate_brick_wall")
    s.set(5, 10, 1, "soul_lantern", hanging="false")
    # cobwebs in the eaves, a soul lantern glowing just inside the door
    for (x, y, z) in [(1, 4, 1), (5, 4, 3), (1, 5, 2)]:
        s.set(x, y, z, "cobweb")
    s.set(3, 1, 1, "soul_lantern", hanging="false")
    fill_air(s)
    return s


# ---- Verdant Court: Heartwood Hall (capitol) and Grove (T1 lab) --------------------------------------------
# y=0 holds the classes' startingBlockTypes (moss block, mossy stone bricks). Nothing hangs: the build order runs
# bottom-up, so a hanging lantern or spore blossom would pop off before the canopy above it exists. Leaves are
# persistent, or they would decay away from the trunk.
LEAF = dict(persistent="true", distance="1", waterlogged="false")


def heartwood_hall():
    """A living-wood pavilion round a great oak trunk: moss floor ringed with mossy stone, eight oak posts carrying a
    stripped-oak beam ring, a domed canopy of azalea and flowering azalea, lanterns on mossy walls, calcite steps
    (the Court's silver) at the -z entrance and mangrove roots splaying from the trunk. 11 x 10 x 11."""
    W = 11
    s = Structure(W, 10, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "mossy_stone_bricks" if edge else "moss_block")
    for x in (c - 1, c, c + 1):
        s.set(x, 0, 0, "calcite")                   # silver steps at the entrance
    # the trunk: 3x3 oak, rising through the canopy
    for y in range(1, 9):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if y >= 7 and dx != 0 and dz != 0:
                    continue                        # rounds off the top
                s.set(c + dx, y, c + dz, "oak_log", axis="y")
    # roots splaying out from the trunk foot
    for (dx, dz) in [(-2, 0), (2, 0), (0, -2), (0, 2), (-2, -2), (2, 2), (-2, 2), (2, -2)]:
        s.set(c + dx, 1, c + dz, "mangrove_roots", waterlogged="false")
    # eight posts on the ring, with mossy walls and standing lanterns between them (the -z middle is the door)
    posts = [(1, 1), (c, 1), (W - 2, 1), (1, c), (W - 2, c), (1, W - 2), (c, W - 2), (W - 2, W - 2)]
    for (x, z) in posts:
        if (x, z) == (c, 1):
            continue
        for y in range(1, 5):
            s.set(x, y, z, "oak_log", axis="y")
    for (x, z) in [(3, 1), (W - 4, 1), (1, 3), (1, W - 4), (W - 2, 3), (W - 2, W - 4), (3, W - 2), (W - 4, W - 2)]:
        s.set(x, 1, z, "mossy_cobblestone_wall")
        s.set(x, 2, z, "lantern", hanging="false", waterlogged="false")
    # beam ring at y=5 joining the posts, spokes into the trunk
    for i in range(1, W - 1):
        s.set(i, 5, 1, "stripped_oak_log", axis="x"); s.set(i, 5, W - 2, "stripped_oak_log", axis="x")
        s.set(1, 5, i, "stripped_oak_log", axis="z"); s.set(W - 2, 5, i, "stripped_oak_log", axis="z")
    for i in range(2, c - 1):
        s.set(i, 5, c, "stripped_oak_log", axis="x"); s.set(W - 1 - i, 5, c, "stripped_oak_log", axis="x")
        s.set(c, 5, i, "stripped_oak_log", axis="z"); s.set(c, 5, W - 1 - i, "stripped_oak_log", axis="z")
    # the canopy dome: wide at y6, narrowing to a crown at y9, flowering every few leaves
    for y, r in [(6, 5.6), (7, 5.0), (8, 3.8), (9, 2.2)]:
        for x in range(W):
            for z in range(W):
                d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
                if d <= r and (x, y, z) not in s.blocks:
                    leaf = "flowering_azalea_leaves" if (x * 7 + z * 3 + y) % 5 == 0 else "azalea_leaves"
                    s.set(x, y, z, leaf, **LEAF)
    # froglights glowing through the canopy where the spokes meet the ring
    for (x, z) in [(c, 1), (1, c), (W - 2, c), (c, W - 2)]:
        s.set(x, 6, z, "verdant_froglight", axis="y")
    # flowering azalea bushes in the corners of the floor
    for (x, z) in [(2, 2), (W - 3, 2), (2, W - 3), (W - 3, W - 3)]:
        s.set(x, 1, z, "flowering_azalea")
    # detail pass (design/building_detail.md): the great trunk breaks through the dome into a small flowering crown
    # with a froglight heart (the capitol's silhouette from across the map), silver birch bands on the posts at eye
    # level, and calcite moonwell posts carrying lanterns on the four corners of the ring for the night
    s.size = (W, 13, W)
    for y in (9, 10, 11):
        s.set(c, y, c, "oak_log", axis="y")
    # leaves carry their true distance to the trunk (RoN compares placed states, vanilla recomputes the distance)
    for y, r in [(11, 1.5), (12, 1.0)]:
        for x in range(c - 2, c + 3):
            for z in range(c - 2, c + 3):
                if ((x - c) ** 2 + (z - c) ** 2) ** 0.5 <= r and (x, y, z) not in s.blocks:
                    dist = 1 if y == 11 and (x == c or z == c) else 2
                    s.set(x, y, z, leaf_for(x, y, z, 3), persistent="true", distance=str(dist), waterlogged="false")
    s.set(c, 12, c, "verdant_froglight", axis="y")
    for (x, z) in posts:
        if (x, z) != (c, 1):
            s.set(x, 3, z, "stripped_birch_log", axis="y")
    for (x, z) in [(0, 0), (W - 1, 0), (0, W - 1), (W - 1, W - 1)]:
        s.set(x, 1, z, "calcite")
        s.set(x, 2, z, "lantern", hanging="false", waterlogged="false")
    fill_air(s)
    return s


def leaf_for(x, y, z, every=4):
    """Azalea with a deterministic sprinkle of flowering azalea (pink from the RTS camera)."""
    return "flowering_azalea_leaves" if (x * 7 + z * 3 + y * 5) % every == 0 else "azalea_leaves"


def crown(s, tx, tz, layers, skip=lambda x, y, z: False, every=4):
    """A rounded azalea crown round (tx, tz): layers = [(y, radius)]. Never overwrites, clipped to the structure."""
    sx, _, sz = s.size
    for y, r in layers:
        for x in range(max(0, tx - 3), min(sx, tx + 4)):
            for z in range(max(0, tz - 3), min(sz, tz + 4)):
                if ((x - tx) ** 2 + (z - tz) ** 2) ** 0.5 <= r and (x, y, z) not in s.blocks and not skip(x, y, z):
                    s.set(x, y, z, leaf_for(x, y, z, every), **LEAF)


def grove():
    """The Court's training glade, read from the RTS camera as a RING OF TREES round an open lawn: five small oaks with
    rounded azalea crowns (flowering here and there) knit into a horseshoe canopy that leaves the glade in the middle
    open to the sky, an arched living-wood gate at -z (two trunks, rounded oak shoulders, a froglight keystone, a
    flowering crest), a calcite path running in to a silver sparring ring, two archery targets on birch posts and a
    fletching table at the back for the Thornbows, lanterns on mossy walls and ferns / flowers under the trees.
    Footprint 9 x 8 like before (placement and tests depend on it); 9 tall. Nothing hangs (the build runs bottom-up)."""
    W, H, D = 9, 9, 8
    s = Structure(W, H, D)
    gx, gz = 4, 3                                       # the glade's centre
    for x in range(W):
        for z in range(D):
            edge = x in (0, W - 1) or z in (0, D - 1)
            ring = 1.3 <= ((x - gx) ** 2 + (z - gz) ** 2) ** 0.5 <= 1.9
            s.set(x, 0, z, "mossy_stone_bricks" if edge else "calcite" if ring else "moss_block")
    for z in range(0, 2):
        s.set(gx, 0, z, "calcite")                      # the path in through the gate
    s.set(gx, 0, gz, "verdant_froglight", axis="y")     # a glow at the heart of the sparring ring
    # the gate: two trunks on the front edge, rounded shoulders, a lintel with a froglight keystone and a leafy crest
    for y in range(1, 5):
        s.set(2, y, 0, "oak_log", axis="y"); s.set(6, y, 0, "oak_log", axis="y")
    s.set(3, 4, 0, "oak_wood", axis="y"); s.set(5, 4, 0, "oak_wood", axis="y")
    for x in range(2, 7):
        s.set(x, 5, 0, "verdant_froglight" if x == gx else "oak_log", axis="y" if x == gx else "x")
    for x in range(1, 8):
        s.set(x, 6, 0, "flowering_azalea_leaves" if x in (3, 4, 5) else "azalea_leaves", **LEAF)
    s.set(gx, 7, 0, "flowering_azalea_leaves", **LEAF)
    # the ring of trees: trunks, mangrove roots at their feet, rounded crowns that stay off the glade
    trees = [(1, 2), (7, 2), (1, 6), (7, 6), (4, 6)]
    in_glade = lambda x, y, z: abs(x - gx) <= 1 and 2 <= z <= 4 and y <= 6
    for (tx, tz) in trees:
        top = 5 if (tx, tz) == (4, 6) else 4
        for y in range(1, top + 1):
            s.set(tx, y, tz, "oak_log", axis="y")
        for (dx, dz) in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
            x, z = tx + dx, tz + dz
            if 0 < x < W - 1 and 0 < z < D - 1 and (x, 1, z) not in s.blocks and (x, z) != (gx, gz) \
                    and (tx * 3 + tz + dx * 5 + dz) % 3 == 0:
                s.set(x, 1, z, "mangrove_roots", waterlogged="false")
        crown(s, tx, tz, [(top, 1.5), (top + 1, 1.9), (top + 2, 1.5), (top + 3, 0.6)], skip=in_glade)
    # knit the side crowns into one canopy so the ring reads closed from above (only the glade and gate stay open)
    for x in (0, 1, 2, 6, 7, 8):
        for y in (5, 6):
            if (x, y, 4) not in s.blocks and (y == 6 or x in (1, 7)):
                s.set(x, y, 4, leaf_for(x, y, 4), **LEAF)
    # archery targets on birch posts either side of the back tree, a fletching table between the side trees
    for x in (3, 5):
        s.set(x, 1, 6, "stripped_birch_log", axis="y")
        s.set(x, 2, 6, "target")
    s.set(7, 1, 4, "fletching_table")
    # lanterns standing on mossy walls along the sides and either side of the gate
    for (x, z) in [(0, 4), (8, 4), (1, 0), (7, 0)]:
        s.set(x, 1, z, "mossy_cobblestone_wall")
        s.set(x, 2, z, "lantern", hanging="false", waterlogged="false")
    # undergrowth: flowering azalea bushes, ferns, flowers and moss carpet on the lawn round the glade
    for (x, z, b) in [(1, 4, "flowering_azalea"), (2, 1, "fern"), (6, 1, "fern"), (2, 5, "allium"),
                      (6, 5, "oxeye_daisy"), (3, 1, "azure_bluet"), (5, 1, "cornflower"), (2, 3, "fern"),
                      (6, 3, "flowering_azalea")]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, b)
    for (x, z) in [(3, 2), (5, 4), (3, 4)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "moss_carpet")
    fill_air(s)
    return s


# ---- Verdant Court economy: living-wood takes on the shared extractor / wind generator / converter ------------
# Same footprints and tier rules as the generic builders (placement, metal-patch checks and getUpgradeLevel depend on
# them), but grown rather than built so they read as the Court's from the RTS camera instead of grass pads with a
# stone rim. Sparse like the generic ones (unlisted positions keep the terrain). Nothing hangs (bottom-up build),
# leaves are persistent, no vines (a vine placed before its supporting log pops off).

def verdant_pad(s, path=()):
    """5x5 / 3x3 moss pad: rooted-dirt corners, mossy stone brick kerb, calcite path stones."""
    sx, _, sz = s.size
    for x in range(sx):
        for z in range(sz):
            corner = x in (0, sx - 1) and z in (0, sz - 1)
            edge = x in (0, sx - 1) or z in (0, sz - 1)
            s.set(x, 0, z, "rooted_dirt" if corner else "mossy_stone_bricks" if edge else "moss_block")
    for (x, z) in path:
        s.set(x, 0, z, "calcite")


def verdant_root_claws(s, c, top):
    """Four great roots rising from the pad corners and bending in over the core at y=top, like fingers gripping it."""
    for (dx, dz) in [(-1, -1), (1, -1), (-1, 1), (1, 1)]:
        s.set(c + 2 * dx, 1, c + 2 * dz, "oak_wood", axis="y")                    # the root's foot
        s.set(c + dx, 1, c + dz, "mangrove_roots", waterlogged="false")
        for y in range(2, top):
            s.set(c + dx, y, c + dz, "oak_wood", axis="y")                         # the knuckle
    for (dx, dz, ax) in [(1, 0, "x"), (-1, 0, "x"), (0, 1, "z"), (0, -1, "z")]:
        s.set(c + dx, top, c + dz, "oak_log", axis=ax)                              # fingertips over the core


def verdant_metal_extractor():
    """T1: roots gripping a glowing ore core. Four oak roots climb from the pad's corners and close their fingertips
    round a raw-iron core banded with froglight, its glowing crown showing between them; moss carpet on the knuckles, a
    lantern stump and a fern tuft at the foot. 5 x 5 x 5. No copper anywhere (copper marks tier 2)."""
    s = Structure(5, 5, 5)
    c = 2
    verdant_pad(s, path=[(2, 0), (2, 4), (0, 2), (4, 2)])
    s.set(c, 0, c, "verdant_froglight", axis="y")
    s.set(c, 1, c, "raw_iron_block")
    s.set(c, 2, c, "verdant_froglight", axis="y")
    s.set(c, 3, c, "raw_iron_block")
    verdant_root_claws(s, c, 3)
    s.set(c, 4, c, "verdant_froglight", axis="y")       # the core's glowing crown, held up in the grip
    for (dx, dz) in [(-1, -1), (1, 1)]:
        s.set(c + dx, 3, c + dz, "moss_carpet")
    s.set(c + 1, 3, c - 1, "azalea_leaves", **LEAF)
    s.set(c - 1, 3, c + 1, "flowering_azalea_leaves", **LEAF)
    # a lantern on a mossy stump at the front edge, a fern and a flowering azalea at the other edges
    s.set(1, 1, 0, "mossy_cobblestone_wall")
    s.set(1, 2, 0, "lantern", hanging="false", waterlogged="false")
    s.set(2, 1, 3, "fern")                              # on the moss between the roots (the kerb is stone)
    s.set(3, 1, 2, "flowering_azalea")
    return s


def verdant_metal_extractor_t2():
    """T2: the claws have grown into a little elder over the core - the same four roots, a trunk rising from the grip
    banded with oxidised copper runes (the Court's silver), a flowering azalea crown with froglights glowing in it
    and a lightning rod drawing on the deep metal. 5 x 8 x 5, so T1 and T2 tell apart at a glance. Contains a
    cut_copper heart block: MetalExtractor.getUpgradeLevel() reads it as the save-proof tier marker."""
    s = Structure(5, 8, 5)
    c = 2
    verdant_pad(s, path=[(2, 0), (2, 4), (0, 2), (4, 2)])
    s.set(c, 0, c, "cut_copper")                        # tier marker, buried under the core
    s.set(c, 1, c, "raw_iron_block")
    s.set(c, 2, c, "verdant_froglight", axis="y")
    verdant_root_claws(s, c, 3)
    s.set(c, 3, c, "raw_iron_block")
    for y in (4, 5, 6):
        s.set(c, y, c, "oak_log", axis="y")
    for (dx, dz) in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
        s.set(c + dx, 4, c + dz, "oxidized_cut_copper")  # the rune band round the trunk's foot
    crown(s, c, c, [(5, 1.5), (6, 2.2), (7, 1.0)], every=3)
    for (x, y, z) in [(c + 2, 6, c), (c - 2, 6, c), (c, 6, c + 2)]:
        s.set(x, y, z, "verdant_froglight", axis="y")
    s.set(c, 7, c, "lightning_rod", facing="up", powered="false", waterlogged="false")
    s.set(1, 1, 0, "mossy_cobblestone_wall")
    s.set(1, 2, 0, "lantern", hanging="false", waterlogged="false")
    s.set(2, 1, 3, "fern")                              # on the moss between the roots (the kerb is stone)
    s.set(3, 1, 2, "flowering_azalea")
    return s


CHERRY_LEAF = dict(persistent="true", distance="1", waterlogged="false")


def verdant_wind_generator():
    """The Blossoming Spirit Tree (design/lively_buildings.md): no sails - lovish: "windmills don't need to be windmill
    shaped, like a tree that spawns flowers". A cherry trunk on a moss pad with flared roots, a pearlescent froglight
    spirit-heart glowing in a hollow of the trunk, boughs, lanterns hung under a full cherry crown (persistent leaves:
    vanilla cherry leaves drop their own petals, and WindmillRenderClientEvents adds a slow spiral of petals from the
    crown - WindGenerator.MOTIONS "_verdant" PETALS at the crown leaf (1,8,1)), pink petals carpeting the foot.
    The heart faces +z: the RTS camera looks north-east from the south-west and sees the -x and +z faces.
    3 x 11 x 3."""
    s = Structure(3, 11, 3)
    verdant_pad(s)
    s.set(1, 0, 1, "rooted_dirt")
    # flared foot: cherry wood on the plus, roots / petals / azalea / a lantern post in the corners
    for (x, z) in [(1, 0), (1, 2), (0, 1), (2, 1)]:
        s.set(x, 1, z, "cherry_wood", axis="y")
    s.set(0, 1, 0, "mangrove_roots", waterlogged="false")
    s.set(2, 1, 0, "pink_petals", facing="north", flower_amount="4")
    s.set(0, 1, 2, "flowering_azalea")
    s.set(2, 1, 2, "mossy_cobblestone_wall")
    s.set(2, 2, 2, "lantern", **LANTERN)
    for y in range(1, 8):
        s.set(1, y, 1, "cherry_log", axis="y")
    # the spirit heart glows out of the trunk's back, the face the RTS camera (south-west, looking north-east) sees
    s.set(1, 2, 2, "cherry_wood", axis="y")
    s.set(1, 3, 2, "pearlescent_froglight", axis="y")
    s.set(1, 4, 2, "cherry_wood", axis="y")
    s.set(0, 2, 1, "moss_carpet")
    # boughs reaching out under the crown
    s.set(0, 5, 1, "cherry_log", axis="x")
    s.set(2, 6, 1, "cherry_log", axis="x")
    s.set(1, 5, 0, "cherry_log", axis="z")
    # the crown: a skirt at y=6, full heads at y=7-8, a plus at y=9 and a tuft at y=10
    for x in range(3):
        for z in range(3):
            if (x, z) in [(0, 0), (2, 2), (1, 0), (0, 2)]:
                s.set(x, 6, z, "cherry_leaves", **CHERRY_LEAF)
            if (x, z) != (1, 1):
                s.set(x, 7, z, "cherry_leaves", **CHERRY_LEAF)
            s.set(x, 8, z, "cherry_leaves", **CHERRY_LEAF)
    for (x, z) in EDGES3 + [(1, 1)]:
        s.set(x, 9, z, "cherry_leaves", **CHERRY_LEAF)
    s.set(1, 10, 1, "cherry_leaves", **CHERRY_LEAF)
    # lanterns hung under the skirt, glowing among the blossom at night
    s.set(0, 5, 0, "lantern", hanging="true", waterlogged="false")
    s.set(2, 5, 2, "lantern", hanging="true", waterlogged="false")
    return s


def verdant_energy_converter():
    """A leafy bower over a glowing hearth: four oak posts roofed with a ring of azalea (flowering at the corners)
    round an open smoke hole, a lit campfire on a smoker at the centre - the fire shows from the RTS camera through
    the hole - on a froglight hearthstone, mossy slabs as the hearth's kerb and a lantern post at the front. 3 x 5 x 3."""
    s = Structure(3, 5, 3)
    verdant_pad(s)
    s.set(1, 0, 1, "verdant_froglight", axis="y")
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        for y in (1, 2, 3):
            s.set(x, y, z, "oak_log", axis="y")
        s.set(x, 4, z, "flowering_azalea_leaves", **LEAF)
    for (x, z) in [(1, 0), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 1, z, "mossy_stone_brick_slab", type="bottom", waterlogged="false")
        s.set(x, 3, z, "azalea_leaves", **LEAF)
    s.set(1, 1, 1, "smoker", facing="north", lit="true")
    s.set(1, 2, 1, "campfire", lit="true", facing="north", signal_fire="false", waterlogged="false")
    s.set(1, 1, 0, "mossy_cobblestone_wall")           # a lantern post at the front (a slab can't carry a lantern)
    s.set(1, 2, 0, "lantern", hanging="false", waterlogged="false")
    return s


def circle_of_elders():
    """The Court's T2 lab: five elder oaks standing in a ring round a moonlit well, their crowns knitted into a leafy
    halo open to the sky in the middle. Moss floor in a mossy stone border, a calcite (silver) ring and path to the -z
    entrance arch, the well a mossy rim round a water cauldron on a froglight, lanterns hung under every crown and
    mangrove roots at each trunk foot. 11 x 9 x 11."""
    W = 11
    s = Structure(W, 9, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if edge:
                s.set(x, 0, z, "mossy_stone_bricks")
            elif 1.6 <= d <= 2.4:
                s.set(x, 0, z, "calcite")              # the silver ring round the well
            else:
                s.set(x, 0, z, "moss_block")
    for z in range(0, c - 1):
        s.set(c, 0, z, "calcite")                      # silver path in from the entrance
    # the well: a froglight under a water cauldron, ringed by a mossy rim
    s.set(c, 0, c, "verdant_froglight", axis="y")
    s.set(c, 1, c, "water_cauldron", level="3")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx and dz:
                s.set(c + dx, 1, c + dz, "mossy_stone_brick_wall")
            elif dx or dz:
                s.set(c + dx, 1, c + dz, "mossy_stone_brick_slab", type="bottom", waterlogged="false")
    # five elders on a ring of radius 4 (the -z point is left open for the entrance)
    trees = [(c + 4, c - 1), (c + 3, c + 3), (c - 3, c + 3), (c - 4, c - 1), (c, c + 4)]
    for (tx, tz) in trees:
        for y in range(1, 7):
            s.set(tx, y, tz, "oak_log", axis="y")
        for (dx, dz) in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
            x, z = tx + dx, tz + dz
            if 0 < x < W - 1 and 0 < z < W - 1 and (x, 1, z) not in s.blocks:
                s.set(x, 1, z, "mangrove_roots", waterlogged="false")
        # the crown: a squat dome of azalea, flowering every few leaves
        for y, r in [(5, 1.6), (6, 2.2), (7, 1.9), (8, 1.0)]:
            for x in range(max(0, tx - 3), min(W, tx + 4)):
                for z in range(max(0, tz - 3), min(W, tz + 4)):
                    if ((x - tx) ** 2 + (z - tz) ** 2) ** 0.5 <= r and (x, y, z) not in s.blocks:
                        leaf = "flowering_azalea_leaves" if (x * 5 + z * 3 + y) % 4 == 0 else "azalea_leaves"
                        s.set(x, y, z, leaf, **LEAF)
        # a lantern hung under the crown on the side facing the well
        lx = tx + (1 if tx < c else -1 if tx > c else 0)
        lz = tz + (1 if tz < c else -1 if tz > c else 0)
        if (lx, 4, lz) not in s.blocks and (lx, 5, lz) in s.blocks:
            s.set(lx, 4, lz, "lantern", hanging="true", waterlogged="false")
    # the halo: leaves knitting the crowns together round the open middle
    for x in range(W):
        for z in range(W):
            d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if 3.4 <= d <= 4.6 and (x, 6, z) not in s.blocks and z >= 2:
                s.set(x, 6, z, "azalea_leaves", **LEAF)
    # entrance arch at -z: two stripped oak posts and a leafy lintel with a froglight keystone
    for y in range(1, 5):
        s.set(c - 2, y, 1, "stripped_oak_log", axis="y")
        s.set(c + 2, y, 1, "stripped_oak_log", axis="y")
    for x in range(c - 2, c + 3):
        s.set(x, 5, 1, "stripped_oak_log", axis="x")
        if (x, 6, 1) not in s.blocks:
            s.set(x, 6, 1, "flowering_azalea_leaves" if x == c else "azalea_leaves", **LEAF)
    s.set(c, 4, 1, "lantern", hanging="true", waterlogged="false")
    # moss carpet and a few ferns on the lawn
    for (x, z) in [(2, 3), (8, 3), (3, 7), (7, 7), (2, 6), (8, 6)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "moss_carpet")
    for (x, z) in [(1, 5), (9, 5), (5, 9)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "fern")
    fill_air(s)
    return s


def heart_of_the_wild():
    """The Court's T3 lab: a giant hollow elder tree, a living cathedral. A ring-shaped trunk of oak wood (radius ~3.5)
    rises from a mossy stone plinth with a tall pointed arch at -z; inside, a moss nave round a froglight heart and a
    moonwell basin and lanterns standing on mossy walls. Mangrove roots
    buttress the trunk like flying buttresses; four great boughs at y=11 carry a huge azalea crown (flowering every
    few leaves) with froglights glowing through it. Nothing hangs (the build order runs bottom-up). 15 x 19 x 15."""
    W, H = 15, 19
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if edge:
                s.set(x, 0, z, "mossy_stone_bricks")
            elif d <= 2.2:
                s.set(x, 0, z, "calcite")                # the silver floor of the nave
            else:
                s.set(x, 0, z, "moss_block")
    for z in range(0, c - 2):
        s.set(c, 0, z, "calcite")                        # silver path in to the arch
    s.set(c, 0, c, "verdant_froglight", axis="y")       # the heart of the wild
    s.set(c, 1, c, "water_cauldron", level="3")          # moonwell basin over the heart
    # the hollow trunk: a ring of oak wood, flaring at the foot and narrowing as it climbs
    for y in range(1, 12):
        r_out = 4.2 if y <= 2 else 3.8 if y <= 7 else 3.3
        r_in = r_out - 1.3
        for x in range(W):
            for z in range(W):
                d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
                if r_in < d <= r_out:
                    # the pointed arch at -z: 3 wide up to y=5, 1 wide at y=6
                    if z < c and abs(x - c) <= 1 and y <= (6 if x == c else 5):
                        continue
                    s.set(x, y, z, "oak_wood", axis="y")
    # the arch frame: stripped wood round the opening, a froglight keystone
    for y in range(1, 6):
        for x in (c - 2, c + 2):
            for z in range(0, c):
                if (x, y, z) in s.blocks:
                    s.set(x, y, z, "stripped_oak_wood", axis="y")
                    break
    for z in range(0, c):
        if (c, 7, z) in s.blocks:
            s.set(c, 7, z, "verdant_froglight", axis="y")
            break
    # windows of light: froglights set into the trunk wall on three sides
    for (x, z) in [(c + 4, c), (c - 4, c), (c, c + 4)]:
        for y in (4, 8):
            xx = x - (1 if x > c else -1 if x < c else 0) * (1 if y == 8 else 0)
            zz = z - (1 if z > c else 0) * (1 if y == 8 else 0)
            if (xx, y, zz) in s.blocks:
                s.set(xx, y, zz, "verdant_froglight", axis="y")
    # inside the nave: lanterns standing on mossy walls round the basin
    for (dx, dz) in [(-2, 0), (2, 0), (0, 2), (-1, 2), (1, 2)]:
        x, z = c + dx, c + dz
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "mossy_cobblestone_wall")
            s.set(x, 2, z, "lantern", hanging="false", waterlogged="false")
    # buttress roots splaying from the trunk foot to the plinth corners and sides
    for (dx, dz) in [(1, 1), (-1, 1), (1, -1), (-1, -1), (1, 0), (-1, 0), (0, 1)]:
        for i in range(4, 7):
            x, z = c + dx * i, c + dz * i
            if 0 < x < W - 1 and 0 < z < W - 1:
                y = max(1, 3 - (i - 4))
                for yy in range(1, y + 1):
                    if (x, yy, z) not in s.blocks:
                        s.set(x, yy, z, "mangrove_roots", waterlogged="false")
    # crown of the trunk: a solid wooden cap at y=12 the boughs grow from
    for x in range(W):
        for z in range(W):
            if ((x - c) ** 2 + (z - c) ** 2) ** 0.5 <= 3.3:
                s.set(x, 12, z, "oak_wood", axis="y")
    # four great boughs reaching out at y=12-13
    for (dx, dz, ax) in [(1, 0, "x"), (-1, 0, "x"), (0, 1, "z"), (0, -1, "z")]:
        for i in range(3, 7):
            s.set(c + dx * i, 12 + (1 if i >= 5 else 0), c + dz * i, "oak_log", axis=ax)
    # the crown: a huge dome of azalea from y=13, with froglights glowing through it
    for y, r in [(13, 6.6), (14, 7.0), (15, 6.6), (16, 5.6), (17, 4.0), (18, 2.2)]:
        for x in range(W):
            for z in range(W):
                d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
                if d <= r and (x, y, z) not in s.blocks:
                    leaf = "flowering_azalea_leaves" if (x * 7 + z * 3 + y) % 4 == 0 else "azalea_leaves"
                    s.set(x, y, z, leaf, **LEAF)
    for (x, z) in [(c + 4, c + 3), (c - 4, c - 3), (c - 3, c + 4), (c + 3, c - 4), (c, c)]:
        s.set(x, 15, z, "verdant_froglight", axis="y")
    # moss carpet and ferns on the lawn round the tree
    for (x, z) in [(2, 3), (12, 3), (2, 11), (12, 11), (3, 2), (11, 12)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "moss_carpet")
    for (x, z) in [(1, 7), (13, 7), (7, 13), (1, 1), (13, 13)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "fern")
    for (x, z) in [(2, 1), (12, 1)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "flowering_azalea")
    fill_air(s)
    return s


def storm_oak():
    """The Court's T2 defence: a gnarled old oak that calls lightning. A mossy stone plinth, mangrove roots clawing out
    from a flared foot, a trunk that twists as it climbs (shifting a block every couple of rows, the joints doubled so it
    reads as one living trunk), a band of oxidised copper runes round its waist (the Court's silver), two crooked limbs,
    a dark ragged crown with froglights flickering inside and a lightning rod at the very top - the bolt's source
    (StormOakPlacement strikes from the rod). Nothing hangs (the build order runs bottom-up). 7 x 15 x 7."""
    W, H = 7, 15
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            corner = x in (0, W - 1) and z in (0, W - 1)
            s.set(x, 0, z, "rooted_dirt" if corner else "mossy_stone_bricks" if edge else "moss_block")
    s.set(c, 0, c, "calcite")
    # the flared foot: 3x3 of wood, then a plus, banded with copper runes
    for y in (1, 2):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if y == 2 and dx and dz:
                    continue
                s.set(c + dx, y, c + dz, "oak_wood", axis="y")
    for (dx, dz) in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
        s.set(c + dx, 3, c + dz, "oak_wood", axis="y")
    for (dx, dz) in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
        s.set(c + dx, 4, c + dz, "oxidized_cut_copper")
    s.set(c, 4, c, "oak_wood", axis="y")
    # roots clawing out to the plinth's corners and sides
    for (dx, dz) in [(2, 2), (-2, 2), (2, -2), (-2, -2), (2, 0), (-2, 0), (0, 2), (0, -2)]:
        if (c + dx, 1, c + dz) not in s.blocks:
            s.set(c + dx, 1, c + dz, "mangrove_roots", waterlogged="false")
    for (dx, dz) in [(2, 0), (-2, 0), (0, 2)]:                 # the side roots climb the foot
        s.set(c + dx, 2, c + dz, "mangrove_roots", waterlogged="false")
    # the twisting trunk: (x, z) per row; where it shifts both columns are wood at that row
    path = {5: (c, c), 6: (c, c), 7: (c + 1, c), 8: (c + 1, c), 9: (c + 1, c + 1), 10: (c, c + 1), 11: (c, c + 1), 12: (c, c)}
    prev = (c, c)
    for y in range(5, 13):
        x, z = path[y]
        s.set(x, y, z, "oak_log", axis="y")
        if (x, z) != prev:
            s.set(prev[0], y, prev[1], "oak_wood", axis="y")
        prev = (x, z)
    # two crooked limbs: one low toward -x, one higher toward -z, each ending in a leaf knot
    for i, (x, y, z) in enumerate([(c, 8, c), (c - 1, 8, c), (c - 2, 9, c)]):
        if (x, y, z) not in s.blocks:
            s.set(x, y, z, "oak_log", axis="x")
    for (x, y, z) in [(c, 10, c), (c, 10, c - 1), (c, 11, c - 2)]:
        if (x, y, z) not in s.blocks:
            s.set(x, y, z, "oak_log", axis="z")
    # the ragged crown: dark oak leaves round the top, holes left on purpose (a storm-torn oak, not a ball)
    for y, r in [(10, 2.2), (11, 3.0), (12, 2.8), (13, 1.8)]:
        for x in range(W):
            for z in range(W):
                d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
                if d <= r and (x, y, z) not in s.blocks and (x * 5 + z * 7 + y * 3) % 7 != 0:
                    leaf = "azalea_leaves" if (x * 3 + z + y) % 5 == 0 else "dark_oak_leaves"
                    s.set(x, y, z, leaf, **LEAF)
    for (x, y, z) in [(c - 2, 10, c), (c, 11, c - 2)]:      # leaf knots at the limb ends
        for (dx, dz) in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
            p = (x + dx, y + 1, z + dz)
            if 0 <= p[0] < W and 0 <= p[2] < W and p not in s.blocks:
                s.set(*p, "dark_oak_leaves", **LEAF)
    # froglights flickering in the crown, the rod on the crown's peak
    for (x, y, z) in [(c + 2, 11, c), (c - 1, 12, c + 2), (c - 2, 11, c - 1)]:
        s.set(x, y, z, "verdant_froglight", axis="y")
    s.set(c, 13, c, "oak_log", axis="y")
    s.set(c, 14, c, "lightning_rod", facing="up", powered="false", waterlogged="false")
    # lanterns standing on mossy walls at two corners of the plinth, moss carpet and ferns
    for (x, z) in [(1, 1), (W - 2, W - 2)]:
        s.set(x, 1, z, "mossy_cobblestone_wall")
        s.set(x, 2, z, "lantern", hanging="false", waterlogged="false")
    for (x, z) in [(1, W - 2), (W - 2, 1)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "fern")
    for (x, z) in [(1, 3), (5, 3), (3, 5)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "moss_carpet")
    fill_air(s)
    return s


# ---- Tidewrought: Wreck Harbour (capitol) and Slipway (T1 lab) -----------------------------------------------
# y=0 holds the classes' startingBlockTypes (smooth sandstone, prismarine bricks). Same rules as the Court's: nothing
# hangs (the build runs bottom-up), copper is waxed and coral dead (section 5 of the plan), no water anywhere.
DRY = dict(waterlogged="false")


def wreck_harbour():
    """A beached galleon made a harbour: a sandstone apron in a prismarine kerb, the hull lying along z with its
    stern open to the -z entrance - a dark-prismarine keel strake, spruce and mangrove planking, a stripped-mangrove
    gunwale with waxed-copper fittings - a broken mainmast with its yard and a torn teal sail, a crow's nest, barrels
    and dried kelp on the deck, dead coral grown over the hull and lanterns standing on the rail. 11 x 10 x 11."""
    W = 11
    s = Structure(W, 10, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "prismarine_bricks" if edge else "smooth_sandstone")
    # the hull outline: half-width per z (stern at z=1 is open as the door, bow comes to a point at z=9)
    half = {1: 3, 2: 3, 3: 3, 4: 3, 5: 3, 6: 3, 7: 2, 8: 1, 9: 0}
    for z, hw in half.items():
        for x in (c - hw, c + hw):
            if z == 1 and abs(x - c) < 3:
                continue
            s.set(x, 1, z, "dark_prismarine")                       # the keel strake, barnacled
            s.set(x, 2, z, "spruce_planks")
            s.set(x, 3, z, "mangrove_planks")
            s.set(x, 4, z, "stripped_mangrove_log", axis="z" if hw >= 2 else "x")   # the gunwale
        if z == 1:   # stern posts either side of the door, a transom beam over it
            for y in range(1, 5):
                s.set(c - 3, y, 1, "stripped_mangrove_log", axis="y")
                s.set(c + 3, y, 1, "stripped_mangrove_log", axis="y")
            for x in range(c - 2, c + 3):
                s.set(x, 5, 1, "stripped_mangrove_log", axis="x")
            s.set(c, 6, 1, "waxed_cut_copper")                       # the stern lantern's brass mount
            s.set(c, 7, 1, "lantern", hanging="false", **DRY)
    # the bowsprit poking out over the kerb
    s.set(c, 4, 10, "stripped_spruce_log", axis="z")
    # waxed copper fittings at the gunwale corners, lanterns standing on the rail
    for (x, z) in [(c - 3, 3), (c + 3, 3), (c - 3, 6), (c + 3, 6)]:
        s.set(x, 5, z, "waxed_cut_copper")
    for (x, z) in [(c - 3, 4), (c + 3, 5)]:
        s.set(x, 5, z, "lantern", hanging="false", **DRY)
    # the broken mainmast, its yard and a torn teal sail, a crow's nest of slabs at the top
    for y in range(1, 10):
        s.set(c, y, 5, "stripped_spruce_log", axis="y")
    for x in range(c - 3, c + 3):          # one arm of the yard is snapped short
        s.set(x, 7, 5, "stripped_spruce_log", axis="x")
    for x in range(c - 2, c + 2):
        for y in (5, 6):
            if (x, y) != (c - 2, 5):       # the torn corner
                s.set(x, y, 4, "cyan_wool")
    for (dx, dz) in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
        s.set(c + dx, 8, 5 + dz, "spruce_slab", type="bottom", **DRY)
    # on deck: barrels, dried kelp bales, a chest of salvage
    for (x, z) in [(c - 2, 7), (c + 2, 2), (c - 2, 2)]:
        s.set(x, 1, z, "barrel", facing="up", open="false")
    for (x, z) in [(c + 2, 6), (c + 1, 7)]:
        s.set(x, 1, z, "dried_kelp_block")
    s.set(c + 2, 1, 3, "chest", facing="west", type="single", **DRY)
    # dead coral grown over the hull and the apron
    for (x, y, z) in [(c - 3, 1, 4), (c + 3, 1, 7), (c + 2, 1, 8), (c - 3, 2, 6)]:
        if (x, y, z) in s.blocks:
            s.set(x, y, z, "dead_tube_coral_block")
    for (x, z) in [(1, 2), (W - 2, 3), (2, W - 2), (W - 2, W - 2), (1, 8)]:
        s.set(x, 1, z, "dead_brain_coral_fan", **DRY)
    for (x, z) in [(W - 2, 1), (1, W - 3)]:
        s.set(x, 1, z, "dead_fire_coral", **DRY)
    # mooring posts at the entrance with lanterns
    for x in (1, W - 2):
        s.set(x, 1, 0, "stripped_mangrove_log", axis="y")
        s.set(x, 2, 0, "lantern", hanging="false", **DRY)
    fill_air(s)
    return s


def slipway():
    """The Tidewrought's yard: a sandstone floor in a prismarine kerb, a timber launching ramp of two mangrove rails
    running toward the -z entrance with a half-built hull on its spruce-fence cradle, a cargo crane on a waxed-copper
    foot in one corner, anchors (anvils) and barrels, dead coral and lanterns. 9 x 7 x 9."""
    W = 9
    s = Structure(W, 7, W)
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "prismarine_bricks" if edge else "smooth_sandstone")
    # the ramp rails, the cradle posts and the half-built hull on them
    for z in range(1, W - 1):
        s.set(2, 1, z, "stripped_mangrove_log", axis="z")
        s.set(6, 1, z, "stripped_mangrove_log", axis="z")
    for z in (2, 4, 6):
        s.set(3, 1, z, "spruce_fence", **DRY)
        s.set(5, 1, z, "spruce_fence", **DRY)
    for z in range(2, 7):
        s.set(4, 1, z, "dark_prismarine")                  # the keel
        s.set(3, 2, z, "spruce_planks")
        s.set(5, 2, z, "spruce_planks")
        if z % 2 == 0:
            s.set(3, 3, z, "mangrove_planks")             # ribs still showing between the strakes
            s.set(5, 3, z, "mangrove_planks")
    s.set(4, 2, 7, "spruce_planks")                       # the bow
    s.set(4, 3, 7, "stripped_spruce_log", axis="y")
    # the cargo crane: a mast on a waxed copper foot, a boom over the hull (nothing hangs from it)
    s.set(7, 1, 7, "waxed_cut_copper")
    for y in range(2, 6):
        s.set(7, y, 7, "stripped_spruce_log", axis="y")
    for x in range(4, 8):
        s.set(x, 6, 7, "stripped_spruce_log", axis="x")
    # anchors, barrels and a lantern at each side of the entrance
    s.set(1, 1, 6, "anvil", facing="north")
    s.set(1, 1, 3, "barrel", facing="up", open="false")
    s.set(7, 1, 2, "barrel", facing="up", open="false")
    s.set(7, 1, 4, "dried_kelp_block")
    for x in (1, W - 2):
        s.set(x, 1, 1, "lantern", hanging="false", **DRY)
    for (x, z) in [(1, 7), (7, 5)]:
        s.set(x, 1, z, "dead_horn_coral_fan", **DRY)
    fill_air(s)
    return s



# ---- Tidewrought T2 (slice 3): the Drydock (T2 lab) and the Lighthouse Battery (T2 defence) -------------------------
def drydock():
    """The Tidewrought's T2 yard: a graving dock on a sandstone apron in a prismarine kerb. Stepped dark-prismarine dock
    walls run along both sides, a hull sits on spruce keel blocks between them (spruce and mangrove planking, a
    stripped-mangrove rail, the stern open to the -z entrance), and a waxed-copper gantry - four legs, two cross beams and
    a travelling beam over the hull - stands over it all with a torn teal pennant. Dead coral on the walls, barrels,
    dried kelp and standing lanterns. Nothing hangs (the build order runs bottom-up). 11 x 9 x 11."""
    W, H = 11, 9
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "prismarine_bricks" if edge else "smooth_sandstone")
    # the dock walls: a dark prismarine wall with a slab step on the inside, down both long sides
    for z in range(1, W - 1):
        for x in (1, W - 2):
            s.set(x, 1, z, "dark_prismarine")
            s.set(x, 2, z, "dark_prismarine" if z % 3 else "dead_brain_coral_block")
        for x in (2, W - 3):
            s.set(x, 1, z, "dark_prismarine_slab", type="bottom", **DRY)
    # the keel blocks and the hull on them (stern open at z=1, bow narrowing at z=8..9)
    for z in (2, 4, 6, 8):
        for x in range(c - 1, c + 2):
            s.set(x, 1, z, "stripped_spruce_log", axis="x")
    half = {2: 2, 3: 2, 4: 2, 5: 2, 6: 2, 7: 2, 8: 1, 9: 0}
    for z, hw in half.items():
        for x in range(c - hw, c + hw + 1):
            s.set(x, 2, z, "dark_prismarine" if x == c else "spruce_planks")   # the keel line, then the bottom
        for x in (c - hw, c + hw):
            s.set(x, 3, z, "spruce_planks")
            s.set(x, 4, z, "mangrove_planks" if hw else "spruce_planks")
            if hw:
                s.set(x, 5, z, "stripped_mangrove_log", axis="z")             # the rail
    s.set(c, 5, 9, "stripped_spruce_log", axis="z")                         # the stem post
    # the gantry: waxed copper legs at the four corners of the dock, beams across at both ends, a travelling beam
    for (x, z) in [(1, 1), (W - 2, 1), (1, W - 2), (W - 2, W - 2)]:
        for y in range(3, 7):
            s.set(x, y, z, "waxed_cut_copper")
    for x in range(1, W - 1):
        s.set(x, 7, 1, "waxed_cut_copper")
        s.set(x, 7, W - 2, "waxed_cut_copper")
    for z in range(2, W - 2):
        s.set(c, 7, z, "waxed_cut_copper_slab", type="bottom", **DRY)
    # a torn teal pennant on the far beam, a lantern standing on each near leg-top
    s.set(c, 8, W - 2, "cyan_wool")
    s.set(c - 1, 8, W - 2, "cyan_wool")
    for x in (1, W - 2):
        s.set(x, 8, 1, "lantern", hanging="false", **DRY)
    # on the apron and the hull's deck: barrels, kelp bales, dead coral fans, lanterns by the entrance
    for (x, y, z) in [(c - 1, 3, 5), (c + 1, 3, 3)]:
        s.set(x, y, z, "barrel", facing="up", open="false")
    s.set(c + 1, 3, 6, "dried_kelp_block")
    for (x, z) in [(2, W - 2), (W - 3, W - 2)]:
        s.set(x, 2, z, "dead_tube_coral_fan", **DRY)
    for x in (2, W - 3):
        s.set(x, 2, 1, "lantern", hanging="false", **DRY)
    fill_air(s)
    return s


def lighthouse_battery():
    """The Tidewrought's T2 defence: a striped lighthouse on a sandstone apron in a prismarine kerb. A 3x3 tower of
    alternating smooth-sandstone and prismarine-brick bands, a dark-prismarine gallery with a waxed-copper swivel gun
    (a lightning rod laid on its side for the barrel), a lamp room of cyan glass round a sea lantern, and a waxed-copper
    cap with a lightning rod finial. LighthouseBatteryPlacement shines and shoots from the lamp room (the top two rows).
    Nothing hangs (the build order runs bottom-up). 5 x 14 x 5."""
    W, H = 5, 14
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "prismarine_bricks" if edge else "smooth_sandstone")
    # the striped tower: two rows of each band, a door gap on the -z face, a few dead coral at its foot
    for y in range(1, 9):
        band = "smooth_sandstone" if (y - 1) // 2 % 2 == 0 else "prismarine_bricks"
        for x in range(c - 1, c + 2):
            for z in range(c - 1, c + 2):
                if (x, z) == (c, c - 1) and y in (1, 2):
                    continue                                   # the door
                s.set(x, y, z, band)
    for (x, z) in [(0, 1), (4, 3), (1, 4)]:
        s.set(x, 1, z, "dead_fire_coral_fan", **DRY)
    s.set(c - 1, 1, 0, "lantern", hanging="false", **DRY)   # beside the door, not in it: a lantern blocks a doorway
    # the gallery: a dark prismarine floor ringing the tower, the swivel gun on the +x side
    for x in range(W):
        for z in range(W):
            s.set(x, 9, z, "dark_prismarine")
    s.set(W - 1, 10, c, "waxed_cut_copper_stairs", facing="west", half="bottom", shape="straight", **DRY)
    s.set(W - 1, 10, c - 1, "lightning_rod", facing="east", powered="false", **DRY)   # the barrel
    for (x, z) in [(0, 0), (0, W - 1), (W - 1, W - 1)]:
        s.set(x, 10, z, "prismarine_wall", up="true", east="none", west="none", north="none", south="none", **DRY)
    # the lamp room: cyan glass round a sea lantern, then the copper cap and finial
    for y in (10, 11):
        for x in range(c - 1, c + 2):
            for z in range(c - 1, c + 2):
                s.set(x, y, z, "sea_lantern" if (x, z) == (c, c) else "cyan_stained_glass")
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            s.set(x, 12, z, "waxed_cut_copper")
    s.set(c, 13, c, "lightning_rod", facing="up", powered="false", **DRY)
    fill_air(s)
    return s


def abyssal_shipyard():
    """The Tidewrought's T3 lab: a drydock under the ribcage of a drowned leviathan. A dark-prismarine apron in a
    prismarine-brick kerb; down the middle a slip of dark prismarine lit by sea lanterns set in its floor, between low
    dock walls of dark prismarine capped with waxed copper and crowned with dead coral fans and sea lanterns. Over the
    slip arch five giant bone ribs (bone blocks rising from the walls and curving in to a spine that runs the slip's
    length at the top), and at the far (+z) end the skull: a bone dome with sea-lantern eyes. A waxed-copper crane
    stands at one corner, its jib reaching over the ribs, and dead coral, dried kelp and barrels litter the apron. The
    slip's mouth is open at -z. Nothing hangs (the build order runs bottom-up). 15 x 13 x 15."""
    W, H = 15, 13
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "prismarine_bricks" if edge else "dark_prismarine")
    # the slip: lanterns in its floor every third block
    for z in range(1, W - 1):
        if z % 3 == 1:
            s.set(c, 0, z, "sea_lantern")
    # the dock walls (x = c-4 and c+4): dark prismarine, waxed-copper coping, coral fans and lanterns on top
    for z in range(2, W - 3):
        for x in (c - 4, c + 4):
            s.set(x, 1, z, "dark_prismarine")
            s.set(x, 2, z, "waxed_cut_copper" if z % 4 else "dead_brain_coral_block")
    # the ribs: rising from the walls, curving in to the spine at the top (y = 8..9)
    rib = [(4, 3), (4, 4), (4, 5), (4, 6), (3, 6), (3, 7), (3, 8), (2, 8), (2, 9), (1, 9), (1, 10)]   # (offset, y)
    for z in (3, 5, 7, 9):
        for off, y in rib:
            for x in (c - off, c + off):
                s.set(x, y, z, "bone_block", axis="y" if off >= 3 else "x")
    # the spine along the top of the ribcage, ending in the skull at +z
    for z in range(2, 11):
        s.set(c, 10, z, "bone_block", axis="z")
    # the skull: a bone dome over the slip's head with sea-lantern eyes looking down the slip (-z) and a jaw of bone
    for x in range(c - 2, c + 3):
        for y in range(3, 8):
            for z in range(10, 13):
                d = ((x - c) / 2.6) ** 2 + ((y - 5) / 2.6) ** 2 + ((z - 11) / 1.6) ** 2
                if d <= 1.0:
                    s.set(x, y, z, "bone_block", axis="y")
    for x in (c - 1, c + 1):
        s.set(x, 6, 10, "sea_lantern")
    for x in range(c - 2, c + 3):
        s.set(x, 3, 10, "bone_block", axis="x")                      # the jaw's edge
    for x in (c - 2, c + 2):
        for y in (1, 2):
            s.set(x, y, 11, "bone_block", axis="y")                  # the jaw's hinge posts down to the floor
    # lamps on the wall tops between the ribs
    for z in (4, 8):
        for x in (c - 4, c + 4):
            s.set(x, 3, z, "sea_lantern")
    # the crane: a waxed-copper mast at the +x/+z corner (behind the ribs from the RTS camera), its jib reaching over
    # the ribcage, a lightning-rod hook at the tip
    mx, mz = W - 2, W - 2
    for y in range(1, 12):
        s.set(mx, y, mz, "waxed_cut_copper")
    for x in range(c, mx):
        s.set(x, 12, mz, "waxed_cut_copper_slab", type="bottom", **DRY)
    s.set(mx, 12, mz, "waxed_cut_copper")
    s.set(c, 11, mz, "lightning_rod", facing="down", powered="false", **DRY)
    # the apron: dead coral fans, kelp bales, barrels, lanterns at the slip's mouth
    for (x, z) in [(2, 2), (W - 3, 3), (2, W - 4), (W - 3, W - 3)]:
        s.set(x, 1, z, "dead_tube_coral_block" if (x + z) % 2 else "dead_fire_coral_block")
        s.set(x, 2, z, "dead_horn_coral_fan", **DRY)
    s.set(W - 2, 1, c, "dried_kelp_block")
    s.set(W - 2, 1, c + 1, "barrel", facing="up", open="false")
    s.set(1, 1, c, "barrel", facing="up", open="false")
    for x in (c - 3, c + 3):
        s.set(x, 1, 1, "lantern", hanging="false", **DRY)
    fill_air(s)
    return s

# ---- Stormcallers: Jade Pagoda (capitol) and Lotus Academy (T1 lab) -------------------------------------------------
# y=0 holds the classes' startingBlockTypes (polished andesite, stone bricks). Rules as for the others: the build runs
# bottom-up so nothing hangs (lanterns and banners stand on full blocks), copper is WAXED, and no walls, fences or panes
# touch a neighbour (their connection states would change on placement and RoN compares placed states exactly).
def jade_pagoda():
    """A three-tier pagoda on a stone plinth: a stepped andesite platform in a stone-brick kerb, red-terracotta columns
    with red-glass paper screens on each tier (the door open to the -z entrance), jade roofs (waxed oxidised cut copper
    stairs climbing inward over a one-block overhang) with gold-block ridge ends, standing lanterns on the front corners
    and red banners on the back ones, a jade-and-gold altar inside and an end-rod finial on a gold cap. 11 x 14 x 11."""
    W, H = 11, 14
    s = Structure(W, H, W)
    c = W // 2
    JADE, JADE_ST = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs"
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "stone_bricks" if edge else "polished_andesite")
    # the stepped platform: a stair ring climbing inward, a raised andesite floor inside it
    ring_stairs(s, 1, 1, W - 2, "stone_brick_stairs")
    s.fill(2, 1, 2, W - 3, 1, W - 3, "polished_andesite")

    def tier(lo, y0, height, door):
        """Columns at the corners and mid-sides of [lo, W-1-lo], red-glass screens between them, a door gap at -z."""
        hi = W - 1 - lo
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if x not in (lo, hi) and z not in (lo, hi):
                    continue
                post = (x in (lo, hi) and z in (lo, hi)) or x == c or z == c
                for y in range(y0, y0 + height):
                    if door and z == lo and abs(x - c) <= 1 and y < y0 + 2:
                        continue   # the doorway
                    s.set(x, y, z, "red_terracotta" if post else "red_stained_glass")

    def roof(lo, y, cap_inside=True):
        """Jade eaves one block wider than the tier below, gold ridge ends on the corners, a jade deck inside."""
        hi = W - 1 - lo
        ring_stairs(s, y, lo, hi, JADE_ST)
        for (x, z) in [(lo, lo), (hi, lo), (lo, hi), (hi, hi)]:
            s.set(x, y, z, "gold_block")
        if cap_inside:
            s.fill(lo + 1, y, lo + 1, hi - 1, y, hi - 1, JADE)

    tier(2, 2, 3, door=True)        # ground floor, y 2-4
    roof(1, 5)                      # first roof over 1..9
    tier(3, 6, 2, door=False)       # second tier, y 6-7
    roof(2, 8)
    tier(4, 9, 2, door=False)       # third tier, y 9-10
    roof(3, 11)
    # the spire: a gold cap on a little jade hip roof, an end-rod finial
    ring_stairs(s, 12, 4, 6, JADE_ST)
    s.set(c, 12, c, "gold_block")
    s.set(c, 13, c, "end_rod", facing="up")
    # lanterns standing on the front ridge ends of the first roof, banners on the back ones (both face the camera side)
    for x in (1, W - 2):
        s.set(x, 6, 1, "lantern", hanging="false", **DRY_)
    for x in (1, W - 2):
        s.set(x, 6, W - 2, "red_banner", rotation=str(TO_CAMERA))
    # inside: a jade altar with a gold top and a lantern, glazed-terracotta tiles round it
    s.set(c, 2, c + 1, "emerald_block")
    s.set(c, 3, c + 1, "gold_block")
    s.set(c, 4, c + 1, "lantern", hanging="false", **DRY_)
    for (x, z) in [(c - 1, c + 1), (c + 1, c + 1)]:
        s.set(x, 2, z, "green_glazed_terracotta", facing="north")
    # stone lanterns (a lantern on a stone-brick post) either side of the entrance on the plinth
    for x in (1, W - 2):
        s.set(x, 1, 0, "stone_bricks")
        s.set(x, 2, 0, "lantern", hanging="false", **DRY_)
    fill_air(s)
    return s


def lotus_academy():
    """The Stormcallers' training court: a stone-brick wall round an andesite yard, red-terracotta corner posts with gold
    caps, a moon gate (a round-headed opening) in the red front wall at -z under a jade coping, a sparring ring of
    smooth-stone slabs with archery targets, a jade-roofed shrine at the back with a lantern and a red banner. 9 x 6 x 9."""
    W, H = 9, 6
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "stone_bricks" if edge else "polished_andesite")
    # side and back walls: stone bricks capped with slabs; corner posts of red terracotta with gold caps
    for i in range(W):
        for (x, z) in [(0, i), (W - 1, i), (i, W - 1)]:
            s.set(x, 1, z, "stone_bricks")
            s.set(x, 2, z, "stone_brick_slab", type="bottom", **DRY_)
    for (x, z) in [(0, 0), (W - 1, 0), (0, W - 1), (W - 1, W - 1)]:
        for y in (1, 2, 3):
            s.set(x, y, z, "red_terracotta")
        s.set(x, 4, z, "gold_block")
    # the moon gate: a red front wall three high with a round-headed opening in its middle, a jade coping on top
    for x in range(1, W - 1):
        for y in (1, 2, 3):
            opening = (abs(x - c) <= 1 and y <= 2) or (x == c and y == 3)
            if not opening:
                s.set(x, y, 0, "red_terracotta")
        s.set(x, 4, 0, "waxed_oxidized_cut_copper_slab", type="bottom", **DRY_)
    # the sparring ring and its targets
    for (dx, dz) in [(-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1)]:
        s.set(c + dx, 1, c + dz, "smooth_stone_slab", type="bottom", **DRY_)
    for x in (1, W - 2):
        s.set(x, 1, c, "target", power="0")
    # the shrine at the back: a gold-topped jade block under a little jade roof, a lantern and a banner beside it
    s.set(c, 1, W - 2, "emerald_block")
    s.set(c, 2, W - 2, "gold_block")
    s.set(c - 1, 1, W - 2, "red_terracotta")
    s.set(c + 1, 1, W - 2, "red_terracotta")
    s.set(c - 1, 2, W - 2, "red_terracotta")
    s.set(c + 1, 2, W - 2, "red_terracotta")
    s.set(c - 1, 3, W - 2, "waxed_oxidized_cut_copper_stairs", facing="east", half="bottom", shape="straight", **DRY_)
    s.set(c + 1, 3, W - 2, "waxed_oxidized_cut_copper_stairs", facing="west", half="bottom", shape="straight", **DRY_)
    s.set(c, 3, W - 2, "waxed_oxidized_cut_copper")
    s.set(c - 2, 1, W - 2, "lantern", hanging="false", **DRY_)
    s.set(c + 2, 1, W - 2, "red_banner", rotation=str(TO_CAMERA))
    fill_air(s)
    return s


def celestial_observatory():
    """The Stormcallers' T2 lab (slice 3): a tall three-tier pagoda tower on a stone-brick plinth - red-terracotta
    corner posts with red-glass paper screens on each tier (the door open to -z), jade roofs (waxed oxidised cut copper
    stairs climbing inward) with gold ridge ends, narrowing as they climb - crowned by a brass orrery: a copper pedestal
    on the top deck, a gold sun on it with four copper (end-rod) arms carrying lantern planets, and a lightning-rod
    finial above. Standing lanterns at the gate, red banners on the back corners. 9 x 17 x 9."""
    W, H = 9, 17
    s = Structure(W, H, W)
    c = W // 2
    JADE, JADE_ST = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs"
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "stone_bricks" if edge else "polished_andesite")
    ring_stairs(s, 1, 1, W - 2, "stone_brick_stairs")
    s.fill(2, 1, 2, W - 3, 1, W - 3, "polished_andesite")

    def tier(lo, y0, height, door):
        hi = W - 1 - lo
        for x in range(lo, hi + 1):
            for z in range(lo, hi + 1):
                if x not in (lo, hi) and z not in (lo, hi):
                    continue
                post = (x in (lo, hi) and z in (lo, hi)) or (hi - lo >= 4 and (x == c or z == c))
                for y in range(y0, y0 + height):
                    if door and z == lo and x == c and y < y0 + 2:
                        continue   # the doorway
                    s.set(x, y, z, "red_terracotta" if post else "red_stained_glass")

    def roof(lo, y):
        hi = W - 1 - lo
        ring_stairs(s, y, lo, hi, JADE_ST)
        for (x, z) in [(lo, lo), (hi, lo), (lo, hi), (hi, hi)]:
            s.set(x, y, z, "gold_block")
        s.fill(lo + 1, y, lo + 1, hi - 1, y, hi - 1, JADE)

    tier(2, 2, 3, door=True)        # ground floor 5x5, y 2-4
    roof(1, 5)                      # eaves over 1..7
    tier(2, 6, 3, door=False)       # second tier 5x5, y 6-8
    roof(1, 9)
    tier(3, 10, 2, door=False)      # third tier 3x3, y 10-11
    roof(2, 12)                     # the top deck: a jade floor at 12 over 2..6
    # the orrery: copper pedestal, gold sun, four end-rod arms with lantern planets, a lightning-rod finial
    s.set(c, 13, c, "waxed_cut_copper")
    s.set(c, 14, c, "gold_block")
    s.set(c, 15, c, "lightning_rod", facing="up", **DRY_)
    s.set(c, 16, c, "end_rod", facing="up")
    for (dx, dz, face) in [(1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")]:
        s.set(c + dx, 14, c + dz, "end_rod", facing=face)
        s.set(c + 2 * dx, 14, c + 2 * dz, "lantern", **LANTERN)
    # a copper rail round the deck's edge so the orrery reads as a platform, not a spire
    for x in range(2, W - 2):
        for z in range(2, W - 2):
            if x in (2, W - 3) or z in (2, W - 3):
                s.set(x, 13, z, "waxed_cut_copper_slab", type="bottom", **DRY_)
    # gate lanterns on the plinth, red banners on the back corners of the first roof
    for x in (1, W - 2):
        s.set(x, 1, 0, "stone_bricks")
        s.set(x, 2, 0, "lantern", **LANTERN)
    for x in (1, W - 2):
        s.set(x, 6, W - 2, "red_banner", rotation=str(TO_CAMERA))
    # inside: a star chart on the floor (glazed terracotta) and a lectern
    for (x, z) in [(c - 1, c), (c + 1, c), (c, c - 1), (c, c + 1)]:
        s.set(x, 2, z, "green_glazed_terracotta", facing="north")
    s.set(c, 2, c, "lectern", facing="north", has_book="false", powered="false")
    fill_air(s)
    return s


def heavens_gate():
    """The Stormcallers' T3 lab (slice 4): a great ceremonial gate on a stone-brick plinth. Two pairs of red-terracotta
    pillars (2x2, gold-capped) carry a wide two-tier jade roof (waxed oxidised cut copper with stair eaves) with gold
    ridge ends; over the roof's middle a gold sun disc on a copper mast with end-rod rays. A star chart of green glazed
    terracotta and gold is laid in the floor under the gate, lantern posts of red terracotta stand at the four corners,
    red banners on the back pillars. Open on every side - a gate, not a hall - so nothing can be trapped in it and the
    dragon rises beside it. 13 x 12 x 13."""
    W, H = 13, 12
    s = Structure(W, H, W)
    c = W // 2
    JADE, JADE_ST, JADE_SL = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "stone_bricks" if edge else "polished_andesite")
    # the star chart under the gate: a ring of green glazed terracotta round a gold centre, gold points at the quarters
    for (dx, dz) in [(-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1), (-2, -1), (-2, 1), (2, -1), (2, 1),
                     (-1, -2), (1, -2), (-1, 2), (1, 2)]:
        s.set(c + dx, 0, c + dz, "green_glazed_terracotta", facing="north" if dz <= 0 else "south")
    s.set(c, 0, c, "gold_block")
    for (dx, dz) in [(-3, 0), (3, 0), (0, -3), (0, 3)]:
        s.set(c + dx, 0, c + dz, "gold_block")
    # the pillars: 2x2 red-terracotta posts at x 2-3 and 9-10, straddling the gate's axis (z c-1..c), gold caps
    for x0 in (2, W - 4):
        for x in (x0, x0 + 1):
            for z in (c - 1, c):
                for y in range(1, 7):
                    s.set(x, y, z, "red_terracotta")
                s.set(x, 7, z, "gold_block")
    # the lower roof: a jade slab over the whole span at y 7 (x 1..W-2, z c-3..c+2), stair eaves on its long edges
    for x in range(1, W - 1):
        for z in range(c - 3, c + 3):
            if (x in (2, 3, W - 4, W - 3)) and z in (c - 1, c):
                continue   # the pillar caps
            s.set(x, 7, z, JADE)
    for x in range(1, W - 1):
        s.set(x, 7, c - 4, JADE_ST, facing="south", half="bottom", shape="straight", **DRY_)
        s.set(x, 7, c + 3, JADE_ST, facing="north", half="bottom", shape="straight", **DRY_)
    for z in range(c - 3, c + 3):
        s.set(0, 7, z, JADE_ST, facing="east", half="bottom", shape="straight", **DRY_)
        s.set(W - 1, 7, z, JADE_ST, facing="west", half="bottom", shape="straight", **DRY_)
    for (x, z) in [(0, c - 4), (W - 1, c - 4), (0, c + 3), (W - 1, c + 3)]:
        s.set(x, 7, z, "gold_block")
    # the upper tier: a narrower jade roof at y 9 on short red posts, gold ridge ends, slab eaves
    for x in range(3, W - 3):
        for z in (c - 2, c + 1):
            s.set(x, 8, z, "red_terracotta")
    for x in range(2, W - 2):
        for z in range(c - 2, c + 2):
            s.set(x, 9, z, JADE)
    for x in range(2, W - 2):
        s.set(x, 9, c - 3, JADE_SL, type="bottom", **DRY_)
        s.set(x, 9, c + 2, JADE_SL, type="bottom", **DRY_)
    for (x, z) in [(2, c - 3), (W - 3, c - 3), (2, c + 2), (W - 3, c + 2)]:
        s.set(x, 9, z, "gold_block")
    # the sun disc: a copper mast on the roof's middle, a gold block with end-rod rays, a lightning-rod finial
    s.set(c, 10, c, "waxed_cut_copper")
    s.set(c, 11, c, "gold_block")
    for (dx, dz, face) in [(1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")]:
        s.set(c + dx, 11, c + dz, "end_rod", facing=face)
    for (dx, dz) in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
        s.set(c + dx, 10, c + dz, "gold_block")
    # lantern posts at the four corners of the plinth, red banners on the back pillars' outer faces
    for (x, z) in [(1, 1), (W - 2, 1), (1, W - 2), (W - 2, W - 2)]:
        s.set(x, 1, z, "red_terracotta")
        s.set(x, 2, z, "red_terracotta")
        s.set(x, 3, z, "lantern", **LANTERN)
    for x in (2, W - 3):
        s.set(x, 1, c + 2, "red_banner", rotation=str(TO_CAMERA))
    fill_air(s)
    return s


# ---- Brassworks (design/brassworks_plan.md section 6) -------------------------------------------------------------------
BRASS, BRASS_ST, BRASS_SL = "waxed_cut_copper", "waxed_cut_copper_stairs", "waxed_cut_copper_slab"
BRASS_D = "waxed_exposed_cut_copper"
SOOT, SOOT_B = "polished_blackstone", "polished_blackstone_bricks"
CAMPFIRE = dict(lit="true", facing="north", signal_fire="false", waterlogged="false")


def tide_wind_generator():
    """The Tide-Mill (design/lively_buildings.md): no sails. A ship-timber mill house on a sandstone apron with a dark
    prismarine mill race along the front, mangrove posts, glass windows lit by a lantern inside, a brass band, a spruce
    hip roof with a dried-kelp patch, lanterns on the front posts and a spruce mast with a lantern; the WATERWHEEL turns
    on the west face (the one the RTS camera sees) round the mangrove axle at (0,1,1) (WindGenerator.MOTIONS "_tide":
    WindmillRenderClientEvents draws its paddles and rim, dipping into the race). 3 x 10 x 3."""
    s = Structure(3, 10, 3)
    s.fill(0, 0, 0, 2, 0, 2, "smooth_sandstone")
    for z in range(3):
        s.set(0, 0, z, "dark_prismarine")              # the mill race under the wheel
    s.set(1, 0, 1, "packed_mud")
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 4, "stripped_mangrove_log")
    s.set(0, 1, 1, "stripped_mangrove_log", axis="x")  # <- the waterwheel's axle (WindGenerator.MOTIONS)
    for (x, z) in [(1, 0), (2, 1), (1, 2)]:
        s.set(x, 1, z, "stripped_spruce_wood", axis="y")
        s.set(x, 2, z, "glass_pane", **DRY_)
        s.set(x, 3, z, "waxed_cut_copper")              # the brass band
    s.set(1, 1, 0, "dead_horn_coral_block")
    s.set(0, 2, 1, "stripped_spruce_wood", axis="y")
    s.set(0, 3, 1, "waxed_cut_copper")
    s.set(1, 1, 1, "barrel", facing="up", open="false")
    s.set(1, 2, 1, "lantern", **LANTERN)               # glows through the three windows
    s.set(1, 3, 1, "spruce_planks")
    # the hip roof: spruce stairs on the edges between the corner posts, a kelp patch at the back
    for (x, z), f in zip(EDGES3, ("south", "east", "west", "north")):
        s.set(x, 4, z, "spruce_stairs", facing=f, half="bottom", shape="straight", **DRY_)
    s.set(1, 4, 2, "dried_kelp_block")
    s.set(1, 4, 1, "spruce_planks")
    for x in (0, 2):
        s.set(x, 5, 0, "lantern", **LANTERN)           # on the front posts
    s.set(2, 5, 2, "cyan_banner", rotation=str(TO_CAMERA))
    # the mast: a planked step, fence spar, a lantern masthead
    s.set(1, 5, 1, "spruce_planks")
    for y in (6, 7, 8):
        s.set(1, y, 1, "spruce_fence", **DRY_)
    s.set(1, 9, 1, "lantern", **LANTERN)
    return s


def storm_wind_generator():
    """The Wind-Chime Pagoda (design/lively_buildings.md): no sails. Three tiers on an andesite plinth - red-lacquer
    (terracotta) posts, glowing paper screens (white glass round a glowstone core), jade eaves (WAXED oxidised copper
    stairs), copper wind-chimes (lightning rods hung point-down) at the corners of the upper tiers - and on top a jade
    vane crown that spins round the cherry hub at (1,8,1) (WindGenerator.MOTIONS "_storm" VANES), under a gold cap and
    a lightning-rod tip that sparks (BuildingAmbienceClientEvents). 3 x 11 x 3."""
    J, JS = "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs"
    s = Structure(3, 11, 3)
    for x in range(3):
        for z in range(3):
            s.set(x, 0, z, "smooth_stone" if (x, z) in CORNERS3 else "polished_andesite")
    s.set(1, 0, 1, "gold_block")
    # tier 1: lacquer posts, a stone sill, paper screens lit from the core
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 2, "red_terracotta")
    for (x, z) in EDGES3:
        s.set(x, 1, z, "chiseled_stone_bricks")
        s.set(x, 2, z, "white_stained_glass")
    s.set(1, 1, 1, "stone_bricks")
    s.set(1, 2, 1, "glowstone")
    ring_stairs(s, 3, 0, 2, JS)                         # eave 1
    s.set(1, 3, 1, "red_terracotta")
    # tier 2: a lacquer plus with a green glazed band, chimes at the corners
    for (x, z) in EDGES3 + [(1, 1)]:
        s.set(x, 4, z, "red_terracotta" if (x, z) == (1, 1) else "green_glazed_terracotta", facing="north")
    s.set(1, 4, 1, "red_terracotta")
    for (x, z) in CORNERS3:
        s.set(x, 4, z, "lightning_rod", facing="down", powered="false", **DRY_)
    ring_stairs(s, 5, 0, 2, JS)                         # eave 2
    s.set(1, 5, 1, "red_terracotta")
    # tier 3: posts on the plus, chimes again
    for (x, z) in EDGES3 + [(1, 1)]:
        s.set(x, 6, z, "red_terracotta")
    for (x, z) in CORNERS3:
        s.set(x, 6, z, "lightning_rod", facing="down", powered="false", **DRY_)
    ring_stairs(s, 7, 0, 2, JS)                         # eave 3
    s.set(1, 7, 1, J)
    s.set(1, 8, 1, "stripped_cherry_log", axis="y")     # <- the vane crown's hub (WindGenerator.MOTIONS)
    s.set(1, 9, 1, "gold_block")
    s.set(1, 10, 1, "lightning_rod", facing="up", powered="false", **DRY_)   # the sparking tip
    return s


def brass_steam_boiler():
    """The Brassworks' Steam Boiler (design/lively_buildings.md), a WindGenerator("_brass") with no sails: a riveted
    brass drum (a plus of waxed cut copper with a darker band and a gauge) on a blackstone firebox whose lit blast
    furnace glows behind bars at the front, copper steam pipes (stacked lightning rods - they never weather) up the
    corners, a piston pump, a soot chimney with a lit campfire for smoke, and a flywheel turning on the west face
    round the basalt shaft at (0,2,1) (WindGenerator.MOTIONS "_brass" WHEEL). Firebox, gauge and flywheel face -x/+z,
    the sides the RTS camera (south-west, looking north-east) sees. Steam puffs from the pipe tops come
    from BuildingAmbienceClientEvents. 3 x 9 x 3."""
    s = Structure(3, 9, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_blackstone")
    s.set(1, 0, 1, "deepslate_bricks")
    # the firebox: solid blackstone two high, the furnace mouth at the front, the flywheel shaft on the east face
    s.fill(0, 1, 0, 2, 2, 2, SOOT_B)
    s.set(1, 1, 2, "blast_furnace", facing="south", lit="true")   # +z: the face the RTS camera sees
    s.set(1, 2, 2, "iron_bars", north="false", south="false", east="false", west="false", waterlogged="false")
    s.set(1, 1, 1, "magma_block")                       # the fire behind the bars
    s.set(2, 1, 0, "piston", facing="up", extended="false")
    s.set(0, 2, 1, "polished_basalt", axis="x")         # <- the flywheel's shaft (WindGenerator.MOTIONS)
    # the drum: a plus shape three high, a darker band in the middle, copper pipes up the corners
    arms = [(1, 1), (1, 0), (1, 2), (0, 1), (2, 1)]
    for y in (3, 4, 5):
        for (x, z) in arms:
            s.set(x, y, z, BRASS_D if y == 4 else BRASS)
    s.set(1, 4, 2, "redstone_lamp", lit="false")   # the gauge
    for (x, z) in CORNERS3:
        for y in (3, 4, 5):
            s.set(x, y, z, "lightning_rod", facing="up", powered="false", waterlogged="false")
    # the top: brass slabs round a soot chimney, a lit campfire for the smoke, a lantern for the night shift
    for (x, z) in arms[1:]:
        s.set(x, 6, z, BRASS_SL, type="bottom", **DRY_)
    s.set(1, 6, 1, SOOT_B)
    s.set(1, 7, 1, SOOT_B)
    s.set(1, 8, 1, "campfire", **CAMPFIRE)
    s.set(2, 6, 2, "lantern", **LANTERN)
    return s


# ---- Geothermal Plant (design/lively_buildings.md, shared by every faction) ---------------------------------------


def geothermal_plant():
    """A geothermal plant, one structure for every faction (GeothermalPlant: only placeable with lava under its
    footprint centre). A squat basalt-and-blackstone housing on a magma floor: polished basalt corner piers, blackstone
    brick walls with iron-bar windows at y=2, and behind each window a LAVA CAULDRON (a block, never a fluid: it can't
    flow, and lights the room) under a bar grate, so the camera looks down through the bars at the lava; a blackstone
    hip roof, a basalt vent stack belching campfire smoke, corner exhaust pipes and lanterns.
    Pocket-free: every inner cell is solid, a cauldron or bars, so no unit can stand inside. 5 x 8 x 5."""
    W = 5
    s = Structure(W, 8, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "polished_blackstone" if edge else "magma_block")
    windows = [(c, 0), (0, c), (W - 1, c), (c, W - 1)]
    cauldrons = [(c, 1), (1, c), (W - 2, c), (c, W - 2)]
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            corner = x in (0, W - 1) and z in (0, W - 1)
            for y in (1, 2):
                if corner:
                    s.set(x, y, z, "polished_basalt", axis="y")
                elif edge:
                    window = (x, z) in windows and y == 2
                    s.set(x, y, z, "iron_bars" if window else "polished_blackstone_bricks", **(DRY_ if window else {}))
                elif (x, z) in cauldrons:
                    s.set(x, y, z, "lava_cauldron" if y == 1 else "iron_bars", **({} if y == 1 else DRY_))
                else:
                    s.set(x, y, z, "magma_block" if y == 1 else "polished_blackstone")
    # the hip roof: stairs round the rim, a blackstone deck, basalt piers capped with lanterns
    ring_stairs(s, 3, 0, W - 1, "polished_blackstone_brick_stairs")
    for (x, z) in [(0, 0), (W - 1, 0), (0, W - 1), (W - 1, W - 1)]:
        s.set(x, 3, z, "polished_basalt", axis="y")
        s.set(x, 4, z, "lantern", **LANTERN)
    s.fill(1, 3, 1, W - 2, 3, W - 2, "polished_blackstone_bricks")
    ring_stairs(s, 4, 1, W - 2, "blackstone_stairs")
    # the vent stack: basalt, a magma collar, the smoking campfire; exhaust pipes on the roof
    s.set(c, 4, c, "magma_block")
    s.set(c, 5, c, "polished_basalt", axis="y")
    s.set(c, 6, c, "polished_basalt", axis="y")
    s.set(c, 7, c, "campfire", **CAMPFIRE)
    for (x, z) in [(1, 1), (W - 2, W - 2)]:
        s.set(x, 5, z, "lightning_rod", facing="up", powered="false", **DRY_)
    return s


def foundry_hall():
    """The Brassworks capitol: a deepslate-brick hall on a stepped blackstone plinth, brass pilasters at the corners and
    mid-sides with orange-glass windows between them and a door open to the -z entrance, a brass cornice and a stepped
    brass dome (waxed cut copper stairs climbing inward) with a lightning-rod finial, a blackstone smokestack at the back
    corner topped by a lit campfire, a furnace mouth (a lit blast furnace between magma under iron bars) on the +x side,
    anvils, a smithing table and a blast furnace along the back wall inside, stone lanterns at the entrance and black
    banners on the back corners. 11 x 12 x 11."""
    W, H = 11, 12
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "deepslate_bricks" if edge else "polished_blackstone")
    # the stepped plinth: a stair ring climbing inward, a raised blackstone floor inside it
    ring_stairs(s, 1, 1, W - 2, "deepslate_brick_stairs")
    s.fill(2, 1, 2, W - 3, 1, W - 3, "polished_blackstone")
    # the walls on [2, 8]: brass pilasters at the corners and mid-sides, deepslate bricks between, orange-glass windows
    # at head height, a two-wide door at -z
    lo, hi = 2, W - 3
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            if x not in (lo, hi) and z not in (lo, hi):
                continue
            post = (x in (lo, hi) and z in (lo, hi)) or x == c or z == c
            for y in range(2, 6):
                if z == lo and abs(x - c) <= 1 and y < 4:
                    continue   # the doorway
                window = not post and y == 4 and (x + z) % 2 == 0
                s.set(x, y, z, BRASS if post else ("orange_stained_glass_pane" if window else "deepslate_bricks"))
    # the furnace mouth on the +x wall: a lit blast furnace flanked by magma behind iron bars (the bars stand on the
    # plinth ring outside the wall, one block out, so nothing walks into the magma)
    s.set(hi, 2, c, "blast_furnace", facing="east", lit="true")
    for z in (c - 1, c + 1):
        s.set(hi, 2, z, "magma_block")
        s.set(hi + 1, 2, z, "iron_bars", north="false", south="false", east="false", west="false", waterlogged="false")
    # the cornice: brass stairs one block out over the walls, a brass deck inside
    ring_stairs(s, 6, lo - 1, hi + 1, BRASS_ST)
    s.fill(lo, 6, lo, hi, 6, hi, BRASS)
    # the dome: stepped rings climbing to a finial
    ring_stairs(s, 7, lo + 1, hi - 1, BRASS_ST)
    s.fill(lo + 2, 7, lo + 2, hi - 2, 7, hi - 2, BRASS)
    ring_stairs(s, 8, lo + 2, hi - 2, BRASS_ST)
    s.set(c, 8, c, BRASS)
    s.set(c, 9, c, BRASS_D)
    s.set(c, 10, c, "lightning_rod", facing="up", powered="false", waterlogged="false")
    # the smokestack at the back +x corner: blackstone from the cornice up, a lit campfire on top for the smoke
    for y in range(7, H - 1):
        s.set(hi, y, hi, SOOT_B)
    s.set(hi, H - 1, hi, "campfire", **CAMPFIRE)
    # inside: the forge row along the back wall (z = hi - 1): anvil, blast furnace, smithing table, anvil
    s.set(c - 2, 2, hi - 1, "anvil", facing="north")
    s.set(c - 1, 2, hi - 1, "blast_furnace", facing="north", lit="true")
    s.set(c, 2, hi - 1, "smithing_table")
    s.set(c + 1, 2, hi - 1, "anvil", facing="north")
    s.set(c + 2, 2, hi - 1, BRASS_D)
    s.set(c + 2, 3, hi - 1, "lantern", hanging="false", **DRY_)
    # stone lanterns (a lantern on a blackstone post) either side of the entrance on the plinth, banners on the back
    # corners of the cornice
    for x in (1, W - 2):
        s.set(x, 1, 0, SOOT_B)
        s.set(x, 2, 0, "lantern", hanging="false", **DRY_)
    for x in (lo - 1, hi + 1):
        s.set(x, 7, hi + 1, "black_banner", rotation=str(TO_CAMERA))
    # detail pass (design/building_detail.md): a second, brass-banded stack on the back-west corner the camera sees
    # first, so the hall reads as a twin-stacked foundry; lanterns on the deck's free corners for the night; a
    # furnace-glow gear medallion (gold teeth round a shroomlight hub) on the back wall over the forge row
    for y in range(7, H - 1):
        s.set(lo, y, hi, BRASS if y % 3 == 0 else SOOT_B)
    s.set(lo, H - 1, hi, "campfire", **CAMPFIRE)
    for (x, z) in ((lo, lo), (hi, lo)):
        s.set(x, 7, z, "lantern", hanging="false", **DRY_)
    for dx in (-1, 0, 1):
        for y in (3, 4, 5):
            s.set(c + dx, y, hi, "shroomlight" if (dx, y) == (0, 4) else ("gold_block" if dx == 0 or y == 4 else BRASS_D))
    fill_air(s)
    return s


def machine_shop():
    """The Brassworks T1 lab: an open-sided workshop - blackstone posts at the corners and mid-sides of a deepslate kerb
    carry a brass roof (waxed cut copper with stair eaves and a slab ridge); inside, a work bench along the back wall
    (smithing table, anvil, lit blast furnace, anvil, smithing table), a crucible (a cauldron on a magma block raised on
    a post, so nothing stands in it), chain hoists under the roof over the bench, a lantern and a black banner on the
    front posts. Open on three sides, so nothing is ever trapped and the machines roll out. 9 x 6 x 9."""
    W, H = 9, 6
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "deepslate_bricks" if edge else "polished_blackstone")
    # the back wall (+z) in deepslate bricks, posts at the corners and mid-sides, brass caps
    for x in range(W):
        for y in (1, 2, 3):
            s.set(x, y, W - 1, "deepslate_bricks")
    for (x, z) in [(0, 0), (W - 1, 0), (0, c), (W - 1, c), (0, W - 1), (W - 1, W - 1), (c, W - 1)]:
        for y in (1, 2, 3):
            s.set(x, y, z, SOOT_B)
    # the roof: brass eaves one block out would leave the footprint - stairs along the edge, a brass deck, a slab ridge
    ring_stairs(s, 4, 0, W - 1, BRASS_ST)
    s.fill(1, 4, 1, W - 2, 4, W - 2, BRASS)
    for x in range(2, W - 2):
        s.set(x, 5, c, BRASS_SL, type="bottom", **DRY_)
    # the work bench along the back wall
    s.set(c - 2, 1, W - 2, "smithing_table")
    s.set(c - 1, 1, W - 2, "anvil", facing="north")
    s.set(c, 1, W - 2, "blast_furnace", facing="north", lit="true")
    s.set(c + 1, 1, W - 2, "anvil", facing="north")
    s.set(c + 2, 1, W - 2, "smithing_table")
    # chain hoists hanging from the roof over the bench
    for x in (c - 1, c + 1):
        s.set(x, 3, W - 2, "chain", axis="y", **DRY_)
    # the crucible at the -x side: a blackstone post, magma, a cauldron on top
    s.set(1, 1, c - 2, SOOT_B)
    s.set(1, 2, c - 2, "magma_block")
    s.set(1, 3, c - 2, "cauldron")
    # a lantern and a banner on the front posts
    s.set(0, 4, 0, "lantern", hanging="false", **DRY_)
    s.set(W - 1, 4, 0, "black_banner", rotation=str(TO_CAMERA))
    fill_air(s)
    return s


def assembly_works():
    """The Brassworks T2 lab: an assembly hall on a deepslate kerb - blackstone posts at the corners and along the sides
    carry a brass roof (waxed cut copper with stair eaves and a slab ridge); a deepslate back wall with orange-glass
    windows; open at the front and between the side posts, so the machines (and anything else) roll straight out. Inside,
    a gantry crane: two brass legs either side of the floor and a beam under the roof with a chain hook hanging over the
    middle; along the back wall a boiler drum (waxed copper blocks over a lit blast furnace), anvils and a smithing
    table. Two blackstone smokestacks rise from the back corners through the roof with lit campfires for the smoke,
    lanterns on the front posts, black banners on the front corners. 11 x 9 x 11."""
    W, H = 11, 9
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "deepslate_bricks" if edge else "polished_blackstone")
    # the back wall (+z): deepslate bricks with an orange-glass window band, blackstone at the corners
    for x in range(W):
        for y in range(1, 5):
            window = y == 3 and 2 <= x <= W - 3 and x % 2 == 0
            s.set(x, y, W - 1, "orange_stained_glass" if window else "deepslate_bricks")
    # posts: the four corners and every third block along the sides (open between them)
    for (x, z) in [(0, 0), (W - 1, 0), (0, 4), (W - 1, 4), (0, 7), (W - 1, 7), (0, W - 1), (W - 1, W - 1)]:
        for y in range(1, 5):
            s.set(x, y, z, SOOT_B)
    # the roof: stair eaves round the edge, a brass deck, a slab ridge along the middle
    ring_stairs(s, 5, 0, W - 1, BRASS_ST)
    s.fill(1, 5, 1, W - 2, 5, W - 2, BRASS)
    for z in range(2, W - 2):
        s.set(c, 6, z, BRASS_SL, type="bottom", **DRY_)
    # the gantry crane: brass legs at x = 1 and x = W - 2 by the middle of the floor, a beam under the roof between
    # them, a chain and a hook (an anvil block would fall: a lantern is the hook) over the centre
    for x in (1, W - 2):
        for y in range(1, 5):
            s.set(x, y, c, BRASS)
    for x in range(2, W - 2):
        s.set(x, 4, c, BRASS_D)
    s.set(c, 3, c, "chain", axis="y", **DRY_)
    s.set(c, 2, c, "lantern", hanging="true", **DRY_)
    # along the back wall: the boiler drum over a lit blast furnace, anvils and a smithing table either side
    s.set(c, 1, W - 2, "blast_furnace", facing="north", lit="true")
    s.set(c, 2, W - 2, "waxed_copper_block")
    s.set(c, 3, W - 2, "waxed_copper_block")
    s.set(c - 1, 1, W - 2, "anvil", facing="north")
    s.set(c + 1, 1, W - 2, "anvil", facing="north")
    s.set(c - 3, 1, W - 2, "smithing_table")
    s.set(c + 3, 1, W - 2, "smithing_table")
    # the smokestacks: from the back corner posts up through the roof, a lit campfire on top of each
    for x in (0, W - 1):
        for y in range(5, H - 1):
            s.set(x, y, W - 1, SOOT_B)
        s.set(x, H - 1, W - 1, "campfire", **CAMPFIRE)
    # banners on the front corners of the roof edge, lanterns on the deck behind them
    for x in (0, W - 1):
        s.set(x, 6, 0, "black_banner", rotation=str(TO_CAMERA))
    s.set(c - 2, 6, 1, "lantern", hanging="false", **DRY_)
    s.set(c + 2, 6, 1, "lantern", hanging="false", **DRY_)
    fill_air(s)
    return s


def rail_battery():
    """The Brassworks T2 defence: a rail cannon on a blackstone emplacement - a deepslate-brick plinth, a soot drum
    with brass bands, the brass turret block on top with two lightning-rod rails pointing out over the -z side, a
    glowing coil (a shroomlight in an iron-bar cage would trap nothing but read as a furnace: a redstone lamp instead).
    3 x 6 x 3, solid all the way up: nothing to stand in."""
    s = Structure(3, 6, 3)
    for x in range(3):
        for z in range(3):
            s.set(x, 0, z, "deepslate_bricks")
            s.set(x, 1, z, SOOT_B)
    for x in range(3):
        for z in range(3):
            s.set(x, 2, z, BRASS if (x + z) % 2 == 0 else SOOT)
    s.set(1, 3, 1, "redstone_lamp", lit="false")
    for (x, z) in ((0, 1), (2, 1), (1, 0), (1, 2)):
        s.set(x, 3, z, BRASS_ST, facing={(0, 1): "east", (2, 1): "west", (1, 0): "south", (1, 2): "north"}[(x, z)],
              half="bottom", shape="straight", **DRY_)
    s.set(1, 4, 1, BRASS)
    s.set(0, 4, 1, "lightning_rod", facing="north", powered="false", waterlogged="false")
    s.set(2, 4, 1, "lightning_rod", facing="north", powered="false", waterlogged="false")
    s.set(1, 5, 1, BRASS_SL, type="bottom", **DRY_)
    fill_air(s)
    return s


# ---- Tidewrought / Stormcallers / Brassworks / Verdant economy: bespoke takes (design/building_detail.md) --------
# These replace the palette-driven generic extractor / converter / vault, which gave three factions the same box in
# different colours. Same footprints and the same rules as the Sunforged / Gravebound / Horde takes above (nothing
# hangs, nothing changes by itself, T1 extractors hold no copper_block / cut_copper / waxed_copper_block, T2s carry a
# waxed_copper_block), plus: every T1 extractor block position is still a block in its T2 (the tier upgrade goes
# through BuildingPlacement.changeStructure, which only ever adds or replaces blocks - an empty T2 cell over a T1 block
# would leave the old block standing beside the new tower). check_mex_tiers() asserts it when the file is generated.
def _pad5(s, corner, ring, stair, inner, centre):
    """The 5x5 extractor pad every take shares: corner stones, a ring, outward stair skirts mid-side, an inner floor."""
    for (x, z) in CORNERS5:
        s.set(x, 0, z, corner)
    for (x, z) in [(1, 0), (3, 0), (0, 1), (0, 3), (4, 1), (4, 3), (1, 4), (3, 4)]:
        s.set(x, 0, z, ring)
    for (x, z, f) in [(2, 0, "south"), (2, 4, "north"), (0, 2, "east"), (4, 2, "west")]:
        s.set(x, 0, z, stair, facing=f, half="bottom", shape="straight", **DRY_)
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            s.set(x, 0, z, inner)
    s.set(2, 0, 2, centre)


def _hip3(s, y, x0, z0, stair, corner=None, centre=None):
    """A ring of stairs round a 3x3 at height y, climbing inward; optional corner and centre blocks."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            if (x, z) == (x0 + 1, z0 + 1):
                if centre:
                    s.set(x, y, z, centre)
                continue
            if corner and x != x0 + 1 and z != z0 + 1:
                s.set(x, y, z, corner)
                continue
            f = "east" if x == x0 else "west" if x == x0 + 2 else "south" if z == z0 else "north"
            s.set(x, y, z, stair, facing=f, half="bottom", shape="straight", **DRY_)


def _hip5(s, y, stair, corner=None):
    """The ring of stairs round the 5x5's rim at height y, climbing inward; optional corner blocks (upturned eaves)."""
    for x in range(5):
        for z in range(5):
            if x not in (0, 4) and z not in (0, 4):
                continue
            if corner and x in (0, 4) and z in (0, 4):
                s.set(x, y, z, corner)
                continue
            f = "east" if x == 0 else "west" if x == 4 else "south" if z == 0 else "north"
            s.set(x, y, z, stair, facing=f, half="bottom", shape="straight", **DRY_)


SPRUCE_FENCE = dict(east="false", north="false", south="false", west="false", waterlogged="false")
BARS = dict(east="false", north="false", south="false", west="false", waterlogged="false")


def tide_metal_extractor():
    """Tidewrought T1 'dredge rig': a ship-timber frame over a dark-prismarine well, a pulley beam with a chain and a
    cauldron bucket, a rope barrel with a lantern and a dead-coral clump on the camera's side, a teal pennant."""
    s = Structure(5, 6, 5)
    _pad5(s, "dark_prismarine", "prismarine_bricks", "prismarine_brick_stairs", "smooth_sandstone", "dark_prismarine")
    for (x, z) in CORNERS5:
        for y in (1, 2, 3):
            s.set(x, y, z, "stripped_mangrove_log", axis="y")
        s.set(x, 4, z, "stripped_mangrove_log", axis="y")
    for x in (1, 2, 3):                       # the two long beams and the pulley beam across them
        s.set(x, 4, 0, "stripped_spruce_log", axis="x")
        s.set(x, 4, 4, "stripped_spruce_log", axis="x")
    for z in (1, 3):
        s.set(2, 4, z, "spruce_slab", type="top", **DRY_)
    s.set(2, 4, 2, "waxed_cut_copper")       # the pulley block
    s.set(2, 3, 2, "chain", axis="y", **DRY_)
    s.set(2, 2, 2, "chain", axis="y", **DRY_)
    s.set(2, 1, 2, "cauldron")                # the dredge bucket, down the well
    s.set(3, 1, 4, "barrel", facing="up", open="false")
    s.set(3, 2, 4, "lantern", **LANTERN)
    s.set(1, 1, 4, "dead_brain_coral_block")
    s.set(1, 2, 4, "dead_tube_coral_fan", **DRY_)
    s.set(1, 1, 0, "blast_furnace", facing="north", lit="true")   # the donkey engine's firebox
    s.set(0, 5, 4, "cyan_banner", rotation=str(TO_CAMERA))
    return s


def tide_metal_extractor_t2():
    """Tidewrought T2 'tide pump': the dredge rig grows a copper pump tower through its pulley - a waxed copper core,
    a prismarine cornice and a sea-lantern lamp head under a dark slab cap - with copper pipes out to the frame and
    prismarine lamp posts on two corners."""
    s = tide_metal_extractor()
    s.size = (5, 9, 5)
    s.set(2, 1, 2, "waxed_copper_block")      # the pump housing replaces the bucket (the tier marker)
    s.set(2, 2, 2, "waxed_cut_copper")
    s.set(2, 3, 2, "waxed_cut_copper")
    s.set(2, 4, 2, "waxed_copper_block")
    s.set(2, 5, 2, "waxed_cut_copper")
    s.set(2, 6, 2, "sea_lantern")
    for (x, z, f) in [(1, 2, "west"), (3, 2, "east"), (2, 1, "north"), (2, 3, "south")]:
        s.set(x, 6, z, "prismarine_brick_stairs", facing=f, half="top", shape="straight", **DRY_)
    s.set(2, 7, 2, "dark_prismarine_slab", type="bottom", **DRY_)
    for x in (1, 3):                          # pipes from the core out along the pulley beam's line
        s.set(x, 4, 2, "waxed_cut_copper_slab", type="top", **DRY_)
    for (x, z) in ((0, 0), (4, 4)):
        s.set(x, 5, z, "prismarine_wall", east="none", north="none", south="none", up="true", west="none", **DRY_)
        s.set(x, 6, z, "lantern", **LANTERN)
    s.set(0, 5, 4, "dark_prismarine")         # the pennant moves up onto a prismarine cap
    s.set(0, 6, 4, "cyan_banner", rotation=str(TO_CAMERA))
    s.set(4, 5, 0, "dark_prismarine")
    s.set(4, 6, 0, "cyan_banner", rotation=str(TO_CAMERA))
    return s


def storm_metal_extractor():
    """Stormcallers T1 'ley drill shrine': red posts under a jade hip roof with upturned gold eaves, a lodestone drill
    on a chain under the roof's heart, a gold finial with a lightning rod, stone lanterns at two corners."""
    s = Structure(5, 6, 5)
    _pad5(s, "smooth_stone", "stone_bricks", "stone_brick_stairs", "polished_andesite", "polished_andesite")
    for (x, z) in CORNERS5:
        s.set(x, 1, z, "red_terracotta")
        s.set(x, 2, z, "red_terracotta")
    _hip5(s, 3, "waxed_oxidized_cut_copper_stairs", corner="gold_block")
    _hip3(s, 4, 1, 1, "waxed_oxidized_cut_copper_stairs", centre="gold_block")
    s.set(2, 3, 2, "waxed_oxidized_cut_copper")   # the roof's heart, the chain hangs from it
    for (x, z) in ((1, 1), (3, 1), (1, 3), (3, 3), (2, 1), (1, 2), (3, 2), (2, 3)):
        s.set(x, 3, z, "spruce_planks")       # the coffered ceiling under the jade
    s.set(2, 5, 2, "lightning_rod", facing="up", powered="false", waterlogged="false")
    s.set(2, 2, 2, "chain", axis="y", **DRY_)
    s.set(2, 1, 2, "lodestone")
    s.set(1, 1, 0, "lantern", **LANTERN)
    s.set(3, 1, 4, "lantern", **LANTERN)
    s.set(1, 1, 4, "red_banner", rotation=str(TO_CAMERA))
    return s


def storm_metal_extractor_t2():
    """Stormcallers T2: the shrine becomes a two-tier pagoda - a red tier with green-glazed panels and a glowing core
    rises through the upper roof, its own jade roof and a taller gold finial; lightning rods on the gold eaves."""
    s = storm_metal_extractor()
    s.size = (5, 9, 5)
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            edge = (x, z) != (2, 2)
            corner = x != 2 and z != 2
            s.set(x, 4, z, "gold_block" if corner else ("red_terracotta" if edge else "waxed_copper_block"))
            s.set(x, 5, z, "red_terracotta" if corner else ("green_glazed_terracotta" if edge else "ochre_froglight"),
                  **({"facing": "north"} if edge and not corner else {"axis": "y"} if not edge else {}))
    _hip3(s, 6, 1, 1, "waxed_oxidized_cut_copper_stairs", corner="gold_block", centre="waxed_oxidized_cut_copper")
    s.set(2, 7, 2, "gold_block")
    s.set(2, 8, 2, "lightning_rod", facing="up", powered="false", waterlogged="false")
    for (x, z) in ((0, 0), (4, 4)):
        s.set(x, 4, z, "lightning_rod", facing="up", powered="false", waterlogged="false")
    for (x, z) in ((4, 0), (0, 4)):
        s.set(x, 4, z, "lantern", **LANTERN)
    return s


def brass_metal_extractor():
    """Brassworks T1 'steam drill': a soot-black gantry over the patch, a piston hammer driving a lightning-rod bit
    through a brass head, a boiler with its firebox facing the camera, a brick stack with smoke, a lantern on the drum."""
    s = Structure(5, 7, 5)
    _pad5(s, "blackstone", "deepslate_bricks", "deepslate_brick_stairs", "polished_blackstone", "polished_blackstone")
    for (x, z) in CORNERS5:
        for y in (1, 2, 3):
            s.set(x, y, z, "polished_blackstone_wall", east="none", north="none", south="none", up="true", west="none", **DRY_)
        s.set(x, 4, z, SOOT_B)
    for i in (1, 2, 3):
        for (x, z) in ((i, 0), (i, 4), (0, i), (4, i)):
            s.set(x, 4, z, "deepslate_brick_slab", type="top", **DRY_)
    for (x, z) in ((2, 1), (2, 3), (1, 2), (3, 2)):
        s.set(x, 4, z, "polished_blackstone_brick_slab", type="top", **DRY_)
    s.set(2, 4, 2, BRASS)                     # the drill head
    s.set(2, 3, 2, "piston", facing="down", extended="false")
    s.set(2, 2, 2, "chain", axis="y", **DRY_)
    s.set(2, 1, 2, "lightning_rod", facing="down", powered="false", waterlogged="false")   # the bit
    s.set(1, 1, 4, "blast_furnace", facing="south", lit="true")   # the boiler's firebox, on the camera's side
    s.set(1, 2, 4, BRASS_D)
    s.set(1, 3, 4, "lantern", **LANTERN)
    for y in (1, 2, 3, 4, 5):
        s.set(3, y, 4, "bricks" if y != 3 else BRASS)
    s.set(3, 6, 4, "campfire", **CAMPFIRE)
    s.set(1, 1, 0, "smithing_table")
    s.set(3, 1, 0, "barrel", facing="up", open="false")
    return s


def brass_metal_extractor_t2():
    """Brassworks T2: the drill head swells into a riveted brass housing with a soot cap and a pressure valve, the
    stack climbs higher through a furnace-glow band, a second boiler drum and a feed pipe join the gantry."""
    s = brass_metal_extractor()
    s.size = (5, 10, 5)
    s.set(2, 4, 2, "waxed_copper_block")     # the tier marker
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            corner = x != 2 and z != 2
            s.set(x, 5, z, SOOT_B if corner else (BRASS if (x, z) != (2, 2) else "waxed_copper_block"))
    _hip3(s, 6, 1, 1, BRASS_ST, centre=BRASS_D)
    s.set(2, 7, 2, "lightning_rod", facing="up", powered="false", waterlogged="false")
    s.set(3, 6, 4, "magma_block")
    s.set(3, 7, 4, "bricks")
    s.set(3, 8, 4, "campfire", **CAMPFIRE)
    s.set(3, 1, 4, BRASS_D)                   # a second drum at the stack's foot
    s.set(1, 4, 3, BRASS_SL, type="top", **DRY_)
    return s


def tide_energy_converter():
    """Tidewrought 'soul-fire galley stove': a ship's stove on a sandstone footing - mangrove corner posts, a firebox
    door at the front, iron grates on the other sides showing the teal soul fire, a waxed copper boiler drum with a
    domed lid, a stovepipe with its own soul-fire smoke, a lantern on one post."""
    s = Structure(3, 6, 3)
    s.fill(0, 0, 0, 2, 0, 2, "smooth_sandstone")
    s.set(1, 0, 1, "prismarine_bricks")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "stripped_mangrove_log", axis="y")
        s.set(x, 2, z, "stripped_mangrove_log", axis="y")
    s.set(1, 1, 0, "blast_furnace", facing="north", lit="true")
    for (x, z) in ((0, 1), (2, 1), (1, 2)):
        s.set(x, 1, z, "iron_bars", **BARS)
    s.set(1, 1, 1, "soul_campfire", **CAMPFIRE)
    for (x, z) in EDGES3:
        s.set(x, 2, z, BRASS)
    s.set(1, 2, 1, BRASS)
    for (x, z, f) in ((1, 0, "south"), (1, 2, "north"), (0, 1, "east"), (2, 1, "west")):
        s.set(x, 3, z, BRASS_ST, facing=f, half="bottom", shape="straight", **DRY_)
    for (x, z) in ((2, 0), (0, 2), (2, 2)):
        s.set(x, 3, z, BRASS_SL, type="bottom", **DRY_)
    s.set(0, 3, 0, "stripped_mangrove_log", axis="y")
    s.set(0, 4, 0, "lantern", **LANTERN)
    s.set(1, 3, 1, BRASS)
    s.set(1, 4, 1, BRASS_D)
    s.set(1, 5, 1, "soul_campfire", **CAMPFIRE)
    return s


def storm_energy_converter():
    """Stormcallers 'Mana Condenser': a jade-blue soul flame under a chain inside four red posts, a jade roof with
    upturned gold corners, a red lantern-tier with a glowing core and a lightning-rod tip."""
    s = Structure(3, 7, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_andesite")
    s.set(1, 0, 1, "stone_bricks")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "red_terracotta")
        s.set(x, 2, z, "red_terracotta")
    s.set(1, 1, 0, "blast_furnace", facing="north", lit="true")
    s.set(1, 1, 1, "soul_campfire", **CAMPFIRE)
    s.set(0, 1, 1, "stone_brick_wall", east="none", north="low", south="low", up="true", west="none", **DRY_)
    s.set(1, 1, 2, "stone_brick_wall", east="low", north="none", south="none", up="true", west="low", **DRY_)
    s.set(1, 2, 1, "chain", axis="y", **DRY_)
    _hip3(s, 3, 0, 0, "waxed_oxidized_cut_copper_stairs", corner="gold_block", centre="waxed_oxidized_cut_copper")
    s.set(1, 4, 1, "ochre_froglight", axis="y")
    s.set(1, 5, 1, "gold_block")
    s.set(1, 6, 1, "lightning_rod", facing="up", powered="false", waterlogged="false")
    s.set(0, 4, 2, "lantern", **LANTERN)
    s.set(2, 4, 0, "lantern", **LANTERN)
    s.set(0, 4, 0, "lightning_rod", facing="up", powered="false", waterlogged="false")
    s.set(2, 4, 2, "lightning_rod", facing="up", powered="false", waterlogged="false")
    return s


def brass_energy_converter():
    """Brassworks 'pressure boiler': two lit fireboxes (front and the camera's back), grates on the sides, a riveted
    brass drum with a domed lid, two pressure valves on soot posts and a brick stack with smoke."""
    s = Structure(3, 6, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_blackstone")
    s.set(1, 0, 1, "deepslate_bricks")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, SOOT_B)
    s.set(1, 1, 0, "blast_furnace", facing="north", lit="true")
    s.set(1, 1, 2, "blast_furnace", facing="south", lit="true")
    s.set(0, 1, 1, "iron_bars", **BARS)
    s.set(2, 1, 1, "iron_bars", **BARS)
    s.set(1, 1, 1, "campfire", **CAMPFIRE)
    for x in range(3):
        for z in range(3):
            s.set(x, 2, z, BRASS if (x + z) % 2 else BRASS_D)
    for (x, z, f) in ((1, 0, "south"), (1, 2, "north"), (0, 1, "east"), (2, 1, "west")):
        s.set(x, 3, z, BRASS_ST, facing=f, half="bottom", shape="straight", **DRY_)
    for (x, z) in CORNERS3:
        s.set(x, 3, z, SOOT_B if (x, z) in ((0, 0), (2, 2)) else "deepslate_brick_slab",
              **({} if (x, z) in ((0, 0), (2, 2)) else dict(type="bottom", **DRY_)))
    s.set(0, 4, 0, "lightning_rod", facing="up", powered="false", waterlogged="false")
    s.set(2, 4, 2, "lightning_rod", facing="up", powered="false", waterlogged="false")
    s.set(1, 3, 1, "bricks")
    s.set(1, 4, 1, "bricks")
    s.set(1, 5, 1, "campfire", **CAMPFIRE)
    return s


def tide_storage_vault():
    """Tidewrought 'chandlery': ship's stores under a sailcloth awning - spruce posts, barrels on every face with
    crate-lid trapdoors over them, kelp bales inside, a lantern on the stacked barrel and a teal pennant."""
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, "smooth_sandstone")
    s.set(1, 0, 1, "prismarine_bricks")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "stripped_spruce_log", axis="y")
        s.set(x, 2, z, "stripped_spruce_log", axis="y")
    for (x, z, f) in [(1, 0, "north"), (0, 1, "west"), (2, 1, "east"), (1, 2, "south")]:
        s.set(x, 1, z, "barrel", facing=f, open="false")
        s.set(x, 2, z, "spruce_trapdoor", facing=f, half="bottom", open="false", powered="false", **DRY_)
    s.set(1, 1, 1, "dried_kelp_block")
    s.set(1, 2, 1, "barrel", facing="up", open="false")
    for (x, z) in CORNERS3:
        s.set(x, 3, z, "spruce_slab", type="bottom", **DRY_)
    for (x, z) in EDGES3:
        s.set(x, 3, z, "cyan_wool" if (x + z) % 2 else "white_wool")
    s.set(1, 3, 1, "lantern", **LANTERN)
    s.set(0, 3, 2, "stripped_spruce_log", axis="y")
    s.set(0, 4, 2, "cyan_banner", rotation=str(TO_CAMERA))
    return s


def storm_storage_vault():
    """Stormcallers 'Mana Cistern': a jade (emerald) store behind painted green-glazed panels between red posts, a jade
    hip roof with upturned gold corners, a gold cap with a lantern."""
    s = Structure(3, 6, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_andesite")
    s.set(1, 0, 1, "stone_bricks")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "red_terracotta")
        s.set(x, 2, z, "red_terracotta")
    for (x, z, f) in [(1, 0, "north"), (0, 1, "west"), (2, 1, "east"), (1, 2, "south")]:
        s.set(x, 1, z, "barrel", facing=f, open="false")
        s.set(x, 2, z, "green_glazed_terracotta", facing=f)
    s.set(1, 1, 1, "emerald_block")
    s.set(1, 2, 1, "emerald_block")
    _hip3(s, 3, 0, 0, "waxed_oxidized_cut_copper_stairs", corner="gold_block", centre="waxed_oxidized_cut_copper")
    s.set(1, 4, 1, "gold_block")
    s.set(1, 5, 1, "lantern", **LANTERN)
    return s


def brass_storage_vault():
    """Brassworks 'strongbox': a soot-black vault with an iron door on the front, barrels on the other faces, raw
    copper inside, a riveted brass band and a brass lid with a lantern."""
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_blackstone")
    s.set(1, 0, 1, "deepslate_bricks")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, SOOT_B)
        s.set(x, 2, z, SOOT_B)
    s.set(1, 1, 0, "iron_door", facing="north", half="lower", hinge="left", open="false", powered="false")
    s.set(1, 2, 0, "iron_door", facing="north", half="upper", hinge="left", open="false", powered="false")
    for (x, z, f) in [(0, 1, "west"), (2, 1, "east"), (1, 2, "south")]:
        s.set(x, 1, z, "barrel", facing=f, open="false")
        s.set(x, 2, z, BRASS_D)
    s.set(1, 1, 1, "raw_copper_block")
    s.set(1, 2, 1, "raw_copper_block")
    for x in range(3):
        for z in range(3):
            s.set(x, 3, z, BRASS if (x + z) % 2 == 0 else BRASS_D)
    for (x, z, f) in ((1, 0, "south"), (1, 2, "north"), (0, 1, "east"), (2, 1, "west")):
        s.set(x, 4, z, BRASS_ST, facing=f, half="bottom", shape="straight", **DRY_)
    s.set(1, 4, 1, "lantern", **LANTERN)
    return s


def verdant_storage_vault():
    """Verdant 'root cellar': a living stump store - oak corner posts, barrels on every face under open oak shutters,
    seed bales inside, a moss roof with a flowering azalea and lanterns on two posts."""
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, "moss_block")
    s.set(1, 0, 1, "rooted_dirt")
    for (x, z) in CORNERS3:
        s.set(x, 1, z, "oak_log", axis="y")
        s.set(x, 2, z, "oak_log", axis="y")
    for (x, z, f) in [(1, 0, "north"), (0, 1, "west"), (2, 1, "east"), (1, 2, "south")]:
        s.set(x, 1, z, "barrel", facing=f, open="false")
        s.set(x, 2, z, "stripped_oak_wood", axis="y")
    s.set(1, 1, 1, "hay_block", axis="y")
    s.set(1, 2, 1, "barrel", facing="up", open="false")
    for x in range(3):
        for z in range(3):
            s.set(x, 3, z, "moss_block")
    s.set(1, 4, 1, "flowering_azalea")
    s.set(0, 4, 0, "lantern", **LANTERN)
    s.set(2, 4, 2, "lantern", **LANTERN)
    s.set(0, 4, 2, "moss_carpet")
    return s


# variant -> (extractor, extractor T2, energy converter, storage vault) for the factions above
BESPOKE_ECONOMY_2 = {
    "_tide": (tide_metal_extractor, tide_metal_extractor_t2, tide_energy_converter, tide_storage_vault),
    "_storm": (storm_metal_extractor, storm_metal_extractor_t2, storm_energy_converter, storm_storage_vault),
    "_brass": (brass_metal_extractor, brass_metal_extractor_t2, brass_energy_converter, brass_storage_vault),
}


def check_mex_tiers(t1, t2, name):
    """Every T1 block position is still a block in the T2, the T1 holds no tier marker and the T2 does."""
    def solid(st):
        return {p for p, i in st.blocks.items() if st.palette[i][0] not in ("air", "cave_air")}
    lost = solid(t1) - solid(t2)
    assert not lost, f"{name}: the T2 leaves T1 blocks behind at {sorted(lost)}"
    markers = {"copper_block", "cut_copper", "waxed_copper_block"}
    assert not any(st[0] in markers for st in t1.palette), f"{name}: the T1 holds a tier-2 marker"
    assert any(st[0] in markers for st in t2.palette), f"{name}: the T2 has no tier-2 marker"


def great_forge():
    """The Brassworks T3 lab (slice 4): a great foundry under a stepped brass roof, where the Iron Leviathan Engine is
    cast. Four 2x2 blackstone towers at the corners carry a brass deck (waxed cut copper, stair eaves) and a narrower
    upper tier on short soot posts; a deepslate back wall holds the forge mouth - three lit blast furnaces under iron
    bars - with a crucible pair (cauldrons) and anvils before it; the casting floor glows (shroomlight channels set
    flush in the floor, safe to walk on - magma would burn the units that roll out). Two tall smokestacks with lit
    campfires rise from the back towers, a waxed-copper boiler drum and a gold gauge crown the roof. Open on the front
    and both sides between the towers, so the 3-block-wide engine walks out and nothing can be trapped. 13 x 13 x 13."""
    W, H = 13, 13
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "deepslate_bricks" if edge else "polished_blackstone")
    # the casting floor: a cross of shroomlight channels flush with the floor, meeting at the centre
    for d in range(-3, 4):
        s.set(c + d, 0, c, "shroomlight")
        s.set(c, 0, c + d, "shroomlight")
    # the corner towers: 2x2 polished blackstone bricks, a brass band at y 4
    for (x0, z0) in [(0, 0), (W - 2, 0), (0, W - 2), (W - 2, W - 2)]:
        for x in (x0, x0 + 1):
            for z in (z0, z0 + 1):
                for y in range(1, 7):
                    s.set(x, y, z, BRASS if y == 4 else SOOT_B)
    # the back wall (+z) between the towers: deepslate bricks, the forge mouth (iron bars over lit blast furnaces)
    for x in range(2, W - 2):
        for y in range(1, 7):
            s.set(x, y, W - 1, "deepslate_bricks")
    for x in (c - 1, c, c + 1):
        s.set(x, 1, W - 2, "blast_furnace", facing="north", lit="true")
        s.set(x, 2, W - 2, "iron_bars", **DRY_)
        s.set(x, 3, W - 2, SOOT)
    s.set(c - 3, 1, W - 2, "cauldron")
    s.set(c + 3, 1, W - 2, "cauldron")
    s.set(c - 2, 1, W - 2, "anvil", facing="north")
    s.set(c + 2, 1, W - 2, "anvil", facing="north")
    # the brass deck at y 7 over the whole hall, stair eaves round its edge
    ring_stairs(s, 7, 0, W - 1, BRASS_ST)
    s.fill(1, 7, 1, W - 2, 7, W - 2, BRASS)
    # the upper tier: short soot posts at y 8, a narrower brass roof at y 9, slab eaves, a ridge at y 10
    for (x, z) in [(3, 3), (W - 4, 3), (3, W - 4), (W - 4, W - 4)]:
        s.set(x, 8, z, SOOT_B)
    s.fill(3, 9, 3, W - 4, 9, W - 4, BRASS)
    for x in range(2, W - 2):
        s.set(x, 9, 2, BRASS_SL, type="bottom", **DRY_)
        s.set(x, 9, W - 3, BRASS_SL, type="bottom", **DRY_)
    for z in range(3, W - 3):
        s.set(2, 9, z, BRASS_SL, type="bottom", **DRY_)
        s.set(W - 3, 9, z, BRASS_SL, type="bottom", **DRY_)
    # the boiler drum on the ridge with a gold gauge on top
    for x in range(c - 2, c + 3):
        s.set(x, 10, c, "waxed_copper_block")
    s.set(c, 11, c, "gold_block")
    # the smokestacks: from the back towers up past the roof, a lit campfire on each
    for x in (0, W - 1):
        for y in range(7, H - 1):
            s.set(x, y, W - 1, SOOT_B)
        s.set(x, H - 1, W - 1, "campfire", **CAMPFIRE)
    # lanterns on the front towers' tops, black banners on the front towers
    for x in (1, W - 2):
        s.set(x, 8, 1, "lantern", **LANTERN)
    for x in (0, W - 1):
        s.set(x, 8, 0, "black_banner", rotation=str(TO_CAMERA))
    fill_air(s)
    return s


# ---- big-units (design/big_units.md): the game enders and their counter -----------------------------------------
def sun_spire():
    """The Sunforged game ender: a white-and-gold needle on a quartz terrace. A 7x7 smooth-quartz terrace with quartz-
    brick steps, a 5x5 chapel drum (quartz bricks, gold corner posts, yellow stained-glass windows), a 3x3 quartz-pillar
    shaft banded with gold every third block, and a crown: a beacon in a ring of gold blocks with end rods pointing out
    and up - the charge rings form round it in play. Solid all the way up: nothing to stand in. 7 x 17 x 7."""
    W, H = 7, 17
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "quartz_bricks" if edge else "smooth_quartz")
    # the chapel drum
    for y in range(1, 5):
        for x in range(1, W - 1):
            for z in range(1, W - 1):
                corner = x in (1, W - 2) and z in (1, W - 2)
                side_mid = (x == c and z in (1, W - 2)) or (z == c and x in (1, W - 2))
                if corner:
                    s.set(x, y, z, "gold_block" if y in (1, 4) else "quartz_pillar", **({} if y in (1, 4) else {"axis": "y"}))
                elif side_mid and y in (2, 3):
                    s.set(x, y, z, "yellow_stained_glass")
                else:
                    s.set(x, y, z, "quartz_bricks")
    ring_stairs(s, 5, 1, W - 2, "quartz_stairs")
    # the shaft
    for y in range(5, 13):
        for x in range(c - 1, c + 2):
            for z in range(c - 1, c + 2):
                band = (y % 3 == 0)
                s.set(x, y, z, "gold_block" if band and (x, z) != (c, c) else "quartz_pillar", **({} if band and (x, z) != (c, c) else {"axis": "y"}))
    # the crown
    s.set(c, 13, c, "beacon")
    for (x, z) in ((c - 1, c), (c + 1, c), (c, c - 1), (c, c + 1)):
        s.set(x, 13, z, "gold_block")
    for (x, z, f) in ((c - 1, c - 1, "up"), (c + 1, c - 1, "up"), (c - 1, c + 1, "up"), (c + 1, c + 1, "up")):
        s.set(x, 13, z, "end_rod", facing=f)
    s.set(c, 14, c, "gold_block")
    s.set(c, 15, c, "end_rod", facing="up")
    s.set(c, 16, c, "lightning_rod", facing="up", powered="false", waterlogged="false")
    fill_air(s)
    return s


def necropolis():
    """The Gravebound game ender: a black mausoleum-ziggurat. A 9x9 blackstone platform edged in polished blackstone
    bricks, a 7x7 crypt (polished blackstone bricks, bone-block pillars at the corners, soul lanterns in the doorways),
    a stepped 5x5 then 3x3 tier, and on top a bone obelisk with a wither skeleton skull and four soul campfires round
    it for the soul-fire glow. 9 x 12 x 9."""
    W, H = 9, 12
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "polished_blackstone_bricks" if edge else "blackstone")
    for y in range(1, 5):
        for x in range(1, W - 1):
            for z in range(1, W - 1):
                corner = x in (1, W - 2) and z in (1, W - 2)
                door = (x == c and z in (1, W - 2)) or (z == c and x in (1, W - 2))
                if corner:
                    s.set(x, y, z, "bone_block", axis="y")
                elif door and y == 1:
                    s.set(x, y, z, "chiseled_polished_blackstone")
                elif door and y == 2:
                    s.set(x, y, z, "soul_lantern", hanging="false", waterlogged="false")
                else:
                    s.set(x, y, z, "polished_blackstone_bricks")
    ring_stairs(s, 5, 1, W - 2, "polished_blackstone_brick_stairs")
    for y in (5, 6):
        for x in range(2, W - 2):
            for z in range(2, W - 2):
                s.set(x, y, z, "cracked_polished_blackstone_bricks" if (x + z + y) % 4 == 0 else "polished_blackstone_bricks")
    for y in (7, 8):
        for x in range(c - 1, c + 2):
            for z in range(c - 1, c + 2):
                s.set(x, y, z, "bone_block", axis="y")
    for (x, z) in ((c - 2, c - 2), (c + 2, c - 2), (c - 2, c + 2), (c + 2, c + 2)):
        s.set(x, 7, z, "soul_campfire", lit="true", facing="north", signal_fire="false", waterlogged="false")
    s.set(c, 9, c, "bone_block", axis="y")
    s.set(c, 10, c, "bone_block", axis="y")
    s.set(c, 11, c, "wither_skeleton_skull", rotation="0")
    fill_air(s)
    return s


def ward_spire():
    """The game enders' counter (every faction's): an obsidian needle with an amethyst heart. Obsidian base, a crying-
    obsidian collar, a column of amethyst blocks with obsidian corner ribs, budding amethyst near the top, and an end rod
    tip over an amethyst cluster - the shimmer in play comes from there. 3 x 9 x 3."""
    s = Structure(3, 9, 3)
    for x in range(3):
        for z in range(3):
            s.set(x, 0, z, "obsidian")
            s.set(x, 1, z, "crying_obsidian" if (x, z) != (1, 1) else "obsidian")
    for y in range(2, 7):
        for x in range(3):
            for z in range(3):
                corner = x in (0, 2) and z in (0, 2)
                if corner:
                    s.set(x, y, z, "obsidian" if y < 6 else "air")
                else:
                    s.set(x, y, z, "budding_amethyst" if y == 5 and (x, z) == (1, 1) else "amethyst_block")
    s.set(1, 7, 1, "amethyst_block")
    s.set(1, 8, 1, "end_rod", facing="up")
    fill_air(s)
    return s






# ---- beasts-2: the other five game enders (design/big_units.md 2.5-2.9). Solid masses with open steps: nothing a unit
# can stand in and not walk out of (tools/audit_structure_pockets.py; the GameTest is the truth).

WALL_POST = dict(up="true", north="none", east="none", south="none", west="none", waterlogged="false")
LEAF_P = dict(persistent="true", distance="1", waterlogged="false")
CAMPFIRE_LIT = dict(lit="true", facing="north", signal_fire="false", waterlogged="false")


def doom_forge():
    """The Ironhide Horde game ender: a blackstone forge-ziggurat. A 9x9 blackstone platform edged in polished
    blackstone; a 7x7 forge (polished blackstone bricks, magma vents mid-wall, gilded seams) with a lit blast furnace
    at each corner - the four bellows - whose chimneys (blackstone walls) rise through the stair hip to smoking
    campfires; a 5x5 nether-brick tier, a 3x3 magma heart round a shroomlight and a crowning furnace that smokes.
    9 x 12 x 9."""
    W, H = 9, 12
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "polished_blackstone" if edge else "blackstone")
    corners = ((1, 1), (1, W - 2), (W - 2, 1), (W - 2, W - 2))
    for y in range(1, 5):
        for x in range(1, W - 1):
            for z in range(1, W - 1):
                mid = (x == c and z in (1, W - 2)) or (z == c and x in (1, W - 2))
                if (x, z) in corners:
                    if y == 1:
                        s.set(x, y, z, "blast_furnace", facing="west" if x == 1 else "east", lit="true")
                    else:
                        s.set(x, y, z, "polished_blackstone")
                elif mid and y == 2:
                    s.set(x, y, z, "magma_block")
                elif y == 3 and (x + z) % 3 == 0:
                    s.set(x, y, z, "gilded_blackstone")
                else:
                    s.set(x, y, z, "polished_blackstone_bricks")
    ring_stairs(s, 5, 1, W - 2, "polished_blackstone_brick_stairs")
    for (x, z) in corners:   # the bellows chimneys stand through the stair ring
        for y in range(5, 8):
            s.set(x, y, z, "polished_blackstone_wall", **WALL_POST)
        s.set(x, 8, z, "campfire", **CAMPFIRE_LIT)
    for y in (5, 6):
        for x in range(2, W - 2):
            for z in range(2, W - 2):
                corner = x in (2, W - 3) and z in (2, W - 3)
                s.set(x, y, z, "chiseled_nether_bricks" if corner else "nether_bricks")
    for y in (7, 8):
        for x in range(c - 1, c + 2):
            for z in range(c - 1, c + 2):
                s.set(x, y, z, "shroomlight" if (x, z) == (c, c) and y == 8 else "magma_block")
    s.set(c, 9, c, "gilded_blackstone")
    s.set(c, 10, c, "blast_furnace", facing="north", lit="true")
    s.set(c, 11, c, "campfire", **CAMPFIRE_LIT)
    fill_air(s)
    return s


def world_seed():
    """The Verdant Court game ender: a colossal seedling on a mossy mound. A 9x9 moss pad edged in rooted dirt, a
    stepped mound (7x7 moss with mossy-cobblestone corners, 5x5 moss, 3x3 rooted dirt), a glowing stem (oak wood
    banded with verdant froglights) and two great seed-leaves of azalea spreading from its crown, a flowering-azalea
    bud with a shroomlight heart on top. Steps one block high: climbable, nothing enclosed. 9 x 14 x 9."""
    W, H = 9, 14
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "rooted_dirt" if edge else "moss_block")
    for x in range(1, W - 1):
        for z in range(1, W - 1):
            corner = x in (1, W - 2) and z in (1, W - 2)
            s.set(x, 1, z, "mossy_cobblestone" if corner else "moss_block")
    for x in range(2, W - 2):
        for z in range(2, W - 2):
            s.set(x, 2, z, "moss_block")
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            s.set(x, 3, z, "rooted_dirt")
    for y in range(4, 11):
        if y in (5, 8):
            s.set(c, y, c, "verdant_froglight", axis="y")
        else:
            s.set(c, y, c, "oak_wood", axis="y")
    # the two seed-leaves: azalea fans east and west of the crown, dipping a block at their tips
    for d in (-1, 1):
        for i in range(2, 4):
            y = 10 if i < 3 else 9
            for dz in (-1, 0, 1):
                if i == 3 and dz != 0:
                    continue
                s.set(c + d * i, y, c + dz, "azalea_leaves", **LEAF_P)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                s.set(c + dx, 10, c + dz, "flowering_azalea_leaves", **LEAF_P)
    s.set(c, 11, c, "flowering_azalea_leaves", **LEAF_P)
    s.set(c, 12, c, "shroomlight")
    s.set(c, 13, c, "flowering_azalea_leaves", **LEAF_P)
    fill_air(s)
    return s


def maelstrom_engine():
    """The Tidewrought game ender: a prismarine-and-copper pump. A 9x9 dark-prismarine platform edged in prismarine
    bricks with copper outflow lips on each side; a 7x7 pump house (prismarine bricks, waxed-copper corner pillars, sea
    lanterns mid-wall), a hip of prismarine stairs, a 5x5 dark tier, a 3x3 copper drum with the conduit on top in a
    ring of lanterns - the eye of the whirlpool. 9 x 10 x 9."""
    W, H = 9, 10
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "prismarine_bricks" if edge else "dark_prismarine")
    for (x, z) in ((c, 0), (c, W - 1), (0, c), (W - 1, c)):
        s.set(x, 1, z, "waxed_cut_copper_slab", type="bottom", waterlogged="false")
    for y in range(1, 5):
        for x in range(1, W - 1):
            for z in range(1, W - 1):
                corner = x in (1, W - 2) and z in (1, W - 2)
                mid = (x == c and z in (1, W - 2)) or (z == c and x in (1, W - 2))
                if corner:
                    s.set(x, y, z, "waxed_cut_copper")
                elif mid and y in (2, 3):
                    s.set(x, y, z, "sea_lantern")
                else:
                    s.set(x, y, z, "prismarine_bricks")
    ring_stairs(s, 5, 1, W - 2, "prismarine_brick_stairs")
    for x in range(2, W - 2):
        for z in range(2, W - 2):
            s.set(x, 5, z, "dark_prismarine")
    for y in (6, 7):
        for x in range(c - 1, c + 2):
            for z in range(c - 1, c + 2):
                s.set(x, y, z, "waxed_copper_block" if (x + z) % 2 == 0 else "prismarine")
    s.set(c, 8, c, "conduit", waterlogged="false")
    for (x, z) in ((c - 1, c), (c + 1, c), (c, c - 1), (c, c + 1)):
        s.set(x, 8, z, "lantern", **LANTERN)
    fill_air(s)
    return s


def heavens_bell():
    """The Stormcallers game ender: a jade bell tower. A 7x7 polished-andesite court edged in stone bricks, a 5x5
    stone-brick plinth with red-lacquer corner posts, a jade (waxed oxidised copper) eave, a 3x3 tower of red
    terracotta with green glazed panels and a gold band, a second jade eave, and the belfry: four red posts under a
    jade cap with the bell hanging inside, a gold finial and a lightning rod to call the storm. 7 x 16 x 7."""
    W, H = 7, 16
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "stone_bricks" if edge else "polished_andesite")
    for y in range(1, 4):
        for x in range(1, W - 1):
            for z in range(1, W - 1):
                corner = x in (1, W - 2) and z in (1, W - 2)
                s.set(x, y, z, "red_terracotta" if corner else "stone_bricks")
    ring_stairs(s, 4, 1, W - 2, "waxed_oxidized_cut_copper_stairs")
    for y in range(4, 10):
        for x in range(c - 1, c + 2):
            for z in range(c - 1, c + 2):
                corner = x in (c - 1, c + 1) and z in (c - 1, c + 1)
                if corner:
                    s.set(x, y, z, "red_terracotta")
                elif y == 8:
                    s.set(x, y, z, "gold_block")
                elif y in (5, 7) and (x, z) != (c, c):
                    s.set(x, y, z, "green_glazed_terracotta", facing="north")
                else:
                    s.set(x, y, z, "red_terracotta")
    ring_stairs(s, 10, 1, W - 2, "waxed_oxidized_cut_copper_stairs")
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            s.set(x, 10, z, "waxed_oxidized_cut_copper")
    for (x, z) in ((c - 1, c - 1), (c + 1, c - 1), (c - 1, c + 1), (c + 1, c + 1)):
        for y in (11, 12):
            s.set(x, y, z, "red_terracotta")
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            s.set(x, 13, z, "waxed_oxidized_cut_copper")
    s.set(c, 12, c, "bell", attachment="ceiling", facing="north", powered="false")
    s.set(c, 14, c, "gold_block")
    s.set(c, 15, c, "lightning_rod", facing="up", powered="false", waterlogged="false")
    fill_air(s)
    return s


def earthcracker():
    """The Brassworks game ender: a steam drill tower. A 7x7 polished-blackstone yard edged in deepslate bricks with two
    smokestacks (blackstone walls topped by smoking campfires) on the east and west kerbs; a 5x5 deepslate-brick engine
    house with brass corner posts and a lit blast-furnace firebox on each side; a hip of stairs; a 3x3 derrick of iron
    banded with brass every third block; a brass head carrying the drill piston and a smoking furnace crown.
    7 x 17 x 7."""
    W, H = 7, 17
    s = Structure(W, H, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "deepslate_bricks" if edge else "polished_blackstone")
    for x in (0, W - 1):
        for y in range(1, 7):
            s.set(x, y, c, "polished_blackstone_wall", **WALL_POST)
        s.set(x, 7, c, "campfire", **CAMPFIRE_LIT)
    for y in range(1, 4):
        for x in range(1, W - 1):
            for z in range(1, W - 1):
                corner = x in (1, W - 2) and z in (1, W - 2)
                mid = (x == c and z in (1, W - 2)) or (z == c and x in (1, W - 2))
                if corner:
                    s.set(x, y, z, "waxed_cut_copper")
                elif mid and y == 1:
                    f = "north" if z == 1 else "south" if z == W - 2 else "west" if x == 1 else "east"
                    s.set(x, y, z, "blast_furnace", facing=f, lit="true")
                else:
                    s.set(x, y, z, "deepslate_bricks")
    ring_stairs(s, 4, 1, W - 2, "deepslate_brick_stairs")
    for y in range(4, 13):
        for x in range(c - 1, c + 2):
            for z in range(c - 1, c + 2):
                corner = x in (c - 1, c + 1) and z in (c - 1, c + 1)
                if y % 3 == 0:
                    s.set(x, y, z, "waxed_cut_copper")
                else:
                    s.set(x, y, z, "polished_blackstone" if corner else "iron_block")
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            s.set(x, 13, z, "waxed_cut_copper")
    s.set(c, 14, c, "piston", facing="up", extended="false")
    s.set(c, 15, c, "blast_furnace", facing="north", lit="true")
    s.set(c, 16, c, "campfire", **CAMPFIRE_LIT)
    fill_air(s)
    return s



if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(OUT_DATA, exist_ok=True)
    for out in (OUT, OUT_DATA):
        for variant, builders in BESPOKE_ECONOMY.items():
            for name, build in zip(("metal_extractor", "metal_extractor_t2", "wind_generator", "energy_converter",
                                    "storage_vault"), builders):
                build().write(f"{out}/{name}{variant}.nbt")
        for variant, P in PALETTES.items():
            if variant == "_verdant":   # grown, not built: bespoke living-wood takes (see the Verdant section)
                verdant_metal_extractor().write(f"{out}/metal_extractor{variant}.nbt")
                verdant_metal_extractor_t2().write(f"{out}/metal_extractor_t2{variant}.nbt")
                verdant_wind_generator().write(f"{out}/wind_generator{variant}.nbt")
                verdant_energy_converter().write(f"{out}/energy_converter{variant}.nbt")
                verdant_storage_vault().write(f"{out}/storage_vault{variant}.nbt")
                continue
            if variant in BESPOKE_ECONOMY_2:   # bespoke takes (design/building_detail.md)
                mex, mex2, conv, vault = BESPOKE_ECONOMY_2[variant]
                check_mex_tiers(mex(), mex2(), variant)
                mex().write(f"{out}/metal_extractor{variant}.nbt")
                mex2().write(f"{out}/metal_extractor_t2{variant}.nbt")
                conv().write(f"{out}/energy_converter{variant}.nbt")
                vault().write(f"{out}/storage_vault{variant}.nbt")
            else:
                metal_extractor(P).write(f"{out}/metal_extractor{variant}.nbt")
                metal_extractor_t2(P).write(f"{out}/metal_extractor_t2{variant}.nbt")
            if variant == "_brass":   # a boiler, not a mill: no rotor hub, no sails
                brass_steam_boiler().write(f"{out}/wind_generator{variant}.nbt")
            elif variant == "_tide":   # a tide-mill with a waterwheel (design/lively_buildings.md)
                tide_wind_generator().write(f"{out}/wind_generator{variant}.nbt")
            elif variant == "_storm":   # a wind-chime pagoda with a vane crown
                storm_wind_generator().write(f"{out}/wind_generator{variant}.nbt")
            else:
                wind_generator(P).write(f"{out}/wind_generator{variant}.nbt")
            if variant not in BESPOKE_ECONOMY_2:
                energy_converter(P).write(f"{out}/energy_converter{variant}.nbt")
                storage_vault(P, variant).write(f"{out}/storage_vault{variant}.nbt")
        kingdom_house().write(f"{out}/villager_house.nbt")
        fallen_house().write(f"{out}/haunted_house.nbt")
        heartwood_hall().write(f"{out}/heartwood_hall.nbt")
        grove().write(f"{out}/grove.nbt")
        circle_of_elders().write(f"{out}/circle_of_elders.nbt")
        heart_of_the_wild().write(f"{out}/heart_of_the_wild.nbt")
        storm_oak().write(f"{out}/storm_oak.nbt")
        wreck_harbour().write(f"{out}/wreck_harbour.nbt")
        slipway().write(f"{out}/slipway.nbt")
        drydock().write(f"{out}/drydock.nbt")
        lighthouse_battery().write(f"{out}/lighthouse_battery.nbt")
        abyssal_shipyard().write(f"{out}/abyssal_shipyard.nbt")
        jade_pagoda().write(f"{out}/jade_pagoda.nbt")
        lotus_academy().write(f"{out}/lotus_academy.nbt")
        celestial_observatory().write(f"{out}/celestial_observatory.nbt")
        heavens_gate().write(f"{out}/heavens_gate.nbt")
        foundry_hall().write(f"{out}/foundry_hall.nbt")
        machine_shop().write(f"{out}/machine_shop.nbt")
        geothermal_plant().write(f"{out}/geothermal_plant.nbt")
        assembly_works().write(f"{out}/assembly_works.nbt")
        rail_battery().write(f"{out}/rail_battery.nbt")
        # the static defences of all seven factions (design/defences.md): walls, gates, traps, towers, emplacements
        import defence_structures
        defence_structures.write_all(out, Structure)
        great_forge().write(f"{out}/great_forge.nbt")
        sun_spire().write(f"{out}/sun_spire.nbt")
        necropolis().write(f"{out}/necropolis.nbt")
        ward_spire().write(f"{out}/ward_spire.nbt")
        doom_forge().write(f"{out}/doom_forge.nbt")
        world_seed().write(f"{out}/world_seed.nbt")
        maelstrom_engine().write(f"{out}/maelstrom_engine.nbt")
        heavens_bell().write(f"{out}/heavens_bell.nbt")
        earthcracker().write(f"{out}/earthcracker.nbt")
