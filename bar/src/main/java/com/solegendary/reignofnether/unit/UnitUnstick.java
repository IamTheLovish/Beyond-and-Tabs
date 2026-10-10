package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Frees units that ended up somewhere they can't walk out of (lovish: "my workers got stuck while playing verdant").
 * The causes found were units spawned inside a building's trunk or basin, paths routed through azalea bushes (vanilla
 * calls them non-solid) and units a construction site was built over; the first two are fixed at the source
 * (ProductionPlacement.produceUnit spawns on standable ground, WalkabilityBuilder treats cauldron rims as no floor and
 * body-sized non-solid collisions as walls), and this is the safety net for whatever else does it.
 * <p>
 * Every {@link #CHECK_INTERVAL} ticks (staggered by entity id) a ground unit is checked for one of:
 * <ul>
 *   <li><b>encased</b>: its body overlaps a block's collision (a log, a root, leaves placed round it) - stuck whatever
 *       it was doing, vanilla never pushes a mob out;</li>
 *   <li><b>on a blocked cell with an order</b>: the pathfinder's own classification says it can't stand where it is
 *       (in a cauldron, on a canopy, wedged under a beam) while it has something to do.</li>
 * </ul>
 * A unit seen like that on {@link #STRIKES} checks in a row without having moved is teleported to the nearest cell the
 * pathfinder can stand on, and its movement goals repath from there. A unit that follows a path without moving for
 * {@link #PUSH_STRIKES} checks (pushing into something the grid thinks is open) has the path dropped so it repaths.
 * Units cost one cell classification and one collision query every 2 s; state is kept only for units that are moving
 * along a path or suspect, a small object per unit at worst.
 */
public final class UnitUnstick {
    private UnitUnstick() { }

    public static final int CHECK_INTERVAL = 40;   // 2 s
    public static final int STRIKES = 2;           // two checks in a row: 2-4 s from stuck to free
    public static final int PUSH_STRIKES = 3;      // 4-6 s pushing along a path without moving
    static final double MOVED_SQ = 0.3 * 0.3;      // moved less than this between checks = not moving
    static final int SEARCH_RADIUS = 10;           // far enough to leave the biggest footprint (Heart of the Wild, 15)
    static final int SEARCH_DOWN = 8, SEARCH_UP = 3;

    private static final class Suspect {
        Vec3 lastPos;
        int strikes;
    }
    private static final Map<Mob, Suspect> SUSPECTS = new WeakHashMap<>();

    /** Server side, every tick from Unit.tick: does nothing except on the unit's staggered check tick. */
    public static void tick(Mob mob) {
        if ((mob.tickCount + mob.getId()) % CHECK_INTERVAL != 0 || !(mob.level() instanceof ServerLevel level))
            return;
        if (!(mob instanceof Unit unit) || !mob.isAlive() || mob.isPassenger() || mob.noPhysics || unit.isFlyingUnit()) {
            SUSPECTS.remove(mob);
            return;
        }
        boolean stuck = isEncased(mob) || (mob.onGround() && !isOnStandableGround(mob) && !unit.isIdle());
        // following a path but not moving: pushing into something the grid thinks is open
        boolean pushing = !stuck && mob.onGround() && !mob.getNavigation().isDone();
        if ((!stuck && !pushing) || GarrisonableBuildingAddon.getGarrison(unit) != null) {
            SUSPECTS.remove(mob);
            return;
        }
        Suspect s = SUSPECTS.computeIfAbsent(mob, m -> new Suspect());
        Vec3 pos = mob.position();
        s.strikes = (s.lastPos != null && s.lastPos.distanceToSqr(pos) < MOVED_SQ) ? s.strikes + 1 : 1;
        s.lastPos = pos;
        if (stuck && s.strikes >= STRIKES) {
            SUSPECTS.remove(mob);
            BlockPos to = findStandPos(level, feetCell(mob), SEARCH_RADIUS, SEARCH_DOWN, SEARCH_UP, mob);
            if (to != null)
                rescue(mob, to);
        } else if (pushing && s.strikes >= PUSH_STRIKES) {
            // drop the path: the goal that owns it repaths from here, and a plain move order that keeps ending on the
            // same spot is given up (MoveToTargetBlockGoal.GIVE_UP_AFTER_STUCK_REPATHS) instead of pushed at forever
            SUSPECTS.remove(mob);
            mob.getNavigation().stop();
        }
    }

    /** The cell a unit's feet are in (nudged up a hair so a unit standing exactly on a block top reads as above it). */
    public static BlockPos feetCell(Mob mob) {
        return BlockPos.containing(mob.getX(), mob.getY() + 0.01, mob.getZ());
    }

    /**
     * Whether the pathfinder would stand the unit where it is: its feet cell or the one above is standable. Both,
     * because a unit on a part-height block (a slab, a path, snow layers - their collision tops sit inside the cell)
     * has its feet inside that block's cell while the pathfinder stands it in the cell above. A unit sunk into a
     * cauldron (feet cell = the cauldron, the one above has the hollow rim as its floor), wedged in a trunk or perched
     * on a canopy fails both.
     */
    public static boolean isOnStandableGround(Mob mob) {
        BlockPos c = feetCell(mob);
        return isStandable(mob.level(), c) || isStandable(mob.level(), c.above());
    }

    /** Whether the body overlaps any block collision (shrunk a little so touching a wall or the floor doesn't count). */
    public static boolean isEncased(Mob mob) {
        AABB box = mob.getBoundingBox().deflate(0.05, 0, 0.05);
        box = new AABB(box.minX, box.minY + 0.1, box.minZ, box.maxX, Math.max(box.minY + 0.2, box.maxY - 0.05), box.maxZ);
        for (VoxelShape shape : mob.level().getBlockCollisions(mob, box))
            if (!shape.isEmpty())
                return true;
        return false;
    }

    /** A cell a ground unit can stand in by the pathfinder's rules (land, or a tidepool on solid ground). */
    public static boolean isStandable(Level level, BlockPos cell) {
        byte kind = WalkabilityBuilder.classify(level, cell.getX(), cell.getY(), cell.getZ());
        return kind == WalkabilityBuilder.KIND_LAND || kind == WalkabilityBuilder.KIND_TIDEPOOL;
    }

    /**
     * The nearest standable cell to {@code origin} within {@code radius} columns, {@code down} below and {@code up}
     * above it; null if there is none. Searched in growing rings and stopped once no farther ring can beat the best
     * found. Going down is cheaper than going up (a unit inside a trunk belongs on the floor, not on the canopy). With a
     * mob, the cell must also fit its whole body (big treants). Rare (spawns into a blocked spot, rescues), so the few
     * thousand block reads of a worst case are fine.
     */
    @Nullable
    public static BlockPos findStandPos(Level level, BlockPos origin, int radius, int down, int up, @Nullable Mob forMob) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r++) {
            if (r * r * 4 > bestScore)
                break;   // every cell in this ring and beyond is farther than the best
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                        continue;   // ring only
                    for (int dy = -down; dy <= up; dy++) {
                        int score = (dx * dx + dz * dz) * 4 + (dy < 0 ? -dy : dy * 3);
                        if (score >= bestScore)
                            continue;
                        p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (!isStandable(level, p) || (forMob != null && !fits(level, forMob, p)))
                            continue;
                        best = p.immutable();
                        bestScore = score;
                    }
                }
        }
        return best;
    }

    private static boolean fits(Level level, Mob mob, BlockPos cell) {
        AABB box = mob.getBoundingBox();
        AABB moved = box.move(cell.getX() + 0.5 - mob.getX(), cell.getY() - box.minY, cell.getZ() + 0.5 - mob.getZ());
        return level.noCollision(mob, moved.deflate(0.01));
    }

    /** Puts the unit on {@code cell} and makes every movement goal it has repath from there. */
    public static void rescue(Mob mob, BlockPos cell) {
        mob.getNavigation().stop();
        mob.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        mob.setDeltaMovement(Vec3.ZERO);
        for (WrappedGoal wg : mob.goalSelector.getAvailableGoals())
            if (wg.getGoal() instanceof MoveToTargetBlockGoal g)
                g.forceRepath();
        if (mob instanceof Unit u && u.getMoveGoal() != null)
            u.getMoveGoal().forceRepath();   // not every unit registers its move goal with the selector
    }

    /** For tests. */
    public static int suspectCount() {
        return SUSPECTS.size();
    }
}
