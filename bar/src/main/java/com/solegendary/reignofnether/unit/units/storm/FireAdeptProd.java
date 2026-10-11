package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers skirmisher: fireballs paid in energy, half rate when starved. Trained at the Lotus Academy. */
public class FireAdeptProd extends StormProd {

    public final static String itemName = "Fire Adept";
    public final static ResourceCost cost = ResourceCosts.FIRE_ADEPT;

    public FireAdeptProd() {
        super(itemName, "fire_adept_unit", "fire_adept", 3, cost, () -> EntityRegistrar.FIRE_ADEPT_UNIT.get(), true);
    }
}
