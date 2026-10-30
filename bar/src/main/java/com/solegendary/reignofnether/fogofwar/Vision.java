package com.solegendary.reignofnether.fogofwar;

/**
 * The one vision tuning knob (lovish, Oct 28: the fog of war reveals too little). Every sight range a unit, building
 * or capture point has is multiplied by {@link #MULTIPLIER} where it becomes a fog reveal radius: Unit.getSightRange,
 * BuildingPlacement.getSightRange and the capture points' fog pass all go through {@link #scaled}, so the fog, the
 * minimap (drawn from the fog the server sends), Hero AI Perception and the bots all read the same number. The
 * stats (attributes, the per-unit sight constants) stay the designed base values. Artillery is the one exception
 * ({@link #artillery}): unscaled, and always short of its own gun.
 */
public final class Vision {
    private Vision() { }

    public static final double MULTIPLIER = 1.5;

    /**
     * Artillery keeps needing spotters (lovish, Oct 28): an indirect-fire unit (IndirectFire: Bombard Crew, Mortar
     * Crawler, Rocket Battery, Mortar Galley, Iron Leviathan Engine) is exempt from {@link #MULTIPLIER} and always sees
     * less than its gun reaches, so its full range needs another unit's eyes. A sight item (the Spyglass) still helps,
     * up to one block short of the gun.
     */
    public static int artillery(int baseSight, float attackRange) {
        return Math.max(0, Math.min(baseSight, (int) Math.ceil(attackRange) - 1));
    }

    /** {@code baseSight} as fog actually reveals it: times {@link #MULTIPLIER}, capped at the fog's own limit. */
    public static int scaled(int baseSight) {
        if (baseSight <= 0)
            return baseSight;
        return (int) Math.min(FogOfWarServerEvents.MAX_SIGHT_BLOCKS, Math.round(baseSight * MULTIPLIER));
    }
}
