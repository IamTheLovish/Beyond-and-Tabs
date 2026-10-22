package com.solegendary.reignofnether.building.buildings.defence;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The static defences of all seven factions (design/defences.md): per faction a wall segment, a gate, two traps, a T1
 * anti-raid tower and a T3 anti-experimental emplacement ({@link Set}, 42 buildings), then the Oct 21 towers
 * ({@link TowerSet}, 28 more: the enhanced anti-raid tower, the Sky Watch and its enhanced twin, the flak) and the
 * enhanced walls and traps ({@link FortSet}, 21 more) - registered from the end of Buildings (its last static block),
 * each group after the one before, so no existing building's registry id moves; handed to their factions by
 * {@link #registerWithFactions()} (called from Factions.registerBuildings, no hotkeys: every faction's build-menu keys
 * are taken). Mechanics and prices are shared; {@link DefenceStyle} carries each faction's look. Structures:
 * tools/defence_structures.py (via gen_structures.py), icons: tools/defence_icons.py (via gen_hud_icons.py).
 */
public final class Defences {
    private Defences() { }

    /** One faction's set, in build-menu order. */
    public record Set(String factionName, WallSegment wall, WallGate gate, Trap trapA, Trap trapB, DefenceTower raidTower,
                      DefenceTower antiT3) {
        public List<Building> all() {
            return List.of(wall, gate, trapA, trapB, raidTower, antiT3);
        }
    }

    /**
     * One faction's Oct 21 towers (lovish 02:47-02:51: anti-air after flyers sniped his commander; enhanced T1 defences
     * "kinda like in bar"), in build-menu order after its {@link Set}.
     */
    public record TowerSet(String factionName, DefenceTower raidTowerHeavy, DefenceTower skyWatch, DefenceTower skyWatchHeavy,
                           DefenceTower flak) {
        public List<Building> all() {
            return List.of(raidTowerHeavy, skyWatch, skyWatchHeavy, flak);
        }
    }

    /**
     * One faction's enhanced wall and traps (lovish Oct 21 02:51, "kinda like in bar"): a fortified wall and an enhanced
     * twin of each of its two traps, all waiting for a T1 lab - registered after every TowerSet.
     */
    public record FortSet(String factionName, WallSegment fortifiedWall, Trap trapAEnhanced, Trap trapBEnhanced) {
        public List<Building> all() {
            return List.of(fortifiedWall, trapAEnhanced, trapBEnhanced);
        }
    }

    private static final List<Set> SETS = new ArrayList<>();
    private static final List<TowerSet> TOWER_SETS = new ArrayList<>();
    private static final List<FortSet> FORT_SETS = new ArrayList<>();

    public static final Set VILLAGERS = add(new DefenceStyle("villagers", "villagers",
            Blocks.STONE_BRICKS, Blocks.OAK_FENCE_GATE, Blocks.CHISELED_STONE_BRICKS, Blocks.GOLD_BLOCK, Blocks.STONE_PRESSURE_PLATE,
            ParticleTypes.CRIT, ParticleTypes.END_ROD, SoundEvents.CROSSBOW_SHOOT, SoundEvents.TRIDENT_THROW),
        "Stone Rampart", "Portcullis Gate", Trap.Kind.SPIKES, "Caltrop Field", Trap.Kind.SNARE, "Snare Net",
        Blocks.STONE_PRESSURE_PLATE, "Crossbow Turret", "Sunlance Ballista", () -> Buildings.CASTLE);

    public static final Set MONSTERS = add(new DefenceStyle("monsters", "monsters",
            Blocks.BONE_BLOCK, Blocks.DARK_OAK_FENCE_GATE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SOUL_LANTERN, Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE,
            ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SCULK_SOUL, SoundEvents.SKELETON_SHOOT, SoundEvents.WITHER_SHOOT),
        "Bone Palisade", "Ossuary Gate", Trap.Kind.SPIKES, "Grave Spikes", Trap.Kind.SNARE, "Bone Snare",
        Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE, "Soulfire Spire", "Grave Ballista", () -> Buildings.STRONGHOLD);

