package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Client only: in the RTS view every hurt and death voice played twice. The server plays the voice and sends it
 * (LivingEntity.hurt), and the client's own damage and death events (handleDamageEvent, entity event 3) play a local
 * copy too. Vanilla drops that local copy because it has no player; ClientLevelMixin plays every sound in the RTS
 * view, so both copies were heard. Measured on an eyes tour ([Ears], PR 426): hurt 263 from the server + 427 local,
 * death 31 + 41. In the RTS view the local copy is skipped; the server's copy reaches the RTS player (0 lost), so
 * each blow and each death is heard once. Outside the RTS view nothing changes.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityVoiceMixin {

    @Shadow protected abstract SoundEvent getHurtSound(DamageSource source);
    @Shadow protected abstract SoundEvent getDeathSound();

    @Redirect(method = "handleDamageEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)Lnet/minecraft/sounds/SoundEvent;"))
    private SoundEvent bat$serverPlaysTheHurtVoice(LivingEntity self, DamageSource source) {
        return OrthoviewClientEvents.isEnabled() ? null : getHurtSound(source);
    }

    // entity event 3 is the only getDeathSound call in handleEntityEvent
    @Redirect(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getDeathSound()Lnet/minecraft/sounds/SoundEvent;"))
    private SoundEvent bat$serverPlaysTheDeathVoice(LivingEntity self) {
        return OrthoviewClientEvents.isEnabled() ? null : getDeathSound();
    }
}
