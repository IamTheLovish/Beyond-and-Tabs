package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Brassworks T2 essence caster: Patch Kit, Ghost Furnace, Soul Boiler and Empower, paid in the essence the fallen drop
 * (SoulEngineerUnit). Does not fight. Trained at the Assembly Works once Tier 2 is researched (BrassT2Prod).
 */
public class SoulEngineerProd extends BrassT2Prod {

    public final static String itemName = "Soul Engineer";
    public final static ResourceCost cost = ResourceCosts.SOUL_ENGINEER;

    public SoulEngineerProd() {
        super(itemName, "soul_engineer_unit", "soul_engineer", 3, cost, () -> EntityRegistrar.SOUL_ENGINEER_UNIT.get(), true);
    }
}
