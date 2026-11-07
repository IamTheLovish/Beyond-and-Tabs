package com.solegendary.reignofnether.mixin.instancing;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/**
 * The render type the buffer source's shared builder is open for. A demoted unit's replay asks the source for its own
 * passes' types, which ends the caller's batch and re-opens the one shared builder for another type; the instancer
 * reads this before the replay and asks for it again after, so the caller keeps writing into its own type.
 */
@Mixin(MultiBufferSource.BufferSource.class)
public interface BufferSourceAccessor {
    @Accessor("lastState")
    Optional<RenderType> bat$lastState();
}
