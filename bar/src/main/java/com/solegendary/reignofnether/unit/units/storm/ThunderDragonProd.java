package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers T2 super flyer: a jade-and-gold sky serpent whose chain lightning arcs through up to three more foes. Trained at the Celestial Observatory. */
public class ThunderDragonProd extends StormT2Prod {

    public final static String itemName = "Thunder Dragon";
    public final static ResourceCost cost = ResourceCosts.THUNDER_DRAGON;

    public ThunderDragonProd() {
        super(itemName, "thunder_dragon_unit", "thunder_dragon", 3, cost, () -> EntityRegistrar.THUNDER_DRAGON_UNIT.get(), false);
    }
}
