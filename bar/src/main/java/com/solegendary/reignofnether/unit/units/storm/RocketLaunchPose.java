package com.solegendary.reignofnether.unit.units.storm;

/**
 * The Rocket Battery's launch animation as plain numbers (no Minecraft): the crew's wind-up, recoil and the rack's lift over
 * the ticks of one volley, driven by {@code RocketBatteryUnit#getLaunchTicks} on the client (RocketBatteryRenderer,
 * RocketBatteryModel, RocketRackLayer). It replaces the mortar crews' generic attack pose.
 * <p>
 * A volley is a {@link #WINDUP}-tick wind-up - the rack swings up and the crew leans into the shot - then the
 * {@link #ROCKETS} rockets, {@link #GAP} ticks apart. Each rocket kicks the crew: a snap back over a tick and a half
 * (a one-tick snap vanished between frames of a 10 fps clip), then a fall-off that is gone after {@link #KICK_TICKS}.
 * The kicks add and are squeezed through 1 - e^-x, so a volley builds (the fourth lands harder than the first) without
 * ever leaving 0..1. The rack is up from the wind-up through the volley and settles in the last six ticks.
 */
public final class RocketLaunchPose {
    private RocketLaunchPose() { }

    /** Rockets per volley and the ticks between two of them (RocketBatteryUnit.VOLLEY_ROCKETS / VOLLEY_GAP_TICKS). */
    public static final int ROCKETS = 4, GAP = 3;
    /** Ticks between the order to fire and the first rocket: the wind-up (RocketBatteryUnit schedules every rocket after it). */
    public static final int WINDUP = 5;
    /** How long one rocket's kick rings out. */
    public static final int KICK_TICKS = 10;
    /** The whole animation of a volley, from the order to fire to the last kick ringing out. */
    public static final int TOTAL_TICKS = WINDUP + (ROCKETS - 1) * GAP + KICK_TICKS;

    /** The kick's rise (ticks), the fall-off of one kick (ticks after its rocket), and how sharply the sum is squeezed. */
    private static final float RISE_TICKS = 1.5f, DECAY_TICKS = 3.0f, SQUEEZE = 1.3f;

    /** Ticks since the order to fire, from the ticks the animation has left and the frame's partial tick (0 = the order just went). */
    public static float age(int ticksLeft, float partial) {
        return clamp(TOTAL_TICKS - ticksLeft + partial, 0f, TOTAL_TICKS);
    }

    /** The wind-up, 0 to 1: building over the {@link #WINDUP} ticks before the first rocket, gone the tick it leaves. */
    public static float windup(float age) {
        return age >= WINDUP ? 0f : smoothstep(age / WINDUP);
    }

    /** The crew's recoil, 0 (none) to 1 (full), {@code age} ticks after the order to fire. */
    public static float recoil(float age) {
        float sum = 0f;
        for (int i = 0; i < ROCKETS; i++)
            sum += kick(age - WINDUP - i * GAP);
        return 1f - (float) Math.exp(-SQUEEZE * sum);
    }

    /** One rocket's kick {@code a} ticks after it went: 0 before, a snap up over RISE_TICKS, then a fall-off to nothing at KICK_TICKS. */
    private static float kick(float a) {
        if (a <= 0f || a >= KICK_TICKS)
            return 0f;
        float rise = Math.min(a / RISE_TICKS, 1f);
        float fall = a <= RISE_TICKS ? 1f : (float) Math.exp(-(a - RISE_TICKS) / DECAY_TICKS);
        float end = 1f - smoothstep((a - (KICK_TICKS - 3)) / 3f);
        return rise * fall * end;
    }

    /** How far the rack is lifted into firing position, 0 to 1, {@code age} ticks after the order: up over the wind-up, held, settling at the end. */
    public static float rackLift(float age) {
        return smoothstep(age / WINDUP) * (1f - smoothstep((age - (TOTAL_TICKS - 6)) / 6f));
    }

    private static float smoothstep(float x) {
        float t = clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
