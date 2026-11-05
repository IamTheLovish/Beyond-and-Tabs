package com.solegendary.reignofnether.unit.units.storm;

/**
 * The Rocket Battery's reload and charge as plain numbers (no Minecraft), shown on the rack: after a volley the four
 * rods stand empty; as the cooldown runs out they take their bolts one after another, and in the last
 * {@link #CHARGE_TICKS} before the next volley the rack lifts and crackles. It is a picture of the attack cooldown the
 * unit already has - nothing here delays or changes a shot.
 * <p>
 * The unit syncs "ticks until it can fire again", clamped to {@link #RELOAD_WINDOW}, and whether it has a target.
 */
public final class RocketCharge {
    private RocketCharge() { }

    /** Ticks before the next volley in which the rods take their bolts (the cooldown above this shows an empty rack). */
    public static final int RELOAD_WINDOW = 72;
    /** The last ticks before the volley, all four rods loaded: the rack lifts and crackles. */
    public static final int CHARGE_TICKS = 24;

    /** How full rod {@code rod} is (0 empty, 1 loaded) when the next volley is {@code ticksUntilReady} ticks away. */
    public static float fill(int rod, int ticksUntilReady) {
        return 1f;   // stub
    }

    /** How charged the rack is, 0 to 1: zero without a target, and rising over the last {@link #CHARGE_TICKS}. */
    public static float charge(int ticksUntilReady, boolean engaged) {
        return 0f;   // stub
    }
}
