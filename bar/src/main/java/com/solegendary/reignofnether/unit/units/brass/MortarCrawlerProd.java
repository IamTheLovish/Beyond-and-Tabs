package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks artillery: a mortar carriage that lobs splash shells. Trained at the Machine Shop. */
public class MortarCrawlerProd extends BrassProd {

    public final static String itemName = "Mortar Crawler";
    public final static ResourceCost cost = ResourceCosts.MORTAR_CRAWLER;

    public MortarCrawlerProd() {
        super(itemName, "mortar_crawler_unit", "mortar_crawler", 3, cost, () -> EntityRegistrar.MORTAR_CRAWLER_UNIT.get(), true);
    }
}
