package com.solegendary.reignofnether.environment.wind;

/**
 * The shared wind field the tree limbs sway in (design/branch_wind.md section 1). One function of world position and
 * time, so every player sees the same gust cross the same tree at the same moment, and two trees 20 blocks apart sit
 * at different points of it (that is the per-tree phase - no per-tree state):
 * <ul>
 *   <li>a wind direction that turns slowly about its base heading;</li>
 *   <li>two gust waves travelling along it - a long slow swell ({@link #L1} blocks, {@link #V1} blocks/s) and a shorter
 *       faster ripple - summed, biased downwind (a limb is pushed, not shaken about its rest);</li>
 *   <li>a small flutter per block, phase from the position, so the outer twigs never move in lockstep;</li>
 *   <li>all scaled by the match wind (resources/Wind: the BAR wind the energy generators follow) - a strong wind on the
 *       HUD is a strong wind in the trees.</li>
 * </ul>
 * Amplitude: {@link #BASE_AMP} x tier x wind multiplier x fade: tier 4 at the average wind moves 0.087 block, at the
 * strongest (x1.7) 0.148 with the flutter on top - the "under ~0.15 block at the twig tips" rule. Pure math, no Minecraft types: the renderer
 * calls it per block per frame and the GameTest checks the bounds.
 */
public final class WindField {
    private WindField() { }

    /** Blocks of travel per sway tier at wind multiplier 1 (fade 1). */
    public static final double BASE_AMP = 0.0195;
    /** The match wind multiplier is clamped to this (Wind.MAX / Wind.MEAN = 1.71): the tip never passes 0.155. */
    public static final double MAX_WIND_MUL = 1.7;

    static final double BASE_HEADING = 0.6;          // radians; the prevailing direction (roughly east-south-east)
    static final double TURN = 0.35;                 // the heading wanders +- this
    static final double TURN_PERIOD = 140.0;         // seconds for one wander
    static final double L1 = 41.0, V1 = 6.5;         // the swell: wavelength (blocks) and speed (blocks/s)
    static final double L2 = 17.0, V2 = 10.0;        // the ripple
    static final double FLUTTER_HZ = 1.6;
    static final double TWO_PI = Math.PI * 2;

    /** The wind heading (radians, x toward +x, z toward +z) at a time in seconds. */
    public static double heading(double seconds) {
        return BASE_HEADING + TURN * Math.sin(TWO_PI * seconds / TURN_PERIOD);
    }

    /**
     * The gust strength at a point: -1 .. 1, biased so the mean is downwind (0.3) and the limb spends most of its
     * time pushed, swinging back on the lulls.
     */
    public static double gust(double x, double z, double seconds, double heading) {
        double d = x * Math.cos(heading) + z * Math.sin(heading);     // distance along the wind
        double swell = Math.sin(TWO_PI * (d / L1 - seconds * V1 / L1));
        double ripple = Math.sin(TWO_PI * (d / L2 - seconds * V2 / L2) + 1.7);
        return 0.3 + 0.7 * (0.6 * swell + 0.4 * ripple);
    }

    /** A per-block phase in 0 .. 2pi from the position (the flutter of each twig). */
    public static double phase(int x, int y, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFF) / 65536.0 * TWO_PI;
    }

    /**
     * The sway offset of a limb block, written to {@code out} (dx, dy, dz in blocks).
     *
     * @param tier    the block's sway tier (1 next to the trunk .. 4 the outer twigs); 0 writes zero
     * @param windMul the match wind multiplier (1 at the average wind), clamped to {@link #MAX_WIND_MUL}
     * @param fade    0 .. 1, the distance ramp (0 beyond the sway range)
     */
    public static void offset(int x, int y, int z, int tier, double seconds, double windMul, double fade, double[] out) {
        if (tier <= 0 || fade <= 0) {
            out[0] = out[1] = out[2] = 0;
            return;
        }
        double amp = BASE_AMP * tier * Math.min(Math.max(windMul, 0), MAX_WIND_MUL) * Math.min(fade, 1);
        double heading = heading(seconds);
        double g = gust(x + 0.5, z + 0.5, seconds, heading);
        double flutter = 0.12 * Math.sin(TWO_PI * FLUTTER_HZ * seconds + phase(x, y, z));
        double h = amp * (g + flutter);
        out[0] = Math.cos(heading) * h;
        out[2] = Math.sin(heading) * h;
        out[1] = -0.2 * Math.abs(h);                               // a pushed limb sags a little
    }

    /** The largest offset any block can have: tier MAX at the strongest wind, full fade, gust and flutter at their peaks. */
    public static double maxOffset() {
        return BASE_AMP * SwayBlocks.MAX_TIER * MAX_WIND_MUL * (1.0 + 0.12);
    }
}
