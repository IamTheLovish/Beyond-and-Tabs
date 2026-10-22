package com.solegendary.reignofnether.unit.support;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.hero.PossessionServer;
import com.solegendary.reignofnether.mana.SealServerEvents;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.Engage;
import com.solegendary.reignofnether.unit.GuardServerEvents;
import com.solegendary.reignofnether.unit.PatrolServerEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.Terrain;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.FlyingUsePortalGoal;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import com.solegendary.reignofnether.unit.goals.UsePortalGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.SupportUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import com.solegendary.reignofnether.unit.support.SupportState.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.unit.support.SupportField.*;

/**
 * The shared brain of every support unit (claude/healer-ai-oct21.md; lovish, Oct 21 20:52: "healers need to be
 * smarter"). Each support unit looks round itself every {@link #THINK_TICKS} ticks (staggered by entity id) with one
 * UnitGrid query flattened into a {@link SupportField}, and in that one look it
 * <ol>
 *   <li>steps out of danger: a melee enemy within {@link #DANGER_RADIUS} blocks, or an enemy within
 *       {@link #AIMED_RADIUS} that has it as its target, sends it {@link #KITE_STEP} blocks away from them and toward its own army (at most once per
 *       {@link #KITE_COOLDOWN} ticks; never on hold position, garrisoned, riding, or off a plain move the player
 *       gave - a player's move is the player's);</li>
 *   <li>carries out its support order (SupportOrders gave it): behind the fighter it escorts, at throw range from the
 *       unit it was told to attack, behind the front of a Fight, or outside an attacked building's guns - remembering
 *       the spot, so after a throw pulled it forward it goes back;</li>
 *   <li>lets the unit decide its own casts on the same look (the Field Medic's potions: MedicPotions).</li>
 * </ol>
 * Its support itself keeps running while it moves: {@link #canAutocastNow} replaces the old "only when fully idle"
 * gate. Cost: one grid box of radius reach + {@link #SCAN_EXTRA} per support unit every 4 ticks, at most 64 entries,
 * candidates capped at 12 (SupportMath); moves are re-pathed only when the spot moved, and at most every
 * {@link #REPATH_TICKS} ticks. Timed under the profiler's REACTIONS section. Server thread only.
 */
public final class SupportBrain {
    private SupportBrain() { }

    /** One look every this many ticks per support unit (staggered by id). */
    public static final int THINK_TICKS = 4;
    /** A melee enemy this close sends a support unit a step away. */
    public static final double DANGER_RADIUS = 4.0;
    /** An enemy that has it as its target counts from this close (an archer further off is the army's job: a healer
     * that ran from every bow in range would never heal). */
    public static final double AIMED_RADIUS = 10.0;
    /** A standoff spot is kept at least this far from any enemy melee unit. */
    public static final double CLEAR_OF_MELEE = DANGER_RADIUS + 1.5;
    /** The danger step: this long, at most once per KITE_COOLDOWN ticks (1.5 s), and the brain leaves it alone for KITE_TICKS. */
    public static final double KITE_STEP = 3.5;
    public static final int KITE_COOLDOWN = 30;
    public static final int KITE_TICKS = 20;
    /** A cornered unit (no dry cell to step to) looks again this much sooner. */
    static final int CORNERED_RETRY = 10;
    /** The standoff behind a fight is the unit's reach less this (blocks): its support just reaches the front. */
    public static final double STANDOFF_MARGIN = 1.5;
    /** Walking with an escorted fighter that is not fighting: this far behind it. */
    public static final double TRAIL = 3.0;
    /** The look reaches this much past the unit's own reach. */
    public static final double SCAN_EXTRA = 8.0;
    /** A spot this close counts as reached. */
    public static final double ARRIVE = 1.5;
    /** A move is re-issued only when the spot moved this much, and at most every REPATH_TICKS ticks. */
    public static final double REPATH_DIST = 1.5;
    public static final int REPATH_TICKS = 10;
    /** An attack-order target this far away is given up (it ran; the support unit does not chase it across the map). */
    static final double LEASH = 48;
    /** Only units within this height of the looker count (a tower's garrison, a cliff top). */
    static final double MAX_DY = 8;
    /** An idle medic that walked further than this for a throw walks back to its post. */
    static final double HOME_DRIFT = 2.0;

