package com.solegendary.reignofnether.keybinds;

/**
 * Tap or hold, for a key that is both a card order and a camera pan (S: Stop and pan-down; any order a player puts on
 * W/A/S/D). No Minecraft or Forge types and no clock of its own - every call takes "now" in nanoseconds - so the
 * rule is unit-tested on scripted timelines (KeyTapTrackerTest); {@link KeyTaps} feeds it the real key events.
 *
 * Why the old rule failed in lag (Oct 21 playtest, a hosted match at 30-50 ms frames with 100-700 ms spikes): GLFW
 * hands over key events once per frame, in glfwPollEvents, so a press and a release are stamped when the frame that
 * held them ENDS, not when the finger moved. A 120 ms tap whose release waited out a 250 ms frame measured 250 ms,
 * "longer than 180", and became a hold: the camera had not moved yet (it was still waiting out the tap) and no order
 * fired, so nothing happened at all. The measurement can be wrong by at most the length of the frame the release
 * landed in, so the verdict now forgives exactly that (capped), and it follows what the player SAW: once a frame has
 * panned the camera for this press ({@link #markPan}) it was a hold, however short it measures.
 *
 * Verdict at release ({@link #isHold}), first match wins:
 *  - key not seen going down: not a hold (the order fires, as before);
 *  - the camera already panned for this press: hold;
 *  - held {@link #NEVER_TAP_MS} or more: hold (nobody taps for that long, whatever the frames were);
 *  - otherwise: hold when held - slack >= {@link #TAP_MS}, where slack is the longest frame seen while the key was
 *    down (the frame the release arrived in included), capped at {@link #MAX_SLACK_MS}.
 * At 120 fps the slack is about 8 ms, so the tap/hold line stays where it was (180 ms).
 */
public final class KeyTapTracker {

    /** A press shorter than this (frame slack aside) is a tap. */
    public static final long TAP_MS = 180;
    /** The most frame lag the verdict forgives: a 150 ms frame is already a slideshow. */
    public static final long MAX_SLACK_MS = 150;
    /** A press this long is a hold whatever the frame times were. */
    public static final long NEVER_TAP_MS = 400;

    private static final long NS_PER_MS = 1_000_000L;
    private static final int MAX_DOWN = 32;

    private final long[] pressedAt;      // nanoTime when the press was processed; 0 = up
    private final long[] worstFrame;     // longest frame period seen while down (nanos)
    private final boolean[] panned;      // a frame already panned the camera for this press
    private final int[] down = new int[MAX_DOWN];
    private int downCount;
    private long lastFrameStart;

    public KeyTapTracker(int keyCount) {
        pressedAt = new long[keyCount];
        worstFrame = new long[keyCount];
        panned = new boolean[keyCount];
    }

    private boolean valid(int key) {
        return key >= 0 && key < pressedAt.length;
    }

    /** A key went down (GLFW press; repeats are not passed in). */
    public void press(int key, long now) {
        if (!valid(key))
            return;
        if (pressedAt[key] == 0 && downCount < MAX_DOWN)
            down[downCount++] = key;
        pressedAt[key] = Math.max(1, now);   // 0 means "up", so a clock at 0 still counts as down
        worstFrame[key] = 0;
        panned[key] = false;
    }

    /** A key went up. Read the verdict ({@link #isHold}) first: this forgets the press. */
    public void release(int key) {
        if (!valid(key))
            return;
        for (int i = 0; i < downCount; i++)
            if (down[i] == key) {
                down[i] = down[--downCount];
                break;
            }
        pressedAt[key] = 0;
        worstFrame[key] = 0;
        panned[key] = false;
    }

    /**
     * A new frame starts (RenderTickEvent START). The period since the previous start is a frame every held key
     * lived through - unless the key went down after that frame began (its press was processed at that frame's end).
     */
    public void frame(long now) {
        long prevStart = lastFrameStart;
        lastFrameStart = now;
        if (prevStart == 0)
            return;
        long dt = now - prevStart;
        for (int i = 0; i < downCount; i++) {
            int k = down[i];
            if (pressedAt[k] < prevStart && dt > worstFrame[k])
                worstFrame[k] = dt;
        }
    }

    /** Milliseconds the key has been down (0 when it is up or was pressed before we saw it). */
    public long heldMs(int key, long now) {
        if (!valid(key) || pressedAt[key] == 0)
            return 0;
        return (now - pressedAt[key]) / NS_PER_MS;
    }

    /** True while the key is down but not yet past a tap: the camera waits here before an order key pans. */
    public boolean inTapWindow(int key, long now) {
        if (!valid(key) || pressedAt[key] == 0)
            return false;
        return (now - pressedAt[key]) / NS_PER_MS < TAP_MS;
    }

    /** The camera panned a frame for this press (it is out of its tap window): from here on it is a hold. */
    public void markPan(int key) {
        if (valid(key) && pressedAt[key] != 0)
            panned[key] = true;
    }

    /** At release: was this press a pan (true) or a tap (false)? See the class comment for the rule. */
    public boolean isHold(int key, long now) {
        if (!valid(key) || pressedAt[key] == 0)
            return false;
        if (panned[key])
            return true;
        long held = (now - pressedAt[key]) / NS_PER_MS;
        if (held >= NEVER_TAP_MS)
            return true;
        long current = lastFrameStart == 0 ? 0 : Math.max(0, now - lastFrameStart);
        long slack = Math.min(MAX_SLACK_MS, Math.max(worstFrame[key], current) / NS_PER_MS);
        return held - slack >= TAP_MS;
    }
}
