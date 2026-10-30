package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.ProductionHudLogic;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Factory tab and Repeat button rules (hud/ProductionHudLogic): which factories count as producing, when Repeat shows. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProductionHudLogicGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void hudlogic_only_built_factories_with_a_queue_count_as_producing(GameTestHelper helper) {
        if (!ProductionHudLogic.isProducing(true, 1))
            helper.fail("A built factory with an item at the front of its queue is producing");
        else if (!ProductionHudLogic.isProducing(true, 4))
            helper.fail("A built factory with a long queue is producing");
        else if (ProductionHudLogic.isProducing(true, 0))
            helper.fail("An idle factory (empty queue) must not get a Factory tab icon");
        else if (ProductionHudLogic.isProducing(false, 1))
            helper.fail("A building still under construction must not count as producing");
        else
            helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void hudlogic_repeat_shows_for_one_owned_factory(GameTestHelper helper) {
        if (!ProductionHudLogic.repeatVisible(1, 1))
            helper.fail("Repeat must show with a single owned built factory selected");
        else if (ProductionHudLogic.repeatVisible(1, 0))
            helper.fail("Repeat must not show for a selected building that is not an owned built factory");
        else if (ProductionHudLogic.repeatVisible(0, 0))
            helper.fail("Repeat must not show with nothing selected");
        else if (ProductionHudLogic.repeatTarget(List.of(false)) != true || ProductionHudLogic.repeatTarget(List.of(true)) != false)
            helper.fail("One press on a single factory flips it");
        else
            helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void hudlogic_repeat_shows_for_two_same_type_factories(GameTestHelper helper) {
        if (!ProductionHudLogic.repeatVisible(2, 2))
            helper.fail("Repeat vanished with two factories selected (the reported bug)");
        else if (!ProductionHudLogic.repeatTarget(List.of(false, false)))
            helper.fail("Both off: a press turns both on");
        else if (ProductionHudLogic.repeatTarget(List.of(true, true)))
            helper.fail("Both on: a press turns both off");
        else
            helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void hudlogic_repeat_for_two_mixed_type_factories_and_mixed_states(GameTestHelper helper) {
        // two different factory types: both are owned built factories, so the count rule is the same as for one type
        if (!ProductionHudLogic.repeatVisible(2, 2))
            helper.fail("Repeat must show for two different factory types selected together");
        else if (ProductionHudLogic.repeatVisible(2, 1))
            helper.fail("Repeat must hide when one of two selected buildings is not a factory (e.g. a house)");
        else if (!ProductionHudLogic.repeatTarget(List.of(true, false)) || !ProductionHudLogic.repeatTarget(List.of(false, true)))
            helper.fail("Mixed repeat states: a press turns everything on");
        else
            helper.succeed();
    }
}
