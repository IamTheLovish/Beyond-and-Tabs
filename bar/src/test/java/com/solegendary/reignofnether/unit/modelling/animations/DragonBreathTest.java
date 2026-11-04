package com.solegendary.reignofnether.unit.modelling.animations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backlog 4 (Command Center, Nov 4): the phase-as-state bug of #303, in the dragon. Dragon.pose drew the breath as
 * Life.breath(age, id, breathTicks * (1 - 0.3 * glide)) = ((age + offset) / period) % 1, and glide is a smoothed
 * weight that moves every frame the dragon speeds up or slows down. Each frame's change of period moves that phase by
 * age * d(1 / period): an hour into a match that is several whole cycles a tick, so the ribcage (+-10% scale, sized
 * to read from the RTS camera) flickers across its full range whenever the dragon changes speed. The phase must be
 * state, advanced by elapsed / period, so a glide changes the breath's speed and never jumps it (#303's rule for the
 * living army and the bison).
 */
class DragonBreathTest {

    private static final Dragon.Params P = new Dragon.Params();
    private static final int ID = 11418;

    /** The breath (-1..1) a tick at a time from age0, with glide(t) as the dragon's glide weight. */
    private static float worstStep(float age0, java.util.function.IntToDoubleFunction glide) {
        float ph = -1f, last = Float.NaN, worst = 0f;
        for (int t = 0; t < 900; t++) {
            ph = Dragon.breathPhase(ph, age0 + t, t == 0 ? 0f : 1f, ID, P, (float) glide.applyAsDouble(t));
            float br = Life.breathAt(ph);
            if (t > 0)
                worst = Math.max(worst, Math.abs(br - last));
            last = br;
        }
        return worst;
    }

    /** A cruise that slows into a hover and speeds up again: glide 1 -> 0 -> 1, smoothed like Dragon's (~2 s). */
    private static final double[] GLIDE = new double[900];
    static {
        double g = 1.0;
        for (int i = 0; i < GLIDE.length; i++) {
            g += ((i < 300 || i >= 600 ? 1.0 : 0.0) - g) * 0.05;
            GLIDE[i] = g;
        }
    }

    private static double speedChanges(int t) {
        return GLIDE[t];
    }

    @Test
    void theBreathNeverJumpsWhileTheGlideChanges() {
        float age0 = 72_000f;   // an hour into a match
        float steady = worstStep(age0, t -> 1.0);   // the quickest breath (gliding), never changing
        float varying = worstStep(age0, DragonBreathTest::speedChanges);
        assertTrue(steady > 0f, "the steady breath moved");
        assertTrue(varying <= steady * 1.05f, "the ribcage jumped " + varying + " of its -1..1 range in one tick while"
            + " the glide changed; a steady gliding breath moves at most " + steady + " a tick");
    }

    @Test
    void aFreshDragonBreathesWhereTheStillPoseDoes() {
        // the first frame seeds the phase where the stateless breath (tools/dragon_poses.py's still poses) puts it
        float age = 1234f, glide = 0.4f;
        float ph = Dragon.breathPhase(-1f, age, 0f, ID, P, glide);
        assertEquals(Life.breath(age, ID, P.breathTicks * (1f - 0.3f * glide)), Life.breathAt(ph), 1e-4f);
    }

    @Test
    void theGlideSetsTheBreathsSpeed() {
        // one full period at a steady glide brings the breath back to where it started
        float glide = 0.5f, period = P.breathTicks * (1f - 0.3f * glide);
        float start = Dragon.breathPhase(-1f, 5000f, 0f, ID, P, glide), ph = start;
        int n = Math.round(period * 4f);
        for (int i = 0; i < n; i++)
            ph = Dragon.breathPhase(ph, 5000f + (i + 1) * 0.25f, 0.25f, ID, P, glide);   // four frames a tick
        assertEquals(Life.breathAt(start), Life.breathAt(ph), 2e-3f);
    }
}
