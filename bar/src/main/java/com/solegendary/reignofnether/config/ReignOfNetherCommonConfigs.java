package com.solegendary.reignofnether.config;

import com.solegendary.reignofnether.resources.ResourceCosts;
import net.minecraftforge.common.ForgeConfigSpec;

/*
    Class responsible for defining all configurable ResourceCosts; this occurs during commonsetup
 */
public class ReignOfNetherCommonConfigs {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    // A world that remembers: the [world_memory] block below (read by worldmemory/WorldMemoryConfig at server start)
    public static final ForgeConfigSpec.ConfigValue<Boolean> WORLD_MEMORY_SCARS;
    public static final ForgeConfigSpec.ConfigValue<Integer> WORLD_MEMORY_SCORCH_HALF_LIFE, WORLD_MEMORY_SCORCH_FLOOR,
        WORLD_MEMORY_CRATER_HALF_LIFE, WORLD_MEMORY_CRATER_FLOOR;

    static {
        BUILDER.push("Configuration File");
        BUILDER.pop();
        BUILDER.comment("Unit cost configurations");
        //*Units*
        //Monsters
        BUILDER.comment("Monsters");
        UnitCosts.CREEPER.define(BUILDER);
        UnitCosts.ZOMBIE.define(BUILDER);
        UnitCosts.ZOMBIE_VILLAGER.define(BUILDER);
        UnitCosts.BAT.define(BUILDER);
        UnitCosts.SKELETON.define(BUILDER);
        UnitCosts.STRAY.define(BUILDER);
        UnitCosts.BOGGED.define(BUILDER);
        UnitCosts.HUSK.define(BUILDER);
        UnitCosts.DROWNED.define(BUILDER);
        UnitCosts.SPIDER.define(BUILDER);
        UnitCosts.POISON_SPIDER.define(BUILDER);
        UnitCosts.WRAITH.define(BUILDER);
        UnitCosts.SLIME.define(BUILDER);
        UnitCosts.WARDEN.define(BUILDER);
        UnitCosts.ZOMBIE_PIGLIN.define(BUILDER);
        UnitCosts.ZOGLIN.define(BUILDER);
        UnitCosts.NECROMANCER.define(BUILDER);
        UnitCosts.WRETCHED_WRAITH.define(BUILDER);
        //VillagersFaction
        BUILDER.comment("VillagersFaction");
        UnitCosts.VILLAGER.define(BUILDER);
        UnitCosts.SCOUT_DOG.define(BUILDER);
        UnitCosts.SCOUT_CAT.define(BUILDER);
        UnitCosts.MILITIA.define(BUILDER);
        UnitCosts.IRON_GOLEM.define(BUILDER);
        UnitCosts.PILLAGER.define(BUILDER);
        UnitCosts.WINDCALLER.define(BUILDER);
        UnitCosts.VINDICATOR.define(BUILDER);
        UnitCosts.WITCH.define(BUILDER);
        UnitCosts.EVOKER.define(BUILDER);
        UnitCosts.RAVAGER.define(BUILDER);
        UnitCosts.WAR_MAMMOTH.define(BUILDER);
        UnitCosts.BONEWRIGHT.define(BUILDER);
        UnitCosts.EMBALMER.define(BUILDER);
        UnitCosts.ROYAL_ARCHITECT.define(BUILDER);
        UnitCosts.ELDER_DRUID.define(BUILDER);
        UnitCosts.STAG_LANCER.define(BUILDER);
        UnitCosts.SHADE_RANGER.define(BUILDER);
        UnitCosts.ELDER_TREANT.define(BUILDER);
        UnitCosts.WISP_CHOIR.define(BUILDER);
        UnitCosts.BLOOM_PRIESTESS.define(BUILDER);
        UnitCosts.SUN_COLOSSUS.define(BUILDER);
        UnitCosts.WORLD_TREE_WALKER.define(BUILDER);
        UnitCosts.SEEDSHAPER.define(BUILDER);
        UnitCosts.SHIPWRIGHT.define(BUILDER);
        UnitCosts.GULL_SPOTTER.define(BUILDER);
        UnitCosts.CUTLASS_RAIDER.define(BUILDER);
        UnitCosts.REEF_GUARD.define(BUILDER);
        UnitCosts.BOMBARD_CREW.define(BUILDER);
        UnitCosts.SIEGE_LOBBER.define(BUILDER);
        UnitCosts.TIDE_PRIEST.define(BUILDER);
        UnitCosts.HARPOONER.define(BUILDER);
        UnitCosts.FLAK_SWIVEL.define(BUILDER);
        UnitCosts.MUDSKIPPER_SLOOP.define(BUILDER);
        UnitCosts.HARBOURMASTER.define(BUILDER);
        UnitCosts.KRAKEN_CALLER.define(BUILDER);
        UnitCosts.MORTAR_GALLEY.define(BUILDER);
        UnitCosts.SKY_CORSAIR.define(BUILDER);
        UnitCosts.LEVIATHAN.define(BUILDER);
        UnitCosts.GEOMANCER.define(BUILDER);
        UnitCosts.PAPER_CRANE.define(BUILDER);
        UnitCosts.BLADE_DANCER.define(BUILDER);
        UnitCosts.FIRE_ADEPT.define(BUILDER);
        UnitCosts.JADE_GUARD.define(BUILDER);
        UnitCosts.WIND_MONK.define(BUILDER);
        UnitCosts.INK_SCRIBE.define(BUILDER);
        UnitCosts.FROST_ADEPT.define(BUILDER);
        UnitCosts.ROCKET_BATTERY.define(BUILDER);
        UnitCosts.LEY_MASTER.define(BUILDER);
        UnitCosts.THUNDER_DRAGON.define(BUILDER);
        UnitCosts.EARTHSHAKER.define(BUILDER);
        UnitCosts.GRAVITY_MONK.define(BUILDER);
        UnitCosts.MIRROR_MAGE.define(BUILDER);
        UnitCosts.SPARK_SAGE.define(BUILDER);
        UnitCosts.SEER.define(BUILDER);
        UnitCosts.LANTERN_FORTRESS.define(BUILDER);
        UnitCosts.PHOENIX.define(BUILDER);
        UnitCosts.CELESTIAL_DRAGON.define(BUILDER);
        UnitCosts.TINKER.define(BUILDER);
        UnitCosts.GYRO_SCOUT.define(BUILDER);
        UnitCosts.STEAM_RUNNER.define(BUILDER);
        UnitCosts.RIFLE_SQUAD.define(BUILDER);
        UnitCosts.IRON_WARDEN.define(BUILDER);
        UnitCosts.FLAK_CART.define(BUILDER);
        UnitCosts.MORTAR_CRAWLER.define(BUILDER);
        UnitCosts.SHIELD_PROJECTOR.define(BUILDER);
        UnitCosts.SAPPER.define(BUILDER);
        UnitCosts.DEPLOYED_TURRET.define(BUILDER);
        UnitCosts.ENGINEER_CAPTAIN.define(BUILDER);
        UnitCosts.STEAM_JUGGERNAUT.define(BUILDER);
        UnitCosts.TESLA_WALKER.define(BUILDER);
        UnitCosts.GYROCOPTER_GUNSHIP.define(BUILDER);
        UnitCosts.DRILL_BURROWER.define(BUILDER);
        UnitCosts.REPAIR_CRAWLER.define(BUILDER);
        UnitCosts.CLOCKWORK_SPIDER.define(BUILDER);
        UnitCosts.IRON_LEVIATHAN_ENGINE.define(BUILDER);
        // big-units: the T2 beasts (design/big_units.md)
        UnitCosts.ORIFLAMME_ELEPHANT.define(BUILDER);
        UnitCosts.GOREHORN_RHINO.define(BUILDER);
        UnitCosts.OSSUARY_BEHEMOTH.define(BUILDER);
        UnitCosts.MOSSBACK_BISON.define(BUILDER);
        // beasts-2: the other three T2 beasts
        UnitCosts.SHELLBACK_TURTLE.define(BUILDER);
        UnitCosts.JADE_QILIN.define(BUILDER);
        UnitCosts.STEAM_HAULER.define(BUILDER);
        // combat engineers (design/combat_engineers.md)
        UnitCosts.FIELD_MASON.define(BUILDER);
        UnitCosts.BONESETTER.define(BUILDER);
        UnitCosts.WRECKER.define(BUILDER);
        UnitCosts.ROOTWARDEN.define(BUILDER);
        UnitCosts.SALVAGER.define(BUILDER);
        UnitCosts.JADE_MENDER.define(BUILDER);
        UnitCosts.PIONEER.define(BUILDER);
        UnitCosts.FOX_COURIER.define(BUILDER);
        UnitCosts.MOONWELL_BEARER.define(BUILDER);
        UnitCosts.OWL_WATCHER.define(BUILDER);
        UnitCosts.HIVE_KEEPER.define(BUILDER);
        UnitCosts.LEAFBLADE.define(BUILDER);
        UnitCosts.THORNBOW.define(BUILDER);
        UnitCosts.SENTINEL_TREANT.define(BUILDER);
        UnitCosts.BONE_DRAGON.define(BUILDER);
        UnitCosts.ROYAL_GUARD.define(BUILDER);
        UnitCosts.ENCHANTER.define(BUILDER);
        //Piglins
        BUILDER.comment("Piglins");
        UnitCosts.GRUNT.define(BUILDER);
        UnitCosts.STRIDER.define(BUILDER);
        UnitCosts.BRUTE.define(BUILDER);
        UnitCosts.HEADHUNTER.define(BUILDER);
        UnitCosts.MARAUDER.define(BUILDER);
        UnitCosts.HOGLIN.define(BUILDER);
        UnitCosts.BLAZE.define(BUILDER);
        UnitCosts.WITHER_SKELETON.define(BUILDER);
        UnitCosts.GHAST.define(BUILDER);
        UnitCosts.MAGMA_CUBE.define(BUILDER);
        UnitCosts.PIGLIN_MERCHANT.define(BUILDER);
        UnitCosts.WILDFIRE.define(BUILDER);
        //Neutral
        UnitCosts.ENDERMAN.define(BUILDER);
        UnitCosts.POLAR_BEAR.define(BUILDER);
        UnitCosts.GRIZZLY_BEAR.define(BUILDER);
        UnitCosts.PANDA.define(BUILDER);
        UnitCosts.WOLF.define(BUILDER);
        UnitCosts.LLAMA.define(BUILDER);
        UnitCosts.BEE.define(BUILDER);
        UnitCosts.KILLER_RABBIT.define(BUILDER);
        UnitCosts.SILVERFISH.define(BUILDER);

        UnitCosts.HERO_BASE_REVIVE_COST.define(BUILDER);
        UnitCosts.HERO_EXTRA_REVIVE_COST_PER_LEVEL.define(BUILDER);

        //*Buildings*
        BUILDER.comment("Building Cost Configurations");
        BuildingCosts.BEACON.define(BUILDER);
        BuildingCosts.STOCKPILE.define(BUILDER);
        BuildingCosts.METAL_EXTRACTOR.define(BUILDER);
        BuildingCosts.WIND_GENERATOR.define(BUILDER);
        BuildingCosts.ENERGY_CONVERTER.define(BUILDER);
        BuildingCosts.STORAGE_VAULT.define(BUILDER);
        BuildingCosts.OAK_BRIDGE.define(BUILDER);
        BuildingCosts.SPRUCE_BRIDGE.define(BUILDER);
        BuildingCosts.BLACKSTONE_BRIDGE.define(BUILDER);
        //Monsters
        BUILDER.comment("Monsters");
        BuildingCosts.MAUSOLEUM.define(BUILDER);
        BuildingCosts.HAUNTED_HOUSE.define(BUILDER);
        BuildingCosts.PUMPKIN_FARM.define(BUILDER);
        BuildingCosts.SCULK_CATALYST.define(BUILDER);
        BuildingCosts.GRAVEYARD.define(BUILDER);
        BuildingCosts.SPIDER_LAIR.define(BUILDER);
        BuildingCosts.DUNGEON.define(BUILDER);
        BuildingCosts.LABORATORY.define(BUILDER);
        BuildingCosts.DARK_WATCHTOWER.define(BUILDER);
        BuildingCosts.SLIME_PIT.define(BUILDER);
        BuildingCosts.STRONGHOLD.define(BUILDER);
        BuildingCosts.ALTAR_OF_DARKNESS.define(BUILDER);
        BuildingCosts.MONSTER_MARKET.define(BUILDER);
        //VillagersFaction
        BUILDER.comment("VillagersFaction");
        BuildingCosts.TOWN_CENTRE.define(BUILDER);
        BuildingCosts.HEARTWOOD_HALL.define(BUILDER);
        BuildingCosts.GROVE.define(BUILDER);
        BuildingCosts.WRECK_HARBOUR.define(BUILDER);
        BuildingCosts.SLIPWAY.define(BUILDER);
        BuildingCosts.DRYDOCK.define(BUILDER);
        BuildingCosts.LIGHTHOUSE_BATTERY.define(BUILDER);
        BuildingCosts.ABYSSAL_SHIPYARD.define(BUILDER);
        BuildingCosts.JADE_PAGODA.define(BUILDER);
        BuildingCosts.LOTUS_ACADEMY.define(BUILDER);
        BuildingCosts.CELESTIAL_OBSERVATORY.define(BUILDER);
        BuildingCosts.HEAVENS_GATE.define(BUILDER);
        BuildingCosts.FOUNDRY_HALL.define(BUILDER);
        BuildingCosts.MACHINE_SHOP.define(BUILDER);
        BuildingCosts.ASSEMBLY_WORKS.define(BUILDER);
        BuildingCosts.RAIL_BATTERY.define(BUILDER);
        BuildingCosts.GREAT_FORGE.define(BUILDER);
        // big-units: game enders and their counter (design/big_units.md)
        BuildingCosts.SUN_SPIRE.define(BUILDER);
        BuildingCosts.NECROPOLIS.define(BUILDER);
        BuildingCosts.WARD_SPIRE.define(BUILDER);
        // beasts-2: the other five game enders
        BuildingCosts.DOOM_FORGE.define(BUILDER);
        BuildingCosts.WORLD_SEED.define(BUILDER);
        BuildingCosts.MAELSTROM_ENGINE.define(BUILDER);
        BuildingCosts.HEAVENS_BELL.define(BUILDER);
        BuildingCosts.EARTHCRACKER.define(BUILDER);
        BuildingCosts.VILLAGER_HOUSE.define(BUILDER);
        BuildingCosts.WHEAT_FARM.define(BUILDER);
        BuildingCosts.BARRACKS.define(BUILDER);
        BuildingCosts.BLACKSMITH.define(BUILDER);
        BuildingCosts.ARCANE_TOWER.define(BUILDER);
        BuildingCosts.CIRCLE_OF_ELDERS.define(BUILDER);
        BuildingCosts.WITCH_HUT.define(BUILDER);
        BuildingCosts.LIBRARY.define(BUILDER);
        BuildingCosts.WATCHTOWER.define(BUILDER);
        BuildingCosts.CASTLE.define(BUILDER);
        BuildingCosts.HEART_OF_THE_WILD.define(BUILDER);
        BuildingCosts.STORM_OAK.define(BUILDER);
        BuildingCosts.IRON_GOLEM_BUILDING.define(BUILDER);
        BuildingCosts.SHRINE_OF_PROSPERITY.define(BUILDER);
        BuildingCosts.VILLAGER_MARKET.define(BUILDER);
        //Piglins
        BUILDER.comment("Piglins");
        BuildingCosts.CENTRAL_PORTAL.define(BUILDER);
        BuildingCosts.BASIC_PORTAL.define(BUILDER);
        BuildingCosts.CIVILIAN_PORTAL.define(BUILDER);
        BuildingCosts.POCKET_PORTAL.define(BUILDER);
        BuildingCosts.NETHERWART_FARM.define(BUILDER);
        BuildingCosts.BASTION.define(BUILDER);
        BuildingCosts.HOGLIN_STABLES.define(BUILDER);
        BuildingCosts.FLAME_SANCTUARY.define(BUILDER);
        BuildingCosts.WITHER_SHRINE.define(BUILDER);
        BuildingCosts.BASALT_SPRINGS.define(BUILDER);
        BuildingCosts.FORTRESS.define(BUILDER);
        BuildingCosts.INFERNAL_PORTAL.define(BUILDER);
        BuildingCosts.PIGLIN_MARKET.define(BUILDER);

        //*Research*
        BUILDER.comment("Research Cost Configurations");
        ResearchCosts.RESEARCH_GOLEM_SMITHING.define(BUILDER);
        ResearchCosts.RESEARCH_SUPERIOR_BLACKSMITH.define(BUILDER);
        ResearchCosts.RESEARCH_MILITIA_BOWS.define(BUILDER);
        ResearchCosts.RESEARCH_LAB_LIGHTNING_ROD.define(BUILDER);
        ResearchCosts.RESEARCH_RESOURCE_CAPACITY.define(BUILDER);
        ResearchCosts.RESEARCH_STEWARD.define(BUILDER);
        ResearchCosts.RESEARCH_ITEM_BACKPACK.define(BUILDER);
        ResearchCosts.RESEARCH_SPIDER_JOCKEYS.define(BUILDER);
        ResearchCosts.RESEARCH_SPIDER_WEBS.define(BUILDER);
        ResearchCosts.RESEARCH_POISON_SPIDERS.define(BUILDER);
        ResearchCosts.RESEARCH_HUSKS.define(BUILDER);
        ResearchCosts.RESEARCH_DROWNED.define(BUILDER);
        ResearchCosts.RESEARCH_STRAYS.define(BUILDER);
        ResearchCosts.RESEARCH_BOGGED.define(BUILDER);
        ResearchCosts.RESEARCH_OVERFLOWING_GRAVEYARD.define(BUILDER);
        ResearchCosts.RESEARCH_MASS_BURIALS.define(BUILDER);
        ResearchCosts.RESEARCH_POSSESSION.define(BUILDER);
        ResearchCosts.RESEARCH_SLIME_CONVERSION.define(BUILDER);
        ResearchCosts.RESEARCH_LINGERING_POTIONS.define(BUILDER);
        ResearchCosts.RESEARCH_HEALING_POTIONS.define(BUILDER);
        ResearchCosts.RESEARCH_WATER_POTIONS.define(BUILDER);
        ResearchCosts.RESEARCH_EVOKER_VEXES.define(BUILDER);
        ResearchCosts.RESEARCH_UPGRADED_WINDCALLERS.define(BUILDER);
        ResearchCosts.RESEARCH_TIER_2.define(BUILDER);
        ResearchCosts.RESEARCH_TIER_3.define(BUILDER);
        ResearchCosts.UPGRADE_EXTRACTOR.define(BUILDER);
        ResearchCosts.RESEARCH_CASTLE_FLAG.define(BUILDER);
        ResearchCosts.RESEARCH_GRAND_LIBRARY.define(BUILDER);
        ResearchCosts.RESEARCH_SILVERFISH.define(BUILDER);
        ResearchCosts.RESEARCH_SCULK_AMPLIFIERS.define(BUILDER);
        ResearchCosts.RESEARCH_RAVAGER_ARTILLERY.define(BUILDER);
        ResearchCosts.RESEARCH_BRUTE_SHIELDS.define(BUILDER);
        ResearchCosts.RESEARCH_HOGLIN_CAVALRY.define(BUILDER);
        ResearchCosts.RESEARCH_HEAVY_TRIDENTS.define(BUILDER);
        ResearchCosts.RESEARCH_GREEDY_TRIDENTS.define(BUILDER);
        ResearchCosts.RESEARCH_CLEAVING_FLAILS.define(BUILDER);
        ResearchCosts.RESEARCH_BLAZE_FIRE_WALL.define(BUILDER);
        ResearchCosts.RESEARCH_FIRE_RESISTANCE.define(BUILDER);
        ResearchCosts.RESEARCH_WITHER_CLOUDS.define(BUILDER);
        ResearchCosts.RESEARCH_BLOODLUST.define(BUILDER);
        ResearchCosts.RESEARCH_ADVANCED_PORTALS.define(BUILDER);
        ResearchCosts.RESEARCH_CIVILIAN_PORTAL.define(BUILDER);
        ResearchCosts.RESEARCH_MILITARY_PORTAL.define(BUILDER);
        ResearchCosts.RESEARCH_TRANSPORT_PORTAL.define(BUILDER);
        ResearchCosts.RESEARCH_CUBE_MAGMA.define(BUILDER);
        ResearchCosts.RESEARCH_SOUL_FIREBALLS.define(BUILDER);
        ResearchCosts.RESEARCH_BEACON_LEVEL1.define(BUILDER);
        ResearchCosts.RESEARCH_BEACON_LEVEL2.define(BUILDER);
        ResearchCosts.RESEARCH_BEACON_LEVEL3.define(BUILDER);
        ResearchCosts.RESEARCH_BEACON_LEVEL4.define(BUILDER);
        ResearchCosts.RESEARCH_BEACON_LEVEL5.define(BUILDER);
        ResearchCosts.RESEARCH_UPGRADE_MARKET.define(BUILDER);
        ResearchCosts.RESEARCH_VINDICATOR_AXES.define(BUILDER);
        ResearchCosts.RESEARCH_PILLAGER_CROSSBOWS.define(BUILDER);

        //*Abilities*
        BUILDER.comment("Ability Cost Configurations");
        AbilityCosts.ENCHANT_MAIMING.define(BUILDER);
        AbilityCosts.THICKET.define(BUILDER);
        AbilityCosts.ENCHANT_QUICK_CHARGE.define(BUILDER);
        AbilityCosts.ENCHANT_SHARPNESS.define(BUILDER);
        AbilityCosts.ENCHANT_MULTISHOT.define(BUILDER);
        AbilityCosts.ENCHANT_VIGOR.define(BUILDER);
        AbilityCosts.ENCHANT_GUST.define(BUILDER);
        AbilityCosts.EQUIP_LEATHER_ARMOR.define(BUILDER);
        AbilityCosts.EQUIP_CHAINMAIL_ARMOR.define(BUILDER);

        // A world that remembers (worldmemory/, claude/world-that-remembers.md): how long battle scars last. The
        // defaults are the spec's recommendations; the server's values ride in every sync packet, so clients never
        // read their own copy of this file for them
        BUILDER.comment("World memory: persistent battle scars (scorch and craters stamped into the ground by blasts, heavy impacts, deaths and collapses)");
        BUILDER.push("world_memory");
        BUILDER.comment("Stamp scars at all (false: the ground forgets, as before)");
        WORLD_MEMORY_SCARS = BUILDER.define("scars", true);
        BUILDER.comment("Scorch half-life in seconds (default 360: a fresh burn halves in 6 minutes, reaching its floor after ~10)");
        WORLD_MEMORY_SCORCH_HALF_LIFE = BUILDER.defineInRange("scorch_half_life_s", 360, 10, 36000);
        BUILDER.comment("Scorch floor in percent of a fresh burn (default 30: the residual that stays for the whole match; 0 = fades away)");
        WORLD_MEMORY_SCORCH_FLOOR = BUILDER.defineInRange("scorch_floor_pct", 30, 0, 100);
        BUILDER.comment("Crater half-life in seconds (default 720: a crater walks down to its floor in ~21 minutes)");
        WORLD_MEMORY_CRATER_HALF_LIFE = BUILDER.defineInRange("crater_half_life_s", 720, 60, 36000);
        BUILDER.comment("Crater floor in percent of a fresh crater (default 33, the same ~30% residual as scorch)");
        WORLD_MEMORY_CRATER_FLOOR = BUILDER.defineInRange("crater_floor_pct", 33, 0, 100);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }


