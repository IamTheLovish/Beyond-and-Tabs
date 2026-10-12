package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks combat engineer, trained at the Assembly Works once Tier 2 is researched. (design/combat_engineers.md) */
public class PioneerProd extends CombatEngineerProd {

    public final static String itemName = "Pioneer";
    public final static ResourceCost cost = ResourceCosts.PIONEER;

    public PioneerProd() {
        super(itemName, "pioneer_unit", "pioneer", 4, cost, () -> EntityRegistrar.PIONEER_UNIT.get(), false);
    }
}
