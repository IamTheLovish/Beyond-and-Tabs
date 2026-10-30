package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.entities.*;
import com.solegendary.reignofnether.hero.HeroExperienceOrb;
import com.solegendary.reignofnether.unit.modelling.renderers.*;
import com.solegendary.reignofnether.unit.units.monsters.*;
import com.solegendary.reignofnether.unit.units.neutral.*;
import com.solegendary.reignofnether.unit.units.piglins.*;
import com.solegendary.reignofnether.unit.units.villagers.*;
import com.solegendary.reignofnether.unit.units.verdant.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;


public class EntityRegistrar {

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ReignOfNether.MOD_ID);

    private static final int UNIT_CLIENT_TRACKING_RANGE = 100;

    public static final RegistryObject<EntityType<ZombieVillagerUnit>> ZOMBIE_VILLAGER_UNIT = ENTITIES.register("zombie_villager_unit",
            () -> EntityType.Builder.of(ZombieVillagerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.ZOMBIE_VILLAGER.getWidth(), EntityType.ZOMBIE_VILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "zombie_villager_unit").toString()));

    public static final RegistryObject<EntityType<BatUnit>> BAT_UNIT = ENTITIES.register("bat_unit",
            () -> EntityType.Builder.of(BatUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.BAT.getWidth(), EntityType.BAT.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bat_unit").toString()));

    public static final RegistryObject<EntityType<ZombieUnit>> ZOMBIE_UNIT = ENTITIES.register("zombie_unit",
            // can add other attributes here like sized() for hitbox, no summon, fire immunity, etc.
            () -> EntityType.Builder.of(ZombieUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.ZOMBIE.getWidth(), EntityType.ZOMBIE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "zombie_unit").toString()));

    public static final RegistryObject<EntityType<HuskUnit>> HUSK_UNIT = ENTITIES.register("husk_unit",
            () -> EntityType.Builder.of(HuskUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.HUSK.getWidth(), EntityType.HUSK.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "husk_unit").toString()));

    public static final RegistryObject<EntityType<DrownedUnit>> DROWNED_UNIT = ENTITIES.register("drowned_unit",
            () -> EntityType.Builder.of(DrownedUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.DROWNED.getWidth(), EntityType.DROWNED.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "drowned_unit").toString()));

    public static final RegistryObject<EntityType<ZombiePiglinUnit>> ZOMBIE_PIGLIN_UNIT = ENTITIES.register("zombie_piglin_unit",
            () -> EntityType.Builder.of(ZombiePiglinUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.ZOMBIFIED_PIGLIN.getWidth(), EntityType.ZOMBIFIED_PIGLIN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "zombie_piglin_unit").toString()));

    public static final RegistryObject<EntityType<ZoglinUnit>> ZOGLIN_UNIT = ENTITIES.register("zoglin_unit",
            () -> EntityType.Builder.of(ZoglinUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.ZOGLIN.getWidth(), EntityType.ZOGLIN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "zoglin_unit").toString()));

    public static final RegistryObject<EntityType<SkeletonUnit>> SKELETON_UNIT = ENTITIES.register("skeleton_unit",
            () -> EntityType.Builder.of(SkeletonUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SKELETON.getWidth(), EntityType.SKELETON.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "skeleton_unit").toString()));

    public static final RegistryObject<EntityType<StrayUnit>> STRAY_UNIT = ENTITIES.register("stray_unit",
            () -> EntityType.Builder.of(StrayUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.STRAY.getWidth(), EntityType.STRAY.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "stray_unit").toString()));

    public static final RegistryObject<EntityType<BoggedUnit>> BOGGED_UNIT = ENTITIES.register("bogged_unit",
            () -> EntityType.Builder.of(BoggedUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SKELETON.getWidth(), EntityType.SKELETON.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bogged_unit").toString()));

    public static final RegistryObject<EntityType<CreeperUnit>> CREEPER_UNIT = ENTITIES.register("creeper_unit",
            () -> EntityType.Builder.of(CreeperUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.CREEPER.getWidth(), EntityType.CREEPER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "creeper_unit").toString()));

    public static final RegistryObject<EntityType<SpiderUnit>> SPIDER_UNIT = ENTITIES.register("spider_unit",
            () -> EntityType.Builder.of(SpiderUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SPIDER.getWidth() - 0.4f, EntityType.SPIDER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spider_unit").toString()));

    public static final RegistryObject<EntityType<PoisonSpiderUnit>> POISON_SPIDER_UNIT = ENTITIES.register("poison_spider_unit",
            () -> EntityType.Builder.of(PoisonSpiderUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SPIDER.getWidth() - 0.4f, EntityType.SPIDER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "poison_spider_unit").toString()));

    public static final RegistryObject<EntityType<WraithUnit>> WRAITH_UNIT = ENTITIES.register("wraith_unit",
            () -> EntityType.Builder.of(WraithUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.BLAZE.getWidth(), EntityType.BLAZE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wraith_unit").toString()));

    public static final RegistryObject<EntityType<VillagerUnit>> VILLAGER_UNIT = ENTITIES.register("villager_unit",
            () -> EntityType.Builder.of(VillagerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VILLAGER.getWidth(), EntityType.VILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "villager_unit").toString()));

    public static final RegistryObject<EntityType<ScoutDogUnit>> SCOUT_DOG_UNIT = ENTITIES.register("scout_dog_unit",
            () -> EntityType.Builder.of(ScoutDogUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WOLF.getWidth(), EntityType.WOLF.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "scout_dog_unit").toString()));

    public static final RegistryObject<EntityType<ScoutCatUnit>> SCOUT_CAT_UNIT = ENTITIES.register("scout_cat_unit",
            () -> EntityType.Builder.of(ScoutCatUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WOLF.getWidth(), EntityType.WOLF.getHeight()) // must be the same size for balance
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "scout_cat_unit").toString()));

    public static final RegistryObject<EntityType<MilitiaUnit>> MILITIA_UNIT = ENTITIES.register("militia_unit",
            () -> EntityType.Builder.of(MilitiaUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VILLAGER.getWidth(), EntityType.VILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "militia_unit").toString()));

    public static final RegistryObject<EntityType<TemporaryMilitiaUnit>> TEMPORARY_MILITIA_UNIT = ENTITIES.register("temporary_militia_unit",
            () -> EntityType.Builder.of(TemporaryMilitiaUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VILLAGER.getWidth(), EntityType.VILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "temporary_militia_unit").toString()));

    public static final RegistryObject<EntityType<VindicatorUnit>> VINDICATOR_UNIT = ENTITIES.register("vindicator_unit",
            () -> EntityType.Builder.of(VindicatorUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "vindicator_unit").toString()));

    public static final RegistryObject<EntityType<PillagerUnit>> PILLAGER_UNIT = ENTITIES.register("pillager_unit",
            () -> EntityType.Builder.of(PillagerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "pillager_unit").toString()));

    public static final RegistryObject<EntityType<WindcallerUnit>> WINDCALLER_UNIT = ENTITIES.register("windcaller_unit",
            () -> EntityType.Builder.of(WindcallerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "windcaller_unit").toString()));

    public static final RegistryObject<EntityType<IronGolemUnit>> IRON_GOLEM_UNIT = ENTITIES.register("iron_golem_unit",
            () -> EntityType.Builder.of(IronGolemUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.IRON_GOLEM.getWidth(), EntityType.IRON_GOLEM.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_golem_unit").toString()));

    public static final RegistryObject<EntityType<WitchUnit>> WITCH_UNIT = ENTITIES.register("witch_unit",
            () -> EntityType.Builder.of(WitchUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WITCH.getWidth(), EntityType.WITCH.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "witch_unit").toString()));

    public static final RegistryObject<EntityType<EvokerUnit>> EVOKER_UNIT = ENTITIES.register("evoker_unit",
            () -> EntityType.Builder.of(EvokerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.EVOKER.getWidth(), EntityType.EVOKER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "evoker_unit").toString()));

    public static final RegistryObject<EntityType<EndermanUnit>> ENDERMAN_UNIT = ENTITIES.register("enderman_unit",
            () -> EntityType.Builder.of(EndermanUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.ENDERMAN.getWidth(), EntityType.ENDERMAN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "enderman_unit").toString()));

    public static final RegistryObject<EntityType<RavagerUnit>> RAVAGER_UNIT = ENTITIES.register("ravager_unit",
            () -> EntityType.Builder.of(RavagerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.RAVAGER.getWidth(), EntityType.RAVAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ravager_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit>> WAR_MAMMOTH_UNIT = ENTITIES.register("war_mammoth_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.RAVAGER.getWidth() * com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.SCALE,
                           EntityType.RAVAGER.getHeight() * com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "war_mammoth_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit>> SUN_COLOSSUS_UNIT = ENTITIES.register("sun_colossus_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.IRON_GOLEM.getWidth() * com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit.SCALE,
                           EntityType.IRON_GOLEM.getHeight() * com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sun_colossus_unit").toString()));

    // Verdant Court T3: the golem body at the walker's scale, so its hitbox matches the tree you see
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit>> WORLD_TREE_WALKER_UNIT = ENTITIES.register("world_tree_walker_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.IRON_GOLEM.getWidth() * com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.SCALE,
                           EntityType.IRON_GOLEM.getHeight() * com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "world_tree_walker_unit").toString()));

    // Verdant Court (design/verdant_court_plan.md, slice 1)
    public static final RegistryObject<EntityType<SeedshaperUnit>> SEEDSHAPER_UNIT = ENTITIES.register("seedshaper_unit",
            () -> EntityType.Builder.of(SeedshaperUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "seedshaper_unit").toString()));

    public static final RegistryObject<EntityType<FoxCourierUnit>> FOX_COURIER_UNIT = ENTITIES.register("fox_courier_unit",
            () -> EntityType.Builder.of(FoxCourierUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.FOX.getWidth(), EntityType.FOX.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "fox_courier_unit").toString()));

    // Verdant Court T1, slice 2
    public static final RegistryObject<EntityType<MoonwellBearerUnit>> MOONWELL_BEARER_UNIT = ENTITIES.register("moonwell_bearer_unit",
            () -> EntityType.Builder.of(MoonwellBearerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WITCH.getWidth(), EntityType.WITCH.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "moonwell_bearer_unit").toString()));

    public static final RegistryObject<EntityType<OwlWatcherUnit>> OWL_WATCHER_UNIT = ENTITIES.register("owl_watcher_unit",
            () -> EntityType.Builder.of(OwlWatcherUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PARROT.getWidth(), EntityType.PARROT.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "owl_watcher_unit").toString()));

    public static final RegistryObject<EntityType<HiveKeeperUnit>> HIVE_KEEPER_UNIT = ENTITIES.register("hive_keeper_unit",
            () -> EntityType.Builder.of(HiveKeeperUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hive_keeper_unit").toString()));

    public static final RegistryObject<EntityType<LeafbladeUnit>> LEAFBLADE_UNIT = ENTITIES.register("leafblade_unit",
            () -> EntityType.Builder.of(LeafbladeUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "leafblade_unit").toString()));

    public static final RegistryObject<EntityType<ThornbowUnit>> THORNBOW_UNIT = ENTITIES.register("thornbow_unit",
            () -> EntityType.Builder.of(ThornbowUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SKELETON.getWidth(), EntityType.SKELETON.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thornbow_unit").toString()));

    public static final RegistryObject<EntityType<SentinelTreantUnit>> SENTINEL_TREANT_UNIT = ENTITIES.register("sentinel_treant_unit",
            () -> EntityType.Builder.of(SentinelTreantUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.IRON_GOLEM.getWidth() * 1.1f, EntityType.IRON_GOLEM.getHeight() * 1.1f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sentinel_treant_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit>> BONE_DRAGON_UNIT = ENTITIES.register("bone_dragon_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PHANTOM.getWidth() * com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.SCALE,
                           EntityType.PHANTOM.getHeight() * com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    // every tick: a big fast flyer lerped from every 3rd tick stutters (DragonFlightGameTests)
                    .updateInterval(1)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bone_dragon_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit>> ROYAL_ARCHITECT_UNIT = ENTITIES.register("royal_architect_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "royal_architect_unit").toString()));

    // Verdant Court T2 (design/verdant_court_plan.md, slice 4): trained at the Circle of Elders
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.verdant.ElderDruidUnit>> ELDER_DRUID_UNIT = ENTITIES.register("elder_druid_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.verdant.ElderDruidUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "elder_druid_unit").toString()));

    // Alex's Mobs' moose body (RenderMoose); a little narrower than AM's wild moose so it fits through a gap in a line
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.verdant.StagLancerUnit>> STAG_LANCER_UNIT = ENTITIES.register("stag_lancer_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.verdant.StagLancerUnit::new, MobCategory.CREATURE)
                    .sized(1.4f, 2.2f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "stag_lancer_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit>> SHADE_RANGER_UNIT = ENTITIES.register("shade_ranger_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SKELETON.getWidth(), EntityType.SKELETON.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shade_ranger_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.verdant.ElderTreantUnit>> ELDER_TREANT_UNIT = ENTITIES.register("elder_treant_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.verdant.ElderTreantUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.IRON_GOLEM.getWidth() * com.solegendary.reignofnether.unit.units.verdant.ElderTreantUnit.SCALE,
                           EntityType.IRON_GOLEM.getHeight() * com.solegendary.reignofnether.unit.units.verdant.ElderTreantUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "elder_treant_unit").toString()));

    // Verdant Court T2 extras: the anti-air choir (three allay-bodied wisps round one hitbox) and the support priestess
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit>> WISP_CHOIR_UNIT = ENTITIES.register("wisp_choir_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit::new, MobCategory.CREATURE)
                    .sized(0.9f, 0.8f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wisp_choir_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.verdant.BloomPriestessUnit>> BLOOM_PRIESTESS_UNIT = ENTITIES.register("bloom_priestess_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.verdant.BloomPriestessUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bloom_priestess_unit").toString()));

    // Tidewrought (design/tidewrought_plan.md, slice 1). The Gull Spotter is Alex's Mobs' seagull body (RenderSeagull),
    // a little bigger than AM's wild gull so it can be clicked from the RTS camera
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.ShipwrightUnit>> SHIPWRIGHT_UNIT = ENTITIES.register("shipwright_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.ShipwrightUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shipwright_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.GullSpotterUnit>> GULL_SPOTTER_UNIT = ENTITIES.register("gull_spotter_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.GullSpotterUnit::new, MobCategory.CREATURE)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gull_spotter_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.CutlassRaiderUnit>> CUTLASS_RAIDER_UNIT = ENTITIES.register("cutlass_raider_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.CutlassRaiderUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "cutlass_raider_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.ReefGuardUnit>> REEF_GUARD_UNIT = ENTITIES.register("reef_guard_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.ReefGuardUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "reef_guard_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit>> BOMBARD_CREW_UNIT = ENTITIES.register("bombard_crew_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bombard_crew_unit").toString()));

    // the Sunfire Mangonel (design/siege_t1.md): the shared T1 siege lobber
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit>> SUNFIRE_MANGONEL_UNIT = ENTITIES.register("sunfire_mangonel_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sunfire_mangonel_unit").toString()));

    // the Skull Hurler (design/siege_t1.md): the shared T1 siege lobber
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit>> SKULL_HURLER_UNIT = ENTITIES.register("skull_hurler_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "skull_hurler_unit").toString()));

    // the Boulder Chucker (design/siege_t1.md): the shared T1 siege lobber
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit>> BOULDER_CHUCKER_UNIT = ENTITIES.register("boulder_chucker_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "boulder_chucker_unit").toString()));

    // the Burl Slinger (design/siege_t1.md): the shared T1 siege lobber
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit>> BURL_SLINGER_UNIT = ENTITIES.register("burl_slinger_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "burl_slinger_unit").toString()));

    // the Powder Monkey (design/siege_t1.md): the shared T1 siege lobber
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit>> POWDER_MONKEY_UNIT = ENTITIES.register("powder_monkey_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "powder_monkey_unit").toString()));

    // the Thunderstone Monk (design/siege_t1.md): the shared T1 siege lobber
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit>> THUNDERSTONE_MONK_UNIT = ENTITIES.register("thunderstone_monk_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thunderstone_monk_unit").toString()));

    // the Steam Lobber (design/siege_t1.md): the shared T1 siege lobber
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit>> STEAM_LOBBER_UNIT = ENTITIES.register("steam_lobber_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "steam_lobber_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.TidePriestUnit>> TIDE_PRIEST_UNIT = ENTITIES.register("tide_priest_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.TidePriestUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WITCH.getWidth(), EntityType.WITCH.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tide_priest_unit").toString()));

    // Tidewrought slice 2: the rest of the Slipway (the Mudskipper Sloop is Alex's Mobs' mudskipper, scaled up)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.HarpoonerUnit>> HARPOONER_UNIT = ENTITIES.register("harpooner_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.HarpoonerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "harpooner_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit>> FLAK_SWIVEL_UNIT = ENTITIES.register("flak_swivel_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "flak_swivel_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.MudskipperSloopUnit>> MUDSKIPPER_SLOOP_UNIT = ENTITIES.register("mudskipper_sloop_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.MudskipperSloopUnit::new, MobCategory.CREATURE)
                    .sized(0.7f * com.solegendary.reignofnether.unit.units.tide.MudskipperSloopUnit.SCALE, 0.44f * com.solegendary.reignofnether.unit.units.tide.MudskipperSloopUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mudskipper_sloop_unit").toString()));

    // Tidewrought slice 3: the Drydock's T2 (the Mortar Galley is Alex's Mobs' snapping turtle scaled up, the Sky
    // Corsair a ghast body at half a wild one's size - both renderers scale the same way)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.HarbourmasterUnit>> HARBOURMASTER_UNIT = ENTITIES.register("harbourmaster_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.HarbourmasterUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "harbourmaster_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.KrakenCallerUnit>> KRAKEN_CALLER_UNIT = ENTITIES.register("kraken_caller_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.KrakenCallerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "kraken_caller_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit>> MORTAR_GALLEY_UNIT = ENTITIES.register("mortar_galley_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit::new, MobCategory.CREATURE)
                    .sized(1.25f * com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit.SCALE, 0.65f * com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mortar_galley_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit>> SKY_CORSAIR_UNIT = ENTITIES.register("sky_corsair_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.GHAST.getWidth() * com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit.SCALE,
                            EntityType.GHAST.getHeight() * com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sky_corsair_unit").toString()));

    // Tidewrought T3 (slice 4: the Abyssal Shipyard): AM's lobster footprint at the Leviathan's scale, and tall enough
    // to take the fortress on its back (shell top + fortress ~2.3 blocks), so the hitbox matches what you click
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.LeviathanUnit>> LEVIATHAN_UNIT = ENTITIES.register("leviathan_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.LeviathanUnit::new, MobCategory.CREATURE)
                    .sized(0.7f * com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.SCALE, 2.4f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "leviathan_unit").toString()));

    // Stormcallers (design/stormcallers_plan.md, slice 1). The Paper Crane is Alex's Mobs' shoebill body (RenderShoebill
    // under our own paper skin), sized a little over AM's wild bird so it can be clicked from the RTS camera
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.GeomancerUnit>> GEOMANCER_UNIT = ENTITIES.register("geomancer_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.GeomancerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "geomancer_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.PaperCraneUnit>> PAPER_CRANE_UNIT = ENTITIES.register("paper_crane_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.PaperCraneUnit::new, MobCategory.CREATURE)
                    .sized(0.7f, 0.9f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "paper_crane_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.BladeDancerUnit>> BLADE_DANCER_UNIT = ENTITIES.register("blade_dancer_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.BladeDancerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blade_dancer_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit>> FIRE_ADEPT_UNIT = ENTITIES.register("fire_adept_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "fire_adept_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.JadeGuardUnit>> JADE_GUARD_UNIT = ENTITIES.register("jade_guard_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.JadeGuardUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "jade_guard_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.WindMonkUnit>> WIND_MONK_UNIT = ENTITIES.register("wind_monk_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.WindMonkUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_monk_unit").toString()));

    // Stormcallers slice 2: the rest of T1 (Ink Scribe on the witch body, Frost Adept and Rocket Battery on the pillager's)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.InkScribeUnit>> INK_SCRIBE_UNIT = ENTITIES.register("ink_scribe_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.InkScribeUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WITCH.getWidth(), EntityType.WITCH.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ink_scribe_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.FrostAdeptUnit>> FROST_ADEPT_UNIT = ENTITIES.register("frost_adept_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.FrostAdeptUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "frost_adept_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit>> ROCKET_BATTERY_UNIT = ENTITIES.register("rocket_battery_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rocket_battery_unit").toString()));



    // Stormcallers slice 3: the Celestial Observatory's T2 (the Ley Master on the Geomancer's body, the Thunder Dragon on
    // Alex's Mobs' spectre - sized for its 1.6x render - the Earthshaker on the iron golem's, the Monk and the Mage on the pillager's)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.LeyMasterUnit>> LEY_MASTER_UNIT = ENTITIES.register("ley_master_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.LeyMasterUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ley_master_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.ThunderDragonUnit>> THUNDER_DRAGON_UNIT = ENTITIES.register("thunder_dragon_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.ThunderDragonUnit::new, MobCategory.CREATURE)
                    .sized(1.6f, 1.0f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    // every tick: a big fast flyer lerped from every 3rd tick stutters (DragonFlightGameTests)
                    .updateInterval(1)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thunder_dragon_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.EarthshakerUnit>> EARTHSHAKER_UNIT = ENTITIES.register("earthshaker_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.EarthshakerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.IRON_GOLEM.getWidth(), EntityType.IRON_GOLEM.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "earthshaker_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.GravityMonkUnit>> GRAVITY_MONK_UNIT = ENTITIES.register("gravity_monk_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.GravityMonkUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gravity_monk_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.MirrorMageUnit>> MIRROR_MAGE_UNIT = ENTITIES.register("mirror_mage_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.MirrorMageUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mirror_mage_unit").toString()));
    // the Stormcallers' essence caster (design/essence_casters.md): the monks' illager body
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.SparkSageUnit>> SPARK_SAGE_UNIT = ENTITIES.register("spark_sage_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.SparkSageUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spark_sage_unit").toString()));

    // Stormcallers slice 4: the rest of the Observatory's T2 (the Seer on the pillager's body, the Lantern Fortress on
    // Alex's Mobs' elephant drawn at 0.75x, the Phoenix on AM's sunbird at 0.55x) and the T3 Celestial Dragon on AM's
    // spectre at 2x - hitboxes sized for their renders, not AM's wild bodies
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.SeerUnit>> SEER_UNIT = ENTITIES.register("seer_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.SeerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "seer_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.LanternFortressUnit>> LANTERN_FORTRESS_UNIT = ENTITIES.register("lantern_fortress_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.LanternFortressUnit::new, MobCategory.CREATURE)
                    .sized(2.0f, 2.6f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lantern_fortress_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.PhoenixUnit>> PHOENIX_UNIT = ENTITIES.register("phoenix_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.PhoenixUnit::new, MobCategory.CREATURE)
                    .sized(1.4f, 0.9f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "phoenix_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit>> CELESTIAL_DRAGON_UNIT = ENTITIES.register("celestial_dragon_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit::new, MobCategory.CREATURE)
                    .sized(2.4f, 1.4f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    // every tick: a big fast flyer lerped from every 3rd tick stutters (DragonFlightGameTests)
                    .updateInterval(1)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "celestial_dragon_unit").toString()));


    // Brassworks Artificers (design/brassworks_plan.md, slice 1): dwarf crews on the illager bodies, the Iron Warden on
    // the iron golem (0.8x), the Steam Runner on the strider (0.8x, fire-immune: a boiler), the Gyro Scout on the parrot
    // (1.4x), and the Guildmaster's Deployed Turret (a legless Rifle Squad)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.TinkerUnit>> TINKER_UNIT = ENTITIES.register("tinker_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.TinkerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tinker_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.GyroScoutUnit>> GYRO_SCOUT_UNIT = ENTITIES.register("gyro_scout_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.GyroScoutUnit::new, MobCategory.CREATURE)
                    .sized(0.7f, 0.9f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gyro_scout_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.SteamRunnerUnit>> STEAM_RUNNER_UNIT = ENTITIES.register("steam_runner_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.SteamRunnerUnit::new, MobCategory.CREATURE)
                    .sized(0.75f, 1.4f)
                    .fireImmune()
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "steam_runner_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.RifleSquadUnit>> RIFLE_SQUAD_UNIT = ENTITIES.register("rifle_squad_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.RifleSquadUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rifle_squad_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.IronWardenUnit>> IRON_WARDEN_UNIT = ENTITIES.register("iron_warden_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.IronWardenUnit::new, MobCategory.CREATURE)
                    .sized(1.15f, 2.2f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_warden_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.FlakCartUnit>> FLAK_CART_UNIT = ENTITIES.register("flak_cart_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.FlakCartUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "flak_cart_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.MortarCrawlerUnit>> MORTAR_CRAWLER_UNIT = ENTITIES.register("mortar_crawler_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.MortarCrawlerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mortar_crawler_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.ShieldProjectorUnit>> SHIELD_PROJECTOR_UNIT = ENTITIES.register("shield_projector_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.ShieldProjectorUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shield_projector_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.SapperUnit>> SAPPER_UNIT = ENTITIES.register("sapper_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.SapperUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sapper_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit>> DEPLOYED_TURRET_UNIT = ENTITIES.register("deployed_turret_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit::new, MobCategory.CREATURE)
                    .sized(0.8f, 1.2f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "deployed_turret_unit").toString()));

    // the Bombard Crew's mortar shell (ThrownItemRenderer draws it as a fire charge)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.BombardShell>> BOMBARD_SHELL = ENTITIES.register("bombard_shell",
            () -> EntityType.Builder.<com.solegendary.reignofnether.unit.units.tide.BombardShell>of(com.solegendary.reignofnether.unit.units.tide.BombardShell::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bombard_shell").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.monsters.EmbalmerUnit>> EMBALMER_UNIT = ENTITIES.register("embalmer_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.monsters.EmbalmerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "embalmer_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.piglins.BonewrightUnit>> BONEWRIGHT_UNIT = ENTITIES.register("bonewright_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.piglins.BonewrightUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PIGLIN.getWidth(), EntityType.PIGLIN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bonewright_unit").toString()));

    public static final RegistryObject<EntityType<WardenUnit>> WARDEN_UNIT = ENTITIES.register("warden_unit",
            () -> EntityType.Builder.of(WardenUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WARDEN.getWidth(), EntityType.WARDEN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "warden_unit").toString()));

    public static final RegistryObject<EntityType<SilverfishUnit>> SILVERFISH_UNIT = ENTITIES.register("silverfish_unit",
            () -> EntityType.Builder.of(SilverfishUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SILVERFISH.getWidth(), EntityType.SILVERFISH.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "silverfish_unit").toString()));

    public static final RegistryObject<EntityType<GruntUnit>> GRUNT_UNIT = ENTITIES.register("grunt_unit",
            () -> EntityType.Builder.of(GruntUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PIGLIN.getWidth(), EntityType.PIGLIN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "grunt_unit").toString()));

    public static final RegistryObject<EntityType<StriderUnit>> STRIDER_UNIT = ENTITIES.register("strider_unit",
            () -> EntityType.Builder.of(StriderUnit::new, MobCategory.CREATURE)
                    .fireImmune()
                    .sized(EntityType.STRIDER.getWidth(), EntityType.STRIDER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "strider_unit").toString()));

    public static final RegistryObject<EntityType<BruteUnit>> BRUTE_UNIT = ENTITIES.register("brute_unit",
            () -> EntityType.Builder.of(BruteUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PIGLIN_BRUTE.getWidth(), EntityType.PIGLIN_BRUTE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "brute_unit").toString()));

    public static final RegistryObject<EntityType<HeadhunterUnit>> HEADHUNTER_UNIT = ENTITIES.register("headhunter_unit",
            () -> EntityType.Builder.of(HeadhunterUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PIGLIN_BRUTE.getWidth(), EntityType.PIGLIN_BRUTE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "headhunter_unit").toString()));

    public static final RegistryObject<EntityType<MarauderUnit>> MARAUDER_UNIT = ENTITIES.register("marauder_unit",
            () -> EntityType.Builder.of(MarauderUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.IRON_GOLEM.getWidth(), EntityType.IRON_GOLEM.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "marauder_unit").toString()));

    public static final RegistryObject<EntityType<HoglinUnit>> HOGLIN_UNIT = ENTITIES.register("hoglin_unit",
            () -> EntityType.Builder.of(HoglinUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.HOGLIN.getWidth(), EntityType.HOGLIN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hoglin_unit").toString()));

    public static final RegistryObject<EntityType<ArmouredHoglinUnit>> ARMOURED_HOGLIN_UNIT = ENTITIES.register("armoured_hoglin_unit",
            () -> EntityType.Builder.of(ArmouredHoglinUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.HOGLIN.getWidth(), EntityType.HOGLIN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "armoured_hoglin_unit").toString()));

    public static final RegistryObject<EntityType<BlazeUnit>> BLAZE_UNIT = ENTITIES.register("blaze_unit",
            () -> EntityType.Builder.of(BlazeUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.BLAZE.getWidth(), EntityType.BLAZE.getHeight())
                    .fireImmune()
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blaze_unit").toString()));

    public static final RegistryObject<EntityType<WitherSkeletonUnit>> WITHER_SKELETON_UNIT = ENTITIES.register("wither_skeleton_unit",
            () -> EntityType.Builder.of(WitherSkeletonUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WITHER_SKELETON.getWidth(), EntityType.WITHER_SKELETON.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wither_skeleton_unit").toString()));

    public static final RegistryObject<EntityType<GhastUnit>> GHAST_UNIT = ENTITIES.register("ghast_unit",
            () -> EntityType.Builder.of(GhastUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.GHAST.getWidth() * GhastUnitRenderer.SCALE_MULT,
                            EntityType.GHAST.getHeight() * GhastUnitRenderer.SCALE_MULT)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ghast_unit").toString()));

    public static final RegistryObject<EntityType<MagmaCubeUnit>> MAGMA_CUBE_UNIT = ENTITIES.register("magma_cube_unit",
            () -> EntityType.Builder.of(MagmaCubeUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.MAGMA_CUBE.getWidth(), EntityType.MAGMA_CUBE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "magma_cube_unit").toString()));

    public static final RegistryObject<EntityType<SlimeUnit>> SLIME_UNIT = ENTITIES.register("slime_unit",
            () -> EntityType.Builder.of(SlimeUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SLIME.getWidth(), EntityType.SLIME.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "slime_unit").toString()));

    public static final RegistryObject<EntityType<RoyalGuardUnit>> ROYAL_GUARD_UNIT = ENTITIES.register("royal_guard_unit",
            () -> EntityType.Builder.of(RoyalGuardUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(),
                            EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "royal_guard_unit").toString()));

    public static final RegistryObject<EntityType<EnchanterUnit>> ENCHANTER_UNIT = ENTITIES.register("enchanter_unit",
            () -> EntityType.Builder.of(EnchanterUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(),
                            EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "enchanter_unit").toString()));

    public static final RegistryObject<EntityType<NecromancerUnit>> NECROMANCER_UNIT = ENTITIES.register("necromancer_unit",
            () -> EntityType.Builder.of(NecromancerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SKELETON.getWidth() * NecromancerRenderer.SCALE_MULT,
                            EntityType.SKELETON.getHeight() * NecromancerRenderer.SCALE_MULT)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "necromancer_unit").toString()));

    public static final RegistryObject<EntityType<WretchedWraithUnit>> WRETCHED_WRAITH_UNIT = ENTITIES.register("wretched_wraith_unit",
            () -> EntityType.Builder.of(WretchedWraithUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.ZOMBIE.getWidth(), EntityType.ZOMBIE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wretched_wraith_unit").toString()));

    public static final RegistryObject<EntityType<PiglinMerchantUnit>> PIGLIN_MERCHANT_UNIT = ENTITIES.register("piglin_merchant_unit",
            () -> EntityType.Builder.of(PiglinMerchantUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.IRON_GOLEM.getWidth(), EntityType.IRON_GOLEM.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "piglin_merchant_unit").toString()));

    public static final RegistryObject<EntityType<WildfireUnit>> WILDFIRE_UNIT = ENTITIES.register("wildfire_unit",
            () -> EntityType.Builder.of(WildfireUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.BLAZE.getWidth() * WildfireRenderer.SCALE_MULT,
                            EntityType.BLAZE.getHeight() * WildfireRenderer.SCALE_MULT)
                    .fireImmune()
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wildfire_unit").toString()));

    public static final RegistryObject<EntityType<PolarBearUnit>> POLAR_BEAR_UNIT = ENTITIES.register("polar_bear_unit",
            () -> EntityType.Builder.of(PolarBearUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.POLAR_BEAR.getWidth(), EntityType.POLAR_BEAR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "polar_bear_unit").toString()));

    public static final RegistryObject<EntityType<GrizzlyBearUnit>> GRIZZLY_BEAR_UNIT = ENTITIES.register("grizzly_bear_unit",
            () -> EntityType.Builder.of(GrizzlyBearUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.POLAR_BEAR.getWidth(), EntityType.POLAR_BEAR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "grizzly_bear_unit").toString()));

    public static final RegistryObject<EntityType<PandaUnit>> PANDA_UNIT = ENTITIES.register("panda_unit",
            () -> EntityType.Builder.of(PandaUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PANDA.getWidth(), EntityType.PANDA.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "panda_unit").toString()));

    public static final RegistryObject<EntityType<WolfUnit>> WOLF_UNIT = ENTITIES.register("wolf_unit",
            () -> EntityType.Builder.of(WolfUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.WOLF.getWidth(), EntityType.WOLF.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wolf_unit").toString()));

    public static final RegistryObject<EntityType<LlamaUnit>> LLAMA_UNIT = ENTITIES.register("llama_unit",
            () -> EntityType.Builder.of(LlamaUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.LLAMA.getWidth(), EntityType.LLAMA.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "llama_unit").toString()));

    public static final RegistryObject<EntityType<BeeUnit>> BEE_UNIT = ENTITIES.register("bee_unit",
            () -> EntityType.Builder.of(BeeUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.BEE.getWidth(), EntityType.BEE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bee_unit").toString()));

    public static final RegistryObject<EntityType<PhantomSummon>> PHANTOM_SUMMON = ENTITIES.register("phantom_summon",
            () -> EntityType.Builder.of(PhantomSummon::new, MobCategory.MONSTER)
                    .sized(EntityType.PHANTOM.getWidth(), EntityType.PHANTOM.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "phantom_summon").toString()));

    public static final RegistryObject<EntityType<HeroExperienceOrb>> HERO_EXPERIENCE_ORB = ENTITIES.register("hero_experience_orb",
            () -> EntityType.Builder.of(HeroExperienceOrb::new, MobCategory.MISC)
                    .sized(EntityType.EXPERIENCE_ORB.getWidth(), EntityType.EXPERIENCE_ORB.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hero_experience_orb").toString()));

    public static final RegistryObject<EntityType<KillerRabbitUnit>> KILLER_RABBIT_UNIT = ENTITIES.register("killer_rabbit_unit",
            () -> EntityType.Builder.of(KillerRabbitUnit::new, MobCategory.MISC)
                    .sized(EntityType.RABBIT.getWidth(), EntityType.RABBIT.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "killer_rabbit_unit").toString()));

    public static final RegistryObject<EntityType<ThrowableTntProjectile>> THROWABLE_TNT_PROJECTILE = ENTITIES.register("tnt_throwable_projectile",
            () -> EntityType.Builder.<ThrowableTntProjectile>of(ThrowableTntProjectile::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tnt_throwable_projectile").toString()));

    public static final RegistryObject<EntityType<ThrownHeroExperienceBottle>> THROWN_HERO_EXPERIENCE_BOTTLE = ENTITIES.register("thrown_hero_experience_bottle",
            () -> EntityType.Builder.<ThrownHeroExperienceBottle>of(ThrownHeroExperienceBottle::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thrown_hero_experience_bottle").toString()));

    public static final RegistryObject<EntityType<AdjustablePrimedTnt>> ADJUSTABLE_PRIMED_TNT = ENTITIES.register("adjustable_primed_tnt",
            () -> EntityType.Builder.<AdjustablePrimedTnt>of(AdjustablePrimedTnt::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.98F, 0.98F)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "adjustable_primed_tnt").toString()));

    public static final RegistryObject<EntityType<NecromancerProjectile>> NECROMANCER_PROJECTILE = ENTITIES.register("necromancer_projectile",
            () -> EntityType.Builder.<NecromancerProjectile>of(NecromancerProjectile::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "necromancer_projectile").toString()));

    public static final RegistryObject<EntityType<WindcallerProjectile>> WINDCALLER_PROJECTILE = ENTITIES.register("windcaller_projectile",
            () -> EntityType.Builder.<WindcallerProjectile>of(WindcallerProjectile::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "windcaller_projectile").toString()));

    public static final RegistryObject<EntityType<WraithSnowball>> WRAITH_SNOWBALL = ENTITIES.register("wraith_snowball",
            () -> EntityType.Builder.of(WraithSnowball::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wraith_snowball").toString()));

    public static final RegistryObject<EntityType<MoltenBombProjectile>> MOLTEN_BOMB_PROJECTILE = ENTITIES.register("molten_bomb_projectile",
            () -> EntityType.Builder.<MoltenBombProjectile>of(MoltenBombProjectile::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "molten_bomb_projectile").toString()));

    public static final RegistryObject<EntityType<TotemOfRegeneration>> TOTEM_OF_REGENERATION = ENTITIES.register("totem_of_regeneration",
            () -> EntityType.Builder.of(TotemOfRegeneration::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.75f, 1.25f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "totem_of_regeneration").toString()));

    public static final RegistryObject<EntityType<TotemOfCasting>> TOTEM_OF_CASTING = ENTITIES.register("totem_of_casting",
            () -> EntityType.Builder.of(TotemOfCasting::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.75f, 1.25f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "totem_of_casting").toString()));

    public static final RegistryObject<EntityType<TotemOfProtection>> TOTEM_OF_PROTECTION = ENTITIES.register("totem_of_protection",
            () -> EntityType.Builder.of(TotemOfProtection::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.75f, 1.25f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "totem_of_protection").toString()));

    public static final RegistryObject<EntityType<TotemOfShielding>> TOTEM_OF_SHIELDING = ENTITIES.register("totem_of_shielding",
            () -> EntityType.Builder.of(TotemOfShielding::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.75f, 1.25f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "totem_of_shielding").toString()));


    // Brassworks slice 2: the Assembly Works' T2 (design/brassworks_plan.md)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.EngineerCaptainUnit>> ENGINEER_CAPTAIN_UNIT = ENTITIES.register("engineer_captain_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.EngineerCaptainUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "engineer_captain_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.SteamJuggernautUnit>> STEAM_JUGGERNAUT_UNIT = ENTITIES.register("steam_juggernaut_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.SteamJuggernautUnit::new, MobCategory.CREATURE)
                    .sized(1.5f, 1.8f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "steam_juggernaut_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.TeslaWalkerUnit>> TESLA_WALKER_UNIT = ENTITIES.register("tesla_walker_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.TeslaWalkerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tesla_walker_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.GyrocopterGunshipUnit>> GYROCOPTER_GUNSHIP_UNIT = ENTITIES.register("gyrocopter_gunship_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.GyrocopterGunshipUnit::new, MobCategory.CREATURE)
                    .sized(0.9f, 1.2f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gyrocopter_gunship_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit>> DRILL_BURROWER_UNIT = ENTITIES.register("drill_burrower_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit::new, MobCategory.CREATURE)
                    .sized(1.0f, 0.75f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "drill_burrower_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.RepairCrawlerUnit>> REPAIR_CRAWLER_UNIT = ENTITIES.register("repair_crawler_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.RepairCrawlerUnit::new, MobCategory.CREATURE)
                    .sized(0.9f, 0.6f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "repair_crawler_unit").toString()));

    // the Brassworks essence caster (design/essence_casters.md): the Tinker's illager body
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.SoulEngineerUnit>> SOUL_ENGINEER_UNIT = ENTITIES.register("soul_engineer_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.SoulEngineerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "soul_engineer_unit").toString()));
    // its Ghost Furnace: a still, targetable summon on a totem's plumbing (Revision 1: 150 HP)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.GhostFurnaceUnit>> GHOST_FURNACE_UNIT = ENTITIES.register("ghost_furnace_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.GhostFurnaceUnit::new, MobCategory.MISC)
                    .sized(0.95f, 1.9f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .updateInterval(10)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ghost_furnace_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.ClockworkSpiderUnit>> CLOCKWORK_SPIDER_UNIT = ENTITIES.register("clockwork_spider_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.ClockworkSpiderUnit::new, MobCategory.CREATURE)
                    .sized(1.4f, 0.9f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "clockwork_spider_unit").toString()));

    // Hero slice 2 (design/hero_mode.md): one faction hero per faction, each on an existing unit's body
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.heroes.DawnPaladinUnit>> DAWN_PALADIN_UNIT = ENTITIES.register("dawn_paladin_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.heroes.DawnPaladinUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "dawn_paladin_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.heroes.GravecallerUnit>> GRAVECALLER_UNIT = ENTITIES.register("gravecaller_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.heroes.GravecallerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.STRAY.getWidth(), EntityType.STRAY.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gravecaller_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.heroes.HordeWarchiefUnit>> HORDE_WARCHIEF_UNIT = ENTITIES.register("horde_warchief_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.heroes.HordeWarchiefUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PIGLIN_BRUTE.getWidth(), EntityType.PIGLIN_BRUTE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "horde_warchief_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.heroes.AshShamanUnit>> ASH_SHAMAN_UNIT = ENTITIES.register("ash_shaman_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.heroes.AshShamanUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PIGLIN_BRUTE.getWidth(), EntityType.PIGLIN_BRUTE.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ash_shaman_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.heroes.ThornheartDruidUnit>> THORNHEART_DRUID_UNIT = ENTITIES.register("thornheart_druid_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.heroes.ThornheartDruidUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.SKELETON.getWidth(), EntityType.SKELETON.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "thornheart_druid_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.heroes.CorsairCaptainUnit>> CORSAIR_CAPTAIN_UNIT = ENTITIES.register("corsair_captain_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.heroes.CorsairCaptainUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "corsair_captain_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.heroes.StormWitchUnit>> STORM_WITCH_UNIT = ENTITIES.register("storm_witch_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.heroes.StormWitchUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "storm_witch_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.heroes.ArchEngineerUnit>> ARCH_ENGINEER_UNIT = ENTITIES.register("arch_engineer_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.heroes.ArchEngineerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PILLAGER.getWidth(), EntityType.PILLAGER.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "arch_engineer_unit").toString()));
    // Brassworks slice 4: the Iron Leviathan Engine - the ravager body at IronLeviathanEngineUnit.SCALE (1.95 x 2.2 wild,
    // so 3.1 x 3.5: the Leviathan's width) - and the clockwork drones it launches (the parrot body at a drone's size)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit>> IRON_LEVIATHAN_ENGINE_UNIT = ENTITIES.register("iron_leviathan_engine_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit::new, MobCategory.CREATURE)
                    .sized(3.1f, 3.5f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_leviathan_engine_unit").toString()));

    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit>> CLOCKWORK_DRONE_UNIT = ENTITIES.register("clockwork_drone_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit::new, MobCategory.CREATURE)
                    .sized(0.5f, 0.6f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "clockwork_drone_unit").toString()));
    // big-units (design/big_units.md): the T2 beasts
    // Alex's Mobs' elephant (RenderElephant), tusked: a 2.6-wide body - the T2 beasts are meant to be big
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantUnit>> ORIFLAMME_ELEPHANT_UNIT = ENTITIES.register("oriflamme_elephant_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantUnit::new, MobCategory.CREATURE)
                    .sized(2.6f, 3.6f)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "oriflamme_elephant_unit").toString()));
    // Alex's Mobs' rhinoceros (RenderRhinoceros) drawn x1.35
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit>> GOREHORN_RHINO_UNIT = ENTITIES.register("gorehorn_rhino_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit::new, MobCategory.CREATURE)
                    .sized(1.6f * com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit.SCALE, 1.6f * com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "gorehorn_rhino_unit").toString()));
    // the zoglin at x1.5; the hitbox grows with its Bone Stacks (getDimensions)
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit>> OSSUARY_BEHEMOTH_UNIT = ENTITIES.register("ossuary_behemoth_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.ZOGLIN.getWidth() * com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit.BASE_SCALE, EntityType.ZOGLIN.getHeight() * com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothUnit.BASE_SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ossuary_behemoth_unit").toString()));
    // Alex's Mobs' bison (RenderBison) drawn x1.3
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit>> MOSSBACK_BISON_UNIT = ENTITIES.register("mossback_bison_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit::new, MobCategory.CREATURE)
                    .sized(1.5f * com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit.SCALE, 1.8f * com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mossback_bison_unit").toString()));
    // beasts-2 (design/big_units.md): the other three T2 beasts
    // the vanilla sea turtle (1.2 x 0.4) drawn x2.4; the box is taller than the shell to cover the fort on its back
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit>> SHELLBACK_TURTLE_UNIT = ENTITIES.register("shellback_turtle_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit::new, MobCategory.CREATURE)
                    .sized(1.2f * com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit.SCALE, 0.8f * com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shellback_turtle_unit").toString()));
    // the vanilla horse (1.3965 x 1.6) drawn x1.5
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit>> JADE_QILIN_UNIT = ENTITIES.register("jade_qilin_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit::new, MobCategory.CREATURE)
                    .sized(1.3965f * com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit.SCALE, 1.6f * com.solegendary.reignofnether.unit.units.storm.JadeQilinUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "jade_qilin_unit").toString()));
    // the vanilla sniffer (1.9 x 1.75) drawn x1.25
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit>> STEAM_HAULER_UNIT = ENTITIES.register("steam_hauler_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit::new, MobCategory.CREATURE)
                    .sized(1.9f * com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit.SCALE, 1.75f * com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit.SCALE)
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "steam_hauler_unit").toString()));

    // combat engineers (design/combat_engineers.md): one per faction, trained at the T2 labs; six illager bodies, the
    // Horde's on the Grunt's piglin body
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.engineers.FieldMasonUnit>> FIELD_MASON_UNIT = ENTITIES.register("field_mason_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.engineers.FieldMasonUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "field_mason_unit").toString()));
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.engineers.BonesetterUnit>> BONESETTER_UNIT = ENTITIES.register("bonesetter_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.engineers.BonesetterUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bonesetter_unit").toString()));
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.engineers.WreckerUnit>> WRECKER_UNIT = ENTITIES.register("wrecker_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.engineers.WreckerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.PIGLIN.getWidth(), EntityType.PIGLIN.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wrecker_unit").toString()));
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.engineers.RootwardenUnit>> ROOTWARDEN_UNIT = ENTITIES.register("rootwarden_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.engineers.RootwardenUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rootwarden_unit").toString()));
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.engineers.SalvagerUnit>> SALVAGER_UNIT = ENTITIES.register("salvager_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.engineers.SalvagerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "salvager_unit").toString()));
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.engineers.JadeMenderUnit>> JADE_MENDER_UNIT = ENTITIES.register("jade_mender_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.engineers.JadeMenderUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "jade_mender_unit").toString()));
    public static final RegistryObject<EntityType<com.solegendary.reignofnether.unit.units.engineers.PioneerUnit>> PIONEER_UNIT = ENTITIES.register("pioneer_unit",
            () -> EntityType.Builder.of(com.solegendary.reignofnether.unit.units.engineers.PioneerUnit::new, MobCategory.CREATURE)
                    .sized(EntityType.VINDICATOR.getWidth(), EntityType.VINDICATOR.getHeight())
                    .clientTrackingRange(UNIT_CLIENT_TRACKING_RANGE)
                    .build(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "pioneer_unit").toString()));

    public static void init(FMLJavaModLoadingContext context) {
        ENTITIES.register(context.getModEventBus());
    }

    @Nullable
    public static EntityType<? extends Mob> getEntityType(String unitName) {
        return switch(unitName) {
            case CreeperProd.itemName -> EntityRegistrar.CREEPER_UNIT.get();
            case SkeletonProd.itemName -> EntityRegistrar.SKELETON_UNIT.get();
            case ZombieProd.itemName -> EntityRegistrar.ZOMBIE_UNIT.get();
            case StrayProd.itemName -> EntityRegistrar.STRAY_UNIT.get();
            case BoggedProd.itemName -> EntityRegistrar.BOGGED_UNIT.get();
            case HuskProd.itemName -> EntityRegistrar.HUSK_UNIT.get();
            case DrownedProd.itemName -> EntityRegistrar.DROWNED_UNIT.get();
            case SpiderProd.itemName -> EntityRegistrar.SPIDER_UNIT.get();
            case PoisonSpiderProd.itemName -> EntityRegistrar.POISON_SPIDER_UNIT.get();
            case WraithProd.itemName -> EntityRegistrar.WRAITH_UNIT.get();
            case VillagerProd.itemName -> EntityRegistrar.VILLAGER_UNIT.get();
            case ScoutDogProd.itemName -> EntityRegistrar.SCOUT_DOG_UNIT.get();
            case ScoutCatProd.itemName -> EntityRegistrar.SCOUT_CAT_UNIT.get();
            case ZombieVillagerProd.itemName -> EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get();
            case BatProd.itemName -> EntityRegistrar.BAT_UNIT.get();
            case VindicatorProd.itemName -> EntityRegistrar.VINDICATOR_UNIT.get();
            case PillagerProd.itemName -> EntityRegistrar.PILLAGER_UNIT.get();
            case WindcallerProd.itemName -> EntityRegistrar.WINDCALLER_UNIT.get();
            case IronGolemProd.itemName -> EntityRegistrar.IRON_GOLEM_UNIT.get();
            case com.solegendary.reignofnether.unit.units.villagers.SunColossusProd.itemName -> EntityRegistrar.SUN_COLOSSUS_UNIT.get();
            case com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerProd.itemName -> EntityRegistrar.WORLD_TREE_WALKER_UNIT.get();
            case SeedshaperProd.itemName -> EntityRegistrar.SEEDSHAPER_UNIT.get();
            case FoxCourierProd.itemName -> EntityRegistrar.FOX_COURIER_UNIT.get();
            case MoonwellBearerProd.itemName -> EntityRegistrar.MOONWELL_BEARER_UNIT.get();
            case OwlWatcherProd.itemName -> EntityRegistrar.OWL_WATCHER_UNIT.get();
            case HiveKeeperProd.itemName -> EntityRegistrar.HIVE_KEEPER_UNIT.get();
            case LeafbladeProd.itemName -> EntityRegistrar.LEAFBLADE_UNIT.get();
            case ThornbowProd.itemName -> EntityRegistrar.THORNBOW_UNIT.get();
            case SentinelTreantProd.itemName -> EntityRegistrar.SENTINEL_TREANT_UNIT.get();
            case WitchProd.itemName -> EntityRegistrar.WITCH_UNIT.get();
            case EvokerProd.itemName -> EntityRegistrar.EVOKER_UNIT.get();
            case SlimeProd.itemName -> EntityRegistrar.SLIME_UNIT.get();
            case WardenProd.itemName -> EntityRegistrar.WARDEN_UNIT.get();
            case RavagerProd.itemName -> EntityRegistrar.RAVAGER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.piglins.BonewrightProd.itemName -> EntityRegistrar.BONEWRIGHT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.monsters.EmbalmerProd.itemName -> EntityRegistrar.EMBALMER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectProd.itemName -> EntityRegistrar.ROYAL_ARCHITECT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.verdant.ElderDruidProd.itemName -> EntityRegistrar.ELDER_DRUID_UNIT.get();
            case com.solegendary.reignofnether.unit.units.verdant.StagLancerProd.itemName -> EntityRegistrar.STAG_LANCER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.verdant.ShadeRangerProd.itemName -> EntityRegistrar.SHADE_RANGER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.verdant.ElderTreantProd.itemName -> EntityRegistrar.ELDER_TREANT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.verdant.WispChoirProd.itemName -> EntityRegistrar.WISP_CHOIR_UNIT.get();
            case com.solegendary.reignofnether.unit.units.verdant.BloomPriestessProd.itemName -> EntityRegistrar.BLOOM_PRIESTESS_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.ShipwrightProd.itemName -> EntityRegistrar.SHIPWRIGHT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.GullSpotterProd.itemName -> EntityRegistrar.GULL_SPOTTER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.CutlassRaiderProd.itemName -> EntityRegistrar.CUTLASS_RAIDER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.ReefGuardProd.itemName -> EntityRegistrar.REEF_GUARD_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.BombardCrewProd.itemName -> EntityRegistrar.BOMBARD_CREW_UNIT.get();
            case com.solegendary.reignofnether.unit.units.shared.SiegeLobberProd.SUNFIRE_MANGONEL -> EntityRegistrar.SUNFIRE_MANGONEL_UNIT.get();
            case com.solegendary.reignofnether.unit.units.shared.SiegeLobberProd.SKULL_HURLER -> EntityRegistrar.SKULL_HURLER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.shared.SiegeLobberProd.BOULDER_CHUCKER -> EntityRegistrar.BOULDER_CHUCKER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.shared.SiegeLobberProd.BURL_SLINGER -> EntityRegistrar.BURL_SLINGER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.shared.SiegeLobberProd.POWDER_MONKEY -> EntityRegistrar.POWDER_MONKEY_UNIT.get();
            case com.solegendary.reignofnether.unit.units.shared.SiegeLobberProd.THUNDERSTONE_MONK -> EntityRegistrar.THUNDERSTONE_MONK_UNIT.get();
            case com.solegendary.reignofnether.unit.units.shared.SiegeLobberProd.STEAM_LOBBER -> EntityRegistrar.STEAM_LOBBER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.TidePriestProd.itemName -> EntityRegistrar.TIDE_PRIEST_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.HarpoonerProd.itemName -> EntityRegistrar.HARPOONER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.FlakSwivelProd.itemName -> EntityRegistrar.FLAK_SWIVEL_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.MudskipperSloopProd.itemName -> EntityRegistrar.MUDSKIPPER_SLOOP_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.HarbourmasterProd.itemName -> EntityRegistrar.HARBOURMASTER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.KrakenCallerProd.itemName -> EntityRegistrar.KRAKEN_CALLER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.MortarGalleyProd.itemName -> EntityRegistrar.MORTAR_GALLEY_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.SkyCorsairProd.itemName -> EntityRegistrar.SKY_CORSAIR_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.LeviathanProd.itemName -> EntityRegistrar.LEVIATHAN_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.GeomancerProd.itemName -> EntityRegistrar.GEOMANCER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.PaperCraneProd.itemName -> EntityRegistrar.PAPER_CRANE_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.BladeDancerProd.itemName -> EntityRegistrar.BLADE_DANCER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.FireAdeptProd.itemName -> EntityRegistrar.FIRE_ADEPT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.JadeGuardProd.itemName -> EntityRegistrar.JADE_GUARD_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.WindMonkProd.itemName -> EntityRegistrar.WIND_MONK_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.InkScribeProd.itemName -> EntityRegistrar.INK_SCRIBE_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.FrostAdeptProd.itemName -> EntityRegistrar.FROST_ADEPT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.RocketBatteryProd.itemName -> EntityRegistrar.ROCKET_BATTERY_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.LeyMasterProd.itemName -> EntityRegistrar.LEY_MASTER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.ThunderDragonProd.itemName -> EntityRegistrar.THUNDER_DRAGON_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.EarthshakerProd.itemName -> EntityRegistrar.EARTHSHAKER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.GravityMonkProd.itemName -> EntityRegistrar.GRAVITY_MONK_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.MirrorMageProd.itemName -> EntityRegistrar.MIRROR_MAGE_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.SparkSageProd.itemName -> EntityRegistrar.SPARK_SAGE_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.SeerProd.itemName -> EntityRegistrar.SEER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.LanternFortressProd.itemName -> EntityRegistrar.LANTERN_FORTRESS_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.PhoenixProd.itemName -> EntityRegistrar.PHOENIX_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.CelestialDragonProd.itemName -> EntityRegistrar.CELESTIAL_DRAGON_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.TinkerProd.itemName -> EntityRegistrar.TINKER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.GyroScoutProd.itemName -> EntityRegistrar.GYRO_SCOUT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.SteamRunnerProd.itemName -> EntityRegistrar.STEAM_RUNNER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.RifleSquadProd.itemName -> EntityRegistrar.RIFLE_SQUAD_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.IronWardenProd.itemName -> EntityRegistrar.IRON_WARDEN_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.FlakCartProd.itemName -> EntityRegistrar.FLAK_CART_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.MortarCrawlerProd.itemName -> EntityRegistrar.MORTAR_CRAWLER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.ShieldProjectorProd.itemName -> EntityRegistrar.SHIELD_PROJECTOR_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.SapperProd.itemName -> EntityRegistrar.SAPPER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.piglins.WarMammothProd.itemName -> EntityRegistrar.WAR_MAMMOTH_UNIT.get();
            case GruntProd.itemName -> EntityRegistrar.GRUNT_UNIT.get();
            case StriderProd.itemName -> EntityRegistrar.STRIDER_UNIT.get();
            case BruteProd.itemName -> EntityRegistrar.BRUTE_UNIT.get();
            case HeadhunterProd.itemName -> EntityRegistrar.HEADHUNTER_UNIT.get();
            case MarauderProd.itemName -> EntityRegistrar.MARAUDER_UNIT.get();
            case HoglinProd.itemName -> EntityRegistrar.HOGLIN_UNIT.get();
            case BlazeProd.itemName -> EntityRegistrar.BLAZE_UNIT.get();
            case WitherSkeletonProd.itemName -> EntityRegistrar.WITHER_SKELETON_UNIT.get();
            case MagmaCubeProd.itemName -> EntityRegistrar.MAGMA_CUBE_UNIT.get();
            case GhastProd.itemName -> EntityRegistrar.GHAST_UNIT.get();
            case com.solegendary.reignofnether.unit.units.monsters.BoneDragonProd.itemName -> EntityRegistrar.BONE_DRAGON_UNIT.get();
            case NecromancerProd.itemName -> EntityRegistrar.NECROMANCER_UNIT.get();
            case PiglinMerchantProd.itemName -> EntityRegistrar.PIGLIN_MERCHANT_UNIT.get();
            case WildfireProd.itemName -> EntityRegistrar.WILDFIRE_UNIT.get();
            case RoyalGuardProd.itemName -> EntityRegistrar.ROYAL_GUARD_UNIT.get();
            case EnchanterProd.itemName -> EntityRegistrar.ENCHANTER_UNIT.get();
            case WretchedWraithProd.itemName -> EntityRegistrar.WRETCHED_WRAITH_UNIT.get();
            case EndermanProd.itemName -> EntityRegistrar.ENDERMAN_UNIT.get();
            case ZombiePiglinProd.itemName -> EntityRegistrar.ZOMBIE_PIGLIN_UNIT.get();
            case ZoglinProd.itemName -> EntityRegistrar.ZOGLIN_UNIT.get();
            case PolarBearProd.itemName -> EntityRegistrar.POLAR_BEAR_UNIT.get();
            case GrizzlyBearProd.itemName -> EntityRegistrar.GRIZZLY_BEAR_UNIT.get();
            case PandaProd.itemName -> EntityRegistrar.PANDA_UNIT.get();
            case WolfProd.itemName -> EntityRegistrar.WOLF_UNIT.get();
            case LlamaProd.itemName -> EntityRegistrar.LLAMA_UNIT.get();
            case BeeProd.itemName -> EntityRegistrar.BEE_UNIT.get();
            case "Killer Rabbit" -> EntityRegistrar.KILLER_RABBIT_UNIT.get();
            case "Militia" -> EntityRegistrar.MILITIA_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.EngineerCaptainProd.itemName -> EntityRegistrar.ENGINEER_CAPTAIN_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.SteamJuggernautProd.itemName -> EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.TeslaWalkerProd.itemName -> EntityRegistrar.TESLA_WALKER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.GyrocopterGunshipProd.itemName -> EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.DrillBurrowerProd.itemName -> EntityRegistrar.DRILL_BURROWER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.RepairCrawlerProd.itemName -> EntityRegistrar.REPAIR_CRAWLER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.SoulEngineerProd.itemName -> EntityRegistrar.SOUL_ENGINEER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.ClockworkSpiderProd.itemName -> EntityRegistrar.CLOCKWORK_SPIDER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineProd.itemName -> EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get();
            case com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantProd.itemName -> EntityRegistrar.ORIFLAMME_ELEPHANT_UNIT.get();
            case com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoProd.itemName -> EntityRegistrar.GOREHORN_RHINO_UNIT.get();
            case com.solegendary.reignofnether.unit.units.monsters.OssuaryBehemothProd.itemName -> EntityRegistrar.OSSUARY_BEHEMOTH_UNIT.get();
            case com.solegendary.reignofnether.unit.units.verdant.MossbackBisonProd.itemName -> EntityRegistrar.MOSSBACK_BISON_UNIT.get();
            case com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleProd.itemName -> EntityRegistrar.SHELLBACK_TURTLE_UNIT.get();
            case com.solegendary.reignofnether.unit.units.storm.JadeQilinProd.itemName -> EntityRegistrar.JADE_QILIN_UNIT.get();
            case com.solegendary.reignofnether.unit.units.brass.SteamHaulerProd.itemName -> EntityRegistrar.STEAM_HAULER_UNIT.get();
            // combat engineers (design/combat_engineers.md)
            case com.solegendary.reignofnether.unit.units.engineers.FieldMasonProd.itemName -> EntityRegistrar.FIELD_MASON_UNIT.get();
            case com.solegendary.reignofnether.unit.units.engineers.BonesetterProd.itemName -> EntityRegistrar.BONESETTER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.engineers.WreckerProd.itemName -> EntityRegistrar.WRECKER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.engineers.RootwardenProd.itemName -> EntityRegistrar.ROOTWARDEN_UNIT.get();
            case com.solegendary.reignofnether.unit.units.engineers.SalvagerProd.itemName -> EntityRegistrar.SALVAGER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.engineers.JadeMenderProd.itemName -> EntityRegistrar.JADE_MENDER_UNIT.get();
            case com.solegendary.reignofnether.unit.units.engineers.PioneerProd.itemName -> EntityRegistrar.PIONEER_UNIT.get();
            // the faction heroes resolve through their kits (hero/HeroKits): one place for the seven names
            default -> com.solegendary.reignofnether.hero.HeroTypes.byItemName(unitName);
        };
    }
}