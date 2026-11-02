package com.solegendary.reignofnether.unit.modelling.motion;

/**
 * A hit reads as a hit (combat feel gain 3, lovish Oct 30): a unit flashes white for a moment, as strong as the
 * blow's share of its health - chip damage a flicker, a big blow a pop - then shows a short red. Vanilla gave every
 * hit the same 10 ticks of red, and in a big fight that red sat on so many models it read as a haze, not as hits.
 *
 * Pure: the overlay rule (mixin/LivingEntityRendererMixin) feeds it vanilla's hurtTime (10 as the damage event
 * arrives, one less each tick after), the frame's partial tick and the blow's share of max health (the health the
 * server synced since the hit, against {@link AnimState#HIT_HEALTH0}). The white is vanilla's own overlay row, the
 * one a creeper swells with, so it costs no draw call.
 */
public final class HitFlash {

    private HitFlash() {}

    /** The white lasts this many ticks from the hit, then the red shows until {@link #RED_END}. */
    public static final float WHITE_TICKS = 3f, RED_END = 7f;
    /** The white a chip of damage gets, and the share of max health that flashes at full strength. */
    public static final float MIN_STRENGTH = 0.25f, FULL_SHARE = 0.25f;

    /**
     * Ticks since the hit at this frame. The entity ticks once between the damage event and the first frame that can
     * show it (hurtTime 9 there); a frame drawn before that tick (hurtTime 10) is the hit's own moment.
     */
    public static float sinceHit(int hurtTime, float partialTick) {
        return Math.max(0f, 9 - hurtTime + partialTick);
    }

    /** The flash's strength, MIN_STRENGTH .. 1, from the blow's share of max health (unknown: the minimum). */
    public static float strength(float share) {
        if (!(share > 0f))
            return MIN_STRENGTH;
        return Math.min(1f, MIN_STRENGTH + (1f - MIN_STRENGTH) * share / FULL_SHARE);
    }

    /** The white overlay, 0..1, on a unit at this frame: the blow's strength at the hit, gone by WHITE_TICKS. */
    public static float white(int hurtTime, float partialTick, float share) {
        if (hurtTime <= 0)
            return 0f;
        float since = sinceHit(hurtTime, partialTick);
        return since >= WHITE_TICKS ? 0f : strength(share) * (1f - since / WHITE_TICKS);
    }

    /** Whether the unit shows the red overlay at this frame: after the white, until RED_END. */
    public static boolean red(int hurtTime, float partialTick) {
        if (hurtTime <= 0)
            return false;
        float since = sinceHit(hurtTime, partialTick);
        return since >= WHITE_TICKS && since < RED_END;
    }
}
