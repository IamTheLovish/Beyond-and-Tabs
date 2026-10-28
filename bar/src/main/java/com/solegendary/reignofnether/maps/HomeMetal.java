package com.solegendary.reignofnether.maps;

/**
 * The one home metal set every seat starts with, on every pre-made map and every player count (lovish Oct 22:
 * "starting mexes around the base can be standardized"). Defined here once; {@link MapLayout} lays it from these numbers
 * and MapMetalGameTests asserts it for every map, seat and seed. Where the terrain refuses a spot (a cliff, water in
 * the pad) the patch moves toward the start, then to the next angle or radius - the count and the yield never change,
 * the distance stays inside [{@link #MIN_DIST}, {@link #MAX_DIST}].
 *
 * <p>Not reached by the extra-metal knob ({@link MetalOptions#extra()}) or the lobby's richness: home metal is the same
 * in every lobby setting. (The procedural "Vanilla terrain" ring, StartAreaClearing.stampHomeRing, is its own thing.)
 */
public final class HomeMetal {
    private HomeMetal() { }

    /**
     * Patches per seat. Six: what Highlands and Verdant Valley already gave, what the procedural ring gives a rich map
     * (StartAreaClearing, lovish Oct 10: "more mexes"), and the one count that loses no map any metal (five on the other
     * six maps was the old number; taking the sixth away from Highlands and Verdant Valley moved the core fill's picks
     * and cost Highlands at 8 players a patch). Measured in the PR.
     */
    public static final int COUNT = 6;
    /** Yield of each: the standard patch. */
    public static final float VALUE = 1.0f;
    /** Distance from the start to the ring the patches sit on (as StartAreaClearing.HOME_RING_RADIUS). */
    public static final int RING = 18;
    /** Angle between neighbours on the ring, a fan facing the map's centre. */
    public static final double ARC = Math.PI / 2.2;
    /** A placed patch is this far from its start, at least (a nudge toward the start moves it up to 6) and at most. */
    public static final int MIN_DIST = 11, MAX_DIST = 24;
    /** Home patches keep at least this far from each other, so an alternative spot never crowds a neighbour. */
    public static final int SPACING = 12;
}
