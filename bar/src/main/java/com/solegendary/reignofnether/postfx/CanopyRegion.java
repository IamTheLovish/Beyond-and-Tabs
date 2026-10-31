package com.solegendary.reignofnether.postfx;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;

/**
 * {@link CanopyShade} read from the level a grass colour call is given (mixin/fogofwar/BiomeColorsMixin): on a meshing
 * thread the meshing task's own region - Embeddium's WorldSlice (its own copy of the sections' light; outside its
 * reach it answers 0) or vanilla's RenderChunkRegion (light as vanilla's own meshing reads it). Never the client level:
 * lovish's crash of Oct 31 03:07 ("Missing Palette entry for index 21") was its getBlockState from an Embeddium
 * worker while the render thread wrote that chunk's palette. Light, not blocks: Embeddium's slice unpacks its
 * neighbours' blocks only within 2 of the section (and reuses the arrays between tasks), but holds the light of the
 * section and all its neighbours, so the taps (2 round), Embeddium's per-corner samples (1 more) and the cell over the
 * ground (1 up) are always inside it. A light read is a byte-array read: no palette. A read that throws leaves the
 * sample unshaded: plain grass, never a crash.
 */
public final class CanopyRegion {
    private CanopyRegion() { }

    /** rgb darkened by what stands over the ground round pos, read from {@code level} only. */
    public static int shade(BlockAndTintGetter level, BlockPos pos, int rgb) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        try {
            return CanopyShade.apply(rgb,
                    (x, y, z) -> CanopyShade.covered(level.getBrightness(LightLayer.SKY, m.set(x, y + 1, z))),
                    pos.getX(), pos.getY(), pos.getZ());
        } catch (RuntimeException e) {
            return rgb;
        }
    }
}
