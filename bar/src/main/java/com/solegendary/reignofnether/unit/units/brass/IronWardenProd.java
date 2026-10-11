package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks line: a small crewed steam golem that heats as it swings. Trained at the Machine Shop. */
public class IronWardenProd extends BrassProd {

    public final static String itemName = "Iron Warden";
    public final static ResourceCost cost = ResourceCosts.IRON_WARDEN;

    public IronWardenProd() {
        super(itemName, "iron_warden_unit", "iron_warden", 3, cost, () -> EntityRegistrar.IRON_WARDEN_UNIT.get(), true);
    }
}