    public static class UnitCosts implements Costs {
        //Monsters
        public static final ResourceCostConfigEntry CREEPER = ResourceCostConfigEntry.Unit(50, 0, 100, 35, 2, ResourceCosts.CREEPER, "Creeper Config");
        public static final ResourceCostConfigEntry BAT = ResourceCostConfigEntry.Unit(50,0,0,15,1, ResourceCosts.BAT, "Bat Config");
        public static final ResourceCostConfigEntry ZOMBIE = ResourceCostConfigEntry.Unit(75, 0, 0, 18, 1, ResourceCosts.ZOMBIE, "Zombie Config");
        public static final ResourceCostConfigEntry ZOMBIE_VILLAGER = ResourceCostConfigEntry.Unit(85,0,0,22,1, ResourceCosts.ZOMBIE_VILLAGER, "Zombie Villager Config");
        public static final ResourceCostConfigEntry SKELETON = ResourceCostConfigEntry.Unit(50,45,0,18,1, ResourceCosts.SKELETON, "Skeleton Config");
        public static final ResourceCostConfigEntry STRAY = ResourceCostConfigEntry.Unit(50,45,0,18,1, ResourceCosts.STRAY, "Stray Config");
        public static final ResourceCostConfigEntry BOGGED = ResourceCostConfigEntry.Unit(50,45,0,18,1, ResourceCosts.BOGGED, "Bogged Config");
        public static final ResourceCostConfigEntry HUSK = ResourceCostConfigEntry.Unit(75,0,0,18,1, ResourceCosts.HUSK, "Husk Config");
        public static final ResourceCostConfigEntry DROWNED = ResourceCostConfigEntry.Unit(75,0,0,18,1, ResourceCosts.DROWNED, "Drowned Config");
        public static final ResourceCostConfigEntry SPIDER = ResourceCostConfigEntry.Unit(80,20,10,25,2, ResourceCosts.SPIDER, "Spider Config");
        public static final ResourceCostConfigEntry POISON_SPIDER = ResourceCostConfigEntry.Unit(80,20,10,25,2, ResourceCosts.POISON_SPIDER, "Poison Spider Config");
        public static final ResourceCostConfigEntry WRAITH = ResourceCostConfigEntry.Unit(70,0,70,30,2, ResourceCosts.WRAITH, "Wraith Config");
        public static final ResourceCostConfigEntry SLIME = ResourceCostConfigEntry.Unit(40,40,40,25,2, ResourceCosts.SLIME, "Slime Config");
        public static final ResourceCostConfigEntry WARDEN = ResourceCostConfigEntry.Unit(275,0,125,50,5, ResourceCosts.WARDEN, "Warden Config");
        public static final ResourceCostConfigEntry NECROMANCER = ResourceCostConfigEntry.Unit(0,0,0,30, 5, ResourceCosts.NECROMANCER, "Necromancer Config");
        public static final ResourceCostConfigEntry WRETCHED_WRAITH = ResourceCostConfigEntry.Unit(0,0,0,30, 5, ResourceCosts.WRETCHED_WRAITH, "Wretched Wraith Config");

