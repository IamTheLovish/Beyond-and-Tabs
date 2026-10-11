package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.tide.AbyssalShipyard;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import javax.annotation.Nullable;

/**
 * Tidewrought T3 experimental: the Leviathan, raised at the Abyssal Shipyard (design/tidewrought_plan.md, slice 4).
 * TideProd's buttons with a Tier 3 gate (Tier 3 implies Tier 2), the twin of WorldTreeWalkerProd. It walks out beside
 * the lab rather than spawning inside - a 3.5-block crab does not fit through the slip.
 */
public class LeviathanProd extends TideProd {

    public final static String itemName = "Leviathan";
    public final static ResourceCost cost = ResourceCosts.LEVIATHAN;

    public LeviathanProd() {
        super(itemName, "leviathan_unit", "leviathan", 3, cost, () -> EntityRegistrar.LEVIATHAN_UNIT.get(), false);
    }

    /** No Tier 3, no experimental: checked on the server when production starts, and greys the button. */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return AbyssalShipyard.tier3Error(pp);
    }
}
