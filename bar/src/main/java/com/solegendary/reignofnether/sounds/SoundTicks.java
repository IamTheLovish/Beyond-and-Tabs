package com.solegendary.reignofnether.sounds;

import net.minecraft.Util;

/**
 * The clock the client's sound throttles count in: real time, 20 ticks a second (SfxClient's gaps and per-tick
 * budgets, SoundClientEvents' repeat damping).
 *
 * <p>Not the level's game time. On the client that is the time of day (mixin/ClientLevelMixin.tickTime sets both to
 * the target the server's time packet gives, mixin/ClientPacketMixin.handleSetTime): it stands still between those
 * packets and jumps about 20 ticks once a second, and stands still for good when the day does. Counted in it, "3 unit
 * sounds a tick" was 3 a second for a whole battle, and each sound's 2-tick gap was a second.
 */
public final class SoundTicks {

    private SoundTicks() { }

    public static long now() {
        return Util.getMillis() / 50L;
    }
}
