package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The accounting behind the [Sys] and [FrameParts] lines (perf/SysTimer, SysReport, BatSections): nested sections
 * report SELF time so the rows add up, a skipped end cannot corrupt the stack, the worst tick is remembered, and the
 * profile-tree sums name our sections wherever vanilla filed them. The clock is a variable, so nothing here is timing-
 * dependent.
 */
class SysTimerTest {

    static final int OUTER = 0, INNER = 1, OTHER = 2;
    static final String[] NAMES = { "outer", "inner", "other" };

    private long now = 0;
    private final SysTimer timer = new SysTimer(3, () -> now);

    private int open(int id) {
        return timer.begin(id);
    }

    private void close(int token, long afterNs) {
        now += afterNs;
        timer.end(token);
    }

    @Test
    void aSectionReportsItsOwnTime() {
        int t = open(OUTER);
        close(t, 5_000);
        assertEquals(5_000, timer.selfNs[OUTER]);
        assertEquals(1, timer.calls[OUTER]);
    }

    @Test
    void aNestedSectionIsTakenOutOfItsParent() {
        int outer = open(OUTER);
        now += 2_000;                 // the outer section's own work
        int inner = open(INNER);
        close(inner, 7_000);          // the inner one's
        close(outer, 1_000);          // more of the outer's own
        assertEquals(7_000, timer.selfNs[INNER]);
        assertEquals(3_000, timer.selfNs[OUTER], "outer is 10 us long, 7 of them inside inner");
        assertEquals(10_000, timer.totalSelfNs(), "the rows add up to the whole: no millisecond counted twice");
    }

    @Test
    void severalChildrenAllComeOutOfTheParent() {
        int outer = open(OUTER);
        for (int i = 0; i < 3; i++) {
            int inner = open(INNER);
            close(inner, 1_000);
        }
        close(outer, 500);
        assertEquals(3_000, timer.selfNs[INNER]);
        assertEquals(3, timer.calls[INNER]);
        assertEquals(500, timer.selfNs[OUTER]);
    }

    @Test
    void anEndSkippedByAnExceptionIsUnwoundByTheOuterEnd() {
        int outer = open(OUTER);
        now += 1_000;
        open(INNER);                  // never closed: the code in between threw
        now += 4_000;
        timer.end(outer);             // the outer finally still runs
        assertFalse(timer.open(), "the stack is empty again");
        assertEquals(5_000, timer.selfNs[OUTER], "the lost inner section's time stays with its parent");
        assertEquals(0, timer.calls[INNER]);
        // and the timer works afterwards
        int again = open(OTHER);
        close(again, 2_000);
        assertEquals(2_000, timer.selfNs[OTHER]);
    }

    @Test
    void aStaleTokenIsIgnored() {
        int outer = open(OUTER);
        int inner = open(INNER);
        close(outer, 1_000);          // unwinds inner too
        timer.end(inner);             // its own end arrives late: nothing happens
        assertEquals(1_000, timer.selfNs[OUTER]);
        assertEquals(0, timer.calls[INNER]);
        timer.end(0);                 // "not recorded" token
        assertFalse(timer.open());
    }

    @Test
    void aFullStackRefusesTheSectionWithoutBreakingTheOnesOpen() {
        int[] tokens = new int[SysTimer.MAX_DEPTH];
        for (int i = 0; i < tokens.length; i++)
            tokens[i] = open(OUTER);
        assertEquals(0, open(INNER), "no room: not recorded");
        assertEquals(1, timer.overflow);
        for (int i = tokens.length - 1; i >= 0; i--) {
            now += 10;
            timer.end(tokens[i]);
        }
        assertFalse(timer.open());
        assertEquals(10L * SysTimer.MAX_DEPTH, timer.totalSelfNs());
    }

