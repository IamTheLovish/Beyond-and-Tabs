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
        assertEquals(level, Dragon.beatTicks(P, 0f, 0f, -1f), 1e-3f, "a dive does not beat faster");
        assertTrue(Dragon.beatTicks(P, 0f, 0f, 1f) >= 0.6f * level, "but not frantically: at most 40% quicker");
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

    // ---- review r1 of #440: the rest of the drivers through the same smoothed path --------------------------------

    /** A cruise at speed (b/t) read off a client tracked every 3rd tick, each tick sampled at 3 frames; returns the
     *  largest flare and the largest head nod after a 60-tick warm-up. */
    private static float[] jitteredCruise(float speed) {
        float[] st = Dragon.newState(0f);
        st[Dragon.ST_SPEED] = speed;
        float[] mult = { 1f, 1f, 1f, 0f, 2f, 1f, 1f };
        float flare = 0f, nod = 0f;
        for (int t = 0; t < 300; t++) {
            for (int f = 0; f < 3; f++)
                Dragon.drivers(st, P, speed * mult[t % mult.length], 0f, 1f / 3f);
            if (t > 60) {
                flare = Math.max(flare, st[Dragon.ST_FLARE]);
                nod = Math.max(nod, Dragon.brakeNod(P, st[Dragon.ST_BRAKE]));
            }
        }
        return new float[] { flare, nod };
    }

    @Test
    void aSlowCruiseThatStuttersNeitherFlaresNorBobsTheHead() {
        for (float speed : new float[] { 0.11f, 0.12f, 0.13f, 0.14f, 0.17f }) {
            float[] r = jitteredCruise(speed);
            assertTrue(r[0] <= 0.1f, "a stuttering cruise at " + speed + " b/t flared to " + r[0]);
            assertTrue(r[1] <= 0.5f, "a stuttering cruise at " + speed + " b/t nodded the head " + r[1] + " deg");
        }
    }

    /** Brakes from cruise at decel b/t a tick; returns {the tick the flare passes 0.5, the speed then, the largest nod}. */
    private static float[] brakeFrom(float decel) {
        float[] st = Dragon.newState(0f);
        for (int t = 0; t < 60; t++)
            Dragon.drivers(st, P, 0.17f, 0f, 1f);
        float start = -1f, at = -1f, nod = 0f;
        for (int t = 0; t < 120; t++) {
            float speed = Math.max(0f, 0.17f - decel * t);
            Dragon.drivers(st, P, speed, 0f, 1f);
            nod = Math.max(nod, Dragon.brakeNod(P, st[Dragon.ST_BRAKE]));
            if (start < 0f && st[Dragon.ST_FLARE] > 0.5f) {
                start = t;
                at = speed;
            }
        }
        return new float[] { start, at, nod };
    }

    @Test
    void theFlareComesWhileItBrakesNotOnlyOnceItHasStopped() {
        float[] hard = brakeFrom(0.012f);
        assertTrue(hard[0] >= 0f && hard[0] <= 13f && hard[1] > 0.02f,
            "a hard brake (0.012 a tick) flares while still moving, by tick 13: tick " + hard[0] + " at " + hard[1]);
        assertTrue(hard[2] >= 4f, "a hard brake nods the head (" + hard[2] + " deg)");
        float[] easy = brakeFrom(0.0028f);
        assertTrue(easy[0] >= 0f && easy[0] <= 30f && easy[1] > 0.05f,
            "a normal approach (0.0028 a tick) flares on the way in, by tick 30: tick " + easy[0] + " at " + easy[1]);
    }

    /**
     * A dragon that comes into tracking range mid-cruise: its state is new (the smoothed speed starts at 0, as animate
     * makes it - the first frame's tick delta is 0, xo == x) and its first tick is a stalled one. It must not play the
     * take-off (review r2 of #440: ~2.5 s of it with a zero seed).
     */
    @Test
    void aDragonThatFliesIntoViewDoesNotPlayTheTakeOff() {
        float[] st = Dragon.newState(0f);
        st[Dragon.ST_HOVER] = 0f;
        float[] mult = { 1f, 1f, 1f, 0f, 2f, 1f, 1f };
        float worst = 0f;
        for (int t = 0; t < 90; t++) {
            for (int f = 0; f < 3; f++)
                Dragon.drivers(st, P, 0.17f * mult[(t + 3) % mult.length], 0f, 1f / 3f, t);
            worst = Math.max(worst, st[Dragon.ST_TAKEOFF]);
        }
        assertTrue(worst <= 0.1f, "a dragon cruising into view (stuttering) played the take-off at " + worst);
    }
}
