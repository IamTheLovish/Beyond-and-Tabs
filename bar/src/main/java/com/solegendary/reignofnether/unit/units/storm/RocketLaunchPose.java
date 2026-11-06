package com.solegendary.reignofnether.unit.units.storm;

/**
 * The Rocket Battery's launch animation as plain numbers (no Minecraft): the crew's recoil and the rack's lift over the
 * ticks of one volley, driven by {@code RocketBatteryUnit#getLaunchTicks} on the client (RocketBatteryRenderer,
 * RocketRackLayer). It replaces the mortar crews' generic attack pose.
 * <p>
 * Each of the {@link #ROCKETS} rockets, {@link #GAP} ticks apart, kicks the crew: a one-tick snap back, then a fall-off
 * that is gone after {@link #KICK_TICKS}. The kicks add and are squeezed through 1 - e^-x, so a volley builds (the
 * fourth lands harder than the first) without ever leaving 0..1. The rack lifts into firing position in the first two
 * ticks, holds through the volley and settles in the last six.
 */
public final class RocketLaunchPose {
    private RocketLaunchPose() { }

    /** Rockets per volley and the ticks between two of them (RocketBatteryUnit.VOLLEY_ROCKETS / VOLLEY_GAP_TICKS). */
    public static final int ROCKETS = 4, GAP = 3;
    /** How long one rocket's kick rings out. */
    public static final int KICK_TICKS = 10;
    /** The whole animation of a volley, from its first rocket. */
    public static final int TOTAL_TICKS = (ROCKETS - 1) * GAP + KICK_TICKS;

    /** The fall-off of one kick (ticks after its rocket), and how sharply the sum is squeezed. */
    private static final float DECAY_TICKS = 2.2f, SQUEEZE = 1.3f;

    /** Ticks since the volley began, from the ticks the animation has left and the frame's partial tick (0 = a rocket just went). */
    public static float age(int ticksLeft, float partial) {
        return clamp(TOTAL_TICKS - ticksLeft + partial, 0f, TOTAL_TICKS);
    }

    /** The crew's recoil, 0 (none) to 1 (full), {@code age} ticks into a volley. */
    public static float recoil(float age) {
        float sum = 0f;
        for (int i = 0; i < ROCKETS; i++)
            sum += kick(age - i * GAP);
        return 1f - (float) Math.exp(-SQUEEZE * sum);
    }

    /** One rocket's kick {@code a} ticks after it went: 0 before, a snap up over the first tick, then a fall-off to nothing at KICK_TICKS. */
    private static float kick(float a) {
        if (a <= 0f || a >= KICK_TICKS)
            return 0f;
        float rise = Math.min(a, 1f);
        float fall = a <= 1f ? 1f : (float) Math.exp(-(a - 1f) / DECAY_TICKS);
        float end = 1f - smoothstep((a - (KICK_TICKS - 3)) / 3f);
        return rise * fall * end;
    }

    /** How far the rack is lifted into firing position, 0 to 1, {@code age} ticks into a volley. */
    public static float rackLift(float age) {
        return smoothstep(age / 2f) * (1f - smoothstep((age - (TOTAL_TICKS - 6)) / 6f));
    }

    private static float smoothstep(float x) {
        float t = clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
