package com.solegendary.reignofnether.mixin.fire;

import com.solegendary.reignofnether.building.BuildingAmbienceClientEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Building animations (lovish, Oct 30: "smoke = working"): a lab's stack campfires don't smoke on their own. Their
 * smoke comes from BuildingAmbienceClientEvents, and only while the lab produces; an idle lab is quiet. Client only:
 * the campfire's client ticker is where vanilla makes its smoke column. Other campfires (hearths, braziers, every
 * building that is not a lab) are untouched.
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {

    @Inject(method = "particleTick", at = @At("HEAD"), cancellable = true)
    private static void bt$quietLabStack(Level level, BlockPos pos, BlockState state, CampfireBlockEntity campfire,
                                         CallbackInfo ci) {
        if (BuildingAmbienceClientEvents.isQuietStack(pos))
            ci.cancel();
    }
}
