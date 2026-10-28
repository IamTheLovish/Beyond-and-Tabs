package com.solegendary.reignofnether.fogofwar;

/**
 * The one vision tuning knob (lovish, Oct 28: the fog of war reveals too little). Every sight range a unit, building
 * or capture point has is multiplied by {@link #MULTIPLIER} where it becomes a fog reveal radius: Unit.getSightRange,
 * BuildingPlacement.getSightRange and the capture points' fog pass all go through {@link #scaled}, so the fog, the
 * minimap (drawn from the fog the server sends), Hero AI Perception and the bots all read the same number. The
 * stats (attributes, the per-unit sight constants) stay the designed base values.
 */
public final class Vision {
    private Vision() { }

    public static final double MULTIPLIER = 1.5;

    /** {@code baseSight} as fog actually reveals it: times {@link #MULTIPLIER}, capped at the fog's own limit. */
    public static int scaled(int baseSight) {
        if (baseSight <= 0)
            return baseSight;
        return (int) Math.min(FogOfWarServerEvents.MAX_SIGHT_BLOCKS, Math.round(baseSight * MULTIPLIER));
    }
}
