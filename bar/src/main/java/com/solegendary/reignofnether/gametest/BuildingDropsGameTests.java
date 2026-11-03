package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A building that is destroyed drops none of its pieces as items (BuildingDrops; lovish's Oct 29 game left 1,313 of
 * them lying round - lanterns, ladders, banners, doors - up to 411 ticking at once). The villagers' Lab Annex (3 x 6 x
 * 3) has four standing lanterns on its pillars: its blocks go bottom first, so before BuildingDrops each lantern lost
 * its pillar, broke the vanilla way and fell as an item. After destroy() no lantern item may lie round it.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildingDropsGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void destroyed_building_drops_none_of_its_pieces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_drops_" + helper.absolutePos(BlockPos.ZERO).asLong();
        // the pocket and the annex (it stands through the arena's lid) are put back when the test ends
        TestCleanup.blocks(helper).region(helper.absolutePos(new BlockPos(3, 1, 3)), helper.absolutePos(new BlockPos(12, 10, 12)));
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BuildingPlacement annex = BuildingUtils.getNewBuildingPlacement(Buildings.LAB_ANNEX_VILLAGERS, level,
            helper.absolutePos(new BlockPos(7, 2, 7)), Rotation.NONE, owner, false);
        int lanterns = 0;
        for (BuildingBlock b : annex.getBlocks())
            if (!b.getBlockState().isAir()) {
                level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
                if (b.getBlockState().is(Blocks.LANTERN))
                    lanterns++;
            }
        if (lanterns == 0) {
            helper.fail("the Lab Annex has no lantern any more: this test needs a building with a piece that breaks off");
            return;
        }
        AABB round = new AABB(annex.minCorner, annex.maxCorner.offset(1, 1, 1)).inflate(4);
        List<ItemEntity> before = level.getEntitiesOfClass(ItemEntity.class, round);
        annex.destroy(level);
        List<ItemEntity> after = level.getEntitiesOfClass(ItemEntity.class, round);
        int lanternItems = 0, others = 0;
        for (ItemEntity ie : after) {
            if (before.contains(ie))
                continue;
            if (ie.getItem().is(Items.LANTERN))
                lanternItems += ie.getItem().getCount();
            else
                others++;
            ie.discard();
        }
        if (lanternItems > 0)
            helper.fail("destroying the Lab Annex dropped " + lanternItems + " lantern item(s) of its " + lanterns
                + " lanterns (and " + others + " other item stack(s))");
        else
            helper.succeed();
    }
}
