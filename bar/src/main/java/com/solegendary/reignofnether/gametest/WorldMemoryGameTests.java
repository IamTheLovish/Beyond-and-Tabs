package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.worldmemory.DecayParams;
import com.solegendary.reignofnether.worldmemory.GroundMemory;
import com.solegendary.reignofnether.worldmemory.GroundMemoryCapability;
import com.solegendary.reignofnether.worldmemory.GroundMemoryClientboundPacket;
import com.solegendary.reignofnether.worldmemory.GroundMemoryServer;
import com.solegendary.reignofnether.worldmemory.GroundSurface;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * A world that remembers, slice 1 (claude/world-that-remembers.md sections 3.1, 5, 7): the ground memory field's
 * rules. The field itself is plain data, so most of these run on {@link GroundMemory} / {@link DecayParams} directly;
 * the rest drive the real server code on a real chunk of the arena - the stamp filters, the chunk's NBT round trip
 * through vanilla's own ChunkSerializer, and the sync pass (through its {@link GroundMemoryServer.Wire} seam, with fake
 * players and the packet codec) - and prove what slice 1 promises players:
 * <ul>
 *   <li>fifty shells on one spot are one burn, never a pile (the black-blob clip), across chunk borders too;</li>
 *   <li>the fade follows lovish's accepted defaults to the byte, and is the same however often it is asked for;</li>
 *   <li>the marks come back from the save byte for byte;</li>
 *   <li>a joining client gets the whole field in one snapshot, later changes as deltas, and never a fogged column;</li>
 *   <li>nothing changes a block (so the pathing grid cannot see a scar), and no mark lands on water, a roof, a
 *       cliff top, a fence post or in the air;</li>
 *   <li>the cost stays inside the spec's budgets.</li>
 * </ul>
 * Every test finishes inside one server tick (the live sync pass cannot interleave with a wire that is the test's
 * own), works inside its own arena, and puts back any global it flips. Own batch "world_memory".
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorldMemoryGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "world_memory";

    // ------------------------------------------------------------------ helpers

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * World Y of the tests' own grass platform. The arena is carved out of generated terrain, where the heightmaps and
     * the real ground disagree (SkirmishGameTests.extractor_and_windmill_fit_on_a_stamped_patch builds its platform at
     * y 200 for the same reason; the first CI run of these tests stamped nothing on the arena's own floor: re-placing a
     * grass block where grass already stands changes no state, so vanilla skips the heightmap update), so each test
     * lays a platform of its own high above it, over its arena's footprint, and every "relative" y below is relative to it.
     */
    static final int PLATFORM_Y = 210;

    /**
     * A flat grass floor at relative y 0 over the arena's footprint, dirt under it, clear air above it up to the build
     * limit: the top-block tests read the whole column, and a sky pad another test left up there (CI run 40651908207:
     * a failed AC-5's stone at relative 54) would be the top block of every column under it.
     */
    static void pad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = level.getMaxBuildHeight() - 1 - PLATFORM_Y;
        for (int x = -1; x <= 16; x++)
            for (int z = -1; z <= 16; z++) {
                level.setBlock(abs(helper, x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(abs(helper, x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = 1; y <= top; y++) {
                    BlockPos p = abs(helper, x, y, z);
                    if (!level.getBlockState(p).isAir())
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
    }

    /** The world position of arena column (x, z) at height y above the platform's grass block. */
    static BlockPos abs(GameTestHelper helper, int x, int y, int z) {
        BlockPos column = helper.absolutePos(new BlockPos(x, 0, z));
        return new BlockPos(column.getX(), PLATFORM_Y + y, column.getZ());
    }

    static GroundMemory field(ServerLevel level, BlockPos pos) {
        return GroundMemoryCapability.of(level.getChunkAt(pos));
    }

    static int cellOf(BlockPos pos) {
        return GroundMemory.index(pos.getX() & 15, pos.getZ() & 15);
    }

    static int scorchAt(ServerLevel level, BlockPos pos) {
        GroundMemory m = field(level, pos);
        return m == null ? -1 : m.scorch(cellOf(pos));
    }

    static int craterAt(ServerLevel level, BlockPos pos) {
        GroundMemory m = field(level, pos);
        return m == null ? -1 : m.crater(cellOf(pos));
    }

    /** The chunks within {@code reach} blocks of a block, as long keys (those that are loaded). */
    static Set<Long> chunksAround(ServerLevel level, BlockPos centre, int reach) {
        Set<Long> keys = new HashSet<>();
        for (int cx = (centre.getX() - reach) >> 4; cx <= (centre.getX() + reach) >> 4; cx++)
            for (int cz = (centre.getZ() - reach) >> 4; cz <= (centre.getZ() + reach) >> 4; cz++)
                if (level.getChunkSource().getChunkNow(cx, cz) != null)
                    keys.add(ChunkPos.asLong(cx, cz));
        return keys;
    }

    // ------------------------------------------------------------------ 1. stamps

    /**
     * The black-blob rule (lovish's clip, the reason BarFx capped scorch marks at 24): fifty shells on one spot are
     * one deeper burn - the same cells as one shell, saturated at 255, never wrapped past it - and a crater only
     * deepens to its maximum. The crater's low nibble (the stain kind) is not touched. A disc on a chunk border
     * lands symmetrically in the four chunks.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 100)
    public static void scar_stamps_merge_and_saturate(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        double cx = 8.5, cz = 8.5, radius = 5.4;
        GroundMemory once = new GroundMemory(0), fifty = new GroundMemory(0);
        long[] changedOnce = once.addScorchDisc(0, 0, cx, cz, radius, 230, null);
        for (int i = 0; i < 50; i++)
            fifty.addScorchDisc(0, 0, cx, cz, radius, 230, null);

        int cellsOnce = 0, cellsFifty = 0, saturated = 0;
        double farthest = 0;
        for (int cell = 0; cell < GroundMemory.CELLS; cell++) {
            if (once.scorch(cell) > 0)
                cellsOnce++;
            int v = fifty.scorch(cell);
            if (v > 0) {
                cellsFifty++;
                double dx = (cell >> 4) + 0.5 - cx, dz = (cell & 15) + 0.5 - cz;
                farthest = Math.max(farthest, Math.sqrt(dx * dx + dz * dz));
            }
            if (v == 255)
                saturated++;
        }
        if (cellsFifty != cellsOnce)
            problems.add("fifty shells marked " + cellsFifty + " cells, one shell " + cellsOnce + ": the patch grew");
        if (farthest > radius * 1.2)
            problems.add(String.format(Locale.ROOT, "a marked cell %.1f blocks from a %.1f block blast (limit 1.2x)", farthest, radius));
        if (fifty.scorch(GroundMemory.index(8, 8)) != 255)
            problems.add("the centre is " + fifty.scorch(GroundMemory.index(8, 8)) + " after fifty shells, not saturated at 255");
        if (saturated < 5)
            problems.add("only " + saturated + " saturated cells after fifty shells");
        if (GroundMemory.bitCount(changedOnce) != cellsOnce)
            problems.add("the stamp reported " + GroundMemory.bitCount(changedOnce) + " changed cells, the field holds " + cellsOnce);

        GroundMemory crater = new GroundMemory(0);
        int centre = GroundMemory.index(8, 8);
        crater.set(centre, GroundMemory.CRATER_STAIN, 0x07);   // stain kind 7, depth 0
        for (int i = 0; i < 50; i++)
            crater.addCraterDisc(0, 0, cx, cz, 4.0, 9, null);
        crater.addCraterDisc(0, 0, cx, cz, 4.0, 15, null);
        if (crater.crater(centre) != 15)
            problems.add("crater depth at the centre is " + crater.crater(centre) + ", wanted the deepest stamp (15)");
        if (crater.stainKind(centre) != 7)
            problems.add("a crater stamp overwrote the stain kind: " + crater.stainKind(centre));
        long[] again = crater.addCraterDisc(0, 0, cx, cz, 4.0, 9, null);
        if (GroundMemory.anyBit(again))
            problems.add("a shallower crater changed a deeper one");

        // a column filter is obeyed
        GroundMemory masked = new GroundMemory(0);
        long[] allowed = new long[4];
        GroundMemory.setBit(allowed, GroundMemory.index(8, 8));
        masked.addScorchDisc(0, 0, cx, cz, radius, 230, allowed);
        int markedUnderMask = 0;
        for (int cell = 0; cell < GroundMemory.CELLS; cell++)
            if (masked.scorch(cell) > 0)
                markedUnderMask++;
        if (markedUnderMask != 1)
            problems.add("a one-column filter let " + markedUnderMask + " columns through");

        // a disc on the corner of four chunks: the same value either side of the border
        GroundMemory[][] chunks = new GroundMemory[2][2];
        for (int a = 0; a < 2; a++)
            for (int b = 0; b < 2; b++) {
                chunks[a][b] = new GroundMemory(0);
                chunks[a][b].addScorchDisc(a, b, 16.0, 16.0, 4.0, 200, null);
            }
        int[][] pairs = { { 15, 15, 16, 16 }, { 15, 16, 16, 15 }, { 14, 14, 17, 17 }, { 14, 15, 17, 16 } };
        for (int[] p : pairs) {
            int v1 = chunks[p[0] >> 4][p[1] >> 4].scorch(GroundMemory.index(p[0] & 15, p[1] & 15));
            int v2 = chunks[p[2] >> 4][p[3] >> 4].scorch(GroundMemory.index(p[2] & 15, p[3] & 15));
            if (v1 != v2 || v1 <= 0)
                problems.add(String.format(Locale.ROOT, "chunk border: (%d,%d) = %d but (%d,%d) = %d", p[0], p[1], v1, p[2], p[3], v2));
        }
        finish(helper, problems);
    }

    // ------------------------------------------------------------------ 2. decay and fade timing

    /**
     * The fade is exact and the same however often it is asked for (the server and every client decay their own
     * copy and must agree byte for byte): one jump, every-quantum steps and ragged jumps give the same bytes in all
     * four channels from a start in the middle of a quantum; a single cell's {@code decayCell} agrees with the field.
     * The timeline is lovish's accepted default: scorch halves in 6 minutes and settles at 30% (77) by ~10 minutes,
     * craters walk 15 -> 11 -> 8 -> 6 -> 5 in ~21 minutes (a third of a fresh crater), and neither goes lower or
     * comes back up for the rest of the match; trample goes away; a stain is gone in minutes.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 200)
    public static void scar_decay_is_exact_and_keeps_its_floor(GameTestHelper helper) {
        DecayParams params = DecayParams.DEFAULT;
        List<String> problems = new ArrayList<>();
        int cell = GroundMemory.index(3, 5);

        // path independence, from a start inside a quantum
        long start = 12_345;
        for (long minutes : new long[] { 1, 6, 10, 22, 60, 180 }) {
            long end = start + minutes * 60 * 20;
            GroundMemory jump = marked(start), steps = marked(start), ragged = marked(start);
            jump.decayTo(end, params);
            for (long t = start; t <= end; t += 7)
                steps.decayTo(t, params);
            steps.decayTo(end, params);
            Random rnd = new Random(minutes);
            long t = start;
            while (t < end) {
                t = Math.min(end, t + 1 + rnd.nextInt(900));
                ragged.decayTo(t, params);
            }
            if (jump.packed(cell) != steps.packed(cell) || jump.packed(cell) != ragged.packed(cell))
                problems.add(String.format(Locale.ROOT, "after %d min one jump %08x, steps %08x, ragged %08x", minutes,
                    jump.packed(cell), steps.packed(cell), ragged.packed(cell)));
            // a cell sent with an older clock and decayed on arrival agrees with the field
            long q0 = Math.floorDiv(start, DecayParams.QUANTUM), q1 = Math.floorDiv(end, DecayParams.QUANTUM);
            int viaCell = GroundMemory.decayCell(marked(start).packed(cell), q0, q1, params);
            if (viaCell != jump.packed(cell))
                problems.add(String.format(Locale.ROOT, "decayCell after %d min %08x, the field %08x", minutes, viaCell, jump.packed(cell)));
        }

        // the timeline, from a start on a boundary of every channel's step (a crater steps every 16 quanta)
        long t0 = 16L * DecayParams.QUANTUM * 10;
        int scorchFloor = params.floor(GroundMemory.SCORCH), craterFloor = params.floor(GroundMemory.CRATER_STAIN);
        if (scorchFloor != 77)
            problems.add("scorch floor is " + scorchFloor + ", lovish's accepted default is 30% of 255 = 77");
        if (craterFloor != 5)
            problems.add("crater floor is " + craterFloor + ", the accepted ~30% of 15 is 5");
        GroundMemory walk = marked(t0);
        int lastScorch = 255, lastCrater = 15, lastTrample = 255, lastStain = 255;
        int scorchFloorAt = -1, craterFloorAt = -1;
        for (int q = 1; q <= 540; q++) {   // 3 hours in 20 s quanta
            walk.decayTo(t0 + (long) q * DecayParams.QUANTUM, params);
            int s = walk.scorch(cell), c = walk.crater(cell), tr = walk.trample(cell), st = walk.stain(cell);
            if (s > lastScorch || c > lastCrater || tr > lastTrample || st > lastStain)
                problems.add("a channel rose at quantum " + q);
            if (s < scorchFloor)
                problems.add("scorch fell to " + s + " under its floor at quantum " + q);
            if (c < craterFloor)
                problems.add("crater fell to " + c + " under its floor at quantum " + q);
            if (scorchFloorAt < 0 && s == scorchFloor)
                scorchFloorAt = q;
            if (craterFloorAt < 0 && c == craterFloor)
                craterFloorAt = q;
            lastScorch = s;
            lastCrater = c;
            lastTrample = tr;
            lastStain = st;
            if (q == 18 && Math.abs(s - 127.5) > 4)
                problems.add("scorch after 6 minutes is " + s + ", a half-life leaves 127.5");
        }
        // 20 s quanta: 10 minutes = 30, 12 = 36
        if (scorchFloorAt < 24 || scorchFloorAt > 36)
            problems.add("scorch reached its floor at " + scorchFloorAt * 20 / 60.0 + " min, expected 8-12 (about 10.4)");
        if (craterFloorAt < 56 || craterFloorAt > 72)
            problems.add("crater reached its floor at " + craterFloorAt * 20 / 60.0 + " min, expected ~21");
        if (walk.scorch(cell) != 77 || walk.crater(cell) != 5)
            problems.add("after 3 hours scorch " + walk.scorch(cell) + " crater " + walk.crater(cell) + ": the scars must stay at their floors");
        if (walk.trample(cell) != 0 || walk.stain(cell) != 0)
            problems.add("trample " + walk.trample(cell) + " and stain " + walk.stain(cell) + " must fade to nothing");

        // pinned points of the crater walk (a third of a fresh crater at the end)
        GroundMemory crater = marked(t0);
        crater.decayTo(t0 + 2 * 60 * 20, params);
        if (crater.crater(cell) != 15)
            problems.add("a crater two minutes old is already " + crater.crater(cell));
        crater.decayTo(t0 + 12 * 60 * 20, params);
        if (crater.crater(cell) != 8)
            problems.add("a crater 12 minutes old is " + crater.crater(cell) + ", expected 8");

        // the NBT keeps the decay clock: a saved field resumes where it was
        GroundMemory saved = marked(t0);
        saved.decayTo(t0 + 5 * 60 * 20, params);
        CompoundTag tag = new CompoundTag();
        saved.save(tag);
        GroundMemory back = GroundMemory.load(tag, 999_999_999L);
        if (!back.sameCells(saved) || back.lastDecayTick() != saved.lastDecayTick())
            problems.add("a saved field did not load back as it was (decay clock " + back.lastDecayTick() + " vs " + saved.lastDecayTick() + ")");
        finish(helper, problems);
    }

    /** One cell at full strength in every channel, its clock at {@code now}. */
    static GroundMemory marked(long now) {
        GroundMemory m = new GroundMemory(now);
        int cell = GroundMemory.index(3, 5);
        m.set(cell, GroundMemory.SCORCH, 255);
        m.setCrater(cell, 15);
        m.set(cell, GroundMemory.TRAMPLE, 255);
        m.set(cell, GroundMemory.STAIN, 255);
        return m;
    }

    // ------------------------------------------------------------------ 3. the save

    /**
     * The marks come back from the save: a real chunk with a burn and a crater is written with vanilla's own chunk
     * serialiser (what the region file stores) and read back into a new chunk object, whose field must equal the
     * original's byte for byte with the same decay clock; the capability's tag is in the chunk's Forge caps, and the
     * field costs a kilobyte.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 200)
    public static void scars_survive_chunk_save_and_load(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        pad(helper);
        BlockPos centre = abs(helper, 8, 1, 8);
        int changed = GroundMemoryServer.stampScorch(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, 4.5, 220)
            + GroundMemoryServer.stampCrater(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, 3.0, 12);
        if (changed == 0) {
            helper.fail("nothing was stamped on the arena floor");
            return;
        }
        LevelChunk chunk = level.getChunkAt(centre);
        GroundMemory mem = GroundMemoryCapability.of(chunk);
        if (mem == null || !mem.hasAnyMark()) {
            helper.fail("the chunk has no ground memory capability or no mark after a stamp");
            return;
        }
        mem.decayTo(level.getGameTime(), GroundMemoryServer.params());
        try {
            CompoundTag tag = ChunkSerializer.write(level, chunk);
            CompoundTag caps = tag.getCompound("ForgeCaps");
            if (!caps.contains(GroundMemoryCapability.ID.toString()))
                problems.add("the chunk's NBT has no " + GroundMemoryCapability.ID + " in ForgeCaps (has " + caps.getAllKeys() + ")");
            else {
                CompoundTag ours = caps.getCompound(GroundMemoryCapability.ID.toString());
                int bytes = ours.getByteArray("cells").length;
                if (bytes != GroundMemory.BYTES)
                    problems.add("the saved field is " + bytes + " bytes, expected " + GroundMemory.BYTES);
            }
            ProtoChunk read = ChunkSerializer.read(level, level.getPoiManager(), chunk.getPos(), tag);
            if (!(read instanceof ImposterProtoChunk imposter)) {
                helper.fail("the saved chunk did not read back as a full chunk: " + read.getClass().getSimpleName());
                return;
            }
            GroundMemory back = GroundMemoryCapability.of(imposter.getWrapped());
            if (back == null)
                problems.add("the reloaded chunk has no ground memory capability");
            else {
                if (back.lastDecayTick() != mem.lastDecayTick())
                    problems.add("decay clock " + back.lastDecayTick() + " after the reload, " + mem.lastDecayTick() + " before");
                if (!back.sameCells(mem))
                    problems.add("the reloaded field differs from the saved one");
                back.decayTo(level.getGameTime() + 5 * 60 * 20, GroundMemoryServer.params());
                GroundMemory later = new GroundMemory(mem.lastDecayTick());
                later.setAll(mem.copyCells());
                later.decayTo(level.getGameTime() + 5 * 60 * 20, GroundMemoryServer.params());
                if (!back.sameCells(later))
                    problems.add("after the reload the field fades differently from the original");
            }
        } catch (Exception e) {
            problems.add("the chunk round trip threw " + e);
        }
        finish(helper, problems);
    }

    // ------------------------------------------------------------------ 4. sync

    /** A wire of the test's own: who is online, which chunks they track, which columns they see, and what was sent. */
    static final class TestWire implements GroundMemoryServer.Wire {
        final List<ServerPlayer> players = new ArrayList<>();
        final Set<Long> chunks = new HashSet<>();
        final Map<UUID, Integer> hiddenBelowX = new HashMap<>();   // world x below this is fogged for the player
        final Map<UUID, List<GroundMemoryClientboundPacket>> sent = new HashMap<>();
        boolean tracking = true;

        @Override
        public List<ServerPlayer> players(ServerLevel level) {
            return players;
        }

        @Override
        public List<ServerPlayer> watching(ServerLevel level, ChunkPos pos) {
            return tracking && chunks.contains(pos.toLong()) ? players : List.of();
        }

        @Override
        public long[] visible(ServerPlayer player, ChunkPos pos) {
            Integer below = hiddenBelowX.get(player.getUUID());
            if (below == null)
                return GroundMemory.fullMask();
            long[] mask = new long[4];
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++)
                    if (pos.getMinBlockX() + lx >= below)
                        GroundMemory.setBit(mask, GroundMemory.index(lx, lz));
            return mask;
        }

        @Override
        public void send(ServerPlayer player, GroundMemoryClientboundPacket packet) {
            sent.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(packet);
        }

        ServerPlayer join(ServerLevel level, String name) {
            ServerPlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
            players.add(p);
            return p;
        }

        int count(ServerPlayer p) {
            return sent.getOrDefault(p.getUUID(), List.of()).size();
        }
    }

    /** What a pass sent a player about the test's chunks: snapshots, deltas, delta cells, wire bytes. */
    record Tally(int snapshots, int deltas, int deltaCells, int bytes) { }

    static Tally tally(TestWire wire, ServerPlayer p, int fromPacket, Set<Long> chunks) {
        int snapshots = 0, deltas = 0, cells = 0, bytes = 0;
        List<GroundMemoryClientboundPacket> all = wire.sent.getOrDefault(p.getUUID(), List.of());
        for (int i = fromPacket; i < all.size(); i++)
            for (GroundMemoryClientboundPacket.Update u : all.get(i).updates) {
                if (!chunks.contains(ChunkPos.asLong(u.chunkX, u.chunkZ)))
                    continue;
                bytes += u.wireBytes();
                if (u.full != null)
                    snapshots++;
                else {
                    deltas++;
                    cells += u.cells.length;
                }
            }
        return new Tally(snapshots, deltas, cells, bytes);
    }

    /** The client's side of a pass: every packet through the codec (as the network does), applied to the copies. */
    static void receive(Map<Long, GroundMemory> copies, TestWire wire, ServerPlayer p, int fromPacket) {
        List<GroundMemoryClientboundPacket> all = wire.sent.getOrDefault(p.getUUID(), List.of());
        for (int i = fromPacket; i < all.size(); i++) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            all.get(i).encode(buf);
            GroundMemoryClientboundPacket decoded = new GroundMemoryClientboundPacket(buf);
            for (GroundMemoryClientboundPacket.Update u : decoded.updates) {
                GroundMemory copy = copies.computeIfAbsent(ChunkPos.asLong(u.chunkX, u.chunkZ), k -> new GroundMemory(u.decayTick));
                u.applyTo(copy, decoded.params);
            }
        }
    }

    /** Cells where a client copy differs from the server's field; fogged columns (world x below the limit) must be empty. */
    static int mismatches(ServerLevel level, Map<Long, GroundMemory> copies, Set<Long> chunks, int hiddenBelowX) {
        long now = level.getGameTime();
        int bad = 0;
        for (long key : chunks) {
            LevelChunk lc = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            GroundMemory server = GroundMemoryCapability.of(lc);
            if (server == null)
                continue;
            server.decayTo(now, GroundMemoryServer.params());
            GroundMemory copy = copies.get(key);
            if (copy != null)
                copy.decayTo(now, GroundMemoryServer.params());
            for (int cell = 0; cell < GroundMemory.CELLS; cell++) {
                int worldX = lc.getPos().getMinBlockX() + (cell >> 4);
                int want = worldX < hiddenBelowX ? 0 : server.packed(cell);
                int got = copy == null ? 0 : copy.packed(cell);
                if (want != got)
                    bad++;
            }
        }
        return bad;
    }

    /**
     * Spec test 7, scar_sync_follows_fog_of_war, and the join: through the real sync pass (the wire is the test's, the
     * decisions are the server's) a player who is online gets the field as one snapshot per marked chunk, a later
     * stamp as a delta of exactly the changed cells, a quiet pass sends nothing, a player who joins late gets the
     * snapshot, a player who loses a chunk and sees it again gets a snapshot, and a player whose fog hides half the
     * chunk is never sent a fogged column - until the fog lifts, when exactly those go out. Every packet goes through
     * the codec and the client's apply, and the copies must equal the server's field byte for byte.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 200)
    public static void scar_sync_joiner_snapshot_deltas_and_fog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        pad(helper);
        BlockPos centre = abs(helper, 8, 1, 8);
        TestWire wire = new TestWire();
        wire.chunks.addAll(chunksAround(level, centre, 9));
        Set<Long> chunks = wire.chunks;
        ServerPlayer a = wire.join(level, "gt_wm_a");
        double y = centre.getY();

        int first = GroundMemoryServer.stampScorch(level, centre.getX() + 0.5, y, centre.getZ() + 0.5, 4.5, 220)
            + GroundMemoryServer.stampCrater(level, centre.getX() + 0.5, y, centre.getZ() + 0.5, 3.0, 10);
        if (first == 0) {
            helper.fail("nothing was stamped");
            return;
        }
        GroundMemoryServer.syncNow(level, wire);
        Tally t1 = tally(wire, a, 0, chunks);
        if (t1.snapshots() == 0 || t1.deltas() != 0)
            problems.add("first sight should be snapshots only: " + t1);
        Map<Long, GroundMemory> aCopies = new HashMap<>();
        int sentA = wire.count(a);
        receive(aCopies, wire, a, 0);
        int bad = mismatches(level, aCopies, chunks, Integer.MIN_VALUE);
        if (bad != 0)
            problems.add(bad + " cells of the first snapshot differ from the server's field");

        // a second stamp: a delta of exactly the cells that changed
        int second = GroundMemoryServer.stampScorch(level, centre.getX() + 2.5, y, centre.getZ() - 1.5, 2.0, 200);
        GroundMemoryServer.syncNow(level, wire);
        Tally t2 = tally(wire, a, sentA, chunks);
        if (second == 0)
            problems.add("the second stamp changed nothing");
        if (t2.snapshots() != 0)
            problems.add("a later stamp sent " + t2.snapshots() + " snapshots instead of a delta");
        if (t2.deltaCells() != second)
            problems.add("the delta lists " + t2.deltaCells() + " cells, the stamp changed " + second);
        if (t2.bytes() >= t1.bytes())
            problems.add("the delta (" + t2.bytes() + " B) is not smaller than the snapshot (" + t1.bytes() + " B)");
        receive(aCopies, wire, a, sentA);
        sentA = wire.count(a);
        bad = mismatches(level, aCopies, chunks, Integer.MIN_VALUE);
        if (bad != 0)
            problems.add(bad + " cells differ after the delta");

        // a quiet pass sends nothing
        GroundMemoryServer.syncNow(level, wire);
        if (wire.count(a) != sentA)
            problems.add("a pass with nothing new sent " + (wire.count(a) - sentA) + " packets");

        // a late joiner: one snapshot per marked chunk, the same bytes; the first player is not bothered
        ServerPlayer b = wire.join(level, "gt_wm_b");
        GroundMemoryServer.syncNow(level, wire);
        Tally tb = tally(wire, b, 0, chunks);
        if (tb.snapshots() != t1.snapshots() || tb.deltas() != 0)
            problems.add("the late joiner got " + tb + ", the first player's first sight was " + t1);
        if (wire.count(a) != sentA)
            problems.add("the first player was sent something when a second one joined");
        Map<Long, GroundMemory> bCopies = new HashMap<>();
        receive(bCopies, wire, b, 0);
        bad = mismatches(level, bCopies, chunks, Integer.MIN_VALUE);
        if (bad != 0)
            problems.add(bad + " cells differ for the late joiner");

        // losing sight of the chunks and seeing them again: a snapshot of what is there now
        wire.tracking = false;
        GroundMemoryServer.syncNow(level, wire);
        wire.tracking = true;
        int beforeBack = wire.count(a);
        GroundMemoryServer.syncNow(level, wire);
        Tally back = tally(wire, a, beforeBack, chunks);
        if (back.snapshots() != t1.snapshots())
            problems.add("a chunk back in sight sent " + back + ", expected " + t1.snapshots() + " snapshots");

        // fog: a player who sees only the columns from the centre east is never sent the others
        ServerPlayer c = wire.join(level, "gt_wm_c");
        wire.hiddenBelowX.put(c.getUUID(), centre.getX());
        GroundMemoryServer.syncNow(level, wire);
        Map<Long, GroundMemory> cCopies = new HashMap<>();
        receive(cCopies, wire, c, 0);
        Tally tc = tally(wire, c, 0, chunks);
        if (tc.snapshots() + tc.deltas() == 0)
            problems.add("the fogged player was sent nothing at all");
        bad = mismatches(level, cCopies, chunks, centre.getX());
        if (bad != 0)
            problems.add(bad + " cells of the fogged player's copy differ from what it may see (or a fogged column leaked)");
        int leaked = 0;
        for (long key : chunks) {
            GroundMemory copy = cCopies.get(key);
            LevelChunk lc = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (copy == null || lc == null)
                continue;
            for (int cell = 0; cell < GroundMemory.CELLS; cell++)
                if (lc.getPos().getMinBlockX() + (cell >> 4) < centre.getX() && copy.packed(cell) != 0)
                    leaked++;
        }
        if (leaked != 0)
            problems.add(leaked + " fogged columns reached the fogged player");
        // the fog lifts: exactly the missing columns go out, with nothing stamped meanwhile
        wire.hiddenBelowX.remove(c.getUUID());
        int sentC = wire.count(c);
        GroundMemoryServer.syncNow(level, wire);
        receive(cCopies, wire, c, sentC);
        bad = mismatches(level, cCopies, chunks, Integer.MIN_VALUE);
        if (bad != 0)
            problems.add(bad + " cells still differ after the fog lifted");
        finish(helper, problems);
    }

    // ------------------------------------------------------------------ 5. where marks land, and what they never do

    /**
     * The edge cases of the ground (review: steps, slabs, snow, paths, water, roofs, posts, the air, chunk borders and
     * a building under fire): a blast is stamped onto full-block steps, slabs, snow layers and a dirt path at the
     * height they are DRAWN at (and under a canopy of leaves, on the ground below it), and never onto water, a roof, a cliff top 4 above the blast, a fence post or from the
     * air; and no block anywhere in the arena changes (so the pathing grid cannot see a scar).
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 200)
    public static void scar_lands_on_the_real_top_face_and_changes_no_block(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        // what another test can leave over this arena: CI run 40651908207 (attempt 2) found a stone sky pad at relative
        // y 54 here (a failed AC-5 in mex_queue left its radius-48 pad at y 264), and every top block below it was the pad
        for (int x = -1; x <= 16; x++)
            for (int z = -1; z <= 16; z++)
                level.setBlock(abs(helper, x, 54, z), Blocks.STONE.defaultBlockState(), 2);
        pad(helper);
        BlockState stone = Blocks.STONE.defaultBlockState();
        // steps: x 3..6, z 6..9, rising 1..4 blocks (the fourth is a cliff top 4 above the blast)
        for (int x = 3; x <= 6; x++)
            for (int z = 6; z <= 9; z++)
                for (int y = 1; y <= x - 2; y++)
                    level.setBlock(abs(helper, x, y, z), stone, 2);
        level.setBlock(abs(helper, 10, 1, 6), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        level.setBlock(abs(helper, 11, 1, 6), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        level.setBlock(abs(helper, 10, 1, 10), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), 2);
        level.setBlock(abs(helper, 11, 1, 10), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4), 2);
        level.setBlock(abs(helper, 12, 0, 7), Blocks.DIRT_PATH.defaultBlockState(), 2);
        for (int x = 6; x <= 8; x++)
            for (int z = 11; z <= 13; z++)
                level.setBlock(abs(helper, x, 1, z), Blocks.WATER.defaultBlockState(), 2);
        for (int x = 11; x <= 13; x++)
            for (int z = 11; z <= 13; z++)
                level.setBlock(abs(helper, x, 6, z), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        level.setBlock(abs(helper, 8, 1, 5), Blocks.OAK_FENCE.defaultBlockState(), 2);
        // a canopy: two blocks of leaves two blocks above the grass of column (5,11) - the ground under it is still the ground
        for (int y = 3; y <= 4; y++)
            level.setBlock(abs(helper, 5, y, 11), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 2);

        // the top block as the client finds it (it has no NO_LEAVES map of its own) is the server's NO_LEAVES top, everywhere, plus snow
        int topMismatch = 0;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                BlockPos p = abs(helper, x, 0, z);
                LevelChunk lc = level.getChunkAt(p);
                int want = lc.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX() & 15, p.getZ() & 15);
                // the one difference by design: snow layers (not in vanilla's heightmaps) lying on that top block are the surface
                if (level.getBlockState(new BlockPos(p.getX(), want + 1, p.getZ())).getBlock() instanceof SnowLayerBlock)
                    want++;
                if (GroundSurface.topBlockY(lc, p.getX() & 15, p.getZ() & 15) != want)
                    topMismatch++;
            }
        if (topMismatch != 0)
            problems.add(topMismatch + " columns where the client's way of finding the top block differs from the NO_LEAVES heightmap");
        BlockPos canopyFoot = abs(helper, 5, 0, 11);
        if (GroundSurface.topBlockY(level.getChunkAt(canopyFoot), canopyFoot.getX() & 15, canopyFoot.getZ() & 15) != canopyFoot.getY())
            problems.add("under a canopy the top block is not the grass");

        // the drawn top of each, as the mesh and the stamp filter see it
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Object[][] surfaces = {
            { "grass", abs(helper, 8, 0, 8), abs(helper, 8, 0, 8).getY() + 1.0 },
            { "step 1", abs(helper, 3, 1, 7), abs(helper, 3, 1, 7).getY() + 1.0 },
            { "step 3", abs(helper, 5, 3, 7), abs(helper, 5, 3, 7).getY() + 1.0 },
            { "bottom slab", abs(helper, 10, 1, 6), abs(helper, 10, 1, 6).getY() + 0.5 },
            { "top slab", abs(helper, 11, 1, 6), abs(helper, 11, 1, 6).getY() + 1.0 },
            { "one snow layer", abs(helper, 10, 1, 10), abs(helper, 10, 1, 10).getY() + 0.125 },
            { "four snow layers", abs(helper, 11, 1, 10), abs(helper, 11, 1, 10).getY() + 0.5 },
            { "dirt path", abs(helper, 12, 0, 7), abs(helper, 12, 0, 7).getY() + 0.9375 },
            { "water", abs(helper, 7, 1, 12), Double.NaN },
            { "fence post", abs(helper, 8, 1, 5), Double.NaN },
        };
        for (Object[] s : surfaces) {
            BlockPos sp = (BlockPos) s[1];
            int topY = GroundSurface.topBlockY(level.getChunkAt(sp), sp.getX() & 15, sp.getZ() & 15);
            if (topY != sp.getY())
                problems.add(s[0] + ": the top block of its column is at relative y " + (topY - PLATFORM_Y) + ", not " + (sp.getY() - PLATFORM_Y));
            double got = GroundSurface.surfaceY(level, m.set((BlockPos) s[1]));
            double want = (Double) s[2];
            boolean ok = Double.isNaN(want) ? Double.isNaN(got) : Math.abs(got - want) < 1e-6;
            if (!ok)
                problems.add(s[0] + ": surface " + got + ", wanted " + want);
        }

        // every block of the arena, before and after
        long before = blockHash(helper, level);
        BlockPos centre = abs(helper, 8, 1, 8);
        GroundMemoryServer.blast(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, 8f, BarFx.K_TNT);
        GroundMemoryServer.collapse(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, 3f);
        GroundMemoryServer.heavyImpact(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, 3f);
        if (blockHash(helper, level) != before)
            problems.add("a scar changed a block");

        int[][] marked = { { 8, 8 }, { 3, 7 }, { 4, 7 }, { 5, 7 }, { 10, 6 }, { 11, 6 }, { 10, 10 }, { 11, 10 }, { 12, 7 }, { 5, 11 } };
        for (int[] c : marked) {
            BlockPos p = abs(helper, c[0], 1, c[1]);
            if (scorchAt(level, p) <= 0)
                problems.add("no scorch on (" + c[0] + "," + c[1] + ")");
        }
        int[][] clean = { { 6, 7 }, { 7, 12 }, { 12, 12 }, { 8, 5 } };   // a cliff top 4 up, water, under a roof, a post
        String[] why = { "the cliff top", "the water", "the roof", "the fence post" };
        for (int i = 0; i < clean.length; i++) {
            BlockPos p = abs(helper, clean[i][0], 1, clean[i][1]);
            if (scorchAt(level, p) != 0 || craterAt(level, p) != 0)
                problems.add("a mark on " + why[i] + " (" + clean[i][0] + "," + clean[i][1] + "): scorch " + scorchAt(level, p) + " crater " + craterAt(level, p));
        }
        if (craterAt(level, centre) != 15)
            problems.add("the centre crater is " + craterAt(level, centre) + ", a radius 8 blast digs the full 15");

        // an airburst and a pit leave nothing; a switched-off server stamps nothing
        BlockPos far = abs(helper, 14, 1, 2);
        int air = GroundMemoryServer.stampScorch(level, far.getX() + 0.5, far.getY() + 6, far.getZ() + 0.5, 2.0, 200);
        if (air != 0)
            problems.add("a burst 6 blocks up marked " + air + " cells");
        boolean was = GroundMemoryServer.enabled;
        try {
            GroundMemoryServer.enabled = false;
            GroundMemoryServer.blast(level, far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 3f, BarFx.K_TNT);
            if (scorchAt(level, far) != 0)
                problems.add("scars are switched off yet a blast marked the ground");
        } finally {
            GroundMemoryServer.enabled = was;
        }
        GroundMemoryServer.blast(level, far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 3f, BarFx.K_TNT);
        if (scorchAt(level, far) <= 0)
            problems.add("scars are switched on again but a blast marked nothing");
        finish(helper, problems);
    }

    static long blockHash(GameTestHelper helper, ServerLevel level) {
        long h = 1125899906842597L;
        for (int x = -1; x <= 16; x++)
            for (int y = 0; y <= 10; y++)
                for (int z = -1; z <= 16; z++)
                    h = 31 * h + level.getBlockState(abs(helper, x, y, z)).hashCode();
        return h;
    }

    // ------------------------------------------------------------------ 6. budget

    /**
     * Spec section 5, the part slice 1 owns: a brawl's worth of stamps and sync passes with eight players watching
     * costs the server about nothing and the network about 6 KB/s a client; no pass ever sends one player more than
     * its cap. Ten seconds of fighting at three blasts a second, a pass every half second, in one tick. Logged as
     * a "[Stress]" line. The bounds are 2-3x the spec's so a loaded CI runner does not flake it.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 300)
    public static void scars_cost_stays_inside_the_budget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        pad(helper);
        BlockPos centre = abs(helper, 8, 1, 8);
        TestWire wire = new TestWire();
        wire.chunks.addAll(chunksAround(level, centre, 9));
        for (int i = 0; i < 8; i++)
            wire.join(level, "gt_wm_stress_" + i);
        Random rnd = new Random(2026);
        int stamps = 0, passes = 0;
        long stampNanos = 0, passNanos = 0, worstStamp = 0;
        int worstPlayerPass = 0;
        // warm-up: one stamp and one pass outside the clock (class loading, JIT)
        GroundMemoryServer.blast(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, 3f, BarFx.K_TNT);
        GroundMemoryServer.syncNow(level, wire);
        int[] sentBefore = new int[wire.players.size()];
        for (int i = 0; i < sentBefore.length; i++)
            sentBefore[i] = wire.count(wire.players.get(i));
        long steadyBytes = 0;
        for (int pass = 0; pass < 20; pass++) {   // 20 passes = 10 s
            int blasts = pass % 2 == 0 ? 1 : 2;   // 1.5 a pass = 3 a second
            for (int b = 0; b < blasts; b++) {
                double x = centre.getX() + 0.5 + rnd.nextInt(9) - 4, z = centre.getZ() + 0.5 + rnd.nextInt(9) - 4;
                float r = 2f + rnd.nextInt(3);
                long t = System.nanoTime();
                GroundMemoryServer.blast(level, x, centre.getY(), z, r, BarFx.K_TNT);
                long d = System.nanoTime() - t;
                stampNanos += d;
                worstStamp = Math.max(worstStamp, d);
                stamps++;
            }
            long t = System.nanoTime();
            GroundMemoryServer.syncNow(level, wire);
            passNanos += System.nanoTime() - t;
            passes++;
            for (int i = 0; i < wire.players.size(); i++) {
                ServerPlayer p = wire.players.get(i);
                Tally tl = tally(wire, p, sentBefore[i], wire.chunks);
                worstPlayerPass = Math.max(worstPlayerPass, tl.bytes());
                steadyBytes += tl.bytes();
                sentBefore[i] = wire.count(p);
            }
        }
        double stampMs = stampNanos / 1e6 / stamps, worstStampMs = worstStamp / 1e6, passMs = passNanos / 1e6 / passes;
        double perClientPerSecond = steadyBytes / (double) wire.players.size() / 10.0;
        ReignOfNether.LOGGER.info(String.format(Locale.ROOT,
            "[Stress] [World memory] %d stamps avg %.3f ms (worst %.3f ms), %d sync passes avg %.3f ms for %d players, %.0f B/s per client, worst pass %d B per player (cap %d)",
            stamps, stampMs, worstStampMs, passes, passMs, wire.players.size(), perClientPerSecond, worstPlayerPass,
            GroundMemoryServer.MAX_BYTES_PER_PASS));
        if (stampMs > 1.0)
            problems.add(String.format(Locale.ROOT, "a stamp averages %.3f ms (budget: 0.15 ms a tick for everything)", stampMs));
        if (worstStampMs > 20)
            problems.add(String.format(Locale.ROOT, "the worst stamp took %.2f ms", worstStampMs));
        if (passMs > 10)
            problems.add(String.format(Locale.ROOT, "a sync pass for 8 players averages %.2f ms", passMs));
        if (perClientPerSecond > 6 * 1024)
            problems.add(String.format(Locale.ROOT, "%.0f B/s per client in a brawl (budget 6 KB/s)", perClientPerSecond));
        if (worstPlayerPass > GroundMemoryServer.MAX_BYTES_PER_PASS)
            problems.add("one pass sent a player " + worstPlayerPass + " B (cap " + GroundMemoryServer.MAX_BYTES_PER_PASS + ")");
        finish(helper, problems);
    }
}
