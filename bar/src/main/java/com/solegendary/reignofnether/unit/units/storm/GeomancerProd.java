package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers constructor: builds, repairs and reclaims; the first one is the Thunder Sage. Trained at the Jade Pagoda. */
public class GeomancerProd extends StormProd {

    public final static String itemName = "Geomancer";
    public final static ResourceCost cost = ResourceCosts.GEOMANCER;

    public GeomancerProd() {
        super(itemName, "geomancer_unit", "geomancer", 3, cost, () -> EntityRegistrar.GEOMANCER_UNIT.get(), true);
    }
}
