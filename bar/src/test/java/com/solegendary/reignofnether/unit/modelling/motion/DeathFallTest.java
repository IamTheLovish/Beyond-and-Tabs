package com.solegendary.reignofnether.unit.modelling.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.models.gen.CrossbowLevyModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The death-pose contract (design/animation-foundation-audit.md, section 3, item 3c): a human falls away from the blow
 * that killed it, by the size of that blow, lands before the server removes it (deathTime 20), rests lying on the
 * ground rather than through it, and the client's body sinks only after the hold. The killing blow is latched once.
 */
class DeathFallTest {

    private static final int B = 0;
    private static final float D = Mth.DEG_TO_RAD;

    /** A unit's slot hit `ticksAgo` ticks before it starts dying, from `dirDeg` (0 = the front), heavy or not. */
    private static float[] dying(float dirDeg, boolean heavy, int ticksAgo, int id) {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, B, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        Kinematics.onHit(s, B, dirDeg * D);
        s[B + AnimState.HIT_SIZE] = heavy ? AnimState.STAGGER : AnimState.FLINCH;
        s[B + AnimState.HIT_AGE] = ticksAgo;
        DeathFall.latch(s, B, id, 1);
        return s;
    }

    private static float[] pose(float[] s, int id, float t, boolean jointed) {
        float[] out = new float[DeathFall.OUT];
        DeathFall.pose(s, B, id, t, jointed, out);
        return out;
    }

    private static float at(float[] out, int bone, int ch) {
        return out[bone * Motion.CH + ch];
    }

    /** The body's tilt at rest, degrees: forwards (RX) and to its right (RZ). */
    private static float[] restTilt(float dirDeg, int id) {
        float[] out = pose(dying(dirDeg, false, 1, id), id, 40f, true);
        return new float[] {at(out, Motion.ROOT, Motion.RX) / D, at(out, Motion.ROOT, Motion.RZ) / D};
    }

    @Test
    void itFallsAwayFromTheBlow() {
        for (int id = 0; id < 50; id++) {
            float[] front = restTilt(0f, id), right = restTilt(90f, id), left = restTilt(-90f, id), back = restTilt(180f, id);
            assertTrue(front[0] < -80f, "hit in the chest: over backwards " + front[0]);
            assertTrue(back[0] > 80f, "hit in the back: on its face " + back[0]);
            assertTrue(right[1] < -80f, "hit from its right: to its left " + right[1]);
            assertTrue(left[1] > 80f, "hit from its left: to its right " + left[1]);
            float jitter = 90f * Mth.sin(DeathFall.BLOW_JITTER_DEG * D) + 0.5f;
            assertTrue(Math.abs(front[1]) <= jitter, "a blow's direction holds to its jitter: " + front[1]);
        }
    }

    @Test
    void twoMenCutDownSideBySideDoNotLandAlike() {
        float[] a = restTilt(0f, 11), b = restTilt(0f, 12);
        assertTrue(Math.abs(a[1] - b[1]) > 1f || Math.abs(a[0] - b[0]) > 1f, "seeded jitter per unit");
        float[] again = restTilt(0f, 11);
        assertEquals(a[0], again[0], 0f, "and the same unit always lands the same");
        assertEquals(a[1], again[1], 0f);
    }

    @Test
    void withNoBlowItCrumplesForwards() {
        for (int id = 0; id < 50; id++) {
            float[] s = dying(180f, false, 200, id);   // the last hit is long past: fire, poison, a spell's tick
            assertEquals(DeathFall.NO_BLOW, s[B + AnimState.DEATH_KIND]);
            float[] out = pose(s, id, 40f, true);
            float rx = at(out, Motion.ROOT, Motion.RX) / D;
            assertTrue(rx > 90f * Mth.cos(DeathFall.CRUMPLE_SPREAD_DEG * D) - 0.5f, "forwards, give or take: " + rx);
        }
    }

    @Test
    void everyFallLandsBeforeTheServerRemovesTheUnit() {
        for (float kind : new float[] {DeathFall.NO_BLOW, DeathFall.BLOW, DeathFall.HEAVY})
            for (int id = 0; id < 1000; id++) {
                float t1 = DeathFall.impactTick(kind, id);
                assertTrue(t1 <= 18f, "lands by tick 18 (removed at 20): " + t1);
                assertEquals(90f, DeathFall.tiltDeg(t1 + 0.001f, kind, id), 0.5f, "down at the impact");
            }
    }

