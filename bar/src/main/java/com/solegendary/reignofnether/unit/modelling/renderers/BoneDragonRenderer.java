package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.postfx.ShaderPacks;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.BoneDragonUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModel;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client only: the Gravebound Bone Dragon - OUR body on the shared dragon rig (BoneDragonUnitModel; the entity stays
 * a ghast underneath, only what is drawn changed): a skeleton of old bone bound in deepslate and blackstone, soul-fire
 * in the eye sockets and the ribcage (the _glow pass), tattered wing membranes, rags of the owner's colour at the
 * elbows and down the tail (the _team pass). Drawn x{@link #DRAW_SCALE}: ~10 blocks across the wings.
 *
 * The sail (every mem_* / web_* bone) is drawn in its own TRANSLUCENT pass ({@link SailLayer}: entityTranslucent at
 * {@link #SAIL_ALPHA}) and skipped in the opaque body pass, so the membrane reads as thin dried skin the ground shows
 * through rather than a dark slab - the first eyes tour (run 39585624818, under Complementary) showed it as mud.
 */
@OnlyIn(Dist.CLIENT)
public class BoneDragonRenderer extends MobRenderer<BoneDragonUnit, BoneDragonUnitModel> {
    private static final String NAME = "bone_dragon_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    /** The body is built at 1x (design px) and drawn this big; the hitbox (BoneDragonUnit.SCALE) is Lane A's. */
    public static final float DRAW_SCALE = 1.4f;
    /** How solid the wing membrane is drawn (1 = opaque). */
    public static final float SAIL_ALPHA = 0.6f;

    private final List<ModelPart> sail = new ArrayList<>();

    public BoneDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BoneDragonUnitModel(ctx.bakeLayer(BoneDragonModel.LAYER_LOCATION)), 0.75f * BoneDragonUnit.SCALE);
        for (Map.Entry<String, ModelPart> e : this.model.bones.entrySet())
            if (e.getKey().startsWith("mem_") || e.getKey().startsWith("web_"))
                sail.add(e.getValue());
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
        this.addLayer(new SailLayer(this));
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
        // the opaque pass leaves the sail out; the layers that follow (team, glow) choose their own parts, and the
        // sail layer draws it last, translucent, over everything
        for (int i = 0, n = sail.size(); i < n; i++)
            sail.get(i).skipDraw = true;
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        this.model.drawAll();
    }

    /** The membrane alone, translucent, in the same pose the body was just drawn in. */
    class SailLayer extends RenderLayer<BoneDragonUnit, BoneDragonUnitModel> {
        SailLayer(RenderLayerParent<BoneDragonUnit, BoneDragonUnitModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, BoneDragonUnit entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible())
                return;
            // a shader pack's shadow map ignores alpha and the tears: the sail would throw a solid black rectangle on
            // the ground (the r7 eyes tour, Complementary), so it casts no shadow at all - a dead wing's skin is thin
            if (ShaderPacks.renderingShadowPass())
                return;
            BoneDragonUnitModel m = this.getParentModel();
            m.drawOnly(sail);
            VertexConsumer buf = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
            m.renderToBuffer(pose, buf, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, SAIL_ALPHA);
            m.drawAll();
        }
    }
}
