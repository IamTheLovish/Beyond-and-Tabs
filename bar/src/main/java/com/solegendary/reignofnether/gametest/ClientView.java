package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.unit.modelling.motion.AnimState;
import com.solegendary.reignofnether.unit.modelling.motion.Kinematics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * What a player's client draws of one unit, replayed from the packets the game's own entity tracker sent for it
 * (MotionFluidityGameTests records them with a copy of ServerEntity, as OrderLatencyGameTests does). The replay is the
 * 1.20.1 client's own handling, line for line: ClientPacketListener.handleMoveEntity / handleTeleportEntity call
 * LivingEntity.lerpTo(x, y, z, yRot, xRot, 3, ...) - a position packet walks the body to the new position over exactly
 * 3 client ticks, a position-only packet aims the yaw lerp at the yaw the client has now, a rotation-only packet aims
 * the position lerp at where the client draws the unit now - and LivingEntity.aiStep takes one step of that lerp a
 * client tick.
 * <p>
 * The client handles a packet at the start of the first client tick (50 ms apart) after it arrives, so what it draws
 * depends on WHEN the server sent each one. A GameTest server ticks as fast as it can, so its own clock says nothing
 * about a match: the replay puts the recorded ticks on a modelled match server instead ({@link Clock}): vanilla's
 * scheduler (a tick starts every 50 ms, or at once when the last one overran, so late ticks run back to back) with tick
 * durations drawn from a log-normal fitted to the p50 / p95 tick times in lovish's own server logs.
 */
final class ClientView {

    private ClientView() { }

    /** One packet the tracker sent at server tick {@code tick} (counted from the recording's start). */
    record Sent(int tick, boolean hasPos, double x, double y, double z, boolean hasRot, float yaw) { }

    /** A match server's clock: when tick k's packets leave (ms), for a run of ticks. */
    record Clock(String name, double p50Ms, double p95Ms, double fixedMs) {
        /** Every tick exactly 50 ms apart and short: what a quiet server does. */
        static Clock steady() { return new Clock("20tps", 0, 0, 0); }

        /** Ticks of p50 / p95 duration (log-normal) under vanilla's scheduler. */
        static Clock logged(String name, double p50, double p95) { return new Clock(name, p50, p95, 0); }

        /** A server that can't keep up at all: every tick takes {@code ms}. */
        static Clock slow(String name, double ms) { return new Clock(name, 0, 0, ms); }

        double[] sendTimes(int ticks, long seed) {
            double[] at = new double[ticks];
            Random rnd = new Random(seed);
            double mu = p50Ms > 0 ? Math.log(p50Ms) : 0, sigma = p50Ms > 0 ? Math.log(p95Ms / p50Ms) / 1.645 : 0;
            double end = 0, scheduled = 0;
            for (int k = 0; k < ticks; k++) {
                double start = Math.max(scheduled, end);
                double d = fixedMs > 0 ? fixedMs : p50Ms > 0 ? Math.exp(mu + sigma * rnd.nextGaussian()) : 1.0;
                at[k] = start + 0.1 * d;     // the chunk map's tracker pass runs early in the tick
                end = start + d;
                scheduled += 50.0;
                if (end - scheduled > 2000.0)  // vanilla gives up catching up after 2 s behind
                    scheduled = end;
            }
            return at;
        }
    }

    /** The client's drawn position, one entry per client tick, from the first client tick to the last packet + 6. */
    static final class Drawn {
        final double[] x, y, z;
        final float[] yaw;
        final int[] serverTickAt;   // the newest server tick whose packets the client has handled by that client tick

        Drawn(int n) {
            x = new double[n]; y = new double[n]; z = new double[n]; yaw = new float[n]; serverTickAt = new int[n];
        }
    }

