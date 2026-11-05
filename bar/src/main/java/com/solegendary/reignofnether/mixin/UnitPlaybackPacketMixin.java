package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.playback.StampedUnit;
import com.solegendary.reignofnether.unit.playback.UnitPlaybackClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the stamped playback (UnitPlaybackMixin) what each move or teleport packet carries, just before
 * ClientPacketListener hands it to Entity.lerpTo - after vanilla's ensureRunningOnSameThread, so on the main thread.
 * lerpTo itself only sees values, with what a packet lacks filled in from the entity; guessing the kind from values equal
 * to the drawn ones (which run 4-7 ticks behind the packets) filed a real turn as none after a server hitch (r1 of #539).
 * It also hands on each velocity packet, just before lerpMotion: a move past the blink limit that the heard velocity
 * explains (a unit thrown by a Ravager's roar) is drawn as motion, not as a blink (UnitPlayback.HEARD_SLACK).
 */
@Mixin(ClientPacketListener.class)
public abstract class UnitPlaybackPacketMixin {

    @Inject(method = "handleMoveEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFIZ)V"), require = 1)
    private void reignofnether$moveKind(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        UnitPlaybackClient.moving(packet.hasPosition(), packet.hasRotation());
    }

    // (1.20.1 hands lerpTo the teleport packet's own position: one call, no near-enough branch)
    @Inject(method = "handleTeleportEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFIZ)V"), require = 1)
    private void reignofnether$teleportKind(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        UnitPlaybackClient.moving(true, true);
    }

    @Inject(method = "handleSetEntityMotion",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpMotion(DDD)V"), require = 1)
    private void reignofnether$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(packet.getId());
        if (e instanceof StampedUnit unit)
            unit.reignofnether$heardMotion(packet.getXa() / 8000.0, packet.getYa() / 8000.0, packet.getZa() / 8000.0);
    }
}
