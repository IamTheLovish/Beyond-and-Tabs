package com.solegendary.reignofnether.unit.modelling.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The motion pass on real vanilla bodies (design/living_army.md, section 3): the parts vanilla copies from another
 * inside setupAnim - a player body's sleeves, trouser legs and jacket, a humanoid's hat - move with what they copy, so
 * a flinch never leaves a sleeve behind its arm; and restore puts every one of them back.
 */
class MotionPassTest {

    private static void offsets() {
        float[] out = MotionPass.scratch();
        java.util.Arrays.fill(out, 0f);
        out[Motion.BODY * Motion.CH + Motion.RX] = 0.2f;
        out[Motion.BODY * Motion.CH + Motion.RZ] = 0.1f;
        out[Motion.HEAD * Motion.CH + Motion.RY] = 0.4f;
        out[Motion.ARM_R * Motion.CH + Motion.RX] = 0.3f;
        out[Motion.ARM_L * Motion.CH + Motion.RZ] = -0.2f;
        out[Motion.LEG_R * Motion.CH + Motion.RZ] = 0.05f;
    }

    private static void same(ModelPart follower, ModelPart leader, String what) {
        assertEquals(leader.x, follower.x, 1e-6f, what + " x");
        assertEquals(leader.y, follower.y, 1e-6f, what + " y");
        assertEquals(leader.z, follower.z, 1e-6f, what + " z");
        assertEquals(leader.xRot, follower.xRot, 1e-6f, what + " xRot");
        assertEquals(leader.yRot, follower.yRot, 1e-6f, what + " yRot");
        assertEquals(leader.zRot, follower.zRot, 1e-6f, what + " zRot");
    }

    @Test
    void aPlayerBodysOverlaysMoveWithTheirLimbs() {
        PlayerModel<LivingEntity> m = new PlayerModel<>(LayerDefinition.create(
            PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        BoneMap map = BoneMaps.forModel(m);
        assertNotNull(map);
        float sleeveBefore = m.rightSleeve.xRot;
        offsets();
        MotionPass.place(map, new PoseStack());
        same(m.rightSleeve, m.rightArm, "right sleeve");
        same(m.leftSleeve, m.leftArm, "left sleeve");
        same(m.rightPants, m.rightLeg, "right trouser leg");
        same(m.jacket, m.body, "jacket");
        same(m.hat, m.head, "hat");
        MotionPass.restore();
        assertEquals(sleeveBefore, m.rightSleeve.xRot, 0f, "restored");
    }

    @Test
    void aHumanoidsHatMovesWithTheHead() {
        HumanoidModel<LivingEntity> m = new HumanoidModel<>(LayerDefinition.create(
            HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64).bakeRoot());
        offsets();
        MotionPass.place(BoneMaps.forModel(m), new PoseStack());
        same(m.hat, m.head, "hat");
        MotionPass.restore();
    }
}
