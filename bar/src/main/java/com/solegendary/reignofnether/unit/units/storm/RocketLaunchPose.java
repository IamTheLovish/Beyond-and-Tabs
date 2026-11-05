package com.solegendary.reignofnether.unit.units.storm;

/**
 * The Rocket Battery's launch animation as plain numbers (no Minecraft): the crew's recoil and the rack's lift over the
 * ticks of one volley. STUB: the timing constants are final, the curves are not written yet.
 */
public final class RocketLaunchPose {
    private RocketLaunchPose() { }

    /** Rockets per volley and the ticks between two of them (RocketBatteryUnit.VOLLEY_ROCKETS / VOLLEY_GAP_TICKS). */
    public static final int ROCKETS = 4, GAP = 3;
    /** How long one rocket's kick rings out. */
    public static final int KICK_TICKS = 10;
    /** The whole animation of a volley, from its first rocket. */
    public static final int TOTAL_TICKS = (ROCKETS - 1) * GAP + KICK_TICKS;

    /** Ticks since the volley began, from the ticks the animation has left and the frame's partial tick. */
    public static float age(int ticksLeft, float partial) {
        return 0f;
    }

    /** The crew's recoil, 0 (none) to 1 (full), {@code age} ticks into a volley. */
    public static float recoil(float age) {
        return 0f;
    }

    /** How far the rack is lifted into firing position, 0 to 1, {@code age} ticks into a volley. */
    public static float rackLift(float age) {
        return 0f;
    }
}
