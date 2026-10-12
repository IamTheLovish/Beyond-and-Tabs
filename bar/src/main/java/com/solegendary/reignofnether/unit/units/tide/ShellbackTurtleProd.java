package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought T2 beast (design/big_units.md 1.5): an island-turtle with a fort on its shell that moors into a harbour. Trained at the Drydock. */
public class ShellbackTurtleProd extends TideT2Prod {

    public final static String itemName = "Shellback Siege-Turtle";
    public final static ResourceCost cost = ResourceCosts.SHELLBACK_TURTLE;

    public ShellbackTurtleProd() {
        super(itemName, "shellback_turtle_unit", "shellback_turtle", 3, cost, () -> EntityRegistrar.SHELLBACK_TURTLE_UNIT.get(), false);
    }
}
