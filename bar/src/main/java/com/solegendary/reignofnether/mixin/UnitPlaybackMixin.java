package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.modelling.motion.UnitPlayback;
import com.solegendary.reignofnether.unit.playback.StampedUnit;
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
 * files it in the unit's Track under the server tick the last clock stamp named - with what the packet carries, which
 * UnitPlaybackPacketMixin read from the packet itself - instead of starting vanilla's 3-client-tick lerp (which stalls
 * whenever a tick runs late: 4-12% of client ticks at 500-799 units, 16-29% at 14.5 TPS). The head-rotation packet
 * (lerpHeadTo) goes into the same Track, so head, body and pitch are drawn on one delay, and so does the server's velocity
 * (StampedUnit, from the velocity packet), which tells a throw from a blink. aiStep, once a client tick, then
 * places and turns the unit where the playback clock says, before vanilla's own lerps (never started) would have run.
 * Client-side copies of units only; anything else keeps vanilla. Each injection must apply (require = 1): a half-applied
 * mixin - packets filed, never drawn - would freeze every unit without a word.
 */
@Mixin(LivingEntity.class)
public abstract class UnitPlaybackMixin extends Entity implements StampedUnit {

    @Shadow protected int lerpSteps;
    @Shadow protected int lerpHeadSteps;

    @Unique private UnitPlayback.Track reignofnether$track;
    @Unique private final double[] reignofnether$drawn = new double[6];

    protected UnitPlaybackMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "lerpTo(DDDFFIZ)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void reignofnether$stamp(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport,
                                     CallbackInfo ci) {
        int kind = UnitPlaybackClient.takePacketKind();   // taken for every lerpTo, so it never outlives its packet
        if (!level().isClientSide() || !UnitPlaybackClient.drawsFromStamps((LivingEntity) (Object) this))
            return;
        long tick = UnitPlaybackClient.CLOCK.newest();
        UnitPlayback.Track track = reignofnether$track;
        if (track == null)
            track = reignofnether$track = new UnitPlayback.Track(getType().updateInterval());
        if (!track.seeded())   // where its spawn packet (or vanilla, before the clock ran) left it
            track.seed(tick - getType().updateInterval(), getX(), getY(), getZ(), getYRot(), getYHeadRot(), getXRot());
        // not a tracker packet (kind < 0: another system placing it): it names where to stand and how to face
        track.packet(tick, kind < 0 ? UnitPlayback.Track.POS | UnitPlayback.Track.ROT : kind, x, y, z, yRot, xRot);
        lerpSteps = 0;
        ci.cancel();
    }

    /** The server's velocity for this unit (UnitPlaybackPacketMixin): it tells a throw from a blink (Track.motion). */
    @Override
    public void reignofnether$heardMotion(double vx, double vy, double vz) {
        UnitPlayback.Track track = reignofnether$track;
        if (track != null && level().isClientSide() && UnitPlaybackClient.drawsFromStamps((LivingEntity) (Object) this))
            track.motion(UnitPlaybackClient.CLOCK.newest(), vx, vy, vz);
    }

    @Inject(method = "lerpHeadTo(FI)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void reignofnether$head(float yHeadRot, int steps, CallbackInfo ci) {
        UnitPlayback.Track track = reignofnether$track;
        if (track == null || !level().isClientSide() || !UnitPlaybackClient.drawsFromStamps((LivingEntity) (Object) this))
            return;
        track.head(UnitPlaybackClient.CLOCK.newest(), yHeadRot);
        lerpHeadSteps = 0;
        ci.cancel();
    }

    @Inject(method = "aiStep", at = @At("HEAD"), require = 1)
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
        lerpHeadSteps = 0;
        setPos(d[0], d[1], d[2]);
        setYRot((float) d[3]);
        setYHeadRot((float) d[4]);
        setXRot((float) d[5]);
    }
}
