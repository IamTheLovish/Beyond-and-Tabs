package com.solegendary.reignofnether.environment.ground;

import com.solegendary.reignofnether.config.DisplaySettings;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The model of a ground block (GroundSheetRules.TABLE: grass, dirt, coarse dirt, path, gravel, mud, podzol) whose top
 * is one cell of an N x N sheet (lovish, Nov 4: "vary the block sizes" = pattern sizes, no block grid), and - on the
 * lawn - one floating clover quad over three blocks in four (concept C, the clover carpet). Athena's {@code
 * athena:giant} loader cannot do it: it puts the sheet on all six faces, and a grass block's sides must stay dirt +
 * turf overlay. So, like the edge overlays:
 * <ul>
 *   <li><b>When:</b> at chunk-mesh time. {@link #getModelData} puts the block's cell (GroundSheetRules.cell of its x
 *   and z) and clover pick (CloverLayerRules.pick of its position) into the model data, one shared instance per pair;
 *   {@link #getQuads} swaps the top quad for its cached copy on that cell's sprite (GroundSheet) and appends the cached
 *   clover quad (CloverLayer). Nothing runs per frame and nothing is allocated per block.</li>
 *   <li><b>What changes:</b> only the UVs and the sprite of the top quads (the block's top sprite and its _v2.._v6
 *   variants, on the up face, wherever they sit: the path's top is at 15/16); vertices, tint index, direction, shade
 *   and ambient occlusion stay. Sides, overlay and bottom pass through as they are.</li>
 *   <li><b>Render type:</b> the block's own layer passes through: vanilla's grass block is CUTOUT_MIPPED, dirt / path /
 *   gravel / mud / podzol SOLID. The clover needs CUTOUT_MIPPED (mostly transparent; mipped, or a 128x cutout sprite
 *   shimmers from the RTS camera). On the grass block it simply joins the top's list in its own cutout pass; a base
 *   model that does NOT draw in that layer (a pack that made grass SOLID) gets SOLID + CUTOUT_MIPPED, with only the
 *   clover in the cutout pass, exactly as EdgeOverlayBakedModel adds its fringe. Which case applies is read once per
 *   reload from the base model (GiantGroundModelEvents), not per block, because a weighted model's getRenderTypes
 *   draws from the same random the mesher then seeds getQuads with.</li>
 *   <li><b>Culling:</b> the clover is on the top face's list (side UP), so it is culled with the top when a block
 *   covers it.</li>
 *   <li><b>Wrapper order:</b> installed INSIDE the fog wrapper (GiantGroundModelEvents runs at HIGH priority, the fog
 *   tint at NORMAL), so FogTintingBakedModel forwards getModelData / getRenderTypes / getQuads to us and sees our quads.
 *   EdgeOverlayBakedModel (also HIGH) wraps dirt / path / gravel too; either order composes - each derives the other's
 *   model data, the fringe pass returns the fringe (no top sprite, nothing to swap) and the solid pass reaches us.</li>
 * </ul>
 * Off per block when its sheet is not in the pack or its option ({@code lawn_sheet} for the grass, {@code
 * ground_sheets} for the rest) is false, and the clover when its sheets are missing or {@code clover_layer} is false:
 * then this costs a config read or two a block and the base model draws as before.
 */
public class GiantGroundBakedModel extends BakedModelWrapper<BakedModel> {

    /** The sheet cell (1..N*N) this block's top shows; absent = the block draws its own top. */
    static final ModelProperty<Integer> CELL = new ModelProperty<>();
    /** The clover pick (1..CloverLayerRules.PICKS) over this block's top; absent = none. */
    static final ModelProperty<Integer> CLOVER = new ModelProperty<>();

    private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutoutMipped());
    // the shared model data of each (cell, clover) pair, filled on first use (a race only builds an equal instance)
    private static final ModelData[][] SHARED =
            new ModelData[GroundSheetRules.MAX_CELLS + 1][CloverLayerRules.PICKS + 1];

    private final GroundSheet sheet;
    private final CloverLayer clover;   // null: this block has no clover
    // whether the base model draws in the cutout-mipped layer (read at BakingCompleted; vanilla grass does)
    private volatile boolean baseCutout = true;
    // the base model's render types and their union with the cutout layer (the base set is one instance per block)
    private volatile ChunkRenderTypeSet[] withClover = null;

    public GiantGroundBakedModel(BakedModel original, GroundSheet sheet, CloverLayer clover) {
        super(original);
        this.sheet = sheet;
        this.clover = clover;
    }

    BakedModel original() {
        return originalModel;
    }

    GroundSheet sheet() {
        return sheet;
    }

    CloverLayer clover() {
        return clover;
    }

    void setBaseCutout(boolean baseCutout) {
        this.baseCutout = baseCutout;
        this.withClover = null;
    }

    private static ModelData shared(int cell, int pick) {
        ModelData d = SHARED[cell][pick];
        if (d == null) {
            ModelData.Builder b = ModelData.builder();
            if (cell > 0) b.with(CELL, cell);
            if (pick > 0) b.with(CLOVER, pick);
            d = b.build();
            SHARED[cell][pick] = d;
        }
        return d;
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        int n = sheet.on() ? sheet.size() : 0;
        int cell = n > 0 ? GroundSheetRules.cell(pos.getX(), pos.getZ(), n) : 0;
        int pick = clover != null && clover.ready() && DisplaySettings.cloverLayer()
                ? CloverLayerRules.pick(pos.getX(), pos.getY(), pos.getZ()) : 0;
        if (cell == 0 && pick == 0)
            return base;
        if (base == ModelData.EMPTY)
            return shared(cell, pick);
        ModelData.Builder b = base.derive();
        if (cell > 0) b.with(CELL, cell);
        if (pick > 0) b.with(CLOVER, pick);
        return b.build();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                     @NotNull ModelData data) {
        ChunkRenderTypeSet base = super.getRenderTypes(state, rand, data);
        if (baseCutout || data.get(CLOVER) == null)
            return base;
        ChunkRenderTypeSet[] cached = withClover;
        if (cached != null && cached[0] == base)
            return cached[1];
        ChunkRenderTypeSet union = ChunkRenderTypeSet.union(base, CUTOUT);
        withClover = new ChunkRenderTypeSet[]{base, union};
        return union;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, RenderType renderType) {
        Integer pick = clover == null ? null : data.get(CLOVER);
        // a base model without a cutout layer: the cutout pass getRenderTypes added is the clover's alone (a
        // SimpleBakedModel ignores the render type it is asked for, so the base must not be asked in it)
        if (pick != null && !baseCutout && renderType == RenderType.cutoutMipped())
            return side == Direction.UP ? clover.only(pick) : List.of();
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        if (side == Direction.UP || side == null) {
            Integer cell = data.get(CELL);
            if (cell != null)
                quads = sheet.swap(quads, cell);
        }
        // the grass block's own cutout pass (or every pass at once: null), on the top's cull list
        if (pick != null && side == Direction.UP && (renderType == null || renderType == RenderType.cutoutMipped()))
            quads = clover.with(quads, pick);
        return quads;
    }
}
