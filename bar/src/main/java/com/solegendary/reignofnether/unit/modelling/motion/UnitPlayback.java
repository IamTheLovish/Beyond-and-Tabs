package com.solegendary.reignofnether.unit.modelling.motion;

import net.minecraft.util.Mth;

/**
 * Where the client draws a unit between the server's position packets (motion fluidity, lovish Nov 5: "not look like
 * their lagging or bugging around"). Pure: no world, no entity; the client glue feeds it the packets and the ticks.
 * <p>
 * STEP 1 (this commit): today's rule, as the 1.20.1 client runs it - each position packet is walked to over exactly 3
 * client ticks (LivingEntity.lerpTo(..., 3) and aiStep's lerp), a rotation-only packet aims the position lerp at where
 * the unit is drawn now, and the clock is not used. UnitPlaybackTest measures it and fails where the game does.
 */
public final class UnitPlayback {

    private UnitPlayback() { }

    /** The server's tick clock as this client hears it: one stamp a server tick, read once a client tick. */
    public static final class Clock {
        private long newest = Long.MIN_VALUE;

        /** The server started tick {@code tick} (its clock packet arrived). */
        public void heard(long tick) {
            newest = Math.max(newest, tick);
        }

        /** Once a client tick, before the units are drawn for it. */
        public void clientTick() { }

        public boolean ready() {
            return newest != Long.MIN_VALUE;
        }

        /** The newest server tick heard. */
        public long newest() {
            return newest;
        }

        /** The server tick a unit tracked every {@code interval} ticks is drawn at this client tick. */
        public double playFor(int interval) {
            return newest;
        }
    }

    /** One unit's drawn position: the packets it was sent, the place it is drawn. */
    public static final class Track {
        private final int interval;
        private boolean seeded = false;
        private double x, y, z, lx, ly, lz;
        private float yaw, lyaw;
        private int steps = 0;

        /** @param interval the unit type's tracker update interval (EntityType.updateInterval) */
        public Track(int interval) {
            this.interval = Math.max(1, interval);
        }

        /** Where the unit stands when it starts being tracked (its spawn packet), at server tick {@code tick}. */
        public void seed(long tick, double x, double y, double z, float yaw) {
            this.x = lx = x;
            this.y = ly = y;
            this.z = lz = z;
            this.yaw = lyaw = yaw;
            steps = 0;
            seeded = true;
        }

        public boolean seeded() {
            return seeded;
        }

        /** A position packet stamped with server tick {@code tick} (yaw: the packet's, or the yaw it has). */
        public void moved(long tick, double px, double py, double pz, float pyaw) {
            if (!seeded) {
                seed(tick, px, py, pz, pyaw);
                return;
            }
            lx = px;
            ly = py;
            lz = pz;
            lyaw = pyaw;
            steps = 3;
        }

        /** A rotation-only packet stamped with server tick {@code tick}. */
        public void turned(long tick, float pyaw) {
            if (!seeded)
                return;
            lx = x;
            ly = y;
            lz = z;
            lyaw = pyaw;
            steps = 3;
        }

        /** Once a client tick: where to draw the unit, into {@code out} = x, y, z, yaw. */
        public void draw(Clock clock, double[] out) {
            if (steps > 0) {
                x += (lx - x) / steps;
                y += (ly - y) / steps;
                z += (lz - z) / steps;
                yaw += Mth.wrapDegrees(lyaw - yaw) / steps;
                steps--;
            }
            out[0] = x;
            out[1] = y;
            out[2] = z;
            out[3] = yaw;
        }
    }
}
