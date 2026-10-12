package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks T2 raider: tunnels under the front (untargetable) and bursts up behind it, throwing foes into the air. Trained at the Assembly Works once Tier 2 is researched (BrassT2Prod). */
public class DrillBurrowerProd extends BrassT2Prod {

    public final static String itemName = "Drill Burrower";
    public final static ResourceCost cost = ResourceCosts.DRILL_BURROWER;

    public DrillBurrowerProd() {
        super(itemName, "drill_burrower_unit", "drill_burrower", 3, cost, () -> EntityRegistrar.DRILL_BURROWER_UNIT.get(), false);
    }
}
