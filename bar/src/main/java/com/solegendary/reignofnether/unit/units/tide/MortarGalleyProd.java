package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought T2 artillery: a snapping turtle with a mortar on its shell, out-ranging the Bombard. Trained at the Drydock. */
public class MortarGalleyProd extends TideT2Prod {

    public final static String itemName = "Mortar Galley";
    public final static ResourceCost cost = ResourceCosts.MORTAR_GALLEY;

    public MortarGalleyProd() {
        super(itemName, "mortar_galley_unit", "mortar_galley", 3, cost, () -> EntityRegistrar.MORTAR_GALLEY_UNIT.get(), false);
    }
}