    @Test
    void theTiltTipsSlowlyLandsFastBouncesOnceAndRests() {
        for (float kind : new float[] {DeathFall.NO_BLOW, DeathFall.BLOW, DeathFall.HEAVY}) {
            int id = 7;
            float t1 = DeathFall.impactTick(kind, id);
            float prev = 0f, maxStep = 0f, firstStep = -1f;
            for (float t = 0f; t <= t1; t += 0.25f) {
                float a = DeathFall.tiltDeg(t, kind, id);
                assertTrue(a >= prev - 1e-3f, "never rises before the impact: " + t);
                if (t > DeathFall.reelTicks(kind) && firstStep < 0f)
                    firstStep = a - prev;
                maxStep = Math.max(maxStep, a - prev);
                prev = a;
            }
            assertTrue(maxStep > 3f * firstStep, "a falling plank: fastest as it lands");
            float min = 90f;
            for (float t = t1; t < t1 + 10f; t += 0.25f) {
                float a = DeathFall.tiltDeg(t, kind, id);
                assertTrue(a <= 90f + 1e-3f, "never through the ground: " + a);
                min = Math.min(min, a);
            }
            assertTrue(min < 88f, "one bounce after the impact: " + min);
            assertEquals(90f, DeathFall.tiltDeg(t1 + 14f, kind, id), 0.6f, "then still");
        }
    }

    @Test
    void aHeavyBlowLandsSoonerAndThrowsTheBodyFurther() {
        for (int id = 0; id < 100; id++) {
            assertTrue(DeathFall.impactTick(DeathFall.HEAVY, id) < DeathFall.impactTick(DeathFall.BLOW, id));
            float[] heavy = pose(dying(0f, true, 1, id), id, 40f, true);
            float[] light = pose(dying(0f, false, 1, id), id, 40f, true);
            assertEquals(DeathFall.HEAVY, dying(0f, true, 1, id)[B + AnimState.DEATH_KIND]);
            // thrown backwards (TZ + = forwards)
            assertTrue(at(heavy, Motion.ROOT, Motion.TZ) < at(light, Motion.ROOT, Motion.TZ) - 3f);
        }
    }

    @Test
    void lyingItRestsOnTheGroundNotThroughIt() {
        for (float dir : new float[] {0f, 90f, -90f, 180f}) {
            float[] out = pose(dying(dir, false, 1, 3), 3, 40f, false);
            float ty = at(out, Motion.ROOT, Motion.TY);
            assertTrue(ty >= DeathFall.HALF_DEPTH * 0.9f, "lifted by at least the body's half depth: " + ty);
            assertTrue(ty <= DeathFall.HALF_WIDTH + 0.01f, "and no more than its half width: " + ty);
        }
    }

    @Test
    void theArmUnderneathASideFallGoesUpPastTheHead() {
        float[] out = pose(dying(90f, false, 1, 5), 5, 40f, false);   // hit from its right: down on its left side
        assertTrue(at(out, Motion.ARM_L, Motion.RZ) < -100f * D, "the left arm out past the head");
        float[] out2 = pose(dying(-90f, false, 1, 5), 5, 40f, false);
        assertTrue(at(out2, Motion.ARM_R, Motion.RZ) > 100f * D, "the right arm out past the head");
    }

    @Test
    void onlyAJointedBodyBucklesAtTheKnees() {
        float[] s = dying(180f, false, 200, 9);   // a crumple: the knees go first
        float reel = DeathFall.reelTicks(DeathFall.NO_BLOW);
        float[] jointed = pose(s, 9, reel, true), straight = pose(s, 9, reel, false);
        assertTrue(at(jointed, DeathFall.KNEE_R, Motion.RX) < -40f * D, "the knee bends: the foot goes back");
        assertTrue(at(jointed, Motion.ROOT, Motion.TY) < 0f, "and the body sinks with it");
        assertEquals(0f, at(straight, Motion.LEG_R, Motion.RX), 1e-6f, "a straight leg does not kick forwards instead");
    }

    @Test
    void theKillingBlowIsLatchedOnce() {
        float[] s = dying(90f, false, 2, 1);
        assertEquals(DeathFall.BLOW, s[B + AnimState.DEATH_KIND]);
        float az = s[B + AnimState.DEATH_AZ];
        Kinematics.onHit(s, B, 0f);                 // another hit while it falls
        s[B + AnimState.HIT_SIZE] = AnimState.STAGGER;
        DeathFall.latch(s, B, 1, 5);
        assertEquals(DeathFall.BLOW, s[B + AnimState.DEATH_KIND], "the fall does not change course");
        assertEquals(az, s[B + AnimState.DEATH_AZ], 0f);
        assertEquals(DeathFall.NO_BLOW, dying(90f, false, 1 + DeathFall.BLOW_WINDOW + 1, 1)[B + AnimState.DEATH_KIND],
            "a hit older than the window is not the killing blow");
        DeathFall.latch(s, B, 1, 0);                // back on its feet (a revive)
        assertEquals(DeathFall.ALIVE, s[B + AnimState.DEATH_KIND]);
        assertEquals(0f, s[B + AnimState.DEATH_DRAWN]);
    }

