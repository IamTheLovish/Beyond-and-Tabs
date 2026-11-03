package com.solegendary.reignofnether.hud;

/**
 * Which ONE economy warning the HUD shows, and which pool a build stall names - plain Java so a GameTest can check
 * the rules without a client.
 *
 * Playtest clip (lovish, 25 min): at the same moment the HUD said "Build speed 62% - short on metal", "Overflowing
 * metal - build more labs or storage" and "Idle factories: 8", three lines that contradict each other. Causes: the
 * stall line named the emptier pool by fill ratio even when BOTH pools were nearly full (then the stall is not a
 * stock shortage at all: a spell's energy wish counts as demand, or a one-tick spike), and the overflow banner and the
 * idle-factory toast were drawn independently of it. Now: stall beats overflow beats idle factories, one at a time, and
 * a stall only names a pool that is really low.
 */
public final class EconomyWarning {

    public enum Kind { NONE, STALL, OVERFLOW, IDLE_FACTORIES }

    public enum Pool { METAL, ENERGY, UNNAMED }

    /** A pool at or above this fill (EconomyBarRenderer.OVERFLOW_FILL) is not what is starving construction. */
    public static final float FULL_FILL = 0.95f;

    private EconomyWarning() { }

    /** The single warning line to show: stall > overflow > idle factories. */
    public static Kind choose(boolean stalled, boolean overflowing, int idleFactories) {
        if (stalled)
            return Kind.STALL;
        if (overflowing)
            return Kind.OVERFLOW;
        return idleFactories > 0 ? Kind.IDLE_FACTORIES : Kind.NONE;
    }

    /**
     * The pool a stall is "short on": the emptier one by fill (energy numbers are always bigger than metal's, so raw
     * amounts mislead). When even the emptier pool is {@link #FULL_FILL} full nothing is short - UNNAMED - and the
     * line says only the build speed, instead of blaming a pool that the overflow banner calls overflowing.
     */
    public static Pool stallPool(float metalFill, float energyFill) {
        boolean metalLower = metalFill <= energyFill;
        float lower = metalLower ? metalFill : energyFill;
        if (lower >= FULL_FILL)
            return Pool.UNNAMED;
        return metalLower ? Pool.METAL : Pool.ENERGY;
    }

    private static volatile long barLineMs = Long.MIN_VALUE;

    /** EconomyBarRenderer, every frame its OWN bar shows a stall or overflow line. */
    public static void markBarLineUp() {
        barLineMs = System.currentTimeMillis();
    }

    /** Was a stall or overflow line up on the player's own bar in the last half second? */
    public static boolean barLineUp() {
        // MIN_VALUE = never (now - MIN_VALUE overflowed negative: "up" until the first line ever showed, and the
        // idle-factory nag, which waits for the bar to be clear, never fired)
        return barLineMs != Long.MIN_VALUE && System.currentTimeMillis() - barLineMs < 500;
    }
}
