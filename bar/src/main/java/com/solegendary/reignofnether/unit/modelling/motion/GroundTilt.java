package com.solegendary.reignofnether.unit.modelling.motion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import static com.solegendary.reignofnether.unit.modelling.motion.AnimState.GROUND;
import static com.solegendary.reignofnether.unit.modelling.motion.AnimState.GROUND_PREV;

/**
 * The ground under the body (motion fluidity step 3, A; lovish, Nov 5: "units interactive with blocks the air and the
 * enviornment almost looks alive and fluid not choppy", "if we cant add physics maybe we can imitate it"). The server
 * stands a unit on the highest block its box overlaps, so a body crossing a step floats on one end over the lower
 * ground (a 1-block step leaves a War Mammoth's hind feet a block in the air for most of its length), and on the way
 * down it hangs over the edge. Each client tick this reads the ground under the body's four feet - a foot pad at each
 * corner of its footprint - and draws the body pitched and rolled to it and lowered (never raised) so the feet stand on
 * it, the way the body itself would settle on that ground.
 * <p>
 * Client drawing only: the server's position, collision and pathing are untouched. A narrow body (a man) stays nearly
 * upright, as people do on a step; a wide one (a beast) takes the ground's slope up to {@link #WIDE_DEG}. Every value
 * moves at most {@link #TILT_STEP} / {@link #DROP_STEP} a tick toward where the ground puts it, so a step is drawn as a
 * climb and a settle, never a pop. Pure but for {@link #of}: the slab and a {@link Ground}, no allocation per call.
 */
public final class GroundTilt {

    private GroundTilt() {}

    /**
     * The top of the ground in one block column near a height: the highest collision top within
     * {@link #REACH} above and {@link #DROP_REACH} below {@code nearY}, or NaN when the column has none in reach or
     * the first solid block from above reaches over it (a wall, a fence: no foot stands there).
     */
    @FunctionalInterface
    public interface Ground {
        double top(int bx, int bz, double nearY);
    }

    /** How far above and below the unit's feet a column's ground may stand to be ground at all, blocks. */
    public static final double REACH = 1.25, DROP_REACH = 1.5;
    /** The foot pads' centres from the body's centre: along its facing and across it, as shares of its width. */
    public static final double SPAN = 0.45, SIDE = 0.3;
    /** The most a body may tilt: NARROW_DEG at a man's width or under, WIDE_DEG at WIDE and over, between in step. */
    public static final float NARROW = 0.8f, WIDE = 1.6f, NARROW_DEG = 3f, WIDE_DEG = 30f;
    /** The most the body is drawn under the server's position, blocks. */
    public static final float MAX_DROP = 0.75f;
    /**
     * How far the drawn tilt (degrees) and drop (blocks) move in one client tick toward the ground's: a beast tips onto
     * a step's slope in 2-3 ticks (it should be on its feet before it has walked far over the edge), and the drop moves
     * less than the playback's drawn rise (0.34 a tick), so the body's height is never drawn moving faster than today.
     */
    public static final float TILT_STEP = 12f, DROP_STEP = 0.3f;

    /** The most a body this wide tilts, degrees. */
    public static float maxDeg(float width) {
        float k = Mth.clamp((width - NARROW) / (WIDE - NARROW), 0f, 1f);
        return Mth.lerp(k, NARROW_DEG, WIDE_DEG);
    }

    /** Half the side of a foot pad, blocks: a man's foot under a fifth of a block, a beast's up to half a block. */
    public static double footRadius(float width) {
        return Math.min(0.5, 0.15 * width + 0.1);
    }

    /** The ground a foot pad stands on: the highest top under the square of half-side {@code r} round (x, z); NaN if none. */
    public static double foot(Ground g, double x, double z, double r, double nearY) {
        int x0 = Mth.floor(x - r), x1 = Mth.floor(x + r - 1e-6), z0 = Mth.floor(z - r), z1 = Mth.floor(z + r - 1e-6);
        double best = Double.NaN;
        for (int bx = x0; bx <= x1; bx++)
            for (int bz = z0; bz <= z1; bz++) {
                double t = g.top(bx, bz, nearY);
                if (!Double.isNaN(t) && (Double.isNaN(best) || t > best))
                    best = t;
            }
        return best;
    }

    /** The higher of two grounds, either may be NaN (NaN only when both are). */
    private static double higher(double a, double b) {
        return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.max(a, b);
    }

