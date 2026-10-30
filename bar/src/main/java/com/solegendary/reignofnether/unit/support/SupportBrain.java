package com.solegendary.reignofnether.unit.support;

import com.solegendary.reignofnether.ability.AutoCaster;
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
import com.solegendary.reignofnether.unit.support.SupportMath.Undead;
import com.solegendary.reignofnether.unit.support.SupportState.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
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
 *       {@link #AIMED_RADIUS} that has it as its target, sends it {@link #KITE_STEP} blocks away from them and toward
 *       its own army (at most once per {@link #KITE_COOLDOWN} ticks; never on hold position, garrisoned, riding, or
 *       off a plain move the player gave - a player's move is the player's), after one autocast look (a caster
 *       spends its spell before it walks off);</li>
 *   <li>carries out its support order (SupportOrders gave it): behind the fighter it escorts, at cast range from the
 *       unit it was told to attack, behind the front of a Fight, or outside an attacked building's guns - remembering
 *       the spot, so after a cast pulled it forward it goes back. On the way it never leads (lovish, Oct 30: "stay
 *       behind units"): a Fight or an escort keeps its standoff behind our fighter nearest a seen enemy, and a Fight's
 *       march no further on than its head less the standoff. Idle, it holds the standoff behind our front whenever
 *       that front fights within its look (never off a player's move, a follow, hold position or a garrison);</li>
 *   <li>picks its next support cast on the same look - an attack order's harm when no friend is near the target, else
 *       an area heal on a cluster, a single heal, a cleanse, and an area harm only where no friend is - and asks the
 *       unit to deliver it (SupportUnit.castSupport; the Field Medic throws a potion today, PotionDelivery).</li>
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
    /**
     * An enemy that has it as its target counts from this close - inside CLEAR_OF_MELEE, so a unit holding its standoff
     * never steps back and forth. Not further: an enemy's idle aggro reaches 10, so at 10 a medic stepped away from a
     * standing group every 1.5 s and never got her attack-order throw off (CI run 39718993567); and an archer off at
     * range is the army's job - a healer that ran from every bow would never heal.
     */
    public static final double AIMED_RADIUS = 5.0;
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
    /**
     * ...and never further back than this: the Field Medic's own standoff (throw range 8 - 1.5). A long-reach support
     * (a Tide Priest, an Ink Scribe: 10) keeps close to its army instead of trailing it by 8.5, still clear of melee.
     */
    public static final double MAX_STANDOFF = 6.5;
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
    /** An idle unit that walked further than this for a cast walks back to its post. */
    static final double HOME_DRIFT = 2.0;
    /** A friend must miss at least this much health for a heal (not a scratch: a potion's cooldown is shared). */
    public static final float HEAL_MIN_MISSING = 3f;
    /** How far past a cast's range a unit may walk for a heal or cleanse: idle, and under a support order. */
    public static final double IDLE_WALK = 4.0, ORDER_WALK = 2.0;

    static final SupportField FIELD = new SupportField(64);
    private static final SupportField CHECK = new SupportField(64);
    private static final List<LivingEntity> SCRATCH = new ArrayList<>();
    private static final double[] P = new double[3], Q = new double[2], OURS = new double[2];
    private static final SupportMath.Aim AIM = new SupportMath.Aim();
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
                else if (mayStepBehind(mob, unit, s))
                    idleBehind(level, mob, unit, s, now, reach);
            }
            cast(level, mob, unit, s, now);
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
            flags |= F_BURNING | F_AFFLICTED;
        else if ((flags & F_FRIEND) != 0 && afflicted(le))
            flags |= F_AFFLICTED;
        Vec3 v = le.getDeltaMovement();
        return f.add(le, le.getX(), le.getZ(), v.x, v.z, le.getHealth(), le.getMaxHealth(), valueOf(u), flags);
    }

    /** Under a harmful effect (a cleanse's job). Only asked of friends; a unit carries a handful of effects at most. */
    static boolean afflicted(LivingEntity le) {
        for (MobEffectInstance e : le.getActiveEffects())
            if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL)
                return true;
        return false;
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
            SupportMath.behind(ax, az, ex, ez, standoff(reach), mob.getX(), mob.getZ(), P);
            return stand(mob, s);
        }
        if (dontLead(mob, reach))
            return true;
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

    /**
     * Behind our front while it fights; else never ahead of the army - behind our fighter nearest a seen enemy
     * ({@link #dontLead}), and on the march no further on than its head less the standoff (the old march walked
     * straight to the rear-rank spot by the click, ahead of a slower army and into the enemy); else on to the rear-rank
     * spot - where the Fight order ends.
     */
    static boolean fightSpot(Mob mob, SupportState s, double reach) {
        if (behindFront(mob, s, reach))
            return stand(mob, s);
        BlockPos d = s.destination;
        if (d == null) {
            s.endOrder();
            return false;
        }
        if (dontLead(mob, reach))
            return stand(mob, s);
        double dx = d.getX() + 0.5 - mob.getX(), dz = d.getZ() + 0.5 - mob.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= ARRIVE + 1) {
            s.endOrder();   // arrived with no fight round it: the order is done, the unit is idle again
            return false;
        }
        double ux = dx / dist, uz = dz / dist;
        // the march's head: our fighter furthest along the way; the unit waits (it never walks back) until the head is
        // its standoff further on
        double go = dist;
        int head = SupportMath.furthestAlong(FIELD, F_FRIEND | F_ATTACKER, F_SUPPORT | F_SELF,
            mob.getX(), mob.getZ(), ux, uz);
        if (head >= 0)
            go = Math.min(go, SupportMath.along(FIELD, head, mob.getX(), mob.getZ(), ux, uz) - standoff(reach));
        if (go < dist) {
            if (go <= ARRIVE)
                return false;
            P[0] = mob.getX() + ux * go;
            P[1] = mob.getZ() + uz * go;
            P[2] = mob.getY();
            return true;
        }
        P[0] = d.getX() + 0.5;
        P[1] = d.getZ() + 0.5;
        P[2] = d.getY();
        return true;
    }

    /**
     * Never ahead of our front (lovish, Oct 30: "stay behind units"): the nearest enemy the look sees and our fighter
     * nearest it - the front. A unit not its standoff further from that enemy than the front gets the spot that far
     * behind the front (SupportMath.behind) in P: true. False with no enemy or no fighter in the look, or when it is
     * behind already.
     */
    static boolean dontLead(Mob mob, double reach) {
        double sx = mob.getX(), sz = mob.getZ();
        if (SupportMath.nearest(FIELD, F_ENEMY | F_ATTACKER, 0, sx, sz, 1e4, 0f) == 0)
            return false;
        int e = FIELD.cand[0];
        double ex = FIELD.x[e], ez = FIELD.z[e];
        if (SupportMath.nearest(FIELD, F_FRIEND | F_ATTACKER, F_SUPPORT | F_SELF, ex, ez, 1e4, 0f) == 0)
            return false;
        int fr = FIELD.cand[0];
        double want = standoff(reach);
        double dFront = Math.hypot(FIELD.x[fr] - ex, FIELD.z[fr] - ez), dMe = Math.hypot(sx - ex, sz - ez);
        if (dMe >= dFront + want - ARRIVE)
            return false;
        SupportMath.behind(FIELD.x[fr], FIELD.z[fr], ex, ez, want, sx, sz, P);
        P[2] = ((Entity) FIELD.ref[fr]).getY();
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
        SupportMath.behind(FIELD.x[fr], FIELD.z[fr], Q[0], Q[1], standoff(reach), mob.getX(), mob.getZ(), P);
        P[2] = ((Entity) FIELD.ref[fr]).getY();
        return true;
    }

    /** How far behind its front a unit of this reach stands: reach - STANDOFF_MARGIN, at most MAX_STANDOFF. */
    public static double standoff(double reach) {
        return Math.min(reach - STANDOFF_MARGIN, MAX_STANDOFF);
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

    /**
     * May an idle unit take its standoff behind a fight by itself? Not on hold position, garrisoned, riding, following,
     * patrolling or guarding, nor while it walks a player's move (a player's move is the player's: Unit.isIdle is false
     * until it ends) - only its own last step, which it may re-aim.
     */
    static boolean mayStepBehind(Mob mob, SupportUnit unit, SupportState s) {
        if (unit.getHoldPosition() || unit.isGarrisoned() || mob.isPassenger() || unit.getFollowTarget() != null)
            return false;
        if (PatrolServerEvents.isPatrolling(mob.getId()) || GuardServerEvents.isGuarding(mob.getId()))
            return false;
        MoveToTargetBlockGoal goal = unit.getMoveGoal();
        if (goal == null)
            return false;
        BlockPos current = goal.getMoveTarget();
        return current != null ? current.equals(s.lastIssued) : unit.isIdle();
    }

    /** An idle unit whose front fights within its look holds the standoff behind it, clear of enemy melee. */
    static void idleBehind(ServerLevel level, Mob mob, SupportUnit unit, SupportState s, long now, double reach) {
        if (!behindFront(mob, s, reach))
            return;
        double dx = P[0] - mob.getX(), dz = P[1] - mob.getZ();
        if (dx * dx + dz * dz <= ARRIVE * ARRIVE)
            return;
        SupportMath.keepClear(FIELD, CLEAR_OF_MELEE, P);
        goTo(level, mob, unit, s, now, P[0], P[2], P[1]);
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
        // the player's own move order (no support order, not following, patrolling or guarding) is left alone; the
        // brain's own idle step (its last issued move) is not one
        if (s.mode == Mode.NONE && !s.homing && unit.getFollowTarget() == null && unit.getMoveGoal() != null
                && unit.getMoveGoal().getMoveTarget() != null
                && !unit.getMoveGoal().getMoveTarget().equals(s.lastIssued)
                && !PatrolServerEvents.isPatrolling(mob.getId()) && !GuardServerEvents.isGuarding(mob.getId()))
            return false;
        if (!SupportMath.fleeDirection(FIELD, mob.getX(), mob.getZ(), DANGER_RADIUS, AIMED_RADIUS, Q))
            return false;
        BlockPos cell = stepCell(level, mob, unit, Q[0], Q[1]);
        if (cell == null) {
            s.lastKite = now - KITE_COOLDOWN + CORNERED_RETRY;   // cornered: stand, look again shortly
            return false;
        }
        // lovish, Oct 29 ("Cast before kiting"): one autocast look before the step - the step is a move order, and the
        // AutoCaster leaves a unit on a move order alone until it ends. A cast ends this think; the next one steps from
        // wherever the cast left it (a blink moves it). A unit with nothing to autocast finds nothing here.
        if (AutoCaster.look(level, mob, unit))
            return false;
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
     * A fresh check at the moment a cast lands or is released (a wind-up gives a friend time to walk in): would an area
     * harm of {@code radius} at (x, z) touch a friend it hurts, now or in about a second? Its own small grid query.
     */
    public static boolean harmSafeAt(ServerLevel level, Mob caster, SupportUnit unit, double x, double z, double radius, Undead rule) {
        scan(CHECK, level, caster, unit, x, z, radius + SupportMath.HARM_SAFETY + SupportField.MAX_LOOKAHEAD);
        // the caster went in first wherever it stands: it counts only if it is near the spot
        boolean safe = SupportMath.harmSafeAt(CHECK, x, z, radius, rule);
        CHECK.clear();
        return safe;
    }

    // ------------------------------------------------------------------ the cast choice

    /**
     * The unit's next support cast on this look, delivery-agnostic (SupportKind): an attack order's area harm at its
     * target whenever no friend it would hurt is near it; otherwise, when it may cast by itself (canAutocastNow), the
     * first of SupportKind.PRIORITY it can cast that finds a target - an area heal on 2+ hurt friends, a single heal on
     * the friend with the most missing health x value, a cleanse on an afflicted friend, an area harm only where no
     * friend is. Nothing found: the unit's own last job (supportIdleCast).
     */
    static void cast(ServerLevel level, Mob mob, SupportUnit unit, SupportState s, long now) {
        if (unit.isSupportCasting() || now < s.kiteUntil || SealServerEvents.isSealed(mob))
            return;
        double sx = mob.getX(), sz = mob.getZ();
        // 1. the attack order
        if (s.mode == Mode.STANDOFF_UNIT && s.orderTarget != null && unit.canCastSupport(level, SupportKind.AREA_HARM, false)) {
            LivingEntity t = s.orderTarget;
            Undead rule = unit.supportUndeadRule(SupportKind.AREA_HARM);
            double radius = unit.supportCastRadius(SupportKind.AREA_HARM);
            boolean spared = rule != Undead.NORMAL && t.getMobType() == MobType.UNDEAD;   // it would not hurt (or would mend) it
            if (!spared && SupportMath.harmSafeAt(FIELD, t.getX(), t.getZ(), radius, rule)
                    && unit.castSupport(level, SupportKind.AREA_HARM, t, t.getX(), t.getY(), t.getZ(), true))
                return;
        }
        if (!canAutocastNow(mob, unit))
            return;
        boolean planted = unit.getHoldPosition() || unit.isGarrisoned();
        double walk = planted ? 0 : s.mode != Mode.NONE ? ORDER_WALK : IDLE_WALK;
        for (SupportKind kind : SupportKind.PRIORITY) {
            if (!unit.canCastSupport(level, kind, true))
                continue;
            double range = unit.supportCastRange(kind), radius = unit.supportCastRadius(kind);
            Undead rule = unit.supportUndeadRule(kind);
            switch (kind) {
                case AREA_HEAL -> {
                    if (SupportMath.areaHealAim(FIELD, sx, sz, range + walk, radius, HEAL_MIN_MISSING, rule, AIM)
                            && castAt(level, mob, unit, kind, AIM.index, AIM.x, AIM.z))
                        return;
                }
                case SINGLE_HEAL -> {
                    int t = SupportMath.healTarget(FIELD, sx, sz, range + walk, radius, HEAL_MIN_MISSING, rule);
                    if (t >= 0 && castOn(level, mob, unit, kind, t))
                        return;
                }
                case CLEANSE -> {
                    int t = SupportMath.cleanseTarget(FIELD, sx, sz, range + walk, unit.supportCleanses());
                    if (t >= 0 && castOn(level, mob, unit, kind, t))
                        return;
                }
                case AREA_HARM -> {
                    // our side: the friends round it that are not support units, else itself
                    if (SupportMath.centroid(FIELD, F_FRIEND, F_SELF | F_SUPPORT, sx, sz, 1e4, OURS) == 0) {
                        OURS[0] = sx;
                        OURS[1] = sz;
                    }
                    // never a walk toward enemies for it: only within its own range
                    if (SupportMath.harmAim(FIELD, sx, sz, range, radius, OURS[0], OURS[1], rule, AIM)
                            && castAt(level, mob, unit, kind, AIM.index, AIM.x, AIM.z))
                        return;
                }
            }
        }
        unit.supportIdleCast(level, now);
    }

    private static boolean castOn(ServerLevel level, Mob mob, SupportUnit unit, SupportKind kind, int i) {
        LivingEntity t = (LivingEntity) FIELD.ref[i];
        armHome(mob, unit);
        return armed(unit, unit.castSupport(level, kind, t, t.getX(), t.getY(), t.getZ(), false));
    }

    private static boolean castAt(ServerLevel level, Mob mob, SupportUnit unit, SupportKind kind, int ref, double x, double z) {
        armHome(mob, unit);
        return armed(unit, unit.castSupport(level, kind, null, x, ((Entity) FIELD.ref[ref]).getY(), z, false));
    }

    /** A cast the unit refused leaves no walk home armed. */
    private static boolean armed(SupportUnit unit, boolean cast) {
        if (!cast)
            unit.getSupportState().homeArmed = false;
        return cast;
    }

    /**
     * A cast went off (for the medic: the potion left her hand): the counters the game tests read, and an idle unit
     * that walked away for it heads back to its post. {@code kind} null: a click the brain did not choose.
     */
    public static void onCast(Mob mob, SupportUnit unit, @Nullable SupportKind kind, boolean selfDecided, double x, double z) {
        SupportState s = unit.getSupportState();
        s.lastCastX = x;
        s.lastCastZ = z;
        if (kind != null)
            switch (kind) {
                case SINGLE_HEAL -> s.heals++;
                case AREA_HEAL -> s.areaHeals++;
                case AREA_HARM -> s.harms++;
                case CLEANSE -> s.cleanses++;
            }
        boolean ordered = s.mode != Mode.NONE || s.homing || unit.getFollowTarget() != null
            || PatrolServerEvents.isPatrolling(mob.getId()) || GuardServerEvents.isGuarding(mob.getId());
        if (ordered && kind != null && kind.heals())
            s.healsWhileOrdered++;
        if (s.homeArmed) {
            s.homeArmed = false;
            double dx = s.homeX - mob.getX(), dz = s.homeZ - mob.getZ();
            if (selfDecided && s.mode == Mode.NONE && dx * dx + dz * dz > HOME_DRIFT * HOME_DRIFT)
                s.homing = true;
        }
    }

    /** An idle unit about to walk for a cast remembers where it stood (onCast sends it back). */
    static void armHome(Mob mob, SupportUnit unit) {
        SupportState s = unit.getSupportState();
        if (s.mode != Mode.NONE || s.homing || !unit.isIdle())
            return;
        s.homeArmed = true;
        s.homeX = mob.getX();
        s.homeY = mob.getY();
        s.homeZ = mob.getZ();
    }
}
