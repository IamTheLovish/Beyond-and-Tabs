package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherCommonConfigs;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.util.FormattedCharSequence;

// defined here because we need to be able to access in both
// static (for ProductionItems) and nonstatic (for getCurrentPopulation) contexts
// and we can't declare static getters in the Unit interface
public class ResourceCosts {
    private static final String ID = ReignOfNether.MOD_ID;

    public static final ResourceCost ZOMBIE_VILLAGER = new ResourceCost(ID, "ZOMBIE_VILLAGER");
    public static final ResourceCost BAT = new ResourceCost(ID, "BAT");
    public static final ResourceCost CREEPER = new ResourceCost(ID, "CREEPER");
    public static final ResourceCost ZOMBIE = new ResourceCost(ID, "ZOMBIE");
    public static final ResourceCost SKELETON = new ResourceCost(ID, "SKELETON");
    public static final ResourceCost STRAY = new ResourceCost(ID, "STRAY");
    public static final ResourceCost BOGGED = new ResourceCost(ID, "BOGGED");
    public static final ResourceCost HUSK = new ResourceCost(ID, "HUSK");
    public static final ResourceCost DROWNED = new ResourceCost(ID, "DROWNED");
    public static final ResourceCost SPIDER = new ResourceCost(ID, "SPIDER");
    public static final ResourceCost POISON_SPIDER = new ResourceCost(ID, "POISON_SPIDER");
    public static final ResourceCost WRAITH = new ResourceCost(ID, "WRAITH");
    public static final ResourceCost SLIME = new ResourceCost(ID, "SLIME");
    public static final ResourceCost WARDEN = new ResourceCost(ID, "WARDEN");
    public static final ResourceCost ZOMBIE_PIGLIN = new ResourceCost(ID, "ZOMBIE_PIGLIN");
    public static final ResourceCost ZOGLIN = new ResourceCost(ID, "ZOGLIN");
    public static final ResourceCost VILLAGER = new ResourceCost(ID, "VILLAGER");
    public static final ResourceCost SCOUT_DOG = new ResourceCost(ID, "SCOUT_DOG");
    public static final ResourceCost SCOUT_CAT = new ResourceCost(ID, "SCOUT_CAT");
    public static final ResourceCost MILITIA = new ResourceCost(ID, "MILITIA");
    public static final ResourceCost IRON_GOLEM = new ResourceCost(ID, "IRON_GOLEM");
    public static final ResourceCost PILLAGER = new ResourceCost(ID, "PILLAGER");
    public static final ResourceCost WINDCALLER = new ResourceCost(ID, "WINDCALLER");
    public static final ResourceCost VINDICATOR = new ResourceCost(ID, "VINDICATOR");
    public static final ResourceCost WITCH = new ResourceCost(ID, "WITCH");
    public static final ResourceCost EVOKER = new ResourceCost(ID, "EVOKER");
    public static final ResourceCost RAVAGER = new ResourceCost(ID, "RAVAGER");
    public static final ResourceCost WAR_MAMMOTH = new ResourceCost(ID, "WAR_MAMMOTH");
    public static final ResourceCost BONEWRIGHT = new ResourceCost(ID, "BONEWRIGHT");
    public static final ResourceCost EMBALMER = new ResourceCost(ID, "EMBALMER");
    public static final ResourceCost ROYAL_ARCHITECT = new ResourceCost(ID, "ROYAL_ARCHITECT");
    // Verdant Court T2
    public static final ResourceCost ELDER_DRUID = new ResourceCost(ID, "ELDER_DRUID");
    public static final ResourceCost STAG_LANCER = new ResourceCost(ID, "STAG_LANCER");
    public static final ResourceCost SHADE_RANGER = new ResourceCost(ID, "SHADE_RANGER");
    public static final ResourceCost ELDER_TREANT = new ResourceCost(ID, "ELDER_TREANT");
    public static final ResourceCost WISP_CHOIR = new ResourceCost(ID, "WISP_CHOIR");
    public static final ResourceCost BLOOM_PRIESTESS = new ResourceCost(ID, "BLOOM_PRIESTESS");
    public static final ResourceCost CIRCLE_OF_ELDERS = new ResourceCost(ID, "CIRCLE_OF_ELDERS");
    public static final ResourceCost SUN_COLOSSUS = new ResourceCost(ID, "SUN_COLOSSUS");
    // Verdant Court T3: the Heart of the Wild and the World Tree Walker
    public static final ResourceCost WORLD_TREE_WALKER = new ResourceCost(ID, "WORLD_TREE_WALKER");
    public static final ResourceCost HEART_OF_THE_WILD = new ResourceCost(ID, "HEART_OF_THE_WILD");
    public static final ResourceCost STORM_OAK = new ResourceCost(ID, "STORM_OAK");
    // Verdant Court
    public static final ResourceCost SEEDSHAPER = new ResourceCost(ID, "SEEDSHAPER");
    // Tidewrought
    public static final ResourceCost SHIPWRIGHT = new ResourceCost(ID, "SHIPWRIGHT");
    public static final ResourceCost GULL_SPOTTER = new ResourceCost(ID, "GULL_SPOTTER");
    public static final ResourceCost CUTLASS_RAIDER = new ResourceCost(ID, "CUTLASS_RAIDER");
    public static final ResourceCost REEF_GUARD = new ResourceCost(ID, "REEF_GUARD");
    public static final ResourceCost BOMBARD_CREW = new ResourceCost(ID, "BOMBARD_CREW");
    public static final ResourceCost TIDE_PRIEST = new ResourceCost(ID, "TIDE_PRIEST");
    public static final ResourceCost HARPOONER = new ResourceCost(ID, "HARPOONER");
    public static final ResourceCost FLAK_SWIVEL = new ResourceCost(ID, "FLAK_SWIVEL");
    public static final ResourceCost MUDSKIPPER_SLOOP = new ResourceCost(ID, "MUDSKIPPER_SLOOP");
    // Tidewrought T2 (slice 3)
    public static final ResourceCost HARBOURMASTER = new ResourceCost(ID, "HARBOURMASTER");
    public static final ResourceCost KRAKEN_CALLER = new ResourceCost(ID, "KRAKEN_CALLER");
    public static final ResourceCost MORTAR_GALLEY = new ResourceCost(ID, "MORTAR_GALLEY");
    public static final ResourceCost SKY_CORSAIR = new ResourceCost(ID, "SKY_CORSAIR");
    public static final ResourceCost DRYDOCK = new ResourceCost(ID, "DRYDOCK");
    public static final ResourceCost LIGHTHOUSE_BATTERY = new ResourceCost(ID, "LIGHTHOUSE_BATTERY");
    // Tidewrought T3 (slice 4): the Abyssal Shipyard and the Leviathan
    public static final ResourceCost LEVIATHAN = new ResourceCost(ID, "LEVIATHAN");
    public static final ResourceCost ABYSSAL_SHIPYARD = new ResourceCost(ID, "ABYSSAL_SHIPYARD");
    // Stormcallers (slice 1)
    public static final ResourceCost GEOMANCER = new ResourceCost(ID, "GEOMANCER");
    public static final ResourceCost PAPER_CRANE = new ResourceCost(ID, "PAPER_CRANE");
    public static final ResourceCost BLADE_DANCER = new ResourceCost(ID, "BLADE_DANCER");
    public static final ResourceCost FIRE_ADEPT = new ResourceCost(ID, "FIRE_ADEPT");
    public static final ResourceCost JADE_GUARD = new ResourceCost(ID, "JADE_GUARD");
    public static final ResourceCost WIND_MONK = new ResourceCost(ID, "WIND_MONK");
    public static final ResourceCost INK_SCRIBE = new ResourceCost(ID, "INK_SCRIBE");
    public static final ResourceCost FROST_ADEPT = new ResourceCost(ID, "FROST_ADEPT");
    public static final ResourceCost ROCKET_BATTERY = new ResourceCost(ID, "ROCKET_BATTERY");
    public static final ResourceCost JADE_PAGODA = new ResourceCost(ID, "JADE_PAGODA");
    public static final ResourceCost LOTUS_ACADEMY = new ResourceCost(ID, "LOTUS_ACADEMY");
    // Stormcallers slice 3 (the Celestial Observatory's T2)
    public static final ResourceCost LEY_MASTER = new ResourceCost(ID, "LEY_MASTER");
    public static final ResourceCost THUNDER_DRAGON = new ResourceCost(ID, "THUNDER_DRAGON");
    public static final ResourceCost EARTHSHAKER = new ResourceCost(ID, "EARTHSHAKER");
    public static final ResourceCost GRAVITY_MONK = new ResourceCost(ID, "GRAVITY_MONK");
    public static final ResourceCost MIRROR_MAGE = new ResourceCost(ID, "MIRROR_MAGE");
    public static final ResourceCost CELESTIAL_OBSERVATORY = new ResourceCost(ID, "CELESTIAL_OBSERVATORY");
    // Stormcallers slice 4 (the rest of T2, Heaven's Gate and the Celestial Dragon)
    public static final ResourceCost SEER = new ResourceCost(ID, "SEER");
    public static final ResourceCost LANTERN_FORTRESS = new ResourceCost(ID, "LANTERN_FORTRESS");
    public static final ResourceCost PHOENIX = new ResourceCost(ID, "PHOENIX");
    public static final ResourceCost CELESTIAL_DRAGON = new ResourceCost(ID, "CELESTIAL_DRAGON");
    public static final ResourceCost HEAVENS_GATE = new ResourceCost(ID, "HEAVENS_GATE");
    // Brassworks (slice 1)
    public static final ResourceCost TINKER = new ResourceCost(ID, "TINKER");
    public static final ResourceCost GYRO_SCOUT = new ResourceCost(ID, "GYRO_SCOUT");
    public static final ResourceCost STEAM_RUNNER = new ResourceCost(ID, "STEAM_RUNNER");
    public static final ResourceCost RIFLE_SQUAD = new ResourceCost(ID, "RIFLE_SQUAD");
    public static final ResourceCost IRON_WARDEN = new ResourceCost(ID, "IRON_WARDEN");
    public static final ResourceCost FLAK_CART = new ResourceCost(ID, "FLAK_CART");
    public static final ResourceCost MORTAR_CRAWLER = new ResourceCost(ID, "MORTAR_CRAWLER");
    public static final ResourceCost SHIELD_PROJECTOR = new ResourceCost(ID, "SHIELD_PROJECTOR");
    public static final ResourceCost SAPPER = new ResourceCost(ID, "SAPPER");
    public static final ResourceCost DEPLOYED_TURRET = new ResourceCost(ID, "DEPLOYED_TURRET");
    public static final ResourceCost FOUNDRY_HALL = new ResourceCost(ID, "FOUNDRY_HALL");
    public static final ResourceCost MACHINE_SHOP = new ResourceCost(ID, "MACHINE_SHOP");
    public static final ResourceCost FOX_COURIER = new ResourceCost(ID, "FOX_COURIER");
    // Verdant Court T1, slice 2 (design/verdant_court_plan.md)
    public static final ResourceCost MOONWELL_BEARER = new ResourceCost(ID, "MOONWELL_BEARER");
    public static final ResourceCost OWL_WATCHER = new ResourceCost(ID, "OWL_WATCHER");
    public static final ResourceCost HIVE_KEEPER = new ResourceCost(ID, "HIVE_KEEPER");
    public static final ResourceCost LEAFBLADE = new ResourceCost(ID, "LEAFBLADE");
    public static final ResourceCost THORNBOW = new ResourceCost(ID, "THORNBOW");
    public static final ResourceCost SENTINEL_TREANT = new ResourceCost(ID, "SENTINEL_TREANT");
    public static final ResourceCost BONE_DRAGON = new ResourceCost(ID, "BONE_DRAGON");
    public static final ResourceCost GRUNT = new ResourceCost(ID, "GRUNT");
    public static final ResourceCost STRIDER = new ResourceCost(ID, "STRIDER");
    public static final ResourceCost BRUTE = new ResourceCost(ID, "BRUTE");
    public static final ResourceCost HEADHUNTER = new ResourceCost(ID, "HEADHUNTER");
    public static final ResourceCost MARAUDER = new ResourceCost(ID, "MARAUDER");
    public static final ResourceCost HOGLIN = new ResourceCost(ID, "HOGLIN");
    public static final ResourceCost BLAZE = new ResourceCost(ID, "BLAZE");
    public static final ResourceCost WITHER_SKELETON = new ResourceCost(ID, "WITHER_SKELETON");
    public static final ResourceCost GHAST = new ResourceCost(ID, "GHAST");
    public static final ResourceCost MAGMA_CUBE = new ResourceCost(ID, "MAGMA_CUBE");

