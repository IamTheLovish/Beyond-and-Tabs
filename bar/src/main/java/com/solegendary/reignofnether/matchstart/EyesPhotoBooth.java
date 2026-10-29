package com.solegendary.reignofnether.matchstart;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 * The structure photo booth's pure parts (EyesDirector scene 15), without the world, so plain JUnit tests walk them
 * (EyesPhotoBoothTest): where the site goes and how far its pad keeps from the arena wall, where the close still's
 * camera stands and where a camera looks, and the switch that keeps ambient life out of the stills.
 */
public final class EyesPhotoBooth {

    private EyesPhotoBooth() { }

    // ---- ambient life ----

    /**
     * True while the eyes rig photographs structures: the ambient birds, butterflies and fireflies (#334) spawn none
     * and the ones alive leave, so a still's dark share and tuft-star count come from the structure alone (round r34:
     * four birds in the measured crop of meadow_sampler_rts_close, and the numbers changed run to run). Only scene 15
     * sets it; normal play never does.
     */
    private static volatile boolean ambientLifeOff = false;

    public static boolean ambientLifeOff() {
        return ambientLifeOff;
    }

    static void setAmbientLifeOff(boolean off) {
        ambientLifeOff = off;
    }

    // ---- the site ----

    /** Blocks from the capitol's centre to the site's along x and along z (the site is on a diagonal). */
    public static final int SITE_OFFSET = 45;
    /** Blocks of grass pad placeStructure lays round a footprint (and clears to air above it). */
    public static final int PAD_MARGIN = 8;
    /** The footprint the site makes room for when it is chosen (before any structure is read): the samplers are 56 wide. */
    public static final int PLANNED_FOOTPRINT = 64;
    /**
     * Blocks the pad keeps from the wall's ring: the widest wall piece (a gatehouse, BattlefieldWall.GATEHOUSE_HALF)
     * reaches 2 either side of its column, and a still should not show the wall's foot cut by the pad either.
     */
    public static final double MIN_RING_CLEARANCE = 12;

    private static final int[][] DIAGONALS = { { -1, 1 }, { 1, 1 }, { -1, -1 }, { 1, -1 } };

    /**
     * The four diagonal axes from the capitol, the one whose site lies nearest the arena's centre first: toward the
     * arena's interior, away from its wall. Ties keep the old order (z+ first, then x-).
     */
    static int[][] siteAxes(int capX, int capZ, double centreX, double centreZ) {
        int[][] axes = new int[DIAGONALS.length][];
        for (int i = 0; i < axes.length; i++)
            axes[i] = DIAGONALS[i].clone();
        Arrays.sort(axes, Comparator.comparingDouble(a -> {   // stable: ties keep DIAGONALS' order
            double dx = capX + a[0] * SITE_OFFSET - centreX, dz = capZ + a[1] * SITE_OFFSET - centreZ;
            return dx * dx + dz * dz;
        }));
        return axes;
    }

    /**
     * The pad placeStructure lays for a sizeX x sizeZ footprint centred on the site: {x0, z0, x1, z1}, inclusive block
     * columns (the footprint's origin is the site less half its size, PAD_MARGIN round it).
     */
    static int[] padBox(int siteX, int siteZ, int sizeX, int sizeZ) {
        int ox = siteX - sizeX / 2, oz = siteZ - sizeZ / 2;
        return new int[] { ox - PAD_MARGIN, oz - PAD_MARGIN, ox + sizeX - 1 + PAD_MARGIN, oz + sizeZ - 1 + PAD_MARGIN };
    }

    /**
     * Blocks between a box of block columns {x0, z0, x1, z1} (covering x0..x1+1, z0..z1+1) and a ring of radius r
     * round (cx, cz): the radius less the farthest corner when the box is inside the ring, the nearest point less the
     * radius when it is outside, 0 when the ring crosses the box.
     */
    static double ringDistance(int[] box, double cx, double cz, double r) {
        double minX = box[0], minZ = box[1], maxX = box[2] + 1, maxZ = box[3] + 1;
        double nx = Math.max(minX, Math.min(cx, maxX)), nz = Math.max(minZ, Math.min(cz, maxZ));
        double near = Math.hypot(nx - cx, nz - cz);
        double far = Math.hypot(Math.max(Math.abs(minX - cx), Math.abs(maxX - cx)),
            Math.max(Math.abs(minZ - cz), Math.abs(maxZ - cz)));
        if (far <= r)
            return r - far;
        if (near >= r)
            return near - r;
        return 0;
    }

