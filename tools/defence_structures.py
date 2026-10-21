#!/usr/bin/env python3
"""The static defences' structures (design/defences.md) for all seven factions: a wall segment, a gate, two traps, a T1
anti-raid tower and a T3 anti-experimental emplacement each. Called by tools/gen_structures.py (which writes both the
assets/ and data/ trees); not run on its own.

Footprints are shared so the mechanics read the same for every faction (only the palette differs):
  wall_<s>        2 x 3 x 2   two solid courses and a wall/fence course on top: never standable, never jumpable
  wall_gate_<s>   2 x 4 x 2   a solid foundation, four fence gates (shut as built - GatePlacement swings them), air for
                              the head, a lintel; open gates have no collision, so units walk through the 2-wide gap
  trap_*_<s>      3 x 1 x 3   flat ground cover (carpet / moss / petals) round a pressure plate - all passable
  raid_tower_<s>  3 x 5 x 3   solid to the top: nothing to stand in
  anti_t3_<s>     5 x 6 x 5   a 2-high plinth (no step onto it), a fence-like ring round the weapon deck
  Oct 21 towers (lovish: anti-air after flyers sniped his commander; enhanced T1 defences "kinda like in bar") -
  placeholders in each faction's palette until Lane B draws them:
  raid_tower_heavy_<s> 3 x 6 x 3   the raid tower one course taller, an accent band, two weapons
  sky_watch_<s>        3 x 6 x 3   a slim tower with a full top deck and the barrel pointing straight up
  sky_watch_heavy_<s>  3 x 7 x 3   taller, an accent band, corner caps round the upward barrel
  flak_<s>             4 x 6 x 4   a low battery: 2-high plinth, fence-like ring, four barrels pointing up
  wall_fortified_<s>   2 x 3 x 2   the wall's footprint (so it mixes into a plain line): plinth, a chiseled course, cap
  trap_*_enhanced_<s>  3 x 1 x 3   the trap's patch with its two covers swapped, so a player tells the enhanced one apart

Pocket-free (the structures_have_no_unit_pockets GameTest): nothing a ground unit can walk into has a way in without a
way out - the walls and towers are solid, the traps flat, the gate a straight 2-wide passage. Nothing changes by itself
during a match: copper is waxed, leaves are persistent, no gravity blocks on edges.
"""

LEAF = dict(persistent="true", distance="1", waterlogged="false")
DRY = dict(waterlogged="false")