    /**
     * Replays {@code sent} (in tick order) from a client that starts on the server's position (x0, y0, z0, yaw0), as
     * the spawn packet leaves it.
     */
    static Drawn replay(List<Sent> sent, int serverTicks, Clock clock, long seed,
                        double x0, double y0, double z0, float yaw0) {
        double[] at = clock.sendTimes(serverTicks, seed);
        final double phase = 17.0;    // the client's ticks are not in step with the server's
        int lastClientTick = (int) Math.ceil((at[serverTicks - 1] - phase) / 50.0) + 6;
        Drawn d = new Drawn(lastClientTick + 1);
        double x = x0, y = y0, z = z0, lx = x0, ly = y0, lz = z0;
        float yRot = yaw0, lyRot = yaw0;
        int steps = 0, next = 0, newest = -1;
        for (int c = 0; c <= lastClientTick; c++) {
            // packets first (Minecraft.runTick runs the queued tasks, then ticks)
            while (next < sent.size() && Math.ceil((at[sent.get(next).tick()] - phase) / 50.0) <= c) {
                Sent p = sent.get(next++);
                newest = Math.max(newest, p.tick());
                if (p.hasPos()) {
                    lx = p.x(); ly = p.y(); lz = p.z();
                    lyRot = p.hasRot() ? p.yaw() : yRot;
                } else {
                    lx = x; ly = y; lz = z;
                    lyRot = p.yaw();
                }
                steps = 3;
            }
            // LivingEntity.aiStep's lerp
            if (steps > 0) {
                x += (lx - x) / steps;
                y += (ly - y) / steps;
                z += (lz - z) / steps;
                yRot += (float) Mth.wrapDegrees(lyRot - yRot) / steps;
                steps--;
            }
            d.x[c] = x; d.y[c] = y; d.z[c] = z; d.yaw[c] = yRot; d.serverTickAt[c] = newest;
        }
        return d;
    }

    /**
     * The same packets drawn by a candidate fix (not in the game: a model, to weigh the options): the server stamps every
     * tick with its number (a tiny clock packet at the tick's start, so each move packet is known to belong to the tick
     * it came after) and the client draws each unit from its last few stamped positions at a playback time that runs
     * steadily {@code interval + margin} server ticks behind the newest tick it has heard of - a late or bunched
     * packet lands in the buffer before it is drawn. The playback clock follows the server's measured tick rate (an
     * average over client ticks) and is pulled 10% a tick toward its place; 20 ticks off, it jumps there. A unit
     * that stood still gets a held sample one interval before it moves again, so it starts on time, not early.
     */
    static Drawn replayBuffered(List<Sent> sent, int serverTicks, Clock clock, long seed, int interval, double margin,
                                double x0, double y0, double z0, float yaw0) {
        double[] at = clock.sendTimes(serverTicks, seed);
        final double phase = 17.0;
        int lastClientTick = (int) Math.ceil((at[serverTicks - 1] - phase) / 50.0) + 6;
        Drawn d = new Drawn(lastClientTick + 1);
        List<double[]> buf = new ArrayList<>();   // tick, x, y, z, yaw
        buf.add(new double[] { -interval, x0, y0, z0, yaw0 });
        int next = 0, newestTick = -1, prevNewest = -1, clockTick = 0;
        double est = Double.NaN, rate = 1.0;
        for (int c = 0; c <= lastClientTick; c++) {
            // the clock packets of every server tick that started before this client tick, then its move packets
            while (clockTick < serverTicks && Math.ceil((at[clockTick] - phase) / 50.0) <= c)
                newestTick = clockTick++;
            while (next < sent.size() && sent.get(next).tick() <= newestTick) {
                Sent p = sent.get(next++);
                double[] last = buf.get(buf.size() - 1);
                if (p.tick() - last[0] > interval)
                    buf.add(new double[] { p.tick() - interval, last[1], last[2], last[3], last[4] });
                buf.add(p.hasPos() ? new double[] { p.tick(), p.x(), p.y(), p.z(), p.hasRot() ? p.yaw() : last[4] }
                    : new double[] { p.tick(), last[1], last[2], last[3], p.yaw() });
                while (buf.size() > 12)
                    buf.remove(0);
            }
            if (newestTick >= 0) {
                if (Double.isNaN(est)) {
                    est = newestTick;
                    prevNewest = newestTick;
                }
                rate += 0.04 * ((newestTick - prevNewest) - rate);
                prevNewest = newestTick;
                est += rate;
                double err = newestTick - est;
                est = Math.abs(err) > 20 ? newestTick : est + 0.1 * err;
            }
            double play = Double.isNaN(est) ? -interval : est - (interval + margin);
            double[] a = buf.get(0), b = a;
            for (double[] s : buf) {
                b = s;
                if (s[0] >= play)
                    break;
                a = s;
            }
            double f = b[0] > a[0] ? Mth.clamp((play - a[0]) / (b[0] - a[0]), 0, 1) : 1;
            d.x[c] = a[1] + (b[1] - a[1]) * f;
            d.y[c] = a[2] + (b[2] - a[2]) * f;
            d.z[c] = a[3] + (b[3] - a[3]) * f;
            d.yaw[c] = (float) (a[4] + Mth.wrapDegrees(b[4] - a[4]) * f);
            d.serverTickAt[c] = newestTick;
        }
        return d;
    }