    static final SupportField FIELD = new SupportField(64);
    private static final SupportField CHECK = new SupportField(64);
    private static final List<LivingEntity> SCRATCH = new ArrayList<>();
    private static final double[] P = new double[3], Q = new double[2];
    private static final double[] STEP_ANGLES = { 0, 0.7, -0.7, 1.4, -1.4 };   // radians: ahead, ~40 and ~80 degrees aside
    private static final double[] STEP_SCALES = { 1.0, 0.6 };

    /** Server side, every tick, from the unit's own tick (after Unit.tick). */
    public static void tick(Mob mob, SupportUnit unit) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive())
            return;
        if ((mob.tickCount + mob.getId()) % THINK_TICKS != 0)
            return;
        int sys = Sys.begin(Sys.REACTIONS);
        try {
            think(level, mob, unit);
        } finally {
            Sys.end(sys);
        }
    }

    static void think(ServerLevel level, Mob mob, SupportUnit unit) {
        // a possessed unit goes where its player walks it; a stunned one does nothing
        if (PossessionServer.isPossessed(mob) || mob.hasEffect(MobEffectRegistrar.STUN.get())
                || mob.hasEffect(MobEffectRegistrar.FREEZE.get()) || mob.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get()))
            return;
        SupportState s = unit.getSupportState();
        long now = level.getGameTime();
        validate(mob, unit, s);
        double reach = unit.getSupportReach();
        scan(FIELD, level, mob, unit, mob.getX(), mob.getZ(), reach + SCAN_EXTRA);
        try {
            if (!kite(level, mob, unit, s, now) && now >= s.kiteUntil && !unit.isSupportCasting()) {
                if (s.mode != Mode.NONE)
                    steer(level, mob, unit, s, now, reach);
                else if (s.homing)
                    home(level, mob, unit, s, now);
            }
            unit.supportThink(level, FIELD, now);
        } finally {
            FIELD.clear();
        }
    }

    // ------------------------------------------------------------------ the look

    /**
     * Fills {@code f} with the live units within {@code r} of (cx, cz) as {@code unit} sees them: friends (its own and
     * allies'), visible enemies; neutrals and garrisoned units left out. The looker itself goes first, so a full field
     * never loses it.
     */
    static void scan(SupportField f, ServerLevel level, Mob mob, SupportUnit unit, double cx, double cz, double r) {
        f.clear();
        add(f, mob, unit, F_FRIEND | F_SELF | F_SUPPORT, mob);
        String owner = unit.getOwnerName();
        double r2 = r * r;
        for (LivingEntity le : UnitGrid.near(level, cx, cz, r, SCRATCH)) {
            if (le == mob || !le.isAlive() || !(le instanceof Unit u) || u.isGarrisoned())
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz;
            if (dx * dx + dz * dz > r2 || Math.abs(le.getY() - mob.getY()) > MAX_DY)
                continue;
            Relationship rel = UnitServerEvents.getUnitToEntityRelationship(unit, le);
            int flags;
            if (rel == Relationship.FRIENDLY || rel == Relationship.OWNED)
                flags = F_FRIEND;
            else if (rel == Relationship.HOSTILE && !Concealment.isHiddenFrom(le, owner))
                flags = F_ENEMY;
            else
                continue;
            if (le instanceof SupportUnit)
                flags |= F_SUPPORT;
            if (add(f, le, u, flags, mob) < 0)
                break;   // full: a crowd this size already decides everything the look needs
        }
        SCRATCH.clear();
    }

    private static int add(SupportField f, LivingEntity le, Unit u, int flags, Mob looker) {
        if (le.getMobType() == MobType.UNDEAD)
            flags |= F_UNDEAD;
        if (le instanceof AttackerUnit) {
            flags |= F_ATTACKER;
            if (!(le instanceof RangedAttackerUnit) && !(le instanceof WorkerUnit))
                flags |= F_MELEE;
        }
        if (le != looker && le instanceof Mob m && m.getTarget() == looker)
            flags |= F_TARGETS_ME;
        if (le.isOnFire())
            flags |= F_BURNING;
        Vec3 v = le.getDeltaMovement();
        return f.add(le, le.getX(), le.getZ(), v.x, v.z, le.getHealth(), le.getMaxHealth(), valueOf(u), flags);
    }

    /** What a unit is worth to save or to hit: 1 + its cost / 300 (capped at +4), +1 for a hero. */
    static float valueOf(Unit u) {
        ResourceCost c = u.getCost();
        float v = c == null ? 0f : (c.metal() + c.energy()) / 300f;
        if (u instanceof HeroUnit)
            v += 1f;
        return 1f + Math.min(4f, Math.max(0f, v));
    }

    // ------------------------------------------------------------------ the support order

    /** Ends a support order whose target is gone; an escort that lost its fighter holds the standoff instead. */
    static void validate(Mob mob, SupportUnit unit, SupportState s) {
        switch (s.mode) {
            case ESCORT -> {
                if (gone(s.orderTarget, unit, mob)) {
                    s.endOrder();
                } else if (s.anchor == null || !s.anchor.isAlive() || s.anchor.isRemoved()) {
                    s.anchor = nearestAlive(mob, s.escortIds);
                    if (s.anchor == null)
                        s.mode = Mode.STANDOFF_UNIT;
                }
            }
            case STANDOFF_UNIT -> {
                if (gone(s.orderTarget, unit, mob))
                    s.endOrder();
            }
            case STANDOFF_BUILDING -> {
                BuildingPlacement b = s.buildingPos == null ? null : BuildingUtils.findBuilding(false, s.buildingPos);
                if (b == null || b.isDestroyedServerside)
                    s.endOrder();
            }
            default -> { }
        }
    }

    static boolean gone(@Nullable LivingEntity t, SupportUnit unit, Mob mob) {
        return t == null || !t.isAlive() || t.isRemoved() || Concealment.isHiddenFrom(t, unit.getOwnerName())
            || t.distanceToSqr(mob) > LEASH * LEASH;
    }

    @Nullable
    static LivingEntity nearestAlive(Mob mob, int[] ids) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (int id : ids) {
            Entity e = mob.level().getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved()) {
                double d = le.distanceToSqr(mob);
                if (d < bestD) {
                    bestD = d;
                    best = le;
                }
            }
        }
        return best;
    }

    /** Moves the unit toward where its support order wants it (P), clear of enemy melee. */
    static void steer(ServerLevel level, Mob mob, SupportUnit unit, SupportState s, long now, double reach) {
        boolean move = switch (s.mode) {
            case ESCORT -> escortSpot(mob, s, reach);
            case STANDOFF_UNIT -> targetSpot(mob, unit, s, reach);
            case SUPPORT_MOVE -> fightSpot(mob, s, reach);
            case STANDOFF_BUILDING -> buildingSpot(mob, s, reach);
            default -> false;
        };
        if (!move)
            return;
        SupportMath.keepClear(FIELD, CLEAR_OF_MELEE, P);
        goTo(level, mob, unit, s, now, P[0], P[2], P[1]);
    }

    /** Behind the escorted fighter, on the far side from what it fights; trailing it when it only walks. */
    static boolean escortSpot(Mob mob, SupportState s, double reach) {
        LivingEntity a = s.anchor;
        if (a == null)
            return false;
        double ax = a.getX(), az = a.getZ();
        // fighting = its target (or any enemy) is close to it; on the way there it only walks, and the healer trails
        double engaged = SupportMath.ENGAGE_RADIUS + 2;
        LivingEntity foe = a instanceof Mob am ? am.getTarget() : null;
        boolean fighting = false;
        double ex = 0, ez = 0;
        if (foe != null && foe.isAlive() && foe.distanceToSqr(a) < engaged * engaged) {
            ex = foe.getX();
            ez = foe.getZ();
            fighting = true;
        } else if (SupportMath.centroid(FIELD, F_ENEMY, 0, ax, az, engaged, Q) > 0) {
            ex = Q[0];
            ez = Q[1];
            fighting = true;
        }
        P[2] = a.getY();
        if (fighting) {
            SupportMath.behind(ax, az, ex, ez, reach - STANDOFF_MARGIN, mob.getX(), mob.getZ(), P);
            return stand(mob, s);
        }
        double dx = mob.getX() - ax, dz = mob.getZ() - az, d = Math.sqrt(dx * dx + dz * dz);
        if (d <= TRAIL + ARRIVE)
            return false;
        P[0] = ax + dx / d * TRAIL;
        P[1] = az + dz / d * TRAIL;
        return true;
    }

    /** At throw range (or out of melee reach, for a unit without a throw) from the unit it was told to attack. */
    static boolean targetSpot(Mob mob, SupportUnit unit, SupportState s, double reach) {
        LivingEntity t = s.orderTarget;
        if (t == null)
            return false;
        double want = Math.max(reach - 1.0, CLEAR_OF_MELEE + 1.0);
        double dx = mob.getX() - t.getX(), dz = mob.getZ() - t.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        P[2] = t.getY();
        if (Math.abs(d - want) <= ARRIVE) {
            remember(s, mob.getX(), mob.getY(), mob.getZ());
            return false;
        }
        SupportMath.onCircle(t.getX(), t.getZ(), want, mob.getX(), mob.getZ(), P);
        remember(s, P[0], P[2], P[1]);
        return true;
    }

    /** Behind our front while it fights; else on to the rear-rank spot - where the Fight order ends. */
    static boolean fightSpot(Mob mob, SupportState s, double reach) {
        if (behindFront(mob, s, reach))
            return stand(mob, s);
        BlockPos d = s.destination;
        if (d == null) {
            s.endOrder();
            return false;
        }
        double dx = d.getX() + 0.5 - mob.getX(), dz = d.getZ() + 0.5 - mob.getZ();
        if (dx * dx + dz * dz <= (ARRIVE + 1) * (ARRIVE + 1)) {
            s.endOrder();   // arrived with no fight round it: the order is done, the unit is idle again
            return false;
        }
        P[0] = d.getX() + 0.5;
        P[1] = d.getZ() + 0.5;
        P[2] = d.getY();
        return true;
    }

    /** Outside the attacked building's guns: behind the front if there is a fight, else on the keep-out circle. */
    static boolean buildingSpot(Mob mob, SupportState s, double reach) {
        if (behindFront(mob, s, reach)) {
            double dx = P[0] - s.buildingX, dz = P[1] - s.buildingZ;
            if (dx * dx + dz * dz < s.keepOut * s.keepOut)
                SupportMath.onCircle(s.buildingX, s.buildingZ, s.keepOut, P[0], P[1], P);
            return stand(mob, s);
        }
        double dx = mob.getX() - s.buildingX, dz = mob.getZ() - s.buildingZ, d = Math.sqrt(dx * dx + dz * dz);
        P[2] = mob.getY();
        if (Math.abs(d - s.keepOut) <= ARRIVE)
            return false;
        SupportMath.onCircle(s.buildingX, s.buildingZ, s.keepOut, mob.getX(), mob.getZ(), P);
        return true;
    }

    /** The standoff behind our front (SupportMath.front) into P; false when nobody near is in a fight. */
    static boolean behindFront(Mob mob, SupportState s, double reach) {
        int fr = SupportMath.front(FIELD, mob.getX(), mob.getZ(), reach + SCAN_EXTRA);
        if (fr < 0)
            return false;
        SupportMath.centroid(FIELD, F_ENEMY, 0, FIELD.x[fr], FIELD.z[fr], SupportMath.ENGAGE_RADIUS, Q);
        SupportMath.behind(FIELD.x[fr], FIELD.z[fr], Q[0], Q[1], reach - STANDOFF_MARGIN, mob.getX(), mob.getZ(), P);
        P[2] = ((Entity) FIELD.ref[fr]).getY();
        return true;
    }

    /** Remembers P as the standoff and says whether the unit still has to walk there. */
    private static boolean stand(Mob mob, SupportState s) {
        remember(s, P[0], P[2], P[1]);
        double dx = P[0] - mob.getX(), dz = P[1] - mob.getZ();
        return dx * dx + dz * dz > ARRIVE * ARRIVE;
    }

    private static void remember(SupportState s, double x, double y, double z) {
        s.hasStand = true;
        s.standX = x;
        s.standY = y;
        s.standZ = z;
    }

    /** Gives the move goal the spot, unless it is already heading there or was given a spot a moment ago. */
    static void goTo(ServerLevel level, Mob mob, SupportUnit unit, SupportState s, long now, double x, double y, double z) {
        MoveToTargetBlockGoal goal = unit.getMoveGoal();
        if (goal == null)
            return;
        BlockPos raw = BlockPos.containing(x, y + 0.01, z);
        BlockPos cell = Engage.standCell(level, raw, MobilityClass.of(unit));
        if (cell == null)
            cell = raw;   // no standable cell right there: the pathfinder snaps the goal itself
        BlockPos current = goal.getMoveTarget();
        if (current != null && current.distSqr(cell) <= REPATH_DIST * REPATH_DIST)
            return;
        if (current != null && now - s.lastIssueTick < REPATH_TICKS)
            return;
        goal.setMoveTarget(cell);
        s.lastIssued = cell;
        s.lastIssueTick = now;
    }

    /** The walk back to its post after a throw pulled an idle medic away (standoff memory for an idle unit). */
    static void home(ServerLevel level, Mob mob, SupportUnit unit, SupportState s, long now) {
        double dx = s.homeX - mob.getX(), dz = s.homeZ - mob.getZ();
        if (dx * dx + dz * dz <= ARRIVE * ARRIVE) {
            s.homing = false;
            return;
        }
        goTo(level, mob, unit, s, now, s.homeX, s.homeY, s.homeZ);
    }

    // ------------------------------------------------------------------ danger

    /** The danger step (see the class comment). True if it stepped. */
    static boolean kite(ServerLevel level, Mob mob, SupportUnit unit, SupportState s, long now) {
        if (now - s.lastKite < KITE_COOLDOWN)
            return false;
        if (unit.getHoldPosition() || mob.isPassenger() || unit.isGarrisoned())
            return false;
        // the player's own move order (no support order, not following, patrolling or guarding) is left alone
        if (s.mode == Mode.NONE && !s.homing && unit.getFollowTarget() == null && unit.getMoveGoal() != null
                && unit.getMoveGoal().getMoveTarget() != null
                && !PatrolServerEvents.isPatrolling(mob.getId()) && !GuardServerEvents.isGuarding(mob.getId()))
            return false;
        if (!SupportMath.fleeDirection(FIELD, mob.getX(), mob.getZ(), DANGER_RADIUS, AIMED_RADIUS, Q))
            return false;
        BlockPos cell = stepCell(level, mob, unit, Q[0], Q[1]);
        if (cell == null) {
            s.lastKite = now - KITE_COOLDOWN + CORNERED_RETRY;   // cornered: stand, look again shortly
            return false;
        }
        unit.cancelSupportCast();
        unit.getMoveGoal().setMoveTarget(cell);
        s.lastIssued = cell;
        s.lastIssueTick = now;
        s.kiteUntil = now + KITE_TICKS;
        s.lastKite = now;
        s.kites++;
        unit.onSupportStep();
        return true;
    }

    /**
     * A dry, standable cell {@link #KITE_STEP} blocks along (dx, dz) - or turned up to ~80 degrees either side, or
     * shorter - that is not where it stands and not next to another enemy melee unit; null when cornered.
     */
    @Nullable
    static BlockPos stepCell(ServerLevel level, Mob mob, Unit unit, double dx, double dz) {
        MobilityClass mobility = MobilityClass.of(unit);
        BlockPos here = mob.blockPosition();
        for (double scale : STEP_SCALES)
            for (double a : STEP_ANGLES) {
                double c = Math.cos(a), sn = Math.sin(a);
                double rx = dx * c - dz * sn, rz = dx * sn + dz * c;
                double len = KITE_STEP * scale;
                double x = mob.getX() + rx * len, z = mob.getZ() + rz * len;
                BlockPos cell = Engage.standCell(level, BlockPos.containing(x, mob.getY() + 0.01, z), mobility);
                if (cell == null || cell.equals(here) || !Terrain.isLand(level, cell))
                    continue;
                if (SupportMath.countWithin(FIELD, F_ENEMY | F_MELEE, 0, cell.getX() + 0.5, cell.getZ() + 0.5,
                        DANGER_RADIUS - 1.0, false) > 0)
                    continue;
                return cell;
            }
        return null;
    }

    // ------------------------------------------------------------------ for the unit's own casts

    /**
     * May the unit cast its support by itself now? The old gate was "fully idle", so a healer on the move, following a
     * friend or holding a Fight standoff never healed (healer-ai root cause 2). Now: yes when idle, under a support
     * order or walking back to its post, following, guarding, patrolling or on hold position; no while it walks into a
     * garrison or a portal, casts something else, is sealed, takes a danger step, or walks a plain move order (a
     * retreat should not detour to a heal).
     */
    public static boolean canAutocastNow(Mob mob, SupportUnit unit) {
        SupportState s = unit.getSupportState();
        if (mob.level().getGameTime() < s.kiteUntil)
            return false;
        if (unit.isCasting() || SealServerEvents.isSealed(mob))
            return false;
        if (unit.getGarrisonGoal() != null && unit.getGarrisonGoal().getBuildingTarget() != null)
            return false;
        MoveToTargetBlockGoal portal = unit.getUsePortalGoal();
        if (portal instanceof UsePortalGoal p && p.getBuildingTarget() != null)
            return false;
        if (portal instanceof FlyingUsePortalGoal p && p.getBuildingTarget() != null)
            return false;
        if (s.mode != Mode.NONE || s.homing)
            return true;
        if (unit.getFollowTarget() != null || unit.getHoldPosition())
            return true;
        if (PatrolServerEvents.isPatrolling(mob.getId()) || GuardServerEvents.isGuarding(mob.getId()))
            return true;
        return unit.isIdle();
    }

    /**
     * A fresh check at the moment of release (the wind-up gave a friend time to walk in): would a harming cloud at
     * (x, z) touch a friend it hurts, now or in about a second? Its own small grid query.
     */
    public static boolean harmSafeAt(ServerLevel level, Mob thrower, SupportUnit unit, double x, double z) {
        scan(CHECK, level, thrower, unit, x, z, SupportMath.CLOUD_RADIUS + SupportMath.HARM_SAFETY + SupportField.MAX_LOOKAHEAD);
        // the thrower went in first wherever it stands: it counts only if it is near the cloud
        boolean safe = SupportMath.harmSafeAt(CHECK, x, z);
        CHECK.clear();
        return safe;
    }

    /**
     * A throw was released (ThrowPotionGoal): the counters the game tests read, and an idle medic that walked away
     * for it heads back to its post.
     */
    public static void onThrow(Mob mob, SupportUnit unit, Kind kind, boolean selfDecided, double x, double z) {
        SupportState s = unit.getSupportState();
        s.lastThrowX = x;
        s.lastThrowZ = z;
        switch (kind) {
            case HEAL -> s.heals++;
            case REGEN -> s.regens++;
            case HARM -> s.harms++;
            case WATER -> s.waters++;
            default -> { }
        }
        boolean ordered = s.mode != Mode.NONE || s.homing || unit.getFollowTarget() != null
            || PatrolServerEvents.isPatrolling(mob.getId()) || GuardServerEvents.isGuarding(mob.getId());
        if (ordered && (kind == Kind.HEAL || kind == Kind.REGEN))
            s.healsWhileOrdered++;
        if (s.homeArmed) {
            s.homeArmed = false;
            double dx = s.homeX - mob.getX(), dz = s.homeZ - mob.getZ();
            if (selfDecided && s.mode == Mode.NONE && dx * dx + dz * dz > HOME_DRIFT * HOME_DRIFT)
                s.homing = true;
        }
    }

    /** An idle medic about to walk for a throw remembers where it stood (SupportBrain.onThrow sends it back). */
    static void armHome(Mob mob, SupportUnit unit) {
        SupportState s = unit.getSupportState();
        if (s.mode != Mode.NONE || s.homing || !unit.isIdle())
            return;
        s.homeArmed = true;
        s.homeX = mob.getX();
        s.homeY = mob.getY();
        s.homeZ = mob.getZ();
    }

    /** What a released potion was, for the counters. */
    public enum Kind { HEAL, REGEN, HARM, WATER, OTHER }
}
