package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import javax.annotation.Nullable;

/**
 * Verdant Court Tier 1 essence caster: Entangling Roots, Seed of Renewal, Sprout Guardian and Empower, paid in the
 * essence the fallen drop (ThornweaverUnit). Does not fight. Trained at the Grove with no research (lovish, Oct 29:
 * "essence casters should be tier 1"): it reuses VerdantT2Prod's buttons but not its Tier 2 gate.
 */
public class ThornweaverProd extends VerdantT2Prod {

    public final static String itemName = "Thornweaver";
    public final static ResourceCost cost = ResourceCosts.THORNWEAVER;

    public ThornweaverProd() {
        super(itemName, "thornweaver_unit", "thornweaver", 3, cost, () -> EntityRegistrar.THORNWEAVER_UNIT.get(), true);
    }

    /** Tier 1: no research gate. */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return null;
    }
}
