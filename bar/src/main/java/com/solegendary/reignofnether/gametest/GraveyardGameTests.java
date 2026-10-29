package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingLevels;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.monsters.Graveyard;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Graveyard raises units at every level. Playtest Oct 22 19:20 (multiplayer, build a8bf003): "the death buildings
 * werent making any units and said graveyard is full" - L2 / L3 graveyards count as overflowing, and the overflow rule
 * banked every finished unit as a skull that never walked out (release toggle off), then refused all orders with
 * "This graveyard is too full" after ten. Since then the bank took units only at the army cap; now there is no cap
 * (lovish, Oct 29: no max population), so nothing is banked and every unit walks out, at every level.
 *
 * Everything runs synchronously in one tick (runQueue ticks the queue by hand).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class GraveyardGameTests {

    /** More than the old ten-skull limit, still under the queue's MAX_QUEUE (16). */
    static final int QUEUED = 12;

    @GameTest(template = SkirmishGameTests.ARENA)
    public static void graveyard_l1_raises_every_queued_unit(GameTestHelper helper) {
        raisesEveryQueuedUnit(helper, 0);
    }

    @GameTest(template = SkirmishGameTests.ARENA)
    public static void overflowing_graveyard_raises_every_queued_unit(GameTestHelper helper) {
        raisesEveryQueuedUnit(helper, 1);
    }

    @GameTest(template = SkirmishGameTests.ARENA)
    public static void graveyard_l2_raises_every_queued_unit(GameTestHelper helper) {
        raisesEveryQueuedUnit(helper, 2);
    }

    @GameTest(template = SkirmishGameTests.ARENA)
    public static void graveyard_l3_raises_every_queued_unit(GameTestHelper helper) {
        raisesEveryQueuedUnit(helper, 3);
    }

    /**
     * {@code code}: 0 plain, 1 Overflowing Graveyard research, 2 / 3 building level (Graveyard.getUpgradeLevel). With
     * no population cap, twelve queued Zombies are all accepted and all walk out; no skull is banked.
     */
    private static void raisesEveryQueuedUnit(GameTestHelper helper, int code) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_graveyard_" + code + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = new Resources(owner, 0, 100000, 100000);
        ResourcesServerEvents.resourcesList.add(pool);
        GraveyardPlacement gy = null;
        List<ZombieUnit> raised = new ArrayList<>();
        try {
            gy = stampGraveyard(helper, owner, code);
            if (gy == null)
                return;
            List<String> problems = new ArrayList<>();
            int accepted = 0;
            for (int i = 0; i < QUEUED; i++)
                if (gy.startProductionItem(ProductionItems.ZOMBIE))
                    accepted++;
            if (accepted != QUEUED)
                problems.add("accepted " + accepted + " of " + QUEUED + " Zombies (skulls " + gy.getTotalSkulls()
                    + ", queued " + gy.productionQueue.size() + ")");
            int ticks = runQueue(gy, 40000);
            if (!gy.productionQueue.isEmpty())
                problems.add("the queue stalled with " + gy.productionQueue.size() + " left after " + ticks + " ticks");
            raised = zombiesOf(level, gy, owner);
            if (raised.size() != accepted)
                problems.add(raised.size() + " Zombies walked out of " + accepted + " finished");
            if (gy.getTotalSkulls() != 0)
                problems.add(gy.getTotalSkulls() + " units were banked as skulls (there is no cap to bank against)");
            if (!problems.isEmpty())
                helper.fail("graveyard upgrade code " + code + " (" + gy.currentStructureName + "): " + String.join("; ", problems));
            else
                helper.succeed();
        } finally {
            for (ZombieUnit z : raised)
                z.discard();
            clear(level, gy);
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    /*
     * Retired - lovish Oct 29: no max population. overflowing_graveyard_at_the_cap_banks_skulls_until_full held an
     * overflowing graveyard at the army cap banking units as skulls up to its slots and refusing the next order; with
     * no cap there is nothing to bank against, so every unit walks out (raisesEveryQueuedUnit above, at every level,
     * and NoMaxPopulationGameTests with a cap set the old way).
     */

    // ------------------------------------------------------------------ helpers

    /** A finished graveyard at upgrade code 0-3 on a stone pad, in the server's list. Fails the test on a mismatch. */
    static GraveyardPlacement stampGraveyard(GameTestHelper helper, String owner, int code) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.GRAVEYARD, level,
            helper.absolutePos(new BlockPos(4, 1, 4)), Rotation.NONE, owner, false);
        if (!(bp instanceof GraveyardPlacement gy)) {
            helper.fail("the Graveyard placement is " + bp);
            return null;
        }
        if (code == 1)
            gy.changeStructure(Graveyard.upgradedStructureName);
        else if (code >= 2)
            BuildingLevels.restore(gy, code);
        if (gy.getUpgradeLevel() != code) {
            helper.fail("graveyard set up for upgrade code " + code + " reads " + gy.getUpgradeLevel() + " ("
                + gy.currentStructureName + ")");
            return null;
        }
        for (int x = gy.minCorner.getX() - 1; x <= gy.maxCorner.getX() + 1; x++)
            for (int z = gy.minCorner.getZ() - 1; z <= gy.maxCorner.getZ() + 1; z++)
                level.setBlock(new BlockPos(x, gy.minCorner.getY() - 1, z), Blocks.STONE.defaultBlockState(), 2);
        for (var blk : gy.getBlocks())
            level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
        gy.initPlacedBlocks();
        gy.isBuilt = true;
        gy.autoRelease = false;   // nothing ticks it in one tick anyway; a banked skull would show in getTotalSkulls
        BuildingServerEvents.getBuildings().add(gy);
        return gy;
    }

    static int runQueue(GraveyardPlacement gy, int limit) {
        int n = 0;
        while (!gy.productionQueue.isEmpty() && n++ < limit) {
            var a = gy.productionQueue.get(0);
            if (a.item.tick(gy, a))
                gy.productionQueue.remove(0);
        }
        return n;
    }

    static List<ZombieUnit> zombiesOf(ServerLevel level, GraveyardPlacement gy, String owner) {
        if (gy == null)
            return new ArrayList<>();
        return new ArrayList<>(level.getEntitiesOfClass(ZombieUnit.class, gy.getAABB().inflate(24),
            z -> owner.equals(z.getOwnerName())));
    }

    static void clear(ServerLevel level, GraveyardPlacement gy) {
        if (gy == null)
            return;
        gy.productionQueue.clear();
        BuildingServerEvents.getBuildings().remove(gy);
        for (var blk : gy.getBlocks())
            level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
    }
}
