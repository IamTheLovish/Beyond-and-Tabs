package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Horde combat engineer, trained at the Flame Sanctuary once Tier 2 is researched. (design/combat_engineers.md) */
public class WreckerProd extends CombatEngineerProd {

    public final static String itemName = "Wrecker";
    public final static ResourceCost cost = ResourceCosts.WRECKER;

    public WreckerProd() {
        super(itemName, "wrecker_unit", "wrecker", 4, cost, () -> EntityRegistrar.WRECKER_UNIT.get(), false);
    }
}
