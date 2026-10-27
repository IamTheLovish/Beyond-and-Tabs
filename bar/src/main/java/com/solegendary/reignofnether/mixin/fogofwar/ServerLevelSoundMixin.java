package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

/**
 * No sound from the fog: world sounds the server broadcasts (level.playSound(null, ...), e.g. HeroFx, a unit eating)
 * skip a player under fog when the sound's column is outside their vision - the same rule as particles
 * (ServerLevelParticleMixin) and the mod's own positional sounds (SoundClientboundPacket via Audience.seenAt). The
 * client-side gate (ClientLevelMixin) only runs in the RTS view and lets every reignofnether sound through.
 * Same range and dimension test as PlayerList.broadcast; with fog off it calls straight through.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSoundMixin {

    @Redirect(
            method = {
                    "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
                    "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V")
    )
    private void reignofnether$fogGateSound(PlayerList list, @Nullable Player except, double x, double y, double z,
                                            double radius, ResourceKey<Level> dimension, Packet<?> packet) {
        if (!FogOfWarServerEvents.isEnabled()) {
            list.broadcast(except, x, y, z, radius, dimension, packet);
            return;
        }
        double r2 = radius * radius;
        for (ServerPlayer sp : list.getPlayers()) {
            if (sp == except || sp.level().dimension() != dimension)
                continue;
            double dx = x - sp.getX(), dy = y - sp.getY(), dz = z - sp.getZ();
            if (dx * dx + dy * dy + dz * dz < r2 && FogOfWarServerEvents.canHearAt(sp, x, z))
                sp.connection.send(packet);
        }
    }
}
