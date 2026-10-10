package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought T2 controller: Tentacle Grab drags and roots one enemy, harder in water. Trained at the Drydock. */
public class KrakenCallerProd extends TideT2Prod {

    public final static String itemName = "Kraken Caller";
    public final static ResourceCost cost = ResourceCosts.KRAKEN_CALLER;

    public KrakenCallerProd() {
        super(itemName, "kraken_caller_unit", "kraken_caller", 3, cost, () -> EntityRegistrar.KRAKEN_CALLER_UNIT.get(), true);
    }
}
