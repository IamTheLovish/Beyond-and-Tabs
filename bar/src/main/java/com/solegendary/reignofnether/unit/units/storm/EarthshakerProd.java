package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers T2 heavy: a jade-veined stone golem whose Ground Slam cracks a line of earth that damages and roots. Trained at the Celestial Observatory. */
public class EarthshakerProd extends StormT2Prod {

    public final static String itemName = "Earthshaker";
    public final static ResourceCost cost = ResourceCosts.EARTHSHAKER;

    public EarthshakerProd() {
        super(itemName, "earthshaker_unit", "earthshaker", 3, cost, () -> EntityRegistrar.EARTHSHAKER_UNIT.get(), false);
    }
}
