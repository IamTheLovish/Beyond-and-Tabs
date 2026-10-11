package com.solegendary.reignofnether.unit.modelling.renderers;

import com.github.alexthe666.alexsmobs.client.render.RenderSpectre;
import com.github.alexthe666.alexsmobs.entity.EntitySpectre;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Stormcallers Celestial Dragon: the Thunder Dragon's spectre model (RenderSpectre, AM's animations) in a deeper
 * jade-and-gold skin (tools/gen_storm_skins.py paints the spectre's UV outline) drawn at {@link #SCALE} - twice the
 * Thunder Dragon - with a <b>serpent tail</b> of our own: {@link TailLayer}, a chain of {@link TailLayer#SEGMENTS}
 * shrinking cubes trailing behind the body, each swaying on a travelling sine wave so the serpent undulates in flight
 * and droops when it is grounded. AM's ghostly layers are dropped (they carry AM's textures); a gold eye-glow layer
 * of ours takes their place.
 */
@OnlyIn(Dist.CLIENT)
public class CelestialDragonRenderer extends RenderSpectre {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/celestial_dragon_unit.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/celestial_dragon_glow.png");
    private static final ResourceLocation TAIL = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/celestial_dragon_tail.png");
    public static final float SCALE = 2.0f;

    public CelestialDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.clear();   // AM's membrane and eyes layers carry AM's textures
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
        this.addLayer(new TailLayer(this));
        this.shadowRadius = 0.5f * SCALE;
    }

    @Override
    protected void scale(EntitySpectre entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(EntitySpectre entity) {
        return TEXTURE;
    }

    @Override
    public float getAlphaForRender(EntitySpectre entity, float partialTicks) {
        return 1f;   // a serpent of scale and storm, not a ghost
    }

    /**
     * The serpent tail: a chain of cubes behind the body in model space (the model faces -z, so the tail runs +z),
     * each segment a little smaller than the last and lifted by a sine wave travelling down the chain. Built once from
     * a LayerDefinition (no layer registration needed: bakeRoot is public) on a 32x32 texture of our own.
     */
    @OnlyIn(Dist.CLIENT)
    static final class TailLayer extends RenderLayer<EntitySpectre, com.github.alexthe666.alexsmobs.client.model.ModelSpectre> {
        static final int SEGMENTS = 9;
        static final float SEG_LENGTH = 0.42f;   // model units (blocks) per segment before the renderer's scale
        private final ModelPart[] segments = new ModelPart[SEGMENTS];

        TailLayer(CelestialDragonRenderer parent) {
            super(parent);
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            for (int i = 0; i < SEGMENTS; i++) {
                float size = 7f - i * 0.6f;   // pixels: 7 down to ~2
                root.addOrReplaceChild("seg" + i, CubeListBuilder.create().texOffs((i % 4) * 8, (i / 4) * 8)
                    .addBox(-size / 2, -size / 2, 0, size, size, SEG_LENGTH * 16f), PartPose.ZERO);
            }
            ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
            for (int i = 0; i < SEGMENTS; i++)
                segments[i] = baked.getChild("seg" + i);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, EntitySpectre entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            boolean grounded = entity instanceof CelestialDragonUnit d && d.isGrounded();
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TAIL));
            poseStack.pushPose();
            // the root of the tail: the back of the spectre's body in model space (y down, 1.5 = the ground plane)
            poseStack.translate(0, 0.95, 0.9);
            float t = ageInTicks * (grounded ? 0.05f : 0.18f);
            for (int i = 0; i < SEGMENTS; i++) {
                float wave = Mth.sin(t - i * 0.55f) * (grounded ? 0.06f : 0.14f);
                float droop = grounded ? 0.06f * i : 0.0f;   // on the ground the tail hangs
                poseStack.mulPose(com.mojang.math.Axis.YP.rotation(wave));
                poseStack.mulPose(com.mojang.math.Axis.XP.rotation(-droop * 0.5f + Mth.cos(t - i * 0.55f) * 0.05f));
                segments[i].render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.translate(0, 0, SEG_LENGTH);
            }
            poseStack.popPose();
        }
    }
}
