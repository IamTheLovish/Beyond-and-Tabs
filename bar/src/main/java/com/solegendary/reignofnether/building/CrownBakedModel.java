package com.solegendary.reignofnether.building;

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
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The model of every leaves block: the block's own, except that the chunk mesh leaves out a leaf cell a living crown
 * draws ({@link CrownRenderClientEvents#hides}). The mesher asks {@link #getModelData} per block (Forge's and
 * Embeddium 0.3.x's both - EdgeOverlayBakedModel relies on the same path) and gets a DRAWN flag for those cells; the
 * crown renderer tessellates with {@link ModelData#EMPTY} and gets the real quads. Only our own position set is read
 * here, never the level (a meshing thread reading the client level crashed lovish's game on Oct 31, CanopyRegion).
 * Installed outside every other wrapper by CrownModelEvents.
 */
public class CrownBakedModel extends BakedModelWrapper<BakedModel> {

    /** True when the crown renderer draws this cell. */
    public static final ModelProperty<Boolean> DRAWN = new ModelProperty<>();
    private static final ModelData DRAWN_DATA = ModelData.builder().with(DRAWN, Boolean.TRUE).build();

    public CrownBakedModel(BakedModel original) {
        super(original);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData data) {
        ModelData base = super.getModelData(level, pos, state, data);
        if (!CrownRenderClientEvents.hides(pos.asLong()))
            return base;
        return base == ModelData.EMPTY ? DRAWN_DATA : base.derive().with(DRAWN, Boolean.TRUE).build();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, RenderType renderType) {
        if (Boolean.TRUE.equals(data.get(DRAWN)))
            return List.of();
        return super.getQuads(state, side, rand, data, renderType);
    }
}
