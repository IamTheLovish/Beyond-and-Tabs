package com.solegendary.reignofnether.unit.modelling.animations;

import net.minecraft.util.Mth;

/**
 * Client only. The shared "life" layer every rig uses on top of its keyed clips (design/ANIMATION-STANDARD.md,
 * lovish Oct 21 2026: animations "extremely realistic and cohesive"; dragons and animals "look like they're breathing
 * and move articulated"). One implementation for every unit, so humans and creatures breathe and settle alike.
 *
 * All functions are pure: they take the entity's age (ticks + partial) and its id (so a block of units is never in
 * step) and return angles in radians or small offsets.
 */
public final class Life {

    private Life() {}

    private static final float TAU = (float) (Math.PI * 2);

    /** Each entity's own phase offset, in ticks: units side by side never breathe or beat in unison. */
    public static float offset(int id) {
        return (id * 37) & 255;
    }

    /**
     * Breath, -1..1: a slow cycle with a quick in-breath and a longer out-breath (a real breath is not a pure sine).
     * periodTicks: ~60 for a man at rest, longer for big bodies (100-140 for a dragon), shorter when panting.
     */
    public static float breath(float age, int id, float periodTicks) {
        float p = ((age + offset(id)) / periodTicks) % 1f;
        // in-breath over the first 40% of the cycle, out-breath over the rest
        float s = p < 0.4f ? p / 0.4f : 1f - (p - 0.4f) / 0.6f;
        return smooth(s) * 2f - 1f;
    }

    /**
     * A wing beat that is not a sine: the downstroke (the power stroke) takes `down` of the cycle and the folded
     * upstroke the rest, faster. Returns the beat angle -1 (top) .. 1 (bottom) and, through {@link #upstroke}, whether
     * the wing is recovering (tips fold then).
     */
    public static float wingBeat(float age, int id, float periodTicks, float down) {
        float p = ((age + offset(id)) / periodTicks) % 1f;
        float s = p < down ? p / down : 1f - (p - down) / (1f - down);
        return smooth(s) * 2f - 1f;
    }

    /** 0..1: how far into the upstroke the wing is (0 during the downstroke) - drives the tip fold. */
    public static float upstroke(float age, int id, float periodTicks, float down) {
        float p = ((age + offset(id)) / periodTicks) % 1f;
        return p < down ? 0f : Mth.sin((p - down) / (1f - down) * (float) Math.PI);
    }

    /**
     * A wave travelling down a chain (neck, back, tail): segment k sees the motion `lagTicks * k` later than the root,
     * so a swing starts at the root and rolls out to the tip. Returns sin of the delayed phase.
     */
    public static float chain(float age, int id, float periodTicks, int k, float lagTicks) {
        return Mth.sin((age + offset(id) - k * lagTicks) / periodTicks * TAU);
    }

    /**
     * An idle behaviour that happens now and then (a head shake, an ear flick, a grazing dip, a tail swish): 0 most of
     * the time, a smooth 0..1..0 bump lasting `lengthTicks` once every `periodTicks`. `seed` separates the behaviours
     * of one entity (so its ear flick and its head shake never coincide) and, with the entity's id, the entities.
     */
    public static float bump(float age, int id, int seed, float periodTicks, float lengthTicks) {
        float t = (age + offset(id) + seed * 53) % periodTicks;
        return t < lengthTicks ? Mth.sin(t / lengthTicks * (float) Math.PI) : 0f;
    }

    /** A quick shake inside a {@link #bump}: `cycles` swings over the bump, -1..1, fading in and out with it. */
    public static float shake(float age, int id, int seed, float periodTicks, float lengthTicks, float cycles) {
        float t = (age + offset(id) + seed * 53) % periodTicks;
        if (t >= lengthTicks)
            return 0f;
        return Mth.sin(t / lengthTicks * (float) Math.PI) * Mth.sin(t / lengthTicks * cycles * TAU);
    }

    /** A slow wander -1..1 that never repeats cleanly: two incommensurate sines, phase-offset per entity. */
    public static float wander(float age, int id, float periodTicks) {
        float t = age + offset(id);
        return 0.6f * Mth.sin(t / periodTicks * TAU) + 0.4f * Mth.sin(t / (periodTicks * 0.37f) * TAU + 1.3f);
    }

    /** Smoothstep 0..1 - eases both ends so a cycle never snaps at its turning points. */
    public static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Degrees to radians, for readable call sites. */
    public static float deg(float d) {
        return d * Mth.DEG_TO_RAD;
    }
}