        public static final ResourceCostConfigEntry ZOMBIE_PIGLIN = ResourceCostConfigEntry.Unit(75,0,0,18,1, ResourceCosts.ZOMBIE_PIGLIN, "Zombie Piglin Config");
        public static final ResourceCostConfigEntry ZOGLIN = ResourceCostConfigEntry.Unit(100,0,40,25,2, ResourceCosts.ZOGLIN, "Zoglin Config");

        // VillagersFaction
        public static final ResourceCostConfigEntry VILLAGER = ResourceCostConfigEntry.Unit(85,0,0,22,1, ResourceCosts.VILLAGER, "Villager Config");
        public static final ResourceCostConfigEntry SCOUT_DOG = ResourceCostConfigEntry.Unit(50,0,0,15,1, ResourceCosts.SCOUT_DOG, "Scout Dog Config");
        public static final ResourceCostConfigEntry SCOUT_CAT = ResourceCostConfigEntry.Unit(50,0,0,15,1, ResourceCosts.SCOUT_CAT, "Scout Cat Config");
        public static final ResourceCostConfigEntry MILITIA = ResourceCostConfigEntry.Unit(50,0,0,15,1, ResourceCosts.MILITIA, "Militia Config");
        public static final ResourceCostConfigEntry IRON_GOLEM = ResourceCostConfigEntry.Unit(0,50,250,45,4, ResourceCosts.IRON_GOLEM, "Iron Golem Config");
        public static final ResourceCostConfigEntry PILLAGER = ResourceCostConfigEntry.Unit(93,80,0,32,3, ResourceCosts.PILLAGER, "Pillager Config");
        public static final ResourceCostConfigEntry WINDCALLER = ResourceCostConfigEntry.Unit(140,60,60,35,3, ResourceCosts.WINDCALLER, "Windcaller Config");
        public static final ResourceCostConfigEntry VINDICATOR = ResourceCostConfigEntry.Unit(160,0,0,32,3, ResourceCosts.VINDICATOR, "Vindicator Config");
        public static final ResourceCostConfigEntry WITCH = ResourceCostConfigEntry.Unit(90,90,90,35,3, ResourceCosts.WITCH, "Witch Config");
        public static final ResourceCostConfigEntry EVOKER = ResourceCostConfigEntry.Unit(140,0,120,35,3, ResourceCosts.EVOKER, "Evoker Config");
        public static final ResourceCostConfigEntry RAVAGER = ResourceCostConfigEntry.Unit(400,50,150,60,7, ResourceCosts.RAVAGER, "Ravager Config");
        // Horde T3: ~4.5x the Siege Ox (T3 rule a); slow and melee-only (rule b)
        public static final ResourceCostConfigEntry SUN_COLOSSUS = ResourceCostConfigEntry.Unit(1800,200,700,150,20, ResourceCosts.SUN_COLOSSUS, "Sun Colossus Config");
        public static final ResourceCostConfigEntry WORLD_TREE_WALKER = ResourceCostConfigEntry.Unit(1800,200,700,150,20, ResourceCosts.WORLD_TREE_WALKER, "World Tree Walker Config");
        // Verdant Court (T1, balanced against the other factions' T1 - see ResourceCosts)
        public static final ResourceCostConfigEntry SEEDSHAPER = ResourceCostConfigEntry.Unit(85,0,0,22,1, ResourceCosts.SEEDSHAPER, "Seedshaper Config");
        // Tidewrought (T1, costed against the other factions' T1: worker = every worker, Gull = Scout Dog, Cutlass =
        // Leafblade, Reef Guard between the Halberdier and the Treant, Bombard a Pillager plus a little ore for its splash
        // and reach, Tide Priest = Moonwell Bearer; see tools/balance_audit.py and design/tidewrought_plan.md)
        public static final ResourceCostConfigEntry SHIPWRIGHT = ResourceCostConfigEntry.Unit(85,0,0,22,1, ResourceCosts.SHIPWRIGHT, "Shipwright Config");
        public static final ResourceCostConfigEntry GULL_SPOTTER = ResourceCostConfigEntry.Unit(50,0,0,15,1, ResourceCosts.GULL_SPOTTER, "Gull Spotter Config");
        public static final ResourceCostConfigEntry CUTLASS_RAIDER = ResourceCostConfigEntry.Unit(130,0,7,26,2, ResourceCosts.CUTLASS_RAIDER, "Cutlass Raider Config");
        public static final ResourceCostConfigEntry REEF_GUARD = ResourceCostConfigEntry.Unit(130,0,25,30,3, ResourceCosts.REEF_GUARD, "Reef Guard Config");
        public static final ResourceCostConfigEntry BOMBARD_CREW = ResourceCostConfigEntry.Unit(100,80,40,34,3, ResourceCosts.BOMBARD_CREW, "Bombard Crew Config");
        // every faction's T1 siege lobber (design/siege_t1.md): 95 metal / 130 energy, the Vindicator's price, 24 s, 2 pop
        public static final ResourceCostConfigEntry SIEGE_LOBBER = ResourceCostConfigEntry.Unit(70,50,40,24,2, ResourceCosts.SIEGE_LOBBER, "Siege Lobber Config");
        public static final ResourceCostConfigEntry TIDE_PRIEST = ResourceCostConfigEntry.Unit(70,60,40,30,2, ResourceCosts.TIDE_PRIEST, "Tide Priest Config");
        // slice 2: the Harpooner prices like a Thornbow plus a little ore for the pull (80m/150e vs 65m/150e); the Flak
        // Swivel is T1 anti-air under the Court's T2 Wisp Choir (90m/150e vs 155m/180e); the Mudskipper Sloop is a
        // cheaper, lighter Cutlass Raider (85m/90e vs 80m/100e) that needs water to shine
        public static final ResourceCostConfigEntry HARPOONER = ResourceCostConfigEntry.Unit(80,60,15,26,2, ResourceCosts.HARPOONER, "Harpooner Config");
        public static final ResourceCostConfigEntry FLAK_SWIVEL = ResourceCostConfigEntry.Unit(80,60,25,26,2, ResourceCosts.FLAK_SWIVEL, "Flak Swivel Config");
        // balance pass 2026-10 (design/balance.md): Mudskipper, Sentinel/Elder Treant, Stag Lancer, Marauder retuned
        public static final ResourceCostConfigEntry MUDSKIPPER_SLOOP = ResourceCostConfigEntry.Unit(125,0,20,22,2, ResourceCosts.MUDSKIPPER_SLOOP, "Mudskipper Sloop Config");
        // slice 3, the Drydock's T2 (priced against the other factions' T2, tools/balance_audit.py): the Harbourmaster is
        // the shared T2 constructor price; the Kraken Caller sits by the Evoker (a controller, 2 dps of its own); the
        // Mortar Galley is a heavier, longer Bombard between the Shade Ranger and the Wither Skeleton; the Sky Corsair a
        // bomber just under the Horde's Ghast (shorter reach, 3 bombs a run, has to fly over AA to use them)
        public static final ResourceCostConfigEntry HARBOURMASTER = ResourceCostConfigEntry.Unit(160,80,40,35,2, ResourceCosts.HARBOURMASTER, "Harbourmaster Config");
        public static final ResourceCostConfigEntry KRAKEN_CALLER = ResourceCostConfigEntry.Unit(120,60,110,38,3, ResourceCosts.KRAKEN_CALLER, "Kraken Caller Config");
        public static final ResourceCostConfigEntry MORTAR_GALLEY = ResourceCostConfigEntry.Unit(180,80,130,45,4, ResourceCosts.MORTAR_GALLEY, "Mortar Galley Config");
        public static final ResourceCostConfigEntry SKY_CORSAIR = ResourceCostConfigEntry.Unit(120,100,180,50,5, ResourceCosts.SKY_CORSAIR, "Sky Corsair Config");
        // Tidewrought T3: the standard experimental price (World Tree Walker, Sun Colossus), 1840 metal
        public static final ResourceCostConfigEntry LEVIATHAN = ResourceCostConfigEntry.Unit(1800,200,700,150,20, ResourceCosts.LEVIATHAN, "Leviathan Config");
        // Stormcallers (T1, design/stormcallers_plan.md slice 1; costed against the other factions' T1 - see
        // tools/balance_audit.py): worker = every worker, Paper Crane = Gull Spotter, Blade Dancer = Leafblade / Cutlass
        // Raider, Fire Adept between the Harpooner and the Pillager (3 dps + burn, but every shot costs 2 energy and a dry
        // pool halves its rate), Jade Guard = Halberdier (its Mirror answers ranged, not melee), Wind Monk = Flak Swivel
        public static final ResourceCostConfigEntry GEOMANCER = ResourceCostConfigEntry.Unit(85,0,0,22,1, ResourceCosts.GEOMANCER, "Geomancer Config");
        public static final ResourceCostConfigEntry PAPER_CRANE = ResourceCostConfigEntry.Unit(50,0,0,15,1, ResourceCosts.PAPER_CRANE, "Paper Crane Config");
        public static final ResourceCostConfigEntry BLADE_DANCER = ResourceCostConfigEntry.Unit(130,0,0,26,2, ResourceCosts.BLADE_DANCER, "Blade Dancer Config");
        public static final ResourceCostConfigEntry FIRE_ADEPT = ResourceCostConfigEntry.Unit(80,70,20,28,2, ResourceCosts.FIRE_ADEPT, "Fire Adept Config");
        public static final ResourceCostConfigEntry JADE_GUARD = ResourceCostConfigEntry.Unit(150,0,10,28,3, ResourceCosts.JADE_GUARD, "Jade Guard Config");
        public static final ResourceCostConfigEntry WIND_MONK = ResourceCostConfigEntry.Unit(80,60,25,26,2, ResourceCosts.WIND_MONK, "Wind Monk Config");
        // slice 2: Ink Scribe = Moonwell Bearer (a support that never fights; its Seal is why it is bought), Frost Adept a
        // little under the Fire Adept (2 dps and a slow instead of 3 dps and a burn), Rocket Battery = Bombard Crew's
        // metal with less energy (gunpowder, not mana: it is the one unit a stalled Stormcaller can still afford to use)
        public static final ResourceCostConfigEntry INK_SCRIBE = ResourceCostConfigEntry.Unit(60,30,40,26,2, ResourceCosts.INK_SCRIBE, "Ink Scribe Config");
        public static final ResourceCostConfigEntry FROST_ADEPT = ResourceCostConfigEntry.Unit(80,60,20,28,2, ResourceCosts.FROST_ADEPT, "Frost Adept Config");
        public static final ResourceCostConfigEntry ROCKET_BATTERY = ResourceCostConfigEntry.Unit(110,80,30,34,3, ResourceCosts.ROCKET_BATTERY, "Rocket Battery Config");
        // slice 3, the Celestial Observatory's T2 (priced against the other factions' T2, tools/balance_audit.py): the Ley
        // Master is the shared T2 constructor price; the Thunder Dragon sits by the Sky Corsair (a flyer that has to come
        // within 10 of AA to use its chain, and pays 10 energy a strike); the Earthshaker between the Iron Golem and the
        // Warden (160 HP, 4.8 dps, immovable, and a 60-energy root); the Gravity Monk and the Mirror Mage are controllers
        // bought for their spell, costed like the Kraken Caller and the Bloom Priestess
        public static final ResourceCostConfigEntry LEY_MASTER = ResourceCostConfigEntry.Unit(160,80,40,35,2, ResourceCosts.LEY_MASTER, "Ley Master Config");
        public static final ResourceCostConfigEntry THUNDER_DRAGON = ResourceCostConfigEntry.Unit(120,110,190,50,5, ResourceCosts.THUNDER_DRAGON, "Thunder Dragon Config");
        public static final ResourceCostConfigEntry EARTHSHAKER = ResourceCostConfigEntry.Unit(200,80,160,50,5, ResourceCosts.EARTHSHAKER, "Earthshaker Config");
        public static final ResourceCostConfigEntry GRAVITY_MONK = ResourceCostConfigEntry.Unit(120,70,100,38,3, ResourceCosts.GRAVITY_MONK, "Gravity Monk Config");
        public static final ResourceCostConfigEntry MIRROR_MAGE = ResourceCostConfigEntry.Unit(110,80,90,38,3, ResourceCosts.MIRROR_MAGE, "Mirror Mage Config");
        // the essence caster (design/essence_casters.md) a step above the Observatory's casters - the Gravity Monk
        // (120/70/100, 38 s) and the Mirror Mage (110/80/90, 38 s) - as its spells also deal damage; 3 population like them
        public static final ResourceCostConfigEntry SPARK_SAGE = ResourceCostConfigEntry.Unit(120,80,100,40,3, ResourceCosts.SPARK_SAGE, "Spark Sage Config");
        // slice 4: the Seer is the cheapest T2 (it only looks - the Owl Watcher's rule); the Lantern Fortress sits with the
        // Earthshaker as a T2 body bought for its dome (no attack); the Phoenix a little under the Thunder Dragon (less damage,
        // but it comes back once); the Celestial Dragon is the standard experimental price (the World Tree Walker's)
        public static final ResourceCostConfigEntry SEER = ResourceCostConfigEntry.Unit(70,50,40,30,2, ResourceCosts.SEER, "Seer Config");
        public static final ResourceCostConfigEntry LANTERN_FORTRESS = ResourceCostConfigEntry.Unit(190,90,150,50,5, ResourceCosts.LANTERN_FORTRESS, "Lantern Fortress Config");
        public static final ResourceCostConfigEntry PHOENIX = ResourceCostConfigEntry.Unit(110,100,170,48,4, ResourceCosts.PHOENIX, "Phoenix Config");
        public static final ResourceCostConfigEntry CELESTIAL_DRAGON = ResourceCostConfigEntry.Unit(1800,200,700,150,20, ResourceCosts.CELESTIAL_DRAGON, "Celestial Dragon Config");
        // Brassworks (T1, design/brassworks_plan.md slice 1; costed against the other factions' T1 - see
        // tools/balance_audit.py): worker = every worker, Gyro Scout = Paper Crane, Steam Runner between the Blade Dancer
        // and the Mudskipper, Rifle Squad = a Pillager and a bit, Iron Warden = a Jade Guard and a half (72 HP, 7 dmg,
        // immovable - but it vents), Flak Cart = Wind Monk, Mortar Crawler = Rocket Battery, Shield Projector = Moonwell
        // Bearer, Sapper = a cheap worker-sized body with one job. The Deployed Turret is free with no population: the
        // Guildmaster pays 40 metal for it in DeployTurret (FREE_OK in balance_audit.py)
        public static final ResourceCostConfigEntry TINKER = ResourceCostConfigEntry.Unit(85,0,0,22,1, ResourceCosts.TINKER, "Tinker Config");
        public static final ResourceCostConfigEntry GYRO_SCOUT = ResourceCostConfigEntry.Unit(50,0,0,15,1, ResourceCosts.GYRO_SCOUT, "Gyro Scout Config");
        public static final ResourceCostConfigEntry STEAM_RUNNER = ResourceCostConfigEntry.Unit(110,8,17,26,2, ResourceCosts.STEAM_RUNNER, "Steam Runner Config");
        public static final ResourceCostConfigEntry RIFLE_SQUAD = ResourceCostConfigEntry.Unit(100,0,15,26,2, ResourceCosts.RIFLE_SQUAD, "Rifle Squad Config");
        public static final ResourceCostConfigEntry IRON_WARDEN = ResourceCostConfigEntry.Unit(100,0,65,32,3, ResourceCosts.IRON_WARDEN, "Iron Warden Config");
        public static final ResourceCostConfigEntry FLAK_CART = ResourceCostConfigEntry.Unit(80,60,25,26,2, ResourceCosts.FLAK_CART, "Flak Cart Config");
        public static final ResourceCostConfigEntry MORTAR_CRAWLER = ResourceCostConfigEntry.Unit(110,80,30,34,3, ResourceCosts.MORTAR_CRAWLER, "Mortar Crawler Config");
        public static final ResourceCostConfigEntry SHIELD_PROJECTOR = ResourceCostConfigEntry.Unit(75,0,15,22,1, ResourceCosts.SHIELD_PROJECTOR, "Shield Projector Config");
        public static final ResourceCostConfigEntry SAPPER = ResourceCostConfigEntry.Unit(50,0,40,22,1, ResourceCosts.SAPPER, "Sapper Config");
        public static final ResourceCostConfigEntry DEPLOYED_TURRET = ResourceCostConfigEntry.Unit(0,0,0,1,0, ResourceCosts.DEPLOYED_TURRET, "Deployed Turret Config");
        public static final ResourceCostConfigEntry FOX_COURIER = ResourceCostConfigEntry.Unit(60,0,0,16,1, ResourceCosts.FOX_COURIER, "Fox Courier Config");
        // slice 2: the healer sits between a Thornbow (65m/150e) and the Kingdom's T2 Witch (170m/210e); the owl is the
        // cheapest scout in the game (25m/40e vs the Scout Dog's 30m/40e) because it cannot fight; the Hive Keeper
        // prices like the Horde's Headhunter (90m/150e vs 70m/160e)
        public static final ResourceCostConfigEntry MOONWELL_BEARER = ResourceCostConfigEntry.Unit(70,60,40,30,2, ResourceCosts.MOONWELL_BEARER, "Moonwell Bearer Config");
        public static final ResourceCostConfigEntry OWL_WATCHER = ResourceCostConfigEntry.Unit(45,0,0,12,1, ResourceCosts.OWL_WATCHER, "Owl Watcher Config");
        public static final ResourceCostConfigEntry HIVE_KEEPER = ResourceCostConfigEntry.Unit(90,50,20,28,2, ResourceCosts.HIVE_KEEPER, "Hive Keeper Config");
        public static final ResourceCostConfigEntry LEAFBLADE = ResourceCostConfigEntry.Unit(130,0,0,26,2, ResourceCosts.LEAFBLADE, "Leafblade Config");
        public static final ResourceCostConfigEntry THORNBOW = ResourceCostConfigEntry.Unit(80,60,0,24,2, ResourceCosts.THORNBOW, "Thornbow Config");
        public static final ResourceCostConfigEntry SENTINEL_TREANT = ResourceCostConfigEntry.Unit(0,60,150,45,4, ResourceCosts.SENTINEL_TREANT, "Sentinel Treant Config");
        public static final ResourceCostConfigEntry BONE_DRAGON = ResourceCostConfigEntry.Unit(1800,200,700,150,20, ResourceCosts.BONE_DRAGON, "Bone Dragon Config");
        public static final ResourceCostConfigEntry ROYAL_ARCHITECT = ResourceCostConfigEntry.Unit(160,80,40,35,2, ResourceCosts.ROYAL_ARCHITECT, "RoyalArchitect Config");
        // Verdant Court T2, priced against the other factions' T2 (the T2 constructors are identical; the Lancer sits
        // between a Windcaller and a Wither Skeleton, the Ranger by an Evoker, the Elder Treant by the Ravager)
        public static final ResourceCostConfigEntry ELDER_DRUID = ResourceCostConfigEntry.Unit(160,80,40,35,2, ResourceCosts.ELDER_DRUID, "Elder Druid Config");
        public static final ResourceCostConfigEntry STAG_LANCER = ResourceCostConfigEntry.Unit(160,60,120,35,3, ResourceCosts.STAG_LANCER, "Stag Lancer Config");
        public static final ResourceCostConfigEntry SHADE_RANGER = ResourceCostConfigEntry.Unit(120,80,120,40,3, ResourceCosts.SHADE_RANGER, "Shade Ranger Config");
        public static final ResourceCostConfigEntry ELDER_TREANT = ResourceCostConfigEntry.Unit(360,60,220,60,7, ResourceCosts.ELDER_TREANT, "Elder Treant Config");
        // the Wisp Choir sits with the Windcaller (an anti-air specialist, useless against the ground), the Bloom
        // Priestess a little above the Witch (a whole-army healer that also cleanses)
        public static final ResourceCostConfigEntry WISP_CHOIR = ResourceCostConfigEntry.Unit(110,60,70,32,3, ResourceCosts.WISP_CHOIR, "Wisp Choir Config");
        public static final ResourceCostConfigEntry BLOOM_PRIESTESS = ResourceCostConfigEntry.Unit(120,80,80,40,3, ResourceCosts.BLOOM_PRIESTESS, "Bloom Priestess Config");
        public static final ResourceCostConfigEntry EMBALMER = ResourceCostConfigEntry.Unit(160,80,40,35,2, ResourceCosts.EMBALMER, "Embalmer Config");
        public static final ResourceCostConfigEntry BONEWRIGHT = ResourceCostConfigEntry.Unit(160,80,40,35,2, ResourceCosts.BONEWRIGHT, "Bonewright Config");
        public static final ResourceCostConfigEntry WAR_MAMMOTH = ResourceCostConfigEntry.Unit(1800,200,700,150,20, ResourceCosts.WAR_MAMMOTH, "War Mammoth Config");
        public static final ResourceCostConfigEntry ROYAL_GUARD = ResourceCostConfigEntry.Unit(0,0,0,30, 5, ResourceCosts.ROYAL_GUARD, "Royal Guard Config");
        public static final ResourceCostConfigEntry ENCHANTER = ResourceCostConfigEntry.Unit(0,0,0,30, 5, ResourceCosts.ENCHANTER, "Enchanter Config");

