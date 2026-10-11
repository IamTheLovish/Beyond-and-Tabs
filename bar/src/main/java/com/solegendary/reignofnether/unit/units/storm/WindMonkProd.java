package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers anti-air: gusts that tear flyers out of the sky. Trained at the Lotus Academy. */
public class WindMonkProd extends StormProd {

    public final static String itemName = "Wind Monk";
    public final static ResourceCost cost = ResourceCosts.WIND_MONK;

    public WindMonkProd() {
        super(itemName, "wind_monk_unit", "wind_monk", 3, cost, () -> EntityRegistrar.WIND_MONK_UNIT.get(), true);
    }
}
