package com.solegendary.reignofnether.ability;

/**
 * Shared wording for the range line on experimental (T3) ability tooltips. Every number comes from the caller's own
 * field or constant, never a typed copy, so the tooltip cannot drift from what the ability does.
 */
public final class ExperimentalAbilityText {
    private ExperimentalAbilityText() {}

    /** "Range: 22 blocks": aimed abilities, from the ability's range value. */
    public static String rangeLine(float range) {
        return "Range: " + blocks(range);
    }

    /** "No target, hits within 8 blocks": self-cast abilities that reach out from the caster. */
    public static String selfLine(float radius) {
        return "No target, hits within " + blocks(radius);
    }

    static String blocks(float v) {
        String n = v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10f) / 10f);
        return n + " blocks";
    }
}
