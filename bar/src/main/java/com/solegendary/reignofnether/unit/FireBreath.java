package com.solegendary.reignofnether.unit;

/**
 * SIGNATURE STUB for the red run (tests first): the fire breath's rules, not written yet. Every method answers as the
 * old one-tick breath would (no pulses after the first tick, no sweep, no cone, flames that go nowhere), so
 * FireBreathTest fails for the reasons it names. The next commit replaces this file.
 */
public final class FireBreath {
    private FireBreath() { }

    public static final int TICKS = 1;
    public static final int PULSE_TICKS = 66;
    public static final double AIR_HALF_DEG = 32, AIR_PAST = 0, AIR_MAX = 12, AIR_SWEEP = 0;
    public static final double GROUND_HALF_DEG = 32, GROUND_LENGTH = 12, GROUND_SWEEP_DEG = 0;
    public static final double JAWS = 1.5;
    public static final double FLAME_SECONDS = 0.1;

    public static boolean pulse(int tick) {
        return tick == 0;
    }

    public static double sweep(int tick) {
        return 0;
    }

    public static double sweep(double tick) {
        return 0;
    }

    public static void airOffset(double headingX, double headingZ, double[] out) {
        out[0] = out[1] = out[2] = 0;
    }

    public static void groundOffset(double fromX, double fromZ, double aimX, double aimZ, double[] out) {
        out[0] = out[1] = out[2] = 0;
    }

    public static double along(double base, double offset, double sweep) {
        return base;
    }

    public static double length(boolean landed, double dist) {
        return GROUND_LENGTH;
    }

    public static double tanHalf(boolean landed) {
        return Math.tan(Math.toRadians(GROUND_HALF_DEG));
    }

    public static boolean reaches(double ax, double ay, double az, double dx, double dy, double dz, double length,
                                  double tanHalf, double px, double py, double pz, double r) {
        return false;
    }

    public static boolean reachesBox(double ax, double ay, double az, double dx, double dy, double dz, double length,
                                     double tanHalf, double minX, double minY, double minZ, double maxX, double maxY,
                                     double maxZ) {
        return false;
    }

    public static void flame(double ax, double ay, double az, double aimX, double aimY, double aimZ, double tanHalf,
                             double u, double v, double[] out) {
        out[0] = out[1] = out[2] = 0;
    }
}
