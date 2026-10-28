package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * More line of sight (lovish, Oct 28: the fog reveals too little; a global 1.5x vision multiplier): a unit with the
 * default sight reveals 24 blocks of fog, not 16, and a capitol sees 48, not 32. The reveal is checked through the
 * same vision the fog tick builds (recomputeVisionFor), as FogHidesEnemiesGameTests does.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class VisionRangeGameTests {

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void vision_default_unit_reveals_24_blocks(GameTestHelper helper) {
        String me = "gt_vision_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        String foe = me + "_foe";
        ServerPlayer viewer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(me.getBytes(StandardCharsets.UTF_8)), me));
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        viewer.moveTo(o.getX() + 4000, o.getY(), o.getZ() + 4000, 0, 0);   // its own sight bubble far away

        // a default-sight unit, an enemy 22 blocks off (inside 24, outside the old 16) and one 28 off (outside 24)
        VindicatorUnit mine = FogHidesEnemiesGameTests.unit(helper, me, 2.5, 2.5);
        VindicatorUnit near = FogHidesEnemiesGameTests.unit(helper, foe, 2.5 + 22, 2.5);
        VindicatorUnit far = FogHidesEnemiesGameTests.unit(helper, foe, 2.5, 2.5 + 28);
        if (mine == null || near == null || far == null) {
            for (VindicatorUnit v : new VindicatorUnit[] { mine, near, far })
                if (v != null)
                    v.discard();
            helper.fail("could not create the vision test units");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (mine.getSightRange() != 24)
                    problems.add("a default-sight unit should see 24 blocks, sees " + mine.getSightRange());
                FogOfWarServerEvents.recomputeVisionFor(viewer);
                if (!FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, near))
                    problems.add("an enemy 22 blocks from a default-sight unit is still in fog");
                if (FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, far))
                    problems.add("an enemy 28 blocks from a default-sight unit is revealed (more than 24)");
            } finally {
                FogOfWarServerEvents.onPlayerDisconnect(viewer.getUUID());
                mine.discard();
                near.discard();
                far.discard();
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    @GameTest(template = SkirmishGameTests.ARENA)
    public static void vision_capitol_sees_48_and_a_building_24(GameTestHelper helper) {
        String me = "gt_vision_b_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        BuildingPlacement capitol = BuildingUtils.getNewBuildingPlacement(Buildings.TOWN_CENTRE, helper.getLevel(),
            helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, me, false);
        BuildingPlacement house = BuildingUtils.getNewBuildingPlacement(Buildings.VILLAGER_HOUSE, helper.getLevel(),
            helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, me, false);
        List<String> problems = new ArrayList<>();
        if (capitol == null || house == null)
            problems.add("could not make the placements");
        else {
            if (capitol.getSightRange() != 48)
                problems.add("a capitol should see 48 blocks, sees " + capitol.getSightRange());
            if (house.getSightRange() != 24)
                problems.add("a plain building should see 24 blocks, sees " + house.getSightRange());
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
