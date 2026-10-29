package com.solegendary.reignofnether.gametest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** TestCleanup's bookkeeping (TestCleanup.Ends), with plain keys for tests - no Minecraft needed. */
class TestCleanupTest {

    @Test
    void stepsRunNewestFirstWhenTheirTestEnds() {
        TestCleanup.Ends<Object> ends = new TestCleanup.Ends<>();
        Object test = new Object();
        List<String> ran = new ArrayList<>();
        ends.add(test, () -> ran.add("pad"), () -> { });
        ends.add(test, () -> ran.add("units"), () -> { });
        assertEquals(2, ends.pending());
        ends.end(test);
        assertEquals(List.of("units", "pad"), ran);
        assertEquals(0, ends.pending());
    }

    @Test
    void aStepRunEarlyDoesNotRunAgainAtTheEnd() {
        TestCleanup.Ends<Object> ends = new TestCleanup.Ends<>();
        Object test = new Object();
        int[] runs = { 0 };
        Runnable undo = ends.add(test, () -> runs[0]++, () -> { });
        undo.run();
        undo.run();
        ends.end(test);
        ends.end(test);
        assertEquals(1, runs[0]);
    }

    @Test
    void aThrowingStepDoesNotSkipTheOthers() {
        TestCleanup.Ends<Object> ends = new TestCleanup.Ends<>();
        Object test = new Object();
        List<String> ran = new ArrayList<>();
        ends.add(test, () -> ran.add("first"), () -> { });
        ends.add(test, () -> { throw new IllegalStateException("undo failed"); }, () -> { });
        ends.add(test, () -> ran.add("last"), () -> { });
        ends.end(test);
        assertEquals(List.of("last", "first"), ran);
        assertEquals(0, ends.pending());
    }

    @Test
    void theHookRunsOncePerTestAndTestsEndApart() {
        TestCleanup.Ends<Object> ends = new TestCleanup.Ends<>();
        Object a = new Object(), b = new Object();
        int[] hooks = { 0 };
        List<String> ran = new ArrayList<>();
        ends.add(a, () -> ran.add("a1"), () -> hooks[0]++);
        ends.add(a, () -> ran.add("a2"), () -> hooks[0]++);
        ends.add(b, () -> ran.add("b1"), () -> hooks[0]++);
        assertEquals(2, hooks[0]);
        ends.end(a);
        assertEquals(List.of("a2", "a1"), ran);
        assertEquals(1, ends.pending());
        ends.end(b);
        assertEquals(List.of("a2", "a1", "b1"), ran);
    }

    @Test
    void aPadCoversWhatStandsOnItAndNothingBesideOrHighAbove() {
        TestCleanup.Footprint pads = new TestCleanup.Footprint();
        assertNull(pads.span());
        pads.add(-5, 50, 7);
        assertTrue(pads.covers(-4.5, 51.0, 7.5), "a unit standing on the pad");
        assertTrue(pads.covers(-4.5, 50.0 + TestCleanup.Footprint.HEADROOM, 7.5), "one hopping on it");
        assertFalse(pads.covers(-4.5, 51.0 + TestCleanup.Footprint.HEADROOM, 7.5), "one passing high over it");
        assertFalse(pads.covers(-4.5, 49.0, 7.5), "one under it");
        assertFalse(pads.covers(-3.5, 51.0, 7.5), "one in the next column");
        assertFalse(pads.covers(-4.5, 51.0, 8.5), "one in the next row");
        assertArrayEquals(new int[] { -5, 50, 7, -5, 50, 7 }, pads.span());
    }

    @Test
    void aBoxCoversItsColumnsFromBottomToTop() {
        TestCleanup.Footprint pads = new TestCleanup.Footprint();
        pads.addBox(3, 30, -2, 1, 20, -4);
        assertTrue(pads.covers(1.5, 20.0, -3.5));
        assertTrue(pads.covers(3.9, 33.0, -1.1));
        assertFalse(pads.covers(4.0, 25.0, -3.0));
        assertFalse(pads.covers(2.0, 19.9, -3.0));
        assertArrayEquals(new int[] { 1, 20, -4, 3, 30, -2 }, pads.span());
    }

    @Test
    void aStepAddedWhileEndingStillRuns() {
        TestCleanup.Ends<Object> ends = new TestCleanup.Ends<>();
        Object test = new Object();
        List<String> ran = new ArrayList<>();
        ends.add(test, () -> {
            ran.add("outer");
            ends.add(test, () -> ran.add("inner"), () -> { });
        }, () -> { });
        ends.end(test);
        assertEquals(List.of("outer", "inner"), ran);
        assertEquals(0, ends.pending());
    }
}
