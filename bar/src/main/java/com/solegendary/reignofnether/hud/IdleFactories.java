package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side list of the player's idle factories: finished buildings that can train units (a lab, a harbour, a
 * capitol) and have nothing queued. BAR nags about these as loudly as about idle constructors; lovish's clip had five
 * labs idle for most of a 17 minute game with nothing on screen saying so. Refreshed every {@link #POLL_TICKS}
 * (one pass over the building list, no per-frame work), read by the idle-factory HUD button
 * (HelperButtons.idleFactoryButton) and by NotificationClientEvents' "Idle factories: N" toast.
 */
public class IdleFactories {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int POLL_TICKS = 10;

    private static final List<ProductionPlacement> idle = new ArrayList<>();
    // whether a building type trains units at all (research-only buildings are never "idle factories"), by type
    private static final Map<ProductionBuilding, Boolean> trainsUnits = new IdentityHashMap<>();
    private static int pollTicks = 0;
    private static int cycleIndex = 0;

    /** The idle factories as of the last poll (owned, built, empty queue). Read-only for callers. */
    public static List<ProductionPlacement> get() {
        return idle;
    }

    public static int count() {
        return idle.size();
    }

    /** Selects and jumps the camera to the next idle factory (round-robin, like the idle-worker button). */
    public static void cycle() {
        if (idle.isEmpty())
            return;
        if (cycleIndex >= idle.size())
            cycleIndex = 0;
        ProductionPlacement next = idle.get(cycleIndex++);
        focus(next);
    }

    /** Selects one building and centres the camera on it. */
    public static void focus(BuildingPlacement b) {
        UnitClientEvents.clearSelectedUnits();
        BuildingClientEvents.clearSelectedBuildings();
        BuildingClientEvents.addSelectedBuilding(b);
        OrthoviewClientEvents.centreCameraOnPos(b.centrePos);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || --pollTicks > 0)
            return;
        pollTicks = POLL_TICKS;
        idle.clear();
        if (MC.player == null || MC.level == null)
            return;
        String me = MC.player.getName().getString();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings()) {
            if (!(b instanceof ProductionPlacement pp) || !b.isBuilt || !me.equals(b.ownerName)
                    || !pp.productionQueue.isEmpty())
                continue;
            if (b.getBuilding() instanceof ProductionBuilding pb && trainsUnits(pb))
                idle.add(pp);
        }
    }

    private static boolean trainsUnits(ProductionBuilding pb) {
        Boolean cached = trainsUnits.get(pb);
        if (cached != null)
            return cached;
        boolean any = false;
        for (ProductionItem item : pb.productions.get())
            if (item instanceof IUnitProductionItem) {
                any = true;
                break;
            }
        trainsUnits.put(pb, any);
        return any;
    }
}
