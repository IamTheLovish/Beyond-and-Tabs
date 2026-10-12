package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks T2 arc-thrower: its lightning chains through three enemies, never a friend. Trained at the Assembly Works once Tier 2 is researched (BrassT2Prod). */
public class TeslaWalkerProd extends BrassT2Prod {

    public final static String itemName = "Tesla Walker";
    public final static ResourceCost cost = ResourceCosts.TESLA_WALKER;

    public TeslaWalkerProd() {
        super(itemName, "tesla_walker_unit", "tesla_walker", 3, cost, () -> EntityRegistrar.TESLA_WALKER_UNIT.get(), false);
    }
}
