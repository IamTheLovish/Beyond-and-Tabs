package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.modelling.motion.AnimStates;
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
}
