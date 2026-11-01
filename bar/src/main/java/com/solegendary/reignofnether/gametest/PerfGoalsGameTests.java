package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.perf.GoalTimes;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.RandomLookAroundUnitGoal;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The [Goals] line (perf/GoalTimes; lovish Oct 31 "the game keeps lagging"): while [Sys] measures, every goal call the
 * units' goal selectors make is timed and summed per goal class, so a slow game's log names the goals the AI time goes
 * to. Its own batch: no other test's units in the window.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfGoalsGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * 20 idle vindicators measured for 60 ticks: their RandomLookAroundUnitGoal's calls are counted under its class, at
     * least one per unit (each idle unit thinks every 4th tick, so ~15 times in the window; the green samples counted
     * 180-240). Not that the line prints it: it is cheap, so with every other mob in the test world in the window it
     * folds into "(+N smaller)" (PR build 41182502247: 179 calls, folded).
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "perf_goals")
    public static void the_goals_line_counts_the_goal_calls_of_every_unit(GameTestHelper helper) {
        BlockPos pad = ArenaReady.pad(helper, 215);
        String owner = "gametest_goals_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Entity> made = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
            if (v == null) {
                cleanUp(made);
                helper.fail("could not create the units");
                return;
            }
            v.moveTo(pad.getX() + 1.5 + (i % 5) * 2.5, pad.getY(), pad.getZ() + 1.5 + (i / 5) * 2.5, 0, 0);
            v.setOwnerName(owner);
            helper.getLevel().addFreshEntity(v);
            made.add(v);
        }
        Object hold = new Object();
        TestCleanup.atEnd(helper, () -> Sys.force(hold, false));
        helper.runAfterDelay(40, () -> {
            Sys.force(hold, true);
            GoalTimes.snapshot();   // a fresh window from here
        });
        helper.runAfterDelay(100, () -> {
            long looks = GoalTimes.calls(RandomLookAroundUnitGoal.class);
            String line = GoalTimes.snapshot();
            Sys.force(hold, false);
            cleanUp(made);
            ReignOfNether.LOGGER.info("GameTest [Goals] 20 idle vindicators, 60 ticks: {} RandomLookAroundUnitGoal calls; {}", looks, line);
            if (line == null)
                helper.fail("no [Goals] line came back");
            else if (looks < made.size())
                helper.fail("the idle units' RandomLookAroundUnitGoal was timed " + looks + " times in 60 ticks (at least "
                    + made.size() + ", one per unit): " + line);
            else
                helper.succeed();
        });
    }

    private static void cleanUp(List<Entity> made) {
        for (Entity e : made) {
            UnitServerEvents.clearQueuedActions(e.getId());
            e.discard();
        }
    }
}
