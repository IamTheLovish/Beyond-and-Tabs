package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trees never obscure the game and are natural reclaim (BAR: trees are energy features you reclaim or build over).
 * <ul>
 *   <li>Placing a building fells every tree in its footprint plus a {@link #MARGIN}-block ring the moment the site
 *       is laid, crediting {@link #ENERGY_PER_LOG} energy per log to the owner. Leaves vanish with their tree.
 *       Natural logs no longer block placement (BuildingValidators): the tree is cleared instead.</li>
 *   <li>Workers' area reclaim (Ctrl+right-drag, AreaCommands) includes trees: each worker walks to its trees and
 *       fells one when it gets within {@link #FELL_REACH} blocks.</li>
 * </ul>
 * Performance: scans are bounded (at most {@link #MAX_SPAN} blocks across, one heightmap lookup per column, at most
 * {@link #MAX_LOGS_PER_TREE} logs and {@link #MAX_LEAVES_PER_TREE} leaves per tree) and the felling itself is spread
 * over ticks ({@link #BLOCKS_PER_TICK} blocks a tick across all jobs). Blocks are removed with flags 2|16 (sent to
 * clients, no neighbour shape updates): no item drops, no leaf decay cascades, no physics.
 * Only natural trees count: a trunk must stand on soil, carry at least one natural (non-persistent) leaf or wart
 * block, and lie outside every building - a building's own log walls are never touched.
 */
public class TreeClearing {

    public static final int MARGIN = 2;
    public static final float ENERGY_PER_LOG = 5f;
    public static final int MAX_SPAN = 96;              // per axis: fits an area-reclaim circle (radius 40) and any footprint
    static final int MAX_TREE_REACH = 7;                // a tree's logs stay within this of its trunk (big oaks, acacias)
    static final int MAX_LOGS_PER_TREE = 160;
    static final int MAX_LEAVES_PER_TREE = 1500;
    static final int MAX_LEAF_STEPS = 6;                // vanilla leaves die beyond 6 from a log
    static final int MAX_TRUNK_WALK = 32;               // heightmap top down to the trunk base
    static final int BLOCKS_PER_TICK = 192;
    public static final double FELL_REACH = 5.0;        // a worker fells a tree from this far (nanolathe reach)
    static final int ORDER_CHECK_TICKS = 10;
    static final int ORDER_LIFETIME_TICKS = 20 * 90;
    static final int MAX_ORDERS = 512;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static boolean isLog(BlockState bs) {
        return bs.is(BlockTags.LOGS);
    }

    /** Leaves and the nether "canopies" of huge fungi (wart blocks, shroomlights). */
    public static boolean isCanopy(BlockState bs) {
        return bs.is(BlockTags.LEAVES) || bs.is(BlockTags.WART_BLOCKS) || bs.is(Blocks.SHROOMLIGHT);
    }

    /** A canopy block the world grew (player-placed and template leaves are persistent). */
    static boolean isNaturalCanopy(BlockState bs) {
        if (bs.getBlock() instanceof LeavesBlock)
            return !bs.getValue(LeavesBlock.PERSISTENT);
        return bs.is(BlockTags.WART_BLOCKS) || bs.is(Blocks.SHROOMLIGHT);
    }

    static boolean isSoil(BlockState bs) {
        return bs.is(BlockTags.DIRT) || bs.is(BlockTags.NYLIUM) || bs.is(BlockTags.SAND) || bs.is(Blocks.MOSS_BLOCK);
    }

    /** One tree: its trunk base, logs and canopy blocks. */
    public static final class Tree {
        public final BlockPos base;
        public final List<BlockPos> logs = new ArrayList<>();
        public final List<BlockPos> canopy = new ArrayList<>();

        Tree(BlockPos base) {
            this.base = base;
        }
    }

    /** What a scan found: the trees and their total logs (energy = logs * ENERGY_PER_LOG). */
    public static final class Scan {
        public final List<Tree> trees = new ArrayList<>();
        public int logs;

        public float energy() {
            return logs * ENERGY_PER_LOG;
        }
    }

    // ------------------------------------------------------------------ scanning (server and client)

    /**
     * The trees whose trunk stands in the box min..max (x/z), with its base between {@code baseMinY} and
     * {@code baseMaxY}. One heightmap lookup per column; a column whose top block is a log is walked down to its
     * base. Clamped to {@link #MAX_SPAN} per axis.
     */
    public static Scan scan(Level level, int minX, int minZ, int maxX, int maxZ, int baseMinY, int baseMaxY) {
        Scan out = new Scan();
        if (maxX - minX >= MAX_SPAN)
            maxX = minX + MAX_SPAN - 1;
        if (maxZ - minZ >= MAX_SPAN)
            maxZ = minZ + MAX_SPAN - 1;
        Set<BlockPos> claimed = new HashSet<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunkAt(m.set(x, baseMinY, z)))
                    continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                m.set(x, top, z);
                if (!isLog(level.getBlockState(m)))
                    continue;
                // down the trunk to the block standing on soil
                int y = top, walked = 0;
                while (walked++ < MAX_TRUNK_WALK && isLog(level.getBlockState(m.set(x, y - 1, z))))
                    y--;
                if (y < baseMinY || y > baseMaxY || !isSoil(level.getBlockState(m.set(x, y - 1, z))))
                    continue;
                BlockPos base = new BlockPos(x, y, z);
                if (claimed.contains(base))
                    continue;   // a 2x2 trunk is one tree
                Tree t = tree(level, base, claimed);
                if (t != null) {
                    out.trees.add(t);
                    out.logs += t.logs.size();
                }
            }
        return out;
    }

    /** Flood one tree from its trunk base; null if it is not a natural tree (no natural canopy, or in a building). */
    public static Tree tree(Level level, BlockPos base, Set<BlockPos> claimed) {
        if (!isLog(level.getBlockState(base)) || BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), base))
            return null;
        Tree t = new Tree(base);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(base);
        seen.add(base);
        while (!open.isEmpty() && t.logs.size() < MAX_LOGS_PER_TREE) {
            BlockPos p = open.poll();
            t.logs.add(p);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos q = p.offset(dx, dy, dz);
                        if (q.getY() < base.getY() || Math.abs(q.getX() - base.getX()) > MAX_TREE_REACH
                                || Math.abs(q.getZ() - base.getZ()) > MAX_TREE_REACH || !seen.add(q))
                            continue;
                        if (isLog(level.getBlockState(q)) && (claimed == null || !claimed.contains(q)))
                            open.add(q);
                    }
        }
        // the canopy: leaves reachable from the logs within the distance vanilla leaves can live from a log
        Set<BlockPos> logSet = new HashSet<>(t.logs);
        Set<BlockPos> leafSeen = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>(t.logs);
        boolean natural = false;
        for (int step = 0; step < MAX_LEAF_STEPS && !frontier.isEmpty() && t.canopy.size() < MAX_LEAVES_PER_TREE; step++) {
            ArrayDeque<BlockPos> next = new ArrayDeque<>();
            for (BlockPos p : frontier)
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    BlockPos q = p.relative(d);
                    if (logSet.contains(q) || !leafSeen.add(q))
                        continue;
                    BlockState bs = level.getBlockState(q);
                    if (!isCanopy(bs))
                        continue;
                    natural |= isNaturalCanopy(bs);
                    t.canopy.add(q);
                    next.add(q);
                    if (t.canopy.size() >= MAX_LEAVES_PER_TREE)
                        break;
                }
            frontier = next;
        }
        if (!natural)
            return null;
        if (claimed != null)
            claimed.addAll(t.logs);
        return t;
    }

    /** The trees a building at min..max would fell: footprint plus MARGIN, trunks based near its floor. */
    public static Scan scanFootprint(Level level, BlockPos min, BlockPos max) {
        return scan(level, min.getX() - MARGIN, min.getZ() - MARGIN, max.getX() + MARGIN, max.getZ() + MARGIN,
            min.getY() - 3, min.getY() + 8);
    }

    // ------------------------------------------------------------------ felling (server)

    /** Blocks waiting to be removed for one owner, logs first. */
    public static final class FellJob {
        final ServerLevel level;
        final String owner;
        final ArrayDeque<BlockPos> logs = new ArrayDeque<>();
        final ArrayDeque<BlockPos> canopy = new ArrayDeque<>();
        /** The site's own blocks (a building may be made of logs or leaves): never removed. */
        final Map<BlockPos, BlockState> keep;
        public int logsFelled = 0;
        public float energyCredited = 0;

        FellJob(ServerLevel level, String owner, Map<BlockPos, BlockState> keep) {
            this.level = level;
            this.owner = owner;
            this.keep = keep;
        }

        public boolean isDone() {
            return logs.isEmpty() && canopy.isEmpty();
        }

        /** Removes up to {@code budget} blocks; returns how many it spent. Public for the GameTests. */
        public int step(int budget) {
            int spent = 0, felled = 0;
            while (spent < budget && !logs.isEmpty()) {
                BlockPos p = logs.poll();
                spent++;
                BlockState bs = level.getBlockState(p);
                if (!isLog(bs) || bs.equals(keep.get(p)))
                    continue;
                level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                felled++;
            }
            while (spent < budget && !canopy.isEmpty()) {
                BlockPos p = canopy.poll();
                spent++;
                BlockState bs = level.getBlockState(p);
                if (!isCanopy(bs) || bs.equals(keep.get(p)))
                    continue;
                level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
            }
            if (felled > 0) {
                logsFelled += felled;
                if (owner != null && !owner.isEmpty())
                    energyCredited += EconomyServerEvents.addReclaimedEnergy(owner, felled * ENERGY_PER_LOG);
            }
            return spent;
        }

        /** Runs the job to the end at once (GameTests). */
        public void finish() {
            while (!isDone())
                step(Integer.MAX_VALUE);
        }
    }

    private static final List<FellJob> jobs = new ArrayList<>();

    static FellJob queue(ServerLevel level, String owner, Scan scan, Map<BlockPos, BlockState> keep) {
        FellJob job = new FellJob(level, owner, keep);
        for (Tree t : scan.trees) {
            job.logs.addAll(t.logs);
            job.canopy.addAll(t.canopy);
        }
        if (!job.isDone())
            jobs.add(job);
        return job;
    }

    /**
     * A building site was laid (BuildingServerEvents.placeBuilding): fell the trees in its footprint and margin for
     * its owner. Called before the site clears or levels its footprint, so the logs are credited, not erased.
     * Returns the job (null when there was nothing to fell).
     */
    public static FellJob clearForBuilding(ServerLevel level, BuildingPlacement placement) {
        if (level == null || placement == null || placement.minCorner == null)
            return null;
        Scan scan = scanFootprint(level, placement.minCorner, placement.maxCorner);
        if (scan.trees.isEmpty())
            return null;
        Map<BlockPos, BlockState> keep = new HashMap<>();
        for (BuildingBlock b : placement.getBlocks())
            if (!b.getBlockState().isAir())
                keep.put(b.getBlockPos(), b.getBlockState());
        return queue(level, placement.ownerName, scan, keep);
    }

    /** Fell the one tree standing on {@code base} for {@code owner} (a worker's reclaim). Null if it is no tree. */
    public static FellJob fellTree(ServerLevel level, String owner, BlockPos base) {
        Tree t = tree(level, base, null);
        if (t == null)
            return null;
        Scan scan = new Scan();
        scan.trees.add(t);
        scan.logs = t.logs.size();
        return queue(level, owner, scan, Map.of());
    }

    // ------------------------------------------------------------------ worker reclaim orders (server)

    /** A worker sent to fell the tree on {@code base}. */
    record Order(String owner, int workerId, BlockPos base, long expires) { }

    private static final List<Order> orders = new ArrayList<>();
    private static long tick = 0;

    /** Area reclaim handed a tree to a worker; it fells it once within FELL_REACH. */
    public static void orderFell(String owner, int workerId, BlockPos base) {
        if (orders.size() >= MAX_ORDERS)
            return;
        orders.add(new Order(owner, workerId, base, tick + ORDER_LIFETIME_TICKS));
    }

    public static boolean hasOrder(int workerId, BlockPos base) {
        for (Order o : orders)
            if (o.workerId() == workerId && o.base().equals(base))
                return true;
        return false;
    }

    /** Fells the trees whose worker has arrived; drops orders that expired or whose worker or tree is gone. */
    public static void checkOrders(ServerLevel level) {
        Iterator<Order> it = orders.iterator();
        while (it.hasNext()) {
            Order o = it.next();
            Entity e = level.getEntity(o.workerId());
            if (tick > o.expires() || (e != null && !e.isAlive())) {
                it.remove();
                continue;
            }
            if (e == null)
                continue;   // another dimension / unloaded: keep until it expires
            if (!isLog(level.getBlockState(o.base()))) {
                it.remove();   // felled already (a building, another worker)
                continue;
            }
            double dx = e.getX() - (o.base().getX() + 0.5), dz = e.getZ() - (o.base().getZ() + 0.5);
            if (dx * dx + dz * dz <= FELL_REACH * FELL_REACH && Math.abs(e.getY() - o.base().getY()) <= 6) {
                it.remove();
                if (fellTree(level, o.owner(), o.base()) != null && e instanceof net.minecraft.world.entity.LivingEntity le)
                    com.solegendary.reignofnether.barfx.BarFx.nano(le, o.base().getX() + 0.5, o.base().getY() + 1.5,
                        o.base().getZ() + 0.5, com.solegendary.reignofnether.barfx.BarFx.NANO_INTERVAL, 22);
            }
        }
    }

    /** Trunk bases of the natural trees in a circle (area reclaim), at most {@code max}. */
    public static List<BlockPos> treesIn(Level level, BlockPos centre, int radius, int maxDy, int max) {
        Scan scan = scan(level, centre.getX() - radius, centre.getZ() - radius, centre.getX() + radius,
            centre.getZ() + radius, centre.getY() - maxDy, centre.getY() + maxDy);
        List<BlockPos> out = new ArrayList<>();
        double r2 = (double) radius * radius;
        for (Tree t : scan.trees) {
            double dx = t.base.getX() - centre.getX(), dz = t.base.getZ() - centre.getZ();
            if (dx * dx + dz * dz <= r2)
                out.add(t.base);
            if (out.size() >= max)
                break;
        }
        return out;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        tick++;
        if (!jobs.isEmpty()) {
            int budget = BLOCKS_PER_TICK;
            Iterator<FellJob> it = jobs.iterator();
            while (it.hasNext() && budget > 0) {
                FellJob job = it.next();
                budget -= job.step(budget);
                if (job.isDone())
                    it.remove();
            }
        }
        if (!orders.isEmpty() && tick % ORDER_CHECK_TICKS == 0 && evt.getServer() != null)
            checkOrders(evt.getServer().overworld());
    }

    /** The server stopped / a new world: nothing carries over (a job holds its level). */
    public static void reset() {
        jobs.clear();
        orders.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent evt) {
        reset();
    }
}
