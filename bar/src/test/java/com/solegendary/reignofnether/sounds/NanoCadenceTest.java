package com.solegendary.reignofnether.sounds;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The builders' construction sound (lovish, Oct 29: "u can hear workers buzzing as they build at its annoying"). Every
 * builder's beam sends a NANO event every BarFx.NANO_INTERVAL (8) ticks and the client played one granule per 10-tick
 * gap - so with anyone building, a granule exactly twice a second, all match long: a metronome, which the ear hears as
 * a buzz. NanoCadence keeps it sparse and irregular, and no busier with more builders.
 */
class NanoCadenceTest {

    private static final int EVENT_EVERY = 8;   // BarFx.NANO_INTERVAL

    /** Ticks at which a granule plays over {@code seconds} with {@code builders} beams (staggered), seeded. */
    private static List<Long> simulate(int builders, int seconds, long seed) {
        Random rng = new Random(seed);
        List<Long> plays = new ArrayList<>();
        long last = Long.MIN_VALUE / 2;
        int gap = 0;
        for (long t = 0; t < seconds * 20L; t++)
            for (int b = 0; b < builders; b++)
                if ((t + b * 3) % EVENT_EVERY == 0 && NanoCadence.shouldPlay(t, last, gap)) {
                    plays.add(t);
                    last = t;
                    gap = NanoCadence.nextGap(rng.nextFloat());
                }
        return plays;
    }

    @Test
    void busyBuildersPlayLessThanOnceASecond() {
        for (int builders : new int[] { 1, 3, 8, 20 }) {
            List<Long> p = simulate(builders, 60, 7L);
            System.out.printf("%2d builders: %d granules a minute%n", builders, p.size());
            assertTrue(p.size() <= 50, builders + " builders: " + p.size() + " granules a minute (was 120)");
            assertTrue(p.size() >= 15, builders + " builders: " + p.size() + " a minute - the work should still be heard");
        }
    }

    @Test
    void moreBuildersAreNotLouderThanAFew() {
        int three = simulate(3, 60, 11L).size(), twenty = simulate(20, 60, 11L).size();
        assertTrue(twenty <= three * 1.4, "20 builders " + twenty + " vs 3 builders " + three);
    }

    @Test
    void theGapsAreIrregular() {
        List<Long> p = simulate(8, 120, 3L);
        double mean = 0;
        for (int i = 1; i < p.size(); i++)
            mean += p.get(i) - p.get(i - 1);
        mean /= p.size() - 1;
        double var = 0;
        for (int i = 1; i < p.size(); i++)
            var += Math.pow(p.get(i) - p.get(i - 1) - mean, 2);
        double cv = Math.sqrt(var / (p.size() - 2)) / mean;
        System.out.printf("8 builders: mean gap %.1f ticks, variation %.2f%n", mean, cv);
        assertTrue(cv >= 0.25, "gaps vary by only " + cv + " of their mean - a metronome");
        assertTrue(mean >= NanoCadence.MIN_GAP, "mean gap " + mean);
    }
}
