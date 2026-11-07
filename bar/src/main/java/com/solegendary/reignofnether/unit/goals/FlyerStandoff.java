package com.solegendary.reignofnether.unit.goals;

/**
 * How far out, measured flat, a flyer parks to shoot a building's block (RangedAttackBuildingGoal.flyerStandoff). The
 * shot is measured in 3D to the block's top, so the flat distance is what is left of its reach after its height above
 * the block. Pure arithmetic for the JUnit test (FlyerStandoffTest): never NaN, never negative except {@link #BLOCK}.
 */
public final class FlyerStandoff {
    private FlyerStandoff() { }

    /** Kept inside the range so a flyer that drifts a little still fires. */
    public static final double REACH_MARGIN = 1.5;
    /** How much closer than that flat distance it aims to stop. */
    public static final double PARK_MARGIN = 1.0;
    /** The nearest it parks to the block's column. */
    public static final double MIN_FLAT = 0.5;
    /** "No stand-off: fly at the block itself" (the flyer is as high above it as its reach, or higher). */
    public static final double BLOCK = -1.0;

    /**
     * The flat stand-off for a flyer of {@code range} that is {@code up} blocks above the block's top, or
     * {@link #BLOCK} when no point within its reach exists at that height (a short-range flyer, a block low in a pit).
     */
    public static double flat(double range, double up) {
        double reach = range - REACH_MARGIN;
        double height = Math.max(0.0, up);
        if (!(reach > 0.0) || !(height < reach))   // also false for NaN inputs
            return BLOCK;
        return Math.max(MIN_FLAT, Math.sqrt(reach * reach - height * height) - PARK_MARGIN);
    }
}
