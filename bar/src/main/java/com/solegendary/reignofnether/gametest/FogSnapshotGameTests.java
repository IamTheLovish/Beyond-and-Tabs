package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogChunkSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The fog snapshot (FogChunkSnapshot) is captured over several ticks with a per-tick budget instead of in one tick
 * (that froze the server 14-23 s at match start). It must hold exactly what the old one-tick capture held: the same
 * chunks, the same bytes, and a chunk about to change or about to be served before its turn is captured first.
 * The two tests share FogChunkSnapshot's static state, so the second starts its capture only after the first (which
 * runs inside its first tick) is done.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogSnapshotGameTests {

    static final String BATCH = "fog_snapshot";
    /** Longest a capture step may hold the server thread: the budget plus one chunk's packet build. */
    static final long STEP_LIMIT_NS = FogChunkSnapshot.TICK_BUDGET_NS + 30_000_000L;

    /** The old capture, kept as the reference: synchronous load + packet + encode for every chunk, in one go. */
    static Map<ChunkPos, byte[]> oneTickCapture(ServerLevel level, int minCx, int maxCx, int minCz, int maxCz) {
        Map<ChunkPos, byte[]> out = new LinkedHashMap<>();
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, true);
                if (chunk == null) continue;
                out.put(new ChunkPos(cx, cz), encode(new ClientboundLevelChunkWithLightPacket(
                        chunk, level.getLightEngine(), null, null)));
            }
        return out;
    }

    static byte[] encode(ClientboundLevelChunkWithLightPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    /** The chunk part of an encoded packet (position, heightmaps, sections, block entities), without the light. */
    static byte[] chunkPart(byte[] encoded) {
        FriendlyByteBuf in = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(in);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeInt(packet.getX());
            buf.writeInt(packet.getZ());
            packet.getChunkData().write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    /**
     * Every reference chunk has a file with the same bytes ({@code lightToo}) or the same chunk part (blocks,
     * heightmaps, block entities); returns the problems.
     */
    static List<String> compareFiles(Map<ChunkPos, byte[]> reference, boolean lightToo) {
        List<String> problems = new ArrayList<>();
        int same = 0, sameBlocks = 0;
        for (var e : reference.entrySet()) {
            Path f = FogChunkSnapshot.fileFor(e.getKey());
            try {
                if (f == null || !Files.isRegularFile(f)) {
                    problems.add("no snapshot file for " + e.getKey());
                    continue;
                }
                byte[] got = Files.readAllBytes(f);
                boolean all = Arrays.equals(got, e.getValue());
                boolean blocks = all || Arrays.equals(chunkPart(got), chunkPart(e.getValue()));
                if (all) same++;
                if (blocks) sameBlocks++;
                if (!blocks)
                    problems.add("blocks of " + e.getKey() + " differ from the one-tick capture");
                else if (lightToo && !all)
                    problems.add("light of " + e.getKey() + " differs from the one-tick capture");
            } catch (Exception ex) {
                problems.add("reading " + f + ": " + ex);
            }
            if (problems.size() > 5) break;
        }
        ReignOfNether.LOGGER.info("[FogSnapshotTest] {} of {} chunks byte-identical to the one-tick capture, "
                + "{} with identical blocks", same, reference.size(), sameBlocks);
        return problems;
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        FogChunkSnapshot.clear();
        FogChunkSnapshot.awaitWrites();
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }

    /**
     * Loaded chunks round the test: the budgeted capture writes the same bytes as the one-tick capture, every step
     * stays under the budget, and a chunk about to change / about to be served before its turn is captured first.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40, batch = BATCH)
    public static void the_budgeted_snapshot_equals_the_one_tick_capture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        int cx0 = origin.getX() >> 4, cz0 = origin.getZ() >> 4;
        int minCx = cx0 - 4, maxCx = cx0 + 4, minCz = cz0 - 4, maxCz = cz0 + 4;

        long t0 = System.nanoTime();
        Map<ChunkPos, byte[]> reference = oneTickCapture(level, minCx, maxCx, minCz, maxCz);
        long oldNs = System.nanoTime() - t0;

        FogChunkSnapshot.beginCapture(level, minCx, maxCx, minCz, maxCz);
        long maxStep = 0, total = 0;
        int steps = 0;
        while (FogChunkSnapshot.isCapturing() && steps < 10_000) {
            long s = System.nanoTime();
            FogChunkSnapshot.step(FogChunkSnapshot.TICK_BUDGET_NS);
            long took = System.nanoTime() - s;
            maxStep = Math.max(maxStep, took);
            total += took;
            steps++;
        }
        FogChunkSnapshot.awaitWrites();
        ReignOfNether.LOGGER.info(String.format(Locale.ROOT, "[FogSnapshotTest] %d loaded chunks: one-tick capture "
                + "(no file writes) %.1f ms; budgeted capture %d steps, %.1f ms on the server thread, longest step %.1f ms",
                reference.size(), oldNs / 1e6, steps, total / 1e6, maxStep / 1e6));
        if (FogChunkSnapshot.isCapturing())
            problems.add("capture of loaded chunks did not finish in " + steps + " steps");
        if (maxStep > STEP_LIMIT_NS)
            problems.add(String.format(Locale.ROOT, "a capture step took %.1f ms (limit %.1f)",
                    maxStep / 1e6, STEP_LIMIT_NS / 1e6));
        if (reference.size() != (maxCx - minCx + 1) * (maxCz - minCz + 1))
            problems.add("reference captured " + reference.size() + " chunks");
        problems.addAll(compareFiles(reference, true));
        if (!FogChunkSnapshot.hasAny())
            problems.add("hasAny() false after a capture");

        // a chunk is captured before its first change, and a waiting chunk is captured when it is served
        ChunkPos changed = new ChunkPos(cx0, cz0), served = new ChunkPos(cx0 + 1, cz0);
        Map<ChunkPos, byte[]> before = oneTickCapture(level, cx0, cx0 + 1, cz0, cz0);
        FogChunkSnapshot.beginCapture(level, minCx, maxCx, minCz, maxCz);
        if (!FogChunkSnapshot.hasAny())
            problems.add("hasAny() false while a capture is running (a first join would restart it)");
        var oldState = level.getBlockState(origin);
        level.setBlock(origin, oldState.is(Blocks.GOLD_BLOCK) ? Blocks.IRON_BLOCK.defaultBlockState()
                : Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        ClientboundLevelChunkWithLightPacket afterChange = FogChunkSnapshot.get(changed);
        if (afterChange == null)
            problems.add("a chunk changed before its turn has no snapshot");
        else if (!Arrays.equals(encode(afterChange), before.get(changed)))
            problems.add("a chunk changed before its turn holds the changed state, not the pre-match one");
        ClientboundLevelChunkWithLightPacket servedPacket = FogChunkSnapshot.get(served);
        if (servedPacket == null)
            problems.add("a chunk served before its turn has no snapshot (a dark player would get nothing)");
        else if (!Arrays.equals(encode(servedPacket), before.get(served)))
            problems.add("a chunk served before its turn differs from the one-tick capture");
        level.setBlock(origin, oldState, 2);
        FogChunkSnapshot.finishNow();
        Map<ChunkPos, byte[]> changedRef = new LinkedHashMap<>();
        changedRef.put(changed, before.get(changed));
        problems.addAll(compareFiles(changedRef, true));
        finish(helper, problems);
    }

    /**
     * Chunks that are not loaded: the capture asks the chunk system for them and builds each when it arrives, over
     * real server ticks (PlayerServerEvents drives it); no step goes over the budget, and the files hold the same blocks,
     * heightmaps and block entities as the one-tick capture of the same chunks. Light is not compared here: a chunk just
     * loaded at level 33 keeps gaining light from neighbours that load after it, so the light of an unloaded chunk's
     * snapshot depended on load order with the old capture too (it loaded the box one chunk at a time); the loaded-area
     * test above compares light byte for byte.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 3000, batch = BATCH)
    public static void the_snapshot_of_unloaded_chunks_loads_them_without_blocking_a_tick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int minCx = (origin.getX() >> 4) + 96, minCz = (origin.getZ() >> 4) + 96;
        int maxCx = minCx + 5, maxCz = minCz + 5;
        long[] startTick = {-1};
        List<String> problems = new ArrayList<>();
        // a non-ticking hold (level 33, as the capture's own ticket) so the reference reads the same loaded chunks
        TicketType<ChunkPos> hold = TicketType.FORCED;
        // start once the other test of the batch (one tick) is done with the shared snapshot state
        helper.runAtTickTime(5, () -> {
            for (int cx = minCx; cx <= maxCx; cx++)
                for (int cz = minCz; cz <= maxCz; cz++) {
                    if (level.getChunkSource().getChunkNow(cx, cz) != null)
                        problems.add("chunk " + cx + "," + cz + " was already loaded; the test needs unloaded ones");
                }
            FogChunkSnapshot.beginCapture(level, minCx, maxCx, minCz, maxCz);
            for (int cx = minCx; cx <= maxCx; cx++)
                for (int cz = minCz; cz <= maxCz; cz++) {
                    ChunkPos p = new ChunkPos(cx, cz);
                    level.getChunkSource().addRegionTicket(hold, p, 0, p);
                }
            startTick[0] = helper.getTick();
        });
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || startTick[0] < 0 || FogChunkSnapshot.isCapturing())
                return;
            done[0] = true;
            long maxStep = FogChunkSnapshot.lastCaptureMaxStepNs();
            FogChunkSnapshot.awaitWrites();
            Map<ChunkPos, byte[]> reference = oneTickCapture(level, minCx, maxCx, minCz, maxCz);
            ReignOfNether.LOGGER.info(String.format(Locale.ROOT, "[FogSnapshotTest] %d unloaded chunks captured over "
                    + "%d ticks, longest server-thread step %.1f ms", reference.size(),
                    helper.getTick() - startTick[0], maxStep / 1e6));
            List<String> all = new ArrayList<>(problems);
            if (maxStep > STEP_LIMIT_NS)
                all.add(String.format(Locale.ROOT, "a capture step took %.1f ms (limit %.1f)",
                        maxStep / 1e6, STEP_LIMIT_NS / 1e6));
            all.addAll(compareFiles(reference, false));
            for (int cx = minCx; cx <= maxCx; cx++)
                for (int cz = minCz; cz <= maxCz; cz++) {
                    ChunkPos p = new ChunkPos(cx, cz);
                    level.getChunkSource().removeRegionTicket(hold, p, 0, p);
                }
            FogChunkSnapshot.clear();
            FogChunkSnapshot.awaitWrites();
            if (all.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", all));
        });
    }
}
