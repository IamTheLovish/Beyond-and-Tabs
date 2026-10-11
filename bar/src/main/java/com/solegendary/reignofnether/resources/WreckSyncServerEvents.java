package com.solegendary.reignofnether.resources;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Tells clients how much metal each wreck holds, for the BAR reclaim labels (WreckClientEvents). A wreck's metal is
 * server-only persistent data on a block-display entity, so once per second the wrecks whose whole-metal value
 * changed since the last broadcast (new heaps, heaps being stripped) go out in one packet - a few bytes each, nothing
 * at all on a quiet map - and a joining player gets the full list once. Clients drop entries whose entity is gone.
 */
public class WreckSyncServerEvents {

    public static final int SYNC_TICKS = 20;

    private static final Int2IntOpenHashMap lastSent = new Int2IntOpenHashMap();
    private static final IntArrayList ids = new IntArrayList();
    private static final IntArrayList metals = new IntArrayList();

    static int wholeMetal(Entity w) {
        return Math.max(0, Math.round(WreckServerEvents.metalOf(w)));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null
                || evt.getServer().getTickCount() % SYNC_TICKS != 0)
            return;
        List<Entity> wrecks = WreckServerEvents.getWrecks();
        if (wrecks.isEmpty() && lastSent.isEmpty())
            return;
        ids.clear();
        metals.clear();
        for (int i = 0; i < wrecks.size(); i++) {
            Entity w = wrecks.get(i);
            if (w.isRemoved())
                continue;
            int m = wholeMetal(w);
            if (lastSent.getOrDefault(w.getId(), -1) != m) {
                lastSent.put(w.getId(), m);
                ids.add(w.getId());
                metals.add(m);
            }
        }
        if (lastSent.size() > 4 * WreckServerEvents.MAX_WRECKS)   // ids of long-gone wrecks: start over now and then
            lastSent.clear();
        if (!ids.isEmpty())
            WreckSyncClientboundPacket.sendToAll(ids.toIntArray(), metals.toIntArray());
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp))
            return;
        List<Entity> wrecks = WreckServerEvents.getWrecks();
        if (wrecks.isEmpty())
            return;
        ids.clear();
        metals.clear();
        for (Entity w : wrecks) {
            if (w.isRemoved())
                continue;
            ids.add(w.getId());
            metals.add(wholeMetal(w));
        }
        WreckSyncClientboundPacket.sendTo(sp, ids.toIntArray(), metals.toIntArray());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        lastSent.clear();
    }
}
