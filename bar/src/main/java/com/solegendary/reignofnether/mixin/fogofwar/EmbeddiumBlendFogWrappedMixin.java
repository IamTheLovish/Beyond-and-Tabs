package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogTintingBlockColor;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives the biome-tinted blocks (grass, ferns, leaves, water...) Embeddium's per-vertex biome blending back. Embeddium
 * 0.3.31's ColorProviderRegistry.registerBlocks skips any block whose BlockColor a mod replaced, and ClientModEvents
 * wraps every block's provider with FogTintingBlockColor for the fog of war - so Embeddium coloured each of those
 * blocks flat: the lawn read as a mosaic of one-block cells and GrassTint's soft variation as hard steps (eyes r47b,
 * Oct 29: the r7 mottle's 5-block patches drawn block by block). With this, Embeddium installs its blending adapter
 * (BiomeColors.getAverageGrassColor / FoliageColor per vertex corner), and BiomeColorsMixin - which already holds the
 * tint, the canopy shade and the fog (explored and never-explored) - runs per corner. The quads only the fog colours
 * (FogTintingBlockColor.FOG_ONLY_TINT_INDEX) are handled by EmbeddiumFogOnlyQuadsMixin.
 * <p>
 * Optional like EmbeddiumRebuildCountMixin: {@code @Pseudo}, {@code require = 0} - without Embeddium nothing applies.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.color.ColorProviderRegistry", remap = false)
public abstract class EmbeddiumBlendFogWrappedMixin {

    @Redirect(method = "registerBlocks", remap = false, require = 0,
        at = @At(value = "INVOKE", remap = false,
            target = "Lit/unimi/dsi/fastutil/objects/ReferenceSet;contains(Ljava/lang/Object;)Z"))
    private boolean reignofnether$blendFogWrapped(ReferenceSet<?> overriden, Object block) {
        if (FogTintingBlockColor.BIOME_TINTED.contains(block))
            return false;   // overridden only by our fog wrapper: blend it (the fog is in BiomeColorsMixin)
        return overriden.contains(block);
    }
}
