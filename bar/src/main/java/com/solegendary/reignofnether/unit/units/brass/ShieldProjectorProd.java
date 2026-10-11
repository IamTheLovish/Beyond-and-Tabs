package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks support: projects a small projectile shield onto a friend. Trained at the Machine Shop. */
public class ShieldProjectorProd extends BrassProd {

    public final static String itemName = "Shield Projector";
    public final static ResourceCost cost = ResourceCosts.SHIELD_PROJECTOR;

    public ShieldProjectorProd() {
        super(itemName, "shield_projector_unit", "shield_projector", 2, cost, () -> EntityRegistrar.SHIELD_PROJECTOR_UNIT.get(), true);
    }
}