    /** The drawn walk's smoothness between two server ticks of the walk (the server's own judged window). */
    record Smooth(double cv, double frozenPct, double jumpRatio, double pitchDeg, double maxDy, double lagTicks, int ticks) {
        String brief() {
            return String.format(Locale.ROOT, "%.2f/%.1f/%.1f/%.1f", cv, frozenPct, pitchDeg, lagTicks);
        }
    }

    /**
     * Over the client ticks that show server ticks [from, to): the per-tick horizontal speed's coefficient of variation,
     * the share of ticks the unit stood still (under a tenth of the server's mean speed), the largest tick's move over
     * the mean, the mean body pitch the living army's lean would give those speeds (Kinematics.step's ACCEL ->
     * PITCH_PER_ACCEL, clamped to PITCH_MAX_DEG: the twitch a hitch puts in the torso), and the largest rise in a tick.
     */
    static Smooth smoothness(Drawn d, int from, int to, double serverMeanSpeed, double[] serverX, double[] serverZ) {
        List<Double> speeds = new ArrayList<>();
        double maxDy = 0, lagSum = 0;
        int lagN = 0;
        // the walk's direction, start to end of the window: the lag is how far behind the newest known server position
        // the drawn unit is, along it, in ticks of the walk's mean speed
        int f0 = Math.max(0, Math.min(from, serverX.length - 1)), t0 = Math.max(0, Math.min(to - 1, serverX.length - 1));
        double wx = serverX[t0] - serverX[f0], wz = serverZ[t0] - serverZ[f0], wl = Math.sqrt(wx * wx + wz * wz);
        if (wl > 1e-6) { wx /= wl; wz /= wl; }
        float[] slab = new float[AnimState.STRIDE];
        double pitchSum = 0;
        int pitchN = 0;
        boolean fresh = true;
        for (int c = 1; c < d.x.length; c++) {
            if (d.serverTickAt[c - 1] < from || d.serverTickAt[c] >= to)
                continue;
            double dx = d.x[c] - d.x[c - 1], dz = d.z[c] - d.z[c - 1];
            speeds.add(Math.sqrt(dx * dx + dz * dz));
            int newest = Math.min(d.serverTickAt[c], serverX.length - 1);
            if (newest >= 0 && serverMeanSpeed > 1e-6 && wl > 1e-6) {
                lagSum += ((serverX[newest] - d.x[c]) * wx + (serverZ[newest] - d.z[c]) * wz) / serverMeanSpeed;
                lagN++;
            }
            maxDy = Math.max(maxDy, d.y[c] - d.y[c - 1]);
            Kinematics.step(slab, 0, (float) dx, (float) (d.y[c] - d.y[c - 1]), (float) dz, 0f, 20f, 20f, false, fresh);
            if (!fresh) {
                pitchSum += Math.abs(Mth.clamp(slab[AnimState.ACCEL] * Kinematics.PITCH_PER_ACCEL,
                    -Kinematics.PITCH_MAX_DEG, Kinematics.PITCH_MAX_DEG));
                pitchN++;
            }
            fresh = false;
        }
        double lag = lagN > 0 ? lagSum / lagN : Double.NaN;
        if (speeds.size() < 4)
            return new Smooth(Double.NaN, Double.NaN, Double.NaN, Double.NaN, maxDy, lag, speeds.size());
        double[] s = speeds.stream().mapToDouble(Double::doubleValue).toArray();
        double mean = Arrays.stream(s).average().orElse(0), var = 0, max = 0;
        int frozen = 0;
        for (double v : s) {
            var += (v - mean) * (v - mean);
            max = Math.max(max, v);
            if (v < 0.1 * serverMeanSpeed)
                frozen++;
        }
        double cv = mean > 1e-6 ? Math.sqrt(var / s.length) / mean : Double.NaN;
        return new Smooth(cv, 100.0 * frozen / s.length, serverMeanSpeed > 1e-6 ? max / serverMeanSpeed : Double.NaN,
            pitchN > 0 ? pitchSum / pitchN : 0, maxDy, lag, s.length);
    }
}
