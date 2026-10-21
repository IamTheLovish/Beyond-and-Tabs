package com.solegendary.reignofnether.environment.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;

/**
 * The block entity of a sway limb block. It holds no data and never ticks: it exists so the client can draw the block
 * itself ({@link BranchWindRenderer}) and so the chunk mesher can be told to leave the block out while it does
 * ({@link #MESH_HIDDEN} in the Forge model data, read by {@link SwayBakedModel}).
 * <p>
 * Which limbs are drawn by the renderer is decided by {@link BranchWindClient} (the setting and the distance to the
 * camera): it flips {@link #meshHidden}, asks for a model data refresh and re-meshes the section. Beyond the sway
 * range the block is an ordinary block in the chunk mesh and costs nothing. On the server the entity is inert.
 */
public class SwayBlockEntity extends BlockEntity {

    /** True while the chunk mesh must leave this block out because the renderer draws it. */
    public static final ModelProperty<Boolean> MESH_HIDDEN = new ModelProperty<>();

    private static final ModelData HIDDEN = ModelData.builder().with(MESH_HIDDEN, Boolean.TRUE).build();
    private static final ModelData SHOWN = ModelData.builder().with(MESH_HIDDEN, Boolean.FALSE).build();

    /** Client: whether the mesh currently leaves this block out (the renderer draws it). */
    public boolean meshHidden = false;
    /** Client: the sway tier, cached from the state. */
    public final int tier;

    public SwayBlockEntity(BlockPos pos, BlockState state) {
        super(SwayBlocks.SWAY_BLOCK_ENTITY.get(), pos, state);
        this.tier = SwayBlocks.tier(state);
    }

    @Override
    public @NotNull ModelData getModelData() {
        return meshHidden ? HIDDEN : SHOWN;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide())
            BranchWindClient.register(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide())
            BranchWindClient.unregister(this);
    }
}