        // Piglins
        public static final ResourceCostConfigEntry GRUNT = ResourceCostConfigEntry.Unit(85,0,0,22,1, ResourceCosts.GRUNT, "Grunt Config");
        public static final ResourceCostConfigEntry STRIDER = ResourceCostConfigEntry.Unit(50,0,0,15,1, ResourceCosts.STRIDER, "Strider Config");
        public static final ResourceCostConfigEntry BRUTE = ResourceCostConfigEntry.Unit(125,0,0,25,2, ResourceCosts.BRUTE, "Brute Config");
        public static final ResourceCostConfigEntry HEADHUNTER = ResourceCostConfigEntry.Unit(82,60,0,25,2, ResourceCosts.HEADHUNTER, "Headhunter Config");
        public static final ResourceCostConfigEntry MARAUDER = ResourceCostConfigEntry.Unit(110,75,55,40,4, ResourceCosts.MARAUDER, "Marauder Config");
        public static final ResourceCostConfigEntry HOGLIN = ResourceCostConfigEntry.Unit(140,0,70,35,3, ResourceCosts.HOGLIN, "Hoglin Config");
        public static final ResourceCostConfigEntry BLAZE = ResourceCostConfigEntry.Unit(40,40,100,30,2, ResourceCosts.BLAZE, "Blaze Config");
        public static final ResourceCostConfigEntry WITHER_SKELETON = ResourceCostConfigEntry.Unit(200,0,125,40,4, ResourceCosts.WITHER_SKELETON, "Wither Skeleton Config");
        public static final ResourceCostConfigEntry GHAST = ResourceCostConfigEntry.Unit(100,100,250,60,6, ResourceCosts.GHAST, "Ghast Config");
        public static final ResourceCostConfigEntry MAGMA_CUBE = ResourceCostConfigEntry.Unit(40,40,40,25,2, ResourceCosts.MAGMA_CUBE, "Magma Cube Config");
        public static final ResourceCostConfigEntry PIGLIN_MERCHANT = ResourceCostConfigEntry.Unit(0,0,0,30, 5, ResourceCosts.PIGLIN_MERCHANT, "Piglin Merchant Config");
        public static final ResourceCostConfigEntry WILDFIRE = ResourceCostConfigEntry.Unit(0,0,0,30, 5, ResourceCosts.WILDFIRE, "Wildfire Config");

