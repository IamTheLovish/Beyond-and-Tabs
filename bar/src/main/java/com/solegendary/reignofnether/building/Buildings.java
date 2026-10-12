package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.monsters.*;
import com.solegendary.reignofnether.building.buildings.neutral.*;
import com.solegendary.reignofnether.building.buildings.piglins.*;
import com.solegendary.reignofnether.building.buildings.villagers.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class Buildings {
    public static final Mausoleum MAUSOLEUM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mausoleum"), new Mausoleum());
    public static final com.solegendary.reignofnether.building.buildings.shared.MetalExtractor METAL_EXTRACTOR_VILLAGERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "metal_extractor_villagers"), new com.solegendary.reignofnether.building.buildings.shared.MetalExtractor());
    public static final com.solegendary.reignofnether.building.buildings.shared.MetalExtractor METAL_EXTRACTOR_MONSTERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "metal_extractor_monsters"), new com.solegendary.reignofnether.building.buildings.shared.MetalExtractor("_dark"));
    public static final com.solegendary.reignofnether.building.buildings.shared.MetalExtractor METAL_EXTRACTOR_PIGLINS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "metal_extractor_piglins"), new com.solegendary.reignofnether.building.buildings.shared.MetalExtractor("_nether"));
    public static final com.solegendary.reignofnether.building.buildings.shared.WindGenerator WIND_GENERATOR_VILLAGERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_generator_villagers"), new com.solegendary.reignofnether.building.buildings.shared.WindGenerator());
    public static final com.solegendary.reignofnether.building.buildings.shared.WindGenerator WIND_GENERATOR_MONSTERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_generator_monsters"), new com.solegendary.reignofnether.building.buildings.shared.WindGenerator("_dark"));
    public static final com.solegendary.reignofnether.building.buildings.shared.WindGenerator WIND_GENERATOR_PIGLINS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_generator_piglins"), new com.solegendary.reignofnether.building.buildings.shared.WindGenerator("_nether"));
    public static final com.solegendary.reignofnether.building.buildings.shared.EnergyConverter ENERGY_CONVERTER_VILLAGERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "energy_converter_villagers"), new com.solegendary.reignofnether.building.buildings.shared.EnergyConverter());
    public static final com.solegendary.reignofnether.building.buildings.shared.EnergyConverter ENERGY_CONVERTER_MONSTERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "energy_converter_monsters"), new com.solegendary.reignofnether.building.buildings.shared.EnergyConverter("_dark"));
    public static final com.solegendary.reignofnether.building.buildings.shared.EnergyConverter ENERGY_CONVERTER_PIGLINS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "energy_converter_piglins"), new com.solegendary.reignofnether.building.buildings.shared.EnergyConverter("_nether"));
    public static final SpruceStockpile SPRUCE_STOCKPILE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spruce_stockpile"), new SpruceStockpile());
    public static final HauntedHouse HAUNTED_HOUSE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "haunted_house"), new HauntedHouse());
    public static final PumpkinFarm PUMPKIN_FARM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "pumpkin_farm"), new PumpkinFarm());
    public static final DarkWatchtower DARK_WATCHTOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "dark_watchtower"), new DarkWatchtower());
    public static final Graveyard GRAVEYARD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "graveyard"), new Graveyard());
    public static final Dungeon DUNGEON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "dungeon"), new Dungeon());
    public static final SpiderLair SPIDER_LAIR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spider_lair"), new SpiderLair());
    public static final SlimePit SLIME_PIT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "slime_pit"), new SlimePit());
    public static final Laboratory LABORATORY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "laboratory"), new Laboratory());
    public static final Stronghold STRONGHOLD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "stronghold"), new Stronghold());
    public static final SpruceBridge SPRUCE_BRIDGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spruce_bridge"), new SpruceBridge());
    public static final SculkCatalyst SCULK_CATALYST = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sculk_catalyst"), new SculkCatalyst());
    public static final CentralPortal CENTRAL_PORTAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "central_portal"), new CentralPortal());
    public static final PortalBasic PORTAL_BASIC = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_basic"), new PortalBasic());
    public static final PortalPocket PORTAL_POCKET = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_pocket"), new PortalPocket());
    public static final PortalCivilian PORTAL_CIVILIAN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_civilian"), new PortalCivilian());
    public static final PortalMilitary PORTAL_MILITARY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_military"), new PortalMilitary());
    public static final PortalTransport PORTAL_TRANSPORT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_transport"), new PortalTransport());
    public static final NetherwartFarm NETHERWART_FARM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "netherwart_farm"), new NetherwartFarm());
    public static final Bastion BASTION = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bastion"), new Bastion());
    public static final HoglinStables HOGLIN_STABLES = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hoglin_stables"), new HoglinStables());
    public static final FlameSanctuary FLAME_SANCTUARY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "flame_sanctuary"), new FlameSanctuary());
    public static final WitherShrine WITHER_SHRINE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wither_shrine"), new WitherShrine());
    public static final BasaltSprings BASALT_SPRINGS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "basalt_springs"), new BasaltSprings());
    public static final Fortress FORTRESS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "fortress"), new Fortress());
    public static final BlackstoneBridge BLACKSTONE_BRIDGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blackstone_bridge"), new BlackstoneBridge());
    public static final TownCentre TOWN_CENTRE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "town_centre"), new TownCentre());
    public static final OakStockpile OAK_STOCKPILE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "oak_stockpile"), new OakStockpile());
    public static final VillagerHouse VILLAGER_HOUSE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "villager_house"), new VillagerHouse());
    public static final WheatFarm WHEAT_FARM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wheat_farm"), new WheatFarm());
    public static final Watchtower WATCHTOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "watchtower"), new Watchtower());
    public static final Barracks BARRACKS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barracks"), new Barracks());
    public static final Blacksmith BLACKSMITH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blacksmith"), new Blacksmith());
    public static final WitchHut WITCH_HUT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "witch_hut"), new WitchHut());
    public static final ArcaneTower ARCANE_TOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "arcane_tower"), new ArcaneTower());
    public static final Library LIBRARY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "library"), new Library());
    public static final Castle CASTLE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "castle"), new Castle());
    public static final IronGolemBuilding IRON_GOLEM_BUILDING = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_golem_building"), new IronGolemBuilding());
    public static final OakBridge OAK_BRIDGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "oak_bridge"), new OakBridge());
    public static final Beacon BEACON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "beacon"), new Beacon());
    public static final CapturableBeacon CAPTURABLE_BEACON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "capturable_beacon"), new CapturableBeacon());
    public static final EndPortal END_PORTAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "end_portal"), new EndPortal());
    public static final HealingFountain HEALING_FOUNTAIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "healing_fountain"), new HealingFountain());
    public static final NeutralTransportPortal NEUTRAL_TRANSPORT_PORTAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "neutral_transport_portal"), new NeutralTransportPortal());
    public static final AltarOfDarkness ALTAR_OF_DARKNESS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "altar_of_darkness"), new AltarOfDarkness());
    public static final ShrineOfProsperity SHRINE_OF_PROSPERITY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shrine_of_prosperity"), new ShrineOfProsperity());
    public static final InfernalPortal INFERNAL_PORTAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "infernal_portal"), new InfernalPortal());
    public static final MonsterMarket MONSTER_MARKET = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "monster_market"), new MonsterMarket());
    public static final PiglinMarket PIGLIN_MARKET = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "piglin_market"), new PiglinMarket());
    public static final VillagerMarket VILLAGER_MARKET = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "villager_market"), new VillagerMarket());
    // Verdant Court (design/verdant_court_plan.md, slice 1); registered last so no existing building's id moves
    public static final com.solegendary.reignofnether.building.buildings.verdant.HeartwoodHall HEARTWOOD_HALL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "heartwood_hall"), new com.solegendary.reignofnether.building.buildings.verdant.HeartwoodHall());
    public static final com.solegendary.reignofnether.building.buildings.verdant.Grove GROVE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "grove"), new com.solegendary.reignofnether.building.buildings.verdant.Grove());
    public static final com.solegendary.reignofnether.building.buildings.shared.MetalExtractor METAL_EXTRACTOR_VERDANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "metal_extractor_verdant"), new com.solegendary.reignofnether.building.buildings.shared.MetalExtractor("_verdant"));
    public static final com.solegendary.reignofnether.building.buildings.shared.WindGenerator WIND_GENERATOR_VERDANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_generator_verdant"), new com.solegendary.reignofnether.building.buildings.shared.WindGenerator("_verdant"));
    public static final com.solegendary.reignofnether.building.buildings.shared.EnergyConverter ENERGY_CONVERTER_VERDANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "energy_converter_verdant"), new com.solegendary.reignofnether.building.buildings.shared.EnergyConverter("_verdant"));
    // Verdant Court T2 lab (slice 4)
    public static final com.solegendary.reignofnether.building.buildings.verdant.CircleOfElders CIRCLE_OF_ELDERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "circle_of_elders"), new com.solegendary.reignofnether.building.buildings.verdant.CircleOfElders());
    // Verdant Court T3 lab
    public static final com.solegendary.reignofnether.building.buildings.verdant.HeartOfTheWild HEART_OF_THE_WILD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "heart_of_the_wild"), new com.solegendary.reignofnether.building.buildings.verdant.HeartOfTheWild());
    // Verdant Court T2 defence (registered last: no existing building's id moves)
    public static final com.solegendary.reignofnether.building.buildings.verdant.StormOak STORM_OAK = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storm_oak"), new com.solegendary.reignofnether.building.buildings.verdant.StormOak());

    // Tidewrought (design/tidewrought_plan.md, slice 1); registered last so no existing building's id moves
    public static final com.solegendary.reignofnether.building.buildings.tide.WreckHarbour WRECK_HARBOUR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wreck_harbour"), new com.solegendary.reignofnether.building.buildings.tide.WreckHarbour());
    public static final com.solegendary.reignofnether.building.buildings.tide.Slipway SLIPWAY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "slipway"), new com.solegendary.reignofnether.building.buildings.tide.Slipway());
    public static final com.solegendary.reignofnether.building.buildings.shared.MetalExtractor METAL_EXTRACTOR_TIDE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "metal_extractor_tide"), new com.solegendary.reignofnether.building.buildings.shared.MetalExtractor("_tide"));
    public static final com.solegendary.reignofnether.building.buildings.shared.WindGenerator WIND_GENERATOR_TIDE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_generator_tide"), new com.solegendary.reignofnether.building.buildings.shared.WindGenerator("_tide"));
    public static final com.solegendary.reignofnether.building.buildings.shared.EnergyConverter ENERGY_CONVERTER_TIDE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "energy_converter_tide"), new com.solegendary.reignofnether.building.buildings.shared.EnergyConverter("_tide"));

    // BAR storage: one per faction, registered after everything else so no existing building's id moves
    public static final com.solegendary.reignofnether.building.buildings.shared.StorageVault STORAGE_VAULT_VILLAGERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storage_vault_villagers"), new com.solegendary.reignofnether.building.buildings.shared.StorageVault());
    public static final com.solegendary.reignofnether.building.buildings.shared.StorageVault STORAGE_VAULT_MONSTERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storage_vault_monsters"), new com.solegendary.reignofnether.building.buildings.shared.StorageVault("_dark"));
    public static final com.solegendary.reignofnether.building.buildings.shared.StorageVault STORAGE_VAULT_PIGLINS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storage_vault_piglins"), new com.solegendary.reignofnether.building.buildings.shared.StorageVault("_nether"));
    public static final com.solegendary.reignofnether.building.buildings.shared.StorageVault STORAGE_VAULT_VERDANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storage_vault_verdant"), new com.solegendary.reignofnether.building.buildings.shared.StorageVault("_verdant"));
    public static final com.solegendary.reignofnether.building.buildings.shared.StorageVault STORAGE_VAULT_TIDE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storage_vault_tide"), new com.solegendary.reignofnether.building.buildings.shared.StorageVault("_tide"));

    // Tidewrought T2 (design/tidewrought_plan.md, slice 3): the Drydock lab and the Lighthouse Battery defence,
    // registered last so no existing building's id moves
    public static final com.solegendary.reignofnether.building.buildings.tide.Drydock DRYDOCK = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "drydock"), new com.solegendary.reignofnether.building.buildings.tide.Drydock());
    public static final com.solegendary.reignofnether.building.buildings.tide.LighthouseBattery LIGHTHOUSE_BATTERY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lighthouse_battery"), new com.solegendary.reignofnether.building.buildings.tide.LighthouseBattery());
    // Tidewrought T3 lab (slice 4)
    public static final com.solegendary.reignofnether.building.buildings.tide.AbyssalShipyard ABYSSAL_SHIPYARD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "abyssal_shipyard"), new com.solegendary.reignofnether.building.buildings.tide.AbyssalShipyard());

    // Stormcallers (design/stormcallers_plan.md, slice 1): capitol, T1 lab, the "_storm" economy and the Mana Cistern
    // (an energy-heavy storage split), registered last so no existing building's id moves
    public static final com.solegendary.reignofnether.building.buildings.storm.JadePagoda JADE_PAGODA = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "jade_pagoda"), new com.solegendary.reignofnether.building.buildings.storm.JadePagoda());
    public static final com.solegendary.reignofnether.building.buildings.storm.LotusAcademy LOTUS_ACADEMY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lotus_academy"), new com.solegendary.reignofnether.building.buildings.storm.LotusAcademy());
    // Stormcallers T2 (design/stormcallers_plan.md, slice 3): the Celestial Observatory lab
    public static final com.solegendary.reignofnether.building.buildings.storm.CelestialObservatory CELESTIAL_OBSERVATORY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "celestial_observatory"), new com.solegendary.reignofnether.building.buildings.storm.CelestialObservatory());
    // Stormcallers T3 (slice 4): Heaven's Gate, registered after everything that came before so no id moves
    public static final com.solegendary.reignofnether.building.buildings.storm.HeavensGate HEAVENS_GATE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "heavens_gate"), new com.solegendary.reignofnether.building.buildings.storm.HeavensGate());
    public static final com.solegendary.reignofnether.building.buildings.shared.MetalExtractor METAL_EXTRACTOR_STORM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "metal_extractor_storm"), new com.solegendary.reignofnether.building.buildings.shared.MetalExtractor("_storm"));
    public static final com.solegendary.reignofnether.building.buildings.shared.WindGenerator WIND_GENERATOR_STORM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_generator_storm"), new com.solegendary.reignofnether.building.buildings.shared.WindGenerator("_storm"));
    public static final com.solegendary.reignofnether.building.buildings.shared.EnergyConverter ENERGY_CONVERTER_STORM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "energy_converter_storm"), new com.solegendary.reignofnether.building.buildings.shared.EnergyConverter("_storm"));
    public static final com.solegendary.reignofnether.building.buildings.shared.StorageVault STORAGE_VAULT_STORM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storage_vault_storm"), new com.solegendary.reignofnether.building.buildings.shared.StorageVault("_storm",
        com.solegendary.reignofnether.building.buildings.shared.StorageVault.STORM_METAL_STORAGE, com.solegendary.reignofnether.building.buildings.shared.StorageVault.STORM_ENERGY_STORAGE));

    // Brassworks Artificers (design/brassworks_plan.md, slice 1): capitol, T1 lab and the "_brass" economy (the Steam Boiler
    // is a WindGenerator variant with no rotor hub, the Strongbox the standard storage split), registered last so no
    // existing building's id moves
    public static final com.solegendary.reignofnether.building.buildings.brass.FoundryHall FOUNDRY_HALL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "foundry_hall"), new com.solegendary.reignofnether.building.buildings.brass.FoundryHall());
    public static final com.solegendary.reignofnether.building.buildings.brass.MachineShop MACHINE_SHOP = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "machine_shop"), new com.solegendary.reignofnether.building.buildings.brass.MachineShop());
    public static final com.solegendary.reignofnether.building.buildings.shared.MetalExtractor METAL_EXTRACTOR_BRASS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "metal_extractor_brass"), new com.solegendary.reignofnether.building.buildings.shared.MetalExtractor("_brass"));
    public static final com.solegendary.reignofnether.building.buildings.shared.WindGenerator WIND_GENERATOR_BRASS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_generator_brass"), new com.solegendary.reignofnether.building.buildings.shared.WindGenerator("_brass"));
    public static final com.solegendary.reignofnether.building.buildings.shared.EnergyConverter ENERGY_CONVERTER_BRASS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "energy_converter_brass"), new com.solegendary.reignofnether.building.buildings.shared.EnergyConverter("_brass"));
    public static final com.solegendary.reignofnether.building.buildings.shared.StorageVault STORAGE_VAULT_BRASS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storage_vault_brass"), new com.solegendary.reignofnether.building.buildings.shared.StorageVault("_brass"));

    // lab annexes (design/building_upgrades.md): one per faction, attached to its T1 army lab; registered last so no
    // existing building's id moves
    public static final com.solegendary.reignofnether.building.buildings.shared.LabAnnex LAB_ANNEX_VILLAGERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lab_annex_villagers"), new com.solegendary.reignofnether.building.buildings.shared.LabAnnex("", BARRACKS, net.minecraft.world.level.block.Blocks.SMITHING_TABLE, "textures/block/smithing_table_front.png"));
    public static final com.solegendary.reignofnether.building.buildings.shared.LabAnnex LAB_ANNEX_MONSTERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lab_annex_monsters"), new com.solegendary.reignofnether.building.buildings.shared.LabAnnex("_dark", GRAVEYARD, net.minecraft.world.level.block.Blocks.BONE_BLOCK, "textures/block/bone_block_side.png"));
    public static final com.solegendary.reignofnether.building.buildings.shared.LabAnnex LAB_ANNEX_PIGLINS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lab_annex_piglins"), new com.solegendary.reignofnether.building.buildings.shared.LabAnnex("_nether", PORTAL_MILITARY, net.minecraft.world.level.block.Blocks.BLAST_FURNACE, "textures/block/blast_furnace_front.png"));
    public static final com.solegendary.reignofnether.building.buildings.shared.LabAnnex LAB_ANNEX_VERDANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lab_annex_verdant"), new com.solegendary.reignofnether.building.buildings.shared.LabAnnex("_verdant", GROVE, net.minecraft.world.level.block.Blocks.FLOWERING_AZALEA, "textures/block/flowering_azalea_top.png"));
    public static final com.solegendary.reignofnether.building.buildings.shared.LabAnnex LAB_ANNEX_TIDE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lab_annex_tide"), new com.solegendary.reignofnether.building.buildings.shared.LabAnnex("_tide", SLIPWAY, net.minecraft.world.level.block.Blocks.LOOM, "textures/block/loom_front.png"));
    public static final com.solegendary.reignofnether.building.buildings.shared.LabAnnex LAB_ANNEX_STORM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lab_annex_storm"), new com.solegendary.reignofnether.building.buildings.shared.LabAnnex("_storm", LOTUS_ACADEMY, net.minecraft.world.level.block.Blocks.LANTERN, "textures/block/lantern.png"));
    public static final com.solegendary.reignofnether.building.buildings.shared.LabAnnex LAB_ANNEX_BRASS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lab_annex_brass"), new com.solegendary.reignofnether.building.buildings.shared.LabAnnex("_brass", MACHINE_SHOP, net.minecraft.world.level.block.Blocks.PISTON, "textures/block/piston_top.png"));
    // Brassworks slice 2: the T2 lab
    public static final com.solegendary.reignofnether.building.buildings.brass.AssemblyWorks ASSEMBLY_WORKS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "assembly_works"), new com.solegendary.reignofnether.building.buildings.brass.AssemblyWorks());
    // Brassworks slice 3: the T2 defence
    public static final com.solegendary.reignofnether.building.buildings.brass.RailBattery RAIL_BATTERY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rail_battery"), new com.solegendary.reignofnether.building.buildings.brass.RailBattery());

    // the Geothermal Plant (design/lively_buildings.md): one shared structure, one instance per faction (a building
    // has one faction); T2 constructors only, placed over lava. Registered last so no existing building's id moves
    public static final com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant GEOTHERMAL_PLANT_VILLAGERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geothermal_plant_villagers"), new com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant());
    public static final com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant GEOTHERMAL_PLANT_MONSTERS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geothermal_plant_monsters"), new com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant());
    public static final com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant GEOTHERMAL_PLANT_PIGLINS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geothermal_plant_piglins"), new com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant());
    public static final com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant GEOTHERMAL_PLANT_VERDANT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geothermal_plant_verdant"), new com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant());
    public static final com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant GEOTHERMAL_PLANT_TIDE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geothermal_plant_tide"), new com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant());
    public static final com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant GEOTHERMAL_PLANT_STORM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geothermal_plant_storm"), new com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant());
    public static final com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant GEOTHERMAL_PLANT_BRASS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geothermal_plant_brass"), new com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant());
    // Brassworks slice 4: the T3 lab (appended last so no existing building's id moves)
    public static final com.solegendary.reignofnether.building.buildings.brass.GreatForge GREAT_FORGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "great_forge"), new com.solegendary.reignofnether.building.buildings.brass.GreatForge());

    // the static defences (design/defences.md): walls, gates, traps, anti-raid towers and anti-T3 emplacements for all
    // seven factions, registered by Defences' own class init. A static block after every field above, so no existing
    // building's registry id moves
    static {
        com.solegendary.reignofnether.building.buildings.defence.Defences.init();
    }

    private static <T extends Building> T register(ResourceLocation id, T building) {
        return Registry.register(ReignOfNetherRegistries.BUILDING, id, building);
    }

    public static void init() {}
}
