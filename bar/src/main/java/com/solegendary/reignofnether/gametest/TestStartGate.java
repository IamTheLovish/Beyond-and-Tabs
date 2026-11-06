package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * Every GameTest's first tick waits until each chunk of its structure is ready for entities
 * ({@link GameTestChunks#firstNotEntityReady}): entity section loaded and entity-ticking. Called once per tick from
 * GameTestInfoMixin while the test would start; the test's own clock (its tick numbers, runAfterDelay, timeoutTicks)
 * only begins once this lets it, so a test never spends its timeout on a half-loaded arena.
 * <p>
 * Why (claude/plan-arena-entity-loading-race-nov6.md): StructureUtilsMixin waits for the 5x5 FORCED chunks to reach
 * FULL only. A unit spawned into a chunk that is FULL but not entity-ready is filed in its section but kept out of
 * level.getEntity / getEntitiesOfClass and never ticks until the chunk is promoted - a tick or more later, longer on
 * a loaded runner (#517's "in level false", #507's autofind scan finding no spider, hero_xp's "entity ticking there
 * false"). #488's wider arena gaps spread a batch over more fresh chunks and made it common.
 * <p>
 * One shared entry for every test, no per-test waits: the per-test ArenaReady.Timeline helpers (#556, #566) keep
 * working and simply find their units ticking on the first tick. Only the headless GameTest server
 * ({@link GameTestChunks#asyncStructureLoad}); a give-up (no progress, see {@link StartGateProgress}) lets the test
 * start as before and logs a {@code [ChunkWait] gametest-start-gate} line with what was not ready.
 */
public final class TestStartGate {

    /** Waits this long or longer are logged (shorter ones are the normal case and would flood the filtered log). */
    static final int LOG_FROM_TICKS = 20;

    private TestStartGate() { }

    /** The state one test carries between ticks (held by GameTestInfoMixin). */
    public static final class Gate {
        final StartGateProgress progress = new StartGateProgress();
        List<long[]> chunks;
        public boolean open;
    }

    /** True: hold the test's first tick back this tick. Opens the gate (for good) otherwise. */
    public static boolean hold(GameTestInfo info, ServerLevel level, Gate gate) {
        if (gate.chunks == null) {
            BlockPos sbPos = info.getStructureBlockPos();
            if (sbPos == null || !(level.getBlockEntity(sbPos) instanceof StructureBlockEntity sbe)) {
                gate.open = true;   // nothing to wait for that we can name: start as vanilla does
                return false;
            }
            BoundingBox box = StructureUtils.getStructureBoundingBox(sbe);
            gate.chunks = GameTestChunks.chunksOf(box);
        }
        int y = info.getStructureBlockPos().getY();
        int ready = 0;
        long[] first = null;
        for (long[] c : gate.chunks)
            if (GameTestChunks.entityReady(level, (int) c[0], (int) c[1], y))
                ready++;
            else if (first == null)
                first = c;
        long now = System.nanoTime();
        StartGateProgress.Verdict v = gate.progress.update(ready, gate.chunks.size(), now);
        if (v == StartGateProgress.Verdict.HOLD)
            return true;
        gate.open = true;
        int held = gate.progress.ticksHeld();
        if (v == StartGateProgress.Verdict.READY) {
            if (held >= LOG_FROM_TICKS)
                ReignOfNether.LOGGER.info("[ChunkWait] gametest-start-gate {}: {} structure chunks entity-ready after {} ticks ({} ms)",
                    info.getTestName(), gate.chunks.size(), held, gate.progress.millisSince(now));
        } else {
            ReignOfNether.LOGGER.warn("[ChunkWait] gametest-start-gate {}: starting anyway ({}) with {} of {} structure chunks"
                    + " not ready for entities after {} ticks ({} ms); first {},{}: entities loaded {}, entity-ticking {},"
                    + " FULL {}", info.getTestName(), v == StartGateProgress.Verdict.STALLED ? "no progress" : "wall-time cap",
                gate.chunks.size() - ready, gate.chunks.size(), held, gate.progress.millisSince(now), first[0], first[1],
                level.areEntitiesLoaded(ChunkPos.asLong((int) first[0], (int) first[1])),
                level.isPositionEntityTicking(new BlockPos(((int) first[0] << 4) + 8, y, ((int) first[1] << 4) + 8)),
                level.getChunkSource().getChunkNow((int) first[0], (int) first[1]) != null);
        }
        return false;
    }
}
