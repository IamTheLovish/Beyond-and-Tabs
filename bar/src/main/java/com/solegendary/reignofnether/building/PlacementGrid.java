package com.solegendary.reignofnether.building;

import java.util.ArrayList;
import java.util.List;

/**
 * The placement grid's numbers and shapes (#38 item 10; lovish chose C "rune lattice" + B "patch glow", Oct 21 -
 * Lane B's mock-ups). Kept free of client classes so PlacementGridGameTests can check them headless; the drawing is
 * PlacementGridClientEvents.
 * <ul>
 *   <li>While placing any building: faint gold dots on the block grid within {@link #RADIUS} blocks of the cursor,
 *       fading with distance, red where a building can't stand; the footprint is a rune circle (a double ring with
 *       glyph marks).</li>
 *   <li>While placing a Metal Extractor: every free patch in {@link #PATCH_RANGE} gets a cyan ring and a short light
 *       column; a taken patch is grey.</li>
 * </ul>
 * Quiet at RTS zoom: thin lines, low alpha, and nothing at all once placement ends.
 */
public final class PlacementGrid {

    private PlacementGrid() {}

    /** Dots are drawn within this many blocks of the cursor. */
    public static final int RADIUS = 10;
    /** Patches are lit within this many blocks of the cursor while an extractor is being placed. */
    public static final int PATCH_RANGE = 48;
    /** A dot's alpha at the cursor; it fades to nothing at RADIUS. */
    public static final float DOT_ALPHA = 0.55f;

    /** Dot alpha at a distance from the cursor: full at the cursor, quadratic fade, 0 at and beyond RADIUS. */
    public static float dotAlpha(double dist) {
        if (dist >= RADIUS)
            return 0f;
        double t = 1.0 - dist / RADIUS;
        return (float) (DOT_ALPHA * t * t);
    }

    /** The grid cells (dx, dz) within RADIUS of the cursor block, the cursor included. */
    public static List<int[]> cells() {
        List<int[]> out = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
                if (dx * dx + dz * dz < RADIUS * RADIUS)
                    out.add(new int[]{ dx, dz });
        return out;
    }

    /** The rune circle's outer radius: it just clears the footprint's corners. */
    public static double runeRadius(int sizeX, int sizeZ) {
        return Math.sqrt(sizeX * sizeX + sizeZ * sizeZ) / 2.0 + 0.4;
    }

    /** The inner ring sits this far inside the outer one; the glyph marks are drawn between them. */
    public static double runeGap(double outer) {
        return Math.min(0.6, Math.max(0.3, outer * 0.12));
    }

    /** Glyph marks around the ring: more for a bigger footprint, always a multiple of 4 so the circle stays balanced. */
    public static int glyphCount(double outer) {
        int n = (int) Math.round(outer * 2.5);
        return Math.max(8, Math.min(32, (n + 3) / 4 * 4));
    }
}
