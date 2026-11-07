package com.solegendary.reignofnether.unit.units.storm;

/**
 * The Rocket Battery's reload and charge as plain numbers (no Minecraft), shown on the rack: after a volley the four
 * tubes stand empty; as the cooldown runs out they take their bolts one after another, and in the last
 * {@link #CHARGE_TICKS} before the next volley the rack lifts and crackles. It is a picture of the attack cooldown the
 * unit already has - nothing here delays or changes a shot.
 * <p>
 * The unit syncs "ticks until it can fire again", clamped to {@link #RELOAD_WINDOW}, and whether it has a target.
 */
public final class RocketCharge {
    private RocketCharge() { }

    /** Ticks before the next volley in which the tubes take their bolts (the cooldown above this shows an empty rack). */
    public static final int RELOAD_WINDOW = 72;
    /** The last ticks before the volley, all four tubes loaded: the rack lifts and crackles. */
    public static final int CHARGE_TICKS = 24;

    /** How full tube {@code tube} is (0 empty, 1 loaded) when the next volley is {@code ticksUntilReady} ticks away. */
    public static float fill(int tube, int ticksUntilReady) {
        // tube 0 takes its bolt first, over STEP ticks; each next tube starts when the one before it is full
        float step = (RELOAD_WINDOW - CHARGE_TICKS) / (float) RocketRackGeometry.TUBES;
        float start = RELOAD_WINDOW - tube * step;
        return smoothstep((start - ticksUntilReady) / step);
    }

    private static float smoothstep(float x) {
        float t = x < 0f ? 0f : Math.min(x, 1f);
        return t * t * (3f - 2f * t);
    }

    /** How charged the rack is, 0 to 1: zero without a target, and rising over the last {@link #CHARGE_TICKS}. */
    public static float charge(int ticksUntilReady, boolean engaged) {
        return engaged ? smoothstep((CHARGE_TICKS - ticksUntilReady) / (float) CHARGE_TICKS) : 0f;
    }
}
