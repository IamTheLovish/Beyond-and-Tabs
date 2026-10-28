package com.solegendary.reignofnether.barfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combat feel, part 1 (lovish approved Oct 28): a trauma-decay camera shake. Trauma 0..1 decays over time; the shake is
 * trauma squared times smoothed noise on yaw, pitch and roll plus a small offset. Big events feed it; in the RTS view
 * it is very gentle, fades with the event's distance from the screen centre and with zoom (a far zoom-out barely
 * moves); first person may be stronger. The player picks Off / Subtle (default) / Full.
 */
class CameraShakeTest {

    private static final float FP_ZOOM = 0f;

    /**
     * The largest |value| of one channel over two seconds of frames at 60 fps, trauma held (we measure the output's
     * size, not the decay); a fresh shake each time, so every measurement sees the same stretch of noise.
     */
    private static float peak(CameraShake given, boolean rts, int mode, float zoom, int channel) {
        float trauma = given.trauma();
        CameraShake s = new CameraShake();
        float[] out = new float[CameraShake.OUT];
        float max = 0f;
        for (int f = 0; f < 120; f++) {
            s.step(1f / 60f);
            s.setTrauma(trauma);
            s.output(rts, mode, 1f, zoom, out);
            max = Math.max(max, Math.abs(out[channel]));
        }
        return max;
    }

    @Test
    void traumaNeverGoesPastOneHoweverManyBlasts() {
        CameraShake s = new CameraShake();
        for (int i = 0; i < 500; i++)
            s.add(0.6f);
        assertEquals(1f, s.trauma(), 0f);
        s.add(-3f);   // a negative feed is ignored, never subtracts
        assertEquals(1f, s.trauma(), 0f);
    }

    @Test
    void traumaDecaysToNothingAndNeverBelow() {
        CameraShake s = new CameraShake();
        s.add(1f);
        float last = s.trauma();
        for (int f = 0; f < 600; f++) {
            s.step(1f / 60f);
            assertTrue(s.trauma() <= last, "decay only goes down");
            assertTrue(s.trauma() >= 0f, "never negative");
            last = s.trauma();
        }
        assertEquals(0f, s.trauma(), 0f, "a full jolt is gone within ten seconds");
        // and it is short: a full jolt is gone within a second and a half
        CameraShake q = new CameraShake();
        q.add(1f);
        for (int f = 0; f < 90; f++)
            q.step(1f / 60f);
        assertEquals(0f, q.trauma(), 1e-6f);
    }

    @Test
    void theShakeIsTraumaSquared() {
        float[] full = new float[CameraShake.OUT], half = new float[CameraShake.OUT];
        CameraShake a = new CameraShake(), b = new CameraShake();
        for (int f = 0; f < 37; f++) {   // the same clock for both
            a.step(1f / 60f);
            b.step(1f / 60f);
        }
        a.setTrauma(1f);
        b.setTrauma(0.5f);
        a.output(false, CameraShake.FULL, 1f, FP_ZOOM, full);
        b.output(false, CameraShake.FULL, 1f, FP_ZOOM, half);
        for (int c = 0; c < CameraShake.OUT; c++)
            assertEquals(full[c] * 0.25f, half[c], 1e-5f, "channel " + c + " at half trauma is a quarter");
    }

    @Test
    void offMeansNoShakeAtAllAndSubtleIsLessThanFull() {
        CameraShake s = new CameraShake();
        s.add(1f);
        for (int c = 0; c < CameraShake.OUT; c++)
            assertEquals(0f, peak(s, false, CameraShake.OFF, FP_ZOOM, c), 0f, "Off, channel " + c);
        float subtle = peak(s, false, CameraShake.SUBTLE, FP_ZOOM, CameraShake.YAW);
        float full = peak(s, false, CameraShake.FULL, FP_ZOOM, CameraShake.YAW);
        assertTrue(subtle > 0f, "Subtle still shakes");
        assertTrue(subtle < full * 0.75f, "Subtle is clearly gentler than Full: " + subtle + " vs " + full);
        assertEquals(CameraShake.SUBTLE, CameraShake.sanitiseMode(99), "an unknown value reads as the default");
        assertEquals(CameraShake.SUBTLE, CameraShake.sanitiseMode(-1));
    }

