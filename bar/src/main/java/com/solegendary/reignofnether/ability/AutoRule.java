package com.solegendary.reignofnether.ability;

/**
 * When a unit casts an ability by itself (the developer's rule: "units should be able to use abilities on their own -
 * unless they are a hero"). One rule per {@link Ability}: set explicitly in its constructor ({@link Ability#autoRule(AutoRule)})
 * or inferred from its shape ({@link Ability#inferAutoRule()}). The AutoCaster reads it once a second per unit;
 * AutocastState turns it into the per-unit default (on for every rule but {@link #MANUAL}).
 */
public enum AutoRule {
    /** Cast on itself while fighting: it has a target, or something hurt it in the last 3 s. Self-buffs, self-centred bursts. */
    SELF_IN_COMBAT,
    /** Cast on the nearest valid enemy within range (its current target first). Strikes, skillshots at the enemy's feet. */
    ENEMY_IN_RANGE,
    /** Cast on the most hurt ally within range that is under 70% HP (itself only for a self-centred area heal). */
    ALLY_HURT,
    /** Cast at the best enemy when at least 3 enemies stand inside the ability's radius around it. Area spells. */
    CROWD,
    /** Cast while fighting and below the summon cap ({@link Ability#liveSummons}). */
    SUMMON_WHEN_FREE,
    /** Never cast by itself: hero abilities, the D-gun and game enders, toggles, orders that need a chosen spot. */
    MANUAL
}
