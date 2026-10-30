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

    // ---- lovish after a playtest, Oct 30: "the wings looked really choppy as well as the model leaned to the side
    // while attacking and flying in a weird way" -------------------------------------------------------------------

    /** The per-tick speed a client read off a cruising dragon tracked every 3rd tick: packets late, then bunched. */
    private static final float[] JITTER = { 0.17f, 0.17f, 0.17f, 0f, 0.34f, 0.17f, 0.17f };

    /**
     * Choppy wings: the drivers read the speed off the client's own tick positions, and a stalled tick followed by a
     * doubled one read as braking (the flare threw the wings forward) and swung the glide in and out. A cruise that
     * stutters like that must not flare and must keep its glide steady (sampled at 3 frames a tick); a real brake
     * still flares.
     */
    @Test
    void packetJitterNeitherFlaresNorFlickersTheGlide() {
        float[] st = Dragon.newState(0f);
        float maxFlare = 0f, maxGlideStep = 0f, lastGlide = Float.NaN;
        for (int t = 0; t < 240; t++) {
            float speed = JITTER[t % JITTER.length];
            for (int f = 0; f < 3; f++)
                Dragon.drivers(st, P, speed, 0f, 1f / 3f);
            if (t >= 80) {
                maxFlare = Math.max(maxFlare, st[Dragon.ST_FLARE]);
                if (!Float.isNaN(lastGlide))
                    maxGlideStep = Math.max(maxGlideStep, Math.abs(st[Dragon.ST_GLIDE] - lastGlide));
            }
            lastGlide = st[Dragon.ST_GLIDE];
        }
        assertTrue(maxFlare <= 0.1f, "a stuttering cruise flared to " + maxFlare);
        assertTrue(maxGlideStep <= 0.02f, "the glide weight swung " + maxGlideStep + " in one tick");
        // a real brake: 0.17 b/t down to a hover at the steering's 0.012 a tick
        float[] br = Dragon.newState(0f);
        for (int t = 0; t < 60; t++)
            Dragon.drivers(br, P, 0.17f, 0f, 1f);
        float peak = 0f;
        for (int t = 0; t < 40; t++) {
            Dragon.drivers(br, P, Math.max(0f, 0.17f - 0.012f * t), 0f, 1f);
            peak = Math.max(peak, br[Dragon.ST_FLARE]);
        }
        assertTrue(peak >= 0.5f, "a real brake still flares (peak " + peak + ")");
    }

    /**
     * The lean while attacking: hovering, the dragon turns to face its target at 6 deg a tick, and the bank read that
     * turn as a flight turn - a steady roll of ~13 deg while it turned on the spot. A dragon turning in place barely
     * rolls; one turning in flight still banks into the turn.
     */
    @Test
    void hoveringWhileTurningToATargetBarelyLeans() {
        float[] st = Dragon.newState(0f);
        float yaw = 0f, maxRoll = 0f;
        for (int t = 0; t < 60; t++)
            for (int f = 0; f < 3; f++) {
                yaw += 2f;
                Dragon.bankStep(st, P, yaw, 1f / 3f, P.bankHover, 0f);
                maxRoll = Math.max(maxRoll, Math.abs(st[Dragon.ST_BANK]));
            }
        assertTrue(maxRoll <= 3f, "turning on the spot rolled " + maxRoll + " deg");

        float[] fl = Dragon.newState(0f);
        yaw = 0f;
        for (int t = 0; t < 60; t++)
            for (int f = 0; f < 3; f++) {
                yaw += 4f / 3f;
                Dragon.bankStep(fl, P, yaw, 1f / 3f, 1f, 0.167f);
            }
        assertTrue(fl[Dragon.ST_BANK] >= 18f, "a cruise turn at 4 deg/tick banks (" + fl[Dragon.ST_BANK] + ")");
    }
}
