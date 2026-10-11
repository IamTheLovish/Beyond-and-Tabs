package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers artillery: rocket volleys that need no energy. Trained at the Lotus Academy. */
public class RocketBatteryProd extends StormProd {

    public final static String itemName = "Rocket Battery";
    public final static ResourceCost cost = ResourceCosts.ROCKET_BATTERY;

    public RocketBatteryProd() {
        super(itemName, "rocket_battery_unit", "rocket_battery", 3, cost, () -> EntityRegistrar.ROCKET_BATTERY_UNIT.get(), true);
    }
}