        public static final ResourceCostConfigEntry ENDERMAN = ResourceCostConfigEntry.Unit(75,75,75,35,3, ResourceCosts.ENDERMAN, "Enderman Config");
        public static final ResourceCostConfigEntry POLAR_BEAR = ResourceCostConfigEntry.Unit(300,0,0,45,5, ResourceCosts.POLAR_BEAR, "Polar Bear Config");
        public static final ResourceCostConfigEntry GRIZZLY_BEAR = ResourceCostConfigEntry.Unit(300,0,0,45,5, ResourceCosts.GRIZZLY_BEAR, "Grizzly Bear Config");
        public static final ResourceCostConfigEntry PANDA = ResourceCostConfigEntry.Unit(300,0,0,45,5, ResourceCosts.PANDA, "Panda Config");
        public static final ResourceCostConfigEntry WOLF = ResourceCostConfigEntry.Unit(120,0,0,25,2, ResourceCosts.WOLF, "Wolf Config");
        public static final ResourceCostConfigEntry LLAMA = ResourceCostConfigEntry.Unit(180,0,0,25,2, ResourceCosts.LLAMA, "Llama Config");
        public static final ResourceCostConfigEntry BEE = ResourceCostConfigEntry.Unit(25,0,0,7,0, ResourceCosts.BEE, "Bee Config");

        public static final ResourceCostConfigEntry KILLER_RABBIT = ResourceCostConfigEntry.Unit(150,0,0,30,2, ResourceCosts.KILLER_RABBIT, "Killer Rabbit Config");
        public static final ResourceCostConfigEntry SILVERFISH = ResourceCostConfigEntry.Unit(25,0,0,0,0, ResourceCosts.SILVERFISH, "Silverfish Config");

        public static final ResourceCostConfigEntry HERO_BASE_REVIVE_COST = ResourceCostConfigEntry.Unit(100,0,0,30, 5, ResourceCosts.HERO_BASE_REVIVE_COST, "Hero Base Revive Cost Config");
        public static final ResourceCostConfigEntry HERO_EXTRA_REVIVE_COST_PER_LEVEL = ResourceCostConfigEntry.Unit(50,0,0,5, 0, ResourceCosts.HERO_EXTRA_REVIVE_COST_PER_LEVEL, "Hero Extra Revive Cost Per Level Config");
        // Brassworks slice 2, the Assembly Works' T2 (design/brassworks_plan.md; costed against the other factions' T2 -
        // tools/balance_audit.py): the Engineer-Captain is the shared T2 constructor price; the Steam Juggernaut sits with
        // the Earthshaker (a tank with boiler plating); the Tesla Walker by the Thunder Dragon (its chain is the job); the
        // Gunship by the Phoenix (a flyer); the Drill Burrower and the Clockwork Spider a step under (a raider and a
        // skirmisher); the Repair Crawler is bought for its aura, costed like the other T2 supports
        public static final ResourceCostConfigEntry ENGINEER_CAPTAIN = ResourceCostConfigEntry.Unit(160,80,40,35,2, ResourceCosts.ENGINEER_CAPTAIN, "Engineer-Captain Config");
        public static final ResourceCostConfigEntry STEAM_JUGGERNAUT = ResourceCostConfigEntry.Unit(200,80,170,50,5, ResourceCosts.STEAM_JUGGERNAUT, "Steam Juggernaut Config");
        public static final ResourceCostConfigEntry TESLA_WALKER = ResourceCostConfigEntry.Unit(120,100,150,45,4, ResourceCosts.TESLA_WALKER, "Tesla Walker Config");
        public static final ResourceCostConfigEntry GYROCOPTER_GUNSHIP = ResourceCostConfigEntry.Unit(110,100,160,45,4, ResourceCosts.GYROCOPTER_GUNSHIP, "Gyrocopter Gunship Config");
        public static final ResourceCostConfigEntry DRILL_BURROWER = ResourceCostConfigEntry.Unit(110,60,120,40,3, ResourceCosts.DRILL_BURROWER, "Drill Burrower Config");
        public static final ResourceCostConfigEntry REPAIR_CRAWLER = ResourceCostConfigEntry.Unit(90,60,80,35,2, ResourceCosts.REPAIR_CRAWLER, "Repair Crawler Config");
        public static final ResourceCostConfigEntry CLOCKWORK_SPIDER = ResourceCostConfigEntry.Unit(100,90,130,42,3, ResourceCosts.CLOCKWORK_SPIDER, "Clockwork Spider Config");
        // slice 4: the Iron Leviathan Engine is the standard experimental price (the World Tree Walker's / Celestial Dragon's)
        public static final ResourceCostConfigEntry IRON_LEVIATHAN_ENGINE = ResourceCostConfigEntry.Unit(1800,200,700,150,20, ResourceCosts.IRON_LEVIATHAN_ENGINE, "Iron Leviathan Engine Config");
        // big-units (design/big_units.md): the T2 beasts cost ~2-2.3x their faction's T2 heavy - 780M/730E elephant
        // (1.9x the Siege Ox), 540M/490E rhino (2.2x the Wither Skeleton), 550M/400E behemoth (1.9x the Warden: it grows),
        // 465M/460E bison (2x the Stag Lancer)
        public static final ResourceCostConfigEntry ORIFLAMME_ELEPHANT = ResourceCostConfigEntry.Unit(720,100,320,80,9, ResourceCosts.ORIFLAMME_ELEPHANT, "Oriflamme Elephant Config");
        public static final ResourceCostConfigEntry GOREHORN_RHINO = ResourceCostConfigEntry.Unit(500,60,220,70,7, ResourceCosts.GOREHORN_RHINO, "Gorehorn Rhino Config");
        public static final ResourceCostConfigEntry OSSUARY_BEHEMOTH = ResourceCostConfigEntry.Unit(500,0,250,70,7, ResourceCosts.OSSUARY_BEHEMOTH, "Ossuary Behemoth Config");
        public static final ResourceCostConfigEntry MOSSBACK_BISON = ResourceCostConfigEntry.Unit(420,80,190,60,6, ResourceCosts.MOSSBACK_BISON, "Mossback Bison Config");
        // beasts-2: ~2-2.1x their faction's T2 heavy - 545M/470E turtle (2.1x the Mortar Galley), 610M/520E qilin (2x
        // the Earthshaker), 630M/480E hauler (2x the Steam Juggernaut)
        public static final ResourceCostConfigEntry SHELLBACK_TURTLE = ResourceCostConfigEntry.Unit(480,60,240,70,7, ResourceCosts.SHELLBACK_TURTLE, "Shellback Siege-Turtle Config");
        public static final ResourceCostConfigEntry JADE_QILIN = ResourceCostConfigEntry.Unit(420,120,320,65,7, ResourceCosts.JADE_QILIN, "Jade Qilin Config");
        public static final ResourceCostConfigEntry STEAM_HAULER = ResourceCostConfigEntry.Unit(450,80,336,70,7, ResourceCosts.STEAM_HAULER, "Steam Hauler Config");
        // combat engineers (design/combat_engineers.md): 130-150 metal - between a worker (50) and a T2 constructor
        // (160) - and 200-230 energy (the Jade Mender pays more: energy is mana); converted by ResourceCost.bakeValues
        public static final ResourceCostConfigEntry FIELD_MASON = ResourceCostConfigEntry.Unit(150,60,37,32,2, ResourceCosts.FIELD_MASON, "Field Mason Config");
        public static final ResourceCostConfigEntry BONESETTER = ResourceCostConfigEntry.Unit(150,54,24,28,2, ResourceCosts.BONESETTER, "Bonesetter Config");
        public static final ResourceCostConfigEntry WRECKER = ResourceCostConfigEntry.Unit(150,66,40,32,2, ResourceCosts.WRECKER, "Wrecker Config");
        public static final ResourceCostConfigEntry ROOTWARDEN = ResourceCostConfigEntry.Unit(150,60,32,30,2, ResourceCosts.ROOTWARDEN, "Rootwarden Config");
        public static final ResourceCostConfigEntry SALVAGER = ResourceCostConfigEntry.Unit(150,60,32,30,2, ResourceCosts.SALVAGER, "Salvager Config");
        public static final ResourceCostConfigEntry JADE_MENDER = ResourceCostConfigEntry.Unit(150,73,23,30,2, ResourceCosts.JADE_MENDER, "Jade Mender Config");
        public static final ResourceCostConfigEntry PIONEER = ResourceCostConfigEntry.Unit(150,54,44,32,2, ResourceCosts.PIONEER, "Pioneer Config");
    }
    public static class BuildingCosts implements Costs {
        public static final ResourceCostConfigEntry BEACON = ResourceCostConfigEntry.Building(0,500,500, ResourceCosts.BEACON, "Beacon Config");

