package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks skirmisher: dwarven musketeers. Trained at the Machine Shop. */
public class RifleSquadProd extends BrassProd {

    public final static String itemName = "Rifle Squad";
    public final static ResourceCost cost = ResourceCosts.RIFLE_SQUAD;

    public RifleSquadProd() {
        super(itemName, "rifle_squad_unit", "rifle_squad", 2, cost, () -> EntityRegistrar.RIFLE_SQUAD_UNIT.get(), true);
    }
}
