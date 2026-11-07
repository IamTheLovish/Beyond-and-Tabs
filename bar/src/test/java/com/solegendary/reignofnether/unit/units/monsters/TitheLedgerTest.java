package com.solegendary.reignofnether.unit.units.monsters;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Tithe Binder's ledger (design sheet 7.1 and 5.3-5.7, lovish's brief Nov 5 2026, with the lead's corrections): the
 * weapon count and how kills, assists, summons and workers move it, one bind per 20 ticks, the cap of 10, the restless
 * decay of weapons 6-10, the volley's damage plan and Grave Hook's drag-or-root rule. Written before the rules (the
 * ledger is a stub on the tests-first commit). Every number is the sheet's, written here rather than read from the
 * class, so a changed constant fails a test too.
 */
class TitheLedgerTest {

    /** A ledger whose binds have all landed: credit then tick until nothing is queued. */
    private static long settle(TitheLedger l, long now) {
        for (int i = 0; i < 400 && l.queued() > 0; i++)
            l.tick(now += 20);
        return now;
    }

    // ---------------------------------------------------------------- the count

    @Test
    void aFreshBinderCarriesOneWeaponAndNoHalf() {
        TitheLedger l = new TitheLedger();
        assertEquals(1, l.weapons());
        assertFalse(l.hasHalf());
        assertEquals(0, l.queued());
        assertEquals(0, l.tick(0));
        assertEquals(1, l.weapons());
    }

    @Test
    void aFullCreditBindsOneWeaponOnTheNextTick() {
        TitheLedger l = new TitheLedger();
        l.credit(2, 1000);
        assertEquals(1, l.weapons(), "the bind lands on a tick, not inside the credit");
        assertEquals(1, l.queued());
        assertEquals(1, l.tick(1000));
        assertEquals(2, l.weapons());
        assertEquals(0, l.queued());
        assertFalse(l.hasHalf());
        assertEquals(0, l.tick(1001));
        assertEquals(2, l.weapons());
    }

    @Test
    void twoHalvesMakeOneWeaponAndAHalfWaitsAsAGhost() {
        TitheLedger l = new TitheLedger();
        l.credit(1, 100);
        assertTrue(l.hasHalf());
        assertEquals(0, l.queued());
        assertEquals(0, l.tick(101));
        assertEquals(1, l.weapons());
        l.credit(1, 200);
        assertFalse(l.hasHalf());
        assertEquals(1, l.queued());
        assertEquals(1, l.tick(200));
        assertEquals(2, l.weapons());
    }

    @Test
    void aHalfThenAFullLeavesAHalfOver() {
        TitheLedger l = new TitheLedger();
        l.credit(1, 0);
        l.credit(2, 0);
        assertEquals(1, l.queued());
        assertTrue(l.hasHalf(), "1.5 binds: one queued, half waiting");
        settle(l, 0);
        assertEquals(2, l.weapons());
        assertTrue(l.hasHalf());
    }

    @Test
    void creditsOfZeroOrLessDoNothing() {
        TitheLedger l = new TitheLedger();
        l.credit(0, 0);
        l.credit(-3, 0);
        assertEquals(0, l.queued());
        assertFalse(l.hasHalf());
    }

    @Test
    void atMostOneBindLandsPerTwentyTicks() {
        TitheLedger l = new TitheLedger();
        for (int i = 0; i < 5; i++)
            l.credit(2, 500);
        assertEquals(5, l.queued());
        int[] landed = new int[5];
        int n = 0;
        for (long t = 500; t <= 600; t++) {
            int ev = l.tick(t);
            if (ev == 1)
                landed[n++] = (int) (t - 500);
            assertTrue(ev >= 0, "nothing leaves while binds are queued");
        }
        assertEquals(5, n);
        assertEquals(6, l.weapons());
        assertEquals(0, landed[0]);
        for (int i = 1; i < 5; i++)
            assertEquals(20, landed[i] - landed[i - 1], "bind " + (i + 1) + " came " + (landed[i] - landed[i - 1]) + " ticks after the one before");
    }

    @Test
    void aBindNineteenTicksAfterTheLastWaits() {
        TitheLedger l = new TitheLedger();
        l.credit(2, 0);
        assertEquals(1, l.tick(0));
        l.credit(2, 5);
        assertEquals(0, l.tick(19));
        assertEquals(2, l.weapons());
        assertEquals(1, l.tick(20));
        assertEquals(3, l.weapons());
    }