    @Test
    void nothingLatchedPosesNothingAndBadInputStaysFinite() {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, B, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        float[] out = pose(s, 1, 10f, true);
        for (float v : out)
            assertEquals(0f, v, 0f);
        float[] bad = dying(0f, false, 1, 2);
        bad[B + AnimState.DEATH_AZ] = Float.NaN;
        for (float t : new float[] {Float.NaN, -5f, 0f, 1e9f}) {
            for (float v : pose(bad, 2, t, true))
                assertTrue(Float.isFinite(v));
        }
    }

    @Test
    void theBodyIsHeldThenSinks() {
        assertEquals(0f, DeathFall.sinkBlocks(20f), 0f);
        assertEquals(0f, DeathFall.sinkBlocks(DeathFall.HOLD_END), 0f, "held still until the hold ends");
        float prev = 0f;
        for (float t = DeathFall.HOLD_END; t <= DeathFall.END; t += 0.5f) {
            float k = DeathFall.sinkBlocks(t);
            assertTrue(k >= prev);
            prev = k;
        }
        assertEquals(DeathFall.SINK_BLOCKS, DeathFall.sinkBlocks(DeathFall.END), 1e-6f, "gone by the end");
        assertTrue(DeathFall.SINK_BLOCKS > DeathFall.HALF_WIDTH * 2f / 16f, "deeper than a body on its side is thick");
        assertTrue(DeathFall.HOLD_END - 20 >= 40, "held for at least two seconds after the server removed it");
    }

    @Test
    void humansFallByThemselvesAndOthersStillFlip() {
        BoneMap h = BoneMaps.forModel(new HumanoidModel<>(LayerDefinition.create(
            HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64).bakeRoot()));
        assertNotNull(h);
        assertTrue(h.deathPose() && !h.jointed, "a vanilla human: its own parts, no knees");
        BoneMap hal = BoneMaps.forModel(new HalberdierModel<Entity>(HalberdierModel.createBodyLayer().bakeRoot()));
        assertNotNull(hal);
        assertTrue(hal.deathPose() && hal.jointed, "a generated human: its chest, elbows and knees");
        for (int bone = Motion.BODY; bone < DeathFall.BONES; bone++)
            if (bone != Motion.ARMS)
                assertNotNull(hal.death[bone], "the Halberdier has bone " + bone);
        BoneMap levy = BoneMaps.forModel(new CrossbowLevyModel<Entity>(CrossbowLevyModel.createBodyLayer().bakeRoot()));
        assertNotNull(levy);
        assertTrue(levy.deathPose() && levy.jointed, "a rig whose body is its torso (no pelvis) falls too");
        BoneMap golem = BoneMaps.forModel(new IronGolemModel<>(IronGolemModel.createBodyLayer().bakeRoot()));
        assertFalse(golem != null && golem.deathPose(), "humans first: a golem still flips");
    }

    @Test
    void placingTheFallLeavesTheBodyExactlyAsItWas() {
        HalberdierModel<Entity> hal = new HalberdierModel<Entity>(HalberdierModel.createBodyLayer().bakeRoot());
        BoneMap map = BoneMaps.forModel(hal);
        assertNotNull(map);
        float[] before = new float[map.death.length * 6];
        for (int i = 1; i < map.death.length; i++)
            if (map.death[i] != null) {
                before[i * 6] = map.death[i].xRot;
                before[i * 6 + 1] = map.death[i].zRot;
                before[i * 6 + 2] = map.death[i].y;
            }
        float[] s = dying(30f, true, 1, 4);
        DeathFall.pose(s, B, 4, 9f, true, MotionPass.fallScratch());
        PoseStack ps = new PoseStack();
        MotionPass.placeFall(map, ps);
        assertTrue(map.death[DeathFall.KNEE_R].xRot != before[DeathFall.KNEE_R * 6], "the fall moved the knee");
        MotionPass.restore();
        for (int i = 1; i < map.death.length; i++)
            if (map.death[i] != null) {
                assertEquals(before[i * 6], map.death[i].xRot, 0f);
                assertEquals(before[i * 6 + 1], map.death[i].zRot, 0f);
                assertEquals(before[i * 6 + 2], map.death[i].y, 0f);
            }
    }

