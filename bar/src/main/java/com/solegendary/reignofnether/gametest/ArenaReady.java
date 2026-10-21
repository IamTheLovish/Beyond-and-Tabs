package com.solegendary.reignofnether.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

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
