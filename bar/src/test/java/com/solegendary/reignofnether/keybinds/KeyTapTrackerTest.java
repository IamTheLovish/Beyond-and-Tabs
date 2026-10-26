package com.solegendary.reignofnether.keybinds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tap/hold rule on scripted timelines, with the frame times of the Oct 21 hosted match (30-50 ms frames, spikes of
 * 100-700 ms). Key events are only seen once per frame, when the frame ends; the camera decides at each frame START.
 */
class KeyTapTrackerTest {

    private static final int S = 83;
    private static long ms(long n) {
        return n * 1_000_000L;
    }

    /** What OrthoviewClientEvents.panKeyDown does for a key that is also an order on the card, at a frame start. */
    private static void frameStart(KeyTapTracker t, long atMs) {
        t.frame(ms(atMs));
        if (t.heldMs(S, ms(atMs)) > 0 && !t.inTapWindow(S, ms(atMs)))
            t.markPan(S);
    }

    @Test
    void aQuickTapAtNormalFrameRateIsATap() {
        KeyTapTracker t = new KeyTapTracker(350);
        t.press(S, ms(1000));
        for (long at = 1008; at <= 1100; at += 8)
            frameStart(t, at);
        assertFalse(t.isHold(S, ms(1100)), "a 100 ms press at 120 fps fires the order");
    }

    @Test
    void aHoldAtNormalFrameRateIsAPan() {
        KeyTapTracker t = new KeyTapTracker(350);
        t.press(S, ms(1000));
        for (long at = 1008; at <= 1300; at += 8)
            frameStart(t, at);
        assertTrue(t.isHold(S, ms(1300)), "a 300 ms press pans and fires nothing");
    }

    @Test
    void theLineStaysAt180MsWhenFramesAreFast() {
        for (long upAt : new long[] {170, 178, 190, 200}) {
            KeyTapTracker t = new KeyTapTracker(350);
            t.press(S, ms(0));
            for (long at = 8; at <= upAt; at += 8)
                frameStart(t, at);
            assertEquals(upAt >= 180, t.isHold(S, ms(upAt)), "released at " + upAt + " ms");
        }
    }

    @Test
    void aTapWhoseReleaseWaitedOutALongFrameIsStillATap() {
        // press seen at 0, a 250 ms frame: the finger lifted at ~100 ms but the release is only seen at 260 ms.
        // The old rule measured 260 > 180 = hold, the camera never moved (still in its tap window): nothing happened.
        KeyTapTracker t = new KeyTapTracker(350);
        t.press(S, ms(0));
        frameStart(t, 10);
        assertTrue(t.heldMs(S, ms(260)) >= KeyTapTracker.TAP_MS, "the old rule would read this as a hold");
        assertFalse(t.isHold(S, ms(260)));
    }

    @Test
    void aTapAmongFrames100MsLongIsATapEvenReadLate() {
        KeyTapTracker t = new KeyTapTracker(350);
        t.press(S, ms(0));
        frameStart(t, 10);
        frameStart(t, 120);          // still inside the tap window, the camera waits
        assertFalse(t.isHold(S, ms(220)));   // released ~150 ms, seen at the end of the next 100 ms frame
    }

    @Test
    void aRealHoldUnderLagStillPansAndIsAHold() {
        KeyTapTracker t = new KeyTapTracker(350);
        t.press(S, ms(0));
        frameStart(t, 10);
        frameStart(t, 120);
        frameStart(t, 230);          // held 230 ms: the camera pans from here
        assertTrue(t.isHold(S, ms(330)));
    }

    @Test
    void oncePannedItIsAHoldHoweverShortItMeasures() {
        // never both: the camera moved for this press, so no order may fire
        KeyTapTracker t = new KeyTapTracker(350);
        t.press(S, ms(0));
        frameStart(t, 10);
        frameStart(t, 160);
        frameStart(t, 190);          // pans
        assertTrue(t.isHold(S, ms(195)));
    }

    @Test
    void theForgivenessIsCappedAndNothingIsATapAfterFourHundredMs() {
        KeyTapTracker t = new KeyTapTracker(350);
        t.press(S, ms(0));
        frameStart(t, 10);
        // one 330 ms frame ends with the release: forgives at most MAX_SLACK_MS (150), so 340 - 150 = 190 >= 180
        assertTrue(t.isHold(S, ms(340)));

        t = new KeyTapTracker(350);
        t.press(S, ms(0));
        frameStart(t, 10);
        assertTrue(t.isHold(S, ms(700)), "a 700 ms freeze (the Esc-menu save) is not a tap");
    }

    @Test
    void aKeyWeNeverSawGoDownIsNotAHold() {
        KeyTapTracker t = new KeyTapTracker(350);
        assertFalse(t.isHold(S, ms(5000)));
        assertEquals(0, t.heldMs(S, ms(5000)));
        assertFalse(t.inTapWindow(S, ms(5000)));
    }

    @Test
    void aSecondPressStartsFresh() {
        KeyTapTracker t = new KeyTapTracker(350);
        t.press(S, ms(0));
        frameStart(t, 10);
        frameStart(t, 200);
        assertTrue(t.isHold(S, ms(210)));
        t.release(S);
        t.press(S, ms(1000));
        assertFalse(t.isHold(S, ms(1050)), "the pan flag of the last press is gone");
    }

    @Test
    void outOfRangeKeysAreIgnored() {
        KeyTapTracker t = new KeyTapTracker(10);
        t.press(-1, 0);
        t.press(99, 0);
        t.markPan(99);
        t.release(99);
        assertFalse(t.isHold(99, ms(1)));
    }
}
