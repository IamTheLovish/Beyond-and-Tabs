package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(
            method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
            at = @At("TAIL")
    )
    private void sendParticles(ServerPlayer pPlayer, boolean pLongDistance, double pPosX, double pPosY, double pPosZ,
                               Packet<?> pPacket, CallbackInfoReturnable<Boolean> cir) {
        boolean originalSuccess = false;
        BlockPos blockpos = pPlayer.blockPosition();
        if (blockpos.closerToCenterThan(new Vec3(pPosX, pPosY, pPosZ), pLongDistance ? 512.0 : 32.0))
            originalSuccess = true;

        // retry at further distance
        if (!originalSuccess && blockpos.closerToCenterThan(new Vec3(pPosX, pPosY, pPosZ), 512))
            pPlayer.connection.send(pPacket);
    }

    // [Sys] vanilla group (Oct 26 perf plan): the whole tick of each top-level entity, Units apart from the rest. The
    // sections timed inside (unit, travel, aiGoals...) are taken out by the timer's self time. Not re-entrant
    // (passengers tick through tickPassenger), so one field holds the open token.
    @Unique private int reignofnether$entityToken = 0;

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void reignofnether$entityTickStart(Entity entity, CallbackInfo ci) {
        reignofnether$entityToken = Sys.begin(entity instanceof Unit ? Sys.VANILLA_UNIT : Sys.VANILLA_OTHER);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void reignofnether$entityTickEnd(Entity entity, CallbackInfo ci) {
        int token = reignofnether$entityToken;
        reignofnether$entityToken = 0;
        Sys.end(token);
    }

    @Redirect(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tick(Ljava/util/function/BooleanSupplier;Z)V"))
    private void reignofnether$timedChunkTick(ServerChunkCache chunks, BooleanSupplier hasTimeLeft, boolean tickChunks) {
        int t = Sys.begin(Sys.CHUNKS);
        try {
            chunks.tick(hasTimeLeft, tickChunks);
        } finally {
            Sys.end(t);
        }
    }

    /**
     * Plan 014: the RTS registries are copied into their saved data at the start of every world save (autosave,
     * /save-all, pause, stop), so the save that follows writes them with the chunks - in place of the 30 s timers
     * that wrote all saved data mid-fight. {@code skipSave} is /save-off; RtsSaves skips the overworld's other
     * dimensions and the stop (whose handlers have saved and cleared the live lists by then).
     */
    @Inject(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At("HEAD"))
    private void reignofnether$snapshotRtsState(net.minecraft.util.ProgressListener progress, boolean flush,
                                                boolean skipSave, CallbackInfo ci) {
        if (!skipSave)
            com.solegendary.reignofnether.persistence.RtsSaves.snapshot((ServerLevel) (Object) this);
    }

    /** [Sys] vanilla group: ServerLevel.tick as a whole, so its self time is the part no other section names. */
    @Unique private int reignofnether$levelToken = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void reignofnether$levelTickStart(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        reignofnether$levelToken = Sys.begin(Sys.LEVEL_REST);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void reignofnether$levelTickEnd(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        int token = reignofnether$levelToken;
        reignofnether$levelToken = 0;
        Sys.end(token);
    }
}
