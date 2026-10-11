package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers T2 sensor: sees 36 blocks and strips the cover from concealed enemies within 14. Trained at the Celestial Observatory (Tier 2). */
public class SeerProd extends StormT2Prod {

    public final static String itemName = "Seer";
    public final static ResourceCost cost = ResourceCosts.SEER;

    public SeerProd() {
        super(itemName, "seer_unit", "seer", 3, cost, () -> EntityRegistrar.SEER_UNIT.get(), true);
    }
}
