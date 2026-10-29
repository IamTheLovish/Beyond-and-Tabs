package com.solegendary.reignofnether;

import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.ItemRegistrar;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.units.monsters.*;
import com.solegendary.reignofnether.unit.units.neutral.*;
import com.solegendary.reignofnether.unit.units.piglins.*;
import com.solegendary.reignofnether.unit.units.villagers.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Bus.MOD)
public class CommonModEvents {

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::init);
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent evt) {
        evt.put(EntityRegistrar.POLAR_BEAR_UNIT.get(), PolarBearUnit.createAttributes().build());
        evt.put(EntityRegistrar.GRIZZLY_BEAR_UNIT.get(), GrizzlyBearUnit.createAttributes().build());
        evt.put(EntityRegistrar.PANDA_UNIT.get(), PandaUnit.createAttributes().build());
        evt.put(EntityRegistrar.WOLF_UNIT.get(), WolfUnit.createAttributes().build());
        evt.put(EntityRegistrar.LLAMA_UNIT.get(), LlamaUnit.createAttributes().build());
        evt.put(EntityRegistrar.BEE_UNIT.get(), BeeUnit.createAttributes().build());
        evt.put(EntityRegistrar.PHANTOM_SUMMON.get(), PhantomSummon.createAttributes().build());
        evt.put(EntityRegistrar.ZOMBIE_UNIT.get(), ZombieUnit.createAttributes().build());
        evt.put(EntityRegistrar.ZOMBIE_PIGLIN_UNIT.get(), ZombiePiglinUnit.createAttributes().build());
        evt.put(EntityRegistrar.ZOGLIN_UNIT.get(), ZoglinUnit.createAttributes().build());
        evt.put(EntityRegistrar.SKELETON_UNIT.get(), SkeletonUnit.createAttributes().build());
        evt.put(EntityRegistrar.HUSK_UNIT.get(), HuskUnit.createAttributes().build());
        evt.put(EntityRegistrar.DROWNED_UNIT.get(), DrownedUnit.createAttributes().build());
        evt.put(EntityRegistrar.STRAY_UNIT.get(), StrayUnit.createAttributes().build());
        evt.put(EntityRegistrar.BOGGED_UNIT.get(), BoggedUnit.createAttributes().build());
        evt.put(EntityRegistrar.CREEPER_UNIT.get(), CreeperUnit.createAttributes().build());
        evt.put(EntityRegistrar.SPIDER_UNIT.get(), SpiderUnit.createAttributes().build());
        evt.put(EntityRegistrar.POISON_SPIDER_UNIT.get(), SpiderUnit.createAttributes().build());
        evt.put(EntityRegistrar.WRAITH_UNIT.get(), WraithUnit.createAttributes().build());
        evt.put(EntityRegistrar.TOMBCALLER_UNIT.get(), com.solegendary.reignofnether.unit.units.monsters.TombcallerUnit.createAttributes().build());
        evt.put(EntityRegistrar.TOMBSTONE_UNIT.get(), com.solegendary.reignofnether.unit.units.monsters.TombstoneUnit.createAttributes().build());
        evt.put(EntityRegistrar.VILLAGER_UNIT.get(), VillagerUnit.createAttributes().build());
        evt.put(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit.createAttributes().build());
        // Verdant Court T2
        evt.put(EntityRegistrar.ELDER_DRUID_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.ElderDruidUnit.createAttributes().build());
        evt.put(EntityRegistrar.STAG_LANCER_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.StagLancerUnit.createAttributes().build());
        evt.put(EntityRegistrar.SHADE_RANGER_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit.createAttributes().build());
        evt.put(EntityRegistrar.ELDER_TREANT_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.ElderTreantUnit.createAttributes().build());
        evt.put(EntityRegistrar.WISP_CHOIR_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit.createAttributes().build());
        evt.put(EntityRegistrar.BLOOM_PRIESTESS_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.BloomPriestessUnit.createAttributes().build());
        evt.put(EntityRegistrar.SCOUT_DOG_UNIT.get(), ScoutDogUnit.createAttributes().build());
        evt.put(EntityRegistrar.SCOUT_CAT_UNIT.get(), ScoutCatUnit.createAttributes().build());
        evt.put(EntityRegistrar.MILITIA_UNIT.get(), MilitiaUnit.createAttributes().build());
        evt.put(EntityRegistrar.TEMPORARY_MILITIA_UNIT.get(), MilitiaUnit.createAttributes().build());
        evt.put(EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(), ZombieVillagerUnit.createAttributes().build());
        evt.put(EntityRegistrar.EMBALMER_UNIT.get(), com.solegendary.reignofnether.unit.units.monsters.EmbalmerUnit.createAttributes().build());
        evt.put(EntityRegistrar.BAT_UNIT.get(), BatUnit.createAttributes().build());
        evt.put(EntityRegistrar.PILLAGER_UNIT.get(), PillagerUnit.createAttributes().build());
        evt.put(EntityRegistrar.VINDICATOR_UNIT.get(), VindicatorUnit.createAttributes().build());
        evt.put(EntityRegistrar.WINDCALLER_UNIT.get(), WindcallerUnit.createAttributes().build());
        evt.put(EntityRegistrar.IRON_GOLEM_UNIT.get(), IronGolemUnit.createAttributes().build());
        evt.put(EntityRegistrar.SUN_COLOSSUS_UNIT.get(), com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit.createAttributes().build());
        evt.put(EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.createAttributes().build());
        // Verdant Court
        evt.put(EntityRegistrar.SEEDSHAPER_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.SeedshaperUnit.createAttributes().build());
        // Tidewrought
        evt.put(EntityRegistrar.SHIPWRIGHT_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.ShipwrightUnit.createAttributes().build());
        evt.put(EntityRegistrar.GULL_SPOTTER_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.GullSpotterUnit.createAttributes().build());
        evt.put(EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.CutlassRaiderUnit.createAttributes().build());
        evt.put(EntityRegistrar.REEF_GUARD_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.ReefGuardUnit.createAttributes().build());
        evt.put(EntityRegistrar.BOMBARD_CREW_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit.createAttributes().build());
        evt.put(EntityRegistrar.SUNFIRE_MANGONEL_UNIT.get(), com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit.createAttributes().build());
        evt.put(EntityRegistrar.SKULL_HURLER_UNIT.get(), com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit.createAttributes().build());
        evt.put(EntityRegistrar.BOULDER_CHUCKER_UNIT.get(), com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit.createAttributes().build());
        evt.put(EntityRegistrar.BURL_SLINGER_UNIT.get(), com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit.createAttributes().build());
        evt.put(EntityRegistrar.POWDER_MONKEY_UNIT.get(), com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit.createAttributes().build());
        evt.put(EntityRegistrar.THUNDERSTONE_MONK_UNIT.get(), com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit.createAttributes().build());
        evt.put(EntityRegistrar.STEAM_LOBBER_UNIT.get(), com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit.createAttributes().build());
        evt.put(EntityRegistrar.TIDE_PRIEST_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.TidePriestUnit.createAttributes().build());
        evt.put(EntityRegistrar.HARPOONER_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.HarpoonerUnit.createAttributes().build());
        evt.put(EntityRegistrar.FLAK_SWIVEL_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit.createAttributes().build());
        evt.put(EntityRegistrar.MUDSKIPPER_SLOOP_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.MudskipperSloopUnit.createAttributes().build());
        evt.put(EntityRegistrar.HARBOURMASTER_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.HarbourmasterUnit.createAttributes().build());
        evt.put(EntityRegistrar.KRAKEN_CALLER_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.KrakenCallerUnit.createAttributes().build());
        evt.put(EntityRegistrar.MORTAR_GALLEY_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit.createAttributes().build());
        evt.put(EntityRegistrar.SKY_CORSAIR_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit.createAttributes().build());
        evt.put(EntityRegistrar.LEVIATHAN_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.createAttributes().build());
        // Stormcallers
        evt.put(EntityRegistrar.GEOMANCER_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.GeomancerUnit.createAttributes().build());
        evt.put(EntityRegistrar.PAPER_CRANE_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.PaperCraneUnit.createAttributes().build());
        evt.put(EntityRegistrar.BLADE_DANCER_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.BladeDancerUnit.createAttributes().build());
        evt.put(EntityRegistrar.FIRE_ADEPT_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit.createAttributes().build());
        evt.put(EntityRegistrar.JADE_GUARD_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.JadeGuardUnit.createAttributes().build());
        evt.put(EntityRegistrar.WIND_MONK_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.WindMonkUnit.createAttributes().build());
        evt.put(EntityRegistrar.INK_SCRIBE_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.InkScribeUnit.createAttributes().build());
        evt.put(EntityRegistrar.FROST_ADEPT_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.FrostAdeptUnit.createAttributes().build());
        evt.put(EntityRegistrar.ROCKET_BATTERY_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit.createAttributes().build());
        evt.put(EntityRegistrar.LEY_MASTER_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.LeyMasterUnit.createAttributes().build());
        evt.put(EntityRegistrar.THUNDER_DRAGON_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.ThunderDragonUnit.createAttributes().build());
        evt.put(EntityRegistrar.EARTHSHAKER_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.EarthshakerUnit.createAttributes().build());
        evt.put(EntityRegistrar.GRAVITY_MONK_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.GravityMonkUnit.createAttributes().build());
        evt.put(EntityRegistrar.MIRROR_MAGE_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.MirrorMageUnit.createAttributes().build());
        evt.put(EntityRegistrar.SEER_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.SeerUnit.createAttributes().build());
        evt.put(EntityRegistrar.LANTERN_FORTRESS_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.LanternFortressUnit.createAttributes().build());
        evt.put(EntityRegistrar.PHOENIX_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.PhoenixUnit.createAttributes().build());
        evt.put(EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.createAttributes().build());
        // Brassworks
        evt.put(EntityRegistrar.TINKER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.TinkerUnit.createAttributes().build());
        evt.put(EntityRegistrar.GYRO_SCOUT_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.GyroScoutUnit.createAttributes().build());
        evt.put(EntityRegistrar.STEAM_RUNNER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.SteamRunnerUnit.createAttributes().build());
        evt.put(EntityRegistrar.RIFLE_SQUAD_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.RifleSquadUnit.createAttributes().build());
        evt.put(EntityRegistrar.IRON_WARDEN_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.IronWardenUnit.createAttributes().build());
        evt.put(EntityRegistrar.FLAK_CART_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.FlakCartUnit.createAttributes().build());
        evt.put(EntityRegistrar.MORTAR_CRAWLER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.MortarCrawlerUnit.createAttributes().build());
        evt.put(EntityRegistrar.SHIELD_PROJECTOR_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.ShieldProjectorUnit.createAttributes().build());
        evt.put(EntityRegistrar.SAPPER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.SapperUnit.createAttributes().build());
        evt.put(EntityRegistrar.DEPLOYED_TURRET_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit.createAttributes().build());
        evt.put(EntityRegistrar.FOX_COURIER_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.FoxCourierUnit.createAttributes().build());
        evt.put(EntityRegistrar.MOONWELL_BEARER_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.MoonwellBearerUnit.createAttributes().build());
        evt.put(EntityRegistrar.OWL_WATCHER_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.OwlWatcherUnit.createAttributes().build());
        evt.put(EntityRegistrar.HIVE_KEEPER_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.HiveKeeperUnit.createAttributes().build());
        evt.put(EntityRegistrar.LEAFBLADE_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.LeafbladeUnit.createAttributes().build());
        evt.put(EntityRegistrar.THORNBOW_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.ThornbowUnit.createAttributes().build());
        evt.put(EntityRegistrar.SENTINEL_TREANT_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.SentinelTreantUnit.createAttributes().build());
        evt.put(EntityRegistrar.WITCH_UNIT.get(), WitchUnit.createAttributes().build());
        evt.put(EntityRegistrar.EVOKER_UNIT.get(), EvokerUnit.createAttributes().build());
        evt.put(EntityRegistrar.ENDERMAN_UNIT.get(), EndermanUnit.createAttributes().build());
        evt.put(EntityRegistrar.WARDEN_UNIT.get(), WardenUnit.createAttributes().build());
        evt.put(EntityRegistrar.RAVAGER_UNIT.get(), RavagerUnit.createAttributes().build());
        evt.put(EntityRegistrar.WAR_MAMMOTH_UNIT.get(), com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.createAttributes().build());
        evt.put(EntityRegistrar.SILVERFISH_UNIT.get(), SilverfishUnit.createAttributes().build());
        evt.put(EntityRegistrar.GRUNT_UNIT.get(), GruntUnit.createAttributes().build());
        evt.put(EntityRegistrar.BONEWRIGHT_UNIT.get(), com.solegendary.reignofnether.unit.units.piglins.BonewrightUnit.createAttributes().build());
        evt.put(EntityRegistrar.STRIDER_UNIT.get(), StriderUnit.createAttributes().build());
        evt.put(EntityRegistrar.HEADHUNTER_UNIT.get(), HeadhunterUnit.createAttributes().build());
        evt.put(EntityRegistrar.MARAUDER_UNIT.get(), MarauderUnit.createAttributes().build());
        evt.put(EntityRegistrar.BRUTE_UNIT.get(), BruteUnit.createAttributes().build());
        evt.put(EntityRegistrar.HOGLIN_UNIT.get(), HoglinUnit.createAttributes().build());
        evt.put(EntityRegistrar.ARMOURED_HOGLIN_UNIT.get(), ArmouredHoglinUnit.createAttributes().build());
        evt.put(EntityRegistrar.BLAZE_UNIT.get(), BlazeUnit.createAttributes().build());
        evt.put(EntityRegistrar.WITHER_SKELETON_UNIT.get(), WitherSkeletonUnit.createAttributes().build());
        evt.put(EntityRegistrar.GHAST_UNIT.get(), GhastUnit.createAttributes().build());
        evt.put(EntityRegistrar.BONE_DRAGON_UNIT.get(), com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.createAttributes().build());
        evt.put(EntityRegistrar.MAGMA_CUBE_UNIT.get(), SlimeUnit.createAttributes().build());
        evt.put(EntityRegistrar.SLIME_UNIT.get(), SlimeUnit.createAttributes().build());
        evt.put(EntityRegistrar.ROYAL_GUARD_UNIT.get(), RoyalGuardUnit.createAttributes().build());
        evt.put(EntityRegistrar.ENCHANTER_UNIT.get(), EnchanterUnit.createAttributes().build());
        evt.put(EntityRegistrar.NECROMANCER_UNIT.get(), NecromancerUnit.createAttributes().build());
        evt.put(EntityRegistrar.WRETCHED_WRAITH_UNIT.get(), WretchedWraithUnit.createAttributes().build());
        evt.put(EntityRegistrar.PIGLIN_MERCHANT_UNIT.get(), PiglinMerchantUnit.createAttributes().build());
        evt.put(EntityRegistrar.WILDFIRE_UNIT.get(), WildfireUnit.createAttributes().build());
        evt.put(EntityRegistrar.KILLER_RABBIT_UNIT.get(), KillerRabbitUnit.createAttributes().build());
        evt.put(EntityRegistrar.TOTEM_OF_REGENERATION.get(), AbstractTotem.createAttributes().build());
        evt.put(EntityRegistrar.TOTEM_OF_CASTING.get(), AbstractTotem.createAttributes().build());
        evt.put(EntityRegistrar.TOTEM_OF_PROTECTION.get(), AbstractTotem.createAttributes().build());
        evt.put(EntityRegistrar.TOTEM_OF_SHIELDING.get(), AbstractTotem.createAttributes().build());
        evt.put(EntityRegistrar.ENGINEER_CAPTAIN_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.EngineerCaptainUnit.createAttributes().build());
        evt.put(EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.SteamJuggernautUnit.createAttributes().build());
        evt.put(EntityRegistrar.TESLA_WALKER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.TeslaWalkerUnit.createAttributes().build());
        evt.put(EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.GyrocopterGunshipUnit.createAttributes().build());
        evt.put(EntityRegistrar.DRILL_BURROWER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit.createAttributes().build());
        evt.put(EntityRegistrar.REPAIR_CRAWLER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.RepairCrawlerUnit.createAttributes().build());
        evt.put(EntityRegistrar.CLOCKWORK_SPIDER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.ClockworkSpiderUnit.createAttributes().build());
        // hero slice 2: the seven faction heroes (their body's attributes plus the hero set and the kit's stats)
        evt.put(EntityRegistrar.DAWN_PALADIN_UNIT.get(), com.solegendary.reignofnether.unit.units.heroes.DawnPaladinUnit.createAttributes().build());
        evt.put(EntityRegistrar.GRAVECALLER_UNIT.get(), com.solegendary.reignofnether.unit.units.heroes.GravecallerUnit.createAttributes().build());
        evt.put(EntityRegistrar.HORDE_WARCHIEF_UNIT.get(), com.solegendary.reignofnether.unit.units.heroes.HordeWarchiefUnit.createAttributes().build());
        evt.put(EntityRegistrar.ASH_SHAMAN_UNIT.get(), com.solegendary.reignofnether.unit.units.heroes.AshShamanUnit.createAttributes().build());
        evt.put(EntityRegistrar.THORNHEART_DRUID_UNIT.get(), com.solegendary.reignofnether.unit.units.heroes.ThornheartDruidUnit.createAttributes().build());
        evt.put(EntityRegistrar.CORSAIR_CAPTAIN_UNIT.get(), com.solegendary.reignofnether.unit.units.heroes.CorsairCaptainUnit.createAttributes().build());
        evt.put(EntityRegistrar.STORM_WITCH_UNIT.get(), com.solegendary.reignofnether.unit.units.heroes.StormWitchUnit.createAttributes().build());
        evt.put(EntityRegistrar.ARCH_ENGINEER_UNIT.get(), com.solegendary.reignofnether.unit.units.heroes.ArchEngineerUnit.createAttributes().build());
        evt.put(EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.createAttributes().build());
        evt.put(EntityRegistrar.CLOCKWORK_DRONE_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit.createDroneAttributes().build());
        // big-units: the T2 beasts
        evt.put(EntityRegistrar.ORIFLAMME_ELEPHANT_UNIT.get(), com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantUnit.createAttributes().build());
        evt.put(EntityRegistrar.GOREHORN_RHINO_UNIT.get(), com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit.createAttributes().build());
        evt.put(EntityRegistrar.OSSUARY_BEHEMOTH_UNIT.get(), com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit.createAttributes().build());
        evt.put(EntityRegistrar.MOSSBACK_BISON_UNIT.get(), com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit.createAttributes().build());
        // beasts-2: the other three T2 beasts
        evt.put(EntityRegistrar.SHELLBACK_TURTLE_UNIT.get(), com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit.createAttributes().build());
        evt.put(EntityRegistrar.JADE_QILIN_UNIT.get(), com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit.createAttributes().build());
        evt.put(EntityRegistrar.STEAM_HAULER_UNIT.get(), com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit.createAttributes().build());
        // combat engineers (design/combat_engineers.md)
        evt.put(EntityRegistrar.FIELD_MASON_UNIT.get(), com.solegendary.reignofnether.unit.units.engineers.FieldMasonUnit.createAttributes().build());
        evt.put(EntityRegistrar.BONESETTER_UNIT.get(), com.solegendary.reignofnether.unit.units.engineers.BonesetterUnit.createAttributes().build());
        evt.put(EntityRegistrar.WRECKER_UNIT.get(), com.solegendary.reignofnether.unit.units.engineers.WreckerUnit.createAttributes().build());
        evt.put(EntityRegistrar.ROOTWARDEN_UNIT.get(), com.solegendary.reignofnether.unit.units.engineers.RootwardenUnit.createAttributes().build());
        evt.put(EntityRegistrar.SALVAGER_UNIT.get(), com.solegendary.reignofnether.unit.units.engineers.SalvagerUnit.createAttributes().build());
        evt.put(EntityRegistrar.JADE_MENDER_UNIT.get(), com.solegendary.reignofnether.unit.units.engineers.JadeMenderUnit.createAttributes().build());
        evt.put(EntityRegistrar.PIONEER_UNIT.get(), com.solegendary.reignofnether.unit.units.engineers.PioneerUnit.createAttributes().build());
    }

    @SubscribeEvent
    public static void creativeTabSetup(BuildCreativeModeTabContentsEvent event) {
        if(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(event.getTab())==CreativeModeTabs.BUILDING_BLOCKS.location()){
            for(Item item : BlockRegistrar.blockItems.get(CreativeModeTabs.BUILDING_BLOCKS)){
                event.accept(item);
            }
        }
        if(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(event.getTab())==CreativeModeTabs.FUNCTIONAL_BLOCKS.location()){
            for(Item item : BlockRegistrar.blockItems.get(CreativeModeTabs.FUNCTIONAL_BLOCKS)){
                event.accept(item);
            }
        }
        if(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(event.getTab())==CreativeModeTabs.SPAWN_EGGS.location()){
            event.accept(ItemRegistrar.ZOMBIE_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.HUSK_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.DROWNED_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ZOMBIE_PIGLIN_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ZOGLIN_UNIT);
            event.accept(ItemRegistrar.SKELETON_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.STRAY_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BOGGED_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.CREEPER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SPIDER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.POISON_SPIDER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WRAITH_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.VILLAGER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.MILITIA_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ZOMBIE_VILLAGER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.VINDICATOR_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.PILLAGER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WINDCALLER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.IRON_GOLEM_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WITCH_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.EVOKER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ENDERMAN_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WARDEN_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.RAVAGER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SILVERFISH_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.GRUNT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BRUTE_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.HEADHUNTER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.MARAUDER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.HOGLIN_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BLAZE_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WITHER_SKELETON_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.GHAST_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.MAGMA_CUBE_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SLIME_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ROYAL_GUARD_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.NECROMANCER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.PIGLIN_MERCHANT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WOLF_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.LLAMA_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.PANDA_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.GRIZZLY_BEAR_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.POLAR_BEAR_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SCOUT_DOG_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SCOUT_CAT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.STRIDER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BAT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ELDER_DRUID_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.STAG_LANCER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SHADE_RANGER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ELDER_TREANT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WORLD_TREE_WALKER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WISP_CHOIR_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BLOOM_PRIESTESS_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SEEDSHAPER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SHIPWRIGHT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.GULL_SPOTTER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.CUTLASS_RAIDER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.REEF_GUARD_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BOMBARD_CREW_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SUNFIRE_MANGONEL_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SKULL_HURLER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BOULDER_CHUCKER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BURL_SLINGER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.POWDER_MONKEY_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.THUNDERSTONE_MONK_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.STEAM_LOBBER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.TIDE_PRIEST_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.HARPOONER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.FLAK_SWIVEL_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.MUDSKIPPER_SLOOP_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.HARBOURMASTER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.KRAKEN_CALLER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.MORTAR_GALLEY_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SKY_CORSAIR_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.LEVIATHAN_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.GEOMANCER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.PAPER_CRANE_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.BLADE_DANCER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.FIRE_ADEPT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.JADE_GUARD_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.WIND_MONK_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.INK_SCRIBE_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.FROST_ADEPT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ROCKET_BATTERY_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.LEY_MASTER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.THUNDER_DRAGON_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.EARTHSHAKER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.GRAVITY_MONK_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.MIRROR_MAGE_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SEER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.LANTERN_FORTRESS_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.PHOENIX_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.CELESTIAL_DRAGON_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.TINKER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.GYRO_SCOUT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.STEAM_RUNNER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.RIFLE_SQUAD_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.IRON_WARDEN_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.FLAK_CART_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.MORTAR_CRAWLER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SHIELD_PROJECTOR_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SAPPER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.DEPLOYED_TURRET_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.FOX_COURIER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.MOONWELL_BEARER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.OWL_WATCHER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.HIVE_KEEPER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.LEAFBLADE_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.THORNBOW_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.SENTINEL_TREANT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.ENGINEER_CAPTAIN_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.STEAM_JUGGERNAUT_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.TESLA_WALKER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.GYROCOPTER_GUNSHIP_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.DRILL_BURROWER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.REPAIR_CRAWLER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.CLOCKWORK_SPIDER_UNIT_SPAWN_EGG);
            event.accept(ItemRegistrar.IRON_LEVIATHAN_ENGINE_UNIT_SPAWN_EGG);
        }
        if (BuiltInRegistries.CREATIVE_MODE_TAB.getKey(event.getTab())==CreativeModeTabs.TOOLS_AND_UTILITIES.location()){
            event.accept(ItemRegistrar.THROWABLE_TNT);
            event.accept(ItemRegistrar.THROWN_HERO_EXPERIENCE_BOTTLE);
            event.accept(ItemRegistrar.STAFF_OF_LIGHTNING);
        }
    }
}

