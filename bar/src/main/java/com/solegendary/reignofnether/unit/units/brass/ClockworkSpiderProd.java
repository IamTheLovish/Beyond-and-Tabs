package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks T2 turret walker: climbs low walls (up to three blocks) and fires bolt volleys from the top. Trained at the Assembly Works once Tier 2 is researched (BrassT2Prod). */
public class ClockworkSpiderProd extends BrassT2Prod {

    public final static String itemName = "Clockwork Spider";
    public final static ResourceCost cost = ResourceCosts.CLOCKWORK_SPIDER;

    public ClockworkSpiderProd() {
        super(itemName, "clockwork_spider_unit", "clockwork_spider", 3, cost, () -> EntityRegistrar.CLOCKWORK_SPIDER_UNIT.get(), false);
    }
}
