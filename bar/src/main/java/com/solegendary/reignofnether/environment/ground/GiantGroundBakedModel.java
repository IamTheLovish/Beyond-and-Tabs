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
 * is one cell of an N x N sheet (lovish, Nov 4: "vary the block sizes" = pattern sizes, no block grid) - of one of up to
 * four sheet variants, picked per N x N square by a hash (round 64, lovish: "8x8 sheet + hashed pick") - and, on the
 * lawn, one floating clover quad over three blocks in four (concept C, the clover carpet) and one untinted flower quad
 * over ~6% of blocks, in drifts (concept C's "flower specks"). Athena's {@code
 * athena:giant} loader cannot do it: it puts the sheet on all six faces, and a grass block's sides must stay dirt +
 * turf overlay. So, like the edge overlays:
 * <ul>
 *   <li><b>When:</b> at chunk-mesh time. {@link #getModelData} puts the block's slot (GroundSheet.slot: its square's
 *   variant and its cell), clover pick (CloverLayerRules.pick) and flower pick (FlowerLayerRules.pick) into the model
 *   data, one shared instance per triple; {@link #getQuads} swaps the top quad for its cached copy on that slot's
 *   sprite (GroundSheet) and appends the cached clover and flower quads (TopLayers). Nothing runs per frame and
 *   nothing is allocated per block.</li>
 *   <li><b>What changes:</b> only the UVs and the sprite of the top quads (the block's top sprite and its _v2.._v6
 *   variants, on the up face, wherever they sit: the path's top is at 15/16); vertices, tint index, direction, shade
 *   and ambient occlusion stay. Sides, overlay and bottom pass through as they are.</li>
 *   <li><b>Render type:</b> the block's own layer passes through: vanilla's grass block is CUTOUT_MIPPED, dirt / path /
 *   gravel / mud / podzol SOLID. The clover and the flowers need CUTOUT_MIPPED (mostly transparent; mipped, or a 128x
 *   cutout sprite shimmers from the RTS camera). On the grass block they simply join the top's list in its own cutout
 *   pass; a base model that does NOT draw in that layer (a pack that made grass SOLID) gets SOLID + CUTOUT_MIPPED, with
 *   only the layers in the cutout pass, exactly as EdgeOverlayBakedModel adds its fringe. Which case applies is read once per
 *   reload from the base model (GiantGroundModelEvents), not per block, because a weighted model's getRenderTypes
 *   draws from the same random the mesher then seeds getQuads with.</li>
 *   <li><b>Culling:</b> the layers are on the top face's list (side UP), so they are culled with the top when a block
 *   covers it.</li>
 *   <li><b>Wrapper order:</b> installed INSIDE the fog wrapper (GiantGroundModelEvents runs at HIGH priority, the fog
 *   tint at NORMAL), so FogTintingBakedModel forwards getModelData / getRenderTypes / getQuads to us and sees our quads.
 *   EdgeOverlayBakedModel (also HIGH) wraps dirt / path / gravel too; either order composes - each derives the other's
 *   model data, the fringe pass returns the fringe (no top sprite, nothing to swap) and the solid pass reaches us.</li>
 * </ul>
 * Off per block when its sheet is not in the pack or its option ({@code lawn_sheet} for the grass, {@code
 * ground_sheets} for the rest) is false, the clover when its sprites are missing or {@code clover_layer} is false,
 * the flowers when theirs are or {@code flower_layer} is: then this costs a few config reads a block and the base
 * model draws as before.
 */
public class GiantGroundBakedModel extends BakedModelWrapper<BakedModel> {

    /** The sheet slot (1..K*N*N: variant, then cell) this block's top shows; absent = the block draws its own top. */
    static final ModelProperty<Integer> SLOT = new ModelProperty<>();
    /** The clover pick (1..CloverLayerRules.PICKS) over this block's top; absent = none. */
    static final ModelProperty<Integer> CLOVER = new ModelProperty<>();
    /** The flower pick (1..FlowerLayerRules.LAYERS) over this block's top; absent = none. */
    static final ModelProperty<Integer> FLOWER = new ModelProperty<>();

    private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutoutMipped());
    private static final int CLOVERS = CloverLayerRules.PICKS + 1, FLOWERS = FlowerLayerRules.LAYERS + 1;
    // the shared model data of each (slot, clover, flower) triple, filled on first use (a race only builds an equal
    // instance); 257 x 33 x 9 references
    private static final ModelData[] SHARED = new ModelData[(GroundSheetRules.MAX_SLOTS + 1) * CLOVERS * FLOWERS];

    private final GroundSheet sheet;
    private final TopLayers layers;   // null: this block has no floating layers (only the lawn has)
    // whether the base model draws in the cutout-mipped layer (read at BakingCompleted; vanilla grass does)
    private volatile boolean baseCutout = true;
    // the base model's render types and their union with the cutout layer (the base set is one instance per block)
    private volatile ChunkRenderTypeSet[] withLayers = null;

    public GiantGroundBakedModel(BakedModel original, GroundSheet sheet, TopLayers layers) {
        super(original);
        this.sheet = sheet;
        this.layers = layers;
    }

    BakedModel original() {
        return originalModel;
    }

    GroundSheet sheet() {
        return sheet;
    }

    TopLayers layers() {
        return layers;
    }

    void setBaseCutout(boolean baseCutout) {
        this.baseCutout = baseCutout;
        this.withLayers = null;
    }

    private static ModelData.Builder put(ModelData.Builder b, int slot, int clover, int flower) {
        if (slot > 0) b.with(SLOT, slot);
        if (clover > 0) b.with(CLOVER, clover);
        if (flower > 0) b.with(FLOWER, flower);
        return b;
    }

    private static ModelData shared(int slot, int clover, int flower) {
        int i = (slot * CLOVERS + clover) * FLOWERS + flower;
        ModelData d = SHARED[i];
        if (d == null) {
            d = put(ModelData.builder(), slot, clover, flower).build();
            SHARED[i] = d;
        }
        return d;
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int slot = sheet.on() ? sheet.slot(x, z) : 0;
        int clover = 0, flower = 0;
        if (layers != null) {
            if (layers.clover.ready() && DisplaySettings.cloverLayer())
                clover = CloverLayerRules.pick(x, y, z);
            if (layers.flowers.ready() && DisplaySettings.flowerLayer())
                flower = FlowerLayerRules.pick(x, y, z);
        }
        if (slot == 0 && clover == 0 && flower == 0)
            return base;
        if (base == ModelData.EMPTY && slot <= GroundSheetRules.MAX_SLOTS)
            return shared(slot, clover, flower);
        return put(base.derive(), slot, clover, flower).build();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                     @NotNull ModelData data) {
        ChunkRenderTypeSet base = super.getRenderTypes(state, rand, data);
        if (baseCutout || (data.get(CLOVER) == null && data.get(FLOWER) == null))
            return base;
        ChunkRenderTypeSet[] cached = withLayers;
        if (cached != null && cached[0] == base)
            return cached[1];
        ChunkRenderTypeSet union = ChunkRenderTypeSet.union(base, CUTOUT);
        withLayers = new ChunkRenderTypeSet[]{base, union};
        return union;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, RenderType renderType) {
        int clover = 0, flower = 0;
        if (layers != null) {
            Integer c = data.get(CLOVER), f = data.get(FLOWER);
            clover = c == null ? 0 : c;
            flower = f == null ? 0 : f;
        }
        boolean layered = clover > 0 || flower > 0;
        // a base model without a cutout layer: the cutout pass getRenderTypes added is the layers' alone (a
        // SimpleBakedModel ignores the render type it is asked for, so the base must not be asked in it)
        if (layered && !baseCutout && renderType == RenderType.cutoutMipped())
            return side == Direction.UP ? layers.only(clover, flower) : List.of();
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        if (side == Direction.UP || side == null) {
            Integer slot = data.get(SLOT);
            if (slot != null)
                quads = sheet.swap(quads, slot);
        }
        // the grass block's own cutout pass (or every pass at once: null), on the top's cull list
        if (layered && side == Direction.UP && (renderType == null || renderType == RenderType.cutoutMipped()))
            quads = layers.with(quads, clover, flower);
        return quads;
    }
}
