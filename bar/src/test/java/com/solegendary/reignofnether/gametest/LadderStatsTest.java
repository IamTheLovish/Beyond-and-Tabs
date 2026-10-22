package com.solegendary.reignofnether.gametest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The ladder's pace arithmetic (LadderStats), on made-up matches - no Minecraft needed. */
class LadderStatsTest {

    private static LadderStats match() {
        LadderStats s = new LadderStats();
        // two minutes of samples every second: side 0 holds 1000 metal of army, side 1 holds 800
        for (int t = 0; t <= 120 * 20; t += 20)
            s.sample(t, new int[] { 1000, 800 }, new int[] { 30, 25 });
        return s;
    }

    @Test
    void aBigLossIsADecisiveFightAndASmallOneIsNot() {
        LadderStats s = match();
        s.death(new LadderStats.Death(40 * 20, 1, 200, true, true, 0));   // 25% of 800: not decisive
        assertTrue(s.decisiveFights().isEmpty());
        s.death(new LadderStats.Death(45 * 20, 1, 150, true, true, 0));   // 350 of 800 = 44%
        var fights = s.decisiveFights();
        assertEquals(1, fights.size());
        assertEquals(1, fights.get(0).loser());
        assertEquals(40 * 20, fights.get(0).tick());
    }

    @Test
    void deathsToNonEnemiesAndWorkersDoNotCount() {
        LadderStats s = match();
        s.death(new LadderStats.Death(40 * 20, 0, 900, true, false, 0));   // fell in a river
        s.death(new LadderStats.Death(41 * 20, 0, 900, false, true, 0));   // workers are not army
        assertTrue(s.decisiveFights().isEmpty());
        assertEquals(900, s.lostValue(0));
    }

    @Test
    void fightsDoNotOverlapAndGapsAreMeasuredBetweenTheirStarts() {
        LadderStats s = match();
        s.death(new LadderStats.Death(10 * 20, 0, 500, true, true, 0));
        s.death(new LadderStats.Death(20 * 20, 0, 100, true, true, 0));   // same fight
        s.death(new LadderStats.Death(80 * 20, 1, 400, true, true, 0));
        var fights = s.decisiveFights();
        assertEquals(2, fights.size());
        assertEquals(java.util.List.of(70L), s.fightGapsSeconds());
    }

    @Test
    void tinyArmiesAreNotFights() {
        LadderStats s = new LadderStats();
        for (int t = 0; t <= 60 * 20; t += 20)
            s.sample(t, new int[] { 100, 100 }, new int[] { 3, 3 });
        s.death(new LadderStats.Death(30 * 20, 0, 100, true, true, 0));
        assertTrue(s.decisiveFights().isEmpty());
    }

    @Test
    void medianLifetimeFightingShareAndAliveCounts() {
        LadderStats s = match();
        s.death(new LadderStats.Death(30 * 20, 0, 50, true, true, 10 * 20));    // 20 s
        s.death(new LadderStats.Death(50 * 20, 0, 50, true, true, 10 * 20));    // 40 s
        s.death(new LadderStats.Death(60 * 20, 0, 50, true, true, 0));          // 60 s
        assertEquals(40.0, s.medianCombatLifetime(0), 1e-9);
        assertEquals(-1.0, s.medianCombatLifetime(1), 1e-9);
        for (int t = 0; t < 30 * 20; t += 20)   // fighting during the first 30 s: 3 of 12 ten-second buckets
            s.fighting(t);
        assertEquals(3 / 12.0, s.fightingShare(), 1e-9);
        assertEquals(30, s.unitsAliveAt(0, 1));
        assertEquals(-1, s.unitsAliveAt(0, 10));   // the match ended at 2:00
    }

    @Test
    void producedPerMinute() {
        LadderStats s = match();
        for (int i = 0; i < 10; i++)
            s.produced(0, i < 6);
        assertEquals(5.0, s.producedPerMinute(0, false), 1e-9);
        assertEquals(3.0, s.producedPerMinute(0, true), 1e-9);
        assertEquals("2:05", LadderStats.clock(125 * 20));
    }
}
