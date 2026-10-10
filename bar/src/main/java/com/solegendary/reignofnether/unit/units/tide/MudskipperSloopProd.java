package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought light raider: an amphibious biter that is fastest on water and tidepools. Trained at the Slipway. */
public class MudskipperSloopProd extends TideProd {

    public final static String itemName = "Mudskipper Sloop";
    public final static ResourceCost cost = ResourceCosts.MUDSKIPPER_SLOOP;

    public MudskipperSloopProd() {
        super(itemName, "mudskipper_sloop_unit", "mudskipper_sloop", 3, cost, () -> EntityRegistrar.MUDSKIPPER_SLOOP_UNIT.get(), true);
    }
}
