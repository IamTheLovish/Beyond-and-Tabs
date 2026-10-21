package com.solegendary.reignofnether.environment.wind;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The baked model of a sway limb block: the vanilla wood / fence model, except that it has no quads when the chunk
 * mesher asks for a block the renderer is drawing ({@link SwayBlockEntity#MESH_HIDDEN} in the model data - the mesher
 * passes the block entity's data, {@link BranchWindRenderer} passes {@link ModelData#EMPTY} and gets the real quads).
 * Installed over every sway block's model by {@link BranchWindModEvents} after the fog tint wrapper.
 */
public class SwayBakedModel extends BakedModelWrapper<BakedModel> {

    public SwayBakedModel(BakedModel original) {
        super(original);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, RenderType renderType) {
        if (Boolean.TRUE.equals(data.get(SwayBlockEntity.MESH_HIDDEN)))
            return List.of();
        return super.getQuads(state, side, rand, data, renderType);
    }
}
