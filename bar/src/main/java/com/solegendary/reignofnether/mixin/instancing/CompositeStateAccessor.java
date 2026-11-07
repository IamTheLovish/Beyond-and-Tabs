package com.solegendary.reignofnether.mixin.instancing;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A render type's shader shard (the instanced unit bodies reproduce only the entity cutout and eyes shaders). */
@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor("shaderState")
    RenderStateShard.ShaderStateShard bat$shaderState();
}
