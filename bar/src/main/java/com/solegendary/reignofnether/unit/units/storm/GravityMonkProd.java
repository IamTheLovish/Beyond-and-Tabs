package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers T2 controller: Singularity drags every enemy round a spot into one clump for the Judgement to land on. Trained at the Celestial Observatory. */
public class GravityMonkProd extends StormT2Prod {

    public final static String itemName = "Gravity Monk";
    public final static ResourceCost cost = ResourceCosts.GRAVITY_MONK;

    public GravityMonkProd() {
        super(itemName, "gravity_monk_unit", "gravity_monk", 3, cost, () -> EntityRegistrar.GRAVITY_MONK_UNIT.get(), true);
    }
}
