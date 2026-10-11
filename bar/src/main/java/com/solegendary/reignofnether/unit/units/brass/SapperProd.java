package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks demolition: plants timed charges on buildings. Trained at the Machine Shop. */
public class SapperProd extends BrassProd {

    public final static String itemName = "Sapper";
    public final static ResourceCost cost = ResourceCosts.SAPPER;

    public SapperProd() {
        super(itemName, "sapper_unit", "sapper", 2, cost, () -> EntityRegistrar.SAPPER_UNIT.get(), true);
    }
}