        public static final ResourceCostConfigEntry STOCKPILE = ResourceCostConfigEntry.Building(0,75,0, ResourceCosts.STOCKPILE, "Stockpile Config");
        // BAR economy: energy (wood slot) and metal (ore slot)
        public static final ResourceCostConfigEntry METAL_EXTRACTOR = ResourceCostConfigEntry.Building(0,60,50, ResourceCosts.METAL_EXTRACTOR, "Metal Extractor Config");
        public static final ResourceCostConfigEntry WIND_GENERATOR = ResourceCostConfigEntry.Building(0,20,45, ResourceCosts.WIND_GENERATOR, "Wind Generator Config");
        public static final ResourceCostConfigEntry ENERGY_CONVERTER = ResourceCostConfigEntry.Building(0,200,15, ResourceCosts.ENERGY_CONVERTER, "Energy Converter Config");
        // BAR storage is cheap: 60 metal / 120 energy for +2000 metal / +3000 energy storage
        public static final ResourceCostConfigEntry STORAGE_VAULT = ResourceCostConfigEntry.Building(0,120,60, ResourceCosts.STORAGE_VAULT, "Storage Vault Config");
        public static final ResourceCostConfigEntry OAK_BRIDGE = ResourceCostConfigEntry.Building(0,100,0, ResourceCosts.OAK_BRIDGE, "Oak Bridge Config");
        public static final ResourceCostConfigEntry SPRUCE_BRIDGE = ResourceCostConfigEntry.Building(0,100,0, ResourceCosts.SPRUCE_BRIDGE, "Spruce Bridge Config");
        public static final ResourceCostConfigEntry BLACKSTONE_BRIDGE = ResourceCostConfigEntry.Building(0,0,100, ResourceCosts.BLACKSTONE_BRIDGE, "Blackstone Bridge Config");

        // Monsters
        public static final ResourceCostConfigEntry MAUSOLEUM = ResourceCostConfigEntry.Building(0,350,250, ResourceCosts.MAUSOLEUM, "Mausoleum Config");
        public static final ResourceCostConfigEntry HAUNTED_HOUSE = ResourceCostConfigEntry.Building(0,100,0, ResourceCosts.HAUNTED_HOUSE, "Haunted House Config");
        public static final ResourceCostConfigEntry PUMPKIN_FARM = ResourceCostConfigEntry.Building(0,175,0, ResourceCosts.PUMPKIN_FARM, "Pumpkin Farm Config");
        public static final ResourceCostConfigEntry SCULK_CATALYST = ResourceCostConfigEntry.Building(0,125,0, ResourceCosts.SCULK_CATALYST, "Sculk Catalyst Config");
        public static final ResourceCostConfigEntry GRAVEYARD = ResourceCostConfigEntry.Building(0,150,0, ResourceCosts.GRAVEYARD, "Graveyard Config");
        public static final ResourceCostConfigEntry SPIDER_LAIR = ResourceCostConfigEntry.Building(0,150,75, ResourceCosts.SPIDER_LAIR, "Spider Lair Config");
        public static final ResourceCostConfigEntry DUNGEON = ResourceCostConfigEntry.Building(0,150,75, ResourceCosts.DUNGEON, "Dungeon Config");
        public static final ResourceCostConfigEntry LABORATORY = ResourceCostConfigEntry.Building(0,250,150, ResourceCosts.LABORATORY, "Laboratory Config");
        public static final ResourceCostConfigEntry DARK_WATCHTOWER = ResourceCostConfigEntry.Building(0,100,75, ResourceCosts.DARK_WATCHTOWER, "Dark Watchtower Config");
        public static final ResourceCostConfigEntry SLIME_PIT = ResourceCostConfigEntry.Building(0,175, 125, ResourceCosts.SLIME_PIT, "Slime Pit Config");
        public static final ResourceCostConfigEntry STRONGHOLD = ResourceCostConfigEntry.Building(0,400,300, ResourceCosts.STRONGHOLD, "Stronghold Config");
        public static final ResourceCostConfigEntry ALTAR_OF_DARKNESS = ResourceCostConfigEntry.Building(0,125,50, ResourceCosts.ALTAR_OF_DARKNESS, "Altar of Darkness Config");
        public static final ResourceCostConfigEntry MONSTER_MARKET = ResourceCostConfigEntry.Building(0,75,75, ResourceCosts.MONSTER_MARKET, "Monster Market Config");

        // VillagersFaction
        public static final ResourceCostConfigEntry TOWN_CENTRE = ResourceCostConfigEntry.Building(0,350,250, ResourceCosts.TOWN_CENTRE, "Town Centre Config");
        public static final ResourceCostConfigEntry HEARTWOOD_HALL = ResourceCostConfigEntry.Building(0,350,250, ResourceCosts.HEARTWOOD_HALL, "Heartwood Hall Config");
        public static final ResourceCostConfigEntry GROVE = ResourceCostConfigEntry.Building(0,150,0, ResourceCosts.GROVE, "Grove Config");
        public static final ResourceCostConfigEntry WRECK_HARBOUR = ResourceCostConfigEntry.Building(0,350,250, ResourceCosts.WRECK_HARBOUR, "Wreck Harbour Config");
        public static final ResourceCostConfigEntry SLIPWAY = ResourceCostConfigEntry.Building(0,150,0, ResourceCosts.SLIPWAY, "Slipway Config");
        // the Drydock is the T2 lab price (Arcane Tower, Circle of Elders); the Lighthouse Battery a scout-and-picket
        // tower with a third of the Storm Oak's damage (6 / 2 s at 20) but 40 sight and a cloak-breaking beam
        public static final ResourceCostConfigEntry DRYDOCK = ResourceCostConfigEntry.Building(0,150,200, ResourceCosts.DRYDOCK, "Drydock Config");
        public static final ResourceCostConfigEntry LIGHTHOUSE_BATTERY = ResourceCostConfigEntry.Building(0,160,200, ResourceCosts.LIGHTHOUSE_BATTERY, "Lighthouse Battery Config");
        public static final ResourceCostConfigEntry ABYSSAL_SHIPYARD = ResourceCostConfigEntry.Building(0,400,300, ResourceCosts.ABYSSAL_SHIPYARD, "Abyssal Shipyard Config");
        // Stormcallers: the capitol and the T1 lab at the other factions' prices
        public static final ResourceCostConfigEntry JADE_PAGODA = ResourceCostConfigEntry.Building(0,350,250, ResourceCosts.JADE_PAGODA, "Jade Pagoda Config");
        public static final ResourceCostConfigEntry LOTUS_ACADEMY = ResourceCostConfigEntry.Building(0,150,0, ResourceCosts.LOTUS_ACADEMY, "Lotus Academy Config");
        // the Celestial Observatory is the T2 lab price (Arcane Tower, Circle of Elders, Drydock)
        public static final ResourceCostConfigEntry CELESTIAL_OBSERVATORY = ResourceCostConfigEntry.Building(0,150,200, ResourceCosts.CELESTIAL_OBSERVATORY, "Celestial Observatory Config");
        // Heaven's Gate is the T3 lab price (Heart of the Wild, Abyssal Shipyard)
        public static final ResourceCostConfigEntry HEAVENS_GATE = ResourceCostConfigEntry.Building(0,400,300, ResourceCosts.HEAVENS_GATE, "Heaven's Gate Config");
        // Brassworks: the capitol and the T1 lab at the other factions' prices
        public static final ResourceCostConfigEntry FOUNDRY_HALL = ResourceCostConfigEntry.Building(0,350,250, ResourceCosts.FOUNDRY_HALL, "Foundry Hall Config");
        public static final ResourceCostConfigEntry MACHINE_SHOP = ResourceCostConfigEntry.Building(0,150,0, ResourceCosts.MACHINE_SHOP, "Machine Shop Config");
        public static final ResourceCostConfigEntry VILLAGER_HOUSE = ResourceCostConfigEntry.Building(0,90,0, ResourceCosts.VILLAGER_HOUSE, "Villager House Config");
        public static final ResourceCostConfigEntry WHEAT_FARM = ResourceCostConfigEntry.Building(0,150,0, ResourceCosts.WHEAT_FARM, "Wheat Farm Config");
        public static final ResourceCostConfigEntry BARRACKS = ResourceCostConfigEntry.Building(0,150,0, ResourceCosts.BARRACKS, "Barracks Config");
        public static final ResourceCostConfigEntry BLACKSMITH = ResourceCostConfigEntry.Building(0,100,300, ResourceCosts.BLACKSMITH, "Blacksmith Config");
        public static final ResourceCostConfigEntry WITCH_HUT = ResourceCostConfigEntry.Building(0,200,50, ResourceCosts.WITCH_HUT, "Witch Hut Config");
        public static final ResourceCostConfigEntry ARCANE_TOWER = ResourceCostConfigEntry.Building(0,100,200, ResourceCosts.ARCANE_TOWER, "Arcane Tower Config");
        public static final ResourceCostConfigEntry CIRCLE_OF_ELDERS = ResourceCostConfigEntry.Building(0,150,200, ResourceCosts.CIRCLE_OF_ELDERS, "Circle of Elders Config");
        public static final ResourceCostConfigEntry LIBRARY = ResourceCostConfigEntry.Building(0,300,100, ResourceCosts.LIBRARY, "Library Config");
        public static final ResourceCostConfigEntry WATCHTOWER = ResourceCostConfigEntry.Building(0,100,75, ResourceCosts.WATCHTOWER, "Watchtower Config");
        public static final ResourceCostConfigEntry CASTLE = ResourceCostConfigEntry.Building(0,400,300, ResourceCosts.CASTLE, "Castle Config");
        public static final ResourceCostConfigEntry HEART_OF_THE_WILD = ResourceCostConfigEntry.Building(0,400,300, ResourceCosts.HEART_OF_THE_WILD, "Heart of the Wild Config");
        // T2 static defence: a Watchtower (100/75) needs three garrisoned units to fight; the Storm Oak fights alone
        // (25 dmg / 6 s at range 22, about one archer's damage, but it never misses) - priced like a small T2 army
        public static final ResourceCostConfigEntry STORM_OAK = ResourceCostConfigEntry.Building(0,180,260, ResourceCosts.STORM_OAK, "Storm Oak Config");
        public static final ResourceCostConfigEntry IRON_GOLEM_BUILDING = ResourceCostConfigEntry.Building(0,50,250, ResourceCosts.IRON_GOLEM_BUILDING, "Iron Golem Building Config");
        public static final ResourceCostConfigEntry SHRINE_OF_PROSPERITY = ResourceCostConfigEntry.Building(0,125,50, ResourceCosts.SHRINE_OF_PROSPERITY, "Shrine of Prosperity Config");
        public static final ResourceCostConfigEntry VILLAGER_MARKET = ResourceCostConfigEntry.Building(0,75,75, ResourceCosts.VILLAGER_MARKET, "Villager Market Config");

