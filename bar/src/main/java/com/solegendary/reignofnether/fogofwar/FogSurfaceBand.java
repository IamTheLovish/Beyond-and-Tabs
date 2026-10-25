package com.solegendary.reignofnether.fogofwar;

import java.util.function.IntPredicate;

/**
 * The section maths of {@link FogRemesh}'s surface band, free of game state so it is unit-tested (FogSurfaceBandTest).
 * Sections are section Y coordinates (block y >> 4), the unit LevelRenderer.setSectionDirty takes.
 */
public final class FogSurfaceBand {
    private FogSurfaceBand() { }

    /**
     * Sections kept below the lowest surface: an overhang, a cave mouth, the foot of a cliff seen under the RTS camera's
     * shallowest tilt (12 degrees, OrthoviewClientEvents.CAMROTY_MAX). The perf plan's "minus one section" margin.
     */
    public static final int MARGIN_SECTIONS = 1;

    /**
     * At most this many sections of water or leaves are looked through below the lowest surface: 64 blocks, more than a
     * vanilla deep ocean (sea level 63, floor ~30) or a jungle canopy. It also stops the walk at an aquifer under a lake.
     */
    public static final int MAX_SEE_THROUGH_SECTIONS = 4;

    /**
     * The lowest section to re-mesh.
     *
     * @param lowestTopY the lowest top block (heightmap WORLD_SURFACE) over the chunk and the ring of columns round it:
     *                   a cliff face on the chunk's edge looks out over the neighbour's lower ground
     * @param seeThrough whether a section of this chunk holds water or leaves: the camera sees through them to what lies
     *                   under (a lake or sea bed; the ground under a canopy whose leaves hide round the cursor)
     */
    public static int low(int lowestTopY, int minSection, int maxSection, IntPredicate seeThrough) {
        int s0 = clamp(lowestTopY >> 4, minSection, maxSection - 1);
        int s = s0;
        for (int steps = 0; steps < MAX_SEE_THROUGH_SECTIONS && s > minSection && seeThrough.test(s); steps++)
            s--;
        // s is the first section under the water / leaves (or s0 itself): it is the margin under a sea bed
        return Math.max(minSection, Math.min(s0 - MARGIN_SECTIONS, s));
    }

    /**
     * The highest section to re-mesh: the highest that holds any block. Everything above is air, which has no mesh to
     * tint, so this is exact and needs no margin - trees, buildings and terrain remembered under the fog are all in.
     */
    public static int high(int highestNonEmptySection, int low, int maxSection) {
        return clamp(Math.max(highestNonEmptySection, low), low, maxSection - 1);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
