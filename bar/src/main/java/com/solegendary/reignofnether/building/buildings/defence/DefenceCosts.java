package com.solegendary.reignofnether.building.buildings.defence;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import java.util.List;

/**
 * Prices of the static defences (design/defences.md), in BAR metal / energy directly - the LabAnnex and Geothermal Plant
 * way ({@link ResourceCost#Building}: wood = energy, ore = metal), not a config entry, so one table holds the whole
 * defence economy. Shared by all seven factions: the factions differ in looks, never in price, so a wall line or a tower
 * costs the same whoever builds it (BAR readability).
 * <p>
 * The anti-T3 defence's price is not a constant: lovish's rule is "a T3 defence ~10-20% of the most expensive T3 unit's
 * cost", so {@link #bakeAntiT3()} sets it to {@link #ANTI_T3_SHARE} of the priciest experimental in {@link #T3_UNITS}
 * once the config costs are baked (ResourceCosts.deferredLoadResourceCosts calls it last). A new experimental joins the
 * list; the defence_anti_t3_costs_ten_to_twenty_percent_of_a_t3 GameTest checks every faction's bot T3 against it.
 */
public final class DefenceCosts {
    private DefenceCosts() { }

    // BAR's dragon's teeth: a few metal each, the cheapest thing a constructor can make. 2x2 blocks of wall.
    public static final ResourceCost WALL = ResourceCost.Building(0, 0, 15, 0);
    // a gate is a wall segment with a mechanism: a little dearer
    public static final ResourceCost GATE = ResourceCost.Building(0, 20, 40, 0);
    // the T1 anti-raid tower (BAR's LLT): about one and a half T1 raiders (a Cutlass Raider is 80m / 100e)
    public static final ResourceCost RAID_TOWER = ResourceCost.Building(0, 150, 110, 0);
    // traps: under half a raider each - a trap is a gamble on the enemy walking over it
    public static final ResourceCost TRAP_SPIKES = ResourceCost.Building(0, 40, 35, 0);
    public static final ResourceCost TRAP_SNARE = ResourceCost.Building(0, 50, 30, 0);
    public static final ResourceCost TRAP_MINE = ResourceCost.Building(0, 60, 50, 0);
    // baked from the T3 costs (bakeAntiT3); until then a placeholder that is never shown (costs bake at common setup)
    public static final ResourceCost ANTI_T3 = ResourceCost.Building(0, 260, 275, 0);

    /** The anti-T3 defence costs this share of the priciest T3 unit, metal and energy alike. */
    public static final float ANTI_T3_SHARE = 0.15f;

    /** Every faction's experimental (the Brassworks' joins when it ships). */
    public static final List<ResourceCost> T3_UNITS = List.of(ResourceCosts.SUN_COLOSSUS, ResourceCosts.BONE_DRAGON,
        ResourceCosts.WAR_MAMMOTH, ResourceCosts.WORLD_TREE_WALKER, ResourceCosts.LEVIATHAN, ResourceCosts.CELESTIAL_DRAGON);

    /** Sets {@link #ANTI_T3} to {@link #ANTI_T3_SHARE} of the priciest T3's metal and energy (rounded like a baked cost). */
    public static void bakeAntiT3() {
        int metal = 0, energy = 0;
        for (ResourceCost c : T3_UNITS) {
            metal = Math.max(metal, c.metal());
            energy = Math.max(energy, c.energy());
        }
        if (metal <= 0)
            return;   // T3 costs not baked: keep the placeholder rather than price a defence at zero
        ANTI_T3.ore = Math.round(metal * ANTI_T3_SHARE / 5f) * 5;
        ANTI_T3.wood = Math.round(energy * ANTI_T3_SHARE / 10f) * 10;
    }
}
