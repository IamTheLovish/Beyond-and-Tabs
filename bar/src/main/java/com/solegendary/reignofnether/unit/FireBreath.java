package com.solegendary.reignofnether.unit;

/**
 * A dragon's fire breath, the rules alone (no Minecraft classes, so JUnit runs them: FireBreathTest). The Bone Dragon
 * is the first breather (lovish, Nov 5: "the bone dragon should get a blue fire breath attack from the air not the
 * current ghast sound effects and projectiles", "the ground effect is very choppy and currently weak it was barely
 * able to hit anything without bugging out", "bone dragon can use this fire breath on the ground as well").
 *
 * <p>One breath lasts {@link #TICKS} and burns every {@link #PULSE_TICKS} - one cone query per burn, never one per
 * flame drawn. Its cone runs from the jaws toward an AIM that sweeps over the breath ({@link #sweep}): a base point (the
 * target) moved along an offset ({@link #aim}, THE shared entry: the server's burns and the client's flames both read
 * it from the same synced numbers, so what burns is what shows).
 * <ul>
 *   <li><b>Airborne</b>: down from the jaws onto the ground. The aim strafes along the dragon's heading from ON its
 *   target to {@link #AIR_SWEEP} past it - a line of fire through an army, the RTS shot of a dragon over it. It used to
 *   start AIR_SWEEP short (base - offset to base + offset): the first two of five burns fell between the dragon and
 *   the squad and the splash drew 3-4 blocks short of it (eyes 42083382498, 42172015718). A narrow cone
 *   ({@link #AIR_HALF_DEG}) that stops {@link #AIR_PAST} past the aim: no further into the ground.</li>
 *   <li><b>Landed</b>: a forward cone at ground level, {@link #GROUND_LENGTH} long and {@link #GROUND_HALF_DEG} to each
 *   side, swinging {@link #GROUND_SWEEP_DEG} side to side across the target.</li>
 * </ul>
 * A body counts when any part of it is inside: its centre within the cone's radius at that depth plus its own radius.
 */
public final class FireBreath {
    private FireBreath() { }

    /** One breath: 2.5 s. Under the Bone Dragon's 66-tick attack cycle, so it draws breath ~0.8 s between two. */
    public static final int TICKS = 50;
    /** A burn every half second: ticks 0, 10, 20, 30, 40 of the breath. */
    public static final int PULSE_TICKS = 10;
    /** Airborne: the cone's half-angle, how far past the aim it reaches, its longest, and the strafe's half-length. */
    public static final double AIR_HALF_DEG = 15, AIR_PAST = 2.5, AIR_MAX = 22, AIR_SWEEP = 3.0;
    /** Landed: the cone's half-angle, its length from the jaws, and the side-to-side swing (degrees each way). */
    public static final double GROUND_HALF_DEG = 30, GROUND_LENGTH = 12, GROUND_SWEEP_DEG = 12;
    /** Within this of the jaws a body burns whichever way the cone points. */
    public static final double JAWS = 1.0;
    /** How long a drawn flame flies from the jaws to the ground it lands on (FireBreath#flame; client). */
    public static final double FLAME_SECONDS = 0.55;

    private static final double AIR_TAN = Math.tan(Math.toRadians(AIR_HALF_DEG));
    private static final double GROUND_TAN = Math.tan(Math.toRadians(GROUND_HALF_DEG));
    private static final double SWING_TAN = Math.tan(Math.toRadians(GROUND_SWEEP_DEG));

    /** Does the breath burn on this tick of it (0 = its first)? */
    public static boolean pulse(int tick) {
        return tick >= 0 && tick < TICKS && tick % PULSE_TICKS == 0;
    }

    /** How far through its sweep the breath is at this tick: 0 on the first, 1 on the last, eased in and out. */
    public static double sweep(int tick) {
        return sweep((double) tick);
    }

    /** {@link #sweep(int)} at a fractional tick (the client draws between ticks). */
    public static double sweep(double tick) {
        double t = Math.max(0, Math.min(1, tick / (TICKS - 1)));
        return t * t * (3 - 2 * t);
    }

    /** Airborne: the strafe's offset (out x, y, z), along the flat heading (hx, hz), {@link #AIR_SWEEP} long. */
    public static void airOffset(double hx, double hz, double[] out) {
        double l = Math.sqrt(hx * hx + hz * hz);
        if (l < 1.0e-6) {
            hx = 0;
            hz = 1;
            l = 1;
        }
        out[0] = hx / l * AIR_SWEEP;
        out[1] = 0;
        out[2] = hz / l * AIR_SWEEP;
    }

