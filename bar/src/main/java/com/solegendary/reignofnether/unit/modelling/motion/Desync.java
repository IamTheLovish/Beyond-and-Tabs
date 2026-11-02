package com.solegendary.reignofnether.unit.modelling.motion;

/**
 * No two units in step (design/living_army.md, section 2): each unit's own tempo (+-8%) and amplitude (+-15%) for
 * every living-army behaviour, and the seeded draws its idle scheduler makes - all from its entity id, so every client
 * sees the same variety and a freshly trained block (consecutive ids) is as varied as a scattered one. Pure.
 */
public final class Desync {

    private Desync() {}

    private static final int SALT_TEMPO = 0x7E, SALT_AMP = 0xA3;

    /** A well-mixed value in [0, 1) for (id, salt): neighbouring ids and neighbouring salts are unrelated. */
    public static float hash01(int id, int salt) {
        int h = id * 0x9E3779B9 + salt * 0x85EBCA6B;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h >>> 8) / (float) (1 << 24);
    }

    /** The unit's tempo multiplier, 0.92..1.08. */
    public static float tempo(int id) {
        return 0.92f + 0.16f * hash01(id, SALT_TEMPO);
    }

    /** The unit's amplitude multiplier, 0.85..1.15. */
    public static float amp(int id) {
        return 0.85f + 0.30f * hash01(id, SALT_AMP);
    }
}
