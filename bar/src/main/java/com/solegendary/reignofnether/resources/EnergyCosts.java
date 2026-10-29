package com.solegendary.reignofnether.resources;

import java.util.List;

/**
 * Energy scales like BAR (lovish, Oct 29: "energy scaling doesn't really work the same way as it does in BAR"; "you can
 * make 5 metal extractors and other buildings without having to make an energy building"). The config still holds Reign
 * of Nether's food/wood/ore prices, and its one conversion (ResourceCost.toEnergy: wood x 1.5 + food x 0.8) priced a T1
 * unit at 1.3 energy per metal and a T2 or T3 one at 0.95 - so energy demand FELL with tech, where BAR's rises (T1 ~10,
 * T2 ~20 energy per metal). This table is the one place unit and opening energy is set: {@link #bake()} overwrites the
 * energy of every cost listed below after the config bake (ResourceCosts.deferredLoadResourceCosts), so the config's
 * wood/food no longer decide energy for them. Metal and build times stay the config's.
 * <ul>
 *   <li><b>The anti-T3 emplacement</b> (DefenceCosts.bakeAntiT3, after this bake) is 15% of the priciest experimental, so
 *       it is energy-heavy too: 275 metal / 4970 energy (was ~260 energy), like BAR's heavy defences.</li>
 *   <li><b>Units, by the lab that trains them</b> (lovish: "in between" 5/10/15 and BAR's 10/20/20): T1 x{@value
 *       #T1_UNIT_ENERGY_PER_METAL}, T2 x{@value #T2_UNIT_ENERGY_PER_METAL} (T2 constructors and combat engineers come
 *       out of the T2 lab too), T3 x{@value #T3_UNIT_ENERGY_PER_METAL} their metal, rounded to 10.</li>
 *   <li><b>The opening</b> (lovish's option A): extractor {@value #METAL_EXTRACTOR_ENERGY}, wind {@value
 *       #WIND_GENERATOR_ENERGY}, Solar Collector {@value #SOLAR_COLLECTOR_ENERGY} (SolarGenerator.Kind.BASIC: it costs
 *       metal and build time instead - the BAR choice), T1 lab {@value #T1_LAB_ENERGY}, worker {@value #WORKER_ENERGY}.
 *       The commander and four workers on extractors then drain ~3x the capitol's 20 energy/s - BAR's commander drains
 *       2.8x its 30 - so the 1000 energy bank runs out at about the 3rd extractor without a wind or a solar.</li>
 * </ul>
 * The lists are the roster of tools/unit_stats.py by tier (tools/test_energy_scaling.py fails when the two part).
 * tools/unit_stats.py and tools/balance_audit.py read this file, so the balance sheet shows the baked prices.
 */
public final class EnergyCosts {
    private EnergyCosts() { }

    public static final float T1_UNIT_ENERGY_PER_METAL = 8f;
    public static final float T2_UNIT_ENERGY_PER_METAL = 15f;
    public static final float T3_UNIT_ENERGY_PER_METAL = 18f;

    public static final int WORKER_ENERGY = 250;
    public static final int METAL_EXTRACTOR_ENERGY = 300;
    public static final int WIND_GENERATOR_ENERGY = 150;
    public static final int SOLAR_COLLECTOR_ENERGY = 0;
    public static final int T1_LAB_ENERGY = 675;
    /** The Horde's lab is a Basic Portal refitted to a Military Portal: the two halves add up to a T1 lab. */
    public static final int HORDE_PORTAL_ENERGY = 330;
    public static final int HORDE_MILITARY_REFIT_ENERGY = T1_LAB_ENERGY - HORDE_PORTAL_ENERGY;

    public static final List<ResourceCost> WORKERS = List.of(ResourceCosts.VILLAGER, ResourceCosts.ZOMBIE_VILLAGER,
        ResourceCosts.GRUNT, ResourceCosts.SEEDSHAPER, ResourceCosts.SHIPWRIGHT, ResourceCosts.GEOMANCER, ResourceCosts.TINKER);

