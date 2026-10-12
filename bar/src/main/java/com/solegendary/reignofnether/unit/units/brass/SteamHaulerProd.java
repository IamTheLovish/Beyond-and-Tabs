package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks T2 beast (design/big_units.md 1.7): a traction engine that mends every machine round it and drops turrets. Trained at the Assembly Works. */
public class SteamHaulerProd extends BrassT2Prod {

    public final static String itemName = "Steam Hauler";
    public final static ResourceCost cost = ResourceCosts.STEAM_HAULER;

    public SteamHaulerProd() {
        super(itemName, "steam_hauler_unit", "steam_hauler", 3, cost, () -> EntityRegistrar.STEAM_HAULER_UNIT.get(), false);
    }
}
