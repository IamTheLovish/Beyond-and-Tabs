package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.BuildingGrid;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingScansLegacy;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.GridNeighbors;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

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
 *       (in a cauldron, on a canopy, wedged under a beam) while it has something to do;</li>
 *   <li><b>pocketed</b>: it stands on a perfectly good cell from which the grid's own moves reach no cell
 *       {@link #FAR_DIST} blocks away - the hollow between a building's pillars and its rim, a 2-high ring, a ledge
 *       too high to drop from (lovish: workers "got stuck by some stone that the building architecture had"). Checked
 *       only once the unit has stood still for a check, and for an idle unit only in or beside a building footprint,
 *       so a parked army on open ground costs nothing.</li>
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
    /** Down-search for a unit put on a building's roof: a last-resort spawn on a Fortress roof is 17 up. */
    static final int SEARCH_DOWN_BUILDING = 24;
    /**
     * Persistent-data flag ProductionPlacement.produceUnit sets on a unit it had to spawn on its building's roof
     * (nothing within 10 blocks fitted its body): a roof is bigger than POCKET_MAX_CELLS, so the pocket rule would
     * leave it up there; this has it brought down to the nearest fitting ground cell at its first check instead.
     */
    public static final String KEY_ROOF_SPAWN = "bt_roof_spawn";
    /** A cell this far (horizontally) from where a unit stands must be walkable-to, or the unit is pocketed. */
    public static final int FAR_DIST = 6;
    static final int POCKET_BUDGET = 160;          // cells expanded before a pocket search gives up (= not pocketed)
    /** A closed area with more cells than this is a yard or a hall, not a pocket: units in it are left alone. */
    static final int POCKET_MAX_CELLS = 20;
    static final int NEAR_BUILDING = 1;            // cells beyond a footprint that still count as "in" it

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
        boolean standable = mob.onGround() && isOnStandableGround(mob);
        boolean roof = mob.getPersistentData().getBoolean(KEY_ROOF_SPAWN);
        boolean stuck = roof || isEncased(mob) || (mob.onGround() && !standable && !unit.isIdle());
        // a rooted (or rooting/uprooting) Treant stands still on purpose: no pocket lift or path drop, but freed if encased
        boolean rootedInPlace = mob instanceof com.solegendary.reignofnether.unit.units.verdant.SentinelTreantUnit t && t.isImmobile();
        // following a path but not moving: pushing into something the grid thinks is open
        boolean pushing = !stuck && !rootedInPlace && mob.onGround() && !mob.getNavigation().isDone();
        // standing still somewhere it may not be able to leave: worth a pocket search once it has stood a check
        boolean maybePocketed = !stuck && !rootedInPlace && standable && (!unit.isIdle() || nearBuilding(feetCell(mob)));
        if ((!stuck && !pushing && !maybePocketed) || GarrisonableBuildingAddon.getGarrison(unit) != null) {
            SUSPECTS.remove(mob);
            return;
        }
        Suspect s = SUSPECTS.computeIfAbsent(mob, m -> new Suspect());
        Vec3 pos = mob.position();
        s.strikes = (s.lastPos != null && s.lastPos.distanceToSqr(pos) < MOVED_SQ) ? s.strikes + 1 : 1;
        s.lastPos = pos;
        if (maybePocketed && s.strikes >= STRIKES && isPocketed(level, standCell(mob)))
            stuck = true;
        if (stuck && (s.strikes >= STRIKES || roof)) {
            SUSPECTS.remove(mob);
            BlockPos to = findStandPos(level, feetCell(mob), SEARCH_RADIUS, roof ? SEARCH_DOWN_BUILDING : SEARCH_DOWN,
                SEARCH_UP, mob, 0f, 0f, c -> !isPocketed(level, c));
            if (to != null) {
                if (roof)
                    mob.getPersistentData().remove(KEY_ROOF_SPAWN);
                rescue(mob, to);
            }
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

    /** The cell the pathfinder stands the unit in: its feet cell, or the one above on a part-height block. */
    public static BlockPos standCell(Mob mob) {
        BlockPos c = feetCell(mob);
        return isStandable(mob.level(), c) ? c : c.above();
    }

    /** Whether a cell is in, or within {@link #NEAR_BUILDING} cells of, a building's footprint (server side). */
    // every standing unit asks this on its check tick: the BuildingGrid cells round the cell, not every building
    static boolean nearBuilding(BlockPos cell) {
        if (BuildingGrid.legacyScans)
            return BuildingScansLegacy.isPosNearAnyBuilding(BuildingServerEvents.getBuildings(), cell, NEAR_BUILDING);
        return BuildingServerEvents.getBuildingGrid().anyInside(cell, NEAR_BUILDING, false);
    }

    /**
     * Whether a 1-wide, 2-tall ground unit standing in {@code origin} can walk to no cell {@link #FAR_DIST} or more
     * blocks away: the grid's own moves (GridNeighbors / GridAStar: 8 flat, 4 cardinal step-ups needing headroom over
     * the origin, step-downs, drops of up to MAX_FALL_DROP) over live block states, searched farthest-first. On open
     * ground it is a handful of expansions to the far ring; in a pocket it runs out of cells. Running out with more
     * than {@link #POCKET_MAX_CELLS} reached (a walled yard, a hall behind a door) or exhausting {@link #POCKET_BUDGET}
     * without reaching the far ring (a maze) counts as NOT pocketed: this frees units from hollows, it doesn't let
     * them out of enclosures a player built. A few hundred block reads at most; run only for units that have stood
     * still for a check.
     */
    public static boolean isPocketed(Level level, BlockPos origin) {
        final int farSq = FAR_DIST * FAR_DIST;
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        // farthest-first: an open field is left in a straight line, a pocket is exhausted whatever the order
        java.util.PriorityQueue<long[]> open = new java.util.PriorityQueue<>((a, b) -> Long.compare(b[1], a[1]));
        long startKey = origin.asLong();
        seen.add(startKey);
        open.add(new long[] { startKey, 0 });
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int expanded = 0;
        while (!open.isEmpty()) {
            long[] cur = open.poll();
            BlockPos c = BlockPos.of(cur[0]);
            int x = c.getX(), y = c.getY(), z = c.getZ();
            if (++expanded > POCKET_BUDGET)
                return false;
            boolean riseOk = !bodyBlocked(level, p.set(x, y + 2, z));
            for (int d = 0; d < 8; d++) {
                int nx = x + GridNeighbors.DX[d], nz = z + GridNeighbors.DZ[d];
                int ny = step(level, p, x, y, z, d, riseOk);
                if (ny == Integer.MIN_VALUE)
                    continue;
                int dx = nx - origin.getX(), dz = nz - origin.getZ();
                long distSq = (long) dx * dx + (long) dz * dz;
                if (distSq >= farSq)
                    return false;
                long key = BlockPos.asLong(nx, ny, nz);
                if (seen.add(key)) {
                    if (seen.size() > POCKET_MAX_CELLS)
                        return false;
                    open.add(new long[] { key, distSq });
                }
            }
        }
        return true;
    }

    /**
     * The height of the cell a ground unit standing in (x, y, z) reaches by one grid move in direction {@code d}
     * (GridNeighbors.DX/DZ): flat, a cardinal step up ({@code riseOk}: headroom over the start), a step down, or a drop
     * of up to MAX_FALL_DROP; {@link Integer#MIN_VALUE} when there is no such move. {@code p} is scratch.
     */
    private static int step(Level level, BlockPos.MutableBlockPos p, int x, int y, int z, int d, boolean riseOk) {
        int nx = x + GridNeighbors.DX[d], nz = z + GridNeighbors.DZ[d];
        boolean diag = GridNeighbors.DX[d] != 0 && GridNeighbors.DZ[d] != 0;
        if (isWalkable(level, p.set(nx, y, nz)) && !diagonalBlocked(level, p, x, y, z, d))
            return y;
        if (!diag && riseOk && isWalkable(level, p.set(nx, y + 1, nz)))
            return y + 1;
        if (isWalkable(level, p.set(nx, y - 1, nz)) && !diagonalBlocked(level, p, x, y - 1, z, d))
            return y - 1;
        if (!bodyBlocked(level, p.set(nx, y, nz)) && !bodyBlocked(level, p.set(nx, y + 1, nz))
                && !bodyBlocked(level, p.set(nx, y - 1, nz)) && !diagonalBlocked(level, p, x, y, z, d)) {
            // a drop: step off the edge into a clear column and land on the first walkable cell below
            for (int fy = y - 2; fy >= y - PathfinderConfig.MAX_FALL_DROP; fy--) {
                if (bodyBlocked(level, p.set(nx, fy + 1, nz)))
                    break;
                if (isWalkable(level, p.set(nx, fy, nz)))
                    return fy;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Blocks outside a footprint a unit must be able to reach to count as out of the building (not one alley cell). */
    public static final int LEAVE_MARGIN = 3;
    /** Cells a {@link #leavesFootprint} search expands before it gives the benefit of the doubt. */
    static final int LEAVE_BUDGET = 1024;

    /**
     * Whether a 1-wide, 2-tall ground unit standing in {@code origin} can walk out of the box {@code min..max} (a
     * building's footprint) to open ground: some cell it can reach is {@link #LEAVE_MARGIN} or more blocks outside the
     * box. The grid's own moves, as {@link #isPocketed}; searched outermost first, so an open way out takes a few dozen
     * cells. A closed yard (the Graveyard's, with a building or a bank against its one gate) runs out of cells: false.
     * {@link #isPocketed} can't see that - the yard is bigger than a pocket - and this is what a building spawning a unit
     * asks (ProductionPlacement.produceUnit). A search that spends {@link #LEAVE_BUDGET} cells answers true: a big
     * interior is not taken for a trap.
     */
    public static boolean leavesFootprint(Level level, BlockPos origin, BlockPos min, BlockPos max) {
        if (outside(origin.getX(), origin.getZ(), min, max) >= LEAVE_MARGIN)
            return true;
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        // outermost first: a cell further out of the box (or nearer its edge, inside) is expanded before the others
        java.util.PriorityQueue<long[]> open = new java.util.PriorityQueue<>((a, b) -> Long.compare(b[1], a[1]));
        seen.add(origin.asLong());
        open.add(new long[] { origin.asLong(), outside(origin.getX(), origin.getZ(), min, max) });
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int expanded = 0;
        while (!open.isEmpty()) {
            if (++expanded > LEAVE_BUDGET)
                return true;
            BlockPos c = BlockPos.of(open.poll()[0]);
            int x = c.getX(), y = c.getY(), z = c.getZ();
            boolean riseOk = !bodyBlocked(level, p.set(x, y + 2, z));
            for (int d = 0; d < 8; d++) {
                int ny = step(level, p, x, y, z, d, riseOk);
                if (ny == Integer.MIN_VALUE)
                    continue;
                int nx = x + GridNeighbors.DX[d], nz = z + GridNeighbors.DZ[d];
                int out = outside(nx, nz, min, max);
                if (out >= LEAVE_MARGIN)
                    return true;
                long key = BlockPos.asLong(nx, ny, nz);
                if (seen.add(key))
                    open.add(new long[] { key, out });
            }
        }
        return false;
    }

    // How far (x, z) is outside the box horizontally: > 0 outside, <= 0 inside (minus the distance to the nearest edge).
    private static int outside(int x, int z, BlockPos min, BlockPos max) {
        return Math.max(Math.max(min.getX() - x, x - max.getX()), Math.max(min.getZ() - z, z - max.getZ()));
    }

    /** A cell a ground unit may move through: anything the pathfinder gives a finite cost (not blocked, not lava). */
    public static boolean isWalkable(Level level, BlockPos cell) {
        byte kind = WalkabilityBuilder.classify(level, cell.getX(), cell.getY(), cell.getZ());
        return kind != WalkabilityBuilder.KIND_BLOCKED && kind != WalkabilityBuilder.KIND_LAVA;
    }

    /** A cell a body can't pass through (the grid's "solid": solid, leaves, fence-like or crossing the centre). */
    public static boolean bodyBlocked(Level level, BlockPos cell) {
        BlockState bs = level.getBlockState(cell);
        return WalkabilityBuilder.blocksBody(level, cell, bs, true) || bs.is(BlockTags.LEAVES)
            || WalkabilityBuilder.isFenceLike(bs);
    }

    // a diagonal move may not cut a blocked corner (GridNeighbors.diagonalBlocked); cardinal moves never do
    private static boolean diagonalBlocked(Level level, BlockPos.MutableBlockPos p, int x, int toY, int z, int d) {
        int dx = GridNeighbors.DX[d], dz = GridNeighbors.DZ[d];
        if (dx == 0 || dz == 0)
            return false;
        return !isWalkable(level, p.set(x + dx, toY, z)) || !isWalkable(level, p.set(x, toY, z + dz));
    }

    /**
     * Where to put a stuck unit: the nearest standable cell that fits its body AND is not itself a pocket - the nearest
     * standable cell to a unit wedged in a trunk is often the hollow beside it, and a unit put in a pocket is rescued
     * into it again two checks later. Null if nothing within range qualifies.
     */
    @Nullable
    public static BlockPos findEscapePos(Level level, BlockPos origin, @Nullable Mob forMob) {
        return findStandPos(level, origin, SEARCH_RADIUS, SEARCH_DOWN, SEARCH_UP, forMob, 0f, 0f,
            c -> !isPocketed(level, c));
    }

    /**
     * As {@link #findStandPos(Level, BlockPos, int, int, int, float, float)} (a body not yet in the world) but never a
     * pocket: where a building spawns a unit. A width of 0 means a one-cell body.
     */
    @Nullable
    public static BlockPos findEscapePos(Level level, BlockPos origin, int radius, int down, int up, float width, float height) {
        return findStandPos(level, origin, radius, down, up, null, width, height, c -> !isPocketed(level, c));
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
        return findStandPos(level, origin, radius, down, up, forMob, 0f, 0f, null);
    }

    /**
     * As {@link #findStandPos(Level, BlockPos, int, int, int, Mob)} for a body that isn't in the world yet (a spawn):
     * the cell must also have room for a {@code width} x {@code height} hitbox centred on it.
     */
    @Nullable
    public static BlockPos findStandPos(Level level, BlockPos origin, int radius, int down, int up, float width, float height) {
        return findStandPos(level, origin, radius, down, up, null, width, height, null);
    }

    // accept: an extra test a candidate cell must pass (null = none); run last, as it may be the costly one
    @Nullable
    private static BlockPos findStandPos(Level level, BlockPos origin, int radius, int down, int up, @Nullable Mob forMob,
                                         float width, float height, @Nullable Predicate<BlockPos> accept) {
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
                        if (!isStandable(level, p) || (forMob != null && !fits(level, forMob, p))
                                || (width > 0f && !bodyFits(level, p, width, height))
                                || (accept != null && !accept.test(p)))
                            continue;
                        best = p.immutable();
                        bestScore = score;
                    }
                }
        }
        return best;
    }

    /**
     * Whether a {@code width} x {@code height} hitbox centred on the cell, standing on its floor, touches no block and
     * no lava or fire. Block by block from the block states, not Level.noCollision: that reads chunks with
     * getChunkNow, which is null for a chunk that is loaded but not yet full (a lab with no unit near it, a pad a
     * test has just stamped), and a null chunk collides with nothing - so a Ghast "fit" inside the Flame Sanctuary's
     * lava pillar and burned to death before the player saw it. Lava and fire have no collision and are rejected by
     * name, and not only inside the body but within a block of it: a 3-wide Ghast put flush against the Sanctuary's
     * lava pillar was nudged 0.4 into it by the Bonewright made beside it and died in a second (units have no hurt
     * window). A spawn or a rescue: a few dozen block reads.
     */
    public static boolean bodyFits(Level level, BlockPos cell, float width, float height) {
        double hw = width / 2.0;
        AABB box = new AABB(cell.getX() + 0.5 - hw, cell.getY(), cell.getZ() + 0.5 - hw,
                            cell.getX() + 0.5 + hw, cell.getY() + height, cell.getZ() + 0.5 + hw).deflate(0.01);
        VoxelShape bodyShape = net.minecraft.world.phys.shapes.Shapes.create(box);
        int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX);
        int y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.maxY);
        int z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(x0 - 1, y0, z0 - 1, x1 + 1, y1, z1 + 1)) {
            BlockState bs = level.getBlockState(p);
            if (bs.isAir())
                continue;
            if (bs.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || bs.is(BlockTags.FIRE))
                return false;
            if (p.getX() < x0 || p.getX() > x1 || p.getZ() < z0 || p.getZ() > z1)
                continue;   // the ring round the body: only lava and fire matter there
            VoxelShape shape = bs.getCollisionShape(level, p);
            if (shape.isEmpty())
                continue;
            if (shape == net.minecraft.world.phys.shapes.Shapes.block()
                    || net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape.move(p.getX(), p.getY(), p.getZ()), bodyShape,
                        net.minecraft.world.phys.shapes.BooleanOp.AND))
                return false;
        }
        return true;
    }

    private static boolean fits(Level level, Mob mob, BlockPos cell) {
        AABB box = mob.getBoundingBox();
        AABB moved = box.move(cell.getX() + 0.5 - mob.getX(), cell.getY() - box.minY, cell.getZ() + 0.5 - mob.getZ());
        return level.noCollision(mob, moved.deflate(0.01));
    }

    /** Puts the unit on {@code cell} and makes every movement goal it has repath from there, once each. */
    public static void rescue(Mob mob, BlockPos cell) {
        mob.getNavigation().stop();
        mob.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        mob.setDeltaMovement(Vec3.ZERO);
        // once each: a forced repath is judged (it can count toward the give-up), and the plain move goal is usually
        // both in the selector and the unit's getMoveGoal - twice made every rescue two stuck repaths
        Set<MoveToTargetBlockGoal> goals = Collections.newSetFromMap(new IdentityHashMap<>());
        for (WrappedGoal wg : mob.goalSelector.getAvailableGoals())
            if (wg.getGoal() instanceof MoveToTargetBlockGoal g)
                goals.add(g);
        if (mob instanceof Unit u && u.getMoveGoal() != null)
            goals.add(u.getMoveGoal());   // not every unit registers its move goal with the selector
        for (MoveToTargetBlockGoal g : goals)
            g.forceRepath();
    }

    /** For tests. */
    public static int suspectCount() {
        return SUSPECTS.size();
    }
}
