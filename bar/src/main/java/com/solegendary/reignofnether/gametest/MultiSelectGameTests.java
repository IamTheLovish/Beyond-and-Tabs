package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBatchServerboundPacket;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.MultiSelectRules;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Several buildings of one type selected (tester, #38 items 41-42): the card keeps an upgrade while any of them can
 * still take it, and units go to the factories that can build right now, not into one that is rebuilding itself.
 * The rules are in building/MultiSelectRules (no client imports); the server side runs on real Barracks placements.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MultiSelectGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** The card takes a button from the shown building, else the next that still shows it, else hides it. */
    @GameTest(template = ARENA)
    public static void card_keeps_upgrade_while_any_building_can_take_it(GameTestHelper helper) {
        List<Integer> group = List.of(0, 1, 2);
        boolean[] can = {false, true, true};   // building 0 is the one the HUD shows, mid-upgrade
        Integer src = MultiSelectRules.firstVisible(0, group, i -> can[i]);
        if (src == null || src != 1) {
            helper.fail("the upgrade should come from the next building that can take it (1), got " + src);
            return;
        }
        if (MultiSelectRules.firstVisible(2, group, i -> can[i]) != 2) {
            helper.fail("a shown building that still shows the button should be used as is");
            return;
        }
        can[1] = false;
        can[2] = false;
        if (MultiSelectRules.firstVisible(0, group, i -> can[i]) != null)
            helper.fail("with no building left that can take the upgrade the button should hide");
        else
            helper.succeed();
    }

    /** Round-robin visits the preferred factories first, and falls back to the rest only when none takes the unit. */
    @GameTest(template = ARENA)
    public static void round_robin_prefers_ready_factories(GameTestHelper helper) {
        boolean[] ready = {false, true, true};
        List<Integer> took = new ArrayList<>();
        int cursor = 0;
        for (int click = 0; click < 4; click++) {
            int at = MultiSelectRules.pickRoundRobin(3, cursor, i -> ready[i], i -> true);
            took.add(at);
            cursor = at + 1;
        }
        if (!took.equals(List.of(1, 2, 1, 2))) {
            helper.fail("units should alternate between the two ready factories, went " + took);
            return;
        }
        // factory 1 and 2 refuse (full): the upgrading factory 0 takes it behind its upgrade
        int at = MultiSelectRules.pickRoundRobin(3, 1, i -> ready[i], i -> i == 0);
        if (at != 0)
            helper.fail("when no ready factory takes the unit the busy one should, got " + at);
        else
            helper.succeed();
    }

    /**
     * Real Barracks, one mid-upgrade: units never go to it while the others take them, and the L2 order starts only
     * on the ones that can still take it (none are left once all three have it).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void batch_units_skip_upgrading_lab_and_upgrade_goes_to_the_rest(GameTestHelper helper) {
        String owner = "gametest_multisel_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = new Resources(owner, 0, 100000, 100000);
        ResourcesServerEvents.resourcesList.add(pool);
        try {
            List<ProductionPlacement> row = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                // placements never put in the world: enough for the queue, invisible to other tests
                var bp = BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, helper.getLevel(),
                    helper.absolutePos(new BlockPos(2 + i * 10, 2, 2)), Rotation.NONE, owner, false);
                if (!(bp instanceof ProductionPlacement pp)) {
                    helper.fail("the Barracks is not a production placement: " + bp);
                    return;
                }
                pp.isBuilt = true;
                row.add(pp);
            }
            var l2 = ProductionItems.UPGRADE_LAB_L2;
            var started = BuildingBatchServerboundPacket.applyEach(List.of(row.get(0)), l2);
            if (started.size() != 1) {
                helper.fail("the first Barracks should take the Level 2 order");
                return;
            }
            int cursor = 0;
            for (int click = 0; click < 4; click++) {
                int at = BuildingBatchServerboundPacket.queueRoundRobin(row, ProductionItems.VINDICATOR, cursor);
                if (at < 1) {
                    helper.fail("click " + click + " went to " + at + ", the upgrading lab (0) should be skipped");
                    return;
                }
                cursor = at + 1;
            }
            if (row.get(0).productionQueue.size() != 1 || row.get(1).productionQueue.size() != 2
                    || row.get(2).productionQueue.size() != 2) {
                helper.fail("queues should be 1 (the upgrade) / 2 / 2, are " + row.get(0).productionQueue.size() + " / "
                    + row.get(1).productionQueue.size() + " / " + row.get(2).productionQueue.size());
                return;
            }
            // the click that follows the card: the upgrade reaches the two labs that can still take it, once
            var rest = BuildingBatchServerboundPacket.applyEach(row, l2);
            if (rest.size() != 2 || rest.contains(row.get(0))) {
                helper.fail("the upgrade should start on the 2 labs without it, started " + rest.size());
                return;
            }
            if (!BuildingBatchServerboundPacket.applyEach(row, l2).isEmpty()) {
                helper.fail("no lab should take Level 2 again once all three have it queued");
                return;
            }
            helper.succeed();
        } finally {
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }
}
