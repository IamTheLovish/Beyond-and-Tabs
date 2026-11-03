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
 * A game test's arena is cleared all the way up: nothing a test left above an earlier arena at the same spot hangs over
 * the next one. Vanilla's StructureUtils.clearSpaceForStructure clears the structure's box only up to 20 blocks above its
 * top, so blocks an earlier batch left higher stayed, unsupported; gravel among them fell ~30 blocks onto the next
 * batch's arenas as soon as their setup touched a neighbour (flake-stress 41646780667: every falling block near a
 * graveyard gate started at y -25..-28, the vanilla ceiling being -29, and landed 42-47 ticks in). Outside a
 * half-blocked graveyard's one open gate cell it closed the way out: "3 of 5 Zombies not out of the graveyard and at
 * the rally", the grid seeing no path (GraveyardExitGameTests, stress 41639234475 and CI on #464).
 * <p>
 * The test clears a small scratch box well above its own arena, with a gravel block left (no block update, so it hangs)
 * a block above vanilla's ceiling and one 10 blocks higher; both must be gone. Its own batch.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArenaClearingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 60, batch = "arena_clearing")
    public static void clearing_an_arena_takes_away_what_hangs_above_vanillas_ceiling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(6, 40, 6));
        BoundingBox box = new BoundingBox(base.getX(), base.getY(), base.getZ(), base.getX() + 3, base.getY() + 3, base.getZ() + 3);
        BlockPos justAbove = new BlockPos(base.getX() + 1, box.maxY() + 21, base.getZ() + 1);
        BlockPos higher = justAbove.above(10);
        level.setBlock(justAbove, Blocks.GRAVEL.defaultBlockState(), 2);
        level.setBlock(higher, Blocks.GRAVEL.defaultBlockState(), 2);
        // y below the box: every cleared position becomes air (vanilla fills what is under y with ground)
        StructureUtils.clearSpaceForStructure(box, box.minY() - 3, level);
        boolean leftJust = level.getBlockState(justAbove).is(Blocks.GRAVEL), leftHigher = level.getBlockState(higher).is(Blocks.GRAVEL);
        level.setBlock(justAbove, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(higher, Blocks.AIR.defaultBlockState(), 2);
        if (leftJust || leftHigher)
            helper.fail("clearing a structure's space left gravel hanging above it: "
                + (leftJust ? "1 block over vanilla's ceiling (box top + 21) " : "") + (leftHigher ? "11 blocks over it" : ""));
        else
            helper.succeed();
    }
}
