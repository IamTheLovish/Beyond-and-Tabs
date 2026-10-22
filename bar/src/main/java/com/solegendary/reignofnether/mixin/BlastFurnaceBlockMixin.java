package com.solegendary.reignofnether.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
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
     * every tick - ~340 in a big match. An unlit furnace with nothing in it does nothing in that ticker, so it gets
     * none. A LIT furnace keeps the vanilla ticker so its first tick still puts it out exactly as before (the
     * structure files store them lit with no fuel); that state change re-asks for the ticker, which is then
     * dropped. The client side never had a ticker.
     */
    @Inject(
            method = "getTicker",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T extends BlockEntity> void bt$noIdleServerTicker(Level pLevel, BlockState pState, BlockEntityType<T> pType,
                                                               CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        if (!pLevel.isClientSide && !pState.getValue(AbstractFurnaceBlock.LIT))
            cir.setReturnValue(null);
    }
}
