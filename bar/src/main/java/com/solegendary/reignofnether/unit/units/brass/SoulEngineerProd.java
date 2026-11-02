package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Brassworks essence caster: Patch Kit, Ghost Furnace, Soul Boiler and Empower, paid in the essence the fallen drop
 * (SoulEngineerUnit). Does not fight. Tier 1, like rez bots (lovish, Oct 29): trained at the Machine Shop, no Tier 2.
 */
public class SoulEngineerProd extends BrassProd {

    public final static String itemName = "Soul Engineer";
    public final static ResourceCost cost = ResourceCosts.SOUL_ENGINEER;

    public SoulEngineerProd() {
        super(itemName, "soul_engineer_unit", "soul_engineer", 3, cost, () -> EntityRegistrar.SOUL_ENGINEER_UNIT.get(), true);
    }
}
