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
