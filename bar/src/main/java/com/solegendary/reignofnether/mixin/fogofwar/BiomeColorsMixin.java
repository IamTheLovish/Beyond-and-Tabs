package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogExploration;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.postfx.CanopyRegion;
import com.solegendary.reignofnether.postfx.GrassTint;
import com.solegendary.reignofnether.postfx.ShaderPacks;
import com.solegendary.reignofnether.worldborder.WorldBorderClientEvents;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// fog tint at BiomeColors so grass/leaves/water stay biome-blended under vanilla and Embeddium; grass and foliage first
// get the world-position tint noise (GrassTint, lovish Oct 22 20:23), so a field is not one flat green - the same
// path, so it is blended the same way (Embeddium samples it per corner: a smooth gradient, no block steps)
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {

    @Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
    private static void ron_fogTintGrass(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        vary(pos, cir, GrassTint.GRASS);
        shade(level, pos, cir);
        applyTint(level, pos, cir);
    }

    @Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void ron_fogTintFoliage(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        vary(pos, cir, GrassTint.FOLIAGE);
        applyTint(level, pos, cir);
    }

    @Inject(method = "getAverageWaterColor", at = @At("RETURN"), cancellable = true)
    private static void ron_fogTintWater(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        applyTint(level, pos, cir);
    }

    private static void vary(BlockPos pos, CallbackInfoReturnable<Integer> cir, int salt) {
        if (pos == null) return;
        cir.setReturnValue(GrassTint.apply(cir.getReturnValueI(), pos.getX(), pos.getZ(), salt));
    }

    // canopy shade (critic r40: "plain lane: trees float"): the lawn under and round a crown darker, plain lane only -
    // a shader pack draws real shadows there. Read from the level this colour call is given - on a meshing thread the
    // region's own copy (postfx/CanopyRegion; lovish's crash of Oct 31 03:07 was the client level read from here)
    private static void shade(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (level == null || pos == null || ShaderPacks.inUse()) return;
        cir.setReturnValue(CanopyRegion.shade(level, pos, cir.getReturnValueI()));
    }

    private static void applyTint(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (level == null || pos == null) return;
        int tint = 0;
        int original = cir.getReturnValueI();
        if (WorldBorderClientEvents.isOutsideWorldBorder(pos)) {
            tint = WorldBorderClientEvents.OUTSIDE_WORLD_BORDER_TINT;
        }
        else if (FogOfWarClientEvents.isEnabled()) {
            // The average-color sample straddles the block's W/N edges (and Embeddium samples 4 positions per
            // vertex). Test block-level visibility at all four touched corners and tint if any is fogged, so the
            // block-granular fog edge has no bright seam.
            int x = pos.getX();
            int z = pos.getZ();
            boolean allVisible =
                    FogOfWarClientEvents.isBlockVisible(x, z) &&
                            FogOfWarClientEvents.isBlockVisible(x - 1, z) &&
                            FogOfWarClientEvents.isBlockVisible(x, z - 1) &&
                            FogOfWarClientEvents.isBlockVisible(x - 1, z - 1);
            if (allVisible) return;
            tint = FogOfWarClientEvents.FOG_TINT_RGB;
        }
        if (tint == 0) return;
        int r = (((original >> 16) & 0xFF) * ((tint >> 16) & 0xFF)) / 255;
        int g = (((original >> 8)  & 0xFF) * ((tint >> 8)  & 0xFF)) / 255;
        int b = (( original        & 0xFF) * ( tint        & 0xFF)) / 255;
        int fogged = (r << 16) | (g << 8) | b;
        // never-explored ground goes on to the darker tint - here, not in FogTintingBlockColor (Oct 29): Embeddium's
        // per-vertex blending calls BiomeColors directly and never sees the wrapper
        if (tint == FogOfWarClientEvents.FOG_TINT_RGB && !FogOfWarClientEvents.isBlockVisible(pos)
                && !FogExploration.isExplored(pos.getX() >> 4, pos.getZ() >> 4))
            fogged = FogExploration.dimToUnexplored(fogged, FogOfWarClientEvents.FOG_TINT_RGB,
                    FogOfWarClientEvents.FOG_TINT_UNEXPLORED);
        cir.setReturnValue(fogged);
    }
}
