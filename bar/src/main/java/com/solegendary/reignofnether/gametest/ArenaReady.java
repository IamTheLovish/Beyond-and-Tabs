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
import net.minecraft.world.phys.Vec3;

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
     * A glass floor over this test's whole 16x16 arena column at absolute height {@code y}, with the six blocks above it
     * cleared to air, for a test whose units move and fight on their own: the grid packs arenas ~21 blocks apart, inside
     * a Vindicator's aggro range (10), so on the arena floor a neighbouring test's units can pick the test's units as
     * targets, walk over, crowd round them and knock them about (SkirmishGameTests.skyPad has the same note).
     *
     * The height must be above the terrain: the arena is cut into the ground at y = -60 of an ordinary world (ocean at
     * sea level 63, caves below), and a pad at 24-88 put units in water and in rock (Oct 21, #94: "in water true", a
     * villager on its glass at 41 and the vindicators floating at 62). Every other pad is 120 or higher, and each caller
     * takes its own, 11 or more from the rest (3D distance inside aggro range is the thing to avoid): 135 and 301 are
     * the slots left. Returns the standing position (y + 1) at the arena's corner; template offsets are added to it.
     */
    public static BlockPos pad(GameTestHelper helper, int y) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
                for (int dy = 1; dy <= 6; dy++)
                    helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y + dy, corner.getZ() + dz), Blocks.AIR.defaultBlockState(), 3);
            }
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
