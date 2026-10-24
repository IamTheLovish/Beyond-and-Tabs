package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.ResourceCost;

/**
 * Units a Graveyard raises. An overflowing graveyard (the Overflowing Graveyard research, or Level 2 / 3, whose
 * structures are drawn on it) can bank finished units as skulls on its slots - Reign of Nether's way past the
 * population cap. The bank is ONLY for that: while the army has room, a finished unit walks out like any lab's.
 *
 * Why (playtest Oct 22 19:20, multiplayer, build a8bf003: "the death buildings werent making any units and said
 * graveyard is full"): RoN banked EVERY unit of an overflowing graveyard as a skull and released skulls only with the
 * release toggle on (off by default). Our population is unlimited (ResourceCosts.UNLIMITED_POPULATION) and building
 * levels made every L2/L3 graveyard overflowing, so a levelled graveyard turned each unit into a skull that never
 * walked out, and after ten skulls (or ten queued units) refused everything with "This graveyard is too full".
 *
 * The rules (who may queue, when a finished unit banks, when production pauses) are PopulationRules - the same
 * tested copy the ordinary labs use; this class only supplies the graveyard's slots and what is committed to them.
 */
public abstract class GraveyardUnitProductionItem extends ProductionItem {

    public GraveyardUnitProductionItem(ResourceCost cost) {
        super(cost, ProdDupeRule.ALLOW);
    }

    private int getMaxSkulls(ProductionPlacement pp) {
        if (pp instanceof GraveyardPlacement gy && gy.getUpgradeLevel() > 0) {
            return gy.getMaxSkulls();
        } else {
            return 0;
        }
    }

    /** Whether the owner's army has room for one more of this unit (current population + its cost within supply). */
    public boolean armyHasRoom(ProductionPlacement pp) {
        int cost = populationCost(pp);
        return cost == 0 || PopulationRules.hasRoom(populationInUse(pp), populationSupply(pp), cost);
    }

    /**
     * Called by each unit's onComplete: true when the finished unit goes into the graveyard as a skull instead of
     * walking out - only an overflowing graveyard whose owner's army is at the cap.
     */
    public boolean banksAsSkull(ProductionPlacement pp) {
        int cost = populationCost(pp);
        int maxSkulls = getMaxSkulls(pp);
        if (cost == 0 || maxSkulls <= 0)
            return false;
        return PopulationRules.banksOnCompletion(populationInUse(pp), populationSupply(pp), cost, maxSkulls);
    }

    /** Skulls on the slots plus graveyard units in the queue (each would become one at the cap). */
    private static int skullsCommitted(GraveyardPlacement gy) {
        return gy.getTotalSkulls() + gy.getSkullsInProgress();
    }

    @Override
    public boolean canAffordPopulation(ProductionPlacement pp) {
        int cost = populationCost(pp);
        if (cost == 0)
            return true;

        int maxSkulls = getMaxSkulls(pp);
        if (maxSkulls <= 0)
            return super.canAffordPopulation(pp);   // no slots: an ordinary lab
        return PopulationRules.verdict(populationInUse(pp), populationSupply(pp), cost,
            skullsCommitted((GraveyardPlacement) pp), maxSkulls).canQueue();
    }

    @Override
    public boolean isBelowPopulationSupply(ProductionPlacement pp) {
        int cost = populationCost(pp);
        if (cost == 0)
            return true;

        int maxSkulls = getMaxSkulls(pp);
        return PopulationRules.keepsProducing(populationInUse(pp), populationSupply(pp), cost,
            maxSkulls > 0 ? skullsCommitted((GraveyardPlacement) pp) : 0, maxSkulls);
    }

    @Override
    public boolean isBelowMaxPopulation(ProductionPlacement pp) {
        // an overflowing graveyard banks what the army cannot hold, so the host's cap is not its business; one without
        // slots is an ordinary lab. (This used to ask canAffordPopulation - the supply, which BAR sets equal to the
        // cap - so it was right only by that coincidence.)
        if (populationCost(pp) == 0 || getMaxSkulls(pp) > 0)
            return true;
        return super.isBelowMaxPopulation(pp);
    }
}
