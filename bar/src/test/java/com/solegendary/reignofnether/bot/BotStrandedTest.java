package com.solegendary.reignofnether.bot;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BotStranded's clock: a worker that stands on one spot under build orders, give-up after give-up, is stranded; one that
 * walks, builds or only waits idle is not (bots_reach_tier_2, flake-stress 41168069584: seven of the easy bot's ten
 * workers stood at y=63 for twelve minutes and its T2 lab rose with one builder).
 */
class BotStrandedTest {

    /** Samples one worker once a second (the bot's think) for {@code seconds}, from {@code t0}; the last answer. */
    private static boolean stand(BotStranded s, int id, double x, double z, boolean ordered, boolean building, long t0, int seconds) {
        boolean out = false;
        for (int i = 0; i <= seconds; i++)
            out = s.sample(id, x, z, ordered, building, t0 + i * 20L);
        return out;
    }

    @Test
    void aWorkerStandingUnderOrdersIsStrandedAfterTheClock() {
        BotStranded s = new BotStranded();
        assertFalse(stand(s, 1, 100, 100, true, false, 0, 39));
        assertTrue(stand(s, 1, 100.5, 100.5, true, false, 40 * 20, 1));
    }

    @Test
    void theGiveUpsBetweenOrdersDoNotRestartTheClock() {
        // 15 s ordered, 1 s idle after the give-up, ordered again: the ordered time adds up across the gaps
        BotStranded s = new BotStranded();
        long t = 0;
        boolean stranded = false;
        for (int round = 0; round < 4; round++) {
            stranded = stand(s, 1, 100, 100, true, false, t, 15);
            t += 16 * 20;
            stand(s, 1, 100, 100, false, false, t, 0);
            t += 20;
        }
        assertTrue(stranded);
    }

    @Test
    void anIdleWorkerWaitingAtHomeIsNeverStranded() {
        BotStranded s = new BotStranded();
        stand(s, 1, 100, 100, true, false, 0, 5);   // ordered, then the site was cancelled
        assertFalse(stand(s, 1, 100, 100, false, false, 6 * 20, 300));
    }

    @Test
    void walkingOrBuildingClearsIt() {
        BotStranded s = new BotStranded();
        assertTrue(stand(s, 1, 100, 100, true, false, 0, 45));
        assertFalse(s.sample(1, 100 + BotStranded.MOVED + 1, 100, true, false, 46 * 20));
        assertFalse(stand(s, 1, 104, 100, true, false, 47 * 20, 30));

        assertTrue(stand(s, 2, 50, 50, true, false, 0, 45));
        assertFalse(s.sample(2, 50, 50, true, true, 46 * 20));   // in range of its site and building
    }

    @Test
    void aStrandedWorkerIsWalkedHomeEveryRescueInterval() {
        BotStranded s = new BotStranded();
        stand(s, 1, 100, 100, true, false, 0, 45);
        long t = 45 * 20;
        assertTrue(s.rescueDue(1, t));
        assertFalse(s.rescueDue(1, t + BotStranded.RESCUE_EVERY_TICKS - 1));
        assertTrue(s.rescueDue(1, t + BotStranded.RESCUE_EVERY_TICKS));
    }

    @Test
    void goneWorkersAreForgotten() {
        BotStranded s = new BotStranded();
        stand(s, 1, 100, 100, true, false, 0, 45);
        s.retain(Set.of(2));
        assertFalse(s.rescueDue(1, 10_000));
        assertFalse(s.sample(1, 100, 100, true, false, 10_000));
    }
}
