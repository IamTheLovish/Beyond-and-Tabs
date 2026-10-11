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
def sun_pad5(s):
    """5x5 parvis: polished diorite flags in a smooth quartz kerb, chiseled quartz corners, lapis studs on the
    path crosses."""
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            s.set(x, 0, z, "chiseled_quartz_block" if corner else "smooth_quartz" if edge else "polished_diorite")
    for (x, z) in [(2, 0), (0, 2), (4, 2), (2, 4)]:
        s.set(x, 0, z, "lapis_block")


def sun_chapel_base(s):
    """The extractor family's ground floor: four quartz piers carrying a lintel ring with a glowing sea-lantern
    keystone on every side, end-rod pinnacles on the piers, the drill's iron shaft in the middle, a blast furnace on
    the camera-facing west side and blue banners over the front."""
    sun_pad5(s)
    for (x, z) in CORNERS5:
        pillar(s, x, z, 1, 2)
        s.set(x, 3, z, "chiseled_quartz_block")
        s.set(x, 4, z, "end_rod", **UP)
    ring_stairs(s, 3, 0, 4, "quartz_stairs", half="top")
    for (x, z) in [(2, 0), (0, 2), (4, 2), (2, 4)]:
        s.set(x, 3, z, "sea_lantern")
    s.set(2, 1, 2, "raw_iron_block")            # the ore the drill bites into
    s.set(0, 1, 2, "blast_furnace", facing="west", lit="true")
    s.set(2, 1, 4, "barrel", facing="south", open="false")
    s.set(4, 1, 2, "smithing_table")
    for x in (1, 3):
        s.set(x, 4, 0, "blue_banner", rotation="8")   # over the entrance, facing out the front
    s.set(0, 4, 3, "blue_banner", rotation="4")       # and one on each camera-facing side
    s.set(1, 4, 4, "blue_banner", rotation="0")


def sun_metal_extractor():
    """T1 'chapel drill': a white quartz chapel over the patch - piers, a lintel ring with glowing keystones, a gold
    and lapis hip roof crowned by the drill head, end-rod pinnacles, blue banners. 5 x 6 x 5."""
    s = Structure(5, 6, 5)
    sun_chapel_base(s)
    s.set(2, 2, 2, "iron_block")
    s.set(2, 3, 2, "iron_block")
    ring_stairs(s, 4, 1, 3, "quartz_stairs")
    s.set(2, 4, 2, "gold_block")                 # the drill head showing through the roof
    s.set(2, 5, 2, "lightning_rod", powered="false", **UP, **DRY_)
    return s


def sun_metal_extractor_t2():
    """T2 'cathedral drill': the chapel grows a square bell tower - quartz brick walls with blue glass lancets and
    pillar corners, a gold band, a cornice with end-rod pinnacles and a gold-and-lapis spire. Carries the
    waxed_copper_block tier marker (the drill's collar). 5 x 12 x 5, so T1 and T2 tell apart at a glance."""
    s = Structure(5, 12, 5)
    sun_chapel_base(s)
    s.set(2, 2, 2, "iron_block")
    s.set(2, 3, 2, "waxed_copper_block")         # tier 2 marker (getUpgradeLevel)
    # roof walk round the tower foot
    for (x, z) in [(1, 0), (3, 0), (0, 1), (0, 3), (4, 1), (4, 3), (1, 4), (3, 4)]:
        if (x, 4, z) not in s.blocks:
            s.set(x, 4, z, "smooth_quartz_slab", type="bottom", **DRY_)
    for (x, z) in [(2, 0), (0, 2), (4, 2), (2, 4)]:
        s.set(x, 4, z, "smooth_quartz_slab", type="bottom", **DRY_)
    # the tower: x,z 1..3, y 4..7
    for y in range(4, 8):
        for x in range(1, 4):
            for z in range(1, 4):
                if x in (1, 3) and z in (1, 3):
                    s.set(x, y, z, "quartz_pillar", axis="y")
                elif x == 2 and z == 2:
                    s.set(x, y, z, "iron_block" if y < 7 else "gold_block")
                elif y in (5, 6):
                    s.set(x, y, z, "blue_stained_glass")    # lancet windows on every face
                else:
                    s.set(x, y, z, "quartz_bricks")
    for (x, z) in [(2, 1), (1, 2), (3, 2), (2, 3)]:
        s.set(x, 7, z, "gold_block")             # gold band under the cornice
    ring_stairs(s, 8, 1, 3, "quartz_stairs", half="top")
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        s.set(x, 8, z, "chiseled_quartz_block")
        s.set(x, 9, z, "end_rod", **UP)
    s.set(2, 8, 2, "lapis_block")
    s.set(2, 9, 2, "quartz_pillar", axis="y")
    s.set(2, 10, 2, "gold_block")
    s.set(2, 11, 2, "lightning_rod", powered="false", **UP, **DRY_)
    return s


