package com.solegendary.reignofnether.mixin.fogofwar;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Lets the fog tick re-run vanilla's per-player tracking decision for a unit (FogOfWarServerEvents.refreshEntityTracking).
 * Vanilla only re-decides when the entity or the player changes section, so without this a unit standing still would
 * stay hidden after a friendly walks up to it, or stay tracked after the vision that showed it moved away.
 * TrackedEntity is package-private, so it is targeted by name; callers cast the Object from ChunkMapAccessor's entity map.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityInvoker {

    @Invoker("updatePlayers")
    void reignofnether$updatePlayers(List<ServerPlayer> players);
}