    @Test
    void theWorstTickAndTheWorstCallAreKept() {
        for (int tick = 1; tick <= 3; tick++) {
            for (int call = 0; call < tick; call++) {
                int t = open(OUTER);
                close(t, tick * 1_000L);
            }
            timer.tickEnd();
        }
        assertEquals(3, timer.ticks);
        assertEquals(6, timer.calls[OUTER]);
        assertEquals(3_000, timer.worstCallNs[OUTER]);
        assertEquals(9_000, timer.worstTickNs[OUTER], "tick 3 spent three calls of 3 us in it");
        assertEquals(1_000 + 2 * 2_000 + 3 * 3_000, timer.selfNs[OUTER]);
    }

    @Test
    void aTickThatClosesWithASectionOpenIsCountedAndResynced() {
        open(OUTER);
        timer.tickEnd();
        assertEquals(1, timer.unbalanced);
        assertFalse(timer.open());
    }

    @Test
    void timeMeasuredElsewhereCountsAsOneCall() {
        timer.add(INNER, 4_000);
        timer.add(INNER, 6_000);
        assertEquals(10_000, timer.selfNs[INNER]);
        assertEquals(2, timer.calls[INNER]);
        assertEquals(6_000, timer.worstCallNs[INNER]);
    }

    @Test
    void resetStartsANewWindow() {
        int t = open(OUTER);
        close(t, 1_000);
        timer.tickEnd();
        timer.reset();
        assertEquals(0, timer.totalSelfNs());
        assertEquals(0, timer.ticks);
        assertEquals(0, timer.calls[OUTER]);
        assertEquals(0, timer.worstTickNs[OUTER]);
    }

    @Test
    void theLifetimeTotalsOutliveAReset() {
        // a benchmark differences two readings across the 10-second report windows (Sys.lifetime)
        int outer = open(OUTER);
        int inner = open(INNER);
        close(inner, 3_000);
        close(outer, 1_000);
        timer.reset();
        timer.add(INNER, 2_000);
        assertEquals(1_000, timer.lifetimeNs[OUTER], "self time, like the window's");
        assertEquals(5_000, timer.lifetimeNs[INNER]);
        assertEquals(2, timer.lifetimeCalls[INNER]);
        assertEquals(2_000, timer.selfNs[INNER], "the window itself did start again");
    }

    // ---- the text ----------------------------------------------------------------------------------------------

    @Test
    void theLineNamesTheHeaviestSectionFirstAndSplitsTheTick() {
        for (int tick = 0; tick < 10; tick++) {
            int outer = open(OUTER);
            now += 1_000_000;                 // 1 ms of its own
            for (int i = 0; i < 5; i++) {
                int inner = open(INNER);
                close(inner, 1_000_000);      // five 1 ms calls
            }
            close(outer, 0);
            int other = open(OTHER);
            close(other, 100_000);            // 0.1 ms
            timer.tickEnd();
        }
        String line = SysReport.line("[Sys]", "tick", NAMES, timer, 10.0);
        assertTrue(line.startsWith("[Sys] 10 ticks, mean 10.0 ms/tick; ours 6.1 ms (61%): "), line);
        assertTrue(line.indexOf("inner 5.00") < line.indexOf("outer 1.00"), "heaviest first: " + line);
        assertTrue(line.indexOf("outer 1.00") < line.indexOf("other 0.10"), line);
        assertTrue(line.contains("inner 5.00 (max 5.0, 5.0/t)"), line);
        assertTrue(line.endsWith("; elsewhere 3.9 ms"), line);
    }

    @Test
    void sectionsUnderTheFloorAreCountedNotListed() {
        int t = open(OUTER);
        close(t, 1_000_000);
        int tiny = open(OTHER);
        close(tiny, 100);
        timer.tickEnd();
        String line = SysReport.line("[Sys]", "tick", NAMES, timer, -1);
        assertTrue(line.contains("outer 1.00"), line);
        assertFalse(line.contains("other 0.00"), line);
        assertTrue(line.contains("(+1 smaller)"), line);
        assertFalse(line.contains("elsewhere"), "no whole-tick figure was given: " + line);
    }

