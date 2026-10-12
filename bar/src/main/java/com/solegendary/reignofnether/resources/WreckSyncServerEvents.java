package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.network.Audience;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tells clients how much metal each wreck holds, for the BAR reclaim labels (WreckClientEvents). A wreck's metal is
 * server-only persistent data on a block-display entity, so once per second each player gets, in one packet, the
 * wrecks whose whole-metal value changed since they were last told - a few bytes each, nothing at all on a quiet map.
 * Only wrecks they can see (network/Audience SEEN; a wreck is neutral, so: in their fog's view, or they spectate):
 * the metal of a heap in the fog falling would tell an enemy that someone is reclaiming there (rewrite 3; it went to
 * everyone). Because "since they were last told" is per player, a heap that changed in the fog is re-sent the moment
 * it is seen again, and a joining player gets every heap they can see on the next pass. Clients drop entries whose
 * entity is gone.
 */
public class WreckSyncServerEvents {

    public static final int SYNC_TICKS = 20;

    // per online player: wreck id -> the whole metal they were last sent
    private static final Map<UUID, Int2IntOpenHashMap> lastSent = new HashMap<>();
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
        List<ServerPlayer> players = evt.getServer().getPlayerList().getPlayers();
        if (lastSent.size() > players.size()) {   // players who left
            Set<UUID> online = new HashSet<>();
            for (ServerPlayer sp : players)
                online.add(sp.getUUID());
            lastSent.keySet().removeIf(uuid -> !online.contains(uuid));
        }
        if (wrecks.isEmpty() || players.isEmpty())
            return;
        // each heap's metal once, not once per player
        int[] metal = new int[wrecks.size()];
        for (int i = 0; i < wrecks.size(); i++)
            metal[i] = wrecks.get(i).isRemoved() ? -1 : wholeMetal(wrecks.get(i));
        for (ServerPlayer sp : players) {
            Int2IntOpenHashMap last = lastSent.computeIfAbsent(sp.getUUID(), k -> new Int2IntOpenHashMap());
            ids.clear();
            metals.clear();
            for (int i = 0; i < wrecks.size(); i++) {
                Entity w = wrecks.get(i);
                if (metal[i] < 0 || last.getOrDefault(w.getId(), -1) == metal[i] || !Audience.mayKnow(sp, w))
                    continue;
                last.put(w.getId(), metal[i]);
                ids.add(w.getId());
                metals.add(metal[i]);
            }
            if (last.size() > 4 * WreckServerEvents.MAX_WRECKS)   // ids of long-gone wrecks: start over now and then
                last.clear();
            if (!ids.isEmpty())
                WreckSyncClientboundPacket.sendTo(sp, ids.toIntArray(), metals.toIntArray());
        }
    }

    /** A (re)joining player has no labels yet: they get every heap they can see on the next pass. */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        lastSent.remove(evt.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        lastSent.clear();
    }
}
