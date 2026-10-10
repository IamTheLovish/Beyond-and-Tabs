package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought skirmisher: a harpoon on a line whose every third throw drags the target in. Trained at the Slipway. */
public class HarpoonerProd extends TideProd {

    public final static String itemName = "Harpooner";
    public final static ResourceCost cost = ResourceCosts.HARPOONER;

    public HarpoonerProd() {
        super(itemName, "harpooner_unit", "harpooner", 3, cost, () -> EntityRegistrar.HARPOONER_UNIT.get(), true);
    }
}
