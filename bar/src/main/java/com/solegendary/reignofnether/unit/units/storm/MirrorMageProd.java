package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers T2 support: Mirror Images conjures three harmless decoys of a friendly unit that draw the enemy's fire. Trained at the Celestial Observatory. */
public class MirrorMageProd extends StormT2Prod {

    public final static String itemName = "Mirror Mage";
    public final static ResourceCost cost = ResourceCosts.MIRROR_MAGE;

    public MirrorMageProd() {
        super(itemName, "mirror_mage_unit", "mirror_mage", 3, cost, () -> EntityRegistrar.MIRROR_MAGE_UNIT.get(), true);
    }
}
