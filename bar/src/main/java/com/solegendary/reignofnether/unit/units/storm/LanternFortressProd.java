package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Stormcallers T2 area shield: a slow, tough elephant that does not fight but carries the Lantern Dome, a channelled
 * dome that soaks projectile damage to friends inside. Trained at the Celestial Observatory (Tier 2). It walks out
 * beside the lab: a 2-block elephant does not fit through the door.
 */
public class LanternFortressProd extends StormT2Prod {

    public final static String itemName = "Lantern Fortress";
    public final static ResourceCost cost = ResourceCosts.LANTERN_FORTRESS;

    public LanternFortressProd() {
        super(itemName, "lantern_fortress_unit", "lantern_fortress", 3, cost, () -> EntityRegistrar.LANTERN_FORTRESS_UNIT.get(), false);
    }
}
