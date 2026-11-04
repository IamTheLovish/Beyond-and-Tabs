package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

/**
 * No world effect from the fog, gated once at the shared entry: {@code PlayerList.broadcast(except, x, y, z, radius,
 * dimension, packet)} is how the server sends everything that happens AT a place to the players near it - sounds
 * (ServerLevel.playSeededSound), level events (levelEvent: 2001 a block breaking, door and dispenser sounds...), and
 * block events (ServerLevel.runBlockEvents: a chest lid, a piston, a bell, a note block). Gating each caller left the
 * next one open (the Overseer, round 13 on #510: block events took the same path ungated), so the rule lives here: a
 * player under fog only gets the packet when (x, z) is inside their vision (FogOfWarServerEvents.broadcastSeen, the
 * particle rule). Same range, dimension and except tests as vanilla. With fog off, vanilla's own loop runs.
 * Nothing in the mod calls this broadcast itself; per-player sends (destroyBlockProgress, explode) are gated in
 * ServerLevelEffectFogMixin.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListFogMixin {

    @Shadow @Final private List<ServerPlayer> players;

    @Inject(
            method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void reignofnether$fogGateBroadcast(@Nullable Player except, double x, double y, double z, double radius,
                                                ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci) {
        if (!FogOfWarServerEvents.isEnabled())
            return;
        FogOfWarServerEvents.broadcastSeen(players, except, x, y, z, radius, dimension, packet);
        ci.cancel();
    }
}