    public static final List<ResourceCost> T1_UNITS = List.of(ResourceCosts.SCOUT_DOG, ResourceCosts.SCOUT_CAT,
        ResourceCosts.VINDICATOR, ResourceCosts.PILLAGER, ResourceCosts.BAT, ResourceCosts.ZOMBIE, ResourceCosts.HUSK,
        ResourceCosts.DROWNED, ResourceCosts.SKELETON, ResourceCosts.STRAY, ResourceCosts.BOGGED, ResourceCosts.SPIDER,
        ResourceCosts.POISON_SPIDER, ResourceCosts.SLIME, ResourceCosts.STRIDER, ResourceCosts.BRUTE, ResourceCosts.HEADHUNTER,
        ResourceCosts.MARAUDER, ResourceCosts.HOGLIN, ResourceCosts.FOX_COURIER, ResourceCosts.OWL_WATCHER,
        ResourceCosts.LEAFBLADE, ResourceCosts.THORNBOW, ResourceCosts.SENTINEL_TREANT, ResourceCosts.HIVE_KEEPER,
        ResourceCosts.MOONWELL_BEARER, ResourceCosts.GULL_SPOTTER, ResourceCosts.CUTLASS_RAIDER, ResourceCosts.HARPOONER,
        ResourceCosts.REEF_GUARD, ResourceCosts.BOMBARD_CREW, ResourceCosts.FLAK_SWIVEL, ResourceCosts.MUDSKIPPER_SLOOP,
        ResourceCosts.TIDE_PRIEST, ResourceCosts.PAPER_CRANE, ResourceCosts.BLADE_DANCER, ResourceCosts.FIRE_ADEPT,
        ResourceCosts.JADE_GUARD, ResourceCosts.WIND_MONK, ResourceCosts.FROST_ADEPT, ResourceCosts.ROCKET_BATTERY,
        ResourceCosts.INK_SCRIBE, ResourceCosts.GYRO_SCOUT, ResourceCosts.STEAM_RUNNER, ResourceCosts.RIFLE_SQUAD,
        ResourceCosts.IRON_WARDEN, ResourceCosts.FLAK_CART, ResourceCosts.MORTAR_CRAWLER, ResourceCosts.SHIELD_PROJECTOR,
        ResourceCosts.SAPPER);

    /** The T2 lab's roster: its army, the T2 constructors and the combat engineers. */
    public static final List<ResourceCost> T2_UNITS = List.of(ResourceCosts.EVOKER, ResourceCosts.WINDCALLER,
        ResourceCosts.WITCH, ResourceCosts.IRON_GOLEM, ResourceCosts.RAVAGER, ResourceCosts.CREEPER, ResourceCosts.WRAITH,
        ResourceCosts.WARDEN, ResourceCosts.BLAZE, ResourceCosts.WITHER_SKELETON, ResourceCosts.MAGMA_CUBE, ResourceCosts.GHAST,
        ResourceCosts.ELDER_TREANT, ResourceCosts.STAG_LANCER, ResourceCosts.WISP_CHOIR, ResourceCosts.SHADE_RANGER,
        ResourceCosts.BLOOM_PRIESTESS, ResourceCosts.THUNDER_DRAGON, ResourceCosts.EARTHSHAKER, ResourceCosts.GRAVITY_MONK,
        ResourceCosts.MIRROR_MAGE, ResourceCosts.SEER, ResourceCosts.LANTERN_FORTRESS, ResourceCosts.PHOENIX,
        ResourceCosts.STEAM_JUGGERNAUT, ResourceCosts.TESLA_WALKER, ResourceCosts.GYROCOPTER_GUNSHIP,
        ResourceCosts.DRILL_BURROWER, ResourceCosts.REPAIR_CRAWLER, ResourceCosts.CLOCKWORK_SPIDER,
        ResourceCosts.ORIFLAMME_ELEPHANT, ResourceCosts.GOREHORN_RHINO, ResourceCosts.OSSUARY_BEHEMOTH,
        ResourceCosts.MOSSBACK_BISON, ResourceCosts.SHELLBACK_TURTLE, ResourceCosts.JADE_QILIN, ResourceCosts.STEAM_HAULER,
        ResourceCosts.KRAKEN_CALLER, ResourceCosts.MORTAR_GALLEY, ResourceCosts.SKY_CORSAIR,
        ResourceCosts.ROYAL_ARCHITECT, ResourceCosts.EMBALMER, ResourceCosts.BONEWRIGHT, ResourceCosts.ELDER_DRUID,
        ResourceCosts.HARBOURMASTER, ResourceCosts.LEY_MASTER, ResourceCosts.ENGINEER_CAPTAIN,
        ResourceCosts.FIELD_MASON, ResourceCosts.BONESETTER, ResourceCosts.WRECKER, ResourceCosts.ROOTWARDEN,
        ResourceCosts.SALVAGER, ResourceCosts.JADE_MENDER, ResourceCosts.PIONEER);

