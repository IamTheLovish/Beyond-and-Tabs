package com.solegendary.reignofnether.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cost read while the common config reloads on another thread (PR run 40469588386, bar-tests (5): mod loading
 * crashed in COMMON_SETUP with "ResourceCostConfigEntry.getWood() is null"). Forge 47's ConfigValue.get() checks its
 * cached value and returns it in two separate reads of a plain field, so a reload's clearCache() between them hands
 * back null. ResourceCosts.deferredLoadResourceCosts reads every entry on a mod-loading worker thread while the config
 * file is being corrected and reloaded.
 */
class ResourceCostConfigEntryTest {

    @Test
    void aReloadOnAnotherThreadNeverMakesACostReadNull() throws InterruptedException {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ResourceCostConfigEntry entry = ResourceCostConfigEntry.Unit(50, 80, 30, 20, 1,
            new ResourceCost("test", "reload_race_unit"), "Reload race unit");
        entry.define(builder);
        ForgeConfigSpec spec = builder.build();
        spec.setConfig(CommentedConfig.inMemory());

        AtomicBoolean stop = new AtomicBoolean();
        Thread reloader = new Thread(() -> {
            while (!stop.get())
                spec.afterReload();          // what a config reload does: every cached value cleared
        }, "config-reloader");
        reloader.start();
        int reads = 0;
        try {
            long end = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < end) {
                assertEquals(50, entry.getFood(), "food after " + reads + " reads");
                assertEquals(80, entry.getWood(), "wood after " + reads + " reads");
                assertEquals(30, entry.getOre(), "ore after " + reads + " reads");
                assertEquals(20, entry.getSeconds(), "seconds after " + reads + " reads");
                assertEquals(1, entry.getPopulation(), "population after " + reads + " reads");
                reads++;
            }
        } finally {
            stop.set(true);
            reloader.join();
        }
        assertTrue(reads > 0);
    }
}
