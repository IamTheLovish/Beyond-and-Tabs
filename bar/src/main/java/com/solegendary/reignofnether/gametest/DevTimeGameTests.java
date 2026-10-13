package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.DevTime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The title screen's "Built in ~N h" line (hud/DevTime): formatting only - the numbers come from CI. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DevTimeGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void dev_time_line_reads_well(GameTestHelper helper) {
        String a = DevTime.format(71.6, 556), b = DevTime.format(0.2, 0), c = DevTime.format(0, 12);
        if (!"Built in ~72 h · 556 commits".equals(a))
            helper.fail("expected 'Built in ~72 h · 556 commits', got '" + a + "'");
        else if (!"Built in ~1 h".equals(b))
            helper.fail("a short build should round up to 1 h and drop the unknown commit count, got '" + b + "'");
        else if (c != null)
            helper.fail("no hours should show nothing, got '" + c + "'");
        else
            helper.succeed();
    }
}
