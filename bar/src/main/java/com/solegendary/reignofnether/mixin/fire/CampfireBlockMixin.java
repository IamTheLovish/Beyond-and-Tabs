package com.solegendary.reignofnether.mixin.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CampfireBlock.class)
public abstract class CampfireBlockMixin extends BaseEntityBlock {

    private static final int DAMAGE_DELAY = 20; // higher == damage less often

    protected CampfireBlockMixin(Properties pProperties) {
        super(pProperties);
    }

    @Inject(
            method = "entityInside",
            at = @At("HEAD"),
            cancellable = true
    )
    public void entityInside(BlockState pState, Level pLevel, BlockPos pPos, Entity pEntity, CallbackInfo ci) {
        ci.cancel();
        if (pState.getValue(CampfireBlock.LIT) && pEntity instanceof LivingEntity && !EnchantmentHelper.hasFrostWalker((LivingEntity)pEntity) &&
            pEntity.tickCount % DAMAGE_DELAY == 0) {
            pEntity.hurt(pLevel.damageSources().inFire(), pState.getBlock() == Blocks.SOUL_CAMPFIRE ? 2 : 1);
        }
    }

    /**
     * No server ticker for campfires. Vanilla's server ticker only cooks / cools the items laid on the fire, and
     * nothing here ever puts an item on one (the structure files carry empty campfires), so it was ~390 idle
     * block-entity ticks a tick in a big match for no effect. LIT is read from the stored blockstate. The client
     * ticker (smoke and sparks) is untouched.
     */
    @Inject(
            method = "getTicker",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T extends BlockEntity> void bt$noServerTicker(Level pLevel, BlockState pState, BlockEntityType<T> pType,
                                                           CallbackInfoReturnable<BlockEntityTicker<T>> cir) {
        if (!pLevel.isClientSide)
            cir.setReturnValue(null);
    }
}