    @Test
    void aWindowWithNothingTimedSaysSo() {
        timer.tickEnd();
        assertTrue(SysReport.line("[Sys]", "tick", NAMES, timer, 5.0).contains("nothing timed"));
    }

    // ---- bat: sections out of a profile tree -----------------------------------------------------------------------

    /** A tree as a map from path to children, the way ProfileResults.getTimes lists them. */
    private static BatSections.Tree tree(Object... pathsAndChildren) {
        java.util.Map<String, List<BatSections.Node>> map = new java.util.HashMap<>();
        for (int i = 0; i < pathsAndChildren.length; i += 2)
            map.put((String) pathsAndChildren[i], castNodes(pathsAndChildren[i + 1]));
        return path -> map.getOrDefault(path, List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<BatSections.Node> castNodes(Object o) {
        return (List<BatSections.Node>) o;
    }

    private static BatSections.Node n(String name, double pct) {
        return new BatSections.Node(name, pct);
    }

    private static final String S = String.valueOf(BatSections.SEP);

    @Test
    void aBatSectionIsSummedAcrossEveryUnitTypeItAppearsUnder() {
        // vanilla files the unit tick under each entity type, so bat:attacker sits in two branches
        BatSections.Tree tree = tree(
            "root", List.of(n("tick", 90)),
            "root" + S + "tick", List.of(n("entities", 80), n("unspecified", 10)),
            "root" + S + "tick" + S + "entities", List.of(n("reignofnether:a_unit", 30), n("reignofnether:b_unit", 20), n("unspecified", 30)),
            "root" + S + "tick" + S + "entities" + S + "reignofnether:a_unit", List.of(n("bat:attacker", 12), n("goalSelector", 8), n("unspecified", 10)),
            "root" + S + "tick" + S + "entities" + S + "reignofnether:b_unit", List.of(n("bat:attacker", 6), n("unspecified", 14)));
        List<BatSections.Row> rows = BatSections.aggregate(tree);
        assertEquals(1, rows.size());
        assertEquals("bat:attacker", rows.get(0).name());
        assertEquals(18, rows.get(0).selfPct(), 1e-9);
        assertEquals(18, rows.get(0).inclusivePct(), 1e-9);
    }

    @Test
    void aBatSectionsOwnShareExcludesTheBatSectionsInsideIt() {
        BatSections.Tree tree = tree(
            "root", List.of(n("tick", 50)),
            "root" + S + "tick", List.of(n("bat:attacker", 20), n("unspecified", 30)),
            "root" + S + "tick" + S + "bat:attacker", List.of(n("bat:attacker/buildingScan", 14), n("bat:attacker/entityScan", 3), n("unspecified", 3)));
        List<BatSections.Row> rows = BatSections.aggregate(tree);
        assertEquals("bat:attacker/buildingScan", rows.get(0).name(), "heaviest self share first");
        BatSections.Row attacker = rows.stream().filter(r -> r.name().equals("bat:attacker")).findFirst().orElseThrow();
        assertEquals(3, attacker.selfPct(), 1e-9, "20 with the scans minus the 17 inside them");
        assertEquals(20, attacker.inclusivePct(), 1e-9);
        String text = BatSections.text(rows, 5);
        assertTrue(text.startsWith("bat:attacker/buildingScan 14.0%"), text);
        assertTrue(text.contains("bat:attacker 3.0% (20.0% with what it calls)"), text);
    }

    @Test
    void anEmptyProfileHasNoSections() {
        assertTrue(BatSections.aggregate(path -> List.of()).isEmpty());
        assertEquals("", BatSections.text(List.of(), 5));
    }

    @Test
    void nodesTooSmallToMatterAreNotWalked() {
        BatSections.Tree tree = tree(
            "root", List.of(n("tick", 0.01)),
            "root" + S + "tick", List.of(n("bat:attacker", 0.01)));
        assertTrue(BatSections.aggregate(tree).isEmpty());
    }
}
