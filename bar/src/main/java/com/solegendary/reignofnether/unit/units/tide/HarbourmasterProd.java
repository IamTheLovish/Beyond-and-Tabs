package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * T2 constructor of the Tidewrought: builds twice as fast with twice a Shipwright's health. Its power, Raise Great
 * Tidepool, floods a wide patch for 45 s. Trained at the Drydock once Tier 2 is researched (TideT2Prod).
 */
public class HarbourmasterProd extends TideT2Prod {

    public final static String itemName = "Harbourmaster";
    public final static ResourceCost cost = ResourceCosts.HARBOURMASTER;

    public HarbourmasterProd() {
        super(itemName, "harbourmaster_unit", "harbourmaster", 3, cost, () -> EntityRegistrar.HARBOURMASTER_UNIT.get(), false);
    }
}
