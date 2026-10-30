package com.solegendary.reignofnether.unit.controls;

/**
 * The Bone Dragon's steering, one server tick at a time (pure numbers: {@link DragonMoveControl} feeds it the way to
 * its wanted point and applies what it returns; DragonFlightTest flies it in empty air).
 *
 * Why (#145, lovish: the flight is wrong): the dragon flew on the ghast's brain ({@link FlyingUnitMoveControl}) -
 * every 2-6 random ticks a 0.06 b/t nudge straight at the point, then the air's 0.91 drag. A ghast is a balloon and
 * that reads right for one; a dragon on it surged and coasted at a random cadence, re-aimed its whole velocity in a
 * few ticks (it slid sideways and backwards, its nose snapping round after it), flew straight up and down, and drifted
 * to a dead stop past its point. Here it is a flier with a nose:
 * - a HEADING that turns at a capped rate - quick when slow ({@link #TURN_SLOW}), wide at cruise ({@link #TURN_CRUISE})
 *   - and the velocity always along it (no slip);
 * - a forward SPEED that eases up ({@link #ACCEL}) and down ({@link #DECEL}) toward the speed it wants: cruise, less in
 *   a hard turn (so a point beside it or behind it is reached by a tight turn, never an orbit), and a braking ramp over
 *   the last {@link #BRAKE} blocks (it comes in under the brake - the rig's flare plays - instead of coasting through);
 * - a climb rate that eases in and is capped ({@link #CLIMB_MAX}): it climbs and dives on its wings, not like a lift;
 * - over what stands on its way (DragonMoveControl reads the route ahead) a steeper ROUTE climb ({@link #ROUTE_CLIMB_MAX})
 *   paced to clear each obstacle in time ({@link #climbNeed}), easing back on the speed only when even that is not
 *   enough - it rises early and flies over, instead of crawling up the wall at the last moment.
 * The cruise speed is the ghast brain's average for the same attribute ({@link #cruiseSpeed}), so the dragon crosses
 * the map as fast as before - no gameplay number changes, only how it gets there.
 *
 * Heading in MC yaw degrees (0 = +z, 90 = -x); speeds in blocks per tick. Returns this tick's displacement.
 */
public final class DragonFlight {

    /** Degrees per tick the nose may turn at a standstill / at cruise speed. */
    public static final float TURN_SLOW = 9f, TURN_CRUISE = 4f;
    /** Forward speed change per tick, speeding up / slowing down. */
    public static final double ACCEL = 0.008, DECEL = 0.012;
    /** The braking ramp's length (blocks) and the radius inside which it wants to stop. */
    public static final double BRAKE = 5.0, STOP = 0.5;
    /** The climb's cap (b/t), its change per tick, and how much of the height still to gain it asks for per tick. */
    public static final double CLIMB_MAX = 0.10, CLIMB_ACCEL = 0.008, CLIMB_GAIN = 0.08;
    /** In a turn sharper than this the dragon slows (cos of the angle off, floored at TURN_SLOWDOWN_MIN). */
    public static final float TURN_SLOWDOWN_FROM = 30f;
    public static final double TURN_SLOWDOWN_MIN = 0.3;
    /** The route climb: its cap (b/t), its change per tick, how much faster than needed it climbs (it clears the top
     *  early, not at the last tick), and how high above a top the feet pass (blocks). */
    public static final double ROUTE_CLIMB_MAX = 0.25, ROUTE_CLIMB_ACCEL = 0.016, ROUTE_MARGIN = 1.15, ROUTE_CLEAR = 1.5;

    private DragonFlight() {}

    /** One dragon's flight state. */
    public static final class State {
        public float heading;
        public double speed, vy;
        /** This tick's displacement, reused every tick (step / dive / brake / perched return it; no allocation). */
        final double[] out = new double[3];

        public State(float heading) {
            this.heading = heading;
        }
    }

    /**
     * The ghast brain's mean ground speed for a movement-speed attribute: a nudge of attr / 5 every 4 ticks on average
     * (FlyingUnitMoveControl: 2 + nextInt(5)) against the 0.91 air drag, i.e. (attr / 5 / 4) / 0.09 per tick.
     */
    public static double cruiseSpeed(double attribute) {
        return attribute / 5.0 / 4.0 / 0.09;
    }

    /**
     * One tick toward a point (dx, dy, dz) away. blocked: the way ahead is shut - slow right down and climb over it.
     * Returns {vx, vy, vz}, this tick's displacement.
     */
    public static double[] step(State s, double dx, double dy, double dz, double cruise, boolean blocked) {
        return step(s, dx, dy, dz, cruise, blocked, 0.0);
    }

    /**
     * The climb (b/t) that lifts the feet {@code rise} blocks within {@code ahead} blocks at {@code speed} (with a block
     * to spare): 0 when there is nothing to rise over.
     */
    public static double climbNeed(double rise, double ahead, double speed) {
        if (rise <= 0.0)
            return 0.0;
        return rise / Math.max(1.0, (ahead - 1.0) / Math.max(speed, 0.05));
    }

