package com.solegendary.reignofnether.gametest;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A plain ConfigValue.get() read while the config reloads on another thread (mixin/ConfigValueReloadRaceMixin). Without
 * the mixin, Forge 47's get() returns null when a reload's clearCache() lands between its two reads of the cached
 * value (the unit test for PR #285 hit it after 65 reads). This runs in the game so the mixin is applied, and it reads
 * the raw value the way the ~90 hud/ and settings call sites do, with no helper in between.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ConfigReloadRaceGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void a_config_read_during_a_reload_is_never_null(GameTestHelper helper) {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ForgeConfigSpec.ConfigValue<Integer> number = builder.define("reload_race_number", 80);
        ForgeConfigSpec.BooleanValue flag = builder.define("reload_race_flag", true);
        ForgeConfigSpec spec = builder.build();
        spec.setConfig(CommentedConfig.inMemory());

        AtomicBoolean stop = new AtomicBoolean();
        Thread reloader = new Thread(() -> {
            while (!stop.get())
                spec.afterReload();          // what a config reload does: every cached value cleared
        }, "config-reloader");
        reloader.setDaemon(true);
        reloader.start();
        long reads = 0;
        String fault = null;
        try {
            long end = System.nanoTime() + 1_500_000_000L;
            while (fault == null && System.nanoTime() < end) {
                Integer n = number.get();
                Boolean f = flag.get();
                if (n == null || f == null)
                    fault = "a read came back null after " + reads + " reads";
                else if (n != 80 || !f)
                    fault = "a read came back wrong after " + reads + " reads: " + n + ", " + f;
                reads++;
            }
        } finally {
            stop.set(true);
            try {
                reloader.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (fault != null)
            helper.fail(fault);
        else if (reads == 0)
            helper.fail("no reads ran");
        else
            helper.succeed();
    }
}
