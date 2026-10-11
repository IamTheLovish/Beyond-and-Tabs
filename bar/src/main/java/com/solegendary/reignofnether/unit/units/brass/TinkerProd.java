package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks constructor: builds, repairs and reclaims, and field-upgrades machines; the first one is the Guildmaster. Trained at the Foundry Hall. */
public class TinkerProd extends BrassProd {

    public final static String itemName = "Tinker";
    public final static ResourceCost cost = ResourceCosts.TINKER;

    public TinkerProd() {
        super(itemName, "tinker_unit", "tinker", 3, cost, () -> EntityRegistrar.TINKER_UNIT.get(), true);
    }
}
