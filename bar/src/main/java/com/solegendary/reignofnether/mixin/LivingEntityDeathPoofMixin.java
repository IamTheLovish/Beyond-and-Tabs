package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.modelling.motion.DeathCollapseClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client only: the server's "death poof" (entity event 60, LivingEntity.makePoofParticles - a burst of white smoke when
 * a dead mob is removed) is skipped for a unit that is collapsing (DeathCollapseClient): the body is still lying there,
 * sinking away, and the white cloud covered it in the 20_combat stills (eyes run 40703756932). Any other entity, and a
 * unit whose plain death played (over the cap, setting off, a flyer), keeps vanilla's poof.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathPoofMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void bat$noPoofWhileCollapsing(byte id, CallbackInfo ci) {
        if (id == 60 && DeathCollapseClient.collapsing((LivingEntity) (Object) this))
            ci.cancel();
    }
}