    @Test
    void theCountStopsAtTenAndExtraCreditsAreDropped() {
        TitheLedger l = new TitheLedger();
        for (int i = 0; i < 15; i++)
            l.credit(2, 0);
        assertEquals(9, l.queued(), "1 + 9 queued = 10; the other 5 are dropped, not kept for later");
        long now = settle(l, 0);
        assertEquals(10, l.weapons());
        l.credit(2, now);
        l.credit(1, now);
        assertEquals(0, l.queued());
        assertFalse(l.hasHalf(), "no ghost slot past the tenth");
        assertEquals(0, l.tick(now + 20));
        assertEquals(10, l.weapons());
    }

    @Test
    void aHalfAtNineAndAHalfMakeTen() {
        TitheLedger l = new TitheLedger();
        l.restore(9, true, 0);
        assertEquals(9, l.weapons());
        assertTrue(l.hasHalf());
        l.credit(1, 0);
        settle(l, 0);
        assertEquals(10, l.weapons());
        assertFalse(l.hasHalf());
    }

    @Test
    void restoreClampsAndDropsAHalfAtTen() {
        TitheLedger l = new TitheLedger();
        l.restore(0, false, 0);
        assertEquals(1, l.weapons());
        l.restore(14, true, 0);
        assertEquals(10, l.weapons());
        assertFalse(l.hasHalf());
        l.restore(7, true, 0);
        assertEquals(7, l.weapons());
        assertTrue(l.hasHalf());
        assertEquals(0, l.queued());
    }

    // ---------------------------------------------------------------- the kill rule

    @Test
    void aLastHitIsAFullBindAWorkerOnlyHalf() {
        assertEquals(2, TitheLedger.bindHalves(true, false, false, true));
        assertEquals(2, TitheLedger.bindHalves(true, false, false, false), "a last hit needs no window");
        assertEquals(1, TitheLedger.bindHalves(true, true, false, true));
    }

    @Test
    void anAssistInTheWindowIsHalfOutsideItNothing() {
        assertEquals(1, TitheLedger.bindHalves(false, false, false, true));
        assertEquals(0, TitheLedger.bindHalves(false, false, false, false));
        assertEquals(1, TitheLedger.bindHalves(false, true, false, true), "a worker assist is half too, never a quarter");
    }

    @Test
    void aSummonGivesNothingEvenOnALastHit() {
        assertEquals(0, TitheLedger.bindHalves(true, false, true, true));
        assertEquals(0, TitheLedger.bindHalves(false, false, true, true));
        assertEquals(0, TitheLedger.bindHalves(true, true, true, true));
    }

    @Test
    void theAssistWindowIsThreeSeconds() {
        assertTrue(TitheLedger.inAssistWindow(1000, 1000));
        assertTrue(TitheLedger.inAssistWindow(1000, 1060));
        assertFalse(TitheLedger.inAssistWindow(1000, 1061));
        assertFalse(TitheLedger.inAssistWindow(1000, 999), "a hit after the death is no assist");
    }

    // ---------------------------------------------------------------- restless weapons

    @Test
    void weaponsSixToTenLeaveOneByOneDownToFive() {
        TitheLedger l = new TitheLedger();
        l.restore(10, false, 1000);
        assertEquals(0, l.tick(1000 + 399));
        assertEquals(10, l.weapons());
        assertEquals(-1, l.tick(1000 + 400), "the first leaves at 20 s");
        assertEquals(9, l.weapons());
        assertEquals(0, l.tick(1000 + 400), "one per due time, not one per tick");
        assertEquals(0, l.tick(1000 + 699));
        assertEquals(-1, l.tick(1000 + 700), "the next 15 s later");
        assertEquals(8, l.weapons());
        assertEquals(-1, l.tick(1000 + 1000));
        assertEquals(-1, l.tick(1000 + 1300));
        assertEquals(-1, l.tick(1000 + 1600));
        assertEquals(5, l.weapons());
        assertEquals(0, l.tick(1000 + 1900));
        assertEquals(0, l.tick(1000 + 100_000));
        assertEquals(5, l.weapons(), "1-5 are permanent while he lives");
    }

    @Test
    void fiveOrFewerNeverLeave() {
        TitheLedger l = new TitheLedger();
        l.restore(5, false, 0);
        for (long t = 0; t <= 20_000; t += 50)
            assertEquals(0, l.tick(t));
        assertEquals(5, l.weapons());
    }

    @Test
    void damageDealtRestartsTheRestlessCount() {
        TitheLedger l = new TitheLedger();
        l.restore(8, false, 0);
        assertEquals(-1, l.tick(400));
        assertEquals(7, l.weapons());
        l.onDamageDealt(500);
        assertEquals(0, l.tick(800), "the count restarted at 500: 20 s from there is 900");
        assertEquals(0, l.tick(899));
        assertEquals(-1, l.tick(900));
        assertEquals(6, l.weapons());
        assertEquals(0, l.tick(1199));
        assertEquals(-1, l.tick(1200));
        assertEquals(5, l.weapons());
    }

