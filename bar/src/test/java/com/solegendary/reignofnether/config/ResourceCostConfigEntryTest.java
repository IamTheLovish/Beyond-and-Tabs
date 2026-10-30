package com.solegendary.reignofnether.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cost read while the common config reloads on another thread (PR run 40469588386, bar-tests (5): mod loading
 * crashed in COMMON_SETUP with "ResourceCostConfigEntry.getWood() is null"). Forge 47's ConfigValue.get() checks its
 * cached value and returns it in two separate reads of a plain field, so a reload's clearCache() between them hands
 * back null. ResourceCosts.deferredLoadResourceCosts reads every entry on a mod-loading worker thread while the config
 * file is being corrected and reloaded.
 *
 * A read that only retries get() is luck, not a fix: #221's CI (run 40587228567) had "unit.Ore cost stayed null across
 * 8 reads" on a loaded runner. So reloads here run back to back on several threads (a busy runner's worst case) while
 * several threads read: a read must never be null however the reloads fall.
 */
class ResourceCostConfigEntryTest {

    private static final int RELOADERS = 4;
    private static final int READERS = 4;

    /**
     * lovish, Oct 29: "beyond all reason doesnt use supply or unit cap". A building's entry defines no population, so
     * an old config file that still gives a building a "Population value" (a house's 10) is ignored: it reads 0.
     */
    @Test
    void aBuildingReadsNoPopulationEvenFromAnOldConfigFile() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ResourceCostConfigEntry entry = ResourceCostConfigEntry.Building(100, 50, 0,
            new ResourceCost("test", "old_supply_house"), "Old supply house");
        entry.define(builder);
        ForgeConfigSpec spec = builder.build();
        CommentedConfig old = CommentedConfig.inMemory();
        old.set(List.of("Old supply house", "Food cost"), 100);
        old.set(List.of("Old supply house", "Wood cost"), 50);
        old.set(List.of("Old supply house", "Ore cost"), 0);
        old.set(List.of("Old supply house", "Time to create"), 0);
        old.set(List.of("Old supply house", "Population value"), 10);
        spec.setConfig(old);
        assertEquals(0, entry.getPopulation(), "a building's population from an old config file");
        assertEquals(100, entry.getFood());
    }

    @Test
    void aReloadOnAnotherThreadNeverMakesACostReadNull() throws InterruptedException {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ResourceCostConfigEntry entry = ResourceCostConfigEntry.Unit(50, 80, 30, 20, 1,
            new ResourceCost("test", "reload_race_unit"), "Reload race unit");
        entry.define(builder);
        ForgeConfigSpec spec = builder.build();
        spec.setConfig(CommentedConfig.inMemory());

        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicLong reads = new AtomicLong();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < RELOADERS; i++)
            threads.add(new Thread(() -> {
                while (!stop.get())
                    spec.afterReload();          // what a config reload does: every cached value cleared
            }, "config-reloader-" + i));
        long end = System.nanoTime() + 3_000_000_000L;
        for (int i = 0; i < READERS; i++)
            threads.add(new Thread(() -> {
                try {
                    while (!stop.get() && System.nanoTime() < end) {
                        long n = reads.get();
                        assertEquals(50, entry.getFood(), "food after " + n + " reads");
                        assertEquals(80, entry.getWood(), "wood after " + n + " reads");
                        assertEquals(30, entry.getOre(), "ore after " + n + " reads");
                        assertEquals(20, entry.getSeconds(), "seconds after " + n + " reads");
                        assertEquals(1, entry.getPopulation(), "population after " + n + " reads");
                        reads.incrementAndGet();
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                    stop.set(true);
                }
            }, "cost-reader-" + i));
        threads.forEach(Thread::start);
        try {
            for (Thread t : threads.subList(RELOADERS, threads.size()))
                t.join();
        } finally {
            stop.set(true);
            for (Thread t : threads)
                t.join();
        }
        assertNull(failure.get(), () -> "a cost read failed during reloads: " + failure.get());
        assertTrue(reads.get() > 0);
    }
}
