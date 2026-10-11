package com.solegendary.reignofnether.network;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.unit.NonUnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who may do what over the network, in one place (design/multiplayer.md). Reign of Nether was written for servers,
 * but several serverbound packets trusted a name or an entity id the client put in them: with a remote player in
 * the match, a modified client could have ordered the host's units, renamed itself the owner of a production queue
 * or started a sandbox and then taken anything. Every serverbound handler now asks here:
 * <ul>
 *   <li>{@link #mayActFor}: the sender is the named owner, or that owner has given the sender allied control, or the
 *       sender is a sandbox player (sandbox players edit the whole map - but only a sandbox player, not "anyone
 *       while somebody is in sandbox", which is what the old checks allowed);</li>
 *   <li>{@link #controllableIds}: the subset of entity ids the sender may order (the rest are dropped);</li>
 *   <li>{@link #isHost}: the singleplayer owner of an integrated (Open to LAN) server, or an op on a dedicated one -
 *       the person who may change match-wide state (game mode, survival waves, the lobby, starting a skirmish);</li>
 *   <li>{@link #allow}: a per-player token bucket for chatty packets, so a client can't flood the server with
 *       sync requests, map pings or orders.</li>
 * </ul>
 */
public final class PacketAuth {
    private PacketAuth() { }

    /** May {@code sender} issue commands on behalf of {@code ownerName}? */
    public static boolean mayActFor(ServerPlayer sender, String ownerName) {
        if (sender == null || ownerName == null)
            return false;
        String me = sender.getName().getString();
        return me.equals(ownerName) || SandboxServer.isSandboxPlayer(me)
            || AlliancesServerEvents.canControlAlly(me, ownerName);
    }

    /** As {@link #mayActFor} by name (game tests and server code that only has the name). */
    public static boolean mayActFor(String senderName, String ownerName) {
        if (senderName == null || ownerName == null)
            return false;
        return senderName.equals(ownerName) || SandboxServer.isSandboxPlayer(senderName)
            || AlliancesServerEvents.canControlAlly(senderName, ownerName);
    }

    /** May {@code senderName} order this entity? Units by owner; other mobs only with the "control all mobs" cheat. */
    public static boolean mayControl(ServerLevel level, String senderName, Entity entity) {
        if (entity == null)
            return false;
        if (entity instanceof Unit unit)
            return mayActFor(senderName, unit.getOwnerName());
        return level != null && NonUnitServerEvents.canControlAllMobs(level, senderName);
    }

    /**
     * The ids among {@code ids} that {@code senderName} may order, in order (the same array when all of them are;
     * a new, shorter one otherwise). Unknown ids are dropped too.
     */
    public static int[] controllableIds(ServerLevel level, String senderName, int[] ids) {
        if (ids == null || ids.length == 0 || level == null)
            return ids == null ? new int[0] : ids;
        int keep = 0;
        boolean[] ok = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) {
            ok[i] = mayControl(level, senderName, level.getEntity(ids[i]));
            if (ok[i])
                keep++;
        }
        if (keep == ids.length)
            return ids;
        int[] out = new int[keep];
        int j = 0;
        for (int i = 0; i < ids.length; i++)
            if (ok[i])
                out[j++] = ids[i];
        return out;
    }

    /**
     * The host: the owner of an integrated server (the player who pressed Open to LAN), or an op (level 2+) on a
     * dedicated server. With "Allow Cheats" on in the LAN dialog every joiner is an op, which is why the guide says
     * to leave it off - but the singleplayer owner is the host either way.
     */
    public static boolean isHost(ServerPlayer sp) {
        if (sp == null)
            return false;
        MinecraftServer server = sp.getServer();
        if (server == null)
            return false;
        if (server.isSingleplayerOwner(sp.getGameProfile()))
            return true;
        return server.isDedicatedServer() && sp.hasPermissions(2);
    }

    /** Host, or an op at {@code level} (match-wide toggles that RoN already gated on permission). */
    public static boolean isHostOrOp(ServerPlayer sp, int level) {
        return sp != null && (isHost(sp) || sp.hasPermissions(level));
    }

    // ---- rate limits ----

    /** Token buckets per player per channel: refill {@code perSecond}, hold at most {@code burst}. */
    private static final Map<UUID, Map<String, Bucket>> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long last;

        Bucket(double tokens, long last) {
            this.tokens = tokens;
            this.last = last;
        }
    }

    /**
     * True if {@code sp} may send one more {@code channel} packet now (and spends a token). {@code burst} packets can
     * arrive at once (a box-select of 200 units issues one order, a frantic player maybe 10 a second), then
     * {@code perSecond} a second; anything over is dropped silently - the client never needs to know.
     */
    public static boolean allow(ServerPlayer sp, String channel, double perSecond, double burst) {
        if (sp == null)
            return false;
        return allow(sp.getUUID(), channel, perSecond, burst, System.nanoTime());
    }

    public static boolean allow(UUID id, String channel, double perSecond, double burst, long nowNanos) {
        Bucket b = buckets.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(channel, k -> new Bucket(burst, nowNanos));
        synchronized (b) {
            double elapsed = (nowNanos - b.last) / 1e9;
            b.last = nowNanos;
            b.tokens = Math.min(burst, b.tokens + Math.max(0, elapsed) * perSecond);
            if (b.tokens < 1)
                return false;
            b.tokens -= 1;
            return true;
        }
    }

    /** Forget a player's buckets (on logout). */
    public static void forget(UUID id) {
        if (id != null)
            buckets.remove(id);
    }
}