def sun_wind_generator():
    """A white belfry mill: quartz tower with pillar corners and blue lancets, a gold band, the spruce hub at y=4
    (white sails on spruce arms), an open belfry of four slim pillars, then a lapis roof with a gold finial and an
    end-rod spire. 3 x 10 x 3."""
    s = Structure(3, 10, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_diorite")
    s.set(1, 0, 1, "lapis_block")
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 3 if z == 0 else 4)
    for y in (1, 2, 3):
        for (x, z) in EDGES3 + [(1, 1)]:
            s.set(x, y, z, "smooth_quartz" if y == 1 else "gold_block" if y == 3 and (x, z) != (1, 1)
                  else "blue_stained_glass" if (x, z) != (1, 1) else "quartz_bricks")
    s.set(1, 4, 0, "stripped_spruce_log", axis="z")     # <- rotor hub (found by WindmillRenderClientEvents)
    for (x, z) in [(1, 1), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 4, z, "quartz_bricks")
    for (x, z) in CORNERS3:                               # the belfry posts the sails turn between
        for y in (5, 6):
            s.set(x, y, z, "quartz_pillar" if z == 2 else "diorite_wall", **({"axis": "y"} if z == 2 else {}))
    # roof: chiseled corners, lapis cap, gold finial, glowing spire
    for (x, z) in CORNERS3:
        s.set(x, 7, z, "chiseled_quartz_block")
    for (x, z) in EDGES3:
        s.set(x, 7, z, "lapis_block")
    s.set(1, 7, 1, "lapis_block")
    for (x, z) in EDGES3:
        s.set(x, 8, z, "quartz_slab", type="bottom", **DRY_)
    s.set(1, 8, 1, "gold_block")
    s.set(1, 9, 1, "end_rod", **UP)
    s.set(0, 8, 2, "blue_banner", rotation=str(TO_CAMERA))   # flies over the camera-facing corner
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
def grave_pad5(s):
    """5x5 grave plot: deepslate tile kerb, cobbled deepslate fill, soul soil corners, a crying obsidian heart."""
    for x in range(5):
        for z in range(5):
            corner = x in (0, 4) and z in (0, 4)
            edge = x in (0, 4) or z in (0, 4)
            s.set(x, 0, z, "soul_soil" if corner else "deepslate_tiles" if edge else "cobbled_deepslate")
    s.set(2, 0, 2, "crying_obsidian")


def grave_ribcage_base(s):
    """The extractor family's ground floor: two bone ribs arching over the pit (white bars across the dark plot
    from above), soul-lantern lamp posts on three corners, a crooked deepslate spire leaning out of the
    camera-facing corner, chains hanging off the ribs, a blast furnace and a wither skull on the west side."""
    grave_pad5(s)
    for z in (1, 3):
        for x in (0, 4):
            s.set(x, 1, z, "bone_block", axis="y")
            s.set(x, 2, z, "bone_block", axis="y")
        for x in (1, 2, 3):
            s.set(x, 3, z, "bone_block", axis="x")
        s.set(2, 2, z, "chain", axis="y", **DRY_)
    for (x, z) in [(0, 0), (4, 0), (4, 4)]:
        s.set(x, 1, z, "deepslate_brick_wall")
        s.set(x, 2, z, "soul_lantern", **LANTERN)
    # the crooked spire: straight for two blocks, then it leans in and back as it climbs
    s.set(0, 1, 4, "deepslate_bricks")
    s.set(0, 2, 4, "deepslate_bricks")
    s.set(0, 3, 4, "cracked_deepslate_bricks")
    s.set(0, 4, 4, "deepslate_brick_wall")
    s.set(1, 5, 4, "deepslate_brick_wall")
    s.set(1, 6, 4, "deepslate_brick_wall")
    s.set(1, 7, 4, "soul_lantern", **LANTERN)
    s.set(0, 1, 2, "blast_furnace", facing="west", lit="true")
    s.set(2, 1, 4, "polished_blackstone_bricks")
    s.set(2, 2, 4, "wither_skeleton_skull", rotation="0")


