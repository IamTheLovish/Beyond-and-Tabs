package com.solegendary.reignofnether.hero;

import java.util.HashMap;
import java.util.Map;

/**
 * Which fallen-hero packets are news for this player (the "Hero fallen" alert, backlog 10). No Minecraft types, so a
 * GameTest can drive it, and FallenHeroClientboundPacket.deliver hands every fall to a {@link Listener}.
 * <ul>
 *   <li>only the owner's own heroes: the server sends the packet to the owner alone, and this is the client's guard;</li>
 *   <li>per hero, not per alert: two heroes falling in one fight are two pieces of news (review round 1 on #508 - the
 *       alert-wide 5 s cooldown silenced the second hero entirely, line and chord), while the same hero's packet twice
 *       within {@link #SAME_HERO_MS} (a resend) is one.</li>
 * </ul>
 * The rejoin resync (HeroServerEvents.onPlayerJoin re-sends every fallen hero) is kept quiet by the alert's RTS warm-up
 * (NotificationClientEvents.canAlert), not here.
 */
public final class HeroFallenNews {

    /** The same hero's fall again within this long is the same news. */
    public static final long SAME_HERO_MS = 5_000;

    /** Told of every hero the server says fell: the owner, the hero's entity description id, its uuid. */
    public interface Listener {
        void heroFallen(String ownerName, String heroDescriptionId, String heroUuid);
    }

    private final Map<String, Long> lastByHero = new HashMap<>();

    /** Whether this fall is news for {@code myName} at {@code nowMs}; records it when it is. */
    public boolean admit(String myName, String ownerName, String heroUuid, long nowMs) {
        if (myName == null || !myName.equals(ownerName))
            return false;
        String key = heroUuid == null ? "" : heroUuid;
        Long last = lastByHero.get(key);
        if (last != null && nowMs - last < SAME_HERO_MS)
            return false;
        lastByHero.put(key, nowMs);
        return true;
    }
}
