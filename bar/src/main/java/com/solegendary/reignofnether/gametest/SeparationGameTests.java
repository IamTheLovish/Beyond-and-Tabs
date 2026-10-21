package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * unit/Separation: the push that spreads overlapping units bypasses the pathfinder, so it must check the ground it
 * moves a unit onto. The bot soak's Gravebound commander (stacked with its two starting workers) was pushed into lava
 * 10 blocks from its start and died 16 s in (Oct 21, main 223a4bb).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SeparationGameTests {

    /** Two stacked units at a lava pool's edge: the one nearer the lava is never pushed into it. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 140)
    public static void separation_never_pushes_a_unit_into_lava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_separation_lava_" + helper.absolutePos(BlockPos.ZERO).asLong();
        // a lava pool level with the floor, east of x=6 (rows z 6..10)
        for (int x = 6; x <= 9; x++)
            for (int z = 6; z <= 10; z++)
                helper.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
        VindicatorUnit edge = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        VindicatorUnit inner = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (edge == null || inner == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        // overlapping, the edge unit on the lava side: the push would send it east, into the pool
        edge.moveTo(o.getX() + 5.75, o.getY(), o.getZ() + 8.5, 0, 0);
        inner.moveTo(o.getX() + 5.35, o.getY(), o.getZ() + 8.5, 0, 0);
        edge.setOwnerName(owner);
        inner.setOwnerName(owner);
        inner.setHoldPosition(true);   // the inner one holds, so only the edge unit is ever pushed
        level.addFreshEntity(edge);
        level.addFreshEntity(inner);
        float full = edge.getMaxHealth();
        helper.runAfterDelay(100, () -> {
            boolean burned = edge.isInLava() || edge.getRemainingFireTicks() > 0 || !edge.isAlive()
                || edge.getHealth() < full;
            String where = String.format("%.2f/%.2f/%.2f", edge.getX() - o.getX(), edge.getY() - o.getY(), edge.getZ() - o.getZ());
            edge.discard();
            inner.discard();
            for (int x = 6; x <= 9; x++)
                for (int z = 6; z <= 10; z++)
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            if (burned)
                helper.fail("the separation push moved the edge unit into the lava (now at " + where + ")");
            else
                helper.succeed();
        });
    }
}
