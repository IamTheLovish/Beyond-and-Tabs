package com.solegendary.reignofnether.unit.modelling.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.CrossbowLevyModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared look solver (design/animation-foundation-audit.md, section 3, item 4): a look split over the chest and
 * the head by shares and limits, with a spring on where the head looks - it arrives late and settles - and a ranged
 * unit drawing on its target turning its upper body to it.
 */
class LookSolverTest {

    private static final float D = Mth.DEG_TO_RAD;
    private static final float[] SLIDERS = {1, 1, 1, 1, 1, 1, 1};

    @Test
    void theChainSplitsALookByShareAndLimit() {
        float[] out = new float[3];
        Life.look(60f, new float[] {0.15f, 0.25f}, new float[] {25f, 35f, 80f}, 3, out);
        assertEquals(9f, out[0], 1e-4f, "the chest its share");
        assertEquals(15f, out[1], 1e-4f, "the neck its share");
        assertEquals(36f, out[2], 1e-4f, "the head the rest");
        Life.look(-170f, new float[] {0.5f}, new float[] {25f, 80f}, 2, out);
        assertEquals(-25f, out[0], 1e-4f, "the chest stops at its limit");
        assertEquals(-80f, out[1], 1e-4f, "and so does the head");
        Life.look(4f, new float[] {2f}, new float[] {25f, 80f}, 2, out);
        assertEquals(4f, out[0], 1e-4f, "a bone never turns past the target");
        assertEquals(0f, out[1], 1e-4f);
        Life.look(Float.NaN, new float[] {0.3f}, new float[] {25f, 80f}, 2, out);
        assertEquals(0f, out[0] + out[1], 0f, "no target, no look");
    }

    private static float[] fresh(float headYaw) {
        float[] s = new float[AnimState.STRIDE];
        Kinematics.step(s, 0, 0f, 0f, 0f, 0f, 20f, 20f, false, true);
        Kinematics.look(s, 0, headYaw, 0f, false, true);
        return s;
    }

    @Test
    void theHeadArrivesLateAndSettles() {
        float[] s = fresh(0f);
        assertEquals(0f, s[AnimState.SP_LOOK_YAW], 0f, "a fresh slot starts on the head");
        Kinematics.look(s, 0, 60f, 20f, false, false);
        assertTrue(s[AnimState.SP_LOOK_YAW] > 1f && s[AnimState.SP_LOOK_YAW] < 30f, "late: " + s[AnimState.SP_LOOK_YAW]);
        float max = 0f;
        for (int t = 0; t < 25; t++) {
            Kinematics.look(s, 0, 60f, 20f, false, false);
            max = Math.max(max, s[AnimState.SP_LOOK_YAW]);
        }
        assertEquals(60f, s[AnimState.SP_LOOK_YAW], 1f, "then there");
        assertEquals(20f, s[AnimState.SP_LOOK_PITCH], 1f);
        assertTrue(max < 62f, "without a wobble past it: " + max);
    }

    @Test
    void itTurnsTheShortWayRoundAndStaysWithinATurn() {
        float[] s = fresh(170f);
        Kinematics.look(s, 0, -170f, 0f, false, false);
        assertTrue(s[AnimState.SP_LOOK_YAW] > 170f, "from 170 to -170 is 20 degrees on, not 340 back");
        for (int t = 0; t < 2000; t++)
            Kinematics.look(s, 0, Mth.wrapDegrees(t * 9f), 0f, false, false);
        assertTrue(Math.abs(s[AnimState.SP_LOOK_YAW]) <= 360f + 1e-3f, "spun for minutes, still within a turn");
        assertTrue(Math.abs(s[AnimState.SP_LOOK_YAW + 2] - s[AnimState.SP_LOOK_YAW]) < 30f, "and its last step with it");
    }

    @Test
    void theAimEasesInAndOut() {
        float[] s = fresh(0f);
        Kinematics.look(s, 0, 0f, 0f, true, false);
        assertTrue(s[AnimState.AIM] > 0f && s[AnimState.AIM] < 0.5f, "eases in");
        for (int t = 0; t < 30; t++)
            Kinematics.look(s, 0, 0f, 0f, true, false);
        assertTrue(s[AnimState.AIM] > 0.99f);
        for (int t = 0; t < 30; t++)
            Kinematics.look(s, 0, 0f, 0f, false, false);
        assertTrue(s[AnimState.AIM] < 0.01f, "and out");
    }

    private static float[] settled(float headYaw, float pitch, boolean aiming) {
        float[] s = fresh(headYaw);
        for (int t = 0; t < 60; t++)
            Kinematics.look(s, 0, headYaw, pitch, aiming, false);
        return s;
    }

    private static float at(float[] out, int bone, int ch) {
        return out[bone * Motion.CH + ch];
    }

