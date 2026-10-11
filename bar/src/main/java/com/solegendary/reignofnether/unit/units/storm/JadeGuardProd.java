package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers line infantry: a jade tower shield that turns arrows back on the archer. Trained at the Lotus Academy. */
public class JadeGuardProd extends StormProd {

    public final static String itemName = "Jade Guard";
    public final static ResourceCost cost = ResourceCosts.JADE_GUARD;

    public JadeGuardProd() {
        super(itemName, "jade_guard_unit", "jade_guard", 3, cost, () -> EntityRegistrar.JADE_GUARD_UNIT.get(), true);
    }
}
