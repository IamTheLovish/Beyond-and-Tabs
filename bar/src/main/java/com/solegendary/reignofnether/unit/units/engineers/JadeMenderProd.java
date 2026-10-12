package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers combat engineer, trained at the Celestial Observatory once Tier 2 is researched. (design/combat_engineers.md) */
public class JadeMenderProd extends CombatEngineerProd {

    public final static String itemName = "Jade Mender";
    public final static ResourceCost cost = ResourceCosts.JADE_MENDER;

    public JadeMenderProd() {
        super(itemName, "jade_mender_unit", "jade_mender", 4, cost, () -> EntityRegistrar.JADE_MENDER_UNIT.get(), false);
    }
}
