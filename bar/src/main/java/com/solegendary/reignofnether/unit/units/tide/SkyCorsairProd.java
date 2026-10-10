package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought T2 bomber: flies over the fight and drops a line of three bombs. Trained at the Drydock. */
public class SkyCorsairProd extends TideT2Prod {

    public final static String itemName = "Sky Corsair";
    public final static ResourceCost cost = ResourceCosts.SKY_CORSAIR;

    public SkyCorsairProd() {
        super(itemName, "sky_corsair_unit", "sky_corsair", 3, cost, () -> EntityRegistrar.SKY_CORSAIR_UNIT.get(), false);
    }
}
