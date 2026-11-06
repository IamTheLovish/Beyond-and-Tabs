package com.solegendary.reignofnether.unit.modelling.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.unit.modelling.animations.AngelParts;
import com.solegendary.reignofnether.unit.modelling.animations.AngelRig;
import com.solegendary.reignofnether.unit.modelling.models.gen.MenderAngelModel;
import com.solegendary.reignofnether.unit.modelling.motion.OwnMotion;
import com.solegendary.reignofnether.unit.units.villagers.MenderAngelUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * the Mender Angel in motion over its generated body (MenderAngelModel, design/models/sunforged_angel_b4_*.json by
 * tools/dsl_to_game.py; generator tools/gen_angels.py): every bone posed by {@link AngelRig} (the port of
 * tools/angel_anim.py), stepped per game tick by AngelDrive and blended per frame by AngelRenderer - the halo rides
 * the head through the rig's own halo channel. OwnMotion: the living army's motion pass leaves it alone. The body's
 * alpha (the arrival's fade-in, the death's fade-out) scales every pass's vertex alpha.
 */
@OnlyIn(Dist.CLIENT)
public class MenderAngelUnitModel extends MenderAngelModel<MenderAngelUnit> implements OwnMotion, AngelBody {
    private final AngelRig.Bones rig = AngelParts.bones(this::bone);
    @Nullable private AngelRig.Pose pose;

    public MenderAngelUnitModel(ModelPart root) {
        super(root);
    }

    @Override
    public void frame(@Nullable AngelRig.Pose pose) {
        this.pose = pose;
    }

    @Override
    public void setupAnim(MenderAngelUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (pose != null)
            AngelRig.apply(pose, rig);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, float r, float g,
                               float b, float a) {
        super.renderToBuffer(poseStack, buffer, light, overlay, r, g, b, pose == null ? a : a * (float) pose.alpha);
    }
}
