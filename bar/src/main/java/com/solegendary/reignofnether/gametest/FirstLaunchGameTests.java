package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.onboarding.FirstLaunch;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Progress noise and first-launch defaults (onboarding/FirstLaunch, GameruleServerEvents.onServerStarted): no
 * advancement chat lines on a started server, advancement and recipe toasts dropped, full screen only on a real
 * install's first launch.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FirstLaunchGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void advancements_are_not_announced(GameTestHelper helper) {
        if (helper.getLevel().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS))
            helper.fail("announceAdvancements must be off once the server has started");
        else
            helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void first_launch_and_toast_rules(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (!FirstLaunch.hideToast(true, false) || !FirstLaunch.hideToast(false, true))
            problems.add("advancement and recipe toasts must be hidden");
        if (FirstLaunch.hideToast(false, false))
            problems.add("other toasts must still show");
        if (!FirstLaunch.goFullscreen(true, false, false))
            problems.add("a real install's first launch goes full screen");
        if (FirstLaunch.goFullscreen(false, false, false))
            problems.add("a dev run (runClient, check harnesses) must never go full screen");
        if (FirstLaunch.goFullscreen(true, true, false))
            problems.add("once applied, a player who went windowed stays windowed");
        if (FirstLaunch.goFullscreen(true, false, true))
            problems.add("already full screen: nothing to toggle");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