    public static final Set PIGLINS = add(new DefenceStyle("piglins", "piglins",
            Blocks.NETHER_BRICKS, Blocks.CRIMSON_FENCE_GATE, Blocks.RED_NETHER_BRICKS, Blocks.MAGMA_BLOCK, Blocks.CRIMSON_PRESSURE_PLATE,
            ParticleTypes.SMALL_FLAME, ParticleTypes.LAVA, SoundEvents.ARROW_SHOOT, SoundEvents.GHAST_SHOOT),
        "Skull Palisade", "War Gate", Trap.Kind.SPIKES, "Spike Pit", Trap.Kind.MINE, "Fire Pot",
        Blocks.CRIMSON_PRESSURE_PLATE, "Spear Tower", "Gutbreaker Ballista", () -> Buildings.FORTRESS);

    public static final Set VERDANT = add(new DefenceStyle("verdant_court", "verdant",
            Blocks.MOSSY_STONE_BRICKS, Blocks.BIRCH_FENCE_GATE, Blocks.OAK_LOG, Blocks.DARK_OAK_LOG, Blocks.MOSS_CARPET,
            ParticleTypes.COMPOSTER, ParticleTypes.HAPPY_VILLAGER, SoundEvents.ARROW_SHOOT, SoundEvents.TRIDENT_THROW),
        "Thorn Hedge", "Living Arch", Trap.Kind.SNARE, "Root Snare", Trap.Kind.SPIKES, "Bramble Patch",
        Blocks.JUNGLE_PRESSURE_PLATE, "Thorn Sentinel", "Heartspear Oak", () -> Buildings.HEART_OF_THE_WILD);

    public static final Set TIDE = add(new DefenceStyle("tidewrought", "tide",
            Blocks.PRISMARINE_BRICKS, Blocks.SPRUCE_FENCE_GATE, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, Blocks.DARK_OAK_PRESSURE_PLATE,
            ParticleTypes.SPLASH, ParticleTypes.CLOUD, SoundEvents.DISPENSER_LAUNCH, SoundEvents.GENERIC_EXPLODE),
        "Coral Bulwark", "Sea Gate", Trap.Kind.MINE, "Powder Keg", Trap.Kind.SNARE, "Net Trap",
        Blocks.DARK_OAK_PRESSURE_PLATE, "Swivel Gun Post", "Harpoon Battery", () -> Buildings.ABYSSAL_SHIPYARD);

    public static final Set STORM = add(new DefenceStyle("stormcallers", "storm",
            Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.CHERRY_FENCE_GATE, Blocks.RED_GLAZED_TERRACOTTA, Blocks.WAXED_OXIDIZED_COPPER, Blocks.BAMBOO_PRESSURE_PLATE,
            ParticleTypes.FLAME, ParticleTypes.ELECTRIC_SPARK, SoundEvents.BLAZE_SHOOT, SoundEvents.LIGHTNING_BOLT_IMPACT),
        "Jade Wall", "Moon Gate", Trap.Kind.MINE, "Fire Glyph", Trap.Kind.SNARE, "Binding Seal",
        Blocks.BAMBOO_PRESSURE_PLATE, "Spirit Lantern", "Heaven's Crossbow", () -> Buildings.HEAVENS_GATE);

    // the Brassworks have no T3 lab yet (slice 4): their emplacement waits for the Assembly Works and Tier 3 research
    public static final Set BRASS = add(new DefenceStyle("brassworks", "brass",
            Blocks.WAXED_CUT_COPPER, Blocks.DARK_OAK_FENCE_GATE, Blocks.DEEPSLATE_BRICKS, Blocks.WAXED_COPPER_BLOCK, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE, SoundEvents.FIREWORK_ROCKET_BLAST, SoundEvents.GENERIC_EXPLODE),
        "Brass Plate Wall", "Steam Gate", Trap.Kind.MINE, "Clockwork Mine", Trap.Kind.SNARE, "Bear Trap",
        Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, "Gatling Post", "Breaker Cannon", () -> Buildings.GREAT_FORGE);   // the T3 lab (brass-t3 merged)

    private static Set add(DefenceStyle st, String wallName, String gateName, Trap.Kind kindA, String trapAName,
                           Trap.Kind kindB, String trapBName, net.minecraft.world.level.block.Block trigger,
                           String towerName, String antiT3Name, Supplier<Building> t3Lab) {
        String s = st.suffix();
        Set set = new Set(st.factionName(),
            register(new WallSegment("wall_" + s, wallName, st)),
            register(new WallGate("wall_gate_" + s, gateName, st)),
            register(new Trap(trapId(kindA, s), trapAName, st, kindA, trigger)),
            register(new Trap(trapId(kindB, s), trapBName, st, kindB, trigger)),
            register(new DefenceTower("raid_tower_" + s, towerName, st, DefenceTower.Kind.RAID, null)),
            register(new DefenceTower("anti_t3_" + s, antiT3Name, st, DefenceTower.Kind.ANTI_T3, () -> labList(t3Lab.get()))));
        SETS.add(set);
        return set;
    }

