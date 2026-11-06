package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A test's arena is ready for entities when the test starts (Command Center Eris.15, Nov 6). The framework waits for a
 * structure's chunks to reach FULL (StructureUtilsMixin), but a chunk at FULL is not yet one whose ENTITY section is
 * loaded and ticking: a unit added there stays out of level.getEntity and getEntitiesOfClass until it is, and every
 * scan misses it - an order finds no unit to command, a burst no one to hurt, a mount search no spider (#517's green
 * 42136690892: "in level false", 5 of 8; #507's greens: autofind_mounts_the_nearest_mountable 4 and 5 of 8; #507's CI:
 * hero_xp_pays_double_for_a_neutral_kill, "entity ticking there false"). It got far worse with #488's wider arena gaps
 * (GameTestLayout.GAP), which spread a batch over many more fresh chunks.
 * <p>
 * In a batch of its own, as hero_xp_pays_double_for_a_neutral_kill and the death-burst tests are: a full-size batch
 * spends long enough spawning its structures that the ring two chunks round each arena (which a chunk needs FULL to be
 * entity-ticking, and which the 5x5 forced grid does not wait for) has loaded by the first tick, so it never showed
 * the race (flake-stress 42141743756: 0 of 3 in the default batch, while hero_xp in its own batch read "entity ticking
 * there false" on 1 of 3). A small batch starts right after its spawn, on chunks #488's wider gaps leave fresh.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArenaEntitiesGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * At the test's first tick: every chunk of the arena has its entities loaded and is entity-ticking, and a unit
     * spawned then is in the level's lookup (level.getEntity) and in a box scan (getEntitiesOfClass) at once.
     */
    @GameTest(template = ARENA, batch = "arena_entities_are_ready_when_a_test_starts", timeoutTicks = 40)
    public static void arena_entities_are_ready_when_a_test_starts(GameTestHelper helper) {
        TerrainGameTests.finish(helper, readyAtStart(helper, "gametest_arena_entities"));
    }

    /**
     * The same check in the default batch (a full-size one), the second case (Command Center Eris.16): it did not show
     * the race before the gate (42141743756, 0 of 3) and must stay clean with it.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void arena_entities_are_ready_in_a_full_batch(GameTestHelper helper) {
        TerrainGameTests.finish(helper, readyAtStart(helper, "gametest_arena_entities_full"));
    }

    static List<String> readyAtStart(GameTestHelper helper, String owner) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        BlockPos a = helper.absolutePos(BlockPos.ZERO), b = helper.absolutePos(new BlockPos(15, 0, 15));
        int cx0 = Math.min(a.getX(), b.getX()) >> 4, cx1 = Math.max(a.getX(), b.getX()) >> 4;
        int cz0 = Math.min(a.getZ(), b.getZ()) >> 4, cz1 = Math.max(a.getZ(), b.getZ()) >> 4;
        int chunks = 0;
        List<String> notReady = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                chunks++;
                boolean loaded = level.areEntitiesLoaded(ChunkPos.asLong(cx, cz));
                boolean ticking = level.isPositionEntityTicking(new BlockPos((cx << 4) + 8, a.getY(), (cz << 4) + 8));
                if (!loaded || !ticking)
                    notReady.add(cx + "," + cz + " (entities loaded " + loaded + ", entity-ticking " + ticking + ")");
            }
        if (!notReady.isEmpty())
            problems.add(notReady.size() + " of the arena's " + chunks + " chunks not ready for entities at the test's start: "
                + String.join(" ", notReady));
        Mob m = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 8, 8);
        if (m == null) {
            problems.add("could not spawn the unit");
        } else {
            boolean inLevel = level.getEntity(m.getId()) == m;
            boolean scanned = level.getEntitiesOfClass(Mob.class, new AABB(m.blockPosition()).inflate(2)).contains(m);
            m.discard();
            if (!inLevel || !scanned)
                problems.add("a unit spawned at the test's start is not in the level: getEntity " + inLevel
                    + ", getEntitiesOfClass " + scanned + " (at " + m.blockPosition().toShortString() + ")");
        }
        return problems;
    }

    /** A ticket that loads a chunk to FULL (level 33) and no further: FULL, never entity-ticking (no ticket at 31 or less). */
    static final TicketType<ChunkPos> FULL_ONLY = TicketType.create("gametest_full_only", Comparator.comparingLong(ChunkPos::toLong));

    /**
     * The gate's readiness check against the failure itself, deterministically (Command Center Eris.17, option b): a
     * fresh far chunk held at FULL by a level-33 ticket alone - what the framework's structure wait counts as loaded -
     * is not entity-ticking, so a unit added there never ticks (hero_xp's "entity ticking there false": its orbs never
     * flew). {@link GameTestChunks#firstNotEntityReady} must call that chunk not ready. Then a FORCED ticket (what
     * StructureUtilsMixin adds, level 31) and its ring loaded: the check must flip to ready, and the same unit ticks.
     * Whether a real test would start in the first state is load-dependent (a small batch in a full shard); this pins
     * that the gate's check sees exactly that state.
     */
    @GameTest(template = ARENA, batch = "arena_entities_probe", timeoutTicks = 400)
    public static void a_chunk_that_is_full_but_not_entity_ticking_is_not_ready_for_entities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int cx = (origin.getX() >> 4) - 256, cz = (origin.getZ() >> 4) - 256;   // ~4000 blocks off: never loaded before
        ChunkPos pos = new ChunkPos(cx, cz);
        List<long[]> one = List.of(new long[] { cx, cz });
        List<String> problems = new ArrayList<>();
        level.getChunkSource().addRegionTicket(FULL_ONLY, pos, 0, pos);
        // run once whenever the test ends (a timeout too), so the far chunk's tickets never outlive it
        Runnable cleanup = TestCleanup.atEnd(helper, () -> {
            level.getChunkSource().removeRegionTicket(FULL_ONLY, pos, 0, pos);
            GameTestChunks.releaseAll(level, one);
        });
        GameTestChunks.Wait full = GameTestChunks.await(level, one, GameTestChunks.WAIT_MS, "probe chunk");
        if (!full.complete()) {
            cleanup.run();
            helper.fail("the probe chunk never loaded to FULL (" + full.summary() + ")");
            return;
        }
        int y = level.getChunk(cx, cz).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 8, 8) + 1;
        Mob m = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (m != null)
            TestCleanup.entities(helper, List.of(m));
        if (m == null) {
            cleanup.run();
            helper.fail("could not create the unit");
            return;
        }
        m.moveTo((cx << 4) + 8.5, y, (cz << 4) + 8.5, 0, 0);
        level.addFreshEntity(m);
        helper.runAfterDelay(10, () -> {
            boolean ticking = level.isPositionEntityTicking(m.blockPosition());
            if (ticking)
                problems.add("setup: the FULL-only chunk is entity-ticking (nothing tested)");
            else if (GameTestChunks.firstNotEntityReady(level, one, y) == null)
                problems.add("a chunk at FULL but not entity-ticking read ready for entities (the unit there ticked "
                    + m.tickCount + " times in 10 ticks)");
            int before = m.tickCount;
            GameTestChunks.hold(level, cx, cz);   // what the structure load adds: level 31, the ring 2 chunks out
            try {
                GameTestChunks.awaitRingOrFail(level, one, "probe");
            } catch (RuntimeException e) {
                problems.add("the ring round the probe chunk never loaded: " + e.getMessage());
            }
            helper.runAfterDelay(40, () -> {
                if (GameTestChunks.firstNotEntityReady(level, one, y) != null)
                    problems.add("with a FORCED ticket and its ring loaded, the chunk still read not ready");
                if (m.tickCount <= before)
                    problems.add("the unit never ticked once the chunk was entity-ready (tickCount " + m.tickCount + ")");
                m.discard();
                cleanup.run();
                TerrainGameTests.finish(helper, problems);
            });
        });
    }
}
