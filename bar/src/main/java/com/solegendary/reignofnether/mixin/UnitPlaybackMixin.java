package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.modelling.motion.UnitPlayback;
import com.solegendary.reignofnether.unit.playback.UnitPlaybackClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Units are drawn from their stamped positions (motion fluidity, UnitPlayback; the shared entry every unit's movement
 * passes on the client): ClientPacketListener hands every move and teleport packet to LivingEntity.lerpTo, which here
 * files it in the unit's Track under the server tick the last clock stamp named, instead of starting vanilla's
 * 3-client-tick lerp (which stalls whenever a tick runs late: 4-8% of client ticks at 500-799 units, 27% at 14.5 TPS).
 * aiStep, once a client tick, then places the unit where the playback clock says, before vanilla's own lerp (never
 * started) would have run. A rotation-only packet comes through lerpTo with the drawn position itself: filed as a turn
 * on the stamped position, not as a move to wherever the unit is drawn. A position-only packet comes with the drawn
 * yaw: filed without a yaw. Client-side copies only; anything else keeps vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class UnitPlaybackMixin extends Entity {

    @Shadow protected int lerpSteps;

    @Unique private UnitPlayback.Track reignofnether$track;
    @Unique private final double[] reignofnether$drawn = new double[4];

    protected UnitPlaybackMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "lerpTo(DDDFFIZ)V", at = @At("HEAD"), cancellable = true)
    private void reignofnether$stamp(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport,
                                     CallbackInfo ci) {
        if (!level().isClientSide() || !UnitPlaybackClient.drawsFromStamps((LivingEntity) (Object) this))
            return;
        long tick = UnitPlaybackClient.CLOCK.newest();
        UnitPlayback.Track track = reignofnether$track;
        if (track == null) {
            track = reignofnether$track = new UnitPlayback.Track(getType().updateInterval());
        }
        if (!track.seeded())   // where its spawn packet (or vanilla, before the clock ran) left it
            track.seed(tick - getType().updateInterval(), getX(), getY(), getZ(), getYRot());
        boolean drawnHere = x == getX() && y == getY() && z == getZ();
        if (drawnHere)
            track.turned(tick, yRot);
        else
            track.moved(tick, x, y, z, yRot == getYRot() ? Float.NaN : yRot);
        setXRot(xRot);
        lerpSteps = 0;
        ci.cancel();
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void reignofnether$draw(CallbackInfo ci) {
        UnitPlayback.Track track = reignofnether$track;
        if (track == null || !level().isClientSide())
            return;
        if (!UnitPlaybackClient.drawsFromStamps((LivingEntity) (Object) this)) {
            reignofnether$track = null;   // vanilla again (the clock stopped, it is possessed or riding): start fresh later
            return;
        }
        double[] d = reignofnether$drawn;
        track.draw(UnitPlaybackClient.CLOCK, d);
        lerpSteps = 0;
        setPos(d[0], d[1], d[2]);
        setYRot((float) d[3]);
    }
}
