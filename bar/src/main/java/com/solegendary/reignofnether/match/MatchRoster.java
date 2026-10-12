package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Who is in the match: the sides in play and the sides whose match is over. RoN kept these as two
 * {@code Collections.synchronizedList} fields on PlayerServerEvents (which still exposes both lists, as
 * {@code rtsPlayers} / {@code postGameRtsPlayers}, for every caller that reads them) and wrapped every walk in
 * {@code synchronized} - pointless once all game state is touched only on the server thread (rewrite 1), and no
 * protection against the real hazard: a defeat <i>during</i> a walk over the list (a beacon victory defeating every
 * other side from inside the loop over them threw ConcurrentModificationException; so would a bot whose think step
 * loses it the match).
 * <p>
 * Copy-on-write fits the access pattern exactly: read by many systems every tick, written a handful of times per
 * match (a side starts, is beaten, spectates) and at most 16 entries long. A walk sees the list as it was when it
 * began, so a side may leave in the middle of one; no lock, nothing allocated per read. Game thread only.
 */
public final class MatchRoster {
    private MatchRoster() { }

    /** The sides still in play, humans and bots (PlayerServerEvents.rtsPlayers). */
    public static final List<RTSPlayer> LIVE = new CopyOnWriteArrayList<>();
    /**
     * The sides whose match is over for them: the beaten, and winners (who stay in LIVE until they spectate). The
     * results table and the player panel's "gone" rows read it; a winner in it may spectate.
     * (PlayerServerEvents.postGameRtsPlayers)
     */
    public static final List<RTSPlayer> FINISHED = new CopyOnWriteArrayList<>();

    public static boolean any() {
        return !LIVE.isEmpty();
    }

    public static boolean isInPlay(String name) {
        return get(name) != null;
    }

    public static boolean isInPlay(int id) {
        for (RTSPlayer p : LIVE)
            if (p.id == id)
                return true;
        return false;
    }

    @Nullable
    public static RTSPlayer get(String name) {
        if (name == null)
            return null;
        for (RTSPlayer p : LIVE)
            if (p.name.equals(name))
                return p;
        return null;
    }

    /** Bot names compare ignoring case, as RoN's isBot always did (bot commands are typed). */
    public static boolean isBot(String name) {
        if (name == null)
            return false;
        for (RTSPlayer p : LIVE)
            if (p.name.equalsIgnoreCase(name))
                return p.isBot();
        return false;
    }

    public static boolean isBot(int id) {
        for (RTSPlayer p : LIVE)
            if (p.id == id)
                return p.isBot();
        return false;
    }

    /** Whether {@code name}'s match is over for them (beaten, or a winner). */
    public static boolean isFinished(String name) {
        if (name == null)
            return false;
        for (RTSPlayer p : FINISHED)
            if (p != null && name.equals(p.name))
                return true;
        return false;
    }

    /** Map colour ids already taken by sides in play, so a new side can pick a free one. */
    public static Set<Integer> takenColorIds() {
        Set<Integer> taken = new HashSet<>();
        for (RTSPlayer p : LIVE)
            taken.add(p.startPosColorId);
        return taken;
    }

    /**
     * A side enters play (a start, a bot, a scenario role). The fog's who-plays cache is dropped, and the match
     * hears of it: the first side of an idle world makes it a classic match (MatchState.sideJoined).
     */
    public static void join(RTSPlayer side) {
        LIVE.add(side);
        FogOfWarServerEvents.invalidateRtsCache();
        MatchState.live().sideJoined(side.name);
    }

    /** A side leaves play (beaten, spectating). Returns it, or null when it wasn't in play. */
    @Nullable
    public static RTSPlayer leave(String name) {
        RTSPlayer side = get(name);
        if (side == null)
            return null;
        LIVE.remove(side);
        FogOfWarServerEvents.invalidateRtsCache();
        return side;
    }

    /** {@code side}'s match is over for them (once: a winner beaten later is not listed twice). */
    public static void finish(@Nullable RTSPlayer side) {
        if (side != null && !isFinished(side.name))
            FINISHED.add(side);
    }

    /** Nobody in play any more (reset, a published scenario). FINISHED is cleared with the save on reset. */
    public static void clearLive() {
        LIVE.clear();
        FogOfWarServerEvents.invalidateRtsCache();
    }
}
