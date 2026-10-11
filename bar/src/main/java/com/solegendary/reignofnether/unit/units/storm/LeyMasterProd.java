package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * T2 constructor of the Stormcallers: builds twice as fast with twice a Geomancer's health. Its power, Mana Well, raises
 * a well that empowers and cheapens every spell cast round it. Trained at the Celestial Observatory once Tier 2 is
 * researched (StormT2Prod).
 */
public class LeyMasterProd extends StormT2Prod {

    public final static String itemName = "Ley Master";
    public final static ResourceCost cost = ResourceCosts.LEY_MASTER;

    public LeyMasterProd() {
        super(itemName, "ley_master_unit", "ley_master", 3, cost, () -> EntityRegistrar.LEY_MASTER_UNIT.get(), false);
    }
}
