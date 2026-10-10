package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought T1 anti-air: flak bursts that wreck flyers and barely scratch the ground. Trained at the Slipway. */
public class FlakSwivelProd extends TideProd {

    public final static String itemName = "Flak Swivel";
    public final static ResourceCost cost = ResourceCosts.FLAK_SWIVEL;

    public FlakSwivelProd() {
        super(itemName, "flak_swivel_unit", "flak_swivel", 3, cost, () -> EntityRegistrar.FLAK_SWIVEL_UNIT.get(), true);
    }
}
