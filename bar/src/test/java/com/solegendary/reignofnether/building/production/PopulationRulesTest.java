package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.production.PopulationRules.Verdict;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may queue a unit and what happens to it at the army cap - the rule the client's card, the server's queue and
 * the graveyards all have to agree on. Written from the intended rules, before the predicates were pulled out of
 * ProductionItem / GraveyardUnitProductionItem (lovish, Oct 22: "write the tests first, so the code can't cheat").
 * <ul>
 *   <li>A lab queues a unit while current + cost fits the supply (BAR: the supply IS the cap); a free unit (cost 0)
 *       is never blocked.</li>
 *   <li>An overflowing graveyard (it has skull slots) works the same while the army has room: the unit walks out.
 *       Only AT the cap does it bank the finished unit as a skull, while the bank has room for what is queued
 *       (playtest Oct 22 19:20, PR #167: "the death buildings werent making any units and said graveyard is full" -
 *       a bank that took every unit filled with skulls that never walked out).</li>
 * </ul>
 */
class PopulationRulesTest {

    private static final int UNLIMITED = 100_000;   // ResourceCosts.UNLIMITED_POPULATION: no cap by default, as in BAR

    private static void check(Verdict expected, int current, int supply, int cost, int committed, int maxSkulls) {
        assertEquals(expected, PopulationRules.verdict(current, supply, cost, committed, maxSkulls),
            "current " + current + " supply " + supply + " cost " + cost + " skulls " + committed + "/" + maxSkulls);
    }

    // ---------------------------------------------------------------- an ordinary lab (no skull bank)

    @Test
    void aLabQueuesWhileTheUnitFitsTheSupplyAndNotOneUnitMore() {
        int[][] table = {
            // current, supply, cost, fits?
            { 0, 10, 1, 1 }, { 8, 10, 1, 1 }, { 9, 10, 1, 1 }, { 9, 10, 2, 0 },
            { 10, 10, 1, 0 }, { 0, 0, 1, 0 }, { 0, 10, 10, 1 }, { 0, 10, 11, 0 },
            { 50_000, UNLIMITED, 5, 1 }, { 99_998, UNLIMITED, 2, 1 }, { 99_999, UNLIMITED, 2, 0 },
        };
        for (int[] r : table)
            check(r[3] == 1 ? Verdict.WALKS_OUT : Verdict.ARMY_FULL, r[0], r[1], r[2], 0, 0);
    }

    @Test
    void aFreeUnitIsNeverBlockedEvenOverTheCap() {
        check(Verdict.WALKS_OUT, 10, 10, 0, 0, 0);
        check(Verdict.WALKS_OUT, 11, 10, 0, 0, 0);
        check(Verdict.WALKS_OUT, 500, 0, 0, 0, 0);
        check(Verdict.WALKS_OUT, 10, 10, 0, 10, 10);   // a full skull bank does not stop a free unit either
    }

    @Test
    void aLabAtTheCapHasNowhereToBankSoItRefuses() {
        check(Verdict.ARMY_FULL, 10, 10, 1, 0, 0);
        check(Verdict.ARMY_FULL, 10, 10, 1, 5, 0);      // skulls committed mean nothing without slots
        check(Verdict.ARMY_FULL, 10, 10, 1, 0, -3);     // a nonsense bank size is no bank
    }

    // ---------------------------------------------------------------- an overflowing graveyard

    @Test
    void whileTheArmyHasRoomTheUnitWalksOutWhateverTheBankHolds() {
        check(Verdict.WALKS_OUT, 5, 10, 1, 0, 10);
        check(Verdict.WALKS_OUT, 9, 10, 1, 0, 10);      // exactly fills the cap
        check(Verdict.WALKS_OUT, 9, 10, 1, 10, 10);     // bank full, army not: still walks out (the PR #167 fix)
        check(Verdict.WALKS_OUT, 0, 10, 1, 99, 10);
    }

    @Test
    void atTheCapAUnitIsBankedAsASkullWhileThereIsSlotRoomForIt() {
        check(Verdict.BANKS_AS_SKULL, 10, 10, 1, 0, 10);
        check(Verdict.BANKS_AS_SKULL, 10, 10, 1, 9, 10);    // the last free slot
        check(Verdict.BANKS_AS_SKULL, 10, 10, 2, 8, 10);    // a 2-pop unit takes two slots
        check(Verdict.BANKS_AS_SKULL, 9, 10, 2, 0, 10);     // does not fit the army by one: the bank takes it
    }

    @Test
    void whenTheBankIsFullTooTheGraveyardIsFull() {
        check(Verdict.BANK_FULL, 10, 10, 1, 10, 10);
        check(Verdict.BANK_FULL, 10, 10, 2, 9, 10);     // one slot left, two needed
        check(Verdict.BANK_FULL, 10, 10, 1, 12, 10);    // over-committed (units queued before a slot was lost)
        check(Verdict.BANK_FULL, 11, 10, 1, 10, 10);    // army over the cap too (supply dropped)
    }

    @Test
    void onlyTheTwoAcceptingVerdictsLetAUnitIntoTheQueue() {
        assertTrue(Verdict.WALKS_OUT.canQueue());
        assertTrue(Verdict.BANKS_AS_SKULL.canQueue());
        assertFalse(Verdict.ARMY_FULL.canQueue());
        assertFalse(Verdict.BANK_FULL.canQueue());
    }

    // ---------------------------------------------------------------- the finished unit: walk out or bank

