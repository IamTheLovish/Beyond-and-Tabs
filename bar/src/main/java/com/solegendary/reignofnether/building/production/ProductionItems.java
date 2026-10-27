package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.researchItems.*;
import com.solegendary.reignofnether.unit.units.monsters.*;
import com.solegendary.reignofnether.unit.units.neutral.*;
import com.solegendary.reignofnether.unit.units.piglins.*;
import com.solegendary.reignofnether.unit.units.villagers.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.List;

public class ProductionItems {
    public static final CreeperProd CREEPER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "creeper"), new CreeperProd());
    public static final WraithProd WRAITH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wraith"), new WraithProd());
    public static final SkeletonProd SKELETON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "skeleton"), new SkeletonProd());
    public static final BoggedProd BOGGED = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bogged"), new BoggedProd());
    public static final ZombieProd ZOMBIE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "zombie"), new ZombieProd());
    public static final StrayProd STRAY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "stray"), new StrayProd());
    public static final HuskProd HUSK = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "husk"), new HuskProd());
    public static final DrownedProd DROWNED = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "drowned"), new DrownedProd());
    public static final SpiderProd SPIDER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spider"), new SpiderProd());
    public static final PoisonSpiderProd POISON_SPIDER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "poison_spider"), new PoisonSpiderProd());
    public static final VillagerProd VILLAGER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "villager"), new VillagerProd());
    public static final MilitiaProd MILITIA = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "militia"), new MilitiaProd());
    public static final ScoutDogProd SCOUT_DOG = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "scout_dog"), new ScoutDogProd());
    public static final ScoutCatProd SCOUT_CAT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "scout_cat"), new ScoutCatProd());
    public static final ZombieVillagerProd ZOMBIE_VILLAGER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "zombie_villager"), new ZombieVillagerProd());
    public static final BatProd BAT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bat"), new BatProd());
    public static final VindicatorProd VINDICATOR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "vindicator"), new VindicatorProd());
    public static final PillagerProd PILLAGER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "pillager"), new PillagerProd());
    public static final IronGolemProd IRON_GOLEM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_golem"), new IronGolemProd());
    public static final WitchProd WITCH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "witch"), new WitchProd());
    public static final EvokerProd EVOKER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "evoker"), new EvokerProd());
    public static final WindcallerProd WINDCALLER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "windcaller"), new WindcallerProd());
    public static final SlimeProd SLIME = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "slime"), new SlimeProd());
    public static final WardenProd WARDEN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "warden"), new WardenProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerProd WORLD_TREE_WALKER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "world_tree_walker"), new com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerProd());   // Verdant T3
    public static final com.solegendary.reignofnether.unit.units.villagers.SunColossusProd SUN_COLOSSUS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sun_colossus"), new com.solegendary.reignofnether.unit.units.villagers.SunColossusProd());
    // Verdant Court
    public static final com.solegendary.reignofnether.unit.units.verdant.SeedshaperProd SEEDSHAPER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "seedshaper"), new com.solegendary.reignofnether.unit.units.verdant.SeedshaperProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.FoxCourierProd FOX_COURIER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "fox_courier"), new com.solegendary.reignofnether.unit.units.verdant.FoxCourierProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.MoonwellBearerProd MOONWELL_BEARER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "moonwell_bearer"), new com.solegendary.reignofnether.unit.units.verdant.MoonwellBearerProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.OwlWatcherProd OWL_WATCHER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "owl_watcher"), new com.solegendary.reignofnether.unit.units.verdant.OwlWatcherProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.HiveKeeperProd HIVE_KEEPER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hive_keeper"), new com.solegendary.reignofnether.unit.units.verdant.HiveKeeperProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.LeafbladeProd LEAFBLADE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "leafblade"), new com.solegendary.reignofnether.unit.units.verdant.LeafbladeProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.ThornbowProd THORNBOW = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thornbow"), new com.solegendary.reignofnether.unit.units.verdant.ThornbowProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.SentinelTreantProd SENTINEL_TREANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sentinel_treant"), new com.solegendary.reignofnether.unit.units.verdant.SentinelTreantProd());
    public static final com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectProd ROYAL_ARCHITECT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "royal_architect"), new com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectProd());
    public static final com.solegendary.reignofnether.unit.units.monsters.EmbalmerProd EMBALMER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "embalmer"), new com.solegendary.reignofnether.unit.units.monsters.EmbalmerProd());
    public static final com.solegendary.reignofnether.unit.units.piglins.BonewrightProd BONEWRIGHT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bonewright"), new com.solegendary.reignofnether.unit.units.piglins.BonewrightProd());
    // Verdant Court T2 (Circle of Elders)
    public static final com.solegendary.reignofnether.unit.units.verdant.ElderDruidProd ELDER_DRUID = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "elder_druid"), new com.solegendary.reignofnether.unit.units.verdant.ElderDruidProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.StagLancerProd STAG_LANCER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "stag_lancer"), new com.solegendary.reignofnether.unit.units.verdant.StagLancerProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.ShadeRangerProd SHADE_RANGER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shade_ranger"), new com.solegendary.reignofnether.unit.units.verdant.ShadeRangerProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.ElderTreantProd ELDER_TREANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "elder_treant"), new com.solegendary.reignofnether.unit.units.verdant.ElderTreantProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.WispChoirProd WISP_CHOIR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wisp_choir"), new com.solegendary.reignofnether.unit.units.verdant.WispChoirProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.BloomPriestessProd BLOOM_PRIESTESS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bloom_priestess"), new com.solegendary.reignofnether.unit.units.verdant.BloomPriestessProd());
    public static final RavagerProd RAVAGER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ravager"), new RavagerProd());
    public static final com.solegendary.reignofnether.unit.units.piglins.WarMammothProd WAR_MAMMOTH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "war_mammoth"), new com.solegendary.reignofnether.unit.units.piglins.WarMammothProd());

    public static final GruntProd GRUNT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "grunt"), new GruntProd());
    public static final StriderProd STRIDER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "strider"), new StriderProd());
    public static final BruteProd BRUTE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "brute"), new BruteProd());
    public static final HeadhunterProd HEADHUNTER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "headhunter"), new HeadhunterProd());
    public static final MarauderProd MARAUDER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "marauder"), new MarauderProd());
    public static final HoglinProd HOGLIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hoglin"), new HoglinProd());
    public static final BlazeProd BLAZE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blaze"), new BlazeProd());
    public static final WitherSkeletonProd WITHER_SKELETON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wither_skeleton"), new WitherSkeletonProd());
    public static final MagmaCubeProd MAGMA_CUBE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "magma_cube"), new MagmaCubeProd());
    public static final com.solegendary.reignofnether.unit.units.monsters.BoneDragonProd BONE_DRAGON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bone_dragon"), new com.solegendary.reignofnether.unit.units.monsters.BoneDragonProd());
    public static final GhastProd GHAST = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ghast"), new GhastProd());
    public static final RoyalGuardProd ROYAL_GUARD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "royal_guard"), new RoyalGuardProd());
    public static final RoyalGuardReviveProd ROYAL_GUARD_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "royal_guard_revive"), new RoyalGuardReviveProd());
    public static final EnchanterProd ENCHANTER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "enchanter"), new EnchanterProd());
    public static final EnchanterReviveProd ENCHANTER_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "enchanter_revive"), new EnchanterReviveProd());
    public static final NecromancerProd NECROMANCER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "necromancer"), new NecromancerProd());
    public static final NecromancerReviveProd NECROMANCER_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "necromancer_revive"), new NecromancerReviveProd());
    public static final WretchedWraithProd WRETCHED_WRAITH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wretched_wraith"), new WretchedWraithProd());
    public static final WretchedWraithReviveProd WRETCHED_WRAITH_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wretched_wraith_revive"), new WretchedWraithReviveProd());
    public static final PiglinMerchantProd PIGLIN_MERCHANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "piglin_merchant"), new PiglinMerchantProd());
    public static final PiglinMerchantReviveProd PIGLIN_MERCHANT_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "piglin_merchant_revive"), new PiglinMerchantReviveProd());
    public static final WildfireProd WILDFIRE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wildfire"), new WildfireProd());
    public static final WildfireReviveProd WILDFIRE_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wildfire_revive"), new WildfireReviveProd());
    public static final ZombiePiglinProd ZOMBIE_PIGLIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "zombie_piglin"), new ZombiePiglinProd());
    public static final ZoglinProd ZOGLIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "zoglin"), new ZoglinProd());
    public static final EndermanProd ENDERMAN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "enderman"), new EndermanProd());
    public static final PolarBearProd POLAR_BEAR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "polar_bear"), new PolarBearProd());
    public static final GrizzlyBearProd GRIZZLY_BEAR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "grizzly_bear"), new GrizzlyBearProd());
    public static final PandaProd PANDA = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "panda"), new PandaProd());
    public static final WolfProd WOLF = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wolf"), new WolfProd());
    public static final LlamaProd LLAMA = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "llama"), new LlamaProd());
    public static final BeeProd BEE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bee"), new BeeProd());

    public static final com.solegendary.reignofnether.research.researchItems.ResearchTier2 RESEARCH_TIER_2 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_tier_2"), new com.solegendary.reignofnether.research.researchItems.ResearchTier2());
    public static final com.solegendary.reignofnether.research.researchItems.ResearchTier3 RESEARCH_TIER_3 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_tier_3"), new com.solegendary.reignofnether.research.researchItems.ResearchTier3());
    public static final com.solegendary.reignofnether.research.researchItems.UpgradeExtractor UPGRADE_EXTRACTOR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "upgrade_extractor"), new com.solegendary.reignofnether.research.researchItems.UpgradeExtractor());
    public static final ResearchVindicatorAxes RESEARCH_VINDICATOR_AXES = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "vindicator_axes"), new ResearchVindicatorAxes());
    public static final ResearchPillagerCrossbows RESEARCH_PILLAGER_CROSSBOWS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "pillager_crossbows"), new ResearchPillagerCrossbows());
    public static final ResearchMilitiaBows RESEARCH_MILITIA_BOWS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "militia_bows"), new ResearchMilitiaBows());
    public static final ResearchLabLightningRod RESEARCH_LAB_LIGHTNING_ROD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lab_lightning_rod"), new ResearchLabLightningRod());
    public static final ResearchResourceCapacity RESEARCH_RESOURCE_CAPACITY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "resource_capacity"), new ResearchResourceCapacity());
    public static final com.solegendary.reignofnether.research.researchItems.ResearchSteward RESEARCH_STEWARD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_steward"), new com.solegendary.reignofnether.research.researchItems.ResearchSteward());
    public static final ResearchItemBackpacks RESEARCH_ITEM_BACKPACKS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "item_backpacks"), new ResearchItemBackpacks());
    public static final ResearchSpiderJockeys RESEARCH_SPIDER_JOCKEYS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spider_jockeys"), new ResearchSpiderJockeys());
    public static final ResearchPoisonSpiders RESEARCH_POISON_SPIDERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_poison_spiders"), new ResearchPoisonSpiders());
    public static final ResearchHusks RESEARCH_HUSKS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_husks"), new ResearchHusks());
    public static final ResearchDrowned RESEARCH_DROWNED = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_drowned"), new ResearchDrowned());
    public static final ResearchStrays RESEARCH_STRAYS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_strays"), new ResearchStrays());
    public static final ResearchBogged RESEARCH_BOGGED = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_bogged"), new ResearchBogged());
    public static final ResearchOverflowingGraveyard RESEARCH_OVERFLOWING_GRAVEYARD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_overflowing_graveyard"), new ResearchOverflowingGraveyard());
    public static final ResearchMassBurials RESEARCH_MASS_BURIAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_mass_burial"), new ResearchMassBurials());
    public static final ResearchSlimeConversion RESEARCH_SLIME_CONVERSION = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "slime_conversion"), new ResearchSlimeConversion());
    public static final ResearchLingeringPotions RESEARCH_LINGERING_POTIONS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lingering_potions"), new ResearchLingeringPotions());
    public static final ResearchWaterPotions RESEARCH_WATER_POTIONS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "water_potions"), new ResearchWaterPotions());
    public static final ResearchHealingPotions RESEARCH_HEALING_POTIONS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "healing_potions"), new ResearchHealingPotions());
    public static final ResearchEvokerVexes RESEARCH_EVOKER_VEXES = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "evoker_vexes"), new ResearchEvokerVexes());
    public static final ResearchUpgradedWindcallers RESEARCH_UPGRADED_WINDCALLERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "upgraded_windcallers"), new ResearchUpgradedWindcallers());
    public static final ResearchGolemSmithing RESEARCH_GOLEM_SMITHING = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "golem_smithing"), new ResearchGolemSmithing());
    public static final ResearchSilverfish RESEARCH_SILVERFISH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "silverfish"), new ResearchSilverfish());
    public static final ResearchSculkAmplifiers RESEARCH_SCULK_AMPLIFIERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sculk_amplifiers"), new ResearchSculkAmplifiers());
    public static final ResearchCastleFlag RESEARCH_CASTLE_FLAG = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "castle_flag"), new ResearchCastleFlag());
    public static final ResearchRavagerCavalry RESEARCH_RAVAGER_CAVALRY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ravager_cavalry"), new ResearchRavagerCavalry());
    public static final ResearchBruteShields RESEARCH_BRUTE_SHIELDS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "brute_shields"), new ResearchBruteShields());
    public static final ResearchHoglinCavalry RESEARCH_HOGLIN_CAVALRY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hoglin_cavalry"), new ResearchHoglinCavalry());
    public static final ResearchHeavyTridents RESEARCH_HEAVY_TRIDENTS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "heavy_tridents"), new ResearchHeavyTridents());
    public static final ResearchGreedyTridents RESEARCH_GREEDY_TRIDENTS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "greedy_tridents"), new ResearchGreedyTridents());
    public static final ResearchCleavingFlails RESEARCH_CLEAVING_FLAILS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "cleaving_flails"), new ResearchCleavingFlails());
    public static final ResearchBlazeFirewall RESEARCH_BLAZE_FIREWALL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blaze_firewall"), new ResearchBlazeFirewall());
    public static final ResearchWitherClouds RESEARCH_WITHER_CLOUDS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wither_clouds"), new ResearchWitherClouds());
    public static final ResearchAdvancedPortals RESEARCH_ADVANCED_PORTALS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "advanced_portals"), new ResearchAdvancedPortals());
    public static final ResearchFireResistance RESEARCH_FIRE_RESISTANCE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "fire_resistance"), new ResearchFireResistance());
    public static final ResearchGrandLibrary RESEARCH_GRAND_LIBRARY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "grand_library"), new ResearchGrandLibrary());
    public static final ResearchSuperiorBlacksmith RESEARCH_SUPERIOR_BLACKSMITH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "superior_blacksmith"), new ResearchSuperiorBlacksmith());
    public static final ResearchSpiderWebs RESEARCH_SPIDER_WEBS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spider_webs"), new ResearchSpiderWebs());
    public static final ResearchPossession RESEARCH_POSSESSION = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "possession"), new ResearchPossession());
    public static final ResearchBloodlust RESEARCH_BLOODLUST = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bloodlust"), new ResearchBloodlust());
    public static final ResearchCubeMagma RESEARCH_CUBE_MAGMA = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "cube_magma"), new ResearchCubeMagma());
    public static final ResearchSoulFireballs RESEARCH_SOUL_FIREBALLS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "soul_fireballs"), new ResearchSoulFireballs());
    public static final ResearchMarketUpgradeVillager RESEARCH_MARKET_UPGRADE_VILLAGER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_market_upgrade_villager"), new ResearchMarketUpgradeVillager());
    public static final ResearchMarketUpgradeMonster RESEARCH_MARKET_UPGRADE_MONSTERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_market_upgrade_monster"), new ResearchMarketUpgradeMonster());
    public static final ResearchMarketUpgradePiglin RESEARCH_MARKET_UPGRADE_PIGLINS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "research_market_upgrade_piglin"), new ResearchMarketUpgradePiglin());

    public static final ResearchPortalForCivilian RESEARCH_PORTAL_FOR_CIVILIAN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_for_civilian"), new ResearchPortalForCivilian());
    public static final ResearchPortalForMilitary RESEARCH_PORTAL_FOR_MILITARY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_for_military"), new ResearchPortalForMilitary());
    public static final ResearchPortalForTransport RESEARCH_PORTAL_FOR_TRANSPORT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_for_transport"), new ResearchPortalForTransport());
    public static final ResearchBeaconLevel1 RESEARCH_BEACON_LEVEL_1 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "beacon_level_1"), new ResearchBeaconLevel1());
    public static final ResearchBeaconLevel2 RESEARCH_BEACON_LEVEL_2 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "beacon_level_2"), new ResearchBeaconLevel2());
    public static final ResearchBeaconLevel3 RESEARCH_BEACON_LEVEL_3 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "beacon_level_3"), new ResearchBeaconLevel3());
    public static final ResearchBeaconLevel4 RESEARCH_BEACON_LEVEL_4 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "beacon_level_4"), new ResearchBeaconLevel4());
    public static final ResearchBeaconLevel5 RESEARCH_BEACON_LEVEL_5 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "beacon_level_5"), new ResearchBeaconLevel5());

    // Tidewrought (slice 1), registered last so no existing production item's id moves
    public static final com.solegendary.reignofnether.unit.units.tide.ShipwrightProd SHIPWRIGHT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shipwright"), new com.solegendary.reignofnether.unit.units.tide.ShipwrightProd());
    public static final com.solegendary.reignofnether.unit.units.tide.GullSpotterProd GULL_SPOTTER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gull_spotter"), new com.solegendary.reignofnether.unit.units.tide.GullSpotterProd());
    public static final com.solegendary.reignofnether.unit.units.tide.CutlassRaiderProd CUTLASS_RAIDER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "cutlass_raider"), new com.solegendary.reignofnether.unit.units.tide.CutlassRaiderProd());
    public static final com.solegendary.reignofnether.unit.units.tide.ReefGuardProd REEF_GUARD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "reef_guard"), new com.solegendary.reignofnether.unit.units.tide.ReefGuardProd());
    public static final com.solegendary.reignofnether.unit.units.tide.BombardCrewProd BOMBARD_CREW = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bombard_crew"), new com.solegendary.reignofnether.unit.units.tide.BombardCrewProd());
    public static final com.solegendary.reignofnether.unit.units.tide.TidePriestProd TIDE_PRIEST = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tide_priest"), new com.solegendary.reignofnether.unit.units.tide.TidePriestProd());
    // Tidewrought slice 2
    public static final com.solegendary.reignofnether.unit.units.tide.HarpoonerProd HARPOONER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "harpooner"), new com.solegendary.reignofnether.unit.units.tide.HarpoonerProd());
    public static final com.solegendary.reignofnether.unit.units.tide.FlakSwivelProd FLAK_SWIVEL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "flak_swivel"), new com.solegendary.reignofnether.unit.units.tide.FlakSwivelProd());
    public static final com.solegendary.reignofnether.unit.units.tide.MudskipperSloopProd MUDSKIPPER_SLOOP = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mudskipper_sloop"), new com.solegendary.reignofnether.unit.units.tide.MudskipperSloopProd());
    // Tidewrought slice 3: the Drydock (T2)
    public static final com.solegendary.reignofnether.unit.units.tide.HarbourmasterProd HARBOURMASTER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "harbourmaster"), new com.solegendary.reignofnether.unit.units.tide.HarbourmasterProd());
    public static final com.solegendary.reignofnether.unit.units.tide.KrakenCallerProd KRAKEN_CALLER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "kraken_caller"), new com.solegendary.reignofnether.unit.units.tide.KrakenCallerProd());
    public static final com.solegendary.reignofnether.unit.units.tide.MortarGalleyProd MORTAR_GALLEY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mortar_galley"), new com.solegendary.reignofnether.unit.units.tide.MortarGalleyProd());
    public static final com.solegendary.reignofnether.unit.units.tide.SkyCorsairProd SKY_CORSAIR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sky_corsair"), new com.solegendary.reignofnether.unit.units.tide.SkyCorsairProd());
    // Tidewrought slice 4: the Abyssal Shipyard (T3)
    public static final com.solegendary.reignofnether.unit.units.tide.LeviathanProd LEVIATHAN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "leviathan"), new com.solegendary.reignofnether.unit.units.tide.LeviathanProd());
    // Stormcallers (slice 1), registered last so no existing production item's id moves
    public static final com.solegendary.reignofnether.unit.units.storm.GeomancerProd GEOMANCER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geomancer"), new com.solegendary.reignofnether.unit.units.storm.GeomancerProd());
    public static final com.solegendary.reignofnether.unit.units.storm.PaperCraneProd PAPER_CRANE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "paper_crane"), new com.solegendary.reignofnether.unit.units.storm.PaperCraneProd());
    public static final com.solegendary.reignofnether.unit.units.storm.BladeDancerProd BLADE_DANCER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blade_dancer"), new com.solegendary.reignofnether.unit.units.storm.BladeDancerProd());
    public static final com.solegendary.reignofnether.unit.units.storm.FireAdeptProd FIRE_ADEPT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "fire_adept"), new com.solegendary.reignofnether.unit.units.storm.FireAdeptProd());
    public static final com.solegendary.reignofnether.unit.units.storm.JadeGuardProd JADE_GUARD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "jade_guard"), new com.solegendary.reignofnether.unit.units.storm.JadeGuardProd());
    public static final com.solegendary.reignofnether.unit.units.storm.WindMonkProd WIND_MONK = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_monk"), new com.solegendary.reignofnether.unit.units.storm.WindMonkProd());
    // Stormcallers (slice 2)
    public static final com.solegendary.reignofnether.unit.units.storm.InkScribeProd INK_SCRIBE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ink_scribe"), new com.solegendary.reignofnether.unit.units.storm.InkScribeProd());
    public static final com.solegendary.reignofnether.unit.units.storm.FrostAdeptProd FROST_ADEPT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "frost_adept"), new com.solegendary.reignofnether.unit.units.storm.FrostAdeptProd());
    public static final com.solegendary.reignofnether.unit.units.storm.RocketBatteryProd ROCKET_BATTERY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rocket_battery"), new com.solegendary.reignofnether.unit.units.storm.RocketBatteryProd());
    // Stormcallers slice 3: the Celestial Observatory (T2)
    public static final com.solegendary.reignofnether.unit.units.storm.LeyMasterProd LEY_MASTER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ley_master"), new com.solegendary.reignofnether.unit.units.storm.LeyMasterProd());
    public static final com.solegendary.reignofnether.unit.units.storm.ThunderDragonProd THUNDER_DRAGON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thunder_dragon"), new com.solegendary.reignofnether.unit.units.storm.ThunderDragonProd());
    public static final com.solegendary.reignofnether.unit.units.storm.EarthshakerProd EARTHSHAKER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "earthshaker"), new com.solegendary.reignofnether.unit.units.storm.EarthshakerProd());
    public static final com.solegendary.reignofnether.unit.units.storm.GravityMonkProd GRAVITY_MONK = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gravity_monk"), new com.solegendary.reignofnether.unit.units.storm.GravityMonkProd());
    public static final com.solegendary.reignofnether.unit.units.storm.MirrorMageProd MIRROR_MAGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mirror_mage"), new com.solegendary.reignofnether.unit.units.storm.MirrorMageProd());
    // Stormcallers slice 4: the rest of the Observatory's T2, and Heaven's Gate's Celestial Dragon (T3)
    public static final com.solegendary.reignofnether.unit.units.storm.SeerProd SEER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "seer"), new com.solegendary.reignofnether.unit.units.storm.SeerProd());
    public static final com.solegendary.reignofnether.unit.units.storm.LanternFortressProd LANTERN_FORTRESS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lantern_fortress"), new com.solegendary.reignofnether.unit.units.storm.LanternFortressProd());
    public static final com.solegendary.reignofnether.unit.units.storm.PhoenixProd PHOENIX = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "phoenix"), new com.solegendary.reignofnether.unit.units.storm.PhoenixProd());
    public static final com.solegendary.reignofnether.unit.units.storm.CelestialDragonProd CELESTIAL_DRAGON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "celestial_dragon"), new com.solegendary.reignofnether.unit.units.storm.CelestialDragonProd());
    // Brassworks (slice 1), registered last so no existing production item's id moves
    public static final com.solegendary.reignofnether.unit.units.brass.TinkerProd TINKER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tinker"), new com.solegendary.reignofnether.unit.units.brass.TinkerProd());
    public static final com.solegendary.reignofnether.unit.units.brass.GyroScoutProd GYRO_SCOUT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gyro_scout"), new com.solegendary.reignofnether.unit.units.brass.GyroScoutProd());
    public static final com.solegendary.reignofnether.unit.units.brass.SteamRunnerProd STEAM_RUNNER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "steam_runner"), new com.solegendary.reignofnether.unit.units.brass.SteamRunnerProd());
    public static final com.solegendary.reignofnether.unit.units.brass.RifleSquadProd RIFLE_SQUAD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rifle_squad"), new com.solegendary.reignofnether.unit.units.brass.RifleSquadProd());
    public static final com.solegendary.reignofnether.unit.units.brass.IronWardenProd IRON_WARDEN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_warden"), new com.solegendary.reignofnether.unit.units.brass.IronWardenProd());
    public static final com.solegendary.reignofnether.unit.units.brass.FlakCartProd FLAK_CART = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "flak_cart"), new com.solegendary.reignofnether.unit.units.brass.FlakCartProd());
    public static final com.solegendary.reignofnether.unit.units.brass.MortarCrawlerProd MORTAR_CRAWLER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mortar_crawler"), new com.solegendary.reignofnether.unit.units.brass.MortarCrawlerProd());
    public static final com.solegendary.reignofnether.unit.units.brass.ShieldProjectorProd SHIELD_PROJECTOR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shield_projector"), new com.solegendary.reignofnether.unit.units.brass.ShieldProjectorProd());
    public static final com.solegendary.reignofnether.unit.units.brass.SapperProd SAPPER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sapper"), new com.solegendary.reignofnether.unit.units.brass.SapperProd());

    // building levels (design/building_upgrades.md): a lab's in-place upgrades, registered last so no id moves
    public static final com.solegendary.reignofnether.research.researchItems.UpgradeLabLevel UPGRADE_LAB_L2 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "upgrade_lab_l2"), new com.solegendary.reignofnether.research.researchItems.UpgradeLabLevel(2));
    public static final com.solegendary.reignofnether.research.researchItems.UpgradeLabLevel UPGRADE_LAB_L3 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "upgrade_lab_l3"), new com.solegendary.reignofnether.research.researchItems.UpgradeLabLevel(3));
    // Brassworks slice 2: the Assembly Works' T2 (design/brassworks_plan.md)
    public static final com.solegendary.reignofnether.unit.units.brass.EngineerCaptainProd ENGINEER_CAPTAIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "engineer_captain"), new com.solegendary.reignofnether.unit.units.brass.EngineerCaptainProd());
    public static final com.solegendary.reignofnether.unit.units.brass.SteamJuggernautProd STEAM_JUGGERNAUT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "steam_juggernaut"), new com.solegendary.reignofnether.unit.units.brass.SteamJuggernautProd());
    public static final com.solegendary.reignofnether.unit.units.brass.TeslaWalkerProd TESLA_WALKER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tesla_walker"), new com.solegendary.reignofnether.unit.units.brass.TeslaWalkerProd());
    public static final com.solegendary.reignofnether.unit.units.brass.GyrocopterGunshipProd GYROCOPTER_GUNSHIP = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gyrocopter_gunship"), new com.solegendary.reignofnether.unit.units.brass.GyrocopterGunshipProd());
    public static final com.solegendary.reignofnether.unit.units.brass.DrillBurrowerProd DRILL_BURROWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "drill_burrower"), new com.solegendary.reignofnether.unit.units.brass.DrillBurrowerProd());
    public static final com.solegendary.reignofnether.unit.units.brass.RepairCrawlerProd REPAIR_CRAWLER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "repair_crawler"), new com.solegendary.reignofnether.unit.units.brass.RepairCrawlerProd());
    public static final com.solegendary.reignofnether.unit.units.brass.ClockworkSpiderProd CLOCKWORK_SPIDER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "clockwork_spider"), new com.solegendary.reignofnether.unit.units.brass.ClockworkSpiderProd());
    // Brassworks slice 4: the Great Forge's experimental (appended last so no existing item's id moves)
    public static final com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineProd IRON_LEVIATHAN_ENGINE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_leviathan_engine"), new com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineProd());

    // Hero slice 2 (design/hero_mode.md): each faction's hero and its revive, trained at the faction's T1 army lab
    public static final com.solegendary.reignofnether.hero.HeroProd DAWN_PALADIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "dawn_paladin"), new com.solegendary.reignofnether.hero.HeroProd(com.solegendary.reignofnether.hero.HeroKits.DAWN_PALADIN, EntityRegistrar.DAWN_PALADIN_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroReviveProd DAWN_PALADIN_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "dawn_paladin_revive"), new com.solegendary.reignofnether.hero.HeroReviveProd(com.solegendary.reignofnether.hero.HeroKits.DAWN_PALADIN, EntityRegistrar.DAWN_PALADIN_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroProd GRAVECALLER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gravecaller"), new com.solegendary.reignofnether.hero.HeroProd(com.solegendary.reignofnether.hero.HeroKits.GRAVECALLER, EntityRegistrar.GRAVECALLER_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroReviveProd GRAVECALLER_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gravecaller_revive"), new com.solegendary.reignofnether.hero.HeroReviveProd(com.solegendary.reignofnether.hero.HeroKits.GRAVECALLER, EntityRegistrar.GRAVECALLER_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroProd HORDE_WARCHIEF = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "horde_warchief"), new com.solegendary.reignofnether.hero.HeroProd(com.solegendary.reignofnether.hero.HeroKits.HORDE_WARCHIEF, EntityRegistrar.HORDE_WARCHIEF_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroReviveProd HORDE_WARCHIEF_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "horde_warchief_revive"), new com.solegendary.reignofnether.hero.HeroReviveProd(com.solegendary.reignofnether.hero.HeroKits.HORDE_WARCHIEF, EntityRegistrar.HORDE_WARCHIEF_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroProd ASH_SHAMAN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ash_shaman"), new com.solegendary.reignofnether.hero.HeroProd(com.solegendary.reignofnether.hero.HeroKits.ASH_SHAMAN, EntityRegistrar.ASH_SHAMAN_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroReviveProd ASH_SHAMAN_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ash_shaman_revive"), new com.solegendary.reignofnether.hero.HeroReviveProd(com.solegendary.reignofnether.hero.HeroKits.ASH_SHAMAN, EntityRegistrar.ASH_SHAMAN_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroProd THORNHEART_DRUID = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thornheart_druid"), new com.solegendary.reignofnether.hero.HeroProd(com.solegendary.reignofnether.hero.HeroKits.THORNHEART_DRUID, EntityRegistrar.THORNHEART_DRUID_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroReviveProd THORNHEART_DRUID_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thornheart_druid_revive"), new com.solegendary.reignofnether.hero.HeroReviveProd(com.solegendary.reignofnether.hero.HeroKits.THORNHEART_DRUID, EntityRegistrar.THORNHEART_DRUID_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroProd CORSAIR_CAPTAIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "corsair_captain"), new com.solegendary.reignofnether.hero.HeroProd(com.solegendary.reignofnether.hero.HeroKits.CORSAIR_CAPTAIN, EntityRegistrar.CORSAIR_CAPTAIN_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroReviveProd CORSAIR_CAPTAIN_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "corsair_captain_revive"), new com.solegendary.reignofnether.hero.HeroReviveProd(com.solegendary.reignofnether.hero.HeroKits.CORSAIR_CAPTAIN, EntityRegistrar.CORSAIR_CAPTAIN_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroProd STORM_WITCH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storm_witch"), new com.solegendary.reignofnether.hero.HeroProd(com.solegendary.reignofnether.hero.HeroKits.STORM_WITCH, EntityRegistrar.STORM_WITCH_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroReviveProd STORM_WITCH_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storm_witch_revive"), new com.solegendary.reignofnether.hero.HeroReviveProd(com.solegendary.reignofnether.hero.HeroKits.STORM_WITCH, EntityRegistrar.STORM_WITCH_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroProd ARCH_ENGINEER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "arch_engineer"), new com.solegendary.reignofnether.hero.HeroProd(com.solegendary.reignofnether.hero.HeroKits.ARCH_ENGINEER, EntityRegistrar.ARCH_ENGINEER_UNIT));
    public static final com.solegendary.reignofnether.hero.HeroReviveProd ARCH_ENGINEER_REVIVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "arch_engineer_revive"), new com.solegendary.reignofnether.hero.HeroReviveProd(com.solegendary.reignofnether.hero.HeroKits.ARCH_ENGINEER, EntityRegistrar.ARCH_ENGINEER_UNIT));
    // big-units (design/big_units.md): the T2 beasts
    public static final com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantProd ORIFLAMME_ELEPHANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "oriflamme_elephant"), new com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantProd());
    public static final com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoProd GOREHORN_RHINO = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gorehorn_rhino"), new com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoProd());
    public static final com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothProd OSSUARY_BEHEMOTH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ossuary_behemoth"), new com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothProd());
    public static final com.solegendary.reignofnether.unit.units.verdant.MossbackBisonProd MOSSBACK_BISON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mossback_bison"), new com.solegendary.reignofnether.unit.units.verdant.MossbackBisonProd());
    // beasts-2 (design/big_units.md): the other three T2 beasts
    public static final com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleProd SHELLBACK_TURTLE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shellback_turtle"), new com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleProd());
    public static final com.solegendary.reignofnether.unit.units.storm.JadeQilinProd JADE_QILIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "jade_qilin"), new com.solegendary.reignofnether.unit.units.storm.JadeQilinProd());
    public static final com.solegendary.reignofnether.unit.units.brass.SteamHaulerProd STEAM_HAULER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "steam_hauler"), new com.solegendary.reignofnether.unit.units.brass.SteamHaulerProd());
    // combat engineers (design/combat_engineers.md): appended last so no existing item's id moves
    public static final com.solegendary.reignofnether.unit.units.engineers.FieldMasonProd FIELD_MASON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "field_mason"), new com.solegendary.reignofnether.unit.units.engineers.FieldMasonProd());
    public static final com.solegendary.reignofnether.unit.units.engineers.BonesetterProd BONESETTER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bonesetter"), new com.solegendary.reignofnether.unit.units.engineers.BonesetterProd());
    public static final com.solegendary.reignofnether.unit.units.engineers.WreckerProd WRECKER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wrecker"), new com.solegendary.reignofnether.unit.units.engineers.WreckerProd());
    public static final com.solegendary.reignofnether.unit.units.engineers.RootwardenProd ROOTWARDEN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rootwarden"), new com.solegendary.reignofnether.unit.units.engineers.RootwardenProd());
    public static final com.solegendary.reignofnether.unit.units.engineers.SalvagerProd SALVAGER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "salvager"), new com.solegendary.reignofnether.unit.units.engineers.SalvagerProd());
    public static final com.solegendary.reignofnether.unit.units.engineers.JadeMenderProd JADE_MENDER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "jade_mender"), new com.solegendary.reignofnether.unit.units.engineers.JadeMenderProd());
    public static final com.solegendary.reignofnether.unit.units.engineers.PioneerProd PIONEER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "pioneer"), new com.solegendary.reignofnether.unit.units.engineers.PioneerProd());
    // second T3 per faction, slot 5 of the T3 lab (second-t3-design.md, slice T3b-1): PLACEHOLDERS that train the first T3's body
    public static final com.solegendary.reignofnether.building.production.SecondT3Prod SUN_T3B = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sun_t3b"), new com.solegendary.reignofnether.building.production.SecondT3Prod(com.solegendary.reignofnether.unit.units.villagers.CathedralWardenUnit.ITEM_NAME, "sun_colossus", com.solegendary.reignofnether.resources.ResourceCosts.SUN_T3B, SUN_COLOSSUS, () -> EntityRegistrar.CATHEDRAL_WARDEN_UNIT.get(), "entity.reignofnether.cathedral_warden_unit"));
    public static final com.solegendary.reignofnether.building.production.SecondT3Prod GRAVE_T3B = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "grave_t3b"), new com.solegendary.reignofnether.building.production.SecondT3Prod(com.solegendary.reignofnether.unit.units.monsters.PlagueAbominationUnit.ITEM_NAME, "zoglin", com.solegendary.reignofnether.resources.ResourceCosts.GRAVE_T3B, BONE_DRAGON, () -> EntityRegistrar.PLAGUE_ABOMINATION_UNIT.get(), "entity.reignofnether.plague_abomination_unit"));
    public static final com.solegendary.reignofnether.building.production.SecondT3Prod HORDE_T3B = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "horde_t3b"), new com.solegendary.reignofnether.building.production.SecondT3Prod(com.solegendary.reignofnether.unit.units.piglins.MagmaHurlerUnit.ITEM_NAME, "magma_cube", com.solegendary.reignofnether.resources.ResourceCosts.HORDE_T3B, WAR_MAMMOTH, () -> EntityRegistrar.MAGMA_HURLER_UNIT.get(), "entity.reignofnether.magma_hurler_unit"));
    public static final com.solegendary.reignofnether.building.production.SecondT3Prod VERDANT_T3B = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "verdant_t3b"), new com.solegendary.reignofnether.building.production.SecondT3Prod(com.solegendary.reignofnether.unit.units.verdant.ThornwingRocUnit.ITEM_NAME, "world_tree_walker", com.solegendary.reignofnether.resources.ResourceCosts.VERDANT_T3B, WORLD_TREE_WALKER, () -> EntityRegistrar.THORNWING_ROC_UNIT.get(), "entity.reignofnether.thornwing_roc_unit"));
    public static final com.solegendary.reignofnether.building.production.SecondT3Prod TIDE_T3B = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tide_t3b"), new com.solegendary.reignofnether.building.production.SecondT3Prod(com.solegendary.reignofnether.unit.units.tide.AbyssalHydraUnit.ITEM_NAME, "leviathan", com.solegendary.reignofnether.resources.ResourceCosts.TIDE_T3B, LEVIATHAN, () -> EntityRegistrar.ABYSSAL_HYDRA_UNIT.get(), "entity.reignofnether.abyssal_hydra_unit"));
    public static final com.solegendary.reignofnether.building.production.SecondT3Prod STORM_T3B = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storm_t3b"), new com.solegendary.reignofnether.building.production.SecondT3Prod(com.solegendary.reignofnether.unit.units.storm.JadeSentinelUnit.ITEM_NAME, "earthshaker", com.solegendary.reignofnether.resources.ResourceCosts.STORM_T3B, CELESTIAL_DRAGON, () -> EntityRegistrar.JADE_SENTINEL_UNIT.get(), "entity.reignofnether.jade_sentinel_unit"));
    public static final com.solegendary.reignofnether.building.production.SecondT3Prod BRASS_T3B = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "brass_t3b"), new com.solegendary.reignofnether.building.production.SecondT3Prod(com.solegendary.reignofnether.unit.units.brass.BrassZeppelinUnit.ITEM_NAME, "iron_leviathan_engine", com.solegendary.reignofnether.resources.ResourceCosts.BRASS_T3B, IRON_LEVIATHAN_ENGINE, () -> EntityRegistrar.BRASS_ZEPPELIN_UNIT.get(), "entity.reignofnether.brass_zeppelin_unit"));

    private static <T extends ProductionItem> T register(ResourceLocation id, T building) {
        return Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM, id, building);
    }

    public static void init() {}

    private static final List<ProductionItem> ALL = List.of(
        CREEPER,
        WRAITH,
        SKELETON,
        BOGGED,
        ZOMBIE,
        STRAY,
        HUSK,
        DROWNED,
        SPIDER,
        POISON_SPIDER,
        VILLAGER,
        MILITIA,
        SCOUT_DOG,
        SCOUT_CAT,
        ZOMBIE_VILLAGER,
        BAT,
        VINDICATOR,
        PILLAGER,
        IRON_GOLEM,
        WITCH,
        EVOKER,
        WINDCALLER,
        SLIME,
        WARDEN,
        RAVAGER,
        ROYAL_ARCHITECT,
        EMBALMER,
        BONEWRIGHT,
        ELDER_DRUID,
        STAG_LANCER,
        SHADE_RANGER,
        ELDER_TREANT,
        WISP_CHOIR,
        BLOOM_PRIESTESS,
        WAR_MAMMOTH,
        SUN_COLOSSUS,
        WORLD_TREE_WALKER,
        SEEDSHAPER,
        FOX_COURIER,
        MOONWELL_BEARER,
        OWL_WATCHER,
        HIVE_KEEPER,
        LEAFBLADE,
        THORNBOW,
        SENTINEL_TREANT,
        SHIPWRIGHT,
        GULL_SPOTTER,
        CUTLASS_RAIDER,
        REEF_GUARD,
        BOMBARD_CREW,
        TIDE_PRIEST,
        HARPOONER,
        FLAK_SWIVEL,
        MUDSKIPPER_SLOOP,
        HARBOURMASTER,
        KRAKEN_CALLER,
        MORTAR_GALLEY,
        SKY_CORSAIR,
        LEVIATHAN,
        GEOMANCER,
        PAPER_CRANE,
        BLADE_DANCER,
        FIRE_ADEPT,
        JADE_GUARD,
        WIND_MONK,
        INK_SCRIBE,
        FROST_ADEPT,
        ROCKET_BATTERY,
        LEY_MASTER,
        THUNDER_DRAGON,
        EARTHSHAKER,
        GRAVITY_MONK,
        MIRROR_MAGE,
        SEER,
        LANTERN_FORTRESS,
        PHOENIX,
        CELESTIAL_DRAGON,
        TINKER,
        GYRO_SCOUT,
        STEAM_RUNNER,
        RIFLE_SQUAD,
        IRON_WARDEN,
        FLAK_CART,
        MORTAR_CRAWLER,
        SHIELD_PROJECTOR,
        SAPPER,
        BONE_DRAGON,
        GRUNT,
        STRIDER,
        BRUTE,
        HEADHUNTER,
        MARAUDER,
        HOGLIN,
        BLAZE,
        WITHER_SKELETON,
        MAGMA_CUBE,
        GHAST,
        ROYAL_GUARD,
        ROYAL_GUARD_REVIVE,
        ENCHANTER,
        ENCHANTER_REVIVE,
        NECROMANCER,
        NECROMANCER_REVIVE,
        WRETCHED_WRAITH,
        WRETCHED_WRAITH_REVIVE,
        PIGLIN_MERCHANT,
        PIGLIN_MERCHANT_REVIVE,
        WILDFIRE,
        WILDFIRE_REVIVE,
        ZOMBIE_PIGLIN,
        ZOGLIN,
        ENDERMAN,
        POLAR_BEAR,
        GRIZZLY_BEAR,
        PANDA,
        WOLF,
        LLAMA,
        ENGINEER_CAPTAIN,
        STEAM_JUGGERNAUT,
        TESLA_WALKER,
        GYROCOPTER_GUNSHIP,
        DRILL_BURROWER,
        REPAIR_CRAWLER,
        CLOCKWORK_SPIDER,
        DAWN_PALADIN,
        DAWN_PALADIN_REVIVE,
        GRAVECALLER,
        GRAVECALLER_REVIVE,
        HORDE_WARCHIEF,
        HORDE_WARCHIEF_REVIVE,
        ASH_SHAMAN,
        ASH_SHAMAN_REVIVE,
        THORNHEART_DRUID,
        THORNHEART_DRUID_REVIVE,
        CORSAIR_CAPTAIN,
        CORSAIR_CAPTAIN_REVIVE,
        STORM_WITCH,
        STORM_WITCH_REVIVE,
        ARCH_ENGINEER,
        ARCH_ENGINEER_REVIVE,
        IRON_LEVIATHAN_ENGINE,
        ORIFLAMME_ELEPHANT,
        GOREHORN_RHINO,
        OSSUARY_BEHEMOTH,
        MOSSBACK_BISON,
        SHELLBACK_TURTLE,
        JADE_QILIN,
        STEAM_HAULER,
        FIELD_MASON,
        BONESETTER,
        WRECKER,
        ROOTWARDEN,
        SALVAGER,
        JADE_MENDER,
        PIONEER
    );

    @Nullable
    public static ProductionItem getProductionItem(EntityType<? extends Mob> entityType) {
        for (ProductionItem prodItem : ALL) {
            String itemName = prodItem.getItemName();
            if (itemName != null && EntityRegistrar.getEntityType(prodItem.getItemName()) == entityType)
                return prodItem;
        }
        return null;
    }
}
