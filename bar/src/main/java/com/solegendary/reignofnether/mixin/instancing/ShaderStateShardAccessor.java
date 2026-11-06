package com.solegendary.reignofnether.mixin.instancing;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.function.Supplier;

/** A shader shard's program (the instanced unit bodies compare it with the game's entity shaders). */
@Mixin(RenderStateShard.ShaderStateShard.class)
public interface ShaderStateShardAccessor {
    @Accessor("shader")
    Optional<Supplier<ShaderInstance>> bat$shader();
}
