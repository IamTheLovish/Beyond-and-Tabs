package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.EconomyClientEvents;

import java.util.HashSet;
import java.util.Set;

/**
 * "Pause unit production" (lovish, Oct 23: "a button to pause all production in case you are stalling metal, so you
 * can upgrade what really matters"). One flag per player: while it is on, every unit item in that player's production
 * buildings makes no progress and spends nothing ({@link ProductionItem#tick}); research and upgrades (anything that is
 * not an {@link IUnitProductionItem}) keep running. The server holds the flag; the owner's HUD gets it with the
 * economy sync ({@code EconomyClientboundPacket.unitsPaused}), which the client's production prediction also reads.
 * Not saved with the world on purpose: it is a few-minute tactical switch, and a match reset clears it.
 */
public final class UnitProductionPause {

    private static final Set<String> paused = new HashSet<>();

    private UnitProductionPause() { }

    public static boolean isPaused(String owner) {
        return owner != null && paused.contains(owner);
    }

    public static void set(String owner, boolean on) {
        if (owner == null || owner.isEmpty())
            return;
        if (on)
            paused.add(owner);
        else
            paused.remove(owner);
    }

    /** The packet's / keybind's toggle; returns the new state. */
    public static boolean toggle(String owner) {
        set(owner, !isPaused(owner));
        return isPaused(owner);
    }

    public static void clearOwner(String owner) {
        paused.remove(owner);
    }

    /** New match / reset: nothing stays paused. */
    public static void clearAll() {
        paused.clear();
    }

    /** True when {@code item} is a unit that its owner has paused (server: the set; client: the synced flag). */
    public static boolean blocks(ProductionItem item, ProductionPlacement placement) {
        if (!(item instanceof IUnitProductionItem))
            return false;
        return placement.getLevel().isClientSide()
            ? EconomyClientEvents.getEconomy(placement.ownerName).unitsPaused
            : isPaused(placement.ownerName);
    }
}
