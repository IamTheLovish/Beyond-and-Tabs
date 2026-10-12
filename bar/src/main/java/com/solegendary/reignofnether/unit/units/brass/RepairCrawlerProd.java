package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks T2 support: mends every machine around it; no weapon. Trained at the Assembly Works once Tier 2 is researched (BrassT2Prod). */
public class RepairCrawlerProd extends BrassT2Prod {

    public final static String itemName = "Repair Crawler";
    public final static ResourceCost cost = ResourceCosts.REPAIR_CRAWLER;

    public RepairCrawlerProd() {
        super(itemName, "repair_crawler_unit", "repair_crawler", 3, cost, () -> EntityRegistrar.REPAIR_CRAWLER_UNIT.get(), false);
    }
}
