package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.BoneDragonUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModel;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client only: the Gravebound Bone Dragon - OUR body on the shared dragon rig (BoneDragonUnitModel; the entity stays
 * a ghast underneath, only what is drawn changed): a skeleton of old bone bound in deepslate and blackstone, soul-fire
 * in the eye sockets and the ribcage (the _glow pass), tattered wing membranes, rags of the owner's colour at the
 * elbows and down the tail (the _team pass). Drawn x{@link #DRAW_SCALE}: ~10 blocks across the wings.
 */
@OnlyIn(Dist.CLIENT)
public class BoneDragonRenderer extends MobRenderer<BoneDragonUnit, BoneDragonUnitModel> {
    private static final String NAME = "bone_dragon_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    /** The body is built at 1x (design px) and drawn this big; the hitbox (BoneDragonUnit.SCALE) is Lane A's. */
    public static final float DRAW_SCALE = 1.4f;

    public BoneDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BoneDragonUnitModel(ctx.bakeLayer(BoneDragonModel.LAYER_LOCATION)), 0.75f * BoneDragonUnit.SCALE);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(BoneDragonUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(BoneDragonUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(DRAW_SCALE, DRAW_SCALE, DRAW_SCALE);
    }

    @Override
    protected void setupRotations(BoneDragonUnit entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
    }

    @Override
    public void render(BoneDragonUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }
}
