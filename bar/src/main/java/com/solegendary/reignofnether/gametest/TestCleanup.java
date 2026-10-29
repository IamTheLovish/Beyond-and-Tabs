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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        BlockUndo b = new BlockUndo(helper.getLevel());
        atEnd(helper, b::restore);
        return b;
    }

    /** What {@link #blocks} hands out: set blocks through it, it remembers what stood there. */
    public static final class BlockUndo {
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> was = new LinkedHashMap<>();

        private BlockUndo(ServerLevel level) {
            this.level = level;
        }

        public void set(BlockPos pos, BlockState state) {
            BlockPos at = pos.immutable();
            if (!was.containsKey(at))
                was.put(at, level.getBlockState(at));
            level.setBlock(at, state, 3);
        }

        /**
         * Remembers every block of the box from {@code a} to {@code b} (corners included) as it stands now, to put
         * back at the end: for a box where the game sets blocks of its own (a stamped patch, a placed building) as
         * well as the test. Call it before anything is built there.
         */
        public BlockUndo region(BlockPos a, BlockPos b) {
            for (BlockPos p : BlockPos.betweenClosed(a, b)) {
                BlockPos at = p.immutable();
                if (!was.containsKey(at))
                    was.put(at, level.getBlockState(at));
            }
            return this;
        }

        /** Puts back every block this set, last set first; then it remembers nothing. */
        public void restore() {
            List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(was.entrySet());
            for (int i = list.size() - 1; i >= 0; i--)
                level.setBlock(list.get(i).getKey(), list.get(i).getValue(), 3);
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
     * Removes every entity but players from a failed test's own column: its arena's x/z, from the bottom of the world to
     * the top, so its sky pad's units go too. Vanilla removes no test's entities when it ends (only the template box,
     * once a later test's structure is placed there), and a test that fails leaves the units it would have discarded on
     * a pass: they keep fighting, and pick neighbouring arenas' units as targets ~21 blocks away. Called by
     * GameTestShards' reporter for every failed test (pass: the test tidies itself). A neighbour's unit that strayed
     * into this column goes too - it was already where no test put it.
     */
    static void sweepColumn(GameTestInfo info) {
        try {
            net.minecraft.world.phys.AABB box = info.getStructureBounds();
            ServerLevel level = info.getLevel();
            net.minecraft.world.phys.AABB column = new net.minecraft.world.phys.AABB(box.minX, level.getMinBuildHeight(),
                box.minZ, box.maxX, level.getMaxBuildHeight(), box.maxZ);
            int n = 0;
            for (Entity e : level.getEntitiesOfClass(Entity.class, column,
                    e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                e.discard();
                n++;
            }
            if (n > 0)
                Ends.LOG.info("[TestCleanup] {} failed: removed {} entities left in its column", info.getTestName(), n);
        } catch (RuntimeException e) {
            Ends.LOG.error("[TestCleanup] could not sweep the column of {}", info.getTestName(), e);
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