        // Piglins
        public static final ResourceCostConfigEntry CENTRAL_PORTAL = ResourceCostConfigEntry.Building(0,350,250, ResourceCosts.CENTRAL_PORTAL, "Central Portal Config");
        public static final ResourceCostConfigEntry BASIC_PORTAL = ResourceCostConfigEntry.Building(0, 75, 0, ResourceCosts.BASIC_PORTAL, "Basic Portal Config");
        public static final ResourceCostConfigEntry CIVILIAN_PORTAL = ResourceCostConfigEntry.Building(0, 75, 0, ResourceCosts.CIVILIAN_PORTAL, "Civilian Portal Config");
        public static final ResourceCostConfigEntry POCKET_PORTAL = ResourceCostConfigEntry.Building(0, 75, 0, ResourceCosts.POCKET_PORTAL, "Pocket Portal Config");
        public static final ResourceCostConfigEntry NETHERWART_FARM = ResourceCostConfigEntry.Building(0, 150, 0, ResourceCosts.NETHERWART_FARM, "Netherwart Farm Config");
        public static final ResourceCostConfigEntry BASTION = ResourceCostConfigEntry.Building(0, 175, 150, ResourceCosts.BASTION, "Bastion Config");
        public static final ResourceCostConfigEntry HOGLIN_STABLES = ResourceCostConfigEntry.Building(0, 150, 50, ResourceCosts.HOGLIN_STABLES, "Hoglin Stables Config");
        public static final ResourceCostConfigEntry FLAME_SANCTUARY = ResourceCostConfigEntry.Building(0, 150, 200, ResourceCosts.FLAME_SANCTUARY, "Flame Sanctuary Config");
        public static final ResourceCostConfigEntry WITHER_SHRINE = ResourceCostConfigEntry.Building(0, 150, 200, ResourceCosts.WITHER_SHRINE, "Wither Shrine Config");
        public static final ResourceCostConfigEntry BASALT_SPRINGS = ResourceCostConfigEntry.Building(0, 150, 200, ResourceCosts.BASALT_SPRINGS, "Basalt Springs Config");
        public static final ResourceCostConfigEntry FORTRESS = ResourceCostConfigEntry.Building(0, 400, 300, ResourceCosts.FORTRESS, "Fortress Config");
        public static final ResourceCostConfigEntry INFERNAL_PORTAL = ResourceCostConfigEntry.Building(0,125,50, ResourceCosts.INFERNAL_PORTAL, "Infernal Portal Config");
        public static final ResourceCostConfigEntry PIGLIN_MARKET = ResourceCostConfigEntry.Building(0,75,75, ResourceCosts.PIGLIN_MARKET, "Piglin Market Config");
        // Brassworks slice 2: the Assembly Works is the T2 lab price (Arcane Tower, Circle of Elders, Celestial Observatory)
        public static final ResourceCostConfigEntry ASSEMBLY_WORKS = ResourceCostConfigEntry.Building(0,150,200, ResourceCosts.ASSEMBLY_WORKS, "Assembly Works Config");
        // slice 3: the Rail Battery is the Storm Oak's price (the other T2 defence that fights alone)
        public static final ResourceCostConfigEntry RAIL_BATTERY = ResourceCostConfigEntry.Building(0,180,260, ResourceCosts.RAIL_BATTERY, "Rail Battery Config");
        // slice 4: the Great Forge is the T3 lab price (Heaven's Gate, Abyssal Shipyard)
        public static final ResourceCostConfigEntry GREAT_FORGE = ResourceCostConfigEntry.Building(0,400,300, ResourceCosts.GREAT_FORGE, "Great Forge Config");
        // big-units: a game ender is ~3x a T3 (5500M/12000E vs 1840M); its counter, the Ward Spire, ~12% of that (650M/2500E)
        public static final ResourceCostConfigEntry SUN_SPIRE = ResourceCostConfigEntry.Building(0,8000,3100, ResourceCosts.SUN_SPIRE, "Sun Spire Config");
        public static final ResourceCostConfigEntry NECROPOLIS = ResourceCostConfigEntry.Building(0,8000,3100, ResourceCosts.NECROPOLIS, "Necropolis Config");
        public static final ResourceCostConfigEntry WARD_SPIRE = ResourceCostConfigEntry.Building(0,1667,150, ResourceCosts.WARD_SPIRE, "Ward Spire Config");
        // beasts-2: the Sun Spire's 5500M/12000E, except the two that pay per stage - the Doom Forge burns 3 x 350 metal
        // (built for 4500M), the Heaven's Bell 3 x 400 energy (built for 10800E)
        public static final ResourceCostConfigEntry DOOM_FORGE = ResourceCostConfigEntry.Building(0,8000,2100, ResourceCosts.DOOM_FORGE, "Doom Forge Config");
        public static final ResourceCostConfigEntry WORLD_SEED = ResourceCostConfigEntry.Building(0,8000,3100, ResourceCosts.WORLD_SEED, "World Seed Config");
        public static final ResourceCostConfigEntry MAELSTROM_ENGINE = ResourceCostConfigEntry.Building(0,8000,3100, ResourceCosts.MAELSTROM_ENGINE, "Maelstrom Engine Config");
        public static final ResourceCostConfigEntry HEAVENS_BELL = ResourceCostConfigEntry.Building(0,7200,3340, ResourceCosts.HEAVENS_BELL, "Heaven's Bell Config");
        public static final ResourceCostConfigEntry EARTHCRACKER = ResourceCostConfigEntry.Building(0,8000,3100, ResourceCosts.EARTHCRACKER, "Earthcracker Config");
    }
    public static class ResearchCosts implements Costs {
        public static final ResourceCostConfigEntry RESEARCH_GOLEM_SMITHING = ResourceCostConfigEntry.Research(0, 150,200, 90, ResourceCosts.RESEARCH_GOLEM_SMITHING, "Golem Smithing Research Config");
        public static final ResourceCostConfigEntry RESEARCH_SUPERIOR_BLACKSMITH = ResourceCostConfigEntry.Research(0, 100,200, 90, ResourceCosts.RESEARCH_SUPERIOR_BLACKSMITH, "Superior Blacksmith Research Config");
        public static final ResourceCostConfigEntry RESEARCH_MILITIA_BOWS = ResourceCostConfigEntry.Research(250,500,0, 160, ResourceCosts.RESEARCH_MILITIA_BOWS, "Militia Bows Research Config");
        public static final ResourceCostConfigEntry RESEARCH_LAB_LIGHTNING_ROD = ResourceCostConfigEntry.Research(0,0,400, 120, ResourceCosts.RESEARCH_LAB_LIGHTNING_ROD, "Lightning Lab Research Config");
        public static final ResourceCostConfigEntry RESEARCH_RESOURCE_CAPACITY = ResourceCostConfigEntry.Research(200,200,0, 90, ResourceCosts.RESEARCH_RESOURCE_CAPACITY, "Stockpile Resource Capacity Research Config");
        public static final ResourceCostConfigEntry RESEARCH_STEWARD = ResourceCostConfigEntry.Research(150,150,0, 60, ResourceCosts.RESEARCH_STEWARD, "Steward (capitol auto-trains workers) Research Config");
        public static final ResourceCostConfigEntry RESEARCH_ITEM_BACKPACK = ResourceCostConfigEntry.Research(0,75,75, 90, ResourceCosts.RESEARCH_ITEM_BACKPACKS, "Item Backpacks Research Config");
        public static final ResourceCostConfigEntry RESEARCH_SPIDER_JOCKEYS = ResourceCostConfigEntry.Research(300,250,0, 100, ResourceCosts.RESEARCH_SPIDER_JOCKEYS, "Spider Jockey Research Config");
        public static final ResourceCostConfigEntry RESEARCH_SPIDER_WEBS = ResourceCostConfigEntry.Research(0,300,300, 140, ResourceCosts.RESEARCH_SPIDER_WEBS, "Spider Webs Research Config");
        public static final ResourceCostConfigEntry RESEARCH_POISON_SPIDERS = ResourceCostConfigEntry.Research(400,0,250, 150, ResourceCosts.RESEARCH_POISON_SPIDERS, "Poison Spider Research Config");
        public static final ResourceCostConfigEntry RESEARCH_HUSKS = ResourceCostConfigEntry.Research(500,0,500, 200, ResourceCosts.RESEARCH_HUSKS, "Husk Research Config");
        public static final ResourceCostConfigEntry RESEARCH_DROWNED = ResourceCostConfigEntry.Research(500,0,500, 200, ResourceCosts.RESEARCH_DROWNED, "Drowned Research Config");
        public static final ResourceCostConfigEntry RESEARCH_STRAYS = ResourceCostConfigEntry.Research(500,500,0, 200, ResourceCosts.RESEARCH_STRAYS, "Stray Research Config");
        public static final ResourceCostConfigEntry RESEARCH_BOGGED = ResourceCostConfigEntry.Research(300,300,0, 140, ResourceCosts.RESEARCH_BOGGED, "Bogged Research Config");
        public static final ResourceCostConfigEntry RESEARCH_OVERFLOWING_GRAVEYARD = ResourceCostConfigEntry.Research(0,75,75, 60, ResourceCosts.RESEARCH_OVERFLOWING_GRAVEYARD, "Overflowing Graveyard Research Config");
        public static final ResourceCostConfigEntry RESEARCH_MASS_BURIALS = ResourceCostConfigEntry.Research(0,400,400, 160, ResourceCosts.RESEARCH_MASS_BURIALS, "Mass Burials Research Config");
        public static final ResourceCostConfigEntry RESEARCH_POSSESSION = ResourceCostConfigEntry.Research(400,0,400, 160, ResourceCosts.RESEARCH_POSSESSION, "Possession Research Config");
        public static final ResourceCostConfigEntry RESEARCH_SLIME_CONVERSION = ResourceCostConfigEntry.Research(300,0,300, 140, ResourceCosts.RESEARCH_SLIME_CONVERSION, "Slime Conversion Research Config");
        public static final ResourceCostConfigEntry RESEARCH_LINGERING_POTIONS = ResourceCostConfigEntry.Research(0,250,250, 140, ResourceCosts.RESEARCH_LINGERING_POTIONS, "Extended Lingering Potion Research Config");
        public static final ResourceCostConfigEntry RESEARCH_HEALING_POTIONS = ResourceCostConfigEntry.Research(0,350,350, 180, ResourceCosts.RESEARCH_HEALING_POTIONS, "Healing Potion Research Config");
        public static final ResourceCostConfigEntry RESEARCH_WATER_POTIONS = ResourceCostConfigEntry.Research(0,250,250, 140, ResourceCosts.RESEARCH_WATER_POTIONS, "Water Potion Research Config");
        public static final ResourceCostConfigEntry RESEARCH_EVOKER_VEXES = ResourceCostConfigEntry.Research(500,0,300, 120, ResourceCosts.RESEARCH_EVOKER_VEXES, "Evoker Vex Research Config");
        public static final ResourceCostConfigEntry RESEARCH_UPGRADED_WINDCALLERS = ResourceCostConfigEntry.Research(300,150,150, 140, ResourceCosts.RESEARCH_UPGRADED_WINDCALLERS, "Upgraded Windcallers Research Config");
        public static final ResourceCostConfigEntry RESEARCH_CASTLE_FLAG = ResourceCostConfigEntry.Research(200,150,150, 90, ResourceCosts.RESEARCH_CASTLE_FLAG, "Captain Banner Research Config");
        // BAR tech: ore->metal, wood->energy at bake time
        // pacing (lovish, Oct 10: "I got to tier 3 so fast"): T2 takes 2 minutes, T3 a further 4 after it
        public static final ResourceCostConfigEntry RESEARCH_TIER_2 = ResourceCostConfigEntry.Research(0,800,160, 120, ResourceCosts.RESEARCH_TIER_2, "Tier 2 Technology Research Config");
        public static final ResourceCostConfigEntry RESEARCH_TIER_3 = ResourceCostConfigEntry.Research(0,2400,600, 240, ResourceCosts.RESEARCH_TIER_3, "Tier 3 Technology Research Config");
        public static final ResourceCostConfigEntry UPGRADE_EXTRACTOR = ResourceCostConfigEntry.Research(0,200,120, 45, ResourceCosts.UPGRADE_EXTRACTOR, "Tier 2 Extractor Upgrade Config");
        public static final ResourceCostConfigEntry RESEARCH_GRAND_LIBRARY = ResourceCostConfigEntry.Research(0,200,100, 140, ResourceCosts.RESEARCH_GRAND_LIBRARY, "Grand Library Research Config");
        public static final ResourceCostConfigEntry RESEARCH_SILVERFISH = ResourceCostConfigEntry.Research(0,300,300, 120, ResourceCosts.RESEARCH_SILVERFISH, "Silverfish Research Config");
        public static final ResourceCostConfigEntry RESEARCH_SCULK_AMPLIFIERS = ResourceCostConfigEntry.Research(0,300,500, 180, ResourceCosts.RESEARCH_SCULK_AMPLIFIERS, "Sculk Amplifier Research Config");
        public static final ResourceCostConfigEntry RESEARCH_RAVAGER_ARTILLERY = ResourceCostConfigEntry.Research(400,0,350, 140, ResourceCosts.RESEARCH_RAVAGER_ARTILLERY, "Ravager Artillery Research Config");
        public static final ResourceCostConfigEntry RESEARCH_BRUTE_SHIELDS = ResourceCostConfigEntry.Research(0,300,300, 150, ResourceCosts.RESEARCH_BRUTE_SHIELDS, "Brute Shields Research Config");
        public static final ResourceCostConfigEntry RESEARCH_HOGLIN_CAVALRY = ResourceCostConfigEntry.Research(400,350,0, 180, ResourceCosts.RESEARCH_HOGLIN_CAVALRY, "Hoglin Cavalry Research Config");
        public static final ResourceCostConfigEntry RESEARCH_HEAVY_TRIDENTS = ResourceCostConfigEntry.Research(0, 300, 300, 150, ResourceCosts.RESEARCH_HEAVY_TRIDENTS, "Heavy Tridents Research Config");
        public static final ResourceCostConfigEntry RESEARCH_GREEDY_TRIDENTS = ResourceCostConfigEntry.Research(0, 350, 350, 160, ResourceCosts.RESEARCH_GREEDY_TRIDENTS, "Greedy Tridents Research Config");
        public static final ResourceCostConfigEntry RESEARCH_CLEAVING_FLAILS = ResourceCostConfigEntry.Research(0, 350, 350, 160, ResourceCosts.RESEARCH_CLEAVING_FLAILS, "Cleaving Flails Research Config");
        public static final ResourceCostConfigEntry RESEARCH_BLAZE_FIRE_WALL = ResourceCostConfigEntry.Research(400, 0, 300, 150, ResourceCosts.RESEARCH_BLAZE_FIRE_WALL, "Blaze Fire Wall Research Config");
        public static final ResourceCostConfigEntry RESEARCH_FIRE_RESISTANCE = ResourceCostConfigEntry.Research(0, 125, 125, 60, ResourceCosts.RESEARCH_FIRE_RESISTANCE, "Fire Resistance Research Config");
        public static final ResourceCostConfigEntry RESEARCH_WITHER_CLOUDS = ResourceCostConfigEntry.Research(250, 0, 350, 150, ResourceCosts.RESEARCH_WITHER_CLOUDS, "Wither Clouds Research Config");
        public static final ResourceCostConfigEntry RESEARCH_BLOODLUST = ResourceCostConfigEntry.Research(250, 250, 250, 150, ResourceCosts.RESEARCH_BLOODLUST, "Bloodlust Research Config");
        public static final ResourceCostConfigEntry RESEARCH_ADVANCED_PORTALS = ResourceCostConfigEntry.Research(0, 300, 300, 150, ResourceCosts.RESEARCH_ADVANCED_PORTALS, "Advanced Portals Research Config");
        public static final ResourceCostConfigEntry RESEARCH_CIVILIAN_PORTAL = ResourceCostConfigEntry.Research(0, 75, 0, 20, ResourceCosts.RESEARCH_CIVILIAN_PORTAL, "Civilian Portal Research Config");
        public static final ResourceCostConfigEntry RESEARCH_MILITARY_PORTAL = ResourceCostConfigEntry.Research(0, 100, 0, 25, ResourceCosts.RESEARCH_MILITARY_PORTAL, "Military Portal Research Config");
        public static final ResourceCostConfigEntry RESEARCH_TRANSPORT_PORTAL = ResourceCostConfigEntry.Research(0, 175, 0, 30, ResourceCosts.RESEARCH_TRANSPORT_PORTAL, "Transport Portal Research Config");
        public static final ResourceCostConfigEntry RESEARCH_CUBE_MAGMA = ResourceCostConfigEntry.Research(300, 0, 300, 140, ResourceCosts.RESEARCH_CUBE_MAGMA, "Cube Magma Research Config");
        public static final ResourceCostConfigEntry RESEARCH_SOUL_FIREBALLS = ResourceCostConfigEntry.Research(0, 250, 250, 120, ResourceCosts.RESEARCH_SOUL_FIREBALLS, "Soul Fireballs Research Config");
        public static final ResourceCostConfigEntry RESEARCH_UPGRADE_MARKET = ResourceCostConfigEntry.Research(0, 75, 75, 120, ResourceCosts.RESEARCH_UPGRADE_MARKET, "Market Upgrade Research Config");