    /**
     * Where the ground puts the body now: out[0] its pitch (degrees, + = nose up), out[1] its roll (degrees, + = its
     * right side up), out[2] its drop (blocks, 0 or below). The body at (x, y, z) - y where the client draws its feet -
     * faces {@code yawDeg} (Minecraft's yaw) and is {@code width} wide. Off the ground (the hop a unit climbs a step
     * with, a throw) it keeps the slope its feet are over but is drawn at its own height: nothing pulls it down out of
     * the air.
     */
    public static void target(Ground g, double x, double y, double z, float yawDeg, float width, boolean grounded,
                              float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        out[2] = 0f;
        if (g == null || !(width > 0f) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yawDeg))
            return;
        float yr = yawDeg * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yr), fz = Mth.cos(yr), rx = -Mth.cos(yr), rz = -Mth.sin(yr);
        double d = SPAN * width, s = SIDE * width, r = footRadius(width);
        // the four pads: front right, front left, back right, back left
        double fr = foot(g, x + fx * d + rx * s, z + fz * d + rz * s, r, y);
        double fl = foot(g, x + fx * d - rx * s, z + fz * d - rz * s, r, y);
        double br = foot(g, x - fx * d + rx * s, z - fz * d + rz * s, r, y);
        double bl = foot(g, x - fx * d - rx * s, z - fz * d - rz * s, r, y);
        double front = higher(fr, fl), back = higher(br, bl), right = higher(fr, br), left = higher(fl, bl);
        double max = maxDeg(width);
        // the slope that puts both ends on their ground (the rigid body's rise is its length x sin), up to the most
        // this body tilts
        double sp = 0, sq = 0;
        if (!Double.isNaN(front) && !Double.isNaN(back)) {
            double p = Mth.clamp(Math.toDegrees(Math.asin(Mth.clamp((front - back) / (2 * d), -1.0, 1.0))), -max, max);
            out[0] = (float) p;
            sp = Math.sin(Math.toRadians(p)) * d;
        }
        if (!Double.isNaN(right) && !Double.isNaN(left)) {
            double q = Mth.clamp(Math.toDegrees(Math.asin(Mth.clamp((right - left) / (2 * s), -1.0, 1.0))), -max, max);
            out[1] = (float) q;
            sq = Math.sin(Math.toRadians(q)) * s;
        }
        if (!grounded)
            return;
        // lowered until the first foot meets its ground, never raised: no pad's ground above its drawn foot
        double c = Double.NEGATIVE_INFINITY;
        c = under(c, fr, y, sp + sq);
        c = under(c, fl, y, sp - sq);
        c = under(c, br, y, -sp + sq);
        c = under(c, bl, y, -sp - sq);
        if (c != Double.NEGATIVE_INFINITY)
            out[2] = (float) Mth.clamp(c, -MAX_DROP, 0.0);
    }

    /**
     * The drop that puts a foot {@code off} above the body's centre on ground {@code h} (NaN: no ground, no say); the
     * highest of them is kept, so every foot stands on or over its ground.
     */
    private static double under(double c, double h, double y, double off) {
        return Double.isNaN(h) ? c : Math.max(c, h - y - off);
    }

    /**
     * Where the drawn feet stand against the ground, for the measurements: the four foot pads (front right, front left,
     * back right, back left) of a body drawn with this pitch, roll and drop. out[0] the most any pad floats over its
     * ground (0 if none does), out[1] the deepest any sinks into it (0 if none does), out[2] the summed |gap| over the
     * pads that have ground, out[3] how many have.
     */
    public static void feet(Ground g, double x, double y, double z, float yawDeg, float width, float pitchDeg,
                            float rollDeg, float drop, double[] out) {
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[3] = 0;
        if (g == null || !(width > 0f))
            return;
        float yr = yawDeg * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yr), fz = Mth.cos(yr), rx = -Mth.cos(yr), rz = -Mth.sin(yr);
        double d = SPAN * width, s = SIDE * width, r = footRadius(width);
        double sp = Math.sin(Math.toRadians(pitchDeg)) * d, sq = Math.sin(Math.toRadians(rollDeg)) * s;
        double base = y + drop;
        for (int a = 1; a >= -1; a -= 2)
            for (int l = 1; l >= -1; l -= 2) {
                double h = foot(g, x + a * fx * d + l * rx * s, z + a * fz * d + l * rz * s, r, y);
                if (Double.isNaN(h))
                    continue;
                double gap = base + a * sp + l * sq - h;
                out[0] = Math.max(out[0], gap);
                out[1] = Math.max(out[1], -gap);
                out[2] += Math.abs(gap);
                out[3]++;
            }
    }

    /**
     * One client tick of the drawn tilt and drop toward {@code target} ({@link #target}'s out): each moves at most
     * {@link #TILT_STEP} / {@link #DROP_STEP}; a fresh slot starts where the ground puts it.
     */
    public static void step(float[] s, int b, float[] target, boolean fresh) {
        for (int k = 0; k < 3; k++) {
            float t = Float.isFinite(target[k]) ? target[k] : 0f;
            float cur = s[b + GROUND + k];
            if (!Float.isFinite(cur))
                cur = 0f;
            float lim = k == 2 ? DROP_STEP : TILT_STEP;
            float next = fresh ? t : cur + Mth.clamp(t - cur, -lim, lim);
            s[b + GROUND_PREV + k] = fresh ? next : cur;
            s[b + GROUND + k] = next;
        }
    }

    /** The drawn pitch (degrees, + = nose up) between the last two ticks. */
    public static float pitchAt(float[] s, int b, float partial) {
        return at(s, b, 0, partial);
    }

    /** The drawn roll (degrees, + = the right side up) between the last two ticks. */
    public static float rollAt(float[] s, int b, float partial) {
        return at(s, b, 1, partial);
    }

    /** The drawn drop (blocks, 0 or below) between the last two ticks. */
    public static float dropAt(float[] s, int b, float partial) {
        return at(s, b, 2, partial);
    }

    private static float at(float[] s, int b, int k, float partial) {
        float prev = s[b + GROUND_PREV + k];
        return prev + (s[b + GROUND + k] - prev) * partial;
    }

    /** A level's ground ({@link Ground}) for one thread: block collision tops, read where they stand. */
    public static Ground of(BlockGetter level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        return (bx, bz, nearY) -> {
            int hi = Mth.floor(nearY + REACH), lo = Mth.floor(nearY - DROP_REACH);
            for (int by = hi; by >= lo; by--) {
                BlockState st = level.getBlockState(p.set(bx, by, bz));
                if (st.isAir())
                    continue;
                VoxelShape shape = st.getCollisionShape(level, p);
                if (shape.isEmpty())
                    continue;
                double top = by + shape.max(Direction.Axis.Y);
                return top > nearY + REACH ? Double.NaN : top;
            }
            return Double.NaN;
        };
    }
}
