package com.solegendary.reignofnether.unit;

import net.minecraft.world.entity.Entity;

/**
 * Whether the per-tick selection sweep drops a unit. Own units that leave the player's tracking range are removed
 * client-side with DISCARDED (or UNLOADED_*), which is not a death: keep them selected (real removals still arrive
 * through onEntityLeave by id, and onEntityJoin swaps a re-tracked entity back in). Drop only dead/dying or KILLED.
 */
public final class SelectionKeep {
    private SelectionKeep() { }

    public static boolean shouldDrop(boolean deadOrDying, Entity.RemovalReason reason) {
        return deadOrDying || reason == Entity.RemovalReason.KILLED;
    }

    /**
     * A selected unit whose owner changed since the last tick (a Spirit Puppet taken or given back, a possession) is
     * dropped: the old side's orders would be refused, the new side never picked it. Null = first seen, kept.
     */
    public static boolean ownerFlipped(String ownerLastTick, String ownerNow) {
        return ownerLastTick != null && !ownerLastTick.equals(ownerNow);
    }
}