    public static final ResourceCost NECROMANCER = new ResourceCost(ID, "NECROMANCER");
    public static final ResourceCost ROYAL_GUARD = new ResourceCost(ID, "ROYAL_GUARD");
    public static final ResourceCost PIGLIN_MERCHANT = new ResourceCost(ID, "PIGLIN_MERCHANT");
    public static final ResourceCost WRETCHED_WRAITH = new ResourceCost(ID, "WRETCHED_WRAITH");
    public static final ResourceCost ENCHANTER = new ResourceCost(ID, "ENCHANTER");
    public static final ResourceCost WILDFIRE = new ResourceCost(ID, "WILDFIRE");
    public static final ResourceCost HERO_BASE_REVIVE_COST = new ResourceCost(ID, "HERO_BASE_REVIVE_COST");
    public static final ResourceCost HERO_EXTRA_REVIVE_COST_PER_LEVEL = new ResourceCost(ID, "HERO_REVIVE_COST_PER_LEVEL");

    public static final ResourceCost ENDERMAN = new ResourceCost(ID, "ENDERMAN");
    public static final ResourceCost POLAR_BEAR = new ResourceCost(ID, "POLAR_BEAR");
    public static final ResourceCost GRIZZLY_BEAR = new ResourceCost(ID, "GRIZZLY_BEAR");
    public static final ResourceCost PANDA = new ResourceCost(ID, "PANDA");
    public static final ResourceCost WOLF = new ResourceCost(ID, "WOLF");
    public static final ResourceCost LLAMA = new ResourceCost(ID, "LLAMA");
    public static final ResourceCost BEE = new ResourceCost(ID, "BEE");
    public static final ResourceCost KILLER_RABBIT = new ResourceCost(ID, "KILLER_RABBIT");
    public static final ResourceCost SILVERFISH = new ResourceCost(ID, "SILVERFISH");

