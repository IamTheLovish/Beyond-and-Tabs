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
}
