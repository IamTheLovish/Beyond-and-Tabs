package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks anti-air: a brass flak gun on a cart. Trained at the Machine Shop. */
public class FlakCartProd extends BrassProd {

    public final static String itemName = "Flak Cart";
    public final static ResourceCost cost = ResourceCosts.FLAK_CART;

    public FlakCartProd() {
        super(itemName, "flak_cart_unit", "flak_cart", 3, cost, () -> EntityRegistrar.FLAK_CART_UNIT.get(), true);
    }
}
