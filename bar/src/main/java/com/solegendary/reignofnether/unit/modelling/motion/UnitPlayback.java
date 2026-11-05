package com.solegendary.reignofnether.unit.modelling.motion;

import net.minecraft.util.Mth;

/**
 * Where the client draws a unit between the server's position packets (motion fluidity, lovish Nov 5: "not look like
 * their lagging or bugging around"). Pure: no world, no entity; the client glue feeds it the packets and the ticks.
 * <p>
 * Vanilla walks a unit to each position packet over exactly 3 client ticks. A unit is sent its position every 3rd
 * server tick, so on a server whose ticks run late or bunch up (lovish's 500+ unit games overrun 50 ms in a third to
 * a half of the 5 s windows; his 770-unit game ran at 14.5 TPS) the lerp has finished before the next packet arrives
 * and the unit stands still for a tick, then moves on: 4-8% of client ticks frozen, 27% at 14.5 TPS (UnitPlaybackTest).
 * The client cannot know a packet will be late, so no lerp length cures it.
 * <p>
 * Instead the server stamps every tick (a clock packet at the tick's start), so the client knows which server tick each
 * position belongs to, keeps each unit's last few stamped positions ({@link Track}), and draws it at a playback time
 * ({@link Clock}) that runs steadily the unit's own update interval plus {@link Clock#margin} ticks behind the newest
 * tick heard: a late or bunched packet lands in the buffer before it is drawn. The playback clock runs at the server's
 * measured tick rate, so a slow server is drawn in smooth slow motion, never stop-go; a server that stands still (a
 * hitch) is drawn standing still, and the missed ticks are caught up at a jog afterwards.
 */
public final class UnitPlayback {

    private UnitPlayback() { }

    /**
     * Ticks beyond a unit's update interval it is drawn behind the newest server tick heard. The price: a unit tracked
     * every 3rd tick is drawn 4 ticks behind the newest tick heard on a steady server, today's lerp 2 (about 100 ms
     * later on screen; the order itself is handled by the server as before).
     */
    public static final double MARGIN = 1.0;
    /**
     * How fast the playback clock learns the server's tick rate and is pulled to its place, per client tick (tuned on
     * UnitPlaybackTest's clocks: a faster pull follows every bunch of late ticks and jerks the walk). A bunch counts at
     * most {@link #MAX_HEARD} ticks toward the rate, so the ticks a server runs back to back after a hitch do not
     * convince the clock the server got faster.
     */
    static final double RATE_GAIN = 0.04, PULL = 0.02, MAX_HEARD = 3.0;
    /** Server ticks the playback may move on in one client tick: it catches up at a jog, never a leap. */
    static final double MAX_STEP = 1.75;
    /**
     * The buffer grows with the clock's typical error ({@code spread}, an average of how far the newest tick heard sits
     * from where the playback expects it): a jittery server is drawn a little further behind instead of stop-go, a
     * steady one keeps {@link #MARGIN}. At most {@link #MAX_EXTRA} ticks more, so a unit is drawn at most its interval
     * + 1 + 3 ticks behind the newest tick heard (7 ticks, 350 ms, for the usual interval 3). The spread rises fast
     * ({@link #SPREAD_RISE}: one long tick deepens the buffer for the next) and falls slowly ({@link #SPREAD_GAIN}) - a
     * jitter buffer's usual attack and release; gains tuned on UnitPlaybackTest's clocks 1-200 (r1 of #539).
     */
    static final double SPREAD_GAIN = 0.005, SPREAD_RISE = 0.1, SPREAD_WEIGHT = 1.0, MAX_EXTRA = 3.0;
    /**
     * Client ticks without a new server tick after which the server counts as standing still (a hitch, not a late tick):
     * {@link #STALL}, or {@link #STALL_TICKS} of the server's own measured tick length on a slow server (at 5 TPS a
     * tick takes 4 client ticks, and that is the game running, not a hitch).
     */
    static final int STALL = 3;
    static final double STALL_TICKS = 2.5;
    /** Ticks off its place past which the playback clock jumps there (a join, a 3 s freeze, a reconnect). */
    static final double JUMP = 60.0;

    /** The server's tick clock as this client hears it: one stamp a server tick, read once a client tick. */
    public static final class Clock {
        private long newest = Long.MIN_VALUE, prev = Long.MIN_VALUE;
        private double play = Double.NaN, rate = 1.0;
        private int quiet = 0;
        private double spread = 0.0;

        /** The server started tick {@code tick} (its clock packet arrived). */
        public void heard(long tick) {
            if (newest == Long.MIN_VALUE || tick > newest)
                newest = tick;
        }

