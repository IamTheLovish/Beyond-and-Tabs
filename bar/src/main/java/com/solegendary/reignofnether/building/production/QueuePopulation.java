package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.villagers.IronGolemBuilding;
import com.solegendary.reignofnether.resources.ResourceCosts;

import java.util.HashMap;
import java.util.List;

/**
 * The production-queue part of an owner's population (queued units, plus an Iron Golem being built), memoised per
 * owner for one side's current tick. getCurrentPopulation walked every building with a string compare and a cost
 * lookup per queued item on every call, and it is called per producing item per tick (ProductionItem.tick) - at 8v8,
 * ~1,700 buildings times every busy lab (Oct 26 perf audit). Now one walk per owner per tick.
 * <p>
 * Exact within the tick for what changes there: any queue change (ProductionQueueList's epoch) or a building joining
 * or leaving the list (its size) recounts at once, so queuing several items in one tick is checked as before. What
 * changes without either - a building changing owner, a graveyard's level, a research that changes a unit's
 * population cost - counts from the next tick, as the unit part of the population already does.
 */
public final class QueuePopulation {
    private final HashMap<String, Integer> byOwner = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int buildingCount = -1;
    private long epoch = -1;

    /**
     * The owner's queued population over {@code buildings} as the given side counts it. {@code tick} is that side's
     * level game time; {@code cacheable} false (no level yet) counts afresh every call.
     */
    public int of(String ownerName, List<BuildingPlacement> buildings, boolean clientSide, long tick, boolean cacheable) {
        if (!cacheable)
            return count(ownerName, buildings, clientSide);
        long nowEpoch = ProductionQueueList.epoch();
        if (tick != this.tick || buildings.size() != buildingCount || nowEpoch != epoch) {
            byOwner.clear();
            this.tick = tick;
            buildingCount = buildings.size();
            epoch = nowEpoch;
        }
        Integer cached = byOwner.get(ownerName);
        if (cached != null)
            return cached;
        int counted = count(ownerName, buildings, clientSide);
        byOwner.put(ownerName, counted);
        return counted;
    }

    /** The walk getCurrentPopulation always did, unchanged. */
    static int count(String ownerName, List<BuildingPlacement> buildings, boolean clientSide) {
        int population = 0;
        for (BuildingPlacement building : buildings)
            if (building.ownerName.equals(ownerName)) {
                if (building instanceof ProductionPlacement prodPlacement) {
                    for (ActiveProduction prodItem : prodPlacement.productionQueue)
                        if (!(prodPlacement instanceof GraveyardPlacement gy) || gy.getUpgradeLevel() <= 0)
                            population += prodItem.item.getCost(clientSide, ownerName).population;
                } else if (building.getBuilding() instanceof IronGolemBuilding) {
                    population += ResourceCosts.IRON_GOLEM.population;
                }
            }
        return population;
    }
}
