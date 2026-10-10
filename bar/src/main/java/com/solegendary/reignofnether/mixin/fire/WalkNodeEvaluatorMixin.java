package com.solegendary.reignofnether.mixin.fire;

import com.solegendary.reignofnether.blocks.BlockUtils;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getBlockPathTypeStatic;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin extends NodeEvaluator {

    public WalkNodeEvaluatorMixin() {
    }

    @Inject(
            method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;",
            at = @At("HEAD"),
            cancellable = true
    )
    public void getBlockPathType(BlockGetter pLevel, int pX, int pY, int pZ, CallbackInfoReturnable<BlockPathTypes> cir) {
        if (!(this.mob instanceof Unit))
            return;

        BlockPos pos = new BlockPos(pX, pY, pZ);
        BlockState blockStateBelow = pLevel.getBlockState(pos.below());
        Block blockBelow = blockStateBelow.getBlock();
        BlockState blockState = pLevel.getBlockState(pos);
        Block block = blockState.getBlock();

        // allow units to walk on fire and magma but not leaves (to prevent workers getting stuck in trees)
        if (block == Blocks.FIRE || blockBelow == Blocks.FIRE ||
            block == Blocks.MAGMA_BLOCK || blockBelow == Blocks.MAGMA_BLOCK)
            cir.setReturnValue(BlockPathTypes.WALKABLE);
        else if (block == Blocks.POINTED_DRIPSTONE || blockBelow == Blocks.POINTED_DRIPSTONE)
            cir.setReturnValue(BlockPathTypes.UNPASSABLE_RAIL);
        else if (BlockUtils.isLeafBlock(blockStateBelow))
            cir.setReturnValue(BlockPathTypes.DAMAGE_FIRE);
        // same rules as the RTS grid (WalkabilityBuilder): an azalea bush is "non-solid" and pathfindable to vanilla
        // but has a full collision box - units were routed into the Heartwood Hall's bushes and pushed at them forever -
        // and a cauldron's rim is no floor (the unit drops into the bowl and can't step back out)
        else if (!blockState.isSolid() && WalkabilityBuilder.blocksBody(pLevel, pos, blockState))
            cir.setReturnValue(BlockPathTypes.BLOCKED);
        else if (WalkabilityBuilder.isPitFloor(blockStateBelow))
            cir.setReturnValue(BlockPathTypes.BLOCKED);
        else {
            BlockPathTypes bpt = getBlockPathTypeStatic(pLevel, new BlockPos.MutableBlockPos(pX, pY, pZ));
            cir.setReturnValue(bpt);
        }
    }
}
