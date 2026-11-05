package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * SkirmishGameTests.openGround on its own keeps the terrain's fluids out of the ground it clears (#529's CI 42015032840:
 * a_forced_target_survives_a_stuck_nudge_give_up's immobile Vindicator floated in falling water, "in water true, feet
 * water[level=8]", in a test that places no water). The arena template (16 x 12 x 16: stone at y 0, grass at y 1, air
 * up to y 11) is cut into generated ground, so an aquifer or cave water beside it, or water over its top, flows into a
 * cleared pocket. ArenaReady.sealed does it for the tests that call it (PathingBudgetGameTests, plan 015 B); openGround
 * itself had only a lid for falling gravel and sand, and 181 tests call it.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArenaGroundGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * A water source over the template's top (y 12, over the middle) and one beside the pocket in the gap between
     * arenas (x -1, y 3): openGround alone, then 60 ticks (water falls a block every 5 ticks and spreads one every 5):
     * no fluid anywhere in the column it cleared, from the pocket (y 2..4) up to the template's top (y 11).
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void open_ground_alone_keeps_the_terrains_water_out(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestCleanup.BlockUndo terrain = TestCleanup.blocks(helper);   // the two sources go back to what stood there
        BlockPos over = helper.absolutePos(new BlockPos(8, 12, 8)), beside = helper.absolutePos(new BlockPos(-1, 3, 8));
        terrain.set(over, Blocks.WATER.defaultBlockState());
        terrain.set(beside, Blocks.WATER.defaultBlockState());
        int placed = (level.getFluidState(over).isEmpty() ? 0 : 1) + (level.getFluidState(beside).isEmpty() ? 0 : 1);
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        helper.runAfterDelay(60, () -> {
            List<String> problems = new ArrayList<>();
            if (placed != 2)
                problems.add("the test placed " + placed + " of its 2 water sources (nothing tested)");
            int wet = 0;
            String first = null;
            for (int x = 0; x <= 15; x++)
                for (int z = 0; z <= 15; z++)
                    for (int y = 2; y <= 11; y++)
                        if (!level.getFluidState(helper.absolutePos(new BlockPos(x, y, z))).isEmpty()) {
                            wet++;
                            if (first == null)
                                first = x + "/" + y + "/" + z + " " + level.getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
                        }
            if (wet > 0)
                problems.add(wet + " fluid cells in the ground openGround cleared after 60 ticks (first " + first + ")");
            TerrainGameTests.finish(helper, problems);
        });
    }
}