    // ---- the Oct 21 towers: after every Set, so the 42 above keep their registry ids ----
    // t1Labs: the faction's T1 army labs, any one of which unlocks the enhanced towers (the bot kits' armyBuilding)

    public static final TowerSet VILLAGERS_TOWERS = addTowers(VILLAGERS, "Great Crossbow Turret", "Skyward Ballista",
        "Great Skyward Ballista", "Sunburst Spire", () -> List.of(Buildings.BARRACKS));
    public static final TowerSet MONSTERS_TOWERS = addTowers(MONSTERS, "Great Soulfire Spire", "Bone Harpoon Spire",
        "Great Bone Harpoon Spire", "Bat Roost", () -> List.of(Buildings.GRAVEYARD));
    public static final TowerSet PIGLINS_TOWERS = addTowers(PIGLINS, "Great Spear Tower", "Harpoon Totem",
        "Great Harpoon Totem", "Fire-Pot Mortar", () -> List.of(Buildings.PORTAL_BASIC, Buildings.PORTAL_MILITARY));
    public static final TowerSet VERDANT_TOWERS = addTowers(VERDANT, "Elder Thorn Sentinel", "Thornspitter Pine",
        "Elder Thornspitter Pine", "Seed-Pod Burster", () -> List.of(Buildings.GROVE));
    public static final TowerSet TIDE_TOWERS = addTowers(TIDE, "Twin Swivel Gun Post", "Swivel Mast",
        "Twin Swivel Mast", "Deck Flak", () -> List.of(Buildings.SLIPWAY));
    public static final TowerSet STORM_TOWERS = addTowers(STORM, "Great Spirit Lantern", "Kite Lantern",
        "Great Kite Lantern", "Thunder Kites", () -> List.of(Buildings.LOTUS_ACADEMY));
    public static final TowerSet BRASS_TOWERS = addTowers(BRASS, "Heavy Gatling Post", "Sky Gun Post",
        "Twin Sky Gun Post", "Steam Flak", () -> List.of(Buildings.MACHINE_SHOP));

    private static TowerSet addTowers(Set base, String heavyRaidName, String skyName, String heavySkyName, String flakName,
                                      Supplier<List<Building>> t1Labs) {
        DefenceStyle st = base.raidTower().style;
        String s = st.suffix();
        TowerSet set = new TowerSet(base.factionName(),
            register(new DefenceTower("raid_tower_heavy_" + s, heavyRaidName, st, DefenceTower.Kind.RAID_HEAVY, t1Labs)),
            register(new DefenceTower("sky_watch_" + s, skyName, st, DefenceTower.Kind.SKY_WATCH, null)),
            register(new DefenceTower("sky_watch_heavy_" + s, heavySkyName, st, DefenceTower.Kind.SKY_WATCH_HEAVY, t1Labs)),
            register(new DefenceTower("flak_" + s, flakName, st, DefenceTower.Kind.FLAK, null)));
        TOWER_SETS.add(set);
        return set;
    }

    // ---- the enhanced walls and traps: after every TowerSet, so the towers above keep their registry ids ----

    public static final FortSet VILLAGERS_FORT = addFort(VILLAGERS, VILLAGERS_TOWERS, "Fortified Rampart", "Iron Caltrop Field", "Chain Snare Net");
    public static final FortSet MONSTERS_FORT = addFort(MONSTERS, MONSTERS_TOWERS, "Bone Bastion Wall", "Barrow Spikes", "Grasping Grave");
    public static final FortSet PIGLINS_FORT = addFort(PIGLINS, PIGLINS_TOWERS, "Iron Skull Palisade", "Deep Spike Pit", "Great Fire Pot");
    public static final FortSet VERDANT_FORT = addFort(VERDANT, VERDANT_TOWERS, "Ironbark Hedge", "Strangling Roots", "Thornbramble Thicket");
    public static final FortSet TIDE_FORT = addFort(TIDE, TIDE_TOWERS, "Reef Bulwark", "Twin Powder Keg", "Kraken Net");
    public static final FortSet STORM_FORT = addFort(STORM, STORM_TOWERS, "Dragon Jade Wall", "Inferno Glyph", "Heavenly Seal");
    public static final FortSet BRASS_FORT = addFort(BRASS, BRASS_TOWERS, "Riveted Bulkhead", "Double Clockwork Mine", "Steam Bear Trap");

