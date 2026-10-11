package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers support: Seal silences an enemy unit's abilities (25 energy). Trained at the Lotus Academy. */
public class InkScribeProd extends StormProd {

    public final static String itemName = "Ink Scribe";
    public final static ResourceCost cost = ResourceCosts.INK_SCRIBE;

    public InkScribeProd() {
        super(itemName, "ink_scribe_unit", "ink_scribe", 3, cost, () -> EntityRegistrar.INK_SCRIBE_UNIT.get(), true);
    }
}