    //BUILDINGS
    public static final ResourceCost BEACON = new ResourceCost(ID, "BEACON");

    public static final ResourceCost STOCKPILE = new ResourceCost(ID, "STOCKPILE");
    public static final ResourceCost METAL_EXTRACTOR = new ResourceCost(ID, "METAL_EXTRACTOR");
    public static final ResourceCost WIND_GENERATOR = new ResourceCost(ID, "WIND_GENERATOR");
    public static final ResourceCost ENERGY_CONVERTER = new ResourceCost(ID, "ENERGY_CONVERTER");
    public static final ResourceCost STORAGE_VAULT = new ResourceCost(ID, "STORAGE_VAULT");
    public static final ResourceCost OAK_BRIDGE = new ResourceCost(ID, "OAK_BRIDGE");
    public static final ResourceCost SPRUCE_BRIDGE = new ResourceCost(ID, "SPRUCE_BRIDGE");
    public static final ResourceCost BLACKSTONE_BRIDGE = new ResourceCost(ID, "BLACKSTONE_BRIDGE");
    //Monster
    public static final ResourceCost MAUSOLEUM = new ResourceCost(ID, "MAUSOLEUM");
    public static final ResourceCost HAUNTED_HOUSE = new ResourceCost(ID, "HAUNTED_HOUSE");
    public static final ResourceCost PUMPKIN_FARM = new ResourceCost(ID, "PUMPKIN_FARM");
    public static final ResourceCost SCULK_CATALYST = new ResourceCost(ID, "SKULK_CATALYST");
    public static final ResourceCost GRAVEYARD = new ResourceCost(ID, "GRAVEYARD");
    public static final ResourceCost SPIDER_LAIR = new ResourceCost(ID, "SPIDER_LAIR");
    public static final ResourceCost DUNGEON = new ResourceCost(ID, "DUNGEON");
    public static final ResourceCost LABORATORY = new ResourceCost(ID, "LABORATORY");
    public static final ResourceCost DARK_WATCHTOWER = new ResourceCost(ID, "DARK_WATCHTOWER");
    public static final ResourceCost SLIME_PIT = new ResourceCost(ID, "SLIME_PIT");
    public static final ResourceCost STRONGHOLD = new ResourceCost(ID, "STRONGHOLD");
    public static final ResourceCost ALTAR_OF_DARKNESS = new ResourceCost(ID, "ALTAR_OF_DARKNESS");
    public static final ResourceCost MONSTER_MARKET = new ResourceCost(ID, "MONSTER_MARKET");
    //VillagersFaction
    //Verdant Court
    public static final ResourceCost HEARTWOOD_HALL = new ResourceCost(ID, "HEARTWOOD_HALL");
    public static final ResourceCost GROVE = new ResourceCost(ID, "GROVE");
    // Tidewrought
    public static final ResourceCost WRECK_HARBOUR = new ResourceCost(ID, "WRECK_HARBOUR");
    public static final ResourceCost SLIPWAY = new ResourceCost(ID, "SLIPWAY");
    public static final ResourceCost TOWN_CENTRE = new ResourceCost(ID, "TOWN_CENTRE");
    public static final ResourceCost VILLAGER_HOUSE = new ResourceCost(ID, "VILLAGER_HOUSE");
    public static final ResourceCost WHEAT_FARM = new ResourceCost(ID, "WHEAT_FARM");
    public static final ResourceCost BARRACKS = new ResourceCost(ID, "BARRACKS");
    public static final ResourceCost BLACKSMITH = new ResourceCost(ID, "BLACKSMITH");
    public static final ResourceCost WITCH_HUT = new ResourceCost(ID, "WITCH_HUT");
    public static final ResourceCost ARCANE_TOWER = new ResourceCost(ID, "ARCANE_TOWER");
    public static final ResourceCost LIBRARY = new ResourceCost(ID, "LIBRARY");
    public static final ResourceCost WATCHTOWER = new ResourceCost(ID, "WATCHTOWER");
    public static final ResourceCost CASTLE = new ResourceCost(ID, "CASTLE");
    public static final ResourceCost IRON_GOLEM_BUILDING = new ResourceCost(ID, "IRON_GOLEM_BUILDING");
    public static final ResourceCost SHRINE_OF_PROSPERITY = new ResourceCost(ID, "SHRINE_OF_PROSPERITY");
    public static final ResourceCost VILLAGER_MARKET = new ResourceCost(ID, "VILLAGER_MARKET");
    //Piglins
    public static final ResourceCost CENTRAL_PORTAL = new ResourceCost(ID, "CENTRAL_PORTAL");
    public static final ResourceCost BASIC_PORTAL = new ResourceCost(ID, "BASIC_PORTAL");
    public static final ResourceCost CIVILIAN_PORTAL = new ResourceCost(ID, "CIVILIAN_PORTAL");
    public static final ResourceCost POCKET_PORTAL = new ResourceCost(ID, "POCKET_PORTAL");
    public static final ResourceCost NETHERWART_FARM = new ResourceCost(ID, "NETHERWART_FARM");
    public static final ResourceCost BASTION = new ResourceCost(ID, "BASTION");
    public static final ResourceCost HOGLIN_STABLES = new ResourceCost(ID, "HOGLIN_STABLES");
    public static final ResourceCost FLAME_SANCTUARY = new ResourceCost(ID, "FLAME_SANCTUARY");
    public static final ResourceCost BASALT_SPRINGS = new ResourceCost(ID, "BASALT_SPRINGS");
    public static final ResourceCost WITHER_SHRINE = new ResourceCost(ID, "WITHER_SHRINE");
    public static final ResourceCost FORTRESS = new ResourceCost(ID, "FORTRESS");
    public static final ResourceCost INFERNAL_PORTAL = new ResourceCost(ID, "INFERNAL_PORTAL");
    public static final ResourceCost PIGLIN_MARKET = new ResourceCost(ID, "PIGLIN_MARKET");

    // RESEARCH

