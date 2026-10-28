package com.solegendary.reignofnether.ambient;

/** The pure maths of the ambient-life spawner (AmbientLifeClientEvents). Stub: the tests come first. */
public final class AmbientLifeMath {

    private AmbientLifeMath() {
    }

    public static double[] focus(double eyeX, double eyeY, double eyeZ, float yawDeg, float pitchDeg, double groundY) {
        return new double[] { 0, 0, 0 };
    }

    public static double radius(float zoom) {
        return 0;
    }

    public static boolean isDay(long dayTime) {
        return false;
    }

    public static boolean isNight(long dayTime) {
        return false;
    }

    public static int toSpawn(int alive, int cap, int perPass) {
        return 0;
    }

    public static int cap(int base, float zoom) {
        return 0;
    }
}
