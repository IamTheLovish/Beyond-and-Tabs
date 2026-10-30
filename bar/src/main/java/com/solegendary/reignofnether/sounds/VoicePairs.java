package com.solegendary.reignofnether.sounds;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Client only, RTS view: one sound event heard once, not twice. A unit's hurt or death voice reaches the client two
 * ways. The server plays it (LivingEntity.hurt) and sends it to players within 16 blocks of their body, and the
 * client's own damage and death events (handleDamageEvent, entity event 3) play a local copy with no player. Vanilla
 * drops that local copy; ClientLevelMixin plays it in the RTS view, because the RTS player's body floats far from most
 * fights and the server's copy reaches only the fights within 16 blocks of it. So a fight near the body was heard twice
 * and a far one once (measured with [Ears], PR 426).
 * <p>
 * A copy is dropped only when its twin has just played: the same sound, one copy local and one from the server, within
 * {@link #NEAR} blocks and {@link #WINDOW_MS} of each other. Whichever arrives first plays. A far fight's local copy
 * has no twin and always plays. Time is real time (Util.getMillis): the client level's game time is no clock in this
 * mod (ClientLevelMixin pins it to a normalised time of day, which stands still and jumps during night distortion). The same pairing removes other doubled events too (an iron golem's swing: the server's
 * doHurtTarget and the client's entity event 4 both play it).
 */
public final class VoicePairs {

    /** Between the two copies of one event: the server's packet and the client's own event can be a few ticks apart. */
    static final long WINDOW_MS = 300;
    /** Blocks between them: the server's position and the client's interpolated one of the same entity. */
    static final double NEAR = 2.5;
    static final int MAX = 256;

    private record Heard(String sound, double x, double y, double z, long ms, boolean local) { }

    private final ArrayDeque<Heard> recent = new ArrayDeque<>();

    /**
     * A sound about to play. Returns true when it is the second copy of one event (drop it); false to play it, and it
     * is remembered as a first copy.
     */
    public boolean secondCopy(String sound, double x, double y, double z, boolean local, long nowMs) {
        while (!recent.isEmpty() && (nowMs - recent.peekFirst().ms() > WINDOW_MS || recent.size() >= MAX))
            recent.pollFirst();
        for (Iterator<Heard> it = recent.iterator(); it.hasNext(); ) {
            Heard h = it.next();
            if (h.local() != local && h.sound().equals(sound) && Math.abs(nowMs - h.ms()) <= WINDOW_MS) {
                double dx = h.x() - x, dy = h.y() - y, dz = h.z() - z;
                if (dx * dx + dy * dy + dz * dz <= NEAR * NEAR) {
                    it.remove();   // one pair per event: a third copy would be a new event
                    return true;
                }
            }
        }
        recent.addLast(new Heard(sound, x, y, z, nowMs, local));
        return false;
    }

    int remembered() {
        return recent.size();
    }

    public void clear() {
        recent.clear();
    }

    public static final VoicePairs CLIENT = new VoicePairs();
}
