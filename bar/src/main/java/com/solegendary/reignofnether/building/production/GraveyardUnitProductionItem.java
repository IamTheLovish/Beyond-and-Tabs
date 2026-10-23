package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;

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

    private int population(ProductionPlacement pp) {
        return getCost(pp.getLevel().isClientSide(), pp.ownerName).population;
    }

    /** Whether the owner's army has room for one more of this unit (current population + its cost within supply). */
    public boolean armyHasRoom(ProductionPlacement pp) {
        int pop = population(pp);
        if (pop == 0)
            return true;
        boolean client = pp.getLevel().isClientSide();
        int currentPop = client ? UnitClientEvents.getCurrentPopulation(pp.ownerName)
                : UnitServerEvents.getCurrentPopulation(pp.ownerName);
        int popSupply = client ? BuildingClientEvents.getTotalPopulationSupply(pp.ownerName)
                : BuildingServerEvents.getTotalPopulationSupply(pp.ownerName);
        return currentPop + pop <= popSupply;
    }

    /**
     * Called by each unit's onComplete: true when the finished unit goes into the graveyard as a skull instead of
     * walking out - only an overflowing graveyard whose owner's army is at the cap.
     */
    public boolean banksAsSkull(ProductionPlacement pp) {
        return getMaxSkulls(pp) > 0 && !armyHasRoom(pp);
    }

    /** Skulls on the slots plus graveyard units in the queue (each would become one at the cap). */
    private static int skullsCommitted(GraveyardPlacement gy) {
        return gy.getTotalSkulls() + gy.getSkullsInProgress();
    }

    @Override
    public boolean canAffordPopulation(ProductionPlacement pp) {
        if (population(pp) == 0)
            return true;

        int maxSkulls = getMaxSkulls(pp);
        if (maxSkulls <= 0)
            return super.canAffordPopulation(pp);
        if (armyHasRoom(pp))
            return true;   // the unit walks out: the skull bank is not involved
        // at the cap: an overflowing graveyard may still bank it, while it has empty slots
        return skullsCommitted((GraveyardPlacement) pp) + population(pp) <= maxSkulls;
    }

    @Override
    public boolean isBelowPopulationSupply(ProductionPlacement pp) {
        if (population(pp) == 0)
            return true;

        if (super.isBelowPopulationSupply(pp))
            return true;
        int maxSkulls = getMaxSkulls(pp);
        if (maxSkulls <= 0)
            return false;
        // over the cap: an overflowing graveyard keeps producing only while its bank can take what is queued
        return skullsCommitted((GraveyardPlacement) pp) <= maxSkulls;
    }

    @Override
    public boolean isBelowMaxPopulation(ProductionPlacement pp) {
        if (population(pp) == 0)
            return true;

        int maxSkulls = getMaxSkulls(pp);
        if (maxSkulls <= 0) {
            return super.canAffordPopulation(pp);
        } else {
            return true;
        }
    }
}