        public static final ResourceCostConfigEntry RESEARCH_BEACON_LEVEL1 = ResourceCostConfigEntry.Research(0, 0, 200, 120, ResourceCosts.RESEARCH_BEACON_LEVEL1, "Beacon Level 1 Research Config");
        public static final ResourceCostConfigEntry RESEARCH_BEACON_LEVEL2 = ResourceCostConfigEntry.Research(0, 0, 300, 120, ResourceCosts.RESEARCH_BEACON_LEVEL2, "Beacon Level 2 Research Config");
        public static final ResourceCostConfigEntry RESEARCH_BEACON_LEVEL3 = ResourceCostConfigEntry.Research(0, 0, 400, 120, ResourceCosts.RESEARCH_BEACON_LEVEL3, "Beacon Level 3 Research Config");
        public static final ResourceCostConfigEntry RESEARCH_BEACON_LEVEL4 = ResourceCostConfigEntry.Research(0, 0, 500, 120, ResourceCosts.RESEARCH_BEACON_LEVEL4, "Beacon Level 4 Research Config");
        public static final ResourceCostConfigEntry RESEARCH_BEACON_LEVEL5 = ResourceCostConfigEntry.Research(0, 0, 600, 120, ResourceCosts.RESEARCH_BEACON_LEVEL5, "Beacon Level 5 Research Config");

        public static final ResourceCostConfigEntry RESEARCH_VINDICATOR_AXES = ResourceCostConfigEntry.Research(0, 200, 400, 150, ResourceCosts.RESEARCH_VINDICATOR_AXES, "Vindicator Axes Research Config");
        public static final ResourceCostConfigEntry RESEARCH_PILLAGER_CROSSBOWS = ResourceCostConfigEntry.Research(0, 600, 300, 180, ResourceCosts.RESEARCH_PILLAGER_CROSSBOWS, "Pillager Crossbows Research Config");
    }
    public static class AbilityCosts implements Costs {
        public static final ResourceCostConfigEntry ENCHANT_MAIMING = ResourceCostConfigEntry.Ability(0,20, 30, ResourceCosts.ENCHANT_MAIMING, "Maiming Enchantment Config");
        // 20m/20e for a 3x3 patch (wood = energy, ore = metal): cheap cover, bounded by ThicketBlockEntity.MAX_PLANTED
        public static final ResourceCostConfigEntry THICKET = ResourceCostConfigEntry.Ability(0,20, 20, ResourceCosts.THICKET, "Thicket Config");
        public static final ResourceCostConfigEntry ENCHANT_QUICK_CHARGE = ResourceCostConfigEntry.Ability(0,40, 20, ResourceCosts.ENCHANT_QUICK_CHARGE, "Quick Charge Enchantment Config");
        public static final ResourceCostConfigEntry ENCHANT_SHARPNESS = ResourceCostConfigEntry.Ability(0,40, 60, ResourceCosts.ENCHANT_SHARPNESS, "Sharpness Enchantment Config");
        public static final ResourceCostConfigEntry ENCHANT_MULTISHOT = ResourceCostConfigEntry.Ability(0,70, 35, ResourceCosts.ENCHANT_MULTISHOT, "Multishot Enchantment Config");
        public static final ResourceCostConfigEntry ENCHANT_VIGOR = ResourceCostConfigEntry.Ability(0,50, 50, ResourceCosts.ENCHANT_VIGOR, "Vigor Enchantment Config");
        public static final ResourceCostConfigEntry ENCHANT_GUST = ResourceCostConfigEntry.Ability(0,50, 50, ResourceCosts.ENCHANT_GUST, "Gust Enchantment Config");
        public static final ResourceCostConfigEntry EQUIP_LEATHER_ARMOR = ResourceCostConfigEntry.Ability(0,0, 30, ResourceCosts.EQUIP_LEATHER_ARMOR, "Equip Leather Armor Config");
        public static final ResourceCostConfigEntry EQUIP_CHAINMAIL_ARMOR = ResourceCostConfigEntry.Ability(0,0, 40, ResourceCosts.EQUIP_CHAINMAIL_ARMOR, "Equip Chainmail Armor Config");
    }
    public interface Costs {}
}
