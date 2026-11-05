package com.solegendary.reignofnether.unit.playback;

/**
 * A client-side living entity drawn from its stamps (UnitPlaybackMixin adds this to LivingEntity): how the velocity
 * packet glue (UnitPlaybackPacketMixin) hands the server's velocity to the unit's UnitPlayback.Track.
 */
public interface StampedUnit {

    /** The server says this entity moves at (vx, vy, vz) blocks a tick (ClientboundSetEntityMotionPacket). */
    void reignofnether$heardMotion(double vx, double vy, double vz);
}