# per faction: wall base / body / body2 (one in four) / cap (a wall or fence block: fence-like to the pathfinder), the
# gate and its lintel, the tower's plinth / body / top / weapon, the emplacement's accent / core / barrel, the trap's
# ground cover and trigger plate. Block names, optionally with a props dict as (name, props).
FACTIONS = {
    # Sunforged Kingdom: white stone, gold, a crossbow turret and a sunlance ballista
    "villagers": dict(
        base="stone_bricks", body="stone_bricks", body2="cracked_stone_bricks", cap="stone_brick_wall",
        gate="oak_fence_gate", lintel="chiseled_stone_bricks",
        plinth="stone_bricks", tbody="smooth_stone", top="chiseled_stone_bricks", weapon=("dispenser", dict(facing="north", triggered="false")),
        accent="gold_block", core="gold_block", barrel=("end_rod", dict(facing="up")),
        cover="brown_carpet", cover2="green_carpet", trigger="stone_pressure_plate"),
    # Gravebound: blackstone and bone, soul-fire
    "monsters": dict(
        base="polished_blackstone_bricks", body="bone_block", body2="polished_blackstone_bricks", cap="polished_blackstone_brick_wall",
        gate="dark_oak_fence_gate", lintel="bone_block",
        plinth="polished_blackstone_bricks", tbody="bone_block", top="polished_blackstone", weapon=("soul_lantern", dict(hanging="false", **DRY)),
        accent="crying_obsidian", core="obsidian", barrel=("soul_lantern", dict(hanging="false", **DRY)),
        cover="gray_carpet", cover2="black_carpet", trigger="polished_blackstone_pressure_plate"),
    # Ironhide Horde: nether brick, a spear tip on every tower
    "piglins": dict(
        base="nether_bricks", body="red_nether_bricks", body2="nether_bricks", cap="nether_brick_fence",
        gate="crimson_fence_gate", lintel="nether_bricks",
        plinth="nether_bricks", tbody="red_nether_bricks", top="chiseled_nether_bricks",
        weapon=("pointed_dripstone", dict(thickness="tip", vertical_direction="up", **DRY)),
        accent="gilded_blackstone", core="gilded_blackstone",
        barrel=("pointed_dripstone", dict(thickness="tip", vertical_direction="up", **DRY)),
        cover="red_carpet", cover2="brown_carpet", trigger="crimson_pressure_plate"),
    # Verdant Court: a hedge of persistent azalea leaves on mossy stone, living-wood towers
    "verdant": dict(
        base="mossy_stone_bricks", body=("azalea_leaves", LEAF), body2=("flowering_azalea_leaves", LEAF),
        cap=("flowering_azalea_leaves", LEAF),
        gate="birch_fence_gate", lintel=("oak_log", dict(axis="x")),
        plinth="mossy_stone_bricks", tbody=("oak_log", dict(axis="y")), top=("oak_wood", dict(axis="y")),
        weapon="flowering_azalea",
        accent="moss_block", core=("dark_oak_log", dict(axis="y")), barrel=("end_rod", dict(facing="up")),
        cover="moss_carpet", cover2="moss_carpet", trigger="jungle_pressure_plate"),
    # Tidewrought: prismarine and dead coral, sandstone aprons, a swivel gun and a harpoon battery
    "tide": dict(
        base="prismarine_bricks", body="prismarine", body2="dead_brain_coral_block", cap="prismarine_wall",
        gate="spruce_fence_gate", lintel="dark_prismarine",
        plinth="smooth_sandstone", tbody="prismarine_bricks", top="dark_prismarine", weapon=("dispenser", dict(facing="north", triggered="false")),
        accent="sea_lantern", core="waxed_copper_block", barrel=("lightning_rod", dict(facing="up", powered="false", **DRY)),
        cover="yellow_carpet", cover2="white_carpet", trigger="dark_oak_pressure_plate"),
    # Stormcallers: andesite plinths, jade (waxed oxidised copper), red lacquer, gold
    "storm": dict(
        base="polished_andesite", body="waxed_oxidized_cut_copper", body2="red_terracotta", cap="andesite_wall",
        gate="cherry_fence_gate", lintel="red_terracotta",
        plinth="polished_andesite", tbody="red_terracotta", top="waxed_oxidized_cut_copper", weapon=("lantern", dict(hanging="false", **DRY)),
        accent="gold_block", core="waxed_oxidized_copper", barrel=("lightning_rod", dict(facing="up", powered="false", **DRY)),
        cover=("pink_petals", dict(facing="north", flower_amount="4")), cover2=("pink_petals", dict(facing="east", flower_amount="3")),
        trigger="bamboo_pressure_plate"),
    # Brassworks: deepslate and soot, riveted brass plate
    "brass": dict(
        base="deepslate_bricks", body="waxed_cut_copper", body2="polished_blackstone_bricks", cap="polished_blackstone_wall",
        gate="dark_oak_fence_gate", lintel="polished_blackstone_bricks",
        plinth="deepslate_bricks", tbody="polished_blackstone_bricks", top="waxed_cut_copper", weapon=("dispenser", dict(facing="north", triggered="false")),
        accent="waxed_copper_block", core="waxed_copper_block", barrel=("lightning_rod", dict(facing="up", powered="false", **DRY)),
        cover="black_carpet", cover2="gray_carpet", trigger="heavy_weighted_pressure_plate"),
}