    /** The experimentals: the same list the anti-T3 emplacement is priced from (one list, not two). */
    public static final List<ResourceCost> T3_UNITS = com.solegendary.reignofnether.building.buildings.defence.DefenceCosts.T3_UNITS;

    /**
     * Everything else a player can train or capture: the neutral units the capturable buildings make (End Portal's Enderman,
     * the animal pens), the Gravebound's converted Zombie Piglin and Zoglin, the Militia and the Bee - priced as T1 units
     * (8 energy per metal). tools/test_energy_scaling.py fails when a priced unit/units/**Prod.java is in no list.
     */
    public static final List<ResourceCost> NEUTRAL_UNITS = List.of(ResourceCosts.ENDERMAN, ResourceCosts.GRIZZLY_BEAR,
        ResourceCosts.POLAR_BEAR, ResourceCosts.PANDA, ResourceCosts.WOLF, ResourceCosts.LLAMA, ResourceCosts.ZOMBIE_PIGLIN,
        ResourceCosts.ZOGLIN, ResourceCosts.MILITIA, ResourceCosts.BEE);

    /** Every faction's T1 army lab (the Horde's is the Basic Portal + its Military refit, below). */
    public static final List<ResourceCost> T1_LABS = List.of(ResourceCosts.BARRACKS, ResourceCosts.GRAVEYARD,
        ResourceCosts.GROVE, ResourceCosts.SLIPWAY, ResourceCosts.LOTUS_ACADEMY, ResourceCosts.MACHINE_SHOP,
        ResourceCosts.HOGLIN_STABLES);

    /** Energy at {@code ratio} per metal, rounded to the nearest 10 like a baked config cost. */
    public static int energyFor(int metal, float ratio) {
        return Math.round(metal * ratio / 10f) * 10;
    }

    /** Overwrites the energy of every cost above (after the config bake; before the anti-T3 and the tier gate's bake). */
    public static void bake() {
        for (ResourceCost c : WORKERS)
            c.wood = WORKER_ENERGY;
        for (ResourceCost c : T1_UNITS)
            c.wood = energyFor(c.ore, T1_UNIT_ENERGY_PER_METAL);
        for (ResourceCost c : NEUTRAL_UNITS)
            c.wood = energyFor(c.ore, T1_UNIT_ENERGY_PER_METAL);
        for (ResourceCost c : T2_UNITS)
            c.wood = energyFor(c.ore, T2_UNIT_ENERGY_PER_METAL);
        for (ResourceCost c : T3_UNITS)
            c.wood = energyFor(c.ore, T3_UNIT_ENERGY_PER_METAL);
        for (ResourceCost c : T1_LABS)
            c.wood = T1_LAB_ENERGY;
        ResourceCosts.BASIC_PORTAL.wood = HORDE_PORTAL_ENERGY;
        ResourceCosts.RESEARCH_MILITARY_PORTAL.wood = HORDE_MILITARY_REFIT_ENERGY;
        ResourceCosts.METAL_EXTRACTOR.wood = METAL_EXTRACTOR_ENERGY;
        ResourceCosts.WIND_GENERATOR.wood = WIND_GENERATOR_ENERGY;
    }
}
