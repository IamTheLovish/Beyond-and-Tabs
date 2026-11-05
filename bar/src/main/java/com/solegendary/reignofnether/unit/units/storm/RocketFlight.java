package com.solegendary.reignofnether.unit.units.storm;

/**
 * How a Rocket Battery's rocket flies (pure numbers, no Minecraft): {@link RocketShell} asks it for the flight time of a
 * shot and for its gravity, and the solved arc (BombardShell.aimAt, which reads both) does the rest.
 * <p>
 * The mortar lob it replaces took 20 ticks plus the flat distance (27 over 7 blocks, 44 from 24 out) on vanilla's 0.03
 * gravity, a 3-block apex at 11 blocks and 6 at 22. A rocket is the quick one of the two (lovish, Nov 5: "straighter,
 * faster"): {@link #BASE_TICKS} plus {@link #TICKS_PER_BLOCK} a block, kept between {@link #MIN_TICKS} and
 * {@link #MAX_TICKS} - 14 ticks over 7 blocks, 24 from 23 out and the cap of 25 from 24 out, a bit over half the mortar's time - on a
 * {@link #GRAVITY} of 0.045. The same arc in fewer ticks needs the harder pull, which is what keeps a short boost arc
 * (about 3.5 blocks at 24 out, 1 at 7) that still clears a two-block wall. The half-second floor is the counterplay:
 * a quick unit can still step out of a 2.5-block splash.
 */
public final class RocketFlight {
    private RocketFlight() { }

    /** Gravity of a rocket in flight, blocks per tick squared (the mortar shell's is 0.03). */
    public static final double GRAVITY = 0.045;

    public static final int BASE_TICKS = 10, MIN_TICKS = 14, MAX_TICKS = 25;
    public static final double TICKS_PER_BLOCK = 0.65;

    /** Flight time in ticks for a shot of this flat length. */
    public static int flightTicks(double flat) {
        return (int) Math.max(MIN_TICKS, Math.min(MAX_TICKS, BASE_TICKS + TICKS_PER_BLOCK * flat));
    }
}
