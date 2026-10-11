package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks scout: a clockwork rotor drone that flies and sees far. Trained at the Foundry Hall. */
public class GyroScoutProd extends BrassProd {

    public final static String itemName = "Gyro Scout";
    public final static ResourceCost cost = ResourceCosts.GYRO_SCOUT;

    public GyroScoutProd() {
        super(itemName, "gyro_scout_unit", "gyro_scout", 2, cost, () -> EntityRegistrar.GYRO_SCOUT_UNIT.get(), false);
    }
}
