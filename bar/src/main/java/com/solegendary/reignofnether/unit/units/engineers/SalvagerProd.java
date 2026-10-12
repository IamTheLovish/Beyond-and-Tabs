package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought combat engineer, trained at the Drydock once Tier 2 is researched. (design/combat_engineers.md) */
public class SalvagerProd extends CombatEngineerProd {

    public final static String itemName = "Salvager";
    public final static ResourceCost cost = ResourceCosts.SALVAGER;

    public SalvagerProd() {
        super(itemName, "salvager_unit", "salvager", 4, cost, () -> EntityRegistrar.SALVAGER_UNIT.get(), false);
    }
}