    @Test
    void aHalberdHeldAtTheGuardLiesAlongTheFallenBody() {
        // the captures: hit in the chest, the Halberdier fell on his back and his halberd, still at the guard (pointing
        // forwards), stood straight up beside him
        HalberdierModel<Entity> hal = new HalberdierModel<Entity>(HalberdierModel.createBodyLayer().bakeRoot());
        BoneMap map = BoneMaps.forModel(hal);
        assertNotNull(map);
        assertEquals(1, map.droppedCount, "the halberd, on its grip beside the right arm");
        ModelPart halberd = map.dropped[0];
        halberd.xRot = -1.3f;     // the guard: pitched forwards, out in front of him
        halberd.z = -6f;
        int id = 4;
        float[] s = dying(0f, false, 1, id);
        float kind = s[B + AnimState.DEATH_KIND];
        assertEquals(0f, DeathFall.dropped(DeathFall.reelTicks(kind), kind, id), 1e-6f, "still held through the reel");
        float down = DeathFall.impactTick(kind, id);
        assertEquals(1f, DeathFall.dropped(down, kind, id), 1e-4f, "at its rest by the time the body lands");
        DeathFall.pose(s, B, id, down + 5f, true, MotionPass.fallScratch());
        MotionPass.placeFall(map, new PoseStack(), DeathFall.dropped(down + 5f, kind, id));
        assertEquals(0f, halberd.xRot, 1e-6f, "upright in the body's frame, as planted at rest: along the body lying");
        assertEquals(0f, halberd.z, 1e-6f, "in the body's plane, not stood out in front of it");
        MotionPass.restore();
        assertEquals(-1.3f, halberd.xRot, 0f, "the living pose is left as setupAnim made it");
        assertEquals(-6f, halberd.z, 0f);
        BoneMap levy = BoneMaps.forModel(new CrossbowLevyModel<Entity>(CrossbowLevyModel.createBodyLayer().bakeRoot()));
        assertNotNull(levy);
        assertEquals(0, levy.droppedCount, "the Levy's crossbow rests held forwards: its rest is no lying pose");
    }

    @Test
    void aCrossbowHeldInBothHandsLowersAlongTheFallenBody() {
        // the captures (eyes run 40978023283): the Levy fell on his back and his crossbow, held out in front of him in
        // both hands, stood straight up out of the body with the arms that hold it. Its grip carries both arms, so
        // the grip itself tips down: the crossbow comes to lie along the body, pointing at the feet
        BoneMap map = BoneMaps.forModel(new CrossbowLevyModel<Entity>(CrossbowLevyModel.createBodyLayer().bakeRoot()));
        assertNotNull(map);
        assertEquals(1, map.loweredCount, "the crossbow's grip, carrying both arms");
        ModelPart grip = map.lowered[0];
        assertTrue(grip.hasChild("crossbow") && grip.hasChild("right_arm") && grip.hasChild("left_arm"));
        grip.xRot = 0.02f;    // setupAnim's breath
        int id = 9;
        float[] s = dying(0f, false, 1, id);
        float kind = s[B + AnimState.DEATH_KIND];
        float reel = DeathFall.reelTicks(kind), down = DeathFall.impactTick(kind, id);
        DeathFall.pose(s, B, id, reel, true, MotionPass.fallScratch());
        MotionPass.placeFall(map, new PoseStack(), DeathFall.dropped(reel, kind, id));
        assertEquals(0.02f, grip.xRot, 1e-6f, "still held out through the reel");
        MotionPass.restore();
        DeathFall.pose(s, B, id, down + 5f, true, MotionPass.fallScratch());
        MotionPass.placeFall(map, new PoseStack(), DeathFall.dropped(down + 5f, kind, id));
        // the crossbow points along -z in its grip's frame; tipped by xRot it points (0, sin, -cos) in the body's
        float toFeet = Mth.sin(grip.xRot), outFront = -Mth.cos(grip.xRot);
        assertTrue(toFeet > 0.9f, "along the body towards the feet once it lands, not out in front: " + toFeet);
        assertTrue(Math.abs(outFront) < 0.45f, "barely off the body's plane: " + outFront);
        MotionPass.restore();
        assertEquals(0.02f, grip.xRot, 0f, "the living pose is left as setupAnim made it");
        BoneMap hal = BoneMaps.forModel(new HalberdierModel<Entity>(HalberdierModel.createBodyLayer().bakeRoot()));
        assertNotNull(hal);
        assertEquals(0, hal.loweredCount, "the halberd's grip carries one arm: the halberd itself settles instead");
    }

    @Test
    void aBodyThatFallsByItselfIsRedOnlyWhileHurt() {
        // vanilla: red while hurt or dying, on the body and every layer (LivingEntityRenderer.getOverlayCoords)
        assertTrue(DeathFall.red(0, 5, false), "vanilla's death: red throughout");
        assertTrue(DeathFall.red(4, 0, true), "the hurt flash of a living unit");
        assertTrue(DeathFall.red(6, 2, true), "the hurt flash of the killing blow");
        // the team cloth asked for the overlay on the falling Halberdier and on the body Corpses held: red to tick 100
        for (int t = 1; t < DeathFall.END; t++)
            assertFalse(DeathFall.red(0, t, true), "its own colours once the flash is over, tick " + t);
        assertFalse(DeathFall.red(0, 0, false), "alive and unhurt");
    }
}
