package com.solegendary.reignofnether.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlastFurnaceBlock.class)
public abstract class BlastFurnaceBlockMixin {

    /**
     * Blast furnaces are scenery (nothing smelts in them), but each one still ran vanilla's furnace server ticker
     * every tick - ~340 in a big match. With no fuel and no items that ticker changes nothing: it only flips LIT when
     * the burn time crosses zero, and a stored-lit furnace with no fuel keeps its LIT state (CI, Oct 22). So the
     * server gets no ticker; lit ones stay lit, unlit ones stay unlit, exactly as before. The client never had one.
     */
    @Inject(
            method = "getTicker",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T extends BlockEntity> void bt$noIdleServerTicker(Level pLevel, BlockState pState, BlockEntityType<T> pType,
                                                               CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        if (!pLevel.isClientSide)
            cir.setReturnValue(null);
    }
}
