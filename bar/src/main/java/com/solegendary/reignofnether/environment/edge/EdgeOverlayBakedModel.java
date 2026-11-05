package com.solegendary.reignofnether.environment.edge;

import com.solegendary.reignofnether.config.DisplaySettings;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The model of a path / dirt / sand / gravel block with the edge overlays (lovish, Oct 30: "build edge overlays"):
 * its own quads, plus - where grass touches it - ragged grass fringe quads over its top (EdgeOverlayRules picks them).
 * <ul>
 *   <li><b>When:</b> at chunk-mesh time. {@link #getModelData} reads the 8 horizontal neighbours once per block per
 *   mesh build and hands the mesher the cached quad list for that block's overlay key; nothing runs per frame.</li>
 *   <li><b>Render type:</b> the blocks are SOLID, whose shader ignores alpha (the fringe's holes would draw as dark
 *   squares), so a block with a fringe reports SOLID + CUTOUT_MIPPED: its own quads in the solid pass, only the fringe
 *   in the cutout one. Mipped, because a 128x cutout sprite without mipmaps shimmers from the RTS camera (AGENTS.md
 *   §4). Forge's chunk mesher and Embeddium 0.3.x both ask getModelData / getRenderTypes / getQuads(..., renderType)
 *   per block (FramedBlocks and Chipped in our pack rely on the same path).</li>
 *   <li><b>Colour:</b> the quads carry FogTintingBlockColor.EDGE_GRASS_TINT_INDEX - the grass colour at this block,
 *   through the same BiomeColors path the lawn uses (GrassTint patches, canopy shade, fog); no new BlockColor.</li>
 *   <li><b>Wrapper order:</b> installed INSIDE the fog wrapper (EdgeOverlayModelEvents runs at HIGH priority, the fog
 *   tint at NORMAL), so FogTintingBakedModel passes our data and render types through and sees our quads - their tint
 *   index is not -1, so it leaves them as they are (and allocates nothing for them).</li>
 * </ul>
 * The client option {@code path_edges} (Display settings) turns it off; the overlays then cost one config read a block.
 */
public class EdgeOverlayBakedModel extends BakedModelWrapper<BakedModel> {

    private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutoutMipped());

    private final EdgeOverlayQuads quads;
    // the base model's render types and their union with the cutout layer (the base set is one instance per block)
    private volatile ChunkRenderTypeSet[] withFringe = null;

    public EdgeOverlayBakedModel(BakedModel original, EdgeOverlayQuads quads) {
        super(original);
        this.quads = quads;
    }

    /** A neighbour that grows the fringe: a grass-like top at the same height, open to the sky above it. */
    static boolean isGrass(BlockAndTintGetter level, BlockPos.MutableBlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!(s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.PODZOL)))
            return false;
        // a snowy grass top is white: no green fringe beside it
        if (s.hasProperty(BlockStateProperties.SNOWY) && s.getValue(BlockStateProperties.SNOWY))
            return false;
        p.move(Direction.UP);
        boolean covered = level.getBlockState(p).isSolidRender(level, p);
        p.move(Direction.DOWN);
        return !covered;
    }

    private static final int[] DX = {0, 1, 0, -1, 1, 1, -1, -1};
    private static final int[] DZ = {-1, 0, 1, 0, -1, 1, 1, -1};
    // bit for each offset above: N, E, S, W, NE, SE, SW, NW
    private static final int[] BIT = {EdgeOverlayRules.N, EdgeOverlayRules.E, EdgeOverlayRules.S, EdgeOverlayRules.W,
            EdgeOverlayRules.NE, EdgeOverlayRules.SE, EdgeOverlayRules.SW, EdgeOverlayRules.NW};

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        if (!quads.ready() || !DisplaySettings.pathEdges())
            return base;
        int mask = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {
            p.set(pos.getX() + DX[i], pos.getY(), pos.getZ() + DZ[i]);
            if (isGrass(level, p)) mask |= BIT[i];
        }
        int key = EdgeOverlayRules.key(mask, pos.getX(), pos.getY(), pos.getZ());
        if (key == 0)
            return base;
        ModelData ours = quads.data(key);
        if (base == ModelData.EMPTY)
            return ours;
        return base.derive().with(EdgeOverlayQuads.OVERLAY, ours.get(EdgeOverlayQuads.OVERLAY)).build();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                     @NotNull ModelData data) {
        ChunkRenderTypeSet base = super.getRenderTypes(state, rand, data);
        if (data.get(EdgeOverlayQuads.OVERLAY) == null)
            return base;
        ChunkRenderTypeSet[] cached = withFringe;
        if (cached != null && cached[0] == base)
            return cached[1];
        ChunkRenderTypeSet union = ChunkRenderTypeSet.union(base, CUTOUT);
        withFringe = new ChunkRenderTypeSet[]{base, union};
        return union;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, RenderType renderType) {
        List<BakedQuad> fringe = data.get(EdgeOverlayQuads.OVERLAY);
        if (fringe == null || renderType != RenderType.cutoutMipped())
            return super.getQuads(state, side, rand, data, renderType);
        // the cutout pass of a block with a fringe: only the fringe (the base blocks draw nothing in it), on the top
        // face's list, so it is culled with the top when a block sits on it
        return side == Direction.UP ? fringe : List.of();
    }
}
