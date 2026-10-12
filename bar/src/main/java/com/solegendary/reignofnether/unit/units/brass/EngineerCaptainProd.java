package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks T2 constructor: builds twice as fast, raises the T3 lab, and mends machines at twice a Tinker's rate. Trained at the Assembly Works once Tier 2 is researched (BrassT2Prod). */
public class EngineerCaptainProd extends BrassT2Prod {

    public final static String itemName = "Engineer-Captain";
    public final static ResourceCost cost = ResourceCosts.ENGINEER_CAPTAIN;

    public EngineerCaptainProd() {
        super(itemName, "engineer_captain_unit", "engineer_captain", 3, cost, () -> EntityRegistrar.ENGINEER_CAPTAIN_UNIT.get(), false);
    }
}
