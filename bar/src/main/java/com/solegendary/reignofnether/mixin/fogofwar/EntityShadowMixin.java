package com.solegendary.reignofnether.mixin.fogofwar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// brightness shading for blocks excluding liquids and flat flace blocks (like tall grass)

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityShadowMixin {

    /**
     * Units stand on the ground in the RTS view: vanilla fades an entity's round shadow by its distance from the
     * CAMERA ((1 - d^2 / 256) x strength, gone past 16 blocks), and the orthographic RTS camera sits far above the
     * field - so no unit ever had a shadow under it there; every body floated over the grass (eyes stills, Oct 16).
     * In the RTS view the distance is taken as 0: the shadow is drawn at its renderer's full strength. Shaders with
     * their own shadows (Iris / Oculus) skip vanilla's anyway. Fog-of-war hiding below still applies.
     */
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;distanceToSqr(DDD)D")
    )
    private double reignofnether$shadowDistance(EntityRenderDispatcher self, double x, double y, double z) {
        if (OrthoviewClientEvents.isEnabled())
            return 0;
        return self.distanceToSqr(x, y, z);
    }

    @Inject(
            method = "renderShadow",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onRenderShadow(PoseStack pMatrixStack, MultiBufferSource pBuffer, Entity pEntity, float pWeight, float pPartialTicks, LevelReader pLevel, float pSize, CallbackInfo ci) {
        if (OrthoviewClientEvents.isEnabled() &&
            pEntity == Minecraft.getInstance().player) {
            ci.cancel();
            return;
        }

        if (!FogOfWarClientEvents.isEnabled())
            return;

        BlockPos bp = pEntity.getOnPos();

        if (!FogOfWarClientEvents.isInBrightChunk(pEntity))
            ci.cancel();
    }
}
