package com.solegendary.reignofnether.mixin.instancing;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The light map's CPU image, the bytes the game uploads each frame: the instanced bodies read a unit's texel from it. */
@Mixin(LightTexture.class)
public interface LightTextureAccessor {
    @Accessor("lightPixels")
    NativeImage bat$lightPixels();
}
