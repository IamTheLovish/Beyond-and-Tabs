package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * No friendly fire on buildings (BuildingPlacement.destroyRandomBlocks with an attacker): lovish's Oct 19 playtest had
 * the Circle of Elders lose 340 -> 326 HP with only his own units round it.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FriendlyFireGameTests {

    static BuildingPlacement built(GameTestHelper helper, String owner, int x, int z) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, level,
            helper.absolutePos(new BlockPos(x, 1, z)), Rotation.NONE, owner, false);
        for (var b : bp.getBlocks())
            if (!b.getBlockState().isAir())
                level.setBlock(b.getBlockPos(), b.getBlockState(), 3);
        bp.initPlacedBlocks();
        bp.isBuilt = true;
        BuildingServerEvents.getBuildings().add(bp);
        return bp;
    }

    static VindicatorUnit striker(GameTestHelper helper, String owner, BuildingPlacement target, double x, double z) {
        VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
        if (v == null)
            return null;
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        v.moveTo(o.getX() + x, o.getY(), o.getZ() + z, 0, 0);
        v.setOwnerName(owner);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /** An own unit ordered onto its own building does no damage and lets go; the same order on an enemy's does damage. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 200)
    public static void own_units_never_damage_own_buildings(GameTestHelper helper) {
        String owner = "gametest_ff_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BuildingPlacement own = built(helper, owner, 2, 2);
        BuildingPlacement foes = built(helper, owner + "_foe", 10, 10);
        VindicatorUnit mine = striker(helper, owner, own, 1.5, 6.5);
        VindicatorUnit control = striker(helper, owner, foes, 9.5, 14.5);
        if (mine == null || control == null) {
            BuildingServerEvents.getBuildings().remove(own);
            BuildingServerEvents.getBuildings().remove(foes);
            helper.fail("could not create the units");
            return;
        }
        int ownStart = own.getBlocksPlaced(), foeStart = foes.getBlocksPlaced();
        helper.runAfterDelay(3, () -> {
            mine.setAttackBuildingTarget(own.originPos, true);       // a forced order: the strongest case
            control.setAttackBuildingTarget(foes.originPos, true);
        });
        helper.runAfterDelay(180, () -> {
            int ownNow = own.getBlocksPlaced(), foeNow = foes.getBlocksPlaced();
            mine.discard();
            control.discard();
            BuildingServerEvents.getBuildings().remove(own);
            BuildingServerEvents.getBuildings().remove(foes);
            if (foeNow >= foeStart)
                helper.fail("the control failed: the same order on an enemy building did no damage ("
                    + foeStart + " -> " + foeNow + " blocks) - the test setup is wrong, not the fix");
            else if (ownNow < ownStart)
                helper.fail("an own unit damaged its own building: " + ownStart + " -> " + ownNow + " blocks");
            else
                helper.succeed();
        });
    }
}
