package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.storm.HeavensGate;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import javax.annotation.Nullable;

/**
 * Stormcallers T3 experimental: the Celestial Dragon, raised at Heaven's Gate (design/stormcallers_plan.md, slice 4).
 * StormProd's buttons with a Tier 3 gate (Tier 3 implies Tier 2), the twin of LeviathanProd. It rises outside the
 * gate rather than spawning inside it.
 */
public class CelestialDragonProd extends StormProd {

    public final static String itemName = "Celestial Dragon";
    public final static ResourceCost cost = ResourceCosts.CELESTIAL_DRAGON;

    public CelestialDragonProd() {
        super(itemName, "celestial_dragon_unit", "celestial_dragon", 3, cost, () -> EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), false);
    }

    /** No Tier 3, no experimental: checked on the server when production starts, and greys the button. */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return HeavensGate.tier3Error(pp);
    }
}
