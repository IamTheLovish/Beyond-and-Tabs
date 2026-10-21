package com.solegendary.reignofnether.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;
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
     * A glass floor over this test's whole 16x16 arena column at absolute height {@code y}, for a test whose units move
     * and fight on their own: the grid packs arenas ~21 blocks apart, inside a Vindicator's aggro range (10) and well
     * inside an archer's, so on the arena floor a neighbouring test's units pick the test's units as targets, walk over,
     * crowd round them and knock them about (SkirmishGameTests.skyPad has the same note, and a neighbour's stamp can land
     * on the arena floor mid-test too). Nothing else uses these heights (every other pad is 120 or higher), and each caller
     * takes its own. Returns the standing position (y + 1) at the arena's corner; template offsets are added to it.
     */
    public static BlockPos pad(GameTestHelper helper, int y) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
        return new BlockPos(corner.getX(), y + 1, corner.getZ());
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
}