        /** Once a client tick, before the units are drawn for it: the playback time moves on. */
        public void clientTick() {
            if (!ready())
                return;
            if (Double.isNaN(play)) {
                play = prev = newest;
                return;
            }
            quiet = newest > prev ? 0 : quiet + 1;
            if (quiet >= Math.max(STALL, STALL_TICKS / rate)) {
                // the server has stood still for longer than a late tick takes (a hitch): the clock learns no slower
                // rate from it, and draws on at its rate only through the ticks already buffered (to the newest tick
                // heard, plus the margin) - then the units stand with the server, never running ahead of what has
                // arrived (to leap when the missed ticks come)
                prev = newest;
                play = Math.max(play, Math.min(play + rate, newest + margin()));
                return;
            }
            rate = Mth.clamp(rate + RATE_GAIN * (Math.min(newest - prev, MAX_HEARD) - rate), 0.2, MAX_STEP);
            prev = newest;
            double err = newest - (play + rate);
            spread += (Math.abs(err) > spread ? SPREAD_RISE : SPREAD_GAIN) * (Math.abs(err) - spread);
            play = Math.abs(err) > JUMP ? newest : play + Mth.clamp(rate + PULL * err, 0.0, MAX_STEP);
        }

        public boolean ready() {
            return newest != Long.MIN_VALUE;
        }

        /** Forget the server (a new world, a reconnect): the next stamp starts the clock again. */
        public void reset() {
            newest = prev = Long.MIN_VALUE;
            play = Double.NaN;
            rate = 1.0;
            quiet = 0;
            spread = 0.0;
        }

        /** The newest server tick heard. */
        public long newest() {
            return newest;
        }

        /** The server tick a unit tracked every {@code interval} ticks is drawn at this client tick. */
        public double playFor(int interval) {
            double now = Double.isNaN(play) ? newest : play;
            return now - Math.max(1, interval) - margin();
        }

        /** The buffer beyond a unit's interval: {@link #MARGIN}, deeper on a server whose ticks arrive unevenly. */
        public double margin() {
            return MARGIN + Math.min(MAX_EXTRA, SPREAD_WEIGHT * spread);
        }
    }

    /**
     * A move between two samples is a blink (drawn as a jump, never a slide across) past {@link #BLINK_BLOCKS} - the
     * most one tracker move packet can carry (a short of 4096ths: ServerEntity sends a teleport packet for more) - or,
     * for a unit whose packets are further apart, past the fastest it can go in the ticks between them: {@link #MAX_RUN}
     * blocks a tick across the ground (a Leaf Dash is 1.33), {@link #MAX_FALL} down or up (vanilla's falling speed
     * tops out at 3.92). Kinematics.TELEPORT_BLOCKS (4) is a per-client-tick limit and was wrongly used here per packet
     * (r1 of #539: a Leaf Dash, 4 blocks a packet, was drawn held then popped).
     */
    static final double BLINK_BLOCKS = 8.0, MAX_RUN = 2.0, MAX_FALL = 4.0;

    /** One unit's stamped positions and rotations (a short ring) and where it is drawn. */
    public static final class Track {
        static final int SIZE = 16;
        /** What a move packet carries (ClientboundMoveEntityPacket.hasPosition / hasRotation; a teleport: both). */
        public static final int POS = 1, ROT = 2;
        private final int interval;
        private final double[] tick = new double[SIZE], x = new double[SIZE], y = new double[SIZE], z = new double[SIZE];
        private final float[] yaw = new float[SIZE], headYaw = new float[SIZE], pitch = new float[SIZE];
        private int head = -1, count = 0;

        /** @param interval the unit type's tracker update interval (EntityType.updateInterval) */
        public Track(int interval) {
            this.interval = Math.max(1, interval);
        }

        /** Where the unit stands when it starts being tracked (its spawn packet), at server tick {@code tick}. */
        public void seed(long t, double px, double py, double pz, float pyaw) {
            seed(t, px, py, pz, pyaw, pyaw, 0f);
        }

        public void seed(long t, double px, double py, double pz, float pyaw, float phead, float ppitch) {
            head = -1;
            count = 0;
            add(t, px, py, pz, pyaw, phead, ppitch);
        }

        public boolean seeded() {
            return count > 0;
        }

        /**
         * One move or teleport packet as the client glue hands it over, stamped with server tick {@code t}; {@code kind}
         * says what it carries ({@link #POS} | {@link #ROT}), read from the packet itself (r1 of #539: guessing it from
         * values equal to the drawn ones - which run 4-7 ticks behind - filed a real turn as none after a hitch). What a
         * packet lacks (ClientPacketListener fills it in from the entity) is ignored: a position-only packet keeps the
         * stamped yaw and pitch, a rotation-only one the stamped position.
         */
        public void packet(long t, int kind, double px, double py, double pz, float pyaw, float ppitch) {
            boolean pos = (kind & POS) != 0, rot = (kind & ROT) != 0;
            if (pos)
                moved(t, px, py, pz, rot ? pyaw : Float.NaN, rot ? ppitch : Float.NaN);
            else if (rot)
                turned(t, pyaw, ppitch);
        }

