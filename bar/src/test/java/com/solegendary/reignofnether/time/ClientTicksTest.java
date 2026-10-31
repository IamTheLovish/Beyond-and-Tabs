package com.solegendary.reignofnether.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client's own clock: one count per running client tick, still while paused, never back. */
class ClientTicksTest {

    @Test
    void countsEveryRunningTickAndHoldsWhilePaused() {
        long t0 = ClientTicks.now();
        for (int i = 0; i < 60; i++)
            ClientTicks.step(true);
        assertEquals(t0 + 60, ClientTicks.now(), "three seconds of play: 60 ticks, whatever the day does");
        for (int i = 0; i < 40; i++)
            ClientTicks.step(false);
        assertEquals(t0 + 60, ClientTicks.now(), "paused (or no level): the clock holds");
        ClientTicks.step(true);
        assertEquals(t0 + 61, ClientTicks.now(), "and goes on from where it stood");
    }

    @Test
    void anAnimationNeverRunsBack() {
        // a frame's time is now + partial: across a tick it goes on from where the last frame left it, where
        // gameTime + partial at the default day speed fell back by up to a tick at two ticks in every three
        while (ClientTicks.now() % ClientTicks.WRAP > ClientTicks.WRAP - 100)   // clear of the hourly wrap
            ClientTicks.step(true);
        float last = ClientTicks.at(0f);
        for (int tick = 0; tick < 30; tick++) {
            for (float p = 0f; p < 1f; p += 0.2f) {
                float t = ClientTicks.at(p);
                assertTrue(t >= last, "frame time went back: " + last + " -> " + t);
                last = t;
            }
            ClientTicks.step(true);
        }
    }

    @Test
    void anAnimationsTimeKeepsItsFractionAfterHours() {
        // unwrapped, a float of the count kept no fraction after ~15 hours in a level (lovish keeps the game open)
        for (long i = 0; i < 20 * 3600 * 20 + 7; i++)
            ClientTicks.step(true);
        float t = ClientTicks.at(0.25f);
        assertTrue(t >= 0f && t < ClientTicks.WRAP + 1, "wrapped to the hour: " + t);
        assertEquals(0.25f, t - (float) Math.floor(t), 1e-3f, "the frame's fraction survives");
    }

    @Test
    void anEveryNGateFiresOnceEveryNTicks() {
        // the gates (now % 20 == 0) now fire once a second; on the day clock they fired on every tick of a stretch
        // where the day stood on a multiple of 20, and never otherwise
        int fired = 0;
        for (int i = 0; i < 200; i++) {
            ClientTicks.step(true);
            if (ClientTicks.now() % 20 == 0)
                fired++;
        }
        assertEquals(10, fired);
    }
}
