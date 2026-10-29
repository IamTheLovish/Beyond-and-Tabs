package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Undo steps that run when a GameTest ends, however it ends: passed, failed or timed out.
 *
 * A test that cleans up in its own last lines leaks on every way out but the happy one: {@code helper.fail} and every
 * assertion throw, after which none of the test's callbacks run again; a {@code succeedWhen} only fails at its
 * timeout; ArenaReady.whenTicking throws when the units never tick; a timeout stops the test wherever it is. Vanilla
 * then leaves the failed test's units alive and every block it set outside its arena in place, and shard 1 runs a few
 * hundred tests in one world: the AC-5 extractor test that failed left its radius-48 stone pad at y 264 over the scar
 * test's arena, which read 54 (264 - 210) as its top face (#338), and a flag such as
 * {@code UnitServerEvents.rtsPathfinding} left on changes every later test's pathing.
 * <p>
 * {@link #atEnd} hangs the step on the test's own GameTestInfo as a GameTestListener: GameTestInfo.tick calls every
 * listener's testPassed or testFailed once the test is done, whatever ended it (checked in the 1.20.1 bytecode:
 * fail() and succeed() only mark it done, tick() then tells the listeners). Steps run last in, first out, each once,
 * each in its own try, so one failing undo cannot skip the rest. The returned Runnable runs the step early (a test
 * that tidies before its next phase); it does not run again at the end. Steps run on the server thread inside the
 * ending tick, before any later test starts.
 * See design/gametest_cleanup_audit.md for the leaks this replaced.
 */
public final class TestCleanup {
    private TestCleanup() { }

    /** Each running test's steps, by its GameTestInfo (identity: two tests never share one). */
    private static final Ends<GameTestInfo> ENDS = new Ends<>();
    /** Each test's pads (what it set through {@link #blocks}), for {@link #sweep}; weak, so ended tests drop out. */
    private static final Map<GameTestInfo, Footprint> PADS = new WeakHashMap<>();
    private static Field infoField;

