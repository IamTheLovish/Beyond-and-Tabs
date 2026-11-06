package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.environment.light.LeafLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crown lighting (environment/light/LeafLight): a see-through leaf block shades the faces round it in smooth lighting
 * by {@link LeafLight#leafShade()} instead of vanilla's full-box 0.2. One entry for every leaf of every tree - vanilla,
 * modded subclasses of LeavesBlock, Verdant building crowns - in vanilla's renderer and Embeddium's alike (both ask the
 * block state, which asks this method), and so in our shader pack, which reads that vertex AO. The mod's decayable wart
 * blocks (LeavesBlock to the game, solid wart to the eye) keep the vanilla shade.
 */
@Mixin(BlockBehaviour.class)
public abstract class LeafShadeMixin {

    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void reignofnether$leafShade(BlockState state, BlockGetter level, BlockPos pos,
                                         CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LeavesBlock leaves && LeafLight.porous(leaves))
            cir.setReturnValue(LeafLight.leafShade());
    }
}