    @Test
    void aCreditAtTheCapRefreshesTheRestlessCount() {
        TitheLedger l = new TitheLedger();
        l.restore(10, false, 0);
        l.credit(2, 350);
        assertEquals(0, l.queued());
        assertEquals(0, l.tick(400), "refreshed at 350: nothing is due before 750");
        assertEquals(-1, l.tick(750));
        assertEquals(9, l.weapons());
    }

    // ---------------------------------------------------------------- the volley

    @Test
    void aVolleyThrowsEveryDockedWeapon() {
        for (int n = 1; n <= 10; n++)
            assertEquals(n, TitheLedger.volleySize(n));
        assertEquals(1, TitheLedger.volleySize(0));
        assertEquals(10, TitheLedger.volleySize(12));
    }

    @Test
    void theHookHitsForSixEachBoundWeaponForThree() {
        assertEquals(6f, TitheLedger.weaponDamage(0), 0f);
        for (int k = 1; k < 10; k++)
            assertEquals(3f, TitheLedger.weaponDamage(k), 0f);
        for (int n = 1; n <= 10; n++)
            assertEquals(6f + 3f * (n - 1), TitheLedger.volleyDamage(n), 0f, "a volley at " + n);
        assertEquals(33f, TitheLedger.volleyDamage(10), 0f);
    }

    @Test
    void weaponsLeaveEveryTwoTicksAndFlySixPlusPointFourPerBlock() {
        assertEquals(6, TitheLedger.flightTicks(0));
        assertEquals(10, TitheLedger.flightTicks(10));
        assertEquals(8, TitheLedger.flightTicks(6));
        assertEquals(0 + 10, TitheLedger.hitDelay(0, 10));
        assertEquals(18 + 10, TitheLedger.hitDelay(9, 10));
        for (int k = 1; k < 10; k++)
            assertEquals(2, TitheLedger.hitDelay(k, 7) - TitheLedger.hitDelay(k - 1, 7));
    }

    @Test
    void theVolleyFitsItsThreeSecondCycle() {
        // N = 10 at the full 10-block range: the last weapon lands at t14 + 18 + 10 = t42 (sheet 5.3's timing check)
        assertEquals(42, TitheLedger.WINDUP_TICKS + TitheLedger.hitDelay(9, 10));
        assertTrue(TitheLedger.WINDUP_TICKS + TitheLedger.hitDelay(9, 10) < TitheLedger.VOLLEY_CYCLE_TICKS);
    }

    @Test
    void buildingsTakeHalf() {
        assertEquals(3f, TitheLedger.buildingDamage(6f), 0f);
        assertEquals(1.5f, TitheLedger.buildingDamage(3f), 0f);
        assertEquals(16.5f, TitheLedger.buildingDamage(TitheLedger.volleyDamage(10)), 0f);
    }

    // ---------------------------------------------------------------- Grave Hook

    @Test
    void aLightTargetIsDraggedAHeavyOneRooted() {
        assertEquals(TitheLedger.Hook.DRAG, TitheLedger.hookOutcome(75, 0.6));
        assertEquals(TitheLedger.Hook.DRAG, TitheLedger.hookOutcome(149.9, 1.39));
        assertEquals(TitheLedger.Hook.ROOT, TitheLedger.hookOutcome(150, 0.6), "150 max health is heavy");
        assertEquals(TitheLedger.Hook.ROOT, TitheLedger.hookOutcome(40, 1.4), "a 1.4-block hitbox is heavy");
        assertEquals(TitheLedger.Hook.ROOT, TitheLedger.hookOutcome(260, 2.0));
    }

    @Test
    void theDragIsFourBlocksButStopsShortOfTheBinder() {
        assertEquals(4.0, TitheLedger.dragDistance(8), 1e-9);
        assertEquals(4.0, TitheLedger.dragDistance(12), 1e-9);
        assertEquals(3.5, TitheLedger.dragDistance(5), 1e-9);
        assertEquals(0.0, TitheLedger.dragDistance(1.0), 1e-9);
    }

    @Test
    void theDragEasesInAndOutOverSixteenTicks() {
        assertEquals(0.0, TitheLedger.dragProgress(0), 1e-9);
        assertEquals(0.5, TitheLedger.dragProgress(8), 1e-9);
        assertEquals(1.0, TitheLedger.dragProgress(16), 1e-9);
        assertEquals(1.0, TitheLedger.dragProgress(40), 1e-9);
        double prev = 0;
        for (int t = 1; t <= 16; t++) {
            double p = TitheLedger.dragProgress(t);
            assertTrue(p > prev, "monotone at " + t);
            prev = p;
        }
        assertTrue(TitheLedger.dragProgress(1) < 1.0 / 16, "slow start");
    }
}