    public static final ResourceCost RESEARCH_SUPERIOR_BLACKSMITH = new ResourceCost(ID, "RESEARCH_SUPERIOR_BLACKSMITH");
    public static final ResourceCost RESEARCH_GOLEM_SMITHING = new ResourceCost(ID, "RESEARCH_GOLEM_SMITHING");
    public static final ResourceCost RESEARCH_MILITIA_BOWS = new ResourceCost(ID, "RESEARCH_MILITIA_BOWS");
    public static final ResourceCost RESEARCH_LAB_LIGHTNING_ROD = new ResourceCost(ID, "RESEARCH_LAB_LIGHTNING_ROD");
    public static final ResourceCost RESEARCH_RESOURCE_CAPACITY = new ResourceCost(ID, "RESEARCH_RESOURCE_CAPACITY");
    public static final ResourceCost RESEARCH_STEWARD = new ResourceCost(ID, "RESEARCH_STEWARD");
    public static final ResourceCost RESEARCH_ITEM_BACKPACKS = new ResourceCost(ID, "RESEARCH_ITEM_BACKPACK");
    public static final ResourceCost RESEARCH_SPIDER_JOCKEYS = new ResourceCost(ID, "RESEARCH_SPIDER_JOCKEYS");
    public static final ResourceCost RESEARCH_SPIDER_WEBS = new ResourceCost(ID, "RESEARCH_SPIDER_WEBS");
    public static final ResourceCost RESEARCH_POISON_SPIDERS = new ResourceCost(ID, "RESEARCH_POISON_SPIDERS");
    public static final ResourceCost RESEARCH_HUSKS = new ResourceCost(ID, "RESEARCH_HUSKS");
    public static final ResourceCost RESEARCH_DROWNED = new ResourceCost(ID, "RESEARCH_DROWNED");
    public static final ResourceCost RESEARCH_STRAYS = new ResourceCost(ID, "RESEARCH_STRAYS");
    public static final ResourceCost RESEARCH_BOGGED = new ResourceCost(ID, "RESEARCH_BOGGED");
    public static final ResourceCost RESEARCH_OVERFLOWING_GRAVEYARD = new ResourceCost(ID, "RESEARCH_OVERFLOWING_GRAVEYARD");
    public static final ResourceCost RESEARCH_MASS_BURIALS = new ResourceCost(ID, "RESEARCH_MASS_BURIALS");
    public static final ResourceCost RESEARCH_POSSESSION = new ResourceCost(ID, "RESEARCH_POSSESSION");
    public static final ResourceCost RESEARCH_SLIME_CONVERSION = new ResourceCost(ID, "RESEARCH_SLIME_CONVERSION");
    public static final ResourceCost RESEARCH_LINGERING_POTIONS = new ResourceCost(ID, "RESEARCH_LINGERING_POTIONS");
    public static final ResourceCost RESEARCH_HEALING_POTIONS = new ResourceCost(ID, "RESEARCH_HEALING_POTIONS");
    public static final ResourceCost RESEARCH_WATER_POTIONS = new ResourceCost(ID, "RESEARCH_WATER_POTIONS");
    public static final ResourceCost RESEARCH_EVOKER_VEXES = new ResourceCost(ID, "RESEARCH_EVOKER_VEXES");
    public static final ResourceCost RESEARCH_UPGRADED_WINDCALLERS = new ResourceCost(ID, "RESEARCH_UPGRADED_WINDCALLERS");
    public static final ResourceCost RESEARCH_CASTLE_FLAG = new ResourceCost(ID, "RESEARCH_CASTLE_FLAG");
    public static final ResourceCost RESEARCH_TIER_2 = new ResourceCost(ID, "RESEARCH_TIER_2");
    public static final ResourceCost RESEARCH_TIER_3 = new ResourceCost(ID, "RESEARCH_TIER_3");
    public static final ResourceCost UPGRADE_EXTRACTOR = new ResourceCost(ID, "UPGRADE_EXTRACTOR");
    public static final ResourceCost RESEARCH_GRAND_LIBRARY = new ResourceCost(ID, "RESEARCH_GRAND_LIBRARY");
    public static final ResourceCost RESEARCH_SILVERFISH = new ResourceCost(ID, "RESEARCH_SILVERFISH");
    public static final ResourceCost RESEARCH_SCULK_AMPLIFIERS = new ResourceCost(ID, "RESEARCH_SCULK_AMPLIFIERS");
    public static final ResourceCost RESEARCH_RAVAGER_ARTILLERY = new ResourceCost(ID, "RESEARCH_RAVAGER_ARTILLERY");
    public static final ResourceCost RESEARCH_BRUTE_SHIELDS = new ResourceCost(ID, "RESEARCH_BRUTE_SHIELDS");
    public static final ResourceCost RESEARCH_HOGLIN_CAVALRY = new ResourceCost(ID, "RESEARCH_HOGLIN_CAVALRY");
    public static final ResourceCost RESEARCH_HEAVY_TRIDENTS = new ResourceCost(ID, "RESEARCH_HEAVY_TRIDENTS");
    public static final ResourceCost RESEARCH_GREEDY_TRIDENTS = new ResourceCost(ID, "RESEARCH_GREEDY_TRIDENTS");
    public static final ResourceCost RESEARCH_CLEAVING_FLAILS = new ResourceCost(ID, "RESEARCH_CLEAVING_FLAILS");
    public static final ResourceCost RESEARCH_BLAZE_FIRE_WALL = new ResourceCost(ID, "RESEARCH_BLAZE_FIRE_WALL");
    public static final ResourceCost RESEARCH_FIRE_RESISTANCE = new ResourceCost(ID, "RESEARCH_FIRE_RESISTANCE");
    public static final ResourceCost RESEARCH_WITHER_CLOUDS = new ResourceCost(ID, "RESEARCH_WITHER_CLOUDS");
    public static final ResourceCost RESEARCH_BLOODLUST = new ResourceCost(ID, "RESEARCH_BLOODLUST");
    public static final ResourceCost RESEARCH_ADVANCED_PORTALS = new ResourceCost(ID, "RESEARCH_ADVANCED_PORTALS");
    public static final ResourceCost RESEARCH_CIVILIAN_PORTAL = new ResourceCost(ID, "RESEARCH_CIVILIAN_PORTAL");
    public static final ResourceCost RESEARCH_MILITARY_PORTAL = new ResourceCost(ID, "RESEARCH_MILITARY_PORTAL");
    public static final ResourceCost RESEARCH_TRANSPORT_PORTAL = new ResourceCost(ID, "RESEARCH_TRANSPORT_PORTAL");
    public static final ResourceCost RESEARCH_CUBE_MAGMA = new ResourceCost(ID, "RESEARCH_CUBE_MAGMA");
    public static final ResourceCost RESEARCH_SOUL_FIREBALLS = new ResourceCost(ID, "RESEARCH_SOUL_FIREBALLS");
    public static final ResourceCost RESEARCH_BEACON_LEVEL1 = new ResourceCost(ID, "RESEARCH_BEACON_LEVEL1");
    public static final ResourceCost RESEARCH_BEACON_LEVEL2 = new ResourceCost(ID, "RESEARCH_BEACON_LEVEL2");
    public static final ResourceCost RESEARCH_BEACON_LEVEL3 = new ResourceCost(ID, "RESEARCH_BEACON_LEVEL3");
    public static final ResourceCost RESEARCH_BEACON_LEVEL4 = new ResourceCost(ID, "RESEARCH_BEACON_LEVEL4");
    public static final ResourceCost RESEARCH_BEACON_LEVEL5 = new ResourceCost(ID, "RESEARCH_BEACON_LEVEL5");
    public static final ResourceCost RESEARCH_UPGRADE_MARKET = new ResourceCost(ID, "RESEARCH_UPGRADE_MARKET");

    // ABILITIES

