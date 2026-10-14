package com.solegendary.reignofnether.unit.modelling.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UpgradeVisuals;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * A researched weapon that shines (unit/UpgradeVisuals, kind GLOW): when the unit's upgrade mask carries a glow bit,
 * the held weapon (or a named model part) is drawn a second time with the additive "eyes" render type - the same
 * trick as spider and enderman eyes - in the owner faction's glow colour (FactionTraits.upgradeGlow), so it reads as
 * light on the weapon rather than a recolour, and keeps the item's own texture under it. A gentle pulse (sin of the
 * entity's age, about 1.5 s, per-entity phase so a line of crossbows doesn't flash in unison).
 *
 * Same geometry, same depth: the additive pass passes the LEQUAL test over the weapon the ItemInHandLayer just drew,
 * so no z-fighting. Nothing is allocated per frame: the tinting vertex consumer and buffer source are built once per
 * layer and re-pointed at the frame's buffers; the memoized render type is looked up, not created. Nothing is drawn
 * when the bit is clear, when the unit is invisible or phasing, or in strategic-icon view (icons replace models).
 *
 * The held-item pass forces every buffer the item renderer asks for onto the block atlas, which is where item models
 * live; an item drawn by a BlockEntityWithoutLevelRenderer from another sheet (shield, trident) would sample the
 * wrong texture, so give such a unit a named part instead ({@link #part}).
 */
@OnlyIn(Dist.CLIENT)
public class UpgradeGlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private static final float PULSE_TICKS = 30f;   // ~1.5 s at 20 tps

    @Nullable private final Function<M, ModelPart> part;   // null = the main-hand item
    private final Tinted tinted = new Tinted();
    private final MultiBufferSource glowBuffers = type -> tinted;

    /** The unit's main-hand weapon glows. The parent model must be an {@link ArmedModel} (every humanoid is). */
    public static <T extends LivingEntity, M extends EntityModel<T>> UpgradeGlowLayer<T, M> heldItem(RenderLayerParent<T, M> parent) {
        return new UpgradeGlowLayer<>(parent, null);
    }

    /** A named model part glows (a blade bone, a lantern, a core): drawn again with the entity's own texture. */
    public static <T extends LivingEntity, M extends EntityModel<T>> UpgradeGlowLayer<T, M> part(RenderLayerParent<T, M> parent, Function<M, ModelPart> part) {
        return new UpgradeGlowLayer<>(parent, part);
    }

    private UpgradeGlowLayer(RenderLayerParent<T, M> parent, @Nullable Function<M, ModelPart> part) {
        super(parent);
        this.part = part;
    }

    /** The faction's glow RGB for a unit (white when the faction has none). */
    static int glowColour(Unit unit) {
        int rgb = FactionTraits.of(Factions.getFaction(unit)).upgradeGlow;
        return rgb == 0 ? 0xFFFFFF : rgb;
    }

    /** 0.55 .. 1.0 brightness, a slow breath; per-entity phase. */
    static float pulse(LivingEntity entity, float ageInTicks) {
        return 0.775f + 0.225f * (float) Math.sin((ageInTicks + (entity.getId() & 31)) * (2f * (float) Math.PI / PULSE_TICKS));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof Unit unit) || entity.isInvisible() || entity.hasEffect(MobEffectRegistrar.PHASING.get()))
            return;
        if (!UpgradeVisuals.has(unit.getUpgradeMask(), UpgradeVisuals.Kind.GLOW))
            return;
        if (StrategicViewClientEvents.isStrategicView() && !StrategicViewClientEvents.keepsModel(entity))
            return;

        int rgb = glowColour(unit);
        float k = pulse(entity, ageInTicks);
        float r = ((rgb >> 16) & 0xFF) / 255f * k, g = ((rgb >> 8) & 0xFF) / 255f * k, b = (rgb & 0xFF) / 255f * k;

        if (part != null) {
            ModelPart mp = part.apply(this.getParentModel());
            if (mp == null)
                return;
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(this.getTextureLocation(entity)));
            mp.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, 1f);
            return;
        }

        ItemStack stack = entity.getMainHandItem();
        if (stack.isEmpty() || !(this.getParentModel() instanceof ArmedModel armed))
            return;
        HumanoidArm arm = entity.getMainArm();
        boolean left = arm == HumanoidArm.LEFT;
        // the additive pass over the item: every buffer the item renderer asks for goes to one fullbright,
        // additive, tinted consumer on the block atlas
        tinted.set(buffers.getBuffer(RenderType.eyes(InventoryMenu.BLOCK_ATLAS)), r, g, b);
        pose.pushPose();
        // exactly ItemInHandLayer.renderArmWithItem, so the glow lands on the weapon the hand layer drew
        armed.translateToHand(arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(entity, stack,
            left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            left, pose, glowBuffers, LightTexture.FULL_BRIGHT);
        pose.popPose();
        tinted.set(null, 1f, 1f, 1f);
    }

    /** Multiplies every vertex colour by the glow tint and forwards the rest; one per layer, re-pointed per frame. */
    private static final class Tinted implements VertexConsumer {
        private VertexConsumer in;
        private float r = 1f, g = 1f, b = 1f;

        void set(@Nullable VertexConsumer in, float r, float g, float b) {
            this.in = in;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        @Override public VertexConsumer vertex(double x, double y, double z) { in.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int cr, int cg, int cb, int ca) {
            in.color((int) (cr * r), (int) (cg * g), (int) (cb * b), ca);
            return this;
        }
        @Override public VertexConsumer uv(float u, float v) { in.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { in.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { in.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { in.normal(x, y, z); return this; }
        @Override public void endVertex() { in.endVertex(); }
        @Override public void defaultColor(int cr, int cg, int cb, int ca) {
            in.defaultColor((int) (cr * r), (int) (cg * g), (int) (cb * b), ca);
        }
        @Override public void unsetDefaultColor() { in.unsetDefaultColor(); }
    }
}
