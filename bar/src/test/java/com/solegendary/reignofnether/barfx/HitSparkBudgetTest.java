package com.solegendary.reignofnether.barfx;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combat feel, part 2 (lovish approved Oct 28): hit sparks have hard caps - a per-second and a per-chunk budget - so a
 * 4v4 battle with hundreds of units never floods particles, while a single hit always gets its burst.
 */
class HitSparkBudgetTest {

    /** Grants inside a sliding window of {@code window} seconds. */
    private static final class Window {
        final float window;
        final ArrayDeque<float[]> grants = new ArrayDeque<>();
        int sum = 0, worst = 0;

        Window(float window) {
            this.window = window;
        }

        void add(float t, int n) {
            if (n <= 0)
                return;
            grants.add(new float[] {t, n});
            sum += n;
            while (!grants.isEmpty() && grants.peekFirst()[0] <= t - window)
                sum -= (int) grants.pollFirst()[1];
            worst = Math.max(worst, sum);
        }
    }

    @Test
    void aSingleHitGetsItsWholeBurst() {
        HitSparkBudget b = new HitSparkBudget();
        assertEquals(4, b.take(3, -7, 0f, 4));
        assertEquals(HitSparkBudget.MAX_PER_HIT, b.take(100, 100, 0f, 500), "one hit never gets more than a burst");
        assertEquals(0, b.take(0, 0, 0f, 0));
        assertEquals(0, b.take(0, 0, 0f, -3));
    }

    @Test
    void aFourVersusFourBattleNeverPassesTheGlobalBudget() {
        // hundreds of units in 30 chunks: 600 hits a second, 5 sparks wanted each, drawn at 60 fps for ten seconds
        HitSparkBudget b = new HitSparkBudget();
        Window w = new Window(1f);
        java.util.Random r = new java.util.Random(7);
        int granted = 0;
        for (int f = 0; f < 600; f++) {
            float t = f / 60f;
            for (int h = 0; h < 10; h++) {
                int n = b.take(r.nextInt(6), r.nextInt(5), t, 5);
                w.add(t, n);
                granted += n;
            }
        }
        assertTrue(w.worst <= HitSparkBudget.GLOBAL_PER_SECOND,
            "never more than " + HitSparkBudget.GLOBAL_PER_SECOND + " sparks in any second: " + w.worst);
        assertTrue(granted >= HitSparkBudget.GLOBAL_PER_SECOND * 7, "but the budget is used, not starved: " + granted);
    }

    @Test
    void oneChunkCannotHogTheBudget() {
        HitSparkBudget b = new HitSparkBudget();
        Window hot = new Window(1f);
        int other = 0;
        for (int f = 0; f < 300; f++) {
            float t = f / 60f;
            for (int h = 0; h < 8; h++)
                hot.add(t, b.take(12, 12, t, 6));
            if (f % 30 == 0)
                other += b.take(-40, 9, t, 3);
        }
        assertTrue(hot.worst <= HitSparkBudget.CHUNK_PER_SECOND, "a hot chunk stays inside its own budget: " + hot.worst);
        assertEquals(30, other, "a quiet chunk next door still gets every spark it asks for");
    }

    @Test
    void theBudgetComesBackAfterAQuietSecond() {
        HitSparkBudget b = new HitSparkBudget();
        int first = 0;
        for (int i = 0; i < 200; i++)
            first += b.take(0, 0, 0f, 6);
        assertTrue(first <= HitSparkBudget.CHUNK_PER_SECOND);
        assertEquals(0, b.take(0, 0, 0.01f, 6), "spent");
        assertEquals(6, b.take(0, 0, 2f, 6), "refilled after a quiet stretch");
    }

    @Test
    void moreLiveChunksThanTheTableHoldsStillRespectTheCaps() {
        HitSparkBudget b = new HitSparkBudget();
        Window all = new Window(1f), hot = new Window(1f);
        for (int f = 0; f < 240; f++) {
            float t = f / 60f;
            for (int c = 0; c < HitSparkBudget.TABLE * 3; c++)
                all.add(t, b.take(c, -c, t, 2));
            hot.add(t, b.take(5000, 5000, t, 6));
        }
        assertTrue(all.worst <= HitSparkBudget.GLOBAL_PER_SECOND, "global cap holds with " + HitSparkBudget.TABLE * 3 + " chunks");
        assertTrue(hot.worst <= HitSparkBudget.CHUNK_PER_SECOND, "per-chunk cap holds with a full table: " + hot.worst);
    }

    @Test
    void aClockThatJumpsBackOrIsNotANumberGrantsNothingExtra() {
        HitSparkBudget b = new HitSparkBudget();
        int spent = 0;
        for (int i = 0; i < 100; i++)
            spent += b.take(1, 1, 5f, 6);
        assertTrue(spent <= HitSparkBudget.CHUNK_PER_SECOND);
        assertEquals(0, b.take(1, 1, 1f, 6), "time going backwards refills nothing");
        assertEquals(0, b.take(1, 1, Float.NaN, 6), "NaN time refills nothing");
        assertEquals(0, b.take(1, 1, 5f, 6));
    }

    @Test
    void resetForgetsEverything() {
        HitSparkBudget b = new HitSparkBudget();
        for (int i = 0; i < 100; i++)
            b.take(2, 2, 0f, 6);
        b.reset();
        assertEquals(6, b.take(2, 2, 0f, 6));
    }
}
