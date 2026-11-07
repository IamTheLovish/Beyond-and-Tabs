package com.solegendary.reignofnether.environment.lawn;

import com.solegendary.reignofnether.config.DisplaySettings;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The model of a grass block (snowy=false) whose top is one cell of the 8x8-block lawn sheet (lovish, Nov 4: big soft
 * patches that run across blocks, no per-block repeat). Athena's {@code athena:giant} loader cannot do it: it puts the
 * sheet on all six faces, and the grass block's sides must stay dirt + turf overlay. So, like the edge overlays:
 * <ul>
 *   <li><b>When:</b> at chunk-mesh time. {@link #getModelData} puts the block's cell (GiantLawnCells.cell of its x and
 *   z) into the model data, one shared instance per cell; {@link #getQuads} swaps the top quad for its cached copy on
 *   that cell's sprite (GiantLawnQuads). Nothing runs per frame and nothing is allocated per block.</li>
 *   <li><b>What changes:</b> only the UVs and the sprite of the quads drawn with the grass top (and its _v2.._v6
 *   variants) on the up face; vertices, tint index 0 (the biome grass colour through GrassTint / fog), direction and
 *   shade stay. The sides, the overlay and the bottom pass through as they are. The blockstate's variant pick still
 *   chooses the sides.</li>
 *   <li><b>Render type:</b> unchanged - getRenderTypes passes through (the top stays SOLID).</li>
 *   <li><b>Wrapper order:</b> installed INSIDE the fog wrapper (GiantLawnModelEvents runs at HIGH priority, the fog
 *   tint at NORMAL), so FogTintingBakedModel forwards getModelData / getQuads to us and sees our quads; their tint index
 *   is 0, so it leaves them untouched. Forge's chunk mesher and Embeddium 0.3.x both ask getModelData and the
 *   five-argument getQuads per block (the path the edge overlays and FramedBlocks use).</li>
 * </ul>
 * Off when the pack has no sheet (GiantLawnQuads.ready) or the client option {@code lawn_sheet} is false: then this
 * costs one config read a block and the base model draws as before.
 */
public class GiantLawnBakedModel extends BakedModelWrapper<BakedModel> {

    private final GiantLawnQuads quads;

    public GiantLawnBakedModel(BakedModel original, GiantLawnQuads quads) {
        super(original);
        this.quads = quads;
    }

    BakedModel original() {
        return originalModel;
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        if (!quads.ready() || !DisplaySettings.lawnSheet())
            return base;
        int cell = GiantLawnCells.cell(pos.getX(), pos.getZ());
        if (base == ModelData.EMPTY)
            return quads.data(cell);
        return base.derive().with(GiantLawnQuads.CELL, cell).build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        if (side != Direction.UP && side != null)
            return base;
        Integer cell = data.get(GiantLawnQuads.CELL);
        if (cell == null)
            return base;
        return quads.swap(base, cell);
    }
}