    public static final ResourceCost ENCHANT_MAIMING = new ResourceCost(ID, "ENCHANT_MAIMING");
    public static final ResourceCost VINE_SNARE = new ResourceCost(ID, "VINE_SNARE");   // Verdant Seedshaper trap
    public static final ResourceCost THICKET = new ResourceCost(ID, "THICKET");         // Verdant Seedshaper 3x3 thicket
    public static final ResourceCost ENCHANT_QUICK_CHARGE = new ResourceCost(ID, "ENCHANT_QUICK_CHARGE");
    public static final ResourceCost ENCHANT_SHARPNESS = new ResourceCost(ID, "ENCHANT_SHARPNESS");
    public static final ResourceCost ENCHANT_MULTISHOT = new ResourceCost(ID, "ENCHANT_MULTISHOT");
    public static final ResourceCost ENCHANT_VIGOR = new ResourceCost(ID, "ENCHANT_VIGOR");
    public static final ResourceCost ENCHANT_GUST = new ResourceCost(ID, "ENCHANT_GUST");
    public static final ResourceCost EQUIP_LEATHER_ARMOR = new ResourceCost(ID, "EQUIP_LEATHER_ARMOR");
    public static final ResourceCost EQUIP_CHAINMAIL_ARMOR = new ResourceCost(ID, "EQUIP_CHAINMAIL_ARMOR");

    // Research (previously unused, now configurable)
    public static final ResourceCost RESEARCH_VINDICATOR_AXES = new ResourceCost(ID, "RESEARCH_VINDICATOR_AXES");
    public static final ResourceCost RESEARCH_PILLAGER_CROSSBOWS = new ResourceCost(ID, "RESEARCH_PILLAGER_CROSSBOWS");
    // Brassworks (slice 2): the Assembly Works and its T2
    public static final ResourceCost ENGINEER_CAPTAIN = new ResourceCost(ID, "ENGINEER_CAPTAIN");
    public static final ResourceCost STEAM_JUGGERNAUT = new ResourceCost(ID, "STEAM_JUGGERNAUT");
    public static final ResourceCost TESLA_WALKER = new ResourceCost(ID, "TESLA_WALKER");
    public static final ResourceCost GYROCOPTER_GUNSHIP = new ResourceCost(ID, "GYROCOPTER_GUNSHIP");
    public static final ResourceCost DRILL_BURROWER = new ResourceCost(ID, "DRILL_BURROWER");
    public static final ResourceCost REPAIR_CRAWLER = new ResourceCost(ID, "REPAIR_CRAWLER");
    public static final ResourceCost CLOCKWORK_SPIDER = new ResourceCost(ID, "CLOCKWORK_SPIDER");
    public static final ResourceCost ASSEMBLY_WORKS = new ResourceCost(ID, "ASSEMBLY_WORKS");
    public static final ResourceCost RAIL_BATTERY = new ResourceCost(ID, "RAIL_BATTERY");
    public static final ResourceCost IRON_LEVIATHAN_ENGINE = new ResourceCost(ID, "IRON_LEVIATHAN_ENGINE");
    // the second T3 per faction (second-t3-design.md, slice T3b-1): same price as the first, own config entry
    public static final ResourceCost SUN_T3B = new ResourceCost(ID, "SUN_T3B");
    public static final ResourceCost GRAVE_T3B = new ResourceCost(ID, "GRAVE_T3B");
    public static final ResourceCost HORDE_T3B = new ResourceCost(ID, "HORDE_T3B");
    public static final ResourceCost VERDANT_T3B = new ResourceCost(ID, "VERDANT_T3B");
    public static final ResourceCost TIDE_T3B = new ResourceCost(ID, "TIDE_T3B");
    public static final ResourceCost STORM_T3B = new ResourceCost(ID, "STORM_T3B");
    public static final ResourceCost BRASS_T3B = new ResourceCost(ID, "BRASS_T3B");
    public static final ResourceCost GREAT_FORGE = new ResourceCost(ID, "GREAT_FORGE");
    // big-units (design/big_units.md)
    public static final ResourceCost ORIFLAMME_ELEPHANT = new ResourceCost(ID, "ORIFLAMME_ELEPHANT");
    public static final ResourceCost GOREHORN_RHINO = new ResourceCost(ID, "GOREHORN_RHINO");
    public static final ResourceCost OSSUARY_BEHEMOTH = new ResourceCost(ID, "OSSUARY_BEHEMOTH");
    public static final ResourceCost MOSSBACK_BISON = new ResourceCost(ID, "MOSSBACK_BISON");
    public static final ResourceCost SUN_SPIRE = new ResourceCost(ID, "SUN_SPIRE");
    public static final ResourceCost NECROPOLIS = new ResourceCost(ID, "NECROPOLIS");
    public static final ResourceCost WARD_SPIRE = new ResourceCost(ID, "WARD_SPIRE");
    // beasts-2 (design/big_units.md): the other three T2 beasts and five game enders
    public static final ResourceCost SHELLBACK_TURTLE = new ResourceCost(ID, "SHELLBACK_TURTLE");
    public static final ResourceCost JADE_QILIN = new ResourceCost(ID, "JADE_QILIN");
    public static final ResourceCost STEAM_HAULER = new ResourceCost(ID, "STEAM_HAULER");
    // combat engineers (design/combat_engineers.md)
    public static final ResourceCost FIELD_MASON = new ResourceCost(ID, "FIELD_MASON");
    public static final ResourceCost BONESETTER = new ResourceCost(ID, "BONESETTER");
    public static final ResourceCost WRECKER = new ResourceCost(ID, "WRECKER");
    public static final ResourceCost ROOTWARDEN = new ResourceCost(ID, "ROOTWARDEN");
    public static final ResourceCost SALVAGER = new ResourceCost(ID, "SALVAGER");
    public static final ResourceCost JADE_MENDER = new ResourceCost(ID, "JADE_MENDER");
    public static final ResourceCost PIONEER = new ResourceCost(ID, "PIONEER");
    public static final ResourceCost DOOM_FORGE = new ResourceCost(ID, "DOOM_FORGE");
    public static final ResourceCost WORLD_SEED = new ResourceCost(ID, "WORLD_SEED");
    public static final ResourceCost MAELSTROM_ENGINE = new ResourceCost(ID, "MAELSTROM_ENGINE");
    public static final ResourceCost HEAVENS_BELL = new ResourceCost(ID, "HEAVENS_BELL");
    public static final ResourceCost EARTHCRACKER = new ResourceCost(ID, "EARTHCRACKER");

