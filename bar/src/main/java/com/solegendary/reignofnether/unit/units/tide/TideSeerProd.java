package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Tidewrought T2 essence caster: Brine Bolt, Drowned Tithe, Rising Tide and Empower, paid in the essence the fallen drop
 * (TideSeerUnit). Does not fight. Trained at the Drydock once Tier 2 is researched (TideT2Prod).
 */
public class TideSeerProd extends TideT2Prod {

    public final static String itemName = "Tide Seer";
    public final static ResourceCost cost = ResourceCosts.TIDE_SEER;

    public TideSeerProd() {
        super(itemName, "tide_seer_unit", "tide_seer", 3, cost, () -> EntityRegistrar.TIDE_SEER_UNIT.get(), true);
    }
}
