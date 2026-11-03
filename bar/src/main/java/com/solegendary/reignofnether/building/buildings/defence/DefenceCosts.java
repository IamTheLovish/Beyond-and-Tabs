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
    public static final ResourceCost WALL = ResourceCost.Building(0, 0, 15);
    // a gate is a wall segment with a mechanism: a little dearer
    public static final ResourceCost GATE = ResourceCost.Building(0, 20, 40);
    // the T1 anti-raid tower (BAR's LLT): about one and a half T1 raiders (a Cutlass Raider is 80m / 100e)
    public static final ResourceCost RAID_TOWER = ResourceCost.Building(0, 150, 110);
    // traps: under half a raider each - a trap is a gamble on the enemy walking over it
    public static final ResourceCost TRAP_SPIKES = ResourceCost.Building(0, 40, 35);
    public static final ResourceCost TRAP_SNARE = ResourceCost.Building(0, 50, 30);
    public static final ResourceCost TRAP_MINE = ResourceCost.Building(0, 60, 50);
    // the enhanced T1 towers (BAR's HLLT; lovish Oct 21 02:51): ~2.2x the price for ~2.3x the weapon - a slightly
    // better rate, with more eggs in one basket (one artillery unit kills more value)
    public static final ResourceCost RAID_TOWER_HEAVY = ResourceCost.Building(0, 400, 260);
    // anti-air (lovish Oct 21 02:47, flyers sniped his commander): a Sky Watch costs under half a T2 flyer (a Gyrocopter
    // Gunship is 255m, 90 hp) and downs one in ~6 s, the enhanced one in ~3 s; the flak hurts a whole clump at once
    public static final ResourceCost SKY_WATCH = ResourceCost.Building(0, 160, 120);
    public static final ResourceCost SKY_WATCH_HEAVY = ResourceCost.Building(0, 380, 260);
    public static final ResourceCost FLAK = ResourceCost.Building(0, 350, 280);
    // the anti-siege tower (BAR's HLT; lovish Oct 29, design/siege_t1.md): ~1.7x the enhanced anti-raid tower, the
    // price of about five T1 siege lobbers (95 m each) - six of them die before it falls
    public static final ResourceCost SIEGE_GUARD = ResourceCost.Building(0, 650, 450);
    // baked from the T3 costs (bakeAntiT3); until then a placeholder that is never shown (costs bake at common setup)
    public static final ResourceCost ANTI_T3 = ResourceCost.Building(0, 260, 275);

    // the enhanced (T1 lab) walls and traps, lovish Oct 21 02:51 "kinda like in bar": a fortified wall is ~2.7x a wall
    // for 2.75x its health; an enhanced trap ~2x its trap for a wider, harder blast and one spare charge
    public static final ResourceCost WALL_FORTIFIED = ResourceCost.Building(0, 20, 40);
    public static final ResourceCost TRAP_SPIKES_ENHANCED = ResourceCost.Building(0, 80, 70);
    public static final ResourceCost TRAP_SNARE_ENHANCED = ResourceCost.Building(0, 100, 60);
    public static final ResourceCost TRAP_MINE_ENHANCED = ResourceCost.Building(0, 120, 100);

    /** The price of a trap of this kind, base or enhanced. */
    public static ResourceCost of(Trap.Kind kind, boolean enhanced) {
        return switch (kind) {
            case SPIKES -> enhanced ? TRAP_SPIKES_ENHANCED : TRAP_SPIKES;
            case SNARE -> enhanced ? TRAP_SNARE_ENHANCED : TRAP_SNARE;
            case MINE -> enhanced ? TRAP_MINE_ENHANCED : TRAP_MINE;
        };
    }

    /** The price of a tower of this kind (one price per job, every faction). */
    public static ResourceCost of(DefenceTower.Kind kind) {
        return switch (kind) {
            case RAID -> RAID_TOWER;
            case RAID_HEAVY -> RAID_TOWER_HEAVY;
            case SKY_WATCH -> SKY_WATCH;
            case SKY_WATCH_HEAVY -> SKY_WATCH_HEAVY;
            case FLAK -> FLAK;
            case ANTI_T3 -> ANTI_T3;
            case SIEGE_GUARD -> SIEGE_GUARD;
        };
    }

    /** The anti-T3 defence costs this share of the priciest T3 unit, metal and energy alike. */
    public static final float ANTI_T3_SHARE = 0.15f;

    /** Every faction's experimental (the Brassworks' joins when it ships). */
    public static final List<ResourceCost> T3_UNITS = List.of(ResourceCosts.SUN_COLOSSUS, ResourceCosts.BONE_DRAGON,
        ResourceCosts.WAR_MAMMOTH, ResourceCosts.WORLD_TREE_WALKER, ResourceCosts.LEVIATHAN, ResourceCosts.CELESTIAL_DRAGON,
        ResourceCosts.IRON_LEVIATHAN_ENGINE);

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
