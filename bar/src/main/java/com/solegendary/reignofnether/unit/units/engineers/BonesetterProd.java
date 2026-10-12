package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Gravebound combat engineer, trained at the Dungeon once Tier 2 is researched. (design/combat_engineers.md) */
public class BonesetterProd extends CombatEngineerProd {

    public final static String itemName = "Bonesetter";
    public final static ResourceCost cost = ResourceCosts.BONESETTER;

    public BonesetterProd() {
        super(itemName, "bonesetter_unit", "bonesetter", 4, cost, () -> EntityRegistrar.BONESETTER_UNIT.get(), false);
    }
}
