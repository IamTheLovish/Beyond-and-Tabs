package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The [Goals] line's accounting (GoalTimer): one row per goal class, its time per tick split by kind of call. */
class GoalTimerTest {

    @Test
    void rowsAreGoalClassesHeaviestFirstPerTickSplitByKind() {
        GoalTimer t = new GoalTimer();
        int look = t.id("RandomLookAroundUnitGoal");
        int melee = t.id("MeleeAttackUnitGoal");
        for (int i = 0; i < 10; i++) {
            t.add(melee, GoalTimer.TICK, 2_000_000);
            t.add(melee, GoalTimer.CAN_USE, 500_000);
            t.add(look, GoalTimer.CAN_USE, 100_000);
            t.add(look, GoalTimer.CAN_USE, 100_000);
            t.tickEnd();
        }
        assertEquals("[Goals] 10 ticks, 2.70 ms/tick in 2 goal classes: "
                + "MeleeAttackUnitGoal 2.50 ms (tick 2.00, canUse 0.50; 2 calls/t, 1250.0 us/call) | "
                + "RandomLookAroundUnitGoal 0.20 ms (canUse 0.20; 2 calls/t, 100.0 us/call)", t.line("[Goals]"));
    }

    @Test
    void aClassIsOneRowWhateverUnitRunsIt() {
        GoalTimer t = new GoalTimer();
        assertEquals(t.id("FloatGoal"), t.id("FloatGoal"));
        t.add(t.id("FloatGoal"), GoalTimer.CAN_USE, 1_000_000);
        t.add(t.id("FloatGoal"), GoalTimer.CAN_USE, 1_000_000);
        assertEquals(2, t.calls(t.id("FloatGoal")));
        assertEquals(2, t.calls(t.id("FloatGoal"), GoalTimer.CAN_USE));
    }

    @Test
    void beyondTheTopRowsAndUnderTheFloorAreCounted() {
        GoalTimer t = new GoalTimer();
        for (int i = 0; i < 40; i++)   // more classes than the first arrays hold
            t.add(t.id("Goal" + i), GoalTimer.TICK, (40 - i) * 1_000_000L);
        t.add(t.id("Tiny"), GoalTimer.TICK, 1_000);
        t.tickEnd();
        String line = t.line("[Goals]");
        assertTrue(line.startsWith("[Goals] 1 ticks, 820.00 ms/tick in 41 goal classes: Goal0 40.00 ms (tick 40.00;"), line);
        assertTrue(line.contains("| Goal11 29.00 ms"), line);
        assertTrue(!line.contains("Goal12 "), line);
        assertTrue(line.endsWith(" (+29 smaller)"), line);
    }

    @Test
    void aNewWindowKeepsTheClassesAndDropsTheTotals() {
        GoalTimer t = new GoalTimer();
        int a = t.id("A");
        t.add(a, GoalTimer.TICK, 5_000_000);
        t.tickEnd();
        t.reset();
        assertNull(t.line("[Goals]"), "no tick closed in the new window");
        t.tickEnd();
        assertEquals("[Goals] 1 ticks, 0.00 ms/tick in 0 goal classes: nothing timed", t.line("[Goals]"));
        assertEquals(a, t.id("A"));
    }

    @Test
    void badCallsAreIgnored() {
        GoalTimer t = new GoalTimer();
        int a = t.id("A");
        t.add(a, GoalTimer.TICK, -5);
        t.add(a + 1, GoalTimer.TICK, 5);
        t.add(a, GoalTimer.KINDS, 5);
        assertEquals(0, t.calls(a));
    }

    @Test
    void aGoalClassPrintsWithoutItsPackage() {
        assertEquals("GoalTimerTest", GoalTimes.name(GoalTimerTest.class));
        assertEquals("Map$Entry", GoalTimes.name(Map.Entry.class));
    }
}
