package com.solegendary.reignofnether.gametest;

import com.google.common.base.Stopwatch;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * TestCleanup's undo steps run on every way a test ends - a callback that throws (helper.fail, an assertion,
 * ArenaReady.whenTicking), a timeout and a pass - so a failing test no longer leaves its units, its sky pad or the
 * rtsPathfinding flag to the tests after it (design/gametest_cleanup_audit.md). Each case runs on a test of its own
 * that is made here and ticked by hand: a GameTestInfo no runner knows about, so its failure is reported nowhere but
 * to its listeners (TestCleanup's), exactly as vanilla tells them for a real test.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TestCleanupGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    // its own batch: it takes the rtsPathfinding hold (no other test of the batch may see the flag change)
    static final String BATCH = "test_cleanup";

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 40)
    public static void cleanup_runs_when_a_test_fails_times_out_or_passes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        if (level.getGameTime() < 2) {
            helper.fail("game time " + level.getGameTime() + ": a hand-made test would start itself on this tick");
            return;
        }
        boolean flagBefore = UnitServerEvents.rtsPathfinding;
        int pendingBefore = TestCleanup.pending();
        // one spot above the arena per case, as a sky pad: outside the template, so only the undo puts it back
        int[] y = { 20 };
        Consumer<GameTestInfo> throwsInACallback = info -> info.setRunAtTickTime(0,
            () -> { throw new GameTestAssertException("the units were not ticking (made up)"); });
        Consumer<GameTestInfo> nothingEver = info -> { };
        Consumer<GameTestInfo> passes = info -> info.setRunAtTickTime(0, info::succeed);
        check(helper, "a thrown assertion", Integer.MAX_VALUE, throwsInACallback, true, y[0]++, problems);
        check(helper, "a timeout", 1, nothingEver, true, y[0]++, problems);
        check(helper, "a pass", Integer.MAX_VALUE, passes, false, y[0]++, problems);
        if (UnitServerEvents.rtsPathfinding != flagBefore)
            problems.add("rtsPathfinding is " + UnitServerEvents.rtsPathfinding + " after all three, was " + flagBefore);
        if (TestCleanup.pending() != pendingBefore)
            problems.add(TestCleanup.pending() - pendingBefore + " undo steps still waiting after all three ended");
        sweep(helper, problems);
        TerrainGameTests.finish(helper, problems);
    }

    /**
     * The failed-test sweep (GameTestShards' reporter calls it for every failed test), run on this test as if it had
     * failed: its units in its arena (the last x/z rows too) and on its own pad go. A unit passing high over the arena
     * (TerrainGameTests' formation at y 280 walks over a neighbour's arena) and one in the gap beside it stay, and so
     * does a unit on the pad of a second test that is still running, though that pad is over this arena: it goes only
     * when its own test ends.
     */
    private static void sweep(GameTestHelper helper, List<String> problems) {
        ServerLevel level = helper.getLevel();
        GameTestInfo self = TestCleanup.info(helper);
        TestCleanup.BlockUndo pad = TestCleanup.blocks(helper);
        BlockPos padAt = helper.absolutePos(new BlockPos(8, 50, 8));
        pad.set(padAt, Blocks.STONE.defaultBlockState());
        // the bounds' max is the last block's corner: that block's middle is past it, as a unit on the last row is
        net.minecraft.world.phys.AABB bounds = self.getStructureBounds();
        BlockPos lastRow = BlockPos.containing(bounds.maxX, bounds.minY + 2, bounds.maxZ);
        // the other test: made by hand, still running while this one is swept; its pad over this arena
        GameTestInfo other = handMade(level, 1);
        GameTestHelper otherHelper = new GameTestHelper(other);
        BlockPos otherPadAt = helper.absolutePos(new BlockPos(12, 40, 12));
        BlockState otherWas = level.getBlockState(otherPadAt);
        TestCleanup.blocks(otherHelper).set(otherPadAt, Blocks.STONE.defaultBlockState());
        List<Entity> othersUnits = new ArrayList<>();
        TestCleanup.entities(otherHelper, othersUnits);

        Mob inArena = pig(level, helper.absolutePos(new BlockPos(4, 2, 4)));
        Mob onLastRow = pig(level, lastRow);
        Mob onPad = pig(level, padAt.above());
        Mob passingOver = pig(level, helper.absolutePos(new BlockPos(8, 70, 8)));
        Mob beside = pig(level, helper.absolutePos(new BlockPos(-3, 2, 8)));
        Mob othersUnit = pig(level, otherPadAt.above());
        List<Mob> all = java.util.Arrays.asList(inArena, onLastRow, onPad, passingOver, beside, othersUnit);
        if (all.contains(null)) {
            problems.add("sweep: could not create its pigs");
            for (Mob m : all)
                if (m != null)
                    m.discard();
            return;
        }
        othersUnits.add(othersUnit);
        int removed = TestCleanup.sweep(self);
        if (!inArena.isRemoved())
            problems.add("sweep: the unit in the arena is still alive");
        if (!onLastRow.isRemoved())
            problems.add("sweep: the unit on the arena's last row is still alive (the box is one block short)");
        if (!onPad.isRemoved())
            problems.add("sweep: the unit on its own pad is still alive");
        if (passingOver.isRemoved())
            problems.add("sweep: the unit passing 70 above the arena, on no pad of it, was removed");
        if (beside.isRemoved())
            problems.add("sweep: the unit beside the arena was removed");
        if (othersUnit.isRemoved())
            problems.add("sweep: the still-running test's unit on its own pad over this arena was removed");
        if (removed != 3)
            problems.add("sweep: removed " + removed + " entities, want the 3 of this test");
        // then the other test times out: its own cleanup takes its unit and its pad
        other.tick();
        if (!other.hasFailed())
            problems.add("sweep: the other test did not time out");
        if (!othersUnit.isRemoved())
            problems.add("sweep: the other test's unit is still alive after it ended");
        if (!level.getBlockState(otherPadAt).equals(otherWas))
            problems.add("sweep: the other test's pad is " + level.getBlockState(otherPadAt) + " after it ended");
        for (Mob m : all)
            if (!m.isRemoved())
                m.discard();
        if (!level.getBlockState(otherPadAt).equals(otherWas))
            level.setBlock(otherPadAt, otherWas, 3);
    }

    private static Mob pig(ServerLevel level, BlockPos at) {
        Mob m = EntityType.PIG.create(level);
        if (m == null)
            return null;
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        m.setNoAi(true);
        m.setNoGravity(true);
        level.addFreshEntity(m);
        return m;
    }

    /**
     * One hand-made test: a unit, a block above the arena and the rtsPathfinding hold, each with TestCleanup; then the
     * test is ticked once, which ends it as {@code ending} sets it up, and every one of them must be undone.
     */
    private static void check(GameTestHelper helper, String how, int timeout, Consumer<GameTestInfo> ending,
                              boolean shouldFail, int dy, List<String> problems) {
        ServerLevel level = helper.getLevel();
        GameTestInfo info = handMade(level, timeout);
        GameTestHelper fake = new GameTestHelper(info);
        // in this (real) test's column, above its 12-high template
        BlockPos spot = helper.absolutePos(new BlockPos(8, dy, 8));
        BlockState before = level.getBlockState(spot);
        TestCleanup.blocks(fake).set(spot, Blocks.GLASS.defaultBlockState());
        List<Entity> made = new ArrayList<>();
        TestCleanup.entities(fake, made);
        BlockPos stand = helper.absolutePos(new BlockPos(8, 2, 8));
        Mob m = EntityType.PIG.create(level);
        if (m == null) {
            problems.add(how + ": could not create a pig");
            return;
        }
        m.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        m.setNoAi(true);
        level.addFreshEntity(m);
        made.add(m);
        boolean flagBefore = UnitServerEvents.rtsPathfinding;
        TestCleanup.rtsPathfinding(fake);
        if (!UnitServerEvents.rtsPathfinding)
            problems.add(how + ": the hold did not switch rtsPathfinding on");
        int[] order = { 0 };
        int[] steps = new int[2];
        TestCleanup.atEnd(fake, () -> steps[0] = ++order[0]);
        TestCleanup.atEnd(fake, () -> steps[1] = ++order[0]);
        ending.accept(info);
        info.tick();
        if (!info.isDone())
            problems.add(how + ": the hand-made test did not end in one tick");
        else if (info.hasFailed() != shouldFail)
            problems.add(how + ": the hand-made test " + (info.hasFailed() ? "failed (" + info.getError() + ")" : "passed"));
        if (!m.isRemoved())
            problems.add(how + ": its unit is still alive");
        if (!level.getBlockState(spot).equals(before))
            problems.add(how + ": its block above the arena is " + level.getBlockState(spot) + ", was " + before);
        if (UnitServerEvents.rtsPathfinding != flagBefore)
            problems.add(how + ": rtsPathfinding is still " + UnitServerEvents.rtsPathfinding);
        if (steps[1] != 1 || steps[0] != 2)
            problems.add(how + ": the steps ran as " + steps[1] + ", " + steps[0] + " (want the newest first: 1, 2)");
        // the same steps never run twice
        info.tick();
        if (order[0] != 2)
            problems.add(how + ": " + order[0] + " step runs, want 2");
        // nothing of it left, even if a check above failed
        if (!m.isRemoved())
            m.discard();
        if (!level.getBlockState(spot).equals(before))
            level.setBlock(spot, before, 3);
    }

    /**
     * A test no runner knows about: its function does nothing, its start tick is 0 (so on its first tick it is well
     * past its own start, and it never runs its function), and its stopwatch runs (GameTestRunner starts it; ending
     * a test stops it, which throws on a stopwatch that never ran). Found by type, like TestCleanup's own field.
     */
    private static GameTestInfo handMade(ServerLevel level, int timeout) {
        TestFunction fn = new TestFunction(BATCH, "test_cleanup_hand_made", ARENA, timeout, 0L, false, h -> { });
        GameTestInfo info = new GameTestInfo(fn, Rotation.NONE, level);
        try {
            for (Field f : GameTestInfo.class.getDeclaredFields())
                if (f.getType() == Stopwatch.class) {
                    f.setAccessible(true);
                    Stopwatch w = (Stopwatch) f.get(info);
                    if (!w.isRunning())
                        w.start();
                    return info;
                }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot start the hand-made test's stopwatch", e);
        }
        throw new IllegalStateException("GameTestInfo has no Stopwatch field");
    }
}
