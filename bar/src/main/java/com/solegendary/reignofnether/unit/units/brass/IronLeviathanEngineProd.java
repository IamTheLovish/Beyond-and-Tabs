package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.building.buildings.brass.GreatForge;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import javax.annotation.Nullable;

/**
 * Brassworks T3 experimental: the Iron Leviathan Engine, cast at the Great Forge (design/brassworks_plan.md, slice 4).
 * BrassProd's buttons with a Tier 3 gate (Tier 3 implies Tier 2), the twin of CelestialDragonProd. It walks out beside
 * the forge rather than spawning inside it.
 */
public class IronLeviathanEngineProd extends BrassProd {

    public final static String itemName = "Iron Leviathan Engine";
    public final static ResourceCost cost = ResourceCosts.IRON_LEVIATHAN_ENGINE;

    public IronLeviathanEngineProd() {
        super(itemName, "iron_leviathan_engine_unit", "iron_leviathan_engine", 3, cost, () -> EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get(), false);
    }

    /** No Tier 3, no experimental: checked on the server when production starts, and greys the button. */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return GreatForge.tier3Error(pp);
    }
}
