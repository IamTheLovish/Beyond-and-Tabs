package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

/**
 * The world effects that still reached a player under fog (Mode A review on #35, Oct 28, "Fog leaks"): each went out
 * by its own route, past the chunk gate (ChunkHolderMixin), the entity gate (TrackedEntityMixin), the particle gate
 * (ServerLevelParticleMixin) and the sound gate (ServerLevelSoundMixin). With remote players in a match a modified
 * client can show anything it receives, so each told where the enemy was:
 * <ul>
 *   <li>{@code destroyBlockProgress} - the crack overlay of a block being broken (a worker cutting, a unit digging),
 *       sent to every player within 32 blocks;</li>
 *   <li>{@code levelEvent} - e.g. 2001, a block breaking (its particles and sound: a building taking damage, a
 *       wall coming down), broadcast within 64 blocks;</li>
 *   <li>{@code explode} - ClientboundExplodePacket, sent within 64 blocks: the blast's particles and sound, the
 *       knockback, and the blocks it removes, which the client clears itself - under fog the chunk gate keeps those
 *       blocks frozen, but this packet broke them anyway.</li>
 * </ul>
 * Each now skips a player whose fog hides the spot (FogOfWarServerEvents.isBlockVisibleFor, the particle and sound
 * rule; true for anyone the fog does not apply to). With fog off nothing changes.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEffectFogMixin {

    @Redirect(
            method = "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V")
    )
    private void reignofnether$fogGateLevelEvent(PlayerList list, @Nullable Player except, double x, double y, double z,
                                                 double radius, ResourceKey<Level> dimension, Packet<?> packet) {
        FogOfWarServerEvents.broadcastSeen(list, except, x, y, z, radius, dimension, packet);
    }

    @Redirect(
            method = "destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V")
    )
    private void reignofnether$fogGateBreakProgress(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        if (packet instanceof ClientboundBlockDestructionPacket p) {
            BlockPos bp = p.getPos();
            if (!FogOfWarServerEvents.isBlockVisibleFor(connection.player, bp.getX(), bp.getZ()))
                return;
        }
        connection.send(packet);
    }

    @Redirect(
            method = "explode",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V")
    )
    private void reignofnether$fogGateExplosion(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        if (packet instanceof ClientboundExplodePacket p
                && !FogOfWarServerEvents.canHearAt(connection.player, p.getX(), p.getZ()))
            return;
        connection.send(packet);
    }
}
