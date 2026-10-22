package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.barfx.VfxClient;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Units standing in a spell's light are lit by it (design/vfx.md "Light"): the packed light every entity renderer
 * receives gets its block-light half raised to what the live effects cast at the entity's middle
 * ({@link VfxClient#blockLightAt}). Shader packs light entities from this same lightmap value, so it works under
 * Complementary as well as vanilla, and it costs one int compare per entity while no effect is lit. The sky half is
 * untouched, and the value is only ever raised (a dark unit next to a pillar brightens; nothing ever darkens).
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class VfxEntityLightMixin {

    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void reignofnether$vfxLight(Entity entity, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        int boost = VfxClient.blockLightAt(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
        if (boost <= 0)
            return;
        int packed = cir.getReturnValue();
        if (boost > LightTexture.block(packed))
            cir.setReturnValue(LightTexture.pack(boost, LightTexture.sky(packed)));
    }
}
