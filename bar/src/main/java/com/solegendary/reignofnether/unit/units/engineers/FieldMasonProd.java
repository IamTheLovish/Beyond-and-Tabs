package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Sunforged combat engineer, trained at the Arcane Tower once Tier 2 is researched. (design/combat_engineers.md) */
public class FieldMasonProd extends CombatEngineerProd {

    public final static String itemName = "Field Mason";
    public final static ResourceCost cost = ResourceCosts.FIELD_MASON;

    public FieldMasonProd() {
        super(itemName, "field_mason_unit", "field_mason", 4, cost, () -> EntityRegistrar.FIELD_MASON_UNIT.get(), false);
    }
}
