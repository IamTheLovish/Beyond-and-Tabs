package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;

import java.lang.reflect.Field;

/**
 * The server-wide rtsPathfinding flag, switched on for the tests of a batch that want the match pathfinder (a real
 * match turns it on: ArenaPathing / WorldBorderServerEvents) and back to what it was after the LAST of them. The tests
 * of one batch run side by side, so a test that put the flag back when it finished used to turn it off under its
 * neighbours still running; batches run one at a time, so nothing outside the batch sees it.
 * Every {@link #take()} is matched by exactly one {@link #release()}. A test takes it with {@link #takeUntilEnd}, which
 * also gives it back when the test ends however it ends (a timeout, a throw out of a callback) - Mode A found the
 * flag left on by a test that never reached its own finish. (PR 352's TestCleanup.rtsPathfinding does the same for
 * every test; these two switch to it when it lands.)
 */
final class RtsPathingLease {
    private RtsPathingLease() { }

    private static int holders = 0;
    private static boolean saved = false;

    static void take() {
        if (holders++ == 0)
            saved = UnitServerEvents.rtsPathfinding;
        UnitServerEvents.rtsPathfinding = true;
    }

    static void release() {
        if (holders > 0 && --holders == 0)
            UnitServerEvents.rtsPathfinding = saved;
    }

    /**
     * {@link #take()} for this test, given back when the test ends (passed, failed or timed out). Returns the release,
     * run-once: call it early where the test tidies up before it ends; it does nothing the second time.
     */
    static Runnable takeUntilEnd(GameTestHelper helper) {
        take();
        boolean[] given = { false };
        Runnable giveBack = () -> {
            if (given[0])
                return;
            given[0] = true;
            release();
        };
        info(helper).addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo i) { }

            @Override
            public void testPassed(GameTestInfo i) {
                giveBack.run();
            }

            @Override
            public void testFailed(GameTestInfo i) {
                giveBack.run();
            }
        });
        return giveBack;
    }

    /** The helper's GameTestInfo, found by its type (not its name in this mapping). */
    private static GameTestInfo info(GameTestHelper helper) {
        try {
            for (Field f : GameTestHelper.class.getDeclaredFields())
                if (f.getType() == GameTestInfo.class) {
                    f.setAccessible(true);
                    return (GameTestInfo) f.get(helper);
                }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read the test's GameTestInfo", e);
        }
        throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
    }
}