    /** Whether the pad of a PLANNED_FOOTPRINT structure on this site keeps MIN_RING_CLEARANCE from the ring. */
    static boolean clearOfRing(int siteX, int siteZ, double cx, double cz, double r) {
        return ringDistance(padBox(siteX, siteZ, PLANNED_FOOTPRINT, PLANNED_FOOTPRINT), cx, cz, r) >= MIN_RING_CLEARANCE;
    }

    /**
     * The site's axis: the first of siteAxes (nearest the arena's centre first) whose planned pad is clear of the ring
     * and that passes {@code usable} (the director's world checks: dry ground, the RTS camera's own spot inside the
     * border). r <= 0: no wall, no ring check. Null when no axis qualifies.
     */
    static int[] chooseAxis(int capX, int capZ, double cx, double cz, double r, Predicate<int[]> usable) {
        for (int[] a : siteAxes(capX, capZ, cx, cz)) {
            if (r > 0 && !clearOfRing(capX + a[0] * SITE_OFFSET, capZ + a[1] * SITE_OFFSET, cx, cz, r))
                continue;
            if (usable.test(a))
                return a;
        }
        return null;
    }

    // ---- the close still ----

    /** Blocks (horizontal) from the structure's middle to the close camera: a person standing among the plants. */
    public static final double CLOSE_DISTANCE = 5;
    /** A standing player's eye over the ground (Player.getStandingEyeHeight). */
    public static final double EYE_HEIGHT = 1.62;
    /** The eye stays at least this far above the aim point, so the close camera always looks down at it. */
    public static final double MIN_DROP = 1.0;
    /**
     * Where the close camera may stand, from the middle: south-west first (the side the RTS camera and the 3/4 still
     * see), then round; the director takes the first whose eye is in open air (not inside a trunk, a canopy or a wall).
     */
    static final int[][] CLOSE_DIRECTIONS = { { -1, 1 }, { 0, 1 }, { -1, 0 }, { 1, 1 }, { -1, -1 }, { 1, 0 }, { 0, -1 }, { 1, -1 } };

    /** {x, z} of the close camera: CLOSE_DISTANCE from the middle (mx, mz) along dir. */
    static double[] closeSpot(double mx, double mz, int[] dir) {
        double len = Math.hypot(dir[0], dir[1]);
        return new double[] { mx + dir[0] / len * CLOSE_DISTANCE, mz + dir[1] / len * CLOSE_DISTANCE };
    }

    /**
     * The close camera {eyeX, eyeY, eyeZ, yaw, pitch} standing on groundY at (x, z): a person's eye height over that
     * ground (at least MIN_DROP over the aim, so it looks down on a hill too), looking at (ax, ay, az). Minecraft's
     * angles in degrees: yaw 0 looks south (+z), 90 west (-x); pitch > 0 looks down.
     */
    static double[] closeCamera(double x, double z, double groundY, double ax, double ay, double az) {
        double eyeY = Math.max(groundY + EYE_HEIGHT, ay + MIN_DROP);
        double dx = ax - x, dz = az - z, dy = ay - eyeY;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        return new double[] { x, eyeY, z, yaw, pitch };
    }

    /**
     * Where a view from the eye (Minecraft yaw / pitch, degrees) crosses the horizontal plane y = planeY: {x, z}; the
     * eye's own x, z for a level view. The "looks at" of the director's "aimed at" lines (eyes_judge checks it).
     */
    static double[] lookPoint(double ex, double ey, double ez, double yawDeg, double pitchDeg, double planeY) {
        double yR = Math.toRadians(yawDeg), xR = Math.toRadians(pitchDeg);
        double fx = -Math.sin(yR) * Math.cos(xR), fy = -Math.sin(xR), fz = Math.cos(yR) * Math.cos(xR);
        double t = Math.abs(fy) < 1e-3 ? 0 : (ey - planeY) / -fy;
        return new double[] { ex + fx * t, ez + fz * t };
    }
}
