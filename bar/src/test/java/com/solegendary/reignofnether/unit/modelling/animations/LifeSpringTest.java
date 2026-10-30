package com.solegendary.reignofnether.unit.modelling.animations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The living army's springs (design/living_army.md, section 1): {@link Life#spring} settles a critically damped slot
 * with no overshoot, rocks an underdamped one once or twice before still, and never blows up whatever the step;
 * {@link Life#springChain} makes the tip arrive after the root. These are the "overshoot and settle" every lean,
 * flinch, stop and tail in the living army is built from.
 */
class LifeSpringTest {

    private static float[] slot(int n) {
        return new float[n * Life.SPRING];
    }

    @Test
    void criticalSpringReachesTheTargetWithoutOvershoot() {
        float[] s = slot(1);
        float max = 0f;
        for (int t = 0; t < 60; t++) {
            Life.spring(s, 0, 1f, 0.5f, 1f, 1f);
            max = Math.max(max, s[0]);
            if (t == 19)
                assertEquals(1f, s[0], 0.01f, "settled by tick 20");
        }
        assertTrue(max <= 1.0001f, "no overshoot at critical damping: " + max);
    }

    @Test
    void underdampedSpringRocksThenSettles() {
        float[] s = slot(1);
        float max = 0f;
        for (int t = 0; t < 40; t++) {
            Life.spring(s, 0, 1f, 0.5f, 0.45f, 1f);
            max = Math.max(max, s[0]);
        }
        assertTrue(max > 1.02f, "an underdamped spring overshoots: " + max);
        assertEquals(1f, s[0], 0.02f, "and is still by tick 40");
    }

    @Test
    void anyStepSizeStaysBounded() {
        for (float dt : new float[] {0.1f, 1f, 5f, 50f}) {
            float[] s = slot(1);
            for (int t = 0; t < 200; t++) {
                Life.spring(s, 0, 1f, 2f, 1f, dt);
                assertTrue(s[0] >= -0.01f && s[0] <= 1.01f, "dt " + dt + " tick " + t + ": " + s[0]);
            }
        }
    }

    @Test
    void springAtLerpsBetweenTheLastTwoTicks() {
        float[] s = slot(1);
        Life.spring(s, 0, 1f, 0.5f, 1f, 1f);
        Life.spring(s, 0, 1f, 0.5f, 1f, 1f);
        float prev = s[2], now = s[0];
        assertEquals(prev, Life.springAt(s, 0, 0f), 1e-6f);
        assertEquals(now, Life.springAt(s, 0, 1f), 1e-6f);
        assertEquals((prev + now) / 2f, Life.springAt(s, 0, 0.5f), 1e-6f);
    }

    @Test
    void chainTipLagsTheRootThenRests() {
        int links = 4;
        float[] s = slot(links);
        for (int t = 0; t < 80; t++) {
            Life.springChain(s, 0, links, 1f, 0.5f, 1f, 1f);
            if (t == 4)
                assertTrue(s[0] > s[3 * Life.SPRING], "the tip arrives after the root: " + s[0] + " vs " + s[3 * Life.SPRING]);
        }
        for (int k = 0; k < links; k++)
            assertEquals(1f, s[k * Life.SPRING], 0.02f, "link " + k + " at rest");
    }
}
