package com.solegendary.reignofnether.unit.units.storm;

/**
 * How a Rocket Battery's rocket flies (pure numbers, no Minecraft): {@link RocketShell} asks it for the flight time of a
 * shot and for its gravity, and the solved arc (BombardShell.aimAt) does the rest. STUB: still the mortar lob's numbers.
 */
public final class RocketFlight {
    private RocketFlight() { }

    /** Gravity of a rocket in flight, blocks per tick squared. */
    public static final double GRAVITY = 0.03;

    /** Flight time in ticks for a shot of this flat length. */
    public static int flightTicks(double flat) {
        return (int) Math.max(20, Math.min(44, 20 + flat));
    }
}