    /**
     * As {@link #step(State, double, double, double, double, boolean)}, with the route ahead needing a climb of
     * {@code need} b/t ({@link #climbNeed}; 0: nothing in the way): it climbs at least ROUTE_MARGIN x that, up to
     * ROUTE_CLIMB_MAX, and if that cap is short it slows so the slope still clears.
     */
    public static double[] step(State s, double dx, double dy, double dz, double cruise, boolean blocked, double need) {
        double hd = Math.sqrt(dx * dx + dz * dz);
        float off = 0f;
        if (hd > STOP) {        // inside the stop radius it holds its nose: a push of a few tenths never swings it round
            float want = (float) Math.toDegrees(-Math.atan2(dx, dz));
            off = wrap(want - s.heading);
        }
        double ratio = cruise > 0 ? Math.min(1.0, s.speed / cruise) : 0.0;
        float maxTurn = (float) (TURN_SLOW + (TURN_CRUISE - TURN_SLOW) * ratio);
        float turn = Math.max(-maxTurn, Math.min(maxTurn, off));
        s.heading = wrap(s.heading + turn);
        float left = Math.abs(off - turn);

        double want = cruise * Math.min(1.0, Math.sqrt(Math.max(0.0, hd - STOP) / BRAKE));
        if (left > TURN_SLOWDOWN_FROM)
            want *= Math.max(TURN_SLOWDOWN_MIN, Math.cos(Math.toRadians(Math.min(90f, left))));
        double wantVy = Math.max(-CLIMB_MAX, Math.min(CLIMB_MAX, dy * CLIMB_GAIN));
        double climbAccel = CLIMB_ACCEL;
        if (need > 0.0) {
            wantVy = Math.min(ROUTE_CLIMB_MAX, Math.max(wantVy, need * ROUTE_MARGIN));
            climbAccel = ROUTE_CLIMB_ACCEL;
            if (need > ROUTE_CLIMB_MAX)
                want = Math.min(want, s.speed * ROUTE_CLIMB_MAX / need);
        }
        if (blocked) {
            want = Math.min(want, cruise * TURN_SLOWDOWN_MIN);
            wantVy = Math.max(wantVy, CLIMB_MAX);
        }
        s.speed += Math.max(-DECEL, Math.min(ACCEL, want - s.speed));
        s.vy += Math.max(-climbAccel, Math.min(climbAccel, wantVy - s.vy));
        return along(s);
    }

    /** The dive's fall cap (b/t), its change per tick, and the speed it stoops at (x cruise). */
    public static final double DIVE_VY = 0.45, DIVE_VY_ACCEL = 0.04, DIVE_SPEED = 1.4;

    /**
     * One tick of the Dive (BoneDragonUnit.startDive) toward a ground spot (dx, dy, dz) away: the nose turns as in
     * flight, it stoops at DIVE_SPEED x cruise easing off over the last blocks, and it comes down along the line to
     * the spot - the fall paced to the way still to go, so it meets the ground AT the spot, not short of it - capped
     * at DIVE_VY. Straight over the spot it drops at the cap.
     */
    public static double[] dive(State s, double dx, double dy, double dz, double cruise) {
        double hd = Math.sqrt(dx * dx + dz * dz);
        float off = hd > 0.05 ? wrap((float) Math.toDegrees(-Math.atan2(dx, dz)) - s.heading) : 0f;
        double ratio = cruise > 0 ? Math.min(1.0, s.speed / cruise) : 0.0;
        float maxTurn = (float) (TURN_SLOW + (TURN_CRUISE - TURN_SLOW) * ratio);
        float turn = Math.max(-maxTurn, Math.min(maxTurn, off));
        s.heading = wrap(s.heading + turn);
        float left = Math.abs(off - turn);
        double want = cruise * DIVE_SPEED * Math.min(1.0, Math.sqrt(hd / 2.0));
        if (left > TURN_SLOWDOWN_FROM)
            want *= Math.max(TURN_SLOWDOWN_MIN, Math.cos(Math.toRadians(Math.min(90f, left))));
        s.speed += Math.max(-DECEL * 2, Math.min(ACCEL * 2, want - s.speed));
        double wantVy = hd < 1.5 ? -DIVE_VY : dy * Math.max(s.speed, 0.05) / hd;
        wantVy = Math.max(-DIVE_VY, Math.min(0.0, wantVy));
        s.vy += Math.max(-DIVE_VY_ACCEL, Math.min(DIVE_VY_ACCEL, wantVy - s.vy));
        return along(s);
    }

    /** One tick on the ground (the Dive's landing): no way on, settle onto the ground. */
    public static double[] perched(State s) {
        s.speed = 0.0;
        s.vy = 0.0;
        s.out[0] = 0.0;
        s.out[1] = -0.3;
        s.out[2] = 0.0;
        return s.out;
    }

    /** One tick with nowhere to go: brake along the nose to a hover and level off. */
    public static double[] brake(State s) {
        s.speed = Math.max(0.0, s.speed - DECEL);
        s.vy += Math.max(-CLIMB_ACCEL, Math.min(CLIMB_ACCEL, -s.vy));
        return along(s);
    }

    private static double[] along(State s) {
        double r = Math.toRadians(s.heading);
        s.out[0] = -Math.sin(r) * s.speed;
        s.out[1] = s.vy;
        s.out[2] = Math.cos(r) * s.speed;
        return s.out;
    }

    public static float wrap(float a) {
        a %= 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }
}
