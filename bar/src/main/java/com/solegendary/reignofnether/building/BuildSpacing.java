package com.solegendary.reignofnether.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BAR's "buildspacing inc / dec" for Shift-drag formations (BuildingLineClientEvents): the gap, in blocks, between
 * neighbouring footprints of a line or grid of the same building, chosen per building type for the session.
 * <ul>
 *   <li>0 = touching (what a grid always did); up to {@link #MAX}.</li>
 *   <li>A type the player never changed keeps today's formation exactly: the grid steps one footprint (gap 0) and the
 *       line keeps its old along-the-drag step ({@link #legacyLine}). Once the player sets a spacing the line is laid
 *       with that gap exactly, measured along the drag's main axis ({@link #line}).</li>
 *   <li>Walls and gates are not spaced: they are laid edge to edge on purpose.</li>
 * </ul>
 * No Minecraft types, so the math is unit-tested (BuildSpacingTest) and reused by the headless GameTests.
 * Widths here are full footprint widths in blocks (a building's dims + 1: BuildingUtils.getBuildingSize is max - min).
 */
public final class BuildSpacing {

    public static final int MIN = 0;
    public static final int MAX = 8;
    public static final int DEFAULT = 0;

    private static final Map<String, Integer> CHOSEN = new HashMap<>();

    private BuildSpacing() { }

    public static int clamp(int gap) {
        return Math.max(MIN, Math.min(MAX, gap));
    }

    /** Has the player changed the spacing of this building type this session? */
    public static boolean isSet(String type) {
        return type != null && CHOSEN.containsKey(type);
    }

    /** The gap for this building type: the player's choice this session, else {@link #DEFAULT}. */
    public static int get(String type) {
        return type == null ? DEFAULT : CHOSEN.getOrDefault(type, DEFAULT);
    }

    /** Raise (delta > 0) or lower the gap of this type by one step per call, clamped; returns the new gap. */
    public static int adjust(String type, int delta) {
        if (type == null)   // walls and gates: never spaced
            return DEFAULT;
        int gap = clamp(get(type) + Integer.signum(delta));
        CHOSEN.put(type, gap);
        return gap;
    }

    /** Forget every choice (tests). */
    public static void reset() {
        CHOSEN.clear();
    }

    /** Grid step along x and z: one footprint plus the gap. */
    public static int[] gridSteps(int widthX, int widthZ, int gap) {
        return new int[] { widthX + gap, widthZ + gap };
    }

    /**
     * The line as it was before spacing existed: {@code step} blocks apart along the drag (the larger footprint side,
     * i.e. footprints touch on a straight drag), centres rounded to blocks. Column centres, excluding the first.
     */
    public static List<int[]> legacyLine(int sx, int sz, int ex, int ez, int step, int max) {
        List<int[]> out = new ArrayList<>();
        double dx = ex - sx, dz = ez - sz;
        double len = Math.hypot(dx, dz);
        if (len == 0 || step <= 0)
            return out;
        int n = (int) Math.min(max, Math.floor(len / step));
        for (int i = 1; i <= n; i++)
            out.add(new int[] { sx + (int) Math.round(dx / len * step * i), sz + (int) Math.round(dz / len * step * i) });
        return out;
    }

    /**
     * A spaced line: along the drag's main axis (the one it moves further in) each centre is exactly that axis's
     * footprint width + {@code gap} past the last, so neighbouring footprints are {@code gap} blocks apart on that
     * axis; the other axis follows the drag, rounded to blocks. Column centres, excluding the first.
     */
    public static List<int[]> line(int sx, int sz, int ex, int ez, int widthX, int widthZ, int gap, int max) {
        List<int[]> out = new ArrayList<>();
        int dx = ex - sx, dz = ez - sz;
        if (dx == 0 && dz == 0)
            return out;
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        int major = alongX ? dx : dz, minor = alongX ? dz : dx;
        int step = (alongX ? widthX : widthZ) + clamp(gap);
        int sgn = major < 0 ? -1 : 1;
        int n = Math.min(max, Math.abs(major) / step);
        for (int i = 1; i <= n; i++) {
            int a = sgn * i * step;
            int b = (int) Math.round((double) minor * a / major);
            out.add(alongX ? new int[] { sx + a, sz + b } : new int[] { sx + b, sz + a });
        }
        return out;
    }

    /** Footprint-to-footprint gap of two boxes given by inclusive min/max on one axis (negative when they overlap). */
    public static int gap(int minA, int maxA, int minB, int maxB) {
        return minB > maxA ? minB - maxA - 1 : minA - maxB - 1;
    }
}
