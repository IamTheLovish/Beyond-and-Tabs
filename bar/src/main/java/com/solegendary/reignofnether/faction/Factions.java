package com.solegendary.reignofnether.faction;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import com.solegendary.reignofnether.survival.spawners.IllagerWaveSpawner;
import com.solegendary.reignofnether.survival.spawners.MonsterWaveSpawner;
import com.solegendary.reignofnether.survival.spawners.PiglinWaveSpawner;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerProd;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.HashMap;

public class Factions {
	
	public static final ArrayList<ResourceLocation> CLASSIC_FACTIONS = new ArrayList<>();
	public static final ArrayList<ResourceLocation> SURVIVAL_FACTIONS = new ArrayList<>();
	public static final ArrayList<ResourceLocation> PLAYABLE_FACTIONS = new ArrayList<>();
	
	public static final HashMap<ResourceLocation, ResourceLocation> ENTITY_FACTION = new HashMap<>();
	// entity type -> faction, filled on first use. getFaction(Unit) was three hash lookups (registry key, ENTITY_FACTION,
	// FACTIONS) and runs per unit in the momentum / formation / trait passes, targeting and the HUD (CR-10). Cleared
	// whenever an entity is (re)registered, so it can never disagree with ENTITY_FACTION.
	private static final java.util.Map<EntityType<?>, Faction> FACTION_BY_TYPE = new java.util.concurrent.ConcurrentHashMap<>();
	
	public static Faction VILLAGERS;
	public static Faction MONSTERS;
	public static Faction PIGLINS;
	public static Faction NEUTRAL;
	public static Faction RANDOM;
	public static Faction NONE;
	// faction 4 of claude/design-factions.md (design/verdant_court_plan.md): playable from slice 1 (T1 only)
	public static Faction VERDANT_COURT;
	// faction 5 (design/tidewrought_plan.md): playable from slice 1 (T1 only, plus the Tides core)
	public static Faction TIDEWROUGHT;
	// faction 6 (design/stormcallers_plan.md): playable from slice 1 (T1 only, plus the Mana core)
	public static Faction STORMCALLERS;
	// faction 7 (design/brassworks_plan.md): playable from slice 1 (T1 only, plus Heat and the Field Upgrade)
	public static Faction BRASSWORKS;

