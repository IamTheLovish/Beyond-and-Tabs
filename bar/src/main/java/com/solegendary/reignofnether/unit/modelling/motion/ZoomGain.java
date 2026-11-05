package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.unit.modelling.animations.Life;

/**
 * How much bigger a living-army behaviour plays as the camera pulls back (design/living_army.md, section 2; lovish
 * Oct 27): realistic (x1) at zoom {@link #NEAR} and closer, easing to x(1 + k) at {@link #FAR}, where units become
 * icons. It scales amplitude only, never speed - faster reads as twitchy, not alive. Pure: no client classes.
 */
public final class ZoomGain {

    private ZoomGain() {}

    public static final float NEAR = 20f, FAR = 70f;

    /** Each behaviour's k: x(1 + k) at the RTS zoom. Flinch (and stagger) reads first from far, so it grows most. */
    public static final float K_BREATH = 1.5f, K_WEIGHT = 1.0f, K_FIDGET = 1.3f, K_LEAN = 1.5f, K_ACCEL = 1.5f,
        K_FLINCH = 2.0f, K_SECONDARY = 1.2f;

    public static float gain(float zoom, float k) {
        return 1f + k * Life.smooth((zoom - NEAR) / (FAR - NEAR));
    }
}
