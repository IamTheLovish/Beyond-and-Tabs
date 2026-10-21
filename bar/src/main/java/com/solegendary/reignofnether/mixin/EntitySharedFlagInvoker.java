package com.solegendary.reignofnether.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Sprint while possessed (lovish, Oct 20: "doesn't feel like minecraft pov that allows players to sprint"): the
 * possessed unit wears vanilla's sprinting flag, so vanilla does the rest the same way it does for a player - the
 * sprint-jump push in LivingEntity.jumpFromGround and the sprint dust under the feet, seen by everyone. Vanilla's
 * public LivingEntity.setSprinting would also add the +30% speed attribute modifier, which possession applies itself
 * (PossessionServer.applyKeys, on the server and in the client's prediction alike) - so only the bare flag is set.
 */
@Mixin(Entity.class)
public interface EntitySharedFlagInvoker {

    @Invoker("setSharedFlag")
    void reignofnether$setSharedFlag(int flag, boolean set);
}
