package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Steward (capitol research, {@code ResearchSteward}): once a player has it, each of their built capitols trains
 * its worker on its own whenever the player holds fewer workers than the tier's quota - workers alive plus workers
 * already in training anywhere - and that capitol's queue is empty, so it never competes with what the player queued
 * there. One worker per capitol per second at most; a capitol with its Steward paused (HUD toggle) is skipped. Checks
 * are done quietly: no warning every second when a capitol cannot take another worker.
 */
public class StewardServerEvents {

    /** Worker quota by tier - the Steward stops at this count (tunable in one place; bots keep their own targets). */
    public static final int QUOTA_T1 = 8;
    public static final int QUOTA_T2 = 12;
    public static final int QUOTA_T3 = 16;
    private static final int PERIOD_TICKS = 20;

    private static int ticks = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ++ticks % PERIOD_TICKS != 0)
            return;
        Map<String, List<ProductionPlacement>> capitols = new HashMap<>();
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
            if (bp instanceof ProductionPlacement pp && eligible(pp))
                capitols.computeIfAbsent(pp.ownerName, k -> new ArrayList<>()).add(pp);
        for (Map.Entry<String, List<ProductionPlacement>> e : capitols.entrySet())
            tend(e.getKey(), e.getValue());
    }

    /** A built, owned, unpaused capitol whose owner has the Steward. */
    public static boolean eligible(ProductionPlacement pp) {
        return pp.isCapitol && pp.isBuilt && !pp.isDestroyedServerside && !pp.decommissioning && !pp.stewardPaused
            && pp.getLevel() != null && !pp.getLevel().isClientSide()
            && ResearchServerEvents.playerHasResearch(pp.ownerName, ProductionItems.RESEARCH_STEWARD);
    }

    /** The worker quota {@code owner} is held to. */
    public static int quota(String owner) {
        if (ResearchServerEvents.playerHasResearch(owner, ProductionItems.RESEARCH_TIER_3))
            return QUOTA_T3;
        if (ResearchServerEvents.playerHasResearch(owner, ProductionItems.RESEARCH_TIER_2))
            return QUOTA_T2;
        return QUOTA_T1;
    }

    /** The item a capitol trains as its worker: the one on abilitySlot1 (a convention every capitol keeps). */
    public static ProductionItem workerItem(ProductionPlacement capitol) {
        if (capitol.getBuilding() instanceof ProductionBuilding pb)
            return pb.productions.forHotkey(Keybindings.abilitySlot1);
        return null;
    }

    /** Workers {@code owner} has alive plus workers in training in any of their buildings. */
    public static int workersHeld(String owner) {
        int held = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof WorkerUnit && le.isAlive() && le instanceof Unit u && owner.equals(u.getOwnerName()))
                held++;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
            if (bp instanceof ProductionPlacement pp && owner.equals(pp.ownerName) && !pp.isDestroyedServerside) {
                ProductionItem worker = workerItem(pp);
                for (ActiveProduction ap : pp.productionQueue)
                    if (ap.item == worker && worker != null)
                        held++;
            }
        return held;
    }

    /**
     * Tops {@code owner}'s workers up from their eligible {@code capitols}: at most one per capitol, never past the
     * quota. Returns how many workers were queued (the GameTest's hook).
     */
    public static int tend(String owner, List<ProductionPlacement> capitols) {
        int deficit = quota(owner) - workersHeld(owner);
        int queued = 0;
        for (ProductionPlacement capitol : capitols) {
            if (deficit <= 0)
                break;
            if (!capitol.productionQueue.isEmpty())
                continue;
            ProductionItem worker = workerItem(capitol);
            if (worker == null || !worker.canProduce(capitol) || !worker.canAfford(capitol))
                continue;
            if (capitol.startProductionItem(worker)) {
                BuildingProductionClientboundPacket.startProduction(owner, capitol.originPos, worker);
                deficit--;
                queued++;
            }
        }
        return queued;
    }
}
