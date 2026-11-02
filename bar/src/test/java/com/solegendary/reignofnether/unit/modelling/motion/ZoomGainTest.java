package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Realistic up close, gently larger from the RTS camera (design/living_army.md, section 2; lovish Oct 27): x1 at
 * zoom 20 and closer, x(1+k) at zoom 70 and farther, rising without a dip between.
 */
class ZoomGainTest {

    @Test
    void realisticUpClose() {
        assertEquals(1f, ZoomGain.gain(5f, 2f), 1e-6f);
        assertEquals(1f, ZoomGain.gain(20f, 2f), 1e-6f);
    }

    @Test
    void fullGainFromTheRtsCamera() {
        assertEquals(3f, ZoomGain.gain(70f, 2f), 1e-6f);
        assertEquals(3f, ZoomGain.gain(200f, 2f), 1e-6f);
    }

    @Test
    void risesSteadilyBetween() {
        float last = ZoomGain.gain(20f, 1.5f);
        for (float z = 20.5f; z <= 70f; z += 0.5f) {
            float g = ZoomGain.gain(z, 1.5f);
            assertTrue(g >= last, "monotone at zoom " + z);
            last = g;
        }
    }

    @Test
    void theSpecsGains() {
        assertEquals(1.5f, ZoomGain.K_BREATH);
        assertEquals(1.0f, ZoomGain.K_WEIGHT);
        assertEquals(1.3f, ZoomGain.K_FIDGET);
        assertEquals(1.5f, ZoomGain.K_LEAN);
        assertEquals(1.5f, ZoomGain.K_ACCEL);
        assertEquals(2.0f, ZoomGain.K_FLINCH);
        assertEquals(1.2f, ZoomGain.K_SECONDARY);
    }
}
