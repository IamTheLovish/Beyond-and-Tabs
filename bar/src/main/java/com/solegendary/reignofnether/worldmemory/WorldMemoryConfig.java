package com.solegendary.reignofnether.worldmemory;

import com.solegendary.reignofnether.config.ReignOfNetherCommonConfigs;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The world memory's tunables as lovish can set them (claude/world-that-remembers.md section 9, answered with the
 * spec's recommendations until he says otherwise): the common config's [world_memory] block, read once at server
 * start into a {@link DecayParams} that every sync packet carries. Before the config loads (or without one, as in the
 * headless tests) the spec's defaults apply.
 */
public final class WorldMemoryConfig {
    private WorldMemoryConfig() { }

    private static int get(ForgeConfigSpec.ConfigValue<Integer> v, int fallback) {
        try {
            return v.get();
        } catch (IllegalStateException | NullPointerException e) {   // config not loaded yet
            return fallback;
        }
    }

    private static boolean get(ForgeConfigSpec.ConfigValue<Boolean> v, boolean fallback) {
        try {
            return v.get();
        } catch (IllegalStateException | NullPointerException e) {
            return fallback;
        }
    }

    public static boolean scarsEnabled() {
        return get(ReignOfNetherCommonConfigs.WORLD_MEMORY_SCARS, true);
    }

    /** The configured decay: seconds to ticks, percent floors to channel units; trample and stain keep the spec's. */
    public static DecayParams decayParams() {
        int scorchHalf = get(ReignOfNetherCommonConfigs.WORLD_MEMORY_SCORCH_HALF_LIFE, DecayParams.SCORCH_HALF_LIFE / 20) * 20;
        int scorchFloor = Math.round(get(ReignOfNetherCommonConfigs.WORLD_MEMORY_SCORCH_FLOOR, 30) / 100f * 255);
        int craterHalf = get(ReignOfNetherCommonConfigs.WORLD_MEMORY_CRATER_HALF_LIFE, DecayParams.CRATER_HALF_LIFE / 20) * 20;
        int craterFloor = Math.round(get(ReignOfNetherCommonConfigs.WORLD_MEMORY_CRATER_FLOOR, 50) / 100f * GroundMemory.CRATER_MAX);
        return new DecayParams(
            new int[] { scorchHalf, DecayParams.TRAMPLE_HALF_LIFE, craterHalf, DecayParams.STAIN_HALF_LIFE },
            new int[] { scorchFloor, DecayParams.TRAMPLE_FLOOR, craterFloor, DecayParams.STAIN_FLOOR });
    }
}