    @Test
    void aVanillaBodyTurnsItsChestAndTheHeadStillEndsOnTheTarget() {
        float bodyYaw = 30f, net = 60f;
        float[] s = settled(bodyYaw + net, 0f, false);
        float[] out = new float[Motion.OUT];
        Motion.pose(s, 0, 1, 100f, 1f, 0f, SLIDERS, false, new float[] {Motion.LOOK_FULL, bodyYaw, net, 0f}, out);
        float chest = at(out, Motion.BODY, Motion.RY) / D;
        assertEquals(Motion.CHEST_YAW_SHARE * net, chest, 0.5f, "the chest takes its share");
        // the head already holds `net` from the model and rides the chest's turn: head + offset + chest = the look
        assertEquals(net, net + at(out, Motion.HEAD, Motion.RY) / D + chest, 0.5f);
        assertEquals(0f, at(out, Motion.ARM_R, Motion.RY), 1e-6f, "no aim: the arms simply ride along");
    }

    @Test
    void aRangedUnitDrawingOnItsTargetTurnsItsUpperBodyToIt() {
        float bodyYaw = 0f, net = 50f, pitch = 20f;
        float[] s = settled(net, pitch, true);
        float[] out = new float[Motion.OUT];
        Motion.pose(s, 0, 1, 100f, 1f, 0f, SLIDERS, false, new float[] {Motion.LOOK_FULL, bodyYaw, net, pitch}, out);
        float chest = at(out, Motion.BODY, Motion.RY) / D;
        assertEquals(Math.min(Motion.CHEST_YAW_LIMIT, Motion.CHEST_YAW_SHARE_AIM * net), chest, 0.5f, "a real twist");
        assertTrue(at(out, Motion.BODY, Motion.RX) / D > 4f, "and a lean down the shot");
        assertEquals(-chest, at(out, Motion.ARM_R, Motion.RY) / D, 0.5f, "the bow arms keep their aim");
    }

    @Test
    void aBodyTurningUnderASteadyGazeDoesNotDragTheGaze() {
        float[] s = settled(90f, 0f, false);           // looking due 90 in the world
        float[] out = new float[Motion.OUT];
        for (float bodyYaw : new float[] {90f, 70f, 40f}) {
            float net = 90f - bodyYaw;
            Motion.pose(s, 0, 1, 100f, 1f, 0f, SLIDERS, false, new float[] {Motion.LOOK_FULL, bodyYaw, net, 0f}, out);
            float total = net + at(out, Motion.HEAD, Motion.RY) / D + at(out, Motion.BODY, Motion.RY) / D;
            assertEquals(net, total, 0.5f, "still on the same point after the body turned to " + bodyYaw);
        }
    }

    @Test
    void aRigAimsItsOwnHeadAndTurnsItsTorsoOnlyWithAnAim() {
        float[] out = new float[Motion.OUT];
        Motion.pose(settled(40f, 0f, false), 0, 1, 100f, 1f, 0f, SLIDERS, true,
            new float[] {Motion.LOOK_TWIST, 0f, 40f, 0f}, out);
        assertEquals(0f, at(out, Motion.BODY, Motion.RY), 1e-6f, "no aim: the rig's own look");
        Motion.pose(settled(40f, 0f, true), 0, 1, 100f, 1f, 0f, SLIDERS, true,
            new float[] {Motion.LOOK_TWIST, 0f, 40f, 0f}, out);
        assertTrue(at(out, Motion.BODY, Motion.RY) / D > 10f, "aiming: the torso brings the crossbow round");
        assertEquals(0f, at(out, Motion.HEAD, Motion.RY), 1e-6f, "the head is the rig's own");
        BoneMap levy = BoneMaps.forModel(new CrossbowLevyModel<Entity>(CrossbowLevyModel.createBodyLayer().bakeRoot()));
        assertNotNull(levy);
        assertEquals(Motion.LOOK_TWIST, levy.look, 0f, "the Crossbow Levy twists");
    }

    @Test
    void onAHumanoidTheShouldersSwingRoundTheSpineAndRestoreIsExact() {
        HumanoidModel<LivingEntity> body = new HumanoidModel<>(LayerDefinition.create(
            HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64).bakeRoot());
        BoneMap map = BoneMaps.forModel(body);
        assertNotNull(map);
        assertEquals(Motion.LOOK_FULL, map.look, 0f);
        float net = 60f;
        body.head.yRot = net * D;                       // as setupAnim leaves it
        float armX = body.rightArm.x, armZ = body.rightArm.z, headYaw = body.head.yRot;
        float[] s = settled(net, 0f, true);
        Motion.pose(s, 0, 1, 100f, 1f, 0f, SLIDERS, false, new float[] {Motion.LOOK_FULL, 0f, net, 0f},
            MotionPass.scratch());
        MotionPass.place(map, new PoseStack());
        assertEquals(net * D, body.head.yRot, 1.5f * D, "the head on the target");
        assertTrue(Math.abs(body.rightArm.z - armZ) > 1f, "the right shoulder swung round");
        MotionPass.restore();
        assertEquals(armX, body.rightArm.x, 0f);
        assertEquals(armZ, body.rightArm.z, 0f);
        assertEquals(headYaw, body.head.yRot, 0f);
    }
}
