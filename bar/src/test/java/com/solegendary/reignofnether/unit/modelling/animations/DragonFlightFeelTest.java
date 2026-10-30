package com.solegendary.reignofnether.unit.modelling.animations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dragon's wings and head answer what the flight does (lovish, Oct 30: "make sure were refining the animation for
 * the dragon"; after the flight itself was made smooth in #410 / #417). Three things the rig did not do:
 * <ul>
 * <li>a climb beat at the cruise's slow rate (it only beat a little wider): a dragon labouring up over a keep should
 *     beat FASTER - {@link Dragon#beatTicks};</li>
 * <li>a dive counted as cruising (its bursts and glides, wings out): a stoop folds the wings back -
 *     {@link Dragon#stoop};</li>
 * <li>braking moved only the wings (the flare): the long neck should carry on a little and nod down as the body
 *     slows, then settle - {@link Dragon#brakeNod}.</li>
 * </ul>
 */
class DragonFlightFeelTest {

    private static final Dragon.Params P = new Dragon.Params();

    @Test
    void aClimbBeatsFasterThanLevelFlight() {
        float level = Dragon.beatTicks(P, 0f, 0f, 0f);
        assertEquals(P.beatTicks, level, 1e-3f, "level flight beats at the cruise rate");
        assertTrue(Dragon.beatTicks(P, 0f, 0f, 1f) <= 0.75f * level, "a full climb beats at least a third faster");
        assertTrue(Dragon.beatTicks(P, 0f, 0f, 0.5f) < level, "half a climb beats faster too");
        assertTrue(Dragon.beatTicks(P, 0f, 0f, 0.5f) > Dragon.beatTicks(P, 0f, 0f, 1f), "and less than a full climb");
        assertEquals(level, Dragon.beatTicks(P, 0f, 0f, -1f), 1e-3f, "a dive does not beat faster (it stoops)");
    }

    @Test
    void aDiveStoopsWithItsWingsSweptBack() {
        assertEquals(0f, Dragon.stoop(0f), 1e-6f, "level flight: no stoop");
        assertEquals(0f, Dragon.stoop(0.8f), 1e-6f, "a climb: no stoop");
        assertTrue(Dragon.stoop(-1f) >= 0.9f, "a full dive is a stoop");
        assertTrue(Dragon.stoop(-0.3f) < Dragon.stoop(-0.7f), "the steeper the dive, the more it folds");
    }

    @Test
    void theHeadNodsWhileItBrakesAndSettlesAfter() {
        // braking in blocks per tick per tick, as Dragon.animate smooths it (the steering brakes at up to ~0.012)
        assertTrue(Dragon.brakeNod(P, 0.010f) >= 4f, "braking hard: the head carries on and nods down a few degrees");
        assertTrue(Dragon.brakeNod(P, 0.004f) < Dragon.brakeNod(P, 0.010f), "a gentler brake nods less");
        assertEquals(0f, Dragon.brakeNod(P, 0f), 1e-6f, "steady flight: no nod");
        assertEquals(0f, Dragon.brakeNod(P, -0.008f), 1e-6f, "speeding up: no nod");
        assertTrue(Dragon.brakeNod(P, 1f) <= 12f, "capped: a sudden stop never folds the neck");
    }
}
