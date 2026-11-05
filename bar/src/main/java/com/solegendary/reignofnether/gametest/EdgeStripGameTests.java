package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Units walking a narrow strip beside a drop they cannot climb back up stay on top of it. The game-test arena is a
 * plinth two blocks above the ground round it. graveyard_l3_units_get_out_past_a_building_across_half_the_gate twice
 * lost a Zombie into the trench outside it on the walk from the gate round the graveyard's west side to a rally cell one
 * block from the edge (PR 516 CI 41959204992 shard 8: "#260 at 159.6/-59.8/14.7 outside, 11.3 from the rally"; 41810782156:
 * "#354 at 175.5/-59.2/-2.0"). A match has drops like it (a ridge, a wall top), and a unit down one has no way back.
 * This lays out the same walk without a graveyard: a two-high wall at x 4 (z 4..13, the yard's west wall) leaves a strip
 * x 0..3 beside the arena's west edge; five Zombies raised 10 ticks apart south of the wall's end walk round it to the
 * cell at x 1, z 3, as the graveyard's units do, with the match pathfinder on. None may leave the plinth.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class EdgeStripGameTests {

    static final int UNITS = 5, SPACING = 10, WATCH = 400;
    static final double RALLY_NEAR = 6.0;

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = UNITS * SPACING + WATCH + 60, batch = "edge_strip")
    public static void units_walking_a_strip_beside_a_two_block_drop_stay_on_top(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Runnable giveBackPathing = RtsPathingLease.takeUntilEnd(helper);
        String owner = "gametest_edge_strip_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));   // standing height on the plinth
        BlockPos rally = helper.absolutePos(new BlockPos(1, 2, 3));
        for (int z = 4; z <= 13; z++)
            for (int y = 2; y <= 3; y++)
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        List<ZombieUnit> made = new ArrayList<>();
        List<Deque<String>> trails = new ArrayList<>();
        TestCleanup.atEnd(helper, () -> {
            for (ZombieUnit z : made)
                z.discard();
            for (int z = 4; z <= 13; z++)
                for (int y = 2; y <= 3; y++)
                    helper.setBlock(new BlockPos(4, y, z), Blocks.AIR);
            giveBackPathing.run();
        });
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            long tick = helper.getTick();
            if (tick >= 5 && tick % SPACING == 5 && made.size() < UNITS) {
                ZombieUnit z = EntityRegistrar.ZOMBIE_UNIT.get().create(level);
                if (z == null) {
                    done[0] = true;
                    helper.fail("could not create a Zombie");
                    return;
                }
                z.moveTo(o.getX() + 7.5 + made.size() % 3, o.getY(), o.getZ() + 14.5, 0, 0);
                z.setOwnerName(owner);
                z.setInvulnerable(true);
                level.addFreshEntity(z);
                EngagementStance.set(z, EngagementStance.HOLD_FIRE);
                ((Unit) z).getMoveGoal().setMoveTarget(rally);
                made.add(z);
                trails.add(new ArrayDeque<>());
            }
            boolean all = made.size() == UNITS;
            for (int i = 0; i < made.size(); i++) {
                ZombieUnit z = made.get(i);
                double rx = z.getX() - o.getX(), ry = z.getY() - o.getY(), rz = z.getZ() - o.getZ();
                var v = z.getDeltaMovement();
                var path = z.getNavigation().getPath();
                Deque<String> trail = trails.get(i);
                trail.addLast(String.format(Locale.ROOT, "t%d %.2f/%.2f/%.2f v%.3f/%.3f g%s next %s", tick, rx, ry, rz, v.x, v.z,
                    z.onGround() ? "1" : "0", path == null || path.isDone() ? "none" : path.getNextNodePos().subtract(o).toShortString()));
                if (trail.size() > 8)
                    trail.removeFirst();
                if (ry < -0.5 || rx < -0.35 || rz < -0.35 || rx > 16.35 || rz > 16.35) {
                    done[0] = true;
                    helper.fail(String.format(Locale.ROOT, "Zombie %d of %d left the plinth on the strip beside the drop (arena-relative,"
                        + " the plinth is x and z 0..16 at y 0), last 8 ticks: %s", i + 1, UNITS, String.join(" | ", trail)));
                    return;
                }
                double dx = z.getX() - (rally.getX() + 0.5), dz = z.getZ() - (rally.getZ() + 0.5);
                all &= Math.sqrt(dx * dx + dz * dz) <= RALLY_NEAR;
            }
            if (all) {
                done[0] = true;
                helper.succeed();
            } else if (tick > UNITS * SPACING + WATCH) {
                done[0] = true;
                List<String> where = new ArrayList<>();
                for (ZombieUnit z : made)
                    where.add(String.format(Locale.ROOT, "%.1f/%.1f/%.1f", z.getX() - o.getX(), z.getY() - o.getY(), z.getZ() - o.getZ()));
                helper.fail("not every Zombie reached the cell at x 1, z 3 in " + WATCH + " ticks (still on the plinth): " + where);
            }
        });
    }
}