def grave_metal_extractor():
    """T1 'ossuary drill': a bone ribcage over a grave pit, a blackstone drill whose soul campfire burns cyan
    straight up at the camera, soul lanterns on wall posts and a crooked spire with a soul lantern. 5 x 8 x 5."""
    s = Structure(5, 8, 5)
    grave_ribcage_base(s)
    s.set(2, 1, 2, "polished_blackstone")
    s.set(2, 2, 2, "polished_blackstone_bricks")
    s.set(2, 3, 2, "polished_blackstone")
    s.set(2, 4, 2, "soul_campfire", lit="true", facing="north", signal_fire="false", **DRY_)
    return s


def grave_metal_extractor_t2():
    """T2 'bone spire': the ribcage carries a verdigris-banded deepslate tower that kinks as it climbs - crying
    obsidian heart, bone pinnacles with wither skulls at its foot, chains, a soul lantern at the crooked tip.
    Carries the waxed_copper_block tier marker (buried under the drill). 5 x 12 x 5."""
    s = Structure(5, 12, 5)
    grave_ribcage_base(s)
    s.set(2, 0, 2, "waxed_copper_block")         # tier 2 marker (getUpgradeLevel), under the drill
    s.set(2, 1, 2, "crying_obsidian")
    s.set(2, 2, 2, "polished_blackstone_bricks")
    s.set(2, 3, 2, "polished_blackstone_bricks")
    # tower foot: a verdigris (waxed oxidised copper) plus, bone pinnacles with skulls on its corners
    s.set(2, 4, 2, "deepslate_tiles")
    for (x, z) in [(1, 2), (3, 2), (2, 1), (2, 3)]:
        s.set(x, 4, z, "waxed_oxidized_cut_copper")
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        s.set(x, 4, z, "bone_block", axis="y")
        s.set(x, 5, z, "wither_skeleton_skull", rotation=str(TO_CAMERA))
    for (x, z) in [(1, 2), (3, 2), (2, 1), (2, 3)]:
        s.set(x, 5, z, "deepslate_tile_stairs", facing={(1, 2): "east", (3, 2): "west", (2, 1): "south",
                                                        (2, 3): "north"}[(x, z)], half="bottom", shape="straight",
              **DRY_)
    # the kinked shaft
    s.set(2, 5, 2, "deepslate_bricks")
    s.set(2, 6, 2, "deepslate_bricks")
    s.set(2, 7, 2, "crying_obsidian")
    s.set(2, 8, 2, "deepslate_bricks")
    s.set(2, 8, 3, "deepslate_brick_wall")       # the kink: it shoulders back toward the camera
    s.set(2, 9, 3, "deepslate_bricks")
    s.set(1, 10, 3, "deepslate_brick_wall")
    s.set(1, 11, 3, "soul_lantern", **LANTERN)
    s.set(2, 10, 3, "soul_lantern", **LANTERN)
    return s


def grave_wind_generator():
    """A crooked bell tower: deepslate brick shell on basalt corners banded in bone, a barred window with a soul lantern inside on
    the camera side, the dark-oak hub at y=4 (grey rag sails), wall posts round the belfry, a deepslate tile hip roof
    and a spire that leans back with a soul lantern on its tip. 3 x 11 x 3."""
    s = Structure(3, 11, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_deepslate")
    s.set(1, 0, 1, "soul_soil")
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 3 if z == 0 else 4, "polished_basalt")
    for y in (1, 2, 3):
        for (x, z) in EDGES3 + [(1, 1)]:
            if y == 3 and (x, z) != (1, 1):
                s.set(x, y, z, "bone_block", axis="y")    # a bone band (upright: the hub is the only horizontal log)
            else:
                s.set(x, y, z, "deepslate_bricks")
    s.set(1, 1, 1, "deepslate_bricks")
    s.set(1, 2, 1, "soul_lantern", **LANTERN)              # glows through the windows
    s.set(1, 2, 2, "iron_bars", **DRY_)
    s.set(0, 2, 1, "iron_bars", **DRY_)
    s.set(1, 4, 0, "stripped_dark_oak_log", axis="z")      # <- rotor hub (found by WindmillRenderClientEvents)
    for (x, z) in [(1, 1), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 4, z, "deepslate_tiles")
    for (x, z) in CORNERS3:
        for y in (5, 6):
            s.set(x, y, z, "deepslate_brick_wall")
    ring_stairs(s, 7, 0, 2, "deepslate_tile_stairs")
    s.set(1, 7, 1, "deepslate_tiles")
    s.set(1, 8, 1, "deepslate_brick_wall")
    s.set(1, 9, 2, "deepslate_brick_wall")                 # leans back toward the camera
    s.set(1, 10, 2, "soul_lantern", **LANTERN)
    s.set(0, 8, 2, "wither_skeleton_skull", rotation=str(TO_CAMERA))
    return s


