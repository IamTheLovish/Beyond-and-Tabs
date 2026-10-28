package com.solegendary.reignofnether.unit.modelling.animations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rig's take-off and flare triggers ({@link Dragon#wantsTakeoff}, {@link Dragon#wantsFlare}). A client's
 * tickCount starts at 0 whenever an entity comes into tracking range, so "the first 60 ticks of life" alone made a
 * dragon that flew into view (or that the camera panned to) play the take-off - legs dangling, body pitched up 25 -
 * for three seconds in mid-cruise. And the flare (wings forward as air brakes) only came once the dragon had already
 * stopped (speed under 0.02), i.e. after the braking it is meant to show.
 */
class DragonStatesTest {

    private static final Dragon.Params P = new Dragon.Params();

    @Test
    void aDragonThatFliesIntoViewDoesNotTakeOff() {
        assertFalse(Dragon.wantsTakeoff(P, 0, 0.16f, 0f), "cruising into tracking range at tickCount 0");
        assertFalse(Dragon.wantsTakeoff(P, 30, 0.12f, 0f), "still cruising 30 ticks after it was first seen");
    }

    @Test
    void aFreshDragonAndAHardClimbFromAStandstillTakeOff() {
        assertTrue(Dragon.wantsTakeoff(P, 0, 0f, 0f), "just raised, standing still");
        assertTrue(Dragon.wantsTakeoff(P, 40, 0.05f, 0f), "just raised, picking up speed");
        assertTrue(Dragon.wantsTakeoff(P, 5000, 0.03f, 0.8f), "climbing hard from a hover");
        assertFalse(Dragon.wantsTakeoff(P, 5000, 0.03f, 0f), "an old dragon hovering is not taking off");
    }

    @Test
    void theFlareShowsWhileItBrakesNotAfter() {
        // decel in blocks per tick per tick (the steering brakes at up to ~0.012)
        assertTrue(Dragon.wantsFlare(0.09f, 0.006f, 0f), "braking hard at mid speed, not yet hovering");
        assertFalse(Dragon.wantsFlare(0.16f, 0.006f, 0f), "slowing a little at cruise speed is not a flare");
        assertFalse(Dragon.wantsFlare(0.09f, 0.0f, 0f), "steady at mid speed");
        assertTrue(Dragon.wantsFlare(0.01f, 0f, 0.5f), "just stopped, the hover still coming in (the old trigger)");
        assertFalse(Dragon.wantsFlare(0.01f, 0f, 0.95f), "settled in the hover");
    }
}
