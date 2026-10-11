package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers T2 flyer: fire dives (4 energy each) and rises once from its own ashes. Trained at the Celestial Observatory (Tier 2). */
public class PhoenixProd extends StormT2Prod {

    public final static String itemName = "Phoenix";
    public final static ResourceCost cost = ResourceCosts.PHOENIX;

    public PhoenixProd() {
        super(itemName, "phoenix_unit", "phoenix", 3, cost, () -> EntityRegistrar.PHOENIX_UNIT.get(), false);
    }
}
