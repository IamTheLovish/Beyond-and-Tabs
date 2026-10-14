package com.solegendary.reignofnether.unit.modelling.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UpgradeVisuals;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Researched trim bolted onto a unit (unit/UpgradeVisuals, kind TRIM): when the mask carries a trim bit, a few extra
 * cubes from a per-faction catalogue are drawn attached to one bone of the model (the body by default), so a
 * Shield-Tactics Brute wears the Horde's bolted plate and a Sunforged line shows gilded shoulders. The catalogue is
 * one {@link ModelPart} per faction, built once on first use in body space (y down from the neck, +z the back, the
 * torso x -4..4 / y 0..12 / z -2..2, the arms outside x +-4) and sized to read from the RTS camera: no cube under 1.5
 * px. Flat-coloured on Forge's white texture in the faction's glow hue darkened to a material, so the same cubes
 * read as gold, bone, rust or coral by colour alone; the per-unit art comes later with the new bodies.
 *
 * One part render per upgraded unit per frame, nothing otherwise: nothing when the bit is clear, the unit is invisible
 * or phasing, or in strategic-icon view. No per-frame allocation (the catalogue map is read, never written, after the
 * first frame per faction).
 */
@OnlyIn(Dist.CLIENT)
public class UpgradeTrimLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation WHITE = ResourceLocation.parse("forge:textures/white.png");
    // faction name (FactionTraits.name) -> the trim part; NONE marks a faction without trim
    private static final Map<String, ModelPart> CATALOGUE = new HashMap<>();
    private static final ModelPart NONE = new ModelPart(java.util.List.of(), java.util.Map.of());

    private final Function<M, ModelPart> bone;

    /** Trim on the torso of a humanoid model. */
    public static <T extends LivingEntity, M extends HumanoidModel<T>> UpgradeTrimLayer<T, M> body(RenderLayerParent<T, M> parent) {
        return new UpgradeTrimLayer<>(parent, m -> m.body);
    }

    /** Trim on any bone of any model (a quadruped's body, a machine's hull). */
    public UpgradeTrimLayer(RenderLayerParent<T, M> parent, Function<M, ModelPart> bone) {
        super(parent);
        this.bone = bone;
    }

    // ---- the catalogue: one entry per faction, a few cubes each ----

    private static ModelPart build(String faction) {
        MeshDefinition mesh = new MeshDefinition();
        CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
        switch (faction) {
            case "sunforged" -> {   // gilded shoulder trim: a bar across each shoulder with a raised lip
                c.addBox(-8.5F, -1.0F, -2.5F, 4.5F, 1.5F, 5.0F).addBox(4.0F, -1.0F, -2.5F, 4.5F, 1.5F, 5.0F)
                 .addBox(-8.5F, -2.5F, -1.0F, 2.0F, 1.5F, 2.0F).addBox(6.5F, -2.5F, -1.0F, 2.0F, 1.5F, 2.0F);
            }
            case "gravebound" -> {   // bone spurs: a spike up from each shoulder on a knuckle of bone
                c.addBox(-8.0F, -0.5F, -1.5F, 3.0F, 1.5F, 3.0F).addBox(5.0F, -0.5F, -1.5F, 3.0F, 1.5F, 3.0F)
                 .addBox(-7.25F, -5.0F, -0.75F, 1.5F, 4.5F, 1.5F).addBox(5.75F, -5.0F, -0.75F, 1.5F, 4.5F, 1.5F);
            }
            case "horde" -> {   // bolted plate: a slab on the chest with four bolt heads
                c.addBox(-3.5F, 1.0F, -3.4F, 7.0F, 6.0F, 1.4F)
                 .addBox(-3.0F, 1.5F, -4.0F, 1.5F, 1.5F, 0.6F).addBox(1.5F, 1.5F, -4.0F, 1.5F, 1.5F, 0.6F)
                 .addBox(-3.0F, 5.0F, -4.0F, 1.5F, 1.5F, 0.6F).addBox(1.5F, 5.0F, -4.0F, 1.5F, 1.5F, 0.6F);
            }
            case "verdant" -> {   // antler tines: a tine up from each shoulder with a short fork
                c.addBox(-6.75F, -6.0F, -0.75F, 1.5F, 6.0F, 1.5F).addBox(5.25F, -6.0F, -0.75F, 1.5F, 6.0F, 1.5F)
                 .addBox(-8.5F, -5.0F, -0.75F, 2.0F, 1.5F, 1.5F).addBox(6.5F, -5.0F, -0.75F, 2.0F, 1.5F, 1.5F);
            }
            case "tidewrought" -> {   // coral tuft: a clump on the right shoulder
                c.addBox(-8.0F, -3.0F, -1.5F, 2.5F, 3.0F, 2.5F).addBox(-6.0F, -2.0F, 0.0F, 1.5F, 2.5F, 1.5F)
                 .addBox(-7.0F, -4.5F, -0.5F, 1.5F, 1.5F, 1.5F);
            }
            case "stormcallers" -> {   // lantern: a box on a hook at the small of the back
                c.addBox(-0.75F, 7.0F, 2.5F, 1.5F, 1.5F, 1.5F).addBox(-1.5F, 8.5F, 2.2F, 3.0F, 3.5F, 3.0F);
            }
            case "brassworks" -> {   // vent: a boiler on the back with a stack
                c.addBox(-2.5F, 1.0F, 2.2F, 5.0F, 5.0F, 2.5F).addBox(0.5F, -2.0F, 2.8F, 1.5F, 3.0F, 1.5F);
            }
            default -> { return NONE; }
        }
        mesh.getRoot().addOrReplaceChild("trim", c, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("trim");
    }

    static ModelPart trimFor(FactionTraits traits) {
        ModelPart part = CATALOGUE.get(traits.name);
        if (part == null) {
            part = build(traits.name);
            CATALOGUE.put(traits.name, part);
        }
        return part == NONE ? null : part;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof Unit unit) || entity.isInvisible() || entity.hasEffect(MobEffectRegistrar.PHASING.get()))
            return;
        if (!UpgradeVisuals.has(unit.getUpgradeMask(), UpgradeVisuals.Kind.TRIM))
            return;
        if (StrategicViewClientEvents.isStrategicView() && !StrategicViewClientEvents.keepsModel(entity))
            return;
        FactionTraits traits = FactionTraits.of(Factions.getFaction(unit));
        ModelPart trim = trimFor(traits);
        ModelPart at = trim == null ? null : bone.apply(this.getParentModel());
        if (at == null)
            return;
        // the glow hue as a material: three quarters of its brightness, lit by the entity shader like the body
        int rgb = traits.upgradeGlow == 0 ? 0xC8C8C8 : traits.upgradeGlow;
        float r = ((rgb >> 16) & 0xFF) / 255f * 0.75f, g = ((rgb >> 8) & 0xFF) / 255f * 0.75f, b = (rgb & 0xFF) / 255f * 0.75f;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        pose.pushPose();
        at.translateAndRotate(pose);
        trim.render(pose, vc, light, OverlayTexture.NO_OVERLAY, r, g, b, 1f);
        pose.popPose();
    }
}
