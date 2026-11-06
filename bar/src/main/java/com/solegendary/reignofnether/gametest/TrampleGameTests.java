package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.worldmemory.DecayParams;
import com.solegendary.reignofnether.worldmemory.GroundMemory;
import com.solegendary.reignofnether.worldmemory.GroundMemoryCapability;
import com.solegendary.reignofnether.worldmemory.GroundMemoryServer;
import com.solegendary.reignofnether.worldmemory.TrampleRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Trampled ground, the world memory's slice 2 (design/battlefield_effects.md): real units walking wear the soft ground
 * they cross, through the one sampler every unit passes (GroundMemoryServer's level tick over UnitServerEvents.getAllUnits),
 * never a hook in a unit class. Several unit kinds walk: militia, a brute, a ravager (wide: it wears more), and a ghast
 * (flying: it wears nothing). Stone stays clean, nothing off the walkers' lanes changes, no block changes (the pathing
 * grid cannot see a track), the field syncs and fades like the scars, and the cost stays inside the design's budget,
 * which was set before anything was measured.
 * <p>
 * Each test lays its own grass pad high above its arena (the arena floor's heightmaps disagree with its ground:
 * WorldMemoryGameTests.PLATFORM_Y) and has its own batch, so no two pads overlap.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TrampleGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_Y = 214, PAD_R = 14, PAD_H = 16;

    /** One test's grass pad (dirt under it, air over it) round the arena's centre, its units, and its undo. */
    static final class Pad {
        final GameTestHelper helper;
        final ServerLevel level;
        final String owner;
        final int cx, cz, r;
        final List<Entity> made = new ArrayList<>();
        final List<String> problems = new ArrayList<>();

        Pad(GameTestHelper helper, String tag, int r) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.r = r;
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
            cx = base.getX();
            cz = base.getZ();
            owner = "gametest_trample_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            lay(false);
        }

        void lay(boolean clear) {
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int y = -1; y <= PAD_H; y++) {
                        BlockState s = clear || y > 0 ? Blocks.AIR.defaultBlockState()
                            : y == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                        level.setBlock(new BlockPos(cx + dx, PAD_Y + y, cz + dz), s, 2);
                    }
        }

        /** An order's target: the grass block itself (a flyer's order at an AIR block is ignored, FlyingMoveToTargetGoal). */
        BlockPos at(int dx, int dz) {
            return new BlockPos(cx + dx, PAD_Y, cz + dz);
        }

        Mob spawn(EntityType<? extends Mob> type, double dx, double dz, double y) {
            Mob m = type.create(level);
            if (m == null)
                return null;
            m.moveTo(cx + dx + 0.5, y, cz + dz + 0.5, 0, 0);
            if (m instanceof Unit u)
                u.setOwnerName(owner);
            level.addFreshEntity(m);
            made.add(m);
            return m;
        }

        void move(List<Mob> units, BlockPos to) {
            int[] ids = new int[units.size()];
            for (int i = 0; i < ids.length; i++)
                ids[i] = units.get(i).getId();
            UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, ids, to, BlockPos.ZERO);
        }

        int trample(int dx, int dz) {
            BlockPos p = new BlockPos(cx + dx, PAD_Y, cz + dz);
            GroundMemory m = GroundMemoryCapability.of(level.getChunkAt(p));
            return m == null ? 0 : m.trample(GroundMemory.index(p.getX() & 15, p.getZ() & 15));
        }

        /** The most worn cell of column dx across the rows dz0..dz1. */
        int bandMax(int dx, int dz0, int dz1) {
            int best = 0;
            for (int dz = dz0; dz <= dz1; dz++)
                best = Math.max(best, trample(dx, dz));
            return best;
        }

        void undo() {
            for (Entity e : made) {
                UnitServerEvents.clearQueuedActions(e.getId());
                if (!e.isRemoved())
                    e.discard();
            }
            lay(true);
        }

        void finish() {
            try {
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                undo();
            }
        }
    }

    // ------------------------------------------------------------------ 1. real units, several kinds

    static final int LANE_MILITIA = -9, LANE_BRUTE = -4, LANE_RAVAGER = 2, LANE_GHAST = 9;
    static final int FROM_X = -11, TO_X = 11;

    /**
     * Militia (three, in formation), a brute and a ravager walk across a grass pad with a two-block band of stone across
     * their lanes, and a ghast flies its own lane, all through the real MOVE order. The lanes they walked are worn on
     * the grass on both sides of the stone; the stone, the ghast's lane and the rows nobody walked stay at 0; the
     * ravager (body 1.95 wide) wears its lane deeper than the brute (0.6) does in one pass; and no block of the pad
     * changed. On a build without the sampler every lane reads 0.
     */
    @GameTest(template = ARENA, batch = "trample_march", timeoutTicks = 520)
    public static void trample_marching_units_wear_the_ground_they_cross(GameTestHelper helper) {
        Pad pad = new Pad(helper, "march", PAD_R);
        for (int dz = -PAD_R; dz <= PAD_R; dz++)
            for (int dx = -1; dx <= 0; dx++)
                pad.level.setBlock(new BlockPos(pad.cx + dx, PAD_Y, pad.cz + dz), Blocks.STONE.defaultBlockState(), 2);
        // every block of the pad and the air over it, to prove nothing changes
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (int dx = -PAD_R; dx <= PAD_R; dx++)
            for (int dz = -PAD_R; dz <= PAD_R; dz++)
                for (int y = -1; y <= 3; y++) {
                    BlockPos p = new BlockPos(pad.cx + dx, PAD_Y + y, pad.cz + dz);
                    before.put(p, pad.level.getBlockState(p));
                }
        List<Mob> militia = new ArrayList<>();
        for (int i = 0; i < 3; i++)
            militia.add(pad.spawn(EntityRegistrar.MILITIA_UNIT.get(), FROM_X - i, LANE_MILITIA, PAD_Y + 1));
        Mob brute = pad.spawn(EntityRegistrar.BRUTE_UNIT.get(), FROM_X, LANE_BRUTE, PAD_Y + 1);
        Mob ravager = pad.spawn(EntityRegistrar.RAVAGER_UNIT.get(), FROM_X, LANE_RAVAGER, PAD_Y + 1);
        Mob ghast = pad.spawn(EntityRegistrar.GHAST_UNIT.get(), FROM_X, LANE_GHAST, PAD_Y + 5);
        if (militia.contains(null) || brute == null || ravager == null || ghast == null) {
            pad.undo();
            helper.fail("(setup) could not create the walkers");
            return;
        }
        List<Mob> ground = new ArrayList<>(militia);
        ground.add(brute);
        ground.add(ravager);
        final int[] t = { 0 };
        final int[] arrivedAt = { -1 };
        final boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            t[0]++;
            if (t[0] == 5) {
                pad.move(militia, pad.at(TO_X, LANE_MILITIA));
                pad.move(List.of(brute), pad.at(TO_X, LANE_BRUTE));
                pad.move(List.of(ravager), pad.at(TO_X, LANE_RAVAGER));
                pad.move(List.of(ghast), pad.at(TO_X, LANE_GHAST));
            }
            if (t[0] <= 5)
                return;
            if (arrivedAt[0] < 0) {
                boolean all = true;
                for (Mob m : ground)
                    all &= m.getX() >= pad.cx + TO_X - 3;
                if (all)
                    arrivedAt[0] = t[0];
                else if (t[0] < 480)
                    return;
            }
            // two more samples after the last step, so the walk's tail is in the field
            if (arrivedAt[0] >= 0 && t[0] < arrivedAt[0] + 2 * TrampleRules.SAMPLE_TICKS + 2)
                return;
            done[0] = true;
            judgeMarch(pad, ground, before, arrivedAt[0]);
            pad.finish();
        });
    }

    private static void judgeMarch(Pad pad, List<Mob> ground, Map<BlockPos, BlockState> before, int arrivedAt) {
        List<String> problems = pad.problems;
        if (arrivedAt < 0) {
            StringBuilder where = new StringBuilder();
            for (Mob m : ground)
                where.append(String.format(Locale.ROOT, "%s at x %+.1f; ", m.getType().toShortString(), m.getX() - pad.cx));
            problems.add("(setup) the walkers did not reach the far side: " + where);
        }
        // each walked lane is worn on the grass west and east of the stone (formations spread a lane over a few rows)
        int[][] lanes = { { LANE_MILITIA, 2 }, { LANE_BRUTE, 1 }, { LANE_RAVAGER, 2 } };
        String[] names = { "militia", "brute", "ravager" };
        for (int l = 0; l < lanes.length; l++) {
            int lane = lanes[l][0], spread = lanes[l][1];
            int worn = 0, columns = 0;
            StringBuilder trail = new StringBuilder();
            for (int dx = FROM_X + 3; dx <= TO_X - 4; dx++) {
                if (dx >= -2 && dx <= 1)
                    continue;   // the stone band and the column either side of it
                columns++;
                int v = pad.bandMax(dx, lane - spread, lane + spread);
                trail.append(v).append(' ');
                if (v > 0)
                    worn++;
            }
            if (worn < columns * 0.8)
                problems.add(names[l] + " lane worn on " + worn + " of " + columns + " grass columns [" + trail.toString().trim() + "]");
        }
        // stone never wears
        int stone = 0;
        for (int dz = -PAD_R; dz <= PAD_R; dz++)
            for (int dx = -1; dx <= 0; dx++)
                if (pad.trample(dx, dz) != 0)
                    stone++;
        if (stone != 0)
            problems.add(stone + " stone cells were worn");
        // the ghast flew: its lane (and the rows beside it) is untouched; the pad's edge rows nobody walked are too
        int ghastCells = 0, edge = 0;
        for (int dx = -PAD_R; dx <= PAD_R; dx++) {
            for (int dz = LANE_GHAST - 2; dz <= LANE_GHAST + 2; dz++)
                if (pad.trample(dx, dz) != 0)
                    ghastCells++;
            if (pad.trample(dx, -PAD_R) != 0 || pad.trample(dx, PAD_R) != 0)
                edge++;
        }
        if (ghastCells != 0)
            problems.add("the flying ghast wore " + ghastCells + " cells of its lane");
        if (edge != 0)
            problems.add(edge + " cells of the rows nobody walked were worn");
        // the wide body wears deeper in one pass than the narrow one
        int brutePeak = 0, ravagerPeak = 0;
        for (int dx = FROM_X + 3; dx <= TO_X - 4; dx++) {
            brutePeak = Math.max(brutePeak, pad.bandMax(dx, LANE_BRUTE - 1, LANE_BRUTE + 1));
            ravagerPeak = Math.max(ravagerPeak, pad.bandMax(dx, LANE_RAVAGER - 2, LANE_RAVAGER + 2));
        }
        if (ravagerPeak <= brutePeak)
            problems.add("the ravager's lane (" + ravagerPeak + ") should be worn deeper than the brute's (" + brutePeak + ")");
        // purely visual: not one block changed
        int changed = 0;
        for (Map.Entry<BlockPos, BlockState> e : before.entrySet())
            if (pad.level.getBlockState(e.getKey()) != e.getValue())
                changed++;
        if (changed != 0)
            problems.add(changed + " blocks of the pad changed (a track must be paint only)");
        ReignOfNether.LOGGER.info("[Stress] [Trample] march: ravager peak {}, brute peak {}, arrived at tick {}", ravagerPeak, brutePeak, arrivedAt);
    }

    // ------------------------------------------------------------------ 2. where a walk wears, sync and decay

    /**
     * A scripted walk through the same entry the sampler uses (GroundMemoryServer.trampleSegment) over grass, a stone
     * column, a water column and a ledge three blocks above the walker's feet: the grass is worn, the others are not.
     * The field goes to a player as a snapshot, a second walk as a delta of exactly the cells it changed, every packet
     * through the codec, and the copy equals the server's field byte for byte. With the trample half-life the track
     * fades to nothing (floor 0) - the grass grows back.
     */
    @GameTest(template = ARENA, batch = "trample_sync", timeoutTicks = 100)
    public static void trample_lands_on_soft_ground_syncs_and_fades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        WorldMemoryGameTests.pad(helper);
        BlockPos stone = WorldMemoryGameTests.abs(helper, 5, 0, 8), water = WorldMemoryGameTests.abs(helper, 7, 0, 8);
        BlockPos ledge = WorldMemoryGameTests.abs(helper, 9, 3, 8);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(ledge, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        BlockPos grass = WorldMemoryGameTests.abs(helper, 3, 0, 8), grassEast = WorldMemoryGameTests.abs(helper, 12, 0, 8);
        double foot = WorldMemoryGameTests.PLATFORM_Y + 1;
        double ax = grass.getX() - 2 + 0.5, bx = grassEast.getX() + 2 + 0.5, z = grass.getZ() + 0.5;
        WorldMemoryGameTests.TestWire wire = new WorldMemoryGameTests.TestWire();
        wire.chunks.addAll(WorldMemoryGameTests.chunksAround(level, WorldMemoryGameTests.abs(helper, 8, 1, 8), 12));
        Set<Long> chunks = wire.chunks;
        ServerPlayer p = wire.join(level, "gt_trample_a");
        try {
            int first = GroundMemoryServer.trampleSegment(level, ax, z, bx, z, foot, 0.6);
            if (first == 0) {
                helper.fail("a walk over grass wore nothing");
                return;
            }
            if (cell(level, grass) <= 0 || cell(level, grassEast) <= 0)
                problems.add("the grass either side was not worn (" + cell(level, grass) + ", " + cell(level, grassEast) + ")");
            if (cell(level, stone) != 0)
                problems.add("stone was worn: " + cell(level, stone));
            if (cell(level, water) != 0)
                problems.add("water was worn: " + cell(level, water));
            if (cell(level, ledge) != 0)
                problems.add("a ledge three above the feet was worn: " + cell(level, ledge));
            GroundMemoryServer.syncNow(level, wire);
            WorldMemoryGameTests.Tally t1 = WorldMemoryGameTests.tally(wire, p, 0, chunks);
            if (t1.snapshots() == 0)
                problems.add("the worn chunk did not go out as a snapshot: " + t1);
            Map<Long, GroundMemory> copies = new HashMap<>();
            WorldMemoryGameTests.receive(copies, wire, p, 0);
            int sent = wire.count(p);
            int second = GroundMemoryServer.trampleSegment(level, ax, z + 1, bx, z + 1, foot, 0.6);
            GroundMemoryServer.syncNow(level, wire);
            WorldMemoryGameTests.Tally t2 = WorldMemoryGameTests.tally(wire, p, sent, chunks);
            if (t2.snapshots() != 0 || t2.deltaCells() != second)
                problems.add("a second walk should be a delta of its " + second + " changed cells, got " + t2);
            WorldMemoryGameTests.receive(copies, wire, p, sent);
            int bad = WorldMemoryGameTests.mismatches(level, copies, chunks, Integer.MIN_VALUE);
            if (bad != 0)
                problems.add(bad + " cells of the client's copy differ from the server's field");
            // the grass grows back: two hours on, the track is gone (trample's floor is 0)
            GroundMemory field = GroundMemoryCapability.of(level.getChunkAt(grass));
            if (field != null) {
                GroundMemory later = GroundMemory.load(saved(field), level.getGameTime());
                later.decayTo(level.getGameTime() + 2L * 60 * 60 * 20, DecayParams.DEFAULT);
                int cellIdx = GroundMemory.index(grass.getX() & 15, grass.getZ() & 15);
                if (later.trample(cellIdx) != 0)
                    problems.add("two hours on the track still reads " + later.trample(cellIdx));
            }
        } finally {
            level.setBlock(stone, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(water, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(ledge, Blocks.AIR.defaultBlockState(), 2);
        }
        WorldMemoryGameTests.finish(helper, problems);
    }

    private static int cell(ServerLevel level, BlockPos p) {
        GroundMemory m = GroundMemoryCapability.of(level.getChunkAt(p));
        return m == null ? 0 : m.trample(GroundMemory.index(p.getX() & 15, p.getZ() & 15));
    }

    private static net.minecraft.nbt.CompoundTag saved(GroundMemory m) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        m.save(tag);
        return tag;
    }

    // ------------------------------------------------------------------ 3. the cost, against the budget set first

    /** design/battlefield_effects.md: <= 3 us per walking unit per sample pass on the CI GameTest server. */
    static final double BUDGET_US_PER_WALK = 3.0;
    /** ...and a 100-unit march costs <= 6 KB/s per client in deltas (the scars brawl budget). */
    static final double BUDGET_BYTES_PER_S_100 = 6 * 1024;

    /**
     * Fifty-two units of three kinds (40 militia, 8 brutes, 4 ravagers) march across a pad and back through real MOVE
     * orders; the live sampler's own totals (GroundMemoryServer.trampleTotals) over the march give its cost per walking
     * unit and the cells it changed, priced at the delta wire's bytes per cell (5, GroundMemoryClientboundPacket) and
     * scaled to 100 units. Both against the design's budgets, set before this was first run.
     */
    @GameTest(template = ARENA, batch = "trample_cost", timeoutTicks = 420)
    public static void trample_cost_stays_inside_the_budget(GameTestHelper helper) {
        Pad pad = new Pad(helper, "cost", 16);
        List<Mob> units = new ArrayList<>();
        for (int i = 0; i < 40; i++)
            units.add(pad.spawn(EntityRegistrar.MILITIA_UNIT.get(), -13 + (i % 4), -10 + (i / 4) * 2, PAD_Y + 1));
        for (int i = 0; i < 8; i++)
            units.add(pad.spawn(EntityRegistrar.BRUTE_UNIT.get(), -8, -10 + i * 2.5, PAD_Y + 1));
        for (int i = 0; i < 4; i++)
            units.add(pad.spawn(EntityRegistrar.RAVAGER_UNIT.get(), -15, -9 + i * 6, PAD_Y + 1));
        if (units.contains(null)) {
            pad.undo();
            helper.fail("(setup) could not create the marchers");
            return;
        }
        final int[] t = { 0 };
        final long[] start = new long[4];
        final boolean[] done = { false };
        final int window = 360;
        helper.onEachTick(() -> {
            if (done[0])
                return;
            t[0]++;
            if (t[0] == 5) {
                long[] s = GroundMemoryServer.trampleTotals(pad.level);
                System.arraycopy(s, 0, start, 0, 4);
                pad.move(units, pad.at(12, 0));
            }
            if (t[0] == 5 + window / 2)
                pad.move(units, pad.at(-12, 0));
            if (t[0] < 5 + window)
                return;
            done[0] = true;
            long[] end = GroundMemoryServer.trampleTotals(pad.level);
            long passes = end[0] - start[0], walks = end[1] - start[1], nanos = end[2] - start[2], cells = end[3] - start[3];
            double seconds = window / 20.0;
            double usPerWalk = walks == 0 ? Double.NaN : nanos / 1e3 / walks;
            double unitsWalking = passes == 0 ? 0 : walks / (double) passes;
            double bytesPerS = cells * 5 / seconds;
            double bytesPerS100 = unitsWalking == 0 ? Double.NaN : bytesPerS * 100 / unitsWalking;
            ReignOfNether.LOGGER.info(String.format(Locale.ROOT,
                "[Stress] [Trample] cost: %d passes, %d walks (%.1f units walking a pass), %.2f us per walk (budget %.1f), %d cells changed = %.0f B/s, %.0f B/s per 100 walkers (budget %.0f)",
                passes, walks, unitsWalking, usPerWalk, BUDGET_US_PER_WALK, cells, bytesPerS, bytesPerS100, BUDGET_BYTES_PER_S_100));
            if (walks < units.size() * 3L)
                pad.problems.add("the sampler counted only " + walks + " walks in " + passes + " passes for " + units.size() + " marching units");
            else {
                if (!(usPerWalk <= BUDGET_US_PER_WALK))
                    pad.problems.add(String.format(Locale.ROOT, "%.2f us per walking unit per pass (budget %.1f)", usPerWalk, BUDGET_US_PER_WALK));
                if (!(bytesPerS100 <= BUDGET_BYTES_PER_S_100))
                    pad.problems.add(String.format(Locale.ROOT, "%.0f B/s per client per 100 walkers (budget %.0f)", bytesPerS100, BUDGET_BYTES_PER_S_100));
            }
            pad.finish();
        });
    }
}
