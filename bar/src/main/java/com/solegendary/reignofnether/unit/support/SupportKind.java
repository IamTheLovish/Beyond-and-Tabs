package com.solegendary.reignofnether.unit.support;

/**
 * What a support cast does, whatever delivers it (claude/healer-ai-oct21.md; lovish, Oct 21 23:37: "the healers will
 * need a rework, we shouldn't use vanilla minecraft potions"). The support brain picks a kind and a target - a unit
 * or a spot - with the cast's range and radius; the unit delivers it through SupportUnit.castSupport. Today the Field
 * Medic delivers all four by potion (PotionDelivery); the faction heal and harm abilities that replace the potions
 * plug in behind the same calls.
 */
public enum SupportKind {
    /** Heal one friend (a splash may reach a few round it: the cast's radius). */
    SINGLE_HEAL,
    /** Heal every hurt friend in an area (a cloud, a circle). */
    AREA_HEAL,
    /** Hurt every enemy in an area - and anyone else in it the delivery does not spare, so friends are kept out. */
    AREA_HARM,
    /** Lift an affliction from a friend (fire today; poison, roots, slows for a cleanse that can). */
    CLEANSE;

    /**
     * The order a unit considers its own casts in, after an attack order's harm: area heal on a cluster first, then
     * one friend, then a cleanse, and harm last - a healer heals before it fights.
     */
    public static final SupportKind[] PRIORITY = { AREA_HEAL, SINGLE_HEAL, CLEANSE, AREA_HARM };

    public boolean heals() {
        return this == SINGLE_HEAL || this == AREA_HEAL;
    }
}
