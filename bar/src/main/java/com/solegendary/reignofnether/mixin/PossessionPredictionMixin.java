package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.hero.PossessionPredictionClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hero slice 3, movement prediction (PossessionPredictionClient): the client's copy of the unit this player possesses
 * counts as controlled by this client - the switch vanilla flips for a horse the local player rides. Vanilla then
 * ignores the server's position packets for it and runs its own travel physics on the client, so WASD moves it at
 * once. Client-side copies only (an integrated server shares entity ids with the client: its entities are skipped by
 * the isClientSide check); every other entity pays one int compare.
 */
@Mixin(Entity.class)
public abstract class PossessionPredictionMixin {

    @Inject(method = "isControlledByLocalInstance", at = @At("HEAD"), cancellable = true)
    private void reignofnether$predictPossessed(CallbackInfoReturnable<Boolean> cir) {
        int id = PossessionPredictionClient.controlledId;
        if (id < 0)
            return;
        Entity self = (Entity) (Object) this;
        if (self.getId() == id && self.level().isClientSide())
            cir.setReturnValue(true);
    }
}
