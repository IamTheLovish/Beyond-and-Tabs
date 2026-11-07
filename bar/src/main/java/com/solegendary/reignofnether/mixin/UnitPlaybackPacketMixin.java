package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.playback.UnitPlaybackClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the stamped playback (UnitPlaybackMixin) what each move or teleport packet carries, just before
 * ClientPacketListener hands it to Entity.lerpTo - after vanilla's ensureRunningOnSameThread, so on the main thread.
 * lerpTo itself only sees values, with what a packet lacks filled in from the entity; guessing the kind from values equal
 * to the drawn ones (which run 4-7 ticks behind the packets) filed a real turn as none after a server hitch (r1 of #539).
 */
@Mixin(ClientPacketListener.class)
public abstract class UnitPlaybackPacketMixin {

    @Inject(method = "handleMoveEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFIZ)V"), require = 1)
    private void reignofnether$moveKind(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        UnitPlaybackClient.moving(packet.hasPosition(), packet.hasRotation());
    }

    @Inject(method = "handleTeleportEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFIZ)V"), require = 1)
    private void reignofnether$teleportKind(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        UnitPlaybackClient.teleporting(packet.getX(), packet.getY(), packet.getZ());
    }
}
