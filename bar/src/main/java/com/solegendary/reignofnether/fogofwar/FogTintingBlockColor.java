package com.solegendary.reignofnether.fogofwar;

import com.solegendary.reignofnether.worldborder.WorldBorderClientEvents;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// Wraps an existing BlockColor and multiplies the result by FOG_TINT_RGB in dark chunks.
// Biome-tinted blocks only get fogged on FOG_ONLY_TINT_INDEX quads - BiomeColorsMixin handles their tinted quads.
public class FogTintingBlockColor implements BlockColor {

    // tint index FogTintingBakedModel assigns to originally-untinted quads: fog only, never the block's own colour
    public static final int FOG_ONLY_TINT_INDEX = 1000;

    /**
     * The biome-tinted blocks: only their untinted quads are fogged here (BiomeColorsMixin fogs the biome colour). The
     * same list as Embeddium 0.3.31's per-vertex biome blending (ColorProviderRegistry.installOverrides): wrapping them
     * made Embeddium treat them as mod-overridden and colour every block flat - the lawn a mosaic of one-block cells
     * (eyes r47b, Oct 29). EmbeddiumBlendFogWrappedMixin gives them their blending back.
     */
    public static final java.util.Set<Block> BIOME_TINTED = java.util.Set.of(
            Blocks.GRASS_BLOCK, Blocks.FERN, Blocks.GRASS, Blocks.POTTED_FERN,
            Blocks.PINK_PETALS, Blocks.SUGAR_CANE, Blocks.LARGE_FERN, Blocks.TALL_GRASS,
            Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES, Blocks.VINE, Blocks.MANGROVE_LEAVES,
            Blocks.WATER, Blocks.BUBBLE_COLUMN
    );

    @Nullable private final BlockColor delegate;
    private final boolean biomeTinted;

    public FogTintingBlockColor(@Nullable BlockColor delegate, boolean biomeTinted) {
        this.delegate = delegate;
        this.biomeTinted = biomeTinted;
    }

    @Override
    public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        boolean fogOnly = tintIndex == FOG_ONLY_TINT_INDEX;
        int base = (delegate != null && !fogOnly) ? delegate.getColor(state, level, pos, tintIndex) : -1;
        if (pos == null || level == null) return base;
        if (biomeTinted && !fogOnly)
            return base;   // BiomeColorsMixin fogs it, never-explored dimming included (Oct 29: one place for both renderers)

        int tint = fogTint(pos);
        if (tint == 0) return base;

        int original = (base == -1) ? 0xFFFFFF : base;
        int r = (((original >> 16) & 0xFF) * ((tint >> 16) & 0xFF)) / 255;
        int g = (((original >> 8)  & 0xFF) * ((tint >> 8)  & 0xFF)) / 255;
        int b = (( original        & 0xFF) * ( tint        & 0xFF)) / 255;
        return (r << 16) | (g << 8) | b;
    }

    /** The fog (or outside-the-border) tint at a block, 0 for none. */
    private static int fogTint(BlockPos pos) {
        if (WorldBorderClientEvents.isOutsideWorldBorder(pos))
            return WorldBorderClientEvents.OUTSIDE_WORLD_BORDER_TINT;
        if (FogOfWarClientEvents.isEnabled())
            return FogOfWarClientEvents.fogTintAt(pos.getX(), pos.getZ());
        return 0;
    }

    /** An untinted quad's colour under the fog (FOG_ONLY_TINT_INDEX): the tint, or white where none - one per block. */
    public static int fogOnlyColor(BlockPos pos) {
        int tint = fogTint(pos);
        return tint == 0 ? 0xFFFFFF : tint;
    }
}
