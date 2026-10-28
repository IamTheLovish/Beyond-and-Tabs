package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No two units in step (design/living_army.md, section 2): every unit's tempo and amplitude are its own, seeded by its
 * entity id, spread across the whole +-8% / +-15% band - so neighbouring ids in a freshly trained block differ too.
 */
class DesyncTest {

    @Test
    void tempoSpreadsAcrossItsBand() {
        Set<Float> seen = new HashSet<>();
        float min = 9f, max = -9f;
        for (int id = 0; id < 100; id++) {
            float t = Desync.tempo(id);
            assertTrue(t >= 0.92f && t <= 1.08f, "id " + id + ": " + t);
            seen.add(t);
            min = Math.min(min, t);
            max = Math.max(max, t);
        }
        assertEquals(100, seen.size(), "every id its own tempo");
        assertTrue(min < 0.93f && max > 1.07f, "the whole band is used: " + min + ".." + max);
    }

    @Test
    void amplitudeSpreadsAcrossItsBand() {
        Set<Float> seen = new HashSet<>();
        float min = 9f, max = -9f;
        for (int id = 0; id < 100; id++) {
            float a = Desync.amp(id);
            assertTrue(a >= 0.85f && a <= 1.15f, "id " + id + ": " + a);
            seen.add(a);
            min = Math.min(min, a);
            max = Math.max(max, a);
        }
        assertEquals(100, seen.size(), "every id its own amplitude");
        assertTrue(min < 0.87f && max > 1.13f, "the whole band is used: " + min + ".." + max);
    }

    @Test
    void hashIsInUnitRangeAndSaltSeparates() {
        int same = 0;
        for (int id = 0; id < 1000; id++) {
            float a = Desync.hash01(id, 0), b = Desync.hash01(id, 1);
            assertTrue(a >= 0f && a < 1f && b >= 0f && b < 1f);
            if (Math.abs(a - b) < 1e-3f)
                same++;
        }
        assertTrue(same < 10, "a salt gives an independent value: " + same);
    }
}
