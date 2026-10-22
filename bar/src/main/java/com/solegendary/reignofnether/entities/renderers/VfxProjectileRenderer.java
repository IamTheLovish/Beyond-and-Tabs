package com.solegendary.reignofnether.entities.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.barfx.VfxClient;
import com.solegendary.reignofnether.barfx.vfx.VfxDef;
import com.solegendary.reignofnether.entities.AbstractMagicProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The projectile visual layer (design/vfx.md "Projectiles"): a magic projectile whose type is named by an
 * assets/reignofnether/vfx/*.json {@code "type": "projectile"} file is drawn by the effects pass instead - a hot core,
 * a camera-facing ribbon trail that tapers and cools, particles shed into the wind, and the light it casts on the
 * ground and on the units it passes. This renderer only hands the projectile to {@link VfxClient} (which batches every
 * projectile on screen into one buffer); without a visual it falls back to the old shulker-bullet look, so a missing or
 * broken JSON never makes a projectile invisible.
 * <p>
 * To convert another projectile: register this renderer for its type (ClientModEvents) and list the type in a
 * projectile JSON. Never queued from a shader pack's shadow pass (an overlay casts no shadow, PR #142).
 */
@OnlyIn(Dist.CLIENT)
public class VfxProjectileRenderer<T extends AbstractMagicProjectile> extends MagicProjectileRenderer<T> {

    public VfxProjectileRenderer(EntityRendererProvider.Context ctx, ResourceLocation fallbackTexture) {
        super(ctx, fallbackTexture);
    }

    @Override
    public void render(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        VfxDef def = VfxClient.projectileVisual(entity);
        if (def == null) {
            super.render(entity, yaw, partialTicks, pose, buffers, light);
            return;
        }
        if (!com.solegendary.reignofnether.postfx.ShaderPacks.renderingShadowPass())
            VfxClient.queueProjectile(entity, def, partialTicks);
        if (!def.projectile.hideModel)
            super.render(entity, yaw, partialTicks, pose, buffers, light);
    }
}
