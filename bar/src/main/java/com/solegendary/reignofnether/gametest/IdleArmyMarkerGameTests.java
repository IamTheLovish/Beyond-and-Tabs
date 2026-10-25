package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.IdleTracker;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The idle-army marker's rule (unit/IdleTracker, drawn by StrategicViewClientEvents, counted by hud/IdleArmy): a unit
 * is idle after standing still for IDLE_AFTER_MS; moving, or being busy, starts the clock again; units not seen in a
 * pass are forgotten. Fake clock, no world changes.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class IdleArmyMarkerGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void unit_is_idle_only_after_standing_still_long_enough(GameTestHelper helper) {
        IdleTracker t = new IdleTracker();
        long s = IdleTracker.IDLE_AFTER_MS;
        t.beginPass();
        boolean a = t.observe(1, 10, 10, false, 0);
        t.endPass();
        t.beginPass();
        boolean b = t.observe(1, 10.02, 10, false, s - 500);   // jitter below the move threshold
        t.endPass();
        t.beginPass();
        boolean c = t.observe(1, 10.02, 10, false, s);
        t.endPass();
        if (a || b)
            helper.fail("a unit must not be idle before " + s + " ms of standing still");
        else if (!c || !t.isIdle(1))
            helper.fail("a unit standing still for " + s + " ms must be idle");
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void moving_or_fighting_restarts_the_idle_clock(GameTestHelper helper) {
        IdleTracker t = new IdleTracker();
        long s = IdleTracker.IDLE_AFTER_MS;
        t.beginPass();
        t.observe(1, 0, 0, false, 0);
        t.observe(2, 0, 0, false, 0);
        t.endPass();
        t.beginPass();
        t.observe(1, 5, 0, false, s);      // walked 5 blocks
        t.observe(2, 0, 0, true, s);       // swinging / being hit
        t.endPass();
        if (t.isIdle(1) || t.isIdle(2))
            helper.fail("a unit that moved or was busy at " + s + " ms must not be idle yet");
        else {
            t.beginPass();
            t.observe(1, 5, 0, false, 2 * s);
            t.endPass();   // unit 2 not seen this pass
            if (!t.isIdle(1) || t.idleCount() != 1)
                helper.fail("unit 1 stood still " + s + " ms since it moved: idle, count 1; got count " + t.idleCount());
            else if (t.isIdle(2))
                helper.fail("a unit that was not seen in the pass is forgotten, not idle");
            else
                helper.succeed();
        }
    }
}