    @Test
    void aFinishedUnitIsBankedOnlyByAnOverflowingGraveyardAtTheCap() {
        assertFalse(PopulationRules.banksOnCompletion(5, 10, 1, 10), "the army has room: it walks out");
        assertTrue(PopulationRules.banksOnCompletion(10, 10, 1, 10), "at the cap with slots: a skull");
        assertFalse(PopulationRules.banksOnCompletion(10, 10, 1, 0), "no slots: an ordinary lab never banks");
        assertFalse(PopulationRules.banksOnCompletion(10, 10, 0, 10), "a free unit always walks out");
        assertTrue(PopulationRules.banksOnCompletion(9, 10, 2, 4));
    }

    @Test
    void hasRoomIsTheSameQuestionTheLabsAsk() {
        assertTrue(PopulationRules.hasRoom(0, 10, 1));
        assertTrue(PopulationRules.hasRoom(9, 10, 1));
        assertFalse(PopulationRules.hasRoom(10, 10, 1));
        assertTrue(PopulationRules.hasRoom(10, 10, 0));
        assertTrue(PopulationRules.hasRoom(40, 10, 0));
    }

    // ---------------------------------------------------------------- production already under way

    @Test
    void productionPausesWhenTheArmyIsOverTheSupplyAndResumesWhenItIsNot() {
        // an ordinary lab: ticking is allowed while current <= supply (checked after a supply loss, not per unit)
        assertTrue(PopulationRules.keepsProducing(0, 10, 1, 0, 0));
        assertTrue(PopulationRules.keepsProducing(10, 10, 1, 0, 0), "exactly at the cap is not over it");
        assertFalse(PopulationRules.keepsProducing(11, 10, 1, 0, 0));
        assertTrue(PopulationRules.keepsProducing(11, 10, 0, 0, 0), "a free item never pauses");
    }

    @Test
    void anOverflowingGraveyardKeepsProducingOverTheCapOnlyWhileItsBankCanTakeWhatIsQueued() {
        assertTrue(PopulationRules.keepsProducing(11, 10, 1, 0, 10));
        assertTrue(PopulationRules.keepsProducing(11, 10, 1, 10, 10), "committed == slots: everything queued still fits");
        assertFalse(PopulationRules.keepsProducing(11, 10, 1, 11, 10), "one more queued than slots: pause");
        assertTrue(PopulationRules.keepsProducing(5, 10, 1, 50, 10), "army under the cap: units walk out, the bank is irrelevant");
        assertTrue(PopulationRules.keepsProducing(11, 10, 0, 50, 10));
    }

    // ---------------------------------------------------------------- the host's hard cap is its own question

    @Test
    void theHostsHardCapIsCheckedApartFromTheSupply() {
        assertTrue(PopulationRules.withinMax(0, 1, 50));
        assertTrue(PopulationRules.withinMax(49, 1, 50));
        assertFalse(PopulationRules.withinMax(50, 1, 50));
        assertFalse(PopulationRules.withinMax(49, 2, 50));
        assertTrue(PopulationRules.withinMax(50, 0, 50), "a free unit is never over the cap");
        assertTrue(PopulationRules.withinMax(70_000, 3, UNLIMITED));
        // a full supply says nothing about the hard cap and the other way round
        check(Verdict.WALKS_OUT, 50, UNLIMITED, 1, 0, 0);
        assertFalse(PopulationRules.withinMax(50, 1, 50));
    }

    // ---------------------------------------------------------------- properties over many inputs

    @Test
    void theVerdictObeysItsRulesEverywhere() {
        Random rng = new Random(2026);
        for (int i = 0; i < 20_000; i++) {
            int current = rng.nextInt(40), supply = rng.nextInt(40), cost = rng.nextInt(6);
            int committed = rng.nextInt(15), maxSkulls = rng.nextInt(12) - 1;
            Verdict v = PopulationRules.verdict(current, supply, cost, committed, maxSkulls);
            String in = "current " + current + " supply " + supply + " cost " + cost + " skulls " + committed + "/" + maxSkulls;

            boolean room = cost == 0 || current + cost <= supply;
            assertEquals(room, v == Verdict.WALKS_OUT, "walks out exactly when the army has room: " + in);
            if (maxSkulls <= 0)
                assertTrue(v == Verdict.WALKS_OUT || v == Verdict.ARMY_FULL, "no slots, no skulls: " + in);
            else
                assertTrue(v != Verdict.ARMY_FULL, "a graveyard with slots is never just 'army full': " + in);
            assertEquals(v == Verdict.BANKS_AS_SKULL || v == Verdict.WALKS_OUT, v.canQueue(), in);
            assertEquals(v == Verdict.BANKS_AS_SKULL, PopulationRules.banksOnCompletion(current, supply, cost, maxSkulls) && v.canQueue(), in);

            // more load never helps: a refusal stays a refusal when the army or the bank is fuller
            if (!v.canQueue()) {
                assertFalse(PopulationRules.verdict(current + 1, supply, cost, committed, maxSkulls).canQueue(), "current+1: " + in);
                assertFalse(PopulationRules.verdict(current, supply, cost, committed + 1, maxSkulls).canQueue(), "skulls+1: " + in);
            }
            // more room never hurts: an acceptance stays an acceptance with a bigger supply or a bigger bank
            if (v.canQueue()) {
                assertTrue(PopulationRules.verdict(current, supply + 1, cost, committed, maxSkulls).canQueue(), "supply+1: " + in);
                assertTrue(PopulationRules.verdict(current, supply, cost, committed, maxSkulls + 1).canQueue(), "slots+1: " + in);
            }
        }
    }
}
