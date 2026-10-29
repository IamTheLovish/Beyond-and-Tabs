package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Verdant Court T2 essence caster: Entangling Roots, Seed of Renewal, Sprout Guardian and Empower, paid in the essence
 * the fallen drop (ThornweaverUnit). Does not fight. Trained at the Circle of Elders once Tier 2 is researched
 * (VerdantT2Prod).
 */
public class ThornweaverProd extends VerdantT2Prod {

    public final static String itemName = "Thornweaver";
    public final static ResourceCost cost = ResourceCosts.THORNWEAVER;

    public ThornweaverProd() {
        super(itemName, "thornweaver_unit", "thornweaver", 3, cost, () -> EntityRegistrar.THORNWEAVER_UNIT.get(), true);
    }
}