def grave_energy_converter():
    """A soul crucible: basalt posts with bone capitals and soul lanterns, a lit blast furnace under a soul campfire
    that burns cyan up through the open crown, chains hanging in the bays and a deepslate tile cornice. 3 x 5 x 3."""
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_deepslate")
    s.set(1, 0, 1, "soul_soil")
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 2, "polished_basalt")
        s.set(x, 3, z, "bone_block", axis="y")
        s.set(x, 4, z, "soul_lantern", **LANTERN)
    s.set(1, 1, 1, "blast_furnace", facing="west", lit="true")
    s.set(1, 2, 1, "soul_campfire", lit="true", facing="north", signal_fire="false", **DRY_)
    for (x, z) in EDGES3:
        s.set(x, 1, z, "deepslate_brick_slab", type="bottom", **DRY_)
        s.set(x, 2, z, "chain", axis="y", **DRY_)
    ring_stairs(s, 3, 0, 2, "deepslate_tile_stairs", half="top")
    for (x, z) in CORNERS3:
        s.set(x, 3, z, "bone_block", axis="y")
    return s


def grave_storage_vault():
    """An ossuary crypt: blackstone corners, barrels on every face, a steep deepslate tile gable with a bone ridge
    running along it, a soul lantern on the ridge and wither skulls on the gable ends. 3 x 6 x 3."""
    s = Structure(3, 6, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_deepslate")
    s.set(1, 0, 1, "deepslate_tiles")
    for (x, z) in CORNERS3:
        pillar(s, x, z, 1, 2, "polished_blackstone_bricks")
    for (x, z, f) in [(1, 0, "north"), (0, 1, "west"), (2, 1, "east"), (1, 2, "south")]:
        s.set(x, 1, z, "barrel", facing=f, open="false")
        s.set(x, 2, z, "deepslate_tiles")
    s.set(1, 1, 1, "bone_block", axis="y")
    s.set(1, 2, 1, "bone_block", axis="y")
    for x in range(3):
        s.set(x, 3, 0, "deepslate_tile_stairs", facing="south", half="bottom", shape="straight", **DRY_)
        s.set(x, 3, 2, "deepslate_tile_stairs", facing="north", half="bottom", shape="straight", **DRY_)
        s.set(x, 4, 1, "bone_block", axis="x")              # the ridge: the ossuary's spine
    for x in (0, 2):
        s.set(x, 3, 1, "deepslate_bricks")
        s.set(x, 5, 1, "wither_skeleton_skull", rotation=str(TO_CAMERA))
    s.set(1, 3, 1, "deepslate_bricks")
    s.set(1, 5, 1, "soul_lantern", **LANTERN)
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
    """A war-totem mill: blackstone-cornered nether brick tower with a shroomlight glowing out of its camera-facing
    window, a rusted copper band, the bone hub at y=4 (red sails on dark oak arms), fence posts round the belfry and
    a nether brick roof with bone spikes on the corners and a skull on a bone totem. 3 x 10 x 3. Every other bone
    block here stands upright (axis y) so the hub stays the only horizontal one."""
    s = Structure(3, 10, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_blackstone")
    s.set(1, 0, 1, "magma_block")
    for (x, z) in CORNERS3:
        for y in range(1, 4 if z == 0 else 5):
            s.set(x, y, z, "polished_blackstone_bricks")
    for y in (1, 2, 3):
        for (x, z) in EDGES3 + [(1, 1)]:
            s.set(x, y, z, RUST if y == 3 and (x, z) != (1, 1) else "nether_bricks")
    s.set(1, 2, 2, "shroomlight")                # forge-glow out of the window the camera sees
    s.set(0, 2, 1, "shroomlight")
    s.set(1, 4, 0, "bone_block", axis="z")      # <- rotor hub (found by WindmillRenderClientEvents)
    for (x, z) in [(1, 1), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 4, z, "nether_bricks")
    for (x, z) in CORNERS3:
        for y in (5, 6):
            s.set(x, y, z, "nether_brick_fence", **DRY_)
    for (x, z) in EDGES3:
        s.set(x, 7, z, "nether_brick_stairs", facing={(1, 0): "south", (0, 1): "east", (2, 1): "west",
                                                      (1, 2): "north"}[(x, z)], half="bottom", shape="straight",
              **DRY_)
    for (x, z) in CORNERS3:
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


def verdant_wind_generator():
    """A tall slender oak on a moss pad, roots splayed at its foot, with its sails' hub growing out of the trunk's
    front: WindmillRenderClientEvents finds the horizontal stripped oak log and spins leaf-green sails on birch arms
    round it, so the tree reads as a living windmill. The crown sits above and behind the sails' sweep (a 3.2 block
    blade tilted back 40 degrees reaches y~7 at the middle column) so they never cut through it. The hub must stay
    the only horizontal stripped log in the structure. 3 x 10 x 3."""
    s = Structure(3, 10, 3)
    verdant_pad(s)
    s.set(1, 0, 1, "oak_wood", axis="y")
    # flared foot: wood on the plus, roots / undergrowth / a lantern in the corners
    for (x, z) in [(1, 0), (1, 2), (0, 1), (2, 1)]:
        s.set(x, 1, z, "oak_wood", axis="y")
    s.set(0, 1, 0, "mangrove_roots", waterlogged="false")
    s.set(2, 1, 0, "fern")
    s.set(0, 1, 2, "flowering_azalea")
    s.set(2, 1, 2, "mossy_cobblestone_wall")
    s.set(2, 2, 2, "lantern", hanging="false", waterlogged="false")
    s.set(1, 2, 2, "mangrove_roots", waterlogged="false")
    # the trunk, with a knot under the hub
    for y in range(1, 9):
        s.set(1, y, 1, "oak_log", axis="y")
    s.set(1, 3, 0, "oak_wood", axis="y")
    s.set(1, 4, 0, "stripped_oak_log", axis="z")       # <- rotor hub (found by the renderer)
    s.set(1, 5, 2, "oak_wood", axis="y")               # a bough on the back balancing the sails
    # the crown: a band of leaves on the back and sides at y=7, a full head at y=8, a flowering crest with a froglight
    for (x, z) in [(0, 2), (1, 2), (2, 2), (0, 1), (2, 1)]:
        s.set(x, 6 if z == 2 else 7, z, "azalea_leaves", **LEAF)
    for (x, z) in [(0, 2), (2, 2)]:
        s.set(x, 7, z, "flowering_azalea_leaves", **LEAF)
    s.set(1, 7, 2, "azalea_leaves", **LEAF)
    for x in range(3):
        for z in range(3):
            if (x, z) != (1, 1):
                s.set(x, 8, z, leaf_for(x, 8, z, 3), **LEAF)
    s.set(1, 9, 1, "verdant_froglight", axis="y")
    for (x, z) in [(1, 0), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 9, z, "flowering_azalea_leaves" if (x + z) % 2 else "azalea_leaves", **LEAF)
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


def brass_steam_boiler():
    """The Brassworks' energy building, a WindGenerator("_brass") with NO rotor hub (so WindmillRenderClientEvents draws
    no sails): a riveted brass boiler drum (a plus of waxed cut copper with a darker band) on a blackstone firebox whose
    lit blast furnace glows at the front, lightning-rod pipes at the drum's corners, a brass gauge (redstone lamp) on the
    front, a blackstone chimney with a lit campfire for the smoke. 3 x 8 x 3."""
    s = Structure(3, 8, 3)
    s.fill(0, 0, 0, 2, 0, 2, "polished_blackstone")
    s.set(1, 0, 1, "deepslate_bricks")
    # the firebox: solid blackstone two high, the furnace mouth at the front
    s.fill(0, 1, 0, 2, 2, 2, SOOT_B)
    s.set(1, 1, 0, "blast_furnace", facing="north", lit="true")
    s.set(1, 2, 0, "iron_bars", north="false", south="false", east="false", west="false", waterlogged="false")
    # the drum: a plus shape three high, a darker band in the middle, pipes in the corners
    arms = [(1, 1), (1, 0), (1, 2), (0, 1), (2, 1)]
    for y in (3, 4, 5):
        for (x, z) in arms:
            s.set(x, y, z, BRASS_D if y == 4 else BRASS)
    s.set(1, 4, 0, "redstone_lamp", lit="false")   # the gauge
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 3, z, "lightning_rod", facing="up", powered="false", waterlogged="false")
    # the top: brass slabs round a chimney, a lit campfire for the smoke
    for (x, z) in arms[1:]:
        s.set(x, 6, z, BRASS_SL, type="bottom", **DRY_)
    s.set(1, 6, 1, SOOT_B)
    s.set(1, 7, 1, "campfire", **CAMPFIRE)
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
                continue
            metal_extractor(P).write(f"{out}/metal_extractor{variant}.nbt")
            metal_extractor_t2(P).write(f"{out}/metal_extractor_t2{variant}.nbt")
            if variant == "_brass":   # a boiler, not a mill: no rotor hub, no sails
                brass_steam_boiler().write(f"{out}/wind_generator{variant}.nbt")
            else:
                wind_generator(P).write(f"{out}/wind_generator{variant}.nbt")
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