    /**
     * Landed: the swing's offset (out x, y, z) for a breath from (fromX, fromZ) at (aimX, aimZ): square to the line of
     * fire, as long as the distance times tan({@link #GROUND_SWEEP_DEG}), so the aim swings that many degrees each way.
     */
    public static void groundOffset(double fromX, double fromZ, double aimX, double aimZ, double[] out) {
        double dx = aimX - fromX, dz = aimZ - fromZ, l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1.0e-6) {
            out[0] = out[1] = out[2] = 0;
            return;
        }
        double w = l * SWING_TAN;
        out[0] = -dz / l * w;
        out[1] = 0;
        out[2] = dx / l * w;
    }

    /** One coordinate of the aim at sweep s: base + offset * (2s - 1). */
    public static double along(double base, double offset, double sweep) {
        return base + offset * (2 * sweep - 1);
    }

    /**
     * One coordinate of the breath's aim at sweep s - the server's burns (BoneDragonUnit.breathAim) and the client's
     * flames (BreathFx) both come here. Landed: {@link #along}, swinging side to side across the target. Airborne: from
     * the target (s = 0) to the offset past it (s = 1), along the heading - never short of it.
     */
    public static double aim(boolean landed, double base, double offset, double sweep) {
        return landed ? along(base, offset, sweep) : base + offset * sweep;
    }

    /** The cone's length for an aim {@code dist} from the jaws. */
    public static double length(boolean landed, double dist) {
        return landed ? GROUND_LENGTH : Math.min(AIR_MAX, dist + AIR_PAST);
    }

    /** tan of the cone's half-angle. */
    public static double tanHalf(boolean landed) {
        return landed ? GROUND_TAN : AIR_TAN;
    }

    /**
     * Does the cone from (ax, ay, az) along the unit axis (dx, dy, dz), {@code length} long and {@code tanHalf} wide,
     * reach a body of radius r centred at (px, py, pz)? Within {@link #JAWS} of the jaws always; behind them never;
     * past length + r never; else when its distance from the axis is at most the cone's radius there plus r.
     */
    public static boolean reaches(double ax, double ay, double az, double dx, double dy, double dz, double length,
                                  double tanHalf, double px, double py, double pz, double r) {
        double vx = px - ax, vy = py - ay, vz = pz - az;
        double d2 = vx * vx + vy * vy + vz * vz;
        if (d2 <= JAWS * JAWS)
            return true;
        double t = vx * dx + vy * dy + vz * dz;
        if (t <= 0 || t > length + r)
            return false;
        double perp2 = Math.max(0, d2 - t * t);
        double rad = Math.min(t, length) * tanHalf + r;
        return perp2 <= rad * rad;
    }

    /**
     * Does the cone reach any part of the box (a building's corners)? The box's point nearest each one-block step down
     * the axis is tested ({@link #reaches} with no radius): at most ~23 steps, for the few buildings near the cone.
     */
    public static boolean reachesBox(double ax, double ay, double az, double dx, double dy, double dz, double length,
                                     double tanHalf, double minX, double minY, double minZ, double maxX, double maxY,
                                     double maxZ) {
        for (double t = 0; t <= length + 1.0e-9; t += 1.0) {
            double sx = ax + dx * t, sy = ay + dy * t, sz = az + dz * t;
            double qx = Math.max(minX, Math.min(maxX, sx)), qy = Math.max(minY, Math.min(maxY, sy));
            double qz = Math.max(minZ, Math.min(maxZ, sz));
            if (reaches(ax, ay, az, dx, dy, dz, length, tanHalf, qx, qy, qz, 0))
                return true;
        }
        return false;
    }

    /**
     * One drawn flame (client): the velocity (out, blocks per SECOND) that takes it from the jaws (ax, ay, az) in
     * {@link #FLAME_SECONDS} to a point on the aim's ground, inside the cone's radius around the aim - u, v in [0, 1)
     * pick the point (sqrt(u) spreads them evenly over the disc). The old breath's flames stopped ~8 blocks over the
     * ground they burnt; these land on it.
     */
    public static void flame(double ax, double ay, double az, double aimX, double aimY, double aimZ, double tanHalf,
                             double u, double v, double[] out) {
        double dx = aimX - ax, dy = aimY - ay, dz = aimZ - az;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double r = dist * tanHalf * 0.85 * Math.sqrt(u), a = v * Math.PI * 2;
        double tx = aimX + Math.cos(a) * r, tz = aimZ + Math.sin(a) * r;
        out[0] = (tx - ax) / FLAME_SECONDS;
        out[1] = (aimY - ay) / FLAME_SECONDS;
        out[2] = (tz - az) / FLAME_SECONDS;
    }
}
