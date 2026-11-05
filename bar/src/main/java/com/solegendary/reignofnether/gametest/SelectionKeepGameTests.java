package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.SelectionKeep;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Own units that scroll out of tracking range (DISCARDED / UNLOADED_*) stay selected; dead or killed ones go. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SelectionKeepGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void units_out_of_range_stay_selected_dead_ones_go(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Entity.RemovalReason r : new Entity.RemovalReason[] { Entity.RemovalReason.DISCARDED,
                Entity.RemovalReason.UNLOADED_TO_CHUNK, Entity.RemovalReason.UNLOADED_WITH_PLAYER,
                Entity.RemovalReason.CHANGED_DIMENSION, null })
            if (SelectionKeep.shouldDrop(false, r))
                problems.add("a living unit removed as " + r + " should stay selected");
        if (!SelectionKeep.shouldDrop(false, Entity.RemovalReason.KILLED))
            problems.add("a KILLED unit should be dropped");
        for (Entity.RemovalReason r : Entity.RemovalReason.values())
            if (!SelectionKeep.shouldDrop(true, r))
                problems.add("a dead or dying unit (" + r + ") should be dropped");
        if (!SelectionKeep.shouldDrop(true, null))
            problems.add("a dying unit still in the world should be dropped");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
