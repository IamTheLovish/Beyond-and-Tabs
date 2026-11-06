package com.solegendary.reignofnether.unit.modelling.motion;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground under the body (GroundTilt, motion fluidity step 3 A). A body walks east along MotionFluidityGameTests'
 * stepped lane - floor, up 1, up 2, a slab down to 1.5, 1, the floor - as the server moves it (it stands on the highest
 * block its box overlaps) and as the client draws its height (rising or falling at most 0.34 a client tick, as the
 * motion measurement shows the playback draw a step: "r0.34"). Each client tick its four foot pads are measured against
 * the ground under them, drawn as today (upright at the drawn position) and drawn with the ground's tilt and drop.
 * <p>
 * The bounds, set before the fix: a beast's feet (Mossback Bison, War Mammoth) stand at most half today's mean gap off
 * the ground, never float more than three quarters of today's worst, never sink deeper than today's worst, and the
 * body's height never moves more in one client tick than today's does (the step is not drawn poppier). A man (a
 * Vindicator) is never drawn worse than today. On flat ground nothing changes at all.
 */
class GroundTiltTest {

    /** Minecraft yaw facing +x (east). */
    private static final float EAST = -90f;
    /** The drawn height's largest move in one client tick (the playback's drawn rise, "r0.34" in the [Motion] notice). */
    private static final double RISE = 0.34;
    /** The walk is judged over the steps and a few blocks either side of them. */
    private static final double FROM = 8, TO = 32;

    /** The stepped lane's ground along x (MotionFluidityGameTests.steps), above the pad's floor. */
    private static double stepped(int bx) {
        if (bx >= 16 && bx <= 19)
            return 2.0;
        if (bx >= 20 && bx <= 23)
            return 1.5;
        if (bx >= 12 && bx <= 27)
            return 1.0;
        return 0.0;
    }

    /** A lane's profile as GroundTilt.of reads a level: the column's top when it stands within reach, else NaN. */
    private static GroundTilt.Ground ground(IntToDoubleFunction profile) {
        return (bx, bz, nearY) -> {
            double top = profile.applyAsDouble(bx);
            return top > nearY + GroundTilt.REACH || top < nearY - GroundTilt.DROP_REACH ? Double.NaN : top;
        };
    }

    /** The server's height for a box `width` wide centred on x: the highest block it overlaps. */
    private static double serverY(IntToDoubleFunction profile, double x, double width) {
        double best = Double.NEGATIVE_INFINITY;
        for (int bx = Mth.floor(x - width / 2); bx <= Mth.floor(x + width / 2 - 1e-6); bx++)
            best = Math.max(best, profile.applyAsDouble(bx));
        return best;
    }

    /** One walk, measured. Gaps in blocks: mean |gap| per foot pad, the worst float, the worst sink. */
    private record Walk(double meanToday, double mean, double floatToday, double floatMax, double sinkToday,
                        double sinkMax, double bodyStepToday, double bodyStep, double maxTilt, double maxDrop) {
        String brief() {
            return String.format(Locale.ROOT, "mean gap %.3f -> %.3f, worst float %.2f -> %.2f, worst sink %.2f -> %.2f,"
                    + " body step %.2f -> %.2f, tilt up to %.1f deg, drop up to %.2f", meanToday, mean, floatToday, floatMax,
                sinkToday, sinkMax, bodyStepToday, bodyStep, maxTilt, maxDrop);
        }
    }

