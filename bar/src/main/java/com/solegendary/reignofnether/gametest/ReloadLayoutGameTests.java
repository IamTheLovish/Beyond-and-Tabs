package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** The layout of SkirmishGameTests.unitsSurviveReload (units_survive_reload_0..7). */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReloadLayoutGameTests {

    /**
     * Every unit of every shard of units_survive_reload_* stands inside the test's own 16 x 16 arena. The shards hold
     * about 21 unit types now, and four to a row 4 blocks apart put the 17th at z 18 and the 21st at z 22: in the trench
     * between arenas and in the next test's arena, where that test's work and cleanup reach it
     * (units_survive_reload_1: "wolf_unit: gone 5 ticks after joining the level", #488's CI 42003290917 attempt 2).
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void units_survive_reload_slots_stay_inside_the_arena(GameTestHelper helper) {
        int total = SkirmishGameTests.reloadUnitTypes(helper.getLevel()).size();
        List<String> out = new ArrayList<>();
        for (int shard = 0; shard < SkirmishGameTests.RELOAD_SHARDS; shard++) {
            int n = SkirmishGameTests.reloadShardSize(total, shard);
            for (int slot = 0; slot < n; slot++) {
                BlockPos p = SkirmishGameTests.reloadSlot(slot, n);
                if (p.getX() < 1 || p.getX() > 14 || p.getZ() < 1 || p.getZ() > 14)
                    out.add("shard " + shard + " slot " + slot + " of " + n + " at " + p.getX() + "/" + p.getZ());
            }
        }
        if (!out.isEmpty())
            helper.fail(out.size() + " units_survive_reload slots stand outside the arena (x and z 1..14 of 0..15; " + total
                + " unit types in " + SkirmishGameTests.RELOAD_SHARDS + " shards): " + out);
        else
            helper.succeed();
    }
}
