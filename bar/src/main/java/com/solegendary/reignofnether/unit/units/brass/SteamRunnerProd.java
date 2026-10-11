package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks raider: a fast boiler on stilt legs that bites and heats. Trained at the Machine Shop. */
public class SteamRunnerProd extends BrassProd {

    public final static String itemName = "Steam Runner";
    public final static ResourceCost cost = ResourceCosts.STEAM_RUNNER;

    public SteamRunnerProd() {
        super(itemName, "steam_runner_unit", "steam_runner", 3, cost, () -> EntityRegistrar.STEAM_RUNNER_UNIT.get(), true);
    }
}
