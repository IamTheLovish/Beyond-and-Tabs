package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The idle scheduler (design/living_army.md, section 2): one fidget at a time (grip, glance, shoulder roll, look
 * around), seeded per unit, gaps of 80-240 ticks, quiet for 30 ticks after a stop and while moving or fighting; a
 * weight shift on its own slower track. A block of units never fidgets as one.
 */
class IdleTest {

    /** Runs one unit standing still from tick 0; returns {start tick, kind} of every fidget that started. */
    private static List<int[]> run(int id, int ticks) {
        float[] s = new float[AnimState.STRIDE];
        List<int[]> starts = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            int k = Idle.step(s, 0, id, t, false, false);
            if (k != Idle.NONE)
                starts.add(new int[] {t, k});
        }
        return starts;
    }

    @Test
    void deterministicPerUnit() {
        List<int[]> a = run(17, 3000), b = run(17, 3000);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i)[0], b.get(i)[0]);
            assertEquals(a.get(i)[1], b.get(i)[1]);
        }
    }

    @Test
    void neverTheSameFidgetTwiceInARow() {
        List<int[]> a = run(5, 6000);
        assertTrue(a.size() >= 20, "enough fidgets to judge: " + a.size());
        for (int i = 1; i < a.size(); i++)
            assertNotEquals(a.get(i - 1)[1], a.get(i)[1], "fidget " + i);
    }

    @Test
    void gapsStayInTheirBand() {
        for (int id = 0; id < 10; id++) {
            List<int[]> a = run(id, 6000);
            for (int i = 1; i < a.size(); i++) {
                int gap = a.get(i)[0] - a.get(i - 1)[0];
                assertTrue(gap >= Idle.GAP_MIN && gap <= Idle.GAP_MAX + Idle.LEN_MAX, "id " + id + " gap " + gap);
            }
        }
    }

    @Test
    void quietWhileMovingFightingAndJustAfterAStop() {
        float[] s = new float[AnimState.STRIDE];
        for (int t = 0; t < 600; t++)
            assertEquals(Idle.NONE, Idle.step(s, 0, 3, t, true, false), "moving, tick " + t);
        for (int t = 600; t < 1200; t++)
            assertEquals(Idle.NONE, Idle.step(s, 0, 3, t, false, true), "fighting, tick " + t);
        for (int t = 1200; t < 1200 + Idle.QUIET_AFTER_STOP; t++)
            assertEquals(Idle.NONE, Idle.step(s, 0, 3, t, false, false), "just stopped, tick " + t);
    }

    @Test
    void neighboursRarelyStartTogether() {
        int ids = 50, ticks = 4800, together = 0, total = 0;
        List<Set<Integer>> startTicks = new ArrayList<>();
        for (int id = 0; id < ids; id++) {
            Set<Integer> set = new HashSet<>();
            for (int[] st : run(id, ticks))
                set.add(st[0]);
            startTicks.add(set);
            total += set.size();
        }
        for (int id = 1; id < ids; id++)
            for (int t : startTicks.get(id))
                if (startTicks.get(id - 1).contains(t))
                    together++;
        assertTrue(together < total * 0.02f, "neighbours together " + together + " of " + total);
    }

    @Test
    void weightShiftsSideToSide() {
        float[] s = new float[AnimState.STRIDE];
        Set<Float> sides = new HashSet<>();
        for (int t = 0; t < 2400; t++) {
            Idle.step(s, 0, 9, t, false, false);
            float w = Idle.weight(s, 0, t);
            assertTrue(w >= -1f && w <= 1f);
            if (Math.abs(w) > 0.9f)
                sides.add(Math.signum(w));
        }
        assertEquals(2, sides.size(), "both legs take the weight in turn");
    }
}