	public static void register() {
		VILLAGERS = register("villagers", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/villager.png"))
			.setIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/villager.png"))
			.setSound(SoundRegistrar.VILLAGER_CALM_THEME_SONG.get())
			.setSpawnWave(IllagerWaveSpawner::spawnIllagerWave)
			.setCustomBuildingCondition((cb) -> cb.buildableByVillagers)
			.setTraits(FactionTraits.SUNFORGED)
		);
		
		MONSTERS = register("monsters", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/zombie_villager.png"))
			.setIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/creeper.png"))
			.setSound(SoundRegistrar.MONSTER_CALM_THEME_SONG.get())
			.setSpawnWave(MonsterWaveSpawner::spawnMonsterWave)
			.setCustomBuildingCondition((cb) -> cb.buildableByMonsters)
			.setTraits(FactionTraits.GRAVEBOUND)
		);
		
		PIGLINS = register("piglins", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/grunt.png"))
			.setIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/grunt.png"))
			.setSpawnWave(IllagerWaveSpawner::spawnIllagerWave)
			.setSound(SoundRegistrar.PIGLIN_CALM_THEME_SONG.get())
			.setCustomBuildingCondition((cb) -> cb.buildableByPiglins)
			.setSpawnWave(PiglinWaveSpawner::spawnPiglinWave)
			.setTraits(FactionTraits.HORDE)
		);
		
		NEUTRAL = register("neutral", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/sheep.png"))
			.noCubeMap()
		);
		
		RANDOM = register("random", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/unknown.png"))
			.setIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/question_mark_bg.png"))
			.setUnplayable()
			.noCubeMap()
		);
		
		NONE = register("none", new Faction()
			.setUnplayable()
			.noCubeMap()
		);

		// Registered last so the registry ids of the six above don't move (getFaction(int) reads them). Playable (lobby,
		// skirmish, sandbox, random, bots) but still no cube map (so the title screen and survival's random pick skip it)
		// and no spawn wave (not a survival faction) until those exist. Its calm theme is vanilla's flower-forest music
		// (SoundRegistrar.VERDANT_CALM_THEME_SONG), no longer the Kingdom's. Custom (sandbox) buildings follow the
		// Kingdom's "buildable" flag until CustomBuilding grows a Court one.
		VERDANT_COURT = register("verdant_court", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/seedshaper.png"))
			.setIcon(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_leaves.png"))
			.setSound(SoundRegistrar.VERDANT_CALM_THEME_SONG.get())
			.setCustomBuildingCondition((cb) -> cb.buildableByVillagers)
			.noCubeMap()
			.setTraits(FactionTraits.VERDANT)
		);

		// After the Court so no existing registry id moves. Live since slice 1 (design/tidewrought_plan.md): playable in
		// the lobby, skirmish (code 5), sandbox, "Random" and for bots, like the Court - but no cube map and no spawn
		// wave yet, so it stays out of the title screen and survival. Calm theme: vanilla's underwater music
		// (SoundRegistrar.TIDEWROUGHT_CALM_THEME_SONG). Custom (sandbox) buildings follow the Kingdom's "buildable" flag
		// until CustomBuilding grows a Tidewrought one. The lobby icon stays vanilla prismarine bricks (teal).
		TIDEWROUGHT = register("tidewrought", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/shipwright.png"))
			.setIcon(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/prismarine_bricks.png"))
			.setSound(SoundRegistrar.TIDEWROUGHT_CALM_THEME_SONG.get())
			.setCustomBuildingCondition((cb) -> cb.buildableByVillagers)
			.noCubeMap()
			.setTraits(FactionTraits.TIDEWROUGHT)
		);

		// After the Tidewrought so no existing registry id moves. Live since slice 1 (design/stormcallers_plan.md):
		// playable in the lobby, skirmish (code 6), sandbox, "Random" and for bots, like the Court and the Tidewrought -
		// but no cube map and no spawn wave yet, so it stays out of the title screen and survival. Calm theme: vanilla's
		// cherry grove music (SoundRegistrar.STORMCALLERS_CALM_THEME_SONG). Custom (sandbox) buildings follow the
		// Kingdom's "buildable" flag until CustomBuilding grows a Stormcallers one. The lobby icon stays vanilla red
		// glazed terracotta (red lacquer and gold).
		STORMCALLERS = register("stormcallers", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/geomancer.png"))
			.setIcon(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/red_glazed_terracotta.png"))
			.setSound(SoundRegistrar.STORMCALLERS_CALM_THEME_SONG.get())
			.setCustomBuildingCondition((cb) -> cb.buildableByVillagers)
			.noCubeMap()
			.setTraits(FactionTraits.STORMCALLERS)
		);

		// After the Stormcallers so no existing registry id moves. Live since slice 1 (design/brassworks_plan.md):
		// playable in the lobby, skirmish (code 7), sandbox, "Random" and for bots, like the three before it - but no
		// cube map and no spawn wave yet, so it stays out of the title screen and survival. Calm theme: vanilla's
		// basalt-deltas music (SoundRegistrar.BRASSWORKS_CALM_THEME_SONG). Custom (sandbox) buildings follow the
		// Kingdom's "buildable" flag until CustomBuilding grows a Brassworks one. The lobby icon is vanilla cut copper
		// (brass; waxed blocks share the unwaxed texture).
		BRASSWORKS = register("brassworks", new Faction()
			.setWorkerIcon(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/tinker.png"))
			.setIcon(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/cut_copper.png"))
			.setSound(SoundRegistrar.BRASSWORKS_CALM_THEME_SONG.get())
			.setCustomBuildingCondition((cb) -> cb.buildableByVillagers)
			.noCubeMap()
			.setTraits(FactionTraits.BRASSWORKS)
		);

		registerUnits();
		registerBuildings();
	}
	
	public static void registerUnits() {
		// Villagers
		registerWorkerEntity(VILLAGERS, EntityRegistrar.VILLAGER_UNIT.get(), ProductionItems.VILLAGER);
		registerEntity(VILLAGERS, EntityRegistrar.MILITIA_UNIT.get(), ProductionItems.VILLAGER);
		registerEntity(VILLAGERS, EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(), ProductionItems.ROYAL_ARCHITECT);
		registerScoutEntity(VILLAGERS, EntityRegistrar.SCOUT_DOG_UNIT.get(), ProductionItems.SCOUT_DOG);
		registerEntity(VILLAGERS, EntityRegistrar.SCOUT_CAT_UNIT.get(), ProductionItems.SCOUT_CAT);
		registerEntity(VILLAGERS, EntityRegistrar.VINDICATOR_UNIT.get(), ProductionItems.VINDICATOR);
		registerEntity(VILLAGERS, EntityRegistrar.PILLAGER_UNIT.get(), ProductionItems.PILLAGER);
		registerEntity(VILLAGERS, EntityRegistrar.IRON_GOLEM_UNIT.get(), ProductionItems.IRON_GOLEM);
		registerEntity(VILLAGERS, EntityRegistrar.SUN_COLOSSUS_UNIT.get(), ProductionItems.SUN_COLOSSUS);
		registerEntity(VILLAGERS, EntityRegistrar.WITCH_UNIT.get(), ProductionItems.WITCH);
		registerEntity(VILLAGERS, EntityRegistrar.EVOKER_UNIT.get(), ProductionItems.EVOKER);
		registerEntity(VILLAGERS, EntityRegistrar.WINDCALLER_UNIT.get(), ProductionItems.WINDCALLER);
		registerEntity(VILLAGERS, EntityRegistrar.RAVAGER_UNIT.get(), ProductionItems.RAVAGER);
		registerEntity(VILLAGERS, EntityRegistrar.ROYAL_GUARD_UNIT.get(), ProductionItems.ROYAL_GUARD);
		registerEntity(VILLAGERS, EntityRegistrar.ENCHANTER_UNIT.get(), ProductionItems.ENCHANTER);
		
		// Monsters
		registerWorkerEntity(MONSTERS, EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(), ProductionItems.ZOMBIE_VILLAGER);
		registerScoutEntity(MONSTERS, EntityRegistrar.BAT_UNIT.get(), ProductionItems.BAT);
		registerEntity(MONSTERS, EntityRegistrar.ZOMBIE_UNIT.get(), ProductionItems.ZOMBIE);
		registerEntity(MONSTERS, EntityRegistrar.EMBALMER_UNIT.get(), ProductionItems.EMBALMER);
		registerEntity(MONSTERS, EntityRegistrar.DROWNED_UNIT.get(), ProductionItems.DROWNED);
		registerEntity(MONSTERS, EntityRegistrar.HUSK_UNIT.get(), ProductionItems.HUSK);
		registerEntity(MONSTERS, EntityRegistrar.SKELETON_UNIT.get(), ProductionItems.SKELETON);
		registerEntity(MONSTERS, EntityRegistrar.BOGGED_UNIT.get(), ProductionItems.BOGGED);
		registerEntity(MONSTERS, EntityRegistrar.STRAY_UNIT.get(), ProductionItems.STRAY);
		registerEntity(MONSTERS, EntityRegistrar.SPIDER_UNIT.get(), ProductionItems.SPIDER);
		registerEntity(MONSTERS, EntityRegistrar.POISON_SPIDER_UNIT.get(), ProductionItems.POISON_SPIDER);
		registerEntity(MONSTERS, EntityRegistrar.CREEPER_UNIT.get(), ProductionItems.CREEPER);
		registerEntity(MONSTERS, EntityRegistrar.WRAITH_UNIT.get(), ProductionItems.WRAITH);
		registerEntity(MONSTERS, EntityRegistrar.SLIME_UNIT.get(), ProductionItems.SLIME);
		registerEntity(MONSTERS, EntityRegistrar.WARDEN_UNIT.get(), ProductionItems.WARDEN);
		registerEntity(MONSTERS, EntityRegistrar.BONE_DRAGON_UNIT.get(), ProductionItems.BONE_DRAGON);
		registerEntity(MONSTERS, EntityRegistrar.ZOMBIE_PIGLIN_UNIT.get(), ProductionItems.ZOMBIE_PIGLIN);
		registerEntity(MONSTERS, EntityRegistrar.ZOGLIN_UNIT.get(), ProductionItems.ZOGLIN);
		registerEntity(MONSTERS, EntityRegistrar.NECROMANCER_UNIT.get(), ProductionItems.NECROMANCER);
		registerEntity(MONSTERS, EntityRegistrar.WRETCHED_WRAITH_UNIT.get(), ProductionItems.WRETCHED_WRAITH);
		registerEntity(MONSTERS, EntityRegistrar.SILVERFISH_UNIT.get());
		
		// Piglins
		registerWorkerEntity(PIGLINS, EntityRegistrar.GRUNT_UNIT.get(), ProductionItems.GRUNT);
		registerScoutEntity(PIGLINS, EntityRegistrar.STRIDER_UNIT.get(), ProductionItems.STRIDER);
		registerEntity(PIGLINS, EntityRegistrar.BRUTE_UNIT.get(), ProductionItems.BRUTE);
		registerEntity(PIGLINS, EntityRegistrar.BONEWRIGHT_UNIT.get(), ProductionItems.BONEWRIGHT);
		registerEntity(PIGLINS, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), ProductionItems.WAR_MAMMOTH);
		registerEntity(PIGLINS, EntityRegistrar.MAGMA_HURLER_UNIT.get(), ProductionItems.HORDE_T3B);
		registerEntity(PIGLINS, EntityRegistrar.WORLDBREAKER_UNIT.get(), ProductionItems.WORLDBREAKER);
		registerEntity(PIGLINS, EntityRegistrar.HEADHUNTER_UNIT.get(), ProductionItems.HEADHUNTER);
		registerEntity(PIGLINS, EntityRegistrar.MARAUDER_UNIT.get(), ProductionItems.MARAUDER);
		registerEntity(PIGLINS, EntityRegistrar.HOGLIN_UNIT.get(), ProductionItems.HOGLIN);
		registerEntity(PIGLINS, EntityRegistrar.BLAZE_UNIT.get(), ProductionItems.BLAZE);
		registerEntity(PIGLINS, EntityRegistrar.WITHER_SKELETON_UNIT.get(), ProductionItems.WITHER_SKELETON);
		registerEntity(PIGLINS, EntityRegistrar.MAGMA_CUBE_UNIT.get(), ProductionItems.MAGMA_CUBE);
		registerEntity(PIGLINS, EntityRegistrar.GHAST_UNIT.get(), ProductionItems.GHAST);
		registerEntity(PIGLINS, EntityRegistrar.PIGLIN_MERCHANT_UNIT.get(), ProductionItems.PIGLIN_MERCHANT);
		registerEntity(PIGLINS, EntityRegistrar.WILDFIRE_UNIT.get(), ProductionItems.WILDFIRE);
		registerEntity(PIGLINS, EntityRegistrar.ARMOURED_HOGLIN_UNIT.get());

		// Verdant Court (slice 1: T1 only)
		registerWorkerEntity(VERDANT_COURT, EntityRegistrar.SEEDSHAPER_UNIT.get(), ProductionItems.SEEDSHAPER);
		registerScoutEntity(VERDANT_COURT, EntityRegistrar.FOX_COURIER_UNIT.get(), ProductionItems.FOX_COURIER);
		registerEntity(VERDANT_COURT, EntityRegistrar.MOONWELL_BEARER_UNIT.get(), ProductionItems.MOONWELL_BEARER);   // slice 2
		registerEntity(VERDANT_COURT, EntityRegistrar.OWL_WATCHER_UNIT.get(), ProductionItems.OWL_WATCHER);
		registerEntity(VERDANT_COURT, EntityRegistrar.HIVE_KEEPER_UNIT.get(), ProductionItems.HIVE_KEEPER);
		registerEntity(VERDANT_COURT, EntityRegistrar.LEAFBLADE_UNIT.get(), ProductionItems.LEAFBLADE);
		registerEntity(VERDANT_COURT, EntityRegistrar.THORNBOW_UNIT.get(), ProductionItems.THORNBOW);
		registerEntity(VERDANT_COURT, EntityRegistrar.SENTINEL_TREANT_UNIT.get(), ProductionItems.SENTINEL_TREANT);
		// Verdant Court T2 (slice 4: the Circle of Elders)
		registerEntity(VERDANT_COURT, EntityRegistrar.ELDER_DRUID_UNIT.get(), ProductionItems.ELDER_DRUID);
		registerEntity(VERDANT_COURT, EntityRegistrar.STAG_LANCER_UNIT.get(), ProductionItems.STAG_LANCER);
		registerEntity(VERDANT_COURT, EntityRegistrar.SHADE_RANGER_UNIT.get(), ProductionItems.SHADE_RANGER);
		registerEntity(VERDANT_COURT, EntityRegistrar.ELDER_TREANT_UNIT.get(), ProductionItems.ELDER_TREANT);
		registerEntity(VERDANT_COURT, EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), ProductionItems.WORLD_TREE_WALKER);   // T3 (Heart of the Wild)
		registerEntity(VERDANT_COURT, EntityRegistrar.WISP_CHOIR_UNIT.get(), ProductionItems.WISP_CHOIR);
		registerEntity(VERDANT_COURT, EntityRegistrar.BLOOM_PRIESTESS_UNIT.get(), ProductionItems.BLOOM_PRIESTESS);

		// Tidewrought (slice 1: T1 only)
		registerWorkerEntity(TIDEWROUGHT, EntityRegistrar.SHIPWRIGHT_UNIT.get(), ProductionItems.SHIPWRIGHT);
		registerScoutEntity(TIDEWROUGHT, EntityRegistrar.GULL_SPOTTER_UNIT.get(), ProductionItems.GULL_SPOTTER);
		registerEntity(TIDEWROUGHT, EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), ProductionItems.CUTLASS_RAIDER);
		registerEntity(TIDEWROUGHT, EntityRegistrar.REEF_GUARD_UNIT.get(), ProductionItems.REEF_GUARD);
		registerEntity(TIDEWROUGHT, EntityRegistrar.BOMBARD_CREW_UNIT.get(), ProductionItems.BOMBARD_CREW);
		registerEntity(TIDEWROUGHT, EntityRegistrar.TIDE_PRIEST_UNIT.get(), ProductionItems.TIDE_PRIEST);
		registerEntity(TIDEWROUGHT, EntityRegistrar.HARPOONER_UNIT.get(), ProductionItems.HARPOONER);
		registerEntity(TIDEWROUGHT, EntityRegistrar.FLAK_SWIVEL_UNIT.get(), ProductionItems.FLAK_SWIVEL);
		registerEntity(TIDEWROUGHT, EntityRegistrar.MUDSKIPPER_SLOOP_UNIT.get(), ProductionItems.MUDSKIPPER_SLOOP);
		// Tidewrought T2 (slice 3: the Drydock)
		registerEntity(TIDEWROUGHT, EntityRegistrar.HARBOURMASTER_UNIT.get(), ProductionItems.HARBOURMASTER);
		registerEntity(TIDEWROUGHT, EntityRegistrar.KRAKEN_CALLER_UNIT.get(), ProductionItems.KRAKEN_CALLER);
		registerEntity(TIDEWROUGHT, EntityRegistrar.MORTAR_GALLEY_UNIT.get(), ProductionItems.MORTAR_GALLEY);
		registerEntity(TIDEWROUGHT, EntityRegistrar.SKY_CORSAIR_UNIT.get(), ProductionItems.SKY_CORSAIR);
		registerEntity(TIDEWROUGHT, EntityRegistrar.LEVIATHAN_UNIT.get(), ProductionItems.LEVIATHAN);   // T3 (Abyssal Shipyard)

		// Stormcallers (slice 1: T1 only)
		registerWorkerEntity(STORMCALLERS, EntityRegistrar.GEOMANCER_UNIT.get(), ProductionItems.GEOMANCER);
		registerScoutEntity(STORMCALLERS, EntityRegistrar.PAPER_CRANE_UNIT.get(), ProductionItems.PAPER_CRANE);
		registerEntity(STORMCALLERS, EntityRegistrar.BLADE_DANCER_UNIT.get(), ProductionItems.BLADE_DANCER);
		registerEntity(STORMCALLERS, EntityRegistrar.FIRE_ADEPT_UNIT.get(), ProductionItems.FIRE_ADEPT);
		registerEntity(STORMCALLERS, EntityRegistrar.JADE_GUARD_UNIT.get(), ProductionItems.JADE_GUARD);
		registerEntity(STORMCALLERS, EntityRegistrar.WIND_MONK_UNIT.get(), ProductionItems.WIND_MONK);
		// slice 2
		registerEntity(STORMCALLERS, EntityRegistrar.INK_SCRIBE_UNIT.get(), ProductionItems.INK_SCRIBE);
		registerEntity(STORMCALLERS, EntityRegistrar.FROST_ADEPT_UNIT.get(), ProductionItems.FROST_ADEPT);
		registerEntity(STORMCALLERS, EntityRegistrar.ROCKET_BATTERY_UNIT.get(), ProductionItems.ROCKET_BATTERY);
		// Stormcallers T2 (slice 3: the Celestial Observatory)
		registerEntity(STORMCALLERS, EntityRegistrar.LEY_MASTER_UNIT.get(), ProductionItems.LEY_MASTER);
		registerEntity(STORMCALLERS, EntityRegistrar.THUNDER_DRAGON_UNIT.get(), ProductionItems.THUNDER_DRAGON);
		registerEntity(STORMCALLERS, EntityRegistrar.EARTHSHAKER_UNIT.get(), ProductionItems.EARTHSHAKER);
		registerEntity(STORMCALLERS, EntityRegistrar.GRAVITY_MONK_UNIT.get(), ProductionItems.GRAVITY_MONK);
		registerEntity(STORMCALLERS, EntityRegistrar.MIRROR_MAGE_UNIT.get(), ProductionItems.MIRROR_MAGE);
		// Stormcallers slice 4: the rest of T2 and the T3 experimental
		registerEntity(STORMCALLERS, EntityRegistrar.SEER_UNIT.get(), ProductionItems.SEER);
		registerEntity(STORMCALLERS, EntityRegistrar.LANTERN_FORTRESS_UNIT.get(), ProductionItems.LANTERN_FORTRESS);
		registerEntity(STORMCALLERS, EntityRegistrar.PHOENIX_UNIT.get(), ProductionItems.PHOENIX);
		registerEntity(STORMCALLERS, EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), ProductionItems.CELESTIAL_DRAGON);

		// Brassworks (slice 1: T1 only); the Deployed Turret is the Guildmaster's, never trained
		registerWorkerEntity(BRASSWORKS, EntityRegistrar.TINKER_UNIT.get(), ProductionItems.TINKER);
		registerScoutEntity(BRASSWORKS, EntityRegistrar.GYRO_SCOUT_UNIT.get(), ProductionItems.GYRO_SCOUT);
		registerEntity(BRASSWORKS, EntityRegistrar.STEAM_RUNNER_UNIT.get(), ProductionItems.STEAM_RUNNER);
		registerEntity(BRASSWORKS, EntityRegistrar.RIFLE_SQUAD_UNIT.get(), ProductionItems.RIFLE_SQUAD);
		registerEntity(BRASSWORKS, EntityRegistrar.IRON_WARDEN_UNIT.get(), ProductionItems.IRON_WARDEN);
		registerEntity(BRASSWORKS, EntityRegistrar.FLAK_CART_UNIT.get(), ProductionItems.FLAK_CART);
		registerEntity(BRASSWORKS, EntityRegistrar.MORTAR_CRAWLER_UNIT.get(), ProductionItems.MORTAR_CRAWLER);
		registerEntity(BRASSWORKS, EntityRegistrar.SHIELD_PROJECTOR_UNIT.get(), ProductionItems.SHIELD_PROJECTOR);
		registerEntity(BRASSWORKS, EntityRegistrar.SAPPER_UNIT.get(), ProductionItems.SAPPER);
		registerEntity(BRASSWORKS, EntityRegistrar.DEPLOYED_TURRET_UNIT.get());
		// slice 2: the Assembly Works' T2
		registerEntity(BRASSWORKS, EntityRegistrar.ENGINEER_CAPTAIN_UNIT.get(), ProductionItems.ENGINEER_CAPTAIN);
		registerEntity(BRASSWORKS, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), ProductionItems.STEAM_JUGGERNAUT);
		registerEntity(BRASSWORKS, EntityRegistrar.TESLA_WALKER_UNIT.get(), ProductionItems.TESLA_WALKER);
		registerEntity(BRASSWORKS, EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), ProductionItems.GYROCOPTER_GUNSHIP);
		registerEntity(BRASSWORKS, EntityRegistrar.DRILL_BURROWER_UNIT.get(), ProductionItems.DRILL_BURROWER);
		registerEntity(BRASSWORKS, EntityRegistrar.REPAIR_CRAWLER_UNIT.get(), ProductionItems.REPAIR_CRAWLER);
		registerEntity(BRASSWORKS, EntityRegistrar.CLOCKWORK_SPIDER_UNIT.get(), ProductionItems.CLOCKWORK_SPIDER);
		// slice 4: the Great Forge's experimental and the drones it launches (no production item: the engine makes them)
		registerEntity(BRASSWORKS, EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get(), ProductionItems.IRON_LEVIATHAN_ENGINE);
		registerEntity(BRASSWORKS, EntityRegistrar.BRASS_ZEPPELIN_UNIT.get(), ProductionItems.BRASS_T3B);
		registerEntity(BRASSWORKS, EntityRegistrar.CLOCKWORK_DRONE_UNIT.get());
		// big-units (design/big_units.md): the T2 beasts
		registerEntity(VILLAGERS, EntityRegistrar.ORIFLAMME_ELEPHANT_UNIT.get(), ProductionItems.ORIFLAMME_ELEPHANT);
		registerEntity(PIGLINS, EntityRegistrar.GOREHORN_RHINO_UNIT.get(), ProductionItems.GOREHORN_RHINO);
		registerEntity(MONSTERS, EntityRegistrar.OSSUARY_BEHEMOTH_UNIT.get(), ProductionItems.OSSUARY_BEHEMOTH);
		registerEntity(MONSTERS, EntityRegistrar.PLAGUE_ABOMINATION_UNIT.get(), ProductionItems.GRAVE_T3B);
		registerEntity(STORMCALLERS, EntityRegistrar.JADE_SENTINEL_UNIT.get(), ProductionItems.STORM_T3B);
		registerEntity(VILLAGERS, EntityRegistrar.CATHEDRAL_WARDEN_UNIT.get(), ProductionItems.SUN_T3B);
		registerEntity(TIDEWROUGHT, EntityRegistrar.ABYSSAL_HYDRA_UNIT.get(), ProductionItems.TIDE_T3B);
		registerEntity(VERDANT_COURT, EntityRegistrar.THORNWING_ROC_UNIT.get(), ProductionItems.VERDANT_T3B);
		registerEntity(VERDANT_COURT, EntityRegistrar.MOSSBACK_BISON_UNIT.get(), ProductionItems.MOSSBACK_BISON);
		// beasts-2: the other three
		registerEntity(TIDEWROUGHT, EntityRegistrar.SHELLBACK_TURTLE_UNIT.get(), ProductionItems.SHELLBACK_TURTLE);
		registerEntity(STORMCALLERS, EntityRegistrar.JADE_QILIN_UNIT.get(), ProductionItems.JADE_QILIN);
		registerEntity(BRASSWORKS, EntityRegistrar.STEAM_HAULER_UNIT.get(), ProductionItems.STEAM_HAULER);
		// combat engineers (design/combat_engineers.md): one per faction, trained at the T2 labs
		registerEntity(VILLAGERS, EntityRegistrar.FIELD_MASON_UNIT.get(), ProductionItems.FIELD_MASON);
		registerEntity(MONSTERS, EntityRegistrar.BONESETTER_UNIT.get(), ProductionItems.BONESETTER);
		registerEntity(PIGLINS, EntityRegistrar.WRECKER_UNIT.get(), ProductionItems.WRECKER);
		registerEntity(VERDANT_COURT, EntityRegistrar.ROOTWARDEN_UNIT.get(), ProductionItems.ROOTWARDEN);
		registerEntity(TIDEWROUGHT, EntityRegistrar.SALVAGER_UNIT.get(), ProductionItems.SALVAGER);
		registerEntity(STORMCALLERS, EntityRegistrar.JADE_MENDER_UNIT.get(), ProductionItems.JADE_MENDER);
		registerEntity(BRASSWORKS, EntityRegistrar.PIONEER_UNIT.get(), ProductionItems.PIONEER);
		
