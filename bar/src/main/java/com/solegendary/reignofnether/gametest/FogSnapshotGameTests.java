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
import net.minecraft.world.level.levelgen.Heightmap;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The fog snapshot (FogChunkSnapshot) is captured over several ticks with a per-tick budget instead of in one tick
 * (that froze the server 14-23 s at match start). It must hold exactly what the old one-tick capture held: the same
 * chunks, the same bytes, and a chunk about to change or about to be served before its turn is captured first.
 * The two tests share FogChunkSnapshot's static state, so the second starts its capture only after the first is done
 * ({@link #loadedTestDone}).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogSnapshotGameTests {

    static final String BATCH = "fog_snapshot";
    /** Longest a capture step may hold the server thread: the budget plus one chunk's packet build. */
    static final long STEP_LIMIT_NS = FogChunkSnapshot.TICK_BUDGET_NS + 30_000_000L;
    /** Set when the loaded-chunks test is done with the shared snapshot state (the unloaded-chunks test waits for it). */
    static final AtomicBoolean loadedTestDone = new AtomicBoolean(false);
    /** Level compareFiles reads live chunks from, to say on a light mismatch which side the world now matches. */
    static ServerLevel diagLevel = null;

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
                    problems.add("blocks of " + e.getKey() + " differ from the one-tick capture" + blockDiag(got, e.getValue(), e.getKey()));
                else if (lightToo && !all) {
                    // light moved after the reference was taken (in a busy world light work keeps arriving after the
                    // test's barriers, with no block of the box changing): the file must then hold the chunk's light as
                    // it is now. The diagnosis says which side the live chunk matches.
                    ServerLevel lv = diagLevel;
                    LevelChunk c = lv == null ? null : lv.getChunkSource().getChunkNow(e.getKey().x, e.getKey().z);
                    byte[] live = c == null ? null
                            : encode(new ClientboundLevelChunkWithLightPacket(c, lv.getLightEngine(), null, null));
                    boolean fileIsLive = live != null && Arrays.equals(got, live);
                    String now = live == null ? " (chunk not loaded now)" : " (file " + (fileIsLive ? "=" : "!=")
                            + " live, reference " + (Arrays.equals(e.getValue(), live) ? "=" : "!=") + " live)";
                    if (fileIsLive)
                        ReignOfNether.LOGGER.info("[FogSnapshotTest] light of {} moved after the reference{}",
                                e.getKey(), now);
                    else
                        problems.add("light of " + e.getKey() + " differs from the one-tick capture" + now);
                }
            } catch (Exception ex) {
                problems.add("reading " + f + ": " + ex);
            }
            if (problems.size() > 5) break;
        }
        ReignOfNether.LOGGER.info("[FogSnapshotTest] {} of {} chunks byte-identical to the one-tick capture, "
                + "{} with identical blocks", same, reference.size(), sameBlocks);
        return problems;
    }

    /** Which chunk parts of file and reference differ, and which of them the live chunk's blocks match now. */
    static String blockDiag(byte[] file, byte[] ref, ChunkPos pos) {
        try {
            var a = new ClientboundLevelChunkWithLightPacket(new FriendlyByteBuf(Unpooled.wrappedBuffer(file))).getChunkData();
            var b = new ClientboundLevelChunkWithLightPacket(new FriendlyByteBuf(Unpooled.wrappedBuffer(ref))).getChunkData();
            List<String> parts = new ArrayList<>();
            if (!a.getHeightmaps().equals(b.getHeightmaps())) parts.add("heightmaps");
            FriendlyByteBuf sa = a.getReadBuffer(), sb = b.getReadBuffer();
            byte[] xa = new byte[sa.readableBytes()], xb = new byte[sb.readableBytes()];
            sa.getBytes(sa.readerIndex(), xa);
            sb.getBytes(sb.readerIndex(), xb);
            if (!Arrays.equals(xa, xb)) parts.add("sections");
            List<String> beA = new ArrayList<>(), beB = new ArrayList<>();
            a.getBlockEntitiesTagsConsumer(pos.x, pos.z).accept((p, t, tag) -> beA.add(p + " " + t + " " + tag));
            b.getBlockEntitiesTagsConsumer(pos.x, pos.z).accept((p, t, tag) -> beB.add(p + " " + t + " " + tag));
            if (!beA.equals(beB)) {
                List<String> onlyFile = new ArrayList<>(beA), onlyRef = new ArrayList<>(beB);
                onlyFile.removeAll(beB);
                onlyRef.removeAll(beA);
                parts.add("block entities (file only " + onlyFile + ", reference only " + onlyRef + ")");
            }
            String live = "";
            ServerLevel lv = diagLevel;
            LevelChunk c = lv == null ? null : lv.getChunkSource().getChunkNow(pos.x, pos.z);
            if (c != null) {
                byte[] l = chunkPart(encode(new ClientboundLevelChunkWithLightPacket(c, lv.getLightEngine(), null, null)));
                live = "; file " + (Arrays.equals(chunkPart(file), l) ? "=" : "!=") + " live, reference "
                        + (Arrays.equals(chunkPart(ref), l) ? "=" : "!=") + " live";
            }
            return " (" + String.join(", ", parts) + live + ")";
        } catch (Exception ex) {
            return " (diagnosis failed: " + ex + ")";
        }
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        FogChunkSnapshot.clear();
        FogChunkSnapshot.awaitWrites();
        loadedTestDone.set(true);
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }

    /**
     * Loaded chunks round the test: the budgeted capture writes the same bytes as the one-tick capture, light included,
     * every step stays under the budget, and a chunk about to change / about to be served before its turn is captured
     * first. Light is computed on the light engine's worker behind the block changes (the batch set-up just placed the
     * arenas; on a busy CI host that light is still spreading for a few ticks), so the reference is taken once the box's
     * light has settled - the same condition the capture waits for (FogChunkSnapshot.lightBarrier) - and the capture
     * then runs over real server ticks (PlayerServerEvents drives it) while nothing in the box changes.
     * <p>
     * The 9x9 box is far west of the test grid (as capture_bounded_when_barrier_hangs's), held with a ring round it by
     * non-ticking tickets (level 33): nothing ticks in it or next to it. It used to be the box round the arena, which
     * took in the arenas of the batch before (stress_8v8): in CI runs 40452699161, 40469592779 and 40472451498 blocks
     * changing in that arena after the reference moved the light of the box's corner chunks again after their files
     * were built ("file != live, reference != live"). The chunk changed before its turn is changed on the top of a
     * column, so its heightmaps change too: the snapshot must hold the heightmaps from before the change (the packet
     * shares the chunk's heightmap arrays until it is encoded, CI run 40469592779: "heightmaps of [22, 52]").
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 2400, batch = BATCH)
    public static void the_budgeted_snapshot_equals_the_one_tick_capture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        diagLevel = level;
        List<String> problems = new ArrayList<>();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        int cx0 = (origin.getX() >> 4) - 200, cz0 = origin.getZ() >> 4;
        int minCx = cx0 - 4, maxCx = cx0 + 4, minCz = cz0 - 4, maxCz = cz0 + 4;
        // the block changed before its chunk's turn: the first free block over a column of chunk (cx0, cz0)
        int topX = (cx0 << 4) + 1, topZ = (cz0 << 4) + 1;
        // keep the box and a ring round it loaded (FULL, lit) across the ticks the test spans: the box's light then
        // depends on loaded chunks only
        TicketType<ChunkPos> hold = TicketType.FORCED;
        for (int cx = minCx - 1; cx <= maxCx + 1; cx++)
            for (int cz = minCz - 1; cz <= maxCz + 1; cz++) {
                ChunkPos p = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(hold, p, 0, p);
            }
        Runnable release = () -> {
            for (int cx = minCx - 1; cx <= maxCx + 1; cx++)
                for (int cz = minCz - 1; cz <= maxCz + 1; cz++) {
                    ChunkPos p = new ChunkPos(cx, cz);
                    level.getChunkSource().removeRegionTicket(hold, p, 0, p);
                }
        };
        // phase 0: wait for the box to be loaded and light-correct; 1-2: two light barrier rounds over the box (the
        // second catches light work queued while the first ran); 3: reference + capture; 4: capture running
        int[] phase = {0};
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] settled = new CompletableFuture[1];
        Map<ChunkPos, byte[]> reference = new LinkedHashMap<>();
        long[] t = {0, 0, 0}; // one-tick capture ns, capture start tick, settle start tick
        helper.onEachTick(() -> {
            if (phase[0] == 0) {
                for (int cx = minCx - 1; cx <= maxCx + 1; cx++)
                    for (int cz = minCz - 1; cz <= maxCz + 1; cz++) {
                        LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                        if (c == null || !c.isLightCorrect()) return;
                    }
                t[2] = helper.getTick();
                phase[0] = 1;
            }
            if (phase[0] == 1 || phase[0] == 2) {
                if (settled[0] != null && !settled[0].isDone()) return; // this round's light still running
                if (settled[0] != null) phase[0]++; // a round done
                if (phase[0] < 3) {
                    List<CompletableFuture<Void>> parts = new ArrayList<>();
                    for (int cx = minCx; cx <= maxCx; cx++)
                        for (int cz = minCz; cz <= maxCz; cz++)
                            parts.add(FogChunkSnapshot.lightBarrier(level, new ChunkPos(cx, cz)));
                    settled[0] = CompletableFuture.allOf(parts.toArray(new CompletableFuture[0]));
                    return;
                }
                // both rounds done: the reference is taken in this same tick
            }
            if (phase[0] == 3) {
                long t0 = System.nanoTime();
                reference.putAll(oneTickCapture(level, minCx, maxCx, minCz, maxCz));
                t[0] = System.nanoTime() - t0;
                FogChunkSnapshot.beginCapture(level, minCx, maxCx, minCz, maxCz);
                t[1] = helper.getTick();
                phase[0] = 4;
                return;
            }
            if (phase[0] != 4 || FogChunkSnapshot.isCapturing()) return;
            phase[0] = 5;
            long maxStep = FogChunkSnapshot.lastCaptureMaxStepNs();
            FogChunkSnapshot.awaitWrites();
            ReignOfNether.LOGGER.info(String.format(Locale.ROOT, "[FogSnapshotTest] %d loaded chunks (box " + minCx + ".." + maxCx + " x " + minCz + ".." + maxCz + "): light settled "
                    + "after %d ticks; one-tick capture (no file writes) %.1f ms; budgeted capture over %d ticks, "
                    + "longest step %.1f ms", reference.size(), t[1] - t[2], t[0] / 1e6, helper.getTick() - t[1],
                    maxStep / 1e6));
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
            BlockPos top = new BlockPos(topX, level.getHeight(Heightmap.Types.WORLD_SURFACE, topX, topZ), topZ);
            var oldState = level.getBlockState(top);
            level.setBlock(top, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
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
            level.setBlock(top, oldState, 2);
            FogChunkSnapshot.finishNow();
            Map<ChunkPos, byte[]> changedRef = new LinkedHashMap<>();
            changedRef.put(changed, before.get(changed));
            problems.addAll(compareFiles(changedRef, true));
            release.run();
            finish(helper, problems);
        });
    }

    /**
     * A light barrier that never completes (the 7-minute capture) must not hold the capture: the loaded chunk behind it
     * is built anyway after MAX_CHUNK_WAIT_NS and counted as forced. Own batch: it needs the shared snapshot state to
     * itself, and batches run one after the other.
     * <p>
     * The 3x3 box is far west of the test grid (which grows east and south from x = 0), held by non-ticking tickets
     * (level 33): no fluid, random or entity tick can change a block in it. It used to be the arena's own corner chunk,
     * which is ticking and next to other tests' arenas: in CI run 40457081140 water from a neighbouring arena flowed
     * into it (FlowingFluid.spreadTo -> LevelChunkMixin -> beforeBlockChange), which rightly captured the chunk on the
     * spot before its barrier timed out, so the capture ended in 40 ms with "forced 0 chunks, expected 1".
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20000, batch = "fog_snapshot_hang")
    public static void capture_bounded_when_barrier_hangs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        int cx0 = (origin.getX() >> 4) - 160, cz0 = origin.getZ() >> 4;
        TicketType<ChunkPos> hold = TicketType.FORCED;
        ChunkPos hung = new ChunkPos(cx0, cz0);
        for (int cx = cx0 - 1; cx <= cx0 + 1; cx++)
            for (int cz = cz0 - 1; cz <= cz0 + 1; cz++) {
                ChunkPos p = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(hold, p, 0, p);
            }
        long[] startNs = {-1};
        helper.onEachTick(() -> {
            if (startNs[0] < 0) {
                for (int cx = cx0 - 1; cx <= cx0 + 1; cx++)
                    for (int cz = cz0 - 1; cz <= cz0 + 1; cz++) {
                        LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                        if (c == null || !c.isLightCorrect()) return;
                    }
                FogChunkSnapshot.testBarrierHook = p -> p.equals(hung) ? new CompletableFuture<>() : null;
                FogChunkSnapshot.beginCapture(level, cx0 - 1, cx0 + 1, cz0 - 1, cz0 + 1);
                startNs[0] = System.nanoTime();
                return;
            }
            if (FogChunkSnapshot.isCapturing()) return;
            long tookMs = (System.nanoTime() - startNs[0]) / 1_000_000L;
            int forced = FogChunkSnapshot.forcedCount();
            FogChunkSnapshot.testBarrierHook = null;
            FogChunkSnapshot.awaitWrites();
            Path f = FogChunkSnapshot.fileFor(hung);
            boolean written = f != null && Files.isRegularFile(f);
            FogChunkSnapshot.clear();
            FogChunkSnapshot.awaitWrites();
            for (int cx = cx0 - 1; cx <= cx0 + 1; cx++)
                for (int cz = cz0 - 1; cz <= cz0 + 1; cz++) {
                    ChunkPos p = new ChunkPos(cx, cz);
                    level.getChunkSource().removeRegionTicket(hold, p, 0, p);
                }
            List<String> problems = new ArrayList<>();
            if (tookMs > 12_000L) problems.add("the capture took " + tookMs + " ms (limit 12000)");
            if (forced != 1) problems.add("forced " + forced + " chunks, expected 1");
            if (!written) problems.add("the forced chunk has no snapshot file");
            if (problems.isEmpty()) helper.succeed();
            else helper.fail(String.join("; ", problems));
        });
        // never leave the hook set if the test times out
        helper.runAfterDelay(19990, () -> FogChunkSnapshot.testBarrierHook = null);
    }

    /**
     * Chunks that are not loaded: the capture asks the chunk system for them and builds each when it arrives, over
     * real server ticks (PlayerServerEvents drives it); no step goes over the budget, and the files hold the same blocks,
     * heightmaps and block entities as the one-tick capture of the same chunks. Light is not compared here: a chunk just
     * loaded at level 33 keeps gaining light from neighbours that load after it, so the light of an unloaded chunk's
     * snapshot depended on load order with the old capture too (it loaded the box one chunk at a time); the loaded-area
     * test above compares light byte for byte.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 5000, batch = BATCH)
    public static void the_snapshot_of_unloaded_chunks_loads_them_without_blocking_a_tick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int minCx = (origin.getX() >> 4) + 96, minCz = (origin.getZ() >> 4) + 96;
        int maxCx = minCx + 5, maxCz = minCz + 5;
        long[] startTick = {-1};
        List<String> problems = new ArrayList<>();
        // a non-ticking hold (level 33, as the capture's own ticket) so the reference reads the same loaded chunks
        TicketType<ChunkPos> hold = TicketType.FORCED;
        // start once the other test of the batch is done with the shared snapshot state (or has timed out)
        helper.onEachTick(() -> {
            if (startTick[0] >= 0 || !(loadedTestDone.get() || helper.getTick() > 2420)) return;
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
