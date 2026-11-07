package com.solegendary.reignofnether.unit.modelling.motion;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The death of a hovering body (DeathFall#poseHover, the Light Grid's drones): it does not topple over its feet like a
 * man. Its lift cuts out and it drops from its hover height to the ground - slow off the top, fastest at the impact,
 * down before the server removes it (deathTime 20) - tipping away from the killing blow only as far as a crashed hull
 * lies, one small bounce, then rest on the ground, never through it and never floating above it. Same latch as the
 * human fall, so the body is kept (Corpses) and the faction's finish plays on it (the Light Grid's derez).
 */
class DeathFallHoverTest {

    private static final int B = 0;
    private static final float D = Mth.DEG_TO_RAD;
    private static final float HOVER = 10f, HALF = 6f;   // the Builder Drone: hull bottom 10 px up, 6 px half wide

    private static float[] dying(float dirDeg, boolean heavy, int id) {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, B, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        Kinematics.onHit(s, B, dirDeg * D);
        s[B + AnimState.HIT_SIZE] = heavy ? AnimState.STAGGER : AnimState.FLINCH;
        s[B + AnimState.HIT_AGE] = 1;
        DeathFall.latch(s, B, id, 1);
        return s;
    }

    private static float[] pose(float[] s, int id, float t) {
        float[] out = new float[DeathFall.OUT];
        DeathFall.poseHover(s, B, id, t, HOVER, HALF, out);
        return out;
    }

    private static float at(float[] out, int bone, int ch) {
        return out[bone * Motion.CH + ch];
    }

    private static float tiltDeg(float[] out) {
        float rx = at(out, Motion.ROOT, Motion.RX), rz = at(out, Motion.ROOT, Motion.RZ);
        return Mth.sqrt(rx * rx + rz * rz) / D;
    }

    /** Where the hull's lowest edge sits above the ground, px, for the pose's drop (TY) and tilt (MotionPass.tilt). */
    private static float lowEdge(float[] out) {
        float th = tiltDeg(out) * D;
        return HOVER * Mth.cos(th) - HALF * Mth.sin(th) + at(out, Motion.ROOT, Motion.TY);
    }

    @Test
    void itDropsToTheGroundBeforeTheServerRemovesIt() {
        for (int id = 0; id < 60; id++)
            for (boolean heavy : new boolean[] {false, true}) {
                float[] s = dying(id * 37f, heavy, id);
                float kind = s[B + AnimState.DEATH_KIND];
                float impact = DeathFall.hoverImpactTick(kind, id);
                assertTrue(impact > 4f && impact <= 16f, "impact at " + impact + " ticks");
                float prev = -1f;
                for (float t = 0f; t <= impact; t += 0.5f) {
                    float drop = DeathFall.hoverDropPx(t, kind, id, HOVER);
                    assertTrue(drop >= prev - 1e-4f, "the drop never climbs back before the impact (t " + t + ")");
                    prev = drop;
                }
                assertEquals(0f, DeathFall.hoverDropPx(0f, kind, id, HOVER), 1e-4f, "nothing has dropped at t 0");
                assertEquals(HOVER, DeathFall.hoverDropPx(impact, kind, id, HOVER), 0.05f, "down at the impact");
                assertEquals(HOVER, DeathFall.hoverDropPx(18f, kind, id, HOVER), 0.05f, "down by tick 18");
                // gravity: the first half of the fall time covers less than half the drop
                float half = DeathFall.hoverDropPx(DeathFall.reelTicks(kind) + 0.5f
                    * (impact - DeathFall.reelTicks(kind)), kind, id, HOVER);
                assertTrue(half < 0.45f * HOVER, "the drop should accelerate like a falling body, half way " + half);
            }
    }

    @Test
    void itTipsAwayFromTheBlowOnlyAsFarAsACrashedHullLies() {
        for (int id = 0; id < 50; id++) {
            float[] front = pose(dying(0f, false, id), id, 40f), right = pose(dying(90f, false, id), id, 40f);
            float tf = tiltDeg(front), tr = tiltDeg(right);
            assertTrue(tf >= 15f && tf <= 40f, "a crashed hull lies tipped 15-40 degrees, got " + tf);
            assertTrue(at(front, Motion.ROOT, Motion.RX) < 0f, "hit in the front: tipped back");
            assertTrue(at(right, Motion.ROOT, Motion.RZ) < 0f, "hit from its right: tipped to its left");
            assertTrue(tr >= 15f && tr <= 40f, "side crash tilt " + tr);
        }
    }

    @Test
    void itLandsOnTheGroundNeverThroughItNorFloatingAboveIt() {
        for (int id = 0; id < 40; id++) {
            float[] s = dying(id * 53f, id % 3 == 0, id);
            for (float t = 0f; t <= 60f; t += 0.5f) {
                float edge = lowEdge(pose(s, id, t));
                assertTrue(edge >= -0.3f, "the hull sank " + (-edge) + " px into the ground at t " + t);
            }
            assertEquals(0f, lowEdge(pose(s, id, 40f)), 0.3f, "at rest the hull's edge lies on the ground");
        }
    }

    @Test
    void itBouncesOnceThenRests() {
        for (int id = 0; id < 40; id++) {
            float[] s = dying(0f, false, id);
            float impact = DeathFall.hoverImpactTick(s[B + AnimState.DEATH_KIND], id);
            float rest = lowEdge(pose(s, id, 40f)), peak = 0f;
            for (float t = impact; t <= impact + 10f; t += 0.25f)
                peak = Math.max(peak, lowEdge(pose(s, id, t)) - rest);
            assertTrue(peak >= 0.4f && peak <= 3f, "one small bounce off the ground, peak " + peak + " px");
            assertEquals(rest, lowEdge(pose(s, id, impact + 14f)), 0.1f, "settled 14 ticks after the impact");
        }
    }

    @Test
    void nothingLatchedNothingMoves() {
        float[] s = new float[AnimState.STRIDE];
        float[] out = pose(s, 3, 10f);
        for (float v : out)
            assertEquals(0f, v, 0f);
    }
}
