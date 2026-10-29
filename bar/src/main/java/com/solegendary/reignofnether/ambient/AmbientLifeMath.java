package com.solegendary.reignofnether.ambient;

/**
 * The pure maths of the ambient-life spawner (AmbientLifeClientEvents): where the camera looks, how far round that
 * point critters may appear, when it is day or night, and how many may spawn (AmbientLifeMathTest).
 */
public final class AmbientLifeMath {

    private AmbientLifeMath() {
    }

    /**
     * The ground point (x, groundY, z) the camera looks at from the eye, Minecraft's yaw / pitch convention (pitch > 0
     * looks down; yaw 0 faces +z, -90 faces +x) - the inverse of OrthoviewClientEvents.centreCameraOnPosInstant. A
     * camera looking at or above the horizon has no ground point: the eye's own column is used.
     */
    public static double[] focus(double eyeX, double eyeY, double eyeZ, float yawDeg, float pitchDeg, double groundY) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double down = Math.sin(pitch);
        if (down < 0.05)
            return new double[] { eyeX, groundY, eyeZ };
        double t = (eyeY - groundY) / down;
        double fx = -Math.sin(yaw) * Math.cos(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        return new double[] { eyeX + fx * t, groundY, eyeZ + fz * t };
    }

    /** Blocks round the focus where critters may appear: about the view's half-height, 12-48. */
    public static double radius(float zoom) {
        return Math.max(12, Math.min(48, 0.9 * zoom));
    }

    /** Full day (sun up): 0-12000 of the 24000-tick day. */
    public static boolean isDay(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t < 12000;
    }

    /** Full night: 13500-22500 (dusk and dawn belong to neither: the critters change shift). */
    public static boolean isNight(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 13500 && t < 22500;
    }

    /** How many to spawn this pass: never past the cap, at most perPass, never negative. */
    public static int toSpawn(int alive, int cap, int perPass) {
        return Math.max(0, Math.min(perPass, cap - alive));
    }

    /** The cap at this zoom: all of them up to zoom 40, none from 140 (a critter is under a pixel there), linear between. */
    public static int cap(int base, float zoom) {
        if (zoom <= 40)
            return base;
        if (zoom >= 140)
            return 0;
        return (int) Math.round(base * (140 - zoom) / 100.0);
    }

    // --- birds: a flock crosses the view high over the ground (AmbientBirdParticle) ---

    /** Ticks of one wing cycle: two beats (8 ticks each: up, level, down, level) and then a glide. */
    public static final int FLAP_CYCLE = 48;
    private static final int BEAT = 8, BEATS = 2;

    /**
     * Where a flock heading at `angle` (radians, 0 = +x, pi/2 = +z) starts and ends: on a line through the focus,
     * 1.4 radii before and after it, so it enters and leaves out of view. {startX, startZ, endX, endZ}.
     */
    public static double[] flockPath(double fx, double fz, double r, double angle) {
        double dx = Math.cos(angle) * 1.4 * r, dz = Math.sin(angle) * 1.4 * r;
        return new double[] { fx - dx, fz - dz, fx + dx, fz + dz };
    }

    /**
     * Slot i of the V: {blocks behind the leader, blocks to the side (+ left of the heading, - right)}. Slot 0 leads;
     * odd slots go left, even right, each pair a step further back and out.
     */
    public static double[] vSlot(int i) {
        if (i <= 0)
            return new double[] { 0, 0 };
        int k = (i + 1) / 2;
        return new double[] { 1.4 * k, (i % 2 == 1 ? 1 : -1) * 1.2 * k };
    }

    /** The wing sprite at this age: 0 wings up, 1 level, 2 down; two beats, then a glide on level wings. */
    public static int wingFrame(int age) {
        int t = Math.floorMod(age, FLAP_CYCLE);
        if (t >= BEAT * BEATS)
            return 1;
        int step = (t % BEAT) / (BEAT / 4);
        return step == 0 ? 0 : step == 2 ? 2 : 1;
    }

    /**
     * The particle roll that points a sprite's head (the texture top) along the velocity (vx, vz) on screen, for a
     * camera at this yaw. Screen up is the camera's horizontal forward (-sin yaw, cos yaw), screen right
     * (-cos yaw, -sin yaw); a positive roll turns the sprite clockwise on screen (SingleQuadParticle rotates its quad
     * about the view axis, local +x being the camera's left).
     */
    public static double screenRoll(double vx, double vz, float camYawDeg) {
        double yaw = Math.toRadians(camYawDeg);
        double up = -Math.sin(yaw) * vx + Math.cos(yaw) * vz;
        double right = -Math.cos(yaw) * vx - Math.sin(yaw) * vz;
        return Math.atan2(right, up);
    }

    /**
     * How far along x a bird's ground shadow falls from under it, `height` blocks up, the sun at `sunAngle` radians
     * from the zenith (Level.getSunAngle: negative in the morning with the sun in the east, +x): away from the sun,
     * never further than 1.5x the height (a low sun would throw it out of view).
     */
    public static double shadowOffsetX(double height, double sunAngle) {
        double t = Math.tan(sunAngle);
        return height * Math.max(-1.5, Math.min(1.5, t));
    }
}
