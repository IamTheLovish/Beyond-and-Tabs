package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers skirmisher: slowing frost bolts paid in energy, and Frost Field. Trained at the Lotus Academy. */
public class FrostAdeptProd extends StormProd {

    public final static String itemName = "Frost Adept";
    public final static ResourceCost cost = ResourceCosts.FROST_ADEPT;

    public FrostAdeptProd() {
        super(itemName, "frost_adept_unit", "frost_adept", 3, cost, () -> EntityRegistrar.FROST_ADEPT_UNIT.get(), true);
    }
}
