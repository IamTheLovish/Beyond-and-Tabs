package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.modelling.motion.AnimStates;
import com.solegendary.reignofnether.unit.modelling.motion.Corpses;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client only: every damage event the server sends a client (ClientboundDamageEventPacket) lands here with its source,
 * so the living army's flinch knows the blow's direction (design/living_army.md, section 3). LivingEntity.hurtDir
 * is no use for this: vanilla sets it for players only.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMotionMixin {

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void bat$livingArmyHit(DamageSource source, CallbackInfo ci) {
        AnimStates.onDamage((LivingEntity) (Object) this, source);
    }

    // vanilla's puff of smoke as a dead mob is removed (entity event 60): a body the client keeps lying there
    // (Corpses, the death-pose contract) does not vanish in smoke first
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void bat$keptBodyNoPoof(byte id, CallbackInfo ci) {
        if (id == 60 && Corpses.keepsBody((LivingEntity) (Object) this))
            ci.cancel();
    }
}
