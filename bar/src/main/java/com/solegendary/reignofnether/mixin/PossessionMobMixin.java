package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.hero.PossessionServer;
import com.solegendary.reignofnether.perf.TickGates;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Possession (design/hero_mode.md): a possessed unit's AI - goals, navigation, move/look/jump control - is skipped
 * and the possessor's input applied instead (PossessionServer.drive). serverAiStep only runs on the server; every
 * other mob pays one empty-map check.
 *
 * Then the Oct 26 perf gates (TickGates): an idle unit thinks every 4th tick (F2), and a wild animal with no unit or
 * player within 32 blocks does not think (F3).
 */
@Mixin(Mob.class)
public abstract class PossessionMobMixin {

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void reignofnether$possessedAi(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (PossessionServer.drive(mob) || TickGates.skipIdleAi(mob) || TickGates.skipWildAi(mob))
            ci.cancel();
    }
}
