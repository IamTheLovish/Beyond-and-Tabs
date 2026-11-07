package com.solegendary.reignofnether.unit.modelling.motion;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Motion fluidity (lovish, Nov 5: "not look like their lagging or bugging around"): where the client draws a walking
 * unit between position packets (UnitPlayback). A replayed server walks a unit and sends what the game's tracker sends
 * (a position packet every 3rd tick while it moves, every 60th while it stands, rotation-only packets where a test puts
 * them, a stamp at every tick's start) on a match server's clock: vanilla's scheduler (a tick every 50 ms, or at once
 * when the last overran) with tick times drawn from a log-normal fitted to lovish's own server logs. The client handles
 * what has arrived at the start of each 50 ms client tick, then draws.
 * <p>
 * Bounds, fixed before the build (MotionFluidityGameTests and the offline model in the lane notes measured today's rule
 * on these clocks: 4-8% of client ticks frozen at 500-799 units, 27.5% at 14.5 TPS, speed cv 0.24-0.62, torso pitch
 * 2.1-6.7 degrees):
 * - {@link #MAX_FROZEN_PCT}: a walking unit stands still on at most 0.5% of client ticks (the aim is none; the margin
 *   covers the playback clock settling on a new rate);
 * - {@link #MAX_CV}: its drawn speed varies by at most 0.10 (cv; the model of the buffered rule reads 0.04-0.07);
 * - {@link #MAX_PITCH}: the living army's torso pitch from those speeds (Kinematics.step's ACCEL lean) averages at most
 *   1.5 degrees (a steady walk is 0; the model reads ~1.1 at 14.5 TPS; today 3.4 at 700 units, 6.7 at 14.5 TPS);
 * - {@link #MAX_LAG}: the price - the drawn unit trails the newest server tick the client has heard of by at most its
 *   update interval + 1.5 ticks on a steady server (today ~2 ticks; the buffer draws a tick and a half later);
 * - {@link #MAX_CATCH_UP}: after a one-second hitch the unit catches up at no more than twice its walk speed.
 */
class UnitPlaybackTest {

    static final double MAX_FROZEN_PCT = 0.5, MAX_CV = 0.10, MAX_PITCH = 1.5, MAX_LAG_OVER_INTERVAL = 1.5;
    /** Walk steps a client tick may move the unit while it catches up after a server hitch (a jog, not a leap). */
    static final double MAX_CATCH_UP = 2.0;
    static final int INTERVAL = 3;
    static final double SPEED = 0.25;   // blocks a tick: an infantry walk

    // ------------------------------------------------------------------------------------------------ the clocks

    /** When each server tick starts (ms): vanilla's scheduler with log-normal tick times (p50, p95), or a fixed time. */
    static double[] starts(double p50, double p95, double fixedMs, int ticks, long seed) {
        double[] at = new double[ticks];
        Random rnd = new Random(seed);
        double mu = p50 > 0 ? Math.log(p50) : 0, sigma = p50 > 0 ? Math.log(p95 / p50) / 1.645 : 0;
        double end = 0, scheduled = 0;
        for (int k = 0; k < ticks; k++) {
            double start = Math.max(scheduled, end);
            double d = fixedMs > 0 ? fixedMs : p50 > 0 ? Math.exp(mu + sigma * rnd.nextGaussian()) : 1.0;
            at[k] = start;
            end = start + d;
            scheduled += 50.0;
            if (end - scheduled > 2000.0)
                scheduled = end;
        }
        return at;
    }

    static double[] steady(int n) { return starts(0, 0, 0, n, 1); }
    /** lovish's 500-599 unit windows with players (p50 34.0, p95 70.9 ms); one clock per seed. */
    static double[] at500(int n, long seed) { return starts(34.0, 70.9, 0, n, seed); }
    /** lovish's 700-799 unit windows with players (p50 38.9, p95 85.2 ms); one clock per seed. */
    static double[] at700(int n, long seed) { return starts(38.9, 85.2, 0, n, seed); }
    /**
     * Clocks drawn per jittery profile: each must pass (one lucky draw proves nothing). The playback's gains were tuned
     * on clocks 1-200 of both profiles (r1 found clocks 1-200 at 700 units missed on 7 of 200 with the first gains);
     * {@link #HELD_OUT_FROM} on are clocks no tuning ever looked at.
     */
    static final long[] SEEDS = java.util.stream.LongStream.rangeClosed(1, 20).toArray();
    /** Clocks never used to tune the playback, judged as a whole (see the held-out test). */
    static final long HELD_OUT_FROM = 2000, HELD_OUT_TO = 2199;
    /** His Oct 29 770-unit game: 14.5 TPS. */
    static double[] at14(int n) { return starts(0, 0, 1000.0 / 14.5, n, 1); }
    /** His multi-thousand-unit stress games: 200 ms ticks, 5 TPS. */
    static double[] at5(int n) { return starts(0, 0, 200.0, n, 1); }

    // ------------------------------------------------------------------------------------------------ the replay

    /** The drawn unit per client tick (x, z, then y, body yaw, head yaw, pitch), and the newest server tick heard at it. */
    record Drawn(double[] x, double[] z, long[] newest, double[] y, double[] yaw, double[] headYaw, double[] pitch) { }

    /** {x, z} or {x, z, y, yaw, pitch}: the walks face east (-90) on level ground unless the truth says otherwise. */
    static double[] full(double[] p) {
        return p.length >= 5 ? p : new double[] { p[0], p[1], 0.0, -90.0, 0.0 };
    }

    /** A position as the tracker sends it (VecDeltaCodec: whole 4096ths of a block). */
    static double q(double v) {
        return Math.round(v * 4096.0) / 4096.0;
    }

    /** A yaw or pitch as the tracker sends it (a byte: 256ths of a turn). */
    static float qa(double deg) {
        return (float) (Math.round(deg * 256.0 / 360.0) * 360.0 / 256.0);
    }

    /**
     * Replays a walk: {@code truth.apply(k)} is the unit after server tick k ({@link #full}); {@code rotOnly} the ticks an
     * extra rotation-only packet (yaw -80) goes out. Tick 0 is the spawn (the track is seeded there). The tracker's
     * packets, as ServerEntity sends them every {@link #INTERVAL}th tick: the position when it moved (or every 60th
     * tick), the yaw and pitch when they changed by a 256th of a turn, the head yaw (here the body's) when it did. The
     * client handles each as ClientPacketListener does - a move packet fills in what it lacks from the entity (the drawn
     * position, yaw and pitch) and goes to {@link UnitPlayback.Track#packet} with what it carries, a head packet to
     * {@link UnitPlayback.Track#head} - then the playback clock moves on and the unit is drawn.
     */
    static Drawn replay(double[] at, IntFunction<double[]> truth, Set<Integer> rotOnly) {
        final int POS = UnitPlayback.Track.POS, ROT = UnitPlayback.Track.ROT, HEAD = 8;
        int ticks = at.length;
        final double phase = 17.0, transitMs = 1.0;
        // arrival ms, server tick, kind (0 a stamp, POS | ROT a move packet, HEAD a head packet), x, z, y, yaw, pitch
        List<Object[]> events = new ArrayList<>();
        double[] p0 = full(truth.apply(0));
        double[] sent = p0.clone();
        float sentHead = qa(p0[3]);
        for (int k = 1; k < ticks; k++) {
            double send = at[k] + transitMs;
            events.add(new Object[] { send, k, 0, 0.0, 0.0, 0.0, 0f, 0f });
            double[] p = full(truth.apply(k));
            if (k % INTERVAL == 0) {
                boolean moved = Math.abs(q(p[0]) - q(sent[0])) > 0 || Math.abs(q(p[2]) - q(sent[2])) > 0
                    || Math.abs(q(p[1]) - q(sent[1])) > 0 || k % 60 == 0;
                boolean turned = qa(p[3]) != qa(sent[3]) || qa(p[4]) != qa(sent[4]);
                int kind = (moved ? POS : 0) | (turned ? ROT : 0);
                if (kind != 0) {
                    events.add(new Object[] { send + 0.01, k, kind, q(p[0]), q(p[1]), q(p[2]), qa(p[3]), qa(p[4]) });
                    if (moved) { sent[0] = p[0]; sent[1] = p[1]; sent[2] = p[2]; }
                    if (turned) { sent[3] = p[3]; sent[4] = p[4]; }
                } else if (rotOnly.contains(k)) {
                    events.add(new Object[] { send + 0.01, k, ROT, 0.0, 0.0, 0.0, -80f, 0f });
                }
                if (qa(p[3]) != sentHead) {
                    sentHead = qa(p[3]);
                    events.add(new Object[] { send + 0.02, k, HEAD, 0.0, 0.0, 0.0, sentHead, 0f });
                }
            } else if (rotOnly.contains(k)) {
                events.add(new Object[] { send + 0.01, k, ROT, 0.0, 0.0, 0.0, -80f, 0f });
            }
        }
        UnitPlayback.Clock clock = new UnitPlayback.Clock();
        UnitPlayback.Track track = new UnitPlayback.Track(INTERVAL);
        clock.heard(0);
        track.seed(0, p0[0], p0[2], p0[1], (float) p0[3]);
        int lastClient = (int) Math.ceil((at[ticks - 1] - phase) / 50.0) + 10;
        int n = lastClient + 1;
        double[] xs = new double[n], zs = new double[n], ys = new double[n], yaws = new double[n], heads = new double[n],
            pitches = new double[n];
        long[] newest = new long[n];
        // the entity as the client holds it between ticks: where it was drawn last
        double[] out = { p0[0], p0[2], p0[1], p0[3], p0[3], p0[4] };
        int e = 0;
        for (int c = 0; c <= lastClient; c++) {
            while (e < events.size() && (double) events.get(e)[0] <= c * 50.0 + phase) {
                Object[] ev = events.get(e++);
                int k = (int) ev[1], kind = (int) ev[2];
                if (kind == 0) {
                    clock.heard(k);
                } else if (kind == HEAD) {
                    track.head(k, (float) ev[6]);
                } else {
                    boolean pos = (kind & POS) != 0, rot = (kind & ROT) != 0;
                    track.packet(k, kind, pos ? (double) ev[3] : out[0], pos ? (double) ev[5] : out[1],
                        pos ? (double) ev[4] : out[2], rot ? (float) ev[6] : (float) out[3], rot ? (float) ev[7] : (float) out[5]);
                }
            }
            clock.clientTick();
            track.draw(clock, out);
            xs[c] = out[0]; ys[c] = out[1]; zs[c] = out[2]; yaws[c] = out[3]; heads[c] = out[4]; pitches[c] = out[5];
            newest[c] = clock.newest();
        }
        return new Drawn(xs, zs, newest, ys, yaws, heads, pitches);
    }

    /** Smoothness over the client ticks that show server ticks [from, to): cv, % frozen, mean torso pitch, mean lag. */
    static double[] judge(Drawn d, int from, int to, IntFunction<double[]> truth) {
        List<Double> speeds = new ArrayList<>();
        float[] slab = new float[AnimState.STRIDE];
        double pitch = 0, lag = 0;
        int pitchN = 0;
        boolean fresh = true;
        for (int c = 1; c < d.x().length; c++) {
            if (d.newest()[c - 1] < from || d.newest()[c] >= to)
                continue;
            double dx = d.x()[c] - d.x()[c - 1], dz = d.z()[c] - d.z()[c - 1];
            speeds.add(Math.hypot(dx, dz));
            Kinematics.step(slab, 0, (float) dx, 0f, (float) dz, 0f, 20f, 20f, false, fresh);
            if (!fresh) {
                pitch += Math.abs(Mth.clamp(slab[AnimState.ACCEL] * Kinematics.PITCH_PER_ACCEL,
                    -Kinematics.PITCH_MAX_DEG, Kinematics.PITCH_MAX_DEG));
                pitchN++;
            }
            fresh = false;
            lag += (truth.apply((int) d.newest()[c])[0] - d.x()[c]) / SPEED;   // the walks below go east
        }
        double mean = speeds.stream().mapToDouble(Double::doubleValue).average().orElse(0), var = 0;
        int frozen = 0;
        for (double v : speeds) {
            var += (v - mean) * (v - mean);
            if (v < 0.1 * SPEED)
                frozen++;
        }
        int n = speeds.size();
        assertTrue(n >= 50, "the judged walk is too short: " + n + " client ticks");
        return new double[] { Math.sqrt(var / n) / mean, 100.0 * frozen / n, pitch / Math.max(1, pitchN), lag / n };
    }

    /** East at {@link #SPEED} from tick 1 on. */
    static double[] walkEast(int k) {
        return new double[] { SPEED * k, 0 };
    }

    private static String fmt(String name, double[] j) {
        return String.format(Locale.ROOT, "%s: speed cv %.3f (bound %.2f), frozen %.1f%% of client ticks (bound %.1f),"
            + " torso pitch %.2f deg (bound %.1f), lag %.2f ticks", name, j[0], MAX_CV, j[1], MAX_FROZEN_PCT, j[2],
            MAX_PITCH, j[3]);
    }

    private static void smoothWalk(String name, double[] at) {
        double[] j = judge(replay(at, UnitPlaybackTest::walkEast, Set.of()), 40, at.length - 40, UnitPlaybackTest::walkEast);
        String line = fmt(name, j);
        System.out.println("[Motion] " + line);
        assertTrue(j[1] <= MAX_FROZEN_PCT, "a walking unit stands still between packets: " + line);
        assertTrue(j[0] <= MAX_CV, "a walking unit's drawn speed jerks: " + line);
        assertTrue(j[2] <= MAX_PITCH, "the walk's speed jerks twitch the torso: " + line);
    }

    // ------------------------------------------------------------------------------------------------ the tests

    /** The control: a steady server, every tick on time - today's rule already draws this evenly. */
    @Test
    void a_steady_server_walk_is_drawn_at_an_even_speed() {
        smoothWalk("steady 20 TPS", steady(400));
    }

    @Test
    void a_server_overrunning_like_lovishs_500_unit_games_draws_the_walk_without_stops() {
        for (long seed : SEEDS)
            smoothWalk("500-599 units (p50 34, p95 71 ms), clock " + seed, at500(600, seed));
    }

    @Test
    void a_server_overrunning_like_lovishs_700_unit_games_draws_the_walk_without_stops() {
        for (long seed : SEEDS)
            smoothWalk("700-799 units (p50 39, p95 85 ms), clock " + seed, at700(600, seed));
    }

    @Test
    void a_server_stuck_at_14_5_tps_draws_the_walk_without_stops() {
        smoothWalk("14.5 TPS", at14(500));
    }

    /** A server crawling at 5 TPS is drawn in slow motion, never stop-go (each tick takes 4 client ticks there). */
    @Test
    void a_server_crawling_at_5_tps_draws_the_walk_without_stops() {
        double[] at = at5(260);
        double[] j = judge(replay(at, UnitPlaybackTest::walkEast, Set.of()), 30, 230, UnitPlaybackTest::walkEast);
        // the drawn speed is the server's walk slowed to its tick rate: judged against that, a quarter of the walk
        double slow = SPEED / 4;
        String line = fmt("5 TPS", j) + String.format(Locale.ROOT, " (walk drawn at a quarter: %.3f b/t)", slow);
        System.out.println("[Motion] " + line);
        assertTrue(j[0] <= MAX_CV, "a crawling server's walk is drawn stop-go: " + line);
    }

    /** The price, bounded: on a steady server the drawn unit trails the newest known tick by at most interval + 1.5. */
    @Test
    void the_drawn_unit_trails_the_newest_server_tick_by_at_most_its_interval_and_a_half() {
        double[] j = judge(replay(steady(400), UnitPlaybackTest::walkEast, Set.of()), 40, 360, UnitPlaybackTest::walkEast);
        System.out.println("[Motion] lag on a steady server: " + fmt("steady", j));
        assertTrue(j[3] <= INTERVAL + MAX_LAG_OVER_INTERVAL, "the unit is drawn too far behind the server: " + fmt("steady", j));
        assertTrue(j[3] >= 0, "the unit is drawn AHEAD of the newest tick heard (extrapolated): " + fmt("steady", j));
    }

    /**
     * A unit that stops and turns in the same moment is drawn where it stopped. The rotation-only packet that follows its
     * last position packet aims today's position lerp at where the client draws the unit at that moment, short of the
     * stop, and it stays short until the 60-tick refresh.
     */
    @Test
    void a_unit_that_stops_and_turns_is_drawn_where_it_stopped() {
        IntFunction<double[]> stopAt100 = k -> new double[] { SPEED * Math.min(k, 100), 0 };
        Drawn d = replay(steady(200), stopAt100, Set.of(103));
        double stop = SPEED * 100;
        // from 12 client ticks after the client heard the stop's packet (tick 102) until the 60-tick refresh (120)
        double worst = 0;
        int judged = 0;
        for (int c = 0; c < d.x().length; c++)
            if (d.newest()[c] >= 102 + 12 && d.newest()[c] < 120) {
                worst = Math.max(worst, Math.abs(d.x()[c] - stop));
                judged++;
            }
        assertTrue(judged > 0, "no client tick judged");
        assertTrue(worst <= 0.01, String.format(Locale.ROOT, "the stopped unit is drawn %.3f blocks from where it stopped", worst));
    }

    /** A unit standing still for a long time starts walking when the server's unit does, not early and not slowly. */
    @Test
    void a_unit_starting_from_rest_does_not_creep_ahead_of_the_server() {
        IntFunction<double[]> startAt200 = k -> new double[] { SPEED * Math.max(0, k - 200), 0 };
        Drawn d = replay(steady(320), startAt200, Set.of());
        for (int c = 0; c < d.x().length; c++)
            if (d.newest()[c] < 200)
                assertTrue(Math.abs(d.x()[c]) <= 1e-9, "the unit is drawn moving before the server's unit moved (client tick "
                    + c + ", x " + d.x()[c] + ")");
        double[] j = judge(d, 212, 300, startAt200);
        assertTrue(j[1] <= MAX_FROZEN_PCT && j[0] <= MAX_CV, "after the start: " + fmt("start from rest", j));
    }

    /**
     * One tick of a whole second (lovish's logs show 300-1000 ms window maxima at big unit counts): the server then runs
     * the ticks it missed back to back, so ~20 ticks of packets land at once. The unit is drawn pausing with the server,
     * then walking on - never leaping to catch up: no client tick moves it more than {@link #MAX_CATCH_UP} walk steps
     * (today's lerp covers the whole missed second in 3 client ticks, ~6-7 steps a tick).
     */
    @Test
    void a_one_second_server_hitch_is_drawn_as_a_pause_not_a_leap() {
        double[] at = starts(0, 0, 0, 400, 1);
        for (int k = 201; k < at.length; k++)   // tick 200 takes a second; vanilla's scheduler runs the rest at once
            at[k] = Math.max(at[k], at[200] + 1000.0 + (k - 201) * 0.2);
        Drawn d = replay(at, UnitPlaybackTest::walkEast, Set.of());
        double worst = 0;
        int judged = 0;
        for (int c = 1; c < d.x().length; c++)
            if (d.newest()[c - 1] >= 150 && d.newest()[c] < 350) {
                worst = Math.max(worst, Math.abs(d.x()[c] - d.x()[c - 1]) / SPEED);
                judged++;
            }
        assertTrue(judged > 50, "too few client ticks judged: " + judged);
        System.out.println(String.format(Locale.ROOT, "[Motion] one-second hitch: worst client tick %.2f walk steps (bound %.1f)", worst, MAX_CATCH_UP));
        assertTrue(worst <= MAX_CATCH_UP, String.format(Locale.ROOT,
            "after a one-second hitch the unit leaps %.1f walk steps in one client tick (bound %.1f)", worst, MAX_CATCH_UP));
    }

    /**
     * A position-only packet (the unit moved, its facing did not change by a 256th of a turn) carries no yaw; the
     * client glue passes NaN, and the unit keeps facing where its last stamp said - never NaN, never snapped.
     */
    @Test
    void a_position_only_packet_keeps_the_stamped_facing() {
        UnitPlayback.Clock clock = new UnitPlayback.Clock();
        UnitPlayback.Track track = new UnitPlayback.Track(INTERVAL);
        clock.heard(0);
        track.seed(0, 0, 0, 0, 30f);
        double[] out = new double[6];
        for (int k = 1; k <= 30; k++) {
            clock.heard(k);
            if (k % INTERVAL == 0)
                track.moved(k, SPEED * k, 0, 0, Float.NaN);
            clock.clientTick();
            track.draw(clock, out);
            assertTrue(Double.isFinite(out[0]) && Math.abs(out[3] - 30.0) < 1e-3,
                "tick " + k + ": drawn at x " + out[0] + ", facing " + out[3] + " (stamped 30)");
        }
    }

    /**
     * A blink (here 8 blocks in one tick on top of the walk: 8.75 blocks between two packets, past the 8 blocks a
     * tracker move packet can carry - ServerEntity sends a teleport packet for more) is drawn as a jump, never as a
     * slide across.
     */
    @Test
    void a_teleport_is_drawn_as_a_jump_not_a_slide() {
        IntFunction<double[]> blink = k -> new double[] { SPEED * k + (k >= 150 ? 8.0 : 0.0), 0 };
        Drawn d = replay(steady(260), blink, Set.of());
        double from = SPEED * 149, to = SPEED * 150 + 8.0;
        int between = 0;
        for (double x : d.x())
            if (x > from + 1.0 && x < to - 1.0)
                between++;
        assertTrue(between == 0, between + " client ticks drew the unit sliding across an 8-block blink");
    }

    /** One client tick's horizontal move, the largest over the client ticks that show server ticks [from, to). */
    static double worstStep(Drawn d, int from, int to) {
        double worst = 0;
        for (int c = 1; c < d.x().length; c++)
            if (d.newest()[c - 1] >= from && d.newest()[c] < to)
                worst = Math.max(worst, Math.hypot(d.x()[c] - d.x()[c - 1], d.z()[c] - d.z()[c - 1]));
        return worst;
    }

    /**
     * r1 B1: a Leafblade's Leaf Dash (LeafDash.LENGTH 8 blocks in DURATION_TICKS 6) is 4 blocks a 3-tick packet. The
     * blink rule took any packet over Kinematics.TELEPORT_BLOCKS (4 - a limit meant per client tick) as a teleport, so on
     * headings where the tracker's 4096ths round the 4 blocks up the dash was drawn held for 3 ticks, then popped 4
     * blocks. Drawn as a dash: no client tick moves more than 1.5 times the dash's even step (1.33 blocks a tick).
     */
    @Test
    void a_leaf_dash_is_drawn_as_a_dash_not_a_hold_and_a_pop() {
        double even = 8.0 / 6;
        StringBuilder bad = new StringBuilder();
        for (double deg : new double[] { 0, 10, 30, 45, 60, 77 }) {
            double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
            // walk, dash ticks 103-108 (packets 105 and 108 carry 4 blocks each), walk on
            IntFunction<double[]> dash = k -> {
                double along = SPEED * Math.min(k, 102) + even * Math.max(0, Math.min(k, 108) - 102)
                    + SPEED * Math.max(0, k - 108);
                return new double[] { along * c, along * s };
            };
            double worst = worstStep(replay(steady(200), dash, Set.of()), 95, 130);
            if (worst > 1.5 * even)
                bad.append(String.format(Locale.ROOT, " heading %.0f: %.2f blocks;", deg, worst));
        }
        assertTrue(bad.length() == 0, "a dash is drawn held, then popped (largest client tick, bound "
            + String.format(Locale.ROOT, "%.2f", 1.5 * even) + "):" + bad);
    }

    /**
     * r1 B1: a unit falling far (vanilla's fall: speed + 0.08 a tick, x 0.98, toward 3.92 blocks a tick) moves more than
     * 4 blocks a packet once it falls faster than 1.33 blocks a tick. Drawn falling, never held and dropped a packet at a
     * time: no client tick drops it more than 1.5 times the fastest tick of the fall.
     */
    @Test
    void a_long_fall_is_drawn_as_a_fall_not_held_and_dropped() {
        int start = 100, end = 190;
        double[] fall = new double[260];
        double v = 0;
        for (int k = start + 1; k < fall.length; k++) {
            v = k <= end ? (v + 0.08) * 0.98 : 0;
            fall[k] = fall[k - 1] - v;
        }
        double fastest = 0;
        for (int k = 1; k < fall.length; k++)
            fastest = Math.max(fastest, fall[k - 1] - fall[k]);
        IntFunction<double[]> truth = k -> new double[] { SPEED * k, 0, fall[Math.min(k, fall.length - 1)], -90, 0 };
        Drawn d = replay(steady(260), truth, Set.of());
        double worst = 0;
        for (int c = 1; c < d.y().length; c++)
            if (d.newest()[c - 1] >= start && d.newest()[c] < end + 20)
                worst = Math.max(worst, d.y()[c - 1] - d.y()[c]);
        assertTrue(worst <= 1.5 * fastest, String.format(Locale.ROOT, "a fall of %.0f blocks is drawn dropping %.2f blocks"
            + " in one client tick (its fastest tick %.2f, bound %.2f)", -fall[end], worst, fastest, 1.5 * fastest));
    }

    /**
     * r1 B2: after a one-second hitch the catch-up packets turn a unit to 90 and straight back to 0. The client glue
     * guessed "no turn" from a packet yaw equal to the drawn yaw - which runs 4-7 ticks behind and still sat at 0 - so
     * the turn back was filed as no turn and the unit stayed drawn facing 90 while the server's unit faced 0. A packet
     * is filed by what it carries: the unit ends drawn facing the server.
     */
    @Test
    void a_turn_and_back_in_a_hitchs_catch_up_ends_facing_the_server() {
        double[] at = starts(0, 0, 0, 400, 1);
        for (int k = 201; k < at.length; k++)
            at[k] = Math.max(at[k], at[200] + 1000.0 + (k - 201) * 0.2);
        IntFunction<double[]> truth = k -> new double[] { SPEED * k, 0, 0, k >= 204 && k < 207 ? 90 : 0, 0 };
        Drawn d = replay(at, truth, Set.of());
        double worst = 0;
        for (int c = 0; c < d.yaw().length; c++)
            if (d.newest()[c] >= 260)
                worst = Math.max(worst, Math.abs(Mth.wrapDegrees(d.yaw()[c])));
        assertTrue(worst <= 1.0, String.format(Locale.ROOT, "the unit is drawn facing %.0f degrees off the server's after"
            + " the hitch", worst));
    }

    /**
     * r1: the head yaw and the pitch followed the newest packet while the body was drawn 4-7 ticks behind, so a unit
     * looked where it was going 100-150 ms before it turned. Head, body and pitch go through the same buffer: through a
     * 90-degree turn with a 30-degree pitch, the three move together.
     */
    @Test
    void the_head_and_pitch_are_drawn_on_the_bodys_delay() {
        IntFunction<double[]> truth = k -> k < 100 ? new double[] { SPEED * k, 0, 0, -90, 0 }
            : new double[] { SPEED * 100, SPEED * (k - 100), 0, 0, 30 };
        Drawn d = replay(steady(200), truth, Set.of());
        double headOff = 0, pitchOff = 0;
        for (int c = 0; c < d.yaw().length; c++) {
            headOff = Math.max(headOff, Math.abs(Mth.wrapDegrees(d.headYaw()[c] - d.yaw()[c])));
            double turned = Mth.wrapDegrees(d.yaw()[c] + 90.0) / 90.0;   // 0 before the turn, 1 after
            pitchOff = Math.max(pitchOff, Math.abs(d.pitch()[c] / 30.0 - turned));
        }
        assertTrue(headOff <= 1.0, String.format(Locale.ROOT, "the head is drawn %.0f degrees off the body", headOff));
        assertTrue(pitchOff <= 0.02, String.format(Locale.ROOT, "the pitch is drawn %.0f%% of the turn off the body",
            100 * pitchOff));
    }

    /** A turn across south (179 to -179 degrees) is drawn the short way: 2 degrees, never a spin through north. */
    @Test
    void a_turn_across_south_is_drawn_the_short_way() {
        IntFunction<double[]> truth = k -> new double[] { SPEED * k, 0, 0, k < 100 ? 179 : -179, 0 };
        Drawn d = replay(steady(200), truth, Set.of());
        double worst = 0;
        for (double yaw : d.yaw())
            worst = Math.max(worst, Math.abs(Mth.wrapDegrees(yaw - 180.0)));
        assertTrue(worst <= 1.5, String.format(Locale.ROOT, "the turn across south swings %.0f degrees from south", worst));
    }

    /**
     * Clocks never used to tune the playback (2000-2199, both profiles): the gains generalize. Bounds, set before these
     * clocks were ever run, from clocks 1000-1199 run once after the tuning (worst cv 0.116, 0.6% frozen, 7 of 400 over
     * the strict bounds): every clock at most 1.0% frozen, cv 0.15 and the torso pitch bound, and at least 95% of them
     * within all the strict bounds. Not every clock can be: one 380 ms tick (clock 83 at 700 units) stands the server's
     * own units still for 7 client ticks, and a buffer that drew through it would draw every unit 400 ms late.
     */
    @Test
    void held_out_clocks_draw_the_walk_without_stops() {
        StringBuilder bad = new StringBuilder();
        int strict = 0, all = 0;
        for (int profile = 0; profile < 2; profile++)
            for (long seed = HELD_OUT_FROM; seed <= HELD_OUT_TO; seed++) {
                double[] at = profile == 0 ? at500(600, seed) : at700(600, seed);
                double[] j = judge(replay(at, UnitPlaybackTest::walkEast, Set.of()), 40, at.length - 40,
                    UnitPlaybackTest::walkEast);
                all++;
                if (j[1] <= MAX_FROZEN_PCT && j[0] <= MAX_CV && j[2] <= MAX_PITCH)
                    strict++;
                if (j[1] > 1.0 || j[0] > 0.15 || j[2] > MAX_PITCH)
                    bad.append(' ').append(fmt((profile == 0 ? "500u " : "700u ") + seed, j)).append(';');
            }
        System.out.println(String.format(Locale.ROOT, "[Motion] held-out clocks: %d of %d within the strict bounds",
            strict, all));
        assertTrue(bad.length() == 0, "held-out clocks drawn stop-go:" + bad);
        assertTrue(strict >= 0.95 * all, strict + " of " + all + " held-out clocks within the strict bounds (bound 95%)");
    }
}
