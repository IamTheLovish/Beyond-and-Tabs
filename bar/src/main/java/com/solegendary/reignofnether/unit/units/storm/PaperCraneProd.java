package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers scout: a folded-paper crane that flies fast and far. Trained at the Jade Pagoda. */
public class PaperCraneProd extends StormProd {

    public final static String itemName = "Paper Crane";
    public final static ResourceCost cost = ResourceCosts.PAPER_CRANE;

    public PaperCraneProd() {
        super(itemName, "paper_crane_unit", "paper_crane", 2, cost, () -> EntityRegistrar.PAPER_CRANE_UNIT.get(), true);
    }
}
