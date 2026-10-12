package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks T2 gunship: a flying rotor gun that makes strafing runs over the enemy line. Trained at the Assembly Works once Tier 2 is researched (BrassT2Prod). */
public class GyrocopterGunshipProd extends BrassT2Prod {

    public final static String itemName = "Gyrocopter Gunship";
    public final static ResourceCost cost = ResourceCosts.GYROCOPTER_GUNSHIP;

    public GyrocopterGunshipProd() {
        super(itemName, "gyrocopter_gunship_unit", "gyrocopter_gunship", 3, cost, () -> EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), false);
    }
}
