package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogTintingBlockColor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * The other half of EmbeddiumBlendFogWrappedMixin. FogTintingBakedModel gives a biome-tinted block's untinted quads
 * (the grass block's dirt sides) tint index FOG_ONLY_TINT_INDEX so the fog can darken them; Embeddium's blending
 * adapter ignores the tint index and would paint them the biome colour (green dirt). For those quads: the fog-only
 * colour, one per block, as FogTintingBlockColor gave them before. Embeddium passes the vanilla BakedQuad (its
 * BakedQuadMixin adds the view), so the tint index is read through vanilla's API.
 * <p>
 * Optional: {@code @Pseudo}, {@code require = 0}; the Embeddium types are coerced to Object.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.quad.blender.BlendedColorProvider", remap = false)
public abstract class EmbeddiumFogOnlyQuadsMixin {

    @Inject(method = "getColors", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void reignofnether$fogOnlyQuads(@Coerce Object view, BlockPos pos, Object state, @Coerce Object quad,
                                            int[] output, CallbackInfo ci) {
        if (quad instanceof BakedQuad bq && bq.getTintIndex() == FogTintingBlockColor.FOG_ONLY_TINT_INDEX) {
            int rgb = FogTintingBlockColor.fogOnlyColor(pos);
            // Embeddium's vertex colours are ABGR
            int abgr = 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
            Arrays.fill(output, abgr);
            ci.cancel();
        }
    }
}