    @Test
    void theSliderScalesAndZeroIsStill() {
        CameraShake s = new CameraShake();
        s.add(1f);
        s.step(0.1f);
        s.setTrauma(1f);
        float[] out = new float[CameraShake.OUT];
        s.output(false, CameraShake.FULL, 0f, FP_ZOOM, out);
        for (int c = 0; c < CameraShake.OUT; c++)
            assertEquals(0f, out[c], 0f, "slider at 0%, channel " + c);
    }

    @Test
    void everyAxisMovesInFirstPersonWithinItsCeiling() {
        CameraShake s = new CameraShake();
        s.add(1f);
        float yaw = peak(s, false, CameraShake.FULL, FP_ZOOM, CameraShake.YAW);
        float pitch = peak(s, false, CameraShake.FULL, FP_ZOOM, CameraShake.PITCH);
        float roll = peak(s, false, CameraShake.FULL, FP_ZOOM, CameraShake.ROLL);
        assertTrue(yaw > 0.5f && pitch > 0.5f && roll > 0.3f, "yaw, pitch and roll all move: " + yaw + " " + pitch + " " + roll);
        assertTrue(yaw <= CameraShake.FP_MAX_DEG && pitch <= CameraShake.FP_MAX_DEG && roll <= CameraShake.FP_MAX_ROLL_DEG,
            "within the first-person ceiling");
    }

    @Test
    void theRtsViewIsVeryGentleComparedWithFirstPerson() {
        CameraShake s = new CameraShake();
        s.add(1f);
        float fp = peak(s, false, CameraShake.FULL, FP_ZOOM, CameraShake.YAW);
        float rts = peak(s, true, CameraShake.FULL, 20f, CameraShake.YAW);
        assertTrue(rts > 0f, "the RTS view still feels a big blast");
        assertTrue(rts <= fp * 0.15f, "RTS yaw at most 15% of first person: " + rts + " vs " + fp);
        float off = peak(s, true, CameraShake.FULL, 20f, CameraShake.OFF_X);
        assertTrue(off > 0f && off <= CameraShake.RTS_MAX_OFFSET, "a small screen offset in the RTS view: " + off);
    }

    @Test
    void aFarZoomOutBarelyMoves() {
        CameraShake s = new CameraShake();
        s.add(1f);
        float close = peak(s, true, CameraShake.FULL, 25f, CameraShake.YAW);
        float mid = peak(s, true, CameraShake.FULL, 60f, CameraShake.YAW);
        float far = peak(s, true, CameraShake.FULL, 110f, CameraShake.YAW);
        assertTrue(close > mid && mid > far, "less shake as the camera zooms out: " + close + " " + mid + " " + far);
        assertTrue(far <= close * 0.08f, "a far zoom-out barely moves: " + far + " vs " + close);
        assertEquals(1f, CameraShake.rtsZoomGain(10f), 0f, "zoomed right in = full RTS shake");
        assertTrue(CameraShake.rtsZoomGain(200f) <= 0.05f);
    }

    @Test
    void anEventFadesWithItsDistanceFromTheScreenCentre() {
        float range = CameraShake.rtsRange(30f);
        assertEquals(1f, CameraShake.falloff(0f, range), 0f, "at the centre");
        assertTrue(CameraShake.falloff(range * 0.5f, range) < 1f);
        assertTrue(CameraShake.falloff(range * 0.5f, range) > CameraShake.falloff(range * 0.9f, range));
        assertEquals(0f, CameraShake.falloff(range * 1.01f, range), 0f, "off screen: nothing");
        assertTrue(CameraShake.rtsRange(80f) > CameraShake.rtsRange(30f), "a wider view reaches further");
        assertEquals(0f, CameraShake.falloff(Float.NaN, range), 0f, "a bad distance gives no shake");
    }