    private static FortSet addFort(Set base, TowerSet towers, String wallName, String trapAName, String trapBName) {
        DefenceStyle st = base.wall().style;
        String s = st.suffix();
        // the enhanced towers' T1-lab gate, reused: one list of a faction's T1 labs
        Supplier<List<Building>> t1Labs = towers.raidTowerHeavy().requiredLabs();
        FortSet set = new FortSet(base.factionName(),
            register(new WallSegment("wall_fortified_" + s, wallName, st, t1Labs)),
            register(new Trap(enhancedTrapId(base.trapA().kind, s), trapAName, st, base.trapA().kind, base.trapA().trigger, t1Labs)),
            register(new Trap(enhancedTrapId(base.trapB().kind, s), trapBName, st, base.trapB().kind, base.trapB().trigger, t1Labs)));
        FORT_SETS.add(set);
        return set;
    }

    public static String enhancedTrapId(Trap.Kind kind, String suffix) {
        return "trap_" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_enhanced_" + suffix;
    }

    /** A lab as a one-element list, or empty if it is not registered (yet). */
    private static List<Building> labList(@Nullable Building lab) {
        return lab == null ? List.of() : List.of(lab);
    }

    public static String trapId(Trap.Kind kind, String suffix) {
        return "trap_" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_" + suffix;
    }

    private static <T extends DefenceBuilding> T register(T building) {
        return Registry.register(ReignOfNetherRegistries.BUILDING, ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, building.id), building);
    }

    /** Forces the class (and so the registrations) to load: Buildings' last static block calls it. */
    public static void init() { }

    public static List<Set> sets() {
        return Collections.unmodifiableList(SETS);
    }

    public static List<TowerSet> towerSets() {
        return Collections.unmodifiableList(TOWER_SETS);
    }

    public static List<FortSet> fortSets() {
        return Collections.unmodifiableList(FORT_SETS);
    }

    /** The faction's enhanced wall and traps, or null (neutral / random / none). */
    @Nullable
    public static FortSet fortOf(@Nullable Faction faction) {
        Set base = of(faction);
        if (base == null)
            return null;
        for (FortSet set : FORT_SETS)
            if (set.factionName().equals(base.factionName()))
                return set;
        return null;
    }

    /** The faction's Oct 21 towers, or null (neutral / random / none). */
    @Nullable
    public static TowerSet towersOf(@Nullable Faction faction) {
        Set base = of(faction);
        if (base == null)
            return null;
        for (TowerSet set : TOWER_SETS)
            if (set.factionName().equals(base.factionName()))
                return set;
        return null;
    }

    /** The faction's set, or null (neutral / random / none). */
    @Nullable
    public static Set of(@Nullable Faction faction) {
        if (faction == null || faction.key == null)
            return null;
        for (Set set : SETS)
            if (set.factionName().equals(faction.key.getPath()))
                return set;
        return null;
    }

    /** Gives every set to its faction's build menu (no hotkeys). Called from Factions.registerBuildings. */
    public static void registerWithFactions() {
        for (Set set : SETS) {
            Faction f = Factions.getFaction(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, set.factionName()));
            if (f == null || f == Factions.NONE)
                continue;
            for (Building b : set.all())
                Factions.registerBuildings(f, b, com.solegendary.reignofnether.building.BuildGroup.DEFENCE);
            for (TowerSet towers : TOWER_SETS)
                if (towers.factionName().equals(set.factionName()))
                    for (Building b : towers.all())
                        Factions.registerBuildings(f, b, com.solegendary.reignofnether.building.BuildGroup.DEFENCE);
            for (FortSet fort : FORT_SETS)
                if (fort.factionName().equals(set.factionName()))
                    for (Building b : fort.all())
                        Factions.registerBuildings(f, b, com.solegendary.reignofnether.building.BuildGroup.DEFENCE);
        }
    }

    /** Is this building one of the 91 static defences? */
    public static boolean isDefence(@Nullable Building b) {
        return b instanceof DefenceBuilding;
    }
}