    private static Walk walk(IntToDoubleFunction profile, float width, double speed) {
        GroundTilt.Ground g = ground(profile);
        float[] s = new float[AnimState.STRIDE];
        float[] target = new float[3];
        double[] feet = new double[4], today = new double[4];
        double z = 5.5, x = 2.5;
        double y = serverY(profile, x, width), body = y, bodyToday = y;
        double sum = 0, sumToday = 0, pads = 0, padsToday = 0, floatMax = 0, floatToday = 0, sinkMax = 0, sinkToday = 0;
        double bodyStep = 0, bodyStepToday = 0, maxTilt = 0, maxDrop = 0;
        boolean fresh = true;
        for (; x < 34; x += speed) {
            y += Mth.clamp(serverY(profile, x, width) - y, -RISE, RISE);
            GroundTilt.target(g, x, y, z, EAST, width, true, target);
            GroundTilt.step(s, 0, target, y, fresh);
            fresh = false;
            float p = GroundTilt.pitchAt(s, 0, 1f), r = GroundTilt.rollAt(s, 0, 1f), d = GroundTilt.dropAt(s, 0, 1f);
            GroundTilt.feet(g, x, y, z, EAST, width, p, r, d, feet);
            GroundTilt.feet(g, x, y, z, EAST, width, 0f, 0f, 0f, today);
            maxTilt = Math.max(maxTilt, Math.max(Math.abs(p), Math.abs(r)));
            maxDrop = Math.max(maxDrop, -d);
            if (x >= FROM && x <= TO) {
                sum += feet[2];
                pads += feet[3];
                sumToday += today[2];
                padsToday += today[3];
                floatMax = Math.max(floatMax, feet[0]);
                floatToday = Math.max(floatToday, today[0]);
                sinkMax = Math.max(sinkMax, feet[1]);
                sinkToday = Math.max(sinkToday, today[1]);
                bodyStep = Math.max(bodyStep, Math.abs(y + d - body));
                bodyStepToday = Math.max(bodyStepToday, Math.abs(y - bodyToday));
            }
            body = y + d;
            bodyToday = y;
        }
        return new Walk(sumToday / Math.max(1, padsToday), sum / Math.max(1, pads), floatToday, floatMax, sinkToday,
            sinkMax, bodyStepToday, bodyStep, maxTilt, maxDrop);
    }

    private static void beast(String name, float width, double speed) {
        Walk w = walk(GroundTiltTest::stepped, width, speed);
        String why = name + " on the stepped lane: " + w.brief();
        assertTrue(w.meanToday() > 0.05, "the measurement has no step to judge: " + why);
        assertTrue(w.mean() <= 0.5 * w.meanToday(), "the feet stand off the ground: " + why);
        assertTrue(w.floatMax() <= 0.75 * w.floatToday(), "a foot still floats over a step: " + why);
        assertTrue(w.sinkMax() <= w.sinkToday() + 1e-9, "a foot sinks deeper into a step than today: " + why);
        assertTrue(w.bodyStep() <= w.bodyStepToday() + 1e-9, "the step is drawn poppier than today: " + why);
    }

    @Test
    void a_beast_crossing_a_step_stands_on_it_with_all_four_feet() {
        beast("Mossback Bison (1.95 wide, 0.153 a tick)", 1.95f, 0.153);
        beast("War Mammoth (3.3 wide, 0.185 a tick)", 3.315f, 0.185);
    }

    @Test
    void a_man_crossing_a_step_is_never_drawn_worse_than_today() {
        Walk w = walk(GroundTiltTest::stepped, 0.6f, 0.131);
        String why = "Vindicator (0.6 wide, 0.131 a tick) on the stepped lane: " + w.brief();
        assertTrue(w.maxTilt() <= GroundTilt.NARROW_DEG + 1e-4, "a man tilts like a beast: " + why);
        assertTrue(w.mean() <= w.meanToday() + 1e-9, "the feet stand further off the ground than today: " + why);
        assertTrue(w.sinkMax() <= w.sinkToday() + 1e-9, "a foot sinks deeper into a step than today: " + why);
        assertTrue(w.bodyStep() <= w.bodyStepToday() + 1e-9, "the step is drawn poppier than today: " + why);
    }

    @Test
    void flat_ground_draws_every_body_exactly_as_today() {
        for (float width : new float[] { 0.6f, 1.95f, 3.315f }) {
            Walk w = walk(bx -> 0.0, width, 0.15);
            assertEquals(0.0, w.maxTilt(), 0.0, width + " wide: " + w.brief());
            assertEquals(0.0, w.maxDrop(), 0.0, width + " wide: " + w.brief());
            assertEquals(0.0, w.mean(), 0.0, width + " wide: " + w.brief());
        }
    }
}