    public static FormattedCharSequence getFormattedCost(ResourceCost resCost) {
        // BAR economy: costs are Metal (ore field, iron ingot icon \uE020) and Energy (wood field, redstone icon \uE021)
        // legacy food costs are paid as metal + energy (ResourceCost.metal()/energy()), so show those totals
        String str = "";
        int metal = Math.round(resCost.metal());
        int energy = Math.round(resCost.energy());
        if (metal > 0)
            str += "\uE020  " + metal + "     ";
        if (energy > 0)
            str += "\uE021  " + energy + "     ";
        if (resCost.emerald > 0)
            str += "\uE010  " + resCost.emerald + "     ";

        if (str.isEmpty())
            str += "\uE020  0     ";
        str = str.trim();
        return FormattedCharSequence.forward(str, MyRenderer.iconStyle);
    }
    public static FormattedCharSequence getFormattedPopAndTime(ResourceCost resCost) {
        return FormattedCharSequence.forward("\uE003  " + resCost.population + "     \uE004  " + resCost.ticks/ResourceCost.TICKS_PER_SECOND + "s", MyRenderer.iconStyle);
    }
    public static FormattedCharSequence getFormattedPop(ResourceCost resCost) {
        return FormattedCharSequence.forward("\uE003  " + resCost.population, MyRenderer.iconStyle);
    }
    public static FormattedCharSequence getFormattedTime(ResourceCost resCost) {
        return FormattedCharSequence.forward("\uE004  " + resCost.ticks/ResourceCost.TICKS_PER_SECOND + "s", MyRenderer.iconStyle);
    }

