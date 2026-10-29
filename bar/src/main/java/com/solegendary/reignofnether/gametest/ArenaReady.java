package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Starts a movement test when its units are really ticking, not when they were added.
 *
 * A unit added to the level only ticks once its chunk is entity-ticking, and on a loaded CI runner (a hundred tests
 * start in one batch) that can come well after addFreshEntity: ReactionGameTests found the brace test's paladin with
 * no tick at test tick 12, audience_cuts_8v8_deliveries found its units missing from the level (GameTestChunks
 * .whenEntitiesReady). The movement tests count ticks from the spawn ("six attackers ring their target by tick 100",
 * "a queued move arrives within 400"), so on that runner they measure the runner. They now give their orders after
 * every unit has run {@link #TICKS} ticks of its own and count from there; if a unit never ticks, the failure says
 * which one and where, instead of a stalled chase that looks like a movement bug.
 */
public final class ArenaReady {
    private ArenaReady() { }

    /**
     * A glass floor over this test's whole 16x16 arena column at absolute height {@code y}, with the six blocks above it
     * cleared to air, for a test whose units move and fight on their own: the grid packs arenas ~21 blocks apart, inside
     * a Vindicator's aggro range (10), so on the arena floor a neighbouring test's units can pick the test's units as
     * targets, walk over, crowd round them and knock them about (SkirmishGameTests.skyPad has the same note).
     *
     * The height must be above the terrain: the arena is cut into the ground at y = -60 of an ordinary world (ocean at
     * sea level 63, caves below), and a pad at 24-88 put units in water and in rock (Oct 21, #94: "in water true", a
     * villager on its glass at 41 and the vindicators floating at 62). Every other pad is 120 or higher, and each caller
     * takes its own, 11 or more from the rest (3D distance inside aggro range is the thing to avoid): 301 is the slot
     * left (WaterPathingGameTests' basin took 135: 132-141). Returns the standing position (y + 1) at the arena's corner; template offsets are added to it.
     * The pad and the air above it are put back when the test ends, however it ends (TestCleanup.blocks).
     */
    public static BlockPos pad(GameTestHelper helper, int y) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        // put back when the test ends, pass or fail (TestCleanup): later batches find this column's sky as it was
        TestCleanup.BlockUndo pad = TestCleanup.blocks(helper);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                pad.set(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState());
                for (int dy = 1; dy <= 6; dy++)
                    pad.set(new BlockPos(corner.getX() + dx, y + dy, corner.getZ() + dz), Blocks.AIR.defaultBlockState());
            }
        return new BlockPos(corner.getX(), y + 1, corner.getZ());
    }

    /**
     * Makes the pocket SkirmishGameTests.openGround cleared (template x/z 0..15, y 2..4) proof against fluids: the arena
     * is cut into generated ground at y = -61 (caves and aquifers), and water beside or over the cleared air flowed into
     * it - run 40401681056: the wall-detour Vindicator stopped at "water level=7" 1-3 blocks ahead, nav done (plan 015 B).
     * Stone goes on the whole lid (y 5 over the arena), on every rim cell beside the pocket (x/z -1 and 16, y 2..4) that
     * is not a full block, and on any fluid at y 1 or 5 round it; a fluid inside the pocket becomes air. Blocks it
     * changes outside the arena go into {@code outside} (what stood there), for the test to put back - later batches find
     * that ground. Returns how many fluid cells it found, for the failure message.
     */
    public static int sealed(GameTestHelper helper, Map<BlockPos, BlockState> outside) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        int fluids = 0;
        for (int x = -1; x <= 16; x++)
            for (int z = -1; z <= 16; z++) {
                boolean inside = x >= 0 && x <= 15 && z >= 0 && z <= 15;
                for (int y = 1; y <= 5; y++) {
                    BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
                    BlockState was = level.getBlockState(abs);
                    boolean fluid = !was.getFluidState().isEmpty();
                    if (fluid)
                        fluids++;
                    boolean pocket = y >= 2 && y <= 4;
                    BlockState want;
                    if (inside)
                        want = pocket ? (fluid ? Blocks.AIR.defaultBlockState() : null) : (y == 5 || fluid ? stone : null);
                    else
                        want = fluid || (pocket && !was.isCollisionShapeFullBlock(level, abs)) ? stone : null;
                    if (want == null || was == want)
                        continue;
                    if (!inside)
                        outside.putIfAbsent(abs, was);
                    level.setBlock(abs, want, 3);
                }
            }
        return fluids;
    }

    /**
     * Takes daylight out of a movement test: undead units (a Zombie Villager, a Skeleton without a helmet) get Slowness
     * every 10 ticks in the day (the Unit sunlight effect), so a test's speeds depended on the time of day its batch ran -
     * run 40469592779: the chaser under "slowness 1" at 0.175 against a runner meant to walk at 60% of its speed, 0.15
     * (plan 015 B). The owners get the sandbox's "slipslopslap" cheat (monster units unaffected by sunlight); the
     * returned undo takes it away again.
     */
    public static Runnable noSunlight(String... owners) {
        for (String o : owners)
            com.solegendary.reignofnether.research.ResearchServerEvents.addCheat(o, "slipslopslap");
        return () -> {
            for (String o : owners)
                com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(o, "slipslopslap");
        };
    }

    /** Ticks of its own a unit must have run before the test counts on it. */
    public static final int TICKS = 5;

    /**
     * Calls {@code body} once, from a test tick, when every unit has run {@link #TICKS} ticks of its own; the body
     * gets the test tick it runs at. Fails the test after {@code maxWait} test ticks with {@link #describe}.
     */
    public static void whenTicking(GameTestHelper helper, List<? extends Entity> units, int maxWait, IntConsumer body) {
        final boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            boolean ready = true;
            for (Entity e : units)
                if (e.isRemoved() || e.tickCount < TICKS) {
                    ready = false;
                    break;
                }
            if (!ready && helper.getTick() < maxWait)
                return;
            done[0] = true;
            if (!ready)
                throw new GameTestAssertException("the units were not ticking after " + maxWait + " test ticks: "
                    + describe(helper.getLevel(), units));
            body.accept((int) helper.getTick());
        });
    }

    /** One entry per entity: type and id, its own tick count, whether its chunk is entity-ticking, where it is. */
    public static String describe(ServerLevel level, List<? extends Entity> units) {
        StringBuilder sb = new StringBuilder();
        for (Entity e : units) {
            if (sb.length() > 0)
                sb.append("; ");
            BlockPos at = e.blockPosition();
            sb.append(e.getType().toShortString()).append('#').append(e.getId())
                .append(" tickCount ").append(e.tickCount)
                .append(e.isRemoved() ? " REMOVED" : "")
                .append(", entity-ticking ").append(level.isPositionEntityTicking(at))
                .append(String.format(Locale.ROOT, ", at %.2f/%.2f/%.2f", e.getX(), e.getY(), e.getZ()));
        }
        return sb.toString();
    }

    /**
     * What could be holding a unit back, for a failure message: its speed and what is slowing it, whether it is moving,
     * the blocks it stands in and (given a point to head for) the blocks in its way, and every other entity within 2.5
     * blocks. The Oct 21 red of stuck_chaser had a free Vindicator 3 blocks short of its target after 300 ticks, nudged
     * and sidestepped, nothing in its own state to say why.
     */
    public static String around(Mob m, Entity toward) {
        return around(m, toward.getX(), toward.getZ());
    }

    /** {@link #around(Mob, Entity)} heading for the point (tx, tz). */
    public static String around(Mob m, double tx, double tz) {
        ServerLevel level = (ServerLevel) m.level();
        StringBuilder sb = new StringBuilder("speed ")
            .append(String.format(Locale.ROOT, "%.3f", m.getAttributeValue(Attributes.MOVEMENT_SPEED)))
            .append(", moving ").append(String.format(Locale.ROOT, "%.3f", m.getDeltaMovement().horizontalDistance()))
            .append(", in water ").append(m.isInWater()).append(", effects [");
        boolean first = true;
        for (MobEffectInstance e : m.getActiveEffects()) {
            sb.append(first ? "" : ", ").append(e.getDescriptionId()).append(' ').append(e.getAmplifier());
            first = false;
        }
        BlockPos at = m.blockPosition();
        sb.append("], feet ").append(level.getBlockState(at)).append(", below ").append(level.getBlockState(at.below()));
        Vec3 dir = new Vec3(tx - m.getX(), 0, tz - m.getZ());
        if (dir.lengthSqr() > 1.0E-4) {
            dir = dir.normalize();
            sb.append(", ahead");
            for (int step = 1; step <= 3; step++) {
                BlockPos p = BlockPos.containing(m.getX() + dir.x * step, m.getY(), m.getZ() + dir.z * step);
                sb.append(" [").append(step).append(": ").append(level.getBlockState(p).isAir() ? "air" : level.getBlockState(p).toString())
                    .append(" / ").append(level.getBlockState(p.above()).isAir() ? "air" : level.getBlockState(p.above()).toString())
                    .append(" / below ").append(level.getBlockState(p.below())).append(']');
            }
        }
        sb.append(", near [");
        int n = 0;
        for (Entity e : level.getEntities(m, m.getBoundingBox().inflate(2.5))) {
            if (n++ == 6)
                break;
            sb.append(n > 1 ? ", " : "").append(e.getType().toShortString()).append('#').append(e.getId())
                .append(e instanceof Unit u ? "(" + u.getOwnerName() + ")" : "")
                .append(String.format(Locale.ROOT, " at %.2f/%.2f/%.2f", e.getX(), e.getY(), e.getZ()));
        }
        return sb.append(']').toString();
    }
}
