package com.solegendary.reignofnether.matchstart;

import java.util.function.Predicate;

/**
 * The structure photo booth's pure parts (EyesDirector scene 15), without the world, so plain JUnit tests walk them
 * (EyesPhotoBoothTest). RED STAGE: the site logic as the director has it today (a fixed axis order, no wall check);
 * the rest is not written yet.
 */
public final class EyesPhotoBooth {

    private EyesPhotoBooth() { }

    // ---- ambient life ----

    public static boolean ambientLifeOff() {
        return false;
    }

    static void setAmbientLifeOff(boolean off) {
    }

    // ---- the site ----

    public static final int SITE_OFFSET = 45;
    public static final int PAD_MARGIN = 8;
    public static final int PLANNED_FOOTPRINT = 64;
    public static final double MIN_RING_CLEARANCE = 12;

    static int[][] siteAxes(int capX, int capZ, double centreX, double centreZ) {
        return new int[][] { { -1, 1 }, { 1, 1 }, { -1, -1 }, { 1, -1 } };
    }

    static int[] padBox(int siteX, int siteZ, int sizeX, int sizeZ) {
        int ox = siteX - sizeX / 2, oz = siteZ - sizeZ / 2;
        return new int[] { ox - PAD_MARGIN, oz - PAD_MARGIN, ox + sizeX - 1 + PAD_MARGIN, oz + sizeZ - 1 + PAD_MARGIN };
    }

    static double ringDistance(int[] box, double cx, double cz, double r) {
        throw new UnsupportedOperationException("not written yet");
    }

    static boolean clearOfRing(int siteX, int siteZ, double cx, double cz, double r) {
        throw new UnsupportedOperationException("not written yet");
    }

    static int[] chooseAxis(int capX, int capZ, double cx, double cz, double r, Predicate<int[]> usable) {
        for (int[] a : siteAxes(capX, capZ, cx, cz))
            if (usable.test(a))
                return a;
        return null;
    }

    // ---- the close still ----

    public static final double EYE_HEIGHT = 1.62;
    static final int[][] CLOSE_DIRECTIONS = { { -1, 1 } };

    static double[] closeSpot(double mx, double mz, int[] dir) {
        throw new UnsupportedOperationException("not written yet");
    }

    static double[] closeCamera(double x, double z, double groundY, double ax, double ay, double az) {
        throw new UnsupportedOperationException("not written yet");
    }

    static double[] lookPoint(double ex, double ey, double ez, double yawDeg, double pitchDeg, double planeY) {
        throw new UnsupportedOperationException("not written yet");
    }
}