        /** A head-rotation packet (ClientboundRotateHeadPacket) stamped with server tick {@code t}: drawn with the body. */
        public void head(long t, float phead) {
            if (!seeded())
                return;
            int l = head;
            hold(t, l);
            add(t, x[l], y[l], z[l], yaw[l], phead, pitch[l]);
        }

        /** A position packet stamped with server tick {@code t} (yaw: the packet's, or NaN for a position-only one). */
        public void moved(long t, double px, double py, double pz, float pyaw) {
            moved(t, px, py, pz, pyaw, Float.NaN);
        }

        /** As {@link #moved(long, double, double, double, float)}, with the packet's pitch (NaN: none). */
        public void moved(long t, double px, double py, double pz, float pyaw, float ppitch) {
            if (!seeded()) {
                if (Float.isFinite(pyaw))
                    seed(t, px, py, pz, pyaw, pyaw, Float.isFinite(ppitch) ? ppitch : 0f);
                return;
            }
            int l = head;
            if (!Float.isFinite(pyaw))
                pyaw = yaw[l];   // a position-only packet: the unit still faces where its last stamp said
            if (!Float.isFinite(ppitch))
                ppitch = pitch[l];
            double ticks = Mth.clamp(t - tick[l], 1.0, interval);
            if (Math.hypot(px - x[l], pz - z[l]) > Math.max(BLINK_BLOCKS, MAX_RUN * ticks)
                    || Math.abs(py - y[l]) > Math.max(BLINK_BLOCKS, MAX_FALL * ticks)) {
                // a blink: held where it was until the tick it jumped, never drawn sliding across
                add(Math.max(tick[l], t - 1e-3), x[l], y[l], z[l], yaw[l], headYaw[l], pitch[l]);
            } else {
                hold(t, l);
            }
            add(t, px, py, pz, pyaw, headYaw[l], ppitch);
        }

        /** A rotation-only packet stamped with server tick {@code t}: the unit turned where it stands. */
        public void turned(long t, float pyaw) {
            turned(t, pyaw, Float.NaN);
        }

        public void turned(long t, float pyaw, float ppitch) {
            if (!seeded())
                return;
            int l = head;
            hold(t, l);
            add(t, x[l], y[l], z[l], pyaw, headYaw[l], Float.isFinite(ppitch) ? ppitch : pitch[l]);
        }

        /**
         * It stood still since sample {@code l} (none is sent while nothing changes): it set off no earlier than one
         * interval before {@code t} - without this it would be drawn creeping off from its last packet's tick.
         */
        private void hold(long t, int l) {
            if (t - tick[l] > interval)
                add(t - interval, x[l], y[l], z[l], yaw[l], headYaw[l], pitch[l]);
        }

        private void add(double t, double px, double py, double pz, float pyaw, float phead, float ppitch) {
            if (count == 0 || t > tick[head]) {
                head = (head + 1) % SIZE;
                count = Math.min(count + 1, SIZE);
                tick[head] = t;
            }
            // (the same tick again, or older: the newer packet wins)
            x[head] = px; y[head] = py; z[head] = pz; yaw[head] = pyaw; headYaw[head] = phead; pitch[head] = ppitch;
        }

        /** Once a client tick: where to draw the unit, into {@code out} = x, y, z, yaw, head yaw, pitch. */
        public void draw(Clock clock, double[] out) {
            if (!seeded())
                return;
            double play = clock.ready() ? clock.playFor(interval) : Double.POSITIVE_INFINITY;
            int newest = head, oldest = (head - count + 1 + SIZE) % SIZE;
            int a = oldest, b = oldest;
            if (play >= tick[newest]) {
                a = b = newest;
            } else if (play > tick[oldest]) {
                for (int i = 1; i < count; i++) {
                    b = (oldest + i) % SIZE;
                    if (tick[b] >= play)
                        break;
                    a = b;
                }
            }
            double f = b != a && tick[b] > tick[a] ? Mth.clamp((play - tick[a]) / (tick[b] - tick[a]), 0.0, 1.0) : 1.0;
            out[0] = x[a] + (x[b] - x[a]) * f;
            out[1] = y[a] + (y[b] - y[a]) * f;
            out[2] = z[a] + (z[b] - z[a]) * f;
            out[3] = yaw[a] + Mth.wrapDegrees(yaw[b] - yaw[a]) * (float) f;
            out[4] = headYaw[a] + Mth.wrapDegrees(headYaw[b] - headYaw[a]) * (float) f;
            out[5] = pitch[a] + (pitch[b] - pitch[a]) * f;
        }
    }
}