# the traps each faction gets (Defences.java must agree): two of spikes / snare / mine
TRAPS = {
    "villagers": ("spikes", "snare"), "monsters": ("spikes", "snare"), "piglins": ("spikes", "mine"),
    "verdant": ("snare", "spikes"), "tide": ("mine", "snare"), "storm": ("mine", "snare"), "brass": ("mine", "snare"),
}


def _put(s, x, y, z, block):
    if isinstance(block, tuple):
        s.set(x, y, z, block[0], **block[1])
    else:
        s.set(x, y, z, block)


def wall(Structure, P):
    s = Structure(2, 3, 2)
    for x in range(2):
        for z in range(2):
            _put(s, x, 0, z, P["base"])
            _put(s, x, 1, z, P["body2"] if (x + z) % 2 == 0 and x == 0 else P["body"])
            _put(s, x, 2, z, P["cap"])
    return s


def gate(Structure, P):
    s = Structure(2, 4, 2)
    for x in range(2):
        for z in range(2):
            _put(s, x, 0, z, P["base"])
            s.set(x, 1, z, P["gate"], facing="north", in_wall="false", open="false", powered="false")
            s.set(x, 2, z, "air")
            _put(s, x, 3, z, P["lintel"])
    return s


def trap(Structure, P, kind):
    s = Structure(3, 1, 3)
    for x in range(3):
        for z in range(3):
            if x == 1 and z == 1:
                s.set(x, 0, z, P["trigger"], **({"power": "0"} if "weighted" in P["trigger"] else {"powered": "false"}))
            else:
                # the spikes / mine / snare patches differ in the mix of the two covers, so a player tells his own apart
                pick = {"spikes": (x + z) % 2 == 0, "snare": x == 1 or z == 1, "mine": (x * z) % 2 == 1}[kind]
                _put(s, x, 0, z, P["cover2"] if pick else P["cover"])
    return s


def raid_tower(Structure, P):
    s = Structure(3, 5, 3)
    for x in range(3):
        for z in range(3):
            _put(s, x, 0, z, P["plinth"])
            _put(s, x, 1, z, P["plinth"])
            _put(s, x, 2, z, P["tbody"])
            corner = x != 1 and z != 1
            _put(s, x, 3, z, P["cap"] if corner else P["top"])
    _put(s, 1, 4, 1, P["weapon"])
    for x in range(3):
        for z in range(3):
            if (x, z) != (1, 1):
                s.set(x, 4, z, "air")
    return s


def anti_t3(Structure, P):
    s = Structure(5, 6, 5)
    for x in range(5):
        for z in range(5):
            _put(s, x, 0, z, P["plinth"])
            _put(s, x, 1, z, P["plinth"])
            corner = x in (0, 4) and z in (0, 4)
            _put(s, x, 2, z, P["accent"] if corner else P["tbody"])
            ring = x in (0, 4) or z in (0, 4)
            _put(s, x, 3, z, P["cap"] if ring else P["top"])
    for x in range(1, 4):
        for z in range(1, 4):
            if (x, z) == (2, 2):
                _put(s, x, 4, z, P["core"])
            elif x == 2 or z == 2:
                _put(s, x, 4, z, P["cap"])   # the weapon's arms: fence-like, so its deck is never a perch
            else:
                s.set(x, 4, z, "air")
    _put(s, 2, 5, 2, P["barrel"])
    for x in range(5):
        for z in range(5):
            for y in (4, 5):
                if (x, y, z) not in s.blocks:
                    s.set(x, y, z, "air")
    return s


def raid_tower_heavy(Structure, P):
    s = Structure(3, 6, 3)
    for x in range(3):
        for z in range(3):
            _put(s, x, 0, z, P["plinth"])
            _put(s, x, 1, z, P["plinth"])
            corner = x != 1 and z != 1
            _put(s, x, 2, z, P["accent"] if corner else P["tbody"])
            _put(s, x, 3, z, P["tbody"])
            _put(s, x, 4, z, P["cap"] if corner else P["top"])
    _put(s, 1, 5, 1, P["weapon"])
    for x in range(3):
        for z in range(3):
            if (x, z) != (1, 1):
                s.set(x, 5, z, "air")
    return s


