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
    /** Clocks drawn per jittery profile: each must pass (one lucky draw proves nothing). */
    static final long[] SEEDS = { 3, 9, 10, 14, 21 };
    /** His Oct 29 770-unit game: 14.5 TPS. */
    static double[] at14(int n) { return starts(0, 0, 1000.0 / 14.5, n, 1); }
    /** His multi-thousand-unit stress games: 200 ms ticks, 5 TPS. */
    static double[] at5(int n) { return starts(0, 0, 200.0, n, 1); }

    // ------------------------------------------------------------------------------------------------ the replay

    /** The drawn unit per client tick, and the newest server tick the client had heard of at it. */
    record Drawn(double[] x, double[] z, long[] newest) { }

    /**
     * Replays a walk: {@code truth.apply(k)} is the unit's {x, z} after server tick k; {@code rotOnly} the ticks a
     * rotation-only packet goes out. Tick 0 is the spawn (the track is seeded there).
     */
    static Drawn replay(double[] at, IntFunction<double[]> truth, Set<Integer> rotOnly) {
        int ticks = at.length;
        final double phase = 17.0, transitMs = 1.0;
        List<Object[]> events = new ArrayList<>();   // arrival ms, server tick, kind (0 stamp, 1 pos, 2 rot), x, z
        double[] lastSent = truth.apply(0);
        for (int k = 1; k < ticks; k++) {
            double send = at[k] + transitMs;
            events.add(new Object[] { send, k, 0, 0.0, 0.0 });
            double[] p = truth.apply(k);
            boolean moved = Math.hypot(p[0] - lastSent[0], p[1] - lastSent[1]) > 1e-4;
            if (k % INTERVAL == 0 && (moved || k % 60 == 0)) {
                events.add(new Object[] { send + 0.01, k, 1, p[0], p[1] });
                lastSent = p;
            } else if (rotOnly.contains(k)) {
                events.add(new Object[] { send + 0.01, k, 2, 0.0, 0.0 });
            }
        }
        UnitPlayback.Clock clock = new UnitPlayback.Clock();
        UnitPlayback.Track track = new UnitPlayback.Track(INTERVAL);
        double[] p0 = truth.apply(0);
        clock.heard(0);
        track.seed(0, p0[0], 0, p0[1], -90f);
        int lastClient = (int) Math.ceil((at[ticks - 1] - phase) / 50.0) + 10;
        double[] xs = new double[lastClient + 1], zs = new double[lastClient + 1];
        long[] newest = new long[lastClient + 1];
        double[] out = new double[4];
        int e = 0;
        for (int c = 0; c <= lastClient; c++) {
            while (e < events.size() && (double) events.get(e)[0] <= c * 50.0 + phase) {
                Object[] ev = events.get(e++);
                int k = (int) ev[1];
                switch ((int) ev[2]) {
                    case 0 -> clock.heard(k);
                    case 1 -> track.moved(k, (double) ev[3], 0, (double) ev[4], -90f);
                    default -> track.turned(k, -80f);
                }
            }
            clock.clientTick();
            track.draw(clock, out);
            xs[c] = out[0];
            zs[c] = out[2];
            newest[c] = clock.newest();
        }
        return new Drawn(xs, zs, newest);
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
        double[] out = new double[4];
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

    /** A jump of more than {@link Kinematics#TELEPORT_BLOCKS} (a blink) is drawn as a jump, never as a slide across. */
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
}
