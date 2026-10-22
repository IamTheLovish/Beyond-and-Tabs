package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.unit.IdleTracker;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.unit.UnitClientEvents.getPlayerToEntityRelationship;

/**
 * The player's idle army: owned military units (the select-army helper's set) that have stood still for
 * {@link IdleTracker#IDLE_AFTER_MS}. One pass over the unit list every {@link #POLL_TICKS} (no per-frame work); the
 * strategic icons and the in-world rings read {@link #isIdle} in O(1), the select-army button shows {@link #count()}.
 */
public class IdleArmy {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int POLL_TICKS = 10;

    private static final IdleTracker TRACKER = new IdleTracker();
    private static final List<LivingEntity> IDLE = new ArrayList<>();
    private static int pollTicks = 0;

    /** Is this unit part of the select-army set (owned, fighting kind, not a scout, not garrisoned)? */
    public static boolean isArmy(LivingEntity u) {
        return u instanceof Unit unit && !(u instanceof WorkerUnit) && !unit.isScout()
                && GarrisonableBuildingAddon.getGarrison(unit) == null
                && getPlayerToEntityRelationship(u) == Relationship.OWNED;
    }

    public static boolean isIdle(LivingEntity u) {
        return TRACKER.isIdle(u.getId());
    }

    public static int count() {
        return IDLE.size();
    }

    /** The idle army as of the last poll. Read-only for callers. */
    public static List<LivingEntity> get() {
        return IDLE;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || --pollTicks > 0)
            return;
        pollTicks = POLL_TICKS;
        IDLE.clear();
        if (MC.player == null || MC.level == null) {
            TRACKER.clear();
            return;
        }
        long now = System.currentTimeMillis();
        TRACKER.beginPass();
        for (LivingEntity u : UnitClientEvents.getAllUnits()) {
            if (!u.isAlive() || u.isRemoved() || !isArmy(u))
                continue;
            boolean busy = u.swinging || u.hurtTime > 0 || u.isUsingItem() || u.isPassenger();
            if (TRACKER.observe(u.getId(), u.getX(), u.getZ(), busy, now))
                IDLE.add(u);
        }
        TRACKER.endPass();
    }
}