    public static final int REPLANT_WOOD_COST = 1;
    public static final int REDUCED_REPLANT_WOOD_COST = 0;
    // BAR-scale battles (150-200+ units per player): was 150. Note the maxPopulation gamerule only takes this
    // default for NEW worlds; existing saves keep their stored value (/gamerule maxPopulation 400).
    // lovish, Oct 13 2026 ("there shouldnt be a max population", after hitting 400 at 26 minutes in his playtest):
    // no cap by default, as in BAR. A value at or above UNLIMITED_POPULATION means "no cap" everywhere (the HUD
    // shows just the count, the gamerule button reads "unlimited"); the gamerule still lets a host set one.
    // Performance is guarded elsewhere (pathfinder budgets, Flywheel rendering to come), not by a cap.
    public static final int UNLIMITED_POPULATION = 100_000;
    public static final int DEFAULT_MAX_POPULATION = UNLIMITED_POPULATION;
    /*
        Unit costs are defined here during CommonSetup
        Do not read values and initialize from config earlier, else will result in IllegalStateException !!!
     */
    public static void deferredLoadResourceCosts() {

        // ******************* UNITS ******************* //
        // Monsters
        CREEPER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.CREEPER);
        ZOMBIE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ZOMBIE);
        ZOMBIE_VILLAGER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ZOMBIE_VILLAGER);
        BAT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BAT);
        SKELETON.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SKELETON);
        STRAY.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.STRAY);
        BOGGED.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BOGGED);
        HUSK.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HUSK);
        DROWNED.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.DROWNED);
        SPIDER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SPIDER);
        POISON_SPIDER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.POISON_SPIDER);
        WRAITH.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WRAITH);
        SLIME.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SLIME);
        WARDEN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WARDEN);
        NECROMANCER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.NECROMANCER);
        WRETCHED_WRAITH.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WRETCHED_WRAITH);
        ZOMBIE_PIGLIN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ZOMBIE_PIGLIN);
        ZOGLIN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ZOGLIN);
        // VillagersFaction
        VILLAGER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.VILLAGER);
        SCOUT_DOG.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SCOUT_DOG);
        SCOUT_CAT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SCOUT_CAT);
        MILITIA.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MILITIA);
        IRON_GOLEM.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.IRON_GOLEM);
        PILLAGER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.PILLAGER);
        WINDCALLER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WINDCALLER);
        VINDICATOR.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.VINDICATOR);
        WITCH.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WITCH);
        EVOKER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.EVOKER);
        RAVAGER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.RAVAGER);
        WAR_MAMMOTH.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WAR_MAMMOTH);
        BONEWRIGHT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BONEWRIGHT);
        EMBALMER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.EMBALMER);
        ROYAL_ARCHITECT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ROYAL_ARCHITECT);
        ELDER_DRUID.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ELDER_DRUID);
        STAG_LANCER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.STAG_LANCER);
        SHADE_RANGER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SHADE_RANGER);
        ELDER_TREANT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ELDER_TREANT);
        WISP_CHOIR.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WISP_CHOIR);
        BLOOM_PRIESTESS.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BLOOM_PRIESTESS);
        CIRCLE_OF_ELDERS.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.CIRCLE_OF_ELDERS);
        SUN_COLOSSUS.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SUN_COLOSSUS);
        WORLD_TREE_WALKER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WORLD_TREE_WALKER);
        HEART_OF_THE_WILD.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.HEART_OF_THE_WILD);
        STORM_OAK.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.STORM_OAK);
        SEEDSHAPER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SEEDSHAPER);
        SHIPWRIGHT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SHIPWRIGHT);
        GULL_SPOTTER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GULL_SPOTTER);
        CUTLASS_RAIDER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.CUTLASS_RAIDER);
        REEF_GUARD.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.REEF_GUARD);
        BOMBARD_CREW.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BOMBARD_CREW);
        TIDE_PRIEST.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.TIDE_PRIEST);
        HARPOONER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HARPOONER);
        FLAK_SWIVEL.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.FLAK_SWIVEL);
        MUDSKIPPER_SLOOP.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MUDSKIPPER_SLOOP);
        HARBOURMASTER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HARBOURMASTER);
        KRAKEN_CALLER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.KRAKEN_CALLER);
        MORTAR_GALLEY.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MORTAR_GALLEY);
        SKY_CORSAIR.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SKY_CORSAIR);
        DRYDOCK.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.DRYDOCK);
        LIGHTHOUSE_BATTERY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.LIGHTHOUSE_BATTERY);
        LEVIATHAN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.LEVIATHAN);
        ABYSSAL_SHIPYARD.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.ABYSSAL_SHIPYARD);
        GEOMANCER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GEOMANCER);
        PAPER_CRANE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.PAPER_CRANE);
        BLADE_DANCER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BLADE_DANCER);
        FIRE_ADEPT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.FIRE_ADEPT);
        JADE_GUARD.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.JADE_GUARD);
        WIND_MONK.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WIND_MONK);
        INK_SCRIBE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.INK_SCRIBE);
        FROST_ADEPT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.FROST_ADEPT);
        ROCKET_BATTERY.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ROCKET_BATTERY);
        JADE_PAGODA.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.JADE_PAGODA);
        LOTUS_ACADEMY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.LOTUS_ACADEMY);
        LEY_MASTER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.LEY_MASTER);
        THUNDER_DRAGON.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.THUNDER_DRAGON);
        EARTHSHAKER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.EARTHSHAKER);
        GRAVITY_MONK.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GRAVITY_MONK);
        MIRROR_MAGE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MIRROR_MAGE);
        CELESTIAL_OBSERVATORY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.CELESTIAL_OBSERVATORY);
        SEER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SEER);
        LANTERN_FORTRESS.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.LANTERN_FORTRESS);
        PHOENIX.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.PHOENIX);
        CELESTIAL_DRAGON.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.CELESTIAL_DRAGON);
        HEAVENS_GATE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.HEAVENS_GATE);
        TINKER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.TINKER);
        GYRO_SCOUT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GYRO_SCOUT);
        STEAM_RUNNER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.STEAM_RUNNER);
        RIFLE_SQUAD.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.RIFLE_SQUAD);
        IRON_WARDEN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.IRON_WARDEN);
        FLAK_CART.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.FLAK_CART);
        MORTAR_CRAWLER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MORTAR_CRAWLER);
        SHIELD_PROJECTOR.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SHIELD_PROJECTOR);
        SAPPER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SAPPER);
        DEPLOYED_TURRET.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.DEPLOYED_TURRET);
        FOUNDRY_HALL.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.FOUNDRY_HALL);
        MACHINE_SHOP.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.MACHINE_SHOP);
        FOX_COURIER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.FOX_COURIER);
        MOONWELL_BEARER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MOONWELL_BEARER);
        OWL_WATCHER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.OWL_WATCHER);
        HIVE_KEEPER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HIVE_KEEPER);
        LEAFBLADE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.LEAFBLADE);
        THORNBOW.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.THORNBOW);
        SENTINEL_TREANT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SENTINEL_TREANT);
        BONE_DRAGON.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BONE_DRAGON);
        ROYAL_GUARD.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ROYAL_GUARD);
        ENCHANTER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ENCHANTER);
        // Piglins
        GRUNT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GRUNT);
        STRIDER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.STRIDER);
        BRUTE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BRUTE);
        HEADHUNTER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HEADHUNTER);
        MARAUDER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MARAUDER);
        HOGLIN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HOGLIN);
        BLAZE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BLAZE);
        WITHER_SKELETON.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WITHER_SKELETON);
        GHAST.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GHAST);
        MAGMA_CUBE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MAGMA_CUBE);
        PIGLIN_MERCHANT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.PIGLIN_MERCHANT);
        WILDFIRE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WILDFIRE);

        ENDERMAN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ENDERMAN);
        POLAR_BEAR.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.POLAR_BEAR);
        GRIZZLY_BEAR.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GRIZZLY_BEAR);
        PANDA.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.PANDA);
        WOLF.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WOLF);
        LLAMA.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.LLAMA);
        BEE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BEE);
        KILLER_RABBIT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.KILLER_RABBIT);
        SILVERFISH.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SILVERFISH);

        HERO_BASE_REVIVE_COST.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HERO_BASE_REVIVE_COST);
        HERO_EXTRA_REVIVE_COST_PER_LEVEL.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HERO_EXTRA_REVIVE_COST_PER_LEVEL);

        // ******************* BUILDINGS ******************* //
        BEACON.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.BEACON);

        STOCKPILE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.STOCKPILE);
        METAL_EXTRACTOR.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.METAL_EXTRACTOR);
        WIND_GENERATOR.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.WIND_GENERATOR);
        ENERGY_CONVERTER.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.ENERGY_CONVERTER);
        STORAGE_VAULT.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.STORAGE_VAULT);
        OAK_BRIDGE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.OAK_BRIDGE);
        SPRUCE_BRIDGE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.SPRUCE_BRIDGE);
        BLACKSTONE_BRIDGE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.BLACKSTONE_BRIDGE);
        // Monsters
        MAUSOLEUM.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.MAUSOLEUM);
        HAUNTED_HOUSE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.HAUNTED_HOUSE);
        PUMPKIN_FARM.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.PUMPKIN_FARM);
        SCULK_CATALYST.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.SCULK_CATALYST);
        GRAVEYARD.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.GRAVEYARD);
        SPIDER_LAIR.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.SPIDER_LAIR);
        DUNGEON.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.DUNGEON);
        LABORATORY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.LABORATORY);
        DARK_WATCHTOWER.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.DARK_WATCHTOWER);
        SLIME_PIT.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.SLIME_PIT);
        STRONGHOLD.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.STRONGHOLD);
        ALTAR_OF_DARKNESS.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.ALTAR_OF_DARKNESS);
        MONSTER_MARKET.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.MONSTER_MARKET);
        // VillagersFaction
        TOWN_CENTRE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.TOWN_CENTRE);
        HEARTWOOD_HALL.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.HEARTWOOD_HALL);
        GROVE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.GROVE);
        WRECK_HARBOUR.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.WRECK_HARBOUR);
        SLIPWAY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.SLIPWAY);
        VILLAGER_HOUSE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.VILLAGER_HOUSE);
        WHEAT_FARM.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.WHEAT_FARM);
        BARRACKS.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.BARRACKS);
        BLACKSMITH.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.BLACKSMITH);
        WITCH_HUT.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.WITCH_HUT);
        ARCANE_TOWER.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.ARCANE_TOWER);
        LIBRARY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.LIBRARY);
        WATCHTOWER.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.WATCHTOWER);
        CASTLE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.CASTLE);
        IRON_GOLEM_BUILDING.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.IRON_GOLEM_BUILDING);
        SHRINE_OF_PROSPERITY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.SHRINE_OF_PROSPERITY);
        VILLAGER_MARKET.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.VILLAGER_MARKET);
        // Piglins
        CENTRAL_PORTAL.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.CENTRAL_PORTAL);
        BASIC_PORTAL.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.BASIC_PORTAL);
        CIVILIAN_PORTAL.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.CIVILIAN_PORTAL);
        POCKET_PORTAL.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.POCKET_PORTAL);
        NETHERWART_FARM.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.NETHERWART_FARM);
        BASTION.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.BASTION);
        HOGLIN_STABLES.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.HOGLIN_STABLES);
        FLAME_SANCTUARY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.FLAME_SANCTUARY);
        WITHER_SHRINE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.WITHER_SHRINE);
        BASALT_SPRINGS.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.BASALT_SPRINGS);
        FORTRESS.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.FORTRESS);
        INFERNAL_PORTAL.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.INFERNAL_PORTAL);
        PIGLIN_MARKET.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.PIGLIN_MARKET);
        // ******************* RESEARCH ******************* //
        RESEARCH_GOLEM_SMITHING.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_GOLEM_SMITHING);
        RESEARCH_SUPERIOR_BLACKSMITH.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_SUPERIOR_BLACKSMITH);
        RESEARCH_MILITIA_BOWS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_MILITIA_BOWS);
        RESEARCH_LAB_LIGHTNING_ROD.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_LAB_LIGHTNING_ROD);
        RESEARCH_RESOURCE_CAPACITY.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_RESOURCE_CAPACITY);
        RESEARCH_STEWARD.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_STEWARD);
        RESEARCH_ITEM_BACKPACKS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_ITEM_BACKPACK);
        RESEARCH_SPIDER_JOCKEYS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_SPIDER_JOCKEYS);
        RESEARCH_SPIDER_WEBS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_SPIDER_WEBS);
        RESEARCH_POISON_SPIDERS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_POISON_SPIDERS);
        RESEARCH_HUSKS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_HUSKS);
        RESEARCH_DROWNED.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_DROWNED);
        RESEARCH_STRAYS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_STRAYS);
        RESEARCH_BOGGED.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BOGGED);
        RESEARCH_OVERFLOWING_GRAVEYARD.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_OVERFLOWING_GRAVEYARD);
        RESEARCH_MASS_BURIALS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_MASS_BURIALS);
        RESEARCH_POSSESSION.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_POSSESSION);
        RESEARCH_SLIME_CONVERSION.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_SLIME_CONVERSION);
        RESEARCH_LINGERING_POTIONS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_LINGERING_POTIONS);
        RESEARCH_WATER_POTIONS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_WATER_POTIONS);
        RESEARCH_HEALING_POTIONS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_HEALING_POTIONS);
        RESEARCH_EVOKER_VEXES.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_EVOKER_VEXES);
        RESEARCH_UPGRADED_WINDCALLERS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_UPGRADED_WINDCALLERS);
        RESEARCH_CASTLE_FLAG.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_CASTLE_FLAG);
        RESEARCH_TIER_2.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_TIER_2);
        RESEARCH_TIER_3.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_TIER_3);
        UPGRADE_EXTRACTOR.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.UPGRADE_EXTRACTOR);
        RESEARCH_GRAND_LIBRARY.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_GRAND_LIBRARY);
        RESEARCH_SILVERFISH.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_SILVERFISH);
        RESEARCH_SCULK_AMPLIFIERS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_SCULK_AMPLIFIERS);
        RESEARCH_RAVAGER_ARTILLERY.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_RAVAGER_ARTILLERY);
        RESEARCH_BRUTE_SHIELDS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BRUTE_SHIELDS);
        RESEARCH_HOGLIN_CAVALRY.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_HOGLIN_CAVALRY);
        RESEARCH_HEAVY_TRIDENTS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_HEAVY_TRIDENTS);
        RESEARCH_GREEDY_TRIDENTS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_GREEDY_TRIDENTS);
        RESEARCH_CLEAVING_FLAILS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_CLEAVING_FLAILS);
        RESEARCH_BLAZE_FIRE_WALL.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BLAZE_FIRE_WALL);
        RESEARCH_FIRE_RESISTANCE.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_FIRE_RESISTANCE);
        RESEARCH_WITHER_CLOUDS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_WITHER_CLOUDS);
        RESEARCH_BLOODLUST.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BLOODLUST);
        RESEARCH_ADVANCED_PORTALS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_ADVANCED_PORTALS);
        RESEARCH_CIVILIAN_PORTAL.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_CIVILIAN_PORTAL);
        RESEARCH_MILITARY_PORTAL.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_MILITARY_PORTAL);
        RESEARCH_TRANSPORT_PORTAL.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_TRANSPORT_PORTAL);
        RESEARCH_CUBE_MAGMA.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_CUBE_MAGMA);
        RESEARCH_SOUL_FIREBALLS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_SOUL_FIREBALLS);
        RESEARCH_BEACON_LEVEL1.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BEACON_LEVEL1);
        RESEARCH_BEACON_LEVEL2.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BEACON_LEVEL2);
        RESEARCH_BEACON_LEVEL3.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BEACON_LEVEL3);
        RESEARCH_BEACON_LEVEL4.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BEACON_LEVEL4);
        RESEARCH_BEACON_LEVEL5.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_BEACON_LEVEL5);
        RESEARCH_UPGRADE_MARKET.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_UPGRADE_MARKET);
        RESEARCH_VINDICATOR_AXES.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_VINDICATOR_AXES);
        RESEARCH_PILLAGER_CROSSBOWS.bakeValues(ReignOfNetherCommonConfigs.ResearchCosts.RESEARCH_PILLAGER_CROSSBOWS);
        // ******************* ABILITIES ******************* //
        ENCHANT_MAIMING.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.ENCHANT_MAIMING);
        VINE_SNARE.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.VINE_SNARE);
        THICKET.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.THICKET);
        ENCHANT_QUICK_CHARGE.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.ENCHANT_QUICK_CHARGE);
        ENCHANT_SHARPNESS.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.ENCHANT_SHARPNESS);
        ENCHANT_MULTISHOT.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.ENCHANT_MULTISHOT);
        ENCHANT_VIGOR.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.ENCHANT_VIGOR);
        ENCHANT_GUST.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.ENCHANT_GUST);
        EQUIP_LEATHER_ARMOR.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.EQUIP_LEATHER_ARMOR);
        EQUIP_CHAINMAIL_ARMOR.bakeValues(ReignOfNetherCommonConfigs.AbilityCosts.EQUIP_CHAINMAIL_ARMOR);
        ENGINEER_CAPTAIN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ENGINEER_CAPTAIN);
        STEAM_JUGGERNAUT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.STEAM_JUGGERNAUT);
        TESLA_WALKER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.TESLA_WALKER);
        GYROCOPTER_GUNSHIP.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GYROCOPTER_GUNSHIP);
        DRILL_BURROWER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.DRILL_BURROWER);
        REPAIR_CRAWLER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.REPAIR_CRAWLER);
        CLOCKWORK_SPIDER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.CLOCKWORK_SPIDER);
        ASSEMBLY_WORKS.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.ASSEMBLY_WORKS);
        RAIL_BATTERY.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.RAIL_BATTERY);
        IRON_LEVIATHAN_ENGINE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.IRON_LEVIATHAN_ENGINE);
        SUN_T3B.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SUN_T3B);
        GRAVE_T3B.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GRAVE_T3B);
        HORDE_T3B.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.HORDE_T3B);
        VERDANT_T3B.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.VERDANT_T3B);
        TIDE_T3B.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.TIDE_T3B);
        STORM_T3B.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.STORM_T3B);
        BRASS_T3B.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BRASS_T3B);
        GREAT_FORGE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.GREAT_FORGE);
        ORIFLAMME_ELEPHANT.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ORIFLAMME_ELEPHANT);
        GOREHORN_RHINO.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.GOREHORN_RHINO);
        OSSUARY_BEHEMOTH.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.OSSUARY_BEHEMOTH);
        MOSSBACK_BISON.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.MOSSBACK_BISON);
        SUN_SPIRE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.SUN_SPIRE);
        NECROPOLIS.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.NECROPOLIS);
        WARD_SPIRE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.WARD_SPIRE);
        SHELLBACK_TURTLE.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SHELLBACK_TURTLE);
        JADE_QILIN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.JADE_QILIN);
        STEAM_HAULER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.STEAM_HAULER);
        // combat engineers (design/combat_engineers.md)
        FIELD_MASON.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.FIELD_MASON);
        BONESETTER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.BONESETTER);
        WRECKER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.WRECKER);
        ROOTWARDEN.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.ROOTWARDEN);
        SALVAGER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.SALVAGER);
        JADE_MENDER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.JADE_MENDER);
        PIONEER.bakeValues(ReignOfNetherCommonConfigs.UnitCosts.PIONEER);
        DOOM_FORGE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.DOOM_FORGE);
        WORLD_SEED.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.WORLD_SEED);
        MAELSTROM_ENGINE.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.MAELSTROM_ENGINE);
        HEAVENS_BELL.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.HEAVENS_BELL);
        EARTHCRACKER.bakeValues(ReignOfNetherCommonConfigs.BuildingCosts.EARTHCRACKER);
        // the anti-T3 emplacements cost a share of the priciest experimental (design/defences.md): after every T3 above
        com.solegendary.reignofnether.building.buildings.defence.DefenceCosts.bakeAntiT3();
        // the tier gate's prices win over the config entries (resources/TierGate: Tier 2 research, the T2 labs, the extractor refit)
        TierGate.bake();
    }
}
