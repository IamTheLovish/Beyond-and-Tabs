package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Nothing loose hangs over a game-test arena. An arena is cut into generated ground (ArenaReady.sealed: y -61, caves and
 * aquifers), and vanilla's StructureUtils.clearSpaceForStructure clears the structure's box only up to 20 blocks above
 * its top: natural gravel resting on what it cleared was left hanging over the arena, and fell ~30 blocks onto it as soon
 * as the setup touched a neighbour (flake-stress 41646780667: every falling block near a graveyard gate started at
 * y -25..-28, the vanilla ceiling being -29, and landed 42-47 ticks in). Outside a half-blocked graveyard's one open gate
 * cell it closed the way out: "3 of 5 Zombies not out of the graveyard and at the rally", the grid seeing no path
 * (GraveyardExitGameTests, stress 41639234475 and CI on #464).
 * <p>
 * The test clears a small scratch box well above its own arena with a two-high stack of gravel resting on vanilla's
 * ceiling (box top + 21 and + 22) and stone over it (+ 23 and + 30): the gravel must no longer be able to fall, and the
 * ground over it must stay - the ground is the arena's lid (taking the whole column away opened the arenas to the sky
 * and the zombies in the blow-timing tests burned: PR 491's first fix). Its own batch.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArenaClearingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 60, batch = "arena_clearing")
    public static void clearing_an_arena_settles_loose_ground_on_vanillas_ceiling_and_keeps_the_lid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(6, 40, 6));
        BoundingBox box = new BoundingBox(base.getX(), base.getY(), base.getZ(), base.getX() + 3, base.getY() + 3, base.getZ() + 3);
        BlockPos onCeiling = new BlockPos(base.getX() + 1, box.maxY() + 21, base.getZ() + 1);
        BlockPos second = onCeiling.above(), lid = onCeiling.above(2), higher = onCeiling.above(9);
        level.setBlock(onCeiling, Blocks.GRAVEL.defaultBlockState(), 2);
        level.setBlock(second, Blocks.GRAVEL.defaultBlockState(), 2);
        level.setBlock(lid, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(higher, Blocks.STONE.defaultBlockState(), 2);
        // y below the box: every cleared position becomes air (vanilla fills what is under y with ground)
        StructureUtils.clearSpaceForStructure(box, box.minY() - 3, level);
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (BlockPos p : new BlockPos[] { onCeiling, second })
            if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.FallingBlock)
                problems.add(level.getBlockState(p).getBlock() + " still loose " + (p.getY() - box.maxY()) + " over the box (vanilla clears to + 20)");
        if (!level.getBlockState(lid).is(Blocks.STONE) || !level.getBlockState(higher).is(Blocks.STONE))
            problems.add("the ground over it was taken away (lid " + level.getBlockState(lid).getBlock() + ", higher "
                + level.getBlockState(higher).getBlock() + "): the arena's lid must stay");
        for (BlockPos p : new BlockPos[] { onCeiling, second, lid, higher })
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