    @Test
    void theNoiseIsSmoothBoundedAndDifferentPerAxis() {
        float prev = CameraShake.noise(0f, 0);
        boolean differs = false;
        for (int i = 1; i <= 4000; i++) {
            float t = i / 1000f;
            for (int axis = 0; axis < 3; axis++) {
                float n = CameraShake.noise(t, axis);
                assertTrue(n >= -1f && n <= 1f, "bounded: " + n);
            }
            float n0 = CameraShake.noise(t, 0);
            assertTrue(Math.abs(n0 - prev) < 0.12f, "smooth (no jump between 1 ms samples) at t=" + t);
            prev = n0;
            if (Math.abs(CameraShake.noise(t, 0) - CameraShake.noise(t, 1)) > 0.2f)
                differs = true;
        }
        assertTrue(differs, "yaw and pitch do not move in lockstep");
        float lo = 1f, hi = -1f;
        for (int i = 0; i < 400; i++) {
            float n = CameraShake.noise(i * 0.01f, 2);
            lo = Math.min(lo, n);
            hi = Math.max(hi, n);
        }
        assertTrue(hi - lo > 0.8f, "it really moves: range " + (hi - lo));
    }

    @Test
    void manySmallEventsCannotOutShakeTheirCap() {
        // T3 hits and nearby heavy landings are "minor" feeds: a big fight of them never grows into a big shake
        CameraShake s = new CameraShake();
        for (int i = 0; i < 400; i++)
            s.addMinor(0.2f);
        assertTrue(s.trauma() <= CameraShake.MINOR_CAP + 1e-6f, "one burst of minor events is capped: " + s.trauma());
        float maxTrauma = 0f;
        for (int f = 0; f < 600; f++) {   // ten seconds of a T3 brawl: 30 minor hits a second
            s.step(1f / 60f);
            if (f % 2 == 0)
                s.addMinor(0.2f);
            maxTrauma = Math.max(maxTrauma, s.trauma());
        }
        assertTrue(maxTrauma <= 0.5f, "sustained minor feed stays a rumble: " + maxTrauma);
        s.add(1f);
        assertEquals(1f, s.trauma(), 0f, "a real blast still lands on top");
    }

    @Test
    void bigEventsEarnTraumaBySizeAndSmallOnesNone() {
        assertEquals(0f, CameraShake.traumaForBlast(1f), 0f, "a small pop does not shake");
        assertTrue(CameraShake.traumaForBlast(3f) > 0f);
        assertTrue(CameraShake.traumaForBlast(8f) > CameraShake.traumaForBlast(3f));
        assertTrue(CameraShake.traumaForBlast(50f) <= 1f);
        assertTrue(CameraShake.traumaForCollapse(6f) > CameraShake.traumaForCollapse(2f));
        assertTrue(CameraShake.traumaForCollapse(100f) <= 1f);
        assertTrue(CameraShake.traumaForHeavyImpact(4f) > 0f && CameraShake.traumaForHeavyImpact(40f) <= 1f);
        assertEquals(0f, CameraShake.traumaForLanding(0.3f), 0f, "a step down is not a landing");
        assertTrue(CameraShake.traumaForLanding(1.2f) > CameraShake.traumaForLanding(0.7f));
        assertTrue(CameraShake.traumaForLanding(50f) <= 1f);
    }

    @Test
    void badInputNeverPoisonsTheCamera() {
        CameraShake s = new CameraShake();
        s.add(Float.NaN);
        s.add(Float.POSITIVE_INFINITY);
        s.step(Float.NaN);
        s.step(-5f);
        s.step(1e9f);
        float[] out = new float[CameraShake.OUT];
        s.add(0.7f);
        s.output(true, CameraShake.FULL, 1f, Float.NaN, out);
        for (float v : out)
            assertTrue(Float.isFinite(v), "finite output");
        s.output(false, CameraShake.FULL, Float.NaN, 0f, out);
        for (float v : out)
            assertTrue(Float.isFinite(v), "finite output");
    }
}
