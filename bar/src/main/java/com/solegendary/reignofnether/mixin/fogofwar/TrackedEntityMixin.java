package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.match.Spectators;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// gate entity packets behind fog; target by name since TrackedEntity is package-private
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {

    @Shadow @Final Entity entity;

    @Shadow public abstract void removePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void reignofnether$gateEntityVisibility(ServerPlayer player, CallbackInfo ci) {
        // a watcher's camera body is never sent to anyone who plays (Spectators.isHiddenFrom); one already sent goes
        if (entity instanceof ServerPlayer body && Spectators.isHiddenFrom(body, player)) {
            removePlayer(player);
            ci.cancel();
            return;
        }
        if (!FogOfWarServerEvents.isFogActiveFor(player)) return;
        // block-level gate: an enemy entity hides unless its own column is inside the viewer's circle
        BlockPos bp = entity.blockPosition();
        if (FogOfWarServerEvents.isBlockVisibleFor(player, bp.getX(), bp.getZ())) return;
        ci.cancel();
    }
}
