package com.solegendary.reignofnether.resources;

/**
 * Skeleton for the red run of EconomyMathTest (tests first): every rule throws until it is implemented.
 */
public final class EconomyMath {
    private EconomyMath() { }

    public static float granted(float stall, float metalPool, float wantMetal, float energyPool, float wantEnergy) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static float stall(float metalPool, float metalDemand, float energyPool, float energyDemand) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static float banked(float income, float storage, float pool) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static float lostFromPool(float pool, float newStorage, float storageLost) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static float energyToConvert(float energy, float energyStorage, float level, float metalRoom,
                                        float capacityPerTick, float ratio) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static float clampLevel(float level, float min) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static boolean isRealLevel(float level) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static boolean sliderTooFast(long now, long last, int minTicks) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static float shareExcess(float metal, float shareLevel, float storage, float maxPerTick) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static float[] splitAmongAllies(float amount, float[] rooms) {
        throw new UnsupportedOperationException("not implemented");
    }
}