def sky_watch(Structure, P):
    s = Structure(3, 6, 3)
    for x in range(3):
        for z in range(3):
            _put(s, x, 0, z, P["plinth"])
            _put(s, x, 1, z, P["plinth"])
            _put(s, x, 2, z, P["tbody"])
            _put(s, x, 3, z, P["tbody"])
            _put(s, x, 4, z, P["top"])
    _put(s, 1, 5, 1, P["barrel"])
    for x in range(3):
        for z in range(3):
            if (x, z) != (1, 1):
                s.set(x, 5, z, "air")
    return s


def sky_watch_heavy(Structure, P):
    s = Structure(3, 7, 3)
    for x in range(3):
        for z in range(3):
            corner = x != 1 and z != 1
            _put(s, x, 0, z, P["plinth"])
            _put(s, x, 1, z, P["plinth"])
            _put(s, x, 2, z, P["tbody"])
            _put(s, x, 3, z, P["accent"] if corner else P["tbody"])
            _put(s, x, 4, z, P["tbody"])
            _put(s, x, 5, z, P["cap"] if corner else P["top"])
    _put(s, 1, 6, 1, P["barrel"])
    for x in range(3):
        for z in range(3):
            if (x, z) != (1, 1):
                s.set(x, 6, z, "air")
    return s


def flak(Structure, P):
    s = Structure(4, 6, 4)
    for x in range(4):
        for z in range(4):
            _put(s, x, 0, z, P["plinth"])
            _put(s, x, 1, z, P["plinth"])
            corner = x in (0, 3) and z in (0, 3)
            _put(s, x, 2, z, P["accent"] if corner else P["tbody"])
            ring = x in (0, 3) or z in (0, 3)
            _put(s, x, 3, z, P["cap"] if ring else P["top"])
    for x in range(4):
        for z in range(4):
            inner = x in (1, 2) and z in (1, 2)
            _put(s, x, 4, z, P["core"]) if inner else s.set(x, 4, z, "air")
            _put(s, x, 5, z, P["barrel"]) if inner else s.set(x, 5, z, "air")
    return s


def wall_fortified(Structure, P):
    s = Structure(2, 3, 2)
    for x in range(2):
        for z in range(2):
            _put(s, x, 0, z, P["plinth"])
            _put(s, x, 1, z, P["top"])
            _put(s, x, 2, z, P["cap"])
    return s


def trap_enhanced(Structure, P, kind):
    return trap(Structure, dict(P, cover=P["cover2"], cover2=P["cover"]), kind)


def write_all(out, Structure):
    for suffix, P in FACTIONS.items():
        wall(Structure, P).write(f"{out}/wall_{suffix}.nbt")
        gate(Structure, P).write(f"{out}/wall_gate_{suffix}.nbt")
        for kind in TRAPS[suffix]:
            trap(Structure, P, kind).write(f"{out}/trap_{kind}_{suffix}.nbt")
        raid_tower(Structure, P).write(f"{out}/raid_tower_{suffix}.nbt")
        anti_t3(Structure, P).write(f"{out}/anti_t3_{suffix}.nbt")
        raid_tower_heavy(Structure, P).write(f"{out}/raid_tower_heavy_{suffix}.nbt")
        sky_watch(Structure, P).write(f"{out}/sky_watch_{suffix}.nbt")
        sky_watch_heavy(Structure, P).write(f"{out}/sky_watch_heavy_{suffix}.nbt")
        flak(Structure, P).write(f"{out}/flak_{suffix}.nbt")
        wall_fortified(Structure, P).write(f"{out}/wall_fortified_{suffix}.nbt")
        for kind in TRAPS[suffix]:
            trap_enhanced(Structure, P, kind).write(f"{out}/trap_{kind}_enhanced_{suffix}.nbt")
