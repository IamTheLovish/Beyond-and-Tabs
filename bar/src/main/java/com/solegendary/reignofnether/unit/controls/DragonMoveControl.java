package com.solegendary.reignofnether.unit.controls;

import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Bone Dragon's move control (#145): {@link DragonFlight}'s steering every tick in place of the ghast's random
 * nudges - a nose that turns at a capped rate, the velocity along it, a smooth speed-up, a braking ramp into the point
 * and a gentle climb - keeping {@link FlyingUnitMoveControl}'s sweep (a shut way ahead climbs over the obstacle; one
 * that cannot climb either waits). With nowhere to go it brakes along its nose to a hover instead of coasting.
 *
 * The Stormcallers' Celestial and Thunder dragons fly on it too (lovish, Oct 30: "choppy laggy ... isnt fluid at all":
 * they were still on the ghast's nudges, their yaw snapped to the velocity). A dragon that is down but no flyer (the
 * Celestial Dragon grounded by wind) walks: the same steering over the ground, pressed onto it.
 *
 * The way there (lovish, Oct 30: "gets stuck on buildings"): the flight point is only ten blocks over the ground at the
 * destination, so anything taller on the way stood in the line, and the sweep saw it a dozen blocks out - it slowed to a
 * crawl and climbed its wall at the gentle climb's cap, and under an overhang (the climb shut too) it waited, the goal
 * re-issued the move next tick and it pressed into the wall again, for good. Now it reads the ROUTE ahead
 * ({@link #scanRoute}: the heightmap across its width out to {@link #ROUTE_LOOK} blocks) and climbs early, at the pace
 * that clears each thing in time (DragonFlight's route climb), holds that height over a town, and comes down past it.
 * It climbs at most {@link #ROUTE_HEADROOM} over its flight height (lovish: "increase max flying high by a bit then go
 * around if it cant fly over that new max"): what stands taller it flies round ({@link #findBypass}). Boxed in with no
 * way up, it side-steps ({@link #DETOUR_TICKS}) instead of waiting.
 *
 * The flight owns the velocity, but a push from outside (knockback, a separation nudge) is kept and decays in the air
 * as it did before: whatever the velocity holds beyond what this control set last tick (after the 0.91 drag) is
 * carried on top of the steering.
 */
public class DragonMoveControl extends FlyingUnitMoveControl {
    /** The air drag FlyingMob.travel applies after the move. */
    private static final double DRAG = 0.91;
    /** Ticks between the sweeps ahead (each sweeps the big hitbox up to MAX_SWEEP lengths: not every tick). */
    private static final int SWEEP_EVERY = 4;
    private static final int MAX_SWEEP = 12;

    private final Mob mob;
    private final DragonFlight.State state;
    private Vec3 lastSet = Vec3.ZERO, push = Vec3.ZERO;
    private boolean blocked = false;
    private int sweepIn = 0;
    /** The route ahead: how far ahead it reads (blocks) and the spacing of its samples along the way. */
    static final double ROUTE_LOOK = 32.0, ROUTE_STEP = 2.0;
    /** The held route height sinks this much a tick once what was ahead is behind it (it does not dip between roofs),
     *  and is dropped this close to the point (blocks, flat): it comes down onto its point. */
    static final double FLOOR_DECAY = 0.06, HOLD_UNTIL = 12.0;
    /** A side-step's length (ticks) when the way ahead and the way up are both shut. */
    static final int DETOUR_TICKS = 24;
    /** How far over its flight height (the move goal's: 10 over the ground at the point) it may climb to cross
     *  something; taller, it flies round. */
    public static final double ROUTE_HEADROOM = 6.0;
    /** A way round: how long one is flown before the straight line is tried again (ticks), how far off the line it
     *  may turn (degrees, in steps of BYPASS_STEP), and how far along it it aims (blocks). */
    static final int BYPASS_TICKS = 8;
    static final float BYPASS_STEP = 20f, BYPASS_MAX = 80f;
    static final double BYPASS_AIM = 16.0;
    private double routeTop = Double.NEGATIVE_INFINITY, routeNeed = 0.0, heldTop = Double.NEGATIVE_INFINITY;
    private float bypassYaw = 0f;
    private int bypassTicks = 0, bypassSide = 1;
    private float detourYaw = 0f;
    private int detourTicks = 0, detourSide = 1;
    /** The Dive's ground spot while it stoops (BoneDragonUnit.startDive), and whether it is down on the ground. */
    private Vec3 diveTo = null;
    private boolean landed = false;

    public DragonMoveControl(Mob mob) {
        super(mob);
        this.mob = mob;
        this.state = new DragonFlight.State(mob.getYRot());
    }

    /** The nose's heading (MC yaw degrees): where the dragon is flying. */
    public float heading() {
        return state.heading;
    }

    /** How fast (degrees per tick) a hovering dragon turns to face what it attacks (BoneDragonUnit.HOVER_TURN). */
    public static final float HOVER_TURN = 6f;

    /**
     * The body's yaw this tick (server side), from its yaw now: along the nose while it cruises; hovering, turned
     * toward (x, z) - the way to what it attacks, (0, 0) for nothing - at {@link #HOVER_TURN} degrees a tick, and the
     * next flight starts from there.
     */
    public float bodyYaw(float yaw, double x, double z) {
        if (isCruising())
            return state.heading;
        if (Math.abs(x) > 0.05 || Math.abs(z) > 0.05)
            yaw = Mth.approachDegrees(yaw, -((float) Mth.atan2(x, z)) * Mth.RAD_TO_DEG, HOVER_TURN);
        setHeading(yaw);
        return yaw;
    }

    /** Turns the nose while hovering (the dragon facing its target); the next flight starts from here. */
    public void setHeading(float yaw) {
        state.heading = Mth.wrapDegrees(yaw);
    }

    /**
     * Flying somewhere, or still carrying speed: the body faces along the flight. A MOVE_TO already on its point is
     * not (a held position or a persistent move leaves it set): there the dragon hovers and faces its target.
     */
    public boolean isCruising() {
        if (landed)
            return false;
        if (diveTo != null || state.speed > 0.03)
            return true;
        if (operation != Operation.MOVE_TO)
            return false;
        double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ();
        return dx * dx + dz * dz > DragonFlight.STOP * DragonFlight.STOP;
    }

    /** The forward speed and the climb (b/t), for the dive's save; restored by {@link #restoreFlight}. */
    public double speed() {
        return state.speed;
    }

    public double climb() {
        return state.vy;
    }

    /** Picks the flight up where a saved dragon left it (a reload mid-dive keeps its way on). */
    public void restoreFlight(double speed, double climb) {
        state.speed = speed;
        state.vy = climb;
    }

    /** Stoop onto this ground spot (null: stop diving) - every other order waits while it dives. */
    public void setDive(Vec3 spot) {
        diveTo = spot;
    }

    /** On the ground (the Dive's landing): no way on, whatever the move goal wants, until it is let up again. */
    public void setLanded(boolean landed) {
        this.landed = landed;
        if (landed)
            diveTo = null;
    }

    @Override
    public void tick() {
        // the nose starts from the body's yaw as it is now: BoneDragonUnit.updateRotation leaves the two equal, but a
        // dragon ordered in its spawn tick (the state was built at yaw 0, before the spawn's moveTo) or turned by other
        // code (a conversion, a possession) would otherwise swing its nose to a stale heading in one tick
        state.heading = Mth.wrapDegrees(mob.getYRot());
        // what moved us besides the steering since last tick (a knockback, a push) rides on top and decays
        Vec3 now = mob.getDeltaMovement();
        // (in water or lava the drag is not the air's 0.91, so what is left there is no push)
        // diving or down, nothing pushes it: the stoop is aimed and a landed dragon is rooted (#300 CI: the units'
        // separation shoved a dragon landing on a crowd 4.6 blocks off its spot)
        if (landed || diveTo != null || mob.horizontalCollision || mob.verticalCollision || mob.isInWater() || mob.isInLava())
            push = Vec3.ZERO;
        else    // last tick's push is in there too, already through the drag
            push = now.subtract(lastSet.scale(DRAG));
        // a bump stopped it: the flight's speed is what it really has (it does not push on at cruise into the wall)
        if (mob.horizontalCollision)
            state.speed = Math.min(state.speed, now.horizontalDistance() / DRAG);
        if (push.lengthSqr() < 1.0e-5)
            push = Vec3.ZERO;
        else if (push.lengthSqr() > 2.25)
            push = push.normalize().scale(1.5);

        // down but no flyer (the Celestial Dragon grounded by wind; a landed Bone Dragon is perched below): it walks
        boolean walking = !landed && mob instanceof Unit u && !u.isFlyingUnit();
        double[] v;
        if (landed) {
            v = DragonFlight.perched(state);
            if (mob.onGround())
                v[1] = 0.0;
        } else if (diveTo != null) {
            AttributeInstance ms = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            double cruise = DragonFlight.cruiseSpeed(ms == null ? 0.0 : ms.getValue());
            v = DragonFlight.dive(state, diveTo.x - mob.getX(), diveTo.y - mob.getY(), diveTo.z - mob.getZ(), cruise);
        } else if (operation == Operation.MOVE_TO) {
            AttributeInstance ms = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            double cruise = DragonFlight.cruiseSpeed(ms == null ? 0.0 : ms.getValue());
            double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.0 && state.speed < 0.02)
                operation = Operation.WAIT;     // there (a take-off's climb has no goal to end it): hover, face targets
            double need = 0.0;
            boolean hover = false;
            if (walking) {
                blocked = false;
                detourTicks = 0;
                heldTop = Double.NEGATIVE_INFINITY;
            } else if (detourTicks > 0) {
                bypassTicks = 0;
                // side-stepping out from under what shut it in: level, along the detour, then back on the route
                if (--detourTicks == 0)
                    sweepIn = 0;                      // look again at once, before it steers back into what shut it in
                double r = Math.toRadians(detourYaw);
                dx = -Math.sin(r) * 8.0;
                dz = Math.cos(r) * 8.0;
                dy = 0.0;
                blocked = false;
            } else {
                double hd = Math.sqrt(dx * dx + dz * dz);
                double ceiling = wantedY + ROUTE_HEADROOM;
                routeTop = Double.NEGATIVE_INFINITY;
                routeNeed = 0.0;
                boolean fits = bypassTicks > 0 || hd < 1.0e-3 || scanRoute(dx / hd, dz / hd, hd, ceiling);
                if (!fits) {
                    // something on the line stands over the ceiling: fly round it, or - no way round - over it
                    if (!findBypass((float) Math.toDegrees(-Math.atan2(dx, dz)), hd, ceiling))
                        scanRoute(dx / hd, dz / hd, hd, Double.POSITIVE_INFINITY);
                }
                if (bypassTicks > 0) {
                    // on the way round: aim along it, read the route along it
                    bypassTicks--;
                    double r = Math.toRadians(bypassYaw), aim = Math.min(hd, BYPASS_AIM);
                    dx = -Math.sin(r) * aim;
                    dz = Math.cos(r) * aim;
                    scanRoute(-Math.sin(r), Math.cos(r), aim, ceiling);
                }
                heldTop = hd > HOLD_UNTIL ? Math.max(routeTop, heldTop - FLOOR_DECAY) : routeTop;
                if (heldTop - mob.getY() > dy)
                    dy = heldTop - mob.getY();        // climb to / hold the height that clears the way
                need = routeNeed;
                if (sweepIn-- <= 0 && dist > 1.0e-4) {
                    sweepIn = SWEEP_EVERY;
                    double r = Math.toRadians(state.heading);
                    // along the way it will fly: the route's climb when it is climbing over something, else the line
                    double rise = need > 0.0
                        ? Mth.clamp(Math.min(DragonFlight.ROUTE_CLIMB_MAX, need * DragonFlight.ROUTE_MARGIN)
                            / Math.max(state.speed, 0.05), 0.0, 2.0)
                        : Mth.clamp(dy / Math.max(1.0, dist), -0.5, 0.5);
                    Vec3 ahead = new Vec3(-Math.sin(r), rise, Math.cos(r)).normalize();
                    int steps = Math.min(MAX_SWEEP, Mth.ceil(dist));
                    blocked = !canReach(ahead, steps);
                    if (blocked && !canReach(new Vec3(0, 1, 0), 2)) {
                        blocked = false;
                        hover = !startDetour((float) Math.toDegrees(-Math.atan2(dx, dz)));
                    }
                }
            }
            v = operation == Operation.MOVE_TO && !hover
                ? DragonFlight.step(state, dx, dy, dz, cruise, blocked, need)
                : DragonFlight.brake(state);
        } else {
            v = DragonFlight.brake(state);
            blocked = false;
        }
        if (walking) {
            // pressed onto the ground every tick (a tick at 0 would leave it off the ground and the push sum wrong)
            state.vy = 0.0;
            v[1] = -0.3;
        }
        lastSet = new Vec3(v[0], v[1], v[2]);
        mob.setDeltaMovement(lastSet.add(push));
    }

    /**
     * Reads the way along (ux, uz) out to {@code reach} blocks flat for what stands on it: the MOTION_BLOCKING
     * heightmap every {@link #ROUTE_STEP} blocks from the nose out to {@link #ROUTE_LOOK} (or reach), across the body's
     * width. Sets routeTop, the feet height that clears all of it up to the ceiling, and routeNeed, the climb (b/t)
     * that clears each such sample in time at the speed it has now (0: nothing on the way is above it). False when a
     * sample stands over the ceiling (it is not counted). An unloaded column reads as the world's floor.
     */
    private boolean scanRoute(double ux, double uz, double reach, double ceiling) {
        routeTop = Double.NEGATIVE_INFINITY;
        routeNeed = 0.0;
        boolean fits = true;
        double half = mob.getBbWidth() * 0.5, y = mob.getY();
        reach = Math.min(ROUTE_LOOK, reach);
        for (double d = half + 1.0; d <= reach; d += ROUTE_STEP) {
            int top = Integer.MIN_VALUE;
            for (int k = -1; k <= 1; k++) {
                double x = mob.getX() + ux * d - uz * half * k, z = mob.getZ() + uz * d + ux * half * k;
                top = Math.max(top, mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)));
            }
            double clear = top + DragonFlight.ROUTE_CLEAR;
            if (clear > ceiling) {
                fits = false;
                continue;
            }
            routeTop = Math.max(routeTop, clear);
            routeNeed = Math.max(routeNeed, DragonFlight.climbNeed(clear - y, d - half, state.speed));
        }
        return fits;
    }

    /**
     * The line to the point (wantYaw, hd flat) is shut above the ceiling: the first way off it, BYPASS_STEP degrees
     * at a time up to BYPASS_MAX on either side (the side it last went round first), whose route fits under the
     * ceiling; flown for {@link #BYPASS_TICKS} before the line is tried again. False when there is none.
     */
    private boolean findBypass(float wantYaw, double hd, double ceiling) {
        for (float off = BYPASS_STEP; off <= BYPASS_MAX; off += BYPASS_STEP)
            for (int side : new int[] { bypassSide, -bypassSide }) {
                float yaw = Mth.wrapDegrees(wantYaw + side * off);
                double r = Math.toRadians(yaw);
                if (scanRoute(-Math.sin(r), Math.cos(r), Math.max(hd, BYPASS_AIM), ceiling)) {
                    bypassYaw = yaw;
                    bypassSide = side;
                    bypassTicks = BYPASS_TICKS;
                    return true;
                }
            }
        return false;
    }

    /**
     * The way ahead and the way up are both shut (an overhang, a gap between walls): side-step for
     * {@link #DETOUR_TICKS} along the first open way round the line to the point (wantYaw) - 45, 90, then 135 degrees
     * off it, the side it last stepped to first. False when every way is shut: it hovers where it is.
     */
    private boolean startDetour(float wantYaw) {
        for (float off : new float[] { 45f, 90f, 135f })
            for (int side : new int[] { detourSide, -detourSide }) {
                float yaw = Mth.wrapDegrees(wantYaw + side * off);
                double r = Math.toRadians(yaw);
                if (canReach(new Vec3(-Math.sin(r), 0, Math.cos(r)), 4)) {
                    detourYaw = yaw;
                    detourSide = side;
                    detourTicks = DETOUR_TICKS;
                    return true;
                }
            }
        return false;
    }
}
