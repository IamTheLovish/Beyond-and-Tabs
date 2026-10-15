package com.solegendary.reignofnether.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.solegendary.reignofnether.unit.modelling.renderers.MipmappedTextures;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mipmaps for the generated unit bodies (claude/unit-mipmaps.md): vanilla's SimpleTexture.doLoad uploads one level;
 * for a location registered in MipmappedTextures we upload the level plus its mips instead. doLoad is where both of
 * load's paths end (directly on the render thread, or through recordRenderCall from a loader thread), and the texture
 * object stays a plain SimpleTexture, which Iris / Oculus need to find the unit's _n / _s maps.
 *
 * require = 0: if a Minecraft update renames doLoad, units only lose their mipmaps rather than the game failing to
 * start - and the eyes tour logs a WARN when no registered texture got them (MipmappedTextures.reportForEyes).
 */
@Mixin(SimpleTexture.class)
public abstract class SimpleTextureMixin {

    @Shadow @Final protected ResourceLocation location;

    @Inject(method = "doLoad", at = @At("HEAD"), cancellable = true, require = 0)
    private void reignofnether$uploadWithMips(NativeImage image, boolean blur, boolean clamp, CallbackInfo ci) {
        if (MipmappedTextures.upload(this.location, ((AbstractTexture) (Object) this).getId(), image, blur, clamp))
            ci.cancel();
    }
}
