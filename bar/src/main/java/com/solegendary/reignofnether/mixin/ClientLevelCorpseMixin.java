package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.modelling.motion.Corpses;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client only: the server's removal of an entity reaches the client here; a dead unit whose fall was drawn is kept as
 * a body for a few seconds (unit/modelling/motion/Corpses, the death-pose contract). The removal itself goes on as
 * normal - the entity leaves the level and every list - Corpses only keeps a reference to draw.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelCorpseMixin {

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void bat$keepCorpse(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        Corpses.onRemove(((ClientLevel) (Object) this).getEntity(id));
    }
}
