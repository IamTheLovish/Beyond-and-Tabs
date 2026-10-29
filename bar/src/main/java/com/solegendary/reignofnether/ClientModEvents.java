package com.solegendary.reignofnether;

import com.solegendary.reignofnether.blocks.InvisibleBlockRenderer;
import com.solegendary.reignofnether.blocks.SkullTypes;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.entities.models.MagicProjectileModel;
import com.solegendary.reignofnether.entities.renderers.*;
import com.solegendary.reignofnether.fogofwar.FogTintingBakedModel;
import com.solegendary.reignofnether.fogofwar.FogTintingBlockColor;
import com.solegendary.reignofnether.mixin.fogofwar.BlockColorsAccessor;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.particles.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.unit.modelling.models.*;
import com.solegendary.reignofnether.unit.modelling.renderers.*;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.HashSet;

@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    // wrap every baked model so the fog tint applies to untinted quads
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult evt) {
        var models = evt.getModels();
        for (var entry : models.entrySet()) {
            entry.setValue(new FogTintingBakedModel(entry.getValue()));
        }
    }

    // LOWEST so we wrap after vanilla and other mods
    @SubscribeEvent(priority = EventPriority.LOWEST)
    @OnlyIn(Dist.CLIENT)
    public static void onBlockColourEvent(RegisterColorHandlersEvent.Block evt) {
        evt.register((bs, blockAndTintGetter, bp, tintIndex) -> {
            int tint = 0xFFFFFF;
            if (bp != null) {
                BuildingPlacement building = BuildingUtils.findBuilding(true, bp);
                if (building instanceof PortalPlacement portal) {
                    switch (portal.getPortalType()) {
                        case CIVILIAN -> tint = 0x00FF00;
                        case MILITARY -> tint = 0xFF0000;
                        case TRANSPORT -> tint = 0x0000FF;
                    }
                }
            }
            return tint;
        }, Blocks.NETHER_PORTAL);

        evt.register(
                (state, level, pos, tintIndex) -> 0xE0E0E0,
                BlockRegistrar.WRAITH_SNOW_LAYER.get()
        );

        // Tidewrought tidepool: the grey vanilla water texture tinted the faction's shallow-sea teal
        evt.register(
                (state, level, pos, tintIndex) -> 0x3CC8BE,
                BlockRegistrar.TIDEPOOL.get()
        );

        // Verdant thicket: its dark base leaves and ferns are the grey vanilla textures (tintindex 0) painted one fixed
        // deep green, so the bush has depth against its untinted azalea top and never turns biome-lime
        evt.register(
                (state, level, pos, tintIndex) -> 0x3E6B2A,
                BlockRegistrar.THICKET.get()
        );

        // wrap every block's provider with the fog multiplier; biome-tinted ones only fog their untinted quads
        // (BiomeColorsMixin fogs the biome colour itself)
        java.util.Set<Block> biomeTinted = java.util.Set.of(
                Blocks.GRASS_BLOCK, Blocks.FERN, Blocks.GRASS, Blocks.POTTED_FERN,
                Blocks.PINK_PETALS, Blocks.SUGAR_CANE, Blocks.LARGE_FERN, Blocks.TALL_GRASS,
                Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES,
                Blocks.DARK_OAK_LEAVES, Blocks.VINE, Blocks.MANGROVE_LEAVES,
                Blocks.WATER, Blocks.BUBBLE_COLUMN
        );
        BlockColors blockColors = evt.getBlockColors();
        java.util.Map<Holder.Reference<Block>, BlockColor> map =
                ((BlockColorsAccessor) (Object) blockColors).getBlockColors();
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            BlockColor existing = map.get(ForgeRegistries.BLOCKS.getDelegateOrThrow(block));
            evt.register(new FogTintingBlockColor(existing, biomeTinted.contains(block)), block);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerEntityRenderer(EntityRegistrar.ZOMBIE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ZombieUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ZOMBIE_PIGLIN_UNIT.get(), PiglinUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ZOGLIN_UNIT.get(), ZoglinRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SKELETON_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SkeletonUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.HUSK_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HuskUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.DROWNED_UNIT.get(), DrownedRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.STRAY_UNIT.get(), StrayRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BOGGED_UNIT.get(), BoggedUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.CREEPER_UNIT.get(), CreeperRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SPIDER_UNIT.get(), SpiderRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.POISON_SPIDER_UNIT.get(), PoisonSpiderUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WRAITH_UNIT.get(), WraithRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.VILLAGER_UNIT.get(), VillagerUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), RoyalArchitectRenderer::new);
        // Verdant Court T2: the Stag Lancer wears Alex's Mobs' own moose renderer (always antlered, StagLancerUnit)
        evt.registerEntityRenderer(EntityRegistrar.ELDER_DRUID_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ElderDruidRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.STAG_LANCER_UNIT.get(), com.github.alexthe666.alexsmobs.client.render.RenderMoose::new);
        evt.registerEntityRenderer(EntityRegistrar.SHADE_RANGER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ShadeRangerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ELDER_TREANT_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ElderTreantRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WISP_CHOIR_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.WispChoirRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BLOOM_PRIESTESS_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.BloomPriestessRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SCOUT_DOG_UNIT.get(), DogUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SCOUT_CAT_UNIT.get(), CatUnitRenderer::new);
        // the Sunforged Squire Runner (trained militia): a generated body (tools/gen_squire.py -> tools/dsl_to_game.py);
        // the temporary militia called to arms keep the villager body with its professions
        evt.registerEntityRenderer(EntityRegistrar.MILITIA_UNIT.get(), SquireRunnerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.TEMPORARY_MILITIA_UNIT.get(), VillagerUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(), ZombieVillagerUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.EMBALMER_UNIT.get(), ZombieVillagerUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BAT_UNIT.get(), BatUnitRenderer::new);
        // the Sunforged Crossbow Levy: a generated body (tools/gen_levy.py -> tools/dsl_to_game.py)
        evt.registerEntityRenderer(EntityRegistrar.PILLAGER_UNIT.get(), CrossbowLevyRenderer::new);
        // the Sunforged Halberdier: a generated body (tools/gen_halberdier.py -> tools/dsl_to_game.py)
        evt.registerEntityRenderer(EntityRegistrar.VINDICATOR_UNIT.get(), HalberdierRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WINDCALLER_UNIT.get(), WindcallerRenderer::new);
        // the Sunforged Paladin (Radiant): the concept model itself, generated by tools/dsl_to_game.py
        evt.registerEntityRenderer(EntityRegistrar.IRON_GOLEM_UNIT.get(), RadiantPaladinRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SUN_COLOSSUS_UNIT.get(), com.solegendary.reignofnether.unit.units.villagers.SunColossusRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.WorldTreeWalkerRenderer::new);
        // Verdant Court: vanilla bodies with the Court's own skins (the fox keeps its vanilla look)
        evt.registerEntityRenderer(EntityRegistrar.SEEDSHAPER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SeedshaperRenderer::new);
        // Tidewrought: vanilla illager / witch bodies in their own skins (tools/gen_tide_skins.py), AM's seagull as is
        evt.registerEntityRenderer(EntityRegistrar.SHIPWRIGHT_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ShipwrightRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.GULL_SPOTTER_UNIT.get(), com.github.alexthe666.alexsmobs.client.render.RenderSeagull::new);
        evt.registerEntityRenderer(EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.CutlassRaiderRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.REEF_GUARD_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ReefGuardRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BOMBARD_CREW_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.BombardCrewRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SUNFIRE_MANGONEL_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SiegeLobberRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SKULL_HURLER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SiegeLobberRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BOULDER_CHUCKER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SiegeLobberRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BURL_SLINGER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SiegeLobberRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.POWDER_MONKEY_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SiegeLobberRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.THUNDERSTONE_MONK_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SiegeLobberRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.STEAM_LOBBER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SiegeLobberRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.TIDE_PRIEST_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.TidePriestRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.HARPOONER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HarpoonerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.FLAK_SWIVEL_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.FlakSwivelRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.MUDSKIPPER_SLOOP_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.MudskipperSloopRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.HARBOURMASTER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HarbourmasterRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.KRAKEN_CALLER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.KrakenCallerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.MORTAR_GALLEY_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.MortarGalleyRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SKY_CORSAIR_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SkyCorsairRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.LEVIATHAN_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.LeviathanRenderer::new);
        // Stormcallers: illager bodies in their own skins (tools/gen_storm_skins.py), AM's shoebill under a paper skin
        evt.registerEntityRenderer(EntityRegistrar.GEOMANCER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.GeomancerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.PAPER_CRANE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.PaperCraneRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BLADE_DANCER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.BladeDancerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.FIRE_ADEPT_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.FireAdeptRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.JADE_GUARD_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.JadeGuardRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WIND_MONK_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.WindMonkRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.INK_SCRIBE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.InkScribeRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.FROST_ADEPT_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.FrostAdeptRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ROCKET_BATTERY_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.RocketBatteryRenderer::new);
        // slice 3 (the Celestial Observatory): AM's spectre under a jade dragon skin, the golem model in stone
        evt.registerEntityRenderer(EntityRegistrar.LEY_MASTER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.LeyMasterRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.THUNDER_DRAGON_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ThunderDragonRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.EARTHSHAKER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.EarthshakerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.GRAVITY_MONK_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.GravityMonkRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.MIRROR_MAGE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.MirrorMageRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SPARK_SAGE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SparkSageRenderer::new);
        // slice 4: the Seer on the illager body, AM's elephant and sunbird under our skins, the T3 serpent on AM's spectre at 2x
        evt.registerEntityRenderer(EntityRegistrar.SEER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SeerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.LANTERN_FORTRESS_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.LanternFortressRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.PHOENIX_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.PhoenixRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.CelestialDragonRenderer::new);
        // Brassworks: dwarf crews on the illager bodies, the golem / strider / parrot machines in brass skins (tools/gen_brass_skins.py)
        evt.registerEntityRenderer(EntityRegistrar.TINKER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.TinkerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.GYRO_SCOUT_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.GyroScoutRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.STEAM_RUNNER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SteamRunnerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.RIFLE_SQUAD_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.RifleSquadRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.IRON_WARDEN_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.IronWardenRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.FLAK_CART_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.FlakCartRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.MORTAR_CRAWLER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.MortarCrawlerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SHIELD_PROJECTOR_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ShieldProjectorRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SAPPER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SapperRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.DEPLOYED_TURRET_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.RifleSquadRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BOMBARD_SHELL.get(), (ctx) -> new ThrownItemRenderer<>(ctx, 1.5F, false));
        evt.registerEntityRenderer(EntityRegistrar.FOX_COURIER_UNIT.get(), net.minecraft.client.renderer.entity.FoxRenderer::new);
        // slice 2: the Bearer and the Watcher keep their vanilla bodies (witch with a lantern, grey parrot)
        evt.registerEntityRenderer(EntityRegistrar.MOONWELL_BEARER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.MoonwellBearerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.OWL_WATCHER_UNIT.get(), net.minecraft.client.renderer.entity.ParrotRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.HIVE_KEEPER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HiveKeeperRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.LEAFBLADE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.LeafbladeRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.THORNBOW_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ThornbowRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SENTINEL_TREANT_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SentinelTreantRenderer::new);
        // the Sunforged Field Medic: a generated body (tools/gen_medic.py -> tools/dsl_to_game.py)
        evt.registerEntityRenderer(EntityRegistrar.WITCH_UNIT.get(), FieldMedicRenderer::new);
        // the Dome Cleric: the human body since Oct 21 (tools/gen_cleric.py -> tools/dsl_to_game.py)
        evt.registerEntityRenderer(EntityRegistrar.EVOKER_UNIT.get(), DomeClericRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ENDERMAN_UNIT.get(), EndermanRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WARDEN_UNIT.get(), WardenRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.RAVAGER_UNIT.get(), RavagerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WAR_MAMMOTH_UNIT.get(), com.solegendary.reignofnether.unit.units.piglins.WarMammothRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SILVERFISH_UNIT.get(), SilverfishRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.GRUNT_UNIT.get(), PiglinUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BONEWRIGHT_UNIT.get(), PiglinUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.STRIDER_UNIT.get(), StriderRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BRUTE_UNIT.get(), PiglinUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.HEADHUNTER_UNIT.get(), PiglinUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.MARAUDER_UNIT.get(), MarauderRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.HOGLIN_UNIT.get(), HoglinRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ARMOURED_HOGLIN_UNIT.get(), ArmouredHoglinUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BLAZE_UNIT.get(), BlazeUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WITHER_SKELETON_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.WitherSkeletonUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.GHAST_UNIT.get(), GhastUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BONE_DRAGON_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.BoneDragonRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.MAGMA_CUBE_UNIT.get(), MagmaCubeUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.SLIME_UNIT.get(), SlimeUnitRenderer::new);

        evt.registerEntityRenderer(EntityRegistrar.ROYAL_GUARD_UNIT.get(), RoyalGuardRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ENCHANTER_UNIT.get(), EnchanterRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.NECROMANCER_UNIT.get(), NecromancerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WRETCHED_WRAITH_UNIT.get(), WretchedWraithRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.PIGLIN_MERCHANT_UNIT.get(), PiglinMerchantRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WILDFIRE_UNIT.get(), WildfireRenderer::new);

        evt.registerEntityRenderer(EntityRegistrar.POLAR_BEAR_UNIT.get(), PolarBearRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.GRIZZLY_BEAR_UNIT.get(), GrizzlyBearRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.PANDA_UNIT.get(), PandaRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WOLF_UNIT.get(), WolfRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.LLAMA_UNIT.get(), LlamaUnitRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.BEE_UNIT.get(), BeeRenderer::new);

        evt.registerEntityRenderer(EntityRegistrar.PHANTOM_SUMMON.get(), PhantomRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.KILLER_RABBIT_UNIT.get(), RabbitRenderer::new);

        evt.registerEntityRenderer(EntityRegistrar.ADJUSTABLE_PRIMED_TNT.get(), TntRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.THROWABLE_TNT_PROJECTILE.get(), ThrowableTntRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.THROWN_HERO_EXPERIENCE_BOTTLE.get(), ThrownItemRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.NECROMANCER_PROJECTILE.get(), NecromancerProjectileRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WINDCALLER_PROJECTILE.get(), WindcallerProjectileRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.WRAITH_SNOWBALL.get(), ThrownItemRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.MOLTEN_BOMB_PROJECTILE.get(), (ctx) -> new ThrownItemRenderer<>(ctx, 3.0F, true));
        evt.registerEntityRenderer(EntityRegistrar.TOTEM_OF_REGENERATION.get(), TotemOfRegenerationRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.TOTEM_OF_CASTING.get(), TotemOfCastingRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.TOTEM_OF_PROTECTION.get(), TotemOfProtectionRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.TOTEM_OF_SHIELDING.get(), TotemOfShieldingRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.ENGINEER_CAPTAIN_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.EngineerCaptainRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SteamJuggernautRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.TESLA_WALKER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.TeslaWalkerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.GyrocopterGunshipRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.DRILL_BURROWER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.DrillBurrowerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.REPAIR_CRAWLER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.RepairCrawlerRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.CLOCKWORK_SPIDER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ClockworkSpiderRenderer::new);
        // hero slice 2: the seven faction heroes, each on its body's renderer in its own skin
        evt.registerEntityRenderer(EntityRegistrar.DAWN_PALADIN_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.DawnPaladinRenderer::new);   // its own human body since Oct 21
        evt.registerEntityRenderer(EntityRegistrar.GRAVECALLER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HeroRenderers.Gravecaller::new);
        evt.registerEntityRenderer(EntityRegistrar.HORDE_WARCHIEF_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HeroRenderers.HordeWarchief::new);
        evt.registerEntityRenderer(EntityRegistrar.ASH_SHAMAN_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HeroRenderers.AshShaman::new);
        evt.registerEntityRenderer(EntityRegistrar.THORNHEART_DRUID_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HeroRenderers.ThornheartDruid::new);
        evt.registerEntityRenderer(EntityRegistrar.CORSAIR_CAPTAIN_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HeroRenderers.CorsairCaptain::new);
        evt.registerEntityRenderer(EntityRegistrar.STORM_WITCH_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HeroRenderers.StormWitch::new);
        evt.registerEntityRenderer(EntityRegistrar.ARCH_ENGINEER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.HeroRenderers.ArchEngineer::new);
        evt.registerEntityRenderer(EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.IronLeviathanEngineRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.CLOCKWORK_DRONE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ClockworkDroneRenderer::new);
        // big-units: the T2 beasts
        evt.registerEntityRenderer(EntityRegistrar.ORIFLAMME_ELEPHANT_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.OriflammeElephantRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.GOREHORN_RHINO_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.GorehornRhinoRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.OSSUARY_BEHEMOTH_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.OssuaryBehemothRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.MOSSBACK_BISON_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.MossbackBisonRenderer::new);
        // beasts-2: the other three T2 beasts
        evt.registerEntityRenderer(EntityRegistrar.SHELLBACK_TURTLE_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.ShellbackTurtleRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.JADE_QILIN_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.JadeQilinRenderer::new);
        evt.registerEntityRenderer(EntityRegistrar.STEAM_HAULER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.SteamHaulerRenderer::new);
        // combat engineers (design/combat_engineers.md): the illager engineers share one renderer, the Wrecker is a piglin
        evt.registerEntityRenderer(EntityRegistrar.FIELD_MASON_UNIT.get(),
            com.solegendary.reignofnether.unit.modelling.renderers.FieldMasonRenderer::new);   // the human body since Oct 21
        evt.<com.solegendary.reignofnether.unit.units.engineers.CombatEngineerUnit>registerEntityRenderer(EntityRegistrar.BONESETTER_UNIT.get(),
            ctx -> new com.solegendary.reignofnether.unit.modelling.renderers.CombatEngineerRenderer(ctx, "bonesetter_unit"));
        evt.registerEntityRenderer(EntityRegistrar.WRECKER_UNIT.get(), com.solegendary.reignofnether.unit.modelling.renderers.WreckerRenderer::new);
        evt.<com.solegendary.reignofnether.unit.units.engineers.CombatEngineerUnit>registerEntityRenderer(EntityRegistrar.ROOTWARDEN_UNIT.get(),
            ctx -> new com.solegendary.reignofnether.unit.modelling.renderers.CombatEngineerRenderer(ctx, "rootwarden_unit"));
        evt.<com.solegendary.reignofnether.unit.units.engineers.CombatEngineerUnit>registerEntityRenderer(EntityRegistrar.SALVAGER_UNIT.get(),
            ctx -> new com.solegendary.reignofnether.unit.modelling.renderers.CombatEngineerRenderer(ctx, "salvager_unit"));
        evt.<com.solegendary.reignofnether.unit.units.engineers.CombatEngineerUnit>registerEntityRenderer(EntityRegistrar.JADE_MENDER_UNIT.get(),
            ctx -> new com.solegendary.reignofnether.unit.modelling.renderers.CombatEngineerRenderer(ctx, "jade_mender_unit"));
        evt.<com.solegendary.reignofnether.unit.units.engineers.CombatEngineerUnit>registerEntityRenderer(EntityRegistrar.PIONEER_UNIT.get(),
            ctx -> new com.solegendary.reignofnether.unit.modelling.renderers.CombatEngineerRenderer(ctx, "pioneer_unit"));
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientSetupEvent(FMLClientSetupEvent evt) {
        // lovish, Oct 23: installs made before the launcher had his cat get it now, without reinstalling
        Thread brand = new Thread(() -> com.solegendary.reignofnether.launcher.LauncherBrand.applyOnce(
                net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()), "lovish-launcher-brand");
        brand.setDaemon(true);
        brand.start();
        evt.enqueueWork(() -> {
            MenuScreens.register(ContainerRegistrar.TOPDOWNGUI_CONTAINER.get(), TopdownGui::new);
            ItemBlockRenderTypes.setRenderLayer(
                    BlockRegistrar.UNEXTINGUISHABLE_SOUL_FIRE.get(),
                    RenderType.cutout()
            );
            // the thicket model also names cutout_mipped, but registering it here keeps the leaf holes cut out under
            // renderers that ignore the model's render_type (otherwise the bush draws as solid lime cubes)
            ItemBlockRenderTypes.setRenderLayer(
                    BlockRegistrar.THICKET.get(),
                    RenderType.cutoutMipped()
            );
        });
        evt.enqueueWork(() -> {
            SkullBlockRenderer.SKIN_BY_TYPE.put(
                    SkullTypes.DROWNED,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/drowned.png")
            );
        });
        evt.enqueueWork(() -> {
            SkullBlockRenderer.SKIN_BY_TYPE.put(
                    SkullTypes.HUSK,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/husk.png")
            );
        });
        evt.enqueueWork(() -> {
            SkullBlockRenderer.SKIN_BY_TYPE.put(
                    SkullTypes.STRAY,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/skeleton/stray.png")
            );
        });
        evt.enqueueWork(() -> {
            SkullBlockRenderer.SKIN_BY_TYPE.put(
                    SkullTypes.BOGGED,
                    ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/entities/bogged_overlay.png")
            );
        });

        BlockEntityType.SKULL.validBlocks = new HashSet<>(BlockEntityType.SKULL.validBlocks);
        BlockEntityType.SKULL.validBlocks.add(BlockRegistrar.DROWNED_HEAD.get());
        BlockEntityType.SKULL.validBlocks.add(BlockRegistrar.HUSK_HEAD.get());
        BlockEntityType.SKULL.validBlocks.add(BlockRegistrar.STRAY_SKULL.get());
        BlockEntityType.SKULL.validBlocks.add(BlockRegistrar.BOGGED_SKULL.get());
        BlockEntityType.SKULL.validBlocks.add(BlockRegistrar.DROWNED_WALL_HEAD.get());
        BlockEntityType.SKULL.validBlocks.add(BlockRegistrar.HUSK_WALL_HEAD.get());
        BlockEntityType.SKULL.validBlocks.add(BlockRegistrar.STRAY_WALL_SKULL.get());
        BlockEntityType.SKULL.validBlocks.add(BlockRegistrar.BOGGED_WALL_SKULL.get());
    }

    @SubscribeEvent
    public static void onCreateSkullModels(EntityRenderersEvent.CreateSkullModels evt) {
        EntityModelSet modelSet = evt.getEntityModelSet();
        evt.registerSkullModel(
                SkullTypes.DROWNED,
                new SkullModel(modelSet.bakeLayer(ModelLayers.ZOMBIE_HEAD))
        );
        evt.registerSkullModel(
                SkullTypes.HUSK,
                new SkullModel(modelSet.bakeLayer(ModelLayers.ZOMBIE_HEAD))
        );
        evt.registerSkullModel(
                SkullTypes.STRAY,
                new SkullModel(modelSet.bakeLayer(ModelLayers.SKELETON_SKULL))
        );
        evt.registerSkullModel(
                SkullTypes.BOGGED,
                new SkullModel(modelSet.bakeLayer(ModelLayers.SKELETON_SKULL))
        );
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(BlockEntityRegistrar.INVISIBLE_BLOCK_ENTITY.get(), InvisibleBlockRenderer::new);
        evt.registerBlockEntityRenderer(BlockEntityRegistrar.VINE_SNARE_BLOCK_ENTITY.get(), com.solegendary.reignofnether.blocks.VineSnareRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VillagerUnitModel.LAYER_LOCATION, VillagerUnitModel::createBodyLayer);
        event.registerLayerDefinition(RoyalGuardModel.LAYER_LOCATION, RoyalGuardModel::createBodyLayer);
        event.registerLayerDefinition(NecromancerModel.LAYER_LOCATION, NecromancerModel::createBodyLayer);
        event.registerLayerDefinition(PiglinMerchantModel.LAYER_LOCATION, PiglinMerchantModel::createBodyLayer);
        event.registerLayerDefinition(MarauderModel.LAYER_LOCATION, MarauderModel::createBodyLayer);
        event.registerLayerDefinition(ArmouredHoglinUnitModel.LAYER_LOCATION, ArmouredHoglinUnitModel::createBodyLayer);
        event.registerLayerDefinition(MagicProjectileModel.LAYER_LOCATION, MagicProjectileModel::createBodyLayer);
        event.registerLayerDefinition(EnchanterModel.LAYER_LOCATION, EnchanterModel::createBodyLayer);
        event.registerLayerDefinition(WretchedWraithModel.LAYER_LOCATION, WretchedWraithModel::createBodyLayer);
        event.registerLayerDefinition(WildfireModel.LAYER_LOCATION, WildfireModel::createBodyLayer);
        event.registerLayerDefinition(WindcallerModel.LAYER_LOCATION, WindcallerModel::createBodyLayer);
        event.registerLayerDefinition(WraithModel.LAYER_LOCATION, WraithModel::createBodyLayer);
        event.registerLayerDefinition(TotemOfRegenerationModel.LAYER_LOCATION, TotemOfRegenerationModel::createBodyLayer);
        event.registerLayerDefinition(TotemOfCastingModel.LAYER_LOCATION, TotemOfCastingModel::createBodyLayer);
        event.registerLayerDefinition(TotemOfShieldingModel.LAYER_LOCATION, TotemOfShieldingModel::createBodyLayer);
        event.registerLayerDefinition(TotemOfProtectionModel.LAYER_LOCATION, TotemOfProtectionModel::createBodyLayer);
        event.registerLayerDefinition(SkyCorsairModel.LAYER_LOCATION, SkyCorsairModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.RadiantPaladinModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.RadiantPaladinModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.FieldMedicModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.FieldMedicModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.CrossbowLevyModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.CrossbowLevyModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.SquireRunnerModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.SquireRunnerModel::createBodyLayer);
        // the four Sunforged men off the villager body (Oct 21, tools/human_kit.py)
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.DomeClericModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.DomeClericModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.RoyalArchitectModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.RoyalArchitectModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.FieldMasonModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.FieldMasonModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.DawnPaladinModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.DawnPaladinModel::createBodyLayer);
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.MossbackBisonModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.MossbackBisonModel::createBodyLayer);
        // the White Angel capitol's marble statue (building/StatueRenderClientEvents; tools/gen_angel_statue.py)
        event.registerLayerDefinition(com.solegendary.reignofnether.unit.modelling.models.gen.WhiteAngelStatueModel.LAYER_LOCATION,
            com.solegendary.reignofnether.unit.modelling.models.gen.WhiteAngelStatueModel::createBodyLayer);
        event.registerLayerDefinition(AbstractVillagerUnitRenderer.VILLAGER_ARMOR_OUTER_LAYER, IllagerArmorModel::createOuterArmorLayer);
        event.registerLayerDefinition(AbstractVillagerUnitRenderer.VILLAGER_ARMOR_INNER_LAYER, IllagerArmorModel::createInnerArmorLayer);
    }

    // a resource reload (F3+T, a pack switched on or off) may add or remove a unit's _team.png: forget the cached
    // answers, or the team colour stays missing (or points at a texture that is gone) until a restart
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void registerReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent evt) {
        evt.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm ->
                com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer.clearCache());
    }

    // the world look pass's shader (postfx/WorldLookPass): loaded with vanilla's core shaders, so F3+T reloads it too
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void registerShaders(RegisterShadersEvent evt) {
        com.solegendary.reignofnether.postfx.WorldLookPass.registerShader(evt);
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void registerParticles(RegisterParticleProvidersEvent evt) {
        evt.registerSpriteSet(
                ParticleRegistrar.BIG_ENCHANT.get(),
                BigEnchantParticle.Provider::new
        );
        evt.registerSpriteSet(
                ParticleRegistrar.BIG_SOUL_FLAME.get(),
                BigSoulFlameParticle.Provider::new
        );
        evt.registerSpriteSet(
                ParticleRegistrar.LEVEL_UP.get(),
                LevelUpParticle.Provider::new
        );
        evt.registerSpriteSet(
                ParticleRegistrar.FLOATING_CRIT.get(),
                AbstractFloatingParticle.Provider::new
        );
        evt.registerSpriteSet(
                ParticleRegistrar.FLOATING_HEART.get(),
                FloatingHeartParticle.Provider::new
        );
        evt.registerSpriteSet(
                ParticleRegistrar.MANA.get(),
                ManaParticle.Provider::new
        );
        evt.registerSpriteSet(
                ParticleRegistrar.BIG_VIBRATION.get(),
                BigVibrationParticle.Provider::new
        );
    }
}

