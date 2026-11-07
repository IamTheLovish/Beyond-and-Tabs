package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Where the Tithe Binder's kills and assists come from (design sheet 7.1; the arithmetic is {@link TitheLedger}).
 * Every weapon that hurts a unit and leaves it alive records "this Binder hit it at tick t" here; when a unit dies,
 * the Binder that landed the killing blow (the damage source's entity: his weapons hurt as his projectiles) gets a
 * full bind, and every other Binder whose last hit is inside the 3 s window gets half. A summon gives nothing, a
 * worker half (TitheLedger.bindHalves). Only enemies count: a unit of the Binder's own side or an ally gives nothing.
 * The record is server-only, keyed by entity id, dropped on the victim's death, pruned every 5 s and cleared when the
 * server stops.
 */
public final class TitheBinderServerEvents {
    private TitheBinderServerEvents() { }

    private static final int PRUNE_EVERY_TICKS = 100;
    /** victim id -> (Binder id -> game tick of that Binder's last hit on it) */
    private static final Int2ObjectOpenHashMap<Int2LongOpenHashMap> HITS = new Int2ObjectOpenHashMap<>();

    /** A Binder's weapon hurt {@code victim} at {@code tick} (and it lived). */
    public static void recordHit(LivingEntity binder, LivingEntity victim, long tick) {
        Int2LongOpenHashMap byBinder = HITS.get(victim.getId());
        if (byBinder == null) {
            byBinder = new Int2LongOpenHashMap();
            HITS.put(victim.getId(), byBinder);
        }
        byBinder.put(binder.getId(), tick);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (!(victim.level() instanceof ServerLevel sl))
            return;
        Int2LongOpenHashMap assists = HITS.remove(victim.getId());
        Entity killer = evt.getSource().getEntity();
        if (!(victim instanceof Unit v) || (assists == null && !(killer instanceof TitheBinderUnit)))
            return;
        boolean summon = Essence.isSummoned(victim);
        boolean worker = victim instanceof WorkerUnit;
        long now = sl.getGameTime();
        if (killer instanceof TitheBinderUnit kb)
            credit(kb, v, TitheLedger.bindHalves(true, worker, summon, true));
        if (assists == null)
            return;
        for (int id : assists.keySet().toIntArray()) {
            if (killer != null && id == killer.getId())
                continue;
            if (sl.getEntity(id) instanceof TitheBinderUnit b)
                credit(b, v, TitheLedger.bindHalves(false, worker, summon, TitheLedger.inAssistWindow(assists.get(id), now)));
        }
    }

    private static void credit(TitheBinderUnit binder, Unit victim, int halves) {
        if (halves <= 0 || !binder.isAlive())
            return;
        String mine = binder.getOwnerName(), theirs = victim.getOwnerName();
        if (mine == null || mine.isEmpty() || theirs == null || AlliancesServerEvents.isAlliedOrOwned(mine, theirs))
            return;
        binder.credit(halves);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || HITS.isEmpty()
                || evt.getServer().getTickCount() % PRUNE_EVERY_TICKS != 0)
            return;
        long now = evt.getServer().overworld().getGameTime();
        for (int victimId : HITS.keySet().toIntArray()) {
            Int2LongOpenHashMap byBinder = HITS.get(victimId);
            long newest = Long.MIN_VALUE;
            for (long t : byBinder.values().toLongArray())
                newest = Math.max(newest, t);
            if (now - newest > TitheLedger.ASSIST_WINDOW_TICKS)
                HITS.remove(victimId);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        HITS.clear();
    }
}
