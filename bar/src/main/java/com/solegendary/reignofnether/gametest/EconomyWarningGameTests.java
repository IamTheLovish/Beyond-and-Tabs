package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.EconomyWarning;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The economy banners' consistency rules (hud/EconomyWarning): one line at a time by priority stall > overflow >
 * idle factories, and a stall never blames a pool that is nearly full (playtest clip: "short on metal" and
 * "Overflowing metal" together). Pure rules, no world.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class EconomyWarningGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void one_economy_line_by_priority(GameTestHelper helper) {
        if (EconomyWarning.choose(true, true, 9) != EconomyWarning.Kind.STALL)
            helper.fail("stall beats overflow and idle factories");
        else if (EconomyWarning.choose(false, true, 9) != EconomyWarning.Kind.OVERFLOW)
            helper.fail("overflow beats idle factories");
        else if (EconomyWarning.choose(false, false, 9) != EconomyWarning.Kind.IDLE_FACTORIES)
            helper.fail("idle factories show when nothing louder is up");
        else if (EconomyWarning.choose(false, false, 0) != EconomyWarning.Kind.NONE)
            helper.fail("nothing to say, nothing shown");
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void stall_never_blames_a_full_pool(GameTestHelper helper) {
        if (EconomyWarning.stallPool(0.10f, 0.80f) != EconomyWarning.Pool.METAL)
            helper.fail("metal at 10% and energy at 80%: short on metal");
        else if (EconomyWarning.stallPool(0.90f, 0.20f) != EconomyWarning.Pool.ENERGY)
            helper.fail("energy at 20%: short on energy");
        else if (EconomyWarning.stallPool(0.97f, 0.99f) != EconomyWarning.Pool.UNNAMED)
            helper.fail("both pools over 95%: the stall is not a shortage, it must not say 'short on metal'");
        else if (EconomyWarning.stallPool(0.97f, 0.50f) != EconomyWarning.Pool.ENERGY)
            helper.fail("metal overflowing, energy half: short on energy, never metal");
        else
            helper.succeed();
    }
}
