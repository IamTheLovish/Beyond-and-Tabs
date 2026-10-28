package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

// gate entity packets behind fog; target by name since TrackedEntity is package-private
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {

    @Shadow @Final Entity entity;
    @Shadow @Final ServerEntity serverEntity;
    @Shadow @Final private Set<ServerPlayerConnection> seenBy;

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void reignofnether$gateEntityVisibility(ServerPlayer player, CallbackInfo ci) {
        // own/allied units: every chunk the owner is sent, not vanilla's circle (the RTS camera sits far off its view)
        if (FogOfWarServerEvents.tracksAcrossLoadedSquare(player, entity)) {
            if (seenBy.add(player.connection))
                serverEntity.addPairing(player);
            ci.cancel();
            return;
        }
        // shared check: own/allied units always, others only inside the viewer's current vision
        if (FogOfWarServerEvents.isEntityVisibleTo(player, entity)) return;
        // a living entity the player already tracks is dropped too, or it keeps streaming its moves from the fog
        // (FogOfWarServerEvents.refreshEntityTracking re-runs this every fog tick)
        if (entity instanceof LivingEntity && seenBy.remove(player.connection)) {
            serverEntity.removePairing(player);
            FogOfWarServerEvents.onHiddenByFog(player, entity);
        }
        ci.cancel();
    }
}
