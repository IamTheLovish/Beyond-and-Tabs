package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * A spatial index of one side's building list, by 16x16 column cell (a chunk), for the lookups that used to walk every
 * building: "is this block inside any building", "which building is here", "the closest building to this point",
 * "the closest building this unit may attack". Lovish's Oct 21 3-player match (perf plan 1.1 / 3.1) ran ~1,700
 * buildings and 420-650 units at 55-105 ms a tick; each idle attacker walked all 1,700 buildings every 4 ticks
 * (MiscUtil.findClosestAttackableBuilding, with a footprint walk and block reads for every hostile one, however far),
 * and isPosInsideAnyBuilding / findClosestBuilding / attack-move / UnitUnstick did the same per unit. Through the grid
 * each looks at the few cells its point or reach touches.
 * <p>
 * Same answers as the full scans, by construction (BuildingGridGameTests compares both on random layouts):
 * <ul>
 * <li>each building is filed under every cell of the box round its footprint and its origin, so any building a point
 *     or box query could match sits in a cell the query reads;</li>
 * <li>every test the full scan made is still made, live, on each candidate (the grid only skips buildings that
 *     cannot pass the query's distance or containment test);</li>
 * <li>ties go to the building earlier in the list, as the full scans' strict {@code <} gave them: candidates carry
 *     their list index, and a nearest-search compares (distance, index).</li>
 * </ul>
 * Kept current without hooks: the index is a snapshot of the list, rebuilt lazily by the first query after the list's
 * {@link BuildingList#version()} moved (a placement, a destroy, a sale - not every tick). A building's footprint never
 * moves after construction (BuildingPlacement: minCorner and centrePos are final, originPos is only set by the
 * constructor; an upgrade only raises maxCorner's Y, BuildingPlacement.changeStructure), so a snapshot stays valid
 * until the list changes. Like UnitGrid it is meant for one thread per side (server / client); a snapshot is immutable
 * once built and published whole, so a reader can never see a half-built one.
 */
public final class BuildingGrid {

    /** 16-block cells: buildings are 3x3 to ~30x30, and the usual query reach (aggro ranges of 6-30) is 1-3 cells. */
    static final int SHIFT = 4;
    /**
     * A footprint spanning more cells than this (a pathological or corrupt one) is not filed; every query checks it
     * instead. Not final only so BuildingGridGameTests can lower it and run that path with ordinary buildings.
     */
    public static int maxCellsPerBuilding = 256;
    /** Grid margin round the footprint box: the attack spot getClosestGroundPos picks is one block outside it. */
    public static final int GROUND_RING = 1;

    /**
     * Test and benchmark switch: true routes every converted lookup back to its pre-grid full scan
     * ({@link BuildingScansLegacy}). The [Stress] benchmark in BuildingGridGameTests times a tick both ways with it.
     * Never set in play.
     */
    public static volatile boolean legacyScans = false;

    public interface Visitor {
        void visit(BuildingPlacement building, int listIndex);
    }

    private final BuildingList list;
    private volatile Snapshot snap = null;
    private int rebuilds = 0;

    public BuildingGrid(BuildingList list) {
        this.list = list;
    }

    /** The grid of the given side's building list (BuildingClientEvents is only touched on the client side). */
    public static BuildingGrid of(boolean isClientSide) {
        return isClientSide ? BuildingClientEvents.getBuildingGrid() : BuildingServerEvents.getBuildingGrid();
    }

    /** How many times the index was rebuilt (the [Stress] benchmark logs it: one per list change, not per tick). */
    public int rebuilds() {
        return rebuilds;
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private Snapshot snapshot() {
        Snapshot s = snap;
        int v = list.version();   // read before the copy: a change during the copy shows as a new version next time
        if (s != null && s.version == v)
            return s;
        s = new Snapshot(list.toArray(new BuildingPlacement[0]), v);
        snap = s;
        rebuilds++;
        return s;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // point queries
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Whether {@code bp} is inside any building's box ({@link BuildingPlacement#isPosInsideBuilding}), or within
     * {@code edge} blocks of it ({@code isPosInsideBuilding(bp, edge)}); with {@code farmsOnly}, of a farm only.
     */
    public boolean anyInside(BlockPos bp, int edge, boolean farmsOnly) {
        Snapshot s = snapshot();
        int x0 = (bp.getX() - edge) >> SHIFT, x1 = (bp.getX() + edge) >> SHIFT;
        int z0 = (bp.getZ() - edge) >> SHIFT, z1 = (bp.getZ() + edge) >> SHIFT;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++) {
                int[] ids = s.cells.get(key(cx, cz));
                if (ids != null)
                    for (int i : ids)
                        if (inside(s.all[i], bp, edge, farmsOnly))
                            return true;
            }
        for (int i : s.big)
            if (inside(s.all[i], bp, edge, farmsOnly))
                return true;
        return false;
    }

    private static boolean inside(BuildingPlacement b, BlockPos bp, int edge, boolean farmsOnly) {
        if (farmsOnly && !(b instanceof FarmPlacement))
            return false;
        return edge == 0 ? b.isPosInsideBuilding(bp) : b.isPosInsideBuilding(bp, edge);
    }

    /** The first building in list order whose origin is {@code pos} or whose box holds it (BuildingUtils.findBuilding). */
    @Nullable
    public BuildingPlacement findAt(BlockPos pos) {
        Snapshot s = snapshot();
        int best = Integer.MAX_VALUE;
        int[] ids = s.cells.get(key(pos.getX() >> SHIFT, pos.getZ() >> SHIFT));
        if (ids != null)
            for (int i : ids) {   // ascending: the first hit is the cell's earliest
                BuildingPlacement b = s.all[i];
                if (b.originPos.equals(pos) || b.isPosInsideBuilding(pos)) {
                    best = i;
                    break;
                }
            }
        for (int i : s.big) {   // ascending too: stop at the first hit, or once past the cell's
            if (i >= best)
                break;
            BuildingPlacement b = s.all[i];
            if (b.originPos.equals(pos) || b.isPosInsideBuilding(pos)) {
                best = i;
                break;
            }
        }
        return best == Integer.MAX_VALUE ? null : s.all[best];
    }

    /** Whether some building in the list has its origin at {@code pos} (BuildingUtils.isBuildingBuildable). */
    public boolean hasOrigin(BlockPos pos) {
        Snapshot s = snapshot();
        int[] ids = s.cells.get(key(pos.getX() >> SHIFT, pos.getZ() >> SHIFT));
        if (ids != null)
            for (int i : ids)
                if (s.all[i].originPos.equals(pos))
                    return true;
        for (int i : s.big)
            if (s.all[i].originPos.equals(pos))
                return true;
        return false;
    }

    /** {@code list.contains(b)} by identity (BuildingPlacement has no equals of its own, so it is the same answer). */
    public boolean contains(BuildingPlacement b) {
        return b != null && snapshot().index.containsKey(b);
    }

    /** The first building in list order that passes {@code cond} (null if none). */
    @Nullable
    public BuildingPlacement firstMatching(Predicate<BuildingPlacement> cond) {
        for (BuildingPlacement b : snapshot().all)
            if (b != null && cond.test(b))
                return b;
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // box query
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Visits, once each and in no particular order, every building whose footprint-and-origin box reaches into the
     * X/Z box [minX..maxX] x [minZ..maxZ] (plus a few that do not: callers make their exact test). The visitor gets
     * each building's list index for tie-breaks. A box wider than the list is worth walks the whole list instead.
     */
    public void forEachNear(int minX, int minZ, int maxX, int maxZ, Visitor v) {
        Snapshot s = snapshot();
        int qx0 = minX >> SHIFT, qx1 = maxX >> SHIFT, qz0 = minZ >> SHIFT, qz1 = maxZ >> SHIFT;
        long cellCount = ((long) qx1 - qx0 + 1) * ((long) qz1 - qz0 + 1);
        if (cellCount > Math.max(16, s.all.length)) {
            for (int i = 0; i < s.all.length; i++)
                if (s.all[i] != null)
                    v.visit(s.all[i], i);
            return;
        }
        for (int cx = qx0; cx <= qx1; cx++)
            for (int cz = qz0; cz <= qz1; cz++) {
                int[] ids = s.cells.get(key(cx, cz));
                if (ids == null)
                    continue;
                for (int i : ids)
                    // a building filed in several of the box's cells is visited in the first of them only
                    if (cx == Math.max(s.cellX0[i], qx0) && cz == Math.max(s.cellZ0[i], qz0))
                        v.visit(s.all[i], i);
            }
        for (int i : s.big)
            v.visit(s.all[i], i);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // nearest by centre
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The building passing {@code cond} with the smallest {@code dist}, among those under {@code maxDistSq}; on a tie
     * the earlier one in the list - exactly what a list-order scan with a strict {@code <} returns.
     * <p>
     * {@code dist} must measure a squared distance from (px, py, pz) to the building's centrePos shifted by
     * {@code centreOffset} on X and Z (0 for the block corner, 0.5 for the block centre) - it is never smaller than the
     * horizontal part, which is what lets the search stop. Searched ring by ring of centre cells outward; it stops once
     * every centre not yet seen is provably farther than the best so far (or than maxDistSq), and falls back to the
     * plain list scan when the rings would read more cells than there are buildings.
     */
    @Nullable
    public BuildingPlacement nearestByCentre(double px, double py, double pz, double centreOffset, double maxDistSq,
                                             ToDoubleFunction<BuildingPlacement> dist, Predicate<BuildingPlacement> cond) {
        Snapshot s = snapshot();
        if (s.all.length == 0)
            return null;
        if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz))
            return nearestLinear(s, maxDistSq, dist, cond);
        int qx = ((int) Math.floor(px)) >> SHIFT, qz = ((int) Math.floor(pz)) >> SHIFT;
        long cellBudget = Math.max(64, s.all.length);
        long cellsRead = 0;
        BuildingPlacement best = null;
        double bestD = maxDistSq;
        int bestI = Integer.MAX_VALUE;
        for (int r = 0; ; r++) {
            // ring r: the cells at Chebyshev distance exactly r from the query's cell
            for (int cx = qx - r; cx <= qx + r; cx++) {
                boolean edgeColumn = cx == qx - r || cx == qx + r;
                int step = edgeColumn ? 1 : 2 * r;
                for (int cz = qz - r; cz <= qz + r; cz += Math.max(1, step)) {
                    cellsRead++;
                    int[] ids = s.centres.get(key(cx, cz));
                    if (ids == null)
                        continue;
                    for (int i : ids) {
                        BuildingPlacement b = s.all[i];
                        double d = dist.applyAsDouble(b);
                        boolean better = best == null ? d < maxDistSq : (d < bestD || (d == bestD && i < bestI));
                        if (better && cond.test(b)) {
                            best = b;
                            bestD = d;
                            bestI = i;
                        }
                    }
                }
            }
            // every centre is in a cell already read
            if (qx - r <= s.cMinX && qx + r >= s.cMaxX && qz - r <= s.cMinZ && qz + r >= s.cMaxZ)
                return best;
            // the nearest a centre outside the rings read so far can be (horizontally), trimmed a hair for rounding
            long lo = ((long) (qx - r) << SHIFT) - 1, hi = (long) (qx + r + 1) << SHIFT;
            long loZ = ((long) (qz - r) << SHIFT) - 1, hiZ = (long) (qz + r + 1) << SHIFT;
            double gap = Math.min(Math.min(px - (lo + centreOffset), (hi + centreOffset) - px),
                Math.min(pz - (loZ + centreOffset), (hiZ + centreOffset) - pz));
            gap = Math.max(0, gap - 1e-6);
            double gapSq = gap * gap;
            if (best != null ? gapSq > bestD : gapSq >= maxDistSq)
                return best;
            // the next ring would read 8(r+1) cells: past the budget the list scan is cheaper (and as exact)
            if (cellsRead + 8L * (r + 1) > cellBudget)
                return nearestLinear(s, maxDistSq, dist, cond);
        }
    }

    private static BuildingPlacement nearestLinear(Snapshot s, double maxDistSq, ToDoubleFunction<BuildingPlacement> dist,
                                                   Predicate<BuildingPlacement> cond) {
        BuildingPlacement best = null;
        double bestD = maxDistSq;
        for (BuildingPlacement b : s.all) {
            if (b == null)
                continue;
            double d = dist.applyAsDouble(b);
            if (d < bestD && cond.test(b)) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** One immutable bucketing of the list as it stood at {@link #version}. */
    private static final class Snapshot {
        final int version;
        final BuildingPlacement[] all;
        /** cell -> ascending list indices of the buildings whose footprint-and-origin box covers the cell */
        final Long2ObjectOpenHashMap<int[]> cells = new Long2ObjectOpenHashMap<>();
        /** cell -> ascending list indices of the buildings whose centrePos is in it (one cell per building) */
        final Long2ObjectOpenHashMap<int[]> centres = new Long2ObjectOpenHashMap<>();
        /** each building's first (lowest) cell, for visiting it once in a box query */
        final int[] cellX0, cellZ0;
        /** buildings too big to file (checked by every query) */
        final int[] big;
        final int cMinX, cMaxX, cMinZ, cMaxZ;
        final Reference2IntOpenHashMap<BuildingPlacement> index;

        Snapshot(BuildingPlacement[] all, int version) {
            this.version = version;
            this.all = all;
            int n = all.length;
            cellX0 = new int[n];
            cellZ0 = new int[n];
            index = new Reference2IntOpenHashMap<>(n);
            Long2ObjectOpenHashMap<IntArrayList> area = new Long2ObjectOpenHashMap<>();
            Long2ObjectOpenHashMap<IntArrayList> centre = new Long2ObjectOpenHashMap<>();
            IntArrayList bigOnes = new IntArrayList();
            int minCX = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE, minCZ = Integer.MAX_VALUE, maxCZ = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                BuildingPlacement b = all[i];
                if (b == null) {   // never expected; filed nowhere, skipped by the list walks
                    cellX0[i] = cellZ0[i] = Integer.MAX_VALUE;
                    continue;
                }
                if (!index.containsKey(b))
                    index.put(b, i);
                BlockPos o = b.originPos, lo = b.minCorner, hi = b.maxCorner;
                int x0 = o.getX(), x1 = o.getX(), z0 = o.getZ(), z1 = o.getZ();
                if (lo.getX() <= hi.getX() && lo.getZ() <= hi.getZ()) {   // a building with no blocks has no box
                    x0 = Math.min(x0, lo.getX());
                    x1 = Math.max(x1, hi.getX());
                    z0 = Math.min(z0, lo.getZ());
                    z1 = Math.max(z1, hi.getZ());
                }
                int cx0 = x0 >> SHIFT, cx1 = x1 >> SHIFT, cz0 = z0 >> SHIFT, cz1 = z1 >> SHIFT;
                cellX0[i] = cx0;
                cellZ0[i] = cz0;
                if (((long) cx1 - cx0 + 1) * ((long) cz1 - cz0 + 1) > maxCellsPerBuilding) {
                    bigOnes.add(i);
                } else {
                    for (int cx = cx0; cx <= cx1; cx++)
                        for (int cz = cz0; cz <= cz1; cz++)
                            add(area, key(cx, cz), i);
                }
                BlockPos c = b.centrePos;
                int ccx = c.getX() >> SHIFT, ccz = c.getZ() >> SHIFT;
                add(centre, key(ccx, ccz), i);
                minCX = Math.min(minCX, ccx);
                maxCX = Math.max(maxCX, ccx);
                minCZ = Math.min(minCZ, ccz);
                maxCZ = Math.max(maxCZ, ccz);
            }
            for (Long2ObjectMap.Entry<IntArrayList> e : area.long2ObjectEntrySet())
                cells.put(e.getLongKey(), e.getValue().toIntArray());
            for (Long2ObjectMap.Entry<IntArrayList> e : centre.long2ObjectEntrySet())
                centres.put(e.getLongKey(), e.getValue().toIntArray());
            big = bigOnes.toIntArray();
            cMinX = minCX;
            cMaxX = maxCX;
            cMinZ = minCZ;
            cMaxZ = maxCZ;
        }

        private static void add(Long2ObjectOpenHashMap<IntArrayList> map, long k, int i) {
            IntArrayList l = map.get(k);
            if (l == null) {
                l = new IntArrayList(4);
                map.put(k, l);
            }
            l.add(i);
        }
    }
}
