package com.solegendary.reignofnether.unit.controls;

import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
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
 * It climbs at most {@link #ROUTE_CEILING} over the ground right under it (lovish: "increase max flying high by a bit
 * then go around if it cant fly over that new max", then "Ground under the dragon"), never past the flyingMaxYLevel
 * rule: what stands taller it flies round ({@link #findBypass}), and only with no way round does it go over (still under
 * the rule). The ground rising - a hill, a mountain - is not "taller" (lovish: "can be higher if it needs to fly over
 * hills or mountains"): each sample ahead is measured against the lowest ground within {@link #HILL_SPAN} blocks of it
 * along the way, so a slope it can climb along is flown over however high it goes, and only what stands more than the
 * ceiling over its own surroundings (a tower, a keep, a cliff) is gone round. The route is read every
 * {@link #ROUTE_EVERY} ticks (~165 heightmap reads, ~41 a tick; a search for a way round reads ~680 more, at most every
 * {@link #ROUTE_EVERY} ticks while one is needed and every {@link #NO_WAY_TICKS} once none was found); between reads the climb it needs is worked out again each tick from the stored samples. Boxed
 * in with no way up, it side-steps ({@link #DETOUR_TICKS}) instead of waiting.
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
    /** The route ahead: how far ahead it reads (blocks) and the spacing of its samples along the way - every block on
     *  the way it flies (a one-block wall is not stepped over), every other on a way round it only weighs. */
    static final double ROUTE_LOOK = 32.0, ROUTE_STEP = 1.0, SEARCH_STEP = 2.0;
    /** The held route height sinks this much a tick once what was ahead is behind it (it does not dip between roofs),
     *  and is dropped this close to the point (blocks, flat): it comes down onto its point. */
    static final double FLOOR_DECAY = 0.06, HOLD_UNTIL = 12.0;
    /** A side-step's length (ticks) when the way ahead and the way up are both shut, and the most it is drawn out
     *  while something still covers its back. */
    static final int DETOUR_TICKS = 24, DETOUR_MAX = 160;
    /** How high over the ground right under it (its column's MOTION_BLOCKING height) it may fly to cross something;
     *  taller, it flies round. */
    public static final double ROUTE_CEILING = 16.0;
    /** Ticks between reads of the route, and how long it goes over (not round) once no way round was found. */
    static final int ROUTE_EVERY = 4, NO_WAY_TICKS = 16;
    /** How far along the way (blocks, either side) a sample's own ground is looked for: the lowest top that close. */
    static final double HILL_SPAN = 8.0;
    /** A way round: how long one is flown before the straight line is tried again (ticks), how far off the line it
     *  may turn (degrees, in steps of BYPASS_STEP), and how far along it it aims (blocks). */
    static final int BYPASS_TICKS = 8;
    static final float BYPASS_STEP = 20f, BYPASS_MAX = 80f;
    static final double BYPASS_AIM = 16.0;
    private double routeTop = Double.NEGATIVE_INFINITY, heldTop = Double.NEGATIVE_INFINITY;
    /** The height the feet may climb to now: the ceiling, or the rule's cap with no way round. */
    private double climbCap = Double.POSITIVE_INFINITY;
    private float bypassYaw = 0f;
    private int bypassTicks = 0, bypassSide = 1, routeIn = 0, noWayTicks = 0;
    /** The last read's samples (their clearing heights and their distances along the read from where it was made). */
    private final double[] sampleClear = new double[(int) (ROUTE_LOOK / ROUTE_STEP) + 2];
    private final double[] sampleDist = new double[sampleClear.length];
    private final int[] readTop = new int[sampleClear.length];
    private final double[] readDist = new double[sampleClear.length];
    private int samples = 0, groundUnder = 0;
    private double scanX, scanZ, scanUx, scanUz, maxFeet = Double.POSITIVE_INFINITY;
    private float detourYaw = 0f;
    private int detourTicks = 0, detourSide = 1, detourTotal = 0;
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
                // side-stepping out from under what shut it in: level, along the detour, then back on the route -
                // not while something still covers its back (a porch roof: back on the route it would only climb
                // into it again), up to DETOUR_MAX ticks in all
                if (--detourTicks == 0 && underCover() && (detourTotal += DETOUR_TICKS) < DETOUR_MAX)
                    detourTicks = DETOUR_TICKS;
                if (detourTicks == 0) {
                    sweepIn = 0;                      // look again at once, before it steers back into what shut it in
                    routeIn = 0;
                    detourTotal = 0;
                }
                double r = Math.toRadians(detourYaw);
                dx = -Math.sin(r) * 8.0;
                dz = Math.cos(r) * 8.0;
                dy = 0.0;
                blocked = false;
            } else if (underCover() && startDetour((float) Math.toDegrees(-Math.atan2(dx, dz)))) {
                // something over its back (a roof): no route reads here - the ground under it is the roof - out first
                bypassTicks = 0;
                double r = Math.toRadians(detourYaw);
                dx = -Math.sin(r) * 8.0;
                dz = Math.cos(r) * 8.0;
                dy = 0.0;
                blocked = false;
            } else {
                double hd = Math.sqrt(dx * dx + dz * dz);
                if (noWayTicks > 0)
                    noWayTicks--;
                if (routeIn-- <= 0) {
                    routeIn = ROUTE_EVERY - 1;
                    planRoute(dx, dz, hd);
                }
                if (bypassTicks > 0) {
                    // on the way round: aim along it
                    bypassTicks--;
                    double r = Math.toRadians(bypassYaw), aim = Math.min(hd, BYPASS_AIM);
                    dx = -Math.sin(r) * aim;
                    dz = Math.cos(r) * aim;
                }
                double top = Math.min(routeTop, climbCap);
                heldTop = Math.min(climbCap, hd > HOLD_UNTIL ? Math.max(top, heldTop - FLOOR_DECAY) : top);
                if (heldTop - mob.getY() > dy)
                    dy = heldTop - mob.getY();        // climb to / hold the height that clears the way
                need = routeNeedNow();
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
                    // shut ahead and no way up - blocks over it, or held at its ceiling (the hard stop below): it
                    // would only press on at the blocked crawl, so it side-steps and reads the route again after
                    if (blocked && (!canReach(new Vec3(0, 1, 0), 2) || mob.getY() + 0.05 >= climbCap)) {
                        blocked = false;
                        hover = !startDetour((float) Math.toDegrees(-Math.atan2(dx, dz)));
                    }
                }
            }
            v = operation == Operation.MOVE_TO && !hover
                ? DragonFlight.step(state, dx, dy, dz, cruise, blocked, need)
                : DragonFlight.brake(state);
            if (!walking && v[1] > 0.0 && mob.getY() + v[1] > climbCap) {
                // never past the ceiling (DragonFlight's climb already eases out under it; this is the hard stop)
                v[1] = Math.max(0.0, climbCap - mob.getY());
                state.vy = v[1];
            }
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
     * Reads the route (every ROUTE_EVERY ticks): the ceiling - ROUTE_CEILING over the ground right under it, at least
     * its flight point's height, never past the flyingMaxYLevel rule - then the way to the point (or along the way round
     * it is flying); a way over the ceiling is gone round ({@link #findBypass}) or, with none, flown over under the rule.
     */
    private void planRoute(double dx, double dz, double hd) {
        maxFeet = mob.level().getGameRules().getRule(GameRuleRegistrar.FLYING_MAX_Y_LEVEL).get();
        groundUnder = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(mob.getX()), Mth.floor(mob.getZ()));
        double ceiling = Math.min(maxFeet, Math.max(groundUnder + ROUTE_CEILING, wantedY));
        climbCap = ceiling;
        if (hd < 1.0e-3) {
            samples = 0;
            routeTop = Double.NEGATIVE_INFINITY;
            bypassTicks = 0;
            return;
        }
        if (bypassTicks > 0) {
            double r = Math.toRadians(bypassYaw);
            if (scanRoute(-Math.sin(r), Math.cos(r), Math.min(hd, BYPASS_AIM), ceiling, ROUTE_STEP)) {
                climbCap = Math.min(maxFeet, Math.max(ceiling, routeTop));
                return;
            }
            bypassTicks = 0;                          // the way round shut: read the line again
        }
        if (scanRoute(dx / hd, dz / hd, hd, ceiling, ROUTE_STEP)) {
            climbCap = Math.min(maxFeet, Math.max(ceiling, routeTop));    // over a hill: as high as it needs
            return;
        }
        // something on the line stands over the ceiling: fly round it, or - no way round - over it, under the rule
        if (noWayTicks <= 0 && findBypass((float) Math.toDegrees(-Math.atan2(dx, dz)), hd, ceiling)) {
            climbCap = Math.min(maxFeet, Math.max(ceiling, routeTop));
            return;
        }
        if (noWayTicks <= 0)
            noWayTicks = NO_WAY_TICKS;
        scanRoute(dx / hd, dz / hd, hd, maxFeet, ROUTE_STEP);
        climbCap = maxFeet;
    }

    /**
     * Reads the way along (ux, uz) out to {@code reach} blocks flat for what stands on it: the MOTION_BLOCKING
     * heightmap every {@code step} blocks from the nose out to {@link #ROUTE_LOOK} (or reach), across the body's
     * width. A sample fits when its clearing height is under the rule's cap and under the ceiling - the one over the
     * ground under the dragon, or ROUTE_CEILING over the sample's own ground (the lowest top within HILL_SPAN of it
     * along the way, the ground under the dragon too): a hill is climbed over, a tower is not. Keeps each sample that
     * fits for {@link #routeNeedNow} and sets routeTop, the feet height that clears them all; false when one does not
     * fit. An unloaded column reads as the world's floor.
     */
    private boolean scanRoute(double ux, double uz, double reach, double ceiling, double step) {
        routeTop = Double.NEGATIVE_INFINITY;
        samples = 0;
        scanX = mob.getX();
        scanZ = mob.getZ();
        scanUx = ux;
        scanUz = uz;
        double half = mob.getBbWidth() * 0.5, side = half + 1.0;
        reach = Math.min(ROUTE_LOOK, reach);
        int n = 0;
        // from beside the body (its flanks pass whatever is there) out; across the body and a block to spare each side
        for (double d = 0.0; d <= reach && n < readTop.length; d += step) {
            int top = Integer.MIN_VALUE;
            for (int k = -2; k <= 2; k++) {
                if (d < half + 1.0 && k == 0)
                    continue;                       // the column under its own middle is the ground it flies over
                double x = scanX + ux * d - uz * side * k * 0.5, z = scanZ + uz * d + ux * side * k * 0.5;
                top = Math.max(top, mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)));
            }
            readTop[n] = top;
            readDist[n++] = Math.max(d, half + 0.5);   // beside it: a top there needs the climb at once
        }
        boolean fits = true;
        for (int i = 0; i < n; i++) {
            int ground = readDist[i] <= HILL_SPAN ? groundUnder : Integer.MAX_VALUE;
            for (int j = 0; j < n; j++)
                if (Math.abs(readDist[j] - readDist[i]) <= HILL_SPAN)
                    ground = Math.min(ground, readTop[j]);
            double clear = readTop[i] + DragonFlight.ROUTE_CLEAR;
            if (clear > maxFeet || clear > Math.max(ceiling, ground + ROUTE_CEILING)) {
                fits = false;
                continue;
            }
            routeTop = Math.max(routeTop, clear);
            sampleClear[samples] = clear;
            sampleDist[samples++] = readDist[i];
        }
        return fits;
    }

    /** The climb (b/t) the stored samples need now, from where it is and the speed it has (0: none above it). */
    private double routeNeedNow() {
        double progress = (mob.getX() - scanX) * scanUx + (mob.getZ() - scanZ) * scanUz;
        double half = mob.getBbWidth() * 0.5, y = mob.getY(), need = 0.0;
        for (int i = 0; i < samples; i++) {
            double d = sampleDist[i] - progress;
            if (d > half)                           // still ahead of the nose
                need = Math.max(need, DragonFlight.climbNeed(sampleClear[i] - y, d - half, state.speed));
        }
        return need;
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
                if (scanRoute(-Math.sin(r), Math.cos(r), Math.max(hd, BYPASS_AIM), ceiling, SEARCH_STEP)) {
                    bypassYaw = yaw;
                    bypassSide = side;
                    bypassTicks = BYPASS_TICKS;
                    return true;
                }
            }
        return false;
    }

    /** Something stands over its back: its column's top is above its head (a roof, an overhang, a bridge). */
    private boolean underCover() {
        int top = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(mob.getX()), Mth.floor(mob.getZ()));
        return top > mob.getY() + mob.getBbHeight();
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
