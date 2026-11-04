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
        return simulate(builders, 0, seconds, seed);
    }

    /**
     * Ticks at which a granule of the builders in earshot plays over {@code seconds}: {@code near} beams whose granule
     * plays and {@code far} beams of the same faction whose granule SfxClient.playAt drops (out of reach), interleaved,
     * through one NanoCadence.Loop as SfxClient offers them. Seeded.
     */
    private static List<Long> simulate(int near, int far, int seconds, long seed) {
        Random rng = new Random(seed);
        NanoCadence.Loop loop = new NanoCadence.Loop();
        List<Long> plays = new ArrayList<>();
        for (long t = 1000; t < 1000 + seconds * 20L; t++)   // SoundTicks: a large real-time tick, never near 0
            for (int b = 0; b < near + far; b++) {
                if ((t + b * 3) % EVENT_EVERY != 0)
                    continue;
                boolean inEarshot = b % (far + 1) == 0 && b / (far + 1) < near;   // far beams sit between the near ones
                if (loop.offer(t, () -> inEarshot, () -> NanoCadence.nextGap(rng.nextFloat())))
                    plays.add(t);
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

    /**
     * Review r1 on PR 409: same-faction builders out of earshot (BarFx sends NANO up to 200 blocks; reach is 48) must not
     * use up the loop of the builder in view. A dropped granule books no gap, so the builder in earshot is heard exactly
     * as often with ten far builders as alone (the gap draws happen only on plays, so the seeded runs match tick for
     * tick). Booking before playAt gave 28 granules a minute alone, 2.7 with ten far builders.
     */
    @Test
    void farBuildersDoNotTakeTheNearBuildersGranules() {
        List<Long> alone = simulate(1, 0, 60, 5L);
        for (int far : new int[] { 1, 3, 10 }) {
            List<Long> withFar = simulate(1, far, 60, 5L);
            System.out.printf("1 builder in earshot, %2d far: %d granules a minute (alone %d)%n", far, withFar.size(),
                alone.size());
            assertTrue(withFar.size() >= 15, "1 builder in earshot with " + far + " far: " + withFar.size()
                + " granules a minute - the work in view should still be heard");
            assertTrue(withFar.equals(alone), "far builders changed the near builder's granules: " + withFar.size()
                + " vs " + alone.size() + " alone");
        }
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
