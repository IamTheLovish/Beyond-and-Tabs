package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Verdant Court combat engineer, trained at the Circle of Elders once Tier 2 is researched. (design/combat_engineers.md) */
public class RootwardenProd extends CombatEngineerProd {

    public final static String itemName = "Rootwarden";
    public final static ResourceCost cost = ResourceCosts.ROOTWARDEN;

    public RootwardenProd() {
        super(itemName, "rootwarden_unit", "rootwarden", 4, cost, () -> EntityRegistrar.ROOTWARDEN_UNIT.get(), false);
    }
}
