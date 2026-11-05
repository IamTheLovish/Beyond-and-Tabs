package com.solegendary.reignofnether.sounds;

import net.minecraft.Util;

/**
 * The clock the client's sound throttles count in: real time, 20 ticks a second (SfxClient's gaps and per-tick
 * budgets, SoundClientEvents' repeat damping).
 *
 * <p>Not the level's game time. On the client that is the time of day: mixin/ClientLevelMixin.tickTime sets game
 * time and day time to the target the server's time packets give (mixin/ClientPacketMixin.handleSetTime). In a match
 * the day moves one tick every matchDayCycle game ticks (match/DayClock: every 3rd at the default Slow), it stands
 * still with the cycle Off, before the match starts and once it is decided, and near a night source it walks to
 * midnight and stays there. Counted in it, "3 unit sounds a tick" was 1 a tick at Slow, and with the clock standing
 * still a category's budget was never renewed and no sound's gap ever ended: after the first few, no unit sound
 * played at all.
 */
public final class SoundTicks {

    private SoundTicks() { }

    public static long now() {
        return of(Util.getMillis());
    }

    /** The tick at a real-time clock reading (Util.getMillis()). */
    public static long of(long millis) {
        return millis / 50L;
    }
}