		// Hero slice 2 (design/hero_mode.md): one faction hero each, trained at the T1 army lab. Registered without a
		// spawn-menu button (HeroProd is not an IUnitProductionItem: a hero is not part of a lab's unit roster)
		registerEntity(VILLAGERS, EntityRegistrar.DAWN_PALADIN_UNIT.get());
		registerEntity(MONSTERS, EntityRegistrar.GRAVECALLER_UNIT.get());
		registerEntity(PIGLINS, EntityRegistrar.HORDE_WARCHIEF_UNIT.get());
		registerEntity(VERDANT_COURT, EntityRegistrar.THORNHEART_DRUID_UNIT.get());
		registerEntity(TIDEWROUGHT, EntityRegistrar.CORSAIR_CAPTAIN_UNIT.get());
		registerEntity(STORMCALLERS, EntityRegistrar.STORM_WITCH_UNIT.get());
		registerEntity(BRASSWORKS, EntityRegistrar.ARCH_ENGINEER_UNIT.get());
		// Neutral
		registerEntity(NEUTRAL, EntityRegistrar.ENDERMAN_UNIT.get(), ProductionItems.ENDERMAN);
		registerEntity(NEUTRAL, EntityRegistrar.POLAR_BEAR_UNIT.get(), ProductionItems.POLAR_BEAR);
		registerEntity(NEUTRAL, EntityRegistrar.GRIZZLY_BEAR_UNIT.get(), ProductionItems.GRIZZLY_BEAR);
		registerEntity(NEUTRAL, EntityRegistrar.PANDA_UNIT.get(), ProductionItems.PANDA);
		registerEntity(NEUTRAL, EntityRegistrar.WOLF_UNIT.get(), ProductionItems.WOLF);
		registerEntity(NEUTRAL, EntityRegistrar.LLAMA_UNIT.get(), ProductionItems.LLAMA);
	}
	
	
	public static void registerBuildings() {
		// Monsters
		registerStartBuilding(MONSTERS, Buildings.MAUSOLEUM, Keybindings.abilitySlot1);
		registerBuildings(MONSTERS, Buildings.SPRUCE_STOCKPILE, Keybindings.abilitySlot2);
		registerBuildings(MONSTERS, Buildings.METAL_EXTRACTOR_MONSTERS, Keybindings.hotkey5);
		registerBuildings(MONSTERS, Buildings.WIND_GENERATOR_MONSTERS, Keybindings.hotkey6);
		registerBuildings(MONSTERS, Buildings.ENERGY_CONVERTER_MONSTERS, Keybindings.hotkey7);
		registerBuildings(MONSTERS, Buildings.STORAGE_VAULT_MONSTERS, Keybindings.hotkey8);
		registerBuildings(MONSTERS, Buildings.SCULK_CATALYST, Keybindings.abilitySlot3);
		// no Pumpkin Farm / Haunted House (lovish, Oct 16: "the farm and village hut arent part of the game"): the old food
		// and population economy's buildings; extractors and wind generators do their jobs. Still registered (old saves).
		registerBuildings(MONSTERS, Buildings.DARK_WATCHTOWER, Keybindings.abilitySlot5);
		registerBuildings(MONSTERS, Buildings.GRAVEYARD, Keybindings.abilitySlot6);
		registerBuildings(MONSTERS, Buildings.DUNGEON, Keybindings.abilitySlot7);
		registerBuildings(MONSTERS, Buildings.SPIDER_LAIR, Keybindings.abilitySlot8);
		registerBuildings(MONSTERS, Buildings.SLIME_PIT, Keybindings.abilitySlot9);
		registerBuildings(MONSTERS, Buildings.LABORATORY, Keybindings.hotkey9);   // was P (abilitySlot10): Patrol is P now
		registerBuildings(MONSTERS, Buildings.STRONGHOLD, Keybindings.hotkey2);
		registerBuildings(MONSTERS, Buildings.ALTAR_OF_DARKNESS, Keybindings.hotkey3);
		registerBuildings(MONSTERS, Buildings.SPRUCE_BRIDGE, Keybindings.hotkey4);
		
		registerBuildings(MONSTERS, Buildings.MONSTER_MARKET, Keybindings.hotkey10);
		registerBuildings(MONSTERS, Buildings.LAB_ANNEX_MONSTERS);   // no hotkey: the build menu's keys are taken (design/building_upgrades.md)
		registerBuildings(MONSTERS, Buildings.GEOTHERMAL_PLANT_MONSTERS);   // T2 constructors, over lava (design/lively_buildings.md); no hotkey left
		registerBuildings(MONSTERS, Buildings.SOLAR_COLLECTOR_MONSTERS);   // steady energy, T1 (claude/solar-spec.md); no hotkey left
		registerBuildings(MONSTERS, Buildings.ADVANCED_SOLAR_MONSTERS);   // steady energy, T2 constructors
		registerBuildings(MONSTERS, Buildings.BEACON);
		
		//Piglins
		registerStartBuilding(PIGLINS, Buildings.CENTRAL_PORTAL, Keybindings.abilitySlot1);
		registerBuildings(PIGLINS, Buildings.PORTAL_BASIC, Keybindings.abilitySlot2);
		// no Netherwart Farm (the old food economy - see the Gravebound above)
		registerBuildings(PIGLINS, Buildings.BASTION, Keybindings.abilitySlot4);
		registerBuildings(PIGLINS, Buildings.HOGLIN_STABLES, Keybindings.abilitySlot5);
		registerBuildings(PIGLINS, Buildings.FLAME_SANCTUARY, Keybindings.abilitySlot6);
		registerBuildings(PIGLINS, Buildings.WITHER_SHRINE, Keybindings.abilitySlot7);
		registerBuildings(PIGLINS, Buildings.BASALT_SPRINGS, Keybindings.abilitySlot8);
		registerBuildings(PIGLINS, Buildings.FORTRESS, Keybindings.abilitySlot9);
		registerBuildings(PIGLINS, Buildings.INFERNAL_PORTAL, Keybindings.hotkey3);
		registerBuildings(PIGLINS, Buildings.BLACKSTONE_BRIDGE, Keybindings.hotkey4);
		registerBuildings(PIGLINS, Buildings.METAL_EXTRACTOR_PIGLINS, Keybindings.hotkey5);
		registerBuildings(PIGLINS, Buildings.WIND_GENERATOR_PIGLINS, Keybindings.hotkey6);
		registerBuildings(PIGLINS, Buildings.ENERGY_CONVERTER_PIGLINS, Keybindings.hotkey7);
		registerBuildings(PIGLINS, Buildings.STORAGE_VAULT_PIGLINS, Keybindings.hotkey8);
		registerBuildings(PIGLINS, Buildings.PIGLIN_MARKET, Keybindings.hotkey10);
		registerBuildings(PIGLINS, Buildings.LAB_ANNEX_PIGLINS);   // no hotkey: the build menu's keys are taken (design/building_upgrades.md)
		registerBuildings(PIGLINS, Buildings.GEOTHERMAL_PLANT_PIGLINS);   // T2 constructors, over lava (design/lively_buildings.md); no hotkey left
		registerBuildings(PIGLINS, Buildings.SOLAR_COLLECTOR_PIGLINS);   // steady energy, T1 (claude/solar-spec.md); no hotkey left
		registerBuildings(PIGLINS, Buildings.ADVANCED_SOLAR_PIGLINS);   // steady energy, T2 constructors
		registerBuildings(PIGLINS, Buildings.BEACON);
		registerBuilding(PIGLINS, Buildings.PORTAL_CIVILIAN);
		registerBuilding(PIGLINS, Buildings.PORTAL_POCKET);
		registerBuilding(PIGLINS, Buildings.PORTAL_MILITARY);
		registerBuilding(PIGLINS, Buildings.PORTAL_TRANSPORT);
		
		//VillagersFaction
		registerStartBuilding(VILLAGERS, Buildings.TOWN_CENTRE, Keybindings.abilitySlot1);
		registerBuildings(VILLAGERS, Buildings.OAK_STOCKPILE, Keybindings.abilitySlot2);
		registerBuildings(VILLAGERS, Buildings.METAL_EXTRACTOR_VILLAGERS, Keybindings.hotkey5);
		registerBuildings(VILLAGERS, Buildings.WIND_GENERATOR_VILLAGERS, Keybindings.hotkey6);
		registerBuildings(VILLAGERS, Buildings.ENERGY_CONVERTER_VILLAGERS, Keybindings.hotkey7);
		registerBuildings(VILLAGERS, Buildings.STORAGE_VAULT_VILLAGERS, Keybindings.hotkey8);
		// no Villager House / Wheat Farm (lovish, Oct 16 - see the Gravebound above); the Grand Market asks for Wind
		// Generators instead of houses (ResearchMarketUpgradeVillager)
		registerBuildings(VILLAGERS, Buildings.WATCHTOWER, Keybindings.abilitySlot5);
		registerBuildings(VILLAGERS, Buildings.BARRACKS, Keybindings.abilitySlot6);
		registerBuildings(VILLAGERS, Buildings.BLACKSMITH, Keybindings.abilitySlot7);
		registerBuildings(VILLAGERS, Buildings.WITCH_HUT, Keybindings.abilitySlot8);
		registerBuildings(VILLAGERS, Buildings.ARCANE_TOWER, Keybindings.abilitySlot9);
		registerBuildings(VILLAGERS, Buildings.LIBRARY, Keybindings.hotkey11);    // was P (abilitySlot10): Patrol is P now
		registerBuildings(VILLAGERS, Buildings.CASTLE, Keybindings.hotkey2);
		registerBuildings(VILLAGERS, Buildings.SHRINE_OF_PROSPERITY, Keybindings.hotkey3);
		registerBuildings(VILLAGERS, Buildings.IRON_GOLEM_BUILDING, Keybindings.hotkey9);
		registerBuildings(VILLAGERS, Buildings.OAK_BRIDGE, Keybindings.hotkey4);
		registerBuildings(VILLAGERS, Buildings.VILLAGER_MARKET, Keybindings.hotkey10);
		registerBuildings(VILLAGERS, Buildings.LAB_ANNEX_VILLAGERS);   // no hotkey: the build menu's keys are taken (design/building_upgrades.md)
		registerBuildings(VILLAGERS, Buildings.GEOTHERMAL_PLANT_VILLAGERS);   // T2 constructors, over lava (design/lively_buildings.md); no hotkey left
		registerBuildings(VILLAGERS, Buildings.SOLAR_COLLECTOR_VILLAGERS);   // steady energy, T1 (claude/solar-spec.md); no hotkey left
		registerBuildings(VILLAGERS, Buildings.ADVANCED_SOLAR_VILLAGERS);   // steady energy, T2 constructors
		registerBuildings(VILLAGERS, Buildings.BEACON);
		
		// Verdant Court: the same hotkey slots as the others (capitol slot 1, army lab slot 6, economy 5/6/7)
		registerStartBuilding(VERDANT_COURT, Buildings.HEARTWOOD_HALL, Keybindings.abilitySlot1);
		registerBuildings(VERDANT_COURT, Buildings.METAL_EXTRACTOR_VERDANT, Keybindings.hotkey5);
		registerBuildings(VERDANT_COURT, Buildings.WIND_GENERATOR_VERDANT, Keybindings.hotkey6);
		registerBuildings(VERDANT_COURT, Buildings.ENERGY_CONVERTER_VERDANT, Keybindings.hotkey7);
		// storage: H, not "." like the others - the Seedshaper's Vine Snare already has "." (hotkey8)
		registerBuildings(VERDANT_COURT, Buildings.STORAGE_VAULT_VERDANT, Keybindings.hotkey10);
		registerBuildings(VERDANT_COURT, Buildings.GROVE, Keybindings.abilitySlot6);
		registerBuildings(VERDANT_COURT, Buildings.CIRCLE_OF_ELDERS, Keybindings.abilitySlot9);   // T2 lab: the Arcane Tower's slot
		registerBuildings(VERDANT_COURT, Buildings.HEART_OF_THE_WILD, Keybindings.hotkey2);   // T3 lab: the Castle's and Stronghold's slot
		registerBuildings(VERDANT_COURT, Buildings.STORM_OAK, Keybindings.abilitySlot5);       // T2 defence: the Watchtowers' slot
		registerBuildings(VERDANT_COURT, Buildings.LAB_ANNEX_VERDANT);   // no hotkey: the build menu's keys are taken (design/building_upgrades.md)
		registerBuildings(VERDANT_COURT, Buildings.GEOTHERMAL_PLANT_VERDANT);   // T2 constructors, over lava (design/lively_buildings.md); no hotkey left
		registerBuildings(VERDANT_COURT, Buildings.SOLAR_COLLECTOR_VERDANT);   // steady energy, T1 (claude/solar-spec.md); no hotkey left
		registerBuildings(VERDANT_COURT, Buildings.ADVANCED_SOLAR_VERDANT);   // steady energy, T2 constructors

		// Tidewrought: the same hotkey slots (capitol slot 1, army lab slot 6, economy 5/6/7, T2 lab slot 9, T2 defence
		// slot 5, T3 lab hotkey 2)
		registerStartBuilding(TIDEWROUGHT, Buildings.WRECK_HARBOUR, Keybindings.abilitySlot1);
		registerBuildings(TIDEWROUGHT, Buildings.METAL_EXTRACTOR_TIDE, Keybindings.hotkey5);
		registerBuildings(TIDEWROUGHT, Buildings.WIND_GENERATOR_TIDE, Keybindings.hotkey6);
		registerBuildings(TIDEWROUGHT, Buildings.ENERGY_CONVERTER_TIDE, Keybindings.hotkey7);
		registerBuildings(TIDEWROUGHT, Buildings.STORAGE_VAULT_TIDE, Keybindings.hotkey8);
		registerBuildings(TIDEWROUGHT, Buildings.SLIPWAY, Keybindings.abilitySlot6);
		registerBuildings(TIDEWROUGHT, Buildings.DRYDOCK, Keybindings.abilitySlot9);              // T2 lab: the Arcane Tower's slot
		registerBuildings(TIDEWROUGHT, Buildings.LIGHTHOUSE_BATTERY, Keybindings.abilitySlot5);   // T2 defence: the Watchtowers' slot
		registerBuildings(TIDEWROUGHT, Buildings.ABYSSAL_SHIPYARD, Keybindings.hotkey2);          // T3 lab: the Castle's and Stronghold's slot
		registerBuildings(TIDEWROUGHT, Buildings.LAB_ANNEX_TIDE);   // no hotkey: the build menu's keys are taken (design/building_upgrades.md)
		registerBuildings(TIDEWROUGHT, Buildings.GEOTHERMAL_PLANT_TIDE);   // T2 constructors, over lava (design/lively_buildings.md); no hotkey left
		registerBuildings(TIDEWROUGHT, Buildings.SOLAR_COLLECTOR_TIDE);   // steady energy, T1 (claude/solar-spec.md); no hotkey left
		registerBuildings(TIDEWROUGHT, Buildings.ADVANCED_SOLAR_TIDE);   // steady energy, T2 constructors

		// Stormcallers: the same hotkey slots (capitol slot 1, army lab slot 6, economy 5/6/7, storage 8); no T2/T3 lab yet
		registerStartBuilding(STORMCALLERS, Buildings.JADE_PAGODA, Keybindings.abilitySlot1);
		registerBuildings(STORMCALLERS, Buildings.METAL_EXTRACTOR_STORM, Keybindings.hotkey5);
		registerBuildings(STORMCALLERS, Buildings.WIND_GENERATOR_STORM, Keybindings.hotkey6);
		registerBuildings(STORMCALLERS, Buildings.ENERGY_CONVERTER_STORM, Keybindings.hotkey7);
		// storage: H, not "." - the Geomancer's Ley Link already has "." (hotkey8); H is Hold, which workers never show
		registerBuildings(STORMCALLERS, Buildings.STORAGE_VAULT_STORM, Keybindings.hotkey10);
		registerBuildings(STORMCALLERS, Buildings.LOTUS_ACADEMY, Keybindings.abilitySlot6);
		registerBuildings(STORMCALLERS, Buildings.CELESTIAL_OBSERVATORY, Keybindings.abilitySlot9);   // T2 lab: the Arcane Tower's slot
		registerBuildings(STORMCALLERS, Buildings.HEAVENS_GATE, Keybindings.hotkey2);                // T3 lab: the Castle's and Stronghold's slot
		registerBuildings(STORMCALLERS, Buildings.LAB_ANNEX_STORM);   // no hotkey: the build menu's keys are taken (design/building_upgrades.md)
		registerBuildings(STORMCALLERS, Buildings.GEOTHERMAL_PLANT_STORM);   // T2 constructors, over lava (design/lively_buildings.md); no hotkey left
		registerBuildings(STORMCALLERS, Buildings.SOLAR_COLLECTOR_STORM);   // steady energy, T1 (claude/solar-spec.md); no hotkey left
		registerBuildings(STORMCALLERS, Buildings.ADVANCED_SOLAR_STORM);   // steady energy, T2 constructors

		// Brassworks: the same hotkey slots (capitol slot 1, army lab slot 6, economy 5/6/7, storage 8); no T2/T3 lab yet
		registerStartBuilding(BRASSWORKS, Buildings.FOUNDRY_HALL, Keybindings.abilitySlot1);
		registerBuildings(BRASSWORKS, Buildings.METAL_EXTRACTOR_BRASS, Keybindings.hotkey5);
		registerBuildings(BRASSWORKS, Buildings.WIND_GENERATOR_BRASS, Keybindings.hotkey6);
		registerBuildings(BRASSWORKS, Buildings.ENERGY_CONVERTER_BRASS, Keybindings.hotkey7);
		registerBuildings(BRASSWORKS, Buildings.STORAGE_VAULT_BRASS, Keybindings.hotkey10);   // H, like the Stormcallers: the Tinker's Field Upgrade sits on "."
		registerBuildings(BRASSWORKS, Buildings.MACHINE_SHOP, Keybindings.abilitySlot6);
		registerBuildings(BRASSWORKS, Buildings.ASSEMBLY_WORKS, Keybindings.abilitySlot9);   // T2 lab: the Arcane Tower's slot
		registerBuildings(BRASSWORKS, Buildings.RAIL_BATTERY, Keybindings.abilitySlot5);      // T2 defence: the Watchtowers' slot
		registerBuildings(BRASSWORKS, Buildings.GREAT_FORGE, Keybindings.hotkey2);           // T3 lab: the Castle's and Heaven's Gate's slot
		registerBuildings(BRASSWORKS, Buildings.LAB_ANNEX_BRASS);   // no hotkey: the build menu's keys are taken (design/building_upgrades.md)
		registerBuildings(BRASSWORKS, Buildings.GEOTHERMAL_PLANT_BRASS);   // T2 constructors, over lava (design/lively_buildings.md); no hotkey left
		registerBuildings(BRASSWORKS, Buildings.SOLAR_COLLECTOR_BRASS);   // steady energy, T1 (claude/solar-spec.md); no hotkey left
		registerBuildings(BRASSWORKS, Buildings.ADVANCED_SOLAR_BRASS);   // steady energy, T2 constructors

		// the static defences of all seven factions (design/defences.md): walls, gates, traps, the T1 anti-raid tower and
		// the anti-T3 emplacement, appended to each build menu with no hotkey (every key is taken)
		com.solegendary.reignofnether.building.buildings.defence.Defences.registerWithFactions();
		// big-units (design/big_units.md): every faction's Ward Spire (the game enders' counter) and the game enders built so
		// far; no hotkeys left in the build menus
		registerBuildings(VILLAGERS, Buildings.WARD_SPIRE_VILLAGERS);
		registerBuildings(MONSTERS, Buildings.WARD_SPIRE_MONSTERS);
		registerBuildings(PIGLINS, Buildings.WARD_SPIRE_PIGLINS);
		registerBuildings(VERDANT_COURT, Buildings.WARD_SPIRE_VERDANT);
		registerBuildings(TIDEWROUGHT, Buildings.WARD_SPIRE_TIDE);
		registerBuildings(STORMCALLERS, Buildings.WARD_SPIRE_STORM);
		registerBuildings(BRASSWORKS, Buildings.WARD_SPIRE_BRASS);
		registerBuildings(VILLAGERS, Buildings.SUN_SPIRE);
		registerBuildings(MONSTERS, Buildings.NECROPOLIS);
		// beasts-2: the other five game enders
		registerBuildings(PIGLINS, Buildings.DOOM_FORGE);
		registerBuildings(VERDANT_COURT, Buildings.WORLD_SEED);
		registerBuildings(TIDEWROUGHT, Buildings.MAELSTROM_ENGINE);
		registerBuildings(STORMCALLERS, Buildings.HEAVENS_BELL);
		registerBuildings(BRASSWORKS, Buildings.EARTHCRACKER);

		//Neutral
		registerBuildings(NEUTRAL, Buildings.CAPTURABLE_BEACON, Keybindings.abilitySlot1);
		registerBuildings(NEUTRAL, Buildings.HEALING_FOUNTAIN, Keybindings.abilitySlot2);
		registerBuildings(NEUTRAL, Buildings.END_PORTAL, Keybindings.abilitySlot3);
		registerBuildings(NEUTRAL, Buildings.NEUTRAL_TRANSPORT_PORTAL, Keybindings.abilitySlot4);
		
		if (FMLEnvironment.dist.isClient())
			for (ResourceLocation faction : PLAYABLE_FACTIONS) {
				getFaction(faction).addCustomBuildings();
			}
		
	}
	
	public static Faction register(String name, Faction faction) {
		ResourceLocation key = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, name);
		if (faction.hasCubeMap)
			CLASSIC_FACTIONS.add(key);
		if (faction.playable)
			PLAYABLE_FACTIONS.add(key);
		if (faction.spawnWave != null)
			SURVIVAL_FACTIONS.add(key);
		faction = register(key, faction).setKey(key);
		return faction;
	}
	
	public static <T extends Faction> T register(ResourceLocation key, T faction) {
		return Registry.register(ReignOfNetherRegistries.FACTIONS, key, faction);
	}
	
	public static void registerStartBuilding(Faction faction, Building building, Keybinding key) {
		registerBuildings(faction, building, key);
		faction.capitolBuilding = ReignOfNetherRegistries.BUILDING.getKey(building);
	}
	
	public static void registerBuildings(Faction faction, Building building, Keybinding key) {
		building.setFaction(faction.key);
		if (key != null)
			faction.buildHotkeys.put(building, key);
		if (FMLEnvironment.dist.isClient())
			faction.addBuilding(building, key);
	}
	
	public static void registerBuilding(Faction faction, Building building) {
		building.setFaction(faction.key);
	}
	
	public static <T extends IUnitProductionItem> void registerWorkerEntity(Faction faction, EntityType<? extends Unit> unit, T productionItem) {
		registerEntity(faction, unit, productionItem);
		faction.setWorkerEntityType(unit);
	}
	
	public static <T extends IUnitProductionItem> void registerScoutEntity(Faction faction, EntityType<? extends Unit> unit, T productionItem) {
		registerEntity(faction, unit, productionItem);
		faction.setScoutEntityType(unit);
	}
	
	public static <T extends IUnitProductionItem> void registerEntity(Faction faction, EntityType<? extends Unit> unit, T productionItem) {
		if (FMLEnvironment.dist.isClient())
			if (unit == EntityRegistrar.MILITIA_UNIT.get())
				faction.addEntityButton(((VillagerProd) productionItem).getMilitiaPlaceButton());
			else
				faction.addEntityButton(productionItem.getPlaceButton());
		ENTITY_FACTION.put(EntityType.getKey(unit), faction.key);
		FACTION_BY_TYPE.clear();
	}
	
	public static void registerEntity(Faction faction, EntityType<? extends Unit> unit) {
		ENTITY_FACTION.put(EntityType.getKey(unit), faction.key);
		FACTION_BY_TYPE.clear();
	}
	
	public static void registerBuildings(Faction faction, Building building) {
		registerBuildings(faction, building, null);
	}
	
	/**
	 * The faction with this key, or null when there is none. Packet decoders use this, not {@link #getFaction}: that one
	 * turns a key nobody has into NONE (the sandbox "faction"), so a client could make up its way into a sandbox.
	 */
	public static Faction findFaction(ResourceLocation pKey) {
		if (pKey == null) return null;
		return ReignOfNetherRegistries.FACTIONS.get(pKey);
	}

	public static Faction getFaction(ResourceLocation pKey) {
		if (pKey == null) return Factions.NONE;
		Faction faction = ReignOfNetherRegistries.FACTIONS.get(pKey);
		if (faction == null) return Factions.NONE;
		return faction; 
	}
	
	public static Faction getFaction(Unit unit) {
		EntityType<?> type = ((Entity) unit).getType();
		Faction cached = FACTION_BY_TYPE.get(type);
		if (cached != null)
			return cached;
		Faction f = getFaction(ENTITY_FACTION.get(EntityType.getKey(type)));
		if (f != null)   // NONE is null until register() ran
			FACTION_BY_TYPE.put(type, f);
		return f;
	}
	
	public static Faction getFaction(int id) {
		Faction faction = ReignOfNetherRegistries.FACTIONS.byId(id);
		if (faction == null) return Factions.NONE;
		return faction;
	}
	
	/** The factions a player can actually start as: playable, not a placeholder, and with a capitol and a worker. */
	public static boolean isLive(Faction faction) {
		return faction != null && faction.playable && !faction.preview
			&& faction.capitolBuilding != null && faction.workerEntityType != null;
	}

	/**
	 * A random faction a player can actually start as (isLive over PLAYABLE_FACTIONS): what "Random" means in the lobby,
	 * the skirmish screen and startRTS, so each new faction joins the pool the moment it goes live.
	 */
	public static Faction randomLive(java.util.Random rng) {
		java.util.List<Faction> live = new java.util.ArrayList<>();
		for (ResourceLocation rl : PLAYABLE_FACTIONS)
			if (isLive(getFaction(rl)))
				live.add(getFaction(rl));
		return live.isEmpty() ? VILLAGERS : live.get(rng.nextInt(live.size()));
	}

	public static ResourceLocation getKey(Faction faction) {
		return ReignOfNetherRegistries.FACTIONS.getKey(faction);
	}
}
