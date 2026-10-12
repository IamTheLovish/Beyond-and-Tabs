package com.solegendary.reignofnether.unit.modelling.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A captain's back banner (design/unit_skins.md): a pole strapped to the back with a flag in the owner's colour
 * flying above the head, like a sashimono. Given to the T2 constructors (Royal Architect, Elder Druid, Harbourmaster,
 * Ley Master, Engineer Captain, Embalmer), so the "senior" builder is found at a glance in a crowd of workers from the
 * RTS camera, and its owner with it. Silhouette only: no gameplay.
 *
 * The geometry is built once in code (no layer registration needed) and follows the body part, so it leans and turns
 * with the unit. Texture: textures/entities/captain_banner.png (tools/gen_hd_skins.py): a wooden pole with a gold
 * finial, and grey-scale cloth that is multiplied by the owner's display colour. Two small cubes per captain per frame.
 */
@OnlyIn(Dist.CLIENT)
public class BackBannerLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/captain_banner.png");
    private static ModelPart pole, flag;

    private final java.util.function.Predicate<T> shows;

    public BackBannerLayer(RenderLayerParent<T, M> parent, java.util.function.Predicate<T> shows) {
        super(parent);
        this.shows = shows;
    }

    public BackBannerLayer(RenderLayerParent<T, M> parent) {
        this(parent, e -> true);
    }

    private static void build() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // body space: y down from the neck, +z is the back. The pole stands clear of the head's hat box (z <= 4.45).
        root.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -16.0F, 4.6F, 1.0F, 26.0F, 1.0F), PartPose.ZERO);
        // the flag hangs from a pivot on the pole, so its sway turns it about the pole rather than about the neck
        root.addOrReplaceChild("flag", CubeListBuilder.create().texOffs(4, 0).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 10.0F, 1.0F), PartPose.offset(0.0F, -15.5F, 5.6F));
        ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
        pole = baked.getChild("pole");
        flag = baked.getChild("flag");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof Unit unit) || entity.isInvisible() || entity.hasEffect(MobEffectRegistrar.PHASING.get()))
            return;
        String owner = unit.getOwnerName();
        if (owner == null || owner.isBlank() || !shows.test(entity))
            return;
        if (pole == null)
            build();
        int rgb = PlayerColors.getPlayerDisplayColorHex(owner);
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        pose.pushPose();
        this.getParentModel().body.translateAndRotate(pose);
        pole.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        // a slow sway so the flag reads as cloth; per-entity phase so a crowd of captains doesn't flap in unison
        flag.yRot = 0.12F * (float) Math.sin(ageInTicks * 0.08F + entity.getId());
        flag.render(pose, vc, light, OverlayTexture.NO_OVERLAY, r, g, b, 1f);
        pose.popPose();
    }
}
