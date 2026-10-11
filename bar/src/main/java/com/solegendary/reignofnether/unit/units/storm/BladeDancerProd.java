package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers raider: twin sabres and Blink Strike onto enemy archers. Trained at the Lotus Academy. */
public class BladeDancerProd extends StormProd {

    public final static String itemName = "Blade Dancer";
    public final static ResourceCost cost = ResourceCosts.BLADE_DANCER;

    public BladeDancerProd() {
        super(itemName, "blade_dancer_unit", "blade_dancer", 3, cost, () -> EntityRegistrar.BLADE_DANCER_UNIT.get(), true);
    }
}
