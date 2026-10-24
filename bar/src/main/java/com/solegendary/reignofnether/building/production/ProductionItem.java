package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.debug.RtsDebugClientEvents;
import com.solegendary.reignofnether.gamerules.GameruleClient;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.EconomyClientEvents;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;

// units and/or research tech that a ProductionBuilding can produce
public abstract class ProductionItem {

    public ResourceCost defaultCost;
    public BiConsumer<Level, ProductionPlacement> onComplete;
    public ProdDupeRule dupeRule;

    public ProductionItem(ResourceCost cost, ProdDupeRule dupeRule, BiConsumer<Level, ProductionPlacement> onComplete) {
        this.defaultCost = cost;
        this.dupeRule = dupeRule;
        this.onComplete = onComplete;
    }

    public ProductionItem(ResourceCost cost, ProdDupeRule dupeRule) {
        this.defaultCost = cost;
        this.dupeRule = dupeRule;
    }

    public ProductionItem(ResourceCost cost) {
        this.defaultCost = cost;
        this.dupeRule = ProdDupeRule.ALLOW;
    }

    // is the player allowed to start this production item?
    public boolean canProduce(ProductionPlacement pp) {
        return getProduceErrorMsg(pp) == null;
    }

    @Nullable
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return null;
    }

    // allows for dynamic costs in subclasses
    public ResourceCost getCost(boolean isClientSide, String ownerName) {
        return defaultCost;
    }

    public abstract String getItemName();

    // BAR flow economy: metal/energy are paid while the item is produced, so queueing only needs population
    // (and emeralds, which are still paid upfront for item-shop style costs)
    public boolean canAfford(ProductionPlacement pp) {
        for (Resources resources : ResourcesServerEvents.resourcesList)
            if (resources.ownerName.equals(pp.ownerName))
                return resources.emerald >= getCost(pp.getLevel().isClientSide(), pp.ownerName).emerald &&
                        canAffordPopulation(pp);
        return false;
    }

    /** This item's population cost, as the side the placement lives on knows it (a research is 0). */
    protected int populationCost(ProductionPlacement pp) {
        return getCost(pp.getLevel().isClientSide(), pp.ownerName).population;
    }

    /** The owner's population (units plus everything queued) as the placement's side counts it. */
    protected int populationInUse(ProductionPlacement pp) {
        return pp.getLevel().isClientSide() ? UnitClientEvents.getCurrentPopulation(pp.ownerName)
            : UnitServerEvents.getCurrentPopulation(pp.ownerName);
    }

    /** The owner's population cap as the placement's side knows it (BAR: the host's maxPopulation for everyone). */
    protected int populationSupply(ProductionPlacement pp) {
        return pp.getLevel().isClientSide() ? BuildingClientEvents.getTotalPopulationSupply(pp.ownerName)
            : BuildingServerEvents.getTotalPopulationSupply(pp.ownerName);
    }

    // the rules themselves are PopulationRules (one tested copy for client, server and graveyards); the counting is
    // asked only for items that cost population - getCurrentPopulation walks every building's queue

    public boolean canAffordPopulation(ProductionPlacement pp) {
        int cost = populationCost(pp);
        if (cost == 0)
            return true;

        for (Resources resources : ResourcesServerEvents.resourcesList)
            if (resources.ownerName.equals(pp.ownerName))
                return PopulationRules.verdict(UnitServerEvents.getCurrentPopulation(pp.ownerName),
                    BuildingServerEvents.getTotalPopulationSupply(pp.ownerName), cost, 0, 0).canQueue();
        return false;
    }

    // check we didn't dip below pop supply after starting production
    public boolean isBelowPopulationSupply(ProductionPlacement pp) {
        int cost = populationCost(pp);
        if (cost == 0)
            return true;
        return PopulationRules.keepsProducing(populationInUse(pp), populationSupply(pp), cost, 0, 0);
    }

    public boolean isBelowMaxPopulation(ProductionPlacement pp) {
        int cost = populationCost(pp);
        if (cost == 0)
            return true;

        int currentPop = UnitServerEvents.getCurrentPopulation(pp.ownerName);

        for (Resources resources : ResourcesServerEvents.resourcesList) {
            if (resources.ownerName.equals(pp.ownerName)) {
                int max = pp.getLevel().isClientSide() ? GameruleClient.maxPopulation : UnitServerEvents.maxPopulation;
                return PopulationRules.withinMax(currentPop, cost, max);
            }
        }
        return false;
    }

    // some items (eg. research) are enabled only if the item doesn't exist in any existing clientside queue
    public boolean itemIsBeingProduced(String ownerName) {
        return itemIsBeingProduced(true, ownerName);
    }

    public boolean itemIsBeingProduced(boolean isClientSide, String ownerName) {
        List<BuildingPlacement> buildings = isClientSide ? BuildingClientEvents.getBuildings() : BuildingServerEvents.getBuildings();

        for (BuildingPlacement building : buildings)
            if (building.ownerName.equals(ownerName) && building instanceof ProductionPlacement prodBuilding)
                for (ActiveProduction prodItem : prodBuilding.productionQueue)
                    if (prodItem.item == this)
                        return true;
        return false;
    }

    // check if this is being produced at one particular building
    public boolean itemIsBeingProducedAt(ProductionPlacement pp) {
        return itemIsBeingProducedAt(true, pp);
    }

    public boolean itemIsBeingProducedAt(boolean isClientSide, ProductionPlacement pp) {
        List<BuildingPlacement> buildings = isClientSide ? BuildingClientEvents.getBuildings() : BuildingServerEvents.getBuildings();

        for (BuildingPlacement building : buildings)
            if (building == pp)
                for (ActiveProduction prodItem : pp.productionQueue)
                    if (prodItem.item == this)
                        return true;
        return false;
    }

    // Button object to build
    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding keybinding) {
        return null;
    }
    // Button object to show in-progress items
    // firstItem means this button will cancel the currently-building item
    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return null;
    }

    /**
     * Research and upgrades (anything that isn't a unit) cost less while the owner or an ally holds an Ancient
     * Forge capture site (design/maps.md). Public for the game test.
     */
    public float forgeDiscount(String owner) {
        if (this instanceof IUnitProductionItem)
            return 1f;
        return com.solegendary.reignofnether.startpos.CapturePointServerEvents.researchCostMultiplier(owner);
    }

    // return true if the tick finished

    // BAR flow economy: nothing was paid when the item was queued. Each tick the item asks the owner's economy for
    // the metal/energy matching the progress it wants to make (production build power * cheat speed); progress is
    // slowed down by the owner's stall when they are short of resources.
    public boolean tick(ProductionPlacement placement, ActiveProduction active) {
        // a queue made while the building is still going up waits whole: no progress, no payment, and no completing a
        // zero-tick item (those skipped the isBuilt check below)
        if (!placement.isBuilt)
            return false;
        boolean isClientSide = placement.getLevel().isClientSide();
        boolean hasCheat = (isClientSide && ResearchClient.hasCheat("warpten")) ||
                (!isClientSide && ResearchServerEvents.playerHasCheat(placement.ownerName, "warpten"));
        float speed = (hasCheat ? 10f : 1f) * placement.getProductionBuildPower();
        // economy pace (EconomyPace): production runs at the match's pace like income and construction; the client
        // predicts with the synced match pace
        speed *= isClientSide ? com.solegendary.reignofnether.resources.EconomyPace.clientMultiplier()
            : com.solegendary.reignofnether.resources.EconomyPace.of(placement.ownerName);

        // asked once per step, not three times: each ask walks every building's queue (getCurrentPopulation), per
        // producing item per tick - at 8v8 that was the bulk of the production step (design/code_review.md CR-08)
        boolean belowSupply = isBelowPopulationSupply(placement);
        if (active.ticksLeft > 0 && belowSupply && placement.isBuilt) {
            if (isClientSide) {
                // clientside prediction only; completion is always decided by the server
                active.ticksLeft -= (RtsDebugClientEvents.getCappedTPS() / 20D) * speed *
                        EconomyClientEvents.getStall(placement.ownerName);
            } else {
                ResourceCost cost = getCost(false, placement.ownerName);
                float totalTicks = Math.max(1, cost.ticks);
                float dTicks = Math.min(active.ticksLeft, speed);
                float df = dTicks / totalTicks;
                float disc = forgeDiscount(placement.ownerName);
                float granted = hasCheat ? 1f : EconomyServerEvents.requestFlow(placement.ownerName, cost.metal() * disc, cost.energy() * disc, df);
                if (!hasCheat) {
                    active.metalSpent += cost.metal() * disc * df * granted;
                    active.energySpent += cost.energy() * disc * df * granted;
                }
                active.ticksLeft -= dTicks * granted;
            }
            if (active.ticksLeft < 0)
                active.ticksLeft = 0;
        }
        // items without a production time still have to be paid for in full before they complete
        if (!isClientSide && active.ticksLeft <= 0 && !active.completed && belowSupply) {
            ResourceCost cost = getCost(false, placement.ownerName);
            if (cost.ticks <= 0 && active.paidFraction < 1f && !hasCheat) {
                float df = 1f - active.paidFraction;
                float disc = forgeDiscount(placement.ownerName);
                float granted = EconomyServerEvents.requestFlow(placement.ownerName, cost.metal() * disc, cost.energy() * disc, df);
                active.paidFraction += df * granted;
                active.metalSpent += cost.metal() * disc * df * granted;
                active.energySpent += cost.energy() * disc * df * granted;
                if (active.paidFraction < 0.999f)
                    return false;
            }
        }
        if (!isClientSide && active.ticksLeft <= 0 && belowSupply && !active.completed) {
            active.complete(placement);
            return true;
        }
        return false;
    }
}
