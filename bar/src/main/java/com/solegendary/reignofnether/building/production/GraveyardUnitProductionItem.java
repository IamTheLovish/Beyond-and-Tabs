package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.resources.ResourceCost;

/**
 * Units a Graveyard raises. Reign of Nether let an overflowing graveyard (the Overflowing Graveyard research, or
 * Level 2 / 3) bank finished units as skulls on its slots - its way past the population cap. There is no cap any more
 * (lovish, Oct 29: "there should be no population in this game"), so nothing is banked: every finished unit walks out,
 * like any lab's. A skull from an old save still walks out through GraveyardPlacement.releaseNextUnit.
 */
public abstract class GraveyardUnitProductionItem extends ProductionItem {

    public GraveyardUnitProductionItem(ResourceCost cost) {
        super(cost, ProdDupeRule.ALLOW);
    }
}
