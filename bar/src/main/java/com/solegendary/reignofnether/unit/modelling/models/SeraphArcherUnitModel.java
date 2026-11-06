package com.solegendary.reignofnether.unit.modelling.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.unit.modelling.animations.AngelParts;
import com.solegendary.reignofnether.unit.modelling.animations.AngelRig;
import com.solegendary.reignofnether.unit.modelling.models.gen.SeraphArcherModel;
import com.solegendary.reignofnether.unit.modelling.motion.OwnMotion;
import com.solegendary.reignofnether.unit.units.villagers.SeraphArcherUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * the Seraph Archer in motion over its generated body (SeraphArcherModel, design/models/sunforged_angel_b4_*.json by
 * tools/dsl_to_game.py; generator tools/gen_angels.py): every bone posed by {@link AngelRig} (the port of
 * tools/angel_anim.py), stepped per game tick by AngelDrive and blended per frame by AngelRenderer - the halo rides
 * the head through the rig's own halo channel. OwnMotion: the living army's motion pass leaves it alone. Every pass
 * (the paint, the team mask, the glow) fades with the rig (AngelDraw): the body at its alpha, the halo at its halo
 * alpha, the Mender's lantern brightened by the heal in the glow pass.
 */
@OnlyIn(Dist.CLIENT)
public class SeraphArcherUnitModel extends SeraphArcherModel<SeraphArcherUnit> implements OwnMotion, AngelBody {
    private final AngelRig.Bones rig = AngelParts.bones(this::bone);
    private final AngelDraw draw = new AngelDraw(root(), bone("halo"), bone("lantern"));
    @Nullable private AngelRig.Pose pose;
    private boolean glowPass;

    public SeraphArcherUnitModel(ModelPart root) {
        super(root);
    }

    @Override
    public void frame(@Nullable AngelRig.Pose pose) {
        this.pose = pose;
    }

    @Override
    public void glowPass(boolean on) {
        this.glowPass = on;
    }

    @Override
    public void setupAnim(SeraphArcherUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (pose != null)
            AngelRig.apply(pose, rig);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, float r, float g,
                               float b, float a) {
        draw.render(pose, glowPass, r, g, b, a,
            (r2, g2, b2, a2) -> super.renderToBuffer(poseStack, buffer, light, overlay, r2, g2, b2, a2));
    }
}
