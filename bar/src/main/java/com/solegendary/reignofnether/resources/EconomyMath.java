package com.solegendary.reignofnether.resources;

/**
 * The flow economy's arithmetic (BAR's rules, spelled out in {@link EconomyServerEvents}), as plain floats with no
 * world, pool or player in sight - so EconomyMathTest can pin every rule in milliseconds. EconomyServerEvents gathers
 * the numbers, asks here, and applies the answer.
 */
public final class EconomyMath {
    private EconomyMath() { }

    /** Room below this is not worth a transfer (the same dust limit the ally share always used). */
    static final float DUST = 0.0001f;
    /** A share slider this close to 1 means "only the overflow at full storage". */
    static final float SHARE_OFF = 0.999f;

    /**
     * The fraction (0-1) of the progress a consumer asked for that it may make this tick: the stall, cut down to what
     * the pool can really pay of the metal and the energy the job needs. A resource the job does not need never
     * blocks it; a pool at or below zero pays nothing. The caller deducts {@code want * granted}.
     */
    public static float granted(float stall, float metalPool, float wantMetal, float energyPool, float wantEnergy) {
        float granted = stall;
        if (wantMetal > 0)
            granted = Math.min(granted, Math.max(0, metalPool) / wantMetal);
        if (wantEnergy > 0)
            granted = Math.min(granted, Math.max(0, energyPool) / wantEnergy);
        return Math.max(0, Math.min(1, granted));
    }

    /**
     * The stall for the next tick, from the demand registered this tick (before the Overcharge cap): the scarcer of
     * the two resources decides, and every consumer is throttled by the same share - BAR's even slowdown.
     */
    public static float stall(float metalPool, float metalDemand, float energyPool, float energyDemand) {
        float stallMetal = metalDemand <= 0 ? 1f : Math.max(0, metalPool) / metalDemand;
        float stallEnergy = energyDemand <= 0 ? 1f : Math.max(0, energyPool) / energyDemand;
        return Math.max(0, Math.min(1f, Math.min(stallMetal, stallEnergy)));
    }

    /** How much of this tick's {@code income} fits in storage; the rest is overflow (shared with allies, else wasted). */
    public static float banked(float income, float storage, float pool) {
        return Math.max(0, Math.min(income, storage - pool));
    }

    /**
     * What a shrinking storage costs the pool (BAR): the pool drops by up to the storage lost, but never below the new
     * cap - so a refund sitting briefly above the cap is not eaten by an unrelated loss. Returns the amount to remove.
     */
    public static float lostFromPool(float pool, float newStorage, float storageLost) {
        if (storageLost <= 0 || pool <= newStorage)
            return 0;
        return Math.min(storageLost, pool - newStorage);
    }

    /**
     * The energy a player's converters turn into metal this tick: only the energy above {@code level} of storage (so
     * they never starve construction), at most the converters' capacity for one tick, and no more than the metal
     * storage has room for at {@code ratio} energy per metal. 0 when there is nothing to convert.
     */
    public static float energyToConvert(float energy, float energyStorage, float level, float metalRoom,
                                        float capacityPerTick, float ratio) {
        float spare = energy - energyStorage * level;
        if (spare <= 0 || metalRoom <= 0)
            return 0;
        return Math.min(spare, Math.min(capacityPerTick, metalRoom * ratio));
    }

    /** A slider level clamped into [min, 1]. */
    public static float clampLevel(float level, float min) {
        return Math.max(min, Math.min(1f, level));
    }

    /** A level off the network is usable if it is a real number (out-of-range values are clamped, not refused). */
    public static boolean isRealLevel(float level) {
        return !Float.isNaN(level) && !Float.isInfinite(level);
    }

    /** Whether a slider change comes too soon after the player's last accepted one (a drag sends faster). */
    public static boolean sliderTooFast(long now, long last, int minTicks) {
        return now - last < minTicks;
    }

    /**
     * The metal the share slider gives away this tick: what is above {@code shareLevel} of storage, at most
     * {@code maxPerTick}; 0 when the slider is at 1 (only the overflow is shared) or the excess is dust.
     */
    public static float shareExcess(float metal, float shareLevel, float storage, float maxPerTick) {
        if (shareLevel >= SHARE_OFF)
            return 0;
        float excess = Math.min(metal - shareLevel * storage, maxPerTick);
        return excess <= DUST ? 0 : excess;
    }

    /**
     * BAR overflow: splits {@code amount} evenly between the allies that have room ({@code rooms[i]}; at or below dust
     * counts as none), then offers what a nearly-full ally could not take to the others - two passes. Never gives an
     * ally more than its room nor more than {@code amount} in all; what nobody can hold is not given (it is waste).
     *
     * @return what each ally takes, same order as {@code rooms}
     */
    public static float[] splitAmongAllies(float amount, float[] rooms) {
        float[] left = rooms.clone();
        float[] gives = new float[rooms.length];
        float remaining = amount;
        for (int pass = 0; pass < 2 && remaining > DUST; pass++) {
            int withRoom = 0;
            for (float room : left)
                if (room > DUST)
                    withRoom++;
            if (withRoom == 0)
                break;
            float share = remaining / withRoom;
            for (int i = 0; i < left.length; i++) {
                float give = Math.min(share, left[i]);
                if (give <= DUST)
                    continue;
                gives[i] += give;
                left[i] -= give;
                remaining -= give;
            }
        }
        return gives;
    }
}
