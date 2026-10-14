package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where a unit goes to fight a target (design/terrain_intelligence.md). The combat goals used to path straight at
 * the target with vanilla A*, which has two faults the Oct 13 clips showed: a unit already in water chases through
 * water (vanilla lets a negative-cost start node accept negative-cost neighbours, so the river becomes the cheapest
 * route), and every attacker of one target paths to the same cell, so three attackers stand inside each other and a
 * crowd at a river bank funnels onto one tile. This picks the cell the chase paths to instead:
 * <ul>
 *   <li><b>Melee</b>: one of {@link #SLOTS} ring slots at {@link #RING_RADIUS} round the target, claimed per target
 *       (released when the attacker changes target, stops, or dies - a slot whose owner no longer wants the target is
 *       free). Only cells the attacker's mobility can stand in count, so a land unit never gets a slot in the water.
 *       A wading target with no land slot in its ring gets the nearest land cells within {@link #LAND_SEARCH} blocks
 *       as its slots (the bank, spread along it); none at all = {@code null} = hold where it is.</li>
 *   <li><b>Ranged</b>: the target's own cell, or, for a wading target, the nearest land cell to it - a shooter holds
 *       the shore and shoots if in range. Once in range, a shooter with another shooter within
 *       {@link #SPREAD_DISTANCE} steps one block sideways, once per {@link #SPREAD_COOLDOWN}.</li>
 * </ul>
 * The chase stays on vanilla A* (synchronous, a few cells) rather than the RTS pathfinder, whose per-request chunk
 * snapshot and async pool are built for orders, not for a repath every 5 ticks per unit in a scrum. Server side.
 */
public final class Engage {
    private Engage() { }

    public static final int SLOTS = 8;
    public static final double RING_RADIUS = 1.2;
    /** How far from a wading target a melee unit looks for a bank cell to wait on. */
    public static final int LAND_SEARCH = 3;
    public static final double SPREAD_DISTANCE = 0.8;
    public static final int SPREAD_COOLDOWN = 60;
    /** How long a sidestep is let walk before the goal's in-range stop applies again. */
    public static final int SPREAD_WALK = 20;

    /** The cell a chase paths to and the exact point on the ring the unit closes to once the path is walked. */
    public record Approach(BlockPos cell, Vec3 point, boolean direct) {
        /** Squared horizontal distance from the unit to the ring point. */
        public double distSqr(Mob mob) {
            double dx = point.x - mob.getX(), dz = point.z - mob.getZ();
            return dx * dx + dz * dz;
        }
    }

    // slot claims: target -> (cell -> attacker). Weak on the target so a dead or unloaded one drops its table.
    private static final Map<LivingEntity, Map<Long, Mob>> CLAIMS = new WeakHashMap<>();
    private static final Map<Mob, LivingEntity> CLAIMED_TARGET = new WeakHashMap<>();
    private static final Map<Mob, Long> LAST_SPREAD = new WeakHashMap<>();
    // the last approach handed to each chaser (HOLD for "nowhere to go"), so StuckNudge can tell a unit standing
    // where its chase put it from one that is stuck short of it
    private static final Map<Mob, Approach> LAST = new WeakHashMap<>();
    private static final Approach HOLD = new Approach(BlockPos.ZERO, Vec3.ZERO, false);
    private static final List<LivingEntity> scratch = new ArrayList<>();
    private static long lastSweep = Long.MIN_VALUE;
    static final int SWEEP_INTERVAL = 600;

    /**
     * Where {@code mob} should go to melee {@code target}: a ring slot, a bank cell for a wading target, or null to
     * hold. Honours a pending stuck sidestep ({@link StuckNudge}) so the chase's own repath cannot undo it.
     */
    @Nullable
    public static Approach melee(Mob mob, LivingEntity target) {
        BlockPos nudge = StuckNudge.sidestep(mob);
        if (nudge != null)
            return new Approach(nudge, Vec3.atBottomCenterOf(nudge), false);
        if (!(mob instanceof Unit unit) || mob.isPassenger())
            return direct(target);
        Level level = mob.level();
        sweep(level.getGameTime());
        MobilityClass mobility = MobilityClass.of(unit);
        double reach = Math.sqrt(reachSqr(mob, target));
        // the ring sits inside reach with a margin for the target's drift within its cell; wide bodies push it out
        double radius = Math.max(RING_RADIUS, (mob.getBbWidth() + target.getBbWidth()) / 2.0 + 0.3);
        radius = Math.min(radius, Math.max(0.6, reach - 0.2));
        List<Approach> candidates = new ArrayList<>(SLOTS);
        BlockPos targetCell = target.blockPosition();
        for (int i = 0; i < SLOTS; i++) {
            double angle = i * (Math.PI * 2 / SLOTS);
            Vec3 p = new Vec3(target.getX() + Math.cos(angle) * radius, target.getY(), target.getZ() + Math.sin(angle) * radius);
            BlockPos cell = standCell(level, BlockPos.containing(p.x, p.y + 0.01, p.z), mobility);
            if (cell == null || cell.equals(targetCell) || contains(candidates, cell))
                continue;
            candidates.add(new Approach(cell, new Vec3(p.x, cell.getY(), p.z), false));
        }
        boolean targetWading = target.isInWater() && !Terrain.isExempt(unit);
        if (candidates.isEmpty() && targetWading) {
            // the whole ring is in the river: wait on the nearest bank cells instead, spread along the bank
            List<BlockPos> bank = nearestLand(level, targetCell, LAND_SEARCH, SLOTS);
            for (BlockPos cell : bank)
                candidates.add(new Approach(cell, Vec3.atBottomCenterOf(cell), false));
        }
        if (candidates.isEmpty()) {
            release(mob);
            LAST.put(mob, HOLD);
            return null;
        }
        Approach a = claim(mob, target, candidates);
        LAST.put(mob, a);
        return a;
    }

    /**
     * Where a shooter walks to get its target in range: at the target, or, for a wading target it must not follow,
     * the nearest land cell to it. Null = nowhere to go (the target is in open water with no bank within reach).
     */
    @Nullable
    public static Approach ranged(Mob mob, LivingEntity target) {
        BlockPos nudge = StuckNudge.sidestep(mob);
        if (nudge != null)
            return new Approach(nudge, Vec3.atBottomCenterOf(nudge), false);
        if (!(mob instanceof Unit unit) || mob.isPassenger() || !target.isInWater() || Terrain.isExempt(unit))
            return direct(target);
        List<BlockPos> bank = nearestLand(mob.level(), target.blockPosition(), LAND_SEARCH + 2, 1);
        if (bank.isEmpty()) {
            LAST.put(mob, HOLD);
            return null;
        }
        Approach a = new Approach(bank.get(0), Vec3.atBottomCenterOf(bank.get(0)), false);
        LAST.put(mob, a);
        return a;
    }

    /** Whether the unit is where its last chase approach put it (or was told to hold): standing there is not stuck. */
    public static boolean settled(Mob mob) {
        Approach a = LAST.get(mob);
        if (a == null)
            return false;
        if (a == HOLD)
            return true;
        return !a.direct() && a.distSqr(mob) < 0.6 * 0.6;
    }

    /** Paths {@code mob} toward its approach: vanilla A* to the cell (or to the target itself when direct). */
    public static boolean moveTo(Mob mob, LivingEntity target, @Nullable Approach approach, float speed) {
        if (approach == null) {
            mob.getNavigation().stop();
            return false;
        }
        if (approach.direct())
            return mob.getNavigation().moveTo(target, speed);
        // accuracy 0: the path must end IN the slot cell (moveTo(x, y, z) would accept a node a block short of it)
        return mob.getNavigation().moveTo(mob.getNavigation().createPath(approach.cell(), 0), speed);
    }

    /** The vanilla path to the approach (for goals that keep the Path), or null to hold. */
    @Nullable
    public static Path path(Mob mob, LivingEntity target, @Nullable Approach approach) {
        if (approach == null)
            return null;
        if (approach.direct())
            return mob.getNavigation().createPath(target, 0);
        return mob.getNavigation().createPath(approach.cell(), 0);
    }

    /**
     * The last half block: a slot cell's centre can be a hand out of reach of a target that drifted within its own
     * cell, and the vanilla path is done once the body is within ~0.45 of the cell centre. When the path is walked
     * and the unit still is not in reach, steer straight at the ring point (a point inside a cell the unit already
     * stands in or beside, so no terrain can be entered). Called every tick while out of reach; a no-op otherwise.
     */
    public static void close(Mob mob, @Nullable Approach approach, float speed) {
        if (approach == null || approach.direct() || !mob.getNavigation().isDone())
            return;
        if (approach.distSqr(mob) > 2.0 * 2.0 || approach.distSqr(mob) < 0.01)
            return;
        Vec3 p = approach.point();
        mob.getMoveControl().setWantedPosition(p.x, p.y, p.z, speed);
    }

    /** Whether a sidestep is still being walked: the goals' in-range stop must not cancel it. */
    public static boolean spreading(Mob mob) {
        Long last = LAST_SPREAD.get(mob);
        return last != null && mob.level().getGameTime() - last < SPREAD_WALK && !mob.getNavigation().isDone();
    }

    /** Drops {@code mob}'s slot claim (its target changed, died, or the goal stopped). */
    public static void release(Mob mob) {
        LAST.remove(mob);
        LivingEntity target = CLAIMED_TARGET.remove(mob);
        if (target == null)
            return;
        Map<Long, Mob> table = CLAIMS.get(target);
        if (table != null) {
            table.values().removeIf(m -> m == mob);
            if (table.isEmpty())
                CLAIMS.remove(target);
        }
    }

    /**
     * A one-off sidestep for a shooter standing in range with another shooter within {@link #SPREAD_DISTANCE}: one
     * block perpendicular to the line to its target, onto a cell it can stand in. Returns whether it stepped.
     */
    public static boolean spread(Mob mob, LivingEntity target) {
        if (!(mob.level() instanceof ServerLevel level) || !(mob instanceof Unit unit) || mob.isPassenger())
            return false;
        long now = level.getGameTime();
        Long last = LAST_SPREAD.get(mob);
        if (last != null && now - last < SPREAD_COOLDOWN)
            return false;
        LivingEntity crowding = null;
        double r2 = SPREAD_DISTANCE * SPREAD_DISTANCE;
        for (LivingEntity le : UnitGrid.near(level, mob.getX(), mob.getZ(), SPREAD_DISTANCE, scratch)) {
            if (le == mob || !le.isAlive() || !(le instanceof RangedAttackerUnit) || le instanceof WorkerUnit
                    || !(le instanceof Unit lu) || !unit.getOwnerName().equals(lu.getOwnerName()))
                continue;
            double dx = le.getX() - mob.getX(), dz = le.getZ() - mob.getZ();
            if (dx * dx + dz * dz <= r2) {
                crowding = le;
                break;
            }
        }
        scratch.clear();
        if (crowding == null)
            return false;
        LAST_SPREAD.put(mob, now);
        // perpendicular to the line of fire, away from the crowding ally
        double tx = target.getX() - mob.getX(), tz = target.getZ() - mob.getZ();
        double len = Math.sqrt(tx * tx + tz * tz);
        if (len < 0.01) {
            tx = 1;
            tz = 0;
            len = 1;
        }
        double px = -tz / len, pz = tx / len;
        double side = (crowding.getX() - mob.getX()) * px + (crowding.getZ() - mob.getZ()) * pz;
        if (side > 0) {
            px = -px;
            pz = -pz;
        }
        MobilityClass mobility = MobilityClass.of(unit);
        BlockPos here = BlockPos.containing(mob.getX(), mob.getY() + 0.01, mob.getZ());
        for (int sign = 1; sign >= -1; sign -= 2) {   // away from the ally first, the other way if that is blocked
            BlockPos cell = standCell(mob.level(), BlockPos.containing(mob.getX() + px * sign, mob.getY() + 0.01,
                mob.getZ() + pz * sign), mobility);
            if (cell == null || cell.equals(here))
                continue;
            mob.getNavigation().moveTo(mob.getNavigation().createPath(cell, 0), 1.0);
            return true;
        }
        return false;
    }

    // ---- internals ----

    /**
     * Drops the tables of dead targets and the claims of dead attackers every {@link #SWEEP_INTERVAL} ticks. The
     * tables are weak on the target, but an attacker's own target field keeps a dead target reachable from its
     * table, so a fight that ended with both sides dead would otherwise keep its claims for ever.
     */
    private static void sweep(long now) {
        if (now - lastSweep < SWEEP_INTERVAL)
            return;
        lastSweep = now;
        CLAIMS.entrySet().removeIf(e -> {
            if (!e.getKey().isAlive())
                return true;
            e.getValue().values().removeIf(m -> !m.isAlive());
            return e.getValue().isEmpty();
        });
        CLAIMED_TARGET.entrySet().removeIf(e -> !e.getKey().isAlive() || !e.getValue().isAlive());
        LAST.keySet().removeIf(m -> !m.isAlive());
        LAST_SPREAD.keySet().removeIf(m -> !m.isAlive());
    }

    private static final int[] STEPS = { 0, 1, -1 };

    private static Approach direct(LivingEntity target) {
        return new Approach(target.blockPosition(), target.position(), true);
    }

    private static boolean contains(List<Approach> list, BlockPos cell) {
        for (Approach a : list)
            if (a.cell().equals(cell))
                return true;
        return false;
    }

    /** The cell at or one step above/below {@code cell} the mobility can stand in (a slope round the target). */
    @Nullable
    static BlockPos standCell(Level level, BlockPos cell, MobilityClass mobility) {
        if (Terrain.canStand(level, cell, mobility))
            return cell;
        if (Terrain.canStand(level, cell.above(), mobility))
            return cell.above();
        if (Terrain.canStand(level, cell.below(), mobility))
            return cell.below();
        return null;
    }

    /** Up to {@code max} land cells within {@code radius} columns of {@code centre}, nearest first. */
    static List<BlockPos> nearestLand(Level level, BlockPos centre, int radius, int max) {
        List<BlockPos> out = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0)
                    continue;
                BlockPos c = centre.offset(dx, 0, dz);
                BlockPos s = null;
                for (int dy : STEPS) {   // level with the target, then the bank a step up, then a step down
                    BlockPos t = c.above(dy);
                    if (Terrain.isLand(level, t)) {
                        s = t;
                        break;
                    }
                }
                if (s == null)
                    continue;
                double d = dx * dx + dz * dz;
                int at = 0;
                while (at < dist.size() && dist.get(at) <= d)
                    at++;
                dist.add(at, d);
                out.add(at, s);
            }
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Keeps the attacker's claimed slot if it is still a candidate, else takes the nearest free one (or shares). */
    private static Approach claim(Mob mob, LivingEntity target, List<Approach> candidates) {
        LivingEntity had = CLAIMED_TARGET.get(mob);
        if (had != null && had != target)
            release(mob);
        Map<Long, Mob> table = CLAIMS.computeIfAbsent(target, t -> new java.util.HashMap<>());
        Approach mine = null;
        for (Approach a : candidates) {
            if (table.get(a.cell().asLong()) == mob) {
                mine = a;
                break;
            }
        }
        if (mine == null) {
            // the table is lazily cleaned: a claim whose owner is dead or wants another target is free, and this
            // attacker's old cell (a moving target's ring shifts every repath) goes before it takes a new one
            table.values().removeIf(m -> m == mob || !m.isAlive() || m.getTarget() != target);
            Approach best = null, bestAny = null;
            double bestD = Double.MAX_VALUE, bestAnyD = Double.MAX_VALUE;
            for (Approach a : candidates) {
                double d = a.distSqr(mob);
                if (d < bestAnyD) {
                    bestAnyD = d;
                    bestAny = a;
                }
                if (!table.containsKey(a.cell().asLong()) && d < bestD) {
                    bestD = d;
                    best = a;
                }
            }
            mine = best != null ? best : bestAny;
            if (best != null)
                table.put(mine.cell().asLong(), mob);
            CLAIMED_TARGET.put(mob, target);
        }
        return mine;
    }

    /** The melee goals' reach (AbstractMeleeAttackUnitGoal.getAttackReachSqr), for the ring radius. */
    static double reachSqr(Mob mob, LivingEntity target) {
        float width = mob.getBbWidth();
        if (mob instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit au)
            width += au.getBonusMeleeRange();
        float targetWidth = target.getBbWidth();
        if (target instanceof Unit u)
            targetWidth += u.getBonusMeleeRangeForAttackers();
        return width * 2.0F * width * 2.0F + targetWidth;
    }

    /** For tests: how many attackers hold a slot on {@code target}. */
    public static int claims(LivingEntity target) {
        Map<Long, Mob> table = CLAIMS.get(target);
        return table == null ? 0 : table.size();
    }
}