    /** Runs {@code undo} once when this test ends - passed, failed or timed out. Returns it, run-once, to call early. */
    public static Runnable atEnd(GameTestHelper helper, Runnable undo) {
        GameTestInfo info = info(helper);
        return ENDS.add(info, undo, () -> info.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo i) { }

            @Override
            public void testPassed(GameTestInfo i) {
                ENDS.end(i);
            }

            @Override
            public void testFailed(GameTestInfo i) {
                ENDS.end(i);
            }
        }));
    }

    /** Discards every entity in {@code made} still alive when the test ends; read at the end, so later spawns count. */
    public static void entities(GameTestHelper helper, List<? extends Entity> made) {
        atEnd(helper, () -> {
            for (Entity e : made)
                if (!e.isRemoved())
                    e.discard();
        });
    }

    /**
     * Block changes outside the test's arena (sky pads, big floors, walls over the template) that are put back when the
     * test ends: vanilla only resets the arena template, and the grid places arenas ~21 blocks apart, so a pad left at
     * an absolute height lands over another test's arena in this batch or a later one. Each position keeps the state
     * it had before this test first set it.
     */
    public static BlockUndo blocks(GameTestHelper helper) {
        BlockUndo b = new BlockUndo(helper.getLevel(), PADS.computeIfAbsent(info(helper), k -> new Footprint()));
        atEnd(helper, b::restore);
        return b;
    }

    /** What {@link #blocks} hands out: set blocks through it, it remembers what stood there. */
    public static final class BlockUndo {
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> was = new LinkedHashMap<>();
        private final Footprint pads;

        private BlockUndo(ServerLevel level, Footprint pads) {
            this.level = level;
            this.pads = pads;
        }

        public void set(BlockPos pos, BlockState state) {
            BlockPos at = pos.immutable();
            if (!was.containsKey(at))
                was.put(at, level.getBlockState(at));
            pads.add(at.getX(), at.getY(), at.getZ());
            level.setBlock(at, state, 3);
        }

        /**
         * Remembers every block of the box from {@code a} to {@code b} (corners included) as it stands now, to put
         * back at the end: for a box where the game sets blocks of its own (a stamped patch, a placed building) as
         * well as the test. Call it before anything is built there.
         */
        public BlockUndo region(BlockPos a, BlockPos b) {
            pads.addBox(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
            for (BlockPos p : BlockPos.betweenClosed(a, b)) {
                BlockPos at = p.immutable();
                if (!was.containsKey(at))
                    was.put(at, level.getBlockState(at));
            }
            return this;
        }

        /**
         * Puts back every block this set, last set first; then it remembers nothing. Without neighbour updates (2|16:
         * to clients, shapes as they stand): a torch or door on a pad being taken away must not pop off as an item.
         */
        public void restore() {
            List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(was.entrySet());
            for (int i = list.size() - 1; i >= 0; i--)
                level.setBlock(list.get(i).getKey(), list.get(i).getValue(), 2 | 16);
            was.clear();
        }

        public int size() {
            return was.size();
        }
    }

    /**
     * Switches the server-wide rtsPathfinding flag on for this test and gives it back when the test ends, however it
     * ends. Counted: with several holders (tests side by side in one batch) the flag goes back to what it was only
     * when the last of them ends, not under a neighbour still running (OrdersObeyGameTests' separate batches).
     */
    public static void rtsPathfinding(GameTestHelper helper) {
        RtsPathing.take();
        atEnd(helper, RtsPathing::release);
    }

    /** The counted rtsPathfinding hold behind {@link #rtsPathfinding}. */
    static final class RtsPathing {
        private RtsPathing() { }

        private static int holders = 0;
        private static boolean saved = false;

        static void take() {
            if (holders++ == 0)
                saved = UnitServerEvents.rtsPathfinding;
            UnitServerEvents.rtsPathfinding = true;
        }

        static void release() {
            if (holders > 0 && --holders == 0)
                UnitServerEvents.rtsPathfinding = saved;
        }

        static int holders() {
            return holders;
        }
    }

    /**
     * Removes every entity but players that a failed test left in its own arena (the template box) or on its own pads
     * (blocks it set through {@link #blocks}, and the {@link Footprint#HEADROOM} above them). Vanilla removes no test's
     * entities when it ends (only the template box, once a later test's structure is placed there), and a test that
     * fails leaves the units it would have discarded on a pass: they keep fighting, and pick neighbouring arenas' units
     * as targets ~21 blocks away. Called by GameTestShards' reporter for every failed test (pass: the test tidies
     * itself). Not the whole column: tests still running send units over their neighbours' arenas on purpose
     * (TerrainGameTests' formation at y 280, the bone dragon's flight, the Admiral 60 out), and those must live on.
     * Returns how many it removed.
     */
    static int sweep(GameTestInfo info) {
        try {
            net.minecraft.world.phys.AABB b = info.getStructureBounds();
            // the bounds run from the first block's corner to the LAST block's corner: one more for the last rows
            net.minecraft.world.phys.AABB arena = new net.minecraft.world.phys.AABB(b.minX, b.minY, b.minZ,
                b.maxX + 1, b.maxY + 1, b.maxZ + 1);
            ServerLevel level = info.getLevel();
            Footprint pads = PADS.get(info);
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(arena.minX, level.getMinBuildHeight(),
                arena.minZ, arena.maxX, level.getMaxBuildHeight(), arena.maxZ);
            int[] span = pads == null ? null : pads.span();
            if (span != null)
                area = area.minmax(new net.minecraft.world.phys.AABB(span[0], span[1], span[2],
                    span[3] + 1, span[4] + 1 + Footprint.HEADROOM, span[5] + 1));
            int n = 0;
            for (Entity e : level.getEntitiesOfClass(Entity.class, area,
                    e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                // RED PROOF (throwaway, never merge): the old whole-column sweep, bottom to top, every entity in it
                if (e.getX() >= arena.minX && e.getX() < arena.maxX && e.getZ() >= arena.minZ && e.getZ() < arena.maxZ) {
                    e.discard();
                    n++;
                }
            }
            if (n > 0)
                Ends.LOG.info("[TestCleanup] swept {}: removed {} entities left in its arena or on its pads",
                    info.getTestName(), n);
            return n;
        } catch (RuntimeException e) {
            Ends.LOG.error("[TestCleanup] could not sweep {}", info.getTestName(), e);
            return 0;
        }
    }

    /**
     * The columns a test's pads stand on, apart from Minecraft (TestCleanupTest runs it): per x/z, the lowest and the
     * highest block set there. A unit is on the pad when it stands in such a column between the lowest block and
     * {@link #HEADROOM} above the highest.
     */
    static final class Footprint {
        /** Blocks above a pad's top that still count as on it: a unit standing there, or one hopping. */
        static final int HEADROOM = 4;
        private final Map<Long, int[]> columns = new HashMap<>();

        private static long key(int x, int z) {
            return ((long) x << 32) | (z & 0xffffffffL);
        }

        void add(int x, int y, int z) {
            int[] r = columns.get(key(x, z));
            if (r == null)
                columns.put(key(x, z), new int[] { y, y });
            else {
                r[0] = Math.min(r[0], y);
                r[1] = Math.max(r[1], y);
            }
        }

        /** Every column of the box from one corner to the other (corners included), from its bottom to its top. */
        void addBox(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    add(x, Math.min(y0, y1), z);
                    add(x, Math.max(y0, y1), z);
                }
        }

        boolean covers(double x, double y, double z) {
            int[] r = columns.get(key((int) Math.floor(x), (int) Math.floor(z)));
            return r != null && y >= r[0] && y < r[1] + 1 + HEADROOM;
        }

        /** minX, minY, minZ, maxX, maxY, maxZ of every block set (blocks, corners included), or null if none. */
        int[] span() {
            if (columns.isEmpty())
                return null;
            int[] s = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
            for (Map.Entry<Long, int[]> c : columns.entrySet()) {
                int x = (int) (c.getKey() >> 32), z = (int) (long) c.getKey();
                s[0] = Math.min(s[0], x);
                s[2] = Math.min(s[2], z);
                s[3] = Math.max(s[3], x);
                s[5] = Math.max(s[5], z);
                s[1] = Math.min(s[1], c.getValue()[0]);
                s[4] = Math.max(s[4], c.getValue()[1]);
            }
            return s;
        }
    }

    /** How many steps are waiting for tests still running (0 between batches; for a proof test). */
    static int pending() {
        return ENDS.pending();
    }

    /** The helper's GameTestInfo, found by its type (not its name in this mapping), as GameTestShards finds batches. */
    static GameTestInfo info(GameTestHelper helper) {
        try {
            if (infoField == null) {
                for (Field f : GameTestHelper.class.getDeclaredFields())
                    if (f.getType() == GameTestInfo.class) {
                        f.setAccessible(true);
                        infoField = f;
                        break;
                    }
                if (infoField == null)
                    throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
            }
            return (GameTestInfo) infoField.get(helper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[TestCleanup] cannot read the test's GameTestInfo", e);
        }
    }

    /**
     * The bookkeeping, apart from Minecraft (TestCleanupTest runs it without a server): steps per key, run last in
     * first out by {@link #end}, each once; the first step for a key also runs {@code hook} (hanging the listener).
     */
    static final class Ends<K> {
        // its own logger, not ReignOfNether's: loading the mod class would set up the mod in a plain JUnit run
        private static final Logger LOG = LogManager.getLogger("TestCleanup");
        private final Map<K, Deque<Once>> steps = new IdentityHashMap<>();

        Runnable add(K key, Runnable undo, Runnable hook) {
            Once once = new Once(undo);
            Deque<Once> list = steps.get(key);
            if (list == null) {
                list = new ArrayDeque<>();
                steps.put(key, list);
                hook.run();
            }
            list.push(once);
            return once;
        }

        /** Runs every step of {@code key} not yet run, newest first; a step that throws is logged, the rest still run. */
        void end(K key) {
            Deque<Once> list = steps.get(key);
            if (list == null)
                return;
            // a step that adds a step (for this key) lands on the same list and runs in this loop
            while (!list.isEmpty()) {
                Once o = list.pop();
                try {
                    o.run();
                } catch (Throwable t) {
                    LOG.error("[TestCleanup] an undo step of {} threw", key, t);
                }
            }
            steps.remove(key);
        }

        int pending() {
            int n = 0;
            for (Deque<Once> list : steps.values())
                for (Once o : list)
                    if (!o.ran)
                        n++;
            return n;
        }
    }

    private static final class Once implements Runnable {
        private final Runnable undo;
        private boolean ran = false;

        Once(Runnable undo) {
            this.undo = undo;
        }

        @Override
        public void run() {
            if (ran)
                return;
            ran = true;
            undo.run();
        }
    }
}
