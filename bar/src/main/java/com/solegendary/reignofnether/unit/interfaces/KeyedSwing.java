package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.unit.HeldBlow;

import javax.annotation.Nullable;

/**
 * A unit whose attack animation is a keyed clip of a known length: vanilla plays an arm swing over 6 ticks
 * ({@code LivingEntity.getCurrentSwingDuration}), and a body whose attack is sampled by {@code attackTime} (0..1 over
 * the swing) would rush an 18-tick clip through in 6 - the Halberdier's lunge thrust, keyed for 18 ticks in
 * design/anims/halberdier.json, was a 0.3 s flicker in every eyes clip (3 frames at 10 fps), with no wind-up a player
 * could read. {@code LivingEntityMixin} returns this length as the swing duration on both sides (each side counts its
 * own swingTime from the animate packet), so the clip plays at the speed it was drawn for.
 */
public interface KeyedSwing {

    /** The attack clip's length in ticks, or 0 to keep vanilla's swing (a body without a keyed attack). */
    int keyedSwingTicks();

    /**
     * Ticks from the swing's start to the clip's hit key, where the blow lands (HeldBlow), or 0 to land it with the
     * swing as vanilla does.
     */
    default int keyedHitTicks() {
        return 0;
    }

    /** The blow held from the swing to the hit key; null for a body that lands it with the swing. */
    @Nullable
    default HeldBlow heldBlow() {
        return null;
    }
}
